#!/usr/bin/env python3
"""Game Thai Translator v1.6: preserve uncensored language in voice and chat."""
from pathlib import Path
import re

root=Path("app/src/main/java/com/example/gamethaitranslator")
keyboard=root/"ChatKeyboardService.java"
slang=root/"ChatSlang.java"
code=keyboard.read_text(encoding="utf-8")
voice_re=re.compile(r'(?P<decl>\b(?:final\s+)?Intent\s+(?P<var>\w+)\s*=\s*new\s+Intent\(\s*(?:android\.speech\.)?RecognizerIntent\.ACTION_RECOGNIZE_SPEECH\s*\)\s*;)')
m=voice_re.search(code)
if not m:
    excerpt="\n".join(line for line in code.splitlines() if "Recogniz" in line or "startListening" in line)
    raise RuntimeError("Unable to identify voice recognition Intent:\n"+excerpt)
flag=m.group("var")+'.putExtra("android.speech.extra.MASK_OFFENSIVE_WORDS", false);'
if flag not in code:
    code=code[:m.end()]+"\n        "+flag+code[m.end():]
keyboard.write_text(code,encoding="utf-8")

pairs=[
("th","en","หมาเย็ดแม่","You motherfucking son of a bitch!"),
("th","en","ไอ้หมาเย็ดแม่","You motherfucking son of a bitch!"),
("th","en","เย็ดแม่","Motherfucker!"),
("th","en","เย็ดแม่มึง","Fuck your mother!"),
("th","en","ไอ้ควาย","You dumb fuck!"),
("th","en","ไอ้โง่","You fucking idiot!"),
("th","en","ไอ้เหี้ย","You fucking asshole!"),
("th","en","ไอ้สัส","You fucking prick!"),
("th","en","แม่งโคตรกาก","That shit is fucking terrible!"),
("th","en","มึงกากชิบหาย","You are fucking trash!"),
("th","en","มึงโง่ชิบหาย","You are fucking stupid!"),
("th","en","ควย","Fuck!"),
("th","en","ควยเอ๊ย","Fuck this shit!"),
("th","en","แม่งเอ๊ย","For fuck's sake!"),
("th","en","พวกมึงเล่นกากชิบหาย","You guys are playing like absolute fucking trash!"),
("th","en","หยุดแจกคิลสักทีไอ้เหี้ย","Stop fucking feeding already, asshole!"),
("th","zh","หมาเย็ดแม่","操你妈，狗东西！"),
("th","zh","ไอ้หมาเย็ดแม่","操你妈，狗东西！"),
("th","zh","เย็ดแม่","操你妈！"),
("th","zh","เย็ดแม่มึง","操你妈！"),
("th","zh","ไอ้ควาย","你他妈真是个蠢货！"),
("th","zh","ไอ้โง่","你个傻逼！"),
("th","zh","ไอ้เหี้ย","你他妈的混蛋！"),
("th","zh","ไอ้สัส","你个傻逼！"),
("th","zh","มึงกากชิบหาย","你他妈菜得要死！"),
("th","zh","มึงโง่ชิบหาย","你他妈蠢死了！"),
("th","zh","ควย","操！"),
("th","zh","ควยเอ๊ย","我操！"),
("th","zh","พวกมึงเล่นกากชิบหาย","你们他妈的打得太菜了！"),
("th","zh","หยุดแจกคิลสักทีไอ้เหี้ย","别他妈再送人头了，傻逼！"),
("en","th","motherfucker","ไอ้เหี้ยเอ๊ย!"),
("en","th","you motherfucking son of a bitch","ไอ้หมาเย็ดแม่!"),
("en","th","you fucking prick","ไอ้สัส!"),
("en","th","you fucking asshole","ไอ้เหี้ย!"),
("zh","th","操你妈，狗东西","ไอ้หมาเย็ดแม่!"),
("zh","th","你他妈的混蛋","ไอ้เหี้ย!"),
("zh","th","你他妈蠢死了","มึงโง่ชิบหาย!"),
]
source=slang.read_text(encoding="utf-8")
marker="    /** Returns null unless there is a vetted *exact phrase* for this direction. */"
assert marker in source,"Missing glossary insertion anchor"
lines=[]
for frm,to,original,translated in pairs:
    assert all('"' not in item and "\\" not in item and "\n" not in item for item in [frm,to,original,translated])
    lines.append(f'        add("{frm}", "{to}", "{original}", "{translated}");')
source=source.replace(marker,"    static {\n        // v1.6: curated uncensored, natural in-language profanity.\n"+"\n".join(lines)+"\n    }\n\n"+marker,1)
slang.write_text(source,encoding="utf-8")
gradle=Path("app/build.gradle")
txt=gradle.read_text(encoding="utf-8")
txt,n=re.subn(r"versionName\s+'[^']+'","versionName '1.6.0'",txt);assert n==1
txt,n=re.subn(r"versionCode\s+\d+","versionCode 7",txt);assert n==1
gradle.write_text(txt,encoding="utf-8")
print("v1.6 applied: recognizer no profanity mask, glossary",len(pairs),"phrases, version 1.6.0")
