package com.hysx.salvosequence.tools;

import com.hysx.salvosequence.combat.Enemy;
import com.hysx.salvosequence.combat.EnemyCatalog;
import com.hysx.salvosequence.deck.Card;
import com.hysx.salvosequence.deck.CardCatalog;
import com.hysx.salvosequence.fleet.Fleet;
import com.hysx.salvosequence.fleet.FleetUnit;
import com.hysx.salvosequence.fleet.Ship;
import com.hysx.salvosequence.fleet.ShipArtStore;
import com.hysx.salvosequence.fleet.ShipCatalog;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 把数据表按 Markdown 打印出来，供 {@code docs/GAMEPLAY-LOOP.md} 回填。
 *
 * <h2>为什么要这个工具</h2>
 * <p>调数值时改的是代码，写文档时抄的是<b>上一次</b>的代码。只要中间跑过几轮调参，
 * 文档就一定开始撒谎——这也是验证过程中真实发生的事（敌人血量从 30/43/58/78/120
 * 调到 72/105/135/160/235，卡表的伤害也整组重算过）。所以文档里的表不再手抄，
 * 一律由这个工具生成、贴回去。</p>
 *
 * <p>纯逻辑、无 JavaFX，可以直接 {@code java -cp target\classes ...} 跑；
 * 输出走文件而不是控制台，避免 Windows 控制台把中文写坏。</p>
 */
public final class DesignDump {

    private DesignDump() {
    }

    public static void main(String[] args) throws IOException {
        String out = args.length > 0 ? args[0] : "target/design-tables.md";
        // 只导出厂数值：本机 ships/*.fleet.txt 里可能还留着玩家摆过的阵型，
        // 混进来会让文档里的起始舰队数据跟"新开一局"对不上。
        try {
            ShipArtStore.useDirectory(Files.createTempDirectory("hull-dump"));
        } catch (java.io.IOException e) {
            throw new IllegalStateException("建临时舰队目录失败", e);
        }
        StringBuilder md = new StringBuilder(8192);

        md.append("### 起始舰队\n\n");
        md.append("| 舰队 | 结构值 | 护盾 | 能量 | 抽牌 | 卡组 | 舰只 |\n");
        md.append("|---|---|---|---|---|---|---|\n");
        for (ShipCatalog.Starter starter : ShipCatalog.starters()) {
            Fleet fleet = starter.fleet();
            StringBuilder ships = new StringBuilder();
            for (FleetUnit unit : fleet.units()) {
                if (ships.length() > 0) {
                    ships.append("、");
                }
                ships.append(unit.labelZh());
            }
            md.append("| ").append(starter.nameZh())
                    .append(" | ").append(fleet.maxHp())
                    .append(" | ").append(fleet.armor())
                    .append(" | ").append(fleet.maxEnergy())
                    .append(" | ").append(fleet.handSize())
                    .append(" | ").append(fleet.deckSize())
                    .append(" | ").append(ships)
                    .append(" |\n");
        }

        md.append("\n### 舰船\n\n");
        md.append("| 种类 | 船 | 占地 | 结构值 | 护盾 | 防御力 | 防空 | 齐射 | 能量 | 抽牌 | 光环 | 卡 |\n");
        md.append("|---|---|---|---|---|---|---|---|---|---|---|---|\n");
        for (Ship ship : ShipCatalog.all()) {
            md.append("| ").append(ship.role().labelZh())
                    .append(" | ").append(ship.nameZh())
                    .append(" | ").append(ship.w()).append("×").append(ship.h())
                    .append(" | ").append(ship.hp())
                    .append(" | ").append(ship.armor() > 0 ? ship.armor() : "—")
                    .append(" | ").append(ship.plate() > 0 ? ship.plate() : "—")
                    .append(" | ").append(ship.flak() > 0 ? ship.flak() : "—")
                    .append(" | ").append(ship.volley() > 0 ? ship.volley() : "—")
                    .append(" | ").append(ship.energy() > 0 ? "+" + ship.energy() : "—")
                    .append(" | ").append(ship.draw() > 0 ? "+" + ship.draw() : "—")
                    .append(" | ").append(perkNames(ship.perks()))
                    .append(" | ").append(cardNames(ship.cardIds()))
                    .append(" |\n");
        }

        md.append("\n### 卡表\n\n");
        md.append("| 卡 | 类型 | 类别 | 费用 | 效果 | 装填 |\n");
        md.append("|---|---|---|---|---|---|\n");
        for (Card card : CardCatalog.all()) {
            md.append("| ").append(card.nameZh())
                    .append(" | ").append(card.damageType() == null ? "—" : card.damageType().labelZh())
                    .append(" | ").append(card.kind().labelZh())
                    .append(" | ").append(card.cost())
                    .append(" | ").append(card.textZh())
                    .append(" | ").append(card.reload() > 0 ? card.reload() : "—")
                    .append(" |\n");
        }

        md.append("\n### 编队（每关；掉落池 = 编队各敌舰的本体船型）\n\n");
        md.append("| 关 | 编队 | 合计结构值 | 单舰计划表（固定循环，玩家可见） | 掉落（本体） |\n");
        md.append("|---|---|---|---|---|\n");
        for (int node = 0; node < EnemyCatalog.routeLength(); node++) {
            List<Enemy> squad = EnemyCatalog.squad(node, node * 7919L + 7);
            int totalHp = 0;
            StringBuilder names = new StringBuilder();
            StringBuilder patterns = new StringBuilder();
            List<Ship> pool = new ArrayList<>();
            for (Enemy enemy : squad) {
                totalHp += enemy.hp();
                if (names.length() > 0) {
                    names.append(" + ");
                    patterns.append("<br>");
                }
                names.append(enemy.nameZh());
                StringBuilder steps = new StringBuilder();
                for (int i = 0; i < enemy.stepCount(); i++) {
                    if (i > 0) {
                        steps.append(" → ");
                    }
                    steps.append(enemy.labelZh(enemy.stepAt(i)));
                }
                patterns.append(enemy.nameZh()).append("：").append(steps);
                if (!pool.contains(enemy.hull())) {
                    pool.add(enemy.hull());
                }
            }
            md.append("| ").append(node + 1)
                    .append(" | ").append(names)
                    .append(" | ").append(totalHp)
                    .append(" | ").append(patterns)
                    .append(" | ").append(shipNames(pool))
                    .append(" |\n");
        }

        Path path = Path.of(out);
        File parent = path.toAbsolutePath().getParent().toFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("建不了输出目录: " + parent);
        }
        Files.writeString(path, md.toString(), StandardCharsets.UTF_8);
        System.out.println("已写出 " + path.toAbsolutePath()
                + "（" + CardCatalog.all().size() + " 张卡 / "
                + ShipCatalog.all().size() + " 艘船 / "
                + EnemyCatalog.routeLength() + " 关编队）");
    }

    private static String cardNames(List<String> ids) {
        StringBuilder out = new StringBuilder();
        for (String id : ids) {
            if (out.length() > 0) {
                out.append(" / ");
            }
            out.append(CardCatalog.byId(id).nameZh());
        }
        return out.toString();
    }

    private static String perkNames(List<com.hysx.salvosequence.fleet.Perk> perks) {
        return perks.isEmpty() ? "—" : com.hysx.salvosequence.fleet.Perk.labelsOf(perks);
    }

    private static String shipNames(List<Ship> ships) {
        StringBuilder out = new StringBuilder();
        for (Ship ship : ships) {
            if (out.length() > 0) {
                out.append("、");
            }
            out.append(ship.nameZh());
        }
        return out.toString();
    }
}
