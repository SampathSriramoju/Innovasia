# Bol Kisan — Physical Android Device & Local Wi-Fi Backend Integration Guide

This guide details how to run the Bol Kisan FastAPI backend on your Windows laptop and connect your physical Android phone (or Android emulator) over the same local Wi-Fi network.

Both **ONLINE** and **OFFLINE** modes operate independently with strict origin attribution:
- `🟢 ONLINE (Gemini + RAG)`: Online FastAPI backend query using Google Gemini 2.5 Flash + pgvector CIBRC RAG.
- `🟢 ONLINE (ICAR Fallback)`: Online FastAPI backend fallback when Gemini is degraded.
- `⚡ OFFLINE SLM (Qwen3-0.6B)`: On-device LiteRT-LM neural inference running the bundled Qwen3-0.6B INT4 model in airplane mode.
- `📦 OFFLINE KNOWLEDGE FALLBACK (ICAR SQLite)`: On-device deterministic Room database fallback.

---

## 1. Network Prerequisites & Windows Firewall

Your physical Android phone and Windows laptop must be connected to the **same Wi-Fi network**.

### Step 1.1: Verify Windows Network Profile is Private
Open PowerShell and check your network profile:
```powershell
Get-NetConnectionProfile
```
If `NetworkCategory` is `Public`, change it to `Private` to allow LAN discovery:
```powershell
Set-NetConnectionProfile -InterfaceAlias "Wi-Fi" -NetworkCategory Private
```

### Step 1.2: Add Scoped Windows Firewall Inbound Rule for Port 8000
Run PowerShell **as Administrator** once:
```powershell
New-NetFirewallRule -DisplayName "Bol Kisan Backend Port 8000" -Direction Inbound -LocalPort 8000 -Protocol TCP -Action Allow -Profile Private
```

### Step 1.3: Find Laptop Wi-Fi IP Address
```powershell
(Get-NetIPAddress -InterfaceAlias "Wi-Fi" -AddressFamily IPv4).IPAddress
```
*Example Output:* `192.168.0.150`

---

## 2. Launching the FastAPI LAN Backend

The project includes pre-configured automation scripts that bind Uvicorn to `0.0.0.0:8000`.

### Start the Server:
```powershell
.\backend\scripts\start_lan_server.ps1
```
The script automatically:
1. Validates the Python `.venv` environment and dependencies.
2. Verifies PostgreSQL database connectivity (`SELECT 1`).
3. Detects the active Wi-Fi IPv4 address (e.g. `192.168.0.150`).
4. Checks that port `8000` is free.
5. Displays the exact connection URLs:
   - **Physical Phone URL:** `http://192.168.0.150:8000/api/v1/`
   - **Emulator URL:** `http://10.0.2.2:8000/api/v1/`
   - **Health Check:** `http://192.168.0.150:8000/health`
6. Starts Uvicorn on `0.0.0.0:8000`.

### Stop the Server:
```powershell
.\backend\scripts\stop_lan_server.ps1
```

---

## 3. Backend Health Check Verification

Before opening the mobile app, verify from your laptop browser or terminal that the health endpoints are answering:

```powershell
Invoke-RestMethod -Uri "http://localhost:8000/health"
```
Or test the LAN IP:
```powershell
Invoke-RestMethod -Uri "http://192.168.0.150:8000/api/v1/health"
```

**Expected JSON Response (HTTP 200):**
```json
{
  "status": "healthy",
  "version": "1.2.0",
  "database": {
    "status": "connected",
    "dialect": "postgresql"
  },
  "ai_provider": {
    "gemini_configured": true,
    "model_name": "gemini-2.5-flash"
  }
}
```

---

## 4. Connecting Your Physical Android Device

### Step 4.1: Install Debug APK
Build and install the debug APK onto your phone (via USB debugging or file transfer):
```powershell
cd apps/farmer-android
.\gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### Step 4.2: Configure Developer Backend Settings
1. Open **Bol Kisan** on your physical phone.
2. In the top app bar, tap either:
   - The **Online/Offline status pill** (`🟢 ONLINE` or `🟠 OFFLINE`), or
   - The **Backend Settings icon** (`Icons.Default.Dns`).
3. The **Backend LAN & Wi-Fi Settings** bottom sheet will open.
4. Tap the **Laptop Wi-Fi (192.168.0.150)** quick preset, or manually enter:
   `http://192.168.0.150:8000/api/v1/`
