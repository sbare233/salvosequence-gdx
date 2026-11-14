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
战斗中：`F2` 弹背景菜单（8 个场景逐行列出，↑↓ 选场景、←→ 选该场景的机位、`P` 切体积星云的配色（冷调蓝/原版暖褐/玫瑰红/面纱青/猎户橙/反射紫 六档）、`R` 换云形种子（12 种）、`B` 切行星巨构（原始/巢都/矿业星/轨道加速器/太空电梯/拉格朗日反应堆/光帆阵列/大气调节网/生物圈环带/星门/宇宙博物馆）、Enter 应用、Esc 还原） · `F3` 循环星球类型。

系统属性：`-Dsalvosequence.windowed=true` 窗口启动；`-Dsalvosequence.music=battle` 试听战斗曲；
`-Dcard.seed=20240601` 固定种子。

## 打包

```bash
bash build-tools/build-all.sh       # 一键：桌面 + 安卓 android/build/SalvoSequence.apk
bash build-tools/build-desktop.sh   # 桌面基础版（mvn package + 组装 dist，等价 package-jar.bat）
bash build-tools/build-exe.sh       # 桌面免装 Java 版（jlink 裁运行时，等价 package-exe.bat）
bash build-tools/build-jpackage.sh  # 桌面 jpackage 版（真 .exe 启动器，等价 package-jpackage.bat）
bash build-tools/build-apk.sh       # 只打安卓
```

桌面端三种产物，按需要挑一个跑（互不覆盖）：

| 脚本 | 产物 | 启动方式 | 目标机要装 Java |
| --- | --- | --- | --- |
| `build-desktop.sh` / `package-jar.bat` | `dist/SalvoSequenceGdx/` | `启动游戏.bat` | 要 JRE 21+ |
| `build-exe.sh` / `package-exe.bat` | `dist/SalvoSequenceGdx-exe/` | `启动游戏.bat`（调内置 runtime） | 不用 |
| `build-jpackage.sh` / `package-jpackage.bat` | `dist/SalvoSequenceGdx-app/SalvoSequence/` | `SalvoSequence.exe` | 不用 |

后两种都是**自带运行时**，整个目录拷走就能玩。区别在启动器：

- `build-exe.sh` 用 `jlink` 裁一份 `java.base`+`java.desktop`+`jdk.unsupported` 的运行时（约 56MB），
  启动器还是 `启动游戏.bat`（内容 ASCII+CRLF，调 `runtime\bin\javaw.exe`），总 144MB 左右。
- `build-jpackage.sh` 用 JDK 自带的 `jpackage` 出 app-image，启动器是真的 `SalvoSequence.exe`
  （带版本资源），运行时由 jpackage 自己 jlink，总 150MB 左右。想再出安装包把 `--type app-image`
  换成 `exe`/`msi`，需要先装 WiX Toolset 3.x。

两个注意点：

1. `jlink` 要用普通 JDK。GraalVM / Liberica NIK 的 jlink 会裁出带 JVMCI 的运行时，跑起来直接
   `Cannot use JVMCI compiler: No JVMCI compiler found` 退出，`build-exe.sh` 会拦下并提示改用
   `JLINK=/path/to/jdk/bin/jlink`；`jpackage` 同理可用 `JPACKAGE=...` 指定。
2. `jpackage` 在受限 shell / 沙箱里会把目标目录误判成不可写，报
   `Destination directory ... is not writable` 或 `Cannot invoke "java.nio.file.Path.getFileSystem()"`
   —— 换个普通命令行窗口重跑即可，不是脚本问题。
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

## 画质风格

最终上屏只有一个 CRT pass（见下方「CRT 滤镜」一节）：扫描线、格栅、桶形畸变和
邻像素混合都在同一个片元着色器里完成，没有额外的后处理链；实时 3D 背景的像素化属于
背景滤镜，和上屏 CRT 是两回事。

