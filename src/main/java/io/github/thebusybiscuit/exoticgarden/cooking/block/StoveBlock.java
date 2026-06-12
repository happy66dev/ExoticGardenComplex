package io.github.thebusybiscuit.exoticgarden.cooking.block;

import io.github.thebusybiscuit.exoticgarden.cooking.interaction.StoveInteractionHandler;
import io.github.thebusybiscuit.exoticgarden.cooking.state.IngredientSlot;
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
import org.bukkit.block.Campfire;
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
                if (b.getState() instanceof Campfire campfire) {
                    for (int i = 0; i < 4; i++) campfire.setItem(i, null);
                    campfire.update(true, false);
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

    public static void syncCampfireSlots(Location loc, StoveState state) {
        if (loc.getWorld() == null) return;
        org.bukkit.block.Block b = loc.getBlock();
        if (!(b.getState() instanceof Campfire campfire)) return;
        for (int i = 0; i < 4; i++) {
            IngredientSlot slot = i < state.slots.length ? state.slots[i] : null;
            if (slot == null) {
                campfire.setItem(i, null);
            } else {
                ItemStack display = reconstructItem(slot.ingredientId);
                campfire.setItem(i, display);
            }
        }
        campfire.update(true, false);
    }

    public static void resetCampfireCookTime(Location loc) {
        if (loc.getWorld() == null) return;
        org.bukkit.block.Block b = loc.getBlock();
        if (!(b.getState() instanceof Campfire campfire)) return;
        for (int i = 0; i < 4; i++) {
            if (campfire.getItem(i) != null) {
                campfire.setCookTime(i, 0);
            }
        }
        campfire.update(false, false);
    }
}
