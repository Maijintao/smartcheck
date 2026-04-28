"""
SmartCheck 设备激活 + 版本管理服务器

端点汇总：
  GET  /admin/login               登录页面
  GET  /admin/logout              退出登录
  GET  /                          管理页面（需登录）
  GET  /uploads/<filename>        下载已上传的 APK（公开）
  GET  /api/app/version/latest    App 检查更新（公开）
  GET  /api/app/version/history   App 查看版本历史（公开）
    POST /api/device/activate       设备 MAC 授权校验（公开）
  POST /admin/apk/upload          上传 APK 并发布版本（需登录）
    POST /admin/codes/add           添加认证数据（MAC/激活码，需登录）
    POST /admin/codes/delete        删除认证数据（MAC/激活码，需登录）
  POST /admin/change_password     修改管理密码（需登录）
"""

import hashlib
import json
import mimetypes
import os
import re
import secrets
import socket
import sqlite3
import threading
import time
from datetime import datetime
from http.server import BaseHTTPRequestHandler
try:
    from http.server import ThreadingHTTPServer
except ImportError:
    from http.server import HTTPServer
    from socketserver import ThreadingMixIn

    class ThreadingHTTPServer(ThreadingMixIn, HTTPServer):
        daemon_threads = True
        allow_reuse_address = True
from urllib.parse import parse_qs, urlparse

BASE_DIR    = os.path.dirname(os.path.abspath(__file__))
DB_PATH     = os.path.join(BASE_DIR, "smartcheck.db")
UPLOADS_DIR = os.path.join(BASE_DIR, "uploads")

DEFAULT_PASSWORD = "admin888"   # 仅首次启动时使用；登录后请在管理页修改
_SESSION_EXPIRE  = 12 * 3600   # 会话有效期：12 小时

_SOCKET_TIMEOUT_SECONDS = 60
_MAX_BODY_BYTES_DEFAULT = 1 * 1024 * 1024
_MAX_BODY_BYTES_UPLOAD  = 200 * 1024 * 1024

MAC_REGEX = re.compile(r"^[0-9A-F]{2}(:[0-9A-F]{2}){5}$")
ACTIVATION_CODE_REGEX = re.compile(r"^[A-Z0-9][A-Z0-9_-]{3,127}$")

# 预置 MAC 白名单（仅首次运行时写入数据库，后续以数据库为准）
INITIAL_WHITELIST_MACS = []


def normalize_mac(mac: str) -> str:
    cleaned = (mac or "").strip().upper().replace("-", ":")
    return cleaned


def is_valid_mac(mac: str) -> bool:
    return bool(MAC_REGEX.fullmatch(normalize_mac(mac)))


def normalize_activation_code(code: str) -> str:
    return (code or "").strip().upper()


def is_valid_activation_code(code: str) -> bool:
    return bool(ACTIVATION_CODE_REGEX.fullmatch(normalize_activation_code(code)))

db_lock = threading.Lock()

# ────────────────────────────────────────────
# 会话管理（内存存储，重启失效）
# ────────────────────────────────────────────

_sessions: dict = {}   # {token: expires_at}
_sessions_lock = threading.Lock()


def _new_session() -> str:
    token = secrets.token_hex(24)
    with _sessions_lock:
        _sessions[token] = time.time() + _SESSION_EXPIRE
    return token


def _check_session(token: str) -> bool:
    with _sessions_lock:
        exp = _sessions.get(token)
        if exp is None:
            return False
        if time.time() > exp:
            del _sessions[token]
            return False
        return True


def _revoke_session(token: str):
    with _sessions_lock:
        _sessions.pop(token, None)


def _revoke_all_sessions():
    with _sessions_lock:
        _sessions.clear()


# ────────────────────────────────────────────
# 密码管理
# ────────────────────────────────────────────

def _hash_pw(password: str) -> str:
    return hashlib.sha256(password.encode('utf-8')).hexdigest()


def get_admin_pw_hash() -> str:
    with db_lock:
        with get_conn() as conn:
            row = conn.execute(
                "SELECT value FROM settings WHERE key='admin_pw_hash'"
            ).fetchone()
    return row["value"] if row else _hash_pw(DEFAULT_PASSWORD)


def verify_password(password: str) -> bool:
    return _hash_pw(password) == get_admin_pw_hash()


def set_admin_password(new_password: str):
    h = _hash_pw(new_password)
    with db_lock:
        with get_conn() as conn:
            conn.execute(
                "INSERT OR REPLACE INTO settings(key, value) VALUES('admin_pw_hash', ?)", (h,)
            )
            conn.commit()


# ────────────────────────────────────────────
# multipart/form-data 解析（纯 stdlib）
# ────────────────────────────────────────────

def parse_multipart(content_type: str, body: bytes) -> dict:
    boundary = None
    for seg in content_type.split(';'):
        seg = seg.strip()
        if seg.lower().startswith('boundary='):
            boundary = seg[9:].strip('"')
            break
    if not boundary:
        return {}

    fields = {}
    delim = ('--' + boundary).encode()

    for chunk in body.split(delim)[1:]:
        if chunk[:2] == b'--':
            break
        if b'\r\n\r\n' not in chunk:
            continue
        hdr_bytes, _, content = chunk.partition(b'\r\n\r\n')
        if content.endswith(b'\r\n'):
            content = content[:-2]

        field_name = filename = None
        for line in hdr_bytes.decode('utf-8', errors='ignore').splitlines():
            if 'Content-Disposition' not in line:
                continue
            for item in line.split(';'):
                item = item.strip()
                if item.lower().startswith('name='):
                    field_name = item[5:].strip('"')
                elif item.lower().startswith('filename='):
                    filename = item[9:].strip('"')

        if field_name:
            if filename is not None:
                fields[field_name] = {'filename': filename, 'data': content}
            else:
                fields[field_name] = content.decode('utf-8', errors='ignore')

    return fields


