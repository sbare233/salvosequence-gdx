"""用本机的 MOSS-Image（ComfyUI）批量跑像素素材。

前提：ComfyUI 已经在这个端口跑起来（`python_embeded\\python.exe -s ComfyUI\\main.py --port 8188 --highvram`），
且 `moss-image-sft-qwen35-int8-convrot-fixed.safetensors` 已经用 `fix_te_prefix.py` 补过 `model.` 前缀。

用法：
    python build-tools/moss/batch.py                 # 跑全部任务（已存在的跳过）
    python build-tools/moss/batch.py nebula-field    # 只跑名字里带这个字串的

产物落在 `gen/moss/<名字>.png`，1024 宽、8 步 turbo 采样，单张约 70 秒（RTX 4060 Ti 8G）。
"""

from __future__ import annotations

import json
import shutil
import sys
import time
import urllib.request
from pathlib import Path

HOST = "http://127.0.0.1:8188"
OUT = Path(__file__).resolve().parents[2] / "gen" / "moss"
COMFY_OUTPUT = Path("D:/ComfyUI_windows_portable/ComfyUI/output")

UNET = "moss_image_sft_turbo_int8_convrot.safetensors"
CLIP = "moss-image-sft-qwen35-int8-convrot-fixed.safetensors"
VAE = "moss-image-sft-vae.safetensors"

STYLE = "pixel art, 16-bit retro game art, crisp square pixels, limited palette, no anti-aliasing, no text, no watermark, no UI"

JOBS: list[dict] = [
    # ---- 太空背景（16:9，进 backdrops-src） ----
    dict(
        name="bg-nebula-teal",
        w=1024,
        h=576,
        seed=1101,
        prompt=f"{STYLE}, deep space nebula, teal and magenta gas clouds, dense star field, dark void background, no planets",
    ),
    dict(
        name="bg-ringed-giant",
        w=1024,
        h=576,
        seed=1102,
        prompt=f"{STYLE}, giant ringed gas planet seen from space, banded orange and cream atmosphere, thin ice rings, faint stars, dark background",
    ),
    dict(
        name="bg-binary-suns",
        w=1024,
        h=576,
        seed=1103,
        prompt=f"{STYLE}, binary star system, one blue hot star and one orange dwarf, lens flare, sparse stars, dark space background",
    ),
    dict(
        name="bg-asteroid-belt",
        w=1024,
        h=576,
        seed=1104,
        prompt=f"{STYLE}, drifting asteroid belt, grey rocks with rim light, faint purple nebula behind, dark space background",
    ),
    dict(
        name="bg-station-silhouette",
        w=1024,
        h=576,
        seed=1105,
        prompt=f"{STYLE}, distant space station silhouette with tiny lit windows, hanging against a green nebula, dark space background, small scale",
    ),
    dict(
        name="bg-red-giant",
        w=1024,
        h=576,
        seed=1106,
        prompt=f"{STYLE}, huge red giant star low on the frame, dusty accretion disk, deep black space, sparse stars",
    ),
    # ---- 战舰（纯侧视，机头朝右；1024x768 方图，之后再降到 18 像素/格） ----
    dict(
        name="ship-main-gun",
        w=1024,
        h=768,
        seed=2101,
        prompt=f"{STYLE}, side view of a single spaceship, orthographic profile, horizontal hull facing right, full silhouette visible, heavy main gun turret on the spine, armoured grey and blue plates, small orange lights, pure black background",
    ),
    dict(
        name="ship-broadside",
        w=1024,
        h=768,
        seed=2102,
        prompt=f"{STYLE}, side view of a single spaceship, orthographic profile, horizontal hull facing right, long row of broadside gun barrels along the flank, boxy grey and teal hull, pure black background",
    ),
    dict(
        name="ship-armor-belt",
        w=1024,
        h=768,
        seed=2103,
        prompt=f"{STYLE}, side view of a single spaceship, orthographic profile, horizontal hull facing right, thick armoured belt plating, heavy slab hull, cool grey with rust accents, pure black background",
    ),
    dict(
        name="ship-carrier",
        w=1024,
        h=768,
        seed=2104,
        prompt=f"{STYLE}, side view of a single spaceship, orthographic profile, horizontal hull facing right, flat carrier deck with tiny hangar bays, long rectangular grey and white hull, pure black background",
    ),
    dict(
        name="ship-dreadnought",
        w=1024,
        h=768,
        seed=2105,
        prompt=f"{STYLE}, side view of a single spaceship, orthographic profile, horizontal hull facing right, huge dreadnought battleship, layered gun decks, forward prow spear, dark grey with cyan lights, pure black background",
    ),
    dict(
        name="ship-corvette",
        w=1024,
        h=768,
        seed=2106,
        prompt=f"{STYLE}, side view of a single spaceship, orthographic profile, horizontal hull facing right, small fast corvette, sleek narrow hull, twin engines glowing blue, light grey plates, pure black background",
    ),
]


