# QuickLink – Serverless URL Shortener

QuickLink is a **URL Shortener system** designed to demonstrate **system design thinking**, starting from a traditional load-balanced architecture and evolving into a **fully serverless AWS solution**.

This repository focuses on **HLD → LLD → trade-offs**, making it suitable for **system design interviews, backend roles, and portfolio review**.


## ✨ Features
- **Convert long URLs into short URLs**
- **Redirect short URLs to original URLs**
- **Collision-free short code generation**
- **Horizontally scalable architecture**
- **Asynchronous analytics collection**
- **Fully serverless AWS deployment**
- **Designed for long-term scale**


## 🧠 Architecture Evolution

This project intentionally documents the **evolution of the design**, showing how architectural decisions change as scalability, reliability, and operational concerns are introduced.


### 1️⃣ Initial Design – Load Balancer Based Architecture
Traditional service-based design using:
- Load Balancer
- Multiple URL Shortener service instances
- Redis for sequence generation
- Database for URL mappings

![Load Balancer Architecture](docs/01-loadbalancer-hld.png)


### 2️⃣ Improved Design – Token Service Based Architecture
Introduces a **Token Service** to avoid collisions and reduce dependency on Redis.

![Token Service Architecture](docs/02-tokenservice-hld.png)

**Key improvements**
- Range-based ID allocation
- Reduced contention
- Acceptable ID loss on service failure
- Clear separation of responsibilities


### 3️⃣ Final Design – Serverless Architecture on AWS
Fully serverless, AWS-native architecture.

![Serverless Architecture](docs/03-serverless-hld.png)


## 🏗️ High-Level Design (Final – Serverless)

### Entry Layer
- **API Gateway (REST API)**
  - Public entry point
  - Gateway-level authorizer (Cognito / Lambda authorizer)
  - Routes requests to Lambda functions

### Compute Layer
- **URL Shortener Lambda (Spring Boot)**
  - Handles `POST /shorten`
  - Handles `GET /{shortCode}` redirects (301 / 302)
  - Invokes Token Service Lambda internally
  - Publishes analytics events asynchronously to SQS using @Async

- **Token Service Lambda (Spring Boot)**
  - Allocates unique ID ranges (10,000 IDs at a time)
  - Uses DynamoDB atomic increment (ADD operation)
  - Caches allocated range in Lambda memory
  - Internal-only service (not exposed via API Gateway)

### Data Layer
- **DynamoDB – URL Mapping Table**
  - Stores `shortCode → longUrl` mappings

- **DynamoDB – Token Metadata Table**
  - Tracks allocated ID ranges
  - Prevents collisions across Lambda instances

### Analytics
- **Amazon SQS**
  - Best-effort, asynchronous analytics ingestion
  - Non-blocking for redirect flow

### Observability
- **Amazon CloudWatch**
  - Logs
  - Metrics
  - Alarms


## 🔍 Low-Level Design (LLD)

### DynamoDB – URL Mapping Table

**Table Name:** `quicklink-urls`

**Primary Key:** `shortCode` (String)

| Attribute | Type | Required | Description | Example |
|-----------|------|----------|-------------|----------|
| shortCode | String | ✅ | 7-char base62 code (PK) | `"aB3xY9z"` |
| longUrl | String | ✅ | Original URL | `"https://example.com/long-url"` |
| createdAt | Number | ✅ | Unix timestamp (seconds) | `1704067200` |
| userId | String | ✅ | Creator identifier | `"user_abc123"` or `"anonymous"` |
| isActive | Boolean | ✅ | Soft delete flag | `true` |
| expiresAt | Number | ❌ | Custom expiry (TTL) | `1735689600` |
| customAlias | Boolean | ✅ | User-chosen vs auto-generated | `false` |
| clickCount | Number | ✅ | Denormalized click counter | `42` |

**Capacity Mode:** On-Demand  
**TTL Attribute:** `expiresAt`  
**Item Size:** ~253 bytes

---

### DynamoDB – Token Metadata Table

**Table Name:** `quicklink-tokens`

