package io.tuji.app;

import android.content.*;
import android.database.Cursor;
import android.database.sqlite.*;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.*;
import android.provider.*;
import android.webkit.MimeTypeMap;
import java.io.*;
import java.util.*;

public final class PhotoStore extends SQLiteOpenHelper {
 private final Context c;
 public static class Photo { public long id; public String uri,name,hash,time; public Double lat,lon; }
 public PhotoStore(Context c){super(c,"tuji.db",null,1);this.c=c;}
 @Override public void onCreate(SQLiteDatabase d){d.execSQL("CREATE TABLE photos(id INTEGER PRIMARY KEY,uri TEXT NOT NULL,name TEXT,hash TEXT UNIQUE NOT NULL,time TEXT,lat REAL,lon REAL)");}
 @Override public void onUpgrade(SQLiteDatabase d,int o,int n){}
 public List<Photo> all(){List<Photo> a=new ArrayList<>();try(Cursor r=getReadableDatabase().rawQuery("SELECT * FROM photos ORDER BY id DESC",null)){while(r.moveToNext()){Photo p=new Photo();p.id=r.getLong(0);p.uri=r.getString(1);p.name=r.getString(2);p.hash=r.getString(3);p.time=r.getString(4);if(!r.isNull(5)&&!r.isNull(6)){p.lat=r.getDouble(5);p.lon=r.getDouble(6);}a.add(p);}}return a;}
 public boolean has(String hash){String uri;try(Cursor r=getReadableDatabase().rawQuery("SELECT uri FROM photos WHERE hash=?",new String[]{hash})){if(!r.moveToFirst())return false;uri=r.getString(0);}try(android.os.ParcelFileDescriptor fd=c.getContentResolver().openFileDescriptor(Uri.parse(uri),"r")){if(fd!=null)return true;}catch(java.io.FileNotFoundException missing){getWritableDatabase().delete("photos","hash=?",new String[]{hash});return false;}catch(SecurityException denied){throw denied;}catch(IOException failure){throw new IllegalStateException("无法验证已保存照片，请稍后重试",failure);}return false;}
 public void remove(long id){getWritableDatabase().delete("photos","id=?",new String[]{""+id});}
 public void removeUri(String uri){getWritableDatabase().delete("photos","uri=?",new String[]{uri});}
 public String name(Uri u){try(Cursor r=c.getContentResolver().query(u,new String[]{OpenableColumns.DISPLAY_NAME},null,null,null)){if(r!=null&&r.moveToFirst())return PhotoBundle.safeName(r.getString(0));}catch(Exception ignored){}return "photo-"+System.currentTimeMillis()+".jpg";}
 /** Reacquire a MediaStore original URI even when a share supplied a redacted view. */
 public Uri originalUri(Uri source)throws IOException{
  Uri media=source;
  if(!MediaStore.AUTHORITY.equals(source.getAuthority())){
   try{Uri mapped=MediaStore.getMediaUri(c,source);if(mapped!=null)media=mapped;}catch(Exception ignored){}
  }
  if(!MediaStore.AUTHORITY.equals(media.getAuthority())&&!DocumentsContract.isDocumentUri(c,media)){
   // OEM share providers may expose a redacted view. Resolve only an unambiguous local original.
   String display=null;long size=-1;
   try(Cursor r=c.getContentResolver().query(source,new String[]{OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE},null,null,null)){
    if(r!=null&&r.moveToFirst()){display=r.getString(0);if(!r.isNull(1))size=r.getLong(1);}
   }catch(Exception ignored){}
   if(display!=null&&size>=0){try(Cursor r=c.getContentResolver().query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,new String[]{MediaStore.Images.Media._ID},MediaStore.Images.Media.DISPLAY_NAME+"=? AND "+MediaStore.Images.Media.SIZE+"=?",new String[]{display,Long.toString(size)},null)){
     if(r!=null&&r.getCount()==1&&r.moveToFirst())media=ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,r.getLong(0));
   }catch(Exception ignored){}}
  }
  if(MediaStore.AUTHORITY.equals(media.getAuthority())){
   if(c.checkSelfPermission("android.permission.ACCESS_MEDIA_LOCATION")!=android.content.pm.PackageManager.PERMISSION_GRANTED)throw new IOException("请允许读取照片拍摄位置后重试，以取得原件");
   return MediaStore.setRequireOriginal(media);
  }
  // A document provider can supply real original files, but a shared provider view cannot prove that.
  if("content".equals(media.getScheme())&&!DocumentsContract.isDocumentUri(c,media))throw new IOException("此分享入口无法确认原件，请在图迹内通过系统文件选择器选择 DCIM 中的原照片");
  return media;
 }
 public File snapshot(Uri uri,File dir,PhotoBundle.Progress progress)throws Exception{
  Uri original=originalUri(uri);dir.mkdirs();File dest=new File(dir,UUID.randomUUID().toString());
  try(InputStream in=c.getContentResolver().openInputStream(original);OutputStream out=new FileOutputStream(dest)){
   if(in==null)throw new IOException("无法打开原照片");byte[] b=new byte[131072];long nbytes=0;for(int n;(n=in.read(b))!=-1;){if(dir.getUsableSpace()<n+8L*1024*1024)throw new IOException("空间不足");out.write(b,0,n);nbytes+=n;progress.update(nbytes,0);}
  }catch(Exception e){dest.delete();throw e;}return dest;
 }
 public Photo metadata(File file){Photo p=new Photo();p.time="";try{ExifInterface e=new ExifInterface(file);p.time=e.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL);float[] ll=new float[2];if(e.getLatLong(ll)&&Double.isFinite(ll[0])&&Double.isFinite(ll[1])&&Math.abs(ll[0])<=90&&Math.abs(ll[1])<=180){
   // getLatLong(float[]) loses precision; use EXIF rational values directly.
   p.lat=coordinate(e.getAttribute(ExifInterface.TAG_GPS_LATITUDE),e.getAttribute(ExifInterface.TAG_GPS_LATITUDE_REF));
   p.lon=coordinate(e.getAttribute(ExifInterface.TAG_GPS_LONGITUDE),e.getAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF));
  }}catch(Exception ignored){}double[] exact=ExifGps.read(file);if(exact!=null){p.lat=exact[0];p.lon=exact[1];}else{try(InputStream jpeg=new FileInputStream(file)){if(jpeg.read()==255&&jpeg.read()==216){p.lat=null;p.lon=null;}}catch(Exception ignored){}}return p;}
 public static Double coordinate(String s,String ref){if(!("N".equals(ref)||"S".equals(ref)||"E".equals(ref)||"W".equals(ref)))return null;try{String[] v=s.split(",");double d=0;for(int k=0;k<3;k++){String[] q=v[k].trim().split("/");double value=Double.parseDouble(q[0])/(q.length==2?Double.parseDouble(q[1]):1);d+=value/Math.pow(60,k);}if(!Double.isFinite(d))return null;if("S".equals(ref)||"W".equals(ref))d=-d;return d;}catch(Exception e){return null;}}
 public void index(Uri uri,String name,File original,String hash){if(has(hash)){refreshMetadata(hash,original);return;}Photo p=metadata(original);ContentValues v=new ContentValues();Uri indexed=uri;try{Uri originalMedia=originalUri(uri);if(MediaStore.AUTHORITY.equals(originalMedia.getAuthority()))indexed=originalMedia.buildUpon().clearQuery().build();}catch(Exception ignored){}v.put("uri",indexed.toString());v.put("name",name);v.put("hash",hash);v.put("time",p.time);if(p.lat!=null&&p.lon!=null){v.put("lat",p.lat);v.put("lon",p.lon);}getWritableDatabase().insertOrThrow("photos",null,v);}
 private void refreshMetadata(String hash,File original){Photo p=metadata(original);ContentValues v=new ContentValues();v.put("time",p.time);if(p.lat!=null&&p.lon!=null){v.put("lat",p.lat);v.put("lon",p.lon);}else{v.putNull("lat");v.putNull("lon");}getWritableDatabase().update("photos",v,"hash=?",new String[]{hash});}
 public boolean save(PhotoBundle.Item i)throws Exception {
  if(has(i.sha256)){refreshMetadata(i.sha256,i.file);return false;}
  ContentResolver r=c.getContentResolver();ContentValues v=new ContentValues();v.put(MediaStore.Images.Media.DISPLAY_NAME,i.name);v.put(MediaStore.Images.Media.MIME_TYPE,mime(i.name));v.put(MediaStore.Images.Media.RELATIVE_PATH,"Pictures/图迹");v.put(MediaStore.Images.Media.IS_PENDING,1);
  Uri uri=r.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,v);if(uri==null)throw new IOException("无法创建系统相册照片");
  try{
   try(OutputStream out=r.openOutputStream(uri);InputStream in=new FileInputStream(i.file)){if(out==null)throw new IOException("无法写入系统相册");byte[] b=new byte[131072];for(int n;(n=in.read(b))!=-1;)out.write(b,0,n);}
   // Re-read the exact bytes before publishing; MediaStore must not transform originals.
   try(InputStream in=r.openInputStream(uri)){java.security.MessageDigest d=PhotoBundle.digest();byte[] b=new byte[131072];for(int n;(n=in.read(b))!=-1;)d.update(b,0,n);if(!PhotoBundle.hex(d.digest()).equals(i.sha256))throw new IOException("系统相册原件校验失败");}
   v.clear();v.put(MediaStore.Images.Media.IS_PENDING,0);r.update(uri,v,null,null);index(uri,i.name,i.file,i.sha256);return true;
  }catch(Exception e){r.delete(uri,null,null);throw e;}
 }
 public static String mime(String name){String ext=name.contains(".")?name.substring(name.lastIndexOf('.')+1).toLowerCase(Locale.ROOT):"";String s=MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);return s==null?"image/jpeg":s;}
}
