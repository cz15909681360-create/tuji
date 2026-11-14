package io.tuji.app;
import android.app.Instrumentation;
import android.content.*;
import android.os.Bundle;
import android.view.KeyEvent;
import androidx.test.platform.app.InstrumentationRegistry;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class ViewerLifecycleTest {
 final Instrumentation i=InstrumentationRegistry.getInstrumentation();
 void await(java.util.function.BooleanSupplier predicate)throws Exception{
  long end=System.currentTimeMillis()+15000;
  while(System.currentTimeMillis()<end){boolean[] ok={false};i.runOnMainSync(()->ok[0]=predicate.getAsBoolean());if(ok[0])return;Thread.sleep(50);}
  fail("state did not settle");
 }
 @Test public void copiesScopesRotationAndTutorialState()throws Exception{
  MainActivity first=(MainActivity)i.startActivitySync(new Intent(i.getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
  TujiApp app=first.app;boolean[] mapAgreed={false};PhotoDetail[] detail={null};Locale previous=Locale.getDefault();
  try{
   await(()->!app.gallery.loading&&!app.gallery.located().isEmpty());
   i.runOnMainSync(()->{
    mapAgreed[0]=first.getPreferences(0).getBoolean("map_agreed",false);
    first.getPreferences(0).edit().putBoolean("map_agreed",false).commit();
    first.detail(app.gallery.located().get(0));detail[0]=first.details.get(0);
   });
   await(()->detail[0].image.base!=null&&detail[0].image.getWidth()>0);
   app.gallery.foreground(false);await(()->!app.gallery.loading);
   i.runOnMainSync(()->{
    PhotoDetail d=detail[0];GalleryRepository.Item photo=app.gallery.find(d.photo.uri);
    Locale.setDefault(Locale.FRANCE);
    for(int which=0;which<3;which++){
     d.copy(which);
     String got=((ClipboardManager)first.getSystemService(Context.CLIPBOARD_SERVICE)).getPrimaryClip().getItemAt(0).getText().toString();
     String lat=PhotoDetail.number(photo.lat),lon=PhotoDetail.number(photo.lon);
     assertEquals(which==0?lat:which==1?lon:lat+","+lon,got);
    }
    ClipboardManager clipboard=(ClipboardManager)first.getSystemService(Context.CLIPBOARD_SERVICE);
    clipboard.setPrimaryClip(ClipData.newPlainText("test","unchanged"));
    int status=photo.location;photo.location=GalleryRepository.NO_PERMISSION;d.update();
    assertFalse(d.copyPair.isEnabled());assertNotNull("GPS denial does not hide pixels",d.image.base);
    d.copy(2);assertEquals("unchanged",clipboard.getPrimaryClip().getItemAt(0).getText().toString());photo.location=status;
    // Simulate MediaStore removing the authorized URI while the original object still contains GPS.
    Map<String,GalleryRepository.Item> original=app.gallery.lookup,scope=new HashMap<>(original);
    scope.remove(photo.uri);app.gallery.lookup=scope;d.update();
    assertNull(d.image.base);assertNull(d.image.sourceUri);assertFalse(d.copyPair.isEnabled());
    d.copy(2);assertEquals("unchanged",clipboard.getPrimaryClip().getItemAt(0).getText().toString());
    app.gallery.lookup=original;d.update();
    assertNotNull("restored scope reloads the same signature",d.image.sourceUri);
   });
   await(()->detail[0].image.base!=null);
   i.runOnMainSync(()->{
    PhotoDetail d=detail[0];d.image.zoom=3;d.image.cx=.45f;d.image.cy=.5f;d.image.clamp();
    d.image.performClick();assertFalse(d.chrome);assertEquals(android.view.View.GONE,d.head.getVisibility());d.image.performClick();assertTrue(d.chrome);d.expanded=true;d.layoutInfo();
    assertTrue(d.scroll.getLayoutParams().height<=.7f*(d.frame.getHeight()-d.frame.getPaddingTop()-d.frame.getPaddingBottom())+1);
    first.setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
   });
   await(()->app.activity!=null&&app.activity!=first&&app.activity.resumed&&!app.activity.details.isEmpty()&&app.activity.details.get(0).image.base!=null);
   MainActivity second=app.activity;
   i.runOnMainSync(()->{
    PhotoDetail d=second.details.get(0);assertTrue("landscape viewport",d.image.getWidth()>d.image.getHeight());assertTrue(d.expanded);assertTrue(d.chrome);
    assertEquals(3,d.image.zoom,.01);assertEquals(.45f,d.image.cx,.01);assertEquals(.5f,d.image.cy,.01);
   });
   i.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);i.waitForIdleSync();
   i.runOnMainSync(()->{assertFalse(second.details.get(0).expanded);assertTrue(second.details.get(0).isShowing());});
   i.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);i.waitForIdleSync();
   i.runOnMainSync(()->{assertTrue(second.details.isEmpty());second.tab=4;second.render();second.tutorial.open(7);});
   i.waitForIdleSync();
   int[] scroll={0};
   i.runOnMainSync(()->{second.tutorial.scrollTo(0,200);scroll[0]=second.tutorial.getScrollY();assertTrue("chapter scrollable",scroll[0]>0);second.recreate();});
   await(()->app.activity!=null&&app.activity!=second&&app.activity.resumed&&app.activity.tutorial!=null&&app.activity.tutorial.getHeight()>0);
   i.waitForIdleSync();
   i.runOnMainSync(()->{
    TutorialScreen tutorial=app.activity.tutorial;
    assertEquals(4,app.activity.tab);assertEquals(7,tutorial.chapter);assertEquals(scroll[0],tutorial.getScrollY());
    tutorial.go(1);assertEquals(0,app.activity.tab);app.activity.tab=4;app.activity.render();
    assertEquals(7,tutorial.chapter);assertTrue(tutorial.back());assertEquals(-1,tutorial.chapter);
   });
  }finally{
   Locale.setDefault(previous);
   i.runOnMainSync(()->{MainActivity a=app.activity;if(a!=null){a.getPreferences(0).edit().putBoolean("map_agreed",mapAgreed[0]).commit();for(PhotoDetail d:new ArrayList<>(a.details))d.dismiss();a.finish();}});
  }
 }
}
