package com.dropwave.tv;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import java.util.regex.*;

/** Shared, streaming Edo-to-Edo sender. The destination must be a reachable LAN server. */
public final class PeerClient {
    public interface Progress { void update(long sent,long total); }
    private final URL base;
    private String cookie;
    private static final int CHUNK=4*1024*1024;
    public PeerClient(String address)throws Exception {
        if(!address.matches("https?://[^/]+/?"))throw new IllegalArgumentException("Use http://LAN-IP:8765 or a trusted HTTPS address");
        base=new URL(address.replaceAll("/$",""));
        InetAddress addressIp=InetAddress.getByName(base.getHost());
        if(!addressIp.isSiteLocalAddress()&&!addressIp.isLoopbackAddress()&&!addressIp.isLinkLocalAddress())
            throw new IllegalArgumentException("Edo only supports local-network recipients");
    }
    private static String encode(String value)throws Exception{return URLEncoder.encode(value,StandardCharsets.UTF_8.name());}
    private static String field(String json,String key)throws IOException {
        Matcher m=Pattern.compile("\\\""+key+"\\\"\\s*:\\s*(?:\\\"([^\\\"]+)\\\"|(\\d+))").matcher(json);
        if(!m.find())throw new IOException("Unexpected Edo response: "+json);
        return m.group(1)==null?m.group(2):m.group(1);
    }
    private static class Response{int status;String body,cookie;}
    private Response request(String path,String method,byte[] data,int len)throws Exception{
        HttpURLConnection c=(HttpURLConnection)new URL(base.toString()+path).openConnection();
        c.setInstanceFollowRedirects(false);c.setRequestMethod(method);c.setConnectTimeout(7000);c.setReadTimeout(120000);
        if(cookie!=null)c.setRequestProperty("Cookie",cookie);
        if(data!=null){c.setDoOutput(true);c.setFixedLengthStreamingMode(len);
            try(OutputStream out=c.getOutputStream()){out.write(data,0,len);}}
        Response r=new Response();r.status=c.getResponseCode();r.cookie=c.getHeaderField("Set-Cookie");
        InputStream input=r.status>=400?c.getErrorStream():c.getInputStream();
        if(input!=null)try(InputStream in=input){ByteArrayOutputStream buf=new ByteArrayOutputStream();byte[] b=new byte[4096];int n;
            while((n=in.read(b))!=-1){if(buf.size()+n>32768)throw new IOException("Server reply too long");buf.write(b,0,n);}r.body=buf.toString("UTF-8");}
        if(r.body==null)r.body="";c.disconnect();
        if(r.status>=400)throw new IOException("Destination error "+r.status+": "+r.body);
        return r;
    }
    public void pair(String pin,String deviceName)throws Exception{
        if(!pin.matches("[0-9]{6}"))throw new IllegalArgumentException("Enter the six-digit PIN from the receiver");
        Response result=request("/api/pair?pin="+encode(pin)+"&device="+encode(deviceName)+"&remember=0","POST",new byte[0],0);
        if(result.status==202){String approvalId=field(result.body,"id");
            for(int i=0;i<40;i++){
                if(Thread.currentThread().isInterrupted())throw new InterruptedException("Cancelled");
                Thread.sleep(2500);result=request("/api/pair/status?id="+encode(approvalId),"GET",null,0);
                if(result.status==200)break;
            }
        }
        if(result.status!=200||result.cookie==null)throw new IOException("Pairing not completed. Check the receiving device.");
        cookie=result.cookie.split(";",2)[0];
        request("/api/status","GET",null,0);
    }
    /** Legacy v1.x receivers accept one streamed POST rather than chunked uploads. */
    private String uploadLegacy(String name,long length,InputStream source,Progress progress)throws Exception {
        HttpURLConnection c=(HttpURLConnection)new URL(base.toString()+"/api/upload?name="+encode(name)).openConnection();
        c.setInstanceFollowRedirects(false);c.setRequestMethod("POST");
        c.setConnectTimeout(7000);c.setReadTimeout(120000);
        c.setRequestProperty("Cookie",cookie);
        c.setRequestProperty("Content-Type","application/octet-stream");
        c.setDoOutput(true);c.setFixedLengthStreamingMode(length);
        long sent=0;
        try(OutputStream out=new BufferedOutputStream(c.getOutputStream())){
            byte[] block=new byte[131072];
            while(sent<length){
                if(Thread.currentThread().isInterrupted())throw new InterruptedException("Cancelled");
                int n=source.read(block,0,(int)Math.min(block.length,length-sent));
                if(n<0)throw new EOFException("File ended unexpectedly");
                if(n==0)continue;
                out.write(block,0,n);sent+=n;
                if(progress!=null)progress.update(sent,length);
            }
        }
        int status=c.getResponseCode();
        try(InputStream body=status>=400?c.getErrorStream():c.getInputStream()){
            if(status!=201&&status!=200){
                String info=body==null?"":new String(body.readNBytes(1024),StandardCharsets.UTF_8);
                throw new IOException("Legacy receiver upload failed ("+status+"): "+info);
            }
        }finally{c.disconnect();}
        return "legacy-transfer";
    }
    public String upload(String name,String folder,long length,InputStream source,Progress progress)throws Exception{
        if(cookie==null)throw new IllegalStateException("Pair first");
        if(!name.equals(LanServer.cleanName(name))||!LanServer.validFolder(folder)||length<0)
            throw new IllegalArgumentException("Invalid file or folder");
        if(folder!=null&&!folder.isEmpty())request("/api/mkdir?folder="+encode(folder),"POST",new byte[0],0);
        Response init;
        try{init=request("/api/upload/init?name="+encode(name)+"&folder="+encode(folder)+"&size="+length,"POST",new byte[0],0);}
        catch(IOException old){
            // v1.x paired clients use POST /api/upload and have no folders/resume API.
            if(old.getMessage()!=null&&old.getMessage().startsWith("Destination error 404:")&&folder.isEmpty()){
                return uploadLegacy(name,length,source,progress);
            }
            throw old;
        }
        String id=field(init.body,"id");long sent=Long.parseLong(field(init.body,"offset"));
        if(sent!=0)throw new IOException("Unexpected destination offset for a fresh upload");
        MessageDigest digest=MessageDigest.getInstance("SHA-256");byte[] block=new byte[CHUNK];
        while(sent<length){if(Thread.currentThread().isInterrupted())throw new InterruptedException("Cancelled");
            int need=(int)Math.min(CHUNK,length-sent),n=0;
            while(n<need){int r=source.read(block,n,need-n);if(r<0)throw new EOFException("File ended unexpectedly");n+=r;}
            digest.update(block,0,n);
            request("/api/upload/chunk?id="+encode(id)+"&offset="+sent,"PUT",block,n);
            sent+=n;if(progress!=null)progress.update(sent,length);
        }
        StringBuilder hash=new StringBuilder();for(byte x:digest.digest())hash.append(String.format(Locale.US,"%02x",x&255));
        request("/api/upload/finish?id="+encode(id)+"&sha256="+hash,"POST",new byte[0],0);
        return id;
    }
}
