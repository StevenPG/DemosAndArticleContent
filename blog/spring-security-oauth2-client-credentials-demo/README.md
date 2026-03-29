# Demo

Setup

```bash
docker run -p 8080:8080 -e KC_BOOTSTRAP_ADMIN_USERNAME=admin -e KC_BOOTSTRAP_ADMIN_PASSWORD=admin quay.io/keycloak/keycloak:26.2.5 start-dev
```

Sign into keycloak on `localhost:8080` with the credentials `admin:admin`.

Create a new client in the master realm with the following settings:

- Client ID: `test`
- Client type: `openid-connect`
- Client authentication: `on`
- Authentication flow: `service account roles`

Override the client secret in application.yaml with the content from Keycloak.

Hit play in Intellij!

Sample Output:

```text
 :: Spring Boot ::                (v3.5.0)

2025-06-04T01:56:49.335-04:00  INFO 16165 --- [           main] com.example.demo.DemoApplication         : Starting DemoApplication using Java 24.0.1 with PID 16165 (/spring-security-oauth2-client-credentials-demo/build/classes/java/main started by user in /spring-security-oauth2-client-credentials-demo)
2025-06-04T01:56:49.336-04:00  INFO 16165 --- [           main] com.example.demo.DemoApplication         : No active profile set, falling back to 1 default profile: "default"
2025-06-04T01:56:49.597-04:00  INFO 16165 --- [           main] o.s.b.w.embedded.tomcat.TomcatWebServer  : Tomcat initialized with port 8081 (http)
2025-06-04T01:56:49.601-04:00  INFO 16165 --- [           main] o.apache.catalina.core.StandardService   : Starting service [Tomcat]
2025-06-04T01:56:49.601-04:00  INFO 16165 --- [           main] o.apache.catalina.core.StandardEngine    : Starting Servlet engine: [Apache Tomcat/10.1.41]
2025-06-04T01:56:49.612-04:00  INFO 16165 --- [           main] o.a.c.c.C.[Tomcat].[localhost].[/]       : Initializing Spring embedded WebApplicationContext
2025-06-04T01:56:49.612-04:00  INFO 16165 --- [           main] w.s.c.ServletWebServerApplicationContext : Root WebApplicationContext: initialization completed in 262 ms
Application has started successfully!
2025-06-04T01:56:49.718-04:00  INFO 16165 --- [           main] ceWritingOAuth2AccessTokenResponseClient : Request URI: http://localhost:8080/realms/master/protocol/openid-connect/token?audience=httpbin.org
Received a successful response from the REST client!
Application has completed successfully!
2025-06-04T01:56:50.068-04:00  INFO 16165 --- [           main] o.s.b.w.embedded.tomcat.TomcatWebServer  : Tomcat started on port 8081 (http) with context path '/'
2025-06-04T01:56:50.072-04:00  INFO 16165 --- [           main] com.example.demo.DemoApplication         : Started DemoApplication in 0.866 seconds (process running for 1.054)

```