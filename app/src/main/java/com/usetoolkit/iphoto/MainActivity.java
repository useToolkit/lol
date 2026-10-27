package com.usetoolkit.iphoto;

import android.Manifest;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.util.Range;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.ComponentActivity;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.camera.camera2.interop.Camera2CameraControl;
import androidx.camera.camera2.interop.Camera2Interop;
import androidx.camera.camera2.interop.Camera2CameraInfo;
import androidx.camera.camera2.interop.CaptureRequestOptions;
import androidx.camera.core.CameraEffect;
import androidx.camera.core.Camera;
import androidx.camera.core.Preview;
import androidx.camera.core.UseCaseGroup;
import androidx.camera.core.ViewPort;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.FocusMeteringAction;
import androidx.camera.core.FocusMeteringResult;
import androidx.camera.core.MeteringPoint;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.ExposureState;
import androidx.camera.media3.effect.Media3Effect;
import androidx.camera.video.FallbackStrategy;
import androidx.camera.video.PendingRecording;
import androidx.camera.video.Recorder;
import androidx.camera.video.VideoCapture;
import androidx.camera.video.MediaStoreOutputOptions;
import androidx.camera.video.Quality;
import androidx.camera.video.QualitySelector;
import androidx.camera.video.Recording;
import androidx.camera.video.VideoRecordEvent;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.LifecycleCameraController;
import androidx.camera.view.PreviewView;
import androidx.camera.view.video.AudioConfig;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.common.util.concurrent.ListenableFuture;

import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.Locale;

/** I photo - on-device photo/video capture with a shared GPU-based 3D LUT. */
@androidx.annotation.OptIn(markerClass = androidx.camera.camera2.interop.ExperimentalCamera2Interop.class)
public final class MainActivity extends ComponentActivity {
    private static final String TAG = "IPhoto";
    private static final int INK = Color.rgb(9, 11, 13);
    private static final int GOLD = Color.rgb(223, 195, 154);
    private static final int WHITE = Color.rgb(244, 239, 231);
    private static final int MUTED = Color.rgb(166, 166, 164);

