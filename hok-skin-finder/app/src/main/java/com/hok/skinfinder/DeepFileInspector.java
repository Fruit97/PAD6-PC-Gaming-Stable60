package com.hok.skinfinder;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.provider.OpenableColumns;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Non-destructive, offline inspection of user-selected game asset files.
 * Recognizes public format signatures, ZIP entries, SQLite schema and UnityFS headers.
 * Does NOT bypass encrypted/proprietary containers or modify the original input.
 */
public final class DeepFileInspector {
    private static final long SQLITE_LIMIT = 48L * 1024 * 1024;
    private static final long STRING_LIMIT = 8L * 1024 * 1024;
    private static final long ZIP_UNCOMPRESSED_LIMIT = 96L * 1024 * 1024;
    private static final int MAX_ZIP_ENTRIES = 1200;
    private static final int MAX_TABLES = 100;
    private static final int MAX_REPORT = 50000;

    private DeepFileInspector() {}

    public static String inspect(Context context, ContentResolver resolver, Uri uri, String name) throws IOException {
        StringBuilder result = new StringBuilder();
        line(result, "HOK Skin Finder 1.6 - Deep File Inspection");
        line(result, "ไฟล์: " + name);
        line(result, "โหมดอ่านอย่างเดียว (ไม่แก้ไขไฟล์ต้นฉบับ)");
        long size = discoverSize(resolver, uri);
        line(result, "ขนาด: " + (size < 0 ? "ไม่ทราบ" : (size + " ไบต์")));
        byte[] prefix;
        try (InputStream in = resolver.openInputStream(uri)) {
            if (in == null) throw new IOException("ไม่สามารถเปิดไฟล์ได้");
            prefix = readPrefix(in, 4096);
        }
        String format = magic(prefix);
        line(result, "ชนิดจากส่วนหัว: " + format);
        line(result, "HEX 128 ไบต์แรก:");
        line(result, hexDump(prefix, Math.min(128, prefix.length)));
        line(result, "");
        try {
            if (format.equals("ZIP archive")) {
                line(result, "ZIP / OBB ที่เป็น ZIP - รายการไฟล์ภายใน:");
                zipEntries(resolver, uri, result);
            } else if (format.equals("SQLite database")) {
                line(result, "SQLite - ตรวจชื่อ Table และคอลัมน์:");
                inspectSqlite(context, resolver, uri, size, result);
            } else if (format.equals("UnityFS bundle")) {
                line(result, "UnityFS - อ่านโครงสร้าง Header จริง:");
                inspectUnityHeader(resolver, uri, result);
                line(result, "หมายเหตุ: รุ่นนี้ยังไม่ถอดรหัส/คลายบล็อก LZ4, LZMA ของ UnityFS");
            } else if (format.equals("GZIP compressed")) {
                line(result, "GZIP - อ่านตัวอย่างข้อมูลหลังคลายการบีบอัด:");
                inspectGzip(resolver, uri, result);
            } else {
                line(result, "ไฟล์ไบนารีหรือรูปแบบที่ยังไม่รองรับการเปิดโครงสร้าง:");
                line(result, "ตรวจพบรูปแบบจากส่วนหัวเท่านั้น; ไม่ใช่การปลดล็อกไฟล์เข้ารหัส");
            }
        } catch (Exception e) {
            line(result, "อ่านโครงสร้างได้ไม่ครบ: " + e.getClass().getSimpleName() + " - " + safe(e.getMessage()));
        }
        line(result, "\nตัวอย่างข้อความ/เส้นทางทรัพยากรที่ฝังอยู่ (อ่านข้อมูลไม่เกิน 8 MB):");
        try (InputStream in = resolver.openInputStream(uri)) {
            if (in != null) scanAscii(in, result, STRING_LIMIT);
        } catch (Exception e) {
            line(result, "สแกนข้อความไม่ได้: " + e.getClass().getSimpleName());
        }
        line(result, "\nสรุป: พบชนิด/โครงสร้างตามข้างต้นเท่านั้น ไม่สามารถยืนยันว่าไฟล์เป็นสกิน HOK ได้จากชื่อเพียงอย่างเดียว");
        if (result.length() > MAX_REPORT) return result.substring(0, MAX_REPORT) + "\n[ตัดรายงานเนื่องจากยาวเกินกำหนด]";
        return result.toString();
    }

