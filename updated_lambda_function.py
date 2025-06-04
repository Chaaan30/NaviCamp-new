import json
import os
import pymysql
import boto3
from botocore.exceptions import ClientError
import base64

# Environment variables (ensure these are set in your Lambda function's configuration)
SECRET_NAME = os.environ.get('DB_SECRET_NAME') 
RDS_HOST = os.environ.get('DB_HOST') 
DB_NAME = os.environ.get('DB_NAME') 

def get_secret():
    """Retrieves database credentials from AWS Secrets Manager."""
    region_name = os.environ.get('AWS_REGION', "ap-southeast-1")

    session = boto3.session.Session()
    client = session.client(
        service_name='secretsmanager',
        region_name=region_name
    )

    try:
        get_secret_value_response = client.get_secret_value(
            SecretId=SECRET_NAME
        )
    except ClientError as e:
        print(f"Error retrieving secret: {e}")
        raise e
    else:
        if 'SecretString' in get_secret_value_response:
            secret = get_secret_value_response['SecretString']
            return json.loads(secret)
        else:
            decoded_binary_secret = base64.b64decode(get_secret_value_response['SecretBinary'])
            return json.loads(decoded_binary_secret)

def send_notification_email(full_name, user_id, action_type):
    """Send email notification to the user about their verification status."""
    try:
        # Initialize SES client
        ses_client = boto3.client('ses', region_name='ap-southeast-1')
        
        # Email configuration
        sender_email = "noreply@navicamp.com"  # Replace with your verified SES email
        recipient_email = f"{user_id}@student.edu.ph"  # Adjust email format as needed
        
        if action_type == "verified":
            subject = "NaviCamp Account Verified Successfully"
            body_html = f"""
            <html>
                <body>
                    <h2>Account Verification Successful</h2>
                    <p>Dear {full_name},</p>
                    <p>Your NaviCamp account has been successfully verified!</p>
                    <p>User ID: {user_id}</p>
                    <p>You can now access all features of the NaviCamp application.</p>
                    <br>
                    <p>Best regards,<br>NaviCamp Admin Team</p>
                </body>
            </html>
            """
        else:  # rejected
            subject = "NaviCamp Account Verification Rejected"
            body_html = f"""
            <html>
                <body>
                    <h2>Account Verification Rejected</h2>
                    <p>Dear {full_name},</p>
                    <p>Unfortunately, your NaviCamp account verification has been rejected.</p>
                    <p>User ID: {user_id}</p>
                    <p>Please contact support for more information or to resubmit your verification documents.</p>
                    <br>
                    <p>Best regards,<br>NaviCamp Admin Team</p>
                </body>
            </html>
            """
        
        # Send email
        response = ses_client.send_email(
            Source=sender_email,
            Destination={'ToAddresses': [recipient_email]},
            Message={
                'Subject': {'Data': subject},
                'Body': {'Html': {'Data': body_html}}
            }
        )
        
        print(f"Email sent successfully. MessageId: {response['MessageId']}")
        return True
        
    except Exception as e:
        print(f"Failed to send email notification: {e}")
        return False

