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
                    // 过期：饱食度恢复减少60%喵
                    int reduced = (int) Math.max(0, Math.round(getFoodValue() * 0.4));
                    p.setFoodLevel(Math.min(p.getFoodLevel() + reduced, 20));
                    p.sendMessage("§c这食物已经过期了，味道怪怪的喵~");
                    io.github.thebusybiscuit.exoticgarden.cooking.DishConsumptionListener.applyExpiredEffectsStatic(p, false);
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
     * 判断物品是否已过期：读取PDC的FOOD_TIMESTAMP和INGREDIENT_ID，查询保质期喵~
     * 无时间戳视为未过期（未经烹饪系统标记的物品）喵
     */
    protected boolean isExpired(ItemStack item) {
        // 喵~防御：null或无meta视为未过期喵
        if (item == null || !item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        // 无时间戳的物品不参与过期机制喵
        Long timestamp = pdc.get(
            io.github.thebusybiscuit.exoticgarden.cooking.CookingKeys.FOOD_TIMESTAMP,
            PersistentDataType.LONG);
        if (timestamp == null) return false;
        // 读取配置保质期，无配置时默认10分钟喵
        String ingId = pdc.get(
            io.github.thebusybiscuit.exoticgarden.cooking.CookingKeys.INGREDIENT_ID,
            PersistentDataType.STRING);
        int shelfLife = 10;
        if (ingId != null) {
            io.github.thebusybiscuit.exoticgarden.cooking.config.IngredientConfig.IngredientData data
                = io.github.thebusybiscuit.exoticgarden.cooking.CookingModule.getIngredients() != null
                ? io.github.thebusybiscuit.exoticgarden.cooking.CookingModule.getIngredients().get(ingId)
                : null;
            if (data != null) shelfLife = data.shelfLifeMinutes;
        }
        long diffMinutes = (System.currentTimeMillis() - timestamp) / 60000L;
        // 喵~防御：时钟回拨（diffMinutes为负）视为未过期喵
        return diffMinutes >= shelfLife;
    }

    private void restoreHunger(@Nonnull Player p) {
        int level = p.getFoodLevel() + getFoodValue();
        p.playSound(p.getEyeLocation(), Sound.ENTITY_GENERIC_EAT, 1, 1);
        p.setFoodLevel(Math.min(level, 20));
        p.setSaturation(p.getSaturation() + getFoodValue());
    }

}
