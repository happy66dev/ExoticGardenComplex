package io.github.thebusybiscuit.exoticgarden.cooking.calculator;

import io.github.thebusybiscuit.exoticgarden.cooking.config.IngredientConfig;

public class StandardDonenessCalculator implements DonenessCalculator {

    @Override
    public double calculate(CookingContext ctx) {
        IngredientConfig.IngredientData config = ctx.config;
        double currentTemp = ctx.currentTemp;
        if (currentTemp < config.minTemp) return 0;
        if (currentTemp >= config.maxTemp) return 0;
        if (config.baseCookTimeSeconds <= 0) return 0;

        double coefficient;
        if (config.optimalTempMin > config.minTemp && currentTemp < config.optimalTempMin) {
            double range = config.optimalTempMin - config.minTemp;
            double progress = currentTemp - config.minTemp;
            coefficient = 0.5 + 0.5 * (progress / range);
        } else if (currentTemp <= config.optimalTempMax) {
            coefficient = 1.0;
        } else {
            double range = config.maxTemp - config.optimalTempMax;
            if (range <= 0) return 0;
            double over = currentTemp - config.optimalTempMax;
            coefficient = 1.0 - 0.5 * (over / range);
        }

        double boost = ctx.hasSpatulaBoost ? 2.0 : 1.0;
        return (1.0 / config.baseCookTimeSeconds) * ctx.deltaTime * coefficient * boost;
    }
}
