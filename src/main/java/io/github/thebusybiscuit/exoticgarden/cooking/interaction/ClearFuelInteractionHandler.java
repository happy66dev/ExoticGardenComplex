package io.github.thebusybiscuit.exoticgarden.cooking.interaction;

import io.github.thebusybiscuit.exoticgarden.cooking.state.StoveState;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class ClearFuelInteractionHandler implements StoveInteractionHandler {

    private static final long CONFIRM_TIMEOUT_MS = 60_000L;

    @Override
    public boolean handle(Player player, ItemStack handItem, StoveState state, Location location) {
        if (handItem.getType() != Material.AIR || !player.isSneaking()) return false;

        long now = System.currentTimeMillis();
        if (state.pendingFuelClear && (now - state.fuelClearConfirmTime) <= CONFIRM_TIMEOUT_MS) {
            state.fuels.clear();
            state.pendingFuelClear = false;
            state.fuelClearConfirmTime = 0L;
            player.sendMessage("§a已清除所有燃料");
        } else {
            state.pendingFuelClear = true;
            state.fuelClearConfirmTime = now;
            player.sendMessage("§e再次潜行右键确认清除燃料（60秒内有效）");
        }
        return true;
    }
}
