# SmartCheck AI - 智能晨检系统

[![版本](https://img.shields.io/badge/version-1.0.7-blue)](https://github.com/Maijintao/smartcheck/releases)
[![平台](https://img.shields.io/badge/platform-Android%208%2B%20(RK3566)-green)](https://www.rock-chips.com/)
[![语言](https://img.shields.io/badge/language-Kotlin%20%7C%20C%2B%2B17-orange)](https://kotlinlang.org/)

## 项目简介

**SmartCheck AI** 是专为学校食堂后厨场景设计的 **AIoT 无人值守晨检终端**，部署在 Rockchip RK3566 工业主板上。系统通过双目摄像头与红外测温模块，自动完成"人脸识别 → 体温测量 → 手部异物检测"的标准晨检流程，替代传统人工检查，提升食品安全管理效率。

### 核心场景

| 场景 | 说明 |
|------|------|
| **每日晨检** | 员工自助刷脸 → 自动体温测量 → 手部异物检测，全程无需人工干预 |
| **员工管理** | 管理员录入员工信息（照片、身份证、健康证有效期）并统一管理 |
| **记录存档** | 所有晨检结果本地持久化，支持按日期/人员/状态筛选和 CSV 导出 |
| **设备激活** | 设备出厂前通过激活码绑定，防止未授权使用 |

### 解决的核心问题

- **健康证过期风险**：系统自动预警即将到期（≤7天）的健康证，并阻止已过期员工上岗
- **手部异物检测**：RKNN + OpenCV 实时检测戒指、手表、手镯、创可贴等食品安全隐患
- **人工成本**：晨检全流程自动化，单次检查 ≤30 秒
- **记录留存**：自动保存人脸照片、手心/手背照片及体温数据，满足监管要求

---

## 快速开始

> 详细步骤请参阅 [SETUP_GUIDE.md](SETUP_GUIDE.md) 和 [docs/quickstart.md](docs/quickstart.md)。

### 1. 前置要求

| 工具 | 版本 | 说明 |
|------|------|------|
| Android Studio | Hedgehog 2023.1.1+ | 推荐 IDE |
| JDK | **17**（强制）| AGP 8.x 要求 |
| Android SDK | API 34 | `compileSdk 34` |
| Android NDK | r25c+ | JNI native 编译 |
| CMake | **3.22.1** | 已在 SDK Manager 安装 |
| Python | 3.8+ | 仅激活服务器需要 |

### 2. 获取代码

```bash
git clone https://github.com/Maijintao/smartcheck.git
cd smartcheck
# LFS 模型文件（SeetaFace6 fr_2_10.dat）
git lfs pull
```

### 3. 放置必需的第三方文件

> ⚠️ **安全提示**：`smartcheck.jks` 签名密钥库的密码**不要提交到 Git**。在本地创建 `local.properties`（已在 `.gitignore` 中），添加：
> ```
> KEY_STORE_PASSWORD=你的密钥库密码
> KEY_PASSWORD=你的密钥密码
> ```

> 这些文件因版权原因不随 Git 仓库提供，需手动放置：

```
# OpenCV Android SDK（从 https://opencv.org/releases/ 下载 4.8.0）
third_party/OpenCV-android-sdk/sdk/native/jni/

# RKNN Runtime（从 github.com/rockchip-linux/rknn-toolkit2 获取）
hand-sdk/src/main/jniLibs/arm64-v8a/librknnrt.so
hand-sdk/src/main/jniLibs/armeabi-v7a/librknnrt.so
hand-sdk/src/main/cpp/rknn_api/rknn_api.h

# RKNN 推理模型（向项目负责人获取）
hand-sdk/src/main/assets/hand_check_rk3566.rknn
hand-sdk/src/main/assets/yiwu_check_rk3566.rknn
```

### 4. 构建 & 安装

```bash
# 构建 Debug APK
./gradlew assembleDebug

# 安装到已连接的设备（USB 调试已开启）
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### 5. 启动激活服务器（可选）

```bash
# 安装依赖（无额外依赖，使用 Python 标准库）
python auth_server.py
# 默认监听 0.0.0.0:80
# 管理员默认密码：admin888（首次登录后请修改）
```

---

## 项目结构

```
smartcheck/
├── app/                          # 主应用模块
│   ├── src/main/
│   │   ├── java/com/smartcheck/app/
│   │   │   ├── api/              # 网络层
│   │   │   │   ├── ApiService.kt       # HTTP API
│   │   │   │   ├── KtorServerManager.kt # 内嵌 Ktor 服务器
│   │   │   │   ├── JwtUtil.kt          # JWT 工具
│   │   │   │   └── model/               # API 模型
│   │   │   ├── data/              # 数据层实现
│   │   │   │   ├── db/            # Room 数据库
│   │   │   │   ├── repository/    # Repository 实现
│   │   │   │   └── serial/         # 串口通信
│   │   │   ├── domain/            # 领域层
│   │   │   │   ├── model/         # 领域模型
│   │   │   │   ├── repository/    # Repository 接口
│   │   │   │   └── usecase/       # 用例
│   │   │   ├── utils/             # 工具类
│   │   │   │   ├── FileLoggingTree.kt # 文件日志
│   │   │   │   ├── DeviceInfo.kt      # 设备信息
│   │   │   │   └── DeviceAuth.kt      # 设备认证
│   │   │   ├── voice/             # 语音播报
│   │   │   ├── viewmodel/         # ViewModel 层
│   │   │   ├── ui/                # UI 层 (Compose)
│   │   │   ├── di/                # Hilt 依赖注入
│   │   │   ├── App.kt             # Application
│   │   │   └── MainActivity.kt    # 主 Activity
│   │   └── AndroidManifest.xml
│   └── build.gradle.kts
│
├── hand-sdk/                     # 手部异物检测 SDK（RKNN + OpenCV + JNI）
│   ├── src/main/
│   │   ├── java/com/smartcheck/sdk/
│   │   │   └── HandDetector.kt    # Kotlin API
│   │   ├── cpp/                   # C++ JNI + RKNN 推理
│   │   ├── assets/                # RKNN 模型
│   │   └── jniLibs/               # RKNN Runtime
│   └── build.gradle.kts
│
├── face-sdk/                     # 人脸识别 SDK（SeetaFace6）
│   ├── src/main/
│   │   ├── java/com/smartcheck/sdk/face/
│   │   │   ├── FaceSdk.kt         # Kotlin API
│   │   │   └── FaceInfo.kt        # 人脸信息模型
│   │   ├── cpp/                   # C++ JNI 封装
│   │   ├── assets/                # SeetaFace6 模型
│   │   └── jniLibs/               # SeetaFace6 动态库
│   └── build.gradle.kts
│
├── settings.gradle.kts
├── build.gradle.kts
├── AGENTS.md                     # Agent 开发规范
├── SETUP_GUIDE.md                # 环境配置指南
└── README.md
```

## 核心功能

### 1. 状态机（CheckState）

严格的线性状态流转：

```
IDLE → FACE_PASS → TEMP_MEASURING → HAND_CHECKING → ALL_PASS
         ↓              ↓                  ↓
      (失败)        TEMP_FAIL         HAND_FAIL
```

### 2. 人脸识别（face-sdk / SeetaFace6）

- **SDK**: SeetaFace6 Android
- **功能模块**:
  - 人脸检测 (FaceDetector)
  - 关键点定位 (FaceLandmarker)
  - 特征提取与比对 (FaceRecognizer)
  - 活体检测 (FaceAntiSpoofingX)
  - 口罩检测 (MaskDetector)
  - 性别/年龄估计
- **模型文件**: `fd_2_00.dat`, `pd_2_00_pts5.dat`, `fr_2_10.dat`, `fas_first/second.csta` 等

### 3. 手部检测（hand-sdk / RKNN）

- **推理框架**: RKNN + OpenCV + JNI
- **功能模块**:
  - 手部检测（整图）
  - 手掌/手背异物检测
- **模型文件**: `hand_check_rk3566.rknn`, `yiwu_check_rk3566.rknn`
- **注意**: 需要 Rockchip NPU 环境（RK3566 系列），模拟器不支持

### 4. 硬件抽象

- 红外测温模块（接口已定义，待接入真实串口）
- 蜂鸣器控制
- 语音播报（TTS）

### 5. 设备激活与认证

- 激活码管理（服务器端 + 设备端）
- 内网穿透部署（natapp）
- JWT 身份验证

### 6. 数据管理

- 本地 Room 数据库
- 晨检记录持久化
- 报表导出（CSV）
- 数据上传（可选）

## 技术栈

| 分类 | 技术/框架 | 版本 |
|------|-----------|------|
| **语言** | Kotlin | 1.9.x |
| **Native** | C++17（JNI） | — |
| **UI 框架** | Jetpack Compose + Material3 | BOM 2023.10.01 |
| **架构模式** | MVVM + Clean Architecture（UseCase / Repository） | — |
| **依赖注入** | Hilt（Dagger2） | 2.48 |
| **数据库** | Room (SQLite) | 2.6.1 |
| **相机** | CameraX (Camera2) | 1.3.0 |
| **网络（客户端）** | Ktor Client + CIO | 2.3.7 |
| **网络（服务端）** | Ktor Server + Netty（内嵌） | 2.3.7 |
| **鉴权** | JWT (java-jwt) | 4.4.0 |
| **异步** | Kotlin Coroutines + Flow | 1.7.3 |
| **图像加载** | Coil | 2.5.0 |
| **串口通信** | android-serialport | 2.1.4 |
| **日志** | Timber + 文件日志轮转 | 5.0.1 |
| **AI - 人脸** | SeetaFace6（JNI/so） | 6.0 |
| **AI - 手部** | RKNN + OpenCV | RKNN-Toolkit2 |
| **激活服务器** | Python 3（标准库） | 3.8+ |

**运行环境要求：**
- Android 8.0+（minSdk 26），推荐 Android 11+
- 硬件：Rockchip RK3566 工业主板（手部 RKNN 检测强制要求）
- 双目摄像头：Face 摄像头 `cameraId=100`，Hand 摄像头 `cameraId=102`

## 开发状态

### ✅ 已完成

| 模块 | 状态 |
|------|------|
| 多模块工程骨架 | ✅ |
| face-sdk (SeetaFace6) | ✅ |
| hand-sdk (RKNN) | ✅ |
| 状态机核心逻辑 | ✅ |
| 用户管理 (CRUD) | ✅ |
| 晨检记录管理 | ✅ |
| 数据导出 (CSV) | ✅ |
| 设备激活验证 | ✅ |
| 登录认证 | ✅ |
| 语音播报 | ✅ |
| 日志轮转 | ✅ |
| 硬件通信框架 | ✅ (串口待接入真实设备) |
| UI 界面 (Home + Admin) | ✅ |
| CameraX 集成 | ✅ |
| Hilt 依赖注入 | ✅ |

### ⚠️ 待完善

- 真实串口通信实现（当前为框架，需接入 android-serialport-api）
- 生产环境 HTTPS 改造

## 构建与运行

### 前置要求

- Android Studio Hedgehog (2023.1.1) 或更高版本
- JDK 17
- Android SDK 34
- Android NDK (用于 native 编译)
- CMake 3.22.1

### 构建命令

```bash
# 清理
./gradlew clean

# 构建 Debug APK
./gradlew assembleDebug

# 构建 Release APK
./gradlew assembleRelease

# 构建 SDK AAR
./gradlew :hand-sdk:assembleDebug :face-sdk:assembleDebug

# 仅编译 Kotlin（不打包，用于快速校验语法）
./gradlew :app:compileDebugKotlin

# Lint 检查
./gradlew :app:lintDebug

# 单元测试
./gradlew test
```

### 注意事项

- 需要授予相机权限
- 手部检测依赖 RKNN（需要 Rockchip NPU 真机）
- SeetaFace6 需要 `fr_2_10.dat` 模型文件（Git LFS 管理）

---

## 配置说明

### App 端关键配置

所有关键业务常量集中在 `app/src/main/java/com/smartcheck/app/App.kt`：

| 常量 | 默认值 | 说明 |
|------|--------|------|
| `ACTIVATION_URL` | `http://<服务器IP>/api/device/activate` | 激活服务器地址 |
| `VERSION_CHECK_URL` | `http://<服务器IP>/api/app/version/latest` | 版本检查地址 |

> **提示**：生产部署时，将 `<服务器IP>` 替换为实际的服务器 IP 或域名，并考虑启用 HTTPS。

### 激活服务器配置

激活服务器 (`auth_server.py`) 的主要参数：

| 参数 | 默认值 | 说明 |
|------|--------|------|
| `DEFAULT_PASSWORD` | `admin888` | 管理员首次登录密码（登录后请修改） |
| `_SESSION_EXPIRE` | `43200`（12小时） | 管理员 Session 有效期（秒） |
| `PORT` | `80` | 监听端口（脚本底部可修改） |
| `DB_PATH` | `./smartcheck.db` | SQLite 数据库路径 |
| `UPLOADS_DIR` | `./uploads/` | APK 上传目录 |

### 内网穿透（可选）

如需远程激活，项目内置 natapp 配置文件 (`natapp.ini`)。配置方式：

1. 在 [natapp.io](https://natapp.io) 注册并获取 authtoken
2. 修改 `natapp.ini` 中的 `authtoken` 字段
3. 运行 `natapp -config=natapp.ini`

---

## 常见问题 FAQ

### Q1: 编译时提示 "Unable to find CMake 3.22.1"

**A**: 打开 Android Studio → SDK Manager → SDK Tools，勾选 CMake 3.22.1 安装。若已安装仍报错，在 `local.properties` 中添加：
```
cmake.dir=/path/to/cmake-3.22.1
```

### Q2: `fr_2_10.dat` 文件只有几 KB（指针文件）

**A**: 该文件通过 Git LFS 管理，运行以下命令下载真实文件：
```bash
git lfs pull
```

### Q3: 手部检测报错 "RKNN init failed"

**A**: RKNN 推理需要 Rockchip RK3566 NPU 硬件，标准 Android 设备和模拟器不支持。请在真机上运行。

### Q4: 人脸识别摄像头画面黑屏

**A**: RK3566 上人脸摄像头的 `cameraId=100`，手部摄像头的 `cameraId=102`，与普通 Android 设备不同。标准设备上前置摄像头通常为 `cameraId=1`，可在 `DualCameraPreview.kt` 中临时修改用于调试。

### Q5: 构建时出现 `duplicate libc++_shared.so`

**A**: 这是正常情况，已在 `app/build.gradle.kts` 中通过 `pickFirsts` 解决：
```kotlin
jniLibs { pickFirsts += setOf("**/libc++_shared.so") }
```

### Q6: 激活服务器运行后 App 无法连接

**A**: 检查以下几点：
1. 服务器与设备在同一局域网，或通过内网穿透（natapp）暴露
2. 防火墙允许 80 端口访问
3. `App.kt` 中的 `ACTIVATION_URL` 已更新为实际服务器 IP

### Q7: Release 版本崩溃但 Debug 正常

**A**: `build.gradle.kts` 中 Release 构建未启用代码混淆（`isMinifyEnabled = false`），如启用 ProGuard/R8 需为 SeetaFace6、RKNN 的 JNI 类添加 keep 规则。

---

## 现状评估与改进建议

### 技术选型评分

| 维度 | 评分 | 说明 |
|------|------|------|
| 技术选型 | ⭐⭐⭐⭐ (85%) | Kotlin/Compose/Room/Hilt 均为主流最佳实践 |
| 架构设计 | ⭐⭐⭐ (70%) | UseCase 层已引入，代码结构清晰有所改善 |
| 安全性 | ⭐⭐ (40%) | 密码存储、本地数据加密有待加强 |
| 可测试性 | ⭐ (10%) | 单元测试基础设施已搭建，测试覆盖率极低 |

### 已知风险

| 风险 | 等级 | 建议措施 |
|------|------|----------|
| 密码使用 SHA-256 存储 | 🔴 高 | 改用 bcrypt / PBKDF2 + salt |
| 本地 Room 数据库无加密 | 🟡 中 | 引入 SQLCipher |
| 人脸照片/健康证照片明文存储 | 🟡 中 | 考虑加密存储敏感图片 |
| `smartcheck.jks` 密钥库密码硬编码在 build.gradle | 🔴 高 | 迁移到 `local.properties` 或 CI Secret |
| 串口通信为框架代码，未接入真实硬件 | 🟡 中 | 接入 android-serialport-api 实现 |
| 无自动化测试 | 🟡 中 | 至少为 UseCase 层补充单元测试 |

### 短期改进建议（P0-P1）

1. **密钥管理**：将 `smartcheck.jks` 密码移至 `local.properties`（已加入 `.gitignore`），例如：
   ```properties
   KEY_STORE_PASSWORD=your_keystore_password
   KEY_PASSWORD=your_key_password
   ```
   然后在 `app/build.gradle.kts` 中通过 `properties["KEY_STORE_PASSWORD"]` 读取，**不要提交明文密码**
2. **密码哈希**：管理员密码改用 BCrypt，激活服务器同步更新
3. **串口对接**：完成红外测温串口读取与蜂鸣器控制的真实实现
4. **基础测试**：为 `MorningCheckUseCase` 等核心用例补充单元测试

### 中期改进建议（P2-P3）

5. **SQLCipher**：为 Room 数据库开启加密，保护员工敏感信息
6. **HTTPS**：激活服务器部署 TLS 证书，避免激活码明文传输
7. **身份证脱敏**：员工详情页身份证号显示 `123456****1234`
8. **OTA 更新**：激活服务器已有 APK 上传/版本检查接口，App 端补充自动下载安装逻辑

---

## 文档

- [`AGENTS.md`](AGENTS.md) - Agent 开发规范与构建命令速查
- [`SETUP_GUIDE.md`](SETUP_GUIDE.md) - 环境配置指南（第三方依赖放置说明）
- [`docs/quickstart.md`](docs/quickstart.md) - 快速上手教程（从零到跑通）
- [`docs/架构设计.md`](docs/架构设计.md) - 架构设计方案（UseCase 层引入说明）
- [`docs/需求说明.md`](docs/需求说明.md) - 功能需求规格说明
- [`docs/接口设计.md`](docs/接口设计.md) - Repository/UseCase 接口定义
- [`docs/数据库设计.md`](docs/数据库设计.md) - Room 实体与 DAO 设计
- [`docs/综合评审报告.md`](docs/综合评审报告.md) - 可行性与安全性评审报告

## 许可证

本项目仅供学习和研究使用。

## 作者

单兵开发者
