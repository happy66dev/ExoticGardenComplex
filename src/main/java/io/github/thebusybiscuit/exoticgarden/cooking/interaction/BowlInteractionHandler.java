package io.github.thebusybiscuit.exoticgarden.cooking.interaction;

import io.github.thebusybiscuit.exoticgarden.cooking.ai.DishGenerator;
import io.github.thebusybiscuit.exoticgarden.cooking.config.FuelConfig;
import io.github.thebusybiscuit.exoticgarden.cooking.config.IngredientConfig;
import io.github.thebusybiscuit.exoticgarden.cooking.config.SeasoningConfig;
import io.github.thebusybiscuit.exoticgarden.cooking.state.*;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class BowlInteractionHandler implements StoveInteractionHandler {

    private static final org.bukkit.NamespacedKey KEY_SF_ITEM =
            new org.bukkit.NamespacedKey("slimefun", "slimefun_item");

    private static final Map<Material, Integer> VANILLA_HUNGER = Map.ofEntries(
        Map.entry(Material.APPLE, 4), Map.entry(Material.BAKED_POTATO, 5),
        Map.entry(Material.BEETROOT, 1), Map.entry(Material.BEETROOT_SOUP, 6),
        Map.entry(Material.BREAD, 5), Map.entry(Material.CARROT, 3),
        Map.entry(Material.COOKED_BEEF, 8), Map.entry(Material.COOKED_CHICKEN, 6),
        Map.entry(Material.COOKED_COD, 5), Map.entry(Material.COOKED_MUTTON, 6),
        Map.entry(Material.COOKED_PORKCHOP, 8), Map.entry(Material.COOKED_RABBIT, 5),
        Map.entry(Material.COOKED_SALMON, 6), Map.entry(Material.COOKIE, 2),
        Map.entry(Material.GOLDEN_APPLE, 4), Map.entry(Material.GOLDEN_CARROT, 6),
        Map.entry(Material.MELON_SLICE, 2), Map.entry(Material.MUSHROOM_STEW, 6),
        Map.entry(Material.PUMPKIN_PIE, 8), Map.entry(Material.BEEF, 3),
        Map.entry(Material.CHICKEN, 2), Map.entry(Material.COD, 2),
        Map.entry(Material.MUTTON, 2), Map.entry(Material.PORKCHOP, 3),
        Map.entry(Material.RABBIT, 3), Map.entry(Material.SALMON, 2),
        Map.entry(Material.POTATO, 1), Map.entry(Material.POISONOUS_POTATO, 2),
        Map.entry(Material.SWEET_BERRIES, 2), Map.entry(Material.GLOW_BERRIES, 2),
        Map.entry(Material.DRIED_KELP, 1), Map.entry(Material.COCOA_BEANS, 0),
        Map.entry(Material.EGG, 0)
    );

    private final Map<String, FuelConfig.FuelData> fuels;
    private final Map<String, IngredientConfig.IngredientData> ingredients;
    private final Map<String, SeasoningConfig.SeasoningData> seasonings;

    public BowlInteractionHandler(Map<String, FuelConfig.FuelData> fuels,
                                  Map<String, IngredientConfig.IngredientData> ingredients,
                                  Map<String, SeasoningConfig.SeasoningData> seasonings) {
        this.fuels = fuels;
        this.ingredients = ingredients;
        this.seasonings = seasonings;
    }

    @Override
    public boolean handle(Player player, ItemStack handItem, StoveState state, Location location) {
        if (handItem.getType() != Material.BOWL) return false;
        if (handItem.getItemMeta() != null) {
            PersistentDataContainer pdc = handItem.getItemMeta().getPersistentDataContainer();
            if (pdc.has(KEY_SF_ITEM, PersistentDataType.STRING))
                return false;
        }

        if (state.cookingInProgress) {
            player.sendMessage("§e烹饪进行中，请等待完成...");
            return true;
        }

        state.pendingFuelClear = false;

        List<DishGenerator.IngredientInfo> ingInfos = new ArrayList<>();
        List<DishGenerator.SeasoningInfo> seaInfos = new ArrayList<>();
        List<String> fxList = new ArrayList<>();
        int totalHunger = 0;
        double totalWeight = 0;

        for (IngredientSlot slot : state.slots) {
            if (slot == null) continue;
            IngredientConfig.IngredientData data = ingredients.get(slot.ingredientId);
            String displayName = data != null ? data.displayName : slot.ingredientId;
            double weight = data != null ? data.weightGrams : 100;
            double doneness = Math.max(slot.frontDoneness, slot.backDoneness);
            totalHunger += getHungerValue(slot.ingredientId);
            totalWeight += weight;
            ingInfos.add(new DishGenerator.IngredientInfo(
                displayName, slot.state.name(),
                doneness,
                CharLevel.fromSeconds(slot.charSeconds).name(),
                weight));
        }

        for (SeasoningEntry se : state.seasonings) {
            if ("WATER".equals(se.seasoningId) || "WATER_BUCKET".equals(se.seasoningId)
                || "OIL".equals(se.seasoningId)) continue;
            SeasoningConfig.SeasoningData sd = seasonings.get(se.seasoningId);
            String displayName = sd != null ? sd.displayName : se.seasoningId;
            totalWeight += se.weight;
            seaInfos.add(new DishGenerator.SeasoningInfo(
                displayName,
                sd != null && sd.hasDoneness ? se.progress : null));
        }

        for (FuelEntry fe : state.fuels) {
            FuelConfig.FuelData fd = fuels.get(fe.fuelId);
            if (fd != null && fd.effect != null && !fd.effect.isEmpty()) fxList.add(fd.effect);
        }

        if (ingInfos.isEmpty()) {
            player.sendMessage("§c灶台上没有食材");
            return true;
        }

        List<String> waterSrcs = new ArrayList<>(state.waterSources);
        String[] prompts = DishGenerator.buildPrompt(ingInfos, seaInfos, fxList,
            state.waterAmount, state.oilAmount, totalHunger, totalWeight, waterSrcs);
        player.sendMessage("§6§l──── AI 提示词调试 ────");
        player.sendMessage("§b[System] §f" + prompts[0]);
        player.sendMessage("§a[User]   §f" + prompts[1]);
        player.sendMessage("§6§l──────────────────────");
        return true;
    }

    private int getHungerValue(String ingredientId) {
        Material mat = Material.getMaterial(ingredientId);
        if (mat != null && VANILLA_HUNGER.containsKey(mat)) {
            return VANILLA_HUNGER.get(mat);
        }
        SlimefunItem sfItem = SlimefunItem.getById(ingredientId);
        if (sfItem instanceof io.github.thebusybiscuit.exoticgarden.items.CustomFood) {
            return ((io.github.thebusybiscuit.exoticgarden.items.CustomFood) sfItem).getFoodValue();
        }
        return 0;
    }
}
