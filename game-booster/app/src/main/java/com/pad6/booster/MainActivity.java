package com.pad6.booster;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final int BACK = Color.rgb(9, 16, 29);
    private static final int PANEL = Color.rgb(23, 37, 56);
    private static final int WHITE = Color.rgb(245, 248, 255);
    private static final int GREEN = Color.rgb(77, 232, 179);
    private static final int DIM = Color.rgb(176, 190, 208);
    private final Handler timer = new Handler(Looper.getMainLooper());
    private final List<ResolveInfo> apps = new ArrayList<>();
    private TextView status, chosen;
    private LinearLayout appList;
    private CheckBox quiet;
    private SharedPreferences pref;
    private String packageName;
    private int oldFilter;
    private boolean changedDnd = false, inGame = false;
    private final Runnable updater = new Runnable() {
        @Override public void run() {
            updateStatus();
            timer.postDelayed(this, 15000);
        }
    };

    private int dp(int n) { return (int)(n * getResources().getDisplayMetrics().density + 0.5f); }
    private LinearLayout vertical() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }
    private TextView label(String s, int size, int color) {
        TextView t = new TextView(this);
        t.setText(s); t.setTextSize(size); t.setTextColor(color);
        t.setPadding(0, dp(5), 0, dp(5));
        return t;
    }
    private GradientDrawable background(int color) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color); d.setCornerRadius(dp(14)); return d;
    }
    private Button button(String s) {
        Button b = new Button(this);
        b.setAllCaps(false); b.setText(s); b.setTextColor(BACK);
        b.setTextSize(15); b.setTypeface(null, Typeface.BOLD);
        b.setBackground(background(GREEN)); return b;
    }
    private void add(LinearLayout p, View c) {
        p.addView(c, new LinearLayout.LayoutParams(-1, -2));
    }
    private LinearLayout panel(LinearLayout parent) {
        LinearLayout p = vertical();
        p.setPadding(dp(16), dp(14), dp(16), dp(14));
        p.setBackground(background(PANEL));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = dp(12);
        parent.addView(p, params);
        return p;
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(BACK);
        getWindow().setNavigationBarColor(BACK);
        pref = getSharedPreferences("booster", MODE_PRIVATE);
        packageName = pref.getString("game", "");
        loadApps();
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(BACK);
        LinearLayout root = vertical();
        root.setPadding(dp(18), dp(24), dp(18), dp(24));
        scroll.addView(root);
        setContentView(scroll);
        add(root, label("PAD 6  •  NO ROOT", 13, GREEN));
        add(root, label("GAME BOOSTER", 30, WHITE));
        add(root, label("เลือกเกม • ตรวจสภาพเครื่อง • ลดสิ่งรบกวน", 14, DIM));

        LinearLayout statCard = panel(root);
        status = label("กำลังอ่านสถานะเครื่อง", 15, WHITE);
        add(statCard, status);

        LinearLayout playCard = panel(root);
        add(playCard, label("เกมที่เลือก", 14, DIM));
        chosen = label("ยังไม่ได้เลือกเกม", 20, WHITE);
        add(playCard, chosen);
        Button start = button("▶ เริ่มเล่นเกม");
        start.setOnClickListener(v -> launchGame());
        add(playCard, start);
        quiet = new CheckBox(this);
        quiet.setText("เปิดห้ามรบกวนระหว่างเล่น (ต้องให้สิทธิ์ก่อน)");
        quiet.setTextColor(WHITE);
        quiet.setChecked(pref.getBoolean("quiet", false));
        quiet.setOnCheckedChangeListener((b, v) -> pref.edit().putBoolean("quiet", v).apply());
        add(playCard, quiet);

        LinearLayout tools = panel(root);
        add(tools, label("เครื่องมือก่อนเข้าเกม", 20, WHITE));
        Button display = button("ตั้งค่าจอภาพ / Hz");
        display.setOnClickListener(v -> openSettings(Settings.ACTION_DISPLAY_SETTINGS));
        add(tools, display);
        Button battery = button("ตั้งค่าแบตเตอรี่");
        battery.setOnClickListener(v -> openSettings(Settings.ACTION_BATTERY_SAVER_SETTINGS));
        add(tools, battery);
        Button dnd = button("ตั้งค่าห้ามรบกวน");
        dnd.setOnClickListener(v -> openSettings(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS));
        add(tools, dnd);
        Button miui = button("เปิด Xiaomi Security / Game Turbo");
        miui.setOnClickListener(v -> {
            Intent i = getPackageManager().getLaunchIntentForPackage("com.miui.securitycenter");
            if (i != null) {
                try { startActivity(i); }
                catch (Exception e) { toast("ไม่สามารถเปิดแอประบบได้"); }
            } else toast("ไม่พบทางลัด Xiaomi Security บนเครื่องนี้");
        });
        add(tools, miui);

        add(root, label("เลือกเกมจากเครื่อง", 20, WHITE));
        EditText search = new EditText(this);
        search.setTextColor(WHITE);
        search.setHintTextColor(DIM);
        search.setHint("ค้นหาเกม เช่น Honor of Kings");
        search.setSingleLine(true);
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int st, int before, int count) { showApps(s.toString()); }
            @Override public void afterTextChanged(Editable e) {}
        });
        add(root, search);
        appList = vertical();
        add(root, appList);
        add(root, label("หมายเหตุ: แอปนี้ไม่ได้เพิ่มความเร็ว GPU, ไม่ปิดระบบกันเครื่องร้อน และไม่ปลดล็อก FPS ของเกมที่จำกัดไว้", 13, DIM));
        showSelection(); showApps(""); updateStatus();
    }
    private void loadApps() {
        PackageManager pm = getPackageManager();
        Intent launcher = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        for (ResolveInfo ri : pm.queryIntentActivities(launcher, 0)) {
            if (ri.activityInfo != null && !getPackageName().equals(ri.activityInfo.packageName)) apps.add(ri);
        }
        Collections.sort(apps, (a, b) -> {
            boolean ag = a.activityInfo.applicationInfo.category == ApplicationInfo.CATEGORY_GAME;
            boolean bg = b.activityInfo.applicationInfo.category == ApplicationInfo.CATEGORY_GAME;
            if (ag != bg) return ag ? -1 : 1;
            return a.loadLabel(pm).toString().compareToIgnoreCase(b.loadLabel(pm).toString());
        });
    }
    private void showSelection() {
        String value = "ยังไม่ได้เลือกเกม";
        for (ResolveInfo ri : apps) {
            if (ri.activityInfo.packageName.equals(packageName)) {
                value = ri.loadLabel(getPackageManager()).toString(); break;
            }
        }
        chosen.setText(value);
    }
    private void showApps(String search) {
        if (appList == null) return;
        appList.removeAllViews();
        String q = search.trim().toLowerCase(Locale.ROOT);
        int count = 0;
        PackageManager pm = getPackageManager();
        for (ResolveInfo ri : apps) {
            String name = ri.loadLabel(pm).toString();
            String pkg = ri.activityInfo.packageName;
            if (!name.toLowerCase(Locale.ROOT).contains(q) && !pkg.toLowerCase(Locale.ROOT).contains(q)) continue;
            if (++count > 70) break;
            Button item = new Button(this);
            item.setAllCaps(false);
            item.setText(name + (pkg.equals(packageName) ? "   ✓" : ""));
            item.setTextColor(WHITE);
            item.setBackground(background(PANEL));
            item.setOnClickListener(v -> {
                packageName = pkg;
                pref.edit().putString("game", pkg).apply();
                showSelection(); showApps(search);
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(52));
            lp.bottomMargin = dp(6); appList.addView(item, lp);
        }
        if (count == 0) add(appList, label("ไม่พบเกมที่ค้นหา", 14, DIM));
    }
    private void updateStatus() {
        if (status == null) return;
        PowerManager p = (PowerManager)getSystemService(POWER_SERVICE);
        boolean saver = p != null && p.isPowerSaveMode();
        int thermal = Build.VERSION.SDK_INT >= 29 && p != null ? p.getCurrentThermalStatus() : -1;
        Intent b = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        int percent = -1, temp = -1;
        if (b != null) {
            int level = b.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = b.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
            if (level >= 0 && scale > 0) percent = Math.round(level * 100f / scale);
            temp = b.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1);
        }
        ActivityManager am = (ActivityManager)getSystemService(Context.ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo info = new ActivityManager.MemoryInfo();
        if (am != null) am.getMemoryInfo(info);
        boolean hot = thermal >= PowerManager.THERMAL_STATUS_SEVERE || temp >= 440;
        String warning = hot ? "เครื่องร้อน: ลดกราฟิกและพักเครื่อง" : saver ? "เปิดโหมดประหยัดแบตอยู่: อาจทำให้ FPS ลด" : "พร้อมเข้าเกม";
        String content = warning + "\nแบตเตอรี่: " + (percent < 0 ? "?" : percent + "%")
            + "    อุณหภูมิแบต: " + (temp < 0 ? "?" : String.format(Locale.US, "%.1f°C", temp / 10f))
            + "\nRAM ว่าง: " + (info.availMem / 1048576L) + " MB"
            + "    สถานะความร้อน: " + (thermal < 0 ? "ไม่มีข้อมูล" : thermal);
        status.setText(content);
        status.setTextColor(hot ? Color.rgb(255, 133, 133) : WHITE);
    }
    private void launchGame() {
        if (packageName == null || packageName.isEmpty()) { toast("เลือกเกมก่อน"); return; }
        Intent launch = getPackageManager().getLaunchIntentForPackage(packageName);
        if (launch == null) { toast("ไม่พบทางเปิดเกม"); return; }
        if (quiet.isChecked()) {
            NotificationManager nm = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
            if (nm == null || !nm.isNotificationPolicyAccessGranted()) {
                new AlertDialog.Builder(this).setTitle("อนุญาตห้ามรบกวน")
                    .setMessage("อนุญาตให้แอปตั้งค่าห้ามรบกวนก่อน หรือปิดตัวเลือกนี้เพื่อเปิดเกมทันที")
                    .setNegativeButton("ยกเลิก", null)
                    .setPositiveButton("เปิดการตั้งค่า", (d, w) -> openSettings(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
                    .show();
                return;
            }
            try {
                oldFilter = nm.getCurrentInterruptionFilter();
                nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY);
                changedDnd = true;
            } catch (SecurityException e) { toast("ไม่มีสิทธิ์เปลี่ยนโหมดห้ามรบกวน"); return; }
        }
        try { inGame = true; startActivity(launch); }
        catch (Exception e) { inGame = false; restoreDnd(); toast("เปิดเกมไม่สำเร็จ"); }
    }
    private void restoreDnd() {
        if (!changedDnd) return;
        changedDnd = false;
        NotificationManager nm = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        if (nm != null && nm.isNotificationPolicyAccessGranted()) {
            try { nm.setInterruptionFilter(oldFilter); } catch (SecurityException ignored) {}
        }
    }
    private void openSettings(String action) {
        try { startActivity(new Intent(action)); } catch (Exception e) { toast("เปิดหน้าตั้งค่านี้ไม่ได้"); }
    }
    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_LONG).show(); }
    @Override protected void onResume() {
        super.onResume();
        if (inGame) { inGame = false; restoreDnd(); }
        timer.removeCallbacks(updater); timer.post(updater);
    }
    @Override protected void onPause() { timer.removeCallbacks(updater); super.onPause(); }
    @Override protected void onDestroy() { if (!inGame) restoreDnd(); super.onDestroy(); }
}
