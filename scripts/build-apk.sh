#!/usr/bin/env bash
set -euo pipefail

project_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$project_dir"

if [[ "$(uname -s)" == "Darwin" ]]; then
    export JAVA_HOME="$(/usr/libexec/java_home -v 17)"
    export ANDROID_HOME="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
fi

./gradlew :app:assembleDebug --console=plain "$@"
mkdir -p output/apk
cp app/build/outputs/apk/debug/app-debug.apk output/apk/caregov.apk
echo "APK: $project_dir/output/apk/caregov.apk"
