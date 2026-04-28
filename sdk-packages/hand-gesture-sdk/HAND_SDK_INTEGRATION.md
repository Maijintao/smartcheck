# SmartCheck Hand SDK 对外集成指南（External）

版本：v1.0  
适用对象：第三方集成方、项目交付方、实施工程师

算法版本：
- 手部算法：RKNN 模型（hand_check_rk3566.rknn + yiwu_check_rk3566.rknn）

激活方式：
- 如项目启用设备激活，请对接项目提供的激活服务（auth_server.py / 设备激活接口）。

激活接口（已实现）：
- POST /api/device/activate
- 请求头：Content-Type: application/json
- 请求体示例（历史激活码模式）：{"activationCode":"TEST001"}
- 请求体示例（当前 MAC 模式，Hand SDK 默认）：{"deviceMac":"AA:BB:CC:DD:EE:FF"}
- 成功响应示例：{"code":0,"message":"激活成功","data":{"activated":true}}
- 失败响应示例：
- code=1001（激活码无效 / MAC 未授权）
- code=1002（激活码已被使用）

版本接口（已实现）：
- GET /api/app/version/latest
- GET /api/app/version/history

==============================
一、能力说明
==============================

本 SDK 提供以下 AI 能力：

1. 手部能力（HandDetector）
- 手部检测
- 手部异物/伤口风险检测

说明：
- 手部能力依赖 Rockchip NPU 环境（推荐 RK3566 / RK3588 真机）。
- 非 RK 设备可用于流程联调，但不保证检测效果与性能。

==============================
二、环境要求
==============================

1. JDK 17
2. Android NDK
3. CMake 3.22.1
4. minSdk 26 及以上
5. Android Gradle Plugin 8.x

==============================
三、交付内容
==============================

1. SDK 模块
- hand-sdk

2. 必需模型与运行库
- 手部模型（hand-sdk assets）
- hand_check_rk3566.rknn
- yiwu_check_rk3566.rknn
- RKNN Runtime（hand-sdk jniLibs）
- arm64-v8a/librknnrt.so
- armeabi-v7a/librknnrt.so

3. Hand SDK 额外前置文件（源码构建场景）
- OpenCV Android SDK：third_party/OpenCV-android-sdk/sdk/native/jni/abi-{ANDROID_ABI}
- RKNN 头文件：hand-sdk/src/main/cpp/rknn_api/rknn_api.h（官方版本）

说明：
- 若仅使用已交付的 AAR，不需要在接入方项目中重新编译 hand-sdk native。

==============================
四、接入步骤（最小可运行）
==============================

步骤 1：添加依赖

源码模块集成方式：

```kotlin
dependencies {
    implementation(project(":hand-sdk"))
}
```

AAR 集成方式（手部 SDK 交付包常见）：

```kotlin
android {
    defaultConfig {
        minSdk = 26
    }

    packaging {
        jniLibs {
            pickFirsts += "**/libc++_shared.so"
        }
    }
}

dependencies {
    implementation(files("libs/hand-sdk-release.aar"))
}
```

步骤 2：应用启动初始化（建议 Application）

```kotlin
HandSdkAuth.configure(
    serverUrl = "http://your-auth-server:8080",
    required = true,
)

val hardware = Build.HARDWARE.lowercase()
val board = Build.BOARD.lowercase()
val product = Build.PRODUCT.lowercase()
val isRockchip = hardware.contains("rk") || board.contains("rk") || product.contains("rk")

if (isRockchip) {
    val handRet = HandDetector.init(this)
    if (handRet != HandDetector.INIT_OK) {
        Log.e("SDK", "HandDetector init failed: code=$handRet")
    }
} else {
    Log.w("SDK", "Skip HandDetector init on non-Rockchip device")
}
```

步骤 3：在视频帧处理中调用推理接口

```kotlin
val hands = HandDetector.detect(bitmap)
for (hand in hands) {
    val risk = hand.hasForeignObject
    val score = hand.score
    val label = hand.label
    val details = hand.foreignObjects
}
```

步骤 4：退出时释放资源

```kotlin
HandDetector.release()
```

步骤 5：运行仓库内置 Demo（可选）

- Demo Activity：`com.smartcheck.app.ui.screens.HandSdkDemoActivity`
- 已在 `app` 模块 Manifest 注册，可通过 adb 启动：

```bash
adb shell am start -n com.smartcheck.app/.ui.screens.HandSdkDemoActivity
```

==============================
五、接口返回说明
==============================

1. HandDetector.init 返回值
- INIT_OK（0）：成功
- INIT_FAILED（-1）：模型或 native 初始化失败
- INIT_AUTH_FAILED（-2）：在线授权失败

2. HandDetector 关键接口
- init(context)：初始化 hand 引擎
- detect(bitmap)：返回手部列表
- release()：释放引擎资源

3. HandInfo 关键字段
- id：手部序号
- box：手框（RectF）
- score：置信度
- keyPoints：关键点列表
- hasForeignObject：是否有异物/伤口风险
- label：分类标签
- foreignObjects：异物明细列表

4. HandSdkAuth 关键接口
- configure(serverUrl, required)：配置授权服务与是否强制校验
- isAuthRequired()：当前是否启用强制授权
- isActivated(context)：本地授权标识
- getCurrentDeviceMac(context)：获取稳定 MAC（固定 wlan0）
- getServerUrl()：获取当前授权服务器地址
- clearActivation(context)：清除本地授权状态

补充说明：
- HandDetector.init(context, licenseKey) 的 licenseKey 参数当前为预留字段，暂未用于授权决策。
- 当前在线授权由 HandSdkAuth.configure(serverUrl, required) + /api/device/activate 控制。

==============================
六、性能与线程建议
==============================

1. 推理放在后台线程执行（例如 Dispatchers.Default）。
2. 初始化建议串行执行，避免重复 init。
3. 实时视频流建议做抽帧（例如每 2 到 3 帧处理 1 帧）。
4. 输入分辨率建议控制在 640 到 1280 宽度区间，平衡精度与性能。

==============================
七、常见问题排查
==============================

1. HandDetector 初始化失败（返回 -1）
- 检查是否在 Rockchip 设备上运行
- 检查 librknnrt.so 是否存在且架构匹配
- 检查 OpenCV Android SDK 路径是否正确、目录是否完整（源码构建场景）
- 检查 rknn_api.h 是否替换为官方头文件（源码构建场景）
- 检查 .rknn 模型是否完整

2. HandDetector 授权失败（返回 -2）
- 检查设备是否可联网
- 检查 serverUrl 是否可访问
- 检查设备 MAC 是否录入白名单
- 检查服务端 /api/device/activate 返回 code 是否为 0

3. detect 返回空
- SDK 未初始化成功
- 输入图像质量不足（模糊、过暗、角度偏差）
- 实时场景计算压力高导致帧跳过

==============================
八、集成验收标准（建议）
==============================

1. 启动后 HandDetector.init 返回成功。
2. 手部画面中可稳定输出手框。
3. 有异物时 hasForeignObject 可触发，且 foreignObjects 有明细输出。
4. 应用退出后释放资源无崩溃、无重复释放异常。

==============================
九、技术支持信息
==============================

如需定制阈值、性能优化、设备适配（RK 平台）、模型替换，请联系项目交付方。

交付建议：
- 对外优先提供 hand-gesture-sdk-delivery.zip（完整包）
- 若仅提供二进制，至少包含 hand-sdk-release.aar + 本文档
