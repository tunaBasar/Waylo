#!/usr/bin/env bash
# Waylo Mock Data Seed Script
# Tum servisleri gercekci verilerle doldurur
set -euo pipefail

BASE="http://localhost:8080"
H='Content-Type: application/json'

post() {
  local path="$1" body="$2" label="$3"
  RESP=$(curl -s -w '\n%{http_code}' -X POST "$BASE$path" -H "$H" -d "$body")
  CODE=$(echo "$RESP" | tail -1)
  BODY=$(echo "$RESP" | sed '$d')
  if [ "$CODE" = "201" ] || [ "$CODE" = "200" ]; then
    ID=$(echo "$BODY" | python3 -c 'import sys,json; print(json.load(sys.stdin)["data"]["id"])' 2>/dev/null || echo "")
    echo "  [OK] $label -> $ID"
    echo "$ID"
  elif [ "$CODE" = "409" ]; then
    echo "  [SKIP] $label (zaten var)"
    echo ""
  else
    echo "  [FAIL:$CODE] $label"
    echo "$BODY" | head -1
    echo ""
  fi
}

# Helper: son satir ID, onceki satirlar log
extract_id() {
  tail -1
}

echo "=========================================="
echo "  WAYLO MOCK DATA SEED"
echo "=========================================="
echo ""

# =============================================
# 1. AIRPORTS
# =============================================
echo "--- 1. HAVAALANLARI ---"

declare -a AIRPORT_CODES=()

create_airport() {
  local code="$1" name="$2" city="$3" country="$4" tz="$5" lat="$6" lon="$7"
  local result
  result=$(post "/api/airports" "{\"iataCode\":\"$code\",\"name\":\"$name\",\"city\":\"$city\",\"country\":\"$country\",\"timezone\":\"$tz\",\"latitude\":$lat,\"longitude\":$lon}" "$code - $name")
  AIRPORT_CODES+=("$code")
}

create_airport "AYT" "Antalya Havalimani"       "Antalya"    "Turkey" "Europe/Istanbul"    36.8987  30.8005
create_airport "ESB" "Esenboga Havalimani"       "Ankara"     "Turkey" "Europe/Istanbul"    40.1281  32.9951
create_airport "ADB" "Adnan Menderes Havalimani" "Izmir"      "Turkey" "Europe/Istanbul"    38.2924  27.1570
create_airport "TZX" "Trabzon Havalimani"        "Trabzon"    "Turkey" "Europe/Istanbul"    40.9951  39.7897
create_airport "DLM" "Dalaman Havalimani"        "Mugla"      "Turkey" "Europe/Istanbul"    36.7131  28.7925
create_airport "BJV" "Milas-Bodrum Havalimani"   "Bodrum"     "Turkey" "Europe/Istanbul"    37.2506  28.6843
create_airport "GZT" "Gaziantep Havalimani"      "Gaziantep"  "Turkey" "Europe/Istanbul"    36.9472  37.4787
create_airport "VAN" "Ferit Melen Havalimani"    "Van"        "Turkey" "Europe/Istanbul"    38.4682  43.3323
create_airport "CDG" "Charles de Gaulle"         "Paris"      "France" "Europe/Paris"       49.0097  2.5479
create_airport "LHR" "Heathrow Airport"          "London"     "UK"     "Europe/London"      51.4700  -0.4543
create_airport "FRA" "Frankfurt Airport"         "Frankfurt"  "Germany" "Europe/Berlin"     50.0379  8.5622
create_airport "BCN" "El Prat Airport"           "Barcelona"  "Spain"  "Europe/Madrid"      41.2971  2.0785
create_airport "FCO" "Fiumicino Airport"         "Rome"       "Italy"  "Europe/Rome"        41.8003  12.2389
create_airport "AMS" "Schiphol Airport"          "Amsterdam"  "Netherlands" "Europe/Amsterdam" 52.3086 4.7639
create_airport "JFK" "John F. Kennedy"           "New York"   "USA"    "America/New_York"   40.6413  -73.7781
create_airport "DXB" "Dubai International"       "Dubai"      "UAE"    "Asia/Dubai"         25.2532  55.3657

