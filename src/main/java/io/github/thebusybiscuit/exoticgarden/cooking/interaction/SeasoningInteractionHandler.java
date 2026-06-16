package io.github.thebusybiscuit.exoticgarden.cooking.interaction;

import io.github.thebusybiscuit.exoticgarden.cooking.CookingKeys;
import io.github.thebusybiscuit.exoticgarden.cooking.config.SeasoningConfig;
import io.github.thebusybiscuit.exoticgarden.cooking.state.SeasoningEntry;
import io.github.thebusybiscuit.exoticgarden.cooking.state.StoveState;
import io.github.thebusybiscuit.exoticgarden.cooking.util.ItemIdUtil;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;

import java.util.List;
import java.util.Map;

/**
 * 调料交互处理器：玩家右键灶台时加调料的逻辑喵~
 * 重构后用 category 字段分支处理，去掉所有硬编码物品 ID 喵
 */
public class SeasoningInteractionHandler implements StoveInteractionHandler {

    // 调料配置 map，key 格式为带命名空间的 ID 喵
    private final Map<String, SeasoningConfig.SeasoningData> seasonings;

    public SeasoningInteractionHandler(Map<String, SeasoningConfig.SeasoningData> seasonings) {
        this.seasonings = seasonings;
    }

    @Override
    public boolean handle(Player player, ItemStack handItem, StoveState state, Location location) {
        // 喵~防御：空气物品不处理喵
        if (handItem.getType() == Material.AIR) return false;

        // 解析物品对应的调料 key 喵
        String seasoningId = resolve(handItem);
        if (seasoningId == null) return false;

        // 取到配置数据喵
        SeasoningConfig.SeasoningData data = seasonings.get(seasoningId);

        state.pendingFuelClear = false;

        // 按 category 分支处理（data=null时_POTION_走potion分支，不走seasoning默认）喵
        String category = data != null ? data.category : ("_POTION_".equals(seasoningId) ? "potion" : "seasoning");

        // 水/油/药水类不受调料槽上限限制，其余调料满10种后拒绝喵
        boolean isExempt = "water".equals(category) || "oil".equals(category) || "potion".equals(category);
        if (!isExempt && state.seasonings.size() >= 10) {
            player.sendMessage("§c调料槽已满（最多10种）");
            return true;
        }

        switch (category) {
            case "water"  -> handleWater(player, handItem, state, data);
            case "oil"    -> handleOil(player, handItem, state, data);
            case "potion" -> handlePotion(player, handItem, state, data, seasoningId);
            default       -> handleSeasoning(player, handItem, state, data, seasoningId);
        }

        return true;
    }

    /**
     * 处理水类调料：加水量、记录水来源、返还容器喵~
     */
    private void handleWater(Player player, ItemStack handItem, StoveState state,
                             SeasoningConfig.SeasoningData data) {
        // 加水量到灶台水量喵
        state.waterAmount += data.waterMl;
        // 记录水来源（去重）喵
        String src = data.displayName;
        if (!state.waterSources.contains(src)) state.waterSources.add(src);
        // 消耗手持物品并返还容器喵
        handItem.setAmount(handItem.getAmount() - 1);
        returnContainer(player, data.containerReturn);
    }

    /**
     * 处理油类调料：加油量、不加调料槽喵~
     */
    private void handleOil(Player player, ItemStack handItem, StoveState state,
                           SeasoningConfig.SeasoningData data) {
        // 加油量到灶台油量喵
        state.oilAmount += data.oilMl;
        // 油类有水分时也加水喵
        if (data.waterMl > 0) {
            state.waterAmount += data.waterMl;
            String src = data.displayName;
            if (!state.waterSources.contains(src)) state.waterSources.add(src);
        }
        // 消耗手持物品并返还容器喵
        handItem.setAmount(handItem.getAmount() - 1);
        returnContainer(player, data.containerReturn);
    }

