package io.github.thebusybiscuit.exoticgarden.cooking.hologram;

import io.github.thebusybiscuit.exoticgarden.cooking.CookingConstants;
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

    // 喵~用 Location 的 world+xyz 作为 key，记录每个灶台上一次的 baseLoc 用于清除旧全息喵
    private static final java.util.concurrent.ConcurrentHashMap<String, org.bukkit.Location> lastBaseLoc
        = new java.util.concurrent.ConcurrentHashMap<>();

    // 喵~将 Location 转为稳定的 key 字符串（world+block坐标）喵
    private static String locKey(Location loc) {
        return loc.getWorld().getName() + ":" + loc.getBlockX() + ":" + loc.getBlockY() + ":" + loc.getBlockZ();
    }

    public static void update(Location loc, StoveState state,
                              Map<String, FuelConfig.FuelData> fuels,
                              Map<String, IngredientConfig.IngredientData> ingredients,
                              Map<String, SeasoningConfig.SeasoningData> seasonings,
                              StoveBlock stove) {
        Block block = loc.getBlock();
        String[] lines = buildLines(state, fuels, ingredients, seasonings).split("\\n");
        double LINE_SPACING = 0.3;
        // 喵~baseLoc = 最顶行位置，行从上往下排，最底行位置固定喵
        // 底部固定点 = getHologramOffset，baseLoc 上移 (n-1)*LINE_SPACING 使顶行在最上方喵
        Location bottomFixed = block.getLocation().add(stove.getHologramOffset(block));
        Location baseLoc = bottomFixed.clone().add(0, (lines.length - 1) * LINE_SPACING, 0);

        String key = locKey(bottomFixed);
        // 喵~如果 baseLoc 变了（行数变了），先删旧全息再设新全息，防止旧实体残留喵
        org.bukkit.Location prev = lastBaseLoc.get(key);
        if (prev != null && Math.abs(prev.getY() - baseLoc.getY()) > 0.01) {
            Slimefun.getHologramsService().removeMultiLineHologram(prev);
        }
        lastBaseLoc.put(key, baseLoc.clone());
        Slimefun.getHologramsService().setMultiLineHologram(baseLoc, lines);
    }

    public static void removeAndClean(Location loc, StoveBlock stove) {
        Block block = loc.getBlock();
        Location bottomFixed = block.getLocation().add(stove.getHologramOffset(block));
        String key = locKey(bottomFixed);
        // 喵~清除 lastBaseLoc 里记录的已知全息喵
        org.bukkit.Location prev = lastBaseLoc.remove(key);
        if (prev != null) {
            Slimefun.getHologramsService().removeMultiLineHologram(prev);
        }
        // 喵~扫描底部固定点上方最多15格内的所有可能 baseLoc，逐一清除残留全息
        // 这里针对的是服务器重启后 lastBaseLoc 为空、但残留全息仍存在的场景喵
        double LINE_SPACING = 0.3;
        int MAX_LINES = 15; // 最多可能有15行全息（每行0.3格，覆盖4.5格高度）喵
        for (int i = 0; i <= MAX_LINES; i++) {
            Location candidate = bottomFixed.clone().add(0, i * LINE_SPACING, 0);
            Slimefun.getHologramsService().removeMultiLineHologram(candidate);
        }
        // 喵~兜底：用 HologramOwner.removeHologram 清除单行全息喵
        stove.removeHologram(block);
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

        double base = CookingConstants.BASE_AMBIENT_TEMP;
        double maxTemp = base;
        double totalHeatRate = 0;
        for (FuelEntry fe : state.fuels) {
            FuelConfig.FuelData fd = fuels.get(fe.fuelId);
            if (fd != null) {
                totalHeatRate += fd.heatRate;
                maxTemp += fd.tempGain;
            }
        }

        // 喵~热容系数：与 tickTemperature 保持一致，显示实际有效速率喵
        double liquidTotal = state.waterAmount + state.oilAmount;
        double heatCapacityFactor = io.github.thebusybiscuit.exoticgarden.cooking.state.StoveState.BASE_LIQUID_ML
            / (io.github.thebusybiscuit.exoticgarden.cooking.state.StoveState.BASE_LIQUID_ML + liquidTotal);

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
            // 喵~升温/降温速率乘以热容系数后的实际值（每tick*10=每秒）喵
            double effectiveHeatRate = totalHeatRate * heatCapacityFactor;
            if (totalHeatRate >= 0) {
                sb.append(String.format("§b升温: +%.2f°C/s\n", effectiveHeatRate));
            } else {
                sb.append(String.format("§b降温: %.2f°C/s\n", effectiveHeatRate));
            }
            // 散热/反向散热速率也乘以热容系数显示真实值喵
            if (state.currentTemp > base) {
                double coolRatePerSec = (state.currentTemp - base) * 0.025 * heatCapacityFactor;
                sb.append(String.format("§3散热: -%.2f°C/s\n", coolRatePerSec));
            } else if (state.currentTemp < base) {
                double warmRatePerSec = (base - state.currentTemp) * 0.025 * heatCapacityFactor;
                sb.append(String.format("§a回升: +%.2f°C/s\n", warmRatePerSec));
            }
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
            if (slot.state == FoodState.WHOLE) {
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
            case WHOLE     -> "完整";
            case SLICED    -> "切片";
            case JULIENNED -> "切条";
            case DICED     -> "切丁";
            case SAUCE     -> "酱汁";
        };
    }
}
