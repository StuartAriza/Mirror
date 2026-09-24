# Mirror

A deliberately small Android smart-mirror shell for the Echelon Reflect 50
(Rockchip RK3288, Android 7.1.2 / API 25, 1080×1920 portrait).

## Version 0.3.2

- True-black, portrait, touch-friendly screen
- MagicMirror-inspired edge-aligned layout with thin dividers and open negative space
- Large system-format-aware clock and date
- Live current weather plus today's high and low from Open-Meteo
- User-selected weather artwork automatically switches for conditions and day/night
- Touch-friendly Mirror settings screen for city and Celsius/Fahrenheit
- Cached weather remains visible if the network is temporarily unavailable
- Bundled modern TLS for the Echelon firmware's outdated HTTPS stack
- IPv4-only weather networking to avoid broken IPv6 routes on some networks
- Programmatic screens that avoid the firmware's broken binary-XML parser
- Local Spotify now-playing display with Previous, Play/Pause, Next, and volume
- Horizontal icon rail keeps Spotify and Photobooth compact
- Uses the owner-selected Spotify logo artwork
- Spotify controls animate open beneath the icon rail
- Built-in photobooth with live preview, three-second countdown, retake, and save
- Minimal camera icon opens the photobooth without occupying mirror space
- Includes the selected pencil artwork for the future notes/drawing module
- Handwritten Post-its with paper colors, pen sizes, undo, clear, save, and delete
- Saved Post-its appear on the main mirror and can be dragged to new positions
- Every Post-it has a dimensional red pin; drag it to rotate the paper
- Drag the folded lower-right corner to resize a Post-it
- Prefers the front camera and supports switching cameras when more than one exists
- Saves accepted photos locally under `Pictures/MirrorPhotobooth`
- Long-press `•••` in the upper-right for Android Settings or Dashline Home
- Pressing Android Back opens the same recovery controls
- Optional system-wide Back / Home / Recents strip using an Accessibility Service
- No AndroidX, Play Services, API key, GPS permission, analytics, root, or boot receiver
- Does not register as Home and does not touch ReflectTouch

## Configure weather

1. Open **Mirror**.
2. Tap the weather panel, or long-press `•••` and choose **Mirror settings**.
3. Enter a city and region, choose Celsius or Fahrenheit, and tap **Save**.

Weather refreshes every 30 minutes while Mirror is open. The city name is sent
to the Open-Meteo geocoding service; the app then requests the forecast for the
returned coordinates. Connections remain HTTPS-secured using bundled Conscrypt
and an Android-compatible OkHttp client. No location permission, API key, or
Google service is used.

## Configure Spotify controls

1. Install and sign in to Spotify on the Echelon.
2. Open **Mirror** and tap the Spotify panel.
3. In Notification Access, enable **Mirror Spotify controls**.
4. Return to Mirror and start music in Spotify.

Mirror reads Spotify's active Android media session and sends standard media
commands. It does not request Spotify credentials, contact the Spotify Web API,
or store notification contents. The volume slider controls Android's music
stream, which is the stream Spotify uses on this device.

## Use the photobooth

1. Tap **Open photobooth** on the Mirror screen.
2. Allow camera and storage access the first time.
3. Frame the picture and tap **Take photo** for a three-second countdown.
4. Choose **Retake** or **Save photo**.

Saved JPEG files appear in `Pictures/MirrorPhotobooth`. The photobooth always
shows a **Mirror** exit button, and camera or permission failures leave a safe
route back rather than trapping the user. It uses Android's legacy Camera API
for compatibility with the RK3288 vendor camera driver.

## Open and build

Use a current Android Studio installation with Android SDK Platform 34 and JDK 17 or 21.
Open this directory, let Gradle sync, then choose **Build > Build APK(s)**.

The debug APK will be written to:

`app/build/outputs/apk/debug/app-debug.apk`

The Gradle 8.7 wrapper is included, so the project can also be built from a
terminal with `gradlew.bat assembleDebug` after Android Studio has installed JDK
17, SDK Platform 34, and the matching SDK build tools.

Copy the debug APK to the USB flash drive and install it with Material Files. For
an update, install the newly built APK over the existing app; keep the same
application ID and signing key.

## Safe navigation setup

1. Launch **Mirror** from Dashline.
2. Long-press the faint `•••` at the upper-right.
3. Confirm **Exit to Home (Dashline)** works.
4. Open the controls again and select **Set up Back / Home / Recents strip**.
5. In Accessibility Settings, enable **Mirror navigation strip**.

The strip is opt-in and can be disabled from Accessibility Settings. It uses
Android's accessibility global actions and does not retrieve window content.

### Immediate escape options before the APK is installed

If ADB is connected and authorized:

```text
adb shell input keyevent KEYCODE_BACK
adb shell input keyevent KEYCODE_HOME
adb shell input keyevent KEYCODE_APP_SWITCH
adb shell am start -a android.settings.SETTINGS
```

A USB keyboard is another safe fallback: **Esc** normally maps to Back, and a
keyboard with a Home key may return to the launcher. These methods do not alter
firmware or uninstall anything.

## Next candidates

Automatic start, replacement-Home behavior, calendar access, drawing notes,
photo-strip layouts, and ESP32 communication remain
deliberately deferred until each feature can be tested safely on the real device.
