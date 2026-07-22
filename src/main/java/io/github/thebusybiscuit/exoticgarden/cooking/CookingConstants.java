package io.github.thebusybiscuit.exoticgarden.cooking;

/**
 * 烹饪系统全局常量喵~
 */
public final class CookingConstants {

    // 基础环境温度（室温），单位°C，散热/升温的平衡基准喵
    public static final double BASE_AMBIENT_TEMP = 30.0;

    // 灶台最低温度限制，低于此值不再降温喵
    public static final double MIN_TEMP = -50.0;

    // 温度系数上限：最高25倍成熟速度喵
    public static final double MAX_TEMP_COEFFICIENT = 25.0;

    // 工具类不允许实例化喵
    private CookingConstants() {}
}
