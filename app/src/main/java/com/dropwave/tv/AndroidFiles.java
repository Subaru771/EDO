package com.dropwave.tv;

import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.os.StatFs;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** Saves new files into Downloads/Edo while retaining access to previous DropWave downloads. */
public final class AndroidFiles implements LanServer.Repository {
    private static final String FOLDER=Environment.DIRECTORY_DOWNLOADS+"/Edo/";
    private static final String LEGACY_FOLDER=Environment.DIRECTORY_DOWNLOADS+"/DropWave/";
    private static final String PREF="shared_uris";
    private static final String FOLDERS="folders";
    private final Context ctx;
    private final ContentResolver resolver;
    public AndroidFiles(Context context){ctx=context.getApplicationContext();resolver=ctx.getContentResolver();}

    /** Import an Android Share Sheet URI as a durable received file, even for non-persistable grants. */
    public LanServer.Entry importIncoming(Uri uri,String folder)throws Exception {
        if(uri==null)throw new FileNotFoundException("No document supplied");
        if(!LanServer.validFolder(folder))throw new IllegalArgumentException("Invalid destination");
        Doc info=metadata(uri);
        String name=LanServer.cleanName(info.name);
        if(name==null)name="Shared file";
        if(info.size>=0){
            try(InputStream in=resolver.openInputStream(uri)){
                if(in==null)throw new IOException("Cannot read the shared document");
                return uploadTo(name,folder,in,info.size);
            }
        }
        // Some content providers do not advertise their length. Stream to a bounded
        // temporary file so MediaStore receives a trustworthy Content-Length.
        File stage=File.createTempFile("edo-incoming-",".part",ctx.getCacheDir());
        try{
            try(InputStream in=resolver.openInputStream(uri);OutputStream out=new BufferedOutputStream(new FileOutputStream(stage))){
                if(in==null)throw new IOException("Cannot read the shared document");
                byte[] buffer=new byte[131072];long total=0;int n;
                while((n=in.read(buffer))!=-1){total+=n;if(total>100L*1024*1024*1024)throw new IOException("File exceeds 100 GB");out.write(buffer,0,n);}
            }
            try(InputStream in=new BufferedInputStream(new FileInputStream(stage))){return uploadTo(name,folder,in,stage.length());}
        }finally{stage.delete();}
    }

