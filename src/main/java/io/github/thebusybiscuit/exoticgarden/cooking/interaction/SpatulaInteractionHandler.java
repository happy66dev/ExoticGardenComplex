package io.github.thebusybiscuit.exoticgarden.cooking.interaction;

import io.github.thebusybiscuit.exoticgarden.cooking.CookingKeys;
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

public class SpatulaInteractionHandler implements StoveInteractionHandler {

    @Override
    public boolean handle(Player player, ItemStack handItem, StoveState state, Location location) {
        if (handItem.getType() == Material.AIR || handItem.getItemMeta() == null) return false;
        PersistentDataContainer pdc = handItem.getItemMeta().getPersistentDataContainer();
        if (!"SPATULA".equals(pdc.get(CookingKeys.ITEM_TYPE, PersistentDataType.STRING))) return false;

        state.pendingFuelClear = false;
        boolean flipped = false;
        for (IngredientSlot slot : state.slots) {
            if (slot == null) continue;
            if (slot.state == FoodState.WHOLE) {
                // 喵~无条件翻面，无论成熟度多少都可以翻喵
                if (slot.currentFace == ActiveFace.FRONT) {
                    slot.currentFace = ActiveFace.BACK;
                    flipped = true;
                } else if (slot.currentFace == ActiveFace.BACK) {
                    slot.currentFace = ActiveFace.FRONT;
                    flipped = true;
                }
            }
        }
        if (flipped) {
            state.spatulaBoostTicksLeft = Math.max(state.spatulaBoostTicksLeft, 200);
            player.sendMessage("§a已翻面！烹饪加速中...");
        }
        return true;
    }
}
