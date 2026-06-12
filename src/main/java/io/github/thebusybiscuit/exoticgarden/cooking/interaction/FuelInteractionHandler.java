package io.github.thebusybiscuit.exoticgarden.cooking.interaction;

import io.github.thebusybiscuit.exoticgarden.cooking.config.FuelConfig;
import io.github.thebusybiscuit.exoticgarden.cooking.state.FuelEntry;
import io.github.thebusybiscuit.exoticgarden.cooking.state.StoveState;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.Map;

public class FuelInteractionHandler implements StoveInteractionHandler {

    private static final NamespacedKey KEY_FUEL_ID = new NamespacedKey("cooking", "fuel_id");
    private final Map<String, FuelConfig.FuelData> fuels;

    public FuelInteractionHandler(Map<String, FuelConfig.FuelData> fuels) {
        this.fuels = fuels;
    }

    @Override
    public boolean handle(Player player, ItemStack handItem, StoveState state, Location location) {
        if (handItem.getType() == Material.AIR) return false;
        String fuelId = resolve(handItem);
        if (fuelId == null) return false;

        state.pendingFuelClear = false;
        if (state.fuels.size() >= 2) {
            player.sendMessage("§c燃料槽已满（最多2格）");
            return true;
        }
        FuelConfig.FuelData fd = fuels.get(fuelId);
        if (fd == null) return true;
        state.fuels.add(new FuelEntry(fuelId, fd.durationSeconds * 20));
        handItem.setAmount(handItem.getAmount() - 1);
        return true;
    }

    private String resolve(ItemStack item) {
        if (item.getItemMeta() != null) {
            PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
            String id = pdc.get(KEY_FUEL_ID, PersistentDataType.STRING);
            if (id != null && fuels.containsKey(id)) return id;
        }
        String matName = item.getType().name();
        if (fuels.containsKey(matName)) return matName;
        return null;
    }
}
