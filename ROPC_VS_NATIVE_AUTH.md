# 🔐 ROPC Flow vs Native Auth Flow

## Tessell IAM Service (Azure B2C ROPC) vs Native Auth POC (Entra Native Auth)

This document provides a **detailed technical comparison** between two password-based authentication approaches:

1. **Tessell IAM Service** - Uses Azure AD B2C with ROPC (Resource Owner Password Credentials) flow
2. **Native Auth POC** - Uses Microsoft Entra External ID with Native Authentication API

---

## 📊 Quick Comparison

| Aspect | Tessell IAM (ROPC) | Native Auth POC |
|--------|-------------------|-----------------|
| **Microsoft Service** | Azure AD B2C | Microsoft Entra External ID |
| **Auth Protocol** | OAuth 2.0 ROPC (deprecated) | Native Auth API (modern) |
| **Password Verification** | Azure B2C ROPC endpoint | BCrypt in application |
| **Password Storage** | Azure AD B2C (managed) | Graph Open Extensions (BCrypt hash) |
| **User Creation** | Admin-initiated | Self-service sign-up |
| **Email Verification** | Optional | Required (OTP) |
| **Token Issuer** | Azure AD B2C | Application (custom JWT) |
| **Token Type** | Azure B2C tokens (access + refresh + id) | Custom JWT |
| **Token Lifetime** | Azure B2C configured (~1 hour) | Application configured (30 days) |
| **Browser Redirect** | ❌ No (direct credentials) | ❌ No (direct credentials) |
| **Microsoft Recommendation** | ⚠️ Deprecated | ✅ Recommended for native apps |
| **Setup Complexity** | High (B2C tenant + ROPC flow) | Medium (Entra tenant + Native Auth) |
| **Password Policies** | ✅ Azure B2C policies | ❌ Application-level only |
| **MFA Support** | ✅ Azure B2C (if configured) | ❌ Not implemented |
| **Account Lockout** | ✅ Azure B2C built-in | ❌ Application must implement |

---

## 🏗️ Architecture Comparison

### Tessell IAM Service - ROPC Flow

```
┌─────────────────┐
│   Client App    │
└────────┬────────┘
         │ 1. POST /login
         │    email: user@example.com
         │    password: SecurePass123!
         ▼
┌─────────────────────────────────────────────────────┐
│         Tessell IAM Service                         │
│  ┌──────────────────────────────────────────────┐  │
│  │  TessellUserController                       │  │
│  │  - Validate tenant                           │  │
│  │  - Check user status (ACTIVE/INACTIVE)       │  │
│  │  - Check password policy compliance          │  │
│  └──────────────┬───────────────────────────────┘  │
│                 │                                   │
│  ┌──────────────▼───────────────────────────────┐  │
│  │  AzureADB2CManager                           │  │
│  │  - Build ROPC request                        │  │
│  │  - username: userId@{domain}.onmicrosoft.com │  │
│  └──────────────┬───────────────────────────────┘  │
└─────────────────┼───────────────────────────────────┘
                  │ 2. ROPC Token Request
                  ▼
┌─────────────────────────────────────────────────────┐
│         Azure AD B2C ROPC Endpoint                  │
│  https://{tenant}.b2clogin.com/{domain}/{policy}/   │
│         oauth2/v2.0/token                           │
│                                                     │
│  POST Parameters:                                   │
│  - grant_type: password                             │
│  - scope: openid {clientId} profile                 │
│  - client_id: {clientId}                            │
│  - username: {userId}@{domain}.onmicrosoft.com      │
│  - password: {password}                             │
│                                                     │
│  ✅ Password verified against Azure B2C            │
│  ✅ Returns access_token, id_token, refresh_token  │
└─────────────────┬───────────────────────────────────┘
                  │ 3. Azure B2C Response
                  ▼
┌─────────────────────────────────────────────────────┐
│         Tessell IAM Service                         │
│  ┌──────────────────────────────────────────────┐  │
│  │  AzureADB2CManager                           │  │
│  │  - If success: credentials valid             │  │
│  │  - If error: credentials invalid             │  │
│  └──────────────┬───────────────────────────────┘  │
│                 │                                   │
│  ┌──────────────▼───────────────────────────────┐  │
│  │  AuthTokenManager                            │  │
│  │  - Generate Tessell refresh token            │  │
│  │  - Generate Tessell access token             │  │
│  │  - Store in PostgreSQL                       │  │
│  └──────────────────────────────────────────────┘  │
└─────────────────┼───────────────────────────────────┘
                  │ 4. Return Tessell tokens
                  ▼
┌─────────────────┐
│   Client App    │
│  - refresh_token│
│  - access_token │
└─────────────────┘
```

