package com.dropwave.desktop;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;
import java.security.*;

/** Opt-in one-way, non-destructive scheduled backup of a chosen desktop folder to DropWave TV. */
final class SyncClient {
    private final URL base;
    private final String pin;
    private final File source;
    private final String destination;
    private String cookie;
    private final Properties seen=new Properties();
    private final File manifest;
    interface Log {void write(String message);}
    private final Log log;
    private final Set<String> made=new HashSet<>();
    SyncClient(String address,String pin,File source,String destination,Log log)throws Exception {
        if(!address.matches("https?://[^/]+/?"))throw new IllegalArgumentException("Enter the server address as http://192.168.x.x:8765");
        base=new URL(address.replaceAll("/$",""));
        if(!base.getProtocol().equals("http")&&!base.getProtocol().equals("https"))throw new IllegalArgumentException("Unsupported URL scheme");
        String host=base.getHost();InetAddress ip=InetAddress.getByName(host);
        if(!(ip.isSiteLocalAddress()||ip.isLoopbackAddress()||ip.isLinkLocalAddress()))throw new IllegalArgumentException("Local network address required");
        if(!pin.matches("[0-9]{6}"))throw new IllegalArgumentException("Enter the six-digit pairing PIN");
        if(!source.isDirectory())throw new IOException("Choose a local backup source folder");
        if(!com.dropwave.tv.LanServer.validFolder(destination)||destination.isEmpty())throw new IllegalArgumentException("Invalid destination folder");
        this.pin=pin;this.source=source.getCanonicalFile();this.destination=destination;this.log=log;
        String key=Integer.toHexString((this.source.toString()+base.toString()+destination).hashCode());
        File save=new File(System.getProperty("user.home"),".dropwave-desktop");save.mkdirs();manifest=new File(save,"backup-"+key+".properties");
        if(manifest.exists())try(InputStream in=new FileInputStream(manifest)){seen.load(in);}
    }
    private static String enc(String text){return URLEncoder.encode(text,StandardCharsets.UTF_8);}
    private static String val(String json,String key){Matcher m=Pattern.compile("\\\""+key+"\\\"\\s*:\\s*(?:\\\"([^\\\"]*)\\\"|([0-9]+))").matcher(json);if(!m.find())throw new IllegalArgumentException("Unexpected server response: "+json);return m.group(1)!=null?m.group(1):m.group(2);}
    private static class Result {int code;String body,cookie;}
    private Result req(String path,String method,byte[] data)throws Exception {
        URL url=new URL(base.toString()+path);HttpURLConnection c=(HttpURLConnection)url.openConnection();c.setInstanceFollowRedirects(false);c.setRequestMethod(method);c.setConnectTimeout(6000);c.setReadTimeout(120000);if(cookie!=null)c.setRequestProperty("Cookie",cookie);
        if(data!=null){c.setDoOutput(true);c.setFixedLengthStreamingMode(data.length);try(OutputStream o=c.getOutputStream()){o.write(data);}}
        Result r=new Result();r.code=c.getResponseCode();r.cookie=c.getHeaderField("Set-Cookie");
        InputStream stream=r.code>=400?c.getErrorStream():c.getInputStream();try(InputStream in=stream){r.body=in==null?"":new String(in.readAllBytes(),StandardCharsets.UTF_8);}c.disconnect();
        if(r.code>=400)throw new IOException("Server "+r.code+": "+r.body);return r;
    }
    void useSession(String rawCookie){this.cookie=rawCookie;}
    private void connect()throws Exception {
        if(cookie!=null){try{req("/api/status","GET",null);return;}catch(IOException e){cookie=null;}}
        Result r=req("/api/pair?pin="+enc(pin)+"&device="+enc("Desktop backup")+"&remember=0","POST",new byte[0]);
        if(r.code==202){log.write("Approve the backup device on your TV/desktop hub…");String id=val(r.body,"id");
            for(int i=0;i<40&&r.code==202;i++){Thread.sleep(3000);r=req("/api/pair/status?id="+enc(id),"GET",null);}if(r.code!=200)throw new IOException("Approval timed out");
        }
        if(r.code!=200||r.cookie==null)throw new IOException("Unable to pair with hub");cookie=r.cookie.split(";",2)[0];
    }
    private void mkdir(String folder)throws Exception{if(made.add(folder))req("/api/mkdir?folder="+enc(folder),"POST",new byte[0]);}
    private void upload(Path path,String rel)throws Exception {
        String folder=destination;int slash=rel.lastIndexOf('/');if(slash>=0)folder+="/"+rel.substring(0,slash);
        mkdir(folder);long size=Files.size(path);String name=path.getFileName().toString();
        Result start=req("/api/upload/init?name="+enc(name)+"&folder="+enc(folder)+"&size="+size,"POST",new byte[0]);
        String id=val(start.body,"id");long offset=Long.parseLong(val(start.body,"offset"));
        try(RandomAccessFile input=new RandomAccessFile(path.toFile(),"r")){
            byte[] buf=new byte[4*1024*1024];while(offset<size){int count=(int)Math.min(buf.length,size-offset);input.seek(offset);input.readFully(buf,0,count);
                req("/api/upload/chunk?id="+enc(id)+"&offset="+offset,"PUT",count==buf.length?buf:Arrays.copyOf(buf,count));offset+=count;
            }
        }
        req("/api/upload/finish?id="+enc(id),"POST",new byte[0]);
    }
    synchronized int run(boolean force)throws Exception{return run(force,Collections.emptySet());}
    synchronized int run(boolean force,Set<String> alreadyOnRemote)throws Exception{
        connect();log.write("Backup starting: "+source.getName()+" → "+destination);
        int pushed=0,skipped=0;Path root=source.toPath();
        try(java.util.stream.Stream<Path> paths=Files.walk(root,12)){
            for(Path p:(Iterable<Path>)paths.filter(q->Files.isRegularFile(q,LinkOption.NOFOLLOW_LINKS)).limit(2000)::iterator){
                String rel=root.relativize(p).toString().replace(File.separatorChar,'/');
                if(rel.startsWith(".")||rel.contains("/."))continue;
                String stamp=Files.size(p)+":"+Files.getLastModifiedTime(p).toMillis();
                if(alreadyOnRemote.contains(rel)){seen.setProperty(rel,stamp);skipped++;continue;}
                if(!force&&stamp.equals(seen.getProperty(rel))){skipped++;continue;}
                upload(p,rel);seen.setProperty(rel,stamp);pushed++;
                log.write("Saved: "+rel);try(OutputStream out=new FileOutputStream(manifest)){seen.store(out,"DropWave successful backups; never auto-delete");}
            }
        }
        try(OutputStream out=new FileOutputStream(manifest)){seen.store(out,"Edo successful backups; never auto-delete");}
        log.write("Backup complete: "+pushed+" uploaded, "+skipped+" unchanged or peer-owned");return pushed;
    }
}
