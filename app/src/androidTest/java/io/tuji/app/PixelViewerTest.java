package io.tuji.app;

import android.app.Instrumentation;
import android.content.Intent;
import android.graphics.*;
import android.media.ExifInterface;
import android.net.Uri;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import static org.junit.Assert.*;

public class PixelViewerTest {
 final Instrumentation i=InstrumentationRegistry.getInstrumentation();
 void await(java.util.function.BooleanSupplier condition)throws Exception{
  long end=System.currentTimeMillis()+15000;
  while(System.currentTimeMillis()<end){
   boolean[] ok={false};i.runOnMainSync(()->ok[0]=condition.getAsBoolean());
   if(ok[0])return;Thread.sleep(50);
  }
  fail("viewer timed out");
 }
 File fixture(File dir,int orientation,int w,int h)throws Exception{
  File file=new File(dir,"orientation-"+orientation+"-"+w+".jpg");
  Bitmap bitmap=Bitmap.createBitmap(w,h,Bitmap.Config.RGB_565);
  Canvas canvas=new Canvas(bitmap);Paint p=new Paint();
  int[] colors={Color.RED,Color.GREEN,Color.BLUE,Color.YELLOW};
  for(int n=0;n<4;n++){p.setColor(colors[n]);canvas.drawRect(n%2*w/2f,n/2*h/2f,(n%2+1)*w/2f,(n/2+1)*h/2f,p);}
  try(OutputStream out=new FileOutputStream(file)){bitmap.compress(Bitmap.CompressFormat.JPEG,95,out);}
  bitmap.recycle();ExifInterface exif=new ExifInterface(file);exif.setAttribute(ExifInterface.TAG_ORIENTATION,""+orientation);exif.saveAttributes();return file;
 }
 Bitmap render(ZoomPhotoView view){
  Bitmap b=Bitmap.createBitmap(view.getWidth(),view.getHeight(),Bitmap.Config.ARGB_8888);
  view.draw(new Canvas(b));return b;
 }
 @Test public void orientedTilesGesturesBudgetsAndCancellation()throws Exception{
  MainActivity a=(MainActivity)i.startActivitySync(new Intent(i.getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
  File dir=new File(a.getCacheDir(),"viewer-tests");dir.mkdirs();
  ZoomPhotoView[] holder={null};
  try{
   i.runOnMainSync(()->{FrameLayout host=new FrameLayout(a);a.setContentView(host);holder[0]=new ZoomPhotoView(a);host.addView(holder[0],new FrameLayout.LayoutParams(600,500));});
   ZoomPhotoView view=holder[0];await(()->view.getWidth()>0);
   for(int orientation=1;orientation<=8;orientation++){
    File source=fixture(dir,orientation,1200,800);
    i.runOnMainSync(()->{view.zoom=1;view.cx=view.cy=.5f;view.load(Uri.fromFile(source).toString());});
    await(()->view.base!=null);
    final int direction=orientation;
    i.runOnMainSync(()->{
     assertNotNull("region supported for JPEG direction "+direction,view.page.decoder);
     assertEquals(direction>=5?800:1200,view.page.width);
     assertEquals(direction>=5?1200:800,view.page.height);
     view.scaleAt(2.5f,300,250);
    });
    AtomicReference<Bitmap> expected=new AtomicReference<>();
    i.runOnMainSync(()->expected.set(render(view)));
    await(()->!view.tileBusy&&view.tiles.size()>0);
    // Allow visible tiles to finish after their callbacks invalidate the view.
    Thread.sleep(400);i.waitForIdleSync();
    i.runOnMainSync(()->{
     assertFalse("tile decode succeeded",view.page.tileFailed);
     Bitmap actual=render(view),before=expected.get();
     for(int y:new int[]{125,375})for(int x:new int[]{150,450}){
      int p=before.getPixel(x,y),q=actual.getPixel(x,y);
      assertTrue("red orientation "+direction,Math.abs(Color.red(p)-Color.red(q))<24);
      assertTrue("green orientation "+direction,Math.abs(Color.green(p)-Color.green(q))<24);
      assertTrue("blue orientation "+direction,Math.abs(Color.blue(p)-Color.blue(q))<24);
     }
     actual.recycle();before.recycle();assertTrue(view.heldPixelBytes()<=view.budget);
    });
   }
   File large=fixture(dir,6,6000,4000);
   i.runOnMainSync(()->view.load(Uri.fromFile(large).toString()));
   await(()->view.base!=null);
   i.runOnMainSync(()->{
    assertTrue("base is bounded",view.base.getAllocationByteCount()<=view.baseBudget);
    view.zoom=1;view.cx=view.cy=.5f;
    view.scaleAt(8,300,250);assertEquals(8,view.zoom,.001);
    view.scaleAt(100,300,250);assertEquals(8,view.zoom,.001);
    android.os.Bundle saved=view.state();view.restore(saved);assertEquals(8,view.zoom,.001);
    view.cx=-5;view.cy=5;view.clamp();assertTrue(view.cx>=0&&view.cx<=1);assertTrue(view.cy>=0&&view.cy<=1);
   });
   final int[] taps={0};
   i.runOnMainSync(()->{view.tap=()->taps[0]++;view.zoom=1;view.cx=view.cy=.5f;long now=android.os.SystemClock.uptimeMillis();single(view,now,0);single(view,now,100);assertEquals("double tap zoom",2.5f,view.zoom,.01);});
   Thread.sleep(400);i.runOnMainSync(()->assertEquals("double tap never toggles chrome",0,taps[0]));
   i.runOnMainSync(()->single(view,android.os.SystemClock.uptimeMillis(),0));
   Thread.sleep(400);i.runOnMainSync(()->{assertEquals("single tap toggles chrome",1,taps[0]);view.zoom=1;view.cx=view.cy=.5f;pinch(view);assertEquals("short two-finger pinch",3,view.zoom,.02);});
   Thread.sleep(400);i.runOnMainSync(()->assertEquals("remaining finger never taps",1,taps[0]));
   i.runOnMainSync(()->{pinch(view,true);assertFalse("multitouch cancel resets",view.pinching);view.performClick();assertEquals("accessibility click",2,taps[0]);});
   await(()->!view.tileBusy);
   i.runOnMainSync(()->assertTrue("held pixels plus overlap/inflight stay bounded",view.heldPixelBytes()+view.baseBudget+view.tileBudget<=view.budget));
   File gif=new File(dir,"fallback.gif");
   java.nio.file.Files.write(gif.toPath(),android.util.Base64.decode("R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAIBRAA7",android.util.Base64.DEFAULT));
   i.runOnMainSync(()->view.load(Uri.fromFile(gif).toString()));await(()->view.base!=null);
   i.runOnMainSync(()->{assertNull("GIF falls back to ImageDecoder",view.page.decoder);assertFalse(view.failed);view.load(Uri.fromFile(new File(dir,"missing.jpg")).toString());});
   await(()->view.failed);
   i.runOnMainSync(()->{assertNull("failed source never shows old pixels",view.base);view.performClick();});
   await(()->view.failed);
   i.runOnMainSync(()->{view.load(Uri.fromFile(large).toString());view.clear();});
   Thread.sleep(500);i.waitForIdleSync();
   i.runOnMainSync(()->{assertNull("cancelled decode cannot repopulate",view.base);view.close();view.close();});
   assertTrue("worker closed",view.worker.awaitTermination(10,java.util.concurrent.TimeUnit.SECONDS));
  }finally{
   i.runOnMainSync(()->{if(holder[0]!=null)holder[0].close();a.finish();});
   PhotoBundle.deleteTree(dir);
  }
 }
 static void single(ZoomPhotoView v,long down,int offset){for(int action:new int[]{0,1}){android.view.MotionEvent e=android.view.MotionEvent.obtain(down+offset,down+offset+action*20,action,300,250,0);v.onTouchEvent(e);e.recycle();}}
 static void pinch(ZoomPhotoView v){pinch(v,false);}
 static void pinch(ZoomPhotoView v,boolean cancel){long time=android.os.SystemClock.uptimeMillis();int[] actions={0,5|(1<<8),2,2,6|(1<<8),1};float[] spans={80,80,160,240,240,240};if(cancel)actions[4]=3;for(int n=0;n<(cancel?5:actions.length);n++){int count=n==0||n==5?1:2;android.view.MotionEvent.PointerProperties[] pp={new android.view.MotionEvent.PointerProperties(),new android.view.MotionEvent.PointerProperties()};android.view.MotionEvent.PointerCoords[] pc={new android.view.MotionEvent.PointerCoords(),new android.view.MotionEvent.PointerCoords()};for(int k=0;k<2;k++){pp[k].id=k;pp[k].toolType=1;pc[k].x=300+(k==0?-spans[n]:spans[n])/2;pc[k].y=250;pc[k].pressure=1;pc[k].size=1;}android.view.MotionEvent e=android.view.MotionEvent.obtain(time,time+n*30,actions[n],count,pp,pc,0,0,1,1,0,0,android.view.InputDevice.SOURCE_TOUCHSCREEN,0);v.onTouchEvent(e);e.recycle();}}

}