def create_response_page(message, success, email_sent):
    """Create a modern, responsive HTML page for the response."""
    
    # Choose colors and icons based on success
    if success:
        bg_color = "#d4edda"
        border_color = "#c3e6cb"
        text_color = "#155724"
        icon = "✅"
        title = "Verification Complete"
    else:
        bg_color = "#f8d7da"
        border_color = "#f5c6cb"
        text_color = "#721c24"
        icon = "❌"
        title = "Action Failed"
    
    email_status = ""
    if success:
        if email_sent:
            email_status = '<p class="email-status success">📧 SMTP email notification has been sent to the user.</p>'
        else:
            email_status = '<p class="email-status warning">⚠️ Email notification could not be sent.</p>'
    
    html = f"""
    <!DOCTYPE html>
    <html lang="en">
    <head>
        <meta charset="UTF-8">
        <meta name="viewport" content="width=device-width, initial-scale=1.0">
        <title>{title} - NaviCamp</title>
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
                border-radius: 15px;
                padding: 40px;
                box-shadow: 0 20px 40px rgba(0,0,0,0.1);
                max-width: 500px;
                width: 100%;
                text-align: center;
                position: relative;
                overflow: hidden;
            }}
            
            .container::before {{
                content: '';
                position: absolute;
                top: 0;
                left: 0;
                right: 0;
                height: 5px;
                background: linear-gradient(90deg, #667eea, #764ba2);
            }}
            
            .logo {{
                font-size: 24px;
                font-weight: bold;
                color: #333;
                margin-bottom: 30px;
                display: flex;
                align-items: center;
                justify-content: center;
                gap: 10px;
            }}
            
            .status-icon {{
                font-size: 48px;
                margin-bottom: 20px;
            }}
            
            .title {{
                font-size: 28px;
                font-weight: 600;
                color: #333;
                margin-bottom: 20px;
            }}
            
            .message {{
                background-color: {bg_color};
                border: 2px solid {border_color};
                color: {text_color};
                padding: 20px;
                border-radius: 10px;
                margin-bottom: 20px;
                font-size: 16px;
                line-height: 1.5;
                font-weight: 500;
            }}
            
            .email-status {{
                padding: 15px;
                border-radius: 8px;
                margin-bottom: 20px;
                font-size: 14px;
                font-weight: 500;
            }}
            
            .email-status.success {{
                background-color: #e8f5e8;
                color: #2d5016;
                border: 1px solid #c3e6cb;
            }}
            
            .email-status.warning {{
                background-color: #fff3cd;
                color: #856404;
                border: 1px solid #ffeaa7;
            }}
            
            .close-btn {{
                background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
                color: white;
                border: none;
                padding: 12px 30px;
                border-radius: 25px;
                font-size: 16px;
                font-weight: 600;
                cursor: pointer;
                transition: all 0.3s ease;
                text-decoration: none;
                display: inline-block;
            }}
            
            .close-btn:hover {{
                transform: translateY(-2px);
                box-shadow: 0 5px 15px rgba(102, 126, 234, 0.3);
            }}
            
            .footer {{
                margin-top: 30px;
                padding-top: 20px;
                border-top: 1px solid #eee;
                color: #666;
                font-size: 12px;
            }}
            
            @media (max-width: 480px) {{
                .container {{
                    padding: 25px;
                }}
                
                .title {{
                    font-size: 24px;
                }}
                
                .message {{
                    font-size: 14px;
                }}
            }}
        </style>
    </head>
    <body>
        <div class="container">
            <div class="logo">
                🧭 NaviCamp Admin
            </div>
            
            <div class="status-icon">{icon}</div>
            
            <h1 class="title">{title}</h1>
            
            <div class="message">
                {message}
            </div>
            
            {email_status}
            
            <button class="close-btn" onclick="window.close();">
                Close Window
            </button>
            
            <div class="footer">
                NaviCamp User Verification System<br>
                Processed at {os.environ.get('AWS_REGION', 'ap-southeast-1')}
            </div>
        </div>
        
        <script>
            // Auto-close after 10 seconds if user doesn't interact
            setTimeout(function() {{
                if (document.hasFocus()) {{
                    window.close();
                }}
            }}, 10000);
        </script>
    </body>
    </html>
    """
    
    return html

