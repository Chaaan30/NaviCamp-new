import json
import boto3
import smtplib
from email.mime.text import MIMEText
from email.mime.multipart import MIMEMultipart
from datetime import datetime, timezone, timedelta
import time
import re

# Configuration
CONFIG = {
    'EMAIL_RETRY_ATTEMPTS': 2,
    'EMAIL_RETRY_DELAY': 1,
    'SMTP_TIMEOUT': 8,
}

def get_secret():
    """Retrieves email credentials from AWS Secrets Manager."""
    try:
        region_name = "ap-southeast-1"
        session = boto3.session.Session()
        client = session.client(service_name='secretsmanager', region_name=region_name)
        
        # Use the same secret name as your original Lambda
        secret_name = "navicamp/rds/credentials"
        get_secret_value_response = client.get_secret_value(SecretId=secret_name)
        
        secret_data = json.loads(get_secret_value_response['SecretString'])
        return secret_data
        
    except Exception as e:
        print(f"ERROR: Error retrieving secret: {e}")
        raise e

def send_notification_email(full_name, user_id, action_type, sender_password, recipient_email):
    """Send email notification with retry logic and timeout controls."""
    email_start_time = time.time()
    max_email_time = 10  # Maximum 10 seconds for all email attempts
    
    for attempt in range(1, CONFIG['EMAIL_RETRY_ATTEMPTS'] + 1):
        if time.time() - email_start_time > max_email_time:
            print(f"ERROR: Email operation exceeded maximum time limit of {max_email_time} seconds")
            return False
            
        try:
            print(f"INFO: Email attempt {attempt}/{CONFIG['EMAIL_RETRY_ATTEMPTS']} - Sending {action_type} notification to {recipient_email}")
            
            smtp_server = "smtp.gmail.com"
            smtp_port = 587
            sender_email = "navicamp.noreply@gmail.com"
            
            # Validate email format
            if not re.match(r'^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$', recipient_email):
                print(f"ERROR: Invalid email format: {recipient_email}")
                return False
            
            message = MIMEMultipart("alternative")
            
            # Create unique timestamp (Philippines time: UTC+8)
            manila_tz = timezone(timedelta(hours=8))
            manila_now = datetime.now(manila_tz)
            unique_id = manila_now.strftime("%Y%m%d%H%M%S%f")
            timestamp = manila_now.strftime("%B %d, %Y at %I:%M %p PHT")
            hidden_timestamp = f'<span style="display:none;font-size:0;color:transparent;">{unique_id}</span>'

            if action_type == "verified":
                subject = "✅ NaviCamp Security Officer Verification Approved"
                status_color = "#28a745"
                status_bg = "#d4edda"
                status_border = "#c3e6cb"
                status_text = "#155724"
                icon = "✅"
                action_text = "verified and authorized as a Security Officer"
                next_steps = "You can now log in and access the NaviCamp Security Officer dashboard. Your credentials have been approved for Mapua Malayan Colleges Laguna (MMCL)."
            else:  # rejected
                subject = "❌ NaviCamp Security Officer Verification Rejected"
                status_color = "#dc3545"
                status_bg = "#f8d7da"
                status_border = "#f5c6cb"
                status_text = "#721c24"
                icon = "❌"
                action_text = "rejected"
                next_steps = "Your application to become a NaviCamp Security Officer for MMCL has been rejected. Please contact our support team for more information or to resubmit your verification documents."

            html_body = f"""
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>{subject}</title>
            </head>
            <body style="margin: 0; padding: 0; background-color: #f8f9fa; font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;">
                {hidden_timestamp}
                <div style="max-width: 600px; margin: 40px auto; background: white; border-radius: 15px; overflow: hidden; box-shadow: 0 10px 30px rgba(0,0,0,0.1);">
                    <!-- Header -->
                    <div style="background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); color: white; padding: 30px 20px; text-align: center;">
                        <div style="font-size: 32px; margin-bottom: 10px;">🧭</div>
                        <h1 style="margin: 0; font-size: 24px; font-weight: 600;">NaviCamp</h1>
                        <p style="margin: 5px 0 0 0; opacity: 0.9; font-size: 14px;">MMCL Security Officer Verification System</p>
                    </div>
                    
                    <!-- Status Banner -->
                    <div style="background: {status_bg}; border: 2px solid {status_border}; color: {status_text}; padding: 25px; text-align: center; margin: 0;">
                        <div style="font-size: 32px; margin-bottom: 15px;">{icon}</div>
                        <h2 style="margin: 0; font-size: 18px; font-weight: 600;">Officer {action_text.title()}</h2>
                    </div>
                    
                    <!-- Content -->
                    <div style="padding: 40px 30px;">
                        <p style="font-size: 18px; color: #333; margin: 0 0 20px 0;">
                            <strong>Dear {full_name},</strong>
                        </p>
                        
                        <p style="font-size: 16px; color: #555; line-height: 1.6; margin: 0 0 25px 0;">
                            Your NaviCamp Security Officer application has been <strong>{action_text}</strong>.
                        </p>
                        
                        <!-- User Info Card -->
                        <div style="background: #f8f9fa; border-left: 4px solid {status_color}; padding: 20px; border-radius: 5px; margin: 25px 0;">
                            <p style="margin: 0; font-weight: 600; color: #333;">Officer Details:</p>
                            <p style="margin: 5px 0 0 0; color: #666;">Officer ID: <strong>{user_id}</strong></p>
                            <p style="margin: 5px 0 0 0; color: #666;">Processed: {timestamp}</p>
                        </div>
                        
                        <p style="font-size: 16px; color: #555; line-height: 1.6; margin: 25px 0;">
                            {next_steps}
                        </p>
                    </div>
                    
                    <!-- Footer -->
                    <div style="background: #f8f9fa; padding: 25px; border-top: 1px solid #e9ecef; text-align: center; color: #666; font-size: 12px;">
                        <p style="margin: 0;">NaviCamp Security Officer Verification System</p>
                        <p style="margin: 5px 0 0 0;">Mapua Malayan Colleges Laguna (MMCL) | Secure Administrative Portal</p>
                        <p style="margin: 5px 0 0 0;">{timestamp}</p>
                    </div>
                </div>
            </body>
            </html>
            """

            text_part = MIMEText(f"""
            NaviCamp Security Officer Verification

            Dear {full_name},

            Your NaviCamp Security Officer application has been {action_text}.

            Officer Details:
            - Officer ID: {user_id}
            - Institution: MMCL
            - Processed: {timestamp}

            {next_steps}

            Best regards,
            NaviCamp Team
            """, "plain")

            html_part = MIMEText(html_body, "html")
            message.attach(text_part)
            message.attach(html_part)

            message["Subject"] = subject
            message["From"] = sender_email
            message["To"] = recipient_email

            server = None
            try:
                server = smtplib.SMTP(smtp_server, smtp_port, timeout=CONFIG['SMTP_TIMEOUT'])
                server.starttls()
                server.login(sender_email, sender_password)
                server.sendmail(sender_email, recipient_email, message.as_string())
                print(f"SUCCESS: Email sent successfully to {recipient_email}")
                return True
            finally:
                if server:
                    try:
                        server.quit()
                    except:
                        pass
            
        except smtplib.SMTPAuthenticationError as e:
            print(f"ERROR: SMTP authentication failed: {e}")
            return False
        except Exception as e:
            print(f"WARNING: Email attempt {attempt} failed: {e}")
            if attempt < CONFIG['EMAIL_RETRY_ATTEMPTS']:
                print(f"INFO: Retrying in {CONFIG['EMAIL_RETRY_DELAY']} seconds...")
                time.sleep(CONFIG['EMAIL_RETRY_DELAY'])
            else:
                print(f"ERROR: Email failed after {CONFIG['EMAIL_RETRY_ATTEMPTS']} attempts")
                return False
    
    return False

