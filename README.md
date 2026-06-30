# MeiCrypt Identity Platform (MIP)

Enterprise Multi-Tenant Identity and Access Management Platform built with Spring Boot 3.x and Java 21.

## 🎯 Overview

MeiCrypt Identity Platform is a production-grade identity and access management solution providing:

- **Multi-Tenant Architecture** - Organization-scoped isolation for B2B and internal use
- **OAuth2 & OpenID Connect** - Standards-compliant authorization flows with PKCE
- **Role-Based Access Control (RBAC)** - Fine-grained permissions and flexible role management
- **Multi-Factor Authentication (MFA)** - TOTP authenticator apps and WebAuthn support
- **Single Sign-On (SSO)** - Centralized authentication across applications
- **Comprehensive Audit Logging** - Immutable security event tracking
- **Client Application Registry** - Manage OAuth2 clients per organization

## 🏗️ Architecture

**Architecture Pattern:** Modular Monolith with Domain-Driven Design principles

**Technology Stack:**
- **Runtime:** Java 21 LTS
- **Framework:** Spring Boot 3.2.5
- **Security:** Spring Security 6.x + Spring Authorization Server 1.2.4
- **Database:** PostgreSQL 16 with Flyway migrations
- **Cache:** Redis 7 for session management
- **API Documentation:** OpenAPI 3.0 (Springdoc)
- **Testing:** JUnit 5, Testcontainers

## 📦 Project Structure

```
com.meicrypt.identity
├── common/              # Shared utilities, exceptions, validation
├── organization/        # Organization domain (multi-tenant anchor)
├── user/               # User identity lifecycle management
├── auth/               # Authentication & session management
├── authorization/      # RBAC framework
├── client/             # OAuth2 client application registry
├── oauth2/             # OAuth2 & OIDC implementation
├── sso/                # Single Sign-On federation
├── mfa/                # Multi-factor authentication
├── audit/              # Audit logging and security events
├── notification/       # Email and SMS delivery
└── config/             # Application configuration beans
```

## 🚀 Getting Started

### Prerequisites

- **Java 21 LTS** - [Download](https://adoptium.net/)
- **Maven 3.9+** - [Download](https://maven.apache.org/download.cgi)
- **Docker & Docker Compose** - [Download](https://www.docker.com/products/docker-desktop)

### Quick Start

1. **Clone the repository**
```bash
cd /home/siva-25197/Downloads/meicrypt
```

2. **Start infrastructure services**
```bash
docker-compose up -d postgres redis
```

This starts:
- PostgreSQL on `localhost:5432`
- Redis on `localhost:6379`
- Optional: pgAdmin on `localhost:5050`
- Optional: Redis Commander on `localhost:8081`

3. **Build the application**
```bash
mvn clean install
```

4. **Run the application**
```bash
mvn spring-boot:run
```

The application will start on `http://localhost:8080`

### Access Points

- **API Documentation:** http://localhost:8080/swagger-ui.html
- **Health Check:** http://localhost:8080/actuator/health
- **pgAdmin:** http://localhost:5050 (admin@meicrypt.com / admin)
- **Redis Commander:** http://localhost:8081

## 🔧 Configuration

### Application Profiles

- **`local`** - Development environment with detailed logging
- **`test`** - Test environment with Testcontainers
- **`production`** - Production environment with environment variables

### Environment Variables (Production)

```bash
DATABASE_URL=jdbc:postgresql://prod-db:5432/meicrypt_identity
DATABASE_USERNAME=meicrypt_prod
DATABASE_PASSWORD=<secure-password>
REDIS_HOST=prod-redis
REDIS_PORT=6379
REDIS_PASSWORD=<secure-password>
ISSUER_URL=https://identity.meicrypt.com
```

## 🧪 Testing

### Run all tests
```bash
mvn test
```

### Run integration tests only
```bash
mvn test -Dgroups=integration
```

Tests use Testcontainers to spin up PostgreSQL and Redis automatically.

## 📚 API Documentation

Interactive API documentation is available at `/swagger-ui.html` when running the application.

Key API endpoints:
- `/api/v1/organizations` - Organization management
- `/api/v1/users` - User management
- `/api/v1/auth` - Authentication endpoints
- `/oauth2/authorize` - OAuth2 authorization endpoint
- `/oauth2/token` - OAuth2 token endpoint
- `/.well-known/openid-configuration` - OIDC discovery

## 🏗️ Development Roadmap

### ✅ Phase 0: Bootstrap & Foundation (COMPLETE)
- Project structure and dependencies
- Database and cache configuration
- Logging and exception handling
- API documentation setup
- Testing infrastructure

### 🚧 Phase 1: Organization Engine (NEXT)
- Organization entity model
- Settings management
- Membership management
- Invitation system
- Custom domains

### 📋 Future Phases
- Phase 2: Identity & Account Lifecycle
- Phase 3: Authentication & Session Management
- Phase 4: RBAC Authorization Framework
- Phase 5: Client Application Registry
- Phase 6-7: OAuth2 & OIDC
- Phase 8-9: SSO Federation & MFA
- Phase 10-13: Notifications, Audit, Admin Console, Developer Portal

## 🔐 Security Principles

1. **Zero Field Injection** - Constructor injection only
2. **Multi-Tenant Isolation** - Organization-scoped data partitioning
3. **Domain Boundary Enforcement** - No direct cross-module repository access
4. **Secure Password Hashing** - Argon2id or BCrypt
5. **PKCE Required** - All OAuth2 authorization flows
6. **Immutable Audit Logs** - Security event tracking
7. **Least Privilege** - Fine-grained RBAC permissions

## 📝 Database Migrations

Flyway manages all database schema changes. Migration files are located in:
```
src/main/resources/db/migration/
├── V1__initial_schema.sql
├── V2__user_tables.sql
└── ...
```

Run migrations manually:
```bash
mvn flyway:migrate
```

## 🤝 Contributing

This is an internal project following strict architectural guidelines:

1. Follow the modular package structure
2. Use constructor injection exclusively
3. Respect domain boundaries
4. Write comprehensive tests
5. Document public APIs
6. Follow RFC 7807 for error responses

## 📄 License

Proprietary - MeiCrypt Engineering Team

## 📧 Contact

Engineering Team: engineering@meicrypt.com

---

**Built with ❤️ by MeiCrypt Engineering Team**
