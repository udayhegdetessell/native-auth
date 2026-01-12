# 🔄 Authentication Flow Comparison

## Tessell IAM Service vs Native Auth POC

This document compares the authentication flows between the **Tessell IAM Service** (production system) and the **Native Auth POC** (Microsoft Entra External ID implementation).

---

## 📊 High-Level Comparison

| Aspect | Tessell IAM Service | Native Auth POC |
|--------|---------------------|-----------------|
| **Architecture** | Microservices with PostgreSQL | Stateless with Microsoft Graph API |
| **User Storage** | PostgreSQL Database | Microsoft Entra External ID |
| **Password Storage** | External User Management System | Microsoft Graph Open Extensions (BCrypt) |
| **Authentication** | Multiple auth types (Password, Google, SSO) | Hybrid (OTP + Password) |
| **Token Management** | Refresh + Access tokens | Custom JWT tokens |
| **OTP Verification** | Custom OTP service | Entra Native Auth API |
| **MFA Support** | ✅ Yes (TOTP-based) | ❌ Not implemented |
| **SSO Support** | ✅ Yes (SAML, IdP) | ❌ Not implemented |
| **Multi-Tenancy** | ✅ Full support | ❌ Single tenant |
| **Database** | PostgreSQL | None (Cloud-based) |
| **License Requirements** | Self-hosted | Microsoft Entra External ID |

---

## 🏗️ Architecture Comparison

### Tessell IAM Service Architecture

```
┌─────────────────┐
│   Client App    │
└────────┬────────┘
         │
         ▼
┌─────────────────────────────────────────────────────┐
│         Tessell IAM Service (Spring Boot)           │
│  ┌──────────────────────────────────────────────┐  │
│  │      TessellUserController                   │  │
│  │  /iam/users/*                                │  │
│  └──────────────┬───────────────────────────────┘  │
│                 │                                   │
│  ┌──────────────▼───────────────────────────────┐  │
│  │      TessellUserService                      │  │
│  │  - Login (Password/Google/SSO)               │  │
│  │  - User management                           │  │
│  │  - MFA verification                          │  │
│  │  - Password policy enforcement               │  │
│  └──────────────┬───────────────────────────────┘  │
│                 │                                   │
│  ┌──────────────▼───────────────────────────────┐  │
│  │  TessellExternalUserMgmtService              │  │
│  │  - Password verification                     │  │
│  │  - User CRUD operations                      │  │
│  └──────────────┬───────────────────────────────┘  │
│                 │                                   │
│  ┌──────────────▼───────────────────────────────┐  │
│  │      AuthTokenManager                        │  │
│  │  - Refresh token generation                  │  │
│  │  - Access token generation                   │  │
│  └──────────────────────────────────────────────┘  │
└─────────────────┼───────────────────────────────────┘
                  │
         ┌────────┴────────┬──────────────┬──────────┐
         ▼                 ▼              ▼          ▼
┌──────────────┐  ┌──────────────┐  ┌─────────┐  ┌──────────┐
│  PostgreSQL  │  │   Tenant     │  │Security │  │Governance│
│   Database   │  │   Service    │  │ Service │  │ Service  │
└──────────────┘  └──────────────┘  └─────────┘  └──────────┘
```

### Native Auth POC Architecture

```
┌─────────────────┐
│   Client App    │
└────────┬────────┘
         │
         ▼
┌─────────────────────────────────────────────────────┐
│      Native Auth POC (Spring Boot)                  │
│  ┌──────────────────────────────────────────────┐  │
│  │      HybridAuthController                    │  │
│  │  /api/v1/hybrid-auth/*                       │  │
│  └──────────────┬───────────────────────────────┘  │
│                 │                                   │
│  ┌──────────────▼───────────────────────────────┐  │
│  │      HybridAuthService                       │  │
│  │  - Sign-up (OTP-based)                       │  │
│  │  - Sign-in (Password-based)                  │  │
│  │  - Custom JWT generation                     │  │
│  └──────────────┬───────────────────────────────┘  │
│                 │                                   │
│  ┌──────────────▼───────────────────────────────┐  │
│  │      GraphApiService                         │  │
│  │  - User CRUD via Graph API                   │  │
│  │  - Password hash storage (Open Extensions)   │  │
│  └──────────────────────────────────────────────┘  │
└─────────────────┼───────────────────────────────────┘
                  │
         ┌────────┴────────┐
         ▼                 ▼
┌──────────────────┐  ┌──────────────────┐
│ Entra Native Auth│  │ Microsoft Graph  │
│   (OTP Service)  │  │   API (Users)    │
└──────────────────┘  └──────────────────┘
```

---



## 🔐 Authentication Flow Comparison

### 1. Sign-Up Flow

#### Tessell IAM Service - Invite User Flow

**Process:**
1. **Admin invites user** via API
2. User record created in **PostgreSQL** with `INVITED` status
3. **Invitation email** sent with secure link
4. User clicks link and **sets password**
5. Password **validated against policy** (min length, complexity, etc.)
6. Password stored in **external user management system**
7. User status updated to `ACTIVE`
8. **Refresh token** (long-lived) and **access token** (short-lived) generated
9. User can now login

**Database Schema:**
```sql
CREATE TABLE TESSELL_USERS (
    ID UUID PRIMARY KEY,
    FIRST_NAME CITEXT,
    LAST_NAME CITEXT,
    INTERNAL_ID CITEXT NOT NULL,
    EMAIL_ID CITEXT UNIQUE,
    AUTH_TYPE TESSELL_AUTH_TYPE NOT NULL,  -- 'PASSWORD' or 'GOOGLE'
    STATUS TESSELL_USER_STATUS NOT NULL,    -- 'INVITED', 'ACTIVE', 'INACTIVE'
    DATE_CREATED TIMESTAMPTZ(0),
    DATE_MODIFIED TIMESTAMPTZ(0),
    CREATED_BY CITEXT NOT NULL
);
```

