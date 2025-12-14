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
- **URL Shortener Lambda (FastAPI)**
  - Handles `POST /shorten`
  - Handles `GET /{shortCode}` redirects (301 / 302)
  - Invokes Token Service Lambda internally
  - Publishes analytics events asynchronously to SQS

- **Token Service Lambda**
  - Allocates unique ID ranges
  - Uses DynamoDB for atomic range tracking
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
| Attribute | Description |
|---------|-------------|
| shortCode (PK) | Short URL identifier |
| longUrl | Original URL |
| createdAt | Creation timestamp |
| expiresAt | Optional expiry |
| clickCount | Optional denormalized metric |

---

### DynamoDB – Token Metadata Table
| Attribute | Description |
|---------|-------------|
| tokenId (PK) | Logical token key |
| currentRangeStart | Start of allocated range |
| currentRangeEnd | End of allocated range |

---

## 🌐 API Endpoints

### Create Short URL
