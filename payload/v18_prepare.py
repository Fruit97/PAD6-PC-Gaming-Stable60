#!/usr/bin/env python3
"""v1.8: preserve complete spoken phrases in the translation keyboard."""
from pathlib import Path
import re
import shutil

root = Path("app/src/main/java/com/example/gamethaitranslator")
shutil.copyfile("payload/v18/ChatKeyboardService.java", root/"ChatKeyboardService.java")
shutil.copyfile("payload/v18/SpeechPhraseGuard.java", root/"SpeechPhraseGuard.java")

slang = root/"ChatSlang.java"
source = slang.read_text(encoding="utf-8")
anchor = "    /** Returns null unless there is a vetted *exact phrase* for this direction. */"
assert source.count(anchor) == 1, "Glossary anchor changed"
pairs = [
    ("th","en","ไอ้ชาติหมา","You fucking son of a bitch!"),
    ("th","zh","ไอ้ชาติหมา","你这狗东西！"),
    ("th","en","ชาติหมา","You son of a bitch!"),
    ("th","zh","ชาติหมา","你这个狗东西！"),
    ("th","en","ไอ้ชาตหมา","You fucking son of a bitch!"),
    ("th","zh","ไอ้ชาตหมา","你这狗东西！"),
    ("th","en","ไอ้ชาดหมา","You fucking son of a bitch!"),
    ("th","zh","ไอ้ชาดหมา","你这狗东西！"),
    ("en","th","fuck you","ไปตายซะ ไอ้เหี้ย!"),
    ("en","th","you fucking idiot","มึงนี่โง่ชิบหาย!"),
    ("en","th","fuck you idiot","ไปตายซะ ไอ้โง่!"),
    ("en","th","what the fuck are you doing","มึงกำลังทำเหี้ยอะไรอยู่วะ!"),
    ("en","th","why the fuck are you playing like that","มึงเล่นเหี้ยอะไรของมึงวะ!"),
    ("en","th","play stupid like this","เล่นโง่อะไรอย่างนี้วะ!"),
    ("th","en","ไปกินข้าวกันเถอะ","Let's go eat!"),
    ("th","zh","ไปกินข้าวกันเถอะ","我们去吃饭吧！"),
    ("th","en","สวัสดีเพื่อนๆ","Hey guys!"),
    ("th","zh","สวัสดีเพื่อนๆ","大家好！"),
    ("en","th","hello guys","หวัดดีทุกคน!"),
    ("zh","th","你这狗东西","ไอ้ชาติหมา!"),
    ("zh","th","你这个狗东西","ไอ้ชาติหมา!"),
    ("th","en","มึงโง่ฉิบหาย","You're fucking stupid!"),
    ("th","zh","มึงโง่ฉิบหาย","你他妈蠢死了！"),
]
for frm,to,original,translated in pairs:
    assert all('"' not in x and "\\" not in x and "\n" not in x for x in [frm,to,original,translated])
lines="\n".join(f'        add("{frm}", "{to}", "{original}", "{translated}");' for frm,to,original,translated in pairs)
source=source.replace(anchor,"    static {\n        // Full-phrase vetted game/colloquial vocabulary, no censorship.\n"+lines+"\n    }\n\n"+anchor)
slang.write_text(source,encoding="utf-8")

gradle = Path("app/build.gradle")
config = gradle.read_text()
config, n = re.subn(r"versionCode\s+\d+","versionCode 9",config); assert n==1
config, n = re.subn(r"versionName\s+'[^']+'","versionName '1.8.0'",config); assert n==1
gradle.write_text(config)
print("Game Thai Translator v1.8 whole-phrase input guard installed")
print("Voice session final chunks are buffered, exact partials merged, and full phrases translated.")
print("Recognized words are not knowingly reduced to a single word; missing audio cannot be reconstructed.")
