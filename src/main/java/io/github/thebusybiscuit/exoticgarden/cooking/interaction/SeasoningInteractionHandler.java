package io.github.thebusybiscuit.exoticgarden.cooking.interaction;

import io.github.thebusybiscuit.exoticgarden.cooking.config.SeasoningConfig;
import io.github.thebusybiscuit.exoticgarden.cooking.state.SeasoningEntry;
import io.github.thebusybiscuit.exoticgarden.cooking.state.StoveState;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.Map;

public class SeasoningInteractionHandler implements StoveInteractionHandler {

    private static final org.bukkit.NamespacedKey KEY_SF_ITEM =
            new org.bukkit.NamespacedKey("slimefun", "slimefun_item");

    private final Map<String, SeasoningConfig.SeasoningData> seasonings;

    public SeasoningInteractionHandler(Map<String, SeasoningConfig.SeasoningData> seasonings) {
        this.seasonings = seasonings;
    }

    @Override
    public boolean handle(Player player, ItemStack handItem, StoveState state, Location location) {
        if (handItem.getType() == Material.AIR) return false;
        String seasoningId = resolve(handItem);
        if (seasoningId == null) return false;

        state.pendingFuelClear = false;

        if ("WATER_BUCKET".equals(seasoningId)) {
            state.waterAmount += 1000;
            handItem.setAmount(handItem.getAmount() - 1);
            if (handItem.getAmount() <= 0) {
                player.getInventory().setItemInMainHand(new ItemStack(Material.BUCKET));
            } else {
                player.getInventory().addItem(new ItemStack(Material.BUCKET));
            }
            return true;
        }

        if ("WATER".equals(seasoningId)) {
            state.waterAmount += 250;
            handItem.setAmount(handItem.getAmount() - 1);
            if (handItem.getAmount() <= 0) {
                player.getInventory().setItemInMainHand(new ItemStack(Material.GLASS_BOTTLE));
            } else {
                player.getInventory().addItem(new ItemStack(Material.GLASS_BOTTLE));
            }
            return true;
        }

        if ("OIL".equals(seasoningId)) {
            state.oilAmount += 100;
            handItem.setAmount(handItem.getAmount() - 1);
            return true;
        }

        if (state.seasonings.size() >= 10) {
            player.sendMessage("§c调料槽已满（最多10种）");
            return true;
        }
        state.seasonings.add(new SeasoningEntry(seasoningId, 0));
        handItem.setAmount(handItem.getAmount() - 1);
        return true;
    }

    private String resolve(ItemStack item) {
        if (item.getItemMeta() != null) {
            PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
            String id = pdc.get(io.github.thebusybiscuit.exoticgarden.cooking.CookingKeys.SEASONING_ID, PersistentDataType.STRING);
            if (id != null && seasonings.containsKey(id)) return id;

            String sfId = pdc.get(KEY_SF_ITEM, PersistentDataType.STRING);
            if (sfId != null) {
                SlimefunItem sfItem = SlimefunItem.getById(sfId);
                if (sfItem != null && seasonings.containsKey(sfItem.getId())) return sfItem.getId();
            }
        }

        Material mat = item.getType();
        if (mat == Material.WATER_BUCKET) return "WATER_BUCKET";
        if (mat == Material.POTION || mat == Material.SPLASH_POTION || mat == Material.LINGERING_POTION) {
            return "_POTION_";
        }

        String matName = mat.name();
        if (seasonings.containsKey(matName)) return matName;
        return null;
    }
}
