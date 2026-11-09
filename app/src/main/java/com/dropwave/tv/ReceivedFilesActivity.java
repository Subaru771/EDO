package com.dropwave.tv;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentUris;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.content.Context;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Native, D-pad accessible browser for Edo downloads (and readable legacy files). */
public final class ReceivedFilesActivity extends Activity {
    private static final int BG=Color.rgb(16,21,26), PANEL=Color.rgb(25,33,39);
    private static final int BORDER=Color.rgb(52,67,75), WHITE=Color.rgb(242,246,247);
    private static final int MUTED=Color.rgb(172,190,199), ACCENT=Color.rgb(163,229,192);
    private LinearLayout listContainer;
    private TextView breadcrumb, summary;
    private ScrollView browserScroll;
    private boolean focusFilesOnLoad=true;
    private View initialFileFocus, initialFolderFocus, lastRowFocus;
    private EditText search;
    private String folder="", query="";
    private int sort=0, pageSize=100;
    private static final String[] SORTS={"Recent","Name","Size","Type"};
    private static final String[] FILTERS={"All","Video","Image","Audio","APK","PDF","Archive","File"};
    private String categoryFilter="All";
    private List<LanServer.Entry> allFiles=Collections.emptyList();
    private List<String> allFolders=Collections.emptyList();

