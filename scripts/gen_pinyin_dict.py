"""
生成 pinyin_to_chars.txt
覆盖范围：
  - GB2312 Level-1 (一级汉字 3755，按拼音排序，最常用)
  - GB2312 Level-2 (二级汉字 3008，按部首笔画排序)
  - 常用扩展字符集中拼音可解析的 CJK 字符 (U+4E00–U+9FFF)
输出格式：每行 "pinyin 字1 字2 ..."，按拼音字母升序排列
"""

import unicodedata
from collections import defaultdict
from pypinyin import lazy_pinyin, Style, load_phrases_dict

# GB2312 Level-1：区号 16–55，每区 94 个字符
# GB2312 Level-2：区号 56–87，每区 94 个字符
def gb2312_chars():
    chars = []
    for zone in range(16, 88):          # Level-1: 16-55, Level-2: 56-87
        for pos in range(1, 95):
            code = (zone + 0xA0) * 256 + (pos + 0xA0)
            try:
                b = bytes([code >> 8, code & 0xFF])
                ch = b.decode('gb2312')
                if '一' <= ch <= '鿿':
                    chars.append(ch)
            except (UnicodeDecodeError, ValueError):
                pass
    return chars

# 完整 CJK 基本区 U+4E00–U+9FFF
def cjk_basic_chars():
    return [chr(cp) for cp in range(0x4E00, 0xA000)]

# 合并去重，GB2312 优先（在前，排序更靠前）
all_chars = []
seen = set()
for ch in gb2312_chars() + cjk_basic_chars():
    if ch not in seen:
        all_chars.append(ch)
        seen.add(ch)

table = defaultdict(list)
seen_in_py = defaultdict(set)

for ch in all_chars:
    py_list = lazy_pinyin(ch, style=Style.NORMAL, errors='ignore')
    if not py_list:
        continue
    py = py_list[0].strip().lower()
    if not py or not py.isalpha():
        continue
    if ch not in seen_in_py[py]:
        table[py].append(ch)
        seen_in_py[py].add(ch)

out_path = r'app\src\main\assets\pinyin_to_chars.txt'
with open(out_path, 'w', encoding='utf-8') as f:
    for py in sorted(table.keys()):
        chars = table[py]
        f.write(py + ' ' + ' '.join(chars) + '\n')

total_lines = len(table)
total_chars = sum(len(v) for v in table.values())
print(f'完成：{total_lines} 个音节，{total_chars} 个汉字 → {out_path}')
