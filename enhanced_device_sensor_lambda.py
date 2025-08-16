import json
import os
import pymysql
from datetime import datetime, timezone, timedelta
import traceback
import uuid
import urllib.request
import urllib.parse

# Environment variables
RDS_HOST = os.environ.get('RDS_HOST')
DB_NAME = os.environ.get('DB_NAME')
DB_USERNAME = os.environ.get('DB_USERNAME', 'admin')
DB_PASSWORD = os.environ.get('DB_PASSWORD')

def get_db_connection():
    """Establishes database connection using environment variables."""
    try:
        connection = pymysql.connect(
            host=RDS_HOST,
            user=DB_USERNAME,
            password=DB_PASSWORD,
            database=DB_NAME,
            connect_timeout=30,
            read_timeout=30,
            write_timeout=30,
            autocommit=True
        )
        
        print("INFO: Database connection established")
        return connection
        
    except Exception as e:
        print(f"ERROR: Database connection failed: {e}")
        raise e

def generate_alert_id():
    """Generate random 8-character alphanumeric alert ID"""
    import random
    import string
    chars = string.ascii_uppercase + string.digits
    return ''.join(random.choices(chars, k=8))

def get_user_full_name(connection, user_id):
    """Get user's full name from database"""
    try:
        with connection.cursor() as cursor:
            query = "SELECT fullName FROM user_table WHERE userID = %s"
            cursor.execute(query, (user_id,))
            result = cursor.fetchone()
            return result[0] if result else "Unknown User"
    except Exception as e:
        print(f"ERROR: Failed to get user full name for {user_id}: {e}")
        return "Unknown User"

def send_fall_notification(location_id, user_id, device_id, floor_level):
    """Send FCM notification about wheelchair fall to Security Officers"""
    try:
        # Prepare notification payload
        payload = {
            "notificationType": "assistance_request",
            "locationID": location_id,
            "userID": user_id,
            "deviceID": device_id,
            "floorLevel": floor_level,
            "assistanceType": "wheelchair_fall"  # Special type for fall detection
        }
        
        # Call FCM notification lambda
        fcm_url = "https://cl67pknqo8.execute-api.ap-southeast-1.amazonaws.com/prod/fcm-notification"
        
        headers = {'Content-Type': 'application/json'}
        data = json.dumps(payload).encode('utf-8')
        
        request = urllib.request.Request(fcm_url, data=data, headers=headers)
        with urllib.request.urlopen(request) as response:
            if response.status == 200:
                print(f"INFO: Fall notification sent successfully for device {device_id}")
            else:
                print(f"WARNING: Fall notification failed with status {response.status}")
                
    except Exception as e:
        print(f"ERROR: Failed to send fall notification: {e}")

def create_fall_assistance_request(connection, device_id, user_id, latitude, longitude, floor_level):
    """Create assistance request for wheelchair fall"""
    try:
        # Generate IDs
        location_id = str(uuid.uuid4()).replace("-", "").upper()[:8]
        alert_id = generate_alert_id()
        
        # Get current datetime in Philippines timezone (UTC+8)
        current_datetime = datetime.now(timezone(timedelta(hours=8))).strftime("%Y-%m-%d %H:%M:%S")
        
        # Get user's full name
        full_name = get_user_full_name(connection, user_id)
        
        # Insert into location_table
        location_query = """
            INSERT INTO location_table (locationID, userID, deviceID, latitude, longitude, floorLevel, dateTime)
            VALUES (%s, %s, %s, %s, %s, %s, %s)
        """
        
        with connection.cursor() as cursor:
            cursor.execute(location_query, (
                location_id, user_id, device_id, latitude, longitude, floor_level, current_datetime
            ))
            print(f"INFO: Created location record {location_id} for fall detection")
        
        # Insert into incident_logs_table
        incident_query = """
            INSERT INTO incident_logs_table (alertID, userID, deviceID, locationID, status, alertDateTime, alertDescription)
            VALUES (%s, %s, %s, %s, %s, %s, %s)
        """
        
        with connection.cursor() as cursor:
            cursor.execute(incident_query, (
                alert_id, user_id, device_id, location_id, "pending", current_datetime,
                f"WHEELCHAIR FALL DETECTED - Device {device_id} detected a fall. User may need immediate assistance."
            ))
            print(f"INFO: Created incident record {alert_id} for wheelchair fall")
        
        # Send notification to Security Officers
        send_fall_notification(location_id, user_id, device_id, floor_level)
        
        return True, location_id, alert_id
        
    except Exception as e:
        print(f"ERROR: Failed to create fall assistance request: {e}")
        print(f"ERROR: Traceback: {traceback.format_exc()}")
        return False, None, None

