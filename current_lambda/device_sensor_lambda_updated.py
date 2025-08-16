import json
import os
import pymysql
import boto3
import uuid
from datetime import datetime, timezone, timedelta
import traceback

# Environment variables
RDS_HOST = os.environ.get('RDS_HOST')
DB_NAME = os.environ.get('DB_NAME')
DB_USERNAME = os.environ.get('DB_USERNAME', 'admin')  # Default username
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

def create_fall_assistance_request(cursor, device_id, user_id, latitude, longitude, floor_level):
    """Create assistance request for wheelchair fall detection."""
    try:
        # Generate unique IDs
        location_id = str(uuid.uuid4()).replace("-", "").upper()[:8]
        alert_id = str(uuid.uuid4()).replace("-", "").upper()[:8]
        
        # Get current datetime in Philippine time (UTC+8)
        manila_tz = timezone(timedelta(hours=8))
        current_datetime = datetime.now(manila_tz).strftime("%Y-%m-%d %H:%M:%S")
        
        # Get user's full name
        user_query = "SELECT fullName FROM user_table WHERE userID = %s"
        cursor.execute(user_query, (user_id,))
        user_result = cursor.fetchone()
        full_name = user_result[0] if user_result else f"User {user_id}"
        
        # Insert into location_table
        location_query = """
        INSERT INTO location_table (locationID, userID, deviceID, fullName, latitude, longitude, floorLevel, dateTime)
        VALUES (%s, %s, %s, %s, %s, %s, %s, %s)
        """
        cursor.execute(location_query, (
            location_id, user_id, device_id, full_name, latitude, longitude, floor_level, current_datetime
        ))
        
        # Insert into incident_logs_table with fall-specific description
        incident_query = """
        INSERT INTO incident_logs_table (alertID, userID, deviceID, locationID, status, alertDateTime, alertDescription)
        VALUES (%s, %s, %s, %s, %s, %s, %s)
        """
        cursor.execute(incident_query, (
            alert_id, user_id, device_id, location_id, "pending", current_datetime,
            f"Wheelchair fall detected for {full_name}. Device sensors detected wheelchair has fallen or tipped over."
        ))
        
        print(f"INFO: Fall assistance request created - LocationID: {location_id}, AlertID: {alert_id}")
        
        # Send FCM notification to security officers
        send_fall_notification(user_id, full_name, floor_level, location_id)
        
        return location_id, alert_id
        
    except Exception as e:
        print(f"ERROR: Failed to create fall assistance request: {e}")
        raise e

