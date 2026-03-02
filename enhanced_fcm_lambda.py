import json
import logging
import os
import urllib.request
import urllib.parse
import urllib.error
import time
from datetime import datetime, timezone

# Configure logging
logger = logging.getLogger()
logger.setLevel(logging.INFO)

def get_db_connection():
    """Create database connection using direct credentials"""
    try:
        import pymysql
        connection = pymysql.connect(
            host=os.environ.get('DB_HOST', 'campusnavigator.c10aiyo64bnv.ap-southeast-1.rds.amazonaws.com'),
            user=os.environ.get('DB_USER', 'navicamp'),
            password=os.environ.get('DB_PASSWORD', 'navicamp'),
            database=os.environ.get('DB_NAME', 'campusnavigator'),
            charset='utf8mb4',
            cursorclass=pymysql.cursors.DictCursor
        )
        logger.info("Database connection established successfully")
        return connection
    except Exception as e:
        logger.error(f"Database connection failed: {str(e)}")
        raise e

def get_security_officer_tokens():
    """Get FCM tokens for verified responders (officers + admins)."""
    connection = None
    try:
        connection = get_db_connection()
        with connection.cursor() as cursor:
            query = """
                SELECT DISTINCT u.fcm_token
                FROM user_table u
                LEFT JOIN safety_officer_profiles_table s ON u.userID = s.userID
                WHERE u.verified = 1
                  AND u.fcm_token IS NOT NULL
                  AND u.fcm_token != ''
                  AND (
                        s.userID IS NOT NULL
                        OR LOWER(COALESCE(u.userType, '')) LIKE '%admin%'
                  )
            """
            cursor.execute(query)
            tokens = [row['fcm_token'] for row in cursor.fetchall()]
            logger.info(f"Found {len(tokens)} responder/admin FCM tokens")
            return tokens
    except Exception as e:
        logger.error(f"Error getting security officer tokens: {str(e)}")
        return []
    finally:
        if connection:
            connection.close()

def get_user_details(user_id):
    """Get user details for notification"""
    connection = None
    try:
        connection = get_db_connection()
        with connection.cursor() as cursor:
            query = "SELECT fullName, userType FROM user_table WHERE userID = %s"
            cursor.execute(query, (user_id,))
            return cursor.fetchone()
    except Exception as e:
        logger.error(f"Error getting user details for userID {user_id}: {str(e)}")
        return None
    finally:
        if connection:
            connection.close()

def get_assistance_details(location_id):
    """Get assistance request details"""
    connection = None
    try:
        connection = get_db_connection()
        with connection.cursor() as cursor:
            query = """
                SELECT l.locationID, l.userID, l.deviceID, u.fullName, l.floorLevel, 
                       i.status, l.latitude, l.longitude, l.dateTime, i.officerResponded, i.alertID 
                FROM location_table l
                JOIN user_table u ON l.userID = u.userID
                LEFT JOIN incident_logs_table i ON l.locationID = i.locationID
                WHERE l.locationID = %s ORDER BY i.alertDateTime DESC LIMIT 1
            """
            cursor.execute(query, (location_id,))
            result = cursor.fetchone()
            logger.info(f"Database result for locationID {location_id}: {result}")
            return result
    except Exception as e:
        logger.error(f"Error getting assistance details for locationID {location_id}: {str(e)}")
        return None
    finally:
        if connection:
            connection.close()

def get_access_token():
    """Generate OAuth access token using Firebase service account key"""
    try:
        service_account_json = os.environ.get('FIREBASE_SERVICE_ACCOUNT_KEY')
        if not service_account_json:
            logger.error("Firebase service account key not found in environment")
            return None
        service_account = json.loads(service_account_json)
        from jose import jwt
        now = int(time.time())
        payload = {
            'iss': service_account['client_email'], 'sub': service_account['client_email'],
            'aud': 'https://oauth2.googleapis.com/token', 'iat': now, 'exp': now + 3600,
            'scope': 'https://www.googleapis.com/auth/firebase.messaging'
        }
        private_key = service_account['private_key']
        jwt_token = jwt.encode(payload, private_key, algorithm='RS256')
        token_url = 'https://oauth2.googleapis.com/token'
        token_data = {'grant_type': 'urn:ietf:params:oauth:grant-type:jwt-bearer', 'assertion': jwt_token}
        token_request = urllib.request.Request(
            token_url, data=urllib.parse.urlencode(token_data).encode('utf-8'),
            headers={'Content-Type': 'application/x-www-form-urlencoded'}
        )
        with urllib.request.urlopen(token_request) as response:
            token_response = json.loads(response.read().decode('utf-8'))
            access_token = token_response.get('access_token')
            if access_token:
                logger.info("Successfully obtained OAuth access token")
                return access_token
            else:
                logger.error(f"No access token in response: {token_response}")
                return None
    except Exception as e:
        logger.error(f"Failed to get OAuth access token: {str(e)}", exc_info=True)
        return None

