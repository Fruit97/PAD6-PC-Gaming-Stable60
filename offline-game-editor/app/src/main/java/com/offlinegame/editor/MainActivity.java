package com.offlinegame.editor;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.View;
import android.widget.*;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.util.*;
import java.util.regex.*;
import java.util.zip.*;

public class MainActivity extends Activity {
  private static final int OPEN_SAVE=1, SAVE_EDITED=2, SAVE_ORIGINAL=3, BACKUP_APK=4, OPEN_APK=5;
  private static final int MAX_SAVE=2_000_000, MAX_APK=250_000_000;
  private static final String NUMBER="-?(?:0|[1-9][0-9]*)(?:\\.[0-9]+)?(?:[eE][+-]?[0-9]+)?";
  private static final Pattern VALID=Pattern.compile(NUMBER);
  private static final Pattern JSON=Pattern.compile("\"([^\"\\\\]+)\"\\s*:\\s*("+NUMBER+")");
  private static final Pattern INI=Pattern.compile("(?m)^[ \\t]*([\\p{L}_][\\p{L}\\p{N}_.-]*)[ \\t]*[=:][ \\t]*("+NUMBER+")[ \\t]*(?:[;#][^\\r\\n]*)?$");
  private static final Pattern XML=Pattern.compile("<([\\p{L}_][\\p{L}\\p{N}_.:-]*)>\\s*("+NUMBER+")\\s*</\\1\\s*>");
  private static final Pattern ATTR=Pattern.compile("([\\p{L}_][\\p{L}\\p{N}_.:-]*)\\s*=\\s*([\"'])("+NUMBER+")\\2");
  private static final Pattern FIELD_HINT=Pattern.compile("(?i)(money|coin|gem|gold|cash|credit|diamond|silver|point|hp|health|energy|stamina|level|exp|quantity|amount|เหรียญ|เพชร|เงิน|เลเวล)");

  static class Field {
    final String key,value; final int from,to;
    Field(String k,String v,int f,int t){key=k;value=v;from=f;to=t;}
  }
  static class AppEntry {
    final String label,pkg,version;
    final String base;
    final String[] splits;
    AppEntry(String a,String b,String c,String d,String[] e){label=a;pkg=b;version=c;base=d;splits=e;}
  }
  private final ArrayList<Field> fields=new ArrayList<>();
  private final ArrayList<AppEntry> apps=new ArrayList<>();
  private String content,filename="example.json",query="",appQuery="";
  private byte[] original;
  private Uri sourceUri;
  private AppEntry selectedApp;
  private LinearLayout gameTab,saveTab,inspectTab,gameList,fieldList,root;
  private EditText searchField,searchApp;
  private TextView fileInfo,apkInfo;
  private String demo="{\n  \"player\": {\n    \"money\": 1250,\n    \"coins\": 42,\n    \"gems\": 10,\n    \"level\": 5,\n    \"hp\": 100\n  },\n  \"inventory\": [{\"quantity\": 2}]\n}\n";

