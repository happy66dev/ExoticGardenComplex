package io.github.thebusybiscuit.exoticgarden.cooking.item;

import io.github.thebusybiscuit.exoticgarden.cooking.CookingKeys;
import io.github.thebusybiscuit.exoticgarden.cooking.block.CuttingBoardBlock;
import io.github.thebusybiscuit.exoticgarden.cooking.state.FoodState;
import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
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

        plugin.getServer().getPluginManager().registerEvents(new org.bukkit.event.Listener() {

            @EventHandler(priority = EventPriority.LOW, ignoreCancelled = false)
            public void onInteract(PlayerInteractEvent e) {
                if (e.getHand() != EquipmentSlot.HAND) return;
                if (e.getAction() != Action.RIGHT_CLICK_BLOCK && e.getAction() != Action.RIGHT_CLICK_AIR) return;

                Player player = e.getPlayer();
                ItemStack hand = player.getInventory().getItemInMainHand();
                if (hand.getItemMeta() == null) return;
                PersistentDataContainer handPdc = hand.getItemMeta().getPersistentDataContainer();
                if (!"KNIFE".equals(handPdc.get(CookingKeys.ITEM_TYPE, PersistentDataType.STRING))) return;

                Location boardLoc = findNearbyBoard(player);
                if (boardLoc == null) return;

                e.setCancelled(true);

                ItemStack held = CuttingBoardBlock.getStoredItem(boardLoc);
                if (held == null) return;

                if (player.isSneaking()) {
                    Map<Integer, ItemStack> leftover = player.getInventory().addItem(held);
                    if (!leftover.isEmpty() && boardLoc.getWorld() != null) {
                        leftover.values().forEach(it -> boardLoc.getWorld().dropItemNaturally(boardLoc, it));
                    }
                    ArmorStand stand = CuttingBoardBlock.boardDisplays.remove(boardLoc);
                    if (stand != null) stand.remove();
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
                if (next == current) return;
                heldPdc.set(CookingKeys.FOOD_STATE, PersistentDataType.STRING, next.name());
                held.setItemMeta(heldMeta);
                CuttingBoardBlock.setStoredItem(boardLoc, held);
                player.sendMessage("§a食材状态: " + stateDisplayName(next));
            }

            @EventHandler(priority = EventPriority.LOW)
            public void onInteractEntity(PlayerInteractAtEntityEvent event) {
                if (event.getHand() != EquipmentSlot.HAND) return;
                if (!(event.getRightClicked() instanceof ArmorStand stand)) return;
                if (!stand.getPersistentDataContainer().has(CookingKeys.BOARD_ITEM, PersistentDataType.STRING)) return;
                event.setCancelled(true);
            }
        }, plugin);
    }

    private Location findNearbyBoard(Player player) {
        Location ploc = player.getLocation();
        Location best = null;
        double bestDist = Double.MAX_VALUE;
        for (Map.Entry<Location, ArmorStand> entry : CuttingBoardBlock.boardDisplays.entrySet()) {
            Location bloc = entry.getKey();
            if (bloc.getWorld() == null || !bloc.getWorld().equals(ploc.getWorld())) continue;
            double d = ploc.distanceSquared(bloc.clone().add(0.5, 0.5, 0.5));
            if (d <= 9.0 && d < bestDist) { bestDist = d; best = bloc; }
        }
        return best;
    }

    private FoodState advanceState(FoodState current) {
        return switch (current) {
            case WHOLE -> FoodState.SLICED;
            case SLICED -> FoodState.DICED;
            default -> current;
        };
    }

    private String stateDisplayName(FoodState state) {
        return switch (state) {
            case WHOLE -> "完整";
            case SLICED -> "切片";
            case DICED -> "切丁";
            case SAUCE -> "酱汁";
        };
    }
}