---

#### Native Auth POC - Self-Service Sign-Up Flow

**Process:**
1. User initiates sign-up with **email and password**
2. Password **temporarily cached** in memory (ConcurrentHashMap)
3. **Entra Native Auth** sends **8-digit OTP** to email
4. User receives OTP and submits with continuation token
5. Native Auth **verifies OTP** and creates user
6. Password **hashed with BCrypt** (cost factor 12)
7. Password hash stored in **Microsoft Graph Open Extension** (`com.tessell.auth`)
8. **Custom JWT token** generated (configurable lifetime, default 30 days)
9. User receives JWT token for authentication

**Password Storage:**
```json
{
  "@odata.type": "microsoft.graph.openTypeExtension",
  "extensionName": "com.tessell.auth",
  "passwordHash": "$2a$12$HrJtczkoQQOv6qa96JOXAO3ezlHRP7TwbnH5qvgZE9R/D8QFOP2sy"
}
```

---

### 2. Sign-In Flow

#### Tessell IAM Service - Login Flow

**Process:**
1. User submits **email, password, authType**
2. **Tenant validation** - Check user belongs to tenant
3. **User lookup** in PostgreSQL database
4. **User status validation** (not INACTIVE/DELETED)
5. **Password policy** retrieved from Security Service
6. **Invalid login attempts** checked (account lockout)
7. **Password verification** via External User Management
8. **Password expiration** check
9. **Force password reset** check
10. **MFA verification** (if enabled)
11. Reset invalid login attempts to 0
12. Load **user tenant attributes** (personas, apps, subscriptions)
13. Generate **refresh token** (long-lived)
14. Generate **access token** (short-lived, tenant-specific)
15. Return tokens + user details

**Key Features:**
- ✅ Multi-tenant support
- ✅ Password policy enforcement
- ✅ Account lockout after max invalid attempts
- ✅ Password expiration check
- ✅ Force password reset support
- ✅ MFA support (TOTP-based)
- ✅ Multiple auth types (PASSWORD, GOOGLE, SSO)
- ✅ Refresh token + Access token (separate lifetimes)
- ✅ Tenant-specific access tokens

**Token Types:**
1. **Refresh Token**: Long-lived, used to obtain new access tokens
2. **Access Token**: Short-lived, tenant-specific, contains user permissions

---

#### Native Auth POC - Sign-In Flow

**Process:**
1. User submits **email and password**
2. **User lookup** via Microsoft Graph API (filter by email)
3. **Retrieve password hash** from Open Extension (`com.tessell.auth`)
4. **Verify password** with BCrypt.checkpw()
5. If valid: Generate **custom JWT token** (30-day expiration)
6. If invalid: Return **401 Unauthorized**

**Key Features:**
- ✅ Simple password-based authentication
- ✅ BCrypt password verification
- ✅ Custom JWT tokens (configurable lifetime)
- ✅ No database required
- ❌ No MFA support
- ❌ No password policy enforcement
- ❌ No account lockout
- ❌ No multi-tenant support
- ❌ Single token type (JWT)

**Token Structure:**
```json
{
  "iss": "tessell-iam",
  "sub": "d0c7cbed-aed3-43c5-a1cd-0421d0b02bd1",
  "name": "Uday Hegde",
  "email": "iamudayhegde@gmail.com",
  "iat": 1768213954,
  "exp": 1770805954
}
```

---

### 3. Token Management

#### Tessell IAM Service

**Refresh Token:**
- Long-lived (configurable, typically 30-90 days)
- Used to obtain new access tokens
- Stored in database (can be revoked)
- Contains user email and expiration

**Access Token:**
- Short-lived (typically 1-24 hours)
- Tenant-specific
- Contains user permissions, personas, tenant ID
- Used for API authorization

**Token Refresh Flow:**
1. Client sends refresh token
2. Validate refresh token (check expiration, revocation)
3. Generate new access token (tenant-specific)
4. Return new access token (refresh token remains same)

---

#### Native Auth POC

**JWT Token:**
- Configurable lifetime (default 30 days)
- Self-contained (no database lookup needed)
- Contains user ID, email, name
- Cannot be revoked (stateless)

**Token Refresh Flow:**
1. Client sends old JWT token
2. Validate old token (check signature, expiration)
3. Extract user info from token
4. Generate new JWT token (same lifetime)
5. Return new JWT token

---


## 🔑 Key Differences

### 1. User Onboarding

| Feature | Tessell IAM Service | Native Auth POC |
|---------|---------------------|-----------------|
| **Initiation** | Admin-initiated (invite) | Self-service (sign-up) |
| **Email Verification** | Invitation link | OTP code (8 digits) |
| **User Status** | INVITED → ACTIVE | Directly ACTIVE |
| **Password Setup** | During invitation acceptance | During sign-up |
| **Approval Required** | Yes (admin must invite) | No (anyone can sign up) |

---

### 2. Password Management

| Feature | Tessell IAM Service | Native Auth POC |
|---------|---------------------|-----------------|
| **Storage** | External User Management System | Microsoft Graph Open Extensions |
| **Hashing** | External system (likely BCrypt) | BCrypt (cost factor 12) |
| **Policy Enforcement** | ✅ Yes (min length, complexity, expiration) | ❌ No |
| **Password Expiration** | ✅ Yes | ❌ No |
| **Force Reset** | ✅ Yes | ❌ No |
| **Invalid Attempts Tracking** | ✅ Yes (account lockout) | ❌ No |
| **Password History** | ✅ Likely yes | ❌ No |

