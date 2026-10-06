# caregov

Hospital accompaniment service Android application with fictional user records.

## Build

Requires JDK 17 and an Android SDK with platform 36. Set `ANDROID_HOME` to your
SDK directory, or configure `sdk.dir` in an untracked `local.properties` file.

```sh
./scripts/build-apk.sh
```

APK: `output/apk/caregov.apk`

```sh
adb install -r output/apk/caregov.apk
adb shell am start -n com.ourmine.caregov.demo/.MainActivity
```

## Verification

With an emulator or Android device connected:

```sh
./gradlew :app:lintDebug :app:connectedDebugAndroidTest
```

## Work Plan

See [docs/work-plan.md](docs/work-plan.md) for implementation stages and verified status.

## Android Updates

See [docs/android-updates.md](docs/android-updates.md) for the APK update channel,
integrity checks, installation confirmation, and signing requirements.