### 实时 3D 背景的像素化：拜耳 / 像素滤镜

设置面板的「背景滤镜」切换 `VideoSettings.backdropFilter`，`live/LiveBackdrops.post()` 里二选一：

| 滤镜 | 实现 | 观感 |
|---|---|---|
| 拜耳（默认） | `core/BayerPixelator.quantizeInPlace`（同尺寸版，8×8 网点） | 网点细密、保留星云的柔和过渡 |
| 像素 | `core/PixelArtFilter`（译自 Godot `PixelArt.gdshader`） | 亮度分档 + Sobel 描边，块面硬、边缘有描边 |

背景工坊里 `K` 切这两种滤镜、`J` 切像素滤镜的调色板（原色/暖/冷/灰）。

CRT 滤镜有四种模式（`VideoSettings.CrtFilter`）：**关 / 扫描线 / 孔径格栅 / 扫描线+格栅**。
设置页按回车循环；调试时可用 `-Dsalvosequence.crt=SCANLINE` 等直接指定。

- 扫描线是**行**：每个设计像素占 `scale` 个设备像素，行尾压暗一条设备像素线
  （2× 屏上一暗一亮，3× 屏上两亮一暗）。旧实现把 y 相位乘 0.3333，变成 3 个设计像素
  才一条线，还让横竖采样混在一起 —— 这是"像素大小不对、文字发虚"的根因。
- 孔径格栅是**列**：纵向红/绿/蓝荧光条，每条 1 个设备像素宽、三条一组，配比 0.5 / 1.5
  （对应 crt.txt 的 `maskDark` / `maskLight`）。格栅固定在**设备像素**上，不跟设计像素
  放大——游戏本身已经按 2×/3× 上屏，格栅再按设计像素算，每条会变成 3 个设备像素宽，
  看起来就像像素又被放大了一次。
- 格栅图案另有「格栅图案」一项（`VideoSettings.GrilleStyle`）：**栅条**（格栅2.txt 启用的
  纵向孔径格栅，三条一组）或**荫罩**（crt.txt 的旋转 shadow mask，`x + y * (6 / u_scale)`，
  一组 6 个设备像素、每条 2 像素宽，近看是斜纹；斜率随 2×/3× 自适应，保证一个设计像素行
  正好走满一组，否则 3× 时奇偶像素行会错开半组）。调试可用 `-Dsalvosequence.grille=SHADOW`。
- 「屏幕扭曲」是独立开关（`VideoSettings.screenWarp()`），**默认关**：关着画面四边是直的，
  只保留扫描线/格栅；打开后才有桶形弧边。调试可用 `-Dsalvosequence.warp=true`。
- 「混合柔化」（`VideoSettings.bloom()`，默认开）是**邻像素混色**，不是加亮，也不按
  "放大后的像素块"整体上色：权重按当前**设备像素**落在设计像素里的位置算
  （`f = fract(pos*res) - 0.5`），左右邻居 `exp2(-k·(f.x±1)²)` 始终参与，上下邻居
  再乘 0.5 且只在**格栅开着**时参与（`u_grille`）——扫描线是横向扫描，纵向柔化归格栅。
  最后除以权重和，亮度守恒。`k = C·s²`，`s = 2·scale/(scale+1)`，C 控制强度（越小越柔），
  所以 2×/3× 的平均渗色量接近。像素中心几乎不混、越靠边邻居权重越大，光是从边界连续
  渗出去的，不会把 3×3 个设备像素染成一块；平色区域不受影响。采样在扫描线之前发生，
  缝里的颜色来自原图而不是被压暗后的行。调试可用 `-Dsalvosequence.mix=false`（旧名
  `bloom` 仍兼容）；开/关对照（3× 全屏截图）见 `docs/crt-mix.png`。
- 扫描线、格栅、混合、扭曲可以单独开，也可以叠加；全开最接近 crt.txt 三分屏右侧那一块。

