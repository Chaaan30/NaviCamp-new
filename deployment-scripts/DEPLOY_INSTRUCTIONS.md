# FCM Lambda Function Deployment Instructions

## Quick Deployment (Recommended)

The updated `fcm_notification_lambda.py` file contains all the officer response notification functionality. To deploy it:

### Option 1: AWS CLI (Command Line)
```bash
# Navigate to your project directory
cd "C:\Users\crist\AndroidStudioProjects\NaviCamp"

# Create the deployment zip
powershell -Command "Compress-Archive -Path 'fcm_notification_lambda.py' -DestinationPath 'fcm_notification_final.zip' -Force"

# Deploy to AWS Lambda
aws lambda update-function-code --function-name NaviCampFCMNotification --zip-file fileb://fcm_notification_final.zip
```

### Option 2: AWS Web Console (GUI)
1. Open [AWS Lambda Console](https://ap-southeast-1.console.aws.amazon.com/lambda/home?region=ap-southeast-1#/functions)
2. Find and click on `NaviCampFCMNotification`
3. In the "Code" tab, click "Upload from" → ".zip file"
4. Create a zip file containing `fcm_notification_lambda.py` renamed to `lambda_function.py`
5. Upload the zip file
6. Click "Save"

### Option 3: Python Script (if boto3 works)
```bash
python direct_deploy.py
```

## What's New in This Deployment

The updated Lambda function now includes:

✅ **Complete Officer Response Notifications**
- When an officer responds to an assistance request, all OTHER security officers get notified
- Notifications show "✅ Assistance Claimed" with officer details
- Prevents duplicate notifications to the responding officer

✅ **Enhanced Database Functions**
- `get_other_security_officer_tokens()` - Gets FCM tokens for all officers except the responding one
- `handle_officer_response()` - Processes officer response notifications
- Better error handling and logging

✅ **Robust Authentication**
- Real RSA JWT signing using `python-jose` library
- Proper OAuth2 flow with Firebase HTTP v1 API
- Handles token refresh and error scenarios

## Testing the Complete System

1. **Assistance Request Flow** (Already Working):
   ```
   Student requests help → Officers get "🚨 Assistance Request" notification
   ```

2. **Officer Response Flow** (NEW):
   ```
   Officer responds → Other officers get "✅ Assistance Claimed" notification
   ```

## Verification Steps

After deployment, test with:
1. Student userID `20250006` requests assistance
2. Officer userID `20250001` responds to the request
3. Check that officer gets success message
4. Verify notifications appear on other officer devices

## Current System Status

- ✅ Android app with permission handling deployed
- ✅ Lambda function with real authentication ready
- ⏳ Final deployment needed for officer-to-officer notifications
- ✅ Database schema updated (alertID as VARCHAR(8))
- ✅ FCM tokens registered and verified

## Important Notes

- The Lambda layer `FinalNaviCampLayer` contains PyMySQL and python-jose
- Firebase project: `campus-navigator-ab838`
- Lambda handler: `lambda_function.lambda_handler`
- Region: `ap-southeast-1`

## If Deployment Fails

Check:
1. AWS CLI credentials are configured
2. Lambda function `NaviCampFCMNotification` exists
3. Lambda layer `FinalNaviCampLayer` is attached
4. Environment variable `FIREBASE_SERVICE_ACCOUNT_KEY` is set 