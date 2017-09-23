package co.bgcs.neterraproxy;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Binder;
import android.os.IBinder;
import android.support.v4.app.NotificationCompat;
import java.io.IOException;


public class MainService extends Service implements Pipe {
    private final NeterraProxy proxy = new NeterraProxy("127.0.0.1", 8888, this);
    private final IBinder binder = new LocalBinder();

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        try {
            proxy.start();
            startForeground(1, getNotification("Ready to serve."));
        } catch (IOException e) {
            e.printStackTrace();
            stopSelf();
        }
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public void onDestroy() {
        proxy.stop();
        stopForeground(true);
    }

    @Override
    public void setNotification(String text) {
        ((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE)).notify(1, getNotification(text));
    }

    private Notification getNotification(String text) {
        return new NotificationCompat.Builder(this)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("NeterraProxy")
                .setContentText(text)
                .setOngoing(true)
                .setPriority(2)
                .build();
    }

    void loadPreferences(SharedPreferences preferences) {
        proxy.init(
                preferences.getString("username", null),
                preferences.getString("password", null),
                getApplicationContext()
        );
    }

    class LocalBinder extends Binder {
        MainService getService() {
            return MainService.this;
        }
    }
}