# ────────────────────────────────────────────
# 数据库
# ────────────────────────────────────────────

def get_conn():
    conn = sqlite3.connect(DB_PATH)
    conn.row_factory = sqlite3.Row
    return conn


def init_db():
    os.makedirs(UPLOADS_DIR, exist_ok=True)

    with get_conn() as conn:
        conn.execute("""
            CREATE TABLE IF NOT EXISTS activation_codes (
                code         TEXT PRIMARY KEY,
                device_sn    TEXT DEFAULT '',
                activated_at TEXT DEFAULT ''
            )
        """)
        conn.execute("""
            CREATE TABLE IF NOT EXISTS device_whitelist (
                mac           TEXT PRIMARY KEY,
                note          TEXT DEFAULT '',
                status        INTEGER DEFAULT 1,
                created_at    TEXT NOT NULL,
                first_seen_at TEXT DEFAULT '',
                last_seen_at  TEXT DEFAULT ''
            )
        """)
        conn.execute("""
            CREATE TABLE IF NOT EXISTS app_versions (
                id            INTEGER PRIMARY KEY AUTOINCREMENT,
                version_code  INTEGER NOT NULL,
                version_name  TEXT    NOT NULL,
                apk_url       TEXT    NOT NULL,
                release_notes TEXT    DEFAULT '',
                created_at    TEXT    NOT NULL,
                is_latest     INTEGER DEFAULT 0
            )
        """)
        conn.execute("""
            CREATE TABLE IF NOT EXISTS settings (
                key   TEXT PRIMARY KEY,
                value TEXT NOT NULL
            )
        """)

        # 初始化 MAC 白名单
        existing = conn.execute("SELECT COUNT(*) FROM device_whitelist").fetchone()[0]
        if existing == 0:
            now = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
            for mac in INITIAL_WHITELIST_MACS:
                normalized = normalize_mac(mac)
                if not is_valid_mac(normalized):
                    continue
                conn.execute(
                    "INSERT OR IGNORE INTO device_whitelist(mac, created_at) VALUES(?, ?)",
                    (normalized, now),
                )
            if INITIAL_WHITELIST_MACS:
                print(f"[DB] 初始化 {len(INITIAL_WHITELIST_MACS)} 个 MAC 白名单")

        # 初始化管理密码（仅首次）
        pw_exists = conn.execute(
            "SELECT key FROM settings WHERE key='admin_pw_hash'"
        ).fetchone()
        if not pw_exists:
            conn.execute(
                "INSERT INTO settings(key, value) VALUES('admin_pw_hash', ?)",
                (_hash_pw(DEFAULT_PASSWORD),)
            )
            print(f"\n{'!'*50}")
            print(f"[Auth] 管理后台初始密码: {DEFAULT_PASSWORD}")
            print(f"[Auth] 登录后请立即在管理页面修改密码！")
            print(f"{'!'*50}\n")

        conn.commit()


# ────────────────────────────────────────────
# 请求处理
# ────────────────────────────────────────────