---

### 3. Authentication Methods

| Method | Tessell IAM Service | Native Auth POC |
|--------|---------------------|-----------------|
| **Password** | ✅ Yes | ✅ Yes |
| **Google OAuth** | ✅ Yes | ❌ No |
| **SAML SSO** | ✅ Yes | ❌ No |
| **MFA (TOTP)** | ✅ Yes | ❌ No |
| **API Keys** | ✅ Yes | ❌ No |
| **OTP (Email)** | ✅ Yes (custom) | ✅ Yes (Entra Native Auth) |

---

### 4. Token Management

| Feature | Tessell IAM Service | Native Auth POC |
|---------|---------------------|-----------------|
| **Token Types** | Refresh + Access | Single JWT |
| **Refresh Token Lifetime** | 30-90 days | N/A |
| **Access Token Lifetime** | 1-24 hours | 30 days (configurable) |
| **Token Revocation** | ✅ Yes (database-backed) | ❌ No (stateless) |
| **Token Storage** | Database | None (stateless) |
| **Tenant-Specific** | ✅ Yes | ❌ No |
| **Contains Permissions** | ✅ Yes (personas, privileges) | ❌ No (only user info) |

---

### 5. Multi-Tenancy

| Feature | Tessell IAM Service | Native Auth POC |
|---------|---------------------|-----------------|
| **Multi-Tenant Support** | ✅ Full support | ❌ Single tenant |
| **Tenant Validation** | ✅ Yes (on login) | ❌ No |
| **Tenant-Specific Tokens** | ✅ Yes | ❌ No |
| **Cross-Tenant Users** | ✅ Yes (same user, multiple tenants) | ❌ No |
| **Tenant Isolation** | ✅ Database-level | N/A |

---

### 6. Security Features

| Feature | Tessell IAM Service | Native Auth POC |
|---------|---------------------|-----------------|
| **Account Lockout** | ✅ Yes (after max attempts) | ❌ No |
| **Password Policy** | ✅ Yes (configurable) | ❌ No |
| **Password Expiration** | ✅ Yes | ❌ No |
| **Force Password Reset** | ✅ Yes | ❌ No |
| **MFA** | ✅ Yes (TOTP) | ❌ No |
| **Session Management** | ✅ Yes (token revocation) | ❌ No |
| **Rate Limiting** | ✅ Yes (email, login) | ❌ No |
| **Audit Logging** | ✅ Yes | ❌ No |

---

### 7. User Management

| Feature | Tessell IAM Service | Native Auth POC |
|---------|---------------------|-----------------|
| **User CRUD** | ✅ Full support | ✅ Basic support |
| **User Status** | ✅ Multiple states (INVITED, ACTIVE, INACTIVE, DELETED) | ✅ Basic (enabled/disabled) |
| **User Personas** | ✅ Yes (role-based) | ❌ No |
| **User Privileges** | ✅ Yes (fine-grained) | ❌ No |
| **User Apps** | ✅ Yes (app assignments) | ❌ No |
| **User Subscriptions** | ✅ Yes | ❌ No |
| **Bulk Operations** | ✅ Yes | ❌ No |
| **SCIM Support** | ✅ Yes | ❌ No |

---

### 8. Integration & Dependencies

| Aspect | Tessell IAM Service | Native Auth POC |
|--------|---------------------|-----------------|
| **Database** | PostgreSQL (required) | None |
| **External Services** | Tenant, Security, Governance, Notification | Microsoft Graph API, Entra Native Auth |
| **Service Dependencies** | High (4+ services) | Low (2 Microsoft services) |
| **Deployment Complexity** | High (microservices) | Low (single service) |
| **Infrastructure** | Self-hosted | Cloud-based (Microsoft) |
| **License Requirements** | PostgreSQL, self-hosted | Microsoft Entra External ID |

---

### 9. API Endpoints

#### Tessell IAM Service

**User Management:**
- `POST /iam/users/invite` - Invite user
- `POST /iam/users/accept-invite` - Accept invitation
- `POST /iam/users/login` - Login
- `POST /iam/users/refresh-token` - Refresh access token
- `POST /iam/users/verify-mfa` - Verify MFA code
- `POST /iam/users/reset-password` - Reset password
- `POST /iam/users/change-password` - Change password
- `GET /iam/users` - List users
- `GET /iam/users/{id}` - Get user
- `PATCH /iam/users/{id}` - Update user
- `DELETE /iam/users/{id}` - Delete user

**Total:** 11+ endpoints

---

#### Native Auth POC

**Hybrid Authentication:**
- `POST /api/v1/hybrid-auth/signup/start` - Start sign-up
- `POST /api/v1/hybrid-auth/signup/complete` - Complete sign-up
- `POST /api/v1/hybrid-auth/signin` - Sign in
- `GET /api/v1/hybrid-auth/validate` - Validate token
- `POST /api/v1/hybrid-auth/refresh` - Refresh token

**User Management:**
- `POST /api/users` - Create user
- `GET /api/users/{email}` - Get user
- `DELETE /api/users/{email}` - Delete user
- `PATCH /api/users/{email}/disable` - Disable user
- `PATCH /api/users/{email}/enable` - Enable user
- `POST /api/users/{email}/force-password-reset` - Force password reset

**Total:** 11 endpoints

---

### 10. Data Models

#### Tessell IAM Service - User Model

