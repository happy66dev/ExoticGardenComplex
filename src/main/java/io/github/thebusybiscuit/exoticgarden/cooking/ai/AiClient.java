package io.github.thebusybiscuit.exoticgarden.cooking.ai;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

/**
 * AI 菜肴生成客户端，使用受限后台线程与受限HTTP正文调用 OpenAI 兼容 API喵~
 * 整体思路：最多两个请求并行、八个请求排队；所有正文按字节限制读取，避免异常服务耗尽堆内存喵~
 */
public class AiClient {

    // 最大重试次数（不含首次），总共最多3次尝试喵
    private static final int MAX_RETRIES = 2;
    // 单次请求超时时间（毫秒），180秒喵
    private static final int TIMEOUT_MS = 180_000;
    // 单次请求正文最大大小，单位：字节，防止异常提示词占用过多网络与堆内存喵
    private static final int MAX_REQUEST_BYTES = 256 * 1024;
    // 成功响应正文最大大小，单位：字节，防止服务端返回超大JSON喵
    private static final int MAX_RESPONSE_BYTES = 1024 * 1024;
    // 错误响应正文最大大小，单位：字节，诊断信息不需要占用完整响应喵
    private static final int MAX_ERROR_RESPONSE_BYTES = 16 * 1024;
    // AI content 最大字符数，避免后续JSON处理、lore与PDC写入异常膨胀喵
    private static final int MAX_CONTENT_CHARS = 64 * 1024;
    // 单个文本字段最大字符数，单位：字符，限制物品lore与PDC内容喵
    private static final int MAX_TEXT_CHARS = 512;
    // AI 数组字段最大元素数，防止异常JSON生成巨型列表喵
    private static final int MAX_LIST_ENTRIES = 8;
    // 所有已创建客户端，用于插件停用时统一关闭后台线程喵
    private static final Set<AiClient> ACTIVE_CLIENTS = java.util.concurrent.ConcurrentHashMap.newKeySet();
    // AI后台线程编号，便于服务器日志识别来源喵
    private static final AtomicInteger THREAD_SEQUENCE = new AtomicInteger();

    private final String apiKey;
    private final String baseUrl;
    private final String model;
    private final Logger logger;
    // 是否启用模型思考功能喵
    private final boolean thinkingEnabled;
    // JSON序列化与解析器只在本客户端使用喵
    private final Gson gson = new Gson();
    // 有界专用线程池，避免阻塞HTTP占用全局ForkJoinPool喵
    private final ThreadPoolExecutor requestExecutor;

    public AiClient(String apiKey, String baseUrl, String model, Logger logger,
                    boolean thinkingEnabled, int thinkingBudget) {
        // 保存鉴权密钥，仅用于Authorization请求头喵
        this.apiKey = apiKey;
        // 喵~防御：基础URL为空时回退官方兼容端点，并移除末尾斜杠喵
        this.baseUrl = baseUrl != null ? baseUrl.replaceAll("/$", "") : "https://api.openai.com/v1";
        // 保存模型名称喵
        this.model = model;
        // 保存日志器，用于记录受限的失败摘要喵
        this.logger = logger;
        // 保存思考开关喵
        this.thinkingEnabled = thinkingEnabled;
        // 创建两个工作线程、八个排队槽位的受限执行器，拒绝时立即让future失败喵
        this.requestExecutor = new ThreadPoolExecutor(2, 2, 30L, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(8), createThreadFactory(), new ThreadPoolExecutor.AbortPolicy());
        // 注册客户端，使插件停用时能够统一关闭所有网络任务喵
        ACTIVE_CLIENTS.add(this);
        // 喵~思考预算不通过API传递，由模型自行决定喵
    }

