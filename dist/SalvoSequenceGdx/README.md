# 齐射序列 Salvo Sequence — libGDX 移植版

原项目（`../salvosequence`）基于 JavaFX Canvas 渲染；本项目把它移植到 **libGDX（LWJGL3 后端）**，
游戏逻辑、像素管线、界面代码 1:1 保留，包名不变（`com.hysx.salvosequence.*`），不依赖任何 JavaFX。

## 运行

```bash
mvn compile exec:java -Dexec.mainClass=com.hysx.salvosequence.PixelHudApp   # 开发运行（默认全屏）
mvn package                    # 产出 target/SalvoSequenceGdx.jar（fat jar，含原生库）
java -jar target/SalvoSequenceGdx.jar
```

常用按键：`F11` 切全屏 · `Ctrl+W` 回 1280x720 窗口 · `Ctrl+Q` 退出 · `C` 直达组舰者 · `F1` 调试 · `F4` 素材实验室。

系统属性：`-Dsalvosequence.windowed=true` 窗口启动；`-Dsalvosequence.music=battle` 试听战斗曲；
`-Dcard.seed=20240601` 固定种子。

## 打包

```bash
bash build-tools/build-all.sh      # 一键：桌面 dist/SalvoSequenceGdx/ + 安卓 android/build/SalvoSequence.apk
bash build-tools/build-desktop.sh  # 只打桌面（mvn package + 组装 dist，等价 package-jar.bat）
bash build-tools/build-apk.sh      # 只打安卓
```

桌面端产物是 `dist/SalvoSequenceGdx/`：fat jar + `启动游戏.bat`（双击即玩）。
安卓端产物是 `android/build/SalvoSequence.apk`，`adb install -r` 就能装。

APK 那条走的是 SDK 自带命令行工具（javac → d8 → aapt2 → zipalign → apksigner），不依赖
Gradle/AGP；首次使用先跑 `build-tools/install-android-sdk.sh` 把 SDK 装到 `/d/android-sdk`
（或设 `ANDROID_SDK_ROOT`）。**minSdk 26 / targetSdk 34**：共享代码用了 `List.of`、
`String.isBlank`、`Stream.toList` 等 Java 9+ 库 API，`--lib android.jar --min-api 26`
交给 d8 时它会把这些调用就地换成合成的 `*$$ExternalSyntheticBackport*` 辅助类（不需要
AGP 的 core library desugaring），所以 Android 8.0 以上都能装；`java.nio.file` 的写文件
和 `Files.readAllBytes` 是 API 26 才有的，这也是不能再往下降的原因。

## 安卓版的差异

- `android/src/main/java` 覆盖了桌面专用实现：`CjkRasterizer`/`PixelText`（字体光栅化走
  `android.graphics`，同一套 3 倍光栅 + 盒式平均）、`Sound`（libGDX `Sound` 播音效）、
  `Music`（暂为静音占位，APK 里不放 MIDI）、`PngWriter`（`Bitmap.compress`）、
  `SalvoSequenceApp`（`closeAudio()` 空实现）。
- 资源走 `assets/`：`Assets.setPlatformLoader` 由 `AndroidLauncher` 接到 `AssetManager`。
- **背景工坊两端共用同一份界面代码**：图片编解码抽象成 `core/ImageCodec`（桌面
  `AwtImageCodec` 用 ImageIO，安卓 `AndroidImageCodec` 用 BitmapFactory），工作目录抽象成
  `core/WorkDir`（桌面是当前目录，安卓是 `Android/data/com.hysx.salvosequence/files/`）。
  所以手机上的 `backdrops-src/`、`backdrops/custom/` 就在那个目录下，用文件管理器把图
  拷进去再进工坊即可。
- 桌面端的 `tools/` 打包与 MIDI 合成器不进 APK。

## 画质风格（设置面板里切换）