echo ""

# =============================================
# 2. FLIGHTS
# =============================================
echo "--- 2. UCUSLAR ---"

declare -a FLIGHT_IDS=()

create_flight() {
  local fn="$1" from="$2" to="$3" dep="$4" arr="$5" airline="$6"
  local result
  result=$(post "/api/flights" "{\"flightNumber\":\"$fn\",\"departureAirportCode\":\"$from\",\"arrivalAirportCode\":\"$to\",\"departureTime\":\"$dep\",\"arrivalTime\":\"$arr\",\"airline\":\"$airline\"}" "$fn $from->$to")
  local id
  id=$(echo "$result" | extract_id)
  [ -n "$id" ] && FLIGHT_IDS+=("$id")
}

# Ic hat ucuslari
create_flight "TK2100" "IST" "AYT" "2026-12-15T06:30:00Z" "2026-12-15T08:00:00Z" "Turkish Airlines"
create_flight "TK2102" "IST" "ESB" "2026-12-15T07:00:00Z" "2026-12-15T08:15:00Z" "Turkish Airlines"
create_flight "TK2104" "IST" "ADB" "2026-12-15T08:30:00Z" "2026-12-15T09:45:00Z" "Turkish Airlines"
create_flight "TK2106" "IST" "TZX" "2026-12-15T09:00:00Z" "2026-12-15T10:45:00Z" "Turkish Airlines"
create_flight "TK2108" "IST" "DLM" "2026-12-15T10:00:00Z" "2026-12-15T11:30:00Z" "Turkish Airlines"
create_flight "PC2201" "SAW" "AYT" "2026-12-15T11:00:00Z" "2026-12-15T12:30:00Z" "Pegasus Airlines"
create_flight "PC2203" "SAW" "ADB" "2026-12-15T12:00:00Z" "2026-12-15T13:15:00Z" "Pegasus Airlines"
create_flight "PC2205" "SAW" "GZT" "2026-12-15T14:00:00Z" "2026-12-15T16:00:00Z" "Pegasus Airlines"
create_flight "AJ3301" "AYT" "IST" "2026-12-16T09:00:00Z" "2026-12-16T10:30:00Z" "AnadoluJet"
create_flight "AJ3303" "ESB" "ADB" "2026-12-16T13:00:00Z" "2026-12-16T14:30:00Z" "AnadoluJet"

# Dis hat ucuslari
create_flight "TK1900" "IST" "CDG" "2026-12-20T06:00:00Z" "2026-12-20T09:00:00Z" "Turkish Airlines"
create_flight "TK1902" "IST" "LHR" "2026-12-20T07:30:00Z" "2026-12-20T10:00:00Z" "Turkish Airlines"
create_flight "TK1904" "IST" "FRA" "2026-12-20T08:00:00Z" "2026-12-20T10:30:00Z" "Turkish Airlines"
create_flight "TK1906" "IST" "BCN" "2026-12-20T11:00:00Z" "2026-12-20T14:00:00Z" "Turkish Airlines"
create_flight "TK1908" "IST" "FCO" "2026-12-20T14:00:00Z" "2026-12-20T16:30:00Z" "Turkish Airlines"
create_flight "TK1910" "IST" "AMS" "2026-12-21T06:30:00Z" "2026-12-21T09:30:00Z" "Turkish Airlines"
create_flight "TK1912" "IST" "JFK" "2026-12-21T10:00:00Z" "2026-12-21T15:00:00Z" "Turkish Airlines"
create_flight "TK1914" "IST" "DXB" "2026-12-21T22:00:00Z" "2026-12-22T03:00:00Z" "Turkish Airlines"
create_flight "PC1801" "SAW" "CDG" "2026-12-22T07:00:00Z" "2026-12-22T10:00:00Z" "Pegasus Airlines"
create_flight "PC1803" "SAW" "AMS" "2026-12-22T09:00:00Z" "2026-12-22T12:00:00Z" "Pegasus Airlines"

