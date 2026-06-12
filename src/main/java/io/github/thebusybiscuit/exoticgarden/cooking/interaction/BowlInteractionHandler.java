package io.github.thebusybiscuit.exoticgarden.cooking.interaction;

import io.github.thebusybiscuit.exoticgarden.cooking.ai.DishGenerator;
import io.github.thebusybiscuit.exoticgarden.cooking.config.FuelConfig;
import io.github.thebusybiscuit.exoticgarden.cooking.config.IngredientConfig;
import io.github.thebusybiscuit.exoticgarden.cooking.config.SeasoningConfig;
import io.github.thebusybiscuit.exoticgarden.cooking.state.*;
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

        if (ingInfos.isEmpty()) {
            player.sendMessage("§c灶台上没有食材");
            return true;
        }

        String[] prompts = DishGenerator.buildPrompt(ingInfos, seaInfos, fxList);
        player.sendMessage("§6§l──── AI 提示词调试 ────");
        player.sendMessage("§b[System] §f" + prompts[0]);
        player.sendMessage("§a[User]   §f" + prompts[1]);
        player.sendMessage("§6§l──────────────────────");
        return true;
    }
}
