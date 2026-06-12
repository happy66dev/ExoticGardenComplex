package io.github.thebusybiscuit.exoticgarden.cooking.item;

import io.github.thebusybiscuit.exoticgarden.cooking.CookingKeys;
import io.github.thebusybiscuit.exoticgarden.cooking.block.CuttingBoardBlock;
import io.github.thebusybiscuit.exoticgarden.cooking.config.IngredientConfig;
import io.github.thebusybiscuit.exoticgarden.cooking.state.FoodState;
import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;

public class SpatulaItem extends SlimefunItem {

    private final Map<String, IngredientConfig.IngredientData> ingredients;

    public SpatulaItem(ItemGroup group, SlimefunItemStack item,
                       RecipeType recipeType, ItemStack[] recipe,
                       JavaPlugin plugin,
                       Map<String, IngredientConfig.IngredientData> ingredients) {
        super(group, item, recipeType, recipe);
        this.ingredients = ingredients;

        plugin.getServer().getPluginManager().registerEvents(new org.bukkit.event.Listener() {
            @org.bukkit.event.EventHandler
            public void onInteract(PlayerInteractAtEntityEvent event) {
                if (event.getHand() != EquipmentSlot.HAND) return;
                Entity target = event.getRightClicked();
                Location boardLoc = findBoardLoc(target);
                if (boardLoc == null) return;

                Player player = event.getPlayer();
                ItemStack hand = player.getInventory().getItemInMainHand();
                if (hand.getItemMeta() == null) return;
                PersistentDataContainer handPdc = hand.getItemMeta().getPersistentDataContainer();
                if (!"SPATULA".equals(handPdc.get(CookingKeys.ITEM_TYPE, PersistentDataType.STRING))) return;

                event.setCancelled(true);

                if (!(target instanceof ArmorStand stand)) return;
                ItemStack held = stand.getEquipment().getHelmet();
                if (held == null || held.getType().isAir()) return;

                org.bukkit.inventory.meta.ItemMeta heldMeta = held.getItemMeta();
                if (heldMeta == null) return;
                PersistentDataContainer heldPdc = heldMeta.getPersistentDataContainer();

                String rawState = heldPdc.get(CookingKeys.FOOD_STATE, PersistentDataType.STRING);
                FoodState current = FoodState.WHOLE;
                if (rawState != null) {
                    try { current = FoodState.valueOf(rawState); } catch (IllegalArgumentException ignored) {}
                }
                if (current != FoodState.WHOLE) return;

                String ingId = heldPdc.get(CookingKeys.INGREDIENT_ID, PersistentDataType.STRING);
                if (ingId == null) return;
                IngredientConfig.IngredientData data = ingredients.get(ingId);
                if (data == null || data.sauceCreation == null) return;

                int clicks = heldPdc.getOrDefault(CookingKeys.SPATULA_CLICKS, PersistentDataType.INTEGER, 0) + 1;
                if (clicks >= data.sauceCreation.clicksRequired) {
                    heldPdc.set(CookingKeys.FOOD_STATE, PersistentDataType.STRING, FoodState.SAUCE.name());
                    heldPdc.remove(CookingKeys.SPATULA_CLICKS);
                    player.sendMessage("§a已制成酱料！");
                } else {
                    heldPdc.set(CookingKeys.SPATULA_CLICKS, PersistentDataType.INTEGER, clicks);
                    player.sendMessage("§e搅拌中: " + clicks + "/" + data.sauceCreation.clicksRequired);
                }
                held.setItemMeta(heldMeta);
                stand.getEquipment().setHelmet(held);
            }
        }, plugin);
    }

    private static Location findBoardLoc(Entity entity) {
        for (Map.Entry<Location, Entity> entry : CuttingBoardBlock.boardDisplays.entrySet()) {
            if (entry.getValue().equals(entity)) return entry.getKey();
        }
        return null;
    }
}
