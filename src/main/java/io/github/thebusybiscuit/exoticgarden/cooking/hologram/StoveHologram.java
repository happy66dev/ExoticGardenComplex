package io.github.thebusybiscuit.exoticgarden.cooking.hologram;

import io.github.thebusybiscuit.exoticgarden.cooking.block.StoveBlock;
import io.github.thebusybiscuit.exoticgarden.cooking.config.FuelConfig;
import io.github.thebusybiscuit.exoticgarden.cooking.config.IngredientConfig;
import io.github.thebusybiscuit.exoticgarden.cooking.config.SeasoningConfig;
import io.github.thebusybiscuit.exoticgarden.cooking.state.*;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import org.bukkit.Location;
import org.bukkit.block.Block;

import java.util.Map;
import java.util.StringJoiner;

public class StoveHologram {

    public static void update(Location loc, StoveState state,
                              Map<String, FuelConfig.FuelData> fuels,
                              Map<String, IngredientConfig.IngredientData> ingredients,
                              Map<String, SeasoningConfig.SeasoningData> seasonings,
                              StoveBlock stove) {
        Block block = loc.getBlock();
        String[] lines = buildLines(state, fuels, ingredients, seasonings).split("\\n");
        // 喵~baseLoc 固定（方块偏移点），行从该点向下排列（SF4 每行 -0.3）
        // 固定 baseLoc 才能保证 SF4 缓存命中，行数增减时能正确删除多余行喵
        Location baseLoc = block.getLocation()
                .add(stove.getHologramOffset(block));
        Slimefun.getHologramsService().setMultiLineHologram(baseLoc, lines);
    }

    public static String buildLines(StoveState state,
                                    Map<String, FuelConfig.FuelData> fuels,
                                    Map<String, IngredientConfig.IngredientData> ingredients,
                                    Map<String, SeasoningConfig.SeasoningData> seasonings) {
        StringBuilder sb = new StringBuilder();

        // 喵~冻结时显示 AI 状态而非常规数据喵
        if (state.frozen) {
            if (state.frozenReason != null) {
                sb.append("§c[AI失败] 右键解冻\n");
                sb.append("§7" + state.frozenReason);
            } else {
                sb.append("§6[AI生成中] 请稍候...\n");
                sb.append("§7温度/食材状态已冻结");
            }
            return sb.toString().trim();
        }

        double maxTemp = 30;
        double totalHeatRate = 0;
        for (FuelEntry fe : state.fuels) {
            FuelConfig.FuelData fd = fuels.get(fe.fuelId);
            if (fd != null) {
                totalHeatRate += fd.heatRate;
                maxTemp += fd.tempGain;
            }
        }

        sb.append(String.format("§6[灶台] §e温度: §a%.0f°C §7/ §f%.0f°C\n",
            state.currentTemp, maxTemp));

        // 始终显示水量和油量，为0时也显示0ml喵
        sb.append(String.format("§b水量: §f%.0fml  §e油量: §f%.0fml\n",
            state.waterAmount, state.oilAmount));

        if (!state.fuels.isEmpty()) {
            for (FuelEntry fe : state.fuels) {
                double secs = fe.ticksRemaining / 20.0;
                FuelConfig.FuelData fd = fuels.get(fe.fuelId);
                String fuelName = fd != null ? fd.displayName : fe.fuelId;
                sb.append(String.format("§b燃料: §f%s §7%.0fs\n", fuelName, secs));
            }
            sb.append(String.format("§b升温: +%.1f°C/s\n", totalHeatRate));
            double coolRate = Math.max(state.currentTemp - 30.0, 0) * 0.05;
            sb.append(String.format("§3散热: -%.1f°C/s\n", coolRate));
        } else {
            sb.append("§7无燃料\n");
        }

        for (int i = 0; i < state.slots.length; i++) {
            IngredientSlot slot = state.slots[i];
            if (slot == null) {
                sb.append(String.format("§e主菜%d: §7无\n", i + 1));
                continue;
            }
            CharLevel charLevel = CharLevel.fromSeconds(slot.charSeconds);
            String ingName = getIngredientName(slot.ingredientId, ingredients);
            if (charLevel == CharLevel.SEVERE || charLevel == CharLevel.HEAVY || charLevel == CharLevel.MEDIUM) {
                sb.append(String.format("§e主菜%d: §c%s §c⚠烧焦\n", i + 1, ingName));
            } else if (slot.state == FoodState.WHOLE) {
                int front = (int) (slot.frontDoneness * 100);
                int back = (int) (slot.backDoneness * 100);
                // 喵~当前烹饪面用亮色§6，另一面用灰色§7，让玩家直观看到当前哪面在受热喵
                String frontDisplay = slot.currentFace == ActiveFace.FRONT
                    ? "§6§l正面" + front + "%§r"
                    : "§7正面" + front + "%";
                String backDisplay = slot.currentFace == ActiveFace.BACK
                    ? "§6§l背面" + back + "%§r"
                    : "§7背面" + back + "%";
                sb.append(String.format("§e主菜%d: §a%s[完整] %s %s\n",
                    i + 1, ingName, frontDisplay, backDisplay));
            } else {
                int pct = (int) (slot.frontDoneness * 100);
                sb.append(String.format("§e主菜%d: §a%s[%s] §e%d%%\n",
                    i + 1, ingName, foodStateDisplay(slot.state), pct));
            }
        }

        if (!state.seasonings.isEmpty()) {
            StringJoiner sj = new StringJoiner(", ");
            for (SeasoningEntry se : state.seasonings) {
                SeasoningConfig.SeasoningData sd = seasonings.get(se.seasoningId);
                String name = sd != null ? sd.displayName : se.seasoningId;
                if (sd != null && sd.hasDoneness) {
                    sj.add(name + " " + (int) (se.progress * 100) + "%");
                } else {
                    sj.add(name);
                }
            }
            sb.append("§d辅料: ").append(sj);
        }

        return sb.toString().trim();
    }

    private static String getIngredientName(String id, Map<String, IngredientConfig.IngredientData> ingredients) {
        IngredientConfig.IngredientData data = ingredients.get(id);
        return data != null ? data.displayName : id;
    }

    private static String foodStateDisplay(FoodState state) {
        return switch (state) {
            case WHOLE -> "完整";
            case SLICED -> "切片";
            case DICED -> "切丁";
            case SAUCE -> "酱汁";
        };
    }
}
