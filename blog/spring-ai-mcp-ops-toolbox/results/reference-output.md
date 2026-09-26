# Reference output

Captured from a live run: `OPS_MCP_API_KEY=dev-key java -jar build/libs/*.jar`, then `./scripts/traffic.sh 300` (sent 300 requests: 275 ok, 25 failed),
then raw JSON-RPC over curl. Stateless mode means no `initialize` handshake is needed for a one-off call.

```bash
curl -s localhost:8080/mcp -H "Authorization: Bearer dev-key" \
  -H "Content-Type: application/json" -H "Accept: application/json, text/event-stream" \
  -d '{"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"http_traffic_summary","arguments":{}}}'
```

## `get_health` `{}`

```json
{
  "components": {
    "diskSpace": {
      "details": {
        "total": 270553174016,
        "free": 28296028160,
        "threshold": 10485760,
        "path": "/home/user/wt/mcp/blog/spring-ai-mcp-ops-toolbox/.",
        "exists": true
      },
      "status": "UP"
    },
    "livenessState": {
      "status": "UP"
    },
    "ping": {
      "status": "UP"
    },
    "readinessState": {
      "status": "UP"
    },
    "ssl": {
      "details": {
        "expiringChains": [],
        "invalidChains": [],
        "validChains": []
      },
      "status": "UP"
    }
  },
  "groups": [
    "liveness",
    "readiness"
  ],
  "status": "UP"
}
```

## `http_traffic_summary` `{}`

```json
[
  {
    "method": "GET",
    "uri": "/api/orders/{id}",
    "requests": 300,
    "serverErrors": 18,
    "clientErrors": 7,
    "meanMs": 33.2,
    "maxMs": 1097.5
  }
]
```

## `recent_logs` `{"minLevel":"ERROR","limit":1}`

```json
[
  {
    "timestamp": "2026-09-25T07:43:22.564Z",
    "level": "ERROR",
    "logger": "com.stevenpg.opsmcp.demo.OrderController",
    "thread": "http-nio-8080-exec-1",
    "message": "Order 138 lookup failed: inventory-service did not respond",
    "error": "java.lang.IllegalStateException: inventory-service unavailable\n    at com.stevenpg.opsmcp.demo.OrderController.order(OrderController.java:46)\n    at org.springframework.web.method.support.InvocableHandlerMethod.doInvoke(InvocableHandlerMethod.java:252)\n    at org.springframework.web.method.support.InvocableHandlerMethod.invokeForRequest(InvocableHandlerMethod.java:184)\n    at org.springframework.web.servlet.mvc.method.annotation.ServletInvocableHandlerMethod.invokeAndHandle(ServletInvocableHandlerMethod.java:117)\nCaused by: java.net.SocketTimeoutException: Read timed out after 2000ms\n    at com.stevenpg.opsmcp.demo.OrderController.order(OrderController.java:45)\n    at org.springframework.web.method.support.InvocableHandlerMethod.doInvoke(InvocableHandlerMethod.java:252)\n    at org.springframework.web.method.support.InvocableHandlerMethod.invokeForRequest(InvocableHandlerMethod.java:184)\n    at org.springframework.web.servlet.mvc.method.annotation.ServletInvocableHandlerMethod.invokeAndHandle(ServletInvocableHandlerMethod.java:117)"
  }
]
```

## `set_log_level_temporarily` `{"logger":"com.stevenpg.opsmcp.demo","level":"DEBUG","ttlMinutes":5}`

```json
{
  "logger": "com.stevenpg.opsmcp.demo",
  "previousLevel": null,
  "newLevel": "DEBUG",
  "revertsAt": "2026-09-25T07:48:24.134393159Z"
}
```

## `set_log_level_temporarily` `{"logger":"ROOT","level":"TRACE"}`

Returned with `"isError": true`. The message appears twice because Spring AI 2.0.1 prints the tool-execution
wrapper's message and then its cause's, which here are the same text.

```text
Logger 'ROOT' is not under an allow-listed prefix [com.stevenpg, org.springframework.web]
Logger 'ROOT' is not under an allow-listed prefix [com.stevenpg, org.springframework.web]
```

## `jvm_summary` `{}`

```json
{
  "javaVersion": "25.0.4.1+1-LTS",
  "uptime": "PT16S",
  "availableProcessors": 4,
  "processCpuLoad": 0.0,
  "heapUsedMiB": 48,
  "heapCommittedMiB": 68,
  "heapMaxMiB": 3422,
  "nonHeapUsedMiB": 66,
  "liveThreads": 23,
  "peakThreads": 23,
  "garbageCollectors": {
    "G1 Young Generation": "10 collections, 84 ms",
    "G1 Concurrent GC": "4 collections, 10 ms",
    "G1 Old Generation": "0 collections, 0 ms"
  }
}
```

## `thread_summary` `{"limit":3}`

```json
{
  "total": 23,
  "byState": {
    "RUNNABLE": 7,
    "WAITING": 11,
    "TIMED_WAITING": 5
  },
  "largestPools": {
    "http-nio-8080-exec-": 10,
    "Catalina-utility-": 2,
    "Reference Handler": 1,
    "Finalizer": 1,
    "Signal Dispatcher": 1,
    "Notification Thread": 1,
    "Common-Cleaner": 1,
    "Cleaner-": 1
  },
  "deadlocked": [],
  "blocked": []
}
```

## Without the key

```
HTTP/1.1 401 
WWW-Authenticate: Bearer
```
