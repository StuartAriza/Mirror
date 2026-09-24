package com.stuart.smartmirror;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextClock;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity {
    private static final long WEATHER_REFRESH_MS = 30L * 60L * 1000L;

    private Handler handler;
    private ExecutorService networkExecutor;
    private TextView weatherLocation;
    private TextView weatherSummary;
    private TextView weatherDetails;
    private boolean weatherRequestRunning;
    private boolean startupComplete;

    private final Runnable weatherRefresh = new Runnable() {
        @Override
        public void run() {
            refreshWeather(false);
            handler.postDelayed(this, WEATHER_REFRESH_MS);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            requestWindowFeature(Window.FEATURE_NO_TITLE);
            getWindow().setStatusBarColor(Color.BLACK);
            getWindow().addFlags(
                    WindowManager.LayoutParams.FLAG_FULLSCREEN
                            | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

            handler = new Handler(Looper.getMainLooper());
            WeatherRepository.installModernTls();
            networkExecutor = Executors.newSingleThreadExecutor();
            View controls = buildMainScreen();
            controls.setOnClickListener(view -> Toast.makeText(
                    this,
                    R.string.hold_for_controls,
                    Toast.LENGTH_SHORT).show());
            controls.setOnLongClickListener(view -> {
                showRecoveryControls();
                return true;
            });
            startupComplete = true;
        } catch (Throwable error) {
            showStartupError(error);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!startupComplete) {
            return;
        }
        try {
            renderCachedWeather();
            handler.removeCallbacks(weatherRefresh);
            handler.post(weatherRefresh);
        } catch (Throwable error) {
            startupComplete = false;
            showStartupError(error);
        }
    }

    @Override
    protected void onPause() {
        if (handler != null) {
            handler.removeCallbacks(weatherRefresh);
        }
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (networkExecutor != null) {
            networkExecutor.shutdownNow();
        }
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        showRecoveryControls();
    }

    private void showRecoveryControls() {
        final String[] actions = {
                getString(R.string.open_mirror_settings),
                getString(R.string.open_android_settings),
                getString(R.string.enable_navigation_strip),
                getString(R.string.exit_to_home),
                getString(R.string.stay_in_mirror)
        };

        new AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK)
                .setTitle(R.string.recovery_controls)
                .setItems(actions, (dialog, which) -> {
                    switch (which) {
                        case 0:
                            openMirrorSettings();
                            break;
                        case 1:
                            launchSettings(Settings.ACTION_SETTINGS);
                            break;
                        case 2:
                            launchSettings(Settings.ACTION_ACCESSIBILITY_SETTINGS);
                            break;
                        case 3:
                            exitToHome();
                            break;
                        default:
                            dialog.dismiss();
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void openMirrorSettings() {
        startActivity(new Intent(this, MirrorSettingsActivity.class));
    }

    private void renderCachedWeather() {
        WeatherRepository.WeatherData cached = WeatherRepository.readCache(this);
        if (cached != null) {
            renderWeather(cached);
            return;
        }

        String city = WeatherRepository.getConfiguredCity(this);
        if (city.isEmpty()) {
            weatherLocation.setText(R.string.weather);
            weatherSummary.setText(R.string.weather_needs_city);
            weatherDetails.setText(R.string.weather_tap_to_configure);
        } else {
            weatherLocation.setText(city.toUpperCase(Locale.getDefault()));
            weatherSummary.setText(R.string.weather_loading);
            weatherDetails.setText(R.string.weather_waiting_for_update);
        }
    }

    private void refreshWeather(boolean force) {
        String city = WeatherRepository.getConfiguredCity(this);
        if (city.isEmpty() || weatherRequestRunning) {
            return;
        }

        WeatherRepository.WeatherData cached = WeatherRepository.readCache(this);
        if (!force && cached != null
                && System.currentTimeMillis() - cached.updatedAt < WEATHER_REFRESH_MS) {
            return;
        }

        weatherRequestRunning = true;
        if (cached == null) {
            weatherSummary.setText(R.string.weather_loading);
        }

        WeatherRepository.fetch(this, networkExecutor, new WeatherRepository.Callback() {
            @Override
            public void onSuccess(WeatherRepository.WeatherData data) {
                weatherRequestRunning = false;
                renderWeather(data);
            }

            @Override
            public void onError(String message) {
                weatherRequestRunning = false;
                if (WeatherRepository.readCache(MainActivity.this) == null) {
                    weatherSummary.setText(R.string.weather_unavailable);
                    weatherDetails.setText(message);
                } else {
                    Toast.makeText(MainActivity.this, message, Toast.LENGTH_LONG).show();
                }
            }
        });
    }

    private void renderWeather(WeatherRepository.WeatherData data) {
        weatherLocation.setText(data.location.toUpperCase(Locale.getDefault()));
        weatherSummary.setText(getString(
                R.string.weather_summary_format,
                data.temperature,
                data.unit,
                data.condition));
        weatherDetails.setText(getString(
                R.string.weather_details_format,
                data.high,
                data.unit,
                data.low,
                data.unit,
                WeatherRepository.formatUpdatedTime(data.updatedAt)));
    }

    private void launchSettings(String action) {
        try {
            startActivity(new Intent(action));
        } catch (ActivityNotFoundException exception) {
            if (!Settings.ACTION_SETTINGS.equals(action)) {
                launchSettings(Settings.ACTION_SETTINGS);
            } else {
                Toast.makeText(this, R.string.settings_unavailable, Toast.LENGTH_LONG).show();
            }
        }
    }

    private void exitToHome() {
        Intent home = new Intent(Intent.ACTION_MAIN);
        home.addCategory(Intent.CATEGORY_HOME);
        home.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(home);
        finish();
    }

    private View buildMainScreen() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        root.setKeepScreenOn(true);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        FrameLayout.LayoutParams contentParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP);
        contentParams.setMargins(dp(64), dp(112), dp(64), 0);
        root.addView(content, contentParams);

        TextClock clock = new TextClock(this);
        clock.setFormat12Hour("h:mm");
        clock.setFormat24Hour("HH:mm");
        clock.setIncludeFontPadding(false);
        clock.setTextColor(Color.WHITE);
        clock.setTextSize(104);
        content.addView(clock);

        TextClock date = new TextClock(this);
        date.setFormat12Hour("EEEE, MMMM d");
        date.setFormat24Hour("EEEE, MMMM d");
        date.setTextColor(0xD9FFFFFF);
        date.setTextSize(30);
        LinearLayout.LayoutParams dateParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        dateParams.topMargin = dp(12);
        content.addView(date, dateParams);

        LinearLayout weatherPanel = new LinearLayout(this);
        weatherPanel.setOrientation(LinearLayout.VERTICAL);
        weatherPanel.setPadding(dp(28), dp(28), dp(28), dp(28));
        GradientDrawable panel = new GradientDrawable();
        panel.setColor(0x12000000);
        panel.setStroke(dp(1), 0x38FFFFFF);
        panel.setCornerRadius(dp(18));
        weatherPanel.setBackground(panel);
        LinearLayout.LayoutParams weatherParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        weatherParams.topMargin = dp(88);
        content.addView(weatherPanel, weatherParams);

        weatherLocation = new TextView(this);
        weatherLocation.setText(R.string.weather);
        weatherLocation.setTextColor(0xD9FFFFFF);
        weatherLocation.setTextSize(18);
        weatherLocation.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        weatherPanel.addView(weatherLocation);

        weatherSummary = new TextView(this);
        weatherSummary.setText(R.string.weather_needs_city);
        weatherSummary.setTextColor(Color.WHITE);
        weatherSummary.setTextSize(30);
        LinearLayout.LayoutParams summaryParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        summaryParams.topMargin = dp(18);
        weatherPanel.addView(weatherSummary, summaryParams);

        weatherDetails = new TextView(this);
        weatherDetails.setText(R.string.weather_tap_to_configure);
        weatherDetails.setTextColor(0x8AFFFFFF);
        weatherDetails.setTextSize(18);
        LinearLayout.LayoutParams detailParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        detailParams.topMargin = dp(8);
        weatherPanel.addView(weatherDetails, detailParams);
        weatherPanel.setOnClickListener(view -> openMirrorSettings());

        TextView controls = new TextView(this);
        controls.setText(R.string.recovery_ellipsis);
        controls.setTextColor(Color.WHITE);
        controls.setTextSize(24);
        controls.setAlpha(0.28f);
        controls.setGravity(Gravity.CENTER);
        controls.setContentDescription(getString(R.string.open_recovery_controls));
        FrameLayout.LayoutParams controlsParams = new FrameLayout.LayoutParams(
                dp(64), dp(64), Gravity.TOP | Gravity.END);
        root.addView(controls, controlsParams);

        setContentView(root);
        return controls;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void showStartupError(Throwable error) {
        Log.e("Mirror", "Mirror startup failed", error);

        int padding = Math.round(48 * getResources().getDisplayMetrics().density);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(padding, padding, padding, padding);
        content.setBackgroundColor(Color.BLACK);

        TextView title = new TextView(this);
        title.setText(R.string.startup_error_title);
        title.setTextColor(Color.WHITE);
        title.setTextSize(34);
        content.addView(title);

        TextView explanation = new TextView(this);
        explanation.setText(R.string.startup_error_explanation);
        explanation.setTextColor(0xFFCCCCCC);
        explanation.setTextSize(19);
        explanation.setPadding(0, padding / 2, 0, padding / 2);
        content.addView(explanation);

        TextView details = new TextView(this);
        details.setText(Log.getStackTraceString(error));
        details.setTextColor(0xFFFF8080);
        details.setTextSize(14);
        details.setTextIsSelectable(true);
        content.addView(details);

        Button settings = new Button(this);
        settings.setText(R.string.open_android_settings);
        settings.setGravity(Gravity.CENTER);
        settings.setOnClickListener(view -> launchSettings(Settings.ACTION_SETTINGS));
        content.addView(settings);

        Button home = new Button(this);
        home.setText(R.string.exit_to_home);
        home.setGravity(Gravity.CENTER);
        home.setOnClickListener(view -> exitToHome());
        content.addView(home);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Color.BLACK);
        scroll.addView(content);
        setContentView(scroll);
    }
}
