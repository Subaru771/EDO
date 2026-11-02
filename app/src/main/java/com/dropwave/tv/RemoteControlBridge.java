package com.dropwave.tv;

import android.app.Activity;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import java.lang.ref.WeakReference;

/** In-app remote control only. Never injects keys system-wide. */
final class RemoteControlBridge {
    private static WeakReference<Activity> current=new WeakReference<>(null);
    private static final Handler main=new Handler(Looper.getMainLooper());
    static void activate(Activity activity){current=new WeakReference<>(activity);}
    static void deactivate(Activity activity){if(current.get()==activity)current=new WeakReference<>(null);}
    static void perform(String action){main.post(()->{
        Activity a=current.get();if(a==null)return;
        if("files".equals(action)){
            a.startActivity(new Intent(a,ReceivedFilesActivity.class));return;
        }
        if("home".equals(action)){
            if(a instanceof MainActivity)return;
            a.startActivity(new Intent(a,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP));return;
        }
        if("back".equals(action)){a.onBackPressed();return;}
        int key;
        switch(action){case "up":key=KeyEvent.KEYCODE_DPAD_UP;break;
            case "down":key=KeyEvent.KEYCODE_DPAD_DOWN;break;
            case "left":key=KeyEvent.KEYCODE_DPAD_LEFT;break;
            case "right":key=KeyEvent.KEYCODE_DPAD_RIGHT;break;
            case "select":key=KeyEvent.KEYCODE_DPAD_CENTER;break;
            default:return;
        }
        long when=android.os.SystemClock.uptimeMillis();
        a.dispatchKeyEvent(new KeyEvent(when,when,KeyEvent.ACTION_DOWN,key,0));
        a.dispatchKeyEvent(new KeyEvent(when,when,KeyEvent.ACTION_UP,key,0));
    });}
}
