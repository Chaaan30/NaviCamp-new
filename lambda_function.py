import json
import os
import pymysql
import boto3
from botocore.exceptions import ClientError
import base64
from datetime import datetime, timezone, timedelta
import traceback
import re
import time

# Configuration
CONFIG = {
    'MAX_USER_ID_LENGTH': 20,
    'VALID_ACTIONS': ['verify', 'reject'],
    'AUTO_CLOSE_DELAY': 10,     # seconds for HTML auto-close
}

# Environment variables
SECRET_NAME = os.environ.get('DB_SECRET_NAME') 
RDS_HOST = os.environ.get('DB_HOST') 
DB_NAME = os.environ.get('DB_NAME') 

def validate_input(user_id, action):
    """Validate and sanitize input parameters."""
    errors = []
    
    # Validate user_id
    if not user_id:
        errors.append("User ID is required")
    elif len(user_id) > CONFIG['MAX_USER_ID_LENGTH']:
        errors.append(f"User ID exceeds maximum length of {CONFIG['MAX_USER_ID_LENGTH']}")
    elif not re.match(r'^[a-zA-Z0-9]+$', user_id):
        errors.append("User ID contains invalid characters (only alphanumeric allowed)")
    
    # Validate action
    if not action:
        errors.append("Action is required")
    elif action.lower() not in CONFIG['VALID_ACTIONS']:
        errors.append(f"Invalid action. Must be one of: {', '.join(CONFIG['VALID_ACTIONS'])}")
    
    return errors

def get_secret():
    """Retrieves all credentials from AWS Secrets Manager with enhanced error handling."""
    try:
        print(f"INFO: Retrieving secret '{SECRET_NAME}' from AWS Secrets Manager")
        region_name = os.environ.get('AWS_REGION', "ap-southeast-1")
        session = boto3.session.Session()
        client = session.client(service_name='secretsmanager', region_name=region_name)
        
        get_secret_value_response = client.get_secret_value(SecretId=SECRET_NAME)
        
        if 'SecretString' in get_secret_value_response:
            secret_data = json.loads(get_secret_value_response['SecretString'])
        else:
            secret_data = json.loads(base64.b64decode(get_secret_value_response['SecretBinary']))
        
        # Validate required keys
        required_keys = ['username', 'password']  # Removed gmail_app_password requirement
        missing_keys = [key for key in required_keys if key not in secret_data]
        if missing_keys:
            raise ValueError(f"Secret missing required keys: {', '.join(missing_keys)}")
        
        print("INFO: Secret retrieved and validated successfully")
        return secret_data
        
    except ClientError as e:
        error_code = e.response['Error']['Code']
        if error_code == 'ResourceNotFoundException':
            print(f"ERROR: Secret '{SECRET_NAME}' not found")
        elif error_code == 'InvalidRequestException':
            print(f"ERROR: Invalid request for secret '{SECRET_NAME}'")
        elif error_code == 'InvalidParameterException':
            print(f"ERROR: Invalid parameter for secret '{SECRET_NAME}'")
        else:
            print(f"ERROR: AWS error retrieving secret: {error_code} - {e}")
        raise e
    except json.JSONDecodeError as e:
        print(f"ERROR: Secret contains invalid JSON: {e}")
        raise e
    except Exception as e:
        print(f"ERROR: Unexpected error retrieving secret: {e}")
        raise e

def send_email_via_sqs(full_name, user_id, action_type, recipient_email):
    """Send email request to SQS instead of sending directly via SMTP."""
    try:
        sqs_client = boto3.client('sqs', region_name='ap-southeast-1')
        queue_url = 'https://ap-southeast-1.queue.amazonaws.com/043309335171/navicamp-email-queue'
        
        # Create message for SQS
        email_message = {
            'full_name': full_name,
            'user_id': user_id,
            'action_type': action_type,
            'recipient_email': recipient_email
        }
        
        print(f"INFO: Sending email request to SQS for {user_id} ({action_type}) to {recipient_email}")
        
        # Send message to SQS
        response = sqs_client.send_message(
            QueueUrl=queue_url,
            MessageBody=json.dumps(email_message)
        )
        
        print(f"SUCCESS: Email request sent to SQS with MessageId: {response.get('MessageId')}")
        return True
        
    except Exception as e:
        print(f"ERROR: Failed to send email request to SQS: {e}")
        return False

