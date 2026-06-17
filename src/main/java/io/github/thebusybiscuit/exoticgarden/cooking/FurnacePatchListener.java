package io.github.thebusybiscuit.exoticgarden.cooking;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.FurnaceSmeltEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.FurnaceInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/**
 * 熔炉补丁监听器：防止菜肴/食材被丢进熔炉类设备烹饪喵~
 *
 * 覆盖范围：
 *   1. 原版熔炉 / 烟熏炉 / 高炉 — 通过 FurnaceSmeltEvent 取消 + InventoryClickEvent 阻止放入
 *   2. 粘液科技电炉等 — 通过 InventoryClickEvent 阻止放入（即使 SF 无对应配方不会被处理，
 *      放入输入槽本身就会造成困惑，这里直接拦截）
 */
public class FurnacePatchListener implements Listener {

    /**
     * 熔炉产出事件：菜肴/食材作为原料被冶炼时直接取消喵~
     * 覆盖原版熔炉、烟熏炉、高炉的冶炼行为喵
     */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onFurnaceSmelt(FurnaceSmeltEvent e) {
        // 检查冶炼原料是否是菜肴喵
        if (isDish(e.getSource())) {
            e.setCancelled(true);
        }
    }

    /**
     * 背包点击事件：菜肴被点击放入熔炉类 GUI 的原料槽时取消喵~
     * 覆盖原版熔炉、烟熏炉、高炉以及粘液科技机器 GUI 喵
     */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onInventoryClick(InventoryClickEvent e) {
        // 喵~防御：只有移动物品到目标背包才需要检查喵
        ItemStack cursor = e.getCursor();
        if (cursor == null || cursor.getType().isAir()) return;

        // 只关心菜肴物品喵
        if (!isDish(cursor)) return;

        // 获取点击的 slot 索引，只拦截放入原料槽（slot 0）的行为喵
        int rawSlot = e.getRawSlot();
        // 喵~rawSlot < 0 表示点击的是玩家背包而不是机器GUI喵
        if (rawSlot < 0) return;

        // 喵~检查顶层背包是否为熔炉类（原版 furnace/smoker/blast_furnace 都是 FurnaceInventory）
        // Slimefun 电炉等机器如果使用了 SF 的自定义 GUI，isDish 外的 recipe 系统会自动拒绝处理，
        // 但为了安全起见，也检查 SF 机器常见的 slot 0 = 输入槽 的特征喵
        boolean isFurnaceLike = false;
        if (e.getInventory() instanceof org.bukkit.inventory.FurnaceInventory) {
            // 原版熔炉 / 烟熏炉 / 高炉喵
            isFurnaceLike = true;
        } else if (e.getInventory().getType() == InventoryType.FURNACE) {
            // 粘液科技等插件可能注册 FURNACE 类型但不返回 FurnaceInventory 实例喵
            isFurnaceLike = true;
        }

        // 喵~不是熔炉类 GUI，不拦截喵
        if (!isFurnaceLike) return;

        // 喵~原料槽（slot 0）才拦截喵
        if (rawSlot == 0) {
            e.setCancelled(true);
            // 喵~发送提示给玩家，让他知道菜肴不能放入熔炉喵
            if (e.getWhoClicked() instanceof org.bukkit.entity.Player player) {
                player.sendMessage("§c菜肴不能放入熔炉烹饪喵~");
            }
        }
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
}
