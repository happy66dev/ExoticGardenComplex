package io.github.thebusybiscuit.exoticgarden.cooking.interaction;

import io.github.thebusybiscuit.exoticgarden.cooking.CookingKeys;
import io.github.thebusybiscuit.exoticgarden.cooking.block.StoveBlock;
import io.github.thebusybiscuit.exoticgarden.cooking.config.IngredientConfig;
import io.github.thebusybiscuit.exoticgarden.cooking.state.ActiveFace;
import io.github.thebusybiscuit.exoticgarden.cooking.state.FoodState;
import io.github.thebusybiscuit.exoticgarden.cooking.state.IngredientSlot;
import io.github.thebusybiscuit.exoticgarden.cooking.state.StoveState;
import io.github.thebusybiscuit.exoticgarden.cooking.util.ItemIdUtil;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.Map;

public class IngredientInteractionHandler implements StoveInteractionHandler {

    private final Map<String, IngredientConfig.IngredientData> ingredients;

    public IngredientInteractionHandler(Map<String, IngredientConfig.IngredientData> ingredients) {
        this.ingredients = ingredients;
    }

    @Override
    public boolean handle(Player player, ItemStack handItem, StoveState state, Location location) {
        // 喵~防御：空气物品跳过喵
        if (handItem.getType() == Material.AIR) return false;
        String ingId = resolveIngredientId(handItem);
        if (ingId == null) return false;

        state.pendingFuelClear = false;
        int emptySlot = -1;
        for (int i = 0; i < state.slots.length; i++) {
            if (state.slots[i] == null) { emptySlot = i; break; }
        }
        if (emptySlot == -1) {
            player.sendMessage("§c食材槽已满（最多4格）");
            return true;
        }

        // 读取物品已有的切割状态（WHOLE/SLICED/DICED），默认 WHOLE 喵
        FoodState foodState = FoodState.WHOLE;
        if (handItem.getItemMeta() != null) {
            PersistentDataContainer pdc = handItem.getItemMeta().getPersistentDataContainer();
            String rawState = pdc.get(CookingKeys.FOOD_STATE, PersistentDataType.STRING);
            if (rawState != null) {
                try { foodState = FoodState.valueOf(rawState); } catch (IllegalArgumentException ignored) {}
            }
        }

        // 将食材放入空槽，并同步灶台物理槽位显示喵
        state.slots[emptySlot] = new IngredientSlot(ingId, foodState, 0, 0, ActiveFace.FRONT, 0);
        handItem.setAmount(handItem.getAmount() - 1);
        StoveBlock.syncCampfireSlots(location, state);
        return true;
    }

    /**
     * 解析物品对应的食材 key喵~
     * 优先级：PDC INGREDIENT_ID → ItemIdUtil.toKey()（含 slimefun:ID 和 minecraft:MATERIAL）
     */
    private String resolveIngredientId(ItemStack item) {
        if (item.getItemMeta() != null) {
            PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();

            // 1. PDC 中已有 INGREDIENT_ID 且在 map 里 → 直接返回喵
            String id = pdc.get(CookingKeys.INGREDIENT_ID, PersistentDataType.STRING);
            if (id != null && ingredients.containsKey(id)) return id;
        }

        // 2. 用 ItemIdUtil 生成带命名空间 key：SF 物品 → "slimefun:ID"，原版 → "minecraft:MATERIAL" 喵
        String itemKey = ItemIdUtil.toKey(item);
        if (itemKey != null && ingredients.containsKey(itemKey)) return itemKey;

        return null;
    }
}
