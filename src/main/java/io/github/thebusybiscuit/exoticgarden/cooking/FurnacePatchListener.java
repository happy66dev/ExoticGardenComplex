package io.github.thebusybiscuit.exoticgarden.cooking;

// 导入营火烹饪事件以阻止过期物品被普通营火烧熟喵~
import org.bukkit.event.block.BlockCookEvent;
// 导入事件处理注解喵~
import org.bukkit.event.EventHandler;
// 导入事件优先级类型喵~
import org.bukkit.event.EventPriority;
// 导入监听器接口喵~
import org.bukkit.event.Listener;
// 导入熔炉冶炼事件喵~
import org.bukkit.event.inventory.FurnaceSmeltEvent;
// 导入库存点击事件喵~
import org.bukkit.event.inventory.InventoryClickEvent;
// 导入库存拖拽事件喵~
import org.bukkit.event.inventory.InventoryDragEvent;
// 导入库存物品移动事件喵~
import org.bukkit.event.inventory.InventoryMoveItemEvent;
// 导入库存类型喵~
import org.bukkit.inventory.Inventory;
// 导入物品类型喵~
import org.bukkit.inventory.ItemStack;
// 导入物品元数据类型喵~
import org.bukkit.inventory.meta.ItemMeta;
// 导入 PDC 容器类型喵~
import org.bukkit.persistence.PersistentDataContainer;
// 导入 PDC 数据类型喵~
import org.bukkit.persistence.PersistentDataType;

/**
 * 设备烹饪补丁监听器：阻止菜肴或过期受管理食物进入机器与普通营火喵~
 *
 * 整体思路：实时读取 PDC 和配置判断过期，不依赖最长滞后60秒的 lore 展示缓存喵~
 * 覆盖：鼠标、shift、快捷栏数字键、副手交换、拖拽、漏斗、熔炉成品与营火成品喵~
 */
public class FurnacePatchListener implements Listener {

    // 处理原版熔炉、烟熏炉和高炉的最终冶炼防线喵~
    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onFurnaceSmelt(FurnaceSmeltEvent event) {
        // 被禁止物品即使由命令或其他插件放入，也不能产生烧制结果喵~
        if (isFurnaceBlocked(event.getSource())) {
            // 取消本次冶炼，避免生成新鲜成品绕过保质期喵~
            event.setCancelled(true);
        }
    }

    // 处理普通营火的烹饪完成事件喵~
    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onBlockCook(BlockCookEvent event) {
        // 仅在来源为菜肴或实时已过期受管理物时取消普通营火产出喵~
        if (isFurnaceBlocked(event.getSource())) {
            // 取消烹饪以避免把过期原料转换成无时间戳的新鲜成品喵~
            event.setCancelled(true);
        }
    }

    // 处理玩家对机器库存的点击操作喵~
    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onInventoryClick(InventoryClickEvent event) {
        // 读取视图顶部库存作为机器目标喵~
        Inventory topInventory = event.getView().getTopInventory();
        // 非机器库存不属于本补丁范围，避免干扰普通容器喵~
        if (!isMachineInventory(topInventory)) {
            // 结束普通容器处理喵~
            return;
        }
        // shift 点击会从玩家背包自动寻址机器输入槽，需优先处理喵~
        if (event.isShiftClick()) {
            // 读取将被快速移动的当前物品喵~
            ItemStack currentItem = event.getCurrentItem();
            // 命中禁止规则时取消整次快速移动喵~
            if (isFurnaceBlocked(currentItem)) {
                // 取消点击以保持玩家物品与机器库存不变喵~
                event.setCancelled(true);
                // 向操作玩家发送一次明确提示喵~
                sendBlockedMessage(event.getWhoClicked());
            }
            // shift 路径处理完成后不再按光标或热键重复判断喵~
            return;
        }
        // 只有顶部输入槽才可能接收物品，其他槽位不应被本补丁误拦截喵~
        if (!isMachineInputSlot(event, topInventory)) {
            // 结束非输入槽处理喵~
            return;
        }
        // 解析本次点击实际可能移动到输入槽的来源物品喵~
        ItemStack sourceItem = resolveInputSource(event);
        // 新鲜物品、空气和无关物品允许按原版规则继续操作喵~
        if (!isFurnaceBlocked(sourceItem)) {
            // 结束允许操作喵~
            return;
        }
        // 取消危险输入操作，覆盖光标、数字键和副手交换喵~
        event.setCancelled(true);
        // 向操作玩家发送一次明确提示喵~
        sendBlockedMessage(event.getWhoClicked());
    }

