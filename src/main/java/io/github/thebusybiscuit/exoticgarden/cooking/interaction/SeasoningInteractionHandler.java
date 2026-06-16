package io.github.thebusybiscuit.exoticgarden.cooking.interaction;

import io.github.thebusybiscuit.exoticgarden.cooking.config.SeasoningConfig;
import io.github.thebusybiscuit.exoticgarden.cooking.state.SeasoningEntry;
import io.github.thebusybiscuit.exoticgarden.cooking.state.StoveState;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
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

        // 喵~防御：调料槽满时仍允许加水/加油（水和油不占调料槽），但普通调料拒绝
        boolean isWaterOrOil = "WATER_BUCKET".equals(seasoningId) || "WATER".equals(seasoningId)
                || "OIL".equals(seasoningId);
        if (!isWaterOrOil && state.seasonings.size() >= 10) {
            player.sendMessage("§c调料槽已满（最多10种）");
            return true;
        }

        if ("WATER_BUCKET".equals(seasoningId)) {
            state.waterAmount += 1000;
            if (!state.waterSources.contains("水(桶)")) state.waterSources.add("水(桶)");
            handItem.setAmount(handItem.getAmount() - 1);
            returnBackItem(player, new ItemStack(Material.BUCKET));
            return true;
        }

        if ("MILK_BUCKET".equals(seasoningId)) {
            // 牛奶作为辅料：走通用调料槽，同时返还空桶喵
            SeasoningConfig.SeasoningData milkData = seasonings.get(seasoningId);
            if (milkData != null && milkData.waterMl > 0) {
                state.waterAmount += milkData.waterMl;
                String src = milkData.displayName;
                if (!state.waterSources.contains(src)) state.waterSources.add(src);
            }
            double milkWeight = milkData != null ? milkData.weightGrams : 250;
            state.seasonings.add(new SeasoningEntry(seasoningId, 0, milkWeight));
            handItem.setAmount(handItem.getAmount() - 1);
            returnBackItem(player, new ItemStack(Material.BUCKET));
            return true;
        }

        // 蜂蜜瓶：作为辅料加入调料槽，同时返还玻璃瓶喵
        if ("HONEY_BOTTLE".equals(seasoningId)) {
            SeasoningConfig.SeasoningData honeyData = seasonings.get(seasoningId);
            if (honeyData != null && honeyData.waterMl > 0) {
                state.waterAmount += honeyData.waterMl;
                String src = honeyData.displayName;
                if (!state.waterSources.contains(src)) state.waterSources.add(src);
            }
            double honeyWeight = honeyData != null ? honeyData.weightGrams : 20;
            state.seasonings.add(new SeasoningEntry(seasoningId, 0, honeyWeight));
            handItem.setAmount(handItem.getAmount() - 1);
            returnBackItem(player, new ItemStack(Material.GLASS_BOTTLE));
            return true;
        }

        if ("WATER".equals(seasoningId)) {
            state.waterAmount += 250;
            if (!state.waterSources.contains("水")) state.waterSources.add("水");
            handItem.setAmount(handItem.getAmount() - 1);
            returnBackItem(player, new ItemStack(Material.GLASS_BOTTLE));
            return true;
        }

        if ("OIL".equals(seasoningId)) {
            state.oilAmount += 100;
            handItem.setAmount(handItem.getAmount() - 1);
            return true;
        }

        // 药水调料：读取PotionMeta中的所有药水效果并存入state，食用菜肴时应用喵
        if ("_POTION_".equals(seasoningId)) {
            if (!(handItem.getItemMeta() instanceof PotionMeta potionMeta)) return true;
            List<PotionEffect> effects = potionMeta.getCustomEffects();
            // 喵~防御：自定义效果为空时尝试读取基础药水效果喵
            if (effects.isEmpty() && potionMeta.getBasePotionType() != null) {
                effects = potionMeta.getBasePotionType().getPotionEffects();
            }
            if (effects.isEmpty()) {
                player.sendMessage("§c此药水没有可添加的效果");
                return true;
            }
            state.potionEffects.addAll(effects);
            handItem.setAmount(handItem.getAmount() - 1);
            returnBackItem(player, new ItemStack(Material.GLASS_BOTTLE));
            player.sendMessage("§a药水效果已加入灶台（共 " + state.potionEffects.size() + " 个效果）");
            return true;
        }

        SeasoningConfig.SeasoningData data = seasonings.get(seasoningId);

        if (data != null && data.waterMl > 0) {
            state.waterAmount += data.waterMl;
            String src = data.displayName;
            if (!state.waterSources.contains(src)) state.waterSources.add(src);
        }

        double w = data != null ? data.weightGrams : 1;
        state.seasonings.add(new SeasoningEntry(seasoningId, 0, w));
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
        if (mat == Material.MILK_BUCKET) return "MILK_BUCKET";
        // 蜂蜜瓶使用后返还玻璃瓶，作为辅料处理喵
        if (mat == Material.HONEY_BOTTLE) return "HONEY_BOTTLE";
        if (mat == Material.POTION || mat == Material.SPLASH_POTION || mat == Material.LINGERING_POTION) {
            return "_POTION_";
        }

        String matName = mat.name();
        if (seasonings.containsKey(matName)) return matName;
        return null;
    }

    private void returnBackItem(Player player, ItemStack item) {
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(item);
        if (!leftover.isEmpty() && player.getLocation().getWorld() != null) {
            leftover.values().forEach(it ->
                player.getLocation().getWorld().dropItemNaturally(player.getLocation(), it));
        }
    }
}
