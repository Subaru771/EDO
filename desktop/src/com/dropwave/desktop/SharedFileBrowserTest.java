package com.dropwave.desktop;

import java.nio.file.*;
import java.util.*;

/** Filesystem safety and mutation regression checks for Edo's desktop Files screen. */
public class SharedFileBrowserTest {
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    private static void shouldFail(Throwing op){try{op.go();throw new AssertionError("Should reject unsafe operation");}catch(java.io.IOException expected){}}
    interface Throwing { void go() throws java.io.IOException; }
    public static void main(String[] args) throws Exception {
        Path sandbox=Files.createTempDirectory("edo-files-test-");Path shared=Files.createDirectory(sandbox.resolve("share"));
        Path outside=Files.createDirectory(sandbox.resolve("outside"));
        Files.writeString(outside.resolve("secret.txt"),"secret");
        try {
            SharedFileBrowser files=new SharedFileBrowser(shared);
            check(files.list(shared).isEmpty(),"Root should start empty");
            Path folder=files.newFolder(shared,"Photos");
            check(Files.isDirectory(folder),"New folder failed");
            shouldFail(()->files.newFolder(shared,"../escape"));
            shouldFail(()->files.enter(outside));
            Path file=Files.writeString(folder.resolve("image.txt"),"hello");
            check(files.list(folder).size()==1,"Listing failed");
            Path renamed=files.rename(file,"notes.txt");
            check(Files.readString(renamed).equals("hello"),"Rename modified data");
            Path copied=files.paste(List.of(renamed),folder,false).get(0);
            check(Files.exists(copied)&&!copied.equals(renamed),"Copy must avoid collisions");
            Path moved=files.paste(List.of(copied),shared,true).get(0);
            check(Files.exists(moved)&&!Files.exists(copied),"Move failed");
            shouldFail(()->files.paste(List.of(folder),folder,false));
            shouldFail(()->files.delete(List.of(shared)));
            Path imported=files.importFiles(List.of(outside.resolve("secret.txt")),shared).get(0);
            check(Files.readString(imported).equals("secret"),"External import failed");
            try {
                Path link=shared.resolve("outside-link");Files.createSymbolicLink(link,outside);
                shouldFail(()->files.enter(link));
                shouldFail(()->files.paste(List.of(link),shared,false));
                files.delete(List.of(folder));
                check(!Files.exists(folder),"Recursive removal failed");
                check(Files.exists(outside.resolve("secret.txt")),"External file must survive");
            } catch(UnsupportedOperationException | java.nio.file.FileSystemException ignored) {
                files.delete(List.of(folder));
            }
            check(Files.exists(outside.resolve("secret.txt")),"Outside tree modified");
            System.out.println("PASS: SharedFileBrowser create/list/rename/copy/move/import/delete/boundary/symlink");
        } finally {
            Files.walk(sandbox).sorted(Comparator.reverseOrder()).forEach(p->{try{Files.deleteIfExists(p);}catch(Exception ignored){}});
        }
    }
}
