"""给 HuggingFace 格式的 MOSS 文本编码器补上 `model.` 前缀。

ComfyUI 的 sd.py 靠 `model.language_model.layers.0.linear_attn.A_log` 这类键名认出
Qwen3.5-9B；而 HF 直接导出的 safetensors 用的是 `language_model.*` / `visual.*`，
认不出来就会退化成 SD1.5 的 CLIP（77 token、768 维），报
`mat1 and mat2 shapes cannot be multiplied (77x768 and 4096x3072)`。

只重写文件头（张量数据原样照搬，偏移不变），所以不需要真的解包权重。
"""

from __future__ import annotations

import json
import shutil
import struct
import sys
from pathlib import Path


def fix(src: Path, dst: Path) -> None:
    with src.open("rb") as f:
        raw_len = f.read(8)
        header_len = struct.unpack("<Q", raw_len)[0]
        header = json.loads(f.read(header_len))

    renamed = {}
    for key, value in header.items():
        if key == "__metadata__":
            renamed[key] = value
            continue
        if key.startswith("model."):
            renamed[key] = value
        elif key.startswith("language_model.") or key.startswith("visual."):
            # 加前缀，保持数据偏移不变（丢掉旧名，免得同一份权重被认成两份）
            renamed["model." + key] = value
        else:
            renamed[key] = value

    blob = json.dumps(renamed, separators=(",", ":")).encode("utf-8")
    blob += b" " * ((8 - len(blob) % 8) % 8)  # safetensors 要求头部 8 字节对齐

    with src.open("rb") as fin, dst.open("wb") as fout:
        fin.seek(8 + header_len)
        fout.write(struct.pack("<Q", len(blob)))
        fout.write(blob)
        shutil.copyfileobj(fin, fout, length=64 << 20)

    print(f"{src.name} -> {dst.name}: {len(header)} keys, header {len(blob)} bytes")


if __name__ == "__main__":
    if len(sys.argv) != 3:
        raise SystemExit("usage: fix_te_prefix.py <src.safetensors> <dst.safetensors>")
    fix(Path(sys.argv[1]), Path(sys.argv[2]))
