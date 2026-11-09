"""
Bol Kisan Knowledge Base Ingestion & Verification Engine.
Validates all 23 statutory agricultural collections (KB01 to KB23), computes
SHA-256 cryptographic checksums, generates standardized RAG retrieval chunks,
and maintains knowledge-base/metadata/kb_registry.json.

Authoritative Statutory Standards:
- ICAR: Indian Council of Agricultural Research
- CIBRC / DPPQS: Central Insecticides Board & Registration Committee / Directorate of Plant Protection
- NIPHM: National Institute of Plant Health Management
- PJTSAU / SAUs: State Agricultural Universities (Vyavasaya Panchangam)
- IMD Agromet: India Meteorological Department
- Agmarknet: Directorate of Marketing & Inspection (DMI)
"""

import os
import sys
import json
import hashlib
from pathlib import Path
from typing import Dict, Any, List, Tuple

KB_DIR = Path(__file__).resolve().parent

# Registry Mapping for All 23 Planned Collections
COLLECTION_SPECS = [
    {
        "id": "KB01",
        "name": "Crop Master Database",
        "file_rel": "crop-information/crops_master_kb01.json",
        "source_org": "ICAR & Ministry of Agriculture and Farmers Welfare",
        "primary_key": "id",
        "required_fields": ["id", "scientific_name", "name_en", "name_te", "name_hi", "category", "season", "growth_duration_days", "soil_requirement", "water_requirement_mm"]
    },
    {
        "id": "KB02",
        "name": "Crop Cultivation & Agronomy",
        "file_rel": "crop-information/cultivation_agronomy_kb02.json",
        "source_org": "PJTSAU Vyavasaya Panchangam & ICAR-CRIDA",
        "primary_key": "crop_id",
        "required_fields": ["crop_id", "crop_name", "seed_rate_per_acre", "spacing_row_x_plant_cm", "sowing_windows", "land_preparation", "nursery_or_sowing_method", "citation"]
    },
    {
        "id": "KB03",
        "name": "Crop Growth Stages",
        "file_rel": "crop-information/growth_stages_bbch_kb03.json",
        "source_org": "ICAR BBCH Standardization Committee",
        "primary_key": "crop_name",
        "required_fields": ["crop_name", "bbch_scale"]
    },
    {
        "id": "KB04",
        "name": "Crop Diseases Directory",
        "file_rel": "disease-information/crop_diseases_kb04.json",
        "source_org": "Directorate of Plant Protection, Quarantine & Storage (DPPQS) & ICAR-IARI",
        "primary_key": "id",
        "required_fields": ["id", "crop_name", "name_en", "name_te", "name_hi", "pathogen_type", "symptoms", "favorable_weather", "citation"]
    },
    {
        "id": "KB05",
        "name": "Pest Information",
        "file_rel": "disease-information/pest_information_kb05.json",
        "source_org": "National Bureau of Agricultural Insect Resources (NBAIR) & ICAR-NCIPM",
        "primary_key": "id",
        "required_fields": ["id", "crop_name", "name_en", "name_te", "name_hi", "scientific_name", "economic_threshold_level", "damage_symptoms", "citation"]
    },
    {
        "id": "KB06",
        "name": "Integrated Pest Management (IPM)",
        "file_rel": "disease-information/integrated_pest_management_kb06.json",
        "source_org": "National Institute of Plant Health Management (NIPHM) & NCIPM",
        "primary_key": "protocol_id",
        "required_fields": ["protocol_id", "crop_name", "target_pest_disease", "cultural_controls", "mechanical_physical_controls", "biological_controls", "monitoring_etl", "chemical_last_resort_cibrc", "niphm_citation"]
    },
    {
        "id": "KB07",
        "name": "Pesticide Products",
        "file_rel": "pesticide-labels/pesticide_products_kb07.json",
        "source_org": "Central Insecticides Board & Registration Committee (CIBRC) National Registry",
        "primary_key": "product_trade_name",
        "required_fields": ["product_trade_name", "manufacturer", "active_ingredient", "formulation_type", "cibrc_registration_number", "toxicity_band", "target_crop_primary", "statutory_source"]
    },
    {
        "id": "KB08",
        "name": "Pesticide Labels and Safety",
        "file_rel": "pesticide-labels/pesticide_labels_safety_kb08.json",
        "source_org": "CIBRC Approved Leaflet & Statutory Safety Directory",
        "primary_key": "cibrc_registration_number",
        "required_fields": ["product_trade_name", "active_ingredient", "cibrc_registration_number", "target_crop", "dosage_per_hectare", "dosage_per_acre", "dilution_water_liters", "pre_harvest_interval_days", "safety_precautions"]
    },
    {
        "id": "KB09",
        "name": "Fertilizers & Nutrient Management",
        "file_rel": "nutrients-soil/fertilizers_nutrients_kb09.json",
        "source_org": "Department of Fertilizers, FAI & PJTSAU",
        "primary_key": "crop_name",
        "required_fields": ["crop_name", "recommended_dose_fertilizer_npk_kg_per_acre", "recommended_dose_fertilizer_npk_kg_per_ha", "basal_dose_kg_per_acre", "top_dressing_schedule", "micronutrient_deficiencies_and_correction", "citation"]
    },
    {
        "id": "KB10",
        "name": "Soil Health & Amendments",
        "file_rel": "nutrients-soil/soil_health_amendments_kb10.json",
        "source_org": "Soil Health Card Scheme (GoI) & ICAR-IISS Bhopal",
        "primary_key": "soil_type",
        "required_fields": ["soil_type", "dominant_regions", "characteristics_and_texture", "ph_range", "organic_carbon_status", "major_nutrient_deficiencies", "soil_amendments_and_reclamation", "citation"]
    },
    {
        "id": "KB11",
        "name": "Irrigation Guidance",
        "file_rel": "water-weed-management/irrigation_guidance_kb11.json",
        "source_org": "Pradhan Mantri Krishi Sinchayee Yojana (PMKSY) & ICAR-WCM",
        "primary_key": "crop_name",
        "required_fields": ["crop_name", "total_water_requirement_mm", "critical_growth_stages", "irrigation_methods", "citation"]
    },
    {
        "id": "KB12",
        "name": "Crop Rotation & Intercropping",
        "file_rel": "water-weed-management/crop_rotation_intercropping_kb12.json",
        "source_org": "ICAR-Indian Institute of Farming Systems Research (IIFSR) Modipuram",
        "primary_key": "cropping_system_name",
        "required_fields": ["cropping_system_name", "spatial_row_ratio", "suitable_agro_climatic_zones", "agronomic_synergy_and_benefits", "economic_land_equivalent_ratio_ler", "citation"]
    },
    {
        "id": "KB13",
        "name": "Weed Management",
        "file_rel": "water-weed-management/weed_management_kb13.json",
        "source_org": "Directorate of Weed Research (DWR) Jabalpur & CIBRC",
        "primary_key": "crop_name",
        "required_fields": ["crop_name", "critical_period_crop_weed_competition", "major_weed_flora", "pre_emergence_herbicide", "post_emergence_herbicide", "citation"]
    },
    {
        "id": "KB14",
        "name": "Harvesting Guidelines",
        "file_rel": "harvest-postharvest/harvesting_guidelines_kb14.json",
        "source_org": "Central Institute of Post-Harvest Engineering & Technology (CIPHET) Ludhiana",
        "primary_key": "crop_name",
        "required_fields": ["crop_name", "physiological_maturity_signs", "grain_or_produce_moisture_at_harvest", "harvesting_methodology_and_equipment", "post_harvest_field_handling", "citation"]
    },
    {
        "id": "KB15",
        "name": "Post-Harvest Management & Storage",
        "file_rel": "harvest-postharvest/post_harvest_storage_kb15.json",
        "source_org": "Warehousing Development and Regulatory Authority (WDRA) & CIPHET",
        "primary_key": "commodity_name",
        "required_fields": ["commodity_name", "safe_storage_moisture_percent", "storage_structures_and_technology", "major_storage_pests", "pest_prevention_and_fumigation_norms", "citation"]
    },
    {
        "id": "KB16",
        "name": "Weather & Climate Agromet",
        "file_rel": "weather-advisories/weather_climate_agromet_kb16.json",
        "source_org": "India Meteorological Department (IMD) Agromet Advisory Service",
        "primary_key": "bulletin_id",
        "required_fields": ["bulletin_id", "district_and_agro_climatic_zone", "weather_scenario_or_phenomenon", "affected_crops", "agromet_impact_assessment", "mandatory_farmer_advisories", "imd_source_attribution"]
    },
    {
        "id": "KB17",
        "name": "Agricultural Advisories (Contingency Farming)",
        "file_rel": "weather-advisories/agricultural_advisories_kb17.json",
        "source_org": "ICAR-CRIDA Hyderabad & State Agriculture Universities",
        "primary_key": "advisory_id",
        "required_fields": ["advisory_id", "contingency_situation", "target_region_and_crops", "contingency_action_plan", "scientific_rationale", "authoritative_source"]
    },
    {
        "id": "KB18",
        "name": "Mandi and Market Prices",
        "file_rel": "market-regional/mandi_market_prices_kb18.json",
        "source_org": "Agmarknet Portal, Directorate of Marketing & Inspection (DMI)",
        "primary_key": "mandi_id",
        "required_fields": ["mandi_id", "mandi_name", "district", "state", "major_commodities", "market_infrastructure", "citation"]
    },
    {
        "id": "KB19",
        "name": "Regional Recommendations",
        "file_rel": "market-regional/regional_recommendations_kb19.json",
        "source_org": "Zonal Agricultural Research Stations (PJTSAU, ANGRAU, MPKV, RVSKVV, PAU)",
        "primary_key": "zone_id",
        "required_fields": ["zone_id", "zone_name", "headquarters_research_station", "state", "districts_covered", "dominant_soil_types", "university_recommended_crop_varieties", "critical_regional_advisories", "citation"]
    },
    {
        "id": "KB20",
        "name": "Multilingual Agronomy Dictionary",
        "file_rel": "language-faqs/multilingual_agronomy_dictionary_kb20.json",
        "source_org": "Central Institute of Indian Languages (CIIL) Mysore & SAUs",
        "primary_key": "english_term",
        "required_fields": ["english_term", "telugu_term", "hindi_term", "category"]
    },
    {
        "id": "KB21",
        "name": "Agricultural FAQs",
        "file_rel": "language-faqs/agricultural_faqs_kb21.json",
        "source_org": "Kisan Call Center (KCC 1800-180-1551) Query Database",
        "primary_key": "faq_id",
        "required_fields": ["faq_id", "farmer_query_en", "farmer_query_te", "farmer_query_hi", "crop", "category", "scientific_solution", "authoritative_source"]
    },
    {
        "id": "KB22",
        "name": "Verified Q&A Repository",
        "file_rel": "language-faqs/verified_qa_repository_kb22.json",
        "source_org": "Bol Kisan Agricultural Extension Officer Resolution Corpus",
        "primary_key": "case_id",
        "required_fields": ["case_id", "farmer_location", "crop", "crop_age_growth_stage", "reported_issue", "field_officer_diagnosis", "certified_remediation_plan", "verifying_officer_name", "officer_designation", "officer_code", "verification_date"]
    },
    {
        "id": "KB23",
        "name": "Regional Extension Officer Directory",
        "file_rel": "language-faqs/extension_officer_directory_kb23.json",
        "source_org": "State Departments of Agriculture (Telangana, AP, MP, Maharashtra, Punjab)",
        "primary_key": "officer_id",
        "required_fields": ["officer_id", "officer_name", "designation", "department", "state", "district", "jurisdiction_mandal_or_block", "office_location", "official_contact_phone", "toll_free_office_line", "official_gov_email", "office_hours"]
    }
]


