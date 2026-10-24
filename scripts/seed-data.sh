#!/usr/bin/env bash
# Waylo Mock Data Seed Script
# Tum servisleri gercekci verilerle doldurur
set -euo pipefail

BASE="http://localhost:8080"
H='Content-Type: application/json'
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ENV_FILE="$ROOT_DIR/.env"

# Safe single-key reader: never sources the file, never echoes values.
env_file_value() {
  local key="$1" line
  [ -f "$ENV_FILE" ] || return 0
  line=$(grep -m1 "^${key}=" "$ENV_FILE" || true)
  line="${line#"${key}="}"
  line="${line%$'\r'}"
  if [[ "$line" == \"*\" ]] || [[ "$line" == \'*\' ]]; then line="${line:1:${#line}-2}"; fi
  printf '%s' "$line"
}

SEED_ADMIN_EMAIL="${SEED_ADMIN_EMAIL:-}"
SEED_ADMIN_PASSWORD="${SEED_ADMIN_PASSWORD:-}"
[ -n "$SEED_ADMIN_EMAIL" ] || SEED_ADMIN_EMAIL="$(env_file_value SEED_ADMIN_EMAIL)"
[ -n "$SEED_ADMIN_PASSWORD" ] || SEED_ADMIN_PASSWORD="$(env_file_value SEED_ADMIN_PASSWORD)"
[ -n "$SEED_ADMIN_EMAIL" ] || SEED_ADMIN_EMAIL="$(env_file_value WAYLO_BOOTSTRAP_ADMIN_EMAIL)"
[ -n "$SEED_ADMIN_PASSWORD" ] || SEED_ADMIN_PASSWORD="$(env_file_value WAYLO_BOOTSTRAP_ADMIN_PASSWORD)"
if [ -z "$SEED_ADMIN_EMAIL" ] || [ -z "$SEED_ADMIN_PASSWORD" ]; then
  echo "HATA: Admin bilgisi yok. SEED_ADMIN_EMAIL ve SEED_ADMIN_PASSWORD ortam degiskenlerini ayarlayin" >&2
  echo "      (veya .env icinde WAYLO_BOOTSTRAP_ADMIN_EMAIL / WAYLO_BOOTSTRAP_ADMIN_PASSWORD tanimlayip auth-service'i yeniden baslatin)." >&2
  exit 1
fi

TOKEN=""
FAIL_COUNT=0
FAIL_COUNT_FILE=$(mktemp)
printf '0\n' > "$FAIL_COUNT_FILE"
SEED_HDRS=$(mktemp)
trap 'rm -f "$FAIL_COUNT_FILE" "$SEED_HDRS"' EXIT
# Access token omru 15 dk: her bolumun basinda yeniden giris yapilir.
login() {
  local payload resp
  payload=$(SEED_ADMIN_EMAIL="$SEED_ADMIN_EMAIL" SEED_ADMIN_PASSWORD="$SEED_ADMIN_PASSWORD" python3 -c 'import json,os; print(json.dumps({"email":os.environ["SEED_ADMIN_EMAIL"],"password":os.environ["SEED_ADMIN_PASSWORD"]}))')
  resp=$(curl -s -X POST "$BASE/api/auth/login" -H "$H" -d "$payload" || true)
  TOKEN=$(echo "$resp" | python3 -c 'import sys,json; print(json.load(sys.stdin)["data"]["token"])' 2>/dev/null || echo "")
  if [ -z "$TOKEN" ]; then
    echo "HATA: admin girisi basarisiz (kimlik bilgilerini ve auth-service bootstrap'ini kontrol edin)" >&2
    exit 1
  fi
}

post() {
  local path="$1" body="$2" label="$3"
  RESP=$(curl -s -w '\n%{http_code}' -X POST "$BASE$path" -H "$H" -H "Authorization: Bearer $TOKEN" -d "$body")
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
    printf '%s\n' "$(( $(cat "$FAIL_COUNT_FILE") + 1 ))" > "$FAIL_COUNT_FILE"
    echo "  [FAIL:$CODE] $label"
    echo "$BODY" | head -1
    echo ""
  fi
}

get_data() {
  local path="$1"
  curl -s "$BASE$path" -H "Authorization: Bearer $TOKEN" | python3 -c 'import sys,json; print(json.dumps(json.load(sys.stdin).get("data", [])))'
}

find_id_by_field() {
  local json="$1" field="$2" value="$3"
  FIELD="$field" VALUE="$value" python3 -c 'import json,os,sys
items=json.loads(sys.stdin.read())
for item in items if isinstance(items,list) else []:
    if str(item.get(os.environ["FIELD"], "")) == os.environ["VALUE"]:
        print(item.get("id", "")); break' <<< "$json"
}

find_id_by_fields() {
  local json="$1" fields_json="$2"
  FIELDS="$fields_json" python3 -c 'import json,os,sys
items=json.loads(sys.stdin.read())
fields=json.loads(os.environ["FIELDS"])
for item in items if isinstance(items,list) else []:
    if all(str(item.get(k, "")) == str(v) for k,v in fields.items()):
        print(item.get("id", "")); break' <<< "$json"
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
login

declare -a AIRPORT_CODES=()

create_airport() {
  local code="$1" name="$2" city="$3" country="$4" tz="$5" lat="$6" lon="$7"
  local result existing
  existing=$(find_id_by_field "$(get_data "/api/airports")" "iataCode" "$code")
  if [ -n "$existing" ]; then
    echo "  [SKIP] $code - $name (zaten var)"
    return
  fi
  result=$(post "/api/airports" "{\"iataCode\":\"$code\",\"name\":\"$name\",\"city\":\"$city\",\"country\":\"$country\",\"timezone\":\"$tz\",\"latitude\":$lat,\"longitude\":$lon}" "$code - $name")
  AIRPORT_CODES+=("$code")
}

create_airport "IST" "Istanbul Havalimani"      "Istanbul"   "Turkey" "Europe/Istanbul"    41.2753  28.7519
create_airport "SAW" "Sabiha Gokcen Havalimani"  "Istanbul"   "Turkey" "Europe/Istanbul"    40.8986  29.3092
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
login

declare -a FLIGHT_IDS=()
declare -a FLIGHT_NUMBERS=()

create_flight() {
  local fn="$1" from="$2" to="$3" dep="$4" arr="$5" airline="$6"
  local result existing
  existing=$(curl -s "$BASE/api/flights/number/$fn" | python3 -c 'import sys,json;
try: print(json.load(sys.stdin)["data"]["id"])
except Exception: pass' 2>/dev/null || true)
  if [ -n "$existing" ]; then
    echo "  [SKIP] $fn $from->$to (zaten var)"
    FLIGHT_IDS+=("$existing")
    FLIGHT_NUMBERS+=("$fn")
    return
  fi
  result=$(post "/api/flights" "{\"flightNumber\":\"$fn\",\"departureAirportCode\":\"$from\",\"arrivalAirportCode\":\"$to\",\"departureTime\":\"$dep\",\"arrivalTime\":\"$arr\",\"airline\":\"$airline\"}" "$fn $from->$to")
  local id
  id=$(echo "$result" | extract_id)
  if [ -n "$id" ]; then FLIGHT_IDS+=("$id"); fi
  FLIGHT_NUMBERS+=("$fn")
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
echo "--- 2b. KOLTUKLAR (satir 1-20, ABCDEF, ECONOMY 1500.00) ---"
login
for fn in "${FLIGHT_NUMBERS[@]}"; do
  existing=$(get_data "/api/seat-inventory/seats/$fn" | python3 -c 'import sys,json; print("1" if json.load(sys.stdin) else "")')
  if [ -n "$existing" ]; then
    echo "  [SKIP] Koltuklar $fn (zaten var)"
  else
    result=$(post "/api/seat-inventory/seats/bulk" "{\"flightNumber\":\"$fn\",\"seatType\":\"ECONOMY\",\"price\":1500.00,\"fromRow\":1,\"toRow\":20,\"letters\":\"ABCDEF\"}" "Koltuklar $fn")
    echo "$result" | head -1
  fi
done

echo ""

# =============================================
# 3. HOTELS
# =============================================
echo "--- 3. OTELLER ---"
login

declare -a HOTEL_IDS=()

# hotel-service her otel icin organizationId ister ve yazma izni ORGANIZATION_OWNER'dan gelir.
# Admin kullanici bir organizasyon olusturur (otomatik owner olur), admin endpoint'i ile onaylanir.
ORG_NAME="Waylo Seed Organization"
ORG_ID=$(curl -s "$BASE/api/me/memberships" -H "Authorization: Bearer $TOKEN" | ORG_NAME="$ORG_NAME" python3 -c '
import sys,json,os
try:
    for m in json.load(sys.stdin)["data"]:
        if m["organizationName"] == os.environ["ORG_NAME"] and m["organizationStatus"] in ("ACTIVE","PENDING"):
            print(m["organizationId"] + " " + m["organizationStatus"]); break
except Exception:
    pass
' 2>/dev/null || echo "")
ORG_STATUS="${ORG_ID#* }"
ORG_ID="${ORG_ID%% *}"
if [ -z "$ORG_ID" ]; then
  ORG_ID=$(curl -s -X POST "$BASE/api/organizations" -H "$H" -H "Authorization: Bearer $TOKEN" -d "{\"name\":\"$ORG_NAME\"}" | python3 -c 'import sys,json; print(json.load(sys.stdin)["data"]["id"])' 2>/dev/null || echo "")
  ORG_STATUS="PENDING"
fi
if [ -n "$ORG_ID" ] && [ "$ORG_STATUS" = "PENDING" ]; then
  curl -s -o /dev/null -X POST "$BASE/api/admin/organizations/$ORG_ID/approve" -H "Authorization: Bearer $TOKEN" || true
fi
if [ -n "$ORG_ID" ]; then
  echo "  [OK] Organizasyon hazir: $ORG_ID"
else
  echo "  [!] Organizasyon olusturulamadi, oteller ve odalar atlaniyor"
fi

create_hotel() {
  local name="$1" city="$2" country="$3" addr="$4" star="$5" desc="$6"
  [ -n "$ORG_ID" ] || return 0
  local result existing
  existing=$(get_data "/api/hotels?organizationId=$ORG_ID" | NAME="$name" python3 -c 'import json,os,sys
for h in json.loads(sys.stdin.read()):
    if h.get("name") == os.environ["NAME"]:
        print(h.get("id", "")); break')
  if [ -n "$existing" ]; then
    echo "  [SKIP] $name (zaten var)"
    HOTEL_IDS+=("$existing")
    return
  fi
  result=$(post "/api/hotels" "{\"name\":\"$name\",\"city\":\"$city\",\"country\":\"$country\",\"address\":\"$addr\",\"starRating\":$star,\"description\":\"$desc\",\"organizationId\":\"$ORG_ID\"}" "$name")
  local id
  id=$(echo "$result" | extract_id)
  if [ -n "$id" ]; then HOTEL_IDS+=("$id"); fi
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
login

create_room() {
  local hotel_id="$1" num="$2" type="$3" cap="$4" price="$5" floor="$6" desc="$7"
  if [ -z "$hotel_id" ]; then return; fi
  local existing
  existing=$(find_id_by_field "$(get_data "/api/hotels/$hotel_id/rooms")" "roomNumber" "$num")
  if [ -n "$existing" ]; then
    echo "  [SKIP] Oda $num ($type) (zaten var)"
    return
  fi
  result=$(post "/api/hotels/$hotel_id/rooms" "{\"roomNumber\":\"$num\",\"roomType\":\"$type\",\"capacity\":$cap,\"pricePerNight\":$price,\"floor\":$floor,\"description\":\"$desc\"}" "Oda $num ($type)")
  echo "$result" | head -1
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
login

create_vehicle() {
  local plate="$1" brand="$2" model="$3" year="$4" type="$5" seat="$6" rate="$7" loc="$8" color="$9" mileage="${10}"
  local existing
  existing=$(curl -s "$BASE/api/vehicles/plate/$plate" | python3 -c 'import sys,json;
try: print(json.load(sys.stdin)["data"]["id"])
except Exception: pass' 2>/dev/null || true)
  if [ -n "$existing" ]; then
    echo "  [SKIP] $brand $model ($plate) (zaten var)"
    return
  fi
  result=$(post "/api/vehicles" "{\"plateNumber\":\"$plate\",\"brand\":\"$brand\",\"model\":\"$model\",\"year\":$year,\"vehicleType\":\"$type\",\"seatCount\":$seat,\"dailyRate\":$rate,\"currency\":\"TRY\",\"location\":\"$loc\",\"color\":\"$color\",\"mileage\":$mileage}" "$brand $model ($plate)")
  echo "$result" | head -1
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
login

declare -a CUSTOMER_IDS=()

SEED_CUSTOMER_PASSWORD="${SEED_CUSTOMER_PASSWORD:-}"
if [ -z "$SEED_CUSTOMER_PASSWORD" ]; then
  SEED_CUSTOMER_PASSWORD=$(python3 -c 'import secrets;print(secrets.token_urlsafe(16))')
  echo "  [INFO] SEED_CUSTOMER_PASSWORD ayarlanmadigi icin bu calisma icin bir sifre uretildi"
fi

create_customer() {
  local fn="$1" ln="$2" email="$3" phone="$4" passport="$5"
  # CRM phone/passport updates are not authenticated endpoints, so these fields are intentionally not posted.
  local body response code id
  # already registered? (admin lookup, no register call -> no rate-limit cost on re-runs)
  code=$(curl -s -o /dev/null -w '%{http_code}' -G "$BASE/api/admin/users" --data-urlencode "email=$email" -H "Authorization: Bearer $TOKEN")
  if [ "$code" = "200" ]; then
    echo "  [SKIP] $fn $ln (zaten kayitli)"
    return
  fi
  body=$(FN="$fn" LN="$ln" EMAIL="$email" PASSWORD="$SEED_CUSTOMER_PASSWORD" python3 -c 'import json,os; print(json.dumps({"firstName":os.environ["FN"],"lastName":os.environ["LN"],"email":os.environ["EMAIL"],"password":os.environ["PASSWORD"]}))')
  response=$(curl -s -D "$SEED_HDRS" -w '\n%{http_code}' -X POST "$BASE/api/auth/register" -H "$H" -d "$body")
  code=$(echo "$response" | tail -1)
  if [ "$code" = "429" ]; then
    # gateway limits register per IP (5/min): wait for the window the gateway tells us, then retry once
    wait_s=$(grep -i '^retry-after:' "$SEED_HDRS" | tr -dc '0-9'); wait_s=${wait_s:-60}
    echo "  [WAIT] register limiti, ${wait_s}s bekleniyor"
    sleep "$wait_s"
    response=$(curl -s -D "$SEED_HDRS" -w '\n%{http_code}' -X POST "$BASE/api/auth/register" -H "$H" -d "$body")
    code=$(echo "$response" | tail -1)
  fi
  response=$(echo "$response" | sed '$d')
  if [ "$code" = "201" ] || [ "$code" = "200" ]; then
    id=$(echo "$response" | python3 -c 'import sys,json; print(json.load(sys.stdin)["data"]["userId"])' 2>/dev/null || echo "")
    echo "  [OK] $fn $ln -> $id"
  elif [ "$code" = "409" ]; then
    echo "  [SKIP] $fn $ln (zaten kayitli)"
    id=""
  else
    printf '%s\n' "$(( $(cat "$FAIL_COUNT_FILE") + 1 ))" > "$FAIL_COUNT_FILE"
    echo "  [FAIL:$code] $fn $ln"
    echo "$response" | head -1
    id=""
  fi
  if [ -n "$id" ]; then CUSTOMER_IDS+=("$id"); fi
}

create_customer "Mehmet"   "Kaya"      "mehmet.kaya@example.com"      "+905321112233" "TR12345678"
create_customer "Ayse"     "Demir"     "ayse.demir@example.com"       "+905339998877" "TR23456789"
create_customer "Fatma"    "Celik"     "fatma.celik@example.com"      "+905347776655" "TR34567890"
create_customer "Mustafa"  "Sahin"     "mustafa.sahin@example.com"    "+905355554433" "TR45678901"
create_customer "Zeynep"   "Yildiz"    "zeynep.yildiz@example.com"    "+905363332211" "TR56789012"
create_customer "Emre"     "Ozturk"    "emre.ozturk@example.com"      "+905371110099" "TR67890123"
create_customer "Elif"     "Arslan"    "elif.arslan@example.com"      "+905389998877" ""
create_customer "Burak"    "Dogan"     "burak.dogan@example.com"      "+905391112233" "TR89012345"
create_customer "Selin"    "Acar"      "selin.acar@example.com"       "+905401234567" ""
create_customer "Kerem"    "Polat"     "kerem.polat@example.com"      "+905417654321" "TR01234567"
create_customer "Deniz"    "Koc"       "deniz.koc@example.com"        "+905428765432" "TR11223344"
create_customer "Hakan"    "Erdem"     "hakan.erdem@example.com"      "+905439876543" ""
create_customer "Gamze"    "Tekin"     "gamze.tekin@example.com"      "+905441122334" "TR22334455"
create_customer "Cem"      "Gunes"     "cem.gunes@example.com"        "+905455566778" "TR33445566"
create_customer "Pinar"    "Bulut"     "pinar.bulut@example.com"      "+905466677889" ""

echo ""

# =============================================
# 7. PRICING RULES
# =============================================
echo "--- 7. FIYATLANDIRMA KURALLARI ---"
login

create_pricing_rule() {
  local name="$1" body="$2" existing
  existing=$(find_id_by_field "$(get_data "/api/pricing-rules")" "name" "$name")
  if [ -n "$existing" ]; then
    echo "  [SKIP] $name (zaten var)"
    return
  fi
  post "/api/pricing-rules" "$body" "$name" > /dev/null
}

create_pricing_rule "Ekonomi Ucus" '{"name":"Ekonomi Ucus","pricingType":"FLIGHT","basePrice":500,"discountPercentage":0,"currency":"TRY","description":"Yurt ici ekonomi ucus fiyati"}'
create_pricing_rule "Business Ucus" '{"name":"Business Ucus","pricingType":"FLIGHT","basePrice":1500,"discountPercentage":0,"currency":"TRY","description":"Business class ucus fiyati"}'
create_pricing_rule "Dis Hat Ucus" '{"name":"Dis Hat Ucus","pricingType":"FLIGHT","basePrice":3000,"discountPercentage":0,"currency":"TRY","description":"Avrupa ucus fiyati"}'
create_pricing_rule "Standart Oda" '{"name":"Standart Oda","pricingType":"HOTEL","basePrice":400,"discountPercentage":0,"currency":"TRY","description":"Standart otel oda fiyati"}'
create_pricing_rule "Suite Oda" '{"name":"Suite Oda","pricingType":"HOTEL","basePrice":1200,"discountPercentage":0,"currency":"TRY","description":"Suite oda fiyati"}'
create_pricing_rule "Ekonomi Arac" '{"name":"Ekonomi Arac","pricingType":"CAR_RENTAL","basePrice":200,"discountPercentage":0,"currency":"TRY","description":"Ekonomi arac gunluk fiyat"}'
create_pricing_rule "SUV Arac" '{"name":"SUV Arac","pricingType":"CAR_RENTAL","basePrice":600,"discountPercentage":0,"currency":"TRY","description":"SUV arac gunluk fiyat"}'
create_pricing_rule "Erken Rezervasyon" '{"name":"Erken Rezervasyon","pricingType":"PACKAGE_DISCOUNT","basePrice":0,"discountPercentage":15,"currency":"TRY","description":"30 gun oncesi erken rezervasyon indirimi"}'
create_pricing_rule "Aile Paketi" '{"name":"Aile Paketi","pricingType":"PACKAGE_DISCOUNT","basePrice":0,"discountPercentage":10,"currency":"TRY","description":"4+ kisi aile paketi indirimi"}'
create_pricing_rule "Sadakat Indirimi" '{"name":"Sadakat Indirimi","pricingType":"PACKAGE_DISCOUNT","basePrice":0,"discountPercentage":20,"currency":"TRY","description":"Gold ve Platinum musterilere ozel"}'

echo ""

# Transactional data only comes from real user flows.

echo ""

# =============================================
# SUMMARY
# =============================================
echo "=========================================="
echo "  SEED TAMAMLANDI - OZET"
echo "=========================================="

count_endpoint() {
  local path="$1" expression="$2" response code body
  response=$(curl -s -w '\n%{http_code}' "$BASE$path" -H "Authorization: Bearer $TOKEN" || true)
  code=$(echo "$response" | tail -1)
  body=$(echo "$response" | sed '$d')
  BODY="$body" CODE="$code" EXPRESSION="$expression" python3 -c '
import json, os
if os.environ["CODE"] not in {"200", "201"}:
    print("N/A (HTTP " + os.environ["CODE"] + ")")
    raise SystemExit
try:
    data=json.loads(os.environ["BODY"]).get("data", [])
    print(eval(os.environ["EXPRESSION"], {"data": data}))
except Exception:
    print("N/A (HTTP " + os.environ["CODE"] + ")")
'
}

login
ROOM_TOTAL=0
ROOM_STATUS=200
for hotel_id in "${HOTEL_IDS[@]}"; do
  room_response=$(curl -s -w '\n%{http_code}' "$BASE/api/hotels/$hotel_id/rooms" -H "Authorization: Bearer $TOKEN" || true)
  room_code=$(echo "$room_response" | tail -1)
  room_body=$(echo "$room_response" | sed '$d')
  if [ "$room_code" != "200" ]; then
    ROOM_STATUS="$room_code"
    continue
  fi
  room_count=$(BODY="$room_body" python3 -c 'import os,json; print(len(json.loads(os.environ["BODY"]).get("data", [])))' 2>/dev/null || echo "")
  if [ -n "$room_count" ]; then ROOM_TOTAL=$((ROOM_TOTAL + room_count)); else ROOM_STATUS=200; fi
done
if [ "$ROOM_TOTAL" = "0" ] && [ "$ROOM_STATUS" != "200" ]; then ROOM_TOTAL="N/A (HTTP $ROOM_STATUS)"; fi

SEAT_TOTAL=0
SEAT_STATUS=200
for flight_number in "${FLIGHT_NUMBERS[@]}"; do
  seat_response=$(curl -s -w '\n%{http_code}' "$BASE/api/seat-inventory/seats/$flight_number/count" -H "Authorization: Bearer $TOKEN" || true)
  seat_code=$(echo "$seat_response" | tail -1)
  seat_body=$(echo "$seat_response" | sed '$d')
  if [ "$seat_code" != "200" ]; then SEAT_STATUS="$seat_code"; continue; fi
  seat_count=$(BODY="$seat_body" python3 -c 'import os,json; print(json.loads(os.environ["BODY"]).get("data", 0))' 2>/dev/null || echo "")
  if [[ "$seat_count" =~ ^[0-9]+$ ]]; then SEAT_TOTAL=$((SEAT_TOTAL + seat_count)); else SEAT_STATUS=200; fi
done
if [ "$SEAT_TOTAL" = "0" ] && [ "$SEAT_STATUS" != "200" ]; then SEAT_TOTAL="N/A (HTTP $SEAT_STATUS)"; fi

echo ""
echo "Airports:       $(count_endpoint "/api/airports" "len(data)")"
echo "Flights:        $(count_endpoint "/api/flights" "len(data)")"
echo "Seats:          $SEAT_TOTAL"
echo "Hotels:         $(count_endpoint "/api/hotels" "len(data)")"
echo "Rooms:          $ROOM_TOTAL"
echo "Vehicles:       $(count_endpoint "/api/vehicles" "len(data)")"
echo "Customers:      $(count_endpoint "/api/customers" "len(data)")"
echo "Pricing Rules:  $(count_endpoint "/api/pricing-rules" "len(data)")"

FAIL_COUNT=$(cat "$FAIL_COUNT_FILE")
echo ""
echo "Done!"
echo "Failures:       $FAIL_COUNT"

if [ "$FAIL_COUNT" -gt 0 ]; then
  exit 1
fi
exit 0
