import json
import os
import pymysql
import boto3
from botocore.exceptions import ClientError
import base64
import smtplib
from email.mime.text import MIMEText
from email.mime.multipart import MIMEMultipart
from datetime import datetime # --- CHANGED: Import datetime to create unique timestamps ---

# Environment variables are correct
SECRET_NAME = os.environ.get('DB_SECRET_NAME') 
RDS_HOST = os.environ.get('DB_HOST') 
DB_NAME = os.environ.get('DB_NAME') 

def get_secret():
    """Retrieves all credentials from AWS Secrets Manager."""
    region_name = os.environ.get('AWS_REGION', "ap-southeast-1")
    session = boto3.session.Session()
    client = session.client(service_name='secretsmanager', region_name=region_name)
    try:
        get_secret_value_response = client.get_secret_value(SecretId=SECRET_NAME)
    except ClientError as e:
        print(f"Error retrieving secret: {e}")
        raise e
    else:
        if 'SecretString' in get_secret_value_response:
            return json.loads(get_secret_value_response['SecretString'])
        else:
            return json.loads(base64.b64decode(get_secret_value_response['SecretBinary']))

def send_notification_email(full_name, user_id, action_type, sender_password, recipient_email):
    """Send email notification to the user about their verification status using Gmail SMTP."""
    try:
        smtp_server = "smtp.gmail.com"
        smtp_port = 587
        sender_email = "navicamp.noreply@gmail.com"
        
        message = MIMEMultipart("alternative")
        
        # --- CHANGED: Create a unique timestamp to prevent Gmail from clipping the email ---
        unique_id = datetime.now().strftime("%Y%m%d%H%M%S%f")
        hidden_timestamp = f'<span style="display:none;font-size:0;color:transparent;">{unique_id}</span>'

        if action_type == "verified":
            subject = "NaviCamp Account Verified Successfully"
            html_body = f"""
            <html><body style="font-family: Arial, sans-serif; line-height: 1.6; color: #333;"><div style="max-width: 600px; margin: 0 auto; padding: 20px;"><div style="background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); color: white; padding: 20px; border-radius: 10px 10px 0 0;"><h2 style="margin: 0; text-align: center;">🧭 NaviCamp</h2></div><div style="background: #f8f9fa; padding: 30px; border-radius: 0 0 10px 10px; border: 1px solid #e9ecef;"><div style="background: #d4edda; border: 2px solid #c3e6cb; color: #155724; padding: 20px; border-radius: 8px; margin-bottom: 20px; text-align: center;"><h2 style="margin: 0 0 10px 0;">✅ Account Verification Successful</h2></div><p style="font-size: 16px;"><strong>Dear {full_name},</strong></p><p style="font-size: 16px;">Your NaviCamp account has been successfully verified!</p><div style="background: white; padding: 15px; border-radius: 5px; border-left: 4px solid #667eea; margin: 20px 0;"><p style="margin: 0; font-weight: bold;">User ID: {user_id}</p></div><p style="font-size: 16px;">You can now access all features of the NaviCamp application.</p><br><p style="font-size: 16px;">Best regards,<br><strong>NaviCamp</strong></p></div><div style="text-align: center; padding: 20px; color: #666; font-size: 12px;">This is an automated message from NaviCamp Verification System</div></div>{hidden_timestamp}</body></html>
            """ # --- CHANGED: Signature and added hidden timestamp
        else:  # rejected
            subject = "NaviCamp Account Verification Rejected"
            html_body = f"""
            <html><body style="font-family: Arial, sans-serif; line-height: 1.6; color: #333;"><div style="max-width: 600px; margin: 0 auto; padding: 20px;"><div style="background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); color: white; padding: 20px; border-radius: 10px 10px 0 0;"><h2 style="margin: 0; text-align: center;">🧭 NaviCamp</h2></div><div style="background: #f8f9fa; padding: 30px; border-radius: 0 0 10px 10px; border: 1px solid #e9ecef;"><div style="background: #f8d7da; border: 2px solid #f5c6cb; color: #721c24; padding: 20px; border-radius: 8px; margin-bottom: 20px; text-align: center;"><h2 style="margin: 0 0 10px 0;">❌ Account Verification Rejected</h2></div><p style="font-size: 16px;"><strong>Dear {full_name},</strong></p><p style="font-size: 16px;">Unfortunately, your NaviCamp account verification has been rejected.</p><div style="background: white; padding: 15px; border-radius: 5px; border-left: 4px solid #dc3545; margin: 20px 0;"><p style="margin: 0; font-weight: bold;">User ID: {user_id}</p></div><p style="font-size: 16px;">Please contact support for more information or to resubmit your verification documents.</p><br><p style="font-size: 16px;">Best regards,<br><strong>NaviCamp</strong></p></div><div style="text-align: center; padding: 20px; color: #666; font-size: 12px;">This is an automated message from NaviCamp Verification System</div></div>{hidden_timestamp}</body></html>
            """ # --- CHANGED: Signature and added hidden timestamp
        
        message["Subject"] = subject
        message["From"] = f"NaviCamp Admin <{sender_email}>"
        message["To"] = recipient_email
        html_part = MIMEText(html_body, "html")
        message.attach(html_part)
        
        print(f"Connecting to Gmail SMTP server...")
        server = smtplib.SMTP(smtp_server, smtp_port)
        server.starttls()
        print(f"Logging in to Gmail SMTP...")
        server.login(sender_email, sender_password)
        print(f"Sending email to {recipient_email}...")
        server.sendmail(sender_email, recipient_email, message.as_string())
        server.quit()
        print(f"Email sent successfully via Gmail SMTP to {recipient_email}")
        return True
        
    except Exception as e:
        print(f"Failed to send email notification via Gmail SMTP: {e}")
        return False

