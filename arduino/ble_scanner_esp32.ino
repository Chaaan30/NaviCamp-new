/*
 * @file ble_scanner_esp32.ino - Professional BLE Proximity Scanner
 * 
 * Scans for hidden BLE beacons and selects the strongest signal (closest beacon).
 * Calibrated for 1-meter distance estimation using RSSI path-loss model.
 * Transmits strongest beacon data: "BeaconName,RSSI,Distance" via UART2.
 * 
 * Hardware: ESP32 with UART2 TX (GPIO17) → Wi-Fi ESP32 RX (GPIO16)
 */

#include <BLEDevice.h>
#include <BLEUtils.h>
#include <BLEScan.h>
#include <BLEAdvertisedDevice.h>
#include <map>
#include <cmath>

// UART2 configuration for Wi-Fi ESP32 communication
HardwareSerial Link(2);  // TX=GPIO17, RX=GPIO16

// BLE service UUID for our beacon network
const char TARGET_SERVICE_UUID[] = "6E400001-B5A3-F393-E0A9-E50E24DCCA9E";

// RSSI calibration parameters - AI Auto-Calibration System
float RSSI_AT_1M_CLEAR = -75.0f;        // Calibration for clear line-of-sight (adjusted for your hardware)
float RSSI_AT_1M_BLOCKED = -85.0f;      // Calibration for through walls/obstacles
float CURRENT_RSSI_AT_1M = -75.0f;      // Currently active calibration
const float PATH_LOSS_EXPONENT = 2.0f;
const float LEARNING_RATE = 0.2f;       // Faster learning rate

// Beacon tracking structure with RSSI smoothing
struct BeaconData {
  String name;
  int rssi;
  int rssiHistory[3] = {-127, -127, -127};  // Last 3 RSSI values for smoothing
  int historyIndex = 0;
  float distance;
  unsigned long lastSeen;
};

std::map<String, BeaconData> detectedBeacons;
BLEScan* scanner;

// AI Calibration System with Environmental Detection
struct CalibrationAI {
  float rssiHistory[10];
  float distanceHistory[10];
  float rssiVariance[10];               // Track signal stability
  int sampleCount = 0;
  int historyIndex = 0;
  float confidence = 0.0f;
  unsigned long lastCalibration = 0;
  bool obstacleDetected = false;        // AI detected obstacle
  int stableReadings = 0;               // Count of stable RSSI readings
  
  void addSample(float rssi, float estimatedDistance) {
    // Calculate RSSI variance (signal stability indicator)
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
    
    // Detect environment type
    detectEnvironment();
    
    // Auto-calibrate every 3 seconds if we have enough samples
    if (millis() - lastCalibration > 3000 && sampleCount >= 3) {
      performAICalibration();
      lastCalibration = millis();
    }
  }
  
  void detectEnvironment() {
    if (sampleCount < 3) return;
    
    // Calculate average variance over last few readings
    float avgVariance = 0;
    for (int i = 0; i < min(sampleCount, 5); i++) {
      avgVariance += rssiVariance[(historyIndex - 1 - i + 10) % 10];
    }
    avgVariance /= min(sampleCount, 5);
    
    // More sensitive detection for weaker RSSI hardware
    // Lower variance threshold since your signals are naturally weaker
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
    // More aggressive calibration for faster learning
    if (sampleCount < 2) return;
    
    // Find best reference sample for current environment
    float bestRSSI = -80.0f;
    float bestDistance = 999.0f;
    
    for (int i = 0; i < sampleCount; i++) {
      float dist = distanceHistory[i];
      if (dist > 0.2f && dist < 5.0f) {  // Wider range for better learning
        if (abs(dist - 1.0f) < abs(bestDistance - 1.0f)) {
          bestDistance = dist;
          bestRSSI = rssiHistory[i];
        }
      }
    }
    
    if (bestDistance < 8.0f) {
      // Calculate target RSSI for current environment
      float targetRSSI = bestRSSI - (10.0f * PATH_LOSS_EXPONENT * log10(bestDistance));
      
      // Update the appropriate calibration mode
      float* targetCalibration = obstacleDetected ? &RSSI_AT_1M_BLOCKED : &RSSI_AT_1M_CLEAR;
      float oldRSSI = *targetCalibration;
      
      *targetCalibration = (*targetCalibration) * (1.0f - LEARNING_RATE) + targetRSSI * LEARNING_RATE;
      CURRENT_RSSI_AT_1M = *targetCalibration;
      
      // Update confidence
      float adjustment = abs(*targetCalibration - oldRSSI);
      confidence = max(0.0f, min(1.0f, confidence + (adjustment < 3.0f ? 0.2f : -0.1f)));
      
      Serial.printf("🤖 AI %s Cal: %.1f→%.1f | Conf: %.0f%% | Dist: %.1fm\n", 
                   obstacleDetected ? "WALL" : "CLEAR",
                   oldRSSI, *targetCalibration, confidence * 100, bestDistance);
    }
  }
} aiCalibration;

