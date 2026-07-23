# 紫马晨检仪 — 对外接口文档

> **版本**: v1.0  
> **更新日期**: 2026-07-23  
> **厂商**: 紫马科技公司  
> **产品**: 紫马晨检仪（智能晨检系统）

---

## 目录

- [1. 概述](#1-概述)
- [2. 认证说明](#2-认证说明)
- [3. 通用约定](#3-通用约定)
- [4. 接口详情](#4-接口详情)
  - [4.1 健康检查](#41-健康检查)
  - [4.2 认证登录](#42-认证登录)
  - [4.3 晨检记录](#43-晨检记录)
  - [4.4 员工管理](#44-员工管理)
  - [4.5 用户账号同步](#45-用户账号同步)
  - [4.6 文件下载](#46-文件下载)
- [5. 枚举值说明](#5-枚举值说明)
- [6. 接入示例](#6-接入示例)

---

## 1. 概述

紫马晨检仪内置本地 REST API 服务器，第三方系统可通过该接口实现员工管理、晨检数据查询等功能。

| 项目 | 说明 |
|------|------|
| 服务器协议 | HTTP（明文） |
| 默认端口 | `8080` |
| 数据格式 | JSON |
| 字符编码 | UTF-8 |
| 认证方式 | JWT Bearer Token |

**基础地址**：`http://<设备IP>:8080`

### 请求限制

| 限制项 | 说明 |
|--------|------|
| 员工批量导入 | 单次最多 **100** 条 |
| 用户批量同步 | 单次最多 **100** 条 |
| 同步接口 limit | 最大 **500** |
| 员工查询 pageSize | 最大 **100** |
| 导出文件有效期 | **1 小时** |

---

## 2. 认证说明

### 2.1 登录获取 Token

调用 `POST /api/auth/login` 获取 JWT Token，有效期 **24 小时**。

### 2.2 使用 Token

在请求头中携带 Token：

```
Authorization: Bearer <token>
```

### 2.3 认证失败响应

未提供 Token 或 Token 无效/过期时，返回：

```json
{
  "code": 1001,
  "message": "Token 无效或已过期"
}
```

---

## 3. 通用约定

### 3.1 响应格式

所有接口返回统一 JSON 格式：

```json
{
  "code": 0,
  "message": "success",
  "data": { ... }
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| `code` | Int | 状态码，`0` 表示成功 |
| `message` | String | 状态描述 |
| `data` | Object/Array/null | 业务数据，失败时为 `null` |

### 3.2 分页响应格式

分页接口的 `data` 结构：

```json
{
  "list": [ ... ],
  "pagination": {
    "page": 1,
    "pageSize": 20,
    "total": 150,
    "totalPages": 8
  }
}
```

> **注意**: `pagination.total` 字段类型为 `Long`（JSON 中为数值）。

### 3.3 错误码

| 错误码 | 说明 |
|--------|------|
| `0` | 成功 |
| `1001` | 未授权 / Token 无效 |
| `1002` | 禁止访问 |
| `1003` | 资源不存在 |
| `1004` | 参数无效 |
| `1005` | 服务器内部错误 |
| `1006` | Token 已过期 |
| `2005` | 用户名已存在 |
| `2006` | 校验失败 |
| `2009` | 密码为空 |
| `2010` | 无效角色 |
| `2011` | 无效状态 |

---

## 4. 接口详情

### 4.1 健康检查

#### `GET /health`

检测设备 API 服务是否在线。

**认证**: 不需要

**响应示例**:

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "status": "ok",
    "timestamp": 1700000000000
  }
}
```

---

### 4.2 认证登录

#### `POST /api/auth/login`

登录获取 JWT Token。

**认证**: 不需要

**请求体**:

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `username` | String | 是 | 用户名 |
| `password` | String | 是 | 密码 |

**请求示例**:

```json
{
  "username": "admin",
  "password": "123456"
}
```

**成功响应**:

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "token": "eyJhbGciOiJIUzI1NiIs...",
    "expiresIn": 86400,
    "tokenType": "Bearer",
    "user": {
      "id": 1,
      "username": "admin",
      "name": "管理员"
    }
  }
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| `token` | String | JWT Token |
| `expiresIn` | Long | 过期时间（秒），固定 86400 |
| `tokenType` | String | Token 类型，固定 `"Bearer"` |
| `user.id` | Long | 用户 ID |
| `user.username` | String | 用户名 |
| `user.name` | String? | 显示名称，可能为 `null` |

**失败响应**:

```json
// HTTP 401
{ "code": 1001, "message": "用户名或密码错误" }

// HTTP 500
{ "code": 1005, "message": "登录失败: ..." }
```

---

### 4.3 晨检记录

#### `GET /api/records`

分页查询晨检记录，支持多条件筛选。

**认证**: 需要

**查询参数**:

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| `startDate` | String | **是** | — | 开始日期，格式 `yyyy-MM-dd` |
| `endDate` | String | **是** | — | 结束日期，格式 `yyyy-MM-dd` |
| `employeeId` | String | 否 | — | 按工号筛选 |
| `isPassed` | Boolean | 否 | — | 按通过状态筛选 |
| `isTempNormal` | Boolean | 否 | — | 按体温是否正常筛选 |
| `isHandNormal` | Boolean | 否 | — | 按手部是否正常筛选 |
| `includeImages` | Boolean | 否 | `false` | 是否返回图片 URL |
| `page` | Int | 否 | `1` | 页码（从 1 开始） |
| `pageSize` | Int | 否 | `20` | 每页条数 |

**响应示例**:

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "list": [
      {
        "id": 1,
        "userId": 10,
        "userName": "张三",
        "employeeId": "EMP001",
        "checkTime": 1700000000000,
        "temperature": 36.5,
        "isTempNormal": true,
        "isHandNormal": true,
        "isPassed": true,
        "handStatus": "NORMAL",
        "healthCertStatus": "VALID",
        "symptomFlags": "",
        "remark": "",
        "images": {
          "face": "/api/images/face_emp_123_1.jpg",
          "palm": "/api/images/palm_emp_123_1.jpg",
          "back": "/api/images/back_emp_123_1.jpg"
        }
      }
    ],
    "pagination": {
      "page": 1,
      "pageSize": 20,
      "total": 150,
      "totalPages": 8
    }
  }
}
```

> **说明**:
> - 仅当 `includeImages=true` 时返回 `images` 字段。图片 URL 为相对路径，完整地址为 `http://<设备IP>:8080<图片路径>`。
> - `symptomFlags` 为逗号分隔的大写枚举字符串，如 `"COUGH,FEVER"`；无症状时为空字符串 `""`。

---

#### `GET /api/records/sync`

增量同步晨检记录，适用于定期拉取新增数据的场景。

**认证**: 需要

**查询参数**:

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| `lastRecordId` | Long | **是** | — | 上次同步的最后记录 ID |
| `limit` | Int | 否 | `100` | 最大返回条数（1~500） |

**响应示例**:

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "list": [ ... ],
    "hasMore": true,
    "lastRecordId": 500,
    "syncTime": 1700000000000
  }
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| `hasMore` | Boolean | 是否还有更多数据，`true` 时请继续以 `lastRecordId` 调用 |
| `lastRecordId` | Long | 本次返回的最后记录 ID，下次传入此值 |
| `syncTime` | Long | 同步时间戳（毫秒） |

---

#### `GET /api/records/{id}`

查询单条晨检记录详情。

**认证**: 需要

**路径参数**:

| 参数 | 类型 | 说明 |
|------|------|------|
| `id` | Long | 记录 ID |

**查询参数**:

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `includeImages` | Boolean | `true` | 是否返回图片 URL |

**响应示例**: 同 `GET /api/records` 中的单条记录结构。

**失败响应**:

```json
// HTTP 400
{ "code": 1004, "message": "记录ID无效" }

// HTTP 404
{ "code": 1003, "message": "记录不存在" }
```

---

#### `GET /api/records/statistics`

查询指定日期范围内的晨检统计数据。

**认证**: 需要

**查询参数**:

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `startDate` | String | **是** | 开始日期，格式 `yyyy-MM-dd` |
| `endDate` | String | **是** | 结束日期，格式 `yyyy-MM-dd` |

**响应示例**:

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "totalCheck": 1000,
    "passed": 950,
    "failed": 50,
    "tempAbnormal": 30,
    "handAbnormal": 20,
    "dailyStats": [
      {
        "date": "2024-01-15",
        "total": 200,
        "passed": 190,
        "failed": 10,
        "tempAbnormal": 6,
        "handAbnormal": 4
      }
    ]
  }
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| `totalCheck` | Long | 总检测人次 |
| `passed` | Long | 通过人次 |
| `failed` | Long | 不合格人次 |
| `tempAbnormal` | Long | 体温异常人次 |
| `handAbnormal` | Long | 手部异常人次 |
| `dailyStats` | Array | 每日明细 |

---

#### `POST /api/records/export`

导出晨检记录为 CSV 文件，返回下载地址。

**认证**: 需要

**请求体**:

| 字段 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| `startDate` | String | **是** | — | 开始日期，格式 `yyyy-MM-dd` |
| `endDate` | String | **是** | — | 结束日期，格式 `yyyy-MM-dd` |
| `format` | String | 否 | `"csv"` | 导出格式 |
| `employeeId` | String | 否 | — | 按工号筛选 |

**响应示例**:

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "downloadUrl": "/api/downloads/records_2024-01-01_2024-01-31.csv",
    "expiresAt": 1700003600000
  }
}
```

> **注意**: 导出文件有效期 **1 小时**，过期后需重新导出。

---

### 4.4 员工管理

#### `GET /api/employees`

分页查询员工列表。

**认证**: 需要

**查询参数**:

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `page` | Int | `1` | 页码（从 1 开始） |
| `pageSize` | Int | `20` | 每页条数（最大 100） |
| `employeeId` | String | — | 按工号精确筛选 |
| `isActive` | Boolean | — | 按启用状态筛选 |

**响应示例**:

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "list": [
      {
        "id": 1,
        "name": "张三",
        "employeeId": "EMP001",
        "idCardNumber": "310101199001011234",
        "healthCertStatus": "VALID",
        "healthCertStartDate": 1700000000000,
        "healthCertEndDate": 1731536000000,
        "faceImage": "/api/employee-images/face_emp_123_1.jpg",
        "healthCertImage": "/api/employee-images/cert_emp_123_1.jpg",
        "isActive": true,
        "createdAt": 1700000000000
      }
    ],
    "pagination": {
      "page": 1,
      "pageSize": 20,
      "total": 50,
      "totalPages": 3
    }
  }
}
```

---

#### `GET /api/employees/sync`

增量同步员工数据，适用于定期拉取新增/变更员工的场景。

**认证**: 需要

**查询参数**:

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `lastEmployeeId` | Long | `0` | 上次同步的最后员工 ID |
| `limit` | Int | `100` | 最大返回条数（1~500） |

**响应示例**:

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "list": [ ... ],
    "hasMore": true,
    "lastRecordId": 50,
    "syncTime": 1700000000000
  }
}
```

---

#### `POST /api/employees/import`

批量导入员工信息（含人脸照片和健康证照片）。

**认证**: 需要

**请求体**:

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `employees` | Array | **是** | 员工列表（最多 100 条） |
| `incremental` | Boolean | 否 | `true`=增量（跳过已存在），`false`=全量覆盖 |

**员工对象字段**:

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `name` | String | **是** | 姓名 |
| `employeeId` | String | **是** | 工号（唯一标识） |
| `idCardNumber` | String | 否 | 身份证号 |
| `phone` | String | 否 | 手机号 |
| `position` | String | 否 | 岗位 |
| `department` | String | 否 | 部门 |
| `healthCertCode` | String | 否 | 健康证编号 |
| `faceImageBase64` | String | 否 | 人脸照片（Base64 编码） |
| `healthCertImageBase64` | String | 否 | 健康证照片（Base64 编码） |
| `healthCertStartDate` | Long | 否 | 健康证有效期起始（时间戳毫秒） |
| `healthCertEndDate` | Long | 否 | 健康证有效期截止（时间戳毫秒） |
| `isActive` | Boolean | 否 | 是否启用，默认 `true` |

> **图片大小建议**: Base64 编码的人脸照片建议控制在 **200KB** 以内（原始 JPEG），过大的图片会增加传输时间和人脸检测耗时。

**请求示例**:

```json
{
  "employees": [
    {
      "name": "张三",
      "employeeId": "EMP001",
      "idCardNumber": "310101199001011234",
      "position": "厨师",
      "department": "厨房",
      "faceImageBase64": "/9j/4AAQSkZJRg...",
      "isActive": true
    }
  ],
  "incremental": true
}
```

**响应示例**:

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "total": 1,
    "success": 1,
    "failed": 0,
    "details": [
      {
        "employeeId": "EMP001",
        "status": "success",
        "message": "导入成功（含人脸特征）",
        "userId": 10
      },
      {
        "employeeId": "EMP002",
        "status": "skipped",
        "message": "员工工号已存在，跳过",
        "userId": null
      }
    ]
  }
}
```

**返回状态值**:

| status | 说明 |
|--------|------|
| `success` | 导入成功 |
| `updated` | 已更新已有记录 |
| `skipped` | 已跳过（增量模式下工号已存在） |
| `failed` | 导入失败，`message` 中包含原因 |

---

#### `POST /api/employees/upload-photo`

上传单个员工人脸照片，自动检测人脸并提取特征。

**认证**: 需要

**请求体**:

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `fileName` | String | **是** | 文件名，格式 `face_<工号>.jpg` |
| `imageBase64` | String | **是** | 照片（Base64 编码 JPEG） |

> `fileName` 必须匹配格式 `face_<工号>.jpg`（工号仅允许字母、数字、下划线），系统从文件名中提取工号。

**响应示例**:

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "employeeId": "emp001",
    "userId": 10,
    "faceImagePath": "face_emp_1700000000000_10.jpg",
    "message": "上传成功，已提取人脸特征"
  }
}
```

**常见错误**:

| 错误 | 说明 |
|------|------|
| `"图片中未检测到人脸"` | 照片中未找到人脸 |
| `"无法提取人脸特征"` | 人脸特征提取失败 |
| `"员工工号不存在: xxx"` | 工号未在设备中注册，请先调用导入接口 |

---

#### `POST /api/employees/upload-cert-photo`

上传单个员工健康证照片。

**认证**: 需要

**请求体**:

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `fileName` | String | **是** | 文件名，格式 `cert_<工号>.jpg` 或 `.jpeg` 或 `.png` |
| `imageBase64` | String | **是** | 照片（Base64 编码） |

**响应示例**:

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "employeeId": "emp001",
    "userId": 10,
    "healthCertImagePath": "cert_emp_1700000000000_10.jpg",
    "message": "上传成功"
  }
}
```

---

#### `DELETE /api/employees/{employeeId}`

删除指定员工。

**认证**: 需要

**路径参数**:

| 参数 | 类型 | 说明 |
|------|------|------|
| `employeeId` | String | 员工工号 |

**响应示例**:

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "employeeId": "EMP001",
    "deleted": true
  }
}
```

---

#### `DELETE /api/employees/clear-all`

清空所有员工数据（不可逆操作）。

**认证**: 需要

**响应示例**:

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "deleted": true,
    "message": "所有员工已清空"
  }
}
```

