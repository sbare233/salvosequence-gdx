#!/usr/bin/env python3
"""把 classes.dex 和 lib/<abi>/*.so 塞进 aapt2 产出的 APK 里。

这台机器上没有 zip 命令，改用标准库 zipfile：dex 用 deflate 压缩，.so 用 store
（不压缩）—— zipalign -p 只对未压缩的 .so 做页对齐，Android 才能直接 mmap。
"""
import os
import sys
import zipfile


def main() -> int:
    apk, dex_dir, lib_dir = sys.argv[1], sys.argv[2], sys.argv[3]

    dex = os.path.join(dex_dir, "classes.dex")
    if not os.path.isfile(dex):
        print(f"缺少 {dex}")
        return 1

    with zipfile.ZipFile(apk, "a", zipfile.ZIP_DEFLATED) as z:
        z.write(dex, "classes.dex")

    if os.path.isdir(lib_dir):
        for root, _dirs, files in os.walk(lib_dir):
            for name in files:
                path = os.path.join(root, name)
                rel = "lib/" + os.path.relpath(path, lib_dir).replace(os.sep, "/")
                with zipfile.ZipFile(apk, "a", zipfile.ZIP_STORED) as z:
                    z.write(path, rel)

    return 0


if __name__ == "__main__":
    sys.exit(main())
