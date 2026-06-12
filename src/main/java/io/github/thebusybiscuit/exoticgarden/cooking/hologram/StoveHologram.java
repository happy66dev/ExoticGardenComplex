package io.github.thebusybiscuit.exoticgarden.cooking.hologram;

import io.github.thebusybiscuit.exoticgarden.cooking.config.FuelConfig;
import io.github.thebusybiscuit.exoticgarden.cooking.config.IngredientConfig;
import io.github.thebusybiscuit.exoticgarden.cooking.config.SeasoningConfig;
import io.github.thebusybiscuit.exoticgarden.cooking.state.*;
import org.bukkit.Location;

import java.util.Map;
import java.util.StringJoiner;

public class StoveHologram {

    public static void update(Location loc, StoveState state,
                              Map<String, FuelConfig.FuelData> fuels,
                              Map<String, IngredientConfig.IngredientData> ingredients,
                              Map<String, SeasoningConfig.SeasoningData> seasonings) {
    }

    public static String buildLines(StoveState state,
                                    Map<String, FuelConfig.FuelData> fuels,
                                    Map<String, IngredientConfig.IngredientData> ingredients,
                                    Map<String, SeasoningConfig.SeasoningData> seasonings) {
        StringBuilder sb = new StringBuilder();

        double maxTemp = 0;
        double totalRate = 0;
        for (FuelEntry fe : state.fuels) {
            FuelConfig.FuelData fd = fuels.get(fe.fuelId);
            if (fd != null) {
                maxTemp += fd.tempGain;
                totalRate += fd.heatRate;
            }
        }

        sb.append(String.format("\u00a76[\u706f\u53f0] \u00a7e\u6e29\u5ea6: \u00a7a%.0f\u00b0C \u00a77/ \u00a7f%.0f\u00b0C\n",
            state.currentTemp, maxTemp));

        if (!state.fuels.isEmpty()) {
            FuelEntry first = state.fuels.get(0);
            double secs = first.ticksRemaining / 20.0;
            sb.append(String.format("\u00a7b\u5347\u6e29: +%.1f\u00b0C/s \u00a77(%s %.0fs)\n",
                totalRate, first.fuelId, secs));
        } else {
            sb.append("\u00a77\u65e0\u71c3\u6599\n");
        }

        for (int i = 0; i < state.slots.length; i++) {
            IngredientSlot slot = state.slots[i];
            if (slot == null) {
                sb.append(String.format("\u00a7e\u4e3b\u83dc%d: \u00a77\u65e0\n", i + 1));
                continue;
            }
            CharLevel charLevel = CharLevel.fromSeconds(slot.charSeconds);
            if (charLevel == CharLevel.SEVERE || charLevel == CharLevel.HEAVY) {
                sb.append(String.format("\u00a7e\u4e3b\u83dc%d: \u00a7c%s \u00a7c\u26a0\u70e7\u7126\n", i + 1, slot.ingredientId));
            } else if (slot.state == FoodState.WHOLE) {
                int front = (int) (slot.frontDoneness * 100);
                int back = (int) (slot.backDoneness * 100);
                sb.append(String.format("\u00a7e\u4e3b\u83dc%d: \u00a7a%s[\u5b8c\u6574] \u00a76\u6b63\u9762%d%% \u80cc\u9762%d%%\n",
                    i + 1, slot.ingredientId, front, back));
            } else {
                int pct = (int) (slot.frontDoneness * 100);
                sb.append(String.format("\u00a7e\u4e3b\u83dc%d: \u00a7a%s[%s] \u00a7e%d%%\n",
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
            sb.append("\u00a7d\u8f85\u6599: ").append(sj);
        }

        return sb.toString().trim();
    }
}
