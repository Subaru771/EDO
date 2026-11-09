package com.dropwave.desktop;

import com.dropwave.tv.LanServer;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Strictly sandboxed repository: paths must remain under the user-picked shared directory. */
public final class DiskRepository implements LanServer.Repository {
    private final Path root;
    public DiskRepository(File root)throws IOException {if(!root.isDirectory())throw new FileNotFoundException("Choose an existing folder");this.root=root.toPath().toRealPath();}
    private Path folder(String folder)throws IOException {
        if(!LanServer.validFolder(folder))throw new IOException("Invalid folder name");
        Path p=root.resolve(folder).normalize();if(!p.startsWith(root))throw new IOException("Outside shared folder");
        Path q=p;while(!Files.exists(q))q=q.getParent();if(!q.toRealPath().startsWith(root))throw new IOException("Folder path uses a link outside shared folder");
        return p;
    }
    private Path path(String id)throws IOException {
        if(id==null||!id.startsWith("d")||id.length()>3000)throw new FileNotFoundException("Invalid file ID");
        String rel;try{rel=new String(Base64.getUrlDecoder().decode(id.substring(1)),java.nio.charset.StandardCharsets.UTF_8);}catch(Exception e){throw new IOException("Invalid file ID");}
        Path p=root.resolve(rel).normalize();if(!p.startsWith(root)||!Files.isRegularFile(p,LinkOption.NOFOLLOW_LINKS)||!p.toRealPath().startsWith(root))throw new FileNotFoundException("File outside shared directory");return p;
    }
    private String id(Path p){return "d"+Base64.getUrlEncoder().withoutPadding().encodeToString(root.relativize(p).toString().replace(File.separatorChar,'/').getBytes(java.nio.charset.StandardCharsets.UTF_8));}
    @Override public synchronized List<LanServer.Entry> list() throws Exception {
        List<LanServer.Entry> result=new ArrayList<>();
        try(java.util.stream.Stream<Path> walk=Files.walk(root,12)){
            walk.filter(p->Files.isRegularFile(p,LinkOption.NOFOLLOW_LINKS)).limit(3000).forEach(p->{try{
                if(!p.toRealPath().startsWith(root))return;
                String folder=root.relativize(p.getParent()).toString().replace(File.separatorChar,'/');
                String name=p.getFileName().toString();String mime=Files.probeContentType(p);if(mime==null)mime="application/octet-stream";
                result.add(new LanServer.Entry(id(p),name,Files.size(p),mime,"Desktop shared",folder));
            }catch(Exception ignored){}});
        }
        return result;
    }
    @Override public synchronized List<String> folders()throws Exception {
        List<String> all=new ArrayList<>();try(java.util.stream.Stream<Path> walk=Files.walk(root,12)){
            walk.filter(p->!p.equals(root)&&Files.isDirectory(p,LinkOption.NOFOLLOW_LINKS)).limit(1000).forEach(p->{try{if(p.toRealPath().startsWith(root))all.add(root.relativize(p).toString().replace(File.separatorChar,'/'));}catch(Exception ignored){}});
        }Collections.sort(all);return all;
    }
    @Override public synchronized LanServer.Entry upload(String name,InputStream in,long length)throws Exception {return uploadTo(name,"",in,length);}
    @Override public synchronized LanServer.Entry uploadTo(String name,String folder,InputStream in,long length)throws Exception{
        if(!name.equals(LanServer.cleanName(name)))throw new IOException("Invalid filename");
        Path parent=folder(folder);Files.createDirectories(parent);if(!parent.toRealPath().startsWith(root))throw new IOException("Invalid folder");
        String stem=name,ext="";int dot=name.lastIndexOf('.');if(dot>0){stem=name.substring(0,dot);ext=name.substring(dot);}
        Path target=parent.resolve(name);int n=2;while(Files.exists(target))target=parent.resolve(stem+" ("+(n++)+")"+ext);
        Path tmp=Files.createTempFile(parent,".dropwave-",".partial");boolean done=false;
        try(OutputStream out=Files.newOutputStream(tmp)){
            LanServer.copyExactly(in,out,length);out.flush();
        }catch(Exception e){Files.deleteIfExists(tmp);throw e;}
        try{Files.move(tmp,target,StandardCopyOption.ATOMIC_MOVE);done=true;}catch(AtomicMoveNotSupportedException e){Files.move(tmp,target);done=true;}finally{if(!done)Files.deleteIfExists(tmp);}
        String mime=Files.probeContentType(target);return new LanServer.Entry(id(target),target.getFileName().toString(),length,mime==null?"application/octet-stream":mime,"Desktop shared",folder);
    }
    @Override public LanServer.Opened open(String id)throws Exception {Path p=path(id);String mime=Files.probeContentType(p);return new LanServer.Opened(Files.newInputStream(p),Files.size(p),p.getFileName().toString(),mime==null?"application/octet-stream":mime);}
    @Override public synchronized void remove(String id)throws Exception{Files.delete(path(id));}
    @Override public long freeBytes(){try{return Files.getFileStore(root).getUsableSpace();}catch(Exception e){return -1;}}
    @Override public synchronized void mkdir(String folder)throws Exception{if(folder.isEmpty())throw new IOException("Invalid folder");Files.createDirectories(folder(folder));}
    @Override public synchronized void removeFolder(String folder)throws Exception{if(folder.isEmpty())throw new IOException("Cannot remove root");Files.delete(folder(folder));}
    @Override public synchronized void rename(String id,String name)throws Exception{if(!name.equals(LanServer.cleanName(name)))throw new IOException("Invalid name");Path p=path(id);Files.move(p,p.getParent().resolve(name));}
    @Override public synchronized void move(String id,String folder)throws Exception{Path p=path(id),dest=folder(folder);Files.createDirectories(dest);if(!dest.toRealPath().startsWith(root))throw new IOException("Invalid folder");Files.move(p,dest.resolve(p.getFileName()));}
    @Override public synchronized void moveTo(String id,String folder,String name)throws Exception{
        if(!name.equals(LanServer.cleanName(name)))throw new IOException("Invalid name");
        Path p=path(id),dest=folder(folder);Files.createDirectories(dest);
        if(!dest.toRealPath().startsWith(root))throw new IOException("Invalid folder");
        Files.move(p,dest.resolve(name));
    }
    @Override public synchronized LanServer.Entry copy(String id,String folder)throws Exception{try(LanServer.Opened o=open(id)){return uploadTo(o.name,folder,o.stream,o.length);}}
}
