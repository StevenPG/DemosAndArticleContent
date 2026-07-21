// backend-inventory: the second downstream service. We run TWO instances of this
// one (on 8082 and 8083) to demonstrate client-side load balancing at the gateway.
dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
}
