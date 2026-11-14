package io.tuji.app;

import android.graphics.*;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.util.LruCache;
import android.view.*;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Read-only, bounded pixel viewer. All native decoders stay on one worker. */
public final class ZoomPhotoView extends View {
 final MainActivity a;
 final ExecutorService worker=Executors.newSingleThreadExecutor();
 final long budget=Math.min(48L*1024*1024,Runtime.getRuntime().maxMemory()/6);
 final long baseBudget=Math.min(8L*1024*1024,budget/4);
 final int tileEdge=Math.max(64,Math.min(512,(int)Math.sqrt(budget/64.0)));
 final long tileBudget=(long)tileEdge*tileEdge*8;
 final LruCache<String,Bitmap> tiles=new LruCache<String,Bitmap>((int)(budget-2*baseBudget-tileBudget)){
  protected int sizeOf(String key,Bitmap b){return b.getAllocationByteCount();}
 };
 final Paint paint=new Paint(3);
 final GestureDetector gestures;
 volatile int generation;
 volatile boolean closed;
 Bitmap base;
 Page page;
 float zoom=1,cx=.5f,cy=.5f,lastSpan,lastFocusX,lastFocusY;
 boolean pinching,tileBusy,failed;
 String sourceUri,sourceSignature="",message="正在读取图片…";
 Runnable tap=()->{};

 static final class Page {
  final int token,width,height,rawWidth,rawHeight;
  final Bitmap bitmap;
  final Matrix origin;
  ParcelFileDescriptor fd;
  BitmapRegionDecoder decoder;
  boolean tileFailed;
  Page(int token,Bitmap bitmap,int width,int height,Matrix origin,ParcelFileDescriptor fd,BitmapRegionDecoder decoder){
   this.token=token;this.bitmap=bitmap;this.width=width;this.height=height;this.origin=origin;this.fd=fd;this.decoder=decoder;
   rawWidth=decoder==null?0:decoder.getWidth();rawHeight=decoder==null?0:decoder.getHeight();
  }
  // Worker only: earlier tile work has finished before resources are disposed.
  void dispose(){if(decoder!=null){decoder.recycle();decoder=null;}if(fd!=null){try{fd.close();}catch(IOException ignored){}fd=null;}}
 }