def send_admin_notification_email(full_name, user_id, action_type, user_email, sender_password, admin_email="cristian013003@gmail.com"):
    """Send admin notification email with clean card design."""
    try:
        print(f"INFO: Sending admin notification for {action_type} action on user {user_id}")
        
        smtp_server = "smtp.gmail.com"
        smtp_port = 587
        sender_email = "navicamp.noreply@gmail.com"
        
        message = MIMEMultipart("alternative")
        
        # Create timestamp (Philippines time: UTC+8)
        manila_tz = timezone(timedelta(hours=8))
        manila_now = datetime.now(manila_tz)
        timestamp = manila_now.strftime("%B %d, %Y at %I:%M %p PHT")
        
        if action_type == "verified":
            subject = "✅ NaviCamp Admin: Security Officer Verified"
            status_color = "#28a745"
            action_text = "verified and authorized"
            action_icon = "✅"
        else:  # rejected
            subject = "❌ NaviCamp Admin: Security Officer Rejected"
            status_color = "#dc3545"
            action_text = "rejected and removed"
            action_icon = "❌"

        # Clean card-based admin email template
        html_body = f"""
        <!DOCTYPE html>
        <html lang="en">
        <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <title>{subject}</title>
        </head>
        <body style="margin: 0; padding: 0; background-color: #f8f9fa; font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;">
            <div style="max-width: 600px; margin: 40px auto; background: white; border-radius: 15px; overflow: hidden; box-shadow: 0 10px 30px rgba(0,0,0,0.1);">
                <!-- Header -->
                <div style="background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); color: white; padding: 30px 20px; text-align: center;">
                    <div style="font-size: 28px; margin-bottom: 10px;">🛡️</div>
                    <h1 style="margin: 0; font-size: 24px; font-weight: 600;">NaviCamp Admin Portal</h1>
                    <p style="margin: 5px 0 0 0; opacity: 0.9; font-size: 14px;">Security Officer Management System</p>
                </div>
                
                <!-- Action Card -->
                <div style="padding: 30px;">
                    <div style="background: #f8f9fa; border-radius: 12px; padding: 25px; border-left: 4px solid {status_color}; margin-bottom: 25px;">
                        <div style="display: flex; align-items: center; margin-bottom: 15px;">
                            <span style="font-size: 24px; margin-right: 10px;">{action_icon}</span>
                            <h2 style="margin: 0; color: #333; font-size: 20px;">Officer {action_text.title()}</h2>
                        </div>
                        
                        <div style="background: white; border-radius: 8px; padding: 20px; margin: 15px 0;">
                            <h3 style="margin: 0 0 15px 0; color: #333; font-size: 16px;">Officer Information</h3>
                            <table style="width: 100%; border-collapse: collapse;">
                                <tr>
                                    <td style="padding: 8px 0; font-weight: 600; color: #666; width: 30%;">Name:</td>
                                    <td style="padding: 8px 0; color: #333;">{full_name}</td>
                                </tr>
                                <tr>
                                    <td style="padding: 8px 0; font-weight: 600; color: #666;">Officer ID:</td>
                                    <td style="padding: 8px 0; color: #333;">{user_id}</td>
                                </tr>
                                <tr>
                                    <td style="padding: 8px 0; font-weight: 600; color: #666;">Email:</td>
                                    <td style="padding: 8px 0; color: #333;">{user_email}</td>
                                </tr>
                                <tr>
                                    <td style="padding: 8px 0; font-weight: 600; color: #666;">Action Date:</td>
                                    <td style="padding: 8px 0; color: #333;">{timestamp}</td>
                                </tr>
                            </table>
                        </div>
                    </div>
                    
                    <!-- Summary Stats (Optional) -->
                    <div style="background: #f8f9fa; border-radius: 8px; padding: 20px; text-align: center;">
                        <p style="margin: 0; color: #666; font-size: 14px;">
                            This notification was automatically generated by the NaviCamp Security Officer Management System.
                        </p>
                    </div>
                </div>
                
                <!-- Footer -->
                <div style="background: #f8f9fa; padding: 20px; border-top: 1px solid #e9ecef; text-align: center; color: #666; font-size: 12px;">
                    <p style="margin: 0;">NaviCamp Admin Notification System</p>
                    <p style="margin: 5px 0 0 0;">Mapua Malayan Colleges Laguna (MMCL) | Administrative Portal</p>
                </div>
            </div>
        </body>
        </html>
        """

        text_part = MIMEText(f"""
        NaviCamp Admin Notification

        Security Officer {action_text.title()}

        Officer Information:
        - Name: {full_name}
        - Officer ID: {user_id}
        - Email: {user_email}
        - Action Date: {timestamp}

        This notification was automatically generated by the NaviCamp Security Officer Management System.
        """, "plain")

        html_part = MIMEText(html_body, "html")
        message.attach(text_part)
        message.attach(html_part)

        message["Subject"] = subject
        message["From"] = sender_email
        message["To"] = admin_email

        server = None
        try:
            server = smtplib.SMTP(smtp_server, smtp_port, timeout=CONFIG['SMTP_TIMEOUT'])
            server.starttls()
            server.login(sender_email, sender_password)
            server.sendmail(sender_email, admin_email, message.as_string())
            print(f"SUCCESS: Admin notification sent successfully to {admin_email}")
            return True
        finally:
            if server:
                try:
                    server.quit()
                except:
                    pass
                    
    except Exception as e:
        print(f"ERROR: Failed to send admin notification: {e}")
        return False

