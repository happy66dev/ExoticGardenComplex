package io.github.thebusybiscuit.exoticgarden.cooking;

import io.github.thebusybiscuit.exoticgarden.cooking.config.IngredientConfig;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
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
        lore.add("§7[烹饪食材]");
        meta.setLore(lore);

        item.setItemMeta(meta);
        return true;
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