实现只有一个全屏片元着色器（`PixelGameView.makeCrtShader`）：取出帧纹理颜色后，
先做邻像素混合（`u_mix`），再乘扫描线系数（`u_scan`）、乘格栅系数（`u_grille`）；
`u_shadow` 选格栅图案，`u_warp` 控制桶形畸变。`OFF` 或缩放小于 2 时走直通路径，
不做任何额外采样。

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
- 帧按需解码 + LRU 只留最近 48 张（约 44MB），不会一次把 1440 张（1.3GB）读进内存；
  缺图/坏图会记进黑名单，不会每帧重试。
- 这 1440 张存的是**调色板 PNG（PNG8，每张只有 70~84 色，解出来是 RGB888 内存布局）**，再用
  `zopfli` 重压过 IDAT：比原来的 24 位真彩小 48%（1440 张 66.7MB → 34.6MB），用游戏自己的解码器
  逐张核对过，1440 帧 ARGB 全部逐字节一致。`PngLoader` 对 RGBA8888 / RGB888 / 其它格式各有一条分支，
  以后再换回真彩图也能读。
- 换 WebP / GIF 没有意义：gdx 的 `Pixmap` 根本不认 WebP（实测 `Image not of any known type`），
  单张 GIF 反而比 PNG8 大 31%；接成动画 GIF 靠帧间差能到 22MB，但只能从第 0 帧顺序解，星图随手
  拖到任意角度会卡死。
- 帧序号与旋转角的符号由 `StarDisk.FRAME_SIGN = -1` 控制：实测这批图的旋臂图案随序号增大是逆时针转，
  与节点盘的正向相反，所以取负号让两者同向。

`mapback/` 会打进 APK（+35MB），如果只要小包，删掉资源目录后星图会退化成纯黑底 + 节点。

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
| `gleba` | 游戏自带的行星 | 复用 `GlebaPlanet`，生成期间返回 null、退回程序化背景。**巨构层**：`B` 键（背景菜单里）循环 11 种——原始 / 巢都 / 矿业星 / 轨道加速器 / 太空电梯 / 拉格朗日反应堆 / 光帆阵列 / 大气调节网 / 生物圈环带 / 星门 / 宇宙博物馆；轨道巨构会按太阳方向在行星表面投下环带与缆绳影子 |
| `pulsar` | 脉冲星 MVP | 中子星 + 磁极喷流 + 磁力线，落在右上角；一帧约 1.6 ms |
| `crab` | 蟹状星云 | 体积丝状结构，云体自转 0.01 rad/s（10.5 分钟一圈） |
| `alro` | 体积星云 | gyroid 气壳 + 瑞利散射，**360° 全景烘焙**：开机烘 1280×720 全景（16 块 × 12 帧抖动采样，约 4.3 秒），之后转多少度都只是从全景取样；配色六档：冷调蓝 / 原版暖褐 / 玫瑰红 / 面纱青 / 猎户橙 / 反射紫；云形种子 12 种（改气壳半径、云团偏心、太阳方位） |

- 选哪个：战斗节点按 `节点号 + 访问次数` 确定性挑一个（同一个节点每次进都一样）；
  工坊里按 `L` 手动切（`LiveBackdrops.setSelected`），手动选中后战斗一律用它。
- 压暗：实时底图会盖在棋盘和文字下面，所以按场景压暗——星云/星球留 70%，
  恒星 32%、脉冲星 30%（这两个有大片高光）。
- 刷新：每 15 fps 重渲一帧，其余帧复用缓存（省下来的一半时间给软件画布）。
- 全景：体积星云既没有视差也没有动画，镜头怎么转看到的都是同一片天球。所以开战时把它
  烘成一张 1280×720 的等角（equirect）全景——16 块瓷砖、每块 320×180，各攒 12 帧抖动采样
  （约 4.3 秒铺满），之后每帧只是按相机朝向在 CPU 上查表取样（0.05 ms），转镜头不再跑光线步进。
  瓷砖按"正对相机"排序先铺底，所以烘到一半就转头也不会看到黑块。换配色档位会重新烘。
  规格看 `FullscreenNebula` 的 `panoramaCols()` / `panoramaRows()` / `panoramaSamples()`。