def build_prompt(job: dict) -> dict:
    text = job["prompt"]
    return {
        "prompt": {
            "1": {"class_type": "UNETLoader", "inputs": {"unet_name": UNET, "weight_dtype": "default"}},
            "2": {"class_type": "CLIPLoader", "inputs": {"clip_name": CLIP, "type": "moss_image", "device": "default"}},
            "3": {"class_type": "VAELoader", "inputs": {"vae_name": VAE}},
            "4": {"class_type": "EmptyFlux2LatentImage", "inputs": {"width": job["w"], "height": job["h"], "batch_size": 1}},
            "5": {"class_type": "CLIPTextEncode", "inputs": {"clip": ["2", 0], "text": text}},
            "6": {
                "class_type": "KSampler",
                "inputs": {
                    "model": ["1", 0],
                    "positive": ["5", 0],
                    "negative": ["5", 0],
                    "latent_image": ["4", 0],
                    "seed": job["seed"],
                    "steps": 8,
                    "cfg": 1.0,
                    "sampler_name": "euler",
                    "scheduler": "simple",
                    "denoise": 1.0,
                },
            },
            "7": {"class_type": "VAEDecode", "inputs": {"samples": ["6", 0], "vae": ["3", 0]}},
            "8": {"class_type": "SaveImage", "inputs": {"images": ["7", 0], "filename_prefix": f"salvo/{job['name']}"}},
        }
    }


def post(path: str, payload: dict) -> dict:
    data = json.dumps(payload).encode("utf-8")
    req = urllib.request.Request(HOST + path, data=data, headers={"Content-Type": "application/json"})
    with urllib.request.urlopen(req, timeout=30) as r:
        return json.load(r)


def get(path: str) -> dict:
    with urllib.request.urlopen(HOST + path, timeout=30) as r:
        return json.load(r)


def wait(prompt_id: str, timeout: float = 900.0) -> dict:
    deadline = time.time() + timeout
    while time.time() < deadline:
        hist = get("/history")
        if prompt_id in hist:
            entry = hist[prompt_id]
            if entry["status"]["completed"]:
                return entry
            if entry["status"]["status_str"] == "error":
                raise RuntimeError(f"生成失败：{entry['status']['messages'][-1]}")
        time.sleep(3)
    raise TimeoutError(prompt_id)


def run(job: dict) -> None:
    dst = OUT / f"{job['name']}.png"
    if dst.exists():
        print(f"跳过（已存在）{dst.name}")
        return
    started = time.time()
    prompt_id = post("/prompt", build_prompt(job))["prompt_id"]
    entry = wait(prompt_id)
    images = [img for node in entry["outputs"].values() for img in node.get("images", [])]
    if not images:
        raise RuntimeError(f"{job['name']} 没有输出图像")
    src = COMFY_OUTPUT / images[0]["subfolder"] / images[0]["filename"]
    OUT.mkdir(parents=True, exist_ok=True)
    shutil.copy2(src, dst)
    print(f"完成 {dst.name}  {time.time() - started:.0f}s  {job['w']}x{job['h']}")


def main() -> None:
    picked = [j for j in JOBS if len(sys.argv) < 2 or any(a in j["name"] for a in sys.argv[1:])]
    print(f"共 {len(picked)} 个任务，输出到 {OUT}")
    for i, job in enumerate(picked, 1):
        print(f"[{i}/{len(picked)}] {job['name']}", flush=True)
        run(job)


if __name__ == "__main__":
    main()