class Handler(BaseHTTPRequestHandler):

    def setup(self):
        super().setup()
        try:
            self.connection.settimeout(_SOCKET_TIMEOUT_SECONDS)
        except Exception:
            pass

    # ── GET ──────────────────────────────────

    def do_GET(self):
        path = urlparse(self.path).path

        # 公开端点
        if path == '/admin/login':
            self._serve_login_page()
            return
        if path == '/admin/logout':
            self._do_logout()
            return
        if path == '/api/app/version/latest':
            self._api_version_latest()
            return
        if path == '/api/app/version/history':
            self._api_version_history()
            return
        if path.startswith('/uploads/'):
            self._serve_upload(path)
            return

        # 以下需要登录
        if not self._require_auth():
            return

        if path in ('/', '/status'):
            query = urlparse(self.path).query
            self._serve_admin_page(query)
        else:
            self.send_error(404)

    # ── POST ─────────────────────────────────

    def do_POST(self):
        path = urlparse(self.path).path

        # 公开端点
        if path == '/admin/login':
            self._handle_login()
            return
        if path == '/api/device/activate':
            self._api_activate()
            return

        # 以下需要登录
        if not self._require_auth():
            return

        if path == '/admin/apk/upload':
            self._admin_apk_upload()
        elif path == '/admin/codes/add':
            self._admin_add_codes()
        elif path == '/admin/codes/delete':
            self._admin_delete_code()
        elif path == '/admin/change_password':
            self._admin_change_password()
        else:
            self.send_error(404)

    # ── 登录 / 登出 ───────────────────────────

    def _serve_login_page(self, error: str = ""):
        error_html = (
            f'<p style="color:#dc2626;margin:0 0 12px;font-size:14px">⚠ {error}</p>'
            if error else ""
        )
        html = f"""<!DOCTYPE html>
<html>
<head>
<meta charset="utf-8">
<title>SmartCheck 管理登录</title>
<style>
  body   {{ margin:0; background:#f0fdf4; display:flex;
           justify-content:center; align-items:center; height:100vh;
           font-family:Arial,sans-serif; }}
  .card  {{ background:#fff; border-radius:10px; padding:40px 48px;
           box-shadow:0 4px 24px rgba(0,0,0,.08); min-width:320px; }}
  h2     {{ color:#16a34a; margin:0 0 24px; text-align:center; }}
  label  {{ display:block; font-size:13px; color:#374151; margin-bottom:4px; }}
  input[type=password] {{
    width:100%; padding:9px 12px; box-sizing:border-box;
    border:1px solid #d1d5db; border-radius:6px; font-size:15px;
    margin-bottom:18px; }}
  input[type=password]:focus {{
    outline:none; border-color:#16a34a; box-shadow:0 0 0 2px #bbf7d0; }}
  button {{ width:100%; padding:10px; background:#16a34a; color:#fff;
           border:none; border-radius:6px; font-size:15px; cursor:pointer; }}
  button:hover {{ background:#15803d; }}
</style>
</head>
<body>
<div class="card">
  <h2>SmartCheck 管理后台</h2>
  {error_html}
  <form method="POST" action="/admin/login">
    <label>管理密码</label>
    <input type="password" name="password" autofocus placeholder="请输入管理密码">
    <button type="submit">登录</button>
  </form>
</div>
</body>
</html>"""
        self._write_html(html)

    def _handle_login(self):
        body = self._read_body()
        if body is None:
            return
        params = parse_qs(body)
        password = params.get('password', [''])[0]

        if verify_password(password):
            token = _new_session()
            self.send_response(302)
            self.send_header('Location', '/')
            self.send_header(
                'Set-Cookie',
                f'session={token}; HttpOnly; Path=/; Max-Age={_SESSION_EXPIRE}'
            )
            self.end_headers()
        else:
            self._serve_login_page(error="密码错误，请重试")

    def _do_logout(self):
        token = self._get_session_token()
        if token:
            _revoke_session(token)
        self.send_response(302)
        self.send_header('Location', '/admin/login')
        self.send_header('Set-Cookie', 'session=; HttpOnly; Path=/; Max-Age=0')
        self.end_headers()

    # ── 鉴权工具 ──────────────────────────────

    def _get_session_token(self) -> str:
        for part in self.headers.get('Cookie', '').split(';'):
            part = part.strip()
            if part.startswith('session='):
                return part[8:]
        return ''

    def _is_authenticated(self) -> bool:
        token = self._get_session_token()
        return bool(token) and _check_session(token)

    def _require_auth(self) -> bool:
        """已认证返回 True；否则重定向到登录页并返回 False。"""
        if not self._is_authenticated():
            self._redirect('/admin/login')
            return False
        return True

    # ── 静态文件：下载 APK ───────────────────

    def _serve_upload(self, url_path):
        filename = os.path.basename(url_path[len('/uploads/'):])
        filepath = os.path.join(UPLOADS_DIR, filename)
        if not os.path.isfile(filepath):
            self.send_error(404)
            return
        mime, _ = mimetypes.guess_type(filepath)
        mime = mime or 'application/octet-stream'
        size = os.path.getsize(filepath)
        self.send_response(200)
        self.send_header('Content-Type', mime)
        self.send_header('Content-Length', size)
        self.send_header('Content-Disposition', f'attachment; filename="{filename}"')
        self.end_headers()
        with open(filepath, 'rb') as f:
            while True:
                chunk = f.read(65536)
                if not chunk:
                    break
                self.wfile.write(chunk)

    # ── API: 最新版本 ─────────────────────────

    def _api_version_latest(self):
        with db_lock:
            with get_conn() as conn:
                row = conn.execute(
                    "SELECT * FROM app_versions WHERE is_latest=1 ORDER BY id DESC LIMIT 1"
                ).fetchone()

        if row is None:
            self._send_json({"code": 1, "message": "暂无版本信息", "data": None})
        else:
            self._send_json({
                "code": 0, "message": "success",
                "data": {
                    "versionCode":  row["version_code"],
                    "versionName":  row["version_name"],
                    "apkUrl":       row["apk_url"],
                    "releaseNotes": row["release_notes"],
                    "createdAt":    row["created_at"],
                }
            })

    # ── API: 版本历史 ─────────────────────────

    def _api_version_history(self):
        with db_lock:
            with get_conn() as conn:
                rows = conn.execute(
                    "SELECT * FROM app_versions ORDER BY id DESC LIMIT 10"
                ).fetchall()
        history = [
            {
                "versionCode":  r["version_code"],
                "versionName":  r["version_name"],
                "releaseNotes": r["release_notes"],
                "createdAt":    r["created_at"],
                "isLatest":     bool(r["is_latest"]),
            }
            for r in rows
        ]
        self._send_json({"code": 0, "data": history})

    # ── API: 设备激活 ─────────────────────────

    def _api_activate(self):
        body = self._read_body()
        if body is None:
            return
        try:
            data = json.loads(body)
            raw_mac = str(data.get('deviceMac') or data.get('mac') or '').strip()
            raw_code = str(data.get('activationCode', '')).strip()
            if not raw_mac and raw_code and is_valid_mac(raw_code):
                # 兼容旧客户端把 MAC 放在 activationCode 字段。
                raw_mac = raw_code
        except Exception:
            self.send_error(400, "Invalid JSON")
            return

        if raw_mac:
            self._activate_by_mac(raw_mac)
            return

        if raw_code:
            self._activate_by_legacy_code(raw_code, data)
            return

        self._send_json({
            "code": 1004,
            "message": "缺少授权参数（deviceMac/mac 或 activationCode）",
            "data": {"activated": False},
        })

    def _activate_by_mac(self, raw_mac: str):
        mac = normalize_mac(raw_mac)
        if not is_valid_mac(mac):
            self._send_json({"code": 1003, "message": "MAC 地址格式无效", "data": {"activated": False}})
            return

        print(f"\n{'='*50}")
        print(f"设备请求授权  MAC: {mac}")
        print('='*50)

        with db_lock:
            with get_conn() as conn:
                row = conn.execute(
                    "SELECT * FROM device_whitelist WHERE mac=? AND status=1", (mac,)
                ).fetchone()

                if row is None:
                    print("✗ MAC 不在白名单\n")
                    self._send_json({"code": 1001, "message": "设备未授权（MAC 不在认证清单）", "data": {"activated": False}})
                    return

                now = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
                first_seen = row["first_seen_at"] or now
                conn.execute(
                    "UPDATE device_whitelist SET first_seen_at=?, last_seen_at=? WHERE mac=?",
                    (first_seen, now, mac),
                )
                conn.commit()

        print("✓ MAC 授权验证成功!\n")
        self._send_json({
            "code": 0,
            "message": "设备授权通过",
            "data": {"activated": True, "deviceMac": mac, "mode": "mac"},
        })

    def _activate_by_legacy_code(self, raw_code: str, payload: dict):
        code = normalize_activation_code(raw_code)
        if not is_valid_activation_code(code):
            self._send_json({"code": 1005, "message": "激活码格式无效", "data": {"activated": False}})
            return

        device_sn = str(
            payload.get('deviceSn') or payload.get('deviceSN') or payload.get('deviceId') or ''
        ).strip()
        if not device_sn:
            # 兼容旧客户端未上传 deviceSn 的情况，用请求源地址兜底绑定。
            device_sn = str(getattr(self, 'client_address', ('',))[0] or '').strip()

        print(f"\n{'='*50}")
        print(f"设备请求授权  激活码: {code}  deviceSn: {device_sn or '-'}")
        print('='*50)

        with db_lock:
            with get_conn() as conn:
                row = conn.execute(
                    "SELECT code, device_sn, activated_at FROM activation_codes WHERE code=?",
                    (code,),
                ).fetchone()
                if row is None:
                    print("✗ 激活码不存在\n")
                    self._send_json({"code": 1006, "message": "激活码无效", "data": {"activated": False}})
                    return

                bound_sn = (row['device_sn'] or '').strip()
                if bound_sn and device_sn and bound_sn != device_sn:
                    print(f"✗ 激活码已绑定其他设备: {bound_sn}\n")
                    self._send_json({
                        "code": 1007,
                        "message": "激活码已绑定其他设备",
                        "data": {"activated": False},
                    })
                    return

                now = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
                target_sn = bound_sn or device_sn or ''
                if not bound_sn:
                    conn.execute(
                        "UPDATE activation_codes SET device_sn=?, activated_at=? WHERE code=?",
                        (target_sn, now, code),
                    )
                    conn.commit()

        print("✓ 激活码授权验证成功!\n")
        self._send_json({
            "code": 0,
            "message": "设备授权通过",
            "data": {
                "activated": True,
                "activationCode": code,
                "deviceSn": device_sn,
                "mode": "activationCode",
            },
        })

    # ── 管理：上传 APK 并发布版本 ─────────────

    def _admin_apk_upload(self):
        content_type = self.headers.get('Content-Type', '')
        if 'multipart/form-data' not in content_type:
            self._send_json({"code": 1, "message": "需要 multipart/form-data"}, status=400)
            return

        body = self._read_body_bytes(max_len=_MAX_BODY_BYTES_UPLOAD)
        if body is None:
            return
        fields = parse_multipart(content_type, body)

        apk_field = fields.get('apk_file')
        if not isinstance(apk_field, dict) or not apk_field.get('data'):
            self._send_json({"code": 1, "message": "未收到 APK 文件"}, status=400)
            return

        try:
            version_code  = int(fields.get('version_code', '0'))
            version_name  = fields.get('version_name', '').strip()
            release_notes = fields.get('release_notes', '').strip()
        except Exception:
            self._send_json({"code": 1, "message": "版本号格式错误"}, status=400)
            return

        if not version_code or not version_name:
            self._send_json({"code": 1, "message": "versionCode 和版本名称为必填"}, status=400)
            return

        original_name = apk_field.get('filename', 'update.apk')
        # 用版本号命名，确保每次上传都是独立的文件
        safe_filename = f"smartcheck-v{version_name}.apk"
        filepath = os.path.join(UPLOADS_DIR, safe_filename)

        with open(filepath, 'wb') as f:
            f.write(apk_field['data'])

        host    = self.headers.get('Host', '127.0.0.1:8080')
        apk_url = f"http://{host}/uploads/{safe_filename}"

        now = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
        with db_lock:
            with get_conn() as conn:
                conn.execute("UPDATE app_versions SET is_latest=0")
                conn.execute(
                    """INSERT INTO app_versions
                       (version_code, version_name, apk_url, release_notes, created_at, is_latest)
                       VALUES (?, ?, ?, ?, ?, 1)""",
                    (version_code, version_name, apk_url, release_notes, now)
                )
                conn.commit()

        size_mb = len(apk_field['data']) / 1024 / 1024
        print(f"[Admin] 发布版本: {version_name} (code={version_code}), {size_mb:.1f}MB → {apk_url}")
        self._send_json({"code": 0, "message": "发布成功", "apk_url": apk_url})

    # ── 管理：修改密码 ────────────────────────

    def _admin_change_password(self):
        body = self._read_body()
        if body is None:
            return
        params = parse_qs(body)
        old_pw  = params.get('old_password', [''])[0]
        new_pw  = params.get('new_password', [''])[0].strip()
        new_pw2 = params.get('new_password2', [''])[0].strip()

        if not verify_password(old_pw):
            self._redirect('/?pw_error=wrong')
            return
        if len(new_pw) < 4:
            self._redirect('/?pw_error=short')
            return
        if new_pw != new_pw2:
            self._redirect('/?pw_error=mismatch')
            return

        set_admin_password(new_pw)
        _revoke_all_sessions()   # 强制重新登录
        print("[Auth] 管理密码已修改，所有会话已吊销。")
        self._redirect('/admin/login?msg=changed')

    # ── 管理：添加认证数据（MAC / 激活码） ─────

    def _admin_add_codes(self):
        body = self._read_body()
        if body is None:
            return
        params = parse_qs(body)
        raw_macs = params.get('macs', [''])[0]
        raw_codes = params.get('codes', [''])[0]

        incoming_macs = [normalize_mac(c) for c in raw_macs.replace(',', '\n').splitlines() if c.strip()]
        incoming_codes = [normalize_activation_code(c) for c in raw_codes.replace(',', '\n').splitlines() if c.strip()]

        added_macs = 0
        invalid_macs = 0
        added_codes = 0
        invalid_codes = 0
        now = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
        with db_lock:
            with get_conn() as conn:
                for mac in incoming_macs:
                    if not is_valid_mac(mac):
                        invalid_macs += 1
                        continue
                    try:
                        conn.execute(
                            "INSERT INTO device_whitelist(mac, created_at) VALUES(?, ?)",
                            (mac, now),
                        )
                        added_macs += 1
                    except sqlite3.IntegrityError:
                        pass

                for code in incoming_codes:
                    if not is_valid_activation_code(code):
                        invalid_codes += 1
                        continue
                    try:
                        conn.execute(
                            "INSERT INTO activation_codes(code, device_sn, activated_at) VALUES(?, '', '')",
                            (code,),
                        )
                        added_codes += 1
                    except sqlite3.IntegrityError:
                        pass
                conn.commit()

        print(
            f"[Admin] 添加 MAC {added_macs} 个(非法 {invalid_macs})，"
            f"激活码 {added_codes} 个(非法 {invalid_codes})"
        )
        self._redirect('/')

    # ── 管理：删除认证数据（MAC / 激活码） ─────

    def _admin_delete_code(self):
        body = self._read_body()
        if body is None:
            return
        params = parse_qs(body)
        kind = params.get('kind', ['auto'])[0].strip().lower()
        mac = normalize_mac(params.get('mac', [''])[0].strip())
        code = normalize_activation_code(params.get('code', [''])[0].strip())

        if kind == 'mac' and not mac:
            self.send_error(400, "MAC 不能为空")
            return
        if kind == 'code' and not code:
            self.send_error(400, "激活码不能为空")
            return

        with db_lock:
            with get_conn() as conn:
                if kind == 'mac' or (kind == 'auto' and mac and not code):
                    conn.execute("DELETE FROM device_whitelist WHERE mac=?", (mac,))
                    print(f"[Admin] 删除 MAC 白名单: {mac}")
                else:
                    target_code = code or normalize_activation_code(params.get('mac', [''])[0].strip())
                    if not target_code:
                        self.send_error(400, "删除参数不能为空")
                        return
                    conn.execute("DELETE FROM activation_codes WHERE code=?", (target_code,))
                    print(f"[Admin] 删除激活码: {target_code}")
                conn.commit()
        self._redirect('/')

    # ── 管理页面 ──────────────────────────────

    def _serve_admin_page(self, query_string: str = ""):
        # 解析错误/提示参数
        qs = parse_qs(query_string)
        pw_error = qs.get('pw_error', [''])[0]
        pw_error_text = {
            'wrong':    '原密码错误',
            'short':    '新密码至少 4 位',
            'mismatch': '两次输入的新密码不一致',
        }.get(pw_error, '')

        with db_lock:
            with get_conn() as conn:
                mac_rows   = conn.execute(
                    "SELECT mac, first_seen_at, last_seen_at, created_at FROM device_whitelist ORDER BY created_at DESC, mac"
                ).fetchall()
                code_rows_data = conn.execute(
                    "SELECT code, device_sn, activated_at FROM activation_codes ORDER BY code"
                ).fetchall()
                latest_ver = conn.execute(
                    "SELECT * FROM app_versions WHERE is_latest=1 ORDER BY id DESC LIMIT 1"
                ).fetchone()
                versions   = conn.execute(
                    "SELECT * FROM app_versions ORDER BY id DESC LIMIT 20"
                ).fetchall()

        mac_total  = len(mac_rows)
        mac_used   = sum(1 for r in mac_rows if r["first_seen_at"])
        mac_unused = mac_total - mac_used

        code_total  = len(code_rows_data)
        code_used   = sum(1 for r in code_rows_data if (r["device_sn"] or '').strip())
        code_unused = code_total - code_used

        next_vc      = (latest_ver["version_code"] + 1) if latest_ver else 1
        cur_ver_text = (
            f'{latest_ver["version_name"]} (versionCode={latest_ver["version_code"]})'
            if latest_ver else "尚未发布"
        )

        # MAC 白名单行
        mac_table_rows = ""
        for r in mac_rows:
            if r["first_seen_at"]:
                status = f"已验证（首次 {r['first_seen_at']} / 最近 {r['last_seen_at']}）"
            else:
                status = "未验证"
            cls = "used" if r["first_seen_at"] else "unused"
            del_btn = (
                f'<form method="POST" action="/admin/codes/delete" style="display:inline">'
                f'<input type="hidden" name="kind" value="mac">'
                f'<input type="hidden" name="mac" value="{r["mac"]}">'
                f'<input type="submit" value="删除" '
                f'onclick="return confirm(\'确定删除？\')" '
                f'style="background:#DC2626;padding:3px 8px;border:none;color:#fff;'
                f'cursor:pointer;border-radius:3px;font-size:12px">'
                f'</form>'
            )
            mac_table_rows += (
                f'<tr class="{cls}">'
                f'<td>{r["mac"]}</td><td>{status}</td><td>{del_btn}</td></tr>\n'
            )

        # 激活码行
        code_table_rows = ""
        for r in code_rows_data:
            bound_sn = (r["device_sn"] or '').strip()
            activated_at = (r["activated_at"] or '').strip()
            status = f"已绑定（{bound_sn or '-'}）" if bound_sn else "未使用"
            if activated_at:
                status += f" / {activated_at}"
            cls = "used" if bound_sn else "unused"
            del_btn = (
                f'<form method="POST" action="/admin/codes/delete" style="display:inline">'
                f'<input type="hidden" name="kind" value="code">'
                f'<input type="hidden" name="code" value="{r["code"]}">'
                f'<input type="submit" value="删除" '
                f'onclick="return confirm(\'确定删除？\')" '
                f'style="background:#DC2626;padding:3px 8px;border:none;color:#fff;'
                f'cursor:pointer;border-radius:3px;font-size:12px">'
                f'</form>'
            )
            code_table_rows += (
                f'<tr class="{cls}">'
                f'<td>{r["code"]}</td><td>{status}</td><td>{del_btn}</td></tr>\n'
            )

        # 版本历史行
        ver_rows = ""
        for v in versions:
            tag = " <span style='color:#16a34a;font-weight:bold'>[当前]</span>" if v["is_latest"] else ""
            ver_rows += (
                f'<tr><td>{v["version_code"]}</td>'
                f'<td>{v["version_name"]}{tag}</td>'
                f'<td style="font-size:12px;word-break:break-all">'
                f'<a href="{v["apk_url"]}" target="_blank">{v["apk_url"]}</a></td>'
                f'<td>{v["release_notes"] or "—"}</td>'
                f'<td>{v["created_at"]}</td></tr>\n'
            )

        pw_error_html = (
            f'<p style="color:#dc2626;margin:4px 0 0;font-size:13px">⚠ {pw_error_text}</p>'
            if pw_error_text else ""
        )

        html = f"""<!DOCTYPE html>
<html>
<head>
<meta charset="utf-8">
<title>SmartCheck 管理</title>
<style>
  body   {{ font-family:Arial,sans-serif; padding:24px;
           max-width:1000px; margin:auto; color:#1f2937; }}
  .topbar {{ display:flex; justify-content:space-between; align-items:center;
            margin-bottom:8px; }}
  h1     {{ color:#16a34a; margin:0; }}
  .logout-btn {{ background:#6b7280; color:#fff; border:none; padding:7px 16px;
                border-radius:5px; cursor:pointer; font-size:13px;
                text-decoration:none; }}
  .logout-btn:hover {{ background:#4b5563; }}
  h2     {{ color:#374151; border-bottom:2px solid #4ade80;
           padding-bottom:4px; margin-top:0; }}
  table  {{ border-collapse:collapse; width:100%; margin-top:8px; }}
  th, td {{ border:1px solid #d1d5db; padding:7px 10px; text-align:left; font-size:13px; }}
  th     {{ background:#16a34a; color:#fff; }}
  .used  {{ background:#fee2e2; }}
  .unused {{ background:#dcfce7; }}
  input[type=text], input[type=number], input[type=password], textarea {{
    width:100%; padding:7px; margin:3px 0 10px;
    box-sizing:border-box; border:1px solid #d1d5db; border-radius:4px; }}
  input[type=submit], .btn {{
    background:#16a34a; color:#fff; border:none;
    padding:9px 22px; cursor:pointer; border-radius:5px; font-size:14px; }}
  .card  {{ background:#f9fafb; border:1px solid #e5e7eb;
           border-radius:8px; padding:20px; margin-bottom:20px; }}
  .stat  {{ display:inline-block; margin-right:24px; font-size:15px; }}
  #drop-zone {{
    border:2px dashed #4ade80; border-radius:8px; padding:32px;
    text-align:center; cursor:pointer; background:#f0fdf4; transition:background .2s;
    margin-bottom:12px; }}
  #drop-zone.dragover {{ background:#dcfce7; border-color:#16a34a; }}
  #drop-zone label {{ cursor:pointer; color:#16a34a; font-weight:bold; }}
  #file-info {{ color:#374151; font-size:13px; margin-bottom:8px; }}
  #up-progress {{ margin-top:10px; font-size:13px; }}
  progress {{ width:100%; height:14px; accent-color:#16a34a; }}
  .pw-form {{ display:flex; gap:10px; flex-wrap:wrap; align-items:flex-end; }}
  .pw-form > div {{ flex:1; min-width:160px; }}
  .pw-form label {{ font-size:13px; margin-bottom:3px; display:block; }}
  .pw-form input {{ margin-bottom:0; }}
</style>
</head>
<body>

<div class="topbar">
  <h1>SmartCheck 服务管理</h1>
  <a href="/admin/logout" class="logout-btn">退出登录</a>
</div>

<!-- 统计 -->
<div class="card">
        <h2>设备认证统计</h2>
    <div style="margin-bottom:6px"><strong>MAC 白名单</strong></div>
    <span class="stat">总计：<strong>{mac_total}</strong></span>
        <span class="stat">已验证：<strong style="color:#dc2626">{mac_used}</strong></span>
        <span class="stat">未验证：<strong style="color:#16a34a">{mac_unused}</strong></span>
    <div style="margin:14px 0 6px"><strong>激活码</strong></div>
    <span class="stat">总计：<strong>{code_total}</strong></span>
        <span class="stat">已使用：<strong style="color:#dc2626">{code_used}</strong></span>
        <span class="stat">未使用：<strong style="color:#16a34a">{code_unused}</strong></span>
</div>

<!-- 发布新版本 -->
<div class="card">
  <h2>发布新版本</h2>
  <p style="margin:0 0 12px;color:#6b7280">当前最新：<strong>{cur_ver_text}</strong></p>

  <div id="drop-zone" onclick="document.getElementById('apk-file').click()">
    <p style="margin:0;font-size:15px">
      拖拽 <strong>.apk</strong> 文件到此处，或 <label for="apk-file">点击选择文件</label>
    </p>
    <input type="file" id="apk-file" accept=".apk"
           style="display:none" onchange="onFileSelected(this.files[0])">
  </div>
  <div id="file-info"></div>

  <label>versionCode（整数，需大于 {latest_ver["version_code"] if latest_ver else 0}，建议 {next_vc}）</label>
  <input type="number" id="version_code" placeholder="{next_vc}" min="1">

  <label>版本名称（如 1.0.7）</label>
  <input type="text" id="version_name" placeholder="1.0.7">

  <label>更新说明（可选）</label>
  <textarea id="release_notes" rows="3" placeholder="• 修复xxx问题&#10;• 新增xxx功能"></textarea>

  <button class="btn" id="upload-btn" onclick="doUpload()">发布</button>
  <div id="up-progress"></div>
</div>

<!-- 版本历史 -->
<div class="card">
  <h2>版本历史</h2>
  <table>
    <tr><th>versionCode</th><th>版本名</th><th>APK 地址</th><th>更新说明</th><th>发布时间</th></tr>
    {ver_rows if ver_rows else '<tr><td colspan="5" style="text-align:center;color:#9ca3af">暂无记录</td></tr>'}
  </table>
</div>

<!-- 添加认证数据 -->
<div class="card">
        <h2>添加认证数据</h2>
  <form method="POST" action="/admin/codes/add">
                <label>MAC 地址（每行一个，或逗号分隔）</label>
        <textarea name="macs" rows="4" placeholder="AA:BB:CC:DD:EE:FF&#10;11:22:33:44:55:66&#10;..."></textarea>
                <label>激活码（每行一个，或逗号分隔）</label>
                <textarea name="codes" rows="4" placeholder="ABCD-1234&#10;QWER_5678&#10;..."></textarea>
    <input type="submit" value="添加">
  </form>
</div>

<!-- 修改密码 -->
<div class="card">
  <h2>修改管理密码</h2>
  <form method="POST" action="/admin/change_password">
    <div class="pw-form">
      <div>
        <label>原密码</label>
        <input type="password" name="old_password" placeholder="原密码">
      </div>
      <div>
        <label>新密码（≥4位）</label>
        <input type="password" name="new_password" placeholder="新密码">
      </div>
      <div>
        <label>确认新密码</label>
        <input type="password" name="new_password2" placeholder="再次输入">
      </div>
      <div style="flex:none">
        <label>&nbsp;</label>
        <input type="submit" value="修改" style="padding:7px 18px">
      </div>
    </div>
  </form>
  {pw_error_html}
  <p style="color:#6b7280;font-size:12px;margin:8px 0 0">
    ⚠ 修改密码后所有已登录会话将立即失效，需重新登录。
  </p>
</div>

<!-- MAC 白名单列表 -->
<div class="card">
        <h2>MAC 白名单列表（共 {mac_total} 个）</h2>
  <table>
        <tr><th>MAC 地址</th><th>状态 / 验证时间</th><th>操作</th></tr>
                {mac_table_rows if mac_table_rows else '<tr><td colspan="3" style="text-align:center;color:#9ca3af">暂无数据</td></tr>'}
    </table>
</div>

<!-- 激活码列表 -->
<div class="card">
        <h2>激活码列表（共 {code_total} 个）</h2>
    <table>
                <tr><th>激活码</th><th>状态 / 绑定信息</th><th>操作</th></tr>
                {code_table_rows if code_table_rows else '<tr><td colspan="3" style="text-align:center;color:#9ca3af">暂无数据</td></tr>'}
  </table>
</div>

<script>
const zone = document.getElementById('drop-zone');
zone.addEventListener('dragover',  e => {{ e.preventDefault(); zone.classList.add('dragover'); }});
zone.addEventListener('dragleave', () => zone.classList.remove('dragover'));
zone.addEventListener('drop', e => {{
  e.preventDefault(); zone.classList.remove('dragover');
  if (e.dataTransfer.files.length) onFileSelected(e.dataTransfer.files[0]);
}});

function onFileSelected(file) {{
  if (!file) return;
  try {{
    const dt = new DataTransfer();
    dt.items.add(file);
    document.getElementById('apk-file').files = dt.files;
  }} catch(e) {{}}
  const sizeMB = (file.size / 1024 / 1024).toFixed(1);
  document.getElementById('file-info').innerHTML =
    `<span style="color:#16a34a">✓ 已选择：${{file.name}} (${{sizeMB}} MB)</span>`;
  document.getElementById('up-progress').innerHTML = '';
  const m = file.name.match(/(\\d+\\.\\d+\\.\\d+)/);
  if (m && !document.getElementById('version_name').value)
    document.getElementById('version_name').value = m[1];
}}

function doUpload() {{
  const fileInput   = document.getElementById('apk-file');
  const versionCode = document.getElementById('version_code').value.trim();
  const versionName = document.getElementById('version_name').value.trim();
  const notes       = document.getElementById('release_notes').value.trim();
  const prog        = document.getElementById('up-progress');
  const btn         = document.getElementById('upload-btn');

  if (!fileInput.files.length) {{ alert('请先选择 APK 文件'); return; }}
  if (!versionCode)            {{ alert('请填写 versionCode'); return; }}
  if (!versionName)            {{ alert('请填写版本名称'); return; }}

  // 禁用按钮，显示上传中
  btn.disabled = true;
  btn.innerText = '上传中...';
  prog.innerHTML = '<span style="color:#16a34a">⏳ 正在上传 APK，请稍候...</span>';

  const fd = new FormData();
  fd.append('apk_file',      fileInput.files[0]);
  fd.append('version_code',  versionCode);
  fd.append('version_name',  versionName);
  fd.append('release_notes', notes);

  const xhr = new XMLHttpRequest();
  xhr.upload.onprogress = e => {{
    if (e.lengthComputable) {{
      const pct = Math.round(e.loaded / e.total * 100);
      prog.innerHTML = `<progress value="${{pct}}" max="100"></progress>
                        <span style="margin-left:8px">${{pct}}% 上传中...</span>`;
    }}
  }};
  xhr.onload = () => {{
    btn.disabled = false;
    btn.innerText = '发布';
    try {{
      const res = JSON.parse(xhr.responseText);
      if (res.code === 0) {{
        prog.innerHTML = `<span style="color:#16a34a;font-weight:bold">
          ✓ 发布成功！APK 地址：${{res.apk_url}}</span>`;
        setTimeout(() => location.reload(), 2000);
      }} else {{
        prog.innerHTML = `<span style="color:#dc2626">✗ 失败：${{res.message}}</span>`;
      }}
    }} catch(e) {{
      prog.innerHTML = `<span style="color:#dc2626">✗ 服务器错误(${{xhr.status}}): ${{e.message}}</span>`;
    }}
  }};
  xhr.onerror = () => {{
    btn.disabled = false;
    btn.innerText = '发布';
    prog.innerHTML = '<span style="color:#dc2626">✗ 网络错误，请检查连接</span>';
  }};
  xhr.open('POST', '/admin/apk/upload');
  xhr.send(fd);
}}
</script>
</body>
</html>"""
        self._write_html(html)

    # ── 工具方法 ──────────────────────────────

    def _read_body(self, max_len: int = _MAX_BODY_BYTES_DEFAULT):
        raw = self._read_body_bytes(max_len=max_len)
        if raw is None:
            return None
        return raw.decode('utf-8', errors='replace')

    def _read_body_bytes(self, max_len: int = _MAX_BODY_BYTES_DEFAULT):
        try:
            length = int(self.headers.get('Content-Length', 0))
        except Exception:
            length = 0

        if length <= 0:
            return b''

        if length > max_len:
            self.send_error(413, "Payload Too Large")
            return None

        try:
            return self.rfile.read(length)
        except socket.timeout:
            self.send_error(408, "Request Timeout")
            return None

    def _send_json(self, data, status=200):
        body = json.dumps(data, ensure_ascii=False).encode('utf-8')
        self.send_response(status)
        self.send_header('Content-Type', 'application/json; charset=utf-8')
        self.send_header('Content-Length', len(body))
        self.end_headers()
        self.wfile.write(body)

    def _write_html(self, html: str):
        body = html.encode('utf-8')
        self.send_response(200)
        self.send_header('Content-Type', 'text/html; charset=utf-8')
        self.send_header('Content-Length', len(body))
        self.end_headers()
        self.wfile.write(body)

    def _redirect(self, location):
        self.send_response(302)
        self.send_header('Location', location)
        self.end_headers()

    def log_message(self, fmt, *args):
        pass  # 关闭默认访问日志


# ────────────────────────────────────────────
# 启动
# ────────────────────────────────────────────

if __name__ == '__main__':
    init_db()
    port   = 8080
    server = ThreadingHTTPServer(('0.0.0.0', port), Handler)
    print('\n' + '=' * 50)
    print('SmartCheck 授权 + 版本管理服务器')
    print('=' * 50)
    print(f'端口        : {port}')
    print(f'数据库      : {DB_PATH}')
    print(f'APK 目录    : {UPLOADS_DIR}')
    print(f'管理页面    : http://localhost:{port}/')
    print(f'授权接口    : POST http://localhost:{port}/api/device/activate')
    print(f'版本检查    : GET  http://localhost:{port}/api/app/version/latest')
    print(f'版本历史    : GET  http://localhost:{port}/api/app/version/history')
    print('=' * 50 + '\n')
    server.serve_forever()
