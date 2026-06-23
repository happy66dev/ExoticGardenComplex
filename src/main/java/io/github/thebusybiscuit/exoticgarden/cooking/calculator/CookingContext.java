package io.github.thebusybiscuit.exoticgarden.cooking.calculator;

import io.github.thebusybiscuit.exoticgarden.cooking.config.IngredientConfig;
import io.github.thebusybiscuit.exoticgarden.cooking.state.FoodState;

public final class CookingContext {

    public final double currentTemp;
    public final IngredientConfig.IngredientData config;
    public final double deltaTime;
    public final boolean hasSpatulaBoost;
    // 食材当前切割状态，用于计算成熟速度倍率喵
    public final FoodState foodState;

    public CookingContext(double currentTemp, IngredientConfig.IngredientData config,
                          double deltaTime, boolean hasSpatulaBoost, FoodState foodState) {
        this.currentTemp = currentTemp;
        this.config = config;
        this.deltaTime = deltaTime;
        this.hasSpatulaBoost = hasSpatulaBoost;
        this.foodState = foodState;
    }
}