```java
class TessellGlobalUserDTO {
    UUID id;
    String firstName;
    String lastName;
    String internalId;
    String emailId;
    TessellAuthType authType;  // PASSWORD, GOOGLE
    TessellUserStatus status;  // INVITED, ACTIVE, INACTIVE, DELETED
    Date dateCreated;
    Date dateModified;
    String createdBy;
    Integer countInvalidAttempts;
    Date passwordExpiryDate;
    Boolean forcePasswordReset;
    List<TenantUserAttribute> tenantUserAttributes;  // Multi-tenant
    List<Persona> personas;
    List<App> apps;
    List<Subscription> subscriptions;
}
```

---

#### Native Auth POC - User Model

```java
// User stored in Microsoft Entra External ID
// Retrieved via Microsoft Graph API
{
    "id": "d0c7cbed-aed3-43c5-a1cd-0421d0b02bd1",
    "userPrincipalName": "iamudayhegde@gmail.com",
    "displayName": "Uday Hegde",
    "mail": "iamudayhegde@gmail.com",
    "accountEnabled": true
}

// Password hash stored in Open Extension
{
    "@odata.type": "microsoft.graph.openTypeExtension",
    "extensionName": "com.tessell.auth",
    "passwordHash": "$2a$12$..."
}
```

---


## 📈 Pros and Cons

### Tessell IAM Service

#### ✅ Pros
1. **Enterprise-Ready**
   - Full multi-tenant support
   - Comprehensive security features
   - Production-tested and battle-hardened

2. **Rich Feature Set**
   - Multiple authentication methods (Password, Google, SAML SSO)
   - MFA support (TOTP-based)
   - Password policy enforcement
   - Account lockout protection
   - Password expiration and force reset

3. **Fine-Grained Access Control**
   - User personas (role-based access)
   - Privileges and permissions
   - App assignments
   - Subscription management

4. **Audit and Compliance**
   - Comprehensive audit logging
   - User activity tracking
   - Invalid login attempt tracking
   - Token revocation support

5. **Scalability**
   - Database-backed (PostgreSQL)
   - Microservices architecture
   - Horizontal scaling support

#### ❌ Cons
1. **Complexity**
   - High deployment complexity (multiple services)
   - Requires PostgreSQL database
   - Multiple service dependencies (Tenant, Security, Governance, Notification)

2. **Infrastructure**
   - Self-hosted infrastructure required
   - Database maintenance overhead
   - Service orchestration complexity

3. **Development Overhead**
   - More code to maintain
   - Complex data models
   - Multiple integration points

4. **Onboarding**
   - Admin-initiated (invite-only)
   - Not suitable for self-service sign-up

---

### Native Auth POC

#### ✅ Pros
1. **Simplicity**
   - Single service deployment
   - No database required
   - Minimal dependencies (only Microsoft services)

2. **Self-Service**
   - Users can sign up directly
   - OTP-based email verification
   - No admin approval needed

3. **Cloud-Native**
   - Leverages Microsoft Entra External ID
   - Managed user storage
   - Built-in email OTP service

4. **Stateless**
   - JWT-based authentication
   - No session management overhead
   - Easy horizontal scaling

5. **Quick Setup**
   - Fast development and deployment
   - Minimal configuration
   - Good for POCs and MVPs

#### ❌ Cons
1. **Limited Features**
   - No MFA support
   - No password policy enforcement
   - No account lockout
   - No multi-tenant support

2. **Security Gaps**
   - No token revocation (stateless JWT)
   - No rate limiting
   - No audit logging
   - No password expiration

3. **Single Authentication Method**
   - Only password-based authentication
   - No SSO support
   - No Google OAuth
   - No API keys

4. **Vendor Lock-In**
   - Dependent on Microsoft Entra External ID
   - Requires Microsoft license
   - Limited customization

5. **Not Production-Ready**
   - Missing critical security features
   - No compliance features
   - No user management features (personas, privileges)

---

## 🎯 Use Case Recommendations

### When to Use Tessell IAM Service

✅ **Best for:**
- **Enterprise applications** requiring multi-tenancy
- **B2B SaaS platforms** with complex access control
- **Regulated industries** requiring audit trails and compliance
- **Applications** needing multiple authentication methods (SSO, OAuth, Password)
- **Scenarios** requiring MFA and advanced security features
- **Large-scale deployments** with thousands of users
- **Admin-controlled** user provisioning

**Example Scenarios:**
- Enterprise SaaS platform with multiple customers (tenants)
- Healthcare application requiring HIPAA compliance
- Financial services platform with strict security requirements
- B2B platform with SSO integration requirements

---

### When to Use Native Auth POC

✅ **Best for:**
- **Proof of Concepts** and MVPs
- **Simple applications** with basic authentication needs
- **Self-service sign-up** requirements
- **Mobile/Desktop apps** needing no-redirect authentication
- **Small-scale applications** (< 1000 users)
- **Quick prototypes** and demos
- **Single-tenant applications**

**Example Scenarios:**
- Mobile app POC with email/password authentication
- Internal tool with basic user management
- Startup MVP with self-service sign-up
- Demo application for client presentations

---

## 🔄 Migration Path

### From Native Auth POC to Tessell IAM Service

If you start with the Native Auth POC and need to migrate to Tessell IAM Service:

**Step 1: Data Migration**
1. Export users from Microsoft Entra External ID
2. Create PostgreSQL database schema
3. Import users into `TESSELL_USERS` table
4. Migrate password hashes (if compatible)

**Step 2: Authentication Flow Changes**
1. Replace self-service sign-up with invite flow
2. Implement admin user management
3. Add tenant support
4. Integrate with Tenant Service

