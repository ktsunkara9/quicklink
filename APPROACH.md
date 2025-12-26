# QuickLink - Implementation Approach & Design Decisions

This document captures the architectural decisions, trade-offs, and learnings throughout the implementation of QuickLink URL Shortener.

---

## Table of Contents
1. [Design Decisions](#design-decisions)
2. [Implementation Learnings](#implementation-learnings)
3. [Caching Strategies](#caching-strategies)
4. [Spring Profiles](#spring-profiles)
5. [AWS & Infrastructure](#aws--infrastructure)

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

**How Base62 Encoding Works:**

Base62 is just base conversion (like decimal to binary).

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

**How HashMap Uses Both:**
1. Call `hashCode()` to find bucket (fast)
2. Call `equals()` to find exact object in bucket (accurate)

**Contract:**
- If `a.equals(b)` is true, then `a.hashCode() == b.hashCode()` MUST be true

---

### 5. Spring Boot vs Lightweight Frameworks

#### Decision: Use Spring Boot

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

### 6. Architecture Decision: Single Lambda vs Separate Lambdas

#### Decision: Use Single Lambda with Multiple Services ✅

**Rationale:**
- **Performance**: No Lambda-to-Lambda invocation latency (50-100ms saved)
- **Cost**: No additional Lambda invocation charges
- **Simplicity**: Easier deployment and debugging
- **Caching**: TokenService range caching works optimally in single Lambda
- **Industry Standard**: Most URL shorteners use single service architecture

**Architecture:**
```
API Gateway
    ↓
┌─────────────────────────────────┐
│  Lambda: URL Shortener          │
│  ├─ UrlController               │
│  ├─ UrlService                  │
│  ├─ TokenService (in-process)   │
│  └─ Repositories                │
└─────────┬───────────────────────┘
          ↓
    DynamoDB (both tables)
```

**Trade-offs:**
- ✅ Lower latency (in-memory method calls)
- ✅ Lower cost (fewer Lambda invocations)
- ✅ Simpler infrastructure
- ✅ Better caching efficiency
- ⚠️ Larger Lambda package size (acceptable)
- ⚠️ Can't scale TokenService independently (not needed at our scale)

---

## Implementation Learnings

### 1. Domain vs Short URL Confusion

**Clarification:**
- **Domain**: Website address (e.g., `skt.inc`) - You configure/buy this
- **Short Code**: Generated identifier (e.g., `aB3xY9z`) - Application generates this
- **Short URL**: Complete URL (e.g., `https://skt.inc/aB3xY9z`) - Combination of both

**Key Takeaway:** We generate the short code, not the domain.

---

### 2. Storing shortUrl vs Computing It

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

### 3. Real-World URL Shortener Use Cases

**Who Uses Custom URL Shorteners:**
1. **Companies (Internal)**: Marketing, sales, HR link tracking
2. **SaaS Products**: Bitly, TinyURL (offer as service)
3. **Platforms**: Twitter (t.co), YouTube (youtu.be)
4. **Events**: Conference-specific shorteners

**Our Project:** Portfolio demonstration + potential internal tool

---

## Caching Strategies

### 1. In-Memory Caching (Current Implementation)

#### TokenService Range Caching

**Implementation:**
```java
@Service
public class TokenService {
    private final Queue<Long> tokenCache = new ConcurrentLinkedQueue<>();
    private static final int BATCH_SIZE = 100;
    
    public Long getNextToken() {
        if (tokenCache.isEmpty()) {
            refillCache();
        }
        return tokenCache.poll();
    }
}
```

**How It Works:**
- Pre-fetches 100 tokens at startup
- Refills when cache is empty
- Uses atomic DynamoDB counter increment
- Thread-safe with ConcurrentLinkedQueue

**Benefits:**
- ✅ Eliminates DynamoDB calls for 99% of requests
- ✅ Reduces latency from 50ms to <1ms
- ✅ Cost efficient (fewer DynamoDB operations)
- ✅ Simple implementation

**Limitations:**
- ❌ Tokens lost on Lambda cold start
- ❌ No sharing between Lambda instances
- ❌ Cache size limited by Lambda memory

---

### 2. Redis Distributed Caching

#### URL Lookup Caching

**Implementation:**
```java
@Service
public class UrlService {
    @Cacheable(value = "urls", key = "#shortCode")
    public UrlEntity findByShortCode(String shortCode) {
        return urlRepository.findByShortCode(shortCode);
    }
}
```

**Configuration:**
```yaml
spring:
  cache:
    type: redis
  redis:
    host: elasticache-cluster.aws.com
    port: 6379
    timeout: 2000ms
```

**Benefits:**
- ✅ Shared cache across all Lambda instances
- ✅ Persistent across cold starts
- ✅ Sub-millisecond lookup times
- ✅ Automatic expiration policies
- ✅ Handles high-traffic URLs efficiently

**Limitations:**
- ❌ Additional infrastructure cost (~$15/month)
- ❌ Network latency (1-3ms)
- ❌ Cache invalidation complexity
- ❌ Potential cache stampede on popular URLs

**Use Case:** High-traffic production systems with frequent URL lookups

---

### 3. DynamoDB DAX (DynamoDB Accelerator)

#### Transparent Database Caching

**Implementation:**
```java
@Configuration
public class DynamoDbConfig {
    @Bean
    public DynamoDbClient dynamoDbClient() {
        return DaxClientBuilder.standard()
            .endpointConfiguration("dax-cluster.aws.com:8111")
            .build();
    }
}
```

**Benefits:**
- ✅ Transparent caching (no code changes)
- ✅ Microsecond response times
- ✅ Automatic cache management
- ✅ Write-through caching
- ✅ Multi-AZ availability

**Limitations:**
- ❌ Expensive (~$200/month minimum)
- ❌ Only works with DynamoDB
- ❌ Limited cache size per node
- ❌ Overkill for small applications

**Use Case:** Enterprise applications with massive DynamoDB workloads

---

### 4. Application-Level LRU Cache

#### Hot URL Caching

**Implementation:**
```java
@Component
public class UrlCache {
    private final Map<String, UrlEntity> cache = 
        Collections.synchronizedMap(new LinkedHashMap<String, UrlEntity>(1000, 0.75f, true) {
            protected boolean removeEldestEntry(Map.Entry<String, UrlEntity> eldest) {
                return size() > 1000;
            }
        });
    
    public UrlEntity get(String shortCode) {
        return cache.get(shortCode);
    }
}
```

**Benefits:**
- ✅ Zero external dependencies
- ✅ Automatic eviction of old entries
- ✅ Memory efficient
- ✅ Fast access for popular URLs

**Limitations:**
- ❌ Lost on Lambda restart
- ❌ No sharing between instances
- ❌ Fixed memory footprint
- ❌ Manual cache management

**Use Case:** Medium-traffic applications with predictable access patterns

---

### 5. CloudFront Edge Caching (Recommended for URL Shorteners)

#### Caching API Redirects at Edge Locations

**Implementation:**
```python
# CDK Configuration
cloudfront.Distribution(
    self, "QuickLinkCDN",
    default_behavior=cloudfront.BehaviorOptions(
        origin=origins.RestApiOrigin(api_gateway),
        cache_policy=cloudfront.CachePolicy.CACHING_OPTIMIZED,
        viewer_protocol_policy=cloudfront.ViewerProtocolPolicy.REDIRECT_TO_HTTPS
    )
)
```

**Architecture:**
```
User (Tokyo) → CloudFront Edge (Tokyo) → Cache Hit → 301 Redirect (50ms)
User (London) → CloudFront Edge (London) → Cache Miss → API Gateway (us-east-1) → Lambda → 301 (200ms)
User (London) → CloudFront Edge (London) → Cache Hit → 301 Redirect (50ms)
```

**Benefits:**
- ✅ 450+ global edge locations (sub-100ms worldwide)
- ✅ Reduces Lambda invocations by 80-95% (massive cost savings)
- ✅ Built-in DDoS protection (AWS Shield)
- ✅ Automatic SSL/TLS termination
- ✅ Perfect for URL shorteners (immutable redirects)
- ✅ Industry standard (bit.ly, TinyURL use CDN)

**Performance:**
```
Without CloudFront:
- Global latency: 200-500ms (distance to AWS region)
- Lambda invocations: 1M/month
- Cost: $10.40/month

With CloudFront:
- Global latency: 50-100ms (nearest edge)
- Lambda invocations: 50K-200K/month (80-95% cache hit)
- Cost: $2-3/month (Lambda) + $1/month (CloudFront) = $3-4/month
- Savings: 60-70% cost reduction
```

**Limitations:**
- ❌ Cache invalidation delay (5-15 minutes)
- ❌ Analytics delayed by cache TTL (use SQS for real-time)

**Why Best for URL Shorteners:**
- Redirects are immutable (URLs don't change after creation)
- Read-heavy workload (1000:1 read-to-write ratio)
- Global user base (not region-specific)
- Simple responses (just 301 redirects)

**Use Case:** Production URL shorteners with global traffic

---

### 6. Browser Caching

#### Client-Side Response Caching

**Implementation:**
```java
@GetMapping("/{shortCode}")
public ResponseEntity<Void> redirect(@PathVariable String shortCode) {
    String longUrl = urlService.getLongUrl(shortCode);
    
    return ResponseEntity.status(HttpStatus.MOVED_PERMANENTLY)
        .header("Location", longUrl)
        .header("Cache-Control", "public, max-age=3600")  // 1 hour
        .build();
}
```

**Benefits:**
- ✅ Zero server load for repeat visits
- ✅ Fastest possible response time
- ✅ Reduces bandwidth costs
- ✅ Works automatically

**Limitations:**
- ❌ User-specific (no sharing)
- ❌ Limited by browser cache size
- ❌ Can't invalidate remotely
- ❌ Not suitable for tracking analytics

**Use Case:** URLs that don't change and don't need click tracking

---

### Caching Strategy Comparison

| Strategy | Latency | Cost | Complexity | Persistence | Sharing | Best For |
|----------|---------|------|------------|-------------|---------|----------|
| **In-Memory** | <1ms | Free | Low | ❌ | ❌ | Token generation |
| **Redis** | 1-3ms | $15/mo | Medium | ✅ | ✅ | Dynamic data |
| **DAX** | <1ms | $200/mo | Low | ✅ | ✅ | Heavy DynamoDB workloads |
| **LRU Cache** | <1ms | Free | Medium | ❌ | ❌ | Hot URLs |
| **CloudFront** | 50-100ms | $1/mo | Medium | ✅ | ✅ | **URL redirects** ✅ |
| **Browser** | 0ms | Free | Low | ❌ | ❌ | Static content |

---

### Current Implementation Choice

**Selected:** In-Memory TokenService Caching

**Rationale:**
- Addresses token generation (highest-latency write operation)
- Zero additional infrastructure cost
- Simple to implement and maintain
- Sufficient for demonstration purposes

**Performance Impact:**
```
Without caching: 50ms (DynamoDB call per URL creation)
With caching: <1ms (99% cache hit rate)
Improvement: 50x faster token generation
```

**Production Recommendation:** Add CloudFront for redirect caching
- CloudFront is the best solution for URL shortener redirects
- Reduces global latency from 200-500ms to 50-100ms
- Cuts Lambda costs by 60-70% (80-95% cache hit rate)
- Industry standard for URL shorteners (bit.ly, TinyURL)
- See CloudFront section above for detailed analysis

---

## Spring Profiles

### How Spring Profiles Work

Spring Profiles allow the same codebase to use different implementations based on the environment.

### Local Development

```yaml
# application.yml
spring:
  profiles:
    active: local
```

**Beans loaded:**
- `InMemoryUrlRepository` ✅
- `InMemoryTokenRepository` ✅
- `DynamoDbUrlRepository` ❌ (not loaded)
- `DynamoDbTokenRepository` ❌ (not loaded)

**Result:** Application uses in-memory storage, no AWS needed!

---

### AWS Production Deployment

**Set environment variable in Lambda:**
```bash
SPRING_PROFILES_ACTIVE=prod
```

**Beans loaded:**
- `InMemoryUrlRepository` ❌ (not loaded)
- `InMemoryTokenRepository` ❌ (not loaded)
- `DynamoDbUrlRepository` ✅
- `DynamoDbTokenRepository` ✅

**Result:** Application uses DynamoDB storage automatically!

---

### How It Works

```java
// Local/Test - Only loaded when profile is "local", "test", or "default"
@Repository
@Profile({"default", "local", "test"})
public class InMemoryUrlRepository implements UrlRepository { }

// Production - Only loaded when profile is "prod" or "aws"
@Repository
@Profile({"prod", "aws"})
public class DynamoDbUrlRepository implements UrlRepository { }
```

**Spring automatically:**
1. Checks active profile
2. Loads only beans matching that profile
3. Injects the correct implementation

**No code changes needed!** Just set the environment variable.

---

### Summary

| Environment | Profile | Repository Used | AWS Needed? |
|-------------|---------|-----------------|-------------|
| **Local Dev** | `local` | InMemory | ❌ No |
| **Testing** | `test` | InMemory | ❌ No |
| **AWS Lambda** | `prod` | DynamoDB | ✅ Yes |

**Key Point:** Same code, different behavior based on profile!

---

## AWS & Infrastructure

### CDK Synthesis Issue on Windows

#### Problem
Running `cdk synth` created empty `cdk.out/` directory without CloudFormation templates.

#### Root Cause
1. `cdk.json` specified `"app": "python3 app.py"`
2. Windows doesn't have `python3` command (only `python`)
3. CDK CLI failed silently when it couldn't find the Python interpreter
4. Even when Python was found, it used system Python instead of venv's Python (which has `aws-cdk-lib`)

#### Solution
**Three-part fix:**

1. **Created run-app.cmd wrapper:**
```batch
@echo off
%~dp0.venv\Scripts\python.exe %~dp0app.py
```

2. **Updated cdk.json:**
```json
{
  "app": "run-app.cmd",
  "output": "cdk.out"
}
```

3. **Modified app.py to manually write templates:**
```python
cloud_assembly = app.synth()

# Manually write CloudFormation template
os.makedirs("cdk.out", exist_ok=True)
for stack_artifact in cloud_assembly.stacks:
    template_file = os.path.join("cdk.out", f"{stack_artifact.stack_name}.template.json")
    with open(template_file, "w") as f:
        json.dump(stack_artifact.template, f, indent=2)
```

#### Key Learnings
- CDK CLI (npm package) and Python CDK library are separate components
- CDK CLI needs explicit path to correct Python interpreter on Windows
- `app.synth()` validates but doesn't write files unless called by CDK CLI
- Batch wrapper ensures venv's Python is used consistently
- Manual template writing provides fallback when CDK CLI doesn't write output

---

### AWS Serverless Java Container - Bridging Spring Boot and Lambda

#### The Problem
Spring Boot and AWS Lambda are fundamentally incompatible:
- **Spring Boot** expects to run as a standalone web server with embedded Tomcat
- **AWS Lambda** expects a handler method: `handleRequest(input, output, context)`
- API Gateway sends requests as JSON (`AwsProxyRequest`), not HTTP

#### The Solution: AWS Serverless Java Container

**Dependency:**
```xml
<dependency>
    <groupId>com.amazonaws.serverless</groupId>
    <artifactId>aws-serverless-java-container-springboot3</artifactId>
    <version>2.0.3</version>
</dependency>
```

#### What It Does

1. **Initializes Spring Context** - Starts Spring Boot application inside Lambda
2. **Request Translation** - Converts API Gateway `AwsProxyRequest` → Spring `HttpServletRequest`
3. **Response Translation** - Converts Spring `HttpServletResponse` → API Gateway `AwsProxyResponse`
4. **Lifecycle Management** - Keeps Spring context warm across Lambda invocations (reduces cold starts)

#### Without This Library

You'd need to manually:
```java
// Initialize Spring ApplicationContext
ApplicationContext context = SpringApplication.run(QuickLinkApplication.class);

// Parse API Gateway JSON
AwsProxyRequest request = objectMapper.readValue(input, AwsProxyRequest.class);

// Route to correct controller
if (request.getPath().equals("/api/v1/shorten")) {
    // Call UrlController.shortenUrl()
}

// Serialize response back to API Gateway format
AwsProxyResponse response = new AwsProxyResponse(200, headers, body);
```

#### With This Library

```java
// Just 3 lines in handler:
handler = SpringBootLambdaContainerHandler.getAwsProxyHandler(QuickLinkApplication.class);
handler.proxyStream(input, output, context);
// Done! All Spring controllers work automatically
```

#### Key Benefits

- **Zero controller changes** - Existing `@RestController` classes work as-is
- **Standard Spring features** - Dependency injection, exception handling, validation all work
- **Reduced boilerplate** - No manual request/response mapping
- **Production-ready** - Used by AWS in official examples

#### Trade-off: Cold Start Impact

**Cost:** Adds ~2-3 seconds to cold start (Spring context initialization)

**Mitigation strategies:**
- Use Provisioned Concurrency for critical endpoints
- Consider Spring Native (GraalVM) for production
- Keep Lambda warm with scheduled health checks

**Decision:** Acceptable for demo/portfolio project to showcase Spring Boot expertise

---

### Lambda Container Lifecycle and Static Spring Context

#### How Lambda Containers Work

AWS Lambda doesn't create a new container for every request. Instead, it reuses containers across multiple invocations to improve performance.

**Container Lifecycle:**
```
┌─────────────────────────────────────────────────┐
│ Lambda Container (stays alive 5-15 minutes)     │
├─────────────────────────────────────────────────┤
│ COLD START (first invocation)                   │
│ 1. Load JAR                                     │
│ 2. Run static {} block → Initialize Spring      │ ← 5-10 seconds
│ 3. Execute handleRequest()                      │ ← 50-200ms
├─────────────────────────────────────────────────┤
│ WARM INVOCATION #2                              │
│ 1. Skip static {} (already initialized)         │
│ 2. Execute handleRequest()                      │ ← 50-200ms
├─────────────────────────────────────────────────┤
│ WARM INVOCATION #3                              │
│ 1. Skip static {} (already initialized)         │
│ 2. Execute handleRequest()                      │ ← 50-200ms
├─────────────────────────────────────────────────┤
│ ... (container stays warm for ~5-15 min)        │
└─────────────────────────────────────────────────┘
```

#### Static Spring Context in StreamLambdaHandler

```java
public class StreamLambdaHandler implements RequestStreamHandler {
    
    private static SpringBootLambdaContainerHandler<AwsProxyRequest, AwsProxyResponse> handler;

    static {
        // This runs ONCE per container, not per request
        handler = SpringBootLambdaContainerHandler.getAwsProxyHandler(QuickLinkApplication.class);
    }

    @Override
    public void handleRequest(InputStream input, OutputStream output, Context context) {
        // This runs for EVERY request, reusing the same Spring context
        handler.proxyStream(input, output, context);
    }
}
```

#### Why Static Initialization?

**Benefits:**
1. **Spring context initialized once** - Expensive operation (5-10s) happens only on cold start
2. **Beans are reused** - Same service instances, repositories, configurations across requests
3. **State persists** - TokenService's cached ID range survives across invocations
4. **Faster warm requests** - Subsequent requests take 50-200ms instead of 5-10s

**How It Works:**
- **Cold start:** Static block runs → Spring context created → Request processed
- **Warm requests:** Static block skipped → Existing Spring context reused → Request processed

#### Impact on TokenService

**TokenService allocates 100 IDs at a time and caches them in memory:**

```java
@Service
public class TokenService {
    private long currentId;
    private long rangeEnd;
    
    public synchronized long getNextId() {
        if (currentId >= rangeEnd) {
            allocateNewRange();  // DynamoDB call every 100 requests
        }
        return currentId++;
    }
}
```

**With static Spring context:**
- Request 1 (cold): Allocate range 1-100, return 1
- Request 2 (warm): Return 2 (no DynamoDB call)
- Request 3 (warm): Return 3 (no DynamoDB call)
- ...
- Request 100 (warm): Return 100 (no DynamoDB call)
- Request 101 (warm): Allocate range 101-200, return 101

**Result:** Only 1 DynamoDB call per 100 requests instead of 100 calls.

#### Container Termination

**When container dies (after 5-15 min inactivity):**
- Spring context is destroyed
- TokenService cache is lost
- Unused IDs in range are wasted (acceptable trade-off)
- Next request triggers cold start

#### Lambda Timeout vs Container Lifetime

| Concept | What It Controls | Who Controls It | Value |
|---------|------------------|-----------------|-------|
| **Lambda Timeout** | Max time for single request | You (CDK config) | 10 seconds |
| **Container Lifetime** | How long container stays warm | AWS (automatic) | 5-15 minutes |

**Lambda timeout = 10 seconds** means:
- Each request must complete within 10 seconds
- Does NOT affect how long container stays alive

**Container lifetime = 5-15 minutes** means:
- AWS keeps container warm for this duration after last request
- You cannot configure this
- Static context survives for this duration

#### Best Practices

1. **Use static initialization** - Initialize expensive resources once
2. **Keep state in memory** - Cache data that survives across requests (like TokenService range)
3. **Handle cold starts** - Design for 5-10s first request, 50-200ms subsequent
4. **Set appropriate timeout** - 10-30s for APIs (API Gateway max is 29s)
5. **Accept ID wastage** - Unused IDs when container dies is acceptable trade-off

#### Key Takeaway

Static Spring context is a **performance optimization** that reduces cost and latency by reusing expensive initialization across multiple Lambda invocations within the same container lifecycle.

---

### Force Lambda Code Update


2025-12-21T07:19:29.233Z
Class not found: inc.skt.quicklink.StreamLambdaHandler: java.lang.ClassNotFoundException
java.lang.ClassNotFoundException: inc.skt.quicklink.StreamLambdaHandler
	at java.base/java.net.URLClassLoader.findClass(Unknown Source)
	at java.base/java.lang.ClassLoader.loadClass(Unknown Source)
	at java.base/java.lang.ClassLoader.loadClass(Unknown Source)
	at java.base/java.lang.Class.forName0(Native Method)
	at java.base/java.lang.Class.forName(Unknown Source)

#### Problem
CDK caches Lambda deployment packages by content hash. If the JAR file path hasn't changed, CDK may not detect that the JAR contents have been updated, causing Lambda to continue using the old code.

#### Symptoms
- `ClassNotFoundException` for newly added classes (e.g., StreamLambdaHandler)
- JAR file locally contains the class, but Lambda doesn't
- `cdk deploy` completes but Lambda code isn't updated

#### Solution Options

**Option 1: Direct Lambda Update (Fast - 2 minutes)**
```bash
# Update Lambda function code directly (run from project root)
aws lambda update-function-code \
  --function-name quicklink-service \
  --zip-file fileb://target/quicklink-1.0.0-aws.jar

# Wait for update to complete
aws lambda wait function-updated \
  --function-name quicklink-service

# Verify update
aws lambda get-function --function-name quicklink-service --query 'Configuration.LastModified'

# Check Lambda logs for errors
aws logs tail /aws/lambda/quicklink-service --follow
```

**Option 2: Force CDK Redeployment (Clean - 10 minutes)**
```bash
cd infrastructure

# Destroy and recreate stack
cdk destroy
cdk deploy
```

**Option 3: Change Asset Path (Workaround)**
```python
# In quicklink_stack.py, modify the code path to force new hash
code=lambda_.Code.from_asset("../target/quicklink-1.0.0.jar")
# Change to:
code=lambda_.Code.from_asset("../target/quicklink-1.0.0.jar", exclude=["*.tmp"])
```

#### Recommended Approach

Use **Option 1** for quick iterations during development:
- Fastest (2 minutes vs 10 minutes)
- Directly updates Lambda code
- No infrastructure changes

Use **Option 2** for clean deployments:
- Ensures infrastructure is in sync
- Useful when CDK stack has other changes
- Recommended before production deployment

#### Prevention

To avoid this issue in future:
1. Always run `mvn clean package` before `cdk deploy`
2. Verify JAR timestamp: `ls -l target/quicklink-1.0.0.jar`
3. Use `cdk diff` to check if Lambda code will be updated
4. Consider using version numbers in JAR filename (e.g., `quicklink-1.0.1.jar`)

#### Key Learning

CDK's asset caching is efficient but can cause confusion during rapid development. Understanding when to use direct Lambda updates vs full CDK redeployment saves time and prevents deployment issues.

**Important:** After updating Lambda code with `aws lambda update-function-code`, old Lambda containers may still be running with the old code for up to 15 minutes. Either:
1. Wait 15 minutes for old containers to expire
2. Force new container with configuration update:
```bash
aws lambda update-function-configuration \
  --function-name quicklink-service \
  --description "Force refresh - $(date +%s)"
```

---

### Maven Shade Plugin - Fixing ClassNotFoundException for Lambda

#### The Problem

Lambda's classloader expects classes at the root level of the JAR:
```
inc/skt/quicklink/StreamLambdaHandler.class  ✅ Lambda can find this
```

But Spring Boot Maven plugin creates a nested structure:
```
BOOT-INF/classes/inc/skt/quicklink/StreamLambdaHandler.class  ❌ Lambda cannot find this
```

**Result:** `ClassNotFoundException: inc.skt.quicklink.StreamLambdaHandler`

#### Why This Happens

Maven executes plugins sequentially:

**Without fix:**
1. **Spring Boot plugin** runs → Creates `quicklink-1.0.0.jar` with BOOT-INF structure
2. **Shade plugin** runs → Copies the BOOT-INF structure into `quicklink-1.0.0-aws.jar`
3. **Result:** Both JARs have BOOT-INF, Lambda fails

**Root cause:** Spring Boot plugin's repackaging creates a special JAR format designed for `java -jar` execution, not for Lambda's classloader.

#### The Solution

Disable Spring Boot plugin repackaging and let Shade plugin create a flat JAR:

```xml
<build>
    <plugins>
        <!-- Disable Spring Boot repackaging -->
        <plugin>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-maven-plugin</artifactId>
            <configuration>
                <excludeDevtools>false</excludeDevtools>
                <skip>true</skip>  <!-- ← This is the key -->
            </configuration>
        </plugin>
        
        <!-- Shade plugin creates flat JAR -->
        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-shade-plugin</artifactId>
            <version>3.5.0</version>
            <configuration>
                <createDependencyReducedPom>false</createDependencyReducedPom>
                <shadedArtifactAttached>true</shadedArtifactAttached>
                <shadedClassifierName>aws</shadedClassifierName>
            </configuration>
            <executions>
                <execution>
                    <phase>package</phase>
                    <goals>
                        <goal>shade</goal>
                    </goals>
                </execution>
            </executions>
        </plugin>
    </plugins>
</build>
```

#### How It Works

**With fix:**
1. **Spring Boot plugin** is skipped (`<skip>true</skip>`)
2. **Shade plugin** runs alone → Creates flat JAR directly from compiled classes
3. **Result:** `quicklink-1.0.0-aws.jar` has flat structure, Lambda succeeds

**JAR structure comparison:**

```
# Before (BOOT-INF structure) ❌
quicklink-1.0.0-aws.jar
├── BOOT-INF/
│   ├── classes/
│   │   └── inc/skt/quicklink/StreamLambdaHandler.class
│   └── lib/
│       └── [all dependencies]
└── org/springframework/boot/loader/

# After (flat structure) ✅
quicklink-1.0.0-aws.jar
├── inc/skt/quicklink/StreamLambdaHandler.class
├── org/springframework/
├── com/amazonaws/
└── [all classes at root level]
```

#### Verification

After building, verify the JAR structure:

```bash
# Check if StreamLambdaHandler is at root level
jar -tf target/quicklink-1.0.0-aws.jar | findstr "StreamLambdaHandler"

# Should output:
inc/skt/quicklink/StreamLambdaHandler.class  ✅

# NOT:
BOOT-INF/classes/inc/skt/quicklink/StreamLambdaHandler.class  ❌
```

#### Why Minimal Configuration Works

We don't need transformers or filters because:
- **No Spring metadata conflicts** - AWS Serverless Java Container handles Spring initialization
- **No signature issues** - Shade plugin automatically excludes signature files
- **No manifest needed** - Lambda doesn't use JAR manifest for handler lookup

**Minimal configuration is sufficient:**
```xml
<configuration>
    <createDependencyReducedPom>false</createDependencyReducedPom>
    <shadedArtifactAttached>true</shadedArtifactAttached>
    <shadedClassifierName>aws</shadedClassifierName>
</configuration>
```

#### Build Output

After `mvn clean package`, you'll have two JARs:

```
target/
├── quicklink-1.0.0.jar        (38 MB, BOOT-INF structure, for local java -jar)
└── quicklink-1.0.0-aws.jar    (75 MB, flat structure, for Lambda)
```

**Use `quicklink-1.0.0-aws.jar` for Lambda deployment.**

#### Key Takeaways

1. **Spring Boot JAR ≠ Lambda-compatible JAR** - Different classloader expectations
2. **`<skip>true</skip>` is critical** - Prevents Spring Boot plugin from creating BOOT-INF structure
3. **Shade plugin creates flat JAR** - Unpacks all dependencies to root level
4. **Minimal configuration works** - No complex transformers needed for our use case
5. **Verify JAR structure** - Always check with `jar -tf` before deploying

#### Trade-offs

- ✅ Lambda can find handler class
- ✅ All dependencies included in single JAR
- ✅ No runtime classpath issues
- ❌ Larger JAR size (75 MB vs 38 MB) - acceptable for Lambda
- ❌ Cannot run locally with `java -jar quicklink-1.0.0-aws.jar` - use the regular JAR for local testing

---

### Testing URL Redirects in Postman

#### The Problem

When testing redirect endpoints (301/302) in Postman, you may see unexpected results:
- **404 Not Found** - Even though your redirect is working
- **200 OK** - Shows the target website's content instead of the redirect response

**Why?** Postman automatically follows redirects by default, so you see the final destination's response, not your redirect response.

#### The Solution

Disable automatic redirect following in Postman to see the actual 301/302 response from your API.

#### Step-by-Step Instructions

**1. Open Postman Settings**
- Click the gear icon (⚙️) in the top-right corner
- Select **Settings**

**2. Disable Automatic Redirects**
- Go to the **General** tab
- Find **"Automatically follow redirects"**
- **Uncheck** this option
- Close settings

**3. Test Your Redirect Endpoint**
```
GET https://YOUR_API_URL/mylink
```

**Expected Response (with redirects disabled):**
```
Status: 301 Moved Permanently

Headers:
Location: https://example.com/target-url
Content-Length: 0
```

**What You'll See (with redirects enabled):**
```
Status: 404 Not Found  (or 200 OK from target site)

Body: [Content from target website]
```

#### Visual Comparison

**❌ With Redirects Enabled (Default)**
```
Request:  GET /mylink
          ↓
Response: 301 → Postman follows → GET https://example.com/target
          ↓
Shows:    404 (if target doesn't exist) or 200 (target's content)
```

**✅ With Redirects Disabled (Correct for Testing)**
```
Request:  GET /mylink
          ↓
Response: 301 Moved Permanently
          Location: https://example.com/target
          ↓
Shows:    Your actual redirect response
```

#### Alternative: Test in Browser

Browsers automatically follow redirects, which is the real-world behavior:

```
1. Paste short URL in browser: https://YOUR_API_URL/mylink
2. Browser receives 301 redirect
3. Browser automatically navigates to target URL
4. You see the target website
```

This is the expected user experience!

#### Key Takeaways

- **Postman default behavior** - Follows redirects automatically (like a browser)
- **For API testing** - Disable redirects to verify your 301/302 response
- **For user testing** - Use browser to verify end-to-end redirect flow
- **404 in Postman** - Usually means redirect worked, but target URL returned 404
- **Check Location header** - This shows where the redirect points to

#### Common Scenarios

| Scenario | Postman (Redirects ON) | Postman (Redirects OFF) |
|----------|------------------------|-------------------------|
| Valid short URL | 200 (target site) | 301 + Location header |
| Invalid short URL | 404 (your API) | 404 (your API) |
| Expired URL | 410 (your API) | 410 (your API) |
| Target URL is 404 | 404 (target site) | 301 + Location header |

---


### @Async Doesn't Work in Lambda

#### The Problem

Spring's @Async annotation doesn't work reliably in AWS Lambda because Lambda freezes the container immediately after the handler returns the response, killing any background threads.

**What happens:**
```
1. Request arrives → Lambda starts handler
2. Handler calls @Async method → Spawns background thread
3. Handler returns response → Lambda freezes container
4. Background thread is killed → SQS message never sent
```

#### Attempted Solution

Initially implemented AnalyticsService with @Async:

```java
@Service
public class AnalyticsService {
    @Async
    public void recordClick(String shortCode, String ipAddress, String userAgent) {
        // Send to SQS
    }
}
```

**Result:** Logs showed "Sending to SQS" but never "Successfully sent". Messages never reached SQS.

#### Solution

Remove @Async and make it synchronous:

```java
@Service
public class AnalyticsService {
    public void recordClick(String shortCode, String ipAddress, String userAgent) {
        // Send to SQS synchronously
    }
}
```

#### Trade-offs

| Approach | Latency Impact | Reliability | Works in Lambda? |
|----------|---------------|-------------|------------------|
| @Async | +0ms (non-blocking) | ❌ Unreliable | ❌ No |
| Synchronous | +50-100ms | ✅ Reliable | ✅ Yes |

**Decision:** Use synchronous SQS calls in Lambda. The 50-100ms latency is acceptable for a demo project, and reliability is more important than micro-optimizations.

#### Alternative Solutions (Not Implemented)

1. **Lambda Extensions** - Background processes that survive after handler returns (complex)
2. **Step Functions** - Orchestrate async workflows (overkill for simple analytics)
3. **EventBridge** - Publish events asynchronously (adds another service)

#### Key Learning

Lambda's execution model is fundamentally different from traditional servers:
- **Traditional server:** Background threads run indefinitely
- **Lambda:** Container freezes after response, killing background work

**Best practice:** Keep all work in the main execution path for Lambda functions.

---


### Why Async Analytics Don't Work in Java Lambda (But Work in Python)

#### The Core Problem: Execution Model Differences

Async analytics and in-memory batching work in Python/Node.js Lambda but not in Java Lambda. This isn't a framework limitation—it's a fundamental difference in how these runtimes handle concurrency.

---

#### Python FastAPI: Event Loop Model ✅

**How it works:**
```python
from fastapi import FastAPI
import asyncio
from collections import deque

app = FastAPI()

# Shared queue across ALL requests (single process)
event_queue = deque()

@app.get("/{short_code}")
async def redirect(short_code: str):
    # 1. Get URL from DynamoDB
    long_url = await get_url(short_code)
    
    # 2. Queue analytics (non-blocking, ~1ms)
    asyncio.create_task(queue_analytics(short_code))
    
    # 3. Return redirect immediately
    return RedirectResponse(long_url, status_code=301)

async def queue_analytics(short_code: str):
    event_queue.append({"shortCode": short_code, ...})
    
    # Batch flush when ready
    if len(event_queue) >= 10:
        batch = [event_queue.popleft() for _ in range(10)]
        await sqs_client.send_message_batch(batch)
```

**Lambda execution:**
```
Lambda Container (1 Python process)
├─ Request 1 → Event loop → Queue event → Return 301
├─ Request 2 → Event loop → Queue event → Return 301
├─ Request 3 → Event loop → Queue event → Return 301
├─ Background: Batch flush continues after responses ✅
└─ All requests share same event_queue ✅
```

**Why it works:**
- Single Python process handles all requests
- Event loop allows cooperative multitasking
- Background tasks continue after response
- Shared memory across all invocations

---

#### Java Spring Boot: Thread-Based Model ❌

**Attempted implementation:**
```java
@Service
public class AnalyticsService {
    // Each Lambda invocation = separate thread
    private static Queue<AnalyticsEvent> eventQueue = new ConcurrentLinkedQueue<>();
    
    @Async
    public void recordClick(String shortCode, String ipAddress, String userAgent) {
        eventQueue.add(new AnalyticsEvent(shortCode, ...));
        
        if (eventQueue.size() >= 10) {
            sqsClient.sendMessageBatch(eventQueue);
        }
    }
}
```

**Lambda execution:**
```
Lambda Container (1 JVM, multiple threads)
├─ Request 1 → Thread 1 → Queue event → Return 301 → Thread killed ❌
├─ Request 2 → Thread 2 → Queue event → Return 301 → Thread killed ❌
├─ Request 3 → Thread 3 → Queue event → Return 301 → Thread killed ❌
└─ Threads don't share queue effectively ❌
```

**Why it doesn't work:**
1. **@Async threads killed:** Lambda freezes container after response, killing background threads
2. **No shared memory:** Each request handled by isolated thread context
3. **Batch never fills:** Requests distributed across multiple Lambda containers

---

#### The Fundamental Difference

| Aspect | Python/Node.js | Java (Spring/Quarkus) |
|--------|----------------|----------------------|
| **Concurrency model** | Event loop (single-threaded) | Thread pool (multi-threaded) |
| **Lambda execution** | 1 process handles all requests | 1 thread per request |
| **Shared memory** | ✅ All requests share same process | ❌ Threads isolated |
| **Async after response** | ✅ Event loop continues | ❌ Threads killed on freeze |
| **Batching** | ✅ Works (shared queue) | ❌ Doesn't work (distributed) |
| **Cold start** | ~100-300ms | ~5-10s |

---

#### Why Multiple Lambda Containers Break Batching

Even if Java could share memory within a container, Lambda scales horizontally:

```
Container 1: Handles requests 1, 5, 9, 13 → Queue: [event1, event5, event9] (3 events)
Container 2: Handles requests 2, 6, 10, 14 → Queue: [event2, event6, event10] (3 events)
Container 3: Handles requests 3, 7, 11, 15 → Queue: [event3, event7, event11] (3 events)
Container 4: Handles requests 4, 8, 12, 16 → Queue: [event4, event8, event12] (3 events)
```

**Result:** No container reaches batch size of 10. Events sit in queues forever.

**This affects Python too, but:**
- Python's event loop can flush partial batches on idle
- Python's lower latency makes synchronous SQS acceptable
- Python containers handle more requests before scaling

---

#### Attempted Solutions in Java

**1. @Async (Spring Boot)**
```java
@Async
public void recordClick(...) {
    sqsClient.sendMessage(event);
}
```
- ❌ Thread killed when Lambda freezes
- ❌ Message never sent

**2. In-memory batching**
```java
private static Queue<Event> queue = new ConcurrentLinkedQueue<>();
```
- ❌ Distributed across containers
- ❌ Batch never fills
- ❌ Data loss on container termination

**3. DynamoDB as shared queue**
```java
dynamoDb.putItem(event);  // +10-20ms
if (count >= 10) flush();  // +50ms
```
- ❌ Higher latency than direct SQS (70-80ms vs 50ms)
- ❌ Race conditions across containers
- ❌ Added complexity

---

#### The Solution: Synchronous SQS

```java
@Service
public class AnalyticsService {
    public void recordClick(String shortCode, String ipAddress, String userAgent) {
        try {
            AnalyticsEvent event = new AnalyticsEvent(shortCode, ...);
            String messageBody = objectMapper.writeValueAsString(event);
            
            sqsClient.sendMessage(SendMessageRequest.builder()
                .queueUrl(queueUrl)
                .messageBody(messageBody)
                .build());
                
            logger.debug("Analytics event sent for shortCode: {}", shortCode);
        } catch (Exception e) {
            logger.error("Failed to send analytics: {}", e.getMessage());
        }
    }
}
```

**Trade-offs:**

| Approach | Latency | Reliability | Complexity | Works in Java Lambda? |
|----------|---------|-------------|------------|-----------------------|
| Synchronous SQS | +50-100ms | ✅ High | Low | ✅ Yes |
| @Async | +0ms | ❌ Unreliable | Low | ❌ No |
| Batching | +0ms | ❌ Data loss | High | ❌ No |
| DynamoDB queue | +70-80ms | ⚠️ Medium | High | ⚠️ Complex |

**Decision:** Synchronous SQS is the correct production solution for Java Lambda.

---

#### Real-World Context

**Industry benchmarks:**
- Bitly average redirect: ~150ms
- TinyURL average redirect: ~200ms
- Our implementation: ~150-200ms (competitive)

**Why companies use synchronous writes:**
1. **Reliability** - No data loss
2. **Simplicity** - Fewer bugs
3. **Debuggability** - Clear error traces
4. **Cost-effective** - No extra services

---

#### Key Learnings

1. **Runtime matters more than framework** - Python/Node.js have inherent advantages for serverless async operations

2. **Lambda execution model** - Designed for event-driven runtimes (Python/Node.js), not thread-based (Java)

3. **Simple > Complex** - Synchronous SQS is simpler and more reliable than async batching

4. **Latency is acceptable** - 50-100ms for analytics is not a problem to solve

5. **Choose the right tool** - Java excels on traditional servers (EC2/ECS), Python/Node.js excel on Lambda

---

#### When to Use Each Runtime

| Use Case | Best Choice | Why |
|----------|-------------|-----|
| **Serverless APIs** | Python/Node.js | Fast cold start, true async |
| **Enterprise apps** | Java | Type safety, mature ecosystem |
| **High-throughput** | Python/Node.js | Event loop efficiency |
| **Complex business logic** | Java | Strong typing, tooling |
| **Microservices on ECS** | Java | Thread pool works well |
| **Lambda functions** | Python/Node.js | Designed for event-driven |

---

#### Conclusion

For this project, **Spring Boot + synchronous SQS** is the correct production solution. It demonstrates:
- ✅ Understanding of Lambda's execution model
- ✅ Pragmatic trade-off decisions
- ✅ Production-grade reliability
- ✅ Industry-standard patterns

If async batching were critical, the project would need to be rewritten in Python/Node.js—but the 50-100ms latency makes that unnecessary.

---


### API Gateway Proxy Mode Limitations

#### What is Proxy Mode?

API Gateway's `LambdaRestApi` with `proxy=True` forwards ALL requests to a single Lambda function, letting the application (Spring Boot) handle routing internally.

**CDK Configuration:**
```python
api = apigw.LambdaRestApi(
    self, "QuickLinkApi",
    handler=lambda_function,
    proxy=True  # ← All requests go to Lambda
)
```

**Request flow:**
```
GET /api/v1/health     → Lambda → Spring Boot → HealthController
POST /api/v1/shorten   → Lambda → Spring Boot → UrlController
GET /abc123            → Lambda → Spring Boot → UrlController
```

---

#### Limitations of Proxy Mode

##### 1. No Per-Endpoint Throttling

**Problem:** Cannot apply different rate limits to different endpoints.

**What you can do:**
```python
# Global throttling only
api = apigw.LambdaRestApi(
    self, "QuickLinkApi",
    handler=lambda_function,
    proxy=True,
    default_method_options=apigw.MethodOptions(
        throttling=apigw.ThrottlingSettings(
            rate_limit=50,      # 50 req/s for ALL endpoints
            burst_limit=100     # 100 burst for ALL endpoints
        )
    )
)
```

**What you cannot do:**
```python
# ❌ This doesn't work with proxy=True
/api/v1/shorten  → 10 req/s   (write-heavy)
/{shortCode}     → 1000 req/s (read-heavy)
```

**Why:** API Gateway doesn't see individual routes—it only sees `/{proxy+}` wildcard.

**Workaround:** Implement application-level rate limiting in Spring Boot (not demonstrated in this project).

---

##### 2. No Per-Endpoint Authentication

**Problem:** Cannot apply authentication to some endpoints while leaving others public.

**What you need:**
```
POST /api/v1/shorten   → Requires authentication ✅
GET /{shortCode}       → Public (no auth) ✅
```

**What proxy mode gives you:**
```
All endpoints → Same auth policy (all or nothing)
```

**Why this breaks the product:**
- Short URLs MUST be public (shared in emails, social media, printed materials)
- If you enable API Gateway authentication, redirect endpoint becomes inaccessible
- Users would need API keys to click short links (defeats the purpose)

**Attempted solutions:**

**Option 1: API Gateway Cognito Authorizer**
```python
authorizer = apigw.CognitoUserPoolsAuthorizer(...)
api = apigw.LambdaRestApi(
    handler=lambda_function,
    proxy=True,
    default_method_options=apigw.MethodOptions(
        authorizer=authorizer  # ← Applies to ALL endpoints
    )
)
```
- ❌ Breaks redirect endpoint (requires auth token)
- ❌ Cannot exclude specific routes

**Option 2: Application-Level Authentication**
```java
@Configuration
public class SecurityConfig {
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) {
        http.authorizeHttpRequests(auth -> auth
            .requestMatchers("/api/v1/shorten").authenticated()
            .requestMatchers("/{shortCode}").permitAll()
        );
    }
}
```
- ✅ Works, but doesn't integrate with AWS Cognito
- ❌ Requires custom JWT validation
- ❌ No AWS-native user management

**Decision:** Skip authentication for this project. Better suited for Password Manager project where all endpoints require auth.

---

##### 3. No Per-Endpoint Rate Limiting (Usage Plans)

**Problem:** Cannot assign different quotas to different API keys.

**What you want:**
```
Free tier:     100 requests/day
Pro tier:      10,000 requests/day
Enterprise:    Unlimited
```

**What proxy mode gives you:**
```
All API keys → Same quota (or no quotas)
```

**Why:** Usage plans require explicit API Gateway routes, not `/{proxy+}` wildcard.

**Workaround:** Implement application-level quotas using DynamoDB (not demonstrated).

---

##### 4. Swagger UI Doesn't Work on Lambda

**Problem:** Swagger UI requires serving static WebJar resources, which AWS Serverless Java Container doesn't support.

**What works:**
```
Local:  http://localhost:8080/swagger-ui.html  ✅
Lambda: https://api.example.com/swagger-ui.html  ❌
```

**Why:** 
- Swagger UI is packaged as WebJar (JAR containing HTML/CSS/JS)
- Spring Boot's ResourceHandler serves these files
- AWS Serverless Java Container doesn't implement WebJar resource serving

**Workaround:**
```bash
# Export OpenAPI spec
curl https://YOUR_API_URL/v3/api-docs > openapi.json

# Import into external tools
- Swagger Editor: https://editor.swagger.io
- Postman: Import → Upload openapi.json
- Insomnia: Import → From File
```

**Trade-off:**
- ❌ No interactive API docs on Lambda
- ✅ OpenAPI spec export works perfectly
- ✅ External tools provide better testing experience

---

##### 5. Cannot Mix Proxy and Explicit Routes

**Problem:** Once you enable `proxy=True`, you cannot add explicit routes for fine-grained control.

**What you cannot do:**
```python
api = apigw.LambdaRestApi(handler=lambda_function, proxy=True)

# ❌ This doesn't work - proxy mode ignores explicit routes
shorten_resource = api.root.add_resource("api").add_resource("v1").add_resource("shorten")
shorten_resource.add_method("POST", throttling=...)
```

**Why:** `proxy=True` creates a single `/{proxy+}` route that catches everything.

**Alternative:** Use `RestApi` with explicit routes (loses Spring Boot routing).

---

#### Proxy Mode vs Explicit Routes Comparison

| Feature | Proxy Mode (LambdaRestApi) | Explicit Routes (RestApi) |
|---------|---------------------------|---------------------------|
| **Setup complexity** | ✅ Simple (1 line) | ❌ Complex (route per endpoint) |
| **Spring Boot routing** | ✅ Works | ❌ Breaks (API Gateway routes first) |
| **Per-endpoint throttling** | ❌ No | ✅ Yes |
| **Per-endpoint auth** | ❌ No | ✅ Yes |
| **Usage plans** | ❌ Limited | ✅ Full support |
| **Maintenance** | ✅ Easy | ❌ Sync routes with code |
| **Best for** | Monolithic apps | Microservices |

---

#### Why We Chose Proxy Mode

**Rationale:**
1. **Simplicity** - Single Lambda, single deployment
2. **Spring Boot routing** - Leverage framework's routing capabilities
3. **Rapid development** - No route synchronization needed
4. **Portfolio focus** - Demonstrate Spring Boot expertise
5. **Scale is acceptable** - Global throttling sufficient for demo

**Trade-offs accepted:**
- ❌ No per-endpoint throttling (acceptable for demo scale)
- ❌ No AWS-native authentication (would break redirect endpoint anyway)
- ❌ No usage plans (not needed for portfolio project)
- ✅ Simpler infrastructure
- ✅ Faster development
- ✅ Better Spring Boot demonstration

---

#### When to Use Explicit Routes

Consider `RestApi` with explicit routes when:
- Need per-endpoint rate limiting (e.g., 10 req/s for writes, 1000 req/s for reads)
- Need per-endpoint authentication (e.g., admin endpoints require auth, public endpoints don't)
- Need usage plans with API keys (e.g., free/pro/enterprise tiers)
- Building microservices (each endpoint is a separate Lambda)
- Need fine-grained CloudWatch metrics per endpoint

**Example use case:**
```
POST /api/v1/shorten   → 10 req/s, requires API key, admin Lambda
GET /{shortCode}       → 1000 req/s, public, redirect Lambda
GET /api/v1/stats      → 100 req/s, requires auth, analytics Lambda
```

---

#### Key Takeaways

1. **Proxy mode = simplicity** - Best for monolithic Spring Boot apps on Lambda
2. **Authentication is incompatible** - Cannot protect some endpoints while keeping others public
3. **Global throttling only** - Per-endpoint limits require explicit routes
4. **Swagger UI doesn't work** - Use OpenAPI export + external tools
5. **Trade-offs are documented** - Shows architectural thinking for interviews

**Decision:** Proxy mode is the correct choice for this project's requirements and scale.

---


### Production Analytics Architecture (Not Implemented)

#### Current Implementation: Synchronous

**Redirect flow:**
```
GET /{shortCode}
  ↓
1. Read URL from DynamoDB (~10-20ms)
2. Increment click count in DynamoDB (~30-50ms) ← Synchronous
3. Send analytics to SQS (~20-30ms) ← Synchronous
4. Return 301 redirect
Total: ~60-100ms
```

#### Recommended Production Approach: Async with Consumer

**Redirect flow:**
```
GET /{shortCode}
  ↓
1. Read URL from DynamoDB (~10-20ms)
2. Send single SQS event (~20-30ms) ← Synchronous but fast
3. Return 301 redirect immediately
Total: ~30-50ms (40-50% faster)

Background (async):
SQS → Consumer Lambda → Update DynamoDB + Store analytics
```

**SQS Event Payload:**
```json
{
  "shortCode": "abc123",
  "ipAddress": "203.0.113.42",
  "userAgent": "Mozilla/5.0...",
  "timestamp": 1704067200,
  "referer": "https://example.com"
}
```

**Consumer Lambda responsibilities:**
1. Batch process SQS events (10-100 at a time)
2. Update DynamoDB click counts (batch write)
3. Store detailed analytics (S3, Timestream, or analytics DB)
4. Aggregate metrics (hourly/daily summaries)

#### Performance Comparison

| Metric | Current (Sync) | Production (Async) | Improvement |
|--------|---------------|-------------------|-------------|
| Redirect latency | 60-100ms | 30-50ms | 40-50% faster |
| DynamoDB writes | 2 per redirect | 1 per batch | 90% reduction |
| SQS cost | $1.04/month | $1.04/month | Same |
| Lambda cost | $10.40/month | $12.50/month | +20% |
| Reliability | ✅ High | ✅ High | Same |
| Complexity | Low | Medium | Trade-off |

#### Why Not Implemented

**Reasons:**
1. **Demo simplicity** - Fewer moving parts to explain
2. **Acceptable latency** - 60-100ms is industry standard
3. **Cost-effective** - Saves $2/month on Lambda
4. **Reliable** - No data loss from async failures
5. **SQS is fast** - 20-30ms is already quite fast

**When to implement:**
- Traffic > 100 req/s (latency becomes critical)
- Need complex analytics (geographic, device, bot detection)
- Want to aggregate metrics in real-time
- Building production service (not demo)

#### Key Insight: SQS vs DynamoDB Performance

**SQS is faster than DynamoDB for writes:**
- **SQS SendMessage**: 10-30ms (fire-and-forget, eventual consistency)
- **DynamoDB UpdateItem**: 20-50ms (atomic operations, strong consistency)

**Why:**
- SQS accepts message and returns immediately (no read-after-write)
- DynamoDB must ensure atomic increment (read-modify-write cycle)
- SQS has no consistency guarantees (just queue it)
- DynamoDB guarantees strong consistency (must wait for confirmation)

**Trade-off:**
- ✅ SQS: Faster writes, eventual processing
- ✅ DynamoDB: Slower writes, immediate consistency
- **Decision:** Use SQS for async analytics, DynamoDB for real-time counters

---


### URL Expiry Handling: Application Check vs DynamoDB TTL

#### Current Implementation: Application-Level Check

Every redirect checks expiry time in application code:

```java
public UrlMapping getOriginalUrl(String shortCode) {
    UrlMapping urlMapping = urlRepository.findByShortCode(shortCode)
            .orElseThrow(() -> new UrlNotFoundException("Short URL not found: " + shortCode));
    
    // Check expiry on every request
    if (urlMapping.getExpiresAt() != null && urlMapping.getExpiresAt() < System.currentTimeMillis() / 1000) {
        throw new UrlExpiredException("This short URL has expired");
    }
    
    return urlMapping;
}
```

**Pros:**
- ✅ Immediate feedback to user
- ✅ Custom error message ("This short URL has expired")
- ✅ Can log expired access attempts
- ✅ Simple to implement

**Cons:**
- ❌ Adds latency (~1-2ms per redirect)
- ❌ Expired URLs still stored in DynamoDB
- ❌ Wastes storage space
- ❌ Manual cleanup required

---

#### Better Approach: DynamoDB TTL (Recommended for Production)

DynamoDB has built-in Time-To-Live (TTL) that automatically deletes expired items.

**How it works:**
1. Enable TTL on `expiresAt` attribute
2. DynamoDB scans table and deletes expired items (within 48 hours)
3. No application code needed
4. Free (no additional cost)

**Implementation:**
```python
# In quicklink_stack.py
urls_table = dynamodb.Table(
    self, "QuickLinkUrls",
    partition_key=dynamodb.Attribute(name="shortCode", type=dynamodb.AttributeType.STRING),
    time_to_live_attribute="expiresAt"  # ← Enable TTL
)
```

**Pros:**
- ✅ Zero latency (no application check)
- ✅ Automatic cleanup (free)
- ✅ Reduces storage costs
- ✅ No code maintenance

**Cons:**
- ❌ Deletion delay (up to 48 hours after expiry)
- ❌ Generic 404 error (item deleted)
- ❌ Cannot log expired access attempts

---

#### Recommended Production Approach: Both

Use both application check AND DynamoDB TTL:

```java
// Application check (immediate feedback)
if (urlMapping.getExpiresAt() != null && urlMapping.getExpiresAt() < System.currentTimeMillis() / 1000) {
    throw new UrlExpiredException("This short URL has expired");
}
```

```python
# DynamoDB TTL (automatic cleanup)
urls_table = dynamodb.Table(
    time_to_live_attribute="expiresAt"
)
```

**Why both:**
- **0-48 hours after expiry:** Application check returns "expired" message
- **48+ hours after expiry:** DynamoDB deletes item, returns 404
- Best of both worlds: immediate UX + automatic cleanup

---

#### Comparison

| Approach | Latency | Storage | User Experience | Cost | Complexity |
|----------|---------|---------|-----------------|------|------------|
| **Application check only** (current) | +1-2ms | Wastes space | ✅ Immediate error | Free | Low |
| **DynamoDB TTL only** | 0ms | ✅ Auto-cleanup | ⚠️ 404 (delayed) | Free | Very low |
| **Both** (recommended) | +1-2ms | ✅ Auto-cleanup | ✅ Immediate error | Free | Low |

---

#### Why Not Implemented

**Reasons:**
1. **Demo simplicity** - Application check is sufficient for portfolio
2. **TTL delay** - 48-hour window requires explanation
3. **Storage cost negligible** - Demo scale doesn't need cleanup
4. **Shows trade-off thinking** - Documents both approaches

**When to implement:**
- Production deployment (storage costs matter)
- High traffic (millions of URLs)
- Long-term operation (years of expired URLs)

---

#### Key Takeaways

1. **Application check** - Good for UX, adds latency
2. **DynamoDB TTL** - Good for storage, has delay
3. **Both together** - Best production solution
4. **Current implementation** - Acceptable for demo, documents trade-offs

**Decision:** Application check only for demo. Production should add DynamoDB TTL.

---
