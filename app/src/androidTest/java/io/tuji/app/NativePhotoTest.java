package io.tuji.app;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;import static org.junit.Assert.*;
import android.content.Context;import java.io.*;
public class NativePhotoTest {
 File fixture(String name)throws Exception {Context target=InstrumentationRegistry.getInstrumentation().getTargetContext();File f=new File(target.getCacheDir(),"test-"+name);try(InputStream in=InstrumentationRegistry.getInstrumentation().getContext().getAssets().open(name);OutputStream out=new FileOutputStream(f)){byte[] b=new byte[131072];for(int n;(n=in.read(b))!=-1;)out.write(b,0,n);}return f;}
 @Test public void platformExifMustNotTurnEmptyGpsIntoZeroZero()throws Exception{Context c=InstrumentationRegistry.getInstrumentation().getTargetContext();PhotoStore store=new PhotoStore(c);File f=fixture("no-gps.jpg");try{PhotoStore.Photo p=store.metadata(f);assertNull(p.lat);assertNull(p.lon);}finally{f.delete();}}
 @Test public void retainsExifRationalPrecision()throws Exception{Context c=InstrumentationRegistry.getInstrumentation().getTargetContext();PhotoStore store=new PhotoStore(c);File f=fixture("has-gps.jpg");try{PhotoStore.Photo p=store.metadata(f);assertNotNull(p.lat);assertEquals(30.512630444444444,p.lat,1e-10);assertEquals(114.3313751111111,p.lon,1e-10);}finally{f.delete();}}
 @Test public void identityPersistsAcrossInstances()throws Exception{assertEquals(new TlsIdentity().fingerprint,new TlsIdentity().fingerprint);}
}