| 风格 | 实现 |
|---|---|
| 原画 | 直绘 |
| 模糊 | 单 pass 高斯 + SCREEN 叠加（自写着色器） |
| 辉光 | **gdx-vfx BloomEffect**（开源后处理链：亮部阈值 → 多 pass 高斯 → 原画合成） |

辉光观感在 `ui/PixelGameView` 顶部的 `BLOOM_THRESHOLD / BLOOM_BLUR_PASSES / BLOOM_BLUR_AMOUNT / BLOOM_INTENSITY / BLOOM_BASE_INTENSITY` 常量里调。

CRT 扫描线（每个设计像素压暗下半像素，周期跟随整数倍缩放）可通过设置面板的「扫描线时机」选择：

| 时机 | 观感 |
|---|---|
| 特效后（默认） | 线条画在风格化结果之上，最锐利，辉光的亮部也会被压暗 |
| 特效前 | 线条先于风格化生效；辉光会把线条一起晕开、发亮，更接近老电视 |

「特效前 + 辉光」时扫描线画进 gdx-vfx 的输入捕获再一起做辉光，避免同一道线在屏幕上被压暗两次。

## 触摸操作（安卓）

桌面鼠标键盘的语义原样保留；触摸屏上由 `ui/TouchGestures` 把手势翻译成同一套热区事件：

| 手势 | 行为 |
|---|---|
| 轻点 | 等同鼠标左键点击（按下 + 松开同区） |
| 长按（约半秒不动） | 等同鼠标悬停：弹出按钮/卡片的说明，松手不会误点 |
| 双指捏合 | 等同滚轮缩放（行星视角），两指中点即缩放锚点；张开一个「滚轮格」等于滚一格 |
| 系统返回键 / 左下角「返回」键 | 等同 `ESC`；在标题页且设置未开时退出游戏 |

判定细节：轻点<b>不会</b>弹出悬浮说明（触摸屏没有悬停，说明只由长按触发，松手即收回）；
长按在手指移动超出 8 设计像素后取消（转成拖动）；第二根手指落下时，单指那次按下会被作废，
避免缩放时误触按钮；捏合结束后的残余手指拖动会被吞掉，不会把星图带着跑。

## 星图（mapback/）

星图背景不再是程序生成的星系，而是 `src/main/resources/mapback/` 里的 1440 张 640×360
角度帧：同一个星系盘绕盘法线每 0.25° 渲染一帧（`frame_%03d_%05.1fdeg-x1-l12.png`，已做过拜耳量化，
所以代码里直接整幅贴、**不再二次抖动**）。

- **左右拖动 = 旋转星盘**：0.5°/设计像素，转到哪一档（0.25°）就换哪张帧；上下拖动无效，滚轮不再缩放。
- **节点画在同一个 3D 盘面上，带透视**：盘面坐标 `(cos(θ+φ)·r, sin(θ+φ)·r)` 经 `StarDisk.project()`
  做「相机俯角 30°、消失点 (320, -40)」的透视投影后落到屏幕（`ELEVATION_DEG = 30`、`VANISH_Y = -40`，
