package com.usetoolkit.iphoto;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.graphics.ImageFormat;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.provider.MediaStore;
import android.util.Size;
import android.util.Log;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Opt-in direct Camera2 still capture. CameraX high-resolution preferences do not enable
 * SENSOR_PIXEL_MODE_MAXIMUM_RESOLUTION. Only use publicly advertised JPEG stream sizes.
 * On unsupported devices the regular CameraX path remains authoritative.
 */
public final class UltraResolutionCapture {
    private static final String TAG = "IPhotoUltra";
    private UltraResolutionCapture() {}

    public interface Completion {
        void finished(Uri saved, String error);
    }

    public static final class Spec {
        public final String cameraId;
        public final Size jpegSize;
        public final boolean maximumPixelMode;
        public final long normalMaxPixels;
        public final long availableMaxPixels;
        public final String source;

        private Spec(String id, Size size, boolean maximum, long normal, long available, String source) {
            this.cameraId = id;
            this.jpegSize = size;
            this.maximumPixelMode = maximum;
            this.normalMaxPixels = normal;
            this.availableMaxPixels = available;
            this.source = source;
        }
        public long pixels() { return (long) jpegSize.getWidth() * jpegSize.getHeight(); }
    }

    private static Size biggest(Size[] sizes) {
        Size best = null;
        if (sizes != null) for (Size s : sizes) {
            if (s == null || s.getWidth() <= 0 || s.getHeight() <= 0) continue;
            if (best == null || (long) s.getWidth() * s.getHeight()
                > (long) best.getWidth() * best.getHeight()) best = s;
        }
        return best;
    }

    private static long pixels(Size s) {
        return s == null ? 0L : (long) s.getWidth() * s.getHeight();
    }