def lambda_handler(event, context):
    """Lambda handler for processing email requests from SQS."""
    print(f"INFO: Email Lambda execution started - Request ID: {context.aws_request_id}")
    
    try:
        # Get secrets
        secrets = get_secret()
        gmail_app_password = secrets.get('gmail_app_password')
        
        if not gmail_app_password:
            print("ERROR: Gmail app password not found in secrets")
            return {
                'statusCode': 500,
                'body': json.dumps('Missing email credentials')
            }
        
        # Process SQS records
        for record in event.get('Records', []):
            try:
                # Parse the message body
                message_body = json.loads(record['body'])
                
                full_name = message_body.get('full_name')
                user_id = message_body.get('user_id')
                action_type = message_body.get('action_type')
                recipient_email = message_body.get('recipient_email')
                
                if not all([full_name, user_id, action_type, recipient_email]):
                    print(f"ERROR: Missing required fields in message: {message_body}")
                    continue
                
                print(f"INFO: Processing email for {user_id} ({action_type}) to {recipient_email}")
                
                # Send the user notification email
                user_email_sent = send_notification_email(
                    full_name, 
                    user_id, 
                    action_type, 
                    gmail_app_password, 
                    recipient_email
                )
                
                # Send admin notification email
                admin_email_sent = send_admin_notification_email(
                    full_name,
                    user_id,
                    action_type,
                    recipient_email,
                    gmail_app_password
                )
                
                if user_email_sent:
                    print(f"SUCCESS: User email sent successfully for {user_id}")
                else:
                    print(f"ERROR: Failed to send user email for {user_id}")
                
                if admin_email_sent:
                    print(f"SUCCESS: Admin email sent successfully for {user_id}")
                else:
                    print(f"ERROR: Failed to send admin email for {user_id}")
                    # You might want to implement DLQ (Dead Letter Queue) here
                
            except json.JSONDecodeError as e:
                print(f"ERROR: Invalid JSON in SQS message: {e}")
            except Exception as e:
                print(f"ERROR: Error processing SQS record: {e}")
        
        return {
            'statusCode': 200,
            'body': json.dumps('Email processing complete')
        }
        
    except Exception as e:
        print(f"ERROR: Unexpected error in email Lambda: {e}")
        return {
            'statusCode': 500,
            'body': json.dumps(f'Error: {str(e)}')
        } 