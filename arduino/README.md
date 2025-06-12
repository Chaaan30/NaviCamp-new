# NaviCamp Wheelchair Arduino & AWS Setup

## Arduino Files Overview

### 1. `main_wheelchair_esp32.ino`
- **Purpose**: Main ESP32 on the wheelchair that scans for BLE beacons
- **Features**:
  - Scans for floor beacons (First Floor, Second Floor, etc.)
  - Calculates distance using RSSI
  - Sends location data to AWS database
  - Communicates with Arduino Mega for line follower control
  - Checks user connection status

### 2. `floor_beacon_esp32.ino`
- **Purpose**: ESP32 beacons placed on each floor
- **Setup**: 
  - Upload this code to 5 different ESP32s
  - **IMPORTANT**: Change the `FLOOR_NAME` constant before uploading:
    - ESP32 #1: `"First Floor"`
    - ESP32 #2: `"Second Floor"`
    - ESP32 #3: `"Third Floor"`
    - ESP32 #4: `"Fourth Floor"` (if needed)
    - ESP32 #5: `"Fifth Floor"` (if needed)

### 3. `arduino_mega_line_follower.ino`
- **Purpose**: Arduino Mega with 5-channel IR line tracker
- **Features**:
  - Line following algorithm with PID control
  - Serial communication with ESP32
  - Can be enabled/disabled remotely
  - Motor control for wheelchair movement

## Hardware Connections

### Main ESP32 (Wheelchair)
- **WiFi**: Connects to your network
- **BLE**: Scans for floor beacons
- **Serial2**: Communication with Arduino Mega (TX=17, RX=16)

### Arduino Mega (Line Follower)
- **IR Sensors**: A0-A4 (5-channel line tracker)
- **Motor Driver**: Pins 5-10 (L298N compatible)
- **Serial**: Communication with ESP32

### Floor Beacon ESP32s
- **Power**: USB or battery powered
- **BLE**: Broadcasts floor name

## AWS Lambda Deployment

### New Lambda Function: `navicamp-device-sensor`

1. **Create Lambda Function**:
   ```bash
   aws lambda create-function \
     --function-name navicamp-device-sensor \
     --runtime python3.9 \
     --role arn:aws:iam::YOUR_ACCOUNT:role/lambda-execution-role \
     --handler lambda_function.lambda_handler \
     --zip-file fileb://device_sensor_lambda.zip
   ```

2. **Environment Variables** (same as your existing Lambda):
   ```
   DB_SECRET_NAME=your-secret-name
   DB_HOST=your-rds-endpoint
   DB_NAME=campusnavigator
   AWS_REGION=ap-southeast-1
   ```

### API Gateway Endpoints

Create a **new API Gateway** called `navicamp-device-api`:

1. **POST /device-config**
   - **Purpose**: ESP32 gets device configuration
   - **Request**: `{"deviceID": "202501"}`
   - **Response**: `{"deviceID": "202501", "userID": "123", "status": "active", "floorLevel": "First Floor", "isConnected": true}`

2. **POST /sensor**
   - **Purpose**: ESP32 sends sensor data
   - **Request**: 
     ```json
     {
       "deviceID": "202501",
       "userID": "123",
       "status": "active",
       "latitude": 14.24379863,
       "longitude": 121.11138234,
       "floorLevel": "First Floor",
       "rssi": -65,
       "distance": 1.5
     }
     ```

### Expected API URLs
After deployment, your ESP32 should connect to:
- Config: `https://NEW_API_ID.execute-api.ap-southeast-1.amazonaws.com/prod/device-config`
- Sensor: `https://NEW_API_ID.execute-api.ap-southeast-1.amazonaws.com/prod/sensor`

## Database Schema
Your existing `devices_table` is perfect - no changes needed:
- `deviceID` - Wheelchair identifier ("202501")
- `userID` - Connected user (filled when QR code is scanned)
- `status` - "active", "idle", "available"
- `latitude`, `longitude` - Current position
- `floorLevel` - Current floor from BLE beacon
- `rssi`, `distance` - Signal strength and distance to beacon
- `connectedUntil` - Connection timeout

## Setup Steps

1. **Deploy AWS Lambda** using `device_sensor_lambda.py`
2. **Create API Gateway** with the two endpoints
3. **Update ESP32 URLs** in `main_wheelchair_esp32.ino`
4. **Upload code to Arduino boards**:
   - Main ESP32 (wheelchair)
   - 5 Floor beacon ESP32s (change FLOOR_NAME for each)
   - Arduino Mega (line follower)

## Testing

1. **Floor Beacons**: Power on and check serial monitor for "Broadcasting: [Floor Name]"
2. **Main ESP32**: Check serial monitor for beacon detection and API calls
3. **Database**: Verify data is being inserted/updated in `devices_table`
4. **Line Follower**: Send commands via ESP32 to enable/disable

## Cost Estimates
- **Lambda**: ~$0.20/month (minimal usage)
- **API Gateway**: ~$1-3/month
- **No additional RDS costs** (using existing database) 