package io.github.thebusybiscuit.exoticgarden.cooking;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.FurnaceSmeltEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import io.github.thebusybiscuit.exoticgarden.cooking.util.ItemIdUtil;

/**
 * 熔炉补丁监听器：防止菜肴被丢进任何熔炉类设备烹饪喵~
 *
 * 覆盖范围：
 *   1. 原版熔炉 / 烟熏炉 / 高炉 — FurnaceSmeltEvent 取消冶炼产出
 *   2. 原版熔炉 / 烟熏炉 / 高炉 / SF 电炉 — InventoryClickEvent 阻止放入原料槽
 *   3. 漏斗喂料 — InventoryMoveItemEvent 阻止漏斗向熔炉输入菜肴
 *
 * 防御策略：不依赖 InventoryType 判断（SF 机器的 GUI 底层可能是 CHEST 而非 FURNACE），
 * 而是直接检查 PDC 中的 DISH_HUNGER 字段，命中即拦截喵~
 */
public class FurnacePatchListener implements Listener {

    /**
     * 第1层：熔炉产出事件 — 原版熔炉/烟熏炉/高炉冶炼时取消喵~
     * 无论物品是怎样进入原料槽的（玩家、漏斗、命令），冶炼前都会被拦截喵
     */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onFurnaceSmelt(FurnaceSmeltEvent e) {
        // 检查冶炼原料是否是菜肴或过期食材喵
        if (isFurnaceBlocked(e.getSource())) {
            e.setCancelled(true);
        }
    }

    /**
     * 第2层：背包点击事件 — 阻止玩家手动把菜肴放入任何机器的输入槽喵~
     *
     * 判断逻辑：
     *   - 光标上有菜肴物品
     *   - rawSlot 指向目标背包的顶层（非玩家背包区域）
     *   - 顶层背包是机器类（FURNACE/BLAST_FURNACE/SMOKER/或SF自定义GUI）
     *
     * 注意：不阻塞菜肴放入普通箱子/潜影盒等存储容器喵
     */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onInventoryClick(InventoryClickEvent e) {
        // 喵~检查光标（正在移动的物品），是菜肴或过期食材才处理喵
        ItemStack cursor = e.getCursor();
        if (cursor == null || cursor.getType().isAir()) return;
        if (!isFurnaceBlocked(cursor)) return;

        // 获取被点击的 slot 索引喵
        int rawSlot = e.getRawSlot();
        // rawSlot < 0 表示点击的是窗口外（如丢弃物品），不拦截喵
        if (rawSlot < 0) return;

        // 喵~判断是否点击到了顶层背包（机器/容器）而非玩家背包喵
        Inventory topInv = e.getView().getTopInventory();
        int topSize = topInv.getSize();
        boolean isTopSlot = rawSlot < topSize;

        if (!isTopSlot) return; // 点击的是玩家背包区域，不拦截喵

        // 喵~判断目标背包是否像是"机器"而非普通存储容器喵
        if (!isMachineInventory(topInv)) return;

        // 喵~对于熔炉类 GUI（3个槽：原料0/燃料1/产物2），只拦截原料槽(slot 0)
        // 对于 SF 自定义 GUI（槽位可能更多），slot 0 通常是输入槽，保守只拦截 slot 0 喵
        if (isFurnaceLike(topInv)) {
            // 熔炉类：原料=0，燃料=1，产物=2 → 只拦截原料槽喵
            if (rawSlot != 0) return;
        }
        // 喵~其他机器（SF 电炉等自定义 GUI）：slot 0 通常是输入槽，拦截喵

        e.setCancelled(true);
        // 喵~提示玩家菜肴不能放入机器喵
        if (e.getWhoClicked() instanceof org.bukkit.entity.Player player) {
            player.sendMessage("§c菜肴或过期食材不能放入机器喵~");
        }
    }

    /**
     * 第3层：漏斗移动物品事件 — 阻止漏斗向机器输入菜肴喵~
     * 覆盖漏斗→原版熔炉、漏斗→SF 机器等场景喵
     */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onInventoryMove(InventoryMoveItemEvent e) {
        // 检查被移动的物品是否是菜肴或过期食材喵
        if (isFurnaceBlocked(e.getItem())) {
            // 检查目标背包是否是机器类（避免干扰漏斗→箱子的正常传输）喵
            Inventory dest = e.getDestination();
            if (isMachineInventory(dest)) {
                e.setCancelled(true);
            }
        }
    }