echo ""

# =============================================
# 3. HOTELS
# =============================================
echo "--- 3. OTELLER ---"

declare -a HOTEL_IDS=()

create_hotel() {
  local name="$1" city="$2" country="$3" addr="$4" star="$5" desc="$6"
  local result
  result=$(post "/api/hotels" "{\"name\":\"$name\",\"city\":\"$city\",\"country\":\"$country\",\"address\":\"$addr\",\"starRating\":$star,\"description\":\"$desc\"}" "$name")
  local id
  id=$(echo "$result" | extract_id)
  [ -n "$id" ] && HOTEL_IDS+=("$id")
}

create_hotel "Waylo Grand Istanbul"     "Istanbul"   "Turkey" "Beyoglu, Istiklal Caddesi 42"       5 "Bogaz manzarali luks otel, spa ve havuzlu"
create_hotel "Waylo Boutique Antalya"   "Antalya"    "Turkey" "Kaleici, Hesapci Sokak 8"           4 "Tarihi Kaleici'de butik otel"
create_hotel "Waylo Resort Bodrum"      "Bodrum"     "Turkey" "Torba Mahallesi, Sahil Yolu 15"     5 "Ege kiyisinda her sey dahil resort"
create_hotel "Ankara Palace Hotel"      "Ankara"     "Turkey" "Kizilay, Ataturk Bulvari 120"       4 "Sehir merkezinde is oteli"
create_hotel "Izmir Kordon Hotel"       "Izmir"      "Turkey" "Alsancak, Kordon Boyu 55"           3 "Kordon'da ekonomik konaklama"
create_hotel "Cappadocia Cave Suites"   "Nevsehir"   "Turkey" "Goreme, Muze Yolu 3"               5 "Peri bacalari manzarali magara otel"
create_hotel "Trabzon Uzungol Lodge"    "Trabzon"    "Turkey" "Uzungol Mahallesi, Gol Kenari 1"    3 "Gol kenarinda huzurlu dagevi"
create_hotel "Le Parisien Hotel"        "Paris"      "France" "15 Rue de Rivoli, 75001"            4 "Louvre yakininda sik otel"
create_hotel "London Bridge Suites"     "London"     "UK"     "120 Tower Bridge Road, SE1"          4 "Tower Bridge manzarali"
create_hotel "Dubai Marina Resort"      "Dubai"      "UAE"    "Marina Walk, JBR 44"                5 "Plaj ve marina manzarali"

echo ""

# =============================================
# 4. ROOMS (her otel icin 3-5 oda)
# =============================================
echo "--- 4. ODALAR ---"

create_room() {
  local hotel_id="$1" num="$2" type="$3" cap="$4" price="$5" floor="$6" desc="$7"
  if [ -z "$hotel_id" ]; then return; fi
  post "/api/hotels/$hotel_id/rooms" "{\"roomNumber\":\"$num\",\"roomType\":\"$type\",\"capacity\":$cap,\"pricePerNight\":$price,\"floor\":$floor,\"description\":\"$desc\"}" "Oda $num ($type)" > /dev/null
  echo "  [+] Oda $num ($type)"
}

# Hotel rooms - her otel icin cesitli odalar
idx=0
for hid in "${HOTEL_IDS[@]}"; do
  idx=$((idx + 1))
  create_room "$hid" "${idx}01" "SINGLE"  1 350   1  "Standart tek kisilik oda"
  create_room "$hid" "${idx}02" "DOUBLE"  2 550   1  "Cift kisilik genis oda"
  create_room "$hid" "${idx}03" "DOUBLE"  2 650   2  "Manzarali cift kisilik"
  create_room "$hid" "${idx}04" "SUITE"   3 1200  3  "Junior suite, oturma odalii"
  if [ $((idx % 2)) -eq 0 ]; then
    create_room "$hid" "${idx}05" "DELUXE"  2 900  2  "Deluxe oda jakuzili"
    create_room "$hid" "${idx}06" "FAMILY"  4 1500 1  "Aile odasi, cocuk yatakli"
  fi
