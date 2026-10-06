# Caregov Android Application

## Goal

Build a complete hospital accompaniment Android application, not a demonstration interface.
Support both patient self-booking and guardian booking on behalf of a patient.
Use fictional user records for patients, guardians, managers, and operators.
User-facing copy and navigation must follow normal service workflows, without
demonstration labels, scenario controls, or arbitrary role switching.

## Delivery Steps

1. DONE: Android project, reproducible APK build, app launch.
2. Shared visual design, navigation, accessible phone layouts.
3. Patient and guardian booking, manager details, progress, reports, history.
4. Manager schedule, acceptance, progress updates, report entry, settlement.
5. Operator assignment, schedule changes, operations overview, settlement.
6. Shared booking state, restart persistence, account access boundaries.
7. End-to-end Android verification, final APK, screenshots, OTA update verification.

## Implementation Requirements

- User records are fictional. Application actions must persist and affect related workflows.
- Do not substitute scenario buttons or fixed success screens for working features.
- Payments, notifications, authentication, and server integration are not implemented yet;
  do not present those integrations as complete or initiate real charges without authorization.
- APK generation and direct installation do not authorize store distribution.
- Do not use Play testing tracks, TestFlight, or other test distribution services.
- Store review or production release requires the owner's exact authorization.

## Stage 1

Implementation: Kotlin, Jetpack Compose, Android Gradle Plugin 8.13.2,
Gradle 8.14.3, JDK 17, minimum Android API 26, target API 36.

The current bootstrap screen has a fictional patient and an appointment.
The initial role-selection menu was removed following the owner's clarification.
Booking and operational workflows are still pending; the current APK is not a complete app.

### Verification (2026-10-06)

- APK build: passed (`:app:assembleDebug`).
- Static checks: passed (`:app:lintDebug`), zero errors and three advisory warnings.
- Signing: Android APK v2 signature verified with `apksigner`.
- Emulator: Android 16, API 36.1, ARM64, read-only AVD session.
- Installation and cold start: passed.
- Instrumentation: `SessionSmokeTest.accountSwitchSurvivesActivityRecreation`
  passed (one test). Covers patient, guardian, manager, operator, and persisted
  selection across activity recreation.
- Display: reviewed at 1080x2400 and at 720x1280 with 130% font scaling.
- A physical Android phone was not connected; physical-device checks are pending.
- Local artifact: `output/apk/caregov-demo.apk` (approximately 9.5 MiB).
- Captures: `output/screenshots/step01-patient.png` and
  `output/screenshots/step01-small-large-text.png`.

The test dependencies explicitly use Espresso 3.7.0 for Android 16 compatibility.

Build references:

- https://developer.android.com/build/releases/agp-8-13-0-release-notes
- https://developer.android.com/develop/ui/compose/setup-compose-dependencies-and-compiler
- https://developer.android.com/jetpack/androidx/releases/test
