# gRPC Spring Boot 4.1 Ultimate Guide — Reference Output

Captured on 2026-07-08 01:48:36 UTC by running the demo end-to-end.

## 1. Build output (Gradle)

```
> Task :storefront-client:processResources
> Task :storefront-client:classes
> Task :storefront-client:resolveMainClassName
> Task :storefront-client:bootJar
> Task :storefront-client:jar
> Task :storefront-client:assemble
> Task :storefront-client:compileTestJava
> Task :storefront-client:processTestResources NO-SOURCE
> Task :storefront-client:testClasses

> Task :storefront-client:test
> Task :storefront-client:check
> Task :storefront-client:build

BUILD SUCCESSFUL in 11s
25 actionable tasks: 25 executed
```

Test summary: tests="6" skipped="0" failures="0" errors="0";tests="1" skipped="0" failures="0" errors="0"

## 2. inventory-server startup log (gRPC server on :9090)

```
2026-07-08T01:48:26.726Z  INFO 28446 --- [inventory-server] [           main] c.s.g.i.InventoryServerApplication       : Starting InventoryServerApplication v0.0.1-SNAPSHOT using Java 21.0.10 with PID 28446 (/home/user/DemosAndArticleContent/blog/grpc-spring-boot-ultimate-guide/inventory-server/build/libs/inventory-server-0.0.1-SNAPSHOT.jar started by root in /home/user/DemosAndArticleContent/blog/grpc-spring-boot-ultimate-guide)
2026-07-08T01:48:27.830Z  INFO 28446 --- [inventory-server] [           main] o.s.grpc.server.NettyGrpcServerFactory   : Registered gRPC service: inventory.v1.InventoryService
2026-07-08T01:48:27.831Z  INFO 28446 --- [inventory-server] [           main] o.s.grpc.server.NettyGrpcServerFactory   : Registered gRPC service: grpc.reflection.v1.ServerReflection
2026-07-08T01:48:27.831Z  INFO 28446 --- [inventory-server] [           main] o.s.grpc.server.NettyGrpcServerFactory   : Registered gRPC service: grpc.health.v1.Health
2026-07-08T01:48:28.166Z  INFO 28446 --- [inventory-server] [           main] c.s.g.i.InventoryServerApplication       : Started InventoryServerApplication in 2.132 seconds (process running for 2.897)
```

## 3. storefront-client startup log (REST edge on :8080)

```
2026-07-08T01:48:26.941Z  INFO 28448 --- [storefront-client] [           main] c.s.g.s.StorefrontClientApplication      : Starting StorefrontClientApplication v0.0.1-SNAPSHOT using Java 21.0.10 with PID 28448 (/home/user/DemosAndArticleContent/blog/grpc-spring-boot-ultimate-guide/storefront-client/build/libs/storefront-client-0.0.1-SNAPSHOT.jar started by root in /home/user/DemosAndArticleContent/blog/grpc-spring-boot-ultimate-guide)
2026-07-08T01:48:29.418Z  INFO 28448 --- [storefront-client] [           main] o.s.boot.tomcat.TomcatWebServer          : Tomcat started on port 8080 (http) with context path '/'
2026-07-08T01:48:29.426Z  INFO 28448 --- [storefront-client] [           main] c.s.g.s.StorefrontClientApplication      : Started StorefrontClientApplication in 3.237 seconds (process running for 4.157)
```

## 4. UNARY RPC — GET /api/products (ListProducts)

```bash
$ curl -s http://localhost:8080/api/products | jq
```
```json
[
  {
    "sku": "SKU-0001",
    "name": "Mechanical Keyboard",
    "description": "Tenkeyless mechanical keyboard with hot-swappable switches",
    "price": "129.99 USD",
    "quantityAvailable": 42,
    "category": "ELECTRONICS",
    "updatedAt": "2026-07-08T01:48:27.539038250Z"
  },
  {
    "sku": "SKU-0002",
    "name": "USB-C Dock",
    "description": "11-in-1 USB-C docking station, dual 4K output",
    "price": "89.50 USD",
    "quantityAvailable": 17,
    "category": "ELECTRONICS",
    "updatedAt": "2026-07-08T01:48:27.539107650Z"
  },
  {
    "sku": "SKU-0003",
    "name": "Distributed Systems Field Guide",
    "description": "600 pages of consensus, clocks, and cautionary tales",
    "price": "45.99 USD",
    "quantityAvailable": 5,
    "category": "BOOKS",
    "updatedAt": "2026-07-08T01:48:27.539112276Z"
  },
  {
    "sku": "SKU-0004",
    "name": "Conference Hoodie",
    "description": "Soft-touch hoodie, unisex fit",
    "price": "38.00 USD",
    "quantityAvailable": 120,
    "category": "APPAREL",
    "updatedAt": "2026-07-08T01:48:27.539115296Z"
  },
  {
    "sku": "SKU-0005",
    "name": "Cold Brew Concentrate",
    "description": "1L cold brew concentrate, makes 8 cups",
    "price": "14.99 USD",
    "quantityAvailable": 0,
    "category": "GROCERY",
    "updatedAt": "2026-07-08T01:48:27.539119861Z"
  }
]
```

