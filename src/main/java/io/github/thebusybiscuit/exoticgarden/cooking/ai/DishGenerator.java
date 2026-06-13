package io.github.thebusybiscuit.exoticgarden.cooking.ai;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.List;

public class DishGenerator {

    public static class IngredientInfo {
        public final String name;
        public final String state;
        public final double doneness;
        public final String charLevel;
        public final double weight;

        public IngredientInfo(String name, String state, double doneness,
                              String charLevel, double weight) {
            this.name = name;
            this.state = state;
            this.doneness = doneness;
            this.charLevel = charLevel;
            this.weight = weight;
        }
    }

    public static class SeasoningInfo {
        public final String name;
        public final Double progress;

        public SeasoningInfo(String name, Double progress) {
            this.name = name;
            this.progress = progress;
        }
    }

    public static class DishResult {
        public final String name;
        public final int hunger;
        public final double saturation;
        public final String quality;
        public final List<String> effects;
        public final String description;

        public DishResult(String name, int hunger, double saturation, String quality,
                          List<String> effects, String description) {
            this.name = name;
            this.hunger = hunger;
            this.saturation = saturation;
            this.quality = quality;
            this.effects = effects;
            this.description = description;
        }
    }

    public static String[] buildPrompt(
            List<IngredientInfo> ingredientInfos,
            List<SeasoningInfo> seasoningInfos,
            List<String> fuelEffects,
            double waterMl, double oilMl,
            int totalHunger, double totalWeight) {
        Gson gson = new Gson();
        JsonObject userContent = new JsonObject();

        JsonArray ingArr = new JsonArray();
        for (IngredientInfo info : ingredientInfos) {
            JsonObject obj = new JsonObject();
            obj.addProperty("name", info.name);
            obj.addProperty("state", info.state);
            obj.addProperty("doneness", info.doneness);
            obj.addProperty("charLevel", info.charLevel);
            obj.addProperty("weight", info.weight);
            ingArr.add(obj);
        }
        userContent.add("ingredients", ingArr);

        JsonArray seaArr = new JsonArray();
        for (SeasoningInfo si : seasoningInfos) {
            JsonObject obj = new JsonObject();
            obj.addProperty("name", si.name);
            if (si.progress != null) {
                obj.addProperty("progress", si.progress);
            } else {
                obj.add("progress", com.google.gson.JsonNull.INSTANCE);
            }
            seaArr.add(obj);
        }
        userContent.add("seasonings", seaArr);

        JsonArray fxArr = new JsonArray();
        for (String fx : fuelEffects) fxArr.add(fx);
        userContent.add("fuelEffects", fxArr);

        userContent.addProperty("waterMl", waterMl);
        userContent.addProperty("oilMl", oilMl);
        userContent.addProperty("totalHunger", totalHunger);
        userContent.addProperty("totalWeight", totalWeight);
        userContent.addProperty("language", "zh-CN");

        String systemPrompt = "你是一个 Minecraft 烹饪游戏的菜肴生成器。\n\n"
            + "输入 JSON 字段说明:\n"
            + "- ingredients: [{name:\"食材名\",state:\"完整/切片/切丁/酱汁\",doneness:熟度(可超100),charLevel:\"NONE/LIGHT/MEDIUM/HEAVY/SEVERE\",weight:\"克\"}]\n"
            + "- seasonings: [{name:\"调料名\",progress:渗入度0-1}]\n"
            + "- fuelEffects: [\"燃料风味\"]\n"
            + "- waterMl: 水量(毫升), oilMl: 油量(毫升)\n"
            + "- totalHunger: 食材饱食度之和, totalWeight: 食材总克重\n\n"
            + "烹饪方向推断(根据水量油量):\n"
            + "- 无水无油->烧烤/干烧(食材适合烤则品质不差,否则干烧品质差)\n"
            + "- 有水无油->炖/煮/蒸  有油无水->煎/炒/炸  有水有油->汤/烩/焖\n"
            + "- 水或油过多(>食材总重2倍)->降低品质系数\n\n"
            + "品质系数: 0.2=彻底失败, 1.0=偏差, 1.25=正常, 2.0=完美\n"
            + "servings: 根据食材克重和水量油量估算可吃次数(1-10)\n"
            + "name: 品质与菜名自然结合, 正常食材贴合菜名, 猎奇食材允许猎奇名\n"
            + "description: 25-200字风味描述, 必须含§颜色符, 可用§l§n和分割线\n"
            + "effects: 可选, 仅特殊食材或完美烹饪时出现, 格式[药水:等级:秒]\n\n"
            + "返回纯JSON: {\"name\":\"\",\"servings\":1,\"qualityCoefficient\":1.0,\"description\":\"\"}\n"
            + "如有effects则加\"effects\":[\"SPEED:1:600\"], 无则省略该字段。";

        String userPrompt = gson.toJson(userContent);

        return new String[]{systemPrompt, userPrompt};
    }
}
