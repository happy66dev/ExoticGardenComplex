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
        public final double weight;
        public final double foodPoints;
        public final double saturation;
        // 给 AI 的特殊属性提示，帮助 AI 更准确识别食材风味和用途喵
        public final String hint;
        // 该食材烹饪期间经历的燃料风味效果列表（中文显示名），影响菜肴的烟熏/燃料风味描述喵
        public final List<String> fuelEffects;

        public IngredientInfo(String name, String state, int doneness,
                              double weight,
                              double foodPoints, double saturation, String hint,
                              List<String> fuelEffects) {
            this.name = name;
            this.state = state;
            this.doneness = doneness;
            this.weight = weight;
            this.foodPoints = foodPoints;
            this.saturation = saturation;
            // 喵~防御：hint 为 null 时存为空字符串，避免 NPE 喵
            this.hint = hint != null ? hint : "";
            // 喵~防御：fuelEffects 为 null 时存为空列表，避免 NPE 喵
            this.fuelEffects = fuelEffects != null ? fuelEffects : new java.util.ArrayList<>();
        }
    }

    public static class SeasoningInfo {
        public final String name;
        public final Integer progress;
        public final double mlAmount;
        // 给 AI 的特殊属性提示，帮助 AI 更准确识别调料风味和用途喵
        public final String hint;

        public SeasoningInfo(String name, Integer progress, double mlAmount, String hint) {
            this.name = name;
            this.progress = progress;
            this.mlAmount = mlAmount;
            // 喵~防御：hint 为 null 时存为空字符串，避免 NPE 喵
            this.hint = hint != null ? hint : "";
        }
    }

    public static class DishResult {
        public final String name;
        public final int servings;
        // 品质中文形容词，如"良好"/"优秀"，通过 qualityToCoefficient 转为系数喵
        public final String quality;
        public final double qualityCoefficient;
        public final List<String> effects;
        public final String description;
        // 菜肴饱食度（hunger points），缺省0喵
        public final int hunger;
        // 菜肴饱和度，缺省0喵
        public final double saturation;

        public DishResult(String name, int servings, String quality,
                          List<String> effects, String description,
                          int hunger, double saturation) {
            this.name = name;
            this.servings = servings;
            this.quality = quality;
            this.qualityCoefficient = qualityToCoefficient(quality);
            this.effects = effects;
            this.description = description;
            this.hunger = hunger;
            this.saturation = saturation;
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
            List<org.bukkit.potion.PotionEffect> potionEffects) {
        Gson gson = new Gson();
        JsonObject userContent = new JsonObject();

        JsonArray ingArr = new JsonArray();
        for (IngredientInfo info : ingredientInfos) {
            JsonObject obj = new JsonObject();
            obj.addProperty("name", info.name);
            obj.addProperty("state", info.state);
            obj.addProperty("doneness", info.doneness);
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
            + "输入 JSON 字段说明:\n"
            + "- ingredients: [{name:\"食材名\",state:\"完整/切片/切丁/酱汁\","
            + "doneness:熟度百分比(不同食材种类不同 参考值:非牛肉类:0~60生 60~90未熟透 90~125成熟 125~180老了 180+焦了 "
            + "牛肉类:0%~10%不熟 10%~25%三分熟 25%~50%五分 50%~75%七分 75~115%全熟 115%~150%老了 150%+焦了 "
            + "菜类(可能有些菜可以生吃 你根据现实知识自行辨别):0%~70%生 70%~90%嫩 90%~110%熟 110%~140%老 140%+糊 "
            + "果类0~50%可以 50~100%熟了 100%~150%烂了 150%+焦了),"
            + "weight:克,foodPoints:饱食度,saturation:饱和度,"
            + "hint(可选):该食材的特殊属性或用途,fuelEffects:烹饪期间经历的燃料风味（可为空）}]\n"
            + "- seasonings: [{name:\"调料名\",progress:渗入度百分比(0-200,100=完美,200%变味了),mlAmount:液体毫升量,hint(可选):该调料的特殊属性或用途}]\n"
            + "- fuelEffects: [\"燃料风味\"]\n"
            + "- waterMl: 当前剩余水量(已蒸发扣除的剩余), oilMl: 油量(毫升)\n"
            + "- waterSources: [\"水来源 可能蒸发了\"] 如[\"柠檬汁\",\"牛奶\",\"料酒\",\"水\"]等\n"
            + "- totalWeight: 食材+辅料总克重\n"
            + "- potionEffects(可选): [{effect:效果名,amplifier:等级,durationSeconds:持续秒}]\n"
            + "- 如有hint字段则表示该食材/调料的特殊属性或用途，应影响菜肴命名和描述\n"
            + "- 食材的fuelEffects字段说明: 该食材烹饪期间经历的燃料风味列表;\n"
            + "  数量1~2=少量风味 适当, 3=较多 品质略降, 3+超量则考虑降低品质;\n"
            + "  负面风味(如'岩浆淬炼'用于不适合高温的食材)越多品质越低\n"
            + "烹饪方向推断(根据水量油量):\n"
            + "- 无水无油->烧烤/干烧(食材适合烤则品质不差,否则干烧品质差)\n"
            + "- 有水无油->炖/煮/蒸  有油无水->煎/炒/炸  有水有油->汤/烩/焖\n"
            + "- 水或油过多(判断按照正常思维来)->影响品质判断\n"
            + "品质: 彻底失败/很差/差/普通/良好/优秀/完美\n"
            + "servings: 根据食材克重和水量油量和加工方式估算可吃次数(1-10)\n"
            + "hunger: 每次食用恢复的饱食度(整数,参考食材foodPoints之和按品质调整,缺省0)\n"
            + "saturation: 每次食用恢复的饱和度(浮点,参考食材saturation之和按品质调整,缺省0.0)\n"
            + "name: 品质与菜名自然结合, 正常食材贴合菜名, 猎奇加工/食材组合(比如刻意烤焦 刻意加很多盐 食材过期等)允许猎奇名\n"
            + "description: 25-200字风味描述, 必须含§颜色符进行lore色彩搭配, 换行使用JSON标准\\n(即JSON字符串中的\\n转义符)每行不超过25字 和分割线(&7---------)\n"
            + "effects: 可选, 仅特殊食材或含药水或完美烹饪时出现, 支持多个效果, 格式[\"药水ID:等级:秒\",\"药水ID2:等级:秒\"];\n"
            + "  注意:等级从0开始 即0=1级 1=2级 以此类推 返回1实际为2级 请按实际期望等级-1填写\n\n"
            + "必须返回纯JSON，必须包含以下所有字段（无论如何不能省略）：\n"
            + "{\"name\":\"菜名（缺省用'未知菜肴'）\","
            + "\"servings\":份数整数(缺省1),"
            + "\"quality\":\"品质中文形容词(缺省'普通')\","
            + "\"hunger\":饱食度整数(缺省0),"
            + "\"saturation\":饱和度浮点(缺省0.0),"
            + "\"description\":\"描述字符串（缺省用'无描述'）\"}\n"
            + "如有effects则加\"effects\":[\"SPEED:1:600\",\"REGENERATION:1:200\"], 可以多个, 无则省略该字段。\n"
            + "description 书写规范: 不使用中文标点符号(逗号用空格代替 句号省略 其他符号用英文符号如!?-)\n"
            + "生成的菜肴参数准则:\n"
            + "1.品质系数需要严格判断\n"
            + "2.必须参考user发送的食材饱食度和饱和度 成品的饱食度饱和度参考:如果品质在普通偏差时可食用次数*饱和度/饱食度=总饱和度/饱食度 四舍五入 如果品质好则适当增加 差则减少 药水时间也这样考虑 等级的话只看食用次数和投料数量 比如投料多但是使用次数少=浓缩 等级提升\n"
            + "3.品质过差可以适当增加debuff效果";

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
