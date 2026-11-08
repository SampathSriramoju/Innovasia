# ADR-005: Regional Escalation Protocol & Extension Officer SLA Management

## Status
Accepted

## Context
When the automated AI or safety filters cannot conclusively determine an agricultural diagnosis or verify a pesticide dosage, the question must be escalated to a human agricultural extension officer.
Farmers need certainty and predictable turnaround times; officers need structured, actionable triage cases rather than unstructured voice dumps.

## Decision
1. **Escalation Triggers**:
   - Safety Gate block (unapproved pesticide, ambiguous dosage, conflicting recommendations).
   - Low confidence in leaf disease diagnosis (< 0.70 confidence from visual classifier).
   - Low retrieval confidence (< 0.65 cosine similarity across all KB collections).
   - Explicit farmer voice request: *"I want to speak with the Krishi Adhikari / Vyavasaya Adhikari"*.
2. **Deterministic Regional Routing**:
   - Each ticket is automatically tagged with the farmer's `State`, `District`, and `Mandal`.
   - The ticket routing engine queries `extension_officers` where `mandal = ANY(assigned_mandals)` and assigns it to the on-duty Mandal Agricultural Officer (MAO).
   - If no officer is assigned to that specific mandal, it bubbles up to the District Agricultural Officer (DAO) or KVK Lead.
3. **Structured Case Dossier**:
   - Ticket Number (e.g. `ESC-2026-MANDAL-0042`).
   - Farmer Profile (Name, Phone, Village, Crop, Acreage, Soil Type).
   - Original Audio Recording + Synchronized Speech Transcript.
   - High-resolution leaf/field photos with edge-compressed detail.
   - System Diagnostic Trace: Extracted entities, matched vs missing CIBRC records, RAG retrieval scores, and reason for escalation.
4. **Officer Response Flow**:
   - Officer reviews the case in the React Officer Dashboard.
   - Officer chooses: Prescribe Chemical Control (from dropdown of verified products), Prescribe Non-Chemical/Biological Control, Request clearer photograph, or Schedule Field Visit.
   - System synthesizes the officer's written prescription into a localized audio voice note and dispatches it to the farmer's Android app and sends an SMS summary.
5. **SLA & Escalation Breach Alerting**:
   - Urgent tickets (chemical safety / quarantine pests): SLA = 4 hours.
   - Standard tickets (disease review / nutrient deficiency): SLA = 24 hours.
   - If unaddressed within SLA, ticket status elevates to `emergency` and notifies the District Lead.

## Consequences
- **Positive**: Guarantees that no farmer is left stranded with bad or missing information. Complete accountability and audit trail.
- **Negative**: Requires active officer onboarding and regional jurisdiction mapping.
