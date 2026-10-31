# CareRound - API Gateway (Postman)

Import `CareRound-Gateway.postman_collection.json` and `CareRound-Gateway.postman_environment.json`, pick the
**CareRound - API Gateway** environment, set `superAdminPassword` and run the folders in order:

| Folder | Checks |
|---|---|
| 00 - Public routes | login, JWKS and the activation page need no JWT; gateway `/actuator/health` |
| 01 - 401 at the gateway | missing / non-Bearer / garbage token → 401 `{status,error,message,timestamp}` + `WWW-Authenticate: Bearer` |
| 02 - Routing | `/api/v1/wards/{id}` → ward-service, `/api/v1/wards/{id}/alerts` → notification-service; unrouted path and downstream actuator → 404 JSON |
| 03 - Correlation id | `X-Request-Id` generated, echoed, present on 401s |
| 04 - CORS | preflight from `http://localhost:5173` allowed; other origins 403 |
| 05 - Rate limit (run last) | the 11th login from this IP within a minute → 429 + `Retry-After`. Uses up the IP's login allowance for up to a minute |

An expired-token 401 is covered by the unit tests (`JwtAuthGlobalFilterTest`) because Postman cannot sign tokens.

The other services' collections also run through the gateway unchanged: point every `*BaseUrl` in their environment
at `http://localhost:8080`.
