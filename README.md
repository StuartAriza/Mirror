# Mirror

A deliberately small Android smart-mirror shell for the Echelon Reflect 50
(Rockchip RK3288, Android 7.1.2 / API 25, 1080×1920 portrait).

## Version 0.1

- True-black, portrait, touch-friendly screen
- Large system-format-aware clock and date
- Weather placeholder ready for the next iteration
- Long-press `•••` in the upper-right for Android Settings or Dashline Home
- Pressing Android Back opens the same recovery controls
- Optional system-wide Back / Home / Recents strip using an Accessibility Service
- No AndroidX, Play Services, network permissions, analytics, root, or boot receiver
- Does not register as Home and does not touch ReflectTouch

## Open and build

Use a current Android Studio installation with Android SDK Platform 35 and JDK 17.
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

## Deliberate non-features

Automatic start, replacement-Home behavior, live weather, calendar access, and
ESP32 communication are deferred until this recovery path is tested on the real
device.
