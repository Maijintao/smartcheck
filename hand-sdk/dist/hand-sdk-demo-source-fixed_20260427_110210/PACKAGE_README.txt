这是可直接用 Android Studio 打开的 Demo 源码工程。

打开方式：
1. Android Studio -> Open
2. 选择本目录
3. 等待 Gradle Sync

注意：
- 本工程依赖 OpenCV Android SDK，默认从以下任一目录自动查找：
  a) <工程>/third_party/OpenCV-android-sdk
  b) <工程>/../third_party/OpenCV-android-sdk
  c) <工程>/../../third_party/OpenCV-android-sdk
  d) <工程>/../../../third_party/OpenCV-android-sdk
