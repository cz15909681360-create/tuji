package io.tuji.app;

import android.os.Bundle;
import android.view.*;
import android.widget.*;

/** Offline, version-matched instructions. No permission or network is needed to read. */
public final class TutorialScreen extends ScrollView {
 final MainActivity a;
 final LinearLayout content;
 int chapter=-1, directoryOffset, pendingY;
 final int[] offsets=new int[8];
 final String[] titles={"快速上手","照片授权与GPS","图库与区间滑选","手机与电脑收发","时间包名与ZIP","看图、复制及导航","空间与原件删除","常见问题"};
 final String[][] steps={
  {"选择照片","连接同一网络","确认设备并发送"},
  {"允许图片访问","单独允许读取位置","区分不同解析状态"},
  {"双指调整三至六列","选框或日期全选","长按后按区间滑选"},
  {"双方打开接收软件","发现设备或输入IP","接收方确认并保存"},
  {"按发送时间命名","电脑收到照片ZIP","解压得到平铺原件"},
  {"缩放和拖动看大图","展开信息复制坐标","选择地图App导航"},
  {"预留约两倍临时空间","只清理闲置任务","删除原件需确认"},
  {"检查授权及定位状态","检查局域网是否互通","重试并留意后台限制"}
 };
 final String[][] icons={
  {"照片","设备","发送"},{"照片","设置","地图"},{"照片","照片","照片"},{"设备","添加","接收"},
  {"文件","接收","文件"},{"照片","文件","地图"},{"文件","删除","照片"},{"设置","设备","刷新"}
 };
 final String[] texts={
  "双方连接同一Wi-Fi或手机热点。选择照片后进入发送页，点击目标设备，再确认发送；接收方同意后开始传输。建议先用一张照片试传。第一次使用可以先阅读教程，拒绝权限不会阻止阅读。",
  "全部授权可浏览系统已索引的图片；部分授权只显示可访问照片。GPS权限单独控制位置读取，未授权GPS仍可看图。待解析、无定位、缺少权限和读取失败是不同状态。未下载云端图片和私密空间不在本机图库内；没有GPS的原件不能凭空恢复定位。",
  "双指捏合调整三至六列。点右上选框切换选择，点图片主体看详情。日期旁全选仅作用于当前相簿和筛选。长按滑动选择起点到终点的完整区间，回拖缩短，保留手势前已选。停在边缘可自动滚动；右侧独立通道按日期快滑。点击已选可预览、单项移除或清空。",
  "电脑先安装并打开LocalSend，手机接收方打开图迹的接收页。双方保持同一网络，发送页点击设备；找不到时可用加号输入IP。默认手动确认，可以主动长期信任真实HTTPS证书，并在设置中撤销。设备重装或身份变化后需要重新确认。",
  "照片包例如202612120531.tuji.zip，使用发起发送时的手机本地时间。同一分钟后续批次加_2、_3；重试视为新批次。电脑选择解压位置，包内图片平铺，无编号子目录或可见清单。图迹兼容v1/v2，原版LocalSend按普通ZIP接收。普通文件保留原名，不额外压缩。",
  "单击图片显隐控件，双击适屏或约2.5倍切换，双指缩放至适屏8倍，放大后单指拖动。底部展开时间、定位和小地图。坐标为原始WGS84，可单独复制纬度、经度，整对复制时纬度在前、英文逗号分隔；腾讯显示坐标另行转换。导航选择高德或百度，未安装会提示。实况附加数据随原件保留，当前显示静态画面。",
  "照片发送使用原件快照加ZIP，额外峰值约两倍原件大小。成功、失败、取消后检查清理，强杀残留在下次启动恢复。设置清理只处理闲置任务缓存，活动任务受保护，不删除相册、图库索引或地图缓存。gallery.db仅保存图片索引及定位，缩略图内存缓存与任务磁盘缓存不同。删除照片会请求删除系统原件，取消或失败保留记录。",
  "没有定位：先看状态是待解析、无GPS、缺少权限还是读取失败。找不到设备：检查同一网络、VPN、热点隔离和电脑防火墙。传输失败：重新发送整批，空间不足时先清理闲置任务。后台收不到新任务：保持接收页打开；已开始的任务使用前台服务，但仍可能受厂商省电策略影响。"
 };
 final int[][] links={{0,1,2},{4},{0},{1,2},{1},{0,3},{5},{4,1,2}};
 final String[] linkNames={"去照片","去发送","去接收","去地图","管理照片权限","查看缓存"};

