package io.github.thebusybiscuit.exoticgarden.cooking.interaction;

import io.github.thebusybiscuit.exoticgarden.cooking.CookingKeys;
import io.github.thebusybiscuit.exoticgarden.cooking.config.FuelConfig;
import io.github.thebusybiscuit.exoticgarden.cooking.state.FuelEntry;
import io.github.thebusybiscuit.exoticgarden.cooking.state.StoveState;
import io.github.thebusybiscuit.exoticgarden.cooking.util.ItemIdUtil;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.Map;

public class FuelInteractionHandler implements StoveInteractionHandler {

    private final Map<String, FuelConfig.FuelData> fuels;

    public FuelInteractionHandler(Map<String, FuelConfig.FuelData> fuels) {
        this.fuels = fuels;
    }

    @Override
    public boolean handle(Player player, ItemStack handItem, StoveState state, Location location) {
        // 喵~防御：空气物品跳过喵
        if (handItem.getType() == Material.AIR) return false;
        String fuelId = resolve(handItem);
        if (fuelId == null) return false;

        state.pendingFuelClear = false;
        if (state.fuels.size() >= 2) {
            player.sendMessage("§c燃料槽已满（最多2格）");
            return true;
        }
        FuelConfig.FuelData fd = fuels.get(fuelId);
        // 喵~防御：fuelId 在 map 中找不到数据时跳过喵
        if (fd == null) return false;
        state.fuels.add(new FuelEntry(fuelId, (int)(fd.durationSeconds * 20)));
        handItem.setAmount(handItem.getAmount() - 1);
        return true;
    }

    /**
     * 解析物品对应的燃料 key喵~
     * 优先级：PDC 中 FUEL_ID → ItemIdUtil.toKey() 生成带命名空间 key
     */
    private String resolve(ItemStack item) {
        // 1. PDC 中已有 FUEL_ID 且在 map 里 → 直接返回喵
        if (item.getItemMeta() != null) {
            PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
            String id = pdc.get(CookingKeys.FUEL_ID, PersistentDataType.STRING);
            if (id != null && fuels.containsKey(id)) return id;
        }
        // 2. 用 ItemIdUtil 生成带命名空间 key（如 minecraft:OAK_LOG）在 map 中查找喵
        String itemKey = ItemIdUtil.toKey(item);
        if (itemKey != null && fuels.containsKey(itemKey)) return itemKey;
        return null;
    }
}