### Native Auth POC - Native Auth Flow

```
┌─────────────────┐
│   Client App    │
└────────┬────────┘
         │ 1. POST /signup/start
         │    email: user@example.com
         │    password: SecurePass123!
         ▼
┌─────────────────────────────────────────────────────┐
│         Native Auth POC                             │
│  ┌──────────────────────────────────────────────┐  │
│  │  HybridAuthController                        │  │
│  └──────────────┬───────────────────────────────┘  │
│                 │                                   │
│  ┌──────────────▼───────────────────────────────┐  │
│  │  HybridAuthService                           │  │
│  │  - Cache password in memory                  │  │
│  └──────────────┬───────────────────────────────┘  │
└─────────────────┼───────────────────────────────────┘
                  │ 2. Native Auth Sign-Up Start
                  ▼
┌─────────────────────────────────────────────────────┐
│    Entra Native Auth API                            │
│  https://{tenant}.ciamlogin.com/{tenant}/           │
│         signup/v1.0/start                           │
│                                                     │
│  - Creates user in Entra External ID                │
│  - Sends 8-digit OTP to email                       │
│  - Returns continuation_token                       │
└─────────────────┬───────────────────────────────────┘
                  │ 3. OTP sent to email
                  ▼
┌─────────────────┐
│   User Email    │
│  OTP: 12345678  │
└────────┬────────┘
         │ 4. POST /signup/complete
         │    continuationToken: xxx
         │    otp: 12345678
         │    displayName: John Doe
         ▼
┌─────────────────────────────────────────────────────┐
│         Native Auth POC                             │
│  ┌──────────────────────────────────────────────┐  │
│  │  HybridAuthService                           │  │

## 🔍 Detailed Flow Breakdown

### 1. Tessell IAM Service - ROPC Authentication Flow

#### Step-by-Step Process

**1. User Login Request**
```http
POST /iam/users/login
Content-Type: application/json

{
  "emailId": "user@example.com",
  "password": "SecurePass123!",
  "tenantId": "tenant-uuid"
}
```

**2. Tessell IAM Service Processing**
- Validates tenant exists and is active
- Looks up user in PostgreSQL database
- Checks user status (must be `ACTIVE`)
- Checks auth type (must be `PASSWORD`)
- Validates password policy compliance

**3. Azure B2C ROPC Call**
```java
// AzureADB2CManager.java
private boolean loginInternal(
    AzureADB2CConfig b2cConfig,
    String username,
    String password) throws TessellException {

  ApiClient b2cApiClient = new ApiClient();
  AzureB2CApi b2CApi = new AzureB2CApi(
      b2cApiClient.setBasePath(b2cConfig.getB2CLoginPath())
  );

  try {
    // Call Azure B2C ROPC endpoint
    b2CApi.b2cLogin(
        b2cConfig.getB2CDomain(),              // {tenant}.onmicrosoft.com
        b2cConfig.getRopcFlowName(),           // ROPC user flow name
        "password",                             // grant_type
        String.format("openid %s profile",
            b2cConfig.getClientId()),          // scope
        "token id_token",                       // response_type
        b2cConfig.getClientId(),               // client_id
        username,                               // username (userId@domain)
        password                                // password
    );
    return true;  // Credentials valid
  }
  catch (Exception e) {
    if (e instanceof HttpClientErrorException e2
        && e2.getResponseBodyAsString().contains("access_denied")) {
      return false;  // Invalid credentials
    }
    throw new TessellException(e.getMessage());
  }
}
```

**4. Azure B2C ROPC Endpoint**
```
POST https://{tenant}.b2clogin.com/{domain}/{ropcFlowName}/oauth2/v2.0/token

