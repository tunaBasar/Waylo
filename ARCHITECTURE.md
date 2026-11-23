# Waylo Architecture

> Bu dosya **bugün çalışan** teknik yapıyı anlatır (son doğrulama: 23 Kasım 2026, commit `669022e` sonrası).
> Hedef ürün yönü: [PRODUCT-ARCHITECTURE.md](PRODUCT-ARCHITECTURE.md). Endpoint sözleşmeleri: [API-DOCS.md](API-DOCS.md).
> Dikey sözleşmeleri: `*-CONTRACT.md` dosyaları. Açık işler ve kararlar: `STATUS.md` (yerel, git dışı).

Waylo, işletmelerin doğrudan katıldığı çok dikeyli bir seyahat pazaryeri: otel, araç kiralama, uçuş ve otobüs.
Müşteri tek siparişte birden fazla dikeyden ürün alır, tek ödeme yapar, her kalem için QR voucher alır.
İşletmeler (kuruluşlar) partner panelinden kendi envanterini yönetir, platform admini kuruluşları ve içerikleri denetler.

## Tech Stack

| Katman | Teknoloji | Versiyon |
|---|---|---|
| Framework | Spring Boot | 4.0.8 |
| Cloud | Spring Cloud (Oakwood) | 2025.1.3 |
| JDK | Eclipse Temurin | 21 |
| Build | Gradle (Groovy DSL, multi-module) | 8.14 |
| Database | PostgreSQL (servis başına ayrı DB) | 16 |
| Migration | Flyway | (Boot managed) |
| Messaging | Apache Kafka (KRaft, Zookeeper yok) | 7.9.0 (Confluent) |
| Cache / rate limit | Redis | 7 |
| Service Discovery | Netflix Eureka (HTTP basic auth) | (Cloud managed) |
| Config | Spring Cloud Config Server, native backend (HTTP basic auth) | (Cloud managed) |
| API Gateway | Spring Cloud Gateway (WebFlux) | (Cloud managed) |
| Inter-service | OpenFeign + imzalı servis kimliği (`service-auth-common`) | (Cloud managed) |
| Web | Next.js + React | 16.4 / 19.3 |
| Mobil | Expo (React Native + TypeScript, expo-router) | SDK 58 / RN 0.88 |
| Observability | Micrometer/Prometheus, X-Request-Id, log maskeleme (`observability-common`) | - |

## Servis Listesi

| Modül | Port | Rol |
|---|---|---|
| config-service | 8888 | Config Server (native), basic auth |
| discovery-service | 8761 | Eureka Server, basic auth |
| api-gateway | 8080 | Tek public giriş: JWT doğrulama, kimlik header'larını sıfırlama, rate limit, CORS |
| auth-service | 8092 | Kimlik, JWT (access/refresh rotation), kuruluş/üyelik/kapsamlı rol, `/internal/access` yetki kararı, platform admin onayı |
| hotel-service | 8083 | Otel kataloğu, oda tipi/rate plan, günlük stok ve fiyat, quote, atomik çok geceli hold, yorumlar, RLS |
| car-rental-service | 8084 | Araç partnerleri (şirket/bireysel), şube, filo, tarife, tek yön, belge onayı, hold |
| bus-service | 8096 | Otobüs firmaları, sefer, koltuk, hold, QR biniş (satış kapısı kapalı, `BUS_SALES_ENABLED=false`) |
| flight-service | 8081 | Uçuş programları, havalimanları |
| flight-seat-inventory-service | 8082 | Koltuk envanteri, uçuş teklifleri (supplier adapter), koltuk hold |
| package-booking-service | 8085 | Order/OrderItem: tek ve çok kalemli sipariş orkestrasyonu, voucher/QR, iptal, no-show, partner sipariş görünümü, legacy `/api/bookings` (deprecated) |
| pricing-service | 8086 | Kampanya, promo kod, vergi oranı, fiyat önizleme, sipariş için fiyat rezervasyonu |
| payment-service | 8087 | Payment intent, escrow, append-only ledger, komisyon, iade, tedarikçi bakiyesi. Provider: `TEST` (iyzico anahtar bekliyor) |
| crm-service | 8088 | Müşteri profili (id = auth userId, auth-events ile senkron), sadakat mekanizması kapalı |
| checkin-service | 8089 | Order uçuş kalemleri için online check-in |
| notification-service | 8090 | E-posta ve Expo push, gerçek teslim durumu (SENT/SUPPRESSED/FAILED), cihaz kaydı |
| traffic-simulator | 8095 | Admin-only, sınırlı salt-okuma yük testi |
| package-booking-saga-orchestrator | 8091 | **Emekli.** Compose profili `legacy-saga`, varsayılan olarak başlamaz (bkz. Karar 6) |
| observability-common | - | Kütüphane: request id, metrikler, outbox backlog gauge, log varsayılanları |
| service-auth-common | - | Kütüphane: imzalı servis kimliği (filter + Feign interceptor) |
| frontend (Next.js) | 3000 | Müşteri, partner (`/partner/*`) ve admin (`/admin/*`) yüzeyleri, ayrı layout ve guard |
| mobile (Expo) | - | Müşteri uygulaması: arama, sepet, checkout, QR siparişler, yorum, check-in, push |