**Primary Key:** `tokenId` (String)

| Attribute | Type | Required | Description | Example |
|-----------|------|----------|-------------|----------|
| tokenId | String | ✅ | Counter identifier (PK) | `"global_counter"` |
| currentRangeEnd | Number | ✅ | Last allocated ID | `1000000` |
| lastUpdated | Number | ✅ | Last allocation timestamp | `1704067200` |
| totalAllocated | Number | ✅ | Total IDs allocated | `1000000` |

**Capacity Mode:** On-Demand  
**Access Pattern:** Atomic increment using ADD operation

---

## 🌐 API Endpoints

### 1. Health Check
```http
GET /health

Response: 200 OK
{
  "status": "UP",
  "service": "quicklink-url-shortener",
  "version": "1.0.0",
  "timestamp": 1704067200
}
```

### 2. Create Short URL
```http
POST /shorten
Content-Type: application/json

Request:
{
  "url": "https://example.com/very/long/url",
  "customAlias": "mylink"  // Optional
}

Response: 201 Created
{
  "shortCode": "aB3xY9z",
  "shortUrl": "https://short.link/aB3xY9z",
  "longUrl": "https://example.com/very/long/url",
  "createdAt": 1704067200
}
```

### 3. Redirect to Original URL
```http
GET /{shortCode}

Response: 301 Moved Permanently
Location: https://example.com/very/long/url
```

### 4. Get URL Statistics (Optional)
```http
GET /stats/{shortCode}

Response: 200 OK
{
  "shortCode": "aB3xY9z",
  "longUrl": "https://example.com/very/long/url",
  "clickCount": 42,
  "createdAt": 1704067200,
  "isActive": true
}
```

---

## 🔤 Base62 Encoding

Converts numeric ID to 7-character short code:

```java
public class Base62Encoder {
    private static final String BASE62 = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
    
    public static String encode(long id) {
        StringBuilder sb = new StringBuilder();
        while (id > 0) {
            sb.append(BASE62.charAt((int)(id % 62)));
            id /= 62;
        }
        return sb.reverse().toString();
    }
}
```

**Examples:**
- ID: `1` → Code: `"0000001"`
- ID: `62` → Code: `"0000010"`
- ID: `3521614606208` → Code: `"zzzzzzz"`

---

## ⚡ Performance Considerations

### Lambda Cold Start Trade-offs

**Challenge:** Spring Boot on Lambda has 5-10s cold starts

**Solutions Evaluated:**
1. **Provisioned Concurrency** - Keeps instances warm (~$15/month)
2. **Spring Native (GraalVM)** - Compiles to native binary (~500ms)
3. **Lightweight framework** - Micronaut/Quarkus (~1-2s)

**Decision for Demo:**
- Using standard Spring Boot to showcase framework expertise
- In production, would use Spring Native + SnapStart
- Documented trade-offs demonstrate architectural thinking

**Mitigation:**
- Redirect endpoint optimized (minimal dependencies)
- @Async for non-blocking analytics
- Health checks keep Lambda warm during testing

### Why Spring Boot Over Alternatives?

| Framework | Cold Start | Expertise | Portfolio Value |
|-----------|------------|-----------|----------------|
| Spring Boot | 5-10s | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐⭐ |
| Quarkus | 500ms-1s | ⭐⭐⭐ | ⭐⭐⭐⭐ |
| Python/FastAPI | 100-300ms | ⭐⭐⭐ | ⭐⭐⭐ |

**Chosen:** Spring Boot for primary skill demonstration and job market value

---

## 💰 Cost Estimates

### AWS Free Tier (Always Free)
| Service | Free Tier Limit | Sufficient For |
|---------|----------------|----------------|
| Lambda | 1M requests/month + 400K GB-sec | ✅ ~0.38 req/s |
| API Gateway | 1M requests/month (12 months) | ✅ ~0.38 req/s |
| DynamoDB | 25 GB storage + 25 WCU/RCU | ✅ ~100M URLs |
| SQS | 1M requests/month | ✅ Analytics queue |
| CloudWatch | 10 metrics, 5 GB logs | ✅ Basic monitoring |

