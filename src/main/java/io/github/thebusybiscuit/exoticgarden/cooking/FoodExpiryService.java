package io.github.thebusybiscuit.exoticgarden.cooking;

// 导入食材配置模型喵~
import io.github.thebusybiscuit.exoticgarden.cooking.config.IngredientConfig;
// 导入食品通用配置模型喵~
import io.github.thebusybiscuit.exoticgarden.cooking.config.FoodsConfig;
// 导入调料配置模型喵~
import io.github.thebusybiscuit.exoticgarden.cooking.config.SeasoningConfig;
// 导入物品键生成工具喵~
import io.github.thebusybiscuit.exoticgarden.cooking.util.ItemIdUtil;
// 导入 Bukkit 物品类型喵~
import org.bukkit.inventory.ItemStack;
// 导入物品元数据类型喵~
import org.bukkit.inventory.meta.ItemMeta;
// 导入 PDC 容器类型喵~
import org.bukkit.persistence.PersistentDataContainer;
// 导入 PDC 数据类型喵~
import org.bukkit.persistence.PersistentDataType;

// 导入映射类型以保存加载后的只读配置喵~
import java.util.Map;
// 导入可选结果类型以明确无有效保质期的情况喵~
import java.util.Optional;

/**
 * 统一解析烹饪物品保质期并按 PDC 时间戳实时判定过期状态喵~
 *
 * 整体思路：所有食用、机器、营火和种植路径均通过本服务读取同一份配置喵~
 * 输入：带有 CookingKeys 时间戳与类别 PDC 的物品喵~
 * 输出：有效的生产时间、保质期分钟数与实时过期结果；未知或无期限物品返回空喵~
 * 边界：系统时钟回拨时把物品视为未过期，避免错误惩罚玩家喵~
 */
public final class FoodExpiryService {

    // 保存通用原版食物在 PDC 中使用的标识符喵~
    public static final String GENERIC_FOOD_ID = "_GENERIC_FOOD_";
    // 保存通用药水在 PDC 中使用的标识符喵~
    public static final String GENERIC_POTION_ID = "_GENERIC_POTION_";
    // 保存一分钟对应的毫秒数，避免各调用点重复计算并产生单位错误喵~
    private static final long MILLIS_PER_MINUTE = 60_000L;

    // 保存食材配置映射以解析普通食材和可选通用覆盖喵~
    private final Map<String, IngredientConfig.IngredientData> ingredients;
    // 保存调料配置映射以解析调料保质期喵~
    private final Map<String, SeasoningConfig.SeasoningData> seasonings;
    // 保存食品通用配置以解析原版食物覆盖和默认期限喵~
    private final FoodsConfig foodsConfig;

    // 创建统一保质期服务喵~
    public FoodExpiryService(Map<String, IngredientConfig.IngredientData> ingredients,
                             Map<String, SeasoningConfig.SeasoningData> seasonings,
                             FoodsConfig foodsConfig) {
        // 喵~防御：食材配置不能为空，避免已标记食材被错误判为未知喵~
        if (ingredients == null) {
            // 拒绝无食材配置的服务初始化喵~
            throw new IllegalArgumentException("食材配置不能为空喵~");
        }
        // 喵~防御：调料配置不能为空，避免调料期限无法正确解析喵~
        if (seasonings == null) {
            // 拒绝无调料配置的服务初始化喵~
            throw new IllegalArgumentException("调料配置不能为空喵~");
        }
        // 喵~防御：食品配置不能为空，避免通用食物与药水回退到错误期限喵~
        if (foodsConfig == null) {
            // 拒绝无食品配置的服务初始化喵~
            throw new IllegalArgumentException("食品配置不能为空喵~");
        }
        // 保存加载后的食材配置引用喵~
        this.ingredients = ingredients;
        // 保存加载后的调料配置引用喵~
        this.seasonings = seasonings;
        // 保存加载后的食品通用配置引用喵~
        this.foodsConfig = foodsConfig;
    }

