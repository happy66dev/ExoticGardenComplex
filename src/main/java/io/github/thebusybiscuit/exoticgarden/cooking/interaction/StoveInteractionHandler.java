package io.github.thebusybiscuit.exoticgarden.cooking.interaction;

import io.github.thebusybiscuit.exoticgarden.cooking.state.StoveState;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public interface StoveInteractionHandler {
    boolean handle(Player player, ItemStack handItem, StoveState state, Location location);
}