**Total Cost (Free Tier): $0/month** 🎉

### Beyond Free Tier - Production Scale

#### Scenario: 10 req/s average (26M requests/month)
| Service | Usage | Monthly Cost |
|---------|-------|-------------|
| API Gateway | 26M requests | $26.00 |
| Lambda | 26M invocations, 512MB, 200ms avg | $10.40 |
| DynamoDB | 2.6M writes, 23.4M reads, 10 GB | $3.50 |
| SQS | 2.6M messages | $1.04 |
| CloudWatch | Logs (5 GB) | $2.50 |
| **Total** | | **~$43/month** |

---

## 🛠️ Tech Stack

### Backend
- **Language:** Java 17
- **Framework:** Spring Boot 3.2
- **Build Tool:** Maven
- **AWS SDK:** AWS SDK for Java v2

### Infrastructure
- **IaC:** AWS CDK (Python)
- **Compute:** AWS Lambda
- **API:** Amazon API Gateway (REST)
- **Database:** Amazon DynamoDB
- **Queue:** Amazon SQS
- **Monitoring:** Amazon CloudWatch

### Key Dependencies
```xml
<dependencies>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
    <dependency>
        <groupId>software.amazon.awssdk</groupId>
        <artifactId>dynamodb</artifactId>
    </dependency>
    <dependency>
        <groupId>software.amazon.awssdk</groupId>
        <artifactId>sqs</artifactId>
    </dependency>
</dependencies>
```

---

## 🚀 Getting Started

### Prerequisites
- Java 17+
- Maven 3.8+
- AWS CLI configured
- AWS CDK installed

### Local Development
```bash
# Clone repository
git clone https://github.com/yourusername/quicklink.git
cd quicklink

# Build project
mvn clean install

# Run locally
mvn spring-boot:run

# Test health endpoint
curl http://localhost:8080/health
```

### Deploy to AWS
```bash
# Navigate to infrastructure
cd infrastructure

# Install CDK dependencies
pip install -r requirements.txt

# Deploy stack
cdk deploy
```

---

## 📚 Project Structure

```
quicklink/
├── README.md
├── docs/
│   ├── 01-loadbalancer-hld.png
│   ├── 02-tokenservice-hld.png
│   └── 03-serverless-hld.png
├── pom.xml
├── src/
│   ├── main/
│   │   ├── java/com/quicklink/
│   │   │   ├── QuickLinkApplication.java
│   │   │   ├── config/
│   │   │   ├── controller/
│   │   │   ├── service/
│   │   │   ├── repository/
│   │   │   ├── model/
│   │   │   └── util/
│   │   └── resources/
│   │       └── application.yml
│   └── test/
└── infrastructure/  # AWS CDK
```

---

## 🎯 Learning Outcomes

This project demonstrates:
- ✅ System design thinking (HLD → LLD)
- ✅ AWS serverless architecture
- ✅ Spring Boot expertise
- ✅ DynamoDB data modeling
- ✅ Async operations with @Async
- ✅ Infrastructure as Code (CDK)
- ✅ Production-grade patterns (soft deletes, auditing)
- ✅ Performance optimization strategies
- ✅ Cost-aware architecture

---

## 📖 Additional Resources

- [Spring Boot Documentation](https://spring.io/projects/spring-boot)
- [AWS Lambda Best Practices](https://docs.aws.amazon.com/lambda/latest/dg/best-practices.html)
- [DynamoDB Best Practices](https://docs.aws.amazon.com/amazondynamodb/latest/developerguide/best-practices.html)
- [AWS CDK Documentation](https://docs.aws.amazon.com/cdk/)

---

## 📄 License

This project is licensed under the MIT License.

---

## 📋 TODO

- [ ] Configure custom domain: `https://skt.inc` (after AWS deployment)
- [ ] Set up DNS/Route53 for custom domain
- [ ] Update `application.yml` with production base URL

---

## 👤 Author

Built as a learning project to demonstrate system design and AWS serverless architecture.
