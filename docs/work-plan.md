# Caregov Android Application

## Goal

Build a complete hospital accompaniment Android application, not a demonstration interface.
Support both patient self-booking and guardian booking on behalf of a patient.
Use fictional user records for patients, guardians, managers, and operators.
User-facing copy and navigation must follow normal service workflows, without
demonstration labels or scenario controls. The owner's latest request authorizes
a user dropdown and password-confirmed identity switching from the account screen.

## Delivery Steps

1. Shared design, role-specific navigation and account structure. Frontend and
   local fictional identities implemented; production authentication pending.
2. Patient self-booking and guardian proxy-booking. Local request flow,
   contacts, relationships, service options, quote, review, modification,
   and cancellation implemented. Server integration remains pending.
3. Operator intake, assignment, rescheduling, and cancellation: local workflow implemented.
4. Manager acceptance, visit progress, and completion: local workflow implemented.
5. Patient/guardian progress visibility and sharing boundaries: local workflow implemented.
6. Reports, reviews, and service history: local workflow implemented.
7. Fees and manager settlement confirmation: local workflow implemented;
   real payment and transfer integration separate.
8. End-to-end verification, phone installation, and OTA delivery.

## Implementation Requirements

- User records are fictional. Application actions must persist and affect related workflows.
- Do not substitute scenario buttons or fixed success screens for working features.
- Real payments, push/SMS notifications, production authentication, and server integration are not implemented yet;
  do not present those integrations as complete or initiate real charges without authorization.
- APK generation and direct installation do not authorize store distribution.
- Do not use Play testing tracks, TestFlight, or other test distribution services.
- Store review or production release requires the owner's exact authorization.

## Stage 1

Implementation: Kotlin, Jetpack Compose, Android Gradle Plugin 8.13.2,
Gradle 8.14.3, JDK 17, minimum Android API 26, target API 36.

The original bootstrap screen had a fictional patient and an appointment.
The initial role-selection menu was removed following the owner's clarification.
Booking and operational workflows were still pending in that initial APK.

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

## Version 0.3.0 Frontend And Booking Progress (2026-10-06)

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
  01000000003 (manager), 01000000004 (operator); local fixture password 260401.
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

## Stage 2 Request Workflow (2026-10-06)

- Version 0.4.0 / code 7. Three steps: user/service, hospital visit, final review.
- Both self and proxy requests include patient contacts; proxy requests require
  guardian contact and relationship. Only explicitly linked fixture patients
  receive proxy records in their own account, not patients with matching names.
- Sharing selection persists: none, progress only, or results included. Guardian
  report access requires results sharing; this is a local fixture boundary only.
- Fictional quote requested by the owner: KRW 20,000 per hour, 1-8 hours.
  Transport and medical charges excluded. No approved commercial tariff or
  actual payment integration. Quote is recalculated at save time and persisted.
- Explicit consent required on each submission, including changes. This does
  not constitute production legal-consent or identity verification.
- Requesters may edit or cancel only their own future pending requests.
  Confirmed, cancelled, and other people's bookings cannot be changed.
  Revision checks reject stale writes; cancellation requires a reason.
- Existing records without new fields remain readable, without data resets.
- APK build and lint passed. Default emulator: 17 tests passed. Small display
  (720x1280, font scale 1.3): 16 tests passed, including creation, recreation,
  modification, cancellation, account menus, storage, permissions, and manifest.
  Three OTA Worker tests passed. Review screenshot inspected.
- Physical installation of this version is not verified. Delivery is via R2,
  without a routine USB connection or any store-console distribution.
- This stage was followed by the batched operational workflows below.

## Batched Operations, Progress And Results (2026-10-06)

- Version 0.5.0 / code 8 groups remaining local workflows in one OTA upload.
  Build after completing the batch, not once per implementation stage.
- Operator reservation filters, assignment/reassignment, future rescheduling,
  cancellation before visits start, and manager schedule overlap rejection.
- Managers accept or decline with a reason. Rescheduling requires another
  acceptance. Only the assigned manager can advance the ordered visit steps.
  Examination uses an examination-completed step; return-home service skips
  hospital and consultation steps. Started visits cannot be reassigned/cancelled.
- Completion requires arrival home and a submitted report. Reports include
  accompaniment details, hospital guidance, medication handover, next visit,
  and entered actual minutes. The app does not generate medical advice.
- Patient/guardian result access, persisted reviews, and post-visit sharing changes.
  Progress-only guardians do not see report contents or private request notes.
  No-sharing hides patient-owned records from the linked guardian.
- Local processing timeline and notification inbox; not push or SMS delivery.
- Fictional fee rule: minimum one hour, then 30-minute increments at KRW
  10,000 per increment. Manager share 80%, operation fee 20%. Amounts are
  recorded when the report is submitted, not charged to a payment instrument.
  Operator confirmation changes settlement records only, never sends money.
- Additional fictional manager: 01000000005 / same fixture password 260401.
- Existing records migrate lazily with empty workflow history, no data reset.
- This is a functional local app using fictional accounts, not a production
  backend. Multi-device synchronization, server authorization, real payments,
  notifications, and patient identity verification remain separate work.
- Delivery status and verification results are recorded in `android-updates.md`.
- Final APK build and lint passed (zero lint errors). Android 16 emulator:
  27 tests passed at 1080x2400; 26 passed at 720x1280 with 130% font scale.
  Three OTA Worker tests passed. The cross-account UI test covers assignment,
  acceptance, all visit steps, report persistence, guardian results, review,
  operator settlement, and revocation of result sharing. Screenshots inspected.

## User Selection (2026-10-06)

- Version 0.5.1 / code 9 replaces manual phone entry with a user dropdown.
- Account screen includes a password-confirmed user change dialog, without
  logging out first. Cancelling or a wrong password leaves the current user intact.
- All five fictional identities use password 260401; the old password is rejected.
- Switching does not reset booking data. Existing logged-in sessions remain valid.
- APK build and lint passed. Fifteen focused tests passed at 720x1280 with
  130% font scale, including all five dropdown choices, password rejection,
  identity persistence, cancellation, booking preservation, and full visit flow.
- Login also accepts the password keyboard's Done action. Login screenshot reviewed.
