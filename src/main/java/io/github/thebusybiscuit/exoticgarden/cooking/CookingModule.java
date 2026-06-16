package io.github.thebusybiscuit.exoticgarden.cooking;

import io.github.thebusybiscuit.exoticgarden.ExoticGarden;
import io.github.thebusybiscuit.exoticgarden.FoodListener;
import io.github.thebusybiscuit.exoticgarden.cooking.block.CuttingBoardBlock;
import io.github.thebusybiscuit.exoticgarden.cooking.block.StoveBlock;
import io.github.thebusybiscuit.exoticgarden.cooking.interaction.BowlInteractionHandler;
import io.github.thebusybiscuit.exoticgarden.cooking.interaction.ClearFuelInteractionHandler;
import io.github.thebusybiscuit.exoticgarden.cooking.interaction.FuelInteractionHandler;
import io.github.thebusybiscuit.exoticgarden.cooking.interaction.IngredientInteractionHandler;
import io.github.thebusybiscuit.exoticgarden.cooking.interaction.SeasoningInteractionHandler;
import io.github.thebusybiscuit.exoticgarden.cooking.calculator.DonenessCalculator;
import io.github.thebusybiscuit.exoticgarden.cooking.calculator.StandardDonenessCalculator;
import io.github.thebusybiscuit.exoticgarden.cooking.config.FuelConfig;
import io.github.thebusybiscuit.exoticgarden.cooking.config.FoodsConfig;
import io.github.thebusybiscuit.exoticgarden.cooking.config.IngredientConfig;
import io.github.thebusybiscuit.exoticgarden.cooking.config.SeasoningConfig;
import io.github.thebusybiscuit.exoticgarden.cooking.interaction.StoveInteractionHandler;
import io.github.thebusybiscuit.exoticgarden.cooking.item.KnifeItem;
import io.github.thebusybiscuit.exoticgarden.cooking.item.SpatulaItem;
import io.github.thebusybiscuit.exoticgarden.cooking.task.StoveTickTask;
import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.libraries.dough.items.CustomItemStack;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import java.util.Scanner;

public class CookingModule {

    // 保存灶台实例引用，用于关服时清除内存数据喵
    private static StoveBlock stoveInstance;
    // 保存食材配置 map，供 CuttingBoardBlock 取回时刷新 lore 使用喵
    private static Map<String, io.github.thebusybiscuit.exoticgarden.cooking.config.IngredientConfig.IngredientData> ingredientsMap;

    /** 获取食材配置 map，用于刷新 lore 等场景喵 */
    public static Map<String, io.github.thebusybiscuit.exoticgarden.cooking.config.IngredientConfig.IngredientData> getIngredients() {
        return ingredientsMap;
    }

    public static void initialize(ExoticGarden plugin) {
        Map<String, FuelConfig.FuelData> fuels = loadConfigs(plugin);
        Map<String, IngredientConfig.IngredientData> ingredients = loadIngredients(plugin);
        ingredientsMap = ingredients; // 保存引用供外部访问喵
        Map<String, SeasoningConfig.SeasoningData> seasonings = loadSeasonings(plugin);
        FoodsConfig foodsConfig = loadFoods(plugin);

        // 加载三个 map 后做交叉 key 冲突检测，发现冲突立即报错喵
        checkDuplicate(plugin, fuels, seasonings, "fuels.yml", "seasonings.yml");
        checkDuplicate(plugin, fuels, ingredients, "fuels.yml", "ingredients.yml");
        checkDuplicate(plugin, seasonings, ingredients, "seasonings.yml", "ingredients.yml");

        ItemGroup cookingGroup = buildItemGroup(plugin);
        Map<String, DonenessCalculator> calculators = buildCalculators();
        List<StoveInteractionHandler> stoveHandlers = buildHandlers(fuels, ingredients, seasonings);

        stoveInstance = registerStove(plugin, cookingGroup, stoveHandlers);
        registerBoard(plugin, cookingGroup);
        registerKnife(plugin, cookingGroup, ingredients);
        registerSpatula(plugin, cookingGroup, ingredients);

        new StoveTickTask(fuels, ingredients, seasonings, calculators, stoveInstance).runTaskTimer(plugin, 2L, 2L);
        // 传入 foodsConfig，让 FoodTagListener 从配置读取黑名单和保质期喵
        plugin.getServer().getPluginManager().registerEvents(new FoodTagListener(ingredients, foodsConfig), plugin);
        plugin.getLogger().info("[Cooking] StoveTickTask 已启动");
        // 喵~砧板数据已在ExoticGarden.loadCuttingBoards()中加载，这里不需要再重建了
        // Temporarily disable dish consumption custom logic.
        plugin.getServer().getPluginManager().registerEvents(new DishConsumptionListener(ingredients), plugin);
        // 喵~头颅食物(EGPlant)右键食用也受过期机制影响
        plugin.getServer().getPluginManager().registerEvents(new FoodListener(plugin, ingredients), plugin);
    }

