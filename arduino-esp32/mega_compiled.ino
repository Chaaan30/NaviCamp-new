#include <Wire.h>
#include <MPU6050.h>

MPU6050 mpu;

// New: Line Follower Control Flag
bool lineFollowerEnabled = false;

// IR Sensor Pins
const int farLeftIR = 25;
const int leftIR   = 22;
const int centerIR = 23;
const int rightIR  = 24;
const int farRightIR = 26;

// Motor Pins (PWM capable)
const int R_PWM_right = 5;
const int L_PWM_right = 6;
const int R_PWM_left  = 11;
const int L_PWM_left  = 12;

// Ultrasonic Sensor Pins
const int trigPin = 9;
const int echoPin = 10;
const int trigPin2 = 3;
const int echoPin2 = 4;

void setup() {
  Serial.begin(115200);

  Wire.begin();
  mpu.initialize();

  pinMode(leftIR, INPUT);
  pinMode(centerIR, INPUT);
  pinMode(rightIR, INPUT);
  pinMode(farLeftIR, INPUT);
  pinMode(farRightIR, INPUT);

  pinMode(R_PWM_right, OUTPUT);
  pinMode(L_PWM_right, OUTPUT);
  pinMode(R_PWM_left, OUTPUT);
  pinMode(L_PWM_left, OUTPUT);

  pinMode(trigPin, OUTPUT);
  pinMode(echoPin, INPUT);

  pinMode(trigPin2, OUTPUT);
  pinMode(echoPin2, INPUT);

  // New: Initialize Serial1 for communication with ESP32 Scanner/Broadcaster
  Serial1.begin(115200); // Using Pins 19 (RX1) and 18 (TX1)
  Serial.println("Arduino Mega Ready for Line Follower Commands.");
}

unsigned long prevMPU = 0;
unsigned long prevUltrasonic = 0;

void loop() {
  // New: Check for incoming commands from ESP32 Scanner/Broadcaster
  if (Serial1.available()) {
    String command = Serial1.readStringUntil('\n');
    command.trim();
    Serial.print("Received command from ESP32: ");
    Serial.println(command);

    if (command == "LF_ON") {
      lineFollowerEnabled = true;
      Serial.println("Line Follower ENABLED.");
    } else if (command == "LF_OFF") {
      lineFollowerEnabled = false;
      stopMoving(); // Immediately stop motors when line follower is disabled
      Serial.println("Line Follower DISABLED.");
    }
  }

  // Only execute line follower logic if it's enabled
  if (lineFollowerEnabled) {
    readLineSensors();
  } else {
    // Ensure motors are stopped if line follower is disabled
    stopMoving();
  }

  unsigned long now = millis();

  if (now - prevUltrasonic > 200) {
    readUltrasonicSensor();
    readUltrasonicSensor2();
    prevUltrasonic = now;
  }

  if (now - prevMPU > 2000) {
    readMPU6050();
    prevMPU = now;
  }
}

String lastMove = "stop";
unsigned long lostLineStart = 0;
bool lineLost = false;
const unsigned long recoveryDuration = 2000; // milliseconds

// === IR Sensor Logic ===
void readLineSensors() {
  int farLeft = digitalRead(farLeftIR);
  int left   = digitalRead(leftIR);
  int center = digitalRead(centerIR);
  int right  = digitalRead(rightIR);
  int farRight = digitalRead(farRightIR);

  if (center == HIGH && left == LOW && right == LOW && farLeft == LOW && farRight == LOW) {
    Serial.println("Moving Forward");
    forward();
    lastMove = "forward";
    lineLost = false;
  } else if (farLeft == HIGH) {
    Serial.println("Moving Far Left");
    turnLeft();
    lastMove = "left";
    lineLost = false;
  } else if (farRight == HIGH) {
    Serial.println("Moving Far Right");
    turnRight();
    lastMove = "right";
    lineLost = false;
  } else if (left == HIGH && center == LOW && right == LOW && farLeft == LOW) {
    Serial.println("Moving Slight Left");
    slightLeft();
    lastMove = "slightLeft";
    lineLost = false;
  } else if (left == LOW && center == LOW && right == HIGH && farRight == LOW) {
    Serial.println("Moving Slight Right");
    slightRight();
    lastMove = "slightRight";
    lineLost = false;
  } else {
    if (!lineLost) {
      lineLost = true;
      lostLineStart = millis();
    }

    if (millis() - lostLineStart < recoveryDuration) {
      resumeLastMove();
    } else {
      if (lastMove != "braked") {
        brake();
        lastMove = "braked";
      }
    }
  }
}
//SPEED FOR TESTING:
// int baseSpeed = 20;
// int curveSpeed = 15;
// int slightSpeed = 10;

