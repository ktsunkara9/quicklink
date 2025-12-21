# QuickLink – Serverless URL Shortener

QuickLink is a **URL Shortener system** designed to demonstrate **system design thinking**, starting from a traditional load-balanced architecture and evolving into a **fully serverless AWS solution**.

This repository focuses on **HLD → LLD → trade-offs**, making it suitable for **system design interviews, backend roles, and portfolio review**.


## 🚧 Implementation Status

### ✅ Completed
- [x] Project setup (Maven, Spring Boot 3.2, Java 17)
- [x] Health endpoint with dependency checks (GET /api/v1/health)
- [x] DTOs (ShortenRequest, ShortenResponse, HealthResponse, ErrorResponse)
- [x] Domain models (UrlMapping, TokenMetadata)
- [x] Base62 encoder/decoder utility with unit tests
- [x] Repository pattern (UrlRepository interface + implementations)
- [x] DynamoDB integration (Enhanced Client + standard client)
- [x] DynamoDB configuration (DynamoDbConfig)
- [x] Spring Profiles (local, test, prod) with environment-specific beans
- [x] UrlController (POST /api/v1/shorten endpoint)
- [x] UrlService (business logic layer with fail-fast validations)
- [x] TokenService (ID generation with range allocation - RANGE_SIZE=100)
- [x] TokenRepository interface (atomic increment)
- [x] DynamoDbTokenRepository (implementation with atomic ADD)
- [x] TokenService integrated with DynamoDB (range allocation complete)
- [x] UrlService integrated with TokenService (distributed ID generation)
- [x] Custom exception classes (InvalidUrlException, InvalidAliasException, AliasAlreadyExistsException, UrlNotFoundException, UrlExpiredException)
- [x] Global exception handler (@RestControllerAdvice with proper HTTP status mapping)
- [x] Input validation (fail-fast validations: URL format, length, self-referencing, localhost/private IPs, custom alias format, reserved keywords, uniqueness check)
- [x] Custom expiry feature (1-365 days, optional)
- [x] Logging (SLF4J) in service layer and exception handler
- [x] GET /{shortCode} redirect endpoint (301 redirect with expiry and active status checks)
- [x] Hybrid API versioning (management endpoints versioned, redirect endpoint clean)
- [x] Unit tests - UrlService (19 tests)
- [x] Unit tests - TokenService (10 tests)
- [x] Unit tests - UrlController (16 tests - includes redirect endpoint tests)
- [x] Unit tests - HealthController (10 tests)
- [x] Unit tests - Base62Encoder (comprehensive coverage)
- [x] Swagger/OpenAPI documentation
- [x] Spring Boot DevTools for hot reload
- [x] AWS CDK infrastructure (Python)
- [x] CDK stack with DynamoDB tables (quicklink-urls, quicklink-tokens)
- [x] CDK Lambda function definition (Spring Boot, 512MB, 10s timeout)
- [x] CDK API Gateway REST API (with throttling)
- [x] IAM permissions (Lambda to DynamoDB)
- [x] AWS Serverless Java Container integration (StreamLambdaHandler)
- [x] Lambda-specific Spring profile (application-prod.yml)
- [x] CDK synthesis successful (CloudFormation template generated)
- [x] Maven Shade plugin configuration (flat JAR for Lambda)
- [x] JAR build successful (quicklink-1.0.0-aws.jar)
- [x] Deployed to AWS (cdk deploy)
- [x] Token counter initialized in DynamoDB
- [x] End-to-end testing on AWS (POST /api/v1/shorten + GET /{shortCode})
- [x] Verified 301 redirects working correctly

### 🔴 Pending
- [ ] Analytics service (@Async)
- [ ] SQS integration
- [ ] Integration tests
- [ ] Custom domain configuration


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
  - Publishes analytics events asynchronously to SQS using @Async

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

### 4. Get URL Statistics (Future)
```http
GET /api/v1/stats/{shortCode}

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

# View Swagger UI
http://localhost:8080/swagger-ui.html
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

## 📋 TODO

### High Priority
- [ ] Analytics service (@Async)
- [ ] SQS integration for analytics
- [ ] Integration tests (full stack testing)

### Medium Priority
- [ ] Demo UI (index.html with form + demo.html as redirect target)
- [ ] Test end-to-end locally with DynamoDB Local
- [ ] Performance testing and optimization
- [ ] Add CloudWatch dashboards

### Low Priority
- [ ] Configure custom domain: `https://skt.inc`
- [ ] Add API authentication (Cognito)
- [ ] Implement rate limiting per user/IP
- [ ] Bot detection and prevention

---

## 👤 Author

Built as a learning project to demonstrate system design and AWS serverless architecture.

python -c "import aws_cdk as cdk; from quicklink_stack import QuickLinkStack; app = cdk.App(); QuickLinkStack(app, 'QuickLinkStack', env=cdk.Environment(region='us-east-1')); print('Synthesizing...'); result = app.synth(); print(f'Done! Assembly: {result.directory}')"

