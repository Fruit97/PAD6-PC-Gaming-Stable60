# NSO FPS Analyzer — Android prototype v0.3

แอปอ่านและวิเคราะห์ NSO Nintendo Switch บน Android โดยไม่ Root

## แก้ไข v0.3
- บันทึกคำสั่ง ARM64 ADRP และคำสั่งที่ใช้ต่อ พร้อม context จริงรอบจุดอ้างอิง
- ทำ crosscheck กับ SHA256 ของ Demon Slayer 2 1.80 และรายงานการอ่าน float ที่ยืนยันได้
- ย้ำว่าการอ้างอิงตำแหน่งหน่วยความจำไม่ใช่หลักฐานว่าตำแหน่งดังกล่าวเป็นตัวปลดล็อก FPS

## แก้ไข v0.2
- ตรวจแยก NSO แบบบีบอัดกับ Eden dump ที่เก็บส่วนหัวเดิมแต่ข้อมูลโปรแกรมถูกถอดไว้แล้ว
- แก้ Invalid LZ4 match offset เมื่อเปิดไฟล์ dump ของ Eden
- สำหรับ Eden dump อ่าน segment แบบสตรีมโดยไม่สร้างไฟล์แคชใหญ่

## ความสามารถ
- เลือก main.nso ผ่านตัวเลือกไฟล์ Android (Storage Access Framework)
- อ่าน NSO0, Build ID, SHA-256 และรายละเอียด TEXT/RODATA/DATA
- รองรับข้อมูล NSO แบบ LZ4 โดยถอดทีละบล็อกและสแกนจากแคช ไม่โหลดไฟล์ทั้งเกมเข้าหน่วยความจำ
- ค้นหาลายเซ็น Gravity/Multiplication, ข้อความ FixedFrameRate, t.MaxFPS, r.VSync
- สแกนคำสั่ง ARM64 ADRP พร้อม ADD/LDR ที่อ้างอิง MAIN RVA เป้าหมาย
- แสดงและคัดลอกรายงาน JSON หรือส่งข้อความผ่าน Android Share Sheet เข้า ChatGPT

## ข้อจำกัด
- ยัง **ไม่สร้างม็อด 60 FPS** และ **ไม่แก้เกม**
- ตำแหน่งที่พบเป็นเพียง xref ไม่ยืนยันว่าเป็นตัวปลดล็อก FPS
- Android share sheet ต้องให้ผู้ใช้เลือก ChatGPT ด้วยตัวเอง ไม่เชื่อมการสนทนาอัตโนมัติ
- รองรับ LZ4 ใน NSO0 และ NSO memory image ที่ Eden ถอดแล้วโดยไม่ถอด LZ4 ซ้ำ; ZBIC และรูปแบบบีบอัดอื่นยังไม่รองรับ
- การวิเคราะห์ต้องเปิดแอปค้างไว้จนเสร็จ; พื้นที่ว่างของแคชต้องเพียงพอต่อ segment ที่ถอด
- ไม่ส่งไฟล์ NSO หรือรายละเอียดเกมออกอินเทอร์เน็ต; มีแต่การแชร์รายงานเมื่อผู้ใช้กดเท่านั้น

## สร้าง APK
เรียก GitHub Actions workflow `Build NSO FPS Analyzer APK` บนสาขา `feature/nso-fps-analyzer-v1` หรือใช้
```sh
gradle -p nso-analyzer :app:assembleDebug
```
ไฟล์ APK อยู่ที่ `nso-analyzer/app/build/outputs/apk/debug/app-debug.apk`

แพ็กเกจ Android: `com.pad6.nsoanalyzer`

โปรเจกต์นี้อยู่ในสาขาแยกเพื่อไม่กระทบแอป PAD6-PC-Gaming-Stable60 ในสาขา main.
