package com.stuart.smartmirror;

import android.app.Activity;
import android.app.AlertDialog;
import android.media.AudioManager;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.content.res.ColorStateList;
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
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
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
    private WeatherIconView weatherIcon;
    private TextView spotifyTitle;
    private TextView spotifyArtist;
    private Button spotifyPrevious;
    private Button spotifyPlayPause;
    private Button spotifyNext;
    private LinearLayout spotifyPanel;
    private LinearLayout spotifyBody;
    private FrameLayout noteLayer;
    private AudioManager audioManager;
    private SeekBar spotifyVolume;
    private SpotifyMediaController spotifyMediaController;
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
            audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
            View controls = buildMainScreen();
            spotifyMediaController = new SpotifyMediaController(
                    this, handler, this::renderSpotifyState);
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
            spotifyMediaController.start();
            syncSpotifyVolume();
            renderPostIts();
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
        if (spotifyMediaController != null) {
            spotifyMediaController.stop();
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
                getString(R.string.setup_spotify_controls),
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
                            launchSettings(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS);
                            break;
                        case 2:
                            launchSettings(Settings.ACTION_SETTINGS);
                            break;
                        case 3:
                            launchSettings(Settings.ACTION_ACCESSIBILITY_SETTINGS);
                            break;
                        case 4:
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
                data.unit));
        weatherIcon.setWeather(data.weatherCode, data.isDay);
        weatherIcon.setContentDescription(data.condition);
        weatherDetails.setText(getString(
                R.string.weather_details_format,
                data.high,
                data.unit,
                data.low,
                data.unit));
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
        contentParams.setMargins(dp(52), dp(72), dp(52), 0);
        root.addView(content, contentParams);

        LinearLayout topRow = new LinearLayout(this);
        topRow.setOrientation(LinearLayout.HORIZONTAL);
        topRow.setGravity(Gravity.TOP);
        content.addView(topRow, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        LinearLayout clockColumn = new LinearLayout(this);
        clockColumn.setOrientation(LinearLayout.VERTICAL);
        topRow.addView(clockColumn, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.56f));

        TextClock clock = new TextClock(this);
        clock.setFormat12Hour("h:mm");
        clock.setFormat24Hour("HH:mm");
        clock.setIncludeFontPadding(false);
        clock.setTextColor(Color.WHITE);
        clock.setTextSize(92);
        clock.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        clockColumn.addView(clock);

        TextClock date = new TextClock(this);
        date.setFormat12Hour("EEEE, MMMM d");
        date.setFormat24Hour("EEEE, MMMM d");
        date.setTextColor(0xD9FFFFFF);
        date.setTextSize(25);
        date.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        LinearLayout.LayoutParams dateParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        dateParams.topMargin = dp(12);
        clockColumn.addView(date, dateParams);

        LinearLayout weatherPanel = new LinearLayout(this);
        weatherPanel.setOrientation(LinearLayout.VERTICAL);
        weatherPanel.setGravity(Gravity.END);
        weatherPanel.setPadding(dp(16), dp(8), 0, dp(8));
        LinearLayout.LayoutParams weatherParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.44f);
        weatherParams.leftMargin = dp(28);
        topRow.addView(weatherPanel, weatherParams);

        weatherLocation = new TextView(this);
        weatherLocation.setText(R.string.weather);
        weatherLocation.setTextColor(0xD9FFFFFF);
        weatherLocation.setTextSize(18);
        weatherLocation.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        weatherLocation.setGravity(Gravity.END);
        weatherPanel.addView(weatherLocation);

        LinearLayout currentWeatherRow = new LinearLayout(this);
        currentWeatherRow.setOrientation(LinearLayout.HORIZONTAL);
        currentWeatherRow.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
        LinearLayout.LayoutParams currentWeatherParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        currentWeatherParams.topMargin = dp(12);
        weatherPanel.addView(currentWeatherRow, currentWeatherParams);

        weatherIcon = new WeatherIconView(this);
        LinearLayout.LayoutParams weatherIconParams = new LinearLayout.LayoutParams(
                dp(76), dp(76));
        weatherIconParams.rightMargin = dp(16);
        currentWeatherRow.addView(weatherIcon, weatherIconParams);

        weatherSummary = new TextView(this);
        weatherSummary.setText(R.string.weather_needs_city);
        weatherSummary.setTextColor(Color.WHITE);
        weatherSummary.setTextSize(40);
        weatherSummary.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        weatherSummary.setGravity(Gravity.END);
        currentWeatherRow.addView(weatherSummary, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        weatherDetails = new TextView(this);
        weatherDetails.setText(R.string.weather_tap_to_configure);
        weatherDetails.setTextColor(0x8AFFFFFF);
        weatherDetails.setTextSize(16);
        weatherDetails.setGravity(Gravity.END);
        LinearLayout.LayoutParams detailParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        detailParams.topMargin = dp(8);
        weatherPanel.addView(weatherDetails, detailParams);
        weatherPanel.setOnClickListener(view -> openMirrorSettings());

        View topDivider = new View(this);
        topDivider.setBackgroundColor(0x55FFFFFF);
        LinearLayout.LayoutParams topDividerParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(1));
        topDividerParams.topMargin = dp(42);
        content.addView(topDivider, topDividerParams);

        LinearLayout iconRail = new LinearLayout(this);
        iconRail.setOrientation(LinearLayout.HORIZONTAL);
        iconRail.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        iconRail.setPadding(0, dp(14), 0, dp(14));
        content.addView(iconRail, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(92)));

        ImageButton spotifyLogo = makeIconButton(
                R.drawable.spotify_logo,
                R.string.spotify_toggle_description);
        iconRail.addView(spotifyLogo, iconRailButtonParams());

        ImageButton photoBooth = makeIconButton(
                R.drawable.photobooth_logo,
                R.string.open_photo_booth);
        photoBooth.setOnClickListener(view ->
                startActivity(new Intent(this, PhotoBoothActivity.class)));
        iconRail.addView(photoBooth, iconRailButtonParams());

        ImageButton notes = makeIconButton(
                R.drawable.notes_logo,
                R.string.create_post_it);
        notes.setOnClickListener(view -> openNoteEditor(null));
        iconRail.addView(notes, iconRailButtonParams());

        spotifyPanel = new LinearLayout(this);
        spotifyPanel.setOrientation(LinearLayout.VERTICAL);
        spotifyPanel.setPadding(0, dp(18), 0, dp(30));
        content.addView(spotifyPanel, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView spotifyLabel = new TextView(this);
        spotifyLabel.setText(R.string.spotify);
        spotifyLabel.setTextColor(0xAFFFFFFF);
        spotifyLabel.setTextSize(15);
        spotifyLabel.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        spotifyLabel.setLetterSpacing(0.12f);
        spotifyPanel.addView(spotifyLabel);

        spotifyBody = new LinearLayout(this);
        spotifyBody.setOrientation(LinearLayout.VERTICAL);
        spotifyPanel.addView(spotifyBody, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        spotifyLogo.setOnClickListener(view -> toggleSpotifyPanel());

        spotifyTitle = new TextView(this);
        spotifyTitle.setText(R.string.spotify_setup_title);
        spotifyTitle.setTextColor(Color.WHITE);
        spotifyTitle.setTextSize(34);
        spotifyTitle.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        spotifyTitle.setSingleLine(true);
        spotifyTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams spotifyTitleParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        spotifyTitleParams.topMargin = dp(14);
        spotifyBody.addView(spotifyTitle, spotifyTitleParams);

        spotifyArtist = new TextView(this);
        spotifyArtist.setText(R.string.spotify_setup_hint);
        spotifyArtist.setTextColor(0x8AFFFFFF);
        spotifyArtist.setTextSize(20);
        spotifyArtist.setSingleLine(true);
        spotifyArtist.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams spotifyArtistParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        spotifyArtistParams.topMargin = dp(6);
        spotifyBody.addView(spotifyArtist, spotifyArtistParams);

        LinearLayout mediaButtons = new LinearLayout(this);
        mediaButtons.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams mediaButtonsParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(64));
        mediaButtonsParams.topMargin = dp(18);
        spotifyBody.addView(mediaButtons, mediaButtonsParams);

        spotifyPrevious = makeMediaButton(R.string.spotify_previous);
        spotifyPlayPause = makeMediaButton(R.string.spotify_play);
        spotifyNext = makeMediaButton(R.string.spotify_next);
        mediaButtons.addView(spotifyPrevious, weightedButtonParams());
        mediaButtons.addView(spotifyPlayPause, weightedButtonParams());
        mediaButtons.addView(spotifyNext, weightedButtonParams());

        spotifyPrevious.setOnClickListener(view -> spotifyMediaController.previous());
        spotifyPlayPause.setOnClickListener(view -> spotifyMediaController.togglePlayback());
        spotifyNext.setOnClickListener(view -> spotifyMediaController.next());

        LinearLayout volumeRow = new LinearLayout(this);
        volumeRow.setOrientation(LinearLayout.HORIZONTAL);
        volumeRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams volumeRowParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(64));
        volumeRowParams.topMargin = dp(12);
        spotifyBody.addView(volumeRow, volumeRowParams);

        TextView volumeLabel = new TextView(this);
        volumeLabel.setText(R.string.spotify_volume);
        volumeLabel.setTextColor(0xD9FFFFFF);
        volumeLabel.setTextSize(18);
        volumeRow.addView(volumeLabel, new LinearLayout.LayoutParams(
                dp(110), LinearLayout.LayoutParams.WRAP_CONTENT));

        spotifyVolume = new SeekBar(this);
        spotifyVolume.setMax(audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC));
        spotifyVolume.setProgress(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC));
        spotifyVolume.setContentDescription(getString(R.string.spotify_volume));
        spotifyVolume.setProgressTintList(ColorStateList.valueOf(Color.WHITE));
        spotifyVolume.setThumbTintList(ColorStateList.valueOf(Color.WHITE));
        spotifyVolume.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, progress, 0);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                // Nothing to prepare.
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                syncSpotifyVolume();
            }
        });
        volumeRow.addView(spotifyVolume, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.MATCH_PARENT, 1f));
        spotifyTitle.setOnClickListener(view -> openSpotifyOrSetup());
        spotifyArtist.setOnClickListener(view -> openSpotifyOrSetup());
        spotifyPanel.setVisibility(View.GONE);

        noteLayer = new FrameLayout(this);
        noteLayer.setClipChildren(false);
        root.addView(noteLayer, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

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

    private Button makeMediaButton(int textResource) {
        Button button = new Button(this);
        button.setText(textResource);
        button.setTextColor(Color.WHITE);
        button.setTextSize(20);
        button.setMinHeight(dp(56));
        button.setBackground(mirrorButtonBackground());
        return button;
    }

    private ImageButton makeIconButton(int drawableResource, int descriptionResource) {
        ImageButton button = new ImageButton(this);
        button.setImageResource(drawableResource);
        button.setContentDescription(getString(descriptionResource));
        button.setBackgroundColor(Color.TRANSPARENT);
        button.setColorFilter(Color.WHITE);
        button.setPadding(dp(10), dp(10), dp(10), dp(10));
        button.setScaleType(android.widget.ImageView.ScaleType.CENTER_INSIDE);
        return button;
    }

    private LinearLayout.LayoutParams iconRailButtonParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(64), dp(64));
        params.rightMargin = dp(18);
        return params;
    }

    private void toggleSpotifyPanel() {
        spotifyPanel.animate().cancel();
        if (spotifyPanel.getVisibility() != View.VISIBLE) {
            spotifyPanel.setAlpha(0f);
            spotifyPanel.setTranslationY(-dp(12));
            spotifyPanel.setVisibility(View.VISIBLE);
            spotifyPanel.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(240L)
                    .start();
            return;
        }

        spotifyPanel.animate()
                .alpha(0f)
                .translationY(-dp(12))
                .setDuration(180L)
                .withEndAction(() -> {
                    spotifyPanel.setVisibility(View.GONE);
                    spotifyPanel.setAlpha(1f);
                    spotifyPanel.setTranslationY(0f);
                })
                .start();
    }

    private void renderPostIts() {
        if (noteLayer == null) {
            return;
        }
        noteLayer.removeAllViews();
        int baseSize = dp(260);
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int screenHeight = getResources().getDisplayMetrics().heightPixels;
        for (NoteRepository.Note note : NoteRepository.load(this)) {
            int size = Math.round(baseSize * note.scale);
            int availableX = Math.max(1, screenWidth - size);
            int availableY = Math.max(1, screenHeight - size);
            PostItView postIt = new PostItView(this, note, baseSize, new PostItView.Listener() {
                @Override
                public void onTransformed(NoteRepository.Note movedNote, float x, float y) {
                    NoteRepository.transform(
                            MainActivity.this,
                            movedNote.id,
                            x,
                            y,
                            movedNote.scale,
                            movedNote.rotation);
                }

                @Override
                public void onOpen(NoteRepository.Note openedNote) {
                    openNoteEditor(openedNote.id);
                }
            });
            noteLayer.addView(postIt, new FrameLayout.LayoutParams(size, size));
            postIt.setX(note.x * availableX);
            postIt.setY(note.y * availableY);
            postIt.setRotation(note.rotation);
        }
    }

    private void openNoteEditor(String noteId) {
        Intent editor = new Intent(this, NoteEditorActivity.class);
        if (noteId != null) {
            editor.putExtra(NoteEditorActivity.EXTRA_NOTE_ID, noteId);
        }
        startActivity(editor);
    }

    private GradientDrawable mirrorButtonBackground() {
        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.TRANSPARENT);
        background.setStroke(dp(1), 0x66FFFFFF);
        background.setCornerRadius(dp(2));
        return background;
    }

    private LinearLayout.LayoutParams weightedButtonParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(64), 1f);
        params.setMargins(dp(4), 0, dp(4), 0);
        return params;
    }

    private void renderSpotifyState(SpotifyMediaController.State state) {
        if (!state.accessGranted) {
            spotifyTitle.setText(R.string.spotify_setup_title);
            spotifyArtist.setText(R.string.spotify_setup_hint);
            setSpotifyButtonsEnabled(false);
            spotifyPlayPause.setText(R.string.spotify_play);
            return;
        }
        if (!state.sessionActive) {
            spotifyTitle.setText(R.string.spotify_nothing_playing);
            spotifyArtist.setText(R.string.spotify_start_hint);
            setSpotifyButtonsEnabled(false);
            spotifyPlayPause.setText(R.string.spotify_play);
            return;
        }

        spotifyTitle.setText(state.title.isEmpty()
                ? getString(R.string.spotify_unknown_track) : state.title);
        spotifyArtist.setText(state.artist.isEmpty()
                ? getString(R.string.spotify_unknown_artist) : state.artist);
        setSpotifyButtonsEnabled(true);
        spotifyPlayPause.setText(state.playing
                ? R.string.spotify_pause : R.string.spotify_play);
    }

    private void setSpotifyButtonsEnabled(boolean enabled) {
        spotifyPrevious.setEnabled(enabled);
        spotifyPlayPause.setEnabled(enabled);
        spotifyNext.setEnabled(enabled);
    }

    private void syncSpotifyVolume() {
        if (audioManager == null || spotifyVolume == null) {
            return;
        }
        spotifyVolume.setMax(audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC));
        spotifyVolume.setProgress(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC));
    }

    private void openSpotifyOrSetup() {
        if (spotifyMediaController == null
                || !spotifyMediaController.hasNotificationAccess()) {
            launchSettings(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS);
            return;
        }
        Intent spotify = getPackageManager().getLaunchIntentForPackage("com.spotify.music");
        if (spotify == null) {
            Toast.makeText(this, R.string.spotify_not_installed, Toast.LENGTH_LONG).show();
            return;
        }
        startActivity(spotify);
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