- **相机**：战斗里所有场景都会以 0.22°/s 缓慢横摆（工坊里关掉，方便停在某个机位）。体积星云是
  全景，横摆只是换个方向取样（取样表约 0.5 秒重算一次，0.1° 一步、不到半个像素，看不出跳），
  拖拽同样即时生效；拖了就停在那，不会自己滑走。
- 调参：背景工坊右侧「实时参数」面板，第 0 页是 `偏航 / 俯仰 / 距离`，往后每页 6 个场景参数
  （点标题翻页、点行选中、再点同一行右半 `+` 左半 `-`，键盘 `↑↓` 选行、`-`/`=` 调值）。
  改完停手 0.4 秒自动存到 `live-params.conf`（`LiveSettings`，桌面在工作目录、安卓在
  `Android/data/com.hysx.salvosequence/files/`），启动时 `PixelGameView.create()` 读回。
- 兜底：GL 初始化失败、着色器编译不过（安卓驱动差异）或行星还在生成时会返回 null，
  `CardLoopScreen.drawBackdrop` 自动退回原来的程序化主题背景。

四个场景（左上星云、右上恒星、左下星球、右下脉冲星）与战斗里的样子：
`docs/live-scenes.png`、`docs/live-battle.png`。

### 巨构材质异常（踩坑记录）

巨构（`gleba/render/Megastructures.java`）偶尔会冒出一块"材质不对"的杂色：亮黄斑、
马赛克、像贴错图的钢板。看着像驱动或法线问题，其实几乎都是下面五类，按出现频率排：

1. **对插值过的 `uv.x` 直接取整**。材质号写成 `float matId = floor(v_uv.x);`，
   而顶点上的 `u` 正好是整数（轮毂用 `2f`、桁架用 `3f`）。顶点着色器传过来的
   `v_uv.x` 在每个像素上是插值出来的，2.0 会抖成 1.9999999 或 2.0000001，
   `floor` 就在 1 和 2 之间逐像素跳——于是同一块圆盘上一半像素走"机件"分支、
   一半走"薄膜帆"分支，暖色和冷色 1 像素交错，就是那团黄斑。
   **规则：材质号一律 `floor(v_uv.x + 0.01)`**（星门的 `+0.001` 也是这个原因）。
   小数部分表示色调档位时，别让它跨到下一个整数上去。
2. **两个面几乎共面**。柱体的自带端盖和另加的一块圆盘只差 0.004，深度缓冲分不开，
   整片互相打架。**规则：要么删掉多余的面，要么拉开 ≥0.01 的轴向距离。**
3. **六边形圆柱**。`addCylinder(..., 6, ...)` 的侧面是一整块平面，端盖是一整块六边形，
   阳光加 Bayer 抖动之后会读成"一块贴错的暗红钢板"。**规则：圆柱至少 20 边，
   粗一点的机体 24 边。**
4. **拿世界坐标撒细纹**。`h11(floor(v_pos.x * 60.0) + ...)` 在 320×180 下每格不到 1 像素，
   镜头稍微一动相邻格子就翻色，整块板子像在抖。**规则：花纹只用 `v_uv`（跟着几何走），
   或者按"件号"取一个色调，不要用世界坐标。**
5. **`v_uv.y` 在不同图元上不是同一个意思**。圆柱侧面的 `y` 是绕轴的角向坐标，
   `addDisc`/`addCylinder` 端盖的 `y` 却是"圆心 → 外缘"的**径向**坐标。同一句
   `fract(v_uv.y * 18.0)` 在侧面上排 18 条环带，在端盖上却排成 18 圈同心环——
   轮毂在屏幕上只有几个像素时，18 圈同心环直接糊成一块雪花点白斑。
   **规则：径向/角向花纹要么只用侧面的 `uv`，要么改成跟视角走的渐变
   （`dot(n, 视线)`），别指望 `uv.y` 在所有面上含义一致。**

