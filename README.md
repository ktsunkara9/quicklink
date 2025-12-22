# QuickLink – Serverless URL Shortener

QuickLink is a **URL Shortener system** designed to demonstrate **system design thinking**, starting from a traditional load-balanced architecture and evolving into a **fully serverless AWS solution**.

This repository focuses on **HLD → LLD → trade-offs**, making it suitable for **system design interviews, backend roles, and portfolio review**.


## 🚧 Implementation Status

### ✅ Completed

**Core Application**
- [x] Spring Boot 3.2 + Java 17 project setup with Maven
- [x] Domain models (UrlMapping, TokenMetadata) and DTOs
- [x] Repository pattern with DynamoDB and in-memory implementations
- [x] Base62 encoder/decoder with unit tests
- [x] Spring Profiles (local, test, prod) for environment-specific beans

**Business Logic**
- [x] TokenService with range-based ID allocation (100 IDs at a time)
- [x] UrlService with fail-fast validations (URL format, length, custom alias, expiry)
- [x] Custom exception handling (@RestControllerAdvice with proper HTTP status mapping)
- [x] Soft delete implementation (isActive flag)

**API Endpoints**
- [x] POST /api/v1/shorten - Create short URL with optional custom alias and expiry
- [x] GET /{shortCode} - 301 redirect with expiry and active status checks
- [x] PATCH /api/v1/urls/{shortCode} - Update URL expiry
- [x] DELETE /api/v1/urls/{shortCode} - Soft delete URL
- [x] GET /api/v1/stats/{shortCode} - Retrieve URL statistics
- [x] GET /api/v1/health - Health check with dependency status
- [x] Hybrid API versioning (management endpoints versioned, redirect clean)

**Analytics & Monitoring**
- [x] Click count tracking with atomic DynamoDB ADD operation
- [x] SQS integration for analytics events (synchronous for Lambda)
- [x] Logging (SLF4J) in service layer and exception handler

**AWS Infrastructure**
- [x] AWS CDK infrastructure (Python) with DynamoDB tables
- [x] Lambda function (Spring Boot, 512MB, 10s timeout)
- [x] API Gateway REST API with request throttling (50 req/s, 100 burst)
- [x] AWS Serverless Java Container integration (StreamLambdaHandler)
- [x] Maven Shade plugin for Lambda-compatible JAR
- [x] Deployed to AWS and end-to-end tested

**Testing & Documentation**
- [x] Unit tests (UrlService: 19, TokenService: 10, Controllers: 26, Base62Encoder)
- [x] Swagger/OpenAPI documentation
- [x] Demo UI (index.html) with HomeController

### 🔴 Pending
- [ ] API Authentication (AWS Cognito or API Keys)
- [ ] Rate limiting with usage plans (per-user quotas, API keys)
- [ ] Integration tests (full stack testing)
- [ ] CloudWatch dashboards (metrics visualization)
- [ ] Bot detection and prevention
- [ ] Performance testing and load testing
- [ ] SQS consumer Lambda (process analytics events)
- [ ] Test end-to-end locally with DynamoDB Local
- [ ] API versioning strategy documentation
- [ ] Backup and disaster recovery plan

### 🔮 Future Enhancements
- [ ] Structured JSON logging (CloudWatch Logs Insights)
- [ ] URL caching layer (ElastiCache/Redis for hot URLs)
- [ ] Custom domain support (Route 53 + CloudFront)
- [ ] QR code generation for short URLs
- [ ] Bulk URL shortening API
- [ ] URL preview/metadata extraction
- [ ] Geographic analytics (country/city-level)
- [ ] A/B testing support (multiple destinations)
- [ ] Scheduled URL activation/deactivation
- [ ] Webhook notifications for URL events


## ✨ Features
- **Convert long URLs into short URLs**
- **Redirect short URLs to original URLs**
- **Collision-free short code generation**
- **Horizontally scalable architecture**
- **Analytics collection via SQS**
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
- Range-based ID allocation (100 IDs at a time)
- Reduced contention on DynamoDB
- Acceptable ID loss on Lambda termination
- TokenService as internal service class (not separate Lambda)
- In-memory caching for performance


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
  - Single Lambda containing all services
  - Handles `POST /shorten`
  - Handles `GET /{shortCode}` redirects (301 / 302)
  - TokenService (in-process) allocates unique ID ranges (100 IDs at a time)
  - Uses DynamoDB atomic increment (ADD operation)
  - Caches allocated range in Lambda memory
  - Publishes analytics events to SQS (synchronous for Lambda compatibility)

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

**Capacity Mode:** On-Demand  
**Access Pattern:** Atomic increment using ADD operation

---

## 🌐 API Endpoints

### 1. Health Check
```http
GET /api/v1/health

Response: 200 OK
{
  "status": "UP",
  "service": "quicklink",
  "version": "1.0.0",
  "timestamp": 1704067200,
  "checks": {
    "dynamodb": "UP",
    "sqs": "UP"
  }
}
```

