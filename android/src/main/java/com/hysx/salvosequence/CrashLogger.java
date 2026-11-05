package com.hysx.salvosequence;

import android.content.Context;
import android.os.Build;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;

/**
 * 崩溃落盘 + logcat。
 *
 * <p>手机上没有控制台，闪退时只能靠 logcat；但普通用户不会 adb。这里把未捕获异常的
 * 完整栈写进 {@code Android/data/<包名>/files/crash.txt}，连上电脑就能直接取走。</p>
 */
final class CrashLogger {

    private static final String TAG = "SalvoCrash";

    private CrashLogger() {
    }

    static void install(final Context context) {
        final Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
            try {
                record(context, thread, error);
            } catch (Throwable ignored) {
                // 落盘失败也不能吞掉原异常
            }
            if (previous != null) {
                previous.uncaughtException(thread, error);
            }
        });
    }

    /** 记一次非致命错误（用来确认某段初始化走没走到）。 */
    static void note(Context context, String message, Throwable error) {
        try {
            record(context, Thread.currentThread(), error == null
                    ? new RuntimeException(message)
                    : new RuntimeException(message, error));
        } catch (Throwable ignored) {
            // 诊断路径本身不能影响主流程
        }
    }

    private static void record(Context context, Thread thread, Throwable error) {
        StringWriter buffer = new StringWriter();
        PrintWriter out = new PrintWriter(buffer);
        out.println("build=" + (Build.MANUFACTURER + " " + Build.MODEL + " / API " + Build.VERSION.SDK_INT));
        out.println("thread=" + (thread == null ? "?" : thread.getName()));
        error.printStackTrace(out);
        out.flush();
        String text = buffer.toString();

        Log.e(TAG, text);

        File dir = context.getExternalFilesDir(null);
        if (dir == null) {
            dir = context.getFilesDir();
        }
        if (dir != null) {
            File file = new File(dir, "crash.txt");
            try (FileOutputStream stream = new FileOutputStream(file, true)) {
                stream.write((text + "\n----\n").getBytes(StandardCharsets.UTF_8));
            } catch (Throwable ignored) {
                // 落盘失败时 logcat 里仍有完整栈
            }
        }
        copyToDownloads(context, text);
    }

    /**
     * 再往「下载」里放一份，用户不用连电脑也能用文件管理器取走。
     *
     * <p>Android 10 起不能直写公共目录，走 MediaStore；同名文件已存在时覆盖。</p>
     */
    private static void copyToDownloads(Context context, String text) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return;
        }
        try {
            android.net.Uri collection = android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI;
            String name = "SalvoCrash.txt";
            android.content.ContentValues values = new android.content.ContentValues();
            values.put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, name);
            values.put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "text/plain");
            values.put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH,
                    android.os.Environment.DIRECTORY_DOWNLOADS);
            android.net.Uri target = context.getContentResolver().insert(collection, values);
            if (target == null) {
                return;
            }
            try (java.io.OutputStream out = context.getContentResolver().openOutputStream(target, "wt")) {
                if (out != null) {
                    out.write(text.getBytes(StandardCharsets.UTF_8));
                }
            }
        } catch (Throwable ignored) {
            // 只是方便用户取日志，失败无所谓
        }
    }
}