Parameters:
- grant_type: password
- scope: openid {clientId} profile
- response_type: token id_token
- client_id: {clientId}
- username: {userId}@{domain}.onmicrosoft.com
- password: {userPassword}
```

**5. Azure B2C Response (Success)**
```json
{
  "access_token": "eyJ0eXAiOiJKV1QiLCJhbGc...",
  "token_type": "Bearer",
  "expires_in": 3600,
  "id_token": "eyJ0eXAiOiJKV1QiLCJhbGc...",
  "refresh_token": "eyJraWQiOiJjcGltY29yZV8w..."
}
```

**6. Azure B2C Response (Failure)**
```json
{
  "error": "access_denied",
  "error_description": "AADB2C90078: The user has entered an incorrect password."
}
```

**7. Tessell Token Generation**
- If Azure B2C returns success, credentials are valid
- Generate Tessell refresh token (long-lived, e.g., 30 days)
- Generate Tessell access token (short-lived, e.g., 1 hour)
- Store tokens in PostgreSQL
- Return tokens to client

**8. Client Response**
```json
{
  "refreshToken": "tessell-refresh-token-xxx",
  "accessToken": "tessell-access-token-yyy",
  "expiresIn": 3600
}
```

---

### 2. Native Auth POC - Sign-Up and Sign-In Flow

#### Sign-Up Flow (Step-by-Step)

**1. Sign-Up Start Request**
```http
POST /api/v1/hybrid-auth/signup/start
Content-Type: application/x-www-form-urlencoded

email=user@example.com&password=SecurePass123!
```

**2. Native Auth POC Processing**
```java
// HybridAuthServiceImpl.java
@Override
public String signUpStart(String email, String password) throws Exception {
    // Step 1: Start sign-up with Native Auth
    String url = config.getNativeAuthBaseUrl() + "/signup/v1.0/start";
    String formBody = String.format(
        "client_id=%s&challenge_type=oob%%20password%%20redirect&username=%s&password=%s",
        config.getClientId(), email, password
    );

    Request request = new Request.Builder()
        .url(url)
        .post(RequestBody.create(formBody, FORM_URL_ENCODED))
        .build();

    Response response = httpClient.newCall(request).execute();
    NativeAuthResponse authResponse = objectMapper.readValue(
        response.body().string(),
        NativeAuthResponse.class
    );

    // Step 2: Call challenge to trigger OTP email
    url = config.getNativeAuthBaseUrl() + "/signup/v1.0/challenge";
    formBody = String.format(
        "client_id=%s&continuation_token=%s&challenge_type=oob",
        config.getClientId(), authResponse.getContinuationToken()
    );

    // ... send challenge request ...

    // Cache password for later use
    signUpCache.put(continuationToken, new SignUpData(email, password));

    return continuationToken;
}
```

**3. Entra Native Auth API Call**
```
POST https://{tenant}.ciamlogin.com/{tenant}/signup/v1.0/start

Parameters:
- client_id: {clientId}
- challenge_type: oob password redirect
- username: user@example.com
- password: SecurePass123!
```

**4. Native Auth Response**
```json
{
  "continuation_token": "uY29tL2F1dGhlbnRpY2F0ZS9vb2IvdjEuMC...",
  "challenge_type": "oob",
  "challenge_channel": "email",
  "challenge_target_label": "u***@example.com"
}
```

**5. OTP Email Sent**
- Entra sends 8-digit OTP to user's email
- User receives email with OTP code

**6. Sign-Up Complete Request**
```http
POST /api/v1/hybrid-auth/signup/complete
Content-Type: application/x-www-form-urlencoded

continuationToken=xxx&otp=12345678&displayName=John Doe
```

**7. OTP Verification and User Creation**
```java
// HybridAuthServiceImpl.java
@Override
public TokenResponse signUpComplete(String continuationToken, String otp, String displayName)
    throws Exception {

    // Step 1: Submit OTP to Native Auth
    String url = config.getNativeAuthBaseUrl() + "/signup/v1.0/continue";
    String formBody = String.format(
        "client_id=%s&continuation_token=%s&grant_type=oob&oob=%s",
        config.getClientId(), continuationToken, otp
    );

    // ... verify OTP ...

    // Step 2: Submit displayName attribute
    // ... submit attributes ...

    // Step 3: Get tokens from Native Auth
    // ... get ID token ...

    // Step 4: Extract userId from ID token
    String userId = extractUserIdFromIdToken(idToken);

    // Step 5: Retrieve cached password
    SignUpData signUpData = signUpCache.remove(continuationToken);
    String password = signUpData.password;

    // Step 6: Hash password with BCrypt
    String passwordHash = BCrypt.hashpw(password, BCrypt.gensalt(12));

    // Step 7: Store password hash in Graph Open Extension
    graphApiService.updateUserExtensionAttribute(userId, "passwordHash", passwordHash);

    // Step 8: Generate custom JWT token
    String jwtToken = generateJwtToken(userId, displayName, email);

    return TokenResponse.builder()
        .accessToken(jwtToken)
        .tokenType("Bearer")
        .expiresIn(tokenLifetimeDays * 24 * 3600)
        .build();
}
```

**8. Password Hash Storage**
```
PATCH https://graph.microsoft.com/v1.0/users/{userId}/extensions/com.tessell.auth