done

echo ""

# =============================================
# 5. VEHICLES
# =============================================
echo "--- 5. ARACLAR ---"

create_vehicle() {
  local plate="$1" brand="$2" model="$3" year="$4" type="$5" seat="$6" rate="$7" loc="$8" color="$9" mileage="${10}"
  post "/api/vehicles" "{\"plateNumber\":\"$plate\",\"brand\":\"$brand\",\"model\":\"$model\",\"year\":$year,\"vehicleType\":\"$type\",\"seatCount\":$seat,\"dailyRate\":$rate,\"location\":\"$loc\",\"color\":\"$color\",\"mileage\":$mileage}" "$brand $model ($plate)" > /dev/null
  echo "  [+] $brand $model ($plate)"
}

create_vehicle "34 WYL 10" "Toyota"     "Corolla"     2024 "ECONOMY"  5  250  "Istanbul Havalimani"  "Beyaz"   15000
create_vehicle "34 WYL 11" "Renault"    "Clio"        2024 "ECONOMY"  5  200  "Istanbul Havalimani"  "Gri"     12000
create_vehicle "34 WYL 12" "Fiat"       "Egea"        2023 "COMPACT"  5  220  "Sabiha Gokcen"        "Mavi"    28000
create_vehicle "34 WYL 13" "Volkswagen" "Golf"        2024 "COMPACT"  5  280  "Sabiha Gokcen"        "Siyah"   8000
create_vehicle "07 WYL 20" "Toyota"     "Camry"       2024 "MIDSIZE"  5  350  "Antalya Havalimani"   "Beyaz"   5000
create_vehicle "07 WYL 21" "Honda"      "Civic"       2023 "MIDSIZE"  5  320  "Antalya Havalimani"   "Kirmizi" 22000
create_vehicle "06 WYL 30" "Ford"       "Mondeo"      2024 "FULLSIZE" 5  400  "Ankara Esenboga"      "Lacivert" 10000
create_vehicle "35 WYL 40" "BMW"        "X3"          2024 "SUV"      5  650  "Izmir Adnan Menderes" "Siyah"   7000
create_vehicle "48 WYL 50" "Mercedes"   "GLE"         2024 "SUV"      5  750  "Bodrum Havalimani"    "Beyaz"   4000
create_vehicle "34 WYL 60" "Mercedes"   "E-Class"     2024 "LUXURY"   5  900  "Istanbul Havalimani"  "Siyah"   3000
create_vehicle "34 WYL 61" "BMW"        "7 Serisi"    2024 "LUXURY"   5  1100 "Istanbul Havalimani"  "Lacivert" 2000
create_vehicle "07 WYL 70" "Ford"       "Tourneo"     2023 "VAN"      9  500  "Antalya Havalimani"   "Beyaz"   35000
create_vehicle "34 WYL 71" "Mercedes"   "Vito"        2024 "VAN"      8  600  "Sabiha Gokcen"        "Gri"     15000
create_vehicle "34 WYL 80" "Hyundai"    "i20"         2023 "ECONOMY"  5  180  "Istanbul Havalimani"  "Yesil"   40000
create_vehicle "61 WYL 90" "Dacia"      "Duster"      2024 "SUV"      5  450  "Trabzon Havalimani"   "Turuncu" 6000

echo ""

# =============================================
# 6. CUSTOMERS
# =============================================
echo "--- 6. MUSTERILER ---"

declare -a CUSTOMER_IDS=()

create_customer() {
  local fn="$1" ln="$2" email="$3" phone="$4" passport="$5"
  local body="{\"firstName\":\"$fn\",\"lastName\":\"$ln\",\"email\":\"$email\""
  [ -n "$phone" ] && body="$body,\"phone\":\"$phone\""
  [ -n "$passport" ] && body="$body,\"passportNumber\":\"$passport\""
  body="$body}"
  local result
  result=$(post "/api/customers" "$body" "$fn $ln")
  local id
  id=$(echo "$result" | extract_id)
  [ -n "$id" ] && CUSTOMER_IDS+=("$id")
}

