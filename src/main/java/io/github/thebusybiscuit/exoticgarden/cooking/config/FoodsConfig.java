package io.github.thebusybiscuit.exoticgarden.cooking.config;

import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;

/**
 * foods.yml 配置加载器喵~
 * 提供食物黑名单、通用保质期、逐物品保质期覆盖等功能
 */
public class FoodsConfig {

    // 从配置读取的不加保质期黑名单材质集合喵
    private final Set<Material> blacklist;

    // 通用食物保质期（分钟），默认10喵
    private final int genericFoodShelfLife;

    // 通用药水保质期（分钟），默认5喵
    private final int genericPotionShelfLife;

    // 逐物品保质期覆盖 key → 分钟数喵（key 格式：minecraft:MATERIAL）
    private final java.util.Map<String, Integer> overrides;

    /**
     * 从指定文件加载 foods 配置喵~
     * 输入：foods.yml 文件、日志器
     * 边界：文件不存在时使用硬编码默认值
     */
    public FoodsConfig(File file, Logger logger) {
        // 喵~防御：文件不存在时使用硬编码兜底值喵
        if (!file.exists()) {
            logger.warning("[Cooking] foods.yml 不存在，使用默认配置喵~");
            this.blacklist = buildDefaultBlacklist();
            this.genericFoodShelfLife = 10;
            this.genericPotionShelfLife = 5;
            this.overrides = Collections.emptyMap();
            return;
        }

        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);

        // 读取通用保质期默认值喵
        this.genericFoodShelfLife = cfg.getInt("defaults.generic_food_shelf_life", 10);
        this.genericPotionShelfLife = cfg.getInt("defaults.generic_potion_shelf_life", 5);

        // 解析黑名单：将 "minecraft:MATERIAL" 转为 Material 枚举喵
        List<String> rawBlacklist = cfg.getStringList("blacklist");
        Set<Material> parsedBlacklist = EnumSet.noneOf(Material.class);
        for (String entry : rawBlacklist) {
            // 喵~防御：entry 为 null 或格式不对时跳过喵
            if (entry == null || entry.isEmpty()) continue;
            String matName = entry; // 原始条目喵
            // 去掉 minecraft: 前缀，得到材质名称喵
            if (matName.startsWith("minecraft:")) {
                matName = matName.substring("minecraft:".length());
            }
            // 将字符串解析为 Material 枚举喵
            Material mat = Material.getMaterial(matName.toUpperCase(java.util.Locale.ENGLISH));
            if (mat != null) {
                parsedBlacklist.add(mat);
            } else {
                // 喵~防御：材质名称无效时打 warning 喵
                logger.warning("[Cooking] foods.yml 黑名单中无效材质: " + entry + "，已跳过喵~");
            }
        }
        this.blacklist = parsedBlacklist.isEmpty()
            ? buildDefaultBlacklist()
            : Collections.unmodifiableSet(parsedBlacklist);

        // 解析逐物品覆盖 overrides 喵
        java.util.Map<String, Integer> parsedOverrides = new java.util.HashMap<>();
        org.bukkit.configuration.ConfigurationSection overridesSection = cfg.getConfigurationSection("overrides");
        if (overridesSection != null) {
            for (String key : overridesSection.getKeys(false)) {
                int shelfLife = overridesSection.getInt(key + ".shelf_life_minutes", -1);
                // 喵~防御：无效保质期（<=0）跳过喵
                if (shelfLife > 0) {
                    parsedOverrides.put(key, shelfLife);
                }
            }
        }
        this.overrides = Collections.unmodifiableMap(parsedOverrides);
    }

    /**
     * 获取不加保质期的材质黑名单喵~
     * 返回：Set<Material>，包含所有不应打保质期标签的物品材质
     */
    public Set<Material> getBlacklist() {
        return blacklist;
    }

    /**
     * 获取通用食物保质期（分钟）喵~
     * 用于没有单独配置的原版可食用物品
     */
    public int getGenericFoodShelfLife() {
        return genericFoodShelfLife;
    }

    /**
     * 获取通用药水保质期（分钟）喵~
     * 用于药水类物品
     */
    public int getGenericPotionShelfLife() {
        return genericPotionShelfLife;
    }

    /**
     * 获取指定物品 key 的覆盖保质期喵~
     * 输入：带命名空间的 key，如 "minecraft:COOKIE"
     * 输出：Optional<Integer>，有覆盖时返回对应分钟数，否则为空
     */
    public Optional<Integer> getOverrideShelfLife(String itemKey) {
        // 喵~防御：itemKey 为 null 时返回空 Optional 喵
        if (itemKey == null) return Optional.empty();
        return Optional.ofNullable(overrides.get(itemKey));
    }

    /**
     * 构建硬编码兜底黑名单，与旧代码保持一致喵~
     */
    private static Set<Material> buildDefaultBlacklist() {
        return Collections.unmodifiableSet(EnumSet.of(
            Material.GOLDEN_APPLE,           // 金苹果喵
            Material.ENCHANTED_GOLDEN_APPLE, // 附魔金苹果喵
            Material.CAKE,                   // 蛋糕（放置型）喵
            Material.ROTTEN_FLESH,           // 腐肉喵
            Material.SPIDER_EYE,             // 蜘蛛眼喵
            Material.FERMENTED_SPIDER_EYE,   // 发酵蜘蛛眼喵
            Material.POISONOUS_POTATO,       // 毒土豆喵
            Material.GLISTERING_MELON_SLICE  // 闪烁的瓜片喵
        ));
    }
}
