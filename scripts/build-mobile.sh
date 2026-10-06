#!/usr/bin/env bash
set -euo pipefail
root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
export JAVA_HOME="$(/usr/libexec/java_home -v 17)"
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
cd "$root/mobile"
npm ci --no-audit --no-fund
npx expo prebuild --platform android --no-install
cd android
./gradlew :app:assembleRelease :app:lintRelease --console=plain --max-workers=4 "$@"
mkdir -p "$root/output/apk"
cp app/build/outputs/apk/release/app-release.apk "$root/output/apk/caregov.apk"
node "$root/scripts/native-runtime.mjs" --record
echo "APK: $root/output/apk/caregov.apk"
