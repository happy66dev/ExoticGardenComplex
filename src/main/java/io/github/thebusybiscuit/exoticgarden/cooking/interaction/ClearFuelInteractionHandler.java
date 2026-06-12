package io.github.thebusybiscuit.exoticgarden.cooking.interaction;

import io.github.thebusybiscuit.exoticgarden.cooking.state.StoveState;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class ClearFuelInteractionHandler implements StoveInteractionHandler {

    @Override
    public boolean handle(Player player, ItemStack handItem, StoveState state, Location location) {
        if (handItem.getType() != Material.AIR || !player.isSneaking()) return false;

        if (state.pendingFuelClear) {
            state.fuels.clear();
            state.pendingFuelClear = false;
            player.sendMessage("§a已清除所有燃料");
        } else {
            state.pendingFuelClear = true;
            player.sendMessage("§e再次潜行右键确认清除燃料");
        }
        return true;
    }
}
