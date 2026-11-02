import com.dropwave.tv.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class LibraryIndexTest {
    public static void main(String[] args)throws Exception{
        List<LanServer.Entry> items=Arrays.asList(
            new LanServer.Entry("a","concert.mkv",110,"video/x-matroska","TV",""),
            new LanServer.Entry("b","image.jpg",33,"image/jpeg","TV","Camera"),
            new LanServer.Entry("c","example.apk",22,"application/octet-stream","TV",""),
            new LanServer.Entry("d","doc.pdf",19,"application/pdf","TV","")
        );
        String stats=LibraryIndex.json(items,2048);
        if(!stats.contains("\"count\":4")||!stats.contains("\"Videos\"")||!stats.contains("\"Photos\"")||!stats.contains("\"APKs\"")||!stats.contains("\"Documents\"")||!stats.contains("\"bytes\":184"))throw new AssertionError(stats);
        String found=LibraryIndex.duplicates(items,"concert.mkv","",110);
        if(!found.contains("\"sameSize\":true"))throw new AssertionError(found);
        String notSame=LibraryIndex.duplicates(items,"concert.mkv","",10);
        if(!notSame.contains("\"sameSize\":false"))throw new AssertionError(notSame);
        String missing=LibraryIndex.duplicates(items,"concert.mkv","Camera",110);
        if(!missing.contains("\"possibleDuplicates\":[]"))throw new AssertionError(missing);
        System.out.println("PASS: v5 media index and conservative duplicate lookup");
    }
}
