package com.dropwave.tv;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Best-effort IPv4 LAN discovery. Responses provide only an address, port and protocol. */
public final class UdpDiscovery implements AutoCloseable {
    private static final int DISCOVERY_PORT=18766;
    private static final byte[] QUERY="EDO_FIND_4".getBytes(StandardCharsets.US_ASCII);
    private final int httpPort;
    private final String scheme;
    private volatile boolean running;
    private DatagramSocket socket;
    private Thread worker;
    public UdpDiscovery(int serverPort,String scheme){this.httpPort=serverPort;this.scheme=scheme;}
    public void start(){try{
        socket=new DatagramSocket(null);socket.setReuseAddress(true);
        socket.bind(new InetSocketAddress("0.0.0.0",DISCOVERY_PORT));socket.setSoTimeout(2000);
        running=true;worker=new Thread(this::loop,"edo-lan-discovery");worker.setDaemon(true);worker.start();
    }catch(Exception ignored){close();}}
    private void loop(){byte[] buffer=new byte[256];
        while(running){try{
            DatagramPacket packet=new DatagramPacket(buffer,buffer.length);socket.receive(packet);
            InetAddress addr=packet.getAddress();
            if(!(addr.isSiteLocalAddress()||addr.isLoopbackAddress()||addr.isLinkLocalAddress()))continue;
            if(packet.getLength()!=QUERY.length)continue;
            boolean match=true;for(int i=0;i<QUERY.length;i++)if(buffer[i]!=QUERY[i]){match=false;break;}
            if(!match)continue;
            byte[] reply=("EDO_HERE_4|"+httpPort+"|"+scheme).getBytes(StandardCharsets.US_ASCII);
            socket.send(new DatagramPacket(reply,reply.length,addr,packet.getPort()));
        }catch(SocketTimeoutException ignored){}catch(IOException e){if(!running)break;}}
    }
    public static final class Peer {public final String url;public Peer(String u){url=u;} }
    public static List<Peer> scan(int timeoutMs)throws IOException {
        LinkedHashSet<String> urls=new LinkedHashSet<>();
        try(DatagramSocket sender=new DatagramSocket()){
            sender.setBroadcast(true);sender.setSoTimeout(Math.max(100,Math.min(timeoutMs,6000)));
            Set<InetAddress> destinations=new LinkedHashSet<>();
            destinations.add(InetAddress.getByName("255.255.255.255"));
            destinations.add(InetAddress.getLoopbackAddress());
            Enumeration<NetworkInterface> interfaces=NetworkInterface.getNetworkInterfaces();
            if(interfaces!=null)while(interfaces.hasMoreElements()){
                NetworkInterface adapter=interfaces.nextElement();
                if(!adapter.isUp()||adapter.isLoopback())continue;
                for(InterfaceAddress address:adapter.getInterfaceAddresses())if(address.getBroadcast()!=null)destinations.add(address.getBroadcast());
            }
            for(InetAddress address:destinations){
                try{sender.send(new DatagramPacket(QUERY,QUERY.length,address,DISCOVERY_PORT));}
                catch(IOException ignored){}
            }
            long end=System.currentTimeMillis()+Math.max(100,Math.min(timeoutMs,6000));
            byte[] buffer=new byte[256];
            while(System.currentTimeMillis()<end){
                try{
                    DatagramPacket response=new DatagramPacket(buffer,buffer.length);sender.setSoTimeout(Math.max(50,(int)(end-System.currentTimeMillis())));sender.receive(response);
                    InetAddress ip=response.getAddress();if(!(ip.isSiteLocalAddress()||ip.isLoopbackAddress()||ip.isLinkLocalAddress()))continue;
                    String message=new String(response.getData(),response.getOffset(),response.getLength(),StandardCharsets.US_ASCII);
                    String[] parts=message.split("\\|");
                    if(parts.length!=3||!parts[0].equals("EDO_HERE_4"))continue;
                    int port=Integer.parseInt(parts[1]);
                    if(port<1024||port>65535||(!parts[2].equals("http")&&!parts[2].equals("https")))continue;
                    String host=ip instanceof Inet6Address?"["+ip.getHostAddress()+"]":ip.getHostAddress();
                    urls.add(parts[2]+"://"+host+":"+port);
                }catch(SocketTimeoutException e){break;}catch(Exception ignored){}
            }
        }
        List<Peer> peers=new ArrayList<>();for(String u:urls)peers.add(new Peer(u));return peers;
    }
    @Override public void close(){running=false;if(socket!=null)socket.close();}
}
