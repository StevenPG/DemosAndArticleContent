# A minimal HTTP server simulating a downstream service.
# The 10ms sleep is intentional — without a small delay, all calls complete
# near-instantly and we can't see the concurrency model working.
#
# Run with: uv run server.py
import json, sys, time
from http.server import ThreadingHTTPServer, BaseHTTPRequestHandler

class EchoHandler(BaseHTTPRequestHandler):
    def do_GET(self):
        try:
            time.sleep(0.010)  # 10ms simulated downstream latency
            body = json.dumps({
                "status": "ok",
                "timestamp_ms": int(time.time() * 1000)
            }).encode()
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.send_header("Content-Length", str(len(body)))
            # Disable keep-alive. Python's ThreadingHTTPServer doesn't manage
            # persistent connections reliably — closing after each response avoids
            # a race where a client reuses a connection Python has already torn down.
            # Java's JDK HttpClient throws EOFException on a stale connection;
            # Go's transport silently retries, so this matters most for Spring.
            self.send_header("Connection", "close")
            self.end_headers()
            self.wfile.write(body)
        except Exception as e:
            # Print the full error so we can see what's going wrong.
            # BaseHTTPRequestHandler silently swallows exceptions and returns 500
            # without any output if we don't catch them here ourselves.
            print(f"[ERROR] {self.path} — {e}", file=sys.stderr)
            raise

    # Override log_request (not log_message) so we only suppress the normal
    # per-request access lines (GET /echo 200) without also silencing error output.
    # log_message is still active for anything that isn't a routine request log.
    def log_request(self, code='-', size='-'): pass

if __name__ == "__main__":
    # ThreadingHTTPServer handles each connection in its own thread.
    # The default HTTPServer is single-threaded — with 50 concurrent callers
    # its TCP accept backlog fills up and callers get connection refused errors.
    server = ThreadingHTTPServer(("localhost", 9000), EchoHandler)
    print("Echo server ready on :9000")
    server.serve_forever()
