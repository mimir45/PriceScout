# Price Comparator

**A sophisticated smartphone price comparison platform that aggregates and normalizes product data from multiple e-commerce stores in Azerbaijan.**

## Table of Contents

- [Overview](#overview)
- [Architecture](#architecture)
- [Technology Stack](#technology-stack)
- [Key Features](#key-features)
- [Project Structure](#project-structure)
- [Data Flow](#data-flow)
- [Setup & Installation](#setup--installation)
- [Configuration](#configuration)
- [API Documentation](#api-documentation)
- [Development Guide](#development-guide)
- [Monitoring](#monitoring)

---

## Overview

Price Comparator is a Spring Boot-based web scraping and price aggregation service that:
- Scrapes smartphone data from 3 major Azerbaijani e-commerce stores (Kontakt, Irshad, Baku Electronics)
- Normalizes product information using intelligent brand/model parsing
- Provides fast search capabilities via Elasticsearch with PostgreSQL fallback
- Caches results using Redis for optimal performance
- Offers RESTful API for price comparison queries

### Supported Stores

| Store | Code | URL |
|-------|------|-----|
| **Kontakt** | `KONTAKT` | https://kontakt.az |
| **Irshad** | `IRSHAD` | https://irshad.az |
| **Baku Electronics** | `BAKU_ELECTRONICS` | https://bakuelectronics.az |

---

## Architecture

### High-Level System Architecture

```
┌─────────────────────────────────────────────────────────────────────────┐
│                           CLIENT APPLICATIONS                           │
│                        (Web, Mobile, API Consumers)                     │
└────────────────────────────────┬────────────────────────────────────────┘
                                 │ HTTP/REST
                                 ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                         SPRING BOOT APPLICATION                         │
│  ┌──────────────────────────────────────────────────────────────────┐  │
│  │                       API LAYER (Controllers)                    │  │
│  │  • OfferSearchController    • ScraperAdminController            │  │
│  └──────────────────┬──────────────────────────┬────────────────────┘  │
│                     │                          │                        │
│  ┌──────────────────▼──────────────────────────▼────────────────────┐  │
│  │                    SERVICE LAYER (Orchestrators)                 │  │
│  │  • SearchOrchestrator       • ScraperOrchestrator               │  │
│  │  • ProductNormalizationService                                  │  │
│  └─────┬─────────────────┬─────────────────────┬────────────────────┘  │
│        │                 │                     │                        │
│  ┌─────▼─────────┐ ┌─────▼──────────┐ ┌───────▼─────────────────────┐ │
│  │  Search Svc   │ │  Scraper Impl  │ │  Persistence Services     │ │
│  │ • Elastic     │ │ • Kontakt      │ │ • OfferPersistenceService │ │
│  │ • JPA         │ │ • Irshad       │ │ • ProductMatchingService  │ │
│  │ • Cache       │ │ • BakuElec     │ │                           │ │
│  └───────────────┘ └────────────────┘ └───────────────────────────┘ │
│                                                                         │
└────┬──────────────────────┬─────────────────────┬─────────────────────┘
     │                      │                     │
     ▼                      ▼                     ▼
┌──────────┐        ┌──────────────┐      ┌─────────────┐
│PostgreSQL│        │ Elasticsearch│      │    Redis    │
│ (Primary)│        │   (Search)   │      │   (Cache)   │
└──────────┘        └──────────────┘      └─────────────┘
```

### Component Architecture

```
┌───────────────────────────────────────────────────────────────────────┐
│                         SCRAPING PIPELINE                             │
├───────────────────────────────────────────────────────────────────────┤
│                                                                       │
│  1. TRIGGER                                                           │
│     ┌─────────────────┐         ┌──────────────────┐                │
│     │ Daily Scheduler │────────▶│ Manual API Call  │                │
│     │   (2 AM Cron)   │         │  /api/scrape/*   │                │
│     └────────┬────────┘         └────────┬─────────┘                │
│              └──────────────┬────────────┘                           │
│                             ▼                                         │
│  2. ORCHESTRATION                                                     │
│     ┌────────────────────────────────────────┐                       │
│     │      ScraperOrchestrator               │                       │
│     │  • Manages parallel shop scraping      │                       │
│     │  • Virtual thread executor             │                       │
│     │  • Job tracking & error handling       │                       │
│     └───────────┬────────────────────────────┘                       │
│                 │                                                     │
│                 ▼                                                     │
│  3. SCRAPING (Parallel Execution)                                    │
│     ┌──────────────┐  ┌──────────────┐  ┌─────────────────┐        │
│     │   Kontakt    │  │   Irshad     │  │ BakuElectronics │        │
│     │   Scraper    │  │   Scraper    │  │    Scraper      │        │
│     │              │  │              │  │                 │        │
│     │ • Selenium   │  │ • Jsoup      │  │ • Jsoup         │        │
│     │ • Anti-bot   │  │ • Load More  │  │ • JSON extract  │        │
│     └──────┬───────┘  └──────┬───────┘  └────────┬────────┘        │
│            └──────────────────┴──────────────────┬┘                 │
│                                                   ▼                  │
│  4. NORMALIZATION                                                    │
│     ┌──────────────────────────────────────────────────┐            │
│     │     ProductNormalizationService                  │            │
│     │  • Brand/Model parsing (BrandModelParser)       │            │
│     │  • Color extraction (ColorExtractor)             │            │
│     │  • Text normalization (TextNormalizationUtil)    │            │
│     └───────────────────┬──────────────────────────────┘            │
│                         ▼                                            │
│  5. PERSISTENCE                                                      │
│     ┌──────────────────────────────────────────────────┐            │
│     │       OfferPersistenceService                    │            │
│     │  • Product matching (fuzzy similarity)           │            │
│     │  • Offer upsert (create/update)                  │            │
│     │  • Shop & product relationship management        │            │
│     └───────────────────┬──────────────────────────────┘            │
│                         ▼                                            │
│  6. INDEXING                                                         │
│     ┌──────────────────────────────────────────────────┐            │
│     │     ElasticsearchIndexService                    │            │
│     │  • Bulk indexing to Elasticsearch                │            │
│     │  • Document transformation                       │            │
│     └──────────────────────────────────────────────────┘            │
│                                                                      │
└──────────────────────────────────────────────────────────────────────┘
```

### Search Flow

```
┌─────────────────────────────────────────────────────────────────────┐
│                          SEARCH REQUEST                             │
│              GET /api/offers/search?query=iPhone+15                 │
└───────────────────────────┬─────────────────────────────────────────┘
                            ▼
                ┌───────────────────────┐
                │  OfferSearchController│
                └───────────┬───────────┘
                            ▼
                ┌───────────────────────┐
                │  SearchOrchestrator   │
                │  (Circuit Breaker)    │
                └───────┬───────────────┘
                        │
                        ▼
            ┌───────────────────────┐
            │  Check Redis Cache    │
            └───────┬───────────────┘
                    │
         ┌──────────┴───────────┐
         │                      │
    Cache HIT              Cache MISS
         │                      │
         ▼                      ▼
    ┌────────┐      ┌──────────────────────┐
    │ Return │      │  Elasticsearch       │
    │ Cached │      │  (with Circuit Break)│
    │ Result │      └──────┬───────────────┘
    └────────┘             │
                           │
                  ┌────────┴─────────┐
                  │                  │
              Success           Failure/CB Open
                  │                  │
                  ▼                  ▼
         ┌─────────────────┐  ┌──────────────┐
         │ Elasticsearch   │  │  JPA Search  │
         │    Results      │  │  (Fallback)  │
         └────────┬────────┘  └──────┬───────┘
                  └────────┬──────────┘
                           ▼
                  ┌─────────────────┐
                  │  Cache Result   │
                  │   in Redis      │
                  └────────┬────────┘
                           ▼
                  ┌─────────────────┐
                  │ Return Response │
                  └─────────────────┘
```

---

## Technology Stack

### Core Framework
- **Java 21** - Latest LTS with Virtual Threads support
- **Spring Boot 3.5.7** - Modern Spring ecosystem
- **Maven** - Dependency management

### Data & Persistence
- **PostgreSQL 16** - Primary relational database
- **Spring Data JPA** - ORM and repository abstraction
- **Hibernate** - JPA implementation

### Search & Caching
- **Elasticsearch 8.18.8** - Full-text search engine
- **Redis 7** - In-memory cache (with persistence)
- **Lettuce** - Redis client
- **Kibana 8.18.8** - Elasticsearch visualization

### Web Scraping
- **Jsoup 1.17.2** - HTML parsing and static scraping
- **Selenium 4.27.0** - Browser automation for JavaScript-heavy sites
- **Chrome WebDriver** - Headless browser for anti-bot bypass

### Resilience & Monitoring
- **Resilience4j 2.1.0** - Circuit breaker pattern
- **Spring Boot Actuator** - Health checks and metrics
- **Prometheus** - Metrics format (ready)

### API & Documentation
- **SpringDoc OpenAPI 2.8.6** - Interactive API documentation (Swagger UI)

### Development Tools
- **Lombok** - Boilerplate code reduction
- **Testcontainers 1.19.3** - Integration testing with Docker

---

## Key Features

### 1. Multi-Store Web Scraping
- **Intelligent Scraping Strategy**
  - Static HTML scraping with Jsoup (fast, low resource)
  - Selenium-based dynamic scraping (anti-bot bypass)
  - Retry mechanism with exponential backoff
  - Polite rate limiting (500ms between requests)

- **Anti-Bot Protection**
  - Stealth mode JavaScript execution
  - Navigator property manipulation
  - Real browser user-agent simulation
  - Scroll-based lazy loading activation

### 2. Product Normalization
- **Brand & Model Extraction**
  - Pattern-based regex parsing for 20+ brands
  - Supports: Apple, Samsung, Xiaomi, Huawei, OnePlus, etc.
  - Handles variations: "iPhone 15 Pro Max" → Brand: Apple, Model: 15 Pro Max

- **Color Extraction**
  - Multi-language color detection (English, Azerbaijani, Russian)
  - Handles complex descriptions: "Space Black", "Midnight Blue"

- **Text Normalization**
  - Removes special characters and extra spaces
  - Standardizes product names for matching
  - Preserves essential product identifiers

### 3. Smart Product Matching
- **Fuzzy Similarity Algorithm**
  - Levenshtein distance calculation
  - Configurable similarity threshold (80% default)
  - Prevents duplicate products from different shops
  - Groups offers under canonical product entries

### 4. High-Performance Search
- **Dual Search Strategy**
  - Primary: Elasticsearch full-text search (fast, scalable)
  - Fallback: JPA/PostgreSQL search (reliable)
  - Circuit breaker pattern for automatic failover

- **Advanced Filtering**
  - Full-text search by product name
  - Filter by condition (New/Used)
  - Filter by color
  - Filter by shop(s)
  - Price range filtering (min/max)

### 5. Caching Layer
- **Redis-Based Caching**
  - 6-hour TTL for search results
  - Cache key generation with query parameters
  - Automatic cache invalidation after scraping
  - Reduces database load and improves response time

### 6. Scheduled Automation
- **Daily Scraping Job**
  - Runs daily at 2 AM (configurable)
  - Parallel scraping with virtual threads
  - Job tracking and history
  - Error logging and recovery

### 7. RESTful API
- **Search Endpoint**
  - `/api/offers/search?query=iPhone&color=Black&condition=NEW`
  - Paginated results
  - JSON response format

- **Admin Endpoints**
  - `/api/scrape` - Trigger scraping for all shops
  - `/api/scrape/{shopCode}` - Scrape specific shop
  - Job history and statistics

---

## Project Structure

```
price-comparator/
├── src/main/java/com/samir/pricecomparator/
│   ├── PriceComparatorApplication.java          # Main entry point
│   │
│   ├── config/                                  # Configuration classes
│   │   ├── ElasticsearchConfig.java             # Elasticsearch client setup
│   │   ├── RedisConfig.java                     # Redis cache configuration
│   │   ├── SchedulingConfig.java                # Scheduled task configuration
│   │   └── ShopBootstrap.java                   # Initialize shop data
│   │
│   ├── controller/                              # REST API controllers
│   │   ├── OfferSearchController.java           # Search API endpoint
│   │   └── ScraperAdminController.java          # Admin scraping endpoints
│   │
│   ├── dto/                                     # Data Transfer Objects
│   │   ├── NormalizedProduct.java               # Normalized product DTO
│   │   ├── OfferDto.java                        # Offer response DTO
│   │   ├── OfferSearchRequest.java              # Search request DTO
│   │   ├── OfferSearchResponse.java             # Search response DTO
│   │   └── ScrapedProductDto.java               # Raw scraped data DTO
│   │
│   ├── entity/                                  # JPA entities
│   │   ├── OfferDocument.java                   # Elasticsearch document
│   │   ├── Product.java                         # Product entity
│   │   ├── ProductOffer.java                    # Product offer entity
│   │   ├── ScrapingJob.java                     # Scraping job tracking
│   │   └── Shop.java                            # Shop entity
│   │
│   ├── repository/                              # Data access layer
│   │   ├── OfferElasticsearchRepository.java    # Elasticsearch repo
│   │   ├── ProductOfferRepository.java          # Offer JPA repo
│   │   ├── ProductRepository.java               # Product JPA repo
│   │   ├── ScrapingJobRepository.java           # Job tracking repo
│   │   └── ShopRepository.java                  # Shop JPA repo
│   │
│   ├── scheduler/                               # Scheduled tasks
│   │   └── DailyScraperJob.java                 # Daily scraping scheduler
│   │
│   ├── search/                                  # Search specifications
│   │   └── OfferSpecifications.java             # JPA Criteria API specs
│   │
│   ├── service/                                 # Business logic
│   │   ├── cache/
│   │   │   ├── CacheKeyGenerator.java           # Redis cache key generator
│   │   │   └── CacheService.java                # Cache abstraction layer
│   │   │
│   │   ├── normalization/
│   │   │   └── ProductNormalizationService.java # Product normalization
│   │   │
│   │   ├── persistence/
│   │   │   ├── OfferPersistenceService.java     # Offer CRUD operations
│   │   │   └── ProductMatchingService.java      # Product matching logic
│   │   │
│   │   ├── scraper/
│   │   │   ├── AbstractShopScraper.java         # Base scraper template
│   │   │   ├── ScraperOrchestrator.java         # Scraping orchestration
│   │   │   ├── ShopScraperFactory.java          # Scraper factory pattern
│   │   │   └── impl/
│   │   │       ├── BakuElectronicsScraper.java  # Baku Electronics impl
│   │   │       ├── IrshadScraper.java           # Irshad impl
│   │   │       └── KontaktScraper.java          # Kontakt impl
│   │   │
│   │   └── search/
│   │       ├── ElasticSearchService.java        # Elasticsearch service
│   │       ├── ElasticsearchIndexService.java   # Indexing service
│   │       ├── JpaSearchService.java            # JPA fallback search
│   │       └── SearchOrchestrator.java          # Search coordination
│   │
│   └── util/                                    # Utility classes
│       ├── BrandModelParser.java                # Brand/model extraction
│       ├── ColorExtractor.java                  # Color extraction
│       ├── SeleniumWebDriverManager.java        # WebDriver lifecycle mgmt
│       └── TextNormalizationUtil.java           # Text normalization
│
├── src/main/resources/
│   └── application.yml                          # Application configuration
│
├── docker-compose.yml                           # Infrastructure services
├── pom.xml                                      # Maven dependencies
└── README.md                                    # This file
```

---

## Data Flow

### 1. Scraping Flow

```
[Scheduler/API Call]
       │
       ▼
[ScraperOrchestrator] ─────────────────┐
       │                                │
       ├─────────────┬─────────────┐    │
       ▼             ▼             ▼    │
[KontaktScraper] [IrshadScraper] [BakuElecScraper]
       │             │             │    │
       │ Selenium    │ Jsoup       │ Jsoup
       │ (Anti-bot)  │ (Load More) │ (JSON)
       │             │             │    │
       ▼             ▼             ▼    │
[ScrapedProductDto List] ──────────────┘
       │
       ▼
[ProductNormalizationService]
  • BrandModelParser
  • ColorExtractor
  • TextNormalizationUtil
       │
       ▼
[NormalizedProduct List]
       │
       ▼
[OfferPersistenceService]
  • ProductMatchingService (fuzzy match)
  • Product creation/retrieval
  • Offer upsert (create/update)
       │
       ▼
[PostgreSQL Database]
  • products table
  • product_offers table
  • shops table
  • scraping_jobs table
       │
       ▼
[ElasticsearchIndexService]
  • Bulk indexing
       │
       ▼
[Elasticsearch Index]
  • offer_index
       │
       ▼
[Cache Invalidation]
  • Clear Redis cache
```

### 2. Search Flow

```
[User] ──▶ GET /api/offers/search?query=iPhone+15
              │
              ▼
       [OfferSearchController]
              │
              ▼
       [SearchOrchestrator]
              │
              ├─────▶ [Redis Cache Check]
              │            │
              │         Cache HIT ──▶ Return cached result
              │            │
              │         Cache MISS
              │            │
              ▼            ▼
       [Circuit Breaker Active?]
              │
         ┌────┴────┐
         │         │
        CLOSED   OPEN
         │         │
         ▼         ▼
   [Elasticsearch] [JPA Search]
    Search         (Fallback)
         │         │
         └────┬────┘
              │
              ▼
       [Filter Results]
         • price range
         • condition
         • color
         • shop codes
              │
              ▼
       [Cache Result in Redis]
              │
              ▼
       [OfferSearchResponse]
              │
              ▼
       [JSON Response to User]
```

### 3. Database Schema

```
┌─────────────────────────────────────────────────────────────────┐
│                        DATABASE SCHEMA                          │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  shops                                                          │
│  ├── id (PK)                                                    │
│  ├── code (UNIQUE)          [KONTAKT, IRSHAD, BAKU_ELECTRONICS]│
│  ├── name                                                       │
│  ├── base_url                                                   │
│  ├── active                                                     │
│  └── last_scraped_at                                            │
│         │                                                       │
│         │ 1:N                                                   │
│         ▼                                                       │
│  products                   product_offers                      │
│  ├── id (PK)                ├── id (PK)                         │
│  ├── normalized_name        ├── product_id (FK) ───────────────┤
│  ├── brand                  ├── shop_id (FK) ◀─────────────────┘
│  ├── model                  ├── title                           │
│  ├── category               ├── url                             │
│  ├── main_image_url         ├── price                           │
│  ├── created_at             ├── old_price                       │
│  └── updated_at             ├── currency                        │
│         ▲                   ├── condition                       │
│         │                   ├── color                           │
│         │ N:1               ├── availability                    │
│         │                   ├── image_url                       │
│         └───────────────────├── in_stock                        │
│                             ├── first_seen_at                   │
│                             ├── last_seen_at                    │
│                             └── is_active                       │
│                                     │                           │
│                                     │ Indexed to:               │
│                                     ▼                           │
│  scraping_jobs              offer_index (Elasticsearch)         │
│  ├── id (PK)                ├── id                              │
│  ├── shop_id (FK)           ├── productId                       │
│  ├── status                 ├── normalizedName                  │
│  ├── products_found         ├── brand                           │
│  ├── offers_created         ├── model                           │
│  ├── offers_updated         ├── title                           │
│  ├── started_at             ├── price                           │
│  ├── completed_at           ├── shopCode                        │
│  ├── duration_seconds       ├── condition                       │
│  └── error_message          ├── color                           │
│                             └── url                             │
└─────────────────────────────────────────────────────────────────┘
```

---

## Setup & Installation

### Prerequisites

- **Java 21+** (JDK)
- **Maven 3.8+**
- **Docker & Docker Compose** (for infrastructure)
- **Chrome/Chromium** (for Selenium scraping)

### Step 1: Clone Repository

```bash
git clone https://github.com/yourusername/price-comparator.git
cd price-comparator
```

### Step 2: Start Infrastructure Services

```bash
docker-compose up -d
```

This starts:
- PostgreSQL (port 5432)
- Redis (port 6379)
- Elasticsearch (port 9200, 9300)
- Kibana (port 5601)

### Step 3: Verify Services

```bash
# Check PostgreSQL
psql -h localhost -U price_user -d price_comparator

# Check Redis
redis-cli ping

# Check Elasticsearch
curl http://localhost:9200/_cluster/health

# Check Kibana
open http://localhost:5601
```

### Step 4: Build Application

```bash
./mvnw clean package
```

### Step 5: Run Application

```bash
./mvnw spring-boot:run
```

Or run the JAR:

```bash
java -jar target/price-comparator-0.0.1-SNAPSHOT.jar
```

### Step 6: Verify Application

```bash
# Check health
curl http://localhost:8080/actuator/health

# View API documentation
open http://localhost:8080/swagger-ui.html
```

---

## Configuration

### Application Properties

Edit `src/main/resources/application.yml`:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/price_comparator
    username: price_user
    password: price_user

  data:
    redis:
      host: localhost
      port: 6379

  elasticsearch:
    uris: http://localhost:9200

# Scraping Configuration
shops:
  kontakt:
    smartphones-url: "https://kontakt.az/telefoniya/smartfonlar"
  irshad:
    smartphones-url: "https://irshad.az/smartphones"
  baku-electronics:
    smartphones-url: "https://bakuelectronics.az/mobiles"

# Scheduling
scraping:
  enabled: true
  cron: "0 0 2 * * *"  # Daily at 2 AM

# Elasticsearch Configuration
elasticsearch:
  enabled: true
```

### Environment Variables

You can override configuration using environment variables:

```bash
export SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/price_comparator
export SPRING_DATASOURCE_USERNAME=price_user
export SPRING_DATASOURCE_PASSWORD=price_user
export SPRING_DATA_REDIS_HOST=localhost
export SPRING_ELASTICSEARCH_URIS=http://localhost:9200
```

### Docker Compose Configuration

Modify `docker-compose.yml` to change service ports or resource limits:

```yaml
services:
  postgres:
    ports:
      - "5432:5432"  # Change external port if needed

  elasticsearch:
    environment:
      - "ES_JAVA_OPTS=-Xms512m -Xmx512m"  # Adjust heap size
```

---

## API Documentation

### Base URL

```
http://localhost:8080
```

### Interactive API Docs

Access Swagger UI for interactive API documentation:
```
http://localhost:8080/swagger-ui.html
```

### Endpoints

#### 1. Search Offers

```http
GET /api/offers/search
```

**Query Parameters:**

| Parameter | Type | Required | Description | Example |
|-----------|------|----------|-------------|---------|
| `query` | String | ✅ Yes | Product search query | `iPhone 15` |
| `condition` | String | ❌ No | Product condition | `NEW`, `USED` |
| `color` | String | ❌ No | Product color | `Black`, `White` |
| `shop` | String[] | ❌ No | Shop codes (multiple) | `KONTAKT`, `IRSHAD` |
| `minPrice` | BigDecimal | ❌ No | Minimum price | `1000` |
| `maxPrice` | BigDecimal | ❌ No | Maximum price | `2000` |
| `limit` | Integer | ❌ No | Max results (default: 3) | `10` |

**Example Request:**

```bash
curl -X GET "http://localhost:8080/api/offers/search?query=iPhone+15&color=Black&condition=NEW&limit=5"
```

**Example Response:**

```json
{
  "query": "iPhone 15",
  "totalResults": 12,
  "offers": [
    {
      "id": 101,
      "productId": 45,
      "normalizedName": "Apple iPhone 15",
      "title": "Apple iPhone 15 128GB Black",
      "brand": "Apple",
      "model": "15",
      "price": 1899.99,
      "oldPrice": 2199.99,
      "currency": "AZN",
      "condition": "NEW",
      "color": "Black",
      "shopCode": "KONTAKT",
      "shopName": "Kontakt Home",
      "url": "https://kontakt.az/product/12345",
      "imageUrl": "https://kontakt.az/images/iphone15.jpg",
      "inStock": true,
      "firstSeenAt": "2025-12-01T10:30:00",
      "lastSeenAt": "2025-12-10T02:15:00"
    },
    {
      "id": 102,
      "productId": 45,
      "normalizedName": "Apple iPhone 15",
      "title": "iPhone 15 128GB Midnight",
      "brand": "Apple",
      "model": "15",
      "price": 1849.99,
      "oldPrice": null,
      "currency": "AZN",
      "condition": "NEW",
      "color": "Black",
      "shopCode": "IRSHAD",
      "shopName": "Irshad Electronics",
      "url": "https://irshad.az/product/67890",
      "imageUrl": "https://irshad.az/images/iphone15.jpg",
      "inStock": true,
      "firstSeenAt": "2025-12-01T11:00:00",
      "lastSeenAt": "2025-12-10T02:20:00"
    }
  ]
}
```

#### 2. Trigger Scraping (All Shops)

```http
POST /api/scrape
```

**Response:**

```json
{
  "message": "Scraping started for all shops",
  "timestamp": "2025-12-10T15:30:00"
}
```

#### 3. Trigger Scraping (Specific Shop)

```http
POST /api/scrape/{shopCode}
```

**Path Parameters:**

| Parameter | Type | Required | Description | Example |
|-----------|------|----------|-------------|---------|
| `shopCode` | String | ✅ Yes | Shop code | `KONTAKT` |

**Example Request:**

```bash
curl -X POST "http://localhost:8080/api/scrape/KONTAKT"
```

**Response:**

```json
{
  "message": "Scraping started for shop: KONTAKT",
  "timestamp": "2025-12-10T15:30:00"
}
```

#### 4. Health Check

```http
GET /actuator/health
```

**Response:**

```json
{
  "status": "UP",
  "components": {
    "db": {
      "status": "UP",
      "details": {
        "database": "PostgreSQL",
        "validationQuery": "isValid()"
      }
    },
    "redis": {
      "status": "UP",
      "details": {
        "version": "7.0.0"
      }
    },
    "elasticsearch": {
      "status": "UP",
      "details": {
        "cluster_name": "docker-cluster",
        "status": "green"
      }
    }
  }
}
```

---

## Development Guide

### Adding a New Shop Scraper

1. **Create Scraper Class**

```java
package com.samir.pricecomparator.service.scraper.impl;

import com.samir.pricecomparator.dto.ScrapedProductDto;
import com.samir.pricecomparator.entity.Shop;
import com.samir.pricecomparator.service.scraper.AbstractShopScraper;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

@Component
public class NewShopScraper extends AbstractShopScraper {

    public NewShopScraper(Shop shop) {
        super(shop);
    }

    @Override
    protected List<String> extractProductUrls() throws IOException {
        // TODO: Implement URL extraction logic
        Document doc = fetchDocument("https://newshop.az/smartphones");
        return doc.select(".product-link")
                .stream()
                .map(e -> e.attr("href"))
                .map(this::normalizeUrl)
                .toList();
    }

    @Override
    protected Optional<ScrapedProductDto> scrapeProduct(String url) {
        // TODO: Implement product scraping logic
        try {
            Document doc = fetchDocument(url);

            ScrapedProductDto product = ScrapedProductDto.builder()
                    .shopCode(shop.getCode())
                    .title(extractText(doc, ".product-title"))
                    .price(parsePrice(extractText(doc, ".price")))
                    .oldPrice(parsePrice(extractText(doc, ".old-price")))
                    .url(url)
                    .imageUrl(extractAttr(doc, ".product-image", "src"))
                    .inStock(isInStock(extractText(doc, ".availability")))
                    .condition("NEW")
                    .currency("AZN")
                    .build();

            return Optional.of(product);
        } catch (Exception e) {
            log.error("Failed to scrape product: {}", url, e);
            return Optional.empty();
        }
    }
}
```

2. **Register Shop in Database**

Add shop entry via SQL or ShopBootstrap:

```sql
INSERT INTO shops (code, name, base_url, active)
VALUES ('NEW_SHOP', 'New Shop Name', 'https://newshop.az', true);
```

3. **Configure Shop URL**

Add to `application.yml`:

```yaml
shops:
  new-shop:
    smartphones-url: "https://newshop.az/smartphones"
```

4. **Register Scraper in Factory**

Update `ShopScraperFactory.java` if using manual registration.

### Running Tests

```bash
# Run all tests
./mvnw test

# Run specific test
./mvnw test -Dtest=ProductNormalizationServiceTest

# Run with coverage
./mvnw clean verify
```

### Code Style

The project follows standard Java conventions:
- Use Lombok annotations (`@Data`, `@Builder`, `@Slf4j`)
- Follow RESTful naming for API endpoints
- Use meaningful variable and method names
- Add JavaDoc for public APIs
- Log important events and errors

### Debugging

Enable debug logging in `application.yml`:

```yaml
logging:
  level:
    com.samir.pricecomparator: DEBUG
    com.samir.pricecomparator.service.scraper: TRACE
```

---

## Monitoring

Price Comparator features a comprehensive monitoring stack using **Prometheus** for metrics collection and **Grafana** for visualization, providing real-time insights into system performance, search operations, scraping activities, and cache efficiency.

### Monitoring Stack Architecture

```
┌─────────────────────────────────────────────────────────────────────┐
│                      MONITORING ARCHITECTURE                        │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  ┌──────────────────────────────────────────────────────────────┐  │
│  │           Spring Boot Application (Port 8080)                │  │
│  │                                                              │  │
│  │  ┌────────────────────────────────────────────────────────┐ │  │
│  │  │        Micrometer Metrics (Custom + JVM)              │ │  │
│  │  │  • Search Operations    • Cache Performance           │ │  │
│  │  │  • Scraper Jobs         • Elasticsearch Health        │ │  │
│  │  │  • JVM Memory/GC        • HTTP Requests               │ │  │
│  │  └────────────────┬───────────────────────────────────────┘ │  │
│  │                   │                                         │  │
│  │  ┌────────────────▼───────────────────────────────────────┐ │  │
│  │  │   Spring Boot Actuator Endpoint                       │ │  │
│  │  │       GET /actuator/prometheus                        │ │  │
│  │  └────────────────┬───────────────────────────────────────┘ │  │
│  └───────────────────┼──────────────────────────────────────────┘  │
│                      │                                             │
│                      │ Scrape every 10s                            │
│                      ▼                                             │
│  ┌──────────────────────────────────────────────────────────────┐  │
│  │             Prometheus (Port 9090)                           │  │
│  │  • Time-series database                                     │  │
│  │  • Data retention: 15 days                                  │  │
│  │  • Scrape interval: 10 seconds                              │  │
│  │  • Evaluation interval: 15 seconds                          │  │
│  └────────────────────┬─────────────────────────────────────────┘  │
│                       │                                            │
│                       │ PromQL queries                             │
│                       ▼                                            │
│  ┌──────────────────────────────────────────────────────────────┐  │
│  │               Grafana (Port 3000)                            │  │
│  │  • Visual dashboards                                         │  │
│  │  • Real-time graphs                                          │  │
│  │  • Alert management                                          │  │
│  │  • Data exploration                                          │  │
│  └──────────────────────────────────────────────────────────────┘  │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

### Quick Access URLs

| Service | URL | Purpose |
|---------|-----|---------|
| **Grafana Dashboard** | http://localhost:3000 | Visual monitoring dashboards |
| **Prometheus UI** | http://localhost:9090 | Raw metrics & queries |
| **Spring Actuator** | http://localhost:8080/actuator | Health & metrics endpoints |
| **Metrics (Prometheus format)** | http://localhost:8080/actuator/prometheus | Raw Prometheus metrics |
| **Kibana (Elasticsearch)** | http://localhost:5601 | Search data visualization |

**Default Grafana Credentials:**
- Username: `admin`
- Password: `admin` (change on first login)

---

### Available Metrics Categories

#### 1. **Search Metrics**
Track search performance and cache efficiency:

| Metric Name | Type | Description | Tags |
|-------------|------|-------------|------|
| `pricecomparator_search_requests_total` | Counter | Total search requests | `source`, `cache_status` |
| `pricecomparator_search_duration_seconds` | Timer | Search request duration | `source`, `cache_status` |
| `pricecomparator_search_results_total` | Counter | Number of search results | `source` |
| `pricecomparator_search_cache_hits_total` | Counter | Redis cache hits | - |
| `pricecomparator_search_cache_misses_total` | Counter | Redis cache misses | - |
| `pricecomparator_search_fallback_total` | Counter | Circuit breaker fallbacks to JPA | - |

**Source values:**
- `elasticsearch` - Primary search engine
- `jpa` - PostgreSQL fallback
- `cache` - Redis cache
- `jpa_fallback` - Circuit breaker activated

**Cache status:**
- `hit` - Found in Redis cache
- `miss` - Not in cache, fetched from source

#### 2. **Scraper Metrics**
Monitor web scraping operations:

| Metric Name | Type | Description | Tags |
|-------------|------|-------------|------|
| `pricecomparator_scraper_active_jobs` | Gauge | Currently running scraping jobs | - |
| `pricecomparator_scraper_attempts_total` | Counter | Total scraper attempts | `shop`, `status` |
| `pricecomparator_scraper_duration_seconds` | Timer | Time to scrape a shop | `shop`, `status` |
| `pricecomparator_scraper_products_found_total` | Counter | Products discovered | `shop` |
| `pricecomparator_scraper_products_created_total` | Counter | New products added | `shop` |
| `pricecomparator_scraper_products_updated_total` | Counter | Existing products updated | `shop` |

**Shop values:**
- `KONTAKT` - Kontakt Home
- `IRSHAD` - Irshad Electronics
- `BAKU_ELECTRONICS` - Baku Electronics

**Status values:**
- `success` - Scraping completed successfully
- `failure` - Scraping failed with errors

#### 3. **Cache Metrics**
Monitor Redis cache operations:

| Metric Name | Type | Description | Tags |
|-------------|------|-------------|------|
| `pricecomparator_cache_operations_total` | Counter | Cache operations | `operation`, `cache_name`, `result` |
| `pricecomparator_cache_evictions_total` | Counter | Cache evictions | `cache_name` |

**Operation values:**
- `get` - Cache read
- `put` - Cache write
- `delete` - Cache invalidation

**Result values:**
- `hit` - Found in cache
- `miss` - Not found in cache
- `success` - Operation succeeded
- `error` - Operation failed

#### 4. **Elasticsearch Metrics**
Track search engine health:

| Metric Name | Type | Description | Tags |
|-------------|------|-------------|------|
| `pricecomparator_elasticsearch_operations_total` | Counter | Elasticsearch operations | `operation`, `result` |
| `pricecomparator_elasticsearch_indexing_duration_seconds` | Timer | Document indexing time | `result` |
| `pricecomparator_elasticsearch_documents_indexed_total` | Counter | Documents indexed | - |

#### 5. **Normalization Metrics**
Monitor product data normalization:

| Metric Name | Type | Description | Tags |
|-------------|------|-------------|------|
| `pricecomparator_normalization_operations_total` | Counter | Normalization operations | `type`, `status` |
| `pricecomparator_normalization_duration_seconds` | Timer | Normalization time | `type` |

**Type values:**
- `brand_model` - Brand/model extraction
- `color` - Color extraction
- `text` - Text normalization

#### 6. **JVM & System Metrics** (Built-in)
Spring Boot Actuator provides standard JVM metrics:

- **Memory**: `jvm_memory_used_bytes`, `jvm_memory_max_bytes`
- **Garbage Collection**: `jvm_gc_pause_seconds`, `jvm_gc_memory_allocated_bytes`
- **Threads**: `jvm_threads_live`, `jvm_threads_daemon`
- **CPU**: `process_cpu_usage`, `system_cpu_usage`
- **Uptime**: `process_uptime_seconds`
- **HTTP**: `http_server_requests_seconds`

---

### Grafana Dashboards

The Price Comparator includes a pre-configured Grafana dashboard with multiple panels for comprehensive monitoring.

#### Dashboard Layout

```
┌───────────────────────────────────────────────────────────────────┐
│                    Price Comparator Dashboard                     │
├───────────────────────────────────────────────────────────────────┤
│                                                                   │
│  ┌─────────────────────  System Overview  ─────────────────────┐ │
│  │                                                              │ │
│  │  [Uptime]  [Memory Usage]  [CPU Usage]  [Active Threads]   │ │
│  │   24.5h       2.1 GB         45%           42 threads       │ │
│  └──────────────────────────────────────────────────────────────┘ │
│                                                                   │
│  ┌─────────────────────  Search Performance  ─────────────────┐  │
│  │                                                             │  │
│  │  [Search Requests/sec]      [Search Duration (p95)]        │  │
│  │       12.5 req/s                  45ms                      │  │
│  │                                                             │  │
│  │  📊 Search Request Rate Over Time                          │  │
│  │     (Line graph showing elasticsearch vs JPA vs cache)     │  │
│  │                                                             │  │
│  │  📊 Search Duration Distribution                           │  │
│  │     (Histogram showing p50, p95, p99 latencies)           │  │
│  └─────────────────────────────────────────────────────────────┘  │
│                                                                   │
│  ┌─────────────────────  Cache Performance  ──────────────────┐  │
│  │                                                             │  │
│  │  [Cache Hit Rate]        [Cache Operations/sec]            │  │
│  │      89.2%                    24.3 ops/s                    │  │
│  │                                                             │  │
│  │  📊 Cache Hit vs Miss Rate                                 │  │
│  │     (Stacked area chart)                                   │  │
│  │                                                             │  │
│  │  📊 Cache Operations by Type                               │  │
│  │     (Pie chart: get/put/delete)                           │  │
│  └─────────────────────────────────────────────────────────────┘  │
│                                                                   │
│  ┌─────────────────────  Scraping Activity  ──────────────────┐  │
│  │                                                             │  │
│  │  [Active Jobs]  [Last Scrape Duration]  [Products Found]  │  │
│  │       0              2m 34s                  284 items      │  │
│  │                                                             │  │
│  │  📊 Scraper Success Rate by Shop                           │  │
│  │     (Bar chart: KONTAKT, IRSHAD, BAKU_ELECTRONICS)        │  │
│  │                                                             │  │
│  │  📊 Products Created vs Updated Over Time                  │  │
│  │     (Line graph showing data ingestion trends)            │  │
│  └─────────────────────────────────────────────────────────────┘  │
│                                                                   │
│  ┌─────────────────────  Circuit Breaker  ────────────────────┐  │
│  │                                                             │  │
│  │  [Elasticsearch Status]  [Fallback Rate]                   │  │
│  │        CLOSED              0.2%                             │  │
│  │                                                             │  │
│  │  📊 Circuit Breaker State Timeline                         │  │
│  │     (Shows CLOSED/OPEN/HALF_OPEN transitions)             │  │
│  └─────────────────────────────────────────────────────────────┘  │
│                                                                   │
└───────────────────────────────────────────────────────────────────┘
```

#### Key Dashboard Panels Explained

**1. System Overview Section**
- **Application Uptime**: Time since last restart (threshold: red if < 1 hour)
- **Memory Usage**: JVM heap usage with max heap threshold
- **CPU Usage**: System and process CPU utilization
- **Active Threads**: Number of active application threads
- **Garbage Collection**: GC pause time and frequency

**2. Search Performance Section**
- **Request Rate Graph**: Shows search traffic patterns
  - Green line: Elasticsearch searches
  - Blue line: JPA fallback searches
  - Yellow line: Cache hits
  - Useful for identifying peak usage times

- **Search Duration Heatmap**: Response time percentiles
  - p50 (median): Should be < 50ms for cache hits
  - p95: Should be < 200ms for Elasticsearch
  - p99: Should be < 500ms
  - Spikes indicate performance issues

- **Source Distribution**: Pie chart showing where search requests are served from
  - High cache hit ratio (>80%) is optimal
  - Low Elasticsearch usage indicates good caching
  - JPA fallback should be minimal (<5%)

**3. Cache Performance Section**
- **Cache Hit Rate**: Percentage of requests served from Redis
  - Target: > 85% for optimal performance
  - Low hit rate indicates cache warming needed or high query variety

- **Cache Operations Timeline**: Shows get/put/delete patterns
  - Put spikes after scraping jobs
  - Get patterns follow search traffic
  - Delete operations during cache invalidation

- **Eviction Rate**: How often cache entries are removed
  - High eviction = cache too small or TTL too short
  - Monitor to optimize cache size

**4. Scraping Activity Section**
- **Active Jobs Gauge**: Currently running scraping operations
  - Should be 0 most of the time
  - Spikes to 3 during scheduled scraping (one per shop)

- **Scrape Duration by Shop**: Time taken to scrape each shop
  - Kontakt: Typically 1-3 minutes (Selenium required)
  - Irshad: Typically 30-60 seconds (Jsoup)
  - Baku Electronics: Typically 20-40 seconds (Jsoup)
  - Alert if duration exceeds 5 minutes

- **Products Found vs Created/Updated**: Data ingestion trends
  - Shows scraping effectiveness
  - High "found" with low "created" indicates mature dataset
  - Sharp increases indicate new product listings

- **Scraper Success Rate**: Success/failure ratio by shop
  - Target: > 95% success rate
  - Failures may indicate site changes or anti-bot measures

**5. Circuit Breaker Section**
- **Elasticsearch Circuit State**: Shows circuit breaker status
  - CLOSED (green): Normal operation
  - OPEN (red): Circuit broken, using JPA fallback
  - HALF_OPEN (yellow): Testing Elasticsearch recovery

- **Fallback Rate**: Percentage of searches using JPA fallback
  - Target: < 2%
  - High rate indicates Elasticsearch issues

---

### Setting Up Monitoring

#### Step 1: Start Monitoring Stack

The monitoring stack is included in `docker-compose.yml`:

```bash
# Start all services including Prometheus and Grafana
docker-compose up -d

# Verify Prometheus is scraping metrics
curl http://localhost:9090/api/v1/targets
```

#### Step 2: Access Grafana

1. Open browser to `http://localhost:3000`
2. Login with default credentials:
   - Username: `admin`
   - Password: `admin`
3. Change password when prompted
4. Dashboard should auto-load as **"Price Comparator Main Dashboard"**

#### Step 3: Explore Metrics

Navigate through dashboard sections:
- **Overview**: System health and uptime
- **Search**: Query performance and caching
- **Scraping**: Data collection metrics
- **Infrastructure**: JVM, memory, CPU

#### Step 4: Set Up Alerts (Optional)

Configure alerts in Grafana for critical conditions:

**Example Alert: High Search Latency**
```yaml
Alert Name: High Search Latency
Condition: search_duration_seconds p95 > 500ms
Duration: 5 minutes
Notification: Email/Slack
```

**Example Alert: Circuit Breaker Open**
```yaml
Alert Name: Elasticsearch Circuit Breaker
Condition: circuit_breaker_state = OPEN
Duration: 1 minute
Notification: Email/Slack (High Priority)
```

**Example Alert: Low Cache Hit Rate**
```yaml
Alert Name: Low Cache Hit Rate
Condition: cache_hit_rate < 70%
Duration: 10 minutes
Notification: Email
```

---

### Using Prometheus Directly

Access Prometheus UI at `http://localhost:9090` for advanced queries:

#### Example PromQL Queries

**1. Search Request Rate (last 5 minutes)**
```promql
rate(pricecomparator_search_requests_total[5m])
```

**2. Cache Hit Ratio**
```promql
sum(rate(pricecomparator_search_cache_hits_total[5m]))
/
sum(rate(pricecomparator_search_requests_total[5m]))
```

**3. Average Search Duration by Source**
```promql
rate(pricecomparator_search_duration_seconds_sum[5m])
/
rate(pricecomparator_search_duration_seconds_count[5m])
```

**4. Active Scraper Jobs**
```promql
pricecomparator_scraper_active_jobs
```

**5. Products Created per Hour**
```promql
increase(pricecomparator_scraper_products_created_total[1h])
```

**6. JVM Memory Usage**
```promql
jvm_memory_used_bytes{area="heap"}
/
jvm_memory_max_bytes{area="heap"}
```

---

### Spring Boot Actuator Endpoints

Direct access to application health and metrics:

| Endpoint | Description | Example |
|----------|-------------|---------|
| `/actuator/health` | Application health status | Overall UP/DOWN status |
| `/actuator/health/liveness` | Liveness probe | K8s liveness check |
| `/actuator/health/readiness` | Readiness probe | K8s readiness check |
| `/actuator/metrics` | Available metrics list | List all metric names |
| `/actuator/metrics/{name}` | Specific metric details | Get metric value |
| `/actuator/prometheus` | Prometheus format metrics | Scraping endpoint |
| `/actuator/info` | Application information | Version, build info |

**Example: Check Application Health**
```bash
curl http://localhost:8080/actuator/health | jq
```

**Response:**
```json
{
  "status": "UP",
  "components": {
    "db": {
      "status": "UP",
      "details": {
        "database": "PostgreSQL",
        "validationQuery": "isValid()"
      }
    },
    "redis": {
      "status": "UP",
      "details": {
        "version": "7.0.0"
      }
    },
    "elasticsearch": {
      "status": "UP",
      "details": {
        "cluster_name": "docker-cluster",
        "status": "green",
        "number_of_nodes": 1
      }
    },
    "diskSpace": {
      "status": "UP",
      "details": {
        "total": 500107862016,
        "free": 187462086656
      }
    }
  }
}
```

**Example: Get Specific Metric**
```bash
curl http://localhost:8080/actuator/metrics/pricecomparator.search.cache.hits | jq
```

---

### Kibana Dashboard (Elasticsearch Data)

Access Kibana for search data visualization:
```
http://localhost:5601
```

**Setup Index Pattern:**
1. Navigate to **Management** → **Stack Management** → **Index Patterns**
2. Click **Create index pattern**
3. Enter pattern: `offer_index*`
4. Click **Next step**
5. Select timestamp field: `lastSeenAt`
6. Click **Create index pattern**

**Useful Kibana Queries:**

**1. Search Recent Offers**
```
GET offer_index/_search
{
  "query": {
    "bool": {
      "must": [
        { "match": { "brand": "Apple" } },
        { "range": { "price": { "lte": 2000 } } }
      ]
    }
  },
  "sort": [ { "price": "asc" } ]
}
```

**2. Aggregation: Average Price by Shop**
```
GET offer_index/_search
{
  "size": 0,
  "aggs": {
    "shops": {
      "terms": { "field": "shopCode.keyword" },
      "aggs": {
        "avg_price": { "avg": { "field": "price" } }
      }
    }
  }
}
```

---

### Redis Monitoring

Monitor cache operations directly:

```bash
# Connect to Redis CLI
docker exec -it price-comparator-redis redis-cli

# Real-time monitoring
MONITOR

# Cache statistics
INFO stats

# View all cached keys
KEYS search:*

# Get cache size
DBSIZE

# Get specific cache entry
GET "search:iphone_15"

# Check memory usage
INFO memory
```

**Key Redis Metrics:**
- `used_memory_human`: Current memory usage
- `keyspace_hits`: Successful key lookups
- `keyspace_misses`: Failed key lookups
- `expired_keys`: Keys expired due to TTL
- `evicted_keys`: Keys removed due to memory pressure

**Cache Hit Rate Calculation:**
```
Hit Rate = keyspace_hits / (keyspace_hits + keyspace_misses)
```
Target: > 85% hit rate

---

### PostgreSQL Monitoring

Monitor database operations:

```bash
# Connect to PostgreSQL
docker exec -it price-comparator-postgres psql -U price_user -d price_comparator

# View recent scraping jobs with performance
SELECT
    sj.id,
    s.name as shop_name,
    sj.status,
    sj.products_found,
    sj.offers_created,
    sj.offers_updated,
    sj.duration_seconds,
    sj.started_at,
    sj.completed_at
FROM scraping_jobs sj
JOIN shops s ON sj.shop_id = s.id
ORDER BY sj.started_at DESC
LIMIT 10;

# Count active offers by shop
SELECT
    s.name as shop_name,
    COUNT(po.id) as active_offers,
    AVG(po.price) as avg_price
FROM shops s
LEFT JOIN product_offers po ON s.id = po.shop_id AND po.is_active = true
GROUP BY s.name
ORDER BY active_offers DESC;

# Product distribution by brand
SELECT
    p.brand,
    COUNT(DISTINCT p.id) as unique_products,
    COUNT(po.id) as total_offers,
    MIN(po.price) as min_price,
    MAX(po.price) as max_price,
    AVG(po.price) as avg_price
FROM products p
JOIN product_offers po ON p.id = po.product_id
WHERE po.is_active = true AND po.in_stock = true
GROUP BY p.brand
ORDER BY total_offers DESC;

# Database size monitoring
SELECT
    pg_size_pretty(pg_database_size('price_comparator')) as db_size;

# Table sizes
SELECT
    schemaname,
    tablename,
    pg_size_pretty(pg_total_relation_size(schemaname||'.'||tablename)) AS size
FROM pg_tables
WHERE schemaname = 'public'
ORDER BY pg_total_relation_size(schemaname||'.'||tablename) DESC;

# Active database connections
SELECT
    count(*) as active_connections,
    state
FROM pg_stat_activity
WHERE datname = 'price_comparator'
GROUP BY state;
```

---

### Performance Tuning Based on Metrics

#### If Search Latency is High (p95 > 200ms):

1. **Check Cache Hit Rate**
   ```promql
   pricecomparator_search_cache_hits_total / pricecomparator_search_requests_total
   ```
   - Target: > 85%
   - If low: Increase Redis cache TTL or memory

2. **Check Elasticsearch Health**
   ```bash
   curl http://localhost:9200/_cluster/health
   ```
   - Should be "green" or "yellow"
   - If "red": Check Elasticsearch logs

3. **Monitor Circuit Breaker State**
   - If frequently OPEN, Elasticsearch may be overloaded
   - Consider scaling Elasticsearch or optimizing queries

#### If Cache Hit Rate is Low (< 70%):

1. **Increase Cache TTL**
   ```yaml
   # application.yml
   cache:
     ttl: 6h  # Increase from default
   ```

2. **Increase Redis Memory**
   ```yaml
   # docker-compose.yml
   redis:
     command: redis-server --maxmemory 512mb
   ```

3. **Monitor Query Variety**
   - High variety = naturally low hit rate
   - Consider query normalization

#### If Scraping Jobs Fail Frequently:

1. **Check Duration Trends**
   ```promql
   pricecomparator_scraper_duration_seconds
   ```
   - Increasing duration = potential anti-bot measures

2. **Monitor Success Rate**
   ```promql
   pricecomparator_scraper_attempts_total{status="success"}
   /
   pricecomparator_scraper_attempts_total
   ```
   - Target: > 95%

3. **Review Error Logs**
   ```bash
   docker logs price-comparator-app | grep "ERROR.*Scraper"
   ```

---

### Monitoring Best Practices

1. **Regular Health Checks**
   - Check Grafana dashboard daily
   - Review weekly performance trends
   - Investigate anomalies promptly

2. **Set Up Alerts**
   - Critical: Elasticsearch circuit breaker, high error rates
   - Warning: Low cache hit rate, slow scraping
   - Info: Scheduled scraping completion

3. **Capacity Planning**
   - Monitor memory growth trends
   - Track database size increases
   - Plan Redis cache scaling

4. **Performance Baselines**
   - Establish normal operating metrics
   - Document expected ranges
   - Alert on significant deviations

5. **Retention Policies**
   - Prometheus: 15 days of metrics
   - Application logs: 7 days
   - Database: Indefinite (with archival)

---

### Troubleshooting with Monitoring

| Symptom | Check These Metrics | Likely Cause | Solution |
|---------|-------------------|--------------|----------|
| Slow searches | `search_duration_seconds` p95 | High latency | Check Elasticsearch health |
| High memory usage | `jvm_memory_used_bytes` | Memory leak | Restart app, investigate heap dump |
| Cache not working | `cache_hit_rate` | Redis down or config issue | Check Redis connection |
| Scraping failures | `scraper_attempts_total{status="failure"}` | Anti-bot detection | Review scraper logic |
| Circuit breaker open | `circuit_breaker_state` | Elasticsearch unavailable | Check ES logs and health |
| No data indexed | `elasticsearch_documents_indexed_total` | Indexing failure | Check ES index settings |

---

This comprehensive monitoring setup provides full visibility into the Price Comparator system, enabling proactive performance management and rapid issue resolution.

---

## Contributing

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add some amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

## Contact

**Author:** Samir
**Project Link:** https://github.com/mimir45/Price-Comparator

---

## Acknowledgments

- **Kontakt Home** - https://kontakt.az
- **Irshad Electronics** - https://irshad.az
- **Baku Electronics** - https://bakuelectronics.az
- Spring Boot Community
- Elasticsearch Community
- Selenium Project

---

**Last Updated:** December 10, 2025
