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

Medication task types are created with `defaultPriorityLevel: HIGH` and orders with `priorityLevel` (Phase 10a).
Order 1 is created already late, so it is stored `OVERDUE` within one timer poll; the collection waits 3 s before
reading it.

Login rate limit: the gateway allows 10 logins per IP per minute, so leave a minute between collection runs.

## Overdue and targeted alerts collection

`CareRound-Overdue.postman_collection.json` + `CareRound-Overdue.postman_environment.json` (single `gatewayBaseUrl`).
Same procedure: folders **00–01**, activate `doctor`, `chargeNurse`, `otherNurse` (folder 02's manual step; the
console line is `NOTIFICATION [EMAIL -> d***@careround.com] key=user-registered-<userId>: ... <link>`), then **02–07**.
Folders 04 and 06 wait (14 s and 10 s) for the real timer, outbox and consumer.

| Folder | Checks |
|---|---|
| 03 - Ward data | charge granted, patient, a medication type defaulting to HIGH, a nursing type |
| 04 - Timers fire | a medication order, a HIGH and a LOW nursing task due in 3 s all become `OVERDUE` with `overdueAt` |
| 05 - Targeted and ward alerts | doctor and charge nurse each have exactly one CRITICAL `MEDICATION_OVERDUE` in `/alerts/mine`; the other nurse has none; the ward replay has one HIGH `TASK_OVERDUE`, nothing for the LOW task and no targeted alerts; ack by other nurse / charge nurse → 403, by the doctor → 200, repeat idempotent |
| 06 - Medication administered | completing a medication order gives the prescriber one INFO alert (`requiresAck=false`), dismissable; the overdue order is claimed (stays OVERDUE) and completed late |
| 07 - Cleanup | cancels the open nursing tasks |

"Exactly one per recipient" is the redelivery guard as seen from the API. Real Kafka redelivery is covered by
notification-service's unit tests and the Phase 10a live run.