**Step 3: Token Migration**
1. Replace custom JWT with refresh + access tokens
2. Implement token storage in database
3. Add token revocation support
4. Update client applications to handle two token types

**Step 4: Feature Additions**
1. Add MFA support
2. Implement password policy enforcement
3. Add account lockout protection
4. Implement audit logging

**Challenges:**
- ⚠️ Breaking changes to API contracts
- ⚠️ Client application updates required
- ⚠️ User re-authentication may be needed
- ⚠️ Database setup and maintenance

---

## 📊 Feature Comparison Matrix

| Category | Feature | Tessell IAM | Native Auth POC |
|----------|---------|-------------|-----------------|
| **Authentication** | Password-based | ✅ | ✅ |
| | Google OAuth | ✅ | ❌ |
| | SAML SSO | ✅ | ❌ |
| | MFA (TOTP) | ✅ | ❌ |
| | API Keys | ✅ | ❌ |
| | OTP (Email) | ✅ | ✅ |
| **User Management** | User CRUD | ✅ Full | ✅ Basic |
| | User Status | ✅ Multiple | ✅ Basic |
| | Personas/Roles | ✅ | ❌ |
| | Privileges | ✅ | ❌ |
| | Bulk Operations | ✅ | ❌ |
| | SCIM | ✅ | ❌ |
| **Security** | Password Policy | ✅ | ❌ |
| | Account Lockout | ✅ | ❌ |
| | Password Expiration | ✅ | ❌ |
| | Force Reset | ✅ | ❌ |
| | Token Revocation | ✅ | ❌ |
| | Rate Limiting | ✅ | ❌ |
| | Audit Logging | ✅ | ❌ |
| **Multi-Tenancy** | Tenant Support | ✅ | ❌ |
| | Tenant Isolation | ✅ | ❌ |
| | Cross-Tenant Users | ✅ | ❌ |
| **Tokens** | Refresh Token | ✅ | ❌ |
| | Access Token | ✅ | ✅ (JWT) |
| | Token Lifetime Control | ✅ | ✅ |
| **Infrastructure** | Database Required | ✅ PostgreSQL | ❌ |
| | External Services | ✅ 4+ | ✅ 2 |
| | Deployment Complexity | High | Low |
| **Onboarding** | Self-Service Sign-Up | ❌ | ✅ |
| | Admin Invite | ✅ | ❌ |
| | Email Verification | ✅ Link | ✅ OTP |

---

## 🏁 Conclusion

### Summary

**Tessell IAM Service** is a **production-ready, enterprise-grade** identity and access management system with:
- ✅ Full multi-tenancy support
- ✅ Comprehensive security features
- ✅ Multiple authentication methods
- ✅ Fine-grained access control
- ✅ Audit and compliance features
- ❌ High complexity and infrastructure requirements

**Native Auth POC** is a **lightweight, cloud-native** authentication solution with:
- ✅ Simple deployment (no database)
- ✅ Self-service sign-up
- ✅ OTP-based email verification
- ✅ Stateless JWT tokens
- ❌ Limited features and security gaps
- ❌ Not suitable for production enterprise use

### Recommendation

- **For Production Enterprise Applications**: Use **Tessell IAM Service**
- **For POCs, MVPs, and Simple Apps**: Use **Native Auth POC**
- **For Migration**: Start with Native Auth POC, plan migration to Tessell IAM Service as you scale

---

**Document Created:** 2026-01-12
**Author:** Tessell Team
**Version:** 1.0


---

## 🔐 ROPC Flow vs Native Auth Flow - Deep Dive

### Overview

Both systems use **password-based authentication**, but they leverage different Microsoft Azure technologies:

| Aspect | Tessell IAM Service (ROPC) | Native Auth POC |
|--------|---------------------------|-----------------|
| **Technology** | Azure AD B2C ROPC Flow | Microsoft Entra External ID Native Auth |
| **OAuth 2.0 Flow** | Resource Owner Password Credentials (ROPC) | Custom Native Auth API |
| **User Storage** | Azure AD B2C + PostgreSQL | Microsoft Entra External ID |
| **Password Verification** | Azure AD B2C ROPC endpoint | Custom (BCrypt) |
| **Token Issuer** | Azure AD B2C | Custom JWT (application) |
| **Browser Required** | ❌ No | ❌ No |
| **Redirect Required** | ❌ No | ❌ No |
| **OTP Support** | ❌ No (separate flow) | ✅ Yes (built-in) |

---

### 1. ROPC Flow (Tessell IAM Service)

#### What is ROPC?

**ROPC (Resource Owner Password Credentials)** is an OAuth 2.0 grant type that allows applications to directly exchange user credentials (username + password) for access tokens **without browser redirects**.

#### Architecture