    /**
     * 判断背包是否像是一个"机器"类的背包（而非普通存储容器）喵~
     *
     * 判断标准：
     *   - 原版熔炉类：FURNACE / BLAST_FURNACE / SMOKER
     *   - SF 机器：InventoryHolder 实现了 Slimefun 的 BlockMenu 接口，
     *     或者背包标题匹配 SF 机器命名格式
     */
    private boolean isMachineInventory(Inventory inv) {
        // 喵~防御：inv 为 null 时直接返回 false 喵
        if (inv == null) return false;

        org.bukkit.event.inventory.InventoryType type = inv.getType();

        // 原版熔炉类：直接命中喵
        if (type == org.bukkit.event.inventory.InventoryType.FURNACE
                || type == org.bukkit.event.inventory.InventoryType.BLAST_FURNACE
                || type == org.bukkit.event.inventory.InventoryType.SMOKER) {
            return true;
        }

        // 喵~SF 机器的 BlockMenu 实现了 io.github.thebusybiscuit.slimefun4.api.items.ItemHandler 相关接口
        // BlockMenuPreset 的 InventoryHolder 实现类可通过类名判断喵
        org.bukkit.inventory.InventoryHolder holder = inv.getHolder();
        if (holder != null) {
            String holderClassName = holder.getClass().getName();
            // SF 的 BlockMenu 相关持有者类名特征喵
            if (holderClassName.contains("slimefun")
                    && (holderClassName.contains("BlockMenu")
                        || holderClassName.contains("Menu")
                        || holderClassName.contains("Preset"))) {
                return true;
            }
        }

        return false;
    }

    /**
     * 判断背包是否是熔炉类（FURNACE/BLAST_FURNACE/SMOKER）喵~
     * 这类 GUI 有固定的槽位布局：原料=0, 燃料=1, 产物=2 喵
     */
    private boolean isFurnaceLike(Inventory inv) {
        // 喵~防御：inv 为 null 时直接返回 false 喵
        if (inv == null) return false;
        org.bukkit.event.inventory.InventoryType type = inv.getType();
        return type == org.bukkit.event.inventory.InventoryType.FURNACE
                || type == org.bukkit.event.inventory.InventoryType.BLAST_FURNACE
                || type == org.bukkit.event.inventory.InventoryType.SMOKER;
    }

    /**
     * 判断物品是否为烹饪系统菜肴喵~
     * 依据：PDC 中是否有 DISH_HUNGER 字段喵
     */
    private boolean isDish(ItemStack item) {
        // 喵~防御：null 或 air 直接返回 false 喵
        if (item == null || item.getType().isAir()) return false;
        ItemMeta meta = item.getItemMeta();
        // 喵~防御：meta 为 null 时无法检查 PDC 喵
        if (meta == null) return false;
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        // 有 DISH_HUNGER 标记的就是菜肴喵
        return pdc.has(CookingKeys.DISH_HUNGER, PersistentDataType.INTEGER);
    }

    /**
     * 判断物品是否为已过期的烹饪食材/调料喵~
     * 依据：lore 首行或任意行含 "§c已过期" 标记（由 FoodTagListener 写入）喵
     * 同时要求物品带有 FOOD_TIMESTAMP 或 INGREDIENT_ID 或 SEASONING_ID PDC，
     * 避免误判原版带红色 lore 的物品喵
     */
    private boolean isExpiredCookingItem(ItemStack item) {
        // 喵~防御：null 或 air 直接返回 false 喵
        if (item == null || item.getType().isAir()) return false;
        ItemMeta meta = item.getItemMeta();
        // 喵~防御：meta 为 null 时无法检查喵
        if (meta == null) return false;
        // 喵~防御：没有 lore 直接跳过喵
        if (!meta.hasLore()) return false;
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        // 必须有烹饪系统的 PDC 标记才视为烹饪食材/调料，避免误判原版物品喵
        boolean isCookingItem = pdc.has(CookingKeys.FOOD_TIMESTAMP, PersistentDataType.LONG)
                || pdc.has(CookingKeys.INGREDIENT_ID, PersistentDataType.STRING)
                || pdc.has(CookingKeys.SEASONING_ID, PersistentDataType.STRING);
        if (!isCookingItem) return false;
        // 检查 lore 中是否有"已过期"标记行喵
        for (String line : meta.getLore()) {
            if ("§c已过期".equals(line)) return true;
        }
        return false;
    }

    /**
     * 判断物品是否不应放入熔炉：菜肴 OR 已过期烹饪食材喵~
     */
    private boolean isFurnaceBlocked(ItemStack item) {
        return isDish(item) || isExpiredCookingItem(item);
    }
}