def send_fcm_notification(tokens, title, body, data=None):
    """Sends FCM notification to a list of tokens using Firebase HTTP v1 API."""
    project_id = 'campus-navigator-ab838'
    url = f'https://fcm.googleapis.com/v1/projects/{project_id}/messages:send'
    access_token = get_access_token()
    if not access_token:
        logger.error("Could not get access token, cannot send FCM notification.")
        return 0
    headers = {'Authorization': f'Bearer {access_token}', 'Content-Type': 'application/json'}
    success_count = 0
    for token in tokens:
        payload = {"message": {"token": token, "notification": {"title": title, "body": body}, "data": data if data else {}, "android": {"priority": "high", "notification": {"sound": "default", "channel_id": "navicamp_notifications"}}}}
        try:
            req = urllib.request.Request(url, data=json.dumps(payload).encode('utf-8'), headers=headers)
            with urllib.request.urlopen(req) as response:
                if 200 <= response.status < 300:
                    logger.info(f"Successfully sent FCM to token: ...{token[-20:]}")
                    success_count += 1
                else:
                    logger.error(f"Failed to send FCM. Status: {response.status}, Response: {response.read().decode('utf-8')}")
        except urllib.error.HTTPError as e:
            logger.error(f"HTTP error sending FCM: {e.code} - {e.read().decode('utf-8')}")
        except Exception as e:
            logger.error(f"Error sending FCM notification: {str(e)}", exc_info=True)
    logger.info(f"Successfully sent {success_count}/{len(tokens)} FCM notifications")
    return success_count

def lambda_handler(event, context):
    """Main Lambda handler function"""
    try:
        body = json.loads(event.get('body', '{}'))
        logger.info(f"Received request: {json.dumps(body, default=str)}")
        
        # Handle both 'notificationType' (from assistance req) and 'type' (from officer res)
        notification_type = body.get('notificationType', body.get('type', ''))
        assistance_type = body.get('assistanceType', '')  # NEW: Check for wheelchair fall

        if notification_type == 'assistance_request':
            if assistance_type == 'wheelchair_fall':
                return handle_wheelchair_fall_request(body)
            else:
                return handle_assistance_request(body)
        elif notification_type == 'officer_response':
            return handle_officer_response(body)
        elif notification_type == 'assistance_resolved':
            return handle_assistance_resolved(body)
        else:
            return create_error_response(f"Unknown or missing notification type. Received: '{notification_type}'")
    except Exception as e:
        logger.error(f"CRITICAL ERROR in lambda_handler: {str(e)}", exc_info=True)
        return create_error_response("Internal server error occurred", 500)

def handle_wheelchair_fall_request(body):
    """Handles wheelchair fall detection notifications"""
    location_id = body.get('locationID')
    user_id = body.get('userID')
    device_id = body.get('deviceID')
    floor_level = body.get('floorLevel')
    
    if not all([location_id, user_id, device_id]):
        return create_error_response("locationID, userID, and deviceID are required for wheelchair fall notification")

    user_details = get_user_details(user_id)
    details = get_assistance_details(location_id)
    
    if not user_details:
        return create_error_response("Could not retrieve user details")

    user_name = user_details.get('fullName', 'A user')
    floor = floor_level or 'an unknown location'
    alert_id = details.get('alertID', 'Unknown') if details else 'Unknown'
    
    # Wheelchair fall specific notification
    title = f"🚨 WHEELCHAIR FALL DETECTED at {floor}"
    message_body = f"⚠️ {user_name}'s wheelchair has fallen and may need immediate assistance. (Device: {device_id}) [Alert: {alert_id}]"
    
    data = {
        "type": "wheelchair_fall", 
        "locationID": str(location_id), 
        "userID": str(user_id),
        "fullName": user_name, 
        "floorLevel": floor, 
        "deviceID": str(device_id),
        "alertID": str(alert_id), 
        "assistanceType": "wheelchair_fall",
        "timestamp": datetime.now(timezone.utc).isoformat()
    }
    
    tokens = get_security_officer_tokens()
    if not tokens:
        return create_error_response("No responders/admins available to notify")
    
    success_count = send_fcm_notification(tokens, title, message_body, data)
    return create_success_response(f"Sent wheelchair fall alert to {success_count}/{len(tokens)} responders/admins.")

