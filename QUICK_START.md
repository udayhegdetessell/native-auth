# Quick Start Guide

## 🚀 Get Started in 5 Minutes

### Step 1: Prerequisites
- Java 17+ installed
- Microsoft Entra External ID tenant created
- App registration with Native Auth enabled

### Step 2: Configure
Edit `src/main/resources/application.yml`:
```yaml
entra:
  external-id:
    tenant-subdomain: YOUR_TENANT_SUBDOMAIN
    tenant-id: YOUR_TENANT_ID
    client-id: YOUR_CLIENT_ID
    client-secret: YOUR_CLIENT_SECRET

jwt:
  secret: CHANGE_THIS_SECRET_IN_PRODUCTION
```

### Step 3: Run
```bash
./gradlew bootRun
```

### Step 4: Test
Open browser: `http://localhost:8080`

---

## 📝 Common API Calls

### Sign-Up (2 steps)

**Step 1: Start Sign-Up**
```bash
curl -X POST "http://localhost:8080/api/v1/hybrid-auth/signup/start?email=user@example.com&password=Pass123!"
```

**Step 2: Complete Sign-Up (with OTP from email)**
```bash
curl -X POST "http://localhost:8080/api/v1/hybrid-auth/signup/complete?continuationToken=TOKEN&otp=12345678&displayName=John%20Doe"
```

### Sign-In
```bash
curl -X POST http://localhost:8080/api/v1/hybrid-auth/signin \
  -H "Content-Type: application/json" \
  -d '{"email":"user@example.com","password":"Pass123!"}'
```

### Validate Token
```bash
curl -X POST http://localhost:8080/api/v1/hybrid-auth/validate \
  -H "Content-Type: application/json" \
  -d '{"token":"YOUR_JWT_TOKEN"}'
```

---

## 🔑 Key Endpoints

| Endpoint | Method | Purpose |
|----------|--------|---------|
| `/api/v1/hybrid-auth/signup/start` | POST | Start sign-up, send OTP |
| `/api/v1/hybrid-auth/signup/complete` | POST | Complete sign-up with OTP |
| `/api/v1/hybrid-auth/signin` | POST | Sign in with password |
| `/api/v1/hybrid-auth/validate` | POST | Validate JWT token |
| `/api/v1/hybrid-auth/refresh` | POST | Refresh JWT token |
| `/api/users` | POST | Create user (admin) |
| `/api/users/{email}` | GET | Get user details |
| `/api/users/{email}` | DELETE | Delete user |
| `/api/users/{email}/disable` | PATCH | Disable user |
| `/api/users/{email}/enable` | PATCH | Enable user |

---

## 🔐 Password Storage

**Where:** Microsoft Graph Open Extensions  
**Extension Name:** `com.tessell.auth`  
**Attribute:** `passwordHash`  
**Algorithm:** BCrypt (cost factor 12)  
**License Required:** ❌ No (works with basic Entra External ID)

---

## 🎯 Architecture Summary

```
Client → Spring Boot API → Entra Native Auth (OTP)
                        → Microsoft Graph API (Users)
                        → Open Extensions (Password Hash)
                        → Custom JWT Tokens (30 days)
```

---

## ⚠️ Important Notes

1. **Change JWT Secret** in production (`jwt.secret` in application.yml)
2. **OTP is 8 digits** (sent via email during sign-up)
3. **Token lifetime** is 30 days by default (configurable)
4. **No SharePoint license** required
5. **BCrypt cost factor** is 12 (secure but performant)

---

## 🐛 Troubleshooting

### Issue: "Tenant does not have a SPO license"
**Solution:** This is fixed! We use Open Extensions instead of `aboutMe` field.

### Issue: "Invalid OTP"
**Solution:** 
- Check email for 8-digit code
- OTP expires after a few minutes
- Make sure you're using the latest continuation token

### Issue: "User not found"
**Solution:**
- User must complete sign-up first
- Check email is correct
- Verify user exists in Entra portal

### Issue: "Invalid credentials"
**Solution:**
- Check password is correct
- Password is case-sensitive
- User account must be enabled

---

## 📚 More Information

See [README.md](README.md) for complete documentation.

---

**Happy Coding! 🎉**

