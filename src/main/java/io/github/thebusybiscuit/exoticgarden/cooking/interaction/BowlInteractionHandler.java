package io.github.thebusybiscuit.exoticgarden.cooking.interaction;

import io.github.thebusybiscuit.exoticgarden.cooking.ai.DishGenerator;
import io.github.thebusybiscuit.exoticgarden.cooking.config.FuelConfig;
import io.github.thebusybiscuit.exoticgarden.cooking.config.IngredientConfig;
import io.github.thebusybiscuit.exoticgarden.cooking.config.SeasoningConfig;
import io.github.thebusybiscuit.exoticgarden.cooking.state.*;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class BowlInteractionHandler implements StoveInteractionHandler {

    private static final NamespacedKey KEY_DISH_NAME = new NamespacedKey("cooking", "dish_name");
    private static final NamespacedKey KEY_DISH_HUNGER = new NamespacedKey("cooking", "dish_hunger");
    private static final NamespacedKey KEY_DISH_SATURATION = new NamespacedKey("cooking", "dish_saturation");
    private static final NamespacedKey KEY_DISH_QUALITY = new NamespacedKey("cooking", "dish_quality");
    private static final NamespacedKey KEY_DISH_DESCRIPTION = new NamespacedKey("cooking", "dish_description");

    private final JavaPlugin plugin;
    private final String apiKey;
    private final String baseUrl;
    private final String model;
    private final Map<String, FuelConfig.FuelData> fuels;
    private final Map<String, IngredientConfig.IngredientData> ingredients;
    private final Map<String, SeasoningConfig.SeasoningData> seasonings;

    public BowlInteractionHandler(JavaPlugin plugin,
                                  Map<String, FuelConfig.FuelData> fuels,
                                  Map<String, IngredientConfig.IngredientData> ingredients,
                                  Map<String, SeasoningConfig.SeasoningData> seasonings,
                                  String apiKey, String baseUrl, String model) {
        this.plugin = plugin;
        this.fuels = fuels;
        this.ingredients = ingredients;
        this.seasonings = seasonings;
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
        this.model = model;
    }

    @Override
    public boolean handle(Player player, ItemStack handItem, StoveState state, Location location) {
        if (handItem.getType() != Material.BOWL) return false;
        if (handItem.getItemMeta() != null) {
            PersistentDataContainer pdc = handItem.getItemMeta().getPersistentDataContainer();
            if (pdc.has(new NamespacedKey("slimefun", "slimefun_item"), PersistentDataType.STRING))
                return false;
        }

        state.pendingFuelClear = false;

        List<DishGenerator.IngredientInfo> ingInfos = new ArrayList<>();
        List<DishGenerator.SeasoningInfo> seaInfos = new ArrayList<>();
        List<String> fxList = new ArrayList<>();

        for (IngredientSlot slot : state.slots) {
            if (slot == null) continue;
            ingInfos.add(new DishGenerator.IngredientInfo(
                slot.ingredientId, slot.state.name(),
                slot.frontDoneness, slot.backDoneness,
                CharLevel.fromSeconds(slot.charSeconds).name()));
        }
        for (SeasoningEntry se : state.seasonings) {
            SeasoningConfig.SeasoningData sd = seasonings.get(se.seasoningId);
            seaInfos.add(new DishGenerator.SeasoningInfo(
                se.seasoningId,
                sd != null && sd.hasDoneness ? se.progress : null));
        }
        for (FuelEntry fe : state.fuels) {
            FuelConfig.FuelData fd = fuels.get(fe.fuelId);
            if (fd != null && fd.effect != null && !fd.effect.isEmpty()) fxList.add(fd.effect);
        }

        Arrays.fill(state.slots, null);
        state.seasonings.clear();

        player.sendMessage("§e烹饪中...");

        DishGenerator.generate(ingInfos, seaInfos, fxList, apiKey, baseUrl, model)
            .thenAccept(result -> plugin.getServer().getScheduler().runTask(plugin, () -> {
                ItemStack dish = buildDishItem(result);
                player.getInventory().addItem(dish);
                player.sendMessage("§a烹饪完成：" + result.name);
            }))
            .exceptionally(ex -> {
                plugin.getServer().getScheduler().runTask(plugin, () ->
                    player.sendMessage("§c烹饪失败，请稍后再试"));
                return null;
            });
        return true;
    }

    private ItemStack buildDishItem(DishGenerator.DishResult result) {
        ItemStack item = new ItemStack(Material.MUSHROOM_STEW, 1);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(result.name);
        List<String> lore = new ArrayList<>();
        lore.add("§7品质: §a" + result.quality);
        lore.add("§7描述: " + result.description);
        lore.add("§7饥饿值: §e+" + result.hunger);
        meta.setLore(lore);
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(KEY_DISH_NAME, PersistentDataType.STRING, result.name);
        pdc.set(KEY_DISH_HUNGER, PersistentDataType.INTEGER, result.hunger);
        pdc.set(KEY_DISH_SATURATION, PersistentDataType.DOUBLE, result.saturation);
        pdc.set(KEY_DISH_QUALITY, PersistentDataType.STRING, result.quality);
        pdc.set(KEY_DISH_DESCRIPTION, PersistentDataType.STRING, result.description);
        item.setItemMeta(meta);
        return item;
    }
}
