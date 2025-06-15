/*
 * @file ble_scanner_broadcaster_esp32.ino - Professional BLE Proximity Scanner & Broadcaster
 * 
 * Scans for hidden BLE beacons and selects the strongest signal (closest beacon).
 * Calibrated for 1-meter distance estimation using RSSI path-loss model.
 * Transmits strongest beacon data: "BeaconName,RSSI,Distance" via UART2.
 * 
 * Simultaneously broadcasts its own BLE signal with the name "WC_202501" every 3 seconds.
 * 
 * Hardware: ESP32 with UART2 TX (GPIO17) → Wi-Fi ESP32 RX (GPIO16)
 */

// Fix for RingbufferType_t compilation error in ESP32 BLE library v2.0.11
#include "esp32-hal.h"

#include <BLEDevice.h>
#include <BLEUtils.h>
#include <BLEScan.h>
#include <BLEAdvertisedDevice.h>
#include <BLEAdvertising.h>
#include <BLEServer.h>
#include <BLE2902.h>
#include <map>
#include <cmath>

// New: HardwareSerial for inter-ESP32 communication (remapped UART1)
HardwareSerial Esp32WifiSerial(1); // UART1 will be remapped: RX=GPIO27, TX=GPIO25

// --- Broadcaster Configuration ---
BLEAdvertising *pAdvertising;
const char* BROADCAST_NAME = "WC_202501";
unsigned long lastBroadcastTime = 0;
const long broadcastInterval = 3000;      // Broadcast every 3 seconds
const long broadcastDuration = 500;       // Broadcast for 500ms
bool isBroadcasting = false;

// --- New: BLE GATT Server Configuration for Line Follower Control ---
BLEServer* pServer = NULL;
BLECharacteristic* pLineFollowerCharacteristic = NULL;
bool deviceConnected = false; // Tracks if a BLE client (Android app) is connected

// New: Define Service and Characteristic UUIDs for Line Follower Control
#define SERVICE_UUID        "6E400001-B5A3-F393-E0A9-E50E24DCCA9E" // Existing Service UUID
#define LF_COMMAND_UUID     "6E400003-B5A3-F393-E0A9-E50E24DCCA9E" // New Characteristic UUID

// RSSI calibration parameters - AI Auto-Calibration System
float RSSI_AT_1M_CLEAR = -75.0f;
float RSSI_AT_1M_BLOCKED = -85.0f;
float CURRENT_RSSI_AT_1M = -75.0f;
const float PATH_LOSS_EXPONENT = 2.0f;
const float LEARNING_RATE = 0.2f;

// Beacon tracking structure with RSSI smoothing
struct BeaconData {
  String name;
  int rssi;
  int rssiHistory[3] = {-127, -127, -127};
  int historyIndex = 0;
  float distance;
  unsigned long lastSeen;
};

std::map<String, BeaconData> detectedBeacons;
BLEScan* scanner;
unsigned long lastProcessTime = 0; // Timer for processing scan results

// AI Calibration System with Environmental Detection (Unchanged)
struct CalibrationAI {
  float rssiHistory[10];
  float distanceHistory[10];
  float rssiVariance[10];
  int sampleCount = 0;
  int historyIndex = 0;
  float confidence = 0.0f;
  unsigned long lastCalibration = 0;
  bool obstacleDetected = false;
  int stableReadings = 0;
  
  void addSample(float rssi, float estimatedDistance) {
    float variance = 0.0f;
    if (sampleCount > 0) {
      float avgRSSI = 0;
      for (int i = 0; i < min(sampleCount, 5); i++) {
        avgRSSI += rssiHistory[(historyIndex - 1 - i + 10) % 10];
      }
      avgRSSI /= min(sampleCount, 5);
      variance = abs(rssi - avgRSSI);
    }
    
    rssiHistory[historyIndex] = rssi;
    distanceHistory[historyIndex] = estimatedDistance;
    rssiVariance[historyIndex] = variance;
    historyIndex = (historyIndex + 1) % 10;
    if (sampleCount < 10) sampleCount++;
    
    detectEnvironment();
    
    if (millis() - lastCalibration > 3000 && sampleCount >= 3) {
      performAICalibration();
      lastCalibration = millis();
    }
  }
  
  void detectEnvironment() {
    if (sampleCount < 3) return;
    float avgVariance = 0;
    for (int i = 0; i < min(sampleCount, 5); i++) {
      avgVariance += rssiVariance[(historyIndex - 1 - i + 10) % 10];
    }
    avgVariance /= min(sampleCount, 5);
    bool newObstacleState = avgVariance > 2.0f;
    
    if (newObstacleState != obstacleDetected) {
      obstacleDetected = newObstacleState;
      CURRENT_RSSI_AT_1M = obstacleDetected ? RSSI_AT_1M_BLOCKED : RSSI_AT_1M_CLEAR;
      Serial.printf("🌍 Environment: %s | Switching to %s mode | Variance: %.1f\n", 
                   obstacleDetected ? "OBSTACLES" : "CLEAR", 
                   obstacleDetected ? "wall-penetration" : "line-of-sight",
                   avgVariance);
    }
  }
  
