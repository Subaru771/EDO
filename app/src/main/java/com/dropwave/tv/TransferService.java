package com.dropwave.tv;

import android.app.*;
import android.content.*;
import android.os.*;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import java.net.*;
import java.util.*;

/** User-started foreground transfer service; stays online when Home is pressed. */
public final class TransferService extends Service {
    static volatile LanServer server;
    static volatile String error="";
    private NsdManager nsd;
    private NsdManager.RegistrationListener registration;
    private PowerManager.WakeLock wake;
    private final Handler wakeHandler=new Handler(Looper.getMainLooper());
    private final Runnable wakeTask=new Runnable(){public void run(){
        LanServer s=server;boolean transferring=s!=null&&s.activeTransfers()>0;
        try{if(transferring && wake!=null&&!wake.isHeld())wake.acquire(10*60*1000L);
            else if(!transferring&&wake!=null&&wake.isHeld())wake.release();}catch(Exception ignored){}
        wakeHandler.postDelayed(this,2500);
    }};
    public static final String ACTION_STOP="com.dropwave.tv.STOP";
    public static final String ACTION_RESTART="com.dropwave.tv.RESTART";
    private static final String CHANNEL="dropwave_transfers";
    @Override public void onCreate(){super.onCreate();CrashReporter.install(this);createNotificationChannel();
        PowerManager pm=(PowerManager)getSystemService(POWER_SERVICE);
        wake=pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"Edo:ActiveTransfer");
        wakeHandler.post(wakeTask);
    }
    @Override public int onStartCommand(Intent intent,int flags,int startId) {
        if(intent!=null&&ACTION_STOP.equals(intent.getAction())){unregisterDiscovery();stopServer();stopSelf();return START_NOT_STICKY;}
        // Restart *inside* this service: avoids the old stopService()/delayed-start race.
        if(intent!=null&&ACTION_RESTART.equals(intent.getAction())){unregisterDiscovery();stopServer();}
        startForeground(26,notification());
        if(server==null||!server.isRunning()){
            try{
                AndroidFiles files=new AndroidFiles(this);
                LanServer instance=new LanServer(files,()->getAssets().open("index.html"),new java.io.File(getFilesDir(),"transfers"));
                instance.setRequireApproval(getSharedPreferences("dropwave",0).getBoolean("require_approval",true));
                instance.setRemoteControl(getSharedPreferences("dropwave",0).getBoolean("allow_remote",false),RemoteControlBridge::perform);
                boolean encrypted=getSharedPreferences("dropwave",0).getBoolean("secure_tls",false);
                if(encrypted)instance.startSecure(8765,TlsAndroid.context());else instance.start(8765);
                server=instance;error="";registerDiscovery(instance.port());
            }catch(Exception e){error=e.getMessage()==null?e.toString():e.getMessage();stopSelf();}
        }
        return START_NOT_STICKY;
    }
    private void createNotificationChannel(){
        if(Build.VERSION.SDK_INT>=26){
            NotificationChannel channel=new NotificationChannel(CHANNEL,"Edo sharing",NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Local file sharing on your Wi-Fi network");
            ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(channel);
        }
    }
    private Notification notification(){
        Intent intent=new Intent(this,MainActivity.class);
        PendingIntent pending=PendingIntent.getActivity(this,0,intent,PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        return new Notification.Builder(this,CHANNEL)
            .setSmallIcon(R.drawable.icon).setContentTitle("Edo is ready")
            .setContentText("Sharing files with devices on your local network")
            .setOngoing(true).setContentIntent(pending).build();
    }
    static synchronized void stopServer(){LanServer current=server;server=null;if(current!=null)current.close();}
    private void unregisterDiscovery(){
        try{if(nsd!=null&&registration!=null)nsd.unregisterService(registration);}catch(Exception ignored){}
        nsd=null;registration=null;
    }
    private void registerDiscovery(int port){
        unregisterDiscovery();
        try{
            nsd=(NsdManager)getSystemService(NSD_SERVICE);NsdServiceInfo info=new NsdServiceInfo();
            info.setServiceName("Edo TV");info.setServiceType("_dropwave._tcp.");info.setPort(port);
            info.setAttribute("scheme",server!=null&&server.isSecure()?"https":"http");
            registration=new NsdManager.RegistrationListener(){
                public void onRegistrationFailed(NsdServiceInfo i,int code){}
                public void onUnregistrationFailed(NsdServiceInfo i,int code){}
                public void onServiceRegistered(NsdServiceInfo i){}
                public void onServiceUnregistered(NsdServiceInfo i){}
            };
            nsd.registerService(info,NsdManager.PROTOCOL_DNS_SD,registration);
        }catch(Exception ignored){}
    }
    @Override public void onDestroy(){
        wakeHandler.removeCallbacks(wakeTask);
        try{if(wake!=null&&wake.isHeld())wake.release();}catch(Exception ignored){}
        unregisterDiscovery();
        stopServer();super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent){return null;}
    public static String localIp(){
        String choice=null;int best=-1;
        try{
            Enumeration<NetworkInterface> network=NetworkInterface.getNetworkInterfaces();
            while(network.hasMoreElements()){
                NetworkInterface nic=network.nextElement();if(!nic.isUp()||nic.isLoopback()||nic.isVirtual())continue;
                String name=nic.getName().toLowerCase(Locale.ROOT);
                if(name.contains("tun")||name.contains("ppp")||name.contains("docker"))continue;
                Enumeration<InetAddress> addresses=nic.getInetAddresses();
                while(addresses.hasMoreElements()){
                    InetAddress a=addresses.nextElement();if(!(a instanceof Inet4Address)||!a.isSiteLocalAddress())continue;
                    int score=(name.startsWith("wlan")||name.startsWith("eth"))?20:5;
                    if(score>best){best=score;choice=a.getHostAddress();}
                }
            }
        }catch(Exception ignored){}
        return choice;
    }
}
