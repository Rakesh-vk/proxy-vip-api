# Proxy VIP API

A Spring Boot service that allocates Virtual IPs (VIPs) to (Source IP, Destination IP) pairs on behalf of DNS Partners, with strict idempotency and per-source uniqueness guarantees.

---

## Overview

This service maintains a shared pool of pre-configured VIPs and hands them out on request. For a given source IP, the same (source, destination) pair always receives the same VIP (idempotent), and a source IP is never given the same VIP twice across different destinations. The same VIP value **can** be given to different source IPs, since VIP usage is tracked independently, per source IP, against one shared global pool.

---

## Tech Stack

- Java 17
- Spring Boot (Spring Web, Spring Boot Starter Validation)
- Maven
- JUnit 5 (Spring Boot Starter Test)
- Lombok (used selectively — see Design Decisions)

---

## How to Run

```bash
mvn clean install
mvn spring-boot:run
```

The service starts on `http://localhost:8080` with the pre-configured pool:
`1.1.1.1, 1.1.1.2, 1.1.1.3, 1.1.1.4, 1.1.1.5, 1.1.1.6`

To run the test suite:

```bash
mvn test
```

---

## API Endpoints

### `POST /api/vip/allocate`

Allocates (or returns the existing) VIP for a given source/destination pair.

**Request:**
```json
{
  "sourceIP": "10.10.10.10",
  "destinationIP": "127.0.0.1"
}
```

**Response — 200 OK:**
```json
{
  "allocatedVip": "1.1.1.1"
}
```

**Response — 409 Conflict** (source IP has exhausted the available pool):
```json
{
  "status": 409,
  "message": "VIP pool Exception : No available VIPs remaining for source IP allocation"
}
```

### `POST /api/vip/add`

Adds a new VIP to the global pool, immediately available to all source IPs for future allocations.

**Request:**
```json
{
  "newVip": "1.1.1.7"
}
```

**Response — 201 Created:**
```json
{
  "isAdded": true
}
```

### `GET /api/vip`

Returns the current global VIP pool (debugging/inspection endpoint).

---

## Design Decisions

### 1. Data model: nested map, per source IP

The core data structure is `ConcurrentHashMap<String, PerSourceState>`, where each source IP maps to its own `PerSourceState` object. `PerSourceState` internally holds:
- `Map<String, String> destinationToVip` — for idempotency: given a destination, return the same VIP every time
- `Set<String> usedVips` — for fast, O(1) membership checks when picking a new, unused VIP

**Why nested-per-source rather than one flat map keyed by a combined (source, dest) string:** the spec's uniqueness rule ("no VIP reused for the same source across different destinations") requires knowing, for a given source, *every* VIP it has used so far. A per-source structure makes this a direct, local lookup. A flat map would require scanning every entry in the entire map to reconstruct one source's usage — both slower and less natural to reason about.

**Why a `Set`, not a `List`, for `usedVips`:** checking "has this VIP already been used" happens on every single allocation of a new pair — the hot path. A `List.contains()` is O(n); a `HashSet.contains()` is O(1) average case. Since this check is far more frequent than the (comparatively rare) write of a new assignment, the small extra bookkeeping cost on write is worth it for a fast, constant-time read.

### 2. `Optional<String>` instead of exceptions or `null` for idempotency lookups

`PerSourceState.getVIP(destinationIP)` returns `Optional<String>` rather than throwing an exception or returning `null` when a destination hasn't been seen before. A missing mapping is the **normal, expected** case for a brand-new pair — not an error — so an exception would be the wrong signal, and would also force every caller to wrap the call in a try/catch for a routine code path. `Optional` also collapses "check if it exists" and "get the value" into a single atomic call via `.orElseGet(...)`, rather than requiring two separate calls that a caller could forget or reorder.

### 3. Concurrency: `computeIfAbsent` + per-source-IP locking

Two distinct race conditions had to be addressed:

**Race 1 — duplicate `PerSourceState` creation.** If two threads simultaneously request a brand-new source IP, a naive "check if present, then create" sequence could let both threads create separate `PerSourceState` objects for the same source IP, silently losing one. `ConcurrentHashMap.computeIfAbsent(sourceIp, key -> new PerSourceState())` solves this atomically — only one `PerSourceState` is ever created per key, even under concurrent calls.

**Race 2 — duplicate VIP assignment.** If two threads allocate for the *same* source IP but two *different*, brand-new destinations at the same instant, both could read the same "not yet used" state, pick the same random VIP, and both assign it — violating the uniqueness rule. This is solved with `synchronized(state)`, where `state` is the specific `PerSourceState` object for that source IP (obtained via `computeIfAbsent`). Because `computeIfAbsent` guarantees one distinct object per source IP, `synchronized(state)` gives **true per-source-IP locking**: two different source IPs synchronize on two different objects and never block each other, while concurrent requests for the *same* source IP correctly serialize. Given the platform's stated scale (tens of millions of subscribers), a single global lock (e.g., `synchronized(this)`) would have created a severe throughput bottleneck; this design allows full parallelism across unrelated source IPs.

### 4. Plain `HashMap`/`HashSet` inside `PerSourceState`, not concurrent variants

