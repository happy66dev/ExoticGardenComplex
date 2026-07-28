package io.github.thebusybiscuit.exoticgarden.cooking.ai;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.List;

public class DishGenerator {

    public static class IngredientInfo {
        public final String name;
        public final String state;
        public final int doneness;
        // WHOLE 状态的正面/背面熟度，其他状态为-1喵
        public final int frontDoneness;
        public final int backDoneness;
        public final double weight;
        public final double foodPoints;
        public final double saturation;
        public final String hint;
        public final List<String> fuelEffects;
        public final boolean isExpired;
        // 过期时间（分钟），isExpired=true时有效；-1表示未过期或无时间戳喵
        public final long expiredMinutes;
        // 食材保质期（分钟），0表示无保质期配置喵
        public final int shelfLifeMinutes;

        public IngredientInfo(String name, String state, int doneness,
                              int frontDoneness, int backDoneness,
                              double weight,
                              double foodPoints, double saturation, String hint,
                              List<String> fuelEffects, boolean isExpired,
                              long expiredMinutes, int shelfLifeMinutes) {
            this.name = name;
            this.state = state;
            this.doneness = doneness;
            this.frontDoneness = frontDoneness;
            this.backDoneness = backDoneness;
            this.weight = weight;
            this.foodPoints = foodPoints;
            this.saturation = saturation;
            this.hint = hint != null ? hint : "";
            this.fuelEffects = fuelEffects != null ? fuelEffects : new java.util.ArrayList<>();
            this.isExpired = isExpired;
            this.expiredMinutes = expiredMinutes;
            this.shelfLifeMinutes = shelfLifeMinutes;
        }
    }

    public static class SeasoningInfo {
        public final String name;
        public final Integer progress;
        public final double mlAmount;
        // 给 AI 的特殊属性提示，帮助 AI 更准确识别调料风味和用途喵
        public final String hint;
        // 调料是否已过期，影响菜肴品质和命名风格喵
        public final boolean isExpired;
        // 过期时间（分钟），isExpired=true时有效；-1表示未过期或无时间戳喵
        public final long expiredMinutes;
        // 调料保质期（分钟），0表示无保质期配置喵
        public final int shelfLifeMinutes;

        public SeasoningInfo(String name, Integer progress, double mlAmount, String hint, boolean isExpired,
                             long expiredMinutes, int shelfLifeMinutes) {
            this.name = name;
            this.progress = progress;
            this.mlAmount = mlAmount;
            // 喵~防御：hint 为 null 时存为空字符串，避免 NPE 喵
            this.hint = hint != null ? hint : "";
            this.isExpired = isExpired;
            this.expiredMinutes = expiredMinutes;
            this.shelfLifeMinutes = shelfLifeMinutes;
        }
    }

    public static class DishResult {
        public final String name;
        public final int servings;
        public final String quality;
        public final double qualityCoefficient;
        public final int qualityScore;
        public final List<String> effects;
        public final String description;
        public final int hunger;
        public final double saturation;
        public final int shelfLifeMinutes;
        // 菜肴图标，原版 Material 名（如 COOKED_BEEF），缺省 SUSPICIOUS_STEW喵
        public final String icon;
        // 食用句子列表，最多5句，每句≤35字，食用未过期菜肴时按顺序展示给玩家喵
        public final List<String> flavorTexts;
        // 进食音效，Minecraft声音ID（如 entity.generic.eat），缺省标准进食音效喵
        public final String eatSound;
        // 进食耗时（秒），默认1.6s，特殊食材可调整喵
        public final double consumeSeconds;

        public DishResult(String name, int servings, String quality, int qualityScore,
                          List<String> effects, String description,
                          int hunger, double saturation, int shelfLifeMinutes, String icon,
                          List<String> flavorTexts, String eatSound, double consumeSeconds) {
            this.name = name;
            this.servings = servings;
            this.quality = quality;
            this.qualityCoefficient = qualityToCoefficient(quality);
            this.qualityScore = qualityScore;
            this.effects = effects;
            this.description = description;
            this.hunger = hunger;
            this.saturation = saturation;
            this.shelfLifeMinutes = shelfLifeMinutes;
            this.icon = icon != null ? icon : "SUSPICIOUS_STEW";
            this.flavorTexts = flavorTexts != null ? flavorTexts : new java.util.ArrayList<>();
            // 喵~防御：eatSound 为 null 或空时用标准音效喵
            String rawSound = (eatSound != null && !eatSound.isBlank()) ? eatSound.trim() : "entity.generic.eat";
            // 喵~防御：Adventure Key.key() 必须是 namespace:value 格式，裸名缺省补 minecraft: 前缀喵
            this.eatSound = rawSound.contains(":") ? rawSound : "minecraft:" + rawSound;
            // 喵~防御：consumeSeconds <=0 时回退默认值1.6s喵
            this.consumeSeconds = consumeSeconds > 0 ? consumeSeconds : 1.6;
        }

