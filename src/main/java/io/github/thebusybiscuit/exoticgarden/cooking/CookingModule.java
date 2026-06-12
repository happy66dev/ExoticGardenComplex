package io.github.thebusybiscuit.exoticgarden.cooking;

import io.github.thebusybiscuit.exoticgarden.ExoticGarden;
import io.github.thebusybiscuit.exoticgarden.cooking.block.CuttingBoardBlock;
import io.github.thebusybiscuit.exoticgarden.cooking.block.StoveBlock;
import io.github.thebusybiscuit.exoticgarden.cooking.calculator.DonenessCalculator;
import io.github.thebusybiscuit.exoticgarden.cooking.calculator.StandardDonenessCalculator;
import io.github.thebusybiscuit.exoticgarden.cooking.config.FuelConfig;
import io.github.thebusybiscuit.exoticgarden.cooking.config.IngredientConfig;
import io.github.thebusybiscuit.exoticgarden.cooking.config.SeasoningConfig;
import io.github.thebusybiscuit.exoticgarden.cooking.interaction.*;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

public class CookingModule {

    public static void initialize(ExoticGarden plugin) {
        Logger logger = plugin.getLogger();

        FuelConfig fuelConfig = new FuelConfig(logger);
        IngredientConfig ingredientConfig = new IngredientConfig(logger);
        SeasoningConfig seasoningConfig = new SeasoningConfig(logger);

        saveResourceIfMissing(plugin, "fuels.yml");
        saveResourceIfMissing(plugin, "ingredients.yml");
        saveResourceIfMissing(plugin, "seasonings.yml");

        Map<String, FuelConfig.FuelData> fuels = fuelConfig.loadAll(
            new File(plugin.getDataFolder(), "fuels.yml"), "");
        Map<String, IngredientConfig.IngredientData> ingredients = ingredientConfig.loadAll(
            new File(plugin.getDataFolder(), "ingredients.yml"), "");
        Map<String, SeasoningConfig.SeasoningData> seasonings = seasoningConfig.loadAll(
            new File(plugin.getDataFolder(), "seasonings.yml"), "");

        String apiKey = plugin.getConfig().getString("cooking.ai_api_key", "");
        String baseUrl = plugin.getConfig().getString("cooking.ai_base_url", "https://api.openai.com/v1");
        String model = plugin.getConfig().getString("cooking.ai_model", "gpt-4o-mini");

        Map<String, DonenessCalculator> calculators = new HashMap<>();
        calculators.put("standard", new StandardDonenessCalculator());

        List<StoveInteractionHandler> stoveHandlers = List.of(
            new SpatulaInteractionHandler(),
            new BowlInteractionHandler(plugin, fuels, ingredients, seasonings, apiKey, baseUrl, model),
            new FuelInteractionHandler(fuels),
            new SeasoningInteractionHandler(seasonings),
            new IngredientInteractionHandler(ingredients),
            new ClearFuelInteractionHandler()
        );

        ItemGroup cookingGroup = new ItemGroup(
            new NamespacedKey(plugin, "cooking"),
            new CustomItemStack(Material.CAMPFIRE, "&6烹饪系统")
        );

        SlimefunItemStack stoveStack = new SlimefunItemStack(
            "EG_COOKING_STOVE",
            Material.BLAST_FURNACE,
            "&6烹饪灶台",
            "&7放置燃料和食材进行烹饪",
            "&7右键交互以操作"
        );
        StoveBlock stove = new StoveBlock(
            cookingGroup, stoveStack, RecipeType.ENHANCED_CRAFTING_TABLE,
            new ItemStack[] {
                new ItemStack(Material.COBBLESTONE), new ItemStack(Material.IRON_INGOT), new ItemStack(Material.COBBLESTONE),
                new ItemStack(Material.IRON_INGOT), new ItemStack(Material.CAMPFIRE), new ItemStack(Material.IRON_INGOT),
                new ItemStack(Material.COBBLESTONE), new ItemStack(Material.IRON_INGOT), new ItemStack(Material.COBBLESTONE)
            },
            stoveHandlers
        );
        stove.register(plugin);

        SlimefunItemStack boardStack = new SlimefunItemStack(
            "EG_CUTTING_BOARD",
            Material.OAK_SLAB,
            "&e砧板",
            "&7放置食材，使用刀具切割",
            "&7潜行右键取回物品"
        );
        CuttingBoardBlock board = new CuttingBoardBlock(
            cookingGroup, boardStack, RecipeType.ENHANCED_CRAFTING_TABLE,
            new ItemStack[] {
                null, null, null,
                new ItemStack(Material.OAK_SLAB), new ItemStack(Material.OAK_SLAB), new ItemStack(Material.OAK_SLAB),
                null, null, null
            }
        );
        board.register(plugin);

        SlimefunItemStack knifeStack = new SlimefunItemStack(
            "EG_COOKING_KNIFE",
            Material.IRON_SWORD,
            "&f烹饪刀",
            "&7右键砧板上的食材进行切割",
            "&7潜行右键取回食材"
        );
        ItemMeta knifeMeta = knifeStack.getItemMeta();
        if (knifeMeta != null) {
            knifeMeta.getPersistentDataContainer()
                .set(new NamespacedKey("cooking", "item_type"),
                     PersistentDataType.STRING, "KNIFE");
            knifeStack.setItemMeta(knifeMeta);
        }
        KnifeItem knife = new KnifeItem(
            cookingGroup, knifeStack, RecipeType.ENHANCED_CRAFTING_TABLE,
            new ItemStack[] {
                null, new ItemStack(Material.IRON_INGOT), null,
                null, new ItemStack(Material.IRON_INGOT), null,
                null, new ItemStack(Material.STICK), null
            },
            plugin
        );
        knife.register(plugin);

        SlimefunItemStack spatulaStack = new SlimefunItemStack(
            "EG_COOKING_SPATULA",
            Material.IRON_SHOVEL,
            "&b烹饪锅铲",
            "&7右键灶台翻面，加速烹饪",
            "&7右键砧板搅拌制酱"
        );
        ItemMeta spatulaMeta = spatulaStack.getItemMeta();
        if (spatulaMeta != null) {
            spatulaMeta.getPersistentDataContainer()
                .set(new NamespacedKey("cooking", "item_type"),
                     PersistentDataType.STRING, "SPATULA");
            spatulaStack.setItemMeta(spatulaMeta);
        }
        SpatulaItem spatula = new SpatulaItem(
            cookingGroup, spatulaStack, RecipeType.ENHANCED_CRAFTING_TABLE,
            new ItemStack[] {
                null, new ItemStack(Material.IRON_INGOT), null,
                null, new ItemStack(Material.IRON_INGOT), null,
                null, new ItemStack(Material.STICK), null
            },
            plugin, ingredients
        );
        spatula.register(plugin);

        new StoveTickTask(fuels, ingredients, seasonings, calculators, stove)
            .runTaskTimer(plugin, 2L, 2L);

        plugin.getServer().getPluginManager().registerEvents(new DishConsumptionListener(), plugin);
    }

    private static void saveResourceIfMissing(ExoticGarden plugin, String name) {
        File file = new File(plugin.getDataFolder(), name);
        if (file.exists()) return;
        try (InputStream in = plugin.getResource(name)) {
            if (in != null) Files.copy(in, file.toPath());
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to save resource: " + name);
        }
    }
}
