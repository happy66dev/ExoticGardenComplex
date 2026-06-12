package io.github.thebusybiscuit.exoticgarden.cooking.calculator;

import io.github.thebusybiscuit.exoticgarden.cooking.config.IngredientConfig;

public interface DonenessCalculator {
    double calculate(double currentTemp, IngredientConfig.IngredientData config,
                     double deltaTime, boolean hasSpatulaBoost);
}
