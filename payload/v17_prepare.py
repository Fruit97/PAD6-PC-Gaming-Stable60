#!/usr/bin/env python3
from pathlib import Path
import re, shutil

p=Path("app/src/main/java/com/example/gamethaitranslator")
shutil.copyfile("payload/v17/ChatKeyboardService.java",p/"ChatKeyboardService.java")
shutil.copyfile("payload/v17/LogoActivity.java",p/"LogoActivity.java")
drawable=Path("app/src/main/res/drawable")
drawable.mkdir(parents=True,exist_ok=True)
shutil.copyfile("payload/v17/ic_gamethai_logo.xml",drawable/"ic_gamethai_logo.xml")

manifest=Path("app/src/main/AndroidManifest.xml")
xml=manifest.read_text()
if 'android.permission.RECORD_AUDIO' not in xml:
    xml=xml.replace("<application",'<uses-permission android:name="android.permission.RECORD_AUDIO"/>\n    <application',1)
if '<queries>' not in xml:
    xml=xml.replace("<application",
'''<queries>
        <intent><action android:name="android.speech.RecognitionService"/></intent>
    </queries>
    <application''',1)
if 'android:icon=' not in xml:
    xml=xml.replace('android:label="@string/app_name"', 'android:label="@string/app_name" android:icon="@drawable/ic_gamethai_logo" android:roundIcon="@drawable/ic_gamethai_logo"',1)
if 'android:name=".LogoActivity"' not in xml:
    xml=xml.replace('<activity\n            android:name=".MainActivity"\n            android:exported="true">\n            <intent-filter>\n                <action android:name="android.intent.action.MAIN" />\n                <category android:name="android.intent.category.LAUNCHER" />\n            </intent-filter>\n        </activity>',
'''<activity android:name=".LogoActivity" android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
        <activity android:name=".MainActivity" android:exported="false"/>''',1)
    assert 'android:name=".LogoActivity"' in xml,"Launcher activity replacement failed"
manifest.write_text(xml)

main=p/"MainActivity.java"
s=main.read_text()
# Ask once for microphone access from the launcher Activity. IME services
# cannot show the runtime permission dialog themselves.
if 'requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}' not in s:
    needle='        super.onCreate(state);'
    assert needle in s, "MainActivity onCreate anchor missing"
    s=s.replace(needle,needle+'''
        if (Build.VERSION.SDK_INT >= 23
                && checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                        != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 1217);
        }''',1)
s=s.replace('TextView logo = text("GAME  /  THAI", 13, 0xFF68E3BD);',
'''TextView logo = text("🎮  GAME THAI TRANSLATOR  /  v1.7", 15, 0xFF68E3BD);''')
s=s.replace('TextView headline = text("แปลภาษาในเกม\\nเป็นไทยในปุ่มเดียว", 30, Color.WHITE);',
'''TextView headline = text("แปลภาษาในเกม\\nและแปลเสียงพูดลงแชตอัตโนมัติ", 27, Color.WHITE);''')
s=s.replace('กด ‘แปล’ เพื่อดูตัวอย่าง และกด ‘ใส่แชต’ เพื่อแทนข้อความเดิม',
'''ถ้ากดไมค์ แอปจะฟังและแปลลงแชตให้อัตโนมัติ เลือกภาษาบนแถบคีย์บอร์ดก่อน หากพิมพ์เองสามารถกด ‘แปล’ ได้''')
s=s.replace('เปิดใช้งานคีย์บอร์ดแปลแชต', 'เปิดใช้งานคีย์บอร์ดแปลแชตและไมค์')
main.write_text(s)

gradle=Path('app/build.gradle')
b=gradle.read_text()
b,n=re.subn(r"versionCode\s+\d+","versionCode 8",b); assert n==1
b,n=re.subn(r"versionName\s+'[^']+'","versionName '1.7.0'",b); assert n==1
gradle.write_text(b)
print("v1.7: continuous listening toggle, automatic translated voice text, clear/share, splash and launcher logo ready")
