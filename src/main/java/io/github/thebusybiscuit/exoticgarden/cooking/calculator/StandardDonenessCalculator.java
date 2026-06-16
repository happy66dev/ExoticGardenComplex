package io.github.thebusybiscuit.exoticgarden.cooking.calculator;

import io.github.thebusybiscuit.exoticgarden.cooking.config.IngredientConfig;

public class StandardDonenessCalculator implements DonenessCalculator {

    @Override
    public double calculate(CookingContext ctx) {
        IngredientConfig.IngredientData config = ctx.config;
        double currentTemp = ctx.currentTemp;
        if (currentTemp < config.minTemp) return 0;
        // 喵~超过maxTemp时成熟系数随温度升高而加速（越热越快熟/焦）：(temp-maxTemp)/(maxTemp-30)喵
        if (currentTemp >= config.maxTemp) {
            if (config.baseCookTimeSeconds <= 0) return 0;
            double range = config.maxTemp - 30.0;
            if (range <= 0) range = 1.0;
            double coefficient = (currentTemp - config.maxTemp) / range;
            double boost = ctx.hasSpatulaBoost ? 2.0 : 1.0;
            return (1.0 / config.baseCookTimeSeconds) * ctx.deltaTime * coefficient * boost;
        }
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
