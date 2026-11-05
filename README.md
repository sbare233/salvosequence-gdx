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

### 输入预压（模糊/辉光/孔径格栅）

后处理会把亮部摊开，直接吃原画容易糊成一片白。所以 `PixelGameView.drawFrame` 在上屏前先调一次输入：

| 条件 | 亮度 | 对比 |
|---|---|---|
| 风格 = 模糊 / 辉光 | ×0.8 | ×0.9 |
| 再叠孔径格栅 | 再 ×0.9 | — |

三个系数都在 `drawFrame` 上方的 `EFFECT_BRIGHTNESS / EFFECT_CONTRAST / GRILLE_BRIGHTNESS` 常量里。
实现塞在原来补格栅亮度的 `beamShader` 里（contrast → brightness → 束流软膝），不额外多一次全屏 pass。
辉光模式下 `drawFrame` 会被调用两次（屏幕铺底 + 输入捕获），两次都走同一套预压。

### 实时 3D 背景的像素化：拜耳 / 像素滤镜

设置面板的「背景滤镜」切换 `VideoSettings.backdropFilter`，`live/LiveBackdrops.post()` 里二选一：

| 滤镜 | 实现 | 观感 |
|---|---|---|
| 拜耳（默认） | `core/BayerPixelator.quantizeInPlace`（同尺寸版，8×8 网点） | 网点细密、保留星云的柔和过渡 |
| 像素 | `core/PixelArtFilter`（译自 Godot `PixelArt.gdshader`） | 亮度分档 + Sobel 描边，块面硬、边缘有描边 |

背景工坊里 `K` 切这两种滤镜、`J` 切像素滤镜的调色板（原色/暖/冷/灰）。

CRT 扫描线有三个设置项：**开关**、**形状**、**时机**（`VideoSettings.scanlines / scanlineShape / scanlineOrder`）。

| 形状 | 观感 |
|---|---|
| 直条 | 一整条贯穿整行的黑条，规整、最像"信号层面的行消隐" |
| 锯齿（默认） | 锯齿只落在**单个设计像素内部**：一个设计像素恰好由红/绿/蓝三条荧光条组成，绿条的黑线整体比红/蓝低 1 个设备像素，于是红蓝变暗的那一行里绿条仍然亮着，看起来像绿点从扫描线里戳出来 |

| 时机 | 观感 |
|---|---|
| 特效后 | 线条画在风格化结果之上，最锐利，辉光的亮部也会被压暗 |
| 特效前（默认） | 线条先于风格化生效；辉光会把线条一起晕开、发亮，更接近老电视 |

「特效前 + 辉光」时扫描线画进 gdx-vfx 的输入捕获再一起做辉光，避免同一道线在屏幕上被压暗两次。

**整数倍缩放要单独对齐**（`PixelGameView.drawScanlines`）：一个设计像素铺满 `px` 个设备像素，
黑条高度必须取整（`px=2 → 1`、`px=3 → 1`、`px=4 → 2`）。以前按 `period*0.5` 算，1080p 下
得到 1.5 像素高的线，会被硬件插值摊成两行半透明的灰，画面上就是深浅不一的横纹。
条宽同理：孔径格栅的栅距取 `max(1, round(px/3))`，让"一个三色组 ≈ 一个设计像素"的比例在
2×/3×/4× 屏幕上都成立。黑条透明度从 0.75 放到 0.5——显像管的行间隙只是"这一行没被扫到"，
不是一层黑纱。

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

### 节点框的颜色

节点外框不写死颜色，而是取这颗星在**星盘 0° 与 90° 两个正交姿态下**脚下背景的平均亮度
（各取 3x3 均值，避拜耳网点）：平均亮度 > 128 用**纯黑框**，否则用**纯白框**。
取两个姿态是为了让颜色与当前旋转角无关——只按当前帧取色，节点掠过亮度阈值会黑白来回跳；
只用第一次的取色，转半圈后又可能糊进背景。0°/90° 平均既不会闪，也不至于转过去就看不见。
阈值是 `CardLoopScreen.FRAME_BLACK_ABOVE_LUM`（128），结果按节点 id 缓存，
实现在 `CardLoopScreen.nodeFrameColor(...)` / `backdropLuminance(...)`。