    private static Map<String, FuelConfig.FuelData> loadConfigs(ExoticGarden plugin) {
        Logger logger = plugin.getLogger();
        saveResourceIfMissing(plugin, "fuels.yml");
        return new FuelConfig(logger).loadAll(new File(plugin.getDataFolder(), "fuels.yml"), "");
    }

    private static Map<String, IngredientConfig.IngredientData> loadIngredients(ExoticGarden plugin) {
        Logger logger = plugin.getLogger();
        saveResourceIfMissing(plugin, "ingredients.yml");
        return new IngredientConfig(logger).loadAll(new File(plugin.getDataFolder(), "ingredients.yml"), "");
    }

    private static Map<String, SeasoningConfig.SeasoningData> loadSeasonings(ExoticGarden plugin) {
        Logger logger = plugin.getLogger();
        saveResourceIfMissing(plugin, "seasonings.yml");
        return new SeasoningConfig(logger).loadAll(new File(plugin.getDataFolder(), "seasonings.yml"), "");
    }

    /**
     * 加载 foods.yml 配置文件喵~
     * 输入：plugin 实例，用于获取数据目录和日志器
     * 输出：FoodsConfig 对象，包含黑名单和通用保质期配置
     */
    private static FoodsConfig loadFoods(ExoticGarden plugin) {
        // 复制默认 foods.yml 到插件数据目录（如不存在）喵
        saveResourceIfMissing(plugin, "foods.yml");
        // 创建 FoodsConfig，从文件读取黑名单和保质期喵
        return new FoodsConfig(new File(plugin.getDataFolder(), "foods.yml"), plugin.getLogger());
    }

    /**
     * 交叉 key 冲突检测：同一个 key 不能同时出现在两个配置 map 中喵~
     * 输入：plugin-插件实例, a-第一个配置 map, b-第二个配置 map,
     *       fileA-a 对应的文件名, fileB-b 对应的文件名
     * 边界：a 或 b 为空 map 时必然无冲突喵
     * 异常：发现冲突时 SEVERE 日志并抛出 IllegalStateException 喵
     */
    private static void checkDuplicate(ExoticGarden plugin, Map<String, ?> a, Map<String, ?> b,
                                       String fileA, String fileB) {
        // 用交集找出同时存在于两个 map 的 key 喵
        Set<String> overlap = new HashSet<>(a.keySet());
        overlap.retainAll(b.keySet());
        // 喵~防御：有交集时报 SEVERE 并抛出，避免运行时混淆喵
        if (!overlap.isEmpty()) {
            plugin.getLogger().severe("[Cooking] 配置冲突！以下物品同时在 " + fileA + " 和 " + fileB + ": " + overlap);
            throw new IllegalStateException("烹饪系统配置冲突，请检查 " + fileA + " 和 " + fileB);
        }
    }

    private static ItemGroup buildItemGroup(ExoticGarden plugin) {
        return new ItemGroup(
            new NamespacedKey(plugin, "cooking"),
            new CustomItemStack(Material.CAMPFIRE, "&6烹饪系统")
        );
    }

    private static Map<String, DonenessCalculator> buildCalculators() {
        Map<String, DonenessCalculator> calculators = new HashMap<>();
        calculators.put("standard", new StandardDonenessCalculator());
        return calculators;
    }

