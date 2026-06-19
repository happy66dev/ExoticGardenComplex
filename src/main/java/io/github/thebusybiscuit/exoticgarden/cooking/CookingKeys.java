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
    // 食材配置中的 hint 提示词（可变，每次刷新覆写）喵
    public static final NamespacedKey INGREDIENT_HINT = new NamespacedKey(NS, "ingredient_hint");
    // 燃料配置中的 hint 提示词（可变，每次刷新覆写）喵
    public static final NamespacedKey FUEL_HINT = new NamespacedKey(NS, "fuel_hint");
    // 菜肴食用句子列表，多句以 | 分隔，按顺序每口发一句喵
    public static final NamespacedKey DISH_FLAVOR_TEXTS = new NamespacedKey(NS, "dish_flavor_texts");
    // 下一次食用时应展示的 flavorTexts 索引（0起），每食用一次 +1 喵
    public static final NamespacedKey DISH_FLAVOR_INDEX = new NamespacedKey(NS, "dish_flavor_index");

    private CookingKeys() {}
}
