package io.github.thebusybiscuit.exoticgarden;

import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class EGPlant extends HandledBlock {
    private static final int food = 2;
    final boolean edible;

    public EGPlant(ItemGroup category, ItemStack item, String name, RecipeType recipeType, boolean edible, ItemStack[] recipe) {
        super(category, item, name, recipeType, recipe);
        this.edible = edible;
    }

    public static SlimefunItem getByName(String name) {
        return SlimefunItem.getById(name);
    }

    public boolean isEdible() {
        return this.edible;
    }

    // 喵~返回食物的饱食度恢复值，用于过期时计算减少60%喵
    public int getEdibleHunger() {
        return food;
    }

    public void restoreHunger(Player p) {
        int level = p.getFoodLevel() + 2;
        p.setFoodLevel(Math.min(level, 20));
        p.setSaturation(2.0F);
    }
}
