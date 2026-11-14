package io.tuji.app;
import android.Manifest;
import android.content.*;
import android.content.pm.PackageManager;
import android.database.*;
import android.database.sqlite.*;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.*;
import android.provider.MediaStore;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;

/** Authorized MediaStore snapshot plus a URI-keyed EXIF cache. Browsing never copies or hashes originals. */
public final class GalleryRepository {
 public static final int PENDING=0,LOCATED=1,NO_GPS=2,FAILED=3,NO_PERMISSION=4;
 public static final class Item extends PhotoStore.Photo {
  public String key,album,day,signature;public long taken,size;public volatile int location=PENDING;
  public String label(){switch(location){case LOCATED:return "有定位";case NO_GPS:return "无定位";case FAILED:return "读取失败";case NO_PERMISSION:return "缺少位置权限";default:return "待解析";}}
 }
 final TujiApp app;final ScheduledExecutorService worker=Executors.newSingleThreadScheduledExecutor();
 final Set<Runnable> listeners=new HashSet<>();final ConcurrentLinkedDeque<String> priority=new ConcurrentLinkedDeque<>();
 final Cache cache;volatile List<Item> snapshot=Collections.emptyList();volatile Map<String,Item> lookup=Collections.emptyMap();
 volatile boolean active,loading;volatile long revision;int scanOffset;long queryToken;String permissionToken="";boolean notifyPending;boolean scanScheduled,refreshScheduled;
 final ContentObserver observer;
 public GalleryRepository(TujiApp a){app=a;cache=new Cache(a);observer=new ContentObserver(a.ui){@Override public void onChange(boolean self){refresh();}};a.getContentResolver().registerContentObserver(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,true,observer);}
 public boolean full(){return app.checkSelfPermission(Build.VERSION.SDK_INT>=33?Manifest.permission.READ_MEDIA_IMAGES:Manifest.permission.READ_EXTERNAL_STORAGE)==PackageManager.PERMISSION_GRANTED;}
 public boolean partial(){return Build.VERSION.SDK_INT>=34&&app.checkSelfPermission(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)==PackageManager.PERMISSION_GRANTED&&!full();}
 public boolean gpsAllowed(){return app.checkSelfPermission(Manifest.permission.ACCESS_MEDIA_LOCATION)==PackageManager.PERMISSION_GRANTED;}
 public String access(){return full()?"全部图片":partial()?"可访问照片":"应用照片 · 未授权图库";}
 public List<Item> items(){return snapshot;}
 public Item find(String uri){return lookup.get(uri);}
 public List<PhotoStore.Photo> located(){List<PhotoStore.Photo> r=new ArrayList<>();if(!gpsAllowed())return r;for(Item i:snapshot)if(i.location==LOCATED&&i.lat!=null&&i.lon!=null)r.add(i);return r;}
 public String progress(){int done=0,located=0;for(Item i:snapshot){if(i.location!=PENDING)done++;if(i.location==LOCATED)located++;}return (loading?"读取图库中 · ":"")+done+" / "+snapshot.size()+" 已解析 · "+located+" 张有定位";}
 public void listen(Runnable r){listeners.add(r);}
 public void unlisten(Runnable r){listeners.remove(r);}
 void changed(){revision++;synchronized(this){if(notifyPending)return;notifyPending=true;}app.ui.postDelayed(()->{synchronized(this){notifyPending=false;}for(Runnable r:new ArrayList<>(listeners))r.run();},180);}
 public void foreground(boolean value){active=value;if(value){String token=full()+":"+partial()+":"+gpsAllowed();if(!permissionToken.equals(token)){permissionToken=token;snapshot=Collections.emptyList();lookup=Collections.emptyMap();changed();}refresh();worker.execute(this::scheduleScan);}}
 public void prioritize(String uri){priority.addFirst(uri);worker.execute(this::scheduleScan);}
 public void refresh(){synchronized(this){if(refreshScheduled)return;refreshScheduled=true;}worker.schedule(()->{synchronized(this){refreshScheduled=false;}if(active)query();},150,TimeUnit.MILLISECONDS);}
 void query(){loading=true;changed();long token=++queryToken;List<Item> result=new ArrayList<>();Map<String,Item> previous=lookup;Map<String,Item> cached=cache.load();boolean gps=gpsAllowed();
  try{
   Uri collection=MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL);
   String[] projection={"_id","_display_name","_size","datetaken","date_added","date_modified","bucket_display_name","volume_name"};
   String libraryVersion=MediaStore.getVersion(app);Set<String> queried=new HashSet<>();SimpleDateFormat day=new SimpleDateFormat("yyyy年MM月dd日",Locale.CHINA);int offset=0;
   while(active){Bundle args=new Bundle();args.putString(ContentResolver.QUERY_ARG_SQL_SORT_ORDER,"datetaken DESC, _id DESC");args.putInt(ContentResolver.QUERY_ARG_LIMIT,200);args.putInt(ContentResolver.QUERY_ARG_OFFSET,offset);int got=0,before=result.size();
    try(Cursor c=app.getContentResolver().query(collection,projection,args,null)){if(c==null)break;while(c.moveToNext()){got++;Item i=new Item();i.id=c.getLong(0);i.name=c.getString(1);i.size=c.getLong(2);i.taken=c.isNull(3)||c.getLong(3)<=0?c.getLong(4)*1000:c.getLong(3);i.album=c.getString(6);if(i.album==null||i.album.isBlank())i.album="其他图片";String volume=c.getString(7);if(volume==null)volume=MediaStore.VOLUME_EXTERNAL;i.uri=ContentUris.withAppendedId(MediaStore.Images.Media.getContentUri(volume),i.id).toString();i.key=i.uri;if(!queried.add(i.uri))continue;i.signature=libraryVersion+":"+i.size+":"+c.getLong(5)+":"+i.taken;i.day=day.format(new Date(i.taken));i.time=new SimpleDateFormat("yyyy-MM-dd HH:mm:ss",Locale.CHINA).format(new Date(i.taken));
     Item old=previous.get(i.uri);if(old==null)old=cached.get(i.uri);if(gps&&old!=null&&i.signature.equals(old.signature)&&old.location!=NO_PERMISSION){i.location=old.location;i.lat=old.lat;i.lon=old.lon;}else if(!gps)i.location=NO_PERMISSION;result.add(i);
    }}
    offset+=got;if(got<200||before==result.size())break;
   }
   if(!active){loading=false;return;}
   // Legacy persisted document URIs remain available only while their grants are still valid.
   Set<String> known=new HashSet<>();for(Item i:result)known.add(i.uri);
   for(PhotoStore.Photo old:app.store.all()){Uri u=Uri.parse(old.uri);try{Uri media=MediaStore.getMediaUri(app,u);if(media!=null&&MediaStore.AUTHORITY.equals(media.getAuthority()))continue;}catch(Exception ignored){}if(MediaStore.AUTHORITY.equals(u.getAuthority())||known.contains(old.uri))continue;try(android.os.ParcelFileDescriptor fd=app.getContentResolver().openFileDescriptor(u,"r")){if(fd==null)continue;Item i=new Item();i.id=old.id;i.uri=old.uri;i.key=old.uri;i.name=old.name;i.album="其他已授权照片";i.day="其他照片";i.time=old.time;i.signature="legacy:"+old.hash;i.location=gps?PENDING:NO_PERMISSION;result.add(i);}catch(Exception ignored){}}
   result.sort((x,y)->Long.compare(y.taken,x.taken));Map<String,Item> byUri=new HashMap<>();for(Item i:result)byUri.put(i.uri,i);cache.prune(byUri.keySet());snapshot=Collections.unmodifiableList(result);lookup=byUri;scanOffset=0;if(!gps)cache.clearGps();
  }catch(SecurityException e){snapshot=Collections.emptyList();lookup=Collections.emptyMap();}catch(Exception e){app.update("图库读取失败，可在授权后重试");}finally{loading=false;changed();scheduleScan();}
 }
 void scheduleScan(){if(scanScheduled||!active||!gpsAllowed())return;scanScheduled=true;worker.schedule(this::scan,40,TimeUnit.MILLISECONDS);}
 void scan(){scanScheduled=false;if(!active||!gpsAllowed())return;int count=0;List<Item> list=snapshot;
  cache.getWritableDatabase().beginTransaction();try{while(count<8&&active){Item i=null;String key=priority.pollFirst();if(key!=null)i=lookup.get(key);if(i==null){while(scanOffset<list.size()){Item candidate=list.get(scanOffset++);if(candidate.location==PENDING){i=candidate;break;}}}if(i==null)break;if(i.location!=PENDING&&i.location!=FAILED)continue;read(i);cache.save(i);count++;}cache.getWritableDatabase().setTransactionSuccessful();}finally{cache.getWritableDatabase().endTransaction();}
  if(count>0)changed();if(scanOffset<snapshot.size()||!priority.isEmpty())scheduleScan();
 }
 void read(Item i){i.lat=null;i.lon=null;try{Uri u=app.store.originalUri(Uri.parse(i.uri));boolean jpeg=false;try(InputStream in=app.getContentResolver().openInputStream(u)){if(in==null)throw new IOException();jpeg=in.read()==255&&in.read()==216;}
   if(jpeg){try(InputStream in=app.getContentResolver().openInputStream(u)){double[] ll=ExifGps.read(in);if(ll!=null){i.lat=ll[0];i.lon=ll[1];}}}
   else try(android.os.ParcelFileDescriptor fd=app.getContentResolver().openFileDescriptor(u,"r")){if(fd==null)throw new IOException();ExifInterface exif=new ExifInterface(fd.getFileDescriptor());i.lat=PhotoStore.coordinate(exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE),exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE_REF));i.lon=PhotoStore.coordinate(exif.getAttribute(ExifInterface.TAG_GPS_LONGITUDE),exif.getAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF));}
   if(i.lat!=null&&i.lon!=null&&Math.abs(i.lat)<=90&&Math.abs(i.lon)<=180)i.location=LOCATED;else{i.lat=null;i.lon=null;i.location=NO_GPS;}
  }catch(SecurityException e){i.location=NO_PERMISSION;}catch(Exception e){i.location=gpsAllowed()?FAILED:NO_PERMISSION;}}
 static final class Cache extends SQLiteOpenHelper{
  Cache(Context c){super(c,"gallery.db",null,1);}
  public void onCreate(SQLiteDatabase d){d.execSQL("CREATE TABLE gps(uri TEXT PRIMARY KEY,signature TEXT,state INTEGER,lat REAL,lon REAL)");}
  public void onUpgrade(SQLiteDatabase d,int o,int n){}
  Map<String,Item> load(){Map<String,Item> out=new HashMap<>();try(Cursor c=getReadableDatabase().rawQuery("SELECT * FROM gps",null)){while(c.moveToNext()){Item i=new Item();i.uri=c.getString(0);i.signature=c.getString(1);i.location=c.getInt(2);if(!c.isNull(3)&&!c.isNull(4)){i.lat=c.getDouble(3);i.lon=c.getDouble(4);}out.put(i.uri,i);}}return out;}
  void save(Item i){ContentValues v=new ContentValues();v.put("uri",i.uri);v.put("signature",i.signature);v.put("state",i.location);if(i.lat!=null){v.put("lat",i.lat);v.put("lon",i.lon);}getWritableDatabase().insertWithOnConflict("gps",null,v,SQLiteDatabase.CONFLICT_REPLACE);}
  void prune(Set<String> uris){SQLiteDatabase db=getWritableDatabase();db.beginTransaction();try{db.execSQL("CREATE TEMP TABLE IF NOT EXISTS visible_gallery(uri TEXT PRIMARY KEY)");db.execSQL("DELETE FROM visible_gallery");android.database.sqlite.SQLiteStatement insert=db.compileStatement("INSERT OR IGNORE INTO visible_gallery(uri) VALUES(?)");try{for(String uri:uris){insert.bindString(1,uri);insert.executeInsert();}}finally{insert.close();}db.execSQL("DELETE FROM gps WHERE uri NOT IN (SELECT uri FROM visible_gallery)");db.execSQL("DELETE FROM visible_gallery");db.setTransactionSuccessful();}finally{db.endTransaction();}}
  void clearGps(){getWritableDatabase().execSQL("UPDATE gps SET lat=NULL,lon=NULL,state=4");}
 }
}
