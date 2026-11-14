package io.tuji.app;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Small bounded HTTP/1.1 reader, including chunked uploads used by LocalSend. */
public final class HttpWire {
 public static String line(InputStream in)throws IOException{ByteArrayOutputStream o=new ByteArrayOutputStream();int b;while((b=in.read())!=-1){if(b==10)break;if(o.size()>16384)throw new IOException("Header too long");if(b!=13)o.write(b);}if(b==-1&&o.size()==0)return null;return o.toString(StandardCharsets.ISO_8859_1.name());}
 public static final class Message {public String first;public Map<String,String> headers=new HashMap<>();public InputStream body;}
 public static Message read(InputStream in)throws IOException{
  Message m=new Message();m.first=line(in);if(m.first==null)throw new EOFException();int length=m.first.length();
  for(String s;(s=line(in))!=null&&!s.isEmpty();){length+=s.length();if(length>65536)throw new IOException("Headers too large");int n=s.indexOf(':');if(n<1)throw new IOException("Bad header");String key=s.substring(0,n).toLowerCase(Locale.ROOT);if(m.headers.containsKey(key)&&(key.equals("content-length")||key.equals("transfer-encoding")))throw new IOException("Duplicate framing header");m.headers.put(key,s.substring(n+1).trim());}
  if(m.headers.containsKey("transfer-encoding")){if(m.headers.containsKey("content-length")||!m.headers.get("transfer-encoding").equalsIgnoreCase("chunked"))throw new IOException("Unsupported HTTP framing");m.body=new Chunked(in);}
  else if(m.headers.containsKey("content-length")){long n;try{n=Long.parseLong(m.headers.get("content-length"));}catch(Exception e){throw new IOException("Invalid length");}if(n<0)throw new IOException("Invalid length");m.body=new Limited(in,n);}
  else m.body=new ByteArrayInputStream(new byte[0]);return m;
 }
 public static final class Limited extends InputStream{final InputStream in;long left;Limited(InputStream i,long n){in=i;left=n;}public int read()throws IOException{byte[] b=new byte[1];int n=read(b,0,1);return n<0?-1:b[0]&255;}public int read(byte[] b,int o,int n)throws IOException{if(left==0)return -1;int got=in.read(b,o,(int)Math.min(n,left));if(got<0)throw new EOFException("Incomplete body");left-=got;return got;}}
 public static final class Chunked extends InputStream{final InputStream in;long left;boolean end,needEnd;Chunked(InputStream i){in=i;}public int read()throws IOException{byte[] b=new byte[1];int n=read(b,0,1);return n<0?-1:b[0]&255;}public int read(byte[] b,int o,int n)throws IOException{
  if(end)return -1;if(left==0){if(needEnd&&!"".equals(line(in)))throw new IOException("Bad chunk end");String s=line(in);if(s==null)throw new EOFException();try{left=Long.parseLong(s.split(";",2)[0],16);}catch(Exception e){throw new IOException("Bad chunk size");}if(left<0)throw new IOException("Bad chunk size");if(left==0){int count=0;while((s=line(in))!=null&&!s.isEmpty())if(++count>64)throw new IOException("Too many trailers");end=true;return -1;}needEnd=true;}
  int got=in.read(b,o,(int)Math.min(n,left));if(got<0)throw new EOFException();left-=got;return got;
 }}
 public static void response(OutputStream out,int code,String body)throws IOException{byte[] b=body.getBytes(StandardCharsets.UTF_8);out.write(("HTTP/1.1 "+code+" "+(code<300?"OK":"Error")+"\r\nContent-Type: application/json; charset=utf-8\r\nContent-Length: "+b.length+"\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));out.write(b);out.flush();}
}
