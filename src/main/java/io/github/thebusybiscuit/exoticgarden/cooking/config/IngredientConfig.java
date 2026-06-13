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

        public IngredientData(double minTemp, double maxTemp, double optimalTempMin,
                              double optimalTempMax, double baseCookTimeSeconds,
                              boolean flipRequired, List<String> states,
                              SauceCreation sauceCreation, String calculatorType,
                              String displayName, double weightGrams) {
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
            s.getDouble("weight_grams", 100)
        );
    }
}