    /**
     * 处理药水类调料：读取 PotionMeta 效果、加水量、加调料槽、返还玻璃瓶喵~
     */
    private void handlePotion(Player player, ItemStack handItem, StoveState state,
                              SeasoningConfig.SeasoningData data, String seasoningId) {
        // 喵~防御：不是 PotionMeta 时直接返回，避免 ClassCastException 喵
        if (!(handItem.getItemMeta() instanceof PotionMeta potionMeta)) return;

        // 读取自定义药水效果喵
        List<PotionEffect> effects = potionMeta.getCustomEffects();
        // 喵~防御：自定义效果为空时尝试读取基础药水效果喵
        if (effects.isEmpty() && potionMeta.getBasePotionType() != null) {
            effects = potionMeta.getBasePotionType().getPotionEffects();
        }

        // 药水加水量喵
        double waterMl = data != null ? data.waterMl : 200;
        state.waterAmount += waterMl;
        if (!state.waterSources.contains("药水")) state.waterSources.add("药水");

        // 有效果时记录到 potionEffects 喵
        if (!effects.isEmpty()) {
            state.potionEffects.addAll(effects);
        }

        // 加入调料槽，让药水在全息和AI中可见喵
        double potionWeight = data != null ? data.weightGrams : 1;
        state.seasonings.add(new SeasoningEntry(seasoningId, 0, potionWeight));

        // 消耗手持物品并返还容器喵
        handItem.setAmount(handItem.getAmount() - 1);
        String containerReturn = data != null ? data.containerReturn : "GLASS_BOTTLE";
        returnContainer(player, containerReturn);

        // 向玩家反馈喵
        if (!effects.isEmpty()) {
            player.sendMessage("§a药水已加入灶台（" + effects.size() + " 个效果，+" + (int) waterMl + "ml水）");
        } else {
            player.sendMessage("§7水瓶已加入灶台（+" + (int) waterMl + "ml水）");
        }
    }

    /**
     * 处理普通调料（seasoning 类）：加水量/油量、加入调料槽喵~
     */
    private void handleSeasoning(Player player, ItemStack handItem, StoveState state,
                                 SeasoningConfig.SeasoningData data, String seasoningId) {
        // 调料有水分时加水量喵
        if (data != null && data.waterMl > 0) {
            state.waterAmount += data.waterMl;
            String src = data.displayName;
            if (!state.waterSources.contains(src)) state.waterSources.add(src);
        }
        // 调料有油分时加油量喵
        if (data != null && data.oilMl > 0) {
            state.oilAmount += data.oilMl;
        }

        // 加入调料槽喵
        double w = data != null ? data.weightGrams : 1;
        state.seasonings.add(new SeasoningEntry(seasoningId, 0, w));

        // 消耗手持物品，并按配置返还容器喵
        handItem.setAmount(handItem.getAmount() - 1);
        if (data != null) returnContainer(player, data.containerReturn);
    }

    /**
     * 解析物品对应的调料 key喵~
     * 查找优先级：
     * 1. PDC 中已有 SEASONING_ID 且在 map 中 → 直接返回
     * 2. ItemIdUtil.toKey() 生成带命名空间 key 在 map 中 → 返回
     * 3. 药水类 Material（非水瓶）→ 返回 "_POTION_"
     * 注意：水瓶 Material=POTION 但无药水效果，会在步骤2命中 minecraft:WATER_BOTTLE 喵
     */
    private String resolve(ItemStack item) {
        // 1. PDC 中的 SEASONING_ID 优先查找喵
        if (item.getItemMeta() != null) {
            PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
            String id = pdc.get(CookingKeys.SEASONING_ID, PersistentDataType.STRING);
            if (id != null && seasonings.containsKey(id)) return id;
        }

        // 2. 用 ItemIdUtil 生成带命名空间的 key 在 map 中查找（含水瓶 minecraft:POTION 等）喵
        String itemKey = ItemIdUtil.toKey(item);
        if (itemKey != null && seasonings.containsKey(itemKey)) return itemKey;

        // 3. 药水类 Material（POTION/SPLASH_POTION/LINGERING_POTION）且在 map 里没有精确 key
        //    → 说明是真实药水而非水瓶，映射到虚拟标识符 _POTION_ 喵
        Material mat = item.getType();
        if (mat == Material.POTION || mat == Material.SPLASH_POTION || mat == Material.LINGERING_POTION) {
            return "_POTION_";
        }

        // 没有匹配的调料喵
        return null;
    }

    /**
     * 通用容器返还方法喵~
     * 输入：containerReturn 字符串（BUCKET/GLASS_BOTTLE/none）
     */
    private void returnContainer(Player player, String containerReturn) {
        // 喵~防御：containerReturn 为 null 时视为 none 喵
        if (containerReturn == null || "none".equalsIgnoreCase(containerReturn)) return;
        if ("BUCKET".equalsIgnoreCase(containerReturn)) {
            // 返还空桶喵
            returnBackItem(player, new ItemStack(Material.BUCKET));
        } else if ("GLASS_BOTTLE".equalsIgnoreCase(containerReturn)) {
            // 返还玻璃瓶喵
            returnBackItem(player, new ItemStack(Material.GLASS_BOTTLE));
        }
        // 其他未知返还类型静默忽略喵
    }

    /**
     * 将物品还给玩家，背包满时掉落到脚下喵~
     */
    private void returnBackItem(Player player, ItemStack item) {
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(item);
        // 喵~防御：背包满时把多余的物品丢到地面喵
        if (!leftover.isEmpty() && player.getLocation().getWorld() != null) {
            leftover.values().forEach(it ->
                player.getLocation().getWorld().dropItemNaturally(player.getLocation(), it));
        }
    }
}
