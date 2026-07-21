// backend-orders: a small, ordinary Spring MVC service. It is one of the two
// downstream systems the gateways route to. Nothing here knows a gateway
// exists — that is the point. A backend behind a gateway is just a normal app.
dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
}
