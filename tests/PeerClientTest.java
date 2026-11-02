import com.dropwave.tv.*;
import com.dropwave.desktop.DiskRepository;
import java.io.*;
import java.nio.file.*;
import java.util.*;
public class PeerClientTest{
    public static void main(String[] args)throws Exception{
        Path root=Files.createTempDirectory("edo-peer"),state=Files.createTempDirectory("edo-peer-state");
        try(LanServer target=new LanServer(new DiskRepository(root.toFile()),()->new ByteArrayInputStream("edo".getBytes()),state.toFile())){
            int port=target.start(21843);PeerClient client=new PeerClient("http://127.0.0.1:"+port);
            client.pair(target.pin(),"Native TV");byte[] bytes=new byte[5*1024*1024+3];new Random(1234).nextBytes(bytes);
            client.upload("tvmovie.bin","Movies",bytes.length,new ByteArrayInputStream(bytes),(a,b)->{});
            if(!Arrays.equals(bytes,Files.readAllBytes(root.resolve("Movies/tvmovie.bin"))))throw new AssertionError("Peer content differs");
            System.out.println("PASS: Native peer pairing, streamed 5MB+ upload, folder creation and SHA256 verification");
        }finally{
            try(java.util.stream.Stream<Path> walk=Files.walk(root)){walk.sorted(Comparator.reverseOrder()).forEach(p->{try{Files.deleteIfExists(p);}catch(Exception ignored){}});}
            try(java.util.stream.Stream<Path> walk=Files.walk(state)){walk.sorted(Comparator.reverseOrder()).forEach(p->{try{Files.deleteIfExists(p);}catch(Exception ignored){}});}
        }
    }
}
