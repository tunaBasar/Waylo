# Waylo Architecture

> Ürün yönü ve hedef B2B/mobil mimarisi: [PRODUCT-ARCHITECTURE.md](PRODUCT-ARCHITECTURE.md).
> Bu dosya mevcut teknik yapıyı anlatır, hedef dokümandaki özelliklerin uygulanmış olduğunu göstermez.

Uçuş + otel + araç kiralama paket rezervasyon platformu. Mikroservis mimarisi.

## Tech Stack

| Katman | Teknoloji | Versiyon |
|---|---|---|
| Framework | Spring Boot | 4.0.8 |
| Cloud | Spring Cloud (Oakwood) | 2025.1.3 |
| JDK | Eclipse Temurin | 21 |
| Build | Gradle (Groovy DSL) | 8.x |
| Database | PostgreSQL | 16 |
| Migration | Flyway | (Boot managed) |
| Messaging | Apache Kafka (KRaft) | 7.9.0 (Confluent) |
| Cache | Redis | 7 |
| Service Discovery | Netflix Eureka | (Cloud managed) |
| Config | Spring Cloud Config Server (native) | (Cloud managed) |
| API Gateway | Spring Cloud Gateway (WebFlux) | (Cloud managed) |
| Inter-service | OpenFeign | (Cloud managed) |

## Servis Listesi

| Servis | Port | Rol |
|---|---|---|
| config-service | 8888 | Spring Cloud Config Server (native backend) |
| discovery-service | 8761 | Eureka Server |
| api-gateway | 8080 | Spring Cloud Gateway (WebFlux) |
| flight-service | 8081 | Uçuş programları |
| flight-seat-inventory-service | 8082 | Koltuk envanteri |
| hotel-service | 8083 | Otel/oda envanteri |
| car-rental-service | 8084 | Araç filosu |
| package-booking-service | 8085 | Paket rezervasyon |
| pricing-service | 8086 | Fiyatlandırma |
| payment-service | 8087 | Ödeme (mock) |
| crm-service | 8088 | Müşteri/sadakat |
| checkin-service | 8089 | Uçuş check-in |
| notification-service | 8090 | Email/SMS (mock) |
| package-booking-saga-orchestrator | 8091 | Saga koordinatörü |
| auth-service | 8092 | Kimlik, JWT, kuruluş/üyelik/rol |
| traffic-simulator | 8095 | Yük testi |
| frontend (Next.js) | 3000 | Müşteri + partner web arayüzü |

## Mimari Kararlar

### 1. DB-per-Service

Her mikroservis kendi PostgreSQL veritabanına sahip. Paylaşımlı DB kullanılmıyor.
Gerekçe: Servisler arası veri bağımsızlığı, bağımsız schema evolution, deployment izolasyonu.

### 2. Kafka: KRaft Mode (Zookeeper Yok)

Kafka, KRaft mode ile çalışıyor (tek container, Zookeeper gereksiz).
Gerekçe: Daha az kaynak tüketimi, daha basit operasyon, Kafka 3.x+ ile production-ready.

### 3. Config Server: Native Backend

Config Server, filesystem (native) backend kullanıyor. Git repo değil.
Gerekçe: v0 aşamasında basitlik. İleride Git backend'e geçiş kolay.

### 4. Apache Ignite - Kullanılmayacak

Değerlendirildi, v0 kapsamında alınmadı.
Gerekçe: Redis zaten cache/TTL ihtiyacını karşılıyor. Ignite'ın sunduğu distributed compute ve
in-memory data grid özellikleri bu aşamada gereksiz karmaşıklık. Redis'in allkeys-lru eviction
policy'si yeterli. Ignite'ın JVM heap pressure'ı ve operasyonel yükü haklı gösterilemiyor.

### 5. Akka Actor Model - Kullanılmayacak

Değerlendirildi, v0 kapsamında alınmadı.
Gerekçe: Spring Boot'un request/response modeli ile Akka'nın actor paradigmasını karıştırmak
gereksiz karmaşıklık yaratır. İki farklı concurrency modelini aynı JVM'de yönetmek, debugging
ve reasoning'i zorlaştırır. Saga orchestrator zaten Kafka ile event-driven çalışıyor ve
Spring'in @Async + virtual threads (Java 21) yeterli concurrency sağlıyor.

### 6. Saga Pattern: Orchestration-based

Paket rezervasyon akışı (uçuş + otel + araç kiralama) için orchestration-based saga kullanılıyor.
package-booking-saga-orchestrator servisi, Kafka event'leri ile diğer servisleri koordine eder.
Choreography yerine orchestration tercih edildi: daha kolay debug, merkezi hata yönetimi,
kompanse eden transaction'ların açık tanımı.

### 7. Saga Compensation Sırası

Compensation her zaman tamamlanmış adımların ters sırasında çalışır.
Saga adım sırası: RESERVE_SEATS → RESERVE_HOTEL → RESERVE_CAR → PROCESS_PAYMENT → CONFIRM_BOOKING.
Bir adım başarısız olduğunda, yalnızca o adımdan önceki başarılı adımlar geri alınır ve geri alma
ters sırada yapılır. Örnek: PROCESS_PAYMENT başarısız olursa compensation sırası
car→hotel→seat'tir (payment hiç başarılı olmadığı için refund gerekmez). Refund yalnızca payment
başarılı olduktan sonra bir adımda (CONFIRM_BOOKING) hata olursa devreye girer.

### 8. Idempotency: Tek Enjeksiyon Noktası

Kafka consumer idempotency altyapısı (processed_events tablosu, ortak base consumer veya
aspect/interceptor) tüm servislerden önce, ortak altyapı fazında kurulur. Her yeni consumer
bunu baştan kullanır; geriye dönük ekleme yapılmaz. DLQ ve retry policy ayrı bir fazda
servis-özel ince ayarlarla tamamlanır.
Gerekçe: bill_project'teki "tek enjeksiyon noktası" prensibi. Cross-cutting concern'ler merkezi
olarak bir kez tanımlanır, her servis aynı altyapıyı devralır.

### 9. Spring Boot 4.x Artifact Değişiklikleri

Spring Boot 4.x'te (Spring Framework 7 tabanlı) bazı artifact isimleri değişti:
- `spring-boot-starter-web` → `spring-boot-starter-webmvc`
- Yeni: `spring-boot-starter-kafka` (eski `spring-kafka` yerine)
- Yeni: `spring-boot-starter-flyway` (eski `org.flywaydb:flyway-core` yerine, ek olarak `flyway-database-postgresql` hala gerekli)
- Yeni: `spring-boot-starter-webclient` (bağımsız reactive HTTP client starter)
- `spring-cloud-starter-gateway` → `spring-cloud-starter-gateway-server-webflux`

## Startup Sırası

```
Phase 0: config-service → discovery-service
Phase 1: api-gateway
Phase 2: Domain servisleri (paralel)
Phase 3: package-booking-saga-orchestrator
Phase 4: traffic-simulator
```

## Klasör Yapısı (Domain Servisleri)

```
src/main/java/com/waylo/{servisadi}/
├── controller/      # REST endpoints
├── service/         # Business logic
├── repository/      # Data access (JPA)
├── domain/          # Entity classes
├── dto/             # Data transfer objects
├── config/          # Spring configuration
├── event/           # Kafka producer/consumer
└── exception/       # Custom exceptions
```
