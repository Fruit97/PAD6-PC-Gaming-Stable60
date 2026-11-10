package com.pcappstudio.launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Craft Anything companion. It prepares official Windows x64 portable ZIP files for
 * Winlator, but does not execute Windows code, change Winlator settings or guarantee
 * the desktop app will run on Android.
 */
public final class CraftAnythingActivity extends Activity {
    private static final int PICK_CRAFT_ZIP = 3001;
    private static final String RELEASES =
        "https://github.com/storytold/craft-anything/releases";
    private static final int BG = Color.rgb(12, 17, 35);
    private static final int PANEL = Color.rgb(26, 34, 57);
    private static final int TEXT = Color.rgb(241, 246, 255);
    private static final int MUTED = Color.rgb(175, 187, 212);
    private static final int ACCENT = Color.rgb(131, 173, 255);
    private static final long MAX_EXTRACTED = 3L * 1024 * 1024 * 1024;
    private static final int MAX_FILES = 6000;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private TextView status;
    private LinearLayout detected;
    private ProgressBar progress;
    private Button importButton;
    private volatile boolean extracting;

    private int dp(float n) { return (int) (n * getResources().getDisplayMetrics().density + .5f); }

    private GradientDrawable panel(int color, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radius));
        return d;
    }

    private TextView text(String s, int size, boolean bold, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(size);
        t.setTextColor(color);
        t.setLineSpacing(dp(2), 1f);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }

    private LinearLayout group() {
        LinearLayout x = new LinearLayout(this);
        x.setOrientation(LinearLayout.VERTICAL);
        x.setPadding(dp(16), dp(16), dp(16), dp(16));
        x.setBackground(panel(PANEL, 20));
        return x;
    }

    private LinearLayout.LayoutParams space(int top) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.topMargin = dp(top);
        return p;
    }

    private Button button(String label, boolean primary, View.OnClickListener click) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextSize(15);
        b.setTextColor(primary ? BG : TEXT);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setMinHeight(dp(49));
        b.setBackground(panel(primary ? ACCENT : Color.rgb(55, 65, 98), 13));
        b.setOnClickListener(click);
        return b;
    }

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);
        LinearLayout root = new LinearLayout(this);
        root.setPadding(dp(20), dp(22), dp(20), dp(42));
        root.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(root);
        setContentView(scroll);

        root.addView(text("PC APP STUDIO / CRAFT MODE", 13, true, ACCENT));
        root.addView(text("Craft Anything บน Xiaomi Pad 6", 24, true, TEXT), space(10));
        root.addView(text("เตรียมไฟล์ Windows Portable สำหรับ Winlator แบบไม่รูท ไม่ต้องแตก ZIP ด้วยตนเอง", 14, false, MUTED), space(8));

        LinearLayout select = group();
        root.addView(select, space(22));
        select.addView(text("1  นำเข้า Craft Anything", 18, true, TEXT));
        select.addView(text("ดาวน์โหลด Windows x64 Portable ZIP จาก GitHub โครงการ แล้วเลือก ZIP ในหน้าต่างนี้ ระบบจะคลายไฟล์โดยรักษาโครงสร้างโฟลเดอร์", 13, false, MUTED), space(9));
        select.addView(button("เปิดหน้าดาวน์โหลดทางการ", false, v -> openUrl(RELEASES)), space(13));
        importButton = button("เลือก ZIP และเตรียมไฟล์", true, v -> chooseArchive());
        select.addView(importButton, space(9));
        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setIndeterminate(true);
        progress.setVisibility(View.GONE);
        select.addView(progress, space(12));
        status = text("ยังไม่ได้เตรียม ZIP", 13, false, MUTED);
        select.addView(status, space(9));

        LinearLayout profile = group();
        root.addView(profile, space(15));
        profile.addView(text("2  ตั้งค่า Winlator", 18, true, TEXT));
        profile.addView(text("โปรไฟล์เริ่มต้น: 1280×720, Wine 64-bit, ไดรเวอร์ Turnip สำหรับ Snapdragon/Adreno, Box64 Compatibility เพื่อทดสอบความเข้ากันได้ก่อน", 13, false, MUTED), space(9));
        profile.addView(button("ดูรายละเอียดการตั้งค่าและแก้จอดำ", false, v -> showSettings()), space(13));
        profile.addView(button("เปิด Winlator", true, v -> openWinlator()), space(9));

        LinearLayout exe = group();
        root.addView(exe, space(15));
        exe.addView(text("3  ไฟล์โปรแกรมที่เตรียมแล้ว", 18, true, TEXT));
        exe.addView(text("เลือกคัดลอกพาธ .exe แล้วเปิดจาก File Explorer ใน Winlator ไดรฟ์ D: (ต้องแมป D: ไปยัง Download)", 13, false, MUTED), space(8));
        detected = new LinearLayout(this);
        detected.setOrientation(LinearLayout.VERTICAL);
        exe.addView(detected, space(8));
        showSavedExecutables();

        root.addView(text("ข้อจำกัด: แอปนี้ยังไม่รวม Wine/Box64 และไม่ได้ยืนยันว่า Craft Anything เปิดใช้งานผ่าน Winlator ได้ครบทุกฟังก์ชัน การสร้างโปรเจกต์ด้วย AI ต้องมี coding agent เพิ่มเติม", 12, false, MUTED), space(19));
    }

    private void openUrl(String url) {
        try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); }
        catch (Exception e) { Toast.makeText(this, "เปิดเบราว์เซอร์ไม่ได้", Toast.LENGTH_SHORT).show(); }
    }

    private void chooseArchive() {
        if (extracting) return;
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("application/zip");
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try { startActivityForResult(i, PICK_CRAFT_ZIP); }
        catch (Exception ex) {
            i.setType("*/*");
            startActivityForResult(i, PICK_CRAFT_ZIP);
        }
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != PICK_CRAFT_ZIP || result != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        String name = "";
        try (Cursor c = getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (c != null && c.moveToFirst()) name = c.getString(0);
        } catch (Exception ignored) { }
        if (name == null || !name.toLowerCase(Locale.ROOT).endsWith(".zip")) {
            new AlertDialog.Builder(this)
                .setTitle("กรุณาเลือกไฟล์ .zip")
                .setMessage("ให้ใช้ Windows x64 Portable ZIP ของ Craft Anything จากหน้า Releases ทางการ")
                .setPositiveButton("ตกลง", null).show();
            return;
        }
        prepareZip(uri);
    }

    private static final class Candidate {
        final String relative, batch;
        final long bytes;
        Candidate(String relative, String batch, long bytes) {
            this.relative = relative; this.batch = batch; this.bytes = bytes;
        }
        JSONObject toJson() throws Exception {
            JSONObject obj = new JSONObject();
            obj.put("relative", relative);
            obj.put("batch", batch);
            obj.put("bytes", bytes);
            return obj;
        }
        String windowsPath() { return CraftZipPaths.windowsFilePath(batch, relative); }
    }

    private void prepareZip(Uri source) {
        extracting = true;
        importButton.setEnabled(false);
        progress.setVisibility(View.VISIBLE);
        status.setText("กำลังตรวจสอบ ZIP...");
        executor.execute(() -> {
            List<Uri> inserted = new ArrayList<>();
            List<Candidate> candidates = new ArrayList<>();
            HashSet<String> names = new HashSet<>();
            String batch = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date()) + "-" + (System.currentTimeMillis() % 100000);
            long total = 0, lastReport = 0;
            int files = 0;
            try (InputStream raw = getContentResolver().openInputStream(source)) {
                if (raw == null) throw new IllegalStateException("เปิด ZIP ไม่สำเร็จ");
                try (ZipInputStream zin = new ZipInputStream(raw)) {
                    ZipEntry entry;
                    while ((entry = zin.getNextEntry()) != null) {
                        if (entry.isDirectory()) { zin.closeEntry(); continue; }
                        String safe = CraftZipPaths.safeRelative(entry.getName());
                        if (safe == null) throw new IllegalArgumentException("ZIP มีพาธที่ไม่ปลอดภัย: " + entry.getName());
                        String key = safe.toLowerCase(Locale.ROOT);
                        if (!names.add(key)) throw new IllegalArgumentException("มีชื่อไฟล์ซ้ำใน ZIP: " + safe);
                        if (++files > MAX_FILES) throw new IllegalArgumentException("ZIP มีไฟล์มากเกินกำหนด");
                        if (entry.getSize() > MAX_EXTRACTED - total) throw new IllegalArgumentException("ZIP ใหญ่เกินกำหนด");

                        ContentValues v = new ContentValues();
                        v.put(MediaStore.Downloads.DISPLAY_NAME, CraftZipPaths.baseName(safe));
                        v.put(MediaStore.Downloads.RELATIVE_PATH,
                            "Download/PCAppStudio/CraftAnything/" + batch + "/" + CraftZipPaths.parentPath(safe));
                        v.put(MediaStore.Downloads.MIME_TYPE, "application/octet-stream");
                        v.put(MediaStore.Downloads.IS_PENDING, 1);
                        Uri output = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
                        if (output == null) throw new IllegalStateException("สร้างไฟล์ใน Downloads ไม่ได้");
                        inserted.add(output);
                        long size = 0;
                        try (OutputStream out = getContentResolver().openOutputStream(output, "w")) {
                            if (out == null) throw new IllegalStateException("เขียนข้อมูลลงเครื่องไม่ได้");
                            byte[] buffer = new byte[131072];
                            int n;
                            while ((n = zin.read(buffer)) != -1) {
                                if (n == 0) continue;
                                total += n;
                                size += n;
                                if (total > MAX_EXTRACTED) throw new IllegalArgumentException("ไฟล์ที่แตกมีขนาดเกิน 3 GB");
                                out.write(buffer, 0, n);
                                long now = SystemClock.uptimeMillis();
                                if (now - lastReport > 700) {
                                    long captured = total;
                                    int fileCount = files;
                                    runOnUiThread(() -> status.setText("เตรียมไฟล์ " + fileCount + " รายการ • " + (captured / 1048576) + " MB"));
                                    lastReport = now;
                                }
                            }
                        }
                        if (CraftZipPaths.isExecutable(safe)) candidates.add(new Candidate(safe, batch, size));
                        zin.closeEntry();
                    }
                }
                if (candidates.isEmpty()) throw new IllegalArgumentException("ไม่พบ .exe ใน ZIP นี้ กรุณาเลือก Windows x64 Portable ZIP");
                // Make files visible only after the entire archive is processed successfully.
                for (Uri u : inserted) {
                    ContentValues values = new ContentValues();
                    values.put(MediaStore.Downloads.IS_PENDING, 0);
                    getContentResolver().update(u, values, null, null);
                }
                candidates.sort(Comparator.comparingInt((Candidate c) -> CraftZipPaths.candidateScore(c.relative)).reversed());
                JSONArray saved = new JSONArray();
                for (Candidate c : candidates) saved.put(c.toJson());
                getPreferences(MODE_PRIVATE).edit().putString("craft_exes", saved.toString()).apply();
                final int fileCount = files;
                final int candidatesCount = candidates.size();
                runOnUiThread(() -> {
                    status.setText("สำเร็จ: " + fileCount + " ไฟล์ • พบไฟล์ EXE " + candidatesCount + " รายการ");
                    showSavedExecutables();
                    new AlertDialog.Builder(this).setTitle("เตรียม Craft Anything เสร็จ")
                        .setMessage("ไฟล์อยู่ใน Download/PCAppStudio/CraftAnything\n\nเปิด Winlator → D: → PCAppStudio → CraftAnything แล้วใช้ไฟล์ craft-anything.exe\n\nหมายเหตุ: ยังไม่ได้ยืนยันว่า Wine เปิดโปรแกรมนี้ได้จริง")
                        .setPositiveButton("เปิด Winlator", (d, which) -> openWinlator())
                        .setNegativeButton("ตกลง", null).show();
                });
            } catch (Exception ex) {
                for (Uri u : inserted) {
                    try { getContentResolver().delete(u, null, null); } catch (Exception ignored) {}
                }
                String message = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
                runOnUiThread(() -> {
                    status.setText("เตรียมไม่สำเร็จ: " + message);
                    new AlertDialog.Builder(this).setTitle("ไม่สามารถนำเข้า ZIP")
                        .setMessage(message + "\n\nโปรดลองตรวจพื้นที่ว่างและเลือก ZIP ทางการอีกครั้ง")
                        .setPositiveButton("ตกลง", null).show();
                });
            } finally {
                extracting = false;
                runOnUiThread(() -> {
                    importButton.setEnabled(true);
                    progress.setVisibility(View.GONE);
                });
            }
        });
    }

    private void showSavedExecutables() {
        detected.removeAllViews();
        JSONArray arr;
        try { arr = new JSONArray(getPreferences(MODE_PRIVATE).getString("craft_exes", "[]")); }
        catch (Exception ignored) { arr = new JSONArray(); }
        if (arr.length() == 0) {
            detected.addView(text("ยังไม่มีไฟล์ที่เตรียมแล้ว", 14, false, MUTED), space(10));
            return;
        }
        for (int i = 0; i < Math.min(arr.length(), 12); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o == null) continue;
            Candidate c = new Candidate(o.optString("relative"), o.optString("batch"), o.optLong("bytes"));
            detected.addView(text(c.relative + "\n" + (c.bytes / 1024) + " KB", 13, false, TEXT), space(12));
            detected.addView(button("คัดลอกพาธ: " + CraftZipPaths.baseName(c.relative), i == 0, v -> {
                ClipboardManager cb = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                cb.setPrimaryClip(ClipData.newPlainText("Winlator Craft Anything", c.windowsPath()));
                Toast.makeText(this, "คัดลอกแล้ว: " + c.windowsPath(), Toast.LENGTH_LONG).show();
            }), space(6));
        }
    }

    private void showSettings() {
        String guide =
            "สำหรับ Xiaomi Pad 6 / Snapdragon 870 / Android:\n\n" +
            "1. Winlator → สร้าง Container แยกสำหรับ Craft Anything\n" +
            "2. Screen Size: เริ่มที่ 1280×720\n" +
            "3. Graphics Driver: ลอง Turnip ก่อน (สำหรับ Adreno)\n" +
            "4. Box64 Preset: เริ่ม Compatibility หากเปิดได้ค่อยทดลอง Performance\n" +
            "5. Wine: ใช้ Windows 10 และ 64-bit runtime ที่มีใน Winlator\n" +
            "6. เปิด File Explorer → D: → PCAppStudio → CraftAnything → โฟลเดอร์ที่เตรียมไว้\n" +
            "7. เปิด craft-anything.exe ไม่ใช่ไฟล์ ZIP\n\n" +
            "ถ้าเจอจอดำ: ลอง VirGL หรือ Box64 Stability และสร้าง Container ใหม่เพื่อแยกการทดสอบ\n\n" +
            "หากโปรแกรมเปิดแล้วแต่สร้างโปรเจกต์ AI ไม่ได้ อาจต้องมี coding-agent CLI ติดตั้งและล็อกอินแยก ซึ่ง Winlator อาจไม่รองรับ\n\n" +
            "ปุ่มนี้เป็นคำแนะนำการตั้งค่า ไม่ได้ปรับ Winlator อัตโนมัติ";
        new AlertDialog.Builder(this).setTitle("โปรไฟล์การทดลอง Craft Anything")
            .setMessage(guide)
            .setPositiveButton("เปิด Winlator", (d, w) -> openWinlator())
            .setNeutralButton("คัดลอกคำแนะนำ", (d, w) -> {
                ClipboardManager cb = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                cb.setPrimaryClip(ClipData.newPlainText("Craft settings", guide));
                Toast.makeText(this, "คัดลอกแล้ว", Toast.LENGTH_SHORT).show();
            })
            .setNegativeButton("ปิด", null).show();
    }

    private void openWinlator() {
        PackageManager pm = getPackageManager();
        for (String pkg : new String[]{"com.winlator", "com.winlator.cmod", "com.winlator.bionic"}) {
            Intent i = pm.getLaunchIntentForPackage(pkg);
            if (i != null) {
                try { startActivity(i); return; } catch (Exception ignored) { }
            }
        }
        new AlertDialog.Builder(this).setTitle("ยังไม่พบ Winlator")
            .setMessage("จำเป็นต้องติดตั้ง Winlator แยกต่างหาก APK นี้ไม่มี Wine หรือ Box64 ภายใน")
            .setPositiveButton("เปิด GitHub Winlator", (d, w) -> openUrl("https://github.com/brunodev85/winlator/releases"))
            .setNegativeButton("ปิด", null).show();
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }
}
