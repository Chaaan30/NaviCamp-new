/*
 * @file floor_beacon_esp32.ino - Professional BLE Proximity Beacon
 * 
 * Broadcasts hidden BLE advertisements for proximity detection system.
 * Uses specific service UUID for secure communication with scanner.
 * Optimized for reliable RSSI-based distance measurement.
 * 
 * IMPORTANT: Change BEACON_NAME for each deployed unit before flashing.
 */

#include <BLEDevice.h>
#include <BLEUtils.h>
#include <BLEAdvertising.h>
#include <BLEServer.h>

// ============ BEACON CONFIGURATION ============
// CHANGE THIS FOR EACH BEACON BEFORE FLASHING
const char BEACON_NAME[] = "Einstein - First Floor";  // Options: "Einstein - First Floor", "Einstein - Second Floor", "Einstein - Third Floor", etc.
// =============================================

// Service UUID - Must match scanner configuration
const char SERVICE_UUID[] = "6E400001-B5A3-F393-E0A9-E50E24DCCA9E";

// Advertising configuration for optimal range and power
const uint16_t ADVERTISING_INTERVAL = 160;  // 100ms intervals (160 * 0.625ms)

BLEAdvertising* advertising;
unsigned long lastStatusReport = 0;

void setup() {
  Serial.begin(115200);
  delay(500);  // Allow serial to stabilize
  
  Serial.println("=== Professional BLE Proximity Beacon ===");
  Serial.printf("Beacon ID: %s\n", BEACON_NAME);
  Serial.printf("Service UUID: %s\n", SERVICE_UUID);
  
  // Initialize BLE with beacon name
  BLEDevice::init(BEACON_NAME);
  
  // Create BLE server (required for service advertising)
  BLEServer* server = BLEDevice::createServer();
  
  // Create service with our UUID
  BLEService* service = server->createService(SERVICE_UUID);
  service->start();
  
  // Configure advertising
  advertising = BLEDevice::getAdvertising();
  advertising->addServiceUUID(SERVICE_UUID);
  advertising->setScanResponse(false);  // Disable scan response for efficiency
  advertising->setMinPreferred(0x06);   // Connection interval (not used)
  advertising->setMaxPreferred(0x12);   // Connection interval (not used)
  
  // Set advertising interval for consistent detection
  advertising->setMinInterval(ADVERTISING_INTERVAL);
  advertising->setMaxInterval(ADVERTISING_INTERVAL);
  
  // Start advertising
  BLEDevice::startAdvertising();
  
  Serial.printf("✓ Broadcasting at %.1f ms intervals\n", ADVERTISING_INTERVAL * 0.625);
  Serial.println("✓ Beacon active - ready for proximity detection");
  Serial.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
}

void loop() {
  // Status report every 30 seconds
  if (millis() - lastStatusReport >= 30000) {
    Serial.printf("Beacon %s operational | Uptime: %lu seconds\n", 
                  BEACON_NAME, millis() / 1000);
    lastStatusReport = millis();
  }
  
  delay(1000);  // Simple status check every second
} 