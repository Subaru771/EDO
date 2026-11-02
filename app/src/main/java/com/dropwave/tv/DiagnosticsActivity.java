package com.dropwave.tv;

import android.app.Activity;
import android.content.*;
import android.graphics.Color;
import android.os.Bundle;
import android.widget.*;

/** Explicitly user-shared local diagnostics. Does not include PINs, tokens or WebDAV credentials. */
public final class DiagnosticsActivity extends Activity {
    private String report(){
        LanServer server=TransferService.server;
        StringBuilder out=new StringBuilder("Edo 5.0 diagnostics\n");
        out.append("Android SDK: ").append(android.os.Build.VERSION.SDK_INT).append('\n');
        out.append("Local IP: ").append(TransferService.localIp()==null?"Unavailable":"Available").append('\n');
        out.append("Sharing: ").append(server!=null&&server.isRunning()?"Running":"Stopped").append('\n');
        if(server!=null&&server.isRunning()){
            out.append("Transport: ").append(server.isSecure()?"HTTPS":"HTTP (unencrypted)").append('\n');
            out.append("TV approval: ").append(server.requireApproval()).append('\n');
            out.append("Active transfers: ").append(server.activeTransfers()).append('\n');
            out.append("Transferred bytes: ").append(server.totalBytes()).append('\n');
        }
        try{
            AndroidFiles files=new AndroidFiles(this);out.append("Files: ").append(files.list().size()).append('\n');
            out.append("Free bytes: ").append(files.freeBytes()).append('\n');
        }catch(Exception e){out.append("Files: unavailable\n");}
        out.append("Last crash (stored locally):\n").append(CrashReporter.read(this));
        return out.toString();
    }
    @Override protected void onCreate(Bundle state){super.onCreate(state);
        LinearLayout view=new LinearLayout(this);view.setOrientation(LinearLayout.VERTICAL);view.setPadding(22,22,22,22);
        view.setBackgroundColor(Color.rgb(13,20,26));
        TextView title=new TextView(this);title.setText("Edo · Diagnostics");title.setTextColor(Color.WHITE);title.setTextSize(25);view.addView(title);
        TextView contents=new TextView(this);contents.setText(report());contents.setTextIsSelectable(true);
        contents.setTextColor(Color.rgb(198,230,220));contents.setTextSize(14);contents.setPadding(0,18,0,18);
        ScrollView scroll=new ScrollView(this);scroll.addView(contents);view.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        Button share=new Button(this);share.setText("Share diagnostic report");share.setOnClickListener(v->{
            Intent send=new Intent(Intent.ACTION_SEND);send.setType("text/plain");send.putExtra(Intent.EXTRA_TEXT,report());
            startActivity(Intent.createChooser(send,"Share Edo diagnostics"));
        });view.addView(share);
        Button close=new Button(this);close.setText("Back to Edo");close.setOnClickListener(v->finish());view.addView(close);
        setContentView(view);
    }
}
