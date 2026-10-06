# Android Updates

The app reads `updates/android.json` from the public caregov repository on launch
and when the user selects the update action. APK files are hosted as GitHub
release assets. This channel is independent of any store distribution console.

Updates require a higher version code, the same application ID, compatible
Android requirements, and the same signing certificate as the installed app.
Downloads are checked against manifest size and SHA-256 before installation.
Android installation permission and the user's installation confirmation are
required; updates are not silent JavaScript updates.

## Publishing An Update

1. Increment the version code and version name in `app/build.gradle.kts`.
2. Run `./scripts/build-apk.sh :app:lintDebug` and verify the APK.
3. Upload `output/apk/caregov.apk` to the corresponding GitHub release.
4. Update `updates/android.json` with the APK URL, version, byte size, SHA-256,
   and concise user-facing release notes. Commit and push only after upload.
5. Verify public access and test downloading from an older installed version.

The current APK uses this workstation's existing Android debug signing key.
Preserve that key outside Git; a different key cannot update this installation.
This is not a store-release signing configuration. No keys or tokens belong in
this public repository.

`LiveUpdateTest` is opt-in, uses the public network, and requires version code 3
installed while version code 4 is published. Run instrumentation with
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