> **警告**: 此操作将删除设备上所有员工记录及人脸特征数据，请谨慎调用。

---

### 4.5 用户账号同步

#### `POST /api/users/sync`

同步设备系统用户账号（登录账号，区别于员工档案）。

**认证**: 需要

**请求体**:

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `action` | String | **是** | 操作类型：`create` / `update` / `delete` / `sync` |
| `syncTime` | Long | 否 | 同步时间戳 |
| `users` | Array | **是** | 用户列表（最多 100 条） |

**用户对象字段**:

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `username` | String | **是** | 用户名 |
| `password` | String | **是** | 密码 |
| `passwordType` | String | 否 | 密码类型：`plain` / `md5` / `bcrypt`，默认 `plain` |
| `employeeId` | String | 否 | 关联员工工号 |
| `role` | String | 否 | 角色，默认 `employee` |
| `status` | String | 否 | 状态：`active` / `inactive`，默认 `active` |

**action 说明**:

| action | 行为 |
|--------|------|
| `create` | 创建新用户，已存在则跳过 |
| `update` | 更新已有用户信息 |
| `delete` | 删除指定用户 |
| `sync` | 全量同步（先清空所有用户，再批量写入） |

**响应示例**:

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "received": 3,
    "success": 2,
    "failed": 1,
    "details": [
      { "username": "user1", "status": "success", "message": "同步成功" },
      { "username": "user2", "status": "failed", "message": "用户名已存在" }
    ]
  }
}
```

---

### 4.6 文件下载

#### `GET /api/images/{filename}`

下载晨检记录关联的图片（人脸照片、手掌照片、手背照片）。

**认证**: 需要

**路径参数**:

| 参数 | 类型 | 说明 |
|------|------|------|
| `filename` | String | 图片文件名，来自记录查询响应中 `images` 字段的相对路径 |

**响应**: 图片原始二进制数据（`Content-Type: image/jpeg`）

**示例**:

```bash
curl http://192.168.1.100:8080/api/images/face_emp_123_1.jpg \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIs..." \
  -o face.jpg
