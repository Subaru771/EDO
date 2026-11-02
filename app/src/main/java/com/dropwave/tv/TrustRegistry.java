package com.dropwave.tv;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Stores only token hashes. A stolen cookie still grants access on unsecured HTTP networks. */
final class TrustRegistry {
    private final File file;
    private final Map<String,Record> devices=new ConcurrentHashMap<>();
    static final class Record {final String name;final long expires;Record(String n,long t){name=n;expires=t;}}
    TrustRegistry(File file){this.file=file;load();}
    static String hash(String token){try{byte[] digest=MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));return ResumeStore.hex(digest);}catch(Exception e){throw new IllegalStateException(e);}}
    private synchronized void load(){if(!file.isFile())return;try{for(String line:java.nio.file.Files.readAllLines(file.toPath(),StandardCharsets.UTF_8)){String[] p=line.split(" ",3);if(p.length==3&&p[0].matches("[0-9a-f]{64}")){long expires=Long.parseLong(p[1]);if(expires>System.currentTimeMillis())devices.put(p[0],new Record(new String(Base64.getUrlDecoder().decode(p[2]),StandardCharsets.UTF_8),expires));}}}catch(Exception ignored){}}
    private synchronized void save(){try{file.getParentFile().mkdirs();List<String> rows=new ArrayList<>();for(Map.Entry<String,Record> e:devices.entrySet())if(e.getValue().expires>System.currentTimeMillis())rows.add(e.getKey()+" "+e.getValue().expires+" "+Base64.getUrlEncoder().withoutPadding().encodeToString(e.getValue().name.getBytes(StandardCharsets.UTF_8)));java.nio.file.Files.write(file.toPath(),rows,StandardCharsets.UTF_8);}catch(Exception ignored){}}
    synchronized void add(String token,String name){String clean=name==null?"Device":name.replaceAll("[^a-zA-Z0-9 _.-]"," ").trim();if(clean.length()>40)clean=clean.substring(0,40);if(clean.isEmpty())clean="Device";devices.put(hash(token),new Record(clean,System.currentTimeMillis()+30L*24*3600_000));save();}
    boolean allows(String token){Record r=devices.get(hash(token));return r!=null&&r.expires>System.currentTimeMillis();}
    synchronized void revokeAll(){devices.clear();save();}
    synchronized String json(){StringBuilder b=new StringBuilder("{\"devices\":[");int i=0;for(Map.Entry<String,Record> e:devices.entrySet())if(e.getValue().expires>System.currentTimeMillis()){if(i++>0)b.append(',');b.append("{\"id\":").append(LanServer.json(e.getKey())).append(",\"name\":").append(LanServer.json(e.getValue().name)).append('}');}return b.append("]}").toString();}
    synchronized void revoke(String hash){devices.remove(hash);save();}
    synchronized int count(){int n=0;for(Record r:devices.values())if(r.expires>System.currentTimeMillis())n++;return n;}
}
