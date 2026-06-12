package io.github.thebusybiscuit.exoticgarden.cooking.item;

import io.github.thebusybiscuit.exoticgarden.cooking.block.CuttingBoardBlock;
import io.github.thebusybiscuit.exoticgarden.cooking.state.FoodState;
import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
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

public class KnifeItem extends SlimefunItem {

    private static final NamespacedKey KEY_FOOD_STATE = new NamespacedKey("cooking", "food_state");
    private static final NamespacedKey KEY_ITEM_TYPE = new NamespacedKey("cooking", "item_type");

    public KnifeItem(ItemGroup group, SlimefunItemStack item,
                     RecipeType recipeType, ItemStack[] recipe, JavaPlugin plugin) {
        super(group, item, recipeType, recipe);

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
                if (!"KNIFE".equals(handPdc.get(KEY_ITEM_TYPE, PersistentDataType.STRING))) return;

                event.setCancelled(true);

                if (!(target instanceof ArmorStand stand)) return;
                ItemStack held = stand.getEquipment().getHelmet();
                if (held == null || held.getType().isAir()) return;

                if (player.isSneaking()) {
                    Map<Integer, ItemStack> leftover = player.getInventory().addItem(held.clone());
                    if (!leftover.isEmpty() && boardLoc.getWorld() != null) {
                        leftover.values().forEach(it -> boardLoc.getWorld().dropItemNaturally(boardLoc, it));
                    }
                    stand.getEquipment().setHelmet(null);
                    CuttingBoardBlock.boardDisplays.remove(boardLoc);
                    target.remove();
                    return;
                }

                org.bukkit.inventory.meta.ItemMeta heldMeta = held.getItemMeta();
                if (heldMeta == null) return;
                PersistentDataContainer heldPdc = heldMeta.getPersistentDataContainer();
                String rawState = heldPdc.get(KEY_FOOD_STATE, PersistentDataType.STRING);
                FoodState current = FoodState.WHOLE;
                if (rawState != null) {
                    try { current = FoodState.valueOf(rawState); } catch (IllegalArgumentException ignored) {}
                }
                FoodState next = advanceState(current);
                heldPdc.set(KEY_FOOD_STATE, PersistentDataType.STRING, next.name());
                held.setItemMeta(heldMeta);
                stand.getEquipment().setHelmet(held);
                player.sendMessage("§a食材状态: " + next.name());
            }
        }, plugin);
    }

    private FoodState advanceState(FoodState current) {
        return switch (current) {
            case WHOLE -> FoodState.SLICED;
            case SLICED -> FoodState.DICED;
            default -> current;
        };
    }

    private Location findBoardLoc(Entity entity) {
        for (Map.Entry<Location, Entity> entry : CuttingBoardBlock.boardDisplays.entrySet()) {
            if (entry.getValue().equals(entity)) return entry.getKey();
        }
        return null;
    }
}
