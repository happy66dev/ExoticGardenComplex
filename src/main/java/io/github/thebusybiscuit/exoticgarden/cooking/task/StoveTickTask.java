package io.github.thebusybiscuit.exoticgarden.cooking.task;

import io.github.thebusybiscuit.exoticgarden.cooking.CookingConstants;
import io.github.thebusybiscuit.exoticgarden.cooking.block.StoveBlock;
import io.github.thebusybiscuit.exoticgarden.cooking.calculator.CookingContext;
import io.github.thebusybiscuit.exoticgarden.cooking.calculator.DonenessCalculator;
import io.github.thebusybiscuit.exoticgarden.cooking.hologram.StoveHologram;
import io.github.thebusybiscuit.exoticgarden.cooking.calculator.StandardDonenessCalculator;
import io.github.thebusybiscuit.exoticgarden.cooking.config.FuelConfig;
import io.github.thebusybiscuit.exoticgarden.cooking.config.IngredientConfig;
import io.github.thebusybiscuit.exoticgarden.cooking.config.SeasoningConfig;
import io.github.thebusybiscuit.exoticgarden.cooking.state.*;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Iterator;
import java.util.Map;

public class StoveTickTask extends BukkitRunnable {

    private final Map<String, FuelConfig.FuelData> fuels;
    private final Map<String, IngredientConfig.IngredientData> ingredients;
    private final Map<String, SeasoningConfig.SeasoningData> seasonings;
    private final Map<String, DonenessCalculator> calculators;
    private final DonenessCalculator defaultCalculator = new StandardDonenessCalculator();
    private final StoveBlock stove;
    private int hologramCounter = 0;


    public StoveTickTask(Map<String, FuelConfig.FuelData> fuels,
                         Map<String, IngredientConfig.IngredientData> ingredients,
                         Map<String, SeasoningConfig.SeasoningData> seasonings,
                         Map<String, DonenessCalculator> calculators,
                         StoveBlock stove) {
        this.fuels = fuels;
        this.ingredients = ingredients;
        this.seasonings = seasonings;
        this.calculators = calculators;
        this.stove = stove;
    }

    @Override
    public void run() {
        hologramCounter = (hologramCounter + 2) % 10;
        for (Map.Entry<Location, StoveState> entry : stove.activeStoves.entrySet()) {
            Location loc = entry.getKey();
            StoveState state = entry.getValue();
            // 喵~首次tick：清除可能残留的旧全息（上次服务器未正常关闭留下的多行全息）喵
            if (state.firstTick) {
                state.firstTick = false;
                StoveHologram.removeAndClean(loc, stove);
            }
            // 喵~AI生成期间冻结灶台，温度/食材/调料状态不变，跳过所有 tick 逻辑喵
            if (state.frozen) {
                if (hologramCounter == 0) {
                    StoveHologram.update(loc, state, fuels, ingredients, seasonings, stove);
                }
                continue;
            }
            tickFuels(state, loc);
            tickTemperature(state);
            tickEvaporation(state);
            if (state.spatulaBoostTicksLeft > 0) {
                state.spatulaBoostTicksLeft -= 2;
            }
            tickIngredients(state);
            tickSeasonings(state);
            StoveBlock.syncCampfireSlots(loc, state);
            if (hologramCounter == 0) {
                StoveHologram.update(loc, state, fuels, ingredients, seasonings, stove);
            }
        }
    }

    private void tickFuels(StoveState state, Location loc) {
        Iterator<FuelEntry> it = state.fuels.iterator();
        while (it.hasNext()) {
            FuelEntry fuel = it.next();
            fuel.ticksRemaining -= 2;
            if (fuel.ticksRemaining <= 0) {
                FuelConfig.FuelData data = fuels.get(fuel.fuelId);
                if (data != null && data.byproduct != null && !data.byproduct.isEmpty()) {
                    dropByproduct(data.byproduct, loc);
                }
                it.remove();
            }
        }
    }

    private void dropByproduct(String byproductId, Location loc) {
        Material mat = Material.getMaterial(byproductId);
        if (mat == null) return;
        if (loc.getWorld() == null) return;
        loc.getWorld().dropItemNaturally(loc, new ItemStack(mat));
    }

