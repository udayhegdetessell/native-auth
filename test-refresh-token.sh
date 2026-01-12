#!/bin/bash

# Test script to demonstrate the complete workaround flow
# This shows how users can sign up and then use refresh tokens to stay signed in

echo "========================================="
echo "Microsoft Entra Native Auth - Workaround Demo"
echo "========================================="
echo ""

# Use a test email (you can change this)
TEST_EMAIL="testuser$(date +%s)@example.com"
TEST_PASSWORD="SecureP@ssw0rd2024!"
TEST_DISPLAY_NAME="Test User"

echo "📝 Step 1: Sign up a new user"
echo "Email: $TEST_EMAIL"
echo ""

# Start sign-up
echo "Starting sign-up..."
SIGNUP_RESPONSE=$(curl -s -X POST "http://localhost:8080/api/auth/signup/start?email=$TEST_EMAIL&password=$TEST_PASSWORD")
echo "Response: $SIGNUP_RESPONSE"
echo ""

# Extract continuation token
CONTINUATION_TOKEN=$(echo $SIGNUP_RESPONSE | grep -o '"continuationToken":"[^"]*"' | cut -d'"' -f4)

if [ -z "$CONTINUATION_TOKEN" ]; then
    echo "❌ Failed to get continuation token"
    echo "This is expected - you need to check your email for OTP"
    echo ""
    echo "To complete the flow manually:"
    echo "1. Check email for OTP code"
    echo "2. Run: curl -X POST \"http://localhost:8080/api/auth/signup/complete?continuationToken=TOKEN&otp=CODE&displayName=$TEST_DISPLAY_NAME\""
    echo "3. Save the refresh_token from the response"
    echo "4. Test refresh: curl -X POST \"http://localhost:8080/api/auth/refresh?refreshToken=YOUR_REFRESH_TOKEN\""
    exit 0
fi

echo "✅ Got continuation token: ${CONTINUATION_TOKEN:0:50}..."
echo ""
echo "📧 Check your email ($TEST_EMAIL) for the OTP code"
echo ""
echo "To complete sign-up, run:"
echo "curl -X POST \"http://localhost:8080/api/auth/signup/complete?continuationToken=$CONTINUATION_TOKEN&otp=YOUR_OTP&displayName=$TEST_DISPLAY_NAME\""
echo ""
echo "========================================="
echo "After completing sign-up, you'll receive:"
echo "- access_token (use for API calls)"
echo "- refresh_token (save securely)"
echo "- id_token (user info)"
echo ""
echo "Then test refresh token:"
echo "curl -X POST \"http://localhost:8080/api/auth/refresh?refreshToken=YOUR_REFRESH_TOKEN\""
echo "========================================="

