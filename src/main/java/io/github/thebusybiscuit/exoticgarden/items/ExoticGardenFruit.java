package io.github.thebusybiscuit.exoticgarden.items;

import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.core.handlers.ItemUseHandler;
import io.github.thebusybiscuit.slimefun4.implementation.items.SimpleSlimefunItem;
import io.github.thebusybiscuit.slimefun4.libraries.dough.items.ItemUtils;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;
import java.util.Optional;

public class ExoticGardenFruit extends SimpleSlimefunItem<ItemUseHandler> {

    private final boolean edible;

    @ParametersAreNonnullByDefault
    public ExoticGardenFruit(ItemGroup itemGroup, SlimefunItemStack item, RecipeType recipeType, boolean edible, ItemStack[] recipe) {
        super(itemGroup, item, recipeType, recipe);
        this.edible = edible;
    }

    @ParametersAreNonnullByDefault
    public ExoticGardenFruit(ItemGroup itemGroup, SlimefunItemStack item, RecipeType recipeType, boolean edible, ItemStack[] recipe, ItemStack recipeOutput) {
        super(itemGroup, item, recipeType, recipe, recipeOutput);
        this.edible = edible;
    }

    @Override
    public boolean useVanillaBlockBreaking() {
        return true;
    }

    @Override
    public ItemUseHandler getItemHandler() {
        return e -> {
            Optional<Block> block = e.getClickedBlock();

            if (block.isPresent()) {
                Material material = block.get().getType();

                // Cancel the Block placement if the Player sneaks or the Block is not interactable
                if (e.getPlayer().isSneaking() || !isInteractable(material)) {
                    e.cancel();
                } else {
                    return;
                }
            }

            if (edible && e.getPlayer().getFoodLevel() < 20) {
                // 喵~检查物品是否已过期喵
                if (isExpired(e.getItem())) {
                    Player p = e.getPlayer();
                    // 喵~过期：饱食度100%减少60%，30%概率额外触发debuff喵
                    int reduced = (int) Math.max(0, Math.round(getFoodValue() * 0.4));
                    p.setFoodLevel(Math.min(p.getFoodLevel() + reduced, 20));
                    p.sendMessage("§c这食物已经过期了，味道怪怪的喵~");
                    if (java.util.concurrent.ThreadLocalRandom.current().nextInt(100) < 30) {
                        io.github.thebusybiscuit.exoticgarden.cooking.DishConsumptionListener.applyExpiredEffectsStatic(p, false);
                    }
                    ItemUtils.consumeItem(e.getItem(), false);
                } else {
                    restoreHunger(e.getPlayer());
                    ItemUtils.consumeItem(e.getItem(), false);
                }
            }
        };
    }

    private boolean isInteractable(@Nonnull Material material) {
        // We cannot rely on Material#isInteractable() sadly
        // as it would allow the placement of this block on strange items like stairs...
        return switch (material) {
            case CRAFTING_TABLE,
                 FURNACE,
                 SMOKER,
                 BLAST_FURNACE,
                 JIGSAW,
                 ENCHANTING_TABLE,
                 ITEM_FRAME,
                 LOOM,
                 CARTOGRAPHY_TABLE,
                 GRINDSTONE,
                 SMITHING_TABLE,
                 BELL,
                 BEACON,
                 ANVIL,
                 BREWING_STAND,
                 CAKE,
                 CHEST,
                 TRAPPED_CHEST,
                 HOPPER,
                 ENDER_CHEST,
                 BARREL -> true;
            default -> material.name().equals("BARREL") ||
                    material.name().endsWith("_SHULKER_BOX") ||
                    material.name().endsWith("DOOR") ||
                    material.name().endsWith("BED");
        };
    }

    protected int getFoodValue() {
        return 2;
    }

    /**
     * 判断物品是否已过期：统一委托烹饪模块的 PDC 与配置判定服务喵~
     * 无时间戳、无有效期限或模块尚未初始化时安全视为未过期喵~
     */
    protected boolean isExpired(ItemStack item) {
        // 获取全局唯一的实时过期判定服务喵~
        io.github.thebusybiscuit.exoticgarden.cooking.FoodExpiryService foodExpiryService =
            io.github.thebusybiscuit.exoticgarden.cooking.CookingModule.getFoodExpiryService();
        // 喵~防御：烹饪模块未就绪时不对果实施加错误处罚喵~
        if (foodExpiryService == null) {
            // 返回未过期结果喵~
            return false;
        }
        // 按 PDC 生产时间和统一配置实时判定果实状态喵~
        return foodExpiryService.isExpired(item);
    }

    private void restoreHunger(@Nonnull Player p) {
        int level = p.getFoodLevel() + getFoodValue();
        p.playSound(p.getEyeLocation(), Sound.ENTITY_GENERIC_EAT, 1, 1);
        p.setFoodLevel(Math.min(level, 20));
        p.setSaturation(p.getSaturation() + getFoodValue());
    }

}
