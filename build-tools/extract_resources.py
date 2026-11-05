#!/usr/bin/env python3
"""把依赖 jar 里的非 class 资源平铺到 assets/。

为什么需要：APK 里没有 jar，libGDX 的 AndroidFiles 把 Classpath 类型的文件映射到
assets/ 根目录下，所以 gdx-vfx 的 `gdxvfx/shaders/*`、gdx 自带的默认字体等必须在
assets 里有一份同样的相对路径。

用法：python build-tools/extract_resources.py <assets 目录> <jar>...
"""
import os
import sys
import zipfile

SKIP_SUFFIX = ('.class', '.gwt.xml', '.rl', '.java', '.kt')
SKIP_PREFIX = ('META-INF/',)


def main(argv):
    if len(argv) < 3:
        print(__doc__)
        return 2
    dst = argv[1]
    count = 0
    for jar in argv[2:]:
        with zipfile.ZipFile(jar) as z:
            for name in z.namelist():
                if name.endswith('/') or name.startswith(SKIP_PREFIX):
                    continue
                if name.endswith(SKIP_SUFFIX):
                    continue
                target = os.path.join(dst, name)
                os.makedirs(os.path.dirname(target), exist_ok=True)
                with open(target, 'wb') as out:
                    out.write(z.read(name))
                count += 1
    print('[res] 复制 %d 个资源文件到 %s' % (count, dst))
    return 0


if __name__ == '__main__':
    sys.exit(main(sys.argv))