def create_response_page(message, success, email_sent, user_details=None):
    """Create HTML response page for the Lambda function."""
    if success:
        title = "✅ Action Completed Successfully"
        icon = "✅"
        color = "#28a745"
        gradient = "linear-gradient(135deg, #28a745 0%, #20c997 100%)"
    else:
        title = "❌ Action Failed"
        icon = "❌"
        color = "#dc3545"
        gradient = "linear-gradient(135deg, #dc3545 0%, #fd7e14 100%)"
    
    # Email status section
    email_status = ""
    if email_sent:
        email_status = """
        <div class="email-status success">
            <div class="email-icon">📧</div>
            <div>
                <strong>Email Notification Queued</strong>
                <p>The email notification has been successfully queued for delivery.</p>
            </div>
        </div>
        """
    elif email_sent is False:  # Explicitly False, not None
        email_status = """
        <div class="email-status warning">
            <div class="email-icon">⚠️</div>
            <div>
                <strong>Email Notification Failed</strong>
                <p>The action was completed but email notification could not be queued.</p>
            </div>
        </div>
        """
    
    # User details section
    user_info = ""
    if user_details:
        user_info = f"""
        <div class="user-details">
            <h3>📋 Action Details</h3>
            <div class="detail-row">
                <span class="label">Officer ID:</span>
                <span class="value">{user_details.get('user_id', 'N/A')}</span>
            </div>
            <div class="detail-row">
                <span class="label">Officer Name:</span>
                <span class="value">{user_details.get('full_name', 'N/A')}</span>
            </div>
            <div class="detail-row">
                <span class="label">Action Taken:</span>
                <span class="value">{user_details.get('action', 'N/A')}</span>
            </div>
        </div>
        """
    
    html = f"""
    <!DOCTYPE html>
    <html lang="en">
    <head>
        <meta charset="UTF-8">
        <meta name="viewport" content="width=device-width, initial-scale=1.0">
        <title>{title} - NaviCamp Admin</title>
        <style>
            * {{
                margin: 0;
                padding: 0;
                box-sizing: border-box;
            }}
            
            body {{
                font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;
                background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
                min-height: 100vh;
                display: flex;
                align-items: center;
                justify-content: center;
                padding: 20px;
            }}
            
            .container {{
                background: white;
                border-radius: 20px;
                padding: 40px;
                box-shadow: 0 25px 50px rgba(0,0,0,0.15);
                max-width: 600px;
                width: 100%;
                text-align: center;
                position: relative;
                overflow: hidden;
                animation: slideIn 0.5s ease-out;
            }}
            
            @keyframes slideIn {{
                from {{ opacity: 0; transform: translateY(30px); }}
                to {{ opacity: 1; transform: translateY(0); }}
            }}
            
            .container::before {{
                content: '';
                position: absolute;
                top: 0;
                left: 0;
                right: 0;
                height: 6px;
                background: {gradient};
            }}
            
            .logo {{
                font-size: 18px;
                font-weight: 600;
                color: #666;
                margin-bottom: 20px;
                display: flex;
                align-items: center;
                justify-content: center;
                gap: 10px;
            }}
            
            .status-icon {{
                font-size: 64px;
                margin-bottom: 20px;
                animation: bounce 0.6s ease-out;
            }}
            
            @keyframes bounce {{
                0%, 20%, 50%, 80%, 100% {{ transform: translateY(0); }}
                40% {{ transform: translateY(-10px); }}
                60% {{ transform: translateY(-5px); }}
            }}
            
            .title {{
                font-size: 28px;
                font-weight: 700;
                color: #333;
                margin-bottom: 20px;
                line-height: 1.2;
            }}
            
            .message {{
                background: rgba({color.replace('#', '').replace(color.replace('#', ''), f"{int(color.replace('#', '')[:2], 16)}, {int(color.replace('#', '')[2:4], 16)}, {int(color.replace('#', '')[4:], 16)}")}, 0.1);
                border: 2px solid {color};
                color: {color};
                padding: 25px;
                border-radius: 12px;
                margin-bottom: 25px;
                font-size: 18px;
                line-height: 1.6;
                font-weight: 500;
            }}
            
            .email-status {{
                display: flex;
                align-items: center;
                padding: 20px;
                border-radius: 10px;
                margin-bottom: 25px;
                gap: 15px;
                text-align: left;
            }}
            
            .email-status.success {{
                background-color: #e8f5e8;
                color: #2d5016;
                border: 2px solid #c3e6cb;
            }}
            
            .email-status.warning {{
                background-color: #fff3cd;
                color: #856404;
                border: 2px solid #ffeaa7;
            }}
            
            .email-icon {{
                font-size: 24px;
                flex-shrink: 0;
            }}
            
            .email-status p {{
                margin: 5px 0 0 0;
                font-size: 14px;
                opacity: 0.9;
            }}
            
            .user-details {{
                background: #f8f9fa;
                border: 1px solid #dee2e6;
                border-radius: 10px;
                padding: 25px;
                margin-bottom: 25px;
                text-align: left;
            }}
            
            .user-details h3 {{
                color: #333;
                margin-bottom: 20px;
                text-align: center;
                font-size: 20px;
            }}
            
            .detail-row {{
                display: flex;
                justify-content: space-between;
                padding: 10px 0;
                border-bottom: 1px solid #e9ecef;
            }}
            
            .detail-row:last-child {{
                border-bottom: none;
            }}
            
            .label {{
                font-weight: 600;
                color: #666;
            }}
            
            .value {{
                color: #333;
                font-weight: 500;
            }}
            
            .actions {{
                display: flex;
                gap: 15px;
                justify-content: center;
                margin-bottom: 25px;
            }}
            
            .close-btn {{
                background: {gradient};
                color: white;
                border: none;
                padding: 15px 35px;
                border-radius: 30px;
                font-size: 16px;
                font-weight: 600;
                cursor: pointer;
                transition: all 0.3s ease;
                text-decoration: none;
                display: inline-block;
                box-shadow: 0 4px 15px rgba(102,126,234,0.3);
            }}
            
            .close-btn:hover {{
                transform: translateY(-2px);
                box-shadow: 0 8px 25px rgba(102,126,234,0.4);
            }}
            
            .refresh-btn {{
                background: transparent;
                color: #667eea;
                border: 2px solid #667eea;
                padding: 15px 35px;
                border-radius: 30px;
                font-size: 16px;
                font-weight: 600;
                cursor: pointer;
                transition: all 0.3s ease;
                text-decoration: none;
                display: inline-block;
            }}
            
            .refresh-btn:hover {{
                background: #667eea;
                color: white;
                transform: translateY(-2px);
            }}
            
            .countdown {{
                margin-top: 25px;
                font-size: 14px;
                color: #666;
                background: #f8f9fa;
                padding: 15px;
                border-radius: 8px;
                border: 1px solid #dee2e6;
            }}
            
            .footer {{
                margin-top: 35px;
                padding-top: 25px;
                border-top: 1px solid #eee;
                color: #666;
                font-size: 12px;
                line-height: 1.4;
            }}
            
            @media (max-width: 480px) {{
                .container {{
                    padding: 25px;
                    margin: 10px;
                }}
                .title {{
                    font-size: 24px;
                }}
                .message {{
                    font-size: 16px;
                }}
                .actions {{
                    flex-direction: column;
                }}
            }}
        </style>
    </head>
    <body>
        <div class="container">
            <div class="logo">🧭 NaviCamp Security Officer Admin</div>
            <div class="status-icon">{icon}</div>
            <h1 class="title">{title}</h1>
            <div class="message">{message}</div>
            {email_status}
            {user_info}
            <div class="actions">
                <button class="close-btn" onclick="window.close();">Close Window</button>
                <button class="refresh-btn" onclick="window.location.reload();">Refresh Page</button>
            </div>
            <div class="countdown" id="countdown"></div>
            <div class="footer">
                NaviCamp Security Officer Verification System<br>
                Mapua Malayan Colleges Laguna (MMCL) - Secure Administrative Portal<br>
                {datetime.now().strftime('%Y-%m-%d %H:%M:%S UTC')}
            </div>
        </div>
        
        <script>
            let timeLeft = {CONFIG['AUTO_CLOSE_DELAY']};
            let isPaused = false;
            let countdownTimer;
            const countdownElement = document.getElementById('countdown');
            const containerElement = document.querySelector('.container');
            
            function updateCountdown() {{
                if (!isPaused && timeLeft > 0) {{
                    countdownElement.innerHTML = `🕒 This window will close automatically in ${{timeLeft}} seconds`;
                    timeLeft--;
                }} else if (!isPaused && timeLeft <= 0) {{
                    countdownElement.innerHTML = "🕒 Closing window...";
                    setTimeout(() => window.close(), 1000);
                    return;
                }} else if (isPaused) {{
                    countdownElement.innerHTML = "⏸️ Timer paused - Move cursor away from card to resume";
                }}
                
                countdownTimer = setTimeout(updateCountdown, 1000);
            }}
            
            // Add hover event listeners to pause/resume timer
            containerElement.addEventListener('mouseenter', function() {{
                isPaused = true;
            }});
            
            containerElement.addEventListener('mouseleave', function() {{
                isPaused = false;
                timeLeft = {CONFIG['AUTO_CLOSE_DELAY']}; // Reset timer to 10 seconds
                countdownElement.innerHTML = `🕒 Timer reset - Window will close in ${{timeLeft}} seconds`;
            }});
            
            // Start the countdown
            updateCountdown();
        </script>
    </body>
    </html>
    """
    
    return html