def lambda_handler(event, context):
    """
    Handles API Gateway request to verify or reject a user.
    Expects query string parameters: 'userID' and 'action' ('verify' or 'reject').
    """
    print(f"Received event: {json.dumps(event)}")
    print("=== Starting lambda_handler ===")

    try:
        print("=== Extracting parameters ===")
        params = event.get('queryStringParameters', {})
        user_id = params.get('userID')
        action = params.get('action')
        print(f"Parameters - userID: {user_id}, action: {action}")

        if not user_id or not action:
            print("Missing userID or action parameter")
            return {
                'statusCode': 400,
                'headers': {'Content-Type': 'text/html'},
                'body': create_response_page("Missing userID or action parameter.", False, False)
            }

        print("=== Validating action ===")
        action = action.lower()
        if action not in ['verify', 'reject']:
            print(f"Invalid action: {action}")
            return {
                'statusCode': 400,
                'headers': {'Content-Type': 'text/html'},
                'body': create_response_page('Invalid action specified. Use "verify" or "reject".', False, False)
            }
        
        print("=== Checking environment variables ===")
        print(f"SECRET_NAME: {SECRET_NAME}")
        print(f"RDS_HOST: {RDS_HOST}")
        print(f"DB_NAME: {DB_NAME}")
        
        if not all([SECRET_NAME, RDS_HOST, DB_NAME]):
            print("Missing database configuration environment variables")
            return {
                'statusCode': 500,
                'headers': {'Content-Type': 'text/html'},
                'body': create_response_page("Database environment variables not set.", False, False)
            }

        print("=== About to call get_secret() ===")
        db_credentials = get_secret()
        print("=== Successfully retrieved secret ===")
        
        db_username = db_credentials.get('username')
        db_password = db_credentials.get('password')
        print(f"Retrieved credentials - username: {db_username}")

        if not db_username or not db_password:
            print("Could not retrieve username or password from secret.")
            return {
                'statusCode': 500,
                'headers': {'Content-Type': 'text/html'},
                'body': create_response_page("Could not parse database credentials from secret.", False, False)
            }

        print("=== About to connect to database ===")
        connection = None
        try:
            connection = pymysql.connect(host=RDS_HOST,
                                         user=db_username,
                                         password=db_password,
                                         database=DB_NAME,
                                         connect_timeout=30)
            print("=== Successfully connected to RDS MySQL instance ===")

            message = ""
            success = False
            email_sent = False

            with connection.cursor() as cursor:
                # First, get the user's full name
                get_user_sql = "SELECT `fullName` FROM `user_table` WHERE `userID` = %s"
                cursor.execute(get_user_sql, (user_id,))
                user_result = cursor.fetchone()
                
                if not user_result:
                    message = f"User {user_id} not found in the database."
                    success = False
                    full_name = "Unknown User"
                    email_sent = False
                else:
                    full_name = user_result[0]
                    
                    if action == 'verify':
                        # Update user verification status
                        sql = "UPDATE `user_table` SET `verified` = 1 WHERE `userID` = %s"
                        cursor.execute(sql, (user_id,))
                        connection.commit()
                        if cursor.rowcount > 0:
                            message = f"{full_name} ({user_id}) proof has been successfully verified."
                            success = True
                            email_sent = send_notification_email(full_name, user_id, "verified")
                            print(f"User verified: {message}")
                        else:
                            message = f"{full_name} ({user_id}) not found or no changes made (already verified?)."
                            success = False
                            email_sent = False
                            print(message)
                    
                    elif action == 'reject':
                        # Delete the user record
                        sql = "DELETE FROM `user_table` WHERE `userID` = %s"
                        cursor.execute(sql, (user_id,))
                        connection.commit()
                        if cursor.rowcount > 0:
                            message = f"{full_name} ({user_id}) proof has been successfully rejected (record deleted)."
                            success = True
                            email_sent = send_notification_email(full_name, user_id, "rejected")
                            print(f"User rejected: {message}")
                        else:
                            message = f"{full_name} ({user_id}) not found for deletion."
                            success = False
                            email_sent = False
                            print(message)

            status_code = 200 if success else 404
            
            # Create a modern HTML response
            html_response_body = create_response_page(message, success, email_sent)
            
            return {
                'statusCode': status_code,
                'headers': {'Content-Type': 'text/html'},
                'body': html_response_body
            }

        except pymysql.MySQLError as e:
            print(f"=== Database connection failed: {e} ===")
            return {
                'statusCode': 500,
                'headers': {'Content-Type': 'text/html'},
                'body': create_response_page("A database operational error occurred. Please check logs.", False, False)
            }
        finally:
            if connection and connection.open:
                connection.close()
                print("Database connection closed.")

    except Exception as e:
        print(f"=== ERROR in lambda_handler: {e} ===")
        import traceback
        traceback.print_exc()
        return {
            'statusCode': 500,
            'headers': {'Content-Type': 'text/html'},
            'body': create_response_page("An unexpected error occurred. Please check server logs.", False, False)
        }
