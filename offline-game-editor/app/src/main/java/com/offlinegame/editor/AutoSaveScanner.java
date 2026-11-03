package com.offlinegame.editor;

import android.content.Context;
import android.os.Environment;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.util.*;
import java.util.regex.*;

/**
 * Attempts local save editing within user-accessible SHARED storage only.
 * Does not access other applications' internal app data, bypass integrity checks,
 * install patched APKs, or claim universal compatibility.
 */
public final class AutoSaveScanner {
    private static final int MAX_FILES=2500, MAX_DIRS=2000;
    private static final long MAX_FILE_BYTES=2_000_000L;
    private static final String NUMBER="-?(?:0|[1-9][0-9]*)(?:\\.[0-9]+)?(?:[eE][+-]?[0-9]+)?";
    private static final Pattern JSON=Pattern.compile("\"([^\"\\\\]+)\"\\s*:\\s*("+NUMBER+")");
    private static final Pattern INI=Pattern.compile("(?m)^[ \\t]*([\\p{L}_][\\p{L}\\p{N}_.-]*)[ \\t]*[=:][ \\t]*("+NUMBER+")[ \\t]*(?:[;#][^\\r\\n]*)?$");
    private static final Pattern XML=Pattern.compile("<([\\p{L}_][\\p{L}\\p{N}_.:-]*)>\\s*("+NUMBER+")\\s*</\\1\\s*>");
    private static final Pattern XML_ATTRIBUTE=Pattern.compile("([\\p{L}_][\\p{L}\\p{N}_.:-]*)\\s*=\\s*([\"'])("+NUMBER+")\\2");
    private static final Pattern TAG=Pattern.compile("<[\\p{L}_][\\p{L}\\p{N}_.:-]*(?:\\s[^<>]*?)?\\s*/?>");
    private static final Pattern CURRENCY=Pattern.compile("(?i)^(?:money|coins?|gold|gems?|diamonds?|cash|credits?|silver|tokens?|points?|currency|wallet|balance)(?:_?(?:balance|count|total|current))?$");
    private static final Pattern STATS=Pattern.compile("(?i)^(?:xp|exp|experience|level|levels|hp|health|energy|stamina|lives)(?:_?(?:current|total))?$");
    private static final Set<String> EXTENSIONS=new HashSet<>(Arrays.asList(
      "json","ini","cfg","xml","sav","save","dat","txt","prefs"));

    static class KeyMatch {
        final String key,value,newValue;
        final int from,to;
        KeyMatch(String key,String value,int from,int to,String newValue) {
            this.key=key;this.value=value;this.from=from;this.to=to;this.newValue=newValue;
        }
    }
    static class Node {
        final File file; final int depth;
        Node(File f,int d){file=f;depth=d;}
    }
    public static class Result {
        public int scannedFiles,scannedDirs,candidateFiles,patchedFiles,patchedFields,errors,skippedEncrypted;
        public String lastBackup="",explanation="",example="";
        public String summary(){
            StringBuilder b=new StringBuilder();
            b.append("ตรวจสอบโฟลเดอร์: ").append(scannedDirs)
             .append("\nตรวจสอบไฟล์: ").append(scannedFiles)
             .append("\nพบไฟล์เซฟที่เข้าข่าย: ").append(candidateFiles)
             .append("\nแก้ไขสำเร็จ: ").append(patchedFiles).append(" ไฟล์ / ")
             .append(patchedFields).append(" ค่า")
             .append("\nไฟล์เข้ารหัส/ไบนารีที่ข้าม: ").append(skippedEncrypted)
             .append("\nข้อผิดพลาด: ").append(errors);
            if(!example.isEmpty())b.append("\n\nตัวอย่างไฟล์ที่แก้:\n").append(example);
            if(!lastBackup.isEmpty())b.append("\n\nบันทึกสำรองในแอปแล้ว (มีเมนูคืนค่า)");
            if(patchedFiles==0)b.append("\n\nไม่สามารถแก้ได้จากไฟล์ที่เข้าถึงได้ ไม่ได้หมายความว่าเกมถูกแก้หรือโกงสำเร็จ");
            if(!explanation.isEmpty())b.append("\n\n").append(explanation);
            return b.toString();
        }
    }
    private AutoSaveScanner(){}

