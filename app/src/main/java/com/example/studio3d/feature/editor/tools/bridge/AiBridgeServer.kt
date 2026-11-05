package com.example.studio3d.feature.editor.tools.bridge

import android.util.Log
import com.example.studio3d.feature.editor.screen.StudioViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket

/**
 * Embedded lightweight HTTP Bridge Server berbasis Kotlin Coroutines murni.
 * Mendengarkan di 0.0.0.0:8765 untuk kontrol universal nirkabel (Wi-Fi) atau kabel (ADB Forward).
 */
class AiBridgeServer(val port: Int = 8765) {

    private val tag = "AiBridgeServer"
    private var serverSocket: ServerSocket? = null
    private var serverJob: Job? = null
    private var dispatcher: AiCommandDispatcher? = null

    var isRunning: Boolean = false
        private set

    var localIpAddress: String? = null

    fun start(viewModel: StudioViewModel, scope: CoroutineScope) {
        if (isRunning) return
        dispatcher = AiCommandDispatcher(viewModel)
        localIpAddress = getActiveLocalIpAddress()

        serverJob = scope.launch(Dispatchers.IO) {
            try {
                val bindAddr = InetAddress.getByName("0.0.0.0")
                serverSocket = ServerSocket(port, 50, bindAddr)
                isRunning = true
                Log.i(tag, "AI Bridge Server started on 0.0.0.0:$port (Local IP: $localIpAddress)")

                while (isActive && serverSocket != null && !serverSocket!!.isClosed) {
                    try {
                        val clientSocket = serverSocket!!.accept()
                        launch(Dispatchers.IO) {
                            handleClient(clientSocket)
                        }
                    } catch (e: Exception) {
                        if (!isActive) break
                    }
                }
            } catch (e: Exception) {
                Log.e(tag, "Error running AI Bridge Server: ${e.message}", e)
            } finally {
                isRunning = false
            }
        }
    }

    fun stop() {
        isRunning = false
        try {
            serverSocket?.close()
        } catch (_: Exception) {}
        serverSocket = null
        serverJob?.cancel()
        serverJob = null
        dispatcher = null
    }

