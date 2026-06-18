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

    // 冰系食材每槽等效冷却速率（°C/tick），参考 minecraft:ICE 的 heatRate=-40 喵
    private static final double ICE_COOLING_PER_SLOT = -2.0;

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
            // 喵~先计算冰食材冷却速率（每槽固定值），再升温/散热喵
            updateIceCooling(state);
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
     * 每 tick 计算冰系食材的等效冷却速率，写入 state.iceCoolingRate 喵~
     * 冰食材每个槽等效于投入冰类燃料（负 heatRate），持续降低灶台温度喵
     */
    private void updateIceCooling(StoveState state) {
        double iceRate = 0;
        for (IngredientSlot slot : state.slots) {
            if (slot == null) continue;
            IngredientConfig.IngredientData data = ingredients.get(slot.ingredientId);
            if (data != null && "ice".equals(data.calculatorType)) {
                iceRate += ICE_COOLING_PER_SLOT;
            }
        }
        state.iceCoolingRate = iceRate;
    }

    /**
     * 温度计算：升温 + 散热/反向散热 + 冰食材冷却喵~
     *
     * 整体思路：
     *   1. 累计燃料的 maxTemp 和 totalHeatRate
     *   2. 叠加冰食材的 iceCoolingRate 到 totalHeatRate
     *   3. 温度 > 室温时正常散热，< 室温时反向散热回升
     *   4. 有燃料（或冰冷却）时，向 maxTemp 方向移动
     */
    private void tickTemperature(StoveState state) {
        double base = CookingConstants.BASE_AMBIENT_TEMP;
        double maxTemp = base;
        double totalHeatRate = 0;
        for (FuelEntry fe : state.fuels) {
            FuelConfig.FuelData data = fuels.get(fe.fuelId);
            if (data != null) {
                maxTemp += data.tempGain;
                totalHeatRate += data.heatRate;
            }
        }
        // 冰食材等效冷却叠加到总加热速率（负值使温度下降）喵
        totalHeatRate += state.iceCoolingRate;

        if (state.currentTemp > base) {
            // 正常散热：高温向室温回落喵
            double coolRate = (state.currentTemp - base) * 0.05;
            state.currentTemp = Math.max(state.currentTemp - coolRate * 0.05, base);
        } else if (state.currentTemp < base) {
            // 反向散热：低温回升到室温（同等速率）喵
            double warmRate = (base - state.currentTemp) * 0.05;
            state.currentTemp = Math.min(state.currentTemp + warmRate * 0.05, base);
        }

        // 有燃料或冰冷却时，向 maxTemp 方向移动喵
        // 主人注意：totalHeatRate 可能为负（冰食材降温），此时 maxTemp 可能低于当前温度，
        // 燃料加热和冰冷却通过同一逻辑自然平衡喵
        if (!state.fuels.isEmpty() || state.iceCoolingRate != 0) {
            if (totalHeatRate > 0 && state.currentTemp < maxTemp) {
                state.currentTemp = Math.min(state.currentTemp + totalHeatRate * 0.1, maxTemp);
            } else if (totalHeatRate < 0 && state.currentTemp > maxTemp) {
                state.currentTemp = Math.max(state.currentTemp + totalHeatRate * 0.1, maxTemp);
            }
        }
    }

    /**
     * 食材成熟/融化计算喵~
     *
     * 整体思路：
     *   1. 改用索引循环（不再用 for-each），以便冰食材融化后 null 掉槽位
     *   2. standard 类型走标准焦化逻辑，ice 类型走融化逻辑
     *   3. 冰食材融化到 100% 时：释放水分到灶台、记录水源、移除槽位
     *   4. 普通食材水/油释放逻辑不变
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

            // 喵~ice 类型走融化逻辑，不焦化喵
            boolean isIce = "ice".equals(data.calculatorType);
            if (!isIce) {
                // 喵~成熟系数>=2时（温度远超参考温度）开始焦化喵
                double base = CookingConstants.BASE_AMBIENT_TEMP;
                double refTemp = data.matureRefTemp;
                double denominator = Math.max(Math.max(refTemp, 50.0) - base, 20.0);
                double coefficient = (state.currentTemp - base) / denominator;
                if (coefficient >= 2.0) {
                    slot.charSeconds = Math.min(slot.charSeconds + 0.1, 60.0);
                }
            }

            // 喵~WHOLE 状态下双面熟度增量，冰类食材 frontDoneness 代表融化进度喵
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

            // 喵~冰食材：融化到 100% 时消失（释放水分+移除槽位）喵
            if (isIce) {
                double meltProgress = Math.max(slot.frontDoneness, slot.backDoneness);
                if (meltProgress >= 1.0) {
                    // 融化完成：释放水分到灶台水量喵
                    state.waterAmount += data.waterMl;
                    if (!state.waterSources.contains(data.displayName)) {
                        state.waterSources.add(data.displayName);
                    }
                    state.slots[i] = null;
                    // 跳过后续水/油释放逻辑（冰已消失）喵
                    continue;
                }
                // 冰类未融化完毕：不释放水/油，继续等待喵
                continue;
            }

            // 喵~普通食材：在0-50%熟度阶段，按比例缓慢释放食材的出水/出油量喵
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
