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
    // 记录砧板上食材被刀切击的次数，用于 DICED→SAUCE 进度计数喵
    public static final NamespacedKey KNIFE_CLICKS    = new NamespacedKey(NS, "knife_clicks");
    // 记录食材/菜肴最后一次被标记时的时间戳（毫秒），用于计算保质期显示喵
    public static final NamespacedKey FOOD_TIMESTAMP  = new NamespacedKey(NS, "food_timestamp");
    // 菜肴保质期（分钟），AI返回值喵
    public static final NamespacedKey DISH_SHELF_LIFE = new NamespacedKey(NS, "dish_shelf_life");
    // 菜肴剩余可食用次数，食用一次扣1，归零时物品变碗喵
    public static final NamespacedKey DISH_SERVINGS_REMAINING = new NamespacedKey(NS, "dish_servings_remaining");

    private CookingKeys() {}
}
