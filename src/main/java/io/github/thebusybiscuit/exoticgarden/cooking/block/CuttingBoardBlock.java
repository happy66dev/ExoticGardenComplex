package io.github.thebusybiscuit.exoticgarden.cooking.block;

import io.github.thebusybiscuit.slimefun4.api.events.PlayerRightClickEvent;
import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.core.handlers.BlockBreakHandler;
import io.github.thebusybiscuit.slimefun4.core.handlers.BlockUseHandler;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class CuttingBoardBlock extends SlimefunItem {

    public static final Map<Location, Entity> boardDisplays = new ConcurrentHashMap<>();

    private static final NamespacedKey KEY_BOARD_ITEM = new NamespacedKey("cooking", "board_item");

    public CuttingBoardBlock(ItemGroup group, SlimefunItemStack item,
                             RecipeType recipeType, ItemStack[] recipe) {
        super(group, item, recipeType, recipe);
        addItemHandler(buildUseHandler(), buildBreakHandler());
    }

    private BlockUseHandler buildUseHandler() {
        return (PlayerRightClickEvent e) -> {
            e.cancel();
            if (e.getClickedBlock().isEmpty()) return;
            Player player = e.getPlayer();
            Location loc = e.getClickedBlock().get().getLocation();
            Entity display = boardDisplays.get(loc);

            if (!player.isSneaking()) {
                ItemStack hand = player.getInventory().getItemInMainHand();
                if (hand.getType() == Material.AIR) return;

                if (display == null) {
                    if (loc.getWorld() == null) return;
                    Entity spawned = spawnDisplay(loc, hand.clone());
                    if (spawned == null) return;
                    boardDisplays.put(loc, spawned);
                    hand.setAmount(hand.getAmount() - 1);
                } else {
                    player.sendMessage("§c砧板上已有物品，请潜行右键取回");
                }
            } else {
                if (display != null) {
                    ItemStack stored = getStoredItem(display);
                    if (stored != null) {
                        Map<Integer, ItemStack> leftover = player.getInventory().addItem(stored);
                        if (!leftover.isEmpty() && loc.getWorld() != null) {
                            leftover.values().forEach(it -> loc.getWorld().dropItemNaturally(loc, it));
                        }
                    }
                    display.remove();
                    boardDisplays.remove(loc);
                }
            }
        };
    }

    private Entity spawnDisplay(Location loc, ItemStack item) {
        if (loc.getWorld() == null) return null;
        Location spawnLoc = loc.clone().add(0.5, 1.0, 0.5);
        ArmorStand stand = (ArmorStand) loc.getWorld().spawnEntity(spawnLoc, EntityType.ARMOR_STAND);
        stand.setVisible(false);
        stand.setGravity(false);
        stand.setInvulnerable(true);
        stand.setSmall(true);
        stand.getEquipment().setHelmet(item);
        PersistentDataContainer pdc = stand.getPersistentDataContainer();
        pdc.set(KEY_BOARD_ITEM, PersistentDataType.STRING, "true");
        return stand;
    }

    private ItemStack getStoredItem(Entity display) {
        if (display instanceof ArmorStand stand) {
            return stand.getEquipment().getHelmet();
        }
        return null;
    }

    private BlockBreakHandler buildBreakHandler() {
        return new BlockBreakHandler(false, false) {
            @Override
            public void onPlayerBreak(@Nonnull BlockBreakEvent e,
                                      @Nonnull ItemStack item,
                                      @Nonnull List<ItemStack> drops) {
                Location loc = e.getBlock().getLocation();
                Entity display = boardDisplays.remove(loc);
                if (display != null) {
                    ItemStack stored = getStoredItem(display);
                    if (stored != null && loc.getWorld() != null) {
                        loc.getWorld().dropItemNaturally(loc, stored);
                    }
                    display.remove();
                }
            }
        };
    }
}