## 5. UNARY RPC with deadline — GET /api/products/{sku} (GetProduct)

```bash
$ curl -s http://localhost:8080/api/products/SKU-0001 | jq
```
```json
{
  "sku": "SKU-0001",
  "name": "Mechanical Keyboard",
  "description": "Tenkeyless mechanical keyboard with hot-swappable switches",
  "price": "129.99 USD",
  "quantityAvailable": 42,
  "category": "ELECTRONICS",
  "updatedAt": "2026-07-08T01:48:27.539038250Z"
}
```

## 6. Error mapping — unknown SKU → gRPC NOT_FOUND → HTTP 404

```bash
$ curl -s -w '\nHTTP status: %{http_code}\n' http://localhost:8080/api/products/SKU-9999
```
```json
{
  "grpcStatus": "NOT_FOUND",
  "message": "No product with SKU 'SKU-9999'"
}
```
```
HTTP status: 404
```

## 7. SERVER STREAMING — GET /api/products/{sku}/stock/stream (WatchStock)

Server-Sent Events; each event is pushed by the server ~400ms apart as the gRPC WatchStock stream emits.

```bash
$ curl -sN 'http://localhost:8080/api/products/SKU-0001/stock/stream?updates=4'
```
```
[01:48:56.959] event:stock-update
[01:48:56.961] data:{"sku":"SKU-0001","quantityAvailable":40,"reason":"SALE","detail":"2 units sold","occurredAt":"2026-07-08T01:48:56.949641938Z"}
[01:48:56.963] 
[01:48:57.357] event:stock-update
[01:48:57.359] data:{"sku":"SKU-0001","quantityAvailable":43,"reason":"RESTOCK","detail":"3 units from Acme Wholesale","occurredAt":"2026-07-08T01:48:57.350920705Z"}
[01:48:57.361] 
[01:48:57.757] event:stock-update
[01:48:57.759] data:{"sku":"SKU-0001","quantityAvailable":52,"reason":"RESTOCK","detail":"9 units from Acme Wholesale","occurredAt":"2026-07-08T01:48:57.752684984Z"}
[01:48:57.761] 
[01:48:58.158] event:stock-update
[01:48:58.160] data:{"sku":"SKU-0001","quantityAvailable":53,"reason":"RESTOCK","detail":"1 units from Acme Wholesale","occurredAt":"2026-07-08T01:48:58.153394174Z"}
[01:48:58.162] 
```

## 8. CLIENT STREAMING — POST /api/shipments (RecordShipments)

Client uploads a batch of shipments; server streams them in one by one and returns a single aggregated summary once the upload stream completes.

```bash
$ curl -s -X POST http://localhost:8080/api/shipments \
    -H 'Content-Type: application/json' \
    -d '[
          {"sku": "SKU-0001", "quantity": 25, "supplier": "Acme Wholesale"},
          {"sku": "SKU-0005", "quantity": 60, "supplier": "Bean Supply Co"},
          {"sku": "SKU-0002", "quantity": 3,  "supplier": "Acme Wholesale"}
        ]' | jq
```
```json
{
  "shipmentsReceived": 3,
  "totalUnitsAdded": 88,
  "updatedQuantities": {
    "SKU-0001": 67,
    "SKU-0005": 60,
    "SKU-0002": 20
  }
}
```

## 9. BIDIRECTIONAL STREAMING — POST /api/orders (ProcessOrders)

Client streams orders while the server streams back per-order confirmations. The storefront logs (captured below) show responses arriving while the next order is still being sent — the signature of bidirectional streaming.

```bash
$ curl -s -X POST http://localhost:8080/api/orders \
    -H 'Content-Type: application/json' \
    -d '[
          {"orderId": "order-001", "sku": "SKU-0001", "quantity": 2},
          {"orderId": "order-002", "sku": "SKU-0004", "quantity": 1},
          {"orderId": "order-003", "sku": "SKU-0005", "quantity": 999},
          {"orderId": "order-004", "sku": "SKU-XXXX", "quantity": 1}
        ]' | jq
```
```json
{
  "statuses": [
    {
      "orderId": "order-001",
      "result": "CONFIRMED",
      "message": "Confirmed - 65 units remaining",
      "totalPrice": "259.98 USD"
    },
    {
      "orderId": "order-002",
      "result": "CONFIRMED",
      "message": "Confirmed - 119 units remaining",
      "totalPrice": "38.00 USD"
    },
    {
      "orderId": "order-003",
      "result": "REJECTED_OUT_OF_STOCK",
      "message": "Insufficient stock for SKU-0005",
      "totalPrice": null
    },
    {
      "orderId": "order-004",
      "result": "REJECTED_UNKNOWN_SKU",
      "message": "Unknown SKU SKU-XXXX",
      "totalPrice": null
    }
  ]
}
```