def send_fall_notification(user_id, full_name, floor_level, location_id):
    """Send FCM notification about wheelchair fall to security officers."""
    try:
        import boto3
        
        # Prepare notification payload
        notification_data = {
            "notificationType": "wheelchair_fall",
            "title": "🚨 Wheelchair Fall Detected",
            "body": f"Wheelchair fall detected for {full_name} at {floor_level}. Immediate assistance required.",
            "userID": str(user_id),
            "fullName": full_name,
            "floorLevel": floor_level,
            "locationID": location_id,
            "timestamp": datetime.now(timezone(timedelta(hours=8))).isoformat()
        }
        
        # Invoke FCM notification lambda
        lambda_client = boto3.client('lambda', region_name='ap-southeast-1')
        response = lambda_client.invoke(
            FunctionName='NaviCampFCMNotification',
            InvocationType='Event',  # Async call
            Payload=json.dumps(notification_data)
        )
        
        print(f"INFO: Fall notification sent to FCM lambda - Response: {response['StatusCode']}")
        
    except Exception as e:
        print(f"ERROR: Failed to send fall notification: {e}")
        # Don't raise - notification failure shouldn't break the main flow

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
                SELECT deviceID, userID, status, floorLevel, connectedUntil
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
                        'isConnected': bool(result[1])  # True if userID exists
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
                    INSERT INTO devices_table (deviceID, userID, status, latitude, longitude, floorLevel, rssi, distance)
                    VALUES (%s, NULL, 'idle', 14.24379863, 121.11138234, 'Unknown', -100, 999.0)
                    """
                    cursor.execute(insert_query, (device_id,))
                    
                    device_config = {
                        'deviceID': device_id,
                        'userID': "",
                        'status': "idle",
                        'floorLevel': "Unknown",
                        'connectedUntil': None,
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
    """Handle /sensor endpoint - Receives and stores sensor data."""
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
        fall_status = body.get('fallStatus', 'Stable')  # Default to "Stable"
        
        print(f"INFO: Updating sensor data for device {device_id}")
        print(f"INFO: Location: {latitude}, {longitude}, Floor: {floor_level}")
        print(f"INFO: Fall Status: {fall_status}")
        
        # Update device data in database
        connection = get_db_connection()
        try:
            with connection.cursor() as cursor:
                # Check if device exists and get current state
                check_query = """
                SELECT deviceID, userID, fallStatus FROM devices_table WHERE deviceID = %s
                """
                cursor.execute(check_query, (device_id,))
                device_info = cursor.fetchone()
                
                previous_fall_status = None
                current_user_id = None
                
                if device_info:
                    current_user_id = device_info[1]
                    previous_fall_status = device_info[2] if len(device_info) > 2 else None
                    
                    # Update existing device - ONLY sensor data, do NOT touch userID/status/connectedUntil
                    update_query = """
                    UPDATE devices_table 
                    SET latitude = %s, longitude = %s, floorLevel = %s, rssi = %s, distance = %s, fallStatus = %s
                    WHERE deviceID = %s
                    """
                    
                    cursor.execute(update_query, (
                        latitude,
                        longitude,
                        floor_level,
                        rssi,
                        distance,
                        fall_status,
                        device_id
                    ))
                    print(f"INFO: Updated sensor data for device {device_id}")
                    
                else:
                    # Insert new device with default connection state (available, no user)
                    insert_query = """
                    INSERT INTO devices_table 
                    (deviceID, userID, status, latitude, longitude, floorLevel, rssi, distance, fallStatus, connectedUntil)
                    VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s)
                    """
                    
                    cursor.execute(insert_query, (
                        device_id,
                        None,  # No user connected initially
                        'available',  # Default status
                        latitude,
                        longitude,
                        floor_level,
                        rssi,
                        distance,
                        fall_status,
                        None  # No connection timeout initially
                    ))
                    print(f"INFO: Inserted new device {device_id} with sensor data")
                
                # **NEW: Fall Detection Logic**
                # Only trigger assistance if:
                # 1. Fall status changed from "Stable" to "Fell"
                # 2. Device has a connected user (userID is not None/empty)
                if (fall_status == "Fell" and 
                    previous_fall_status != "Fell" and 
                    current_user_id is not None and 
                    str(current_user_id).strip() != ""):
                    
                    print(f"INFO: Fall detected for device {device_id} with user {current_user_id}")
                    create_fall_assistance_request(cursor, device_id, current_user_id, latitude, longitude, floor_level)
                elif fall_status == "Fell" and (current_user_id is None or str(current_user_id).strip() == ""):
                    print(f"INFO: Fall detected for device {device_id} but no user connected - no assistance created")
                
                # Return success response with Philippines time
                manila_tz = timezone(timedelta(hours=8))
                response_data = {
                    'success': True,
                    'message': 'Sensor data updated successfully',
                    'deviceID': device_id,
                    'fallDetected': fall_status == "Fell" and current_user_id is not None,
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
                        'available',  # Default status
                        latitude,
                        longitude,
                        floor_level,
                        rssi,
                        distance,
                        None  # No connection timeout initially
                    ))
                    print(f"INFO: Inserted new device {device_id} with sensor data")
                
                # Return success response
                # Return success response with Philippines time
                manila_tz = timezone(timedelta(hours=8))
                response_data = {
                    'success': True,
                    'message': 'Sensor data updated successfully',
                    'deviceID': device_id,
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