```
┌─────────────────┐
│   Client App    │
└────────┬────────┘
         │ 1. POST /login (email, password)
         ▼
┌─────────────────────────────────────────────────────┐
│         Tessell IAM Service                         │
│  ┌──────────────────────────────────────────────┐  │
│  │  TessellUserController                       │  │
│  │  - Validate tenant                           │  │
│  │  - Check user status                         │  │
│  │  - Check password policy                     │  │
│  └──────────────┬───────────────────────────────┘  │
│                 │ 2. Delegate to Azure B2C         │
│  ┌──────────────▼───────────────────────────────┐  │
│  │  AzureADB2CManager                           │  │
│  │  - Build ROPC request                        │  │
│  │  - Call Azure B2C ROPC endpoint              │  │
│  └──────────────┬───────────────────────────────┘  │
└─────────────────┼───────────────────────────────────┘
                  │ 3. ROPC Token Request
                  ▼
┌─────────────────────────────────────────────────────┐
│         Azure AD B2C ROPC Endpoint                  │
│  https://{tenant}.b2clogin.com/{domain}/{policy}/   │
│         oauth2/v2.0/token                           │
│                                                     │
│  Request:                                           │
│  - grant_type: password                             │
│  - scope: openid {clientId} profile                 │
│  - response_type: token id_token                    │
│  - client_id: {clientId}                            │
│  - username: {userId}@{domain}                      │
│  - password: {password}                             │
└─────────────────┬───────────────────────────────────┘
                  │ 4. Verify credentials
                  │ 5. Return tokens (or error)
                  ▼
┌─────────────────────────────────────────────────────┐
│         Tessell IAM Service                         │
│  ┌──────────────────────────────────────────────┐  │
│  │  AzureADB2CManager                           │  │
│  │  - Receive Azure B2C response                │  │
│  │  - If success: credentials valid             │  │
│  │  - If error: credentials invalid             │  │
│  └──────────────┬───────────────────────────────┘  │
│                 │ 6. Generate Tessell tokens       │
│  ┌──────────────▼───────────────────────────────┐  │
│  │  AuthTokenManager                            │  │
│  │  - Generate refresh token                    │  │
│  │  - Generate access token                     │  │
│  └──────────────────────────────────────────────┘  │
└─────────────────┼───────────────────────────────────┘
                  │ 7. Return Tessell tokens
                  ▼
┌─────────────────┐
│   Client App    │
│  - Refresh token│
│  - Access token │
└─────────────────┘
```

#### ROPC Request Details

**Endpoint:**
```
POST https://{tenant}.b2clogin.com/{domain}/{ropcFlowName}/oauth2/v2.0/token
```

**Request Parameters:**
```http
POST /oauth2/v2.0/token HTTP/1.1
Host: {tenant}.b2clogin.com
Content-Type: application/x-www-form-urlencoded

grant_type=password
&scope=openid {clientId} profile
&response_type=token id_token
&client_id={clientId}
&username={userId}@{domain}.onmicrosoft.com
&password={userPassword}
```

**Success Response (from Azure B2C):**
```json
{
  "access_token": "eyJ0eXAiOiJKV1QiLCJhbGc...",
  "token_type": "Bearer",
  "expires_in": 3600,
  "id_token": "eyJ0eXAiOiJKV1QiLCJhbGc...",
  "refresh_token": "eyJraWQiOiJjcGltY29yZV8w..."
}
```

**Error Response (from Azure B2C):**
```json
{
  "error": "access_denied",
  "error_description": "AADB2C90078: The user has entered an incorrect password."
}
```

#### Code Implementation

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

#### Key Characteristics

✅ **Pros:**
- **No browser redirect** - Direct credential exchange
- **Azure-managed passwords** - Passwords stored securely in Azure AD B2C
- **OAuth 2.0 compliant** - Standard protocol
- **Azure B2C features** - Password policies, MFA (if configured)
- **Centralized user management** - Azure AD B2C portal

❌ **Cons:**
- **ROPC is deprecated** - Microsoft recommends against using ROPC
- **Security concerns** - Application handles raw passwords
- **Limited to password auth** - Cannot use other Azure B2C features easily
- **Azure B2C dependency** - Requires Azure B2C tenant and configuration
- **Complex setup** - Requires ROPC user flow configuration in Azure B2C

---

### 2. Native Auth Flow (Native Auth POC)

#### What is Native Auth?

**Native Auth** is a **new Microsoft Entra External ID feature** designed specifically for **mobile and desktop applications** that need password-based authentication **without browser redirects**.

#### Architecture

```
┌─────────────────┐
│   Client App    │
└────────┬────────┘
         │ 1. POST /signup/start (email, password)
         ▼
┌─────────────────────────────────────────────────────┐
│         Native Auth POC                             │
│  ┌──────────────────────────────────────────────┐  │
│  │  HybridAuthController                        │  │
│  │  - Cache password temporarily                │  │
│  └──────────────┬───────────────────────────────┘  │
│                 │ 2. Start sign-up                 │
│  ┌──────────────▼───────────────────────────────┐  │
│  │  HybridAuthService                           │  │
│  │  - Call Native Auth API                      │  │
│  └──────────────┬───────────────────────────────┘  │
└─────────────────┼───────────────────────────────────┘
                  │ 3. Native Auth Sign-Up Request
                  ▼
┌─────────────────────────────────────────────────────┐
│    Entra Native Auth API                            │
│  https://{tenant}.ciamlogin.com/{tenant}/           │
│         signup/v1.0/start                           │
│                                                     │
│  Request:                                           │
│  - client_id: {clientId}                            │
│  - challenge_type: oob password redirect            │
│  - username: {email}                                │
│  - password: {password}                             │
└─────────────────┬───────────────────────────────────┘
                  │ 4. Send OTP to email
                  │ 5. Return continuation token
                  ▼
┌─────────────────┐
│   User Email    │
│  OTP: 12345678  │
└─────────────────┘
         │ 6. User enters OTP
         ▼
┌─────────────────┐
│   Client App    │
└────────┬────────┘
         │ 7. POST /signup/complete (token, OTP)
         ▼
┌─────────────────────────────────────────────────────┐
│         Native Auth POC                             │
│  ┌──────────────────────────────────────────────┐  │
│  │  HybridAuthService                           │  │
│  │  - Verify OTP with Native Auth               │  │
│  │  - Extract userId from ID token              │  │
│  │  - Hash password with BCrypt                 │  │
│  │  - Store hash in Graph Open Extension        │  │
│  │  - Generate custom JWT token                 │  │
│  └──────────────────────────────────────────────┘  │
└─────────────────┼───────────────────────────────────┘
                  │ 8. Return JWT token
                  ▼
┌─────────────────┐
│   Client App    │
│  - JWT token    │
└─────────────────┘
```


