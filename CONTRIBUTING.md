# 贡献指南 / Contributing Guide (SmartCheck)

> 本文档面向项目开发团队与外部贡献者，中文为主，关键术语保留英文。

This project is intended for enterprise delivery. Changes must be traceable, reviewable, and verified.

---

## 1. 开发工作流 / Workflow

### 分支管理

- 以 Issue/工单 ID 作为追踪依据（即使只是一个文本备注）
- 每次改动新建分支：
  - `feature/<ticket>-<short-name>` 新功能
  - `fix/<ticket>-<short-name>` 缺陷修复
  - `hotfix/<ticket>-<short-name>` 生产紧急修复
- 保持改动小而专注，不要在一个 PR 里混入无关重构

### 前置环境

参见 [docs/quickstart.md](docs/quickstart.md) 或 [SETUP_GUIDE.md](SETUP_GUIDE.md)。

---

## 2. Commit 规范 / Commit Conventions

推荐格式（Angular-style）：

```
<type>(<scope>): <简要说明>

[可选正文]

[可选 Footer，如 Close #123]
```

| type | 说明 |
|------|------|
| `feat` | 新功能 |
| `fix` | 缺陷修复 |
| `refactor` | 重构（不改变功能） |
| `docs` | 仅文档变更 |
| `test` | 添加/修改测试 |
| `chore` | 构建/工具/配置变更 |
| `perf` | 性能优化 |

**示例：**
```
fix(camera): select cameraId 100/102 on RK3566
feat(hand): add still-image detection flow
refactor(ui): simplify HandCheck layout
docs(readme): add FAQ section
```

---

## 3. PR 标准 / Pull Request Standard

每次改动提交 PR 时，请包含：

1. **改动内容**：做了什么，为什么这样做
2. **测试验证**：在哪些设备/场景下验证过
3. **截图/录屏**：UI 改动必须附图
4. **影响范围**：是否影响摄像头/Native/序列化等敏感模块

PR 模板文件：`docs/PR_TEMPLATE.md`（如存在）

---

## 4. 质量门禁 / Quality Gates

提交 PR 前，必须在本地通过以下检查：

```bash
# 1. Kotlin 编译（快速语法检查）
./gradlew :app:compileDebugKotlin

# 2. 构建完整 Debug APK
./gradlew :app:assembleDebug

# 3. Lint 检查（不强制零警告，但不能新增 error）
./gradlew :app:lintDebug

# 4. 单元测试（如有）
./gradlew test
```

> **注意**：Android Gradle Plugin 要求 Java 17。Native/JNI 模块变更可能需要 NDK/CMake。

---

## 5. 设备回归测试 / Device Regression (RK3566)

如果改动涉及摄像头 / 检测流程 / UI，需在 RK3566 真机验证：

| 检查项 | 说明 |
|--------|------|
| 摄像头选择 | 人脸 `cameraId=100`，手部 `cameraId=102` |
| 启动/停止稳定性 | 打开 → 切换模式 → 退后台 → 返回，无黑屏 |
| 检测性能 | 检测延迟可接受，无 UI 卡顿 |
| 权限授予 | 相机/存储权限提示正常 |

详细回归清单：`docs/DEVICE_REGRESSION_CHECKLIST.md`（如存在）

---

## 6. 安全与隐私 / Security and Privacy

- **禁止提交密钥**：API Key、密码、`.jks` 签名文件的密码、`.pem` 等不得出现在代码中
  - `smartcheck.jks` 的密码请放入 `local.properties`（已在 `.gitignore` 中排除），格式如下：
    ```properties
    KEY_STORE_PASSWORD=your_keystore_password
    KEY_PASSWORD=your_key_password
    ```
- **最小权限**：不申请不必要的 Android 权限
- **敏感数据**：不持久化用户人脸图片/特征，除非需求明确要求且经过评审
- **日志安全**：不在 Timber/日志中打印身份证号、人脸特征等 PII 数据

---

## 7. 代码风格 / Code Style

遵循 [`AGENTS.md`](AGENTS.md) 中的 Code Style Guidelines，重点：

- Kotlin 4 空格缩进，行长 ≤120 字符
- 避免 `!!` 非空断言，改用 `?.`、`?:` 或 `require/check`
- 优先 `val`，必要时才用 `var`
- `:app` 中使用 `Timber.*`，不使用 `Log.*` 或 `println`
- 公共 API 写明显式类型

---

## 8. 提问与反馈

- 通过 GitHub Issues 提交 Bug 报告或功能建议
- 紧急问题直接联系项目负责人

