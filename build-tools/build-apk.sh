#!/usr/bin/env bash
# 打 Android APK：javac（android.jar + gdx）→ d8 → aapt2 链接 → 塞 dex/so/资源 → zipalign → apksigner。
# 不依赖 Gradle/AGP，全部用 SDK 自带命令行工具，方便在没有 Android Studio 的机器上出包。
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SDK="${ANDROID_SDK_ROOT:-/d/android-sdk}"
BT="${ANDROID_BUILD_TOOLS:-$SDK/build-tools/35.0.0}"
AJAR="$SDK/platforms/android-34/android.jar"
VER="1.13.1"
M2="${M2_REPO:-$HOME/.m2/repository}"
GDX="$M2/com/badlogicgames/gdx"
OUT="$ROOT/android/build"
# minSdk 26（Android 8.0）：共享代码用了不少 Java 9+ 的库 API（List.of/String.isBlank/Stream.toList…），
# d8 自带「库方法回填」：--lib android.jar + --min-api 26 会把 List.of/String.isBlank/Stream.toList
# 这些高版本 API 就地换成合成的 ExternalSyntheticBackport 辅助类，所以不用 AGP 的 core library desugaring。
MIN_API=26
TARGET_API=34
APK_NAME="SalvoSequence.apk"

[ -f "$AJAR" ] || { echo "缺 android.jar: $AJAR（先跑 build-tools/install-android-sdk.sh）"; exit 1; }
mkdir -p "$BT" >/dev/null

# ---------------------------------------------------------------- 依赖 jar
# gdx-backend-android 从 1.13.x 起只发 AAR（不再发 jar），所以直接从 Maven Central
# 拉 AAR 再解出里面的 classes.jar；gdx.jar 优先用本地 ~/.m2 里的。
DEPS="$ROOT/android/deps"
mkdir -p "$DEPS"
CENTRAL="https://repo.maven.apache.org/maven2/com/badlogicgames/gdx"

grab() { # $1=URL $2=目标文件
  [ -s "$2" ] && return 0
  echo "[apk] 拉取 $(basename "$2")"
  curl -fsSL -o "$2" "$1" || { echo "拉取失败: $1"; exit 1; }
}

GDX_JAR="$GDX/gdx/$VER/gdx-$VER.jar"
[ -s "$GDX_JAR" ] || GDX_JAR="$DEPS/gdx-$VER.jar"
grab "$CENTRAL/gdx/$VER/gdx-$VER.jar" "$GDX_JAR"

VFX_VER="0.5.5"
VFX_CORE="$M2/com/crashinvaders/vfx/gdx-vfx-core/$VFX_VER/gdx-vfx-core-$VFX_VER.jar"
VFX_FX="$M2/com/crashinvaders/vfx/gdx-vfx-effects/$VFX_VER/gdx-vfx-effects-$VFX_VER.jar"
grab "https://repo.maven.apache.org/maven2/com/crashinvaders/vfx/gdx-vfx-core/$VFX_VER/gdx-vfx-core-$VFX_VER.jar" "$DEPS/gdx-vfx-core-$VFX_VER.jar"
grab "https://repo.maven.apache.org/maven2/com/crashinvaders/vfx/gdx-vfx-effects/$VFX_VER/gdx-vfx-effects-$VFX_VER.jar" "$DEPS/gdx-vfx-effects-$VFX_VER.jar"
VFX_CORE="$DEPS/gdx-vfx-core-$VFX_VER.jar"
VFX_FX="$DEPS/gdx-vfx-effects-$VFX_VER.jar"

BACKEND_AAR="$DEPS/gdx-backend-android-$VER.aar"
BACKEND_JAR="$DEPS/gdx-backend-android-$VER.jar"
grab "$CENTRAL/gdx-backend-android/$VER/gdx-backend-android-$VER.aar" "$BACKEND_AAR"
if [ ! -s "$BACKEND_JAR" ]; then
  echo "[apk] 从 AAR 解出 backend classes.jar"
  unzip -o -q -j "$BACKEND_AAR" classes.jar -d "$DEPS"
  mv -f "$DEPS/classes.jar" "$BACKEND_JAR"
fi
# libGDX 1.13 的 AndroidApplication.init() 在 SDK≥30 时固定 new AndroidXKeyboardHeightProvider，
# 它依赖 androidx.core（本工程手打 APK 不带 AndroidX）→ 真机 NoClassDefFoundError。
# 游戏不用软键盘，android/src 下给了一个零高度替身，这里把原 class 从 d8 输入里剔掉。
BACKEND_DEX_JAR="$DEPS/gdx-backend-android-$VER-slim.jar"
python build-tools/strip_class.py "$BACKEND_JAR" "$BACKEND_DEX_JAR" \
  com.badlogic.gdx.backends.android.keyboardheight.AndroidXKeyboardHeightProvider
CP="$AJAR:$GDX_JAR:$BACKEND_JAR:$VFX_CORE:$VFX_FX"

