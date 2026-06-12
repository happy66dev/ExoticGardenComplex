package io.github.thebusybiscuit.exoticgarden.cooking;

import org.bukkit.NamespacedKey;
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

public class DishConsumptionListener implements Listener {

    @EventHandler(ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent e) {
        ItemStack item = e.getItem();
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        if (!pdc.has(CookingKeys.DISH_HUNGER, PersistentDataType.INTEGER)) return;

        Integer hungerRaw = pdc.get(CookingKeys.DISH_HUNGER, PersistentDataType.INTEGER);
        if (hungerRaw == null) return;
        int hunger = Math.max(0, Math.min(hungerRaw, 20));

        Double saturationRaw = pdc.getOrDefault(CookingKeys.DISH_SATURATION, PersistentDataType.DOUBLE, 0.8);
        double saturation = Math.max(0, Math.min(saturationRaw, 20.0));

        e.setItem(new ItemStack(org.bukkit.Material.AIR));

        Player player = e.getPlayer();
        int newFood = Math.min(player.getFoodLevel() + hunger, 20);
        float newSat = (float) Math.min(player.getSaturation() + saturation, newFood);
        player.setFoodLevel(newFood);
        player.setSaturation(newSat);

        String effectsRaw = pdc.get(CookingKeys.DISH_EFFECTS, PersistentDataType.STRING);
        if (effectsRaw != null && !effectsRaw.isEmpty()) {
            for (String effectStr : effectsRaw.split("\\|")) {
                String[] parts = effectStr.trim().split(":");
                String effectName = parts[0].toUpperCase().replace(" ", "_").replace("-", "_");
                int duration = parts.length > 1 ? parseInt(parts[1], 200) : 200;
                int amplifier = parts.length > 2 ? parseInt(parts[2], 0) : 0;
                PotionEffectType type = PotionEffectType.getByName(effectName);
                if (type != null) {
                    player.addPotionEffect(new PotionEffect(type, duration, amplifier));
                }
            }
        }
    }

    private int parseInt(String s, int def) {
        try { return Integer.parseInt(s.trim()); } catch (NumberFormatException e) { return def; }
    }
}
