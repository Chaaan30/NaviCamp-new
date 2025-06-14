#!/usr/bin/env python3
import boto3
import zipfile
import os
import sys

def deploy_lambda():
    """Deploy the FCM notification Lambda function"""
    try:
        # Create a zip file with the Lambda function
        zip_filename = 'fcm_notification_lambda.zip'
        
        with zipfile.ZipFile(zip_filename, 'w', zipfile.ZIP_DEFLATED) as zipf:
            zipf.write('lambda_function.py', 'lambda_function.py')
        
        print(f"Created {zip_filename}")
        
        # Create Lambda client
        lambda_client = boto3.client('lambda', region_name='ap-southeast-1')
        
        # Read the zip file
        with open(zip_filename, 'rb') as zip_file:
            zip_bytes = zip_file.read()
        
        # Update the Lambda function
        response = lambda_client.update_function_code(
            FunctionName='NaviCampFCMNotification',
            ZipFile=zip_bytes
        )
        
        print("Lambda function updated successfully!")
        print(f"Function ARN: {response.get('FunctionArn')}")
        print(f"Last Modified: {response.get('LastModified')}")
        
        # Clean up
        os.remove(zip_filename)
        print(f"Cleaned up {zip_filename}")
        
        return True
        
    except Exception as e:
        print(f"Error deploying Lambda: {str(e)}")
        return False

if __name__ == "__main__":
    success = deploy_lambda()
    sys.exit(0 if success else 1) 