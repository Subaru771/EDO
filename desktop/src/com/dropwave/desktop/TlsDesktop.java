package com.dropwave.desktop;

import javax.net.ssl.*;
import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.security.cert.X509Certificate;
import java.util.*;

/** Local-only, opt-in HTTPS. Generated keys stay on the computer; clients must trust the certificate. */
final class TlsDesktop {
    static SSLContext context(File stateDir,String localIp)throws Exception {
        if(!stateDir.isDirectory()&&!stateDir.mkdirs())throw new IOException("Cannot create Edo state directory");
        File keystore=new File(stateDir,"edo-local.p12"),secret=new File(stateDir,"edo-tls.secret");
        String password;
        if(secret.isFile())password=Files.readString(secret.toPath()).trim();
        else {
            byte[] random=new byte[32];new SecureRandom().nextBytes(random);
            password=Base64.getUrlEncoder().withoutPadding().encodeToString(random);
            Files.writeString(secret.toPath(),password,StandardOpenOption.CREATE_NEW);
            try{Files.setPosixFilePermissions(secret.toPath(),java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"));}catch(UnsupportedOperationException ignored){}
        }
        if(!keystore.isFile()){
            String binary=Paths.get(System.getProperty("java.home"),"bin","keytool"+(System.getProperty("os.name").toLowerCase().contains("win")?".exe":"")).toString();
            ProcessBuilder b=new ProcessBuilder(binary,"-genkeypair","-noprompt","-alias","edo","-keyalg","RSA","-keysize","3072", "-sigalg","SHA256withRSA","-validity","3650","-storetype","PKCS12","-keystore",keystore.getAbsolutePath(),"-storepass",password,"-keypass",password,"-dname","CN=Edo Local Transfer,O=Edo,C=US", "-ext","SAN=IP:"+localIp+",DNS:localhost,IP:127.0.0.1");
            b.redirectErrorStream(true);Process proc=b.start();byte[] output=proc.getInputStream().readAllBytes();int code=proc.waitFor();
            if(code!=0)throw new IOException("Failed to generate local certificate: "+new String(output));
            try{Files.setPosixFilePermissions(keystore.toPath(),java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"));}catch(UnsupportedOperationException ignored){}
        }
        KeyStore ks=KeyStore.getInstance("PKCS12");try(InputStream in=new FileInputStream(keystore)){ks.load(in,password.toCharArray());}
        KeyManagerFactory km=KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());km.init(ks,password.toCharArray());
        SSLContext ssl=SSLContext.getInstance("TLS");ssl.init(km.getKeyManagers(),null,new SecureRandom());return ssl;
    }
    static String fingerprint(File stateDir)throws Exception {
        String pwd=Files.readString(new File(stateDir,"edo-tls.secret").toPath()).trim();
        KeyStore ks=KeyStore.getInstance("PKCS12");try(InputStream in=new FileInputStream(new File(stateDir,"edo-local.p12"))){ks.load(in,pwd.toCharArray());}
        byte[] bytes=MessageDigest.getInstance("SHA-256").digest(((X509Certificate)ks.getCertificate("edo")).getEncoded());
        StringBuilder b=new StringBuilder();for(byte x:bytes)b.append(String.format("%02X:",x));return b.substring(0,b.length()-1);
    }
}
