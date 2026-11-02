package com.dropwave.tv;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Bounded persistent transfer records, with no personal addresses recorded. */
public final class TransferJournal {
    private final File file;
    private final ArrayDeque<String> entries=new ArrayDeque<>();
    public TransferJournal(File file){this.file=file;try {if(file.isFile())for(String row:java.nio.file.Files.readAllLines(file.toPath(),StandardCharsets.UTF_8))if(!row.isEmpty()){entries.addLast(row);if(entries.size()>100)entries.removeFirst();}}catch(Exception ignored){}}
    public synchronized void record(String action,String name,long bytes,boolean ok){
        String encoded=Base64.getUrlEncoder().withoutPadding().encodeToString((name==null?"":name).getBytes(StandardCharsets.UTF_8));
        entries.addLast(System.currentTimeMillis()+"\t"+action+"\t"+encoded+"\t"+bytes+"\t"+(ok?"ok":"failed"));
        while(entries.size()>100)entries.removeFirst();
        try{File p=file.getParentFile();if(p!=null)p.mkdirs();java.nio.file.Files.write(file.toPath(),entries,StandardCharsets.UTF_8);}catch(IOException ignored){}
    }
    public synchronized String json(){StringBuilder b=new StringBuilder("{\"history\":[");int count=0;
        for(Iterator<String> it=entries.descendingIterator();it.hasNext();){try{String[] p=it.next().split("\\t");if(p.length<5)continue;if(count++>0)b.append(',');String name=new String(Base64.getUrlDecoder().decode(p[2]),StandardCharsets.UTF_8);b.append("{\"time\":").append(Long.parseLong(p[0])).append(",\"action\":").append(LanServer.json(p[1])).append(",\"name\":").append(LanServer.json(name)).append(",\"bytes\":").append(Long.parseLong(p[3])).append(",\"state\":").append(LanServer.json(p[4])).append('}');}catch(Exception ignored){}}
        return b.append("]}").toString();}
}
