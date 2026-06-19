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
     *   2. 热容系数 = 200 / (200 + 灶台液体总量)，液体越多加热/散热越慢喵
     *   3. 温度 > 室温时正常散热，< 室温时反向散热回升
     *   4. 冰燃料降温需快于反向散热，保证温度实际下降
     *   5. 最低温度限制 MIN_TEMP，不可低于 -50°C
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

        // 喵~热容系数：灶台假设热容等效200ml液体，液体越多升温/散热越慢喵
        // heatCapacityFactor = 200 / (200 + 当前液体总量)，范围(0,1]喵
        double liquidTotal = state.waterAmount + state.oilAmount;
        double heatCapacityFactor = StoveState.BASE_LIQUID_ML / (StoveState.BASE_LIQUID_ML + liquidTotal);

        // 喵~先计算反向散热量（低温时回升到室温的自然速率），也受热容系数影响喵
        double warmDelta = 0;
        if (state.currentTemp < base) {
            double warmRate = (base - state.currentTemp) * 0.05;
            // 喵~反向散热同样乘以热容系数：液体多则回升更慢喵
            warmDelta = warmRate * 0.05 * heatCapacityFactor;
        }

        // 喵~散热（高温向室温回落），乘以热容系数喵
        if (state.currentTemp > base) {
            double coolRate = (state.currentTemp - base) * 0.05;
            double coolDelta = coolRate * 0.05 * heatCapacityFactor;
            state.currentTemp = Math.max(state.currentTemp - coolDelta, base);
        } else if (state.currentTemp < base) {
            // 喵~反向散热：低温回升到室温喵
            state.currentTemp = Math.min(state.currentTemp + warmDelta, base);
        }

        // 喵~燃料效果（含冰燃料负 heatRate），乘以热容系数喵
        if (!state.fuels.isEmpty()) {
            if (totalHeatRate > 0 && state.currentTemp < maxTemp) {
                // 正常燃料加热：速度 = 原速度 * 热容系数喵
                state.currentTemp = Math.min(state.currentTemp + totalHeatRate * 0.1 * heatCapacityFactor, maxTemp);
            } else if (totalHeatRate < 0) {
                // 冰燃料降温，同样乘以热容系数喵
                double coolingDelta = -totalHeatRate * 0.1 * heatCapacityFactor;
                double netCooling = coolingDelta - warmDelta;
                if (netCooling > 0) {
                    state.currentTemp = Math.max(state.currentTemp - netCooling, minTemp);
                }
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
            // 判断是否水煮模式：有足够水量(>食材数*50ml)或食材配置no_flip_side时双面同时加热喵
            if (slot.state == FoodState.WHOLE) {
                int slotCount = 0;
                for (IngredientSlot s2 : state.slots) { if (s2 != null) slotCount++; }
                boolean waterCook = data.noFlipSide || state.waterAmount > slotCount * 50.0;
                if (waterCook) {
                    // 水煮/no_flip_side：双面同时加热，速度 * 0.8（均匀受热稍慢）喵
                    double waterIncrement = increment * 0.8;
                    slot.frontDoneness += waterIncrement;
                    slot.backDoneness += waterIncrement;
                } else if (slot.currentFace == ActiveFace.FRONT) {
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
                    // 喵~热均衡：食材渗出的液体视为室温混入喵
                    state.mixLiquid(deltaWater);
                    state.waterAmount += deltaWater;
                    slot.releasedWaterMl += deltaWater;
                }
                if (deltaOil > 0) {
                    state.mixLiquid(deltaOil);
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
