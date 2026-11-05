package com.hysx.salvosequence.tools;

import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import javax.sound.midi.MetaMessage;
import javax.sound.midi.MidiEvent;
import javax.sound.midi.MidiMessage;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.Sequence;
import javax.sound.midi.ShortMessage;
import javax.sound.midi.Track;

/**
 * 把一堆 .mid 逐个解剖成清单（纯 JDK，不依赖 JavaFX，所以能对着分发包跑）。
 *
 * <p>存在的理由：判断一批外部 MIDI 能不能用，光看文件名不够。这个工具把文件
 * <b>自己内嵌的</b>版权/作者/来源元数据、通道配置、音量、音色号全部读出来，
 * 再把文件名当作第二路证据（很多收藏文件的名字本身就点明了来源作品）。</p>
 *
 * <p>输出两张表到 {@code target/reports/}：{@code midi-catalog.md}（人看的，按
 * 授权风险分组）与 {@code midi-catalog.tsv}（机器处理用，一首一行）。</p>
 *
 * <p>用法：{@code java -cp target\classes com.hysx.salvosequence.tools.MidiCatalog <目录> [输出目录]}</p>
 */
public final class MidiCatalog {

    /** MIDI 元事件类型：文本 / 版权 / 曲名 / 乐器名 / 歌词 / 标记。 */
    private static final int META_TEXT = 0x01;
    private static final int META_COPYRIGHT = 0x02;
    private static final int META_TRACK_NAME = 0x03;
    private static final int META_INSTRUMENT = 0x04;
    private static final int META_LYRIC = 0x05;
    private static final int META_MARKER = 0x06;

    /** 文件名里出现这些词，几乎可以断定是某个商业作品的衍生品。 */
    private static final String[] KNOWN_WORKS = {
        "aerosmith", "billy", "joel", "final fantasy", "ff", "doom", "duke", "mortal",
        "railgun", "senbonzakura", "scat", "testament", "nirvana", "metallica", "queen",
        "beatles", "eagles", "gun", "zeppelin", "acdc", "bon jovi", "madonna", "prince",
        "toto", "journey", "foreigner", "chicago", "styx", "kansas", "boston", "yes",
        "pink", "floyd", "genesis", "rush", "elo", "abba", "bee", "gees", "carpen",
        "disney", "ghibli", "pokemon", "zelda", "mario", "sonic", "chrono", "xeno",
        "touhou", "vocaloid", "hatsune", "anime", "kirby", "metroid", "castlevania",
        "street fighter", "tekken", "megaman", "metal gear", "silent hill", "resident",
        "yukari", "初音", "東方", "鸟之诗", "鳥之詩", "甩葱", "炉心", "深海少女",
        "千本桜", "senbon", "kanon", "air", "clannad", "naruto", "bleach", "evangelion",
        "gundam", "macross", "cowboy", "bebop", "slam dunk", "dragon ball", "inuyasha",
        "sailor", "cardcaptor", "ranma", "lum", "touch", "saint seiya",
    };

    /** 文件名里出现这些词，通常是"来源不明/自娱自乐"的个人作品，风险低但要留痕。 */
    private static final String[] LOW_RISK_HINTS = {
        "demo", "test", "jam", "practice", "sample", "untitled", "new", "my", "temp",
    };

    private static final class Entry {
        String name = "";
        long bytes;
        String format = "-";
        int tracks;
        int resolution;
        int notes;
        double seconds;
        String copyright = "";
        String title = "";
        String trackNames = "";
        String otherText = "";
        boolean lyrics;
        boolean karaokeText;
        int[] programs = new int[16];
        int[] volumes = new int[16];
        int volumeChannels;
        int maxVolume = -1;
        String error = "";
        String risk = "";
        String evidence = "";
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.out.println("用法: MidiCatalog <目录> [输出目录]");
            System.exit(2);
            return;
        }
        File dir = new File(args[0]);
        if (!dir.isDirectory()) {
            System.out.println("[catalog] 不是目录: " + dir);
            System.exit(2);
            return;
        }
        File outDir = new File(args.length >= 2 ? args[1] : "target/reports");
        Files.createDirectories(outDir.toPath());

