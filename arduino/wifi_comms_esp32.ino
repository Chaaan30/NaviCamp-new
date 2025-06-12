/*
 * @file wifi_comms_esp32.ino - Professional Wi-Fi Communication Hub
 * 
 * Receives strongest beacon data from BLE scanner via UART2 RX (GPIO16)
 * Input format: "BeaconName,RSSI,Distance"
 * Uploads sensor data to AWS database every 7 seconds
 * GPS coordinates set to 0.0 (awaiting Arduino Mega NEO-6M integration)
 */

#include <WiFi.h>
#include <HTTPClient.h>
#include <WiFiClientSecure.h>
#include <ArduinoJson.h>
#include <time.h>

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

// ============ BEACON DATA STORAGE ============
struct CurrentBeaconState {
  String name = "Unknown";
  int rssi = -127;
  float distance = 0.0;
  unsigned long lastUpdate = 0;
} beacon;

// ============ USER CONFIGURATION ============
String userID = "";
bool userLinked = false;
unsigned long lastUpload = 0;

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
  } else {
    Serial.println("\n✗ Wi-Fi connection failed!");
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
  if (WiFi.status() != WL_CONNECTED) {
    Serial.println("✗ Cannot fetch config - no Wi-Fi");
    return;
  }
  
  WiFiClientSecure client;
  client.setInsecure();
  client.setHandshakeTimeout(15000);
  
  HTTPClient http;
  http.setTimeout(20000);
  http.begin(client, CONFIG_URL);
  http.addHeader("Content-Type", "application/json");
  
  // Create request payload
  StaticJsonDocument<128> payload;
  payload["deviceID"] = DEVICE_ID;
  String jsonString;
  serializeJson(payload, jsonString);
  
  int responseCode = http.POST(jsonString);
  Serial.printf("Device config: %d ", responseCode);
  
  if (responseCode == 200) {
    String response = http.getString();
    Serial.println("✓");
    
    // Parse user configuration
    StaticJsonDocument<256> doc;
    if (deserializeJson(doc, response) == DeserializationError::Ok) {
      if (doc.containsKey("userID")) {
        userID = doc["userID"].as<String>();
        userLinked = (userID.length() > 0);
        Serial.printf("User status: %s\n", userLinked ? "linked" : "not linked");
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
  client.setHandshakeTimeout(15000);
  
  HTTPClient http;
  http.setTimeout(20000);
  http.begin(client, SENSOR_URL);
  http.addHeader("Content-Type", "application/json");
  
  // Create sensor payload
  StaticJsonDocument<256> payload;
  payload["deviceID"] = DEVICE_ID;
  payload["userID"] = userID;
  payload["status"] = userLinked ? "active" : "idle";
  payload["latitude"] = 0.0;          // Awaiting NEO-6M GPS integration
  payload["longitude"] = 0.0;         // Awaiting NEO-6M GPS integration
  payload["floorLevel"] = beacon.name;
  payload["rssi"] = beacon.rssi;
  payload["distance"] = beacon.distance;
  
  String jsonString;
  serializeJson(payload, jsonString);
  
  // Upload to AWS
  int responseCode = http.POST(jsonString);
  
  Serial.printf("UPLOAD: %s | %d dBm | %.1fm → %d ", 
                beacon.name.c_str(), beacon.rssi, beacon.distance, responseCode);
  
  if (responseCode == 200) {
    String response = http.getString();
    Serial.println("✓");
  } else {
    Serial.println("✗");
  }
  
  http.end();
}

// ============ BEACON DATA PROCESSING ============
void processBeaconData(const String& line) {
  if (line.length() == 0) return;
  
  // Parse: "BeaconName,RSSI,Distance"
  int comma1 = line.indexOf(',');
  int comma2 = line.indexOf(',', comma1 + 1);
  
  if (comma1 > 0 && comma2 > comma1) {
    beacon.name = line.substring(0, comma1);
    beacon.rssi = line.substring(comma1 + 1, comma2).toInt();
    beacon.distance = line.substring(comma2 + 1).toFloat();
    beacon.lastUpdate = millis();
    
    Serial.printf("RX: %s | %d dBm | %.1fm\n", 
                  beacon.name.c_str(), beacon.rssi, beacon.distance);
  }
}

// ============ MAIN PROGRAM ============
void setup() {
  Serial.begin(115200);
  Link.begin(115200, SERIAL_8N1, 16, 17);
  
  Serial.println("=== Professional Wi-Fi Communication Hub ===");
  Serial.println("Listening for strongest beacon data on UART2...");
  
  establishWiFiConnection();
  synchronizeSystemTime();
  fetchDeviceConfiguration();
  
  Serial.println("System ready - monitoring beacon proximity...");
}

void loop() {
  // Process incoming beacon data
  if (Link.available()) {
    String line = Link.readStringUntil('\n');
    line.trim();
    processBeaconData(line);
  }
  
  // Maintain Wi-Fi connection
  if (WiFi.status() != WL_CONNECTED) {
    Serial.println("Reconnecting Wi-Fi...");
    establishWiFiConnection();
  }
  
  // Upload sensor data at regular intervals
  if (millis() - lastUpload >= UPLOAD_INTERVAL) {
    uploadSensorData();
    lastUpload = millis();
  }
  
  delay(50);  // Efficient loop timing
} 