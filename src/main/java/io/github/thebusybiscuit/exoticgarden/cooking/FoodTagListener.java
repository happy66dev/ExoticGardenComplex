package io.github.thebusybiscuit.exoticgarden.cooking;

import io.github.thebusybiscuit.exoticgarden.cooking.config.IngredientConfig;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCreativeEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class FoodTagListener implements Listener {

    private static final org.bukkit.NamespacedKey KEY_SF_ITEM =
            new org.bukkit.NamespacedKey("slimefun", "slimefun_item");

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

    @EventHandler(ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof org.bukkit.entity.Player player)) return;
        // cursor 跟随鼠标，任何情况下都扫喵
        ItemStack cursor = e.getCursor() != null ? e.getCursor().clone() : null;
        if (tagIfIngredient(cursor)) e.setCursor(cursor);
        // currentItem：点击的格子物品喵
        ItemStack current = e.getCurrentItem() != null ? e.getCurrentItem().clone() : null;
        if (tagIfIngredient(current)) e.setCurrentItem(current);
        // shift+click 会直接把物品转移进背包，延迟1tick扫玩家背包兜底喵
        if (e.isShiftClick()) {
            org.bukkit.Bukkit.getScheduler().runTaskLater(
                io.github.thebusybiscuit.exoticgarden.ExoticGarden.getInstance(),
                () -> {
                    for (int i = 0; i < player.getInventory().getSize(); i++) {
                        ItemStack it = player.getInventory().getItem(i);
                        if (tagIfIngredient(it)) player.getInventory().setItem(i, it);
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

        // 首次标记时写入食材状态"WHOLE"和食材ID喵
        if (!pdc.has(CookingKeys.FOOD_STATE, PersistentDataType.STRING)) {
            // 首次打标签，设置整块状态喵
            pdc.set(CookingKeys.FOOD_STATE, PersistentDataType.STRING, "WHOLE");
        }
        if (!pdc.has(CookingKeys.INGREDIENT_ID, PersistentDataType.STRING)) {
            // 首次打标签，写入食材ID喵
            pdc.set(CookingKeys.INGREDIENT_ID, PersistentDataType.STRING, ingId);
        }

        // 每次调用都更新时间戳，记录本次标记时刻（毫秒）喵
        long nowMs = System.currentTimeMillis(); // 当前时间戳，单位：毫秒喵
        pdc.set(CookingKeys.FOOD_TIMESTAMP, PersistentDataType.LONG, nowMs);

        // 构建lore列表，已有lore则复制一份可修改的副本喵
        List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();

        // 从食材配置读取饱食度+饱和度，未配置时为0喵
        IngredientConfig.IngredientData data = ingredients.get(ingId);
        double nutrition = data != null ? data.foodPoints + data.saturation : 0;
        String nutritionStr = nutrition > 0
                ? " §e营养度: " + (Math.round(nutrition * 10.0) / 10.0)
                : "";
        // 烹饪食材标签行内容喵
        String ingredientLine = "§7[烹饪食材] §f" + translateState("WHOLE") + nutritionStr;

        // 检查lore中是否已有烹饪食材行，有则替换，没有则追加喵
        boolean hasIngredientLine = false; // 标记是否已找到并替换了食材行喵
        for (int loreIdx = 0; loreIdx < lore.size(); loreIdx++) {
            if (lore.get(loreIdx).startsWith("§7[烹饪食材]")) {
                // 找到旧的食材行，替换为新内容喵
                lore.set(loreIdx, ingredientLine);
                hasIngredientLine = true;
                break;
            }
        }
        if (!hasIngredientLine) {
            // 没有找到旧食材行，直接追加喵
            lore.add(ingredientLine);
        }

        // 生成保质期显示文字，格式："§8保质期: X天X小时前" 或 "§8保质期: 刚刚"喵
        String freshnesLine = buildFreshnessLore(nowMs); // 保质期lore行文字喵

        // 在lore中查找已有保质期行（以"§8保质期:"开头），找到则替换，没有则追加喵
        boolean hasFreshnessLine = false; // 标记是否已找到并替换了保质期行喵
        for (int loreIdx = 0; loreIdx < lore.size(); loreIdx++) {
            if (lore.get(loreIdx).startsWith("§8保质期:")) {
                // 找到旧的保质期行，替换为新时间喵
                lore.set(loreIdx, freshnesLine);
                hasFreshnessLine = true;
                break;
            }
        }
        if (!hasFreshnessLine) {
            // 没有找到旧保质期行，直接追加喵
            lore.add(freshnesLine);
        }

        // 把修改后的lore和meta写回物品喵
        meta.setLore(lore);
        item.setItemMeta(meta);
        return true;
    }

    /**
     * 根据时间戳生成已存放时间的lore文字喵~
     * 输入：timestampMs - 食材/菜肴被标记时的毫秒时间戳
     * 输出：带颜色代码的lore字符串，例如 "§8保质期: 2天3小时前"
     * 边界：刚刚打标签时（差值<1分钟）显示"刚刚"喵
     */
    static String buildFreshnessLore(long timestampMs) {
        // 计算距今的毫秒差值喵
        long diffMs = System.currentTimeMillis() - timestampMs; // 经过的时间，单位：毫秒喵
        // 喵~防御：差值为负（时钟回拨等异常情况），视为刚刚标记喵
        if (diffMs < 0) diffMs = 0;

        long totalSeconds = diffMs / 1000L; // 转换为秒喵
        long totalMinutes = totalSeconds / 60L; // 转换为分钟喵
        long totalHours   = totalMinutes / 60L; // 转换为小时喵
        long days         = totalHours / 24L;   // 转换为天数喵
        long hours        = totalHours % 24L;   // 剩余不足一天的小时数喵

        // 根据时长范围拼装显示文字喵
        String timeDesc; // 已存放时间的文字描述喵
        if (totalMinutes < 1) {
            // 不足1分钟，显示"刚刚"喵
            timeDesc = "刚刚";
        } else if (totalHours < 1) {
            // 不足1小时，只显示分钟喵
            timeDesc = totalMinutes + "分钟前";
        } else if (days < 1) {
            // 不足1天，只显示小时喵
            timeDesc = totalHours + "小时前";
        } else if (hours == 0) {
            // 整天，只显示天数喵
            timeDesc = days + "天前";
        } else {
            // 超过1天，显示天+小时喵
            timeDesc = days + "天" + hours + "小时前";
        }

        // 返回带颜色代码的保质期lore行喵
        return "§8保质期: " + timeDesc;
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

    private String resolveIngredientId(ItemStack item) {
        if (item.getItemMeta() != null) {
            PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
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
        return null;
    }
}
