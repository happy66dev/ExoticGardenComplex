package io.github.thebusybiscuit.exoticgarden.cooking.config;

import io.github.thebusybiscuit.exoticgarden.cooking.util.ItemIdUtil;
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

    /**
     * 读取 YAML 配置文件并解析所有条目喵~
     * 每个 key 在放入 map 前会通过 ItemIdUtil.normalizeKey() 规范化：
     * - 无命名空间前缀的旧格式 key 自动补 minecraft: 前缀并打 warning 喵
     * - 虚拟标识符（_开头）保持原样喵
     */
    public Map<String, T> loadAll(File file, String rootKey) {
        // 喵~防御：文件不存在时返回空 map 并打 warning 喵
        if (!file.exists()) {
            logger.warning("[Cooking] Config file not found: " + file.getName());
            return Collections.emptyMap();
        }
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        // 根据 rootKey 决定读取整个配置还是某个子节点喵
        ConfigurationSection section = rootKey.isEmpty() ? cfg
            : cfg.getConfigurationSection(rootKey);
        if (section == null) {
            logger.warning("[Cooking] Missing root section in " + file.getName());
            return Collections.emptyMap();
        }
        Map<String, T> result = new HashMap<>();
        for (String rawKey : section.getKeys(false)) {
            ConfigurationSection s = section.getConfigurationSection(rawKey);
            if (s == null) continue;

            // 规范化 key：旧格式无命名空间前缀时，自动补 minecraft: 并打 warning 喵
            String normalizedKey = ItemIdUtil.normalizeKey(rawKey);
            // 判断是否为旧格式（补了前缀才会和原始 key 不同）喵
            if (!rawKey.equals(normalizedKey)) {
                logger.warning("[Cooking] 配置 key '" + rawKey + "' 不含命名空间前缀，已自动补全为 '" + normalizedKey
                    + "'，建议更新配置文件喵~");
            }

            try {
                // 用规范化后的 key 存入 map 喵
                result.put(normalizedKey, parseEntry(normalizedKey, s));
            } catch (Exception e) {
                logger.warning("[Cooking] Failed to parse entry '" + rawKey + "' in " + file.getName() + ": " + e.getMessage());
            }
        }
        return Collections.unmodifiableMap(result);
    }

    protected abstract T parseEntry(String key, ConfigurationSection section);
}