排查手法：`-Dsalvo.meshdbg=true` 打印每档 `uv` 的顶点数、半径范围和包围盒；
临时加个开关（当时的 `salvo.nohub` / `salvo.nodish`）把一块几何抽掉做二分；
`MegaShadowProbe` 会顺手写一张 `*_raw.png`（`LiveBackdrops.lastRaw`，
像素滤镜之前的那一帧），用来区分"着色器画出来的"和"滤镜抖出来的"。

## 性能（实测）

1080p 全屏、`-Dsalvosequence.profile=true`、关 vsync 测 600 帧：

| 阶段 | 优化前 | 优化后 |
| --- | --- | --- |
| 软件画布 draw | 3.03 ms | 0.74 ms |
| 纹理上传 | 0.71 ms | 0.34 ms |
| 原画 blit | 0.02 ms | 0.01 ms |
| 辉光后处理（v44 前的独立链路） | 0.25 ms | 0.20 ms |
| **每帧合计** | **4.19 ms（238 FPS）** | **1.46 ms（686 FPS）** |

> 表中「辉光后处理」是旧独立链路的测量值；v45 起混合柔化已并入 CRT pass，未单独重测。

提升来自四处：底色/尘埃带只在需要时扫（三角函数改查表 + 和角公式）、`ditherRect` 按步长跳写、
桌面端用 `GL_BGRA` 直接灌 `IntBuffer` 上传、帧纹理固定 640×360 再一次性放大。
星盘换帧的 PNG 解码挪到后台线程（每帧取帧 ≈0.03ms），快速拖动不再卡。解码队列是
**"永远只解最新目标"**：拖动时目标角度每帧都在跳，排一堆旧帧只会把最新那帧堵在后面
（画面先停住、等新帧出来再猛跳到目标角度，看上去就是"突变"）；现在后台任务解完一张
会再看一眼最新目标，过期的直接跳过。目标帧还没解好时退回缓存里**角度最近**的一档
（不是"最后一张"——那可能差几十度）。

调试开关：`-Dsalvosequence.crt=OFF|SCANLINE|GRILLE|SCANLINE_GRILLE`、
`-Dsalvosequence.grille=APERTURE|SHADOW`、`-Dsalvosequence.warp=true|false`、
`-Dsalvosequence.mix=true|false`（旧名 `bloom`）、`-Dsalvo.settings=open`、
`-Dsalvosequence.profile`、`-Dsalvosequence.novsync`、`-Dsalvosequence.smoke.png=<path>`、
`-Dsalvosequence.smoke.frames=<n>`。

## 孔径格栅（aperture grille）

扫描线是**行**，孔径格栅是**列**：荫栅式显像管（Trinitron 一系）在玻璃后面挂着
三色荧光竖条，所以近看是一条条 R/G/B 的竖线。现在它和扫描线是同一个 CRT pass 里的
三个系数（`u_scan` / `u_grille` / `u_mix`），设置页「CRT 滤镜」可单独开、也可叠加：

| 模式 | 观感 |
|---|---|
| 关 | 直通，最近邻像素，完全锐利 |
| 扫描线 | 只压暗行尾，文字仍然清楚 |
| 孔径格栅 | 纵向 RGB 条，每条 1 个设备像素宽 |
| 扫描线+格栅 | 完整 CRT 观感（默认） |

格栅图案可在设置页「格栅图案」切换：

| 图案 | 实现 | 观感 |
|---|---|---|
| 栅条（默认） | `mod(x, 3)`，每条 1 个设备像素 | 纵向 RGB 细线，最像格栅2.txt 的启用版本 |
| 荫罩 | `mod(x + y * (6 / u_scale), 6) / 2`，每条 2 个设备像素 | 斜向 RGB 纹，crt.txt 的 shadow mask；斜率随缩放自适应 |

