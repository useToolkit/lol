package com.usetoolkit.iphoto;

import android.Manifest;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.animation.ValueAnimator;
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
import android.media.MediaMetadataRetriever;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.util.Range;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
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
import androidx.camera.core.CameraInfo;
import androidx.camera.core.ZoomState;
import androidx.camera.core.DynamicRange;
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
import androidx.camera.video.VideoCapabilities;
import androidx.camera.video.MediaStoreOutputOptions;
import androidx.camera.video.Quality;
import androidx.camera.video.QualitySelector;
import androidx.camera.video.Recording;
import androidx.camera.video.VideoRecordEvent;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.extensions.ExtensionsManager;
import androidx.camera.extensions.ExtensionMode;
import androidx.camera.view.LifecycleCameraController;
import androidx.camera.view.PreviewView;
import androidx.camera.view.video.AudioConfig;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;

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
    private int flickerPreference = FlickerGuard.AUTO;
    private volatile int measuredFlickerHz = 0;
    private volatile boolean lockConfirmationPosted = false;
    private volatile boolean lockWarningPosted = false;
    private boolean filterFallback = false;
    private boolean photoOnlyFallback = false;
    private boolean destroyed = false;
    private int previewRetries = 0;
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
    private volatile boolean focusLocked = false;
    private boolean touchMoved = false, touchedLong = false, pinchActive = false;
    private float focusX = -1f, focusY = -1f, touchX, touchY, lastPinchDistance, cameraZoom = 1f;
    private float zoomMin=1f,zoomMax=6f;
    private LinearLayout zoomRow;
    private ZoomDialView zoomDial;
    private TextView[] zoomButtons=new TextView[0];
    private float[] zoomStops=new float[0];
    private LiveData<ZoomState> zoomStateLiveData;
    private Observer<ZoomState> zoomStateObserver;
    private ValueAnimator zoomAnimator;
    private Runnable zoomHideTask, zoomHoldTask;
    private boolean zoomHoldActive=false, zoomTouchMoved=false;
    private float zoomTouchBeginX, zoomHoldBaseRatio;
    private long lastZoomSentAt=0L;
    private Runnable pendingFocusLock;
    private ExposureSunGuide exposureSunGuide;
    private boolean aeLocked = false;
    private volatile boolean afAeLockPending = false;
    private boolean draggingSun = false;
    private boolean awbLocked = false;
    private boolean exposureAdjustmentSupported = false;
    private volatile long focusRequestSerial = 0L;
    private float exposureDragStartY, exposureDragStartEv, exposureLockBaseEv;
    private int lastExposureIndex = Integer.MIN_VALUE;
    private String frameRatio = "9:16";
    private float currentEv = -.5f;
    private static final String[] RATIOS = {"4:3", "9:16", "1:1", "3:2"};
    private Media3Effect media3;
    private Recording recording;
    private Uri lastMedia;
    private boolean isVideo = false;
    private String videoQualitySetting = "FHD";
    private int videoFpsSetting = 30;
    private boolean stabilizationRequested = true;
    // 0 OFF, 1 Standard (recording EIS / supported OIS), 2 Enhanced
    // (WYSIWYG preview + recording EIS when supported).
    private int stabilizationMode = 1;
    private boolean previewStabilizationRequested = false;
    private boolean opticalStabilizationAvailable = false;
    private boolean opticalStabilizationRequested = false;
    private volatile int actualEisMode = CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_OFF;
    private volatile int actualOisMode = CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_OFF;
    private TextView stabilizationBadge;
    private long lastStabUiMillis = 0L;
    private String effectiveVideoQuality = "FHD";
    private int effectiveVideoFps = 30;
    private boolean effectiveStabilization = false;
    private boolean videoCompatibilityFallback = false;
    private boolean video60Supported = false;
    private ExtensionsManager extensionsManager;
    private boolean portraitBokehRequested = false;
    private boolean portraitBokehActive = false;
    private boolean videoCapabilitiesFallbackNotified = false;
    private boolean proMode = false;
    private TextView modePro, proTelemetry;
    private ColorLooks.Scene scenePreference = ColorLooks.Scene.AUTO;
    private ColorLooks.Scene sceneResolved = ColorLooks.Scene.DAYLIGHT;
    private volatile long lastSceneSwitchMs = 0L;
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
        portraitBokehRequested=getPreferences(MODE_PRIVATE).getBoolean("portrait_bokeh",false);
        videoQualitySetting=getPreferences(MODE_PRIVATE).getString("video_quality","FHD");
        if(!videoQualitySetting.equals("UHD")&&!videoQualitySetting.equals("FHD")&&
           !videoQualitySetting.equals("HD"))videoQualitySetting="FHD";
        videoFpsSetting=getPreferences(MODE_PRIVATE).getInt("video_fps",30)==60?60:30;
        stabilizationRequested=getPreferences(MODE_PRIVATE).getBoolean("video_stabilization",true);
        stabilizationMode=getPreferences(MODE_PRIVATE).getInt("stabilization_mode",
            stabilizationRequested?1:0);
        if(stabilizationMode<0 || stabilizationMode>2) stabilizationMode=1;
        stabilizationRequested=stabilizationMode>0;
        try{
            scenePreference=ColorLooks.Scene.valueOf(
                getPreferences(MODE_PRIVATE).getString("scene_mode","AUTO"));
        }catch(IllegalArgumentException e){scenePreference=ColorLooks.Scene.AUTO;}
        sceneResolved=scenePreference==ColorLooks.Scene.AUTO?
            ColorLooks.Scene.DAYLIGHT:scenePreference;
        intensity=getPreferences(MODE_PRIVATE).getFloat("filter_intensity",.82f);
        flickerPreference = getPreferences(MODE_PRIVATE).getInt("flicker_preference", FlickerGuard.AUTO);
        if(flickerPreference!=FlickerGuard.AUTO && flickerPreference!=FlickerGuard.HZ50 &&
           flickerPreference!=FlickerGuard.HZ60 && flickerPreference!=FlickerGuard.OFF)
           flickerPreference=FlickerGuard.AUTO;
        try {
            selected = ColorLooks.Look.valueOf(
                getPreferences(MODE_PRIVATE).getString("selected_look", "LUXURY"));
        } catch (IllegalArgumentException ignored) {
            selected = ColorLooks.Look.LUXURY;
        }
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
                    runOnUiThread(()->{
                        if(destroyed || filterFallback)return;
                        filterFallback=true;
                        showMessage("필터 처리 오류 · 기본 카메라 화면으로 복구합니다.");
                        if(recording==null)preview.postDelayed(this::bindCameraUseCases,200);
                    });
                });
            media3.setEffects(Collections.singletonList(
                ColorLooks.generate(selected,intensity,sceneResolved)));
            enableFocusGestures();
            ListenableFuture<ProcessCameraProvider> future = ProcessCameraProvider.getInstance(this);
            future.addListener(() -> {
                try {
                    cameraProvider = future.get();
                    preview.post(this::bindCameraUseCases);
                    // Extensions can initialize asynchronously without delaying camera start.
                    try {
                        ListenableFuture<ExtensionsManager> ext=ExtensionsManager.getInstanceAsync(
                            this,cameraProvider);
                        ext.addListener(()->{
                            try {
                                extensionsManager=ext.get();
                                if(portraitBokehRequested && recording==null && !destroyed)
                                    preview.post(this::bindCameraUseCases);
                            } catch(Exception e){
                                Log.w(TAG,"OEM portrait extensions unavailable",e);
                            }
                        },ContextCompat.getMainExecutor(this));
                    } catch(Exception e) {
                        Log.w(TAG,"CameraX extensions init failed",e);
                    }
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
        if(destroyed)return;
        if (cameraProvider == null || preview.getWidth() == 0 || preview.getHeight() == 0) {
            if(++previewRetries <= 25) preview.postDelayed(this::bindCameraUseCases, 110);
            else showMessage("카메라 미리보기를 열지 못했습니다. 앱을 재실행해 주세요.");
            return;
        }
        previewRetries=0;
        if (recording != null) return;
        try {
            if (focusLocked || aeLocked || afAeLockPending) unlockFocus();
            ++currentCameraEpoch;
            final int boundEpoch=currentCameraEpoch;
            lockConfirmationPosted=false;
            lockWarningPosted=false;
            hardLockRequested = false;
            hardLockVerified = false;
            fallbackLockRequested = false;
            manualSensorAvailable = false;
            consecutiveLockFrames = 0;
            mismatchFrames = 0;
            boundCamera = null;
            portraitBokehActive=false;
            cancelZoomAnimation();
            detachZoomState();
            cameraProvider.unbindAll();
            lastCaptureTimeMs = 0;
            lastSensorExposureNs = 0;
            lastSensorISO = 0;
            lastLensFocusDistance = -1f;
            lastResultAeMode = -1;
            if (portraitBokehRequested && !isFront && !isVideo && extensionsManager!=null) {
                try {
                    if(extensionsManager.isExtensionAvailable(
                        CameraSelector.DEFAULT_BACK_CAMERA,ExtensionMode.BOKEH)){
                        Preview bokehPreview=new Preview.Builder().build();
                        bokehPreview.setSurfaceProvider(preview.getSurfaceProvider());
                        ImageCapture bokehPhoto=new ImageCapture.Builder()
                            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY).build();
                        bokehPhoto.setFlashMode(flash ?
                            ImageCapture.FLASH_MODE_ON:ImageCapture.FLASH_MODE_OFF);
                        CameraSelector bokehSelector=extensionsManager.getExtensionEnabledCameraSelector(
                            CameraSelector.DEFAULT_BACK_CAMERA,ExtensionMode.BOKEH);
                        boundCamera=cameraProvider.bindToLifecycle(this,
                            bokehSelector,bokehPreview,bokehPhoto);
                        previewUseCase=bokehPreview;imageCapture=bokehPhoto;
                        attachZoomState();
                        videoRecorder=null;videoCapture=null;
                        portraitBokehActive=true;
                        setInfo();
                        showMessage("제조사 인물 보케 적용 · 사진 모드만 지원하며 색감 LUT는 잠시 해제됩니다.");
                        return;
                    } else {
                        portraitBokehRequested=false;
                        getPreferences(MODE_PRIVATE).edit().putBoolean("portrait_bokeh",false).apply();
                        showMessage("이 갤럭시 카메라는 인물 배경 흐림 확장을 지원하지 않습니다.");
                    }
                } catch(Exception error){
                    portraitBokehRequested=false;
                    getPreferences(MODE_PRIVATE).edit().putBoolean("portrait_bokeh",false).apply();
                    Log.w(TAG,"OEM Bokeh failed, returning to standard",error);
                    cameraProvider.unbindAll();
                    showMessage("인물 배경 흐림을 사용할 수 없어 일반 촬영으로 복구합니다.");
                }
            }
            CameraSelector selector=isFront?
                CameraSelector.DEFAULT_FRONT_CAMERA:CameraSelector.DEFAULT_BACK_CAMERA;
            CameraInfo selectedInfo=selector.filter(cameraProvider.getAvailableCameraInfos()).get(0);
            opticalStabilizationAvailable=false;
            try {
                int[] oisModes=Camera2CameraInfo.from(selectedInfo)
                    .getCameraCharacteristic(CameraCharacteristics.LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION);
                if(oisModes!=null) for(int m:oisModes)
                    if(m==CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_ON)
                        opticalStabilizationAvailable=true;
            }catch(Exception e){Log.w(TAG,"Cannot inspect OIS availability",e);}
            // Enhanced stabilization is supported only on Android 13+,
            // and requested for FHD/30 or lower, avoiding CameraX conflicts.
            boolean previewStabCapable=false;
            try {
                previewStabCapable=android.os.Build.VERSION.SDK_INT>=33 &&
                    Preview.getPreviewCapabilities(selectedInfo).isStabilizationSupported();
            }catch(Exception e){Log.w(TAG,"Cannot inspect preview stabilization",e);}
            Preview.Builder pb = new Preview.Builder();
            previewStabilizationRequested=false;
            if(stabilizationMode==2 && !photoOnlyFallback &&
               !videoCompatibilityFallback &&
               videoFpsSetting==30 && !videoQualitySetting.equals("UHD") &&
               previewStabCapable){
                pb.setPreviewStabilizationEnabled(true);
                previewStabilizationRequested=true;
            }
            new Camera2Interop.Extender<>(pb).setSessionCaptureCallback(
                new CameraCaptureSession.CaptureCallback() {
                    @Override
                    public void onCaptureCompleted(android.hardware.camera2.CameraCaptureSession session,
                            CaptureRequest request, TotalCaptureResult result) {
                        if(boundEpoch!=currentCameraEpoch || destroyed)return;
                        Long exp = result.get(CaptureResult.SENSOR_EXPOSURE_TIME);
                        Integer iso = result.get(CaptureResult.SENSOR_SENSITIVITY);
                        Float focus = result.get(CaptureResult.LENS_FOCUS_DISTANCE);
                        Integer aeMode = result.get(CaptureResult.CONTROL_AE_MODE);
                        Boolean aeLock = result.get(CaptureResult.CONTROL_AE_LOCK);
                        Integer awbMode = result.get(CaptureResult.CONTROL_AWB_MODE);
                        Integer flicker = result.get(CaptureResult.STATISTICS_SCENE_FLICKER);
                        if(flicker!=null){
                            if(flicker==CaptureResult.STATISTICS_SCENE_FLICKER_50HZ)
                                measuredFlickerHz=FlickerGuard.HZ50;
                            else if(flicker==CaptureResult.STATISTICS_SCENE_FLICKER_60HZ)
                                measuredFlickerHz=FlickerGuard.HZ60;
                        }
                        if (exp != null && exp > 0L && iso != null && iso > 0) {
                            lastSensorExposureNs = exp;
                            lastSensorISO = iso;
                            lastCaptureTimeMs = android.os.SystemClock.elapsedRealtime();
                            if(!focusLocked && !afAeLockPending && !hardLockRequested &&
                               !hardLockVerified && recording==null)
                                maybeChangeAutomaticScene(iso,exp);
                        }
                        if (focus != null) lastLensFocusDistance = focus;
                        if (aeMode != null) lastResultAeMode = aeMode;
                        lastResultAeLocked = Boolean.TRUE.equals(aeLock);
                        if (awbMode != null) lastResultAwbMode = awbMode;
                        inspectSensorLockResult(result);
                        updateActualStabilization(result);
                    }
                });
            previewUseCase = pb.build();
            previewUseCase.setSurfaceProvider(preview.getSurfaceProvider());
            imageCapture = new ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY).build();
            imageCapture.setFlashMode(flash ? ImageCapture.FLASH_MODE_ON : ImageCapture.FLASH_MODE_OFF);
            UseCaseGroup.Builder group = new UseCaseGroup.Builder()
                .addUseCase(previewUseCase).addUseCase(imageCapture);
            if (!photoOnlyFallback) {
                VideoCapabilities caps=Recorder.getVideoCapabilities(selectedInfo);
                boolean uhd=caps.isQualitySupported(Quality.UHD,DynamicRange.SDR);
                boolean fhd=caps.isQualitySupported(Quality.FHD,DynamicRange.SDR);
                boolean hd=caps.isQualitySupported(Quality.HD,DynamicRange.SDR);
                effectiveVideoQuality=VideoProfile.chooseQuality(
                    videoCompatibilityFallback?"FHD":videoQualitySetting,uhd,fhd,hd);
                Quality q=effectiveVideoQuality.equals("UHD")?Quality.UHD:
                    effectiveVideoQuality.equals("HD")?Quality.HD:Quality.FHD;
                video60Supported=false;
                try{
                    Range<Integer>[] ranges=Camera2CameraInfo.from(selectedInfo)
                        .getCameraCharacteristic(
                            CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES);
                    if(ranges!=null)for(Range<Integer> range:ranges){
                        if(range.getUpper()>=60 && range.getLower()<=60){
                            video60Supported=true;break;
                        }
                    }
                }catch(Exception fpsError){Log.w(TAG,"Cannot query supported fps",fpsError);}
                effectiveVideoFps=VideoProfile.chooseFps(
                    videoCompatibilityFallback?30:videoFpsSetting,video60Supported);
                effectiveStabilization=VideoProfile.allowStabilization(
                    stabilizationMode>0,caps.isStabilizationSupported(),
                    effectiveVideoQuality,effectiveVideoFps);
                if(!effectiveStabilization)previewStabilizationRequested=false;
                videoRecorder=new Recorder.Builder()
                    .setQualitySelector(QualitySelector.from(q,
                        FallbackStrategy.higherQualityOrLowerThan(q))).build();
                VideoCapture.Builder<Recorder> videoBuilder=new VideoCapture.Builder<>(videoRecorder);
                videoBuilder.setTargetFrameRate(new Range<>(effectiveVideoFps,effectiveVideoFps));
                if(effectiveStabilization)videoBuilder.setVideoStabilizationEnabled(true);
                videoCapture=videoBuilder.build();
                group.addUseCase(videoCapture);
                if(!videoCapabilitiesFallbackNotified &&
                    (!effectiveVideoQuality.equals(videoQualitySetting) ||
                    effectiveVideoFps!=videoFpsSetting)){
                    videoCapabilitiesFallbackNotified=true;
                    showMessage("기기 지원 범위에 맞춰 "+effectiveVideoQuality+" "+
                        effectiveVideoFps+"fps로 조정합니다.");
                }
            } else {
                videoRecorder=null; videoCapture=null;
                effectiveStabilization=false;
                previewStabilizationRequested=false;
            }
            if (!filterFallback) group.addEffect(media3);
            ViewPort vp = preview.getViewPort();
            if (vp != null) group.setViewPort(vp);
            boundCamera = cameraProvider.bindToLifecycle(this,
                isFront ? CameraSelector.DEFAULT_FRONT_CAMERA : CameraSelector.DEFAULT_BACK_CAMERA,
                group.build());
            attachZoomState();
            if (boundCamera != null && flash && isVideo)
                boundCamera.getCameraControl().enableTorch(true);
            manualSensorAvailable = readManualSensorCapabilities();
            opticalStabilizationRequested=stabilizationMode>0 &&
                opticalStabilizationAvailable && !effectiveStabilization;
            lastExposureIndex = Integer.MIN_VALUE;
            applyAntiBanding();
            applyOpticalStabilization();
            updateStabilizationBadge();
            handler.postDelayed(() -> setExposureEv(currentEv), 800);
            setInfo();
        } catch (Exception e) {
            Log.e(TAG, "Camera bind failed", e);
            if(!videoCompatibilityFallback && !photoOnlyFallback &&
                (videoQualitySetting.equals("UHD") || videoFpsSetting==60 ||
                 stabilizationRequested)) {
                videoCompatibilityFallback=true;
                showMessage("고급 영상 조합이 지원되지 않아 1080p·30fps로 복구합니다.");
                preview.postDelayed(this::bindCameraUseCases,180);
            } else if(!filterFallback) {
                filterFallback=true;
                showMessage("GPU 효과 연결 실패 · 효과 없이 복구합니다.");
                preview.postDelayed(this::bindCameraUseCases,180);
            } else if(!photoOnlyFallback) {
                photoOnlyFallback=true;
                showMessage("동영상 연결 불가 · 사진 전용으로 복구합니다.");
                preview.postDelayed(this::bindCameraUseCases,180);
            } else {
                showMessage("카메라 연결 실패 · 카메라 권한과 기기 호환성을 확인하세요.");
            }
        }
    }

    /** Auto estimates brightness from Camera2 metering; it is NOT object detection. */
    private void maybeChangeAutomaticScene(int iso,long shutterNs) {
        if(scenePreference!=ColorLooks.Scene.AUTO || filterFallback)return;
        long now=android.os.SystemClock.elapsedRealtime();
        if(now-lastSceneSwitchMs<3500L)return;
        ColorLooks.Scene next=(iso>=850 || (iso>=450 && shutterNs>=24_000_000L))?
            ColorLooks.Scene.NIGHT:ColorLooks.Scene.DAYLIGHT;
        if(next==sceneResolved)return;
        lastSceneSwitchMs=now;
        runOnUiThread(()->{
            if(destroyed || scenePreference!=ColorLooks.Scene.AUTO ||
               recording!=null || focusLocked || afAeLockPending)return;
            sceneResolved=next;applyLook();setInfo();
        });
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
        if ((hardLockRequested || fallbackLockRequested) && matches &&
            consecutiveLockFrames>=3 && !lockConfirmationPosted) {
            lockConfirmationPosted=true;
            final boolean wasHard=hardLockRequested;
            runOnUiThread(()->{
                lockConfirmationPosted=false;
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
        } else if (focusLocked && mismatchFrames>=18 && !lockWarningPosted){
            lockWarningPosted=true;
            runOnUiThread(()->{
                lockWarningPosted=false;
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
        info.setSingleLine(true);
        info.setMaxWidth(dp(310));
        info.setEllipsize(android.text.TextUtils.TruncateAt.END);
        FrameLayout.LayoutParams infoLp =
            new FrameLayout.LayoutParams(-2, dp(27), Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        infoLp.topMargin = dp(61);
        root.addView(info, infoLp);

        stabilizationBadge=label("STAB 준비 중",11,GOLD,Typeface.BOLD);
        stabilizationBadge.setGravity(Gravity.CENTER);
        stabilizationBadge.setPadding(dp(12),0,dp(12),0);
        stabilizationBadge.setBackground(shape(0xC21A2025,dp(13),0x55FFFFFF,dp(1)));
        FrameLayout.LayoutParams stabLp=new FrameLayout.LayoutParams(
            -2,dp(30),Gravity.TOP|Gravity.RIGHT);
        stabLp.topMargin=dp(98);
        stabLp.rightMargin=dp(12);
        root.addView(stabilizationBadge,stabLp);
        stabilizationBadge.setOnClickListener(v->showStabilizationModePicker(stabilizationBadge));
        updateStabilizationBadge();

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
        LinearLayout.LayoutParams pr = new LinearLayout.LayoutParams(-1, dp(50));
        bottom.addView(presetRow, pr);
        ColorLooks.Look[] looks = ColorLooks.Look.values();
        presets = new TextView[looks.length];
        for (int i = 0; i < looks.length; i++) {
            ColorLooks.Look look = looks[i];
            String shortLabel = look == ColorLooks.Look.LUXURY ? "Luxury" :
                look == ColorLooks.Look.SOFT ? "Soft" :
                look == ColorLooks.Look.CINEMA ? "Noir" :
                look == ColorLooks.Look.CANON50 ? "Canon" : "원본";
            TextView chip = label(shortLabel, 11, look == selected ? INK : WHITE, Typeface.BOLD);
            chip.setContentDescription(look.label + " 색감 프리셋");
            chip.setGravity(Gravity.CENTER);
            chip.setSingleLine(true);
            chip.setBackground(shape(look == selected ? GOLD : 0xA4212529, dp(19),
                look == selected ? 0 : 0x66FFFFFF, dp(1)));
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0, dp(46), 1f);
            cp.setMargins(dp(4), 0, dp(4), 0);
            presetRow.addView(chip, cp);
            presets[i] = chip;
            chip.setOnClickListener(v -> selectLook(look));
        }

        // iPhone-like zoom interactions: tap focal presets, hold and slide,
        // or pinch anywhere on the camera preview. The list is rebuilt from
        // the camera's actual ZoomState range; unsupported 0.5x is not faked.
        zoomDial=new ZoomDialView();
        zoomDial.setVisibility(View.GONE);
        zoomDial.setContentDescription("연속 줌 다이얼. 좌우로 드래그하여 확대·축소");
        bottom.addView(zoomDial,new LinearLayout.LayoutParams(-1,dp(68)));
        zoomRow=new LinearLayout(this);
        zoomRow.setGravity(Gravity.CENTER);
        bottom.addView(zoomRow,new LinearLayout.LayoutParams(-1,dp(50)));
        rebuildZoomButtons();

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
        bottom.addView(modes, new LinearLayout.LayoutParams(-1, dp(45)));
        modePhoto = label("사진", 15, GOLD, Typeface.BOLD);
        modePhoto.setGravity(Gravity.CENTER);
        modes.addView(modePhoto, new LinearLayout.LayoutParams(dp(90), dp(44)));
        modeVideo = label("동영상", 15, MUTED, Typeface.NORMAL);
        modeVideo.setGravity(Gravity.CENTER);
        modes.addView(modeVideo, new LinearLayout.LayoutParams(dp(90), dp(44)));
        modePhoto.setOnClickListener(v -> changeMode(false));
        modeVideo.setOnClickListener(v -> changeMode(true));
        modePro = label("프로",14,MUTED,Typeface.BOLD);
        modePro.setGravity(Gravity.CENTER);
        modes.addView(modePro,new LinearLayout.LayoutParams(dp(70),dp(44)));
        modePro.setOnClickListener(v->{
            if(recording!=null)return;
            isVideo=false;proMode=true;
            modePhoto.setTextColor(MUTED);modeVideo.setTextColor(MUTED);
            modePro.setTextColor(GOLD);
            capture.setBackground(shape(WHITE,dp(34),0,0));
            if(proTelemetry!=null)proTelemetry.setVisibility(View.VISIBLE);
            showProControls();setInfo();
        });

        proTelemetry=label("",11,WHITE,Typeface.BOLD);
        proTelemetry.setGravity(Gravity.CENTER);
        proTelemetry.setBackground(shape(0xB9161B1F,dp(12),0,0));
        FrameLayout.LayoutParams pt=new FrameLayout.LayoutParams(-2,dp(30),
            Gravity.TOP|Gravity.CENTER_HORIZONTAL);
        pt.topMargin=dp(130);
        root.addView(proTelemetry,pt);
        proTelemetry.setVisibility(View.GONE);
        Runnable telemetryTick=new Runnable(){
            @Override public void run(){
                if(destroyed)return;
                if(proMode&&proTelemetry!=null){
                    String iso=lastSensorISO>0?String.valueOf(lastSensorISO):"—";
                    String shutter=lastSensorExposureNs>0?
                        "1/"+Math.max(1L,Math.round(1e9/lastSensorExposureNs)):"—";
                    proTelemetry.setText(" ISO "+iso+" · "+shutter+"s · "+
                        (hardLockVerified?"LOCK":"AUTO")+" ");
                }
                handler.postDelayed(this,750);
            }
        };
        handler.postDelayed(telemetryTick,750);

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

        TextView sceneRow=label("장면별 색감 · "+scenePreference.label,
            14,WHITE,Typeface.BOLD);
        sceneRow.setPadding(dp(16),0,dp(16),0);
        sceneRow.setBackground(shape(0xFF2C3238,dp(13),0,0));
        LinearLayout.LayoutParams sceneRowLp=new LinearLayout.LayoutParams(-1,dp(48));
        sceneRowLp.bottomMargin=dp(8);
        panel.addView(sceneRow,sceneRowLp);
        sceneRow.setOnClickListener(v->{dialog.dismiss();showScenePicker();});

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

        TextView flickerChoice = label("조명 깜빡임 감소 · " + flickerLabel(),
            14, WHITE, Typeface.BOLD);
        flickerChoice.setPadding(dp(16),0,dp(16),0);
        flickerChoice.setBackground(shape(0xFF2C3238,dp(13),0,0));
        LinearLayout.LayoutParams flickerLp=new LinearLayout.LayoutParams(-1,dp(49));
        flickerLp.topMargin=dp(8);
        panel.addView(flickerChoice,flickerLp);
        flickerChoice.setOnClickListener(v->showFlickerSettings(flickerChoice));

        TextView bokehRow=label("인물 배경 흐림 · "+
            (portraitBokehRequested?"켜짐":"꺼짐"),14,WHITE,Typeface.BOLD);
        bokehRow.setPadding(dp(16),0,dp(16),0);
        bokehRow.setBackground(shape(0xFF2C3238,dp(13),0,0));
        LinearLayout.LayoutParams blp=new LinearLayout.LayoutParams(-1,dp(48));
        blp.topMargin=dp(8);panel.addView(bokehRow,blp);
        bokehRow.setOnClickListener(v->{
            if(recording!=null){showMessage("녹화 중에는 설정할 수 없습니다.");return;}
            if(!portraitBokehRequested){
                try{
                    if(extensionsManager==null || !extensionsManager.isExtensionAvailable(
                        CameraSelector.DEFAULT_BACK_CAMERA,ExtensionMode.BOKEH)){
                        showMessage("제조사 Bokeh 확장이 지원되지 않는 기기입니다.");
                        return;
                    }
                }catch(Exception err){
                    Log.w(TAG,"Portrait feature unavailable",err);
                    showMessage("기기에서 Bokeh 기능을 확인할 수 없습니다.");
                    return;
                }
            }
            portraitBokehRequested=!portraitBokehRequested;
            getPreferences(MODE_PRIVATE).edit().putBoolean("portrait_bokeh",portraitBokehRequested).apply();
            bokehRow.setText("인물 배경 흐림 · "+(portraitBokehRequested?"켜짐":"꺼짐"));
            if(cameraProvider!=null)bindCameraUseCases();
        });
        TextView qualityRow = label("영상 화질 · "+videoQualitySetting+" / "+
            videoFpsSetting+"fps",14,WHITE,Typeface.BOLD);
        qualityRow.setPadding(dp(16),0,dp(16),0);
        qualityRow.setBackground(shape(0xFF2C3238,dp(13),0,0));
        LinearLayout.LayoutParams qlp=new LinearLayout.LayoutParams(-1,dp(48));
        qlp.topMargin=dp(8);
        panel.addView(qualityRow,qlp);
        qualityRow.setOnClickListener(v->{dialog.dismiss();showVideoSettings();});
        TextView stabRow=label("손떨림 보정 · "+stabilizationModeText(),
            14,WHITE,Typeface.BOLD);
        stabRow.setPadding(dp(16),0,dp(16),0);
        stabRow.setBackground(shape(0xFF2C3238,dp(13),0,0));
        LinearLayout.LayoutParams slp=new LinearLayout.LayoutParams(-1,dp(48));
        slp.topMargin=dp(8);
        panel.addView(stabRow,slp);
        stabRow.setOnClickListener(v->showStabilizationModePicker(stabRow));

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
                    getPreferences(MODE_PRIVATE).edit().putFloat("filter_intensity",intensity).apply();
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
        android.widget.ScrollView scrolling=new android.widget.ScrollView(this);
        scrolling.setFillViewport(false);
        scrolling.addView(panel);
        dialog.setContentView(scrolling);
        android.view.Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.setDimAmount(.57f);
        }
        dialog.show();
        if (window != null) {
            window.setGravity(Gravity.BOTTOM);
            window.setLayout(-1,
                Math.min(dp(690),Math.round(getResources().getDisplayMetrics().heightPixels*.80f)));
            window.setNavigationBarColor(INK);
        }
    }

    private String stabilizationModeText(){
        return stabilizationMode==0?"끄기":stabilizationMode==2?"강화 · 미리보기+영상":"표준";
    }
    private void showStabilizationModePicker(TextView title){
        if(recording!=null || busy){
            showMessage("녹화·촬영 중에는 손떨림 설정을 변경할 수 없습니다.");
            return;
        }
        final String[] choices={"끄기","표준 · 영상 EIS 및 기기 OIS",
            "강화 · 지원 기기에서 미리보기+영상 동시 보정"};
        new android.app.AlertDialog.Builder(this).setTitle("손떨림 방지")
            .setSingleChoiceItems(choices,stabilizationMode,(dialog,index)->{
                dialog.dismiss();
                stabilizationMode=index;
                stabilizationRequested=index>0;
                videoCompatibilityFallback=false;
                videoCapabilitiesFallbackNotified=false;
                getPreferences(MODE_PRIVATE).edit()
                    .putInt("stabilization_mode",index)
                    .putBoolean("video_stabilization",stabilizationRequested).apply();
                title.setText("손떨림 보정 · "+stabilizationModeText());
                if(cameraProvider!=null && recording==null)bindCameraUseCases();
            }).setNegativeButton("취소",null).show();
    }
    private void applyOpticalStabilization(){
        if(boundCamera==null || !opticalStabilizationRequested)return;
        try{
            Camera2CameraControl.from(boundCamera.getCameraControl())
                .addCaptureRequestOptions(new CaptureRequestOptions.Builder()
                    .setCaptureRequestOption(CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE,
                        CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_ON).build());
        }catch(Exception e){Log.w(TAG,"OIS request could not be applied",e);}
    }
    private void updateActualStabilization(TotalCaptureResult frame){
        Integer eis=frame.get(CaptureResult.CONTROL_VIDEO_STABILIZATION_MODE);
        Integer ois=frame.get(CaptureResult.LENS_OPTICAL_STABILIZATION_MODE);
        if(eis!=null)actualEisMode=eis;
        if(ois!=null)actualOisMode=ois;
        long now=android.os.SystemClock.elapsedRealtime();
        if(now-lastStabUiMillis<850L)return;
        lastStabUiMillis=now;
        runOnUiThread(()->{if(!destroyed)updateStabilizationBadge();});
    }
    private String actualStabilizationText(){
        if(stabilizationMode==0)return "STAB OFF";
        if(actualEisMode==CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_PREVIEW_STABILIZATION)
            return "STAB 미리보기+영상";
        if(actualEisMode==CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_ON)
            return "STAB 영상 EIS";
        if(actualOisMode==CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_ON)
            return "STAB 렌즈 OIS";
        if(effectiveStabilization || opticalStabilizationRequested)
            return "STAB 준비 중";
        return "STAB 제한";
    }
    private void updateStabilizationBadge(){
        if(stabilizationBadge==null)return;
        boolean show=isVideo || stabilizationMode>0;
        stabilizationBadge.setVisibility(show?View.VISIBLE:View.GONE);
        String description=actualStabilizationText();
        stabilizationBadge.setText(description);
        stabilizationBadge.setContentDescription("손떨림 방지 상태 "+description+
            ". 눌러 설정 변경");
        stabilizationBadge.setTextColor(stabilizationMode==0?MUTED:GOLD);
    }

    private String flickerLabel() {
        if(flickerPreference==FlickerGuard.HZ50)return "50Hz";
        if(flickerPreference==FlickerGuard.HZ60)return "60Hz";
        if(flickerPreference==FlickerGuard.OFF)return "꺼짐";
        return "자동";
    }

    private void showFlickerSettings(TextView display) {
        if(recording!=null || afAeLockPending){
            showMessage("녹화/잠금 처리 중에는 변경할 수 없습니다.");return;
        }
        final int[] choices={FlickerGuard.AUTO,FlickerGuard.HZ50,FlickerGuard.HZ60,FlickerGuard.OFF};
        final String[] names={"자동 감지","50Hz 조명","60Hz 조명","끄기"};
        int index=0;
        for(int i=0;i<choices.length;i++)if(choices[i]==flickerPreference)index=i;
        new android.app.AlertDialog.Builder(this).setTitle("안티플리커")
            .setSingleChoiceItems(names,index,(dialog,which)->{
                dialog.dismiss();
                if(focusLocked)unlockFocus();
                flickerPreference=choices[which];
                getPreferences(MODE_PRIVATE).edit().putInt("flicker_preference",flickerPreference).apply();
                applyAntiBanding();
                display.setText("조명 깜빡임 감소 · "+flickerLabel());
            }).setNegativeButton("취소",null).show();
    }

    private void applyAntiBanding() {
        if(boundCamera==null || hardLockRequested || hardLockVerified)return;
        try{
            int[] supported=Camera2CameraInfo.from(boundCamera.getCameraInfo())
                .getCameraCharacteristic(CameraCharacteristics.CONTROL_AE_AVAILABLE_ANTIBANDING_MODES);
            if(supported==null || supported.length==0)return;
            int chosen=CaptureRequest.CONTROL_AE_ANTIBANDING_MODE_AUTO;
            if(flickerPreference==FlickerGuard.HZ50)chosen=CaptureRequest.CONTROL_AE_ANTIBANDING_MODE_50HZ;
            else if(flickerPreference==FlickerGuard.HZ60)chosen=CaptureRequest.CONTROL_AE_ANTIBANDING_MODE_60HZ;
            else if(flickerPreference==FlickerGuard.OFF)chosen=CaptureRequest.CONTROL_AE_ANTIBANDING_MODE_OFF;
            boolean found=false;
            for(int v:supported)if(v==chosen)found=true;
            if(!found){
                chosen=supported[0];
                for(int v:supported)if(v==CaptureRequest.CONTROL_AE_ANTIBANDING_MODE_AUTO)chosen=v;
                showMessage("현재 기기에서 해당 주파수를 지원하지 않아 대체 설정을 적용합니다.");
            }
            CaptureRequestOptions opts=new CaptureRequestOptions.Builder()
                .setCaptureRequestOption(CaptureRequest.CONTROL_AE_ANTIBANDING_MODE,chosen)
                .build();
            Camera2CameraControl.from(boundCamera.getCameraControl()).addCaptureRequestOptions(opts);
        }catch(Exception e){Log.w(TAG,"Antibanding not supported",e);}
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
        if(portraitBokehActive) {
            showMessage("제조사 보케 촬영에서는 색감 LUT를 함께 적용할 수 없습니다. 설정에서 보케를 끄세요.");
            return;
        }
        if (filterFallback) { showMessage("이 기기의 GPU 필터가 지원되지 않습니다."); return; }
        selected = look;
        getPreferences(MODE_PRIVATE).edit()
            .putString("selected_look", look.name()).apply();
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
        if (media3 == null || filterFallback || portraitBokehActive) return;
        try {
            if (selected == ColorLooks.Look.ORIGINAL || intensity == 0f)
                media3.setEffects(Collections.emptyList());
            else media3.setEffects(Collections.singletonList(
                ColorLooks.generate(selected,intensity,sceneResolved)));
        } catch (Exception e) {
            Log.e(TAG, "Effect changed failed", e);
            showMessage("필터 적용 오류");
        }
    }

    private void setInfo() {
        String currentLabel = selected == ColorLooks.Look.CANON50
            ? "CANON R5 II · 50mm LOOK"
            : selected.label.toUpperCase(Locale.US);
        String qualityInfo=isVideo?(" · "+effectiveVideoQuality+" "+
            effectiveVideoFps+"fps"+(effectiveStabilization?" · STAB":"")):"";
        info.setText(currentLabel + " · " + frameRatio +
            (filterFallback?" · 필터 중단":"") +
            (portraitBokehActive?" · 제조사 보케":"") +
            " · " + (proMode?"PRO":isVideo?"VIDEO":"PHOTO")+qualityInfo);
        captureLabel.setText(selected.hint + "  ·  기기 내 처리");
    }

    private void showVideoSettings() {
        if(recording!=null){showMessage("녹화 중에는 변경할 수 없습니다.");return;}
        final String[] choices={"4K UHD","1080p FHD","720p HD"};
        final String[] levels={"UHD","FHD","HD"};
        int selectedIndex=videoQualitySetting.equals("UHD")?0:videoQualitySetting.equals("HD")?2:1;
        new android.app.AlertDialog.Builder(this).setTitle("영상 해상도")
            .setSingleChoiceItems(choices,selectedIndex,(d,index)->{
                d.dismiss();
                videoQualitySetting=levels[index];
                getPreferences(MODE_PRIVATE).edit().putString("video_quality",videoQualitySetting).apply();
                showVideoFpsPicker();
            }).setNegativeButton("취소",null).show();
    }

    private void showVideoFpsPicker() {
        if(recording!=null)return;
        final String[] choices={"30fps · 저조도 우선","60fps · 움직임 우선 (기기별 지원)"};
        new android.app.AlertDialog.Builder(this).setTitle("영상 프레임 속도")
            .setSingleChoiceItems(choices,videoFpsSetting==60?1:0,(d,index)->{
                d.dismiss();
                videoFpsSetting=index==1?60:30;
                getPreferences(MODE_PRIVATE).edit().putInt("video_fps",videoFpsSetting).apply();
                videoCompatibilityFallback=false;
                videoCapabilitiesFallbackNotified=false;
                if(cameraProvider!=null)bindCameraUseCases();
            }).setNegativeButton("취소",null).show();
    }

    private void showScenePicker() {
        ColorLooks.Scene[] scenes=ColorLooks.Scene.values();
        String[] names=new String[scenes.length];int choice=0;
        for(int i=0;i<scenes.length;i++){
            names[i]=scenes[i].label;
            if(scenes[i]==scenePreference)choice=i;
        }
        new android.app.AlertDialog.Builder(this)
            .setTitle("장면별 색감 · 자동은 노출값으로 추정")
            .setSingleChoiceItems(names,choice,(d,index)->{
                d.dismiss();scenePreference=scenes[index];
                sceneResolved=scenePreference==ColorLooks.Scene.AUTO?
                    (lastSensorISO>=850?ColorLooks.Scene.NIGHT:ColorLooks.Scene.DAYLIGHT):
                    scenePreference;
                getPreferences(MODE_PRIVATE).edit().putString("scene_mode",scenePreference.name()).apply();
                applyLook();setInfo();
            }).setNegativeButton("취소",null).show();
    }

    private void showProControls() {
        if(boundCamera==null)return;
        String text="ISO "+(lastSensorISO>0?lastSensorISO:"—")+
            " · "+(lastSensorExposureNs>0?"1/"+Math.max(1L,Math.round(1e9/lastSensorExposureNs))+"s":"셔터 —")+
            "\n노출 잠금 "+(hardLockVerified?"센서 고정":
                focusLocked?"기기 AE 고정":"자동")+
            "\n노출 보정 "+String.format(Locale.US,"%+.1f EV",currentEv);
        new android.app.AlertDialog.Builder(this).setTitle("프로 촬영")
            .setMessage(text+"\n\n길게 눌러 AE/AF를 고정한 후 태양 아이콘을 위아래로 움직여 노출을 조절할 수 있습니다.")
            .setPositiveButton("AE/AF 잠금",(d,v)->focusAt(
                preview.getWidth()/2f,preview.getHeight()/2f,true))
            .setNegativeButton("닫기",null).show();
    }

    private void changeMode(boolean video) {
        if (recording != null) return;
        if (video && photoOnlyFallback) {
            showMessage("이 카메라에서는 사진만 촬영할 수 있습니다.");return;
        }
        isVideo = video;
        if(cameraProvider!=null && portraitBokehRequested &&
            (portraitBokehActive!=(!video && !isFront)))
            preview.postDelayed(this::bindCameraUseCases,90);
        proMode=false;
        if(modePro!=null)modePro.setTextColor(MUTED);
        if(proTelemetry!=null)proTelemetry.setVisibility(View.GONE);
        modePhoto.setTextColor(video ? MUTED : GOLD);
        modePhoto.setTypeface(null, video ? Typeface.NORMAL : Typeface.BOLD);
        modeVideo.setTextColor(video ? GOLD : MUTED);
        modeVideo.setTypeface(null, video ? Typeface.BOLD : Typeface.NORMAL);
        capture.setBackground(shape(video ? 0xFFFF5658 : WHITE, dp(34), 0, 0));
        setInfo();
    }

    private void onShutter() {
        if (boundCamera == null) { showMessage("카메라를 준비하는 중입니다."); return; }
        if (isVideo && videoRecorder == null) {
            showMessage("동영상 촬영을 지원하지 않는 카메라입니다.");return;
        }
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
                    cancelZoomAnimation();
                    revealZoomDial();
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
                            float desired=ZoomMath.limit(cameraZoom*d/lastPinchDistance,zoomMin,zoomMax);
                            setZoomDirect(desired);
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
                    scheduleHideZoomDial();
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
        if (portraitBokehActive && lock) {
            showMessage("인물 보케 모드에서는 장시간 노출 잠금이 지원되지 않습니다.");
            lock=false;
        }
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
                int effectiveHz=FlickerGuard.effectiveHz(flickerPreference,measuredFlickerHz);
                FlickerGuard.Choice quantized=FlickerGuard.choose(
                    baselineShutterNs,baselineISO,supportedISO.getLower(),supportedISO.getUpper(),
                    supportedShutterNs.getLower(),Math.min(33_333_333L,supportedShutterNs.getUpper()),
                    effectiveHz);
                requestedISO=quantized.iso;
                requestedShutterNs=quantized.shutterNs;
                if(quantized.shortExposure && effectiveHz!=0)
                    showMessage("빠른 셔터에서는 LED 깜빡임이 남을 수 있습니다.");
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
        lockConfirmationPosted=false;lockWarningPosted=false;
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
            if(hadAE)handler.postDelayed(()->{
                applyAntiBanding();
                applyOpticalStabilization();
            },140);
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
                        if(filterFallback && !destroyed)
                            preview.postDelayed(this::bindCameraUseCases,160);
                        if (!end.hasError()) {
                            lastMedia = end.getOutputResults().getOutputUri();
                            showMessage("영상이 갤러리에 저장되었습니다.");
                            inspectSavedVideo(lastMedia);
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

    /**
     * Verify the SAVED media format. Requested resolution/frame rate can differ
     * from actual encoding after capability fallback or camera firmware limits.
     */
    private void inspectSavedVideo(Uri uri) {
        if(uri==null)return;
        new Thread(()->{
            MediaMetadataRetriever retriever=new MediaMetadataRetriever();
            try {
                retriever.setDataSource(getApplicationContext(),uri);
                String w=retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH);
                String h=retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT);
                String frames=retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_FRAME_COUNT);
                String millis=retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
                String fps="프레임 정보 없음";
                if(frames!=null && millis!=null){
                    long count=Long.parseLong(frames),duration=Long.parseLong(millis);
                    if(duration>0 && count>0)
                        fps=String.format(Locale.US,"평균 %.1ffps",count*1000d/duration);
                }
                final String result=(w==null||h==null?"해상도 미확인":w+"×"+h)+
                    " · "+fps;
                Log.i(TAG,"Saved actual video format "+result);
                runOnUiThread(()->{if(!destroyed)showMessage("녹화 파일 확인: "+result);});
            } catch(Exception e) {
                Log.w(TAG,"Saved video specification inspection unavailable",e);
            } finally {
                try{retriever.release();}catch(Exception ignored){}
            }
        },"IPhoto-MediaInspector").start();
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

    private void attachZoomState(){
        detachZoomState();
        if(boundCamera==null)return;
        zoomStateLiveData=boundCamera.getCameraInfo().getZoomState();
        zoomStateObserver=(state)->{
            if(state==null || destroyed)return;
            float lower=Math.max(.1f,state.getMinZoomRatio());
            float upper=Math.max(lower,state.getMaxZoomRatio());
            boolean rangeChanged=Math.abs(zoomMin-lower)>.015f ||
                Math.abs(zoomMax-upper)>.015f;
            zoomMin=lower;zoomMax=upper;
            cameraZoom=ZoomMath.limit(state.getZoomRatio(),zoomMin,zoomMax);
            if(rangeChanged)rebuildZoomButtons();
            updateZoomVisuals();
        };
        zoomStateLiveData.observe(this,zoomStateObserver);
        ZoomState current=zoomStateLiveData.getValue();
        if(current!=null)zoomStateObserver.onChanged(current);
    }
    private void detachZoomState(){
        if(zoomStateLiveData!=null && zoomStateObserver!=null)
            zoomStateLiveData.removeObserver(zoomStateObserver);
        zoomStateLiveData=null;zoomStateObserver=null;
    }
    private void rebuildZoomButtons(){
        if(zoomRow==null)return;
        zoomRow.removeAllViews();
        zoomStops=ZoomMath.presets(zoomMin,zoomMax);
        zoomButtons=new TextView[zoomStops.length];
        for(int i=0;i<zoomStops.length;i++){
            final float stop=zoomStops[i];
            TextView button=label(zoomLabel(stop),13,WHITE,Typeface.BOLD);
            button.setGravity(Gravity.CENTER);
            button.setContentDescription(zoomLabel(stop)+
                " 배율로 전환. 길게 눌러 다이얼 연 다음 가로로 드래그");
            button.setMinimumHeight(dp(48));
            button.setBackground(shape(0xB0252A2F,dp(26),0,0));
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(48),1f);
            lp.setMargins(dp(2),0,dp(2),0);
            zoomRow.addView(button,lp);
            zoomButtons[i]=button;
            button.setOnClickListener(v->animateZoomTo(stop));
            button.setOnTouchListener((view,event)->{
                switch(event.getActionMasked()){
                    case MotionEvent.ACTION_DOWN:
                        zoomHoldActive=false;zoomTouchMoved=false;
                        zoomTouchBeginX=event.getRawX();
                        zoomHoldBaseRatio=cameraZoom;
                        if(zoomHoldTask!=null)handler.removeCallbacks(zoomHoldTask);
                        zoomHoldTask=()->{
                            zoomHoldActive=true;
                            revealZoomDial();
                            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                        };
                        handler.postDelayed(zoomHoldTask,360L);
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        float dx=event.getRawX()-zoomTouchBeginX;
                        if(!zoomHoldActive && Math.abs(dx)>dp(13)){
                            zoomTouchMoved=true;
                            if(zoomHoldTask!=null)handler.removeCallbacks(zoomHoldTask);
                        }
                        if(zoomHoldActive)
                            setZoomDirect(ZoomMath.drag(zoomHoldBaseRatio,dx,dp(135),
                                zoomMin,zoomMax));
                        return true;
                    case MotionEvent.ACTION_UP:
                        if(zoomHoldTask!=null)handler.removeCallbacks(zoomHoldTask);
                        if(zoomHoldActive){zoomHoldActive=false;scheduleHideZoomDial();}
                        else if(!zoomTouchMoved)view.performClick();
                        return true;
                    case MotionEvent.ACTION_CANCEL:
                        if(zoomHoldTask!=null)handler.removeCallbacks(zoomHoldTask);
                        zoomHoldActive=false;return true;
                    default:return true;
                }
            });
        }
        updateZoomVisuals();
    }
    private String zoomLabel(float value){
        return String.format(Locale.US,Math.abs(value-Math.round(value))<.05f?
            "%.0f×":"%.1f×",value);
    }
    private void updateZoomVisuals(){
        if(zoomDial!=null)zoomDial.invalidate();
        if(zoomButtons==null)return;
        for(int i=0;i<zoomButtons.length && i<zoomStops.length;i++){
            TextView button=zoomButtons[i];
            boolean active=Math.abs(Math.log(Math.max(.1f,cameraZoom)/zoomStops[i]))<.13f;
            button.setTextColor(active?INK:WHITE);
            button.setBackground(shape(active?GOLD:0xA5292D31,dp(24),0,0));
        }
    }
    private void cancelZoomAnimation(){
        if(zoomAnimator!=null){
            zoomAnimator.cancel();
            zoomAnimator=null;
        }
    }
    private void animateZoomTo(float ratio){
        if(boundCamera==null)return;
        cancelZoomAnimation();
        float start=cameraZoom,target=ZoomMath.limit(ratio,zoomMin,zoomMax);
        if(Math.abs(start-target)<.01f){setZoomDirect(target);return;}
        zoomAnimator=ValueAnimator.ofFloat(start,target);
        zoomAnimator.setDuration(210L);
        zoomAnimator.setInterpolator(new DecelerateInterpolator());
        zoomAnimator.addUpdateListener(a->setZoomDirect((float)a.getAnimatedValue()));
        zoomAnimator.start();
        scheduleHideZoomDial();
    }
    private void setZoom(float ratio){animateZoomTo(ratio);}
    private void setZoomDirect(float ratio){
        if(boundCamera==null)return;
        float wanted=ZoomMath.limit(ratio,zoomMin,zoomMax);
        if(Math.abs(wanted-cameraZoom)<.003f)return;
        long now=android.os.SystemClock.elapsedRealtime();
        if(now-lastZoomSentAt<18L)return;
        lastZoomSentAt=now;
        try{
            boundCamera.getCameraControl().setZoomRatio(wanted);
            cameraZoom=wanted;
            updateZoomVisuals();
        }catch(Exception e){Log.w(TAG,"Zoom request failed",e);}
    }
    private void revealZoomDial(){
        if(zoomDial==null)return;
        if(zoomHideTask!=null)handler.removeCallbacks(zoomHideTask);
        zoomDial.setVisibility(View.VISIBLE);
        zoomDial.invalidate();
    }
    private void scheduleHideZoomDial(){
        if(zoomHideTask!=null)handler.removeCallbacks(zoomHideTask);
        zoomHideTask=()->{
            if(zoomDial!=null && !zoomHoldActive)
                zoomDial.setVisibility(View.GONE);
        };
        handler.postDelayed(zoomHideTask,2800L);
    }

    /** Centered graduated iPhone-style zoom scrubber with logarithmic mapping. */
    private final class ZoomDialView extends View{
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        ZoomDialView(){super(MainActivity.this);setLayerType(View.LAYER_TYPE_SOFTWARE,null);}
        @Override protected void onDraw(Canvas c){
            super.onDraw(c);
            float w=getWidth(),h=getHeight(),x0=dp(24),x1=w-dp(24);
            if(w<=dp(80))return;
            float y=h*.64f;
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(1.1f));paint.setColor(0x9ACCC5B9);
            c.drawLine(x0,y,x1,y,paint);
            for(int i=0;i<=40;i++){
                float x=x0+(x1-x0)*i/40f;
                float height=dp(i%5==0?10:5);
                paint.setStrokeWidth(dp(i%5==0?1.8f:1f));
                paint.setColor(i%5==0?0xFFF0EAE1:0x8FADA7A0);
                c.drawLine(x,y-height*.5f,x,y+height*.5f,paint);
            }
            float pos=x0+(x1-x0)*ZoomMath.position(cameraZoom,zoomMin,zoomMax);
            paint.setColor(GOLD);paint.setStrokeWidth(dp(3));
            c.drawLine(pos,y-dp(14),pos,y+dp(14),paint);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(WHITE);
            paint.setTextSize(dp(15));paint.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));
            paint.setTextAlign(Paint.Align.CENTER);
            c.drawText(zoomLabel(cameraZoom),pos,dp(18),paint);
            paint.setTextSize(dp(10));paint.setColor(0xFFACA59A);
            paint.setTextAlign(Paint.Align.LEFT);
            c.drawText(zoomLabel(zoomMin),x0,dp(18),paint);
            paint.setTextAlign(Paint.Align.RIGHT);
            c.drawText(zoomLabel(zoomMax),x1,dp(18),paint);
        }
        @Override public boolean onTouchEvent(MotionEvent event){
            if(boundCamera==null)return false;
            int action=event.getActionMasked();
            if(action==MotionEvent.ACTION_DOWN || action==MotionEvent.ACTION_MOVE){
                if(action==MotionEvent.ACTION_DOWN){
                    cancelZoomAnimation();revealZoomDial();
                }
                float margin=dp(24),range=Math.max(1f,getWidth()-margin*2f);
                float position=Math.max(0f,Math.min(1f,(event.getX()-margin)/range));
                setZoomDirect(ZoomMath.ratio(position,zoomMin,zoomMax));
                return true;
            }
            if(action==MotionEvent.ACTION_UP || action==MotionEvent.ACTION_CANCEL){
                if(action==MotionEvent.ACTION_UP)performClick();
                scheduleHideZoomDial();return true;
            }
            return true;
        }
        @Override public boolean performClick(){super.performClick();return true;}
    }

    private void switchCamera() {
        if (boundCamera == null || recording != null || busy) return;
        try {
            unlockFocus();
            filterFallback=false;
            photoOnlyFallback=false;
            videoCompatibilityFallback=false;
            videoCapabilitiesFallbackNotified=false;
            lastExposureIndex = Integer.MIN_VALUE;
            cameraZoom=1f;
            zoomMin=1f;zoomMax=6f;
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
        destroyed=true;
        handler.removeCallbacks(tick);
        if (pendingFocusLock != null) handler.removeCallbacks(pendingFocusLock);
        if (zoomHideTask != null) handler.removeCallbacks(zoomHideTask);
        if (zoomHoldTask != null) handler.removeCallbacks(zoomHoldTask);
        cancelZoomAnimation();
        detachZoomState();
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
