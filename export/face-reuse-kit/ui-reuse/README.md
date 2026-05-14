# UI Reuse Kit

## 已打包 UI 文件说明
- 识别 UI：HomeScreen + DualCameraPreview + FaceOverlay
- 注册 UI：EmployeeEnrollScreen/EmployeeDetailScreen + CameraCaptureDialog

## 接入依赖
- Compose
- CameraX
- Accompanist Permissions
- Coil
- Hilt

## 与算法包的关系
- 依赖 export/face-reuse-kit/core-sdk
- 依赖 app-integration 中的 FaceEngine/SeetaFaceEngine

## 最小改造点
- 包名替换
- 导航接入
- ViewModel 注入
- cameraId(100/102)按设备调整

## 注意事项
- 权限
- 前后置/外接摄像头
- 性能节流 analysisThrottleMs
