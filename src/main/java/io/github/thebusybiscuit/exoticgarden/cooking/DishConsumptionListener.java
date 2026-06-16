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

        // ===== 分支2：有保质期标记的食物（有FOOD_TIMESTAMP标记，包含原版食物/药水/烹饪食材）=====
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
     */
    private void handleDishConsume(PlayerItemConsumeEvent e, ItemStack item, ItemMeta meta, PersistentDataContainer pdc) {
        // 喵~防御：取消原版消耗事件，手动处理喵
        e.setCancelled(true);
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

        // ===== 过期检测：读取 DISH_SHELF_LIFE + FOOD_TIMESTAMP 判断 =====
        boolean expired = false;
        Long timestamp = pdc.get(CookingKeys.FOOD_TIMESTAMP, PersistentDataType.LONG);
        Integer shelfLifeMinutes = pdc.get(CookingKeys.DISH_SHELF_LIFE, PersistentDataType.INTEGER);

        // 喵~防御：无 DISH_SHELF_LIFE 字段视为不过期；无时间戳也视为不过期喵
        if (timestamp != null && shelfLifeMinutes != null && shelfLifeMinutes > 0) {
            long nowMs = System.currentTimeMillis();
            long diffMinutes = (nowMs - timestamp) / 60000L;
            expired = diffMinutes >= shelfLifeMinutes;
            // 喵~调试：输出过期判断信息，确认字段存在和时间差喵
            e.getPlayer().sendMessage("§8[debug] 时间戳=" + timestamp + " 保质期=" + shelfLifeMinutes
                + "min 已过=" + diffMinutes + "min 过期=" + expired);
        } else {
            // 喵~调试：输出字段缺失信息喵
            e.getPlayer().sendMessage("§8[debug] FOOD_TIMESTAMP=" + timestamp + " DISH_SHELF_LIFE=" + shelfLifeMinutes);
        }

        if (expired) {
            // 过期：饱食度和饱和度恢复量乘以0.4（减少60%），最低0喵
            hunger = (int) Math.max(0, Math.round(hunger * 0.4));
            saturation = Math.max(0, saturation * 0.4);
            // 给玩家发送过期提示喵
            player.sendMessage("§c这道菜已经过期了，吃起来味道怪怪的喵~");
            // 应用过期debuff（菜肴模式，isMeal=true：额外随机移除1个正面buff）喵
            applyExpiredEffects(player, true);
        }

        // 恢复饱食度（过期后为减少的值，不过期为原值）喵
        int newFood = Math.min(player.getFoodLevel() + hunger, 20);
        // 喵~防御：饱和度不能超过当前饱食度值喵
        float newSat = (float) Math.min(player.getSaturation() + saturation, newFood);
        player.setFoodLevel(newFood);
        player.setSaturation(newSat);

        // 应用药水效果（只有不过期时正常应用，过期时跳过正面buff，因为已经被随机移除了）喵
        if (!expired) {
            // 不过期：正常应用菜肴自带的药水效果喵
            String effectsRaw = pdc.get(CookingKeys.DISH_EFFECTS, PersistentDataType.STRING);
            if (effectsRaw != null && !effectsRaw.isEmpty()) {
                for (String effectStr : effectsRaw.split("\\|")) {
                    String[] parts = effectStr.trim().split(":");
                    String effectName = parts[0].toUpperCase().replace(" ", "_").replace("-", "_");
                    int duration  = parts.length > 1 ? parseInt(parts[1], 200) : 200;
                    int amplifier = parts.length > 2 ? parseInt(parts[2], 0)   : 0;
                    PotionEffectType type = PotionEffectType.getByName(effectName);
                    // 喵~防御：type为null时跳过该效果喵
                    if (type != null) {
                        player.addPotionEffect(new PotionEffect(type, duration, amplifier));
                    }
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
    private void handleIngredientConsume(PlayerItemConsumeEvent e, ItemStack item, ItemMeta meta, PersistentDataContainer pdc) {
        long nowMs = System.currentTimeMillis();

        // 读取食材ID，查询对应的保质期配置喵
        String ingId = pdc.get(CookingKeys.INGREDIENT_ID, PersistentDataType.STRING);
        IngredientConfig.IngredientData data = ingId != null ? ingredients.get(ingId) : null;
        // 喵~防御：无配置时使用默认保质期10分钟喵
        int shelfLifeMinutes = data != null ? data.shelfLifeMinutes : 10;

        // 读取原始时间戳，用于过期判断（食用前的时间）喵
        Long oldTimestamp = pdc.get(CookingKeys.FOOD_TIMESTAMP, PersistentDataType.LONG);

        // 判断是否过期：用旧时间戳判断喵
        boolean expired = false;
        if (oldTimestamp != null) {
            long diffMinutes = (nowMs - oldTimestamp) / 60000L;
            // 经过时间超过保质期则过期喵
            expired = diffMinutes >= shelfLifeMinutes;
        }

        if (expired) {
            // ===== 过期处理：取消原版恢复，手动施加减少60%的饱食度 =====
            // 取消原版消耗事件，防止原版自动恢复饱食度喵
            e.setCancelled(true);

            Player player = e.getPlayer();

            // 取消后物品不会被消耗，手动扣除1个喵
            ItemStack mainHand = player.getInventory().getItemInMainHand();
            if (mainHand.isSimilar(item) && mainHand.getAmount() > 0) {
                int newAmount = mainHand.getAmount() - 1;
                // 喵~先设置数量再写回背包，确保实际扣除喵
                if (newAmount == 0) {
                    player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
                } else {
                    mainHand.setAmount(newAmount);
                    player.getInventory().setItemInMainHand(mainHand);
                }
            }

            // 计算原始饱食度和饱和度（从配置读取，无配置时查原版Material，再无则默认2）喵
            double baseFoodPoints;
            double baseSaturation;
            if (data != null) {
                // 从食材配置读取喵
                baseFoodPoints = data.foodPoints;
                baseSaturation = data.saturation;
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
