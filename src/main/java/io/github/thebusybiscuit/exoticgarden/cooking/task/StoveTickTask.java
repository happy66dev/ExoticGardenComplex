package io.github.thebusybiscuit.exoticgarden.cooking.task;

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

    private void tickTemperature(StoveState state) {
        double maxTemp = 30;
        double totalHeatRate = 0;
        for (FuelEntry fe : state.fuels) {
            FuelConfig.FuelData data = fuels.get(fe.fuelId);
            if (data != null) {
                maxTemp += data.tempGain;
                totalHeatRate += data.heatRate;
            }
        }

        // 散热速度改为原来的50%喵
        double coolRate = Math.max(state.currentTemp - 30.0, 0) * 0.05;
        state.currentTemp = Math.max(state.currentTemp - coolRate * 0.05, 30.0);

        if (!state.fuels.isEmpty() && state.currentTemp < maxTemp) {
            state.currentTemp = Math.min(state.currentTemp + totalHeatRate * 0.1, maxTemp);
        }
    }

    private void tickIngredients(StoveState state) {
        for (IngredientSlot slot : state.slots) {
            if (slot == null) continue;
            IngredientConfig.IngredientData data = ingredients.get(slot.ingredientId);
            if (data == null) continue;

            DonenessCalculator calculator = calculators.getOrDefault(data.calculatorType, defaultCalculator);
            CookingContext ctx = new CookingContext(state.currentTemp, data, 0.1, state.spatulaBoostTicksLeft > 0);
            // 全局食材成熟速度减慢4倍，让烹饪更有耐心喵~
            double increment = calculator.calculate(ctx) / 4.0;

            // 喵~成熟系数>=2时（温度远超参考温度）开始焦化喵
            double refTemp = data.matureRefTemp;
            double denominator = Math.max(Math.max(refTemp, 50.0) - 30.0, 20.0);
            double coefficient = (state.currentTemp - 30.0) / denominator;
            if (coefficient >= 2.0) {
                slot.charSeconds = Math.min(slot.charSeconds + 0.1, 60.0);
            }

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

            // 在0-50%熟度阶段，按比例缓慢释放食材的出水/出油量喵
            // 当前最高熟度（正反面取最大值）喵
            if (data.waterMl > 0 || data.oilMl > 0) {
                double maxDoneness = Math.max(slot.frontDoneness, slot.backDoneness);
                // 喵~防御：熟度钳制在0-0.5区间内计算释放比例，超过0.5后不再释放喵
                double releaseRatio = Math.min(maxDoneness / 0.5, 1.0);
                // 本tick应累计到的总释放量 = 总量 * 比例喵
                double targetWater = data.waterMl * releaseRatio;
                double targetOil   = data.oilMl   * releaseRatio;
                // 本tick实际释放增量（目标值 - 已释放量），不倒流喵
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
            // 全局调料渗入速度减慢4倍，与食材成熟速度保持一致喵~
            double increment = (1.0 / data.baseTimeSeconds) * 0.1 * boost / 4.0;
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
