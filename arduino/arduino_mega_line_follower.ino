/*
 * NaviCamp Arduino Mega Line Follower
 * 
 * Controls wheelchair movement using 5-channel IR line tracker
 * Communicates with ESP32 for remote control functionality
 */

// IR Sensor Pins (5-channel line tracker)
const int IR_PINS[5] = {A0, A1, A2, A3, A4};  // Left to Right sensors
const int NUM_SENSORS = 5;

// Motor Driver Pins (L298N or similar)
const int MOTOR_LEFT_PWM = 5;   // Left motor speed (PWM)
const int MOTOR_LEFT_DIR1 = 6;  // Left motor direction pin 1
const int MOTOR_LEFT_DIR2 = 7;  // Left motor direction pin 2

const int MOTOR_RIGHT_PWM = 10; // Right motor speed (PWM)
const int MOTOR_RIGHT_DIR1 = 8; // Right motor direction pin 1
const int MOTOR_RIGHT_DIR2 = 9; // Right motor direction pin 2

// Control Variables
bool lineFollowerEnabled = false;  // Can be controlled by ESP32
int baseSpeed = 150;               // Base motor speed (0-255)
int maxSpeed = 200;                // Maximum motor speed
int minSpeed = 100;                // Minimum motor speed

// Sensor readings
int sensorValues[NUM_SENSORS];
int lastPosition = 0;              // Last known position of line

// Communication with ESP32
String espCommand = "";
unsigned long lastStatusSent = 0;
const unsigned long statusInterval = 5000; // Send status every 5 seconds

void setup() {
  Serial.begin(9600);  // Communication with ESP32
  Serial.println("=== NaviCamp Arduino Mega Line Follower ===");
  
  // Initialize sensor pins
  for (int i = 0; i < NUM_SENSORS; i++) {
    pinMode(IR_PINS[i], INPUT);
  }
  
  // Initialize motor pins
  pinMode(MOTOR_LEFT_PWM, OUTPUT);
  pinMode(MOTOR_LEFT_DIR1, OUTPUT);
  pinMode(MOTOR_LEFT_DIR2, OUTPUT);
  pinMode(MOTOR_RIGHT_PWM, OUTPUT);
  pinMode(MOTOR_RIGHT_DIR1, OUTPUT);
  pinMode(MOTOR_RIGHT_DIR2, OUTPUT);
  
  // Stop motors initially
  stopMotors();
  
  Serial.println("Line follower initialized - Waiting for enable command");
  Serial.println("Commands: 'enable_line_follower', 'disable_line_follower', 'status'");
}

void loop() {
  // Handle ESP32 communication
  handleESP32Communication();
  
  // Run line follower if enabled
  if (lineFollowerEnabled) {
    readSensors();
    followLine();
  } else {
    stopMotors();
  }
  
  // Send periodic status to ESP32
  sendStatusToESP32();
  
  delay(50); // Small delay for stability
}

void handleESP32Communication() {
  if (Serial.available()) {
    espCommand = Serial.readString();
    espCommand.trim();
    
    if (espCommand == "enable_line_follower") {
      lineFollowerEnabled = true;
      Serial.println("line_follower_enabled");
    }
    else if (espCommand == "disable_line_follower") {
      lineFollowerEnabled = false;
      stopMotors();
      Serial.println("line_follower_disabled");
    }
    else if (espCommand == "status") {
      sendStatusToESP32();
    }
    else if (espCommand.startsWith("set_speed_")) {
      int newSpeed = espCommand.substring(10).toInt();
      if (newSpeed >= 50 && newSpeed <= 255) {
        baseSpeed = newSpeed;
        Serial.println("speed_set:" + String(baseSpeed));
      }
    }
  }
}

void sendStatusToESP32() {
  unsigned long currentTime = millis();
  if (currentTime - lastStatusSent >= statusInterval) {
    String status = "line_follower_status:";
    status += lineFollowerEnabled ? "enabled" : "disabled";
    status += ",speed:" + String(baseSpeed);
    status += ",position:" + String(lastPosition);
    
    Serial.println(status);
    lastStatusSent = currentTime;
  }
}

void readSensors() {
  for (int i = 0; i < NUM_SENSORS; i++) {
    // Read analog values and convert to digital (adjust threshold as needed)
    int rawValue = analogRead(IR_PINS[i]);
    sensorValues[i] = (rawValue > 512) ? 1 : 0;  // Threshold for line detection
  }
}

void followLine() {
  // Calculate position based on sensor readings
  int position = calculatePosition();
  
  if (position != -1) {
    lastPosition = position;
  }
  
  // Calculate motor speeds based on position
  int error = position - 2000; // Center position is 2000
  
  // PID-like control (simplified)
  int correction = error / 4; // Adjust this divisor for sensitivity
  
  int leftSpeed = baseSpeed + correction;
  int rightSpeed = baseSpeed - correction;
  
  // Constrain speeds
  leftSpeed = constrain(leftSpeed, minSpeed, maxSpeed);
  rightSpeed = constrain(rightSpeed, minSpeed, maxSpeed);
  
  // Apply motor speeds
  setMotorSpeeds(leftSpeed, rightSpeed);
}

int calculatePosition() {
  int weightedSum = 0;
  int totalActive = 0;
  
  for (int i = 0; i < NUM_SENSORS; i++) {
    if (sensorValues[i] == 1) { // Line detected
      weightedSum += (i * 1000); // Position values: 0, 1000, 2000, 3000, 4000
      totalActive++;
    }
  }
  
  if (totalActive == 0) {
    return -1; // No line detected
  }
  
  return weightedSum / totalActive;
}

void setMotorSpeeds(int leftSpeed, int rightSpeed) {
  // Left motor
  if (leftSpeed >= 0) {
    digitalWrite(MOTOR_LEFT_DIR1, HIGH);
    digitalWrite(MOTOR_LEFT_DIR2, LOW);
    analogWrite(MOTOR_LEFT_PWM, abs(leftSpeed));
  } else {
    digitalWrite(MOTOR_LEFT_DIR1, LOW);
    digitalWrite(MOTOR_LEFT_DIR2, HIGH);
    analogWrite(MOTOR_LEFT_PWM, abs(leftSpeed));
  }
  
  // Right motor
  if (rightSpeed >= 0) {
    digitalWrite(MOTOR_RIGHT_DIR1, HIGH);
    digitalWrite(MOTOR_RIGHT_DIR2, LOW);
    analogWrite(MOTOR_RIGHT_PWM, abs(rightSpeed));
  } else {
    digitalWrite(MOTOR_RIGHT_DIR1, LOW);
    digitalWrite(MOTOR_RIGHT_DIR2, HIGH);
    analogWrite(MOTOR_RIGHT_PWM, abs(rightSpeed));
  }
}

void stopMotors() {
  analogWrite(MOTOR_LEFT_PWM, 0);
  analogWrite(MOTOR_RIGHT_PWM, 0);
  digitalWrite(MOTOR_LEFT_DIR1, LOW);
  digitalWrite(MOTOR_LEFT_DIR2, LOW);
  digitalWrite(MOTOR_RIGHT_DIR1, LOW);
  digitalWrite(MOTOR_RIGHT_DIR2, LOW);
} 