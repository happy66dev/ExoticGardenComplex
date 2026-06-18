package io.github.thebusybiscuit.exoticgarden.cooking.calculator;

import io.github.thebusybiscuit.exoticgarden.cooking.CookingConstants;
import io.github.thebusybiscuit.exoticgarden.cooking.config.IngredientConfig;

public class StandardDonenessCalculator implements DonenessCalculator {

    @Override
    public double calculate(CookingContext ctx) {
        IngredientConfig.IngredientData config = ctx.config;
        double currentTemp = ctx.currentTemp;
        if (config.baseCookTimeSeconds <= 0) return 0;

        // 成熟速度系数公式：min((当前温度-室温)/max(max(成熟参考温度,50)-室温,20), 10)喵
        double refTemp = config.matureRefTemp;
        double base = CookingConstants.BASE_AMBIENT_TEMP;
        double denominator = Math.max(Math.max(refTemp, 50.0) - base, 20.0);
        double coefficient = Math.min((currentTemp - base) / denominator, 10.0);
        // 喵~防御：系数不能为负（温度低于室温时不成熟）喵
        if (coefficient <= 0) return 0;

        double boost = ctx.hasSpatulaBoost ? 2.0 : 1.0;
        return (1.0 / config.baseCookTimeSeconds) * ctx.deltaTime * coefficient * boost;
    }
}
