package com.dropwave.desktop;

import com.dropwave.tv.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import javax.net.ssl.*;
import java.util.*;

/** Trusted local test certificate. No production code disables TLS validation. */
public final class SecureDavTest{
    static void ok(boolean value,String label){if(!value)throw new AssertionError(label);}
    static String request(SSLContext client,int port,String verb,String path,String creds,byte[] body,String extra)throws Exception {
        try(SSLSocket sock=(SSLSocket)client.getSocketFactory().createSocket("127.0.0.1",port)){
            sock.setSoTimeout(12000);sock.startHandshake();
            String header=verb+" "+path+" HTTP/1.1\r\nHost: 127.0.0.1:"+port+"\r\n"
                +(creds==null?"":"Authorization: Basic "+Base64.getEncoder().encodeToString(("edo:"+creds).getBytes(StandardCharsets.UTF_8))+"\r\n")
                +(extra==null?"":extra)+"Content-Length: "+(body==null?0:body.length)+"\r\nConnection: close\r\n\r\n";
            OutputStream out=sock.getOutputStream();out.write(header.getBytes(StandardCharsets.UTF_8));if(body!=null)out.write(body);out.flush();
            byte[] bytes=sock.getInputStream().readAllBytes();return new String(bytes,StandardCharsets.UTF_8);
        }
    }
    public static void main(String[] args)throws Exception{
        Path root=Files.createTempDirectory("edo-dav-files"),state=Files.createTempDirectory("edo-dav-state"),cert=Files.createTempDirectory("edo-dav-cert");
        try{
            SSLContext server=TlsDesktop.context(cert.toFile(),"127.0.0.1");
            String password=Files.readString(cert.resolve("edo-tls.secret")).trim();
            KeyStore keys=KeyStore.getInstance("PKCS12");try(InputStream in=Files.newInputStream(cert.resolve("edo-local.p12"))){keys.load(in,password.toCharArray());}
            KeyStore trust=KeyStore.getInstance(KeyStore.getDefaultType());trust.load(null);trust.setCertificateEntry("edo",keys.getCertificate("edo"));
            TrustManagerFactory factory=TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());factory.init(trust);
            SSLContext client=SSLContext.getInstance("TLS");client.init(null,factory.getTrustManagers(),new SecureRandom());
            try(LanServer dav=new LanServer(new DiskRepository(root.toFile()),()->new ByteArrayInputStream("edo".getBytes()),state.toFile())){
                int port=dav.startSecure(21953,server);String secret=dav.webdavPassword();
                ok(dav.isSecure(),"HTTPS enabled");
                String noAuth=request(client,port,"PROPFIND","/dav/",null,null,"Depth: 0\r\n");
                ok(noAuth.startsWith("HTTP/1.1 401"),"No anonymous DAV access");
                String listing=request(client,port,"PROPFIND","/dav/",secret,null,"Depth: 1\r\n");
                ok(listing.startsWith("HTTP/1.1 207")&&listing.contains("multistatus"),"DAV listing");
                String folder=request(client,port,"MKCOL","/dav/Movies",secret,null,null);
                ok(folder.startsWith("HTTP/1.1 201"),"DAV folder creation");
                String put=request(client,port,"PUT","/dav/Movies/clip.txt",secret,"File from DAV".getBytes(StandardCharsets.UTF_8),null);
                ok(put.startsWith("HTTP/1.1 201"),"DAV upload");
                String fetched=request(client,port,"GET","/dav/Movies/clip.txt",secret,null,null);
                ok(fetched.contains("File from DAV"),"DAV download");
                String overwrite=request(client,port,"PUT","/dav/Movies/clip.txt",secret,"no".getBytes(StandardCharsets.UTF_8),null);
                ok(overwrite.startsWith("HTTP/1.1 409"),"DAV overwrite rejection");
                String move=request(client,port,"MOVE","/dav/Movies/clip.txt",secret,null,"Destination: https://127.0.0.1:"+port+"/dav/Movies/renamed.txt\r\n");
                ok(move.startsWith("HTTP/1.1 201"),"DAV rename");
                ok(Files.readString(root.resolve("Movies/renamed.txt")).equals("File from DAV"),"DAV moved data");
                Files.createDirectories(root.resolve("Other"));
                Files.writeString(root.resolve("Other/renamed.txt"),"Keep existing destination file");
                String crossFolder=request(client,port,"MOVE","/dav/Movies/renamed.txt",secret,null,
                    "Destination: https://127.0.0.1:"+port+"/dav/Other/final.txt\r\n");
                ok(crossFolder.startsWith("HTTP/1.1 201"),"Move and rename ignores unrelated old-name collision");
                ok(Files.readString(root.resolve("Other/final.txt")).equals("File from DAV"),"Final destination has source bytes");
                ok(Files.readString(root.resolve("Other/renamed.txt")).equals("Keep existing destination file"),"Old-name collision remains unchanged");
                ok(!Files.exists(root.resolve("Movies/renamed.txt")),"Source removed after move");
                Files.writeString(root.resolve("Movies/source.txt"),"Preserve source on conflict");
                String conflict=request(client,port,"MOVE","/dav/Movies/source.txt",secret,null,
                    "Destination: /dav/Other/final.txt\r\n");
                ok(conflict.startsWith("HTTP/1.1 409"),"Move cannot overwrite final destination");
                ok(Files.readString(root.resolve("Movies/source.txt")).equals("Preserve source on conflict"),"Rejected move preserves source");
                String deleted=request(client,port,"DELETE","/dav/Other/final.txt",secret,null,null);
                ok(deleted.startsWith("HTTP/1.1 204"),"DAV delete");
                ok(!Files.exists(root.resolve("Other/final.txt")),"DAV removed file");
                System.out.println("PASS: Real TLS handshake with trusted cert + authenticated WebDAV PROPFIND/MKCOL/PUT/GET/MOVE/DELETE, rejection of overwrite");
            }
        }finally{
            for(Path dir:new Path[]{root,state,cert})try(java.util.stream.Stream<Path> walk=Files.walk(dir)){
                walk.sorted(Comparator.reverseOrder()).forEach(p->{try{Files.deleteIfExists(p);}catch(Exception ignored){}});
            }
        }
    }
}
