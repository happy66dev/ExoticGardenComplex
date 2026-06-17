package io.github.thebusybiscuit.exoticgarden.cooking.block;

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
                            // AI失败：允许解冻喵
                            state.frozen = false;
                            e.getPlayer().sendMessage("§c[AI] 上次生成失败: " + state.frozenReason);
                            e.getPlayer().sendMessage("§a灶台已解冻，可以继续交互喵~");
                            state.frozenReason = null;
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
            Location loc = e.getClickedBlock().get().getLocation().clone();
            ItemStack hand = player.getInventory().getItemInMainHand();
            StoveState state = activeStoves.computeIfAbsent(loc, k -> new StoveState());
            // 喵~AI冻结期间BlockUseHandler也不处理（onPlayerInteract已拦截，这里兜底）
            if (state.frozen) return;

            boolean handled = false;
            for (StoveInteractionHandler handler : handlers) {
                if (handler.handle(player, hand, state, loc)) {
                    handled = true;
                    break;
                }
            }
            if (!handled && !player.isSneaking()) {
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
                Location loc = b.getLocation();
                StoveState state = activeStoves.remove(loc);
                // 喵~清除多行全息（removeAndClean 会扫描所有可能的全息位置并移除），
                // 同时也调用 HologramOwner.removeHologram 清除单行全息兜底喵
                io.github.thebusybiscuit.exoticgarden.cooking.hologram.StoveHologram.removeAndClean(loc, StoveBlock.this);
                if (state == null) return;
                if (b.getState() instanceof Campfire campfire) {
                    for (int i = 0; i < 4; i++) campfire.setItem(i, null);
                    campfire.update(true, false);
                }
            }
        };
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