    private suspend fun handleClient(socket: Socket) {
        withContext(Dispatchers.IO) {
            try {
                socket.soTimeout = 8000
                val inStream = socket.getInputStream()
                val outStream = socket.getOutputStream()

                // Read HTTP request headers as raw bytes until double CRLF (\r\n\r\n) or (\n\n)
                val headerBytes = java.io.ByteArrayOutputStream()
                var p3 = 0
                var p2 = 0
                var p1 = 0
                var b: Int
                while (inStream.read().also { b = it } != -1) {
                    headerBytes.write(b)
                    if ((p3 == '\r'.code && p2 == '\n'.code && p1 == '\r'.code && b == '\n'.code) ||
                        (p1 == '\n'.code && b == '\n'.code)) {
                        break
                    }
                    p3 = p2
                    p2 = p1
                    p1 = b
                }

                val headerStr = headerBytes.toString("UTF-8")
                val lines = headerStr.split("\r\n", "\n")
                if (lines.isEmpty() || lines[0].isBlank()) return@withContext

                val tokens = lines[0].split(" ")
                if (tokens.size < 2) return@withContext

                val method = tokens[0].uppercase()
                val path = tokens[1]
                Log.d(tag, "Incoming HTTP request: $method $path")

                // Parse Content-Length header accurately
                var contentLength = 0
                for (line in lines) {
                    val l = line.lowercase()
                    if (l.startsWith("content-length:")) {
                        contentLength = line.substring(15).trim().toIntOrNull() ?: 0
                    }
                }

                // Read exact byte payload
                val body = if (contentLength > 0) {
                    val bodyBytes = ByteArray(contentLength)
                    var readTotal = 0
                    while (readTotal < contentLength) {
                        val r = inStream.read(bodyBytes, readTotal, contentLength - readTotal)
                        if (r == -1) break
                        readTotal += r
                    }
                    String(bodyBytes, 0, readTotal, Charsets.UTF_8)
                } else ""

                // Handle routing
                val (responseContent, contentType) = when {
                    method == "OPTIONS" -> "" to "text/plain; charset=utf-8"
                    method == "GET" && (path == "/mcp.py" || path == "/mcp_server.py" || path == "/download/mcp.py") -> {
                        getEmbeddedPythonScript(localIpAddress ?: "127.0.0.1", port) to "text/x-python; charset=utf-8"
                    }
                    (path == "/mcp" || path == "/rpc" || body.contains("\"jsonrpc\"")) && method == "POST" -> {
                        (dispatcher?.handleMcpRequest(body) ?: "{\"jsonrpc\":\"2.0\",\"error\":{\"code\":-32000,\"message\":\"Dispatcher unavailable\"}}") to "application/json; charset=utf-8"
                    }
                    method == "GET" && (path == "/mcp" || path == "/mcp/tools") -> {
                        val obj = org.json.JSONObject()
                        obj.put("status", "ok")
                        obj.put("tools", dispatcher?.getMcpToolsList() ?: org.json.JSONArray())
                        obj.toString(2) to "application/json; charset=utf-8"
                    }
                    method == "GET" && (path == "/" || path == "/api" || path == "/api/schema") -> {
                        (dispatcher?.getSchema() ?: "{\"error\":\"Dispatcher unavailable\"}") to "application/json; charset=utf-8"
                    }
                    method == "GET" && path == "/api/status" -> {
                        (dispatcher?.getStatus() ?: "{\"error\":\"Dispatcher unavailable\"}") to "application/json; charset=utf-8"
                    }
                    method == "GET" && path == "/api/scene" -> {
                        (dispatcher?.getScene() ?: "{\"error\":\"Dispatcher unavailable\"}") to "application/json; charset=utf-8"
                    }
                    method == "GET" && path == "/api/selection" -> {
                        (dispatcher?.getSelection() ?: "{\"error\":\"Dispatcher unavailable\"}") to "application/json; charset=utf-8"
                    }
                    method == "GET" && path == "/api/camera" -> {
                        (dispatcher?.getCamera() ?: "{\"error\":\"Dispatcher unavailable\"}") to "application/json; charset=utf-8"
                    }
                    method == "POST" && (path == "/api/command" || path == "/command") -> {
                        (dispatcher?.executeCommand(body) ?: "{\"error\":\"Dispatcher unavailable\"}") to "application/json; charset=utf-8"
                    }
                    method == "POST" && (path == "/api/pipeline" || path == "/pipeline") -> {
                        (dispatcher?.executePipeline(body) ?: "{\"error\":\"Dispatcher unavailable\"}") to "application/json; charset=utf-8"
                    }
                    else -> {
                        "{\"status\":\"error\",\"message\":\"Not found: $method $path. Use GET /api/schema for available endpoints.\"}" to "application/json; charset=utf-8"
                    }
                }

                // Write HTTP Response
                val bytes = responseContent.toByteArray(Charsets.UTF_8)
                val header = StringBuilder()
                header.append("HTTP/1.1 200 OK\r\n")
                header.append("Content-Type: ").append(contentType).append("\r\n")
                header.append("Content-Length: ").append(bytes.size).append("\r\n")
                header.append("Access-Control-Allow-Origin: *\r\n")
                header.append("Access-Control-Allow-Methods: GET, POST, OPTIONS\r\n")
                header.append("Access-Control-Allow-Headers: Content-Type\r\n")
                header.append("Connection: close\r\n\r\n")

                outStream.write(header.toString().toByteArray(Charsets.UTF_8))
                if (bytes.isNotEmpty()) {
                    outStream.write(bytes)
                }
                outStream.flush()
            } catch (e: Exception) {
                Log.e(tag, "handleClient exception: ${e.message}", e)
            } finally {
                try {
                    socket.close()
                } catch (_: Exception) {}
            }
        }
    }

    /**
     * Mendeteksi alamat IP lokal aktif (IPv4 non-loopback) perangkat Android secara dinamis.
     */
    fun getActiveLocalIpAddress(): String? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                if (networkInterface.isLoopback || !networkInterface.isUp) continue

