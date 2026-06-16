package io.github.thebusybiscuit.exoticgarden.cooking.config;

import org.bukkit.configuration.ConfigurationSection;

import java.util.List;
import java.util.logging.Logger;

public class IngredientConfig extends YamlConfigLoader<IngredientConfig.IngredientData> {

    public static class SauceCreation {
        public final int clicksRequired;

        public SauceCreation(int clicksRequired) {
            this.clicksRequired = clicksRequired;
        }
    }

    public static class IngredientData {
        public final double minTemp;
        public final double maxTemp;
        public final double optimalTempMin;
        public final double optimalTempMax;
        public final double baseCookTimeSeconds;
        public final boolean flipRequired;
        public final List<String> states;
        public final SauceCreation sauceCreation;
        public final String calculatorType;
        public final String displayName;
        public final double weightGrams;
        // 饱食度恢复量（hunger points），未配置时为2喵
        public final double foodPoints;
        // 饱和度，未配置时为2喵
        public final double saturation;
        // 保质期，单位：分钟，未配置时默认10分钟喵
        public final int shelfLifeMinutes;
        // 烹饪时向灶台释放的水量（ml），0-50%熟度阶段缓慢释放喵
        public final double waterMl;
        // 烹饪时向灶台释放的油量（ml），0-50%熟度阶段缓慢释放喵
        public final double oilMl;
        // 给 AI 的提示信息，帮助 AI 更准确识别食材风味和用途喵
        public final String hint;

        public IngredientData(double minTemp, double maxTemp, double optimalTempMin,
                              double optimalTempMax, double baseCookTimeSeconds,
                              boolean flipRequired, List<String> states,
                              SauceCreation sauceCreation, String calculatorType,
                              String displayName, double weightGrams,
                              double foodPoints, double saturation, int shelfLifeMinutes,
                              double waterMl, double oilMl, String hint) {
            this.minTemp = minTemp;
            this.maxTemp = maxTemp;
            this.optimalTempMin = optimalTempMin;
            this.optimalTempMax = optimalTempMax;
            this.baseCookTimeSeconds = baseCookTimeSeconds;
            this.flipRequired = flipRequired;
            this.states = states;
            this.sauceCreation = sauceCreation;
            this.calculatorType = calculatorType;
            this.displayName = displayName;
            this.weightGrams = weightGrams;
            this.foodPoints = foodPoints;
            this.saturation = saturation;
            this.shelfLifeMinutes = shelfLifeMinutes;
            this.waterMl = waterMl;
            this.oilMl = oilMl;
            this.hint = hint;
        }
    }

    public IngredientConfig(Logger logger) {
        super(logger);
    }

    @Override
    protected IngredientData parseEntry(String key, ConfigurationSection s) {
        SauceCreation sauce = null;
        if (s.contains("sauce_creation")) {
            sauce = new SauceCreation(s.getInt("sauce_creation.clicks_required", 3));
        }
        return new IngredientData(
            s.getDouble("min_temp"),
            s.getDouble("max_temp"),
            s.getDouble("optimal_temp_min"),
            s.getDouble("optimal_temp_max"),
            s.getDouble("base_cook_time_seconds"),
            s.getBoolean("flip_required", false),
            s.getStringList("states"),
            sauce,
            s.getString("calculator_type", "standard"),
            s.getString("display_name", key),
            s.getDouble("weight_grams", 100),
            s.getDouble("food_points", 2),
            s.getDouble("saturation", 2),
            s.getInt("shelf_life_minutes", 10),
            s.getDouble("water_ml", 0),   // 烹饪时释放的水量，默认0喵
            s.getDouble("oil_ml", 0),     // 烹饪时释放的油量，默认0喵
            s.getString("hint", "")       // 给 AI 的提示信息，默认空字符串喵
        );
    }
}