  @Override public void onCreate(Bundle saved){
    super.onCreate(saved);
    getWindow().setStatusBarColor(Color.rgb(8,17,32));
    ScrollView scroll=new ScrollView(this);
    scroll.setFillViewport(true);
    scroll.setBackgroundColor(0xff081120);
    root=new LinearLayout(this);root.setPadding(18,22,18,45);root.setOrientation(LinearLayout.VERTICAL);
    scroll.addView(root);setContentView(scroll);
    text(root,"OFFLINE PATCH STUDIO",25,Color.WHITE);
    text(root,"V2.0 • เกมออฟไลน์ • ไม่รูท • ไม่มีอินเทอร์เน็ต",13,0xff90bfd1);
    LinearLayout tabs=new LinearLayout(this);tabs.setOrientation(LinearLayout.HORIZONTAL);root.addView(tabs);
    tab(tabs,"แอป",0);tab(tabs,"เซฟเกม",1);tab(tabs,"APK",2);

    gameTab=section();
    text(gameTab,"แอปที่เปิดจากหน้าจอได้",19,Color.WHITE);
    text(gameTab,"เลือกรายการเพื่อตรวจข้อมูล สำรอง APK หรือเปิดเซฟที่เลือกเอง (ไม่มีสิทธิ์เข้าข้อมูลส่วนตัวของแอปอื่น)",12,0xffaab4c1);
    searchApp=new EditText(this);searchApp.setTextColor(Color.WHITE);searchApp.setHintTextColor(0xff8296ac);
    searchApp.setSingleLine(true);searchApp.setHint("ค้นหาชื่อแอปหรือแพ็กเกจ");gameTab.addView(searchApp);
    searchApp.addTextChangedListener(new TextWatcher(){
      public void beforeTextChanged(CharSequence s,int st,int c,int a){}
      public void onTextChanged(CharSequence s,int st,int b,int c){appQuery=s.toString().toLowerCase(Locale.ROOT);drawApps();}
      public void afterTextChanged(Editable e){}
    });
    button(gameTab,"↻  รีเฟรชรายการแอป",()->loadApps());
    gameList=sectionInner(gameTab);

    saveTab=section();
    text(saveTab,"เครื่องมือแก้ไฟล์เซฟออฟไลน์",19,Color.WHITE);
    text(saveTab,"รองรับ JSON / INI / XML ที่เป็นข้อความธรรมดา",12,0xffaab4c1);
    button(saveTab,"📂  เลือกไฟล์เซฟ",()->chooseSave());
    button(saveTab,"🎮  ทดลองด้วยไฟล์ตัวอย่าง",()->useDemo());
    fileInfo=text(saveTab,"",14,0xff44dfb5);
    searchField=new EditText(this);searchField.setHint("ค้นหา money, gems, coins, level...");
    searchField.setTextColor(Color.WHITE);searchField.setHintTextColor(0xff8296ac);searchField.setSingleLine(true);
    saveTab.addView(searchField);
    searchField.addTextChangedListener(new TextWatcher(){
      public void beforeTextChanged(CharSequence s,int st,int c,int a){}
      public void onTextChanged(CharSequence s,int st,int b,int c){query=s.toString().toLowerCase(Locale.ROOT);drawFields();}
      public void afterTextChanged(Editable e){}
    });
    fieldList=sectionInner(saveTab);
    button(saveTab,"⚡  ปรับค่าเงิน/พลังที่ตรวจพบเป็น 999999",()->bulkEdit());
    button(saveTab,"↶  คืนค่าเซฟต้นฉบับ",()->restore());
    button(saveTab,"💾  ส่งออกเซฟที่แก้ไข",()->export(SAVE_EDITED));
    button(saveTab,"🛡  ส่งออกเซฟต้นฉบับ",()->export(SAVE_ORIGINAL));
    button(saveTab,"✍  เขียนทับไฟล์เซฟที่เลือก (ยืนยันก่อน)",()->overwrite());

    inspectTab=section();
    text(inspectTab,"ตรวจสอบโครงสร้าง APK",19,Color.WHITE);
    text(inspectTab,"เลือก APK จากเครื่องเพื่อตรวจแพ็กเกจ เวอร์ชัน สิทธิ์ที่ร้องขอ และจำนวนไฟล์ภายใน",13,0xffacb6c6);
    button(inspectTab,"📦  เลือก APK เพื่อตรวจสอบ",()->chooseApk());
    apkInfo=text(inspectTab,"ยังไม่ได้เลือกไฟล์ APK",14,0xffb3c9d8);
    text(inspectTab,"ข้อจำกัด: ไม่ถอดรหัสเกม ไม่ข้ามการชำระเงิน ไม่ปลดล็อกไอเทมจากเซิร์ฟเวอร์ และไม่แก้ไข RAM ของเกมอื่น\nไฟล์สำรองจากแอปที่ใช้ split APK เป็น ZIP สำหรับเก็บรักษา ไม่ใช่ APK กดติดตั้งโดยตรง",12,0xff94aabd);
    useDemo();
    showTab(0);
    loadApps();
  }
  private LinearLayout section(){
    LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);root.addView(l);
    return l;
  }
  private LinearLayout sectionInner(LinearLayout parent){
    LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);parent.addView(l);return l;
  }
  private TextView text(LinearLayout parent,String message,int size,int color){
    TextView v=new TextView(this);v.setText(message);v.setTextColor(color);v.setTextSize(size);v.setPadding(0,9,0,9);
    parent.addView(v);return v;
  }
  private void button(LinearLayout parent,String label,Runnable action){
    Button b=new Button(this);b.setText(label);b.setAllCaps(false);
    parent.addView(b,new LinearLayout.LayoutParams(-1,-2));b.setOnClickListener(v->action.run());
  }
  private void tab(LinearLayout parent,String name,int id){
    Button b=new Button(this);b.setText(name);b.setAllCaps(false);b.setTextSize(12);
    parent.addView(b,new LinearLayout.LayoutParams(0,-2,1f));b.setOnClickListener(v->showTab(id));
  }
  private void showTab(int id){
    if(gameTab!=null)gameTab.setVisibility(id==0?View.VISIBLE:View.GONE);
    if(saveTab!=null)saveTab.setVisibility(id==1?View.VISIBLE:View.GONE);
    if(inspectTab!=null)inspectTab.setVisibility(id==2?View.VISIBLE:View.GONE);
  }
  private void loadApps(){
    apps.clear();
    PackageManager pm=getPackageManager();
    Intent launchers=new Intent(Intent.ACTION_MAIN);launchers.addCategory(Intent.CATEGORY_LAUNCHER);
    List<ResolveInfo> found=pm.queryIntentActivities(launchers,0);
    HashSet<String> seen=new HashSet<>();
    for(ResolveInfo r:found){
      if(r.activityInfo==null||r.activityInfo.applicationInfo==null)continue;
      ApplicationInfo ai=r.activityInfo.applicationInfo;
      if(!seen.add(ai.packageName))continue;
      String label=String.valueOf(ai.loadLabel(pm));
      String version="?";
      try {PackageInfo pi=pm.getPackageInfo(ai.packageName,0);version=pi.versionName==null?"?":pi.versionName;}catch(Exception ignored){}
      apps.add(new AppEntry(label,ai.packageName,version,ai.sourceDir,ai.splitSourceDirs));
    }
    Collections.sort(apps,(a,b)->a.label.compareToIgnoreCase(b.label));
    drawApps();
  }
  private void drawApps(){
    if(gameList==null)return;
    gameList.removeAllViews();int shown=0;
    for(AppEntry a:apps){
      if(!(a.label.toLowerCase(Locale.ROOT).contains(appQuery)||a.pkg.toLowerCase(Locale.ROOT).contains(appQuery)))continue;
      if(shown++>=150)break;
      button(gameList,"🎮  "+a.label+"\n"+a.pkg+"  •  "+a.version,()->appMenu(a));
    }
    if(shown==0)text(gameList,"ไม่พบแอปที่ตรงกับการค้นหา",14,0xffefb36a);
  }
  private void appMenu(AppEntry a){
    final String[] options={"🔎  ดูข้อมูลแอปและสิทธิ์","📦  สำรองไฟล์ APK (ZIP)","📂  เลือกไฟล์เซฟเพื่อแก้ไข","▶  เปิดแอป"};
    new AlertDialog.Builder(this).setTitle(a.label).setItems(options,(d,which)->{
      if(which==0)showAppInfo(a);
      if(which==1)backupApp(a);
      if(which==2){showTab(1);chooseSave();}
      if(which==3){
        try{
          Intent launch=getPackageManager().getLaunchIntentForPackage(a.pkg);
          if(launch==null)throw new Exception("เปิดแอปไม่ได้");
          startActivity(launch);
        }catch(Exception e){notice(e.getMessage());}
      }
    }).setNegativeButton("ยกเลิก",null).show();
  }
  private void showAppInfo(AppEntry a){
    StringBuilder s=new StringBuilder("แพ็กเกจ: "+a.pkg+"\nเวอร์ชัน: "+a.version+
      "\nSplit APK: "+(a.splits==null?0:a.splits.length)+"\n");
    try{
      PackageInfo p=getPackageManager().getPackageInfo(a.pkg,PackageManager.GET_PERMISSIONS);
      s.append("\nสิทธิ์ที่ร้องขอ:\n");
      if(p.requestedPermissions!=null)for(String permission:p.requestedPermissions)s.append("• ").append(permission).append('\n');
    }catch(Exception e){s.append("\nอ่านข้อมูลสิทธิ์ไม่ได้");}
    new AlertDialog.Builder(this).setTitle(a.label).setMessage(s.toString())
      .setPositiveButton("ตกลง",null).show();
  }
  private void backupApp(AppEntry a){
    selectedApp=a;
    Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);
    i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("application/zip");
    i.putExtra(Intent.EXTRA_TITLE,"apk-backup-"+a.pkg+".zip");
    startActivityForResult(i,BACKUP_APK);
  }
  private void writeApkBackup(Uri dest,AppEntry a)throws Exception{
    try(OutputStream o=getContentResolver().openOutputStream(dest,"wt");
        ZipOutputStream z=new ZipOutputStream(o)){
      String info="App: "+a.label+"\nPackage: "+a.pkg+"\nVersion: "+a.version+
        "\nSplit-package backup; installation may need a split APK installer.\n";
      z.putNextEntry(new ZipEntry("readme.txt"));z.write(info.getBytes(StandardCharsets.UTF_8));z.closeEntry();
      copyFileToZip(z,new File(a.base),"base.apk");
      if(a.splits!=null){
        for(int j=0;j<a.splits.length;j++)copyFileToZip(z,new File(a.splits[j]),"split-"+j+".apk");
      }
      z.finish();
    }
  }
  private void copyFileToZip(ZipOutputStream z,File file,String name)throws Exception{
    z.putNextEntry(new ZipEntry(name));
    try(InputStream in=new FileInputStream(file)){
      byte[] buf=new byte[32768];int n;
      while((n=in.read(buf))!=-1)z.write(buf,0,n);
    }
    z.closeEntry();
  }
  private void chooseApk(){
    Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("*/*");
    i.addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(i,OPEN_APK);
  }
  private void inspectApk(Uri u)throws Exception{
    File temp=new File(getCacheDir(),"inspect.apk");long size=0;
    try(InputStream in=getContentResolver().openInputStream(u);
        OutputStream out=new FileOutputStream(temp)){
      if(in==null)throw new Exception("ไม่พบไฟล์");
      byte[] buf=new byte[32768];int n;
      while((n=in.read(buf))!=-1){
        size+=n;if(size>MAX_APK)throw new Exception("รองรับไฟล์ APK ไม่เกิน 250 MB");
        out.write(buf,0,n);
      }
    }
    try{
      int entries=0,dex=0,assets=0,libs=0;
      try(ZipFile z=new ZipFile(temp)){
        Enumeration<? extends ZipEntry> e=z.entries();
        while(e.hasMoreElements()){
          String name=e.nextElement().getName();entries++;
          if(name.endsWith(".dex"))dex++;
          if(name.startsWith("assets/"))assets++;
          if(name.startsWith("lib/"))libs++;
          if(entries>100000)throw new Exception("ไฟล์ใน APK มากผิดปกติ");
        }
      }
      StringBuilder report=new StringBuilder();
      report.append("ขนาด APK: ").append(size/1024/1024).append(" MB\n");
      PackageInfo p=getPackageManager().getPackageArchiveInfo(temp.getAbsolutePath(),PackageManager.GET_PERMISSIONS);
      if(p!=null){
        report.append("แพ็กเกจ: ").append(p.packageName).append("\nเวอร์ชัน: ").append(p.versionName).append("\n");
        if(p.requestedPermissions!=null){
          report.append("สิทธิ์ที่ร้องขอ:\n");
          for(int j=0;j<Math.min(p.requestedPermissions.length,30);j++)report.append("• ").append(p.requestedPermissions[j]).append('\n');
        }
      } else report.append("อ่านข้อมูล Manifest ไม่สำเร็จ\n");
      report.append("\nจำนวนไฟล์: ").append(entries).append("\nDEX: ").append(dex)
        .append("\nAssets: ").append(assets).append("\nNative libs: ").append(libs);
      apkInfo.setText(report.toString());showTab(2);
    }finally{temp.delete();}
  }

  private void useDemo(){
    sourceUri=null;filename="example.json";original=demo.getBytes(StandardCharsets.UTF_8);content=demo;refresh();
  }
  private void refresh(){
    fields.clear();if(content==null)return;
    String trimmed=content.trim();Matcher m;
    if(trimmed.startsWith("{")||trimmed.startsWith("[")){
      m=JSON.matcher(content);while(m.find())addField(m.group(1),m.group(2),m.start(2),m.end(2));
    }else if(trimmed.startsWith("<")){
      m=XML.matcher(content);while(m.find())addField(m.group(1),m.group(2),m.start(2),m.end(2));
      m=Pattern.compile("<[\\p{L}_][\\p{L}\\p{N}_.:-]*(?:\\s[^<>]*?)?\\s*/?>").matcher(content);
      while(m.find()){
        Matcher a=ATTR.matcher(m.group());
        while(a.find())addField(a.group(1),a.group(3),m.start()+a.start(3),m.start()+a.end(3));
      }
    }else{
      m=INI.matcher(content);while(m.find())addField(m.group(1),m.group(2),m.start(2),m.end(2));
    }
    Collections.sort(fields,(a,b)->Integer.compare(a.from,b.from));
    drawFields();
  }
  private void addField(String key,String value,int from,int to){
    if(fields.size()<3000)fields.add(new Field(key,value,from,to));
  }
  private void drawFields(){
    if(fieldList==null)return;
    fieldList.removeAllViews();
    if(fileInfo!=null)fileInfo.setText("ไฟล์: "+filename+"   |   พบ "+fields.size()+" ค่า");
    int shown=0;
    for(Field f:fields){
      if(!f.key.toLowerCase(Locale.ROOT).contains(query))continue;
      if(shown++>=250)break;
      button(fieldList,f.key+"  =  "+f.value,()->editOne(f));
    }
    if(shown==0)text(fieldList,"ไม่พบค่าตัวเลข",13,0xffe2bc78);
  }
  private void editOne(Field f){
    EditText input=new EditText(this);
    input.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_SIGNED|InputType.TYPE_NUMBER_FLAG_DECIMAL);
    input.setSingleLine(true);input.setText(f.value);input.selectAll();
    LinearLayout wrap=new LinearLayout(this);wrap.setPadding(22,0,22,0);wrap.addView(input);
    new AlertDialog.Builder(this).setTitle("แก้ "+f.key).setView(wrap)
      .setNegativeButton("ยกเลิก",null).setPositiveButton("บันทึก",(d,w)->{
        String value=input.getText().toString().trim();
        if(value.length()>32||!VALID.matcher(value).matches()){notice("ตัวเลขไม่ถูกต้อง");return;}
        if(f.to>content.length()||!content.substring(f.from,f.to).equals(f.value)){
          notice("ข้อมูลเปลี่ยนไป กรุณาเลือกใหม่");return;
        }
        content=content.substring(0,f.from)+value+content.substring(f.to);
        refresh();notice("แก้ "+f.key+" สำเร็จ");
      }).show();
  }
  private void bulkEdit(){
    ArrayList<Field> matches=new ArrayList<>();
    for(Field f:fields)if(FIELD_HINT.matcher(f.key).find())matches.add(f);
    if(matches.isEmpty()){notice("ไม่พบชื่อค่าที่รู้จัก");return;}
    new AlertDialog.Builder(this).setTitle("ปรับค่า "+matches.size()+" รายการ")
      .setMessage("เปลี่ยนค่าที่มีชื่อคล้ายเงิน/เหรียญ/เพชร/พลัง/เลเวลเป็น 999999\n"+
        "บางเกมอาจยอมรับได้เฉพาะช่วงตัวเลขที่กำหนด ควรสำรองเซฟก่อน")
      .setNegativeButton("ยกเลิก",null).setPositiveButton("ปรับค่า",(d,w)->{
        Collections.sort(matches,(a,b)->Integer.compare(b.from,a.from));
        StringBuilder work=new StringBuilder(content);
        for(Field f:matches)if(f.to<=work.length())work.replace(f.from,f.to,"999999");
        content=work.toString();refresh();notice("ปรับค่าแล้ว กรุณาส่งออกเซฟ");
      }).show();
  }
  private void restore(){
    if(original==null)return;
    content=new String(original,StandardCharsets.UTF_8);refresh();notice("คืนค่าต้นฉบับในตัวแก้ไขแล้ว");
  }
  private void chooseSave(){
    Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("*/*");
    i.addCategory(Intent.CATEGORY_OPENABLE);
    i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION|
      Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
    startActivityForResult(i,OPEN_SAVE);
  }
  private void export(int id){
    if(content==null){notice("ยังไม่มีไฟล์");return;}
    Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);
    i.setType("application/octet-stream");
    i.putExtra(Intent.EXTRA_TITLE,id==SAVE_ORIGINAL?filename+".bak":"edited-"+filename);
    startActivityForResult(i,id);
  }
  private void overwrite(){
    if(sourceUri==null){notice("ต้องเปิดไฟล์เซฟจริงก่อน");return;}
    new AlertDialog.Builder(this).setTitle("เขียนทับเซฟ?")
      .setMessage("การแก้ไขอาจทำให้เกมไม่อ่านเซฟ ควรส่งออกสำรองต้นฉบับก่อนทุกครั้ง")
      .setNegativeButton("ยกเลิก",null)
      .setPositiveButton("ยืนยัน",(d,w)->{
        try{write(sourceUri,content.getBytes(StandardCharsets.UTF_8));notice("เขียนทับสำเร็จ");}
        catch(Exception e){notice("เขียนทับไม่ได้: "+e.getMessage());}
      }).show();
  }
  @Override protected void onActivityResult(int request,int result,Intent data){
    super.onActivityResult(request,result,data);
    if(result!=RESULT_OK||data==null||data.getData()==null)return;
    Uri u=data.getData();
    try{
      if(request==OPEN_SAVE){
        byte[] bytes=read(u);
        String decoded=StandardCharsets.UTF_8.newDecoder()
          .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
          .decode(ByteBuffer.wrap(bytes)).toString();
        if(decoded.indexOf('\0')>=0)throw new Exception("ไม่รองรับเซฟไบนารี");
        try(FileOutputStream out=openFileOutput("last-save-backup.bak",MODE_PRIVATE)){out.write(bytes);}
        String n="save.dat";
        try(android.database.Cursor cur=getContentResolver().query(u,
          new String[]{android.provider.OpenableColumns.DISPLAY_NAME},null,null,null)){
          if(cur!=null&&cur.moveToFirst())n=cur.getString(0);
        }
        filename=n;sourceUri=u;original=bytes;content=decoded;
        int flags=data.getFlags()&(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        try{getContentResolver().takePersistableUriPermission(u,flags);}catch(Exception ignored){}
        refresh();showTab(1);
        notice("สำรองเซฟแล้ว พบ "+fields.size()+" ค่า");
      }else if(request==SAVE_EDITED){write(u,content.getBytes(StandardCharsets.UTF_8));notice("บันทึกเซฟที่แก้แล้ว");}
      else if(request==SAVE_ORIGINAL){write(u,original);notice("สำรองต้นฉบับเรียบร้อย");}
      else if(request==BACKUP_APK){
        if(selectedApp==null)throw new Exception("ยังไม่เลือกแอป");
        writeApkBackup(u,selectedApp);notice("สำรอง APK เป็น ZIP แล้ว");
      }else if(request==OPEN_APK){inspectApk(u);notice("ตรวจสอบ APK แล้ว");}
    }catch(Exception e){notice("เกิดข้อผิดพลาด: "+e.getMessage());}
  }
  private byte[] read(Uri u)throws Exception{
    try(InputStream in=getContentResolver().openInputStream(u);ByteArrayOutputStream out=new ByteArrayOutputStream()){
      if(in==null)throw new Exception("เปิดไฟล์ไม่ได้");
      byte[] buf=new byte[8192];int n;
      while((n=in.read(buf))!=-1){
        if(out.size()+n>MAX_SAVE)throw new Exception("ไฟล์เซฟเกิน 2 MB");
        out.write(buf,0,n);
      }
      return out.toByteArray();
    }
  }
  private void write(Uri u,byte[] bytes)throws Exception{
    try(OutputStream out=getContentResolver().openOutputStream(u,"wt")){
      if(out==null)throw new Exception("เขียนไฟล์ไม่ได้");
      out.write(bytes);out.flush();
    }
  }
  private void notice(String message){Toast.makeText(this,message,Toast.LENGTH_LONG).show();}
}
