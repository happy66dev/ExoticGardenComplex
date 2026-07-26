package io.github.thebusybiscuit.exoticgarden.cooking.block;

import io.github.thebusybiscuit.exoticgarden.cooking.hologram.StoveHologram;
import io.github.thebusybiscuit.exoticgarden.cooking.interaction.StoveInteractionHandler;
import io.github.thebusybiscuit.exoticgarden.cooking.state.IngredientSlot;
import io.github.thebusybiscuit.exoticgarden.cooking.state.StoveState;
import io.github.thebusybiscuit.slimefun4.api.events.PlayerRightClickEvent;
import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.core.attributes.HologramOwner;
import io.github.thebusybiscuit.slimefun4.core.handlers.BlockBreakHandler;
import io.github.thebusybiscuit.slimefun4.core.handlers.BlockUseHandler;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Campfire;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockCookEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class StoveBlock extends SlimefunItem implements HologramOwner {

    public final Map<Location, StoveState> activeStoves = new ConcurrentHashMap<>();

    private final List<StoveInteractionHandler> handlers;

    public StoveBlock(ItemGroup group, SlimefunItemStack item, RecipeType recipeType,
                      ItemStack[] recipe, List<StoveInteractionHandler> handlers,
                      JavaPlugin plugin) {
        super(group, item, recipeType, recipe);
        this.handlers = handlers;
        addItemHandler(buildUseHandler(), buildBreakHandler());
        plugin.getServer().getPluginManager().registerEvents(new Listener() {
            @EventHandler(ignoreCancelled = true)
            public void onBlockCook(BlockCookEvent e) {
                if (activeStoves.containsKey(e.getBlock().getLocation())) {
                    e.setCancelled(true);
                }
            }

            // 喵~兜底：营火上物品掉落时，如果该位置是EG灶台则取消掉落（防止服务器重启/插件卸载时物品泄漏）
            @EventHandler(ignoreCancelled = true)
            public void onBlockDropItem(org.bukkit.event.block.BlockDropItemEvent e) {
                if (e.getBlock().getType() != org.bukkit.Material.CAMPFIRE) return;
                if (!activeStoves.containsKey(e.getBlock().getLocation())) return;
                // 喵~取消篝火产生的物品掉落，防止食材因篝火机制掉落喵
                e.setCancelled(true);
            }

            @EventHandler(priority = EventPriority.LOW, ignoreCancelled = false)
            public void onPlayerInteract(PlayerInteractEvent e) {
                if (e.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) return;
                if (e.getClickedBlock() == null) return;
                if (e.getClickedBlock().getType() != org.bukkit.Material.CAMPFIRE) return;
                Location loc = e.getClickedBlock().getLocation();
                if (!activeStoves.containsKey(loc)) return;
                ItemStack hand = e.getPlayer().getInventory().getItemInMainHand();
                if (e.getAction() == org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) {
                    e.setCancelled(true);
                    StoveState state = activeStoves.get(loc);
                    // 喵~冻结状态处理：AI进行中不允许解冻，AI失败才允许右键解冻喵
                    if (state.frozen) {
                        if (state.frozenReason != null) {
                            // AI失败：使失败请求令牌失效后允许继续交互喵
                            state.invalidateAiRequest();
                            state.frozen = false;
                            e.getPlayer().sendMessage("§c[AI] 上次生成失败: " + state.frozenReason);
                            e.getPlayer().sendMessage("§a灶台已解冻，可以继续交互喵~");
                            state.frozenReason = null;
                            state.lastActiveAtMillis = System.currentTimeMillis();
                        } else {
                            // AI进行中：提示不可操作喵
                            e.getPlayer().sendMessage("§e[AI] 正在生成菜肴，请稍候...");
                        }
                        return;
                    }
                    // 碗/食材/牛奶桶/任何物品：统一走handler链路喵
                    for (StoveInteractionHandler handler : handlers) {
                        if (handler.handle(e.getPlayer(), hand, state, loc)) break;
                    }
                }
            }
        }, plugin);
    }

    private BlockUseHandler buildUseHandler() {
        return (PlayerRightClickEvent e) -> {
            e.cancel();
            if (e.getClickedBlock().isEmpty()) return;
            Player player = e.getPlayer();
            // 使用方块坐标副本作为稳定键，避免位置对象被外部修改导致缓存失配喵
            Location loc = e.getClickedBlock().get().getLocation().clone();
            ItemStack hand = player.getInventory().getItemInMainHand();
            // 仅在本次交互真正被处理后保存新状态，防止无效右键累积空灶台缓存喵
            StoveState state = activeStoves.get(loc);
            boolean createdForInteraction = state == null;
            if (createdForInteraction) state = new StoveState();
            // 喵~AI冻结期间BlockUseHandler也不处理（onPlayerInteract已拦截，这里兜底）
            if (state.frozen) return;

            boolean handled = false;
            for (StoveInteractionHandler handler : handlers) {
                if (handler.handle(player, hand, state, loc)) {
                    handled = true;
                    break;
                }
            }
            // 仅持久化成功处理的新状态，避免空手或无关物品右键泄漏 Location 引用喵
            if (handled && createdForInteraction) activeStoves.put(loc, state);
            // 成功处理时刷新空闲计时，单位：毫秒喵
            if (handled) state.lastActiveAtMillis = System.currentTimeMillis();
            // 喵~防御：只有既有状态才允许重置待清燃料标记喵
            if (!handled && !createdForInteraction && !player.isSneaking()) {
                state.pendingFuelClear = false;
            }
        };
    }

    private BlockBreakHandler buildBreakHandler() {
        return new BlockBreakHandler(false, false) {
            @Override
            public void onPlayerBreak(@Nonnull BlockBreakEvent e,
                                      @Nonnull ItemStack item,
                                      @Nonnull List<ItemStack> drops) {
                org.bukkit.block.Block b = e.getBlock();
                // 真实玩家破坏时清理状态、全息、AI请求和营火展示槽喵
                cleanupStove(b.getLocation(), true);
            }
        };
    }

    /**
     * 清理指定灶台的运行时状态、全息和可选的营火展示槽喵~
     * 输入：loc-灶台方块坐标，clearCampfireSlots-是否清空已加载区块内的展示槽
     * 输出：无
     * 边界：未加载区块绝不访问 BlockState，避免清理过程反向加载区块喵
     */
    public void cleanupStove(Location loc, boolean clearCampfireSlots) {
        // 喵~防御：位置或世界为空时无法访问方块，但仍尽量释放状态引用喵
        if (loc == null) return;
        // 从映射移除状态，使定时任务不再继续持有该灶台喵
        StoveState removedState = activeStoves.remove(loc);
        // 喵~防御：存在AI请求时先失效令牌并尝试取消后台future喵
        if (removedState != null) removedState.invalidateAiRequest();
        // 只要世界仍存在就清除所有单行和多行全息以及位置缓存喵
        if (loc.getWorld() != null) StoveHologram.removeAndClean(loc, this);
        // 喵~防御：无需清槽、世界不存在或区块未加载时禁止访问方块状态喵
        if (!clearCampfireSlots || loc.getWorld() == null
                || !loc.getWorld().isChunkLoaded(loc.getBlockX() >> 4, loc.getBlockZ() >> 4)) return;
        org.bukkit.block.Block block = loc.getBlock();
        // 喵~防御：仅清理营火展示槽，避免误修改已替换成的其他方块喵
        if (!(block.getState() instanceof Campfire campfire)) return;
        // 清空原版营火展示的四个槽位，物品真实状态已随灶台状态一并回收喵
        for (int index = 0; index < 4; index++) campfire.setItem(index, null);
        campfire.update(true, false);
    }

    /**
     * 仅清理灶台可视全息，不移除烹饪状态或营火槽，供区块/世界卸载使用喵~
     * 输入：loc-灶台方块坐标
     * 输出：无
     * 边界：卸载期间必须保留食材状态，避免重载后丢失或复制喵
     */
    public void cleanupHologram(Location loc) {
        // 喵~防御：世界为空时无法调用Slimefun全息服务喵
        if (loc == null || loc.getWorld() == null) return;
        StoveHologram.removeAndClean(loc, this);
    }

    private static ItemStack reconstructItem(String id) {
        // 喵~SF物品先查（去掉命名空间前缀）喵
        String sfId = id.startsWith("slimefun:") ? id.substring("slimefun:".length()) : id;
        SlimefunItem sfItem = SlimefunItem.getById(sfId);
        if (sfItem != null) return sfItem.getItem().clone();
        // 喵~原版物品：去掉 minecraft: 前缀后查 Material喵
        String matName = id.contains(":") ? id.substring(id.indexOf(':') + 1) : id;
        Material mat = Material.getMaterial(matName);
        if (mat != null && mat != Material.AIR) return new ItemStack(mat, 1);
        return null;
    }

    public static void syncCampfireSlots(Location loc, StoveState state) {
        if (loc.getWorld() == null) return;
        org.bukkit.block.Block b = loc.getBlock();
        if (!(b.getState() instanceof Campfire campfire)) return;
        for (int i = 0; i < 4; i++) {
            IngredientSlot slot = i < state.slots.length ? state.slots[i] : null;
            if (slot == null) {
                campfire.setItem(i, null);
            } else {
                ItemStack display = reconstructItem(slot.ingredientId);
                campfire.setItem(i, display);
            }
        }
        campfire.update(true, false);
    }
}