    /** Returns the biggest JPEG output publicly advertised by this exact logical camera ID. */
    public static Spec inspect(CameraManager manager, String cameraId) throws CameraAccessException {
        CameraCharacteristics cc = manager.getCameraCharacteristics(cameraId);
        StreamConfigurationMap regular = cc.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        Size normal = regular == null ? null : biggest(regular.getOutputSizes(ImageFormat.JPEG));
        Size highDefault = regular == null ? null : biggest(regular.getHighResolutionOutputSizes(ImageFormat.JPEG));
        Size winner = pixels(highDefault) > pixels(normal) ? highDefault : normal;
        String source = pixels(highDefault) > pixels(normal) ? "HIGH_RES_DEFAULT_PIXEL_MODE" : "NORMAL_PIXEL_MODE";
        boolean maxMode = false;
        boolean hasUltraCapability = false;
        int[] capabilities = cc.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES);
        if (capabilities != null && android.os.Build.VERSION.SDK_INT >= 31) {
            for (int cap : capabilities) if (
                cap == CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_ULTRA_HIGH_RESOLUTION_SENSOR) {
                hasUltraCapability = true;
                break;
            }
        }
        if (hasUltraCapability && android.os.Build.VERSION.SDK_INT >= 31) {
            StreamConfigurationMap maximum = cc.get(
                CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP_MAXIMUM_RESOLUTION);
            Size extra = maximum == null ? null : biggest(maximum.getOutputSizes(ImageFormat.JPEG));
            if (pixels(extra) > pixels(winner)) {
                winner = extra;
                maxMode = true;
                source = "SENSOR_PIXEL_MODE_MAXIMUM_RESOLUTION";
            }
        }
        return winner == null ? null : new Spec(cameraId, winner, maxMode,
            pixels(normal), pixels(winner), source);
    }

    public static String diagnostics(CameraManager manager, String cameraId) {
        try {
            Spec s = inspect(manager, cameraId);
            if (s == null) return "카메라가 JPEG 출력 규격을 제공하지 않습니다.";
            return "Camera ID: " + s.cameraId
                + "\n일반 JPEG 최대: " + (s.normalMaxPixels / 1_000_000d) + "MP"
                + "\n앱 접근 가능한 JPEG 최대: " + s.jpegSize.getWidth()
                + "×" + s.jpegSize.getHeight()
                + " (" + PhotoResolutionInfo.megapixelLabel(
                    s.jpegSize.getWidth(), s.jpegSize.getHeight()) + ")"
                + "\n출력 경로: " + s.source
                + "\n기본 카메라의 비공개 제조사 경로는 포함되지 않습니다.";
        } catch (Exception e) {
            return "Camera2 해상도 조회 실패: " + e.getClass().getSimpleName();
        }
    }

    /** CameraX must release the camera before starting this separate camera session. */
    public static void capture(Context context, CameraManager manager, Spec spec,
                               int jpegOrientation, Completion callback) {
        new Session(context.getApplicationContext(), manager, spec,
            jpegOrientation, callback).start();
    }

    private static final class Session {
        final Context context;
        final CameraManager manager;
        final Spec spec;
        final int orientation;
        final Completion callback;
        final HandlerThread thread = new HandlerThread("IPhoto-Ultra-Still");
        final AtomicBoolean ended = new AtomicBoolean(false);
        Handler worker;
        CameraDevice device;
        CameraCaptureSession cameraSession;
        ImageReader jpeg;
        ImageReader metering;
        Runnable timeout;

        Session(Context context, CameraManager manager, Spec spec, int orientation, Completion cb) {
            this.context = context; this.manager = manager; this.spec = spec;
            this.orientation = orientation; this.callback = cb;
        }

        void start() {
            thread.start();
            worker = new Handler(thread.getLooper());
            timeout = () -> finish(null, "고화소 카메라 응답 시간 초과");
            worker.postDelayed(timeout, 15000L);
            try {
                manager.openCamera(spec.cameraId, new CameraDevice.StateCallback() {
                    @Override public void onOpened(CameraDevice camera) {
                        if (ended.get()) { camera.close(); return; }
                        device = camera;
                        setupSession();
                    }
                    @Override public void onDisconnected(CameraDevice camera) {
                        camera.close();
                        finish(null, "카메라 연결이 해제됨");
                    }
                    @Override public void onError(CameraDevice camera, int error) {
                        camera.close();
                        finish(null, "카메라 열기 오류 " + error);
                    }
                }, worker);
            } catch (Exception e) {
                finish(null, "고화소 촬영 준비 실패: " + e.getClass().getSimpleName());
            }
        }

        void setupSession() {
            try {
                jpeg = ImageReader.newInstance(spec.jpegSize.getWidth(),
                    spec.jpegSize.getHeight(), ImageFormat.JPEG, 2);
                StreamConfigurationMap map = manager.getCameraCharacteristics(spec.cameraId)
                    .get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                Size previewSize = meteringSize(map == null ? null
                    : map.getOutputSizes(ImageFormat.YUV_420_888));
                if (previewSize == null) {
                    finish(null, "노출 측정용 카메라 스트림 없음"); return;
                }
                // Consume small YUV frames promptly to keep the sensor AE/AF running
                // before capturing a single full-resolution JPEG.
                metering = ImageReader.newInstance(previewSize.getWidth(),
                    previewSize.getHeight(), ImageFormat.YUV_420_888, 3);
                metering.setOnImageAvailableListener(reader -> {
                    Image frame = null;
                    try { frame = reader.acquireLatestImage(); }
                    catch (Exception ignored) {}
                    finally { if (frame != null) frame.close(); }
                }, worker);
                jpeg.setOnImageAvailableListener(reader -> {
                    Image picture = null;
                    try {
                        picture = reader.acquireNextImage();
                        if (picture == null) throw new IllegalStateException("Empty JPEG");
                        ByteBuffer buffer = picture.getPlanes()[0].getBuffer();
                        byte[] bytes = new byte[buffer.remaining()];
                        buffer.get(bytes);
                        Uri saved = saveJpeg(bytes);
                        finish(saved, null);
                    } catch (Exception e) {
                        Log.e(TAG, "Full sensor JPEG could not be saved", e);
                        finish(null, "고화소 JPG 저장 실패: " + e.getClass().getSimpleName());
                    } finally { if (picture != null) picture.close(); }
                }, worker);
                List<android.view.Surface> targets = Arrays.asList(
                    metering.getSurface(), jpeg.getSurface());
                device.createCaptureSession(targets, new CameraCaptureSession.StateCallback() {
                    @Override public void onConfigured(CameraCaptureSession session) {
                        if (ended.get()) { session.close(); return; }
                        cameraSession = session;
                        try {
                            CaptureRequest.Builder preview = device.createCaptureRequest(
                                CameraDevice.TEMPLATE_PREVIEW);
                            preview.addTarget(metering.getSurface());
                            preview.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO);
                            preview.set(CaptureRequest.CONTROL_AF_MODE,
                                CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                            session.setRepeatingRequest(preview.build(), null, worker);
                            worker.postDelayed(() -> captureStill(), 1000L);
                        } catch (Exception e) {
                            finish(null, "카메라 노출 준비 실패: " + e.getClass().getSimpleName());
                        }
                    }
                    @Override public void onConfigureFailed(CameraCaptureSession session) {
                        finish(null, "기기에서 고화소 JPEG 스트림 조합을 허용하지 않음");
                    }
                }, worker);
            } catch (Exception e) {
                Log.e(TAG, "Ultra session failure", e);
                finish(null, "고화소 스트림 연결 실패: " + e.getClass().getSimpleName());
            }
        }

        static Size meteringSize(Size[] all) {
            if (all == null) return null;
            Size result = null;
            long bestDelta = Long.MAX_VALUE;
            for (Size size : all) {
                long area = pixels(size);
                if (area <= 0 || area > 1280L * 960L) continue;
                long delta = Math.abs(area - 640L * 480L);
                if (delta < bestDelta) { result = size; bestDelta = delta; }
            }
            return result;
        }

        void captureStill() {
            if (ended.get() || device == null || cameraSession == null) return;
            try {
                CaptureRequest.Builder still =
                    device.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
                still.addTarget(jpeg.getSurface());
                still.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO);
                still.set(CaptureRequest.CONTROL_AF_MODE,
                    CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                still.set(CaptureRequest.JPEG_QUALITY, (byte) 100);
                still.set(CaptureRequest.JPEG_ORIENTATION, orientation);
                if (spec.maximumPixelMode && android.os.Build.VERSION.SDK_INT >= 31) {
                    still.set(CaptureRequest.SENSOR_PIXEL_MODE,
                        CameraMetadata.SENSOR_PIXEL_MODE_MAXIMUM_RESOLUTION);
                }
                cameraSession.capture(still.build(), new CameraCaptureSession.CaptureCallback() {
                    @Override public void onCaptureFailed(CameraCaptureSession session,
                        android.hardware.camera2.CaptureRequest request,
                        android.hardware.camera2.CaptureFailure failure) {
                        finish(null, "센서 고화소 촬영 실패");
                    }
                }, worker);
            } catch (Exception e) {
                Log.e(TAG, "Ultra capture failed", e);
                finish(null, "고화소 촬영 거부: " + e.getClass().getSimpleName());
            }
        }

        Uri saveJpeg(byte[] jpegData) throws Exception {
            ContentResolver resolver = context.getContentResolver();
            ContentValues values = new ContentValues();
            values.put(MediaStore.MediaColumns.DISPLAY_NAME,
                "Iphoto_HighRes_" + System.currentTimeMillis() + ".jpg");
            values.put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg");
            values.put(MediaStore.MediaColumns.RELATIVE_PATH,
                Environment.DIRECTORY_PICTURES + "/I photo");
            values.put(MediaStore.MediaColumns.IS_PENDING, 1);
            Uri uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
            if (uri == null) throw new java.io.IOException("MediaStore insert failed");
            try {
                try (java.io.OutputStream stream = resolver.openOutputStream(uri, "w")) {
                    if (stream == null) throw new java.io.IOException("No output stream");
                    stream.write(jpegData);
                }
                ContentValues ready = new ContentValues();
                ready.put(MediaStore.MediaColumns.IS_PENDING, 0);
                if (resolver.update(uri, ready, null, null) < 1)
                    throw new java.io.IOException("MediaStore publish failed");
                return uri;
            } catch (Exception failure) {
                resolver.delete(uri, null, null);
                throw failure;
            }
        }

        void finish(Uri saved, String error) {
            if (!ended.compareAndSet(false, true)) return;
            if (worker != null && timeout != null) worker.removeCallbacks(timeout);
            try { if (cameraSession != null) cameraSession.close(); }
            catch (Exception ignored) {}
            try { if (device != null) device.close(); }
            catch (Exception ignored) {}
            try { if (jpeg != null) jpeg.close(); }
            catch (Exception ignored) {}
            try { if (metering != null) metering.close(); }
            catch (Exception ignored) {}
            if (worker != null) thread.quitSafely();
            callback.finished(saved, error);
        }
    }
}