def check_user_status(cursor, user_id):
    """Check if user exists and get their current status."""
    try:
        query = """
        SELECT fullName, email, verified, proofPicture 
        FROM user_table 
        WHERE userID = %s
        """
        cursor.execute(query, (user_id,))
        result = cursor.fetchone()
        
        if result:
            return {
                'exists': True,
                'full_name': result[0],
                'email': result[1],
                'verified': bool(result[2]),
                'proof_picture': result[3]
            }
        else:
            return {'exists': False}
    except Exception as e:
        print(f"ERROR: Error checking user status: {e}")
        return {'exists': False}

def delete_s3_proof_image(proof_picture_filename):
    """Delete proof image from S3 bucket with timeout controls."""
    if not proof_picture_filename:
        print(f"INFO: No proof picture filename provided, skipping S3 deletion")
        return True
    
    try:
        # Set timeouts for S3 operations
        s3_client = boto3.client(
            's3',
            config=boto3.session.Config(
                connect_timeout=10,
                read_timeout=15,
                retries={'max_attempts': 2}
            )
        )
        bucket_name = 'navicampbucket'
        # Don't add prefix - the filename already contains the full path
        object_key = proof_picture_filename
        
        print(f"INFO: Attempting to delete S3 object: {object_key} from bucket: {bucket_name}")
        
        # Skip existence check to save time, just try to delete directly
        s3_client.delete_object(Bucket=bucket_name, Key=object_key)
        print(f"SUCCESS: Deleted S3 object {object_key}")
        return True
        
    except ClientError as e:
        error_code = e.response['Error']['Code']
        if error_code == 'NoSuchKey':
            print(f"WARNING: S3 object {object_key} not found, may have been already deleted")
            return True  # Consider this a success since object doesn't exist
        else:
            print(f"ERROR: S3 ClientError deleting {object_key}: {error_code} - {e}")
            return False
    except Exception as e:
        print(f"ERROR: Unexpected error deleting S3 object {object_key}: {e}")
        return False

