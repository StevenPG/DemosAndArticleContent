rootProject.name = "spring-cloud-function-ultimate-guide"

// The business logic — plain java.util.function beans. No adapter, no server.
include("functions-core")

// One module per SURFACE the same functions are exposed over.
include("app-web")          // spring-cloud-function-web  -> HTTP endpoints
include("app-stream-kafka") // spring-cloud-stream        -> Kafka topics
include("app-rsocket")      // FunctionCatalog             -> RSocket routes
include("adapter-aws")      // spring-cloud-function-adapter-aws -> Lambda (local-only)
