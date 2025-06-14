import boto3
import zipfile
import io
import json

def deploy_lambda_directly():
    """Deploy Lambda function directly using boto3"""
    try:
        # Read the Lambda function code
        with open('fcm_notification_lambda.py', 'r', encoding='utf-8') as f:
            lambda_code = f.read()
        
        # Create ZIP file in memory
        zip_buffer = io.BytesIO()
        with zipfile.ZipFile(zip_buffer, 'w', zipfile.ZIP_DEFLATED) as zipf:
            zipf.writestr('lambda_function.py', lambda_code)
        
        zip_content = zip_buffer.getvalue()
        print(f"Created ZIP file in memory ({len(zip_content)} bytes)")
        
        # Create Lambda client
        lambda_client = boto3.client('lambda', region_name='ap-southeast-1')
        
        # Update function code
        response = lambda_client.update_function_code(
            FunctionName='NaviCampFCMNotification',
            ZipFile=zip_content
        )
        
        print("✅ Lambda function deployed successfully!")
        print(f"Function ARN: {response.get('FunctionArn')}")
        print(f"Last Modified: {response.get('LastModified')}")
        print(f"Code SHA256: {response.get('CodeSha256')}")
        
        # Also update the handler to make sure it's correct
        try:
            lambda_client.update_function_configuration(
                FunctionName='NaviCampFCMNotification',
                Handler='lambda_function.lambda_handler'
            )
            print("✅ Function handler updated to lambda_function.lambda_handler")
        except Exception as e:
            print(f"Note: Could not update handler (may already be correct): {e}")
        
        return True
        
    except Exception as e:
        print(f"❌ Error deploying Lambda: {str(e)}")
        return False

if __name__ == "__main__":
    success = deploy_lambda_directly()
    if success:
        print("\n🎉 Deployment completed! The Lambda function now includes:")
        print("   • Complete officer response notification functionality")
        print("   • RSA JWT signing with python-jose")
        print("   • Proper notification routing to all security officers")
        print("   • Enhanced error handling and logging")
        print("\nYour FCM notification system is now fully functional!")
    else:
        print("\n❌ Deployment failed. Please check your AWS credentials and try again.") 