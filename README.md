# Smart Campus Sensor & Room Management API

A **JAX-RS / Jersey** RESTful web service deployed as a **WAR on Apache Tomcat**.  
Manages university Rooms and their associated Sensors, with full historical reading logs, custom exception handling and request/response logging filters.

---

## Table of Contents
1. [API Design Overview](#api-design-overview)
2. [Technology Stack](#technology-stack)
3. [Project Structure](#project-structure)
4. [Build & Run Instructions](#build--run-instructions)
5. [API Reference](#api-reference)
6. [Sample curl Commands](#sample-curl-commands)
7. [Conceptual Report (Question & Answers)](#conceptual-report-question--answers)

---

## API Design Overview

The API follows a **resource-oriented RESTful** architecture. Every entity — Room, Sensor, SensorReading — is exposed as a distinct URI collection. Resources are nested to reflect the physical campus layout:

```
/api/v1                          ← Discovery endpoint (HATEOAS metadata)
/api/v1/rooms                    ← Room collection
/api/v1/rooms/{roomId}           ← Individual room
/api/v1/sensors                  ← Sensor collection (supports ?type= filter)
/api/v1/sensors/{sensorId}       ← Individual sensor
/api/v1/sensors/{sensorId}/readings        ← Reading sub-resource collection
/api/v1/sensors/{sensorId}/readings/{id}   ← Individual reading
```

**Data storage** is fully in-memory using `ConcurrentHashMap` and `CopyOnWriteArrayList` — no database is used, as per the coursework requirement.

**Error handling** is centralised via JAX-RS `ExceptionMapper` implementations. Every error is returned as a structured JSON object; raw stack traces are never exposed.

**Cross-cutting concerns** (logging) are handled via `ContainerRequestFilter` / `ContainerResponseFilter`, keeping resource methods clean.

---

## Technology Stack

| Layer | Technology |
|---|---|
| Language | Java 11 |
| REST Framework | JAX-RS 2.1 (Jersey 2.41) |
| Server | Apache Tomcat 9 (external) |
| JSON Serialisation | Jackson (via `jersey-media-json-jackson`) |
| Build Tool | Apache Maven 3 |
| Data Storage | `ConcurrentHashMap`, `CopyOnWriteArrayList` |

---

## Project Structure

```
smart-campus-api/
├── pom.xml
└── src/main/java/com/smartcampus/
    ├── (no Main.java — Tomcat is the server)
    ├── application/
    │   ├── SmartCampusApplication.java      ← JAX-RS Application subclass
    │   └── DataStore.java                   ← In-memory data + seed data
    ├── model/
    │   ├── Room.java
    │   ├── Sensor.java
    │   └── SensorReading.java
    ├── resource/
    │   ├── DiscoveryResource.java           ← GET /api/v1
    │   ├── RoomResource.java                ← /api/v1/rooms
    │   ├── SensorResource.java              ← /api/v1/sensors
    │   └── SensorReadingResource.java       ← /api/v1/sensors/{id}/readings
    ├── exception/
    │   ├── RoomNotEmptyException.java           ← 409
    │   ├── RoomNotEmptyExceptionMapper.java
    │   ├── LinkedResourceNotFoundException.java ← 422
    │   ├── LinkedResourceNotFoundExceptionMapper.java
    │   ├── SensorUnavailableException.java      ← 403
    │   ├── SensorUnavailableExceptionMapper.java
    │   └── GlobalExceptionMapper.java           ← 500 catch-all
    └── filter/
        └── LoggingFilter.java               ← Request + Response logging
```

---

## Build & Run Instructions

### Prerequisites
- **Java 11** or later (`java -version`)
- **Apache Maven 3.6+** (`mvn -version`)
- **Apache Tomcat 9** (download from https://tomcat.apache.org/download-90.cgi)

### Step 1 – Clone the project
```bash
git clone https://github.com/<your-username>/smart-campus-api.git
cd smart-campus-api
```

### Step 2 – Build the WAR file
```bash
mvn clean package -DskipTests
```
Maven War Plugin produces a deployable WAR at:
```
target/ROOT.war
```

### Step 3 – Deploy to Tomcat
**Important:** The WAR is named `ROOT.war` so Tomcat serves it at the root context (`/`), giving you clean URLs like `http://localhost:8080/api/v1/...`.

First, delete or backup the existing `$CATALINA_HOME/webapps/ROOT` directory, then copy the WAR:

**Windows:**
```cmd
rmdir /s /q C:\apache-tomcat-9.x.x\webapps\ROOT
copy target\ROOT.war C:\apache-tomcat-9.x.x\webapps\
```

**Mac / Linux:**
```bash
rm -rf /opt/tomcat/webapps/ROOT
cp target/ROOT.war /opt/tomcat/webapps/
```

### Step 4 – Start Tomcat

**Windows:**
```cmd
C:\apache-tomcat-9.x.x\bin\startup.bat
```

**Mac / Linux:**
```bash
/opt/tomcat/bin/startup.sh
```

Tomcat will auto-deploy the WAR. You will see in `logs/catalina.out`:
```
INFO: Deployment of web application archive [.../ROOT.war] has finished
```

### Step 5 – Verify the server is running
```bash
curl http://localhost:8080/api/v1
```

Expected: JSON discovery object with version, contact, and resource links.

### Step 6 – Stop Tomcat

**Windows:**
```cmd
C:\apache-tomcat-9.x.x\bin\shutdown.bat
```

**Mac / Linux:**
```bash
/opt/tomcat/bin/shutdown.sh
```

### NetBeans tip
Right-click project → Properties → Run → select your Tomcat server instance.  
Then press the green Run button — NetBeans will build, deploy and open the browser automatically.

---

## API Reference

### Base URL
```
http://localhost:8080/api/v1
```

### Seeded Data (available immediately after startup)

**Rooms:**
| ID | Name | Capacity |
|---|---|---|
| LIB-301 | Library Quiet Study | 40 |
| LAB-101 | Computer Lab 101 | 30 |

**Sensors:**
| ID | Type | Status | Room |
|---|---|---|---|
| TEMP-001 | Temperature | ACTIVE | LIB-301 |
| CO2-001 | CO2 | ACTIVE | LAB-101 |
| OCC-001 | Occupancy | MAINTENANCE | LIB-301 |

---

### Part 1 — Discovery

#### `GET /api/v1`
Returns API metadata, version, contact info and resource links (HATEOAS).

**Response 200:**
```json
{
  "api": "Smart Campus Sensor & Room Management API",
  "version": "1.0.0",
  "status": "OPERATIONAL",
  "contact": {
    "name": "Smart Campus Admin",
    "email": "admin@smartcampus.ac.uk"
  },
  "resources": {
    "rooms":   "/api/v1/rooms",
    "sensors": "/api/v1/sensors"
  },
  "links": {
    "self":    "/api/v1/",
    "rooms":   "/api/v1/rooms",
    "sensors": "/api/v1/sensors"
  }
}
```

---

### Part 2 — Room Management

#### `GET /api/v1/rooms`
Returns all rooms.

**Response 200:**
```json
[
  { "id": "LIB-301", "name": "Library Quiet Study", "capacity": 40, "sensorIds": ["TEMP-001","OCC-001"] },
  { "id": "LAB-101", "name": "Computer Lab 101",    "capacity": 30, "sensorIds": ["CO2-001"] }
]
```

---

#### `POST /api/v1/rooms`
Creates a new room.

**Request body:**
```json
{ "id": "HALL-001", "name": "Main Hall", "capacity": 200 }
```

**Response 201:**
```json
{
  "message": "Room created successfully.",
  "room": { "id": "HALL-001", "name": "Main Hall", "capacity": 200, "sensorIds": [] },
  "link": "/api/v1/rooms/HALL-001"
}
```

**Response 400** — missing id  
**Response 409** — room id already exists

---

#### `GET /api/v1/rooms/{roomId}`
Returns a single room.

**Response 200:**
```json
{ "id": "LIB-301", "name": "Library Quiet Study", "capacity": 40, "sensorIds": ["TEMP-001","OCC-001"] }
```

**Response 404** — room not found

---

#### `DELETE /api/v1/rooms/{roomId}`
Deletes a room. **Blocked** if the room still has sensors assigned.

**Response 200** (empty room):
```json
{ "message": "Room 'HALL-001' deleted successfully." }
```

**Response 404** — room not found  
**Response 409** — room has sensors (custom `RoomNotEmptyException`):
```json
{
  "status": 409,
  "error": "Conflict",
  "message": "Room 'LIB-301' has 2 sensor(s) still assigned: [TEMP-001, OCC-001]",
  "hint": "Remove all sensors from the room before deleting it."
}
```

---

### Part 3 — Sensor Operations

#### `GET /api/v1/sensors`
Returns all sensors. Optionally filter by type.

`GET /api/v1/sensors?type=CO2`

**Response 200:**
```json
[
  { "id": "CO2-001", "type": "CO2", "status": "ACTIVE", "currentValue": 450.0, "roomId": "LAB-101" }
]
```

---

#### `POST /api/v1/sensors`
Registers a new sensor. The `roomId` must reference an existing room.

**Request body:**
```json
{ "id": "LIGHT-001", "type": "Light", "status": "ACTIVE", "currentValue": 0.0, "roomId": "LIB-301" }
```

**Response 201:**
```json
{
  "message": "Sensor registered successfully.",
  "sensor": { "id": "LIGHT-001", "type": "Light", "status": "ACTIVE", "currentValue": 0.0, "roomId": "LIB-301" },
  "link": "/api/v1/sensors/LIGHT-001/readings"
}
```

**Response 400** — missing id  
**Response 409** — sensor already exists  
**Response 422** — `roomId` does not exist (custom `LinkedResourceNotFoundException`):
```json
{
  "status": 422,
  "error": "Unprocessable Entity",
  "message": "Room 'XYZ-999' does not exist.",
  "hint": "Make sure the roomId in the request body refers to an existing room."
}
```

---

#### `GET /api/v1/sensors/{sensorId}`
Returns a single sensor.

**Response 200:**
```json
{ "id": "TEMP-001", "type": "Temperature", "status": "ACTIVE", "currentValue": 22.5, "roomId": "LIB-301" }
```

---

### Part 4 — Sensor Readings (Sub-Resource)

#### `GET /api/v1/sensors/{sensorId}/readings`
Returns all historical readings for a sensor.

**Response 200:**
```json
{
  "sensorId": "TEMP-001",
  "count": 2,
  "readings": [
    { "id": "uuid-1", "timestamp": 1714000000000, "value": 23.1 },
    { "id": "uuid-2", "timestamp": 1714000060000, "value": 23.4 }
  ]
}
```

---

#### `POST /api/v1/sensors/{sensorId}/readings`
Appends a new reading for a sensor. Also updates `currentValue` on the parent sensor.  
**Blocked** if sensor status is `MAINTENANCE` or `OFFLINE`.

**Request body:**
```json
{ "value": 24.7 }
```

**Response 201:**
```json
{
  "message": "Reading recorded successfully.",
  "reading": { "id": "uuid-3", "timestamp": 1714000120000, "value": 24.7 },
  "sensorCurrentValue": 24.7
}
```

**Response 403** — sensor is MAINTENANCE or OFFLINE (custom `SensorUnavailableException`):
```json
{
  "status": 403,
  "error": "Forbidden",
  "message": "Sensor 'OCC-001' is MAINTENANCE and cannot accept readings.",
  "hint": "Change the sensor status to ACTIVE before posting readings."
}
```

---

### Part 5 — Error Responses Summary

| Scenario | HTTP Status | Exception |
|---|---|---|
| Delete room with sensors | 409 Conflict | `RoomNotEmptyException` |
| Create sensor with invalid roomId | 422 Unprocessable Entity | `LinkedResourceNotFoundException` |
| Post reading to MAINTENANCE/OFFLINE sensor | 403 Forbidden | `SensorUnavailableException` |
| Any unexpected runtime error | 500 Internal Server Error | `GlobalExceptionMapper` (catch-all) |

---

## Sample curl Commands

> Replace `http://localhost:8080/api/v1` with your actual base URL if needed.

### 1. Discovery endpoint
```bash
curl -s http://localhost:8080/api/v1 | python3 -m json.tool
```

### 2. List all rooms
```bash
curl -s http://localhost:8080/api/v1/rooms | python3 -m json.tool
```

### 3. Create a new room
```bash
curl -s -X POST \
  -H "Content-Type: application/json" \
  -d '{"id":"HALL-001","name":"Main Hall","capacity":200}' \
  http://localhost:8080/api/v1/rooms | python3 -m json.tool
```

### 4. Get a specific room
```bash
curl -s http://localhost:8080/api/v1/rooms/LIB-301 | python3 -m json.tool
```

### 5. Attempt to delete a room that still has sensors (expect 409)
```bash
curl -s -X DELETE \
  http://localhost:8080/api/v1/rooms/LIB-301 | python3 -m json.tool
```

### 6. Filter sensors by type
```bash
curl -s "http://localhost:8080/api/v1/sensors?type=CO2" | python3 -m json.tool
```

### 7. Register a new sensor
```bash
curl -s -X POST \
  -H "Content-Type: application/json" \
  -d '{"id":"LIGHT-001","type":"Light","status":"ACTIVE","currentValue":0.0,"roomId":"LIB-301"}' \
  http://localhost:8080/api/v1/sensors | python3 -m json.tool
```

### 8. Attempt to register a sensor with a non-existent roomId (expect 422)
```bash
curl -s -X POST \
  -H "Content-Type: application/json" \
  -d '{"id":"GHOST-001","type":"Ghost","status":"ACTIVE","currentValue":0.0,"roomId":"DOES-NOT-EXIST"}' \
  http://localhost:8080/api/v1/sensors | python3 -m json.tool
```

### 9. Post a reading to an ACTIVE sensor
```bash
curl -s -X POST \
  -H "Content-Type: application/json" \
  -d '{"value":24.7}' \
  http://localhost:8080/api/v1/sensors/TEMP-001/readings | python3 -m json.tool
```

### 10. Attempt to post a reading to a MAINTENANCE sensor (expect 403)
```bash
curl -s -X POST \
  -H "Content-Type: application/json" \
  -d '{"value":1.0}' \
  http://localhost:8080/api/v1/sensors/OCC-001/readings | python3 -m json.tool
```

### 11. Get reading history for a sensor
```bash
curl -s http://localhost:8080/api/v1/sensors/TEMP-001/readings | python3 -m json.tool
```

### 12. Get all sensors
```bash
curl -s http://localhost:8080/api/v1/sensors | python3 -m json.tool
```

---

## Conceptual Report (Question & Answers)

---

### Part 1.1 — Project & Application Configuration

> **Question:** Explain the default lifecycle of a JAX-RS Resource class. Is a new instance instantiated for every incoming request, or does the runtime treat it as a singleton? Elaborate on how this architectural decision impacts the way you manage and synchronize your in-memory data structures (maps/lists) to prevent data loss or race conditions.

By default, JAX-RS instantiates a **new resource class instance for every incoming HTTP request** (request-scoped). This is the behaviour mandated by the JAX-RS specification and is used by Jersey unless the developer explicitly opts into a different lifecycle.

**Impact on in-memory data management:**  
Because a fresh instance is created per request, you **cannot** store shared application state as instance fields inside a resource class — each request would see a blank slate. To share data safely across requests, the mutable state must live outside any particular resource instance, in a location that has a longer lifetime than a single request.

In this implementation, all shared data is stored as `public static final` fields in the `DataStore` class (a singleton-style utility class). These maps and lists are initialised once in a `static {}` block at class-load time and are referenced by all resource instances for every request.

**Thread-safety:**  
Because multiple requests can arrive concurrently, plain `HashMap` or `ArrayList` would cause data corruption. This project uses:
- `ConcurrentHashMap<String, Room>` and `ConcurrentHashMap<String, Sensor>` — lock-stripe concurrent maps that allow safe parallel reads and writes without a global lock.
- `CopyOnWriteArrayList<SensorReading>` — a thread-safe list where each mutation produces a new internal array snapshot, making it ideal for reading-heavy collections like sensor histories.

This design means no explicit `synchronized` blocks are needed in resource methods, and no data loss or race conditions occur even under high concurrency.

---

### Part 1.2 — The "Discovery" Endpoint

> **Question:** Why is the provision of "Hypermedia" (links and navigation within responses) considered a hallmark of advanced RESTful design (HATEOAS)? How does this approach benefit client developers compared to static documentation?

**HATEOAS** (Hypermedia as the Engine of Application State) is a constraint of REST architecture in which every API response includes navigational links pointing to related resources and valid next actions. Rather than expecting clients to hard-code URLs, they discover what they can do next by reading the links embedded in each response.

**Benefits over static documentation:**

1. **Loose coupling** — Client code does not need to know any endpoint URLs in advance. It starts at a single entry point (e.g. `GET /api/v1`) and follows links to navigate the entire API, just as a browser navigates HTML pages via `<a href="...">` tags.

2. **Evolvability** — If a URL structure changes in a future API version, clients that follow links rather than hard-coding paths continue to work without modification.

3. **Discoverability** — A developer exploring an unfamiliar API can read response bodies and immediately understand what operations are available and where to go next, without consulting external documentation.

4. **Self-documentation** — The API is partially self-describing. The discovery endpoint at `GET /api/v1` returns links to all primary resource collections, giving new clients a complete map of the system in a single request.

In this project, the discovery endpoint returns a `links` map pointing to `/api/v1/rooms` and `/api/v1/sensors`, and `POST /api/v1/sensors` responses include a direct link to the newly created sensor's readings endpoint — both are examples of hypermedia-driven navigation.

---

### Part 2.1 — Room Resource Implementation

> **Question:** When returning a list of rooms, what are the implications of returning only IDs versus returning the full room objects? Consider network bandwidth and client-side processing.

When designing `GET /api/v1/rooms` the key trade-off is between **network efficiency** and **client-side simplicity**.

**Returning only IDs** (`["LIB-301", "LAB-101"]`):
- Minimal payload — very fast for large collections.
- Forces the client to issue an additional `GET /api/v1/rooms/{id}` request per room to retrieve useful data — the "N+1 request" anti-pattern that dramatically increases latency and server load when the client needs details for all rooms.

**Returning full room objects** (this implementation):
- Single round-trip delivers all information the client needs to render a room list page.
- Larger payload, but for typical campus-scale collections (tens to low hundreds of rooms) the overhead is negligible compared to the saved round-trips.
- Consistent with the RESTful principle that a collection endpoint should return a complete representation of each member.

**Recommendation:** Return full objects for moderate-sized collections as implemented here. For very large datasets, combine full objects with **pagination** (e.g. `?page=0&size=20`) to control response size without forcing the client to make N extra requests.

---

### Part 2.2 — Room Deletion & Safety Logic

> **Question:** Is the DELETE operation idempotent in your implementation? Provide a detailed justification by describing what happens if a client mistakenly sends the exact same DELETE request for a room multiple times.

**Yes, DELETE is idempotent in this implementation**, but with a careful qualification.

The HTTP specification defines idempotency as: calling the same operation multiple times produces the same **server state** as calling it once. It does **not** require that successive calls return identical status codes.

In this implementation:
- **First DELETE** of a valid, empty room → removes the room from `DataStore.rooms` → returns `200 OK`.
- **Second DELETE** of the same room ID → `DataStore.rooms.get(roomId)` returns `null` → returns `404 Not Found`.

The server state is identical after the first and second call (the room is gone in both cases), so the operation is idempotent at the **resource state** level. The different status code (`200` vs `404`) is acceptable and expected — this is standard RESTful behaviour. The client can safely retry a DELETE on network failure without fear of unintended side effects such as deleting the wrong resource or corrupting data.

**The 409 case:** If the room has sensors, the delete is rejected (`409 Conflict`) without modifying state — also idempotent because repeated attempts produce no state change until the blocking condition is resolved.

---

### Part 3.1 — Sensor Resource & Integrity

> **Question:** We explicitly use the `@Consumes(MediaType.APPLICATION_JSON)` annotation on the POST method. Explain the technical consequences if a client attempts to send data in a different format, such as `text/plain` or `application/xml`. How does JAX-RS handle this mismatch?

The `@Consumes(MediaType.APPLICATION_JSON)` annotation declares that the POST endpoint only accepts requests with a `Content-Type: application/json` header.

**What happens when a client sends `Content-Type: text/plain` or `Content-Type: application/xml`:**

JAX-RS performs **content negotiation** before executing any resource method. When the runtime receives a request, it compares the request's `Content-Type` header against the `@Consumes` value declared on every candidate method. If no method matches, Jersey immediately returns:

```
HTTP/1.1 415 Unsupported Media Type
```

The resource method body is **never invoked** — the rejection happens at the framework routing layer. This is the correct and standards-compliant behaviour. No custom code is needed to handle this; the `@Consumes` annotation alone enforces the contract.

This ensures the Jackson deserializer is only called with content it can safely parse, preventing `JsonParseException` or `UnrecognizedPropertyException` that might otherwise propagate as unhandled 500 errors.

---

### Part 3.2 — Filtered Retrieval & Search

> **Question:** You implemented this filtering using `@QueryParam`. Contrast this with an alternative design where the type is part of the URL path (e.g., `/api/v1/sensors/type/CO2`). Why is the query parameter approach generally considered superior for filtering and searching collections?

**Path segment approach:** `/api/v1/sensors/type/CO2`  
**Query parameter approach:** `/api/v1/sensors?type=CO2` ← this implementation

**Reasons query parameters are the better design for filtering:**

1. **REST resource identity** — A path segment implies a distinct, addressable resource. `/sensors/type/CO2` implies that `type/CO2` is a standalone resource, which it is not — it is merely a filtered view of the `/sensors` collection. Using a path segment violates the principle that paths identify resources, not operations.

2. **Optional filtering** — Query parameters are inherently optional. `GET /api/v1/sensors` returns all sensors, while `GET /api/v1/sensors?type=CO2` narrows the result. With path segments you would need a separate route definition for the unfiltered case, or handle the absence awkwardly.

3. **Multiple simultaneous filters** — Query parameters compose naturally: `?type=CO2&status=ACTIVE`. Path segments for multiple filters produce deeply nested, unreadable URLs (`/sensors/type/CO2/status/ACTIVE`) and require increasingly complex `@Path` patterns.

4. **Caching semantics** — `/api/v1/sensors` has a stable, cacheable URL for the full collection. Search/filter variants with query parameters are conventionally treated as ephemeral views and are not cached by intermediate proxies, which is the desired behaviour.

5. **RFC 3986 convention** — The URI specification itself distinguishes the path (resource hierarchy) from the query string (further qualification of the resource). Filtering is qualification, not hierarchy.

---

### Part 4.1 — The Sub-Resource Locator Pattern

> **Question:** Discuss the architectural benefits of the Sub-Resource Locator pattern. How does delegating logic to separate classes help manage complexity in large APIs compared to defining every nested path (e.g., `sensors/{id}/readings/{rid}`) in one massive controller class?

In JAX-RS, a **sub-resource locator** is a resource method that returns an object instance rather than a `Response`. The runtime then uses that object as the handler for the remaining URL path segments.

In this project:
```java
@Path("/{sensorId}/readings")
public SensorReadingResource getReadingResource(@PathParam("sensorId") String sensorId) {
    // validate sensorId exists, then:
    return new SensorReadingResource(sensorId);
}
```

**Benefits of this pattern over a single monolithic controller:**

1. **Separation of concerns** — `SensorResource` handles sensor-level operations. `SensorReadingResource` handles reading-level operations. Each class has a single responsibility, making both easier to read, test, and maintain.

2. **Contextual state** — The `SensorReadingResource` receives `sensorId` in its constructor, so every method in that class already knows which sensor it is operating on. There is no need to pass `sensorId` as a parameter to every method or to look it up repeatedly.

3. **Scalability** — As the API grows (e.g., `/api/v1/sensors/{id}/calibrations`, `/api/v1/sensors/{id}/alerts`), each sub-resource is a separate class. One massive controller class would grow unmanageable, with hundreds of methods, unclear ownership, and higher risk of merge conflicts in a team environment.

4. **Testability** — `SensorReadingResource` can be unit-tested in isolation by simply constructing it with a known `sensorId`, without needing to set up the entire `SensorResource` routing chain.

5. **Validation at the boundary** — The locator method is the natural place to check that the parent resource (`sensorId`) exists before delegating. If it does not, a `404` is returned immediately, and the sub-resource class never needs to repeat this guard.

---

### Part 5.2 — Dependency Validation (422 Unprocessable Entity)

> **Question:** Why is HTTP 422 often considered more semantically accurate than a standard 404 when the issue is a missing reference inside a valid JSON payload?

When a client POSTs a new sensor with a `roomId` that does not exist in the system, returning `404 Not Found` would be **misleading**.

- `404 Not Found` means the resource identified by the **request URI** was not found — i.e., `POST /api/v1/sensors` does not exist. But that endpoint clearly exists; the request reached it and was processed.

- The actual problem is that the **payload itself is invalid** — it contains a reference (`roomId`) to a resource that cannot be resolved. The request was syntactically correct JSON and reached the correct endpoint, but the business logic cannot fulfil it because of a broken reference inside the body.

- **`422 Unprocessable Entity`** was designed for exactly this scenario: the server understands the request format and content type, but the contained instructions cannot be processed due to semantic errors. A broken foreign-key reference inside an otherwise valid JSON payload is a semantic error, not a routing error.

This distinction matters to API consumers: `404` tells them "you called the wrong URL", while `422` tells them "your URL was right, your JSON was valid, but the data inside it refers to something that doesn't exist — check the `roomId` field."

---

### Part 5.4 — The Global Safety Net (500)

> **Question:** From a cybersecurity standpoint, explain the risks associated with exposing internal Java stack traces to external API consumers. What specific information could an attacker gather from such a trace?

Returning raw Java stack traces in API error responses is a serious security vulnerability. The information they reveal can be directly exploited:

1. **Technology fingerprinting** — A stack trace reveals the exact framework versions in use (e.g., `org.glassfish.jersey 2.41`, `org.eclipse.jetty 9.4.51`). An attacker can cross-reference these against public CVE databases to find known, unpatched vulnerabilities for that specific version and craft targeted exploits.

2. **Internal package structure disclosure** — Stack traces expose fully qualified class names, package hierarchies, and method names (e.g., `com.smartcampus.application.DataStore.rooms`). This gives attackers a map of the application's internal architecture, reducing the effort required to craft injection attacks or understand where sensitive operations occur.

3. **File system path disclosure** — Compiled stack traces often include source file paths, revealing OS type, directory layout, and server configuration conventions. This aids privilege escalation attacks on a compromised system.

4. **Business logic leakage** — Method names and call chains reveal the order in which business operations are executed, allowing an attacker to infer which inputs or sequences of requests trigger specific code paths that might contain vulnerabilities.

5. **SQL / query structure disclosure** — If an ORM or query builder is in use, stack traces frequently include the query that caused an exception, enabling injection reconnaissance.

**Mitigation (implemented in this project):**  
The `GlobalExceptionMapper<Throwable>` intercepts all unexpected exceptions, logs the full stack trace **server-side only** using `java.util.logging.Logger`, and returns a generic, non-informative `500` response body to the client. The client learns only that an internal error occurred — not why, where, or how.

---

### Part 5.5 — API Request & Response Logging Filters

> **Question:** Why is it advantageous to use JAX-RS filters for cross-cutting concerns like logging, rather than manually inserting `Logger.info()` statements inside every single resource method?

Using JAX-RS filters (`ContainerRequestFilter` / `ContainerResponseFilter`) for logging provides several key advantages over manually adding log statements to each resource method:

1. **Single point of control** — All request and response logging lives in one class (`LoggingFilter`). If the logging format needs to change (e.g., adding a correlation ID or timestamp), you update one file instead of hunting through every resource method across the entire codebase.

2. **No code duplication** — Manually adding `Logger.info()` to every method violates the DRY (Don't Repeat Yourself) principle. With tens of endpoints, this becomes error-prone — developers forget to add logging to new methods or accidentally remove it during refactoring.

3. **Separation of concerns** — Resource methods should focus exclusively on their business logic (creating rooms, reading sensors). Mixing in logging statements pollutes the method body with infrastructure code that has nothing to do with the method's purpose, reducing readability and maintainability.

4. **Guaranteed coverage** — A filter registered with JAX-RS is invoked for **every** request and response automatically, including requests that are rejected at the routing layer (e.g., 415 Unsupported Media Type). Manual logging inside resource methods would miss these cases entirely because the method body is never reached.

5. **Consistent log format** — Filters ensure that every log entry follows the same structure (method + URI on request, status code on response), making logs machine-parseable and easier to feed into monitoring tools like ELK Stack or Splunk.
