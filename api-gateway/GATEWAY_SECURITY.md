
# Gateway Security

## Overview

The API Gateway is the entry point for client requests in the microservices architecture.

The Gateway is responsible for:

- Validating JWT access tokens
- Creating the Spring Security `Authentication`
- Protecting authenticated endpoints
- Routing requests to downstream microservices
- Providing gateway-level authorization when required

The Identity Service has a different responsibility. It authenticates users using their credentials and generates JWT access tokens.

---

## Security Flow

```text
Client
  |
  | Authorization: Bearer <JWT>
  v
API Gateway
  |
  | Verify JWT signature
  | using RSA Public Key
  | Check expiration
  v
Authenticated Request
  |
  v
Downstream Microservice
````

For login:

```text
Client
  |
  | POST /auth/login
  v
API Gateway
  |
  | Route request
  v
Identity Service
  |
  | Validate email/password
  | Generate JWT using Private Key
  v
JWT
```

---

## JWT Asymmetric-Key Security

The Identity Service generates JWTs using an RSA private key.

```text
Identity Service
       |
       | Private Key
       v
    Sign JWT
       |
       v
      JWT
```

The Gateway contains only the RSA public key.

```text
API Gateway
     |
     | Public Key
     v
 Verify JWT
```

The private key is never stored in the Gateway.

The asymmetric-key model is:

```text
Private Key → Sign JWT
Public Key  → Verify JWT
```

This allows the Gateway to verify tokens without having the ability to create valid tokens.

---

## Gateway JWT Authentication Filter

The Gateway uses a custom `JwtAuthenticationFilter` extending `OncePerRequestFilter`.

The filter performs the following operations:

1. Reads the `Authorization` header.
2. Checks whether the header starts with `Bearer `.
3. Extracts the JWT.
4. Verifies the JWT signature using the RSA public key.
5. Checks token expiration.
6. Extracts the username from the JWT.
7. Creates a Spring Security `Authentication`.
8. Stores the authentication in the `SecurityContext`.
9. Continues the request.

```text
Authorization Header
        |
        v
    Bearer JWT
        |
        v
 Extract Token
        |
        v
 Verify Signature
        |
        v
 Check Expiration
        |
        v
 Create Authentication
        |
        v
 SecurityContext
        |
        v
 Continue Request
