package io.github.thebusybiscuit.exoticgarden;

import io.github.thebusybiscuit.exoticgarden.cooking.CookingKeys;
import io.github.thebusybiscuit.exoticgarden.cooking.config.IngredientConfig;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.libraries.dough.items.CustomItemStack;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;

import java.util.Map;

public class FoodListener implements Listener {
    // 过期反胃debuff持续时间：5秒 = 100 tick喵
    private static final int EXPIRED_NAUSEA_TICKS = 100;

    final ExoticGarden plugin;
    // 食材配置表，用于查询保质期喵
    private final Map<String, IngredientConfig.IngredientData> ingredients;

    public FoodListener(ExoticGarden plugin, Map<String, IngredientConfig.IngredientData> ingredients) {
        this.plugin = plugin;
        this.ingredients = ingredients;
        // 喵~不在构造函数中自注册，由CookingModule.initialize()统一注册，避免双重注册导致事件处理两次
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onUse(final PlayerInteractEvent e) {
        SlimefunItem item;
        if (e.getPlayer().getFoodLevel() >= 20)
            return;
        EquipmentSlot hand = e.getHand();
        if (hand == null) {
            return;
        }

        // 喵~防御：右键方块时不触发食用（防止与灶台、砧板等交互冲突）喵
        if (e.getAction() == Action.RIGHT_CLICK_BLOCK) return;

        switch (hand) {
            case HAND:
                item = SlimefunItem.getByItem(new CustomItemStack(e.getPlayer().getInventory().getItemInMainHand(), 1));
                if (item instanceof EGPlant && ((EGPlant) item).isEdible()) {
                    ItemStack handItem = e.getPlayer().getInventory().getItemInMainHand();
                    // 喵~过期食材：饱食度100%减少60%，30%概率额外触发debuff喵
                    if (isExpired(handItem)) {
                        double basePoints = ((EGPlant) item).getEdibleHunger();
                        int reduced = (int) Math.max(0, Math.round(basePoints * 0.4));
                        int newFood = Math.min(e.getPlayer().getFoodLevel() + reduced, 20);
                        e.getPlayer().setFoodLevel(newFood);
                        e.getPlayer().sendMessage("§c这食材已经过期了 吃了感觉很不舒服喵~");
                        // 喵~30%概率触发debuff喵
                        if (java.util.concurrent.ThreadLocalRandom.current().nextInt(100) < 30) {
                            io.github.thebusybiscuit.exoticgarden.cooking.DishConsumptionListener.applyExpiredEffectsStatic(e.getPlayer(), false);
                        }
                        // 扣除物品喵
                        Bukkit.getScheduler().scheduleSyncDelayedTask(this.plugin, () -> {
                            var a = e.getPlayer().getInventory().getItemInMainHand();
                            a.setAmount(a.getAmount() - 1);
                            e.getPlayer().getInventory().setItemInMainHand(a.getAmount() == 0
                                ? new ItemStack(org.bukkit.Material.AIR) : a);
                        }, 0L);
                        break;
                    }
                    ((EGPlant) item).restoreHunger(e.getPlayer());
                    e.getPlayer().getWorld().playSound(e.getPlayer().getEyeLocation(), Sound.ENTITY_GENERIC_EAT, 1.0F, 1.0F);
                    Bukkit.getScheduler().scheduleSyncDelayedTask(this.plugin, () -> {
                        var a = e.getPlayer().getInventory().getItemInMainHand();
                        a.setAmount(a.getAmount() - 1);
                        e.getPlayer().getInventory().setItemInMainHand(a);
                    }, 0L);
                }
                break;


            case OFF_HAND:
                item = SlimefunItem.getByItem(new CustomItemStack(e.getPlayer().getInventory().getItemInOffHand(), 1));
                if (item instanceof EGPlant && ((EGPlant) item).isEdible()) {
                    ItemStack offItem = e.getPlayer().getInventory().getItemInOffHand();
                    // 喵~过期食材：饱食度100%减少60%，30%概率额外触发debuff喵
                    if (isExpired(offItem)) {
                        double basePoints = ((EGPlant) item).getEdibleHunger();
                        int reduced = (int) Math.max(0, Math.round(basePoints * 0.4));
                        int newFood = Math.min(e.getPlayer().getFoodLevel() + reduced, 20);
                        e.getPlayer().setFoodLevel(newFood);
                        e.getPlayer().sendMessage("§c这食材已经过期了 吃了感觉很不舒服喵~");
                        // 喵~30%概率触发debuff喵
                        if (java.util.concurrent.ThreadLocalRandom.current().nextInt(100) < 30) {
                            io.github.thebusybiscuit.exoticgarden.cooking.DishConsumptionListener.applyExpiredEffectsStatic(e.getPlayer(), false);
                        }
                        Bukkit.getScheduler().scheduleSyncDelayedTask(this.plugin, () -> {
                            var a = e.getPlayer().getInventory().getItemInOffHand();
                            a.setAmount(a.getAmount() - 1);
                            e.getPlayer().getInventory().setItemInOffHand(a.getAmount() == 0
                                ? new ItemStack(org.bukkit.Material.AIR) : a);
                        }, 0L);
                        break;
                    }
                    ((EGPlant) item).restoreHunger(e.getPlayer());
                    e.getPlayer().getWorld().playSound(e.getPlayer().getEyeLocation(), Sound.ENTITY_GENERIC_EAT, 1.0F, 1.0F);
                    Bukkit.getScheduler().scheduleSyncDelayedTask(this.plugin, () -> {
                        var a = e.getPlayer().getInventory().getItemInOffHand();
                        a.setAmount(a.getAmount() - 1);
                        e.getPlayer().getInventory().setItemInOffHand(a);
                    }, 0L);
                }
                break;
        }
    }

    /**
     * 判断物品是否已过期：统一委托烹饪模块的 PDC 与配置判定服务喵
     * 输入：待食用或待种植的物品。
     * 输出：true=已达到真实配置保质期，false=未标记、无期限或未过期。
     */
    private boolean isExpired(ItemStack item) {
        // 获取所有食用与设备路径共享的实时过期判定服务喵
        io.github.thebusybiscuit.exoticgarden.cooking.FoodExpiryService foodExpiryService =
                io.github.thebusybiscuit.exoticgarden.cooking.CookingModule.getFoodExpiryService();
        // 喵~防御：烹饪模块尚未完成初始化时保守放行，避免插件启用期间空指针异常喵~
        if (foodExpiryService == null) {
            // 返回未过期结果，等待模块完成初始化后的正常事件处理喵~
            return false;
        }
        // 以 PDC 生产时间和当前配置实时判断，不依赖可能滞后60秒的 lore 喵~
        return foodExpiryService.isExpired(item);
    }


    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        // 读取本次实际放置后的方块材质，避免把普通可放置食物误认成作物喵~
        Material placedMaterial = e.getBlockPlaced().getType();
        // 已过期的受管理可种植作物禁止落地，防止收获时获得新生产时间戳喵~
        if (isPlantableCrop(placedMaterial) && isExpired(e.getItemInHand())) {
            // 取消放置以保留原物品的过期状态喵~
            e.setCancelled(true);
            // 向玩家说明该批作物不能通过种植刷新保质期喵~
            e.getPlayer().sendMessage("§c这份作物已经过期，不能种植来刷新保质期喵~");
            // 已处理过期种植后不再执行旧头颅保护判断喵~
            return;
        }
        // 读取 Slimefun 物品以保留旧版头颅植物放置保护喵~
        SlimefunItem item = SlimefunItem.getByItem(e.getItemInHand());
        // 原有头颅植物无论是否带时间戳都禁止通过原版放置路径落地喵~
        if (item instanceof EGPlant && e.getItemInHand().getType() == Material.PLAYER_HEAD) {
            // 取消旧版不支持的头颅放置操作喵~
            e.setCancelled(true);
        }
    }

    // 判断实际放置方块是否属于可由食物物品种下的作物喵~
    private boolean isPlantableCrop(Material placedMaterial) {
        // 喵~防御：未知方块材质不能安全归类为作物喵~
        if (placedMaterial == null) {
            // 返回非作物结果喵~
            return false;
        }
        // 返回原版可由食物或种子直接种下的作物方块匹配结果喵~
        return placedMaterial == Material.WHEAT
                || placedMaterial == Material.CARROTS
                || placedMaterial == Material.POTATOES
                || placedMaterial == Material.BEETROOTS
                || placedMaterial == Material.NETHER_WART
                || placedMaterial == Material.COCOA
                || placedMaterial == Material.SWEET_BERRY_BUSH
                || placedMaterial == Material.TORCHFLOWER_CROP
                || placedMaterial == Material.PITCHER_CROP;
    }

    @EventHandler
    public void onEquip(InventoryClickEvent e) {
        if (e.getSlotType() != InventoryType.SlotType.ARMOR)
            return;
        SlimefunItem item = SlimefunItem.getByItem(e.getCursor());
        if (item instanceof EGPlant && e.getCursor().getType() == Material.PLAYER_HEAD)
            e.setCancelled(true);
    }
}


