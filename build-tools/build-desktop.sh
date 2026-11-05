#!/usr/bin/env bash
# 桌面端打包：出 target/SalvoSequenceGdx.jar，并组装 dist/SalvoSequenceGdx/（双击即玩）。
set -euo pipefail

ROOT=$(cd "$(dirname "$0")/.." && pwd)
cd "$ROOT"

DIST="$ROOT/dist/SalvoSequenceGdx"
JAR="SalvoSequenceGdx.jar"

echo "[desktop] mvn clean package -DskipTests"
mvn -q clean package -DskipTests

echo "[desktop] 组装 $DIST"
rm -rf "$DIST"
mkdir -p "$DIST"
cp "target/$JAR" "$DIST/$JAR"
cp README.md "$DIST/README.md"

# 启动器：内容全是 ASCII + CRLF，中文 Windows 的 cmd（代码页 936）也不会认错。
printf '@echo off\r\nrem Salvo Sequence (libGDX build)\r\nstart "SalvoSequence" javaw -jar "%%~dp0SalvoSequenceGdx.jar"\r\n' \
    > "$DIST/启动游戏.bat"

BYTES=$(stat -c %s "$DIST/$JAR")
echo "[desktop] 完成: $DIST/SalvoSequenceGdx.jar ($(awk -v s="$BYTES" 'BEGIN { printf "%.1f", s / 1048576 }') MB)"
