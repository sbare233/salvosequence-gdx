# -*- coding: utf-8 -*-
"""生成 package-jar.bat：内容 UTF-8，落盘为 GBK + CRLF，中文 Windows 的 cmd 才能正确解析。"""
import pathlib

BAT = r'''@echo off
setlocal
cd /d "%~dp0"
title SalvoSequence 打包

echo [1/3] Maven 打包（fat jar，跳过测试）...
call mvn -q clean package -DskipTests
if errorlevel 1 (
    echo.
    echo [失败] Maven 构建出错，游戏没有打出来。
    pause
    exit /b 1
)
set "JAR=target\SalvoSequenceGdx.jar"
if not exist "%JAR%" (
    echo [失败] 未找到 %JAR%
    pause
    exit /b 1
)

echo [2/3] 组装发布目录 dist\SalvoSequenceGdx\ ...
if exist "dist\SalvoSequenceGdx" rmdir /s /q "dist\SalvoSequenceGdx"
mkdir "dist\SalvoSequenceGdx" 2>nul
copy /y "%JAR%" "dist\SalvoSequenceGdx\" >nul
copy /y "README.md" "dist\SalvoSequenceGdx\" >nul

>  "dist\SalvoSequenceGdx\启动游戏.bat" echo @echo off
>> "dist\SalvoSequenceGdx\启动游戏.bat" echo rem 齐射序列 Salvo Sequence（libGDX 版）
>> "dist\SalvoSequenceGdx\启动游戏.bat" echo start "SalvoSequence" javaw -jar "%%~dp0SalvoSequenceGdx.jar"

for %%F in ("%JAR%") do set "SIZE=%%~zF"
echo [3/3] 完成。
echo   jar : %JAR%  (%SIZE% 字节)
echo   发布: dist\SalvoSequenceGdx\  (含 启动游戏.bat，整个目录拷走即可玩)
echo   运行要求: JRE 21+；java -jar SalvoSequenceGdx.jar 也可以直接启动
pause
'''

pathlib.Path("package-jar.bat").write_bytes(
    BAT.replace("\n", "\r\n").encode("gbk"))
print("wrote package-jar.bat", len(BAT))
