package com.dropwave.tv;

import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.util.*;

/** Native Android Share Sheet entry; copied files persist beyond the temporary URI permission. */
public final class ShareReceiverActivity extends Activity {
    private TextView message;
    private int dp(float n){return (int)(n*getResources().getDisplayMetrics().density+0.5f);}
    private final Handler main=new Handler(Looper.getMainLooper());
    @Override public void onCreate(Bundle state){super.onCreate(state);getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        LinearLayout root=new LinearLayout(this);root.setOrientation(1);root.setPadding(dp(24),dp(22),dp(24),dp(22));root.setBackgroundColor(Color.rgb(16,21,26));
        getWindow().setStatusBarColor(Color.rgb(16,21,26));getWindow().setNavigationBarColor(Color.rgb(16,21,26));
        TextView brand=new TextView(this);brand.setText("edo.  /  Import files");brand.setTextSize(25);brand.setTextColor(Color.rgb(242,246,247));root.addView(brand);
        message=new TextView(this);message.setText("Preparing incoming files…");message.setTextSize(15);message.setTextColor(Color.rgb(172,190,199));message.setPadding(0,dp(14),0,dp(14));root.addView(message);
        setContentView(root);
        Intent intent=getIntent();List<Uri> received=new ArrayList<>();
        if(Intent.ACTION_SEND_MULTIPLE.equals(intent.getAction())){
            ArrayList<Uri> u=intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);if(u!=null)received.addAll(u);
        }else if(Intent.ACTION_SEND.equals(intent.getAction())){
            Uri uri=intent.getParcelableExtra(Intent.EXTRA_STREAM);if(uri!=null)received.add(uri);
        }
        if(received.isEmpty()&&intent.getClipData()!=null){
            for(int i=0;i<intent.getClipData().getItemCount();i++){
                Uri u=intent.getClipData().getItemAt(i).getUri();if(u!=null)received.add(u);
            }
        }
        if(received.isEmpty()){message.setText("No files provided by the sharing app.");return;}
        if(received.size()>100){message.setText("Select at most 100 files per share.");return;}
        new Thread(()->{
            int ok=0;String error="";AndroidFiles files=new AndroidFiles(this);
            for(Uri uri:received){try{
                final int current=ok+1;main.post(()->message.setText("Importing file "+current+" / "+received.size()+"…"));
                files.importIncoming(uri,"");ok++;
            }catch(Exception ex){error=ex.getMessage()==null?ex.toString():ex.getMessage();}}
            final int complete=ok;final String failures=error;
            main.post(()->{
                message.setText(complete+" / "+received.size()+" files saved to Edo Downloads."+(failures.isEmpty()?"":"\nLast issue: "+failures));
                Button done=new Button(this);done.setText("Open Edo");done.setAllCaps(false);done.setTextSize(15);done.setTextColor(Color.rgb(12,36,24));done.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(163,229,192)));done.setOnClickListener(v->{startActivity(new Intent(this,MainActivity.class));finish();});
                ((LinearLayout)message.getParent()).addView(done);
            });
        },"edo-share-import").start();
    }
}