## 战斗波次与战术休整

一个战斗节点不再是"一场打完就走"，改成 **3 波连打**：

- 第 1/2/3 波的敌舰上限是 **3 / 4 / 6 艘**，目标战力是我方战力的 **35% / 50% / 70%**（再乘节点系数：
  普通 0.85、精英 1.0、最终旗舰 1.35）。所以第一波真的只有几艘侦察舰，越往后越多越硬。
- 打空一波就进 **战术休整**（`Run.Stage.LULL`）：界面**不另起一页**，而是像太空港那样把中间一块
  （`PORT_PANEL_X=122` 起、宽 256）换成休整面板，**自己的舰队挪到最右**（`PORT_BOARD_X=382`，256 宽），
  可以直接把船拖来拖去变阵。
- 面板里是「牌库」列表：**点一行**就把这张牌在本节点禁掉（下一波洗牌不抽它），**空格**出发打下一波。
  键盘也能用：上下选行、回车禁牌、空格出发。
- 禁牌**只在本节点生效**：`Run.bannedCards` 进节点时清空，`Battle` 构造时接收 `Set<String>`，`drawPile` 过滤掉。
- 打完第 3 波才走原来的节点结算（战利品 / 太空港 / 星图）。

参数在 `Run` 里：`WAVES_PER_NODE`、`WAVE_POWER`、`WAVE_SHIPS`；敌方编队生成走
`EnemyCatalog.squadForPower(..., maxShips)`，数量下限跟着本波上限走（不会强行凑 6 艘）。

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
| `K` | 切背景滤镜：拜耳 / 像素（和设置面板里的「背景滤镜」是同一个值） |
| `J` | 像素滤镜的调色板：原色 / 暖色 / 冷色 / 灰阶 |
| `Q/A` `W/S` `E/D` `R/F` `T/G` `Y/H` `B` | 拜耳模式：像素块 / 色阶 / 抖动 / 亮度 / 对比 / 饱和 / 深空化；像素模式：色阶 / 锐化 / 抖动 / 描边 / 阈值（后两项 `Y/H`、`B` 只对拜耳有效） |
| `ENTER` | 导出当前这张到 `backdrops/custom/`（游戏会当自定义主题自动轮播） |
| `P`（或参数面板下方的「批量导出整个文件夹」按钮） | 按当前参数把 `backdrops-src/` 里所有图各处理一张，写进新文件夹 `backdrops/batch/<yyyyMMdd-HHmmss>/` |
| `L`（或左上第二个按钮） | 切实时 3D 背景：星云 / 恒星 / 星球 / 脉冲星 / 关（见下节） |

工坊的静态预览、`ENTER` 导出、`P` 批量导出都跟着 `K` 选的滤镜走（`core/BackdropFiltering`
统一分流：拜耳走 `BayerPixelator`，像素走 `PixelArtFilter` 先面积平均 cover 到 640×360 再过滤），
右侧参数面板会换成对应那套参数。实时 3D 背景同样吃这个设置（见「实时 3D 背景的像素化」）。

实时 3D 预览的操作：**拖动画面**转偏航/俯仰，**滚轮**调远近，**点右侧参数行**直接增减（右半 +、左半 −；点行首标题翻页），`-` / `=` 也行（`↑`/`↓` 选行）。整屏拖拽热区只在实时预览打开（`L`）时登记，开关一变热区会重建。

批量导出每次新建一个时间戳文件夹，旧批次不会被覆盖；文件名带参数（拜耳 `原名-x像素块-l色阶.png`，
像素 `原名-px-s色阶.png`），同名源图（如 `a.png` 与 `a.jpg`）会自动加 `-2` 后缀。单张读图失败只跳过并在结果里计数，
不影响其余图片。`backdrops/batch/` 不会被游戏自动加载——挑好的再拷进 `backdrops/custom/`。
星球模式（3D 行星实时渲染）没有批量导出：批量只处理 `backdrops-src/` 里的静态图。

## 实时 3D 背景（live/）

战斗和太空港的底图现在直接吃实时渲染的 3D 场景，不再是纯程序化星云。
场景在 `src/main/java/com/hysx/salvosequence/live/`，每个场景渲进 320×180 的帧缓冲、
读回 ARGB 再 2× 放大贴到 640×360 画布上（像素对齐，不插值）：

