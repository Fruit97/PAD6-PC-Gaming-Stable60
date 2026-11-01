package com.pad6.nsoanalyzer;

import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.view.Gravity;
import android.widget.*;
import org.json.JSONObject;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity {
    private static final int PICK_NSO=901;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private Uri chosen;
    private String filename="main.nso", report="";
    private TextView status,output;
    private EditText addresses;
    private Button analyzeButton,shareButton,copyButton;
    private final int background=Color.rgb(13,17,24),panel=Color.rgb(28,35,47);
    private final int accent=Color.rgb(45,194,173),white=Color.rgb(233,239,247);
    private int dp(int n){return (int)(n*getResources().getDisplayMetrics().density+0.5f);}
    private GradientDrawable bg(int color,int radius){
        GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;
    }
    private TextView text(String value,int size,boolean bold){
        TextView t=new TextView(this);
        t.setText(value);t.setTextColor(white);t.setTextSize(size);
        if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        return t;
    }
    private Button button(String name,int fill){
        Button b=new Button(this);b.setText(name);b.setTextColor(white);
        b.setAllCaps(false);b.setBackground(bg(fill,12));b.setTextSize(15);
        return b;
    }
    private LinearLayout.LayoutParams params(int marginTop){
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(marginTop);return p;
    }
    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);
        getWindow().setStatusBarColor(background);getWindow().setNavigationBarColor(background);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackgroundColor(background);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20),dp(26),dp(20),dp(30));
        scroll.addView(root);setContentView(scroll);
        TextView title=text("NSO FPS Analyzer",25,true);title.setTextColor(accent);
        root.addView(title,params(0));
        TextView subtitle=text("วิเคราะห์โค้ดเกม Switch ใน Xiaomi Pad 6\nไม่ต้อง Root · ไม่แก้ไฟล์เกม · ไม่อัปโหลด NSO",14,false);
        subtitle.setLineSpacing(dp(2),1f);root.addView(subtitle,params(8));
        TextView section=text("1. เลือกไฟล์ main.nso",17,true);root.addView(section,params(24));
        Button pick=button("เลือกไฟล์ NSO",panel);root.addView(pick,params(10));
        pick.setOnClickListener(v->{
            Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);
            i.setType("*/*");i.addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(i,PICK_NSO);
        });
        status=text("ยังไม่ได้เลือกไฟล์",14,false);root.addView(status,params(8));
        root.addView(text("2. ระบุ MAIN Offset ที่ต้องการตรวจสอบ",17,true),params(26));
        TextView desc=text("ค่าตั้งต้น: 1 ตำแหน่งที่พบการอ้างอิง และอีก 2 ตำแหน่งสมมติฐาน ไม่ใช่ FPS Patch ที่ยืนยันแล้ว",13,false);
        root.addView(desc,params(8));
        addresses=new EditText(this);addresses.setText("0xCDA2378, 0xCDA1FF0, 0xBDB5EB0");
        addresses.setTextColor(white);addresses.setHintTextColor(Color.LTGRAY);
        addresses.setTextSize(14);addresses.setSingleLine(false);addresses.setMinLines(2);
        addresses.setPadding(dp(12),dp(12),dp(12),dp(12));addresses.setBackground(bg(panel,10));
        root.addView(addresses,params(10));
        analyzeButton=button("เริ่มตรวจวิเคราะห์ไฟล์",Color.rgb(7,104,100));
        analyzeButton.setEnabled(false);root.addView(analyzeButton,params(15));
        analyzeButton.setOnClickListener(v->startScan());
        root.addView(text("3. ผลวิเคราะห์",17,true),params(28));
        output=text("ผลรายงานจะปรากฏตรงนี้เมื่อสแกนเสร็จ",13,false);
        output.setTextIsSelectable(true);output.setTypeface(Typeface.MONOSPACE);
        output.setPadding(dp(12),dp(12),dp(12),dp(12));output.setBackground(bg(panel,10));
        root.addView(output,params(10));
        copyButton=button("คัดลอกรายงาน",panel);root.addView(copyButton,params(12));
        copyButton.setEnabled(false);copyButton.setOnClickListener(v->{
            ClipboardManager m=(ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE);
            m.setPrimaryClip(ClipData.newPlainText("NSO Analyzer Report",report));
            Toast.makeText(this,"คัดลอกแล้ว",Toast.LENGTH_SHORT).show();
        });
        shareButton=button("แชร์รายงานเข้า ChatGPT",Color.rgb(7,104,100));
        shareButton.setEnabled(false);root.addView(shareButton,params(10));
        shareButton.setOnClickListener(v->{
            Intent send=new Intent(Intent.ACTION_SEND);
            send.setType("text/plain");
            send.putExtra(Intent.EXTRA_TEXT,
                "ช่วยวิเคราะห์รายงาน NSO นี้ต่อ ห้ามเดาตำแหน่ง FPS ที่ไม่ยืนยัน:\n"+report);
            startActivity(Intent.createChooser(send,"เลือก ChatGPT เพื่อส่งรายงาน"));
        });
        TextView foot=text("แอปนี้เป็นเครื่องมืออ่านและค้นหาเท่านั้น ยังไม่สร้างม็อด 60 FPS อัตโนมัติ และไม่เชื่อมกับแชทโดยไม่ผ่านการแชร์",12,false);
        foot.setTextColor(Color.rgb(168,179,196));root.addView(foot,params(24));
    }
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(request!=PICK_NSO||result!=RESULT_OK||data==null||data.getData()==null)return;
        chosen=data.getData(); filename="main.nso";
        try(Cursor c=getContentResolver().query(chosen,new String[]{OpenableColumns.DISPLAY_NAME},null,null,null)){
            if(c!=null&&c.moveToFirst())filename=c.getString(0);
        } catch(Exception ignored){}
        status.setText("เลือกแล้ว: "+filename);
        analyzeButton.setEnabled(true);shareButton.setEnabled(false);copyButton.setEnabled(false);
        output.setText("กด เริ่มตรวจวิเคราะห์ไฟล์ เพื่อสแกน");
    }
    private Set<Long> parseTargets(){
        Set<Long> result=new LinkedHashSet<>();
        for(String raw:addresses.getText().toString().split("[,;\\s]+")){
            String s=raw.trim();
            if(s.isEmpty())continue;
            try { result.add(Long.parseUnsignedLong(s.replaceFirst("(?i)^0x",""),16)); }
            catch(Exception e){throw new IllegalArgumentException("Offset ไม่ถูกต้อง: "+s);}
            if(result.size()>12)throw new IllegalArgumentException("ใส่ Offset ได้สูงสุด 12 ค่า");
        }
        return result;
    }
    private void startScan(){
        if(chosen==null)return;
        final Set<Long> targets;
        try{targets=parseTargets();}catch(Exception e){Toast.makeText(this,e.getMessage(),Toast.LENGTH_LONG).show();return;}
        analyzeButton.setEnabled(false);shareButton.setEnabled(false);copyButton.setEnabled(false);
        status.setText("กำลังวิเคราะห์ — โปรดเปิดแอปค้างไว้");output.setText("กำลังทำงาน...");
        worker.execute(()->{
            try{
                JSONObject json=NsoAnalyzer.analyze(this,chosen,filename,targets,
                    msg->runOnUiThread(()->status.setText(msg)));
                String text=json.toString(2);
                report=text;
                runOnUiThread(()->{
                    output.setText(text);
                    status.setText("วิเคราะห์เสร็จแล้ว ✓");
                    shareButton.setEnabled(true);copyButton.setEnabled(true);analyzeButton.setEnabled(true);
                });
            } catch(Exception ex){
                runOnUiThread(()->{
                    output.setText("วิเคราะห์ไม่สำเร็จ: "+ex.getClass().getSimpleName()+"\n"+ex.getMessage());
                    status.setText("เกิดข้อผิดพลาด");
                    analyzeButton.setEnabled(true);
                });
            }
        });
    }
    @Override protected void onDestroy(){worker.shutdownNow();super.onDestroy();}
}