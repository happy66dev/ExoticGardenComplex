package io.github.thebusybiscuit.exoticgarden.cooking.util;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/**
 * 物品ID工具类：统一处理 Slimefun 物品和原版物品的命名空间 key 生成与规范化喵~
 * 所有物品 key 格式统一为：slimefun:ID 或 minecraft:MATERIAL_NAME
 */
public final class ItemIdUtil {

    // Slimefun 写入 PDC 的 key，用于读取 SF 物品 ID 喵
    private static final NamespacedKey KEY_SF_ITEM = new NamespacedKey("slimefun", "slimefun_item");

    /**
     * 从 ItemStack 生成带命名空间的 key喵~
     * - SF 物品 → "slimefun:SF_ITEM_ID"
     * - 原版物品 → "minecraft:MATERIAL_NAME"
     * - null 或 air → 返回 null
     * 输入：ItemStack 物品
     * 输出：带命名空间的 key 字符串，或 null
     */
    public static String toKey(ItemStack item) {
        // 喵~防御：item 为 null 或空气时返回 null 喵
        if (item == null || item.getType().isAir()) return null;

        // 尝试从 PDC 读取 SF 物品 ID 喵
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            // 读取 SF 写入的物品 ID 字段喵
            String sfId = pdc.get(KEY_SF_ITEM, PersistentDataType.STRING);
            if (sfId != null && !sfId.isEmpty()) {
                // 是 SF 物品，返回 slimefun: 前缀 key 喵
                return "slimefun:" + sfId;
            }
        }

        // 不是 SF 物品，使用原版材质名作为 minecraft: 前缀 key 喵
        return "minecraft:" + item.getType().name();
    }

    /**
     * 规范化 yml 配置中的 key喵~
     * - 已含 ":" 的 key 直接原样返回（已有命名空间）
     * - 以 "_" 开头的虚拟标识符原样返回（如 _POTION_）
     * - 其余旧格式 key 自动补 "minecraft:" 前缀
     * 输入：yml 中的 key 字符串（可为 null）
     * 输出：规范化后的 key，或 null
     */
    public static String normalizeKey(String key) {
        // 喵~防御：key 为 null 时返回 null 喵
        if (key == null) return null;

        // 已含命名空间分隔符，直接返回喵
        if (key.contains(":")) return key;

        // 虚拟标识符（以下划线开头，如 _POTION_），保持原样喵
        if (key.startsWith("_")) return key;

        // 旧格式无前缀 key，自动补 minecraft: 前缀喵
        return "minecraft:" + key;
    }

    // 工具类不允许实例化喵
    private ItemIdUtil() {}
}
