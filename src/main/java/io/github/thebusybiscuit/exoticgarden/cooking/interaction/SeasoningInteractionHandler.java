package io.github.thebusybiscuit.exoticgarden.cooking.interaction;

import io.github.thebusybiscuit.exoticgarden.cooking.config.SeasoningConfig;
import io.github.thebusybiscuit.exoticgarden.cooking.state.SeasoningEntry;
import io.github.thebusybiscuit.exoticgarden.cooking.state.StoveState;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.Map;

public class SeasoningInteractionHandler implements StoveInteractionHandler {

    private static final NamespacedKey KEY_SEASONING_ID = new NamespacedKey("cooking", "seasoning_id");
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
            String id = pdc.get(KEY_SEASONING_ID, PersistentDataType.STRING);
            if (id != null && seasonings.containsKey(id)) return id;
        }
        String matName = item.getType().name();
        if (seasonings.containsKey(matName)) return matName;
        return null;
    }
}
