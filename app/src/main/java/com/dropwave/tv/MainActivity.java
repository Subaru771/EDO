package com.dropwave.tv;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.widget.*;
import com.google.zxing.*;
import com.google.zxing.common.BitMatrix;
import java.util.*;

/** Native, D-pad-friendly Google TV dashboard. */
public final class MainActivity extends Activity {
    // TV-friendly variation of Edo's mint / midnight design language.
    private static final int BG=Color.rgb(16,21,26), PANEL=Color.rgb(25,33,39), BORDER=Color.rgb(52,67,75);
    private static final int WHITE=Color.rgb(242,246,247), MUTED=Color.rgb(172,190,199), LIME=Color.rgb(163,229,192);
    private TextView status,url,pin,count,activity,files,transportLabel;
    private ImageView qr;
    private LinearLayout actions, pendingActions;
    private TextView approvalStatus;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private String shownLink="", shownPending="";
    private boolean started=false;
    private boolean liteMode=false;
    private final Runnable refresh=new Runnable(){public void run(){update();handler.postDelayed(this,1800);}};
    private int dp(float size){return (int)(size*getResources().getDisplayMetrics().density+0.5f);}
    private GradientDrawable shape(int fill,int border,float corner){GradientDrawable d=new GradientDrawable();d.setColor(fill);d.setCornerRadius(dp(corner));d.setStroke(dp(1),border);return d;}
    private TextView label(String t,int size,int color,boolean bold){TextView v=new TextView(this);v.setText(t);v.setTextColor(color);v.setTextSize(size);if(bold)v.setTypeface(android.graphics.Typeface.DEFAULT,1);return v;}
    private LinearLayout column(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
    private LinearLayout.LayoutParams params(int w,int h){return new LinearLayout.LayoutParams(w<0?w:dp(w),h<0?h:dp(h));}
    private void gap(LinearLayout parent,int h){View v=new View(this);parent.addView(v,params(1,h));}
    private TextView action(String text,Runnable callback,boolean primary){
        TextView b=label(text,16,primary?BG:WHITE,true);b.setGravity(Gravity.CENTER);
        b.setPadding(dp(15),dp(11),dp(15),dp(11));b.setFocusable(true);b.setClickable(true);
        b.setBackground(shape(primary?LIME:PANEL,primary?LIME:BORDER,11));
        b.setElevation(dp(0));
        b.setOnFocusChangeListener((v,focus)->{
            b.setBackground(shape(focus?WHITE:(primary?LIME:PANEL),focus?WHITE:(primary?LIME:BORDER),11));
            b.setTextColor(focus?BG:(primary?BG:WHITE));
            b.setScaleX(focus?1.015f:1);b.setScaleY(focus?1.015f:1);
            b.setElevation(dp(focus?3:0));
        });
        b.setOnClickListener(v->callback.run());return b;
    }
    @Override public void onCreate(Bundle b){super.onCreate(b);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN|View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(BG));
        CrashReporter.install(this);
        liteMode=getPreferences(0).getBoolean("lite",false);
        build();
        if(getPreferences(0).getBoolean("sharing",true))startSharing();
    }
    private void switchLite(){liteMode=!liteMode;getPreferences(0).edit().putBoolean("lite",liteMode).apply();shownLink="";shownPending="";build();update();}
    private void build(){
        if(liteMode){buildLite();return;}
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackgroundColor(BG);
        LinearLayout root=column();root.setPadding(dp(25),dp(19),dp(25),dp(22));scroll.addView(root);
        LinearLayout heading=new LinearLayout(this);heading.setGravity(Gravity.CENTER_VERTICAL);root.addView(heading);
        LinearLayout logoArea=new LinearLayout(this);logoArea.setGravity(Gravity.CENTER_VERTICAL);heading.addView(logoArea,new LinearLayout.LayoutParams(0,dp(42),1));ImageView brandMark=new ImageView(this);brandMark.setImageResource(R.drawable.edo_brandmark);brandMark.setContentDescription("Edo file transfer logo");brandMark.setScaleType(ImageView.ScaleType.FIT_CENTER);logoArea.addView(brandMark,params(34,34));TextView logo=label("edo.",25,WHITE,true);logo.setPadding(dp(9),0,0,0);logoArea.addView(logo);
        status=label("●  STARTING",13,LIME,true);heading.addView(status);gap(root,11);
        TextView brandLine=label("Connect & transfer",24,WHITE,true);root.addView(brandLine);gap(root,4);
        TextView sub=label("Share with your phone or PC over the same Wi-Fi. No cloud needed.",15,MUTED,false);root.addView(sub);gap(root,17);
        boolean compact=getResources().getConfiguration().screenWidthDp<720;
        LinearLayout content=new LinearLayout(this);content.setOrientation(compact?LinearLayout.VERTICAL:LinearLayout.HORIZONTAL);root.addView(content);
        LinearLayout left=column();left.setBackground(shape(PANEL,BORDER,15));left.setPadding(dp(19),dp(18),dp(19),dp(18));
        LinearLayout.LayoutParams leftParams=compact?new LinearLayout.LayoutParams(-1,-2):new LinearLayout.LayoutParams(0,-2,1.15f);
        leftParams.setMargins(0,0,compact?0:dp(12),compact?dp(12):0);content.addView(left,leftParams);
        left.addView(label("CONNECT A DEVICE",12,LIME,true));gap(left,8);
        url=label("Finding your local address…",23,WHITE,true);url.setSingleLine(true);url.setEllipsize(android.text.TextUtils.TruncateAt.END);left.addView(url);
        gap(left,7);
        transportLabel=label("Checking connection security…",13,MUTED,true);left.addView(transportLabel);
        gap(left,14);
        LinearLayout join=new LinearLayout(this);join.setGravity(Gravity.CENTER_VERTICAL);left.addView(join);
        qr=new ImageView(this);qr.setScaleType(ImageView.ScaleType.FIT_CENTER);qr.setBackground(shape(Color.WHITE,Color.WHITE,10));qr.setPadding(dp(7),dp(7),dp(7),dp(7));join.addView(qr,params(compact?112:150,compact?112:150));
        LinearLayout side=column();side.setPadding(dp(16),0,0,0);join.addView(side,new LinearLayout.LayoutParams(0,-2,1));
        side.addView(label("SCAN WITH PHONE",12,LIME,true));gap(side,7);
        side.addView(label("Scan the QR or open the address in a browser.",14,MUTED,false));gap(side,12);
        side.addView(label("PAIRING PIN",12,MUTED,true));
        pin=label("—— —— ——",32,LIME,true);pin.setLetterSpacing(0.12f);side.addView(pin);
        gap(left,12);
        left.addView(label("Keep Edo running while transferring.",13,MUTED,false));
        gap(left,9);
        left.addView(label("PAIRING REQUESTS",12,LIME,true));gap(left,9);
        pendingActions=column();left.addView(pendingActions);

        LinearLayout right=column();right.setBackground(shape(PANEL,BORDER,15));right.setPadding(dp(18),dp(18),dp(18),dp(18));
        content.addView(right,compact?new LinearLayout.LayoutParams(-1,-2):new LinearLayout.LayoutParams(0,-2,0.85f));
        right.addView(label("FILES ON THIS TV",12,LIME,true));gap(right,8);
        count=label("0 files shared",21,WHITE,true);right.addView(count);gap(right,5);
        activity=label("No active transfers",13,MUTED,false);right.addView(activity);gap(right,9);
        files=label("Your latest received files will appear here.",13,MUTED,false);files.setMaxLines(4);right.addView(files);gap(right,13);
        actions=column();right.addView(actions);
        actions.addView(action("▣  Received files",this::browseReceivedFiles,true));gap(actions,7);
        actions.addView(action("＋  Send a file",this::chooseFile,false));gap(actions,7);
        actions.addView(action("▤  USB & storage",
            ()->startActivity(new Intent(this,ExternalStorageActivity.class)),false));gap(actions,13);
        LinearLayout moreActions=column();
        moreActions.setVisibility(View.GONE);actions.addView(moreActions);
        final TextView moreToggle=action("＋   More file actions",()->{
            boolean open=moreActions.getVisibility()!=View.VISIBLE;
            moreActions.setVisibility(open?View.VISIBLE:View.GONE);
            if(open)moreActions.requestFocus();
        },false);
        actions.addView(moreToggle);
        moreActions.addView(action("↻   Refresh file list",this::update,false));gap(moreActions,10);
        moreActions.addView(action("⬇   Install a received APK",this::installApk,false));gap(moreActions,10);
        moreActions.addView(action("■   Start / stop sharing",this::toggleSharing,false));
        gap(root,11);
        final TextView securityToggle=action("⚙   Advanced settings & security",()->{},false);
        root.addView(securityToggle);gap(root,13);
        LinearLayout security=column();security.setBackground(shape(PANEL,BORDER,15));security.setPadding(dp(18),dp(17),dp(18),dp(17));root.addView(security);
        security.setVisibility(View.GONE);
        securityToggle.setOnClickListener(v->{
            boolean open=security.getVisibility()!=View.VISIBLE;
            security.setVisibility(open?View.VISIBLE:View.GONE);
            securityToggle.setText(open?"−   Hide advanced settings":"⚙   Advanced settings & security");
            if(open){security.requestFocus();scroll.post(()->scroll.smoothScrollTo(0,security.getTop()));}
        });
        security.addView(label("SETTINGS & TRUST",13,LIME,true));gap(security,11);
        approvalStatus=label("New-device approvals: off",15,WHITE,true);security.addView(approvalStatus);gap(security,12);
        security.addView(action("◉  Toggle TV approval for new devices (optional)",this::toggleApproval,false));gap(security,10);
        security.addView(action("◈  Toggle HTTPS encryption (advanced)",this::toggleTls,false));gap(security,10);
        security.addView(action("?  How to connect with encryption",this::showEncryptionGuide,false));gap(security,10);
        security.addView(action("◎  Toggle paired-phone remote control",this::toggleRemote,false));gap(security,10);
        security.addView(action("×  Disconnect and forget all paired devices",this::clearDevices,false));gap(security,12);
        security.addView(label("Pairing requests appear next to the QR code above.",13,MUTED,false));
        gap(root,26);root.addView(label("Local Wi-Fi / Ethernet  •  Files received in Downloads / Edo  •  One TV, many devices",13,MUTED,false));
        security.addView(action("WebDAV credentials (HTTPS only)",()->{
            LanServer server=TransferService.server;
            if(server==null||!server.isSecure()){
                new AlertDialog.Builder(this).setTitle("Enable HTTPS first")
                    .setMessage("Secure WebDAV is unavailable while HTTP mode is active.").setPositiveButton("OK",null).show();return;
            }
            new AlertDialog.Builder(this).setTitle("Edo WebDAV")
                .setMessage("URL: "+server.scheme()+"://"+TransferService.localIp()+":"+server.port()+"/dav/\nUsername: edo\nPassword: "+server.webdavPassword()+"\n\nTrust Edo’s certificate on the connecting device. Do not share this secret.")
                .setPositiveButton("Close",null).show();
        },false));gap(security,10);
        security.addView(action("Quick receive (QR-only mode)",this::switchLite,false));gap(security,10);
        security.addView(action("Check connection / troubleshoot",this::checkConnection,false));gap(security,10);
        security.addView(action("View / share diagnostics",()->startActivity(new Intent(this,DiagnosticsActivity.class)),false));gap(security,10);
        security.addView(action("Certificate fingerprint / export",this::showCertificateInfo,false));
        setContentView(scroll);
    }
    private void showEncryptionGuide(){
        LanServer srv=TransferService.server;
        boolean secure=srv!=null&&srv.isSecure();
        String address=srv!=null&&srv.isRunning()&&TransferService.localIp()!=null?
            srv.scheme()+"://"+TransferService.localIp()+":"+srv.port():"Sharing is offline";
        String guide="1. Connect your phone/PC to the same Wi-Fi as the TV.\n"+
            "2. Open or scan: "+address+"\n"+
            "3. If HTTPS is ON, your device must trust the TV certificate BEFORE pairing.\n"+
            "4. Compare the SHA-256 certificate fingerprint on this TV with the certificate on the connecting device.\n"+
            "5. Once the secure page opens, enter the pairing PIN displayed on the TV.\n\n"+
            "Browser note: Edo uses a self-signed local certificate. Some browsers reject it even after you install its public certificate, especially if the certificate lacks an IP-address Subject Alternative Name (SAN). Edo cannot bypass browser certificate validation.\n\n"+
            "Windows: inspect the certificate in the browser or import it only after validating its fingerprint.\n"+
            "Android: certificate installation depends on Android version and browser policy.\n"+
            "iPhone: Safari may still reject local self-signed certificates; installing a profile is not a guaranteed fix.\n\n"+
            (secure?"Current mode: HTTPS. If your browser cannot validate the certificate, use a trusted network and explicitly switch back to HTTP until certificate support is improved.":"Current mode: HTTP. Transfers are not encrypted; use only a trusted network.");
        new AlertDialog.Builder(this).setTitle("Encrypted connection guide")
            .setMessage(guide).setNegativeButton("Close",null)
            .setPositiveButton("Certificate details",(d,w)->showCertificateInfo()).show();
    }
    private void showCertificateInfo(){
        try {
            new AlertDialog.Builder(this).setTitle("Edo certificate (public only)")
                .setMessage(TlsAndroid.certificateDetails()+"\n\nOnly save this certificate after confirming the fingerprint on the TV. The private key never leaves the TV.")
                .setNegativeButton("Close",null)
                .setNeutralButton("Copy fingerprint",(d,w)->{
                    String f;try{f=TlsAndroid.fingerprint();}catch(Exception ex){return;}
                    ((android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(android.content.ClipData.newPlainText("Edo TLS fingerprint",f));
                    Toast.makeText(this,"Public fingerprint copied",Toast.LENGTH_SHORT).show();
                })
                .setPositiveButton("Save certificate",(d,w)->exportPublicCertificate()).show();
        }catch(Exception e){new AlertDialog.Builder(this).setTitle("Certificate unavailable")
            .setMessage("Enable HTTPS and start sharing first. Details: "+e.getMessage())
            .setPositiveButton("OK",null).show();}
    }
    private void exportPublicCertificate(){
        try {
            Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("application/x-x509-ca-cert");
            i.putExtra(Intent.EXTRA_TITLE,"Edo-TV-public-certificate.cer");
            startActivityForResult(i,84);
        }catch(Exception e){Toast.makeText(this,"No document picker on this TV: "+e.getMessage(),Toast.LENGTH_LONG).show();}
    }
    private void checkConnection(){
        LanServer srv=TransferService.server;
        if(srv==null||!srv.isRunning()){
            new AlertDialog.Builder(this).setTitle("Edo sharing is stopped")
                .setMessage("Start sharing from the main screen, then scan the newly generated QR code.")
                .setPositiveButton("Start sharing",(d,w)->{getPreferences(0).edit().putBoolean("sharing",true).apply();startSharing();handler.postDelayed(this::update,700);})
                .setNegativeButton("Close",null).show();return;
        }
        final String address=srv.scheme()+"://"+TransferService.localIp()+":"+srv.port();
        final String details="Address: "+address+"\nPairing PIN: "+srv.pin()+
            "\nTV approval: "+(srv.requireApproval()?"ON (approve new connections next to QR)":"OFF (PIN-only pairing)")+
            "\nEncryption: "+(srv.isSecure()?"HTTPS (certificate trust required)":"HTTP (trusted LAN only)")+
            (srv.isSecure()?"\nCertificate SHA-256: "+certificateFingerprintOrError():"")+
            "\n\nTo transfer: open the address on the phone/PC using the same Wi-Fi, enter the PIN, and choose a file."
            +(srv.isSecure()?"\n\nIf the browser warns about a certificate, either explicitly trust Edo's certificate or disable advanced HTTPS in TV settings.":"")+
            "\n\nIf the page does not load, check your Wi-Fi client isolation or device firewall.";
        new AlertDialog.Builder(this).setTitle("Transfer connection check")
            .setMessage(details).setPositiveButton("OK",null)
            .setNeutralButton("Show QR",(d,w)->{if(liteMode)return;switchLite();}).show();
    }
    private String certificateFingerprintOrError(){
        try{return TlsAndroid.fingerprint();}catch(Exception e){return "Unavailable: "+e.getMessage();}
    }
    private void toggleRemote(){
        boolean next=!getSharedPreferences("dropwave",0).getBoolean("allow_remote",false);
        getSharedPreferences("dropwave",0).edit().putBoolean("allow_remote",next).apply();
        if(TransferService.server!=null)TransferService.server.setRemoteControl(next,RemoteControlBridge::perform);
        Toast.makeText(this,next?"Paired phone remote enabled":"Phone remote disabled",Toast.LENGTH_SHORT).show();
        update();
    }
    private void toggleTls(){
        boolean next=!getSharedPreferences("dropwave",0).getBoolean("secure_tls",false);
        new AlertDialog.Builder(this).setTitle(next?"Enable encrypted HTTPS?":"Switch back to HTTP?")
          .setMessage(next?"Encrypted transfers require other devices to trust Edo’s local self-signed certificate. Some browsers, especially iOS, may refuse the connection until that certificate is trusted. The service will restart.":"HTTP is not encrypted and should only be used on a trusted LAN. The service will restart.")
          .setNegativeButton("Cancel",null).setPositiveButton("Continue",(d,w)->{
            getSharedPreferences("dropwave",0).edit().putBoolean("secure_tls",next).apply();
            // Restart in the already-running service; no asynchronous stop/start race.
            try { startForegroundService(new Intent(this,TransferService.class).setAction(TransferService.ACTION_RESTART)); }
            catch(Exception e){Toast.makeText(this,"Could not restart Edo: "+e.getMessage(),Toast.LENGTH_LONG).show();}
            shownLink="";handler.postDelayed(this::update,900);
          }).show();
    }
    private void buildLite(){
        ScrollView scroll=new ScrollView(this);scroll.setBackgroundColor(BG);scroll.setFillViewport(true);
        LinearLayout root=column();root.setPadding(dp(25),dp(19),dp(25),dp(22));scroll.addView(root);
        LinearLayout liteBrand=new LinearLayout(this);liteBrand.setGravity(Gravity.CENTER_VERTICAL);root.addView(liteBrand);
        ImageView liteMark=new ImageView(this);liteMark.setImageResource(R.drawable.edo_brandmark);liteMark.setContentDescription("Edo file transfer logo");
        liteBrand.addView(liteMark,params(42,42));TextView liteName=label("edo.",30,LIME,true);liteName.setPadding(dp(12),0,0,0);liteBrand.addView(liteName);gap(root,4);
        root.addView(label("Every Device. One Connection.",18,WHITE,true));gap(root,18);
        status=label("●  STARTING",16,LIME,true);root.addView(status);gap(root,14);
        root.addView(label("SCAN TO RECEIVE",15,LIME,true));gap(root,7);
        url=label("Finding your local address…",26,WHITE,true);root.addView(url);gap(root,6);
        transportLabel=label("Checking connection security…",13,MUTED,true);root.addView(transportLabel);gap(root,12);
        qr=new ImageView(this);qr.setScaleType(ImageView.ScaleType.FIT_CENTER);
        qr.setBackground(shape(Color.WHITE,Color.WHITE,15));qr.setPadding(dp(10),dp(10),dp(10),dp(10));
        root.addView(qr,params(230,230));gap(root,15);
        pin=label("—— —— ——",38,LIME,true);pin.setLetterSpacing(.12f);root.addView(pin);gap(root,9);
        root.addView(label("Open the QR link on a phone or PC. Pair once, then send files.",16,MUTED,false));gap(root,20);
        root.addView(action("Open full Edo",this::switchLite,true));gap(root,10);
        root.addView(label("PAIRING REQUESTS",13,LIME,true));gap(root,8);
        approvalStatus=label("Approval is required for new devices",13,MUTED,false);root.addView(approvalStatus);gap(root,8);
        pendingActions=column();root.addView(pendingActions);gap(root,10);
        root.addView(action("Browse received files",this::browseReceivedFiles,false));gap(root,10);
        root.addView(action("Start / stop sharing",this::toggleSharing,false));gap(root,10);
        root.addView(action("Connection check",this::checkConnection,false));gap(root,10);
        root.addView(action("Encrypted connection guide",this::showEncryptionGuide,false));gap(root,10);
        root.addView(action("Diagnostics",()->startActivity(new Intent(this,DiagnosticsActivity.class)),false));
        setContentView(scroll);
        count=null;activity=null;files=null;actions=null;
        shownLink="";shownPending="";
    }
    private void browseReceivedFiles(){startActivity(new Intent(this,ReceivedFilesActivity.class));}
    private void toggleApproval(){
        boolean next=!getSharedPreferences("dropwave",0).getBoolean("require_approval",false);
        getSharedPreferences("dropwave",0).edit().putBoolean("require_approval",next).apply();
        if(TransferService.server!=null)TransferService.server.setRequireApproval(next);
        update();
    }
    private void clearDevices(){
        new AlertDialog.Builder(this).setTitle("Disconnect all devices?")
            .setMessage("All sessions and remembered devices will need to pair again.")
            .setNegativeButton("Cancel",null).setPositiveButton("Disconnect",(d,w)->{
                if(TransferService.server!=null)TransferService.server.disconnectAll();
                Toast.makeText(this,"Devices disconnected",Toast.LENGTH_SHORT).show();
            }).show();
    }
    private void installApk(){
        try{
            List<LanServer.Entry> entries=new AndroidFiles(this).list();List<LanServer.Entry> apks=new ArrayList<>();
            for(LanServer.Entry e:entries)if(e.id.startsWith("m")&&e.name.toLowerCase(Locale.ROOT).endsWith(".apk"))apks.add(e);
            if(apks.isEmpty()){Toast.makeText(this,"No APK files in received downloads",Toast.LENGTH_LONG).show();return;}
            String[] names=new String[apks.size()];for(int i=0;i<names.length;i++)names[i]=apks.get(i).name;
            new AlertDialog.Builder(this).setTitle("Install an APK").setItems(names,(dialog,which)->{
                try{
                    Uri uri=android.content.ContentUris.withAppendedId(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI,Long.parseLong(apks.get(which).id.substring(1)));
                    Intent intent=new Intent(Intent.ACTION_VIEW);intent.setDataAndType(uri,"application/vnd.android.package-archive");
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);startActivity(intent);
                }catch(Exception e){Toast.makeText(this,"No compatible installer, or installation is blocked: "+e.getMessage(),Toast.LENGTH_LONG).show();}
            }).show();
        }catch(Exception e){Toast.makeText(this,"Cannot access APK files",Toast.LENGTH_LONG).show();}
    }
    private void chooseFile(){
        try{
            Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("*/*");
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
            startActivityForResult(i,42);
        }catch(Exception e){Toast.makeText(this,"This TV has no document picker. Received files can still be downloaded.",Toast.LENGTH_LONG).show();}
    }
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(request==84 && result==RESULT_OK&&data!=null&&data.getData()!=null){
            try(java.io.OutputStream out=getContentResolver().openOutputStream(data.getData())){
                if(out==null)throw new java.io.IOException("Cannot write selected document");
                out.write(TlsAndroid.certificateDer());
                Toast.makeText(this,"Saved Edo public certificate (no private key)",Toast.LENGTH_LONG).show();
            }catch(Exception e){Toast.makeText(this,"Certificate export failed: "+e.getMessage(),Toast.LENGTH_LONG).show();}
            return;
        }
        if(request==42 && result==RESULT_OK&&data!=null&&data.getData()!=null){
            try{new AndroidFiles(this).share(data.getData());Toast.makeText(this,"File available to your devices",Toast.LENGTH_LONG).show();update();}
            catch(Exception e){Toast.makeText(this,"Could not share file: "+e.getMessage(),Toast.LENGTH_LONG).show();}
        }
    }
    private void startSharing(){
        try{startForegroundService(new Intent(this,TransferService.class));started=true;}
        catch(Exception e){Toast.makeText(this,"Could not start: "+e.getMessage(),Toast.LENGTH_LONG).show();}
    }
    private void toggleSharing(){
        boolean on=TransferService.server!=null && TransferService.server.isRunning();
        getPreferences(0).edit().putBoolean("sharing",!on).apply();
        if(on)startService(new Intent(this,TransferService.class).setAction(TransferService.ACTION_STOP));
        else startSharing();
        handler.postDelayed(this::update,300);
    }
    private static String sizeText(long n){if(n<0)return "unknown";double g=n/1073741824.0;return String.format(java.util.Locale.US,"%.1f GB",g);}
    private void update(){
        if(status==null)return;
        LanServer srv=TransferService.server;
        boolean online=srv!=null&&srv.isRunning();
        String ip=TransferService.localIp();
        status.setText(online?(ip==null?"●  WAITING FOR NETWORK":(srv.isSecure()?"●  HTTPS - CERTIFICATE TRUST REQUIRED":"●  READY TO TRANSFER")):"○  SHARING OFF");
        status.setTextColor(online?LIME:MUTED);
        String link=online&&ip!=null?srv.scheme()+"://"+ip+":"+srv.port():"";
        url.setText(link.isEmpty()?(TransferService.error.isEmpty()?"Connect TV to Wi-Fi or Ethernet":TransferService.error):link);
        if(transportLabel!=null){
            transportLabel.setText(online?(srv.isSecure()?"●  HTTPS ENCRYPTED · Certificate trust required":"●  HTTP UNENCRYPTED · Trusted LAN only"):
                (TransferService.error.isEmpty()?"Sharing is offline":"Server error: check Security & Pairing"));
            transportLabel.setTextColor(online&&srv.isSecure()?LIME:MUTED);
        }
        pin.setText(online?srv.pin():"— — — — — —");
        if(!link.equals(shownLink)){
            shownLink=link;
            if(!link.isEmpty()){
                try{renderQr(link+"/?pin="+srv.pin());}catch(Exception e){qr.setImageDrawable(null);}
            }else qr.setImageDrawable(null);
        }
        if(activity!=null)activity.setText(srv==null||srv.activeTransfers()==0?"No active transfers":srv.activeTransfers()+" active transfer(s)");
        if(approvalStatus!=null)approvalStatus.setText("TV approval: "+(srv!=null&&srv.requireApproval()?"ON":"OFF")+"  •  HTTPS: "+(srv!=null&&srv.isSecure()?"ON":"OFF")+"  •  Phone remote: "+(srv!=null&&srv.remoteEnabled()?"ON":"OFF")+(srv!=null?"    •    Free: "+sizeText(new AndroidFiles(this).freeBytes()):""));
        if(pendingActions!=null){
            List<String[]> requests=srv==null?Collections.emptyList():srv.pendingRequests();
            StringBuilder current=new StringBuilder();for(String[] req:requests)current.append(req[0]).append(';');
            if(!shownPending.equals(current.toString())){
              shownPending=current.toString();pendingActions.removeAllViews();
              for(String[] req:requests){
                pendingActions.addView(label("PAIRING REQUEST: "+req[1],15,WHITE,true));gap(pendingActions,7);
                final String id=req[0];
                TextView approve=action("✓  Approve "+req[1],()->{srv.decide(id,true);update();},true);
                pendingActions.addView(approve);gap(pendingActions,6);
                pendingActions.addView(action("×  Reject",()->{srv.decide(id,false);update();},false));gap(pendingActions,12);
                if(requests.indexOf(req)==0){approve.requestFocus();Toast.makeText(this,"New pairing request: approve next to QR",Toast.LENGTH_LONG).show();}
              }
            }
        }
        try{
            List<LanServer.Entry> list=new AndroidFiles(this).list();
            if(count!=null)count.setText(list.size()+" file"+(list.size()==1?"":"s")+" available");
            if(list.isEmpty()){if(files!=null)files.setText("Files sent to this TV will appear here.");}
            else{
                StringBuilder sb=new StringBuilder();int shown=Math.min(6,list.size());
                for(int i=0;i<shown;i++){
                    LanServer.Entry f=list.get(i);String n=f.name;
                    if(n.length()>37)n=n.substring(0,34)+"…";
                    sb.append("▸  ").append(n).append("\n");
                }
                if(list.size()>shown)sb.append("+ ").append(list.size()-shown).append(" more");
                if(files!=null)files.setText(sb.toString().trim());
            }
        }catch(Exception ignored){}
        if(actions!=null && actions.getChildCount()>0){
            TextView toggle=(TextView)actions.getChildAt(actions.getChildCount()-1);
            toggle.setText(online?"■  Stop sharing":"▶  Start sharing");
        }
    }
    private void renderQr(String link) throws WriterException {
        BitMatrix matrix=new com.google.zxing.qrcode.QRCodeWriter().encode(link,BarcodeFormat.QR_CODE,440,440);
        Bitmap bmp=Bitmap.createBitmap(matrix.getWidth(),matrix.getHeight(),Bitmap.Config.RGB_565);
        int[] px=new int[matrix.getWidth()*matrix.getHeight()];
        for(int y=0;y<matrix.getHeight();y++)for(int x=0;x<matrix.getWidth();x++)px[y*matrix.getWidth()+x]=matrix.get(x,y)?Color.BLACK:Color.WHITE;
        bmp.setPixels(px,0,matrix.getWidth(),0,0,matrix.getWidth(),matrix.getHeight());qr.setImageBitmap(bmp);
    }
    @Override protected void onResume(){super.onResume();RemoteControlBridge.activate(this);handler.removeCallbacks(refresh);handler.post(refresh);}
    @Override protected void onPause(){RemoteControlBridge.deactivate(this);handler.removeCallbacks(refresh);super.onPause();}
}