Since all access to `PerSourceState`'s internals is already fully serialized by the caller's `synchronized(state)` block, using `ConcurrentHashMap`/thread-safe collections *inside* `PerSourceState` would be redundant — extra overhead (bucket locking, CAS operations) with no additional safety benefit, since mutual exclusion is already guaranteed by the coarser lock around it.

### 5. `CopyOnWriteArrayList` for the global VIP pool

The global pool (`vipPool`) is read on every single allocation (scanning for unused candidates) but written to only rarely (via `addVip()`). `CopyOnWriteArrayList` is built exactly for this read-heavy, write-rare profile: reads are fully lock-free, and the (infrequent) cost of copying the whole array on write is an acceptable trade-off given how rarely writes occur.

### 6. Random selection via `ThreadLocalRandom`

`ThreadLocalRandom.current()` is used instead of a shared `java.util.Random` instance, since `Random` is not designed for safe, contention-free use across many concurrent threads. `ThreadLocalRandom` gives each thread its own generator, avoiding contention entirely.

### 7. HTTP 409 Conflict for pool exhaustion

Retrying the exact same `allocate()` call for an exhausted source IP will never succeed until `addVip()` grows the pool — this isn't a transient server-availability issue (ruling out 503) or an issue with the request itself being malformed (ruling out 400/422). It's a conflict between the request and the current state of the resource (this source IP has used every available VIP), which is precisely what 409 Conflict is for. This is a judgment call — reasonable engineers could argue for other status codes — but 409 was chosen specifically using this "would retrying help?" lens.

### 8. DTOs as Java records

`AllocateRequest`, `AllocateResponse`, `AddVipRequest`, and `ErrorResponse` are all Java records rather than traditional classes. Records are immutable by default, require no boilerplate getters, and need no Lombok dependency — a natural fit for simple data carriers with no behavior.

### 9. Package structure — no `repository` layer

The project is organized into `controller`, `service`, `exception`, and `dto` packages. A `repository` package was deliberately **not** created: this service holds all state in memory (`ConcurrentHashMap`, `CopyOnWriteArrayList`) with no database or persistence layer, so a `repository` package would have nothing genuine to abstract — it would be an artificial layer added purely to match a conventional template, not because the problem calls for it.

### 10. Hardcoded initial VIP pool

`ProxyVipService` uses a no-argument constructor that initializes the pool with the spec's fixed list internally, rather than requiring Spring to inject an externally-configured list. Given the assignment specifies a fixed, known set of pre-configured VIPs, this avoided the added complexity of a separate `@Configuration` class or `application.yml` binding for a genuinely static list. The trade-off is explicit: changing the initial pool later requires a code change and redeploy, not just a config edit — see Future Improvements.

---

## Testing Approach

The test suite (`ProxyVipServiceTest`) covers all six functional rules from the spec, plus concurrency safety:

1. **Idempotency** — the same (source, destination) pair returns the same VIP on repeated calls
2. **Per-source uniqueness** — the same source IP never receives the same VIP for two different destinations
3. **Cross-source independence** — a fully exhausted source IP has no effect on a different source IP's ability to allocate from the full pool
4. **Random, non-sequential selection** — allocating across all 6 destinations for one source does not reproduce the pool's original insertion order
5. **Exhaustion** — allocating beyond a source's available VIPs throws `VipPoolExhaustedException`
6. **Dynamic pool growth** — a VIP added via `addVip()` becomes immediately allocatable, including recovering a previously-exhausted source
7. **Concurrency** — multiple threads concurrently allocating for the *same* source IP (different destinations) never receive duplicate VIPs, verified using an `ExecutorService` + `CountDownLatch` to run requests genuinely in parallel and collect results into a `CopyOnWriteArrayList` for safe inspection

**Known limitations, stated honestly:**
- The randomness test (#4) has a theoretical (astronomically small, 1-in-720) chance of a false failure if random selection happens to reproduce the exact sequential order by coincidence.
- The concurrency test (#7) is probabilistic, not a mathematical proof — passing consistently across multiple runs increases confidence in the locking design, but does not exhaustively rule out every possible interleaving the way a pure logic/unit test can.

---

## Trade-offs & Future Improvements

Given more time, the following would be natural next steps:

- **Persistence across restarts.** All state currently lives in memory and is lost on service restart. For production hardening, I would add periodic snapshotting of state to a database or an append-only event log, applied **asynchronously/batched** rather than synchronously per-request — a synchronous DB write on every `allocate()` call would undermine the per-source-IP locking design built specifically for high-throughput, low-latency parallelism.
- **Externalized VIP pool configuration.** Move the initial pool from a hardcoded list into `application.yml` via `@ConfigurationProperties`, so it can be changed without a code deployment.
- **HTTP-layer integration tests.** The current suite tests `ProxyVipService` directly; adding `MockMvc`-based tests would additionally verify the controller and `GlobalExceptionHandler` wiring end-to-end, including input validation (`@NotBlank`) behavior.
- **Metrics/observability.** Given the platform's emphasis on telco-grade reliability at scale, I'd add metrics for pool utilization per source IP, allocation latency, and exhaustion rate.
