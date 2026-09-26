# StatusBar Hider

A minimal Android app that hides the status bar system-wide (including the launcher) using [Shizuku](https://shizuku.rikka.app/). No system apps are uninstalled. Swipe-down to expand notifications is preserved.

The app reads OEM-specific commands from JSON files in `app/src/main/assets/oem/`. Currently supported: **HyperOS** (Xiaomi/Redmi). See [CONTRIBUTING.md](CONTRIBUTING.md) to add more.

## Prerequisites

- Android Studio with JDK 17+
- A device running a supported OEM skin
- [Shizuku](https://shizuku.rikka.app/) installed and running on the device
  - Start via `adb shell sh /storage/emulated/0/Android/data/moe.shizuku.privileged.api/start.sh`
  - Or pair wirelessly; enable auto-start on boot if needed

## Build

### With [mise](https://mise.jdx.dev) (recommended)

```bash
mise install        # installs Java 21, Gradle 8.8, Android SDK
mise run build      # assembleRelease (signed if keystore.properties exists)
```

### With Gradle directly

```bash
gradle assembleRelease
```

Requires `ANDROID_HOME` to be set and the following SDK components installed:

- `platforms;android-35`
- `build-tools;34.0.0` (or higher)

## Signing

Release builds are signed with a keystore generated during setup.
The keystore and its password live in files that are **gitignored**:

```
keystore/statusbarhider.keystore   # PKCS12, RSA 2048, 10 000 days
keystore.properties                 # storeFile, storePassword, keyAlias, keyPassword
```

To generate a new keystore:

```bash
keytool -genkeypair -v \
  -keystore keystore/statusbarhider.keystore \
  -alias statusbarhider -keyalg RSA -keysize 2048 \
  -validity 10000 -storetype PKCS12 \
  -storepass <password> -keypass <password> \
  -dname "CN=StatusBar Hider, OU=Dev, O=Local, L=Local, ST=Local, C=FR"
```

Then create `keystore.properties` at the project root:

```properties
storeFile=keystore/statusbarhider.keystore
storePassword=<password>
keyAlias=statusbarhider
keyPassword=<password>
```

## How it works

1. **Authorize Shizuku** -- grants the app shell-level permissions via the Shizuku binder.
2. **Hide status bar** -- runs three shell commands (defined in the OEM JSON):
   - `settings put global policy_control 'immersive.status=*'` -- immersive mode globally (persistent)
   - `settings put secure icon_blacklist <74 slots>` -- hides system icon slots (persistent)
   - `cmd statusbar send-disable-flag system-icons clock notification-icons` -- suppresses icon groups (volatile, lost on reboot)
3. **Check state** -- reads back the current values of those settings.
4. **Restore** -- deletes the settings keys and clears the disable flags.

All commands are OEM-specific. The JSON files in `app/src/main/assets/oem/` define which commands to run. See [CONTRIBUTING.md](CONTRIBUTING.md) for how to discover commands on a new device.

## App features

- **Hide from launcher** -- disables the launcher activity so the app disappears from the app drawer. Re-access via Settings > Apps > StatusBar Hider > Open.
- **Export logs** -- copies the log output to the clipboard.
- **Log panel** -- shows the output of every command for debugging.

## Known limitations

- Disable flags (`cmd statusbar send-disable-flag`) are volatile and reset on reboot / SystemUI restart. The app includes a `BOOT_COMPLETED` receiver that automatically re-applies the hide commands on boot — but **Shizuku must be configured to start on boot** in its settings.
- `ShizukuCmd` calls `IShizukuService.newProcess()` directly via the `aidl` artifact instead of the deprecated `Shizuku.newProcess()` method (private since Shizuku 13.1.5). This reads stdout via `ParcelFileDescriptor.AutoCloseInputStream` with a timeout to avoid pipe deadlocks.

## Credits

Inspired by [Essentials](https://github.com/sameerasw/essentials) by Sameera Perera and [SystemUI Tuner](https://github.com/zacharee/Tweaker) by Zachary Wander.

## License

This project is provided as-is for educational purposes. Use at your own risk.
