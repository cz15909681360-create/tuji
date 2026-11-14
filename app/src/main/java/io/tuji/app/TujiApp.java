package io.tuji.app;
import android.app.*;
import android.content.*;
import android.net.Uri;
import android.os.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

public final class TujiApp extends Application {
 public static TujiApp app;
 public final ExecutorService jobs=Executors.newSingleThreadExecutor();
 public final ExecutorService network=Executors.newCachedThreadPool();
 public final Handler ui=new Handler(Looper.getMainLooper());
 public volatile MainActivity activity; public volatile boolean visible,cancelled,busy;
 private volatile long lastProgress;
 public volatile String status="准备就绪";public PhotoStore store;public GalleryRepository gallery;public LocalProtocol protocol;public volatile Throwable startupError;
 public TaskCache taskCache;public File stage;public List<Uri> retainedSelection=new ArrayList<>();public boolean retainedPhotoMode=true;
 @Override public void onCreate(){super.onCreate();app=this;store=new PhotoStore(this);gallery=new GalleryRepository(this);stage=new File(getCacheDir(),"jobs");taskCache=new TaskCache(stage);jobs.execute(()->{long remaining=taskCache.clean();if(remaining>0)update("任务缓存残留 "+remaining/1024+" KB，可在设置重试清理");});
  // Remove pending rows left behind by process termination during our own imports.
  try { getContentResolver().delete(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,"is_pending=1 AND owner_package_name=?",new String[]{getPackageName()}); }catch(Exception ignored){}
  network.execute(()->{try{protocol=new LocalProtocol(this);protocol.start();ui.post(()->{if(activity!=null)activity.devicesChanged();});update("图迹已就绪 · 局域网设备发现中");}catch(Exception e){startupError=e;update("局域网服务启动失败："+e.getMessage());}});
 }
 public void update(String text){status=text;TransferService.update(text);ui.post(()->{MainActivity a=activity;if(a!=null)a.refreshStatus();});}
 public void progress(long done,long total)throws IOException{if(cancelled||Thread.currentThread().isInterrupted())throw new IOException("任务已取消");if(total>0&&SystemClock.elapsedRealtime()-lastProgress>200){lastProgress=SystemClock.elapsedRealtime();update("处理中 "+(done*100/Math.max(1,total))+"% · "+(done/1024/1024)+" MB");}}
 public synchronized boolean begin(String title){if(busy)return false;busy=true;taskCache.active(true);cancelled=false;try{startForegroundService(new Intent(this,TransferService.class));}catch(Exception e){busy=false;taskCache.active(false);update("请先打开图迹再开始任务");return false;}update(title);return true;}
 public void cancel(){cancelled=true;update("正在取消任务…");if(protocol!=null)protocol.abort();}
 public synchronized void finish(String text){if(text.startsWith("发送完成"))retainedSelection=new ArrayList<>();taskCache.active(false);long residue=taskCache.clean();if(residue>0)text+=" · 临时缓存清理未完成，可在设置重试";busy=false;cancelled=false;stopService(new Intent(this,TransferService.class));update(text);ui.post(()->{if(activity!=null)activity.onTaskFinished();});}
 public boolean trusted(String fp){return getSharedPreferences("trust",0).contains(fp);}
 public void trust(String fp,String name){getSharedPreferences("trust",0).edit().putString(fp,name).apply();}
 public void importBundle(File zip)throws Exception{
  File dir=taskCache.allocate("extract-");int count=0;
  try { List<PhotoBundle.Item> items=PhotoBundle.unpack(zip,dir,this::progress);
   // Validate every entry before exposing any entry to MediaStore.
   for(PhotoBundle.Item i:items){progress(0,0);if(store.save(i))count++;}
   update("照片导入完成 · 新增 "+count+" 张，其余已去重");
  }finally{PhotoBundle.deleteTree(dir);}
 }
 public void importLocal(List<Uri> uris){if(!begin("读取相册原件"))return;jobs.execute(()->{File dir=taskCache.allocate("import-");int count=0;try{
   for(Uri u:uris){File f=store.snapshot(u,dir,this::progress);store.index(u,store.name(u),f,PhotoBundle.hash(f));f.delete();count++;}
   finish("已导入 "+count+" 张照片");
  }catch(Exception e){finish("导入失败："+e.getMessage());}finally{PhotoBundle.deleteTree(dir);}});}
}