```

An invalid JWT does not create an authenticated `SecurityContext`.

---

## Stateless Security

The Gateway uses:

```java
SessionCreationPolicy.STATELESS
```

The Gateway does not maintain HTTP sessions.

Each request must provide its authentication information through the JWT.

```text
Request 1 → JWT → Authenticate
Request 2 → JWT → Authenticate
Request 3 → JWT → Authenticate
```

---

## Public and Protected Endpoints

The following endpoints are public:

```text
/auth/register
/auth/login
/auth/refresh
/actuator/health
```

They are configured using:

```java
.permitAll()
```

All other endpoints require authentication:

```java
.anyRequest().authenticated()
```

Example:

```text
/auth/login       → Public
/auth/register    → Public
/auth/refresh     → Public
/users/**         → Authentication required
/other/**         → Authentication required
```

---

## CSRF Protection

CSRF protection is configured specifically for:

```text
POST /auth/refresh
```

This is useful when the refresh-token flow uses cookies because cookies can be sent automatically by the browser.

The Gateway uses:

```java
CookieCsrfTokenRepository.withHttpOnlyFalse()
```

and requires CSRF protection for the refresh endpoint.

---

# Gateway vs Identity Service Security

The Gateway and Identity Service both use Spring Security, but they have different responsibilities.

## Identity Service

The Identity Service is responsible for user authentication.

Its authentication flow is:

```text
Email + Password
       |
       v
AuthenticationManager
       |
       v
DaoAuthenticationProvider
       |
       v
UserDetailsService
       |
       v
Database
       |
       v
Authenticated User
       |
       v
Generate JWT
```

Because of this, Identity Service requires:

```text
AuthenticationManager
AuthenticationProvider
DaoAuthenticationProvider
UserDetailsService
PasswordEncoder
```

The Identity Service also contains the RSA private key because it generates JWTs.

```text
Identity Service
├── Private Key → Sign JWT
└── Public Key  → Verify JWT
```

---

## API Gateway

The Gateway does not authenticate usernames and passwords.

It receives an already-issued JWT and verifies it.

```text
JWT
 |
 v
Public Key
 |
 v
Signature Verification
 |
 v
Expiration Check
 |
 v
Authentication
```

Therefore, the Gateway does not need:

```text
AuthenticationManager
AuthenticationProvider
DaoAuthenticationProvider
UserDetailsService
PasswordEncoder
```

The Gateway only needs the RSA public key for JWT verification.

```text
Gateway
└── Public Key → Verify JWT
```

---

# Why Authentication Is Created in Both Services

Both services create their own `Authentication` object because they are separate applications.

For example:

```text
Gateway JVM
    |
    └── SecurityContext
          └── Authentication A
```

and:

```text
Identity Service JVM
    |
    └── SecurityContext
          └── Authentication B
```

`Authentication A` is not transferred to the Identity Service.

Only the JWT is transferred through HTTP:

```text
Client
   |
   | JWT
   v
Gateway
   |
   | JWT
   v
Identity Service
```

Therefore, creating an `Authentication` object in both services is normal and expected.

They represent the same logical user, but they are separate Java objects in separate JVMs.

---

# Difference Between the Authentication Objects

The Gateway currently creates:

```java
new UsernamePasswordAuthenticationToken(
    username,
    null,
    Collections.emptyList()
);
```

Therefore the Gateway authentication contains:

```text
Principal    → username
Credentials  → null
Authorities  → empty
```

The Identity Service creates:

```java
new UsernamePasswordAuthenticationToken(
    userDetails,
    null,
    userDetails.getAuthorities()
);
```

Therefore the Identity Service authentication contains:

```text
Principal    → User/UserDetails
Credentials  → null
Authorities  → User authorities
```

The two `Authentication` objects are local to their respective applications.

---

# Why Gateway Does Not Query the Identity Database

The Gateway verifies the JWT locally using the public key.

It does not need to call the Identity Service or database for every request.

Without local verification:

```text
Client
  |
  v
Gateway
  |
  v
Identity Service
  |
  v
Database
  |
  v
Gateway
  |
  v
Downstream Service
```

With local JWT verification:

```text
Client
  |
  v
Gateway
  |
  | Local JWT verification
  v
Downstream Service
```

This avoids unnecessary network calls and keeps authentication stateless.

Feign should be used when the Gateway genuinely needs to communicate with another service, not for ordinary JWT validation.

---

# Role and Authorization

The Gateway currently creates:

```java
Collections.emptyList()
```

for authorities.

Therefore, the Gateway currently authenticates the request but does not have role information in the `Authentication`.

For example, role-based authorization such as:

```java
.hasRole("ADMIN")
```

requires the Gateway to have the user's role as an authority.

A possible JWT structure is:

```text
JWT
├── subject = userId
├── role = ADMIN
├── issuedAt
└── expiration
```

The Gateway can extract the `role` claim and create:

```text
ROLE_ADMIN
```

as a Spring Security authority.

Then the Gateway can perform gateway-level authorization.

For example:

```java
.requestMatchers("/users/**").hasRole("ADMIN")
```

This means:

```text
/users/** → Only ADMIN can pass the Gateway
```

The route configuration itself does not perform role checking.

For example:

```yaml
spring:
  cloud:
    gateway:
      routes:
        - id: identity-service-users
          uri: lb://IDENTITY-SERVICE
          predicates:
            - Path=/users/**
```

This only means:

```text
/users/** → IDENTITY-SERVICE
```

It does not mean:

```text
/users/** → ADMIN only
```

Authorization is handled separately by Spring Security.

---

# Gateway Routing vs Authorization

## Routing

Gateway routes requests based on path predicates:

```yaml
- id: identity-service-users
  uri: lb://IDENTITY-SERVICE
  predicates:
    - Path=/users/**
```

This determines **where the request goes**.

## Authorization

Spring Security determines whether the authenticated user is allowed to access the endpoint:

```java
.requestMatchers("/users/**").hasRole("ADMIN")
```

This determines **who is allowed to access it**.

Therefore:

```text
Routing      → Where should the request go?
Authorization → Is this user allowed to access it?
```

---

# Security Responsibility Summary

| Responsibility              | Identity Service | API Gateway |
| --------------------------- | ---------------- | ----------- |
| User registration           | Yes              | No          |
| Login                       | Yes              | No          |
| Password validation         | Yes              | No          |
| Access JWT generation       | Yes              | No          |
| RSA private key             | Yes              | No          |
| RSA public key              | Yes              | Yes         |
| JWT signature verification  | Yes              | Yes         |
| JWT expiration check        | Yes              | Yes         |
| Load user from database     | Yes              | No          |
| UserDetailsService          | Yes              | No          |
| PasswordEncoder             | Yes              | No          |
| AuthenticationManager       | Yes              | No          |
| Request routing             | No               | Yes         |
| Stateless security          | Yes              | Yes         |
| Gateway-level authorization | No / Limited     | Yes         |
| Business authorization      | Yes              | No          |

---

# Key Security Principle

```text
                 IDENTITY SERVICE
                 ┌─────────────────┐
                 │                 │
                 │ Private Key 🔐  │
                 │                 │
                 │ Authenticate    │
                 │ Users           │
                 │                 │
                 │ Issue JWT       │
                 └────────┬────────┘
                          |
                          | JWT
                          v
                    API GATEWAY
                 ┌─────────────────┐
                 │                 │
                 │ Public Key 🔑   │
                 │                 │
                 │ Verify JWT      │
                 │                 │
                 │ Route Request   │
                 └────────┬────────┘
                          |
                          v
                   MICROSERVICES
                 ┌─────────────────┐
                 │                 │
                 │ Public Key 🔑   │
                 │                 │
                 │ Verify JWT      │
                 │                 │
                 │ Authorize       │
                 │ Business Logic  │
                 └─────────────────┘
```

## Final Principle

**Identity Service authenticates the user and issues the JWT.**

**API Gateway verifies the JWT and controls access to the API entry point.**

**Downstream services can independently verify the JWT and enforce their own business-level authorization.**

The JWT is what crosses the network.

The Java `Authentication` object does not cross the network.

```
```
