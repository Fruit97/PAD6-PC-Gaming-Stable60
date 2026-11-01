package com.pad6.nsoanalyzer;

import android.content.Context;
import android.net.Uri;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

final class NsoAnalyzer {
    interface Progress { void update(String message); }
    static final String[] NAMES = {"TEXT", "RODATA", "DATA"};
    static final int CHUNK = 1024 * 1024;
    static final byte[][] PATTERNS = {
        hex("0A61815201102E1E"),
        hex("0409231E030A241E"),
        "bUseFixedFrameRate".getBytes(StandardCharsets.UTF_8),
        "FixedFrameRate".getBytes(StandardCharsets.UTF_8),
        "t.MaxFPS".getBytes(StandardCharsets.UTF_8),
        "r.VSync".getBytes(StandardCharsets.UTF_8)
    };
    static final String[] PATTERN_NAMES = {
        "Gravity reference bytes", "Multiplication reference bytes",
        "bUseFixedFrameRate string", "FixedFrameRate string",
        "t.MaxFPS string", "r.VSync string"
    };
    static byte[] hex(String s) {
        byte[] b = new byte[s.length()/2];
        for (int i = 0; i < b.length; i++) b[i] = (byte) Integer.parseInt(s.substring(i*2, i*2+2), 16);
        return b;
    }
    static String hex(byte[] b, int first, int end) {
        final char[] digits = "0123456789ABCDEF".toCharArray();
        StringBuilder sb = new StringBuilder();
        for (int i = first; i < end; i++) {
            int v = b[i] & 255; sb.append(digits[v>>>4]).append(digits[v&15]);
        }
        return sb.toString();
    }
    static long u32(ByteBuffer b, int pos) { return Integer.toUnsignedLong(b.getInt(pos)); }
    static String fmt(long v) { return String.format(Locale.US, "0x%X", v); }
    static void skipExact(InputStream in, long n) throws IOException {
        byte[] buf = new byte[65536];
        while (n > 0) {
            int r = in.read(buf, 0, (int)Math.min(n, buf.length));
            if (r < 0) throw new EOFException("Incomplete NSO input");
            n -= r;
        }
    }
    static final class LimitedStream extends FilterInputStream {
        long remaining;
        LimitedStream(InputStream in, long bytes) { super(in); remaining=bytes; }
        public int read() throws IOException {
            if (remaining == 0) return -1;
            int v = in.read(); if (v >= 0) remaining--; return v;
        }
        public int read(byte[] b,int off,int len) throws IOException {
            if (remaining==0) return -1;
            int r = in.read(b,off,(int)Math.min(len,remaining));
            if(r>0) remaining-=r; return r;
        }
    }
    static void copyExact(InputStream in,OutputStream out,long n) throws IOException {
        byte[] buf=new byte[65536];
        while(n>0){
            int r=in.read(buf,0,(int)Math.min(n,buf.length));
            if(r<0) throw new EOFException("Segment truncated");
            out.write(buf,0,r); n-=r;
        }
    }
    static long decompressLz4(InputStream source,OutputStream target,long expected) throws IOException {
        LimitedStream input=(LimitedStream)source;
        byte[] dict=new byte[65536], literal=new byte[8192];
        BufferedOutputStream out=new BufferedOutputStream(target,65536);
        long produced=0;
        while(input.remaining>0) {
            int token=input.read();
            if(token<0) throw new EOFException("LZ4 token truncated");
            long lit=token>>>4;
            if(lit==15) {
                int k;
                do { k=input.read(); if(k<0) throw new EOFException("LZ4 literal extension"); lit+=k; } while(k==255);
            }
            if(lit>expected-produced) throw new IOException("LZ4 literal overflow");
            while(lit>0) {
                int n=(int)Math.min(literal.length,lit), used=0;
                while(used<n) {
                    int r=input.read(literal,used,n-used);
                    if(r<0) throw new EOFException("LZ4 literal missing");
                    used+=r;
                }
                out.write(literal,0,n);
                for(int i=0;i<n;i++) dict[(int)((produced+i)&65535)]=literal[i];
                produced+=n; lit-=n;
            }
            if(input.remaining==0) break;
            int low=input.read(), high=input.read();
            if(low<0||high<0) throw new EOFException("LZ4 missing offset");
            int distance=low|(high<<8);
            if(distance==0||distance>produced) throw new IOException("Invalid LZ4 match offset");
            long match=(token&15)+4;
            if((token&15)==15) {
                int k; do { k=input.read(); if(k<0) throw new EOFException("LZ4 match extension"); match+=k; } while(k==255);
            }
            if(match>expected-produced) throw new IOException("LZ4 match overflow");
            for(long i=0;i<match;i++) {
                byte v=dict[(int)((produced-distance)&65535)];
                out.write(v); dict[(int)(produced&65535)]=v; produced++;
            }
        }
        out.flush();
        if(produced!=expected) throw new IOException("LZ4 decoded "+produced+" but expected "+expected);
        return produced;
    }
    static final class Section {
        final String name; final long fileOffset,mainRva,size,storedSize; final boolean compressed;
        Section(String name,long fileOffset,long mainRva,long size,long storedSize,boolean compressed) {
            this.name=name;this.fileOffset=fileOffset;this.mainRva=mainRva;
            this.size=size;this.storedSize=storedSize;this.compressed=compressed;
        }
        JSONObject json() throws Exception {
            JSONObject o=new JSONObject();
            o.put("name",name).put("file_offset",fmt(fileOffset)).put("main_rva",fmt(mainRva))
                .put("decoded_size",size).put("stored_size",storedSize).put("compressed",compressed);
            return o;
        }
    }
    static final class HitLog {
        final JSONArray hits=new JSONArray(); final Map<String,Integer> counts=new LinkedHashMap<>();
        void hit(String kind,String section,long rva,long sourceOffset) throws Exception {
            int n=counts.containsKey(kind)?counts.get(kind):0;
            counts.put(kind,n+1);
            if(n<20) {
                JSONObject o=new JSONObject();
                o.put("kind",kind).put("segment",section).put("main_rva",fmt(rva)).put("decoded_segment_offset",fmt(sourceOffset));
                hits.put(o);
            }
        }
    }
    static int word(byte[] b,int off) {
        return (b[off]&255)|((b[off+1]&255)<<8)|((b[off+2]&255)<<16)|((b[off+3]&255)<<24);
    }
    static void scan(File file,Section section,Set<Long> targets,HitLog log) throws Exception {
        try(InputStream in=new BufferedInputStream(new FileInputStream(file),65536)){
            byte[] b=new byte[CHUNK+128]; int carry=0; long fileBase=0;
            for(;;){
                int r=in.read(b,carry,CHUNK);
                if(r==0) continue;
                boolean last=r<0;
                int total=carry+Math.max(0,r);
                int end=last?total:((Math.max(0,total-64))&~3);
                if(end<0) end=0;
                for(int j=0;j<PATTERNS.length;j++){
                    byte[] p=PATTERNS[j];
                    for(int i=0;i<end&&i+p.length<=total;i++){
                        if(b[i]!=p[0]) continue;
                        boolean ok=true;
                        for(int k=1;k<p.length;k++) if(b[i+k]!=p[k]){ok=false;break;}
                        if(ok) log.hit(PATTERN_NAMES[j],section.name,section.mainRva+fileBase+i,fileBase+i);
                    }
                }
                if(section.name.equals("TEXT")&&!targets.isEmpty()) {
                    for(int i=0;i+32<=total&&i<end;i+=4){
                        int a=word(b,i);
                        if((a&0x9F000000)!=0x90000000) continue;
                        int reg=a&31;
                        long immediate=((long)(a>>>5)&0x7ffffL)<<2 | ((long)(a>>>29)&3);
                        if((immediate&0x100000L)!=0) immediate|=~0x1fffffL;
                        long pc=section.mainRva+fileBase+i;
                        long page=(pc&~4095L)+(immediate<<12);
                        for(int k=1;k<=6;k++){
                            int w=word(b,i+4*k);
                            int rn=(w>>>5)&31;
                            if(rn!=reg) continue;
                            long address=-1;
                            if((w&0x7F000000)==0x11000000){
                                int shift=(w>>>22)&3;
                                if(shift<2) address=page+(((long)(w>>>10)&4095L)<<(shift==1?12:0));
                            } else {
                                int mask=w&0xFFC00000;
                                int bytes=mask==0xF9400000||mask==0xFD400000?8:
                                      mask==0xB9400000||mask==0xBD400000?4:0;
                                if(bytes!=0)address=page+((long)((w>>>10)&4095)*bytes);
                            }
                            if(targets.contains(address))log.hit("ARM64 ADRP reference to "+fmt(address),section.name,pc,fileBase+i);
                        }
                    }
                }
                if(last)break;
                carry=total-end;
                System.arraycopy(b,end,b,0,carry);
                fileBase+=end;
            }
        }
    }
    static JSONObject analyze(Context context, Uri uri, String displayName, Set<Long> targets, Progress progress) throws Exception {
        JSONObject result=new JSONObject();
        result.put("analyzer_version","0.1.0");
        result.put("type","NSO_SCAN_ONLY_NOT_AN_FPS_MOD");
        result.put("name",displayName);
        byte[] rawHeader=new byte[256];
        try(InputStream in=context.getContentResolver().openInputStream(uri)) {
            if(in==null)throw new IOException("Cannot open selected file");
            new DataInputStream(in).readFully(rawHeader);
        }
        if(rawHeader[0]!='N'||rawHeader[1]!='S'||rawHeader[2]!='O'||rawHeader[3]!='0')
            throw new IOException("Not an NSO0 file");
        ByteBuffer header=ByteBuffer.wrap(rawHeader).order(ByteOrder.LITTLE_ENDIAN);
        long flags=u32(header,0xC);
        result.put("flags",fmt(flags));
        result.put("build_id",hex(rawHeader,0x40,0x60));
        progress.update("กำลังตรวจ SHA-256 ของไฟล์...");
        MessageDigest md=MessageDigest.getInstance("SHA-256");
        long totalSize=0;
        try(InputStream in=new BufferedInputStream(context.getContentResolver().openInputStream(uri),131072)){
            byte[] b=new byte[131072];int r;
            while((r=in.read(b))!=-1){md.update(b,0,r);totalSize+=r;}
        }
        result.put("nso_size",totalSize).put("sha256",hex(md.digest(),0,32));
        if((flags & 0x80)!=0)result.put("unsupported_compression","ZBIC not supported in v0.1");
        List<Section> sections=new ArrayList<>();
        JSONArray sectionInfo=new JSONArray();
        for(int i=0;i<3;i++){
            long f=u32(header,0x10+i*16),v=u32(header,0x14+i*16),n=u32(header,0x18+i*16);
            boolean compressed=(flags&(1<<i))!=0;
            long stored=compressed?u32(header,0x60+i*4):n;
            Section s=new Section(NAMES[i],f,v,n,stored,compressed);
            sections.add(s);sectionInfo.put(s.json());
        }
        result.put("segments",sectionInfo);
        JSONArray inputs=new JSONArray();
        for(long v:targets)inputs.put(fmt(v));
        result.put("reference_targets_for_investigation_only",inputs);
        HitLog log=new HitLog();
        JSONArray warnings=new JSONArray();
        for(Section section:sections){
            if(section.size==0)continue;
            if(section.size>450_000_000L || section.storedSize>450_000_000L ||
               section.fileOffset+section.storedSize>totalSize){
                warnings.put(section.name+": invalid or unsafe section size");continue;
            }
            if((flags&0x80)!=0 && section.compressed){
                warnings.put(section.name+": ZBIC section skipped");continue;
            }
            progress.update("กำลังถอดและตรวจ "+section.name+"...");
            File tmp=null;
            try{
                if(context.getCacheDir().getUsableSpace()<section.size+8*1024*1024L)
                    throw new IOException("Insufficient cache space");
                tmp=File.createTempFile("nso_scan_",".dat",context.getCacheDir());
                try(InputStream source=context.getContentResolver().openInputStream(uri);
                    OutputStream dest=new BufferedOutputStream(new FileOutputStream(tmp),65536)){
                    if(source==null)throw new IOException("Cannot reopen NSO");
                    skipExact(source,section.fileOffset);
                    if(section.compressed){
                        decompressLz4(new LimitedStream(source,section.storedSize),dest,section.size);
                    } else copyExact(source,dest,section.size);
                }
                scan(tmp,section,targets,log);
            } catch(Exception ex){
                warnings.put(section.name+": "+ex.getClass().getSimpleName()+": "+ex.getMessage());
            } finally { if(tmp!=null)tmp.delete(); }
        }
        JSONObject counts=new JSONObject();
        for(Map.Entry<String,Integer> e:log.counts.entrySet())counts.put(e.getKey(),e.getValue());
        result.put("hit_counts",counts).put("hits_first_20_each",log.hits).put("warnings",warnings);
        result.put("disclaimer","Confirmed signature bytes do not by themselves prove a 60FPS patch or valid cheat target. No game data was changed.");
        return result;
    }
}