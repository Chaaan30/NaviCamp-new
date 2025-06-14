# PowerShell Deployment Script for NaviCamp FCM Lambda
# Run this script in PowerShell as Administrator

Write-Host "🚀 Deploying NaviCamp FCM Lambda Function..." -ForegroundColor Green

# Set the working directory
Set-Location "C:\Users\crist\AndroidStudioProjects\NaviCamp"

try {
    # Create the deployment zip file
    Write-Host "📦 Creating deployment package..." -ForegroundColor Yellow
    
    # Remove existing zip if it exists
    if (Test-Path "fcm_notification_final.zip") {
        Remove-Item "fcm_notification_final.zip" -Force
    }
    
    # Create temporary directory
    $tempDir = "temp_lambda_deploy"
    if (Test-Path $tempDir) {
        Remove-Item $tempDir -Recurse -Force
    }
    New-Item -ItemType Directory -Path $tempDir
    
    # Copy and rename the Lambda function
    Copy-Item "fcm_notification_lambda.py" "$tempDir/lambda_function.py"
    
    # Create zip file
    Compress-Archive -Path "$tempDir/*" -DestinationPath "fcm_notification_final.zip" -Force
    
    # Clean up temp directory
    Remove-Item $tempDir -Recurse -Force
    
    Write-Host "✅ Deployment package created: fcm_notification_final.zip" -ForegroundColor Green
    
    # Deploy to AWS Lambda
    Write-Host "🚀 Deploying to AWS Lambda..." -ForegroundColor Yellow
    
    $deployResult = aws lambda update-function-code --function-name NaviCampFCMNotification --zip-file fileb://fcm_notification_final.zip
    
    if ($LASTEXITCODE -eq 0) {
        Write-Host "✅ Lambda function deployed successfully!" -ForegroundColor Green
        Write-Host "🎉 FCM notification system is now fully functional!" -ForegroundColor Cyan
        Write-Host ""
        Write-Host "✅ Features now active:" -ForegroundColor Green
        Write-Host "   • Complete officer response notifications" -ForegroundColor White
        Write-Host "   • RSA JWT authentication with Firebase" -ForegroundColor White
        Write-Host "   • Enhanced error handling and logging" -ForegroundColor White
        Write-Host "   • Notification routing to all security officers" -ForegroundColor White
        Write-Host ""
        Write-Host "🧪 Test the system:" -ForegroundColor Yellow
        Write-Host "   1. Student requests assistance" -ForegroundColor White
        Write-Host "   2. Officer responds to request" -ForegroundColor White
        Write-Host "   3. Other officers should receive '✅ Assistance Claimed' notification" -ForegroundColor White
    } else {
        Write-Host "❌ Deployment failed!" -ForegroundColor Red
        Write-Host "Please check your AWS credentials and Lambda function name." -ForegroundColor Yellow
    }
    
} catch {
    Write-Host "❌ Error during deployment: $($_.Exception.Message)" -ForegroundColor Red
    Write-Host "Please check the error above and try again." -ForegroundColor Yellow
}

Write-Host ""
Write-Host "Press any key to exit..." -ForegroundColor Gray
$null = $Host.UI.RawUI.ReadKey("NoEcho,IncludeKeyDown") 