  void performAICalibration() {
    if (sampleCount < 2) return;
    float bestRSSI = -80.0f;
    float bestDistance = 999.0f;
    for (int i = 0; i < sampleCount; i++) {
      float dist = distanceHistory[i];
      if (dist > 0.2f && dist < 5.0f) {
        if (abs(dist - 1.0f) < abs(bestDistance - 1.0f)) {
          bestDistance = dist;
          bestRSSI = rssiHistory[i];
        }
      }
    }
    if (bestDistance < 8.0f) {
      float targetRSSI = bestRSSI - (10.0f * PATH_LOSS_EXPONENT * log10(bestDistance));
      float* targetCalibration = obstacleDetected ? &RSSI_AT_1M_BLOCKED : &RSSI_AT_1M_CLEAR;
      float oldRSSI = *targetCalibration;
      *targetCalibration = (*targetCalibration) * (1.0f - LEARNING_RATE) + targetRSSI * LEARNING_RATE;
      CURRENT_RSSI_AT_1M = *targetCalibration;
      float adjustment = abs(*targetCalibration - oldRSSI);
      confidence = max(0.0f, min(1.0f, confidence + (adjustment < 3.0f ? 0.2f : -0.1f)));
      Serial.printf("🤖 AI %s Cal: %.1f→%.1f | Conf: %.0f%% | Dist: %.1fm\n", 
                   obstacleDetected ? "WALL" : "CLEAR",
                   oldRSSI, *targetCalibration, confidence * 100, bestDistance);
    }
  }
} aiCalibration;

// Calculate distance using environment-aware AI calibration (Unchanged)
float calculateDistance(int rssi) {
  if (rssi >= 0 || rssi < -100) return 99.9;
  if (rssi > -20) rssi = -20;
  if (rssi < -95) rssi = -95;
  float ratio = (CURRENT_RSSI_AT_1M - (float)rssi) / (10.0f * PATH_LOSS_EXPONENT);
  float distance = pow(10.0f, ratio);
  aiCalibration.addSample((float)rssi, distance);
  if (distance < 0.1) distance = 0.1;
  if (distance > 50.0) distance = 50.0;
  return distance;
}

// Smooth RSSI to reduce signal jumps (Unchanged)
int smoothRSSI(BeaconData &beacon, int newRSSI) {
  beacon.rssiHistory[beacon.historyIndex] = newRSSI;
  beacon.historyIndex = (beacon.historyIndex + 1) % 3;
  int sum = 0;
  int count = 0;
  for (int i = 0; i < 3; i++) {
    if (beacon.rssiHistory[i] != -127) {
      sum += beacon.rssiHistory[i];
      count++;
    }
  }
  return count > 0 ? sum / count : newRSSI;
}

// BLE advertisement callback handler (Unchanged)
class BeaconScanCallback : public BLEAdvertisedDeviceCallbacks {
public:
  void onResult(BLEAdvertisedDevice device) override {
    String deviceName = device.getName().c_str();
    if (deviceName.length() == 0) return;
    
    if (deviceName.indexOf("Einstein") >= 0 || 
        deviceName.indexOf("Home") >= 0 ||
        deviceName.indexOf("Floor") >= 0) {
      int rawRSSI = device.getRSSI();
      if (detectedBeacons.find(deviceName) == detectedBeacons.end()) {
        BeaconData newBeacon;
        newBeacon.name = deviceName;
        detectedBeacons[deviceName] = newBeacon;
      }
      int smoothedRSSI = smoothRSSI(detectedBeacons[deviceName], rawRSSI);
      float distance = calculateDistance(smoothedRSSI);
      detectedBeacons[deviceName].rssi = smoothedRSSI;
      detectedBeacons[deviceName].distance = distance;
      detectedBeacons[deviceName].lastSeen = millis();
    }
  }
};

