# Caregov Android Application

## Goal

Build a complete hospital accompaniment Android application, not a demonstration interface.
Support both patient self-booking and guardian booking on behalf of a patient.
Use fictional user records for patients, guardians, managers, and operators.
User-facing copy and navigation must follow normal service workflows, without
demonstration labels, scenario controls, or arbitrary role switching.

## Delivery Steps

1. Shared design, role-specific navigation and account structure. Frontend and
   local fictional identities implemented; production authentication pending.
2. Patient self-booking and guardian proxy-booking. Basic local request flow
   implemented; contact, relationship, and service option extensions pending.
3. Operator intake, assignment, rescheduling, and cancellation.
4. Manager acceptance, visit progress, and completion.
5. Patient/guardian progress visibility and sharing boundaries.
6. Reports and service history.
7. Fees and manager settlement; real payment integration separate.
8. End-to-end verification, phone installation, and OTA delivery.

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

## Frontend And Booking Progress (2026-10-06)

- Added role-specific home, booking list, detail, account, and login screens.
- Update action lives in the account screen; no demonstration account controls.
- Patient self-request and guardian proxy-request forms save actual local records.
- Required fields and consent gate submission; past visit times are rejected.
- Saved records survive activity and application restart. Requests remain pending
  assignment; the app does not invent a confirmed manager or report.
- Patient/guardian access and assigned-manager visibility are filtered locally.
- Local fictional identity login only, not production server authentication.
  SMS verification, account provisioning, server-side authorization, multi-device
  synchronization, payment, assignment, progress, and report writing are pending.
- Fictional accounts: 01000000001 (patient), 01000000002 (guardian),
  01000000003 (manager), 01000000004 (operator); local fixture password 482619.
  These are public fixtures, not real credentials. Do not use for real patients.
- Version 0.3.0 / code 5: APK build and lint passed; signature verified.
- 720x1280 at 130% font scale: 10 instrumentation tests passed, including form
  submission, recreation, four account menu contexts, persistence, permissions,
  rejected past dates, and update manifest validation. Screenshot reviewed.
- Physical phone disconnected during this stage; 0.3.0 direct installation
  is not verified. OTA asset upload is independent of store distribution.

Build references:

- https://developer.android.com/build/releases/agp-8-13-0-release-notes
- https://developer.android.com/develop/ui/compose/setup-compose-dependencies-and-compiler
- https://developer.android.com/jetpack/androidx/releases/test
