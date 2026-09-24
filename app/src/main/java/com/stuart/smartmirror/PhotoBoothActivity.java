package com.stuart.smartmirror;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.hardware.Camera;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@SuppressWarnings("deprecation")
public final class PhotoBoothActivity extends Activity implements SurfaceHolder.Callback {
    private static final int PERMISSION_REQUEST = 42;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService saveExecutor = Executors.newSingleThreadExecutor();

    private FrameLayout root;
    private SurfaceView surfaceView;
    private ImageView reviewImage;
    private TextView countdown;
    private LinearLayout actionRow;
    private Button captureButton;
    private Button switchButton;
    private Camera camera;
    private byte[] pendingJpeg;
    private boolean surfaceReady;
    private boolean captureRunning;
    private int cameraId = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setStatusBarColor(Color.BLACK);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN
                | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        buildUi();
        requestNeededPermissions();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (hasPermissions() && surfaceReady && pendingJpeg == null) {
            openCamera();
        }
    }

    @Override
    protected void onPause() {
        stopCountdown();
        releaseCamera();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        saveExecutor.shutdownNow();
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (pendingJpeg != null) {
            retake();
        } else {
            finish();
        }
    }

    private void buildUi() {
        root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        surfaceView = new SurfaceView(this);
        surfaceView.getHolder().addCallback(this);
        root.addView(surfaceView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

        reviewImage = new ImageView(this);
        reviewImage.setBackgroundColor(Color.BLACK);
        reviewImage.setScaleType(ImageView.ScaleType.CENTER_CROP);
        reviewImage.setVisibility(View.GONE);
        root.addView(reviewImage, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

        countdown = new TextView(this);
        countdown.setTextColor(Color.WHITE);
        countdown.setTextSize(150);
        countdown.setGravity(Gravity.CENTER);
        countdown.setVisibility(View.GONE);
        countdown.setShadowLayer(16f, 0f, 4f, Color.BLACK);
        root.addView(countdown, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

        Button back = overlayButton(R.string.photo_booth_back);
        back.setOnClickListener(view -> onBackPressed());
        FrameLayout.LayoutParams backParams = new FrameLayout.LayoutParams(
                dp(190), dp(72), Gravity.TOP | Gravity.START);
        backParams.setMargins(dp(24), dp(24), 0, 0);
        root.addView(back, backParams);

        TextView title = new TextView(this);
        title.setText(R.string.photo_booth);
        title.setTextColor(Color.WHITE);
        title.setTextSize(26);
        title.setGravity(Gravity.CENTER);
        title.setShadowLayer(8f, 0f, 2f, Color.BLACK);
        FrameLayout.LayoutParams titleParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, dp(72),
                Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        titleParams.topMargin = dp(24);
        root.addView(title, titleParams);

        actionRow = new LinearLayout(this);
        actionRow.setOrientation(LinearLayout.HORIZONTAL);
        actionRow.setGravity(Gravity.CENTER);
        actionRow.setPadding(dp(24), dp(12), dp(24), dp(24));
        FrameLayout.LayoutParams actionParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, dp(116), Gravity.BOTTOM);
        root.addView(actionRow, actionParams);

        switchButton = overlayButton(R.string.photo_booth_switch_camera);
        switchButton.setOnClickListener(view -> switchCamera());
        actionRow.addView(switchButton, weightedParams());

        captureButton = overlayButton(R.string.photo_booth_capture);
        captureButton.setOnClickListener(view -> startCountdown(3));
        LinearLayout.LayoutParams captureParams = weightedParams();
        captureParams.setMargins(dp(12), 0, 0, 0);
        actionRow.addView(captureButton, captureParams);

        setContentView(root);
        updateSwitchVisibility();
    }

    private Button overlayButton(int label) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextColor(Color.WHITE);
        button.setTextSize(18);
        button.setGravity(Gravity.CENTER);
        GradientDrawable background = new GradientDrawable();
        background.setColor(0xCC111111);
        background.setStroke(dp(1), 0x99FFFFFF);
        background.setCornerRadius(dp(14));
        button.setBackground(background);
        return button;
    }

    private LinearLayout.LayoutParams weightedParams() {
        return new LinearLayout.LayoutParams(0, dp(80), 1f);
    }

    private void requestNeededPermissions() {
        if (hasPermissions()) {
            return;
        }
        requestPermissions(new String[] {
                Manifest.permission.CAMERA,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
        }, PERMISSION_REQUEST);
    }

    private boolean hasPermissions() {
        return checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != PERMISSION_REQUEST) {
            return;
        }
        if (hasPermissions()) {
            if (surfaceReady) {
                openCamera();
            }
        } else {
            showPermissionRecovery();
        }
    }

    private void showPermissionRecovery() {
        releaseCamera();
        actionRow.removeAllViews();

        LinearLayout recovery = new LinearLayout(this);
        recovery.setOrientation(LinearLayout.VERTICAL);
        recovery.setGravity(Gravity.CENTER);
        recovery.setPadding(dp(48), dp(48), dp(48), dp(48));
        recovery.setBackgroundColor(Color.BLACK);

        TextView title = new TextView(this);
        title.setText(R.string.photo_booth_permission_title);
        title.setTextColor(Color.WHITE);
        title.setTextSize(34);
        title.setGravity(Gravity.CENTER);
        recovery.addView(title);

        TextView message = new TextView(this);
        message.setText(R.string.photo_booth_permission_message);
        message.setTextColor(0xFFCCCCCC);
        message.setTextSize(20);
        message.setGravity(Gravity.CENTER);
        message.setPadding(0, dp(24), 0, dp(32));
        recovery.addView(message);

        Button settingsButton = overlayButton(R.string.photo_booth_open_settings);
        settingsButton.setOnClickListener(view -> {
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + getPackageName()));
            startActivity(intent);
        });
        recovery.addView(settingsButton, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(80)));

        Button mirrorButton = overlayButton(R.string.photo_booth_back);
        mirrorButton.setOnClickListener(view -> finish());
        LinearLayout.LayoutParams mirrorParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(80));
        mirrorParams.topMargin = dp(20);
        recovery.addView(mirrorButton, mirrorParams);

        root.addView(recovery, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        surfaceReady = true;
        if (hasPermissions() && pendingJpeg == null) {
            openCamera();
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        if (camera != null) {
            startPreview();
        }
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        surfaceReady = false;
        releaseCamera();
    }

    private void openCamera() {
        if (camera != null || !surfaceReady || !hasPermissions()) {
            return;
        }
        try {
            if (cameraId < 0) {
                cameraId = preferredCameraId();
            }
            if (cameraId < 0) {
                showCameraError();
                return;
            }
            camera = Camera.open(cameraId);
            configureCamera();
            camera.setPreviewDisplay(surfaceView.getHolder());
            startPreview();
        } catch (Throwable error) {
            releaseCamera();
            showCameraError();
        }
    }

    private int preferredCameraId() {
        int fallback = -1;
        Camera.CameraInfo info = new Camera.CameraInfo();
        for (int id = 0; id < Camera.getNumberOfCameras(); id++) {
            Camera.getCameraInfo(id, info);
            if (fallback < 0) {
                fallback = id;
            }
            if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                return id;
            }
        }
        return fallback;
    }

    private void configureCamera() {
        Camera.Parameters parameters = camera.getParameters();
        List<String> focusModes = parameters.getSupportedFocusModes();
        if (focusModes != null
                && focusModes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)) {
            parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
        }
        Camera.Size pictureSize = choosePictureSize(parameters.getSupportedPictureSizes());
        if (pictureSize != null) {
            parameters.setPictureSize(pictureSize.width, pictureSize.height);
        }
        parameters.setJpegQuality(90);
        parameters.setRotation(photoRotation());
        camera.setParameters(parameters);
        camera.setDisplayOrientation(displayOrientation());
    }

    private Camera.Size choosePictureSize(List<Camera.Size> sizes) {
        if (sizes == null || sizes.isEmpty()) {
            return null;
        }
        Camera.Size best = null;
        for (Camera.Size size : sizes) {
            long pixels = (long) size.width * size.height;
            if (pixels > 8_000_000L) {
                continue;
            }
            if (best == null || pixels > (long) best.width * best.height) {
                best = size;
            }
        }
        return best != null ? best : sizes.get(sizes.size() - 1);
    }

    private void startPreview() {
        if (camera == null || !surfaceReady) {
            return;
        }
        try {
            camera.stopPreview();
        } catch (Throwable ignored) {
            // Some legacy camera drivers throw when no preview has started yet.
        }
        try {
            camera.setPreviewDisplay(surfaceView.getHolder());
            camera.startPreview();
        } catch (Throwable error) {
            releaseCamera();
            showCameraError();
        }
    }

    private int displayOrientation() {
        Camera.CameraInfo info = new Camera.CameraInfo();
        Camera.getCameraInfo(cameraId, info);
        int degrees = displayDegrees();
        if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            return (360 - ((info.orientation + degrees) % 360)) % 360;
        }
        return (info.orientation - degrees + 360) % 360;
    }

    private int photoRotation() {
        Camera.CameraInfo info = new Camera.CameraInfo();
        Camera.getCameraInfo(cameraId, info);
        int degrees = displayDegrees();
        if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            return (info.orientation - degrees + 360) % 360;
        }
        return (info.orientation + degrees) % 360;
    }

    private int displayDegrees() {
        int rotation = getWindowManager().getDefaultDisplay().getRotation();
        switch (rotation) {
            case Surface.ROTATION_90:
                return 90;
            case Surface.ROTATION_180:
                return 180;
            case Surface.ROTATION_270:
                return 270;
            default:
                return 0;
        }
    }

    private void startCountdown(int value) {
        if (captureRunning || camera == null) {
            return;
        }
        captureRunning = true;
        captureButton.setEnabled(false);
        switchButton.setEnabled(false);
        countdown.setVisibility(View.VISIBLE);
        tickCountdown(value);
    }

    private void tickCountdown(int value) {
        if (!captureRunning) {
            return;
        }
        if (value <= 0) {
            countdown.setText("");
            takePhoto();
            return;
        }
        countdown.setText(String.valueOf(value));
        handler.postDelayed(() -> tickCountdown(value - 1), 1000L);
    }

    private void takePhoto() {
        if (camera == null) {
            resetCaptureControls();
            return;
        }
        try {
            camera.takePicture(null, null, (data, source) -> {
                pendingJpeg = data;
                showReview(data);
            });
        } catch (Throwable error) {
            resetCaptureControls();
            Toast.makeText(this, R.string.photo_booth_prepare_failed, Toast.LENGTH_LONG).show();
            startPreview();
        }
    }

    private void showReview(byte[] jpeg) {
        captureRunning = false;
        countdown.setVisibility(View.GONE);
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(jpeg, 0, jpeg.length, bounds);
        int sample = 1;
        while (bounds.outWidth / sample > 1440 || bounds.outHeight / sample > 2560) {
            sample *= 2;
        }
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sample;
        Bitmap bitmap = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.length, options);
        reviewImage.setImageBitmap(bitmap);
        reviewImage.setVisibility(View.VISIBLE);
        surfaceView.setVisibility(View.INVISIBLE);

        actionRow.removeAllViews();
        Button retake = overlayButton(R.string.photo_booth_retake);
        retake.setOnClickListener(view -> retake());
        actionRow.addView(retake, weightedParams());

        Button save = overlayButton(R.string.photo_booth_save);
        save.setOnClickListener(view -> savePhoto());
        LinearLayout.LayoutParams saveParams = weightedParams();
        saveParams.setMargins(dp(12), 0, 0, 0);
        actionRow.addView(save, saveParams);
    }

    private void retake() {
        pendingJpeg = null;
        reviewImage.setImageDrawable(null);
        reviewImage.setVisibility(View.GONE);
        surfaceView.setVisibility(View.VISIBLE);
        restoreCaptureActions();
        if (camera == null) {
            openCamera();
        } else {
            startPreview();
        }
    }

    private void savePhoto() {
        final byte[] jpeg = pendingJpeg;
        if (jpeg == null) {
            return;
        }
        setActionButtonsEnabled(false);
        saveExecutor.execute(() -> {
            File saved = null;
            try {
                File pictures = Environment.getExternalStoragePublicDirectory(
                        Environment.DIRECTORY_PICTURES);
                File folder = new File(pictures, "MirrorPhotobooth");
                if ((folder.exists() || folder.mkdirs()) && folder.isDirectory()) {
                    String timestamp = new SimpleDateFormat(
                            "yyyyMMdd_HHmmss", Locale.US).format(new Date());
                    saved = new File(folder, "Mirror_" + timestamp + ".jpg");
                    try (FileOutputStream output = new FileOutputStream(saved)) {
                        output.write(jpeg);
                    }
                }
            } catch (Throwable ignored) {
                saved = null;
            }
            final File completed = saved;
            handler.post(() -> {
                if (completed == null) {
                    setActionButtonsEnabled(true);
                    Toast.makeText(this, R.string.photo_booth_save_failed,
                            Toast.LENGTH_LONG).show();
                    return;
                }
                MediaScannerConnection.scanFile(this,
                        new String[] { completed.getAbsolutePath() },
                        new String[] { "image/jpeg" }, null);
                Toast.makeText(this, R.string.photo_booth_saved, Toast.LENGTH_LONG).show();
                retake();
            });
        });
    }

    private void switchCamera() {
        int count = Camera.getNumberOfCameras();
        if (count < 2 || captureRunning) {
            return;
        }
        cameraId = (cameraId + 1) % count;
        releaseCamera();
        openCamera();
    }

    private void restoreCaptureActions() {
        actionRow.removeAllViews();
        actionRow.addView(switchButton, weightedParams());
        LinearLayout.LayoutParams captureParams = weightedParams();
        captureParams.setMargins(dp(12), 0, 0, 0);
        actionRow.addView(captureButton, captureParams);
        resetCaptureControls();
        updateSwitchVisibility();
    }

    private void resetCaptureControls() {
        captureRunning = false;
        countdown.setVisibility(View.GONE);
        captureButton.setEnabled(true);
        switchButton.setEnabled(true);
    }

    private void setActionButtonsEnabled(boolean enabled) {
        for (int index = 0; index < actionRow.getChildCount(); index++) {
            actionRow.getChildAt(index).setEnabled(enabled);
        }
    }

    private void updateSwitchVisibility() {
        switchButton.setVisibility(
                Camera.getNumberOfCameras() > 1 ? View.VISIBLE : View.GONE);
    }

    private void stopCountdown() {
        captureRunning = false;
        handler.removeCallbacksAndMessages(null);
        if (countdown != null) {
            countdown.setVisibility(View.GONE);
        }
    }

    private void releaseCamera() {
        if (camera == null) {
            return;
        }
        try {
            camera.stopPreview();
        } catch (Throwable ignored) {
            // The preview may already be stopped after takePicture.
        }
        camera.release();
        camera = null;
    }

    private void showCameraError() {
        Toast.makeText(this, R.string.photo_booth_unavailable, Toast.LENGTH_LONG).show();
        captureButton.setEnabled(false);
        switchButton.setEnabled(false);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
