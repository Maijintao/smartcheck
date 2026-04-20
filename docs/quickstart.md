# SmartCheck AI - 快速上手指南

> 目标：从零开始，在 30 分钟内完成环境准备、编译构建、安装运行。

---

## 目录

1. [环境检查](#1-环境检查)
2. [获取源码](#2-获取源码)
3. [放置第三方依赖](#3-放置第三方依赖)
4. [Android Studio 配置](#4-android-studio-配置)
5. [编译构建](#5-编译构建)
6. [安装与运行](#6-安装与运行)
7. [启动激活服务器](#7-启动激活服务器（可选）)
8. [首次使用流程](#8-首次使用流程)
9. [常见报错排查](#9-常见报错排查)

---

## 1. 环境检查

在开始之前，确认以下工具已安装并配置正确：

```bash
# 检查 Java 版本（必须是 17）
java -version
# 期望输出：openjdk version "17.x.x"

# 检查 ADB（用于设备安装调试）
adb version
# 期望输出：Android Debug Bridge version x.x.x
```

如果 Java 版本不是 17，请：
- **macOS/Linux**：使用 `sdkman` 或 `brew install openjdk@17`
- **Windows**：从 [Adoptium](https://adoptium.net/) 下载 JDK 17 并设置 `JAVA_HOME`

> **⚠️ 重要**：Android Gradle Plugin 8.x 强制要求 JDK 17，使用其他版本会导致构建失败。

---

## 2. 获取源码

```bash
# 克隆仓库
git clone https://github.com/Maijintao/smartcheck.git
cd smartcheck

# 拉取 Git LFS 管理的大文件（SeetaFace6 模型 fr_2_10.dat）
git lfs pull
```

如果 `git lfs` 未安装：
- macOS：`brew install git-lfs && git lfs install`
- Ubuntu：`sudo apt install git-lfs && git lfs install`
- Windows：从 [git-lfs.github.com](https://git-lfs.github.com/) 安装

---

## 3. 放置第三方依赖

以下文件因版权/体积原因不随仓库提供，需手动获取并放置：

### 3.1 OpenCV Android SDK（必需）

1. 前往 https://opencv.org/releases/ 下载 **OpenCV-4.8.0-android-sdk.zip**
2. 解压后复制到项目中，使目录结构如下：

```
smartcheck/
└── third_party/
    └── OpenCV-android-sdk/
        └── sdk/
            └── native/
                └── jni/
                    ├── abi-arm64-v8a/
                    ├── abi-armeabi-v7a/
                    └── ...
```

### 3.2 RKNN Runtime 库（必需，仅 RK3566 设备）

1. 克隆 RKNN-Toolkit2：`git clone https://github.com/rockchip-linux/rknn-toolkit2.git`
2. 找到 `rknpu2/runtime/Android/` 下的 `librknnrt.so`
3. 复制到以下位置：

```
hand-sdk/src/main/jniLibs/
├── arm64-v8a/
│   └── librknnrt.so
└── armeabi-v7a/
    └── librknnrt.so
```

4. 将 `rknpu2/runtime/Linux/librknn_api/include/rknn_api.h` 复制到：
```
hand-sdk/src/main/cpp/rknn_api/rknn_api.h
```

### 3.3 RKNN 推理模型（必需）

向项目负责人获取以下文件并放置到：
```
hand-sdk/src/main/assets/
├── hand_check_rk3566.rknn   # 手部检测模型
└── yiwu_check_rk3566.rknn   # 异物检测模型
```

### 快速检查清单

放置完成后，验证文件结构：

```bash
# 检查 OpenCV
ls third_party/OpenCV-android-sdk/sdk/native/jni/abi-arm64-v8a/

# 检查 RKNN Runtime
ls hand-sdk/src/main/jniLibs/arm64-v8a/librknnrt.so
ls hand-sdk/src/main/jniLibs/armeabi-v7a/librknnrt.so

# 检查 RKNN 头文件
ls hand-sdk/src/main/cpp/rknn_api/rknn_api.h

# 检查 RKNN 模型
ls hand-sdk/src/main/assets/*.rknn

# 检查 SeetaFace6 模型（LFS 拉取后应为真实大文件，而非指针）
ls -lh face-sdk/src/main/assets/fr_2_10.dat
# 正常大小应为 ~50MB+，而非几 KB
```

---

## 4. Android Studio 配置

1. 打开 Android Studio，选择 **Open**，选中 `smartcheck/` 目录
2. 等待 Gradle Sync 完成
3. 确认以下 SDK 组件已安装（File → Settings → Android SDK → SDK Tools）：
   - Android SDK Build-Tools（34）
   - NDK（Side by side，推荐 r25c）
   - CMake **3.22.1**

4. 如果遇到 CMake 未找到的错误，在 `local.properties` 中添加：
   ```
   cmake.dir=/path/to/cmake-3.22.1
   ```

---

## 5. 编译构建

### 命令行构建（推荐）

```bash
# 第一次构建前先清理
./gradlew clean

# 构建 Debug APK（推荐开发使用）
./gradlew assembleDebug

# 构建 Release APK（需要签名配置）
./gradlew assembleRelease

# 仅编译 Kotlin（不打包，用于快速语法检查）
./gradlew :app:compileDebugKotlin

# Lint 检查
./gradlew :app:lintDebug

# 单元测试
./gradlew test
```

APK 输出路径：
- Debug：`app/build/outputs/apk/debug/app-debug.apk`
- Release：`app/build/outputs/apk/release/app-release.apk`

### Android Studio 构建

- 菜单：**Build → Build Bundle(s) / APK(s) → Build APK(s)**
- 或点击工具栏的 ▶ 运行按钮直接安装到已连接的设备

---

## 6. 安装与运行

### USB 调试安装

```bash
# 确认设备已连接
adb devices

# 安装 Debug APK
adb install -r app/build/outputs/apk/debug/app-debug.apk

# 查看 Timber 日志
adb logcat -s Timber:*
```

### 权限要求

首次启动时，App 需要以下权限（请在系统提示时全部允许）：
- 📷 摄像头（人脸识别、手部检测）
- 🎤 麦克风（可选）
- 📁 存储（导出记录）

---

## 7. 启动激活服务器（可选）

设备首次使用前需要激活。激活服务器用于管理激活码和设备授权。

```bash
# 无需安装额外依赖，使用 Python 标准库
python auth_server.py

# 服务器输出类似：
# Serving on http://0.0.0.0:80
# 管理页面：http://localhost/
```

**管理员登录：**
- 地址：`http://<服务器IP>/admin/login`
- 默认账号/密码：`admin` / `admin888`
- **⚠️ 首次登录后请立即修改密码**

**可用激活码：**
脚本中已内置测试激活码 `TEST001` ~ `TEST030`，用于开发调试。

**修改 App 激活服务器地址：**
在 `app/src/main/java/com/smartcheck/app/App.kt` 中修改：
```kotlin
const val ACTIVATION_URL = "http://192.168.1.100/api/device/activate"
```

---

## 8. 首次使用流程

1. **设备激活**：在设备上启动 App，在激活页输入激活码（如 `TEST001`）
2. **管理员登录**：使用默认账号密码登录管理后台
3. **员工录入**：进入员工管理，添加员工信息并拍摄人脸照片
4. **晨检测试**：返回首页，点击"我要晨检"，完成人脸识别 → 体温测量 → 手部检测流程
5. **查看记录**：进入晨检记录，查看本次晨检结果

---

## 9. 常见报错排查

### `AAPT: error: resource not found`

原因：模型文件缺失。检查 `hand-sdk/src/main/assets/` 下是否有 `.rknn` 文件。

### `CMake Error: Could not find cmake`

原因：CMake 3.22.1 未安装或路径未配置。参考 [第4节](#4-android-studio-配置)。

### `ld: error: cannot find -lrknnrt`

原因：`librknnrt.so` 未放置。参考 [第3.2节](#32-rknn-runtime-库必需仅-rk3566-设备)。

### `RKNN: RKNN API version x.x.x vs runtime x.x.x mismatch`

原因：`rknn_api.h` 头文件版本与 `librknnrt.so` 版本不匹配。请确保从同一版本的 rknn-toolkit2 获取两个文件。

### App 启动后白屏/闪退

查看 logcat 获取具体错误：
```bash
adb logcat -s AndroidRuntime:E Timber:* | head -50
```

### Gradle Sync 失败（网络问题）

在 `gradle.properties` 中配置代理：
```properties
systemProp.http.proxyHost=127.0.0.1
systemProp.http.proxyPort=7890
systemProp.https.proxyHost=127.0.0.1
systemProp.https.proxyPort=7890
```

---

## 相关文档

- [README.md](../README.md) - 项目总览
- [SETUP_GUIDE.md](../SETUP_GUIDE.md) - 第三方依赖详细配置指南
- [CONTRIBUTING.md](../CONTRIBUTING.md) - 贡献指南
- [架构设计.md](架构设计.md) - 系统架构说明
- [需求说明.md](需求说明.md) - 功能需求规格
