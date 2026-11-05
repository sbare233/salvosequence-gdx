#!/usr/bin/env bash
# 安装 Android SDK 最小集：cmdline-tools + platform-tools + android-34 平台 + build-tools 34
set -euo pipefail
SDK="${ANDROID_SDK_ROOT:-/d/android-sdk}"
mkdir -p "$SDK"
cd "$SDK"

if [ ! -d "$SDK/cmdline-tools/latest" ]; then
  echo "[sdk] downloading commandlinetools..."
  curl -sSL -o cmdtools.zip https://dl.google.com/android/repository/commandlinetools-win-11076708_latest.zip
  unzip -q -o cmdtools.zip -d "$SDK/tmp-cmdtools"
  mkdir -p "$SDK/cmdline-tools"
  rm -rf "$SDK/cmdline-tools/latest"
  mv "$SDK/tmp-cmdtools/cmdline-tools" "$SDK/cmdline-tools/latest"
  rm -rf "$SDK/tmp-cmdtools" cmdtools.zip
fi

export SDKMANAGER="$SDK/cmdline-tools/latest/bin/sdkmanager.bat"
export JAVA_HOME="${JAVA_HOME:-/c/Program Files/BellSoft/LibericaJDK-21}"
if [ ! -d "$JAVA_HOME" ]; then
  for c in "/c/Program Files/BellSoft/LibericaJDK-21" "/c/Program Files/Java/jdk-21" "/c/Program Files/Eclipse Adoptium"; do
    [ -d "$c" ] && JAVA_HOME="$c" && break
  done
fi
export PATH="$JAVA_HOME/bin:$PATH"
echo "[sdk] java: $(java -version 2>&1 | head -1)"

yes | "$SDKMANAGER" --sdk_root="$SDK" --licenses >/dev/null 2>&1 || true
"$SDKMANAGER" --sdk_root="$SDK" "platform-tools" "platforms;android-34" "build-tools;34.0.0" 2>&1 | tail -5

echo "[sdk] installed:"
ls "$SDK/platforms" "$SDK/build-tools" 2>/dev/null
