package com.hok.skinfinder;

import android.app.Activity;
import android.content.Intent;
import android.content.ClipboardManager;
import android.content.ClipData;
import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.DocumentsContract;
import android.graphics.Typeface;
import android.content.res.ColorStateList;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.view.WindowManager;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PushbackInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class MainActivity extends Activity {
    private static final int FILES=11, FOLDER=12, EXPORT=13, EXPORT_BRIEF=14, INSPECT_FILE=15, INSPECT_EXPORT=16;
    private static final int GREEN=0xff13b394, BG=0xff101827, TEXT=0xffecf5ff, CARD=0xff26374b;
    private static final String[] WORDS={"skin","skinin","skinid","costume","hero","heroid","character","avatar","model","texture","material","prefab","mesh","asset","fashion","portrait","皮肤","英雄","模型","贴图"};
    private static final long FAST_LIMIT=32L*1024*1024;
    private static final int MAX_ROWS=25000;
    private final Handler ui=new Handler(Looper.getMainLooper());
    private final AtomicBoolean stop=new AtomicBoolean(false);
    private final List<Uri> selected=new ArrayList<>();
    private final List<JSONObject> hits=new ArrayList<>();
    private final List<JSONObject> largeUnknown=new ArrayList<>();
    private long excludedLogs=0, excludedPandora=0, metadataCount=0, bundles=0, textures=0, opaque=0;
    private Uri tree=null;
    private TextView status, output;
    private Button scan, export, copyBrief, exportBrief, chooseFile, chooseFolder, cancel;
    private Button inspectFile, copyInspection, saveInspection;
    private String lastInspection=null;
    private CheckBox deep;
    private volatile boolean busy=false;
    private long totalFiles=0,totalBytes=0,archives=0,errors=0,lastUpdate=0;
    private JSONArray finalResults=null;
    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout outer=new LinearLayout(this); outer.setOrientation(1);
        outer.setBackgroundColor(BG); outer.setPadding(px(16),px(10),px(16),px(10));
        outer.addView(text("HOK SKIN FINDER",23,true,GREEN));
        outer.addView(text("V1.6 • ตรวจโครงสร้าง ZIP / SQLite / UnityFSแบบออฟไลน์ • ไม่ต้อง Root",13,false,0xffb3cada));
        ScrollView scroll=new ScrollView(this);
        outer.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout body=new LinearLayout(this); body.setOrientation(1); scroll.addView(body);
        TextView expl=text("เลือกไฟล์เกมที่คัดลอกจาก OBB/data หรือโฟลเดอร์ที่อ่านได้ แอปจะตรวจชื่อไฟล์ ส่วนหัว และคำที่อาจเกี่ยวข้องกับ Skin/Hero/Texture แล้วส่งรายงาน JSON โดยไม่แก้ไขเกม\n\nหมายเหตุ: ไฟล์เข้ารหัสหรือรูปแบบเฉพาะอาจค้นหาได้เพียงส่วนหัวหรือข้อความที่มองเห็น",14,false,TEXT);
        expl.setPadding(0,px(16),0,px(16)); body.addView(expl);
        chooseFile=button("1. เลือกไฟล์เกม / OBB / MPK / ZIP"); body.addView(chooseFile);
        chooseFolder=button("2. เลือกโฟลเดอร์ไฟล์เกม"); body.addView(chooseFolder);
        inspectFile=button("เจาะโครงสร้างไฟล์เดี่ยว (ZIP / DB / UnityFS / MPK)"); body.addView(inspectFile);
        copyInspection=button("คัดลอกรายงานเจาะไฟล์"); body.addView(copyInspection);
        saveInspection=button("บันทึกรายงานเจาะไฟล์ TXT"); body.addView(saveInspection);
        deep=new CheckBox(this); deep.setText("สแกนละเอียดทุกไบต์ (อาจใช้เวลานาน)");
        deep.setTextColor(TEXT); deep.setButtonTintList(ColorStateList.valueOf(GREEN)); body.addView(deep);
        scan=button("3. เริ่มค้นหาสกิน"); body.addView(scan);
        cancel=button("หยุดสแกน"); body.addView(cancel);
        export=button("4. บันทึกรายงาน JSON ฉบับเต็ม"); body.addView(export);
        copyBrief=button("5. คัดลอกรายงานย่อ (ส่งในแชทได้เลย)"); body.addView(copyBrief);
        exportBrief=button("6. บันทึกรายงานย่อ .txt"); body.addView(exportBrief);
        status=text("ยังไม่ได้เลือกข้อมูล",14,true,TEXT);
        status.setPadding(0,px(14),0,px(14)); body.addView(status);
        body.addView(text("ผลวิเคราะห์เบื้องต้น",16,true,GREEN));
        output=text("เมื่อสแกนเสร็จจะแสดงไฟล์ที่มีเบาะแสสกินหรือฮีโร่ คะแนนเป็นการจัดอันดับเบื้องต้น ไม่ใช่ผลยืนยันว่าเป็นสกิน",13,false,TEXT);
        output.setPadding(px(10),px(12),px(10),px(20)); output.setBackgroundColor(CARD);
        output.setTextIsSelectable(true); body.addView(output);
        setContentView(outer);
        chooseFile.setOnClickListener(v->{
            Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("*/*");
            i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true);
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
            startActivityForResult(i,FILES);
        });
        chooseFolder.setOnClickListener(v->{
            Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
            startActivityForResult(i,FOLDER);
        });
        inspectFile.setOnClickListener(v->{
            Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("*/*");
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivityForResult(i,INSPECT_FILE);
        });
        copyInspection.setOnClickListener(v->{
            if(lastInspection==null)return;
            ClipboardManager cb=(ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
            if(cb!=null){
                cb.setPrimaryClip(ClipData.newPlainText("HOK Deep File Report",lastInspection));
                message("คัดลอกรายงานเจาะไฟล์แล้ว กลับ ChatGPT และกดวางได้เลย");
            }
        });
        saveInspection.setOnClickListener(v->{
            if(lastInspection==null)return;
            Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);
            i.setType("text/plain");i.addCategory(Intent.CATEGORY_OPENABLE);
            i.putExtra(Intent.EXTRA_TITLE,"HOK-Deep-Inspection.txt");
            startActivityForResult(i,INSPECT_EXPORT);
        });
        scan.setOnClickListener(v->startScan());
        cancel.setOnClickListener(v->{stop.set(true);status.setText("กำลังหยุดสแกน...");});
        copyBrief.setOnClickListener(v->{
            if(finalResults==null)return;
            ClipboardManager cb=(ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
            if(cb!=null){
                cb.setPrimaryClip(ClipData.newPlainText("HOK Skin Finder brief",briefText()));
                message("คัดลอกรายงานย่อแล้ว กลับไป ChatGPT และกดวางข้อความได้เลย");
            }
        });
        exportBrief.setOnClickListener(v->{
            if(finalResults==null)return;
            Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);
            i.setType("text/plain"); i.addCategory(Intent.CATEGORY_OPENABLE);
            i.putExtra(Intent.EXTRA_TITLE,"HOK-Skin-Finder-brief.txt");
            startActivityForResult(i,EXPORT_BRIEF);
        });
        export.setOnClickListener(v->{
            if(finalResults==null)return;
            Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);
            i.setType("application/json"); i.addCategory(Intent.CATEGORY_OPENABLE);
            i.putExtra(Intent.EXTRA_TITLE,"HOK-Skin-Finder-report.json");
            startActivityForResult(i,EXPORT);
        });
        controls();
    }
    private int px(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
    private TextView text(String v,int size,boolean bold,int color) {
        TextView t=new TextView(this);t.setText(v);t.setTextSize(size);t.setTextColor(color);
        if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return t;
    }
    private Button button(String v) {
        Button b=new Button(this);b.setText(v);b.setAllCaps(false);
        b.setTextColor(TEXT);b.setBackgroundTintList(ColorStateList.valueOf(CARD));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,px(52));p.bottomMargin=px(7);b.setLayoutParams(p);return b;
    }
    private void controls(){
        scan.setEnabled(!busy);chooseFile.setEnabled(!busy);chooseFolder.setEnabled(!busy);
        cancel.setEnabled(busy);export.setEnabled(!busy && finalResults!=null);
        inspectFile.setEnabled(!busy);
        copyInspection.setEnabled(!busy && lastInspection!=null);
        saveInspection.setEnabled(!busy && lastInspection!=null);
        copyBrief.setEnabled(!busy && finalResults!=null);
        exportBrief.setEnabled(!busy && finalResults!=null);
    }
    @Override protected void onActivityResult(int req,int result,Intent data){
        super.onActivityResult(req,result,data);
        if(result!=RESULT_OK||data==null)return;
        if(req==INSPECT_EXPORT){
            if(data.getData()!=null&&lastInspection!=null){
                try(OutputStream out=getContentResolver().openOutputStream(data.getData(),"w")){
                    if(out==null)throw new IOException("No output stream");
                    out.write(lastInspection.getBytes(StandardCharsets.UTF_8));
                    status.setText("บันทึกรายงานเจาะไฟล์สำเร็จ");
                }catch(Exception e){message("บันทึกไม่ได้: "+e.getMessage());}
            }
            return;
        }
        if(req==INSPECT_FILE){
            Uri picked=data.getData();
            if(picked!=null)deepInspect(picked);
            return;
        }
        if(req==EXPORT_BRIEF){
            if(data.getData()!=null&&finalResults!=null){
                try(OutputStream out=getContentResolver().openOutputStream(data.getData(),"w")){
                    if(out==null)throw new IOException("No output stream");
                    out.write(briefText().getBytes(StandardCharsets.UTF_8));
                    status.setText("บันทึก HOK-Skin-Finder-brief.txt สำเร็จ");
                }catch(Exception e){message("บันทึกรายงานย่อไม่ได้: "+e.getMessage());}
            }
            return;
        }
        if(req==EXPORT){
            if(data.getData()!=null&&finalResults!=null){
                try(OutputStream out=getContentResolver().openOutputStream(data.getData(),"w")){
                    if(out==null)throw new IOException("No output stream");
                    JSONObject r=new JSONObject();
                    r.put("app","HOK Skin Finder 1.6");r.put("scannedFiles",totalFiles);
                    r.put("scannedBytes",totalBytes);r.put("archives",archives);r.put("errors",errors);
                    r.put("note","Results are heuristic clues, not verified skin assets");
                    r.put("rows",finalResults);
                    r.put("excludedLogFiles",excludedLogs);
                    r.put("excludedPandoraFiles",excludedPandora);
                    r.put("metadataFiles",metadataCount);
                    r.put("assetBundles",bundles);
                    r.put("textures",textures);
                    r.put("opaquePackages",opaque);
                    JSONArray unresolved=new JSONArray();
                    for(JSONObject item:largeUnknown)unresolved.put(item);
                    r.put("largestUnknownFiles",unresolved);
                    out.write(r.toString(2).getBytes(StandardCharsets.UTF_8));
                    status.setText("บันทึกสำเร็จ ส่งไฟล์ JSON กลับมาให้วิเคราะห์ต่อได้");
                }catch(Exception e){message("บันทึกไม่ได้: "+e.getMessage());}
            }return;
        }
        selected.clear();tree=null;finalResults=null;
        try {
            if(req==FILES) {
                if(data.getClipData()!=null){
                    for(int j=0;j<data.getClipData().getItemCount();j++)selected.add(data.getClipData().getItemAt(j).getUri());
                }else if(data.getData()!=null)selected.add(data.getData());
                for(Uri u:selected){
                    try{getContentResolver().takePersistableUriPermission(u,Intent.FLAG_GRANT_READ_URI_PERMISSION);}
                    catch(Exception ignored){}
                }
                status.setText("เลือกแล้ว "+selected.size()+" ไฟล์");
            }else if(req==FOLDER){
                tree=data.getData();
                if(tree!=null){
                    try{getContentResolver().takePersistableUriPermission(tree,Intent.FLAG_GRANT_READ_URI_PERMISSION);}
                    catch(Exception ignored){}
                    status.setText("เลือกโฟลเดอร์แล้ว พร้อมสแกน");
                }
            }
        }catch(Exception e){message("เลือกไฟล์ไม่สำเร็จ: "+e.getMessage());}
        controls();
    }
    private void deepInspect(Uri picked){
        if(busy)return;
        busy=true;lastInspection=null;status.setText("กำลังอ่านโครงสร้างไฟล์จริง...");
        output.setText("อ่านส่วนหัวและรายการข้อมูลที่รองรับ โดยไม่เปลี่ยนไฟล์เกม");
        controls();
        new Thread(()->{
            String report;
            try{
                String name=metadata(picked)[0];
                report=DeepFileInspector.inspect(this,getContentResolver(),picked,name);
            }catch(Exception e){
                report="ไม่สามารถตรวจโครงสร้างไฟล์: "+e.getClass().getSimpleName()+": "+e.getMessage();
            }
            final String ready=report;
            ui.post(()->{
                lastInspection=ready;
                busy=false;
                status.setText("วิเคราะห์ไฟล์เสร็จแล้ว เลือกคัดลอกหรือบันทึกรายงาน");
                output.setText(ready);
                controls();
            });
        },"hok-deep-file-inspector").start();
    }
    private void startScan(){
        if(busy)return;
        if(tree==null&&selected.isEmpty()){message("กรุณาเลือกไฟล์หรือโฟลเดอร์ก่อน");return;}
        busy=true;stop.set(false);totalFiles=0;totalBytes=0;archives=0;errors=0;
        finalResults=null;hits.clear();largeUnknown.clear();lastUpdate=0;
        excludedLogs=0;excludedPandora=0;metadataCount=0;bundles=0;textures=0;opaque=0;
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        status.setText("กำลังสแกน...");output.setText("กำลังอ่านข้อมูล");controls();
        final Uri root=tree;
        final List<Uri> list=new ArrayList<>(selected);
        final long limit=deep.isChecked()?Long.MAX_VALUE:FAST_LIMIT;
        new Thread(()->{
            try{
                if(root!=null){
                    String id=DocumentsContract.getTreeDocumentId(root);
                    walk(root,id,"",0,limit);
                }
                for(Uri u:list){
                    if(stop.get())break;
                    String[] s=metadata(u);
                    scanUri(u,s[0],toLong(s[1]),limit);
                }
            }catch(Exception e){errors++;}
            Collections.sort(hits,Comparator.comparingInt(o->-o.optInt("score",0)));
            Collections.sort(largeUnknown,Comparator.comparingLong(o->-o.optLong("size",0)));
            if(largeUnknown.size()>30)largeUnknown.subList(30,largeUnknown.size()).clear();
            JSONArray rows=new JSONArray();
            for(JSONObject h:hits)rows.put(h);
            finalResults=rows;
            final String report=displayReport();
            ui.post(()->{
                busy=false;
                getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                status.setText((stop.get()?"หยุดแล้ว":"สแกนเสร็จ")+" • "+totalFiles+" ไฟล์ • "+archives+" archive • ผิดพลาด "+errors);
                output.setText(report);controls();
            });
        },"hok-scanner").start();
    }
    private void walk(Uri root,String id,String prefix,int depth,long limit){
        if(stop.get()||depth>32||totalFiles>=MAX_ROWS)return;
        Uri children=DocumentsContract.buildChildDocumentsUriUsingTree(root,id);
        String[] fields={DocumentsContract.Document.COLUMN_DOCUMENT_ID,DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,DocumentsContract.Document.COLUMN_SIZE};
        try(Cursor c=getContentResolver().query(children,fields,null,null,null)){
            if(c==null){errors++;return;}
            while(c.moveToNext()&&!stop.get()&&totalFiles<MAX_ROWS){
                String next=c.getString(0),name=c.getString(1),mime=c.getString(2);
                long size=c.isNull(3)?-1:c.getLong(3);
                String path=prefix.isEmpty()?name:prefix+"/"+name;
                if(DocumentsContract.Document.MIME_TYPE_DIR.equals(mime))walk(root,next,path,depth+1,limit);
                else scanUri(DocumentsContract.buildDocumentUriUsingTree(root,next),path,size,limit);
            }
        }catch(Exception e){errors++;}
    }
    private String[] metadata(Uri u){
        String name=u.getLastPathSegment(),size="-1";
        try(Cursor c=getContentResolver().query(u,new String[]{DocumentsContract.Document.COLUMN_DISPLAY_NAME,DocumentsContract.Document.COLUMN_SIZE},null,null,null)){
            if(c!=null&&c.moveToFirst()){
                if(!c.isNull(0))name=c.getString(0);
                if(!c.isNull(1))size=String.valueOf(c.getLong(1));
            }
        }catch(Exception ignored){}
        return new String[]{name==null?"file":name,size};
    }
    private long toLong(String s){try{return Long.parseLong(s);}catch(Exception e){return -1;}}
    private void scanUri(Uri u,String path,long size,long limit){
        if(stop.get())return;
        try(InputStream s=getContentResolver().openInputStream(u)){
            if(s==null)throw new IOException("Null input stream");
            scanInput(s,path,size,limit);
        }catch(Exception e){errors++;addError(path,e);}
    }
    private void addError(String path,Exception e){
        if(hits.size()>=MAX_ROWS)return;
        try{JSONObject row=new JSONObject();row.put("path",path);row.put("error",e.getClass().getSimpleName());
            row.put("score",0);hits.add(row);}catch(Exception ignored){}
    }
    private void scanInput(InputStream input,String path,long size,long limit)throws IOException{
        if(stop.get())return;
        PushbackInputStream in=new PushbackInputStream(new BufferedInputStream(input,65536),32);
        byte[] h=new byte[16];int n=in.read(h);if(n<0)n=0;if(n>0)in.unread(h,0,n);
        if(n>=4&&h[0]=='P'&&h[1]=='K'&&h[2]==3&&h[3]==4){
            archives++;ZipInputStream zip=new ZipInputStream(in);
            ZipEntry entry;int count=0;
            while(!stop.get()&&count<12000&&(entry=zip.getNextEntry())!=null){
                count++;if(entry.isDirectory())continue;
                String entryName=entry.getName().replace('\\','/');
                scanRaw(zip,path+"!/"+entryName,entry.getSize(),limit);
                if(stop.get())break;
                zip.closeEntry();
            }
        }else scanRaw(in,path,size,limit);
    }
    private void scanRaw(InputStream in,String path,long size,long limit)throws IOException{
        totalFiles++;
        if(totalFiles%25==0){
            long t=System.currentTimeMillis();
            if(t-lastUpdate>750){lastUpdate=t;ui.post(()->status.setText("กำลังอ่านไฟล์: "+totalFiles+" • "+path));}
        }
        if(hits.size()>=MAX_ROWS||stop.get())return;
        final int block=65536,overlap=128;
        byte[] buff=new byte[block+overlap];
        boolean[] seen=new boolean[WORDS.length];
        long count=0;int tail=0,first=0;
        byte[] head=new byte[16];
        while(!stop.get()&&count<limit){
            int r=in.read(buff,tail,(int)Math.min(block,limit-count));
            if(r<0)break;if(r==0)continue;
            int length=tail+r;
            if(first<16){int m=Math.min(16-first,length-tail);System.arraycopy(buff,tail,head,first,m);first+=m;}
            String chunk=new String(buff,0,length,StandardCharsets.ISO_8859_1).toLowerCase(Locale.ROOT);
            for(int i=0;i<WORDS.length;i++){
                if(seen[i])continue;
                String key=WORDS[i];
                String utf8=new String(key.getBytes(StandardCharsets.UTF_8),StandardCharsets.ISO_8859_1).toLowerCase(Locale.ROOT);
                String utf16=new String(key.getBytes(StandardCharsets.UTF_16LE),StandardCharsets.ISO_8859_1).toLowerCase(Locale.ROOT);
                if(chunk.contains(utf8)||chunk.contains(utf16))seen[i]=true;
            }
            count+=r;totalBytes+=r;
            tail=Math.min(overlap,length);
            System.arraycopy(buff,length-tail,buff,0,tail);
        }
        List<String> markers=new ArrayList<>();
        String lower=path.toLowerCase(Locale.ROOT);
        for(int i=0;i<WORDS.length;i++)if(seen[i]||lower.contains(WORDS[i]))markers.add(WORDS[i]);
        String format=signature(head,first,lower);
        String category=category(lower,format);
        int score=0;
        if(category.equals("asset_bundle")){bundles++;score+=45;}
        else if(category.equals("texture")){textures++;score+=45;}
        else if(category.equals("opaque_package")){opaque++;score+=25;}
        else if(category.equals("log"))excludedLogs++;
        else if(category.equals("pandora"))excludedPandora++;
        else if(category.equals("metadata"))metadataCount++;
        else if(category.equals("pandora_bundle")){excludedPandora++;score+=8;}
        for(String m:markers){
            if(m.equals("skin")||m.equals("skinin")||m.equals("skinid")||m.equals("皮肤")||m.equals("costume"))score+=10;
            else if(m.contains("hero")||m.equals("英雄")||m.equals("character"))score+=5;
            else if(m.equals("texture")||m.equals("model")||m.equals("material")||m.equals("prefab")||m.equals("mesh")||m.equals("贴图"))score+=3;
            else score+=1;
        }
        if(lower.contains("skin")||lower.contains("costume")||lower.contains("hero")||lower.contains("character"))score+=30;
        if(size>=1048576 && category.equals("opaque_package"))score+=10;
        try{
            JSONObject item=new JSONObject();
            item.put("path",path);item.put("size",size);item.put("readBytes",count);
            item.put("format",format);item.put("category",category);
            item.put("score",Math.min(100,score));
            item.put("partial",count>=limit && (size<0||size>count));
            item.put("markers",new JSONArray(markers));
            // Logs, UI minigames, and IL2CPP metadata are not skin texture/model files.
            boolean useful=category.equals("asset_bundle")||category.equals("texture")||
                    category.equals("opaque_package")||category.equals("pandora_bundle")||
                    (category.equals("other")&&!markers.isEmpty());
            if(useful)hits.add(item);
            if(category.equals("opaque_package")||category.equals("other")){
                if(size>=1048576){
                    JSONObject info=new JSONObject();
                    info.put("path",path);info.put("size",size);info.put("format",format);
                    info.put("markers",new JSONArray(markers));
                    largeUnknown.add(info);
                    if(largeUnknown.size()>150){
                        Collections.sort(largeUnknown,Comparator.comparingLong(o->-o.optLong("size",0)));
                        largeUnknown.subList(75,largeUnknown.size()).clear();
                    }
                }
            }
        }catch(Exception ignored){}
    }
    private static String category(String lower,String format){
        if(lower.contains("/bqlog/")||lower.endsWith(".log")||lower.endsWith(".logcompr")||
                lower.contains("/logs/"))return "log";
        if(lower.endsWith("global-metadata.dat")||lower.contains("/metadata/"))return "metadata";
        if(lower.contains("/pandora/")){
            if(lower.endsWith(".assetbundle")||format.startsWith("Unity"))return "pandora_bundle";
            return "pandora";
        }
        if(format.startsWith("Unity")||lower.endsWith(".assetbundle")||lower.endsWith(".unity3d")||
                lower.endsWith(".bundle")||lower.endsWith(".ab"))return "asset_bundle";
        if(format.contains("texture")||format.contains("image")||
                lower.endsWith(".astc")||lower.endsWith(".ktx")||lower.endsWith(".ktx2")||
                lower.endsWith(".pvr")||lower.endsWith(".png")||lower.endsWith(".jpg")||
                lower.endsWith(".jpeg")||lower.endsWith(".dds"))return "texture";
        if(lower.endsWith(".mpk")||lower.endsWith(".pak")||lower.endsWith(".obb")||
                lower.endsWith(".dat")||lower.endsWith(".res")||
                lower.endsWith(".bytes")||lower.endsWith(".bin"))return "opaque_package";
        return "other";
    }

    private static String signature(byte[] b,int n,String lower){
        if(n>=7&&ascii(b,"UnityFS"))return "UnityFS";
        if(n>=8&&ascii(b,"UnityRaw"))return "UnityRaw";
        if(n>=8&&ascii(b,"UnityWeb"))return "UnityWeb";
        if(n>=4&&b[0]=='P'&&b[1]=='K')return "ZIP";
        if(n>=4&&(b[0]&255)==0x89&&b[1]=='P'&&b[2]=='N'&&b[3]=='G')return "PNG image";
        if(n>=3&&(b[0]&255)==255&&(b[1]&255)==216&&(b[2]&255)==255)return "JPEG image";
        if(n>=4&&ascii(b,"DDS "))return "DDS texture";
        if(n>=4&&(b[0]&255)==0x13&&(b[1]&255)==0xab&&(b[2]&255)==0xa1&&(b[3]&255)==0x5c)return "ASTC texture";
        if(n>=4&&(b[0]&255)==0xab&&b[1]=='K'&&b[2]=='T'&&b[3]=='X')return "KTX texture";
        if(n>=4&&(b[0]&255)==0x28&&(b[1]&255)==0xb5&&(b[2]&255)==0x2f&&(b[3]&255)==0xfd)return "Zstd compressed";
        if(n>=4&&(b[0]&255)==0x04&&(b[1]&255)==0x22&&(b[2]&255)==0x4d&&(b[3]&255)==0x18)return "LZ4 compressed";
        if(n>=4&&ascii(b,"OggS"))return "OGG";
        if(n>=4&&ascii(b,"FSB5"))return "FMOD";
        if(n>=4&&(b[0]&255)==0x7f&&b[1]=='E'&&b[2]=='L'&&b[3]=='F')return "ELF";
        if(n>=4&&b[0]=='P'&&b[1]=='V'&&b[2]=='R'&&(b[3]&255)==3)return "PVR texture";
        if(lower.endsWith(".mpk"))return "MPK (unknown container)";
        if(lower.endsWith(".pak"))return "PAK (unknown container)";
        if(lower.endsWith(".obb"))return "OBB (opaque)";
        if(lower.endsWith(".dat"))return "DAT";
        if(lower.endsWith(".json"))return "JSON";
        return "unknown";
    }
    private static boolean ascii(byte[] b,String x){for(int i=0;i<x.length();i++)if(b[i]!=(byte)x.charAt(i))return false;return true;}
    private String briefText(){
        StringBuilder b=new StringBuilder();
        b.append("HOK Skin Finder V1.6 - Targeted asset report\n");
        b.append("Scanned files: ").append(totalFiles).append("\n");
        b.append("Scanned MB: ").append(totalBytes/1048576).append("\n");
        b.append("ZIP archives: ").append(archives).append("\n");
        b.append("Scan errors: ").append(errors).append("\n");
        b.append("Excluded log files: ").append(excludedLogs).append("\n");
        b.append("Excluded Pandora UI files: ").append(excludedPandora).append("\n");
        b.append("IL2CPP metadata files: ").append(metadataCount).append("\n");
        b.append("Asset bundles: ").append(bundles).append("\n");
        b.append("Textures: ").append(textures).append("\n");
        b.append("Opaque packages: ").append(opaque).append("\n");
        b.append("Candidate entries: ").append(finalResults==null?0:finalResults.length()).append("\n\n");
        b.append("TOP RESOURCE CANDIDATES\n");
        if(finalResults!=null){
            int top=Math.min(25,finalResults.length());
            for(int i=0;i<top;i++){
                JSONObject o=finalResults.optJSONObject(i);
                if(o==null)continue;
                b.append("#").append(i+1).append(" score=").append(o.optInt("score")).append(" category=").append(o.optString("category")).append("\n");
                b.append("path: ").append(o.optString("path")).append("\n");
                b.append("format: ").append(o.optString("format")).append(" size=").append(o.optLong("size")).append("\n");
                b.append("markers: ").append(o.optJSONArray("markers")).append("\n\n");
                if(b.length()>17000)break;
            }
        }
        b.append("\nLARGEST OPAQUE OR UNKNOWN FILES\n");
        int n=Math.min(20,largeUnknown.size());
        for(int i=0;i<n;i++){
            JSONObject o=largeUnknown.get(i);
            b.append("#").append(i+1).append(" ").append(o.optLong("size")).append(" bytes | ")
                .append(o.optString("format")).append(" | ").append(o.optString("path")).append("\n");
            if(b.length()>19500)break;
        }
        b.append("\nThese are heuristic leads, not verified skin assets. No game files were modified.");
        return b.toString();
    }
    private String displayReport(){
        StringBuilder s=new StringBuilder();
        s.append("ตรวจ ").append(totalFiles).append(" ไฟล์ / ").append(totalBytes/1048576).append(" MB\n");
        s.append("AssetBundles: ").append(bundles).append(" | รูปภาพ/Texture: ").append(textures).append("\n");
        s.append("ไฟล์แพ็กเกจไม่ทราบโครงสร้าง: ").append(opaque).append("\n");
        s.append("คัดออก: Log ").append(excludedLogs).append(" / Pandora ").append(excludedPandora).append("\n\n");
        s.append("ไฟล์ทรัพยากรที่น่าตรวจต่อ:\n");
        int top=Math.min(25,hits.size());
        for(int i=0;i<top;i++){
            JSONObject o=hits.get(i);
            s.append(i+1).append(". [").append(o.optInt("score")).append("] ")
                .append(o.optString("path")).append("\n");
            s.append(o.optString("category")).append(" • ").append(o.optString("format"))
                .append(" • ").append(o.optJSONArray("markers")).append("\n\n");
        }
        if(hits.isEmpty())s.append("ยังไม่พบไฟล์ตรงตามลักษณะที่สแกน\n");
        s.append("\nไฟล์ใหญ่ที่ยังไม่ทราบโครงสร้าง:\n");
        for(int i=0;i<Math.min(10,largeUnknown.size());i++){
            JSONObject o=largeUnknown.get(i);
            s.append(o.optLong("size")/1048576).append(" MB: ").append(o.optString("path")).append("\n");
        }
        s.append("\nปุ่มคัดลอกรายงานย่อใช้ส่งผลกลับแชทได้");
        return s.toString();
    }
    private void message(String x){Toast.makeText(this,x,Toast.LENGTH_LONG).show();if(status!=null)status.setText(x);}
    @Override protected void onDestroy(){stop.set(true);super.onDestroy();}
}
