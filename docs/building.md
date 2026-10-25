# Build & signing

## Mise tasks

| Task                      | Description                      |
|---------------------------|----------------------------------|
| `mise run build`          | Build signed release APK         |
| `mise run debug`          | Build debug APK                  |
| `mise run install`        | Build + install release APK      |
| `mise run install-debug`  | Build + install debug APK        |
| `mise run clean`          | Clean Gradle outputs             |
| `mise run devices`        | List connected ADB devices       |
| `mise run logs`           | Live log TUI (logstream)         |
| `mise run uninstall`      | Uninstall the app from device    |

Building needs an **Android SDK** (`ANDROID_HOME`) with the
`platforms;android-37.0` and `build-tools;37.0.0` packages - neither is
installed by mise.

The **JDK 21** is: `mise.toml` pins `java = "21"`, so every `mise run` starts
Gradle with an installed Temurin 21 on `JAVA_HOME`. The Gradle daemon is pinned
to 21 as well (`gradle/gradle-daemon-jvm.properties`, `toolchainVendor=eclipse`
= Temurin) because detekt 1.23.8's embedded compiler throws on newer JVMs. That
daemon JVM is chosen before any build script runs, so Gradle **cannot download
it**: a 21 has to be installed locally, which is exactly what the mise pin
guarantees.

## Build without mise

```bash
./gradlew assembleRelease   # APK
./gradlew testDebugUnitTest # unit tests (prefs, backup, control API)
```

Requires a **JDK 21** on `JAVA_HOME` (see the daemon pin above) and the
following SDK components installed:

- `platforms;android-37.0`
- `build-tools;37.0.0` (or higher)

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

## Debug vs release

Debug builds install alongside the release one (`applicationIdSuffix = ".debug"`)
and are told apart by a **"Corniche Debug"** label and a launcher icon
with an amber `D` badge - see `app/src/debug/res/`.
