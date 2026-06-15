package io.github.thebusybiscuit.exoticgarden.cooking;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
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

public class DishConsumptionListener implements Listener {

    /**
     * 监听玩家消耗物品事件，处理自定义菜肴的饥饿值、饱和度、药水效果喵~
     * 复杂逻辑说明：
     *   - 只处理PDC中含有DISH_HUNGER标记的物品（自定义菜肴）
     *   - 消耗时写入FOOD_TIMESTAMP时间戳，并在lore中更新保质期行
     *   - hunger/saturation都做了0~20范围钳制，防止溢出喵
     */
    @EventHandler(ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent e) {
        ItemStack item = e.getItem();
        ItemMeta meta = item.getItemMeta();
        // 喵~防御：meta为null时无法读取PDC，直接跳过喵
        if (meta == null) return;

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        // 不含DISH_HUNGER说明不是自定义菜肴，跳过喵
        if (!pdc.has(CookingKeys.DISH_HUNGER, PersistentDataType.INTEGER)) return;

        Integer hungerRaw = pdc.get(CookingKeys.DISH_HUNGER, PersistentDataType.INTEGER);
        // 喵~防御：hungerRaw读取失败时跳过，避免NPE喵
        if (hungerRaw == null) return;
        // 钳制hunger值在0~20之间，防止越界喵
        int hunger = Math.max(0, Math.min(hungerRaw, 20)); // 处理后的饥饿恢复值，单位：格喵

        // 读取饱和度，未配置时默认0.8喵
        Double saturationRaw = pdc.getOrDefault(CookingKeys.DISH_SATURATION, PersistentDataType.DOUBLE, 0.8);
        // 钳制saturation在0.0~20.0之间喵
        double saturation = Math.max(0, Math.min(saturationRaw, 20.0)); // 处理后的饱和度值喵

        // 将原物品替换为空气，等效消耗掉菜肴喵
        e.setItem(new ItemStack(org.bukkit.Material.AIR));

        // 写入FOOD_TIMESTAMP时间戳，记录菜肴被消耗时的时刻喵
        long nowMs = System.currentTimeMillis(); // 当前时间戳，单位：毫秒喵
        pdc.set(CookingKeys.FOOD_TIMESTAMP, PersistentDataType.LONG, nowMs);

        // 更新lore中的保质期行，找到旧行则替换，没有则追加喵
        List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
        String freshnessLine = FoodTagListener.buildFreshnessLore(nowMs); // 新的保质期显示行喵
        boolean hasFreshnessLine = false; // 标记是否找到并替换了保质期行喵
        for (int loreIdx = 0; loreIdx < lore.size(); loreIdx++) {
            if (lore.get(loreIdx).startsWith("§8保质期:")) {
                // 找到旧的保质期行，替换喵
                lore.set(loreIdx, freshnessLine);
                hasFreshnessLine = true;
                break;
            }
        }
        if (!hasFreshnessLine) {
            // 没有保质期行，追加喵
            lore.add(freshnessLine);
        }
        meta.setLore(lore);
        // 把更新后的meta写回item（此时item已被替换为AIR，但仍需同步meta到原物品引用以防后续读取喵）
        item.setItemMeta(meta);

        // 应用饥饿值和饱和度到玩家喵
        Player player = e.getPlayer();
        int newFood = Math.min(player.getFoodLevel() + hunger, 20); // 新的食物等级，上限20喵
        float newSat = (float) Math.min(player.getSaturation() + saturation, newFood); // 新的饱和度，不超过食物等级喵
        player.setFoodLevel(newFood);
        player.setSaturation(newSat);

        // 读取并应用药水效果，格式："effectName:duration:amplifier|..."喵
        String effectsRaw = pdc.get(CookingKeys.DISH_EFFECTS, PersistentDataType.STRING);
        if (effectsRaw != null && !effectsRaw.isEmpty()) {
            for (String effectStr : effectsRaw.split("\\|")) {
                // 解析每一条效果配置喵
                String[] parts = effectStr.trim().split(":");
                String effectName = parts[0].toUpperCase().replace(" ", "_").replace("-", "_");
                int duration  = parts.length > 1 ? parseInt(parts[1], 200) : 200; // 效果持续时间，单位：tick喵
                int amplifier = parts.length > 2 ? parseInt(parts[2], 0)   : 0;   // 效果等级，0=一级喵
                PotionEffectType type = PotionEffectType.getByName(effectName);
                if (type != null) {
                    // 喵~防御：type为null时说明effectName无效，跳过不崩服务器喵
                    player.addPotionEffect(new PotionEffect(type, duration, amplifier));
                }
            }
        }
    }

    /**
     * 安全解析整数字符串，解析失败时返回默认值喵~
     * 输入：s - 要解析的字符串；def - 解析失败时的默认值
     * 输出：解析成功的int值，或def喵
     */
    private int parseInt(String s, int def) {
        try { return Integer.parseInt(s.trim()); } catch (NumberFormatException e) { return def; }
    }
}

