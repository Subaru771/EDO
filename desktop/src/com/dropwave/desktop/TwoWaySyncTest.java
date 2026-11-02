package com.dropwave.desktop;
import com.dropwave.tv.LanServer;
import java.io.*;
import java.nio.file.*;
import java.util.*;
public class TwoWaySyncTest {
    static void assertYes(boolean x,String message){if(!x)throw new AssertionError(message);}
    public static void main(String[] args)throws Exception{
        Path remote=Files.createTempDirectory("edo-merge-remote"),local=Files.createTempDirectory("edo-merge-local"),state=Files.createTempDirectory("edo-merge-state");
        try(LanServer server=new LanServer(new DiskRepository(remote.toFile()),()->new ByteArrayInputStream(new byte[0]),state.toFile())){
            int port=server.start(21862);String url="http://127.0.0.1:"+port;
            Files.createDirectories(remote.resolve("Backups/Sub"));Files.writeString(remote.resolve("Backups/Sub/only-remote.txt"),"hello remote");
            Files.writeString(remote.resolve("Backups/collision.txt"),"remote version");
            Files.writeString(local.resolve("collision.txt"),"local version");
            Files.writeString(local.resolve("only-local.txt"),"hello local");
            TwoWaySync sync=new TwoWaySync(url,server.pin(),local.toFile(),"Backups",m->{});
            int count=sync.merge();assertYes(count>=3,"Did not merge in both directions");
            assertYes(Files.readString(local.resolve("collision.txt")).equals("local version"),"Local conflict overwritten");
            assertYes(Files.readString(remote.resolve("Backups/collision.txt")).equals("remote version"),"Remote conflict overwritten");
            assertYes(Files.exists(local.resolve("Sub/only-remote.txt")),"Missing remote-only file");
            assertYes(Files.exists(remote.resolve("Backups/only-local.txt")),"Missing local-only file");
            try(java.util.stream.Stream<Path> paths=Files.list(local)){
                assertYes(paths.anyMatch(p->p.getFileName().toString().contains(".from-edo-")),"Missing conflict copy");
            }
            System.out.println("PASS: conservative two-way merge, nested folders, push/pull, no overwrite, conflict copies");
        }finally{
            for(Path dir:new Path[]{remote,local,state})try(java.util.stream.Stream<Path> paths=Files.walk(dir)){
                paths.sorted(Comparator.reverseOrder()).forEach(p->{try{Files.deleteIfExists(p);}catch(Exception ignored){}});
            }
        }
    }
}
