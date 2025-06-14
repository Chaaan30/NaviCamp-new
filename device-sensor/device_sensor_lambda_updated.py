import json
import os
import pymysql
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
        required_fields = ['deviceID', 'latitude', 'longitude', 'floorLevel', 'rssi', 'distance']
        missing_fields = [field for field in required_fields if field not in body]
        
        if missing_fields:
            return {
                'statusCode': 400,
                'headers': {'Content-Type': 'application/json'},
                'body': json.dumps({'error': f'Missing required fields: {missing_fields}'})
            }
        
        device_id = body['deviceID']
        user_id = body.get('userID', None)  # Can be empty/null
        
        # Fix status logic - use proper status values
        if user_id:
            status = 'in_use'  # User is connected
        else:
            status = 'available'  # No user connected
            
        latitude = float(body['latitude'])
        longitude = float(body['longitude'])
        floor_level = body['floorLevel']
        rssi = int(body['rssi'])
        distance = float(body['distance'])
        
        print(f"INFO: Updating sensor data for device {device_id}")
        print(f"INFO: Location: {latitude}, {longitude}, Floor: {floor_level}")
        
        # Update device data in database
        connection = get_db_connection()
        try:
            with connection.cursor() as cursor:
                # Check if device exists
                check_query = "SELECT deviceID FROM devices_table WHERE deviceID = %s"
                cursor.execute(check_query, (device_id,))
                exists = cursor.fetchone()
                
                if exists:
                    # Update existing device
                    update_query = """
                    UPDATE devices_table 
                    SET userID = %s, status = %s, latitude = %s, longitude = %s, 
                        floorLevel = %s, rssi = %s, distance = %s, connectedUntil = %s
                    WHERE deviceID = %s
                    """
                    
                    # Set connectedUntil to 5 minutes from now if user is connected (Philippines time)
                    connected_until = None
                    if user_id:
                        manila_tz = timezone(timedelta(hours=8))
                        connected_until = datetime.now(manila_tz) + timedelta(minutes=5)
                    
                    cursor.execute(update_query, (
                        user_id if user_id else None,
                        status,
                        latitude,
                        longitude,
                        floor_level,
                        rssi,
                        distance,
                        connected_until,
                        device_id
                    ))
                    print(f"INFO: Updated device {device_id}")
                    
                else:
                    # Insert new device
                    insert_query = """
                    INSERT INTO devices_table 
                    (deviceID, userID, status, latitude, longitude, floorLevel, rssi, distance, connectedUntil)
                    VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s)
                    """
                    
                    connected_until = None
                    if user_id:
                        manila_tz = timezone(timedelta(hours=8))
                        connected_until = datetime.now(manila_tz) + timedelta(minutes=5)
                    
                    cursor.execute(insert_query, (
                        device_id,
                        user_id if user_id else None,
                        status,
                        latitude,
                        longitude,
                        floor_level,
                        rssi,
                        distance,
                        connected_until
                    ))
                    print(f"INFO: Inserted new device {device_id}")
                
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