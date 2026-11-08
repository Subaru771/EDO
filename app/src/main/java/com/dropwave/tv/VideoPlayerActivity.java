package com.dropwave.tv;

import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.net.Uri;
import android.os.*;
import android.provider.MediaStore;
import android.view.*;
import android.widget.*;
import java.util.*;

/** Simple in-app media playback; for advanced codecs/subtitles use the external player action. */
public final class VideoPlayerActivity extends Activity {
    private VideoView player;private String fileId;private String title;private Uri uri;
    private int dp(float n){return (int)(n*getResources().getDisplayMetrics().density+0.5f);}
    @Override public void onCreate(Bundle b){super.onCreate(b);fileId=getIntent().getStringExtra("fileId");
        if(fileId==null||!fileId.startsWith("m")){finish();return;}
        LanServer.Entry selected=null;for(LanServer.Entry e:new AndroidFiles(this).list())if(e.id.equals(fileId)){selected=e;break;}
        if(selected==null||selected.mime==null||!selected.mime.startsWith("video/")){finish();return;}
        title=selected.name;
        try{uri=android.content.ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI,Long.parseLong(fileId.substring(1)));}
        catch(Exception e){finish();return;}
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        LinearLayout root=new LinearLayout(this);root.setOrientation(1);root.setBackgroundColor(Color.BLACK);
        TextView header=new TextView(this);header.setText("edo.  /  "+title);header.setTextColor(Color.rgb(242,246,247));header.setTextSize(14);header.setSingleLine(true);header.setEllipsize(android.text.TextUtils.TruncateAt.END);
        header.setPadding(dp(16),dp(7),dp(16),dp(7));root.addView(header);
        player=new VideoView(this);root.addView(player,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout actions=new LinearLayout(this);actions.setGravity(Gravity.CENTER);actions.setPadding(dp(8),dp(4),dp(8),dp(5));actions.setBackgroundColor(Color.rgb(25,33,39));root.addView(actions);
        Button back=new Button(this);back.setText("Close");back.setAllCaps(false);back.setTextSize(14);back.setTextColor(Color.rgb(242,246,247));back.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(36,48,57)));back.setOnClickListener(v->finish());actions.addView(back);
        Button external=new Button(this);external.setText("Other player");external.setAllCaps(false);external.setTextSize(14);external.setTextColor(Color.rgb(12,36,24));external.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(163,229,192)));external.setOnClickListener(v->openOther());actions.addView(external);
        setContentView(root);
        MediaController controls=new MediaController(this);controls.setAnchorView(player);player.setMediaController(controls);
        player.setVideoURI(uri);player.setOnPreparedListener(mp->{
            int last=getPreferences(0).getInt("seek_"+fileId,0);if(last>0)player.seekTo(last);
            player.start();controls.show(5000);
        });
        player.setOnCompletionListener(mp->getPreferences(0).edit().remove("seek_"+fileId).apply());
        player.setOnErrorListener((mp,what,extra)->{
            new AlertDialog.Builder(this).setTitle("Unsupported video or audio codec")
                .setMessage("The TV's built-in decoder cannot play this file. Try an installed player such as VLC.")
                .setPositiveButton("Open other player",(d,w)->openOther()).setNegativeButton("Close",(d,w)->finish()).show();return true;
        });
    }
    private void openOther(){try{Intent i=new Intent(Intent.ACTION_VIEW);i.setDataAndType(uri,"video/*");
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);startActivity(Intent.createChooser(i,"Open in video player"));}
        catch(Exception e){Toast.makeText(this,"No compatible video player installed",Toast.LENGTH_LONG).show();}}
    @Override protected void onPause(){if(player!=null&&player.isPlaying()){
        getPreferences(0).edit().putInt("seek_"+fileId,player.getCurrentPosition()).apply();player.pause();}super.onPause();}
}
