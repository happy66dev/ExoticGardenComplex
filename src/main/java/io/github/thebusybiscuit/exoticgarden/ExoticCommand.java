package io.github.thebusybiscuit.exoticgarden;

import io.github.thebusybiscuit.exoticgarden.cooking.CookingKeys;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

public class ExoticCommand implements CommandExecutor {

    // 当前插件版本号，每次 commit 时手动更新喵
    public static final String PLUGIN_VERSION = "1.0.29";

    public boolean onCommand(CommandSender commandSender, Command command, String s, String[] strings) {
        // /exotic version 喵~
        if (strings.length >= 1 && strings[0].equalsIgnoreCase("version")) {
            commandSender.sendMessage("§8[§b异域花园§8] §7当前版本: §e" + PLUGIN_VERSION);
            return true;
        }
        // /exotic debug getfood 喵~
        if (strings.length >= 2
                && strings[0].equalsIgnoreCase("debug")
                && strings[1].equalsIgnoreCase("getfood")) {
            if (!hasPermission(commandSender, "exoticgarden.admin")) {
                commandSender.sendMessage("§c你没有权限这么做!");
                return true;
            }
            if (!(commandSender instanceof Player player)) {
                commandSender.sendMessage("§c只有玩家才能使用此命令喵~");
                return true;
            }
            // 喵~创建一个已过期的测试菜肴（保质期0分钟 = 立即过期）喵
            ItemStack dish = new ItemStack(Material.SUSPICIOUS_STEW, 1);
            ItemMeta meta = dish.getItemMeta();
            if (meta != null) {
                meta.setDisplayName("§c[已过期] §f测试菜肴");
                List<String> lore = new ArrayList<>();
                lore.add("§7品质: §f普通  §7饱食: §f4  §7饱和: §f2.0  §7份量: §f3");
                lore.add("§8变质期: §f0分钟");
                lore.add("§8生产日期: §f很久以前");
                lore.add("§c§l已过期");
                lore.add("§7这是一个调试用的过期菜肴喵~");
                meta.setLore(lore);
                org.bukkit.persistence.PersistentDataContainer pdc = meta.getPersistentDataContainer();
                pdc.set(CookingKeys.DISH_HUNGER,              PersistentDataType.INTEGER, 4);
                pdc.set(CookingKeys.DISH_SATURATION,          PersistentDataType.DOUBLE,  2.0);
                pdc.set(CookingKeys.DISH_QUALITY,             PersistentDataType.STRING,  "普通");
                pdc.set(CookingKeys.DISH_DESCRIPTION,         PersistentDataType.STRING,  "调试菜肴");
                pdc.set(CookingKeys.DISH_SHELF_LIFE,          PersistentDataType.INTEGER, 0); // 0分钟=立即过期喵
                pdc.set(CookingKeys.DISH_SERVINGS_REMAINING,  PersistentDataType.INTEGER, 3);
                // 时间戳设为1小时前，确保过期喵
                pdc.set(CookingKeys.FOOD_TIMESTAMP, PersistentDataType.LONG, System.currentTimeMillis() - 3600_000L);
                dish.setItemMeta(meta);
            }
            player.getInventory().addItem(dish);
            player.sendMessage("§a已给予一个过期测试菜肴喵~");
            return true;
        }

        if (strings.length == 3) {
            if (hasPermission(commandSender, "exoticgarden.admin") &&
                    strings[0].equalsIgnoreCase("alo") &&
                    strings[1].equalsIgnoreCase("info")) {
                if (Bukkit.getPlayer(strings[2]).isOnline()) {
                    commandSender.sendMessage("§8[§b异域花园§8] §7玩家§e" + strings[2] + "§7的酒精度为§e" + ExoticGarden.drunkPlayers.get(strings[2]).getAlcohol());
                } else {
                    commandSender.sendMessage("§8[§b异域花园§8] §c指定的玩家不在线！");
                }
            }
            return true;
        }
        if (strings.length == 4) {
            if (hasPermission(commandSender, "exoticgarden.admin")) {
                if (strings[1].equalsIgnoreCase("add")) {
                    if (Bukkit.getPlayer(strings[2]).isOnline()) {
                        ExoticGarden.drunkPlayers.get(strings[2]).addAlcohol(Integer.parseInt(strings[3]));
                        commandSender.sendMessage("§8[§b异域花园§8] §7为玩家§e" + strings[2] + "§7增加了§e" + strings[3] + "§酒精度");
                    } else {
                        commandSender.sendMessage("§8[§b异域花园§8] §c指定的玩家不在线！");
                    }
                } else if (strings[1].equalsIgnoreCase("set")) {
                    if (Bukkit.getPlayer(strings[2]).isOnline()) {
                        ExoticGarden.drunkPlayers.get(strings[2]).setAlcohol(Integer.parseInt(strings[3]));
                        commandSender.sendMessage("§8[§b异域花园§8] §7将玩家§e" + strings[2] + "§7的酒精度设置为§e" + strings[3]);
                    } else {
                        commandSender.sendMessage("§8[§b异域花园§8] §c指定的玩家不在线！");
                    }
                }
            }
            return true;
        }
        sendHelp(commandSender);
        return true;
    }

    private void sendHelp(CommandSender sender) {
        if (hasPermission(sender, "exoticgarden.admin")) {
            String[] help = {
                "        §7--------§8====§e[ §b异域花园 §e]§8====§7--------",
                "§b/exotic version                      §7显示当前版本号",
                "§b/exotic help                         §7显示帮助信息",
                "§b/exotic alo info <玩家名>            §7查看指定玩家酒精度",
                "§b/exotic alo add <玩家名> <值>        §7增加/减少 酒精度",
                "§b/exotic alo set <玩家名> <值>        §7设定 酒精度",
                "§b/exotic debug getfood               §7获取一个已过期的测试菜肴"
            };
            sender.sendMessage(help);
        } else {
            sender.sendMessage("§c你没有权限这么做!");
        }
    }

    private boolean hasPermission(CommandSender sender, String perms) {
        if (!(sender instanceof Player player)) {
            return true;
        }
        return (player.hasPermission(perms) || player.isOp());
    }
}


