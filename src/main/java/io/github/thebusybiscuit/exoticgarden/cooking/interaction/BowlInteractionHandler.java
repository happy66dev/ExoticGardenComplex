package io.github.thebusybiscuit.exoticgarden.cooking.interaction;

import io.github.thebusybiscuit.exoticgarden.cooking.CookingKeys;
import io.github.thebusybiscuit.exoticgarden.cooking.ai.DishGenerator;
import io.github.thebusybiscuit.exoticgarden.cooking.config.FuelConfig;
import io.github.thebusybiscuit.exoticgarden.cooking.config.IngredientConfig;
import io.github.thebusybiscuit.exoticgarden.cooking.config.SeasoningConfig;
import io.github.thebusybiscuit.exoticgarden.cooking.state.*;
import org.bukkit.Location;
import org.bukkit.Material;
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

    private static final org.bukkit.NamespacedKey KEY_SF_ITEM =
            new org.bukkit.NamespacedKey("slimefun", "slimefun_item");

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

        state.cookingInProgress = true;
        handItem.setAmount(handItem.getAmount() - 1);
        player.sendMessage("§e烹饪中...");

        String[] prompts = DishGenerator.buildPrompt(ingInfos, seaInfos, fxList);
        player.sendMessage("§6§l──── AI 提示词调试 ────");
        player.sendMessage("§b[System] §f" + prompts[0]);
        player.sendMessage("§a[User]   §f" + prompts[1]);
        player.sendMessage("§6§l──────────────────────");

        DishGenerator.DishResult result = buildLocalResult(ingInfos);
        state.cookingInProgress = false;
        Arrays.fill(state.slots, null);
        state.seasonings.clear();
        ItemStack dish = buildDishItem(result);
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(dish);
        if (!leftover.isEmpty() && location.getWorld() != null) {
            leftover.values().forEach(it -> location.getWorld().dropItemNaturally(location, it));
        }
        player.sendMessage("§a烹饪完成（临时本地生成）：" + result.name);
        return true;
    }

    private DishGenerator.DishResult buildLocalResult(List<DishGenerator.IngredientInfo> ingInfos) {
        StringBuilder name = new StringBuilder();
        for (int i = 0; i < ingInfos.size(); i++) {
            if (i > 0) name.append("·");
            name.append(ingInfos.get(i).id);
        }
        name.append("料理");
        int hunger = Math.min(4 + ingInfos.size(), 10);
        return new DishGenerator.DishResult(
            name.toString(), hunger, 0.8, "普通",
            List.of(), "临时生成的测试菜肴"
        );
    }

    private ItemStack buildDishItem(DishGenerator.DishResult result) {
        ItemStack item = new ItemStack(Material.MUSHROOM_STEW, 1);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;
        meta.setDisplayName(result.name);
        List<String> lore = new ArrayList<>();
        lore.add("§7品质: §a" + result.quality);
        lore.add("§7描述: " + result.description);
        lore.add("§7饥饿值: §e+" + result.hunger);
        if (!result.effects.isEmpty()) {
            lore.add("§7效果: §b" + String.join(", ", result.effects));
        }
        meta.setLore(lore);
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(CookingKeys.DISH_NAME,        PersistentDataType.STRING,  result.name);
        pdc.set(CookingKeys.DISH_HUNGER,      PersistentDataType.INTEGER, result.hunger);
        pdc.set(CookingKeys.DISH_SATURATION,  PersistentDataType.DOUBLE,  result.saturation);
        pdc.set(CookingKeys.DISH_QUALITY,     PersistentDataType.STRING,  result.quality);
        pdc.set(CookingKeys.DISH_DESCRIPTION, PersistentDataType.STRING,  result.description);
        if (!result.effects.isEmpty()) {
            pdc.set(CookingKeys.DISH_EFFECTS, PersistentDataType.STRING, String.join("|", result.effects));
        }
        item.setItemMeta(meta);
        return item;
    }
}
