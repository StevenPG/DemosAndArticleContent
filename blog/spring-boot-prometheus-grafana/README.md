# Spring Boot + Prometheus + Grafana: From Zero to Dashboard

Companion project for the article [Spring Boot + Prometheus + Grafana: From Zero to Dashboard](https://stevenpg.com/posts/spring-boot-prometheus-grafana).

A minimal Spring Boot 4 app exporting Micrometer metrics at
`/actuator/prometheus`, plus a docker-compose stack with Prometheus and a
fully provisioned Grafana — datasource and dashboard included, zero clicking.

## Running

```bash
# 1. Start Prometheus + Grafana
docker compose up -d

# 2. Start the app on the host
./gradlew bootRun

# 3. Give the dashboard something to show
./generate-traffic.sh
```

Then open [localhost:3000](http://localhost:3000) → Dashboards → Spring Boot →
**Spring Boot Overview**. Anonymous viewer access is enabled; admin login is
admin/admin.

## The downloadable dashboard

The dashboard JSON lives at
[`docker/grafana/dashboards/spring-boot-overview.json`](docker/grafana/dashboards/spring-boot-overview.json)
and can be imported into any Grafana (Dashboards → Import). Panels:

- Request rate by endpoint / 5xx error rate
- Latency p50/p95/p99 from `http_server_requests` histograms
- JVM heap used vs max
- Custom business metrics: orders placed vs failed, payment p95
