/*
 * @file wifi_comms_esp32.ino - Professional Wi-Fi Communication Hub
 * 
 * Receives strongest beacon data from BLE scanner via UART2 RX (GPIO16)
 * Input format: "BeaconName,RSSI,Distance"
 * Uploads sensor data to AWS database every 7 seconds
 * Integrates NEO-6M GPS data via UART1 (GPIO4)
 */

#include <WiFi.h>
#include <HTTPClient.h>
#include <WiFiClientSecure.h>
#include <ArduinoJson.h>
#include <time.h>
#include <TinyGPSPlus.h>

#define LED_WIFI GPIO_NUM_2  // Blue LED usually connected to GPIO2

// ============ NETWORK CONFIGURATION ============
const char* WIFI_SSID = "DELOS_SANTOS_2K24";
const char* WIFI_PASSWORD = "DSFAM2K24";

// ============ AWS API ENDPOINTS ============
const String DEVICE_ID = "202501";
const char* CONFIG_URL = "https://sd7slrvf6g.execute-api.ap-southeast-1.amazonaws.com/prod/device-config";
const char* SENSOR_URL = "https://sd7slrvf6g.execute-api.ap-southeast-1.amazonaws.com/prod/sensor";

// ============ TIMING CONFIGURATION ============
const uint32_t UPLOAD_INTERVAL = 7000;  // Upload every 7 seconds

// ============ HARDWARE INTERFACES ============
HardwareSerial Link(2);  // UART2: RX=GPIO16 (from BLE scanner), TX=GPIO17 (unused)
HardwareSerial GPS(1);   // UART1: RX=GPIO4 (from GPS), TX=not used
TinyGPSPlus gps;

// ============ BEACON DATA STORAGE ============
struct CurrentBeaconState {
  String name = "Unknown";
  int rssi = -0;
  float distance = 0.0;
  unsigned long lastUpdate = 0;
} beacon;

// ============ DEVICE STATE ============
String userID = "";
unsigned long lastUpload = 0;
float latitude = 0.0;
float longitude = 0.0;

// ============ NETWORK FUNCTIONS ============
void establishWiFiConnection() {
  WiFi.mode(WIFI_STA);
  WiFi.begin(WIFI_SSID, WIFI_PASSWORD);
  Serial.printf("Connecting to %s", WIFI_SSID);
  uint32_t startTime = millis();

  while (WiFi.status() != WL_CONNECTED && millis() - startTime < 20000) {
    Serial.print(".");
    delay(500);
  }

  if (WiFi.status() == WL_CONNECTED) {
    Serial.printf("\n✓ Wi-Fi connected: %s\n", WiFi.localIP().toString().c_str());
    digitalWrite(LED_WIFI, HIGH);
  } else {
    Serial.println("\n✗ Wi-Fi connection failed!");
    digitalWrite(LED_WIFI, LOW);
  }
}

void synchronizeSystemTime() {
  configTime(0, 0, "pool.ntp.org", "time.nist.gov");
  Serial.print("Synchronizing time");
  while (time(nullptr) < 1700000000) {
    Serial.print(".");
    delay(200);
  }
  Serial.println(" ✓");
}

void fetchDeviceConfiguration() {
  if (WiFi.status() != WL_CONNECTED) return;

  WiFiClientSecure client;
  client.setInsecure();
  HTTPClient http;
  http.begin(client, CONFIG_URL);
  http.addHeader("Content-Type", "application/json");

  StaticJsonDocument<128> payload;
  payload["deviceID"] = DEVICE_ID;
  String jsonString;
  serializeJson(payload, jsonString);

  int responseCode = http.POST(jsonString);
  Serial.printf("Device config: %d ", responseCode);
  if (responseCode == 200) {
    String response = http.getString();
    Serial.println("✓");
    StaticJsonDocument<256> doc;
    if (deserializeJson(doc, response) == DeserializationError::Ok) {
      if (doc.containsKey("userID")) {
        userID = doc["userID"].as<String>();
        Serial.printf("UserID: %s\n", userID.c_str());
      }
    }
  } else {
    Serial.println("✗");
  }
  http.end();
}

void uploadSensorData() {
  if (WiFi.status() != WL_CONNECTED) {
    Serial.println("✗ Upload failed - no Wi-Fi");
    return;
  }

  WiFiClientSecure client;
  client.setInsecure();
  HTTPClient http;
  http.begin(client, SENSOR_URL);
  http.addHeader("Content-Type", "application/json");

  StaticJsonDocument<256> payload;
  payload["deviceID"] = DEVICE_ID;
  payload["userID"] = userID;
  payload["latitude"] = latitude;
  payload["longitude"] = longitude;
  payload["floorLevel"] = beacon.name;
  payload["rssi"] = beacon.rssi;
  payload["distance"] = beacon.distance;

  String jsonString;
  serializeJson(payload, jsonString);

  int responseCode = http.POST(jsonString);
  Serial.printf("UPLOAD: %s | %d dBm | %.1fm → %d ",
                beacon.name.c_str(), beacon.rssi, beacon.distance, responseCode);
  Serial.println((responseCode == 200) ? "✓" : "✗");

  http.end();
}

void processBeaconData(const String& line) {
  if (line.length() == 0) return;

  int comma1 = line.indexOf(',');
  int comma2 = line.indexOf(',', comma1 + 1);

  if (comma1 > 0 && comma2 > comma1) {
    beacon.name = line.substring(0, comma1);
    beacon.rssi = line.substring(comma1 + 1, comma2).toInt();
    beacon.distance = line.substring(comma2 + 1).toFloat();
    beacon.lastUpdate = millis();

    Serial.printf("RX: %s | lat: %.6f | long: %.6f | rssi: %d | dist: %.1fm\n",
      beacon.name.c_str(), latitude, longitude, beacon.rssi, beacon.distance);
  }
}


void setup() {
  Serial.begin(115200);
  Link.begin(115200, SERIAL_8N1, 16, 17);
  GPS.begin(9600, SERIAL_8N1, 4, -1);

  pinMode(LED_WIFI, OUTPUT);
  digitalWrite(LED_WIFI, LOW);

  Serial.println("=== Professional Wi-Fi Communication Hub ===");
  Serial.println("Listening for strongest beacon data on UART2...");

  establishWiFiConnection();
  synchronizeSystemTime();
  fetchDeviceConfiguration();
  Serial.println("System ready - monitoring beacon proximity...");
}

void loop() {
  if (Link.available()) {
    String line = Link.readStringUntil('\n');
    line.trim();
    processBeaconData(line);
  }

  while (GPS.available()) {
    gps.encode(GPS.read());
  }

  if (gps.location.isUpdated() && gps.location.isValid()) {
    latitude = gps.location.lat();
    longitude = gps.location.lng();
    Serial.printf("📍 Lat: %.6f | Lng: %.6f\n", latitude, longitude);
  }


  if (WiFi.status() != WL_CONNECTED) {
    Serial.println("Reconnecting Wi-Fi...");
    establishWiFiConnection();
  }

  if (millis() - lastUpload >= UPLOAD_INTERVAL) {
    uploadSensorData();
    lastUpload = millis();
  }
}
