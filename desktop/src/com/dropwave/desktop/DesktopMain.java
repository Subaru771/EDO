package com.dropwave.desktop;

import com.dropwave.tv.LanServer;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import javax.imageio.ImageIO;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/** Cross-platform, genuine Swing desktop server. No embedded browser or command window. */
public final class DesktopMain {
    private static LanServer server;
    private static JFrame frame;
    private static JLabel status;
    private static JTextField folderField;
    private static JCheckBox approval, encrypted;
    private static JTextArea info;
    private static final ExecutorService syncWorkers=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"dropwave-backup");t.setDaemon(true);return t;});
    private static JTextField remoteField,pinField,backupField,targetField;
    private static JCheckBox automatic, twoWayAuto;
    private static volatile long lastSync=0;
    private static final ScheduledExecutorService scheduler=Executors.newSingleThreadScheduledExecutor(r->{Thread t=new Thread(r,"dropwave-desktop-status");t.setDaemon(true);return t;});
    private static final java.util.prefs.Preferences pref=java.util.prefs.Preferences.userNodeForPackage(DesktopMain.class);
    public static void main(String[] args){SwingUtilities.invokeLater(DesktopMain::build);}
    private static String localIp(){try{Enumeration<NetworkInterface> n=NetworkInterface.getNetworkInterfaces();while(n.hasMoreElements()){NetworkInterface i=n.nextElement();if(!i.isUp()||i.isLoopback()||i.isVirtual())continue;Enumeration<InetAddress> add=i.getInetAddresses();while(add.hasMoreElements()){InetAddress a=add.nextElement();if(a instanceof Inet4Address&&a.isSiteLocalAddress())return a.getHostAddress();}}}catch(Exception ignored){}return "YOUR-PC-IP";}
    private static void build(){
        new File(System.getProperty("user.home"),"EdoShare").mkdirs();
        frame=new JFrame("Edo — Every Device. One Connection.");
        try(InputStream iconInput=DesktopMain.class.getResourceAsStream("/edo-icon.png")){
            if(iconInput!=null){Image icon=ImageIO.read(iconInput);if(icon!=null)frame.setIconImage(icon);}
        }catch(IOException ignored){} // Brand art is optional at runtime; file sharing must still start.
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);frame.setSize(710,720);frame.setLocationRelativeTo(null);
        JPanel root=new JPanel();root.setLayout(new BoxLayout(root,BoxLayout.Y_AXIS));root.setBorder(BorderFactory.createEmptyBorder(22,24,22,24));
        JPanel brandRow=new JPanel(new FlowLayout(FlowLayout.LEFT,12,0));brandRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        brandRow.setOpaque(false);
        try(InputStream iconInput=DesktopMain.class.getResourceAsStream("/edo-icon.png")){
            if(iconInput!=null){Image icon=ImageIO.read(iconInput);if(icon!=null)brandRow.add(new JLabel(new ImageIcon(icon.getScaledInstance(54,54,Image.SCALE_SMOOTH))));}
        }catch(IOException ignored){}
        JLabel heading=new JLabel("Edo  ·  Every Device. One Connection.");heading.setFont(heading.getFont().deriveFont(Font.BOLD,23f));brandRow.add(heading);root.add(brandRow);root.add(Box.createVerticalStrut(9));
        JTextArea tagline=new JTextArea("PC ↔ PC, PC ↔ TV, phone ↔ PC, and phone ↔ TV. No TV needed for PC-to-PC transfers. Files stay on your local network.");tagline.setEditable(false);tagline.setLineWrap(true);tagline.setWrapStyleWord(true);tagline.setOpaque(false);tagline.setMaximumSize(new Dimension(800,55));root.add(tagline);root.add(Box.createVerticalStrut(16));
        JPanel sharingHelp=new JPanel(new FlowLayout(FlowLayout.LEFT,8,2));
        sharingHelp.setAlignmentX(Component.LEFT_ALIGNMENT);
        sharingHelp.add(new JLabel("No TV required · Share PC to PC in minutes"));
        JButton helpButton=new JButton("How to transfer between devices");
        sharingHelp.add(helpButton);
        helpButton.addActionListener(ev->showSharingGuide());
        root.add(sharingHelp);root.add(Box.createVerticalStrut(11));
        root.add(new JLabel("Folder to share (files in this folder are accessible to paired devices):"));
        JPanel folderPanel=new JPanel(new BorderLayout(8,8));folderField=new JTextField(pref.get("folder",System.getProperty("user.home")+File.separator+"EdoShare"));JButton browse=new JButton("Browse…");browse.addActionListener(e->{JFileChooser chooser=new JFileChooser(folderField.getText());chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);if(chooser.showOpenDialog(frame)==JFileChooser.APPROVE_OPTION)folderField.setText(chooser.getSelectedFile().getAbsolutePath());});folderPanel.add(folderField);folderPanel.add(browse,BorderLayout.EAST);folderPanel.setMaximumSize(new Dimension(1000,45));root.add(folderPanel);
        root.add(Box.createVerticalStrut(10));approval=new JCheckBox("Approve every new device on this computer",pref.getBoolean("approve",true));root.add(approval);
        encrypted=new JCheckBox("Encrypted HTTPS (advanced: trust the local certificate first)",pref.getBoolean("tls",false));root.add(encrypted);
        JPanel buttons=new JPanel(new FlowLayout(FlowLayout.LEFT));JButton start=new JButton("Start sharing");JButton stop=new JButton("Stop sharing");stop.setEnabled(false);buttons.add(start);buttons.add(stop);root.add(buttons);
        status=new JLabel("Sharing is off");root.add(status);root.add(Box.createVerticalStrut(10));
        info=new JTextArea("PC to PC: Start sharing here; on the other PC, open this address in a browser and enter the PIN. No TV needed.\n");info.setEditable(false);info.setLineWrap(true);info.setWrapStyleWord(true);info.setBackground(root.getBackground());root.add(info);
        root.add(Box.createVerticalStrut(15));
        JLabel syncTitle=new JLabel("Advanced: backup & safe two-way merge (PC or TV)");syncTitle.setFont(syncTitle.getFont().deriveFont(Font.BOLD,17f));root.add(syncTitle);
        root.add(new JLabel("Remote Edo address and pairing PIN (TV or another PC):"));
        JButton scanPeers=new JButton("Find Edo receivers on LAN");root.add(scanPeers);
        JPanel remoteRow=new JPanel(new BorderLayout(8,8));remoteField=new JTextField(pref.get("remote","http://192.168.1.10:8765"));pinField=new JTextField(6);remoteRow.add(remoteField,BorderLayout.CENTER);remoteRow.add(pinField,BorderLayout.EAST);remoteRow.setMaximumSize(new Dimension(900,42));root.add(remoteRow);
        scanPeers.addActionListener(e->{scanPeers.setEnabled(false);
            syncWorkers.execute(()->{try{
                java.util.List<com.dropwave.tv.UdpDiscovery.Peer> found=com.dropwave.tv.UdpDiscovery.scan(1800);
                SwingUtilities.invokeLater(()->{
                    scanPeers.setEnabled(true);
                    if(found.isEmpty()){JOptionPane.showMessageDialog(frame,"No receivers answered. Use the address shown on the TV instead.");return;}
                    String[] options=found.stream().map(p->p.url).toArray(String[]::new);
                    String selected=(String)JOptionPane.showInputDialog(frame,"Choose a nearby Edo server:","Nearby Edo",JOptionPane.PLAIN_MESSAGE,null,options,options[0]);
                    if(selected!=null)remoteField.setText(selected);
                });
            }catch(Exception ex){SwingUtilities.invokeLater(()->{scanPeers.setEnabled(true);JOptionPane.showMessageDialog(frame,ex.getMessage());});}});
        });
        root.add(new JLabel("Local source folder:"));
        JPanel backupRow=new JPanel(new BorderLayout(8,8));backupField=new JTextField(pref.get("backup",System.getProperty("user.home")+File.separator+"Pictures"));JButton chooseBackup=new JButton("Browse…");chooseBackup.addActionListener(ev->{JFileChooser c=new JFileChooser(backupField.getText());c.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);if(c.showOpenDialog(frame)==JFileChooser.APPROVE_OPTION)backupField.setText(c.getSelectedFile().toString());});backupRow.add(backupField);backupRow.add(chooseBackup,BorderLayout.EAST);backupRow.setMaximumSize(new Dimension(900,42));root.add(backupRow);
        root.add(new JLabel("Remote destination folder:"));targetField=new JTextField(pref.get("target","DesktopBackup"));targetField.setMaximumSize(new Dimension(900,35));root.add(targetField);
        JPanel syncRow=new JPanel(new FlowLayout(FlowLayout.LEFT));JButton syncNow=new JButton("Back up now");automatic=new JCheckBox("Repeat every 10 minutes (while app is open)");syncRow.add(syncNow);syncRow.add(automatic);root.add(syncRow);
        JPanel twoWayRow=new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton pullNow=new JButton("Pull from remote Edo"),mergeNow=new JButton("Safe two-way merge");
        twoWayAuto=new JCheckBox("Auto two-way (no deletions)");
        twoWayRow.add(pullNow);twoWayRow.add(mergeNow);twoWayRow.add(twoWayAuto);root.add(twoWayRow);
        JTextArea syncLog=new JTextArea("Backups and merges only add copies. Conflicts are preserved with new names; nothing is automatically deleted or overwritten.\n");syncLog.setEditable(false);syncLog.setLineWrap(true);syncLog.setWrapStyleWord(true);JScrollPane logs=new JScrollPane(syncLog);logs.setPreferredSize(new Dimension(600,85));root.add(logs);
        Runnable performBackup=()->{
            final String host=remoteField.getText().trim(),pinCode=pinField.getText().trim(),source=backupField.getText().trim(),target=targetField.getText().trim();
            pref.put("remote",host);pref.put("backup",source);pref.put("target",target);
            syncWorkers.execute(()->{try{SyncClient client=new SyncClient(host,pinCode,new File(source),target,msg->SwingUtilities.invokeLater(()->{syncLog.append(msg+"\n");syncLog.setCaretPosition(syncLog.getDocument().getLength());}));client.run(false);}
                catch(Exception ex){SwingUtilities.invokeLater(()->syncLog.append("ERROR: "+ex.getMessage()+"\n"));}});
        };
        Runnable performPull=()->{
            final String host=remoteField.getText().trim(),pinCode=pinField.getText().trim(),source=backupField.getText().trim(),target=targetField.getText().trim();
            syncWorkers.execute(()->{try{TwoWaySync client=new TwoWaySync(host,pinCode,new File(source),target,msg->SwingUtilities.invokeLater(()->syncLog.append(msg+"\n")));
                client.pull();}catch(Exception ex){SwingUtilities.invokeLater(()->syncLog.append("ERROR: "+ex.getMessage()+"\n"));}});
        };
        Runnable performMerge=()->{
            final String host=remoteField.getText().trim(),pinCode=pinField.getText().trim(),source=backupField.getText().trim(),target=targetField.getText().trim();
            syncWorkers.execute(()->{try{TwoWaySync client=new TwoWaySync(host,pinCode,new File(source),target,msg->SwingUtilities.invokeLater(()->syncLog.append(msg+"\n")));
                client.merge();}catch(Exception ex){SwingUtilities.invokeLater(()->syncLog.append("ERROR: "+ex.getMessage()+"\n"));}});
        };
        syncNow.addActionListener(e->performBackup.run());pullNow.addActionListener(e->performPull.run());mergeNow.addActionListener(e->performMerge.run());
        scheduler.scheduleAtFixedRate(()->{if(automatic!=null&&automatic.isSelected()&&System.currentTimeMillis()-lastSync>=9*60*1000L){lastSync=System.currentTimeMillis();SwingUtilities.invokeLater(twoWayAuto!=null&&twoWayAuto.isSelected()?performMerge:performBackup);}},10,10,TimeUnit.MINUTES);
        root.add(Box.createVerticalStrut(10));
        JButton revoke=new JButton("Disconnect all devices");revoke.setAlignmentX(Component.LEFT_ALIGNMENT);root.add(revoke);revoke.addActionListener(e->{if(server!=null&&JOptionPane.showConfirmDialog(frame,"Disconnect and forget all trusted devices?","Confirm",JOptionPane.YES_NO_OPTION)==JOptionPane.YES_OPTION)server.disconnectAll();});
        start.addActionListener(e->{try{Path path=Paths.get(folderField.getText()).toRealPath();DiskRepository repo=new DiskRepository(path.toFile());
            LanServer s=new LanServer(repo,()->{InputStream in=DesktopMain.class.getResourceAsStream("/index.html");if(in==null)throw new IOException("Client page missing");return in;},new File(System.getProperty("user.home"),".dropwave-desktop"));
            int port=encrypted.isSelected()?s.startSecure(8765,TlsDesktop.context(new File(System.getProperty("user.home"),".edo-desktop"),localIp())):s.start(8765);
            server=s;s.setRequireApproval(approval.isSelected());pref.put("folder",path.toString());pref.putBoolean("approve",approval.isSelected());pref.putBoolean("tls",encrypted.isSelected());
            start.setEnabled(false);stop.setEnabled(true);folderField.setEnabled(false);approval.setEnabled(false);encrypted.setEnabled(false);
            updateStatus();
        }catch(Exception ex){JOptionPane.showMessageDialog(frame,ex.getMessage(),"Could not start",JOptionPane.ERROR_MESSAGE);}});
        stop.addActionListener(e->{if(server!=null){server.close();server=null;}start.setEnabled(true);stop.setEnabled(false);folderField.setEnabled(true);approval.setEnabled(true);encrypted.setEnabled(true);updateStatus();});
        scheduler.scheduleAtFixedRate(()->SwingUtilities.invokeLater(DesktopMain::updateStatus),2,2,TimeUnit.SECONDS);
        JScrollPane viewport=new JScrollPane(root);viewport.getVerticalScrollBar().setUnitIncrement(16);frame.add(viewport);frame.setVisible(true);
    }
    /** Human-readable help for the three primary ways to share. Does not alter transfer settings. */
    private static void showSharingGuide(){
        String text="PC to PC (no television needed)\n\n"
            +"1. On PC A, choose a folder and click Start sharing.\n"
            +"2. Copy PC A's address and PIN from this window.\n"
            +"3. On PC B, open that address in a browser and enter the PIN.\n"
            +"4. Upload PC B files in Transfer; download PC A files in Files.\n"
            +"5. If asked, approve PC B from this window.\n\n"
            +"Phone to PC\nStart Edo Desktop on the PC, then open its address in the phone browser.\n\n"
            +"Phone/PC to Google TV\nStart Edo on Google TV, scan the QR or open its address in a browser, then pair.\n\n"
            +"Same local network required. HTTP is not encrypted. "
            +"If HTTPS is enabled, verify the local certificate fingerprint first.";
        JTextArea body=new JTextArea(text,18,65);
        body.setEditable(false);body.setWrapStyleWord(true);body.setLineWrap(true);
        body.setCaretPosition(0);
        JScrollPane scroll=new JScrollPane(body);
        scroll.setPreferredSize(new Dimension(610,340));
        JOptionPane.showMessageDialog(frame,scroll,"How Edo sharing works",JOptionPane.INFORMATION_MESSAGE);
    }
    private static void updateStatus(){if(server==null||!server.isRunning()){status.setText("Sharing is off");return;}
        String addr=server.scheme()+"://"+localIp()+":"+server.port();status.setText("● ONLINE · "+addr);
        StringBuilder b=new StringBuilder("On another PC/phone, open "+addr+" (no TV needed)\nPairing PIN: "+server.pin()+"\nActive transfers: "+server.activeTransfers()+"\n");
        java.util.List<String[]> pending=server.pendingRequests();if(!pending.isEmpty()){
            b.append("\nNew pairing requests:\n");
            for(String[] p:pending){int result=JOptionPane.showConfirmDialog(frame,"Allow device: "+p[1]+"?","Edo pairing request",JOptionPane.YES_NO_OPTION);server.decide(p[0],result==JOptionPane.YES_OPTION);}
        }
        if(server.isSecure()){b.append("WebDAV: ").append(addr).append("/dav/\nUsername: edo\nPassword: ").append(server.webdavPassword()).append("\n");try{b.append("TLS certificate SHA-256: ").append(TlsDesktop.fingerprint(new File(System.getProperty("user.home"),".edo-desktop"))).append("\nBrowsers must trust this self-signed certificate.\n");}catch(Exception ex){b.append("Certificate fingerprint unavailable\n");}}
        info.setText(b.toString());
    }
}
