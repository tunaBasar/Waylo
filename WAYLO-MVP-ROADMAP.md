# Waylo MVP Yol Haritası: Otel B2B'den Tüketici Mobiline

Tarih: 14 Ekim 2026. **Durum: planlama dokümanıdır. Bu doküman tek başına geliştirme izni değildir, kullanıcı ayrıca geliştirmeye geçilmesini onaylamalıdır.**

Kaynaklar resmi/birincil sayfalardan salt okuma ile derlendi (web.run/webfetch). Kurulum, hesap açma, form gönderme, ödeme veya kodlama bu doküman üretilirken yapılmadı. Sayısal eşikler (yüzde, süre, sayı) burada **önerilen hipotez eşiğidir**, sektör kanıtlanmış standart değildir; her biri ilgili görevde yeniden değerlendirilir.

İlişkili dokümanlar: `PRODUCT-ARCHITECTURE.md` (hedef mimari), `ARCHITECTURE.md` (mevcut teknik yapı), `PRODUCT-DISCUSSION-NOTES.md` (görüşme notları), Superpro Waylo projesi (83 mevcut uygulama alt görevi).

## 1. Yönetici özeti

**KRİTİK GÜNCELLEME (14 Ekim 2026, kullanıcı sorusu üzerine bulundu):** ENUYGUN, 5 Ekim 2026'da "Wegsuite" adlı yeni bir otel extranet'i lansmanı yaptı. Bu bulgu önceki rakip araştırmasından (Bölüm 2) SONRA tespit edildi ve mevcut MVP tezini doğrudan etkiler.
- Kaynak: Turizm Günlüğü, "ENUYGUN, Yapay Zekâ Destekli Otel Extranet'i Wegsuite'i Açtı", 5 Ekim 2026, https://www.turizmgunlugu.com/2026/10/05/enuygun-wegsuite-yapay-zeka-otel-extranet/ (14 Ekim 2026'da fetch edildi).
- Doğrulanan olgu: Wegsuite oteller için fiyat, kontenjan, indirim, kampanya, rezervasyon, ödeme ve içerik işlemlerini tek panelden yönetmeyi sağlar; ilk etapta 500'ü aşkın tesise açıldı; sahiplik doğrulaması sonrası kurulum ücreti veya teknik ekip gerekmeden aynı gün başlanabiliyor; AI destekli fiyat/kontenjan önerisi sunuyor.
- Ölçek: Türkiye otel ekosisteminin 14.450 tesis, Enuygun grubunun doğrudan kontratlı portföyünün 3.600 tesis olduğu kaynakta belirtiliyor. Bu rakamlar şirket beyanıdır, bağımsız doğrulanmadı.
- Sınırlama: Tek haber kaynağı okundu, Wegsuite'in gerçek kullanım kolaylığı, ücretlendirme detayları ve hangi otel segmentlerini kapsadığı (büyük/küçük tesis ayrımı dışında) doğrulanmadı.
- **Sonuç: "Otel kendi panelinden kendi tesisini yönetsin" fikri artık sadece rakipte parity değil, Türkiye'nin en büyük seyahat gruplarından birinde ücretsiz, AI destekli ve geniş ölçekte canlı. Genel amaçlı bir otel yönetim paneli MVP'si bu haliyle satılabilir bir fark taşımaz. RM-01/RM-02/RM-03 bu bulguyla yeniden çalıştırılmalı: dar bir segment/coğrafya/iş akışı boşluğu bulunmadan B2B panel inşasına geçilmemeli.**

**İKİNCİ KRİTİK BULGU (14 Ekim 2026, kullanıcı sorusu üzerine):** Kullanıcı hedefi bireysel taşınmaz sahiplerine (bungalov, apart, villa, tatil evi) kaydırmayı ve otobüs bileti eklemeyi önerdi. Bu yön de salt araştırmayla test edildi, aşağıdaki iki bulgu nedeniyle hotel-first tezden daha riskli görünüyor.

- **Rekabet:** Türkiye'de en az 7 aktif platform aynı işi yapıyor: Villaindeks (komisyonsuz, AI arama, doğrudan ev sahibinden, "İlan Ekle"), RentRovi, TatildeKirala, Heryervilla, TatilTrendy, Bungoo, Evoteli. Kaynak: web_search sonuçları, 14 Ekim 2026, villaindeks.com, rentrovi.com, tatildekirala.com, heryervilla.com, tatiltrendy.com, bungalovbul.com, evoteli.com. "Ev sahibi bizim doğrudan müşterimiz olsun" fikri burada da parity'dir, yeni değildir.
- **Hukuki risk (otel segmentinden farklı ve daha ağır):** 7464 sayılı "Konutların Turizm Amaçlı Kiralanmasına Dair Kanun" (01.01.2024 yürürlükte), 100 günden kısa konut kiralamasını Kültür ve Turizm Bakanlığı izin belgesine ve kat maliklerinin oybirliğine bağlıyor. Kaynak: hukukcularevi.com (8 gün önce güncellendi), pmcgroup.com.tr, fatiharas.com, mutso.org.tr, turizmgazetesi.com (13 gün önce), 14 Ekim 2026'da kontrol edildi.
  - İzinsiz kiralamada ilk tespitte 2026 yeniden değerleme tutarı 180.617 TL, 15 gün sonra hâlâ faaliyete devam edilirse 903.088 TL, sonra 1.806.177 TL idari para cezası uygulanıyor (mutso.org.tr, hamlegazetesi.com.tr).
  - **Platform/aracı sorumluluğu:** İzin belgesi olmayan konuta aracılık edenler için her bir sözleşme başına ayrı 180.617 TL (2026 güncel tutar) idari para cezası öngörülüyor (mutso.org.tr, hamlegazetesi.com.tr, turizmgazetesi.com). Bu, Waylo'nun kendisinin, izinsiz bir evi listeleyip her rezervasyonda ayrı cezaya çarpılma riski taşıdığı anlamına gelir.
  - Sınırlama: Bu haber/blog kaynakları resmi Mevzuat Bilgi Sistemi/Resmî Gazete metni değildir, güncel ceza tutarı ve yönetmelik detayları resmi kaynaktan ve hukuk danışmanından teyit edilmeden hiçbir mülk listelenmemelidir.
- **Otobüs bileti:** Enuygun'un uçak+otel için ayrı bir paket sayfası var ("Tek Aramada Uçuş + Otel"), ama otobüs veya araç kiralamayı bu pakete dahil ettiğine veya gerçek tek sepet/tek ödeme sunduğuna dair kanıt bulunamadı. Bu hâlâ test edilmemiş bir fark hipotezidir (bkz. Bölüm 3, H5).
- **Sonuç:** Bu segment hotel-first tezden daha kalabalık rekabet VE daha ağır, platforma doğrudan uygulanan bir hukuki yükümlülük taşıyor. RM-05 (ticari/hukuki netleştirme) bu segment seçilirse izin belgesi doğrulama mekanizmasını gün 1 zorunlu kapı olarak tasarlamalı, ertelenebilir görev olarak ele almamalıdır.

- **Konumlandırma bulgusu:** Kanıtlardan çıkarım: Booking.com Extranet, Expedia Partner Central, Otelz Extranet ve SiteMinder/HotelRunner yönetim kabiliyetleri nedeniyle owner dashboard tek başına savunulabilir fark değildir. OTA extraneti tam PMS ile eşdeğer sayılmamalıdır.

- Kanıtlardan çıkarım: ENUYGUN aynı yerde çoklu seyahat ürünleri, uçak+otel ve seyahat yönetimi sunar. Geniş 'tek noktada seyahat' iddiası parity'dir. Tek sepet, ortak ödeme ve değişiklik orkestrasyonu ise bu araştırmada doğrulanmadı, ayrı test edilmesi gerekir.

- Waylo'nun önce doğrudan tesis onboarding ve B2B fayda, sonra consumer marketplace sıralaması bir geliştirme ve pazara giriş stratejisidir. Kaynaklar bunun benzersiz olduğunu kanıtlamaz. Consumer aşaması öncesi arz doğruluğu ve B2B kalıcılığı ölçülmelidir.

- B2B operasyon rakipleri ile OTA talep rakipleri ayrıdır. Doğrudan otel onboarding, consumer rezervasyonunun otelin kendi kanalından yapılmasıyla aynı kavram değildir. Komisyon üstünlüğü veya hukuki avantaj çıkarılmadı.

- Bu yüzden roadmap, 'otel paneli var' veya 'tek yerde uçak+otel var' gibi iddiaları fark olarak saymaz. Fark iddiaları Bölüm 3'te ölçülebilir hipoteze çevrilmiştir.

## 2. Rakip ve komşu ürün matrisi (resmi kaynaklı)

Kontrol tarihi: 14 Ekim 2026. 'Doğrulanmış özellik' kamuya açık resmi sayfada tanıtıldığı anlamına gelir, çalışan ürün testi değildir.

| Rakip | Sınıf | Doğrulanmış özellikler | Waylo için parity (fark değil) | Test edilecek hipotez | Doğrulanmayanlar |
|---|---|---|---|---|---|
| Booking.com | OTA | Doğrudan tesis listeleme yüzeyi; Extranet varlığı; Tesis doğrulaması olasılığı; Tesis bilgisi aktarımı ve takvim eşleme tanıtımı | Otelin kendi kaydını açması ve B2B yönetim paneli başlı başına fark değildir. Temel arz onboarding ve yönetim kabiliyeti rakipte vardır. | Waylo belirli Türkiye otel segmentinde daha kısa aktif iş süresi ve daha az destek ihtiyacıyla satışa hazır arz oluşturabilir. | Türkiye otellerine özgü ekran ve özellik kapsamı; Onboarding tamamlanma ve terk oranları; Panel kullanım kolaylığı; Türkiye hukuki durumu, fiyat, komisyon ve pazar payı |
| Expedia Partner Central | OTA partner extranet, PMS değil | Envanter yönetimi; Fiyat optimizasyonu; Rekabet içgörüleri; PMS/channel-manager/CRS entegrasyon programı | Hotel owner dashboard, envanter ve fiyat kontrolü mevcut OTA partner işlevleridir. Waylo bunları tek başına farklılaşma olarak sunmamalıdır. | Waylo belirli segmentte veri tekrarını ve günlük yönetim yükünü azaltan yerel operasyon yaklaşımıyla tercih edilebilir. | Gerçek Partner Central ekran akışı; Türkiye kapsamı; Waylo için API kabulü ve entegrasyon hakları; Ticari şartlar |
| Otelz | OTA | Tesis ekleme bağlantısı; Extranet giriş bağlantısı; Tüketici rezervasyon yönetimi bağlantısı; Tüketici mobil uygulama bağlantıları | Türkiye odaklı tesis katılımı, extranet varlığı ve consumer mobil dağıtım yüzeyi tek başına Waylo farkı değildir. | Waylo hotel-first aşamasında otellere tüketici trafiğinden önce ölçülebilir operasyon faydası verebilir. | Extranet fiyat ve müsaitlik özellikleri; Onboarding adımları ve doğrulama süresi; PMS ve channel-manager bağlantıları; İşlem rehberinin ayrıntılı gövdesi; Ticari şartlar |
| ENUYGUN | OTA, çok ürünlü seyahat satış platformu | Otel karşılaştırma ve rezervasyon tanıtımı; Uçak, otobüs, otel, araç ve transfer yüzeyleri; Uçak+otel arama yüzeyi; Rezervasyon durum sorgulama; Tüketici mobil uygulama tanıtımı | Tüm seyahati tek noktada toplama ve mobil tüketici erişimi mevcut konumlandırmadır. 'Tek seyahat akışı' geniş anlamıyla özgün değildir. | Tek işlem bağlamında kesintisiz planlama, ödeme ve değişiklik yönetimi gerçekten daha iyi ise Waylo avantajı olabilir. Rakibin bunu sunmadığı kanıtlanmadı. | Ortak sepet ve ortak ödeme; Ürünler arası iptal ve değişiklik orkestrasyonu; Doğrudan otel onboarding ve owner dashboard; Arzın doğrudan sözleşme oranı |
| SiteMinder | channel-manager, otel ticaret platformu, PMS entegrasyon sağlayıcısı | Merkezi fiyat, müsaitlik ve kısıt yönetimi; Oda ve fiyat planı eşleme; Kanal senkronizasyonu; Booking.com Enhanced Sync; Seçili PMS bağlantıları; İçgörü ve raporlama; Ortak entegrasyon süreci | Tek panel ve dağıtım otomasyonu B2B tarafta güçlü mevcut kategori kabiliyetleridir. SiteMinder tüketici OTA'sı olarak değil operasyon ve dağıtım alternatifi olarak ele alınmalıdır. | Waylo mevcut sistemi değiştirmek yerine uyumlu bir satış kanalı olursa onboarding yükünü azaltabilir. | Waylo kanal entegrasyonu uygunluğu; Desteklenen Türkiye PMS kapsamı; Gerçek eşleme gecikmesi ve hata davranışı; Ticari şartlar |
| HotelRunner | PMS + channel-manager + booking engine, otel operasyon ve dağıtım platformu | PMS ve CRS ürünleri; Channel-manager ve booking engine; Misafir yönetimi ve yorum merkezi; Tesis operasyon mobil uygulaması; POS ve grup otel yönetimi; Analitik; Müsaitlik, fiyat, kısıt ve rezervasyon API alışverişi | Hotel-first B2B panel, operasyonları tek yerde toplama ve mobil tesis yönetimi mevcut kategoridir. HotelRunner'ın tesis mobil uygulaması Waylo consumer marketplace uygulamasıyla karıştırılmamalıdır. | Waylo belirli arz segmenti ve talep kullanım amacı üzerinden farklılaşabilir, bütün PMS kabiliyetlerini yeniden üretmek zorunlu değildir. | Consumer marketplace varlığı veya yokluğu; Ürünlerin paket bazlı kapsamı; Waylo API erişimi ve kabul koşulları; Gerçek kullanıcı deneyimi; Ticari şartlar |

