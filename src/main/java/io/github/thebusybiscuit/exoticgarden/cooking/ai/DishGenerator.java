package io.github.thebusybiscuit.exoticgarden.cooking.ai;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.List;

public class DishGenerator {

    public static class IngredientInfo {
        public final String id;
        public final String state;
        public final double frontDoneness;
        public final double backDoneness;
        public final String charLevel;

        public IngredientInfo(String id, String state, double frontDoneness,
                              double backDoneness, String charLevel) {
            this.id = id;
            this.state = state;
            this.frontDoneness = frontDoneness;
            this.backDoneness = backDoneness;
            this.charLevel = charLevel;
        }
    }

    public static class SeasoningInfo {
        public final String id;
        public final Double progress;

        public SeasoningInfo(String id, Double progress) {
            this.id = id;
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
            List<String> fuelEffects) {
        Gson gson = new Gson();
        JsonObject userContent = new JsonObject();

        JsonArray ingArr = new JsonArray();
        for (IngredientInfo info : ingredientInfos) {
            JsonObject obj = new JsonObject();
            obj.addProperty("id", info.id);
            obj.addProperty("state", info.state);
            obj.addProperty("frontDoneness", info.frontDoneness);
            obj.addProperty("backDoneness", info.backDoneness);
            obj.addProperty("charLevel", info.charLevel);
            ingArr.add(obj);
        }
        userContent.add("ingredients", ingArr);

        JsonArray seaArr = new JsonArray();
        for (SeasoningInfo si : seasoningInfos) {
            JsonObject obj = new JsonObject();
            obj.addProperty("id", si.id);
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
        userContent.addProperty("language", "zh-CN");

        String systemPrompt = "你是一个 Minecraft 烹饪游戏的菜肴生成器。根据食材和烹饪状态，用 JSON 格式返回菜肴信息。严格遵守格式，不输出任何其他内容。";
        String userPrompt = gson.toJson(userContent);

        return new String[]{systemPrompt, userPrompt};
    }
}
