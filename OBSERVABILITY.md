# Observability

## Following a request

1. Every response from the gateway (`:8080`) has an `X-Request-Id` header. A client may send its own id. Only values
   of 1 to 64 chars `[A-Za-z0-9-]` are kept. Anything else is replaced with a new UUID (this blocks log and header
   injection).
2. The gateway forwards the id downstream. Each Spring service puts it into the MDC (`requestId`) and returns it.
3. The id travels along each hop:
   - **Feign**: a global `RequestInterceptor` copies the MDC id onto every outgoing call.
   - **Kafka, direct sends**: a `ProducerInterceptor` adds the `X-Request-Id` header. A `RecordInterceptor` on every
     listener container factory restores it into the MDC.
   - **Kafka, outbox** (package-booking, payment): `OutboxWriter` stores the id in `outbox_event.request_id`.
     `OutboxRelay` sends it as the header, so asynchronous events keep the id of the request that caused them.
4. Every log line looks like `... INFO [thread] [<requestId>] logger : message`. To follow one request:

```bash
ID=<value of X-Request-Id>
for c in api-gateway package-booking-service payment-service notification-service; do
  docker logs waylo-$c 2>&1 | grep "$ID"; done
```

Logs from jobs with no request (schedulers, relay polling) show `[]`.

All of this lives in the `observability-common` module (Boot auto-configuration). A service gets it by adding
`implementation project(':observability-common')`.

**Why not Micrometer Tracing (Brave)?** Brave would add trace and span ids, but it still needs B3/W3C propagation set
up on Feign and Kafka. It would also generate its own trace id, so the gateway's `X-Request-Id` contract would need
extra code to match it. Our services build their own `KafkaTemplate` and container factories, so observation would
have to be enabled on each one. Also, the outbox relay sends from a scheduler thread, so the trace context would be
lost there anyway. Without a Zipkin/Tempo container, spans give little value. One header with about 150 lines of
code covers every hop, including the outbox.

## Log hygiene

The shared log pattern masks every e-mail address in log messages: `john.doe@example.com` becomes
`j***@example.com`. This covers all services, including the ones that log user e-mails (auth, notification), with no
change to their code. A grep found no logging of passwords, tokens or card data. Payment tokens and JWTs are never
passed to a logger.

## Metrics

Each service exposes `GET /actuator/prometheus` on its **own** port (only on 127.0.0.1 or the compose network). The
gateway excludes `prometheus` and `metrics`, so `curl :8080/actuator/prometheus` returns 404 (checked by
`scripts/test-observability.py`). To scrape from inside the network:
`docker exec waylo-payment-service wget -qO- localhost:8087/actuator/prometheus`.

| Metric | Service | Meaning |
|---|---|---|
| `waylo_orders_total{event="created"\|"paid"\|"cancelled"}` | package-booking | counted after commit |
| `waylo_payments_total{outcome="confirmed"\|"failed"}` | payment | intent confirm result (provider decline = failed) |
| `waylo_payment_refunds_total` | payment | escrow items refunded to the customer |
| `waylo_notifications_total{channel,status}` | notification | EMAIL/PUSH/SMS × SENT/FAILED/SUPPRESSED/PENDING |
| `waylo_outbox_unpublished` | package-booking, payment | `outbox_event` rows with `published_at IS NULL` |
| `http_server_requests_seconds_*` | all | standard Spring MVC metrics (status, uri) |

## Suggested alerts (PromQL)

```yaml
- alert: OutboxBacklog          # relay stuck or Kafka down
  expr: waylo_outbox_unpublished > 50
  for: 5m
- alert: PaymentFailureRate
  expr: sum(rate(waylo_payments_total{outcome="failed"}[15m]))
        / sum(rate(waylo_payments_total[15m])) > 0.2
  for: 10m
- alert: High5xxRate
  expr: sum by (job) (rate(http_server_requests_seconds_count{status=~"5.."}[5m]))
        / sum by (job) (rate(http_server_requests_seconds_count[5m])) > 0.05
  for: 5m
- alert: NotificationFailures
  expr: sum(rate(waylo_notifications_total{status="FAILED"}[15m])) > 0
  for: 15m
```

No Prometheus container ships with the stack. Add a scrape job per service port when you need one.