# ---------------------------------------------------------------- 编译
echo "[apk] 编译（--release 21，排除桌面专用源码）"
rm -rf "$OUT"
mkdir -p "$OUT/classes"
cd "$ROOT"
find src/main/java -name '*.java' \
  | grep -v '/tools/' \
  | grep -v '/PackagingApp.java' \
  | grep -v '/PixelHudApp.java' \
  | grep -v '/ui/SalvoSequenceApp.java' \
  | grep -v '/core/CjkRasterizer.java' \
  | grep -v '/core/PixelText.java' \
  | grep -v '/audio/Audio.java' \
  | grep -v '/audio/Mixer.java' \
  | grep -v '/audio/SoundClip.java' \
  | grep -v '/audio/SoundManager.java' \
  | grep -v '/audio/Sound.java$' \
  | grep -v '/audio/Music.java' \
  | grep -v '/audio/MusicRegistry.java' \
  | grep -v '/core/AwtImageCodec.java' > "$OUT/sources.txt"
find android/src/main/java -name '*.java' >> "$OUT/sources.txt"
echo "[apk] 源码 $(wc -l < "$OUT/sources.txt") 个文件"
javac -encoding UTF-8 --release 21 -nowarn -cp "$CP" -d "$OUT/classes" @"$OUT/sources.txt"

# ---------------------------------------------------------------- dex
echo "[apk] d8 → classes.dex"
mkdir -p "$OUT/dex"
# d8（build-tools 34 自带 8.2）只认 jar/zip，不认 class 目录，先打成 jar
( cd "$OUT/classes" && jar cf "$OUT/app-classes.jar" . )
java -cp "$BT/lib/d8.jar" com.android.tools.r8.D8 \
  --lib "$AJAR" --min-api "$MIN_API" \
  --output "$OUT/dex" \
  "$OUT/app-classes.jar" "$GDX_JAR" "$BACKEND_DEX_JAR" "$VFX_CORE" "$VFX_FX"

# ---------------------------------------------------------------- 资源
echo "[apk] 收集 assets"
mkdir -p "$OUT/assets"
cp -r src/main/resources/. "$OUT/assets/"
rm -f "$OUT/assets/music/"*.mid 2>/dev/null || true   # 配乐在 Android 上暂不提供

# 依赖 jar 里的 classpath 资源（gdx-vfx 的 gdxvfx/shaders/*、gdx 的默认字体）：
# APK 里没有 jar，AndroidFiles 会把 Classpath 类型映射到 assets/，所以解出来平铺进去。
echo "[apk] 依赖 jar 的 classpath 资源 → assets"
python build-tools/extract_resources.py "$OUT/assets" \
  "$GDX_JAR" "$VFX_CORE" "$VFX_FX" >/dev/null

# ---------------------------------------------------------------- aapt2
echo "[apk] aapt2 链接 manifest"
"$BT/aapt2.exe" link \
  -o "$OUT/base.apk" \
  -I "$AJAR" \
  --manifest "$ROOT/android/AndroidManifest.xml" \
  -A "$OUT/assets" \
  --min-sdk-version "$MIN_API" \
  --target-sdk-version "$TARGET_API" \
  --version-code 1 \
  --version-name 1.0

# ---------------------------------------------------------------- 原生库
echo "[apk] 解出 libgdx 原生库"
for abi in arm64-v8a armeabi-v7a; do
  JAR="$GDX/gdx-platform/$VER/gdx-platform-$VER-natives-$abi.jar"
  if [ ! -f "$JAR" ]; then
    N="com.badlogicgames.gdx:gdx-platform:$VER:jar:natives-$abi"
    ( cd "$ROOT" && mvn -q dependency:get -Dartifact="$N" -Dtransitive=false )
  fi
  mkdir -p "$OUT/lib/$abi"
  unzip -o -q -j "$JAR" '*.so' -d "$OUT/lib/$abi"
done

# ---------------------------------------------------------------- 组包
echo "[apk] 组包"
cp "$OUT/base.apk" "$OUT/unsigned.apk"
python "$ROOT/build-tools/apk_pack.py" "$OUT/unsigned.apk" "$OUT/dex" "$OUT/lib"

"$BT/zipalign.exe" -f -p 4 "$OUT/unsigned.apk" "$OUT/aligned.apk"

KS="$ROOT/android/debug.keystore"
if [ ! -f "$KS" ]; then
  echo "[apk] 生成调试签名"
  keytool -genkeypair -v -keystore "$KS" -storepass android -keypass android \
    -alias androiddebugkey -keyalg RSA -keysize 2048 -validity 10000 \
    -dname "CN=Android Debug,O=Android,C=US" >/dev/null 2>&1
fi

echo "[apk] 签名"
java -jar "$BT/lib/apksigner.jar" sign \
  --ks "$KS" --ks-pass pass:android --key-pass pass:android \
  --ks-key-alias androiddebugkey \
  --out "$OUT/$APK_NAME" "$OUT/aligned.apk"

java -jar "$BT/lib/apksigner.jar" verify --print-certs "$OUT/$APK_NAME" | head -3
ls -la "$OUT/$APK_NAME"
echo "[apk] 完成: $OUT/$APK_NAME"