两种图案的 3× 实拍对照见 `docs/crt-grille-styles.png`（左栅条、右荫罩，都开着扫描线）。

`docs/crt-modes.png` 是四种模式的对照（3× 全屏 1920×1080 上截取标题区域再放大）。`docs/crt-grille.png`、`docs/scanlines.png`
是旧实现的图，只能参考观感——新实现的行/列周期都对齐设备像素，不会再出现旧图里那种
随窗口变化的粗细。

## 架构差异（相对 JavaFX 原版）

- **渲染**：`PixelArt` 仍是 640x360 的 CPU `int[]` ARGB 缓冲，绘制原语零改动；上屏从
  JavaFX `WritableImage` 换成 `PixelArt.Sink` → `Pixmap` → `Texture`（Nearest 采样 + 整数倍缩放）。
- **颜色**：自实现 `core.Color`，API 口径同 `javafx.scene.paint.Color`，调色板/混色代码未改。
- **中文字体**：`CjkRasterizer` 从 Prism 换成 AWT（同一份点阵 TTF，同样的 3 倍光栅 + 盒式平均管线）。
- **热区**：`ScreenManager` 里的透明 `Region` 节点换成软件命中测试（`Hotspot` 列表），
  点击=按下+松开同区、拖动手势抓取、enter/exit、光标样式都与原版语义一致。
- **图片加载**：JavaFX `Image` 全部换成 `core.PngLoader`（libGDX Pixmap 解码 → 非预乘 ARGB）。
- **音频**：不变，仍是 JDK 自带 `javax.sound.sampled` / MIDI 合成。

## 界面用 Aseprite 画（ui/）

界面元素的"在哪、多大、点哪里"可以不在代码里写死，而是用 **Aseprite** 画好导出，游戏直接读。
Aseprite 本身就是最顺手的像素界面编辑器（图层、参考图、切片都能用），没必要再造一个。

约定（画的时候照这个来）：

- 画布就是界面尺寸 **640×360**；
- **贴图图层**用 `img.` 前缀，名字后半段是元素 id：`img.panel.left`、`img.gear`、`img.info.box`、`img.card.slot.0`；
- **切片**（Slice）用**裸 id** 命名：`panel.left`、`gear`、`info.box`、`card.slot.0` —— 切片的矩形就是元素的绘制区域 + 点击热区；
- 想标注"这块是热区"又不画像素，就把空图层放进 `hotspots` 组，命名 `hit.<id>`（游戏不读这些图层）；
- 于是：**切片管"在哪、点哪里"，`img.` 图层管"长什么样"**，两者可以分开改。

导出（一条命令，图层名和切片一起写进 JSON）：

```bash
tools/ase-export.sh                       # 导出 ui/ 下所有 .aseprite
tools/ase-export.sh ui/battle.aseprite    # 只导一张
# 等价于：
# aseprite -b ui/battle.aseprite --list-layers --list-slices \
#          --sheet ui/battle.png --data ui/battle.json --format json-array
```

游戏侧读法（`core/AseUi`）：

```java
AseUi ui = AseUi.active();             // 读 -Dsalvo.ase=ui/battle.json（或给目录 + -Dsalvo.ase.screen）
int[] r = ui.slice("card.slot.0");     // 热区 {x,y,w,h}；没有这个切片返回 null
int[] tex = ui.texture("gear");        // 贴图矩形：有切片且画了 img.gear 图层才返回
ui.drawTexture(art, "gear");           // 按切片位置贴到画布上（透明处保留底下的画）
```

已经接上贴图的元素：左下梯形面板 `panel.left`、齿轮 `gear`、右下信息框 `info.box`
（`CardLoopScreen.drawAseTexture(art, id)`：有贴图就贴、没有就画代码里的图形）。

