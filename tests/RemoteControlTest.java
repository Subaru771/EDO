import com.dropwave.tv.*;
import com.dropwave.desktop.DiskRepository;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.concurrent.atomic.AtomicReference;
public class RemoteControlTest {
    static class Response {int status;String cookie;}
    static Response call(String base,String path,String method,String cookie)throws Exception{
        HttpURLConnection c=(HttpURLConnection)new URL(base+path).openConnection();c.setRequestMethod(method);c.setConnectTimeout(3000);c.setReadTimeout(3000);
        if(cookie!=null)c.setRequestProperty("Cookie",cookie);
        if(method.equals("POST")){c.setDoOutput(true);c.setFixedLengthStreamingMode(0);c.getOutputStream().close();}
        Response r=new Response();r.status=c.getResponseCode();r.cookie=c.getHeaderField("Set-Cookie");
        InputStream in=r.status>=400?c.getErrorStream():c.getInputStream();if(in!=null)in.close();c.disconnect();return r;
    }
    public static void main(String[] args)throws Exception{
        Path root=Files.createTempDirectory("edo-remote"),state=Files.createTempDirectory("edo-remote-state");
        try(LanServer s=new LanServer(new DiskRepository(root.toFile()),()->new ByteArrayInputStream(new byte[0]),state.toFile())){
            String base="http://127.0.0.1:"+s.start(21865);
            if(call(base,"/api/remote/action?action=up","POST",null).status!=401)throw new AssertionError("Remote did not require pairing");
            Response auth=call(base,"/api/pair?pin="+s.pin()+"&device=Browser","POST",null);
            String token=auth.cookie.split(";",2)[0];
            if(call(base,"/api/remote/action?action=up","POST",token).status!=403)throw new AssertionError("Remote enabled by default");
            AtomicReference<String> action=new AtomicReference<>();s.setRemoteControl(true,action::set);
            if(call(base,"/api/remote/action?action=not-a-key","POST",token).status!=400)throw new AssertionError("Invalid remote command accepted");
            if(call(base,"/api/remote/action?action=files","POST",token).status!=200||!"files".equals(action.get()))throw new AssertionError("Remote command failed");
            System.out.println("PASS: Remote commands require session + explicit TV opt-in; action allowlist enforced");
        }finally{for(Path dir:new Path[]{root,state})try(java.util.stream.Stream<Path> paths=Files.walk(dir)){paths.sorted(java.util.Comparator.reverseOrder()).forEach(p->{try{Files.deleteIfExists(p);}catch(Exception ignored){}});}}
    }
}
