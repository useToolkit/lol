package com.usetoolkit.iphoto;

import android.Manifest;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Color;
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
import androidx.camera.core.CameraEffect;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.ExposureState;
import androidx.camera.media3.effect.Media3Effect;
import androidx.camera.video.FallbackStrategy;
import androidx.camera.video.MediaStoreOutputOptions;
import androidx.camera.video.Quality;
import androidx.camera.video.QualitySelector;
import androidx.camera.video.Recording;
import androidx.camera.video.VideoRecordEvent;
import androidx.camera.view.CameraController;
import androidx.camera.view.LifecycleCameraController;
import androidx.camera.view.PreviewView;
import androidx.camera.view.video.AudioConfig;
import androidx.core.content.ContextCompat;

import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.Locale;

/** I photo - on-device photo/video capture with a shared GPU-based 3D LUT. */
public final class MainActivity extends ComponentActivity {
    private static final String TAG = "IPhoto";
    private static final int INK = Color.rgb(9, 11, 13);
    private static final int GOLD = Color.rgb(223, 195, 154);
    private static final int WHITE = Color.rgb(244, 239, 231);
    private static final int MUTED = Color.rgb(166, 166, 164);

    private ActivityResultLauncher<String[]> permissions;
    private LifecycleCameraController controller;
    private PreviewView preview;
    private FrameLayout previewStage, previewFrame;
    private LinearLayout bottomControls;
    private FrameGuide frameGuide;
    private TextView[] ratioButtons;
    private String frameRatio = "4:3";
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
        frameRatio = getPreferences(MODE_PRIVATE).getString("capture_ratio", "4:3");
        boolean validRatio = false;
        for (String r : RATIOS) if (r.equals(frameRatio)) validRatio = true;
        if (!validRatio) frameRatio = "4:3";
        buildInterface();
        permissions = registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
            Boolean granted = result.get(Manifest.permission.CAMERA);
            if (Boolean.TRUE.equals(granted) || hasCamera()) startCamera();
            else showMessage("카메라 권한을 허용해야 촬영할 수 있습니다.");
        });
        if (hasCamera()) startCamera();
        else permissions.launch(new String[] {Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO});
    }

    private void startCamera() {
        if (controller != null) return;
        try {
            media3 = new Media3Effect(this,
                CameraEffect.PREVIEW | CameraEffect.VIDEO_CAPTURE | CameraEffect.IMAGE_CAPTURE,
                ContextCompat.getMainExecutor(this), t -> {
                    Log.e(TAG, "GPU effect failed", t);
                    showMessage("효과 처리 오류: 다시 실행해 주세요.");
                });
            media3.setEffects(Collections.singletonList(ColorLooks.generate(selected, intensity)));
            controller = new LifecycleCameraController(this);
            controller.setEnabledUseCases(CameraController.IMAGE_CAPTURE | CameraController.VIDEO_CAPTURE);
            controller.setImageCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY);
            controller.setVideoCaptureQualitySelector(QualitySelector.from(
                Quality.FHD, FallbackStrategy.higherQualityOrLowerThan(Quality.FHD)));
            controller.setPinchToZoomEnabled(true);
            controller.setTapToFocusEnabled(true);
            controller.setEffects(Collections.singleton(media3));
            preview.setController(controller);
            controller.bindToLifecycle(this);
            // Slightly darker capture exposure is intentional for luxury-night scenes.
            preview.postDelayed(() -> setExposureEv(-.5f), 900);
            setInfo();
        } catch (Exception e) {
            Log.e(TAG, "Unable to initialize camera", e);
            showMessage("카메라 초기화 실패: " + e.getClass().getSimpleName());
        }
    }

    private void buildInterface() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(INK);
        setContentView(root);
        // Only the framed preview is recorded. Black matte outside is NOT captured.
        previewStage = new FrameLayout(this);
        root.addView(previewStage, new FrameLayout.LayoutParams(-1, -1));
        previewFrame = new FrameLayout(this);
        previewFrame.setBackgroundColor(0xFF161A1E);
        previewStage.addView(previewFrame, new FrameLayout.LayoutParams(dp(280), dp(375), Gravity.CENTER));
        preview = new PreviewView(this);
        preview.setImplementationMode(PreviewView.ImplementationMode.COMPATIBLE);
        preview.setScaleType(PreviewView.ScaleType.FILL_CENTER);
        previewFrame.addView(preview, new FrameLayout.LayoutParams(-1, -1));
        grid = new GridOverlay();
        grid.setVisibility(View.GONE);
        previewFrame.addView(grid, new FrameLayout.LayoutParams(-1, -1));
        frameGuide = new FrameGuide();
        previewFrame.addView(frameGuide, new FrameLayout.LayoutParams(-1, -1));
        previewStage.addOnLayoutChangeListener((v, left, top, right, b, ol, ot, or, ob) ->
            updateCaptureFrame());

        View shadeTop = new View(this);
        shadeTop.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
            new int[] {0xDD090B0E, 0x88090B0E, Color.TRANSPARENT}));
        FrameLayout.LayoutParams shadeTopLp = new FrameLayout.LayoutParams(-1, dp(72), Gravity.TOP);
        root.addView(shadeTop, shadeTopLp);

        View shadeBottom = new View(this);
        shadeBottom.setBackground(new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP,
            new int[] {0xF9080A0D, 0xF4080A0D, 0xBF080A0D, Color.TRANSPARENT}));
        FrameLayout.LayoutParams shadeBottomLp = new FrameLayout.LayoutParams(-1, dp(265), Gravity.BOTTOM);
        root.addView(shadeBottom, shadeBottomLp);

        LinearLayout top = new LinearLayout(this);
        top.setPadding(dp(23), dp(16), dp(23), 0);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setOrientation(LinearLayout.HORIZONTAL);
        FrameLayout.LayoutParams topLp = new FrameLayout.LayoutParams(-1, dp(75), Gravity.TOP);
        root.addView(top, topLp);

        LinearLayout brand = new LinearLayout(this);
        brand.setOrientation(LinearLayout.VERTICAL);
        TextView brandName = label("I photo", 30, WHITE, Typeface.BOLD);
        brandName.setLetterSpacing(.02f);
        brand.addView(brandName);
        TextView brandSub = label("THE WAY YOU SEE THE NIGHT", 8, GOLD, Typeface.NORMAL);
        brandSub.setLetterSpacing(.2f);
        brand.addView(brandSub);
        top.addView(brand, new LinearLayout.LayoutParams(0, -2, 1));

        flashButton = pill("플래시 끔");
        flashButton.setOnClickListener(v -> toggleFlash());
        top.addView(flashButton);
        TextView gridButton = pill("격자");
        LinearLayout.LayoutParams gp = new LinearLayout.LayoutParams(-2, -2);
        gp.leftMargin = dp(7);
        top.addView(gridButton, gp);
        gridButton.setOnClickListener(v -> grid.setVisibility(grid.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE));

        LinearLayout ratioRow = new LinearLayout(this);
        ratioRow.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams rr = new FrameLayout.LayoutParams(-1, dp(42), Gravity.TOP);
        rr.topMargin = dp(75);
        root.addView(ratioRow, rr);
        ratioButtons = new TextView[RATIOS.length];
        for (int i = 0; i < RATIOS.length; i++) {
            final String ratio = RATIOS[i];
            TextView choice = label(ratio, 12, WHITE, Typeface.BOLD);
            choice.setGravity(Gravity.CENTER);
            choice.setContentDescription("촬영 비율 " + ratio);
            LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(dp(69), dp(34));
            rp.setMargins(dp(3), 0, dp(3), 0);
            ratioRow.addView(choice, rp);
            ratioButtons[i] = choice;
            choice.setOnClickListener(v -> selectRatio(ratio));
        }
        updateRatioButtons();

        info = label("LUXURY NIGHT • CAMERA", 10, WHITE, Typeface.BOLD);
        info.setLetterSpacing(.13f);
        info.setGravity(Gravity.CENTER);
        info.setBackground(shape(0xC414181C, dp(20), 0, 0));
        FrameLayout.LayoutParams infoLp = new FrameLayout.LayoutParams(dp(265), dp(29), Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        infoLp.topMargin = dp(119);
        root.addView(info, infoLp);

        LinearLayout bottom = new LinearLayout(this);
        bottomControls = bottom;
        bottom.setPadding(dp(15), dp(8), dp(15), dp(8));
        bottom.setOrientation(LinearLayout.VERTICAL);
        bottom.setGravity(Gravity.CENTER_HORIZONTAL);
        FrameLayout.LayoutParams bottomLp = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
        root.addView(bottom, bottomLp);
        bottom.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> updateStageBounds());

        TextView section = label("SIGNATURE LOOKS", 10, GOLD, Typeface.BOLD);
        section.setLetterSpacing(.21f);
        section.setGravity(Gravity.CENTER);
        bottom.addView(section);

        LinearLayout presetRow = new LinearLayout(this);
        presetRow.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams pr = new LinearLayout.LayoutParams(-1, dp(42));
        pr.topMargin = dp(6);
        bottom.addView(presetRow, pr);
        ColorLooks.Look[] looks = ColorLooks.Look.values();
        presets = new TextView[looks.length];
        for (int i = 0; i < looks.length; i++) {
            ColorLooks.Look look = looks[i];
            TextView b = label(look.label, 11, look == selected ? INK : WHITE, Typeface.BOLD);
            b.setGravity(Gravity.CENTER);
            b.setBackground(shape(look == selected ? GOLD : 0x661E2226, dp(16),
                look == selected ? 0 : 0x33FFFFFF, dp(1)));
            b.setSingleLine(true);
            LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(0, dp(38), 1f);
            bp.setMargins(dp(3), 0, dp(3), 0);
            presetRow.addView(b, bp);
            presets[i] = b;
            b.setOnClickListener(v -> selectLook(look));
        }

        // Keep advanced sliders collapsed so the real capture area stays large.
        LinearLayout adjustments = new LinearLayout(this);
        adjustments.setOrientation(LinearLayout.VERTICAL);
        adjustments.setVisibility(View.GONE);
        bottom.addView(adjustments, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout adjustRow = new LinearLayout(this);
        adjustRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(-1, dp(37));
        ap.topMargin = dp(2);
        adjustments.addView(adjustRow, ap);
        TextView filterLabel = label("필터", 12, MUTED, Typeface.NORMAL);
        adjustRow.addView(filterLabel, new LinearLayout.LayoutParams(dp(42), -2));
        strengthSeek = new SeekBar(this);
        strengthSeek.setProgressTintList(android.content.res.ColorStateList.valueOf(GOLD));
        strengthSeek.setThumbTintList(android.content.res.ColorStateList.valueOf(GOLD));
        strengthSeek.setProgress(Math.round(intensity * 100));
        adjustRow.addView(strengthSeek, new LinearLayout.LayoutParams(0, -2, 1));
        TextView strengthValue = label("82%", 12, WHITE, Typeface.BOLD);
        strengthValue.setGravity(Gravity.RIGHT);
        adjustRow.addView(strengthValue, new LinearLayout.LayoutParams(dp(43), -2));
        strengthSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int value, boolean fromUser) {
                strengthValue.setText(value + "%");
                intensity = value / 100f;
            }
            @Override public void onStartTrackingTouch(SeekBar bar) {}
            @Override public void onStopTrackingTouch(SeekBar bar) { applyLook(); }
        });

        LinearLayout exposureRow = new LinearLayout(this);
        exposureRow.setGravity(Gravity.CENTER_VERTICAL);
        adjustments.addView(exposureRow, new LinearLayout.LayoutParams(-1, dp(35)));
        exposureRow.addView(label("노출", 12, MUTED, Typeface.NORMAL), new LinearLayout.LayoutParams(dp(42), -2));
        exposureSeek = new SeekBar(this);
        exposureSeek.setMax(40);
        exposureSeek.setProgress(15);
        exposureSeek.setProgressTintList(android.content.res.ColorStateList.valueOf(GOLD));
        exposureSeek.setThumbTintList(android.content.res.ColorStateList.valueOf(GOLD));
        exposureRow.addView(exposureSeek, new LinearLayout.LayoutParams(0, -2, 1));
        TextView exposureValue = label("-0.5", 12, WHITE, Typeface.BOLD);
        exposureValue.setGravity(Gravity.RIGHT);
        exposureRow.addView(exposureValue, new LinearLayout.LayoutParams(dp(43), -2));
        exposureSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar b, int progress, boolean fromUser) {
                float ev = (progress - 20) / 10f;
                exposureValue.setText(String.format(Locale.US, "%+.1f", ev));
                if (fromUser) setExposureEv(ev);
            }
            @Override public void onStartTrackingTouch(SeekBar b) {}
            @Override public void onStopTrackingTouch(SeekBar b) {}
        });

        LinearLayout tools = new LinearLayout(this);
        tools.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(-1, dp(37));
        tl.topMargin = dp(2);
        bottom.addView(tools, tl);
        TextView compare = label("원본 비교 (누르고 있기)", 11, MUTED, Typeface.NORMAL);
        compare.setGravity(Gravity.CENTER);
        compare.setBackground(shape(0x55000000, dp(14), 0x33FFFFFF, dp(1)));
        tools.addView(compare, new LinearLayout.LayoutParams(0, dp(30), 1));
        compare.setOnTouchListener((v, event) -> {
            if (media3 == null || recording != null) return false;
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN: media3.setEffects(Collections.emptyList()); return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL: applyLook(); return true;
                default: return true;
            }
        });
        TextView zoom1 = label("1×", 13, GOLD, Typeface.BOLD);
        zoom1.setGravity(Gravity.CENTER);
        zoom1.setOnClickListener(v -> setZoom(1f));
        tools.addView(zoom1, new LinearLayout.LayoutParams(dp(53), dp(35)));
        TextView zoom2 = label("2×", 13, WHITE, Typeface.BOLD);
        zoom2.setGravity(Gravity.CENTER);
        zoom2.setOnClickListener(v -> setZoom(2f));
        tools.addView(zoom2, new LinearLayout.LayoutParams(dp(40), dp(35)));
        TextView adjustToggle = label("보정 +", 12, GOLD, Typeface.BOLD);
        adjustToggle.setGravity(Gravity.CENTER);
        tools.addView(adjustToggle, new LinearLayout.LayoutParams(dp(62), dp(35)));
        adjustToggle.setOnClickListener(v -> {
            boolean open = adjustments.getVisibility() != View.VISIBLE;
            adjustments.setVisibility(open ? View.VISIBLE : View.GONE);
            adjustToggle.setText(open ? "보정 −" : "보정 +");
        });

        LinearLayout modes = new LinearLayout(this);
        modes.setGravity(Gravity.CENTER);
        bottom.addView(modes, new LinearLayout.LayoutParams(-1, dp(34)));
        modePhoto = label("사진", 14, GOLD, Typeface.BOLD);
        modePhoto.setGravity(Gravity.CENTER);
        modes.addView(modePhoto, new LinearLayout.LayoutParams(dp(72), dp(33)));
        modeVideo = label("동영상", 14, MUTED, Typeface.NORMAL);
        modeVideo.setGravity(Gravity.CENTER);
        modes.addView(modeVideo, new LinearLayout.LayoutParams(dp(82), dp(33)));
        modePhoto.setOnClickListener(v -> changeMode(false));
        modeVideo.setOnClickListener(v -> changeMode(true));

        LinearLayout shutterRow = new LinearLayout(this);
        shutterRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams sr = new LinearLayout.LayoutParams(-1, dp(82));
        bottom.addView(shutterRow, sr);
        TextView gallery = label("최근 사진 ↗", 12, WHITE, Typeface.NORMAL);
        gallery.setGravity(Gravity.CENTER);
        gallery.setOnClickListener(v -> openGallery());
        shutterRow.addView(gallery, new LinearLayout.LayoutParams(0, dp(62), 1));

        FrameLayout shutterOuter = new FrameLayout(this);
        shutterOuter.setBackground(shape(Color.TRANSPARENT, dp(44), WHITE, dp(3)));
        LinearLayout.LayoutParams so = new LinearLayout.LayoutParams(dp(82), dp(82));
        shutterRow.addView(shutterOuter, so);
        capture = label("", 12, WHITE, Typeface.BOLD);
        capture.setBackground(shape(WHITE, dp(32), 0, 0));
        FrameLayout.LayoutParams cl = new FrameLayout.LayoutParams(dp(65), dp(65), Gravity.CENTER);
        shutterOuter.addView(capture, cl);
        shutterOuter.setOnClickListener(v -> onShutter());

        TextView flip = label("카메라 전환 ↻", 12, WHITE, Typeface.NORMAL);
        flip.setGravity(Gravity.CENTER);
        flip.setOnClickListener(v -> switchCamera());
        shutterRow.addView(flip, new LinearLayout.LayoutParams(0, dp(62), 1));

        recordTimer = label("", 13, WHITE, Typeface.BOLD);
        recordTimer.setGravity(Gravity.CENTER);
        bottom.addView(recordTimer, new LinearLayout.LayoutParams(-1, dp(20)));
        captureLabel = label("사진과 영상에 동일한 색감을 적용합니다", 10, MUTED, Typeface.NORMAL);
        captureLabel.setGravity(Gravity.CENTER);
        bottom.addView(captureLabel, new LinearLayout.LayoutParams(-1, dp(17)));
        root.post(this::updateStageBounds);
    }

    private void selectRatio(String ratio) {
        if (busy || recording != null) {
            showMessage("촬영 중에는 화면 비율을 바꿀 수 없습니다.");
            return;
        }
        if (ratio.equals(frameRatio)) return;
        frameRatio = ratio;
        getPreferences(MODE_PRIVATE).edit().putString("capture_ratio", ratio).apply();
        updateRatioButtons();
        updateCaptureFrame();
        setInfo();
        // Reattach after PreviewView receives its new size. CameraX uses this
        // viewport for photo/video crop rectangles, not merely a visual mask.
        preview.postDelayed(() -> {
            if (controller == null || recording != null) return;
            try {
                controller.unbind();
                preview.setController(null);
                preview.setController(controller);
                controller.bindToLifecycle(this);
                setExposureEv((exposureSeek.getProgress() - 20) / 10f);
            } catch (Exception e) {
                Log.e(TAG, "Ratio rebind failed", e);
                showMessage("비율 변경 실패, 카메라를 다시 실행해 주세요.");
            }
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

    private void updateStageBounds() {
        if (previewStage == null || bottomControls == null || bottomControls.getHeight() == 0) return;
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) previewStage.getLayoutParams();
        int top = dp(153);
        int bottom = bottomControls.getHeight() + dp(8);
        if (lp.topMargin != top || lp.bottomMargin != bottom) {
            lp.topMargin = top;
            lp.bottomMargin = bottom;
            previewStage.setLayoutParams(lp);
        } else updateCaptureFrame();
    }

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
        int maxW = Math.max(1, stageW - dp(18));
        int maxH = Math.max(1, stageH - dp(12));
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
        if (controller == null) { showMessage("카메라를 준비하는 중입니다."); return; }
        if (isVideo) {
            if (recording != null) stopRecording();
            else startRecording();
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
            controller.takePicture(options, ContextCompat.getMainExecutor(this), new ImageCapture.OnImageSavedCallback() {
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
            recording = controller.startRecording(options, hasMic ? AudioConfig.create(true) : AudioConfig.AUDIO_DISABLED,
                ContextCompat.getMainExecutor(this), event -> {
                    if (event instanceof VideoRecordEvent.Start) {
                        startedAt = android.os.SystemClock.elapsedRealtime();
                        handler.post(tick);
                        capture.setBackground(shape(0xFFE74C4C, dp(7), 0, 0));
                    } else if (event instanceof VideoRecordEvent.Finalize) {
                        VideoRecordEvent.Finalize end = (VideoRecordEvent.Finalize) event;
                        handler.removeCallbacks(tick);
                        recordTimer.setText("");
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
        if (controller == null || controller.getCameraInfo() == null || controller.getCameraControl() == null) return;
        ExposureState state = controller.getCameraInfo().getExposureState();
        if (state == null || !state.isExposureCompensationSupported()) return;
        float step = state.getExposureCompensationStep().floatValue();
        if (step == 0f) return;
        int index = Math.round(ev / step);
        Range<Integer> range = state.getExposureCompensationRange();
        index = Math.max(range.getLower(), Math.min(range.getUpper(), index));
        controller.getCameraControl().setExposureCompensationIndex(index);
    }

    private void setZoom(float ratio) {
        if (controller == null) return;
        try { controller.setZoomRatio(ratio); }
        catch (Exception ignored) { showMessage("이 배율은 지원되지 않습니다."); }
    }

    private void switchCamera() {
        if (controller == null || recording != null || busy) return;
        try {
            isFront = !isFront;
            controller.setCameraSelector(isFront ? CameraSelector.DEFAULT_FRONT_CAMERA : CameraSelector.DEFAULT_BACK_CAMERA);
            setExposureEv(-.5f);
        } catch (Exception e) {
            isFront = !isFront;
            showMessage("이 카메라를 사용할 수 없습니다.");
        }
    }

    private void toggleFlash() {
        if (controller == null) return;
        flash = !flash;
        flashButton.setText(flash ? "플래시 켬" : "플래시 끔");
        controller.setImageCaptureFlashMode(flash ? ImageCapture.FLASH_MODE_ON : ImageCapture.FLASH_MODE_OFF);
        // Video uses torch rather than still-image flash.
        try { controller.enableTorch(flash && isVideo); } catch (Exception ignored) {}
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
        if (recording != null) recording.stop();
        if (controller != null) controller.unbind();
        if (media3 != null) media3.close();
        super.onDestroy();
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
            paint.setColor(0xD8EDE9E2);
            canvas.drawRect(inset, inset, w-inset, h-inset, paint);
            paint.setStrokeWidth(dp(2.5f));
            paint.setColor(GOLD);
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
