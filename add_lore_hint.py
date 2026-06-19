"""为 ingredients.yml 和 seasonings.yml 批量追加 lore_hint 字段
在每个 hint: 行后插入 lore_hint: ""（如已有则跳过）"""
import re

FILES = [
    r"src\main\resources\ingredients.yml",
    r"src\main\resources\seasonings.yml",
]

for fpath in FILES:
    with open(fpath, "r", encoding="utf-8") as f:
        lines = f.read().split('\n')

    new_lines = []
    added = 0
    i = 0
    while i < len(lines):
        line = lines[i]
        new_lines.append(line)
        m = re.match(r'^(\s+)hint:\s', line)
        if m:
            indent = m.group(1)
            # 检查下一行是否已经是 lore_hint
            if i + 1 < len(lines) and re.match(rf'^{indent}lore_hint:\s', lines[i + 1]):
                pass  # 已存在，跳过
            else:
                new_lines.append(f'{indent}lore_hint: ""')
                added += 1
        i += 1

    with open(fpath, "w", encoding="utf-8") as f:
        f.write('\n'.join(new_lines))

    print(f'[OK] {fpath}  追加 {added} 行 lore_hint')