    // 返回物品的有效保质期信息，无有效期限时返回空结果喵~
    public Optional<ExpiryInfo> getExpiryInfo(ItemStack item) {
        // 喵~防御：空物品或空气没有 PDC 信息，直接跳过喵~
        if (item == null || item.getType().isAir()) {
            // 返回空结果表示不受保质期系统管理喵~
            return Optional.empty();
        }
        // 读取物品元数据以访问 PDC 喵~
        ItemMeta meta = item.getItemMeta();
        // 喵~防御：没有元数据的物品无法带有烹饪 PDC，直接跳过喵~
        if (meta == null) {
            // 返回空结果避免空指针异常喵~
            return Optional.empty();
        }
        // 读取物品持久化数据容器喵~
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        // 读取不可变生产时间戳喵~
        Long timestampMillis = pdc.get(CookingKeys.FOOD_TIMESTAMP, PersistentDataType.LONG);
        // 喵~防御：没有生产时间戳的物品不参与过期逻辑喵~
        if (timestampMillis == null) {
            // 返回空结果让普通未标记物品保持原版行为喵~
            return Optional.empty();
        }
        // 解析与当前物品类别一致的有效保质期分钟数喵~
        Optional<Integer> shelfLifeMinutes = resolveShelfLifeMinutes(item, pdc);
        // 没有正保质期的调料等物品不受过期限制喵~
        if (shelfLifeMinutes.isEmpty()) {
            // 返回空结果避免把无保质期物品误判为立即过期喵~
            return Optional.empty();
        }
        // 返回供调用方统一复用的时间戳与期限快照喵~
        return Optional.of(new ExpiryInfo(timestampMillis, shelfLifeMinutes.get()));
    }

    // 实时判断物品是否已过期，未知或无期限物品视为未过期喵~
    public boolean isExpired(ItemStack item) {
        // 使用当前系统时间执行实时判断喵~
        return isExpired(item, System.currentTimeMillis());
    }

    // 按指定时间判断物品是否已过期，便于边界测试和同一事件内复用时钟喵~
    public boolean isExpired(ItemStack item, long nowMillis) {
        // 读取物品有效的保质期信息喵~
        Optional<ExpiryInfo> expiryInfo = getExpiryInfo(item);
        // 没有有效期限的物品不应被禁止食用、烹饪或种植喵~
        if (expiryInfo.isEmpty()) {
            // 返回未过期结果喵~
            return false;
        }
        // 使用统一快照计算是否达到过期边界喵~
        return expiryInfo.get().isExpiredAt(nowMillis);
    }

