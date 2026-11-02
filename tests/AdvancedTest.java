import com.dropwave.tv.*;
import com.dropwave.desktop.DiskRepository;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.regex.*;
import java.util.zip.*;

public class AdvancedTest {
    static final class Response{int code;byte[] bytes;String cookie,range;String body(){return new String(bytes,StandardCharsets.UTF_8);} }
    static Response call(String base,String uri,String method,byte[] payload,String cookie,String range)throws Exception {
        HttpURLConnection c=(HttpURLConnection)new URL(base+uri).openConnection();c.setRequestMethod(method);c.setConnectTimeout(3000);c.setReadTimeout(6000);
        if(cookie!=null)c.setRequestProperty("Cookie",cookie);if(range!=null)c.setRequestProperty("Range",range);
        if(payload!=null){c.setDoOutput(true);c.setFixedLengthStreamingMode(payload.length);try(OutputStream out=c.getOutputStream()){out.write(payload);}}
        Response r=new Response();r.code=c.getResponseCode();r.cookie=c.getHeaderField("Set-Cookie");r.range=c.getHeaderField("Content-Range");
        InputStream i=r.code>=400?c.getErrorStream():c.getInputStream();try(ByteArrayOutputStream out=new ByteArrayOutputStream()){
            if(i!=null)try(InputStream in=i){byte[] b=new byte[32768];int n;while((n=in.read(b))!=-1)out.write(b,0,n);}r.bytes=out.toByteArray();
        }c.disconnect();return r;
    }
    static String val(String json,String name){Matcher m=Pattern.compile("\\\""+name+"\\\"\\s*:\\s*(?:\\\"([^\\\"]+)\\\"|(\\d+))").matcher(json);if(!m.find())throw new AssertionError("Missing "+name+": "+json);return m.group(1)!=null?m.group(1):m.group(2);}
    static void ok(boolean yes,String msg){if(!yes)throw new AssertionError(msg);}
    static String enc(String text){return URLEncoder.encode(text,StandardCharsets.UTF_8);}
    public static void main(String[] args)throws Exception{
        Path share=Files.createTempDirectory("dropwave-test-files"),state=Files.createTempDirectory("dropwave-test-state");
        byte[] source=new byte[6*1024*1024+719];new Random(113).nextBytes(source);
        String sid;
        try(LanServer s=new LanServer(new DiskRepository(share.toFile()),()->new ByteArrayInputStream("DropWave".getBytes(StandardCharsets.UTF_8)),state.toFile())){
            String base="http://127.0.0.1:"+s.start(19219);
            Response pair=call(base,"/api/pair?pin="+s.pin()+"&remember=1&device=TestPC","POST",new byte[0],null,null);
            ok(pair.code==200,"pair");sid=pair.cookie.split(";",2)[0];
            ok(call(base,"/api/folders","GET",null,sid,null).code==200,"folder list");
            ok(call(base,"/api/mkdir?folder=Photos%2F2026","POST",new byte[0],sid,null).code==200,"mkdir");
            ok(call(base,"/api/upload/init?name=record.bin&folder=Photos%2F2026&size="+source.length,"POST",new byte[0],sid,null).code==200,"init");
            Response init=call(base,"/api/upload/init?name=record.bin&folder=Photos%2F2026&size="+source.length,"POST",new byte[0],sid,null);String id=val(init.body(),"id");
            byte[] first=Arrays.copyOfRange(source,0,4*1024*1024);
            ok(call(base,"/api/upload/chunk?id="+id+"&offset=0","PUT",first,sid,null).code==200,"first chunk");
            ok(call(base,"/api/upload/chunk?id="+id+"&offset=0","PUT",new byte[]{1},sid,null).code==409,"reject offset mismatch");
            ok(call(base,"/api/upload/status?id="+id,"GET",null,sid,null).body().contains("4194304"),"offset status");
            byte[] second=Arrays.copyOfRange(source,first.length,source.length);
            ok(call(base,"/api/upload/chunk?id="+id+"&offset="+first.length,"PUT",second,sid,null).code==200,"second chunk");
            ok(call(base,"/api/upload/finish?id="+id+"&sha256="+HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(source)),"POST",new byte[0],sid,null).code==201,"checksum finish");
            Response list=call(base,"/api/files","GET",null,sid,null);String fileid=val(list.body(),"id");ok(list.body().contains("Photos/2026"),"folder in listing");
            Response range=call(base,"/api/file?id="+enc(fileid),"GET",null,sid,"bytes=100-200");ok(range.code==206&&range.bytes.length==101,"range code and length");ok(Arrays.equals(Arrays.copyOfRange(source,100,201),range.bytes),"range contents");
            ok(call(base,"/api/file?id="+enc(fileid),"GET",null,sid,"bytes=999999999-1000000000").code==416,"bad range");
            Response download=call(base,"/api/file?id="+enc(fileid),"GET",null,sid,null);ok(Arrays.equals(download.bytes,source),"entire download");
            Response zip=call(base,"/api/zip?ids="+enc(fileid),"GET",null,sid,null);ok(zip.code==200,"zip");try(ZipInputStream in=new ZipInputStream(new ByteArrayInputStream(zip.bytes))){ZipEntry ze=in.getNextEntry();ok(ze!=null&&ze.getName().equals("record.bin"),"zip item");ByteArrayOutputStream o=new ByteArrayOutputStream();in.transferTo(o);ok(Arrays.equals(o.toByteArray(),source),"zip data");}
            ok(call(base,"/api/clipboard","POST","hello ✓".getBytes(StandardCharsets.UTF_8),sid,null).code==200,"clipboard write");
            ok(call(base,"/api/clipboard","GET",null,sid,null).body().contains("hello ✓"),"clipboard read");
            ok(call(base,"/api/rename?id="+enc(fileid)+"&name=renamed.bin","POST",new byte[0],sid,null).code==200,"rename");
            ok(call(base,"/api/move?id="+enc(fileid)+"&folder=","POST",new byte[0],sid,null).code==404,"stale ID does not move");
            ok(call(base,"/api/history","GET",null,sid,null).body().contains("upload"),"history");
            ok(call(base,"/api/devices","GET",null,sid,null).body().contains("TestPC"),"remembered device");
            s.setRequireApproval(true);Response waiting=call(base,"/api/pair?pin="+s.pin()+"&device=Phone","POST",new byte[0],null,null);ok(waiting.code==202,"needs approval");
            String req=val(waiting.body(),"id");ok(s.pendingRequests().size()==1,"visible to TV");s.decide(req,true);
            ok(call(base,"/api/pair/status?id="+enc(req),"GET",null,null,null).code==200,"approval completed");
            System.out.println("PASS: resume chunks, offset rejection, SHA-256, nested folders, range, ZIP, clipboard, history, trusted pairing, TV approval");
        }finally{
            try(java.util.stream.Stream<Path> walk=Files.walk(share)){walk.sorted(Comparator.reverseOrder()).forEach(p->{try{Files.deleteIfExists(p);}catch(Exception ignored){}});}
            try(java.util.stream.Stream<Path> walk=Files.walk(state)){walk.sorted(Comparator.reverseOrder()).forEach(p->{try{Files.deleteIfExists(p);}catch(Exception ignored){}});}
        }
    }
}
