# NSO FPS Analyzer — Android prototype v0.1

แอปอ่านและวิเคราะห์ NSO Nintendo Switch บน Android โดยไม่ Root

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
- รองรับ LZ4 ใน NSO0; ZBIC และรูปแบบบีบอัดอื่นยังไม่รองรับ
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
