package com.offlinegame.editor;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.View;
import android.widget.*;
import android.graphics.Color;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.util.*;
import java.util.regex.*;

public class MainActivity extends Activity {
  private static final int OPEN=1, SAVE=2, BACKUP=3;
  private static final int MAX=2_000_000;
  private static final String NUMBER="-?(?:0|[1-9][0-9]*)(?:\\.[0-9]+)?(?:[eE][+-]?[0-9]+)?";
  private static final Pattern VALID=Pattern.compile(NUMBER);
  private static final Pattern JSON=Pattern.compile("\"([^\"\\\\]+)\"\\s*:\\s*("+NUMBER+")");
  private static final Pattern INI=Pattern.compile("(?m)^[ \\t]*([\\p{L}_][\\p{L}\\p{N}_.-]*)[ \\t]*[=:][ \\t]*("+NUMBER+")[ \\t]*(?:[;#][^\\r\\n]*)?$");
  private static final Pattern XML=Pattern.compile("<([\\p{L}_][\\p{L}\\p{N}_.:-]*)>\\s*("+NUMBER+")\\s*</\\1\\s*>");
  private static final Pattern ATTR=Pattern.compile("([\\p{L}_][\\p{L}\\p{N}_.:-]*)\\s*=\\s*([\"'])("+NUMBER+")\\2");
  static class Field {
    final String key, value; final int from,to;
    Field(String k,String v,int f,int t){key=k;value=v;from=f;to=t;}
  }
  private String content, filename="example.json", query="";
  private byte[] original;
  private Uri sourceUri;
  private TextView info;
  private LinearLayout list;
  private EditText search;
  private ArrayList<Field> fields=new ArrayList<>();
  private String demo="{\n  \"player\": {\n    \"money\": 1250,\n    \"coins\": 42,\n    \"gems\": 10,\n    \"level\": 5,\n    \"hp\": 100\n  },\n  \"inventory\": [{\"quantity\": 2}]\n}\n";

