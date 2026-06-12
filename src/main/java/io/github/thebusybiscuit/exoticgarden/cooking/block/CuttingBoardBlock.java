package io.github.thebusybiscuit.exoticgarden.cooking.block;

import io.github.thebusybiscuit.exoticgarden.cooking.CookingKeys;
import io.github.thebusybiscuit.slimefun4.api.events.PlayerRightClickEvent;
import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.core.handlers.BlockBreakHandler;
import io.github.thebusybiscuit.slimefun4.core.handlers.BlockUseHandler;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class CuttingBoardBlock extends SlimefunItem {

    public static final Map<Location, ItemFrame> boardDisplays = new ConcurrentHashMap<>();

    public CuttingBoardBlock(ItemGroup group, SlimefunItemStack item,
                             RecipeType recipeType, ItemStack[] recipe,
                             JavaPlugin plugin) {
        super(group, item, recipeType, recipe);
        addItemHandler(buildUseHandler(), buildBreakHandler());
        plugin.getServer().getPluginManager().registerEvents(new BoardProtectionListener(), plugin);
    }

    private BlockUseHandler buildUseHandler() {
        return (PlayerRightClickEvent e) -> {
            e.cancel();
            if (e.getClickedBlock().isEmpty()) return;
            Player player = e.getPlayer();
            Location loc = e.getClickedBlock().get().getLocation();
            ItemFrame frame = boardDisplays.get(loc);

            if (!player.isSneaking()) {
                ItemStack hand = player.getInventory().getItemInMainHand();
                if (hand.getType() == Material.AIR) return;

                if (frame == null) {
                    if (loc.getWorld() == null) return;
                    ItemFrame spawned = spawnFrame(loc, hand.clone());
                    if (spawned == null) return;
                    boardDisplays.put(loc, spawned);
                    hand.setAmount(hand.getAmount() - 1);
                } else {
                    player.sendMessage("§c砧板上已有物品，请潜行右键取回");
                }
            } else {
                if (frame != null) {
                    ItemStack stored = frame.getItem().clone();
                    if (!stored.getType().isAir()) {
                        Map<Integer, ItemStack> leftover = player.getInventory().addItem(stored);
                        if (!leftover.isEmpty() && loc.getWorld() != null) {
                            leftover.values().forEach(it -> loc.getWorld().dropItemNaturally(loc, it));
                        }
                    }
                    frame.remove();
                    boardDisplays.remove(loc);
                }
            }
        };
    }

    private ItemFrame spawnFrame(Location loc, ItemStack item) {
        if (loc.getWorld() == null) return null;
        Location spawnLoc = loc.clone().add(0.5, 0.5, 0.5);
        ItemFrame frame = (ItemFrame) loc.getWorld().spawnEntity(spawnLoc, EntityType.ITEM_FRAME);
        frame.setFacingDirection(BlockFace.UP, true);
        frame.setFixed(true);
        frame.setVisible(false);
        frame.setItem(item);
        PersistentDataContainer pdc = frame.getPersistentDataContainer();
        pdc.set(CookingKeys.BOARD_ITEM, PersistentDataType.STRING, "true");
        return frame;
    }

    private BlockBreakHandler buildBreakHandler() {
        return new BlockBreakHandler(false, false) {
            @Override
            public void onPlayerBreak(@Nonnull BlockBreakEvent e,
                                      @Nonnull ItemStack item,
                                      @Nonnull List<ItemStack> drops) {
                Location loc = e.getBlock().getLocation();
                ItemFrame frame = boardDisplays.remove(loc);
                if (frame != null) {
                    ItemStack stored = frame.getItem();
                    if (stored != null && !stored.getType().isAir() && loc.getWorld() != null) {
                        loc.getWorld().dropItemNaturally(loc, stored);
                    }
                    frame.remove();
                }
            }
        };
    }

    public static ItemStack getStoredItem(Location boardLoc) {
        ItemFrame frame = boardDisplays.get(boardLoc);
        if (frame == null) return null;
        ItemStack item = frame.getItem();
        return item.getType().isAir() ? null : item.clone();
    }

    public static void setStoredItem(Location boardLoc, ItemStack item) {
        ItemFrame frame = boardDisplays.get(boardLoc);
        if (frame == null) return;
        frame.setItem(item);
    }

    private static class BoardProtectionListener implements Listener {

        @EventHandler(ignoreCancelled = true)
        public void onHangingBreak(HangingBreakByEntityEvent e) {
            if (!(e.getEntity() instanceof ItemFrame frame)) return;
            if (!frame.getPersistentDataContainer().has(CookingKeys.BOARD_ITEM, PersistentDataType.STRING)) return;
            e.setCancelled(true);
        }

        @EventHandler(ignoreCancelled = true)
        public void onEntityDamage(EntityDamageByEntityEvent e) {
            if (!(e.getEntity() instanceof ItemFrame frame)) return;
            if (!frame.getPersistentDataContainer().has(CookingKeys.BOARD_ITEM, PersistentDataType.STRING)) return;
            e.setCancelled(true);
        }

        @EventHandler(ignoreCancelled = true)
        public void onPlayerInteract(PlayerInteractEntityEvent e) {
            if (e.getHand() != EquipmentSlot.HAND) return;
            if (!(e.getRightClicked() instanceof ItemFrame frame)) return;
            if (!frame.getPersistentDataContainer().has(CookingKeys.BOARD_ITEM, PersistentDataType.STRING)) return;
            e.setCancelled(true);
        }
    }
}
