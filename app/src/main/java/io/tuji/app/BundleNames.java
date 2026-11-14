package io.tuji.app;
import android.content.*;import java.util.*;import java.text.*;import java.io.*;
public final class BundleNames{public static synchronized String allocate(Context c,long time)throws IOException{String minute=new SimpleDateFormat("yyyyMMddHHmm",Locale.ROOT).format(new Date(time));SharedPreferences prefs=c.getSharedPreferences("bundle_names",0);int number=prefs.getInt(minute,0)+1;if(number<=0||!prefs.edit().putInt(minute,number).commit())throw new IOException("无法保存照片包序号");return minute+(number==1?"":"_"+number)+".tuji.zip";}}
