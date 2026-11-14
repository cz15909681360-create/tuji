package io.tuji.app;
import org.junit.Test;import static org.junit.Assert.*;
public class CoordinatesTest {
 @Test public void gpsIsNotModified(){double lat=30.512630444444444,lon=114.3313751111111;double[] q=Coordinates.gcj(lat,lon);assertTrue(Math.abs(q[0]-lat)<0.01);assertTrue(Math.abs(q[1]-lon)<0.01);assertTrue(Math.abs(q[1]-lon)>0.001);assertEquals(30.512630444444444,lat,0);}
 @Test public void outsideChinaIsIdentity(){assertArrayEquals(new double[]{51.5,-0.12},Coordinates.gcj(51.5,-0.12),0);}
 @Test public void navigationDeclaresOriginalCoordinates(){String a=Coordinates.amap(30.5,114.3,"x y"),b=Coordinates.baidu(30.5,114.3,"x y");assertTrue(a.contains("dev=1"));assertTrue(a.contains("dlat=30.5"));assertTrue(b.contains("coord_type=wgs84"));assertTrue(b.contains("latlng%3A30.5%2C114.3"));}
 @Test public void malformedJpegHasNoLocation()throws Exception{java.io.File f=java.io.File.createTempFile("bad-photo",".jpg");try{java.nio.file.Files.write(f.toPath(),new byte[]{(byte)255,(byte)216,(byte)255,(byte)225,0,1});assertNull(ExifGps.read(f));}finally{f.delete();}}
}
