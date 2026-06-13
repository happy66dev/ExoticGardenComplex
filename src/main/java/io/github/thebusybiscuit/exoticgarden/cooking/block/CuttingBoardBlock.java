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
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.EulerAngle;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class CuttingBoardBlock extends SlimefunItem {

    public static final Map<Location, ArmorStand> boardDisplays = new ConcurrentHashMap<>();

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
            ArmorStand stand = boardDisplays.get(loc);

            if (!player.isSneaking()) {
                ItemStack hand = player.getInventory().getItemInMainHand();
                if (hand.getType() == Material.AIR) return;

                if (stand == null) {
                    if (loc.getWorld() == null) return;
                    ItemStack toPlace = ensureIngredientId(hand.clone());
                    ArmorStand spawned = spawnStand(loc, toPlace);
                    if (spawned == null) return;
                    boardDisplays.put(loc, spawned);
                    hand.setAmount(hand.getAmount() - 1);
                } else {
                    player.sendMessage("§c砧板上已有物品，请潜行右键取回");
                }
            } else {
                if (stand != null) {
                    ItemStack helmet = stand.getEquipment().getHelmet();
                    if (helmet != null && !helmet.getType().isAir()) {
                        ItemStack stored = helmet.clone();
                        Map<Integer, ItemStack> leftover = player.getInventory().addItem(stored);
                        if (!leftover.isEmpty() && loc.getWorld() != null) {
                            leftover.values().forEach(it -> loc.getWorld().dropItemNaturally(loc, it));
                        }
                    }
                    stand.remove();
                    boardDisplays.remove(loc);
                }
            }
        };
    }

    private ArmorStand spawnStand(Location loc, ItemStack item) {
        if (loc.getWorld() == null) return null;
        Location spawnLoc = loc.clone().add(0.5, -0.3, 0.5);
        ArmorStand stand = (ArmorStand) loc.getWorld().spawnEntity(spawnLoc, EntityType.ARMOR_STAND);
        stand.setVisible(false);
        stand.setGravity(false);
        stand.setMarker(true);
        stand.setArms(false);
        stand.setBasePlate(false);
        stand.setCollidable(false);
        stand.getEquipment().setHelmet(item);
        stand.setHeadPose(new EulerAngle(0, 0, 0));
        PersistentDataContainer pdc = stand.getPersistentDataContainer();
        pdc.set(CookingKeys.BOARD_ITEM, PersistentDataType.STRING, "true");
        return stand;
    }

    private static ItemStack ensureIngredientId(ItemStack item) {
        if (item.getItemMeta() == null) return item;
        org.bukkit.inventory.meta.ItemMeta meta = item.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        if (!pdc.has(CookingKeys.INGREDIENT_ID, PersistentDataType.STRING)) {
            io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem sfItem =
                io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem.getByItem(item);
            String id = sfItem != null ? sfItem.getId() : item.getType().name();
            pdc.set(CookingKeys.INGREDIENT_ID, PersistentDataType.STRING, id);
        }
        if (!pdc.has(CookingKeys.FOOD_STATE, PersistentDataType.STRING)) {
            pdc.set(CookingKeys.FOOD_STATE, PersistentDataType.STRING, "WHOLE");
        }
        item.setItemMeta(meta);
        return item;
    }

    private BlockBreakHandler buildBreakHandler() {
        return new BlockBreakHandler(false, false) {
            @Override
            public void onPlayerBreak(@Nonnull BlockBreakEvent e,
                                      @Nonnull ItemStack item,
                                      @Nonnull List<ItemStack> drops) {
                Location loc = e.getBlock().getLocation();
                ArmorStand stand = boardDisplays.remove(loc);
                if (stand != null) {
                    ItemStack stored = stand.getEquipment().getHelmet();
                    if (stored != null && !stored.getType().isAir() && loc.getWorld() != null) {
                        loc.getWorld().dropItemNaturally(loc, stored);
                    }
                    stand.remove();
                }
            }
        };
    }

    public static ItemStack getStoredItem(Location boardLoc) {
        ArmorStand stand = boardDisplays.get(boardLoc);
        if (stand == null) return null;
        ItemStack item = stand.getEquipment().getHelmet();
        return (item == null || item.getType().isAir()) ? null : item.clone();
    }

    public static void setStoredItem(Location boardLoc, ItemStack item) {
        ArmorStand stand = boardDisplays.get(boardLoc);
        if (stand == null) return;
        stand.getEquipment().setHelmet(item);
    }

    private static class BoardProtectionListener implements Listener {

        @EventHandler(ignoreCancelled = true)
        public void onEntityDamage(EntityDamageByEntityEvent e) {
            if (!(e.getEntity() instanceof ArmorStand stand)) return;
            if (!stand.getPersistentDataContainer().has(CookingKeys.BOARD_ITEM, PersistentDataType.STRING)) return;
            e.setCancelled(true);
        }

        @EventHandler(ignoreCancelled = true)
        public void onArmorStandManipulate(PlayerArmorStandManipulateEvent e) {
            if (!e.getRightClicked().getPersistentDataContainer().has(CookingKeys.BOARD_ITEM, PersistentDataType.STRING)) return;
            e.setCancelled(true);
        }
    }
}