def handle_device_config(event):
    """Handle /device-config endpoint - Returns device configuration."""
    try:
        # Parse request body
        if 'body' in event:
            if isinstance(event['body'], str):
                body = json.loads(event['body'])
            else:
                body = event['body']
        else:
            return {
                'statusCode': 400,
                'headers': {'Content-Type': 'application/json'},
                'body': json.dumps({'error': 'Missing request body'})
            }
        
        device_id = body.get('deviceID')
        if not device_id:
            return {
                'statusCode': 400,
                'headers': {'Content-Type': 'application/json'},
                'body': json.dumps({'error': 'deviceID is required'})
            }
        
        print(f"INFO: Getting config for device {device_id}")
        
        # Get device info from database
        connection = get_db_connection()
        try:
            with connection.cursor() as cursor:
                query = """
                SELECT deviceID, userID, status, floorLevel, connectedUntil, fallStatus
                FROM devices_table 
                WHERE deviceID = %s
                """
                cursor.execute(query, (device_id,))
                result = cursor.fetchone()
                
                if result:
                    device_config = {
                        'deviceID': result[0],
                        'userID': str(result[1]) if result[1] else "",
                        'status': result[2] or "idle",
                        'floorLevel': result[3] or "",
                        'connectedUntil': result[4].isoformat() if result[4] else None,
                        'fallStatus': result[5] or "Stable",
                        'isConnected': bool(result[1])
                    }
                    
                    print(f"INFO: Device config found: {device_config}")
                    return {
                        'statusCode': 200,
                        'headers': {'Content-Type': 'application/json'},
                        'body': json.dumps(device_config)
                    }
                else:
                    # Device not found, create new entry
                    print(f"INFO: Device {device_id} not found, creating new entry")
                    insert_query = """
                    INSERT INTO devices_table (deviceID, userID, status, latitude, longitude, floorLevel, rssi, distance, fallStatus)
                    VALUES (%s, NULL, 'idle', 14.24379863, 121.11138234, 'Unknown', -100, 999.0, 'Stable')
                    """
                    cursor.execute(insert_query, (device_id,))
                    
                    device_config = {
                        'deviceID': device_id,
                        'userID': "",
                        'status': "idle",
                        'floorLevel': "Unknown",
                        'connectedUntil': None,
                        'fallStatus': "Stable",
                        'isConnected': False
                    }
                    
                    return {
                        'statusCode': 200,
                        'headers': {'Content-Type': 'application/json'},
                        'body': json.dumps(device_config)
                    }
                    
        finally:
            connection.close()
            
    except Exception as e:
        print(f"ERROR: Device config handler failed: {e}")
        print(f"ERROR: Traceback: {traceback.format_exc()}")
        return {
            'statusCode': 500,
            'headers': {'Content-Type': 'application/json'},
            'body': json.dumps({'error': 'Internal server error'})
        }