 public TutorialScreen(MainActivity a) {
  super(a);this.a=a;setFillViewport(true);content=a.column();addView(content);render();
 }
 void render() {
  content.removeAllViews();
  content.addView(a.text(chapter<0?"教程 · 从这里开始":titles[chapter],22));
  if(chapter<0) {
   content.addView(a.text("离线图文指南，无需照片权限",13));
   for(int n=0;n<titles.length;n++){
    final int topic=n;
    TextView entry=MobileUi.flat(a,(n+1)+"  "+titles[n]+"  →","教程",()->open(topic));
    entry.setMinHeight(a.dp(48));entry.setTextSize(14);
    content.addView(entry,new LinearLayout.LayoutParams(-1,-2));
   }
  } else {
   content.addView(action("← 返回目录",this::back));
   LinearLayout illustration=a.column();
   illustration.setPadding(a.dp(12),a.dp(8),a.dp(12),a.dp(8));
   illustration.setBackground(MobileUi.shape(a,MobileUi.surface(a),14));
   illustration.setContentDescription("操作示意："+String.join("，",steps[chapter]));
   for(int n=0;n<3;n++) {
    LinearLayout row=new LinearLayout(a);row.setGravity(Gravity.CENTER_VERTICAL);
    ImageView icon=MobileUi.icon(a,icons[chapter][n]);
    icon.setImageDrawable(new MobileUi.LineIcon(icons[chapter][n],MobileUi.dark(a)?0xff69d9c3:MobileUi.TEAL));
    row.addView(icon,new LinearLayout.LayoutParams(a.dp(24),a.dp(24)));
    TextView caption=a.text((n+1)+"  "+steps[chapter][n],15);
    caption.setPadding(a.dp(12),a.dp(10),0,a.dp(10));
    row.addView(caption,new LinearLayout.LayoutParams(0,-2,1));illustration.addView(row);
   }
   content.addView(illustration);
   content.addView(a.text(texts[chapter],16));
   content.addView(a.text("按步骤操作",14));
   for(int target:links[chapter])content.addView(action(linkNames[target],()->go(target)));
  }
  pendingY=chapter<0?directoryOffset:offsets[chapter];requestLayout();
 }
 TextView action(String label,Runnable run){
  TextView view=MobileUi.flat(a,label,"",run);view.setMinHeight(a.dp(44));view.setTextSize(14);return view;
 }
 void remember(){
  int value=pendingY>=0?pendingY:getScrollY();
  if(chapter<0)directoryOffset=value;else offsets[chapter]=value;
 }
 void open(int topic){remember();chapter=topic;render();}
 boolean back(){if(chapter<0)return false;remember();chapter=-1;render();return true;}
 void go(int target){
  remember();
  if(target==4)a.manageGalleryAccess();
  else if(target==5)a.settings();
  else{int[] tabs={2,0,1,3};a.tab=tabs[target];a.render();}
 }
 @Override protected void onLayout(boolean changed,int l,int t,int r,int b){
  super.onLayout(changed,l,t,r,b);
  if(pendingY>=0){int y=pendingY;pendingY=-1;scrollTo(0,y);}
 }
 Bundle state(){
  remember();Bundle b=new Bundle();b.putInt("chapter",chapter);b.putInt("directory",directoryOffset);b.putIntArray("offsets",offsets.clone());return b;
 }
 void restore(Bundle b){
  chapter=b.getInt("chapter",-1);if(chapter< -1||chapter>=titles.length)chapter=-1;
  directoryOffset=Math.max(0,b.getInt("directory"));
  int[] saved=b.getIntArray("offsets");if(saved!=null)System.arraycopy(saved,0,offsets,0,Math.min(saved.length,offsets.length));
  render();
 }
}
