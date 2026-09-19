# Proxy VIP API

A Spring Boot service that allocates Virtual IPs (VIPs) to (Source IP, Destination IP) pairs on behalf of DNS Partners, with idempotency, per-source uniqueness, and persistence across restarts.

---

## Tech Stack

Java 17, Spring Boot (Web, Validation, Data JPA), PostgreSQL, Maven, JUnit 5 + Mockito, Lombok.

---

## How to Run

**1. Database setup** — requires a local PostgreSQL instance with a database named `proxyvipdb`.

**2. Set the DB password** via a `.env` file (or environment variable):
```
DB_PASSWORD=your_postgres_password
```
`application.properties` reads this as `${DB_PASSWORD}`. `.env` is git-ignored — never committed.

**3. Run:**
```bash
mvn clean install
mvn spring-boot:run
```
Hibernate auto-creates the `vip_allocation` table on first run (`ddl-auto=update`). Service starts on `http://localhost:8080` with the pre-configured pool: `1.1.1.1` – `1.1.1.6`.

**4. Test:**
```bash
mvn test
```
SQL/schema debug logging is left enabled in `application.properties` intentionally, for reviewer transparency into what Hibernate is doing.

---

## API Endpoints (spec-required)

| Method | Endpoint | Maps to spec's | Behavior |
|---|---|---|---|
| `POST` | `/api/vip/allocate` | `allocate(SourceIP, DestinationIP)` | Returns the VIP for this pair, allocating a new one if it doesn't exist yet |
| `GET` | `/api/vip/getVip/{sourceIP}/{destinationIP}` | `get(SourceIP, DestinationIP)` | Returns the already-allocated VIP; throws 404 if none exists |
| `POST` | `/api/vip/add` | `add(VIP)` | Adds a new VIP to the global pool, immediately usable |

**`allocate` — request/response:**
```json
// POST /api/vip/allocate  { "sourceIP": "10.10.10.10", "destinationIP": "127.0.0.1" }
// 200 OK
{ "allocatedVip": "1.1.1.1" }
// 409 Conflict (pool exhausted for this source)
{ "status": 409, "message": "VIP pool Exception : No available VIPs remaining for source IP allocation" }
```

**`get` — 404 when no allocation exists:**
```json
{ "status": 404, "message": "VIP is not allocated" }
```

**`add` — request/response:**
```json
// POST /api/vip/add  { "newVip": "1.1.1.7" }
// 201 Created
{ "isAdded": true }
```

## Additional Utility Endpoints (beyond spec, for debugging/ops visibility)

- `GET /api/vip/getAllVips` — current global VIP pool
- `GET /api/vip/allocations` — all persisted allocations from the database
- `DELETE /api/vip/removeAllVips` — clears all allocations (memory + DB); the VIP pool itself (initial + added) is intentionally preserved

---

## Design Decisions (brief)

- **Per-source-IP data model:** `ConcurrentHashMap<String, PerSourceState>`, where each source's `PerSourceState` holds a `destinationIP → VIP` map and a `Set<VIP>` for O(1) "already used" checks — required by the rule that a source can't reuse a VIP across destinations.
- **Concurrency:** `computeIfAbsent` atomically creates one `PerSourceState` per source IP; `synchronized(state)` locks per-source (not globally), so unrelated source IPs never block each other. Verified with a 100K-concurrent-source stress test (see Testing).
- **`Optional` over exceptions for internal lookups:** a missing mapping is the normal case for a new pair, not an error — `get()` uses `orElseThrow` since a caller of `get()` *does* expect the pair to already exist.
- **409 for pool exhaustion, 404 for "not found":** chosen using a retry-safety lens — 409 because retrying an exhausted allocation won't help until `add()` is called; 404 because "get" for a pair that was never allocated is a standard not-found case.
- **Persistence, kept off the hot path:** `allocate()` writes to the in-memory map only; the DB write happens via `@Async` in a separate `VipPersistenceService` bean, so `allocate()` never waits on Postgres. On startup, `@PostConstruct` reads all rows via `findAll()` and rebuilds the in-memory state before the app accepts traffic. **Known limitation:** if the async write fails, the in-memory and DB state can diverge until the next successful write for that pair — failures are logged, not retried, given the assignment's time scope.
- **One DB table, not two:** `vip_allocation(source_ip, destination_ip, vip)` is enough to rebuild both `destinationToVip` and `usedVips` on reload — a second table would just duplicate derivable data.
- **No separate cache layer:** the in-memory `ConcurrentHashMap` already serves every live read/write instantly; the database is only touched for background persistence and startup reload, never in the request path — so a cache in front of the database would have nothing meaningful to do.

---

## Testing

12 tests in `ProxyVipServiceTest`, using Mockito to mock `VipAllocationRepository`/`VipPersistenceService` (no real DB needed to run tests):

1. Idempotency — same pair returns same VIP
2. Per-source uniqueness — same source, different destinations, different VIPs
3. Cross-source independence — one exhausted source doesn't affect another
4. Random, non-sequential VIP selection
5. Exhaustion throws `VipPoolExhaustedException`
6. Dynamic pool growth via `add()`
7. `get()` success
8. `get()` failure (`VipNotAllocated`)
9. Allocated VIPs always come from the configured pool
10. Concurrency — 6 threads, same source, no duplicate VIPs
11. **Stress test — 100,000 distinct concurrent source IPs allocating simultaneously** (200-thread pool): zero errors, zero state corruption, every source gets exactly one valid VIP. Timing printed to console.
12. **DB reload** — seeds a mocked `findAll()` with existing allocations, calls `loadAllocationsFromDatabase()` directly (simulating a restart), and confirms `get()` returns the correct VIPs without any new `allocate()` call. This test caught a real bug during development (an entity constructor silently discarding its arguments) that manual testing had missed.

**Known limitations:** the randomness test has a theoretical 1-in-720 chance of false failure; the concurrency/stress tests are probabilistic evidence of correctness under load, not a mathematical proof against every possible thread interleaving.

---

## Future Improvements

- Retry/reconciliation for failed async DB writes
- Externalize the initial VIP pool to `application.yml`
- HTTP-layer (`MockMvc`) tests for the controller and exception handler
- Migration tooling (Flyway/Liquibase) instead of `ddl-auto=update` for production use
