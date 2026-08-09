package io.github.thebusybiscuit.exoticgarden.cooking;

import io.github.thebusybiscuit.exoticgarden.cooking.config.FoodsConfig;
import io.github.thebusybiscuit.exoticgarden.cooking.config.FuelConfig;
import io.github.thebusybiscuit.exoticgarden.cooking.config.IngredientConfig;
import io.github.thebusybiscuit.exoticgarden.cooking.util.ItemIdUtil;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.player.PlayerBackpack;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCreativeEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BundleMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class FoodTagListener implements Listener {

    private static final org.bukkit.NamespacedKey KEY_SF_ITEM =
            new org.bukkit.NamespacedKey("slimefun", "slimefun_item");
    // 记录是否已报告外部背包 API 不兼容，避免每分钟重复刷屏喵~
    private static boolean externalBackpackApiWarningLogged = false;
    // 限制收纳袋嵌套扫描深度，防御异常物品数据导致周期任务递归失控喵~
    private static final int MAX_BUNDLE_DEPTH = 4;

    /**
     * 通用食物标识符，用于原版可食用物品（不在黑名单中）的 fallback 保质期标签喵~
     * 保质期默认 10 分钟
     */
    private static final String GENERIC_FOOD_ID = "_GENERIC_FOOD_";

    /**
     * 通用药水标识符，用于药水类物品的 fallback 保质期标签喵~
     * 保质期默认 5 分钟（药水效果随时间减弱）
     */
    private static final String GENERIC_POTION_ID = "_GENERIC_POTION_";

    // 从 FoodsConfig 读取的黑名单，不再硬编码喵
    private final Set<Material> blacklist;

    // 从 FoodsConfig 读取的通用食物保质期喵
    private final int genericFoodShelfLifeMinutes;

    // 从 FoodsConfig 读取的通用药水保质期喵
    private final int genericPotionShelfLifeMinutes;

    private final Map<String, IngredientConfig.IngredientData> ingredients;
    // 燃料配置 map，用于燃料物品 lore 标签喵
    private final Map<String, FuelConfig.FuelData> fuels;
    // 完整 foodsConfig 引用，用于 overrides 逐物品保质期查询喵
    private final io.github.thebusybiscuit.exoticgarden.cooking.config.FoodsConfig foodsConfig;
    // 调料配置 map，用于排除调料物品被误标为食物喵
    private final Map<String, io.github.thebusybiscuit.exoticgarden.cooking.config.SeasoningConfig.SeasoningData> seasonings;

    /**
     * 构造函数，接收 FoodsConfig、fuels、seasonings map 喵~
     */
    public FoodTagListener(Map<String, IngredientConfig.IngredientData> ingredients,
                           io.github.thebusybiscuit.exoticgarden.cooking.config.FoodsConfig foodsConfig,
                           Map<String, FuelConfig.FuelData> fuels,
                           Map<String, io.github.thebusybiscuit.exoticgarden.cooking.config.SeasoningConfig.SeasoningData> seasonings) {
        this.ingredients = ingredients;
        this.fuels = fuels;
        this.foodsConfig = foodsConfig;
        this.seasonings = seasonings;
        // 从 foodsConfig 读取配置，不再硬编码喵
        this.blacklist = foodsConfig.getBlacklist();
        this.genericFoodShelfLifeMinutes = foodsConfig.getGenericFoodShelfLife();
        this.genericPotionShelfLifeMinutes = foodsConfig.getGenericPotionShelfLife();
    }

    // [已禁用] 事件驱动触发（含保质期物品）已全部注释，改由定时扫描兜底喵~
    // @EventHandler(ignoreCancelled = true)
    // public void onCreativeClick(InventoryCreativeEvent e) { ... }

    // @EventHandler(ignoreCancelled = true)
    // public void onHeldItemChange(PlayerItemHeldEvent e) { ... }

    // @EventHandler(ignoreCancelled = true)
    // public void onInventoryClick(InventoryClickEvent e) { ... }

    // @EventHandler(ignoreCancelled = true)
    // public void onInventoryOpen(InventoryOpenEvent e) { ... }

    // @EventHandler(ignoreCancelled = false)
    // public void onInventoryClose(InventoryCloseEvent e) { ... }

    /**
     * 玩家拾取物品时，立即为无保质期物品（燃料）打标签喵~
     * 整体思路：燃料的 lore 标签内容不含时间戳，所有已标签燃料完全一致。
     *           若在物品进入背包前就打好标签，Minecraft 原生合叠逻辑会把它与
     *           背包里同款已标签燃料合并，省去玩家手动整理喵~
     * 仅处理燃料：含保质期物品（食材/调料/食物）需要在进入背包后才写时间戳，不在此处处理喵
     */
    @EventHandler(ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent e) {
        // 喵~防御：只处理玩家拾取，非玩家实体跳过喵
        if (!(e.getEntity() instanceof org.bukkit.entity.Player)) return;
        ItemStack item = e.getItem().getItemStack();
        // 喵~防御：空物品跳过喵
        if (item == null || item.getType().isAir()) return;
        // 克隆后尝试打燃料标签；若是燃料则更新地上实体的 ItemStack，使其带标签进入背包喵
        ItemStack copy = item.clone();
        if (tagIfFuel(copy)) {
            // 喵~把打好标签的副本写回地面实体，进背包时 Minecraft 会与同款标签堆合并喵
            e.getItem().setItemStack(copy);
        }
    }

    private void scanPlayerInventory(org.bukkit.entity.Player player) {
        // 喵~防御：玩家光标上有物品（正在拖拽），跳过本次扫描，避免setItem覆盖光标状态造成卡手喵
        if (player.getOpenInventory().getCursor() != null
                && !player.getOpenInventory().getCursor().getType().isAir()) {
            return;
        }
        for (int i = 0; i < player.getInventory().getSize(); i++) {
            updateInventorySlot(player.getInventory(), i);
        }
        ItemStack offHand = player.getInventory().getItemInOffHand();
        // 喵~防御：副手为空气时没有根物品与收纳袋内容需要刷新喵~
        if (!offHand.getType().isAir()) {
            // 刷新副手根物品及其受限深度内的收纳袋内容喵~
            ItemStack refreshedOffHand = refreshItemTree(offHand);
            // 仅副手根物品实际变化时才写回，避免无意义背包同步喵~
            if (!offHand.equals(refreshedOffHand)) player.getInventory().setItemInOffHand(refreshedOffHand);
        }
        scanHeldSlimefunBackpacks(player);
        refreshExternalPlayerBackpack(player);
    }

    /**
     * 更新一个 Bukkit Inventory 槽位内的食物 PDC 与 lore 喵~
     * 输入：inventory-目标库存，slot-待检查槽位
     * 输出：物品实际变化时写回；空物品或无变化时不写入
     */
    private void updateInventorySlot(org.bukkit.inventory.Inventory inventory, int slot) {
        // 喵~防御：库存为空或槽位越界时跳过，避免周期任务异常中断喵~
        if (inventory == null || slot < 0 || slot >= inventory.getSize()) return;
        ItemStack originalItem = inventory.getItem(slot);
        // 喵~防御：空物品无需读取 PDC 或写回喵~
        if (originalItem == null || originalItem.getType().isAir()) return;
        // 克隆根物品，隔离本轮 PDC、lore 与收纳袋内容修改喵~
        ItemStack refreshedItem = originalItem.clone();
        // 刷新根物品和受限深度内的所有收纳袋物品喵~
        refreshedItem = refreshItemTree(refreshedItem);
        // 仅在根物品实际变化时写回库存，避免周期扫描产生无意义同步喵~
        if (!originalItem.equals(refreshedItem)) inventory.setItem(slot, refreshedItem);
    }

    /**
     * 刷新根物品及其收纳袋内受限深度的食物 PDC 与 lore 喵~
     * 输入：rootItem-待刷新根物品
     * 输出：实际变化时返回独立更新副本，无变化时返回原物品引用
     * 边界：收纳袋嵌套超过固定上限时停止深入，保证周期扫描资源可控喵~
     */
    private ItemStack refreshItemTree(ItemStack rootItem) {
        // 从根层开始递归扫描收纳袋内容喵~
        return refreshItemTree(rootItem, 0);
    }

    /**
     * 递归刷新单个物品及其收纳袋内容喵~
     * 输入：sourceItem-当前层物品，bundleDepth-当前收纳袋深度
     * 输出：实际变化时返回更新副本，无变化时返回原物品
     */
    private ItemStack refreshItemTree(ItemStack sourceItem, int bundleDepth) {
        // 喵~防御：空物品或空气没有 PDC、lore 与收纳袋内容可更新喵~
        if (sourceItem == null || sourceItem.getType().isAir()) return sourceItem;
        // 克隆当前层物品，隔离更新过程对库存原对象的可变修改喵~
        ItemStack refreshedItem = sourceItem.clone();
        // 按既有规则刷新当前层食物、菜肴、调料或燃料标签喵~
        updateItem(refreshedItem);
        // 只有收纳袋且未到深度上限时才递归读取内部物品喵~
        if (refreshedItem.getType() == Material.BUNDLE && bundleDepth < MAX_BUNDLE_DEPTH) {
            // 读取收纳袋元数据以访问不可变内容快照喵~
            ItemMeta itemMeta = refreshedItem.getItemMeta();
            // 喵~防御：异常 Bundle 元数据不符合 BundleMeta 时不尝试写入喵~
            if (itemMeta instanceof BundleMeta bundleMeta) {
                // 复制内容快照为可修改列表，禁止直接修改 BundleMeta 返回列表喵~
                List<ItemStack> refreshedContents = new ArrayList<>(bundleMeta.getItems());
                // 记录是否有任意内部根物品实际发生变化喵~
                boolean contentsChanged = false;
                // 主人注意：每层最多遍历收纳袋实际内容，深度固定为4层以限制周期扫描成本喵~
                for (int contentIndex = 0; contentIndex < refreshedContents.size(); contentIndex++) {
                    // 读取当前内部物品喵~
                    ItemStack originalContent = refreshedContents.get(contentIndex);
                    // 递归刷新内部物品及更深层收纳袋喵~
                    ItemStack refreshedContent = refreshItemTree(originalContent, bundleDepth + 1);
                    // 仅内部根物品实际不同才替换该列表元素喵~
                    if (originalContent != null && !originalContent.equals(refreshedContent)) {
                        // 写入更新后的内部物品副本喵~
                        refreshedContents.set(contentIndex, refreshedContent);
                        // 标记当前收纳袋内容已发生真实变化喵~
                        contentsChanged = true;
                    }
                }
                // 至少一个内部物品变化时才整体写回收纳袋元数据喵~
                if (contentsChanged) {
                    // 写入完整且保持原有顺序的收纳袋内容列表喵~
                    bundleMeta.setItems(refreshedContents);
                    // 将更新后的元数据写回外层收纳袋物品喵~
                    refreshedItem.setItemMeta(bundleMeta);
                }
            }
        }
        // 根物品无实际差异时复用原引用，供外部背包 API 跳过数据库事务喵~
        return sourceItem.equals(refreshedItem) ? sourceItem : refreshedItem;
    }

    /**
     * 扫描玩家当前实际持有的 Slimefun 原生背包，不按玩家 UUID 枚举全部背包喵~
     * 输入：player-当前在线玩家
     * 输出：每个唯一背包最多扫描并保存一次
     */
    private void scanHeldSlimefunBackpacks(org.bukkit.entity.Player player) {
        // 喵~防御：玩家为空或已离线时不提交异步背包解析请求喵~
        if (player == null || !player.isOnline()) return;
        java.util.List<ItemStack> heldItems = new java.util.ArrayList<>();
        // 收集主背包中实际持有的候选背包物品喵~
        for (ItemStack inventoryItem : player.getInventory().getContents()) {
            if (inventoryItem != null && !inventoryItem.getType().isAir()) heldItems.add(inventoryItem.clone());
        }
        // 收集副手候选背包物品喵~
        ItemStack offHandItem = player.getInventory().getItemInOffHand();
        if (offHandItem != null && !offHandItem.getType().isAir()) heldItems.add(offHandItem.clone());
        // 每轮回调按背包 UUID 去重，避免复制出的同一背包重复保存喵~
        java.util.Set<java.util.UUID> scannedBackpackIds = new java.util.HashSet<>();
        for (ItemStack heldItem : heldItems) {
            // PlayerBackpack 自行兼容新版 PDC UUID 与旧版 lore ID，并在主线程回调喵~
            PlayerBackpack.getAsync(heldItem, backpack -> {
                // 喵~防御：玩家开始拖拽、背包失效或重复 UUID 时跳过，避免异步回调覆盖交互状态喵~
                if (!player.isOnline() || (player.getOpenInventory().getCursor() != null
                        && !player.getOpenInventory().getCursor().getType().isAir())
                        || backpack == null || backpack.isInvalid()
                        || !scannedBackpackIds.add(backpack.getUniqueId())) return;
                // 打开的背包可能正在由玩家编辑，本轮跳过以避免竞争覆盖喵~
                if (!backpack.getInventory().getViewers().isEmpty()) return;
                synchronized (backpack) {
                    // 喵~防御：锁内二次检查查看者，避免进入锁前刚打开背包喵~
                    if (!backpack.getInventory().getViewers().isEmpty()) return;
                    boolean changed = false;
                    for (int slot = 0; slot < backpack.getInventory().getSize(); slot++) {
                        ItemStack originalItem = backpack.getInventory().getItem(slot);
                        if (originalItem == null || originalItem.getType().isAir()) continue;
                        // 刷新背包格根物品与其收纳袋内容喵~
                        ItemStack refreshedItem = refreshItemTree(originalItem);
                        // 仅根物品实际变化时写回并标记需要 Slimefun 持久化喵~
                        if (!originalItem.equals(refreshedItem)) {
                            backpack.getInventory().setItem(slot, refreshedItem);
                            changed = true;
                        }
                    }
                    // 仅真实变更时委托 Slimefun 正规控制器计算差异、刷新快照并持久化喵~
                    if (changed) Slimefun.getDatabaseManager().getProfileDataController().saveBackpackInventory(backpack);
                }
            }, true);
        }
    }

    /**
     * 通过可选 PlayerBackpack 插件的公开服务刷新该玩家的独立背包喵~
     * 输入：player-当前在线玩家
     * 输出：插件缺失、停用或接口不可用时安全跳过
     */
    private void refreshExternalPlayerBackpack(org.bukkit.entity.Player player) {
        // 喵~防御：仅在目标插件启用时调用反射 API，避免硬依赖导致启动失败喵~
        org.bukkit.plugin.Plugin externalPlugin = org.bukkit.Bukkit.getPluginManager().getPlugin("PlayerBackpack");
        if (externalPlugin == null || !externalPlugin.isEnabled()) return;
        try {
            Object backpackService = externalPlugin.getClass().getMethod("getBackpackService").invoke(externalPlugin);
            // 喵~防御：服务尚未完成初始化时跳过本次周期刷新喵~
            if (backpackService == null) return;
            java.util.function.UnaryOperator<ItemStack> itemRefresher = originalItem -> {
                // 喵~防御：空物品没有收纳袋内容或食物标签可刷新喵~
                if (originalItem == null || originalItem.getType().isAir()) return originalItem;
                // 刷新根物品及其收纳袋内容；无差异时方法会返回原引用避免 SQLite 写入喵~
                return refreshItemTree(originalItem);
            };
            backpackService.getClass().getMethod("refreshExistingItems", java.util.UUID.class,
                    java.util.function.UnaryOperator.class).invoke(backpackService, player.getUniqueId(), itemRefresher);
        } catch (ReflectiveOperationException exception) {
            // 喵~防御：旧版本或不兼容 API 仅跳过本次刷新，不影响原版与Slimefun背包逻辑喵~
            if (!externalBackpackApiWarningLogged) {
                externalBackpackApiWarningLogged = true;
                org.bukkit.Bukkit.getLogger().warning("[Cooking] PlayerBackpack 未提供兼容刷新 API，已跳过外部背包食物更新喵~");
            }
        }
    }

    // 喵~统一更新接口：菜肴走过期lore更新，调料优先检查，食材走tagIfIngredient，燃料走tagIfFuel喵
    private boolean updateItem(ItemStack item) {
        if (item == null || item.getType().isAir()) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        // 喵~菜肴：只更新过期相关的lore和displayName，不改PDC喵
        if (meta.getPersistentDataContainer().has(CookingKeys.DISH_HUNGER, PersistentDataType.INTEGER)) {
            return refreshDishExpiryLore(item, meta);
        }
        // 喵~调料优先：防止旧PDC里遗留的INGREDIENT_ID=GENERIC_FOOD_ID导致调料被误标为[食物]喵
        boolean taggedAsSeasoning = tagIfSeasoning(item);
        if (taggedAsSeasoning) return true;
        // 食材喵
        boolean taggedAsIngredient = tagIfIngredient(item);
        if (taggedAsIngredient) return true;
        // 燃料喵
        return tagIfFuel(item);
    }

    /**
     * 为调料物品打 [辅料] 标签，写 SEASONING_ID 和 FOOD_TIMESTAMP，清除旧食材 PDC/lore 喵~
     * 输入：ItemStack（可为null）
     * 输出：是否是调料（true=是调料已处理，false=不是调料跳过）
     */
    private boolean tagIfSeasoning(ItemStack item) {
        // 喵~防御：null 或空气直接跳过喵
        if (item == null || item.getType().isAir()) return false;
        String itemKey = ItemIdUtil.toKey(item);
        if (itemKey == null || !seasonings.containsKey(itemKey)) return false;

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return true;
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        boolean changed = false;

        // 喵~清除旧版本可能错误写入的食材PDC喵
        if (pdc.has(CookingKeys.INGREDIENT_ID, PersistentDataType.STRING)) {
            pdc.remove(CookingKeys.INGREDIENT_ID);
            changed = true;
        }
        if (pdc.has(CookingKeys.FOOD_STATE, PersistentDataType.STRING)) {
            pdc.remove(CookingKeys.FOOD_STATE);
            changed = true;
        }

        // 喵~写入调料 ID，供过期检查使用喵
        if (!pdc.has(CookingKeys.SEASONING_ID, PersistentDataType.STRING)) {
            pdc.set(CookingKeys.SEASONING_ID, PersistentDataType.STRING, itemKey);
            changed = true;
        }

        // 喵~写入时间戳（首次），供过期计算使用；不再删除旧时间戳喵
        if (!pdc.has(CookingKeys.FOOD_TIMESTAMP, PersistentDataType.LONG)) {
            pdc.set(CookingKeys.FOOD_TIMESTAMP, PersistentDataType.LONG, System.currentTimeMillis());
            changed = true;
        }

        // 获取保质期，用于 lore 展示喵
        io.github.thebusybiscuit.exoticgarden.cooking.config.SeasoningConfig.SeasoningData sd
            = seasonings.get(itemKey);
        int shelfLife = sd != null ? sd.shelfLifeMinutes : 0;
        long nowMs = pdc.get(CookingKeys.FOOD_TIMESTAMP, PersistentDataType.LONG);

        // 更新 lore：移除旧食物/食材行，保留或写入 [辅料] + 保质期喵
        List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
        String seasoningLine = "§7[辅料]";
        boolean hasSeasoningLine = false;

        java.util.Iterator<String> it = lore.iterator();
        while (it.hasNext()) {
            String line = it.next();
            if (line.startsWith("§7[食物]") || line.startsWith("§7[烹饪食材]")) {
                it.remove();
                changed = true;
            } else if (line.equals(seasoningLine)) {
                hasSeasoningLine = true;
            }
        }

        if (!hasSeasoningLine) {
            lore.add(seasoningLine);
            changed = true;
        }

        // 喵~有渗入度的辅料显示配置中的完整渗入时间，避免部分辅料缺少时间提示喵
        if (sd != null && sd.hasDoneness) {
            // 将秒数四舍五入到一位小数，保持配置精度且避免显示过长小数喵
            double roundedBaseTimeSeconds = Math.round(sd.baseTimeSeconds * 10.0) / 10.0;
            // 生成统一的渗入时间 Lore 行喵
            String donenessTimeLine = "§7渗入时间: §e" + roundedBaseTimeSeconds + "秒";
            // 替换已有渗入时间行，确保配置更新后旧显示不会残留喵
            replaceLoreLineOrAdd(lore, "§7渗入时间:", donenessTimeLine);
            changed = true;
        } else {
            // 喵~防御：无渗入度的辅料清理旧渗入时间行，避免误导玩家喵
            if (lore.removeIf(line -> line.startsWith("§7渗入时间:"))) changed = true;
        }

        // 喵~有保质期的调料显示保质期和生产日期喵
        if (shelfLife > 0) {
            String shelfLifeLine = "§8保质期: " + formatShelfLife(shelfLife);
            boolean hasShelfLine = false;
            for (int i = 0; i < lore.size(); i++) {
                if (lore.get(i).startsWith("§8保质期:")) {
                    if (!lore.get(i).equals(shelfLifeLine)) { lore.set(i, shelfLifeLine); changed = true; }
                    hasShelfLine = true; break;
                }
            }
            if (!hasShelfLine) { lore.add(shelfLifeLine); changed = true; }

            boolean hasProductionLine = lore.stream().anyMatch(l -> l.startsWith("§8生产日期:"));
            if (!hasProductionLine) { lore.add("§8生产日期: " + formatTimestamp(nowMs)); changed = true; }

            // 使用毫秒边界判断调料过期，保持与统一实时判定服务的阈值一致喵
            long elapsedMillis = System.currentTimeMillis() - nowMs;
            // 喵~防御：系统时钟回拨时不把调料误标为过期喵
            boolean expired = elapsedMillis >= 0L && elapsedMillis >= (long) shelfLife * 60_000L;
            String expiredMark = "§c已过期";
            boolean hasExpiredMark = !lore.isEmpty() && lore.get(0).equals(expiredMark);
            if (expired && !hasExpiredMark) { lore.add(0, expiredMark); changed = true; }
            else if (!expired && hasExpiredMark) { lore.remove(0); changed = true; }
        } else {
            // 无保质期：清除旧保质期行喵
            if (lore.removeIf(l -> l.startsWith("§8保质期:") || l.startsWith("§8生产日期:") || l.equals("§c已过期"))) {
                changed = true;
            }
        }

        if (changed) {
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return true;
    }

    /**
     * 刷新菜肴的过期 lore 和 displayName，不改动任何 PDC 字段喵~
     */
    private boolean refreshDishExpiryLore(ItemStack item, ItemMeta meta) {
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        Long timestamp = pdc.get(CookingKeys.FOOD_TIMESTAMP, PersistentDataType.LONG);
        Integer shelfLifeMinutes = pdc.get(CookingKeys.DISH_SHELF_LIFE, PersistentDataType.INTEGER);
        if (timestamp == null || shelfLifeMinutes == null) return false;

        // 获取统一服务，确保菜肴 lore 与食用处罚按同一 PDC 和配置边界判定喵
        FoodExpiryService foodExpiryService = CookingModule.getFoodExpiryService();
        // 读取当前时间一次，避免同次刷新出现边界抖动喵
        long nowMillis = System.currentTimeMillis();
        // 解析菜肴有效期限信息，损坏 PDC 时安全跳过展示更新喵
        java.util.Optional<FoodExpiryService.ExpiryInfo> expiryInfo = foodExpiryService != null
                ? foodExpiryService.getExpiryInfo(item)
                : java.util.Optional.empty();
        // 喵~防御：没有有效期限的菜肴不应被添加过期标记喵
        if (expiryInfo.isEmpty()) return false;
        // 读取统一服务解析出的期限快照喵
        FoodExpiryService.ExpiryInfo resolvedInfo = expiryInfo.get();
        // 计算连续保质期进度供营养 lore 复用喵
        double shelfProgress = resolvedInfo.calculateProgressAt(nowMillis);
        // 按统一毫秒边界判断是否已过期喵
        boolean expired = resolvedInfo.isExpiredAt(nowMillis);

        List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
        String expiredMark = "§c已过期";
        boolean hasExpiredMark = !lore.isEmpty() && lore.get(0).equals(expiredMark);

        boolean changed = false;
        if (expired && !hasExpiredMark) {
            lore.add(0, expiredMark);
            changed = true;
        } else if (!expired && hasExpiredMark) {
            lore.remove(0);
            changed = true;
        }

        // 喵~营养行：读取原始饱食/饱和/品质/份量，重新计算当前值+减少量并替换喵
        Integer baseHunger = pdc.get(CookingKeys.DISH_HUNGER, PersistentDataType.INTEGER);
        Double baseSat = pdc.get(CookingKeys.DISH_SATURATION, PersistentDataType.DOUBLE);
        String quality = pdc.get(CookingKeys.DISH_QUALITY, PersistentDataType.STRING);
        Integer servings = pdc.get(CookingKeys.DISH_SERVINGS_REMAINING, PersistentDataType.INTEGER);
        if (baseHunger != null && baseSat != null && quality != null && servings != null) {
            String newNutritionLine = DishConsumptionListener.buildNutritionLine(
                quality, baseHunger, baseSat, servings, shelfProgress);
            // 喵~找到§7品质:开头的行替换，找不到则追加喵
            boolean found = false;
            // 喵~过期标记占第0行时，品质行紧接其后，偏移1；否则从0开始找喵
            for (int i = 0; i < lore.size(); i++) {
                if (lore.get(i).startsWith("§7品质:")) {
                    if (!lore.get(i).equals(newNutritionLine)) {
                        lore.set(i, newNutritionLine);
                        changed = true;
                    }
                    found = true;
                    break;
                }
            }
            if (!found) {
                // 喵~防御：lore里没有品质行时追加喵
                lore.add(newNutritionLine);
                changed = true;
            }
        }

        // 喵~displayName 加/移除"§7(过期)"后缀喵
        String displayName = meta.hasDisplayName() ? meta.getDisplayName() : null;
        if (expired && displayName != null && !displayName.endsWith("§7(过期)")) {
            meta.setDisplayName(displayName + "§7(过期)");
            changed = true;
        } else if (!expired && displayName != null && displayName.endsWith("§7(过期)")) {
            meta.setDisplayName(displayName.substring(0, displayName.length() - "§7(过期)".length()));
            changed = true;
        }

        if (changed) {
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return changed;
    }

    /**
     * 启动1200tick定时扫描（60秒一次），兜底覆盖所有遗漏场景喵~
     * 需在插件初始化时调用一次喵
     */
    public void startPeriodicScan(org.bukkit.plugin.java.JavaPlugin plugin) {
        // 喵~每1200tick(60秒)扫描所有在线玩家背包+副手，避免频繁setItem造成卡手喵
        org.bukkit.Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (org.bukkit.entity.Player p : org.bukkit.Bukkit.getOnlinePlayers()) {
                scanPlayerInventory(p);
            }
        }, 1200L, 1200L);
    }

    /**
     * 为食材打标签：写入食材状态、ID、时间戳，并更新保质期lore喵~
     * 复杂逻辑说明：
     *   - 已有 FOOD_STATE 标签的物品不再早return，而是继续刷新时间戳和保质期lore
     *   - lore中保质期行以"§8保质期:"开头作为标识，找到则替换，找不到则追加
     *   - 时间戳存入PDC的LONG类型，直接用System.currentTimeMillis()喵
     * 输入：ItemStack（可为null）
     * 输出：是否对物品做了修改（true=改了，false=未改）
     * 边界：item为null、air直接返回false喵
     */
    private boolean tagIfIngredient(ItemStack item) {
        // 喵~防御：item为null或空气时直接返回，避免NPE喵
        if (item == null || item.getType().isAir()) return false;
        // 喵~防御：菜肴物品（有DISH_HUNGER PDC）不参与保质期标签体系，跳过喵
        ItemMeta preCheck = item.getItemMeta();
        if (preCheck != null && preCheck.getPersistentDataContainer()
                .has(CookingKeys.DISH_HUNGER, PersistentDataType.INTEGER)) return false;
        // 解析该物品对应的食材ID，不是食材则跳过喵
        String ingId = resolveIngredientId(item);
        if (ingId == null) return false;

        // 获取物品meta，meta不存在则无法写PDC喵
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        PersistentDataContainer pdc = meta.getPersistentDataContainer();

        // 首次标记时写入食材状态和食材ID：通用食物/药水不写FOOD_STATE（它们不参与烹饪状态机）喵
        boolean isGeneric = GENERIC_FOOD_ID.equals(ingId) || GENERIC_POTION_ID.equals(ingId);
        if (!isGeneric && !pdc.has(CookingKeys.FOOD_STATE, PersistentDataType.STRING)) {
            // 首次打标签，设置整块状态（仅烹饪食材需要）喵
            pdc.set(CookingKeys.FOOD_STATE, PersistentDataType.STRING, "WHOLE");
        }
        if (!pdc.has(CookingKeys.INGREDIENT_ID, PersistentDataType.STRING)) {
            // 首次打标签，写入食材ID（通用标识也写入，用于后续识别）喵
            pdc.set(CookingKeys.INGREDIENT_ID, PersistentDataType.STRING, ingId);
        }

        // 时间戳不可变：首次写入后不再更新，用于计算保质期喵
        if (!pdc.has(CookingKeys.FOOD_TIMESTAMP, PersistentDataType.LONG)) {
            pdc.set(CookingKeys.FOOD_TIMESTAMP, PersistentDataType.LONG, System.currentTimeMillis());
        }
        // 从PDC读取不可变时间戳喵
        long nowMs = pdc.get(CookingKeys.FOOD_TIMESTAMP, PersistentDataType.LONG);

        // 构建lore列表，已有lore则复制一份可修改的副本喵
        List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();

        // 判断是通用食物、通用药水还是普通食材配置，分别取保质期和标签行喵
        final int shelfLifeMinutes; // 最终使用的保质期（分钟）喵
        final String ingredientLine; // 最终显示在lore里的类型标签行喵

        if (GENERIC_FOOD_ID.equals(ingId)) {
            // 通用食物：先查 foods.yml overrides 逐物品覆盖，再查 ingredients 通用配置，最后用默认值喵
            String itemKey = io.github.thebusybiscuit.exoticgarden.cooking.util.ItemIdUtil.toKey(item);
            java.util.Optional<Integer> override = itemKey != null
                ? foodsConfig.getOverrideShelfLife(itemKey)
                : java.util.Optional.empty();
            if (override.isPresent()) {
                shelfLifeMinutes = override.get();
            } else {
                IngredientConfig.IngredientData genericFood = ingredients.get(GENERIC_FOOD_ID);
                shelfLifeMinutes = genericFood != null ? genericFood.shelfLifeMinutes : genericFoodShelfLifeMinutes;
            }
            ingredientLine = "§7[食物]";
        } else if (GENERIC_POTION_ID.equals(ingId)) {
            // 通用药水/水瓶：lore显示"[药水] 200ml"，保质期优先读配置文件，默认用 foodsConfig 值喵
            IngredientConfig.IngredientData genericPotion = ingredients.get(GENERIC_POTION_ID);
            shelfLifeMinutes = genericPotion != null ? genericPotion.shelfLifeMinutes : genericPotionShelfLifeMinutes;
            ingredientLine = "§7[药水] §b200ml";
        } else {
            // 普通烹饪食材：从配置读取营养值、保质期和克重喵
            // 可变标签：营养值随配置变化，每次刷新喵
            IngredientConfig.IngredientData data = ingredients.get(ingId);
            double nutrition = data != null ? data.foodPoints + data.saturation : 0;
            shelfLifeMinutes = data != null ? data.shelfLifeMinutes : 10; // 保质期分钟数，默认10喵
            double weightGrams = data != null ? data.weightGrams : 100; // 克重，默认100g喵
            String nutritionStr = nutrition > 0
                    ? " §e营养度: " + (Math.round(nutrition * 10.0) / 10.0)
                    : "";
            // 喵~防御：克重为0时不显示(0g)，只在克重>0时显示喵
            String weightStr = weightGrams > 0 ? " §7(" + (int) weightGrams + "g)" : "";
            // 不可变标签：读取实际 FOOD_STATE，而非硬编码"WHOLE"喵
            String currentState = pdc.get(CookingKeys.FOOD_STATE, PersistentDataType.STRING);
            ingredientLine = "§7[烹饪食材] §f" + translateState(currentState) + nutritionStr + weightStr;

            // 喵~可变：推荐烹饪温度和时间，按当前切割状态倍率计算实际时间，每次刷新喵
            if (data != null) {
                // 从PDC读取当前切割状态，计算成熟速度倍率喵
                String rawFoodState = pdc.get(CookingKeys.FOOD_STATE, PersistentDataType.STRING);
                io.github.thebusybiscuit.exoticgarden.cooking.state.FoodState foodState =
                    io.github.thebusybiscuit.exoticgarden.cooking.state.FoodState.WHOLE;
                if (rawFoodState != null) {
                    try { foodState = io.github.thebusybiscuit.exoticgarden.cooking.state.FoodState.valueOf(rawFoodState); }
                    catch (IllegalArgumentException ignored) {}
                }
                double multiplier = foodState.getMultiplier();
                // 实际成熟时间 = 基础时间 / 倍率，保留一位小数喵
                double effectiveTime = Math.round(data.baseCookTimeSeconds / multiplier * 10.0) / 10.0;
                String cookTimeStr;
                if (multiplier > 1.0) {
                    // 切割状态：显示"实际时间s(-减少秒数s)"，格式与KnifeItem一致喵
                    double reducedSeconds = Math.round((data.baseCookTimeSeconds - effectiveTime) * 10.0) / 10.0;
                    cookTimeStr = "§e" + effectiveTime + "s§7(-" + reducedSeconds + "s)";
                } else {
                    // WHOLE：无减少，直接显示原始时间喵
                    cookTimeStr = "§e" + effectiveTime + "s";
                }
                String tempLine = "§7推荐温度: §e" + (int) data.matureRefTemp + "°C  §7烹饪时间: " + cookTimeStr;
                replaceLoreLineOrAdd(lore, "§7推荐温度:", tempLine);
            }

            // 喵~可变：loreHint 非空时替换/追加，为空时删除旧提示行喵
            if (data != null && !data.loreHint.isEmpty()) {
                String hintLine = "§8食材提示: §7" + data.loreHint;
                pdc.set(CookingKeys.INGREDIENT_HINT, PersistentDataType.STRING, data.loreHint);
                replaceLoreLineOrAdd(lore, "§8食材提示:", hintLine);
            } else {
                // loreHint 为空时删除已有的提示行喵
                lore.removeIf(line -> line.startsWith("§8食材提示:"));
                pdc.remove(CookingKeys.INGREDIENT_HINT);
            }
            // [已废弃] hint 现改为传给 AI 的提示词，不再写入物品 lore 喵
            // if (data != null && !data.hint.isEmpty()) {
            //     String hintLine = "§8食材提示: §7" + data.hint;
            //     pdc.set(CookingKeys.INGREDIENT_HINT, PersistentDataType.STRING, data.hint);
            //     replaceLoreLineOrAdd(lore, "§8食材提示:", hintLine);
            // }
        }

        // 检查lore中是否已有对应类型行，有则替换，没有则追加喵
        boolean hasIngredientLine = false; // 标记是否已找到并替换了食材行喵
        for (int loreIdx = 0; loreIdx < lore.size(); loreIdx++) {
            String loreLine = lore.get(loreIdx);
            // 匹配"§7[烹饪食材]"、"§7[食物]"或"§7[药水]"开头的行喵
            if (loreLine.startsWith("§7[烹饪食材]") || loreLine.startsWith("§7[食物]") || loreLine.startsWith("§7[药水]")) {
                // 找到旧的类型行，替换为新内容喵
                lore.set(loreIdx, ingredientLine);
                hasIngredientLine = true;
                break;
            }
        }
        if (!hasIngredientLine) {
            // 没有找到旧类型行，直接追加喵
            lore.add(ingredientLine);
        }

        // 可变标签：保质期随配置变化，每次刷新喵
        String shelfLifeLine = "§8保质期: " + formatShelfLife(shelfLifeMinutes);

        // 可变标签：过期状态随时间变化，每次刷新，以毫秒边界与实时消费判定保持一致喵
        long elapsedMillis = System.currentTimeMillis() - nowMs;
        // 喵~防御：系统时钟回拨时不把未来时间戳物品误标为过期喵
        boolean expired = elapsedMillis >= 0L && elapsedMillis >= (long) shelfLifeMinutes * 60_000L;

        // 生产日期不可变：首次写入后不再更新喵
        boolean hasProductionLine = false;
        for (int loreIdx = 0; loreIdx < lore.size(); loreIdx++) {
            if (lore.get(loreIdx).startsWith("§8生产日期:")) {
                hasProductionLine = true;
                break;
            }
        }
        if (!hasProductionLine) {
            // 首次写入生产日期喵
            lore.add("§8生产日期: " + formatTimestamp(nowMs));
        }

        // 替换或追加保质期固定行喵
        boolean hasShelfLifeLine = false;
        for (int loreIdx = 0; loreIdx < lore.size(); loreIdx++) {
            if (lore.get(loreIdx).startsWith("§8保质期:")) {
                lore.set(loreIdx, shelfLifeLine);
                hasShelfLifeLine = true;
                break;
            }
        }
        if (!hasShelfLifeLine) {
            lore.add(shelfLifeLine);
        }

        // 处理过期：首行插入/移除"§c已过期"标记，并修改displayName喵
        String expiredMark = "§c已过期";
        boolean hasExpiredMark = !lore.isEmpty() && lore.get(0).equals(expiredMark);
        if (expired && !hasExpiredMark) {
            // 过期时在lore最前插入红色过期标记喵
            lore.add(0, expiredMark);
        } else if (!expired && hasExpiredMark) {
            // 未过期但有旧过期标记，移除喵
            lore.remove(0);
        }

        // 处理过期时displayName追加§7(过期)喵
        String displayName = meta.hasDisplayName() ? meta.getDisplayName() : null;
        if (expired) {
            if (displayName != null && !displayName.endsWith("§7(过期)")) {
                // 追加过期后缀喵
                meta.setDisplayName(displayName + "§7(过期)");
            } else if (displayName == null) {
                // 原版物品无displayName，用本地化译名兜底并追加喵
                String localizedName = getLocalizedName(item);
                meta.setDisplayName(localizedName + "§7(过期)");
            }
        } else if (displayName != null && displayName.endsWith("§7(过期)")) {
            // 未过期时移除过期后缀喵
            meta.setDisplayName(displayName.substring(0, displayName.length() - "§7(过期)".length()));
        }

        // 把修改后的lore和meta写回物品喵
        meta.setLore(lore);
        item.setItemMeta(meta);
        return true;
    }

    /**
     * 根据时间戳生成已存放时间的描述文字喵~
     * 输入：timestampMs - 食材被标记时的毫秒时间戳
     * 输出：时间描述字符串，例如 "2分钟前"/"刚刚"
     */
    public static String buildTimeDesc(long timestampMs) {
        // 计算距今的毫秒差值喵
        long diffMs = System.currentTimeMillis() - timestampMs;
        // 喵~防御：差值为负（时钟回拨等异常情况），视为刚刚标记喵
        if (diffMs < 0) diffMs = 0;

        long totalSeconds = diffMs / 1000L;
        long totalMinutes = totalSeconds / 60L;
        long totalHours   = totalMinutes / 60L;
        long days         = totalHours / 24L;
        long hours        = totalHours % 24L;

        if (totalMinutes < 1) {
            return "刚刚";
        } else if (totalHours < 1) {
            return totalMinutes + "分钟前";
        } else if (days < 1) {
            return totalHours + "小时前";
        } else if (hours == 0) {
            return days + "天前";
        } else {
            return days + "天" + hours + "小时前";
        }
    }

    // 兼容旧调用，委托给新方法喵
    static String buildFreshnessLore(long timestampMs) {
        return "§8生产日期: " + buildTimeDesc(timestampMs);
    }

    /**
     * 将毫秒时间戳格式化为"xxxx年xx月xx日 xx时xx分xx秒"喵~
     */
    public static String formatTimestamp(long timestampMs) {
        java.time.LocalDateTime dt = java.time.LocalDateTime.ofInstant(
                java.time.Instant.ofEpochMilli(timestampMs),
                java.time.ZoneId.systemDefault());
        return String.format("%d年%02d月%02d日 %02d时%02d分%02d秒",
                dt.getYear(), dt.getMonthValue(), dt.getDayOfMonth(),
                dt.getHour(), dt.getMinute(), dt.getSecond());
    }

    /**
     * 将保质期分钟数转换为可读单位字符串，最小单位分钟，自动升级到小时/天喵~
     * 例：10→"10分钟"，90→"1小时30分钟"，1440→"1天"，1500→"1天1小时"
     */
    public static String formatShelfLife(int totalMinutes) {
        // 喵~防御：负数或0视为无效，返回0分钟喵
        if (totalMinutes <= 0) return "0分钟";
        int days    = totalMinutes / 1440; // 1天=1440分钟喵
        int hours   = (totalMinutes % 1440) / 60;
        int minutes = totalMinutes % 60;
        StringBuilder sb = new StringBuilder();
        if (days > 0)    sb.append(days).append("天");
        if (hours > 0)   sb.append(hours).append("小时");
        if (minutes > 0) sb.append(minutes).append("分钟");
        return sb.toString();
    }

    // 将食材状态枚举转为中文显示名喵
    private static String translateState(String state) {
        return switch (state) {
            case "WHOLE"     -> "整块";
            case "SLICED"    -> "切片";
            case "JULIENNED" -> "切丝";
            case "DICED"     -> "切丁";
            case "SAUCE"     -> "酱料";
            default          -> state;
        };
    }

    /**
     * 获取物品的本地化译名：优先Slimefun物品名，其次用zh_cn.json查原版中文译名，兜底英文枚举名喵
     * 输入：ItemStack物品。
     * 输出：本地化后的显示名称字符串。
     */
    private static String getLocalizedName(ItemStack item) {
        // 喵~防御：item为null时返回空字符串喵
        if (item == null) return "";
        // 优先：Slimefun物品的自定义名称喵
        SlimefunItem sfItem = SlimefunItem.getByItem(item);
        if (sfItem != null) {
            String sfName = sfItem.getItemName();
            if (sfName != null && !sfName.isEmpty()) return sfName;
        }
        // 其次：原版材质的中文译名（从zh_cn.json懒加载查找）喵
        String vanillaName = VanillaLocalization.getItemName(item.getType());
        if (vanillaName != null && !vanillaName.isEmpty()) return vanillaName;
        // 兜底：英文枚举名喵
        return item.getType().name();
    }

    /**
     * 原版材质中文译名懒加载工具类，从jar内zh_cn.json读取喵
     */
    private static class VanillaLocalization {
        private static final java.util.Map<String, String> CACHE = new java.util.HashMap<>();
        private static boolean loaded = false;

        static String getItemName(org.bukkit.Material mat) {
            if (!loaded) load();
            return CACHE.get(mat.name());
        }

        private static void load() {
            loaded = true;
            // 喵~优先从 Slimefun4 jar 读取 zh_cn.json（EG 自身 jar 不包含此文件）喵
            java.io.InputStream in = null;
            try {
                // 通过 SF4 主类加载 zh_cn.json 喵
                in = io.github.thebusybiscuit.slimefun4.implementation.Slimefun.class.getResourceAsStream("/zh_cn.json");
                // 喵~防御：SF4 路径失败时再尝试当前 jar 喵
                if (in == null) in = FoodTagListener.class.getResourceAsStream("/zh_cn.json");
                if (in == null) return;
                com.google.gson.JsonObject root = new com.google.gson.JsonParser()
                        .parse(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8))
                        .getAsJsonObject();
                for (java.util.Map.Entry<String, com.google.gson.JsonElement> entry : root.entrySet()) {
                    String key = entry.getKey();
                    if (key.startsWith("item.minecraft.") || key.startsWith("block.minecraft.")) {
                        // 将 "item.minecraft.rabbit_stew" 转为 "RABBIT_STEW" 枚举名喵
                        String matName = key.substring(key.lastIndexOf('.') + 1).toUpperCase(java.util.Locale.ENGLISH);
                        CACHE.put(matName, entry.getValue().getAsString());
                    }
                }
            } catch (Exception ignored) {
                // 喵~防御：解析失败时静默忽略，使用兜底名称喵
            } finally {
                if (in != null) try { in.close(); } catch (Exception ignored) {}
            }
        }
    }

    private String resolveIngredientId(ItemStack item) {
        // 喵~防御：只调用一次getItemMeta()避免重复创建ItemMeta副本
        ItemMeta itemMeta = item.getItemMeta();
        if (itemMeta != null) {
            PersistentDataContainer pdc = itemMeta.getPersistentDataContainer();

            // 1. PDC 中已有 INGREDIENT_ID 且在 map 里 → 直接返回喵
            String id = pdc.get(CookingKeys.INGREDIENT_ID, PersistentDataType.STRING);
            if (id != null && ingredients.containsKey(id)) return id;
        }

        // 2. 用 ItemIdUtil 生成带命名空间 key 在 map 中查找：
        //    SF 物品 → "slimefun:ID"，原版 → "minecraft:MATERIAL" 喵
        String itemKey = io.github.thebusybiscuit.exoticgarden.cooking.util.ItemIdUtil.toKey(item);
        if (itemKey != null && ingredients.containsKey(itemKey)) return itemKey;

        // 喵~防御：如果物品在 seasonings map 里，说明它是调料不是食物，跳过喵
        if (itemKey != null && seasonings.containsKey(itemKey)) return null;

        // ===== fallback：食材配置里没有时，检查原版食物和药水 =====
        Material mat = item.getType();

        // 喵~防御：检查是否是药水类型（不受黑名单约束，药水不在黑名单中）喵
        if (mat == Material.POTION || mat == Material.SPLASH_POTION || mat == Material.LINGERING_POTION) {
            // 喵~防御：水瓶 Material=POTION 但无任何药水效果，不应视为药水食材，直接跳过喵
            if (itemMeta instanceof org.bukkit.inventory.meta.PotionMeta potionMeta) {
                org.bukkit.potion.PotionType base = null;
                try { base = potionMeta.getBasePotionType(); } catch (Exception ignored) {}
                boolean hasEffects = (base != null && base != org.bukkit.potion.PotionType.WATER
                        && base != org.bukkit.potion.PotionType.MUNDANE
                        && base != org.bukkit.potion.PotionType.THICK)
                        || !potionMeta.getCustomEffects().isEmpty();
                // 喵~无有效效果（水瓶/平淡/浓稠药水）直接返回null，不参与烹饪标签体系喵
                if (!hasEffects) return null;
            } else {
                // 喵~防御：meta不是PotionMeta时，无法确认效果，跳过喵
                return null;
            }
            // 药水类物品返回通用药水标识符，保质期默认5分钟喵
            return GENERIC_POTION_ID;
        }

        // 检查是否是可食用原版物品或SF食品，且不在黑名单内喵（blacklist 来自 foodsConfig）
        if (mat.isEdible() && !blacklist.contains(mat)) {
            return GENERIC_FOOD_ID;
        }

        // SF加工食品：有PDC SLIMEFUN_ITEM标记的可食用物品（如汉堡、派等）也加保质期喵
        if (itemMeta != null) {
            PersistentDataContainer pdc = itemMeta.getPersistentDataContainer();
            String sfId = pdc.get(KEY_SF_ITEM, PersistentDataType.STRING);
            if (sfId != null) {
                SlimefunItem sfItem = SlimefunItem.getById(sfId);
                if (sfItem != null) {
                    // 喵~原版材质可食用、EGPlant、CustomFood、或 ExoticGardenFruit（果实类，不是 EGPlant/CustomFood 子类但可食用）喵
                    if (sfItem.getItem().getType().isEdible()
                            || sfItem instanceof io.github.thebusybiscuit.exoticgarden.EGPlant
                            || sfItem instanceof io.github.thebusybiscuit.exoticgarden.items.CustomFood
                            || sfItem instanceof io.github.thebusybiscuit.exoticgarden.items.ExoticGardenFruit) {
                        return GENERIC_FOOD_ID;
                    }
                }
            }
        }

        // 既不是配置食材、也不是药水、也不是可食用物品，返回null跳过喵
        return null;
    }

    /**
     * 替换 lore 中以 prefix 开头的行，找不到则追加喵~
     * 复用 DishConsumptionListener.replaceLoreLine 相同逻辑喵
     */
    private static void replaceLoreLineOrAdd(List<String> lore, String prefix, String newLine) {
        for (int i = 0; i < lore.size(); i++) {
            if (lore.get(i).startsWith(prefix)) {
                lore.set(i, newLine);
                return;
            }
        }
        lore.add(newLine);
    }

    /**
     * 为燃料物品打 lore 标签喵~
     * 整体思路：查燃料配置 → 写 [燃料] 行 + 可选 hint 行到 lore + 写 PDC。
     * hint 缺省（空字符串）时不添加也不删除 lore 中的 §8燃料提示: 行喵。
     * 输入：ItemStack（可为null）
     * 输出：是否对物品做了修改
     */
    private boolean tagIfFuel(ItemStack item) {
        // 喵~防御：null 或空气直接跳过喵
        if (item == null || item.getType().isAir()) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        // 喵~防御：已有食材或菜肴标记的物品不处理喵
        if (pdc.has(CookingKeys.DISH_HUNGER, PersistentDataType.INTEGER)) return false;
        if (pdc.has(CookingKeys.INGREDIENT_ID, PersistentDataType.STRING)) return false;

        // 查燃料配置喵
        String fuelKey = ItemIdUtil.toKey(item);
        if (fuelKey == null) return false;
        FuelConfig.FuelData data = fuels.get(fuelKey);
        if (data == null) return false;

        List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();

        // 喵~可变：[燃料] 行始终替换/追加喵
        String fuelLine = "§7[燃料] §f" + data.effectDisplayName;
        replaceLoreLineOrAdd(lore, "§7[燃料]", fuelLine);

        // [已废弃] hint 现改为传给 AI 的提示词，不再写入物品 lore 喵
        // if (!data.hint.isEmpty()) {
        //     pdc.set(CookingKeys.FUEL_HINT, PersistentDataType.STRING, data.hint);
        //     String hintLine = "§8燃料提示: §7" + data.hint;
        //     replaceLoreLineOrAdd(lore, "§8燃料提示:", hintLine);
        // }

        meta.setLore(lore);
        item.setItemMeta(meta);
        return true;
    }
}
