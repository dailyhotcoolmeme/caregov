# Caregov

Hospital accompaniment app with fictional patient, guardian, manager and
operator identities. All accounts use the requested local password `260401`.
The current app is Expo/React Native in `mobile/`, with real bundle OTA on R2.

Local workflows include self/proxy booking, operator assignment and schedule
management, manager acceptance and ordered visit progress, result reports,
sharing controls, reviews, a notification inbox, and settlement confirmation.
No production backend, push delivery, real charge, or bank transfer is connected.

## Bundle Updates

```sh
node --test mobile/tests/*.test.mjs scripts/ota-worker.test.mjs
node scripts/publish-bundle.mjs
```

Compatible screen/workflow updates do not rebuild or reinstall the APK.
Read [Android Updates](docs/android-updates.md) before publishing.

## Transition / Native Build

Requires JDK 17 and an Android SDK with platform 36. Set `ANDROID_HOME` to your
SDK directory, or configure `sdk.dir` in an untracked `local.properties` file.

```sh
bash scripts/build-mobile.sh
```

APK: `output/apk/caregov.apk`

The commands below are for local emulator verification, not routine phone
delivery. One same-signed APK replaces the previous Compose runtime; after that,
deliver completed compatible batches with `node scripts/publish-bundle.mjs`.
Use `node scripts/publish-r2.mjs` only for transition/native changes.

```sh
adb install -r output/apk/caregov.apk
adb shell am start -n com.ourmine.caregov.demo/.MainActivity
```

Existing SharedPreferences records and account migrate once to AsyncStorage;
original preferences remain intact. The original `app/` project is retained for
legacy verification, not current UI development.

## Legacy Verification

With an emulator or Android device connected:

```sh
./gradlew :app:lintDebug :app:connectedDebugAndroidTest
```

## Work Plan

See [docs/work-plan.md](docs/work-plan.md) for implementation stages and verified status.

## Android Updates

See [docs/android-updates.md](docs/android-updates.md) for the bundle update channel,
integrity checks, installation confirmation, and signing requirements.
Do not build or publish separately for each workflow stage. Complete a batch,
verify it, then publish one version with the existing signing key.