Domain servis portları host'ta yalnız `127.0.0.1`'e bağlıdır. Dışarıya açık olanlar gateway (8080) ve frontend (3000).
flight-service ve crm-service host'ta 18081/18088'e eşlenir (konteyner içi 8081/8088).

## İstek ve güven akışı

```mermaid
flowchart LR
    Web[Next.js web] --> GW[api-gateway :8080]
    Mobile[Expo mobil] --> GW
    GW -->|"X-User-Id/Email/Role (JWT'den)"| Svc[Domain servisleri]
    Svc -->|"Feign + X-Service-Token"| Svc2[Diğer servislerin /internal uçları]
    Svc -->|"/internal/access"| Auth[auth-service]
    Svc --> DB[(Servise ait PostgreSQL)]
    Svc -->|outbox relay| K[(Kafka)]
    K --> Notif[notification / crm]
```

1. **Gateway**: `typ=access` ve issuer `waylo-auth` olan JWT'yi doğrular, refresh token'ı reddeder. İstemciden gelen
   `X-User-Id`, `X-User-Email`, `X-User-Role`, `X-Internal-Token`, `X-Service-Token` header'larını her istekte siler ve
   kimliği yalnız doğrulanmış token'dan yazar. Katalog GET'leri public, geri kalan her şey token ister. `/internal/**` route'u yoktur.
2. **Yetki domain serviste**: gateway yalnız kimliği doğrular. Kuruluş/tesis kapsamlı yetki her serviste
   `auth-service /internal/access` ile kontrol edilir ve hata durumunda **fail-closed** davranır.
   Client'ın gönderdiği `organizationId` yalnız seçicidir, yetki kanıtı değildir.
3. **Servisler arası kimlik** (`service-auth-common`): çağıran servis kendi Ed25519 anahtarıyla kısa ömürlü bir JWT imzalar
   (`iss`=çağıran, `aud`=hedef, `exp-iat` <= 60 sn, `jti`). Token tek kullanımlıktır: alıcı servis kabul ettiği `(iss, jti)`
   çiftini süre dolana kadar hatırlar, tekrar gelen token 401 alır (servis örneği başına bellek içi). Hedef servis her `/internal` route grubu için izinli çağıranları
   `waylo.service-auth.routes` altında tanımlar. Tanımsız `/internal` yolları fail-closed'dır.
   `WAYLO_SERVICE_AUTH_MODE=dual` eski paylaşımlı `INTERNAL_API_TOKEN`'ı da kabul eder, `strict` yalnız imzalı token kabul eder.
   Anahtarlar `scripts/gen-service-keys.py` ile `.env`'e yazılır.

## Mimari Kararlar

### 1. DB-per-Service
Her servis kendi PostgreSQL veritabanına sahiptir. Cross-DB foreign key veya başka servisin DB'sine SQL erişimi yoktur.
Referanslar UUID, servis sözleşmesi (Feign `/internal`) ve Kafka olaylarıyla taşınır.

