package com.dropwave.desktop;

import com.dropwave.tv.LanServer;
import com.dropwave.tv.UdpDiscovery;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.*;
import java.awt.geom.*;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.prefs.Preferences;

/** Edo Desktop: LAN transfer host with a responsive native desktop dashboard. */
public final class DesktopMain {
    private static final String APP_VERSION = "1.0";
    private static final Color BACK = new Color(11, 20, 28);
    private static final Color SIDE = new Color(13, 24, 32);
    private static final Color PANEL = new Color(21, 35, 45);
    private static final Color PANEL2 = new Color(27, 44, 55);
    private static final Color LINE = new Color(47, 68, 78);
    private static final Color TEXT = new Color(239, 247, 247);
    private static final Color MUTED = new Color(152, 171, 178);
    private static final Color GREEN = new Color(186, 250, 107);
    private static final Color DARK_GREEN = new Color(22, 45, 30);
    private static final Color DANGER = new Color(252, 141, 137);

    private static final Preferences pref = Preferences.userNodeForPackage(DesktopMain.class);
    private static final ExecutorService workers = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "edo-background-work");t.setDaemon(true);return t;
    });
    private static final ScheduledExecutorService poller = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "edo-status-poller");t.setDaemon(true);return t;
    });

    private static JFrame frame;
    private static LanServer server;
    private static boolean transitioning = false;
    private static JPanel screenHolder, sidebar;
    private static CardLayout screenCards;
    private static JButton shareNav, syncNav, prefsNav, startButton, stopButton, linkCopy, pinCopy, openBrowser;
    private static JTextField folderField, remoteField, pinField, backupField, targetField;
    private static JCheckBox approval, encrypted, automatic, twoWayAuto;
    private static JLabel statusChip, heroStatus, heroAddress, addressLabel, pinLabel, transfersLabel, deviceHint;
    private static JTextArea syncLog;
    private static volatile long lastSync = 0;
    private static final String OFFLINE_URL = "Start sharing to create a connection link";

    private DesktopMain() {}
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); }
            catch (Exception ignored) {}
            build();
        });
    }

    private static Font font(float size, int weight) {
        String name = "Segoe UI";
        if (!Arrays.asList(GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()).contains(name))
            name = "SansSerif";
        return new Font(name, weight, Math.round(size));
    }
    private static JLabel label(String value, int size, Color fg, int weight) {
        JLabel l = new JLabel(value);
        l.setForeground(fg);l.setFont(font(size, weight));
        return l;
    }
    private static JLabel label(String value, int size, Color fg) { return label(value, size, fg, Font.PLAIN); }
    private static Component gap(int height) { return Box.createRigidArea(new Dimension(1, height)); }
    private static JPanel stack(int spacing) {
        JPanel p = new JPanel();p.setOpaque(false);p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        return p;
    }
    private static void alignLeft(JComponent c) { c.setAlignmentX(Component.LEFT_ALIGNMENT); }
    private static JTextField input(String initial) {
        JTextField f = new JTextField(initial);
        f.setFont(font(13, Font.PLAIN));f.setBackground(PANEL2);f.setForeground(TEXT);f.setCaretColor(GREEN);
        f.setSelectionColor(new Color(74, 106, 67));f.setSelectedTextColor(TEXT);
        f.setBorder(BorderFactory.createCompoundBorder(new RoundBorder(12, LINE), new EmptyBorder(11, 13, 11, 13)));
        f.setPreferredSize(new Dimension(180, 44));
        return f;
    }
    private static JCheckBox toggle(String title, boolean initial) {
        JCheckBox c = new JCheckBox(title, initial);
        c.setFont(font(13, Font.PLAIN));c.setOpaque(false);c.setForeground(TEXT);c.setFocusPainted(false);
        c.setIcon(new ToggleIcon(false));c.setSelectedIcon(new ToggleIcon(true));c.setIconTextGap(12);
        return c;
    }
    private static JButton button(String name, boolean primary) {
        JButton b = new JButton(name);
        b.setFont(font(13, Font.BOLD));b.setForeground(primary ? BACK : TEXT);
        b.setBackground(primary ? GREEN : PANEL2);b.setOpaque(false);b.setFocusPainted(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setBorder(new EmptyBorder(12, 18, 12, 18));
        b.setUI(new javax.swing.plaf.basic.BasicButtonUI());
        b.setContentAreaFilled(false);
        b.setPreferredSize(new Dimension(Math.max(108, name.length() * 9 + 26), 43));
        b.putClientProperty("primary", primary);
        b.addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent e) { b.repaint(); }
            public void mouseExited(MouseEvent e) { b.repaint(); }
        });
        return new PaintedButton(b);
    }
    private static JPanel line(int topGap) {
        JPanel p = new JPanel(new BorderLayout());p.setOpaque(false);
        p.setBorder(new EmptyBorder(topGap, 0, 0, 0));
        JSeparator s = new JSeparator();s.setForeground(LINE);s.setBackground(LINE);p.add(s, BorderLayout.CENTER);
        return p;
    }
    private static JPanel card(LayoutManager lm) {
        JPanel p = new RoundedPanel(PANEL, 20);p.setLayout(lm);
        p.setBorder(new EmptyBorder(22, 24, 22, 24));
        return p;
    }
    private static JPanel row(Component... comps) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 9, 0));p.setOpaque(false);
        for (Component c : comps) p.add(c);return p;
    }
    private static JPanel column(String heading, String detail) {
        JPanel p = stack(0);
        JLabel h = label(heading, 14, TEXT, Font.BOLD);alignLeft(h);p.add(h);
        if (detail != null) {p.add(gap(5));JLabel d = label(detail, 12, MUTED);alignLeft(d);p.add(d);}
        return p;
    }
    private static JPanel fieldWithButton(JTextField f, JButton action) {
        JPanel p = new JPanel(new BorderLayout(10, 0));p.setOpaque(false);
        p.add(f, BorderLayout.CENTER);p.add(action, BorderLayout.EAST);
        return p;
    }
    private static JPanel content(String header, String subheading) {
        JPanel p = stack(0);p.setBorder(new EmptyBorder(28, 32, 36, 32));
        JLabel title = label(header, 27, TEXT, Font.BOLD);alignLeft(title);p.add(title);p.add(gap(7));
        JLabel detail = label(subheading, 13, MUTED);alignLeft(detail);p.add(detail);p.add(gap(25));
        return p;
    }

    private static void build() {
        new File(System.getProperty("user.home"), "EdoShare").mkdirs();
        frame = new JFrame("Edo Desktop v" + APP_VERSION + " · Every Device. One Connection.");
        try (InputStream stream = DesktopMain.class.getResourceAsStream("/edo-icon.png")) {
            if (stream != null) frame.setIconImage(ImageIO.read(stream));
        } catch (Exception ignored) {}
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        frame.setSize(1160, 810);frame.setMinimumSize(new Dimension(870, 640));frame.setLocationRelativeTo(null);
        JPanel shell = new JPanel(new BorderLayout());shell.setBackground(BACK);
        shell.add(createSidebar(), BorderLayout.WEST);
        screenCards = new CardLayout();screenHolder = new JPanel(screenCards);screenHolder.setBackground(BACK);
        screenHolder.add(scroll(createShareScreen()), "share");
        screenHolder.add(scroll(createSyncScreen()), "sync");
        screenHolder.add(scroll(createSettingsScreen()), "prefs");
        shell.add(screenHolder, BorderLayout.CENTER);
        frame.setContentPane(shell);
        navigate("share");
        updateStatus();
        poller.scheduleAtFixedRate(() -> SwingUtilities.invokeLater(DesktopMain::updateStatus), 2, 2, TimeUnit.SECONDS);
        poller.scheduleAtFixedRate(() -> SwingUtilities.invokeLater(DesktopMain::automaticSync), 10, 10, TimeUnit.MINUTES);
        frame.setVisible(true);
    }
    private static JScrollPane scroll(JPanel p) {
        JScrollPane s = new JScrollPane(p);s.setBorder(null);s.setOpaque(false);s.getViewport().setBackground(BACK);
        s.getVerticalScrollBar().setUnitIncrement(18);s.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        return s;
    }
    private static JPanel createSidebar() {
        sidebar = new JPanel(new BorderLayout());sidebar.setBackground(SIDE);
        sidebar.setPreferredSize(new Dimension(228, 0));
        sidebar.setBorder(new EmptyBorder(28, 14, 20, 14));
        JPanel top = stack(0);
        JPanel logoRow = row();logoRow.setBorder(new EmptyBorder(0, 10, 0, 0));
        try(InputStream in = DesktopMain.class.getResourceAsStream("/edo-icon.png")) {
            if(in != null) {Image im = ImageIO.read(in).getScaledInstance(40, 40, Image.SCALE_SMOOTH);logoRow.add(new JLabel(new ImageIcon(im)));}
        } catch(IOException ignored) {}
        JPanel words = stack(0);words.add(label("edo.", 29, TEXT, Font.BOLD));words.add(label("LOCAL FILE SHARING  ·  v" + APP_VERSION, 9, MUTED, Font.BOLD));logoRow.add(words);
        alignLeft(logoRow);top.add(logoRow);top.add(gap(44));
        JLabel menu = label("WORKSPACE", 10, MUTED, Font.BOLD);menu.setBorder(new EmptyBorder(0, 13, 8, 0));alignLeft(menu);top.add(menu);
        shareNav = nav("▣   Share files", "share");syncNav=nav("⇄   Backup & sync", "sync");prefsNav=nav("⚙   Settings", "prefs");
        alignLeft(shareNav);alignLeft(syncNav);alignLeft(prefsNav);
        top.add(shareNav);top.add(gap(7));top.add(syncNav);top.add(gap(7));top.add(prefsNav);
        sidebar.add(top, BorderLayout.NORTH);
        JPanel bottom = stack(0);
        JButton guide = nav("?   How it works", "guide");alignLeft(guide);bottom.add(guide);bottom.add(gap(22));
        JPanel pill = new RoundedPanel(new Color(25, 45, 41), 14);pill.setLayout(new BorderLayout());pill.setBorder(new EmptyBorder(12, 13, 12, 10));
        pill.add(label("●  LOCAL-FIRST", 10, GREEN, Font.BOLD),BorderLayout.NORTH);
        pill.add(label("Your files stay on your network", 10, MUTED),BorderLayout.SOUTH);
        alignLeft(pill);bottom.add(pill);
        sidebar.add(bottom,BorderLayout.SOUTH);
        return sidebar;
    }
    private static JButton nav(String text, String dest) {
        JButton b = new JButton(text);b.setFont(font(13, Font.BOLD));b.setHorizontalAlignment(SwingConstants.LEFT);
        b.setForeground(MUTED);b.setContentAreaFilled(false);b.setOpaque(false);
        b.setFocusPainted(false);b.setBorder(new EmptyBorder(13,14,13,8));b.setMaximumSize(new Dimension(Integer.MAX_VALUE,47));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        if(dest.equals("guide")) b.addActionListener(e->showSharingGuide());else b.addActionListener(e->navigate(dest));
        return b;
    }
    private static void navigate(String name) {
        screenCards.show(screenHolder,name);
        for(JButton n: new JButton[]{shareNav,syncNav,prefsNav}) {
            boolean selected=(name.equals("share")&&n==shareNav)||(name.equals("sync")&&n==syncNav)||(name.equals("prefs")&&n==prefsNav);
            n.setForeground(selected? GREEN:MUTED);
            n.setOpaque(selected);n.setBackground(selected?new Color(31,50,48):SIDE);
        }
    }
    private static JPanel createShareScreen() {
        JPanel p = content("Share your files", "One connection, all your devices. Secure local sharing without cloud uploads.");
        JPanel hero = new RoundedPanel(new Color(25, 49, 49), 22);
        hero.setLayout(new BorderLayout(18, 12));hero.setBorder(new EmptyBorder(25, 28, 25, 27));
        JPanel heroLeft = stack(0);
        JLabel eyebrow=label("YOUR SHARING SPACE", 10, GREEN, Font.BOLD);alignLeft(eyebrow);heroLeft.add(eyebrow);heroLeft.add(gap(10));
        JLabel mainTitle=label("Ready when you are.", 26, TEXT, Font.BOLD);alignLeft(mainTitle);heroLeft.add(mainTitle);heroLeft.add(gap(10));
        JLabel subline1=label("Start Edo here. Connect from another PC or phone", 13, MUTED);
        JLabel subline2=label("using its browser. Keep this window open.", 13, MUTED);
        alignLeft(subline1);alignLeft(subline2);heroLeft.add(subline1);heroLeft.add(subline2);heroLeft.add(gap(20));
        startButton = button("▶  Start sharing", true);stopButton = button("■  Stop", false);
        stopButton.setEnabled(false);JPanel heroButtons=row(startButton,stopButton);alignLeft(heroButtons);heroLeft.add(heroButtons);
        JPanel graphic = new NetworkGraphic();graphic.setPreferredSize(new Dimension(178, 145));graphic.setOpaque(false);
        hero.add(heroLeft,BorderLayout.CENTER);hero.add(graphic,BorderLayout.EAST);
        alignLeft(hero);p.add(hero);p.add(gap(21));

        JPanel connection = card(new BorderLayout(0,18));
        JPanel ctop = new JPanel(new BorderLayout());ctop.setOpaque(false);
        ctop.add(column("Your connection", "Share this address with another device on your network"), BorderLayout.WEST);
        statusChip = label("●  OFFLINE", 11, MUTED, Font.BOLD);ctop.add(statusChip,BorderLayout.EAST);
        connection.add(ctop,BorderLayout.NORTH);
        JPanel connectionBody = new JPanel(new GridLayout(1,2,18,0));connectionBody.setOpaque(false);
        JPanel addrCard = new RoundedPanel(PANEL2,15);addrCard.setLayout(new BorderLayout(0,12));addrCard.setBorder(new EmptyBorder(17,18,17,18));
        addrCard.add(label("CONNECTION ADDRESS",10,MUTED,Font.BOLD),BorderLayout.NORTH);
        addressLabel=label(OFFLINE_URL,15,TEXT,Font.BOLD);addressLabel.setToolTipText(OFFLINE_URL);
        addrCard.add(addressLabel,BorderLayout.CENTER);
        JPanel addrButtons=row();linkCopy=button("Copy address",false);openBrowser=button("Open in browser",false);
        addrButtons.add(linkCopy);addrButtons.add(openBrowser);addrCard.add(addrButtons,BorderLayout.SOUTH);
        JPanel pinCard = new RoundedPanel(PANEL2,15);pinCard.setLayout(new BorderLayout(0,9));pinCard.setBorder(new EmptyBorder(17,18,17,18));
        pinCard.add(label("PAIRING PIN",10,MUTED,Font.BOLD),BorderLayout.NORTH);
        pinLabel=label("— — — — — —",29,GREEN,Font.BOLD);pinLabel.setFont(new Font(Font.MONOSPACED,Font.BOLD,29));
        pinCard.add(pinLabel,BorderLayout.CENTER);pinCopy=button("Copy PIN",false);JPanel pb=row(pinCopy);pinCard.add(pb,BorderLayout.SOUTH);
        connectionBody.add(addrCard);connectionBody.add(pinCard);
        connection.add(connectionBody,BorderLayout.CENTER);
        JPanel cfoot=new JPanel(new BorderLayout());cfoot.setOpaque(false);
        transfersLabel=label("Active transfers  0",12,MUTED);cfoot.add(transfersLabel,BorderLayout.WEST);
        deviceHint=label("Waiting for sharing to start",12,MUTED);cfoot.add(deviceHint,BorderLayout.EAST);
        connection.add(cfoot,BorderLayout.SOUTH);
        alignLeft(connection);p.add(connection);p.add(gap(21));

        JPanel folderCard=card(new BorderLayout(0,15));
        folderCard.add(column("Folder to share", "Only selected files become accessible to paired devices."),BorderLayout.NORTH);
        folderField=input(pref.get("folder",System.getProperty("user.home")+File.separator+"EdoShare"));
        JButton browse=button("Choose folder",false);browse.addActionListener(e->selectFolder(folderField));
        folderCard.add(fieldWithButton(folderField,browse),BorderLayout.CENTER);
        alignLeft(folderCard);p.add(folderCard);p.add(gap(21));

        JPanel steps=card(new GridLayout(1,3,13,0));
        for(String[] step:new String[][]{{"01", "Choose a folder", "Pick what you want to share"},{"02", "Start sharing", "Copy the address and PIN"},{"03", "Connect & transfer", "Open Edo on another device"}}){
            JPanel bit=stack(0);bit.add(label(step[0],13,GREEN,Font.BOLD));bit.add(gap(8));bit.add(label(step[1],14,TEXT,Font.BOLD));bit.add(gap(5));bit.add(label(step[2],11,MUTED));steps.add(bit);
        }
        alignLeft(steps);p.add(steps);

        startButton.addActionListener(e->startSharing());stopButton.addActionListener(e->stopSharing());
        linkCopy.addActionListener(e->{if(server!=null&&server.isRunning())copy(connectionUrl(),"Address copied");});
        pinCopy.addActionListener(e->{if(server!=null&&server.isRunning())copy(server.pin(),"PIN copied");});
        openBrowser.addActionListener(e->{if(server!=null&&server.isRunning())openUrl(connectionUrl());});
        return p;
    }
    private static JPanel createSyncScreen() {
        JPanel p=content("Backup & sync", "Back up folders or safely merge files with another Edo host.");
        JPanel connect=card(new BorderLayout(0,15));
        connect.add(column("Connect to another Edo", "Paste the remote computer or TV address and its pairing PIN."),BorderLayout.NORTH);
        remoteField=input(pref.get("remote","http://192.168.1.10:8765"));pinField=input("");pinField.setColumns(7);pinField.setToolTipText("Remote Edo pairing PIN");
        JPanel connection=new JPanel(new BorderLayout(12,0));connection.setOpaque(false);connection.add(remoteField,BorderLayout.CENTER);
        JPanel pinW=new JPanel(new BorderLayout());pinW.setOpaque(false);pinW.add(pinField);pinW.setPreferredSize(new Dimension(125,44));connection.add(pinW,BorderLayout.EAST);
        connect.add(connection,BorderLayout.CENTER);
        JButton discover=button("Discover nearby Edo devices",false);connect.add(row(discover),BorderLayout.SOUTH);
        discover.addActionListener(e->discover(discover));alignLeft(connect);p.add(connect);p.add(gap(20));

        JPanel folders=card(new BorderLayout(0,15));
        folders.add(column("Synchronization folders", "Select your local source and a destination folder on the remote device."),BorderLayout.NORTH);
        JPanel fields=stack(0);
        JLabel local=label("LOCAL SOURCE",10,MUTED,Font.BOLD);alignLeft(local);fields.add(local);fields.add(gap(7));
        backupField=input(pref.get("backup",System.getProperty("user.home")+File.separator+"Pictures"));
        JButton select=button("Choose folder",false);select.addActionListener(e->selectFolder(backupField));
        JPanel source=fieldWithButton(backupField,select);alignLeft(source);fields.add(source);fields.add(gap(17));
        JLabel dest=label("REMOTE FOLDER NAME",10,MUTED,Font.BOLD);alignLeft(dest);fields.add(dest);fields.add(gap(7));
        targetField=input(pref.get("target","DesktopBackup"));alignLeft(targetField);fields.add(targetField);
        folders.add(fields,BorderLayout.CENTER);alignLeft(folders);p.add(folders);p.add(gap(20));

        JPanel run=card(new BorderLayout(0,13));run.add(column("Run a transfer", "Safe merge preserves conflicting copies and avoids automatic deletion."),BorderLayout.NORTH);
        JButton push=button("Back up now",true),pull=button("Pull from remote",false),merge=button("Two-way merge",false);
        JPanel actions=row(push,pull,merge);run.add(actions,BorderLayout.CENTER);
        JPanel modes=stack(0);automatic=toggle("Repeat backup every 10 minutes while Edo is open",false);
        twoWayAuto=toggle("Use two-way merge for automatic backups",false);alignLeft(automatic);alignLeft(twoWayAuto);
        modes.add(automatic);modes.add(gap(7));modes.add(twoWayAuto);run.add(modes,BorderLayout.SOUTH);
        alignLeft(run);p.add(run);p.add(gap(20));

        JPanel logs=card(new BorderLayout(0,12));logs.add(column("Transfer activity", "Progress and any errors appear here."),BorderLayout.NORTH);
        syncLog=new JTextArea("Ready to sync. Transfers never automatically delete files.\n");
        syncLog.setEditable(false);syncLog.setForeground(TEXT);syncLog.setBackground(PANEL2);syncLog.setFont(new Font(Font.MONOSPACED,Font.PLAIN,12));
        syncLog.setLineWrap(true);syncLog.setWrapStyleWord(true);syncLog.setBorder(new EmptyBorder(12,13,12,13));
        JScrollPane sc=new JScrollPane(syncLog);sc.setBorder(new RoundBorder(12,LINE));sc.setPreferredSize(new Dimension(600,120));
        logs.add(sc,BorderLayout.CENTER);alignLeft(logs);p.add(logs);

        push.addActionListener(e->performSync("backup"));pull.addActionListener(e->performSync("pull"));merge.addActionListener(e->performSync("merge"));
        return p;
    }
    private static JPanel createSettingsScreen() {
        JPanel p=content("Settings", "Choose how Edo keeps your devices connected and your files protected.");
        JPanel security=card(new BorderLayout(0,17));security.add(column("Connection security", "These settings are applied when sharing starts."),BorderLayout.NORTH);
        approval=toggle("Ask me before accepting a new device",pref.getBoolean("approve",true));
        encrypted=toggle("Use encrypted HTTPS (advanced)",pref.getBoolean("tls",false));
        JPanel toggles=stack(0);alignLeft(approval);alignLeft(encrypted);toggles.add(approval);toggles.add(gap(13));toggles.add(encrypted);
        security.add(toggles,BorderLayout.CENTER);
        security.add(label("HTTPS uses a local certificate. Your browser may require you to trust it.",11,MUTED),BorderLayout.SOUTH);
        alignLeft(security);p.add(security);p.add(gap(20));
        JPanel trust=card(new BorderLayout(0,13));trust.add(column("Paired devices", "Revoke access for devices that were previously trusted."),BorderLayout.NORTH);
        JButton revoke=button("Disconnect & forget all devices",false);revoke.addActionListener(e->{if(server==null){message("Start sharing first to manage trusted devices.");return;}
            if(JOptionPane.showConfirmDialog(frame,"Forget all trusted devices? They will need to pair again.","Disconnect devices",JOptionPane.YES_NO_OPTION,JOptionPane.WARNING_MESSAGE)==JOptionPane.YES_OPTION){server.disconnectAll();message("All paired devices were disconnected.");}});
        trust.add(row(revoke),BorderLayout.CENTER);alignLeft(trust);p.add(trust);p.add(gap(20));
        JPanel about=card(new BorderLayout(0,10));about.add(column("About Edo", "Every Device. One Connection."),BorderLayout.NORTH);
        JPanel text=stack(0);text.add(label("Edo Desktop v" + APP_VERSION + "  ·  Desktop sharing over your local network.",12,MUTED));text.add(gap(5));
        text.add(label("HTTP is not encrypted. Avoid public Wi-Fi and port forwarding.",12,MUTED));
        about.add(text,BorderLayout.CENTER);JButton help=button("Open sharing guide",false);help.addActionListener(e->showSharingGuide());about.add(row(help),BorderLayout.SOUTH);
        alignLeft(about);p.add(about);
        return p;
    }

    private static void selectFolder(JTextField text) {
        JFileChooser chooser=new JFileChooser(text.getText());chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setDialogTitle("Select a folder to share");
        if(chooser.showOpenDialog(frame)==JFileChooser.APPROVE_OPTION)text.setText(chooser.getSelectedFile().getAbsolutePath());
    }
    private static String localIp() {
        try {
            Enumeration<NetworkInterface> list=NetworkInterface.getNetworkInterfaces();
            while(list.hasMoreElements()){
                NetworkInterface network=list.nextElement();if(!network.isUp()||network.isLoopback()||network.isVirtual())continue;
                Enumeration<InetAddress> addrs=network.getInetAddresses();
                while(addrs.hasMoreElements()) {InetAddress addr=addrs.nextElement();if(addr instanceof Inet4Address&&addr.isSiteLocalAddress())return addr.getHostAddress();}
            }
        }catch(Exception ignored){}
        return "127.0.0.1";
    }
    private static String connectionUrl(){LanServer s=server;return s==null?"":s.scheme()+"://"+localIp()+":"+s.port();}
    private static void startSharing(){
        if(transitioning||server!=null)return;
        final String folder=folderField.getText().trim();final boolean useHttps=encrypted.isSelected();final boolean requireApproval=approval.isSelected();
        transitioning=true;startButton.setText("Starting...");startButton.setEnabled(false);
        workers.execute(()->{
            LanServer result=null;
            try {
                Path path=Paths.get(folder).toRealPath();if(!Files.isDirectory(path))throw new IOException("Choose a folder that exists.");
                DiskRepository repo=new DiskRepository(path.toFile());
                result=new LanServer(repo,()->{InputStream in=DesktopMain.class.getResourceAsStream("/index.html");if(in==null)throw new IOException("Transfer page missing");return in;},
                    new File(System.getProperty("user.home"),".dropwave-desktop"));
                if(useHttps)result.startSecure(8765,TlsDesktop.context(new File(System.getProperty("user.home"),".edo-desktop"),localIp()));
                else result.start(8765);
                result.setRequireApproval(requireApproval);
                final LanServer running=result;
                SwingUtilities.invokeLater(()->{
                    server=running;pref.put("folder",path.toString());pref.putBoolean("approve",requireApproval);pref.putBoolean("tls",useHttps);
                    folderField.setEnabled(false);approval.setEnabled(false);encrypted.setEnabled(false);
                    transitioning=false;updateStatus();
                });
            }catch(Exception ex){
                if(result!=null)try{result.close();}catch(Exception ignored){}
                SwingUtilities.invokeLater(()->{transitioning=false;updateStatus();JOptionPane.showMessageDialog(frame,
                    "Edo couldn't start sharing.\n\n"+ex.getMessage(),"Sharing unavailable",JOptionPane.ERROR_MESSAGE);});
            }
        });
    }
    private static void stopSharing(){
        if(transitioning||server==null)return;
        transitioning=true;stopButton.setEnabled(false);
        LanServer current=server;server=null;
        workers.execute(()->{
            try{current.close();}catch(Exception ignored){}
            SwingUtilities.invokeLater(()->{transitioning=false;folderField.setEnabled(true);approval.setEnabled(true);encrypted.setEnabled(true);updateStatus();});
        });
    }
    private static void updateStatus(){
        if(startButton==null)return;
        LanServer s=server;boolean running=s!=null&&s.isRunning();
        startButton.setEnabled(!transitioning&&!running);stopButton.setEnabled(!transitioning&&running);
        startButton.setText(transitioning?"Please wait...":running?"●  Sharing is on":"▶  Start sharing");
        if(!running){
            statusChip.setText(transitioning?"●  WORKING":"●  OFFLINE");statusChip.setForeground(MUTED);
            addressLabel.setText(OFFLINE_URL);addressLabel.setFont(font(12,Font.PLAIN));pinLabel.setText("— — — — — —");
            transfersLabel.setText("Active transfers  0");deviceHint.setText("Waiting for sharing to start");
            linkCopy.setEnabled(false);pinCopy.setEnabled(false);openBrowser.setEnabled(false);
            return;
        }
        String address=connectionUrl();statusChip.setText("●  SHARING LIVE");statusChip.setForeground(GREEN);
        addressLabel.setText(address);addressLabel.setFont(font(15,Font.BOLD));addressLabel.setToolTipText(address);
        pinLabel.setText(s.pin());transfersLabel.setText("Active transfers  "+s.activeTransfers());
        deviceHint.setText(s.isSecure()?"HTTPS enabled":"Local network connection");
        linkCopy.setEnabled(true);pinCopy.setEnabled(true);openBrowser.setEnabled(true);
        List<String[]> pending=s.pendingRequests();
        for(String[] request:pending){
            int reply=JOptionPane.showConfirmDialog(frame,
                "Allow this device to connect?\n\n"+request[1],"Edo pairing request",JOptionPane.YES_NO_OPTION,JOptionPane.QUESTION_MESSAGE);
            s.decide(request[0],reply==JOptionPane.YES_OPTION);
        }
    }
    private static void copy(String value,String result) {
        try{Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(value),null);
            deviceHint.setText(result);}
        catch(Exception e){message("Couldn't copy to clipboard: "+e.getMessage());}
    }
    private static void openUrl(String url) {
        try{if(!Desktop.isDesktopSupported())throw new IOException("Desktop browser integration is unavailable");
            Desktop.getDesktop().browse(URI.create(url));}catch(Exception ex){message("Please open this address in your browser:\n"+url);}
    }
    private static void message(String content) {JOptionPane.showMessageDialog(frame,content,"Edo Desktop",JOptionPane.INFORMATION_MESSAGE);}
    private static void showSharingGuide(){
        JTextArea help=new JTextArea(
            "PC TO PC\n\n1. On PC A, choose a folder and click Start sharing.\n"
           +"2. Copy the address and PIN shown on the Share files screen.\n"
           +"3. On PC B, open the address in any compatible browser.\n"
           +"4. Enter the pairing PIN and approve the device on PC A if asked.\n"
           +"5. Use Transfer to upload to PC A; use Files to download from PC A.\n\n"
           +"PHONE TO PC\n\nStart sharing on the PC; visit its address in the phone browser.\n\n"
           +"PHONE / PC TO GOOGLE TV\n\nStart the Edo TV app, then scan its QR code or open its address.\n\n"
           +"All devices need a reachable local network. HTTP is not encrypted.\n"
           +"If using HTTPS, you may need to trust the host certificate.");
        help.setFont(font(13,Font.PLAIN));help.setForeground(TEXT);help.setBackground(PANEL);
        help.setEditable(false);help.setWrapStyleWord(true);help.setLineWrap(true);help.setCaretPosition(0);
        JScrollPane sc=new JScrollPane(help);sc.setPreferredSize(new Dimension(590,370));
        JOptionPane.showMessageDialog(frame,sc,"How Edo works",JOptionPane.PLAIN_MESSAGE);
    }
    private static void discover(JButton button) {
        button.setEnabled(false);button.setText("Searching...");
        workers.execute(()->{
            try {
                List<UdpDiscovery.Peer> found=UdpDiscovery.scan(1800);
                SwingUtilities.invokeLater(()->{
                    button.setEnabled(true);button.setText("Discover nearby Edo devices");
                    if(found.isEmpty()){message("No Edo devices found. Enter the remote address manually.");return;}
                    String[] options=found.stream().map(x->x.url).toArray(String[]::new);
                    String selected=(String)JOptionPane.showInputDialog(frame,"Choose an Edo device:","Nearby devices",
                        JOptionPane.PLAIN_MESSAGE,null,options,options[0]);if(selected!=null)remoteField.setText(selected);
                });
            }catch(Exception ex){SwingUtilities.invokeLater(()->{button.setEnabled(true);button.setText("Discover nearby Edo devices");message("Discovery failed: "+ex.getMessage());});}
        });
    }
    private static void appendLog(String msg) {
        if(syncLog!=null){syncLog.append(msg+"\n");syncLog.setCaretPosition(syncLog.getDocument().getLength());}
    }
    private static void performSync(String operation){
        final String host=remoteField.getText().trim(),pin=pinField.getText().trim();
        final String source=backupField.getText().trim(),target=targetField.getText().trim();
        pref.put("remote",host);pref.put("backup",source);pref.put("target",target);
        appendLog("Starting "+operation+"...");
        workers.execute(()->{
            try{
                if(operation.equals("backup"))new SyncClient(host,pin,new File(source),target,msg->SwingUtilities.invokeLater(()->appendLog(msg))).run(false);
                else {TwoWaySync client=new TwoWaySync(host,pin,new File(source),target,msg->SwingUtilities.invokeLater(()->appendLog(msg)));
                    if(operation.equals("pull"))client.pull();else client.merge();}
                SwingUtilities.invokeLater(()->appendLog("Finished "+operation+"."));
            }catch(Exception e){SwingUtilities.invokeLater(()->appendLog("ERROR: "+e.getMessage()));}
        });
    }
    private static void automaticSync(){
        if(automatic!=null&&automatic.isSelected()&&System.currentTimeMillis()-lastSync>=9*60*1000L){
            lastSync=System.currentTimeMillis();performSync(twoWayAuto!=null&&twoWayAuto.isSelected()?"merge":"backup");
        }
    }

    private static class RoundBorder extends javax.swing.border.AbstractBorder {
        private final int radius;private final Color color;
        RoundBorder(int radius,Color color){this.radius=radius;this.color=color;}
        @Override public void paintBorder(Component c,Graphics g,int x,int y,int w,int h){
            Graphics2D g2=(Graphics2D)g.create();g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(color);g2.drawRoundRect(x,y,w-1,h-1,radius,radius);g2.dispose();}
    }
    private static class RoundedPanel extends JPanel {
        private final Color color;private final int round;
        RoundedPanel(Color color,int round){this.color=color;this.round=round;setOpaque(false);}
        @Override protected void paintComponent(Graphics g){Graphics2D g2=(Graphics2D)g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(color);g2.fillRoundRect(0,0,getWidth(),getHeight(),round,round);g2.dispose();super.paintComponent(g);}
    }
    private static class ToggleIcon implements Icon {
        private final boolean checked;ToggleIcon(boolean on){checked=on;}
        public int getIconWidth(){return 21;}public int getIconHeight(){return 21;}
        public void paintIcon(Component c,Graphics g,int x,int y){Graphics2D g2=(Graphics2D)g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(checked?GREEN:PANEL2);g2.fillRoundRect(x,y,20,20,7,7);
            g2.setColor(checked?GREEN:LINE);g2.drawRoundRect(x,y,19,19,7,7);
            if(checked){g2.setColor(BACK);g2.setStroke(new BasicStroke(2f,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));
                g2.drawLine(x+5,y+10,x+9,y+14);g2.drawLine(x+9,y+14,x+16,y+6);}
            g2.dispose();}
    }
    private static class PaintedButton extends JButton {
        private final JButton source;
        PaintedButton(JButton s){super(s.getText());source=s;
            setFont(s.getFont());setForeground(s.getForeground());setOpaque(false);setContentAreaFilled(false);
            setFocusPainted(false);setBorder(s.getBorder());setCursor(s.getCursor());setPreferredSize(s.getPreferredSize());
            putClientProperty("primary",s.getClientProperty("primary"));
        }
        @Override protected void paintComponent(Graphics g){Graphics2D g2=(Graphics2D)g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
            boolean primary=Boolean.TRUE.equals(getClientProperty("primary"));
            Color base=primary?GREEN:new Color(48,70,81);
            if(!isEnabled())base=new Color(53,67,72);
            else if(getModel().isPressed())base=primary?new Color(142,203,72):new Color(59,78,89);
            else if(getModel().isRollover())base=primary?new Color(211,255,150):new Color(67,88,100);
            g2.setColor(base);g2.fillRoundRect(0,0,getWidth(),getHeight(),13,13);g2.dispose();super.paintComponent(g);}
    }
    private static class NetworkGraphic extends JPanel {
        @Override protected void paintComponent(Graphics g){super.paintComponent(g);
            Graphics2D q=(Graphics2D)g.create();q.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
            int w=getWidth(),h=getHeight();q.setStroke(new BasicStroke(2f,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));
            q.setColor(new Color(70,106,91));q.drawLine(45,h/2,w-39,h/2);q.drawLine(w/2,h/2-28,w/2,h/2+28);
            q.setColor(GREEN);q.fillOval(w/2-7,h/2-7,14,14);
            q.setColor(new Color(45,76,65));q.fillRoundRect(5,h/2-36,58,73,13,13);
            q.fillRoundRect(w-62,h/2-27,50,53,11,11);
            q.setColor(new Color(188,229,199));q.drawRoundRect(12,h/2-28,44,49,5,5);
            q.drawLine(21,h/2+27,49,h/2+27);
            q.drawRoundRect(w-52,h/2-20,30,40,5,5);
            q.setColor(GREEN);q.fillOval(31,h/2-7,6,6);q.fillOval(w-39,h/2-3,6,6);
            q.dispose();}
    }
}