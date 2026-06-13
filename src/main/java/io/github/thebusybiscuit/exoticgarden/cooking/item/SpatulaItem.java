package io.github.thebusybiscuit.exoticgarden.cooking.item;

import io.github.thebusybiscuit.exoticgarden.cooking.CookingKeys;
import io.github.thebusybiscuit.exoticgarden.cooking.block.CuttingBoardBlock;
import io.github.thebusybiscuit.exoticgarden.cooking.block.StoveBlock;
import io.github.thebusybiscuit.exoticgarden.cooking.config.IngredientConfig;
import io.github.thebusybiscuit.exoticgarden.cooking.state.ActiveFace;
import io.github.thebusybiscuit.exoticgarden.cooking.state.FoodState;
import io.github.thebusybiscuit.exoticgarden.cooking.state.IngredientSlot;
import io.github.thebusybiscuit.exoticgarden.cooking.state.StoveState;
import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;

public class SpatulaItem extends SlimefunItem {

    private final Map<String, IngredientConfig.IngredientData> ingredients;

    public SpatulaItem(ItemGroup group, SlimefunItemStack item,
                       RecipeType recipeType, ItemStack[] recipe,
                       JavaPlugin plugin,
                       Map<String, IngredientConfig.IngredientData> ingredients) {
        super(group, item, recipeType, recipe);
        this.ingredients = ingredients;

        plugin.getServer().getPluginManager().registerEvents(new Listener() {

            @EventHandler(priority = EventPriority.LOW, ignoreCancelled = false)
            public void onInteract(PlayerInteractEvent e) {
                if (e.getHand() != EquipmentSlot.HAND) return;
                if (e.getAction() != Action.RIGHT_CLICK_BLOCK && e.getAction() != Action.RIGHT_CLICK_AIR) return;

                Player player = e.getPlayer();
                ItemStack hand = player.getInventory().getItemInMainHand();
                if (hand.getItemMeta() == null) return;
                PersistentDataContainer handPdc = hand.getItemMeta().getPersistentDataContainer();
                if (!"SPATULA".equals(handPdc.get(CookingKeys.ITEM_TYPE, PersistentDataType.STRING))) return;

                Block block = e.getClickedBlock();
                if (block != null && block.getType() == Material.CAMPFIRE) {
                    SlimefunItem sfItem = SlimefunItem.getById("EG_COOKING_STOVE");
                    if (!(sfItem instanceof StoveBlock stove)) return;
                    Location loc = block.getLocation();
                    StoveState state = stove.activeStoves.get(loc);
                    if (state == null) return;

                    e.setCancelled(true);

                    if (player.isSneaking()) {
                        java.util.Arrays.fill(state.slots, null);
                        state.seasonings.clear();
                        state.waterAmount = 0;
                        state.oilAmount = 0;
                        state.waterSources.clear();
                        state.cookingInProgress = false;
                        StoveBlock.syncCampfireSlots(loc, state);
                        player.sendMessage("§a灶台已清空（温度与燃料保留）");
                        return;
                    }

                    state.pendingFuelClear = false;
                    boolean flipped = false;
                    for (IngredientSlot slot : state.slots) {
                        if (slot == null) continue;
                        if (slot.state == FoodState.WHOLE) {
                            if (slot.currentFace == ActiveFace.FRONT && slot.frontDoneness >= 0.5) {
                                slot.currentFace = ActiveFace.BACK;
                                flipped = true;
                            } else if (slot.currentFace == ActiveFace.BACK && slot.backDoneness >= 0.5) {
                                slot.currentFace = ActiveFace.FRONT;
                                flipped = true;
                            }
                        }
                    }
                    if (flipped) {
                        state.spatulaBoostTicksLeft = Math.max(state.spatulaBoostTicksLeft, 200);
                        player.sendMessage("§a已翻面！烹饪加速中...");
                    } else {
                        player.sendMessage("§e食材还不到翻面时机（需要熟度 ≥ 50%）");
                    }
                    return;
                }

                Location boardLoc = findNearbyBoard(player);
                if (boardLoc == null) return;
                e.setCancelled(true);

                ItemStack held = CuttingBoardBlock.getStoredItem(boardLoc);
                if (held == null) return;

                org.bukkit.inventory.meta.ItemMeta heldMeta = held.getItemMeta();
                if (heldMeta == null) return;
                PersistentDataContainer heldPdc = heldMeta.getPersistentDataContainer();

                String rawState = heldPdc.get(CookingKeys.FOOD_STATE, PersistentDataType.STRING);
                FoodState current = FoodState.WHOLE;
                if (rawState != null) {
                    try { current = FoodState.valueOf(rawState); } catch (IllegalArgumentException ignored) {}
                }
                if (current != FoodState.WHOLE) return;

                String ingId = heldPdc.get(CookingKeys.INGREDIENT_ID, PersistentDataType.STRING);
                if (ingId == null) ingId = held.getType().name();
                IngredientConfig.IngredientData data = ingredients.get(ingId);
                if (data == null || data.sauceCreation == null) return;

                int clicks = heldPdc.getOrDefault(CookingKeys.SPATULA_CLICKS, PersistentDataType.INTEGER, 0) + 1;
                if (clicks >= data.sauceCreation.clicksRequired) {
                    heldPdc.set(CookingKeys.FOOD_STATE, PersistentDataType.STRING, FoodState.SAUCE.name());
                    heldPdc.remove(CookingKeys.SPATULA_CLICKS);
                    player.sendMessage("§a已制成酱料！");
                } else {
                    heldPdc.set(CookingKeys.SPATULA_CLICKS, PersistentDataType.INTEGER, clicks);
                    player.sendMessage("§e搅拌中: " + clicks + "/" + data.sauceCreation.clicksRequired);
                }
                held.setItemMeta(heldMeta);
                CuttingBoardBlock.setStoredItem(boardLoc, held);
            }

            @EventHandler(priority = EventPriority.LOW)
            public void onInteractEntity(PlayerInteractAtEntityEvent event) {
                if (event.getHand() != EquipmentSlot.HAND) return;
                if (!(event.getRightClicked() instanceof ArmorStand stand)) return;
                if (!stand.getPersistentDataContainer().has(CookingKeys.BOARD_ITEM, PersistentDataType.STRING)) return;
                event.setCancelled(true);
            }
        }, plugin);
    }

    private Location findNearbyBoard(Player player) {
        Location ploc = player.getLocation();
        Location best = null;
        double bestDist = Double.MAX_VALUE;
        for (Map.Entry<Location, ArmorStand> entry : CuttingBoardBlock.boardDisplays.entrySet()) {
            Location bloc = entry.getKey();
            if (bloc.getWorld() == null || !bloc.getWorld().equals(ploc.getWorld())) continue;
            double d = ploc.distanceSquared(bloc.clone().add(0.5, 0.5, 0.5));
            if (d <= 9.0 && d < bestDist) { bestDist = d; best = bloc; }
        }
        return best;
    }
}
