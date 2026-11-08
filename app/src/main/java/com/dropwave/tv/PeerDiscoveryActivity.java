package com.dropwave.tv;

import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.net.nsd.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.net.*;
import java.util.*;

/** Native remote-friendly peer picker; requires the other Edo server to be on the same LAN. */
public final class PeerDiscoveryActivity extends Activity {
    private static final int BG=Color.rgb(16,21,26), PANEL=Color.rgb(25,33,39);
    private static final int BORDER=Color.rgb(52,67,75), WHITE=Color.rgb(242,246,247);
    private int dp(float v){return (int)(v*getResources().getDisplayMetrics().density+0.5f);}
    private android.graphics.drawable.GradientDrawable surface(int fill,int border){
        android.graphics.drawable.GradientDrawable d=new android.graphics.drawable.GradientDrawable();
        d.setColor(fill);d.setCornerRadius(dp(11));d.setStroke(dp(1),border);return d;
    }
    private NsdManager nsd;
    private NsdManager.DiscoveryListener discovery;
    private LinearLayout list;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Set<String> shown=new HashSet<>();
    private String fileId;
    @Override public void onCreate(Bundle b){super.onCreate(b);fileId=getIntent().getStringExtra("fileId");
        ScrollView scroll=new ScrollView(this);scroll.setBackgroundColor(BG);
        list=new LinearLayout(this);list.setOrientation(1);list.setPadding(dp(24),dp(19),dp(24),dp(24));scroll.addView(list);
        title("Nearby devices");subtitle("Send to another Edo host on the same Wi-Fi.");
        button("Enter address manually",()->manual(null));button("Refresh nearby devices",this::restart);
        setContentView(scroll);restart();
    }
    private void title(String s){TextView t=new TextView(this);t.setText(s);t.setTextColor(WHITE);t.setTextSize(26);t.setPadding(dp(4),dp(8),dp(4),dp(8));list.addView(t);}
    private void subtitle(String s){TextView t=new TextView(this);t.setText(s);t.setTextColor(Color.rgb(172,190,199));t.setTextSize(14);t.setPadding(dp(4),dp(2),dp(4),dp(13));list.addView(t);}
    private void button(String label,Runnable action){
        Button b=new Button(this);b.setText(label);b.setAllCaps(false);b.setFocusable(true);b.setTextSize(16);b.setTextColor(WHITE);
        b.setPadding(dp(13),dp(11),dp(13),dp(11));b.setBackground(surface(PANEL,BORDER));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,0,0,dp(7));list.addView(b,lp);
        b.setOnFocusChangeListener((v,focus)->{b.setBackground(surface(focus?WHITE:PANEL,focus?WHITE:BORDER));b.setTextColor(focus?BG:WHITE);});
        b.setOnClickListener(v->action.run());
    }
    private void restart(){stopDiscovery();shown.clear();try{
        nsd=(NsdManager)getSystemService(NSD_SERVICE);
        discovery=new NsdManager.DiscoveryListener(){
            public void onDiscoveryStarted(String type){}
            public void onStartDiscoveryFailed(String type,int error){handler.post(()->title("Discovery unavailable. Enter the receiver address manually."));}
            public void onStopDiscoveryFailed(String type,int error){}
            public void onDiscoveryStopped(String type){}
            public void onServiceLost(NsdServiceInfo info){}
            public void onServiceFound(NsdServiceInfo service){
                if(!service.getServiceType().toLowerCase(Locale.ROOT).contains("dropwave"))return;
                nsd.resolveService(service,new NsdManager.ResolveListener(){
                    public void onResolveFailed(NsdServiceInfo i,int error){}
                    public void onServiceResolved(NsdServiceInfo i){
                        InetAddress host=i.getHost();if(host==null)return;
                        String ip=host.getHostAddress();if(ip.contains(":"))ip="["+ip+"]";
                        String protocol="http";
                        byte[] attribute=i.getAttributes().get("scheme");
                        if(attribute!=null&&"https".equals(new String(attribute,java.nio.charset.StandardCharsets.UTF_8)))protocol="https";
                        String url=protocol+"://"+ip+":"+i.getPort();
                        if(TransferService.server!=null&&i.getPort()==TransferService.server.port()&&
                            host.getHostAddress().equals(TransferService.localIp()))return;
                        handler.post(()->{if(shown.add(url))button("Send to "+i.getServiceName()+"  ·  "+url,()->manual(url));});
                    }
                });
            }
        };
        nsd.discoverServices("_dropwave._tcp.",NsdManager.PROTOCOL_DNS_SD,discovery);
    }catch(Exception e){title("Could not start discovery: "+e.getMessage());}}
    private void manual(String detected){
        LinearLayout form=new LinearLayout(this);form.setOrientation(1);form.setPadding(18,6,18,6);
        EditText address=new EditText(this);address.setSingleLine();address.setHint("http://192.168.1.10:8765");address.setText(detected==null?"":detected);form.addView(address);
        EditText pin=new EditText(this);pin.setSingleLine();pin.setInputType(2);pin.setHint("Receiver's six-digit PIN");form.addView(pin);
        new AlertDialog.Builder(this).setTitle("Send to Edo receiver").setView(form)
            .setNegativeButton("Cancel",null).setPositiveButton("Send",(d,w)->send(address.getText().toString().trim(),pin.getText().toString().trim())).show();
    }
    private void send(String address,String pin){
        if(fileId==null){Toast.makeText(this,"Choose a file in Received Files first",Toast.LENGTH_LONG).show();return;}
        AlertDialog progress=new AlertDialog.Builder(this).setTitle("Sending to Edo").setMessage("Pairing with destination…").setCancelable(false).create();progress.show();
        new Thread(()->{
            try{
                LanServer.Entry file=null;AndroidFiles repo=new AndroidFiles(this);
                for(LanServer.Entry e:repo.list())if(e.id.equals(fileId)){file=e;break;}
                if(file==null)throw new java.io.FileNotFoundException("Selected file no longer exists");
                final LanServer.Entry selected=file;PeerClient client=new PeerClient(address);
                client.pair(pin,"Edo Android TV");
                try(LanServer.Opened opened=repo.open(fileId)){
                    client.upload(selected.name,"",opened.length,opened.stream,(sent,total)->handler.post(()->progress.setMessage("Sent "+sent/1048576+" / "+Math.max(1,total/1048576)+" MB")));
                }
                handler.post(()->{progress.dismiss();new AlertDialog.Builder(this).setTitle("Transfer complete")
                    .setMessage("File sent successfully to the selected Edo device.").setPositiveButton("Done",(a,b)->finish()).show();});
            }catch(Exception e){handler.post(()->{progress.dismiss();new AlertDialog.Builder(this).setTitle("Transfer failed").setMessage(e.getMessage()).setPositiveButton("OK",null).show();});}
        },"edo-peer-sender").start();
    }
    private void stopDiscovery(){if(nsd!=null&&discovery!=null)try{nsd.stopServiceDiscovery(discovery);}catch(Exception ignored){}discovery=null;}
    @Override protected void onDestroy(){stopDiscovery();super.onDestroy();}
}