| 场景 | 来源 | 说明 |
|---|---|---|
| `nebula` | 星云体积光 MVP | 橙红体积云 + 星点，相机不动；一帧约 2.3 ms |
| `star` | 恒星 MVP | 颗粒感太阳，压在右上角；一帧约 1.1 ms |
| `gleba` | 游戏自带的行星 | 复用 `GlebaPlanet`，生成期间返回 null、退回程序化背景 |
| `pulsar` | 脉冲星 MVP | 中子星 + 磁极喷流 + 磁力线，落在右上角；一帧约 1.6 ms |

- 选哪个：战斗节点按 `节点号 + 访问次数` 确定性挑一个（同一个节点每次进都一样）；
  工坊里按 `L` 手动切（`LiveBackdrops.setSelected`），手动选中后战斗一律用它。
- 压暗：实时底图会盖在棋盘和文字下面，所以按场景压暗——星云/星球留 70%，
  恒星 32%、脉冲星 30%（这两个有大片高光）。
- 刷新：每 15 fps 重渲一帧，其余帧复用缓存（省下来的一半时间给软件画布）。
- **相机不再自动转**：早期版本让相机随时间缓慢环绕，现在相机只由「指针拖拽旋转 / 滚轮缩放 /
  存档里的角度」决定，所以同一场景在战斗里是静止的，不会自己漂移。
- 调参：背景工坊右侧「实时参数」面板，第 0 页是 `偏航 / 俯仰 / 距离`，往后每页 6 个场景参数
  （点标题翻页、点行选中、再点同一行右半 `+` 左半 `-`，键盘 `↑↓` 选行、`-`/`=` 调值）。
  改完停手 0.4 秒自动存到 `live-params.conf`（`LiveSettings`，桌面在工作目录、安卓在
  `Android/data/com.hysx.salvosequence/files/`），启动时 `PixelGameView.create()` 读回。
- 兜底：GL 初始化失败、着色器编译不过（安卓驱动差异）或行星还在生成时会返回 null，
  `CardLoopScreen.drawBackdrop` 自动退回原来的程序化主题背景。

四个场景（左上星云、右上恒星、左下星球、右下脉冲星）与战斗里的样子：
`docs/live-scenes.png`、`docs/live-battle.png`。

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
星盘换帧的 PNG 解码挪到后台线程（每帧取帧 ≈0.03ms），快速拖动不再卡。解码队列是
**"永远只解最新目标"**：拖动时目标角度每帧都在跳，排一堆旧帧只会把最新那帧堵在后面
（画面先停住、等新帧出来再猛跳到目标角度，看上去就是"突变"）；现在后台任务解完一张
会再看一眼最新目标，过期的直接跳过。目标帧还没解好时退回缓存里**角度最近**的一档
（不是"最后一张"——那可能差几十度）。

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
- **开格栅时自动切双线性平滑放大**（`PixelGameView.updateSampling()`）：最近邻会把每个
  设计像素硬切成 3×3 的方块，三色荧光条被黑矩阵切得太利落，看着像贴了层彩膜；改成
  双线性后相邻设计像素互相渗一点，荧光条也跟着柔化，才有电子束"浸染"进玻璃的感觉。
  这条会盖过 F7 的像素化开关（格栅优先级更高），关掉格栅即恢复 F7 的最近邻。

`docs/crt-grille.png` 是同一帧的左右对照（左：关；右：开，4 倍放大）；
`docs/grille-smooth.png` 是"硬像素 vs 平滑浸染"的 4 倍对照。

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

## 彩蛋：人物立绘（F12）

任何界面按 `F12` 都能开关人物立绘（`sprites/portrait.png`，缩到 256 设计像素高、
站在左边、脚底对齐 338 行）。它画在所有界面内容之上、过渡遮罩之下，不占布局、
不改玩法；开关是会话级的，重开游戏恢复隐藏。

- 自测/截图：加 `-Dsalvosequence.portrait=true` 一进游戏就显示。
- 原来的诊断双导出（设计缓冲 + GL 屏幕帧）挪到 `F10`。
- 实现在 `ui/ScreenManager`（懒加载 + 全局热键），`screen/experiment/PortraitOverlay` 只管贴图。
