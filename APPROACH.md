# QuickLink - Implementation Approach & Design Decisions

This document captures the architectural decisions, trade-offs, and learnings throughout the implementation of QuickLink URL Shortener.

---

## How Spring Profiles Work

### Local Development (Current)
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

## Summary

| Environment | Profile | Repository Used | AWS Needed? |
|-------------|---------|-----------------|-------------|
| **Local Dev** | `local` | InMemory | ❌ No |
| **Testing** | `test` | InMemory | ❌ No |
| **AWS Lambda** | `prod` | DynamoDB | ✅ Yes |

**Key Point:** Same code, different behavior based on profile!

