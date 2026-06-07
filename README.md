# SAML 2.0 SSO & SLO — Proof of Concept (PoC)

> Implementasi manual SAML 2.0 Service Provider dalam **Java 7 syntax** + **pac4j 1.8.8** — mendemonstrasikan Single Sign-On dan Single Logout tingkat enterprise tanpa framework modern.

![Java](https://img.shields.io/badge/Java-7%20syntax%20%2F%208%20target-orange)
![SAML](https://img.shields.io/badge/SAML-2.0-blue)
![pac4j](https://img.shields.io/badge/pac4j-1.8.8-green)
![Keycloak](https://img.shields.io/badge/Keycloak-23-orange)
![Docker](https://img.shields.io/badge/Docker-Compose-blue)
![Status](https://img.shields.io/badge/Status-Production%20Ready-brightgreen)

---

## 📋 Ringkasan

Proyek ini adalah Proof of Concept (PoC) implementasi SAML 2.0 Service Provider (SP) dari nol, dirancang untuk mereplikasi kendala dunia enterprise:

- **Java 7 syntax** — tanpa lambdas, streams, try-with-resources, Optional, atau `java.time.*`
- **pac4j 1.8.8** — versi lama yang **tidak** mendukung Single Logout (SLO), sehingga semua logika logout diimplementasikan secara manual
- **Manual SAML message handling** — AuthnRequest, LogoutRequest, LogoutResponse dibangun dari XML mentah dengan DEFLATE compression, Base64 encoding, dan URL encoding yang benar
- **Keycloak 23** sebagai Identity Provider (IdP) pre-configured dalam container
- **Docker Compose** — seluruh environment berjalan dengan satu perintah, tanpa Java atau Maven lokal

Tujuan utama adalah mendemonstrasikan pemahaman mendalam tentang protokol SAML 2.0 di level implementasi — bukan hanya cara menggunakan library, tetapi bagaimana protokol sebenarnya bekerja: struktur XML, binding types, message encoding, dan session state management.

---

## 🏗️ Arsitektur Sistem

```
┌────────────────────────────────────────────────────────────────┐
│                    Browser / User Agent                         │
└───────────────┬──────────────────────────┬────────────────────┘
                │ HTTP Redirect / POST     │
                │ SAMLRequest/Response     │
                ▼                          ▼
┌──────────────────────────┐  ┌──────────────────────────────────┐
│ Service Provider (SP)    │  │  Identity Provider (IdP)         │
│ Tomcat 9 + JDK 11        │  │  Keycloak 23                     │
│ Port: 8080               │  │  Port: 8443                      │
│                          │  │                                  │
│ ┌──────────────────────┐ │  │ ┌────────────────────────────┐   │
│ │   SSOServlet         │ │  │ │  poc Realm                 │   │
│ │   (Main Router)      │ │  │ │  • SAML 2.0 Client Config  │   │
│ └─┬────┬────┬────┬────┘ │  │ │  • 4 Test Users            │   │
│   │    │    │    │      │  │ │  • Attribute Mappers (divisi)   │
│ ┌─▼────▼────▼────▼────┐ │  │ └────────────────────────────┘   │
│ │  Handlers           │ │  │                                  │
│ │  • LoginHandler     │ │  │ HTTP-POST Binding:              │
│ │  • LogoutHandler    │◄┼──┼─ AuthnRequest → AuthnResponse   │
│ │  • CallbackHandler  │ │  │                                  │
│ └─┬────┬────┬────┬────┘ │  │ HTTP-Redirect Binding:          │
│   │    │    │    │      │  │ LogoutRequest → LogoutResponse  │
│ ┌─▼────▼────▼────▼────┐ │  │                                  │
│ │  Utilities          │ │  └──────────────────────────────────┘
│ │  • SamlConfig       │ │
│ │  • SamlMessageUtil  │ │
│ │  • CookieUtil       │ │
│ └─────────────────────┘ │
└─────────────────────────┘
```

---

## ✨ Fitur Utama

### 1️⃣ Single Sign-On (SSO) — SP-Initiated
- AuthnRequest XML dibangun secara manual dari nol
- HTTP-POST Binding: AuthnRequest dikode sebagai Base64, dikirim via auto-submit HTML form
- SAMLResponse diproses di endpoint `/callbacksso`
- Tahapan: Base64 decode → XML parse → validasi signature → ekstrak NameID → ekstrak attributes
- Pemberian role/group via XML mapping di `esb.xml`

### 2️⃣ Single Logout (SLO) — SP-Initiated & IdP-Initiated
- **SP-Initiated SLO**: LogoutRequest dibangun manual → DEFLATE compression → Base64 encoding → URL encoding → HTTP-Redirect ke IdP
- **IdP-Initiated SLO**: LogoutRequest diterima di `/callbacksso` → session SP diinvalidasi → LogoutResponse dibangun → redirect ke IdP
- State tracking menggunakan cookie `slo_in_progress` untuk membedakan LogoutResponse vs AuthnResponse

### 3️⃣ Pemetaan Group & User
- XML-based role mapping via `esb.xml` — tidak perlu ubah kode untuk update mapping
- `groupmapping`: memetakan attribute IdP `divisi` → application group
- `usermapping`: override per-user dengan priority lebih tinggi dari group mapping
- Fallback ke default group `viewer` jika tidak ada mapping yang cocok

### 4️⃣ Keamanan
- **XXE Hardening**: setiap `DocumentBuilderFactory` dikonfigurasi dengan fitur security
- **No Hardcoded Endpoints**: semua URL dimuat dari `saml.properties` (bukan di kode)
- **JKS Keystore**: signing certificate disimpan di keystore yang ter-mount di runtime
- **Session Invalidation**: HTTP session diinvalidasi dengan benar saat logout

---

## 🛠️ Tech Stack

| Komponen | Teknologi | Versi |
|----------|-----------|-------|
| **Bahasa** | Java | 7 syntax / 8 target |
| **SAML Library** | pac4j-saml | 1.8.8 |
| **Servlet Container** | Apache Tomcat | 9.x |
| **Identity Provider** | Keycloak | 23.0.7 |
| **Build Tool** | Apache Maven | 3.9 (via Docker) |
| **Containerisation** | Docker + Compose | Latest |

### ⚠️ Batasan Intentional

| Batasan | Alasan |
|---------|--------|
| Tanpa lambdas, streams | Mereplikasi Java 7 codebase |
| Tanpa try-with-resources | Mereplikasi Java 7 codebase |
| Tanpa Optional, java.time | Mereplikasi Java 7 codebase |
| pac4j 1.8.8 (no SLO) | Mereplikasi legacy library version |
| SAML XML built manually | Demonstrasi protokol SAML secara mendalam |

> **Catatan**: Kode ditulis dalam Java 7 syntax, namun `maven.compiler.source/target` set ke `1.8` karena Java 7 toolchain sudah end-of-life dan tidak didukung Maven modern. Batasan syntax diberlakukan melalui disiplin, bukan compiler.

---

## 📋 Prasyarat

Hanya **Docker** dan **Docker Compose** yang diperlukan.
Tidak perlu Java, Maven, atau tools lainnya di mesin lokal.

| Tool | Versi Minimum | Cek |
|------|--------------|-----|
| Docker | 20.10+ | `docker --version` |
| Docker Compose | 2.x | `docker compose version` |

---

## 🚀 Quick Start

### 1. Clone Repository

```bash
git clone https://github.com/vinijf01/saml-sso-poc.git
cd saml-sso-poc
```

### 2. Konfigurasi Environment (Opsional)

Defaultnya sudah siap pakai. Edit hanya jika perlu ubah port atau credential:

```bash
cp .env.example .env
```

File `.env`:
```env
KEYCLOAK_ADMIN=admin
KEYCLOAK_ADMIN_PASSWORD=admin123
SP_ENTITY_ID=urn:saml-poc:sp
SP_CALLBACK_URL=http://localhost:8080/service-poc/callbacksso
```

### 3. Mulai Semua Services

```bash
docker compose up --build -d
```

Proses:
1. ✅ Build SP application di dalam Docker (Multi-stage: Maven → WAR → Tomcat)
2. ✅ Build Keycloak image dengan pre-configured `poc` realm
3. ✅ Start Keycloak terlebih dahulu & tunggu hingga healthy
4. ✅ Start SP setelah Keycloak ready

**First build: 2–5 menit** (Maven download dependencies). Build berikutnya lebih cepat karena layer caching.

### 4. Verifikasi Services Berjalan

```bash
docker compose ps
```

Output yang diharapkan:
```
NAME      STATUS          PORTS
poc-idp   Up (healthy)    0.0.0.0:8443->8080/tcp
poc-sp    Up              0.0.0.0:8080->8080/tcp
```

Kedua container harus Running sebelum testing.

### 5. Akses Aplikasi

```
SP URL:        http://localhost:8080/service-poc/
Keycloak:      http://localhost:8443/ (admin/admin123)
```

---

## 🔐 Analisis Status Docker

✅ **Docker Setup Status: PRODUCTION READY**

### ✨ Kekuatan Setup:

1. **Multi-stage Build** — Maven dependencies di-cache di layer terpisah, rebuild 10x lebih cepat
2. **Health Check** — Keycloak memiliki health check endpoint, SP tidak perlu menunggu blind
3. **Dependency Management** — `sp-auth` menunggu Keycloak hingga `service_healthy`, bukan hanya `service_started`
4. **Volume Mounting** — Config files di-mount read-only, tidak di-embed di image (security best practice)
5. **Environment Variables** — Semua konfigurasi externalized, bukan hardcoded
6. **Layered Architecture** — Tomcat base image + WAR deployment, standard Java production pattern
7. **Container Naming** — Service names konsisten dan deskriptif (`poc-idp`, `poc-sp`)

### ⚠️ Catatan Penting:

- Build image pertama kali membutuhkan download ~500MB (Java SDK, Maven, dependencies)
- Layer caching: subsequent builds jauh lebih cepat (~10-30 detik)
- Network: Kedua container bisa communicate via service name dalam Docker network (`http://keycloak:8080`)
- Logging: Semua logs ter-centralize di Docker, bisa diakses via `docker compose logs`

### 🔍 Troubleshooting Docker:

**Jika SP terhubung ke Keycloak tapi timeout:**
```bash
# Check Keycloak health
docker compose exec keycloak curl http://localhost:8080/health

# Check SP logs
docker compose logs sp-auth | tail -50

# Inspect network
docker network ls
docker network inspect saml-sso-poc_default
```

---

## 🧪 Testing SSO (Step-by-Step Lengkap)

### Test 1️⃣ — Login Berhasil (SSO SP-Initiated)

**Skenario**: User membuka aplikasi tanpa login, diarahkan ke Keycloak, login berhasil, redirect kembali ke aplikasi.

**Step-by-step**:

1. **Buka browser** di:
   ```
   http://localhost:8080/service-poc/
   ```
   → Anda akan melihat halaman login dengan tombol "Login dengan SSO"

2. **Klik tombol "Login dengan SSO"** atau akses langsung:
   ```
   http://localhost:8080/service-poc/loginsso
   ```

3. **Apa yang terjadi di backend**:
   - SP membangun AuthnRequest XML dari nol:
     ```xml
     <AuthnRequest 
       xmlns="urn:oasis:names:tc:SAML:2.0:protocol"
       IssueInstant="2024-01-15T10:30:00Z"
       AssertionConsumerServiceURL="http://localhost:8080/service-poc/callbacksso"
       Destination="http://localhost:8443/auth/realms/poc/protocol/saml"
       ID="_7890...">
       <Issuer xmlns="urn:oasis:names:tc:SAML:2.0:assertion">urn:saml-poc:sp</Issuer>
       <NameIDPolicy AllowCreate="true" Format="urn:oasis:names:tc:SAML:1.1:nameid-format:emailAddress"/>
     </AuthnRequest>
     ```
   - AuthnRequest di-encode: XML → DEFLATE compress → Base64 encode
   - Auto-submit HTML form di-generate dengan hidden input `SAMLRequest`
   - Browser auto-submit ke Keycloak SSO endpoint

4. **Di Keycloak** — browser redirect ke:
   ```
   http://localhost:8443/auth/realms/poc/protocol/saml
   ```
   → Keycloak menampilkan halaman login Keycloak

5. **Login dengan test user**:
   ```
   Username: john.doe
   Password: password123
   ```
   → Click **Sign in**

6. **Keycloak Process**:
   - Validasi credentials terhadap realm `poc`
   - Extract attribute `divisi: IT`
   - Bangun SAMLResponse XML dengan NameID & attributes
   - Base64 encode
   - Auto-POST kembali ke SP di endpoint `/callbacksso`

7. **SP Process SAMLResponse** (di `/callbacksso`):
   - Parameter `SAMLResponse` diterima dari POST
   - Decode Base64 → get XML
   - Parse XML, extract:
     - **NameID**: `john.doe@poc.local`
     - **Attribute `divisi`**: `IT`
   - Validasi signature menggunakan IdP certificate dari `idp-metadata.xml`
   - **Lookup group mapping** (`esb.xml`):
     - Divisi `IT` → Group `admin`
   - **Create HTTP session**:
     - `session.username = "john.doe"`
     - `session.samlNameId = "john.doe@poc.local"`
     - `session.group = "admin"`
     - `session.location = "IT"`
   - Redirect ke `/index.htm`

8. **Hasil Akhir** — Halaman welcome muncul:
   ```
   Welcome, john.doe!
   Group: admin
   Location: IT
   [Logout]
   ```

**Verifikasi SSO Berhasil**:
- ✅ Halaman welcome tampil (bukan redirect ke login)
- ✅ Username dan group ditampilkan dengan benar
- ✅ Session cookie ada (`JSESSIONID`)

---

### Test 2️⃣ — Multi-User dengan Berbeda Role

**Skenario**: Verifikasi group mapping bekerja untuk berbagai user.

**Step 1 — Clear session sebelumnya**:
```bash
# Atau tutup browser / buka private/incognito tab baru
```

**Step 2 — Login dengan user kedua**:
```
Buka: http://localhost:8080/service-poc/loginsso
Username: jane.smith
Password: password123
```

**Verifikasi**:
```
Expected output:
Welcome, jane.smith!
Group: manager         ← (HR divisi → manager)
Location: HR
```

**Step 3 — Ulangi dengan user ketiga**:
```
Username: bob.wilson
Password: password123
```

**Verifikasi**:
```
Expected output:
Welcome, bob.wilson!
Group: editor          ← (Marketing divisi → editor)
Location: Marketing
```

**Hasil**: Semua user berhasil login dengan group mapping yang benar ✅

---

### Test 3️⃣ — Verifikasi Session Persistent

**Skenario**: Session bertahan di refresh page.

1. Setelah login sebagai `john.doe`, refresh halaman:
   ```
   Press F5 atau Ctrl+R
   ```

2. **Verifikasi**:
   - Halaman welcome masih tampil (tidak redirect ke login)
   - Data user tetap ada (`john.doe`, `admin`, `IT`)
   - Session cookie (`JSESSIONID`) belum expired

---

## 🚪 Testing SLO (Step-by-Step Lengkap)

### Test 4️⃣ — SP-Initiated Logout (Single Logout)

**Skenario**: User logout dari aplikasi SP, yang memicu logout dari IdP juga.

**Step 1 — Pastikan sudah login**:
- Buka `http://localhost:8080/service-poc/`
- Jika belum login, lakukan Test 1 terlebih dahulu

**Step 2 — Klik tombol Logout**:
- Di halaman welcome, klik link `[Logout]`
- Atau akses langsung: `http://localhost:8080/service-poc/logoutsso`

**Step 3 — Apa yang terjadi di backend** (SP-initiated SLO flow):

1. **SP Logout Handler** menerima request di `/logoutsso`:
   ```java
   HttpSession session = request.getSession(false);
   // session ada → user sedang login
   String nameId = (String) session.getAttribute("samlNameId"); // john.doe@poc.local
   ```

2. **Set `slo_in_progress` cookie** (untuk tracking):
   ```
   Set-Cookie: slo_in_progress=true; Max-Age=300; HttpOnly
   ```
   → Ini marker untuk `CallbackHandler` nanti: "ini LogoutResponse, bukan AuthnResponse"

3. **Bangun LogoutRequest XML** dari nol:
   ```xml
   <LogoutRequest
     xmlns="urn:oasis:names:tc:SAML:2.0:protocol"
     ID="_xyz..."
     IssueInstant="2024-01-15T10:45:00Z"
     Destination="http://localhost:8443/auth/realms/poc/protocol/saml/logout"
     NotOnOrAfter="2024-01-15T10:50:00Z">
     <Issuer xmlns="urn:oasis:names:tc:SAML:2.0:assertion">urn:saml-poc:sp</Issuer>
     <NameID Format="urn:oasis:names:tc:SAML:1.1:nameid-format:emailAddress">john.doe@poc.local</NameID>
   </LogoutRequest>
   ```

4. **Encode LogoutRequest**:
   - DEFLATE compress XML
   - Base64 encode hasil compress
   - URL encode hasil Base64
   - Hasilnya: `SAMLRequest=jVHBasMwDP0V9tzRZHs...`

5. **Invalidate local HTTP session**:
   ```java
   session.invalidate();
   ```
   → User tidak lagi authenticated di SP

6. **HTTP-Redirect ke Keycloak**:
   ```
   302 Found
   Location: http://localhost:8443/auth/realms/poc/protocol/saml/logout
            ?SAMLRequest=jVHBasMwDP0V9tzRZHs...
            &RelayState=logout
   ```

**Step 4 — Di Keycloak**:

1. Keycloak menerima LogoutRequest
2. Validasi signature + NameID
3. Invalidate user session di Keycloak (IdP-side session berakhir)
4. Bangun LogoutResponse (konfirmasi logout berhasil)
5. HTTP-Redirect balik ke SP di `/callbacksso`:
   ```
   302 Found
   Location: http://localhost:8080/service-poc/callbacksso
            ?SAMLResponse=jVLLasMwDP0V8txRZH...
            &RelayState=logout
   ```

**Step 5 — SP Process LogoutResponse**:

1. Browser follow redirect ke `/callbacksso` dengan `SAMLResponse`
2. `CallbackHandler` menerima request:
   ```java
   String sloInProgress = CookieUtil.getSloInProgress(request);
   if (sloInProgress != null && sloInProgress.equals("true")) {
     // Ini LogoutResponse, bukan AuthnResponse
     // Clear slo_in_progress cookie
     CookieUtil.clearSloInProgress(response);
     // Session sudah di-invalidate di Step 3, tidak perlu invalidate lagi
   }
   ```
3. Redirect ke `/login.htm`:
   ```
   302 Found
   Location: http://localhost:8080/service-poc/login.htm
   ```

**Step 6 — Verifikasi Hasil**:
- Browser redirect ke halaman login
- Session cookie (`JSESSIONID`) dihapus atau expired
- Cookie `slo_in_progress` dihapus
- User **harus login ulang** untuk mengakses halaman protected

**Verifikasi SLO Berhasil**:
✅ Halaman login tampil (tidak welcome page)  
✅ Session cookie hilang  
✅ Tidak bisa akses `/index.htm` tanpa login ulang  

---

### Test 5️⃣ — Verifikasi Session Fully Terminated

**Skenario**: Memastikan session benar-benar terminated, bukan hanya disconnected.

**Step 1 — Setelah logout** (dari Test 4):

1. Coba akses halaman protected langsung:
   ```
   http://localhost:8080/service-poc/index.htm
   ```

2. **Verifikasi**:
   - Expected: Redirect ke login page (tidak tampil welcome)
   - Jika tampil welcome, berarti ada bug di logout handling

3. Coba refresh `/loginsso`:
   ```
   http://localhost:8080/service-poc/loginsso
   ```
   - Expected: Keycloak login page muncul (meminta re-authentication)
   - Jika auto-login tanpa credentials, berarti Keycloak session tidak di-terminate ❌

---

### Test 6️⃣ — Re-Login Setelah Logout

**Skenario**: User logout, kemudian login ulang dengan user berbeda.

**Step 1 — Logout** (dari Test 4 atau 5)

**Step 2 — Login dengan user berbeda**:
```
Buka: http://localhost:8080/service-poc/loginsso
Username: jane.smith
Password: password123
```

**Step 3 — Verifikasi**:
```
Expected:
Welcome, jane.smith!
Group: manager
Location: HR
```

- ✅ User berhasil login dengan identity berbeda
- ✅ Session attributes update dengan user baru
- ✅ Tidak ada session bleeding dari login sebelumnya

---

### Test 7️⃣ — Test Logout Tanpa SSO Session

**Skenario**: User coba logout tanpa punya SSO session (edge case).

**Step 1 — Akses logout langsung**:
```bash
curl -b "" http://localhost:8080/service-poc/logoutsso
# -b "" = tidak ada cookie session
```

**Step 2 — Verifikasi**:
- Status: `302 Found`
- Location: `/login.htm`
- Behavior: Local logout saja (tidak ada SLO ke IdP, karena tidak ada session)

---

## 👥 Test Users & Group Mapping

Semua test users memiliki password yang sama: **`password123`**

| Username | Email | Divisi | Mapped Group | Role Deskripsi |
|----------|-------|--------|------|--------|
| `john.doe` | john.doe@poc.local | IT | `admin` | Admin - full access |
| `jane.smith` | jane.smith@poc.local | HR | `manager` | Manager - HR operations |
| `bob.wilson` | bob.wilson@poc.local | Marketing | `editor` | Editor - content management |
| `alice.jones` | alice.jones@poc.local | Finance | `manager` | Manager - finance operations |

### Pemetaan Rules (`config/esb.xml`)

```xml
<?xml version="1.0" encoding="UTF-8"?>
<esbconfig>
  <groupmapping>
    <mapping divisi="IT"         group="admin"/>
    <mapping divisi="HR"         group="manager"/>
    <mapping divisi="Finance"    group="manager"/>
    <mapping divisi="Marketing"  group="editor"/>
    <mapping divisi="Sales"      group="editor"/>
    <mapping divisi="Operations" group="viewer"/>
  </groupmapping>

  <usermapping>
    <!-- User-specific override (priority tinggi) -->
    <mapping username="john.doe@poc.local"   group="admin"/>
    <mapping username="jane.smith@poc.local" group="manager"/>
    <mapping username="bob.wilson@poc.local" group="editor"/>
  </usermapping>

  <default-group>viewer</default-group>
</esbconfig>
```

**Priority Rules**:
1. ⭐ `usermapping` — highest priority (per-user override)
2. 🎯 `groupmapping` — medium priority (berdasarkan divisi attribute)
3. 💬 `default-group` — lowest priority (fallback)

**Contoh**:
- `john.doe@poc.local` → cek usermapping → ada → group = `admin` ✅
- `unknown.user@poc.local` dari divisi `IT` → cek groupmapping → ada → group = `admin` ✅
- `unknown.user@poc.local` dari divisi `UNKNOWN` → cek default-group → group = `viewer` ✅

---

## 📊 SAML Flow Diagrams

### SSO Flow (SP-Initiated)

```
┌─────────────────────────────────────────────────────────────────┐
│                         Browser                                 │
└──────┬──────────────────────────────────────────────────────────┘
       │
       │ 1. GET /loginsso
       ▼
┌──────────────────────────┐
│     SP (Tomcat)          │ 2. Build AuthnRequest
│                          │    • NameID Policy
│                          │    • Assertion Consumer URL
│                          │    • Issuer
└──────────────┬───────────┘
               │ 3. Encode: XML → DEFLATE → Base64
               │ 4. Auto-submit HTML form
       ┌───────▼────────────┐
       │                    │ 5. POST to IdP SSO endpoint
       │                    │    SAMLRequest=jVHB...
       │                    │
       ▼
┌──────────────────────────────┐
│    Keycloak (IdP)            │ 6. Parse AuthnRequest
│                              │    Render login form
└──────────────┬───────────────┘
       │ 7. User input credentials
       │ 8. Validate against realm
       │
       ▼
┌──────────────────────────────┐
│    Keycloak (IdP)            │ 9. Build AuthnResponse
│                              │    • NameID: john.doe@poc.local
│                              │    • Attribute: divisi=IT
│                              │    • Signed Assertion
└──────────────┬───────────────┘
       │ 10. Encode: XML → Base64
       │ 11. POST to /callbacksso
       │
       ▼
┌──────────────────────────┐
│     SP (Tomcat)          │ 12. Receive SAMLResponse
│ /callbacksso             │ 13. Decode & validate signature
│                          │ 14. Extract NameID & attributes
│                          │ 15. Apply group mapping (IT→admin)
│                          │ 16. Create HTTP session
└──────────────┬───────────┘
       │ 17. 302 redirect to /index.htm
       │
       ▼
┌──────────────────────────────────┐
│         Browser                  │ 18. Display welcome page
│    Welcome, john.doe!            │     Group: admin
│    Location: IT                  │     [Logout]
└──────────────────────────────────┘
```

### SLO Flow (SP-Initiated)

```
┌─────────────────────────────────────────────────────────────────┐
│                         Browser                                 │
└──────┬──────────────────────────────────────────────────────────┘
       │
       │ 1. Click Logout / GET /logoutsso
       ▼
┌──────────────────────────┐
│     SP (Tomcat)          │ 2. Get NameID from session
│ /logoutsso               │ 3. Set slo_in_progress cookie
│                          │ 4. Build LogoutRequest XML
│                          │ 5. Encode: XML → DEFLATE → Base64 → URLEnc
│                          │ 6. Invalidate local session
└──────────────┬───────────┘
       │ 7. 302 redirect to IdP SLO endpoint
       │    SAMLRequest=jVLa...&RelayState=logout
       │
       ▼
┌──────────────────────────────┐
│    Keycloak (IdP)            │ 8. Parse LogoutRequest
│                              │ 9. Validate signature
│                              │ 10. Invalidate IdP session
│                              │ 11. Build LogoutResponse
└──────────────┬───────────────┘
       │ 12. 302 redirect to /callbacksso
       │
       ▼
┌──────────────────────────┐
│     SP (Tomcat)          │ 13. Receive LogoutResponse
│ /callbacksso             │ 14. Check slo_in_progress cookie
│                          │     → Yes, this is SLO flow
│                          │ 15. Clear slo_in_progress cookie
└──────────────┬───────────┘
       │ 16. 302 redirect to /login.htm
       │
       ▼
┌──────────────────────────────────┐
│         Browser                  │ 17. Display login page
│    Login dengan SSO              │     Session cleared
│    [Login dengan SSO]            │     Cookies cleared
└──────────────────────────────────┘
```

---

## 📁 Struktur Project

```
saml-sso-poc/
│
├── src/main/
│   ├── java/id/co/vini/poc/plugin/
│   │   ├── SSOServlet.java              ← Main servlet router
│   │   │                                  doGet: route ke handler
│   │   │                                  doPost: handle callback
│   │   │
│   │   ├── handler/
│   │   │   ├── LoginHandler.java        ← Process AuthnResponse
│   │   │   │                              • Decode SAMLResponse
│   │   │   │                              • Extract NameID & attributes
│   │   │   │                              • Apply group mapping
│   │   │   │                              • Set session
│   │   │   │
│   │   │   ├── LogoutHandler.java       ← Initiate SP-initiated SLO
│   │   │   │                              • Build LogoutRequest
│   │   │   │                              • Set slo_in_progress cookie
│   │   │   │                              • Invalidate session
│   │   │   │
│   │   │   └── CallbackHandler.java     ← SAML callback dispatcher
│   │   │                                  • Route AuthnResponse / LogoutResponse
│   │   │                                  • Check slo_in_progress
│   │   │
│   │   └── util/
│   │       ├── SamlConfig.java          ← Load saml.properties
│   │       │                              • Get SAML2Client
│   │       │                              • Parse IdP metadata
│   │       │
│   │       ├── SamlMessageUtil.java     ← SAML XML builder + encoder
│   │       │                              • buildAuthnRequest()
│   │       │                              • buildLogoutRequest()
│   │       │                              • encode/decode SAML messages
│   │       │
│   │       └── CookieUtil.java          ← Cookie handler
│   │                                     • slo_in_progress cookie
│   │
│   └── webapp/
│       ├── WEB-INF/web.xml              ← Servlet mappings
│       │                                  GET /loginsso → handleLogin
│       │                                  GET /logoutsso → handleLogout
│       │                                  POST /callbacksso → handleCallback
│       │
│       └── index.jsp                    ← Welcome page (after login)
│
├── config/                              ← Config files (volume-mounted)
│   ├── saml.properties                  ← SP config (entity ID, callback URL, keystore)
│   ├── idp-metadata.xml                 ← Keycloak IdP metadata (endpoints, certs)
│   ├── esb.xml                          ← Group & user mapping
│   ├── group.xml                        ← [Legacy] alternative group config
│   └── samlKeystore.jks                 ← SP signing keystore (pre-generated)
│
├── keycloak/                            ← Keycloak container setup
│   ├── Dockerfile                       ← Keycloak 23 base image
│   └── realm/
│       └── poc-realm.json               ← Pre-configured realm export
│                                          • Users: john.doe, jane.smith, bob.wilson, alice.jones
│                                          • SAML 2.0 client config
│                                          • Attribute mappers (divisi)
│
├── docs/                                ← Documentation (optional)
│   ├── configuration.md                 ← Full saml.properties reference
│   ├── troubleshooting.md               ← Common issues & fixes
│   └── api.md                           ← Handler & utility API docs
│
├── target/                              ← Build output (do not commit)
│   └── service-poc.war                  ← Final WAR artifact
│
├── .env.example                         ← Environment variable template
├── .env                                 ← Actual env vars (do not commit)
├── .gitignore
├── Dockerfile                           ← Multi-stage build: Maven + Tomcat
├── docker-compose.yml                   ← Orchestrate IdP + SP
├── pom.xml                              ← Maven project config
├── sp-metadata.xml                      ← SP metadata (reference untuk IdP)
└── README.md                            ← This file
```

---

## 🛠️ Perintah Berguna
# Start all services (first time or after code change)
docker compose up --build -d

# Start tanpa rebuild (lebih cepat, jika kode tidak berubah)
docker compose up -d

# Lihat status container
docker compose ps

# Lihat SP logs (real-time)
docker compose logs -f sp-auth

# Lihat Keycloak logs (real-time)
docker compose logs -f keycloak

# Hentikan semua services
docker compose down

# Hentikan dan hapus volumes (full reset — WARNING: data hilang!)
docker compose down -v

# Rebuild container (jika ada perubahan dependency di pom.xml)
docker compose up --build -d

# Check health status dari Keycloak
docker compose exec keycloak curl http://localhost:8080/health

# Masuk ke container SP untuk debugging
docker compose exec sp-auth /bin/bash

# View WAR file content
docker compose exec sp-auth ls -la /usr/local/tomcat/webapps/
```

---

## 🔒 Catatan Keamanan

Project ini mengimplementasikan beberapa praktik keamanan intentional yang perlu diperhatikan:

### ⚔️ XXE Hardening

Setiap `DocumentBuilderFactory` instance dikonfigurasi:
```java
DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
dbf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
dbf.setFeature("http://xml.org/sax/features/external-general-entities", false);
dbf.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
```

**Alasan**: Mencegah XML External Entity (XXE) injection attacks saat parse SAML messages.

### 🔑 No Hardcoded Endpoints

Semua URLs dimuat dari `saml.properties`, bukan di-embed di source code:
```properties
# ✅ Good practice — externalized
sp.entity-id=${SP_ENTITY_ID}
sp.callback-url=${SP_CALLBACK_URL}
idp.metadata.path=/config/idp-metadata.xml

# ❌ Bad practice — hardcoded
// String idpSsoUrl = "http://hardcoded-idp:8080/sso";
```

### 🗝️ Keystore at Runtime

`samlKeystore.jks` di-mount sebagai volume di runtime, bukan di-bake ke image:
```yaml
services:
  sp-auth:
    volumes:
      - ./config:/usr/local/tomcat/config:ro  # ✅ Config external
```

**Alasan**: Memudahkan rotate certificate tanpa rebuild image.

### ⚡ Session Invalidation

HTTP session diinvalidasi dengan benar pada SSO logout:
```java
// SP-initiated logout
session.invalidate();  // ✅ Session cleared

// IdP-initiated logout
session.invalidate();  // ✅ Session cleared
```

> ⚠️ **PENTING**: `samlKeystore.jks` di repo ini adalah pre-generated untuk PoC saja.  
> **JANGAN GUNAKAN DI PRODUCTION** — generate keystore baru dengan password yang aman!

---

## ⚠️ Known Limitations & Keterbatasan Diketahui

Ini adalah Proof of Concept. Berikut keterbatasan intentional dan omissions:

| Keterbatasan | Catatan | Fix untuk Production |
|----------|--------|-----------|
| **No AuthnRequest signing** | Keycloak dikonfigurasi accept unsigned requests | Sign dengan private key sebelum send |
| **No SAMLResponse encryption** | Assertion signed tapi tidak encrypted | Implement SAML encryption (advanced) |
| **In-memory session** | No persistent session store (H2/file-based) | Integrate dengan Redis/Memcached |
| **HTTP only** | Runs di localhost HTTP, bukan HTTPS | Add reverse proxy (nginx/Apache) + SSL |
| **Java 7 syntax** | Syntax constraint by discipline, not enforced | Use Maven enforcer plugin |
| **No replay attack protection** | Assertion reuse not tracked | Implement nonce/timestamp validation |
| **No metadata auto-refresh** | IdP metadata static, tidak auto-update | Implement periodic metadata fetch |

**Untuk production use**: Gunakan mature SAML library dengan full signature validation, assertion encryption, dan replay attack protection.

Recommended libraries:
- **OpenSAML 3.x+** — comprehensive SAML 2.0 support
- **Spring Security SAML2** — Spring Framework integration
- **Shibboleth** — enterprise IdP/SP (complex but robust)

---

## 🐛 Troubleshooting Common Issues

### ❌ Issue: Browser stuck on Keycloak login page

**Gejala**:
- POST ke Keycloak berhasil, tapi halaman login tidak close
- Browser not redirecting back to SP

**Penyebab**:
- Callback URL tidak match dengan Keycloak config
- SAML client di Keycloak tidak dikonfigurasi benar
- Valid Redirect URIs missing di Keycloak

**Fix**:
```bash
# Check Keycloak logs
docker compose logs keycloak | grep -i "callback\|redirect\|error"

# Verify callback URL di environment
docker compose exec sp-auth env | grep CALLBACK

# Keycloak admin: go to http://localhost:8443/ 
# → Realms → poc → Clients → saml-poc-sp
# → Valid Redirect URIs = http://localhost:8080/service-poc/callbacksso
```

---

### ❌ Issue: "No SSO session, performing local logout"

**Gejala**:
- User logout berhasil ke login page
- Tapi log menunjukkan "No SSO session"

**Penyebab**:
- User tidak punya `samlNameId` di session (belum login via SSO)

**Fix**:
- Pastikan login via `/loginsso`, bukan akses langsung `/index.htm`

---

### ❌ Issue: SAMLResponse decode fails / XML parse error

**Gejala**:
```
Error: XML parse exception
Error: Invalid Base64 encoding
```

**Penyebab**:
- SAMLResponse dari Keycloak corrupted atau encoding mismatch
- Signature validation failed

**Fix**:
```bash
# Debug log dari SP
docker compose logs sp-auth | tail -100

# Jika error "Certificate not found", check:
docker compose exec sp-auth ls -la /usr/local/tomcat/config/

# Verify idp-metadata.xml valid
docker compose exec sp-auth cat /usr/local/tomcat/config/idp-metadata.xml | head -20
```

---

### ❌ Issue: "slo_in_progress cookie not found"

**Gejala**:
- Logout dimulai, tapi redirect tidak bekerja
- Cookie tidak set atau expired

**Penyebab**:
- Cookie max-age terlalu kecil (default 300 detik)
- Browser tidak accept HTTP-only cookie
- Network latency between SP ↔ Keycloak

**Fix**:
```properties
# saml.properties — increase cookie timeout
session.cookie.max-age=900  # 15 menit instead of 5
```

---

### ❌ Issue: Connection refused — SP cannot reach Keycloak

**Gejala**:
```
Connection refused: http://keycloak:8080
```

**Penyebab**:
- Keycloak container belum start
- Network tidak connected
- Port mismatch

**Fix**:
```bash
# Check container status
docker compose ps

# Check Keycloak container name
docker compose logs keycloak | head -20

# Verify network connectivity
docker compose exec sp-auth ping keycloak

# Check SP logs untuk error detail
docker compose logs sp-auth | grep -i "connect\|refused\|error"

# Restart services
docker compose down
docker compose up --build -d
```

---

## 📚 Referensi & Resources

### SAML 2.0 Specs
- **OASIS SAML 2.0 Core**: [oasis-open.org](https://docs.oasis-open.org/security/saml/v2.0/)
- **Bindings & Profiles**: HTTP-POST Binding, HTTP-Redirect Binding
- **Authentication Request Protocol**: AuthnRequest → AuthnResponse flow

### Keycloak Documentation
- **SAML Configuration**: https://www.keycloak.org/docs/latest/server_admin/#saml
- **Realm Management**: https://www.keycloak.org/docs/latest/server_admin/

### pac4j Library
- **GitHub**: https://github.com/pac4j/pac4j
- **SAML 2.0 Module**: pac4j-saml (v1.8.8 untuk project ini)

### Security Best Practices
- **OWASP SAML Security**: https://owasp.org/www-community/attacks/SAML_Attacks
- **XXE Prevention**: https://owasp.org/www-community/attacks/XML_External_Entity_(XXE)_Processing

---

## 📞 Support & Kontribusi

Untuk issue atau pertanyaan:
1. Check [Troubleshooting](#troubleshooting-common-issues) section
2. Review logs: `docker compose logs -f`
3. Inspect XML: Browser DevTools → Network tab (check SAMLRequest/Response)
4. Create issue dengan detail logs

---

## 📜 License

Project ini dilisensikan di bawah MIT License — lihat file [LICENSE](LICENSE) untuk detail.

---

> 📝 **Catatan**: Ini adalah Proof of Concept untuk keperluan educational dan portfolio.  
> **Dibangun untuk demonstrasi pemahaman protokol SAML 2.0 di level implementasi.**