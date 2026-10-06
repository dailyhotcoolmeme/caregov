# Caregov Delivery Rules

- Deliver Android updates through Cloudflare R2, not repeated USB installation.
- Batch authorized workflow changes. Build and validate after the batch, then
  publish one OTA update; do not build or upload a release for every stage.
- Reference: `/Users/ourmine/dev/ootd/app/scripts/publish-apk.mjs` and
  `publish-ota.mjs` (mozzzi / 모입찌). Read only; do not change that project.
- Caregov uses Kotlin/Compose APK updates, not Expo JavaScript bundle updates.
  Android installation confirmation is required. Do not claim silent updates.
- Dedicated bucket: `caregov-media-apac`; Worker: `caregov-ota`.
- Manifest: `https://caregov-ota.dailyhotcoolmeme.workers.dev/android.json`.
- APK keys include version and content hash. Never overwrite an APK URL with
  different bytes. Validate the public download hash before updating the pointer.
- Publish: `./scripts/build-apk.sh :app:lintDebug`, then
  `node scripts/publish-r2.mjs`. Keep the same signing key and raise version code.
- Credentials stay local; use `scripts/cloudflare-env.mjs`, never copy tokens
  into files, logs, Git, or memory. Do not prune other projects or old APKs.
- `updates/android.json` and GitHub release 0.3.1 are a one-time bridge for
  installed 0.2.x/0.3.0 apps. New apps use R2; do not revert to GitHub hosting.
- Store console upload, review, and production release remain separately gated
  by the owner's safety instructions. R2 upload is not a store release.
- Report uploaded state separately from actual phone installation and receipt.