        // 将中文品质形容词转为系数，未知词汇默认1.0喵
        public static double qualityToCoefficient(String quality) {
            if (quality == null) return 1.0;
            return switch (quality) {
                case "彻底失败" -> 0.2;
                case "很差"    -> 0.5;
                case "差"      -> 0.8;
                case "普通"    -> 1.0;
                case "良好"    -> 1.25;
                case "优秀"    -> 1.6;
                case "完美"    -> 2.0;
                default        -> 1.0;
            };
        }
    }

    public static String[] buildPrompt(
            List<IngredientInfo> ingredientInfos,
            List<SeasoningInfo> seasoningInfos,
            double waterMl, double oilMl,
            double totalWeight,
            List<String> waterSources,
            List<org.bukkit.potion.PotionEffect> potionEffects,
            double currentTemp) {
        Gson gson = new Gson();
        JsonObject userContent = new JsonObject();

        JsonArray ingArr = new JsonArray();
        for (IngredientInfo info : ingredientInfos) {
            JsonObject obj = new JsonObject();
            obj.addProperty("name", info.name);
            obj.addProperty("state", info.state);
            // WHOLE状态传双面熟度，其他状态传单一doneness喵
            if ("完整".equals(info.state) && info.frontDoneness >= 0 && info.backDoneness >= 0) {
                obj.addProperty("frontDoneness", info.frontDoneness);
                obj.addProperty("backDoneness", info.backDoneness);
            } else {
                obj.addProperty("doneness", info.doneness);
            }
            obj.addProperty("weight", info.weight);
            obj.addProperty("foodPoints", info.foodPoints);
            obj.addProperty("saturation", info.saturation);
            // hint 非空时才传给 AI，减少无效字段喵
            if (!info.hint.isEmpty()) {
                obj.addProperty("hint", info.hint);
            }
            // fuelEffects 非空时才传给 AI，告知该食材经历的燃料风味喵
            if (!info.fuelEffects.isEmpty()) {
                JsonArray fxArr = new JsonArray();
                for (String fx : info.fuelEffects) fxArr.add(fx);
                obj.add("fuelEffects", fxArr);
            }
            // 食材过期时传给 AI，影响品质判断和猎奇名喵
            if (info.isExpired) {
                obj.addProperty("expired", true);
                // 已过期多久（分钟），-1表示无时间戳喵
                if (info.expiredMinutes >= 0) obj.addProperty("expiredMinutes", info.expiredMinutes);
                // 食材保质期（分钟），供AI判断腐败程度喵
                if (info.shelfLifeMinutes > 0) obj.addProperty("shelfLifeMinutes", info.shelfLifeMinutes);
            }
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
            obj.addProperty("mlAmount", si.mlAmount);
            // hint 非空时才传给 AI，减少无效字段喵
            if (!si.hint.isEmpty()) {
                obj.addProperty("hint", si.hint);
            }
            // 调料过期时传给 AI，影响品质和命名风格喵
            if (si.isExpired) {
                obj.addProperty("expired", true);
                if (si.expiredMinutes >= 0) obj.addProperty("expiredMinutes", si.expiredMinutes);
                if (si.shelfLifeMinutes > 0) obj.addProperty("shelfLifeMinutes", si.shelfLifeMinutes);
            }
            seaArr.add(obj);
        }
        userContent.add("seasonings", seaArr);

        // 喵~fuelEffects 已绑定到各食材的 JSON 对象里，不需要全局字段重复传递喵

        userContent.addProperty("waterMl", waterMl);
        userContent.addProperty("oilMl", oilMl);

        JsonArray wsArr = new JsonArray();
        for (String ws : waterSources) wsArr.add(ws);
        userContent.add("waterSources", wsArr);

        // 删除 totalHunger 字段，只保留 totalWeight 喵
        userContent.addProperty("totalWeight", totalWeight);
        userContent.addProperty("language", "zh-CN");
        // 当前灶台温度（°C），影响实际烹饪方式判断喵
        userContent.addProperty("currentTemp", (int) currentTemp);

        // 药水效果：显式传给AI，格式[{effect:效果名, amplifier:等级, durationSeconds:持续秒}]喵
        if (potionEffects != null && !potionEffects.isEmpty()) {
            JsonArray potionArr = new JsonArray();
            for (org.bukkit.potion.PotionEffect pe : potionEffects) {
                JsonObject obj = new JsonObject();
                obj.addProperty("effect", pe.getType().getName());
                obj.addProperty("amplifier", pe.getAmplifier() + 1);
                obj.addProperty("durationSeconds", pe.getDuration() / 20);
                potionArr.add(obj);
            }
            userContent.add("potionEffects", potionArr);
        }

        String systemPrompt = "你是一个 Minecraft 烹饪游戏的菜肴生成器。\n"
            + "Minecraft颜色代码(§前缀): §0黑 §1深蓝 §2深绿 §3深青 §4深红 §5深紫 §6金黄 §7灰 "
            + "§8深灰 §9蓝 §a绿 §b青 §c红 §d浅紫 §e黄 §f白 ; 格式化:§l粗体 §o斜体 §n下划线 §r重置\n"
            + "输入 JSON 字段说明:\n"
            + "- ingredients: [{name:\"食材名\",state:\"完整/切片/切条/切丁/酱汁\","
            + "注意: state为\"完整\"(未切割)时 字段为 frontDoneness(正面熟度%) 和 backDoneness(背面熟度%) 两面分别表示; "
            + "state非\"完整\"(已切割加工)时 字段为 doneness(整体熟度%) 单值; "
            + "熟度百分比参考值:非牛肉类:0~60生 60~90未熟透 90~125成熟 125~180老了 180+焦了 "
            + "牛肉类:0%~10%不熟 10%~25%三分熟 25%~50%五分 50%~75%七分 75~115%全熟 115%~150%老了 150%+焦了 "
            + "菜类(可能有些菜可以生吃 你根据现实知识自行辨别):0%~70%生 70%~90%嫩 90%~110%熟 110%~140%老 140%+糊 "
            + "果类0~50%可以 50~100%熟了 100%~150%烂了 150%+焦了),"
            + "weight:克,foodPoints:饱食度,saturation:饱和度,"
            + "hint(可选):该食材的特殊属性或用途,fuelEffects:烹饪期间经历的燃料风味（可为空）,"
            + "expired(可选):true表示食材放入灶台前已过期 会影响菜肴品质和命名风格;"
            + "expiredMinutes(可选,expired=true时存在):已过期多少分钟(以盛菜时为准);"
            + "shelfLifeMinutes(可选,expired=true时存在):该食材的保质期分钟数 可用于推断腐败程度(过期时间/保质期=腐败倍率)}]\n"
            + "- seasonings: [{name:\"调料名\",progress:渗入度百分比(0-200,100=完美,200%变味了),mlAmount:液体毫升量,hint(可选):该调料的特殊属性或用途,"
            + "expired(可选):true表示该调料放入灶台前已过期 影响菜肴品质;"
            + "expiredMinutes(可选,expired=true时存在):已过期多少分钟(以盛菜时为准);"
            + "shelfLifeMinutes(可选,expired=true时存在):该调料的保质期分钟数}]\n"
            + "  可用调料参考(玩家实际投料在上方seasonings列表中): 盐 白糖 红糖 料酒 醋 黑胡椒碎 葱花 花生碎 香菜碎 咖喱叶 茶叶 蜂蜜 黄油(辅料) 淡奶油(辅料) 植物油(油类) 味精 "
            + "各类果汁(柠檬汁/橙汁/苹果汁/葡萄汁/草莓汁/樱桃汁/梅子汁/桃子汁/梨汁/石榴汁/火龙果汁/菠萝汁/椰奶/番茄汁/胡萝卜汁/南瓜汁等) "
            + "特殊调料(恶魔瓜丁/地狱果片/药水) 肉类碎料(培根碎/炸鸡碎/鸡块碎/薯条碎/洋葱圈碎/芝士碎) 酱料(烤肉酱/蛋黄酱/芥末/肉汁)\n"
            + "- fuelEffects: [\"燃料风味\"]\n"
            + "- waterMl: 当前剩余水量(已蒸发扣除的剩余), oilMl: 油量(毫升)\n"
            + "- waterSources: [\"水来源 可能蒸发了\"] 如[\"柠檬汁\",\"牛奶\",\"料酒\",\"水\"]等\n"
            + "- totalWeight: 食材+辅料总克重\n"
            + "- potionEffects(可选): [{effect:效果名,amplifier:等级,durationSeconds:持续秒}]\n"
            + "- currentTemp: 当前灶台温度(°C)，影响实际烹饪方式判断\n"
            + "- 如有hint字段则表示该食材/调料的特殊属性或用途，应影响菜肴命名和描述\n"
            + "- 食材的fuelEffects字段说明: 该食材烹饪期间经历的燃料风味列表;\n"
            + "  数量1~2=少量风味 适当, 3=较多 品质略降, 3+超量则考虑降低品质;\n"
            + "  负面风味(如'岩浆淬炼'用于不适合高温的食材)越多品质越低\n"
            + "烹饪方向推断(根据水量油量):\n"
            + "- 无水无油->烧烤/干烧(食材适合烤则品质不差,否则干烧品质差)\n"
            + "- 有水无油->炖/煮/蒸  有油无水->煎/炒/炸  有水有油->汤/烩/焖\n"
            + "- 水或油过多(判断按照正常思维来)->影响品质判断\n"
            + "注意 水和油的量也在烹饪输出参考内 并非只决定品质,也决定食用次数 以及间接导致的每口饱食度等\n"
            + "品质: 彻底失败/很差/差/普通/良好/优秀/完美 (大类)\n"
            + "qualityScore: 0~100的整数，细化品质分数，在品质大类范围内自由判断\n"
            + "  参考区间: 彻底失败0~15 很差16~35 差36~45 普通46~55 良好56~70 优秀71~85 完美86~100\n"
            + "  从多维度评分:食材选取 燃料词条 加工方式 温度控制 食材丰富度 调味等\n"
            + "  对应颜色: §4彻底失败 §c很差 §e差 §7普通 §a良好 §b优秀 §6完美\n"
            + "  quality字段格式: [品质颜色]品质字 §7([品质颜色]分数§7/§e100分§7) 全部使用§符号不使用&\n"
            + "  示例: §6完美 §7(§692§7/§e100分§7)  §7普通 §7(§750§7/§e100分§7)\n"
            + "name: 菜名自然贴合食材，猎奇加工/过期食材允许猎奇名，菜名本身无需包含品质字段\n"
            + "servings: 根据食材克重和水量油量和加工方式估算可吃次数(1-10)\n"
            + "hunger: 每次食用恢复的饱食度(整数,参考食材foodPoints之和按品质调整,缺省0)\n"
            + "saturation: 每次食用恢复的饱和度(浮点,参考食材saturation之和按品质调整,缺省0.0)\n"
            + "shelfLifeMinutes: 常温变质期(整数 单位:分钟 参考现实食物常温变质时间 参考:水果拼盘6小时 烤牛排16小时 蔬菜沙拉6小时 烤面包36小时 如果食材有过期 则大幅减少变质期 缺省60)\n"
            + "name: 菜名必须包含§颜色符（如§6金苹果炖菜）品质与菜名自然结合 正常食材贴合菜名 猎奇加工/食材组合允许猎奇名\n"
            + "description: 风味描述, 颜色符必须使用§前缀(不用&前缀), 每行都必须包含颜色符(不支持跨行颜色继承), "
            + "换行使用JSON标准\\n(即JSON字符串中的\\n转义符) 每行严格控制在24字以内(含颜色符不算字数) 总行数3~7行 "
            + "分割线格式:§7---------\n"
            + "effects: 可选, 仅特殊食材或含药水或完美烹饪时出现, 支持多个效果, 格式[\"药水ID:等级:秒\",\"药水ID2:等级:秒\"];\n"
            + "  注意:等级从0开始 即0=1级 1=2级 以此类推 返回1实际为2级 请按(实际期望等级-1)填写\n\n"
            + "必须返回纯JSON，必须包含以下所有字段（无论如何不能省略，缺字段会导致解析失败）：\n"
            + "{\"name\":\"[品质颜色]菜名（必须含颜色符，缺省'§7未知菜肴'）\","
            + "\"servings\":份数整数(缺省1),"
            + "\"quality\":\"品质带颜色和分数格式字符串(必填！缺省'§7普通 §7(§750§7/§e100分§7) 注意右括号要用§7')\","
            + "\"qualityScore\":细化品质分数整数0~100(必填！缺省50),"
            + "\"hunger\":饱食度整数(缺省0),"
            + "\"saturation\":饱和度浮点(缺省0.0),"
            + "\"shelfLifeMinutes\":常温变质期整数分钟(缺省60),"
            + "\"icon\":\"Minecraft物品材质名(如COOKED_BEEF、BREAD、MUSHROOM_STEW等，根据菜名选择最贴切的材质名，缺省SUSPICIOUS_STEW)\","
            + "\"description\":\"描述字符串（缺省用'无描述'）\"}\n"
            + "如有effects则加\"effects\":[\"SPEED:1:600\",\"REGENERATION:1:200\"], 可以多个, 无则省略该字段。\n"
            + "如有食用句子则加\"flavorTexts\":[\"§e句子1\",\"§a句子2\"], 句子数量必须等于servings(份数), 无则省略该字段;\n"
            + "  flavorTexts 说明: 玩家每吃一口会按顺序收到对应句子，句子描述从第一口到最后一口的感受，类似吃东西时脑子里的OS或旁白。\n"
            + "  flavorTexts 规则: 每句必须包含§颜色符 每句严格不超过35字(含颜色符不算字数) 句子数量必须与servings完全一致\n"
            + "  flavorTexts 风格示例: §e第一口下去满是烟熏香  §a越吃越停不下来  §6吃到最后一口 有点舍不得\n"
            + "eatSound(可选): 进食音效Minecraft声音ID，缺省不填即使用标准进食音效(entity.generic.eat);\n"
            + "  只在食材特殊时才设置，如食物成焦炭 汤类 腐败等\n"
            + "  参考音效(你也可以自己从自己知识内选择一个): entity.generic.eat(标准) entity.generic.drink(喝) entity.generic.explode(爆炸) item.bottle.fill(液体装瓶) block.anvil.place(铁砧) block.stone.break(石头) block.grass.break(草)\n"
            + "consumeSeconds(可选): 进食耗时秒数(浮点,缺省不填即1.6s);\n"
            + "最低0.6s 原版值为1.6s 根据食物大小判断 推荐往大于原版数值靠(除非食物每份分量小) 可用范围:0.6s~5s\n"
            + "icon 选材建议(不要完全参考): 炖菜类→MUSHROOM_STEW/SUSPICIOUS_STEW 烧烤类→COOKED_BEEF/COOKED_PORKCHOP/COOKED_CHICKEN/COOKED_MUTTON "
            + "煎炒类→COOKED_COD/COOKED_SALMON 面包类→BREAD 汤类→BEETROOT_SOUP/RABBIT_STEW 甜点类→COOKIE/PUMPKIN_PIE 生食类→APPLE/MELON_SLICE\n"
            + "description 书写规范: 不使用中文标点符号(逗号用空格代替 句号省略 其他符号用英文符号如!?-)\n"
            + "务必注意食材搭配!!!! 对于不合理搭配应该降低分数\n"
            + "生成的菜肴参数准则:\n"
            + "1.品质系数需要严格判断\n"
            + "2.必须参考user发送的食材饱食度和饱和度 成品的饱食度饱和度参考:如果品质在普通偏差时可食用次数*饱和度/饱食度=总饱和度/饱食度 四舍五入 如果品质好则适当增加 差则减少 药水时间也这样考虑 等级的话只看食用次数和投料数量 比如投料多但是使用次数少=浓缩 等级提升\n"
            + "3.品质过差可以适当增加debuff效果\n"
            + "4.食材种类数量与分数上限限制(硬性规则不可违反):\n"
            + "  1种食材: qualityScore最高85\n"
            + "  2种食材: qualityScore最高92\n"
            + "  3种及以上食材: qualityScore无上限\n"
            + "5.buff效果(effects)条件: 食材种类>=2 且 qualityScore>=90 才可考虑追加正面buff 否则禁止追加正面buff\n"
            + "  追加说明: 此处buff是在原有药水效果(potionEffects)以及食材buff基础上额外追加的奖励效果 与食材本身的药水/效果无关\n"
            + "  buff强度软性参考(非硬规则 根据食材组合和烹饪情况自由判断):\n"
            + "  85~91=偏弱效果 短时长; 92~97=中等效果 中时长; 98~100=强效果 长时长\n"
            + "  可追加多种不同buff 搭配越丰富的菜肴可追加越多种类 根据食材特性决定风格";

        String userPrompt = gson.toJson(userContent);

        return new String[]{systemPrompt, userPrompt};
    }

    /**
     * 将 AI 返回的 description 字符串按换行符拆分为 lore 行列表喵~
     * 支持 JSON 字符串中的 \\n（字面量两字符）和真正的换行符 \n 两种形式。
     * 输入：description 字符串。
     * 输出：List<String>，每个元素是一行 lore，空行过滤掉。
     */
    public static java.util.List<String> descriptionToLore(String description) {
        // 喵~防御：null 或空字符串直接返回空列表喵
        if (description == null || description.isEmpty()) return java.util.Collections.emptyList();
        java.util.List<String> lines = new java.util.ArrayList<>();
        // 同时支持字面量 \\n（AI有时输出转义序列）和真实换行符 \n 喵
        for (String line : description.split("\\\\n|\\n")) {
            if (line != null && !line.isBlank()) lines.add(line);
        }
        return lines;
    }
}
