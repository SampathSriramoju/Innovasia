# BOL KISAN – Bug Tracking & Remediation Report

**Date:** 2026-11-08  
**Scope:** Full Stack (Android, Backend, Database, Safety Gate, Officer Dashboard)  
**Standard:** Root Cause Resolution with Dedicated Regression Test

---

## 1. Summary of Discovered & Resolved Issues

| Bug ID | Component | Severity | Description | Status |
| :---: | :--- | :---: | :--- | :---: |
| **BUG-001** | Android Emulator | Critical | Android Studio emulator API 37 failed on C: drive due to insufficient disk space (<7.4GB partition required). | **FIXED** |
| **BUG-002** | BlueStacks ADB | High | ADB daemon protocol version clash (client 36 vs server 41) caused connection abort on direct adb install. | **FIXED** |
| **BUG-003** | Android App | High | Missing unit tests for Kotlin DTOs and knapsack pump dilution calculations. | **FIXED** |
| **BUG-004** | Backend API | Medium | Stale daemon instance caused newly registered routers (`/disease`, `/weather`, `/mandi`) to return 404. | **FIXED** |
| **BUG-005** | Safety Gate | High | Regex false positive: phrases like "mix 6.6 g in water" erroneously flagged as unapproved chemical tank-mix. | **FIXED** |
| **BUG-006** | Officer Login | Medium | Missing required fields in test payload (`officer_code`, `phone_number`, `otp`) causing HTTP 422. | **FIXED** |
| **BUG-007** | Weather Router | Low | Subscripting `None` when accessing unkeyed advisory property on district weather response. | **FIXED** |
| **BUG-008** | Officer Dashboard | Low | TypeScript warning `TS6133` (unused `CheckCircle2` import) blocking clean production build. | **FIXED** |

---

## 2. Detailed Bug Analyses & Fixes

### BUG-001: Android Emulator Partition Allocation Failure on Drive C:
- **Symptom:** `FATAL | Not enough space to create userdata partition. Available: 6832.29 MB at ...\Medium_Phone.avd, need 12288.00 MB.`
- **Root Cause:** Android Studio default AVD directory `~/.android/avd` is on Drive `C:`, which had only ~6.7GB free space, while the AVD image requested 12.2GB.
- **Fix:** Relocated AVD folder to `D:\Android\avd\Medium_Phone.avd` (Drive `D:` has 116GB free). Created `D:\Android\avd\Medium_Phone.ini` pointing to the target path and exported `$env:ANDROID_AVD_HOME = "D:\Android\avd"`.
- **Verification:** Emulator booted cleanly without partition errors.

### BUG-002: BlueStacks Direct ADB Protocol Mismatch
- **Symptom:** `adb: connect error for write: closed` and `adb server version (41) doesn't match this client (36)`.
- **Root Cause:** BlueStacks internal emulator daemon requires matching client protocol, while Android SDK uses version 41. Furthermore, ADB bridge access was disabled by default in `bluestacks.conf` (`bst.enable_adb_access="0"`).
- **Fix:** Used BlueStacks native APK installer command line:
  `C:\Program Files\BlueStacks_nxt\HD-Player.exe --instance Pie64 --cmd installApk --filepath "<path_to_apk>"`.
- **Verification:** Executed with return code 0; app installed and launched instantly on screen via `HD-Player.exe --cmd launchApp --package "com.innovasia.bolkisan"`.

### BUG-003: Missing Android Unit Test Coverage
- **Symptom:** Gradle reported `Task :app:testDebugUnitTest NO-SOURCE`.
- **Root Cause:** Test files had not been placed in `apps/farmer-android/app/src/test/java/com/bolkisan/farmer/`.
- **Fix:** Implemented `DataModelsTest.kt` (testing Gson serialization of all remote DTOs) and `OfflineSafetyRulesTest.kt` (testing 15L knapsack dilution ratios for Emamectin Benzoate, Flubendiamide, Chlorantraniliprole, and Mancozeb).
- **Verification:** `gradlew.bat testDebugUnitTest` executed: **11 passed, 0 failures, 100% success rate**.

### BUG-005: Safety Regex False Positive on Single-Chemical Dilution
- **Symptom:** A prompt saying "mix 6.6 g in water" was flagged by `_check_tank_mix()` because the word "mix" occurred before a substance.
- **Root Cause:** Regex pattern `r"\bmix\b.*\b(with|and|in)\b"` matched general water dilution sentences.
- **Fix:** Refined regex to require at least two distinct active ingredients or agrochemical trade names when detecting tank mixtures.
- **Verification:** `test_post_generation_compliant_response_passes` and `test_unapproved_tank_mixture_in_query` both pass consistently.

### BUG-007: Weather Response KeyError in Integration Script
- **Symptom:** `TypeError: 'NoneType' object is not subscriptable` when printing `w_data.get('advisory')[:40]`.
- **Root Cause:** The district weather endpoint returns structured `forecast_5day` and `crop_advisories` lists rather than a flat `advisory` string.
- **Fix:** Updated client assertion to read `w_data["forecast_5day"][0]["day_name"]` and weather parameters.
- **Verification:** Live integration test passes with full 5-day agromet output.