{
  "passwordHash": "$2a$12$KIXxLVq9Z8YvWz7aXfj2O.abc123..."
}
```

**9. Custom JWT Generation**
```java
private String generateJwtToken(String userId, String displayName, String email) {
    Instant now = Instant.now();
    Instant expiry = now.plus(tokenLifetimeDays, ChronoUnit.DAYS);

    return JWT.create()
        .withIssuer("tessell-iam")
        .withSubject(userId)
        .withClaim("name", displayName)
        .withClaim("email", email)
        .withIssuedAt(Date.from(now))
        .withExpiresAt(Date.from(expiry))
        .sign(Algorithm.HMAC256(jwtSecret));
}
```

**10. Client Response**
```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "tokenType": "Bearer",
  "expiresIn": 2592000
}
```

---

#### Sign-In Flow (Step-by-Step)

**1. Sign-In Request**
```http
POST /api/v1/hybrid-auth/signin
Content-Type: application/json

{
  "email": "user@example.com",
  "password": "SecurePass123!"
}
```

**2. User Lookup via Graph API**
```java
// HybridAuthServiceImpl.java
@Override
public TokenResponse signIn(LoginRequest request) throws Exception {
    // Step 1: Look up user by email
    JsonNode user = graphApiService.getUserByEmail(request.getEmail());

    if (user == null) {
        throw new Exception("Invalid credentials");
    }

    String userId = user.get("id").asText();
    String displayName = user.has("displayName") ? user.get("displayName").asText() : "User";

    // Step 2: Retrieve password hash from Graph Open Extension
    String storedPasswordHash = graphApiService.getUserExtensionAttribute(userId, "passwordHash");

    if (storedPasswordHash == null || storedPasswordHash.isEmpty()) {
        throw new Exception("Invalid credentials");
    }

    // Step 3: Verify password using BCrypt
    if (!BCrypt.checkpw(request.getPassword(), storedPasswordHash)) {
        throw new Exception("Invalid credentials");
    }

    // Step 4: Generate custom JWT token
    String jwtToken = generateJwtToken(userId, displayName, request.getEmail());

    return TokenResponse.builder()
        .accessToken(jwtToken)
        .tokenType("Bearer")
        .expiresIn(tokenLifetimeDays * 24 * 3600)
        .build();
}
```

**3. Graph API User Lookup**
```
GET https://graph.microsoft.com/v1.0/users?$filter=mail eq 'user@example.com' or userPrincipalName eq 'user@example.com'
```

**4. Graph API Extension Retrieval**
```
GET https://graph.microsoft.com/v1.0/users/{userId}/extensions/com.tessell.auth
```

**5. BCrypt Password Verification**
```java
// In-memory verification (no network call)
boolean isValid = BCrypt.checkpw(plainPassword, storedHash);
```

**6. Client Response**
```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "tokenType": "Bearer",
  "expiresIn": 2592000
}
```

## 📋 Key Differences Summary

### Password Verification

| Aspect | Tessell IAM (ROPC) | Native Auth POC |
|--------|-------------------|-----------------|
| **Verification Method** | Azure B2C ROPC endpoint | BCrypt.checkpw() in application |
| **Password Storage** | Azure AD B2C (managed) | Graph Open Extensions (BCrypt hash) |
| **Hashing Algorithm** | Azure-managed (unknown) | BCrypt (cost factor 12) |
| **Password Policies** | Azure B2C policies | Application-level only |
| **Verification Endpoint** | `https://{tenant}.b2clogin.com/.../oauth2/v2.0/token` | Application logic (in-memory) |
| **Network Calls** | 1 call to Azure B2C | 2 calls to Graph API (user lookup + extension) |
| **Response Time** | ~200-500ms (Azure B2C) | ~100-300ms (Graph API + BCrypt) |
| **Brute Force Protection** | ✅ Azure B2C built-in | ❌ Must implement in application |
| **Account Lockout** | ✅ Azure B2C policies | ❌ Must implement in application |

