package io.github.thebusybiscuit.exoticgarden.cooking.interaction;

import io.github.thebusybiscuit.exoticgarden.ExoticGarden;
import io.github.thebusybiscuit.exoticgarden.cooking.CookingKeys;
import io.github.thebusybiscuit.exoticgarden.cooking.ai.AiClient;
import io.github.thebusybiscuit.exoticgarden.cooking.ai.DishGenerator;
import io.github.thebusybiscuit.exoticgarden.cooking.config.FuelConfig;
import io.github.thebusybiscuit.exoticgarden.cooking.config.IngredientConfig;
import io.github.thebusybiscuit.exoticgarden.cooking.config.SeasoningConfig;
import io.github.thebusybiscuit.exoticgarden.cooking.state.*;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class BowlInteractionHandler implements StoveInteractionHandler {

    private static final org.bukkit.NamespacedKey KEY_SF_ITEM =
            new org.bukkit.NamespacedKey("slimefun", "slimefun_item");

    private final Map<String, FuelConfig.FuelData> fuels;
    private final Map<String, IngredientConfig.IngredientData> ingredients;
    private final Map<String, SeasoningConfig.SeasoningData> seasonings;
    // AI 客户端，从 config.yml 读取 api_key/base_url/model 初始化喵
    private final AiClient aiClient;

    public BowlInteractionHandler(Map<String, FuelConfig.FuelData> fuels,
                                  Map<String, IngredientConfig.IngredientData> ingredients,
                                  Map<String, SeasoningConfig.SeasoningData> seasonings) {
        this.fuels = fuels;
        this.ingredients = ingredients;
        this.seasonings = seasonings;
        // 喵~从 ExoticGarden config.yml 读取 AI 配置初始化客户端喵
        ExoticGarden plugin = ExoticGarden.getInstance();
        String apiKey = plugin != null ? plugin.getConfig().getString("cooking.ai_api_key", "") : "";
        String baseUrl = plugin != null ? plugin.getConfig().getString("cooking.ai_base_url", "https://api.openai.com/v1") : "https://api.openai.com/v1";
        String model  = plugin != null ? plugin.getConfig().getString("cooking.ai_model", "gpt-4o-mini") : "gpt-4o-mini";
        boolean thinking = plugin != null && plugin.getConfig().getBoolean("cooking.ai_thinking_enabled", false);
        int thinkingBudget = plugin != null ? plugin.getConfig().getInt("cooking.ai_thinking_budget", 16000) : 16000;
        this.aiClient = new AiClient(apiKey, baseUrl, model,
            plugin != null ? plugin.getLogger() : java.util.logging.Logger.getLogger("BowlHandler"),
            thinking, thinkingBudget);
    }

    @Override
    public boolean handle(Player player, ItemStack handItem, StoveState state, Location location) {
        if (handItem.getType() != Material.BOWL) return false;
        if (handItem.getItemMeta() != null) {
            PersistentDataContainer pdc = handItem.getItemMeta().getPersistentDataContainer();
            if (pdc.has(KEY_SF_ITEM, PersistentDataType.STRING))
                return false;
        }

        if (state.cookingInProgress) {
            player.sendMessage("§e烹饪进行中，请等待完成...");
            return true;
        }

        state.pendingFuelClear = false;

        List<DishGenerator.IngredientInfo> ingInfos = new ArrayList<>();
        List<DishGenerator.SeasoningInfo> seaInfos = new ArrayList<>();
        double totalWeight = 0;

        long now = System.currentTimeMillis();

        for (IngredientSlot slot : state.slots) {
            if (slot == null) continue;
            IngredientConfig.IngredientData data = ingredients.get(slot.ingredientId);
            String displayName = data != null ? data.displayName : slot.ingredientId;
            double weight = data != null ? data.weightGrams : 100;
            int doneness = (int) Math.round(Math.max(slot.frontDoneness, slot.backDoneness) * 100);
            int frontDoneness = (int) Math.round(slot.frontDoneness * 100);
            int backDoneness  = (int) Math.round(slot.backDoneness * 100);
            int foodPts = data != null ? (int) data.foodPoints : 0;
            int sat = data != null ? (int) data.saturation : 0;
            String hint = data != null ? data.hint : "";
            totalWeight += weight;
            // 喵~以盛菜时为准，计算食材已过期多少分钟（foodTimestamp>0才有效）喵
            long ingExpiredMinutes = -1;
            if (slot.isExpired && slot.foodTimestamp > 0 && slot.shelfLifeMinutes > 0) {
                long diffMin = (now - slot.foodTimestamp) / 60000L;
                ingExpiredMinutes = Math.max(0, diffMin - slot.shelfLifeMinutes);
            }
            ingInfos.add(new DishGenerator.IngredientInfo(
                displayName, foodStateDisplay(slot.state),
                doneness, frontDoneness, backDoneness,
                weight, foodPts, sat, hint, slot.fuelEffects, slot.isExpired,
                ingExpiredMinutes, slot.shelfLifeMinutes));
        }

        for (SeasoningEntry se : state.seasonings) {
            SeasoningConfig.SeasoningData sd = seasonings.get(se.seasoningId);
            // 水/油分类的调料不显示在调料列表中（它们已计入水量/油量）喵
            if (sd != null && ("water".equals(sd.category) || "oil".equals(sd.category))) continue;
            String displayName = sd != null ? sd.displayName : se.seasoningId;
            String hint = sd != null ? sd.hint : "";
            totalWeight += se.weight;
            int progress = sd != null && sd.hasDoneness ? (int) Math.round(se.progress * 100) : -1;
            double mlAmt = sd != null ? sd.waterMl + sd.oilMl : 0;
            // 喵~判断调料是否过期（addedTimestamp>0 且 shelfLifeMinutes>0），以盛菜时为准喵
            boolean seaExpired = false;
            long seaExpiredMinutes = -1;
            int seaShelfLife = sd != null ? sd.shelfLifeMinutes : 0;
            if (seaShelfLife > 0 && se.addedTimestamp > 0) {
                long diffMin = (now - se.addedTimestamp) / 60000L;
                seaExpired = diffMin >= seaShelfLife;
                if (seaExpired) seaExpiredMinutes = Math.max(0, diffMin - seaShelfLife);
            }
            seaInfos.add(new DishGenerator.SeasoningInfo(
                displayName,
                sd != null && sd.hasDoneness ? progress : null,
                mlAmt, hint, seaExpired, seaExpiredMinutes, seaShelfLife));
        }

        // 喵~fuelEffects 已绑定到各食材 slot.fuelEffects，不需要全局 fxList 喵

        if (ingInfos.isEmpty()) {
            player.sendMessage("§c灶台上没有食材");
            return true;
        }

        List<String> waterSrcs = new ArrayList<>(state.waterSources);
        double totalWeightWithWater = totalWeight + state.waterAmount + state.oilAmount;
        String[] prompts = DishGenerator.buildPrompt(ingInfos, seaInfos,
            state.waterAmount, state.oilAmount, totalWeightWithWater, waterSrcs,
            state.potionEffects, state.currentTemp);

        // 喵~检查 AI 是否启用，未启用时发送调试提示词，启用时调用 AI 喵
        ExoticGarden pluginInst = ExoticGarden.getInstance();
        boolean aiEnabled = pluginInst != null && pluginInst.getConfig().getBoolean("cooking.ai_enabled", false);

        if (!aiEnabled) {
            // debug 模式：展示提示词供调试喵
            player.sendMessage("§6§l──── AI 提示词调试 ────");
            player.sendMessage("§b[System] §f" + prompts[0]);
            player.sendMessage("§a[User]   §f" + prompts[1]);
            player.sendMessage("§6§l──────────────────────");
            player.sendMessage("§7[debug] ai_enabled=false，未调用 AI 喵~");
            return true;
        }

        // 喵~冻结灶台，不允许任何交互（温度/食材状态不变）喵
        state.frozen = true;
        state.frozenReason = null;

        // 喵~消耗碗喵
        handItem.setAmount(handItem.getAmount() - 1);

        final java.util.UUID playerUUID = player.getUniqueId();
        final Location loc2 = location;
        player.sendMessage("§6正在生成菜肴... 请等待喵~");

        // 喵~异步调用 AI，主线程不阻塞喵
        aiClient.generateDish(prompts[0], prompts[1]).thenAccept(result -> {
            // 喵~回调在异步线程，需要切回主线程操作Bukkit API喵
            Bukkit.getScheduler().runTask(ExoticGarden.getInstance(), () -> {
                // 喵~防御：灶台可能已被破坏或重置喵
                ExoticGarden plugin = ExoticGarden.getInstance();
                if (plugin == null) return;
                Player p = Bukkit.getPlayer(playerUUID);
                String pName = p != null ? p.getName() : "未知玩家";
                // 喵~AI成功：解冻，清空食材/调料/水油/药水（保留燃料和温度）喵
                state.frozen = false;
                state.frozenReason = null;
                Arrays.fill(state.slots, null);
                state.seasonings.clear();
                state.waterSources.clear();
                state.potionEffects.clear();
                state.waterAmount = 0;
                state.oilAmount = 0;
                state.spatulaBoostTicksLeft = 0;
                io.github.thebusybiscuit.exoticgarden.cooking.block.StoveBlock.syncCampfireSlots(loc2, state);

                // 喵~用 AI 返回的 icon 字段决定物品材质，缺省谜之炖菜喵
                Material iconMat = Material.getMaterial(result.icon.toUpperCase());
                if (iconMat == null || iconMat.isAir()) iconMat = Material.SUSPICIOUS_STEW;
                ItemStack dish = new ItemStack(iconMat, 1);
                ItemMeta meta = dish.getItemMeta();
                if (meta != null) {
                    // 显示名：菜名喵
                    meta.setDisplayName(result.name);
                    long nowMs = System.currentTimeMillis();
                    // lore：品质行 + 保质期 + 生产日期 + description 换行展开喵
                    List<String> lore = new ArrayList<>();
                    lore.add("§7品质: " + result.quality
                        + "  §7饱食: §f" + result.hunger
                        + "  §7饱和: §f" + String.format("%.1f", result.saturation)
                        + "  §7份量: §f" + result.servings);
                    lore.add("§8保质期: §f" + io.github.thebusybiscuit.exoticgarden.cooking.FoodTagListener.formatShelfLife(result.shelfLifeMinutes));
                    lore.add("§8生产日期: §f" + io.github.thebusybiscuit.exoticgarden.cooking.FoodTagListener.formatTimestamp(nowMs));
                    lore.addAll(DishGenerator.descriptionToLore(result.description));
                    meta.setLore(lore);
                    // PDC：写入菜肴数据供 DishConsumptionListener 读取喵
                    PersistentDataContainer pdc = meta.getPersistentDataContainer();
                    pdc.set(CookingKeys.DISH_HUNGER,            PersistentDataType.INTEGER, result.hunger);
                    pdc.set(CookingKeys.DISH_SATURATION,        PersistentDataType.DOUBLE,  result.saturation);
                    pdc.set(CookingKeys.DISH_QUALITY,           PersistentDataType.STRING,  result.quality);
                    pdc.set(CookingKeys.DISH_DESCRIPTION,       PersistentDataType.STRING,  result.description);
                    pdc.set(CookingKeys.DISH_SHELF_LIFE,        PersistentDataType.INTEGER, result.shelfLifeMinutes);
                    pdc.set(CookingKeys.DISH_SERVINGS_REMAINING,PersistentDataType.INTEGER, result.servings);
                    pdc.set(CookingKeys.FOOD_TIMESTAMP,         PersistentDataType.LONG,    nowMs);
                    if (!result.effects.isEmpty()) {
                        pdc.set(CookingKeys.DISH_EFFECTS, PersistentDataType.STRING,
                            String.join("|", result.effects));
                    }
                    // 喵~食用句子：多句以 | 分隔存入 PDC 喵
                    if (!result.flavorTexts.isEmpty()) {
                        pdc.set(CookingKeys.DISH_FLAVOR_TEXTS, PersistentDataType.STRING,
                            String.join("|", result.flavorTexts));
                    }
                    dish.setItemMeta(meta);
                }

                // 喵~放入玩家背包，背包满则掉落在玩家位置喵
                if (p != null) {
                    java.util.Map<Integer, ItemStack> leftover = p.getInventory().addItem(dish);
                    leftover.values().forEach(it -> p.getWorld().dropItemNaturally(p.getLocation(), it));
                    p.sendMessage("§a[AI] 菜肴生成完成: §f" + result.name);
                } else {
                    if (loc2.getWorld() != null) loc2.getWorld().dropItemNaturally(loc2, dish);
                }
                plugin.getLogger().info("[Cooking] " + pName + " 生成菜肴: " + result.name + " 品质:" + result.quality);
            });
        }).exceptionally(ex -> {
            // 喵~AI失败：保持冻结，记录原因，等待玩家右键解冻喵
            Bukkit.getScheduler().runTask(ExoticGarden.getInstance(), () -> {
                String reason = ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage();
                if (reason != null && reason.length() > 100) reason = reason.substring(0, 100) + "...";
                state.frozenReason = reason;
                // 喵~frozen保持true，玩家右键时解冻喵
                Player p = Bukkit.getPlayer(playerUUID);
                if (p != null) {
                    p.sendMessage("§c[AI] 菜肴生成失败: " + reason);
                    p.sendMessage("§e右键灶台可解冻继续交互喵~");
                }
                ExoticGarden plugin = ExoticGarden.getInstance();
                if (plugin != null) plugin.getLogger().warning("[Cooking] AI生成失败: " + reason);
            });
            return null;
        });

        return true;
    }

    private String foodStateDisplay(FoodState state) {
        return switch (state) {
            case WHOLE     -> "完整";
            case SLICED    -> "切片";
            case JULIENNED -> "切条";
            case DICED     -> "切丁";
            case SAUCE     -> "酱汁";
        };
    }
}
