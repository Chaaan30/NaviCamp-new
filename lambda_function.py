import json
import os
import pymysql
import boto3
from botocore.exceptions import ClientError
import base64
import smtplib
from email.mime.text import MIMEText
from email.mime.multipart import MIMEMultipart
from datetime import datetime, timezone, timedelta
import traceback
import re
import time

# Configuration
CONFIG = {
    'EMAIL_RETRY_ATTEMPTS': 2,  # Reduced to prevent timeouts
    'EMAIL_RETRY_DELAY': 1,     # Reduced delay
    'SMTP_TIMEOUT': 8,          # SMTP connection timeout in seconds
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
        required_keys = ['username', 'password', 'gmail_app_password']
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

def send_notification_email(full_name, user_id, action_type, sender_password, recipient_email):
    """Send email notification with retry logic, timeout controls, and enhanced error handling."""
    email_start_time = time.time()
    max_email_time = 10  # Maximum 10 seconds for all email attempts
    
    for attempt in range(1, CONFIG['EMAIL_RETRY_ATTEMPTS'] + 1):
        # Check if we've exceeded the maximum email time
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
            
            # Force IPv4 resolution for SMTP to avoid VPC IPv6 issues
            import socket
            try:
                # Resolve Gmail SMTP to IPv4 address
                smtp_ip = socket.getaddrinfo(smtp_server, smtp_port, socket.AF_INET)[0][4][0]
                print(f"INFO: Resolved {smtp_server} to IPv4: {smtp_ip}")
                smtp_host = smtp_ip
            except Exception as e:
                print(f"WARNING: Could not resolve IPv4 for {smtp_server}, using hostname: {e}")
                smtp_host = smtp_server
            
            message = MIMEMultipart("alternative")
            
            # Create unique timestamp to prevent Gmail clipping (Philippines time: UTC+8)
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
                <div style="max-width: 600px; margin: 40px auto; background: white; border-radius: 15px; overflow: hidden; box-shadow: 0 10px 30px rgba(0,0,0,0.1);">
                    <!-- Header -->
                    <div style="background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); color: white; padding: 30px 20px; text-align: center;">
                        <div style="font-size: 32px; margin-bottom: 10px;">🧭</div>
                        <h1 style="margin: 0; font-size: 24px; font-weight: 600;">NaviCamp</h1>
                        <p style="margin: 5px 0 0 0; opacity: 0.9; font-size: 14px;">MMCL Security Officer Verification System</p>
                    </div>
                    
                    <!-- Status Banner -->
                    <div style="background: {status_bg}; border: 2px solid {status_border}; color: {status_text}; padding: 25px; text-align: center; margin: 0;">
                        <div style="font-size: 48px; margin-bottom: 15px;">{icon}</div>
                        <h2 style="margin: 0; font-size: 22px; font-weight: 600;">Officer {action_text.title()}</h2>
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
                            <p style="margin: 5px 0 0 0; color: #666;">Institution: <strong>MMCL</strong></p>
                            <p style="margin: 5px 0 0 0; color: #666;">Processed: {timestamp}</p>
                        </div>
                        
                        <p style="font-size: 16px; color: #555; line-height: 1.6; margin: 25px 0;">
                            {next_steps}
                        </p>
                        
                        <div style="text-align: center; margin: 35px 0;">
                            <a href="mailto:support@navicamp.edu" style="background: {status_color}; color: white; padding: 12px 30px; text-decoration: none; border-radius: 25px; font-weight: 600; display: inline-block;">Contact Support</a>
                        </div>
                        
                        <p style="font-size: 16px; color: #333; margin: 30px 0 0 0;">
                            Best regards,<br>
                            <strong>NaviCamp Security Administration Team</strong><br>
                            <em>Mapua Malayan Colleges Laguna (MMCL)</em>
                        </p>
                    </div>
                    
                    <!-- Footer -->
                    <div style="background: #f8f9fa; padding: 20px; text-align: center; border-top: 1px solid #dee2e6;">
                        <p style="margin: 0; font-size: 12px; color: #666;">
                            This is an automated message from the NaviCamp Security Officer Verification System.<br>
                            Mapua Malayan Colleges Laguna (MMCL) - Please do not reply to this email.
                        </p>
                    </div>
                </div>
                {hidden_timestamp}
            </body>
            </html>
            """
            
            message["Subject"] = subject
            message["From"] = f"NaviCamp Security Admin <{sender_email}>"
            message["To"] = recipient_email
            html_part = MIMEText(html_body, "html")
            message.attach(html_part)
            
            # Send email with timeout controls
            print(f"INFO: Connecting to Gmail SMTP server at {smtp_host}...")
            server = None
            try:
                # Create SMTP connection with timeout using IPv4 address
                server = smtplib.SMTP(smtp_host, smtp_port, timeout=CONFIG['SMTP_TIMEOUT'])
                print(f"INFO: Starting TLS encryption...")
                server.starttls()
                print(f"INFO: Authenticating with Gmail...")
                server.login(sender_email, sender_password)
                print(f"INFO: Sending email to {recipient_email}...")
                server.sendmail(sender_email, recipient_email, message.as_string())
                print(f"SUCCESS: Email sent successfully to {recipient_email}")
                return True
            finally:
                # Always close the connection
                if server:
                    try:
                        server.quit()
                    except:
                        pass  # Ignore errors when closing
            
        except smtplib.SMTPAuthenticationError as e:
            print(f"ERROR: SMTP authentication failed: {e}")
            return False  # Don't retry auth errors
        except smtplib.SMTPRecipientsRefused as e:
            print(f"ERROR: Invalid recipient email: {e}")
            return False  # Don't retry invalid emails
        except OSError as e:
            if e.errno == 97:  # Address family not supported
                print(f"WARNING: IPv6/IPv4 connectivity issue on attempt {attempt}: {e}")
                print(f"INFO: This is likely a VPC networking issue. Check NAT Gateway routes and DNS resolution.")
                if attempt < CONFIG['EMAIL_RETRY_ATTEMPTS']:
                    print(f"INFO: Retrying in {CONFIG['EMAIL_RETRY_DELAY']} seconds...")
                    time.sleep(CONFIG['EMAIL_RETRY_DELAY'])
                else:
                    print(f"ERROR: Email failed after {CONFIG['EMAIL_RETRY_ATTEMPTS']} attempts - VPC networking issue")
                    print(f"ERROR: Consider checking: 1) NAT Gateway routes 2) Security Group rules 3) DNS resolution")
                    return False
            else:
                print(f"WARNING: Email attempt {attempt} failed: {e}")
                if attempt < CONFIG['EMAIL_RETRY_ATTEMPTS']:
                    print(f"INFO: Retrying in {CONFIG['EMAIL_RETRY_DELAY']} seconds...")
                    time.sleep(CONFIG['EMAIL_RETRY_DELAY'])
                else:
                    print(f"ERROR: Email failed after {CONFIG['EMAIL_RETRY_ATTEMPTS']} attempts")
                    return False
        except (smtplib.SMTPException, ConnectionError, TimeoutError) as e:
            print(f"WARNING: Email attempt {attempt} failed: {e}")
            if attempt < CONFIG['EMAIL_RETRY_ATTEMPTS']:
                print(f"INFO: Retrying in {CONFIG['EMAIL_RETRY_DELAY']} seconds...")
                time.sleep(CONFIG['EMAIL_RETRY_DELAY'])
            else:
                print(f"ERROR: Email failed after {CONFIG['EMAIL_RETRY_ATTEMPTS']} attempts")
                return False
        except Exception as e:
            print(f"ERROR: Unexpected email error: {e}")
            return False
    
    return False

def create_response_page(message, success, email_sent, user_details=None):
    """Create enhanced HTML response page with better styling and information."""
    if success:
        bg_color, border_color, text_color, icon, title = "#d4edda", "#c3e6cb", "#155724", "✅", "Action Completed Successfully"
        gradient = "linear-gradient(135deg, #28a745 0%, #20c997 100%)"
    else:
        bg_color, border_color, text_color, icon, title = "#f8d7da", "#f5c6cb", "#721c24", "❌", "Action Failed"
        gradient = "linear-gradient(135deg, #dc3545 0%, #fd7e14 100%)"
    
    # Email status indicator
    email_status = ""
    if email_sent:
        email_status = '''
        <div class="email-status success">
            <div class="email-icon">📧</div>
            <div>
                <strong>Email Notification Sent</strong>
                <p>The security officer has been notified via email about this action.</p>
            </div>
        </div>'''
    elif not success:
        email_status = '''
        <div class="email-status warning">
            <div class="email-icon">⚠️</div>
            <div>
                <strong>Email Notification Failed</strong>
                <p>The action was processed but email notification could not be sent.</p>
            </div>
        </div>'''
    
    # User details section
    user_info = ""
    if user_details:
        manila_tz = timezone(timedelta(hours=8))  # Philippines time: UTC+8
        manila_now = datetime.now(manila_tz)
        timestamp = manila_now.strftime("%B %d, %Y at %I:%M %p PHT")
        user_info = f'''
        <div class="user-details">
            <h3>Action Details</h3>
            <div class="detail-row">
                <span class="label">Officer ID:</span>
                <span class="value">{user_details.get('user_id', 'N/A')}</span>
            </div>
            <div class="detail-row">
                <span class="label">Full Name:</span>
                <span class="value">{user_details.get('full_name', 'N/A')}</span>
            </div>
            <div class="detail-row">
                <span class="label">Action:</span>
                <span class="value">{user_details.get('action', 'N/A').title()}</span>
            </div>
            <div class="detail-row">
                <span class="label">Processed:</span>
                <span class="value">{timestamp}</span>
            </div>
        </div>'''
    
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
                font-size: 28px;
                font-weight: bold;
                color: #333;
                margin-bottom: 30px;
                display: flex;
                align-items: center;
                justify-content: center;
                gap: 12px;
            }}
            
            .status-icon {{
                font-size: 64px;
                margin-bottom: 20px;
                animation: bounce 1s ease-in-out;
            }}
            
            @keyframes bounce {{
                0%, 100% {{ transform: translateY(0); }}
                50% {{ transform: translateY(-10px); }}
            }}
            
            .title {{
                font-size: 32px;
                font-weight: 700;
                color: #333;
                margin-bottom: 25px;
                line-height: 1.2;
            }}
            
            .message {{
                background-color: {bg_color};
                border: 2px solid {border_color};
                color: {text_color};
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
            let countdownTimer = null;
            let isPaused = false;
            const countdownElement = document.getElementById('countdown');
            
            function updateCountdown() {{
                if (isPaused) return; // Don't update if paused
                
                if (timeLeft <= 0) {{
                    countdownElement.innerHTML = '🔄 Window closing...';
                    clearInterval(countdownTimer);
                    setTimeout(() => window.close(), 500);
                }} else {{
                    countdownElement.innerHTML = `⏱️ This window will automatically close in ${{timeLeft}} seconds...`;
                    timeLeft--;
                }}
            }}
            
            function startCountdown() {{
                if (countdownTimer) clearInterval(countdownTimer); // Clear any existing timer
                countdownTimer = setInterval(updateCountdown, 1000);
                isPaused = false;
            }}
            
            function pauseCountdown() {{
                if (countdownTimer) {{
                    clearInterval(countdownTimer);
                    countdownTimer = null;
                }}
                isPaused = true;
                countdownElement.innerHTML = '⏸️ Auto-close paused (mouse over window)';
            }}
            
            function resumeCountdown() {{
                if (!isPaused) return; // Already running
                isPaused = false;
                timeLeft = {CONFIG['AUTO_CLOSE_DELAY']}; // Reset timer
                updateCountdown(); // Update immediately
                startCountdown(); // Start the interval
            }}
            
            // Initialize countdown
            updateCountdown();
            startCountdown();
            
            // Pause/resume countdown on hover
            document.querySelector('.container').addEventListener('mouseenter', pauseCountdown);
            document.querySelector('.container').addEventListener('mouseleave', resumeCountdown);
            
            // Also pause when clicking anywhere in the container
            document.querySelector('.container').addEventListener('click', () => {{
                if (!isPaused) {{
                    pauseCountdown();
                    setTimeout(() => {{
                        if (isPaused) {{ // Only resume if still paused after 3 seconds
                            countdownElement.innerHTML = '⏱️ Click detected - countdown will resume in 3 seconds...';
                            setTimeout(resumeCountdown, 3000);
                        }}
                    }}, 100);
                }}
            }});
        </script>
    </body>
    </html>
    """
    return html

def check_user_status(cursor, user_id):
    """Check current user status and return detailed information including proof picture."""
    try:
        query = "SELECT userID, fullName, email, verified, proofPicture FROM user_table WHERE userID = %s"
        cursor.execute(query, (user_id,))
        result = cursor.fetchone()
        
        if result:
            return {
                'exists': True,
                'user_id': result[0],
                'full_name': result[1],
                'email': result[2],
                'verified': bool(result[3]),
                'proof_picture': result[4] if result[4] else None,
                'status': 'verified' if result[3] else 'unverified'
            }
        else:
            return {
                'exists': False,
                'status': 'not_found'
            }
    except Exception as e:
        print(f"ERROR: Failed to check user status: {e}")
        raise e

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
    """Enhanced Lambda handler with comprehensive error handling and duplicate prevention."""
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
        gmail_app_password = secrets.get('gmail_app_password')
        
        if not all([db_username, db_password, gmail_app_password]):
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
                        
                        # Send notification email
                        email_sent = send_notification_email(
                            user_status['full_name'], 
                            user_id, 
                            "verified", 
                            gmail_app_password, 
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
                        
                        # Send notification email
                        email_sent = send_notification_email(
                            user_status['full_name'], 
                            user_id, 
                            "rejected", 
                            gmail_app_password, 
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