    private static List<StoveInteractionHandler> buildHandlers(
            Map<String, FuelConfig.FuelData> fuels,
            Map<String, IngredientConfig.IngredientData> ingredients,
            Map<String, SeasoningConfig.SeasoningData> seasonings) {
        return List.of(
            new BowlInteractionHandler(fuels, ingredients, seasonings),
            new FuelInteractionHandler(fuels),
            new SeasoningInteractionHandler(seasonings),
            new IngredientInteractionHandler(ingredients),
            new ClearFuelInteractionHandler()
        );
    }

    private static StoveBlock registerStove(ExoticGarden plugin, ItemGroup group,
                                            List<StoveInteractionHandler> handlers) {
        SlimefunItemStack stack = new SlimefunItemStack("EG_COOKING_STOVE", Material.CAMPFIRE,
            "&6烹饪灶台", "&7放置燃料和食材进行烹饪", "&7右键交互以操作");
        StoveBlock stove = new StoveBlock(group, stack, RecipeType.ENHANCED_CRAFTING_TABLE,
            new ItemStack[] {
                new ItemStack(Material.COBBLESTONE), new ItemStack(Material.IRON_INGOT), new ItemStack(Material.COBBLESTONE),
                new ItemStack(Material.IRON_INGOT),  new ItemStack(Material.CAMPFIRE),   new ItemStack(Material.IRON_INGOT),
                new ItemStack(Material.COBBLESTONE), new ItemStack(Material.IRON_INGOT), new ItemStack(Material.COBBLESTONE)
            }, handlers, plugin);
        stove.register(plugin);
        return stove;
    }

    private static void registerBoard(ExoticGarden plugin, ItemGroup group) {
        SlimefunItemStack stack = new SlimefunItemStack("EG_CUTTING_BOARD", Material.CRAFTING_TABLE,
            "&e砧板", "&7放置食材，使用刀具切割", "&7潜行右键取回物品");
        new CuttingBoardBlock(group, stack, RecipeType.ENHANCED_CRAFTING_TABLE,
            new ItemStack[] {
                null, null, null,
                new ItemStack(Material.OAK_SLAB), new ItemStack(Material.OAK_SLAB), new ItemStack(Material.OAK_SLAB),
                null, null, null
            }, plugin).register(plugin);
    }

