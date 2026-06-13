package io.github.thebusybiscuit.exoticgarden.cooking;

import io.github.thebusybiscuit.exoticgarden.ExoticGarden;
import io.github.thebusybiscuit.exoticgarden.cooking.block.CuttingBoardBlock;
import io.github.thebusybiscuit.exoticgarden.cooking.block.StoveBlock;
import io.github.thebusybiscuit.exoticgarden.cooking.interaction.BowlInteractionHandler;
import io.github.thebusybiscuit.exoticgarden.cooking.interaction.ClearFuelInteractionHandler;
import io.github.thebusybiscuit.exoticgarden.cooking.interaction.FuelInteractionHandler;
import io.github.thebusybiscuit.exoticgarden.cooking.interaction.IngredientInteractionHandler;
import io.github.thebusybiscuit.exoticgarden.cooking.interaction.SeasoningInteractionHandler;
import io.github.thebusybiscuit.exoticgarden.cooking.interaction.SpatulaInteractionHandler;
import io.github.thebusybiscuit.exoticgarden.cooking.calculator.DonenessCalculator;
import io.github.thebusybiscuit.exoticgarden.cooking.calculator.StandardDonenessCalculator;
import io.github.thebusybiscuit.exoticgarden.cooking.config.FuelConfig;
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
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.Scanner;

public class CookingModule {

    public static void initialize(ExoticGarden plugin) {
        Map<String, FuelConfig.FuelData> fuels = loadConfigs(plugin);
        Map<String, IngredientConfig.IngredientData> ingredients = loadIngredients(plugin);
        Map<String, SeasoningConfig.SeasoningData> seasonings = loadSeasonings(plugin);

        ItemGroup cookingGroup = buildItemGroup(plugin);
        Map<String, DonenessCalculator> calculators = buildCalculators();
        List<StoveInteractionHandler> stoveHandlers = buildHandlers(fuels, ingredients, seasonings);

        StoveBlock stove = registerStove(plugin, cookingGroup, stoveHandlers);
        registerBoard(plugin, cookingGroup);
        registerKnife(plugin, cookingGroup);
        registerSpatula(plugin, cookingGroup, ingredients);

        new StoveTickTask(fuels, ingredients, seasonings, calculators, stove).runTaskTimer(plugin, 2L, 2L);
        plugin.getServer().getPluginManager().registerEvents(new FoodTagListener(ingredients), plugin);
        plugin.getLogger().info("[Cooking] StoveTickTask 已启动");
        // Temporarily disable dish consumption custom logic.
        // plugin.getServer().getPluginManager().registerEvents(new DishConsumptionListener(), plugin);
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

    private static void registerKnife(ExoticGarden plugin, ItemGroup group) {
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
            }, plugin).register(plugin);
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