    private ActivityResultLauncher<String[]> permissions;
    private ActivityResultLauncher<String> microphonePermission;
    private boolean microphonePermissionAsked = false;
    // Explicit CameraX UseCaseGroup gives us per-frame Camera2 metadata that
    // LifecycleCameraController previously hid, needed for real sensor exposure lock.
    private ProcessCameraProvider cameraProvider;
    private Camera boundCamera;
    private Preview previewUseCase;
    private ImageCapture imageCapture;
    private Recorder videoRecorder;
    private VideoCapture<Recorder> videoCapture;
    private boolean cameraInitializationStarted = false;
    private volatile long lastCaptureTimeMs = 0;
    private volatile long lastSensorExposureNs = 0;
    private volatile int lastSensorISO = 0;
    private volatile float lastLensFocusDistance = -1f;
    private volatile int lastResultAeMode = -1;
    private volatile boolean lastResultAeLocked = false;
    private volatile int lastResultAwbMode = -1;
    // Manual sensor exposure: save the ORIGINAL scene's sensor state and apply
    // the same ISO + shutter duration to every subsequent preview/video frame.
    private android.util.Range<Integer> supportedISO;
    private android.util.Range<Long> supportedShutterNs;
    private volatile boolean hardLockRequested = false;
    private volatile boolean hardLockVerified = false;
    private volatile boolean fallbackLockRequested = false;
    private volatile boolean manualFocusRequested = false;
    private volatile float targetFocusDistance = -1f;
    private volatile long baselineShutterNs = 0L;
    private volatile int baselineISO = 0;
    private volatile long requestedShutterNs = 0L;
    private volatile int requestedISO = 0;
    private volatile int consecutiveLockFrames = 0;
    private volatile int mismatchFrames = 0;
    private volatile long allowSensorSettleUntilMs = 0L;
    private volatile int currentCameraEpoch = 0;
    private volatile boolean manualSensorAvailable = false;
    private float manualExposureDeltaEV = 0f;
    private long manualExposureLastUpdateMs = 0L;
    private PreviewView preview;
    private FrameLayout previewStage, previewFrame;
    private LinearLayout bottomControls;
    private FrameGuide frameGuide;
    private TextView[] ratioButtons;
    private TextView focusButton, focusIndicator;
    private boolean focusLocked = false, touchMoved = false, touchedLong = false, pinchActive = false;
    private float focusX = -1f, focusY = -1f, touchX, touchY, lastPinchDistance, cameraZoom = 1f;
    private Runnable pendingFocusLock;
    private ExposureSunGuide exposureSunGuide;
    private boolean aeLocked = false;
    private boolean afAeLockPending = false;
    private boolean draggingSun = false;
    private boolean awbLocked = false;
    private boolean exposureAdjustmentSupported = false;
    private long focusRequestSerial = 0L;
    private float exposureDragStartY, exposureDragStartEv, exposureLockBaseEv;
    private int lastExposureIndex = Integer.MIN_VALUE;
    private String frameRatio = "9:16";
    private float currentEv = -.5f;
    private static final String[] RATIOS = {"4:3", "9:16", "1:1", "3:2"};
    private Media3Effect media3;
    private Recording recording;
    private Uri lastMedia;
    private boolean isVideo = false;
    private boolean isFront = false;
    private boolean flash = false;
    private boolean busy = false;
    private ColorLooks.Look selected = ColorLooks.Look.LUXURY;
    private float intensity = .82f;
    private TextView info, modePhoto, modeVideo, capture, captureLabel, flashButton, recordTimer;
    private TextView[] presets;
    private SeekBar strengthSeek, exposureSeek;
    private GridOverlay grid;
    private long startedAt;
    private final android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (recording != null) {
                int seconds = (int) ((android.os.SystemClock.elapsedRealtime() - startedAt) / 1000);
                recordTimer.setText(String.format(Locale.US, "%02d:%02d", seconds / 60, seconds % 60));
                handler.postDelayed(this, 500);
            }
        }
    };

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(INK);
        getWindow().setNavigationBarColor(INK);
        // Android 15/16+ enforces edge-to-edge for targetSdk 35.
        // Draw the background beneath the bars, but keep touch controls within
        // the actual status/navigation/cutout safe area.
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        frameRatio = getPreferences(MODE_PRIVATE).getString("capture_ratio", "9:16");
        boolean validRatio = false;
        for (String r : RATIOS) if (r.equals(frameRatio)) validRatio = true;
        if (!validRatio) frameRatio = "9:16";
        buildInterface();
        grid.setVisibility(getPreferences(MODE_PRIVATE).getBoolean("grid", false) ? View.VISIBLE : View.GONE);
        permissions = registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
            Boolean granted = result.get(Manifest.permission.CAMERA);
            if (Boolean.TRUE.equals(granted) || hasCamera()) startCamera();
            else showMessage("사진 촬영에는 카메라 권한만 필요합니다.");
        });
        // Microphone is requested ONLY when recording a video, not when
        // opening the camera or taking photos. The user can film silently.
        microphonePermission = registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
            if (!Boolean.TRUE.equals(granted)) showMessage("마이크 권한 없이 무음 동영상으로 촬영합니다.");
            if (isVideo && recording == null && !busy && boundCamera != null) startRecording();
        });
        if (hasCamera()) startCamera();
        else permissions.launch(new String[] {Manifest.permission.CAMERA});
    }

    private void startCamera() {
        if (cameraInitializationStarted) return;
        cameraInitializationStarted = true;
        try {
            media3 = new Media3Effect(this,
                CameraEffect.PREVIEW | CameraEffect.VIDEO_CAPTURE | CameraEffect.IMAGE_CAPTURE,
                ContextCompat.getMainExecutor(this), t -> {
                    Log.e(TAG, "GPU effect failed", t);
                    showMessage("효과 처리 오류: 다시 실행해 주세요.");
                });
            media3.setEffects(Collections.singletonList(ColorLooks.generate(selected, intensity)));
            enableFocusGestures();
            ListenableFuture<ProcessCameraProvider> future = ProcessCameraProvider.getInstance(this);
            future.addListener(() -> {
                try {
                    cameraProvider = future.get();
                    preview.post(this::bindCameraUseCases);
                } catch (Exception e) {
                    cameraInitializationStarted = false;
                    Log.e(TAG, "Camera provider failed", e);
                    showMessage("카메라 연결에 실패했습니다.");
                }
            }, ContextCompat.getMainExecutor(this));
        } catch (Exception e) {
            cameraInitializationStarted = false;
            Log.e(TAG, "Unable to initialize camera", e);
            showMessage("카메라 초기화 실패: " + e.getClass().getSimpleName());
        }
    }

    /** Rebind all captures at once, ensuring the preview crop matches saved media. */
    private void bindCameraUseCases() {
        if (cameraProvider == null || preview.getWidth() == 0 || preview.getHeight() == 0) {
            preview.postDelayed(this::bindCameraUseCases, 110);
            return;
        }
        if (recording != null) return;
        try {
            if (focusLocked || aeLocked || afAeLockPending) unlockFocus();
            ++currentCameraEpoch;
            hardLockRequested = false;
            hardLockVerified = false;
            fallbackLockRequested = false;
            manualSensorAvailable = false;
            consecutiveLockFrames = 0;
            mismatchFrames = 0;
            boundCamera = null;
            cameraProvider.unbindAll();
            lastCaptureTimeMs = 0;
            lastSensorExposureNs = 0;
            lastSensorISO = 0;
            lastLensFocusDistance = -1f;
            lastResultAeMode = -1;
            Preview.Builder pb = new Preview.Builder();
            new Camera2Interop.Extender<>(pb).setSessionCaptureCallback(
                new CameraCaptureSession.CaptureCallback() {
                    @Override
                    public void onCaptureCompleted(android.hardware.camera2.CameraCaptureSession session,
                            CaptureRequest request, TotalCaptureResult result) {
                        Long exp = result.get(CaptureResult.SENSOR_EXPOSURE_TIME);
                        Integer iso = result.get(CaptureResult.SENSOR_SENSITIVITY);
                        Float focus = result.get(CaptureResult.LENS_FOCUS_DISTANCE);
                        Integer aeMode = result.get(CaptureResult.CONTROL_AE_MODE);
                        Boolean aeLock = result.get(CaptureResult.CONTROL_AE_LOCK);
                        Integer awbMode = result.get(CaptureResult.CONTROL_AWB_MODE);
                        if (exp != null && exp > 0L && iso != null && iso > 0) {
                            lastSensorExposureNs = exp;
                            lastSensorISO = iso;
                            lastCaptureTimeMs = android.os.SystemClock.elapsedRealtime();
                        }
                        if (focus != null) lastLensFocusDistance = focus;
                        if (aeMode != null) lastResultAeMode = aeMode;
                        lastResultAeLocked = Boolean.TRUE.equals(aeLock);
                        if (awbMode != null) lastResultAwbMode = awbMode;
                        inspectSensorLockResult(result);
                    }
                });
            previewUseCase = pb.build();
            previewUseCase.setSurfaceProvider(preview.getSurfaceProvider());
            imageCapture = new ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY).build();
            imageCapture.setFlashMode(flash ? ImageCapture.FLASH_MODE_ON : ImageCapture.FLASH_MODE_OFF);
            videoRecorder = new Recorder.Builder().setQualitySelector(QualitySelector.from(
                Quality.FHD, FallbackStrategy.higherQualityOrLowerThan(Quality.FHD))).build();
            videoCapture = VideoCapture.withOutput(videoRecorder);
            UseCaseGroup.Builder group = new UseCaseGroup.Builder()
                .addUseCase(previewUseCase).addUseCase(imageCapture)
                .addUseCase(videoCapture).addEffect(media3);
            ViewPort vp = preview.getViewPort();
            if (vp != null) group.setViewPort(vp);
            boundCamera = cameraProvider.bindToLifecycle(this,
                isFront ? CameraSelector.DEFAULT_FRONT_CAMERA : CameraSelector.DEFAULT_BACK_CAMERA,
                group.build());
            if (boundCamera != null && flash && isVideo)
                boundCamera.getCameraControl().enableTorch(true);
            manualSensorAvailable = readManualSensorCapabilities();
            lastExposureIndex = Integer.MIN_VALUE;
            handler.postDelayed(() -> setExposureEv(currentEv), 800);
            setInfo();
        } catch (Exception e) {
            Log.e(TAG, "Camera bind failed", e);
            showMessage("카메라 연결 오류: " + e.getClass().getSimpleName());
        }
    }

    /** Called from Camera2's capture thread for each COMPLETED repeating result. */
    private void inspectSensorLockResult(TotalCaptureResult result) {
        if (!hardLockRequested && !hardLockVerified && !fallbackLockRequested) return;
        Integer mode=result.get(CaptureResult.CONTROL_AE_MODE);
        Long exp=result.get(CaptureResult.SENSOR_EXPOSURE_TIME);
        Integer iso=result.get(CaptureResult.SENSOR_SENSITIVITY);
        Boolean locked=result.get(CaptureResult.CONTROL_AE_LOCK);
        final int epoch=currentCameraEpoch;
        final long serial=focusRequestSerial;
        long now=android.os.SystemClock.elapsedRealtime();
        if (now<allowSensorSettleUntilMs) return;
        boolean matches=false;
        if (hardLockRequested || hardLockVerified) {
            matches=mode!=null && mode==CaptureRequest.CONTROL_AE_MODE_OFF
                && iso!=null && exp!=null
                && Math.abs(iso-requestedISO)<=Math.max(2,requestedISO*.08)
                && Math.abs(exp-requestedShutterNs)<=Math.max(25000L,requestedShutterNs*.08);
        }else if(fallbackLockRequested){
            // Fallback AE-lock state must also be VERIFIED from actual frames.
            matches=Boolean.TRUE.equals(locked);
        }
        consecutiveLockFrames=matches?consecutiveLockFrames+1:0;
        if (matches) mismatchFrames=0;
        else mismatchFrames++;
        if ((hardLockRequested || fallbackLockRequested) && matches && consecutiveLockFrames>=3){
            final boolean wasHard=hardLockRequested;
            runOnUiThread(()->{
                if(epoch!=currentCameraEpoch || serial!=focusRequestSerial || !afAeLockPending)return;
                hardLockRequested=false;
                hardLockVerified=wasHard;
                focusLocked=true;
                aeLocked=true;
                afAeLockPending=false;
                exposureAdjustmentSupported=wasHard || canAdjustExposure();
                exposureLockBaseEv=currentEv;
                manualExposureDeltaEV=0f;
                showFocusTarget(focusX,focusY);
                showMessage(wasHard ?
                    "센서 노출 고정 확인 · ISO와 셔터시간 유지":
                    "기기 수동 노출 미지원 · AE 잠금 모드(유지 보장 제한)");
            });
        } else if (focusLocked && mismatchFrames>=18){
            runOnUiThread(()->{
                if(epoch!=currentCameraEpoch || serial!=focusRequestSerial || !focusLocked)return;
                showMessage("노출 고정 해제 감지 · 재고정이 필요합니다.");
                unlockFocus();
            });
        }
    }

    /** Inspect MANUAL_SENSOR before promising persistent exposure to the user. */
    private boolean readManualSensorCapabilities() {
        if(boundCamera==null || boundCamera.getCameraInfo()==null)return false;
        try {
            Camera2CameraInfo inf=Camera2CameraInfo.from(boundCamera.getCameraInfo());
            int[] cap=inf.getCameraCharacteristic(
                CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES);
            boolean sensor=false;
            if(cap!=null)for(int c:cap)if(c==
                CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR)sensor=true;
            supportedISO=inf.getCameraCharacteristic(
                CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE);
            supportedShutterNs=inf.getCameraCharacteristic(
                CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE);
            return sensor && supportedISO!=null && supportedShutterNs!=null;
        }catch(Exception e){
            Log.w(TAG,"Manual sensor capabilities unavailable",e);
            return false;
        }
    }

    private boolean canManuallySetFocus() {
        if(boundCamera==null || boundCamera.getCameraInfo()==null)return false;
        try {
            Camera2CameraInfo info=Camera2CameraInfo.from(boundCamera.getCameraInfo());
            Float minDistance=info.getCameraCharacteristic(
                CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE);
            int[] afModes=info.getCameraCharacteristic(
                CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES);
            boolean hasOff=false;
            if(afModes!=null) for(int m:afModes)if(m==
                CaptureRequest.CONTROL_AF_MODE_OFF)hasOff=true;
            return minDistance!=null && minDistance>0f && hasOff &&
                lastLensFocusDistance>=0 && lastLensFocusDistance<=minDistance;
        }catch(Exception e){return false;}
    }


    private void buildInterface() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(INK);
        setContentView(root);
        // FrameLayout padding is honored by ALL gravity-based children, so
        // top labels, AF/settings buttons, shutter and mode tabs remain above
        // system navigation, without reducing the preview unnecessarily.
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, windowInsets) -> {
            Insets safe = windowInsets.getInsets(
                WindowInsetsCompat.Type.systemBars() |
                WindowInsetsCompat.Type.displayCutout());
            int left = Math.max(0, safe.left);
            int top = Math.max(0, safe.top);
            int right = Math.max(0, safe.right);
            int bottom = Math.max(0, safe.bottom);
            if (view.getPaddingLeft() != left || view.getPaddingTop() != top + dp(4) ||
                view.getPaddingRight() != right || view.getPaddingBottom() != bottom + dp(6)) {
                view.setPadding(left, top + dp(4), right, bottom + dp(6));
                view.post(this::updateCaptureFrame);
            }
            return windowInsets;
        });
        ViewCompat.requestApplyInsets(root);

        // True WYSIWYG preview: camera content extends behind the shutter controls.
        // Only the ratio-matched PreviewView is bound to CameraX's ViewPort.
        previewStage = new FrameLayout(this);
        FrameLayout.LayoutParams stageLp = new FrameLayout.LayoutParams(-1, -1);
        stageLp.topMargin = dp(53);
        root.addView(previewStage, stageLp);

        previewFrame = new FrameLayout(this);
        previewFrame.setBackgroundColor(0xFF12171B);
        previewStage.addView(previewFrame,
            new FrameLayout.LayoutParams(dp(340), dp(600), Gravity.CENTER));
        preview = new PreviewView(this);
        preview.setImplementationMode(PreviewView.ImplementationMode.COMPATIBLE);
        preview.setScaleType(PreviewView.ScaleType.FILL_CENTER);
        previewFrame.addView(preview, new FrameLayout.LayoutParams(-1, -1));
        grid = new GridOverlay();
        grid.setVisibility(View.GONE);
        previewFrame.addView(grid, new FrameLayout.LayoutParams(-1, -1));
        frameGuide = new FrameGuide();
        previewFrame.addView(frameGuide, new FrameLayout.LayoutParams(-1, -1));
        focusIndicator = label("+", 26, GOLD, Typeface.NORMAL);
        focusIndicator.setGravity(Gravity.CENTER);
        focusIndicator.setBackground(shape(0x4B0C1116, dp(9), GOLD, dp(2)));
        focusIndicator.setVisibility(View.GONE);
        focusIndicator.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        previewFrame.addView(focusIndicator,
            new FrameLayout.LayoutParams(dp(56), dp(56), Gravity.LEFT | Gravity.TOP));
        exposureSunGuide = new ExposureSunGuide();
        exposureSunGuide.setVisibility(View.GONE);
        exposureSunGuide.setClickable(false);
        exposureSunGuide.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        previewFrame.addView(exposureSunGuide,
            new FrameLayout.LayoutParams(-1, -1));
        previewStage.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob) -> updateCaptureFrame());

        View topScrim = new View(this);
        topScrim.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
            new int[] {0xFB080B0F, 0xB0090C10, Color.TRANSPARENT}));
        root.addView(topScrim,
            new FrameLayout.LayoutParams(-1, dp(106), Gravity.TOP));

        View bottomScrim = new View(this);
        bottomScrim.setBackground(new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP,
            new int[] {0xEE090B0F, 0xBF090B0F, 0x79090B0F, Color.TRANSPARENT}));
        root.addView(bottomScrim,
            new FrameLayout.LayoutParams(-1, dp(255), Gravity.BOTTOM));

        LinearLayout top = new LinearLayout(this);
        top.setPadding(dp(18), dp(1), dp(18), 0);
        top.setGravity(Gravity.CENTER_VERTICAL);
        FrameLayout.LayoutParams topLp =
            new FrameLayout.LayoutParams(-1, dp(56), Gravity.TOP);
        root.addView(top, topLp);
        TextView brand = label("I photo", 23, WHITE, Typeface.BOLD);
        brand.setLetterSpacing(.005f);
        top.addView(brand, new LinearLayout.LayoutParams(0, -1, 1f));
        focusButton = pill("AF");
        focusButton.setContentDescription("AE AF 초점 노출 고정. 다시 누르면 해제");
        LinearLayout.LayoutParams focusBtnLp = new LinearLayout.LayoutParams(dp(73), dp(34));
        focusBtnLp.rightMargin = dp(6);
        top.addView(focusButton, focusBtnLp);
        focusButton.setOnClickListener(v -> {
            if (focusLocked || afAeLockPending) unlockFocus();
            else focusAt(focusX >= 0 ? focusX : preview.getWidth()/2f,
                focusY >= 0 ? focusY : preview.getHeight()/2f, true);
        });
        flashButton = pill("⚡ 끔");
        flashButton.setContentDescription("플래시");
        flashButton.setOnClickListener(v -> toggleFlash());
        top.addView(flashButton,
            new LinearLayout.LayoutParams(dp(64), dp(34)));
        TextView settings = pill("⚙");
        settings.setTextSize(22);
        settings.setContentDescription("카메라 설정 및 사진 비율");
        LinearLayout.LayoutParams settingLp =
            new LinearLayout.LayoutParams(dp(46), dp(40));
        settingLp.leftMargin = dp(8);
        top.addView(settings, settingLp);
        settings.setOnClickListener(v -> showSettings());

        info = label("LUXURY NIGHT", 10, WHITE, Typeface.BOLD);
        info.setLetterSpacing(.07f);
        info.setPadding(dp(13), 0, dp(13), 0);
        info.setGravity(Gravity.CENTER);
        info.setBackground(shape(0x9A161B1F, dp(15), 0, 0));
        FrameLayout.LayoutParams infoLp =
            new FrameLayout.LayoutParams(-2, dp(27), Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        infoLp.topMargin = dp(61);
        root.addView(info, infoLp);

        // Controls float OVER the preview, like the Samsung camera reference.
        LinearLayout bottom = new LinearLayout(this);
        bottomControls = bottom;
        bottom.setPadding(dp(14), dp(4), dp(14), dp(8));
        bottom.setOrientation(LinearLayout.VERTICAL);
        bottom.setGravity(Gravity.CENTER_HORIZONTAL);
        root.addView(bottom,
            new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));

        LinearLayout presetRow = new LinearLayout(this);
        presetRow.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams pr = new LinearLayout.LayoutParams(-1, dp(43));
        bottom.addView(presetRow, pr);
        ColorLooks.Look[] looks = ColorLooks.Look.values();
        presets = new TextView[looks.length];
        for (int i = 0; i < looks.length; i++) {
            ColorLooks.Look look = looks[i];
            String shortLabel = look == ColorLooks.Look.LUXURY ? "Luxury" :
                look == ColorLooks.Look.SOFT ? "Soft" :
                look == ColorLooks.Look.CINEMA ? "Noir" : "Original";
            TextView chip = label(shortLabel, 12, look == selected ? INK : WHITE, Typeface.BOLD);
            chip.setGravity(Gravity.CENTER);
            chip.setSingleLine(true);
            chip.setBackground(shape(look == selected ? GOLD : 0xA4212529, dp(19),
                look == selected ? 0 : 0x66FFFFFF, dp(1)));
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0, dp(34), 1f);
            cp.setMargins(dp(4), 0, dp(4), 0);
            presetRow.addView(chip, cp);
            presets[i] = chip;
            chip.setOnClickListener(v -> selectLook(look));
        }

        LinearLayout zoomRow = new LinearLayout(this);
        zoomRow.setGravity(Gravity.CENTER);
        bottom.addView(zoomRow, new LinearLayout.LayoutParams(-1, dp(40)));
        TextView zoom1 = label("1×", 16, GOLD, Typeface.BOLD);
        zoom1.setGravity(Gravity.CENTER);
        zoom1.setBackground(shape(0xD12C3136, dp(30), 0, 0));
        zoom1.setContentDescription("1배 줌");
        zoomRow.addView(zoom1, new LinearLayout.LayoutParams(dp(55), dp(38)));
        zoom1.setOnClickListener(v -> setZoom(1f));
        TextView zoom2 = label("2×", 15, WHITE, Typeface.BOLD);
        zoom2.setGravity(Gravity.CENTER);
        zoom2.setContentDescription("2배 줌");
        LinearLayout.LayoutParams z2p = new LinearLayout.LayoutParams(dp(55), dp(38));
        z2p.leftMargin = dp(4);
        zoomRow.addView(zoom2, z2p);
        zoom2.setOnClickListener(v -> setZoom(2f));

        LinearLayout shutterRow = new LinearLayout(this);
        shutterRow.setGravity(Gravity.CENTER_VERTICAL);
        bottom.addView(shutterRow, new LinearLayout.LayoutParams(-1, dp(92)));
        TextView gallery = label("▧", 30, WHITE, Typeface.NORMAL);
        gallery.setGravity(Gravity.CENTER);
        gallery.setBackground(shape(0xCC353B40, dp(32), 0, 0));
        gallery.setContentDescription("최근 사진 보기");
        gallery.setOnClickListener(v -> openGallery());
        LinearLayout.LayoutParams galleryLp = new LinearLayout.LayoutParams(dp(57), dp(57));
        shutterRow.addView(gallery, galleryLp);
        View leftSpace = new View(this);
        shutterRow.addView(leftSpace, new LinearLayout.LayoutParams(0, dp(1), 1f));

        FrameLayout shutterOuter = new FrameLayout(this);
        shutterOuter.setBackground(shape(Color.TRANSPARENT, dp(45), WHITE, dp(3)));
        LinearLayout.LayoutParams so = new LinearLayout.LayoutParams(dp(85), dp(85));
        shutterRow.addView(shutterOuter, so);
        capture = label("", 12, WHITE, Typeface.BOLD);
        capture.setBackground(shape(WHITE, dp(33), 0, 0));
        shutterOuter.addView(capture,
            new FrameLayout.LayoutParams(dp(67), dp(67), Gravity.CENTER));
        shutterOuter.setContentDescription("사진 촬영 또는 동영상 녹화");
        shutterOuter.setOnClickListener(v -> onShutter());
        shutterRow.addView(new View(this),
            new LinearLayout.LayoutParams(0, dp(1), 1f));
        TextView flip = label("↻", 32, WHITE, Typeface.NORMAL);
        flip.setGravity(Gravity.CENTER);
        flip.setBackground(shape(0xCC353B40, dp(32), 0, 0));
        flip.setContentDescription("전면 후면 카메라 전환");
        flip.setOnClickListener(v -> switchCamera());
        shutterRow.addView(flip, new LinearLayout.LayoutParams(dp(57), dp(57)));

        LinearLayout modes = new LinearLayout(this);
        modes.setGravity(Gravity.CENTER);
        bottom.addView(modes, new LinearLayout.LayoutParams(-1, dp(35)));
        modePhoto = label("사진", 15, GOLD, Typeface.BOLD);
        modePhoto.setGravity(Gravity.CENTER);
        modes.addView(modePhoto, new LinearLayout.LayoutParams(dp(90), dp(32)));
        modeVideo = label("동영상", 15, MUTED, Typeface.NORMAL);
        modeVideo.setGravity(Gravity.CENTER);
        modes.addView(modeVideo, new LinearLayout.LayoutParams(dp(90), dp(32)));
        modePhoto.setOnClickListener(v -> changeMode(false));
        modeVideo.setOnClickListener(v -> changeMode(true));

        recordTimer = label("", 13, WHITE, Typeface.BOLD);
        recordTimer.setGravity(Gravity.CENTER);
        recordTimer.setBackground(shape(0xC3B5272F, dp(14), 0, 0));
        FrameLayout.LayoutParams timerLp =
            new FrameLayout.LayoutParams(dp(90), dp(29), Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        timerLp.topMargin = dp(96);
        recordTimer.setVisibility(View.GONE);
        root.addView(recordTimer, timerLp);

        // Kept for shared preset metadata; no space-consuming caption on capture UI.
        captureLabel = label("", 10, MUTED, Typeface.NORMAL);
        strengthSeek = null;
        exposureSeek = null;
        setInfo();
        root.post(this::updateCaptureFrame);
    }

    private void showSettings() {
        android.app.Dialog dialog = new android.app.Dialog(this);
        LinearLayout panel = new LinearLayout(this);
        panel.setPadding(dp(22), dp(18), dp(22), dp(26));
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackground(shape(0xFF1B2026, dp(25), 0, 0));
        TextView header = label("카메라 설정", 21, WHITE, Typeface.BOLD);
        panel.addView(header, new LinearLayout.LayoutParams(-1, dp(46)));
        TextView description = label("촬영 화면을 방해하지 않도록 설정을 분리했습니다.", 12, MUTED, Typeface.NORMAL);
        panel.addView(description, new LinearLayout.LayoutParams(-1, dp(33)));
        TextView ratioTitle = label("사진 · 동영상 비율", 14, WHITE, Typeface.BOLD);
        panel.addView(ratioTitle, new LinearLayout.LayoutParams(-1, dp(43)));

        LinearLayout ratios = new LinearLayout(this);
        ratios.setGravity(Gravity.CENTER);
        panel.addView(ratios, new LinearLayout.LayoutParams(-1, dp(54)));
        ratioButtons = new TextView[RATIOS.length];
        for (int i = 0; i < RATIOS.length; i++) {
            String ratio = RATIOS[i];
            TextView button = label(ratio, 14, WHITE, Typeface.BOLD);
            button.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(0, dp(48), 1f);
            rp.setMargins(dp(3), 0, dp(3), 0);
            ratios.addView(button, rp);
            ratioButtons[i] = button;
            button.setContentDescription(ratio + " 비율 사용");
            button.setOnClickListener(v -> {
                if (recording != null || busy) {
                    showMessage("촬영 중에는 비율을 바꿀 수 없습니다.");
                } else {
                    selectRatio(ratio);
                    dialog.dismiss();
                }
            });
        }
        updateRatioButtons();
        TextView ratioHint = label("9:16은 화면을 크게 채웁니다. 4:3은 센서 영역을 더 넓게 활용합니다.", 11, MUTED, Typeface.NORMAL);
        ratioHint.setGravity(Gravity.CENTER_VERTICAL);
        panel.addView(ratioHint, new LinearLayout.LayoutParams(-1, dp(53)));

        TextView gridOption = label(grid.getVisibility() == View.VISIBLE ? "⊞  격자 끄기" : "⊞  격자 켜기", 14, WHITE, Typeface.BOLD);
        gridOption.setPadding(dp(16), 0, dp(16), 0);
        gridOption.setBackground(shape(0xFF2C3238, dp(13), 0, 0));
        panel.addView(gridOption, new LinearLayout.LayoutParams(-1, dp(49)));
        gridOption.setOnClickListener(v -> {
            boolean enabled = grid.getVisibility() != View.VISIBLE;
            grid.setVisibility(enabled ? View.VISIBLE : View.GONE);
            gridOption.setText(enabled ? "⊞  격자 끄기" : "⊞  격자 켜기");
            getPreferences(MODE_PRIVATE).edit().putBoolean("grid", enabled).apply();
        });

        TextView filterTitle = label("필터 강도", 14, WHITE, Typeface.BOLD);
        LinearLayout.LayoutParams ftp = new LinearLayout.LayoutParams(-1, dp(35));
        ftp.topMargin = dp(14);
        panel.addView(filterTitle, ftp);
        SeekBar strength = new SeekBar(this);
        strength.setMax(100);
        strength.setProgress(Math.round(intensity * 100));
        strength.setProgressTintList(android.content.res.ColorStateList.valueOf(GOLD));
        strength.setThumbTintList(android.content.res.ColorStateList.valueOf(GOLD));
        panel.addView(strength, new LinearLayout.LayoutParams(-1, dp(39)));
        strength.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int v, boolean fromUser) {
                if (fromUser) filterTitle.setText("필터 강도  " + v + "%");
            }
            @Override public void onStartTrackingTouch(SeekBar bar) {}
            @Override public void onStopTrackingTouch(SeekBar bar) {
                if (recording == null) {
                    intensity = bar.getProgress() / 100f;
                    applyLook();
                } else showMessage("녹화 종료 후 필터를 변경할 수 있습니다.");
            }
        });
        TextView exposureTitle = label("노출 보정  " + String.format(Locale.US, "%+.1f", currentEv), 14, WHITE, Typeface.BOLD);
        panel.addView(exposureTitle, new LinearLayout.LayoutParams(-1, dp(34)));
        SeekBar exposure = new SeekBar(this);
        exposure.setMax(40);
        exposure.setProgress(Math.round(currentEv * 10f) + 20);
        exposure.setProgressTintList(android.content.res.ColorStateList.valueOf(GOLD));
        exposure.setThumbTintList(android.content.res.ColorStateList.valueOf(GOLD));
        panel.addView(exposure, new LinearLayout.LayoutParams(-1, dp(40)));
        exposure.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar b, int progress, boolean fromUser) {
                float ev = (progress - 20) / 10f;
                exposureTitle.setText("노출 보정  " + String.format(Locale.US, "%+.1f", ev));
                if (fromUser) {
                    currentEv = ev;
                    setExposureEv(ev);
                }
            }
            @Override public void onStartTrackingTouch(SeekBar b) {}
            @Override public void onStopTrackingTouch(SeekBar b) {}
        });
        TextView close = label("완료", 15, INK, Typeface.BOLD);
        close.setGravity(Gravity.CENTER);
        close.setBackground(shape(GOLD, dp(15), 0, 0));
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1, dp(48));
        cp.topMargin = dp(14);
        panel.addView(close, cp);
        close.setOnClickListener(v -> dialog.dismiss());
        dialog.setContentView(panel);
        android.view.Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.setDimAmount(.57f);
        }
        dialog.show();
        if (window != null) {
            window.setGravity(Gravity.BOTTOM);
            window.setLayout(-1, -2);
            window.setNavigationBarColor(INK);
        }
    }

    private void selectRatio(String ratio) {
        if (busy || recording != null) {
            showMessage("촬영 중에는 화면 비율을 바꿀 수 없습니다.");
            return;
        }
        if (ratio.equals(frameRatio)) return;
        unlockFocus();
        if (focusLocked || afAeLockPending) unlockFocus();
        frameRatio = ratio;
        getPreferences(MODE_PRIVATE).edit().putString("capture_ratio", ratio).apply();
        updateRatioButtons();
        updateCaptureFrame();
        setInfo();
        // Reattach after PreviewView receives its new size. CameraX uses this
        // viewport for photo/video crop rectangles, not merely a visual mask.
        preview.postDelayed(() -> {
            if (cameraProvider != null && recording == null) bindCameraUseCases();
        }, 130);
    }

    private void updateRatioButtons() {
        if (ratioButtons == null) return;
        for (int i = 0; i < RATIOS.length; i++) {
            boolean active = RATIOS[i].equals(frameRatio);
            ratioButtons[i].setTextColor(active ? INK : WHITE);
            ratioButtons[i].setBackground(shape(active ? GOLD : 0xB02D3033, dp(16),
                active ? 0 : 0x88FFFFFF, dp(1)));
        }
    }

    private void updateStageBounds() { updateCaptureFrame(); }

    private void updateCaptureFrame() {
        if (previewStage == null || previewFrame == null) return;
        int stageW = previewStage.getWidth(), stageH = previewStage.getHeight();
        if (stageW < dp(50) || stageH < dp(60)) return;
        boolean horizontal = getResources().getConfiguration().orientation
            == android.content.res.Configuration.ORIENTATION_LANDSCAPE;
        float widthOverHeight;
        switch (frameRatio) {
            case "9:16": widthOverHeight = horizontal ? 16f/9f : 9f/16f; break;
            case "1:1": widthOverHeight = 1f; break;
            case "3:2": widthOverHeight = horizontal ? 3f/2f : 2f/3f; break;
            default: widthOverHeight = horizontal ? 4f/3f : 3f/4f;
        }
        int maxW = Math.max(1, stageW - dp(2));
        int maxH = Math.max(1, stageH - dp(2));
        int width = Math.min(maxW, Math.round(maxH * widthOverHeight));
        int height = Math.min(maxH, Math.round(width / widthOverHeight));
        // Enforce an even viewport dimension for camera encoding/crop stability.
        width = Math.max(2, width & ~1);
        height = Math.max(2, height & ~1);
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) previewFrame.getLayoutParams();
        if (lp.width == width && lp.height == height) return;
        lp.width = width;
        lp.height = height;
        lp.gravity = Gravity.CENTER;
        previewFrame.setLayoutParams(lp);
    }

    private void selectLook(ColorLooks.Look look) {
        if (recording != null) { showMessage("녹화 중에는 필터를 변경할 수 없습니다."); return; }
        selected = look;
        for (int i = 0; i < presets.length; i++) {
            boolean active = ColorLooks.Look.values()[i] == look;
            presets[i].setTextColor(active ? INK : WHITE);
            presets[i].setBackground(shape(active ? GOLD : 0x661E2226, dp(16),
                active ? 0 : 0x33FFFFFF, dp(1)));
        }
        setInfo();
        applyLook();
    }

    private void applyLook() {
        if (media3 == null) return;
        try {
            if (selected == ColorLooks.Look.ORIGINAL || intensity == 0f)
                media3.setEffects(Collections.emptyList());
            else media3.setEffects(Collections.singletonList(ColorLooks.generate(selected, intensity)));
        } catch (Exception e) {
            Log.e(TAG, "Effect changed failed", e);
            showMessage("필터 적용 오류");
        }
    }

    private void setInfo() {
        info.setText(selected.label.toUpperCase(Locale.US) + "  ·  " + frameRatio + "  ·  " + (isVideo ? "VIDEO" : "PHOTO"));
        captureLabel.setText(selected.hint + "  ·  기기 내 처리");
    }

    private void changeMode(boolean video) {
        if (recording != null) return;
        isVideo = video;
        modePhoto.setTextColor(video ? MUTED : GOLD);
        modePhoto.setTypeface(null, video ? Typeface.NORMAL : Typeface.BOLD);
        modeVideo.setTextColor(video ? GOLD : MUTED);
        modeVideo.setTypeface(null, video ? Typeface.BOLD : Typeface.NORMAL);
        capture.setBackground(shape(video ? 0xFFFF5658 : WHITE, dp(34), 0, 0));
        setInfo();
    }

    private void onShutter() {
        if (boundCamera == null) { showMessage("카메라를 준비하는 중입니다."); return; }
        if (afAeLockPending) {
            showMessage("센서 잠금 결과를 확인한 뒤 촬영해 주세요.");
            return;
        }
        if (isVideo) {
            if (recording != null) {
                stopRecording();
            } else if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED && !microphonePermissionAsked) {
                microphonePermissionAsked = true;
                microphonePermission.launch(Manifest.permission.RECORD_AUDIO);
            } else {
                startRecording();
            }
        } else takePhoto();
    }

    private void takePhoto() {
        if (busy) return;
        busy = true;
        capture.setAlpha(.55f);
        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, mediaName("jpg"));
        values.put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg");
        values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/I photo");
        ImageCapture.OutputFileOptions options = new ImageCapture.OutputFileOptions.Builder(
            getContentResolver(), MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values).build();
        try {
            imageCapture.takePicture(options, ContextCompat.getMainExecutor(this), new ImageCapture.OnImageSavedCallback() {
                @Override public void onImageSaved(ImageCapture.OutputFileResults result) {
                    busy = false;
                    capture.setAlpha(1f);
                    lastMedia = result.getSavedUri();
                    showMessage("사진을 갤러리에 저장했습니다.");
                }
                @Override public void onError(ImageCaptureException exception) {
                    busy = false;
                    capture.setAlpha(1f);
                    Log.e(TAG, "Photo failed", exception);
                    showMessage("사진 촬영 실패: " + exception.getMessage());
                }
            });
        } catch (Exception e) {
            busy = false;
            capture.setAlpha(1f);
            Log.e(TAG, "Photo setup failed", e);
            showMessage("사진 촬영을 시작할 수 없습니다.");
        }
    }


    /** Single tap: autofocus. Long press: hold AF until tap/unlock/camera switch. */
    private void enableFocusGestures() {
        preview.setOnTouchListener((v,e)->{
            if(boundCamera==null)return false;
            switch(e.getActionMasked()){
                case MotionEvent.ACTION_DOWN:
                    touchX=e.getX();touchY=e.getY();
                    touchMoved=false;touchedLong=false;pinchActive=false;
                    draggingSun=focusLocked && isNearSun(touchX,touchY);
                    if(pendingFocusLock!=null)handler.removeCallbacks(pendingFocusLock);
                    if(draggingSun){
                        exposureDragStartY=touchY;
                        exposureDragStartEv=currentEv;
                        if(!exposureAdjustmentSupported)showMessage("이 기기는 노출 밝기 조정을 지원하지 않습니다.");
                        return true;
                    }
                    pendingFocusLock=()->{
                        touchedLong=true;
                        focusAt(touchX,touchY,true);
                    };
                    handler.postDelayed(pendingFocusLock,520);
                    return true;
                case MotionEvent.ACTION_POINTER_DOWN:
                    if(pendingFocusLock!=null)handler.removeCallbacks(pendingFocusLock);
                    draggingSun=false;
                    pinchActive=true;
                    lastPinchDistance=fingerDistance(e);
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if(draggingSun && e.getPointerCount()==1){
                        if(exposureAdjustmentSupported){
                            // Upward drag = positive exposure compensation, downward = negative.
                            float ev=exposureDragStartEv+(exposureDragStartY-e.getY())/dp(85f);
                            setExposureEv(ev);
                        }
                        return true;
                    }
                    if(e.getPointerCount()>=2){
                        float d=fingerDistance(e);
                        if(lastPinchDistance>10f && d>10f){
                            float desired=Math.max(1f,Math.min(6f,cameraZoom*d/lastPinchDistance));
                            setZoom(desired);
                        }
                        lastPinchDistance=d;
                    }else if(Math.abs(touchX-e.getX())>dp(13)||
                            Math.abs(touchY-e.getY())>dp(13)){
                        touchMoved=true;
                        if(pendingFocusLock!=null)handler.removeCallbacks(pendingFocusLock);
                    }
                    return true;
                case MotionEvent.ACTION_POINTER_UP:
                    if(pendingFocusLock!=null)handler.removeCallbacks(pendingFocusLock);
                    return true;
                case MotionEvent.ACTION_UP:
                    if(pendingFocusLock!=null)handler.removeCallbacks(pendingFocusLock);
                    if(draggingSun){draggingSun=false;return true;}
                    if(!touchMoved && !touchedLong && !pinchActive)
                        focusAt(e.getX(),e.getY(),false);
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    draggingSun=false;
                    if(pendingFocusLock!=null)handler.removeCallbacks(pendingFocusLock);
                    return true;
                default:return true;
            }
        });
    }

    private boolean isNearSun(float x,float y){
        if(exposureSunGuide==null || !focusLocked)return false;
        return Math.abs(x-sunX())<=dp(45)&&Math.abs(y-sunY())<=dp(112);
    }
    private float sunX(){
        float w=preview==null?dp(320):preview.getWidth();
        float chosen=focusX + (focusX>w*.68f?-dp(86):dp(86));
        return Math.max(dp(34),Math.min(w-dp(34),chosen));
    }
    private float sunY(){
        float h=preview==null?dp(550):preview.getHeight();
        return Math.max(dp(118),Math.min(h-dp(118),focusY));
    }

    private float fingerDistance(MotionEvent event){
        if(event.getPointerCount()<2)return 0f;
        float dx=event.getX(0)-event.getX(1),dy=event.getY(0)-event.getY(1);
        return (float)Math.hypot(dx,dy);
    }

    private boolean isAELockSupported(){
        if(boundCamera==null || boundCamera.getCameraInfo()==null)return false;
        try{
            Boolean b=Camera2CameraInfo.from(boundCamera.getCameraInfo())
                .getCameraCharacteristic(CameraCharacteristics.CONTROL_AE_LOCK_AVAILABLE);
            return Boolean.TRUE.equals(b);
        }catch(Exception e){
            Log.e(TAG,"AE lock characteristics unavailable",e);
            return false;
        }
    }

    private boolean isAWBLockSupported(){
        if(boundCamera==null || boundCamera.getCameraInfo()==null)return false;
        try{
            Boolean b=Camera2CameraInfo.from(boundCamera.getCameraInfo())
                .getCameraCharacteristic(CameraCharacteristics.CONTROL_AWB_LOCK_AVAILABLE);
            return Boolean.TRUE.equals(b);
        }catch(Exception e){return false;}
    }

    private void focusAt(float x,float y,boolean lock){
        if(boundCamera==null||boundCamera.getCameraControl()==null||
            preview.getWidth()<2||preview.getHeight()<2)return;
        if(focusLocked || aeLocked || afAeLockPending)unlockFocus();
        x=Math.max(1,Math.min(preview.getWidth()-1,x));
        y=Math.max(1,Math.min(preview.getHeight()-1,y));
        focusX=x;focusY=y;
        final long serial=++focusRequestSerial;
        final float targetX=x,targetY=y;
        if(lock && !manualSensorAvailable && !isAELockSupported()){
            showMessage("이 카메라는 수동 노출·AE 잠금을 지원하지 않습니다.");
            lock=false;
        }
        final boolean requestedLock=lock;
        try{
            MeteringPoint point=preview.getMeteringPointFactory().createPoint(x,y);
            FocusMeteringAction.Builder builder=new FocusMeteringAction.Builder(
                point, FocusMeteringAction.FLAG_AF|FocusMeteringAction.FLAG_AE|
                    FocusMeteringAction.FLAG_AWB);
            if(requestedLock)builder.disableAutoCancel();
            ListenableFuture<FocusMeteringResult> focusFuture=
                boundCamera.getCameraControl().startFocusAndMetering(builder.build());
            afAeLockPending=requestedLock;
            if(requestedLock && focusButton!=null)focusButton.setText("초점 조정 중");
            if(!requestedLock){
                showFocusTarget(x,y);
            }else{
                focusFuture.addListener(()->{
                    if(serial!=focusRequestSerial || !afAeLockPending)return;
                    try{
                        FocusMeteringResult res=focusFuture.get();
                        if(!res.isFocusSuccessful()){
                            afAeLockPending=false;
                            showMessage("초점을 확정하지 못했습니다. 다시 길게 눌러주세요.");
                            boundCamera.getCameraControl().cancelFocusAndMetering();
                            showFocusTarget(targetX,targetY);
                            return;
                        }
                        // Let AE/AWB metering settle at the original subject before
                        // freezing Camera2 request state.
                        handler.postDelayed(()->applyHardwareLock(serial,targetX,targetY),180);
                    }catch(Exception e){
                        if(serial!=focusRequestSerial)return;
                        afAeLockPending=false;
                        Log.e(TAG,"AF metering error",e);
                        showMessage("초점 고정 실패. 다시 시도해 주세요.");
                        if(focusButton!=null)focusButton.setText("AF");
                    }
                },ContextCompat.getMainExecutor(this));
            }
        }catch(Exception err){
            afAeLockPending=false;
            Log.e(TAG,"Focus failed",err);
            showMessage("초점 조정을 시작할 수 없습니다.");
        }
    }

    private void applyHardwareLock(long serial,float x,float y){
        if(serial!=focusRequestSerial || !afAeLockPending || boundCamera==null||
            boundCamera.getCameraControl()==null)return;
        final boolean awb=isAWBLockSupported();
        final int cameraEpoch=currentCameraEpoch;
        try{
            CaptureRequestOptions.Builder options=new CaptureRequestOptions.Builder();
            boolean manual=manualSensorAvailable &&
                supportedISO!=null && supportedShutterNs!=null &&
                lastSensorISO>0 && lastSensorExposureNs>0 &&
                android.os.SystemClock.elapsedRealtime()-lastCaptureTimeMs<1200;
            if(manual){
                baselineISO=Math.max(supportedISO.getLower(), Math.min(
                    supportedISO.getUpper(), lastSensorISO));
                baselineShutterNs=Math.max(supportedShutterNs.getLower(), Math.min(
                    supportedShutterNs.getUpper(), lastSensorExposureNs));
                requestedISO=baselineISO;
                requestedShutterNs=baselineShutterNs;
                manualFocusRequested=canManuallySetFocus();
                if(manualFocusRequested){
                    targetFocusDistance=lastLensFocusDistance;
                    options.setCaptureRequestOption(CaptureRequest.CONTROL_AF_MODE,
                        CaptureRequest.CONTROL_AF_MODE_OFF);
                    options.setCaptureRequestOption(CaptureRequest.LENS_FOCUS_DISTANCE,
                        targetFocusDistance);
                }
                options.setCaptureRequestOption(CaptureRequest.CONTROL_AE_MODE,
                    CaptureRequest.CONTROL_AE_MODE_OFF);
                options.setCaptureRequestOption(CaptureRequest.SENSOR_SENSITIVITY,requestedISO);
                options.setCaptureRequestOption(CaptureRequest.SENSOR_EXPOSURE_TIME,requestedShutterNs);
                hardLockRequested=true;
                fallbackLockRequested=false;
            }else if(isAELockSupported()){
                options.setCaptureRequestOption(CaptureRequest.CONTROL_AE_LOCK,true);
                hardLockRequested=false;
                fallbackLockRequested=true;
            }else{
                afAeLockPending=false;
                if(focusButton!=null)focusButton.setText("AF");
                showMessage("이 기기는 하드웨어 노출 잠금을 지원하지 않습니다.");
                return;
            }
            if(awb)options.setCaptureRequestOption(CaptureRequest.CONTROL_AWB_LOCK,true);
            awbLocked=awb;
            allowSensorSettleUntilMs=android.os.SystemClock.elapsedRealtime()+220;
            consecutiveLockFrames=0;
            mismatchFrames=0;
            ListenableFuture<Void> applied=Camera2CameraControl
                .from(boundCamera.getCameraControl()).addCaptureRequestOptions(options.build());
            applied.addListener(()->{
                if(serial!=focusRequestSerial || cameraEpoch!=currentCameraEpoch)return;
                try{applied.get();}
                catch(Exception e){
                    Log.e(TAG,"Apply sensor exposure failed",e);
                    failPendingHardwareLock(serial,"카메라 노출 고정 적용 실패");
                }
            },ContextCompat.getMainExecutor(this));
            handler.postDelayed(()->{
                if(serial==focusRequestSerial && cameraEpoch==currentCameraEpoch
                    && afAeLockPending)failPendingHardwareLock(serial,
                        "프레임에서 고정이 확인되지 않아 잠금을 취소했습니다.");
            },2500);
        }catch(Exception e){
            Log.e(TAG,"Cannot apply exposure lock",e);
            failPendingHardwareLock(serial,"노출 잠금이 실패했습니다.");
        }
    }

    private void failPendingHardwareLock(long serial,String message){
        if(serial!=focusRequestSerial)return;
        unlockFocus();
        showMessage(message);
    }

    private boolean canAdjustExposure(){
        if(boundCamera==null || boundCamera.getCameraInfo()==null)return false;
        ExposureState state=boundCamera.getCameraInfo().getExposureState();
        return state!=null && state.isExposureCompensationSupported() &&
            state.getExposureCompensationStep().floatValue()>0f &&
            state.getExposureCompensationRange().getUpper()>
                state.getExposureCompensationRange().getLower();
    }

    private void unlockFocus(){
        final boolean hadAE=aeLocked || afAeLockPending ||
            hardLockVerified || hardLockRequested || fallbackLockRequested;
        ++focusRequestSerial;
        afAeLockPending=false;focusLocked=false;aeLocked=false;awbLocked=false;
        hardLockRequested=false;hardLockVerified=false;fallbackLockRequested=false;
        manualFocusRequested=false;consecutiveLockFrames=0;mismatchFrames=0;
        draggingSun=false;
        if(boundCamera!=null && boundCamera.getCameraControl()!=null){
            try{
                if(hadAE){
                    Camera2CameraControl.from(boundCamera.getCameraControl())
                        .clearCaptureRequestOptions();
                }
                boundCamera.getCameraControl().cancelFocusAndMetering();
            }catch(Exception e){Log.w(TAG,"AE/AF unlock error",e);}
        }
        if(focusIndicator!=null)focusIndicator.setVisibility(View.GONE);
        if(exposureSunGuide!=null)exposureSunGuide.setVisibility(View.GONE);
        if(focusButton!=null){
            focusButton.setText("AF");
            focusButton.setTextColor(WHITE);
            focusButton.setBackground(shape(0x663A3837,dp(18),0x44FFFFFF,dp(1)));
        }
    }

    private void showFocusTarget(float x,float y){
        if(focusIndicator!=null){
            FrameLayout.LayoutParams lp=(FrameLayout.LayoutParams)focusIndicator.getLayoutParams();
            lp.leftMargin=Math.round(x)-dp(28);
            lp.topMargin=Math.round(y)-dp(28);
            focusIndicator.setLayoutParams(lp);
            focusIndicator.setText(focusLocked?"L":"+");
            focusIndicator.setVisibility(View.VISIBLE);
            if(!focusLocked)handler.postDelayed(()->{
                if(!focusLocked && focusIndicator!=null)
                    focusIndicator.setVisibility(View.GONE);
            },1450);
        }
        if(focusButton!=null){
            focusButton.setText(focusLocked?"AE/AF":"AF");
            focusButton.setTextColor(focusLocked?INK:WHITE);
            focusButton.setBackground(shape(focusLocked?GOLD:0x663A3837,
                dp(18),0x44FFFFFF,dp(1)));
        }
        if(exposureSunGuide!=null){
            exposureSunGuide.setVisibility(focusLocked?View.VISIBLE:View.GONE);
            exposureSunGuide.invalidate();
        }
    }

    private void startRecording() {
        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, mediaName("mp4"));
        values.put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4");
        values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/I photo");
        MediaStoreOutputOptions options = new MediaStoreOutputOptions.Builder(
            getContentResolver(), MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
            .setContentValues(values).build();
        boolean hasMic = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            == PackageManager.PERMISSION_GRANTED;
        try {
            PendingRecording pending = videoRecorder.prepareRecording(this, options);
            if (hasMic) pending = pending.withAudioEnabled();
            recording = pending.start(ContextCompat.getMainExecutor(this), event -> {
                    if (event instanceof VideoRecordEvent.Start) {
                        startedAt = android.os.SystemClock.elapsedRealtime();
                        recordTimer.setVisibility(View.VISIBLE);
                        handler.post(tick);
                        capture.setBackground(shape(0xFFE74C4C, dp(7), 0, 0));
                    } else if (event instanceof VideoRecordEvent.Finalize) {
                        VideoRecordEvent.Finalize end = (VideoRecordEvent.Finalize) event;
                        handler.removeCallbacks(tick);
                        recordTimer.setText("");
                        recordTimer.setVisibility(View.GONE);
                        capture.setBackground(shape(0xFFFF5658, dp(34), 0, 0));
                        recording = null;
                        if (!end.hasError()) {
                            lastMedia = end.getOutputResults().getOutputUri();
                            showMessage("영상이 갤러리에 저장되었습니다.");
                        } else {
                            Log.e(TAG, "Video error " + end.getError());
                            showMessage("영상 저장 오류: " + end.getError());
                        }
                    }
                });
        } catch (Exception e) {
            Log.e(TAG, "Video failed", e);
            recording = null;
            showMessage("영상 녹화를 시작할 수 없습니다.");
        }
    }

    private void stopRecording() {
        if (recording != null) {
            Recording running = recording;
            // Do not set recording=null until Finalize to prevent double starts.
            try { running.stop(); } catch (Exception e) { Log.e(TAG, "Stop recording", e); }
        }
    }

    private void setExposureEv(float ev) {
        if(boundCamera==null || boundCamera.getCameraInfo()==null)return;
        if(hardLockVerified){
            // Camera2 SENSOR values are manual: CameraX exposure compensation
            // would not work, so modify the FIXED shutter/ISO instead.
            adjustLockedSensorExposure(ev-exposureLockBaseEv);
            return;
        }
        ExposureState state=boundCamera.getCameraInfo().getExposureState();
        if(state==null || !state.isExposureCompensationSupported())return;
        float step=state.getExposureCompensationStep().floatValue();
        if(step<=0f)return;
        Range<Integer> range=state.getExposureCompensationRange();
        int index=Math.max(range.getLower(),Math.min(range.getUpper(),Math.round(ev/step)));
        currentEv=index*step;
        if(exposureSunGuide!=null)exposureSunGuide.invalidate();
        if(lastExposureIndex==index)return;
        lastExposureIndex=index;
        try{boundCamera.getCameraControl().setExposureCompensationIndex(index);}
        catch(Exception e){Log.e(TAG,"AE compensation failed",e);lastExposureIndex=Integer.MIN_VALUE;}
    }

    private void adjustLockedSensorExposure(float ev){
        if(!hardLockVerified || supportedISO==null || supportedShutterNs==null ||
            boundCamera==null || baselineISO<=0 || baselineShutterNs<=0)return;
        double desiredGain=Math.pow(2d,Math.max(-3d,Math.min(3d,ev)));
        // Preserve shutter time when possible, adjusting ISO first.
        // If ISO range runs out, adjust shutter within safe device limits.
        final double exposureProduct=baselineISO*(double)baselineShutterNs*desiredGain;
        long shutter=baselineShutterNs;
        int iso=(int)Math.round(exposureProduct/shutter);
        iso=Math.max(supportedISO.getLower(),Math.min(supportedISO.getUpper(),iso));
        shutter=Math.round(exposureProduct/Math.max(1,iso));
        long maxVideoShutter=Math.min(supportedShutterNs.getUpper(),33000000L);
        shutter=Math.max(supportedShutterNs.getLower(),Math.min(maxVideoShutter,shutter));
        // Recompute ISO in case shutter had to be clamped.
        iso=(int)Math.round(exposureProduct/Math.max(1,shutter));
        iso=Math.max(supportedISO.getLower(),Math.min(supportedISO.getUpper(),iso));
        requestedISO=iso;
        requestedShutterNs=shutter;
        double actualGain=iso*(double)shutter/(baselineISO*(double)baselineShutterNs);
        manualExposureDeltaEV=(float)(Math.log(Math.max(.001,actualGain))/Math.log(2));
        currentEv=exposureLockBaseEv+manualExposureDeltaEV;
        if(exposureSunGuide!=null)exposureSunGuide.invalidate();
        long now=android.os.SystemClock.elapsedRealtime();
        if(now-manualExposureLastUpdateMs<75)return;
        manualExposureLastUpdateMs=now;
        allowSensorSettleUntilMs=now+270;
        CaptureRequestOptions options=new CaptureRequestOptions.Builder()
            .setCaptureRequestOption(CaptureRequest.SENSOR_SENSITIVITY,requestedISO)
            .setCaptureRequestOption(CaptureRequest.SENSOR_EXPOSURE_TIME,requestedShutterNs)
            .build();
        try{
            Camera2CameraControl.from(boundCamera.getCameraControl())
                .addCaptureRequestOptions(options);
        }catch(Exception e){Log.e(TAG,"Manual exposure adjust failed",e);}
    }

    private void setZoom(float ratio) {
        if (boundCamera == null) return;
        try {
            boundCamera.getCameraControl().setZoomRatio(ratio);
            cameraZoom = ratio;
        }
        catch (Exception ignored) { showMessage("이 배율은 지원되지 않습니다."); }
    }

    private void switchCamera() {
        if (boundCamera == null || recording != null || busy) return;
        try {
            unlockFocus();
            lastExposureIndex = Integer.MIN_VALUE;
            isFront = !isFront;
            bindCameraUseCases();
        } catch (Exception e) {
            isFront = !isFront;
            showMessage("이 카메라를 사용할 수 없습니다.");
        }
    }

    private void toggleFlash() {
        if (boundCamera == null) return;
        flash = !flash;
        flashButton.setText(flash ? "⚡ 켬" : "⚡ 끔");
        if (imageCapture != null) imageCapture.setFlashMode(flash ? ImageCapture.FLASH_MODE_ON : ImageCapture.FLASH_MODE_OFF);
        // Video uses torch rather than still-image flash.
        try { boundCamera.getCameraControl().enableTorch(flash && isVideo); } catch (Exception ignored) {}
    }

    private void openGallery() {
        Intent intent;
        if (lastMedia != null) {
            intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(lastMedia, isLastVideo() ? "video/mp4" : "image/jpeg");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } else {
            intent = new Intent(Intent.ACTION_VIEW, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        }
        try { startActivity(intent); }
        catch (Exception e) { showMessage("갤러리 앱을 찾을 수 없습니다."); }
    }

    private boolean isLastVideo() {
        if (lastMedia == null) return false;
        String mime = getContentResolver().getType(lastMedia);
        return mime != null && mime.startsWith("video/");
    }

    private boolean hasCamera() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
    }

    private String mediaName(String extension) {
        return "Iphoto_" + new SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(new Date()) + "." + extension;
    }

    private TextView label(String text, int sp, int color, int style) {
        TextView result = new TextView(this);
        result.setText(text);
        result.setTextColor(color);
        result.setTextSize(sp);
        result.setTypeface(Typeface.create("sans-serif", style));
        result.setGravity(Gravity.CENTER_VERTICAL);
        result.setIncludeFontPadding(false);
        return result;
    }

    private TextView pill(String text) {
        TextView value = label(text, 11, WHITE, Typeface.BOLD);
        value.setPadding(dp(13), 0, dp(13), 0);
        value.setGravity(Gravity.CENTER);
        value.setBackground(shape(0x663A3837, dp(18), 0x44FFFFFF, dp(1)));
        value.setHeight(dp(36));
        return value;
    }

    private GradientDrawable shape(int fill, int radius, int border, int borderWidth) {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(fill);
        bg.setCornerRadius(radius);
        if (borderWidth > 0) bg.setStroke(borderWidth, border);
        return bg;
    }

    private int dp(float v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    private void showMessage(String text) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
    }

    @Override protected void onDestroy() {
        handler.removeCallbacks(tick);
        if (pendingFocusLock != null) handler.removeCallbacks(pendingFocusLock);
        if (focusLocked || aeLocked || afAeLockPending) unlockFocus();
        if (recording != null) recording.stop();
        if (cameraProvider != null) cameraProvider.unbindAll();
        if (media3 != null) media3.close();
        super.onDestroy();
    }

    private final class ExposureSunGuide extends View {
        private final Paint pen=new Paint(Paint.ANTI_ALIAS_FLAG);
        ExposureSunGuide(){super(MainActivity.this);}
        @Override protected void onDraw(Canvas canvas){
            if(!focusLocked || getWidth()<dp(80) || getHeight()<dp(160))return;
            float x=sunX(),y=sunY(), r=dp(10);
            float deltaY=Math.max(-dp(72),Math.min(dp(72),
                -(currentEv-exposureLockBaseEv)*dp(36)));
            float knobY=y+deltaY;
            // Subtle dotted exposure ruler, with a movable sunlight icon.
            pen.setStyle(Paint.Style.STROKE);
            pen.setStrokeWidth(dp(1.6f));
            pen.setColor(0xDFE6DDCC);
            pen.setPathEffect(new DashPathEffect(new float[]{dp(1.5f),dp(6)},0));
            canvas.drawLine(x,y-dp(89),x,y+dp(89),pen);
            pen.setPathEffect(null);
            pen.setStyle(Paint.Style.FILL);
            pen.setColor(0xB9090B0F);
            canvas.drawCircle(x,knobY,dp(22),pen);
            pen.setStyle(Paint.Style.STROKE);
            pen.setStrokeWidth(dp(2.1f));
            pen.setColor(GOLD);
            canvas.drawCircle(x,knobY,r,pen);
            for(int i=0;i<8;i++){
                double angle=2*Math.PI*i/8;
                float dx=(float)Math.cos(angle),dy=(float)Math.sin(angle);
                canvas.drawLine(x+dx*dp(14),knobY+dy*dp(14),
                    x+dx*dp(20),knobY+dy*dp(20),pen);
            }
            pen.setStyle(Paint.Style.FILL);
            pen.setColor(WHITE);
            pen.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));
            pen.setTextAlign(Paint.Align.CENTER);
            pen.setTextSize(dp(11));
            canvas.drawText(String.format(Locale.US,"%+.1f EV",currentEv-exposureLockBaseEv),
                x,Math.max(dp(20),y-dp(99)),pen);
            pen.setTextSize(dp(10));
            float tx=Math.max(dp(75),Math.min(getWidth()-dp(75),focusX));
            float ty=Math.max(dp(16),focusY-dp(44));
            canvas.drawText(hardLockVerified ? "ISO / SHUTTER HOLD" :
                "AE LOCK (LIMITED)",tx,ty,pen);
            if(hardLockVerified && requestedShutterNs>0){
                String sensorState="ISO "+requestedISO+"  ·  1/"+
                    Math.max(1,Math.round(1000000000d/requestedShutterNs))+"s";
                float secondLine=Math.min(getHeight()-dp(15),ty+dp(16));
                canvas.drawText(sensorState,tx,secondLine,pen);
            }
        }
    }

    private final class FrameGuide extends View {
        final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        FrameGuide() { super(MainActivity.this); setClickable(false); setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO); }
        @Override protected void onDraw(Canvas canvas) {
            float w = getWidth(), h = getHeight();
            if (w <= 0 || h <= 0) return;
            float inset = dp(1.5f);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(1.1f));
            paint.setColor(0x90EDE9E2);
            canvas.drawRect(inset, inset, w-inset, h-inset, paint);
            paint.setStrokeWidth(dp(2.5f));
            paint.setColor(0xB9DFC39A);
            float d = dp(21), p = inset + dp(1);
            canvas.drawLine(p, p, p+d, p, paint);
            canvas.drawLine(p, p, p, p+d, paint);
            canvas.drawLine(w-p, p, w-p-d, p, paint);
            canvas.drawLine(w-p, p, w-p, p+d, paint);
            canvas.drawLine(p, h-p, p+d, h-p, paint);
            canvas.drawLine(p, h-p, p, h-p-d, paint);
            canvas.drawLine(w-p, h-p, w-p-d, h-p, paint);
            canvas.drawLine(w-p, h-p, w-p, h-p-d, paint);
            paint.setStyle(Paint.Style.FILL);
        }
    }

    private final class GridOverlay extends View {
        final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        GridOverlay() { super(MainActivity.this); setClickable(false); }
        @Override protected void onDraw(Canvas c) {
            paint.setColor(0x78FFFFFF);
            paint.setStrokeWidth(dp(.6f));
            float w = getWidth(), h = getHeight();
            for (int i = 1; i <= 2; i++) {
                c.drawLine(w * i / 3f, 0, w * i / 3f, h, paint);
                c.drawLine(0, h * i / 3f, w, h * i / 3f, paint);
            }
        }
    }
}