    /**
     * 温度计算：升温 + 散热/反向散热 + 冰燃料冷却喵~
     *
     * 整体思路：
     *   1. 累计燃料的 maxTemp 和 totalHeatRate（冰燃料 heatRate 为负值）
     *   2. 温度 > 室温时正常散热，< 室温时反向散热回升
     *   3. 冰燃料降温需快于反向散热，保证温度实际下降
     *   4. 最低温度限制 MIN_TEMP，不可低于 -20°C
     */
    private void tickTemperature(StoveState state) {
        double base = CookingConstants.BASE_AMBIENT_TEMP;
        double minTemp = CookingConstants.MIN_TEMP;
        double maxTemp = base;
        double totalHeatRate = 0;
        for (FuelEntry fe : state.fuels) {
            FuelConfig.FuelData data = fuels.get(fe.fuelId);
            if (data != null) {
                maxTemp += data.tempGain;
                totalHeatRate += data.heatRate;
            }
        }

        // 喵~先计算反向散热量（低温时回升到室温的自然速率）喵
        double warmDelta = 0;
        if (state.currentTemp < base) {
            double warmRate = (base - state.currentTemp) * 0.05;
            warmDelta = warmRate * 0.05; // 正值，代表每tick回升量喵
        }

        // 喵~散热（高温向室温回落）喵
        if (state.currentTemp > base) {
            double coolRate = (state.currentTemp - base) * 0.05;
            state.currentTemp = Math.max(state.currentTemp - coolRate * 0.05, base);
        } else if (state.currentTemp < base) {
            // 喵~反向散热：低温回升到室温（warmDelta 正值）喵
            // 冰燃料降温在下方叠加，需净效果 < 0 才能继续降温喵
            state.currentTemp = Math.min(state.currentTemp + warmDelta, base);
        }

        // 喵~燃料效果（含冰燃料负 heatRate）喵
        if (!state.fuels.isEmpty()) {
            if (totalHeatRate > 0 && state.currentTemp < maxTemp) {
                // 正常燃料加热喵
                state.currentTemp = Math.min(state.currentTemp + totalHeatRate * 0.1, maxTemp);
            } else if (totalHeatRate < 0) {
                // 冰燃料降温：每 tick 降温量需大于反向散热量，确保净效果是降温喵
                // 降温量 = |totalHeatRate| * 0.1，反向散热量 = warmDelta（低于室温时）
                // 净降温 = 降温量 - warmDelta（若为正则实际降温）喵
                double coolingDelta = -totalHeatRate * 0.1; // 正值，代表降温量喵
                double netCooling = coolingDelta - warmDelta; // 净降温喵
                if (netCooling > 0) {
                    state.currentTemp = Math.max(state.currentTemp - netCooling, minTemp);
                }
                // 喵~若净降温 <= 0（冰效果弱于反向散热），则维持反向散热的结果，不额外降温喵
            }
        }

        // 喵~防御：绝对不低于最低温度喵
        if (state.currentTemp < minTemp) state.currentTemp = minTemp;
    }

    /**
     * 食材成熟计算喵~（冰系食材相关代码已移除，冰系仅作为燃料使用）
     */
    private void tickIngredients(StoveState state) {
        for (int i = 0; i < state.slots.length; i++) {
            IngredientSlot slot = state.slots[i];
            if (slot == null) continue;
            IngredientConfig.IngredientData data = ingredients.get(slot.ingredientId);
            if (data == null) continue;

            // 喵~根据 calculatorType 选择对应计算器喵
            DonenessCalculator calculator = calculators.getOrDefault(data.calculatorType, defaultCalculator);
            CookingContext ctx = new CookingContext(state.currentTemp, data, 0.1, state.spatulaBoostTicksLeft > 0);
            double increment = calculator.calculate(ctx);

            // 喵~成熟系数>=2时（温度远超参考温度）开始焦化喵
            double base = CookingConstants.BASE_AMBIENT_TEMP;
            double refTemp = data.matureRefTemp;
            double denominator = Math.max(Math.max(refTemp, 50.0) - base, 20.0);
            double coefficient = (state.currentTemp - base) / denominator;
            if (coefficient >= 2.0) {
                slot.charSeconds = Math.min(slot.charSeconds + 0.1, 60.0);
            }

            // 喵~WHOLE 状态下双面熟度增量喵
            if (slot.state == FoodState.WHOLE) {
                if (slot.currentFace == ActiveFace.FRONT) {
                    slot.frontDoneness += increment;
                    slot.backDoneness += increment * 0.3;
                } else {
                    slot.backDoneness += increment;
                }
            } else {
                slot.frontDoneness += increment;
            }

            // 喵~在0-50%熟度阶段，按比例缓慢释放食材的出水/出油量喵
            if (data.waterMl > 0 || data.oilMl > 0) {
                double maxDoneness = Math.max(slot.frontDoneness, slot.backDoneness);
                double releaseRatio = Math.min(maxDoneness / 0.5, 1.0);
                double targetWater = data.waterMl * releaseRatio;
                double targetOil   = data.oilMl   * releaseRatio;
                double deltaWater = Math.max(0, targetWater - slot.releasedWaterMl);
                double deltaOil   = Math.max(0, targetOil   - slot.releasedOilMl);
                if (deltaWater > 0) {
                    state.waterAmount += deltaWater;
                    slot.releasedWaterMl += deltaWater;
                }
                if (deltaOil > 0) {
                    state.oilAmount += deltaOil;
                    slot.releasedOilMl += deltaOil;
                }
            }
        }
    }

    private void tickSeasonings(StoveState state) {
        for (SeasoningEntry se : state.seasonings) {
            SeasoningConfig.SeasoningData data = seasonings.get(se.seasoningId);
            if (data == null || !data.hasDoneness) continue;
            if (data.baseTimeSeconds <= 0) continue;
            if (state.currentTemp < data.minTemp) continue;
            double boost = state.spatulaBoostTicksLeft > 0 ? 1.5 : 1.0;
            // 喵~渗入速度由 base_time_seconds 配置直接控制，不再全局缩放喵
            double increment = (1.0 / data.baseTimeSeconds) * 0.1 * boost;
            // 辅料烹饪进度最大值200%（100%为完美值）喵
            se.progress = Math.min(se.progress + increment, 2.0);
        }
    }

    private void tickEvaporation(StoveState state) {
        if (state.waterAmount > 0 && state.currentTemp > 100) {
            // 水蒸发速度改为原来的25%喵
            double evapRate = (state.currentTemp - 100) * 0.005;
            state.waterAmount = Math.max(0, state.waterAmount - evapRate);
        }
    }
}