create_customer "Mehmet"   "Kaya"      "mehmet.kaya@email.com"      "+905321112233" "TR12345678"
create_customer "Ayse"     "Demir"     "ayse.demir@email.com"       "+905339998877" "TR23456789"
create_customer "Fatma"    "Celik"     "fatma.celik@email.com"      "+905347776655" "TR34567890"
create_customer "Mustafa"  "Sahin"     "mustafa.sahin@email.com"    "+905355554433" "TR45678901"
create_customer "Zeynep"   "Yildiz"    "zeynep.yildiz@email.com"    "+905363332211" "TR56789012"
create_customer "Emre"     "Ozturk"    "emre.ozturk@email.com"      "+905371110099" "TR67890123"
create_customer "Elif"     "Arslan"    "elif.arslan@email.com"      "+905389998877" ""
create_customer "Burak"    "Dogan"     "burak.dogan@email.com"      "+905391112233" "TR89012345"
create_customer "Selin"    "Acar"      "selin.acar@email.com"       "+905401234567" ""
create_customer "Kerem"    "Polat"     "kerem.polat@email.com"      "+905417654321" "TR01234567"
create_customer "Deniz"    "Koc"       "deniz.koc@email.com"        "+905428765432" "TR11223344"
create_customer "Hakan"    "Erdem"     "hakan.erdem@email.com"      "+905439876543" ""
create_customer "Gamze"    "Tekin"     "gamze.tekin@email.com"      "+905441122334" "TR22334455"
create_customer "Cem"      "Gunes"     "cem.gunes@email.com"        "+905455566778" "TR33445566"
create_customer "Pinar"    "Bulut"     "pinar.bulut@email.com"      "+905466677889" ""

echo ""

# =============================================
# 7. PRICING RULES
# =============================================
echo "--- 7. FIYATLANDIRMA KURALLARI ---"

post "/api/pricing-rules" '{"name":"Ekonomi Ucus","pricingType":"FLIGHT","basePrice":500,"discountPercentage":0,"currency":"TRY","description":"Yurt ici ekonomi ucus fiyati"}' "Ekonomi Ucus" > /dev/null
echo "  [+] Ekonomi Ucus"
post "/api/pricing-rules" '{"name":"Business Ucus","pricingType":"FLIGHT","basePrice":1500,"discountPercentage":0,"currency":"TRY","description":"Business class ucus fiyati"}' "Business Ucus" > /dev/null
echo "  [+] Business Ucus"
post "/api/pricing-rules" '{"name":"Dis Hat Ucus","pricingType":"FLIGHT","basePrice":3000,"discountPercentage":0,"currency":"TRY","description":"Avrupa ucus fiyati"}' "Dis Hat Ucus" > /dev/null
echo "  [+] Dis Hat Ucus"
post "/api/pricing-rules" '{"name":"Standart Oda","pricingType":"HOTEL","basePrice":400,"discountPercentage":0,"currency":"TRY","description":"Standart otel oda fiyati"}' "Standart Oda" > /dev/null
echo "  [+] Standart Oda"
post "/api/pricing-rules" '{"name":"Suite Oda","pricingType":"HOTEL","basePrice":1200,"discountPercentage":0,"currency":"TRY","description":"Suite oda fiyati"}' "Suite Oda" > /dev/null
echo "  [+] Suite Oda"
post "/api/pricing-rules" '{"name":"Ekonomi Arac","pricingType":"CAR_RENTAL","basePrice":200,"discountPercentage":0,"currency":"TRY","description":"Ekonomi arac gunluk fiyat"}' "Ekonomi Arac" > /dev/null
echo "  [+] Ekonomi Arac"
post "/api/pricing-rules" '{"name":"SUV Arac","pricingType":"CAR_RENTAL","basePrice":600,"discountPercentage":0,"currency":"TRY","description":"SUV arac gunluk fiyat"}' "SUV Arac" > /dev/null
echo "  [+] SUV Arac"
post "/api/pricing-rules" '{"name":"Erken Rezervasyon","pricingType":"PACKAGE_DISCOUNT","basePrice":0,"discountPercentage":15,"currency":"TRY","description":"30 gun oncesi erken rezervasyon indirimi"}' "Erken Rezervasyon" > /dev/null
echo "  [+] Erken Rezervasyon"
post "/api/pricing-rules" '{"name":"Aile Paketi","pricingType":"PACKAGE_DISCOUNT","basePrice":0,"discountPercentage":10,"currency":"TRY","description":"4+ kisi aile paketi indirimi"}' "Aile Paketi" > /dev/null
echo "  [+] Aile Paketi"
post "/api/pricing-rules" '{"name":"Sadakat Indirimi","pricingType":"PACKAGE_DISCOUNT","basePrice":0,"discountPercentage":20,"currency":"TRY","description":"Gold ve Platinum musterilere ozel"}' "Sadakat Indirimi" > /dev/null
echo "  [+] Sadakat Indirimi"

