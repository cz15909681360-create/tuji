package io.tuji.app;
import android.content.*;import android.content.res.Configuration;import android.graphics.*;import android.graphics.drawable.*;import android.view.*;import android.widget.*;
/** Original native components inspired by runner's spacing and teal palette. */
public final class MobileUi {
 public static final int TEAL=0xff087f74;
 public static boolean dark(Context c){return (c.getResources().getConfiguration().uiMode&Configuration.UI_MODE_NIGHT_MASK)==Configuration.UI_MODE_NIGHT_YES;}
 public static int background(Context c){return dark(c)?0xff101f20:0xfff3f6f8;}
 public static int surface(Context c){return dark(c)?0xff1b3031:0xffffffff;}
 public static int ink(Context c){return dark(c)?0xffe3f1ed:0xff18332f;}
 public static int muted(Context c){return dark(c)?0xffa5bbb6:0xff74817e;}
 public static int dp(Context c,int x){return Math.round(c.getResources().getDisplayMetrics().density*x);}
 public static GradientDrawable shape(Context c,int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(c,radius));return d;}
 public static void button(Button b,boolean primary){Context c=b.getContext();String label=b.getText().toString();boolean danger=label.contains("删除")||label.contains("撤销");boolean location=label.contains("导航")||label.contains("地图查看");int accent=danger?0xffbd3547:location?0xff286fbd:TEAL;primary=primary||location; b.setAllCaps(false);String iconKind=label.contains("发送")?"发送":label.contains("导航")||label.contains("地图查看")?"地图":label.contains("照片")?"照片":label.contains("删除")||label.contains("清理")?"删除":label.equals("设置")?"设置":null;if(iconKind!=null){LineIcon icon=new LineIcon(iconKind,primary?0xffffffff:accent);icon.setBounds(0,0,dp(c,20),dp(c,20));b.setCompoundDrawables(icon,null,null,null);b.setCompoundDrawablePadding(dp(c,7));}b.setTextSize(14);b.setTextColor(new android.content.res.ColorStateList(new int[][]{new int[]{-android.R.attr.state_enabled},new int[]{}},new int[]{muted(c),primary?0xffffffff:danger?accent:ink(c)}));GradientDrawable normal=shape(c,primary?accent:surface(c),12);if(!primary)normal.setStroke(dp(c,1),dark(c)?0xff33504b:0xffdae6e1);b.setBackground(new RippleDrawable(android.content.res.ColorStateList.valueOf(0x33087f74),normal,null));b.setMinHeight(dp(c,primary?52:44));b.setMinimumWidth(0);b.setMinWidth(0);b.setPadding(dp(c,14),dp(c,8),dp(c,14),dp(c,8));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,-2);lp.setMargins(dp(c,3),dp(c,4),dp(c,3),dp(c,4));b.setLayoutParams(lp);}

 public static TextView flat(Context c,String label,String kind,Runnable action){TextView v=new TextView(c);v.setText(label);v.setTextSize(12);v.setGravity(Gravity.CENTER_VERTICAL|Gravity.LEFT);v.setTextColor(dark(c)?0xff69d9c3:TEAL);v.setPadding(dp(c,6),0,dp(c,6),0);v.setMinHeight(dp(c,32));v.setBackground(new RippleDrawable(android.content.res.ColorStateList.valueOf(0x22087f74),null,shape(c,Color.WHITE,8)));v.setOnClickListener(w->action.run());v.setCompoundDrawablePadding(dp(c,5));if(!kind.isEmpty()){LineIcon icon=new LineIcon(kind,dark(c)?0xff69d9c3:TEAL);icon.setBounds(0,0,dp(c,20),dp(c,20));v.setCompoundDrawables(icon,null,null,null);}v.setFocusable(true);return v;}
 public static TextView tool(Context c,String label,String kind,Runnable action){TextView v=flat(c,"",kind,action);if(v.getCompoundDrawables()[0]!=null)v.getCompoundDrawables()[0].setBounds(0,0,dp(c,24),dp(c,24));v.setContentDescription(label);v.setTooltipText(label);v.setPadding(dp(c,10),dp(c,10),dp(c,10),dp(c,10));v.setLayoutParams(new LinearLayout.LayoutParams(dp(c,44),dp(c,44)));return v;}
 public static class StateMark extends Drawable{final Paint p=new Paint(3);final int state,size,color;public StateMark(Context c,int state){this.state=state;size=dp(c,16);color=dark(c)?0xff69d9c3:TEAL;}public int getIntrinsicWidth(){return size;}public int getIntrinsicHeight(){return size;}public void draw(Canvas c){c.save();c.translate(getBounds().left,getBounds().top);c.scale(getBounds().width()/16f,getBounds().height()/16f);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1.3f);p.setColor(color);c.drawCircle(8,8,6,p);if(state==1)c.drawLine(4,8,12,8,p);if(state==2){Path x=new Path();x.moveTo(4,8);x.lineTo(7,11);x.lineTo(12,5);c.drawPath(x,p);}c.restore();}public void setAlpha(int x){}public void setColorFilter(ColorFilter f){}public int getOpacity(){return -3;}}
 public static ImageView icon(Context c,String kind){ImageView v=new ImageView(c);v.setImageDrawable(new LineIcon(kind,TEAL));v.setScaleType(ImageView.ScaleType.CENTER_INSIDE);return v;}
 public static final class LineIcon extends Drawable {
  final String kind;final Paint p=new Paint(3);public LineIcon(String k,int color){kind=k;p.setColor(color);p.setStrokeWidth(1.8f);p.setStyle(Paint.Style.STROKE);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);}
  public void draw(Canvas canvas){canvas.save();canvas.translate(getBounds().left,getBounds().top);canvas.scale(getBounds().width()/24f,getBounds().height()/24f);Path path=new Path();
   switch(kind){case "教程":path.moveTo(12,5);path.lineTo(3,3);path.lineTo(3,20);path.lineTo(12,22);path.lineTo(21,20);path.lineTo(21,3);path.lineTo(12,5);path.lineTo(12,22);break;
    case "文件":path.moveTo(6,3);path.lineTo(14,3);path.lineTo(19,8);path.lineTo(19,21);path.lineTo(6,21);path.close();path.moveTo(14,3);path.lineTo(14,8);path.lineTo(19,8);break;
    case "设备":canvas.drawRoundRect(3,4,21,17,2,2,p);path.moveTo(12,17);path.lineTo(12,21);path.moveTo(7,21);path.lineTo(17,21);break;
    case "图层":path.moveTo(3,8);path.lineTo(12,3);path.lineTo(21,8);path.lineTo(12,13);path.close();path.moveTo(3,12);path.lineTo(12,17);path.lineTo(21,12);path.moveTo(3,16);path.lineTo(12,21);path.lineTo(21,16);break;
    case "全览":path.moveTo(3,9);path.lineTo(3,3);path.lineTo(9,3);path.moveTo(15,3);path.lineTo(21,3);path.lineTo(21,9);path.moveTo(21,15);path.lineTo(21,21);path.lineTo(15,21);path.moveTo(9,21);path.lineTo(3,21);path.lineTo(3,15);break;
    case "刷新":canvas.drawArc(4,4,20,20,30,290,false,p);path.moveTo(20,3);path.lineTo(20,9);path.lineTo(14,9);break;
    case "添加":path.moveTo(12,4);path.lineTo(12,20);path.moveTo(4,12);path.lineTo(20,12);break;
    case "照片":canvas.drawRoundRect(3,4,21,20,3,3,p);canvas.drawCircle(8,9,1.5f,p);path.moveTo(4,18);path.lineTo(11,11);path.lineTo(15,15);path.lineTo(18,12);path.lineTo(21,16);break;
    case "地图":path.moveTo(3,5);path.lineTo(9,3);path.lineTo(15,6);path.lineTo(21,4);path.lineTo(21,19);path.lineTo(15,21);path.lineTo(9,18);path.lineTo(3,20);path.close();path.moveTo(9,3);path.lineTo(9,18);path.moveTo(15,6);path.lineTo(15,21);break;
    case "发送":path.moveTo(3,11);path.lineTo(21,3);path.lineTo(14,21);path.lineTo(11,13);path.close();path.moveTo(11,13);path.lineTo(21,3);break;
    case "删除":path.moveTo(5,7);path.lineTo(19,7);path.moveTo(9,4);path.lineTo(15,4);path.moveTo(7,7);path.lineTo(8,21);path.lineTo(16,21);path.lineTo(17,7);path.moveTo(10,10);path.lineTo(10,18);path.moveTo(14,10);path.lineTo(14,18);break;
    case "设置":canvas.drawCircle(12,12,7,p);canvas.drawCircle(12,12,2.5f,p);for(int n=0;n<8;n++){double r=n*Math.PI/4;canvas.drawLine((float)(12+8*Math.cos(r)),(float)(12+8*Math.sin(r)),(float)(12+10*Math.cos(r)),(float)(12+10*Math.sin(r)),p);}break;
    case "接收":path.moveTo(12,3);path.lineTo(12,15);path.moveTo(7,10);path.lineTo(12,15);path.lineTo(17,10);path.moveTo(4,15);path.lineTo(4,21);path.lineTo(20,21);path.lineTo(20,15);break;
    default:canvas.drawCircle(12,12,7,p);canvas.drawCircle(12,12,2,p);
   }canvas.drawPath(path,p);canvas.restore();}
  public void setAlpha(int a){p.setAlpha(a);}public void setColorFilter(ColorFilter f){p.setColorFilter(f);}public int getOpacity(){return android.graphics.PixelFormat.TRANSLUCENT;}
 }
}