int baseSpeed = 120;
int curveSpeed = 75;
int slightSpeed = 40;

// === Motor Movement ===
void forward() {
  analogWrite(R_PWM_right, curveSpeed);
  analogWrite(L_PWM_right, 0);
  analogWrite(R_PWM_left, 0);
  analogWrite(L_PWM_left, curveSpeed);
}

void turnLeft() {
  analogWrite(R_PWM_right, baseSpeed);
  analogWrite(L_PWM_right, 0);
  analogWrite(R_PWM_left, slightSpeed);
  analogWrite(L_PWM_left, 0); // slower for turning
}

void turnRight() {
  analogWrite(R_PWM_right, 0);
  analogWrite(L_PWM_right, slightSpeed);
  analogWrite(R_PWM_left, 0);
  analogWrite(L_PWM_left, baseSpeed);
}

void slightLeft() {
  analogWrite(R_PWM_right, curveSpeed);
  analogWrite(L_PWM_right, 0);
  analogWrite(R_PWM_left, 0);
  analogWrite(L_PWM_left, slightSpeed);
}

void slightRight() {
  analogWrite(R_PWM_right, slightSpeed);
  analogWrite(L_PWM_right, 0);
  analogWrite(R_PWM_left, 0);
  analogWrite(L_PWM_left, curveSpeed);
}

void stopMoving() {
  analogWrite(R_PWM_right, 0);
  analogWrite(L_PWM_right, 0);
  analogWrite(R_PWM_left, 0);
  analogWrite(L_PWM_left, 0);
}

void brake() {
  analogWrite(R_PWM_right, 0);
  analogWrite(L_PWM_right, slightSpeed);
  analogWrite(R_PWM_left, slightSpeed);
  analogWrite(L_PWM_left, 0);
  
  delay(1000);
  stopMoving();
}

void resumeLastMove() {
  if (lastMove == "forward") forward();
  else if (lastMove == "left") turnLeft();
  else if (lastMove == "right") turnRight();
  else if (lastMove == "slightLeft") slightLeft();
  else if (lastMove == "slightRight") slightRight();
}

// === Ultrasonic Sensors ===

int dangerDistance = 30;

void readUltrasonicSensor() {
  digitalWrite(trigPin, LOW);
  delayMicroseconds(2);
  digitalWrite(trigPin, HIGH);
  delayMicroseconds(10);
  digitalWrite(trigPin, LOW);

  long duration = pulseIn(echoPin, HIGH);
  float distance = duration * 0.034 / 2;

  Serial.print("Distance: ");
  Serial.print(distance);
  Serial.println(" cm");

  if (distance < dangerDistance) {
    Serial.print("  OBSTACLE DETECTED (RIGHT)");
    brake();
  } else {
  }
}

void readUltrasonicSensor2() {
  digitalWrite(trigPin2, LOW);
  delayMicroseconds(2);
  digitalWrite(trigPin2, HIGH);
  delayMicroseconds(10);
  digitalWrite(trigPin2, LOW);

  long duration = pulseIn(echoPin2, HIGH);
  float distance = duration * 0.034 / 2;

  Serial.print("Distance2: ");
  Serial.print(distance);
  Serial.println(" cm");

  if (distance < dangerDistance) {
    Serial.println("  OBSTACLE DETECTED (LEFT)");
    brake();
  }
}

// === MPU6050 Tilt ===
void readMPU6050() {
  int16_t ax, ay, az, gx, gy, gz;
  mpu.getMotion6(&ax, &ay, &az, &gx, &gy, &gz);

  int X = constrain(map(ax, -17000, 17000, 0, 255), 0, 255); // X axis data
  int Y = constrain(map(ay, -17000, 17000, 0, 255), 0, 255); 
  int Z = constrain(map(az, -17000, 17000, 0, 255), 0, 255); // Y axis data

  Serial.print("Accel X: "); Serial.print(X);
  Serial.print(" | Y: "); Serial.print(Y);
  Serial.print(" | Z: "); Serial.println(Z);

  static String lastStatus = "";

  String currentStatus = (Y <= 30 || Y >= 220) ? "FALL DETECTED" : "Stable";
  if (currentStatus != lastStatus) {
    Serial.println(currentStatus);
    lastStatus = currentStatus;

    if (currentStatus == "FALL DETECTED") {
      stopMoving();
    }
  }
}