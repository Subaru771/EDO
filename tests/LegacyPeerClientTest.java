import com.dropwave.tv.PeerClient;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpExchange;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
/** Verifies fallback against old Edo/DropWave POST /api/upload endpoint. */
public final class LegacyPeerClientTest {
    public static void main(String[] args)throws Exception {
        byte[] original=new byte[5*1024*1024+23];new Random(39).nextBytes(original);
        AtomicReference<byte[]> received=new AtomicReference<>();
        HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/api/pair",e->{
            reply(e,200,"{\"ok\":true}","Set-Cookie","dw_session=legacy123; Path=/; HttpOnly");
        });
        server.createContext("/api/status",e->{
            if(!"dw_session=legacy123".equals(e.getRequestHeaders().getFirst("Cookie"))){reply(e,401,"not paired",null,null);return;}
            reply(e,200,"{\"connected\":true}",null,null);
        });
        server.createContext("/api/upload/init",e->reply(e,404,"{\"error\":\"Not found\"}",null,null));
        server.createContext("/api/upload",e->{
            if(!"dw_session=legacy123".equals(e.getRequestHeaders().getFirst("Cookie"))){reply(e,401,"not paired",null,null);return;}
            try(InputStream in=e.getRequestBody();ByteArrayOutputStream data=new ByteArrayOutputStream()){
                in.transferTo(data);received.set(data.toByteArray());
            }
            reply(e,201,"{\"ok\":true}",null,null);
        });
        server.start();
        try{
            PeerClient client=new PeerClient("http://127.0.0.1:"+server.getAddress().getPort());
            client.pair("123456","Old-client check");
            client.upload("legacy-file.bin","",original.length,new ByteArrayInputStream(original),null);
            if(!Arrays.equals(original,received.get()))throw new AssertionError("Legacy upload content corrupted");
            System.out.println("PASS: v1 receiver API compatibility, 5MB+ streamed legacy upload");
        }finally{server.stop(0);}
    }
    private static void reply(HttpExchange e,int status,String payload,String extraName,String extraValue)throws IOException {
        if(extraName!=null)e.getResponseHeaders().set(extraName,extraValue);
        byte[] bytes=payload.getBytes(StandardCharsets.UTF_8);
        e.getResponseHeaders().set("Content-Type","application/json");
        e.sendResponseHeaders(status,bytes.length);
        try(OutputStream out=e.getResponseBody()){out.write(bytes);}
    }
}
