import com.dropwave.tv.*;
import com.dropwave.desktop.DiskRepository;
import java.io.*;
import java.nio.file.*;
public class UdpDiscoveryTest{
 public static void main(String[] args)throws Exception{
  Path dir=Files.createTempDirectory("edo-discover"),state=Files.createTempDirectory("edo-discover-state");
  try(LanServer server=new LanServer(new DiskRepository(dir.toFile()),()->new ByteArrayInputStream(new byte[0]),state.toFile())){
   int port=server.start(21889);boolean found=false;
   for(UdpDiscovery.Peer p:UdpDiscovery.scan(1200))if(p.url.endsWith(":"+port))found=true;
   if(!found)throw new AssertionError("EDO discovery receiver not found");
   System.out.println("PASS: UDP LAN discovery responder and client scanner");
  }finally{for(Path d:new Path[]{dir,state})try(java.util.stream.Stream<Path> walk=Files.walk(d)){
   walk.sorted(java.util.Comparator.reverseOrder()).forEach(p->{try{Files.deleteIfExists(p);}catch(Exception ignored){}});
  }}
 }
}
