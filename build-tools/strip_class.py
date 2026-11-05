#!/usr/bin/env python3
"""从 jar 里剔除指定类（连同内部类），输出一个新 jar，给 d8 用。

用法：python build-tools/strip_class.py <源 jar> <目标 jar> <类名> [类名...]
类名用全限定名，不带 .class，例如 com.badlogic.gdx.backends.android.keyboardheight.AndroidXKeyboardHeightProvider
"""
import sys
import zipfile


def main(argv):
    if len(argv) < 4:
        print(__doc__)
        return 2
    src, dst = argv[1], argv[2]
    prefixes = tuple(name.replace('.', '/') + '.class' for name in argv[3:])
    dropped = []
    with zipfile.ZipFile(src) as zin, zipfile.ZipFile(dst, 'w', zipfile.ZIP_DEFLATED) as zout:
        for item in zin.infolist():
            name = item.filename
            if name.startswith(prefixes) or any(
                    name.startswith(p[:-6] + '$') for p in prefixes):
                dropped.append(name)
                continue
            zout.writestr(item, zin.read(name))
    print('[strip] %s → %s，剔除 %d 个类' % (src, dst, len(dropped)))
    for name in dropped:
        print('        -', name)
    return 0


if __name__ == '__main__':
    sys.exit(main(sys.argv))
