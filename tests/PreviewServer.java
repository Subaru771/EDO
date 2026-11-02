import com.dropwave.tv.LanServer;
import java.io.*;
import java.nio.file.*;
import java.util.*;

public class PreviewServer {
  static class Repo implements LanServer.Repository {
    Map<String,byte[]> files=new LinkedHashMap<>();
    Repo(){files.put("Holiday video.mp4",new byte[12345]);files.put("Photos.zip",new byte[27654]);files.put("Game installer.apk",new byte[8421]);}
    public synchronized List<LanServer.Entry> list(){List<LanServer.Entry> out=new ArrayList<>();for(var e:files.entrySet())out.add(new LanServer.Entry(e.getKey(),e.getKey(),e.getValue().length,"application/octet-stream","TV downloads"));return out;}
    public synchronized LanServer.Entry upload(String n,InputStream s,long len)throws Exception{ByteArrayOutputStream b=new ByteArrayOutputStream();LanServer.copyExactly(s,b,len);files.put(n,b.toByteArray());return new LanServer.Entry(n,n,len,"application/octet-stream","TV downloads");}
    public synchronized LanServer.Opened open(String id)throws Exception{if(!files.containsKey(id))throw new FileNotFoundException();return new LanServer.Opened(new ByteArrayInputStream(files.get(id)),files.get(id).length,id,"application/octet-stream");}
    public synchronized void remove(String id){files.remove(id);}
  }
  public static void main(String[] args)throws Exception{
    Path html=Paths.get(args[0]);
    LanServer server=new LanServer(new Repo(),()->Files.newInputStream(html));
    System.out.println("PORT="+server.start(19231));System.out.println("PIN="+server.pin());System.out.flush();
    System.in.read();server.close();
  }
}