  @Override public void onCreate(Bundle saved){
    super.onCreate(saved);
    getWindow().setStatusBarColor(Color.rgb(8,17,32));
    ScrollView scroll=new ScrollView(this);
    scroll.setBackgroundColor(Color.rgb(8,17,32));
    LinearLayout root=new LinearLayout(this); root.setPadding(20,24,20,40);root.setOrientation(LinearLayout.VERTICAL);
    scroll.addView(root);setContentView(scroll);
    title(root,"OFFLINE GAME EDITOR",26,Color.WHITE);
    title(root,"แก้ไขเซฟเกมออฟไลน์ • ไม่มีโฆษณา • ไม่ต้องรูท",13,0xff96b7d0);
    button(root,"📂  เปิดไฟล์เซฟจากเครื่อง",()->open());
    button(root,"🎮  ทดลองไฟล์ตัวอย่าง",()->demo());
    info=title(root,"",14,0xff44dfb5);
    search=new EditText(this);search.setSingleLine(true);search.setHint("ค้นหา money, coins, gems, level...");
    search.setHintTextColor(0xff8799ac);search.setTextColor(Color.WHITE);
    root.addView(search);
    search.addTextChangedListener(new TextWatcher(){
      public void beforeTextChanged(CharSequence s,int a,int c,int d){}
      public void onTextChanged(CharSequence s,int a,int b,int c){query=s.toString().toLowerCase(Locale.ROOT);draw();}
      public void afterTextChanged(Editable e){}
    });
    list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);root.addView(list);
    button(root,"💾  ส่งออกไฟล์เซฟที่แก้แล้ว",()->export(SAVE));
    button(root,"🛡  ส่งออกเซฟต้นฉบับ (สำรอง)",()->export(BACKUP));
    button(root,"✍  เขียนทับไฟล์ที่เลือก",()->overwrite());
    title(root,"รองรับเฉพาะไฟล์ข้อความ JSON / INI / XML ที่เครื่องเข้าถึงได้\n" +
      "ไม่สามารถสแกน RAM หรือเข้าไฟล์ส่วนตัวของเกมอื่นได้โดยอัตโนมัติ\n" +
      "ปิดเกมก่อนแก้ไฟล์ และเก็บเซฟต้นฉบับไว้เสมอ",12,0xffa4b4c6);
    demo();
  }
  private TextView title(LinearLayout parent,String s,int size,int color){
    TextView t=new TextView(this);t.setText(s);t.setTextColor(color);t.setTextSize(size);
    t.setPadding(0,9,0,9);parent.addView(t);return t;
  }
  private void button(LinearLayout parent,String label,Runnable action){
    Button b=new Button(this);b.setText(label);b.setAllCaps(false);
    parent.addView(b,new LinearLayout.LayoutParams(-1,-2));b.setOnClickListener(v->action.run());
  }
  private void demo(){
    sourceUri=null;filename="example.json";
    original=demo.getBytes(StandardCharsets.UTF_8);content=demo;
    refresh();info.setText("ตัวอย่าง — 6 ค่า • ใช้งานออฟไลน์");
  }
  private void refresh(){
    fields.clear();
    String stripped=content.trim();
    Matcher m;
    if(stripped.startsWith("{")||stripped.startsWith("[")){
      m=JSON.matcher(content);
      while(m.find())add(m.group(1),m.group(2),m.start(2),m.end(2));
    } else if(stripped.startsWith("<")){
      m=XML.matcher(content);
      while(m.find())add(m.group(1),m.group(2),m.start(2),m.end(2));
      m=Pattern.compile("<[\\p{L}_][\\p{L}\\p{N}_.:-]*(?:\\s[^<>]*?)?\\s*/?>").matcher(content);
      while(m.find()){
        Matcher a=ATTR.matcher(m.group());
        while(a.find())add(a.group(1),a.group(3),m.start()+a.start(3),m.start()+a.end(3));
      }
    } else {
      m=INI.matcher(content);
      while(m.find())add(m.group(1),m.group(2),m.start(2),m.end(2));
    }
    Collections.sort(fields,(a,b)->Integer.compare(a.from,b.from));
    draw();
  }
  private void add(String k,String v,int a,int b){
    if(fields.size()<3000)fields.add(new Field(k,v,a,b));
  }
  private void draw(){
    if(list==null)return;
    list.removeAllViews();
    if(fields.isEmpty()){title(list,"ไม่พบค่าตัวเลขที่แก้ไขได้",14,0xfff5b971);return;}
    int shown=0;
    for(Field f:fields){
      if(!f.key.toLowerCase(Locale.ROOT).contains(query))continue;
      if(shown++>=250)break;
      button(list,f.key+"   =   "+f.value,()->edit(f));
    }
    info.setText("ไฟล์: "+filename+"   |   พบ "+fields.size()+" ค่า");
  }
  private void edit(Field f){
    EditText e=new EditText(this);e.setInputType(InputType.TYPE_CLASS_NUMBER|
      InputType.TYPE_NUMBER_FLAG_SIGNED|InputType.TYPE_NUMBER_FLAG_DECIMAL);
    e.setText(f.value);e.selectAll();e.setSingleLine(true);
    LinearLayout wrap=new LinearLayout(this);wrap.setPadding(25,5,25,5);wrap.addView(e);
    new AlertDialog.Builder(this).setTitle("แก้ "+f.key).setView(wrap).setNegativeButton("ยกเลิก",null)
      .setPositiveButton("บันทึก",(d,w)->{
        String n=e.getText().toString().trim();
        if(n.length()>32||!VALID.matcher(n).matches()){notice("ตัวเลขไม่ถูกต้อง");return;}
        if(f.to>content.length()||!content.substring(f.from,f.to).equals(f.value)){
          notice("ข้อมูลเปลี่ยนไป กรุณาเปิดไฟล์ใหม่");return;
        }
        content=content.substring(0,f.from)+n+content.substring(f.to);
        refresh();notice("แก้ "+f.key+" แล้ว");
      }).show();
  }
  private void open(){
    Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("*/*");
    i.addCategory(Intent.CATEGORY_OPENABLE);
    i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION|
      Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
    startActivityForResult(i,OPEN);
  }
  private void export(int request){
    if(content==null){notice("ยังไม่มีข้อมูล");return;}
    Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);
    i.setType("application/octet-stream");
    i.putExtra(Intent.EXTRA_TITLE,request==BACKUP?filename+".bak":"edited-"+filename);
    startActivityForResult(i,request);
  }
  private void overwrite(){
    if(sourceUri==null){notice("กรุณาเปิดไฟล์จริงก่อน");return;}
    new AlertDialog.Builder(this).setTitle("เขียนทับเซฟ?")
      .setMessage("แอปได้สำรองไฟล์ต้นฉบับไว้แล้ว การเขียนทับอาจทำให้เกมอ่านเซฟไม่ได้")
      .setNegativeButton("ยกเลิก",null)
      .setPositiveButton("ยืนยัน",(d,w)->{
        try {
          write(sourceUri,content.getBytes(StandardCharsets.UTF_8));
          notice("เขียนทับสำเร็จ"); 
        } catch(Exception e){notice("เขียนทับไม่ได้: "+e.getMessage());}
      }).show();
  }
  @Override protected void onActivityResult(int request,int result,Intent data){
    super.onActivityResult(request,result,data);
    if(result!=RESULT_OK||data==null||data.getData()==null)return;
    Uri u=data.getData();
    try {
      if(request==OPEN){
        byte[] bytes=read(u);
        String s=StandardCharsets.UTF_8.newDecoder()
          .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
          .decode(ByteBuffer.wrap(bytes)).toString();
        if(s.indexOf('\0')>=0)throw new Exception("ไฟล์ไบนารีใช้ไม่ได้");
        new File(getFilesDir(),"last-save-backup.bak").delete();
        try(FileOutputStream out=openFileOutput("last-save-backup.bak",MODE_PRIVATE)){out.write(bytes);}
        String nm="save.dat";
        try(android.database.Cursor cur=getContentResolver().query(u,
          new String[]{android.provider.OpenableColumns.DISPLAY_NAME},null,null,null)){
          if(cur!=null&&cur.moveToFirst())nm=cur.getString(0);
        }
        filename=nm;sourceUri=u;original=bytes;content=s;refresh();
        int flags=data.getFlags()&(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        try{getContentResolver().takePersistableUriPermission(u,flags);}catch(Exception ignored){}
        notice(fields.size()+" ค่าที่แก้ได้ • สำรองไฟล์แล้ว");
      } else if(request==SAVE) {
        write(u,content.getBytes(StandardCharsets.UTF_8));notice("บันทึกไฟล์เซฟที่แก้แล้ว");
      } else if(request==BACKUP) {
        write(u,original);notice("บันทึกต้นฉบับแล้ว");
      }
    } catch(Exception e){notice("ทำรายการไม่สำเร็จ: "+e.getMessage());}
  }
  private byte[] read(Uri u)throws Exception{
    try(InputStream in=getContentResolver().openInputStream(u);ByteArrayOutputStream out=new ByteArrayOutputStream()){
      if(in==null)throw new Exception("อ่านไฟล์ไม่ได้");
      byte[] b=new byte[8192];int n;
      while((n=in.read(b))!=-1){
        if(out.size()+n>MAX)throw new Exception("ไฟล์เกิน 2 MB");
        out.write(b,0,n);
      }return out.toByteArray();
    }
  }
  private void write(Uri u,byte[] bytes)throws Exception{
    try(OutputStream out=getContentResolver().openOutputStream(u,"wt")){
      if(out==null)throw new Exception("เขียนไฟล์ไม่ได้");
      out.write(bytes);out.flush();
    }
  }
  private void notice(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
}