def handle_assistance_request(body):
    """Handles new assistance requests"""
    location_id = body.get('locationID')
    user_id = body.get('userID')
    if not all([location_id, user_id]):
        return create_error_response("locationID and userID are required for assistance request")

    user_details = get_user_details(user_id)
    details = get_assistance_details(location_id)
    if not user_details or not details:
        return create_error_response("Could not retrieve user or assistance details")

    user_name = user_details.get('fullName', 'A user')
    floor = details.get('floorLevel', 'an unknown location')
    device_id = details.get('deviceID', 'Unknown device')
    alert_id = details.get('alertID', 'Unknown')
    title = f"🚨 Assistance Request at {floor}"
    message_body = f"{user_name} needs help. (Device: {device_id}) [Alert: {alert_id}]"
    
    data = {
        "type": "assistance_request", "locationID": str(location_id), "userID": str(user_id),
        "fullName": user_name, "floorLevel": floor, "deviceID": str(device_id),
        "alertID": str(alert_id), "timestamp": datetime.now(timezone.utc).isoformat()
    }
    tokens = get_security_officer_tokens()
    if not tokens:
        return create_error_response("No responders/admins available to notify")
    success_count = send_fcm_notification(tokens, title, message_body, data)
    return create_success_response(f"Sent assistance request to {success_count}/{len(tokens)} responders/admins.")

def get_other_security_officer_tokens(responding_officer_id):
    """Get all responder/admin tokens, excluding the responding officer."""
    connection = None
    try:
        connection = get_db_connection()
        with connection.cursor() as cursor:
            query = """
                SELECT DISTINCT u.fcm_token
                FROM user_table u
                LEFT JOIN safety_officer_profiles_table s ON u.userID = s.userID
                WHERE u.verified = 1
                  AND u.fcm_token IS NOT NULL
                  AND u.fcm_token != ''
                  AND u.userID != %s
                  AND (
                        s.userID IS NOT NULL
                        OR LOWER(COALESCE(u.userType, '')) LIKE '%admin%'
                  )
            """
            cursor.execute(query, (responding_officer_id,))
            tokens = [row['fcm_token'] for row in cursor.fetchall()]
            logger.info(f"Found {len(tokens)} other responders/admins to notify.")
            return tokens
    except Exception as e:
        logger.error(f"Error getting other security officer tokens: {str(e)}")
        return []
    finally:
        if connection:
            connection.close()

def get_user_fcm_token(user_id):
    """Get FCM token for a specific user"""
    connection = None
    try:
        connection = get_db_connection()
        with connection.cursor() as cursor:
            query = "SELECT fcm_token FROM user_table WHERE userID = %s AND fcm_token IS NOT NULL AND fcm_token != ''"
            cursor.execute(query, (user_id,))
            result = cursor.fetchone()
            if result:
                logger.info(f"Found FCM token for user {user_id}")
                return result['fcm_token']
            else:
                logger.warning(f"No FCM token found for user {user_id}")
                return None
    except Exception as e:
        logger.error(f"Error getting FCM token for user {user_id}: {str(e)}")
        return None
    finally:
        if connection:
            connection.close()