### 2. Kafka: KRaft Mode
Tek konteyner, Zookeeper yok. Topic'ler: `auth-events`, `order-events`, `payment-events`, `flight-events` ve legacy
`/api/bookings` akışının `booking-events` topic'i.

### 3. Config Server: Native Backend
Dosya sistemi backend'i. Eureka ve config-service HTTP basic auth ile korunur. Compose'daki dev şifre varsayılanları
paylaşımlı ortamlarda `.env` ile ezilmelidir.

### 4. Apache Ignite ve 5. Akka: kullanılmıyor
Redis cache/rate-limit ihtiyacını karşılıyor. Spring request/response modeli ve Java 21 virtual thread'leri yeterli.
İki ayrı concurrency modeli bakım ve debug maliyeti getirir.

### 6. Sipariş orkestrasyonu: package-booking-service içinde senkron (saga emekli)
Başlangıçta Kafka tabanlı bir saga orchestrator vardı. Bugün sipariş akışını `OrderServiceImpl` yürütür:
1. Fiyat: otel için hotel-service quote'u, diğer dikeylerde dikey servisin teklifi. pricing-service kampanya/promo/vergiyi uygular ve sipariş için rezerve eder.
2. Her kalem için dikey servisten **hold** alınır (otel: tüm geceler tek transaction'da, `PESSIMISTIC_WRITE` kilidi
   `roomTypeId, stayDate` sırasıyla alınır).
3. Bir adım başarısız olursa alınmış hold'lar ve fiyat rezervasyonu **ters yönde serbest bırakılır**. Serbest bırakma
   başarısız olursa her dikeyin expiry scheduler'ı hold'u süre dolunca geri alır.
4. Tek ödeme: payment-service intent ve escrow oluşturur. Ödeme sonucu `markPaid` ile siparişe döner.
5. `markPaid` her kalemin hold'unu idempotent olarak **confirm** eder. Confirm edilemeyen kalem iade edilir ve hold'u bırakılır.
   Teyit edilen kalemler için QR voucher üretilir.

Saga yalnız legacy `PackageBooking` adımlarını işaretliyordu, gerçek rezervasyon yapmıyordu. Bu yüzden ikinci ve yanıltıcı bir
doğruluk kaynağı olmaması için devreden çıkarıldı (`SAGA-DECISION.md`). Modül derlenir ve testleri çalışır, `/api/sagas` route'u 503 döner.

### 7. İptal, iade ve finans
İptal kuralları kalem bazında saklanan **politika snapshot'ından** hesaplanır, sonradan fiyat veya otel düzenlemesi bunu değiştirmez.
`GET /api/orders/{id}/cancellation-preview` sonucu önceden gösterir. Para iadesi, escrow ve stok serbest bırakma ayrı sonuçlarla izlenir.
Komisyon, işletme alacağı ve iade etkisi payment-service'te **append-only ledger** kayıtlarıdır. Partner kazanç ekranı
escrow kalemlerinden beslenir (HELD / RELEASED / REFUNDED toplamları). Payout (işletmeye ödeme) akışı henüz yok.

### 8. Olaylar: transactional outbox + idempotent consumer
auth-service, package-booking-service ve payment-service domain değişikliğini ve olayı aynı local transaction'da
`outbox_event` tablosuna yazar. `OutboxRelay` olayları Kafka'ya taşır, backlog metrik olarak izlenir.
Consumer tarafında `processed_events` tablosu tekrar işlemeyi engeller. Kafka exactly-once iş etkisi garantisi olarak sunulmaz.
`X-Request-Id` HTTP ve Kafka üzerinden uçtan uca taşınır.