def lambda_handler(event, context):
    """Enhanced Lambda handler that uses SQS for email notifications."""
    print(f"INFO: Lambda execution started - Request ID: {context.aws_request_id}")
    
    try:
        # Extract and validate parameters
        params = event.get('queryStringParameters', {})
        user_id = params.get('userID', '').strip()
        action = params.get('action', '').strip().lower()
        
        print(f"INFO: Processing request - User ID: {user_id}, Action: {action}")
        
        # Input validation
        validation_errors = validate_input(user_id, action)
        if validation_errors:
            error_message = "Invalid request: " + "; ".join(validation_errors)
            print(f"ERROR: {error_message}")
            return {
                'statusCode': 400,
                'headers': {'Content-Type': 'text/html'},
                'body': create_response_page(error_message, False, False)
            }
        
        # Environment validation
        if not all([SECRET_NAME, RDS_HOST, DB_NAME]):
            print("ERROR: Missing required environment variables")
            return {
                'statusCode': 500,
                'headers': {'Content-Type': 'text/html'},
                'body': create_response_page("Server configuration error: Missing environment variables.", False, False)
            }
        
        # Get credentials
        print("INFO: Retrieving database credentials")
        secrets = get_secret()
        db_username = secrets.get('username')
        db_password = secrets.get('password')
        
        if not all([db_username, db_password]):
            print("ERROR: Incomplete credentials in secret")
            return {
                'statusCode': 500,
                'headers': {'Content-Type': 'text/html'},
                'body': create_response_page("Server configuration error: Incomplete credentials.", False, False)
            }
        
        # Database operations
        print("INFO: Connecting to database")
        connection = None
        try:
            connection = pymysql.connect(
                host=RDS_HOST,
                user=db_username,
                password=db_password,
                database=DB_NAME,
                connect_timeout=30,
                read_timeout=30,
                write_timeout=30
            )
            
            with connection.cursor() as cursor:
                # Check current user status
                print(f"INFO: Checking status for user {user_id}")
                user_status = check_user_status(cursor, user_id)
                
                if not user_status['exists']:
                    message = f"Security Officer {user_id} not found in the database."
                    print(f"WARNING: {message}")
                    return {
                        'statusCode': 404,
                        'headers': {'Content-Type': 'text/html'},
                        'body': create_response_page(message, False, False)
                    }
                
                # Duplicate action prevention
                if action == 'verify':
                    if user_status['verified']:
                        message = f"Security Officer {user_status['full_name']} ({user_id}) is already verified. No action taken."
                        print(f"INFO: {message}")
                        return {
                            'statusCode': 200,
                            'headers': {'Content-Type': 'text/html'},
                            'body': create_response_page(
                                message, 
                                True, 
                                False,
                                {
                                    'user_id': user_id,
                                    'full_name': user_status['full_name'],
                                    'action': 'Already Verified'
                                }
                            )
                        }
                    
                    # Proceed with verification
                    print(f"INFO: Verifying user {user_id}")
                    update_query = "UPDATE user_table SET verified = 1 WHERE userID = %s"
                    cursor.execute(update_query, (user_id,))
                    connection.commit()
                    
                    if cursor.rowcount > 0:
                        message = f"✅ Security Officer {user_status['full_name']} ({user_id}) has been successfully verified and authorized!"
                        print(f"SUCCESS: User {user_id} verified")
                        
                        # Send notification email via SQS
                        email_sent = send_email_via_sqs(
                            user_status['full_name'], 
                            user_id, 
                            "verified", 
                            user_status['email']
                        )
                        
                        return {
                            'statusCode': 200,
                            'headers': {'Content-Type': 'text/html'},
                            'body': create_response_page(
                                message, 
                                True, 
                                email_sent,
                                {
                                    'user_id': user_id,
                                    'full_name': user_status['full_name'],
                                    'action': 'Verified'
                                }
                            )
                        }
                    else:
                        message = f"Failed to verify Security Officer {user_id}. Database update unsuccessful."
                        print(f"ERROR: {message}")
                        return {
                            'statusCode': 500,
                            'headers': {'Content-Type': 'text/html'},
                            'body': create_response_page(message, False, False)
                        }
                
                elif action == 'reject':
                    # Check if user is already verified - prevent deletion of verified users
                    if user_status['verified']:
                        message = f"❌ Cannot reject Security Officer {user_status['full_name']} ({user_id}) - Officer is already verified and active in the system. Verified officers cannot be deleted to prevent data loss."
                        print(f"WARNING: Attempted to reject verified user {user_id}")
                        return {
                            'statusCode': 403,
                            'headers': {'Content-Type': 'text/html'},
                            'body': create_response_page(
                                message, 
                                False, 
                                False,
                                {
                                    'user_id': user_id,
                                    'full_name': user_status['full_name'],
                                    'action': 'Rejection Blocked - Officer Already Verified'
                                }
                            )
                        }
                    
                    # Proceed with rejection (deletion) only for unverified users
                    print(f"INFO: Rejecting unverified user {user_id}")
                    
                    # First, delete the proof image from S3 if it exists (with timeout protection)
                    s3_deletion_success = True
                    if user_status.get('proof_picture'):
                        print(f"INFO: Deleting proof image for user {user_id}: {user_status['proof_picture']}")
                        try:
                            s3_deletion_success = delete_s3_proof_image(user_status['proof_picture'])
                            if not s3_deletion_success:
                                print(f"WARNING: Failed to delete S3 proof image, but continuing with database deletion")
                        except Exception as e:
                            print(f"WARNING: S3 deletion failed with exception: {e}, but continuing with database deletion")
                            s3_deletion_success = False
                    else:
                        print(f"INFO: No proof picture found for user {user_id}, skipping S3 deletion")
                    
                    # Delete from database
                    delete_query = "DELETE FROM user_table WHERE userID = %s AND verified = 0"
                    cursor.execute(delete_query, (user_id,))
                    connection.commit()
                    
                    if cursor.rowcount > 0:
                        # Construct success message (always clean, regardless of S3 status)
                        message = f"❌ Security Officer application for {user_status['full_name']} ({user_id}) has been rejected and removed from the system."
                        
                        print(f"SUCCESS: Unverified user {user_id} rejected and deleted")
                        
                        # Send notification email via SQS
                        email_sent = send_email_via_sqs(
                            user_status['full_name'], 
                            user_id, 
                            "rejected", 
                            user_status['email']
                        )
                        
                        return {
                            'statusCode': 200,
                            'headers': {'Content-Type': 'text/html'},
                            'body': create_response_page(
                                message, 
                                True, 
                                email_sent,
                                {
                                    'user_id': user_id,
                                    'full_name': user_status['full_name'],
                                    'action': 'Rejected'
                                }
                            )
                        }
                    else:
                        message = f"Failed to reject Security Officer {user_id}. Officer may not exist or may already be verified."
                        print(f"ERROR: {message}")
                        return {
                            'statusCode': 500,
                            'headers': {'Content-Type': 'text/html'},
                            'body': create_response_page(message, False, False)
                        }
        
        except pymysql.Error as e:
            error_msg = f"Database error: {str(e)}"
            print(f"ERROR: {error_msg}")
            return {
                'statusCode': 500,
                'headers': {'Content-Type': 'text/html'},
                'body': create_response_page(f"Database operation failed: {str(e)}", False, False)
            }
        
        finally:
            if connection:
                connection.close()
                print("INFO: Database connection closed")
    
    except Exception as e:
        error_msg = f"Unexpected error: {str(e)}"
        print(f"ERROR: {error_msg}")
        print(f"ERROR: Traceback: {traceback.format_exc()}")
        return {
            'statusCode': 500,
            'headers': {'Content-Type': 'text/html'},
            'body': create_response_page("An unexpected server error occurred. Please try again later.", False, False)
        } 