package io.github.thebusybiscuit.exoticgarden.cooking.interaction;

import io.github.thebusybiscuit.exoticgarden.cooking.CookingKeys;
import io.github.thebusybiscuit.exoticgarden.cooking.block.StoveBlock;
import io.github.thebusybiscuit.exoticgarden.cooking.config.IngredientConfig;
import io.github.thebusybiscuit.exoticgarden.cooking.state.ActiveFace;
import io.github.thebusybiscuit.exoticgarden.cooking.state.FoodState;
import io.github.thebusybiscuit.exoticgarden.cooking.state.IngredientSlot;
import io.github.thebusybiscuit.exoticgarden.cooking.state.StoveState;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.Map;

public class IngredientInteractionHandler implements StoveInteractionHandler {

    private final Map<String, IngredientConfig.IngredientData> ingredients;

    public IngredientInteractionHandler(Map<String, IngredientConfig.IngredientData> ingredients) {
        this.ingredients = ingredients;
    }

    @Override
    public boolean handle(Player player, ItemStack handItem, StoveState state, Location location) {
        if (handItem.getType() == Material.AIR || handItem.getItemMeta() == null) return false;
        PersistentDataContainer pdc = handItem.getItemMeta().getPersistentDataContainer();
        String ingId = pdc.get(CookingKeys.INGREDIENT_ID, PersistentDataType.STRING);
        if (ingId == null || !ingredients.containsKey(ingId)) return false;

        state.pendingFuelClear = false;
        int emptySlot = -1;
        for (int i = 0; i < state.slots.length; i++) {
            if (state.slots[i] == null) { emptySlot = i; break; }
        }
        if (emptySlot == -1) {
            player.sendMessage("§c食材槽已满（最多4格）");
            return true;
        }
        String rawState = pdc.get(CookingKeys.FOOD_STATE, PersistentDataType.STRING);
        FoodState foodState = FoodState.WHOLE;
        if (rawState != null) {
            try { foodState = FoodState.valueOf(rawState); } catch (IllegalArgumentException ignored) {}
        }
        state.slots[emptySlot] = new IngredientSlot(ingId, foodState, 0, 0, ActiveFace.FRONT, 0);
        handItem.setAmount(handItem.getAmount() - 1);
        StoveBlock.syncCampfireSlots(location, state);
        return true;
    }
}