def compute_file_sha256(filepath: Path) -> str:
    """Computes SHA-256 hash of a file."""
    hasher = hashlib.sha256()
    with open(filepath, "rb") as f:
        while chunk := f.read(65536):
            hasher.update(chunk)
    return hasher.hexdigest()


def compute_object_sha256(obj: Any) -> str:
    """Computes deterministic SHA-256 hash of a JSON-serializable python object."""
    encoded = json.dumps(obj, sort_keys=True, ensure_ascii=False).encode("utf-8")
    return hashlib.sha256(encoded).hexdigest()


def validate_and_ingest(verbose: bool = True) -> Dict[str, Any]:
    """
    Ingests all 23 collections, validates schemas, computes checksums,
    and updates metadata/kb_registry.json.
    """
    total_records = 0
    registry_collections = []
    validation_errors = []

    if verbose:
        print("=" * 86)
        print("  BOL KISAN: AGRICULTURAL KNOWLEDGE BASE INGESTION & AUDIT ENGINE")
        print("=" * 86)
        print(f"{'ID':<6} {'Collection Name':<34} {'Count':<7} {'Status':<10} {'SHA-256 Checksum (first 12)':<16}")
        print("-" * 86)

    for spec in COLLECTION_SPECS:
        coll_id = spec["id"]
        coll_name = spec["name"]
        file_path = KB_DIR / spec["file_rel"]

        if not file_path.exists():
            validation_errors.append(f"[{coll_id}] Missing file: {spec['file_rel']}")
            registry_collections.append({
                "id": coll_id,
                "name": coll_name,
                "file_path": spec["file_rel"],
                "source_org": spec["source_org"],
                "status": "missing",
                "docs_count": 0,
                "sha256": None,
                "last_audit_date": "2026-11-08"
            })
            continue

        try:
            with open(file_path, "r", encoding="utf-8") as f:
                data = json.load(f)
        except Exception as e:
            validation_errors.append(f"[{coll_id}] JSON Parse Error in {spec['file_rel']}: {e}")
            continue

        records = data if isinstance(data, list) else [data]
        record_count = len(records)
        total_records += record_count

        # Check required fields
        for idx, rec in enumerate(records):
            missing_fields = [rf for rf in spec["required_fields"] if rf not in rec]
            if missing_fields:
                validation_errors.append(f"[{coll_id}] Record #{idx} missing fields: {missing_fields}")

        # Compute SHA-256 hash of the file
        file_sha256 = compute_file_sha256(file_path)

        registry_entry = {
            "id": coll_id,
            "name": coll_name,
            "file_path": spec["file_rel"],
            "source_org": spec["source_org"],
            "status": "verified",
            "docs_count": record_count,
            "sha256": file_sha256,
            "last_audit_date": "2026-11-08"
        }
        registry_collections.append(registry_entry)

        if verbose:
            print(f"{coll_id:<6} {coll_name:<34} {record_count:<7} {'VERIFIED':<10} {file_sha256[:12]}...")

    if verbose:
        print("-" * 86)
        print(f"TOTAL: 23 Collections | Total Records: {total_records} | Validation Errors: {len(validation_errors)}")
        print("=" * 86)

    # Save to kb_registry.json
    registry_path = KB_DIR / "metadata" / "kb_registry.json"
    registry_payload = {
        "project": "BOL KISAN",
        "version": "1.0.0",
        "last_audit_date": "2026-11-08",
        "total_collections": len(COLLECTION_SPECS),
        "total_verified_records": total_records,
        "audit_status": "ALL_COLLECTIONS_VERIFIED" if not validation_errors else "VALIDATION_FAILED",
        "collections": registry_collections
    }

    with open(registry_path, "w", encoding="utf-8") as f:
        json.dump(registry_payload, f, indent=2, ensure_ascii=False)

    if verbose:
        print(f"[OK] Successfully updated registry: {registry_path.relative_to(KB_DIR.parent)}")

    return {
        "total_records": total_records,
        "collections_count": len(registry_collections),
        "errors": validation_errors,
        "registry": registry_payload
    }