#### Native Auth Request Details

**Sign-Up Start Endpoint:**
```
POST https://{tenant}.ciamlogin.com/{tenant}/signup/v1.0/start
```

**Request Parameters:**
```http
POST /signup/v1.0/start HTTP/1.1
Host: {tenant}.ciamlogin.com
Content-Type: application/x-www-form-urlencoded

client_id={clientId}
&challenge_type=oob password redirect
&username={email}
&password={password}
```

**Success Response:**
```json
{
  "continuation_token": "uY29tL2F1dGhlbnRpY2F0ZS9vb2IvdjEuMC...",
  "challenge_type": "oob",
  "challenge_channel": "email",
  "challenge_target_label": "u***@example.com"
}
```

**Sign-Up Complete Endpoint:**
```
POST https://{tenant}.ciamlogin.com/{tenant}/signup/v1.0/continue
```

**Request Parameters:**
```http
POST /signup/v1.0/continue HTTP/1.1
Host: {tenant}.ciamlogin.com
Content-Type: application/x-www-form-urlencoded

client_id={clientId}
&continuation_token={continuationToken}
&grant_type=oob
&oob={otpCode}
```

**Success Response:**
```json
{
  "id_token": "eyJhbGciOiJSUzI1NiIsImtpZCI6...",
  "token_type": "Bearer",
  "expires_in": 3600
}
```

#### Code Implementation

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
    JsonNode jsonResponse = objectMapper.readTree(response.body().string());

    String continuationToken = jsonResponse.get("continuation_token").asText();

    // Cache password for later use
    signUpCache.put(continuationToken, new SignUpData(email, password));

    return continuationToken;
}

@Override
public TokenResponse signUpComplete(String continuationToken, String otp, String displayName)
    throws Exception {
    // Step 2: Verify OTP and complete sign-up
    String url = config.getNativeAuthBaseUrl() + "/signup/v1.0/continue";
    String formBody = String.format(
        "client_id=%s&continuation_token=%s&grant_type=oob&oob=%s",
        config.getClientId(), continuationToken, otp
    );

    // ... verify OTP with Native Auth ...

    // Step 3: Extract userId from ID token
    String userId = extractUserIdFromIdToken(idToken);

    // Step 4: Hash password with BCrypt
    SignUpData signUpData = signUpCache.remove(continuationToken);
    String hashedPassword = BCrypt.hashpw(signUpData.password, BCrypt.gensalt(12));

    // Step 5: Store password hash in Graph Open Extension
    graphApiService.storePasswordHash(userId, hashedPassword);

    // Step 6: Generate custom JWT token
    String jwtToken = generateJwtToken(userId, signUpData.email, displayName);

    return TokenResponse.builder()
        .accessToken(jwtToken)
        .tokenType("Bearer")
        .expiresIn(tokenLifetimeDays * 24 * 60 * 60)
        .build();
}
```

#### Key Characteristics

✅ **Pros:**
- **Modern approach** - Designed for native apps (not deprecated like ROPC)
- **Built-in OTP verification** - Email verification included
- **Self-service sign-up** - Users can register without admin
- **No ROPC dependency** - Uses newer Native Auth API
- **Flexible password storage** - Can use custom hashing (BCrypt)
- **Custom token control** - Full control over JWT token lifetime

❌ **Cons:**
- **Hybrid approach** - Combines Native Auth (sign-up) + custom auth (sign-in)
- **Password stored separately** - Not in Azure, stored in Graph Open Extensions
- **Manual password verification** - Application handles password checking
- **Limited Azure features** - Cannot leverage Azure B2C password policies
- **Newer technology** - Less mature than Azure B2C

---

### 3. Detailed Comparison

#### Password Verification

| Aspect | ROPC Flow | Native Auth Flow |
|--------|-----------|------------------|
| **Verification Method** | Azure B2C ROPC endpoint | BCrypt.checkpw() in application |
| **Password Storage** | Azure AD B2C (managed) | Graph Open Extensions (custom) |
| **Hashing Algorithm** | Azure-managed (unknown) | BCrypt (cost factor 12) |
| **Password Policies** | Azure B2C policies | None (application-level only) |
| **Verification Endpoint** | `https://{tenant}.b2clogin.com/.../oauth2/v2.0/token` | Application logic |
| **Response Time** | Network call to Azure | Local BCrypt verification |

---

#### Token Generation

| Aspect | ROPC Flow | Native Auth Flow |
|--------|-----------|------------------|
| **Token Issuer** | Azure AD B2C | Application (custom JWT) |
| **Token Type** | Azure B2C tokens (access + refresh + id) | Custom JWT |
| **Token Lifetime** | Azure B2C configured (typically 1 hour) | Application configured (30 days default) |
| **Token Format** | Azure B2C JWT (complex claims) | Simple JWT (user info only) |
| **Token Validation** | Azure B2C public keys | Application secret (HMAC-SHA256) |
| **Token Refresh** | Azure B2C refresh token endpoint | Application refresh logic |

**ROPC Token Claims (from Azure B2C):**
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

**Native Auth Custom JWT Claims:**
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

#### User Onboarding