    // 处理一次拖拽同时分配到多个库存槽的路径喵~
    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onInventoryDrag(InventoryDragEvent event) {
        // 读取顶部库存以识别机器视图喵~
        Inventory topInventory = event.getView().getTopInventory();
        // 非机器库存不应被拦截喵~
        if (!isMachineInventory(topInventory)) {
            // 结束普通容器处理喵~
            return;
        }
        // 读取被拖拽的光标物品喵~
        ItemStack oldCursor = event.getOldCursor();
        // 新鲜或无关物品无需检查目标槽喵~
        if (!isFurnaceBlocked(oldCursor)) {
            // 结束允许拖拽喵~
            return;
        }
        // 遍历本次拖拽涉及的原始槽位喵~
        for (int rawSlot : event.getRawSlots()) {
            // 仅当危险物确实会落入机器输入槽时取消喵~
            if (isMachineInputRawSlot(rawSlot, topInventory)) {
                // 取消整次拖拽以保证原子性和物品安全喵~
                event.setCancelled(true);
                // 向操作玩家发送一次明确提示喵~
                sendBlockedMessage(event.getWhoClicked());
                // 已取消后无需继续检查其他槽位喵~
                return;
            }
        }
    }

    // 处理漏斗等自动化库存转移路径喵~
    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onInventoryMove(InventoryMoveItemEvent event) {
        // 只有被禁止物品才需要检查目标库存喵~
        if (!isFurnaceBlocked(event.getItem())) {
            // 结束新鲜或无关物品处理喵~
            return;
        }
        // 目标是机器时阻止自动化输入喵~
        if (isMachineInventory(event.getDestination())) {
            // 取消移动以防止漏斗绕过玩家点击保护喵~
            event.setCancelled(true);
        }
    }

    // 解析点击路径实际会进入输入槽的来源物品喵~
    private ItemStack resolveInputSource(InventoryClickEvent event) {
        // 数字键路径的来源是玩家快捷栏对应索引，而不是空光标喵~
        if (event.getClick() == org.bukkit.event.inventory.ClickType.NUMBER_KEY) {
            // 读取快捷栏按钮索引喵~
            int hotbarButton = event.getHotbarButton();
            // 喵~防御：非法索引不读取玩家库存，避免越界异常喵~
            if (hotbarButton < 0 || hotbarButton > 8) {
                // 返回空值让调用方安全放行异常事件喵~
                return null;
            }
            // 返回对应快捷栏物品作为真实交换来源喵~
            return event.getWhoClicked().getInventory().getItem(hotbarButton);
        }
        // 副手交换路径的来源是玩家当前副手，而不是空光标喵~
        if (event.getClick() == org.bukkit.event.inventory.ClickType.SWAP_OFFHAND) {
            // 返回副手物品作为真实交换来源喵~
            return event.getWhoClicked().getInventory().getItemInOffHand();
        }
        // 其余普通点击沿用光标物品作为放入来源喵~
        return event.getCursor();
    }

    // 判断点击目标是否为机器顶层输入槽喵~
    private boolean isMachineInputSlot(InventoryClickEvent event, Inventory topInventory) {
        // 将原始槽位委托给统一索引判断喵~
        return isMachineInputRawSlot(event.getRawSlot(), topInventory);
    }

    // 判断原始槽位是否为机器顶层输入槽喵~
    private boolean isMachineInputRawSlot(int rawSlot, Inventory topInventory) {
        // 喵~防御：窗口外和玩家背包区域不能被视为机器输入喵~
        if (rawSlot < 0 || rawSlot >= topInventory.getSize()) {
            // 返回非输入槽结果喵~
            return false;
        }
        // 原版炉类仅0号槽是原料输入，不能阻止燃料或产物槽喵~
        if (isFurnaceLike(topInventory)) {
            // 返回是否为原料槽喵~
            return rawSlot == 0;
        }
        // 既有 SF 机器约定使用0号槽作为输入，保持原补丁行为喵~
        return rawSlot == 0;
    }

