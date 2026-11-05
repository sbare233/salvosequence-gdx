# 星图改造前的备份（2026-11-04 01:50）

用户 m01866 要求「备份现在的全部星图代码」后，把改动会碰到的星图相关源文件原样拷到这里。
直接按下面的相对路径覆盖回项目根目录即可恢复成旧版星图。

## 备份内容

| 备份内路径 | 作用 |
| --- | --- |
| `src/main/java/com/hysx/salvosequence/screen/CardLoopScreen.java` | 全屏星图绘制（程序化星云 + 星系盘 + 尘埃 + 前景星 + 节点/航路）、缩放 `starZoom`、平移 `starCenterX/Y`、拖动热区 |
| `src/main/java/com/hysx/salvosequence/run/Run.java` | `StarNode` 记录、`GALAXY_SQUASH = 0.62`、`buildStarMap()/tryBuildStarMap()/fallbackStarMap()`、`skyCoordOf()` 与真实星名分配 |
| `src/main/java/com/hysx/salvosequence/tools/LoopValidation.java` | 离线校验循环时用到的星图接口 |
| `src/test/java/com/hysx/salvosequence/run/RunTest.java` | 星图生成相关单测 |

## 旧版星图的样子（BUILD_TAG `2026-11-03-backdrop-batch-v15`）

- 背景全部用代码生成：`buildNebula()` 星野底噪 → `buildGalaxy()` + `buildGalaxyOverlay()` + `buildDust()` 三张缓存盘面 → 按 `starZoom` 缩放贴到画布（`blitArgbTileDithered(..., 14)` 走拜耳抖动）→ 900 颗尘埃粒子 + 1500 颗前景星 + 90 颗核球星团。
- 交互：滚轮 = 以指针为锚缩放（`starZoom` 1.0–3.0，`Math.pow(1.0025, delta)`）；按住左右拖动 = 平移星图（`starCenterX/Y`，`clampStarCenter()` 限制范围）。
- 节点坐标：`starScreenPos = (starCenterX + cos(theta)*radius*zoom, starCenterY + sin(theta)*radius*GALAXY_SQUASH*zoom)`，`GALAXY_SQUASH = 0.62`，中心初值 (320, 186)。
- 星区标签按「半径 210 × starZoom」摆位，核心屏障用虚线圈。

## 恢复方式

```bash
cp -r backup/star-map-20261104-015047/src .
```

（会覆盖当前同名文件，包括 `CardLoopScreen.java`、`Run.java`；如需保留新版，先另行备份。）
