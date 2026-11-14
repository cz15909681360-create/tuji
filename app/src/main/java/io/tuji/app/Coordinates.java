package io.tuji.app;
public final class Coordinates {
 private static final double PI=Math.PI,A=6378245.0,EE=0.00669342162296594323;
 public static double[] gcj(double lat,double lon){if(lon<72.004||lon>137.8347||lat<0.8293||lat>55.8271)return new double[]{lat,lon};double x=lon-105,y=lat-35;
  double dl=-100+2*x+3*y+0.2*y*y+0.1*x*y+0.2*Math.sqrt(Math.abs(x));dl+=(20*Math.sin(6*x*PI)+20*Math.sin(2*x*PI))*2/3;dl+=(20*Math.sin(y*PI)+40*Math.sin(y/3*PI))*2/3;dl+=(160*Math.sin(y/12*PI)+320*Math.sin(y*PI/30))*2/3;
  double dn=300+x+2*y+0.1*x*x+0.1*x*y+0.1*Math.sqrt(Math.abs(x));dn+=(20*Math.sin(6*x*PI)+20*Math.sin(2*x*PI))*2/3;dn+=(20*Math.sin(x*PI)+40*Math.sin(x/3*PI))*2/3;dn+=(150*Math.sin(x/12*PI)+300*Math.sin(x/30*PI))*2/3;
  double rad=lat/180*PI,magic=1-EE*Math.sin(rad)*Math.sin(rad),sq=Math.sqrt(magic);dl=dl*180/((A*(1-EE))/(magic*sq)*PI);dn=dn*180/(A/sq*Math.cos(rad)*PI);return new double[]{lat+dl,lon+dn};
 }
 public static String amap(double lat,double lon,String name){return "amapuri://route/plan/?sourceApplication=Tuji&dlat="+lat+"&dlon="+lon+"&dname="+encode(name)+"&dev=1&t=0";}
 public static String baidu(double lat,double lon,String name){return "baidumap://map/direction?destination="+encode("latlng:"+lat+","+lon+"|name:"+name)+"&coord_type=wgs84&mode=driving&src=io.tuji.app";}
 private static String encode(String s){try{return java.net.URLEncoder.encode(s,"UTF-8");}catch(Exception e){throw new IllegalArgumentException(e);}}
}
