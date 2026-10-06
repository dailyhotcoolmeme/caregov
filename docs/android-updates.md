# Android Updates

From version 0.3.1, the app reads the no-cache manifest at
`https://caregov-ota.dailyhotcoolmeme.workers.dev/android.json` on launch and
when the user selects the update action. APKs and pointers live in the dedicated
R2 bucket `caregov-media-apac`. This channel is independent of store consoles.

The implementation follows mozzzi's R2 APK publishing pattern: version plus
content-hash filename, explicit APK download content type, public download
hash verification before pointer publication, and locally supplied credentials.
Unlike mozzzi's Expo app, Caregov is native Kotlin/Compose and uses APK updates,
not Expo bundle replacement. Android installation approval remains required.

Updates require a higher version code, the same application ID, compatible
Android requirements, and the same signing certificate as the installed app.
Downloads are checked against manifest size and SHA-256 before installation.
Android installation permission and the user's installation confirmation are
required; updates are not silent JavaScript updates.

## Publishing An Update

1. Increment the version code and version name in `app/build.gradle.kts`.
2. Run `./scripts/build-apk.sh :app:lintDebug` and verify the APK.
3. Run `node scripts/publish-r2.mjs`. It derives APK metadata and SHA-256,
   uploads an immutable hash-named APK, validates the public download, then
   publishes `ota/pointer/android.json` and verifies its public response.
4. Commit `updates/r2-android.json` and associated source changes after verification.
5. Verify public access and test downloading from an older installed version.
   Do not make USB connection a routine delivery requirement; return the R2 URL.

First-time infrastructure setup: `node scripts/setup-r2.mjs`. This creates the
dedicated bucket if absent and deploys only the Caregov read-only Worker.
Do not run this for ordinary app updates.

## Legacy Installation Bridge

Versions before 0.3.1 trust GitHub release URLs only. The old GitHub pointer
therefore points to the matching 0.3.1 APK on GitHub. Installing that update or
the direct R2 download migrates the app to the R2 manifest. No USB is needed.
Do not replace the legacy pointer with an R2 APK URL: old apps reject it.

## R2 Verification (2026-10-06)

### Version 0.5.1 / Code 9

- Status: uploaded to R2. User dropdown, account-switch dialog, and requested
  common fictional password 260401; existing booking records retained.
- APK: https://caregov-ota.dailyhotcoolmeme.workers.dev/apk/caregov-0.5.1-68d691c21a9f.apk
- Build and lint passed. Fifteen focused tests passed on the small display with
  130% font scaling; login screenshot reviewed. Tests cover all users, wrong/old
  password rejection, persisted identities, cancellation, and visit workflow.
- Public APK size and SHA-256 verified before updating the R2 pointer. An
  emulator with the actual 0.5.0 APK downloaded and verified 0.5.1, including
  signing continuity and wrong-hash rejection. Live current-version dialog
  passed after installing the published 0.5.1 APK on the emulator.
- Physical phone receipt and installer completion remain unverified. No store
  console upload, test track, review submission, or production release.

### Version 0.5.0 / Code 8

- Status: uploaded to R2, one OTA publication for the batched workflows.
- APK: https://caregov-ota.dailyhotcoolmeme.workers.dev/apk/caregov-0.5.0-abada9870cae.apk
- Public APK SHA-256 and exact size verified before publishing the no-cache
  pointer. Same existing debug signing certificate; package ID unchanged.
- Final APK build and lint passed. Android 16 emulator: 27 functional tests at
  1080x2400, 26 at 720x1280 / 130% font scale; three Worker tests passed.
  Reviewed assignment, report, and settlement screenshots. Compact navigation
  labels prevent wrapping at the enlarged font setting.
- Installed the actual published 0.4.0 APK on the emulator, without rebuilding
  an artificial older version. One live test fetched/downloaded 0.5.0 and
  validated size, hash, package, newer version, signing continuity, FileProvider
  access, and rejection of an intentionally wrong hash.
- Installed the exact published 0.5.0 APK on the emulator. The live current
  version dialog test passed. Physical phone receipt and OS installer completion
  are not verified; no routine phone USB installation was used.
- No store upload, review submission, testing track, or production rollout.

### Version 0.4.0 / Code 7

- Uploaded APK and manifest to R2; public download SHA-256 and byte size verified.
- APK: https://caregov-ota.dailyhotcoolmeme.workers.dev/apk/caregov-0.4.0-6e61b2d6b716.apk
- An emulator build with older version metadata (0.3.1 / code 6) fetched,
  downloaded, and verified the new APK, including FileProvider access and
  rejection of an intentionally wrong hash. One live OTA test passed.
- This older-version check uses current updater code, not a physical phone.
  Physical receipt and OS installer completion remain unverified.
- No store-console upload, review submission, or production rollout occurred.

### Version 0.3.1 / Code 6

- Uploaded 0.3.1 / code 6 to the dedicated R2 bucket.
- Public APK GET: exact full SHA-256 and byte-size match.
- Public manifest GET/HEAD: HTTP 200, JSON, `no-store, max-age=0`.
- Three Worker tests passed: non-cached manifest, immutable downloadable APK,
  HEAD response, unrelated-path rejection and read-only method enforcement.
- Android emulator: an older-version build with the R2 updater fetched and
  verified 0.3.1, including rejection of a wrong SHA-256; five tests passed.
  After installing the published APK, six tests passed including the live
  current-version dialog. These are emulator checks, not physical-device receipt.
- Physical receipt/installation of 0.3.1 remains unverified.

The current APK uses this workstation's existing Android debug signing key.
Preserve that key outside Git; a different key cannot update this installation.
This is not a store-release signing configuration. No keys or tokens belong in
this public repository.

`LiveUpdateTest` is opt-in, uses the public network, and requires an older version
installed than the published manifest. Run instrumentation with
`-e liveOta true -e class com.ourmine.caregov.demo.LiveUpdateTest`.

## Verification (2026-10-06)

- Samsung SM-S921N, Android 16: version 0.2.1 installed and launched by USB.
- On that phone, the published version 0.2.2 was fetched and downloaded over
  HTTPS. Size, SHA-256, package identity, newer version, Android compatibility,
  and matching APK signing certificate checks passed.
- An intentionally mismatched SHA-256 was rejected. FileProvider read passed.
- Version 0.2.2 (version code 4) was then installed by USB, not by the OS installer.
- Five physical-device instrumentation tests passed, including the in-app
  current-version screen. The separate live-download test passed once.
- The public manifest URL initially returned a cached 404, then became reachable.
- OS installation permission and installation confirmation were not automated
  or bypassed. Full in-app OS installer completion remains unverified.
- APK and source uploaded to GitHub. No store console upload or distribution.