                val addresses = networkInterface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val address = addresses.nextElement()
                    if (address is Inet4Address && !address.isLoopbackAddress) {
                        val host = address.hostAddress
                        // Prioritas interface wlan (Wi-Fi) atau rndis/hotspot
                        if (host != null && (host.startsWith("192.168.") || host.startsWith("10.") || host.startsWith("172."))) {
                            return host
                        }
                    }
                }
            }
        } catch (_: Exception) {}
        return null
    }

    /**
     * Menghasilkan skrip Python MCP Server turnkey lengkap untuk diunduh langsung oleh PC.
     */
    fun getEmbeddedPythonScript(defaultIp: String, port: Int): String {
        return """
#!/usr/bin/env python3
# Apex 3D Studio - Model Context Protocol (MCP) Server
# Auto-downloaded from Apex 3D Android App ($defaultIp:$port)
import sys, json, os, argparse, subprocess, socket, urllib.request

DEFAULT_PORT = $port
DEFAULT_DEVICE_IP = "$defaultIp"

class Apex3dMcpServer:
    def __init__(self, mode="auto", host=None, port=DEFAULT_PORT):
        self.mode = mode
        self.port = port
        self.custom_host = host or os.environ.get("APEX3D_HOST")
        self.active_host = None
        self._init_connection()

    def _get_adb_path(self):
        for p in ["C:\\adb\\adb.exe", "adb"]:
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
        try:
            with socket.create_connection((host, self.port), timeout=timeout):
                return True
        except Exception:
            return False

    def _init_connection(self):
        if self.mode == "usb":
            self._setup_adb_forward()
            self.active_host = "127.0.0.1"
        elif self.mode == "wireless":
            self.active_host = self.custom_host or DEFAULT_DEVICE_IP
        else:
            self._setup_adb_forward()
            if self._probe_tcp("127.0.0.1"):
                self.active_host = "127.0.0.1"
            elif self.custom_host and self._probe_tcp(self.custom_host):
                self.active_host = self.custom_host
            elif self._probe_tcp(DEFAULT_DEVICE_IP):
                self.active_host = DEFAULT_DEVICE_IP
            else:
                self.active_host = self.custom_host or DEFAULT_DEVICE_IP

    def get_working_host(self):
        if self._probe_tcp(self.active_host):
            return self.active_host
        candidates = ["127.0.0.1"]
        if self.custom_host:
            candidates.append(self.custom_host)
        candidates.append(DEFAULT_DEVICE_IP)
        for c in candidates:
            if c != self.active_host:
                if c == "127.0.0.1":
                    self._setup_adb_forward()
                if self._probe_tcp(c):
                    self.active_host = c
                    sys.stderr.write(f"[Apex3D MCP] Auto-switched target to: http://{self.active_host}:{self.port}\n")
                    sys.stderr.flush()
                    return self.active_host
        return self.active_host

    def forward_to_android(self, req_data):
        host = self.get_working_host()
        url = f"http://{host}:{self.port}/mcp"
        body = json.dumps(req_data).encode("utf-8")
        req = urllib.request.Request(url, data=body, headers={"Content-Type": "application/json; charset=utf-8"})
        try:
            with urllib.request.urlopen(req, timeout=10) as resp:
                return json.loads(resp.read().decode("utf-8"))
        except Exception as e:
            return {"jsonrpc": "2.0", "id": req_data.get("id"), "error": {"code": -32000, "message": f"Connection failed to Apex3D at {url}. (Detail: {e})"}}

    def run_stdio(self):
        sys.stderr.write(f"[Apex3D MCP Server] Running in '{self.mode}' mode, target=http://{self.active_host}:{self.port}\n")
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
            except Exception as e:
                sys.stderr.write(f"[Apex3D MCP Server] Error: {e}\n")
                sys.stderr.flush()

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Apex 3D MCP Server (STDIO)")
    parser.add_argument("--mode", choices=["auto", "usb", "wireless"], default="auto")
    parser.add_argument("--host", default=None)
    parser.add_argument("--port", type=int, default=DEFAULT_PORT)
    args = parser.parse_args()
    Apex3dMcpServer(mode=args.mode, host=args.host, port=args.port).run_stdio()
""".trimIndent()
    }
}