```

---

#### `GET /api/employee-images/{filename}`

下载员工档案图片（人脸照片、健康证照片）。

**认证**: 需要

**路径参数**:

| 参数 | 类型 | 说明 |
|------|------|------|
| `filename` | String | 图片文件名，来自员工查询响应中 `faceImage` / `healthCertImage` 字段的相对路径 |

**响应**: 图片原始二进制数据

---

#### `GET /api/downloads/{filename}`

下载导出的 CSV 文件。

**认证**: 需要

**路径参数**:

| 参数 | 类型 | 说明 |
|------|------|------|
| `filename` | String | 文件名，来自导出接口返回的 `downloadUrl` |

**响应**: CSV 文件原始数据（`Content-Type: text/csv`）

> **注意**: 导出文件 **1 小时后自动过期删除**。

---

## 5. 枚举值说明

### 手部状态 `handStatus`

| 值 | 说明 |
|----|------|
| `NORMAL` | 正常 |
| `ABNORMAL` | 异常（检测到异物） |
| `NOT_CHECKED` | 未检测 |

### 健康证状态 `healthCertStatus`

| 值 | 说明 |
|----|------|
| `VALID` | 有效 |
| `EXPIRING_SOON` | 即将过期 |
| `EXPIRED` | 已过期 |

### 症状标记 `symptomFlags`

`symptomFlags` 为逗号分隔的大写枚举字符串，多症状时如 `"COUGH,FEVER"`，无症状时为空字符串 `""`。

| 值 | 说明 |
|----|------|
| `COUGH` | 咳嗽 |
| `FEVER` | 发热 |
| `HEADACHE` | 头痛 |
| `FATIGUE` | 疲劳 |
| `SORE_THROAT` | 咽痛 |
| `DIARRHEA` | 腹泻 |
| `OTHER` | 其他 |

---

## 6. 接入示例

以下为完整的接入流程示例（使用 `curl` 命令行）。

### 第一步：检查设备是否在线

```bash
curl http://192.168.1.100:8080/health
```

### 第二步：登录获取 Token

```bash
curl -X POST http://192.168.1.100:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"123456"}'
```

响应：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "token": "eyJhbGciOiJIUzI1NiIs...",
    "expiresIn": 86400,
    "tokenType": "Bearer",
    "user": { "id": 1, "username": "admin", "name": "管理员" }
  }
}
```