def handle_sensor_data(event):
    """Handle /sensor endpoint - Receives and stores sensor data with fall detection."""
    try:
        # Parse request body
        if 'body' in event:
            if isinstance(event['body'], str):
                body = json.loads(event['body'])
            else:
                body = event['body']
        else:
            return {
                'statusCode': 400,
                'headers': {'Content-Type': 'application/json'},
                'body': json.dumps({'error': 'Missing request body'})
            }
        
        # Validate required fields
        required_fields = ['deviceID', 'latitude', 'longitude', 'floorLevel', 'rssi', 'distance', 'fallStatus']
        missing_fields = [field for field in required_fields if field not in body]
        
        if missing_fields:
            return {
                'statusCode': 400,
                'headers': {'Content-Type': 'application/json'},
                'body': json.dumps({'error': f'Missing required fields: {missing_fields}'})
            }
        
        device_id = body['deviceID']
        latitude = float(body['latitude'])
        longitude = float(body['longitude'])
        floor_level = body['floorLevel']
        rssi = int(body['rssi'])
        distance = float(body['distance'])
        fall_status = body.get('fallStatus', 'Stable')
        
        print(f"INFO: Updating sensor data for device {device_id}")
        print(f"INFO: Location: {latitude}, {longitude}, Floor: {floor_level}, Fall Status: {fall_status}")
        
        # Update device data in database
        connection = get_db_connection()
        try:
            with connection.cursor() as cursor:
                # Get current device state to check for fall detection
                check_query = """
                    SELECT deviceID, userID, fallStatus 
                    FROM devices_table 
                    WHERE deviceID = %s
                """
                cursor.execute(check_query, (device_id,))
                current_device = cursor.fetchone()
                
                if current_device:
                    current_user_id = current_device[1]
                    previous_fall_status = current_device[2] or "Stable"
                    
                    # **FALL DETECTION LOGIC**
                    # Only create assistance if:
                    # 1. userID is not NULL/empty (wheelchair is occupied)
                    # 2. fallStatus changed from "Stable" to "Fell"
                    if (current_user_id and 
                        previous_fall_status == "Stable" and 
                        fall_status == "Fell"):
                        
                        print(f"🚨 FALL DETECTED: Device {device_id} user {current_user_id} - Creating assistance request")
                        
                        success, location_id, alert_id = create_fall_assistance_request(
                            connection, device_id, current_user_id, latitude, longitude, floor_level
                        )
                        
                        if success:
                            print(f"✅ Fall assistance created: LocationID={location_id}, AlertID={alert_id}")
                        else:
                            print(f"❌ Failed to create fall assistance request")
                    
                    elif not current_user_id and fall_status == "Fell":
                        print(f"ℹ️  Fall detected on unoccupied wheelchair {device_id} - No assistance created")
                    
                    elif fall_status == "Stable" and previous_fall_status == "Fell":
                        print(f"✅ Wheelchair {device_id} recovered to stable position")
                    
                    # Update existing device sensor data
                    update_query = """
                    UPDATE devices_table 
                    SET latitude = %s, longitude = %s, floorLevel = %s, rssi = %s, distance = %s, fallStatus = %s
                    WHERE deviceID = %s
                    """
                    
                    cursor.execute(update_query, (
                        latitude, longitude, floor_level, rssi, distance, fall_status, device_id
                    ))
                    print(f"INFO: Updated sensor data for device {device_id}")
                    
                else:
                    # Insert new device with default connection state
                    insert_query = """
                    INSERT INTO devices_table 
                    (deviceID, userID, status, latitude, longitude, floorLevel, rssi, distance, connectedUntil, fallStatus)
                    VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s)
                    """
                    
                    cursor.execute(insert_query, (
                        device_id, None, 'available', latitude, longitude, floor_level, 
                        rssi, distance, None, fall_status
                    ))
                    print(f"INFO: Inserted new device {device_id} with sensor data")
                
                # Return success response with Philippines time
                manila_tz = timezone(timedelta(hours=8))
                response_data = {
                    'success': True,
                    'message': 'Sensor data updated successfully',
                    'deviceID': device_id,
                    'fallStatus': fall_status,
                    'assistanceCreated': current_device and current_device[1] and 
                                       (current_device[2] or "Stable") == "Stable" and 
                                       fall_status == "Fell",
                    'timestamp': datetime.now(manila_tz).isoformat()
                }
                
                return {
                    'statusCode': 200,
                    'headers': {'Content-Type': 'application/json'},
                    'body': json.dumps(response_data)
                }
                
        finally:
            connection.close()
            
    except Exception as e:
        print(f"ERROR: Sensor data handler failed: {e}")
        print(f"ERROR: Traceback: {traceback.format_exc()}")
        return {
            'statusCode': 500,
            'headers': {'Content-Type': 'application/json'},
            'body': json.dumps({'error': 'Internal server error'})
        }

def lambda_handler(event, context):
    """Main Lambda handler for device and sensor endpoints."""
    print(f"INFO: Lambda execution started - Request ID: {context.aws_request_id}")
    print(f"INFO: Event: {json.dumps(event, default=str)}")
    
    try:
        # Check environment variables
        if not all([RDS_HOST, DB_NAME, DB_PASSWORD]):
            print("ERROR: Missing required environment variables")
            return {
                'statusCode': 500,
                'headers': {'Content-Type': 'application/json'},
                'body': json.dumps({'error': 'Server configuration error'})
            }
        
        # Get the HTTP method and path
        http_method = event.get('httpMethod', 'POST')
        resource_path = event.get('resource', '')
        
        print(f"INFO: Processing {http_method} request to {resource_path}")
        
        # Route to appropriate handler
        if resource_path == '/device-config':
            return handle_device_config(event)
        elif resource_path == '/sensor':
            return handle_sensor_data(event)
        else:
            return {
                'statusCode': 404,
                'headers': {'Content-Type': 'application/json'},
                'body': json.dumps({'error': 'Endpoint not found'})
            }
            
    except Exception as e:
        print(f"ERROR: Lambda handler failed: {e}")
        print(f"ERROR: Traceback: {traceback.format_exc()}")
        return {
            'statusCode': 500,
            'headers': {'Content-Type': 'application/json'},
            'body': json.dumps({'error': 'Internal server error'})
        }