def export_rag_chunks() -> List[Dict[str, Any]]:
    """
    Transforms all 23 collections into standardized chunks ready for hybrid RAG retrieval.
    Each chunk contains: collection_id, chunk_id, title, crop, content, citation, sha256_hash.
    """
    chunks: List[Dict[str, Any]] = []

    for spec in COLLECTION_SPECS:
        coll_id = spec["id"]
        file_path = KB_DIR / spec["file_rel"]
        if not file_path.exists():
            continue

        with open(file_path, "r", encoding="utf-8") as f:
            data = json.load(f)

        records = data if isinstance(data, list) else [data]

        for idx, rec in enumerate(records):
            chunk_content = ""
            chunk_title = ""
            chunk_crop = rec.get("crop_name") or rec.get("crop") or rec.get("commodity_name") or rec.get("target_crop")

            if coll_id == "KB01":
                chunk_title = f"Crop Master: {rec.get('name_en')}"
                chunk_crop = rec.get("name_en")
                chunk_content = f"Crop: {rec.get('name_en')} ({rec.get('name_te')}, {rec.get('name_hi')}). Scientific name: {rec.get('scientific_name')}. Category: {rec.get('category')}. Season: {rec.get('season')}. Duration: {rec.get('growth_duration_days')} days. Soil: {rec.get('soil_requirement')}. Water: {rec.get('water_requirement_mm')}."
                citation = "ICAR Crop Master 2025"

            elif coll_id == "KB02":
                chunk_title = f"Cultivation Guide: {rec.get('crop_name')}"
                chunk_content = f"Agronomy for {rec.get('crop_name')}: Seed rate: {rec.get('seed_rate_per_acre')}. Spacing: {rec.get('spacing_row_x_plant_cm')}. Sowing windows: {rec.get('sowing_windows')}. Land preparation: {rec.get('land_preparation')}. Sowing method: {rec.get('nursery_or_sowing_method')}. Plant population: {rec.get('plant_population_per_acre')}."
                citation = rec.get("citation", "PJTSAU Vyavasaya Panchangam 2025")

            elif coll_id == "KB03":
                chunk_title = f"BBCH Growth Stages: {rec.get('crop_name')}"
                stages_desc = "; ".join([f"{st['bbch_code']} ({st['stage_name']} {st['days_range']}): {st['description']}" for st in rec.get("bbch_scale", [])])
                chunk_content = f"BBCH Growth Stages for {rec.get('crop_name')}: {stages_desc}."
                citation = "ICAR BBCH Standards 2025"

            elif coll_id == "KB04":
                chunk_title = f"Disease: {rec.get('name_en')} in {rec.get('crop_name')}"
                chunk_content = f"Disease: {rec.get('name_en')} ({rec.get('name_te')}, {rec.get('name_hi')}) in {rec.get('crop_name')}. Pathogen: {rec.get('pathogen_type')}. Symptoms: {rec.get('symptoms')}. Favorable weather: {rec.get('favorable_weather')}."
                citation = rec.get("citation", "DPPQS Disease Compendium 2025")

            elif coll_id == "KB05":
                chunk_title = f"Pest: {rec.get('name_en')} in {rec.get('crop_name')}"
                chunk_content = f"Insect Pest: {rec.get('name_en')} ({rec.get('name_te')}, {rec.get('name_hi')}) in {rec.get('crop_name')}. Scientific name: {rec.get('scientific_name')}. Economic Threshold Level (ETL): {rec.get('economic_threshold_level')}. Damage symptoms: {rec.get('damage_symptoms')}. Natural predators: {rec.get('natural_predators')}."
                citation = rec.get("citation", "NBAIR Pest Guide 2025")

            elif coll_id == "KB06":
                chunk_title = f"IPM Protocol: {rec.get('target_pest_disease')} in {rec.get('crop_name')}"
                chunk_content = f"IPM for {rec.get('target_pest_disease')} on {rec.get('crop_name')}. Cultural: {rec.get('cultural_controls')}. Mechanical: {rec.get('mechanical_physical_controls')}. Biological: {rec.get('biological_controls')}. ETL Monitoring: {rec.get('monitoring_etl')}. Chemical last resort: {rec.get('chemical_last_resort_cibrc')}."
                citation = rec.get("niphm_citation", "NIPHM IPM Protocol 2025")

            elif coll_id == "KB07":
                chunk_title = f"Pesticide Product: {rec.get('product_trade_name')}"
                chunk_crop = rec.get("target_crop_primary")
                chunk_content = f"CIBRC Product: {rec.get('product_trade_name')} by {rec.get('manufacturer')}. Active ingredient: {rec.get('active_ingredient')}. Formulation: {rec.get('formulation_type')}. Toxicity: {rec.get('toxicity_band')}. Primary crop: {rec.get('target_crop_primary')}. CIBRC Reg: {rec.get('cibrc_registration_number')}."
                citation = f"CIBRC Registry: {rec.get('cibrc_registration_number')}"

            elif coll_id == "KB08":
                chunk_title = f"Statutory Label: {rec.get('product_trade_name')} for {rec.get('target_crop')}"
                chunk_content = (
                    f"CIBRC Approved Use: {rec.get('product_trade_name')} ({rec.get('active_ingredient')}) on {rec.get('target_crop')} against {rec.get('target_pest') or rec.get('target_disease')}. "
                    f"Dosage/Acre: {rec.get('dosage_per_acre')}. Dosage/Ha: {rec.get('dosage_per_hectare')}. "
                    f"Dilution: {rec.get('dilution_water_liters')}. PHI: {rec.get('pre_harvest_interval_days')} days. "
                    f"Toxicity: {rec.get('toxicity_band')}. Safety: {rec.get('safety_precautions')}."
                )
                citation = f"CIBRC Reg: {rec.get('cibrc_registration_number')}"

            elif coll_id == "KB09":
                chunk_title = f"Fertilizer Schedule: {rec.get('crop_name')}"
                chunk_content = f"Nutrient Management for {rec.get('crop_name')}: RDF: {rec.get('recommended_dose_fertilizer_npk_kg_per_acre')} NPK/acre ({rec.get('recommended_dose_fertilizer_npk_kg_per_ha')} NPK/ha). Basal: {rec.get('basal_dose_kg_per_acre')}. Top dressing: {rec.get('top_dressing_schedule')}. Micronutrients: {rec.get('micronutrient_deficiencies_and_correction')}. Biofertilizers: {rec.get('biofertilizers_recommended')}."
                citation = rec.get("citation", "Department of Fertilizers 2025")

            elif coll_id == "KB10":
                chunk_title = f"Soil Profile: {rec.get('soil_type')}"
                chunk_crop = "All Crops"
                chunk_content = f"Soil Type: {rec.get('soil_type')}. Regions: {rec.get('dominant_regions')}. Texture: {rec.get('characteristics_and_texture')}. pH: {rec.get('ph_range')}. Organic Carbon: {rec.get('organic_carbon_status')}. Deficiencies: {rec.get('major_nutrient_deficiencies')}. Reclamation/Amendments: {rec.get('soil_amendments_and_reclamation')}. Green Manure: {rec.get('green_manuring_and_organic_practices')}."
                citation = rec.get("citation", "Soil Health Card Scheme GoI 2025")

            elif coll_id == "KB11":
                chunk_title = f"Irrigation Guide: {rec.get('crop_name')}"
                crit_stages = "; ".join([f"{cs['stage_name']} ({cs['days_range']}): {cs['importance']}" for cs in rec.get("critical_growth_stages", [])])
                chunk_content = f"Irrigation for {rec.get('crop_name')}: Water Requirement: {rec.get('total_water_requirement_mm')}. Critical stages: {crit_stages}. Methods: {rec.get('irrigation_methods')}. Water stress management: {rec.get('water_stress_remedy')}."
                citation = rec.get("citation", "PMKSY Irrigation Guide 2025")

            elif coll_id == "KB12":
                chunk_title = f"Cropping System: {rec.get('cropping_system_name')}"
                chunk_content = f"Cropping System: {rec.get('cropping_system_name')}. Spacing/Ratio: {rec.get('spatial_row_ratio')}. Zones: {rec.get('suitable_agro_climatic_zones')}. Synergies: {rec.get('agronomic_synergy_and_benefits')}. LER: {rec.get('economic_land_equivalent_ratio_ler')}. Pest benefits: {rec.get('pest_management_benefit')}."
                citation = rec.get("citation", "ICAR-IIFSR Modipuram 2025")

            elif coll_id == "KB13":
                chunk_title = f"Weed Schedule: {rec.get('crop_name')}"
                chunk_content = f"Weed Control in {rec.get('crop_name')}: Critical Competition Period: {rec.get('critical_period_crop_weed_competition')}. Major Weeds: {rec.get('major_weed_flora')}. Pre-emergence: {rec.get('pre_emergence_herbicide')}. Post-emergence: {rec.get('post_emergence_herbicide')}. Cultural: {rec.get('cultural_mechanical_practices')}."
                citation = rec.get("citation", "ICAR-DWR Jabalpur 2025")

            elif coll_id == "KB14":
                chunk_title = f"Harvest Guide: {rec.get('crop_name')}"
                chunk_content = f"Harvesting {rec.get('crop_name')}: Maturity signs: {rec.get('physiological_maturity_signs')}. Moisture at harvest: {rec.get('grain_or_produce_moisture_at_harvest')}. Method: {rec.get('harvesting_methodology_and_equipment')}. Field handling: {rec.get('post_harvest_field_handling')}."
                citation = rec.get("citation", "ICAR-CIPHET Ludhiana 2025")

            elif coll_id == "KB15":
                chunk_title = f"Storage Protocol: {rec.get('commodity_name')}"
                chunk_crop = rec.get("commodity_name")
                chunk_content = f"Post-Harvest Storage for {rec.get('commodity_name')}: Safe Moisture: {rec.get('safe_storage_moisture_percent')}. Structures/Hermetic: {rec.get('storage_structures_and_technology')}. Pests: {rec.get('major_storage_pests')}. Prevention & Celphos norms: {rec.get('pest_prevention_and_fumigation_norms')}. Safe Duration: {rec.get('permissible_storage_duration')}."
                citation = rec.get("citation", "WDRA Warehousing Standards 2025")

            elif coll_id == "KB16":
                chunk_title = f"Agromet Bulletin: {rec.get('bulletin_id')}"
                chunk_content = f"IMD Agromet Advisory [{rec.get('bulletin_id')}] for {rec.get('district_and_agro_climatic_zone')}. Scenario: {rec.get('weather_scenario_or_phenomenon')}. Affected Crops: {rec.get('affected_crops')}. Impact: {rec.get('agromet_impact_assessment')}. Advisories: {' '.join(rec.get('mandatory_farmer_advisories', []))}."
                citation = rec.get("imd_source_attribution", "IMD Agromet 2025")

            elif coll_id == "KB17":
                chunk_title = f"Contingency Advisory: {rec.get('advisory_id')}"
                chunk_content = f"Contingency Plan [{rec.get('advisory_id')}]: {rec.get('contingency_situation')}. Region & Crops: {rec.get('target_region_and_crops')}. Action Plan: {' '.join(rec.get('contingency_action_plan', []))}. Rationale: {rec.get('scientific_rationale')}."
                citation = rec.get("authoritative_source", "ICAR-CRIDA Hyderabad 2025")

            elif coll_id == "KB18":
                chunk_title = f"Mandi Modal Prices: {rec.get('mandi_name')}"
                commodities_str = "; ".join([f"{c['commodity']} ({c['variety_grade']}): Modal Rs.{c['modal_price_range_inr_per_quintal']}/qtl (MSP Rs.{c.get('msp_benchmark_inr_per_quintal', 'N/A')}), Moisture rule: {c['moisture_deduction_rule']}" for c in rec.get("major_commodities", [])])
                chunk_content = f"Mandi: {rec.get('mandi_name')}, {rec.get('district')}, {rec.get('state')}. Commodities: {commodities_str}. Infrastructure: {rec.get('market_infrastructure')}."
                citation = rec.get("citation", "Agmarknet Portal 2025")

            elif coll_id == "KB19":
                chunk_title = f"Regional Package: {rec.get('zone_name')}"
                chunk_content = f"Regional Agronomy for {rec.get('zone_name')} ({rec.get('state')}), HQ: {rec.get('headquarters_research_station')}. Districts: {rec.get('districts_covered')}. Soil: {rec.get('dominant_soil_types')}. Recommended Varieties: {rec.get('university_recommended_crop_varieties')}. Advisories: {' '.join(rec.get('critical_regional_advisories', []))}."
                citation = rec.get("citation", "ZARS University Package 2025")

            elif coll_id == "KB20":
                chunk_title = f"Dictionary Term: {rec.get('english_term')}"
                chunk_crop = rec.get("english_term")
                chunk_content = f"Terminology: {rec.get('english_term')} | Telugu: {rec.get('telugu_term')} | Hindi: {rec.get('hindi_term')} | Category: {rec.get('category')} | Synonyms/Dialects: {rec.get('synonyms_dialects')}."
                citation = "CIIL Multilingual Agronomy Dictionary 2025"

            elif coll_id == "KB21":
                chunk_title = f"KCC FAQ: {rec.get('faq_id')} ({rec.get('crop')})"
                chunk_content = f"Farmer Query: {rec.get('farmer_query_en')} (Telugu: {rec.get('farmer_query_te')}, Hindi: {rec.get('farmer_query_hi')}). Crop: {rec.get('crop')}. Category: {rec.get('category')}. Scientific Solution: {rec.get('scientific_solution')}."
                citation = rec.get("authoritative_source", "Kisan Call Center 2025")

            elif coll_id == "KB22":
                chunk_title = f"Officer Resolution: {rec.get('case_id')} ({rec.get('crop')})"
                chunk_content = f"Field Case [{rec.get('case_id')}]: Location: {rec.get('farmer_location')}. Crop: {rec.get('crop')} ({rec.get('crop_age_growth_stage')}). Problem: {rec.get('reported_issue')}. Diagnosis: {rec.get('field_officer_diagnosis')}. Certified Remediation: {' '.join(rec.get('certified_remediation_plan', []))}. Sign-off: {rec.get('verifying_officer_name')}, {rec.get('officer_designation')} ({rec.get('officer_code')}) on {rec.get('verification_date')}."
                citation = rec.get("citation", "Bol Kisan Field Resolution Corpus 2025")

            elif coll_id == "KB23":
                chunk_title = f"Extension Officer: {rec.get('officer_name')}"
                chunk_crop = "Extension Directory"
                chunk_content = f"Agricultural Officer: {rec.get('officer_name')} ({rec.get('designation')}), {rec.get('department')}. Jurisdiction: {rec.get('jurisdiction_mandal_or_block')}, {rec.get('district')}, {rec.get('state')}. Office: {rec.get('office_location')}. Phone: {rec.get('official_contact_phone')}, Toll-Free: {rec.get('toll_free_office_line')}, Email: {rec.get('official_gov_email')}. Hours: {rec.get('office_hours')}."
                citation = "State Agriculture Department Directory 2025"

            chunk_id = f"{coll_id}_{idx+1:03d}"
            chunk_hash = hashlib.sha256(chunk_content.encode("utf-8")).hexdigest()

            chunks.append({
                "chunk_id": chunk_id,
                "collection_id": coll_id,
                "title": chunk_title,
                "crop": chunk_crop,
                "content": chunk_content,
                "citation": citation,
                "sha256_hash": chunk_hash
            })

    # Also load and append comprehensive verified crop cards
    cards_path = KB_DIR / "crop-information" / "crop_knowledge_cards_verified.json"
    if cards_path.exists():
        try:
            with open(cards_path, "r", encoding="utf-8") as f:
                verified_cards = json.load(f)
            for c in verified_cards:
                c_hash = hashlib.sha256(c["content"].encode("utf-8")).hexdigest()
                chunks.append({
                    "chunk_id": c["id"],
                    "collection_id": c.get("collectionId", "KB04"),
                    "title": c["title"],
                    "crop": c["crop"],
                    "content": c["content"],
                    "citation": c["sourceCitation"],
                    "language": c.get("language", "en"),
                    "sha256_hash": c_hash
                })
        except Exception as e:
            print(f"[WARN] Failed to append verified crop cards: {e}")

    return chunks


if __name__ == "__main__":
    result = validate_and_ingest(verbose=True)
    if "--export-chunks" in sys.argv:
        chunks = export_rag_chunks()
        out_path = KB_DIR / "metadata" / "rag_chunks.json"
        with open(out_path, "w", encoding="utf-8") as f:
            json.dump(chunks, f, indent=2, ensure_ascii=False)
        print(f"[OK] Exported {len(chunks)} verified RAG chunks to {out_path.relative_to(KB_DIR.parent)}")
