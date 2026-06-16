package io.github.thebusybiscuit.exoticgarden.cooking;

import io.github.thebusybiscuit.exoticgarden.cooking.config.IngredientConfig;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCreativeEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class FoodTagListener implements Listener {

    private static final org.bukkit.NamespacedKey KEY_SF_ITEM =
            new org.bukkit.NamespacedKey("slimefun", "slimefun_item");

    /**
     * 不加保质期的黑名单食物列表：
     * - 金苹果/附魔金苹果（特殊效果物品，不易腐坏喵）
     * - 蛋糕（放置型方块食物，逻辑特殊喵）
     * - 腐肉/蜘蛛眼/发酵蜘蛛眼/毒土豆（本身就是"有问题"的食物喵）
     * - 闪烁的瓜片（合成材料，通常不直接吃喵）
     */
    private static final Set<Material> BLACKLIST = EnumSet.of(
        Material.GOLDEN_APPLE,          // 金苹果喵
        Material.ENCHANTED_GOLDEN_APPLE, // 附魔金苹果喵
        Material.CAKE,                  // 蛋糕（放置型）喵
        Material.ROTTEN_FLESH,          // 腐肉喵
        Material.SPIDER_EYE,            // 蜘蛛眼喵
        Material.FERMENTED_SPIDER_EYE,  // 发酵蜘蛛眼喵
        Material.POISONOUS_POTATO,      // 毒土豆喵
        Material.GLISTERING_MELON_SLICE // 闪烁的瓜片（即GOLDEN_MELON_SLICE）喵
    );

    /**
     * 通用食物标识符，用于原版可食用物品（不在黑名单中）的 fallback 保质期标签喵~
     * 保质期默认 10 分钟
     */
    private static final String GENERIC_FOOD_ID = "_GENERIC_FOOD_";

    /**
     * 通用药水标识符，用于药水类物品的 fallback 保质期标签喵~
     * 保质期默认 5 分钟（药水效果随时间减弱）
     */
    private static final String GENERIC_POTION_ID = "_GENERIC_POTION_";

    /** 通用食物默认保质期：10分钟喵 */
    private static final int GENERIC_FOOD_SHELF_LIFE_MINUTES = 10;

    /** 通用药水默认保质期：5分钟喵 */
    private static final int GENERIC_POTION_SHELF_LIFE_MINUTES = 5;

    private final Map<String, IngredientConfig.IngredientData> ingredients;

    public FoodTagListener(Map<String, IngredientConfig.IngredientData> ingredients) {
        this.ingredients = ingredients;
    }

    @EventHandler(ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent e) {
        if (e.getEntity().getType() != org.bukkit.entity.EntityType.PLAYER) return;
        ItemStack item = e.getItem().getItemStack();
        tagIfIngredient(item);
    }

    // 创造模式取物走 InventoryCreativeEvent，InventoryClickEvent 不覆盖它喵
    @EventHandler(ignoreCancelled = true)
    public void onCreativeClick(InventoryCreativeEvent e) {
        if (!(e.getWhoClicked() instanceof org.bukkit.entity.Player player)) return;
        ItemStack cursor = e.getCursor() != null ? e.getCursor().clone() : null;
        if (tagIfIngredient(cursor)) e.setCursor(cursor);
        // 延迟1tick扫描背包，覆盖创造模式直接放入背包的物品喵
        org.bukkit.Bukkit.getScheduler().runTaskLater(
            io.github.thebusybiscuit.exoticgarden.ExoticGarden.getInstance(),
            () -> {
                for (int i = 0; i < player.getInventory().getSize(); i++) {
                    ItemStack it = player.getInventory().getItem(i);
                    if (tagIfIngredient(it)) player.getInventory().setItem(i, it);
                }
            }, 1L);
    }

    // 玩家切换主手槽位时，检查目标槽物品并更新时间戳喵
    @EventHandler(ignoreCancelled = true)
    public void onHeldItemChange(PlayerItemHeldEvent e) {
        // 获取切换后目标槽的物品喵
        ItemStack target = e.getPlayer().getInventory().getItem(e.getNewSlot());
        if (target == null || target.getType().isAir()) return;
        // 喵~防御：是食材则原地更新时间戳/lore，不是食材静默跳过喵
        ItemStack copy = target.clone();
        if (tagIfIngredient(copy)) {
            e.getPlayer().getInventory().setItem(e.getNewSlot(), copy);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof org.bukkit.entity.Player player)) return;

        // cursor 跟随鼠标：clone后标记，写回前对比PDC是否真的变化，防止刷物品喵
        ItemStack cursor = e.getCursor() != null ? e.getCursor().clone() : null;
        if (tagIfIngredient(cursor)) {
            // 喵~防御：写回前确认 cursor 槽位内容未被其他操作修改（对比类型和数量），一致才写回喵
            ItemStack liveCursor = e.getCursor();
            if (liveCursor != null && liveCursor.getType() == cursor.getType()
                    && liveCursor.getAmount() == cursor.getAmount()) {
                e.setCursor(cursor);
            }
        }

        // currentItem：延迟1tick写回，避免和Bukkit事件机制对槽位的赋值竞争导致刷物品喵
        if (e.getCurrentItem() != null && !e.getCurrentItem().getType().isAir()) {
            final int rawSlot = e.getRawSlot();
            final org.bukkit.inventory.Inventory inv = e.getInventory();
            // 记录事件触发时的物品快照，用于后续对比喵
            final ItemStack snapshot = e.getCurrentItem().clone();
            org.bukkit.Bukkit.getScheduler().runTaskLater(
                io.github.thebusybiscuit.exoticgarden.ExoticGarden.getInstance(),
                () -> {
                    // 喵~防御：延迟后从背包重新获取物品，确保操作的是最新状态喵
                    ItemStack live = rawSlot < inv.getSize() ? inv.getItem(rawSlot) : null;
                    if (live == null || live.getType().isAir()) return;
                    // 喵~防御：只有类型和数量与事件时一致，才判定物品未被移动，安全写回喵
                    if (live.getType() != snapshot.getType() || live.getAmount() != snapshot.getAmount()) return;
                    ItemStack toTag = live.clone();
                    if (tagIfIngredient(toTag)) inv.setItem(rawSlot, toTag);
                }, 1L);
        }

        // shift+click 会把物品转移到其他位置，延迟1tick扫玩家背包所有格子喵
        if (e.isShiftClick()) {
            org.bukkit.Bukkit.getScheduler().runTaskLater(
                io.github.thebusybiscuit.exoticgarden.ExoticGarden.getInstance(),
                () -> {
                    for (int i = 0; i < player.getInventory().getSize(); i++) {
                        ItemStack it = player.getInventory().getItem(i);
                        if (it == null || it.getType().isAir()) continue;
                        ItemStack copy = it.clone();
                        if (tagIfIngredient(copy)) player.getInventory().setItem(i, copy);
                    }
                }, 1L);
        }
    }

    /**
     * 为食材打标签：写入食材状态、ID、时间戳，并更新保质期lore喵~
     * 复杂逻辑说明：
     *   - 已有 FOOD_STATE 标签的物品不再早return，而是继续刷新时间戳和保质期lore
     *   - lore中保质期行以"§8保质期:"开头作为标识，找到则替换，找不到则追加
     *   - 时间戳存入PDC的LONG类型，直接用System.currentTimeMillis()喵
     * 输入：ItemStack（可为null）
     * 输出：是否对物品做了修改（true=改了，false=未改）
     * 边界：item为null、air直接返回false喵
     */
    private boolean tagIfIngredient(ItemStack item) {
        // 喵~防御：item为null或空气时直接返回，避免NPE喵
        if (item == null || item.getType().isAir()) return false;
        // 解析该物品对应的食材ID，不是食材则跳过喵
        String ingId = resolveIngredientId(item);
        if (ingId == null) return false;

        // 获取物品meta，meta不存在则无法写PDC喵
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        PersistentDataContainer pdc = meta.getPersistentDataContainer();

        // 首次标记时写入食材状态和食材ID：通用食物/药水不写FOOD_STATE（它们不参与烹饪状态机）喵
        boolean isGeneric = GENERIC_FOOD_ID.equals(ingId) || GENERIC_POTION_ID.equals(ingId);
        if (!isGeneric && !pdc.has(CookingKeys.FOOD_STATE, PersistentDataType.STRING)) {
            // 首次打标签，设置整块状态（仅烹饪食材需要）喵
            pdc.set(CookingKeys.FOOD_STATE, PersistentDataType.STRING, "WHOLE");
        }
        if (!pdc.has(CookingKeys.INGREDIENT_ID, PersistentDataType.STRING)) {
            // 首次打标签，写入食材ID（通用标识也写入，用于后续识别）喵
            pdc.set(CookingKeys.INGREDIENT_ID, PersistentDataType.STRING, ingId);
        }

        // 时间戳不可变：首次写入后不再更新，用于计算保质期喵
        if (!pdc.has(CookingKeys.FOOD_TIMESTAMP, PersistentDataType.LONG)) {
            pdc.set(CookingKeys.FOOD_TIMESTAMP, PersistentDataType.LONG, System.currentTimeMillis());
        }
        // 从PDC读取不可变时间戳喵
        long nowMs = pdc.get(CookingKeys.FOOD_TIMESTAMP, PersistentDataType.LONG);

        // 构建lore列表，已有lore则复制一份可修改的副本喵
        List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();

        // 判断是通用食物、通用药水还是普通食材配置，分别取保质期和标签行喵
        final int shelfLifeMinutes; // 最终使用的保质期（分钟）喵
        final String ingredientLine; // 最终显示在lore里的类型标签行喵

        if (GENERIC_FOOD_ID.equals(ingId)) {
            // 通用原版食物：lore显示"[食物]"，保质期10分钟，默认克重100g喵
            shelfLifeMinutes = GENERIC_FOOD_SHELF_LIFE_MINUTES;
            ingredientLine = "§7[食物] §e营养度: 0 §7(100g)";
        } else if (GENERIC_POTION_ID.equals(ingId)) {
            // 通用药水：lore显示"[药水]"，保质期5分钟，默认克重0g喵
            shelfLifeMinutes = GENERIC_POTION_SHELF_LIFE_MINUTES;
            ingredientLine = "§7[药水] §7(0g)";
        } else {
            // 普通烹饪食材：从配置读取营养值、保质期和克重喵
            // 可变标签：营养值随配置变化，每次刷新喵
            IngredientConfig.IngredientData data = ingredients.get(ingId);
            double nutrition = data != null ? data.foodPoints + data.saturation : 0;
            shelfLifeMinutes = data != null ? data.shelfLifeMinutes : 10; // 保质期分钟数，默认10喵
            double weightGrams = data != null ? data.weightGrams : 100; // 克重，默认100g喵
            String nutritionStr = nutrition > 0
                    ? " §e营养度: " + (Math.round(nutrition * 10.0) / 10.0)
                    : "";
            // 喵~防御：克重为0时不显示(0g)，只在克重>0时显示喵
            String weightStr = weightGrams > 0 ? " §7(" + (int) weightGrams + "g)" : "";
            // 不可变标签：读取实际 FOOD_STATE，而非硬编码"WHOLE"喵
            String currentState = pdc.get(CookingKeys.FOOD_STATE, PersistentDataType.STRING);
            ingredientLine = "§7[烹饪食材] §f" + translateState(currentState) + nutritionStr + weightStr;
        }

        // 检查lore中是否已有对应类型行，有则替换，没有则追加喵
        boolean hasIngredientLine = false; // 标记是否已找到并替换了食材行喵
        for (int loreIdx = 0; loreIdx < lore.size(); loreIdx++) {
            String loreLine = lore.get(loreIdx);
            // 匹配"§7[烹饪食材]"、"§7[食物]"或"§7[药水]"开头的行喵
            if (loreLine.startsWith("§7[烹饪食材]") || loreLine.startsWith("§7[食物]") || loreLine.startsWith("§7[药水]")) {
                // 找到旧的类型行，替换为新内容喵
                lore.set(loreIdx, ingredientLine);
                hasIngredientLine = true;
                break;
            }
        }
        if (!hasIngredientLine) {
            // 没有找到旧类型行，直接追加喵
            lore.add(ingredientLine);
        }

        // 可变标签：保质期随配置变化，每次刷新喵
        String shelfLifeLine = "§8保质期: " + formatShelfLife(shelfLifeMinutes);

        // 可变标签：过期状态随时间变化，每次刷新喵
        long diffMinutes = (System.currentTimeMillis() - nowMs) / 60000L;
        // 喵~防御：diffMinutes为负（时钟回拨）视为未过期喵
        boolean expired = diffMinutes >= shelfLifeMinutes;

        // 生产日期不可变：首次写入后不再更新喵
        boolean hasProductionLine = false;
        for (int loreIdx = 0; loreIdx < lore.size(); loreIdx++) {
            if (lore.get(loreIdx).startsWith("§8生产日期:")) {
                hasProductionLine = true;
                break;
            }
        }
        if (!hasProductionLine) {
            // 首次写入生产日期喵
            lore.add("§8生产日期: " + formatTimestamp(nowMs));
        }

        // 替换或追加保质期固定行喵
        boolean hasShelfLifeLine = false;
        for (int loreIdx = 0; loreIdx < lore.size(); loreIdx++) {
            if (lore.get(loreIdx).startsWith("§8保质期:")) {
                lore.set(loreIdx, shelfLifeLine);
                hasShelfLifeLine = true;
                break;
            }
        }
        if (!hasShelfLifeLine) {
            lore.add(shelfLifeLine);
        }

        // 处理过期：首行插入/移除"§c已过期"标记，并修改displayName喵
        String expiredMark = "§c已过期";
        boolean hasExpiredMark = !lore.isEmpty() && lore.get(0).equals(expiredMark);
        if (expired && !hasExpiredMark) {
            // 过期时在lore最前插入红色过期标记喵
            lore.add(0, expiredMark);
        } else if (!expired && hasExpiredMark) {
            // 未过期但有旧过期标记，移除喵
            lore.remove(0);
        }

        // 处理过期时displayName追加§7(过期)喵
        String displayName = meta.hasDisplayName() ? meta.getDisplayName() : null;
        if (expired) {
            if (displayName != null && !displayName.endsWith("§7(过期)")) {
                // 追加过期后缀喵
                meta.setDisplayName(displayName + "§7(过期)");
            } else if (displayName == null) {
                // 原版物品无displayName，用本地化译名兜底并追加喵
                String localizedName = getLocalizedName(item);
                meta.setDisplayName(localizedName + "§7(过期)");
            }
        } else if (displayName != null && displayName.endsWith("§7(过期)")) {
            // 未过期时移除过期后缀喵
            meta.setDisplayName(displayName.substring(0, displayName.length() - "§7(过期)".length()));
        }

        // 把修改后的lore和meta写回物品喵
        meta.setLore(lore);
        item.setItemMeta(meta);
        return true;
    }

    /**
     * 根据时间戳生成已存放时间的描述文字喵~
     * 输入：timestampMs - 食材被标记时的毫秒时间戳
     * 输出：时间描述字符串，例如 "2分钟前"/"刚刚"
     */
    static String buildTimeDesc(long timestampMs) {
        // 计算距今的毫秒差值喵
        long diffMs = System.currentTimeMillis() - timestampMs;
        // 喵~防御：差值为负（时钟回拨等异常情况），视为刚刚标记喵
        if (diffMs < 0) diffMs = 0;

        long totalSeconds = diffMs / 1000L;
        long totalMinutes = totalSeconds / 60L;
        long totalHours   = totalMinutes / 60L;
        long days         = totalHours / 24L;
        long hours        = totalHours % 24L;

        if (totalMinutes < 1) {
            return "刚刚";
        } else if (totalHours < 1) {
            return totalMinutes + "分钟前";
        } else if (days < 1) {
            return totalHours + "小时前";
        } else if (hours == 0) {
            return days + "天前";
        } else {
            return days + "天" + hours + "小时前";
        }
    }

    // 兼容旧调用，委托给新方法喵
    static String buildFreshnessLore(long timestampMs) {
        return "§8生产日期: " + buildTimeDesc(timestampMs);
    }

    /**
     * 将毫秒时间戳格式化为"xxxx年xx月xx日 xx时xx分xx秒"喵~
     */
    static String formatTimestamp(long timestampMs) {
        java.time.LocalDateTime dt = java.time.LocalDateTime.ofInstant(
                java.time.Instant.ofEpochMilli(timestampMs),
                java.time.ZoneId.systemDefault());
        return String.format("%d年%02d月%02d日 %02d时%02d分%02d秒",
                dt.getYear(), dt.getMonthValue(), dt.getDayOfMonth(),
                dt.getHour(), dt.getMinute(), dt.getSecond());
    }

    /**
     * 将保质期分钟数转换为可读单位字符串，最小单位分钟，自动升级到小时/天喵~
     * 例：10→"10分钟"，90→"1小时30分钟"，1440→"1天"，1500→"1天1小时"
     */
    static String formatShelfLife(int totalMinutes) {
        // 喵~防御：负数或0视为无效，返回0分钟喵
        if (totalMinutes <= 0) return "0分钟";
        int days    = totalMinutes / 1440; // 1天=1440分钟喵
        int hours   = (totalMinutes % 1440) / 60;
        int minutes = totalMinutes % 60;
        StringBuilder sb = new StringBuilder();
        if (days > 0)    sb.append(days).append("天");
        if (hours > 0)   sb.append(hours).append("小时");
        if (minutes > 0) sb.append(minutes).append("分钟");
        return sb.toString();
    }

    // 将食材状态枚举转为中文显示名喵
    private static String translateState(String state) {
        return switch (state) {
            case "WHOLE"  -> "整块";
            case "SLICED" -> "切片";
            case "DICED"  -> "切丁";
            case "SAUCE"  -> "酱料";
            default       -> state;
        };
    }

    /**
     * 获取物品的本地化译名：优先Slimefun物品名，其次用zh_cn.json查原版中文译名，兜底英文枚举名喵
     * 输入：ItemStack物品。
     * 输出：本地化后的显示名称字符串。
     */
    private static String getLocalizedName(ItemStack item) {
        // 喵~防御：item为null时返回空字符串喵
        if (item == null) return "";
        // 优先：Slimefun物品的自定义名称喵
        SlimefunItem sfItem = SlimefunItem.getByItem(item);
        if (sfItem != null) {
            String sfName = sfItem.getItemName();
            if (sfName != null && !sfName.isEmpty()) return sfName;
        }
        // 其次：原版材质的中文译名（从zh_cn.json懒加载查找）喵
        String vanillaName = VanillaLocalization.getItemName(item.getType());
        if (vanillaName != null && !vanillaName.isEmpty()) return vanillaName;
        // 兜底：英文枚举名喵
        return item.getType().name();
    }

    /**
     * 原版材质中文译名懒加载工具类，从jar内zh_cn.json读取喵
     */
    private static class VanillaLocalization {
        private static final java.util.Map<String, String> CACHE = new java.util.HashMap<>();
        private static boolean loaded = false;

        static String getItemName(org.bukkit.Material mat) {
            if (!loaded) load();
            return CACHE.get(mat.name());
        }

        private static void load() {
            loaded = true;
            try (java.io.InputStream in = FoodTagListener.class.getResourceAsStream("/zh_cn.json")) {
                if (in == null) return;
                com.google.gson.JsonObject root = new com.google.gson.JsonParser()
                        .parse(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8))
                        .getAsJsonObject();
                for (java.util.Map.Entry<String, com.google.gson.JsonElement> entry : root.entrySet()) {
                    String key = entry.getKey();
                    if (key.startsWith("item.minecraft.") || key.startsWith("block.minecraft.")) {
                        // 将 "item.minecraft.apple" 转为 "APPLE" 枚举名喵
                        String matName = key.substring(key.lastIndexOf('.') + 1).toUpperCase(java.util.Locale.ENGLISH);
                        CACHE.put(matName, entry.getValue().getAsString());
                    }
                }
            } catch (Exception ignored) {
                // 喵~防御：zh_cn.json不存在或解析失败时静默忽略，使用兜底名称喵
            }
        }
    }

    private String resolveIngredientId(ItemStack item) {
        // 喵~防御：只调用一次getItemMeta()避免重复创建ItemMeta副本
        ItemMeta itemMeta = item.getItemMeta();
        if (itemMeta != null) {
            PersistentDataContainer pdc = itemMeta.getPersistentDataContainer();
            String id = pdc.get(CookingKeys.INGREDIENT_ID, PersistentDataType.STRING);
            if (id != null && ingredients.containsKey(id)) return id;

            String sfId = pdc.get(KEY_SF_ITEM, PersistentDataType.STRING);
            if (sfId != null) {
                SlimefunItem sfItem = SlimefunItem.getById(sfId);
                if (sfItem != null && ingredients.containsKey(sfItem.getId())) return sfItem.getId();
            }
        }
        String matName = item.getType().name();
        if (ingredients.containsKey(matName)) return matName;

        // ===== fallback：食材配置里没有时，检查原版食物和药水 =====
        Material mat = item.getType();

        // 喵~防御：检查是否是药水类型（不受黑名单约束，药水不在黑名单中）喵
        if (mat == Material.POTION || mat == Material.SPLASH_POTION || mat == Material.LINGERING_POTION) {
            // 药水类物品返回通用药水标识符，保质期默认5分钟喵
            return GENERIC_POTION_ID;
        }

        // 检查是否是可食用原版物品，且不在黑名单内喵
        // 喵~防御：isEdible()返回true才进一步判断，避免对非食物打标签喵
        if (mat.isEdible() && !BLACKLIST.contains(mat)) {
            // 原版可食用物品（排除黑名单）返回通用食物标识符，保质期默认10分钟喵
            return GENERIC_FOOD_ID;
        }

        // 既不是配置食材、也不是药水、也不是可食用原版物品，返回null跳过喵
        return null;
    }
}
