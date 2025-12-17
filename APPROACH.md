# QuickLink - Implementation Approach & Design Decisions

This document captures the architectural decisions, trade-offs, and learnings throughout the implementation of QuickLink URL Shortener.

---

## Table of Contents
1. [Project Overview](#project-overview)
2. [Design Decisions](#design-decisions)
3. [Implementation Phases](#implementation-phases)
4. [Trade-offs](#trade-offs)
5. [Learnings](#learnings)

---

## Project Overview

**Goal:** Build a production-ready, serverless URL shortener demonstrating system design thinking and AWS expertise.

**Tech Stack:**
- Backend: Java 17 + Spring Boot 3.2
- Database: Amazon DynamoDB
- Compute: AWS Lambda
- API: Amazon API Gateway
- Queue: Amazon SQS (for analytics)

---

## Design Decisions

### 1. Domain Model Design

#### Decision: Use `shortCode` as Primary Key (Not `shortUrl`)

**Rationale:**
- Domain names can change over time (e.g., `skt.inc` → `go.skt.inc`)
- Storing full URL wastes storage (31 bytes vs 7 bytes per record)
- Domain is configuration, not data
- Enables environment-specific URLs (localhost, staging, production)

**Trade-off:**
- ✅ Flexibility: Can change domain without database migration
- ✅ Storage efficiency: 77% reduction in PK size
- ✅ Environment independence: Same data works across all environments
- ❌ Need to build `shortUrl` dynamically in application layer

**Implementation:**
```java
// Stored in DB
String shortCode = "aB3xY9z";

// Built dynamically
String shortUrl = baseUrl + "/" + shortCode;
// = "https://skt.inc/aB3xY9z"
```

---

### 2. Base62 Encoding for Short Codes

#### Decision: Use Base62 (0-9, a-z, A-Z) for 7-character codes

**Rationale:**
- URL-safe characters only (no special chars like `+`, `/`, `=`)
- Compact representation: 62^7 = 3.5 trillion unique codes
- Collision-free: Each ID maps to exactly one short code
- Reversible: Can decode back to original ID if needed

**Trade-off:**
- ✅ No collisions (deterministic mapping)
- ✅ Efficient: 7 characters support massive scale
- ✅ Human-readable: No confusing special characters
- ❌ Sequential IDs are predictable (security consideration for future)

**Capacity:**
```
7 characters × 62 possibilities = 62^7 = 3,521,614,606,208 URLs
At 1M URLs/day = 9,645 years of capacity
```

---

### 3. Domain Selection: `skt.inc` vs `quicklink.skt.inc`

#### Decision: Use `skt.inc` (root domain)

**Rationale:**
- Shorter URLs are better for a URL shortener (core value proposition)
- More professional and memorable
- Easier to share verbally and in print
- Follows industry pattern (bit.ly, youtu.be, t.co)

**Trade-off:**
- ✅ Shorter: `skt.inc/abc` vs `quicklink.skt.inc/abc`
- ✅ Professional appearance
- ✅ Easier to remember
- ⚠️ Need careful routing (7-char codes vs static pages)

**Routing Strategy:**
```
https://skt.inc/           → Main website (if exists)
https://skt.inc/about      → Static page
https://skt.inc/aB3xY9z    → URL redirect (7-char base62 pattern)
```

---

### 4. equals() and hashCode() Implementation

#### Decision: Implement based on `shortCode` (primary key)

**Rationale:**
- DynamoDB handles uniqueness at database level
- Needed for Java collections (HashSet, HashMap)
- Essential for unit testing (object comparison)
- Follows best practice: equals/hashCode based on business key

**Trade-off:**
- ✅ Enables proper collection usage
- ✅ Simplifies testing
- ✅ Matches database uniqueness constraint
- ❌ Minimal overhead (simple implementation)

**Implementation:**
```java
@Override
public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    UrlMapping that = (UrlMapping) o;
    return shortCode != null ? shortCode.equals(that.shortCode) : that.shortCode == null;
}

@Override
public int hashCode() {
    return shortCode != null ? shortCode.hashCode() : 0;
}
```

---

## Implementation Phases

### Phase 1: Foundation ✅ COMPLETED
- [x] Base62Encoder utility (ID → short code conversion)
- [x] UrlMapping domain model (DynamoDB entity)
- [x] ShortenRequest DTO (API input)
- [x] ShortenResponse DTO (API output)
- [x] HealthController (health check endpoint)

**Key Learnings:**
- Base62 encoding is similar to decimal-to-binary conversion
- Domain model should be storage-agnostic (no DynamoDB annotations yet)
- DTOs provide clean API contracts separate from domain models

---

### Phase 2: Repository Layer (IN PROGRESS)
- [ ] InMemoryUrlRepository (local testing)
- [ ] UrlRepository interface (abstraction)
- [ ] DynamoDbUrlRepository (production implementation)

**Approach:**
- Start with in-memory HashMap for rapid local development
- Define repository interface for abstraction
- Implement DynamoDB version later (after AWS setup)

---

### Phase 3: Service Layer (UPCOMING)
- [ ] TokenService (ID generation)
- [ ] UrlService (business logic)
- [ ] Async analytics (SQS integration)

---

### Phase 4: Controller Layer (UPCOMING)
- [ ] UrlController (POST /shorten, GET /{shortCode})
- [ ] Exception handling (@ControllerAdvice)
- [ ] Input validation

---

### Phase 5: AWS Integration (UPCOMING)
- [ ] DynamoDB configuration
- [ ] SQS configuration
- [ ] Lambda deployment
- [ ] API Gateway setup
- [ ] Custom domain (skt.inc)

---

## Trade-offs

### 1. Spring Boot vs Lightweight Frameworks

**Decision:** Use Spring Boot

**Trade-off:**
- ❌ Cold start: 5-10 seconds (vs 100-500ms for Quarkus/Micronaut)
- ✅ Expertise: Demonstrates Spring Boot mastery
- ✅ Job market: Most in-demand framework
- ✅ Ecosystem: Rich library support

**Mitigation:**
- Document cold start trade-offs
- Mention Spring Native + SnapStart for production
- Show architectural thinking (not just coding)

---

### 2. In-Memory Storage First vs Direct DynamoDB

**Decision:** Start with in-memory HashMap

**Trade-off:**
- ✅ Faster local development (no AWS setup needed)
- ✅ Easier testing (no external dependencies)
- ✅ Clear separation of concerns (repository pattern)
- ❌ Need to implement DynamoDB version later

**Benefit:**
- Can test business logic immediately
- Swap implementations easily (dependency injection)
- Demonstrates proper abstraction

---

### 3. Storing shortUrl vs Computing It

**Decision:** Compute dynamically from `baseUrl + shortCode`

**Trade-off:**
- ✅ Storage savings: 77% reduction
- ✅ Flexibility: Change domain without migration
- ✅ Environment independence
- ❌ Slight CPU overhead (negligible string concatenation)

**Calculation:**
```
100M URLs:
- Storing shortUrl: 3.1 GB
- Storing shortCode: 700 MB
- Savings: 2.4 GB (77%)
```

---

## Learnings

### 1. Base62 Encoding Deep Dive

**Key Insight:** Base62 is just base conversion (like decimal to binary)

**Example:**
```
ID 125 → Base62:
125 % 62 = 1 → '1'
125 / 62 = 2
2 % 62 = 2 → '2'
2 / 62 = 0
Result (reversed): "21" → Padded: "0000021"
```

**Why It Works:**
- Modulo gives the "digit" in base62
- Division moves to next position
- Reverse because division produces digits backwards

---

### 2. Domain vs Short URL Confusion

**Clarification:**
- **Domain**: Website address (e.g., `skt.inc`) - You configure/buy this
- **Short Code**: Generated identifier (e.g., `aB3xY9z`) - Application generates this
- **Short URL**: Complete URL (e.g., `https://skt.inc/aB3xY9z`) - Combination of both

**Key Takeaway:** We generate the short code, not the domain.

---

### 3. equals() vs hashCode()

**Key Insight:** They are independent but must be consistent

**Contract:**
- If `a.equals(b)` is true, then `a.hashCode() == b.hashCode()` MUST be true
- Used together by HashMap/HashSet for efficient lookups

**How HashMap Uses Both:**
1. Call `hashCode()` to find bucket (fast)
2. Call `equals()` to find exact object in bucket (accurate)

---

### 4. Real-World URL Shortener Use Cases

**Who Uses Custom URL Shorteners:**
1. **Companies (Internal)**: Marketing, sales, HR link tracking
2. **SaaS Products**: Bitly, TinyURL (offer as service)
3. **Platforms**: Twitter (t.co), YouTube (youtu.be)
4. **Events**: Conference-specific shorteners

**Our Project:** Portfolio demonstration + potential internal tool

---

## Next Steps

1. Implement InMemoryUrlRepository for local testing
2. Create TokenService for ID generation
3. Build UrlService with business logic
4. Add UrlController for REST endpoints
5. Test locally with in-memory storage
6. Integrate DynamoDB after AWS deployment
7. Configure custom domain (skt.inc)

---

## Questions & Decisions Log

### Q: Why not use shortUrl as primary key?
**A:** Domain can change; storing shortCode is more flexible and efficient.

### Q: Why Base62 instead of random strings?
**A:** Collision-free, deterministic, and efficient. No need for collision checking.

### Q: Should we use `skt.inc` or `quicklink.skt.inc`?
**A:** `skt.inc` - shorter is better for URL shortener.

### Q: Do we need equals() and hashCode()?
**A:** Yes, for collections and testing. DynamoDB handles uniqueness separately.

---

**Last Updated:** [Current Date]
**Status:** Phase 1 Complete, Phase 2 In Progress
