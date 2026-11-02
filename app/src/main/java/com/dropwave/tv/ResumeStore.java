package com.dropwave.tv;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.*;

/** Durable, offset-verified, bounded chunk receiver. All operations on a transfer are synchronized. */
public final class ResumeStore {
    public static final long MAX_CHUNK = 4L * 1024 * 1024;
    private static final long MAX_FILE = 1_099_511_627_776L;
    private final File dir;
    private final SecureRandom rng = new SecureRandom();
    private final Map<String,Job> jobs=new HashMap<>();
    public static final class Job {
        public final String id,name,folder;
        public final long size;
        public long offset;
        private Job(String id,String name,String folder,long size,long offset){this.id=id;this.name=name;this.folder=folder;this.size=size;this.offset=offset;}
    }
    public ResumeStore(File folder) throws IOException {dir=folder; if(!dir.exists()&&!dir.mkdirs())throw new IOException("Cannot create transfer cache"); cleanup();load();}
    private void cleanup(){File[] found=dir.listFiles();if(found==null)return;long stale=System.currentTimeMillis()-7L*24*60*60*1000;
        for(File f:found)if((f.getName().endsWith(".part")||f.getName().endsWith(".meta")||f.getName().endsWith(".meta.tmp"))&&f.lastModified()<stale)f.delete();
    }
    public long tempFreeBytes(){return dir.getUsableSpace();}
    private File data(String id){return new File(dir,id+".part");}
    private File meta(String id){return new File(dir,id+".meta");}
    static String hex(byte[] b){char[] digits="0123456789abcdef".toCharArray();char[] out=new char[b.length*2];for(int i=0;i<b.length;i++){out[i*2]=digits[(b[i]&255)>>4];out[i*2+1]=digits[b[i]&15];}return new String(out);}
    private static boolean good(String id){return id!=null&&id.matches("[a-f0-9]{48}");}
    private static String decode(String s){return new String(Base64.getUrlDecoder().decode(s),StandardCharsets.UTF_8);}
    private static String encode(String s){return Base64.getUrlEncoder().withoutPadding().encodeToString(s.getBytes(StandardCharsets.UTF_8));}
    private void save(Job j) throws IOException {
        File next=new File(dir,j.id+".meta.tmp");
        try(OutputStream o=new FileOutputStream(next)){o.write((encode(j.name)+"\n"+encode(j.folder)+"\n"+j.size+"\n").getBytes(StandardCharsets.UTF_8));o.flush();}
        try{Files.move(next.toPath(),meta(j.id).toPath(),StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}
        catch(AtomicMoveNotSupportedException e){Files.move(next.toPath(),meta(j.id).toPath(),StandardCopyOption.REPLACE_EXISTING);}
    }
    private synchronized void load(){
        File[] metas=dir.listFiles((d,n)->n.endsWith(".meta"));if(metas==null)return;
        for(File f:metas){try{
            String id=f.getName().substring(0,f.getName().length()-5);if(!good(id))continue;
            List<String> lines=Files.readAllLines(f.toPath(),StandardCharsets.UTF_8);if(lines.size()<3)continue;
            String name=decode(lines.get(0)),folder=decode(lines.get(1));long size=Long.parseLong(lines.get(2));
            if(LanServer.cleanName(name)==null||size<0||size>MAX_FILE||!LanServer.validFolder(folder))continue;
            File part=data(id);if(!part.exists()||part.length()>size)continue;
            jobs.put(id,new Job(id,name,folder,size,part.length()));
        }catch(Exception ignored){}}
    }
    public synchronized Job begin(String name,String folder,long size,String resumeId)throws Exception{
        if(LanServer.cleanName(name)==null||!LanServer.validFolder(folder)||size<0||size>MAX_FILE)throw new IllegalArgumentException("Invalid upload details");
        if(good(resumeId)){
            Job old=jobs.get(resumeId);
            if(old!=null&&old.name.equals(name)&&old.folder.equals(folder)&&old.size==size)return old;
        }
        if(jobs.size()>=100)throw new IOException("Too many pending uploads; complete or cancel an existing transfer");
        byte[] random=new byte[24];rng.nextBytes(random);String id=hex(random);
        Job j=new Job(id,name,folder,size,0);Files.createFile(data(id).toPath());save(j);jobs.put(id,j);return j;
    }
    public synchronized Job find(String id)throws FileNotFoundException{Job j=jobs.get(id);if(j==null)throw new FileNotFoundException("Transfer expired or cancelled");return j;}
    public void append(String id,long offset,InputStream stream,long length)throws Exception{
        Job j=find(id);synchronized(j){
            if(length<0||length>MAX_CHUNK||offset!=j.offset||offset+length>j.size)throw new IllegalArgumentException("Chunk offset or length mismatch; resume from current offset");
            try(RandomAccessFile f=new RandomAccessFile(data(id),"rw")){
                f.seek(offset);byte[] buf=new byte[65536];long left=length;
                try{while(left>0){int n=stream.read(buf,0,(int)Math.min(buf.length,left));if(n<0)throw new EOFException("Upload interrupted");f.write(buf,0,n);left-=n;}f.getFD().sync();}
                catch(Exception e){f.setLength(offset);throw e;}
            }
            j.offset+=length;
        }
    }
    public LanServer.Entry finish(String id,LanServer.Repository repository,String expectedHash)throws Exception {
        Job j=find(id);synchronized(j){
            if(j.offset!=j.size)throw new IllegalArgumentException("Upload incomplete");
            if(expectedHash!=null&&!expectedHash.isEmpty()){
                if(!expectedHash.matches("[a-fA-F0-9]{64}"))throw new IllegalArgumentException("Invalid SHA-256 checksum");
                MessageDigest digest=MessageDigest.getInstance("SHA-256");try(InputStream input=new FileInputStream(data(id))){byte[] b=new byte[65536];int n;while((n=input.read(b))!=-1)digest.update(b,0,n);}
                if(!MessageDigest.isEqual(hex(digest.digest()).getBytes(StandardCharsets.US_ASCII),expectedHash.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.US_ASCII)))throw new IllegalArgumentException("SHA-256 verification failed");
            }
            LanServer.Entry entry;try(InputStream src=new FileInputStream(data(id))){entry=repository.uploadTo(j.name,j.folder,src,j.size);}
            cancel(id);return entry;
        }
    }
    public synchronized void cancel(String id){Job j=jobs.remove(id);if(j!=null){data(id).delete();meta(id).delete();}}
}
