#!/usr/bin/env python3
"""One-shot probe: token + folder access + tiny write/delete on launcher-dev."""

from __future__ import annotations

import json
import sys
import tempfile
import urllib.error
import urllib.parse
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

from yandex_disk import (  # noqa: E402
    API_BASE,
    YandexDiskError,
    _request,
    load_local_properties,
    upload_file,
)


def main() -> int:
    root = Path(__file__).resolve().parent.parent
    props = load_local_properties(root)
    token = props.get("yandex.disk.oauth", "").strip()
    print("token_present:", bool(token), "len:", len(token))
    if not token:
        print("FAIL: yandex.disk.oauth empty")
        return 1

    try:
        status, body = _request("GET", f"{API_BASE}/", token=token)
        info = json.loads(body)
        print(
            "disk_ok:",
            status,
            "total_gb:",
            round(info.get("total_space", 0) / 1e9, 2),
            "used_gb:",
            round(info.get("used_space", 0) / 1e9, 2),
        )
    except YandexDiskError as error:
        print("disk_info_FAIL:", error)
        return 2

    for path in (
        props.get("yandex.disk.launcher.devPath", "/launcher-dev").strip() or "/launcher-dev",
        props.get("yandex.disk.launcher.releasePath", "/launcher-release").strip()
        or "/launcher-release",
    ):
        if not path.startswith("/"):
            path = f"/{path}"
        query = urllib.parse.urlencode({"path": path})
        try:
            status, body = _request("GET", f"{API_BASE}/resources?{query}", token=token)
            meta = json.loads(body)
            print(
                f"folder_ok {path}:",
                "type=" + str(meta.get("type")),
                "name=" + str(meta.get("name")),
                "public_url=" + str(meta.get("public_url") or "-"),
            )
        except YandexDiskError as error:
            print(f"folder_MISS {path}:", str(error)[:240])

    probe = Path(tempfile.gettempdir()) / "tbox_ota_write_probe.txt"
    probe.write_text("ota-write-probe", encoding="utf-8")
    dev_path = props.get("yandex.disk.launcher.devPath", "/launcher-dev").strip() or "/launcher-dev"
    if not dev_path.startswith("/"):
        dev_path = f"/{dev_path}"
    remote = f"{dev_path.rstrip('/')}/tbox_ota_write_probe.txt"
    try:
        upload_file(token, probe, remote, overwrite=True)
        print("write_ok:", remote)
        query = urllib.parse.urlencode({"path": remote, "permanently": "true"})
        _request("DELETE", f"{API_BASE}/resources?{query}", token=token)
        print("delete_ok: probe removed")
    except (YandexDiskError, OSError, urllib.error.URLError) as error:
        print("write_FAIL:", error)
        return 3

    print("RESULT: write access to", dev_path, "works")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
