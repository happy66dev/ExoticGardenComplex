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
        if (!(e.getWhoClicked() instanceof org.bukkit.entity.Player)) return;
        if (e.getClickedInventory() == null) return;
        if (e.getClickedInventory().getType() == InventoryType.PLAYER
                || e.getClickedInventory().getType() == InventoryType.CRAFTING
                || e.getClickedInventory().getType() == InventoryType.CREATIVE) {
            ItemStack cursor = e.getCursor() != null ? e.getCursor().clone() : null;
            if (tagIfIngredient(cursor)) e.setCursor(cursor);
            ItemStack current = e.getCurrentItem() != null ? e.getCurrentItem().clone() : null;
            if (tagIfIngredient(current)) e.setCurrentItem(current);
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
        // 获取食材状态显示名（初始为整块）喵
        String stateDisplay = translateState("WHOLE");
        // 从Bukkit食物组件读取营养度（饱食度+饱和度），用反射兼容旧编译依赖喵
        String nutritionDisplay = "";
        try {
            // Paper 1.21+ API：Material#getFoodComponent()，旧版返回null喵
            var foodComp = item.getType().getClass().getMethod("getFoodComponent")
                    .invoke(item.getType());
            if (foodComp != null) {
                // 饱食度恢复量喵
                int nutrition = (int) foodComp.getClass().getMethod("getNutrition").invoke(foodComp);
                // 实际饱和度 = nutrition * saturationModifier * 2喵
                float satMod = (float) foodComp.getClass().getMethod("getSaturationModifier").invoke(foodComp);
                double total = Math.round((nutrition + nutrition * satMod * 2f) * 10.0) / 10.0;
                nutritionDisplay = " §e营养度: " + total;
            }
        } catch (Exception ignored) {
            // 喵~防御：API不存在或非食物物品时静默忽略喵
        }
        lore.add("§7[烹饪食材] §f" + stateDisplay + nutritionDisplay);
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
