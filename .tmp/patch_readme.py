from pathlib import Path
p = Path('README.md')
s = p.read_text(encoding='utf-8')

anchor = '辉光观感在 `ui/PixelGameView` 顶部的 `BLOOM_THRESHOLD / BLOOM_BLUR_PASSES / BLOOM_BLUR_AMOUNT / BLOOM_INTENSITY / BLOOM_BASE_INTENSITY` 常量里调。\n'
add = anchor + '''
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
'''
assert anchor in s
s = s.replace(anchor, add, 1)
p.write_text(s, encoding='utf-8')
print('README updated')
