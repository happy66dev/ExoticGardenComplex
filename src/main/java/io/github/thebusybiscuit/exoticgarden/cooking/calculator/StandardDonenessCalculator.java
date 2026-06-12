package io.github.thebusybiscuit.exoticgarden.cooking.calculator;

import io.github.thebusybiscuit.exoticgarden.cooking.config.IngredientConfig;

public class StandardDonenessCalculator implements DonenessCalculator {

    @Override
    public double calculate(double currentTemp, IngredientConfig.IngredientData config,
                           double deltaTime, boolean hasSpatulaBoost) {
        if (currentTemp < config.minTemp) return 0;

        double coefficient;
        if (currentTemp <= config.optimalTempMax) {
            coefficient = 1.0;
        } else if (currentTemp < config.maxTemp) {
            double range = config.maxTemp - config.optimalTempMax;
            double over = currentTemp - config.optimalTempMax;
            coefficient = 1.0 - 0.5 * (over / range);
        } else {
            coefficient = 1.5;
        }

        double boost = hasSpatulaBoost ? 2.0 : 1.0;
        return (1.0 / config.baseCookTimeSeconds) * deltaTime * coefficient * boost;
    }
}
