# 测试说明

构建及JVM测试：.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest -Pstress=true

GPS样本相关单元测试在.fixtures不存在时跳过；Android NativePhotoTest需要自行提供.fixtures/has-gps.jpg和no-gps.jpg。私人样本不随源码分发。图库集成测试还需要测试设备上至少18张可访问的图片，ViewerTest和ViewerLifecycleTest需要至少一张已解析GPS的图片；应授予测试包io.tuji.app.debug图片及GPS权限。

PixelViewerTest自行生成并清理JPEG／GIF文件，不需要私人图片。它检查八种EXIF方向、分块、像素预算、手势、失败和取消。ViewerLifecycleTest还验证实际横屏、系统返回、坐标复制和教程阅读位置。

Activity测试可分别运行：adb shell am instrument -w -e class io.tuji.app.PixelViewerTest io.tuji.app.debug.test/androidx.test.runner.AndroidJUnitRunner。测试类之间停止测试包再启动，避免前一个Activity的结束过程干扰后一个startActivitySync。不要在测试期间启动另一个包或操作设备界面。

本次验证的详细结果及真机未验收项见VERIFICATION.md。
