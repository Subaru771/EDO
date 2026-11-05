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

/** Native, D-pad accessible browser for received content under Downloads/DropWave. */
public final class ReceivedFilesActivity extends Activity {
    private static final int BG=Color.rgb(11,18,27), PANEL=Color.rgb(24,37,49);
    private static final int BORDER=Color.rgb(61,83,97), WHITE=Color.rgb(246,250,251);
    private static final int MUTED=Color.rgb(174,194,205), ACCENT=Color.rgb(154,242,184);
    private LinearLayout listContainer;
    private TextView breadcrumb, summary, sortButton;
    private EditText search;
    private String folder="", query="";
    private int sort=0, pageSize=100;
    private String categoryFilter="All";
    private List<LanServer.Entry> allFiles=Collections.emptyList();
    private List<String> allFolders=Collections.emptyList();

    private int dp(float v){return (int)(v*getResources().getDisplayMetrics().density+0.5f);}
    private GradientDrawable background(int fill,int border){
        GradientDrawable d=new GradientDrawable();d.setColor(fill);d.setCornerRadius(dp(17));d.setStroke(dp(1),border);return d;
    }
    private LinearLayout column(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
    private TextView text(String s,int size,int color,boolean bold){
        TextView t=new TextView(this);t.setText(s);t.setTextColor(color);t.setTextSize(size);
        if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return t;
    }
    private void space(LinearLayout l,int h){View v=new View(this);l.addView(v,new LinearLayout.LayoutParams(1,dp(h)));}
    private TextView button(String s,Runnable action){
        TextView t=text(s,17,WHITE,true);t.setGravity(Gravity.CENTER);t.setPadding(dp(18),dp(15),dp(18),dp(15));
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
        ScrollView sc=new ScrollView(this);sc.setFillViewport(true);sc.setBackgroundColor(BG);
        LinearLayout root=column();root.setPadding(dp(36),dp(31),dp(36),dp(32));sc.addView(root);
        TextView back=button("‹   Back to dashboard",this::finish);root.addView(back);
        space(root,27);root.addView(text("YOUR TV LIBRARY",13,ACCENT,true));space(root,9);
        root.addView(text("Received files",34,WHITE,true));space(root,7);
        root.addView(text("Explore files delivered to this TV from your other devices.",16,MUTED,false));
        space(root,25);
        breadcrumb=text("Downloads / Edo",16,ACCENT,true);root.addView(breadcrumb);space(root,14);
        search=new EditText(this);search.setSingleLine(true);search.setHint("Search received files and folders");
        search.setTextColor(WHITE);search.setHintTextColor(MUTED);search.setTextSize(17);
        search.setPadding(dp(14),dp(9),dp(14),dp(9));search.setBackground(background(PANEL,BORDER));
        root.addView(search,new LinearLayout.LayoutParams(-1,dp(54)));
        search.addTextChangedListener(new TextWatcher(){
            public void beforeTextChanged(CharSequence s,int a,int c,int f){}
            public void onTextChanged(CharSequence s,int start,int before,int count){query=s.toString().trim();pageSize=100;render();}
            public void afterTextChanged(Editable e){}
        });
        space(root,12);
        LinearLayout toolbar=new LinearLayout(this);toolbar.setOrientation(LinearLayout.HORIZONTAL);root.addView(toolbar);
        sortButton=button("Sort: Recent",()->{sort=(sort+1)%4;render();});
        toolbar.addView(sortButton,new LinearLayout.LayoutParams(0,-2,1));
        TextView refresh=button("↻  Refresh",this::reload);LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(0,-2,1);
        rp.leftMargin=dp(10);toolbar.addView(refresh,rp);
        space(root,12);
        LinearLayout filters=new LinearLayout(this);filters.setOrientation(LinearLayout.HORIZONTAL);root.addView(filters);
        for(String filter:new String[]{"All","Video","Image","Audio","APK"}){
            TextView option=button(filter,()->{categoryFilter=filter;render();});
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);
            lp.setMargins(0,0,dp(5),0);filters.addView(option,lp);
        }
        space(root,15);
        summary=text("Loading…",14,MUTED,false);root.addView(summary);space(root,12);
        listContainer=column();root.addView(listContainer);
        setContentView(sc);
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
        breadcrumb.setText("Downloads / DropWave"+(folder.isEmpty()?"":" / "+folder.replace("/"," / ")));
        sortButton.setText("Sort: "+new String[]{"Recent","Name","Size","Type"}[sort]);
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
        summary.setText(dirs.size()+" folder(s)  •  "+matches.size()+" file(s)"+(query.isEmpty()?"":" matching search"));
        if(dirs.isEmpty()&&matches.isEmpty())addEmpty();
        for(String d:dirs){String name=folder.isEmpty()?d:folder+"/"+d;
            addRow("▸  "+d,"Folder",()->navigate(name),null);
        }
        int visible=Math.min(matches.size(),pageSize);
        for(int i=0;i<visible;i++){
            LanServer.Entry e=matches.get(i);String kind=category(e);
            String detail=kind+"  •  "+formatSize(e.size)+(query.isEmpty()?"":"  •  "+(e.folder.isEmpty()?"Top level":e.folder));
            addRow(icon(kind)+"  "+e.name,detail,()->openFile(e),()->options(e));
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
        row.setPadding(dp(14),dp(12),dp(12),dp(12));row.setBackground(background(PANEL,BORDER));
        LinearLayout detailsView=column();TextView name=text(title,17,WHITE,true);name.setMaxLines(2);
        detailsView.addView(name);space(detailsView,5);detailsView.addView(text(details,13,MUTED,false));
        detailsView.setFocusable(true);detailsView.setClickable(true);
        detailsView.setPadding(dp(4),dp(7),dp(4),dp(7));
        detailsView.setOnFocusChangeListener((v,focused)->{
            name.setTextColor(focused?ACCENT:WHITE);row.setBackground(background(focused?BORDER:PANEL,focused?ACCENT:BORDER));
        });
        detailsView.setOnClickListener(v->onClick.run());
        detailsView.setOnLongClickListener(v->{if(more!=null){more.run();return true;}return false;});
        row.addView(detailsView,new LinearLayout.LayoutParams(0,-2,1));
        if(more!=null){TextView menu=button("⋮  Options",more);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-2,-2);p.leftMargin=dp(10);row.addView(menu,p);}
        listContainer.addView(row);space(listContainer,8);
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
        String[] menu={category(e).equals("APK")?"Install APK":"Open / Preview", "Send to nearby Edo", "Copy to USB / external", "File details", "Rename", "Move to folder", "Delete"};
        new AlertDialog.Builder(this).setTitle(e.name).setItems(menu,(d,choice)->{
            if(choice==0)openFile(e);
            else if(choice==1){Intent intent=new Intent(this,PeerDiscoveryActivity.class);intent.putExtra("fileId",e.id);startActivity(intent);}
            else if(choice==2){Intent i=new Intent(this,ExternalStorageActivity.class);i.putExtra("exportId",e.id);startActivity(i);}
            else if(choice==3)details(e);
            else if(choice==4)rename(e);
            else if(choice==5)move(e);
            else if(choice==6)delete(e);
        }).setNegativeButton("Cancel",null).show();
    }
    private void details(LanServer.Entry e){
        String path="Downloads/DropWave/"+(e.folder.isEmpty()?"":e.folder+"/")+e.name;
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
        String[] labels=new String[folders.size()];for(int i=0;i<folders.size();i++)labels[i]=folders.get(i).isEmpty()?"Downloads / DropWave (root)":folders.get(i);
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
        if(!folder.isEmpty()){navigate(parent(folder));return;}
        super.onBackPressed();
    }
}
