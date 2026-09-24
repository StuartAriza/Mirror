package com.stuart.smartmirror;

import android.content.ComponentName;
import android.content.Context;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.provider.Settings;

import java.util.List;
import java.util.Locale;

final class SpotifyMediaController {
    interface Listener {
        void onSpotifyState(State state);
    }

    static final class State {
        final boolean accessGranted;
        final boolean sessionActive;
        final boolean playing;
        final String title;
        final String artist;

        State(boolean accessGranted, boolean sessionActive, boolean playing,
                String title, String artist) {
            this.accessGranted = accessGranted;
            this.sessionActive = sessionActive;
            this.playing = playing;
            this.title = title;
            this.artist = artist;
        }
    }

    private final Context context;
    private final Handler handler;
    private final Listener listener;
    private final MediaSessionManager sessionManager;
    private final ComponentName listenerComponent;
    private MediaController controller;
    private boolean listening;

    private final MediaController.Callback controllerCallback =
            new MediaController.Callback() {
                @Override
                public void onMetadataChanged(MediaMetadata metadata) {
                    publishState();
                }

                @Override
                public void onPlaybackStateChanged(PlaybackState state) {
                    publishState();
                }

                @Override
                public void onSessionDestroyed() {
                    refreshSessions();
                }
            };

    private final MediaSessionManager.OnActiveSessionsChangedListener sessionsListener =
            this::selectSpotifySession;

    SpotifyMediaController(Context context, Handler handler, Listener listener) {
        this.context = context.getApplicationContext();
        this.handler = handler;
        this.listener = listener;
        this.sessionManager = (MediaSessionManager) context.getSystemService(
                Context.MEDIA_SESSION_SERVICE);
        this.listenerComponent = new ComponentName(
                context, MirrorNotificationListenerService.class);
    }

    void start() {
        stop();
        if (!hasNotificationAccess()) {
            listener.onSpotifyState(new State(false, false, false, "", ""));
            return;
        }

        try {
            sessionManager.addOnActiveSessionsChangedListener(
                    sessionsListener, listenerComponent, handler);
            listening = true;
            refreshSessions();
        } catch (SecurityException error) {
            listener.onSpotifyState(new State(false, false, false, "", ""));
        }
    }

    void stop() {
        if (listening) {
            sessionManager.removeOnActiveSessionsChangedListener(sessionsListener);
            listening = false;
        }
        setController(null);
    }

    boolean hasNotificationAccess() {
        String enabled = Settings.Secure.getString(
                context.getContentResolver(), "enabled_notification_listeners");
        return enabled != null && enabled.contains(listenerComponent.flattenToString());
    }

    void previous() {
        if (controller != null) {
            controller.getTransportControls().skipToPrevious();
        }
    }

    void togglePlayback() {
        if (controller == null) {
            return;
        }
        PlaybackState state = controller.getPlaybackState();
        if (state != null && state.getState() == PlaybackState.STATE_PLAYING) {
            controller.getTransportControls().pause();
        } else {
            controller.getTransportControls().play();
        }
    }

    void next() {
        if (controller != null) {
            controller.getTransportControls().skipToNext();
        }
    }

    private void refreshSessions() {
        try {
            selectSpotifySession(sessionManager.getActiveSessions(listenerComponent));
        } catch (SecurityException error) {
            listener.onSpotifyState(new State(false, false, false, "", ""));
        }
    }

    private void selectSpotifySession(List<MediaController> controllers) {
        MediaController spotify = null;
        if (controllers != null) {
            for (MediaController candidate : controllers) {
                String packageName = candidate.getPackageName();
                if (packageName != null
                        && packageName.toLowerCase(Locale.US).contains("spotify")) {
                    spotify = candidate;
                    break;
                }
            }
        }
        setController(spotify);
        publishState();
    }

    private void setController(MediaController nextController) {
        if (controller != null) {
            controller.unregisterCallback(controllerCallback);
        }
        controller = nextController;
        if (controller != null) {
            controller.registerCallback(controllerCallback, handler);
        }
    }

    private void publishState() {
        if (controller == null) {
            listener.onSpotifyState(new State(true, false, false, "", ""));
            return;
        }

        MediaMetadata metadata = controller.getMetadata();
        String title = metadataText(metadata,
                MediaMetadata.METADATA_KEY_DISPLAY_TITLE,
                MediaMetadata.METADATA_KEY_TITLE);
        String artist = metadataText(metadata,
                MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE,
                MediaMetadata.METADATA_KEY_ARTIST);
        PlaybackState playbackState = controller.getPlaybackState();
        boolean playing = playbackState != null
                && playbackState.getState() == PlaybackState.STATE_PLAYING;
        listener.onSpotifyState(new State(true, true, playing, title, artist));
    }

    private String metadataText(MediaMetadata metadata, String preferred, String fallback) {
        if (metadata == null) {
            return "";
        }
        String value = metadata.getString(preferred);
        if (value == null || value.trim().isEmpty()) {
            value = metadata.getString(fallback);
        }
        return value == null ? "" : value;
    }
}