# --- CHANGED: Added countdown timer logic to the HTML and JavaScript ---
def create_response_page(message, success, email_sent):
    if success:
        bg_color, border_color, text_color, icon, title = "#d4edda", "#c3e6cb", "#155724", "✅", "Verification Complete"
    else:
        bg_color, border_color, text_color, icon, title = "#f8d7da", "#f5c6cb", "#721c24", "❌", "Action Processed"
    
    email_status = ""
    if email_sent:
        email_status = '<p class="email-status success">📧 Email notification has been sent to the user.</p>'
    elif message not in ["User not found in the database.", "User not found for deletion."]:
        email_status = '<p class="email-status warning">⚠️ Email notification could not be sent.</p>'
    
    html = f"""
    <!DOCTYPE html><html lang="en"><head><meta charset="UTF-8"><meta name="viewport" content="width=device-width, initial-scale=1.0"><title>{title} - NaviCamp</title><style>*{{margin:0;padding:0;box-sizing:border-box}}body{{font-family:'Segoe UI',Tahoma,Geneva,Verdana,sans-serif;background:linear-gradient(135deg,#667eea 0%,#764ba2 100%);min-height:100vh;display:flex;align-items:center;justify-content:center;padding:20px}}.container{{background:white;border-radius:15px;padding:40px;box-shadow:0 20px 40px rgba(0,0,0,0.1);max-width:500px;width:100%;text-align:center;position:relative;overflow:hidden}}.container::before{{content:'';position:absolute;top:0;left:0;right:0;height:5px;background:linear-gradient(90deg,#667eea,#764ba2)}}.logo{{font-size:24px;font-weight:bold;color:#333;margin-bottom:30px;display:flex;align-items:center;justify-content:center;gap:10px}}.status-icon{{font-size:48px;margin-bottom:20px}}.title{{font-size:28px;font-weight:600;color:#333;margin-bottom:20px}}.message{{background-color:{bg_color};border:2px solid {border_color};color:{text_color};padding:20px;border-radius:10px;margin-bottom:20px;font-size:16px;line-height:1.5;font-weight:500}}.email-status{{padding:15px;border-radius:8px;margin-bottom:20px;font-size:14px;font-weight:500}}.email-status.success{{background-color:#e8f5e8;color:#2d5016;border:1px solid #c3e6cb}}.email-status.warning{{background-color:#fff3cd;color:#856404;border:1px solid #ffeaa7}}.close-btn{{background:linear-gradient(135deg,#667eea 0%,#764ba2 100%);color:white;border:none;padding:12px 30px;border-radius:25px;font-size:16px;font-weight:600;cursor:pointer;transition:all 0.3s ease;text-decoration:none;display:inline-block}}.close-btn:hover{{transform:translateY(-2px);box-shadow:0 5px 15px rgba(102,126,234,0.3)}}.footer{{margin-top:30px;padding-top:20px;border-top:1px solid #eee;color:#666;font-size:12px}}#countdown{{margin-top:20px;font-size:14px;color:#555}}@media (max-width:480px){{.container{{padding:25px}}.title{{font-size:24px}}.message{{font-size:14px}}}}</style></head><body><div class="container"><div class="logo">🧭 NaviCamp Admin</div><div class="status-icon">{icon}</div><h1 class="title">{title}</h1><div class="message">{message}</div>{email_status}<button class="close-btn" onclick="window.close();">Close Window</button><p id="countdown"></p><div class="footer">NaviCamp User Verification System<br>Processed at {os.environ.get('AWS_REGION','ap-southeast-1')}</div></div><script>var timeleft=10;var countdownTimer=setInterval(function(){{if(timeleft<=0){{clearInterval(countdownTimer);window.close()}}else{{document.getElementById("countdown").innerHTML="This window will close in "+timeleft+" seconds..."}}timeleft-=1}},1000);</script></body></html>
    """
    return html

