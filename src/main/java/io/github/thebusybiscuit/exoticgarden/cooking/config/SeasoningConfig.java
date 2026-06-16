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

        // 调料分类：water/oil/seasoning/potion，用于 SeasoningInteractionHandler 分支处理喵
        public final String category;

        // 容器返还类型：BUCKET/GLASS_BOTTLE/none，使用后返还对应容器喵
        public final String containerReturn;

        // 给 AI 的提示信息，帮助 AI 更准确识别调料风味和用途喵
        public final String hint;

        public SeasoningData(String displayName, boolean hasDoneness, double minTemp,
                             double optimalTemp, double baseTimeSeconds,
                             double weightGrams, double waterMl, double oilMl,
                             String category, String containerReturn, String hint) {
            this.displayName = displayName;
            this.hasDoneness = hasDoneness;
            this.minTemp = minTemp;
            this.optimalTemp = optimalTemp;
            this.baseTimeSeconds = baseTimeSeconds;
            this.weightGrams = weightGrams;
            this.waterMl = waterMl;
            this.oilMl = oilMl;
            this.category = category;
            this.containerReturn = containerReturn;
            this.hint = hint;
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
            s.getDouble("water_ml", 200),  // 默认出水量200ml喵
            s.getDouble("oil_ml", 0),      // 默认出油量0ml喵
            s.getString("category", "seasoning"),       // 默认为普通调料喵
            s.getString("container_return", "none"),    // 默认不返还容器喵
            s.getString("hint", "")                     // 默认无提示喵
        );
    }
}