    /**
     * 异步调用 AI 生成菜肴结果喵~
     * 输入：systemPrompt系统提示词，userPrompt用户内容JSON。
     * 输出：CompletableFuture<DishGenerator.DishResult>，失败时 completeExceptionally。
     */
    public CompletableFuture<DishGenerator.DishResult> generateDish(String systemPrompt, String userPrompt) {
        // 创建由调用方持有的结果future，避免暴露线程池内部任务喵
        CompletableFuture<DishGenerator.DishResult> resultFuture = new CompletableFuture<>();
        try {
            // 在线程池中执行阻塞HTTP，绝不阻塞Bukkit主线程喵
            requestExecutor.execute(() -> executeRequest(systemPrompt, userPrompt, resultFuture));
        } catch (RejectedExecutionException rejectedException) {
            // 喵~防御：队列已满或客户端已关闭时立即失败，调用方沿用既有手动解冻语义喵
            resultFuture.completeExceptionally(new IllegalStateException("AI 请求繁忙，请稍后重试", rejectedException));
        }
        return resultFuture;
    }

    /**
     * 关闭当前客户端的后台执行器喵~
     * 输入：无
     * 输出：无
     * 边界：HTTP中断不保证立即返回，调用方仍需使用请求令牌拒绝迟到回调喵
     */
    public void shutdown() {
        // 停止接收新任务并中断排队或运行中的后台请求喵
        requestExecutor.shutdownNow();
        // 从全局客户端集合删除，释放插件类加载器引用喵
        ACTIVE_CLIENTS.remove(this);
    }

    /**
     * 关闭所有已创建的AI客户端，供插件停用生命周期调用喵~
     * 输入：无
     * 输出：无
     * 边界：复制集合以避免shutdown过程中修改集合导致并发遍历问题喵
     */
    public static void shutdownAll() {
        // 遍历快照，确保每个客户端都有机会释放专用线程池喵
        for (AiClient client : List.copyOf(ACTIVE_CLIENTS)) client.shutdown();
    }

    /**
     * 在线程池内执行最多三次的网络请求喵~
     * 输入：两个提示词及待完成future
     * 输出：无，结果或异常写入future
     * 边界：future被取消时停止重试，避免灶台销毁后继续占用网络资源喵
     */
    private void executeRequest(String systemPrompt, String userPrompt,
                                CompletableFuture<DishGenerator.DishResult> resultFuture) {
        Exception lastException = null;
        // 最多执行首次加重试次数，所有工作留在受限后台线程喵
        for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
            // 喵~防御：调用方取消future后不再进行新的HTTP尝试喵
            if (resultFuture.isCancelled() || Thread.currentThread().isInterrupted()) return;
            try {
                // 调用远端并获得受限的模型content喵
                String content = callApi(systemPrompt, userPrompt);
                // 解析并校验模型输出，避免异常字段写入ItemMeta或PDC喵
                DishGenerator.DishResult dish = parseResult(content);
                // 完成future；若已被取消则不再触发下游回调喵
                resultFuture.complete(dish);
                return;
            } catch (Exception exception) {
                // 保存最后异常供所有重试失败后返回喵
                lastException = exception;
                // 喵~防御：取消或中断不是远端失败，不再刷日志或重试喵
                if (resultFuture.isCancelled() || Thread.currentThread().isInterrupted()) return;
                // 记录长度受限的摘要，防止远端正文进入日志造成内存和磁盘压力喵
                logger.warning("[AiClient] 第 " + (attempt + 1) + " 次尝试失败: " + abbreviate(exception.getMessage(), 256));
            }
        }
        // 所有尝试失败后将最后一次受控异常交给现有失败逻辑喵
        resultFuture.completeExceptionally(lastException != null ? lastException : new IllegalStateException("AI 请求未返回结果"));
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
        // 构建系统与用户消息数组喵
        JsonArray messages = new JsonArray();
        JsonObject systemMessage = new JsonObject();
        systemMessage.addProperty("role", "system");
        systemMessage.addProperty("content", systemPrompt);
        messages.add(systemMessage);
        JsonObject userMessage = new JsonObject();
        userMessage.addProperty("role", "user");
        userMessage.addProperty("content", userPrompt);
        messages.add(userMessage);
        requestBody.add("messages", messages);
        // 将请求序列化为UTF-8字节喵
        byte[] requestBytes = gson.toJson(requestBody).getBytes(StandardCharsets.UTF_8);
        // 喵~防御：拒绝异常大的提示词，避免写入网络前就造成堆或带宽压力喵
        if (requestBytes.length > MAX_REQUEST_BYTES) throw new IllegalArgumentException("AI 请求内容超过大小限制");

