package io.github.thebusybiscuit.exoticgarden.cooking.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

public abstract class YamlConfigLoader<T> {

    protected final Logger logger;

    protected YamlConfigLoader(Logger logger) {
        this.logger = logger;
    }

    public Map<String, T> loadAll(File file, String rootKey) {
        if (!file.exists()) {
            logger.warning("[Cooking] Config file not found: " + file.getName());
            return Collections.emptyMap();
        }
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = rootKey.isEmpty() ? cfg
            : cfg.getConfigurationSection(rootKey);
        if (section == null) {
            logger.warning("[Cooking] Missing root section in " + file.getName());
            return Collections.emptyMap();
        }
        Map<String, T> result = new HashMap<>();
        for (String key : section.getKeys(false)) {
            ConfigurationSection s = section.getConfigurationSection(key);
            if (s == null) continue;
            try {
                result.put(key, parseEntry(key, s));
            } catch (Exception e) {
                logger.warning("[Cooking] Failed to parse entry '" + key + "' in " + file.getName() + ": " + e.getMessage());
            }
        }
        return Collections.unmodifiableMap(result);
    }

    protected abstract T parseEntry(String key, ConfigurationSection section);
}
