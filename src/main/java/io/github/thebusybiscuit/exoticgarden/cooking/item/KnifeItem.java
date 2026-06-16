package io.github.thebusybiscuit.exoticgarden.cooking.item;

import io.github.thebusybiscuit.exoticgarden.cooking.CookingKeys;
import io.github.thebusybiscuit.exoticgarden.cooking.block.CuttingBoardBlock;
import io.github.thebusybiscuit.exoticgarden.cooking.config.IngredientConfig;
import io.github.thebusybiscuit.exoticgarden.cooking.state.FoodState;
import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class KnifeItem extends SlimefunItem {

    // 保存插件引用，用于调用持久化方法喵
    private final JavaPlugin pluginInstance;

    public KnifeItem(ItemGroup group, SlimefunItemStack item,
                     RecipeType recipeType, ItemStack[] recipe, JavaPlugin plugin,
                     Map<String, IngredientConfig.IngredientData> ingredients) {
        super(group, item, recipeType, recipe);
        this.pluginInstance = plugin;

        plugin.getServer().getPluginManager().registerEvents(new org.bukkit.event.Listener() {

            @EventHandler(priority = EventPriority.LOW, ignoreCancelled = false)
            public void onInteract(PlayerInteractEvent e) {
                if (e.getHand() != EquipmentSlot.HAND) return;
                if (e.getAction() != Action.RIGHT_CLICK_BLOCK && e.getAction() != Action.RIGHT_CLICK_AIR) return;

                Player player = e.getPlayer();
                ItemStack hand = player.getInventory().getItemInMainHand();
                if (hand.getItemMeta() == null) return;
                PersistentDataContainer handPdc = hand.getItemMeta().getPersistentDataContainer();
                if (!"KNIFE".equals(handPdc.get(CookingKeys.ITEM_TYPE, PersistentDataType.STRING))) return;

                Location boardLoc = findNearbyBoard(player);
                if (boardLoc == null) return;

                e.setCancelled(true);

                ItemStack held = CuttingBoardBlock.getStoredItem(boardLoc);
                if (held == null) return;

                if (player.isSneaking()) {
                    // 取回前刷新lore，确保显示最新状态喵
                    org.bukkit.inventory.meta.ItemMeta heldMetaReturn = held.getItemMeta();
                    if (heldMetaReturn != null) {
                        PersistentDataContainer heldPdcReturn = heldMetaReturn.getPersistentDataContainer();
                        String rawStateReturn = heldPdcReturn.get(CookingKeys.FOOD_STATE, PersistentDataType.STRING);
                        FoodState stateReturn = FoodState.WHOLE;
                        if (rawStateReturn != null) {
                            try { stateReturn = FoodState.valueOf(rawStateReturn); } catch (IllegalArgumentException ignored) {}
                        }
                        refreshIngredientLore(heldMetaReturn, stateReturn, heldPdcReturn, ingredients);
                        held.setItemMeta(heldMetaReturn);
                    }
                    Map<Integer, ItemStack> leftover = player.getInventory().addItem(held);
                    if (!leftover.isEmpty() && boardLoc.getWorld() != null) {
                        leftover.values().forEach(it -> boardLoc.getWorld().dropItemNaturally(boardLoc, it));
                    }
                    ArmorStand stand = CuttingBoardBlock.boardDisplays.remove(boardLoc);
                    if (stand != null) stand.remove();
                    // 喵~防御：取回物品后清除YAML记录，防止重启时物品复制
                    CuttingBoardBlock.saveCuttingBoardToYaml();
                    return;
                }

                org.bukkit.inventory.meta.ItemMeta heldMeta = held.getItemMeta();
                if (heldMeta == null) return;
                PersistentDataContainer heldPdc = heldMeta.getPersistentDataContainer();

                // 喵~防御：非食材（配置文件无记录）不允许加工，直接提示喵
                String ingIdCheck2 = heldPdc.get(CookingKeys.INGREDIENT_ID, PersistentDataType.STRING);
                if (ingIdCheck2 == null || !ingredients.containsKey(ingIdCheck2)) {
                    player.sendMessage("§c此物品不是烹饪食材，无法加工");
                    return;
                }

                String rawState = heldPdc.get(CookingKeys.FOOD_STATE, PersistentDataType.STRING);
                FoodState current = FoodState.WHOLE;
                if (rawState != null) {
                    try { current = FoodState.valueOf(rawState); } catch (IllegalArgumentException ignored) {}
                }
                FoodState next = advanceState(current, heldPdc, ingredients);
                if (next == current) {
                    // SAUCE终态：已加工到最细喵
                    if (current == FoodState.SAUCE) {
                        player.sendMessage("§c此食材已加工到最细");
                        return;
                    }
                    // DICED研磨进度中：PDC已更新clicks计数，写回砧板；无配置时提示不可加工喵
                    String ingIdCheck = heldPdc.get(CookingKeys.INGREDIENT_ID, PersistentDataType.STRING);
                    IngredientConfig.IngredientData dataCheck = ingIdCheck != null ? ingredients.get(ingIdCheck) : null;
                    if (dataCheck != null && dataCheck.sauceCreation != null) {
                        int curClicks = heldPdc.getOrDefault(CookingKeys.KNIFE_CLICKS, PersistentDataType.INTEGER, 0);
                        held.setItemMeta(heldMeta);
                        CuttingBoardBlock.setStoredItem(boardLoc, held);
                        player.sendMessage("§a研磨中: " + curClicks + "/" + dataCheck.sauceCreation.clicksRequired);
                    } else {
                        player.sendMessage("§c此食材已无法进一步加工");
                    }
                    return;
                }
                heldPdc.set(CookingKeys.FOOD_STATE, PersistentDataType.STRING, next.name());
                // 同步刷新lore — 找到[烹饪食材]行替换，保留其余lore喵
                refreshIngredientLore(heldMeta, next, heldPdc, ingredients);
                held.setItemMeta(heldMeta);
                CuttingBoardBlock.setStoredItem(boardLoc, held);
                // DICED状态且未完成制酱时显示进度，否则显示状态名喵
                if (next == FoodState.DICED) {
                    String ingId2 = heldPdc.get(CookingKeys.INGREDIENT_ID, PersistentDataType.STRING);
                    IngredientConfig.IngredientData data2 = ingId2 != null ? ingredients.get(ingId2) : null;
                    if (data2 != null && data2.sauceCreation != null) {
                        int cur = heldPdc.getOrDefault(CookingKeys.KNIFE_CLICKS, PersistentDataType.INTEGER, 0);
                        player.sendMessage("§a已切丁！研磨中: " + cur + "/" + data2.sauceCreation.clicksRequired);
                    } else {
                        player.sendMessage("§a食材状态: 切丁（已无法进一步加工）");
                    }
                } else {
                    player.sendMessage("§a食材状态: " + stateDisplayName(next));
                }
            }

            @EventHandler(priority = EventPriority.LOW)
            public void onInteractEntity(PlayerInteractAtEntityEvent event) {
                if (event.getHand() != EquipmentSlot.HAND) return;
                if (!(event.getRightClicked() instanceof ArmorStand stand)) return;
                if (!stand.getPersistentDataContainer().has(CookingKeys.BOARD_ITEM, PersistentDataType.STRING)) return;
                event.setCancelled(true);
            }
        }, plugin);
    }

    private Location findNearbyBoard(Player player) {
        Location ploc = player.getLocation();
        Location best = null;
        double bestDist = Double.MAX_VALUE;
        for (Map.Entry<Location, ArmorStand> entry : CuttingBoardBlock.boardDisplays.entrySet()) {
            Location bloc = entry.getKey();
            if (bloc.getWorld() == null || !bloc.getWorld().equals(ploc.getWorld())) continue;
            double d = ploc.distanceSquared(bloc.clone().add(0.5, 0.5, 0.5));
            if (d <= 9.0 && d < bestDist) { bestDist = d; best = bloc; }
        }
        return best;
    }

    // DICED→SAUCE需要按配置的clicks_required次数研磨，其余状态直接推进喵
    private FoodState advanceState(FoodState current, PersistentDataContainer pdc,
                                   Map<String, IngredientConfig.IngredientData> ingredients) {
        return switch (current) {
            case WHOLE  -> FoodState.SLICED;
            case SLICED -> FoodState.DICED;
            case DICED  -> {
                // 从PDC读取食材ID，查sauce_creation配置喵
                String ingId = pdc.get(CookingKeys.INGREDIENT_ID, PersistentDataType.STRING);
                IngredientConfig.IngredientData data = ingId != null ? ingredients.get(ingId) : null;
                // 无sauce_creation配置 → 无法制酱喵
                if (data == null || data.sauceCreation == null) yield current;
                int required = data.sauceCreation.clicksRequired;
                // 读取当前计数，+1后写回PDC喵
                int clicks = pdc.getOrDefault(CookingKeys.KNIFE_CLICKS, PersistentDataType.INTEGER, 0) + 1;
                if (clicks >= required) {
                    // 达到次数，清除计数并升级为SAUCE喵
                    pdc.remove(CookingKeys.KNIFE_CLICKS);
                    yield FoodState.SAUCE;
                }
                // 未达到次数，写回计数但不改变状态喵
                pdc.set(CookingKeys.KNIFE_CLICKS, PersistentDataType.INTEGER, clicks);
                yield current;
            }
            default -> current;
        };
    }

    private String stateDisplayName(FoodState state) {
        return switch (state) {
            case WHOLE -> "完整";
            case SLICED -> "切片";
            case DICED -> "切丁";
            case SAUCE -> "酱汁";
        };
    }

    // 替换lore中的[烹饪食材]行，保留其余行不变喵（public static 供 CuttingBoardBlock 取回时复用）
    public static void refreshIngredientLore(org.bukkit.inventory.meta.ItemMeta meta, FoodState newState,
                                       PersistentDataContainer pdc,
                                       Map<String, IngredientConfig.IngredientData> ingredients) {
        List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
        // 计算营养度和克重显示喵
        String ingId = pdc.get(CookingKeys.INGREDIENT_ID, PersistentDataType.STRING);
        IngredientConfig.IngredientData data = ingId != null ? ingredients.get(ingId) : null;
        double nutrition = data != null ? data.foodPoints + data.saturation : 0;
        String nutritionStr = nutrition > 0
                ? " §e营养度: " + (Math.round(nutrition * 10.0) / 10.0)
                : "";
        double weightGrams = data != null ? data.weightGrams : 100;
        String weightStr = weightGrams > 0 ? " §7(" + (int) weightGrams + "g)" : "";
        String newLine = "§7[烹饪食材] §f" + translateState(newState.name()) + nutritionStr + weightStr;
        // 找到旧[烹饪食材]行替换，找不到则追加喵
        boolean replaced = false;
        for (int i = 0; i < lore.size(); i++) {
            if (lore.get(i).contains("[烹饪食材]")) {
                lore.set(i, newLine);
                replaced = true;
                break;
            }
        }
        if (!replaced) lore.add(newLine);
        meta.setLore(lore);
    }

    private static String translateState(String state) {
        return switch (state) {
            case "WHOLE"  -> "整块";
            case "SLICED" -> "切片";
            case "DICED"  -> "切丁";
            case "SAUCE"  -> "酱料";
            default       -> state;
        };
    }
}
