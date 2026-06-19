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
     * 判断物品是否已过期：读取PDC的FOOD_TIMESTAMP和INGREDIENT_ID，查询保质期喵~
     * 无时间戳视为未过期（未经烹饪系统标记的物品）喵
     * 保质期优先级：ingredients.yml 单独配置 > foods.yml overrides > foods.yml 通用默认值
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

        String ingId = pdc.get(
            io.github.thebusybiscuit.exoticgarden.cooking.CookingKeys.INGREDIENT_ID,
            PersistentDataType.STRING);

        int shelfLife = 10; // 兜底默认值喵

        // 1. 先查 ingredients.yml 单独配置喵
        if (ingId != null) {
            java.util.Map<String, io.github.thebusybiscuit.exoticgarden.cooking.config.IngredientConfig.IngredientData> ingMap
                = io.github.thebusybiscuit.exoticgarden.cooking.CookingModule.getIngredients();
            if (ingMap != null) {
                io.github.thebusybiscuit.exoticgarden.cooking.config.IngredientConfig.IngredientData data
                    = ingMap.get(ingId);
                if (data != null) {
                    shelfLife = data.shelfLifeMinutes;
                    long diffMinutes = (System.currentTimeMillis() - timestamp) / 60000L;
                    return diffMinutes >= shelfLife;
                }
            }
        }

        // 2. ingredients.yml 无配置时查 foods.yml overrides 喵
        io.github.thebusybiscuit.exoticgarden.cooking.config.FoodsConfig foodsCfg
            = io.github.thebusybiscuit.exoticgarden.cooking.CookingModule.getFoodsConfig();
        if (foodsCfg != null) {
            // 用 ItemIdUtil 生成带命名空间 key 查 overrides 喵
            String itemKey = io.github.thebusybiscuit.exoticgarden.cooking.util.ItemIdUtil.toKey(item);
            java.util.Optional<Integer> override = foodsCfg.getOverrideShelfLife(itemKey != null ? itemKey : "");
            if (override.isPresent()) {
                shelfLife = override.get();
            } else {
                // 3. 回退到 foods.yml 通用默认值喵
                shelfLife = foodsCfg.getGenericFoodShelfLife();
            }
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