echo ""

# =============================================
# 8. BOOKINGS + PAYMENTS
# =============================================
echo "--- 8. REZERVASYONLAR + ODEMELER ---"

# Get actual flight IDs
ALL_FLIGHTS=$(curl -s "$BASE/api/flights" | python3 -c 'import sys,json; [print(f["id"]) for f in json.load(sys.stdin)["data"]]')
FLIGHT_ARR=()
while IFS= read -r line; do [ -n "$line" ] && FLIGHT_ARR+=("$line"); done <<< "$ALL_FLIGHTS"

# Get actual customer IDs
ALL_CUSTOMERS=$(curl -s "$BASE/api/customers" | python3 -c 'import sys,json; [print(c["id"]) for c in json.load(sys.stdin)["data"]]')
CUST_ARR=()
while IFS= read -r line; do [ -n "$line" ] && CUST_ARR+=("$line"); done <<< "$ALL_CUSTOMERS"

NUM_FLIGHTS=${#FLIGHT_ARR[@]}
NUM_CUSTS=${#CUST_ARR[@]}

if [ "$NUM_FLIGHTS" -gt 0 ] && [ "$NUM_CUSTS" -gt 0 ]; then
  BOOKING_IDS=()
  for i in $(seq 0 9); do
    CIDX=$((i % NUM_CUSTS))
    FIDX=$((i % NUM_FLIGHTS))
    CID="${CUST_ARR[$CIDX]}"
    FID="${FLIGHT_ARR[$FIDX]}"
    NOTES=("Yilbasi tatili paketi" "Is seyahati" "Balayi paketi" "Aile tatili" "Hafta sonu kacamagi" "Konferans gezisi" "Kultur turu" "Plaj tatili" "Kayak tatili" "Ogrenci turu")
    RESULT=$(post "/api/bookings" "{\"customerId\":\"$CID\",\"flightId\":\"$FID\",\"notes\":\"${NOTES[$i]}\"}" "Booking #$((i+1))")
    BID=$(echo "$RESULT" | extract_id)
    [ -n "$BID" ] && BOOKING_IDS+=("$BID")
  done

  echo ""
  echo "--- 8b. ODEMELER ---"

  AMOUNTS=(2500 4800 8500 3200 1900 5500 3700 6200 4100 2800)
  METHODS=("CREDIT_CARD" "CREDIT_CARD" "CREDIT_CARD" "DEBIT_CARD" "BANK_TRANSFER" "CREDIT_CARD" "DEBIT_CARD" "CREDIT_CARD" "CREDIT_CARD" "BANK_TRANSFER")
  CURRENCIES=("TRY" "TRY" "EUR" "TRY" "TRY" "USD" "TRY" "EUR" "TRY" "TRY")

  for i in "${!BOOKING_IDS[@]}"; do
    BID="${BOOKING_IDS[$i]}"
    CIDX=$((i % NUM_CUSTS))
    CID="${CUST_ARR[$CIDX]}"
    AMT="${AMOUNTS[$i]}"
    MTH="${METHODS[$i]}"
    CUR="${CURRENCIES[$i]}"
    post "/api/payments" "{\"bookingId\":\"$BID\",\"customerId\":\"$CID\",\"amount\":$AMT,\"currency\":\"$CUR\",\"paymentMethod\":\"$MTH\"}" "Payment $AMT $CUR" > /dev/null
    echo "  [+] Payment $AMT $CUR for booking ${BID:0:8}..."
  done
else
  echo "  [!] Ucus veya musteri verisi yok, booking/payment atlanıyor"
fi

echo ""

# =============================================
# 9. NOTIFICATIONS (manual create)
# =============================================
echo "--- 9. BILDIRIMLER ---"

if [ "$NUM_CUSTS" -gt 0 ]; then
  for i in 0 1 2 3 4; do
    CIDX=$((i % NUM_CUSTS))
    CID="${CUST_ARR[$CIDX]}"
    SUBJECTS=("Rezervasyon Onaylandi" "Odeme Basarili" "Check-in Hatirlatmasi" "Hosgeldiniz" "Ozel Kampanya")
    BODIES=("Rezervasyonunuz basariyla onaylandi." "Odemeniz basariyla alindi." "Ucusunuza 24 saat kaldi, check-in yapabilirsiniz." "Waylo'ya hosgeldiniz! Ilk rezervasyonunuza %10 indirim." "Ozel kis kampanyasi: tum paketlerde %25 indirim!")
    TYPES=("EMAIL" "EMAIL" "PUSH" "EMAIL" "SMS")
    RECIPIENTS=("musteri${i}@email.com" "musteri${i}@email.com" "device-token-${i}" "musteri${i}@email.com" "+90555000${i}${i}${i}${i}")
    post "/api/notifications" "{\"customerId\":\"$CID\",\"type\":\"${TYPES[$i]}\",\"recipient\":\"${RECIPIENTS[$i]}\",\"subject\":\"${SUBJECTS[$i]}\",\"body\":\"${BODIES[$i]}\"}" "${SUBJECTS[$i]}" > /dev/null
    echo "  [+] ${SUBJECTS[$i]} (${TYPES[$i]})"
  done
fi

echo ""

# =============================================
# SUMMARY
# =============================================
echo "=========================================="
echo "  SEED TAMAMLANDI - OZET"
echo "=========================================="

echo ""
echo "Airports:       $(curl -s $BASE/api/airports | python3 -c 'import sys,json; print(len(json.load(sys.stdin)["data"]))')"
echo "Flights:        $(curl -s $BASE/api/flights | python3 -c 'import sys,json; print(len(json.load(sys.stdin)["data"]))')"
echo "Hotels:         $(curl -s $BASE/api/hotels | python3 -c 'import sys,json; print(len(json.load(sys.stdin)["data"]))')"
# Rooms counted by summing hotel roomCounts
ROOM_TOTAL=$(curl -s $BASE/api/hotels | python3 -c 'import sys,json; print(sum(h["roomCount"] for h in json.load(sys.stdin)["data"]))' 2>/dev/null || echo 'N/A')
echo "Rooms:          $ROOM_TOTAL"
echo "Vehicles:       $(curl -s $BASE/api/vehicles | python3 -c 'import sys,json; print(len(json.load(sys.stdin)["data"]))')"
echo "Customers:      $(curl -s $BASE/api/customers | python3 -c 'import sys,json; print(len(json.load(sys.stdin)["data"]))')"
echo "Bookings:       $(curl -s $BASE/api/bookings | python3 -c 'import sys,json; print(len(json.load(sys.stdin)["data"]))')"
echo "Payments:       $(curl -s $BASE/api/payments | python3 -c 'import sys,json; print(len(json.load(sys.stdin)["data"]))')"
echo "Pricing Rules:  $(curl -s $BASE/api/pricing-rules | python3 -c 'import sys,json; print(len(json.load(sys.stdin)["data"]))')"
echo "Notifications:  $(curl -s $BASE/api/notifications | python3 -c 'import sys,json; print(len(json.load(sys.stdin)["data"]))')"

echo ""
echo "Done!"
