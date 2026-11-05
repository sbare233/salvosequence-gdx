#!/usr/bin/env bash
# 一键打包三件套的入口：桌面端（dist/SalvoSequenceGdx/）+ 安卓端（android/build/SalvoSequence.apk）。
#
#   bash build-tools/build-all.sh
#
# 安卓第一次跑之前先装 SDK（只需要一次）：
#   bash build-tools/install-android-sdk.sh
set -euo pipefail

ROOT=$(cd "$(dirname "$0")/.." && pwd)
cd "$ROOT"

bash "$ROOT/build-tools/build-desktop.sh"
bash "$ROOT/build-tools/build-apk.sh"

echo
echo "==== 打包完成 ===="
echo "桌面端: $ROOT/dist/SalvoSequenceGdx/"
ls -l "$ROOT/dist/SalvoSequenceGdx/SalvoSequenceGdx.jar" | awk '{ printf "  %s  %s\n", $5, $9 }'
echo "安卓端: $ROOT/android/build/SalvoSequence.apk"
ls -l "$ROOT/android/build/SalvoSequence.apk" | awk '{ printf "  %s  %s\n", $5, $9 }'