---

### Token Management

| Aspect | Tessell IAM (ROPC) | Native Auth POC |
|--------|-------------------|-----------------|
| **Token Issuer** | Azure AD B2C | Application (custom JWT) |
| **Token Type** | Azure B2C tokens (access + refresh + id) | Custom JWT (access only) |
| **Token Lifetime** | Azure B2C configured (~1 hour access, ~30 days refresh) | Application configured (30 days default) |
| **Token Format** | Azure B2C JWT (complex claims) | Simple JWT (user info only) |
| **Token Validation** | Azure B2C public keys (RSA) | Application secret (HMAC-SHA256) |
| **Token Refresh** | Azure B2C refresh token endpoint | Application refresh logic |
| **Token Storage** | PostgreSQL database | Client-side only |
| **Token Revocation** | Database-based | Not implemented |

**Tessell IAM Token Claims (from Azure B2C):**
```json
{
  "iss": "https://{tenant}.b2clogin.com/{tenantId}/v2.0/",
  "exp": 1768217554,
  "nbf": 1768213954,
  "aud": "{clientId}",
  "sub": "{userId}",
  "name": "John Doe",
  "emails": ["user@example.com"],
  "tfp": "B2C_1_ROPC",
  "scp": "openid profile",
  "azp": "{clientId}",
  "ver": "1.0",
  "iat": 1768213954
}
```

**Native Auth POC Custom JWT Claims:**
```json
{
  "iss": "tessell-iam",
  "sub": "d0c7cbed-aed3-43c5-a1cd-0421d0b02bd1",
  "name": "John Doe",
  "email": "user@example.com",
  "iat": 1768213954,
  "exp": 1770805954
}
```

---

### User Onboarding

| Aspect | Tessell IAM (ROPC) | Native Auth POC |
|--------|-------------------|-----------------|
| **User Creation** | Admin creates in Azure B2C | Self-service via Native Auth |
| **Email Verification** | Optional (Azure B2C config) | Required (OTP) |
| **Password Setup** | Admin sets or user sets on first login | User sets during sign-up |
| **Approval Required** | Yes (admin must create user) | No (automatic) |
| **User Storage** | Azure AD B2C + PostgreSQL | Microsoft Entra External ID |
| **User Identifier** | `{userId}@{domain}.onmicrosoft.com` | Email address |

---

### Security Comparison

| Security Feature | Tessell IAM (ROPC) | Native Auth POC |
|------------------|-------------------|-----------------|
| **Password Exposure** | ⚠️ Application sees raw password | ⚠️ Application sees raw password |
| **Password Storage** | ✅ Azure-managed (secure) | ✅ BCrypt hash in Graph (secure) |
| **Brute Force Protection** | ✅ Azure B2C built-in | ❌ Application must implement |
| **Account Lockout** | ✅ Azure B2C policies | ❌ Application must implement |
| **Password Complexity** | ✅ Azure B2C policies | ❌ Application must implement |
| **Password Expiration** | ✅ Azure B2C policies | ❌ Application must implement |
| **MFA Support** | ✅ Azure B2C (if configured) | ❌ Not implemented |
| **Audit Logging** | ✅ Azure B2C logs | ❌ Application must implement |
| **OAuth 2.0 Compliance** | ✅ Yes (ROPC grant type) | ❌ Custom implementation |
| **Microsoft Recommendation** | ⚠️ Deprecated (not recommended) | ✅ Recommended for native apps |

---

### Configuration Complexity

**Tessell IAM Service (ROPC) Setup:**
1. Create Azure AD B2C tenant
2. Create ROPC user flow in Azure B2C
3. Configure password policies in B2C
4. Register application in B2C
5. Grant API permissions
6. Configure client secret
7. Set up PostgreSQL database
8. Configure Tessell IAM Service with B2C details
9. Configure external services (Tenant, Security, etc.)
10. Create users in Azure B2C (admin process)

**Native Auth POC Setup:**
1. Create Entra External ID tenant
2. Register application
3. Enable Native Authentication
4. Grant API permissions (User.ReadWrite.All)
5. Configure client secret
6. Configure application.yml with Entra details
7. Configure JWT secret
8. No database required
9. No external services required
10. Users self-register via API