    // 解析物品应使用的保质期分钟数，未知类别返回空结果喵~
    private Optional<Integer> resolveShelfLifeMinutes(ItemStack item, PersistentDataContainer pdc) {
        // 菜肴拥有独立的成品保质期字段，优先级最高喵~
        if (pdc.has(CookingKeys.DISH_HUNGER, PersistentDataType.INTEGER)) {
            // 读取菜肴生成时固定下来的保质期喵~
            Integer dishShelfLifeMinutes = pdc.get(CookingKeys.DISH_SHELF_LIFE, PersistentDataType.INTEGER);
            // 喵~防御：零或负保质期表示该类别不受过期限制，避免无期限物品被误判为立即过期喵~
            if (dishShelfLifeMinutes == null || dishShelfLifeMinutes <= 0) {
                // 返回空结果表示不执行菜肴过期限制喵~
                return Optional.empty();
            }
            // 返回菜肴生成时固定下来的有效期限喵~
            return Optional.of(dishShelfLifeMinutes);
        }
        // 读取调料标识符以按调料配置解析期限喵~
        String seasoningId = pdc.get(CookingKeys.SEASONING_ID, PersistentDataType.STRING);
        // 有调料标识时只按调料配置处理，避免旧食材 PDC 干扰喵~
        if (seasoningId != null) {
            // 查询对应调料配置喵~
            SeasoningConfig.SeasoningData seasoningData = seasonings.get(seasoningId);
            // 喵~防御：未知调料或非正期限调料不受过期限制喵~
            if (seasoningData == null || seasoningData.shelfLifeMinutes <= 0) {
                // 返回空结果保留无保质期调料的既有行为喵~
                return Optional.empty();
            }
            // 返回调料配置中的有效期限喵~
            return Optional.of(seasoningData.shelfLifeMinutes);
        }
        // 读取食材标识符以解析普通和通用食物期限喵~
        String ingredientId = pdc.get(CookingKeys.INGREDIENT_ID, PersistentDataType.STRING);
        // 喵~防御：没有食材标识的时间戳物品可能属于其他插件，绝不误拦截喵~
        if (ingredientId == null) {
            // 返回空结果表示未知类别喵~
            return Optional.empty();
        }
        // 查询食材配置，普通食材和可选通用覆盖均优先使用它喵~
        IngredientConfig.IngredientData ingredientData = ingredients.get(ingredientId);
        // 通用食物需优先应用 foods.yml 的逐物品覆盖喵~
        if (GENERIC_FOOD_ID.equals(ingredientId)) {
            // 根据当前物品生成与 foods.yml 一致的命名空间键喵~
            String itemKey = ItemIdUtil.toKey(item);
            // 查询该具体物品是否有覆盖期限喵~
            Optional<Integer> overrideShelfLife = foodsConfig.getOverrideShelfLife(itemKey);
            // 有覆盖时必须优先返回覆盖值喵~
            if (overrideShelfLife.isPresent()) {
                // 返回逐物品配置期限喵~
                return overrideShelfLife;
            }
            // 可选通用食材配置存在时可覆盖默认值喵~
            if (ingredientData != null && ingredientData.shelfLifeMinutes >= 0) {
                // 返回显式通用食物覆盖期限喵~
                return Optional.of(ingredientData.shelfLifeMinutes);
            }
            // 返回 foods.yml 中的通用食物默认期限喵~
            return foodsConfig.getGenericFoodShelfLife() >= 0
                    ? Optional.of(foodsConfig.getGenericFoodShelfLife())
                    : Optional.empty();
        }
        // 通用药水使用独立默认期限，不能误用通用食物设置喵~
        if (GENERIC_POTION_ID.equals(ingredientId)) {
            // 可选通用药水食材配置存在时优先使用喵~
            if (ingredientData != null && ingredientData.shelfLifeMinutes >= 0) {
                // 返回显式通用药水覆盖期限喵~
                return Optional.of(ingredientData.shelfLifeMinutes);
            }
            // 返回 foods.yml 中的通用药水默认期限喵~
            return foodsConfig.getGenericPotionShelfLife() >= 0
                    ? Optional.of(foodsConfig.getGenericPotionShelfLife())
                    : Optional.empty();
        }
        // 普通食材必须存在于配置中才属于本系统管理范围喵~
        if (ingredientData == null || ingredientData.shelfLifeMinutes < 0) {
            // 返回空结果避免未知 ID 被默认十分钟错误处罚喵~
            return Optional.empty();
        }
        // 返回普通食材配置期限喵~
        return Optional.of(ingredientData.shelfLifeMinutes);
    }

    // 保存一次解析得到的生产时间和保质期，确保多个调用点使用同一单位喵~
    public record ExpiryInfo(long timestampMillis, int shelfLifeMinutes) {

        // 按指定当前时间判断是否达到过期边界喵~
        public boolean isExpiredAt(long nowMillis) {
            // 计算从生产时间到当前时间的毫秒差喵~
            long elapsedMillis = nowMillis - timestampMillis;
            // 喵~防御：系统时钟回拨时不把未来生产时间误判为过期喵~
            if (elapsedMillis < 0L) {
                // 返回未过期结果喵~
                return false;
            }
            // 主人注意：此处乘法使用 long，避免大保质期配置在 int 范围内溢出喵~
            long shelfLifeMillis = (long) shelfLifeMinutes * MILLIS_PER_MINUTE;
            // 到达或超过期限边界即视为过期，零分钟配置会立即过期喵~
            return elapsedMillis >= shelfLifeMillis;
        }

        // 返回保质期进度，供菜肴营养衰减公式复用喵~
        public double calculateProgressAt(long nowMillis) {
            // 计算从生产时间到当前时间的毫秒差喵~
            long elapsedMillis = nowMillis - timestampMillis;
            // 喵~防御：时钟回拨或零分钟期限不能进行除零运算喵~
            if (elapsedMillis <= 0L || shelfLifeMinutes <= 0) {
                // 零分钟菜肴由 isExpiredAt 判定立即过期，进度保持零避免除零异常喵~
                return 0.0D;
            }
            // 返回以真实毫秒为基础的连续保质期进度喵~
            return (double) elapsedMillis / ((long) shelfLifeMinutes * MILLIS_PER_MINUTE);
        }
    }
}
