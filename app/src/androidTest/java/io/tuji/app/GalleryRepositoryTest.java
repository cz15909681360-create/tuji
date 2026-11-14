package io.tuji.app;
import androidx.test.platform.app.InstrumentationRegistry;import org.junit.Test;import static org.junit.Assert.*;
import android.content.*;import android.net.Uri;import android.provider.MediaStore;import java.util.*;
public class GalleryRepositoryTest {
 @Test public void browsesAuthorizedMediaWithoutImportAndStopsScanningInBackground()throws Exception{
  TujiApp app=(TujiApp)InstrumentationRegistry.getInstrumentation().getTargetContext().getApplicationContext();
  app.gallery.foreground(true);long until=System.currentTimeMillis()+15000;while((app.gallery.loading||app.gallery.items().isEmpty())&&System.currentTimeMillis()<until)Thread.sleep(100);
  assertTrue("MediaStore fixture photos visible without import",app.gallery.items().size()>=18);assertTrue(app.store.all().isEmpty());
  app.gallery.foreground(false);Thread.sleep(200);int count=0;for(GalleryRepository.Item i:app.gallery.items())if(i.location==GalleryRepository.PENDING)count++;Thread.sleep(300);int later=0;for(GalleryRepository.Item i:app.gallery.items())if(i.location==GalleryRepository.PENDING)later++;assertEquals(count,later);
 }
 @Test public void tenThousandMediaRecordsArePagedWithoutCopyingOriginals()throws Exception{
  TujiApp app=(TujiApp)InstrumentationRegistry.getInstrumentation().getTargetContext().getApplicationContext();ContentResolver resolver=app.getContentResolver();Uri uri=MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
  app.gallery.foreground(false);resolver.delete(uri,"relative_path=?",new String[]{"Pictures/TujiStress/"});
  long started=System.currentTimeMillis();
  try{for(int batch=0;batch<100;batch++){ContentValues[] values=new ContentValues[100];for(int k=0;k<100;k++){ContentValues v=new ContentValues();v.put("_display_name","tuji-stress-"+(batch*100+k)+".jpg");v.put("mime_type","image/jpeg");v.put("relative_path","Pictures/TujiStress");v.put("datetaken",1000000000000L+batch*100+k);values[k]=v;}assertEquals(100,resolver.bulkInsert(uri,values));}
   app.gallery.foreground(true);app.gallery.refresh();long until=System.currentTimeMillis()+30000;while((app.gallery.loading||app.gallery.items().size()<10000)&&System.currentTimeMillis()<until)Thread.sleep(100);
   assertTrue("10k records available",app.gallery.items().size()>=10000);Set<String> ids=new HashSet<>();for(GalleryRepository.Item i:app.gallery.items())assertTrue("unique media identity",ids.add(i.key));
   java.io.File[] files=app.stage.listFiles();assertTrue("browsing did not snapshot originals",files==null||files.length==0);
   android.util.Log.i("TujiGalleryTest","10k MediaStore test completed in "+(System.currentTimeMillis()-started)+" ms");
  }finally{app.gallery.foreground(false);resolver.delete(uri,"relative_path=?",new String[]{"Pictures/TujiStress/"});app.gallery.refresh();}
 }
}
