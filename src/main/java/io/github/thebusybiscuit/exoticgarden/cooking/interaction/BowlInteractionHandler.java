package io.github.thebusybiscuit.exoticgarden.cooking.interaction;

import io.github.thebusybiscuit.exoticgarden.ExoticGarden;
import io.github.thebusybiscuit.exoticgarden.cooking.CookingKeys;
import io.github.thebusybiscuit.exoticgarden.cooking.ai.AiClient;
import io.github.thebusybiscuit.exoticgarden.cooking.ai.DishGenerator;
import io.github.thebusybiscuit.exoticgarden.cooking.config.FuelConfig;
import io.github.thebusybiscuit.exoticgarden.cooking.config.IngredientConfig;
import io.github.thebusybiscuit.exoticgarden.cooking.config.SeasoningConfig;
import io.github.thebusybiscuit.exoticgarden.cooking.block.StoveBlock;
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
        // 为本次请求生成唯一令牌，迟到回调必须匹配令牌才可修改灶台喵
        final java.util.UUID requestId = java.util.UUID.randomUUID();
        state.activeAiRequestId = requestId;
        // 记录本次有效交互时间，防止冻结状态被空闲回收喵
        state.lastActiveAtMillis = System.currentTimeMillis();

        // 喵~消耗碗喵
        handItem.setAmount(handItem.getAmount() - 1);

        final java.util.UUID playerUUID = player.getUniqueId();
        // 克隆方块坐标，避免异步回调持有可变Location实例喵
        final Location loc2 = location.clone();
        player.sendMessage("§6正在生成菜肴... 请等待喵~");

        // 发起受限异步AI请求，并保存future供灶台销毁或插件停用时取消喵
        java.util.concurrent.CompletableFuture<DishGenerator.DishResult> dishFuture = aiClient.generateDish(prompts[0], prompts[1]);
        state.activeAiFuture = dishFuture;
        dishFuture.thenAccept(result -> {
            // 喵~防御：插件停用时不再向已卸载调度器提交任务喵
            ExoticGarden callbackPlugin = ExoticGarden.getInstance();
            if (callbackPlugin == null || !callbackPlugin.isEnabled()) return;
            // 喵~回调在异步线程，需要切回主线程操作Bukkit API喵
            Bukkit.getScheduler().runTask(callbackPlugin, () -> {
                ExoticGarden plugin = ExoticGarden.getInstance();
                // 喵~防御：灶台已破坏、请求已取消或插件已停用时拒绝迟到成功回调喵
                if (!isCurrentRequest(plugin, state, requestId, loc2)) return;
                Player p = Bukkit.getPlayer(playerUUID);
                String pName = p != null ? p.getName() : "未知玩家";
                // AI成功：清除请求令牌并解冻，再清空食材/调料/水油/药水（保留燃料和温度）喵
                state.activeAiRequestId = null;
                state.activeAiFuture = null;
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
                    // 喵~刚生成时progress=0，无减少量；定时扫描会随时间更新减少量显示喵
                    lore.add(io.github.thebusybiscuit.exoticgarden.cooking.DishConsumptionListener
                        .buildNutritionLine(result.quality, result.hunger, result.saturation,
                            result.servings, 0.0));
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

                // 喵~在物品上直接写入 CONSUMABLE 数据组件，让原版处理进食耗时和音效喵
                // 必须在 setItemMeta 之后再调用 setData，否则 meta 会覆盖组件喵
                net.kyori.adventure.key.Key soundKey;
                try {
                    // 喵~防御：Key.key() 要求 namespace:value 格式，格式非法时回退标准音效喵
                    soundKey = net.kyori.adventure.key.Key.key(result.eatSound);
                } catch (Exception keyEx) {
                    soundKey = net.kyori.adventure.key.Key.key("minecraft:entity.generic.eat");
                }
                io.papermc.paper.datacomponent.item.Consumable consumable =
                    io.papermc.paper.datacomponent.item.Consumable.consumable()
                        .consumeSeconds((float) result.consumeSeconds)
                        .sound(soundKey)
                        .animation(io.papermc.paper.datacomponent.item.consumable.ItemUseAnimation.EAT)
                        .build();
                dish.setData(io.papermc.paper.datacomponent.DataComponentTypes.CONSUMABLE, consumable);

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
            // 喵~防御：插件停用时不再向已卸载调度器提交失败处理任务喵
            ExoticGarden callbackPlugin = ExoticGarden.getInstance();
            if (callbackPlugin == null || !callbackPlugin.isEnabled()) return null;
            // AI失败：保持冻结，记录原因，等待玩家右键解冻喵
            Bukkit.getScheduler().runTask(callbackPlugin, () -> {
                ExoticGarden plugin = ExoticGarden.getInstance();
                // 喵~防御：仅当前有效请求才能写入失败状态，避免旧请求覆盖新一轮烹饪喵
                if (!isCurrentRequest(plugin, state, requestId, loc2)) return;
                String reason = ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage();
                if (reason == null || reason.isBlank()) reason = "AI 请求失败";
                if (reason.length() > 100) reason = reason.substring(0, 100) + "...";
                // 失败不清除冻结和令牌，玩家右键解冻时会明确放弃本次失败请求喵
                state.frozenReason = reason;
                state.activeAiFuture = null;
                Player p = Bukkit.getPlayer(playerUUID);
                if (p != null) {
                    p.sendMessage("§c[AI] 菜肴生成失败: " + reason);
                    p.sendMessage("§e右键灶台可解冻继续交互喵~");
                }
                plugin.getLogger().warning("[Cooking] AI生成失败: " + reason);
            });
            return null;
        });

        return true;
    }

    /**
     * 确认异步回调仍属于当前存在的同一台灶台和同一轮AI请求喵~
     * 输入：plugin-插件实例，state-回调捕获状态，requestId-请求令牌，location-灶台坐标
     * 输出：仅在所有生命周期条件仍有效时返回 true
     * 边界：区块未加载、灶台被破坏、插件停用和请求被替换均必须拒绝回调喵
     */
    private boolean isCurrentRequest(ExoticGarden plugin, StoveState state,
                                     java.util.UUID requestId, Location location) {
        // 喵~防御：插件不存在或已停用时禁止任何Bukkit状态写入喵
        if (plugin == null || !plugin.isEnabled()) return false;
        // 喵~防御：世界不存在或区块未加载时不能访问方块或发放物品喵
        if (location.getWorld() == null
                || !location.getWorld().isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4)) return false;
        // 获取已注册的灶台物品实例喵
        io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem slimefunItem =
                io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem.getById("EG_COOKING_STOVE");
        // 喵~防御：注册项不是StoveBlock时拒绝，避免类型转换异常喵
        if (!(slimefunItem instanceof StoveBlock stoveBlock)) return false;
        // 喵~防御：映射状态或令牌不匹配时说明灶台已销毁、重置或开始了新请求喵
        return stoveBlock.activeStoves.get(location) == state && requestId.equals(state.activeAiRequestId);
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
