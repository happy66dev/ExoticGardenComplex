# 辅料推荐渗入温度 Lore 设计

## 目标

为具有渗入度的辅料补充推荐渗入温度 Lore，让玩家能够直接看到该辅料配置中的最佳渗入温度。

## 范围

- 修改辅料物品 Lore 生成逻辑。
- 仅处理 `has_doneness: true` 的辅料。
- 使用 `SeasoningData.optimalTemp` 作为推荐温度。
- 显示格式固定为 `§7推荐渗入温度: §e<温度>°C`。
- 无渗入度的辅料清理旧的推荐渗入温度行，避免遗留错误信息。
- 不改变 `min_temp`、渗入计算、灶台温度判定、辅料配置和其他 Lore 内容。

## 数据流

`SeasoningConfig.parseEntry` 已将 `optimal_temp` 读取到 `SeasoningData.optimalTemp`。`FoodTagListener.tagIfSeasoning` 在刷新辅料 Lore 时读取该字段：当 `hasDoneness` 为真时替换或追加推荐渗入温度行；否则删除旧行。

## 边界与错误处理

- 找不到辅料配置时不增加推荐温度 Lore。
- `has_doneness: false` 的辅料不显示推荐温度，并删除旧行。
- 推荐温度按整数显示，与现有温度 Lore 的显示风格保持一致。
- 其他已有 Lore 行保持不变。

## 验证

- 执行 `mvn package -DskipTests`，确认 Java 编译和打包成功。
- 检查工作区时不修改已有的 `fuels.yml` 用户变更。
- 创建中文 Git commit 保存设计与实现变更。
