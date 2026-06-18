package io.github.thebusybiscuit.exoticgarden.cooking.calculator;

/**
 * 冰系食材融化计算器喵~
 * 整体思路：冰食材的"成熟度"即融化程度。温度越高融化越快，
 * 以0°C为融化起点，100°C时系数=1（标准速率），超过100°C系数线性增大。
 * 0°C以下不融化（系数=0）。
 * 输入：CookingContext（含当前灶台温度、食材配置）
 * 输出：本 tick 的融化进度增量（0~正数）
 */
public class IceDonenessCalculator implements DonenessCalculator {

    @Override
    public double calculate(CookingContext ctx) {
        double temp = ctx.currentTemp;
        // 喵~防御：0°C 以下不融化，返回 0 喵
        if (temp <= 0) return 0;
        // 喵~防御：baseCookTimeSeconds<=0 时跳过，防止除零喵
        if (ctx.config.baseCookTimeSeconds <= 0) return 0;

        // 融化系数：温度/100，100°C时=1，线性增长，上限10喵
        double coefficient = Math.min(temp / 100.0, 10.0);

        double boost = ctx.hasSpatulaBoost ? 2.0 : 1.0;
        // 喵~主人注意：baseCookTimeSeconds 越大融化越慢喵
        return (1.0 / ctx.config.baseCookTimeSeconds) * ctx.deltaTime * coefficient * boost;
    }
}