    private static long discoverSize(ContentResolver r, Uri uri) {
        try (Cursor c = r.query(uri, new String[]{OpenableColumns.SIZE}, null, null, null)) {
            if (c != null && c.moveToFirst() && !c.isNull(0)) return c.getLong(0);
        } catch (Exception ignored) {}
        return -1;
    }

    private static byte[] readPrefix(InputStream in, int limit) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[2048];
        int total = 0, n;
        while (total < limit && (n = in.read(buf, 0, Math.min(buf.length, limit - total))) != -1) {
            if (n == 0) continue;
            out.write(buf, 0, n); total += n;
        }
        return out.toByteArray();
    }

    private static String magic(byte[] b) {
        if (starts(b, new byte[]{'P','K',3,4}) || starts(b, new byte[]{'P','K',5,6})) return "ZIP archive";
        if (starts(b, "SQLite format 3\0".getBytes(StandardCharsets.ISO_8859_1))) return "SQLite database";
        if (starts(b, "UnityFS\0".getBytes(StandardCharsets.ISO_8859_1))) return "UnityFS bundle";
        if (starts(b, "UnityRaw\0".getBytes(StandardCharsets.ISO_8859_1))) return "UnityRaw bundle (legacy)";
        if (starts(b, "UnityWeb\0".getBytes(StandardCharsets.ISO_8859_1))) return "UnityWeb bundle (legacy)";
        if (starts(b, new byte[]{(byte)0x1f,(byte)0x8b})) return "GZIP compressed";
        if (starts(b, new byte[]{(byte)0x89,0x50,0x4e,0x47})) return "PNG image";
        if (starts(b, new byte[]{(byte)0xff,(byte)0xd8,(byte)0xff})) return "JPEG image";
        if (starts(b, "DDS ".getBytes(StandardCharsets.US_ASCII))) return "DDS texture";
        if (starts(b, new byte[]{0x13,(byte)0xab,(byte)0xa1,0x5c})) return "ASTC texture";
        if (starts(b, new byte[]{(byte)0xab,'K','T','X'})) return "KTX texture";
        if (starts(b, new byte[]{0x28,(byte)0xb5,0x2f,(byte)0xfd})) return "Zstd compressed";
        if (starts(b, new byte[]{0x04,0x22,0x4d,0x18})) return "LZ4 frame";
        if (starts(b, "OggS".getBytes(StandardCharsets.US_ASCII))) return "OGG audio";
        if (starts(b, new byte[]{0x7f,'E','L','F'})) return "ELF executable/library";
        if (starts(b, "FSB5".getBytes(StandardCharsets.US_ASCII))) return "FMOD audio";
        if (starts(b, new byte[]{'P','V','R',3})) return "PVR texture";
        return "unknown/proprietary";
    }

    private static boolean starts(byte[] b, byte[] sig) {
        if (b.length < sig.length) return false;
        for (int i = 0; i < sig.length; i++) if (b[i] != sig[i]) return false;
        return true;
    }

    private static void zipEntries(ContentResolver resolver, Uri uri, StringBuilder out) throws IOException {
        try (InputStream src = resolver.openInputStream(uri)) {
            if (src == null) throw new IOException("ไม่พบข้อมูล");
            try (ZipInputStream zip = new ZipInputStream(new BufferedInputStream(src))) {
                ZipEntry entry;
                byte[] buf = new byte[65536];
                int entries = 0;
                long read = 0;
                while (entries < MAX_ZIP_ENTRIES && read < ZIP_UNCOMPRESSED_LIMIT && (entry = zip.getNextEntry()) != null) {
                    entries++;
                    if (!entry.isDirectory()) {
                        // Don't trust entry sizes or write archive entry paths to disk.
                        byte[] head = readPrefix(zip, 64);
                        read += head.length;
                        String innerType = magic(head);
                        if (entries <= 180) {
                            line(out, "[" + entries + "] " + clipped(entry.getName(), 220)
                                + " size=" + entry.getSize() + " | " + innerType);
                        }
                        // ZipInputStream will stream-decompress the remainder on closeEntry.
                        // Count decompressed output so a zip bomb cannot grow without bounds.
                        int n;
                        while (read < ZIP_UNCOMPRESSED_LIMIT &&
                                (n = zip.read(buf, 0, (int)Math.min(buf.length, ZIP_UNCOMPRESSED_LIMIT-read))) != -1) {
                            read += n;
                        }
                        if (read >= ZIP_UNCOMPRESSED_LIMIT) {
                            line(out, "[หยุดอ่าน: จำกัดข้อมูลคลายบีบอัดไว้ 96 MB]");
                            break;
                        }
                    }
                    zip.closeEntry();
                }
                line(out, "รายการที่ตรวจ: " + entries);
                if (entries >= MAX_ZIP_ENTRIES) line(out, "จำกัดรายการ ZIP สูงสุด " + MAX_ZIP_ENTRIES + " รายการต่อไฟล์");
            }
        }
    }

    private static void inspectUnityHeader(ContentResolver r, Uri uri, StringBuilder out) throws IOException {
        try (InputStream src = r.openInputStream(uri)) {
            if (src == null) throw new IOException("ไม่พบข้อมูล");
            DataInputStream in = new DataInputStream(new BufferedInputStream(src));
            String sig = cstring(in, 32);
            int version = in.readInt();
            String player = cstring(in, 96);
            String engine = cstring(in, 96);
            long fileSize = in.readLong();
            long compressed = Integer.toUnsignedLong(in.readInt());
            long uncompressed = Integer.toUnsignedLong(in.readInt());
            long flags = Integer.toUnsignedLong(in.readInt());
            line(out, "Signature: " + sig);
            line(out, "Format version: " + version);
            line(out, "Player version: " + player);
            line(out, "Unity engine version: " + engine);
            line(out, "Reported bundle size: " + fileSize + " bytes");
            line(out, "Compressed blocks-info size: " + compressed + " bytes");
            line(out, "Original blocks-info size: " + uncompressed + " bytes");
            line(out, "Flags: 0x" + Long.toHexString(flags).toUpperCase(Locale.ROOT));
            String algorithm = ((flags & 63) == 0) ? "none" :
                    ((flags & 63) == 1 ? "LZMA" :
                    ((flags & 63) == 2 ? "LZ4" :
                    ((flags & 63) == 3 ? "LZ4HC" : "other"));
            line(out, "Block-info compression: " + algorithm);
            line(out, "Block info at end: " + ((flags & 0x80) != 0));
        }
    }

    private static String cstring(DataInputStream in, int max) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        for (int i = 0; i < max; i++) {
            int b = in.readUnsignedByte();
            if (b == 0) return new String(bytes.toByteArray(), StandardCharsets.UTF_8);
            bytes.write(b);
        }
        throw new IOException("cstring ยาวเกิน " + max + " ไบต์");
    }

    private static void inspectSqlite(Context context, ContentResolver r, Uri uri, long size, StringBuilder out) throws IOException {
        if (size > SQLITE_LIMIT) {
            line(out, "ไฟล์เกิน 48 MB - ไม่คัดลอกเข้าพื้นที่ชั่วคราว ตรวจได้เพียงส่วนหัว");
            return;
        }
        File tmp = File.createTempFile("hok-scan-", ".db", context.getCacheDir());
        try {
            try (InputStream in = r.openInputStream(uri); FileOutputStream f = new FileOutputStream(tmp)) {
                if (in == null) throw new IOException("ไม่พบข้อมูล");
                byte[] buf = new byte[65536];
                long copied = 0;
                int n;
                while ((n = in.read(buf)) != -1) {
                    copied += n;
                    if (copied > SQLITE_LIMIT) throw new IOException("ฐานข้อมูลใหญ่เกิน 48 MB");
                    f.write(buf, 0, n);
                }
            }
            SQLiteDatabase db = SQLiteDatabase.openDatabase(tmp.getAbsolutePath(), null, SQLiteDatabase.OPEN_READONLY);
            try {
                try (Cursor c = db.rawQuery(
                        "SELECT type,name FROM sqlite_master WHERE type IN ('table','view') AND name NOT LIKE 'sqlite_%' ORDER BY name LIMIT " + MAX_TABLES,
                        null)) {
                    int count=0;
                    while(c.moveToNext()) {
                        String type=c.getString(0), table=c.getString(1);
                        line(out, "[" + (++count) + "] " + type + " " + clipped(table, 150));
                        if ("table".equals(type) && count<=50) {
                            String escaped=table.replace("'", "''");
                            try(Cursor cols=db.rawQuery("PRAGMA table_info('" + escaped + "')",null)) {
                                List<String> names=new ArrayList<>();
                                while(cols.moveToNext()&&names.size()<45) {
                                    names.add(cols.getString(1) + " : " + cols.getString(2));
                                }
                                line(out, "คอลัมน์: " + names.toString());
                            } catch (Exception e) {
                                line(out, "ไม่สามารถอ่านคอลัมน์ได้");
                            }
                        }
                        if (out.length()>MAX_REPORT-4000) break;
                    }
                    if(count==0)line(out,"ไม่พบตารางที่อ่านได้");
                }
            } finally { db.close(); }
        } finally {
            // Only the private, temporary copy is removed, never the game file.
            if (!tmp.delete()) tmp.deleteOnExit();
        }
    }

    private static void inspectGzip(ContentResolver r, Uri uri, StringBuilder out) throws IOException {
        try(InputStream src = r.openInputStream(uri)) {
            if(src==null)throw new IOException("ไม่พบข้อมูล");
            try(GZIPInputStream gz=new GZIPInputStream(new BufferedInputStream(src))) {
                byte[] prefix=readPrefix(gz,65536);
                line(out,"คลายตัวอย่างได้ "+prefix.length+" ไบต์ (ไม่เขียนทับต้นฉบับ)");
                line(out,"ตัวอย่าง 256 ไบต์: \n"+hexDump(prefix,Math.min(256,prefix.length)));
            }
        }
    }

    private static void scanAscii(InputStream in, StringBuilder out, long budget) throws IOException {
        Set<String> strings = new LinkedHashSet<>();
        byte[] buf = new byte[32768];
        StringBuilder candidate = new StringBuilder();
        long read = 0;
        while (read < budget && strings.size()<45) {
            int n = in.read(buf, 0, (int)Math.min(buf.length, budget-read));
            if (n==-1)break;
            if(n==0)continue;
            read+=n;
            for(int i=0;i<n;i++) {
                int ch=buf[i]&0xff;
                if(ch>=32&&ch<=126) {
                    if(candidate.length()<200)candidate.append((char)ch);
                } else {
                    remember(candidate,strings);
                    candidate.setLength(0);
                }
            }
        }
        remember(candidate,strings);
        int good=0;
        for(String value:strings) {
            String s=value.toLowerCase(Locale.ROOT);
            if(s.contains("skin")||s.contains("hero")||s.contains("model")||s.contains("mesh")||
                s.contains("texture")||s.contains("prefab")||s.contains("asset")||
                s.contains(".png")||s.contains(".ab")||s.contains(".bytes")||
                s.contains("costume")) {
                line(out, "• " + clipped(value,155));
                good++;
            }
        }
        if(good==0) {
            line(out,"ไม่พบข้อความชื่อทรัพยากรชัดเจนในตัวอย่าง");
            int shown=0;
            for(String value:strings) {
                line(out,"• "+clipped(value,120));
                if(++shown>=12)break;
            }
        }
        line(out,"อ่านตัวอย่างข้อความ "+read+" ไบต์ (คำที่พบไม่ยืนยันว่าเป็นสกิน)");
    }

    private static void remember(StringBuilder b,Set<String> out) {
        if(b.length()>=7&&b.length()<=180){
            String s=b.toString().trim();
            if(s.length()>=7 && !s.startsWith("http") && !s.contains("token="))out.add(s);
        }
    }

    private static String hexDump(byte[] b,int len) {
        StringBuilder s=new StringBuilder();
        for(int i=0;i<len;i+=16){
            s.append(String.format(Locale.ROOT,"%04X: ",i));
            int end=Math.min(len,i+16);
            for(int j=i;j<end;j++)s.append(String.format(Locale.ROOT,"%02X ",b[j]&0xff));
            s.append(" | ");
            for(int j=i;j<end;j++){
                int c=b[j]&0xff;s.append((c>=32&&c<127)?(char)c:'.');
            }
            s.append("\n");
        }
        return s.toString();
    }
    private static String clipped(String s,int limit) {
        if(s==null)return "";
        s=s.replace('\n',' ').replace('\r',' ');
        return s.length()<=limit?s:s.substring(0,limit)+"…";
    }
    private static String safe(String s){return clipped(s==null?"":s,180);}
    private static void line(StringBuilder b,String s){if(b.length()<MAX_REPORT+3000)b.append(s).append('\n');}
}
