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

        // ===== 分支2：已标记烹饪食材（有FOOD_STATE标记）=====
        if (pdc.has(CookingKeys.FOOD_STATE, PersistentDataType.STRING)) {
            handleIngredientConsume(e, item, meta, pdc);
        }
        // 其他物品不处理喵
    }

    /**
     * 处理自定义菜肴消费：检查过期 → 过期给debuff取消恢复，不过期正常恢复喵~
     */
    private void handleDishConsume(PlayerItemConsumeEvent e, ItemStack item, ItemMeta meta, PersistentDataContainer pdc) {
        long nowMs = System.currentTimeMillis();

        // 读取上次时间戳判断过期喵（菜肴使用DISH_HUNGER，保质期固定为-1=永不过期）
        Long timestamp = pdc.get(CookingKeys.FOOD_TIMESTAMP, PersistentDataType.LONG);
        // 菜肴没有shelfLifeMinutes配置，暂时不过期（后续可扩展）喵
        // 更新时间戳喵
        pdc.set(CookingKeys.FOOD_TIMESTAMP, PersistentDataType.LONG, nowMs);

        Integer hungerRaw = pdc.get(CookingKeys.DISH_HUNGER, PersistentDataType.INTEGER);
        if (hungerRaw == null) return;
        int hunger = Math.max(0, Math.min(hungerRaw, 20));
        Double saturationRaw = pdc.getOrDefault(CookingKeys.DISH_SATURATION, PersistentDataType.DOUBLE, 0.8);
        double saturation = Math.max(0, Math.min(saturationRaw, 20.0));

        // 喵~防御：取消原版消耗事件，手动处理饱食度恢复+药水效果，避免干扰第三方插件
        e.setCancelled(true);
        // 手动扣除物品喵
        Player player = e.getPlayer();
        ItemStack mainHand = player.getInventory().getItemInMainHand();
        if (mainHand.isSimilar(item) && mainHand.getAmount() > 0) {
            mainHand.setAmount(mainHand.getAmount() - 1);
            player.getInventory().setItemInMainHand(mainHand.getAmount() == 0
                    ? new ItemStack(org.bukkit.Material.AIR) : mainHand);
        }

        // 更新lore中的生产日期行喵
        List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
        String prodLine = "§8生产日期: " + FoodTagListener.formatTimestamp(nowMs);
        replaceLoreLine(lore, "§8生产日期:", prodLine);
        meta.setLore(lore);
        item.setItemMeta(meta);

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