相机距离由 `D = (CENTER_Y - vanishY) / tan(30°)` 反推；消失点 Y 是**运行时可调**的
  （`StarDisk.vanishY()/setVanishY()`：星图里 `[` 抬高、`]` 压低、`\` 复位到 `DEFAULT_VANISH_Y = -50`，
  底部提示会显示当前值）；盘面坐标再乘 `PLANE_SCALE = 0.76`，
让最外圈节点压在旋臂上）：
  近侧（屏幕下方）被放大、远侧被压缩，所以是真 3D 圆盘而不是简单压扁成椭圆。盘心 (320, 175)；
  星区标签、核心屏障圈、太阳标记都走同一个投影，跟着星盘一起转。
- 背景 640×360 **原尺寸一像素对一像素直贴**：不缩放、不再过拜耳，否则重采样会把原图的网点拉成
  忽大忽小的格子，看着像又抖了一遍。
- **盘面上撒了一层闪烁星**（`drawDiskTwinkles`）：420 颗、位置由固定种子生成（核心屏障圈
  `CORE_BARRIER_R = 30` 以内不放，太阳系那块留干净），跟星盘一起转，
  亮度按 `sin(time·speed + phase)` 呼吸；近侧（`StarDisk.depthScale > 1.15`）的亮星多画一个像素，
  大小也跟着透视走。
- 帧按需解码 + LRU 只留最近 16 张（约 15MB），不会一次把 1440 张（1.3GB）读进内存；
  缺图/坏图会记进黑名单，不会每帧重试。
- 帧序号与旋转角的符号由 `StarDisk.FRAME_SIGN = -1` 控制：实测这批图的旋臂图案随序号增大是逆时针转，
  与节点盘的正向相反，所以取负号让两者同向。

`mapback/` 会打进 APK（+69MB），如果只要小包，删掉资源目录后星图会退化成纯黑底 + 节点。

## 背景工坊（backdrops-src/）

把照片（星空、星云）拜耳像素化成游戏的像素背景：原图放进 `backdrops-src/`（PNG/JPG），
标题菜单或 `F6` 进入工坊，键盘实时调参预览。桌面是工程根目录下的 `backdrops-src/`；
安卓是这个目录（用文件管理器拷图进去即可）：

```
Android/data/com.hysx.salvosequence/files/backdrops-src/
Android/data/com.hysx.salvosequence/files/backdrops/custom/
```

手机上同样能调参、单张导出与 `P` 批量导出，只是没有物理键盘时用屏幕上的覆盖热区
（工坊里每个参数行都能点）。

| 操作 | 行为 |
|---|---|
| `←` / `→` | 换图 |
| `Q/A` `W/S` `E/D` `R/F` `T/G` `Y/H` `B` | 像素块 / 色阶 / 抖动 / 亮度 / 对比 / 饱和 / 深空化 |
| `ENTER` | 导出当前这张到 `backdrops/custom/`（游戏会当自定义主题自动轮播） |
| `P`（或参数面板下方的「批量导出整个文件夹」按钮） | 按当前参数把 `backdrops-src/` 里所有图各处理一张，写进新文件夹 `backdrops/batch/<yyyyMMdd-HHmmss>/` |

批量导出每次新建一个时间戳文件夹，旧批次不会被覆盖；文件名带参数（`原名-x像素块-l色阶.png`），
同名源图（如 `a.png` 与 `a.jpg`）会自动加 `-2` 后缀。单张读图失败只跳过并在结果里计数，
不影响其余图片。`backdrops/batch/` 不会被游戏自动加载——挑好的再拷进 `backdrops/custom/`。
星球模式（3D 行星实时渲染）没有批量导出：批量只处理 `backdrops-src/` 里的静态图。

## 性能（实测）

1080p 全屏、`-Dsalvosequence.profile=true`、关 vsync 测 600 帧：

| 阶段 | 优化前 | 优化后 |
| --- | --- | --- |
| 软件画布 draw | 3.03 ms | 0.74 ms |
| 纹理上传 | 0.71 ms | 0.34 ms |
| 原画 blit | 0.02 ms | 0.01 ms |
| 辉光后处理 | 0.25 ms | 0.20 ms |
| **每帧合计** | **4.19 ms（238 FPS）** | **1.46 ms（686 FPS）** |

提升来自四处：底色/尘埃带只在需要时扫（三角函数改查表 + 和角公式）、`ditherRect` 按步长跳写、
桌面端用 `GL_BGRA` 直接灌 `IntBuffer` 上传、辉光链固定跑 640×360 再一次性放大。
星盘换帧的 PNG 解码挪到后台线程并预取邻近档（每帧取帧 ≈0.03ms），快速拖动不再卡。

调试开关：`-Dsalvosequence.style=RAW|BLUR|BLOOM`、`-Dsalvosequence.profile`、
`-Dsalvosequence.novsync`、`-Dsalvosequence.smoke.png=<path>`、`-Dsalvosequence.smoke.frames=<n>`。

## 孔径格栅（aperture grille）

扫描线是**行**，孔径格栅是**列**：荫栅式显像管（Trinitron 一系）在玻璃后面挂着
三色荧光竖条，条纹之间是黑矩阵，所以画面近看是一条条 R/G/B 的竖线。两边年代相同，
代码里也做成并列的两个开关（设置页「孔径格栅」，可与扫描线同时开，默认关）。

- 实现是 3×1 的 R/G/B 纹理横向平铺、一次 `batch.draw` 画完，混合方式是**乘法**
  （`GL_DST_COLOR, GL_ZERO`）：红条只保留 R 通道、G/B 乘 `GRILLE_PASS = 0`，也就是
  每条荧光粉**只发自己那一种色**，另外两个通道直接归零（不做任何混色）。绿/蓝条同理。
- 只剩 1/3 的光会明显发暗，所以上屏前先过一遍"束流补偿"着色器（`BEAM_GAIN = 3.0`）：
  `x*g/(1+x*(g-1))` 这种软拐点曲线，暗部按 g 放大、亮部渐近到 1。
  **不能直接乘 g 再截断**——拜耳抖动的网点本来就在中灰，硬乘 3 会把整片网点顶到
  255，亮度差被抹平，星系会糊成一坨白斑；软拐点全程单调、不截断，网点疏密仍然是亮度。
  实测整屏平均亮度：关格栅 10.42、开格栅 7.37（不补偿只有 3.48）。
- 三种荧光粉的整体亮度另有三个独立常量 `PHOSPHOR_R/G/B`（默认都是 1.0），想让格栅
  偏暖/偏冷直接调这三个数；注意 `batch.setColor` 会把分量夹到 0..1，靠它提亮补挡光
  做不到，亮度只能靠 `BEAM_GAIN`（全局增益）调。
- 一条荧光条 = **一个设备像素**：显像管的栅距本来就远小于像素，跟着设计像素放大反而
  会变成 9px 一根的粗彩虹。所以窗口/全屏越大，格栅越细、越接近实机观感。
- 画在**所有风格化之后**（合成完再压）：真实显像管里格栅就贴在荧光粉前面，作用在最终
  出射的光上。

`docs/crt-grille.png` 是同一帧的左右对照（左：关；右：开，4 倍放大）。

## 架构差异（相对 JavaFX 原版）

- **渲染**：`PixelArt` 仍是 640x360 的 CPU `int[]` ARGB 缓冲，绘制原语零改动；上屏从
  JavaFX `WritableImage` 换成 `PixelArt.Sink` → `Pixmap` → `Texture`（Nearest 采样 + 整数倍缩放）。
- **颜色**：自实现 `core.Color`，API 口径同 `javafx.scene.paint.Color`，调色板/混色代码未改。
- **中文字体**：`CjkRasterizer` 从 Prism 换成 AWT（同一份点阵 TTF，同样的 3 倍光栅 + 盒式平均管线）。
- **热区**：`ScreenManager` 里的透明 `Region` 节点换成软件命中测试（`Hotspot` 列表），
  点击=按下+松开同区、拖动手势抓取、enter/exit、光标样式都与原版语义一致。
- **图片加载**：JavaFX `Image` 全部换成 `core.PngLoader`（libGDX Pixmap 解码 → 非预乘 ARGB）。
- **音频**：不变，仍是 JDK 自带 `javax.sound.sampled` / MIDI 合成。

## 工具（tools/）

- `CardLoopApp`：直达组舰者玩法循环的可玩入口
- `CanvasAudit`：逐界面导设计稿 + 整屏块状分析（`-Daudit.outdir=...`）
- `CardLoopAudit`：固定种子把一局从选舰驱动到编入，导 11 张核对稿
- `FlickerAudit`：文本闪烁逐帧导图
- `SpriteExport` / `OutlineLab` / `ModuleLab` / `ShipLab` / `AudioSelfTest` / `AudioProbe`：素材导出与自检