---

## ✅ Pros and Cons

### Tessell IAM Service (ROPC Flow)

**✅ Pros:**
- **Azure-managed passwords** - Passwords stored securely in Azure AD B2C
- **Built-in security features** - Password policies, account lockout, brute force protection
- **MFA support** - Can enable MFA in Azure B2C
- **Centralized user management** - Azure AD B2C portal
- **OAuth 2.0 compliant** - Standard protocol (ROPC grant type)
- **Audit logging** - Azure B2C provides comprehensive logs
- **No password storage** - Application never stores passwords
- **Enterprise-ready** - Proven solution for enterprise applications

**❌ Cons:**
- **ROPC is deprecated** - Microsoft recommends against using ROPC
- **Security concerns** - Application handles raw passwords
- **Complex setup** - Requires Azure B2C tenant and ROPC user flow configuration
- **Admin-controlled** - Users cannot self-register
- **Azure B2C dependency** - Requires Azure B2C tenant and licensing
- **Limited flexibility** - Token lifetime controlled by Azure B2C
- **Higher cost** - Azure B2C licensing costs

---

### Native Auth POC (Native Auth Flow)

**✅ Pros:**
- **Modern approach** - Designed for native apps (not deprecated)
- **Self-service sign-up** - Users can register without admin
- **Built-in OTP verification** - Email verification included
- **Flexible token lifetime** - Full control over JWT token lifetime (30 days default)
- **No ROPC dependency** - Uses newer Native Auth API
- **Simpler setup** - No database required
- **Lower cost** - Entra External ID free tier available
- **Microsoft recommended** - Recommended for native apps

**❌ Cons:**
- **Hybrid approach** - Combines Native Auth (sign-up) + custom auth (sign-in)
- **Password stored separately** - Not in Azure, stored in Graph Open Extensions
- **Manual password verification** - Application handles password checking
- **Limited Azure features** - Cannot leverage Azure B2C password policies
- **Security features missing** - No brute force protection, account lockout, etc.
- **Not OAuth 2.0 compliant** - Custom implementation
- **Newer technology** - Less mature than Azure B2C
- **Application responsibility** - Must implement security features

---

## 🎯 When to Use Each Approach

### Use Tessell IAM Service (ROPC Flow) When:

✅ **You have existing Azure B2C infrastructure**
- Already using Azure AD B2C for other applications
- Have Azure B2C expertise in the team
- Azure B2C licensing already in place

✅ **You need enterprise-grade security features**
- Password policies (complexity, expiration, history)
- Account lockout after failed attempts
- Brute force protection
- MFA support
- Comprehensive audit logging

✅ **You need admin-controlled user provisioning**
- Users should not self-register
- Admin approval required for new users
- Centralized user management

✅ **You can tolerate ROPC deprecation warnings**
- Understand that ROPC is deprecated
- Plan to migrate to modern auth in the future
- Need password-based auth without browser redirect now

✅ **You need OAuth 2.0 compliance**
- Regulatory requirements for OAuth 2.0
- Integration with OAuth 2.0 clients

---

### Use Native Auth POC (Native Auth Flow) When:

✅ **You're building a new application**
- Starting from scratch
- No existing Azure B2C infrastructure
- Want to use modern Microsoft technologies

✅ **You need self-service sign-up**
- Users should be able to register themselves
- No admin approval required
- Email verification via OTP

✅ **You need flexible token lifetime**
- Want long-lived tokens (e.g., 30 days)
- Full control over token expiration
- Custom JWT claims

✅ **You want simpler infrastructure**
- No database required
- Fewer external services
- Cloud-based user storage

✅ **You're building a mobile/desktop app**
- Native Auth is designed for native apps
- No browser redirect required
- Microsoft recommended approach

⚠️ **BUT you must implement:**
- Password complexity validation
- Brute force protection
- Account lockout mechanism
- Audit logging
- Password expiration (if needed)

---

## 🔄 Migration Considerations

### From Tessell IAM (ROPC) to Native Auth POC

**Challenges:**

1. **Password Migration**
   - ROPC: Passwords in Azure B2C (cannot export)
   - Native Auth: Passwords in Graph Open Extensions (BCrypt hash)
   - **Solution**: Force password reset for all users

2. **Token Format Change**
   - ROPC: Azure B2C tokens (complex claims)
   - Native Auth: Custom JWT (simple claims)
   - **Solution**: Update client applications to handle new token format

