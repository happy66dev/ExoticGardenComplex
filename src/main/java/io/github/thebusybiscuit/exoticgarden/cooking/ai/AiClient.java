package io.github.thebusybiscuit.exoticgarden.cooking.ai;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

/**
 * AI 菜肴生成客户端，异步调用 OpenAI 兼容 API 生成菜肴数据喵~
 * 整体思路：异步线程发 HTTP POST，最多重试2次（共3次），超时180s，返回 CompletableFuture。
 * 思考字段（reasoning_content/thinking）不计入结果，只取 content 字段解析 JSON。
 */
public class AiClient {

    // 最大重试次数（不含首次），总共最多3次尝试喵
    private static final int MAX_RETRIES = 2;
    // 单次请求超时时间（毫秒），180秒喵
    private static final int TIMEOUT_MS = 180_000;

    private final String apiKey;
    private final String baseUrl;
    private final String model;
    private final Logger logger;
    // 是否启用模型思考功能喵
    private final boolean thinkingEnabled;
    private final Gson gson = new Gson();

    public AiClient(String apiKey, String baseUrl, String model, Logger logger,
                    boolean thinkingEnabled, int thinkingBudget) {
        this.apiKey = apiKey;
        this.baseUrl = baseUrl != null ? baseUrl.replaceAll("/$", "") : "https://api.openai.com/v1";
        this.model = model;
        this.logger = logger;
        this.thinkingEnabled = thinkingEnabled;
        // 喵~思考预算不通过API传递，由模型自行决定喵
    }

