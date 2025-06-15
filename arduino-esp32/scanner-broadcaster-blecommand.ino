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

#include <BLEDevice.h>
#include <BLEUtils.h>
#include <BLEScan.h>
#include <BLEAdvertisedDevice.h>
#include <BLEAdvertising.h> // Include the advertising library
#include <map>
#include <cmath>

// UART2 configuration for Wi-Fi ESP32 communication
HardwareSerial Link(2);  // TX=GPIO17, RX=GPIO16

// --- Broadcaster Configuration ---
BLEAdvertising *pAdvertising;
const char* BROADCAST_NAME = "WC_202501";
unsigned long lastBroadcastTime = 0;
const long broadcastInterval = 3000;      // Broadcast every 3 seconds
const long broadcastDuration = 500;       // Broadcast for 500ms
bool isBroadcasting = false;

// --- Scanner Configuration ---
const char TARGET_SERVICE_UUID[] = "6E400001-B5A3-F393-E0A9-E50E24DCCA9E";

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
  
  Link.printf("%s,%d,%.1f\n", strongest.name.c_str(), strongest.rssi, strongest.distance);
  Link.flush();
  
  Serial.printf("TX → %s | %d dBm | %.1fm (strongest of %d beacons)\n",
                strongest.name.c_str(), strongest.rssi, strongest.distance, // *** FIX 1: Corrected c_c_str() to c_str() ***
                detectedBeacons.size());
}

void setup() {
  Serial.begin(115200);
  Link.begin(115200, SERIAL_8N1, 16, 17);
  
  Serial.println("=== Professional BLE Scanner & Broadcaster ===");
  
  // *** FIX 2: Initialize BLE stack with the broadcast name ***
  BLEDevice::init(BROADCAST_NAME);
  
  // --- Configure the Broadcaster ---
  pAdvertising = BLEDevice::getAdvertising();
  pAdvertising->addServiceUUID(TARGET_SERVICE_UUID);
  pAdvertising->setScanResponse(true);
  pAdvertising->setMinPreferred(0x06);
  pAdvertising->setMinPreferred(0x12);
  Serial.printf("Broadcasting as: %s\n", BROADCAST_NAME);

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
  unsigned long currentTime = millis();

  // State 1: We are currently broadcasting, check if it's time to stop.
  if (isBroadcasting) {
    if (currentTime - lastBroadcastTime > broadcastDuration) {
      pAdvertising->stop();
      isBroadcasting = false;
      //Serial.println("...broadcast stopped.");
    }
  }
  // State 2: We are not broadcasting, check if it's time to start.
  else {
    if (currentTime - lastBroadcastTime > broadcastInterval) {
      lastBroadcastTime = currentTime;
      pAdvertising->start();
      isBroadcasting = true;
      Serial.printf(">>> Broadcasting '%s'...\n", BROADCAST_NAME);
    }
  }
}

void loop() {
  // Handle the periodic broadcasting state machine
  manageBroadcaster();

  // Process the collected scan data every ~1.1 seconds
  if (millis() - lastProcessTime > 1100) {
    lastProcessTime = millis();
    transmitStrongestBeacon();
  }
  
  // A small delay to yield to other tasks and prevent watchdog timeouts
  delay(10); 
}