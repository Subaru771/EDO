package com.dropwave.tv;

import java.util.*;

/** Aggregate a TV/desktop library without reading the actual file payloads. */
public final class LibraryIndex {
    private LibraryIndex() {}
    public static String category(LanServer.Entry f) {
        String m=f.mime==null?"":f.mime.toLowerCase(Locale.ROOT);
        String n=f.name.toLowerCase(Locale.ROOT);
        if (m.startsWith("video/")||n.matches(".*\\.(mkv|avi|mov|wmv|ts)$")) return "Videos";
        if (m.startsWith("image/")||n.matches(".*\\.(heic|heif|avif)$")) return "Photos";
        if (m.startsWith("audio/")||n.matches(".*\\.(flac|opus|m4a)$")) return "Audio";
        if (n.endsWith(".apk")) return "APKs";
        if (n.matches(".*\\.(zip|7z|rar|tar|gz)$")) return "Archives";
        if (n.matches(".*\\.(pdf|doc|docx|txt|xls|xlsx|ppt|pptx)$")) return "Documents";
        return "Other";
    }
    public static String json(List<LanServer.Entry> files, long free) {
        String[] kinds={"Videos","Photos","Audio","APKs","Archives","Documents","Other"};
        Map<String,long[]> counts=new LinkedHashMap<>();for(String kind:kinds)counts.put(kind,new long[]{0,0});
        long total=0;
        List<LanServer.Entry> largest=new ArrayList<>();
        for(LanServer.Entry f:files){
            long safeSize=Math.max(0,f.size);
            long[] item=counts.get(category(f));item[0]++;item[1]+=safeSize;
            total+=safeSize;largest.add(f);
        }
        largest.sort((a,b)->Long.compare(b.size,a.size));
        StringBuilder j=new StringBuilder("{\"count\":").append(files.size()).append(",\"bytes\":").append(total)
                .append(",\"free\":").append(free).append(",\"categories\":[");
        boolean first=true;for(Map.Entry<String,long[]> e:counts.entrySet()){
            if(!first)j.append(',');first=false;
            j.append("{\"name\":").append(LanServer.json(e.getKey())).append(",\"count\":").append(e.getValue()[0])
             .append(",\"bytes\":").append(e.getValue()[1]).append('}');
        }
        j.append("],\"largest\":[");
        for(int i=0;i<Math.min(8,largest.size());i++){
            LanServer.Entry f=largest.get(i);if(i>0)j.append(',');
            j.append("{\"id\":").append(LanServer.json(f.id)).append(",\"name\":").append(LanServer.json(f.name))
             .append(",\"folder\":").append(LanServer.json(f.folder)).append(",\"size\":").append(f.size).append('}');
        }
        return j.append("]}").toString();
    }
    public static String duplicates(List<LanServer.Entry> files, String name, String folder, long size){
        StringBuilder j=new StringBuilder("{\"possibleDuplicates\":[");int i=0;
        for(LanServer.Entry f:files){
            if(f.name.equalsIgnoreCase(name)&&f.folder.equals(folder)){
                if(i++>=30)break;if(i>1)j.append(',');
                j.append("{\"id\":").append(LanServer.json(f.id)).append(",\"name\":")
                 .append(LanServer.json(f.name)).append(",\"size\":").append(f.size)
                 .append(",\"sameSize\":").append(f.size==size).append('}');
            }
        }
        return j.append("],\"note\":\"Matching names and sizes are not proof that file contents are identical.\"}").toString();
    }
}
