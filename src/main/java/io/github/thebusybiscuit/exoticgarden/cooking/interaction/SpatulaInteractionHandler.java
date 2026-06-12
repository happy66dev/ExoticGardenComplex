package io.github.thebusybiscuit.exoticgarden.cooking.interaction;

import io.github.thebusybiscuit.exoticgarden.cooking.state.ActiveFace;
import io.github.thebusybiscuit.exoticgarden.cooking.state.FoodState;
import io.github.thebusybiscuit.exoticgarden.cooking.state.IngredientSlot;
import io.github.thebusybiscuit.exoticgarden.cooking.state.StoveState;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

public class SpatulaInteractionHandler implements StoveInteractionHandler {

    private static final NamespacedKey KEY_ITEM_TYPE = new NamespacedKey("cooking", "item_type");

    @Override
    public boolean handle(Player player, ItemStack handItem, StoveState state, Location location) {
        if (handItem.getType() == Material.AIR || handItem.getItemMeta() == null) return false;
        PersistentDataContainer pdc = handItem.getItemMeta().getPersistentDataContainer();
        if (!"SPATULA".equals(pdc.get(KEY_ITEM_TYPE, PersistentDataType.STRING))) return false;

        state.pendingFuelClear = false;
        boolean flipped = false;
        for (IngredientSlot slot : state.slots) {
            if (slot == null) continue;
            if (slot.state == FoodState.WHOLE && slot.frontDoneness >= 0.5
                    && slot.currentFace == ActiveFace.FRONT) {
                slot.currentFace = ActiveFace.BACK;
                flipped = true;
            }
        }
        if (flipped) {
            state.spatulaBoostTicksLeft = Math.max(state.spatulaBoostTicksLeft, 200);
            player.sendMessage("§a已翻面！烹饪加速中...");
        }
        return true;
    }
}