    private static void registerKnife(ExoticGarden plugin, ItemGroup group,
                                       Map<String, IngredientConfig.IngredientData> ingredients) {
        SlimefunItemStack stack = new SlimefunItemStack("EG_COOKING_KNIFE", Material.IRON_SWORD,
            "&f烹饪刀", "&7右键砧板上的食材进行切割", "&7潜行右键取回食材");
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(CookingKeys.ITEM_TYPE, PersistentDataType.STRING, "KNIFE");
            stack.setItemMeta(meta);
        }
        new KnifeItem(group, stack, RecipeType.ENHANCED_CRAFTING_TABLE,
            new ItemStack[] {
                null, new ItemStack(Material.IRON_INGOT), null,
                null, new ItemStack(Material.IRON_INGOT), null,
                null, new ItemStack(Material.STICK),      null
            }, plugin, ingredients).register(plugin);
    }

    private static void registerSpatula(ExoticGarden plugin, ItemGroup group,
                                        Map<String, IngredientConfig.IngredientData> ingredients) {
        SlimefunItemStack stack = new SlimefunItemStack("EG_COOKING_SPATULA", Material.IRON_SHOVEL,
            "&b烹饪锅铲", "&7右键灶台翻面，加速烹饪", "&7右键砧板搅拌制酱");
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(CookingKeys.ITEM_TYPE, PersistentDataType.STRING, "SPATULA");
            stack.setItemMeta(meta);
        }
        new SpatulaItem(group, stack, RecipeType.ENHANCED_CRAFTING_TABLE,
            new ItemStack[] {
                null, new ItemStack(Material.IRON_INGOT), null,
                null, new ItemStack(Material.IRON_INGOT), null,
                null, new ItemStack(Material.STICK),      null
            }, plugin, ingredients).register(plugin);
    }

    /**
     * 关服时清除所有已加载的灶台运行时数据，并清空对应篝火方块槽位，防止重启时物品掉落喵~
     * 调用时机：ExoticGarden.onDisable()
     */
    public static void clearStoveData() {
        // 喵~防御：stoveInstance未初始化时跳过喵
        if (stoveInstance == null) return;
        for (java.util.Map.Entry<org.bukkit.Location, io.github.thebusybiscuit.exoticgarden.cooking.state.StoveState> entry
                : stoveInstance.activeStoves.entrySet()) {
            org.bukkit.Location loc = entry.getKey();
            // 喵~防御：区块可能已卸载，只处理已加载的区块喵
            if (loc.getWorld() == null || !loc.getWorld().isChunkLoaded(loc.getBlockX() >> 4, loc.getBlockZ() >> 4)) continue;
            org.bukkit.block.Block block = loc.getBlock();
            // 喵~防御：方块必须是营火才处理，防止误清其他方块喵
            if (!(block.getState() instanceof org.bukkit.block.Campfire campfire)) continue;
            // 清空篝火所有槽位，防止服务器重启时物品掉落喵
            for (int i = 0; i < 4; i++) {
                campfire.setItem(i, null);
            }
            campfire.update(true, false);
        }
        stoveInstance.activeStoves.clear();
    }

    // 喵~此方法已废弃，砧板数据加载逻辑已移到ExoticGarden.loadCuttingBoards()
    // 保留注释供参考：原来用于启动后20tick扫描ArmorStand重建缓存
    /*
    private static void rebuildBoardDisplays(ExoticGarden plugin) {
        CuttingBoardBlock.boardDisplays.clear();
        for (org.bukkit.World world : plugin.getServer().getWorlds()) {
            for (org.bukkit.entity.Entity entity : world.getEntitiesByClass(org.bukkit.entity.ArmorStand.class)) {
                org.bukkit.entity.ArmorStand stand = (org.bukkit.entity.ArmorStand) entity;
                if (stand.getPersistentDataContainer().has(
                        io.github.thebusybiscuit.exoticgarden.cooking.CookingKeys.BOARD_ITEM,
                        org.bukkit.persistence.PersistentDataType.STRING)) {
                    Location spawnLoc = stand.getLocation();
                    Location blockLoc = new Location(spawnLoc.getWorld(),
                        Math.floor(spawnLoc.getX()), Math.floor(spawnLoc.getY() + 0.3), Math.floor(spawnLoc.getZ()));
                    CuttingBoardBlock.boardDisplays.put(blockLoc, stand);
                }
            }
        }
        plugin.getLogger().info("[Cooking] 重建砧板盔甲架映射: " + CuttingBoardBlock.boardDisplays.size() + " 个");
    }
    */

    private static void saveResourceIfMissing(ExoticGarden plugin, String name) {
        File file = new File(plugin.getDataFolder(), name);
        int jarVersion = readJarVersion(plugin, name);
        int fileVersion = readFileVersion(file);
        if (file.exists() && jarVersion <= fileVersion) return;

        if (file.exists()) {
            File backup = new File(file.getParentFile(), name + ".bak");
            try { java.nio.file.Files.move(file.toPath(), backup.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING); }
            catch (Exception ignored) {}
            plugin.getLogger().info("[Cooking] 配置文件 " + name + " 已更新 (v" + fileVersion + " -> v" + jarVersion + ")");
        }

        try (InputStream in = plugin.getResource(name)) {
            if (in == null) {
                plugin.getLogger().severe("[Cooking] 资源文件未找到: " + name + "，请检查 jar 是否完整");
                return;
            }
            Files.copy(in, file.toPath());
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to save resource: " + name + " - " + e.getMessage());
        }
    }

    private static int readJarVersion(ExoticGarden plugin, String name) {
        try (InputStream in = plugin.getResource(name)) {
            if (in == null) return 0;
            java.util.Scanner sc = new java.util.Scanner(in, "UTF-8");
            while (sc.hasNextLine()) {
                String line = sc.nextLine().trim();
                if (line.startsWith("# config_version:")) {
                    return Integer.parseInt(line.substring("# config_version:".length()).trim());
                }
            }
        } catch (Exception ignored) {}
        return 0;
    }

    private static int readFileVersion(File file) {
        if (!file.exists()) return 0;
        try (java.util.Scanner sc = new java.util.Scanner(file, "UTF-8")) {
            while (sc.hasNextLine()) {
                String line = sc.nextLine().trim();
                if (line.startsWith("# config_version:")) {
                    return Integer.parseInt(line.substring("# config_version:".length()).trim());
                }
            }
        } catch (Exception ignored) {}
        return 0;
    }
}
