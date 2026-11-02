package com.dropwave.tv;

import android.content.Context;
import java.io.*;
import java.nio.charset.StandardCharsets;

/** Private, local-only crash report; no telemetry or network uploads. */
final class CrashReporter {
    private static volatile boolean installed;
    private CrashReporter(){}
    static synchronized void install(Context context){
        if(installed)return;
        final File file=new File(context.getApplicationContext().getFilesDir(),"last-crash.txt");
        final Thread.UncaughtExceptionHandler previous=Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread,error)->{
            try{
                StringWriter out=new StringWriter();PrintWriter print=new PrintWriter(out);
                print.println("Edo process crash — "+new java.util.Date());
                print.println("Android SDK: "+android.os.Build.VERSION.SDK_INT);
                print.println("Thread: "+thread.getName());
                error.printStackTrace(print);print.flush();
                byte[] bytes=out.toString().getBytes(StandardCharsets.UTF_8);
                try(FileOutputStream stream=new FileOutputStream(file,false)){
                    stream.write(bytes,0,Math.min(bytes.length,24*1024));
                }
            }catch(Exception ignored){}
            if(previous!=null)previous.uncaughtException(thread,error);
        });
        installed=true;
    }
    static String read(Context context){
        try{
            File file=new File(context.getFilesDir(),"last-crash.txt");
            if(!file.isFile())return "No crash recorded on this device.";
            byte[] content=new byte[(int)Math.min(24*1024,file.length())];
            try(InputStream in=new FileInputStream(file)){int n=in.read(content);if(n<0)return "Empty report";return new String(content,0,n,StandardCharsets.UTF_8);}
        }catch(Exception ex){return "Crash report unavailable";}
    }
}
