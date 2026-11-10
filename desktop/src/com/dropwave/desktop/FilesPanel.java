package com.dropwave.desktop;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.event.*;
import java.io.IOException;
import java.nio.file.*;
import java.text.DateFormat;
import java.util.List;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

/** In-app file manager for Edo's selected shared directory. Does not expose the computer's other folders. */
public final class FilesPanel extends JPanel {
    private static final Color BACK = new Color(16,21,26), PANEL = new Color(25,33,39),
        PANEL2 = new Color(36,48,57), LINE = new Color(52,67,75),
        TEXT = new Color(242,246,247), MUTED = new Color(172,190,199),
        GREEN = new Color(163,229,192), DANGER = new Color(255,146,151);
    private final Supplier<Path> sharedFolder;
    private final ExecutorService io = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r,"edo-file-browser-io");t.setDaemon(true);return t;
    });
    private SharedFileBrowser browser;
    private Path current;
    private List<SharedFileBrowser.Entry> entries = new ArrayList<>();
    private List<Path> clipboard = List.of();
    private boolean moveClipboard = false;
    private boolean mutating = false;
    private long refreshVersion = 0;
    private final FileTableModel model = new FileTableModel();
    private final JTable table = new JTable(model);
    private final TableRowSorter<FileTableModel> sorter = new TableRowSorter<>(model);
    private final JTextField search = input("", "Search this folder...");
    private final JLabel pathLabel = label("Choose a folder on Share files",12,MUTED);
    private final JLabel status = label("Select the folder you want to share.",12,MUTED);
    private final JLabel count = label("0 items",12,MUTED);
    private final JLabel selection = label("Select a file to view its details",12,MUTED);
    private final JButton home = btn("Home",false), up = btn("Up",false), refresh = btn("Refresh",false),
        add = btn("Import files",true), folder = btn("New folder",false), open = btn("Open",false),
        rename = btn("Rename",false), copy = btn("Copy",false), cut = btn("Move",false),
        paste = btn("Paste",false), delete = btn("Delete",false);

    public FilesPanel(Supplier<Path> sharedFolder) {
        super(new BorderLayout(0,11));
        this.sharedFolder = sharedFolder;
        setBackground(BACK);setBorder(new EmptyBorder(22,25,22,25));
        add(createHeader(), BorderLayout.NORTH);
        add(createBrowser(), BorderLayout.CENTER);
        add(createBottom(), BorderLayout.SOUTH);
        configureTable();
        installActions();
        updateControls();
    }

    private static Font font(int size, int style) {
        return new Font("Segoe UI",style,size);
    }
    private static JLabel label(String content,int size,Color color) {
        JLabel l=new JLabel(content);l.setForeground(color);l.setFont(font(size,Font.PLAIN));return l;
    }
    private static JTextField input(String value,String hint) {
        JTextField f = new JTextField(value);
        f.putClientProperty("JTextField.placeholderText",hint);
        f.setFont(font(13,Font.PLAIN));f.setBackground(PANEL2);f.setForeground(TEXT);
        f.setCaretColor(GREEN);f.setSelectionColor(new Color(54,103,88));
        f.setBorder(BorderFactory.createCompoundBorder(new RoundedBorder(11),
            new EmptyBorder(8,11,8,11)));
        f.setToolTipText(hint);
        return f;
    }
    private static JButton btn(String title,boolean primary){
        JButton b=new JButton(title);b.setForeground(primary?BACK:TEXT);
        b.setBackground(primary?GREEN:PANEL2);b.setFont(font(13,Font.BOLD));
        b.setBorder(BorderFactory.createCompoundBorder(new RoundedBorder(10),
                new EmptyBorder(8,11,8,11)));
        b.setFocusPainted(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return b;
    }
    private JPanel createHeader(){
        JPanel p = new JPanel(new BorderLayout(0,11));p.setOpaque(false);
        JPanel title = new JPanel(new BorderLayout(0,5));title.setOpaque(false);
        JLabel head=label("Files",25,TEXT);head.setFont(font(25,Font.BOLD));title.add(head,BorderLayout.NORTH);
        title.add(label("Browse and manage your shared folder.",12,MUTED),BorderLayout.SOUTH);
        p.add(title,BorderLayout.NORTH);
        JPanel bar=new JPanel(new BorderLayout(8,0));bar.setOpaque(false);
        JPanel nav = new JPanel(new FlowLayout(FlowLayout.LEFT,5,0));nav.setOpaque(false);
        nav.add(home);nav.add(up);nav.add(refresh);
        bar.add(nav,BorderLayout.WEST);
        pathLabel.setFont(font(12,Font.BOLD));pathLabel.setHorizontalAlignment(SwingConstants.RIGHT);
        pathLabel.setToolTipText("Current directory relative to your selected shared folder");
        bar.add(pathLabel,BorderLayout.CENTER);
        p.add(bar,BorderLayout.SOUTH);
        return p;
    }
    private JPanel createBrowser(){
        JPanel p=new RoundedPanel(new BorderLayout(0,10));p.setBackground(PANEL);
        p.setBorder(new EmptyBorder(14,14,14,14));
        JPanel commands=new JPanel(new BorderLayout(10,0));commands.setOpaque(false);
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT,5,0));left.setOpaque(false);
        left.add(add);left.add(folder);
        commands.add(left,BorderLayout.WEST);
        search.setPreferredSize(new Dimension(140,36));
        JPanel searchBox=new JPanel(new BorderLayout(8,0));searchBox.setOpaque(false);
        JLabel searchCaption=label("SEARCH",10,GREEN);searchCaption.setFont(font(10,Font.BOLD));
        searchBox.add(searchCaption,BorderLayout.WEST);searchBox.add(search,BorderLayout.CENTER);
        commands.add(searchBox,BorderLayout.CENTER);
        p.add(commands,BorderLayout.NORTH);
        JPanel inner = new JPanel(new BorderLayout());inner.setOpaque(false);
        JScrollPane sc=new JScrollPane(table);
        sc.setBorder(new RoundedBorder(11));
        sc.getViewport().setBackground(PANEL);
        sc.getVerticalScrollBar().setUnitIncrement(20);
        inner.add(sc,BorderLayout.CENTER);
        JPanel below = new JPanel(new BorderLayout(12,0));below.setOpaque(false);
        below.setBorder(new EmptyBorder(8,0,0,0));
        JPanel actions=new JPanel(new FlowLayout(FlowLayout.LEFT,5,0));actions.setOpaque(false);
        JButton more=btn("File actions ▾",false);
        JPopupMenu menu=new JPopupMenu();
        menuItem(menu,"Open",this::openSelection);
        menuItem(menu,"Rename",this::renameSelection);
        menuItem(menu,"Copy",()->setClipboard(false));
        menuItem(menu,"Move",()->setClipboard(true));
        menuItem(menu,"Delete…",this::deleteSelection);
        more.addActionListener(e->{
            boolean[] enabled={open.isEnabled(),rename.isEnabled(),copy.isEnabled(),cut.isEnabled(),delete.isEnabled()};
            for(int i=0;i<enabled.length;i++)menu.getComponent(i).setEnabled(enabled[i]);
            menu.show(more,0,more.getHeight());
        });
        actions.add(more);actions.add(paste);
        below.add(actions,BorderLayout.WEST);
        selection.setHorizontalAlignment(SwingConstants.RIGHT);
        below.add(selection,BorderLayout.CENTER);
        inner.add(below,BorderLayout.SOUTH);
        p.add(inner,BorderLayout.CENTER);
        return p;
    }
    private JPanel createBottom(){
        JPanel p=new JPanel(new BorderLayout(8,7));p.setOpaque(false);
        p.add(status,BorderLayout.CENTER);p.add(count,BorderLayout.EAST);
        JLabel hint = label("Double-click to open · Drag files here to import · Right-click for actions",11,MUTED);
        p.add(hint,BorderLayout.SOUTH);
        return p;
    }
    /** Soft-surface browser panel with rounded corners and a subtle outline. */
    private static final class RoundedPanel extends JPanel {
        RoundedPanel(LayoutManager layout) { super(layout); setOpaque(false); }
        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2=(Graphics2D)g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(PANEL);g2.fillRoundRect(0,0,getWidth(),getHeight(),14,14);
            g2.setColor(LINE);g2.drawRoundRect(0,0,getWidth()-1,getHeight()-1,14,14);
            g2.dispose();super.paintComponent(g);
        }
    }
    private static final class RoundedBorder extends javax.swing.border.AbstractBorder {
        private final int radius;
        RoundedBorder(int radius) { this.radius=radius; }
        @Override public void paintBorder(Component c,Graphics g,int x,int y,int w,int h) {
            Graphics2D g2=(Graphics2D)g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(LINE);g2.drawRoundRect(x,y,w-1,h-1,radius,radius);g2.dispose();
        }
    }

    private void configureTable(){
        table.setFont(font(13,Font.PLAIN));table.setForeground(TEXT);table.setBackground(PANEL);
        table.setRowHeight(39);table.setShowGrid(false);
        table.setIntercellSpacing(new Dimension(0,1));
        table.setSelectionBackground(new Color(45,92,77));table.setSelectionForeground(TEXT);
        table.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        table.setFillsViewportHeight(true);table.setAutoCreateColumnsFromModel(true);
        table.setRowSorter(sorter);
        JTableHeader h=table.getTableHeader();h.setBackground(PANEL2);h.setForeground(MUTED);
        h.setFont(font(11,Font.BOLD));h.setPreferredSize(new Dimension(0,33));
        DefaultTableCellRenderer render=new DefaultTableCellRenderer(){
            @Override public Component getTableCellRendererComponent(JTable t,Object value,boolean selected,boolean focus,int row,int col){
                super.getTableCellRendererComponent(t,value,selected,focus,row,col);
                setBorder(new EmptyBorder(0,12,0,12));
                setForeground(selected?TEXT:(col==0?TEXT:MUTED));
                setBackground(selected?new Color(43,77,67):(row%2==0?PANEL:new Color(29,39,45)));
                if(col==0) setFont(font(14,Font.BOLD));else setFont(font(13,Font.PLAIN));
                return this;
            }
        };
        table.setDefaultRenderer(Object.class,render);
        table.getColumnModel().getColumn(0).setPreferredWidth(340);
        table.getColumnModel().getColumn(1).setPreferredWidth(115);
        table.getColumnModel().getColumn(2).setPreferredWidth(100);
        table.getColumnModel().getColumn(3).setPreferredWidth(160);
        table.addMouseListener(new MouseAdapter(){
            @Override public void mouseClicked(MouseEvent e){if(e.getClickCount()==2 && SwingUtilities.isLeftMouseButton(e)) openSelection();}
            @Override public void mousePressed(MouseEvent e){popup(e);}
            @Override public void mouseReleased(MouseEvent e){popup(e);}
            private void popup(MouseEvent e){
                if(!e.isPopupTrigger())return;
                int row=table.rowAtPoint(e.getPoint());
                if(row>=0&&!table.isRowSelected(row))table.setRowSelectionInterval(row,row);
                JPopupMenu menu=new JPopupMenu();
                menuItem(menu,"Open",FilesPanel.this::openSelection);
                menuItem(menu,"Rename",FilesPanel.this::renameSelection);
                menuItem(menu,"Copy",()->setClipboard(false));
                menuItem(menu,"Move",()->setClipboard(true));
                menuItem(menu,"Paste",FilesPanel.this::pasteSelection);
                menu.addSeparator();menuItem(menu,"Delete...",FilesPanel.this::deleteSelection);
                menu.show(table,e.getX(),e.getY());
            }
        });
        table.getSelectionModel().addListSelectionListener(e->{if(!e.getValueIsAdjusting())updateControls();});
        table.setTransferHandler(new TransferHandler(){
            @Override public boolean canImport(TransferSupport support){return support.isDataFlavorSupported(DataFlavor.javaFileListFlavor) && browser!=null && !mutating;}
            @Override public boolean importData(TransferSupport support){
                if(!canImport(support))return false;
                try {
                    @SuppressWarnings("unchecked")
                    List<java.io.File> files=(List<java.io.File>)support.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
                    importPaths(files.stream().map(java.io.File::toPath).toList());
                    return true;
                }catch(Exception ex){error(ex);return false;}
            }
        });
        search.getDocument().addDocumentListener(new DocumentListener(){
            public void insertUpdate(DocumentEvent e){filter();}
            public void removeUpdate(DocumentEvent e){filter();}
            public void changedUpdate(DocumentEvent e){filter();}
        });
        KeyStrokeMapping("ENTER","open",this::openSelection);
        KeyStrokeMapping("BACK_SPACE","up",this::goUp);
        KeyStrokeMapping("F2","rename",this::renameSelection);
        KeyStrokeMapping("DELETE","delete",this::deleteSelection);
        KeyStrokeMapping("control C","copy",()->setClipboard(false));
        KeyStrokeMapping("control X","cut",()->setClipboard(true));
        KeyStrokeMapping("control V","paste",this::pasteSelection);
    }
    private void KeyStrokeMapping(String stroke,String name,Runnable task){
        table.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(stroke),name);
        table.getActionMap().put(name,new AbstractAction(){public void actionPerformed(ActionEvent e){task.run();}});
    }
    private static void menuItem(JPopupMenu menu,String name,Runnable task){
        JMenuItem item=new JMenuItem(name);item.addActionListener(e->task.run());menu.add(item);
    }
    private void installActions(){
        home.addActionListener(e->goHome());up.addActionListener(e->goUp());refresh.addActionListener(e->refresh());
        folder.addActionListener(e->newFolder());add.addActionListener(e->importDialog());
        open.addActionListener(e->openSelection());rename.addActionListener(e->renameSelection());
        copy.addActionListener(e->setClipboard(false));cut.addActionListener(e->setClipboard(true));
        paste.addActionListener(e->pasteSelection());delete.addActionListener(e->deleteSelection());
    }
    public void whenShown(){
        try{
            SharedFileBrowser next=new SharedFileBrowser(sharedFolder.get());
            if(browser==null||!browser.root().equals(next.root())){
                browser=next;current=next.root();clipboard=List.of();
                search.setText("");
            }
            refresh();
        }catch(Exception ex){
            browser=null;current=null;entries=List.of();model.fireTableDataChanged();
            pathLabel.setText("No shared folder selected");
            status.setText("Choose an existing shared folder in Share files, then return here.");
            updateControls();
        }
    }
    private void filter(){
        String term=search.getText().strip();
        if(term.isEmpty())sorter.setRowFilter(null);
        else sorter.setRowFilter(RowFilter.regexFilter("(?i)"+java.util.regex.Pattern.quote(term),0));
        count.setText(sorter.getViewRowCount()+" shown / "+entries.size()+" total");
        updateControls();
    }
    private void refresh(){
        if(browser==null||current==null)return;
        final Path directory=current;final SharedFileBrowser active=browser;final long generation=++refreshVersion;
        pathLabel.setText(displayPath());
        status.setForeground(MUTED);status.setText("Loading files...");
        io.execute(()->{
            try{
                List<SharedFileBrowser.Entry> newEntries=active.list(directory);
                SwingUtilities.invokeLater(()->{
                    if(generation!=refreshVersion)return;
                    entries=newEntries;model.fireTableDataChanged();
                    count.setText(sorter.getViewRowCount()+" shown / "+newEntries.size()+" total");
                    status.setText("Folder ready. Changes to files are visible to your connected devices.");
                    updateControls();
                });
            }catch(Exception error){
                SwingUtilities.invokeLater(()->{
                    if(generation!=refreshVersion)return;
                    entries=List.of();model.fireTableDataChanged();
                    status.setText("Cannot read folder: "+error.getMessage());updateControls();
                });
            }
        });
    }
    private String displayPath(){
        if(browser==null || current==null)return "No folder";
        Path relative=browser.root().relativize(current);
        String value=relative.toString();
        String name=browser.root().getFileName()==null?browser.root().toString():browser.root().getFileName().toString();
        if(value.isEmpty())return "Shared folder  /  "+name;
        return "Shared folder  /  "+name+"  /  "+value;
    }
    private void goHome(){if(browser!=null&&!mutating){current=browser.root();refresh();}}
    private void goUp(){if(browser!=null && current!=null && !current.equals(browser.root()) && !mutating){current=current.getParent();refresh();}}
    private List<SharedFileBrowser.Entry> selected(){
        List<SharedFileBrowser.Entry> picked=new ArrayList<>();
        for(int row:table.getSelectedRows()){
            int modelRow=table.convertRowIndexToModel(row);
            if(modelRow>=0 && modelRow<entries.size())picked.add(entries.get(modelRow));
        }
        return picked;
    }
    private void updateControls(){
        int n=selected().size();boolean active=browser!=null&&!mutating;
        home.setEnabled(active && current!=null&&!current.equals(browser.root()));
        up.setEnabled(active && current!=null&&!current.equals(browser.root()));
        refresh.setEnabled(active);add.setEnabled(active);folder.setEnabled(active);
        open.setEnabled(active && n==1);rename.setEnabled(active && n==1);
        copy.setEnabled(active&&n>0);cut.setEnabled(active&&n>0);
        paste.setEnabled(active&&!clipboard.isEmpty());delete.setEnabled(active&&n>0);
        if(n==1){
            SharedFileBrowser.Entry e=selected().get(0);
            selection.setText(e.directory?"Folder selected":e.link?"Link (cannot be opened)":human(e.size)+" selected");
        } else selection.setText(n==0?"Select files to manage them":n+" files selected");
    }
    private static String human(long bytes){
        if(bytes<0)return "—";
        if(bytes<1024)return bytes+" B";
        double v=bytes;String[] units={"B","KB","MB","GB","TB"};int i=0;
        while(v>=1024 && i<units.length-1){v/=1024;i++;}
        return String.format(Locale.ROOT,"%.1f %s",v,units[i]);
    }
    private void openSelection(){
        List<SharedFileBrowser.Entry> choice=selected();if(choice.size()!=1||mutating)return;
        SharedFileBrowser.Entry item=choice.get(0);
        try{
            if(item.link)throw new IOException("Symbolic links are not supported in the shared file browser.");
            if(item.directory){current=browser.enter(item.path);search.setText("");refresh();}
            else if(Desktop.isDesktopSupported())Desktop.getDesktop().open(item.path.toFile());
            else throw new IOException("Opening files with the system is not supported on this desktop.");
        }catch(Exception ex){error(ex);}
    }
    private void newFolder(){
        if(browser==null||mutating)return;
        String name=(String)JOptionPane.showInputDialog(this,"New folder name:","Create a folder",JOptionPane.PLAIN_MESSAGE,null,null,"New Folder");
        if(name==null||name.trim().isEmpty())return;
        Path dir=current;runTask("Creating folder...",()->browser.newFolder(dir,name));
    }
    private void renameSelection(){
        List<SharedFileBrowser.Entry> items=selected();if(items.size()!=1||mutating)return;
        Path src=items.get(0).path;
        String next=JOptionPane.showInputDialog(this,"Rename to:",src.getFileName().toString());
        if(next==null||next.trim().isEmpty()||next.equals(src.getFileName().toString()))return;
        runTask("Renaming...",()->browser.rename(src,next));
    }
    private void importDialog(){
        if(browser==null||mutating)return;
        JFileChooser chooser=new JFileChooser();chooser.setDialogTitle("Import files or folders into Edo");
        chooser.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);chooser.setMultiSelectionEnabled(true);
        if(chooser.showOpenDialog(this)!=JFileChooser.APPROVE_OPTION)return;
        importPaths(Arrays.stream(chooser.getSelectedFiles()).map(java.io.File::toPath).toList());
    }
    private void importPaths(List<Path> sources){
        if(browser==null||mutating||sources.isEmpty())return;
        Path target=current;
        runTask("Importing "+sources.size()+" item(s)...",()->browser.importFiles(sources,target));
    }
    private void setClipboard(boolean move){
        if(browser==null||mutating)return;
        List<SharedFileBrowser.Entry> items=selected();if(items.isEmpty())return;
        clipboard=items.stream().map(e->e.path).toList();moveClipboard=move;
        status.setText(items.size()+" item(s) ready to "+(move?"move":"copy")+". Open a destination folder and click Paste.");
        updateControls();
    }
    private void pasteSelection(){
        if(browser==null||mutating||clipboard.isEmpty())return;
        List<Path> inputs=clipboard;boolean move=moveClipboard;Path target=current;
        runTask(move?"Moving items...":"Copying items...",()->browser.paste(inputs,target,move),()->{
            if(move){clipboard=List.of();moveClipboard=false;}
        });
    }
    private void deleteSelection(){
        if(browser==null||mutating)return;
        List<SharedFileBrowser.Entry> items=selected();if(items.isEmpty())return;
        String detail=items.size()==1?items.get(0).path.getFileName().toString():items.size()+" items";
        int answer=JOptionPane.showConfirmDialog(this,
            "Permanently delete "+detail+"?\n\nThis cannot be undone. Folders and their contents will be deleted.",
            "Delete from shared folder",JOptionPane.YES_NO_OPTION,JOptionPane.WARNING_MESSAGE);
        if(answer!=JOptionPane.YES_OPTION)return;
        List<Path> paths=items.stream().map(e->e.path).toList();
        runTask("Deleting...",()->browser.delete(paths),()->{
            clipboard=clipboard.stream().filter(p->paths.stream().noneMatch(parent->p.startsWith(parent))).toList();
        });
    }
    @FunctionalInterface private interface FileOperation {void run() throws Exception;}
    private void runTask(String label,FileOperation task){runTask(label,task,()->{});}
    private void runTask(String label,FileOperation task,Runnable completed){
        if(mutating)return;
        mutating=true;status.setText(label);updateControls();
        io.execute(()->{
            Exception failure=null;try{task.run();}catch(Exception ex){failure=ex;}
            Exception error=failure;
            SwingUtilities.invokeLater(()->{
                mutating=false;
                if(error!=null)error(error);else completed.run();
                refresh();updateControls();
            });
        });
    }
    private void error(Exception ex){
        status.setForeground(DANGER);status.setText("File operation failed: "+ex.getMessage());
        JOptionPane.showMessageDialog(this,ex.getMessage(),"Edo Files",JOptionPane.ERROR_MESSAGE);
    }
    private final class FileTableModel extends AbstractTableModel {
        private final String[] heads={"NAME","SIZE","TYPE","MODIFIED"};
        public int getColumnCount(){return heads.length;}
        public String getColumnName(int i){return heads[i];}
        public int getRowCount(){return entries.size();}
        public Object getValueAt(int r,int c){
            SharedFileBrowser.Entry e=entries.get(r);
            return switch(c){
                case 0 -> (e.directory?"▣   ":e.link?"↗   ":"▫   ")+e.path.getFileName();
                case 1 -> e.directory?"—":human(e.size);
                case 2 -> e.link?"Shortcut":e.directory?"Folder":"File";
                default -> DateFormat.getDateTimeInstance(DateFormat.MEDIUM,DateFormat.SHORT).format(new Date(e.modified.toMillis()));
            };
        }
        public boolean isCellEditable(int row,int col){return false;}
    }
}
