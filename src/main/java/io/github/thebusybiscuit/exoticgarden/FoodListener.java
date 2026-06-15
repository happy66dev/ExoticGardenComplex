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
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
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

        switch (hand) {
            case HAND:
                item = SlimefunItem.getByItem(new CustomItemStack(e.getPlayer().getInventory().getItemInMainHand(), 1));
                if (item instanceof EGPlant && ((EGPlant) item).isEdible()) {
                    ItemStack handItem = e.getPlayer().getInventory().getItemInMainHand();
                    // 喵~防御：过期食材禁止食用，给反胃debuff提示喵
                    if (isExpired(handItem)) {
                        e.getPlayer().addPotionEffect(new PotionEffect(VersionedPotionEffectType.CONFUSION, EXPIRED_NAUSEA_TICKS, 4));
                        e.getPlayer().sendMessage("§c这食材已经过期了，吃了感觉很不舒服喵~");
                        // 手动扣除物品（食用失败也消耗）喵
                        Bukkit.getScheduler().scheduleSyncDelayedTask(this.plugin, () -> {
                            var a = e.getPlayer().getInventory().getItemInMainHand();
                            a.setAmount(a.getAmount() - 1);
                            e.getPlayer().getInventory().setItemInMainHand(a);
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
                    // 喵~防御：过期食材禁止食用，给反胃debuff提示喵
                    if (isExpired(offItem)) {
                        e.getPlayer().addPotionEffect(new PotionEffect(VersionedPotionEffectType.CONFUSION, EXPIRED_NAUSEA_TICKS, 4));
                        e.getPlayer().sendMessage("§c这食材已经过期了，吃了感觉很不舒服喵~");
                        Bukkit.getScheduler().scheduleSyncDelayedTask(this.plugin, () -> {
                            var a = e.getPlayer().getInventory().getItemInOffHand();
                            a.setAmount(a.getAmount() - 1);
                            e.getPlayer().getInventory().setItemInOffHand(a);
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
     * 判断物品是否已过期：从PDC读取INGREDIENT_ID+FOOD_TIMESTAMP，查询保质期配置喵
     * 输入：itemStack物品。
     * 输出：true=已过期或无时间戳时返回false（视为未过期）。
     */
    private boolean isExpired(ItemStack item) {
        // 喵~防御：item为null或无meta时视为未过期喵
        if (item == null || !item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        // 喵~防御：meta为null时视为未过期喵
        if (meta == null) return false;
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        // 无时间戳的物品视为未过期（没经过烹饪系统标记的食物）喵
        Long timestamp = pdc.get(CookingKeys.FOOD_TIMESTAMP, PersistentDataType.LONG);
        if (timestamp == null) return false;
        // 读取食材ID查询保质期配置喵
        String ingId = pdc.get(CookingKeys.INGREDIENT_ID, PersistentDataType.STRING);
        IngredientConfig.IngredientData data = ingId != null ? ingredients.get(ingId) : null;
        int shelfLifeMinutes = data != null ? data.shelfLifeMinutes : 10; // 默认10分钟喵
        // 计算从生产到现在经过的分钟数喵
        long diffMinutes = (System.currentTimeMillis() - timestamp) / 60000L;
        // 喵~防御：diffMinutes为负（时钟回拨）视为未过期喵
        return diffMinutes >= shelfLifeMinutes;
    }


    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        SlimefunItem item = SlimefunItem.getByItem(e.getItemInHand());
        if (item instanceof EGPlant && e.getItemInHand().getType() == Material.PLAYER_HEAD)
            e.setCancelled(true);
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