### 第三步：查询今日晨检记录

```bash
curl http://192.168.1.100:8080/api/records?startDate=2026-07-23&endDate=2026-07-23&page=1&pageSize=50 \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIs..."
```

### 第四步：增量同步晨检记录

```bash
# 首次同步（lastRecordId=0）
curl "http://192.168.1.100:8080/api/records/sync?lastRecordId=0&limit=100" \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIs..."

# 响应中 hasMore=true 时，用 lastRecordId 继续拉取
curl "http://192.168.1.100:8080/api/records/sync?lastRecordId=500&limit=100" \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIs..."
```

### 第五步：增量同步员工数据

```bash
# 首次同步（lastEmployeeId=0）
curl "http://192.168.1.100:8080/api/employees/sync?lastEmployeeId=0&limit=100" \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIs..."

# 响应中 hasMore=true 时，用 lastRecordId 继续拉取
curl "http://192.168.1.100:8080/api/employees/sync?lastEmployeeId=50&limit=100" \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIs..."
```

### 第六步：批量导入员工

```bash
curl -X POST http://192.168.1.100:8080/api/employees/import \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIs..." \
  -d '{
    "employees": [
      {
        "name": "李四",
        "employeeId": "EMP002",
        "position": "帮厨",
        "department": "厨房"
      }
    ],
    "incremental": true
  }'
```

---

> **文档结束** — 如有疑问请联系紫马科技技术支持。