    // 发送统一的设备输入禁止提示喵~
    private void sendBlockedMessage(org.bukkit.entity.HumanEntity clicker) {
        // 仅玩家点击者需要接收聊天提示喵~
        if (clicker instanceof org.bukkit.entity.Player player) {
            // 告知玩家过期物或菜肴不能作为机器原料喵~
            player.sendMessage("§c菜肴或过期食材不能放入机器喵~");
        }
    }

    // 判断库存是否属于原版炉类或 Slimefun 机器菜单喵~
    private boolean isMachineInventory(Inventory inventory) {
        // 喵~防御：空库存无法判断类型，直接跳过喵~
        if (inventory == null) {
            // 返回非机器结果喵~
            return false;
        }
        // 读取 Bukkit 库存类型喵~
        org.bukkit.event.inventory.InventoryType type = inventory.getType();
        // 原版炉、烟熏炉和高炉直接属于机器喵~
        if (type == org.bukkit.event.inventory.InventoryType.FURNACE
                || type == org.bukkit.event.inventory.InventoryType.BLAST_FURNACE
                || type == org.bukkit.event.inventory.InventoryType.SMOKER) {
            // 返回机器结果喵~
            return true;
        }
        // 读取库存持有者以识别 Slimefun 菜单实现喵~
        org.bukkit.inventory.InventoryHolder holder = inventory.getHolder();
        // 没有持有者时不是本补丁可识别的 SF 机器喵~
        if (holder == null) {
            // 返回非机器结果喵~
            return false;
        }
        // 读取持有者实现类名称进行兼容识别喵~
        String holderClassName = holder.getClass().getName();
        // 返回与既有 BlockMenu/Menu/Preset 特征相符的结果喵~
        return holderClassName.contains("slimefun")
                && (holderClassName.contains("BlockMenu")
                || holderClassName.contains("Menu")
                || holderClassName.contains("Preset"));
    }

    // 判断库存是否为具有固定三槽布局的原版炉类喵~
    private boolean isFurnaceLike(Inventory inventory) {
        // 喵~防御：空库存不可能是炉类喵~
        if (inventory == null) {
            // 返回非炉类结果喵~
            return false;
        }
        // 读取 Bukkit 库存类型喵~
        org.bukkit.event.inventory.InventoryType type = inventory.getType();
        // 返回三种原版炉类的匹配结果喵~
        return type == org.bukkit.event.inventory.InventoryType.FURNACE
                || type == org.bukkit.event.inventory.InventoryType.BLAST_FURNACE
                || type == org.bukkit.event.inventory.InventoryType.SMOKER;
    }

    // 判断物品是否为自定义菜肴喵~
    private boolean isDish(ItemStack item) {
        // 喵~防御：空物品或空气没有菜肴 PDC 喵~
        if (item == null || item.getType().isAir()) {
            // 返回非菜肴结果喵~
            return false;
        }
        // 读取物品元数据喵~
        ItemMeta meta = item.getItemMeta();
        // 喵~防御：没有元数据的物品无法是本系统菜肴喵~
        if (meta == null) {
            // 返回非菜肴结果喵~
            return false;
        }
        // 读取物品 PDC 容器喵~
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        // 使用菜肴饥饿字段识别自定义菜肴喵~
        return pdc.has(CookingKeys.DISH_HUNGER, PersistentDataType.INTEGER);
    }

    // 判断物品是否为实时已过期且受本系统管理的烹饪物品喵~
    private boolean isExpiredCookingItem(ItemStack item) {
        // 获取全局统一保质期判定服务喵~
        FoodExpiryService foodExpiryService = CookingModule.getFoodExpiryService();
        // 喵~防御：模块启用早期服务未就绪时不误拦截物品喵~
        if (foodExpiryService == null) {
            // 返回未过期结果喵~
            return false;
        }
        // 使用 PDC 时间戳与配置实时判断，绝不依赖 lore 文本喵~
        return foodExpiryService.isExpired(item);
    }

    // 判断物品是否不能进入设备或营火：所有菜肴或实时已过期受管理物喵~
    private boolean isFurnaceBlocked(ItemStack item) {
        // 返回任一禁止条件的合并结果喵~
        return isDish(item) || isExpiredCookingItem(item);
    }
}
