package io.github.thebusybiscuit.exoticgarden.cooking.hologram;

import io.github.thebusybiscuit.exoticgarden.cooking.block.StoveBlock;
import io.github.thebusybiscuit.exoticgarden.cooking.config.FuelConfig;
import io.github.thebusybiscuit.exoticgarden.cooking.config.IngredientConfig;
import io.github.thebusybiscuit.exoticgarden.cooking.config.SeasoningConfig;
import io.github.thebusybiscuit.exoticgarden.cooking.state.*;
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
        stove.updateMultiLineHologram(block, lines);
    }

    public static String buildLines(StoveState state,
                                    Map<String, FuelConfig.FuelData> fuels,
                                    Map<String, IngredientConfig.IngredientData> ingredients,
                                    Map<String, SeasoningConfig.SeasoningData> seasonings) {
        StringBuilder sb = new StringBuilder();

        double maxTemp = 0;
        double totalHeatRate = 0;
        for (FuelEntry fe : state.fuels) {
            FuelConfig.FuelData fd = fuels.get(fe.fuelId);
            if (fd != null) {
                totalHeatRate += fd.heatRate;
                maxTemp = Math.max(maxTemp, fd.tempGain);
            }
        }

        sb.append(String.format("§6[灶台] §e温度: §a%.0f°C §7/ §f%.0f°C\n",
            state.currentTemp, maxTemp));

        if (!state.fuels.isEmpty()) {
            for (FuelEntry fe : state.fuels) {
                double secs = fe.ticksRemaining / 20.0;
                sb.append(String.format("§b燃料: §f%s §7%.0fs\n", fe.fuelId, secs));
            }
            sb.append(String.format("§b升温: +%.1f°C/s\n", totalHeatRate));
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
            if (charLevel == CharLevel.SEVERE || charLevel == CharLevel.HEAVY) {
                sb.append(String.format("§e主菜%d: §c%s §c⚠烧焦\n", i + 1, slot.ingredientId));
            } else if (slot.state == FoodState.WHOLE) {
                int front = (int) (slot.frontDoneness * 100);
                int back = (int) (slot.backDoneness * 100);
                sb.append(String.format("§e主菜%d: §a%s[完整] §6正面%d%% 背面%d%%\n",
                    i + 1, slot.ingredientId, front, back));
            } else {
                int pct = (int) (slot.frontDoneness * 100);
                sb.append(String.format("§e主菜%d: §a%s[%s] §e%d%%\n",
                    i + 1, slot.ingredientId, slot.state.name(), pct));
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
}
