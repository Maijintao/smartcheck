# Full Face Recognition Kit

## 模块结构
- face-sdk/：完整人脸算法 SDK（Kotlin JNI 接口、C++、assets、jniLibs、build 配置）
- app/src/main/java/com/smartcheck/app/ml/：FaceEngine 与 SeetaFaceEngine
- app/src/main/java/com/smartcheck/app/viewmodel/：注册/识别流程 ViewModel 与状态
- app/src/main/java/com/smartcheck/app/ui/：识别与注册界面、相机预览、人脸覆盖层
- app/src/main/java/com/smartcheck/app/data/：用户特征存储 DAO/Entity/Repository
- app/src/main/java/com/smartcheck/app/di/：注入配置
- app/src/main/java/com/smartcheck/app/utils/：文件工具
- app/build.gradle.kts、AndroidManifest.xml、根 build.gradle.kts、settings.gradle.kts

## 快速接入
1. 将 face-sdk 作为独立 module 引入目标工程。
2. 合并 app 侧相关文件并按目标包名调整 import。
3. 在 app 依赖中接入 CameraX、Compose、Hilt、Coil、Accompanist Permissions。
4. 补齐 CAMERA 等权限与动态授权。
5. 启动时初始化 FaceEngine/SeetaFaceEngine，确保模型和 so 加载成功。
6. 注册流程调用特征提取并入库；识别流程调用 detect + extract + similarity。

## 关键参数
- MULTI_USER_MATCH_THRESHOLD = 0.78f
- SINGLE_USER_MATCH_THRESHOLD = 0.82f
- MIN_TOP_GAP = 0.04f
- FACE_CROP_EXPAND_RATIO = 0.20f

## 设备适配点
- cameraId（100/102）需按设备调整
- 前后置/外接摄像头选择
- 镜像坐标校正
- analysisThrottleMs 性能节流
