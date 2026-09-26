# Spring AI 2.0 MCP Ops Toolbox

A Spring Boot 4.1 service that exposes its own operational state (health, metrics, HTTP traffic,
recent logs, JVM and thread state) as an **MCP server**, so an AI agent like Claude Code, Claude
Desktop or the MCP Inspector can triage the running instance directly.

Built with **Spring AI 2.0.1** (`@McpTool` annotations, stateless Streamable HTTP) and the **MCP Java
SDK 2.0**.

Accompanies the post **[Give Your Spring Boot Service an MCP Ops Interface with Spring AI 2.0](https://stevenpg.com/posts/spring-ai-2-mcp-server-ops-toolbox/)**.

## What it exposes

| Kind | Name | What it does | Read-only |
|---|---|---|---|
| tool | `get_health` | The actuator `HealthEndpoint`, with components | yes |
| tool | `list_metrics` | Micrometer meter names, optional prefix filter | yes |
| tool | `get_metric` | One meter's values per tag set, optional `key:value` filters | yes |
| tool | `http_traffic_summary` | Per-endpoint requests, 4xx/5xx, mean/max latency, busiest first | yes |
| tool | `recent_logs` | Last N log lines from an in-memory ring buffer, filterable, with stack summaries | yes |
| tool | `get_log_level` | A logger's configured/effective level and any pending revert | yes |
| tool | `set_log_level_temporarily` | Change a level on an allow-listed logger; **auto-reverts** after a TTL | **no** |
| tool | `jvm_summary` | Heap, CPU, threads, GC counts/time | yes |
| tool | `thread_summary` | Threads by state, largest pools, deadlocks, BLOCKED stacks | yes |
| resource | `ops://app/info` | App name, profiles, JVM, start time, PID | n/a |
| prompt | `triage` | A triage runbook: tools in the order an on-call engineer would use them | n/a |

Every tool carries MCP tool annotations (`readOnlyHint`, `idempotentHint`, `destructiveHint`,
`openWorldHint`). Clients use them to decide what to auto-approve.

There's also a deliberately flaky demo endpoint, `GET /api/orders/{id}`. It fails 5% of requests
with a logged `SocketTimeoutException` from a pretend `inventory-service` and makes another 5% slow,
so the tools have something to find.

## Run it

Requires JDK 25+.

```bash
./gradlew bootRun
# look for:  Using generated MCP API key: 3f9c...
# or choose your own:
OPS_MCP_API_KEY=dev-key ./gradlew bootRun
```

Generate some traffic so the tools have something to report:

```bash
./scripts/traffic.sh            # 300 requests at /api/orders/{id}, some fail, some are slow
```

### Connect Claude Code

```bash
claude mcp add --transport http ops-toolbox http://localhost:8080/mcp \
  --header "Authorization: Bearer dev-key"
```

Then ask it something like *"the orders API is flaky, what's going on?"* or run the `triage` prompt.

### Connect the MCP Inspector

```bash
npx @modelcontextprotocol/inspector
# Transport: Streamable HTTP, URL: http://localhost:8080/mcp
# Authentication: Bearer token = dev-key
```

## Tests

```bash
./gradlew test
```

`OpsMcpServerIT` starts the app on a random port and drives it with the official MCP Java SDK
client over Streamable HTTP, the same way Claude Code or the Inspector would. It covers:

- every tool is advertised, and exactly one of them is not read-only
- `get_health` returns `UP` with components
- after three failing `/api/orders` calls, `http_traffic_summary` shows 3 server errors and
  `recent_logs` finds the `SocketTimeoutException`
- `set_log_level_temporarily` works on an allow-listed logger, reports its revert time, and
  refuses `ROOT`
- the `ops://app/info` resource and the `triage` prompt resolve
- a client with the wrong key can't initialize

## Design notes

- **`STATELESS` protocol.** Every JSON-RPC call is self-contained, so any replica behind a plain
  load balancer can answer, with no sticky sessions. The cost is that the server can't push
  anything to the client: no sampling, elicitation or progress notifications. None of these tools
  need them.
- **Tools call the beans, not the HTTP actuator.** `HealthEndpoint`, `MeterRegistry` and
  `LoggingSystem` are used in-process. The actuator's web exposure stays at `health` only, and none of
  it has to be exposed for the MCP tools to work.
- **Responses are shaped for a context window.** `http_traffic_summary` returns one row per
  endpoint instead of the actuator's full timer JSON, and log errors carry the exception chain with
  4 frames per cause instead of the full stack.
- **The one write tool has guard rails.** It only touches loggers under
  `ops.mcp.writable-logger-prefixes` (not `ROOT`), and every change reverts itself after 15 minutes
  (60 max). On shutdown, pending changes revert immediately. An agent can't leave DEBUG on over a
  weekend.
- **No environment or config dump tool, on purpose.** It's the obvious next tool to write and the
  most likely to leak a secret into a transcript.
- **Authentication is a bearer token on `/mcp` only.** That's the minimum for a local or dev
  instance, compared in constant time. The Spring AI starters expose an *unauthenticated* endpoint
  by default. For anything shared, use Spring Security as an OAuth2 resource server per the MCP
  authorization spec.

## Layout

```
src/main/java/com/stevenpg/opsmcp/
  tools/        @McpTool / @McpResource / @McpPrompt beans
  logs/         Logback ring-buffer appender backing recent_logs
  security/     bearer-token filter on /mcp
  demo/         the flaky /api/orders endpoint
src/test/java/  OpsMcpServerIT: MCP SDK client end-to-end tests
scripts/        traffic.sh
```
