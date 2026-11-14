package io.tuji.app;
import java.io.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
/** Lossless JPEG EXIF rational reader; zero denominators are missing GPS, not zero coordinates. */
public final class ExifGps {
 public static double[] read(File f){try{return read(new FileInputStream(f));}catch(Exception e){return null;}}
 public static double[] read(InputStream source){try(DataInputStream in=new DataInputStream(new BufferedInputStream(source))){if(in.readUnsignedShort()!=0xffd8)return null;
  while(true){int prefix=in.readUnsignedByte();if(prefix!=255)return null;int marker;do{marker=in.readUnsignedByte();}while(marker==255);if(marker==0xda||marker==0xd9)return null;if(marker==0x01||(marker>=0xd0&&marker<=0xd7))continue;int size=in.readUnsignedShort()-2;if(size<0)return null;byte[] data=new byte[size];in.readFully(data);if(marker==0xe1&&size>=14&&data[0]=='E'&&data[1]=='x'&&data[2]=='i'&&data[3]=='f'&&data[4]==0&&data[5]==0){double[] result=parse(data);if(result!=null)return result;}}
 }catch(Exception e){return null;}}
 static double[] parse(byte[] a){try{ByteBuffer b=ByteBuffer.wrap(a,6,a.length-6).slice();int order=b.getShort(0)&65535;if(order!=0x4949&&order!=0x4d4d)return null;b.order(order==0x4949?ByteOrder.LITTLE_ENDIAN:ByteOrder.BIG_ENDIAN);if(b.getShort(2)!=42)return null;int root=offset(b,4);int gps=0;
  int n=b.getShort(root)&65535;for(int k=0;k<n;k++){int e=root+2+12*k;if((b.getShort(e)&65535)==0x8825){gps=offset(b,e+8);break;}}if(gps==0)return null;
  String latref=null,lonref=null;Double lat=null,lon=null;n=b.getShort(gps)&65535;
  for(int k=0;k<n;k++){int e=gps+2+12*k,tag=b.getShort(e)&65535,type=b.getShort(e+2)&65535;long count=Integer.toUnsignedLong(b.getInt(e+4));if((tag==1||tag==3)&&type==2&&count>=1&&count<=4){String s=Character.toString((char)(b.get(e+8)&255));if(tag==1)latref=s;else lonref=s;}
   if((tag==2||tag==4)&&type==5&&count==3){int off=offset(b,e+8);double d=0;for(int j=0;j<3;j++){long numerator=Integer.toUnsignedLong(b.getInt(off+j*8)),denominator=Integer.toUnsignedLong(b.getInt(off+j*8+4));if(denominator==0)return null;double v=(double)numerator/denominator;if(j>0&&v>=60)return null;d+=v/Math.pow(60,j);}if(tag==2)lat=d;else lon=d;}
  }
  if(lat==null||lon==null||!("N".equals(latref)||"S".equals(latref))||!("E".equals(lonref)||"W".equals(lonref))||lat>90||lon>180)return null;
  return new double[]{"S".equals(latref)?-lat:lat,"W".equals(lonref)?-lon:lon};
 }catch(Exception e){return null;}}
 private static int offset(ByteBuffer b,int p)throws IOException{long n=Integer.toUnsignedLong(b.getInt(p));if(n>b.limit()-2)throw new IOException("Bad TIFF offset");return (int)n;}
}
