rootProject.name = "spring-cloud-gateway-ultimate-guide"

// Downstream services the gateways route to.
include("backend-orders")
include("backend-inventory")

// The two flavors of Spring Cloud Gateway, built from the same set of routes.
include("gateway-webflux")
include("gateway-webmvc")