def handle_officer_response(body):
    """Handles officer responding to an assistance request"""
    location_id = body.get('locationID')
    officer_id = body.get('officerID')
    user_id = body.get('userID', '')
    
    if not all([location_id, officer_id]):
        return create_error_response("locationID and officerID are required for officer response")
    
    officer_details = get_user_details(officer_id)
    if not officer_details:
        return create_error_response(f"Could not find details for officer {officer_id}")
    
    # Get assistance details to extract alertID
    assistance_details = get_assistance_details(location_id)
    alert_id = assistance_details.get('alertID', 'Unknown') if assistance_details else 'Unknown'
    
    officer_name = officer_details.get('fullName', 'An officer')
    
    # Notify other responders/admins that this officer is responding
    other_officer_tokens = get_other_security_officer_tokens(officer_id)
    officers_notified = 0
    if other_officer_tokens:
        title = "✅ Assistance Claimed"
        notification_body = f"{officer_name} is responding to the request. [Alert: {alert_id}]"
        data = {
            "type": "officer_response", "locationID": str(location_id), "officerID": str(officer_id),
            "officerName": officer_name, "userID": user_id, "alertID": str(alert_id),
            "timestamp": datetime.now(timezone.utc).isoformat()
        }
        officers_notified = send_fcm_notification(other_officer_tokens, title, notification_body, data)
    
    # Notify the user who requested assistance that help is coming
    user_notified = 0
    if user_id:
        user_token = get_user_fcm_token(user_id)
        if user_token:
            user_title = "🚀 Help is Coming!"
            user_notification_body = f"{officer_name} is coming to assist you. [Alert: {alert_id}]"
            user_data = {
                "type": "officer_coming", "locationID": str(location_id), "officerID": str(officer_id),
                "officerName": officer_name, "userID": user_id, "alertID": str(alert_id),
                "timestamp": datetime.now(timezone.utc).isoformat()
            }
            user_notified = send_fcm_notification([user_token], user_title, user_notification_body, user_data)
    
    # Create response message
    messages = [f"Response from {officer_name} recorded."]
    if officers_notified > 0:
        messages.append(f"Notified {officers_notified} other responder/admin user(s).")
    if user_notified > 0:
        messages.append(f"Notified user that help is coming.")
    elif user_id:
        messages.append("Could not notify user (no FCM token found).")
    
    return create_success_response(" ".join(messages))

def handle_assistance_resolved(body):
    """Handles assistance resolution notifications"""
    logger.info("Assistance resolved notification received")
    
    location_id = body.get('locationID')
    user_id = body.get('userID')
    officer_id = body.get('officerID', '')  # The officer who resolved it
    
    if not all([location_id, user_id]):
        return create_error_response("locationID and userID are required for assistance resolved")
    
    # Get user details who originally requested assistance
    user_details = get_user_details(user_id)
    assistance_details = get_assistance_details(location_id)
    
    if not user_details:
        return create_error_response(f"Could not find details for user {user_id}")
    
    user_name = user_details.get('fullName', 'A user')
    floor = assistance_details.get('floorLevel', 'unknown location') if assistance_details else 'unknown location'
    alert_id = assistance_details.get('alertID', 'Unknown') if assistance_details else 'Unknown'
    
    # Notify all responders/admins EXCEPT the one who resolved it
    if officer_id:
        officer_tokens = get_other_security_officer_tokens(officer_id)
    else:
        officer_tokens = get_security_officer_tokens()
    
    officers_notified = 0
    
    if officer_tokens:
        title = "✅ Assistance Resolved"
        notification_body = f"{user_name}'s assistance request on {floor} has been resolved. [Alert: {alert_id}]"
        data = {
            "type": "assistance_resolved", "locationID": str(location_id), "userID": str(user_id),
            "userName": user_name, "floorLevel": floor, "alertID": str(alert_id),
            "timestamp": datetime.now(timezone.utc).isoformat()
        }
        officers_notified = send_fcm_notification(officer_tokens, title, notification_body, data)
    
    # Create response message
    if officers_notified > 0:
        return create_success_response(f"Assistance resolved. Notified {officers_notified} other responder/admin user(s).")
    else:
        return create_success_response("Assistance resolved. No other responder/admin users to notify.")

def create_success_response(message):
    """Create a successful response"""
    return {'statusCode': 200, 'body': json.dumps({'success': True, 'message': message})}

def create_error_response(message, status_code=400):
    """Create an error response"""
    return {'statusCode': status_code, 'body': json.dumps({'success': False, 'error': message})}