3. **User Identifier Change**
   - ROPC: `{userId}@{domain}.onmicrosoft.com`
   - Native Auth: Email address
   - **Solution**: Update user lookup logic

4. **Feature Loss**
   - ROPC: Azure B2C password policies, MFA, account lockout
   - Native Auth: None of these features
   - **Solution**: Implement these features in application layer

5. **Database Migration**
   - ROPC: PostgreSQL database with user data
   - Native Auth: No database (cloud-based)
   - **Solution**: Export user data, migrate to Entra External ID

**Migration Steps:**

1. **Export user list from PostgreSQL**
   ```sql
   SELECT email_id, first_name, last_name, status
   FROM tessell_users
   WHERE auth_type = 'PASSWORD' AND status = 'ACTIVE';
   ```

2. **Create users in Entra External ID**
   - Use Graph API to create users
   - Set temporary passwords
   - Mark accounts for password reset

3. **Send password reset emails**
   - Notify users of migration
   - Provide sign-up link
   - Users complete sign-up via Native Auth

4. **Update client applications**
   - Change API endpoints
   - Update token handling logic
   - Test authentication flow

5. **Implement security features**
   - Add password complexity validation
   - Implement brute force protection
   - Add account lockout mechanism
   - Set up audit logging

6. **Decommission Azure B2C tenant**
   - Verify all users migrated
   - Archive Azure B2C data
   - Cancel Azure B2C subscription

---

### From Native Auth POC to Tessell IAM (ROPC)

**Challenges:**

1. **Password Migration**
   - Native Auth: BCrypt hashes in Graph Open Extensions
   - ROPC: Azure B2C managed passwords
   - **Solution**: Force password reset for all users

2. **Self-Service to Admin-Controlled**
   - Native Auth: Self-service sign-up
   - ROPC: Admin creates users
   - **Solution**: Implement admin user creation workflow

3. **Token Format Change**
   - Native Auth: Custom JWT
   - ROPC: Azure B2C tokens
   - **Solution**: Update client applications

4. **Database Setup**
   - Native Auth: No database
   - ROPC: PostgreSQL required
   - **Solution**: Set up PostgreSQL, create schema

**Migration Steps:**

1. **Set up Azure AD B2C tenant**
   - Create B2C tenant
   - Configure ROPC user flow
   - Set up password policies

2. **Set up PostgreSQL database**
   - Create database schema
   - Set up connection pooling
   - Configure backups

3. **Export users from Entra External ID**
   - Use Graph API to list users
   - Export user data (email, displayName, etc.)

4. **Create users in Azure B2C**
   - Admin creates users in B2C
   - Set temporary passwords
   - Import user data to PostgreSQL

5. **Send password reset emails**
   - Notify users of migration
   - Provide login link
   - Users set new passwords

6. **Update client applications**
   - Change API endpoints
   - Update token handling logic
   - Test authentication flow

7. **Decommission Entra External ID tenant**
   - Verify all users migrated
   - Archive Entra data
   - Cancel Entra subscription

---

## 📊 Performance Comparison

### Tessell IAM Service (ROPC)

**Login Flow Performance:**
```
1. Client → Tessell IAM: ~50ms (network)
2. Tessell IAM → PostgreSQL: ~10ms (user lookup)
3. Tessell IAM → Azure B2C: ~200-500ms (ROPC call)
4. Tessell IAM → PostgreSQL: ~10ms (token storage)
5. Tessell IAM → Client: ~50ms (network)

Total: ~320-620ms
```

**Bottleneck:** Azure B2C ROPC endpoint (200-500ms)

---

### Native Auth POC

**Sign-Up Flow Performance:**
```
1. Client → Native Auth POC: ~50ms (network)
2. Native Auth POC → Entra Native Auth: ~200-400ms (sign-up start)
3. Native Auth POC → Entra Native Auth: ~200-400ms (OTP challenge)
4. [User receives OTP email]
5. Client → Native Auth POC: ~50ms (network)
6. Native Auth POC → Entra Native Auth: ~200-400ms (OTP verify)
7. Native Auth POC → Entra Native Auth: ~200-400ms (attributes)
8. Native Auth POC → Entra Native Auth: ~200-400ms (get tokens)
9. Native Auth POC → Graph API: ~100-200ms (store password hash)
10. Native Auth POC → Client: ~50ms (network)

Total: ~1250-2450ms (excluding OTP email delivery)
```

