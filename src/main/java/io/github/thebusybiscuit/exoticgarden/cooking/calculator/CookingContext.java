package io.github.thebusybiscuit.exoticgarden.cooking.calculator;

import io.github.thebusybiscuit.exoticgarden.cooking.config.IngredientConfig;

public final class CookingContext {

    public final double currentTemp;
    public final IngredientConfig.IngredientData config;
    public final double deltaTime;
    public final boolean hasSpatulaBoost;

    public CookingContext(double currentTemp, IngredientConfig.IngredientData config,
                          double deltaTime, boolean hasSpatulaBoost) {
        this.currentTemp = currentTemp;
        this.config = config;
        this.deltaTime = deltaTime;
        this.hasSpatulaBoost = hasSpatulaBoost;
    }
}
