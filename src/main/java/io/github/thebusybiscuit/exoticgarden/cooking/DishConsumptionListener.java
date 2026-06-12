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

public class DishConsumptionListener implements Listener {

    private static final NamespacedKey KEY_DISH_HUNGER = new NamespacedKey("cooking", "dish_hunger");
    private static final NamespacedKey KEY_DISH_SATURATION = new NamespacedKey("cooking", "dish_saturation");

    @EventHandler(ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent e) {
        ItemStack item = e.getItem();
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        if (!pdc.has(KEY_DISH_HUNGER, PersistentDataType.INTEGER)) return;

        Integer hungerRaw = pdc.get(KEY_DISH_HUNGER, PersistentDataType.INTEGER);
        if (hungerRaw == null) return;
        int hunger = Math.max(0, Math.min(hungerRaw, 20));

        Double saturationRaw = pdc.getOrDefault(KEY_DISH_SATURATION, PersistentDataType.DOUBLE, 0.8);
        double saturation = Math.max(0, Math.min(saturationRaw == null ? 0.8 : saturationRaw, 20.0));

        e.setItem(new ItemStack(org.bukkit.Material.AIR));

        Player player = e.getPlayer();
        int newFood = Math.min(player.getFoodLevel() + hunger, 20);
        float newSat = (float) Math.min(player.getSaturation() + saturation, newFood);
        player.setFoodLevel(newFood);
        player.setSaturation(newSat);
    }
}
