package io.tuji.app;
import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.os.*;

public final class TransferService extends Service {
 private static TransferService current;private PowerManager.WakeLock lock;
 private static final String CHANNEL="tuji_transfer";
 @Override public void onCreate(){super.onCreate();current=this;NotificationManager nm=getSystemService(NotificationManager.class);nm.createNotificationChannel(new NotificationChannel(CHANNEL,"照片与文件传输",NotificationManager.IMPORTANCE_LOW));
  PowerManager pm=getSystemService(PowerManager.class);lock=pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"tuji:transfer");lock.acquire(60*60*1000L);
  startForeground(1,notification("准备传输"),ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
 }
 private Notification notification(String text){PendingIntent open=PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);PendingIntent cancel=PendingIntent.getService(this,1,new Intent(this,TransferService.class).setAction("cancel"),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
  return new Notification.Builder(this,CHANNEL).setSmallIcon(R.drawable.ic_tuji).setContentTitle("图迹 · 正在处理").setContentText(text).setContentIntent(open).setOngoing(true).addAction(new Notification.Action.Builder(null,"取消",cancel).build()).build();}
 public static void update(String text){TransferService s=current;if(s!=null)s.getSystemService(NotificationManager.class).notify(1,s.notification(text));}
 @Override public int onStartCommand(Intent i,int flags,int id){if(i!=null&&"cancel".equals(i.getAction())){TujiApp.app.cancel();}return START_NOT_STICKY;}
 @Override public void onDestroy(){current=null;if(lock!=null&&lock.isHeld())lock.release();super.onDestroy();}
 @Override public IBinder onBind(Intent i){return null;}
 @Override public void onTimeout(int id,int type){TujiApp.app.cancel();stopSelf();}
}
