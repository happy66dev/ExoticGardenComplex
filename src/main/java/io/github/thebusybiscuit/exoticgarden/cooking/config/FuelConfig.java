package io.github.thebusybiscuit.exoticgarden.cooking.config;

import org.bukkit.configuration.ConfigurationSection;

import java.util.logging.Logger;

public class FuelConfig extends YamlConfigLoader<FuelConfig.FuelData> {

    public static class FuelData {
        public final double tempGain;
        public final double durationSeconds;
        public final double heatRate;
        // 燃料风味效果的英文 ID（如 WOOD_SMOKE），来自 fuels.yml 的 effect 字段喵
        public final String effect;
        public final String byproduct;
        public final String displayName;
        // 燃料风味效果的中文显示名（如 "橡木烟熏"），来自 fuels.yml 的 effect_display_name 字段喵
        // 若未配置则回退为 effect 字段原值喵
        public final String effectDisplayName;

        public FuelData(double tempGain, double durationSeconds, double heatRate,
                        String effect, String byproduct, String displayName, String effectDisplayName) {
            this.tempGain = tempGain;
            this.durationSeconds = durationSeconds;
            this.heatRate = heatRate;
            this.effect = effect;
            this.byproduct = byproduct;
            this.displayName = displayName;
            // 喵~防御：effectDisplayName 为 null 时回退为 effect 原值，避免 NPE 喵
            this.effectDisplayName = effectDisplayName != null ? effectDisplayName : effect;
        }
    }

    public FuelConfig(Logger logger) {
        super(logger);
    }

    @Override
    protected FuelData parseEntry(String key, ConfigurationSection s) {
        double durationSeconds = s.getDouble("duration_seconds");
        if (durationSeconds <= 0) {
            logger.warning("[FuelConfig] 燃料 '" + key + "' 的 duration_seconds <= 0，将被忽略或立刻消耗");
        }
        // 读取英文效果 ID，默认空字符串喵
        String effect = s.getString("effect", "");
        // 读取中文显示名，若未配置则回退为英文 effect 值喵
        String effectDisplayName = s.getString("effect_display_name", null);
        // 喵~防御：effectDisplayName 未配置时用 effect 原值，避免传给 AI 空内容喵
        if (effectDisplayName == null || effectDisplayName.isEmpty()) {
            effectDisplayName = effect;
        }
        return new FuelData(
            s.getDouble("temp_gain"),
            durationSeconds,
            s.getDouble("heat_rate"),
            effect,
            s.getString("byproduct", null),
            s.getString("display_name", key),
            effectDisplayName
        );
    }
}
