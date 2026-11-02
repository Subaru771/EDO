package com.dropwave.desktop;
import com.dropwave.tv.LanServer;
import java.io.*;
import java.nio.file.*;
import java.util.*;
public final class SyncTest {
    static void check(boolean okay,String message){if(!okay)throw new AssertionError(message);}
    public static void main(String[] args)throws Exception{
        Path root=Files.createTempDirectory("dw-sync-target"),src=Files.createTempDirectory("dw-sync-source"),state=Files.createTempDirectory("dw-sync-state");
        try{
            Files.createDirectories(src.resolve("Photos"));Files.writeString(src.resolve("Photos/hello.txt"),"Hello from backup");
            try(LanServer srv=new LanServer(new DiskRepository(root.toFile()),()->new ByteArrayInputStream(new byte[0]),state.toFile())){
                int port=srv.start(19331);
                SyncClient client=new SyncClient("http://127.0.0.1:"+port,srv.pin(),src.toFile(),"DesktopBackup",s->{});
                check(client.run(false)==1,"First sync uploads one file");
                check(Files.readString(root.resolve("DesktopBackup/Photos/hello.txt")).equals("Hello from backup"),"Uploaded bytes are correct");
                check(client.run(false)==0,"Second sync skips unchanged file");
                Thread.sleep(15);Files.writeString(src.resolve("Photos/hello.txt"),"Updated copy!");
                check(client.run(false)==1,"Changed file backed up");
                check(Files.exists(root.resolve("DesktopBackup/Photos/hello (2).txt")),"Changed file creates a safe duplicate");
                System.out.println("PASS: One-way desktop backup, nested folders, unchanged-file skip, changed-file versioning");
            }
        }finally{
            for(Path dir:new Path[]{src,root,state})try(java.util.stream.Stream<Path> walk=Files.walk(dir)){walk.sorted(Comparator.reverseOrder()).forEach(p->{try{Files.deleteIfExists(p);}catch(Exception ignored){}});}
        }
    }
}
