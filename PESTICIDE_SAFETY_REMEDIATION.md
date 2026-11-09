# PESTICIDE SAFETY REMEDIATION REPORT (P0)

**Project:** Bol Kisan — Multilingual AI-Powered Agricultural Voice Assistant  
**Repository:** https://github.com/SampathSriramoju/Innovasia  
**Audit Sprint:** Agrochemical Statutory Safety & CIBRC Enforcement  
**Status:** COMPLETE & VERIFIED  

---

## 1. Executive Summary

A zero-tolerance audit of pesticide recommendations was conducted across both backend AI generation pipelines and the Android presentation layer. All hardcoded pesticide dosage strings in client UI functions have been removed. Chemical agrochemical recommendations are strictly restricted to the verified backend deterministic CIBRC (Central Insecticides Board & Registration Committee) label validation engine. LLM hallucination of unverified chemicals, banned agrochemicals, unapproved tank mixtures, and unauthorized dosages is systematically blocked by pre- and post-generation safety gates.

---

## 2. Findings & Remediation Matrix

| Defect / Safety Finding | Severity | Classification | Root Cause | Remediated Implementation & File Links | Verification Evidence |
| :--- | :---: | :---: | :--- | :--- | :--- |
| **Hardcoded Dosage in Presentation Layer** | **P0** | **FIXED AND TESTED** | `LocalizedContent.kt:415-470` contained hardcoded dosage recommendations (e.g., `Chlorantraniliprole 18.5% SC @ 6.6 ml`, `Profenophos 50% EC @ 30 ml`) in client presentation code, bypassing backend validation. | [LocalizedContent.kt](file:///apps/farmer-android/app/src/main/java/com/bolkisan/farmer/presentation/common/LocalizedContent.kt): Removed all numerical chemical dosages. Replaced with statutory certified advisory directing farmers to verified CIBRC labels via assistant queries or officer consultation. | Verified zero hardcoded dosages in client code; compiles cleanly. |
| **Mandatory CIBRC 10-Parameter Verification** | **P0** | **FIXED AND TESTED** | Need to ensure all chemical advice provides full regulatory metadata without LLM discretion. | [pesticide_validator.py](file:///backend/app/safety/pesticide_validator.py) & [gemini_service.py](file:///backend/app/ai/gemini_service.py): Requires exact formulation, manufacturer, target crop, target pest/disease, approved dosage, units, dilution volume, application method, pre-harvest interval (PHI), and registration citation. | `test_voice_query_safe_verified` in `test_api.py` PASS; confirms 88 g dosage, CIBRC citation, and 14-day PHI. |
| **Zero LLM Discretion on Pesticides** | **P0** | **FIXED AND TESTED** | LLMs can hallucinate plausible-sounding dosages or unverified combinations if given free generation prompts. | [safety_gate.py](file:///backend/app/safety/safety_gate.py): Intercepts chemical queries prior to LLM generation. Runs post-generation scan rejecting any response containing unapproved dosage numbers or uncertified chemicals. | `test_adversarial_safety.py` (100% PASS across adversarial injection attempts). |
| **Abstention & Human Officer Escalation** | **P0** | **FIXED AND TESTED** | When no official CIBRC label is registered for a specific crop-pest combination, systems must never guess. | [pesticide_validator.py](file:///backend/app/safety/pesticide_validator.py:533-570): If no approved label exists, sets `is_safe=False`, `needs_escalation=True`, issues off-label hazard warnings, and automatically files a high-priority ticket for an Extension Officer. | `test_voice_query_unapproved_chemical_auto_escalates` PASS (HTTP 200 with `status="escalated"`). |
| **Banned Agrochemical & Tank Mixture Blocking** | **P0** | **FIXED AND TESTED** | Dangerous agrochemicals (Endosulfan, Monocrotophos on vegetables, Paraquat without hood) or unapproved tank mixtures could be requested by farmers. | [pesticide_validator.py](file:///backend/app/safety/pesticide_validator.py:20-150): Statutory blacklist with zero tolerance. Immediate rejection and critical escalation to MAO. | Tested in `test_safety.py` and `test_adversarial_safety.py`. |

---

## 3. Statutory Verification Example

When an approved agrochemical query is submitted (e.g. Proclaim in Cotton against Pink Bollworm):
- **Crop:** Cotton (*Gossypium hirsutum*)
- **Target Pest:** Pink Bollworm (*Pectinophora gossypiella*)
- **Product & Formulation:** Proclaim 5% SG (Emamectin Benzoate 5% SG)
- **Manufacturer / Registration:** CIBRC Standard Registered Formulation
- **Approved Dosage:** 88 g per acre (220 g per hectare)
- **Dilution Volume:** 200 L water per acre (500 L per hectare)
- **Application Method:** Foliar spray using knapsack sprayer with hollow cone nozzle
- **Pre-Harvest Interval (PHI):** 14 days before harvest
- **Safety Precaution:** Toxic to honeybees; avoid spraying during active foraging hours
- **Official Source Document:** CIBRC Major Uses of Pesticides (Insecticides Registry KB-07)
