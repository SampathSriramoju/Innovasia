import re, sys
sys.stdout.reconfigure(encoding='utf-8')
content = open('apps/farmer-android/app/src/main/java/com/bolkisan/farmer/data/local/KnowledgePrepopulator.kt', encoding='utf-8').read()
crops = re.findall(r'crop\s*=\s*"([^"]+)"', content)
categories = re.findall(r'category\s*=\s*"([^"]+)"', content)
titles = re.findall(r'title\s*=\s*"([^"]+)"', content)
print('Total entries:', len(crops))
print('Crops represented:', set(crops))
print('Categories represented:', set(categories))
print('Unique titles count:', len(set(titles)))
for i, (c, cat, t) in enumerate(zip(crops, categories, titles)):
    if i % 3 == 0:
        print(f"[{c}] ({cat}): {t}")
