# Waylo

## Proje hakkında

Waylo, otel öncelikli kurulan bir seyahat pazaryeridir. Oteller doğrudan katılır ve tesislerini B2B partner web panelinden yönetir. Son kullanıcılar yayınlanmış otelleri arar, rezerve eder, öder ve değerlendirir (tüketici mobil uygulaması otel web + backend'den sonra gelecek). Uçuş ve araç kiralama gelecekteki dikeylerdir. Ortak çekirdek (kimlik, kuruluş/üyelik, sipariş, ödeme, bildirim, audit) dikeyden bağımsız kalır.

**Kullanıcılar**
- Otel işletmeleri: ORGANIZATION_OWNER, HOTEL_OWNER, HOTEL_MANAGER, HOTEL_STAFF (kuruluş/otel kapsamlı)
- Platform ekibi: PLATFORM_ADMIN, PLATFORM_SUPPORT
- Son kullanıcı (CUSTOMER). Aynı kimlik hem müşteri hem partner üyesi olabilir.

**Ana akışlar**
1. Partner: kuruluş başvurusu → admin onayı → otel taslağı → içerik → oda tipleri/rate planlar → günlük stok/fiyat → yayın talebi → admin yayını
2. Müşteri: yayınlanmış otel arama → müsaitlik → teklif (quote) → çok gecelik atomik hold → sipariş → ödeme → onay → konaklama → doğrulanmış yorum
3. İptal/iade: politika snapshot'ı ve stok iadesi

**Dokümanlar:** [ARCHITECTURE.md](ARCHITECTURE.md) (mevcut teknik yapı, servisler ve portlar), [PRODUCT-ARCHITECTURE.md](PRODUCT-ARCHITECTURE.md) (hedef tasarım), [WAYLO-MVP-ROADMAP.md](WAYLO-MVP-ROADMAP.md) (fazlı yol haritası), [API-DOCS.md](API-DOCS.md), [auth-service/ORGANIZATION-DOMAIN.md](auth-service/ORGANIZATION-DOMAIN.md).

## Tek komutla başlatma

Gereksinimler: Docker Engine ve Docker Compose v2 veya üstü. Yerel Java, Gradle veya Node.js kurulumu gerekmez.

Proje kökünde:

```bash
docker compose up -d --build
```

Proje kökündeki `.env` dosyasında `POSTGRES_PASSWORD` ve `PGADMIN_PASSWORD` değişkenleri tanımlı olmalıdır; aksi halde Compose başlatmayı reddeder.

### Servisler arası kimlik (X-Service-Token)

Her servis kendi Ed25519 anahtarıyla imzalı kısa ömürlü bir token (JWT, `iss`=çağıran, `aud`=hedef, `exp`<=60 sn, `jti`) gönderir; alıcı servis imzayı çağıranın açık anahtarıyla, `aud` alanını ve `/internal/**` route grubu başına izinli çağıran listesini (`waylo.service-auth.routes`, her servisin `application.yml` dosyasında) doğrular. Ortak kod: `service-auth-common` modülü. Token tek kullanımlıktır: kabul edilen `(iss, jti)` süresi dolana kadar hatırlanır ve aynı token ikinci kez gelirse 401 döner (bellek içi, servis örneği başına).

İlk kurulumda (ve yeni servis eklendiğinde) anahtarları üretin; anahtarlar `.env` dosyasına yazılır (gitignore'da), ekrana basılmaz, mevcut anahtarlar korunur:

```bash
python3 scripts/gen-service-keys.py            # eksik anahtarları üret
python3 scripts/gen-service-keys.py --rotate payment-service   # bir servisin anahtarını yenile
```

Servisler arası çağrılar yalnızca imzalı `X-Service-Token` ile kabul edilir. Eski paylaşımlı `INTERNAL_API_TOKEN` / `X-Internal-Token` kaldırıldı ve `WAYLO_SERVICE_AUTH_MODE` artık yoktur. Anahtar yoksa servisler arası çağrılar reddedilir. Doğrulama: `python3 scripts/test-service-auth.py`.

Bu komut backend kaynaklarını Java 21 ile derler, frontend için `npm ci` ve production build çalıştırır, ardından altyapı ve uygulama servislerini başlatır. İlk derleme internet bağlantısı gerektirir ve sonraki derlemelerden daha uzun sürer.

Başlatmanın tamamlanması için `docker compose up -d --build --wait --wait-timeout 240` kullanılabilir. İlk açılışta Eureka kayıtlarının gateway'e ulaşması kısa süre alabilir, bu sırada API geçici olarak 503 dönebilir.

- Uygulama: http://localhost:3000
- API gateway: http://localhost:8080
- Uçuş servisine doğrudan erişim: http://localhost:18081 (container içinde 8081). Gerekirse `.env` içinde `FLIGHT_SERVICE_PORT` ile değiştirilebilir.
- Servis durumları: `docker compose ps`
- Loglar: `docker compose logs -f frontend api-gateway auth-service notification-service`

Frontend şu an tarayıcıdan `localhost:8080` adresine bağlanır. Bu kurulum yerel geliştirme içindir, uzaktan erişim/production deployment için API adresi ve güvenlik ayarları ayrıca yapılandırılmalıdır.

## İlk platform ADMIN (bootstrap)

`.env` içinde `WAYLO_BOOTSTRAP_ADMIN_EMAIL` tanımlıysa (PASSWORD olmadan) auth-service açılışında, sistemde hiç ADMIN yoksa bu e-postaya sahip mevcut ve e-postası doğrulanmış kullanıcı bir kez ADMIN yapılır ve denetim satırı loglanır. ADMIN zaten varsa hiçbir şey yapmaz. Kullanıcı önce normal kayıt olup e-postasını doğrulamalıdır. (EMAIL + PASSWORD birlikte verilirse dev/seed modu: kullanıcı yoksa oluşturulur.)

## SMTP

Proje kökünde, Git tarafından dışlanan `.env` dosyasını oluşturun (`.env.example` örnektir):

```dotenv
MAIL_USERNAME=your-address@gmail.com
MAIL_PASSWORD=your-app-password-without-spaces
```

SMTP şifresi veya kaynak kod değiştiğinde tekrar `docker compose up -d --build` çalıştırın. `docker compose start` mevcut container yapılandırmasını değiştirmez.

## Durdurma ve yeniden başlatma

```bash
docker compose stop
docker compose start
```

Durdurma veritabanı verilerini silmez. Verileri korumak için volume silen komutları kullanmayın. Mevcut hesaplar veritabanında kalır, aynı e-posta ile tekrar kayıt yerine giriş yapın.

Backend servisleri Compose üzerinden kökteki ortak `Dockerfile` ile derlenir. `build/libs` altında önceden oluşturulmuş yerel JAR dosyalarına bağımlılık yoktur. Frontend kendi çok aşamalı `frontend/Dockerfile` dosyasını kullanır.
