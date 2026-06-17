# ExoticGardenComplex 复合花园 [happy66dev 魔改版]

这是一个缝合了众多异域花园附属及其本体的 Slimefun 附属，并在原版基础上新增了完整的**烹饪系统**。

本项目遵循 [GPL 3.0 License](LICENSE)。本项目基于以下上游项目二次开发，修改部分同样遵循 GPL-3.0 协议并已在各文件头部保留原始版权声明：
- [ExoticGarden](https://github.com/SlimefunGuguProject/ExoticGarden)（GPL-3.0）
- [ExoticGarden-Nar](https://github.com/SlimeAddonCollection1-12/ExoticGarden-Nar)（GPL-3.0）
- [BEPlugin](https://github.com/wdog5/BEPlugin)（GPL-3.0）
- [ExoticGarden-Changed](https://github.com/SlimefunGuguProject/ExoticGarden-changed)（GPL-3.0）
- [ExoticGarden-for-Watering-System](https://github.com/NCBPFluffyBear/ExoticGarden-for-Watering-System)（MIT）
- [ExoticGarden-it-dainb](https://github.com/it-dainb/ExoticGarden)（GPL-3.0）

---

## 烹饪系统

本魔改版核心新增功能，提供完整的游戏内烹饪体验。

### 设备

| 物品 | 功能 |
|---|---|
| **烹饪灶台** | 篝火改造，燃料供热，放置食材烹饪 |
| **砧板** | 使用烹饪刀对食材进行切割/研磨加工 |
| **烹饪刀** | 配合砧板，将食材从整块→切片→切丁→酱料 |
| **烹饪锅铲** | 给灶台食材翻面，加速烹饪，搅拌制酱 |

### 烹饪流程

1. 燃料放入灶台点火（木材/煤炭等，温度影响成熟速度）
2. 食材/调料放入灶台（可翻面，调料随时间渗入）
3. 使用碗触发 AI 生成菜肴（需配置 API Key）

### AI 菜肴生成

- 支持 OpenAI 兼容 API（在 `config.yml` 中配置 `cooking.ai_api_key`、`cooking.ai_base_url`、`cooking.ai_model`）
- 设置 `cooking.ai_enabled: true` 启用；默认 `false` 为 debug 模式（显示提示词，不调用 AI）
- 支持模型思考参数：`cooking.ai_thinking_enabled: true`

### 食材与调料

- 所有原版可食用物品自动获得保质期
- 食材可通过砧板加工（整块→切片→切丁→酱料）
- 调料支持渗入度（0~200%，100%为完美）
- 水/油类（水桶、植物油、黄油等）影响烹饪方向

### 保质期与过期

- 食材/菜肴有变质期（常温），超过后变质
- 过期食用：恢复量减少 60%，随机施加饥饿/反胃/中毒效果
- 菜肴过期额外随机移除1个正面 buff

---

## 构建

```bash
# 依赖 Slimefun4 (happy66dev fork)
cd Slimefun4-master && mvn clean package -DskipTests
cd ExoticGardenComplex && mvn clean package -DskipTests
```

---

## 许可证

GNU General Public License v3.0 — 详见 [LICENSE](LICENSE)

Copyright (C) 2025 happy (k666kkk666k@163.com)

本项目在 GPL-3.0 许可下对上游作者的原始代码进行了修改和扩展。所有新增代码同样以 GPL-3.0 协议发布，修改后的文件均保留了原始版权声明。

---

[![Star History Chart](https://api.star-history.com/svg?repos=happy66dev/ExoticGardenComplex&type=Date)](https://star-history.com/#happy66dev/ExoticGardenComplex&Date)
