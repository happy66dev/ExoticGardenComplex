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
    // 喵~保存插件实例引用，用于调用YAML持久化方法
    private final io.github.thebusybiscuit.exoticgarden.ExoticGarden pluginInstance;

    public CuttingBoardBlock(ItemGroup group, SlimefunItemStack item,
                             RecipeType recipeType, ItemStack[] recipe,
                             JavaPlugin plugin) {
        super(group, item, recipeType, recipe);
        this.pluginInstance = (io.github.thebusybiscuit.exoticgarden.ExoticGarden) plugin;
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

                // 喵~防御：菜肴物品不能放到砧板上，防止PDC被污染喵
                if (hand.getItemMeta() != null
                        && hand.getItemMeta().getPersistentDataContainer()
                            .has(CookingKeys.DISH_HUNGER, PersistentDataType.INTEGER)) {
                    player.sendMessage("§c菜肴不能放到砧板上喵~");
                    return;
                }

                if (stand == null) {
                    if (loc.getWorld() == null) return;
                    ItemStack toPlace = ensureIngredientId(hand.clone());
                    toPlace.setAmount(1); // 喵~防御：只放置1个，避免clone保留原始数量导致取回时数量翻倍
                    ArmorStand spawned = spawnStand(loc, toPlace);
                    if (spawned == null) return;
                    boardDisplays.put(loc, spawned);
                    hand.setAmount(hand.getAmount() - 1);
                    // 喵~放置物品后，保存到YAML持久化
                    saveCuttingBoardToYaml();
                } else {
                    player.sendMessage("§c砧板上已有物品，请潜行右键取回");
                }
            } else {
                if (stand != null) {
                    ItemStack helmet = stand.getEquipment().getHelmet();
                    if (helmet != null && !helmet.getType().isAir()) {
                        ItemStack stored = helmet.clone();
                        // 喵~取回前刷新 lore（状态/营养值/克重），仅对已知食材执行喵
                        org.bukkit.inventory.meta.ItemMeta storedMeta = stored.getItemMeta();
                        if (storedMeta != null) {
                            org.bukkit.persistence.PersistentDataContainer storedPdc = storedMeta.getPersistentDataContainer();
                            String rawState = storedPdc.get(CookingKeys.FOOD_STATE, org.bukkit.persistence.PersistentDataType.STRING);
                            String ingId = storedPdc.get(CookingKeys.INGREDIENT_ID, org.bukkit.persistence.PersistentDataType.STRING);
                            var ingredients = io.github.thebusybiscuit.exoticgarden.cooking.CookingModule.getIngredients();
                            // 喵~防御：只有 ingredients map 里有该食材配置才刷 lore，防止普通物品（碗等）被写入食材 lore 喵
                            if (rawState != null && ingId != null && ingredients != null && ingredients.containsKey(ingId)) {
                                io.github.thebusybiscuit.exoticgarden.cooking.state.FoodState fs = io.github.thebusybiscuit.exoticgarden.cooking.state.FoodState.WHOLE;
                                try { fs = io.github.thebusybiscuit.exoticgarden.cooking.state.FoodState.valueOf(rawState); } catch (IllegalArgumentException ignored) {}
                                io.github.thebusybiscuit.exoticgarden.cooking.item.KnifeItem.refreshIngredientLore(storedMeta, fs, storedPdc, ingredients);
                                stored.setItemMeta(storedMeta);
                            }
                        }
                        Map<Integer, ItemStack> leftover = player.getInventory().addItem(stored);
                        if (!leftover.isEmpty() && loc.getWorld() != null) {
                            leftover.values().forEach(it -> loc.getWorld().dropItemNaturally(loc, it));
                        }
                    }
                    stand.remove();
                    boardDisplays.remove(loc);
                    // 喵~取走物品后，保存到YAML持久化
                    saveCuttingBoardToYaml();
                }
            }
        };
    }

    /**
     * 判断实体是否为本插件的砧板展示盔甲架喵~
     * 输入：entity-待检查实体
     * 输出：带砧板PDC标记的ArmorStand时返回true
     * 边界：null或其他实体类型安全返回false喵
     */
    public static boolean isBoardDisplay(org.bukkit.entity.Entity entity) {
        // 喵~防御：仅允许带明确PDC标记的ArmorStand参与恢复与清理喵
        return entity instanceof ArmorStand stand
                && stand.getPersistentDataContainer().has(CookingKeys.BOARD_ITEM, PersistentDataType.STRING);
    }

    /**
     * 根据砧板展示盔甲架的位置反推所在砧板方块坐标喵~
     * 输入：stand-带砧板标记的盔甲架
     * 输出：对应方块位置；世界不存在时返回null
     */
    public static Location getBoardLocation(ArmorStand stand) {
        // 喵~防御：实体或世界为空时无法安全构造方块位置喵
        if (stand == null || stand.getWorld() == null) return null;
        Location spawnLocation = stand.getLocation();
        return new Location(spawnLocation.getWorld(), Math.floor(spawnLocation.getX()),
                Math.floor(spawnLocation.getY() + 0.3), Math.floor(spawnLocation.getZ()));
    }

    /**
     * 在砧板位置创建带PDC标记的展示盔甲架喵~
     * 输入：loc-砧板方块位置，item-展示物品
     * 输出：创建成功的ArmorStand；参数无效时返回null
     */
    public static ArmorStand spawnBoardDisplay(Location loc, ItemStack item) {
        // 喵~防御：世界为空、物品为空或物品为空气时禁止生成无效展示实体喵
        if (loc == null || loc.getWorld() == null || item == null || item.getType().isAir()) return null;
        Location spawnLoc = loc.clone().add(0.5, -0.9, 0.5);
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

    // 保留旧私有入口，统一委托共享展示生成方法，避免放置和恢复逻辑分叉喵
    private static ArmorStand spawnStand(Location loc, ItemStack item) {
        return spawnBoardDisplay(loc, item);
    }

    private static ItemStack ensureIngredientId(ItemStack item) {
        if (item.getItemMeta() == null) return item;
        org.bukkit.inventory.meta.ItemMeta meta = item.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        // 喵~防御：菜肴物品（有DISH_HUNGER标记）不能打食材标签，直接返回原物品喵
        if (pdc.has(CookingKeys.DISH_HUNGER, PersistentDataType.INTEGER)) return item;

        // 喵~防御：只有在食材配置里的物品才打标签，普通物品（碗、工具等）不打标签直接返回喵~
        if (!pdc.has(CookingKeys.INGREDIENT_ID, PersistentDataType.STRING)) {
            io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem sfItem =
                io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem.getByItem(item);
            // 喵~与 ItemIdUtil.toKey() 格式保持一致：SF物品用 slimefun:ID，原版用 minecraft:MATERIAL 喵
            String id = sfItem != null
                ? "slimefun:" + sfItem.getId()
                : "minecraft:" + item.getType().name();

            // 喵~检查这个物品是否在食材配置里，不在的话不打标签直接返回喵~
            var ingredients = io.github.thebusybiscuit.exoticgarden.cooking.CookingModule.getIngredients();
            if (ingredients == null || !ingredients.containsKey(id)) {
                return item; // 喵~不是食材，原样返回，不打PDC标签喵~
            }

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
                // 喵~破坏砧板后，从缓存移除并保存到YAML
                ArmorStand stand = boardDisplays.remove(loc);
                saveCuttingBoardToYaml();
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

    // 喵~辅助方法：将setStoredItem也触发YAML保存，因为KnifeItem/SpatulaItem可能通过这个方法更新砧板物品
    public static void setStoredItem(Location boardLoc, ItemStack item) {
        ArmorStand stand = boardDisplays.get(boardLoc);
        if (stand == null) return;
        stand.getEquipment().setHelmet(item);
        // 喵~物品更新后也要同步到YAML，确保切割等操作也能持久化
        saveCuttingBoardToYaml();
    }

    // 喵~将当前所有砧板数据保存到storge.yml的CuttingBoards section
    public static void saveCuttingBoardToYaml() {
        io.github.thebusybiscuit.exoticgarden.ExoticGarden plugin = io.github.thebusybiscuit.exoticgarden.ExoticGarden.getInstance();
        // 喵~防御：插件实例不存在时跳过保存
        if (plugin == null) return;
        plugin.saveCuttingBoards();
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