**Sign-In Flow Performance:**
```
1. Client → Native Auth POC: ~50ms (network)
2. Native Auth POC → Graph API: ~100-200ms (user lookup)
3. Native Auth POC → Graph API: ~100-200ms (get password hash)
4. Native Auth POC → BCrypt: ~100-200ms (password verification)
5. Native Auth POC → Client: ~50ms (network)

Total: ~400-700ms
```

**Bottleneck:** Multiple Graph API calls (200-400ms total)

---

## 🔐 Security Best Practices

### For Tessell IAM Service (ROPC)

1. **Use HTTPS only** - Never send credentials over HTTP
2. **Implement rate limiting** - Prevent brute force attacks
3. **Log all authentication attempts** - Monitor for suspicious activity
4. **Use strong Azure B2C password policies** - Enforce complexity requirements
5. **Enable MFA** - Add second factor authentication
6. **Rotate client secrets** - Change secrets regularly
7. **Monitor Azure B2C logs** - Set up alerts for failed logins
8. **Plan migration** - ROPC is deprecated, plan to migrate

---

### For Native Auth POC

1. **Use HTTPS only** - Never send credentials over HTTP
2. **Implement rate limiting** - Prevent brute force attacks (CRITICAL)
3. **Implement account lockout** - Lock accounts after failed attempts (CRITICAL)
4. **Validate password complexity** - Enforce strong passwords (CRITICAL)
5. **Implement audit logging** - Log all authentication events (CRITICAL)
6. **Secure JWT secret** - Use strong secret, rotate regularly
7. **Implement token revocation** - Add ability to revoke tokens
8. **Monitor Graph API calls** - Set up alerts for suspicious activity
9. **Implement password expiration** - Force password changes periodically
10. **Add MFA support** - Consider adding second factor authentication

---

## 📝 Conclusion

### Tessell IAM Service (ROPC Flow)

**Summary:**
- ✅ Mature, feature-rich, Azure-managed security
- ✅ Enterprise-grade password policies and MFA
- ❌ Deprecated by Microsoft (ROPC)
- ❌ Complex setup and higher cost
- ❌ Limited flexibility (token lifetime)

**Best for:**
- Existing Azure B2C deployments
- Enterprise applications requiring robust security
- Admin-controlled user provisioning
- Applications that can tolerate ROPC deprecation

---

### Native Auth POC (Native Auth Flow)

**Summary:**
- ✅ Modern, Microsoft-recommended approach
- ✅ Self-service sign-up with OTP verification
- ✅ Flexible token lifetime (30 days default)
- ✅ Simpler setup, lower cost
- ❌ Missing security features (must implement)
- ❌ Not OAuth 2.0 compliant

**Best for:**
- New projects starting from scratch
- Mobile/desktop applications
- Self-service sign-up requirements
- POCs and MVPs
- Applications wanting full control over tokens

---

## 🚀 Final Recommendation

**For New Projects:**
- Use **Native Auth POC** approach
- Modern, not deprecated
- Add security features (password policy, brute force protection, account lockout)
- Plan to implement MFA in the future

**For Existing Azure B2C Deployments:**
- Continue with **Tessell IAM (ROPC)** for now
- Plan migration to modern auth (e.g., Authorization Code Flow with PKCE)
- Consider Native Auth as an alternative for new features

**For Production Enterprise Applications:**
- If starting fresh: **Native Auth** + implement all security features
- If existing: **Tessell IAM (ROPC)** + plan migration path
- Consider hybrid approach: Native Auth for sign-up, ROPC for sign-in

---

**Last Updated:** 2026-01-12
**Document Version:** 1.0
**Author:** Tessell Engineering Team
│  │  - Extension: com.tessell.auth               │  │
│  │  - Attribute: passwordHash                   │  │
│  └──────────────┬───────────────────────────────┘  │
│                 │                                   │
│  ┌──────────────▼───────────────────────────────┐  │
│  │  JWT Token Generator                         │  │
│  │  - Generate custom JWT (30-day lifetime)     │  │
│  └──────────────────────────────────────────────┘  │
└─────────────────┼───────────────────────────────────┘
                  │ 5. Return custom JWT
                  ▼
┌─────────────────┐
│   Client App    │
│  - access_token │
│    (JWT)        │
└─────────────────┘
```

---