    private int dp(float v){return (int)(v*getResources().getDisplayMetrics().density+0.5f);}
    private GradientDrawable background(int fill,int border){
        GradientDrawable d=new GradientDrawable();d.setColor(fill);d.setCornerRadius(dp(11));d.setStroke(dp(1),border);return d;
    }
    private LinearLayout column(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
    private TextView text(String s,int size,int color,boolean bold){
        TextView t=new TextView(this);t.setText(s);t.setTextColor(color);t.setTextSize(size);
        if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return t;
    }
    private void space(LinearLayout l,int h){View v=new View(this);l.addView(v,new LinearLayout.LayoutParams(1,dp(h)));}
    private TextView button(String s,Runnable action){
        TextView t=text(s,15,WHITE,true);t.setGravity(Gravity.CENTER);t.setPadding(dp(13),dp(11),dp(13),dp(11));
        t.setBackground(background(PANEL,BORDER));t.setFocusable(true);t.setClickable(true);
        t.setOnFocusChangeListener((v,hasFocus)->{
            t.setBackground(background(hasFocus?WHITE:PANEL,hasFocus?WHITE:BORDER));
            t.setTextColor(hasFocus?BG:WHITE);
        });
        t.setOnClickListener(v->action.run());return t;
    }
    private void toast(String message){Toast.makeText(this,message,Toast.LENGTH_LONG).show();}

    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);
        if(state!=null){folder=state.getString("folder","");sort=state.getInt("sort",0);}
        build();reload();
    }
    @Override public void onResume(){super.onResume();RemoteControlBridge.activate(this);if(listContainer!=null)reload();}
    @Override protected void onPause(){RemoteControlBridge.deactivate(this);super.onPause();}
    @Override protected void onSaveInstanceState(Bundle out){out.putString("folder",folder);out.putInt("sort",sort);super.onSaveInstanceState(out);}
    private void build(){
        browserScroll=new ScrollView(this);
        browserScroll.setFillViewport(true);browserScroll.setBackgroundColor(BG);
        LinearLayout root=column();root.setPadding(dp(20),dp(14),dp(20),dp(20));
        browserScroll.addView(root);

        // Single small toolbar: the file list, not configuration, is the main screen.
        LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(top);
        TextView back=button("‹",this::finish);back.setTextSize(25);back.setContentDescription("Back to Edo");
        top.addView(back,new LinearLayout.LayoutParams(dp(48),dp(46)));
        LinearLayout heading=column();heading.setPadding(dp(13),0,0,0);
        top.addView(heading,new LinearLayout.LayoutParams(0,-2,1));
        heading.addView(text("Received files",23,WHITE,true));
        summary=text("Loading files…",12,MUTED,false);heading.addView(summary);
        TextView searchToggle=button("⌕",()->{
            boolean open=search.getVisibility()!=View.VISIBLE;
            search.setVisibility(open?View.VISIBLE:View.GONE);
            if(open){search.requestFocus();browserScroll.smoothScrollTo(0,0);}
            else{search.setText("");hideKeyboard();}
        });
        searchToggle.setTextSize(23);searchToggle.setContentDescription("Search files");
        LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(dp(48),dp(46));
        sp.leftMargin=dp(7);top.addView(searchToggle,sp);
        TextView more=button("⋯",this::showBrowserMenu);more.setTextSize(23);
        more.setContentDescription("Sort, filter and refresh files");
        LinearLayout.LayoutParams mp=new LinearLayout.LayoutParams(dp(48),dp(46));
        mp.leftMargin=dp(7);top.addView(more,mp);

        space(root,8);
        breadcrumb=text("Downloads / Edo",12,ACCENT,true);root.addView(breadcrumb);
        space(root,9);

        search=new EditText(this);search.setSingleLine(true);
        search.setHint("Search files and folders");
        search.setTextColor(WHITE);search.setHintTextColor(MUTED);search.setTextSize(14);
        search.setPadding(dp(12),dp(8),dp(12),dp(8));
        search.setBackground(background(PANEL,BORDER));search.setVisibility(View.GONE);
        root.addView(search,new LinearLayout.LayoutParams(-1,dp(43)));
        search.addTextChangedListener(new TextWatcher(){
            public void beforeTextChanged(CharSequence value,int start,int count,int after){}
            public void onTextChanged(CharSequence value,int start,int before,int count){
                query=value.toString().trim();pageSize=100;render();
            }
            public void afterTextChanged(Editable value){}
        });

        // No permanent sort bar, 'All' chips or oversized empty header.
        listContainer=column();root.addView(listContainer);
        setContentView(browserScroll);
    }
    private void showBrowserMenu(){
        String[] menu={"Sort  ·  "+SORTS[sort],"Filter  ·  "+categoryFilter,"↻  Refresh","Clear filters"};
        new AlertDialog.Builder(this).setTitle("File browser").setItems(menu,(dialog,which)->{
            if(which==0)new AlertDialog.Builder(this).setTitle("Sort files")
                .setSingleChoiceItems(SORTS,sort,(d,choice)->{
                    sort=choice;pageSize=100;render();d.dismiss();
                }).setNegativeButton("Cancel",null).show();
            else if(which==1){
                int selected=0;
                for(int i=0;i<FILTERS.length;i++)if(FILTERS[i].equals(categoryFilter)){selected=i;break;}
                new AlertDialog.Builder(this).setTitle("Show file types")
                    .setSingleChoiceItems(FILTERS,selected,(d,choice)->{
                        categoryFilter=FILTERS[choice];pageSize=100;render();d.dismiss();
                    }).setNegativeButton("Cancel",null).show();
            }else if(which==2)reload();
            else {sort=0;categoryFilter="All";query="";search.setText("");
                search.setVisibility(View.GONE);pageSize=100;render();}
        }).setNegativeButton("Close",null).show();
    }
    private void reload(){
        try{
            AndroidFiles files=new AndroidFiles(this);
            List<LanServer.Entry> found=new ArrayList<>();
            for(LanServer.Entry e:files.list())if(e.id.startsWith("m"))found.add(e);
            allFiles=found;allFolders=files.folders();render();
        }catch(Exception e){toast("Unable to read received files: "+e.getMessage());}
    }
    private static String formatSize(long b){
        if(b<0)return "Unknown size";
        if(b<1024)return b+" B";
        String[] units={"KB","MB","GB","TB"};double n=b;int index=-1;
        do{n/=1024;index++;}while(n>=1024&&index<units.length-1);
        return String.format(Locale.US,"%.1f %s",n,units[index]);
    }
    private String category(LanServer.Entry e){
        String m=e.mime==null?"":e.mime.toLowerCase(Locale.ROOT),n=e.name.toLowerCase(Locale.ROOT);
        if(m.startsWith("image/"))return "Image";
        if(m.startsWith("video/"))return "Video";
        if(m.startsWith("audio/"))return "Audio";
        if(n.endsWith(".apk"))return "APK";
        if(n.endsWith(".pdf"))return "PDF";
        if(n.endsWith(".zip")||n.endsWith(".rar")||n.endsWith(".7z"))return "Archive";
        return "File";
    }
    private static String icon(String kind){
        switch(kind){case "Image":return "▧";case "Video":return "▶";case "Audio":return "♫";
            case "APK":return "⬡";case "PDF":return "▤";case "Archive":return "▣";default:return "▹";}
    }
    private void render(){
        if(listContainer==null)return;
        breadcrumb.setText("Downloads / Edo"+(folder.isEmpty()?"":" / "+folder.replace("/"," / ")));
        initialFileFocus=null;initialFolderFocus=null;
        listContainer.removeAllViews();
        if(!folder.isEmpty())addRow("↰  Parent folder","Go up one level",()->navigate(parent(folder)),null);
        List<String> dirs=new ArrayList<>();Set<String> seen=new HashSet<>();
        for(String path:allFolders){
            if(!path.startsWith(folder.isEmpty()?"":folder+"/"))continue;
            String suffix=folder.isEmpty()?path:path.substring(folder.length()+1);
            if(suffix.isEmpty())continue;
            String child=suffix.split("/",2)[0];
            if(!query.isEmpty()&&!child.toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT)))continue;
            if(seen.add(child))dirs.add(child);
        }
        Collections.sort(dirs,String.CASE_INSENSITIVE_ORDER);
        List<LanServer.Entry> matches=new ArrayList<>();
        for(LanServer.Entry e:allFiles){
            if(query.isEmpty()){
                if(!e.folder.equals(folder))continue;
            }else{
                if(!e.folder.equals(folder)&&!e.folder.startsWith(folder.isEmpty()?"":folder+"/"))continue;
                if(!e.name.toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT)))continue;
            }
            if(!categoryFilter.equals("All")&&!category(e).equals(categoryFilter))continue;
            matches.add(e);
        }
        if(sort==1)matches.sort(Comparator.comparing(e->e.name.toLowerCase(Locale.ROOT)));
        if(sort==2)matches.sort((a,b)->Long.compare(b.size,a.size));
        if(sort==3)matches.sort(Comparator.comparing((LanServer.Entry e)->category(e)).thenComparing(e->e.name.toLowerCase(Locale.ROOT)));
        summary.setText(matches.size()+" files  ·  "+dirs.size()+" folders"+(categoryFilter.equals("All")?"":"  ·  "+categoryFilter));
        if(dirs.isEmpty()&&matches.isEmpty())addEmpty();
        for(String d:dirs){String name=folder.isEmpty()?d:folder+"/"+d;
            addRow("▸  "+d,"Folder",()->navigate(name),null);
            if(initialFolderFocus==null)initialFolderFocus=lastRowFocus;
        }
        int visible=Math.min(matches.size(),pageSize);
        for(int i=0;i<visible;i++){
            LanServer.Entry e=matches.get(i);String kind=category(e);
            String detail=kind+"  •  "+formatSize(e.size)+(query.isEmpty()?"":"  •  "+(e.folder.isEmpty()?"Top level":e.folder));
            addRow(icon(kind)+"  "+e.name,detail,()->openFile(e),()->options(e));
            if(initialFileFocus==null)initialFileFocus=lastRowFocus;
        }
        if(focusFilesOnLoad){
            focusFilesOnLoad=false;
            View target=initialFileFocus!=null?initialFileFocus:initialFolderFocus;
            if(target!=null)target.post(target::requestFocus);
        }
        if(matches.size()>visible){
            addRow("Show more files…",(matches.size()-visible)+" remaining",()->{pageSize+=100;render();},null);
        }
    }
    private static String parent(String path){int index=path.lastIndexOf('/');return index<0?"":path.substring(0,index);}
    private void navigate(String to){folder=to;query="";search.setText("");pageSize=100;hideKeyboard();render();}
    private void hideKeyboard(){try{((InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(search.getWindowToken(),0);}catch(Exception ignored){}}
    private void addEmpty(){
        TextView empty=text("No files in this folder.\nSend files from your phone or PC using the address on the Edo dashboard.",16,MUTED,false);
        empty.setPadding(dp(16),dp(30),dp(16),dp(30));listContainer.addView(empty);
    }
    private void addRow(String title,String details,Runnable onClick,Runnable more){
        LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(11),dp(5),dp(8),dp(5));row.setBackground(background(PANEL,BORDER));
        LinearLayout detailsView=column();TextView name=text(title,15,WHITE,true);name.setMaxLines(2);
        detailsView.addView(name);space(detailsView,3);detailsView.addView(text(details,12,MUTED,false));
        detailsView.setFocusable(true);detailsView.setClickable(true);
        detailsView.setPadding(dp(3),dp(5),dp(3),dp(5));
        detailsView.setOnFocusChangeListener((v,focused)->{
            name.setTextColor(focused?ACCENT:WHITE);row.setBackground(background(focused?BORDER:PANEL,focused?ACCENT:BORDER));
        });
        detailsView.setOnClickListener(v->onClick.run());
        lastRowFocus=detailsView;
        detailsView.setOnLongClickListener(v->{if(more!=null){more.run();return true;}return false;});
        row.addView(detailsView,new LinearLayout.LayoutParams(0,-2,1));
        if(more!=null){TextView menu=button("⋮",more);menu.setTextSize(20);
            menu.setContentDescription("Actions for "+title);
            LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(dp(44),dp(46));
            p.leftMargin=dp(8);row.addView(menu,p);}
        listContainer.addView(row);space(listContainer,4);
    }
    private Uri uriFor(LanServer.Entry e){
        // IDs are obtained from AndroidFiles.list(), not arbitrary user input.
        return ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI,Long.parseLong(e.id.substring(1)));
    }
    private String openMime(LanServer.Entry e){
        String name=e.name.toLowerCase(Locale.ROOT);
        if(name.endsWith(".apk"))return "application/vnd.android.package-archive";
        if(name.endsWith(".pdf"))return "application/pdf";
        return e.mime==null?"application/octet-stream":e.mime;
    }
    private void previewImage(LanServer.Entry e){
        try{
            Uri uri=uriFor(e);
            BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;
            try(InputStream input=getContentResolver().openInputStream(uri)){
                if(input==null)throw new Exception("File is unavailable");
                BitmapFactory.decodeStream(input,null,bounds);
            }
            int sample=1;
            while(bounds.outWidth/sample>1920||bounds.outHeight/sample>1080)sample*=2;
            BitmapFactory.Options options=new BitmapFactory.Options();options.inSampleSize=sample;
            Bitmap bitmap;
            try(InputStream input=getContentResolver().openInputStream(uri)){
                if(input==null)throw new Exception("File is unavailable");
                bitmap=BitmapFactory.decodeStream(input,null,options);
            }
            if(bitmap==null)throw new Exception("This image format is not supported");
            ImageView view=new ImageView(this);view.setImageBitmap(bitmap);
            view.setAdjustViewBounds(true);view.setScaleType(ImageView.ScaleType.FIT_CENTER);
            view.setPadding(dp(12),dp(12),dp(12),dp(12));
            view.setMaxHeight(dp(540));
            new AlertDialog.Builder(this).setTitle(e.name).setView(view)
                .setPositiveButton("Open in app",(d,w)->openExternally(e))
                .setNegativeButton("Close",null).show();
        }catch(Exception ex){openExternally(e);}
    }
    private void openFile(LanServer.Entry e){
        if(category(e).equals("Image")){previewImage(e);return;}
        if(category(e).equals("Video")){
            Intent intent=new Intent(this,VideoPlayerActivity.class);intent.putExtra("fileId",e.id);
            startActivity(intent);return;
        }
        openExternally(e);
    }
    private void openExternally(LanServer.Entry e){
        try{
            Uri uri=uriFor(e);
            Intent i=new Intent(Intent.ACTION_VIEW);
            i.setDataAndType(uri,openMime(e));
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            if(i.resolveActivity(getPackageManager())==null){
                new AlertDialog.Builder(this).setTitle("No app to open this file")
                    .setMessage(e.name+"\n\nInstall a compatible TV app or download this file using Edo in a browser.")
                    .setPositiveButton("Details",(d,w)->details(e)).setNegativeButton("Close",null).show();return;
            }
            startActivity(i);
        }catch(Exception ex){toast("Could not open file: "+ex.getMessage());}
    }
    private void options(LanServer.Entry e){
        String[] choices={category(e).equals("APK")?"Install APK":"Open / Preview",
            "Send to Edo","Copy to USB","Details","Rename","Move","Delete"};
        LinearLayout grid=column();
        grid.setPadding(dp(12),dp(8),dp(12),dp(5));
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle(e.name)
            .setView(grid).setNegativeButton("Close",null).create();
        for(int i=0;i<choices.length;i+=2){
            LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);
            for(int j=i;j<Math.min(i+2,choices.length);j++){
                final int choice=j;
                TextView option=button(choices[j],()->{
                    dialog.dismiss();
                    if(choice==0)openFile(e);
                    else if(choice==1){Intent intent=new Intent(this,PeerDiscoveryActivity.class);intent.putExtra("fileId",e.id);startActivity(intent);}
                    else if(choice==2){Intent intent=new Intent(this,ExternalStorageActivity.class);intent.putExtra("exportId",e.id);startActivity(intent);}
                    else if(choice==3)details(e);
                    else if(choice==4)rename(e);
                    else if(choice==5)move(e);
                    else if(choice==6)delete(e);
                });
                option.setTextSize(14);option.setMinHeight(dp(48));
                LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-2,1);
                p.setMargins(0,0,j==i?dp(7):0,0);
                row.addView(option,p);
            }
            if(i+1>=choices.length)row.addView(new View(this),new LinearLayout.LayoutParams(0,dp(1),1));
            grid.addView(row,new LinearLayout.LayoutParams(-1,-2));
            space(grid,7);
        }
        dialog.show();
    }
    private void details(LanServer.Entry e){
        String base="Legacy TV downloads".equals(e.source)?"Downloads/DropWave/":"Downloads/Edo/";
        String path=base+(e.folder.isEmpty()?"":e.folder+"/")+e.name;
        new AlertDialog.Builder(this).setTitle("File information")
            .setMessage("Name: "+e.name+"\nType: "+category(e)+"\nSize: "+formatSize(e.size)+"\nMIME: "+e.mime+"\nLocation: "+path)
            .setPositiveButton("Open",(d,w)->openFile(e)).setNegativeButton("Close",null).show();
    }
    private void rename(LanServer.Entry e){
        EditText field=new EditText(this);field.setSingleLine(true);field.setText(e.name);field.selectAll();
        new AlertDialog.Builder(this).setTitle("Rename received file").setView(field)
            .setNegativeButton("Cancel",null).setPositiveButton("Rename",(d,w)->{
                try{new AndroidFiles(this).rename(e.id,field.getText().toString().trim());reload();}
                catch(Exception ex){toast("Rename failed: "+ex.getMessage());}
            }).show();
    }
    private void move(LanServer.Entry e){
        List<String> folders=new ArrayList<>();folders.add("");folders.addAll(allFolders);
        String[] labels=new String[folders.size()];for(int i=0;i<folders.size();i++)labels[i]=folders.get(i).isEmpty()?"Downloads / Edo (root)":folders.get(i);
        new AlertDialog.Builder(this).setTitle("Move to folder").setItems(labels,(d,which)->{
            try{new AndroidFiles(this).move(e.id,folders.get(which));reload();}
            catch(Exception ex){toast("Move failed: "+ex.getMessage());}
        }).setNegativeButton("Cancel",null).show();
    }
    private void delete(LanServer.Entry e){
        new AlertDialog.Builder(this).setTitle("Delete "+e.name+"?")
            .setMessage("This permanently removes the received file from the TV. It does not delete copies on other devices.")
            .setNegativeButton("Cancel",null).setPositiveButton("Delete",(d,w)->{
                try{new AndroidFiles(this).remove(e.id);reload();}
                catch(Exception ex){toast("Delete failed: "+ex.getMessage());}
            }).show();
    }
    @Override public void onBackPressed(){
        if(!query.isEmpty()){search.setText("");query="";hideKeyboard();render();return;}
        if(search.getVisibility()==View.VISIBLE){search.setVisibility(View.GONE);hideKeyboard();return;}
        if(!folder.isEmpty()){navigate(parent(folder));return;}
        super.onBackPressed();
    }
}
