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

        double coolRate = Math.max(state.currentTemp - 30.0, 0) * 0.05;
        state.currentTemp = Math.max(state.currentTemp - coolRate * 0.1, 30.0);

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
            double increment = calculator.calculate(ctx);

            if (state.currentTemp >= data.maxTemp) {
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
        }
    }

    private void tickSeasonings(StoveState state) {
        for (SeasoningEntry se : state.seasonings) {
            SeasoningConfig.SeasoningData data = seasonings.get(se.seasoningId);
            if (data == null || !data.hasDoneness) continue;
            if (data.baseTimeSeconds <= 0) continue;
            if (state.currentTemp < data.minTemp) continue;
            double increment = (1.0 / data.baseTimeSeconds) * 0.1;
            se.progress = Math.min(se.progress + increment, 1.0);
        }
    }

    private void tickEvaporation(StoveState state) {
        if (state.waterAmount > 0 && state.currentTemp > 100) {
            double evapRate = (state.currentTemp - 100) * 0.02;
            state.waterAmount = Math.max(0, state.waterAmount - evapRate);
        }
    }
}