### 2. Create Short URL
```http
POST /api/v1/shorten
Content-Type: application/json

Request:
{
  "url": "https://example.com/very/long/url",
  "customAlias": "mylink",  // Optional
  "expiryInDays": 30          // Optional (1-365 days)
}

Response: 201 Created
{
  "shortCode": "aB3xY9z",
  "shortUrl": "https://skt.inc/aB3xY9z",
  "longUrl": "https://example.com/very/long/url",
  "createdAt": 1704067200,
  "expiresAt": 1706659200     // null if no expiry
}
```

### 3. Redirect to Original URL
```http
GET /{shortCode}

Response: 301 Moved Permanently
Location: https://example.com/very/long/url
```

### 4. Update URL Expiry
```http
PATCH /api/v1/urls/{shortCode}
Content-Type: application/json

Request:
{
  "expiryInDays": 30  // 1-365 days, or null to remove expiry
}

Response: 200 OK
{
  "shortCode": "aB3xY9z",
  "shortUrl": "https://skt.inc/aB3xY9z",
  "longUrl": "https://example.com/very/long/url",
  "createdAt": 1704067200,
  "expiresAt": 1706659200
}
```

### 5. Delete URL (Soft Delete)
```http
DELETE /api/v1/urls/{shortCode}

Response: 204 No Content
```

### 6. Get URL Statistics
```http
GET /api/v1/stats/{shortCode}

Response: 200 OK
{
  "shortCode": "aB3xY9z",
  "longUrl": "https://example.com/very/long/url",
  "clickCount": 42,
  "createdAt": 1704067200,
  "expiresAt": null,
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
- **AWS SDK:** AWS SDK for Java v2 (Enhanced Client)

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
        <groupId>org.springdoc</groupId>
        <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-devtools</artifactId>
    </dependency>
</dependencies>
```

---

## 🚀 Getting Started

### Prerequisites
- Java 17+
- Maven 3.8+
- Node.js 14+ (for AWS CDK CLI)
- Python 3.12+ (for CDK infrastructure code)
- AWS CLI configured (for deployment)
- AWS CDK CLI installed (for deployment)

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
curl http://localhost:8080/api/v1/health

# View Swagger UI (local)
http://localhost:8080/swagger-ui.html

# View OpenAPI JSON spec
http://localhost:8080/v3/api-docs
```

### Deploy to AWS

#### Step 1: Install AWS CDK CLI
```bash
# Install CDK CLI globally (requires Node.js)
npm install -g aws-cdk

# Verify installation
cdk --version  # Should show 2.x.x
```

#### Step 2: Configure AWS Credentials
```bash
# Configure AWS CLI with your credentials
aws configure
# Enter: Access Key ID, Secret Access Key, Region (us-east-1), Output format (json)

# Verify credentials
aws sts get-caller-identity
```

#### Step 3: Build Java Application
```bash
# Build Spring Boot JAR
mvn clean package

# Verify JAR exists
ls -l target/quicklink-1.0.0.jar
```

#### Step 4: Setup CDK Infrastructure
```bash
# Navigate to infrastructure directory
cd infrastructure

# Create Python virtual environment
python3 -m venv .venv

# Activate virtual environment
# On Windows:
.venv\Scripts\activate
# On Mac/Linux:
source .venv/bin/activate

# Install CDK dependencies
pip install -r requirements.txt
```

#### Step 5: Bootstrap CDK (First Time Only)
```bash
# Bootstrap CDK in your AWS account
cdk bootstrap

# This creates:
# - S3 bucket for CDK assets
# - IAM roles for deployments
# - CloudFormation stack: CDKToolkit
```

#### Step 6: Deploy Infrastructure
```bash
# Preview changes (optional)
cdk diff

# Deploy stack
cdk deploy

# Confirm deployment when prompted
# Wait 5-10 minutes for deployment to complete
```

#### Step 7: Initialize Token Counter
```bash
# After deployment, initialize the global counter
aws dynamodb put-item \
  --table-name quicklink-tokens \
  --item '{
    "tokenId": {"S": "global_counter"},
    "currentRangeEnd": {"N": "0"},
    "lastUpdated": {"N": "'$(date +%s)'"}
  }'

# Verify initialization
aws dynamodb get-item \
  --table-name quicklink-tokens \
  --key '{"tokenId": {"S": "global_counter"}}'
```

#### Step 8: Test Deployment

##### Option A: Using cURL

```bash
# Get API Gateway URL from CloudFormation outputs
aws cloudformation describe-stacks \
  --stack-name QuickLinkStack \
  --query 'Stacks[0].Outputs[?OutputKey==`ApiUrl`].OutputValue' \
  --output text

# Test health endpoint
curl https://YOUR_API_URL/api/v1/health

