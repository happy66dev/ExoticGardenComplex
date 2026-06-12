package io.github.thebusybiscuit.exoticgarden.cooking.interaction;

import io.github.thebusybiscuit.exoticgarden.cooking.state.FuelEntry;
import io.github.thebusybiscuit.exoticgarden.cooking.state.StoveState;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
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
            for (FuelEntry fe : state.fuels) {
                ItemStack drop = reconstructFuelItem(fe.fuelId);
                if (drop != null && location.getWorld() != null) {
                    location.getWorld().dropItemNaturally(location, drop);
                }
            }
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

    private static ItemStack reconstructFuelItem(String id) {
        SlimefunItem sfItem = SlimefunItem.getById(id);
        if (sfItem != null) return sfItem.getItem().clone();
        Material mat = Material.getMaterial(id);
        if (mat != null && mat != Material.AIR) return new ItemStack(mat, 1);
        return null;
    }
}
