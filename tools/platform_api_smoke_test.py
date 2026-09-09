#!/usr/bin/env python3
"""Smoke-test the third-party platform API implemented for SmartCheck."""

from __future__ import annotations

import argparse
import json
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path
from typing import Any


class SmokeTestFailure(RuntimeError):
    pass


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Verify the SmartCheck third-party platform API against a live server."
    )
    parser.add_argument("--base-url", required=True, help="Platform root URL, without /api/device")
    parser.add_argument("--api-key", required=True, help="Device API key")
    parser.add_argument("--employee-id", help="Also verify the single-employee endpoint")
    parser.add_argument("--file-id", help="Also verify the employee image endpoint")
    parser.add_argument(
        "--morning-check-json",
        type=Path,
        help="POST this JSON fixture to the morning-check endpoint",
    )
    parser.add_argument(
        "--employee-changes-json",
        type=Path,
        help="POST this JSON fixture to the employee changes endpoint",
    )
    parser.add_argument("--timeout", type=float, default=60.0, help="Request timeout in seconds")
    parser.add_argument(
        "--allow-http",
        action="store_true",
        help="Allow HTTP for non-production development checks",
    )
    return parser.parse_args()


def load_json(path: Path) -> dict[str, Any]:
    try:
        value = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        raise SmokeTestFailure(f"Cannot read JSON fixture {path}: {exc}") from exc
    if not isinstance(value, dict):
        raise SmokeTestFailure(f"JSON fixture must contain an object: {path}")
    return value


def request(
    base_url: str,
    api_key: str,
    method: str,
    path: str,
    timeout: float,
    payload: dict[str, Any] | None = None,
) -> tuple[int, str, bytes]:
    body = None
    headers = {"api-key": api_key, "Accept": "application/json"}
    if payload is not None:
        body = json.dumps(payload, ensure_ascii=False, separators=(",", ":")).encode("utf-8")
        headers["Content-Type"] = "application/json"

    req = urllib.request.Request(
        url=f"{base_url}{path}",
        data=body,
        headers=headers,
        method=method,
    )
    started = time.monotonic()
    try:
        with urllib.request.urlopen(req, timeout=timeout) as response:
            content = response.read()
            elapsed_ms = int((time.monotonic() - started) * 1000)
            print(f"PASS {method:4} {path} -> HTTP {response.status} ({elapsed_ms} ms)")
            return response.status, response.headers.get("Content-Type", ""), content
    except urllib.error.HTTPError as exc:
        content = exc.read()
        text = content.decode("utf-8", errors="replace")[:2000]
        raise SmokeTestFailure(f"{method} {path} returned HTTP {exc.code}: {text}") from exc
    except urllib.error.URLError as exc:
        raise SmokeTestFailure(f"{method} {path} failed: {exc.reason}") from exc


def require_json_response(status: int, content_type: str, content: bytes, label: str) -> dict[str, Any]:
    if status < 200 or status >= 300:
        raise SmokeTestFailure(f"{label} returned non-2xx HTTP status {status}")
    try:
        value = json.loads(content.decode("utf-8"))
    except (UnicodeDecodeError, json.JSONDecodeError) as exc:
        raise SmokeTestFailure(f"{label} did not return valid UTF-8 JSON ({content_type})") from exc
    if not isinstance(value, dict):
        raise SmokeTestFailure(f"{label} response must be a JSON object")
    if value.get("code") != 200:
        message = value.get("message", value.get("msg", ""))
        raise SmokeTestFailure(f"{label} returned business code {value.get('code')}: {message}")
    return value


def require_data_keys(response: dict[str, Any], label: str, keys: set[str]) -> dict[str, Any]:
    data = response.get("data")
    if not isinstance(data, dict):
        raise SmokeTestFailure(f"{label} response.data must be an object")
    missing = sorted(keys - data.keys())
    if missing:
        raise SmokeTestFailure(f"{label} response.data missing keys: {', '.join(missing)}")
    return data


def main() -> int:
    args = parse_args()
    base_url = args.base_url.rstrip("/")
    parsed = urllib.parse.urlparse(base_url)
    if parsed.scheme not in {"http", "https"} or not parsed.netloc:
        raise SmokeTestFailure("--base-url must be an absolute HTTP(S) URL")
    if parsed.scheme != "https" and not args.allow_http:
        raise SmokeTestFailure("HTTPS is required; pass --allow-http only for development")
    if base_url.endswith("/api/device"):
        raise SmokeTestFailure("--base-url must not include /api/device")

    status, content_type, content = request(
        base_url, args.api_key, "POST", "/api/device/refresh", args.timeout
    )
    require_json_response(status, content_type, content, "heartbeat")

    status, content_type, content = request(
        base_url,
        args.api_key,
        "GET",
        "/api/device/employees/changes?after_cursor=0&limit=1",
        args.timeout,
    )
    pull = require_json_response(status, content_type, content, "employee changes")
    require_data_keys(
        pull,
        "employee changes",
        {"changes", "next_cursor", "has_more", "server_time"},
    )

    status, content_type, content = request(
        base_url, args.api_key, "GET", "/api/device/employees/snapshot", args.timeout
    )
    snapshot = require_json_response(status, content_type, content, "employee snapshot")
    require_data_keys(snapshot, "employee snapshot", {"employees", "total", "cursor", "server_time"})

    if args.employee_id:
        employee_id = urllib.parse.quote(args.employee_id, safe="")
        status, content_type, content = request(
            base_url,
            args.api_key,
            "GET",
            f"/api/device/employees/{employee_id}",
            args.timeout,
        )
        employee = require_json_response(status, content_type, content, "employee detail")
        require_data_keys(employee, "employee detail", {"employee_id", "deleted", "version", "employee"})

    if args.file_id:
        file_id = urllib.parse.quote(args.file_id, safe="")
        status, content_type, content = request(
            base_url,
            args.api_key,
            "GET",
            f"/api/device/employees/images/{file_id}",
            args.timeout,
        )
        if "json" in content_type.lower():
            raise SmokeTestFailure("employee image endpoint returned JSON instead of image bytes")
        if not content:
            raise SmokeTestFailure("employee image endpoint returned an empty body")

    if args.morning_check_json:
        status, content_type, content = request(
            base_url,
            args.api_key,
            "POST",
            "/api/device/morning-check/upload",
            args.timeout,
            load_json(args.morning_check_json),
        )
        morning = require_json_response(status, content_type, content, "morning check upload")
        data = require_data_keys(morning, "morning check upload", {"recordIds"})
        if not isinstance(data["recordIds"], list) or len(data["recordIds"]) != 1:
            raise SmokeTestFailure("morning check response.data.recordIds must contain one item")

    if args.employee_changes_json:
        status, content_type, content = request(
            base_url,
            args.api_key,
            "POST",
            "/api/device/employees/changes",
            args.timeout,
            load_json(args.employee_changes_json),
        )
        changes = require_json_response(status, content_type, content, "employee changes upload")
        require_data_keys(
            changes,
            "employee changes upload",
            {"accepted", "duplicates", "conflicts", "server_cursor", "results"},
        )

    print("All requested platform API smoke checks passed.")
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except SmokeTestFailure as exc:
        print(f"FAIL {exc}", file=sys.stderr)
        sys.exit(1)