// Calculate distance using environment-aware AI calibration
float calculateDistance(int rssi) {
  if (rssi >= 0 || rssi < -100) return 99.9;
  
  if (rssi > -20) rssi = -20;
  if (rssi < -95) rssi = -95;
  
  // Use current environment-appropriate calibration
  float ratio = (CURRENT_RSSI_AT_1M - (float)rssi) / (10.0f * PATH_LOSS_EXPONENT);
  float distance = pow(10.0f, ratio);
  
  // Add sample to AI learning system
  aiCalibration.addSample((float)rssi, distance);
  
  if (distance < 0.1) distance = 0.1;
  if (distance > 50.0) distance = 50.0;
  
  return distance;
}

// Smooth RSSI to reduce signal jumps
int smoothRSSI(BeaconData &beacon, int newRSSI) {
  beacon.rssiHistory[beacon.historyIndex] = newRSSI;
  beacon.historyIndex = (beacon.historyIndex + 1) % 3;
  
  // Calculate average of last 3 readings
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

// BLE advertisement callback handler
class BeaconScanCallback : public BLEAdvertisedDeviceCallbacks {
public:
  void onResult(BLEAdvertisedDevice device) override {
    String deviceName = device.getName().c_str();
    
    // Check if device name contains "Einstein" or matches our beacon pattern
    if (deviceName.length() == 0) return;  // Skip unnamed devices
    
    if (deviceName.indexOf("Einstein") >= 0 || 
        deviceName.indexOf("Home") >= 0 ||
        deviceName.indexOf("Floor") >= 0) {
      
      int rawRSSI = device.getRSSI();
      
      // Get or create beacon data
      if (detectedBeacons.find(deviceName) == detectedBeacons.end()) {
        BeaconData newBeacon;
        newBeacon.name = deviceName;
        detectedBeacons[deviceName] = newBeacon;
      }
      
      // Smooth RSSI to reduce jumps
      int smoothedRSSI = smoothRSSI(detectedBeacons[deviceName], rawRSSI);
      float distance = calculateDistance(smoothedRSSI);
      
      // Update beacon data
      detectedBeacons[deviceName].rssi = smoothedRSSI;
      detectedBeacons[deviceName].distance = distance;
      detectedBeacons[deviceName].lastSeen = millis();
      
      Serial.printf("Detected: %s | Raw:%d Smooth:%d | %.1fm\n", 
                    deviceName.c_str(), rawRSSI, smoothedRSSI, distance);
    } else {
      // Debug: show all devices to help troubleshoot
      Serial.printf("Other device: %s | %d dBm\n", 
                    deviceName.c_str(), device.getRSSI());
    }
  }
};

// Find and transmit strongest (closest) beacon
void transmitStrongestBeacon() {
  if (detectedBeacons.empty()) {
    Serial.println("No beacons detected in range");
    return;
  }
  
  // Remove stale entries (older than 5 seconds)
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
  
  // Find strongest signal (highest RSSI = closest beacon)
  BeaconData strongest;
  strongest.name = "";
  strongest.rssi = -127;
  strongest.distance = 999.0f;
  strongest.lastSeen = 0;
  
  for (const auto& pair : detectedBeacons) {
    if (pair.second.rssi > strongest.rssi) {
      strongest = pair.second;
    }
  }
  
  // Transmit strongest beacon to Wi-Fi ESP32
  Link.printf("%s,%d,%.1f\n", strongest.name.c_str(), strongest.rssi, strongest.distance);
  Link.flush();
  
  Serial.printf("TX → %s | %d dBm | %.1fm (strongest of %d beacons)\n",
                strongest.name.c_str(), strongest.rssi, strongest.distance, 
                detectedBeacons.size());
}

void setup() {
  Serial.begin(115200);
  Link.begin(115200, SERIAL_8N1, 16, 17);
  
  Serial.println("=== Professional BLE Proximity Scanner ===");
  Serial.printf("Target UUID: %s\n", TARGET_SERVICE_UUID);
  Serial.printf("RSSI@1m: %d dBm | Path Loss: %.1f\n", CURRENT_RSSI_AT_1M, PATH_LOSS_EXPONENT);
  
  // Initialize BLE subsystem
  BLEDevice::init("");
  scanner = BLEDevice::getScan();
  scanner->setAdvertisedDeviceCallbacks(new BeaconScanCallback());
  scanner->setActiveScan(true);    // Active scan for better sensitivity
  scanner->setInterval(100);       // 100ms scan interval
  scanner->setWindow(90);          // 90ms scan window
  
  Serial.println("Scanner initialized - Starting proximity detection...");
}

void loop() {
  // Perform 1-second scan cycle
  scanner->start(1, false);
  scanner->clearResults();  // Clear for fresh detection cycle
  
  // Process and transmit strongest beacon
  transmitStrongestBeacon();
  
  // Brief pause before next cycle
  delay(100);
} 