不传 `-Dsalvo.ase` 就完全走代码里的默认几何，所以布局文件是可选的增强。
目前接上的是战斗卡槽 `card.slot.0..6`（`CardLoopScreen.cardBarRect` 先查切片、再查 `.uilab`、最后退回常量），
绘制和热区共用这一个函数，挪一下两者一起动。

`ui/battle.json` + `ui/battle.png` 是仓库里附的一份样例（12 个切片，其中 `card.slot.0` 故意挪到棋盘中央），
配 `-Dsalvo.ase=ui/battle.json` 启动就能看到第一张卡换位置。

### 没有 Aseprite 也能画（.uimap → JSON）

Aseprite 的 JSON 没什么神秘的，就是「一张 sheet + 一张元素表」。`tools/UiSheetJson`
把这份结构从 Aseprite 里解耦出来：用本 harness 的 `pixel_shader` / `pixel_art`、或者任何画图工具
出图，再写一份元素清单就行。仓库里的 `ui/battle.uimap` 就是从 Aseprite 的导出结果反推的**完整样例**
（41 个图层 / 20 个切片），两份 JSON 解析出来逐项相同。

清单格式（`#` 和 `//` 都是注释，坐标就是屏幕坐标）：

```
image battle.png            # 可省，默认同名 .png
layer img.gear              # 贴图图层：游戏认 img.<id> 前缀
slice gear 94 6 18 18       # <id> x y w h —— 既是绘制区也是点击热区
center info.box 2 2 2 2     # 可省，九宫格中心
```

转换（纯 JDK，无图像库、无 Aseprite；等价于 `ase-export.sh` 的产物）：

```bash
mvn -q -o exec:java -Dexec.mainClass=com.hysx.salvosequence.tools.UiSheetJson \
    -Dexec.args="ui/battle.uimap"
# 或 -Dstudio.map=ui/battle.uimap   -Dstudio.png=/ -Dstudio.out= 可覆盖
```

要点：生成的 JSON 默认落在 sheet 图旁边，因为游戏是**按 JSON 所在目录**去找 `meta.image` 的；
越界、重名、拼错指令都会直接报错退出，不会写出一个游戏读不了的布局。

> 没有 Aseprite、只想手摆热区的话，`D:\projects\uilab` 里还有个小工具能存 `.uilab`，
> 游戏侧的 `core/UiLayout` 也照读——两条路并存，Aseprite 优先。

## 工具（tools/）

- `CardLoopApp`：直达组舰者玩法循环的可玩入口
- `CanvasAudit`：逐界面导设计稿 + 整屏块状分析（`-Daudit.outdir=...`）
- `CardLoopAudit`：固定种子把一局从选舰驱动到编入，导 11 张核对稿
- `FlickerAudit`：文本闪烁逐帧导图
- `SpriteExport` / `OutlineLab` / `ModuleLab` / `ShipLab` / `AudioSelfTest` / `AudioProbe`：素材导出与自检
- `ase-export.sh`：把 `ui/*.aseprite` 导成界面数据（见上节）
- `UiSheetJson`：把 `.uimap` 清单 + sheet PNG 写成同一份界面数据（不需要 Aseprite）
- `AseUiProbe`：跳进战斗界面导设计像素图，核对美术稿到底有没有被用上

## 彩蛋：人物立绘（F12）

任何界面按 `F12` 都能开关人物立绘（`sprites/portrait.png`，缩到 256 设计像素高、
站在左边、脚底对齐 338 行）。它画在所有界面内容之上、过渡遮罩之下，不占布局、
不改玩法；开关是会话级的，重开游戏恢复隐藏。

- 自测/截图：加 `-Dsalvosequence.portrait=true` 一进游戏就显示。
- 原来的诊断双导出（设计缓冲 + GL 屏幕帧）挪到 `F10`。
- 实现在 `ui/ScreenManager`（懒加载 + 全局热键），`screen/experiment/PortraitOverlay` 只管贴图。
