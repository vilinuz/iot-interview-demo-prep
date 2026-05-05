# LEGIC Backend Engineering Portfolio

This repository contains a curated collection of Java backend patterns and implementations tailored to demonstrate modern, high-performance, and secure backend engineering principles. 

The project is built on **Java 25 (with preview features)** and leverages core JDK capabilities, minimizing reliance on heavy frameworks to showcase strong foundational knowledge of the JVM, memory models, and networking.

## Tech Stack

* **Language:** Java 25 (utilizing Virtual Threads, Structured Concurrency, Scoped Values, Pattern Matching, and the FFM API)
* **Build Tool:** Apache Maven
* **Dependencies:** RabbitMQ Client, HikariCP, PostgreSQL JDBC, Guava, Project Reactor

---

## Architectural Patterns & Implementations

### 1. High-Performance Concurrency & Virtual Threads

* **`SensorAggregator`** (`com.legic.interview.concurrency`): Demonstrates advanced Structured Concurrency using Java 25's `StructuredTaskScope.Joiner` API. Implements fan-out requests, redundant fast-failover (fetch from multiple sensors and return the first successful), and strict hard-deadline enforcement without thread leaks. Uses `ScopedValue` for lightweight context propagation.
* **`SpscRingBuffer`**: A zero-allocation, lock-free Single-Producer Single-Consumer (SPSC) ring buffer queue optimized via fine-grained memory fences using `VarHandle`.
* **`SensorIngestionServer`** (`com.legic.interview.ingestion`): A high-throughput HTTP ingestion pipeline utilizing bounded `LinkedBlockingDeque` for natural backpressure and Virtual Threads to handle high concurrency with minimal RAM overhead.

### 2. Device Connectivity & Protocol Parsing

* **`ZeroAllocationParser`** (`com.legic.interview.protocol`): Showcases zero-copy, garbage-free binary payload parsing leveraging the Java 21+ Foreign Function & Memory (FFM) API (`MemorySegment`, `ValueLayout`).
* **`WiegandParser`**: Low-level bitwise manipulation for decoding Wiegand physical access hardware protocols (e.g., 26-bit formats).
* **`HardwareDebouncer`** (`com.legic.interview.ingestion`): Hardware signal debouncing implemented using both Project Reactor (`sample`) and Java Virtual Threads.

### 3. Security, mTLS & Cryptography

* **`MtlsGatewayServer`** (`com.legic.interview.mtls`): A high-security IoT device gateway implementing Mutual TLS (mTLS). It enforces `setNeedClientAuth(true)`, verifies client certificate chains against trusted CAs, and implements strict SHA-256 certificate pinning to mitigate compromised CA risks. Extracts identity directly from the X.509 `CN` (Common Name).
* **`HighScaleNonceValidator`** (`com.legic.interview.crypto`): Scalable cryptographic nonce validation. Leverages a Guava `BloomFilter` for an ultra-fast local path (avoiding network I/O) before falling back to Redis for cross-node synchronization.

### 4. Distributed Systems & Exactly-Once Semantics

* **`CommandDispatcher` & `CommandConsumer`** (`com.legic.interview.dispatch`): RabbitMQ integration demonstrating exactly-once event processing. Utilizes the Transactional Outbox pattern, idempotent database updates (`ON CONFLICT DO NOTHING`), and pessimistic locking to handle concurrent retries safely in a distributed cluster.

### 5. API Rate Limiting & Flow Control

* **`RateLimitFilter`** (`com.legic.interview.ratelimit`): A Java `HttpServer` middleware filter chaining global token buckets and per-connection sliding window rate limiters to protect backend systems from overload.
* **`LazyTokenBucket`**: A high-performance, lock-free global rate limiter using `AtomicLong` CAS loops and lazy time-based refills.
* **`SlidingWindowCounter`**: A lock-free anomaly detector utilizing arrays of `LongAdder` mapped to real-time seconds.

### 6. Domain Driven Design (DDD) & Modern Java Syntax

* **`EventProcessor`** (`com.legic.interview.domain`): Dispatches domain events using Java's exhaustive `switch` expressions and type pattern matching on sealed interfaces.
* **`AntiPassbackController`**: Lock-free business state management using `ConcurrentHashMap` atomic operations to enforce access control rules (e.g., preventing users from entering a zone twice without exiting).

### 7. Kubernetes / Cloud-Native Integration

* **`HealthAwareServer`** (`com.legic.interview.k8s`): Native JVM integration with Kubernetes lifecycle events. Implements distinct `/health/live` and `/health/ready` endpoints, tracking in-flight requests for clean, graceful shutdowns via `Runtime.addShutdownHook()`.

---

## Getting Started

### Prerequisites

* GraalVM or standard JDK 25
* Apache Maven 3.9+

### Building the Project
To compile the project and download all dependencies, run:
```bash
mvn clean install
```
*(Note: Because the codebase uses Java 25 preview APIs like `StructuredTaskScope`, the `--enable-preview` compiler flag is automatically injected via the `pom.xml`.)*
