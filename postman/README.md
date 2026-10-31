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

## Read side collection

`CareRound-ReadSide.postman_collection.json` + `CareRound-ReadSide.postman_environment.json` (single
`gatewayBaseUrl`, default `http://localhost:8080`) cover the paged/filtered read endpoints end to end through the
gateway. Set `superAdminEmail`/`superAdminPassword`, run folders **00–01**, activate the three new accounts from the
emailed links (folder 02's manual step: set `nursePassword`, `otherNursePassword`, `doctorPassword`), then run
**02–06**.

| Folder | Checks |
|---|---|
| 00 - Bootstrap | admin login (fresh `runId`), a ward for the run, the shift running now |
| 01 - Setup Staff | charge nurse, other nurse, doctor on that ward |
| 02 - Activate and Log In | logins; ids captured from the JWT `sub` |
| 03 - Data for the reads | charge grant, two patients, three doctor orders (one overdue), unknown patient → 404, medication details + administration, a round, clock-in, a handover with a note |
| 04 - Reads | directory (no email/phone) + batch, patient paging, care-task list (paging, sort, `overdueDerived`, `mine`, `unassigned`), medication log, ward task stats, round lists and `/patients`, attendance, handovers list/detail, paged wards, admin user list, admin notifications (masked, `/failed`) |
| 05 - Denials and bad input | nurse on medication log, non-charge nurse / doctor on stats, rounds and attendance, nurse/doctor on admin lists → 403; no scope filter, malformed id, 101 ids → 400; size clamp |
| 06 - Cleanup | admin override clock-out of the run's nurse |

Login rate limit: the gateway allows 10 logins per IP per minute, so leave a minute between collection runs.
