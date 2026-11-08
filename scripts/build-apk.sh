#!/usr/bin/env bash
# Builds the Ardoise APK with the local toolchain installed by
# setup-android-toolchain.sh. Usage: build-apk.sh [gradle tasks...]
set -o pipefail

export JAVA_HOME="${JAVA_HOME:-$HOME/android-tc/jdk}"
export PATH="$JAVA_HOME/bin:$PATH"
export ANDROID_HOME="${ANDROID_HOME:-$HOME/android-tc/sdk}"
export ANDROID_SDK_ROOT="$ANDROID_HOME"

GRADLE="${GRADLE:-$HOME/android-tc/gradle/bin/gradle}"
REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"

TASKS=("$@")
if [ ${#TASKS[@]} -eq 0 ]; then
  TASKS=(":app:assembleRelease")
fi

echo "[build] JAVA_HOME=$JAVA_HOME"
echo "[build] ANDROID_HOME=$ANDROID_HOME"
echo "[build] tasks: ${TASKS[*]}"

exec "$GRADLE" -p "$REPO_ROOT/android" "${TASKS[@]}"
