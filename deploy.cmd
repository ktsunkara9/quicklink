@echo off
echo ========================================
echo QuickLink - Build and Deploy Script
echo ========================================
echo.

echo [1/3] Building shaded JAR with Maven...
echo Please build the project in IntelliJ:
echo 1. Open Maven tool window (right side)
echo 2. Expand quicklink -^> Lifecycle
echo 3. Double-click 'clean'
echo 4. Double-click 'package'
echo 5. Wait for BUILD SUCCESS
echo.
echo Press any key after build completes...
pause >nul

if not exist "target\quicklink-1.0.0-aws.jar" (
    echo ERROR: Shaded JAR not found! Build may have failed.
    exit /b 1
)
echo Build successful!
echo.

echo [2/3] Deploying to AWS Lambda via CDK...
cd infrastructure
call cdk deploy --require-approval never
if %ERRORLEVEL% NEQ 0 (
    echo ERROR: CDK deployment failed!
    cd ..
    exit /b 1
)
cd ..
echo Deployment successful!
echo.

echo [3/3] Waiting for Lambda to be ready...
aws lambda wait function-updated --function-name quicklink-service
echo.

echo ========================================
echo Deployment Complete!
echo ========================================
echo.
echo Test your API:
echo 1. Get API URL:
echo    aws cloudformation describe-stacks --stack-name QuickLinkStack --query "Stacks[0].Outputs[?OutputKey=='ApiUrl'].OutputValue" --output text
echo.
echo 2. Test health endpoint:
echo    curl YOUR_API_URL/api/v1/health
echo.
echo 3. Test in Postman with the API URL
echo ========================================
