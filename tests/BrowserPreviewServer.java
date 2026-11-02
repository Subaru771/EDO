import com.dropwave.tv.LanServer;
import com.dropwave.desktop.DiskRepository;
import java.io.*;
import java.nio.file.*;
public class BrowserPreviewServer {
  public static void main(String[] args)throws Exception{
    Path root=Files.createTempDirectory("dropwave-browser-");
    File page=new File("app/src/main/assets/index.html");
    try(LanServer server=new LanServer(new DiskRepository(root.toFile()),()->new FileInputStream(page),Files.createTempDirectory("dropwave-teststate").toFile())){
      int port=server.start(18991);System.out.println("READY "+port+" "+server.pin());System.out.flush();
      new BufferedReader(new InputStreamReader(System.in)).readLine();
    }
  }
}