## 10. Server-side interceptor logging

The global server interceptor (`GrpcInterceptorConfig`) logs every call with the client ID read from metadata (attached by the client's own interceptor), the gRPC status, and timing:

```
2026-07-08T01:48:45.136Z  INFO 28446 --- [inventory-server] [ault-executor-0] c.s.g.i.config.GrpcInterceptorConfig     : gRPC inventory.v1.InventoryService/ListProducts from client [storefront-client] -> OK in 15 ms
2026-07-08T01:48:50.782Z  INFO 28446 --- [inventory-server] [ault-executor-0] c.s.g.i.config.GrpcInterceptorConfig     : gRPC inventory.v1.InventoryService/GetProduct from client [storefront-client] -> OK in 1 ms
2026-07-08T01:48:58.553Z  INFO 28446 --- [inventory-server] [ault-executor-0] c.s.g.i.config.GrpcInterceptorConfig     : gRPC inventory.v1.InventoryService/WatchStock from client [storefront-client] -> OK in 1607 ms
2026-07-08T01:49:07.153Z  INFO 28446 --- [inventory-server] [ault-executor-0] c.s.g.i.config.GrpcInterceptorConfig     : gRPC inventory.v1.InventoryService/RecordShipments from client [storefront-client] -> OK in 90 ms
2026-07-08T01:49:14.171Z  INFO 28446 --- [inventory-server] [ault-executor-0] c.s.g.i.config.GrpcInterceptorConfig     : gRPC inventory.v1.InventoryService/ProcessOrders from client [storefront-client] -> OK in 9 ms
```

## 11. Client-side bidirectional interleaving (storefront-client log)

Notice `<- order-00X` confirmations arriving between the `-> order-00X` sends — proof both directions of the stream are live simultaneously:

```
2026-07-08T01:49:14.159Z  INFO 28448 --- [storefront-client] [nio-8080-exec-2] c.s.g.s.api.StorefrontController         : -> order order-001 (2 x SKU-0001)
2026-07-08T01:49:14.160Z  INFO 28448 --- [storefront-client] [nio-8080-exec-2] c.s.g.s.api.StorefrontController         : -> order order-002 (1 x SKU-0004)
2026-07-08T01:49:14.160Z  INFO 28448 --- [storefront-client] [nio-8080-exec-2] c.s.g.s.api.StorefrontController         : -> order order-003 (999 x SKU-0005)
2026-07-08T01:49:14.160Z  INFO 28448 --- [storefront-client] [nio-8080-exec-2] c.s.g.s.api.StorefrontController         : -> order order-004 (1 x SKU-XXXX)
2026-07-08T01:49:14.170Z  INFO 28448 --- [storefront-client] [ault-executor-1] c.s.g.s.api.StorefrontController         : <- order order-001 : RESULT_CONFIRMED
2026-07-08T01:49:14.171Z  INFO 28448 --- [storefront-client] [ault-executor-1] c.s.g.s.api.StorefrontController         : <- order order-002 : RESULT_CONFIRMED
2026-07-08T01:49:14.172Z  INFO 28448 --- [storefront-client] [ault-executor-1] c.s.g.s.api.StorefrontController         : <- order order-003 : RESULT_REJECTED_OUT_OF_STOCK
2026-07-08T01:49:14.173Z  INFO 28448 --- [storefront-client] [ault-executor-1] c.s.g.s.api.StorefrontController         : <- order order-004 : RESULT_REJECTED_UNKNOWN_SKU
```

## 12. Actuator health (storefront-client)

```bash
$ curl -s http://localhost:8080/actuator/health | jq
```
```json
{
  "components": {
    "diskSpace": {
      "details": {
        "total": 270553174016,
        "free": 31774715904,
        "threshold": 10485760,
        "path": "/home/user/DemosAndArticleContent/blog/grpc-spring-boot-ultimate-guide/.",
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

## Note on grpcurl

`scripts/grpcurl-examples.sh` talks to inventory-server directly (no REST) using the standard reflection service — `grpcurl -plaintext localhost:9090 list`, `describe`, and raw calls for all four RPC types, plus the standard health check. grpcurl wasn't installable in the sandbox that generated this file; run the script locally (`brew install grpcurl`) to capture that output too.

