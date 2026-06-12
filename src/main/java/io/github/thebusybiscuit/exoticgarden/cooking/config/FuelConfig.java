package io.github.thebusybiscuit.exoticgarden.cooking.config;

import org.bukkit.configuration.ConfigurationSection;

import java.util.logging.Logger;

public class FuelConfig extends YamlConfigLoader<FuelConfig.FuelData> {

    public static class FuelData {
        public final double tempGain;
        public final double durationSeconds;
        public final double heatRate;
        public final String effect;
        public final String byproduct;

        public FuelData(double tempGain, double durationSeconds, double heatRate,
                        String effect, String byproduct) {
            this.tempGain = tempGain;
            this.durationSeconds = durationSeconds;
            this.heatRate = heatRate;
            this.effect = effect;
            this.byproduct = byproduct;
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
        return new FuelData(
            s.getDouble("temp_gain"),
            durationSeconds,
            s.getDouble("heat_rate"),
            s.getString("effect", ""),
            s.getString("byproduct", null)
        );
    }
}
