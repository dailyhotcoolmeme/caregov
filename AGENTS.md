# Caregov Delivery Rules

- Deliver Android updates through Cloudflare R2, not repeated USB installation.
- Batch authorized workflow changes. Build and validate after the batch, then
  publish one OTA update; do not build or upload a release for every stage.
- Reference: `/Users/ourmine/dev/ootd/app/scripts/publish-apk.mjs` and
  `publish-ota.mjs` (mozzzi / 모입찌). Read only; do not change that project.
- Caregov uses Expo/React Native in `mobile/` and real JavaScript bundle OTA,
  following mozzzi's self-hosted Expo Updates protocol. Kotlin/Compose in `app/`
  is the legacy client, not the current UI implementation.
- The transition requires one same-signed APK installation with Android
  confirmation. Subsequent compatible UI/workflow changes use bundles, not APKs.
  Native dependencies/runtime changes require a new APK.
- Dedicated bucket: `caregov-media-apac`; Worker: `caregov-ota`.
- Bundle manifest: `https://caregov-ota.dailyhotcoolmeme.workers.dev/manifest`.
- Transition APK manifest: `https://caregov-ota.dailyhotcoolmeme.workers.dev/android.json`.
- APK keys include version and content hash. Never overwrite an APK URL with
  different bytes. Validate the public download hash before updating the pointer.
- Routine updates: run `node --test mobile/tests/*.test.mjs scripts/ota-worker.test.mjs`,
  then `node scripts/publish-bundle.mjs`. Keep runtimeVersion unchanged unless
  native code/dependencies change. Upload alone does not prove device receipt.
- Transition/native APK only: `bash scripts/build-mobile.sh`, then
  `node scripts/publish-r2.mjs`. Keep the same signing key and raise version code.
- Persist records/account before reload. Never reload while a form/dialog/save
  is active. Preserve the legacy preferences; never reset invalid stored data.
- Credentials stay local; use `scripts/cloudflare-env.mjs`, never copy tokens
  into files, logs, Git, or memory. Do not prune other projects or old APKs.
- `updates/android.json` and GitHub release 0.3.1 are a one-time bridge for
  installed 0.2.x/0.3.0 apps. New apps use R2; do not revert to GitHub hosting.
- Store console upload, review, and production release remain separately gated
  by the owner's safety instructions. R2 upload is not a store release.
- Report uploaded state separately from actual phone installation and receipt.
