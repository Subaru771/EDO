package com.dropwave.tv;

import android.app.*;
import android.content.*;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.*;
import android.provider.DocumentsContract;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.*;

/** SAF-based USB/external folder browser. No unrestricted storage permission is needed. */
public final class ExternalStorageActivity extends Activity {
    private static final int PICK_TREE=6304;
    private static final int BG=Color.rgb(11,18,27), PANEL=Color.rgb(24,37,49);
    private static final int BORDER=Color.rgb(61,83,97), WHITE=Color.rgb(246,250,251);
    private int dp(float v){return (int)(v*getResources().getDisplayMetrics().density+0.5f);}
    private android.graphics.drawable.GradientDrawable surface(int fill,int border){
        android.graphics.drawable.GradientDrawable d=new android.graphics.drawable.GradientDrawable();
        d.setColor(fill);d.setCornerRadius(dp(16));d.setStroke(dp(1),border);return d;
    }
    private final Handler main=new Handler(Looper.getMainLooper());
    private LinearLayout content;private Uri tree;private String documentId,displayPath="External storage";
    private String exportId;
    private TextView title;
    private final Deque<String> parentIds=new ArrayDeque<>();
    private final Deque<String> pathStack=new ArrayDeque<>();
    @Override public void onCreate(Bundle state){super.onCreate(state);exportId=getIntent().getStringExtra("exportId");
        ScrollView scroll=new ScrollView(this);scroll.setBackgroundColor(BG);content=new LinearLayout(this);content.setOrientation(1);content.setPadding(dp(35),dp(32),dp(35),dp(42));scroll.addView(content);
        title=new TextView(this);title.setTextSize(33);title.setTextColor(WHITE);title.setPadding(0,0,0,dp(18));content.addView(title);
        Button pick=button("Choose USB / external folder",this::chooseTree);content.addView(pick);
        Button back=button("↑  Parent folder",this::up);content.addView(back);
        Button save=button("Copy selected TV file here",this::exportHere);content.addView(save);save.setVisibility(exportId==null?View.GONE:View.VISIBLE);
        setContentView(scroll);
        String saved=getPreferences(0).getString("tree",null);
        if(saved!=null){try{openTree(Uri.parse(saved));}catch(Exception e){title.setText("Choose a USB or external folder");}}
        else title.setText("Choose a USB or external folder");
    }
    private Button button(String name,Runnable handler){
        Button b=new Button(this);b.setAllCaps(false);b.setText(name);b.setFocusable(true);
        b.setTextSize(16);b.setTextColor(WHITE);b.setPadding(dp(16),dp(14),dp(16),dp(14));
        b.setBackground(surface(PANEL,BORDER));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,0,0,dp(10));b.setLayoutParams(lp);
        b.setOnFocusChangeListener((v,focus)->{b.setBackground(surface(focus?WHITE:PANEL,focus?WHITE:BORDER));b.setTextColor(focus?BG:WHITE);});
        b.setOnClickListener(v->handler.run());return b;
    }
    private void chooseTree(){try{Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION|
            Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION|Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
        startActivityForResult(i,PICK_TREE);
    }catch(Exception e){new AlertDialog.Builder(this).setTitle("Folder picker unavailable")
        .setMessage("This TV does not provide Android's system folder picker. You can still manage internal received files in Edo.")
        .setPositiveButton("OK",null).show();}}
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);
        if(request==PICK_TREE&&result==RESULT_OK&&data!=null&&data.getData()!=null){
            Uri selected=data.getData();try{
                int flags=data.getFlags()&(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                getContentResolver().takePersistableUriPermission(selected,flags);
                getPreferences(0).edit().putString("tree",selected.toString()).apply();
                openTree(selected);
            }catch(Exception e){Toast.makeText(this,"Cannot access folder: "+e.getMessage(),Toast.LENGTH_LONG).show();}
        }
    }
    private void openTree(Uri selected){tree=selected;documentId=DocumentsContract.getTreeDocumentId(tree);
        displayPath="External storage";pathStack.clear();parentIds.clear();render();}
    private void up(){if(!parentIds.isEmpty()){documentId=parentIds.removeLast();displayPath=pathStack.removeLast();render();}
        else Toast.makeText(this,"At selected folder root",Toast.LENGTH_SHORT).show();}
    private void render(){if(tree==null)return;
        title.setText(displayPath);
        // Keep toolbar buttons (title + three buttons); replace only content rows.
        while(content.getChildCount()>4)content.removeViewAt(4);
        Uri children=DocumentsContract.buildChildDocumentsUriUsingTree(tree,documentId);
        String[] cols={DocumentsContract.Document.COLUMN_DOCUMENT_ID,DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,DocumentsContract.Document.COLUMN_SIZE};
        try(Cursor cursor=getContentResolver().query(children,cols,null,null,null)){
            if(cursor==null)throw new IOException("The storage provider returned no listing");
            int total=0;
            while(cursor.moveToNext()&&total<500){total++;
                String id=cursor.getString(0),name=cursor.getString(1),mime=cursor.getString(2);
                if(name==null||id==null)continue;
                final String childId=id,label=name,kind=mime;
                boolean folder=DocumentsContract.Document.MIME_TYPE_DIR.equals(mime);
                Button b=button((folder?"▸  ":"◫  ")+name,()->{
                    if(folder){parentIds.addLast(documentId);pathStack.addLast(displayPath);
                        documentId=childId;displayPath+=" / "+label;render();}
                    else showFile(childId,label,kind);
                });content.addView(b);
            }
            if(total==0){TextView empty=new TextView(this);empty.setText("This folder is empty.");empty.setTextColor(Color.LTGRAY);content.addView(empty);}
        }catch(Exception e){TextView error=new TextView(this);error.setText("Cannot list this folder: "+e.getMessage());error.setTextColor(Color.LTGRAY);content.addView(error);}
    }
    private void showFile(String childId,String name,String mime){
        Uri doc=DocumentsContract.buildDocumentUriUsingTree(tree,childId);
        new AlertDialog.Builder(this).setTitle(name).setItems(new String[]{"Open in a compatible app","Copy into Edo Downloads"},(d,index)->{
            if(index==0){try{Intent i=new Intent(Intent.ACTION_VIEW);i.setDataAndType(doc,mime);
                i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);startActivity(i);
            }catch(Exception e){Toast.makeText(this,"Cannot open: "+e.getMessage(),Toast.LENGTH_LONG).show();}}
            else runCopy(()->new AndroidFiles(this).importIncoming(doc,""),"Imported into Edo Downloads");
        }).setNegativeButton("Cancel",null).show();
    }
    private void exportHere(){if(tree==null||exportId==null)return;
        runCopy(()->{
            AndroidFiles files=new AndroidFiles(this);LanServer.Entry selected=null;
            for(LanServer.Entry e:files.list())if(e.id.equals(exportId)){selected=e;break;}
            if(selected==null)throw new FileNotFoundException("Selected file no longer exists");
            Uri dir=DocumentsContract.buildDocumentUriUsingTree(tree,documentId);
            Uri destination=DocumentsContract.createDocument(getContentResolver(),dir,selected.mime,selected.name);
            if(destination==null)throw new IOException("Storage provider refused file creation");
            try(LanServer.Opened src=files.open(exportId);OutputStream out=getContentResolver().openOutputStream(destination,"w")){
                if(out==null)throw new IOException("Cannot write to external storage");
                LanServer.copyExactly(src.stream,out,src.length);
            }catch(Exception e){DocumentsContract.deleteDocument(getContentResolver(),destination);throw e;}
            return selected;
        },"Copied to external storage");
    }
    private interface Work { Object run()throws Exception; }
    private void runCopy(Work work,String success){AlertDialog dialog=new AlertDialog.Builder(this)
        .setTitle("Transferring file").setMessage("Keep Edo open until the copy completes.").setCancelable(false).create();dialog.show();
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        new Thread(()->{
            Exception failure=null;try{work.run();}catch(Exception e){failure=e;}
            Exception result=failure;main.post(()->{dialog.dismiss();getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                new AlertDialog.Builder(this).setTitle(result==null?"Done":"Transfer failed")
                    .setMessage(result==null?success:result.getMessage()).setPositiveButton("OK",null).show();});
        },"edo-usb-copy").start();
    }
}
