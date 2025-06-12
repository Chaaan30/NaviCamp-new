// === NaviCamp Wheelchair – ESP-NOW + HTTPS ===
// Upload to the wheelchair-mounted ESP32
// Detects floor beacons via ESP-NOW broadcast packets
// Sends data to AWS using HTTPS while Wi-Fi STA remains connected

#include <WiFi.h>
#include <HTTPClient.h>
#include <WiFiClientSecure.h>
#include <ArduinoJson.h>
#include <esp_now.h>
#include <esp_wifi.h>
#include <time.h>

// ---------------- Wi-Fi credentials ----------------
const char* ssid     = "DELOS_SANTOS_2K24";
const char* password = "DSFAM2K24";

// ---------------- Device / Building ----------------
const String  deviceID = "202501";
const float   buildingLat = 14.24379863;
const float   buildingLng = 121.11138234;

// ---------------- AWS API endpoints ----------------
const char* CONFIG_URL = "https://sd7slrvf6g.execute-api.ap-southeast-1.amazonaws.com/prod/device-config";
const char* SENSOR_URL = "https://sd7slrvf6g.execute-api.ap-southeast-1.amazonaws.com/prod/sensor";

// post every 7 s
const uint32_t UPDATE_INTERVAL_MS = 7000;

// ---------------- Hardware ----------------
const int LED_PIN = 2;   // built-in LED

// ---------------- Runtime state ----------------
String userID         = "";
String currentFloor   = "Unknown";
bool   userLinked     = false;

float currentLat = buildingLat;
float currentLng = buildingLng;

volatile unsigned long lastBeaconMs = 0;
unsigned long lastUpdateMs          = 0;

int beaconRSSI = -127;            // last RSSI from beacon

// ========== ESP-NOW beacon payload ==========
typedef struct __attribute__((packed)) {
  char floor[12];                   // "Third Floor" etc.
} BeaconMsg;

// ========== Wi-Fi helpers ==========
void connectWiFi() {
  WiFi.mode(WIFI_STA);
  WiFi.begin(ssid, password);
  Serial.print("Connecting to Wi-Fi");
  uint32_t t0 = millis();
  while (WiFi.status() != WL_CONNECTED && millis() - t0 < 20000) {
    Serial.print('.');
    delay(500);
  }
  if (WiFi.status() == WL_CONNECTED) {
    Serial.println("\nWi-Fi connected: " + WiFi.localIP().toString());
    digitalWrite(LED_PIN, HIGH);
  } else {
    Serial.println("\nWi-Fi connection FAILED");
    digitalWrite(LED_PIN, LOW);
  }
}

// ========== Time sync ==========
void syncTime() {
  configTime(0, 0, "pool.ntp.org", "time.nist.gov");
  Serial.print("Syncing time");
  while (time(nullptr) < 1700000000) { Serial.print('.'); delay(500); }
  Serial.println(" done");
}

// ========== ESP-NOW callback ==========
void onEspNowRecv(const esp_now_recv_info_t* info, const uint8_t* data, int len) {
  if (len != sizeof(BeaconMsg)) return;
  BeaconMsg msg; memcpy(&msg, data, sizeof(msg));
  currentFloor = String(msg.floor);
  if (info->rx_ctrl) beaconRSSI = info->rx_ctrl->rssi;
  lastBeaconMs = millis();
  Serial.printf("Beacon %s RSSI=%d\n", msg.floor, beaconRSSI);
}

// ========== HTTPS helper ==========
void postJson(const char* url, const String& body, const char* tag) {
  WiFiClientSecure client; client.setInsecure(); client.setHandshakeTimeout(15000);
  HTTPClient http; http.setTimeout(20000);
  http.begin(client, url);
  http.addHeader("Content-Type", "application/json");
  int code = http.POST(body);
  Serial.printf("[%s] HTTP=%d\n", tag, code);
  if (code == 200) Serial.println(http.getString());
  http.end();
}

// ---------- /device-config ----------
void fetchConfig() {
  StaticJsonDocument<128> d; d["deviceID"] = deviceID; String payload; serializeJson(d, payload);
  postJson(CONFIG_URL, payload, "Config");
}

// ---------- /sensor ----------
void sendSensor() {
  StaticJsonDocument<256> d;
  d["deviceID"]   = deviceID;
  d["userID"]     = userID;
  d["status"]     = userLinked ? "active" : "idle";
  d["latitude"]   = currentLat;
  d["longitude"]  = currentLng;
  d["floorLevel"] = currentFloor;
  d["rssi"]       = (beaconRSSI == -127) ? WiFi.RSSI() : beaconRSSI;
  float meters = rssiToDistance(beaconRSSI);
  int metric   = (meters < 0) ? -1 : int(meters * 10 + 0.5f); // 0,10,20 per meter
  d["distance"] = metric;
  String payload; serializeJson(d, payload);
  Serial.println("POST sensor: " + payload);
  postJson(SENSOR_URL, payload, "Sensor");
}

float rssiToDistance(int rssi) {
  const int RSSI_REF = -60;    // RSSI at 1 m (your calibration)
  const float N = 2.2;         // path-loss exponent tweak as needed
  if (rssi == 0) return -1.0;
  float ratio = (RSSI_REF - rssi) / (10.0f * N);
  return powf(10.0f, ratio);   // meters
}

// ========== setup ==========
void setup() {
  pinMode(LED_PIN, OUTPUT);
  Serial.begin(115200); delay(1000);
  Serial.println("\n=== Wheelchair ESP32 – ESP-NOW ===");

  connectWiFi();
  syncTime();

  if (esp_now_init() != ESP_OK) {
    Serial.println("ESP-NOW init failed");
    return;
  }
  esp_now_register_recv_cb(onEspNowRecv);
  Serial.println("ESP-NOW ready");

  fetchConfig();
}

// ========== loop ==========
void loop() {
  if (WiFi.status() != WL_CONNECTED) connectWiFi();

  unsigned long now = millis();
  if (now - lastBeaconMs > 10000) currentFloor = "Unknown";

  if (now - lastUpdateMs > UPDATE_INTERVAL_MS) {
    lastUpdateMs = now;
    sendSensor();
  }
}