def lambda_handler(event, context):
    # This entire handler function remains unchanged from the last version.
    print(f"Received event: {json.dumps(event)}")
    try:
        params = event.get('queryStringParameters', {})
        user_id, action = params.get('userID'), params.get('action')

        if not all([user_id, action]):
            return {'statusCode': 400, 'headers': {'Content-Type': 'text/html'}, 'body': create_response_page("Missing userID or action parameter.", False, False)}

        action = action.lower()
        if action not in ['verify', 'reject']:
            return {'statusCode': 400, 'headers': {'Content-Type': 'text/html'}, 'body': create_response_page('Invalid action specified. Use "verify" or "reject".', False, False)}
        
        if not all([SECRET_NAME, RDS_HOST, DB_NAME]):
            return {'statusCode': 500, 'headers': {'Content-Type': 'text/html'}, 'body': create_response_page("Database environment variables not set.", False, False)}

        secrets = get_secret()
        db_username, db_password, gmail_app_password = secrets.get('username'), secrets.get('password'), secrets.get('gmail_app_password')
        
        if not all([db_username, db_password, gmail_app_password]):
            return {'statusCode': 500, 'headers': {'Content-Type': 'text/html'}, 'body': create_response_page("Could not parse all required credentials from secret.", False, False)}

        connection = None
        try:
            connection = pymysql.connect(host=RDS_HOST, user=db_username, password=db_password, database=DB_NAME, connect_timeout=30)
            message, page_is_success_themed, http_status_code, email_sent = "", False, 404, False

            with connection.cursor() as cursor:
                get_user_sql = "SELECT `fullName`, `email` FROM `user_table` WHERE `userID` = %s"
                cursor.execute(get_user_sql, (user_id,))
                user_result = cursor.fetchone()
                
                if not user_result:
                    message = f"User {user_id} not found in the database."
                else:
                    full_name, recipient_email = user_result[0], user_result[1]
                    
                    if action == 'verify':
                        sql = "UPDATE `user_table` SET `verified` = 1 WHERE `userID` = %s"
                        cursor.execute(sql, (user_id,))
                        connection.commit()
                        if cursor.rowcount > 0:
                            message, page_is_success_themed, http_status_code = f"{full_name} ({user_id}) proof has been successfully verified.", True, 200
                            email_sent = send_notification_email(full_name, user_id, "verified", gmail_app_password, recipient_email)
                        else:
                            message, http_status_code = f"User {full_name} ({user_id}) was not updated (perhaps already verified?).", 200
                    
                    elif action == 'reject':
                        sql = "DELETE FROM `user_table` WHERE `userID` = %s"
                        cursor.execute(sql, (user_id,))
                        connection.commit()
                        if cursor.rowcount > 0:
                            message, http_status_code = f"User {full_name} ({user_id}) has been successfully rejected and their record was deleted.", 200
                            email_sent = send_notification_email(full_name, user_id, "rejected", gmail_app_password, recipient_email)
                        else:
                            message = f"User {user_id} not found for deletion."
            
            return {'statusCode': http_status_code, 'headers': {'Content-Type': 'text/html'}, 'body': create_response_page(message, page_is_success_themed, email_sent)}

        except pymysql.MySQLError as e:
            return {'statusCode': 500, 'headers': {'Content-Type': 'text/html'}, 'body': create_response_page(f"A database operational error occurred: {e}", False, False)}
        finally:
            if connection: connection.close()

    except Exception as e:
        import traceback
        traceback.print_exc()
        return {'statusCode': 500, 'headers': {'Content-Type': 'text/html'}, 'body': create_response_page("An unexpected error occurred.", False, False)}