 public ZoomPhotoView(MainActivity a){
  super(a);this.a=a;setBackgroundColor(Color.BLACK);
  setContentDescription("照片；双指缩放，双击放大或还原，单击显隐工具栏");
  gestures=new GestureDetector(a,new GestureDetector.SimpleOnGestureListener(){
   public boolean onDown(MotionEvent e){return true;}
   public boolean onSingleTapConfirmed(MotionEvent e){
    performClick();return true;
   }
   public boolean onDoubleTap(MotionEvent e){scaleAt((zoom>1.05f?1:2.5f)/zoom,e.getX(),e.getY());return true;}
   public boolean onScroll(MotionEvent e,MotionEvent end,float dx,float dy){
    if(!pinching&&base!=null&&fit()>0){cx+=dx/(base.getWidth()*fit()*zoom);cy+=dy/(base.getHeight()*fit()*zoom);clamp();invalidate();}return true;
   }
  });
 }
 @Override public boolean performClick(){super.performClick();if(!closed){if(failed&&sourceUri!=null)load(sourceUri,sourceSignature);else tap.run();}return true;}
 @Override public boolean onTouchEvent(MotionEvent e){
  int action=e.getActionMasked();
  if(action==MotionEvent.ACTION_CANCEL){pinching=false;lastSpan=0;gestures.onTouchEvent(e);return true;}
  if(action==MotionEvent.ACTION_DOWN){pinching=false;lastSpan=0;}
  if(e.getPointerCount()>=2){
   float x=(e.getX(0)+e.getX(1))/2,y=(e.getY(0)+e.getY(1))/2;
   float span=(float)Math.hypot(e.getX(1)-e.getX(0),e.getY(1)-e.getY(0));
   if(!pinching){pinching=true;MotionEvent cancel=MotionEvent.obtain(e);cancel.setAction(MotionEvent.ACTION_CANCEL);gestures.onTouchEvent(cancel);cancel.recycle();}
   else if(action==MotionEvent.ACTION_MOVE&&lastSpan>0&&span>0)transform(span/lastSpan,lastFocusX,lastFocusY,x,y);
   lastSpan=span;lastFocusX=x;lastFocusY=y;return true;
  }
  if(pinching){if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL)pinching=false;return true;}
  gestures.onTouchEvent(e);return true;
 }
 float fit(){return base==null||getWidth()==0||getHeight()==0?0:Math.min(getWidth()/(float)base.getWidth(),getHeight()/(float)base.getHeight());}
 void scaleAt(float factor,float x,float y){transform(factor,x,y,x,y);}
 void transform(float factor,float oldX,float oldY,float x,float y){
  if(base==null||fit()<=0||!Float.isFinite(factor))return;
  float old=zoom;zoom=Math.max(1,Math.min(8,zoom*factor));
  cx+=(oldX-getWidth()/2f)/(base.getWidth()*fit()*old)-(x-getWidth()/2f)/(base.getWidth()*fit()*zoom);
  cy+=(oldY-getHeight()/2f)/(base.getHeight()*fit()*old)-(y-getHeight()/2f)/(base.getHeight()*fit()*zoom);
  clamp();invalidate();
 }
 void clamp(){
  if(base==null||fit()<=0)return;
  float hx=Math.min(.5f,getWidth()/(2*base.getWidth()*fit()*zoom)),hy=Math.min(.5f,getHeight()/(2*base.getHeight()*fit()*zoom));
  cx=Math.max(hx,Math.min(1-hx,cx));cy=Math.max(hy,Math.min(1-hy,cy));
 }
 @Override protected void onSizeChanged(int w,int h,int oldW,int oldH){clamp();}
 void load(String uri){load(uri,"");}
 void load(String uri,String signature){
  if(closed)return;
  sourceUri=uri;sourceSignature=signature;failed=false;int token=++generation;
  releasePage();message="正在读取图片…";invalidate();
  worker.execute(()->{
   if(!current(token))return;
   Page decoded=null;
   try{decoded=decode(Uri.parse(uri),token,baseBudget);}
   catch(OutOfMemoryError exhausted){
    try{if(current(token))decoded=decode(Uri.parse(uri),token,Math.max(65536,baseBudget/4));}
    catch(Exception|OutOfMemoryError ignored){}
   }catch(Exception ignored){}
   final Page result=decoded;
   if(!current(token)){if(result!=null)result.dispose();return;}
   post(()->{
    if(!current(token)){if(result!=null)result.dispose();return;}
    if(result==null){failed=true;message="图片无法读取，单击重试";}
    else{page=result;base=result.bitmap;message=result.decoder==null&&(result.width>base.getWidth()||result.height>base.getHeight())?"当前格式使用受限采样预览":"";clamp();}
    invalidate();
   });
  });
 }
 private Page decode(Uri uri,int token,long limit)throws IOException{
  int[] size=new int[2];
  Bitmap bitmap=ImageDecoder.decodeBitmap(ImageDecoder.createSource(a.getContentResolver(),uri),(d,info,src)->{
   size[0]=info.getSize().getWidth();size[1]=info.getSize().getHeight();d.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
   double ratio=Math.min(1,Math.sqrt(limit/(8.0*size[0]*size[1])));
   d.setTargetSize(Math.max(1,(int)(size[0]*ratio)),Math.max(1,(int)(size[1]*ratio)));
  });
  ParcelFileDescriptor fd=null;BitmapRegionDecoder decoder=null;Matrix origin=new Matrix();
  try{
   if(current(token)){
    int orientation;
    try(ParcelFileDescriptor meta=a.getContentResolver().openFileDescriptor(uri,"r")){
     if(meta==null)throw new IOException("无法读取图片方向");
     orientation=new ExifInterface(meta.getFileDescriptor()).getAttributeInt(ExifInterface.TAG_ORIENTATION,1);
    }
    if(orientation<1||orientation>8)orientation=1;
    fd=a.getContentResolver().openFileDescriptor(uri,"r");if(fd==null)throw new IOException("无法读取高清图片");
    decoder=BitmapRegionDecoder.newInstance(fd.getFileDescriptor(),false);if(decoder==null)throw new IOException("不支持分块");
    int w=decoder.getWidth(),h=decoder.getHeight(),ow=orientation>=5?h:w,oh=orientation>=5?w:h;
    if(ow!=size[0]||oh!=size[1])throw new IOException("方向不可验证");
    origin=orientationMatrix(orientation,w,h);
   }
  }catch(Exception|OutOfMemoryError unsupported){
   if(decoder!=null)decoder.recycle();decoder=null;
   if(fd!=null){try{fd.close();}catch(IOException ignored){}fd=null;}
  }
  return new Page(token,bitmap,size[0],size[1],origin,fd,decoder);
 }
 static Matrix orientationMatrix(int orientation,int w,int h){
  float[][] points={{0,0,w,0,0,h},{w,0,0,0,w,h},{w,h,0,h,w,0},{0,h,w,h,0,0},
   {0,0,0,w,h,0},{h,0,h,w,0,0},{h,w,h,0,0,w},{0,w,0,0,h,w}};
  Matrix matrix=new Matrix();matrix.setPolyToPoly(new float[]{0,0,w,0,0,h},0,points[orientation-1],0,3);return matrix;
 }
 @Override protected void onDraw(Canvas c){
  if(base!=null){
   float s=fit()*zoom;Matrix m=new Matrix();m.setScale(s,s);
   m.postTranslate(getWidth()/2f-cx*base.getWidth()*s,getHeight()/2f-cy*base.getHeight()*s);
   c.drawBitmap(base,m,paint);drawTiles(c);
  }
  if(!message.isEmpty()){
   paint.setColor(0xd0000000);c.drawRect(0,getHeight()-a.dp(40),getWidth(),getHeight(),paint);
   paint.setColor(Color.WHITE);paint.setTextSize(a.dp(12));c.drawText(message,a.dp(12),getHeight()-a.dp(14),paint);
  }
 }
 void drawTiles(Canvas c){
  final Page owner=page;
  if(owner==null||owner.decoder==null||owner.tileFailed||zoom<=1.05f)return;
  float s=fit()*zoom*base.getWidth()/owner.width;int sample=1;
  while(sample*2f*s<1&&sample<1<<20)sample*=2;
  int block=tileEdge*sample;Matrix transform=new Matrix(owner.origin);transform.postScale(s,s);
  transform.postTranslate(getWidth()/2f-cx*owner.width*s,getHeight()/2f-cy*owner.height*s);
  Matrix inverse=new Matrix();if(!transform.invert(inverse))return;
  RectF visible=new RectF(0,0,getWidth(),getHeight());inverse.mapRect(visible);
  int firstX=Math.max(0,(int)Math.floor(visible.left/block)),firstY=Math.max(0,(int)Math.floor(visible.top/block));
  c.save();c.concat(transform);boolean requested=false;
  for(int y=firstY;y*(long)block<owner.rawHeight&&y*(long)block<visible.bottom;y++){
   for(int x=firstX;x*(long)block<owner.rawWidth&&x*(long)block<visible.right;x++){
    Rect rect=new Rect(x*block,y*block,Math.min(owner.rawWidth,(x+1)*block),Math.min(owner.rawHeight,(y+1)*block));
    String key=sample+":"+x+":"+y;Bitmap tile=tiles.get(key);
    if(tile!=null)c.drawBitmap(tile,null,rect,paint);
    else if(!tileBusy&&!requested){requested=true;requestTile(owner,key,rect,sample);}
   }
  }
  c.restore();
 }
 private void requestTile(Page owner,String key,Rect rect,int sample){
  tileBusy=true;
  worker.execute(()->{
   Bitmap decoded=null;
   if(current(owner.token)){
    try{
     BitmapFactory.Options options=new BitmapFactory.Options();options.inSampleSize=sample;options.inPreferredConfig=Bitmap.Config.ARGB_8888;
     decoded=owner.decoder.decodeRegion(rect,options);
     if(decoded!=null&&decoded.getAllocationByteCount()>tileBudget){decoded.recycle();decoded=null;}
    }catch(Exception|OutOfMemoryError ignored){}
   }
   final Bitmap result=decoded;
   post(()->{
    if(!current(owner.token)||page!=owner){if(result!=null)result.recycle();return;}
    tileBusy=false;if(result==null){owner.tileFailed=true;message="高清读取失败，当前显示采样预览";}else tiles.put(key,result);
    invalidate();
   });
  });
 }
 private boolean current(int token){return !closed&&generation==token;}
 private void releasePage(){
  Page old=page;page=null;base=null;tileBusy=false;tiles.evictAll();if(old!=null)worker.execute(old::dispose);
 }
 void clear(){
  if(closed)return;generation++;sourceUri=null;failed=false;releasePage();message="照片已删除或访问权限已变化";invalidate();
 }
 void close(){if(closed)return;closed=true;generation++;releasePage();worker.shutdown();}
 long heldPixelBytes(){return(base==null?0:base.getAllocationByteCount())+tiles.size();}
 Bundle state(){Bundle b=new Bundle();b.putFloat("zoom",zoom);b.putFloat("cx",cx);b.putFloat("cy",cy);return b;}
 void restore(Bundle b){
  if(b==null)return;zoom=finiteClamp(b.getFloat("zoom",1),1,8,1);cx=finiteClamp(b.getFloat("cx",.5f),0,1,.5f);cy=finiteClamp(b.getFloat("cy",.5f),0,1,.5f);clamp();invalidate();
 }
 private static float finiteClamp(float value,float min,float max,float fallback){return Float.isFinite(value)?Math.max(min,Math.min(max,value)):fallback;}
}
