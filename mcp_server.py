#!/usr/bin/env python3
"""
Apex 3D Studio - Model Context Protocol (MCP) Server
Universal Bridge connecting LLMs (Antigravity, Claude Desktop, Cursor, Gemini)
to the running Apex 3D Android app via USB (ADB) or Wireless (Wi-Fi).
Supports ultra-fast dynamic probing and auto-failover between USB cable and Wi-Fi LAN.
"""

import sys
import json
import os
import argparse
import subprocess
import socket
import urllib.request
import urllib.error

DEFAULT_PORT = 8765

class Apex3dMcpServer:
    def __init__(self, mode="auto", host=None, port=DEFAULT_PORT):
        self.mode = mode
        self.port = port
        self.custom_host = host or os.environ.get("APEX3D_HOST")
        self.active_host = None
        self._init_connection()

    def _get_adb_path(self):
        paths = ["C:\\adb\\adb.exe", "adb"]
        for p in paths:
            try:
                res = subprocess.run([p, "version"], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, stdin=subprocess.DEVNULL, timeout=1.5)
                if res.returncode == 0:
                    return p
            except Exception:
                continue
        return "adb"

    def _setup_adb_forward(self):
        adb = self._get_adb_path()
        try:
            subprocess.run([adb, "forward", f"tcp:{self.port}", f"tcp:{self.port}"],
                           stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, stdin=subprocess.DEVNULL, timeout=2.0)
        except Exception:
            pass

    def _probe_tcp(self, host, timeout=0.35):
        """Fast TCP probe to test connection availability in < 350ms."""
        try:
            with socket.create_connection((host, self.port), timeout=timeout):
                return True
        except Exception:
            return False

    def _init_connection(self):
        """Determine initial connection target."""
        if self.mode == "usb":
            self._setup_adb_forward()
            self.active_host = "127.0.0.1"
        elif self.mode == "wireless":
            self.active_host = self.custom_host or "192.168.8.101"
        else: # auto
            # Try USB forward first
            self._setup_adb_forward()
            if self._probe_tcp("127.0.0.1"):
                self.active_host = "127.0.0.1"
            elif self.custom_host and self._probe_tcp(self.custom_host):
                self.active_host = self.custom_host
            elif self._probe_tcp("192.168.8.101"):
                self.active_host = "192.168.8.101"
            else:
                self.active_host = self.custom_host or "192.168.8.101"

    def get_working_host(self):
        """Verifies active host; if offline, auto-switches."""
        if self._probe_tcp(self.active_host):
            return self.active_host

        candidates = ["127.0.0.1"]
        if self.custom_host:
            candidates.append(self.custom_host)
        candidates.append("192.168.8.101")

        for c in candidates:
            if c != self.active_host:
                if c == "127.0.0.1":
                    self._setup_adb_forward()
                if self._probe_tcp(c):
                    self.active_host = c
                    sys.stderr.write(f"[Apex3D MCP] Auto-switched to active connection: http://{self.active_host}:{self.port}\n")
                    sys.stderr.flush()
                    return self.active_host

        return self.active_host

    def forward_to_android(self, req_data):
        """Sends JSON-RPC payload to Android app /mcp endpoint."""
        host = self.get_working_host()
        url = f"http://{host}:{self.port}/mcp"
        body = json.dumps(req_data).encode("utf-8")
        req = urllib.request.Request(
            url,
            data=body,
            headers={"Content-Type": "application/json; charset=utf-8"}
        )
        try:
            with urllib.request.urlopen(req, timeout=10) as resp:
                data = resp.read().decode("utf-8")
                return json.loads(data)
        except Exception as e:
            req_id = req_data.get("id")
            return {
                "jsonrpc": "2.0",
                "id": req_id,
                "error": {
                    "code": -32000,
                    "message": f"Connection failed to Apex3D at {url}. (Detail: {e})"
                }
            }

    def run_stdio(self):
        """Main loop: reads line-delimited JSON-RPC from STDIN and writes to STDOUT."""
        sys.stderr.write(f"[Apex3D MCP Server] Initialized. Mode='{self.mode}', Active Target=http://{self.active_host}:{self.port}\n")
        sys.stderr.flush()

        while True:
            try:
                line = sys.stdin.readline()
                if not line:
                    break
                line = line.strip()
                if not line:
                    continue

                req = json.loads(line)
                response = self.forward_to_android(req)
                sys.stdout.write(json.dumps(response) + "\n")
                sys.stdout.flush()
            except json.JSONDecodeError as e:
                err_resp = {
                    "jsonrpc": "2.0",
                    "id": None,
                    "error": {"code": -32700, "message": f"Parse error: {e}"}
                }
                sys.stdout.write(json.dumps(err_resp) + "\n")
                sys.stdout.flush()
            except Exception as e:
                sys.stderr.write(f"[Apex3D MCP Server] Error: {e}\n")
                sys.stderr.flush()

def main():
    parser = argparse.ArgumentParser(description="Apex 3D MCP Server (STDIO)")
    parser.add_argument("--mode", choices=["auto", "usb", "wireless"], default="auto",
                        help="Connection mode: 'auto' (detects USB ADB or Wi-Fi), 'usb' (forces ADB), 'wireless' (forces Wi-Fi)")
    parser.add_argument("--host", default=None, help="Device IP for wireless mode (e.g. 192.168.8.101)")
    parser.add_argument("--port", type=int, default=DEFAULT_PORT, help=f"Target port (default: {DEFAULT_PORT})")
    args = parser.parse_args()

    server = Apex3dMcpServer(mode=args.mode, host=args.host, port=args.port)
    server.run_stdio()

if __name__ == "__main__":
    main()
