#!/usr/bin/env bash
# Installs a self-contained Android build toolchain inside $HOME (no root needed):
#   $HOME/tc-jdk17      -> Temurin JDK 17
#   $HOME/tc-sdk        -> Android SDK (platform-tools, platforms;android-35, build-tools;35.0.0)
#   $HOME/tc-gradle     -> Gradle 8.9 (matches android/.gradle/8.9)
# Safe to re-run: each step is skipped when the target already exists.
set -u

TC_ROOT="${TC_ROOT:-$HOME/android-tc}"
mkdir -p "$TC_ROOT"
cd "$TC_ROOT"

say() { echo "[setup] $*"; }

if [ ! -x "$TC_ROOT/jdk/bin/java" ]; then
  say "downloading JDK 17"
  curl -fsSL -o jdk.tar.gz \
    "https://api.adoptium.net/v3/binary/latest/17/ga/linux/x64/jdk/hotspot/normal/eclipse"
  rm -rf jdk && mkdir -p jdk
  tar -xzf jdk.tar.gz -C jdk --strip-components=1
  rm -f jdk.tar.gz
else
  say "JDK already present"
fi

if [ ! -x "$TC_ROOT/gradle/bin/gradle" ]; then
  say "downloading Gradle 8.9"
  curl -fsSL -o gradle.zip "https://services.gradle.org/distributions/gradle-8.9-bin.zip"
  rm -rf gradle && unzip -q gradle.zip && mv gradle-8.9 gradle
  rm -f gradle.zip
else
  say "Gradle already present"
fi

if [ ! -d "$TC_ROOT/sdk/platforms/android-35" ]; then
  say "downloading Android command-line tools"
  curl -fsSL -o cmdtools.zip \
    "https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip"
  rm -rf cmdtools && mkdir -p cmdtools
  unzip -q cmdtools.zip -d cmdtools
  rm -f cmdtools.zip
  export JAVA_HOME="$TC_ROOT/jdk"
  export PATH="$TC_ROOT/jdk/bin:$PATH"
  say "installing SDK packages"
  yes | "$TC_ROOT/cmdtools/cmdline-tools/bin/sdkmanager" \
    --sdk_root="$TC_ROOT/sdk" --licenses >/dev/null 2>&1 || true
  yes | "$TC_ROOT/cmdtools/cmdline-tools/bin/sdkmanager" \
    --sdk_root="$TC_ROOT/sdk" \
    "platform-tools" "platforms;android-35" "build-tools;35.0.0" || true
else
  say "Android SDK already present"
fi

echo "[setup] DONE"