    /**
     * 异步调用 AI 生成菜肴结果喵~
     * 输入：systemPrompt系统提示词，userPrompt用户内容JSON。
     * 输出：CompletableFuture<DishGenerator.DishResult>，失败时 completeExceptionally。
     */
    public CompletableFuture<DishGenerator.DishResult> generateDish(String systemPrompt, String userPrompt) {
        CompletableFuture<DishGenerator.DishResult> future = new CompletableFuture<>();
        // 喵~在异步线程执行，不阻塞主线程喵
        CompletableFuture.runAsync(() -> {
            Exception lastException = null;
            // 喵~总共最多 MAX_RETRIES+1 次尝试喵
            for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
                try {
                    String result = callApi(systemPrompt, userPrompt);
                    DishGenerator.DishResult dish = parseResult(result);
                    future.complete(dish);
                    return;
                } catch (Exception e) {
                    lastException = e;
                    // 喵~防御：记录每次失败日志，便于排查喵
                    logger.warning("[AiClient] 第 " + (attempt + 1) + " 次尝试失败: " + e.getMessage());
                }
            }
            // 喵~所有重试均失败，将最后一次异常传出喵
            future.completeExceptionally(lastException);
        });
        return future;
    }

    /**
     * 发送单次 HTTP POST 请求到 AI API喵~
     * 输入：系统提示词、用户提示词。
     * 输出：API 返回的 content 文本（已过滤思考字段）。
     */
    private String callApi(String systemPrompt, String userPrompt) throws Exception {
        // 构建 OpenAI 兼容的请求体喵
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("model", model);
        requestBody.addProperty("temperature", 0.8);

        // 喵~如果启用思考功能，加入 thinking 参数（兼容支持该参数的模型）喵
        if (thinkingEnabled) {
            JsonObject thinking = new JsonObject();
            thinking.addProperty("type", "enabled");
            requestBody.add("thinking", thinking);
        }

        JsonArray messages = new JsonArray();
        JsonObject sysMsg = new JsonObject();
        sysMsg.addProperty("role", "system");
        sysMsg.addProperty("content", systemPrompt);
        messages.add(sysMsg);
        JsonObject userMsg = new JsonObject();
        userMsg.addProperty("role", "user");
        userMsg.addProperty("content", userPrompt);
        messages.add(userMsg);
        requestBody.add("messages", messages);

        String requestJson = gson.toJson(requestBody);
        byte[] requestBytes = requestJson.getBytes(StandardCharsets.UTF_8);

        // 建立 HTTP 连接喵
        URL url = new URL(baseUrl + "/chat/completions");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
        conn.setRequestProperty("Authorization", "Bearer " + apiKey);
        conn.setConnectTimeout(TIMEOUT_MS);
        conn.setReadTimeout(TIMEOUT_MS);
        conn.setDoOutput(true);

        // 发送请求体喵
        try (OutputStream os = conn.getOutputStream()) {
            os.write(requestBytes);
        }

        // 读取响应喵
        int statusCode = conn.getResponseCode();
        // 喵~防御：非200时从 error stream 读错误信息喵
        if (statusCode != 200) {
            String errBody = "";
            // 喵~防御：getErrorStream()在部分服务端提前关闭时可能为null，防NPE喵
            java.io.InputStream es = conn.getErrorStream();
            if (es != null) {
                try (Scanner sc = new Scanner(es, StandardCharsets.UTF_8)) {
                    errBody = sc.useDelimiter("\\A").hasNext() ? sc.next() : "";
                }
            }
            throw new RuntimeException("HTTP " + statusCode + ": " + errBody);
        }

        String responseBody;
        try (Scanner sc = new Scanner(conn.getInputStream(), StandardCharsets.UTF_8)) {
            responseBody = sc.useDelimiter("\\A").hasNext() ? sc.next() : "";
        }

        // 解析响应，提取 content 字段（忽略 thinking/reasoning_content 等思考字段）喵
        return extractContent(responseBody);
    }

    /**
     * 从 API 响应 JSON 中提取 content 字段，过滤掉思考字段喵~
     * 思考字段：reasoning_content、thinking（部分模型返回）不参与结果解析。
     * 输入：API 返回的完整 JSON 字符串。
     * 输出：choices[0].message.content 的文本内容。
     */
    private String extractContent(String responseBody) {
        JsonObject root = JsonParser.parseString(responseBody).getAsJsonObject();
        // 喵~防御：choices 不存在或为空时抛异常喵
        JsonArray choices = root.getAsJsonArray("choices");
        if (choices == null || choices.size() == 0) {
            throw new RuntimeException("API 返回 choices 为空: " + responseBody);
        }
        JsonObject message = choices.get(0).getAsJsonObject().getAsJsonObject("message");
        // 喵~防御：content 字段不存在时抛异常喵
        JsonElement contentEl = message.get("content");
        if (contentEl == null || contentEl.isJsonNull()) {
            throw new RuntimeException("API 返回 content 为 null，可能只有思考字段: " + responseBody);
        }
        return contentEl.getAsString().trim();
    }

    /**
     * 将 AI 返回的文本解析为 DishResult 对象喵~
     * 整体思路：先尝试直接解析，失败则从文本中提取 JSON 块（```json...```）后再解析。
     * 输入：AI 返回的 content 文本。
     * 输出：解析成功的 DishResult。
     */
    private DishGenerator.DishResult parseResult(String content) {
        // 喵~防御：去掉可能包裹的 markdown 代码块喵
        String json = extractJson(content);
        try {
            JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
            String name = obj.has("name") ? obj.get("name").getAsString() : "未知菜肴";
            int servings = obj.has("servings") ? obj.get("servings").getAsInt() : 1;
            // quality 为中文形容词，兼容旧格式 qualityCoefficient 数字喵
            String quality = "普通";
            if (obj.has("quality")) {
                quality = obj.get("quality").getAsString();
            } else if (obj.has("qualityCoefficient")) {
                // 喵~兼容：旧版数字系数转中文喵
                double coeff = obj.get("qualityCoefficient").getAsDouble();
                if (coeff <= 0.3) quality = "彻底失败";
                else if (coeff <= 0.65) quality = "很差";
                else if (coeff <= 0.9) quality = "差";
                else if (coeff <= 1.1) quality = "普通";
                else if (coeff <= 1.4) quality = "良好";
                else if (coeff <= 1.8) quality = "优秀";
                else quality = "完美";
            }
            String description = obj.has("description") ? obj.get("description").getAsString() : "无描述";
            int hunger = obj.has("hunger") ? obj.get("hunger").getAsInt() : 0;
            double saturation = obj.has("saturation") ? obj.get("saturation").getAsDouble() : 0.0;
            int shelfLifeMinutes = obj.has("shelfLifeMinutes") ? obj.get("shelfLifeMinutes").getAsInt() : 60;
            int qualityScore = obj.has("qualityScore") ? obj.get("qualityScore").getAsInt() : 50;
            String icon = obj.has("icon") ? obj.get("icon").getAsString() : "SUSPICIOUS_STEW";
            java.util.List<String> effects = new java.util.ArrayList<>();
            if (obj.has("effects") && obj.get("effects").isJsonArray()) {
                for (JsonElement el : obj.getAsJsonArray("effects")) {
                    effects.add(el.getAsString());
                }
            }
            // 解析食用句子列表，可选字段，最多5句喵
            java.util.List<String> flavorTexts = new java.util.ArrayList<>();
            if (obj.has("flavorTexts") && obj.get("flavorTexts").isJsonArray()) {
                for (JsonElement el : obj.getAsJsonArray("flavorTexts")) {
                    String text = el.getAsString();
                    // 喵~防御：空句子不加入喵
                    if (text != null && !text.isBlank()) {
                        flavorTexts.add(text);
                    }
                }
            }
            // 解析进食音效，可选字段，缺省由 DishResult 兜底喵
            String eatSound = obj.has("eatSound") ? obj.get("eatSound").getAsString() : null;
            // 解析进食耗时，可选字段，缺省由 DishResult 兜底为1.6s喵
            double consumeSeconds = obj.has("consumeSeconds") ? obj.get("consumeSeconds").getAsDouble() : 0;
            return new DishGenerator.DishResult(name, servings, quality, qualityScore, effects, description, hunger, saturation, shelfLifeMinutes, icon, flavorTexts, eatSound, consumeSeconds);
        } catch (Exception e) {
            throw new RuntimeException("JSON解析失败，原始内容: " + content, e);
        }
    }

    /**
     * 从文本中提取 JSON 块：先找 ```json...``` 格式，找不到则取第一个 { 到最后一个 } 之间的内容喵~
     */
    private String extractJson(String text) {
        // 喵~防御：null或空时返回空对象喵
        if (text == null || text.isEmpty()) return "{}";
        // 优先匹配 ```json...``` 格式喵
        int jsonStart = text.indexOf("```json");
        if (jsonStart >= 0) {
            int start = text.indexOf('{', jsonStart);
            int end = text.lastIndexOf('}');
            if (start >= 0 && end > start) return text.substring(start, end + 1);
        }
        // 回退：取第一个{到最后一个}之间喵
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start >= 0 && end > start) return text.substring(start, end + 1);
        return text;
    }
}