    public synchronized void share(Uri uri) throws Exception {
        // The document picker grants read access; persist it for future sessions.
        resolver.takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION);
        Set<String> shared=new HashSet<>(ctx.getSharedPreferences("dropwave",0).getStringSet(PREF,Collections.emptySet()));
        shared.add(uri.toString());ctx.getSharedPreferences("dropwave",0).edit().putStringSet(PREF,shared).apply();
    }
    private synchronized Set<String> shared(){return new HashSet<>(ctx.getSharedPreferences("dropwave",0).getStringSet(PREF,Collections.emptySet()));}
    private static String key(Uri u){
        try{
            byte[] h=MessageDigest.getInstance("SHA-256").digest(u.toString().getBytes(StandardCharsets.UTF_8));
            return "s"+Base64.getUrlEncoder().withoutPadding().encodeToString(h).substring(0,22);
        }catch(Exception e){throw new IllegalStateException(e);}
    }
    private static String mime(String name,String hint){
        if(hint!=null&&!hint.isEmpty())return hint;
        String m=java.net.URLConnection.guessContentTypeFromName(name);
        return m==null?"application/octet-stream":m;
    }
    private static class Doc {String name,mime;long size;}
    private Doc metadata(Uri uri){
        Doc d=new Doc();d.name="Shared file";d.mime=resolver.getType(uri);d.size=-1;
        try(Cursor c=resolver.query(uri,new String[]{OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE},null,null,null)){
            if(c!=null&&c.moveToFirst()){
                int n=c.getColumnIndex(OpenableColumns.DISPLAY_NAME),s=c.getColumnIndex(OpenableColumns.SIZE);
                if(n>=0&&!c.isNull(n))d.name=c.getString(n);
                if(s>=0&&!c.isNull(s))d.size=c.getLong(s);
            }
        }catch(Exception ignored){}
        return d;
    }
    @Override public synchronized List<LanServer.Entry> list() {
        List<LanServer.Entry> all=new ArrayList<>();
        String[] cols={MediaStore.MediaColumns._ID,MediaStore.MediaColumns.DISPLAY_NAME,MediaStore.MediaColumns.SIZE,MediaStore.MediaColumns.MIME_TYPE,MediaStore.MediaColumns.RELATIVE_PATH};
        // Keep legacy files visible until Android permits migration. MediaStore IDs stay
        // stable when a file moves, so existing file links and actions continue to work.
        for(String base:new String[]{FOLDER,LEGACY_FOLDER}){
            try(Cursor c=resolver.query(MediaStore.Downloads.EXTERNAL_CONTENT_URI,cols,
                    MediaStore.MediaColumns.RELATIVE_PATH+" LIKE ?",new String[]{base+"%"},
                    MediaStore.MediaColumns.DATE_MODIFIED+" DESC")) {
                if(c!=null)while(c.moveToNext()){
                    long id=c.getLong(0);String name=c.getString(1);long size=c.isNull(2)?0:c.getLong(2);String mim=c.getString(3);
                    String path=c.getString(4);
                    if(path==null||!path.startsWith(base))continue;
                    String sub=path.substring(base.length());
                    if(sub.endsWith("/"))sub=sub.substring(0,sub.length()-1);
                    if(!LanServer.validFolder(sub))continue;
                    all.add(new LanServer.Entry("m"+id,name,size,mime(name,mim),
                        base.equals(FOLDER)?"TV downloads":"Legacy TV downloads",sub));
                }
            }catch(Exception ignored){}
        }
        for(String raw:shared()) {
            try{
                Uri uri=Uri.parse(raw);Doc d=metadata(uri);
                if(d.size>=0)all.add(new LanServer.Entry(key(uri),d.name,d.size,mime(d.name,d.mime),"Shared from TV"));
            }catch(Exception ignored){}
        }
        return all;
    }
    /**
     * Best-effort, non-destructive migration of previous Edo/DropWave downloads.
     * Existing MediaStore rows are moved (not copied or deleted). Android may deny
     * a move for files that are not owned by this app; list() still displays them.
     * Conflicting filenames are renamed safely instead of overwriting new files.
     */
    public int migrateLegacyDownloads() {
        int moved=0;
        String[] columns={MediaStore.MediaColumns._ID,MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.RELATIVE_PATH};
        // Track the destination names for each relative folder to prevent collisions.
        Map<String,Set<String>> existing=new HashMap<>();
        try(Cursor c=resolver.query(MediaStore.Downloads.EXTERNAL_CONTENT_URI,columns,
                MediaStore.MediaColumns.RELATIVE_PATH+" LIKE ?",new String[]{FOLDER+"%"},null)){
            if(c!=null)while(c.moveToNext()){
                String path=c.getString(2),name=c.getString(1);
                if(path!=null&&path.startsWith(FOLDER)&&name!=null)
                    existing.computeIfAbsent(path,k->new HashSet<>()).add(name.toLowerCase(Locale.ROOT));
            }
        }catch(Exception ignored){}
        try(Cursor c=resolver.query(MediaStore.Downloads.EXTERNAL_CONTENT_URI,columns,
                MediaStore.MediaColumns.RELATIVE_PATH+" LIKE ?",new String[]{LEGACY_FOLDER+"%"},null)){
            if(c!=null)while(c.moveToNext()){
                String path=c.getString(2),name=c.getString(1);
                if(path==null||!path.startsWith(LEGACY_FOLDER)||name==null)continue;
                String sub=path.substring(LEGACY_FOLDER.length());
                if(sub.endsWith("/"))sub=sub.substring(0,sub.length()-1);
                if(!LanServer.validFolder(sub))continue;
                String destination=FOLDER+(sub.isEmpty()?"":sub+"/");
                Set<String> names=existing.computeIfAbsent(destination,k->new HashSet<>());
                String safeName=uniqueName(name,names);
                ContentValues update=new ContentValues();
                update.put(MediaStore.MediaColumns.RELATIVE_PATH,destination);
                if(!safeName.equals(name))update.put(MediaStore.MediaColumns.DISPLAY_NAME,safeName);
                try{
                    Uri uri=ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI,c.getLong(0));
                    if(resolver.update(uri,update,null,null)>0){
                        names.add(safeName.toLowerCase(Locale.ROOT));moved++;
                    }
                }catch(Exception ignored){
                    // Leave the original file untouched and discoverable in its old folder.
                }
            }
        }catch(Exception ignored){}
        return moved;
    }

    private static String uniqueName(String desired,Set<String> existing) {
        String candidate=desired;int dot=desired.lastIndexOf('.');String stem=dot>0?desired.substring(0,dot):desired;
        String extension=dot>0?desired.substring(dot):"";int n=2;
        while(existing.contains(candidate.toLowerCase(Locale.ROOT))){candidate=stem+" ("+(n++)+")"+extension;}
        return candidate;
    }
    @Override public synchronized LanServer.Entry upload(String filename,InputStream body,long length) throws Exception {
        return uploadTo(filename,"",body,length);
    }
    @Override public synchronized LanServer.Entry uploadTo(String filename,String folder,InputStream body,long length) throws Exception {
        if(!LanServer.validFolder(folder))throw new IllegalArgumentException("Invalid folder");
        Set<String> names=new HashSet<>();for(LanServer.Entry e:list())if(e.folder.equals(folder))names.add(e.name.toLowerCase(Locale.ROOT));
        String name=uniqueName(filename,names);
        ContentValues values=new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME,name);
        values.put(MediaStore.MediaColumns.MIME_TYPE,mime(name,null));
        values.put(MediaStore.MediaColumns.RELATIVE_PATH,FOLDER+(folder.isEmpty()?"":folder+"/"));
        values.put(MediaStore.MediaColumns.IS_PENDING,1);
        Uri uri=resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,values);
        if(uri==null)throw new IOException("Could not create file. Check available storage.");
        boolean complete=false;
        try {
            try(OutputStream dst=resolver.openOutputStream(uri,"w")) {
                if(dst==null)throw new IOException("File destination unavailable");
                LanServer.copyExactly(body,dst,length);
            }
            ContentValues finished=new ContentValues();finished.put(MediaStore.MediaColumns.IS_PENDING,0);
            resolver.update(uri,finished,null,null);
            complete=true;
            return new LanServer.Entry("m"+android.content.ContentUris.parseId(uri),name,length,mime(name,null),"TV downloads",folder);
        }finally{if(!complete)resolver.delete(uri,null,null);}
    }

    @Override public long freeBytes() {
        try {StatFs external=new StatFs(Environment.getExternalStorageDirectory().getAbsolutePath());
            StatFs internal=new StatFs(ctx.getFilesDir().getAbsolutePath());
            return Math.min(external.getAvailableBytes(),internal.getAvailableBytes());}
        catch(Exception e){return -1;}
    }
    private synchronized Set<String> folderSet(){return new HashSet<>(ctx.getSharedPreferences("dropwave",0).getStringSet(FOLDERS,Collections.emptySet()));}
    private synchronized void saveFolders(Set<String> f){ctx.getSharedPreferences("dropwave",0).edit().putStringSet(FOLDERS,new HashSet<>(f)).apply();}
    @Override public synchronized List<String> folders(){
        Set<String> paths=folderSet();
        for(LanServer.Entry e:list()){
            String folder=e.folder;
            while(!folder.isEmpty()){
                paths.add(folder);int slash=folder.lastIndexOf('/');folder=slash<0?"":folder.substring(0,slash);
            }
        }
        ArrayList<String> result=new ArrayList<>(paths);Collections.sort(result);return result;
    }
    @Override public synchronized void mkdir(String folder){
        if(!LanServer.validFolder(folder)||folder.isEmpty())throw new IllegalArgumentException("Invalid folder");
        Set<String> paths=folderSet();String at="";
        for(String part:folder.split("/")){at=at.isEmpty()?part:at+"/"+part;paths.add(at);}
        saveFolders(paths);
    }
    @Override public synchronized void removeFolder(String folder)throws Exception {
        if(folder.isEmpty()||!LanServer.validFolder(folder))throw new IllegalArgumentException("Invalid folder");
        for(LanServer.Entry e:list())if(e.folder.equals(folder)||e.folder.startsWith(folder+"/"))throw new IOException("Move or delete files inside this folder first");
        Set<String> dirs=folderSet();for(String f:dirs)if(f.startsWith(folder+"/"))throw new IOException("Remove subfolders first");
        dirs.remove(folder);saveFolders(dirs);
    }
    @Override public synchronized void rename(String id,String name)throws Exception {
        if(!name.equals(LanServer.cleanName(name)))throw new IllegalArgumentException("Invalid filename");
        Uri uri=mediaUri(id);ContentValues values=new ContentValues();values.put(MediaStore.MediaColumns.DISPLAY_NAME,name);
        if(resolver.update(uri,values,null,null)!=1)throw new IOException("Rename failed");
    }
    @Override public synchronized void move(String id,String folder)throws Exception {
        if(!LanServer.validFolder(folder))throw new IllegalArgumentException("Invalid folder");
        Uri uri=mediaUri(id);ContentValues values=new ContentValues();values.put(MediaStore.MediaColumns.RELATIVE_PATH,FOLDER+(folder.isEmpty()?"":folder+"/"));
        if(resolver.update(uri,values,null,null)!=1)throw new IOException("Move failed");
    }
    @Override public synchronized LanServer.Entry copy(String id,String folder)throws Exception {
        if(!LanServer.validFolder(folder))throw new IllegalArgumentException("Invalid folder");
        try(LanServer.Opened original=open(id)) {return uploadTo(original.name,folder,original.stream,original.length);}
    }
    private Uri sharedUri(String id) throws FileNotFoundException {
        for(String raw:shared()) {Uri uri=Uri.parse(raw);if(key(uri).equals(id))return uri;}
        throw new FileNotFoundException("This file is no longer shared");
    }
    private Uri mediaUri(String id) throws FileNotFoundException {
        for(LanServer.Entry e:list()){
            if(e.id.equals(id) && id.startsWith("m")){
                try{return ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI,Long.parseLong(id.substring(1)));}
                catch(NumberFormatException ignored){}
            }
        }
        throw new FileNotFoundException("File not found");
    }
    @Override public LanServer.Opened open(String id) throws Exception {
        if(id==null || id.length()<2)throw new FileNotFoundException("Missing file");
        Uri uri=id.startsWith("s")?sharedUri(id):mediaUri(id);
        Doc doc=metadata(uri);
        if(doc.size<0)throw new IOException("File size is unavailable from its provider");
        InputStream in=resolver.openInputStream(uri);
        if(in==null)throw new FileNotFoundException("File unavailable");
        return new LanServer.Opened(in,doc.size,doc.name,mime(doc.name,doc.mime));
    }
    @Override public synchronized void remove(String id) throws Exception {
        if(id!=null&&id.startsWith("s")){
            Uri uri=sharedUri(id);Set<String> remaining=shared();remaining.remove(uri.toString());
            ctx.getSharedPreferences("dropwave",0).edit().putStringSet(PREF,remaining).apply();
            // Removing shared content unlinks it; original file remains untouched.
        }else{
            Uri uri=mediaUri(id);
            if(resolver.delete(uri,null,null)==0)throw new IOException("Could not delete file");
        }
    }
}
