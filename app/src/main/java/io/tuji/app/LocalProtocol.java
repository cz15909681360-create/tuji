package io.tuji.app;

import android.content.*;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.provider.MediaStore;
import org.json.*;
import javax.net.ssl.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

/** Native Java LocalSend v2.2 HTTPS implementation. No Flutter/Rust runtime. */
public final class LocalProtocol {
 public static final int PORT=53317;public static final String GROUP="224.0.0.167",API="/api/localsend/v2";
 final TujiApp app;final TlsIdentity tls;
 public final Map<String,Device> devices=new ConcurrentHashMap<>();
 final ScheduledExecutorService timer=Executors.newSingleThreadScheduledExecutor();
 SSLServerSocket server;MulticastSocket multicast;WifiManager.MulticastLock lock;
 volatile Session session;volatile boolean awaiting;
 final Set<SSLSocket> active=ConcurrentHashMap.newKeySet();
 public void abort(){for(SSLSocket s:active)try{s.close();}catch(Exception ignored){}Session x=session;if(x!=null&&!x.uploading){session=null;app.finish("任务已取消");}}
 public static final class Device{public String alias,fp,ip;public int port;public String toString(){return alias+" · "+ip;}}
 static final class Session{String id,fp,alias;JSONObject files,tokens;Set<String> done=new HashSet<>();long touched=System.currentTimeMillis();volatile boolean uploading,cancelled;}
 public LocalProtocol(TujiApp a)throws Exception{app=a;tls=new TlsIdentity();}
 public String alias(){return app.getSharedPreferences("settings",0).getString("alias","图迹 · "+Build.MODEL);}
 public JSONObject info()throws JSONException{return new JSONObject().put("alias",alias()).put("version","2.2").put("deviceModel",Build.MODEL).put("deviceType","mobile").put("fingerprint",tls.fingerprint).put("port",PORT).put("protocol","https").put("download",false);}
 public String fingerprint(){return tls.fingerprint;}
 public void start()throws Exception{
  server=(SSLServerSocket)tls.context.getServerSocketFactory().createServerSocket();server.setReuseAddress(true);server.bind(new InetSocketAddress(PORT));server.setNeedClientAuth(true);
  app.network.execute(()->{while(!server.isClosed())try{SSLSocket s=(SSLSocket)server.accept();app.network.execute(()->handle(s));}catch(Exception e){if(!server.isClosed())app.update("接收服务异常："+e.getMessage());}});
  try{
   WifiManager wm=(WifiManager)app.getApplicationContext().getSystemService(Context.WIFI_SERVICE);lock=wm.createMulticastLock("tuji-discovery");lock.setReferenceCounted(false);lock.acquire();
   multicast=new MulticastSocket(null);multicast.setReuseAddress(true);multicast.bind(new InetSocketAddress(PORT));multicast.setTimeToLive(1);
   Enumeration<NetworkInterface> interfaces=NetworkInterface.getNetworkInterfaces();while(interfaces.hasMoreElements()){NetworkInterface ni=interfaces.nextElement();if(ni.isUp()&&!ni.isLoopback()&&ni.supportsMulticast())try{multicast.joinGroup(new InetSocketAddress(GROUP,PORT),ni);}catch(Exception ignored){}}
   app.network.execute(()->{byte[] b=new byte[65507];while(!multicast.isClosed())try{DatagramPacket dp=new DatagramPacket(b,b.length);multicast.receive(dp);JSONObject j=new JSONObject(new String(dp.getData(),dp.getOffset(),dp.getLength(),StandardCharsets.UTF_8));Device d=remember(j,dp.getAddress().getHostAddress());if(d!=null&&j.optBoolean("announce"))app.network.execute(()->{try{request(d,"POST",API+"/register",info().toString().getBytes(StandardCharsets.UTF_8),null,null);}catch(Exception ignored){}});}catch(Exception ignored){}});
  }catch(Exception e){app.update("自动发现不可用，可手动添加设备 IP");}
  timer.scheduleWithFixedDelay(()->{if(app.visible)announce();Session s=session;if(s!=null&&System.currentTimeMillis()-s.touched>180000&&!s.uploading){session=null;app.finish("接收等待超时，请重新发送");}},0,12,TimeUnit.SECONDS);
 }
 public void announce(){if(multicast==null)return;try{byte[] b=info().put("announce",true).toString().getBytes(StandardCharsets.UTF_8);Enumeration<NetworkInterface> ns=NetworkInterface.getNetworkInterfaces();while(ns.hasMoreElements()){NetworkInterface ni=ns.nextElement();if(ni.isUp()&&!ni.isLoopback()&&ni.supportsMulticast())try{multicast.setNetworkInterface(ni);multicast.send(new DatagramPacket(b,b.length,InetAddress.getByName(GROUP),PORT));}catch(Exception ignored){}}}catch(Exception ignored){}}
 private Device remember(JSONObject j,String ip){String fp=j.optString("fingerprint");if(fp.equals(tls.fingerprint)||fp.isEmpty()||!j.optString("protocol","https").equals("https"))return null;int port=j.optInt("port",PORT);if(port<1||port>65535)return null;Device d=new Device();d.alias=j.optString("alias","LocalSend");d.fp=fp.toUpperCase(Locale.ROOT);d.ip=ip;d.port=port;devices.put(d.fp,d);app.ui.post(()->{if(app.activity!=null)app.activity.devicesChanged();});return d;}
 public void addIp(String ip){app.network.execute(()->{try{Device d=new Device();java.net.URI target=new java.net.URI(ip.contains("://")?ip.trim():"https://"+ip.trim());if(!"https".equals(target.getScheme())||target.getHost()==null)throw new IOException("请输入设备 IP，可附带端口");d.ip=target.getHost();d.port=target.getPort()<0?PORT:target.getPort();d.fp=null;String r=request(d,"GET",API+"/info",null,null,null);JSONObject j=new JSONObject(r);j.put("fingerprint",d.fp);j.put("port",d.port);j.put("protocol","https");remember(j,d.ip);app.update("已找到设备 "+j.optString("alias"));}catch(Exception e){android.util.Log.w("TujiProtocol","Connection failed",e);app.update("连接失败："+e.getMessage());}});}
 private static JSONObject json(InputStream in)throws Exception{return new JSONObject(new String(PhotoBundle.readBounded(in,2*1024*1024),StandardCharsets.UTF_8));}
 private void handle(SSLSocket socket){
  try(SSLSocket s=socket){s.setSoTimeout(180000);s.startHandshake();String peer=TlsIdentity.peer(s);HttpWire.Message req=HttpWire.read(s.getInputStream());String[] first=req.first.split(" ");if(first.length!=3){HttpWire.response(s.getOutputStream(),400,"{}");return;}Uri u=Uri.parse(first[1]);String path=u.getPath();OutputStream out=s.getOutputStream();
   if(path.equals(API+"/info")&&first[0].equals("GET")){HttpWire.response(out,200,info().toString());return;}
   if(path.equals(API+"/register")&&first[0].equals("POST")){JSONObject j=json(req.body);j.put("fingerprint",peer);remember(j,s.getInetAddress().getHostAddress());HttpWire.response(out,200,info().toString());return;}
   if(!first[0].equals("POST")){HttpWire.response(out,405,"{}");return;}
   if(path.equals(API+"/prepare-upload")){prepare(peer,json(req.body),out);return;}
   Session x=session;
   if(path.equals(API+"/cancel")){if(x!=null&&x.fp.equals(peer)&&x.id.equals(u.getQueryParameter("sessionId"))){x.cancelled=true;app.cancelled=true;if(!x.uploading){session=null;app.finish("对方已取消传输");}}HttpWire.response(out,200,"{}");return;}
   if(path.equals(API+"/upload")){
    if(x==null||!x.fp.equals(peer)||!x.id.equals(u.getQueryParameter("sessionId"))){HttpWire.response(out,403,"{}");return;}
    synchronized(x){if(x.cancelled)throw new IOException("传输已取消");String id=u.getQueryParameter("fileId");if(id==null||x.done.contains(id)||!x.tokens.has(id)||!x.tokens.getString(id).equals(u.getQueryParameter("token"))){HttpWire.response(out,403,"{}");return;}
     JSONObject f=x.files.getJSONObject(id);long expected=f.getLong("size");File tmp=app.taskCache.allocate("incoming-");
     x.uploading=true;active.add(s);try{java.security.MessageDigest hash=PhotoBundle.digest();long count=0;x.touched=System.currentTimeMillis();try(OutputStream file=new FileOutputStream(tmp)){byte[] b=new byte[131072];for(int n;(n=req.body.read(b))!=-1;){if(x.cancelled)throw new IOException("传输已取消");count+=n;if(count>expected)throw new IOException("文件超出声明大小");file.write(b,0,n);hash.update(b,0,n);app.progress(count,expected);x.touched=System.currentTimeMillis();}}
      String got=PhotoBundle.hex(hash.digest()),wanted=f.optString("sha256","");if(count!=expected||(!wanted.isEmpty()&&!wanted.equalsIgnoreCase("null")&&!got.equalsIgnoreCase(wanted)))throw new IOException("文件长度或哈希校验失败");
      String name=PhotoBundle.safeName(f.getString("fileName"));if(name.toLowerCase(Locale.ROOT).endsWith(".tuji.zip"))app.importBundle(tmp);else saveDownload(tmp,name,f.optString("fileType","application/octet-stream"));
      x.done.add(id);HttpWire.response(out,200,"{}");if(x.done.size()==x.files.length()){session=null;app.finish("接收完成 · "+x.done.size()+" 个文件");}
     }catch(Exception e){session=null;app.finish("接收失败："+e.getMessage());HttpWire.response(out,400,"{}");}finally{x.uploading=false;active.remove(s);tmp.delete();}
    }return;
   }
   HttpWire.response(out,404,"{}");
  }catch(Exception e){android.util.Log.w("TujiProtocol","TLS or HTTP request failed",e);}
 }
 private void prepare(String peer,JSONObject body,OutputStream out)throws Exception{
  synchronized(this){if(awaiting||session!=null||app.busy||!app.visible){HttpWire.response(out,409,"{}");return;}awaiting=true;}
  boolean accepted=false;
  try{
   JSONObject files=body.getJSONObject("files");if(files.length()<1||files.length()>10000){HttpWire.response(out,400,"{}");return;}
   long total=0;for(Iterator<String> it=files.keys();it.hasNext();){String id=it.next();JSONObject f=files.getJSONObject(id);long size=f.getLong("size");if(size<0||f.getString("fileName").isBlank())throw new IOException("无效文件");total=Math.addExact(total,size);}
   if(app.stage.getUsableSpace()<Math.addExact(Math.multiplyExact(total,2),16L*1024*1024)){HttpWire.response(out,413,"{}");return;}
   String name=body.getJSONObject("info").optString("alias","LocalSend");
   if(app.trusted(peer))accepted=true;else{CountDownLatch latch=new CountDownLatch(1);boolean[] decision={false};final long bytes=total;app.ui.post(()->{MainActivity a=app.activity;if(a==null||!app.visible){latch.countDown();return;}a.confirmIncoming(name,peer,files.length(),bytes,yes->{decision[0]=yes;latch.countDown();});});latch.await(90,TimeUnit.SECONDS);accepted=decision[0];}
   if(!accepted){HttpWire.response(out,403,"{}");return;}
   if(!app.begin("正在接收来自 "+name+" 的文件")){HttpWire.response(out,409,"{}");return;}
   Session x=new Session();x.id=UUID.randomUUID().toString();x.fp=peer;x.alias=name;x.files=files;x.tokens=new JSONObject();for(Iterator<String> i=files.keys();i.hasNext();)x.tokens.put(i.next(),UUID.randomUUID().toString());session=x;
   HttpWire.response(out,200,new JSONObject().put("sessionId",x.id).put("files",x.tokens).toString());
   timer.schedule(()->{if(session==x&&System.currentTimeMillis()-x.touched>170000){session=null;app.finish("接收等待超时，请重新发送");}},180,TimeUnit.SECONDS);
  }finally{awaiting=false;}
 }
 private void saveDownload(File file,String name,String mime)throws Exception{
  ContentValues v=new ContentValues();v.put(MediaStore.Downloads.DISPLAY_NAME,name);v.put(MediaStore.Downloads.MIME_TYPE,mime);v.put(MediaStore.Downloads.RELATIVE_PATH,"Download/图迹");v.put(MediaStore.Downloads.IS_PENDING,1);Uri u=app.getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,v);if(u==null)throw new IOException("无法保存下载文件");
  try{try(InputStream in=new FileInputStream(file);OutputStream o=app.getContentResolver().openOutputStream(u)){if(o==null)throw new IOException("无法写入下载文件");byte[] b=new byte[131072];for(int n;(n=in.read(b))!=-1;){app.progress(0,0);o.write(b,0,n);}}v.clear();v.put(MediaStore.Downloads.IS_PENDING,0);app.getContentResolver().update(u,v,null,null);}catch(Exception e){app.getContentResolver().delete(u,null,null);throw e;}
 }
 public String request(Device d,String method,String path,byte[] data,File file,PhotoBundle.Progress progress)throws Exception{
  SSLSocket socket=(SSLSocket)tls.context.getSocketFactory().createSocket();if(app.busy)active.add(socket);try(SSLSocket s=socket){s.connect(new InetSocketAddress(d.ip,d.port),7000);s.setSoTimeout(15000);s.startHandshake();s.setSoTimeout(180000);String real=TlsIdentity.peer(s);if(d.fp!=null&&!real.equalsIgnoreCase(d.fp))throw new SSLHandshakeException("设备证书已变化，请重新确认设备身份");d.fp=real;
   long size=file!=null?file.length():(data==null?0:data.length);OutputStream o=new BufferedOutputStream(s.getOutputStream());o.write((method+" "+path+" HTTP/1.1\r\nHost: "+d.ip+":"+d.port+"\r\nContent-Type: "+(file!=null?"application/octet-stream":"application/json")+"\r\nContent-Length: "+size+"\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
   if(file!=null){try(InputStream in=new FileInputStream(file)){byte[] b=new byte[131072];long done=0;for(int n;(n=in.read(b))!=-1;){o.write(b,0,n);done+=n;if(progress!=null)progress.update(done,size);}}}else if(data!=null)o.write(data);o.flush();
   HttpWire.Message reply=HttpWire.read(s.getInputStream());int code=Integer.parseInt(reply.first.split(" ")[1]);String body=new String(PhotoBundle.readBounded(reply.body,2*1024*1024),StandardCharsets.UTF_8);if(code>=300)throw new IOException("对方返回 "+code+(code==403?"（已拒绝）":code==409?"（忙碌或未打开）":code==401?"（需要 PIN，请先关闭对方 PIN 保护后重试）":""));return body;
  }finally{active.remove(socket);}
 }
 public void send(Device d,List<PhotoBundle.Item> files)throws Exception{
  JSONObject fs=new JSONObject();Map<String,PhotoBundle.Item> byId=new LinkedHashMap<>();for(PhotoBundle.Item f:files){String id=UUID.randomUUID().toString();byId.put(id,f);fs.put(id,new JSONObject().put("id",id).put("fileName",f.name).put("size",f.file.length()).put("fileType",f.name.endsWith(".zip")?"application/zip":"application/octet-stream").put("sha256",f.sha256));}
  JSONObject prep=new JSONObject(request(d,"POST",API+"/prepare-upload",new JSONObject().put("info",info()).put("files",fs).toString().getBytes(StandardCharsets.UTF_8),null,null));String sid=prep.getString("sessionId");JSONObject tokens=prep.getJSONObject("files");
  try{for(String id:byId.keySet()){if(!tokens.has(id))continue;PhotoBundle.Item f=byId.get(id);app.progress(0,0);request(d,"POST",API+"/upload?sessionId="+enc(sid)+"&fileId="+enc(id)+"&token="+enc(tokens.getString(id)),null,f.file,app::progress);}}
  catch(Exception e){try{request(d,"POST",API+"/cancel?sessionId="+enc(sid),new byte[0],null,null);}catch(Exception ignored){}throw e;}
 }
 private static String enc(String s)throws Exception{return URLEncoder.encode(s,"UTF-8");}
}
