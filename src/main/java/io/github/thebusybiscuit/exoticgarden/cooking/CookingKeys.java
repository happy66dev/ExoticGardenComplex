package io.github.thebusybiscuit.exoticgarden.cooking;

import org.bukkit.NamespacedKey;

public final class CookingKeys {

    private static final String NS = "cooking";

    public static final NamespacedKey DISH_NAME       = new NamespacedKey(NS, "dish_name");
    public static final NamespacedKey DISH_HUNGER     = new NamespacedKey(NS, "dish_hunger");
    public static final NamespacedKey DISH_SATURATION = new NamespacedKey(NS, "dish_saturation");
    public static final NamespacedKey DISH_QUALITY    = new NamespacedKey(NS, "dish_quality");
    public static final NamespacedKey DISH_DESCRIPTION= new NamespacedKey(NS, "dish_description");
    public static final NamespacedKey DISH_EFFECTS    = new NamespacedKey(NS, "dish_effects");

    public static final NamespacedKey FUEL_ID         = new NamespacedKey(NS, "fuel_id");
    public static final NamespacedKey INGREDIENT_ID   = new NamespacedKey(NS, "ingredient_id");
    public static final NamespacedKey SEASONING_ID    = new NamespacedKey(NS, "seasoning_id");

    public static final NamespacedKey FOOD_STATE      = new NamespacedKey(NS, "food_state");
    public static final NamespacedKey ITEM_TYPE       = new NamespacedKey(NS, "item_type");
    public static final NamespacedKey SPATULA_CLICKS  = new NamespacedKey(NS, "spatula_clicks");
    public static final NamespacedKey BOARD_ITEM      = new NamespacedKey(NS, "board_item");

    private CookingKeys() {}
}