Kaynaklar:
- `b1` [List Your Apartment, Hotel, Vacation Home, or B&B on Booking.com](https://www.join.booking.com/index.html): kontrol 2026-10-14. Tesis listeleme ve Extranet bağlantısı sunulur. Yayına geçiş öncesinde tesis doğrulaması istenebileceği belirtilir. Başka seyahat sitelerinden tesis bilgisi aktarımı ve takvim eşleme tanıtılır. Ödeme kolaylaştırma sunulur, ödeme yöntemi ve zamanlaması bölgesel uygunluğa bağlıdır. *Sınır: Sayfa otel yanında tatil evi ve diğer tesisleri kapsar. Her özelliğin Türkiye otellerinde veya her tesis türünde geçerli olduğu doğrulanmadı. Extranet iç ekranları ve onboarding süresi denenmedi.*
- `e1` [Log in to your Expedia Group portal](https://partner.expediagroup.com/en-us/log-in): kontrol 2026-10-14. Resmi portal tanıtımı, otel ortaklarının Partner Central üzerinden rekabet içgörüleri inceleyebileceğini, envanter yönetebileceğini ve fiyatlarını optimize edebileceğini belirtir. *Sınır: Yalnız kamuya açık tanıtım okundu, login bağlantısı takip edilmedi. Ekranlar, yetkiler, Türkiye kapsamı ve kullanım kolaylığı test edilmedi.*
- `e2` [Welcome | EG Connectivity Hub](https://developers.expediagroup.com/supply/lodging): kontrol 2026-10-14. PMS, channel-manager ve CRS sağlayıcıları için tesis listeleme ve yönetim bağlantıları tanıtılır. Entegrasyon süreci lisans ve PCI incelemesi ile geliştirme, test ve soft launch aşamalarını içerir. Sayfa bireysel tesislerden doğrudan API bağlantısı kabul edilmediğini belirtir. *Sınır: Waylo'nun kabul edileceği, sertifikasyon süresi veya erişim kapsamı doğrulanmadı. Belgelerin tanıtılması, API'nin Waylo tarafından kullanılabilir olduğu anlamına gelmez.*
- `o1` [İşlem Rehberi (Rezervasyon) | Otelz](https://www.otelz.com/corporate/company/user-guide): kontrol 2026-10-14. Kamuya açık içerikte tesis ekleme, Extranet giriş, rezervasyon yönetme ve tüketici mobil uygulama indirme bağlantıları bulunur. Otel, termal otel, kayak oteli, daire ve villa kategorileri görünür. *Sınır: Erişilen çıktı çoğunlukla navigasyon ve footer içerir, işlem rehberinin ayrıntılı gövdesi görünmez. Extranet varlığı doğrulanır, fiyat veya müsaitlik düzenleme gibi iç fonksiyonlar bu kaynaktan doğrulanmaz.*
- `n1` [ENUYGUN Türkiye'nin Seyahat Sitesi - Uçak Bileti, Otobüs, Otel](https://www.enuygun.com/): kontrol 2026-10-14. Aynı tüketici sitesinde uçak, otel, otobüs, uçak+otel, araç kiralama ve transfer arama yüzeyleri vardır. Seyahat yönetimi altında online check-in ve uçak, otobüs, otel rezervasyonu durum sorgulama tanıtılır. Tüketici mobil uygulaması tanıtılır. *Sınır: Çoklu ürün yüzeyi tek sepet, ortak ödeme veya uçtan uca bütünleşik seyahat yaşam döngüsü kanıtı değildir. Arama veya rezervasyon gönderilmedi, mobil uygulama çalıştırılmadı.*
- `n2` [Hakkında - ENUYGUN](https://www.enuygun.com/hakkinda/): kontrol 2026-10-14. Şirket uçak, otobüs, otel, transfer ve araç kiralama ürünlerini sunduğunu belirtir. Otellerin fiyat ve özelliklerini karşılaştırarak rezervasyon yapılmasını tanıtır. *Sınır: Otel arzının doğrudan tesis sözleşmesinden mi aracıdan mı geldiği, otel sahibi paneli ve doğrudan onboarding bu sayfadan doğrulanmadı. Şirketin büyüklük iddiaları kullanılmadı.*
- `s1` [Hotel Channel Manager | SiteMinder](https://www.siteminder.com/channel-manager/): kontrol 2026-10-14. Oda ve fiyat planı eşleme, fiyat, müsaitlik ve kısıtların tek platformdan kanallara eşlenmesi tanıtılır. Booking.com'a medya, oda ve fiyat planı gönderimi ve seçili PMS ortakları için fiyat ve kısıt eşlemesi belirtilir. İçgörü ve pace raporlaması sunulur. *Sınır: Anlık eşleme pazarlama beyanıdır, gecikme ve hata oranı ölçülmedi. PMS eşlemesi seçili ortaklarla sınırlıdır. Tüm entegrasyonların Türkiye'de veya Waylo için uygunluğu doğrulanmadı.*
- `s2` [Developer Guide | SiteMinder](https://developer.siteminder.com/): kontrol 2026-10-14. Resmi geliştirici rehberi yeni ortaklar için başvuru, geliştirme, sertifikasyon ve canlıya geçişten oluşan yapılandırılmış entegrasyon yolu tanıtır. Mevcut ortakların ek kabiliyetlerle entegrasyonlarını genişletmesi için ayrı yol gösterilir. *Sınır: API uçları çağrılmadı. Waylo'nun kabul koşulları, erişim hakları ve sertifikasyon süresi incelenmedi. Entegrasyon maliyeti çıkarılmadı.*
- `h1` [HotelRunner | A Sales-First Approach to Hospitality](https://hotelrunner.com/): kontrol 2026-10-14. Ürün kataloğunda PMS, CRS, channel-manager, booking engine, otel web sitesi, misafir ilişki yönetimi, yorum merkezi, mobil uygulama, POS, grup otel yönetimi ve analitik bulunur. Operasyon yönetimi ile satış ve dağıtım ürünleri aynı platform altında tanıtılır. *Sınır: Katalogda bulunmak tüm ürünlerin her pakette mevcut olduğu anlamına gelmez. Mobil uygulama tesis operasyonları bağlamında sunulur, consumer marketplace uygulaması olduğu çıkarılmadı. İç ekranlar test edilmedi.*
- `h2` [Introduction | HotelRunner Developer Docs](https://developers.hotelrunner.com/): kontrol 2026-10-14. App Manager belgeleri PMS, gelir yönetimi, POS ve hotspot bağlantıları tanıtır. XML/SOAP ve REST/JSON üzerinden müsaitlik, kısıt ve fiyat gönderme, rezervasyon alma işlemleri listelenir. POS harcamalarını misafir foliosuna işleme desteği tanıtılır. *Sınır: API erişimi veya entegrasyon yapılmadı. Yetkilendirme, sertifikasyon, üretim güvenilirliği ve Türkiye tesislerine özgü uygunluk doğrulanmadı.*

## 3. Farklılaşma hipotezleri ve SWOT

Bunlar henüz kanıtlanmamış önerilerdir. Her biri RM-01..RM-04 ve RM-40'ta test edilecek, pilotta doğrulanmazsa terk edilecektir.

### Güçlü yönler (mevcut, gerçek)

- Çalışan mikroservis iskeleti: auth, hotel, booking, pricing, payment, saga-orchestrator, notification servisleri ve Next.js frontend zaten repoda var (bkz. `ARCHITECTURE.md`), sıfırdan altyapı kurulumu gerekmiyor.

- Hedef mimari (`PRODUCT-ARCHITECTURE.md`) kiracı izolasyonu, stok/fiyat ayrımı ve sipariş sınırlarını MVP öncesi netleştirdi; sonradan temel değişikliği riski azaltıldı.

- Tek ekip/karar hızı: büyük rakiplerin (Booking, Expedia) kurumsal onay süreçleri yok, segment/özellik hızlı değiştirilebilir.

### Zayıf yönler (gerçek, gizlenmemiş)

- Hiç gerçek otel, misafir, rezervasyon veya ödeme geçmişi yok; tüm talep ve arz hipotez.

- Ödeme/hukuki rol (merchant-of-record, TÜRSAB kapsamı) belirsiz; bu çözülmeden canlı para hareketi açılamaz (RM-05, Bölüm 5).

- Marka güveni ve tesis ağı sıfırdan kurulacak; Booking/Expedia/Enuygun gibi kurulu trafik/güven yok.

- Tek geliştirici/küçük ekip kapasitesiyle PMS/channel-manager kapsamı SiteMinder/HotelRunner seviyesine çıkmayacak; bu kapsam dışı tutulmalı, taklit edilmemeli.

### Fırsatlar

- Büyük OTA'ların kapsamadığı dar segment (örn. belirli bölge/bağımsız tesis tipi) içinde onboarding sürtünmesini azaltma.

- Doğrudan sözleşmeli arzda komisyon/koşul şeffaflığını farklılaşma konusu yapma (ölçülmeden iddia edilmez).

### Tehditler

- Hukuki/ödeme kapısı netleşmeden pilot başlatılırsa iade/komisyon anlaşmazlığı doğabilir.

- Stok tek kanal (Waylo-only allotment) olduğundan işletme başka kanalda aynı odayı satarsa overbooking riski bizim kontrolümüz dışındadır (bkz. PRODUCT-ARCHITECTURE.md §4).

## 4. Faz haritası (özet)

| Faz | Başlık | İçerdiği görevler |
|---|---|---|
| F0 | F0: Araştırma, konumlandırma ve ticari/hukuki hazırlık (geliştirme yok) | RM-01, RM-02, RM-03, RM-04, RM-05, RM-06, RM-07 |
| F1 | F1: Mimari sözleşme ve ortak temel tasarımı | RM-08, RM-09, RM-10, RM-11 |
| F2 | F2: Ortak temel implementasyonu (kimlik, kiracı, migration) | RM-12, RM-13, RM-14, RM-15, RM-16 |
| F3 | F3: Otel B2B web paneli ve backend | RM-17, RM-18, RM-19, RM-20, RM-21, RM-22 |
| F4 | F4: Otel transaction backend (quote/order/payment/review) | RM-23, RM-24, RM-25, RM-26, RM-27, RM-28, RM-29, RM-30, RM-31 |
| F5 | F5: Tüketici mobil uygulaması | RM-32, RM-33, RM-34, RM-35, RM-36 |
| F6 | F6: Mock temizliği ve bütün akış kabul testi | RM-37, RM-38 |
| F7 | F7: Kontrollü beta, gerçek pilot ve devam/pivot kararı | RM-39, RM-40 |

**Faz kapıları tam sırayla gözetilir: F0 kanıtı olmadan F1'e, F1 sözleşmesi olmadan F2 implementasyonuna geçilmez. F5 (mobil) ancak F3 ve F4 kabul kapıları geçtikten sonra başlar: kullanıcının 'önce otel web+backend, sonra mobil' isteği budur.**

## 5. Ayrıntılı görevler

### F0: Araştırma, konumlandırma ve ticari/hukuki hazırlık (geliştirme yok)

#### RM-01: Hedef segmenti ve gerçek müşteri problemini doğrula
*Öncelik P0 · Efor M · Sorumlu: Ürün kurucusu / araştırma · Bağımlılık: yok · Karşıladığı gereksinim: R1, R8*

**Amaç:** Kod yazmadan, hangi otellerin ve hangi müşterilerin neden Waylo kullanacağını belirlemek. Türkiye'de tek bölge ve bağımsız küçük/orta oteller başlangıç hipotezi, kesin pazar kararı değil.

**Nerede:**
- `planning/research/customer-interviews.md` (önerilen/yeni)
- `PRODUCT-ARCHITECTURE.md` (mevcut)

**Nasıl:**
1. İki ayrı görüşme rehberi hazırla: işletme için son fiyat/stok güncellemesi, son iptal, mevcut panel/PMS ve satış kanalları. Müşteri için son gerçek otel arama/ödeme/iptal deneyimi. 'Bunu kullanır mısınız' yerine geçmiş davranışı sor.
2. Önerilen keşif örneklemi: 10 işletme ve 10 tüketici. İletişim/katılım izni ve not saklama politikası ile kullanıcı onaylı görüşmeler yap, bu plan şu an kimseyle iletişime geçmez.
3. Sorun frekansı, mevcut alternatif, iş etkisi ve pilot katılımını ayrı kaydet. Sezon, tesis boyutu ve personel sayısına göre segmentle, istatistiksel pazar sonucu çıkarma.
4. Bölgeyi erişilebilir pilot oteller, talep ve stok disiplinine göre seç. Sadece büyük pazar olduğu için ülke çapında başlama.

**Sözleşme/kontrat:**
- InterviewRecord: participantType, segment, lastIncident, currentWorkaround, severity, pilotIntent, evidenceReference; kişisel veri minimum.

**Kabul ölçütü (bu görev uygulandığında koşulacak testler):**
- En az 3 işletme gerçek bir tarihli stok ve içerik pilotuna katılmayı kabul eder veya segment yeniden seçilir. Bu eşik plan önerisidir, gerçekleşmiş sonuç değildir.
- En az 5 işletmede aynı somut sorun geçmiş olayla desteklenir, övgü veya varsayımsal niyet tek başına kanıt sayılmaz.
- İşletme ve müşteri problemleri ayrı 1 sayfalık problem tanımına dönüşür, çelişen bulgular da kaydedilir.

**Teslimat:** Anonimleştirilmiş görüşme notları; Segment karar kartı; Problem ve pilot aday listesi

**İlgili kaynaklar:**
  - `MAIN-YC-DISCOVERY` [YC Startup School Week 4: early users and host learning](https://www.ycombinator.com/blog/startup-school-week-4-recap-kat-manalac-and-gustaf-alstromer/): YC'nin resmi konuşma transkripti erken Airbnb host'larıyla doğrudan görüşerek liste içeriği/güven sorunlarını öğrenmeyi ve erken kullanıcıları ölçeklenmeyen kişisel çalışmalarla bulmayı örnekler.

#### RM-02: Rakip matrisi ve olmazsa olmaz özellikleri çıkar
*Öncelik P0 · Efor M · Sorumlu: Ürün / araştırma · Bağımlılık: RM-01 · Karşıladığı gereksinim: R9, R10*

**Amaç:** OTA ile PMS/channel-manager'ı ayırıp Waylo'nun rakipleri kadar yapması gerekenlerle gerçekten farklılaşabileceği işleri ayırmak.

**Nerede:**
- `planning/research/competitor-matrix.md` (önerilen/yeni)

**Nasıl:**
1. Booking.com, Expedia, Otelz ve ENUYGUN için resmi public özellikleri incele. HotelRunner ve SiteMinder'ı işletme araçları/entegrasyon ekosistemi olarak ayrı sınıfa koy.
2. Her satırda hedef müşteri, onboarding, rate/calendar, reservation, review, mobil/çok dikey, dağıtım, gelir modeli bilgisi ve resmi URL tut. Public sayfada görülmeyeni 'yok' değil 'doğrulanmadı' işaretle.
3. Hotel_owner paneli, müşteri yorumları ve tek yerde uçak/otel/araç bulunmasının tek başına fark olmadığını matriste açıkça belirt. Her şeyi tek atomik işlemde sunduklarını kanıt olmadan varsayma.
4. Ticari ücretler, gerçek support SLA ve iç extranet UX'i için hesap/sözleşme gerektiren alanları araştırma boşluğu olarak bırak, rakibi test etmiş gibi yazma.

**Sözleşme/kontrat:**
- CompetitorClaim: vendor, class, feature, evidenceUrl, checkedDate, evidenceType, limitation, parityOrHypothesis.

**Kabul ölçütü (bu görev uygulandığında koşulacak testler):**
- 6 rakip/komşu ürünün her birinde en az bir resmi erişilmiş kaynak ve sınırlama bulunur.
- Hiçbir 'rakipte yok' iddiası public sayfada bulunmamasından çıkarılmaz.
- Waylo için minimum parity listesi ve yapmayacağımız PMS/OTA kapsamı ayrı yazılır.

**Teslimat:** Kaynaklı rakip matrisi; Parity ve kapsam dışı listesi

**İlgili kaynaklar:**
  - `b1` [List Your Apartment, Hotel, Vacation Home, or B&B on Booking.com](https://www.join.booking.com/index.html): Tesis listeleme ve Extranet bağlantısı sunulur. Yayına geçiş öncesinde tesis doğrulaması istenebileceği belirtilir. Başka seyahat sitelerinden tesis bilgisi aktarımı ve takvim eşleme tanıtılır. Ödeme kolaylaştırma sunulur, ödeme yöntemi ve zamanlaması bölgesel uygunluğa bağlıdır.
  - `e1` [Log in to your Expedia Group portal](https://partner.expediagroup.com/en-us/log-in): Resmi portal tanıtımı, otel ortaklarının Partner Central üzerinden rekabet içgörüleri inceleyebileceğini, envanter yönetebileceğini ve fiyatlarını optimize edebileceğini belirtir.
  - `e2` [Welcome | EG Connectivity Hub](https://developers.expediagroup.com/supply/lodging): PMS, channel-manager ve CRS sağlayıcıları için tesis listeleme ve yönetim bağlantıları tanıtılır. Entegrasyon süreci lisans ve PCI incelemesi ile geliştirme, test ve soft launch aşamalarını içerir. Sayfa bireysel tesislerden doğrudan API bağlantısı kabul edilmediğini belirtir.
  - `o1` [İşlem Rehberi (Rezervasyon) | Otelz](https://www.otelz.com/corporate/company/user-guide): Kamuya açık içerikte tesis ekleme, Extranet giriş, rezervasyon yönetme ve tüketici mobil uygulama indirme bağlantıları bulunur. Otel, termal otel, kayak oteli, daire ve villa kategorileri görünür.
  - `n1` [ENUYGUN Türkiye'nin Seyahat Sitesi - Uçak Bileti, Otobüs, Otel](https://www.enuygun.com/): Aynı tüketici sitesinde uçak, otel, otobüs, uçak+otel, araç kiralama ve transfer arama yüzeyleri vardır. Seyahat yönetimi altında online check-in ve uçak, otobüs, otel rezervasyonu durum sorgulama tanıtılır. Tüketici mobil uygulaması tanıtılır.
  - `n2` [Hakkında - ENUYGUN](https://www.enuygun.com/hakkinda/): Şirket uçak, otobüs, otel, transfer ve araç kiralama ürünlerini sunduğunu belirtir. Otellerin fiyat ve özelliklerini karşılaştırarak rezervasyon yapılmasını tanıtır.
  - `s1` [Hotel Channel Manager | SiteMinder](https://www.siteminder.com/channel-manager/): Oda ve fiyat planı eşleme, fiyat, müsaitlik ve kısıtların tek platformdan kanallara eşlenmesi tanıtılır. Booking.com'a medya, oda ve fiyat planı gönderimi ve seçili PMS ortakları için fiyat ve kısıt eşlemesi belirtilir. İçgörü ve pace raporlaması sunulur.
  - `s2` [Developer Guide | SiteMinder](https://developer.siteminder.com/): Resmi geliştirici rehberi yeni ortaklar için başvuru, geliştirme, sertifikasyon ve canlıya geçişten oluşan yapılandırılmış entegrasyon yolu tanıtır. Mevcut ortakların ek kabiliyetlerle entegrasyonlarını genişletmesi için ayrı yol gösterilir.
  - `h1` [HotelRunner | A Sales-First Approach to Hospitality](https://hotelrunner.com/): Ürün kataloğunda PMS, CRS, channel-manager, booking engine, otel web sitesi, misafir ilişki yönetimi, yorum merkezi, mobil uygulama, POS, grup otel yönetimi ve analitik bulunur. Operasyon yönetimi ile satış ve dağıtım ürünleri aynı platform altında tanıtılır.
  - `h2` [Introduction | HotelRunner Developer Docs](https://developers.hotelrunner.com/): App Manager belgeleri PMS, gelir yönetimi, POS ve hotspot bağlantıları tanıtır. XML/SOAP ve REST/JSON üzerinden müsaitlik, kısıt ve fiyat gönderme, rezervasyon alma işlemleri listelenir. POS harcamalarını misafir foliosuna işleme desteği tanıtılır.
  - `MAIN-EXPEDIA-API` [Expedia Connectivity: Lodging APIs overview](https://connectivityportal.expediagroup.com/documentation/expedia): Resmi doküman availability/rates, reservation management ve product management sınırlarını ayırır. Product API oda tipi/rate plan yönetimini, review API onaylı yorumlara yanıt ve puan agregasını tanıtır.

#### RM-03: Farklılaşma ve SWOT'u test edilebilir hipotezlere çevir
*Öncelik P0 · Efor M · Sorumlu: Ürün kurucusu · Bağımlılık: RM-01, RM-02 · Karşıladığı gereksinim: R1, R10*

**Amaç:** 'Daha kolay, ucuz, güvenli' iddiaları yerine hangi kullanıcı sonucunda daha iyi olacağımızı ve hangi kanıtla bunu söyleyebileceğimizi belirlemek.

**Nerede:**
- `planning/product/positioning-and-hypotheses.md` (önerilen/yeni)

**Nasıl:**
1. Aday değer önerisi: seçili bağımsız oteller için düşük eğitim ihtiyacı, açık toplam fiyat/iptal bilgisi, güvenilir teyit ve yerel destek. Bunların şu an ürün avantajı değil hedef olduğunu işaretle.
2. Her hipoteze segment, başlangıç ölçümü, deney, başarı ve vazgeçme koşulu ekle. Onboarding süresini ölçmeden 'rakipten hızlı' deme.
3. Güçlü yanlara mevcut kod/operasyon altyapısını ve daraltılabilir ürün yönünü yaz. Zayıflıklara henüz müşteri/talep, tesis ağı, güven, stok entegrasyonu ve canlı işlem kanıtı olmamasını yaz.
4. Geniş katalog ve en düşük fiyat yarışını MVP dışında tut. İnceleme sonucunda sorun zayıfsa özellik eklemek yerine segmenti değiştir.

**Sözleşme/kontrat:**
- HypothesisCard: audience, promisedOutcome, baseline, experiment, proposedThreshold, falsification, owner, nextDecision.

**Kabul ölçütü (bu görev uygulandığında koşulacak testler):**
- Her fark iddiası ölçülebilir deney ve başarısızlık kararı içerir.
- En az 3 farklılaşma adayı, 4 güçlü/zayıf yön ve ilgili önlem kayıtlıdır.
- Kaynaklı olgu, repo bulgusu ve asistan çıkarımı ayrı etiketlidir.

**Teslimat:** Konumlandırma cümlesi; SWOT ve deney kartları

**İlgili kaynaklar:**
  - `b1` [List Your Apartment, Hotel, Vacation Home, or B&B on Booking.com](https://www.join.booking.com/index.html): Tesis listeleme ve Extranet bağlantısı sunulur. Yayına geçiş öncesinde tesis doğrulaması istenebileceği belirtilir. Başka seyahat sitelerinden tesis bilgisi aktarımı ve takvim eşleme tanıtılır. Ödeme kolaylaştırma sunulur, ödeme yöntemi ve zamanlaması bölgesel uygunluğa bağlıdır.
  - `e1` [Log in to your Expedia Group portal](https://partner.expediagroup.com/en-us/log-in): Resmi portal tanıtımı, otel ortaklarının Partner Central üzerinden rekabet içgörüleri inceleyebileceğini, envanter yönetebileceğini ve fiyatlarını optimize edebileceğini belirtir.
  - `e2` [Welcome | EG Connectivity Hub](https://developers.expediagroup.com/supply/lodging): PMS, channel-manager ve CRS sağlayıcıları için tesis listeleme ve yönetim bağlantıları tanıtılır. Entegrasyon süreci lisans ve PCI incelemesi ile geliştirme, test ve soft launch aşamalarını içerir. Sayfa bireysel tesislerden doğrudan API bağlantısı kabul edilmediğini belirtir.
  - `o1` [İşlem Rehberi (Rezervasyon) | Otelz](https://www.otelz.com/corporate/company/user-guide): Kamuya açık içerikte tesis ekleme, Extranet giriş, rezervasyon yönetme ve tüketici mobil uygulama indirme bağlantıları bulunur. Otel, termal otel, kayak oteli, daire ve villa kategorileri görünür.
  - `n1` [ENUYGUN Türkiye'nin Seyahat Sitesi - Uçak Bileti, Otobüs, Otel](https://www.enuygun.com/): Aynı tüketici sitesinde uçak, otel, otobüs, uçak+otel, araç kiralama ve transfer arama yüzeyleri vardır. Seyahat yönetimi altında online check-in ve uçak, otobüs, otel rezervasyonu durum sorgulama tanıtılır. Tüketici mobil uygulaması tanıtılır.
  - `n2` [Hakkında - ENUYGUN](https://www.enuygun.com/hakkinda/): Şirket uçak, otobüs, otel, transfer ve araç kiralama ürünlerini sunduğunu belirtir. Otellerin fiyat ve özelliklerini karşılaştırarak rezervasyon yapılmasını tanıtır.
  - `s1` [Hotel Channel Manager | SiteMinder](https://www.siteminder.com/channel-manager/): Oda ve fiyat planı eşleme, fiyat, müsaitlik ve kısıtların tek platformdan kanallara eşlenmesi tanıtılır. Booking.com'a medya, oda ve fiyat planı gönderimi ve seçili PMS ortakları için fiyat ve kısıt eşlemesi belirtilir. İçgörü ve pace raporlaması sunulur.
  - `s2` [Developer Guide | SiteMinder](https://developer.siteminder.com/): Resmi geliştirici rehberi yeni ortaklar için başvuru, geliştirme, sertifikasyon ve canlıya geçişten oluşan yapılandırılmış entegrasyon yolu tanıtır. Mevcut ortakların ek kabiliyetlerle entegrasyonlarını genişletmesi için ayrı yol gösterilir.
  - `h1` [HotelRunner | A Sales-First Approach to Hospitality](https://hotelrunner.com/): Ürün kataloğunda PMS, CRS, channel-manager, booking engine, otel web sitesi, misafir ilişki yönetimi, yorum merkezi, mobil uygulama, POS, grup otel yönetimi ve analitik bulunur. Operasyon yönetimi ile satış ve dağıtım ürünleri aynı platform altında tanıtılır.
  - `h2` [Introduction | HotelRunner Developer Docs](https://developers.hotelrunner.com/): App Manager belgeleri PMS, gelir yönetimi, POS ve hotspot bağlantıları tanıtır. XML/SOAP ve REST/JSON üzerinden müsaitlik, kısıt ve fiyat gönderme, rezervasyon alma işlemleri listelenir. POS harcamalarını misafir foliosuna işleme desteği tanıtılır.
  - `MAIN-YC-DISCOVERY` [YC Startup School Week 4: early users and host learning](https://www.ycombinator.com/blog/startup-school-week-4-recap-kat-manalac-and-gustaf-alstromer/): YC'nin resmi konuşma transkripti erken Airbnb host'larıyla doğrudan görüşerek liste içeriği/güven sorunlarını öğrenmeyi ve erken kullanıcıları ölçeklenmeyen kişisel çalışmalarla bulmayı örnekler.

#### RM-04: Gelir modeli ve rezervasyon başına ekonomi senaryosu
*Öncelik P0 · Efor M · Sorumlu: Kurucu / finans · Bağımlılık: RM-01, RM-03 · Karşıladığı gereksinim: R6, R12*

**Amaç:** Teknik olarak rezervasyon almanın kârlı ve sürdürülebilir işletme yaratmakla aynı olmadığını hesaba katmak.

**Nerede:**
- `planning/business/unit-economics.csv` (önerilen/yeni)

**Nasıl:**
1. İlk pilotta pazaryeri komisyonu ana aday model olarak değerlendir, SaaS aboneliğini ayrı alternatif satır yap. Gerçek oranı işletme ve payment provider teklifi olmadan belirlenmiş kabul etme.
2. GMV, brüt komisyon, ödeme maliyeti, iade/chargeback kaybı, destek süresi, kampanya sübvansiyonu ve müşteri edinim maliyetini ayrı girdiler yap.
3. Rezervasyon katkısı = net platform geliri - değişken işlem/destek/edinim maliyetleri. İşletmeye aktarılacak para gelir sayılmaz. Break-even yalnız katkı pozitifse hesaplanır.
4. Düşük/orta/yüksek talep ve iptal senaryosu oluştur. Tutarları tahmin etiketi ve kaynağıyla tut, KOSGEB'i onaylı finansman gibi bütçeye ekleme.

**Sözleşme/kontrat:**
- EconomicsScenario: currency, assumptions, commissionBasis, paymentFee, refundRate, supportCost, CAC, contributionPerBooking, fixedCost.

**Kabul ölçütü (bu görev uygulandığında koşulacak testler):**
- Gerçek teklif bekleyen girdiler görünür, sahte kesin fiyat veya komisyon yoktur.
- En az 3 senaryoda katkı/break-even hesapları yeniden üretilebilir.
- Negatif katkılı model için fiyat, kanal veya ürün kapsamı kararı vardır. Sadece GMV büyümesi başarı sayılmaz.

**Teslimat:** Varsayım etiketli finans modeli; Gelir modeli karar kaydı

**İlgili kaynaklar:**
  - `iyzico-submerchant` [iyzico: Alt Üye](https://docs.iyzico.com/urunler/pazaryeri/pazaryeri-entegrasyonu/alt-uye-olusturma): Onboarding API üç alt üye tipi destekler. Alanlar tipe göre değişir. Yanıttaki subMerchantKey satıcıyı ödeme sırasında tanımlar. IBAN ilgili kişi veya şirket unvanıyla uyumlu olmalıdır ve para gönderimi için onay öncesinde tamamlanmalıdır. Sayfa gerçek bilgiler gönderilmesini açıkça ister.
  - `iyzico-payment` [iyzico: Pazaryeri Ödemesi](https://docs.iyzico.com/urunler/pazaryeri/pazaryeri-entegrasyonu/pazaryeri-odemesi): Pazaryeri ödeme kırılımlarında subMerchantKey ve subMerchantPrice gönderilir. İkinci alan alt üyeye gönderilmesi istenen tutardır. Sayfadaki NON3D örneği tüm yöntemlerde alt üye alanlarının eklenmesi gerektiğini belirtir.
  - `iyzico-approval` [iyzico: Onay](https://docs.iyzico.com/urunler/pazaryeri/pazaryeri-entegrasyonu/onay): Belge, ödeme kırılımı onaylanana kadar paranın korumalı havuz hesapta bekletildiğini açıklar. Kırılım onayı ve onay kaldırma paymentTransactionId üzerinden yapılır. Onaylar kod seviyesinde gönderilir, panelde bu fonksiyon bulunmadığı belirtilir.
  - `iyzico-sandbox` [iyzico: Live vs Sandbox](https://docs.iyzico.com/en/getting-started/preliminaries/live-vs-sandbox): Sandbox gerçek işlem veya fonları etkilemeden ödeme akışlarını simüle eder. Sandbox ve canlı API adresleri ile kimlik bilgileri farklıdır. Test kartları yalnız sandbox senaryoları için sunulur.
  - `iyzico-refund` [iyzico: Refund & Cancel](https://docs.iyzico.com/en/advanced/refund-and-cancel): Standart iade paymentTransactionId ve price kullanır, tam veya kısmi olabilir ve kalan iade edilebilir tutarı aşamaz. Belge çok ürünlü sepetlerde Refund V2 kullanımını önermez. Cancel aynı gün ve tam işlem içindir. Karta yansıma bankaya göre değişir.
  - `iyzico-webhook` [iyzico: Webhook](https://docs.iyzico.com/ek-servisler/webhook): Webhook JSON HTTP POST bildirimi kullanır. HTTPS URL istenir. Belge X-IYZ-SIGNATURE-V3 doğrulamasını açıklar ve önceki sürümlerin desteklenmeyeceğini belirtir. İmza özelliğinin hesapta etkin olması gerekir. 2xx gelmeyen bildirimler için tekrar davranışı belgelenmiştir.
  - `MAIN-APPLE-PAYMENT` [Apple App Review Guidelines: 3.1.3(e)](https://developer.apple.com/app-store/review/guidelines/): 3.1.3(e) uygulama dışında tüketilen fiziksel ürün/hizmetlerin ödemesinde in-app purchase dışındaki yöntemleri belirtir.
  - `b1` [List Your Apartment, Hotel, Vacation Home, or B&B on Booking.com](https://www.join.booking.com/index.html): Tesis listeleme ve Extranet bağlantısı sunulur. Yayına geçiş öncesinde tesis doğrulaması istenebileceği belirtilir. Başka seyahat sitelerinden tesis bilgisi aktarımı ve takvim eşleme tanıtılır. Ödeme kolaylaştırma sunulur, ödeme yöntemi ve zamanlaması bölgesel uygunluğa bağlıdır.
  - `e1` [Log in to your Expedia Group portal](https://partner.expediagroup.com/en-us/log-in): Resmi portal tanıtımı, otel ortaklarının Partner Central üzerinden rekabet içgörüleri inceleyebileceğini, envanter yönetebileceğini ve fiyatlarını optimize edebileceğini belirtir.
  - `e2` [Welcome | EG Connectivity Hub](https://developers.expediagroup.com/supply/lodging): PMS, channel-manager ve CRS sağlayıcıları için tesis listeleme ve yönetim bağlantıları tanıtılır. Entegrasyon süreci lisans ve PCI incelemesi ile geliştirme, test ve soft launch aşamalarını içerir. Sayfa bireysel tesislerden doğrudan API bağlantısı kabul edilmediğini belirtir.
  - `o1` [İşlem Rehberi (Rezervasyon) | Otelz](https://www.otelz.com/corporate/company/user-guide): Kamuya açık içerikte tesis ekleme, Extranet giriş, rezervasyon yönetme ve tüketici mobil uygulama indirme bağlantıları bulunur. Otel, termal otel, kayak oteli, daire ve villa kategorileri görünür.
  - `n1` [ENUYGUN Türkiye'nin Seyahat Sitesi - Uçak Bileti, Otobüs, Otel](https://www.enuygun.com/): Aynı tüketici sitesinde uçak, otel, otobüs, uçak+otel, araç kiralama ve transfer arama yüzeyleri vardır. Seyahat yönetimi altında online check-in ve uçak, otobüs, otel rezervasyonu durum sorgulama tanıtılır. Tüketici mobil uygulaması tanıtılır.
  - `n2` [Hakkında - ENUYGUN](https://www.enuygun.com/hakkinda/): Şirket uçak, otobüs, otel, transfer ve araç kiralama ürünlerini sunduğunu belirtir. Otellerin fiyat ve özelliklerini karşılaştırarak rezervasyon yapılmasını tanıtır.
  - `s1` [Hotel Channel Manager | SiteMinder](https://www.siteminder.com/channel-manager/): Oda ve fiyat planı eşleme, fiyat, müsaitlik ve kısıtların tek platformdan kanallara eşlenmesi tanıtılır. Booking.com'a medya, oda ve fiyat planı gönderimi ve seçili PMS ortakları için fiyat ve kısıt eşlemesi belirtilir. İçgörü ve pace raporlaması sunulur.
  - `s2` [Developer Guide | SiteMinder](https://developer.siteminder.com/): Resmi geliştirici rehberi yeni ortaklar için başvuru, geliştirme, sertifikasyon ve canlıya geçişten oluşan yapılandırılmış entegrasyon yolu tanıtır. Mevcut ortakların ek kabiliyetlerle entegrasyonlarını genişletmesi için ayrı yol gösterilir.
  - `h1` [HotelRunner | A Sales-First Approach to Hospitality](https://hotelrunner.com/): Ürün kataloğunda PMS, CRS, channel-manager, booking engine, otel web sitesi, misafir ilişki yönetimi, yorum merkezi, mobil uygulama, POS, grup otel yönetimi ve analitik bulunur. Operasyon yönetimi ile satış ve dağıtım ürünleri aynı platform altında tanıtılır.
  - `h2` [Introduction | HotelRunner Developer Docs](https://developers.hotelrunner.com/): App Manager belgeleri PMS, gelir yönetimi, POS ve hotspot bağlantıları tanıtır. XML/SOAP ve REST/JSON üzerinden müsaitlik, kısıt ve fiyat gönderme, rezervasyon alma işlemleri listelenir. POS harcamalarını misafir foliosuna işleme desteği tanıtılır.

#### RM-05: Satış, ödeme, veri ve canlı kullanım sorumluluğunu netleştir
*Öncelik P0 · Efor L · Sorumlu: Kurucu + hukuk/muhasebe danışmanı · Bağımlılık: RM-04 · Karşıladığı gereksinim: R6, R10, R12*

**Amaç:** Kimin satıcı/acente/ödeme alan taraf olduğu belirsizken ödeme ve payout tasarımını kesinleştirmemek.

**Nerede:**
- `planning/business/legal-and-payment-decisions.md` (önerilen/yeni)
- `payment-service/src/main/java` (mevcut)

**Nasıl:**
1. Operatör, acente, merchant-of-record, submerchant ve işletme para aktarımı seçeneklerini sorumluluk matrisiyle yaz. Bir provider'ın API'sinin varlığı ticari kabul demek değildir.
2. Resmi TÜRSAB/KVKK ve seçilecek provider şartlarını uzmanla iş modeline uygulat. 'Her durumda şu belge zorunlu' veya doğrulanmamış ücret/tutar yazma.
3. İptal/iade, vergi/fatura, chargeback, müşteri şikâyeti ve işletme çıkışında mevcut rezervasyonun sorumlusunu belirle.
4. Veri envanteri çıkar: misafir, kullanıcı, işletme belgesi, ödeme referansı ve audit. İşleme amacı/dayanağı, aydınlatma, retention, erişim ve silme talebi akışlarını ayrıştır, her şeyi consent kutusuna bağlama.

**Sözleşme/kontrat:**
- ResponsibilityDecision: scenario, responsibleEntity, legalReviewReference, providerWrittenAcceptance, retentionPolicy, openQuestion, liveGate.

**Kabul ölçütü (bu görev uygulandığında koşulacak testler):**
- Satış ve ödeme akışındaki her rol için yazılı karar ve uzman/provider doğrulaması ihtiyacı atanmıştır.
- Canlı satış kapısını bloke eden belirsizlikler açık listeye alınır. Sandbox çalışma hukuki izin sayılmaz.
- Hesap silme talebi booking/finans geçmişini körlemesine cascade silmez, saklama politikası ayrıdır.

**Teslimat:** Ticari/hukuki karar matrisi; Veri envanteri ve retention taslağı; Canlı satış ön koşulları

**İlgili kaynaklar:**
  - `iyzico-submerchant` [iyzico: Alt Üye](https://docs.iyzico.com/urunler/pazaryeri/pazaryeri-entegrasyonu/alt-uye-olusturma): Onboarding API üç alt üye tipi destekler. Alanlar tipe göre değişir. Yanıttaki subMerchantKey satıcıyı ödeme sırasında tanımlar. IBAN ilgili kişi veya şirket unvanıyla uyumlu olmalıdır ve para gönderimi için onay öncesinde tamamlanmalıdır. Sayfa gerçek bilgiler gönderilmesini açıkça ister.
  - `iyzico-payment` [iyzico: Pazaryeri Ödemesi](https://docs.iyzico.com/urunler/pazaryeri/pazaryeri-entegrasyonu/pazaryeri-odemesi): Pazaryeri ödeme kırılımlarında subMerchantKey ve subMerchantPrice gönderilir. İkinci alan alt üyeye gönderilmesi istenen tutardır. Sayfadaki NON3D örneği tüm yöntemlerde alt üye alanlarının eklenmesi gerektiğini belirtir.
  - `iyzico-approval` [iyzico: Onay](https://docs.iyzico.com/urunler/pazaryeri/pazaryeri-entegrasyonu/onay): Belge, ödeme kırılımı onaylanana kadar paranın korumalı havuz hesapta bekletildiğini açıklar. Kırılım onayı ve onay kaldırma paymentTransactionId üzerinden yapılır. Onaylar kod seviyesinde gönderilir, panelde bu fonksiyon bulunmadığı belirtilir.
  - `iyzico-sandbox` [iyzico: Live vs Sandbox](https://docs.iyzico.com/en/getting-started/preliminaries/live-vs-sandbox): Sandbox gerçek işlem veya fonları etkilemeden ödeme akışlarını simüle eder. Sandbox ve canlı API adresleri ile kimlik bilgileri farklıdır. Test kartları yalnız sandbox senaryoları için sunulur.
  - `iyzico-refund` [iyzico: Refund & Cancel](https://docs.iyzico.com/en/advanced/refund-and-cancel): Standart iade paymentTransactionId ve price kullanır, tam veya kısmi olabilir ve kalan iade edilebilir tutarı aşamaz. Belge çok ürünlü sepetlerde Refund V2 kullanımını önermez. Cancel aynı gün ve tam işlem içindir. Karta yansıma bankaya göre değişir.
  - `iyzico-webhook` [iyzico: Webhook](https://docs.iyzico.com/ek-servisler/webhook): Webhook JSON HTTP POST bildirimi kullanır. HTTPS URL istenir. Belge X-IYZ-SIGNATURE-V3 doğrulamasını açıklar ve önceki sürümlerin desteklenmeyeceğini belirtir. İmza özelliğinin hesapta etkin olması gerekir. 2xx gelmeyen bildirimler için tekrar davranışı belgelenmiştir.
  - `MAIN-APPLE-PAYMENT` [Apple App Review Guidelines: 3.1.3(e)](https://developer.apple.com/app-store/review/guidelines/): 3.1.3(e) uygulama dışında tüketilen fiziksel ürün/hizmetlerin ödemesinde in-app purchase dışındaki yöntemleri belirtir.
  - `kvkk-security` [KVKK: Kişisel Veri Güvenliği Rehberi, Teknik ve İdari Tedbirler](https://www.kvkk.gov.tr/yayinlar/veri_guvenligi_rehberi.pdf): Resmi rehber risklere uygun teknik ve idari tedbirleri ele alır. Veri envanteri, erişim politikaları, veri işleyen ilişkileri, bulut, yedekleme ve saklama/imha başlıkları vardır. İhtiyaç duyulmayan verinin güvenli imhası ve veri işleyen güvenliğinin değerlendirilmesi açıklanır.
  - `kvkk-notice-consent` [KVKK: 18.02.2026 tarihli 2026/347 sayılı ilke kararına ilişkin aydınlatma ve açık rıza duyurusu](https://www.kvkk.gov.tr/Icerik/8710/veri-sorumlulari-tarafindan-acik-riza-ve-aydinlatma-metinlerinin-ayri-ayri-duzenlenmesi-gerektigi-hakkinda-kisisel-verileri-koruma-kurulunun-18-02-2026-tarihli-ve-2026-347-sayili-ilke-kararina-iliskin-kamuoyu-duyurusu): Kurum duyurusu aydınlatma ve açık rıza metinlerinin ayrı düzenlenmesini, aydınlatma için onay/rıza istenmemesini ve metinlerin faaliyete uyarlanmasını vurgular. Veri kategorileri, işleme amaçları ve hukuki sebepler açıkça ifade edilmelidir.
  - `tursab-licensing` [TÜRSAB: Yeni İşletme Belgesi Başvurusu](https://www.tursab.org.tr/yeni-isletme-belgesi-basvurusu): Resmi sayfa seyahat acentası işletme belgesi isteyen tüzel kişiler için Bakanlığa unvan başvurusu, TÜRSAB incelemesi ve Bakanlık belge sürecini açıklar. Seyahat acentası sıfatının üyelik ve Bakanlık işletme belgesinin teslimiyle kazanıldığı belirtilir.

#### RM-06: Pilot otel arzı, allotment ve destek operasyonu
*Öncelik P0 · Efor L · Sorumlu: Kurucu / otel operasyonları · Bağımlılık: RM-01, RM-05 · Karşıladığı gereksinim: R1, R5, R11, R12*

**Amaç:** Boş bir uygulama yerine doğrulanmış içerik ve satılabilir stok veren işletmelerle pilotun zeminini kurmak.

**Nerede:**
- `planning/operations/pilot-hotel-playbook.md` (önerilen/yeni)

**Nasıl:**
1. Önerilen pilot: tek bölgede 3-5 işletme. Hedef sayıdır, anlaşılmış otel olarak sunulmaz. Belge, işletme temsil yetkisi ve görsel hakları denetim kontrol listesi hazırla.
2. Waylo'ya ayrılan günlük allotment, güncelleme sıklığı, son oda satışı, stop-sell ve diğer kanallar için sorumluluk yaz. Channel-manager yokken tüm tesis overbooking'ini engellediğimiz iddia edilmez.
3. İşletme oturum daveti, içerik aktarımı, fiyat/vergi/iptal kontrolü ve örnek rezervasyon teyidi adımlarını playbook'a koy.
4. Destek kapsamı/saatleri, teyit gelmediğinde yapılacaklar, alternatif konaklama, refund, stok uyuşmazlığı ve pilot durdurma kişisini belirle. Sunulmayan 7/24 SLA vaat etme.

**Sözleşme/kontrat:**
- PilotHotelAgreementChecklist: authorizedContact, rights, allotmentSource, rateCurrency, cancelPolicy, updateDuty, incidentContact, acceptanceEvidence.

**Kabul ölçütü (bu görev uygulandığında koşulacak testler):**
- En az 3 adayda içerik ve allotment sorumluluğu açık, sağlayamıyorsa pilot ertelenir.
- Stok tutarsızlığı ve otel yanıt vermeme senaryosu masa başı prova ile karar/eskalasyon üretir.
- Pilot kayıtları test ve canlı ortam ayrımıyla listelenir, gerçek kişiye plansız e-posta/ödeme yapılmaz.

**Teslimat:** Pilot otel kontrol listesi; Destek ve stok incident playbook'u

**İlgili kaynaklar:**
  - `MAIN-BOOKING-ARI` [Booking.com: Create or update inventory, rates and restrictions](https://developers.booking.com/connectivity/docs/b_xml-availability): Resmi örnek stok sayısını oda tipi seviyesinde bütün rate planlar için ortak tutar. Fiyat ve kısıtlar oda tipi/rate plan/tarih boyutlarıyla güncellenir, tarih aralığının son günü dahil değildir.
  - `e1` [Log in to your Expedia Group portal](https://partner.expediagroup.com/en-us/log-in): Resmi portal tanıtımı, otel ortaklarının Partner Central üzerinden rekabet içgörüleri inceleyebileceğini, envanter yönetebileceğini ve fiyatlarını optimize edebileceğini belirtir.
  - `e2` [Welcome | EG Connectivity Hub](https://developers.expediagroup.com/supply/lodging): PMS, channel-manager ve CRS sağlayıcıları için tesis listeleme ve yönetim bağlantıları tanıtılır. Entegrasyon süreci lisans ve PCI incelemesi ile geliştirme, test ve soft launch aşamalarını içerir. Sayfa bireysel tesislerden doğrudan API bağlantısı kabul edilmediğini belirtir.
  - `MAIN-EXPEDIA-API` [Expedia Connectivity: Lodging APIs overview](https://connectivityportal.expediagroup.com/documentation/expedia): Resmi doküman availability/rates, reservation management ve product management sınırlarını ayırır. Product API oda tipi/rate plan yönetimini, review API onaylı yorumlara yanıt ve puan agregasını tanıtır.
  - `b1` [List Your Apartment, Hotel, Vacation Home, or B&B on Booking.com](https://www.join.booking.com/index.html): Tesis listeleme ve Extranet bağlantısı sunulur. Yayına geçiş öncesinde tesis doğrulaması istenebileceği belirtilir. Başka seyahat sitelerinden tesis bilgisi aktarımı ve takvim eşleme tanıtılır. Ödeme kolaylaştırma sunulur, ödeme yöntemi ve zamanlaması bölgesel uygunluğa bağlıdır.
  - `o1` [İşlem Rehberi (Rezervasyon) | Otelz](https://www.otelz.com/corporate/company/user-guide): Kamuya açık içerikte tesis ekleme, Extranet giriş, rezervasyon yönetme ve tüketici mobil uygulama indirme bağlantıları bulunur. Otel, termal otel, kayak oteli, daire ve villa kategorileri görünür.
  - `n1` [ENUYGUN Türkiye'nin Seyahat Sitesi - Uçak Bileti, Otobüs, Otel](https://www.enuygun.com/): Aynı tüketici sitesinde uçak, otel, otobüs, uçak+otel, araç kiralama ve transfer arama yüzeyleri vardır. Seyahat yönetimi altında online check-in ve uçak, otobüs, otel rezervasyonu durum sorgulama tanıtılır. Tüketici mobil uygulaması tanıtılır.
  - `n2` [Hakkında - ENUYGUN](https://www.enuygun.com/hakkinda/): Şirket uçak, otobüs, otel, transfer ve araç kiralama ürünlerini sunduğunu belirtir. Otellerin fiyat ve özelliklerini karşılaştırarak rezervasyon yapılmasını tanıtır.
  - `s1` [Hotel Channel Manager | SiteMinder](https://www.siteminder.com/channel-manager/): Oda ve fiyat planı eşleme, fiyat, müsaitlik ve kısıtların tek platformdan kanallara eşlenmesi tanıtılır. Booking.com'a medya, oda ve fiyat planı gönderimi ve seçili PMS ortakları için fiyat ve kısıt eşlemesi belirtilir. İçgörü ve pace raporlaması sunulur.
  - `s2` [Developer Guide | SiteMinder](https://developer.siteminder.com/): Resmi geliştirici rehberi yeni ortaklar için başvuru, geliştirme, sertifikasyon ve canlıya geçişten oluşan yapılandırılmış entegrasyon yolu tanıtır. Mevcut ortakların ek kabiliyetlerle entegrasyonlarını genişletmesi için ayrı yol gösterilir.
  - `h1` [HotelRunner | A Sales-First Approach to Hospitality](https://hotelrunner.com/): Ürün kataloğunda PMS, CRS, channel-manager, booking engine, otel web sitesi, misafir ilişki yönetimi, yorum merkezi, mobil uygulama, POS, grup otel yönetimi ve analitik bulunur. Operasyon yönetimi ile satış ve dağıtım ürünleri aynı platform altında tanıtılır.
  - `h2` [Introduction | HotelRunner Developer Docs](https://developers.hotelrunner.com/): App Manager belgeleri PMS, gelir yönetimi, POS ve hotspot bağlantıları tanıtır. XML/SOAP ve REST/JSON üzerinden müsaitlik, kısıt ve fiyat gönderme, rezervasyon alma işlemleri listelenir. POS harcamalarını misafir foliosuna işleme desteği tanıtılır.

#### RM-07: İşletme ve müşteri akışlarını prototipte doğrula
*Öncelik P0 · Efor L · Sorumlu: Ürün / UX · Bağımlılık: RM-03, RM-06 · Karşıladığı gereksinim: R1, R4, R7, R8*

**Amaç:** Backend ve mobile ekranlarını yapmadan asıl akışların anlaşılabilirliğini gözlemek, kapsamı dondurmak.

**Nerede:**
- `planning/design/partner-and-consumer-flows.md` (önerilen/yeni)
- `frontend/src/app` (mevcut)

**Nasıl:**
1. Partner için başvuru→taslak→oda tipi/rate plan→takvim→yayın talebi akışını tıklanabilir prototip veya ekran şemasıyla çiz.
2. Müşteri için tarih/kişi→otel/oda→toplam fiyat ve koşullar→ödeme sonucu→teyit→iptal→yorum akışını çiz. Pending/error/unknown/no-review gibi başarısız ve boş durumları dahil et.
3. Önerilen keşif testi: 5 otel çalışanı ve 5 tüketici. Yönlendirmesiz görev tamamlama, anlama hatası, süre ve geri dönme nedenini not et. Küçük örneklem pazar genellemesi değildir.
4. Prototipte görsel/sahte veri kullanılabilir fakat production mock gibi sayılmaz. Ekran üstünde demo etiketi bulunur. Kullanıcı onayı olmadan ücretli tasarım aracı seçme.

**Sözleşme/kontrat:**
- FlowStateMap: screen, actor, allowedAction, requiredData, loading, empty, error, pending, nextState.

**Kabul ölçütü (bu görev uygulandığında koşulacak testler):**
- Önerilen kapı: her grupta 4/5 ana görevi yardımsız tamamlar, kritik toplam fiyat/iptal yanlış anlaması sıfır veya yeniden tasarım yapılır.
- Owner'ın yayını kendi onaylayamadığı, payment-success ile reservation-confirmed ayrımı ekranda anlaşılır.
- MVP Must/Should/Later listesi kullanıcı tarafından gözden geçirilebilir halde kaydedilir.

**Teslimat:** İki aktörün akış prototipi; UX gözlem raporu; MVP kapsam kartı

**İlgili kaynaklar:**
  - `MAIN-YC-DISCOVERY` [YC Startup School Week 4: early users and host learning](https://www.ycombinator.com/blog/startup-school-week-4-recap-kat-manalac-and-gustaf-alstromer/): YC'nin resmi konuşma transkripti erken Airbnb host'larıyla doğrudan görüşerek liste içeriği/güven sorunlarını öğrenmeyi ve erken kullanıcıları ölçeklenmeyen kişisel çalışmalarla bulmayı örnekler.
  - `b1` [List Your Apartment, Hotel, Vacation Home, or B&B on Booking.com](https://www.join.booking.com/index.html): Tesis listeleme ve Extranet bağlantısı sunulur. Yayına geçiş öncesinde tesis doğrulaması istenebileceği belirtilir. Başka seyahat sitelerinden tesis bilgisi aktarımı ve takvim eşleme tanıtılır. Ödeme kolaylaştırma sunulur, ödeme yöntemi ve zamanlaması bölgesel uygunluğa bağlıdır.
  - `e1` [Log in to your Expedia Group portal](https://partner.expediagroup.com/en-us/log-in): Resmi portal tanıtımı, otel ortaklarının Partner Central üzerinden rekabet içgörüleri inceleyebileceğini, envanter yönetebileceğini ve fiyatlarını optimize edebileceğini belirtir.
  - `e2` [Welcome | EG Connectivity Hub](https://developers.expediagroup.com/supply/lodging): PMS, channel-manager ve CRS sağlayıcıları için tesis listeleme ve yönetim bağlantıları tanıtılır. Entegrasyon süreci lisans ve PCI incelemesi ile geliştirme, test ve soft launch aşamalarını içerir. Sayfa bireysel tesislerden doğrudan API bağlantısı kabul edilmediğini belirtir.
  - `o1` [İşlem Rehberi (Rezervasyon) | Otelz](https://www.otelz.com/corporate/company/user-guide): Kamuya açık içerikte tesis ekleme, Extranet giriş, rezervasyon yönetme ve tüketici mobil uygulama indirme bağlantıları bulunur. Otel, termal otel, kayak oteli, daire ve villa kategorileri görünür.
  - `n1` [ENUYGUN Türkiye'nin Seyahat Sitesi - Uçak Bileti, Otobüs, Otel](https://www.enuygun.com/): Aynı tüketici sitesinde uçak, otel, otobüs, uçak+otel, araç kiralama ve transfer arama yüzeyleri vardır. Seyahat yönetimi altında online check-in ve uçak, otobüs, otel rezervasyonu durum sorgulama tanıtılır. Tüketici mobil uygulaması tanıtılır.
  - `n2` [Hakkında - ENUYGUN](https://www.enuygun.com/hakkinda/): Şirket uçak, otobüs, otel, transfer ve araç kiralama ürünlerini sunduğunu belirtir. Otellerin fiyat ve özelliklerini karşılaştırarak rezervasyon yapılmasını tanıtır.
  - `s1` [Hotel Channel Manager | SiteMinder](https://www.siteminder.com/channel-manager/): Oda ve fiyat planı eşleme, fiyat, müsaitlik ve kısıtların tek platformdan kanallara eşlenmesi tanıtılır. Booking.com'a medya, oda ve fiyat planı gönderimi ve seçili PMS ortakları için fiyat ve kısıt eşlemesi belirtilir. İçgörü ve pace raporlaması sunulur.
  - `s2` [Developer Guide | SiteMinder](https://developer.siteminder.com/): Resmi geliştirici rehberi yeni ortaklar için başvuru, geliştirme, sertifikasyon ve canlıya geçişten oluşan yapılandırılmış entegrasyon yolu tanıtır. Mevcut ortakların ek kabiliyetlerle entegrasyonlarını genişletmesi için ayrı yol gösterilir.
  - `h1` [HotelRunner | A Sales-First Approach to Hospitality](https://hotelrunner.com/): Ürün kataloğunda PMS, CRS, channel-manager, booking engine, otel web sitesi, misafir ilişki yönetimi, yorum merkezi, mobil uygulama, POS, grup otel yönetimi ve analitik bulunur. Operasyon yönetimi ile satış ve dağıtım ürünleri aynı platform altında tanıtılır.
  - `h2` [Introduction | HotelRunner Developer Docs](https://developers.hotelrunner.com/): App Manager belgeleri PMS, gelir yönetimi, POS ve hotspot bağlantıları tanıtır. XML/SOAP ve REST/JSON üzerinden müsaitlik, kısıt ve fiyat gönderme, rezervasyon alma işlemleri listelenir. POS harcamalarını misafir foliosuna işleme desteği tanıtılır.
  - `TECH-NEXT-LAYOUT` [Next.js: Layouts and Pages](https://nextjs.org/docs/app/getting-started/layouts-and-pages): App Router layout birden fazla sayfanın ortak UI'ını kapsar, gezinmede durumunu korur ve yeniden render edilmez.
  - `TECH-NEXT-AUTH` [Next.js: Authentication](https://nextjs.org/docs/app/guides/authentication): Partial rendering nedeniyle layout auth kontrolü her route değişiminde çalışmaz. Doküman kontrolleri veri kaynağına yakın DAL katmanında yapmayı önerir, layoutta çocukları gizlemek RSC payload erişimini önlemez.

### F1: Mimari sözleşme ve ortak temel tasarımı

#### RM-08: Domain sınırları, ERD ve veri sahipliği
*Öncelik P0 · Efor L · Sorumlu: Backend / mimari · Bağımlılık: RM-05, RM-07 · Karşıladığı gereksinim: R2, R3, R9, R10*

**Amaç:** Kiracı, kullanıcı, otel ve sipariş ilişkilerini sonraki uçuş/araç genişlemesinde yeniden kimlik modeli kurmadan taşıyacak şekilde tanımlamak.

**Nerede:**
- `PRODUCT-ARCHITECTURE.md` (mevcut)
- `planning/architecture/domain-erd.md` (önerilen/yeni)

**Nasıl:**
1. Organization/Membership/RoleAssignment, Hotel/ContentRevision, RoomType/PhysicalRoom/RatePlan/DailyInventory/DailyRate/Hold/HotelReservation, Order/OrderItem/PaymentAttempt/Refund/Review tablolarını ERD'de göster.
2. Her tablonun servis sahibi, tenant/user/platform/public sınıfı, unique/FK/index ve retention kararını yaz. Cross-DB join/FK koyma, remote UUID reference kullan.
3. Hotel root kuruluş malı, customer-owned order cross-tenant olabilir. Supplier yalnız kendi item projection'ını görür. PhysicalRoom stok kaynağı ile RoomType satış stoğunu karıştırma.
4. Flight ve car typed fulfillment portlarıyla geleceğe ayrılır. PMS, RMS veya tüm kanalları senkronlayan channel-manager'ı MVP diye inşa etme.

**Sözleşme/kontrat:**
- ERD + TableClassification registry; available=allotment-outOfService-held-confirmed; OrderItem(vertical,supplierOrganizationId,fulfillmentRef,snapshot).

**Kabul ölçütü (bu görev uygulandığında koşulacak testler):**
- Tüm kritik tabloların sahibi ve erişim sınıfı belirli, hassas/public alan ayrımı var.
- İki kuruluşlu müşteri siparişi örneğinde otel A'nın otel B verisi görünmüyor.
- Yeni uçuş/araç ürünü için ayrı domain extension gösterilir, ortak kullanıcı/üyelik modelinin kopyası gerekmiyor.

**Teslimat:** ERD; Domain-owned data ve indeks kararları

**İlgili kaynaklar:**
  - `TECH-TENANT` [OWASP Multi-Tenant Application Security Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Multi_Tenant_Security_Cheat_Sheet.html): İstemciden gelen tenant kimliği yalnızca seçicidir, doğrulanmış kimlik ve güncel üyelikle yetkilendirilmelidir. PostgreSQL tenant bağlamını transaction-local tutmak, havuzdaki bağlantının sonraki isteğe tenant durumunu sızdırmasını önler.
  - `TECH-PG-RLS` [PostgreSQL 16: Row Security Policies](https://www.postgresql.org/docs/16/ddl-rowsecurity.html): RLS görünür satırları ve yazılabilecek satırları ayrı ifadelerle sınırlayabilir. Superuser ve BYPASSRLS rolleri her zaman, tablo sahibi normalde politikayı aşar, FORCE yalnızca sahibi politikaya tabi kılar.
  - `MAIN-BOOKING-ARI` [Booking.com: Create or update inventory, rates and restrictions](https://developers.booking.com/connectivity/docs/b_xml-availability): Resmi örnek stok sayısını oda tipi seviyesinde bütün rate planlar için ortak tutar. Fiyat ve kısıtlar oda tipi/rate plan/tarih boyutlarıyla güncellenir, tarih aralığının son günü dahil değildir.

#### RM-09: API, DTO ve hata sözleşmelerini kesinleştir
*Öncelik P0 · Efor L · Sorumlu: Backend + frontend · Bağımlılık: RM-08 · Karşıladığı gereksinim: R3, R4, R6, R8*

**Amaç:** Frontend ve mobilin aynı business contract'a dayanması, boş method isimlerinden gerçek request/response tasarımına geçmek.

**Nerede:**
- `API-DOCS.md` (mevcut)
- `planning/contracts/waylo-v1.openapi.yaml` (önerilen/yeni)
- `frontend/src/lib/api.ts` (mevcut)

**Nasıl:**
1. /api/v1/me, partner/organizations/{orgId}, admin, public hotels, quotes ve owned orders alanlarını ayrı security scheme/scope ile OpenAPI'de tanımla.
2. Create/update/calendar/quote/order DTO'larına tarih, occupancy, money(currency+decimal), version, quote expiry, cancellation snapshot ve idempotency alanlarını koy. İstemci fiyatı otorite olmasın.
3. 401/403/404/409/422, pagination, unknown/null, 204 ve correlationId davranışlarını örnek response'larla yaz. Tenant yetkisiz resource lookup 404 politikası veri varlığını sızdırmasın.
4. Gateway route ve provider/consumer DTO eşleşmesini contract test listesine dök. Eski /api/... yolları için güvenli compatibility/deprecation planı ekle.

**Sözleşme/kontrat:**
- QuoteRequest(hotelId,roomTypeId,ratePlanId,checkIn,checkOut,adults,childrenAges), Money, OwnedOrderResponse, PartnerReservationResponse; hepsi önerilen v1 sözleşmesi.

**Kabul ölçütü (bu görev uygulandığında koşulacak testler):**
- Her Must ekranın endpoint/DTO/error örneği vardır, UI no-op eylemi veya bulunmayan endpoint bırakılmaz.
- OpenAPI şeması lint/validation ve örnek request/response kontrollerini geçirir, bu task uygulandığında yapılacak testtir.
- Aynı hotel rezervasyonu consumer ve partner projection'larında ortak ID ile fakat farklı alan yetkisiyle örneklenir.

**Teslimat:** OpenAPI taslağı ve örnek payloadlar; Gateway/consumer contract matrisi

**İlgili kaynaklar:**
  - `TECH-OPENAPI` [OpenAPI Specification v3.1.1: Schema Object](https://spec.openapis.org/oas/v3.1.1.html): OAS 3.1 Schema Object JSON Schema Draft 2020-12 temellidir. format gibi bazı anahtarlar varsayılan vocabulary içinde doğrudan doğrulama değil annotation niteliğindedir.
  - `TECH-AUTHORIZATION` [OWASP Authorization Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Authorization_Cheat_Sheet.html): OWASP varsayılan reddetmeyi ve kaynağı ne olursa olsun her istekte izin doğrulamayı önerir.
  - `TECH-SECURITY-METHOD` [Spring Security: Method Security](https://docs.spring.io/spring-security/reference/servlet/authorization/method-security.html): EnableMethodSecurity, Spring-managed metotlarda PreAuthorize/PostAuthorize kontrollerini etkinleştirir, security starter bunu kendiliğinden açmaz. Metot yetkilendirmesi Spring AOP tabanlıdır.
  - `TECH-SECURITY-JWT` [Spring Security: OAuth 2.0 Resource Server JWT](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html): Resource Server imza, exp, nbf ve yapılandırılmış issuer kontrolü yapar, scope değerlerini SCOPE_ authority olarak eşler. Audience doğrulaması ayrıca yapılandırılabilir.
  - `e1` [Log in to your Expedia Group portal](https://partner.expediagroup.com/en-us/log-in): Resmi portal tanıtımı, otel ortaklarının Partner Central üzerinden rekabet içgörüleri inceleyebileceğini, envanter yönetebileceğini ve fiyatlarını optimize edebileceğini belirtir.
  - `e2` [Welcome | EG Connectivity Hub](https://developers.expediagroup.com/supply/lodging): PMS, channel-manager ve CRS sağlayıcıları için tesis listeleme ve yönetim bağlantıları tanıtılır. Entegrasyon süreci lisans ve PCI incelemesi ile geliştirme, test ve soft launch aşamalarını içerir. Sayfa bireysel tesislerden doğrudan API bağlantısı kabul edilmediğini belirtir.
  - `MAIN-EXPEDIA-API` [Expedia Connectivity: Lodging APIs overview](https://connectivityportal.expediagroup.com/documentation/expedia): Resmi doküman availability/rates, reservation management ve product management sınırlarını ayırır. Product API oda tipi/rate plan yönetimini, review API onaylı yorumlara yanıt ve puan agregasını tanıtır.

#### RM-10: State machine ve arıza senaryolarını tasarla
*Öncelik P0 · Efor L · Sorumlu: Backend / QA · Bağımlılık: RM-08, RM-09 · Karşıladığı gereksinim: R5, R6, R7, R9, R12*

**Amaç:** Ödeme, rezervasyon, yayın ve yorum durumlarının keyfi dropdown değil denetlenebilir iş kuralları olması.

**Nerede:**
- `planning/architecture/state-machines-and-failure-cases.md` (önerilen/yeni)
- `package-booking-saga-orchestrator/src/main/java` (mevcut)

**Nasıl:**
1. Publication, Hold, PaymentAttempt, Order, HotelReservation, Refund ve ReviewModeration için allowed transition/actor/precondition/effect tabloları yaz.
2. Quote→hold→payment→confirm yolunda crash, duplicate webhook, geç payment, expired hold, supplier fail ve timeout satırlarını doldur. Compensate yalnız tamamlanmış gerçek adımlara uygulanır.
3. NO_SHOW ve COMPLETED ayrımı, checkout timezone, cancellation cutoff, refund pending ve manual review gösterimini belirle.
4. Her command için idempotency scope, optimisticVersion, event, retry ve audit etkisini tanımla. Hold TTL ve timeout değerleri capacity/sandbox deneyiyle belirlenecek, şimdi uydurma SLA koyma.

**Sözleşme/kontrat:**
- TransitionSpec(from,to,actor,guard,effect,event,compensation), FailureCase(trigger,expectedStock,expectedMoney,customerState).

**Kabul ölçütü (bu görev uygulandığında koşulacak testler):**
- Payment paid ama booking unconfirmed örneği success olarak gösterilmez.
- En az 12 hata/replay/timeout örneğinde stok ve para beklenen sonucu ayrı belirtilir.
- Hotel-only akış flight/car servisi zorunluluğu taşımayan bir orchestration örneğine sahiptir.

**Teslimat:** State machine diyagramları; Arıza ve compensation matrisi

**İlgili kaynaklar:**
  - `TECH-OUTBOX` [Debezium: Outbox Event Router](https://debezium.io/documentation/reference/stable/transformations/outbox-event-router.html): Connector outbox tablosundaki değişiklikleri yakalar ve outbox event router SMT ile dönüştürür. Doküman dönüşümün yalnızca outbox olaylarına uygulanmasını, birden çok outbox varsa aynı yapıya sahip olmalarını ister.
  - `iyzico-submerchant` [iyzico: Alt Üye](https://docs.iyzico.com/urunler/pazaryeri/pazaryeri-entegrasyonu/alt-uye-olusturma): Onboarding API üç alt üye tipi destekler. Alanlar tipe göre değişir. Yanıttaki subMerchantKey satıcıyı ödeme sırasında tanımlar. IBAN ilgili kişi veya şirket unvanıyla uyumlu olmalıdır ve para gönderimi için onay öncesinde tamamlanmalıdır. Sayfa gerçek bilgiler gönderilmesini açıkça ister.
  - `iyzico-payment` [iyzico: Pazaryeri Ödemesi](https://docs.iyzico.com/urunler/pazaryeri/pazaryeri-entegrasyonu/pazaryeri-odemesi): Pazaryeri ödeme kırılımlarında subMerchantKey ve subMerchantPrice gönderilir. İkinci alan alt üyeye gönderilmesi istenen tutardır. Sayfadaki NON3D örneği tüm yöntemlerde alt üye alanlarının eklenmesi gerektiğini belirtir.
  - `iyzico-approval` [iyzico: Onay](https://docs.iyzico.com/urunler/pazaryeri/pazaryeri-entegrasyonu/onay): Belge, ödeme kırılımı onaylanana kadar paranın korumalı havuz hesapta bekletildiğini açıklar. Kırılım onayı ve onay kaldırma paymentTransactionId üzerinden yapılır. Onaylar kod seviyesinde gönderilir, panelde bu fonksiyon bulunmadığı belirtilir.
  - `iyzico-sandbox` [iyzico: Live vs Sandbox](https://docs.iyzico.com/en/getting-started/preliminaries/live-vs-sandbox): Sandbox gerçek işlem veya fonları etkilemeden ödeme akışlarını simüle eder. Sandbox ve canlı API adresleri ile kimlik bilgileri farklıdır. Test kartları yalnız sandbox senaryoları için sunulur.
  - `iyzico-refund` [iyzico: Refund & Cancel](https://docs.iyzico.com/en/advanced/refund-and-cancel): Standart iade paymentTransactionId ve price kullanır, tam veya kısmi olabilir ve kalan iade edilebilir tutarı aşamaz. Belge çok ürünlü sepetlerde Refund V2 kullanımını önermez. Cancel aynı gün ve tam işlem içindir. Karta yansıma bankaya göre değişir.
  - `iyzico-webhook` [iyzico: Webhook](https://docs.iyzico.com/ek-servisler/webhook): Webhook JSON HTTP POST bildirimi kullanır. HTTPS URL istenir. Belge X-IYZ-SIGNATURE-V3 doğrulamasını açıklar ve önceki sürümlerin desteklenmeyeceğini belirtir. İmza özelliğinin hesapta etkin olması gerekir. 2xx gelmeyen bildirimler için tekrar davranışı belgelenmiştir.
  - `MAIN-APPLE-PAYMENT` [Apple App Review Guidelines: 3.1.3(e)](https://developer.apple.com/app-store/review/guidelines/): 3.1.3(e) uygulama dışında tüketilen fiziksel ürün/hizmetlerin ödemesinde in-app purchase dışındaki yöntemleri belirtir.
  - `TECH-PG-LOCK` [PostgreSQL 16: Explicit Locking](https://www.postgresql.org/docs/16/explicit-locking.html): SELECT FOR UPDATE seçilen satırların rakip güncellenmesini, silinmesini ve çatışan kilitlenmesini transaction sonuna kadar engeller. Normal okumaları engellemez, yüksek izolasyonda değişmiş satırı kilitlemek hata verebilir.

#### RM-11: 83 mevcut görevi yeni MVP'ye eşle ve teslim planını dondur
*Öncelik P0 · Efor M · Sorumlu: Ürün + teknik lider · Bağımlılık: RM-09, RM-10 · Karşıladığı gereksinim: R8, R9, R10, R11*

**Amaç:** Eski tam platform backlog'unu MVP diye körlemesine uygulamamak, çalışmaların sırasını ve kapsamını görünür yapmak.

**Nerede:**
- `Superpro / Waylo / mevcut 31 ana görev ve 83 alt görev` (mevcut)
- `planning/product/backlog-mapping.md` (önerilen/yeni)

**Nasıl:**
1. Her eski Spec-Key'i KEEP-reference, ADAPT veya DEFER diye sınıflandır, yeni RM adımına bağla. Bu roadmap kaydı eski görevleri yeniden yazmaz/silmez/tamamlamaz.
2. Uçuş, araç, boarding/check-in ve loyalty görevlerini otel MVP dışında tut. Generic auth/session/DTO/event/security/test/ops altyapısını uygun hotel scope'una uyarla.
3. Ayrıntılı bağımlılık DAG'si, insan sorumluluğu ve S/M/L/XL göreli efor yaz. Bunlar teslim tarihi veya toplam süre vaadi değildir, sağlayıcı ve ekip kapasitesi belli olunca takvim çıkar.
4. F0 kanıt kapısından sonra ancak kullanıcı geliştirmeye geçilmesini açıkça isterse implementasyon başlayabilir. MVP'yi küçük tutmak security/refund/stock doğruluğunu ertelemek değildir.

**Sözleşme/kontrat:**
- BacklogMapping(oldSpecKey, disposition, roadmapIds, reason); PhaseGate(requiredEvidence, blockers, goNoGo).

**Kabul ölçütü (bu görev uygulandığında koşulacak testler):**
- 83/83 eski Spec-Key sınıflandırılmış ve referans verilen RM-ID'ler mevcut.
- Roadmap'te döngü veya sonra gelen zorunlu bağımlılık yoktur, faz kapıları gözlenebilir.
- Eski task ID, içerik ve sırası korunmuş, roadmap planlandı durumundadır.

**Teslimat:** 83 görev eşleme tablosu; MVP Must/Later ve bağımlılık grafı; Kullanıcıya sunulabilir uygulama başlangıç kapısı

**İlgili kaynaklar:**
  - `TECH-OPENAPI` [OpenAPI Specification v3.1.1: Schema Object](https://spec.openapis.org/oas/v3.1.1.html): OAS 3.1 Schema Object JSON Schema Draft 2020-12 temellidir. format gibi bazı anahtarlar varsayılan vocabulary içinde doğrudan doğrulama değil annotation niteliğindedir.
  - `TECH-TESTCONTAINERS` [Testcontainers for Java: Postgres Module](https://java.testcontainers.org/modules/databases/postgres/): PostgreSQLContainer Java içinden seçilen PostgreSQL image ile geçici veritabanı başlatır, doküman alternatif jdbc:tc bağlantı mekanizmasını da gösterir.

### F2: Ortak temel implementasyonu (kimlik, kiracı, migration)

#### RM-12: Kimlik, oturum ve me sözleşmesi
*Öncelik P0 · Efor L · Sorumlu: Backend Identity Engineer · Bağımlılık: RM-05, RM-09, RM-10 · Karşıladığı gereksinim: R2, R8, R10*

**Amaç:** Tek kullanıcı kimliğini müşteri ve partner yüzeylerinde koruyan, token türü ve oturum yaşam döngüsü açık kimlik temeli kurmak. Mevcut tek role claim'i kuruluş yetkisi sayılmayacak. Consumer mobile geliştirmesine başlamadan gerçek backend üzerinde giriş, yenileme ve iptal kapısı geçilecek.

**Nerede:**
- `auth-service/src/main/java/com/waylo/authservice/config/JwtUtil.java` (mevcut)
- `auth-service/src/main/java/com/waylo/authservice/service/AuthService.java` (mevcut)
- `auth-service/src/main/java/com/waylo/authservice/domain/Session.java` (önerilen/yeni)

**Nasıl:**
1. Access ve refresh için ayrı tokenType, issuer, audience, sessionId ve kısa access ömrü tanımla. Domain servisleri access türünü ve servis hedefini bağımsız doğrulasın, gateway header kimlik yerine geçmesin.
2. Refresh hash ve token ailesini oturumda tut. Rotation yarışını transaction ile koru, eski token yeniden kullanıldığında aileyi iptal et. Çıkış ve güvenlik iptali tüm sonraki yenilemeleri durdursun.
3. Kayıt DTO'sunda role kabul etme. HOTEL_OWNER veya PLATFORM_ADMIN ekleyen payload reddedilsin. USER müşteri kimliğine kontrollü eşlensin, üyelikler token içindeki tek role'den türetilmesin.
4. Me DTO'sunu profil ve aktif oturum bilgisiyle sınırla. Web cookie/CSRF ve mobil bearer taşımasını ayrı belgeleyip loglarda token ve kişisel veriyi maskele.

**Sözleşme/kontrat:**
- MeResponse(userId, customerProfile, sessionId), getMe(actor): GET /api/v1/me
- SessionRefreshRequest(refreshToken), rotateSession(): POST /api/v1/auth/sessions/refresh, hata TOKEN_REUSED veya INVALID_TOKEN

**Kabul ölçütü (bu görev uygulandığında koşulacak testler):**
- Pozitif: giriş sonrası me aynı userId döndürür, refresh yeni token üretir ve eski refresh geçersizleşir.
- Negatif: refresh access yerine, yanlış audience veya iptal edilmiş session ile me çağrısı 401 verir, kullanıcı bilgisi sızmaz.
- E2E: web çıkışı ardından mobil yenileme iptal politikasına göre reddedilir, korelasyonlu audit kaydı görünür.
- Negatif: owner rolü taşıyan kayıt isteği hiçbir ayrıcalık yaratmaz, legacy login erişimi yeni doğrulamayı atlayamaz.

**Teslimat:** Oturum modeli ve ileri migration tasarımı; OpenAPI örnekleri, hata ve token lifecycle matrisi; Gerçek auth API pozitif/negatif entegrasyon test planı

**İlgili kaynaklar:**
  - `TECH-AUTHORIZATION` [OWASP Authorization Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Authorization_Cheat_Sheet.html): OWASP varsayılan reddetmeyi ve kaynağı ne olursa olsun her istekte izin doğrulamayı önerir.
  - `TECH-SECURITY-METHOD` [Spring Security: Method Security](https://docs.spring.io/spring-security/reference/servlet/authorization/method-security.html): EnableMethodSecurity, Spring-managed metotlarda PreAuthorize/PostAuthorize kontrollerini etkinleştirir, security starter bunu kendiliğinden açmaz. Metot yetkilendirmesi Spring AOP tabanlıdır.
  - `TECH-SECURITY-JWT` [Spring Security: OAuth 2.0 Resource Server JWT](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html): Resource Server imza, exp, nbf ve yapılandırılmış issuer kontrolü yapar, scope değerlerini SCOPE_ authority olarak eşler. Audience doğrulaması ayrıca yapılandırılabilir.
  - `TECH-OPENAPI` [OpenAPI Specification v3.1.1: Schema Object](https://spec.openapis.org/oas/v3.1.1.html): OAS 3.1 Schema Object JSON Schema Draft 2020-12 temellidir. format gibi bazı anahtarlar varsayılan vocabulary içinde doğrudan doğrulama değil annotation niteliğindedir.
  - `TECH-MOBILE` [Flutter: Guide to app architecture](https://docs.flutter.dev/app-architecture/guide): Flutter rehberi view/view-model ile UI durumu ve görünümü ayırır, data katmanını repository ve service olarak böler. Repository domain verisinin kaynağıdır ve cache, hata, retry ve refresh davranışlarını yönetir.
  - `MAIN-RN-SECURITY` [React Native: Security](https://reactnative.dev/docs/security): Resmi rehber AsyncStorage'ı şifrelenmeyen store olarak açıklar ve token/secrets için önermediğini belirtir. Client bundle'a server secret koymamak ve platform secure storage seçeneklerini değerlendirmek gerekir.
  - `kvkk-security` [KVKK: Kişisel Veri Güvenliği Rehberi, Teknik ve İdari Tedbirler](https://www.kvkk.gov.tr/yayinlar/veri_guvenligi_rehberi.pdf): Resmi rehber risklere uygun teknik ve idari tedbirleri ele alır. Veri envanteri, erişim politikaları, veri işleyen ilişkileri, bulut, yedekleme ve saklama/imha başlıkları vardır. İhtiyaç duyulmayan verinin güvenli imhası ve veri işleyen güvenliğinin değerlendirilmesi açıklanır.
  - `kvkk-notice-consent` [KVKK: 18.02.2026 tarihli 2026/347 sayılı ilke kararına ilişkin aydınlatma ve açık rıza duyurusu](https://www.kvkk.gov.tr/Icerik/8710/veri-sorumlulari-tarafindan-acik-riza-ve-aydinlatma-metinlerinin-ayri-ayri-duzenlenmesi-gerektigi-hakkinda-kisisel-verileri-koruma-kurulunun-18-02-2026-tarihli-ve-2026-347-sayili-ilke-kararina-iliskin-kamuoyu-duyurusu): Kurum duyurusu aydınlatma ve açık rıza metinlerinin ayrı düzenlenmesini, aydınlatma için onay/rıza istenmemesini ve metinlerin faaliyete uyarlanmasını vurgular. Veri kategorileri, işleme amaçları ve hukuki sebepler açıkça ifade edilmelidir.

#### RM-13: Kuruluş üyeliği ve kapsamlı rol delegasyonu
*Öncelik P0 · Efor XL · Sorumlu: Backend Identity Engineer · Bağımlılık: RM-09, RM-10, RM-12 · Karşıladığı gereksinim: R1, R2, R3, R10*

**Amaç:** Organization, Membership ve RoleAssignment ile aynı kullanıcının farklı işletmelerde farklı yetkiler taşımasını sağlamak. Kuruluş owner'ı ile hotel owner'ı ayrı kapsamlar olacak. Başvuru sahipliği otomatik satış veya owner self-assignment anlamına gelmeyecek, hazır rol paketleri kullanılacak.

**Nerede:**
- `auth-service/src/main/java/com/waylo/authservice/domain/UserRole.java` (mevcut)
- `auth-service/src/main/java/com/waylo/authservice/domain/User.java` (mevcut)
- `auth-service/src/main/java/com/waylo/authservice/partner/OrganizationMembershipService.java` (önerilen/yeni)

**Nasıl:**
1. Aktiflik, permissionVersion, scopeType ve scopeId alanlarını modelle. Kuruluş sahipliği kuruluş üyeliklerini yönetir, HOTEL_OWNER yalnız atanmış tesislerde manager/staff delegasyonu yapabilir.
2. Davet oluşturma ve kabul anında grantable permission ve hotel kümesinin aktör yetkisinin alt kümesi olduğunu tekrar doğrula. Davet token'ını hash, expiry, hedef kimlik ve tek kullanım koşuluyla koru.
3. Son ORGANIZATION_OWNER kaldırılmasını transaction içinde engelle. Owner devrini yeniden kimlik doğrulama ve gerekçeli audit ile ayrı command yap, normal rol güncellemesine gizleme.
4. Kritik mutasyonlarda güncel üyeliği otoriteden doğrula. Üyelik iptali veya permissionVersion değişiminde cache invalidation yayınla, otorite kesintisinde fail-closed davran.

**Sözleşme/kontrat:**
- MembershipResponse, listActiveMemberships(userId): GET /api/v1/me/memberships
- InvitationRequest(roleScopes), inviteMember(actor, orgId, request): POST /api/v1/partner/organizations/{orgId}/invitations
- acceptInvitation(actor, token): POST /api/v1/partner/invitations/{token}/accept

**Kabul ölçütü (bu görev uygulandığında koşulacak testler):**
- Pozitif: aynı kişi A tesisinde owner ve B tesisinde staff olabilir, müşteri profili değişmez.
- Negatif: HOTEL_OWNER kuruluş owner rolü veya kapsam dışı tesis grant edemez, veri tabanında atama oluşmaz.
- Negatif: son kuruluş owner silinmez, tekrar kullanılan ya da yanlış kullanıcıya ait davet reddedilir.
- E2E: üyelik iptalinden sonra halen geçerli access token ile fiyat değiştirme reddedilir, audit aktör ve kapsamı gösterir.

**Teslimat:** Kuruluş/üyelik schema ve rol policy matrisi; Davet ve devir OpenAPI sözleşmeleri; İki kuruluşlu yetki ve eşzamanlı son-owner testleri

**İlgili kaynaklar:**
  - `TECH-TENANT` [OWASP Multi-Tenant Application Security Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Multi_Tenant_Security_Cheat_Sheet.html): İstemciden gelen tenant kimliği yalnızca seçicidir, doğrulanmış kimlik ve güncel üyelikle yetkilendirilmelidir. PostgreSQL tenant bağlamını transaction-local tutmak, havuzdaki bağlantının sonraki isteğe tenant durumunu sızdırmasını önler.
  - `TECH-AUTHORIZATION` [OWASP Authorization Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Authorization_Cheat_Sheet.html): OWASP varsayılan reddetmeyi ve kaynağı ne olursa olsun her istekte izin doğrulamayı önerir.
  - `TECH-SECURITY-METHOD` [Spring Security: Method Security](https://docs.spring.io/spring-security/reference/servlet/authorization/method-security.html): EnableMethodSecurity, Spring-managed metotlarda PreAuthorize/PostAuthorize kontrollerini etkinleştirir, security starter bunu kendiliğinden açmaz. Metot yetkilendirmesi Spring AOP tabanlıdır.
  - `TECH-SECURITY-JWT` [Spring Security: OAuth 2.0 Resource Server JWT](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html): Resource Server imza, exp, nbf ve yapılandırılmış issuer kontrolü yapar, scope değerlerini SCOPE_ authority olarak eşler. Audience doğrulaması ayrıca yapılandırılabilir.
  - `TECH-OPENAPI` [OpenAPI Specification v3.1.1: Schema Object](https://spec.openapis.org/oas/v3.1.1.html): OAS 3.1 Schema Object JSON Schema Draft 2020-12 temellidir. format gibi bazı anahtarlar varsayılan vocabulary içinde doğrudan doğrulama değil annotation niteliğindedir.
  - `TECH-TESTCONTAINERS` [Testcontainers for Java: Postgres Module](https://java.testcontainers.org/modules/databases/postgres/): PostgreSQLContainer Java içinden seçilen PostgreSQL image ile geçici veritabanı başlatır, doküman alternatif jdbc:tc bağlantı mekanizmasını da gösterir.

#### RM-14: Tenant scoped repository, PostgreSQL RLS ve legacy güvenliği
*Öncelik P0 · Efor XL · Sorumlu: Backend Security Engineer · Bağımlılık: RM-05, RM-11, RM-12, RM-13 · Karşıladığı gereksinim: R3, R4, R10*

**Amaç:** Servis, repository ve DB katmanlarında kuruluş izolasyonunu kurmak. Tenant header yalnız seçici olacak, kaynak sahipliğini kanıtlamayacak. Public projection, user-owned sipariş ve tenant-owned otel ayrı politikalar taşıyacak, eski URL veya doğrudan servis çağrısı bypass kapısı olmayacak.

**Nerede:**
- `hotel-service/src/main/java/com/waylo/hotelservice/repository/HotelRepository.java` (mevcut)
- `hotel-service/src/main/java/com/waylo/hotelservice/controller/HotelController.java` (mevcut)
- `api-gateway/src/main/java/com/waylo/apigateway/RequestLoggingFilter.java` (mevcut)
- `hotel-service/src/main/java/com/waylo/hotelservice/security/TenantTransactionContext.java` (önerilen/yeni)

**Nasıl:**
1. Tabloları tenant-owned, user-owned, platform-global ve public projection olarak sınıflandır. Repository lookup'larını organizationId+hotelId+childId ile bağla, scoped FK/unique/index tasarla.
2. Request DB rolünü superuser ve BYPASSRLS olmadan çalıştır. RLS policy ve gereken FORCE RLS davranışını tanımla, doğrulanmış tenant bağlamını yalnız transaction-local kur, public sorguya yapay tenant verme.
3. Gateway ve doğrudan servis girişinde principal/service identity doğrula. Forged header, cache key, export ve exact-object imzalı medya erişimine aynı kaynak policy'sini uygula.
4. Legacy mutasyonları ve bookings/customers/payments/profile hassas GET'lerini yeni policy ile koru. Yalnız eski public hotel katalog GET güvenli yayın projeksiyonuna yönlensin, deprecation kullanım ölçümüyle ilerlesin.

**Sözleşme/kontrat:**
- ScopedHotelQuery(orgId, hotelId), getScopedHotel(actor, query): GET /api/v1/partner/organizations/{orgId}/hotels/{hotelId}
- PublishedHotelResponse, getPublishedHotelDetail(id): GET /api/v1/hotels/{id}

**Kabul ölçütü (bu görev uygulandığında koşulacak testler):**
- Pozitif: A yetkilisi A kaynağını okur, published hotel anonim kullanıcıya yalnız güvenli alanlarla döner.
- Negatif: B hotelId/childId ve forged tenant header GET/PATCH/export/file/cache üzerinde veri veya mutasyon üretmez.
- Pool testi: aynı fiziksel bağlantı A→B→anonim ve exception sonrası yeniden kullanılır, önceki tenant görünmez.
- E2E: gateway atlanarak servis ve legacy hassas GET çağrıldığında yanlış principal reddedilir, public belge veya misafir verisi açılmaz.

**Teslimat:** Repository ve RLS policy tasarımı; Legacy route güvenlik envanteri; PostgreSQL Testcontainers pooling ve izolasyon test paketi

**İlgili kaynaklar:**
  - `TECH-TENANT` [OWASP Multi-Tenant Application Security Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Multi_Tenant_Security_Cheat_Sheet.html): İstemciden gelen tenant kimliği yalnızca seçicidir, doğrulanmış kimlik ve güncel üyelikle yetkilendirilmelidir. PostgreSQL tenant bağlamını transaction-local tutmak, havuzdaki bağlantının sonraki isteğe tenant durumunu sızdırmasını önler.
  - `TECH-PG-RLS` [PostgreSQL 16: Row Security Policies](https://www.postgresql.org/docs/16/ddl-rowsecurity.html): RLS görünür satırları ve yazılabilecek satırları ayrı ifadelerle sınırlayabilir. Superuser ve BYPASSRLS rolleri her zaman, tablo sahibi normalde politikayı aşar, FORCE yalnızca sahibi politikaya tabi kılar.
  - `TECH-AUTHORIZATION` [OWASP Authorization Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Authorization_Cheat_Sheet.html): OWASP varsayılan reddetmeyi ve kaynağı ne olursa olsun her istekte izin doğrulamayı önerir.
  - `TECH-SECURITY-METHOD` [Spring Security: Method Security](https://docs.spring.io/spring-security/reference/servlet/authorization/method-security.html): EnableMethodSecurity, Spring-managed metotlarda PreAuthorize/PostAuthorize kontrollerini etkinleştirir, security starter bunu kendiliğinden açmaz. Metot yetkilendirmesi Spring AOP tabanlıdır.
  - `TECH-SECURITY-JWT` [Spring Security: OAuth 2.0 Resource Server JWT](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html): Resource Server imza, exp, nbf ve yapılandırılmış issuer kontrolü yapar, scope değerlerini SCOPE_ authority olarak eşler. Audience doğrulaması ayrıca yapılandırılabilir.
  - `TECH-TESTCONTAINERS` [Testcontainers for Java: Postgres Module](https://java.testcontainers.org/modules/databases/postgres/): PostgreSQLContainer Java içinden seçilen PostgreSQL image ile geçici veritabanı başlatır, doküman alternatif jdbc:tc bağlantı mekanizmasını da gösterir.
  - `kvkk-security` [KVKK: Kişisel Veri Güvenliği Rehberi, Teknik ve İdari Tedbirler](https://www.kvkk.gov.tr/yayinlar/veri_guvenligi_rehberi.pdf): Resmi rehber risklere uygun teknik ve idari tedbirleri ele alır. Veri envanteri, erişim politikaları, veri işleyen ilişkileri, bulut, yedekleme ve saklama/imha başlıkları vardır. İhtiyaç duyulmayan verinin güvenli imhası ve veri işleyen güvenliğinin değerlendirilmesi açıklanır.
  - `kvkk-notice-consent` [KVKK: 18.02.2026 tarihli 2026/347 sayılı ilke kararına ilişkin aydınlatma ve açık rıza duyurusu](https://www.kvkk.gov.tr/Icerik/8710/veri-sorumlulari-tarafindan-acik-riza-ve-aydinlatma-metinlerinin-ayri-ayri-duzenlenmesi-gerektigi-hakkinda-kisisel-verileri-koruma-kurulunun-18-02-2026-tarihli-ve-2026-347-sayili-ilke-kararina-iliskin-kamuoyu-duyurusu): Kurum duyurusu aydınlatma ve açık rıza metinlerinin ayrı düzenlenmesini, aydınlatma için onay/rıza istenmemesini ve metinlerin faaliyete uyarlanmasını vurgular. Veri kategorileri, işleme amaçları ve hukuki sebepler açıkça ifade edilmelidir.

#### RM-15: Transactional outbox, inbox ve audit altyapısı
*Öncelik P0 · Efor L · Sorumlu: Backend Platform Engineer · Bağımlılık: RM-05, RM-10, RM-14 · Karşıladığı gereksinim: R3, R6, R10, R12*

**Amaç:** Domain değişiklikleri ile olay kaydını aynı yerel transaction'da güvenceye almak, tekrar teslimin iş etkisini çoğaltmasını önlemek ve aktör/gerekçe izini korumak. Kafka exactly-once iş garantisi iddia edilmeyecek. Servisler kendi DB'lerinin sahibi kalacak, cross-DB FK kurulmayacak.

**Nerede:**
- `hotel-service/src/main/java/com/waylo/hotelservice/domain/ProcessedEvent.java` (mevcut)
- `payment-service/src/main/java/com/waylo/paymentservice/event/PaymentEventPublisher.java` (mevcut)
- `hotel-service/src/main/java/com/waylo/hotelservice/event/BaseEvent.java` (mevcut)
- `hotel-service/src/main/java/com/waylo/hotelservice/event/OutboxRelay.java` (önerilen/yeni)

**Nasıl:**
1. Event envelope'a eventId, version, occurredAt, correlationId, causationId, producer ve verified scope ekle. Tenant olayları ile platform order olaylarının izinli consumer kapsamını ayır.
2. Domain mutation, append-only audit ve outbox insert'i tek DB transaction içinde yaz. Relay için retry/backoff, publish attempt ve lease modeli tanımla, broker ACK sonrasında relay çöküşünü hesaba kat.
3. Inbox eventId+consumer unique kaydını business effect ile aynı transaction'a bağla. Idempotency scope+principal+operation+key üzerinden çalışsın, aynı key farklı body için 409 verilsin.
4. DLQ ve replay command'ını açık platform permission, neden ve audit ile sınırla. PII payload minimizasyonu, broker ACL, servis kimliği ve audit retention politikasını belgeleyip ham kart/token loglamayı yasakla.

**Sözleşme/kontrat:**
- EventEnvelope<T>, consumeOnce(envelope, handler), appendAudit(actor, scope, action, reason)
- ReplayRequest(eventId, reason), replayAuthorizedEvent(actor, request): POST /api/v1/admin/events/{eventId}/replay

**Kabul ölçütü (bu görev uygulandığında koşulacak testler):**
- Pozitif: committed mutation bir audit ve teslim edilebilir outbox kaydı oluşturur, rollback hiçbirini bırakmaz.
- Negatif: yinelenen event yalnız bir iş etkisi yaratır, aynı idempotency key farklı body 409 döndürür.
- E2E: publish ACK sonrası process öldürülüp yeniden başlatıldığında consumer tek rezervasyon etkisi üretir.
- Negatif: tenant kullanıcısı platform replay yapamaz, izinli replay gerekçe ve correlationId ile denetlenebilir.

**Teslimat:** Outbox/inbox migration ve event version sözleşmesi; Audit veri minimizasyon ve retention tasarımı; Crash, replay, duplicate ve DLQ entegrasyon senaryoları

**İlgili kaynaklar:**
  - `TECH-OUTBOX` [Debezium: Outbox Event Router](https://debezium.io/documentation/reference/stable/transformations/outbox-event-router.html): Connector outbox tablosundaki değişiklikleri yakalar ve outbox event router SMT ile dönüştürür. Doküman dönüşümün yalnızca outbox olaylarına uygulanmasını, birden çok outbox varsa aynı yapıya sahip olmalarını ister.
  - `TECH-AUTHORIZATION` [OWASP Authorization Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Authorization_Cheat_Sheet.html): OWASP varsayılan reddetmeyi ve kaynağı ne olursa olsun her istekte izin doğrulamayı önerir.
  - `TECH-SECURITY-METHOD` [Spring Security: Method Security](https://docs.spring.io/spring-security/reference/servlet/authorization/method-security.html): EnableMethodSecurity, Spring-managed metotlarda PreAuthorize/PostAuthorize kontrollerini etkinleştirir, security starter bunu kendiliğinden açmaz. Metot yetkilendirmesi Spring AOP tabanlıdır.
  - `TECH-SECURITY-JWT` [Spring Security: OAuth 2.0 Resource Server JWT](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html): Resource Server imza, exp, nbf ve yapılandırılmış issuer kontrolü yapar, scope değerlerini SCOPE_ authority olarak eşler. Audience doğrulaması ayrıca yapılandırılabilir.
  - `TECH-TENANT` [OWASP Multi-Tenant Application Security Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Multi_Tenant_Security_Cheat_Sheet.html): İstemciden gelen tenant kimliği yalnızca seçicidir, doğrulanmış kimlik ve güncel üyelikle yetkilendirilmelidir. PostgreSQL tenant bağlamını transaction-local tutmak, havuzdaki bağlantının sonraki isteğe tenant durumunu sızdırmasını önler.
  - `kvkk-security` [KVKK: Kişisel Veri Güvenliği Rehberi, Teknik ve İdari Tedbirler](https://www.kvkk.gov.tr/yayinlar/veri_guvenligi_rehberi.pdf): Resmi rehber risklere uygun teknik ve idari tedbirleri ele alır. Veri envanteri, erişim politikaları, veri işleyen ilişkileri, bulut, yedekleme ve saklama/imha başlıkları vardır. İhtiyaç duyulmayan verinin güvenli imhası ve veri işleyen güvenliğinin değerlendirilmesi açıklanır.
  - `kvkk-notice-consent` [KVKK: 18.02.2026 tarihli 2026/347 sayılı ilke kararına ilişkin aydınlatma ve açık rıza duyurusu](https://www.kvkk.gov.tr/Icerik/8710/veri-sorumlulari-tarafindan-acik-riza-ve-aydinlatma-metinlerinin-ayri-ayri-duzenlenmesi-gerektigi-hakkinda-kisisel-verileri-koruma-kurulunun-18-02-2026-tarihli-ve-2026-347-sayili-ilke-kararina-iliskin-kamuoyu-duyurusu): Kurum duyurusu aydınlatma ve açık rıza metinlerinin ayrı düzenlenmesini, aydınlatma için onay/rıza istenmemesini ve metinlerin faaliyete uyarlanmasını vurgular. Veri kategorileri, işleme amaçları ve hukuki sebepler açıkça ifade edilmelidir.
  - `TECH-TESTCONTAINERS` [Testcontainers for Java: Postgres Module](https://java.testcontainers.org/modules/databases/postgres/): PostgreSQLContainer Java içinden seçilen PostgreSQL image ile geçici veritabanı başlatır, doküman alternatif jdbc:tc bağlantı mekanizmasını da gösterir.

#### RM-16: Expand-backfill-validate-contract migration ve legacy readback
*Öncelik P0 · Efor XL · Sorumlu: Database Engineer · Bağımlılık: RM-11, RM-13, RM-14, RM-15 · Karşıladığı gereksinim: R3, R9, R10, R12*

**Amaç:** Eski kullanıcı, otel, fiziksel oda ve package booking geçmişini kaybetmeden hedef schema'ya geçiş planını uygulamak. Geçmiş V1 migration dosyaları değişmeyecek. Eski oteller gerçek sahiplik kanıtı olmadan bir kullanıcıya atanmayacak, statik fiyatlar doğrulanmış gelecek stok olarak sunulmayacak.

**Nerede:**
- `hotel-service/src/main/resources/db/migration/V1__init.sql` (mevcut)
- `auth-service/src/main/resources/db/migration/V1__create_users_table.sql` (mevcut)
- `package-booking-service/src/main/java/com/waylo/packagebookingservice/domain/PackageBooking.java` (mevcut)
- `hotel-service/src/main/resources/db/migration/V2__expand_tenant_ownership.sql` (önerilen/yeni)

**Nasıl:**
1. Yeni migration sürümlerini gerçek Flyway geçmişiyle doğrula. organizationId alanını nullable genişlet, auth kuruluş referansını UUID/sözleşme üzerinden taşı, DB-per-service sınırını koru.
2. Legacy quarantine kuruluşuyla sahipsiz otelleri satış dışı tut. Chunk'lı, yeniden başlatılabilir backfill checkpoint ve audit ekle, owner claim yalnız denetimli kanıt incelemesiyle ilerlesin.
3. Room kayıtları ve eski booking ID'lerini koru. RoomType bağını kontrollü dönüştür, statik pricePerNight veya AVAILABLE değerinden uydurma calendar üretme, eski okuma compatibility projection tasarla.
4. Staging kopyasında count/reference/readback doğrula, sonra NOT NULL, scoped constraint ve RLS contract adımını aç. Feature flag cutover, eski reader uyumluluğu ve DB recovery tatbikatını kapı haline getir.

**Sözleşme/kontrat:**
- MigrationReadbackReport(counts, orphanRefs, sampleHashes), validateLegacyReadback()
- LegacyBookingResponse, getOwnedLegacyBooking(actor, id): GET /api/v1/me/legacy-bookings/{id}

**Kabul ölçütü (bu görev uygulandığında koşulacak testler):**
- Pozitif: staging geçişi öncesi/sonrası kullanıcı, oda ve rezervasyon sayıları ile tarihsel ID/readback eşleşir.
- Negatif: orphan veya sahiplik belirsizliğinde contract durur, quarantine otel public satışta görünmez.
- E2E: yarıda kesilen backfill kaldığı checkpoint'ten tekrarlanır, duplicate sahiplik veya kayıt kaybı oluşmaz.
- Recovery: flag kapanışı ve yedekten geri yükleme denenir, hassas legacy booking yalnız sahibine döner.

**Teslimat:** İleri migration sırası ve runbook; Backfill doğrulama/karantina raporu; Recovery ve legacy compatibility kabul kanıtları

**İlgili kaynaklar:**
  - `TECH-PG-RLS` [PostgreSQL 16: Row Security Policies](https://www.postgresql.org/docs/16/ddl-rowsecurity.html): RLS görünür satırları ve yazılabilecek satırları ayrı ifadelerle sınırlayabilir. Superuser ve BYPASSRLS rolleri her zaman, tablo sahibi normalde politikayı aşar, FORCE yalnızca sahibi politikaya tabi kılar.
  - `TECH-TENANT` [OWASP Multi-Tenant Application Security Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Multi_Tenant_Security_Cheat_Sheet.html): İstemciden gelen tenant kimliği yalnızca seçicidir, doğrulanmış kimlik ve güncel üyelikle yetkilendirilmelidir. PostgreSQL tenant bağlamını transaction-local tutmak, havuzdaki bağlantının sonraki isteğe tenant durumunu sızdırmasını önler.
  - `TECH-TESTCONTAINERS` [Testcontainers for Java: Postgres Module](https://java.testcontainers.org/modules/databases/postgres/): PostgreSQLContainer Java içinden seçilen PostgreSQL image ile geçici veritabanı başlatır, doküman alternatif jdbc:tc bağlantı mekanizmasını da gösterir.
  - `TECH-AUTHORIZATION` [OWASP Authorization Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Authorization_Cheat_Sheet.html): OWASP varsayılan reddetmeyi ve kaynağı ne olursa olsun her istekte izin doğrulamayı önerir.
  - `TECH-SECURITY-METHOD` [Spring Security: Method Security](https://docs.spring.io/spring-security/reference/servlet/authorization/method-security.html): EnableMethodSecurity, Spring-managed metotlarda PreAuthorize/PostAuthorize kontrollerini etkinleştirir, security starter bunu kendiliğinden açmaz. Metot yetkilendirmesi Spring AOP tabanlıdır.
  - `TECH-SECURITY-JWT` [Spring Security: OAuth 2.0 Resource Server JWT](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html): Resource Server imza, exp, nbf ve yapılandırılmış issuer kontrolü yapar, scope değerlerini SCOPE_ authority olarak eşler. Audience doğrulaması ayrıca yapılandırılabilir.

### F3: Otel B2B web paneli ve backend

#### RM-17: Partner onboarding ve bağımsız admin onayı
*Öncelik P0 · Efor L · Sorumlu: Full-stack Partner Engineer · Bağımlılık: RM-12, RM-13, RM-14, RM-15, RM-16 · Karşıladığı gereksinim: R1, R2, R3, R4, R8*

**Amaç:** İşletmenin kendi başvurusunu açıp inceleme sonucunu takip etmesini sağlamak. İlk kuruluş başvurusu satış açmaz. Kuruluş onayı ile otel yayın onayı ayrı kararlar olarak kayıtlı kalır. Partner başvuru sahibi platform admin yetkisi veya kendiliğinden HOTEL_OWNER ataması kazanmaz.

**Nerede:**
- `frontend/src/app/register/page.tsx` (mevcut)
- `auth-service/src/main/java/com/waylo/authservice/controller/AuthController.java` (mevcut)
- `frontend/src/app/partner/onboarding/page.tsx` (önerilen/yeni)
- `auth-service/src/main/java/com/waylo/authservice/partner/OrganizationApplicationService.java` (önerilen/yeni)

**Nasıl:**
1. Başvuru taslağı, submit, review, approve/reject durumlarını ve gerekçeyi modelle. Aynı kullanıcının duplicate başvurusunda idempotency ve düzeltme yolunu belirle.
2. Belge erişimini exact-object authorization ile sınırla. Dosya türü/boyutu ve tarama sonucunu kontrol et, PII içeren belgeyi public içerik deposundan ayır.
3. Admin decision command'ını PLATFORM_ADMIN scope, reason ve expectedVersion ile koru. Onay sonrası kuruluş ve sınırlı sahiplik grant'lerini açık policy ile yarat, satış aktivasyonunu yayın kapısına bırak.
4. Onboarding ve admin organizations ekranlarını gerçek API'ye bağla. Loading, validation, rejected reason ve retry durumlarını göster, approval rozetini yayın/satış açık gibi sunma.

**Sözleşme/kontrat:**
- OrganizationApplicationRequest, submitOrganizationApplication(actor, request): POST /api/v1/partner/organization-applications
- ApplicationDecisionRequest(decision, reason, expectedVersion), decideApplication(): POST /api/v1/admin/organization-applications/{id}/decisions

**Kabul ölçütü (bu görev uygulandığında koşulacak testler):**
- Pozitif: giriş yapmış müşteri başvuru oluşturup kendi inceleme durumunu görür.
- Negatif: submitted veya organization-approved kuruluşun yayımlanmamış oteli aramada ve checkout'ta satılamaz.
- Negatif: başvuru sahibi başka başvuruyu/belgeyi göremez, owner rolünü payload üzerinden atayamaz veya admin karar veremez.
- E2E: admin gerekçeli onay/reddeder, ekran güncellenir ve actor/scope/audit kanıtı oluşur.

**Teslimat:** Başvuru ve karar state machine/OpenAPI; Gerçek partner onboarding ve admin inceleme ekranları; Playwright başvuru/onay/izolasyon kabul senaryoları

**İlgili kaynaklar:**
  - `TECH-NEXT-LAYOUT` [Next.js: Layouts and Pages](https://nextjs.org/docs/app/getting-started/layouts-and-pages): App Router layout birden fazla sayfanın ortak UI'ını kapsar, gezinmede durumunu korur ve yeniden render edilmez.
  - `TECH-NEXT-AUTH` [Next.js: Authentication](https://nextjs.org/docs/app/guides/authentication): Partial rendering nedeniyle layout auth kontrolü her route değişiminde çalışmaz. Doküman kontrolleri veri kaynağına yakın DAL katmanında yapmayı önerir, layoutta çocukları gizlemek RSC payload erişimini önlemez.
  - `TECH-AUTHORIZATION` [OWASP Authorization Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Authorization_Cheat_Sheet.html): OWASP varsayılan reddetmeyi ve kaynağı ne olursa olsun her istekte izin doğrulamayı önerir.
  - `TECH-SECURITY-METHOD` [Spring Security: Method Security](https://docs.spring.io/spring-security/reference/servlet/authorization/method-security.html): EnableMethodSecurity, Spring-managed metotlarda PreAuthorize/PostAuthorize kontrollerini etkinleştirir, security starter bunu kendiliğinden açmaz. Metot yetkilendirmesi Spring AOP tabanlıdır.
  - `TECH-SECURITY-JWT` [Spring Security: OAuth 2.0 Resource Server JWT](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html): Resource Server imza, exp, nbf ve yapılandırılmış issuer kontrolü yapar, scope değerlerini SCOPE_ authority olarak eşler. Audience doğrulaması ayrıca yapılandırılabilir.
  - `kvkk-security` [KVKK: Kişisel Veri Güvenliği Rehberi, Teknik ve İdari Tedbirler](https://www.kvkk.gov.tr/yayinlar/veri_guvenligi_rehberi.pdf): Resmi rehber risklere uygun teknik ve idari tedbirleri ele alır. Veri envanteri, erişim politikaları, veri işleyen ilişkileri, bulut, yedekleme ve saklama/imha başlıkları vardır. İhtiyaç duyulmayan verinin güvenli imhası ve veri işleyen güvenliğinin değerlendirilmesi açıklanır.
  - `kvkk-notice-consent` [KVKK: 18.02.2026 tarihli 2026/347 sayılı ilke kararına ilişkin aydınlatma ve açık rıza duyurusu](https://www.kvkk.gov.tr/Icerik/8710/veri-sorumlulari-tarafindan-acik-riza-ve-aydinlatma-metinlerinin-ayri-ayri-duzenlenmesi-gerektigi-hakkinda-kisisel-verileri-koruma-kurulunun-18-02-2026-tarihli-ve-2026-347-sayili-ilke-kararina-iliskin-kamuoyu-duyurusu): Kurum duyurusu aydınlatma ve açık rıza metinlerinin ayrı düzenlenmesini, aydınlatma için onay/rıza istenmemesini ve metinlerin faaliyete uyarlanmasını vurgular. Veri kategorileri, işleme amaçları ve hukuki sebepler açıkça ifade edilmelidir.
  - `TECH-OPENAPI` [OpenAPI Specification v3.1.1: Schema Object](https://spec.openapis.org/oas/v3.1.1.html): OAS 3.1 Schema Object JSON Schema Draft 2020-12 temellidir. format gibi bazı anahtarlar varsayılan vocabulary içinde doğrudan doğrulama değil annotation niteliğindedir.
  - `TECH-PLAYWRIGHT` [Playwright: Authentication](https://playwright.dev/docs/auth): Auth setup browser state dosyasını sonraki testlerde yeniden kullanabilir, dosya hassas cookie/header içerdiğinden repoya konmamalıdır. Paralel testler sunucu durumunu değiştiriyorsa farklı hesaplar gerekir.

#### RM-18: Otel içerik, medya ve güvenli public revision
*Öncelik P0 · Efor XL · Sorumlu: Full-stack Hotel Engineer · Bağımlılık: RM-14, RM-15, RM-16, RM-17 · Karşıladığı gereksinim: R1, R3, R4, R8*

**Amaç:** Partnerin tesis taslağı, içerik ve fotoğraflarını yönetmesini, bağımsız inceleme sonrası yalnız onaylı revision'ın müşteriye açılmasını sağlamak. Taslak düzenleme anında yayınlanmayacak. Askıya alınan otelin yeni satışı kapanırken mevcut rezervasyon erişimi ve operasyon desteği korunacak.

**Nerede:**
- `hotel-service/src/main/java/com/waylo/hotelservice/domain/Hotel.java` (mevcut)
- `hotel-service/src/main/java/com/waylo/hotelservice/controller/HotelController.java` (mevcut)
- `frontend/src/app/hotels/page.tsx` (mevcut)
- `hotel-service/src/main/java/com/waylo/hotelservice/content/HotelContentRevision.java` (önerilen/yeni)

**Nasıl:**
1. DRAFT/SUBMITTED/APPROVED/PUBLISHED/REJECTED/SUSPENDED/ARCHIVED geçişlerini izinli command'larla kur. Content expectedVersion çatışmasını 409 ile bildir, publication request kararını ayrı kaydet.
2. Medya hak kaynağı, sıralama, storage key ve scan durumunu sakla. Upload boyut/tür sınırı ve exact-object signed URL policy uygula, taranmamış dosyayı yayına sokma.
3. Published projection'da güvenli alan allowlist'i kullan. İşletme belgeleri, guest identity ve ticari hesapları dışarıda bırak, yıldız sınıflandırması ile verified review puanını ayır.
4. Partner content ekranı ve admin publication incelemesini bağla. Yeni taslak sırasında eski approved revision görünürlüğünü koru, suspend/archive için yeni satış blokajını domain'e uygula.

**Sözleşme/kontrat:**
- ContentRevisionRequest(expectedVersion, content), updateContentRevision(): PATCH /api/v1/partner/organizations/{orgId}/hotels/{hotelId}/content
- submitPublication(): POST /api/v1/partner/organizations/{orgId}/hotels/{hotelId}/publication-requests
- PublishedHotelResponse, getPublishedHotelDetail(id): GET /api/v1/hotels/{id}

**Kabul ölçütü (bu görev uygulandığında koşulacak testler):**
- Pozitif: admin yayın kararı sonrası public detay doğru revision ve güvenli alanlarla açılır.
- Negatif: taslak değişiklik, taranmamış medya ve özel belge public yanıtta görünmez.
- Negatif: başka otelin medya key'i veya childId ile içerik değiştirilemez, eski version 409 verir.
- E2E: published içerik düzenlenip reddedildiğinde önceki revision korunur, suspension yeni satışı kapatır.

**Teslimat:** Revision/public projection modelleri; İçerik-medya ve yayın inceleme UI/API; Publication ve medya erişim test raporu

**İlgili kaynaklar:**
  - `TECH-NEXT-LAYOUT` [Next.js: Layouts and Pages](https://nextjs.org/docs/app/getting-started/layouts-and-pages): App Router layout birden fazla sayfanın ortak UI'ını kapsar, gezinmede durumunu korur ve yeniden render edilmez.
  - `TECH-NEXT-AUTH` [Next.js: Authentication](https://nextjs.org/docs/app/guides/authentication): Partial rendering nedeniyle layout auth kontrolü her route değişiminde çalışmaz. Doküman kontrolleri veri kaynağına yakın DAL katmanında yapmayı önerir, layoutta çocukları gizlemek RSC payload erişimini önlemez.
  - `TECH-AUTHORIZATION` [OWASP Authorization Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Authorization_Cheat_Sheet.html): OWASP varsayılan reddetmeyi ve kaynağı ne olursa olsun her istekte izin doğrulamayı önerir.
  - `TECH-SECURITY-METHOD` [Spring Security: Method Security](https://docs.spring.io/spring-security/reference/servlet/authorization/method-security.html): EnableMethodSecurity, Spring-managed metotlarda PreAuthorize/PostAuthorize kontrollerini etkinleştirir, security starter bunu kendiliğinden açmaz. Metot yetkilendirmesi Spring AOP tabanlıdır.
  - `TECH-SECURITY-JWT` [Spring Security: OAuth 2.0 Resource Server JWT](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html): Resource Server imza, exp, nbf ve yapılandırılmış issuer kontrolü yapar, scope değerlerini SCOPE_ authority olarak eşler. Audience doğrulaması ayrıca yapılandırılabilir.
  - `kvkk-security` [KVKK: Kişisel Veri Güvenliği Rehberi, Teknik ve İdari Tedbirler](https://www.kvkk.gov.tr/yayinlar/veri_guvenligi_rehberi.pdf): Resmi rehber risklere uygun teknik ve idari tedbirleri ele alır. Veri envanteri, erişim politikaları, veri işleyen ilişkileri, bulut, yedekleme ve saklama/imha başlıkları vardır. İhtiyaç duyulmayan verinin güvenli imhası ve veri işleyen güvenliğinin değerlendirilmesi açıklanır.
  - `kvkk-notice-consent` [KVKK: 18.02.2026 tarihli 2026/347 sayılı ilke kararına ilişkin aydınlatma ve açık rıza duyurusu](https://www.kvkk.gov.tr/Icerik/8710/veri-sorumlulari-tarafindan-acik-riza-ve-aydinlatma-metinlerinin-ayri-ayri-duzenlenmesi-gerektigi-hakkinda-kisisel-verileri-koruma-kurulunun-18-02-2026-tarihli-ve-2026-347-sayili-ilke-kararina-iliskin-kamuoyu-duyurusu): Kurum duyurusu aydınlatma ve açık rıza metinlerinin ayrı düzenlenmesini, aydınlatma için onay/rıza istenmemesini ve metinlerin faaliyete uyarlanmasını vurgular. Veri kategorileri, işleme amaçları ve hukuki sebepler açıkça ifade edilmelidir.
  - `TECH-OPENAPI` [OpenAPI Specification v3.1.1: Schema Object](https://spec.openapis.org/oas/v3.1.1.html): OAS 3.1 Schema Object JSON Schema Draft 2020-12 temellidir. format gibi bazı anahtarlar varsayılan vocabulary içinde doğrudan doğrulama değil annotation niteliğindedir.
  - `TECH-PLAYWRIGHT` [Playwright: Authentication](https://playwright.dev/docs/auth): Auth setup browser state dosyasını sonraki testlerde yeniden kullanabilir, dosya hassas cookie/header içerdiğinden repoya konmamalıdır. Paralel testler sunucu durumunu değiştiriyorsa farklı hesaplar gerekir.

#### RM-19: Oda tipi ve rate plan satış modeli
*Öncelik P0 · Efor L · Sorumlu: Full-stack Hotel Engineer · Bağımlılık: RM-14, RM-16, RM-18 · Karşıladığı gereksinim: R3, R5, R8, R9, R10*

**Amaç:** Fiziksel oda enum'undan bağımsız işletmeye özgü RoomType ve ticari RatePlan modeli kurmak. Kapasite, occupancy, yemek, ödeme ve iptal koşulları açık olacaktır. Rate plan başına stok yaratılmayacak, aynı oda tipinin tüm planları ortak inventory kullanacak.

**Nerede:**
- `hotel-service/src/main/java/com/waylo/hotelservice/domain/RoomType.java` (mevcut)
- `hotel-service/src/main/java/com/waylo/hotelservice/domain/Room.java` (mevcut)
- `hotel-service/src/main/java/com/waylo/hotelservice/domain/SellableRoomType.java` (önerilen/yeni)
- `frontend/src/app/partner/hotels/[hotelId]/room-types/page.tsx` (önerilen/yeni)

**Nasıl:**
1. Mevcut RoomType enum'u ile yeni entity isim çakışmasını kontrollü çöz. PhysicalRoom kayıtlarını koru, yeni sellable tip için kuruluş+otel ilişkisini zorunlu yap.
2. Adult/child kapasitesi ve çocuk yaş kurallarını validation ile tanımla. Fiziksel oda ataması satış ön şartı olmasın, arşivlenen tip geçmiş rezervasyonu silmesin.
3. RatePlan'a cancellation/payment/meal policy version ve aktiflik ekle. Stok ilişkisi yalnız roomType düzeyinde kalsın, plan bazlı fiyat/restriction ayrı tablo ve DTO ile yönetilsin.
4. Gerçek oda tipi/rate plan web formlarını scoped API'ye bağla. Yetkisiz staff düzenlemesini backend reddetsin, referans verilen planın destructive delete yerine archive yolu olsun.

**Sözleşme/kontrat:**
- RoomTypeRequest(capacity, childRules, amenities), createRoomType(): POST /api/v1/partner/organizations/{orgId}/hotels/{hotelId}/room-types
- RatePlanRequest(roomTypeId, policies, meal), createRatePlan(): POST /api/v1/partner/organizations/{orgId}/hotels/{hotelId}/rate-plans

**Kabul ölçütü (bu görev uygulandığında koşulacak testler):**
- Pozitif: owner iki farklı rate planı aynı oda tipine bağlar, UI ve API policy version'larını gösterir.
- Negatif: aşırı occupancy ve başka otele ait roomTypeId reddedilir, orphan plan oluşmaz.
- Invariant: iki rate plan oluşturulduğunda inventory kapasitesi çoğalmaz, ortak stok referansı aynıdır.
- E2E: mevcut PhysicalRoom ve tarihsel kayıt okunur, tip arşivleme geçmiş ticari referansı bozmaz.

**Teslimat:** Sellable room type/rate plan schema ve DTO; Scoped yönetim ekranları; Occupancy, ortak stok ve legacy oda testleri

**İlgili kaynaklar:**
  - `MAIN-BOOKING-ARI` [Booking.com: Create or update inventory, rates and restrictions](https://developers.booking.com/connectivity/docs/b_xml-availability): Resmi örnek stok sayısını oda tipi seviyesinde bütün rate planlar için ortak tutar. Fiyat ve kısıtlar oda tipi/rate plan/tarih boyutlarıyla güncellenir, tarih aralığının son günü dahil değildir.
  - `e1` [Log in to your Expedia Group portal](https://partner.expediagroup.com/en-us/log-in): Resmi portal tanıtımı, otel ortaklarının Partner Central üzerinden rekabet içgörüleri inceleyebileceğini, envanter yönetebileceğini ve fiyatlarını optimize edebileceğini belirtir.
  - `e2` [Welcome | EG Connectivity Hub](https://developers.expediagroup.com/supply/lodging): PMS, channel-manager ve CRS sağlayıcıları için tesis listeleme ve yönetim bağlantıları tanıtılır. Entegrasyon süreci lisans ve PCI incelemesi ile geliştirme, test ve soft launch aşamalarını içerir. Sayfa bireysel tesislerden doğrudan API bağlantısı kabul edilmediğini belirtir.
  - `MAIN-EXPEDIA-API` [Expedia Connectivity: Lodging APIs overview](https://connectivityportal.expediagroup.com/documentation/expedia): Resmi doküman availability/rates, reservation management ve product management sınırlarını ayırır. Product API oda tipi/rate plan yönetimini, review API onaylı yorumlara yanıt ve puan agregasını tanıtır.
  - `TECH-OPENAPI` [OpenAPI Specification v3.1.1: Schema Object](https://spec.openapis.org/oas/v3.1.1.html): OAS 3.1 Schema Object JSON Schema Draft 2020-12 temellidir. format gibi bazı anahtarlar varsayılan vocabulary içinde doğrudan doğrulama değil annotation niteliğindedir.
  - `TECH-NEXT-LAYOUT` [Next.js: Layouts and Pages](https://nextjs.org/docs/app/getting-started/layouts-and-pages): App Router layout birden fazla sayfanın ortak UI'ını kapsar, gezinmede durumunu korur ve yeniden render edilmez.
  - `TECH-NEXT-AUTH` [Next.js: Authentication](https://nextjs.org/docs/app/guides/authentication): Partial rendering nedeniyle layout auth kontrolü her route değişiminde çalışmaz. Doküman kontrolleri veri kaynağına yakın DAL katmanında yapmayı önerir, layoutta çocukları gizlemek RSC payload erişimini önlemez.
  - `TECH-TESTCONTAINERS` [Testcontainers for Java: Postgres Module](https://java.testcontainers.org/modules/databases/postgres/): PostgreSQLContainer Java içinden seçilen PostgreSQL image ile geçici veritabanı başlatır, doküman alternatif jdbc:tc bağlantı mekanizmasını da gösterir.

#### RM-20: Günlük takvim, inventory ve fiyat yönetimi
*Öncelik P0 · Efor XL · Sorumlu: Full-stack Inventory Engineer · Bağımlılık: RM-14, RM-15, RM-19 · Karşıladığı gereksinim: R3, R5, R8*

**Amaç:** Tesis yerel tarihleriyle günlük kapasite, out-of-service, fiyat ve satış kısıtlarını gerçek grid üzerinden yönetmek. Bu aşama takvim ve kapasite ledger'ını teslim eder, son oda hold yarışı RM-24'te uygulanır. Gelecek held/confirmed invariant'ları schema ve güncelleme policy'sinde şimdiden korunur.

**Nerede:**
- `hotel-service/src/main/java/com/waylo/hotelservice/service/RoomServiceImpl.java` (mevcut)
- `hotel-service/src/main/java/com/waylo/hotelservice/domain/DailyInventory.java` (önerilen/yeni)
- `frontend/src/app/partner/hotels/[hotelId]/calendar/page.tsx` (önerilen/yeni)

**Nasıl:**
1. DailyInventory key'ini hotel+roomType+stayDate yap, available=allotment-outOfService-held-confirmed>=0 invariant'ını uygula. outOfService toplam allotment içinden düşer, ikinci kez kapasite azaltma yapılmaz.
2. DailyRate'i roomType+ratePlan+date+occupancyBand ile ayır. Decimal/minor-unit para ve currency kullan, taxPolicyVersion sakla, eksik fiyatı bilinmiyor olarak döndür.
3. Stop-sell, min/max stay, arrival/departure ve booking-window kurallarını ekle. [checkIn,checkOut) ve hotel timezone semantiğini kontrata yaz, checkout gecesini fiyatlandırma.
4. Bulk calendar update'i expectedVersions ve adjustment reason ile atomik uygula. allotment-outOfService held+confirmed altına inmesin, rate planlar aynı stok satırına baksın, UI çatışmada yenileme istesin.

**Sözleşme/kontrat:**
- CalendarResponse(days, inventories, rates, restrictions), getCalendar(): GET /api/v1/partner/organizations/{orgId}/hotels/{hotelId}/calendar
- CalendarUpdateRequest(expectedVersions, changes, reason), updateCalendar(): PUT /api/v1/partner/organizations/{orgId}/hotels/{hotelId}/calendar

**Kabul ölçütü (bu görev uygulandığında koşulacak testler):**
- Pozitif: owner tarih grid'inde fiyat/stok değiştirir, yeniden GET aynı version ve değerleri döndürür.
- Negatif: eski version, negatif available veya rezerveli kapasitenin altına indirme 409/422 verir, hiçbir gün kısmi değişmez.
- Invariant: iki rate plan ortak stok okur, allotment 10 ve outOfService 2 iken held/confirmed sıfırsa available 8 olur.
- E2E: timezone sınırında checkout günü satılmaz, eksik fiyat ve stop-sell satışa uygun gösterilmez.

**Teslimat:** Inventory/rate/restriction schema ve güncelleme API; Gerçek calendar grid ve audit adjustment; Bulk conflict, tarih ve kapasite test planı

**İlgili kaynaklar:**
  - `MAIN-BOOKING-ARI` [Booking.com: Create or update inventory, rates and restrictions](https://developers.booking.com/connectivity/docs/b_xml-availability): Resmi örnek stok sayısını oda tipi seviyesinde bütün rate planlar için ortak tutar. Fiyat ve kısıtlar oda tipi/rate plan/tarih boyutlarıyla güncellenir, tarih aralığının son günü dahil değildir.
  - `TECH-PG-LOCK` [PostgreSQL 16: Explicit Locking](https://www.postgresql.org/docs/16/explicit-locking.html): SELECT FOR UPDATE seçilen satırların rakip güncellenmesini, silinmesini ve çatışan kilitlenmesini transaction sonuna kadar engeller. Normal okumaları engellemez, yüksek izolasyonda değişmiş satırı kilitlemek hata verebilir.
  - `TECH-OPENAPI` [OpenAPI Specification v3.1.1: Schema Object](https://spec.openapis.org/oas/v3.1.1.html): OAS 3.1 Schema Object JSON Schema Draft 2020-12 temellidir. format gibi bazı anahtarlar varsayılan vocabulary içinde doğrudan doğrulama değil annotation niteliğindedir.
  - `TECH-NEXT-LAYOUT` [Next.js: Layouts and Pages](https://nextjs.org/docs/app/getting-started/layouts-and-pages): App Router layout birden fazla sayfanın ortak UI'ını kapsar, gezinmede durumunu korur ve yeniden render edilmez.
  - `TECH-NEXT-AUTH` [Next.js: Authentication](https://nextjs.org/docs/app/guides/authentication): Partial rendering nedeniyle layout auth kontrolü her route değişiminde çalışmaz. Doküman kontrolleri veri kaynağına yakın DAL katmanında yapmayı önerir, layoutta çocukları gizlemek RSC payload erişimini önlemez.
  - `TECH-TESTCONTAINERS` [Testcontainers for Java: Postgres Module](https://java.testcontainers.org/modules/databases/postgres/): PostgreSQLContainer Java içinden seçilen PostgreSQL image ile geçici veritabanı başlatır, doküman alternatif jdbc:tc bağlantı mekanizmasını da gösterir.

#### RM-21: Partner layout, tenant seçimi ve ekip UI
*Öncelik P0 · Efor L · Sorumlu: Frontend Partner Engineer · Bağımlılık: RM-12, RM-13, RM-14, RM-17, RM-18, RM-19, RM-20 · Karşıladığı gereksinim: R2, R3, R4, R8*

**Amaç:** Partner, platform admin ve tüketici yüzeylerini Next.js route/layout sınırlarıyla ayırmak. Partnerin doğru kuruluş ve tesisi seçip ekip yetkilerini yönetmesini sağlamak. Sidebar gizleme güvenlik kontrolü yerine geçmeyecek, her işlem backend policy ile doğrulanacak ve F3 kapısı mobil geliştirmeden önce geçilecek.

**Nerede:**
- `frontend/src/app/layout.tsx` (mevcut)
- `frontend/src/app/login/page.tsx` (mevcut)
- `frontend/src/app/partner/layout.tsx` (önerilen/yeni)
- `frontend/src/app/partner/team/page.tsx` (önerilen/yeni)

**Nasıl:**
1. Partner ve admin layout guard'larını me/memberships ile kur, server rendering ve client cache key'lerinde scope taşı. Yetkisiz kullanıcı doğrudan URL açtığında güvenli reddetme göster.
2. Kuruluş/tesis switcher'ını yalnız aktif membership kapsamından üret. Scope değişiminde önceki kuruluş sorgularını iptal et ve cache temizle, stale ekran üzerinden mutasyona izin verme.
3. Team list, davet, rol/scope değişimi ve revoke UI'sını gerçek API'ye bağla. HOTEL_OWNER'a yalnız grantable hotel manager/staff seçenekleri göster, ORGANIZATION_OWNER işlemleri ayrı kalır.
4. Loading, empty, forbidden, offline ve version conflict durumlarını erişilebilir tasarla. Owner devir yeniden auth ve son-owner reddi için açık akış sağla, müşteri profilini ayrı kimliğe dönüştürme.

**Sözleşme/kontrat:**
- MembershipListResponse, listActiveMemberships(): GET /api/v1/me/memberships
- revokeMembership(actor, orgId, membershipId): DELETE /api/v1/partner/organizations/{orgId}/memberships/{membershipId}
- RoleScopeUpdateRequest(expectedVersion, assignments), updateMemberScopes(): PATCH /api/v1/partner/organizations/{orgId}/memberships/{membershipId}/role-scopes

**Kabul ölçütü (bu görev uygulandığında koşulacak testler):**
- Pozitif: A owner/B staff kullanıcısı switcher üzerinden yalnız kendisine açık menü ve kaynakları görür.
- Negatif: yanlış rolle admin URL ve doğrudan team API isteği reddedilir, UI gizlemesi bypass edilemez.
- E2E: davet kabulü ve revoke gerçek backend'de görünür, tenant geçişinde A cache verisi B ekranına taşınmaz.
- Negatif: owner kendi kapsamından geniş atama yapamaz, son kuruluş owner kaldırma hatası ekranda açıkça gösterilir.

**Teslimat:** Partner/admin layout ve tenant switcher; Gerçek ekip yönetimi UI; Playwright rol, deep-link ve cache geçiş testleri

**İlgili kaynaklar:**
  - `TECH-NEXT-LAYOUT` [Next.js: Layouts and Pages](https://nextjs.org/docs/app/getting-started/layouts-and-pages): App Router layout birden fazla sayfanın ortak UI'ını kapsar, gezinmede durumunu korur ve yeniden render edilmez.
  - `TECH-NEXT-AUTH` [Next.js: Authentication](https://nextjs.org/docs/app/guides/authentication): Partial rendering nedeniyle layout auth kontrolü her route değişiminde çalışmaz. Doküman kontrolleri veri kaynağına yakın DAL katmanında yapmayı önerir, layoutta çocukları gizlemek RSC payload erişimini önlemez.
  - `TECH-TENANT` [OWASP Multi-Tenant Application Security Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Multi_Tenant_Security_Cheat_Sheet.html): İstemciden gelen tenant kimliği yalnızca seçicidir, doğrulanmış kimlik ve güncel üyelikle yetkilendirilmelidir. PostgreSQL tenant bağlamını transaction-local tutmak, havuzdaki bağlantının sonraki isteğe tenant durumunu sızdırmasını önler.
  - `TECH-AUTHORIZATION` [OWASP Authorization Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Authorization_Cheat_Sheet.html): OWASP varsayılan reddetmeyi ve kaynağı ne olursa olsun her istekte izin doğrulamayı önerir.
  - `TECH-SECURITY-METHOD` [Spring Security: Method Security](https://docs.spring.io/spring-security/reference/servlet/authorization/method-security.html): EnableMethodSecurity, Spring-managed metotlarda PreAuthorize/PostAuthorize kontrollerini etkinleştirir, security starter bunu kendiliğinden açmaz. Metot yetkilendirmesi Spring AOP tabanlıdır.
  - `TECH-SECURITY-JWT` [Spring Security: OAuth 2.0 Resource Server JWT](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html): Resource Server imza, exp, nbf ve yapılandırılmış issuer kontrolü yapar, scope değerlerini SCOPE_ authority olarak eşler. Audience doğrulaması ayrıca yapılandırılabilir.
  - `TECH-PLAYWRIGHT` [Playwright: Authentication](https://playwright.dev/docs/auth): Auth setup browser state dosyasını sonraki testlerde yeniden kullanabilir, dosya hassas cookie/header içerdiğinden repoya konmamalıdır. Paralel testler sunucu durumunu değiştiriyorsa farklı hesaplar gerekir.
  - `TECH-OPENAPI` [OpenAPI Specification v3.1.1: Schema Object](https://spec.openapis.org/oas/v3.1.1.html): OAS 3.1 Schema Object JSON Schema Draft 2020-12 temellidir. format gibi bazı anahtarlar varsayılan vocabulary içinde doğrudan doğrulama değil annotation niteliğindedir.

#### RM-22: Partner rezervasyon panel altyapısı ve gerçek katalog KPI
*Öncelik P1 · Efor M · Sorumlu: Full-stack Partner Engineer · Bağımlılık: RM-14, RM-18, RM-19, RM-20, RM-21 · Karşıladığı gereksinim: R3, R4, R5, R8*

**Amaç:** Rezervasyon panelinin güvenli layout, filtre, tablo ve durum altyapısını hazırlamak, yalnız mevcut gerçek katalog completeness ve stok KPI'larını sunmak. Rezervasyon backend'i henüz yokken rezervasyon ve gelir KPI'ları bekleniyor/unavailable olarak gösterilecek, sahte sıfır veya seed başarı kullanılmayacak.

**Nerede:**
- `frontend/src/app/bookings/page.tsx` (mevcut)
- `frontend/src/app/page.tsx` (mevcut)
- `frontend/src/app/partner/hotels/[hotelId]/reservations/page.tsx` (önerilen/yeni)
- `hotel-service/src/main/java/com/waylo/hotelservice/partner/HotelDashboardQueryService.java` (önerilen/yeni)

**Nasıl:**
1. Scoped dashboard DTO'sunda metric value, availability state, computedAt ve source tanımla. Published revision completeness, eksik calendar günleri ve satışa kapalı oda tipi gibi ölçümleri gerçek DB'den üret.
2. Rezervasyon list/detail bileşenlerinin pagination, tarih filtresi, loading ve unavailable state'lerini kur. RM-25 tesliminde orderitem→partner reservation readprojection binding'inin aynı paneli tamamlayacağını belgeleyip ileri bağımlılık ekleme.
3. Gelir/rezervasyon KPI'sını backend hazır değilken null+PENDING_BACKEND göster. RM-31 finans ledger kaynağı bağlanana kadar tahmini tutarı tahsilat diye sunma.
4. Query cache, export kontrolü ve scoped view policy uygula. F3 B2B kapısında onboarding→content→calendar→team akışını gerçek backend ile dene, henüz booking yapılabildiği iddiasında bulunma.

**Sözleşme/kontrat:**
- HotelDashboardResponse(catalogMetrics, inventoryMetrics, reservationState, financeState), getDashboard(): GET /api/v1/partner/organizations/{orgId}/hotels/{hotelId}/dashboard

**Kabul ölçütü (bu görev uygulandığında koşulacak testler):**
- Pozitif: eksik fiyatlı gün eklenince gerçek completeness KPI değişir, yeniden GET aynı hesabı doğrular.
- Negatif: rezervasyon backend'i yokken sayı/tutar 0 gösterilmez, bekleniyor durumu ve kaynak açıklaması görünür.
- Negatif: başka kuruluş hotelId ile dashboard veya export denenince veri dönmez.
- E2E: partner içerik ve takvim değişikliği sonrası KPI yenilenir, rezervasyon alanı açık limitation ile kullanılabilir panel altyapısında kalır.

**Teslimat:** Scoped dashboard query ve gerçek catalog KPI; Rezervasyon panel bileşenleri ve unavailable UX; RM-25/RM-31 binding handoff ve F3 kapı kanıtı

**İlgili kaynaklar:**
  - `TECH-NEXT-LAYOUT` [Next.js: Layouts and Pages](https://nextjs.org/docs/app/getting-started/layouts-and-pages): App Router layout birden fazla sayfanın ortak UI'ını kapsar, gezinmede durumunu korur ve yeniden render edilmez.
  - `TECH-NEXT-AUTH` [Next.js: Authentication](https://nextjs.org/docs/app/guides/authentication): Partial rendering nedeniyle layout auth kontrolü her route değişiminde çalışmaz. Doküman kontrolleri veri kaynağına yakın DAL katmanında yapmayı önerir, layoutta çocukları gizlemek RSC payload erişimini önlemez.
  - `TECH-TENANT` [OWASP Multi-Tenant Application Security Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Multi_Tenant_Security_Cheat_Sheet.html): İstemciden gelen tenant kimliği yalnızca seçicidir, doğrulanmış kimlik ve güncel üyelikle yetkilendirilmelidir. PostgreSQL tenant bağlamını transaction-local tutmak, havuzdaki bağlantının sonraki isteğe tenant durumunu sızdırmasını önler.
  - `TECH-PLAYWRIGHT` [Playwright: Authentication](https://playwright.dev/docs/auth): Auth setup browser state dosyasını sonraki testlerde yeniden kullanabilir, dosya hassas cookie/header içerdiğinden repoya konmamalıdır. Paralel testler sunucu durumunu değiştiriyorsa farklı hesaplar gerekir.
  - `TECH-OPENAPI` [OpenAPI Specification v3.1.1: Schema Object](https://spec.openapis.org/oas/v3.1.1.html): OAS 3.1 Schema Object JSON Schema Draft 2020-12 temellidir. format gibi bazı anahtarlar varsayılan vocabulary içinde doğrudan doğrulama değil annotation niteliğindedir.

### F4: Otel transaction backend (quote/order/payment/review)

#### RM-23: Customer search, availability ve immutable quote
*Öncelik P0 · Efor L · Sorumlu: Backend Commerce Engineer · Bağımlılık: RM-12, RM-18, RM-19, RM-20, RM-22 · Karşıladığı gereksinim: R5, R6, R8, R9*

**Amaç:** Yayınlanmış otel arama/detay/müsaitlik ve sunucu fiyatına dayalı quote üretmek. Consumer mobile'a taşınacak kontrat gerçek backend ile doğrulanacak, iş kuralı mobilde tekrar yazılmayacak. F2/F3 B2B kapıları tamamlanmadan mobil uygulama geliştirmesi başlamayacak, API harness mobil kabulünün yerine geçmeyecek.

**Nerede:**
- `pricing-service/src/main/java/com/waylo/pricingservice/service/PricingRuleServiceImpl.java` (mevcut)
- `hotel-service/src/main/java/com/waylo/hotelservice/service/HotelServiceImpl.java` (mevcut)
- `pricing-service/src/main/java/com/waylo/pricingservice/quote/QuoteService.java` (önerilen/yeni)

**Nasıl:**
1. Public query'yi published revision ve satış durumu üzerinden üret. Stay/occupancy/childrenAges, pagination ve timezone doğrulaması yap, private alanları public DTO'ya ekleme.
2. Hotel domain'inden tarihli fiyat/restriction ve shared inventory al. Eksik gün, stop-sell veya kapasite aşımında teklif verme, cache'i nihai stok otoritesi sayma.
3. Quote içinde amount/currency, tax/fee/discount/commission, policy version ve expiry snapshot'ını dondur. İstemci total'ını kabul etme, fiyatı yıldız veya rastgele katsayıdan türetme.
4. RevalidateOffer typed port'u kur ve OpenAPI consumer/provider testleri yaz. Mobil arama/checkout istemcisine örnek DTO ve hata semantiği teslim et, supplier adapter sözleşmesini direct inventory uygulamasından ayır.

**Sözleşme/kontrat:**
- SearchCriteria, searchPublishedHotels(): GET /api/v1/hotels?checkIn=&checkOut=&adults=&childrenAges=
- AvailableOffersResponse, getAvailableOffers(): GET /api/v1/hotels/{id}/availability
- QuoteRequest(hotelOffer), QuoteResponse(quoteId, expiresAt, money, snapshot), createQuote(): POST /api/v1/quotes

**Kabul ölçütü (bu görev uygulandığında koşulacak testler):**
- Pozitif: geçerli stay/occupancy için tüm gecelerin fiyatı ve koşullarıyla immutable quote oluşur.
- Negatif: unpublished/suspended otel, eksik fiyat veya kapasite aşımı teklif yaratmaz, private alan açılmaz.
- Negatif: değiştirilmiş client total yok sayılır veya reddedilir, farklı currency karışımı kabul edilmez.
- E2E: partner calendar güncellemesi yeni quote'u değiştirir, eski quote snapshot'ı değişmez ve expiry hatası tanımlıdır.

**Teslimat:** Public search/availability ve quote API; Immutable quote schema ve typed offer port; Gerçek backend harness ve mobil kontrat örnekleri

**İlgili kaynaklar:**
  - `TECH-MOBILE` [Flutter: Guide to app architecture](https://docs.flutter.dev/app-architecture/guide): Flutter rehberi view/view-model ile UI durumu ve görünümü ayırır, data katmanını repository ve service olarak böler. Repository domain verisinin kaynağıdır ve cache, hata, retry ve refresh davranışlarını yönetir.
  - `MAIN-RN-SECURITY` [React Native: Security](https://reactnative.dev/docs/security): Resmi rehber AsyncStorage'ı şifrelenmeyen store olarak açıklar ve token/secrets için önermediğini belirtir. Client bundle'a server secret koymamak ve platform secure storage seçeneklerini değerlendirmek gerekir.
  - `TECH-OPENAPI` [OpenAPI Specification v3.1.1: Schema Object](https://spec.openapis.org/oas/v3.1.1.html): OAS 3.1 Schema Object JSON Schema Draft 2020-12 temellidir. format gibi bazı anahtarlar varsayılan vocabulary içinde doğrudan doğrulama değil annotation niteliğindedir.
  - `MAIN-BOOKING-ARI` [Booking.com: Create or update inventory, rates and restrictions](https://developers.booking.com/connectivity/docs/b_xml-availability): Resmi örnek stok sayısını oda tipi seviyesinde bütün rate planlar için ortak tutar. Fiyat ve kısıtlar oda tipi/rate plan/tarih boyutlarıyla güncellenir, tarih aralığının son günü dahil değildir.
  - `e1` [Log in to your Expedia Group portal](https://partner.expediagroup.com/en-us/log-in): Resmi portal tanıtımı, otel ortaklarının Partner Central üzerinden rekabet içgörüleri inceleyebileceğini, envanter yönetebileceğini ve fiyatlarını optimize edebileceğini belirtir.
  - `e2` [Welcome | EG Connectivity Hub](https://developers.expediagroup.com/supply/lodging): PMS, channel-manager ve CRS sağlayıcıları için tesis listeleme ve yönetim bağlantıları tanıtılır. Entegrasyon süreci lisans ve PCI incelemesi ile geliştirme, test ve soft launch aşamalarını içerir. Sayfa bireysel tesislerden doğrudan API bağlantısı kabul edilmediğini belirtir.
  - `MAIN-EXPEDIA-API` [Expedia Connectivity: Lodging APIs overview](https://connectivityportal.expediagroup.com/documentation/expedia): Resmi doküman availability/rates, reservation management ve product management sınırlarını ayırır. Product API oda tipi/rate plan yönetimini, review API onaylı yorumlara yanıt ve puan agregasını tanıtır.
  - `kvkk-security` [KVKK: Kişisel Veri Güvenliği Rehberi, Teknik ve İdari Tedbirler](https://www.kvkk.gov.tr/yayinlar/veri_guvenligi_rehberi.pdf): Resmi rehber risklere uygun teknik ve idari tedbirleri ele alır. Veri envanteri, erişim politikaları, veri işleyen ilişkileri, bulut, yedekleme ve saklama/imha başlıkları vardır. İhtiyaç duyulmayan verinin güvenli imhası ve veri işleyen güvenliğinin değerlendirilmesi açıklanır.
  - `kvkk-notice-consent` [KVKK: 18.02.2026 tarihli 2026/347 sayılı ilke kararına ilişkin aydınlatma ve açık rıza duyurusu](https://www.kvkk.gov.tr/Icerik/8710/veri-sorumlulari-tarafindan-acik-riza-ve-aydinlatma-metinlerinin-ayri-ayri-duzenlenmesi-gerektigi-hakkinda-kisisel-verileri-koruma-kurulunun-18-02-2026-tarihli-ve-2026-347-sayili-ilke-kararina-iliskin-kamuoyu-duyurusu): Kurum duyurusu aydınlatma ve açık rıza metinlerinin ayrı düzenlenmesini, aydınlatma için onay/rıza istenmemesini ve metinlerin faaliyete uyarlanmasını vurgular. Veri kategorileri, işleme amaçları ve hukuki sebepler açıkça ifade edilmelidir.

#### RM-24: Atomik çok geceli hold, confirm ve release
*Öncelik P0 · Efor XL · Sorumlu: Backend Inventory Engineer · Bağımlılık: RM-14, RM-15, RM-20, RM-23 · Karşıladığı gereksinim: R5, R6, R12*

**Amaç:** Tüm konaklama gecelerini tek transaction'da tutan, süresi ve idempotent confirm/release davranışı açık HotelHold kurmak. Son stok için aynı veya farklı rate plan yarışında yalnız kapasite kadar kazanan olacak. Waylo allotment dışı kanallardaki satışları bilmeden toplam tesis overbooking garantisi verilmeyecek.

**Nerede:**
- `hotel-service/src/main/java/com/waylo/hotelservice/repository/RoomRepository.java` (mevcut)
- `hotel-service/src/main/java/com/waylo/hotelservice/inventory/HotelHoldService.java` (önerilen/yeni)
- `hotel-service/src/test/java/com/waylo/hotelservice/inventory/MultiNightHoldIT.java` (önerilen/yeni)

**Nasıl:**
1. Hotel/roomType/date kararlı lock sırasıyla tüm gecelerin shared inventory satırını kilitle. Eksik gün veya yetersiz available varsa tüm transaction rollback olsun, checkout gecesi kapsam dışında kalsın.
2. Hold'a principal/order correlation, quote reference, quantity, expiresAt ve durum ekle. Stock held artışı ve outbox aynı transaction'da oluşsun, aynı scope/key tekrarında aynı sonucu döndür.
3. Confirm held→confirmed ve release held azaltımını tek izinli geçiş olarak uygula. Expired hold'a geç ödeme confirm yapmasın, yeniden stock acquire veya refund/manual-review sonucu dönsün.
4. Expiry worker ve yarışları lock/version ile koru. Bulk calendar update de aynı kapasite invariant'ına uysun, duplicate confirm/release stok sayaçlarını ikinci kez değiştirmesin.

**Sözleşme/kontrat:**
- HotelHoldRequest(quoteId, stay, quantity, idempotencyKey), hold(): POST /api/v1/internal/hotel-holds
- confirmHold(holdId), releaseHold(holdId): POST /api/v1/internal/hotel-holds/{id}/confirm ve POST /api/v1/internal/hotel-holds/{id}/release, yalnız authenticated service scope

**Kabul ölçütü (bu görev uygulandığında koşulacak testler):**
- Pozitif: üç geceli hold her gün held sayısını artırır, confirm yalnız bir kez confirmed'a taşır.
- Negatif: tek gecede stok yoksa hiçbir gün tutulmaz, hiçbir inventory available negatif olmaz.
- Concurrency: aynı roomType'ın iki rate planında son oda için eşzamanlı hold'lardan yalnız biri kazanır.
- E2E: expiry/confirm/release yarışı ve restart sonrası sayaçlar tutarlı kalır, public kullanıcı internal confirm yapamaz.

**Teslimat:** Hold state machine ve internal typed port; Kararlı lock/invariant uygulama tasarımı; Gerçek PostgreSQL concurrency ve expiry testleri

**İlgili kaynaklar:**
  - `TECH-PG-LOCK` [PostgreSQL 16: Explicit Locking](https://www.postgresql.org/docs/16/explicit-locking.html): SELECT FOR UPDATE seçilen satırların rakip güncellenmesini, silinmesini ve çatışan kilitlenmesini transaction sonuna kadar engeller. Normal okumaları engellemez, yüksek izolasyonda değişmiş satırı kilitlemek hata verebilir.
  - `TECH-TESTCONTAINERS` [Testcontainers for Java: Postgres Module](https://java.testcontainers.org/modules/databases/postgres/): PostgreSQLContainer Java içinden seçilen PostgreSQL image ile geçici veritabanı başlatır, doküman alternatif jdbc:tc bağlantı mekanizmasını da gösterir.
  - `MAIN-BOOKING-ARI` [Booking.com: Create or update inventory, rates and restrictions](https://developers.booking.com/connectivity/docs/b_xml-availability): Resmi örnek stok sayısını oda tipi seviyesinde bütün rate planlar için ortak tutar. Fiyat ve kısıtlar oda tipi/rate plan/tarih boyutlarıyla güncellenir, tarih aralığının son günü dahil değildir.
  - `TECH-AUTHORIZATION` [OWASP Authorization Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Authorization_Cheat_Sheet.html): OWASP varsayılan reddetmeyi ve kaynağı ne olursa olsun her istekte izin doğrulamayı önerir.
  - `TECH-SECURITY-METHOD` [Spring Security: Method Security](https://docs.spring.io/spring-security/reference/servlet/authorization/method-security.html): EnableMethodSecurity, Spring-managed metotlarda PreAuthorize/PostAuthorize kontrollerini etkinleştirir, security starter bunu kendiliğinden açmaz. Metot yetkilendirmesi Spring AOP tabanlıdır.
  - `TECH-SECURITY-JWT` [Spring Security: OAuth 2.0 Resource Server JWT](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html): Resource Server imza, exp, nbf ve yapılandırılmış issuer kontrolü yapar, scope değerlerini SCOPE_ authority olarak eşler. Audience doğrulaması ayrıca yapılandırılabilir.
  - `TECH-OUTBOX` [Debezium: Outbox Event Router](https://debezium.io/documentation/reference/stable/transformations/outbox-event-router.html): Connector outbox tablosundaki değişiklikleri yakalar ve outbox event router SMT ile dönüştürür. Doküman dönüşümün yalnızca outbox olaylarına uygulanmasını, birden çok outbox varsa aynı yapıya sahip olmalarını ister.

#### RM-25: Order, OrderItem snapshot ve partner rezervasyon readprojection
*Öncelik P0 · Efor XL · Sorumlu: Backend Orders Engineer · Bağımlılık: RM-14, RM-15, RM-16, RM-22, RM-23, RM-24 · Karşıladığı gereksinim: R3, R4, R6, R8, R9, R10*

**Amaç:** Mevcut booking servisine yeni Order/OrderItem modülü eklemek, hotel-only checkout'u uçuş/araba zorunluluğundan ayırmak ve RM-22 panelini gerçek rezervasyon projeksiyonuna bağlamak. İlk MVP bir order/bir hotel item kullanacak, schema typed çok item genişlemesine hazır kalacak, sipariş bütünü bir hotel tenant'ının malı sayılmayacak.

**Nerede:**
- `package-booking-service/src/main/java/com/waylo/packagebookingservice/domain/PackageBooking.java` (mevcut)
- `package-booking-service/src/main/java/com/waylo/packagebookingservice/controller/PackageBookingController.java` (mevcut)
- `package-booking-service/src/main/java/com/waylo/packagebookingservice/order/OrderService.java` (önerilen/yeni)
- `hotel-service/src/main/java/com/waylo/hotelservice/reservation/PartnerReservationProjection.java` (önerilen/yeni)

**Nasıl:**
1. Order customer ownership ve tek settlement currency modelle. Item'a vertical, supplierOrganizationId, productRef, fulfillmentRef ve immutable commercial snapshot ekle, legacy geçmiş okumasını ayrı tut.
2. Quote revalidation ve hold reference ile idempotent order yarat. Aynı key/body tekrarında aynı order dönsün, yeni mutasyon mevcut PackageBooking flight/car referanslarını zorunlu kılmasın.
3. Supplier item event'lerinden partner reservation readprojection kur. Tenant yalnız kendi item tutarı, operasyon için minimum guest ve fulfillment durumunu görsün, müşteri diğer item/payment toplamı açılmasın.
4. RM-22 rezervasyon list/detail binding ve gerçek rezervasyon KPI'sını tamamla. Henüz ödeme/confirm oluşmayan pending kaydı confirmed gibi gösterme, finance KPI RM-31'e kadar bekleniyor kalsın.

**Sözleşme/kontrat:**
- CreateOrderRequest(quoteId, holdId), createOrderFromQuote(): POST /api/v1/orders
- OwnedOrderResponse, getOwnedOrder(): GET /api/v1/me/orders/{id}
- PartnerReservationResponse, listHotelReservations(): GET /api/v1/partner/organizations/{orgId}/hotels/{hotelId}/reservations

**Kabul ölçütü (bu görev uygulandığında koşulacak testler):**
- Pozitif: hotel-only order tek item ve değişmez quote/policy snapshot ile oluşur, legacy ID readback çalışır.
- Negatif: başka müşteri order'ı ve başka supplier item'ı okunamaz, partner toplam ödeme detayını göremez.
- E2E: gerçek order item projeksiyonu partner panelinde pending görünür, replay duplicate rezervasyon yaratmaz.
- Contract: typed multi-item test adapter diğer item'ı sızdırmaz, gerçek uçuş/araba satışının tamamlandığı iddia edilmez.

**Teslimat:** Order/OrderItem schema ve ownership policy; Partner readprojection ve RM-22 panel binding; Hotel-only, duplicate ve cross-item contract testleri

**İlgili kaynaklar:**
  - `TECH-TENANT` [OWASP Multi-Tenant Application Security Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Multi_Tenant_Security_Cheat_Sheet.html): İstemciden gelen tenant kimliği yalnızca seçicidir, doğrulanmış kimlik ve güncel üyelikle yetkilendirilmelidir. PostgreSQL tenant bağlamını transaction-local tutmak, havuzdaki bağlantının sonraki isteğe tenant durumunu sızdırmasını önler.
  - `TECH-OPENAPI` [OpenAPI Specification v3.1.1: Schema Object](https://spec.openapis.org/oas/v3.1.1.html): OAS 3.1 Schema Object JSON Schema Draft 2020-12 temellidir. format gibi bazı anahtarlar varsayılan vocabulary içinde doğrudan doğrulama değil annotation niteliğindedir.
  - `TECH-OUTBOX` [Debezium: Outbox Event Router](https://debezium.io/documentation/reference/stable/transformations/outbox-event-router.html): Connector outbox tablosundaki değişiklikleri yakalar ve outbox event router SMT ile dönüştürür. Doküman dönüşümün yalnızca outbox olaylarına uygulanmasını, birden çok outbox varsa aynı yapıya sahip olmalarını ister.
  - `TECH-MOBILE` [Flutter: Guide to app architecture](https://docs.flutter.dev/app-architecture/guide): Flutter rehberi view/view-model ile UI durumu ve görünümü ayırır, data katmanını repository ve service olarak böler. Repository domain verisinin kaynağıdır ve cache, hata, retry ve refresh davranışlarını yönetir.
  - `MAIN-RN-SECURITY` [React Native: Security](https://reactnative.dev/docs/security): Resmi rehber AsyncStorage'ı şifrelenmeyen store olarak açıklar ve token/secrets için önermediğini belirtir. Client bundle'a server secret koymamak ve platform secure storage seçeneklerini değerlendirmek gerekir.
  - `kvkk-security` [KVKK: Kişisel Veri Güvenliği Rehberi, Teknik ve İdari Tedbirler](https://www.kvkk.gov.tr/yayinlar/veri_guvenligi_rehberi.pdf): Resmi rehber risklere uygun teknik ve idari tedbirleri ele alır. Veri envanteri, erişim politikaları, veri işleyen ilişkileri, bulut, yedekleme ve saklama/imha başlıkları vardır. İhtiyaç duyulmayan verinin güvenli imhası ve veri işleyen güvenliğinin değerlendirilmesi açıklanır.
  - `kvkk-notice-consent` [KVKK: 18.02.2026 tarihli 2026/347 sayılı ilke kararına ilişkin aydınlatma ve açık rıza duyurusu](https://www.kvkk.gov.tr/Icerik/8710/veri-sorumlulari-tarafindan-acik-riza-ve-aydinlatma-metinlerinin-ayri-ayri-duzenlenmesi-gerektigi-hakkinda-kisisel-verileri-koruma-kurulunun-18-02-2026-tarihli-ve-2026-347-sayili-ilke-kararina-iliskin-kamuoyu-duyurusu): Kurum duyurusu aydınlatma ve açık rıza metinlerinin ayrı düzenlenmesini, aydınlatma için onay/rıza istenmemesini ve metinlerin faaliyete uyarlanmasını vurgular. Veri kategorileri, işleme amaçları ve hukuki sebepler açıkça ifade edilmelidir.
  - `TECH-PLAYWRIGHT` [Playwright: Authentication](https://playwright.dev/docs/auth): Auth setup browser state dosyasını sonraki testlerde yeniden kullanabilir, dosya hassas cookie/header içerdiğinden repoya konmamalıdır. Paralel testler sunucu durumunu değiştiriyorsa farklı hesaplar gerekir.

#### RM-26: Sandbox ödeme attempt ve imzalı callback
*Öncelik P0 · Efor L · Sorumlu: Backend Payment Engineer · Bağımlılık: RM-12, RM-14, RM-15, RM-25 · Karşıladığı gereksinim: R6, R8, R11, R12*

**Amaç:** Order sahibi için sandbox ödeme denemesi başlatmak ve yalnız doğrulanmış provider callback'ini finansal sonuç olarak kabul etmek. Payment, Order ve Fulfillment state machine'leri ayrı kalacak. Payment success tek başına otel confirmation sayılmayacak, canlı tahsilat ve merchant-of-record kararları bu teknik sandbox tesliminin dışında kapı olarak kalacak.

**Nerede:**
- `payment-service/src/main/java/com/waylo/paymentservice/service/PaymentServiceImpl.java` (mevcut)
- `payment-service/src/main/java/com/waylo/paymentservice/controller/PaymentController.java` (mevcut)
- `payment-service/src/main/java/com/waylo/paymentservice/payment/ProviderCallbackVerifier.java` (önerilen/yeni)

**Nasıl:**
1. PaymentAttempt'a owned order, amount/currency snapshot, provider reference ve idempotency scope ekle. Client amount'ı otorite sayma, secret ve kart verisini uygulama DB/loglarına yazma.
2. Sandbox provider port ve signed callback kontratını seçimsiz adapter sınırıyla tasarla. Ham body imzası, timestamp/replay window, provider identity ve attempt amount/currency doğrulaması yap.
3. Callback dedup'u provider event reference ile transaction içinde uygula. Payment sonucu ve outbox atomik olsun, duplicate callback yeni charge/attempt oluşturmasın, belirsiz sonucu pending/manual-review tut.
4. Order/partner ekranına ödeme ile fulfillment ayrı durumlarını taşı. Sandbox başarıyı gerçek payout gibi gösterme, mobil checkout kontratında processing ve retry UX'ini açık belirt.

**Sözleşme/kontrat:**
- PaymentAttemptRequest(idempotencyKey), startPayment(actor, ownedOrder): POST /api/v1/orders/{id}/payment-attempts
- SignedProviderCallback, verifyAndApplyCallback(rawBody, signature): POST /api/v1/payments/providers/{provider}/callbacks

**Kabul ölçütü (bu görev uygulandığında koşulacak testler):**
- Pozitif: order sahibi sandbox attempt başlatır, doğru imzalı callback tek payment success kaydı oluşturur.
- Negatif: yanlış imza, expired timestamp, başka order sahibi veya amount/currency mismatch teyit üretmez.
- Replay: aynı callback tekrarı aynı sonucu verir, ikinci charge/attempt/booking oluşmaz.
- E2E: checkout processing durumundan ödeme sonucuna geçer, stok confirm henüz yoksa rezervasyon confirmed görünmez.

**Teslimat:** Payment attempt ve provider adapter kontratı; İmza/dedup güvenlik testleri; Sandbox API ve UI durum entegrasyon kanıtı

**İlgili kaynaklar:**
  - `iyzico-submerchant` [iyzico: Alt Üye](https://docs.iyzico.com/urunler/pazaryeri/pazaryeri-entegrasyonu/alt-uye-olusturma): Onboarding API üç alt üye tipi destekler. Alanlar tipe göre değişir. Yanıttaki subMerchantKey satıcıyı ödeme sırasında tanımlar. IBAN ilgili kişi veya şirket unvanıyla uyumlu olmalıdır ve para gönderimi için onay öncesinde tamamlanmalıdır. Sayfa gerçek bilgiler gönderilmesini açıkça ister.
  - `iyzico-payment` [iyzico: Pazaryeri Ödemesi](https://docs.iyzico.com/urunler/pazaryeri/pazaryeri-entegrasyonu/pazaryeri-odemesi): Pazaryeri ödeme kırılımlarında subMerchantKey ve subMerchantPrice gönderilir. İkinci alan alt üyeye gönderilmesi istenen tutardır. Sayfadaki NON3D örneği tüm yöntemlerde alt üye alanlarının eklenmesi gerektiğini belirtir.
  - `iyzico-approval` [iyzico: Onay](https://docs.iyzico.com/urunler/pazaryeri/pazaryeri-entegrasyonu/onay): Belge, ödeme kırılımı onaylanana kadar paranın korumalı havuz hesapta bekletildiğini açıklar. Kırılım onayı ve onay kaldırma paymentTransactionId üzerinden yapılır. Onaylar kod seviyesinde gönderilir, panelde bu fonksiyon bulunmadığı belirtilir.
  - `iyzico-sandbox` [iyzico: Live vs Sandbox](https://docs.iyzico.com/en/getting-started/preliminaries/live-vs-sandbox): Sandbox gerçek işlem veya fonları etkilemeden ödeme akışlarını simüle eder. Sandbox ve canlı API adresleri ile kimlik bilgileri farklıdır. Test kartları yalnız sandbox senaryoları için sunulur.
  - `iyzico-refund` [iyzico: Refund & Cancel](https://docs.iyzico.com/en/advanced/refund-and-cancel): Standart iade paymentTransactionId ve price kullanır, tam veya kısmi olabilir ve kalan iade edilebilir tutarı aşamaz. Belge çok ürünlü sepetlerde Refund V2 kullanımını önermez. Cancel aynı gün ve tam işlem içindir. Karta yansıma bankaya göre değişir.
  - `iyzico-webhook` [iyzico: Webhook](https://docs.iyzico.com/ek-servisler/webhook): Webhook JSON HTTP POST bildirimi kullanır. HTTPS URL istenir. Belge X-IYZ-SIGNATURE-V3 doğrulamasını açıklar ve önceki sürümlerin desteklenmeyeceğini belirtir. İmza özelliğinin hesapta etkin olması gerekir. 2xx gelmeyen bildirimler için tekrar davranışı belgelenmiştir.
  - `MAIN-APPLE-PAYMENT` [Apple App Review Guidelines: 3.1.3(e)](https://developer.apple.com/app-store/review/guidelines/): 3.1.3(e) uygulama dışında tüketilen fiziksel ürün/hizmetlerin ödemesinde in-app purchase dışındaki yöntemleri belirtir.
  - `TECH-AUTHORIZATION` [OWASP Authorization Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Authorization_Cheat_Sheet.html): OWASP varsayılan reddetmeyi ve kaynağı ne olursa olsun her istekte izin doğrulamayı önerir.
  - `TECH-SECURITY-METHOD` [Spring Security: Method Security](https://docs.spring.io/spring-security/reference/servlet/authorization/method-security.html): EnableMethodSecurity, Spring-managed metotlarda PreAuthorize/PostAuthorize kontrollerini etkinleştirir, security starter bunu kendiliğinden açmaz. Metot yetkilendirmesi Spring AOP tabanlıdır.
  - `TECH-SECURITY-JWT` [Spring Security: OAuth 2.0 Resource Server JWT](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html): Resource Server imza, exp, nbf ve yapılandırılmış issuer kontrolü yapar, scope değerlerini SCOPE_ authority olarak eşler. Audience doğrulaması ayrıca yapılandırılabilir.
  - `TECH-OUTBOX` [Debezium: Outbox Event Router](https://debezium.io/documentation/reference/stable/transformations/outbox-event-router.html): Connector outbox tablosundaki değişiklikleri yakalar ve outbox event router SMT ile dönüştürür. Doküman dönüşümün yalnızca outbox olaylarına uygulanmasını, birden çok outbox varsa aynı yapıya sahip olmalarını ister.
  - `TECH-OPENAPI` [OpenAPI Specification v3.1.1: Schema Object](https://spec.openapis.org/oas/v3.1.1.html): OAS 3.1 Schema Object JSON Schema Draft 2020-12 temellidir. format gibi bazı anahtarlar varsayılan vocabulary içinde doğrudan doğrulama değil annotation niteliğindedir.
  - `TECH-MOBILE` [Flutter: Guide to app architecture](https://docs.flutter.dev/app-architecture/guide): Flutter rehberi view/view-model ile UI durumu ve görünümü ayırır, data katmanını repository ve service olarak böler. Repository domain verisinin kaynağıdır ve cache, hata, retry ve refresh davranışlarını yönetir.
  - `MAIN-RN-SECURITY` [React Native: Security](https://reactnative.dev/docs/security): Resmi rehber AsyncStorage'ı şifrelenmeyen store olarak açıklar ve token/secrets için önermediğini belirtir. Client bundle'a server secret koymamak ve platform secure storage seçeneklerini değerlendirmek gerekir.
  - `kvkk-security` [KVKK: Kişisel Veri Güvenliği Rehberi, Teknik ve İdari Tedbirler](https://www.kvkk.gov.tr/yayinlar/veri_guvenligi_rehberi.pdf): Resmi rehber risklere uygun teknik ve idari tedbirleri ele alır. Veri envanteri, erişim politikaları, veri işleyen ilişkileri, bulut, yedekleme ve saklama/imha başlıkları vardır. İhtiyaç duyulmayan verinin güvenli imhası ve veri işleyen güvenliğinin değerlendirilmesi açıklanır.
  - `kvkk-notice-consent` [KVKK: 18.02.2026 tarihli 2026/347 sayılı ilke kararına ilişkin aydınlatma ve açık rıza duyurusu](https://www.kvkk.gov.tr/Icerik/8710/veri-sorumlulari-tarafindan-acik-riza-ve-aydinlatma-metinlerinin-ayri-ayri-duzenlenmesi-gerektigi-hakkinda-kisisel-verileri-koruma-kurulunun-18-02-2026-tarihli-ve-2026-347-sayili-ilke-kararina-iliskin-kamuoyu-duyurusu): Kurum duyurusu aydınlatma ve açık rıza metinlerinin ayrı düzenlenmesini, aydınlatma için onay/rıza istenmemesini ve metinlerin faaliyete uyarlanmasını vurgular. Veri kategorileri, işleme amaçları ve hukuki sebepler açıkça ifade edilmelidir.

#### RM-27: Saga timeout, restart, replay ve geç ödeme reconciliation
*Öncelik P0 · Efor XL · Sorumlu: Backend Reliability Engineer · Bağımlılık: RM-15, RM-24, RM-25, RM-26 · Karşıladığı gereksinim: R6, R9, R11, R12*

**Amaç:** Quote→hold→order/payment→confirm akışını kalıcı saga durumlarıyla yönetmek, timeout ve replay altında stok/para uzlaşması sağlamak. Belirsiz işlem kullanıcıya başarı diye sunulmayacak. Hotel-only saga uçuş ve araba adımlarını çalıştırmayacak, platform cross-tenant scope sessiz tenant filtresi atlama şeklinde uygulanmayacak.

**Nerede:**
- `package-booking-saga-orchestrator/src/main/java/com/waylo/packagebookingsagaorchestrator/service/SagaOrchestratorServiceImpl.java` (mevcut)
- `package-booking-saga-orchestrator/src/main/java/com/waylo/packagebookingsagaorchestrator/domain/SagaInstance.java` (mevcut)
- `package-booking-saga-orchestrator/src/main/java/com/waylo/packagebookingsagaorchestrator/saga/HotelCheckoutSaga.java` (önerilen/yeni)

**Nasıl:**
1. Her adıma durable state, deadline, attempt ve correlation kaydet. Lease/retry mekanizması restart sonrası kaldığı adımdan devam etsin, yalnız hotel item için typed fulfillment port çalışsın.
2. Payment failure/timeout'ta hold release ve order state'i ayrı izle. Ödeme success sonrası confirm başarısızsa capture ile inventory sonucunu reconcile et, refund-required/manual-review işini kaybetme.
3. Expired hold'a geç callback gelince otomatik confirm etme. Kontrollü yeni acquire veya ödeme telafisi kararı üret, compensation başarısızlığını operatör kuyruğunda görünür tut.
4. DLQ replay, out-of-order callback ve duplicate event'i idempotent command'larla işle. Reconciliation job açık platform permission/service identity ile çalışsın, audit ve metric'lerde unresolved durumları göster.

**Sözleşme/kontrat:**
- HotelCheckoutSagaState(orderId, step, deadline, outcome), advanceCheckout(event)
- ReconciliationResult, reconcileOrder(actor, orderId, reason): POST /api/v1/admin/orders/{id}/reconciliation-requests

**Kabul ölçütü (bu görev uygulandığında koşulacak testler):**
- Pozitif: sandbox ödeme sonrası hold confirm olur, owned order ve partner panel aynı fulfillment'i gösterir.
- Negatif: timeout stok tutmasını serbest bırakır, geç payment success expired hold'u kendiliğinden confirmed yapmaz.
- Restart/replay: her adımda process kesilip yeniden başlatılır, tek fulfillment ve tutarlı para/stok sonucu oluşur.
- E2E: confirm başarısızlığında kullanıcı processing/manual-review görür, audit ve unresolved reconciliation kaydı operatöre ulaşır.

**Teslimat:** Kalıcı hotel-only saga ve compensation matrisi; Timeout/restart/DLQ fault injection testleri; Reconciliation API, metric ve runbook

**İlgili kaynaklar:**
  - `TECH-OUTBOX` [Debezium: Outbox Event Router](https://debezium.io/documentation/reference/stable/transformations/outbox-event-router.html): Connector outbox tablosundaki değişiklikleri yakalar ve outbox event router SMT ile dönüştürür. Doküman dönüşümün yalnızca outbox olaylarına uygulanmasını, birden çok outbox varsa aynı yapıya sahip olmalarını ister.
  - `iyzico-submerchant` [iyzico: Alt Üye](https://docs.iyzico.com/urunler/pazaryeri/pazaryeri-entegrasyonu/alt-uye-olusturma): Onboarding API üç alt üye tipi destekler. Alanlar tipe göre değişir. Yanıttaki subMerchantKey satıcıyı ödeme sırasında tanımlar. IBAN ilgili kişi veya şirket unvanıyla uyumlu olmalıdır ve para gönderimi için onay öncesinde tamamlanmalıdır. Sayfa gerçek bilgiler gönderilmesini açıkça ister.
  - `iyzico-payment` [iyzico: Pazaryeri Ödemesi](https://docs.iyzico.com/urunler/pazaryeri/pazaryeri-entegrasyonu/pazaryeri-odemesi): Pazaryeri ödeme kırılımlarında subMerchantKey ve subMerchantPrice gönderilir. İkinci alan alt üyeye gönderilmesi istenen tutardır. Sayfadaki NON3D örneği tüm yöntemlerde alt üye alanlarının eklenmesi gerektiğini belirtir.
  - `iyzico-approval` [iyzico: Onay](https://docs.iyzico.com/urunler/pazaryeri/pazaryeri-entegrasyonu/onay): Belge, ödeme kırılımı onaylanana kadar paranın korumalı havuz hesapta bekletildiğini açıklar. Kırılım onayı ve onay kaldırma paymentTransactionId üzerinden yapılır. Onaylar kod seviyesinde gönderilir, panelde bu fonksiyon bulunmadığı belirtilir.
  - `iyzico-sandbox` [iyzico: Live vs Sandbox](https://docs.iyzico.com/en/getting-started/preliminaries/live-vs-sandbox): Sandbox gerçek işlem veya fonları etkilemeden ödeme akışlarını simüle eder. Sandbox ve canlı API adresleri ile kimlik bilgileri farklıdır. Test kartları yalnız sandbox senaryoları için sunulur.
  - `iyzico-refund` [iyzico: Refund & Cancel](https://docs.iyzico.com/en/advanced/refund-and-cancel): Standart iade paymentTransactionId ve price kullanır, tam veya kısmi olabilir ve kalan iade edilebilir tutarı aşamaz. Belge çok ürünlü sepetlerde Refund V2 kullanımını önermez. Cancel aynı gün ve tam işlem içindir. Karta yansıma bankaya göre değişir.
  - `iyzico-webhook` [iyzico: Webhook](https://docs.iyzico.com/ek-servisler/webhook): Webhook JSON HTTP POST bildirimi kullanır. HTTPS URL istenir. Belge X-IYZ-SIGNATURE-V3 doğrulamasını açıklar ve önceki sürümlerin desteklenmeyeceğini belirtir. İmza özelliğinin hesapta etkin olması gerekir. 2xx gelmeyen bildirimler için tekrar davranışı belgelenmiştir.
  - `MAIN-APPLE-PAYMENT` [Apple App Review Guidelines: 3.1.3(e)](https://developer.apple.com/app-store/review/guidelines/): 3.1.3(e) uygulama dışında tüketilen fiziksel ürün/hizmetlerin ödemesinde in-app purchase dışındaki yöntemleri belirtir.
  - `TECH-PG-LOCK` [PostgreSQL 16: Explicit Locking](https://www.postgresql.org/docs/16/explicit-locking.html): SELECT FOR UPDATE seçilen satırların rakip güncellenmesini, silinmesini ve çatışan kilitlenmesini transaction sonuna kadar engeller. Normal okumaları engellemez, yüksek izolasyonda değişmiş satırı kilitlemek hata verebilir.
  - `TECH-TESTCONTAINERS` [Testcontainers for Java: Postgres Module](https://java.testcontainers.org/modules/databases/postgres/): PostgreSQLContainer Java içinden seçilen PostgreSQL image ile geçici veritabanı başlatır, doküman alternatif jdbc:tc bağlantı mekanizmasını da gösterir.
  - `TECH-AUTHORIZATION` [OWASP Authorization Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Authorization_Cheat_Sheet.html): OWASP varsayılan reddetmeyi ve kaynağı ne olursa olsun her istekte izin doğrulamayı önerir.
  - `TECH-SECURITY-METHOD` [Spring Security: Method Security](https://docs.spring.io/spring-security/reference/servlet/authorization/method-security.html): EnableMethodSecurity, Spring-managed metotlarda PreAuthorize/PostAuthorize kontrollerini etkinleştirir, security starter bunu kendiliğinden açmaz. Metot yetkilendirmesi Spring AOP tabanlıdır.
  - `TECH-SECURITY-JWT` [Spring Security: OAuth 2.0 Resource Server JWT](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html): Resource Server imza, exp, nbf ve yapılandırılmış issuer kontrolü yapar, scope değerlerini SCOPE_ authority olarak eşler. Audience doğrulaması ayrıca yapılandırılabilir.

#### RM-28: Policy-bound iptal, async refund ve stok release
*Öncelik P0 · Efor L · Sorumlu: Backend Commerce Engineer · Bağımlılık: RM-15, RM-25, RM-26, RM-27 · Karşıladığı gereksinim: R6, R7, R11, R12*

**Amaç:** Müşteri ve izinli partner iptalini değişmez booking policy snapshot'ına bağlamak, finansal iade ile fulfillment iptalini ayrı takip etmek. Otel politikası sonradan düzenlense bile geçmiş siparişin hakları değişmeyecek. Async refund beklerken UI iade tamamlandı demeyecek, inventory release ikinci kez uygulanmayacak.

**Nerede:**
- `package-booking-service/src/main/java/com/waylo/packagebookingservice/service/PackageBookingServiceImpl.java` (mevcut)
- `payment-service/src/main/java/com/waylo/paymentservice/event/PaymentRefundedEvent.java` (mevcut)
- `package-booking-service/src/main/java/com/waylo/packagebookingservice/order/CancellationService.java` (önerilen/yeni)

**Nasıl:**
1. Cancellation request'i actor, owned itemIds, reason ve idempotency key ile oluştur. Tesis timezone ve snapshot policy cutoff'una göre izin/tutar hesapla, güncel RatePlan politikasıyla geçmişi değiştirme.
2. CancellationStatus, RefundStatus ve FulfillmentStatus geçişlerini ayrı tut. İptal onayı, confirmed inventory release ve refund request outbox'ını izinli local transaction sınırlarında üret.
3. Provider refund'u payment-service üzerinden idempotent yürüt. Partial/failed/unknown refund durumlarında retry ve manual-review üret, order currency ve tahsil edilmiş tutarı aşan iade engellensin.
4. Müşteri ve partner ekranına policy sonucu, bekleyen iade ve izinli command'ları bağla. Sonuçları reconciliation'a taşı, iptal terminal booking'e review hakkı vermesin ve audit gerekçesi korunsun.

**Sözleşme/kontrat:**
- CancellationRequest(itemIds, reason), requestCancellation(): POST /api/v1/me/orders/{id}/cancellation-requests
- PartnerCancellationRequest(reason), requestPartnerCancellation(): POST /api/v1/partner/organizations/{orgId}/hotels/{hotelId}/reservations/{id}/cancellation-requests

**Kabul ölçütü (bu görev uygulandığında koşulacak testler):**
- Pozitif: cutoff öncesi iptal snapshot'a uygun iade tutarı ve stok release üretir, müşteri sonucu görür.
- Negatif: başka müşterinin/tesisin item'ı iptal edilemez, policy dışı istek reason/error ile reddedilir.
- Replay: duplicate iptal/refund callback ikinci stok release veya fazla iade oluşturmaz.
- E2E: async refund başarısızlığı pending/failed görünür, reconciliation sonrası ödeme ve stok kayıtları açıklanabilir biçimde eşleşir.

**Teslimat:** Cancellation/refund state machine ve DTO; Policy snapshot hesaplama ve scoped UI binding; Cutoff, replay, async hata ve reconciliation testleri

**İlgili kaynaklar:**
  - `iyzico-submerchant` [iyzico: Alt Üye](https://docs.iyzico.com/urunler/pazaryeri/pazaryeri-entegrasyonu/alt-uye-olusturma): Onboarding API üç alt üye tipi destekler. Alanlar tipe göre değişir. Yanıttaki subMerchantKey satıcıyı ödeme sırasında tanımlar. IBAN ilgili kişi veya şirket unvanıyla uyumlu olmalıdır ve para gönderimi için onay öncesinde tamamlanmalıdır. Sayfa gerçek bilgiler gönderilmesini açıkça ister.
  - `iyzico-payment` [iyzico: Pazaryeri Ödemesi](https://docs.iyzico.com/urunler/pazaryeri/pazaryeri-entegrasyonu/pazaryeri-odemesi): Pazaryeri ödeme kırılımlarında subMerchantKey ve subMerchantPrice gönderilir. İkinci alan alt üyeye gönderilmesi istenen tutardır. Sayfadaki NON3D örneği tüm yöntemlerde alt üye alanlarının eklenmesi gerektiğini belirtir.
  - `iyzico-approval` [iyzico: Onay](https://docs.iyzico.com/urunler/pazaryeri/pazaryeri-entegrasyonu/onay): Belge, ödeme kırılımı onaylanana kadar paranın korumalı havuz hesapta bekletildiğini açıklar. Kırılım onayı ve onay kaldırma paymentTransactionId üzerinden yapılır. Onaylar kod seviyesinde gönderilir, panelde bu fonksiyon bulunmadığı belirtilir.
  - `iyzico-sandbox` [iyzico: Live vs Sandbox](https://docs.iyzico.com/en/getting-started/preliminaries/live-vs-sandbox): Sandbox gerçek işlem veya fonları etkilemeden ödeme akışlarını simüle eder. Sandbox ve canlı API adresleri ile kimlik bilgileri farklıdır. Test kartları yalnız sandbox senaryoları için sunulur.
  - `iyzico-refund` [iyzico: Refund & Cancel](https://docs.iyzico.com/en/advanced/refund-and-cancel): Standart iade paymentTransactionId ve price kullanır, tam veya kısmi olabilir ve kalan iade edilebilir tutarı aşamaz. Belge çok ürünlü sepetlerde Refund V2 kullanımını önermez. Cancel aynı gün ve tam işlem içindir. Karta yansıma bankaya göre değişir.
  - `iyzico-webhook` [iyzico: Webhook](https://docs.iyzico.com/ek-servisler/webhook): Webhook JSON HTTP POST bildirimi kullanır. HTTPS URL istenir. Belge X-IYZ-SIGNATURE-V3 doğrulamasını açıklar ve önceki sürümlerin desteklenmeyeceğini belirtir. İmza özelliğinin hesapta etkin olması gerekir. 2xx gelmeyen bildirimler için tekrar davranışı belgelenmiştir.
  - `MAIN-APPLE-PAYMENT` [Apple App Review Guidelines: 3.1.3(e)](https://developer.apple.com/app-store/review/guidelines/): 3.1.3(e) uygulama dışında tüketilen fiziksel ürün/hizmetlerin ödemesinde in-app purchase dışındaki yöntemleri belirtir.
  - `TECH-OUTBOX` [Debezium: Outbox Event Router](https://debezium.io/documentation/reference/stable/transformations/outbox-event-router.html): Connector outbox tablosundaki değişiklikleri yakalar ve outbox event router SMT ile dönüştürür. Doküman dönüşümün yalnızca outbox olaylarına uygulanmasını, birden çok outbox varsa aynı yapıya sahip olmalarını ister.
  - `MAIN-BOOKING-ARI` [Booking.com: Create or update inventory, rates and restrictions](https://developers.booking.com/connectivity/docs/b_xml-availability): Resmi örnek stok sayısını oda tipi seviyesinde bütün rate planlar için ortak tutar. Fiyat ve kısıtlar oda tipi/rate plan/tarih boyutlarıyla güncellenir, tarih aralığının son günü dahil değildir.
  - `TECH-OPENAPI` [OpenAPI Specification v3.1.1: Schema Object](https://spec.openapis.org/oas/v3.1.1.html): OAS 3.1 Schema Object JSON Schema Draft 2020-12 temellidir. format gibi bazı anahtarlar varsayılan vocabulary içinde doğrudan doğrulama değil annotation niteliğindedir.
  - `TECH-MOBILE` [Flutter: Guide to app architecture](https://docs.flutter.dev/app-architecture/guide): Flutter rehberi view/view-model ile UI durumu ve görünümü ayırır, data katmanını repository ve service olarak böler. Repository domain verisinin kaynağıdır ve cache, hata, retry ve refresh davranışlarını yönetir.
  - `MAIN-RN-SECURITY` [React Native: Security](https://reactnative.dev/docs/security): Resmi rehber AsyncStorage'ı şifrelenmeyen store olarak açıklar ve token/secrets için önermediğini belirtir. Client bundle'a server secret koymamak ve platform secure storage seçeneklerini değerlendirmek gerekir.

#### RM-29: Konaklama operasyonu ve doğrulanmış review
*Öncelik P0 · Efor L · Sorumlu: Full-stack Hotel Engineer · Bağımlılık: RM-13, RM-14, RM-15, RM-25, RM-27, RM-28 · Karşıladığı gereksinim: R3, R7, R8, R11*

**Amaç:** Partner check-in/check-out/no-show command'ları ile gerçek fulfillment durumunu yönetmek ve yalnız uygun tamamlanmış konaklamanın sahibine review hakkı vermek. NO_SHOW completed değildir. Tarihin geçmesi tek başına completed yapmaz, kayıtlı check-out gerekir. İşletme müşterinin puanını değiştiremez veya silemez.

**Nerede:**
- `frontend/src/app/checkins/page.tsx` (mevcut)
- `hotel-service/src/main/java/com/waylo/hotelservice/reservation/StayOperationService.java` (önerilen/yeni)
- `hotel-service/src/main/java/com/waylo/hotelservice/review/VerifiedReviewService.java` (önerilen/yeni)
- `frontend/src/app/partner/hotels/[hotelId]/reviews/page.tsx` (önerilen/yeni)

**Nasıl:**
1. CHECK_IN/CHECK_OUT/NO_SHOW için role, ownership, expectedVersion ve state geçişlerini doğrula. Serbest status dropdown kullanma, checkout geçmişliği hotel timezone ile denetlensin.
2. Review eligibility'de customer ownership, COMPLETED ve checkout geçmişliği şartlarını birlikte uygula. İptal/no-show ve aynı tesiste aktif işletme üyeliğini reddet, reservation+customer unique constraint ekle.
3. Score 1..5, text validation ve moderation history modelle. Public ortalamayı yalnız yayınlanmış uygun review'lardan hesapla, reviewCount sıfırken score null olsun.
4. Partner yalnız response/report command kullansın. Admin moderation gerekçe/audit gerektirsin, mobil review DTO ve partner operasyon ekranını gerçek API'ye bağla, edit window kararını pilot kapısında açık tut.

**Sözleşme/kontrat:**
- StayOperationRequest(operation, expectedVersion), applyStayOperation(): POST /api/v1/partner/organizations/{orgId}/hotels/{hotelId}/reservations/{id}/operations
- ReviewRequest(score, text), submitVerifiedStayReview(): POST /api/v1/me/hotel-reservations/{id}/review
- listPublishedReviews(): GET /api/v1/hotels/{id}/reviews

**Kabul ölçütü (bu görev uygulandığında koşulacak testler):**
- Pozitif: kayıtlı checkout sonrası reservation sahibi tek review yazar, public ortalama güncellenir.
- Negatif: no-show, cancelled, başkası, erken checkout tarihi ve aktif hotel üyesi review yazamaz.
- Negatif: işletme puan değiştiremez, duplicate review unique conflict verir, boş otel puanı null kalır.
- E2E: ödeme→partner check-in/out→customer review→partner response gerçek kayıtlarla çalışır, moderation audit saklanır.

**Teslimat:** Stay operation ve review eligibility API; Partner operasyon/review ekranları ve mobil kontrat; Tamamlanma/no-show/owner ve public aggregate testleri

**İlgili kaynaklar:**
  - `TECH-MOBILE` [Flutter: Guide to app architecture](https://docs.flutter.dev/app-architecture/guide): Flutter rehberi view/view-model ile UI durumu ve görünümü ayırır, data katmanını repository ve service olarak böler. Repository domain verisinin kaynağıdır ve cache, hata, retry ve refresh davranışlarını yönetir.
  - `MAIN-RN-SECURITY` [React Native: Security](https://reactnative.dev/docs/security): Resmi rehber AsyncStorage'ı şifrelenmeyen store olarak açıklar ve token/secrets için önermediğini belirtir. Client bundle'a server secret koymamak ve platform secure storage seçeneklerini değerlendirmek gerekir.
  - `TECH-OPENAPI` [OpenAPI Specification v3.1.1: Schema Object](https://spec.openapis.org/oas/v3.1.1.html): OAS 3.1 Schema Object JSON Schema Draft 2020-12 temellidir. format gibi bazı anahtarlar varsayılan vocabulary içinde doğrudan doğrulama değil annotation niteliğindedir.
  - `TECH-AUTHORIZATION` [OWASP Authorization Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Authorization_Cheat_Sheet.html): OWASP varsayılan reddetmeyi ve kaynağı ne olursa olsun her istekte izin doğrulamayı önerir.
  - `TECH-SECURITY-METHOD` [Spring Security: Method Security](https://docs.spring.io/spring-security/reference/servlet/authorization/method-security.html): EnableMethodSecurity, Spring-managed metotlarda PreAuthorize/PostAuthorize kontrollerini etkinleştirir, security starter bunu kendiliğinden açmaz. Metot yetkilendirmesi Spring AOP tabanlıdır.
  - `TECH-SECURITY-JWT` [Spring Security: OAuth 2.0 Resource Server JWT](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html): Resource Server imza, exp, nbf ve yapılandırılmış issuer kontrolü yapar, scope değerlerini SCOPE_ authority olarak eşler. Audience doğrulaması ayrıca yapılandırılabilir.
  - `kvkk-security` [KVKK: Kişisel Veri Güvenliği Rehberi, Teknik ve İdari Tedbirler](https://www.kvkk.gov.tr/yayinlar/veri_guvenligi_rehberi.pdf): Resmi rehber risklere uygun teknik ve idari tedbirleri ele alır. Veri envanteri, erişim politikaları, veri işleyen ilişkileri, bulut, yedekleme ve saklama/imha başlıkları vardır. İhtiyaç duyulmayan verinin güvenli imhası ve veri işleyen güvenliğinin değerlendirilmesi açıklanır.
  - `kvkk-notice-consent` [KVKK: 18.02.2026 tarihli 2026/347 sayılı ilke kararına ilişkin aydınlatma ve açık rıza duyurusu](https://www.kvkk.gov.tr/Icerik/8710/veri-sorumlulari-tarafindan-acik-riza-ve-aydinlatma-metinlerinin-ayri-ayri-duzenlenmesi-gerektigi-hakkinda-kisisel-verileri-koruma-kurulunun-18-02-2026-tarihli-ve-2026-347-sayili-ilke-kararina-iliskin-kamuoyu-duyurusu): Kurum duyurusu aydınlatma ve açık rıza metinlerinin ayrı düzenlenmesini, aydınlatma için onay/rıza istenmemesini ve metinlerin faaliyete uyarlanmasını vurgular. Veri kategorileri, işleme amaçları ve hukuki sebepler açıkça ifade edilmelidir.
  - `TECH-PLAYWRIGHT` [Playwright: Authentication](https://playwright.dev/docs/auth): Auth setup browser state dosyasını sonraki testlerde yeniden kullanabilir, dosya hassas cookie/header içerdiğinden repoya konmamalıdır. Paralel testler sunucu durumunu değiştiriyorsa farklı hesaplar gerekir.

#### RM-30: Gerçek bildirim teslimi ve delivery tracking
*Öncelik P1 · Efor L · Sorumlu: Backend Messaging Engineer · Bağımlılık: RM-12, RM-15, RM-17, RM-27, RM-28, RM-29 · Karşıladığı gereksinim: R1, R6, R8, R11, R12*

**Amaç:** Başvuru, davet, rezervasyon teyidi, iptal ve refund bildirimlerini gerçek teslim kanalıyla göndermek ve queued/sent/delivered/failed durumlarını ayırmak. DB'ye notification yazmak veya log basmak teslim sayılmayacak. Mobil cihaz token'ları kimlikle bağlanacak, kanal erişimi ve provider seçimi uygulama kapısı olarak doğrulanacak.

**Nerede:**
- `notification-service/src/main/java/com/waylo/notificationservice/service/EmailServiceImpl.java` (mevcut)
- `notification-service/src/main/java/com/waylo/notificationservice/listener/BookingEventListener.java` (mevcut)
- `notification-service/src/main/java/com/waylo/notificationservice/domain/NotificationStatus.java` (mevcut)
- `notification-service/src/main/java/com/waylo/notificationservice/delivery/DeliveryAttemptService.java` (önerilen/yeni)

**Nasıl:**
1. Inbox dedup ile business event'ten recipient ve template version üret. Partner event'inde yalnız yetkili recipient ve kendi item bilgisi bulunsun, email/log payload'ına gereksiz guest PII ekleme.
2. Gerçek email/push adapter port'una delivery attempt, provider reference, retry/backoff ve dead-letter ekle. Provider acceptance ile final delivery ayrı alanlarda izlensin, timeout success olmasın.
3. Provider callback imza ve replay doğrulaması yap. Push token register/revoke işlemini user/session/device ownership ile koru, çıkış ve geçersiz token durumunu ele al.
4. Gerçek kontrollü inbox veya cihazda teyit mesajını gözle. Delivery failure rezervasyonun ticari durumunu bozmasın, operatör retry/audit ve kullanıcı bildirim geçmişi scoped API ile sunulsun.

**Sözleşme/kontrat:**
- DeviceRegistrationRequest(deviceId, pushToken), registerOwnedDevice(): POST /api/v1/me/notification-devices
- NotificationDeliveryResponse, listOwnedNotifications(): GET /api/v1/me/notifications
- DeliveryReceipt, applySignedReceipt(): POST /api/v1/notifications/providers/{provider}/callbacks

**Kabul ölçütü (bu görev uygulandığında koşulacak testler):**
- Pozitif: gerçek sandbox rezervasyon teyidi kontrollü inbox/cihaza ulaşır, provider reference ve delivery kanıtı kaydolur.
- Negatif: provider timeout/invalid token failed veya retrying kalır, yalnız DB kaydı delivered gösterilmez.
- Replay: aynı domain event ve receipt ikinci kullanıcı mesajı veya teslim etkisi yaratmaz.
- E2E: iptal/refund durum mesajı doğru kullanıcıya gider, başka tenant/cihaz geçmişi okunamaz ve rezervasyon state'i teslim hatasından etkilenmez.

**Teslimat:** Gerçek kanal adapter ve delivery state modeli; Scoped cihaz/bildirim API ve retry runbook; Inbox/cihaz teslim kanıtı ve negatif provider testleri

**İlgili kaynaklar:**
  - `TECH-OUTBOX` [Debezium: Outbox Event Router](https://debezium.io/documentation/reference/stable/transformations/outbox-event-router.html): Connector outbox tablosundaki değişiklikleri yakalar ve outbox event router SMT ile dönüştürür. Doküman dönüşümün yalnızca outbox olaylarına uygulanmasını, birden çok outbox varsa aynı yapıya sahip olmalarını ister.
  - `TECH-MOBILE` [Flutter: Guide to app architecture](https://docs.flutter.dev/app-architecture/guide): Flutter rehberi view/view-model ile UI durumu ve görünümü ayırır, data katmanını repository ve service olarak böler. Repository domain verisinin kaynağıdır ve cache, hata, retry ve refresh davranışlarını yönetir.
  - `MAIN-RN-SECURITY` [React Native: Security](https://reactnative.dev/docs/security): Resmi rehber AsyncStorage'ı şifrelenmeyen store olarak açıklar ve token/secrets için önermediğini belirtir. Client bundle'a server secret koymamak ve platform secure storage seçeneklerini değerlendirmek gerekir.
  - `TECH-AUTHORIZATION` [OWASP Authorization Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Authorization_Cheat_Sheet.html): OWASP varsayılan reddetmeyi ve kaynağı ne olursa olsun her istekte izin doğrulamayı önerir.
  - `TECH-SECURITY-METHOD` [Spring Security: Method Security](https://docs.spring.io/spring-security/reference/servlet/authorization/method-security.html): EnableMethodSecurity, Spring-managed metotlarda PreAuthorize/PostAuthorize kontrollerini etkinleştirir, security starter bunu kendiliğinden açmaz. Metot yetkilendirmesi Spring AOP tabanlıdır.
  - `TECH-SECURITY-JWT` [Spring Security: OAuth 2.0 Resource Server JWT](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html): Resource Server imza, exp, nbf ve yapılandırılmış issuer kontrolü yapar, scope değerlerini SCOPE_ authority olarak eşler. Audience doğrulaması ayrıca yapılandırılabilir.
  - `kvkk-security` [KVKK: Kişisel Veri Güvenliği Rehberi, Teknik ve İdari Tedbirler](https://www.kvkk.gov.tr/yayinlar/veri_guvenligi_rehberi.pdf): Resmi rehber risklere uygun teknik ve idari tedbirleri ele alır. Veri envanteri, erişim politikaları, veri işleyen ilişkileri, bulut, yedekleme ve saklama/imha başlıkları vardır. İhtiyaç duyulmayan verinin güvenli imhası ve veri işleyen güvenliğinin değerlendirilmesi açıklanır.
  - `kvkk-notice-consent` [KVKK: 18.02.2026 tarihli 2026/347 sayılı ilke kararına ilişkin aydınlatma ve açık rıza duyurusu](https://www.kvkk.gov.tr/Icerik/8710/veri-sorumlulari-tarafindan-acik-riza-ve-aydinlatma-metinlerinin-ayri-ayri-duzenlenmesi-gerektigi-hakkinda-kisisel-verileri-koruma-kurulunun-18-02-2026-tarihli-ve-2026-347-sayili-ilke-kararina-iliskin-kamuoyu-duyurusu): Kurum duyurusu aydınlatma ve açık rıza metinlerinin ayrı düzenlenmesini, aydınlatma için onay/rıza istenmemesini ve metinlerin faaliyete uyarlanmasını vurgular. Veri kategorileri, işleme amaçları ve hukuki sebepler açıkça ifade edilmelidir.
  - `TECH-OPENAPI` [OpenAPI Specification v3.1.1: Schema Object](https://spec.openapis.org/oas/v3.1.1.html): OAS 3.1 Schema Object JSON Schema Draft 2020-12 temellidir. format gibi bazı anahtarlar varsayılan vocabulary içinde doğrudan doğrulama değil annotation niteliğindedir.

#### RM-31: Partner finans ledger, reconciliation ve gerçek KPI binding
*Öncelik P0 · Efor XL · Sorumlu: Backend Finance Engineer · Bağımlılık: RM-14, RM-15, RM-22, RM-25, RM-26, RM-27, RM-28, RM-30 · Karşıladığı gereksinim: R3, R4, R6, R8, R11, R12*

**Amaç:** Komisyon, işletme alacağı ve refund etkisini append-only ledger'da modellemek, gerçek finans KPI'larını RM-22 partner paneline bağlamak. Tahmini satış, tahsilat ve payout ayrı gösterilecek. Sandbox/offline kayıt gerçek işletme ödemesi sayılmayacak, merchant-of-record ve canlı payout kararları çözülmeden canlı satış kapısı açılmayacak.

**Nerede:**
- `payment-service/src/main/java/com/waylo/paymentservice/domain/Payment.java` (mevcut)
- `frontend/src/app/payments/page.tsx` (mevcut)
- `payment-service/src/main/java/com/waylo/paymentservice/finance/PartnerLedgerService.java` (önerilen/yeni)
- `frontend/src/app/partner/finance/page.tsx` (önerilen/yeni)

**Nasıl:**
1. OrderItem snapshot ve verified payment/refund olaylarından balanced append-only entries üret. Komisyon/policy version, currency, supplier org ve source event reference sakla, düzeltmeyi eski satır silmeden reversal ile yap.
2. Reconciliation job'da provider amount, order item, payment/refund ve fulfillment sonuçlarını eşleştir. Eksik/duplicate/unknown kayıtları açıklanabilir exception olarak tut, idempotent replay finans etkisini çoğaltmasın.
3. Organization financial summary yalnız aktörün izinli item'larından hesaplansın. Partner müşteri toplamı, başka hotel item'ı veya kart detayını göremesin, export ve cache aynı scope taşısın.
4. RM-22 beklenen finance KPI'sını gerçek ledger source ve computedAt ile bağla. Tahmini gelir/gerçek tahsilat/payout unavailable ayrımını koru, işletme sözleşmesi ve canlı ödeme kapısını runbook'ta görünür bırak.

**Sözleşme/kontrat:**
- OrganizationFinancialSummary(currency, collected, commission, receivable, refunds, payoutState), getOrganizationFinancialSummary(): GET /api/v1/partner/organizations/{orgId}/financial-summary
- ReconciliationReport, getFinanceExceptions(): GET /api/v1/admin/finance/reconciliation-exceptions

**Kabul ölçütü (bu görev uygulandığında koşulacak testler):**
- Pozitif: sandbox capture ve refund ledger'da dengeli entry/reversal oluşturur, panel aynı item bazlı tutarları gösterir.
- Negatif: replay ikinci alacak yaratmaz, currency karışımı veya provider mismatch unresolved exception üretir.
- Negatif: başka kuruluş özeti/export'u reddedilir, partner diğer item/order payment toplamını göremez.
- E2E: rezervasyon→capture→iptal→async refund→reconciliation sonrası stok ve para açıklanabilir eşleşir, payout bağlanmadıysa unavailable kalır.

**Teslimat:** Append-only finance ledger ve reconciliation modeli; Gerçek partner finance ekranı ve RM-22 KPI binding; Duplicate/refund/isolation kabul raporu ve canlı kapı checklist'i

**İlgili kaynaklar:**
  - `iyzico-submerchant` [iyzico: Alt Üye](https://docs.iyzico.com/urunler/pazaryeri/pazaryeri-entegrasyonu/alt-uye-olusturma): Onboarding API üç alt üye tipi destekler. Alanlar tipe göre değişir. Yanıttaki subMerchantKey satıcıyı ödeme sırasında tanımlar. IBAN ilgili kişi veya şirket unvanıyla uyumlu olmalıdır ve para gönderimi için onay öncesinde tamamlanmalıdır. Sayfa gerçek bilgiler gönderilmesini açıkça ister.
  - `iyzico-payment` [iyzico: Pazaryeri Ödemesi](https://docs.iyzico.com/urunler/pazaryeri/pazaryeri-entegrasyonu/pazaryeri-odemesi): Pazaryeri ödeme kırılımlarında subMerchantKey ve subMerchantPrice gönderilir. İkinci alan alt üyeye gönderilmesi istenen tutardır. Sayfadaki NON3D örneği tüm yöntemlerde alt üye alanlarının eklenmesi gerektiğini belirtir.
  - `iyzico-approval` [iyzico: Onay](https://docs.iyzico.com/urunler/pazaryeri/pazaryeri-entegrasyonu/onay): Belge, ödeme kırılımı onaylanana kadar paranın korumalı havuz hesapta bekletildiğini açıklar. Kırılım onayı ve onay kaldırma paymentTransactionId üzerinden yapılır. Onaylar kod seviyesinde gönderilir, panelde bu fonksiyon bulunmadığı belirtilir.
  - `iyzico-sandbox` [iyzico: Live vs Sandbox](https://docs.iyzico.com/en/getting-started/preliminaries/live-vs-sandbox): Sandbox gerçek işlem veya fonları etkilemeden ödeme akışlarını simüle eder. Sandbox ve canlı API adresleri ile kimlik bilgileri farklıdır. Test kartları yalnız sandbox senaryoları için sunulur.
  - `iyzico-refund` [iyzico: Refund & Cancel](https://docs.iyzico.com/en/advanced/refund-and-cancel): Standart iade paymentTransactionId ve price kullanır, tam veya kısmi olabilir ve kalan iade edilebilir tutarı aşamaz. Belge çok ürünlü sepetlerde Refund V2 kullanımını önermez. Cancel aynı gün ve tam işlem içindir. Karta yansıma bankaya göre değişir.
  - `iyzico-webhook` [iyzico: Webhook](https://docs.iyzico.com/ek-servisler/webhook): Webhook JSON HTTP POST bildirimi kullanır. HTTPS URL istenir. Belge X-IYZ-SIGNATURE-V3 doğrulamasını açıklar ve önceki sürümlerin desteklenmeyeceğini belirtir. İmza özelliğinin hesapta etkin olması gerekir. 2xx gelmeyen bildirimler için tekrar davranışı belgelenmiştir.
  - `MAIN-APPLE-PAYMENT` [Apple App Review Guidelines: 3.1.3(e)](https://developer.apple.com/app-store/review/guidelines/): 3.1.3(e) uygulama dışında tüketilen fiziksel ürün/hizmetlerin ödemesinde in-app purchase dışındaki yöntemleri belirtir.
  - `TECH-OUTBOX` [Debezium: Outbox Event Router](https://debezium.io/documentation/reference/stable/transformations/outbox-event-router.html): Connector outbox tablosundaki değişiklikleri yakalar ve outbox event router SMT ile dönüştürür. Doküman dönüşümün yalnızca outbox olaylarına uygulanmasını, birden çok outbox varsa aynı yapıya sahip olmalarını ister.
  - `TECH-TENANT` [OWASP Multi-Tenant Application Security Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Multi_Tenant_Security_Cheat_Sheet.html): İstemciden gelen tenant kimliği yalnızca seçicidir, doğrulanmış kimlik ve güncel üyelikle yetkilendirilmelidir. PostgreSQL tenant bağlamını transaction-local tutmak, havuzdaki bağlantının sonraki isteğe tenant durumunu sızdırmasını önler.
  - `kvkk-security` [KVKK: Kişisel Veri Güvenliği Rehberi, Teknik ve İdari Tedbirler](https://www.kvkk.gov.tr/yayinlar/veri_guvenligi_rehberi.pdf): Resmi rehber risklere uygun teknik ve idari tedbirleri ele alır. Veri envanteri, erişim politikaları, veri işleyen ilişkileri, bulut, yedekleme ve saklama/imha başlıkları vardır. İhtiyaç duyulmayan verinin güvenli imhası ve veri işleyen güvenliğinin değerlendirilmesi açıklanır.
  - `kvkk-notice-consent` [KVKK: 18.02.2026 tarihli 2026/347 sayılı ilke kararına ilişkin aydınlatma ve açık rıza duyurusu](https://www.kvkk.gov.tr/Icerik/8710/veri-sorumlulari-tarafindan-acik-riza-ve-aydinlatma-metinlerinin-ayri-ayri-duzenlenmesi-gerektigi-hakkinda-kisisel-verileri-koruma-kurulunun-18-02-2026-tarihli-ve-2026-347-sayili-ilke-kararina-iliskin-kamuoyu-duyurusu): Kurum duyurusu aydınlatma ve açık rıza metinlerinin ayrı düzenlenmesini, aydınlatma için onay/rıza istenmemesini ve metinlerin faaliyete uyarlanmasını vurgular. Veri kategorileri, işleme amaçları ve hukuki sebepler açıkça ifade edilmelidir.
  - `TECH-OPENAPI` [OpenAPI Specification v3.1.1: Schema Object](https://spec.openapis.org/oas/v3.1.1.html): OAS 3.1 Schema Object JSON Schema Draft 2020-12 temellidir. format gibi bazı anahtarlar varsayılan vocabulary içinde doğrudan doğrulama değil annotation niteliğindedir.
  - `TECH-PLAYWRIGHT` [Playwright: Authentication](https://playwright.dev/docs/auth): Auth setup browser state dosyasını sonraki testlerde yeniden kullanabilir, dosya hassas cookie/header içerdiğinden repoya konmamalıdır. Paralel testler sunucu durumunu değiştiriyorsa farklı hesaplar gerekir.

### F5: Tüketici mobil uygulaması

#### RM-32: Mobil teknoloji kararını ve feature mimarisini doğrula
*Öncelik P1 · Efor M · Sorumlu: Mobil / ürün · Bağımlılık: RM-07, RM-09, RM-22, RM-31 · Karşıladığı gereksinim: R8, R9*

**Amaç:** Otel web/backend tamamlandıktan sonra son kullanıcı mobilini aynı contract'larla kurmak, framework'ü moda olduğu için seçmemek.

**Nerede:**
- `mobile/ (yeni proje, framework kararından sonra)` (önerilen/yeni)
- `planning/decisions/mobile-framework.md` (önerilen/yeni)

**Nasıl:**
1. Flutter ve React Native'ı ekip yetkinliği, payment checkout/deep link, test, accessibility ve bakım açısından karşılaştır. Mevcut React bilgisi avantaj adayıdır, otomatik seçim değildir.
2. Minimal spike acceptance'ı tasarla: login, public hotel list, secure session, sandbox checkout dönüşü ve gerçek cihaz. Şimdi spike çalıştırılmaz.
3. Feature modülleri auth/hotels/checkout/bookings/reviews/profile, typed API client, repository ve view-state sınırlarını oluşturulacak dosya ağacıyla tanımla.
4. Çevrimdışı cache yalnız gösterim için, fiyat/stok/ödeme online authority. Mobil business logic veya ayrı fiyat motoru yaratma.

**Sözleşme/kontrat:**
- MobileDecisionMatrix + feature boundary diagram; /api/v1 sözleşmeleri aynı kalır.

**Kabul ölçütü (bu görev uygulandığında koşulacak testler):**
- Seçimin gerekçesi ve reddedilen alternatif yazılı, provider SDK/dönüş linki uyumu doğrulanacak kontrol olarak listelenmiş.
- Tüm Must feature'ların navigation ve pending/error/offline durumları tanımlı.
- Seçim backend domain'ini değiştirmenizi gerektirmiyor.

**Teslimat:** Mobil ADR ve dosya ağacı; Cihaz/sandbox spike test planı

**İlgili kaynaklar:**
  - `TECH-MOBILE` [Flutter: Guide to app architecture](https://docs.flutter.dev/app-architecture/guide): Flutter rehberi view/view-model ile UI durumu ve görünümü ayırır, data katmanını repository ve service olarak böler. Repository domain verisinin kaynağıdır ve cache, hata, retry ve refresh davranışlarını yönetir.
  - `MAIN-RN-SECURITY` [React Native: Security](https://reactnative.dev/docs/security): Resmi rehber AsyncStorage'ı şifrelenmeyen store olarak açıklar ve token/secrets için önermediğini belirtir. Client bundle'a server secret koymamak ve platform secure storage seçeneklerini değerlendirmek gerekir.
  - `iyzico-submerchant` [iyzico: Alt Üye](https://docs.iyzico.com/urunler/pazaryeri/pazaryeri-entegrasyonu/alt-uye-olusturma): Onboarding API üç alt üye tipi destekler. Alanlar tipe göre değişir. Yanıttaki subMerchantKey satıcıyı ödeme sırasında tanımlar. IBAN ilgili kişi veya şirket unvanıyla uyumlu olmalıdır ve para gönderimi için onay öncesinde tamamlanmalıdır. Sayfa gerçek bilgiler gönderilmesini açıkça ister.
  - `iyzico-payment` [iyzico: Pazaryeri Ödemesi](https://docs.iyzico.com/urunler/pazaryeri/pazaryeri-entegrasyonu/pazaryeri-odemesi): Pazaryeri ödeme kırılımlarında subMerchantKey ve subMerchantPrice gönderilir. İkinci alan alt üyeye gönderilmesi istenen tutardır. Sayfadaki NON3D örneği tüm yöntemlerde alt üye alanlarının eklenmesi gerektiğini belirtir.
  - `iyzico-approval` [iyzico: Onay](https://docs.iyzico.com/urunler/pazaryeri/pazaryeri-entegrasyonu/onay): Belge, ödeme kırılımı onaylanana kadar paranın korumalı havuz hesapta bekletildiğini açıklar. Kırılım onayı ve onay kaldırma paymentTransactionId üzerinden yapılır. Onaylar kod seviyesinde gönderilir, panelde bu fonksiyon bulunmadığı belirtilir.
  - `iyzico-sandbox` [iyzico: Live vs Sandbox](https://docs.iyzico.com/en/getting-started/preliminaries/live-vs-sandbox): Sandbox gerçek işlem veya fonları etkilemeden ödeme akışlarını simüle eder. Sandbox ve canlı API adresleri ile kimlik bilgileri farklıdır. Test kartları yalnız sandbox senaryoları için sunulur.
  - `iyzico-refund` [iyzico: Refund & Cancel](https://docs.iyzico.com/en/advanced/refund-and-cancel): Standart iade paymentTransactionId ve price kullanır, tam veya kısmi olabilir ve kalan iade edilebilir tutarı aşamaz. Belge çok ürünlü sepetlerde Refund V2 kullanımını önermez. Cancel aynı gün ve tam işlem içindir. Karta yansıma bankaya göre değişir.
  - `iyzico-webhook` [iyzico: Webhook](https://docs.iyzico.com/ek-servisler/webhook): Webhook JSON HTTP POST bildirimi kullanır. HTTPS URL istenir. Belge X-IYZ-SIGNATURE-V3 doğrulamasını açıklar ve önceki sürümlerin desteklenmeyeceğini belirtir. İmza özelliğinin hesapta etkin olması gerekir. 2xx gelmeyen bildirimler için tekrar davranışı belgelenmiştir.
  - `MAIN-APPLE-PAYMENT` [Apple App Review Guidelines: 3.1.3(e)](https://developer.apple.com/app-store/review/guidelines/): 3.1.3(e) uygulama dışında tüketilen fiziksel ürün/hizmetlerin ödemesinde in-app purchase dışındaki yöntemleri belirtir.

#### RM-33: Mobil oturum, profil ve güvenli kullanıcı durumu
*Öncelik P1 · Efor L · Sorumlu: Mobil + backend · Bağımlılık: RM-12, RM-13, RM-32 · Karşıladığı gereksinim: R2, R3, R8*

**Amaç:** Consumer uygulamasında gerçek kimlik/session akışı, güvenli token yaşam döngüsü ve kullanıcıya ait profil.

**Nerede:**
- `mobile/ features/auth ve features/profile (framework ADR'ye göre lib/ veya src/)` (önerilen/yeni)
- `auth-service/src/main/java` (mevcut)

**Nasıl:**
1. RM12 contract'larına register/login/verification/refresh/logout/me typed adapter yaz. Desteklenmeyen sosyal login/MFA butonlarını success simüle ederek açma.
2. Token'ları platform güvenli storage abstraction ile tut, memory/navigation state'i logout ve hesap değişiminde temizle. Access/refresh farklı error/retry yollarına sahip olsun.
3. Deep-link doğrulama ve sandbox checkout dönüşünde route allowlist/ownership uygula. E-posta kodunu veya token'ı loglama.
4. Profil güncellemesi API readback ile görünür. Hesap silme yalnız retention-aware talep, doğrudan bütün veriyi silen client action değil.

**Sözleşme/kontrat:**
- AuthSessionStore, refreshSingleFlight(), getOwnedProfile(); mevcut RM12 OpenAPI erişim/oturum sözleşmesi.

**Kabul ölçütü (bu görev uygulandığında koşulacak testler):**
- Uygulama yeniden açılışında geçerli oturum restore edilir, revoke/expired token protected işlem yapamaz.
- A hesabından logout/B login sonrasında A'nın cached booking/profile verisi görünmez.
- Aynı anda iki expired request tek güvenli refresh ve deterministik sonucu üretir, döngüsel login retry olmaz.

**Teslimat:** Gerçek auth/profile feature'ları; Session ve deep-link cihaz testleri

**İlgili kaynaklar:**
  - `TECH-MOBILE` [Flutter: Guide to app architecture](https://docs.flutter.dev/app-architecture/guide): Flutter rehberi view/view-model ile UI durumu ve görünümü ayırır, data katmanını repository ve service olarak böler. Repository domain verisinin kaynağıdır ve cache, hata, retry ve refresh davranışlarını yönetir.
  - `MAIN-RN-SECURITY` [React Native: Security](https://reactnative.dev/docs/security): Resmi rehber AsyncStorage'ı şifrelenmeyen store olarak açıklar ve token/secrets için önermediğini belirtir. Client bundle'a server secret koymamak ve platform secure storage seçeneklerini değerlendirmek gerekir.
  - `TECH-AUTHORIZATION` [OWASP Authorization Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Authorization_Cheat_Sheet.html): OWASP varsayılan reddetmeyi ve kaynağı ne olursa olsun her istekte izin doğrulamayı önerir.
  - `TECH-SECURITY-METHOD` [Spring Security: Method Security](https://docs.spring.io/spring-security/reference/servlet/authorization/method-security.html): EnableMethodSecurity, Spring-managed metotlarda PreAuthorize/PostAuthorize kontrollerini etkinleştirir, security starter bunu kendiliğinden açmaz. Metot yetkilendirmesi Spring AOP tabanlıdır.
  - `TECH-SECURITY-JWT` [Spring Security: OAuth 2.0 Resource Server JWT](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html): Resource Server imza, exp, nbf ve yapılandırılmış issuer kontrolü yapar, scope değerlerini SCOPE_ authority olarak eşler. Audience doğrulaması ayrıca yapılandırılabilir.
  - `kvkk-security` [KVKK: Kişisel Veri Güvenliği Rehberi, Teknik ve İdari Tedbirler](https://www.kvkk.gov.tr/yayinlar/veri_guvenligi_rehberi.pdf): Resmi rehber risklere uygun teknik ve idari tedbirleri ele alır. Veri envanteri, erişim politikaları, veri işleyen ilişkileri, bulut, yedekleme ve saklama/imha başlıkları vardır. İhtiyaç duyulmayan verinin güvenli imhası ve veri işleyen güvenliğinin değerlendirilmesi açıklanır.
  - `kvkk-notice-consent` [KVKK: 18.02.2026 tarihli 2026/347 sayılı ilke kararına ilişkin aydınlatma ve açık rıza duyurusu](https://www.kvkk.gov.tr/Icerik/8710/veri-sorumlulari-tarafindan-acik-riza-ve-aydinlatma-metinlerinin-ayri-ayri-duzenlenmesi-gerektigi-hakkinda-kisisel-verileri-koruma-kurulunun-18-02-2026-tarihli-ve-2026-347-sayili-ilke-kararina-iliskin-kamuoyu-duyurusu): Kurum duyurusu aydınlatma ve açık rıza metinlerinin ayrı düzenlenmesini, aydınlatma için onay/rıza istenmemesini ve metinlerin faaliyete uyarlanmasını vurgular. Veri kategorileri, işleme amaçları ve hukuki sebepler açıkça ifade edilmelidir.

#### RM-34: Mobil gerçek otel arama, detay ve oda seçimi
*Öncelik P1 · Efor L · Sorumlu: Mobil · Bağımlılık: RM-23, RM-32, RM-33 · Karşıladığı gereksinim: R1, R5, R8*

**Amaç:** Mock görsel/fiyat/yıldız/özellik fallback'lerini kaldırıp gerçek yayın ve teklif verisini kullanıcıya sunmak.

**Nerede:**
- `mobile/ features/hotels (framework ADR'ye göre lib/ veya src/)` (önerilen/yeni)
- `hotel-service/src/main/java` (mevcut)

**Nasıl:**
1. Tarih, adults/childrenAges ve destinasyon aramasını RM23 public API'ye bağla. Validation ve query state UI'da korunur.
2. Published hotel detail, gerçek oda özellik/görselleri ve her offer'ın toplam fiyat/iptal koşulunu göster. Eksik veri için açık unknown/placeholder, uydurma pool/spa/puan/fiyat yok.
3. Pagination, loading, empty, retry, stop-sell ve quote-expired akışlarını tasarla. Search sonucu envanter teyidi değildir.
4. Favoriler harici backend hazır değilse scope dışı tut. Map provider seçilmediyse lisanssız map/photo scraping yapma.

**Sözleşme/kontrat:**
- GET /api/v1/hotels, GET /api/v1/hotels/{id}, GET /api/v1/hotels/{id}/availability; offerId/version/expiry.

**Kabul ölçütü (bu görev uygulandığında koşulacak testler):**
- İşletme takvim fiyatını değiştirince yeni arama/quote yeni fiyatı gösterir.
- Unpublished/suspended otel public aramada görünmez, unknown price ile checkout açılmaz.
- Checkout tarihi satılmayan gece olarak uygulanır, kapasiteyi aşan kişi sayısı uygun teklif üretmez.

**Teslimat:** API tabanlı arama/detay/offer ekranları; API-visible truth regresyon testleri

**İlgili kaynaklar:**
  - `TECH-MOBILE` [Flutter: Guide to app architecture](https://docs.flutter.dev/app-architecture/guide): Flutter rehberi view/view-model ile UI durumu ve görünümü ayırır, data katmanını repository ve service olarak böler. Repository domain verisinin kaynağıdır ve cache, hata, retry ve refresh davranışlarını yönetir.
  - `MAIN-RN-SECURITY` [React Native: Security](https://reactnative.dev/docs/security): Resmi rehber AsyncStorage'ı şifrelenmeyen store olarak açıklar ve token/secrets için önermediğini belirtir. Client bundle'a server secret koymamak ve platform secure storage seçeneklerini değerlendirmek gerekir.
  - `MAIN-BOOKING-ARI` [Booking.com: Create or update inventory, rates and restrictions](https://developers.booking.com/connectivity/docs/b_xml-availability): Resmi örnek stok sayısını oda tipi seviyesinde bütün rate planlar için ortak tutar. Fiyat ve kısıtlar oda tipi/rate plan/tarih boyutlarıyla güncellenir, tarih aralığının son günü dahil değildir.
  - `TECH-OPENAPI` [OpenAPI Specification v3.1.1: Schema Object](https://spec.openapis.org/oas/v3.1.1.html): OAS 3.1 Schema Object JSON Schema Draft 2020-12 temellidir. format gibi bazı anahtarlar varsayılan vocabulary içinde doğrudan doğrulama değil annotation niteliğindedir.

#### RM-35: Mobil checkout, teyit, rezervasyon takibi ve iptal
*Öncelik P1 · Efor XL · Sorumlu: Mobil + payment/backend · Bağımlılık: RM-25, RM-26, RM-27, RM-28, RM-30, RM-34 · Karşıladığı gereksinim: R6, R8, R11, R12*

**Amaç:** Uygulamadan gerçek backend ve sandbox payment ile sonuca giden, belirsiz işlemi başarı gibi göstermeyen akış.

**Nerede:**
- `mobile/ features/checkout ve features/bookings` (önerilen/yeni)
- `package-booking-service/src/main/java` (mevcut)
- `payment-service/src/main/java` (mevcut)

**Nasıl:**
1. Quote revalidation sonrası unique idempotency key ile order/payment attempt oluştur. Double tap ve yeniden açılış aynı owned order'ı takip eder.
2. Provider hosted checkout dönüşünü ödeme kanıtı sayma, backend owned order status sorgula. PAYMENT_PENDING/CONFIRMING/CONFIRMED/FAILED/REFUND_PENDING ekranlarını ayır.
3. Owned order/item detail, commercial snapshot ve cancel-policy açıklamasını göster. Partner paneli aynı hotel reservation'ı görmeli.
4. İptal isteğinde policy'ye göre sonuç ve refund durumu göster. Ağ kopmasında aynı isteği güvenli şekilde sorgula/tekrar et, yeni charge üretme.

**Sözleşme/kontrat:**
- POST /api/v1/orders, POST /api/v1/orders/{id}/payment-attempts, GET /api/v1/me/orders/{id}, POST /api/v1/me/orders/{id}/cancellation-requests.

**Kabul ölçütü (bu görev uygulandığında koşulacak testler):**
- Sandbox başarılı akış confirmed rezervasyon ve aynı ID'li partner kaydı üretir.
- Ödeme ekranından dönüp uygulama kapanması/tekrar açılması ikinci tahsilat veya sipariş üretmez.
- Late/failed payment ve async refund doğru pending/error sonucu gösterir, notification failure booking'i gizlemez.

**Teslimat:** Gerçek checkout/order/cancel feature'ları; Double-tap, resume ve pending-state cihaz kanıtı

**İlgili kaynaklar:**
  - `TECH-MOBILE` [Flutter: Guide to app architecture](https://docs.flutter.dev/app-architecture/guide): Flutter rehberi view/view-model ile UI durumu ve görünümü ayırır, data katmanını repository ve service olarak böler. Repository domain verisinin kaynağıdır ve cache, hata, retry ve refresh davranışlarını yönetir.
  - `MAIN-RN-SECURITY` [React Native: Security](https://reactnative.dev/docs/security): Resmi rehber AsyncStorage'ı şifrelenmeyen store olarak açıklar ve token/secrets için önermediğini belirtir. Client bundle'a server secret koymamak ve platform secure storage seçeneklerini değerlendirmek gerekir.
  - `iyzico-submerchant` [iyzico: Alt Üye](https://docs.iyzico.com/urunler/pazaryeri/pazaryeri-entegrasyonu/alt-uye-olusturma): Onboarding API üç alt üye tipi destekler. Alanlar tipe göre değişir. Yanıttaki subMerchantKey satıcıyı ödeme sırasında tanımlar. IBAN ilgili kişi veya şirket unvanıyla uyumlu olmalıdır ve para gönderimi için onay öncesinde tamamlanmalıdır. Sayfa gerçek bilgiler gönderilmesini açıkça ister.
  - `iyzico-payment` [iyzico: Pazaryeri Ödemesi](https://docs.iyzico.com/urunler/pazaryeri/pazaryeri-entegrasyonu/pazaryeri-odemesi): Pazaryeri ödeme kırılımlarında subMerchantKey ve subMerchantPrice gönderilir. İkinci alan alt üyeye gönderilmesi istenen tutardır. Sayfadaki NON3D örneği tüm yöntemlerde alt üye alanlarının eklenmesi gerektiğini belirtir.
  - `iyzico-approval` [iyzico: Onay](https://docs.iyzico.com/urunler/pazaryeri/pazaryeri-entegrasyonu/onay): Belge, ödeme kırılımı onaylanana kadar paranın korumalı havuz hesapta bekletildiğini açıklar. Kırılım onayı ve onay kaldırma paymentTransactionId üzerinden yapılır. Onaylar kod seviyesinde gönderilir, panelde bu fonksiyon bulunmadığı belirtilir.
  - `iyzico-sandbox` [iyzico: Live vs Sandbox](https://docs.iyzico.com/en/getting-started/preliminaries/live-vs-sandbox): Sandbox gerçek işlem veya fonları etkilemeden ödeme akışlarını simüle eder. Sandbox ve canlı API adresleri ile kimlik bilgileri farklıdır. Test kartları yalnız sandbox senaryoları için sunulur.
  - `iyzico-refund` [iyzico: Refund & Cancel](https://docs.iyzico.com/en/advanced/refund-and-cancel): Standart iade paymentTransactionId ve price kullanır, tam veya kısmi olabilir ve kalan iade edilebilir tutarı aşamaz. Belge çok ürünlü sepetlerde Refund V2 kullanımını önermez. Cancel aynı gün ve tam işlem içindir. Karta yansıma bankaya göre değişir.
  - `iyzico-webhook` [iyzico: Webhook](https://docs.iyzico.com/ek-servisler/webhook): Webhook JSON HTTP POST bildirimi kullanır. HTTPS URL istenir. Belge X-IYZ-SIGNATURE-V3 doğrulamasını açıklar ve önceki sürümlerin desteklenmeyeceğini belirtir. İmza özelliğinin hesapta etkin olması gerekir. 2xx gelmeyen bildirimler için tekrar davranışı belgelenmiştir.
  - `MAIN-APPLE-PAYMENT` [Apple App Review Guidelines: 3.1.3(e)](https://developer.apple.com/app-store/review/guidelines/): 3.1.3(e) uygulama dışında tüketilen fiziksel ürün/hizmetlerin ödemesinde in-app purchase dışındaki yöntemleri belirtir.
  - `TECH-OPENAPI` [OpenAPI Specification v3.1.1: Schema Object](https://spec.openapis.org/oas/v3.1.1.html): OAS 3.1 Schema Object JSON Schema Draft 2020-12 temellidir. format gibi bazı anahtarlar varsayılan vocabulary içinde doğrudan doğrulama değil annotation niteliğindedir.

#### RM-36: Mobil doğrulanmış yorum ve destek erişimi
*Öncelik P1 · Efor M · Sorumlu: Mobil + hotel/backend · Bağımlılık: RM-29, RM-30, RM-35 · Karşıladığı gereksinim: R3, R7, R8, R11*

**Amaç:** Kullanıcının gerçek tamamlanmış konaklaması sonrası puanlama ve yardım akışını uçtan uca bitirmek.

**Nerede:**
- `mobile/ features/reviews ve support` (önerilen/yeni)
- `hotel-service/src/main/java` (mevcut)

**Nasıl:**
1. Eligibility backend'den okunur. Completed stay review formu 1..5 puan, text, moderation status ve duplicate guard içerir.
2. Public review list ve owner response'u farklı aktörlerle göster. Yorumsuz otelde puan null ve count0, sahte8.9/5.0 yok.
3. Destek vakasını owned order context ile açılacak şekilde tanımla. Destek iletişim kanalı/çalışma saatleri RM06 playbook'una bağlı, sahte çalışan live-chat yok.
4. No-show, cancelled ve başka kullanıcının rezervasyonunda review butonu çıkmasa bile direkt API denemesi reddedilir.

**Sözleşme/kontrat:**
- POST /api/v1/me/hotel-reservations/{id}/review, GET /api/v1/hotels/{id}/reviews; destek sağlayıcı seçilmeden channel capability=false.

**Kabul ölçütü (bu görev uygulandığında koşulacak testler):**
- Checkout completed→review→moderation→public skor→partner response zinciri gözlenir.
- Owner müşterinin puanını değiştirip silemez, yalnız yanıt/şikâyet işlevi vardır.
- Review duplicate ve yetkisiz request satır eklemez, destek sadece kendi order bilgisini kullanır.

**Teslimat:** Review/support feature'ları; İşletme ve müşteri rollerini kapsayan yorum akışı testi

**İlgili kaynaklar:**
  - `TECH-MOBILE` [Flutter: Guide to app architecture](https://docs.flutter.dev/app-architecture/guide): Flutter rehberi view/view-model ile UI durumu ve görünümü ayırır, data katmanını repository ve service olarak böler. Repository domain verisinin kaynağıdır ve cache, hata, retry ve refresh davranışlarını yönetir.
  - `MAIN-RN-SECURITY` [React Native: Security](https://reactnative.dev/docs/security): Resmi rehber AsyncStorage'ı şifrelenmeyen store olarak açıklar ve token/secrets için önermediğini belirtir. Client bundle'a server secret koymamak ve platform secure storage seçeneklerini değerlendirmek gerekir.
  - `e1` [Log in to your Expedia Group portal](https://partner.expediagroup.com/en-us/log-in): Resmi portal tanıtımı, otel ortaklarının Partner Central üzerinden rekabet içgörüleri inceleyebileceğini, envanter yönetebileceğini ve fiyatlarını optimize edebileceğini belirtir.
  - `e2` [Welcome | EG Connectivity Hub](https://developers.expediagroup.com/supply/lodging): PMS, channel-manager ve CRS sağlayıcıları için tesis listeleme ve yönetim bağlantıları tanıtılır. Entegrasyon süreci lisans ve PCI incelemesi ile geliştirme, test ve soft launch aşamalarını içerir. Sayfa bireysel tesislerden doğrudan API bağlantısı kabul edilmediğini belirtir.
  - `MAIN-EXPEDIA-API` [Expedia Connectivity: Lodging APIs overview](https://connectivityportal.expediagroup.com/documentation/expedia): Resmi doküman availability/rates, reservation management ve product management sınırlarını ayırır. Product API oda tipi/rate plan yönetimini, review API onaylı yorumlara yanıt ve puan agregasını tanıtır.
  - `TECH-AUTHORIZATION` [OWASP Authorization Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Authorization_Cheat_Sheet.html): OWASP varsayılan reddetmeyi ve kaynağı ne olursa olsun her istekte izin doğrulamayı önerir.
  - `TECH-SECURITY-METHOD` [Spring Security: Method Security](https://docs.spring.io/spring-security/reference/servlet/authorization/method-security.html): EnableMethodSecurity, Spring-managed metotlarda PreAuthorize/PostAuthorize kontrollerini etkinleştirir, security starter bunu kendiliğinden açmaz. Metot yetkilendirmesi Spring AOP tabanlıdır.
  - `TECH-SECURITY-JWT` [Spring Security: OAuth 2.0 Resource Server JWT](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html): Resource Server imza, exp, nbf ve yapılandırılmış issuer kontrolü yapar, scope değerlerini SCOPE_ authority olarak eşler. Audience doğrulaması ayrıca yapılandırılabilir.

### F6: Mock temizliği ve bütün akış kabul testi

#### RM-37: Mock temizliği, contract ve tenant güvenlik regresyonu
*Öncelik P0 · Efor L · Sorumlu: QA + backend/frontend/mobile · Bağımlılık: RM-14, RM-16, RM-31, RM-36 · Karşıladığı gereksinim: R3, R4, R5, R10, R11*

**Amaç:** 'Ekran açılıyor' yerine kullanıcıya gösterilen iş bilgilerinin API gerçekliğiyle ve yetki sınırlarıyla tutarlı olduğunu kanıtlamak.

**Nerede:**
- `frontend/src/app` (mevcut)
- `frontend/tests/e2e/partner (yeni)` (önerilen/yeni)
- `scripts/acceptance (yeni)` (önerilen/yeni)
- `mobile/ integration tests (yeni)` (önerilen/yeni)

**Nasıl:**
1. Önceki 124 business display envanterini hotel-MVP scope'una filtrele. Her otel/partner/mobil KPI, fiyat, özellik, eylem ve fallback için API owner/expected state eşleştir, flight/car kapsam dışı diye dürüstçe işaretle.
2. Fixture, dekoratif placeholder, ikon ve skeleton'ı business mock'tan ayır. Yetkisiz, missing, unknown ve zero state'lerini ayrı doğrula.
3. OwnerA/managerA/staffA/ownerB/customer/admin kullanıcıları ile GET/PATCH/file/export/cache/invitation/legacy URL ve direct-service authorization matrix çalıştır.
4. Schema sınıflandırmasından RLS coverage keşfet, request role non-superuser/non-bypass ve reused connection testi çalıştır. Public projection sensitive field redaction'ı doğrula.

**Sözleşme/kontrat:**
- RequirementEvidence: RM-ID, R-ID, publicOutput, command, environment, observedResult, artifact; manifestDrivenMockCheck().

**Kabul ölçütü (bu görev uygulandığında koşulacak testler):**
- MVP kapsamındaki her business display için gerçek API değeri veya açık unknown vardır, no-op action bitmiş sayılmaz.
- Tenant negatif testlerinde diğer kuruluş verisi sıfır sızıntı, unauthorized mutation sıfır etki.
- Contract şemaları ve migration readback gerçek uygulama public interfaces üzerinden geçer. Test stub'ı canlı entegrasyon başarısı diye raporlanmaz.

**Teslimat:** MVP mock-output manifest ve sonuç raporu; Contract/authorization/RLS kanıtları

**İlgili kaynaklar:**
  - `TECH-PLAYWRIGHT` [Playwright: Authentication](https://playwright.dev/docs/auth): Auth setup browser state dosyasını sonraki testlerde yeniden kullanabilir, dosya hassas cookie/header içerdiğinden repoya konmamalıdır. Paralel testler sunucu durumunu değiştiriyorsa farklı hesaplar gerekir.
  - `TECH-TESTCONTAINERS` [Testcontainers for Java: Postgres Module](https://java.testcontainers.org/modules/databases/postgres/): PostgreSQLContainer Java içinden seçilen PostgreSQL image ile geçici veritabanı başlatır, doküman alternatif jdbc:tc bağlantı mekanizmasını da gösterir.
  - `TECH-TENANT` [OWASP Multi-Tenant Application Security Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Multi_Tenant_Security_Cheat_Sheet.html): İstemciden gelen tenant kimliği yalnızca seçicidir, doğrulanmış kimlik ve güncel üyelikle yetkilendirilmelidir. PostgreSQL tenant bağlamını transaction-local tutmak, havuzdaki bağlantının sonraki isteğe tenant durumunu sızdırmasını önler.
  - `TECH-PG-RLS` [PostgreSQL 16: Row Security Policies](https://www.postgresql.org/docs/16/ddl-rowsecurity.html): RLS görünür satırları ve yazılabilecek satırları ayrı ifadelerle sınırlayabilir. Superuser ve BYPASSRLS rolleri her zaman, tablo sahibi normalde politikayı aşar, FORCE yalnızca sahibi politikaya tabi kılar.

#### RM-38: Tam cihaz akışı, stok yarışı ve recovery kabul kapısı
*Öncelik P0 · Efor XL · Sorumlu: QA / operasyon · Bağımlılık: RM-37 · Karşıladığı gereksinim: R1, R2, R3, R4, R5, R6, R7, R8, R9, R10, R11, R12*

**Amaç:** Hotel B2B ve consumer mobil bütün sonucunu son değişikliklerle birlikte yeniden test etmek.

**Nerede:**
- `scripts/acceptance/full-hotel-flow (yeni)` (önerilen/yeni)
- `docker-compose.yml` (mevcut)
- `mobile/ integration tests (yeni)` (önerilen/yeni)

**Nasıl:**
1. İzole staging/test ortamında iki işletme ve bir müşteriyle onboarding→admin approval→calendar→mobile search→sandbox payment→partner booking→checkout→review→cancel/refund akışlarını çalıştır.
2. Son oda için eşzamanlı hold yarışları, çok gece partial failure, duplicate/late callback ve crash/restart/outbox replay hata enjeksiyonu uygula. Tarihsel SMTP replay'e gerçek alıcı kullanma, test sink/allowlist oluştur.
3. DB backup restore, tenant scoped export/restore ve rollback/flag-disable prova yap. Sağlık endpointi ile işsel reconciliation ayrı raporlanır.
4. Latency/load, pilot trafik tavanı, RPO/RTO ve queue lag hedefleri RM06/RM10 ile onaylanır. Şimdi gelişi güzel production SLA yazılmaz.

**Sözleşme/kontrat:**
- AcceptanceReport(R1..R12, artifacts, observedStock, observedMoney, deliveryState, blockers); public device + backend workflow.

**Kabul ölçütü (bu görev uygulandığında koşulacak testler):**
- R1-R12'nin hepsi gerçek app/API/DB akışında kanıtlı sonuç içerir, blocked testler passed sayılmaz.
- Race/replay testlerinde negatif stok, duplicate charge, cross-tenant veri ve yanlış confirmed sonuç sıfırdır.
- Restore/restart sonrası aktif holds/orders/financial kayıtları reconciliation ile tutarlı, kurtarma çıktısı gözlenmiştir.

**Teslimat:** Cihaz/E2E kanıt paketi; Load/failure/recovery raporu; Go/no-go blocker listesi

**İlgili kaynaklar:**
  - `TECH-PLAYWRIGHT` [Playwright: Authentication](https://playwright.dev/docs/auth): Auth setup browser state dosyasını sonraki testlerde yeniden kullanabilir, dosya hassas cookie/header içerdiğinden repoya konmamalıdır. Paralel testler sunucu durumunu değiştiriyorsa farklı hesaplar gerekir.
  - `TECH-TESTCONTAINERS` [Testcontainers for Java: Postgres Module](https://java.testcontainers.org/modules/databases/postgres/): PostgreSQLContainer Java içinden seçilen PostgreSQL image ile geçici veritabanı başlatır, doküman alternatif jdbc:tc bağlantı mekanizmasını da gösterir.
  - `TECH-PG-LOCK` [PostgreSQL 16: Explicit Locking](https://www.postgresql.org/docs/16/explicit-locking.html): SELECT FOR UPDATE seçilen satırların rakip güncellenmesini, silinmesini ve çatışan kilitlenmesini transaction sonuna kadar engeller. Normal okumaları engellemez, yüksek izolasyonda değişmiş satırı kilitlemek hata verebilir.
  - `TECH-OUTBOX` [Debezium: Outbox Event Router](https://debezium.io/documentation/reference/stable/transformations/outbox-event-router.html): Connector outbox tablosundaki değişiklikleri yakalar ve outbox event router SMT ile dönüştürür. Doküman dönüşümün yalnızca outbox olaylarına uygulanmasını, birden çok outbox varsa aynı yapıya sahip olmalarını ister.
  - `TECH-MOBILE` [Flutter: Guide to app architecture](https://docs.flutter.dev/app-architecture/guide): Flutter rehberi view/view-model ile UI durumu ve görünümü ayırır, data katmanını repository ve service olarak böler. Repository domain verisinin kaynağıdır ve cache, hata, retry ve refresh davranışlarını yönetir.
  - `MAIN-RN-SECURITY` [React Native: Security](https://reactnative.dev/docs/security): Resmi rehber AsyncStorage'ı şifrelenmeyen store olarak açıklar ve token/secrets için önermediğini belirtir. Client bundle'a server secret koymamak ve platform secure storage seçeneklerini değerlendirmek gerekir.

### F7: Kontrollü beta, gerçek pilot ve devam/pivot kararı

#### RM-39: Onaylı kapalı beta ve sınırlı gerçek pilot
*Öncelik P0 · Efor L · Sorumlu: Kurucu + operasyon + QA · Bağımlılık: RM-05, RM-06, RM-38 · Karşıladığı gereksinim: R6, R8, R11, R12*

**Amaç:** Sandbox sonucu ile gerçek hizmeti ayırarak, kontrollü az sayıda işletme ve davetli kullanıcıyla ürünü işletmek.

**Nerede:**
- `planning/operations/live-pilot-checklist.md` (önerilen/yeni)
- `infra/` (mevcut)
- `mobile/ beta release config (yeni)` (önerilen/yeni)

**Nasıl:**
1. Kapalı beta dağıtımı, mağaza hesap/organizasyon bilgisi, privacy/deletion URL, review credentials ve checkout deep-link kontrollerini resmi platform kaynaklarına göre hazırla. Hesap açma/ücret ödeme/yayınlama bu planın eylemi değildir.
2. Gerçek provider kabulü, uzman doğrulamaları, partner sözleşmesi, allotment, refund ve payout readiness kapılarını tamamla. Kullanıcıdan canlı işlem için ayrı açık onay al.
3. 3-5 hedef otelle davetli pilot yap, rezervasyon/ciro/tarih kapsamı limitli olsun. İlk gerçek işlem ve işletme teyidini audit ile takip et. Gerçek ödeme/iletişim plansız tetiklenmez.
4. Cross-tenant leak, overbooking, duplicate charge veya payment mismatch kritikse satış flag'i kapat, mevcut order/support/refund takibini sürdür. Go-live mevcut bookings'in kaybolması demek değildir.

**Sözleşme/kontrat:**
- LiveGate: businessApprovals, paymentAcceptance, providerKeysIsolation, pilotLimits, stopOwner, incidentRunbook, customerConsent.

**Kabul ölçütü (bu görev uygulandığında koşulacak testler):**
- Sandbox beta ile real paid booking ayrı metrik/ortamda görülür.
- Canlı ilk booking için payment→confirmation→notification→hotel acknowledgement→financial reconciliation kanıtı vardır.
- Durdurma provası yeni satışı engeller, mevcut rezervasyon ve refund erişimini korur.

**Teslimat:** Beta dağıtım kontrol listesi; Onaylı pilot go/no-go kaydı; İlk gerçek işlem kanıtı

**İlgili kaynaklar:**
  - `MAIN-APPLE-BETA` [Apple: TestFlight Overview](https://developer.apple.com/help/app-store-connect/test-a-beta-version/testflight-overview/): TestFlight beta build dağıtımı, internal/external tester yönetimi ve feedback toplama akışını açıklar. İlk external beta build review sürecine girebilir.
  - `MAIN-PLAY-BETA` [Google Play: Set up an open, closed, or internal test](https://support.google.com/googleplay/android-developer/answer/9845334?hl=en): Play Console internal, closed ve open test tracklerini ayrı tanımlar. Internal testten küçük closed test grubuna ilerlemeyi önerir, yeni personal accountlar için ek test şartlarına işaret eder.
  - `iyzico-submerchant` [iyzico: Alt Üye](https://docs.iyzico.com/urunler/pazaryeri/pazaryeri-entegrasyonu/alt-uye-olusturma): Onboarding API üç alt üye tipi destekler. Alanlar tipe göre değişir. Yanıttaki subMerchantKey satıcıyı ödeme sırasında tanımlar. IBAN ilgili kişi veya şirket unvanıyla uyumlu olmalıdır ve para gönderimi için onay öncesinde tamamlanmalıdır. Sayfa gerçek bilgiler gönderilmesini açıkça ister.
  - `iyzico-payment` [iyzico: Pazaryeri Ödemesi](https://docs.iyzico.com/urunler/pazaryeri/pazaryeri-entegrasyonu/pazaryeri-odemesi): Pazaryeri ödeme kırılımlarında subMerchantKey ve subMerchantPrice gönderilir. İkinci alan alt üyeye gönderilmesi istenen tutardır. Sayfadaki NON3D örneği tüm yöntemlerde alt üye alanlarının eklenmesi gerektiğini belirtir.
  - `iyzico-approval` [iyzico: Onay](https://docs.iyzico.com/urunler/pazaryeri/pazaryeri-entegrasyonu/onay): Belge, ödeme kırılımı onaylanana kadar paranın korumalı havuz hesapta bekletildiğini açıklar. Kırılım onayı ve onay kaldırma paymentTransactionId üzerinden yapılır. Onaylar kod seviyesinde gönderilir, panelde bu fonksiyon bulunmadığı belirtilir.
  - `iyzico-sandbox` [iyzico: Live vs Sandbox](https://docs.iyzico.com/en/getting-started/preliminaries/live-vs-sandbox): Sandbox gerçek işlem veya fonları etkilemeden ödeme akışlarını simüle eder. Sandbox ve canlı API adresleri ile kimlik bilgileri farklıdır. Test kartları yalnız sandbox senaryoları için sunulur.
  - `iyzico-refund` [iyzico: Refund & Cancel](https://docs.iyzico.com/en/advanced/refund-and-cancel): Standart iade paymentTransactionId ve price kullanır, tam veya kısmi olabilir ve kalan iade edilebilir tutarı aşamaz. Belge çok ürünlü sepetlerde Refund V2 kullanımını önermez. Cancel aynı gün ve tam işlem içindir. Karta yansıma bankaya göre değişir.
  - `iyzico-webhook` [iyzico: Webhook](https://docs.iyzico.com/ek-servisler/webhook): Webhook JSON HTTP POST bildirimi kullanır. HTTPS URL istenir. Belge X-IYZ-SIGNATURE-V3 doğrulamasını açıklar ve önceki sürümlerin desteklenmeyeceğini belirtir. İmza özelliğinin hesapta etkin olması gerekir. 2xx gelmeyen bildirimler için tekrar davranışı belgelenmiştir.
  - `MAIN-APPLE-PAYMENT` [Apple App Review Guidelines: 3.1.3(e)](https://developer.apple.com/app-store/review/guidelines/): 3.1.3(e) uygulama dışında tüketilen fiziksel ürün/hizmetlerin ödemesinde in-app purchase dışındaki yöntemleri belirtir.
  - `kvkk-security` [KVKK: Kişisel Veri Güvenliği Rehberi, Teknik ve İdari Tedbirler](https://www.kvkk.gov.tr/yayinlar/veri_guvenligi_rehberi.pdf): Resmi rehber risklere uygun teknik ve idari tedbirleri ele alır. Veri envanteri, erişim politikaları, veri işleyen ilişkileri, bulut, yedekleme ve saklama/imha başlıkları vardır. İhtiyaç duyulmayan verinin güvenli imhası ve veri işleyen güvenliğinin değerlendirilmesi açıklanır.
  - `kvkk-notice-consent` [KVKK: 18.02.2026 tarihli 2026/347 sayılı ilke kararına ilişkin aydınlatma ve açık rıza duyurusu](https://www.kvkk.gov.tr/Icerik/8710/veri-sorumlulari-tarafindan-acik-riza-ve-aydinlatma-metinlerinin-ayri-ayri-duzenlenmesi-gerektigi-hakkinda-kisisel-verileri-koruma-kurulunun-18-02-2026-tarihli-ve-2026-347-sayili-ilke-kararina-iliskin-kamuoyu-duyurusu): Kurum duyurusu aydınlatma ve açık rıza metinlerinin ayrı düzenlenmesini, aydınlatma için onay/rıza istenmemesini ve metinlerin faaliyete uyarlanmasını vurgular. Veri kategorileri, işleme amaçları ve hukuki sebepler açıkça ifade edilmelidir.
  - `tursab-licensing` [TÜRSAB: Yeni İşletme Belgesi Başvurusu](https://www.tursab.org.tr/yeni-isletme-belgesi-basvurusu): Resmi sayfa seyahat acentası işletme belgesi isteyen tüzel kişiler için Bakanlığa unvan başvurusu, TÜRSAB incelemesi ve Bakanlık belge sürecini açıklar. Seyahat acentası sıfatının üyelik ve Bakanlık işletme belgesinin teslimiyle kazanıldığı belirtilir.

#### RM-40: Pilot ölçümü, devam/pivot kararı ve yeni dikey kapısı
*Öncelik P0 · Efor M · Sorumlu: Kurucu / ürün · Bağımlılık: RM-03, RM-04, RM-39 · Karşıladığı gereksinim: R9, R10, R11, R12*

**Amaç:** MVP'nin teknik teslimi değil iş hipotezini sınamak ve uçuş/araç eklemek için kanıta dayalı karar almak.

**Nerede:**
- `planning/product/pilot-scorecard.md` (önerilen/yeni)
- `planning/product/next-vertical-decision.md` (önerilen/yeni)

**Nasıl:**
1. Funnel ölç: hotel onboarding/activation, rate freshness, search→offer→quote→payment→confirmed, confirmed→completed, review, support ve refund lead time. PII/token/card info analytics'e gönderme.
2. Önerilen pilot gözlem penceresi 4 hafta veya sezonun anlamlı booking örneklemi. Küçük veriyle istatistiksel superiority veya retention ispatı iddia etme. İlk repeat booking fırsatı oluşmadan repeat-rate hedefi koyma.
3. Önerilen kapılar: en az 3 aktif işletme, 10 tamamlanmış gerçek konaklama, kritik izolasyon/stok/charge incident0, pozitif ölçülmüş katkı senaryosu ve sürdürülebilir operasyon. Sayılar hedef/erken sinyal, ürün-pazar uyumu kanıtı değil.
4. Arz iyi talep yoksa kanal/segment deneyi, talep iyi stok kötü ise integration çözümü, ekonomi negatifse gelir modeli değişimi yap. Yeni dikey yalnız mevcut otel işlevlerini ve ortak contract regresyonunu koruyan ayrı ADR ile başlar.

**Sözleşme/kontrat:**
- PilotScorecard(metric, denominator, window, baseline, observed, proposedTarget, caveat, decision); extensionContractCheck(verticalCapabilities).

**Kabul ölçütü (bu görev uygulandığında koşulacak testler):**
- RM03 fark hipotezleri ve RM04 maliyet varsayımları gerçek pilot gözlemiyle karşılaştırılır, başarısızlıklar da raporlanır.
- Continue/pivot/stop kararı gerekçeli, yeni özellik listesiyle başarısız pilot gizlenmez.
- Flight/car supplier erişimi ve economics ayrıca araştırılmadan satış açılmaz, mevcut auth/order modeli için extension contract testi planlanır.

**Teslimat:** Pilot scorecard ve karar; Doğrulanan/doğrulanmayan avantaj listesi; Yeni dikey başlama/erteleme ADR'si

**İlgili kaynaklar:**
  - `MAIN-YC-DISCOVERY` [YC Startup School Week 4: early users and host learning](https://www.ycombinator.com/blog/startup-school-week-4-recap-kat-manalac-and-gustaf-alstromer/): YC'nin resmi konuşma transkripti erken Airbnb host'larıyla doğrudan görüşerek liste içeriği/güven sorunlarını öğrenmeyi ve erken kullanıcıları ölçeklenmeyen kişisel çalışmalarla bulmayı örnekler.
  - `iyzico-submerchant` [iyzico: Alt Üye](https://docs.iyzico.com/urunler/pazaryeri/pazaryeri-entegrasyonu/alt-uye-olusturma): Onboarding API üç alt üye tipi destekler. Alanlar tipe göre değişir. Yanıttaki subMerchantKey satıcıyı ödeme sırasında tanımlar. IBAN ilgili kişi veya şirket unvanıyla uyumlu olmalıdır ve para gönderimi için onay öncesinde tamamlanmalıdır. Sayfa gerçek bilgiler gönderilmesini açıkça ister.
  - `iyzico-payment` [iyzico: Pazaryeri Ödemesi](https://docs.iyzico.com/urunler/pazaryeri/pazaryeri-entegrasyonu/pazaryeri-odemesi): Pazaryeri ödeme kırılımlarında subMerchantKey ve subMerchantPrice gönderilir. İkinci alan alt üyeye gönderilmesi istenen tutardır. Sayfadaki NON3D örneği tüm yöntemlerde alt üye alanlarının eklenmesi gerektiğini belirtir.
  - `iyzico-approval` [iyzico: Onay](https://docs.iyzico.com/urunler/pazaryeri/pazaryeri-entegrasyonu/onay): Belge, ödeme kırılımı onaylanana kadar paranın korumalı havuz hesapta bekletildiğini açıklar. Kırılım onayı ve onay kaldırma paymentTransactionId üzerinden yapılır. Onaylar kod seviyesinde gönderilir, panelde bu fonksiyon bulunmadığı belirtilir.
  - `iyzico-sandbox` [iyzico: Live vs Sandbox](https://docs.iyzico.com/en/getting-started/preliminaries/live-vs-sandbox): Sandbox gerçek işlem veya fonları etkilemeden ödeme akışlarını simüle eder. Sandbox ve canlı API adresleri ile kimlik bilgileri farklıdır. Test kartları yalnız sandbox senaryoları için sunulur.
  - `iyzico-refund` [iyzico: Refund & Cancel](https://docs.iyzico.com/en/advanced/refund-and-cancel): Standart iade paymentTransactionId ve price kullanır, tam veya kısmi olabilir ve kalan iade edilebilir tutarı aşamaz. Belge çok ürünlü sepetlerde Refund V2 kullanımını önermez. Cancel aynı gün ve tam işlem içindir. Karta yansıma bankaya göre değişir.
  - `iyzico-webhook` [iyzico: Webhook](https://docs.iyzico.com/ek-servisler/webhook): Webhook JSON HTTP POST bildirimi kullanır. HTTPS URL istenir. Belge X-IYZ-SIGNATURE-V3 doğrulamasını açıklar ve önceki sürümlerin desteklenmeyeceğini belirtir. İmza özelliğinin hesapta etkin olması gerekir. 2xx gelmeyen bildirimler için tekrar davranışı belgelenmiştir.
  - `MAIN-APPLE-PAYMENT` [Apple App Review Guidelines: 3.1.3(e)](https://developer.apple.com/app-store/review/guidelines/): 3.1.3(e) uygulama dışında tüketilen fiziksel ürün/hizmetlerin ödemesinde in-app purchase dışındaki yöntemleri belirtir.
  - `n1` [ENUYGUN Türkiye'nin Seyahat Sitesi - Uçak Bileti, Otobüs, Otel](https://www.enuygun.com/): Aynı tüketici sitesinde uçak, otel, otobüs, uçak+otel, araç kiralama ve transfer arama yüzeyleri vardır. Seyahat yönetimi altında online check-in ve uçak, otobüs, otel rezervasyonu durum sorgulama tanıtılır. Tüketici mobil uygulaması tanıtılır.
  - `n2` [Hakkında - ENUYGUN](https://www.enuygun.com/hakkinda/): Şirket uçak, otobüs, otel, transfer ve araç kiralama ürünlerini sunduğunu belirtir. Otellerin fiyat ve özelliklerini karşılaştırarak rezervasyon yapılmasını tanıtır.

## 6. Kaynak kayıt defteri (tüm görüşmede kullanılan birincil kaynaklar)

| ID | Başlık | URL | Doğrulanan olgu (özet) |
|---|---|---|---|
| b1 | List Your Apartment, Hotel, Vacation Home, or B&B on Booking.com | https://www.join.booking.com/index.html | Tesis listeleme ve Extranet bağlantısı sunulur. Yayına geçiş öncesinde tesis doğrulaması istenebileceği belirtilir. Başka seyahat sitelerinden tesis bilgisi akt… |
| e1 | Log in to your Expedia Group portal | https://partner.expediagroup.com/en-us/log-in | Resmi portal tanıtımı, otel ortaklarının Partner Central üzerinden rekabet içgörüleri inceleyebileceğini, envanter yönetebileceğini ve fiyatlarını optimize edeb… |
| e2 | Welcome | EG Connectivity Hub | https://developers.expediagroup.com/supply/lodging | PMS, channel-manager ve CRS sağlayıcıları için tesis listeleme ve yönetim bağlantıları tanıtılır. Entegrasyon süreci lisans ve PCI incelemesi ile geliştirme, te… |
| o1 | İşlem Rehberi (Rezervasyon) | Otelz | https://www.otelz.com/corporate/company/user-guide | Kamuya açık içerikte tesis ekleme, Extranet giriş, rezervasyon yönetme ve tüketici mobil uygulama indirme bağlantıları bulunur. Otel, termal otel, kayak oteli, … |
| n1 | ENUYGUN Türkiye'nin Seyahat Sitesi - Uçak Bileti, Otobüs, Otel | https://www.enuygun.com/ | Aynı tüketici sitesinde uçak, otel, otobüs, uçak+otel, araç kiralama ve transfer arama yüzeyleri vardır. Seyahat yönetimi altında online check-in ve uçak, otobü… |
| n2 | Hakkında - ENUYGUN | https://www.enuygun.com/hakkinda/ | Şirket uçak, otobüs, otel, transfer ve araç kiralama ürünlerini sunduğunu belirtir. Otellerin fiyat ve özelliklerini karşılaştırarak rezervasyon yapılmasını tan… |
| s1 | Hotel Channel Manager | SiteMinder | https://www.siteminder.com/channel-manager/ | Oda ve fiyat planı eşleme, fiyat, müsaitlik ve kısıtların tek platformdan kanallara eşlenmesi tanıtılır. Booking.com'a medya, oda ve fiyat planı gönderimi ve se… |
| s2 | Developer Guide | SiteMinder | https://developer.siteminder.com/ | Resmi geliştirici rehberi yeni ortaklar için başvuru, geliştirme, sertifikasyon ve canlıya geçişten oluşan yapılandırılmış entegrasyon yolu tanıtır. Mevcut orta… |
| h1 | HotelRunner | A Sales-First Approach to Hospitality | https://hotelrunner.com/ | Ürün kataloğunda PMS, CRS, channel-manager, booking engine, otel web sitesi, misafir ilişki yönetimi, yorum merkezi, mobil uygulama, POS, grup otel yönetimi ve … |
| h2 | Introduction | HotelRunner Developer Docs | https://developers.hotelrunner.com/ | App Manager belgeleri PMS, gelir yönetimi, POS ve hotspot bağlantıları tanıtır. XML/SOAP ve REST/JSON üzerinden müsaitlik, kısıt ve fiyat gönderme, rezervasyon … |
| TECH-TENANT | OWASP Multi-Tenant Application Security Cheat Sheet | https://cheatsheetseries.owasp.org/cheatsheets/Multi_Tenant_Security_Cheat_Sheet.html | İstemciden gelen tenant kimliği yalnızca seçicidir, doğrulanmış kimlik ve güncel üyelikle yetkilendirilmelidir. PostgreSQL tenant bağlamını transaction-local tu… |
| TECH-AUTHORIZATION | OWASP Authorization Cheat Sheet | https://cheatsheetseries.owasp.org/cheatsheets/Authorization_Cheat_Sheet.html | OWASP varsayılan reddetmeyi ve kaynağı ne olursa olsun her istekte izin doğrulamayı önerir. |
| TECH-PG-RLS | PostgreSQL 16: Row Security Policies | https://www.postgresql.org/docs/16/ddl-rowsecurity.html | RLS görünür satırları ve yazılabilecek satırları ayrı ifadelerle sınırlayabilir. Superuser ve BYPASSRLS rolleri her zaman, tablo sahibi normalde politikayı aşar… |
| TECH-PG-LOCK | PostgreSQL 16: Explicit Locking | https://www.postgresql.org/docs/16/explicit-locking.html | SELECT FOR UPDATE seçilen satırların rakip güncellenmesini, silinmesini ve çatışan kilitlenmesini transaction sonuna kadar engeller. Normal okumaları engellemez… |
| TECH-SECURITY-METHOD | Spring Security: Method Security | https://docs.spring.io/spring-security/reference/servlet/authorization/method-security.html | EnableMethodSecurity, Spring-managed metotlarda PreAuthorize/PostAuthorize kontrollerini etkinleştirir, security starter bunu kendiliğinden açmaz. Metot yetkile… |
| TECH-SECURITY-JWT | Spring Security: OAuth 2.0 Resource Server JWT | https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html | Resource Server imza, exp, nbf ve yapılandırılmış issuer kontrolü yapar, scope değerlerini SCOPE_ authority olarak eşler. Audience doğrulaması ayrıca yapılandır… |
| TECH-FLYWAY-EXPAND | Redgate Flyway: Rolling out updates from a single schema to multiple production databases | https://documentation.red-gate.com/flyway/deploying-database-changes-using-flyway/rolling-out-updates-from-a-single-schema-to-multiple-production-databases | Expand/contract eski yapıyı koruyarak nullable/default yeni yapıyı ekler, davranış ve geçmiş veriyi taşır, tüm uygulamalar yeni yola geçtikten sonra eski yapıyı… |
| TECH-OUTBOX | Debezium: Outbox Event Router | https://debezium.io/documentation/reference/stable/transformations/outbox-event-router.html | Connector outbox tablosundaki değişiklikleri yakalar ve outbox event router SMT ile dönüştürür. Doküman dönüşümün yalnızca outbox olaylarına uygulanmasını, bird… |
| TECH-NEXT-LAYOUT | Next.js: Layouts and Pages | https://nextjs.org/docs/app/getting-started/layouts-and-pages | App Router layout birden fazla sayfanın ortak UI'ını kapsar, gezinmede durumunu korur ve yeniden render edilmez. |
| TECH-NEXT-AUTH | Next.js: Authentication | https://nextjs.org/docs/app/guides/authentication | Partial rendering nedeniyle layout auth kontrolü her route değişiminde çalışmaz. Doküman kontrolleri veri kaynağına yakın DAL katmanında yapmayı önerir, layoutt… |
| TECH-OPENAPI | OpenAPI Specification v3.1.1: Schema Object | https://spec.openapis.org/oas/v3.1.1.html | OAS 3.1 Schema Object JSON Schema Draft 2020-12 temellidir. format gibi bazı anahtarlar varsayılan vocabulary içinde doğrudan doğrulama değil annotation niteliğ… |
| TECH-PLAYWRIGHT | Playwright: Authentication | https://playwright.dev/docs/auth | Auth setup browser state dosyasını sonraki testlerde yeniden kullanabilir, dosya hassas cookie/header içerdiğinden repoya konmamalıdır. Paralel testler sunucu d… |
| TECH-TESTCONTAINERS | Testcontainers for Java: Postgres Module | https://java.testcontainers.org/modules/databases/postgres/ | PostgreSQLContainer Java içinden seçilen PostgreSQL image ile geçici veritabanı başlatır, doküman alternatif jdbc:tc bağlantı mekanizmasını da gösterir. |
| TECH-MOBILE | Flutter: Guide to app architecture | https://docs.flutter.dev/app-architecture/guide | Flutter rehberi view/view-model ile UI durumu ve görünümü ayırır, data katmanını repository ve service olarak böler. Repository domain verisinin kaynağıdır ve c… |
| iyzico-submerchant | iyzico: Alt Üye | https://docs.iyzico.com/urunler/pazaryeri/pazaryeri-entegrasyonu/alt-uye-olusturma | Onboarding API üç alt üye tipi destekler. Alanlar tipe göre değişir. Yanıttaki subMerchantKey satıcıyı ödeme sırasında tanımlar. IBAN ilgili kişi veya şirket un… |
| iyzico-payment | iyzico: Pazaryeri Ödemesi | https://docs.iyzico.com/urunler/pazaryeri/pazaryeri-entegrasyonu/pazaryeri-odemesi | Pazaryeri ödeme kırılımlarında subMerchantKey ve subMerchantPrice gönderilir. İkinci alan alt üyeye gönderilmesi istenen tutardır. Sayfadaki NON3D örneği tüm yö… |
| iyzico-approval | iyzico: Onay | https://docs.iyzico.com/urunler/pazaryeri/pazaryeri-entegrasyonu/onay | Belge, ödeme kırılımı onaylanana kadar paranın korumalı havuz hesapta bekletildiğini açıklar. Kırılım onayı ve onay kaldırma paymentTransactionId üzerinden yapı… |
| iyzico-sandbox | iyzico: Live vs Sandbox | https://docs.iyzico.com/en/getting-started/preliminaries/live-vs-sandbox | Sandbox gerçek işlem veya fonları etkilemeden ödeme akışlarını simüle eder. Sandbox ve canlı API adresleri ile kimlik bilgileri farklıdır. Test kartları yalnız … |
| iyzico-refund | iyzico: Refund & Cancel | https://docs.iyzico.com/en/advanced/refund-and-cancel | Standart iade paymentTransactionId ve price kullanır, tam veya kısmi olabilir ve kalan iade edilebilir tutarı aşamaz. Belge çok ürünlü sepetlerde Refund V2 kull… |
| iyzico-webhook | iyzico: Webhook | https://docs.iyzico.com/ek-servisler/webhook | Webhook JSON HTTP POST bildirimi kullanır. HTTPS URL istenir. Belge X-IYZ-SIGNATURE-V3 doğrulamasını açıklar ve önceki sürümlerin desteklenmeyeceğini belirtir. … |
| kvkk-security | KVKK: Kişisel Veri Güvenliği Rehberi, Teknik ve İdari Tedbirler | https://www.kvkk.gov.tr/yayinlar/veri_guvenligi_rehberi.pdf | Resmi rehber risklere uygun teknik ve idari tedbirleri ele alır. Veri envanteri, erişim politikaları, veri işleyen ilişkileri, bulut, yedekleme ve saklama/imha … |
| kvkk-notice-consent | KVKK: 18.02.2026 tarihli 2026/347 sayılı ilke kararına ilişkin aydınlatma ve açık rıza duyurusu | https://www.kvkk.gov.tr/Icerik/8710/veri-sorumlulari-tarafindan-acik-riza-ve-aydinlatma-metinlerinin-ayri-ayri-duzenlenmesi-gerektigi-hakkinda-kisisel-verileri-koruma-kurulunun-18-02-2026-tarihli-ve-2026-347-sayili-ilke-kararina-iliskin-kamuoyu-duyurusu | Kurum duyurusu aydınlatma ve açık rıza metinlerinin ayrı düzenlenmesini, aydınlatma için onay/rıza istenmemesini ve metinlerin faaliyete uyarlanmasını vurgular.… |
| tursab-licensing | TÜRSAB: Yeni İşletme Belgesi Başvurusu | https://www.tursab.org.tr/yeni-isletme-belgesi-basvurusu | Resmi sayfa seyahat acentası işletme belgesi isteyen tüzel kişiler için Bakanlığa unvan başvurusu, TÜRSAB incelemesi ve Bakanlık belge sürecini açıklar. Seyahat… |
| MAIN-BOOKING-ARI | Booking.com: Create or update inventory, rates and restrictions | https://developers.booking.com/connectivity/docs/b_xml-availability | Resmi örnek stok sayısını oda tipi seviyesinde bütün rate planlar için ortak tutar. Fiyat ve kısıtlar oda tipi/rate plan/tarih boyutlarıyla güncellenir, tarih a… |
| MAIN-EXPEDIA-API | Expedia Connectivity: Lodging APIs overview | https://connectivityportal.expediagroup.com/documentation/expedia | Resmi doküman availability/rates, reservation management ve product management sınırlarını ayırır. Product API oda tipi/rate plan yönetimini, review API onaylı … |
| MAIN-YC-DISCOVERY | YC Startup School Week 4: early users and host learning | https://www.ycombinator.com/blog/startup-school-week-4-recap-kat-manalac-and-gustaf-alstromer/ | YC'nin resmi konuşma transkripti erken Airbnb host'larıyla doğrudan görüşerek liste içeriği/güven sorunlarını öğrenmeyi ve erken kullanıcıları ölçeklenmeyen kiş… |
| MAIN-APPLE-BETA | Apple: TestFlight Overview | https://developer.apple.com/help/app-store-connect/test-a-beta-version/testflight-overview/ | TestFlight beta build dağıtımı, internal/external tester yönetimi ve feedback toplama akışını açıklar. İlk external beta build review sürecine girebilir. |
| MAIN-PLAY-BETA | Google Play: Set up an open, closed, or internal test | https://support.google.com/googleplay/android-developer/answer/9845334?hl=en | Play Console internal, closed ve open test tracklerini ayrı tanımlar. Internal testten küçük closed test grubuna ilerlemeyi önerir, yeni personal accountlar içi… |
| MAIN-RN-SECURITY | React Native: Security | https://reactnative.dev/docs/security | Resmi rehber AsyncStorage'ı şifrelenmeyen store olarak açıklar ve token/secrets için önermediğini belirtir. Client bundle'a server secret koymamak ve platform s… |
| MAIN-APPLE-PAYMENT | Apple App Review Guidelines: 3.1.3(e) | https://developer.apple.com/app-store/review/guidelines/ | 3.1.3(e) uygulama dışında tüketilen fiziksel ürün/hizmetlerin ödemesinde in-app purchase dışındaki yöntemleri belirtir. |

Bütün kayıtlarda kontrol tarihi 14 Ekim 2026'dır. Her kaynağın tam sınırlaması (limitation) ilgili bölümde veya alt-agent ham çıktısında mevcuttur.

## 7. Bu dokümanın sınırları

- Bu roadmap 40 adımın **tasarımıdır**, hiçbiri henüz uygulanmadı. RM-01 tamamlanmadan RM-02'ye kanıt temelli geçilemez; sıra zorunludur.

- RM-11, mevcut 83 Superpro alt görevinin bu roadmap'e KEEP/ADAPT/DEFER olarak eşlenmesini tanımlar; bu eşleme RM-11 çalıştırıldığında yapılacaktır, bu dokümanda satır satır yapılmamıştır.

- Sayısal hedefler (görüşme sayısı, yüzde eşikleri, pilot süresi) araştırmacı/asistan önerisidir, doğrulanmış sektör ölçütü değildir.

- Ticari/hukuki konular (RM-05, Bölüm 'payment/privacy/travel-law' kaynakları) resmi kaynak okumasıdır, hukuki görüş değildir; uzman onayı gerekir.

- Kullanıcı açıkça 'geliştirmeye başla' demeden hiçbir RM görevinin kodu yazılmayacak, migration çalıştırılmayacak, hesap açılmayacak veya ödeme/iletişim gönderilmeyecektir.
