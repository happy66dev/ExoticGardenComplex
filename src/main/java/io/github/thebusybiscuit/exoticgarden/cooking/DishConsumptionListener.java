package io.github.thebusybiscuit.exoticgarden.cooking;

import io.github.thebusybiscuit.exoticgarden.VersionedPotionEffectType;
import io.github.thebusybiscuit.exoticgarden.cooking.config.IngredientConfig;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class DishConsumptionListener implements Listener {

    // 过期debuff持续时间：5秒 = 100 tick喵
    private static final int EXPIRED_NAUSEA_TICKS = 100;

    private final Map<String, IngredientConfig.IngredientData> ingredients;

    public DishConsumptionListener(Map<String, IngredientConfig.IngredientData> ingredients) {
        this.ingredients = ingredients;
    }

    /**
     * 监听玩家消耗物品事件，统一处理自定义菜肴和已标记烹饪食材喵~
     * 逻辑：先刷新时间戳，判断过期 → 过期则取消饱食度恢复+给反胃debuff，不过期走原始行为喵
     */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onConsume(PlayerItemConsumeEvent e) {
        ItemStack item = e.getItem();
        ItemMeta meta = item.getItemMeta();
        // 喵~防御：meta为null时无法读取PDC，直接跳过喵
        if (meta == null) return;

        PersistentDataContainer pdc = meta.getPersistentDataContainer();

        // ===== 分支1：自定义菜肴（有DISH_HUNGER标记）=====
        if (pdc.has(CookingKeys.DISH_HUNGER, PersistentDataType.INTEGER)) {
            handleDishConsume(e, item, meta, pdc);
            return;
        }

        // ===== 分支2：有保质期标记的食物（有FOOD_TIMESTAMP标记，包含原版食物/药水/烹饪食材）=====
        if (pdc.has(CookingKeys.FOOD_TIMESTAMP, PersistentDataType.LONG)) {
            handleIngredientConsume(e, item, meta, pdc);
        }
        // 其他物品不处理喵
    }

    /**
     * 处理自定义菜肴消费：检查过期 → 过期给debuff取消恢复，不过期正常恢复喵~
     */
    private void handleDishConsume(PlayerItemConsumeEvent e, ItemStack item, ItemMeta meta, PersistentDataContainer pdc) {
        // 喵~防御：取消原版消耗事件，手动处理喵
        e.setCancelled(true);
        Player player = e.getPlayer();

        Integer servingsLeft = pdc.get(CookingKeys.DISH_SERVINGS_REMAINING, PersistentDataType.INTEGER);
        int remaining = servingsLeft != null ? servingsLeft : 1;

        Integer hungerRaw = pdc.get(CookingKeys.DISH_HUNGER, PersistentDataType.INTEGER);
        if (hungerRaw == null) return;
        int hunger = Math.max(0, Math.min(hungerRaw, 20));
        Double saturationRaw = pdc.getOrDefault(CookingKeys.DISH_SATURATION, PersistentDataType.DOUBLE, 0.0);
        double saturation = Math.max(0, Math.min(saturationRaw, 20.0));

        // 恢复饱食度喵
        int newFood = Math.min(player.getFoodLevel() + hunger, 20);
        float newSat = (float) Math.min(player.getSaturation() + saturation, newFood);
        player.setFoodLevel(newFood);
        player.setSaturation(newSat);

        // 应用药水效果喵
        String effectsRaw = pdc.get(CookingKeys.DISH_EFFECTS, PersistentDataType.STRING);
        if (effectsRaw != null && !effectsRaw.isEmpty()) {
            for (String effectStr : effectsRaw.split("\\|")) {
                String[] parts = effectStr.trim().split(":");
                String effectName = parts[0].toUpperCase().replace(" ", "_").replace("-", "_");
                int duration  = parts.length > 1 ? parseInt(parts[1], 200) : 200;
                int amplifier = parts.length > 2 ? parseInt(parts[2], 0)   : 0;
                PotionEffectType type = PotionEffectType.getByName(effectName);
                if (type != null) {
                    player.addPotionEffect(new PotionEffect(type, duration, amplifier));
                }
            }
        }

        // 扣减剩余次数喵
        remaining--;
        ItemStack mainHand = player.getInventory().getItemInMainHand();
        if (remaining <= 0) {
            // 喵~食用完毕：物品变成空碗喵
            ItemStack bowl = new ItemStack(org.bukkit.Material.BOWL, 1);
            if (mainHand.isSimilar(item)) {
                player.getInventory().setItemInMainHand(bowl);
            }
        } else {
            // 喵~更新剩余次数写回 PDC 和 lore喵
            pdc.set(CookingKeys.DISH_SERVINGS_REMAINING, PersistentDataType.INTEGER, remaining);
            // 刷新 lore 第一行的份量显示喵
            if (meta.hasLore()) {
                List<String> lore = new ArrayList<>(meta.getLore());
                for (int i = 0; i < lore.size(); i++) {
                    if (lore.get(i).contains("§7份量:")) {
                        lore.set(i, lore.get(i).replaceAll("§7份量: §f\\d+", "§7份量: §f" + remaining));
                        break;
                    }
                }
                meta.setLore(lore);
            }
            item.setItemMeta(meta);
            // 喵~不扣物品数量（setAmount），meta 已更新喵
        }
    }

    /**
     * 处理已标记烹饪食材消费：刷新时间戳 → 检查过期 → 过期取消原版饱食度恢复+给反胃debuff喵~
     */
    private void handleIngredientConsume(PlayerItemConsumeEvent e, ItemStack item, ItemMeta meta, PersistentDataContainer pdc) {
        long nowMs = System.currentTimeMillis();

        // 获取保质期配置（从食材配置读取）喵
        String ingId = pdc.get(CookingKeys.INGREDIENT_ID, PersistentDataType.STRING);
        IngredientConfig.IngredientData data = ingId != null ? ingredients.get(ingId) : null;
        int shelfLifeMinutes = data != null ? data.shelfLifeMinutes : 10;

        // 读取原始时间戳，用于过期判断喵
        Long oldTimestamp = pdc.get(CookingKeys.FOOD_TIMESTAMP, PersistentDataType.LONG);

        // 刷新时间戳为当前时刻喵（更新生产日期lore）
        pdc.set(CookingKeys.FOOD_TIMESTAMP, PersistentDataType.LONG, nowMs);

        // 判断是否过期：用旧时间戳判断（食用前的时间），而不是刚写入的nowMs喵
        boolean expired = false;
        if (oldTimestamp != null) {
            long diffMinutes = (nowMs - oldTimestamp) / 60000L;
            expired = diffMinutes >= shelfLifeMinutes;
        }

        if (expired) {
            // 过期：取消本次消费事件（不恢复饱食度），给反胃debuff喵
            e.setCancelled(true);
            // 取消后物品不会被消耗，手动扣除1个喵
            Player player = e.getPlayer();
            ItemStack mainHand = player.getInventory().getItemInMainHand();
            if (mainHand.isSimilar(item) && mainHand.getAmount() > 0) {
                mainHand.setAmount(mainHand.getAmount() - 1);
                player.getInventory().setItemInMainHand(mainHand.getAmount() == 0
                        ? new ItemStack(org.bukkit.Material.AIR) : mainHand);
            }
            // 给予反胃debuff 5秒喵
            player.addPotionEffect(new PotionEffect(VersionedPotionEffectType.CONFUSION, EXPIRED_NAUSEA_TICKS, 4));
            player.sendMessage("§c这食材已经过期了，吃了感觉很不舒服喵~");
        }
        // 不过期时什么都不做，让原版饱食度恢复正常进行喵
    }

    /**
     * 在lore列表中查找以prefix开头的行，找到则替换为newLine，找不到则追加喵~
     */
    static void replaceLoreLine(List<String> lore, String prefix, String newLine) {
        for (int i = 0; i < lore.size(); i++) {
            if (lore.get(i).startsWith(prefix)) {
                lore.set(i, newLine);
                return;
            }
        }
        lore.add(newLine);
    }

    private int parseInt(String s, int def) {
        try { return Integer.parseInt(s.trim()); } catch (NumberFormatException ex) { return def; }
    }
}