# Test shorten endpoint
curl -X POST https://YOUR_API_URL/api/v1/shorten \
  -H "Content-Type: application/json" \
  -d '{"url": "https://example.com/long-url"}'

# Test redirect (use shortCode from response)
curl -L https://YOUR_API_URL/0000001
```

##### Option B: Using Postman

**1. Get API URL**
```bash
aws cloudformation describe-stacks \
  --stack-name QuickLinkStack \
  --query 'Stacks[0].Outputs[?OutputKey==`ApiUrl`].OutputValue' \
  --output text
```

**2. Test Health Check**
- Method: `GET`
- URL: `https://YOUR_API_URL/api/v1/health`
- Expected: `200 OK` with JSON response

**3. Test Create Short URL**
- Method: `POST`
- URL: `https://YOUR_API_URL/api/v1/shorten`
- Headers: `Content-Type: application/json`
- Body (raw JSON):
```json
{
  "url": "https://example.com/test"
}
```
- Expected: `201 Created` with shortCode

**4. Test Redirect**
- Method: `GET`
- URL: `https://YOUR_API_URL/0000001` (use shortCode from step 3)
- Postman Settings: Disable "Automatically follow redirects" to see 301 response
- Expected: `301 Moved Permanently` with Location header

**Note:** First request will take 5-10 seconds (Spring Boot cold start). Subsequent requests will be fast (~100-200ms).

##### Option C: Using OpenAPI Specification

**Get OpenAPI JSON spec:**
```bash
curl https://YOUR_API_URL/v3/api-docs > openapi.json
```

**Then import into:**
- **Swagger Editor**: https://editor.swagger.io (paste JSON)
- **Postman**: Import → Upload openapi.json
- **Insomnia**: Import → From File

**Note:** 
- Swagger UI doesn't work on Lambda with AWS Serverless Java Container due to WebJar static resource serving limitations
- OpenAPI spec export works perfectly and can be used with external tools
- For interactive API testing on AWS, use Postman or cURL (recommended)
- Swagger UI works great for local development (http://localhost:8080/swagger-ui.html)

#### Useful CDK Commands
```bash
cdk ls          # List all stacks
cdk synth       # Synthesize CloudFormation template
cdk diff        # Show differences between deployed and local
cdk deploy      # Deploy stack
cdk destroy     # Delete all resources
```

---

## 📚 Project Structure

```
quicklink/
├── README.md
├── APPROACH.md
├── docs/
│   ├── 01-loadbalancer-hld.png
│   ├── 02-tokenservice-hld.png
│   └── 03-serverless-hld.png
├── pom.xml
├── src/
│   ├── main/
│   │   ├── java/inc/skt/quicklink/
│   │   │   ├── QuickLinkApplication.java
│   │   │   ├── config/
│   │   │   │   └── DynamoDbConfig.java
│   │   │   ├── controller/
│   │   │   │   ├── HealthController.java
│   │   │   │   └── UrlController.java
│   │   │   ├── dto/
│   │   │   │   ├── ErrorResponse.java
│   │   │   │   ├── HealthResponse.java
│   │   │   │   ├── ShortenRequest.java
│   │   │   │   └── ShortenResponse.java
│   │   │   ├── exception/
│   │   │   │   ├── AliasAlreadyExistsException.java
│   │   │   │   ├── GlobalExceptionHandler.java
│   │   │   │   ├── InvalidAliasException.java
│   │   │   │   ├── InvalidUrlException.java
│   │   │   │   ├── UrlExpiredException.java
│   │   │   │   └── UrlNotFoundException.java
│   │   │   ├── model/
│   │   │   │   ├── UrlMapping.java
│   │   │   │   └── TokenMetadata.java
│   │   │   ├── repository/
│   │   │   │   ├── UrlRepository.java
│   │   │   │   ├── InMemoryUrlRepository.java
│   │   │   │   ├── DynamoDbUrlRepository.java
│   │   │   │   ├── TokenRepository.java
│   │   │   │   └── DynamoDbTokenRepository.java
│   │   │   ├── service/
│   │   │   │   ├── UrlService.java
│   │   │   │   └── TokenService.java
│   │   │   └── util/
│   │   │       └── Base62Encoder.java
│   │   └── resources/
│   │       └── application.yml
│   └── test/
│       └── java/inc/skt/quicklink/
│           ├── controller/
│           │   ├── HealthControllerTest.java
│           │   └── UrlControllerTest.java
│           ├── service/
│           │   ├── TokenServiceTest.java
│           │   └── UrlServiceTest.java
│           └── util/
│               └── Base62EncoderTest.java
└── infrastructure/
    ├── requirements.txt
    ├── cdk.json
    ├── app.py
    └── quicklink_stack.py
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



## 👤 Author

Built as a learning project to demonstrate system design and AWS serverless architecture.

