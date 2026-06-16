package io.github.thebusybiscuit.exoticgarden.cooking.config;

import org.bukkit.configuration.ConfigurationSection;

import java.util.logging.Logger;

public class SeasoningConfig extends YamlConfigLoader<SeasoningConfig.SeasoningData> {

    public static class SeasoningData {
        public final String displayName;
        public final boolean hasDoneness;
        public final double minTemp;
        public final double optimalTemp;
        public final double baseTimeSeconds;
        public final double weightGrams;
        public final double waterMl;
        public final double oilMl; // 出油量（毫升），默认0喵

        public SeasoningData(String displayName, boolean hasDoneness, double minTemp,
                             double optimalTemp, double baseTimeSeconds,
                             double weightGrams, double waterMl, double oilMl) {
            this.displayName = displayName;
            this.hasDoneness = hasDoneness;
            this.minTemp = minTemp;
            this.optimalTemp = optimalTemp;
            this.baseTimeSeconds = baseTimeSeconds;
            this.weightGrams = weightGrams;
            this.waterMl = waterMl;
            this.oilMl = oilMl;
        }
    }

    public SeasoningConfig(Logger logger) {
        super(logger);
    }

    @Override
    protected SeasoningData parseEntry(String key, ConfigurationSection s) {
        return new SeasoningData(
            s.getString("display_name", key),
            s.getBoolean("has_doneness", false),
            s.getDouble("min_temp", 0),
            s.getDouble("optimal_temp", 100),
            s.getDouble("base_time_seconds", 30),
            s.getDouble("weight_grams", 1),
            s.getDouble("water_ml", 0),
            s.getDouble("oil_ml", 0) // 出油量，默认0喵
        );
    }
}
