// 来源：gleba-libgdx-mvp（蓝色大肥鱼）——Gleba 星球生成核心，包名归入本项目。
package com.hysx.salvosequence.gleba.gen;

/** 生成结果的扁平数组存储（无 GL 依赖，可在后台线程生成）。 */
public final class PlanetData {
    public final SphereGrid grid;
    public final int n;

    public final int[] plate;             // 所属板块 id
    public final boolean[] continental;   // 大陆地壳 / 大洋地壳
    public final float[] elevation;       // 高程（米）
    public final float[] water;           // 汇流量（河流用）
    public final boolean[] land;
    public final float[] tempC;           // 年均温（°C）
    public final float[] rainMm;          // 年降水（mm）
    public final float[] continentality;  // 0=海洋性, 1=极端大陆性
    public final byte[] koppen;           // Köppen 分类索引
    public final float[] snow;            // 雪/冰覆盖 0..1

    public PlanetData(SphereGrid grid) {
        this.grid = grid;
        this.n = grid.tiles;
        plate = new int[n];
        continental = new boolean[n];
        elevation = new float[n];
        water = new float[n];
        land = new boolean[n];
        tempC = new float[n];
        rainMm = new float[n];
        continentality = new float[n];
        koppen = new byte[n];
        snow = new float[n];
    }

    public int landCount() {
        int c = 0;
        for (boolean b : land) if (b) c++;
        return c;
    }
}
