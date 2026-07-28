package io.github.thebusybiscuit.exoticgarden.cooking.interaction;

import io.github.thebusybiscuit.exoticgarden.cooking.CookingKeys;
import io.github.thebusybiscuit.exoticgarden.cooking.block.StoveBlock;
import io.github.thebusybiscuit.exoticgarden.cooking.config.FuelConfig;
import io.github.thebusybiscuit.exoticgarden.cooking.config.IngredientConfig;
import io.github.thebusybiscuit.exoticgarden.cooking.state.ActiveFace;
import io.github.thebusybiscuit.exoticgarden.cooking.state.FoodState;
import io.github.thebusybiscuit.exoticgarden.cooking.state.FuelEntry;
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
    // 燃料配置 map，用于入槽时收集当前燃料的 effectDisplayName 喵
    private final Map<String, FuelConfig.FuelData> fuels;

    public IngredientInteractionHandler(Map<String, IngredientConfig.IngredientData> ingredients,
                                        Map<String, FuelConfig.FuelData> fuels) {
        this.ingredients = ingredients;
        // 喵~防御：fuels 不应为 null，外部传入喵
        this.fuels = fuels;
    }

    @Override
    public boolean handle(Player player, ItemStack handItem, StoveState state, Location location) {
        if (handItem.getType() == Material.AIR) return false;
        // 喵~防御：菜肴物品（有DISH_HUNGER PDC）不允许加入灶台喵
        if (handItem.getItemMeta() != null) {
            org.bukkit.persistence.PersistentDataContainer pdc = handItem.getItemMeta().getPersistentDataContainer();
            if (pdc.has(io.github.thebusybiscuit.exoticgarden.cooking.CookingKeys.DISH_HUNGER,
                    org.bukkit.persistence.PersistentDataType.INTEGER)) {
                player.sendMessage("§c菜肴不能作为食材放入灶台喵~");
                return true;
            }
        }
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
        boolean isExpired = false;
        long foodTimestamp = 0L;
        int ingredientShelfLife = 0;
        if (handItem.getItemMeta() != null) {
            PersistentDataContainer pdc = handItem.getItemMeta().getPersistentDataContainer();
            String rawState = pdc.get(CookingKeys.FOOD_STATE, PersistentDataType.STRING);
            if (rawState != null) {
                try { foodState = FoodState.valueOf(rawState); } catch (IllegalArgumentException ignored) {}
            }
            // 获取统一服务，确保灶台记录的期限与食用、lore和机器拦截完全一致喵
            io.github.thebusybiscuit.exoticgarden.cooking.FoodExpiryService foodExpiryService =
                    io.github.thebusybiscuit.exoticgarden.cooking.CookingModule.getFoodExpiryService();
            // 读取物品生产时间以供成菜后追溯保质期进度喵
            Long timestamp = pdc.get(CookingKeys.FOOD_TIMESTAMP, PersistentDataType.LONG);
            // 仅对带时间戳且可解析有效期限的物品写入过期快照喵
            if (timestamp != null && foodExpiryService != null) {
                // 解析统一服务计算出的时间戳和保质期信息喵
                java.util.Optional<io.github.thebusybiscuit.exoticgarden.cooking.FoodExpiryService.ExpiryInfo> expiryInfo =
                        foodExpiryService.getExpiryInfo(handItem);
                // 仅在物品属于本系统且具有有效期限时记录给 AI 和成菜流程喵
                if (expiryInfo.isPresent()) {
                    // 读取不可变期限快照喵
                    io.github.thebusybiscuit.exoticgarden.cooking.FoodExpiryService.ExpiryInfo resolvedInfo = expiryInfo.get();
                    // 按当前时间实时判断是否已过期喵
                    isExpired = resolvedInfo.isExpiredAt(System.currentTimeMillis());
                    // 保存原始生产时间戳喵
                    foodTimestamp = resolvedInfo.timestampMillis();
                    // 保存统一配置解析的保质期分钟数喵
                    ingredientShelfLife = resolvedInfo.shelfLifeMinutes();
                }
            }
        }

        // 将食材放入空槽，并同步灶台物理槽位显示喵
        IngredientSlot slot = new IngredientSlot(ingId, foodState, 0, 0, ActiveFace.FRONT, 0);
        slot.isExpired = isExpired;
        slot.foodTimestamp = foodTimestamp;
        slot.shelfLifeMinutes = ingredientShelfLife;

        // 收集当前灶台已有燃料的 hint（AI提示词），非空时加入食材的 fuelEffects 喵
        for (FuelEntry fe : state.fuels) {
            FuelConfig.FuelData fd = fuels.get(fe.fuelId);
            // 喵~防御：fd 为 null 时跳过，hint 为空时也跳过喵
            if (fd == null) continue;
            String fuelHint = fd.hint;
            if (fuelHint != null && !fuelHint.isEmpty() && !slot.fuelEffects.contains(fuelHint)) {
                // 避免重复添加同一种风味喵
                slot.fuelEffects.add(fuelHint);
            }
        }

        state.slots[emptySlot] = slot;
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
