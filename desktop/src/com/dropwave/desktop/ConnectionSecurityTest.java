package com.dropwave.desktop;

import com.dropwave.tv.LanServer;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import javax.net.ssl.*;

/** Tests the public reachability probe, private API, TLS pairing, and schemeful write origin. */
public final class ConnectionSecurityTest {
    private static void check(boolean condition,String reason){if(!condition)throw new AssertionError(reason);}
    private static String plain(int port,String method,String path,String extra,byte[] body)throws Exception {
        try(Socket socket=new Socket("127.0.0.1",port)){
            socket.setSoTimeout(8000);
            OutputStream out=socket.getOutputStream();
            String headers=method+" "+path+" HTTP/1.1\r\nHost: 127.0.0.1:"+port+"\r\n"+
                (extra==null?"":extra)+"Content-Length: "+(body==null?0:body.length)+"\r\nConnection: close\r\n\r\n";
            out.write(headers.getBytes(StandardCharsets.UTF_8));if(body!=null)out.write(body);out.flush();
            return new String(socket.getInputStream().readAllBytes(),StandardCharsets.UTF_8);
        }
    }
    private static String cookie(String response){
        for(String line:response.split("\r\n"))if(line.toLowerCase(Locale.ROOT).startsWith("set-cookie:")){
            return line.substring(line.indexOf(':')+1).trim().split(";",2)[0];
        }
        throw new AssertionError("No pairing session cookie");
    }
    private static SSLContext clientContext(Path state)throws Exception{
        String password=Files.readString(state.resolve("edo-tls.secret")).trim();
        KeyStore source=KeyStore.getInstance("PKCS12");
        try(InputStream in=Files.newInputStream(state.resolve("edo-local.p12"))){source.load(in,password.toCharArray());}
        KeyStore trust=KeyStore.getInstance(KeyStore.getDefaultType());trust.load(null);
        trust.setCertificateEntry("edo",source.getCertificate("edo"));
        TrustManagerFactory f=TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());f.init(trust);
        SSLContext context=SSLContext.getInstance("TLS");context.init(null,f.getTrustManagers(),new SecureRandom());
        return context;
    }
    public static void main(String[] args)throws Exception{
        Path shared=Files.createTempDirectory("edo-security-files");
        Path state=Files.createTempDirectory("edo-security-state");
        Path cert=Files.createTempDirectory("edo-security-cert");
        try{
            LanServer.Repository repo=new DiskRepository(shared.toFile());
            try(LanServer server=new LanServer(repo,()->new ByteArrayInputStream("Edo".getBytes()),state.toFile())){
                int port=server.start(25761),mode=0;
                String base="http://127.0.0.1:"+port;
                String h=plain(port,"GET","/api/health",null,null);
                check(h.startsWith("HTTP/1.1 200")&&h.contains("\"transport\":\"http\""),"HTTP health identifies transport");
                check(!h.contains(server.pin())&&!h.contains("dw_session"),"Public health contains no credentials");
                check(plain(port,"GET","/api/files",null,null).startsWith("HTTP/1.1 401"),"File list stays private");
                String p=plain(port,"POST","/api/pair?pin="+server.pin(),null,new byte[0]);
                String c=cookie(p);
                String denied=plain(port,"POST","/api/clipboard","Cookie: "+c+"\r\nOrigin: https://127.0.0.1:"+port+"\r\n",new byte[0]);
                check(denied.startsWith("HTTP/1.1 403"),"HTTP does not accept HTTPS-origin write");
                String accepted=plain(port,"POST","/api/clipboard","Cookie: "+c+"\r\nOrigin: "+base+"\r\n",new byte[0]);
                check(accepted.startsWith("HTTP/1.1 200"),"HTTP same-origin write still works");
            }
            SSLContext tls=TlsDesktop.context(cert.toFile(),"127.0.0.1");
            SSLContext client=clientContext(cert);
            try(LanServer server=new LanServer(repo,()->new ByteArrayInputStream("Edo".getBytes()),state.toFile())){
                int port=server.startSecure(25766,tls);
                String health=SecureDavTest.request(client,port,"GET","/api/health",null,null,null);
                check(health.startsWith("HTTP/1.1 200")&&health.contains("\"transport\":\"https\""),"HTTPS health identifies transport");
                check(!health.contains(server.pin()),"HTTPS public health contains no PIN");
                String noSession=SecureDavTest.request(client,port,"GET","/api/files",null,null,null);
                check(noSession.startsWith("HTTP/1.1 401"),"HTTPS files require pairing");
                String paired=SecureDavTest.request(client,port,"POST","/api/pair?pin="+server.pin(),null,new byte[0],null);
                String c=cookie(paired);
                String rejected=SecureDavTest.request(client,port,"POST","/api/clipboard",null,new byte[0],"Cookie: "+c+"\r\nOrigin: http://127.0.0.1:"+port+"\r\n");
                check(rejected.startsWith("HTTP/1.1 403"),"HTTPS rejects HTTP-origin writes");
                String accepted=SecureDavTest.request(client,port,"POST","/api/clipboard",null,new byte[0],"Cookie: "+c+"\r\nOrigin: https://127.0.0.1:"+port+"\r\n");
                check(accepted.startsWith("HTTP/1.1 200"),"HTTPS same-origin write works");
            }
            System.out.println("PASS: Safe public health, PIN confidentiality, protected file APIs, strict HTTP/HTTPS origin checks, trusted TLS pairing and writes");
        }finally{
            for(Path dir:new Path[]{shared,state,cert})try(java.util.stream.Stream<Path> walk=Files.walk(dir)){
                walk.sorted(Comparator.reverseOrder()).forEach(p->{try{Files.deleteIfExists(p);}catch(Exception ignored){}});
            }
        }
    }
}
