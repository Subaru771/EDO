package com.dropwave.desktop;

import com.dropwave.tv.LanServer;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.*;

/** Conservative add-only merge. Conflicting remote versions are renamed locally; nothing is deleted or overwritten. */
final class TwoWaySync {
    interface Log {void write(String s);}
    private final URL base;private final String pin;private final Path local;private final String remoteFolder;private final Log log;
    private String cookie;private final Set<String> pulledNames=new HashSet<>();
    static final class RemoteFile {String id,rel;long size;RemoteFile(String id,String rel,long size){this.id=id;this.rel=rel;this.size=size;}}
    TwoWaySync(String url,String pin,File directory,String folder,Log log)throws Exception {
        if(!url.matches("https?://[^/]+/?"))throw new IllegalArgumentException("Enter a local Edo server address");
        base=new URL(url.replaceAll("/$",""));InetAddress ip=InetAddress.getByName(base.getHost());
        if(!(ip.isSiteLocalAddress()||ip.isLoopbackAddress()||ip.isLinkLocalAddress()))throw new IllegalArgumentException("LAN only");
        if(!pin.matches("[0-9]{6}"))throw new IllegalArgumentException("Enter the receiver PIN");
        if(!directory.isDirectory())throw new FileNotFoundException("Select an existing local folder");
        if(folder==null||folder.isEmpty()||!LanServer.validFolder(folder))throw new IllegalArgumentException("Invalid destination folder");
        this.pin=pin;local=directory.getCanonicalFile().toPath();remoteFolder=folder;this.log=log;
    }
    private static String enc(String value)throws Exception{return URLEncoder.encode(value,"UTF-8");}
    private static String val(String s,String key)throws IOException{
        java.util.regex.Matcher m=java.util.regex.Pattern.compile("\\\""+key+"\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"").matcher(s);
        if(!m.find())throw new IOException("Unexpected peer response");return m.group(1);
    }
    private HttpURLConnection open(String path,String method)throws Exception {
        HttpURLConnection c=(HttpURLConnection)new URL(base.toString()+path).openConnection();
        c.setInstanceFollowRedirects(false);c.setRequestMethod(method);c.setConnectTimeout(7000);c.setReadTimeout(180000);
        if(cookie!=null)c.setRequestProperty("Cookie",cookie);return c;
    }
    private String req(String path,String method)throws Exception {
        HttpURLConnection c=open(path,method);if(method.equals("POST")){c.setDoOutput(true);c.setFixedLengthStreamingMode(0);c.getOutputStream().close();}
        int code=c.getResponseCode();String saved=c.getHeaderField("Set-Cookie");if(saved!=null)cookie=saved.split(";",2)[0];
        InputStream response=code>=400?c.getErrorStream():c.getInputStream();String text="";
        if(response!=null)try(InputStream in=response){byte[] bytes=in.readNBytes(10*1024*1024+1);if(bytes.length>10*1024*1024)throw new IOException("Manifest too large");text=new String(bytes,StandardCharsets.UTF_8);}
        c.disconnect();if(code>=400)throw new IOException("Edo returned "+code+": "+text);
        return code+"\n"+text;
    }
    private void connect()throws Exception {
        String pair=req("/api/pair?pin="+enc(pin)+"&remember=0&device="+enc("Edo desktop sync"),"POST");
        int status=Integer.parseInt(pair.substring(0,pair.indexOf('\n')));
        if(status==202){String id=val(pair.substring(pair.indexOf('\n')+1),"id");
            for(int i=0;i<40;i++){Thread.sleep(2500);
                pair=req("/api/pair/status?id="+enc(id),"GET");status=Integer.parseInt(pair.substring(0,pair.indexOf('\n')));
                if(status==200)break;
            }
        }
        if(status!=200||cookie==null)throw new IOException("Pairing not approved");
    }
    private static String decode(String b)throws Exception{return new String(Base64.getUrlDecoder().decode(b),StandardCharsets.UTF_8);}
    private List<RemoteFile> index()throws Exception {
        String response=req("/api/sync/index","GET");String body=response.substring(response.indexOf('\n')+1);
        List<RemoteFile> result=new ArrayList<>();
        for(String line:body.split("\\n")){
            if(line.isEmpty())continue;String[] col=line.split("\\t",-1);if(col.length!=4)continue;
            String folder=decode(col[0]),name=decode(col[1]),id=decode(col[2]);
            if(!name.equals(LanServer.cleanName(name))||!LanServer.validFolder(folder))continue;
            if(!folder.equals(remoteFolder)&&!folder.startsWith(remoteFolder+"/"))continue;
            String rel=(folder.equals(remoteFolder)?"":folder.substring(remoteFolder.length()+1)+"/")+name;
            long size=Long.parseLong(col[3]);if(size<0||size>100L*1024*1024*1024)continue;
            if(result.size()>=10000)throw new IOException("Too many files to merge safely");
            result.add(new RemoteFile(id,rel,size));
        }
        return result;
    }
    private static byte[] hash(Path file)throws Exception{
        MessageDigest m=MessageDigest.getInstance("SHA-256");try(InputStream in=Files.newInputStream(file)){
            byte[] block=new byte[131072];int count;while((count=in.read(block))!=-1)m.update(block,0,count);
        }return m.digest();
    }
    private static String hex(byte[] hash){StringBuilder b=new StringBuilder();for(byte c:hash)b.append(String.format("%02x",c&255));return b.toString();}
    private static Path conflict(Path desired,String digest){
        String name=desired.getFileName().toString();int dot=name.lastIndexOf('.');
        String stem=dot<=0?name:name.substring(0,dot),ext=dot<=0?"":name.substring(dot);
        return desired.resolveSibling(stem+".from-edo-"+digest.substring(0,10)+ext);
    }
    int pull()throws Exception {
        connect();return pullConnected();
    }
    private int pullConnected()throws Exception{
        int fetched=0,identical=0,conflicts=0;List<RemoteFile> remote=index();
        for(RemoteFile file:remote){
            if(Thread.currentThread().isInterrupted())throw new InterruptedException("Merge interrupted");
            Path intended=local.resolve(file.rel.replace('/',File.separatorChar)).normalize();
            if(!intended.startsWith(local))throw new IOException("Peer returned an invalid path");
            Files.createDirectories(intended.getParent());
            if(!intended.getParent().toRealPath().startsWith(local))throw new IOException("Cannot write through a symbolic link");
            // Download to a separate temp file. No existing user file is ever overwritten.
            FileStore disk=Files.getFileStore(local);if(disk.getUsableSpace()<file.size+16*1024*1024)throw new IOException("Not enough disk space for "+file.rel);
            Path tmp=Files.createTempFile(intended.getParent(),".edo-merge-",".part");
            try{
                HttpURLConnection c=open("/api/file?id="+enc(file.id),"GET");
                if(c.getResponseCode()!=200)throw new IOException("Peer download failed for "+file.rel);
                try(InputStream in=c.getInputStream();OutputStream out=Files.newOutputStream(tmp)){
                    byte[] buf=new byte[131072];long n=0;int got;
                    while((got=in.read(buf))!=-1){n+=got;if(n>file.size)throw new IOException("Peer sent more bytes than expected");out.write(buf,0,got);}
                    if(n!=file.size)throw new IOException("Incomplete file: "+file.rel);
                }finally{c.disconnect();}
                byte[] remoteHash=hash(tmp);String digest=hex(remoteHash);
                Path target=intended;
                if(Files.exists(target,LinkOption.NOFOLLOW_LINKS)){
                    if(Files.isRegularFile(target,LinkOption.NOFOLLOW_LINKS)&&Arrays.equals(hash(target),remoteHash)){identical++;continue;}
                    target=conflict(intended,digest);conflicts++;
                    if(Files.exists(target,LinkOption.NOFOLLOW_LINKS)){
                        if(Files.isRegularFile(target,LinkOption.NOFOLLOW_LINKS)&&Arrays.equals(hash(target),remoteHash)){identical++;continue;}
                        int seq=2;while(Files.exists(target,LinkOption.NOFOLLOW_LINKS)){
                            target=conflict(intended,digest).resolveSibling(conflict(intended,digest).getFileName().toString()+"-"+(seq++));
                        }
                    }
                }
                Files.move(tmp,target);fetched++;pulledNames.add(local.relativize(target).toString().replace(File.separatorChar,'/'));
                log.write("Received: "+local.relativize(target));
            }finally{Files.deleteIfExists(tmp);}
        }
        log.write("Pull complete: "+fetched+" received, "+identical+" identical, "+conflicts+" conflicts preserved");
        return fetched;
    }
    int merge()throws Exception {
        connect();int pulled=pullConnected();
        // Existing upload engine is additive and never deletes remote content. This
        // is deliberately not destructive bidirectional mirroring.
        SyncClient upload=new SyncClient(base.toString(),pin,local.toFile(),remoteFolder,log::write);
        upload.useSession(cookie);int pushed=upload.run(false,pulledNames);
        log.write("Two-way safe merge: "+pulled+" pulled, "+pushed+" uploaded. No deletions or overwrites.");
        return pulled+pushed;
    }
}
