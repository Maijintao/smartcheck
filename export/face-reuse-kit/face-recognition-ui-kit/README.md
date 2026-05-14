# Face Recognition UI Kit

## 包用途
人脸识别界面复用（主页面识别流程）。

## 核心文件说明
- `HomeScreen.kt`：主页面识别流程入口与页面编排。
- `DualCameraPreview.kt`：双路摄像头预览与画面展示。
- `FaceOverlay.kt`：人脸检测结果叠加绘制。

## 依赖项
- Compose
- CameraX
- Accompanist Permissions
- Coil
- Hilt

## 与算法层关系
依赖 `face-reuse-kit/core-sdk`，并对接 `app-integration` 中的 `FaceEngine/SeetaFaceEngine`。

## 最小接入步骤
1. 复制本包文件到目标工程并保持相对路径。
2. 替换包名与 import。
3. 将页面挂载到导航。
4. 注入 `MainViewModel` 并联通状态流转。

## 设备适配点
- `cameraId`（按设备调整）
- 前后置/外接摄像头选择
- `analysisThrottleMs` 性能节流参数
