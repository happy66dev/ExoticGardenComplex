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

    private boolean tagIfIngredient(ItemStack item) {
        if (item == null || item.getType().isAir()) return false;
        String ingId = resolveIngredientId(item);
        if (ingId == null) return false;

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        if (pdc.has(CookingKeys.FOOD_STATE, PersistentDataType.STRING)) return false;

        pdc.set(CookingKeys.FOOD_STATE, PersistentDataType.STRING, "WHOLE");
        if (!pdc.has(CookingKeys.INGREDIENT_ID, PersistentDataType.STRING)) {
            pdc.set(CookingKeys.INGREDIENT_ID, PersistentDataType.STRING, ingId);
        }

        List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
        // 从食材配置读取饱食度+饱和度，未配置时为0喵
        IngredientConfig.IngredientData data = ingredients.get(ingId);
        double nutrition = data != null ? data.foodPoints + data.saturation : 0;
        String nutritionStr = nutrition > 0
                ? " §e营养度: " + (Math.round(nutrition * 10.0) / 10.0)
                : "";
        lore.add("§7[烹饪食材] §f" + translateState("WHOLE") + nutritionStr);
        meta.setLore(lore);

        item.setItemMeta(meta);
        return true;
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
