package io.github.thebusybiscuit.exoticgarden.cooking.config;

import org.bukkit.configuration.ConfigurationSection;

import java.util.List;
import java.util.logging.Logger;

public class IngredientConfig extends YamlConfigLoader<IngredientConfig.IngredientData> {

    public static class SauceCreation {
        public final int clicksRequired;

        public SauceCreation(int clicksRequired) {
            this.clicksRequired = clicksRequired;
        }
    }

    public static class IngredientData {
        // 成熟参考温度：在此温度时系数=1（成熟速度标准速度）喵
        public final double matureRefTemp;
        public final double baseCookTimeSeconds;
        public final boolean flipRequired;
        public final List<String> states;
        public final SauceCreation sauceCreation;
        public final String calculatorType;
        public final String displayName;
        public final double weightGrams;
        // 饱食度恢复量（hunger points），未配置时为2喵
        public final double foodPoints;
        // 饱和度，未配置时为2喵
        public final double saturation;
        // 保质期，单位：分钟，未配置时默认10分钟喵
        public final int shelfLifeMinutes;
        // 烹饪时向灶台释放的水量（ml），0-50%熟度阶段缓慢释放喵
        public final double waterMl;
        // 烹饪时向灶台释放的油量（ml），0-50%熟度阶段缓慢释放喵
        public final double oilMl;
        // 给 AI 的提示信息，仅传给 AI，不写入物品 lore 喵
        public final String hint;
        // 物品 lore 显示用的提示词，与 AI 无关喵
        public final String loreHint;
        // 加工完成后变为的新 SF 物品 ID，null 表示不转换喵
        public final String transformTo;
        // 是否不分正反（水煮时双面同时加热，如面条类），优先于灶台水量检测喵
        public final boolean noFlipSide;

        public IngredientData(double matureRefTemp, double baseCookTimeSeconds,
                              boolean flipRequired, List<String> states,
                              SauceCreation sauceCreation, String calculatorType,
                              String displayName, double weightGrams,
                              double foodPoints, double saturation, int shelfLifeMinutes,
                              double waterMl, double oilMl, String hint, String loreHint,
                              String transformTo, boolean noFlipSide) {
            this.matureRefTemp = matureRefTemp;
            this.baseCookTimeSeconds = baseCookTimeSeconds;
            this.flipRequired = flipRequired;
            this.states = states;
            this.sauceCreation = sauceCreation;
            this.calculatorType = calculatorType;
            this.displayName = displayName;
            this.weightGrams = weightGrams;
            this.foodPoints = foodPoints;
            this.saturation = saturation;
            this.shelfLifeMinutes = shelfLifeMinutes;
            this.waterMl = waterMl;
            this.oilMl = oilMl;
            this.hint = hint;
            this.loreHint = loreHint;
            this.transformTo = transformTo;
            this.noFlipSide = noFlipSide;
        }
    }

    public IngredientConfig(Logger logger) {
        super(logger);
    }

    @Override
    protected IngredientData parseEntry(String key, ConfigurationSection s) {
        SauceCreation sauce = null;
        if (s.contains("sauce_creation")) {
            sauce = new SauceCreation(s.getInt("sauce_creation.clicks_required", 3));
        }
        return new IngredientData(
            s.getDouble("mature_ref_temp", 150),
            s.getDouble("base_cook_time_seconds"),
            s.getBoolean("flip_required", false),
            s.getStringList("states"),
            sauce,
            s.getString("calculator_type", "standard"),
            s.getString("display_name", key),
            s.getDouble("weight_grams", 100),
            s.getDouble("food_points", 2),
            s.getDouble("saturation", 2),
            s.getInt("shelf_life_minutes", 10),
            s.getDouble("water_ml", 0),
            s.getDouble("oil_ml", 0),
            s.getString("hint", ""),
            s.getString("lore_hint", ""),
            s.getString("transform_to", null),
            s.getBoolean("no_flip_side", false)  // 不分正反（如面条类）喵
        );
    }
}
