/*
 * A gRPC system is almost always a *multi-module* build:
 *
 *   - one module owns the .proto contract and the code generated from it
 *   - every service (server or client) depends on that contract module
 *
 * This mirrors how real organizations share proto files (often via a
 * dedicated "proto" repository or a Buf registry). Keeping generated code
 * in ONE place means the server and client can never drift apart.
 */
rootProject.name = "grpc-spring-boot-ultimate-guide"

// The shared gRPC contract: .proto files + generated Java stubs.
include("inventory-proto")

// A pure gRPC server (no HTTP at all) exposing the InventoryService.
include("inventory-server")

// A Spring MVC application that consumes InventoryService as a gRPC client
// and re-exposes it as a friendly REST/JSON API.
include("storefront-client")
