# Architecture

The Python launcher owns computer-side setup. It discovers ADB, guides the user through TV settings, chooses an authorized device, installs the APK, launches its activity, and starts an `app_process` helper with shell UID 2000. The APK contains both the ordinary app and the helper's bytecode.

Before executing installed APK bytecode as shell, the launcher checks that its SHA-256 matches the APK supplied with the launcher. A package-name match alone is not sufficient. Android enforces the signing key when an existing app is updated.

The Android app owns the calibration UI and private preferences. Margins are stored per physical display ID, together with the dimensions at which they were saved. When the output mode changes, the profile scales the margins from those reference dimensions. The first run imports an existing compositor rectangle when it can read one.

The public name is Android TV Overscan. The Android application ID remains `com.skipstery.projectorcalibrator` from version 0.1.0 so signed upgrades preserve saved profiles. The build script also retains the original signing-key path for compatible updates.

The helper owns privileged display access. It obtains the physical display token through Android 14's `DisplayControl` and applies a `SurfaceControl.Transaction.setDisplayProjection` transaction. The source is the full logical display. The destination is the physical display minus the four margins. It does not change resolution, density, system settings, or the HDMI signal mode.

Android 14 declares `ACCESS_SURFACE_FLINGER` as a signature permission, and the shell package holds it. An ordinary app cannot acquire it with `pm grant`. See the AOSP [permission declaration](https://android.googlesource.com/platform/frameworks/base/+/android-14.0.0_r1/core/res/AndroidManifest.xml) and [Shell manifest](https://android.googlesource.com/platform/frameworks/base/+/android-14.0.0_r1/packages/Shell/AndroidManifest.xml).

## Binder connection

The app exposes a bootstrap provider protected by `INTERACT_ACROSS_USERS_FULL`. The provider additionally checks that the caller is UID 2000. The helper obtains this provider through `ActivityManager.getContentProviderExternal`, sends its Binder, and receives the app UID and saved profile.

The app uses that Binder for status and margin changes. The helper accepts only the installed app's UID, enforces an interface descriptor, validates all dimensions and margins, and clears Binder calling identity before invoking the compositor. It accepts no arbitrary shell commands and opens no TCP listener.

An external-provider reference keeps the app process available while the helper runs. A Binder token lets Android release that reference if the helper dies. The helper links to the app's lifetime Binder and exits if the app dies. Stopping or replacing the app therefore also stops its old helper.

See Android 14's [SurfaceControl implementation](https://android.googlesource.com/platform/frameworks/base/+/android-14.0.0_r1/core/java/android/view/SurfaceControl.java) and [DisplayControl implementation](https://android.googlesource.com/platform/frameworks/base/+/android-14.0.0_r1/services/core/java/com/android/server/display/DisplayControl.java).

## Lifetime and recovery

The helper checks the physical display mode and logical display state every two seconds. It reapplies the rectangle after a change and retries failed display updates. It reloads a display's stored profile when its ID or dimensions change, avoiding cumulative rounding during repeated mode switches. Opening the UI reapplies the current profile as well.

This controller is a transient ADB shell process, not a boot service. A full reboot stops it. The launcher must run again to restore the saved profile. Preferences survive the reboot. Normal app updates preserve preferences when the signing key matches.

Margins must leave at least half of each display dimension visible. Both the app model and the privileged Binder endpoint enforce the bound. Reset restores the full picture, and Show original temporarily previews it without overwriting the saved margins. Done reapplies and saves the calibrated margins, closes the activity, and returns to the TV home screen.
