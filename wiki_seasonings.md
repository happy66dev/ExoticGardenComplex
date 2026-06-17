# 烹饪系统 - 辅料 Wiki

本文档基于 `seasonings.yml` 默认配置生成。辅料通过右键灶台加入，最多同时放 10 种（水类和油类不计入上限）。

---

## 字段说明

| 字段 | 说明 |
|---|---|
| `category` | 分类：`water`=水类(不占槽)、`oil`=油类(不占槽)、`seasoning`=普通辅料、`potion`=药水辅料 |
| `has_doneness` | 是否有渗入度（0~200%，100%完美，200%变味）|
| `min_temp` | 开始渗入的最低温度（仅 has_doneness=true 时有效）|
| `optimal_temp` | 渗入速度最快的温度 |
| `base_time_seconds` | 在最佳温度下达到100%渗入所需秒数 |
| `water_ml` | 加入时向灶台贡献的水量（ml）|
| `oil_ml` | 加入时向灶台贡献的油量（ml）|
| `container_return` | 使用后返还的容器：`BUCKET`/`GLASS_BOTTLE`/`none` |

---

## 水类（不计入调料槽上限）

| 物品 ID | 显示名 | 出水量 | 返还容器 |
|---|---|---|---|
| `minecraft:WATER_BOTTLE` | 水 | 250ml | 玻璃瓶 |
| `minecraft:WATER_BUCKET` | 水(桶) | 1000ml | 桶 |

---

## 油类（不计入调料槽上限）

| 物品 ID | 显示名 | 出油量 | 出水量 |
|---|---|---|---|
| `slimefun:VEGETABLE_OIL` | 植物油 | 100ml | — |

---

## 普通辅料（占调料槽，上限10种）

### 基础调料

| 物品 ID | 显示名 | 克重 | 渗入 | 备注 |
|---|---|---|---|---|
| `slimefun:SALT` | 盐 | 1g | 无 | |
| `slimefun:SUGAR` | 白糖 | 1g | 无 | |
| `slimefun:BROWN_SUGAR` | 红糖 | 1g | 无 | |
| `slimefun:MSG` | 味精 | 1g | 无 | |
| `slimefun:BLACK_PEPPER` | 黑胡椒碎 | 1g | 无 | |
| `slimefun:LEEK` | 葱花 | 1g | 无 | |
| `slimefun:CILANTRO` | 香菜碎 | 1g | 无 | |
| `slimefun:PEANUT` | 花生碎 | 1g | 无 | |
| `slimefun:CURRY_LEAF` | 咖喱叶 | 1g | 无 | |
| `slimefun:TEA_LEAF` | 茶叶 | 1g | 无 | |

### 特殊调料

| 物品 ID | 显示名 | 克重 | 渗入 | 备注 |
|---|---|---|---|---|
| `slimefun:DEVIL_MELON` | 恶魔瓜丁 | 1g | 无 | 猎奇食材 |
| `slimefun:INFERNOFRUIT` | 地狱果片 | 1g | 无 | 猎奇食材 |

### 乳制品 / 油脂辅料（进调料槽，兼有出水/出油）

| 物品 ID | 显示名 | 克重 | 出水量 | 出油量 | 返还容器 |
|---|---|---|---|---|---|
| `minecraft:MILK_BUCKET` | 牛奶(桶) | 250g | 250ml | — | 桶 |
| `minecraft:HONEY_BOTTLE` | 蜂蜜 | 20g | 25ml | — | 玻璃瓶 |
| `slimefun:BUTTER` | 黄油 | 20g | — | 100ml | — |
| `slimefun:HEAVY_CREAM` | 淡奶油 | 20g | 50ml | 50ml | — |

### 料酒 / 醋（热渗入液体调料）

| 物品 ID | 显示名 | 克重 | 出水量 | 最低温 | 最佳温 | 渗入时间 |
|---|---|---|---|---|---|---|
| `slimefun:RICE_WINE` | 料酒 | 1g | 100ml | 50°C | 80°C | 20s |
| `slimefun:VINEGAR` | 醋 | 1g | 100ml | 40°C | 70°C | 15s |

---

## 药水辅料（占调料槽）

| 物品 ID | 显示名 | 出水量 | 最低温 | 最佳温 | 渗入时间 | 返还容器 |
|---|---|---|---|---|---|---|
| `_POTION_`（所有药水） | 药水 | 200ml | 40°C | 60°C | 18s | 玻璃瓶 |

> 药水会将其药水效果传入菜肴，无效果的药水视为普通水瓶（+200ml 水）。

---

## 果汁类调料（热渗入，含 200ml 出水量）

所有果汁克重均为 1g，无返还容器，出油量 0。

### 柑橘类（快速渗入：min_temp=40, optimal_temp=60, 12s）

柠檬汁、酸橙汁、橙汁

### 浆果类（中快速渗入：min_temp=45, optimal_temp=70, 16s）

苹果汁、西瓜汁、甜浆果汁、发光浆果汁、葡萄汁、蓝莓汁、树莓汁、黑莓汁、蔓越莓汁、草莓汁

### 核果/热带类（中速渗入：min_temp=50, optimal_temp=80, 20s）

桃子汁、梅子汁、梨汁、樱桃汁、石榴汁、火龙果汁、橡树苹果汁、面包果汁、外星果汁、潘趣果汁、菠萝蜜汁、大萝卜汁、大麦汁、苋菜汁、恶魔瓜汁、橡子南瓜汁

### 浓郁类（慢速渗入：min_temp=55, optimal_temp=90, 25s）

椰奶、菠萝汁、番茄汁

### 根茎类（最慢渗入：min_temp=60, optimal_temp=100, 30s）

南瓜汁、胡萝卜汁

---

## 烹饪成品辅料（纯 topping，无渗入，克重 1g）

| 显示名 | 物品 ID |
|---|---|
| 培根碎 | `slimefun:BACON` |
| 芝士碎 | `slimefun:CHEESE` |
| 炸鸡碎 | `slimefun:FRIED_CHICKEN` |
| 薯条碎 | `slimefun:FRIES` |
| 土豆饼碎 | `slimefun:HASHBROWN` |
| 洋葱圈碎 | `slimefun:ONION_RINGS` |
| 鸡块碎 | `slimefun:CHICKEN_NUGGETS` |
| 肉汁 | `slimefun:COUNTRY_GRAVY` |
| 蛋黄酱 | `slimefun:MAYO` |
| 烤肉酱 | `slimefun:BBQ_SAUCE` |
| 芥末 | `slimefun:MUSTARD` |

---

## 配置自定义

辅料配置文件路径：`plugins/ExoticGarden/seasonings.yml`

每次服务器启动若版本不匹配会自动备份旧文件并覆写为新默认值。
可修改 `hint` 字段为非空字符串，AI 生成菜肴时会将该提示信息传递给模型。