5. Tap **Test Connection**:
   - The app actively queries `GET /api/v1/health` using `SystemClock.elapsedRealtime()` monotonic timing.
   - You will see:
     - Status: `CONNECTED`
     - Monotonic Latency: `12 ms`
     - Backend Version: `1.2.0`
     - DB: `connected` | AI: `Ready`
6. Tap **Save & Apply**:
   - Persists the endpoint into app preferences (`bolkisan_network_prefs`) and immediately updates the active Retrofit client.
7. Tap **1-Tap Login as Demo Farmer**:
   - Authenticates as test farmer `Ramesh Patel` (`9876543210`) with dev OTP `123456`.
   - Acquires the JWT Bearer token required for FastAPI online voice queries.

---

## 5. End-to-End Verification Scenarios

### Scenario A: Online Mode (Phone + Laptop Backend + Internet)
1. Ensure laptop server is running (`start_lan_server.ps1`).
2. Phone is connected to Wi-Fi with internet access.
3. In Bol Kisan, ask an agricultural question:
   > *"మిరపలో తామర పురుగుల నివారణ ఏమిటి?"* (How to control thrips in chilli?)
4. **Verification:**
   - Response arrives from laptop FastAPI backend via Gemini 2.5 Flash + CIBRC RAG.
   - Badge displays: `🟢 ONLINE (Gemini + RAG)`
   - CIBRC / ICAR package citations are shown.

### Scenario B: Local LAN Without Internet (Private Offline Router)
1. Disconnect laptop from WAN internet, but keep phone and laptop on the private Wi-Fi router.
2. Ask a question.
3. **Verification:**
   - App connects to local laptop backend at `192.168.0.150:8000`.
   - If Gemini API call fails due to no WAN, backend automatically uses its local verified knowledge base.
   - Badge displays: `🟢 ONLINE (ICAR Fallback)`
   - Response is safe, verified and grounded.

### Scenario C: Full Offline Airplane Mode
1. Enable **Airplane Mode** on the Android phone (disconnect all Wi-Fi and Cellular).
2. Ask an agricultural question:
   > *"పత్తిలో గులాబీ రంగు పురుగు నివారణ ఏమిటి?"* (How to control pink bollworm in cotton?)
3. **Verification:**
   - Top status bar displays: `🟠 OFFLINE`.
   - App does not attempt network calls.
   - Model extracts and initializes locally via LiteRT-LM runtime.
   - Response is generated on-device with Qwen3-0.6B INT4 SLM.
   - Badge displays: `⚡ OFFLINE SLM (Qwen3-0.6B)` or `📦 OFFLINE KNOWLEDGE FALLBACK (ICAR SQLite)`.
   - Zero crashes, zero network leaks.

### Scenario D: Recovery Mode
1. Disable Airplane Mode and reconnect to Wi-Fi.
2. Ask another question.
3. **Verification:**
   - Top status bar returns to `🟢 ONLINE`.
   - Subsequent queries seamlessly route back to `🟢 ONLINE (Gemini + RAG)`.

---

## 6. Security Architecture Notice

- **Debug Builds:**
  - `apps/farmer-android/app/src/debug/res/xml/network_security_config.xml` permits cleartext HTTP only to private subnets (`192.168.0.0/16`, `10.0.0.0/8`, `172.16.0.0/12`) and emulator loopback (`10.0.2.2`).
  - Dynamic developer URL override is allowed.
- **Release Builds:**
  - `IS_PRODUCTION = true`.
  - Android Manifest enforces `android:usesCleartextTraffic="false"`.
  - Strict HTTPS endpoint `https://api.bolkisan.gov.in/api/v1/` is pinned.
  - Any attempt to use `http://` or private LAN IPs is rejected by `ApiClient.validateBaseUrl`.