        // 建立HTTP连接，finally中无条件断开连接喵
        HttpURLConnection connection = (HttpURLConnection) new URL(baseUrl + "/chat/completions").openConnection();
        try {
            // 设置POST请求方法喵
            connection.setRequestMethod("POST");
            // 设置JSON编码请求头喵
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            // 设置Bearer鉴权请求头喵
            connection.setRequestProperty("Authorization", "Bearer " + apiKey);
            // 设置连接超时喵
            connection.setConnectTimeout(TIMEOUT_MS);
            // 设置读取超时喵
            connection.setReadTimeout(TIMEOUT_MS);
            // 声明请求含有输出正文喵
            connection.setDoOutput(true);
            // 发送受限请求正文喵
            try (OutputStream outputStream = connection.getOutputStream()) {
                outputStream.write(requestBytes);
            }
            // 读取HTTP状态码喵
            int statusCode = connection.getResponseCode();
            // 喵~防御：所有非2xx状态都按受限错误正文失败喵
            if (statusCode < 200 || statusCode >= 300) {
                String errorBody = "";
                // 获取可能为空的错误流喵
                InputStream errorStream = connection.getErrorStream();
                // 喵~防御：部分服务端不会提供错误流，避免空指针喵
                if (errorStream != null) {
                    try (InputStream managedErrorStream = errorStream) {
                        errorBody = readLimitedBody(managedErrorStream, MAX_ERROR_RESPONSE_BYTES);
                    }
                }
                throw new IllegalStateException("HTTP " + statusCode + ": " + abbreviate(errorBody, 512));
            }
            // 成功响应按字节上限读取，阻止Scanner一次性读入超大正文喵
            try (InputStream inputStream = connection.getInputStream()) {
                return extractContent(readLimitedBody(inputStream, MAX_RESPONSE_BYTES));
            }
        } finally {
            // 无论成功、异常、超限或取消都释放底层HTTP连接资源喵
            connection.disconnect();
        }
    }

    /**
     * 从流中读取不超过指定字节数的UTF-8正文喵~
     * 输入：inputStream-响应流，maximumBytes-允许的最大字节数
     * 输出：受限正文字符串
     * 异常：正文超过上限时抛出异常，绝不继续分配更大数组喵
     */
    private static String readLimitedBody(InputStream inputStream, int maximumBytes) throws Exception {
        // 喵~防御：流为空时返回空文本，调用方按协议字段判断失败喵
        if (inputStream == null) return "";
        // 以最多上限大小创建缓冲，避免根据恶意Content-Length预分配喵
        ByteArrayOutputStream bodyBuffer = new ByteArrayOutputStream(Math.min(maximumBytes, 8192));
        // 每次以固定小块读取网络数据喵
        byte[] readBuffer = new byte[4096];
        int totalBytes = 0;
        int readBytes;
        // 循环读取直到流结束喵
        while ((readBytes = inputStream.read(readBuffer)) != -1) {
            // 累加前检查上限，防止整数溢出和超限写入喵
            if (readBytes > maximumBytes - totalBytes) throw new IllegalStateException("AI 响应超过大小限制");
            totalBytes += readBytes;
            bodyBuffer.write(readBuffer, 0, readBytes);
        }
        return bodyBuffer.toString(StandardCharsets.UTF_8);
    }

    /**
     * 从 API 响应 JSON 中提取受限 content 字段，过滤掉思考字段喵~
     * 输入：API 返回的完整但已受限 JSON 字符串。
     * 输出：choices[0].message.content 的文本内容。
     */
    private String extractContent(String responseBody) {
        // 解析根对象并确认JSON类型正确喵
        JsonElement rootElement = JsonParser.parseString(responseBody);
        // 喵~防御：根节点不是对象时给出简短协议异常喵
        if (!rootElement.isJsonObject()) throw new IllegalStateException("API 响应不是JSON对象");
        JsonObject root = rootElement.getAsJsonObject();
        // 读取choices数组喵
        JsonArray choices = root.getAsJsonArray("choices");
        // 喵~防御：choices不存在或为空时拒绝异常响应喵
        if (choices == null || choices.isEmpty() || !choices.get(0).isJsonObject()) throw new IllegalStateException("API 返回 choices 为空");
        JsonObject choice = choices.get(0).getAsJsonObject();
        // 读取message对象喵
        JsonObject message = choice.getAsJsonObject("message");
        // 喵~防御：message缺失时避免空指针喵
        if (message == null) throw new IllegalStateException("API 返回 message 缺失");
        JsonElement contentElement = message.get("content");
        // 喵~防御：content不存在或为空时明确失败喵
        if (contentElement == null || contentElement.isJsonNull() || !contentElement.isJsonPrimitive()) {
            throw new IllegalStateException("API 返回 content 为空");
        }
        String content = contentElement.getAsString().trim();
        // 喵~防御：content过长会放大后续JSON、lore与PDC内存占用喵
        if (content.length() > MAX_CONTENT_CHARS) throw new IllegalStateException("AI 返回内容超过大小限制");
        return content;
    }

    /**
     * 将 AI 返回的文本解析并校验为 DishResult 对象喵~
     * 整体思路：提取JSON块后限制所有文本、数组和数值，避免AI输出形成超大ItemMeta喵
     */
    private DishGenerator.DishResult parseResult(String content) {
        try {
            // 提取可能被markdown代码块包裹的JSON喵
            JsonObject object = JsonParser.parseString(extractJson(content)).getAsJsonObject();
            // 读取并限制名称字段喵
            String name = readText(object, "name", "未知菜肴");
            // 读取并钳制份数，防止负数或异常大数值喵
            int servings = clamp(readInt(object, "servings", 1), 1, 64);
            // 读取品质文本或兼容旧的数值质量系数喵
            String quality = resolveQuality(object);
            // 读取并限制描述字段喵
            String description = readText(object, "description", "无描述");
            // 读取并钳制饥饿值喵
            int hunger = clamp(readInt(object, "hunger", 0), 0, 20);
            // 读取并钳制饱和度喵
            double saturation = clamp(readDouble(object, "saturation", 0.0), 0.0, 20.0);
            // 读取并钳制保质期分钟数喵
            int shelfLifeMinutes = clamp(readInt(object, "shelfLifeMinutes", 60), 1, 525_600);
            // 读取并钳制品质分数喵
            int qualityScore = clamp(readInt(object, "qualityScore", 50), 0, 100);
            // 读取并限制图标材质名称喵
            String icon = readText(object, "icon", "SUSPICIOUS_STEW");
            // 读取受限效果列表喵
            List<String> effects = readTextList(object, "effects");
            // 读取受限食用文案列表喵
            List<String> flavorTexts = readTextList(object, "flavorTexts");
            // 读取可选进食音效名称喵
            String eatSound = object.has("eatSound") ? readText(object, "eatSound", "minecraft:entity.generic.eat") : null;
            // 读取并钳制食用耗时，单位：秒喵
            double consumeSeconds = clamp(readDouble(object, "consumeSeconds", 1.6), 0.1, 10.0);
            return new DishGenerator.DishResult(name, servings, quality, qualityScore, effects, description, hunger,
                    saturation, shelfLifeMinutes, icon, flavorTexts, eatSound, consumeSeconds);
        } catch (Exception exception) {
            // 仅返回受限错误摘要，避免完整AI内容被异常链长期引用喵
            throw new IllegalStateException("AI 菜肴JSON解析失败: " + abbreviate(exception.getMessage(), 256), exception);
        }
    }

    /** 读取并限制JSON文本字段喵~ */
    private static String readText(JsonObject object, String fieldName, String fallbackValue) {
        // 喵~防御：字段不存在、为null或非原始值时使用安全默认值喵
        if (!object.has(fieldName) || object.get(fieldName).isJsonNull() || !object.get(fieldName).isJsonPrimitive()) return fallbackValue;
        return abbreviate(object.get(fieldName).getAsString(), MAX_TEXT_CHARS);
    }

    /** 读取整数JSON字段，格式不正确时回退默认值喵~ */
    private static int readInt(JsonObject object, String fieldName, int fallbackValue) {
        try {
            return object.has(fieldName) ? object.get(fieldName).getAsInt() : fallbackValue;
        } catch (Exception ignored) {
            return fallbackValue;
        }
    }

    /** 读取浮点JSON字段，格式不正确或非有限数时回退默认值喵~ */
    private static double readDouble(JsonObject object, String fieldName, double fallbackValue) {
        try {
            double value = object.has(fieldName) ? object.get(fieldName).getAsDouble() : fallbackValue;
            return Double.isFinite(value) ? value : fallbackValue;
        } catch (Exception ignored) {
            return fallbackValue;
        }
    }

    /** 读取并限制JSON字符串数组喵~ */
    private static List<String> readTextList(JsonObject object, String fieldName) {
        List<String> values = new ArrayList<>();
        // 喵~防御：字段不是数组时返回空列表喵
        if (!object.has(fieldName) || !object.get(fieldName).isJsonArray()) return values;
        for (JsonElement element : object.getAsJsonArray(fieldName)) {
            // 喵~防御：达到业务上限后停止读取剩余元素喵
            if (values.size() >= MAX_LIST_ENTRIES) break;
            // 喵~防御：仅接受非空字符串原始值喵
            if (!element.isJsonPrimitive()) continue;
            String value = abbreviate(element.getAsString(), MAX_TEXT_CHARS);
            if (!value.isBlank()) values.add(value);
        }
        return values;
    }

    /** 根据新旧字段解析质量描述喵~ */
    private static String resolveQuality(JsonObject object) {
        // 新格式quality优先喵
        if (object.has("quality")) return readText(object, "quality", "普通");
        // 兼容旧质量系数字段喵
        if (!object.has("qualityCoefficient")) return "普通";
        double coefficient = readDouble(object, "qualityCoefficient", 1.0);
        if (coefficient <= 0.3) return "彻底失败";
        if (coefficient <= 0.65) return "很差";
        if (coefficient <= 0.9) return "差";
        if (coefficient <= 1.1) return "普通";
        if (coefficient <= 1.4) return "良好";
        if (coefficient <= 1.8) return "优秀";
        return "完美";
    }

    /** 将整数限制在闭区间内喵~ */
    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    /** 将浮点数限制在闭区间内喵~ */
    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    /** 截断文本到最大字符数并添加省略标记喵~ */
    private static String abbreviate(String value, int maximumCharacters) {
        // 喵~防御：空文本统一回退为空字符串，避免日志与异常拼接空指针喵
        if (value == null) return "";
        return value.length() <= maximumCharacters ? value : value.substring(0, maximumCharacters) + "...";
    }

    /** 创建守护AI工作线程，避免插件关闭后线程阻止JVM退出喵~ */
    private static ThreadFactory createThreadFactory() {
        return runnable -> {
            Thread thread = new Thread(runnable, "ExoticGarden-AI-" + THREAD_SEQUENCE.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
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
