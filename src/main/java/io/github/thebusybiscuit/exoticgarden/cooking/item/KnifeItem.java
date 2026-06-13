package io.github.thebusybiscuit.exoticgarden.cooking.item;

import io.github.thebusybiscuit.exoticgarden.cooking.CookingKeys;
import io.github.thebusybiscuit.exoticgarden.cooking.block.CuttingBoardBlock;
import io.github.thebusybiscuit.exoticgarden.cooking.state.FoodState;
import io.github.thebusybiscuit.slimefun4.api.events.PlayerRightClickEvent;
import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.core.handlers.BlockUseHandler;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;

public class KnifeItem extends SlimefunItem {

    public KnifeItem(ItemGroup group, SlimefunItemStack item,
                     RecipeType recipeType, ItemStack[] recipe, JavaPlugin plugin) {
        super(group, item, recipeType, recipe);

        addItemHandler((BlockUseHandler) (PlayerRightClickEvent e) -> {
            e.cancel();
        });

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
                if (!"KNIFE".equals(handPdc.get(CookingKeys.ITEM_TYPE, PersistentDataType.STRING))) return;

                event.setCancelled(true);

                ItemStack held = CuttingBoardBlock.getStoredItem(boardLoc);
                if (held == null) return;

                if (player.isSneaking()) {
                    Map<Integer, ItemStack> leftover = player.getInventory().addItem(held);
                    if (!leftover.isEmpty() && boardLoc.getWorld() != null) {
                        leftover.values().forEach(it -> boardLoc.getWorld().dropItemNaturally(boardLoc, it));
                    }
                    target.remove();
                    CuttingBoardBlock.boardDisplays.remove(boardLoc);
                    return;
                }

                org.bukkit.inventory.meta.ItemMeta heldMeta = held.getItemMeta();
                if (heldMeta == null) return;
                PersistentDataContainer heldPdc = heldMeta.getPersistentDataContainer();
                String rawState = heldPdc.get(CookingKeys.FOOD_STATE, PersistentDataType.STRING);
                FoodState current = FoodState.WHOLE;
                if (rawState != null) {
                    try { current = FoodState.valueOf(rawState); } catch (IllegalArgumentException ignored) {}
                }
                FoodState next = advanceState(current);
                heldPdc.set(CookingKeys.FOOD_STATE, PersistentDataType.STRING, next.name());
                held.setItemMeta(heldMeta);
                CuttingBoardBlock.setStoredItem(boardLoc, held);
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
        for (Map.Entry<Location, ItemFrame> entry : CuttingBoardBlock.boardDisplays.entrySet()) {
            if (entry.getValue().equals(entity)) return entry.getKey();
        }
        return null;
    }
}