### 9. Kiracı izolasyonu: kod + PostgreSQL RLS (hotel-service)
Kod katmanı: her mutasyon `HotelAccessGuard` ile auth-service'e sorulur, child kayıtlar `hotelId + childId` ile bağlanır.
DB katmanı (V9): uygulama her transaction'da `SET LOCAL ROLE waylo_app` (NOLOGIN, BYPASSRLS yok) ve transaction-local
`app.mode` / `app.org_ids` ayarlar. Partner modunda yalnız yetkili kuruluşların satırları okunur ve yazılır. Public, servis ve admin
okumaları bugünkü gibi kalır. Mod başlangıçta en düşük yetkidir (`/internal` ve `/internal/...` -> SERVICE, ADMIN rolü -> ADMIN,
geri kalan -> PUBLIC). PARTNER modu yalnız başarılı erişim kontrolünden sonra açılır. Bağlam transaction sonunda düşer, pool'da sızmaz.
RLS şimdilik yalnız hotel-service'te. Quote, hold ve outbox gibi transaction tabloları kapsam dışındadır.

### 10. Public projeksiyon ve satış kapısı
Public otel listesi ve araması yalnız aktif otelleri ve `ACTIVE` kuruluşa ait otelleri gösterir. Kuruluş durumu
auth-service'ten kısa süreli cache'lenir. auth-service erişilemezse kuruluş otelleri gizlenir (fail-closed).
Kuruluşu olmayan 22 legacy otel salt-okunur görünür (sahiplik kararı kullanıcıda). Otel yayın akışı
(`DRAFT -> SUBMITTED -> APPROVED -> PUBLISHED`) henüz kodda yok, sözleşmesi `HOTEL-PUBLICATION-CONTRACT.md`'de.
Bus satışları hukuki maddeler kapanana kadar kapalıdır.

### 11. Spring Boot 4.x artifact değişiklikleri
- `spring-boot-starter-web` -> `spring-boot-starter-webmvc`
- `spring-boot-starter-kafka` (eski `spring-kafka` yerine)
- `spring-boot-starter-flyway` + `flyway-database-postgresql`
- `spring-cloud-starter-gateway` -> `spring-cloud-starter-gateway-server-webflux`
- Jackson 3 varsayılan JSON kütüphanesi.

## Çalıştırma

Dizüstü bilgisayarda bütün yığını aynı anda çalıştırmak ağırdır. İşe göre küçük setler kullanılır:

```bash
scripts/dev-up.sh hotel     # core + hotel + orders + payments (car | bus | flight | mail | all | down)
```

Core: postgres, kafka, redis, config, discovery, gateway, auth, frontend. Her Java servisi `-Xmx256m` ve 384 MB bellek
limitiyle çalışır. Tek servis yeniden dağıtımı: `docker compose up --no-deps -d --build <servis>`.
Başlangıç sırası compose `depends_on` ve healthcheck ile sağlanır: config -> discovery -> gateway/auth -> domain servisleri.

## Test ve kalite kapıları

- Unit: `./gradlew :<modül>:test` (hotel-service RLS için Testcontainers PostgreSQL kullanır).
- Statik: `python3 scripts/check-bean-constructors.py` (Spring bean'lerinde tek constructor) ve `scripts/check-yaml-duplicates.py`.
- Canlı kabul: `scripts/test-*.py` gateway üzerinden gerçek akışları sınar. Örnekler: test-package-order, test-payments,
  test-rls, test-service-auth, test-gateway-security.
- Web: `npx tsc --noEmit`, `npx eslint`, `npm test`, Playwright e2e (`frontend/e2e`).
- Mobil: `npx tsc --noEmit`, `npx jest --ci`, `npx expo lint`.
- CI (`.github/workflows/ci.yml`): statik kontroller + tüm Gradle testleri, frontend ve mobil kontrolleri.

## Klasör Yapısı (Domain Servisleri)

```
src/main/java/com/waylo/{servisadi}/
├── controller/      # REST endpoints (public /api/** ve servisler arası /internal/**)
├── service/         # İş mantığı
├── repository/      # JPA
├── domain/          # Entity ve enum'lar
├── dto/             # Request/response record'ları, MapStruct mapper
├── client/          # Feign client'ları
├── security|filter/ # Erişim kontrolleri
├── config/          # Spring konfigürasyonu
├── event/           # Kafka producer/consumer, outbox
└── exception/       # Hata tipleri ve global handler
```
