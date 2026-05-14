# Face Reuse Kit

## 目录说明
- core-sdk/：人脸 SDK 核心代码（Kotlin 接口、JNI/CMake、模型 assets、arm64-v8a so）。
- app-integration/：业务侧接入示例（FaceEngine、SeetaFaceEngine、ViewModel、Repository、DI）。
- config/：接入时可参考的 build.gradle.kts、settings.gradle.kts、AndroidManifest.xml。

## 最小接入步骤
1. 添加 module：将 core-sdk/face-sdk 作为独立 module 引入工程。
2. 添加依赖：在 app module 依赖 face-sdk，并补齐项目所需库。
3. 添加权限：在 Manifest 声明相机及存储相关权限（按系统版本处理动态权限）。
4. 初始化：应用启动后初始化 FaceEngine/SeetaFaceEngine，确保模型与 JNI 已加载。
5. 注册调用：在注册流程中提取并保存人脸特征（参考 EmployeeEnrollViewModel、EmployeeDetailViewModel）。
6. 识别调用：在识别流程中执行检测、特征提取与阈值比对（参考 SeetaFaceEngine）。

## 必要阈值常量说明（SeetaFaceEngine）
- MULTI_USER_MATCH_THRESHOLD = 0.78f：多用户场景匹配阈值。
- SINGLE_USER_MATCH_THRESHOLD = 0.82f：单用户场景匹配阈值。
- MIN_TOP_GAP = 0.04f：Top1 与 Top2 最小分差，防止近似误识别。
- FACE_CROP_EXPAND_RATIO = 0.20f：人脸裁剪扩展比例，影响特征提取稳定性。

## 注意事项
- 模型文件：assets 内模型文件名与加载路径必须一致。
- so ABI：当前导出为 arm64-v8a，如需兼容其他设备请补充对应 ABI。
- Java17：构建链路（JDK/Gradle/Kotlin）需与 Java 17 保持一致。
- NDK/CMake：需安装可用 NDK 与 CMake，版本与 module 配置保持匹配。
