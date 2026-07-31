package io.github.thebusybiscuit.exoticgarden.cooking;

import io.github.thebusybiscuit.exoticgarden.VersionedPotionEffectType;
import io.github.thebusybiscuit.exoticgarden.cooking.config.IngredientConfig;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public class DishConsumptionListener implements Listener {

    // 正面药水效果列表，过期菜肴随机移除其中1个喵
    // 注意：JUMP_BOOST旧版名为JUMP，RESISTANCE旧版名为DAMAGE_RESISTANCE，通过VersionedPotionEffectType兼容喵
    private static final List<PotionEffectType> POSITIVE_EFFECTS = buildPositiveEffectList();

    // debuff类型枚举，便于加权随机选取喵
    private enum DebuffType {
        HUNGER,   // 饥饿1级，权重40%
        NAUSEA,   // 反胃2级，权重30%
        POISON    // 中毒1级，权重30%（含两段时长）
    }

    private final Map<String, IngredientConfig.IngredientData> ingredients;

    public DishConsumptionListener(Map<String, IngredientConfig.IngredientData> ingredients) {
        this.ingredients = ingredients;
    }

    /**
     * 监听玩家消耗物品事件，统一处理自定义菜肴和已标记烹饪食材喵~
     * 逻辑：先刷新时间戳，判断过期 → 过期则减少饱食度恢复+给debuff，不过期走原始行为喵
     */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onConsume(PlayerItemConsumeEvent e) {
        ItemStack item = e.getItem();
        ItemMeta meta = item.getItemMeta();
        // 喵~防御：meta为null时无法读取PDC，直接跳过喵
        if (meta == null) return;

        PersistentDataContainer pdc = meta.getPersistentDataContainer();

        // ===== 分支1：自定义菜肴（有DISH_HUNGER标记）=====
        if (pdc.has(CookingKeys.DISH_HUNGER, PersistentDataType.INTEGER)) {
            handleDishConsume(e, item, meta, pdc);
            return;
        }

        // ===== 分支2：调料（有SEASONING_ID标记）=====
        if (pdc.has(CookingKeys.SEASONING_ID, PersistentDataType.STRING)) {
            handleSeasoningConsume(e, item, meta, pdc);
            return;
        }

        // ===== 分支3：有保质期标记的食物（有FOOD_TIMESTAMP标记，包含原版食物/药水/烹饪食材）=====
        if (pdc.has(CookingKeys.FOOD_TIMESTAMP, PersistentDataType.LONG)) {
            handleIngredientConsume(e, item, meta, pdc);
        }
        // 其他物品不处理喵
    }

    /**
     * 处理自定义菜肴消费：检查过期 → 过期减少恢复量+给debuff，不过期正常恢复喵~
     * 复杂逻辑说明：
     *   - 菜肴取消原版事件，手动处理饱食度恢复和物品扣减
     *   - 过期时：hunger和saturation乘以0.4，应用随机debuff，随机移除1个正面buff
     *   - 不过期时：正常恢复，正常应用buff
     *   - 通过 setItem 阻止原版材质效果（金苹果等）触发喵
     */
    private void handleDishConsume(PlayerItemConsumeEvent e, ItemStack item, ItemMeta meta, PersistentDataContainer pdc) {
        // 喵~防御：取消原版消耗事件，手动处理喵
        e.setCancelled(true);
        // 喵~关键：将消费物品替换为空气，阻止 Bukkit 应用原版材质效果（金苹果、附魔金苹果等）喵
        e.setItem(new ItemStack(Material.AIR));
        Player player = e.getPlayer();

        // 读取剩余份量，默认1份喵
        Integer servingsLeft = pdc.get(CookingKeys.DISH_SERVINGS_REMAINING, PersistentDataType.INTEGER);
        int remaining = servingsLeft != null ? servingsLeft : 1;

        // 读取菜肴饱食度配置，clamp到[0,20]喵
        Integer hungerRaw = pdc.get(CookingKeys.DISH_HUNGER, PersistentDataType.INTEGER);
        if (hungerRaw == null) return;
        int hunger = Math.max(0, Math.min(hungerRaw, 20));

        // 读取饱和度配置，clamp到[0,20]喵
        Double saturationRaw = pdc.getOrDefault(CookingKeys.DISH_SATURATION, PersistentDataType.DOUBLE, 0.0);
        double saturation = Math.max(0, Math.min(saturationRaw, 20.0));

        // ===== 保质期进度计算 =====
        // 获取统一服务，确保消费处罚与物品 lore 使用完全相同的配置来源喵
        FoodExpiryService foodExpiryService = CookingModule.getFoodExpiryService();
        // 读取当前时间一次，确保本次消费内所有判断使用相同边界喵
        long nowMillis = System.currentTimeMillis();
        // 使用统一服务解析菜肴 PDC 的真实生产时间与保质期喵
        java.util.Optional<FoodExpiryService.ExpiryInfo> expiryInfo = foodExpiryService != null
                ? foodExpiryService.getExpiryInfo(item)
                : java.util.Optional.empty();
        // 缺失期限信息时保持既有保守行为，视为新鲜避免损坏物品被错误处罚喵
        double shelfProgress = expiryInfo.map(info -> info.shelfLifeMinutes() == 0
                ? (info.isExpiredAt(nowMillis) ? 1.0D : 0.0D)
                : info.calculateProgressAt(nowMillis)).orElse(0.0D);

        // 饱食度倍率：0~75%不变 → 75~100%线性1.0降0.6 → 100~300%线性0.6降0喵
        double hungerMult = calcHungerMult(shelfProgress);
        // 饱和度倍率：0~100%线性1.0降0.6 → 100~150%线性0.6降0喵
        double satMult = calcSatMult(shelfProgress);
        // debuff触发率：progress<1.0→0，1.0~3.0线性0.0到3.0；超过1.0代表多次触发喵
        double debuffRate = calcDebuffRate(shelfProgress);

        // 按倍率缩减恢复量，最低0喵
        hunger = (int) Math.max(0, Math.round(hunger * hungerMult));
        saturation = Math.max(0, saturation * satMult);

        if (shelfProgress >= 1.0) {
            // 已过期：发过期提示，按进度触发debuff喵
            player.sendMessage("§c这道菜已经过期了，吃起来味道怪怪的喵~");
            // debuffRate整数部分=必定触发次数，小数部分=额外概率触发喵
            // 例：rate=2.3 → 必定触发2次，再30%概率再触发1次喵
            int guaranteed = (int) debuffRate;
            double extra = debuffRate - guaranteed;
            // 必定触发部分：循环guaranteed次喵
            for (int i = 0; i < guaranteed; i++) {
                applyExpiredEffects(player, true);
            }
            // 额外概率部分喵
            if (ThreadLocalRandom.current().nextDouble() < extra) {
                applyExpiredEffects(player, true);
            }
        } else if (shelfProgress > 0.75) {
            // 接近过期（75%~100%）：口感下降但未过期，提示玩家喵
            player.sendMessage("§e这道菜快过期了，口感有些差喵~");
        }

        // 恢复饱食度（过期后为减少的值，不过期为原值）喵
        int newFood = Math.min(player.getFoodLevel() + hunger, 20);
        // 喵~防御：饱和度不能超过当前饱食度值喵
        float newSat = (float) Math.min(player.getSaturation() + saturation, newFood);
        player.setFoodLevel(newFood);
        player.setSaturation(newSat);

        // 应用药水效果：未过期(progress<1.0)时正常应用，过期后跳过正面buff喵
        if (shelfProgress < 1.0) {
            // 不过期：正常应用菜肴自带的药水效果喵
            String effectsRaw = pdc.get(CookingKeys.DISH_EFFECTS, PersistentDataType.STRING);
            if (effectsRaw != null && !effectsRaw.isEmpty()) {
                for (String effectStr : effectsRaw.split("\\|")) {
                    String[] parts = effectStr.trim().split(":");
                    String effectName = parts[0].toUpperCase().replace(" ", "_").replace("-", "_");
                    // 格式：效果名:等级(0起):持续秒数，转为tick(*20)喵
                    int amplifier    = parts.length > 1 ? parseInt(parts[1], 0)   : 0;
                    int durationSecs = parts.length > 2 ? parseInt(parts[2], 200) : 200;
                    PotionEffectType type = PotionEffectType.getByName(effectName);
                    if (type != null) {
                        player.addPotionEffect(new PotionEffect(type, durationSecs * 20, amplifier));
                    }
                }
            }
            // 喵~按顺序展示食用句子：读取当前索引，展示对应句，索引+1写回喵
            String flavorRaw = pdc.get(CookingKeys.DISH_FLAVOR_TEXTS, PersistentDataType.STRING);
            if (flavorRaw != null && !flavorRaw.isEmpty()) {
                String[] texts = flavorRaw.split("\\|");
                // 过滤空句子喵
                List<String> validTexts = new ArrayList<>();
                for (String t : texts) {
                    if (t != null && !t.isBlank()) validTexts.add(t);
                }
                if (!validTexts.isEmpty()) {
                    // 读取当前索引，默认0喵
                    int idx = pdc.getOrDefault(CookingKeys.DISH_FLAVOR_INDEX, PersistentDataType.INTEGER, 0);
                    // 喵~防御：索引越界时从0开始循环喵
                    if (idx < 0 || idx >= validTexts.size()) idx = 0;
                    player.sendMessage(validTexts.get(idx));
                    // 索引+1写回，下一次食用时展示下一句喵
                    pdc.set(CookingKeys.DISH_FLAVOR_INDEX, PersistentDataType.INTEGER, idx + 1);
                }
            }
        }

        // 扣减剩余次数喵
        remaining--;
        ItemStack mainHand = player.getInventory().getItemInMainHand();
        if (remaining <= 0) {
            // 喵~食用完毕：物品变成空碗喵
            ItemStack bowl = new ItemStack(org.bukkit.Material.BOWL, 1);
            if (mainHand.isSimilar(item)) {
                player.getInventory().setItemInMainHand(bowl);
            }
        } else {
            // 喵~更新剩余次数写回 PDC 和 lore喵
            pdc.set(CookingKeys.DISH_SERVINGS_REMAINING, PersistentDataType.INTEGER, remaining);
            // 刷新 lore 中的份量显示行喵
            if (meta.hasLore()) {
                List<String> lore = new ArrayList<>(meta.getLore());
                for (int i = 0; i < lore.size(); i++) {
                    if (lore.get(i).contains("§7份量:")) {
                        lore.set(i, lore.get(i).replaceAll("§7份量: §f\\d+", "§7份量: §f" + remaining));
                        break;
                    }
                }
                meta.setLore(lore);
            }
            item.setItemMeta(meta);
            // 喵~同步更新背包里的实际物品喵
            ItemStack mainHandUpdate = player.getInventory().getItemInMainHand();
            if (mainHandUpdate.isSimilar(item) || mainHandUpdate.getType() == item.getType()) {
                mainHandUpdate.setItemMeta(meta);
                player.getInventory().setItemInMainHand(mainHandUpdate);
            }
        }
    }

    /**
     * 处理已标记烹饪食材消费：检查过期 → 过期时减少饱食度恢复量+给debuff喵~
     * 复杂逻辑说明：
     *   - 不过期：让原版饱食度恢复正常进行，不干预
     *   - 过期：取消原版事件，手动扣物品，手动施加减少60%后的饱食度恢复，应用debuff
     */
    /**
     * 处理调料消费：检查保质期 → 过期时饱食度减少+30%概率debuff喵~
     * 调料大多无保质期(shelfLifeMinutes=0)，有保质期的才检查喵
     */
    private void handleSeasoningConsume(PlayerItemConsumeEvent e, ItemStack item, ItemMeta meta, PersistentDataContainer pdc) {
        String seaId = pdc.get(CookingKeys.SEASONING_ID, PersistentDataType.STRING);
        if (seaId == null) return;

        // 从 CookingModule 获取调料配置查询保质期喵
        java.util.Map<String, io.github.thebusybiscuit.exoticgarden.cooking.config.SeasoningConfig.SeasoningData> seaMap
            = io.github.thebusybiscuit.exoticgarden.cooking.CookingModule.getSeasonings();
        io.github.thebusybiscuit.exoticgarden.cooking.config.SeasoningConfig.SeasoningData sd
            = seaMap != null ? seaMap.get(seaId) : null;
        int shelfLifeMinutes = sd != null ? sd.shelfLifeMinutes : 0;
        if (shelfLifeMinutes <= 0) return; // 无保质期不干预喵

        Long timestamp = pdc.get(CookingKeys.FOOD_TIMESTAMP, PersistentDataType.LONG);
        if (timestamp == null) return;

        long diffMin = (System.currentTimeMillis() - timestamp) / 60000L;
        if (diffMin < shelfLifeMinutes) return; // 未过期不干预喵

        // 过期：取消原版事件，手动扣物品，饱食度减少60%，30%概率debuff喵
        e.setCancelled(true);
        Player player = e.getPlayer();

        // 喵~手动扣物品喵
        ItemStack mainHand = player.getInventory().getItemInMainHand();
        if (mainHand.isSimilar(item) && mainHand.getAmount() > 0) {
            int amt = mainHand.getAmount() - 1;
            mainHand.setAmount(amt);
            player.getInventory().setItemInMainHand(amt == 0 ? new ItemStack(org.bukkit.Material.AIR) : mainHand);
        } else {
            ItemStack offHand = player.getInventory().getItemInOffHand();
            if (offHand.isSimilar(item) && offHand.getAmount() > 0) {
                int amt = offHand.getAmount() - 1;
                offHand.setAmount(amt);
                player.getInventory().setItemInOffHand(amt == 0 ? new ItemStack(org.bukkit.Material.AIR) : offHand);
            }
        }

        // 喵~饱食度恢复减少60%，最低0喵
        int reduced = (int) Math.max(0, Math.round(1 * 0.4));
        player.setFoodLevel(Math.min(player.getFoodLevel() + reduced, 20));
        player.sendMessage("§c这调料已经过期了，入口有股怪味喵~");

        // 喵~30%概率触发debuff喵
        if (ThreadLocalRandom.current().nextInt(100) < 30) {
            applyExpiredEffects(player, false);
        }
    }

    /**
     * 将 lore 保质期文本反向解析为分钟数喵~
     * 支持格式：Xd Yh Zm → 天*1440+小时*60+分钟
     */
    private static int parseShelfLifeFromLore(String text) {
        // 喵~防御：null 或空直接返回 0 喵
        if (text == null || text.isBlank()) return 0;
        int minutes = 0;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d+)([天小时分钟]+)").matcher(text);
        while (m.find()) {
            int val = Integer.parseInt(m.group(1));
            String unit = m.group(2);
            if (unit.contains("天"))  minutes += val * 1440;
            else if (unit.contains("小时")) minutes += val * 60;
            else if (unit.contains("分钟")) minutes += val;
        }
        return minutes;
    }

    private void handleIngredientConsume(PlayerItemConsumeEvent e, ItemStack item, ItemMeta meta, PersistentDataContainer pdc) {
        // 读取当前时间一次，确保本次消费使用一致的过期边界喵
        long nowMillis = System.currentTimeMillis();
        // 获取统一的 PDC 与配置过期判定服务喵
        FoodExpiryService foodExpiryService = CookingModule.getFoodExpiryService();
        // 读取当前食材标识以获取其营养配置，缺失配置时仍回退原版营养表喵
        String ingredientId = pdc.get(CookingKeys.INGREDIENT_ID, PersistentDataType.STRING);
        IngredientConfig.IngredientData ingredientData = ingredientId != null ? ingredients.get(ingredientId) : null;
        // 喵~防御：服务尚未初始化时保守放行，避免启用阶段错误处罚玩家喵~
        boolean expired = foodExpiryService != null && foodExpiryService.isExpired(item, nowMillis);

        if (expired) {
            // 喵~30%概率触发过期处罚，70%概率安全通过喵
            if (ThreadLocalRandom.current().nextInt(100) < 30) {
                // ===== 过期处理：取消原版恢复，手动施加减少60%的饱食度 =====
                // 取消原版消耗事件，防止原版自动恢复饱食度喵
                e.setCancelled(true);

                Player player = e.getPlayer();

                // 取消后物品不会被消耗，检查主手或副手扣除1个喵
                ItemStack mainHand = player.getInventory().getItemInMainHand();
                ItemStack offHand = player.getInventory().getItemInOffHand();
                if (mainHand.isSimilar(item) && mainHand.getAmount() > 0) {
                    int newAmount = mainHand.getAmount() - 1;
                    if (newAmount == 0) {
                        player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
                    } else {
                        mainHand.setAmount(newAmount);
                        player.getInventory().setItemInMainHand(mainHand);
                    }
                } else if (offHand.isSimilar(item) && offHand.getAmount() > 0) {
                    // 喵~副手吃东西时扣副手喵
                    int newAmount = offHand.getAmount() - 1;
                    if (newAmount == 0) {
                        player.getInventory().setItemInOffHand(new ItemStack(Material.AIR));
                    } else {
                        offHand.setAmount(newAmount);
                        player.getInventory().setItemInOffHand(offHand);
                    }
                }

                // 计算原始饱食度和饱和度（从配置读取，无配置时查原版Material，再无则默认2）喵
                double baseFoodPoints;
                double baseSaturation;
                if (ingredientData != null) {
                    // 从食材配置读取喵
                    baseFoodPoints = ingredientData.foodPoints;
                    baseSaturation = ingredientData.saturation;
                } else {
                    // 喵~防御：无配置时查原版Material饱食度，再无则默认2喵
                    baseFoodPoints = getVanillaFoodPoints(item.getType());
                    baseSaturation = getVanillaSaturation(item.getType());
                }

                // 实际恢复 = 原值 * 0.4（减少60%），最低0喵
                int reducedHunger = (int) Math.max(0, Math.round(baseFoodPoints * 0.4));
                float reducedSaturation = (float) Math.max(0, baseSaturation * 0.4);

                // 手动施加减少后的饱食度恢复喵
                int newFood = Math.min(player.getFoodLevel() + reducedHunger, 20);
                // 喵~防御：饱和度不能超过当前饱食度值喵
                float newSat = Math.min(player.getSaturation() + reducedSaturation, newFood);
                player.setFoodLevel(newFood);
                player.setSaturation(newSat);

                // 给玩家发送过期提示喵
                player.sendMessage("§c这食材已经过期了，味道不太对喵~");

                // 应用过期debuff（普通食材模式，isMeal=false：不移除正面buff）喵
                applyExpiredEffects(player, false);
            }
        }
        // 不过期时什么都不做，让原版饱食度恢复正常进行喵
    }

    /**
     * 应用过期食物的随机debuff效果喵~
     *
     * 整体思路：
     *   - 用加权随机从3种debuff类型中选2个不重复的类型
     *   - 权重分布：HUNGER=40, NAUSEA=30, POISON=30（总和100）
     *   - 中毒（POISON）选中后，再随机决定时长：50%概率120~160s，50%概率30~60s
     *   - isMeal=true时，额外随机移除玩家当前1个正面药水效果
     *
     * 输入：player=目标玩家，isMeal=是否为菜肴（true=菜肴，false=普通食材）
     * 边界条件：玩家没有正面buff时，移除操作无效果（不报错）
     *
     * @param player  受到过期效果的玩家喵
     * @param isMeal  是否为菜肴（true时额外随机移除1个正面buff）喵
     */
    /**
     * 静态版本的过期效果应用，供 FoodListener 等外部类调用喵~
     */
    public static void applyExpiredEffectsStatic(Player player, boolean isMeal) {
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        DebuffType first = selectDebuffStatic(rng.nextInt(100));
        DebuffType second;
        int tryCount = 0;
        do {
            second = selectDebuffStatic(rng.nextInt(100));
            tryCount++;
        } while (second == first && tryCount < 10);
        if (second == first) {
            for (DebuffType t : DebuffType.values()) {
                if (t != first) { second = t; break; }
            }
        }
        applyDebuffStatic(player, first, rng);
        applyDebuffStatic(player, second, rng);
        if (isMeal) {
            java.util.List<PotionEffectType> activePositive = new java.util.ArrayList<>();
            for (PotionEffectType pt : POSITIVE_EFFECTS) {
                if (player.hasPotionEffect(pt)) activePositive.add(pt);
            }
            if (!activePositive.isEmpty()) {
                player.removePotionEffect(activePositive.get(rng.nextInt(activePositive.size())));
            }
        }
    }

    private static DebuffType selectDebuffStatic(int roll) {
        if (roll < 40) return DebuffType.HUNGER;
        else if (roll < 70) return DebuffType.NAUSEA;
        else return DebuffType.POISON;
    }

    private static void applyDebuffStatic(Player player, DebuffType type, ThreadLocalRandom rng) {
        switch (type) {
            case HUNGER -> player.addPotionEffect(new PotionEffect(PotionEffectType.HUNGER, (rng.nextInt(121) + 120) * 20, 0));
            case NAUSEA -> player.addPotionEffect(new PotionEffect(VersionedPotionEffectType.CONFUSION, (rng.nextInt(41) + 80) * 20, 1));
            case POISON -> {
                int t = rng.nextBoolean() ? (rng.nextInt(41) + 120) * 20 : (rng.nextInt(31) + 30) * 20;
                player.addPotionEffect(new PotionEffect(PotionEffectType.POISON, t, 0));
            }
        }
    }

    private void applyExpiredEffects(Player player, boolean isMeal) {
        ThreadLocalRandom rng = ThreadLocalRandom.current();

        // 加权随机选取2个不重复的debuff类型喵
        // 权重区间：HUNGER=[0,40)，NAUSEA=[40,70)，POISON=[70,100)
        DebuffType first = selectDebuff(rng.nextInt(100));
        DebuffType second;
        // 喵~防御：避免重复选同一类型，最多尝试10次喵
        int tryCount = 0;
        do {
            second = selectDebuff(rng.nextInt(100));
            tryCount++;
        } while (second == first && tryCount < 10);

        // 如果10次后还是相同（极端情况），强制选不同类型喵
        if (second == first) {
            DebuffType[] allTypes = DebuffType.values();
            for (DebuffType t : allTypes) {
                if (t != first) {
                    second = t;
                    break;
                }
            }
        }

        // 应用两个debuff喵
        applyDebuff(player, first, rng);
        applyDebuff(player, second, rng);

        // 菜肴过期：额外随机移除1个正面buff喵
        if (isMeal) {
            // 筛选玩家当前拥有的正面效果喵
            List<PotionEffectType> activePositive = new ArrayList<>();
            for (PotionEffectType positiveType : POSITIVE_EFFECTS) {
                // 喵~防御：检查玩家是否有该效果喵
                if (player.hasPotionEffect(positiveType)) {
                    activePositive.add(positiveType);
                }
            }
            // 喵~防御：有正面效果才随机移除，没有则跳过喵
            if (!activePositive.isEmpty()) {
                // 随机选1个正面效果移除喵
                PotionEffectType toRemove = activePositive.get(rng.nextInt(activePositive.size()));
                player.removePotionEffect(toRemove);
            }
        }
    }

    /**
     * 根据加权随机值选取debuff类型喵~
     * 权重区间：HUNGER=[0,40)，NAUSEA=[40,70)，POISON=[70,100)
     *
     * @param roll 0~99的随机整数喵
     * @return 对应的debuff类型喵
     */
    private DebuffType selectDebuff(int roll) {
        if (roll < 40) {
            // 饥饿，权重40%喵
            return DebuffType.HUNGER;
        } else if (roll < 70) {
            // 反胃，权重30%喵
            return DebuffType.NAUSEA;
        } else {
            // 中毒，权重30%喵
            return DebuffType.POISON;
        }
    }

    /**
     * 对玩家应用指定类型的debuff，时长随机喵~
     *
     * @param player 目标玩家喵
     * @param type   debuff类型喵
     * @param rng    随机数生成器喵
     */
    private void applyDebuff(Player player, DebuffType type, ThreadLocalRandom rng) {
        switch (type) {
            case HUNGER: {
                // 饥饿1级（amplifier=0），持续120~240秒（2400~4800 tick）喵
                int durationTicks = (rng.nextInt(121) + 120) * 20; // 120到240秒，转为tick喵
                player.addPotionEffect(new PotionEffect(PotionEffectType.HUNGER, durationTicks, 0));
                break;
            }
            case NAUSEA: {
                // 反胃2级（amplifier=1），持续80~120秒（1600~2400 tick）喵
                int durationTicks = (rng.nextInt(41) + 80) * 20; // 80到120秒，转为tick喵
                player.addPotionEffect(new PotionEffect(VersionedPotionEffectType.CONFUSION, durationTicks, 1));
                break;
            }
            case POISON: {
                // 中毒1级（amplifier=0），随机两段时长喵
                // 50%概率：120~160秒（2400~3200 tick）；50%概率：30~60秒（600~1200 tick）
                int durationTicks;
                if (rng.nextBoolean()) {
                    // 长时间中毒：120~160秒喵
                    durationTicks = (rng.nextInt(41) + 120) * 20;
                } else {
                    // 短时间中毒：30~60秒喵
                    durationTicks = (rng.nextInt(31) + 30) * 20;
                }
                player.addPotionEffect(new PotionEffect(PotionEffectType.POISON, durationTicks, 0));
                break;
            }
        }
    }

    /**
     * 查询原版Material的饱食度（hunger points）喵~
     * 只覆盖常见可食用原版物品，未收录的返回默认值2喵
     *
     * @param material 物品材质喵
     * @return 饱食度点数（原版food_level恢复量）喵
     */
    private double getVanillaFoodPoints(Material material) {
        // 喵~防御：null材质返回默认值喵
        if (material == null) return 2.0;
        switch (material) {
            // 肉类喵
            case COOKED_BEEF:         return 8;
            case COOKED_PORKCHOP:     return 8;
            case COOKED_CHICKEN:      return 6;
            case COOKED_MUTTON:       return 6;
            case COOKED_RABBIT:       return 5;
            case COOKED_COD:          return 5;
            case COOKED_SALMON:       return 6;
            case BEEF:                return 3;
            case PORKCHOP:            return 3;
            case CHICKEN:             return 2;
            case MUTTON:              return 2;
            case RABBIT:              return 3;
            case COD:                 return 2;
            case SALMON:              return 2;
            case TROPICAL_FISH:       return 1;
            case PUFFERFISH:          return 1;
            case ROTTEN_FLESH:        return 4;
            // 面包类喵
            case BREAD:               return 5;
            case COOKIE:              return 2;
            case CAKE:                return 2;
            case PUMPKIN_PIE:         return 8;
            // 果蔬类喵
            case APPLE:               return 4;
            case GOLDEN_APPLE:        return 4;
            case ENCHANTED_GOLDEN_APPLE: return 4;
            case CARROT:              return 3;
            case GOLDEN_CARROT:       return 6;
            case POTATO:              return 1;
            case BAKED_POTATO:        return 5;
            case POISONOUS_POTATO:    return 2;
            case BEETROOT:            return 1;
            case SWEET_BERRIES:       return 2;
            case GLOW_BERRIES:        return 2;
            case MELON_SLICE:         return 2;
            case CHORUS_FRUIT:        return 4;
            case MUSHROOM_STEW:       return 6;
            case BEETROOT_SOUP:       return 6;
            case RABBIT_STEW:         return 10;
            case SUSPICIOUS_STEW:     return 6;
            case HONEY_BOTTLE:        return 6;
            case DRIED_KELP:          return 1;
            case KELP:                return 1;
            // 喵~防御：未收录物品返回默认值2喵
            default:                  return 2.0;
        }
    }

    /**
     * 查询原版Material的饱和度（saturation modifier）喵~
     * 只覆盖常见可食用原版物品，未收录的返回默认值2喵
     *
     * @param material 物品材质喵
     * @return 饱和度修正值喵
     */
    private double getVanillaSaturation(Material material) {
        // 喵~防御：null材质返回默认值喵
        if (material == null) return 2.0;
        switch (material) {
            // 肉类喵
            case COOKED_BEEF:         return 12.8;
            case COOKED_PORKCHOP:     return 12.8;
            case COOKED_CHICKEN:      return 7.2;
            case COOKED_MUTTON:       return 9.6;
            case COOKED_RABBIT:       return 6.0;
            case COOKED_COD:          return 6.0;
            case COOKED_SALMON:       return 9.6;
            case BEEF:                return 1.8;
            case PORKCHOP:            return 1.8;
            case CHICKEN:             return 1.2;
            case MUTTON:              return 1.2;
            case RABBIT:              return 1.8;
            case COD:                 return 0.4;
            case SALMON:              return 0.4;
            case TROPICAL_FISH:       return 0.2;
            case PUFFERFISH:          return 0.2;
            case ROTTEN_FLESH:        return 0.8;
            // 面包类喵
            case BREAD:               return 6.0;
            case COOKIE:              return 0.4;
            case CAKE:                return 0.4;
            case PUMPKIN_PIE:         return 4.8;
            // 果蔬类喵
            case APPLE:               return 2.4;
            case GOLDEN_APPLE:        return 9.6;
            case ENCHANTED_GOLDEN_APPLE: return 9.6;
            case CARROT:              return 3.6;
            case GOLDEN_CARROT:       return 14.4;
            case POTATO:              return 0.6;
            case BAKED_POTATO:        return 6.0;
            case POISONOUS_POTATO:    return 1.2;
            case BEETROOT:            return 1.2;
            case SWEET_BERRIES:       return 0.4;
            case GLOW_BERRIES:        return 0.4;
            case MELON_SLICE:         return 1.2;
            case CHORUS_FRUIT:        return 2.4;
            case MUSHROOM_STEW:       return 7.2;
            case BEETROOT_SOUP:       return 7.2;
            case RABBIT_STEW:         return 12.0;
            case SUSPICIOUS_STEW:     return 7.2;
            case HONEY_BOTTLE:        return 1.2;
            case DRIED_KELP:          return 0.6;
            case KELP:                return 0.2;
            // 喵~防御：未收录物品返回默认值2喵
            default:                  return 2.0;
        }
    }

    /**
     * 构建正面效果列表，使用 VersionedPotionEffectType.get 进行跨版本兼容查找喵~
     * 跳跃和抗性在旧版 Bukkit API 名字不同，需要兼容处理喵
     */
    private static List<PotionEffectType> buildPositiveEffectList() {
        List<PotionEffectType> list = new ArrayList<>();
        // 速度喵
        list.add(PotionEffectType.SPEED);
        // 跳跃加速：新版叫 JUMP_BOOST，旧版叫 JUMP 喵
        PotionEffectType jumpBoost = VersionedPotionEffectType.get("JUMP_BOOST", "JUMP");
        if (jumpBoost != null) list.add(jumpBoost);
        // 生命恢复喵
        list.add(PotionEffectType.REGENERATION);
        // 伤害吸收喵
        list.add(PotionEffectType.ABSORPTION);
        // 饱和喵
        list.add(PotionEffectType.SATURATION);
        // 抗性：新版叫 RESISTANCE，旧版叫 DAMAGE_RESISTANCE 喵
        list.add(VersionedPotionEffectType.DAMAGE_RESISTANCE);
        // 防火喵
        list.add(PotionEffectType.FIRE_RESISTANCE);
        // 水下呼吸喵
        list.add(PotionEffectType.WATER_BREATHING);
        // 夜视喵
        list.add(PotionEffectType.NIGHT_VISION);
        // 缓降：旧版可能不存在，兼容处理喵
        PotionEffectType slowFalling = VersionedPotionEffectType.get("SLOW_FALLING");
        if (slowFalling != null) list.add(slowFalling);
        // 喵~防御：过滤掉null值（旧版不存在某些效果时）喵
        list.removeIf(t -> t == null);
        return list;
    }

    /**
     * 构建品质/饱食/饱和/份量 lore 行，根据保质期进度显示当前值和减少量喵~
     *
     * 格式：§7品质: X  §7饱食: §fA§e(-B)  §7饱和: §fC.C§e(-D.D)  §7份量: §fN
     * 减少量颜色：未过期黄色§e，已过期红色§c；减少量<0.05时不显示减少部分喵
     *
     * @param quality      品质字符串喵
     * @param baseHunger   AI返回的原始饱食度喵
     * @param baseSat      AI返回的原始饱和度喵
     * @param servings     当前剩余份量喵
     * @param shelfProgress 保质期进度（0=新鲜，1=刚过期，3=过期3倍）喵
     * @return 格式化后的 lore 行喵
     */
    public static String buildNutritionLine(String quality, int baseHunger, double baseSat,
                                            int servings, double shelfProgress) {
        // 喵~防御：进度为负时视为新鲜喵
        double safeProgress = Math.max(0.0, shelfProgress);
        double hungerMult = calcHungerMult(safeProgress);
        double satMult    = calcSatMult(safeProgress);

        // 当前实际值 = 基础值 * 倍率喵
        double currentHunger = baseHunger * hungerMult;
        double currentSat    = baseSat    * satMult;
        // 减少量 = 基础值 - 当前值，四舍五入到1位小数避免浮点误差显示喵
        double reducedHunger = Math.round((baseHunger - currentHunger) * 10.0) / 10.0;
        double reducedSat    = Math.round((baseSat    - currentSat)    * 10.0) / 10.0;

        // 过期时减少量用红色，接近/未过期用黄色喵
        String reductionColor = safeProgress >= 1.0 ? "§c" : "§e";

        // 饱食度显示：当前值保留1位小数喵
        String hungerStr = "§f" + String.format("%.1f", currentHunger);
        if (reducedHunger >= 0.05) {
            // 喵~括号本身用§7灰色，括号内减少量用reductionColor，右括号恢复§7喵
            hungerStr += "§7(" + reductionColor + "-" + String.format("%.1f", reducedHunger) + "§7)";
        }

        // 饱和度显示：同样1位小数喵
        String satStr = "§f" + String.format("%.1f", currentSat);
        if (reducedSat >= 0.05) {
            satStr += "§7(" + reductionColor + "-" + String.format("%.1f", reducedSat) + "§7)";
        }

        return "§7品质: " + quality
             + "  §7饱食: " + hungerStr
             + "  §7饱和: " + satStr
             + "  §7份量: §f" + servings;
    }

    /**
     * 饱食度倍率计算喵~
     * 0~75%  → 1.0（不变）
     * 75~100% → 线性从1.0降到0.6
     * 100~300% → 线性从0.6降到0.0
     *
     * @param p 保质期进度（0.0=新鲜，1.0=刚过期，3.0=过期3倍时长）喵
     * @return 饱食度倍率 [0.0, 1.0] 喵
     */
    public static double calcHungerMult(double p) {
        // 喵~防御：进度为负时（时钟回拨等异常）视为新鲜喵
        if (p <= 0.75) return 1.0;
        if (p <= 1.0) {
            // 75%~100%：1.0 线性降到 0.6，斜率 = (0.6-1.0)/(1.0-0.75) = -1.6喵
            return 1.0 + (p - 0.75) * (0.6 - 1.0) / (1.0 - 0.75);
        }
        if (p <= 3.0) {
            // 100%~300%：0.6 线性降到 0.0，斜率 = (0.0-0.6)/(3.0-1.0) = -0.3喵
            double val = 0.6 + (p - 1.0) * (0.0 - 0.6) / (3.0 - 1.0);
            return Math.max(0.0, val);
        }
        // 超过300%：恢复量归零喵
        return 0.0;
    }

    /**
     * 饱和度倍率计算喵~
     * 0~100%  → 线性从1.0降到0.6
     * 100~150% → 线性从0.6降到0.0
     *
     * @param p 保质期进度喵
     * @return 饱和度倍率 [0.0, 1.0] 喵
     */
    public static double calcSatMult(double p) {
        // 喵~防御：进度为负时视为新鲜喵
        if (p <= 0.0) return 1.0;
        if (p <= 1.0) {
            // 0~100%：1.0 线性降到 0.6喵
            return 1.0 + p * (0.6 - 1.0);
        }
        if (p <= 1.5) {
            // 100%~150%：0.6 线性降到 0.0，斜率 = (0.0-0.6)/(1.5-1.0) = -1.2喵
            double val = 0.6 + (p - 1.0) * (0.0 - 0.6) / (1.5 - 1.0);
            return Math.max(0.0, val);
        }
        // 超过150%：饱和度归零喵
        return 0.0;
    }

    /**
     * debuff触发率计算喵~
     * 0~100%：触发率0
     * 100~300%：线性从0.0升到3.0（对应0%到300%）
     * 超过300%：固定3.0
     *
     * 整数部分 = 必触发次数，小数部分 = 额外一次的概率喵
     * 例：rate=2.3 → 必定触发2次，再30%概率触发第3次喵
     *
     * @param p 保质期进度喵
     * @return debuff触发率 [0.0, 3.0] 喵
     */
    public static double calcDebuffRate(double p) {
        // 喵~防御：未过期时触发率为0喵
        if (p <= 1.0) return 0.0;
        if (p <= 3.0) {
            // 100%~300%：线性从0升到3.0喵
            return (p - 1.0) / (3.0 - 1.0) * 3.0;
        }
        // 超过300%：最高触发率3.0喵
        return 3.0;
    }

    /**
     * 在lore列表中查找以prefix开头的行，找到则替换为newLine，找不到则追加喵~
     */
    static void replaceLoreLine(List<String> lore, String prefix, String newLine) {
        for (int i = 0; i < lore.size(); i++) {
            if (lore.get(i).startsWith(prefix)) {
                lore.set(i, newLine);
                return;
            }
        }
        lore.add(newLine);
    }

    // 安全解析整数，失败时返回默认值喵
    private int parseInt(String s, int def) {
        try { return Integer.parseInt(s.trim()); } catch (NumberFormatException ex) { return def; }
    }
}