| Aspect | ROPC Flow | Native Auth Flow |
|--------|-----------|------------------|
| **User Creation** | Admin creates in Azure B2C | Self-service via Native Auth |
| **Email Verification** | Optional (Azure B2C config) | Required (OTP) |
| **Password Setup** | Admin sets or user sets on first login | User sets during sign-up |
| **Approval Required** | Yes (admin must create user) | No (automatic) |
| **User Storage** | Azure AD B2C + PostgreSQL | Microsoft Entra External ID |

---

#### Security Comparison

| Security Feature | ROPC Flow | Native Auth Flow |
|------------------|-----------|------------------|
| **Password Exposure** | ⚠️ Application sees raw password | ⚠️ Application sees raw password |
| **Password Storage** | ✅ Azure-managed (secure) | ✅ BCrypt hash in Graph (secure) |
| **Brute Force Protection** | ✅ Azure B2C built-in | ❌ Application must implement |
| **Account Lockout** | ✅ Azure B2C policies | ❌ Application must implement |
| **Password Complexity** | ✅ Azure B2C policies | ❌ Application must implement |
| **Password Expiration** | ✅ Azure B2C policies | ❌ Application must implement |
| **MFA Support** | ✅ Azure B2C (if configured) | ❌ Not implemented |
| **Audit Logging** | ✅ Azure B2C logs | ❌ Application must implement |

---

#### Configuration Complexity

**ROPC Flow Configuration:**
1. Create Azure AD B2C tenant
2. Create ROPC user flow in Azure B2C
3. Configure password policies
4. Register application
5. Grant API permissions
6. Configure client secret
7. Configure application.yml with B2C details
8. Set up PostgreSQL database
9. Configure external services (Tenant, Security, etc.)

**Native Auth Flow Configuration:**
1. Create Entra External ID tenant
2. Register application
3. Enable Native Authentication
4. Grant API permissions (User.ReadWrite.All)
5. Configure client secret
6. Configure application.yml with Entra details
7. No database required
8. No external services required

---

### 4. Migration Considerations

#### From ROPC to Native Auth

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

**Migration Steps:**
1. Export user list from Azure B2C
2. Create users in Entra External ID
3. Send password reset emails to all users
4. Users sign up via Native Auth (sets new password)
5. Update client applications to use new API endpoints
6. Decommission Azure B2C tenant

---

#### From Native Auth to ROPC

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

**Migration Steps:**
1. Create Azure AD B2C tenant
2. Configure ROPC user flow
3. Export users from Entra External ID
4. Create users in Azure B2C (admin process)
5. Send password reset emails
6. Update client applications
7. Decommission Entra External ID tenant

---

### 5. Recommendations

#### When to Use ROPC Flow (Tessell IAM Service Approach)

✅ **Best for:**
- **Enterprise applications** with existing Azure B2C infrastructure
- **Scenarios** requiring Azure B2C password policies
- **Applications** needing MFA support
- **Regulated industries** requiring Azure compliance
- **Admin-controlled** user provisioning
- **Applications** that can tolerate ROPC deprecation warnings

⚠️ **Considerations:**
- ROPC is deprecated by Microsoft (not recommended for new projects)
- Requires Azure B2C tenant and configuration
- Higher infrastructure complexity

---

#### When to Use Native Auth Flow (Native Auth POC Approach)

✅ **Best for:**
- **New projects** starting from scratch
- **Mobile/Desktop applications** needing no-redirect auth
- **Self-service sign-up** requirements
- **POCs and MVPs** with simple auth needs
- **Applications** wanting full control over token lifetime
- **Scenarios** where Azure B2C is overkill

⚠️ **Considerations:**
- Requires implementing security features (password policy, account lockout, etc.)
- Password verification happens in application (not Azure)
- Newer technology (less mature)

---

### 6. Summary Table

| Feature | ROPC Flow | Native Auth Flow | Winner |
|---------|-----------|------------------|--------|
| **No Browser Redirect** | ✅ | ✅ | Tie |
| **OAuth 2.0 Compliant** | ✅ | ❌ | ROPC |
| **Microsoft Recommended** | ❌ (deprecated) | ✅ | Native Auth |
| **Password Policies** | ✅ Azure B2C | ❌ | ROPC |
| **MFA Support** | ✅ | ❌ | ROPC |
| **Self-Service Sign-Up** | ❌ | ✅ | Native Auth |
| **OTP Verification** | ❌ | ✅ | Native Auth |
| **Token Lifetime Control** | ❌ (Azure B2C) | ✅ (Custom) | Native Auth |
| **Setup Complexity** | High | Low | Native Auth |
| **Security Features** | ✅ Azure B2C | ❌ (DIY) | ROPC |
| **Maturity** | ✅ Mature | ⚠️ New | ROPC |
| **Future-Proof** | ❌ Deprecated | ✅ Modern | Native Auth |

---

### 7. Conclusion

**ROPC Flow (Tessell IAM Service):**
- ✅ Mature, feature-rich, Azure-managed security
- ❌ Deprecated, complex setup, limited flexibility
- **Best for**: Existing Azure B2C deployments, enterprise apps

**Native Auth Flow (Native Auth POC):**
- ✅ Modern, flexible, self-service, simple setup
- ❌ Missing security features, requires custom implementation
- **Best for**: New projects, mobile apps, POCs

**Recommendation:**
- **For new projects**: Use **Native Auth** (modern, not deprecated)
- **For existing Azure B2C deployments**: Continue with **ROPC** (but plan migration)
- **For production enterprise apps**: Implement **Native Auth** + add security features (password policy, MFA, account lockout)

---

**Last Updated:** 2026-01-12
**Section:** ROPC vs Native Auth Deep Dive
