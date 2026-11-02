package com.dropwave.tv;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** Minimal standards-oriented WebDAV Level 1 resource management for Edo's managed files.
 * Only available over TLS and only with a separate, random Basic secret.
 */
final class WebDav {
    private WebDav(){}
    static boolean authorized(String header,String password){
        if(header==null||!header.regionMatches(true,0,"Basic ",0,6))return false;
        try{
            String raw=new String(Base64.getDecoder().decode(header.substring(6).trim()),StandardCharsets.UTF_8);
            String expected="edo:"+password;
            return MessageDigest.isEqual(raw.getBytes(StandardCharsets.UTF_8),expected.getBytes(StandardCharsets.UTF_8));
        }catch(Exception e){return false;}
    }
    private static String path(String raw)throws Exception {
        if(!raw.equals("/dav")&&!raw.startsWith("/dav/"))throw new FileNotFoundException();
        String encoded=raw.equals("/dav")?"":raw.substring(5);
        if(encoded.endsWith("/"))encoded=encoded.substring(0,encoded.length()-1);
        String decoded=URLDecoder.decode(encoded.replace("+","%2B"),"UTF-8");
        if(!LanServer.validFolder(decoded))throw new IllegalArgumentException("Unsafe WebDAV path");
        return decoded;
    }
    private static String encode(String path)throws Exception{
        StringBuilder b=new StringBuilder("/dav/");
        for(String part:path.split("/",-1))if(!part.isEmpty())b.append(URLEncoder.encode(part,"UTF-8").replace("+","%20")).append('/');
        if(!path.isEmpty())b.setLength(b.length()-1);
        return b.toString();
    }
    private static String xml(String raw){return raw.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;").replace("'","&apos;");}
    private static String parent(String path){int i=path.lastIndexOf('/');return i<0?"":path.substring(0,i);}
    private static String leaf(String path){int i=path.lastIndexOf('/');return i<0?path:path.substring(i+1);}
    private static LanServer.Entry file(LanServer.Repository repo,String path)throws Exception {
        for(LanServer.Entry e:repo.list()){
            String real=(e.folder.isEmpty()?"":e.folder+"/")+e.name;
            if(real.equals(path))return e;
        }return null;
    }
    private static boolean directory(LanServer.Repository repo,String path)throws Exception {
        if(path.isEmpty())return true;
        for(String f:repo.folders())if(f.equals(path)||f.startsWith(path+"/"))return true;
        for(LanServer.Entry e:repo.list())if(e.folder.equals(path)||e.folder.startsWith(path+"/"))return true;
        return false;
    }
    private static void write(OutputStream out,int status,String reason,String mime,long length,String extra)throws IOException {
        String head="HTTP/1.1 "+status+" "+reason+"\r\nContent-Length: "+length+"\r\nContent-Type: "+mime+"\r\nConnection: close\r\nCache-Control: no-store\r\n"
            +"DAV: 1\r\nX-Content-Type-Options: nosniff\r\n"+(extra==null?"":extra)+"\r\n";
        out.write(head.getBytes(StandardCharsets.UTF_8));out.flush();
    }
    private static void simple(OutputStream out,int status,String reason,String text)throws IOException {
        byte[] body=text.getBytes(StandardCharsets.UTF_8);write(out,status,reason,"text/plain; charset=utf-8",body.length,null);out.write(body);out.flush();
    }
    private static String response(String path,LanServer.Entry file,boolean isDir)throws Exception {
        String href=encode(path)+(isDir?"/":"");
        String display=path.isEmpty()?"Edo":leaf(path);
        StringBuilder b=new StringBuilder("<d:response><d:href>").append(xml(href)).append("</d:href><d:propstat><d:prop><d:displayname>")
            .append(xml(display)).append("</d:displayname><d:resourcetype>");
        if(isDir)b.append("<d:collection/>");
        b.append("</d:resourcetype><d:getcontentlength>").append(isDir?0:file.size).append("</d:getcontentlength>")
            .append("<d:getcontenttype>").append(isDir?"httpd/unix-directory":xml(file.mime==null?"application/octet-stream":file.mime))
            .append("</d:getcontenttype></d:prop><d:status>HTTP/1.1 200 OK</d:status></d:propstat></d:response>");
        return b.toString();
    }
    private static String destinationPath(String value,String expectedHost)throws Exception{
        if(value==null)throw new IllegalArgumentException("Destination header required");
        URI uri=URI.create(value);
        if(uri.isAbsolute()){
            if(uri.getRawAuthority()==null||expectedHost==null||!uri.getRawAuthority().equalsIgnoreCase(expectedHost))
                throw new IllegalArgumentException("Cross-host WebDAV move is prohibited");
            return path(uri.getRawPath());
        }
        return path(uri.getRawPath());
    }
    static void handle(LanServer.Repository repo,String method,String rawPath,Map<String,String> headers,InputStream input,OutputStream output)throws Exception {
        String name=path(rawPath);
        LanServer.Entry entry=file(repo,name);boolean dir=directory(repo,name);
        if(method.equals("OPTIONS")){write(output,200,"OK","text/plain",0,
            "Allow: OPTIONS, PROPFIND, GET, HEAD, PUT, MKCOL, DELETE, MOVE, COPY\r\n");return;}
        if(method.equals("PROPFIND")){
            if(entry==null&&!dir){simple(output,404,"Not Found","Not found");return;}
            String depth=headers.getOrDefault("depth","1");
            if(!depth.equals("0")&&!depth.equals("1")){simple(output,403,"Forbidden","Only Depth: 0 or 1 is supported");return;}
            StringBuilder xml=new StringBuilder("<?xml version=\"1.0\" encoding=\"utf-8\"?><d:multistatus xmlns:d=\"DAV:\">");
            xml.append(response(name,entry,entry==null));
            if(entry==null&&depth.equals("1")){
                Set<String> childDirs=new TreeSet<>();
                for(String folder:repo.folders()){
                    if(folder.startsWith(name.isEmpty()?"":name+"/")){
                        String sub=folder.substring(name.isEmpty()?0:name.length()+1).split("/",2)[0];
                        if(!sub.isEmpty())childDirs.add(name.isEmpty()?sub:name+"/"+sub);
                    }
                }
                for(LanServer.Entry e:repo.list()){
                    if(e.folder.startsWith(name.isEmpty()?"":name+"/" )&&!e.folder.equals(name)){
                        String sub=e.folder.substring(name.isEmpty()?0:name.length()+1).split("/",2)[0];
                        if(!sub.isEmpty())childDirs.add(name.isEmpty()?sub:name+"/"+sub);
                    }
                }
                for(String child:childDirs)xml.append(response(child,null,true));
                for(LanServer.Entry e:repo.list())if(e.folder.equals(name))xml.append(response((name.isEmpty()?"":name+"/")+e.name,e,false));
            }
            xml.append("</d:multistatus>");byte[] b=xml.toString().getBytes(StandardCharsets.UTF_8);
            write(output,207,"Multi-Status","application/xml; charset=utf-8",b.length,null);output.write(b);output.flush();return;
        }
        if(method.equals("GET")||method.equals("HEAD")){
            if(entry==null){simple(output,404,"Not Found","File not found");return;}
            try(LanServer.Opened opened=repo.open(entry.id)){
                write(output,200,"OK",opened.mime,opened.length,"Accept-Ranges: bytes\r\n");
                if(method.equals("GET"))LanServer.copyExactly(opened.stream,output,opened.length);
                output.flush();
            }return;
        }
        if(method.equals("MKCOL")){
            if(name.isEmpty()||entry!=null||dir){simple(output,405,"Method Not Allowed","Folder already exists");return;}
            if(!directory(repo,parent(name))){simple(output,409,"Conflict","Create parent folder first");return;}
            repo.mkdir(name);simple(output,201,"Created","");return;
        }
        if(method.equals("PUT")){
            String filename=leaf(name),folder=parent(name);
            if(filename.isEmpty()||!filename.equals(LanServer.cleanName(filename))||!directory(repo,folder)){
                simple(output,409,"Conflict","Destination folder is unavailable");return;
            }
            if(entry!=null||dir){simple(output,409,"Conflict","Edo will not overwrite existing files; choose a new filename");return;}
            if(headers.containsKey("transfer-encoding")){simple(output,411,"Length Required","Chunked requests are unsupported");return;}
            long length;try{length=Long.parseLong(headers.getOrDefault("content-length","-1"));}catch(Exception e){length=-1;}
            if(length<0||length>100L*1024*1024*1024){simple(output,413,"Payload Too Large","Invalid upload size");return;}
            long free=repo.freeBytes();if(free>=0&&free<length+16L*1024*1024){simple(output,507,"Insufficient Storage","Not enough space");return;}
            repo.uploadTo(filename,folder,input,length);simple(output,201,"Created","");return;
        }
        if(method.equals("DELETE")){
            if(name.isEmpty()){simple(output,403,"Forbidden","Cannot remove the root");return;}
            if(entry!=null)repo.remove(entry.id);
            else if(dir)repo.removeFolder(name);
            else {simple(output,404,"Not Found","Not found");return;}
            simple(output,204,"No Content","");return;
        }
        if(method.equals("MOVE")||method.equals("COPY")){
            if(entry==null){simple(output,404,"Not Found","Only files can be moved or copied");return;}
            String dest=destinationPath(headers.get("destination"),headers.get("host"));
            if(dest.isEmpty()||!directory(repo,parent(dest))){simple(output,409,"Conflict","Destination folder missing");return;}
            if(file(repo,dest)!=null||directory(repo,dest)){simple(output,409,"Conflict","Destination already exists");return;}
            if(method.equals("COPY")){
                LanServer.Entry copy=repo.copy(entry.id,parent(dest));
                if(!copy.name.equals(leaf(dest)))repo.rename(copy.id,leaf(dest));
            }else{
                if(!entry.folder.equals(parent(dest)))repo.move(entry.id,parent(dest));
                if(!entry.name.equals(leaf(dest))){
                    LanServer.Entry moved=file(repo,(parent(dest).isEmpty()?"":parent(dest)+"/")+entry.name);
                    if(moved==null)throw new FileNotFoundException("Moved file no longer accessible");
                    repo.rename(moved.id,leaf(dest));
                }
            }
            simple(output,201,"Created","");return;
        }
        simple(output,405,"Method Not Allowed","Unsupported WebDAV action");
    }
}
