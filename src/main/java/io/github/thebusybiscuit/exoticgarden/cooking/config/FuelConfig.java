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
        return new FuelData(
            s.getDouble("temp_gain"),
            s.getDouble("duration_seconds"),
            s.getDouble("heat_rate"),
            s.getString("effect", ""),
            s.getString("byproduct", null)
        );
    }
}
