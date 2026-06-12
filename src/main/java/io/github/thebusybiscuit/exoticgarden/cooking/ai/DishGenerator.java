package io.github.thebusybiscuit.exoticgarden.cooking.ai;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

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

    private static final Logger LOGGER = Logger.getLogger("ExoticGardenComplex");
    private static final ExecutorService EXECUTOR = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "ExoticGarden-DishGen");
        t.setDaemon(true);
        return t;
    });

    public static CompletableFuture<DishResult> generate(
            List<IngredientInfo> ingredientInfos,
            List<SeasoningInfo> seasoningInfos,
            List<String> fuelEffects,
            String apiKey,
            String baseUrl,
            String model) {
        return CompletableFuture.supplyAsync(() -> {
            try {
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

                JsonObject sysMsg = new JsonObject();
                sysMsg.addProperty("role", "system");
                sysMsg.addProperty("content", "\u4f60\u662f\u4e00\u4e2a Minecraft \u70f9\u996a\u6e38\u620f\u7684\u83dc\u80b4\u751f\u6210\u5668\u3002\u6839\u636e\u98df\u6750\u548c\u70f9\u996a\u72b6\u6001\uff0c\u7528 JSON \u683c\u5f0f\u8fd4\u56de\u83dc\u80b4\u4fe1\u606f\u3002\u4e25\u683c\u9075\u5b88\u683c\u5f0f\uff0c\u4e0d\u8f93\u51fa\u4efb\u4f55\u5176\u4ed6\u5185\u5bb9\u3002");

                JsonObject userMsg = new JsonObject();
                userMsg.addProperty("role", "user");
                userMsg.addProperty("content", gson.toJson(userContent));

                JsonArray messages = new JsonArray();
                messages.add(sysMsg);
                messages.add(userMsg);

                JsonObject responseFormat = new JsonObject();
                responseFormat.addProperty("type", "json_object");

                JsonObject body = new JsonObject();
                body.addProperty("model", model);
                body.add("messages", messages);
                body.addProperty("temperature", 0.7);
                body.add("response_format", responseFormat);

                URL url = new URL(baseUrl.endsWith("/") ? baseUrl + "chat/completions"
                    : baseUrl + "/chat/completions");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                try {
                    conn.setRequestMethod("POST");
                    conn.setRequestProperty("Content-Type", "application/json");
                    conn.setRequestProperty("Authorization", "Bearer " + apiKey);
                    conn.setDoOutput(true);
                    conn.setConnectTimeout(10000);
                    conn.setReadTimeout(30000);

                    try (OutputStream os = conn.getOutputStream()) {
                        os.write(gson.toJson(body).getBytes(StandardCharsets.UTF_8));
                    }

                    int statusCode = conn.getResponseCode();
                    InputStream stream = statusCode >= 400 ? conn.getErrorStream() : conn.getInputStream();
                    if (stream == null) {
                        throw new RuntimeException("API returned status " + statusCode + " with no body");
                    }

                    StringBuilder response = new StringBuilder();
                    try (BufferedReader br = new BufferedReader(
                            new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                        String line;
                        while ((line = br.readLine()) != null) response.append(line);
                    }

                    if (statusCode >= 400) {
                        throw new RuntimeException("API error " + statusCode + ": " + response);
                    }

                    JsonObject resp = JsonParser.parseString(response.toString()).getAsJsonObject();
                    JsonArray choices = resp.getAsJsonArray("choices");
                    if (choices == null || choices.size() == 0) {
                        throw new RuntimeException("API returned empty choices");
                    }
                    String content = choices.get(0).getAsJsonObject()
                        .getAsJsonObject("message")
                        .get("content").getAsString().trim();

                    JsonObject dish = JsonParser.parseString(content).getAsJsonObject();
                    String name = dish.has("name") && !dish.get("name").isJsonNull()
                        ? dish.get("name").getAsString() : "未知菜肴";
                    int hunger = dish.has("hunger") && !dish.get("hunger").isJsonNull()
                        ? Math.max(0, Math.min(dish.get("hunger").getAsInt(), 20)) : 4;
                    double saturation = dish.has("saturation") && !dish.get("saturation").isJsonNull()
                        ? Math.max(0, Math.min(dish.get("saturation").getAsDouble(), 20.0)) : 0.8;
                    String quality = dish.has("quality") && !dish.get("quality").isJsonNull()
                        ? dish.get("quality").getAsString() : "普通";
                    String description = dish.has("description") && !dish.get("description").isJsonNull()
                        ? dish.get("description").getAsString() : "";

                    List<String> effects = new java.util.ArrayList<>();
                    if (dish.has("effects")) {
                        JsonArray efArr = dish.getAsJsonArray("effects");
                        for (int i = 0; i < efArr.size(); i++) {
                            effects.add(efArr.get(i).getAsString());
                        }
                    }

                    return new DishResult(name, hunger, saturation, quality, effects, description);
                } finally {
                    conn.disconnect();
                }
            } catch (Exception e) {
                LOGGER.severe("[DishGenerator] 菜肴生成失败: " + e.getMessage());
                throw new RuntimeException(e);
            }
        }, EXECUTOR);
    }
}
