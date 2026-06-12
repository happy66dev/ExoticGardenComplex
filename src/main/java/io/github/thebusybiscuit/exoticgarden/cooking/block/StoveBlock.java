package io.github.thebusybiscuit.exoticgarden.cooking.block;

import io.github.thebusybiscuit.exoticgarden.cooking.interaction.StoveInteractionHandler;
import io.github.thebusybiscuit.exoticgarden.cooking.state.FuelEntry;
import io.github.thebusybiscuit.exoticgarden.cooking.state.IngredientSlot;
import io.github.thebusybiscuit.exoticgarden.cooking.state.SeasoningEntry;
import io.github.thebusybiscuit.exoticgarden.cooking.state.StoveState;
import io.github.thebusybiscuit.slimefun4.api.events.PlayerRightClickEvent;
import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.core.attributes.HologramOwner;
import io.github.thebusybiscuit.slimefun4.core.handlers.BlockBreakHandler;
import io.github.thebusybiscuit.slimefun4.core.handlers.BlockUseHandler;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class StoveBlock extends SlimefunItem implements HologramOwner {

    public final Map<Location, StoveState> activeStoves = new ConcurrentHashMap<>();

    private final List<StoveInteractionHandler> handlers;

    public StoveBlock(ItemGroup group, SlimefunItemStack item, RecipeType recipeType,
                      ItemStack[] recipe, List<StoveInteractionHandler> handlers) {
        super(group, item, recipeType, recipe);
        this.handlers = handlers;
        addItemHandler(buildUseHandler(), buildBreakHandler());
    }

    private BlockUseHandler buildUseHandler() {
        return (PlayerRightClickEvent e) -> {
            e.cancel();
            if (e.getClickedBlock().isEmpty()) return;
            Player player = e.getPlayer();
            Location loc = e.getClickedBlock().get().getLocation().clone();
            StoveState state = activeStoves.computeIfAbsent(loc, k -> new StoveState());
            ItemStack hand = player.getInventory().getItemInMainHand();

            boolean handled = false;
            for (StoveInteractionHandler handler : handlers) {
                if (handler.handle(player, hand, state, loc)) {
                    handled = true;
                    break;
                }
            }
            if (!handled) {
                state.pendingFuelClear = false;
            }
        };
    }

    private BlockBreakHandler buildBreakHandler() {
        return new BlockBreakHandler(false, false) {
            @Override
            public void onPlayerBreak(@Nonnull BlockBreakEvent e,
                                      @Nonnull ItemStack item,
                                      @Nonnull List<ItemStack> drops) {
                org.bukkit.block.Block b = e.getBlock();
                Location loc = b.getLocation();
                StoveState state = activeStoves.remove(loc);
                removeHologram(b);
                if (state == null) return;
                World world = loc.getWorld();
                if (world == null) return;
                Location dropLoc = loc.clone().add(0.5, 0.5, 0.5);
                for (IngredientSlot slot : state.slots) {
                    if (slot == null) continue;
                    ItemStack drop = reconstructItem(slot.ingredientId);
                    if (drop != null) world.dropItemNaturally(dropLoc, drop);
                }
                for (FuelEntry fe : state.fuels) {
                    ItemStack drop = reconstructItem(fe.fuelId);
                    if (drop != null) world.dropItemNaturally(dropLoc, drop);
                }
                for (SeasoningEntry se : state.seasonings) {
                    ItemStack drop = reconstructItem(se.seasoningId);
                    if (drop != null) world.dropItemNaturally(dropLoc, drop);
                }
            }
        };
    }

    private static ItemStack reconstructItem(String id) {
        SlimefunItem sfItem = SlimefunItem.getById(id);
        if (sfItem != null) return sfItem.getItem().clone();
        Material mat = Material.getMaterial(id);
        if (mat != null && mat != Material.AIR) return new ItemStack(mat, 1);
        return null;
    }
}