    public static boolean hasSharedStorageAccess(){
        return android.os.Build.VERSION.SDK_INT>=30 && Environment.isExternalStorageManager();
    }

    private static String normalized(String s){
        return s.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]","");
    }

    private static Set<String> tokens(String packageName,String appName){
        HashSet<String> t=new HashSet<>();
        if(packageName!=null)t.add(packageName.toLowerCase(Locale.ROOT));
        if(appName!=null){
            String label=normalized(appName);
            if(label.length()>=5)t.add(label);
            String[] words=appName.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+");
            for(String w:words)if(w.length()>=5&&!w.equals("game")&&!w.equals("mobile")&&!w.equals("global"))t.add(w);
        }
        if(packageName!=null){
            String[] parts=packageName.toLowerCase(Locale.ROOT).split("\\.");
            String last=parts[parts.length-1];
            if(last.length()>=6&&!last.equals("global")&&!last.equals("android")&&!last.equals("mobile")&&!last.equals("game"))t.add(last);
        }
        return t;
    }
    private static boolean belongsToApp(File file,Set<String> hints){
        String path=normalized(file.getAbsolutePath());
        for(String hint:hints){
            if(path.contains(normalized(hint)))return true;
        }
        return false;
    }
    private static boolean supportedFile(File f){
        String name=f.getName().toLowerCase(Locale.ROOT);
        int dot=name.lastIndexOf('.');
        return dot>0 && EXTENSIONS.contains(name.substring(dot+1)) && f.length()>0 && f.length()<=MAX_FILE_BYTES;
    }
    private static boolean pathForbidden(String path){
        String p=path.replace('\\','/').toLowerCase(Locale.ROOT);
        return p.contains("/android/data/")||p.contains("/android/obb/")||
               p.endsWith("/android/data")||p.endsWith("/android/obb");
    }
    private static boolean underSharedRoot(File f,File externalRoot){
        try {
            String file=f.getCanonicalPath();
            String root=externalRoot.getCanonicalPath()+File.separator;
            return file.startsWith(root)&&!pathForbidden(file);
        }catch(IOException e){return false;}
    }
    static String replaceRecognizedValues(String input,int[] changed){
        String trimmed=input.trim();
        ArrayList<KeyMatch> hits=new ArrayList<>();
        if(trimmed.startsWith("{")||trimmed.startsWith("[")){
            Matcher m=JSON.matcher(input);
            while(m.find())addIfSupported(hits,m.group(1),m.group(2),m.start(2),m.end(2));
        }else if(trimmed.startsWith("<")){
            Matcher m=XML.matcher(input);
            while(m.find())addIfSupported(hits,m.group(1),m.group(2),m.start(2),m.end(2));
            m=TAG.matcher(input);
            while(m.find()){
                Matcher a=XML_ATTRIBUTE.matcher(m.group());
                while(a.find())addIfSupported(hits,a.group(1),a.group(3),
                      m.start()+a.start(3),m.start()+a.end(3));
            }
        }else{
            Matcher m=INI.matcher(input);
            while(m.find())addIfSupported(hits,m.group(1),m.group(2),m.start(2),m.end(2));
        }
        Collections.sort(hits,(a,b)->Integer.compare(b.from,a.from));
        StringBuilder output=new StringBuilder(input);
        int previousStart=Integer.MAX_VALUE;
        for(KeyMatch hit:hits){
            if(hit.to>previousStart)continue; // don't patch overlapping matches
            if(!output.substring(hit.from,hit.to).equals(hit.value))continue;
            output.replace(hit.from,hit.to,hit.newValue);
            changed[0]++;previousStart=hit.from;
        }
        return output.toString();
    }
    private static void addIfSupported(ArrayList<KeyMatch> hits,String key,String value,int a,int b){
        String canonical=key.replace("-","").replace("_","").trim();
        String replacement=null;
        if(CURRENCY.matcher(canonical).matches())replacement="999999";
        else if(STATS.matcher(canonical).matches()){
            if(canonical.toLowerCase(Locale.ROOT).startsWith("level"))replacement="99";
            else replacement="9999";
        }
        if(replacement==null||replacement.equals(value))return;
        // only integers for auto-edit, as fractional fixed-point save formats can be delicate
        if(value.contains(".")||value.contains("e")||value.contains("E"))return;
        hits.add(new KeyMatch(key,value,a,b,replacement));
    }

    private static File[] scanRoots(File external){
        return new File[]{
            new File(external,"Games"),new File(external,"Game"),
            new File(external,"SaveGames"),new File(external,"Saves"),
            new File(external,"Documents"),new File(external,"Download"),
            new File(external,"Android/media"),new File(external,"game_data")
        };
    }
    private static String readText(File file)throws Exception{
        if(!supportedFile(file))return null;
        byte[] b=Files.readAllBytes(file.toPath());
        String s=StandardCharsets.UTF_8.newDecoder()
          .onMalformedInput(CodingErrorAction.REPORT)
          .onUnmappableCharacter(CodingErrorAction.REPORT)
          .decode(ByteBuffer.wrap(b)).toString();
        if(s.indexOf('\0')>=0)return null;
        int control=0;
        for(int i=0;i<s.length();i++){
            char c=s.charAt(i);
            if(c<32 && c!='\n' && c!='\r' && c!='\t')control++;
        }
        if(control>0)return null;
        return s;
    }
    public static Result patch(Context context,String packageName,String label){
        Result result=new Result();
        if(!hasSharedStorageAccess()){
            result.explanation="ยังไม่ได้เปิดสิทธิ์จัดการไฟล์ที่ใช้ร่วมกัน";
            return result;
        }
        File shared=Environment.getExternalStorageDirectory();
        if(shared==null||!shared.isDirectory()){
            result.explanation="ไม่มีที่เก็บข้อมูลที่เข้าถึงได้";
            return result;
        }
        Set<String> hints=tokens(packageName,label);
        File backupRoot=new File(context.getFilesDir(),
            "auto-backups/"+packageName.replaceAll("[^a-zA-Z0-9_.-]","_")+
            "/run-"+System.currentTimeMillis());
        ArrayDeque<Node> queue=new ArrayDeque<>();
        for(File dir:scanRoots(shared))if(dir.isDirectory())queue.add(new Node(dir,0));
        HashSet<String> visitedDirs=new HashSet<>();
        int handled=0;
        while(!queue.isEmpty()&&result.scannedFiles<MAX_FILES&&result.scannedDirs<MAX_DIRS){
            Node node=queue.poll();
            File dir=node.file;
            if(!underSharedRoot(dir,shared)||pathForbidden(dir.getAbsolutePath()))continue;
            String canonical;
            try{canonical=dir.getCanonicalPath();}catch(IOException ex){continue;}
            if(!visitedDirs.add(canonical))continue;
            result.scannedDirs++;
            File[] children=dir.listFiles();
            if(children==null)continue;
            for(File file:children){
                if(result.scannedFiles>=MAX_FILES||result.scannedDirs>=MAX_DIRS)break;
                if(!underSharedRoot(file,shared))continue;
                if(file.isDirectory()){
                    if(node.depth<4&&queue.size()<MAX_DIRS)queue.add(new Node(file,node.depth+1));
                    continue;
                }
                if(!file.isFile()||!supportedFile(file))continue;
                result.scannedFiles++;
                if(!belongsToApp(file,hints))continue;
                result.candidateFiles++;
                try{
                    String input=readText(file);
                    if(input==null){result.skippedEncrypted++;continue;}
                    int[] count={0};
                    String output=replaceRecognizedValues(input,count);
                    if(count[0]==0)continue;
                    // Always back up BEFORE writing. Backups use this app's private storage.
                    if(!backupRoot.exists()&&!backupRoot.mkdirs())throw new IOException("สร้างที่สำรองไม่ได้");
                    String name=String.format(Locale.ROOT,"%04d",handled++);
                    Path backup=new File(backupRoot,name+".bak").toPath();
                    Path meta=new File(backupRoot,name+".path").toPath();
                    Files.copy(file.toPath(),backup,StandardCopyOption.REPLACE_EXISTING);
                    Files.write(meta,file.getCanonicalPath().getBytes(StandardCharsets.UTF_8));
                    Path temp=new File(file.getParentFile(),".ops_tmp_"+System.nanoTime()).toPath();
                    try{
                        Files.write(temp,output.getBytes(StandardCharsets.UTF_8));
                        try{
                            Files.move(temp,file.toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
                        }catch(AtomicMoveNotSupportedException ex){
                            Files.move(temp,file.toPath(),StandardCopyOption.REPLACE_EXISTING);
                        }
                        result.patchedFiles++;
                        result.patchedFields+=count[0];
                        result.lastBackup=backupRoot.getAbsolutePath();
                        if(result.example.length()<400)result.example+=file.getName()+" ("+count[0]+" ค่า)\n";
                    }catch(Exception ex){
                        result.errors++;
                        try{Files.deleteIfExists(temp);}catch(Exception ignored){}
                    }
                }catch(CharacterCodingException ex){result.skippedEncrypted++;}
                catch(Exception ex){result.errors++;}
            }
        }
        if(result.patchedFiles==0){
            result.explanation="ไม่พบเซฟแบบข้อความที่มีชื่อโฟลเดอร์ตรงกับเกมที่เลือกในพื้นที่จัดเก็บที่ Android อนุญาต\n"+
              "เซฟอาจอยู่ใน /Android/data, พื้นที่ส่วนตัวของเกม, เป็นข้อมูลเข้ารหัส หรือถูกเก็บบนเซิร์ฟเวอร์\n"+
              "ระบบจะไม่แก้ไฟล์ที่ไม่ทราบเจ้าของหรือปลอมสถานะสำเร็จ";
        }
        return result;
    }
    public static Result restoreLast(Context context,String packageName){
        Result r=new Result();
        if(!hasSharedStorageAccess()){r.explanation="ต้องอนุญาตจัดการไฟล์ก่อน";return r;}
        File root=new File(context.getFilesDir(),
          "auto-backups/"+packageName.replaceAll("[^a-zA-Z0-9_.-]","_"));
        File[] sessions=root.listFiles(File::isDirectory);
        if(sessions==null||sessions.length==0){r.explanation="ไม่พบไฟล์สำรอง";return r;}
        Arrays.sort(sessions,(a,b)->Long.compare(b.lastModified(),a.lastModified()));
        File shared=Environment.getExternalStorageDirectory();
        File[] metas=sessions[0].listFiles((dir,name)->name.endsWith(".path"));
        if(metas==null){r.explanation="ไม่พบข้อมูลสำรอง";return r;}
        for(File meta:metas){
            try{
                String original=new String(Files.readAllBytes(meta.toPath()),StandardCharsets.UTF_8);
                File destination=new File(original);
                if(!underSharedRoot(destination,shared))throw new IOException("เส้นทางอยู่นอกพื้นที่ที่อนุญาต");
                File source=new File(meta.getParentFile(),meta.getName().replace(".path",".bak"));
                if(!source.isFile())throw new IOException("ไม่พบไฟล์สำรอง");
                Files.copy(source.toPath(),destination.toPath(),StandardCopyOption.REPLACE_EXISTING);
                r.patchedFiles++;
            }catch(Exception ex){r.errors++;}
        }
        r.explanation=r.patchedFiles>0?"คืนค่าเซฟจากไฟล์สำรองล่าสุดแล้ว":"คืนค่าไม่สำเร็จ";
        return r;
    }
}
