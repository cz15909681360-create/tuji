package io.tuji.app;

import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.*;

/** Tuji bundles v1/v2. Original bytes are streamed unchanged; no image codec is involved. */
public final class PhotoBundle {
 public interface Progress { void update(long done,long total) throws IOException; }
 public static final class Item {
  public final File file; public final String name,path,sha256; public final long size;
  public Item(File f,String n,String p,String h,long s){file=f;name=n;path=p;sha256=h;size=s;}
 }
 public static String hex(byte[] b){StringBuilder s=new StringBuilder();for(byte v:b)s.append(String.format(Locale.ROOT,"%02x",v&255));return s.toString();}
 public static MessageDigest digest(){try{return MessageDigest.getInstance("SHA-256");}catch(Exception e){throw new IllegalStateException(e);}}
 public static String hash(File f) throws IOException {return hash(f,(d,t)->{});}
 public static String hash(File f,Progress progress) throws IOException {MessageDigest d=digest();long done=0;try(InputStream in=new FileInputStream(f)){byte[] b=new byte[131072];for(int n;(n=in.read(b))!=-1;){if(Thread.currentThread().isInterrupted())throw new IOException("任务已取消");progress.update(done,f.length());d.update(b,0,n);done+=n;}}return hex(d.digest());}
 public static String safeName(String n){n=n.replace('\\','_').replace('/','_').replace('\0','_');return n.isBlank()?"photo.jpg":n;}
 public static String windowsName(String n){n=safeName(n).replaceAll("[<>:\"|?*\\x00-\\x1f]","_").replaceAll("[. ]+$","");if(n.isBlank())n="photo.jpg";String stem=n.split("\\.",2)[0];if(stem.matches("(?i)CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9]"))n="_"+n;return n;}
 public static String uniqueName(String name,Set<String> used){String n=windowsName(name),base=n,ext="";int dot=n.lastIndexOf('.');if(dot>0){base=n.substring(0,dot);ext=n.substring(dot);}int suffix=2;while(!used.add(n.toLowerCase(Locale.ROOT)))n=base+"_"+(suffix++)+ext;return n;}
 private static byte[] extra(String name,String hash)throws Exception{byte[] data=new JSONObject().put("version",2).put("name",name).put("sha256",hash).toString().getBytes(StandardCharsets.UTF_8);if(data.length>65531)throw new IOException("照片名称过长");java.nio.ByteBuffer b=java.nio.ByteBuffer.allocate(data.length+4).order(java.nio.ByteOrder.LITTLE_ENDIAN);b.putShort((short)0x6a74).putShort((short)data.length).put(data);return b.array();}
 private static JSONObject metadata(ZipEntry entry)throws Exception{byte[] x=entry.getExtra();if(x==null)throw new IOException("缺少照片校验信息");java.nio.ByteBuffer b=java.nio.ByteBuffer.wrap(x).order(java.nio.ByteOrder.LITTLE_ENDIAN);JSONObject found=null;while(b.remaining()>=4){int id=b.getShort()&65535,n=b.getShort()&65535;if(n>b.remaining())throw new IOException("损坏的ZIP元数据");byte[] data=new byte[n];b.get(data);if(id==0x6a74){if(found!=null)throw new IOException("重复照片元数据");found=new JSONObject(new String(data,StandardCharsets.UTF_8));}}if(found==null||found.optInt("version")!=2)throw new IOException("不支持的照片元数据");return found;}
 public static void pack(List<Item> items,File target,Progress progress) throws Exception {
  if(items.isEmpty())throw new IOException("没有选择照片");
  long total=0;for(Item i:items)total=Math.addExact(total,i.file.length());
  if(target.getParentFile().getUsableSpace()<total+16L*1024*1024)throw new IOException("空间不足，无法生成照片包");
  File part=new File(target+".part");long done=0;
  try(ZipOutputStream z=new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(part)))){
   z.setLevel(0);z.setComment("TUJI_PHOTO_BUNDLE_V2");Set<String> used=new HashSet<>();byte[] b=new byte[131072];
   for(Item i:items){String path=uniqueName(i.name,used);String expected=hash(i.file,progress);ZipEntry entry=new ZipEntry(path);entry.setExtra(extra(safeName(i.name),expected));z.putNextEntry(entry);MessageDigest d=digest();long size=0;
    try(InputStream in=new FileInputStream(i.file)){for(int n;(n=in.read(b))!=-1;){z.write(b,0,n);d.update(b,0,n);size+=n;done+=n;progress.update(done,total);}}
    z.closeEntry();if(!hex(d.digest()).equals(expected))throw new IOException("原件在打包时发生变化");
   }
  }catch(Exception e){part.delete();throw e;}
  if(!part.renameTo(target)){part.delete();throw new IOException("无法保存照片包");}
 }
 public static List<Item> unpack(File zip,File staging,Progress progress) throws Exception {
  if(!staging.mkdirs()&&!staging.isDirectory())throw new IOException("无法建立临时目录");
  List<Item> out=new ArrayList<>();
  try(ZipFile z=new ZipFile(zip)){
   Set<String> entries=new HashSet<>();Enumeration<? extends ZipEntry> all=z.entries();while(all.hasMoreElements()){ZipEntry e=all.nextElement();if(!entries.add(e.getName()))throw new IOException("重复压缩条目");}
   boolean v2="TUJI_PHOTO_BUNDLE_V2".equals(z.getComment());JSONArray fs;
   if(v2){fs=new JSONArray();Enumeration<? extends ZipEntry> es=z.entries();while(es.hasMoreElements()){ZipEntry e=es.nextElement();String name=e.getName();if(e.isDirectory()||name.contains("/")||name.contains("\\")||!windowsName(name).equals(name))throw new IOException("v2照片必须平铺在根目录");JSONObject f=metadata(e);f.put("path",name).put("size",e.getSize());fs.put(f);}}
   else{ZipEntry me=z.getEntry("manifest.json");if(me==null||me.getSize()<0||me.getSize()>4*1024*1024)throw new IOException("不是有效的图迹照片包");byte[] mb;try(InputStream in=z.getInputStream(me)){mb=readBounded(in,4*1024*1024);}JSONObject m=new JSONObject(new String(mb,StandardCharsets.UTF_8));if(!m.optString("format").equals("tuji.photo-bundle")||m.optInt("version")!=1)throw new IOException("不支持的照片包版本");fs=m.getJSONArray("files");}
   if(fs.length()==0||fs.length()>10000)throw new IOException("无效的照片数量");
   long total=0;Set<String> paths=new HashSet<>();
   for(int k=0;k<fs.length();k++){JSONObject f=fs.getJSONObject(k);String path=f.getString("path");long size=f.getLong("size");
    if((!v2&&!validPath(path))||!paths.add(path)||size<0||!f.getString("sha256").matches("[a-fA-F0-9]{64}"))throw new IOException("无效的文件清单");
    ZipEntry e=z.getEntry(path);if(e==null||e.isDirectory()||e.getSize()!=size)throw new IOException("文件大小或条目不匹配");total=Math.addExact(total,size);
   }
   if(entries.size()!=paths.size()+(v2?0:1))throw new IOException("压缩包含未声明条目");
   if(staging.getUsableSpace()<total+16L*1024*1024)throw new IOException("空间不足，无法还原原照片");
   long done=0;byte[] buf=new byte[131072];
   for(int k=0;k<fs.length();k++){JSONObject f=fs.getJSONObject(k);String path=f.getString("path"),h=f.getString("sha256");long size=f.getLong("size");File target=new File(staging,"original-"+k);MessageDigest d=digest();long count=0;
    try(InputStream in=z.getInputStream(z.getEntry(path));OutputStream dest=new FileOutputStream(target)){for(int n;(n=in.read(buf))!=-1;){count+=n;if(count>size)throw new IOException("文件超出声明长度");dest.write(buf,0,n);d.update(buf,0,n);done+=n;progress.update(done,total);}}
    if(count!=size||!hex(d.digest()).equalsIgnoreCase(h))throw new IOException("原件校验失败："+f.getString("name"));
    out.add(new Item(target,safeName(f.getString("name")),path,h.toLowerCase(Locale.ROOT),size));
   }
   return out;
  }catch(Exception e){deleteTree(staging);throw e;}
 }
 public static boolean validPath(String p){if(!p.startsWith("photos/")||p.contains("\\")||p.contains("\0"))return false;for(String s:p.split("/",-1))if(s.isEmpty()||s.equals(".")||s.equals(".."))return false;return true;}
 public static byte[] readBounded(InputStream in,int limit)throws IOException{ByteArrayOutputStream o=new ByteArrayOutputStream();byte[] b=new byte[8192];for(int n;(n=in.read(b))!=-1;){if(o.size()+n>limit)throw new IOException("请求数据过大");o.write(b,0,n);}return o.toByteArray();}
 public static void deleteTree(File f){File[] cs=f.listFiles();if(cs!=null)for(File c:cs)deleteTree(c);f.delete();}
}