        File[] files = dir.listFiles((d, n) -> {
            String lower = n.toLowerCase(Locale.ROOT);
            return lower.endsWith(".mid") || lower.endsWith(".midi") || lower.endsWith(".rmi");
        });
        if (files == null || files.length == 0) {
            System.out.println("[catalog] 目录里没有 .mid/.midi/.rmi: " + dir);
            System.exit(2);
            return;
        }
        List<File> sorted = new ArrayList<>(List.of(files));
        sorted.sort(Comparator.comparing(f -> f.getName().toLowerCase(Locale.ROOT)));

        List<Entry> entries = new ArrayList<>();
        for (File f : sorted) {
            entries.add(inspect(f));
        }

        Path md = outDir.toPath().resolve("midi-catalog.md");
        Path tsv = outDir.toPath().resolve("midi-catalog.tsv");
        writeMarkdown(md, dir, entries);
        writeTsv(tsv, entries);

        long bad = entries.stream().filter(e -> !e.error.isEmpty()).count();
        long withCopyright = entries.stream().filter(e -> !e.copyright.isBlank()).count();
        System.out.println("[catalog] 扫描 " + entries.size() + " 个文件，读取失败 " + bad
                + " 个，带内嵌版权声明 " + withCopyright + " 个");
        System.out.println("[catalog] 写出 " + md.toAbsolutePath());
        System.out.println("[catalog] 写出 " + tsv.toAbsolutePath());
        if (bad > 0) {
            System.out.println("[catalog] 读取失败清单:");
            for (Entry e : entries) {
                if (!e.error.isEmpty()) {
                    System.out.println("  " + e.name + " -> " + e.error);
                }
            }
        }
    }

    // ------------------------------------------------------------------ 解剖

    private static Entry inspect(File file) {
        Entry e = new Entry();
        e.name = file.getName();
        e.bytes = file.length();
        try {
            Sequence seq = MidiSystem.getSequence(file);
            e.tracks = seq.getTracks().length;
            e.resolution = seq.getResolution();
            e.seconds = seq.getMicrosecondLength() / 1_000_000.0;
            e.format = MidiSystem.getMidiFileFormat(file).getType() + "";
            if (e.tracks > 1) {
                e.format = "1(" + e.tracks + "轨)";
            }
            for (Track t : seq.getTracks()) {
                readTrack(t, e);
            }
        } catch (Exception ex) {
            e.error = ex.getClass().getSimpleName() + ": " + ex.getMessage();
        }
        classify(e);
        return e;
    }

    private static void readTrack(Track track, Entry e) {
        StringBuilder names = new StringBuilder();
        StringBuilder others = new StringBuilder();
        for (int i = 0; i < track.size(); i++) {
            MidiEvent me = track.get(i);
            MidiMessage msg = me.getMessage();
            if (msg instanceof ShortMessage sm) {
                int ch = sm.getChannel();
                if (sm.getCommand() == ShortMessage.NOTE_ON && sm.getData2() > 0) {
                    e.notes++;
                } else if (sm.getCommand() == ShortMessage.PROGRAM_CHANGE && ch < 16
                        && sm.getData1() > 0 && e.programs[ch] == 0) {
                    e.programs[ch] = sm.getData1();
                } else if (sm.getCommand() == ShortMessage.CONTROL_CHANGE && sm.getData1() == 7
                        && ch < 16) {
                    e.volumes[ch] = sm.getData2();
                    if (e.maxVolume < 0) {
                        e.maxVolume = 0;
                    }
                    e.maxVolume = Math.max(e.maxVolume, sm.getData2());
                }
            } else if (msg instanceof MetaMessage mm) {
                String text = decode(mm.getData());
                switch (mm.getType()) {
                    case META_COPYRIGHT -> {
                        if (e.copyright.isBlank()) {
                            e.copyright = text;
                        } else if (!e.copyright.contains(text)) {
                            e.copyright = e.copyright + " | " + text;
                        }
                    }
                    case META_TRACK_NAME -> {
                        if (e.tracks > 1 && !text.isBlank() && names.length() < 200) {
                            if (names.length() > 0) {
                                names.append(" / ");
                            }
                            names.append(text);
                        }
                    }
                    case META_TEXT -> {
                        if (text.toUpperCase(Locale.ROOT).contains("KARAOKE")
                                || text.startsWith("@KMIDI") || text.startsWith("@T")) {
                            e.karaokeText = true;
                        }
                        if (e.title.isBlank() && looksLikeTitle(text)) {
                            e.title = text;
                        }
                        if (others.length() < 300) {
                            if (others.length() > 0) {
                                others.append(" / ");
                            }
                            others.append(text);
                        }
                    }
                    case META_LYRIC -> e.lyrics = true;
                    case META_INSTRUMENT -> {
                        if (others.length() < 300) {
                            if (others.length() > 0) {
                                others.append(" / ");
                            }
                            others.append("乐器:").append(text);
                        }
                    }
                    case META_MARKER -> {
                        if (others.length() < 300) {
                            if (others.length() > 0) {
                                others.append(" / ");
                            }
                            others.append("标记:").append(text);
                        }
                    }
                    default -> { }
                }
            }
        }
        e.trackNames = names.toString();
        e.otherText = others.toString();
    }

    private static boolean looksLikeTitle(String text) {
        String t = text.trim();
        return !t.isEmpty() && t.length() < 60
                && !t.toUpperCase(Locale.ROOT).contains("KARAOKE")
                && !t.startsWith("@");
    }

    /** MIDI 文本元事件没有声明编码，实测以 ASCII/Latin-1 为主，中文多为 GBK 残字节。 */
    private static String decode(byte[] data) {
        if (data == null || data.length == 0) {
            return "";
        }
        String s = new String(data, StandardCharsets.ISO_8859_1);
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= 0x20 && c != 0x7F) {
                sb.append(c);
            }
        }
        return sb.toString().trim();
    }

    // ------------------------------------------------------------ 风险归类

    /**
     * 把"最硬"的证据放在前面：内嵌版权声明 &gt; 卡拉OK标记 &gt; 文件名点名作品
     * &gt; 有歌词 &gt; 什么都没写。
     */
    private static void classify(Entry e) {
        if (!e.error.isEmpty()) {
            e.risk = "读不出";
            e.evidence = e.error;
            return;
        }
        String lower = e.name.toLowerCase(Locale.ROOT);
        List<String> commercial = new ArrayList<>();
        if (!e.copyright.isBlank()) {
            commercial.add("内嵌声明");
        }
        if (e.karaokeText) {
            commercial.add("KARAOKE");
        }
        for (String w : KNOWN_WORKS) {
            if (lower.contains(w.toLowerCase(Locale.ROOT))) {
                commercial.add("文件名:" + w);
            }
        }
        if (e.lyrics) {
            commercial.add("带歌词");
        }
        if (!commercial.isEmpty()) {
            e.risk = "商业作品";
            e.evidence = String.join("; ", commercial) + (e.copyright.isBlank() ? "" : " " + e.copyright);
            return;
        }
        for (String w : LOW_RISK_HINTS) {
            if (lower.contains(w)) {
                e.risk = "来源不明";
                e.evidence = "文件名含常见试用词，需自己确认";
                return;
            }
        }
        e.risk = "来源不明";
        e.evidence = e.copyright.isBlank() ? "无任何元数据佐证" : e.copyright;
    }

    // ------------------------------------------------------------------ 输出

    private static void writeMarkdown(Path path, File dir, List<Entry> entries) throws IOException {
        try (PrintWriter w = new PrintWriter(new OutputStreamWriter(
                Files.newOutputStream(path), StandardCharsets.UTF_8))) {
            w.println("# MIDI 素材清单");
            w.println();
            w.println("扫描目录：`" + dir.getAbsolutePath() + "`");
            w.println();
            long total = entries.size();
            long bad = entries.stream().filter(e -> !e.error.isEmpty()).count();
            long withCr = entries.stream().filter(e -> !e.copyright.isBlank()).count();
            long karaoke = entries.stream().filter(e -> e.karaokeText).count();
            long lyrics = entries.stream().filter(e -> e.lyrics).count();
            long bytes = entries.stream().mapToLong(e -> e.bytes).sum();
            w.println("- 文件数：**" + total + "**（合计 " + (bytes / 1024 / 1024) + " MB）");
            w.println("- 内嵌版权声明：**" + withCr + "**");
            w.println("- 带 KARAOKE 标记：" + karaoke);
            w.println("- 带歌词事件：" + lyrics);
            w.println("- 读取失败：" + bad);
            w.println();
            w.println("「内嵌声明」= 文件里写着版权方；这类基本可以断定是商业作品的衍生/转录。");
            w.println("「来源不明」= 文件里没有任何授权线索，不代表可商用。");
            w.println();

            List<String> groups = List.of("商业作品", "来源不明", "读不出");
            for (String group : groups) {
                List<Entry> subset = entries.stream().filter(e -> e.risk.equals(group)).toList();
                if (subset.isEmpty()) {
                    continue;
                }
                w.println("## " + group + "（" + subset.size() + " 个）");
                w.println();
                w.println("| 文件 | KB | 格式 | 轨 | 音符 | 时长 | 音色 | 内嵌证据 |");
                w.println("| --- | ---: | --- | ---: | ---: | ---: | --- | --- |");
                for (Entry e : subset) {
                    w.println("| `" + escape(e.name) + "` | " + (e.bytes / 1024)
                            + " | " + e.format
                            + " | " + e.tracks
                            + " | " + e.notes
                            + " | " + String.format(Locale.ROOT, "%.0fs", e.seconds)
                            + " | " + programList(e)
                            + " | " + escape(shorten(e.evidence, 90)) + " |");
                }
                w.println();
            }

            w.println("## 全量明细");
            w.println();
            w.println("| 文件 | 判定 | 曲名 | 轨名 | 其它文本 | 通道音量 | 峰值CC7 |");
            w.println("| --- | --- | --- | --- | --- | --- | ---: |");
            for (Entry e : entries) {
                w.println("| `" + escape(e.name) + "` | " + e.risk
                        + " | " + escape(e.title)
                        + " | " + escape(shorten(e.trackNames, 60))
                        + " | " + escape(shorten(e.otherText, 70))
                        + " | " + volumeList(e)
                        + " | " + (e.maxVolume < 0 ? "-" : e.maxVolume + "") + " |");
            }
            w.println();
        }
    }

    private static String programList(Entry e) {
        StringBuilder sb = new StringBuilder();
        for (int ch = 0; ch < 16; ch++) {
            if (e.programs[ch] > 0) {
                if (sb.length() > 0) {
                    sb.append(' ');
                }
                sb.append('c').append(ch).append('=').append(e.programs[ch]);
            }
        }
        return sb.length() == 0 ? "-" : sb.toString();
    }

    private static String volumeList(Entry e) {
        StringBuilder sb = new StringBuilder();
        for (int ch = 0; ch < 16; ch++) {
            if (e.volumes[ch] > 0) {
                if (sb.length() > 0) {
                    sb.append(' ');
                }
                sb.append('c').append(ch).append('=').append(e.volumes[ch]);
            }
        }
        return sb.length() == 0 ? "-" : sb.toString();
    }

    private static void writeTsv(Path path, List<Entry> entries) throws IOException {
        try (PrintWriter w = new PrintWriter(new OutputStreamWriter(
                Files.newOutputStream(path), StandardCharsets.UTF_8))) {
            w.println("file\tbytes\tformat\ttracks\tresolution\tnotes\tseconds\t"
                    + "copyright\ttitle\ttrackNames\totherText\tlyrics\tkaraoke\trisk\tevidence");
            for (Entry e : entries) {
                w.println(String.join("\t",
                        e.name, e.bytes + "", e.format, e.tracks + "", e.resolution + "",
                        e.notes + "", String.format(Locale.ROOT, "%.1f", e.seconds),
                        flat(e.copyright), flat(e.title), flat(e.trackNames), flat(e.otherText),
                        e.lyrics ? "1" : "0", e.karaokeText ? "1" : "0",
                        e.risk, flat(e.evidence)));
            }
        }
    }

    private static String flat(String s) {
        return s == null ? "" : s.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ');
    }

    private static String escape(String s) {
        return flat(s).replace("|", "\\|");
    }

    private static String shorten(String s, int max) {
        String t = flat(s);
        return t.length() <= max ? t : t.substring(0, max - 1) + "~";
    }
}
