import com.dropwave.tv.LanServer;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class SmokeTest {
    static final class MemoryRepo implements LanServer.Repository {
        Map<String,byte[]> data=new LinkedHashMap<>();
        public synchronized List<LanServer.Entry> list(){
            List<LanServer.Entry> res=new ArrayList<>();
            for(String k:data.keySet())res.add(new LanServer.Entry(k,k,data.get(k).length,"application/octet-stream","TV downloads"));
            return res;
        }
        public synchronized LanServer.Entry upload(String n,InputStream src,long len) throws Exception {
            ByteArrayOutputStream out=new ByteArrayOutputStream();LanServer.copyExactly(src,out,len);
            data.put(n,out.toByteArray());return new LanServer.Entry(n,n,len,"application/octet-stream","TV downloads");
        }
        public synchronized LanServer.Opened open(String id)throws Exception{
            if(!data.containsKey(id))throw new FileNotFoundException();
            return new LanServer.Opened(new ByteArrayInputStream(data.get(id)),data.get(id).length,id,"application/octet-stream");
        }
        public synchronized void remove(String id){data.remove(id);}
    }
    static final class Response {int status;String cookie;String body; byte[] bytes;}
    static Response request(String base,String route,String method,byte[] payload,String cookie,String origin) throws Exception {
        HttpURLConnection c=(HttpURLConnection)new URL(base+route).openConnection();
        c.setRequestMethod(method);c.setConnectTimeout(3000);c.setReadTimeout(5000);
        if(cookie!=null)c.setRequestProperty("Cookie",cookie);
        if(origin!=null)c.setRequestProperty("Origin",origin);
        if(payload!=null){c.setDoOutput(true);c.setFixedLengthStreamingMode(payload.length);try(OutputStream o=c.getOutputStream()){o.write(payload);}}
        Response r=new Response();r.status=c.getResponseCode();r.cookie=c.getHeaderField("Set-Cookie");
        InputStream in=r.status>=400?c.getErrorStream():c.getInputStream();
        if(in==null)r.bytes=new byte[0];else try(InputStream i=in;ByteArrayOutputStream out=new ByteArrayOutputStream()) {byte[] buf=new byte[8192];int n;while((n=i.read(buf))!=-1)out.write(buf,0,n);r.bytes=out.toByteArray();}
        r.body=new String(r.bytes,StandardCharsets.UTF_8);c.disconnect();return r;
    }
    static void eq(boolean b,String why){if(!b)throw new AssertionError(why);}
    public static void main(String[] args)throws Exception{
        MemoryRepo r=new MemoryRepo();
        try(LanServer server=new LanServer(r,()->new ByteArrayInputStream("<h1>DropWave</h1>".getBytes(StandardCharsets.UTF_8)))){
            int p=server.start(19213);String base="http://127.0.0.1:"+p;
            Response home=request(base,"/","GET",null,null,null);eq(home.status==200&&home.body.contains("DropWave"),"home UI");
            eq(request(base,"/api/files","GET",null,null,null).status==401,"auth gate");
            eq(request(base,"/api/pair?pin=000bad","POST",new byte[0],null,null).status==401,"bad PIN");
            Response pair=request(base,"/api/pair?pin="+server.pin(),"POST",new byte[0],null,null);
            eq(pair.status==200&&pair.cookie!=null,"valid pair");String cookie=pair.cookie.split(";",2)[0];
            byte[] bytes=new byte[1024*1024+101];new Random(2).nextBytes(bytes);
            String filename="Movie%20%5B1080p%5D%20%26%20more.mkv";
            eq(request(base,"/api/upload?name="+filename,"POST",bytes,cookie,base).status==201,"streaming upload");
            Response list=request(base,"/api/files","GET",null,cookie,null);
            eq(list.status==200&&list.body.contains("Movie [1080p] & more.mkv"),"listing and decoding");
            Response idx=request(base,"/api/library","GET",null,cookie,null);
            eq(idx.status==200&&idx.body.contains("\"count\":1")&&idx.body.contains("Movie [1080p] & more.mkv"),"library summary");
            Response dupe=request(base,"/api/duplicates?name="+filename+"&folder=&size="+bytes.length,"GET",null,cookie,null);
            eq(dupe.status==200&&dupe.body.contains("\"sameSize\":true"),"duplicate lookup");
            eq(request(base,"/api/library","GET",null,null,null).status==401,"library auth");
            Response dl=request(base,"/api/file?id="+filename,"GET",null,cookie,null);
            eq(dl.status==200&&Arrays.equals(dl.bytes,bytes),"streaming exact download");
            try(Socket sock=new Socket("127.0.0.1",p)) {
                OutputStream stream=sock.getOutputStream();
                String raw="POST /api/upload?name=test.bin HTTP/1.1\r\nHost: 127.0.0.1:"+p+"\r\nOrigin: http://malicious.example\r\nCookie: "+cookie+"\r\nContent-Length: 1\r\nConnection: close\r\n\r\nx";
                stream.write(raw.getBytes(StandardCharsets.UTF_8));stream.flush();
                byte[] answer=new byte[200];int n=sock.getInputStream().read(answer);
                String status=new String(answer,0,n,StandardCharsets.UTF_8);
                eq(status.startsWith("HTTP/1.1 403"),"origin check");
            }
            eq(request(base,"/api/delete?id="+filename,"POST",new byte[0],cookie,base).status==200,"delete");
            eq(r.data.isEmpty(),"file removed");
            eq(LanServer.cleanName("../secret.txt").equals(".._secret.txt"),"sanitize path traversal");
            eq(LanServer.cleanName("..")==null,"reject dotdot");
            System.out.println("PASS: UI, auth, pairing, 1MB+ binary upload/download, listing, origin, delete, filename sanitization");
        }
    }
}