// Find and transmit strongest (closest) beacon
void transmitStrongestBeacon() {
  if (detectedBeacons.empty()) {
    return;
  }
  unsigned long currentTime = millis();
  for (auto it = detectedBeacons.begin(); it != detectedBeacons.end();) {
    if (currentTime - it->second.lastSeen > 5000) {
      Serial.printf("Beacon timeout: %s\n", it->first.c_str());
      it = detectedBeacons.erase(it);
    } else {
      ++it;
    }
  }
  if (detectedBeacons.empty()) return;

  BeaconData strongest;
  strongest.rssi = -127;
  for (const auto& pair : detectedBeacons) {
    if (pair.second.rssi > strongest.rssi) {
      strongest = pair.second;
    }
  }
  
  // Transmit strongest beacon data to ESP32 WiFi via remapped UART1
  Esp32WifiSerial.printf("%s,%d,%.1f\n", strongest.name.c_str(), strongest.rssi, strongest.distance);
  Esp32WifiSerial.flush();
  
  Serial.printf("TX → %s | %d dBm | %.1fm (strongest of %d beacons)\n",
                strongest.name.c_str(), strongest.rssi, strongest.distance,
                detectedBeacons.size());
}

// New: Callback class for handling BLE server events
class MyServerCallbacks: public BLEServerCallbacks {
    void onConnect(BLEServer* pServer) {
      deviceConnected = true;
      Serial.println("BLE Client Connected for Line Follower Control!");
    };

    void onDisconnect(BLEServer* pServer) {
      deviceConnected = false;
      Serial.println("BLE Client Disconnected from Line Follower Control.");
      // Restart advertising to allow reconnection
      pAdvertising->start();
    }
};

// New: Callback class for handling characteristic write events
class MyCharacteristicCallbacks: public BLECharacteristicCallbacks {
    void onWrite(BLECharacteristic *pCharacteristic) {
      std::string value = pCharacteristic->getValue().c_str();
      if (value.length() > 0) {
        Serial.print("Received BLE command: ");
        Serial.println(value.c_str());

        // Forward command to Arduino Mega via UART2
        Serial2.println(value.c_str());
      }
    }
};

void setup() {
  Serial.begin(115200); // For USB/debugging (UART0)
  Serial2.begin(115200, SERIAL_8N1, 16, 17); // For communication to Arduino Mega (UART2)
  Esp32WifiSerial.begin(115200, SERIAL_8N1, 27, 25); // For communication to ESP32 WiFi (remapped UART1)

  Serial.println("=== Professional BLE Scanner & Broadcaster with GATT Server ===");
  
  // --- Initialize BLE Stack and GATT Server ---
  BLEDevice::init(BROADCAST_NAME); // Initialize BLE with the device name
  
  // Create the BLE Server
  pServer = BLEDevice::createServer();
  pServer->setCallbacks(new MyServerCallbacks());

  // Create the BLE Service
  BLEService *pService = pServer->createService(SERVICE_UUID);

  // Create a BLE Characteristic for Line Follower Commands - FIXED PROPERTIES
  pLineFollowerCharacteristic = pService->createCharacteristic(
                                   LF_COMMAND_UUID,
                                   BLECharacteristic::PROPERTY_WRITE | // Fixed: Use PROPERTY_WRITE instead of PROPERTY_WRITE_NO_RSP
                                   BLECharacteristic::PROPERTY_READ
                                 );
  pLineFollowerCharacteristic->setCallbacks(new MyCharacteristicCallbacks());

  // Start the service
  pService->start();

  // Set up advertising for the server (will advertise the service)
  pAdvertising = pServer->getAdvertising(); // Use pServer->getAdvertising() for server
  pAdvertising->addServiceUUID(SERVICE_UUID); // Advertise the new service UUID
  pAdvertising->setScanResponse(true);
  pAdvertising->setMinPreferred(0x06); // functions that help with iPhone connections issue
  pAdvertising->setMinPreferred(0x12);
  Serial.printf("Broadcasting as: %s with Service UUID: %s\n", BROADCAST_NAME, SERVICE_UUID);
  pAdvertising->start(); // Start advertising immediately

  // --- Configure the Scanner ---
  scanner = BLEDevice::getScan();
  scanner->setAdvertisedDeviceCallbacks(new BeaconScanCallback());
  scanner->setActiveScan(true);
  scanner->setInterval(100);
  scanner->setWindow(90);
  Serial.println("Scanner initialized - Starting continuous scan...");
  
  // Start the scan non-blockingly. It will run in the background.
  scanner->start(0, nullptr, false);
}

void manageBroadcaster() {
  // Simplified broadcaster management - removed isAdvertising() check
  if (!deviceConnected) {
      // If no client is connected, ensure advertising is running
      // Note: Removed isAdvertising() check due to library compatibility
      pAdvertising->start();
  }
}

void loop() {
  // Simplified broadcaster management
  manageBroadcaster();

  // Process the collected scan data every ~1.1 seconds
  if (millis() - lastProcessTime > 1100) {
    lastProcessTime = millis();
    transmitStrongestBeacon();
  }
  
  // A small delay to yield to other tasks and prevent watchdog timeouts
  delay(10);
}