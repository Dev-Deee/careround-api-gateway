# careround-api-gateway

Single entry point for CareRound clients on **8080** (reactive Spring Cloud Gateway). It routes, checks the JWT,
handles CORS, adds a correlation id and rate-limits the public auth endpoints. No database, Kafka or gRPC.

The services still verify the JWT themselves and enforce role/ward rules (defence in depth). The gateway forwards
`Authorization` unchanged and never mints or rewrites tokens.

## Versions

Spring Boot 4.1.0 / Java 25 with **Spring Cloud 2025.1.4** (`spring-cloud-starter-gateway-server-webflux`). The
2025.1.x (Oakwood) release train supports Spring Boot 4.0.x and 4.1.x starting with 2025.1.2
([spring.io/projects/spring-cloud](https://spring.io/projects/spring-cloud), "Release train Spring Boot compatibility").

## Run

Needs the services and `careround-redis` (root `docker-compose.yml`). `.env` holds only `JWT_PUBLIC_KEY` (same value as
the other services; gitignored): copy `.env.example` to `.env`.

```bash
./mvnw clean package -DskipTests && java -jar target/careround-api-gateway-0.0.1-SNAPSHOT.jar
```

| Property | Default | |
|---|---|---|
| `app.jwt.public-key` | `${JWT_PUBLIC_KEY}` | RSA public key (static PEM, not the JWKS endpoint) |
| `app.routes.{auth,user,ward,patient,round,care-task,notification,realtime}-url` | `http://localhost:8081` … `8088` | downstream base URLs |
| `app.routes.connect-timeout-ms` / `response-timeout-ms` | `2000` / `30000` | per route |
| `app.cors.allowed-origins` | `http://localhost:*` | comma-separated origin patterns |
| `app.cors.allow-credentials` | `false` | |
| `app.rate-limit.login-per-minute` / `refresh-per-minute` / `activate-per-minute` | `10` / `30` / `10` | per client IP |
| `app.rate-limit.enabled` | `true` | |
| `spring.data.redis.host` / `port` | `localhost` / `6379` | rate-limit counters |

## Routes

First match wins, so more specific routes come first. The table is `config/RouteTable.java`; it drives both routing
and the public-path check.

| # | Paths | Service | Gateway JWT check |
|---|---|---|---|
| 1 | `/api/v1/wards/*/alerts` | notification 8087 | yes |
| 2 | `/api/v1/alerts/**` | notification 8087 | yes |
| 3 | `/.well-known/jwks.json`, `/api/v1/auth/login`, `/api/v1/auth/refresh`, `/api/v1/auth/activate-account` (GET page + form POST) | auth 8081 | **public** (rate-limited) |
| 4 | `/api/v1/auth/**` | auth 8081 | yes |
| 5 | `/api/v1/users/**` | user 8082 | yes |
| 6 | `/api/v1/wards/**`, `/api/v1/attendance/**`, `/api/v1/charge-assignments/**`, `/api/v1/charge-succession/**`, `/api/v1/handovers/**`, `/api/v1/shift-definitions/**`, `/api/v1/staffing-requirements/**` | ward 8083 | yes |
| 7 | `/api/v1/patients/**` (incl. `/{id}/next-of-kin`) | patient 8084 | yes |
| 8 | `/api/v1/rounds/**`, `/api/v1/reviews/**`, `/api/v1/notes/**`, `/api/v1/emergencies/**` | round 8085 | yes |
| 9 | `/api/v1/care-tasks/**` (incl. `/{id}/medication-details`), `/api/v1/care-plans/**`, `/api/v1/care-protocols/**`, `/api/v1/task-type-definitions/**` | care-task 8086 | yes |
| 10 | `/ws/**` (WebSocket upgrade) | realtime 8088 | **public**: realtime verifies the JWT on STOMP CONNECT |

Anything else → 404. The gateway's own `/actuator/health` is public; other actuator endpoints and the services'
swagger/actuator are not exposed.

**Adding a route:** add a `GatewayRoute` row to `RouteTable.ROUTES` at the right position (before any broader pattern
it overlaps), and a URL property in `RoutesProperties` if it is a new service. Mark it `Access.PUBLIC` only if the
service handles unauthenticated requests on purpose.

## Errors

Every gateway-generated error uses the services' body: `{"status", "error", "message", "timestamp"}`.

| Status | When |
|---|---|
| 401 + `WWW-Authenticate: Bearer` | protected route without a Bearer token, or with an invalid/expired one (`Missing bearer token`, `Invalid token`, `Token expired`) |
| 404 | no route matches |
| 429 + `Retry-After` | rate limit on login/refresh/activate-account exceeded |
| 503 | the service could not be reached (connection refused / connect timeout) |
| 504 | the service did not answer within the response timeout |
| 500 | anything unexpected (logged at ERROR with the stack trace; generic message to the client) |

Errors produced by a service (400, 403, 404, 409 …) pass through unchanged. A path inside a routed prefix that the service does not
map (e.g. `GET /api/v1/users/`) gets the service's own 404 `No endpoint <METHOD> <path>`. Note the services answer an unauthenticated
request with 403; through the gateway such a request is already stopped with 401.

## Correlation id

`X-Request-Id`: a well-formed inbound value (`[A-Za-z0-9._-]{1,128}`) is kept, otherwise one is generated. It is sent to
the service, returned on the response (also on 401/404/429) and on the gateway's access-log line:
`requestId=<id> GET /api/v1/wards -> 200 OK route=ward 12ms`.

## CORS and rate limiting

- CORS is configured only here. Allowed headers `Authorization, Content-Type, Accept, X-Request-Id`; `X-Request-Id` is
  exposed. The services configure no CORS, so responses carry exactly one set of CORS headers.
- Rate limiting covers only login, refresh and activate-account, per client IP (socket peer; `X-Forwarded-For` is not
  trusted) in fixed one-minute windows counted in Redis. If Redis is down or slower than 500 ms the request is allowed
  and a WARN is logged (fail open), so a Redis outage cannot lock staff out. `/ws` is never limited.
- A ward behind one NAT address shares one budget; raise the limits if tablets share an IP.

## Tests

`./mvnw test`: `PublicPathMatcherTest` (public / protected / unrouted paths) and `JwtAuthGlobalFilterTest` (valid,
public, missing, non-Bearer, garbage, bad signature, expired). Postman: see `postman/README.md`.
