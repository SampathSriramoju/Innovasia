import uuid
from datetime import datetime, timezone
from sqlalchemy import (
    Column, String, Boolean, DateTime, ForeignKey, Integer, Numeric, Text, JSON
)
from sqlalchemy.orm import relationship
from app.database.base import Base

def generate_uuid() -> str:
    return str(uuid.uuid4())

def utc_now() -> datetime:
    return datetime.now(timezone.utc)

class User(Base):
    __tablename__ = "users"

    id = Column(String(36), primary_key=True, default=generate_uuid)
    phone_number = Column(String(20), unique=True, index=True, nullable=False)
    full_name = Column(String(120), nullable=False)
    role = Column(String(30), default="farmer", nullable=False) # 'farmer', 'extension_officer', 'admin'
    preferred_lang = Column(String(10), default="te", nullable=False) # 'te', 'hi', 'en'
    is_active = Column(Boolean, default=True, nullable=False)
    is_verified = Column(Boolean, default=False, nullable=False)
    created_at = Column(DateTime(timezone=True), default=utc_now, nullable=False)
    updated_at = Column(DateTime(timezone=True), default=utc_now, onupdate=utc_now, nullable=False)

    farmer_profile = relationship("FarmerProfile", back_populates="user", uselist=False, cascade="all, delete-orphan")
    officer_profile = relationship("ExtensionOfficer", back_populates="user", uselist=False, cascade="all, delete-orphan")
    tickets_filed = relationship("AgriculturalTicket", foreign_keys="AgriculturalTicket.farmer_id", back_populates="farmer")
    tickets_assigned = relationship("AgriculturalTicket", foreign_keys="AgriculturalTicket.assigned_officer_id", back_populates="officer")
    conversations = relationship("Conversation", back_populates="farmer", cascade="all, delete-orphan")

class FarmerProfile(Base):
    __tablename__ = "farmer_profiles"

    id = Column(String(36), primary_key=True, default=generate_uuid)
    user_id = Column(String(36), ForeignKey("users.id", ondelete="CASCADE"), unique=True, nullable=False)
    state = Column(String(60), nullable=False)
    district = Column(String(60), nullable=False)
    mandal = Column(String(60), nullable=False)
    village = Column(String(80), nullable=False)
    pincode = Column(String(10))
    land_size_acres = Column(Numeric(6, 2), default=0.0)
    soil_type = Column(String(50))
    irrigation_source = Column(String(50))
    created_at = Column(DateTime(timezone=True), default=utc_now, nullable=False)

    user = relationship("User", back_populates="farmer_profile")

class ExtensionOfficer(Base):
    __tablename__ = "extension_officers"

    id = Column(String(36), primary_key=True, default=generate_uuid)
    user_id = Column(String(36), ForeignKey("users.id", ondelete="CASCADE"), unique=True, nullable=False)
    officer_code = Column(String(30), unique=True, index=True, nullable=False)
    designation = Column(String(80), nullable=False)
    department = Column(String(100), nullable=False)
    assigned_state = Column(String(60), nullable=False)
    assigned_district = Column(String(60), nullable=False)
    assigned_mandals = Column(JSON, default=list, nullable=False) # List of mandal strings
    approval_status = Column(String(30), default="PENDING_APPROVAL", nullable=False) # 'PENDING_APPROVAL', 'APPROVED', 'REJECTED'
    employee_id = Column(String(60), nullable=True)
    official_email = Column(String(120), nullable=True)
    verified_at = Column(DateTime(timezone=True), default=utc_now)
    created_at = Column(DateTime(timezone=True), default=utc_now, nullable=False)

    user = relationship("User", back_populates="officer_profile")

class Crop(Base):
    __tablename__ = "crops"

    id = Column(String(36), primary_key=True, default=generate_uuid)
    scientific_name = Column(String(120), nullable=False)
    name_en = Column(String(80), index=True, nullable=False)
    name_te = Column(String(80), index=True, nullable=False)
    name_hi = Column(String(80), index=True, nullable=False)
    category = Column(String(50), nullable=False)
    season = Column(String(30))
    is_active = Column(Boolean, default=True, nullable=False)
    created_at = Column(DateTime(timezone=True), default=utc_now, nullable=False)

    diseases = relationship("Disease", back_populates="crop", cascade="all, delete-orphan")
    pests = relationship("Pest", back_populates="crop", cascade="all, delete-orphan")

class Disease(Base):
    __tablename__ = "diseases"

    id = Column(String(36), primary_key=True, default=generate_uuid)
    crop_id = Column(String(36), ForeignKey("crops.id", ondelete="CASCADE"), nullable=False)
    name_en = Column(String(100), nullable=False)
    name_te = Column(String(100), nullable=False)
    name_hi = Column(String(100), nullable=False)
    pathogen_type = Column(String(255), nullable=False)
    symptoms_description = Column(Text, nullable=False)
    favorable_weather_conditions = Column(Text)
    created_at = Column(DateTime(timezone=True), default=utc_now, nullable=False)

    crop = relationship("Crop", back_populates="diseases")

class Pest(Base):
    __tablename__ = "pests"

    id = Column(String(36), primary_key=True, default=generate_uuid)
    crop_id = Column(String(36), ForeignKey("crops.id", ondelete="CASCADE"), nullable=False)
    name_en = Column(String(100), nullable=False)
    name_te = Column(String(100), nullable=False)
    name_hi = Column(String(100), nullable=False)
    scientific_name = Column(String(120))
    economic_threshold_level = Column(Text)
    damage_symptoms = Column(Text, nullable=False)
    created_at = Column(DateTime(timezone=True), default=utc_now, nullable=False)

    crop = relationship("Crop", back_populates="pests")

class PesticideProduct(Base):
    __tablename__ = "pesticide_products"

    id = Column(String(36), primary_key=True, default=generate_uuid)
    trade_name = Column(String(120), index=True, nullable=False)
    manufacturer = Column(String(120), nullable=False)
    registration_number = Column(String(80), unique=True, nullable=False)
    formulation_type = Column(String(30), nullable=False)
    toxicity_band = Column(String(40), nullable=False)
    cibrc_status = Column(String(40), default="approved", nullable=False)
    antidote_statement = Column(Text)
    created_at = Column(DateTime(timezone=True), default=utc_now, nullable=False)

    labels = relationship("PesticideLabel", back_populates="product", cascade="all, delete-orphan")

class PesticideLabel(Base):
    __tablename__ = "pesticide_labels"

    id = Column(String(36), primary_key=True, default=generate_uuid)
    product_id = Column(String(36), ForeignKey("pesticide_products.id", ondelete="CASCADE"), nullable=False)
    active_ingredient = Column(String(150), index=True, nullable=False)
    concentration = Column(String(50), nullable=False)
    cibrc_approval_number = Column(String(100), nullable=False)
    version_number = Column(Integer, default=1, nullable=False)
    approval_date = Column(DateTime(timezone=True), default=utc_now, nullable=False)
    official_leaflet_url = Column(Text)
    is_current_valid = Column(Boolean, default=True, nullable=False)
    created_at = Column(DateTime(timezone=True), default=utc_now, nullable=False)

    product = relationship("PesticideProduct", back_populates="labels")
    approved_uses = relationship("ApprovedPesticideUse", back_populates="label", cascade="all, delete-orphan")

class ApprovedPesticideUse(Base):
    __tablename__ = "approved_pesticide_uses"

    id = Column(String(36), primary_key=True, default=generate_uuid)
    label_id = Column(String(36), ForeignKey("pesticide_labels.id", ondelete="CASCADE"), nullable=False)
    crop_id = Column(String(36), ForeignKey("crops.id", ondelete="CASCADE"), nullable=False)
    pest_id = Column(String(36), ForeignKey("pests.id", ondelete="SET NULL"), nullable=True)
    disease_id = Column(String(36), ForeignKey("diseases.id", ondelete="SET NULL"), nullable=True)
    dosage_per_hectare = Column(String(60), nullable=False)
    dosage_per_acre = Column(String(60), nullable=False)
    dilution_water_liters = Column(String(60), nullable=False)
    pre_harvest_interval_days = Column(Integer, nullable=False)
    max_applications_per_season = Column(Integer, default=2)
    application_method = Column(String(80), default="Foliar Spray", nullable=False)
    safety_precautions = Column(Text, nullable=False)
    verification_status = Column(String(30), default="verified", nullable=False)
    created_at = Column(DateTime(timezone=True), default=utc_now, nullable=False)

    label = relationship("PesticideLabel", back_populates="approved_uses")

class AgriculturalTicket(Base):
    __tablename__ = "agricultural_tickets"

    id = Column(String(36), primary_key=True, default=generate_uuid)
    ticket_number = Column(String(30), unique=True, index=True, nullable=False)
    farmer_id = Column(String(36), ForeignKey("users.id", ondelete="CASCADE"), nullable=False)
    assigned_officer_id = Column(String(36), ForeignKey("users.id", ondelete="SET NULL"), nullable=True)
    crop_id = Column(String(36), ForeignKey("crops.id", ondelete="SET NULL"), nullable=True)
    conversation_id = Column(String(36), ForeignKey("conversations.id", ondelete="SET NULL"), nullable=True)
    state = Column(String(60), nullable=False)
    district = Column(String(60), nullable=False)
    mandal = Column(String(60), index=True, nullable=False)
    village = Column(String(80), nullable=True)
    status = Column(String(30), default="PENDING", index=True, nullable=False) # 'PENDING', 'ASSIGNED', 'IN_REVIEW', 'WAITING_FOR_FARMER', 'RESPONDED', 'RESOLVED', 'CLOSED'
    priority = Column(String(20), default="medium", nullable=False) # 'low', 'medium', 'high', 'emergency'
    escalation_reason = Column(Text, nullable=False)
    original_question_transcript = Column(Text, nullable=False)
    audio_recording_url = Column(Text)
    attached_image_urls = Column(JSON, default=list)
    resolution_prescription = Column(Text)
    officer_response = Column(Text)
    resolved_at = Column(DateTime(timezone=True))
    created_at = Column(DateTime(timezone=True), default=utc_now, nullable=False)
    updated_at = Column(DateTime(timezone=True), default=utc_now, onupdate=utc_now, nullable=False)

    farmer = relationship("User", foreign_keys=[farmer_id], back_populates="tickets_filed")
    officer = relationship("User", foreign_keys=[assigned_officer_id], back_populates="tickets_assigned")
    conversation = relationship("Conversation", back_populates="tickets")
    messages = relationship("TicketMessage", back_populates="ticket", cascade="all, delete-orphan")

class Conversation(Base):
    __tablename__ = "conversations"

    id = Column(String(36), primary_key=True, default=generate_uuid)
    farmer_id = Column(String(36), ForeignKey("users.id", ondelete="CASCADE"), nullable=False, index=True)
    title = Column(String(200), nullable=False)
    crop = Column(String(80), nullable=False)
    language = Column(String(10), default="te", nullable=False)
    message_count = Column(Integer, default=0, nullable=False)
    escalation_status = Column(String(30), default="NONE", nullable=False) # 'NONE', 'ESCALATED', 'RESOLVED'
    created_at = Column(DateTime(timezone=True), default=utc_now, nullable=False)
    updated_at = Column(DateTime(timezone=True), default=utc_now, onupdate=utc_now, nullable=False)

    farmer = relationship("User", back_populates="conversations")
    messages = relationship("ConversationMessage", back_populates="conversation", cascade="all, delete-orphan", order_by="ConversationMessage.timestamp")
    tickets = relationship("AgriculturalTicket", back_populates="conversation")

class ConversationMessage(Base):
    __tablename__ = "conversation_messages"

    id = Column(String(36), primary_key=True, default=generate_uuid)
    conversation_id = Column(String(36), ForeignKey("conversations.id", ondelete="CASCADE"), nullable=False, index=True)
    sender_type = Column(String(20), nullable=False) # 'FARMER', 'AI', 'OFFICER'
    original_query = Column(Text, nullable=True)
    transcript = Column(Text, nullable=True)
    response = Column(Text, nullable=False)
    source_citations = Column(JSON, default=list)
    language = Column(String(10), default="te", nullable=False)
    is_safe = Column(Boolean, default=True, nullable=False)
    is_escalated = Column(Boolean, default=False, nullable=False)
    ticket_id = Column(String(36), ForeignKey("agricultural_tickets.id", ondelete="SET NULL"), nullable=True)
    timestamp = Column(DateTime(timezone=True), default=utc_now, nullable=False)

    conversation = relationship("Conversation", back_populates="messages")
    ticket = relationship("AgriculturalTicket")

class TicketMessage(Base):
    __tablename__ = "ticket_messages"

    id = Column(String(36), primary_key=True, default=generate_uuid)
    ticket_id = Column(String(36), ForeignKey("agricultural_tickets.id", ondelete="CASCADE"), nullable=False)
    sender_id = Column(String(36), ForeignKey("users.id", ondelete="CASCADE"), nullable=False)
    msg_type = Column(String(20), default="text", nullable=False) # 'text', 'audio', 'image', 'prescription'
    content = Column(Text, nullable=False)
    media_url = Column(Text)
    created_at = Column(DateTime(timezone=True), default=utc_now, nullable=False)

    ticket = relationship("AgriculturalTicket", back_populates="messages")

class AuditEvent(Base):
    __tablename__ = "audit_events"

    id = Column(String(36), primary_key=True, default=generate_uuid)
    event_type = Column(String(60), index=True, nullable=False)
    actor_id = Column(String(36), ForeignKey("users.id", ondelete="SET NULL"), nullable=True)
    target_entity = Column(String(60), nullable=False)
    target_id = Column(String(36))
    details_json = Column(JSON, default=dict)
    ip_address = Column(String(45))
    created_at = Column(DateTime(timezone=True), default=utc_now, nullable=False)


# ==============================================================================
# STATUTORY KNOWLEDGE BASE REPOSITORY MODELS (KB01 to KB23)
# ==============================================================================

class AgriculturalSource(Base):
    __tablename__ = "agricultural_sources"

    id = Column(String(36), primary_key=True, default=generate_uuid)
    code = Column(String(40), unique=True, index=True, nullable=False) # e.g. 'ICAR', 'CIBRC', 'PJTSAU'
    name = Column(String(200), nullable=False)
    organization_type = Column(String(80), nullable=False) # 'Government Regulator', 'National Research Council', 'Agricultural University'
    official_url = Column(Text, nullable=False)
    authority_level = Column(Integer, default=1, nullable=False) # 1 (Regulatory), 2 (ICAR/SAU), 3 (Peer-reviewed), 4 (Historical), 5 (Third-party)
    state = Column(String(80), default="National", nullable=False)
    description = Column(Text)
    is_active = Column(Boolean, default=True, nullable=False)
    created_at = Column(DateTime(timezone=True), default=utc_now, nullable=False)


class AgriculturalDocument(Base):
    __tablename__ = "agricultural_documents"

    id = Column(String(36), primary_key=True, default=generate_uuid)
    source_id = Column(String(36), ForeignKey("agricultural_sources.id", ondelete="CASCADE"), nullable=False)
    collection_id = Column(String(20), index=True, nullable=False) # 'KB01' - 'KB23'
    title = Column(String(300), nullable=False)
    document_type = Column(String(80), nullable=False) # 'Package of Practices', 'CIBRC Approved Leaflet', 'Agromet Bulletin', 'Mandi Price Feed'
    file_format = Column(String(20), default="JSON", nullable=False)
    original_url = Column(Text)
    storage_path = Column(Text, nullable=False)
    file_size_bytes = Column(Integer, default=0)
    sha256_hash = Column(String(64), index=True, nullable=False)
    publication_date = Column(String(40))
    version = Column(String(40), default="1.0.0", nullable=False)
    language = Column(String(10), default="en", nullable=False)
    verification_status = Column(String(40), default="VERIFIED", index=True, nullable=False)
    created_at = Column(DateTime(timezone=True), default=utc_now, nullable=False)


class DocumentVersion(Base):
    __tablename__ = "document_versions"

    id = Column(String(36), primary_key=True, default=generate_uuid)
    document_id = Column(String(36), ForeignKey("agricultural_documents.id", ondelete="CASCADE"), nullable=False)
    version_number = Column(String(40), nullable=False)
    effective_from = Column(DateTime(timezone=True), default=utc_now, nullable=False)
    effective_to = Column(DateTime(timezone=True), nullable=True)
    is_current = Column(Boolean, default=True, nullable=False)
    changelog = Column(Text)
    sha256_hash = Column(String(64), nullable=False)
    created_at = Column(DateTime(timezone=True), default=utc_now, nullable=False)


class DocumentExtractionJob(Base):
    __tablename__ = "document_extraction_jobs"

    id = Column(String(36), primary_key=True, default=generate_uuid)
    document_id = Column(String(36), ForeignKey("agricultural_documents.id", ondelete="CASCADE"), nullable=False)
    status = Column(String(40), default="COMPLETED", nullable=False) # 'QUEUED', 'RUNNING', 'COMPLETED', 'FAILED'
    extractor_name = Column(String(80), nullable=False)
    extracted_records_count = Column(Integer, default=0, nullable=False)
    extracted_chunks_count = Column(Integer, default=0, nullable=False)
    error_message = Column(Text, nullable=True)
    started_at = Column(DateTime(timezone=True), default=utc_now, nullable=False)
    completed_at = Column(DateTime(timezone=True), default=utc_now, nullable=True)


class KnowledgeRecord(Base):
    __tablename__ = "knowledge_records"

    id = Column(String(36), primary_key=True, default=generate_uuid)
    knowledge_id = Column(String(80), unique=True, index=True, nullable=False)
    collection_id = Column(String(20), index=True, nullable=False) # 'KB01' to 'KB23'
    category = Column(String(80), index=True, nullable=False)
    subcategory = Column(String(80))
    crop_name = Column(String(80), index=True)
    state = Column(String(60), default="National", index=True)
    district = Column(String(60))
    season = Column(String(30))
    growth_stage = Column(String(80))
    language = Column(String(10), default="en", nullable=False)
    content = Column(Text, nullable=False)
    structured_payload = Column(JSON, default=dict)
    source_org = Column(String(200), nullable=False)
    source_title = Column(String(300), nullable=False)
    source_url = Column(Text)
    document_id = Column(String(36), ForeignKey("agricultural_documents.id", ondelete="SET NULL"), nullable=True)
    page_number = Column(Integer, nullable=True)
    verification_level = Column(Integer, default=1, nullable=False)
    verification_status = Column(String(40), default="VERIFIED", index=True, nullable=False)
    safety_classification = Column(String(40), default="NORMAL", nullable=False)
    created_at = Column(DateTime(timezone=True), default=utc_now, nullable=False)


class KnowledgeChunk(Base):
    __tablename__ = "knowledge_chunks"

    id = Column(String(36), primary_key=True, default=generate_uuid)
    chunk_id = Column(String(80), unique=True, index=True, nullable=False)
    document_id = Column(String(36), ForeignKey("agricultural_documents.id", ondelete="SET NULL"), nullable=True)
    collection_id = Column(String(20), index=True, nullable=False)
    crop = Column(String(80), index=True)
    region = Column(String(80), default="National", index=True)
    language = Column(String(10), default="en", nullable=False)
    title = Column(String(300), nullable=False)
    chunk_text = Column(Text, nullable=False)
    citation = Column(Text, nullable=False)
    token_count = Column(Integer, default=0, nullable=False)
    embedding = Column(JSON, default=list) # 128/384-dimensional dense semantic embedding vector
    sha256_hash = Column(String(64), nullable=False)
    verification_status = Column(String(40), default="VERIFIED", index=True, nullable=False)
    created_at = Column(DateTime(timezone=True), default=utc_now, nullable=False)


class KnowledgeVerification(Base):
    __tablename__ = "knowledge_verification"

    id = Column(String(36), primary_key=True, default=generate_uuid)
    entity_type = Column(String(60), index=True, nullable=False) # 'document', 'knowledge_record', 'pesticide_label'
    entity_id = Column(String(80), index=True, nullable=False)
    verifier_type = Column(String(60), default="STATUTORY_AUDITOR", nullable=False)
    verified_by = Column(String(120), nullable=False)
    verification_status = Column(String(40), default="VERIFIED", index=True, nullable=False)
    confidence_score = Column(Numeric(5, 4), default=1.0, nullable=False)
    notes = Column(Text)
    verified_at = Column(DateTime(timezone=True), default=utc_now, nullable=False)


class CropVariety(Base):
    __tablename__ = "crop_varieties"

    id = Column(String(36), primary_key=True, default=generate_uuid)
    crop_id = Column(String(36), ForeignKey("crops.id", ondelete="CASCADE"), nullable=False)
    variety_name = Column(String(120), index=True, nullable=False)
    releasing_institution = Column(String(200), nullable=False)
    release_year = Column(Integer)
    duration_days = Column(String(60))
    recommended_regions = Column(Text)
    special_features = Column(Text)
    yield_potential_q_per_acre = Column(String(60))
    created_at = Column(DateTime(timezone=True), default=utc_now, nullable=False)


class FertilizerRecord(Base):
    __tablename__ = "fertilizer_records"

    id = Column(String(36), primary_key=True, default=generate_uuid)
    crop_name = Column(String(80), index=True, nullable=False)
    npk_ratio = Column(String(40), nullable=False)
    recommended_n_kg_acre = Column(Numeric(6, 2))
    recommended_p_kg_acre = Column(Numeric(6, 2))
    recommended_k_kg_acre = Column(Numeric(6, 2))
    basal_dose_details = Column(Text, nullable=False)
    top_dressing_schedule = Column(Text, nullable=False)
    micronutrient_recommendations = Column(Text)
    source_citation = Column(Text, nullable=False)
    created_at = Column(DateTime(timezone=True), default=utc_now, nullable=False)


class SoilRecord(Base):
    __tablename__ = "soil_records"

    id = Column(String(36), primary_key=True, default=generate_uuid)
    soil_type = Column(String(80), index=True, nullable=False)
    dominant_regions = Column(Text, nullable=False)
    texture = Column(String(80))
    ph_range = Column(String(40), nullable=False)
    organic_carbon_status = Column(String(60))
    nutrient_deficiencies = Column(Text)
    reclamation_measures = Column(Text, nullable=False)
    source_citation = Column(Text, nullable=False)
    created_at = Column(DateTime(timezone=True), default=utc_now, nullable=False)


class IrrigationRecord(Base):
    __tablename__ = "irrigation_records"

    id = Column(String(36), primary_key=True, default=generate_uuid)
    crop_name = Column(String(80), index=True, nullable=False)
    total_water_requirement_mm = Column(String(60), nullable=False)
    critical_stages = Column(Text, nullable=False)
    irrigation_methods = Column(Text, nullable=False)
    water_saving_techniques = Column(Text)
    source_citation = Column(Text, nullable=False)
    created_at = Column(DateTime(timezone=True), default=utc_now, nullable=False)


class RegionalRecommendation(Base):
    __tablename__ = "regional_recommendations"

    id = Column(String(36), primary_key=True, default=generate_uuid)
    zone_id = Column(String(40), index=True, nullable=False)
    zone_name = Column(String(150), nullable=False)
    state = Column(String(60), index=True, nullable=False)
    districts_covered = Column(Text, nullable=False)
    headquarters = Column(String(150), nullable=False)
    recommended_crops_varieties = Column(Text, nullable=False)
    contingency_plans = Column(Text)
    source_citation = Column(Text, nullable=False)
    created_at = Column(DateTime(timezone=True), default=utc_now, nullable=False)


class AgriculturalAdvisory(Base):
    __tablename__ = "agricultural_advisories"

    id = Column(String(36), primary_key=True, default=generate_uuid)
    advisory_id = Column(String(80), unique=True, index=True, nullable=False)
    title = Column(String(300), nullable=False)
    target_region = Column(String(120), index=True, nullable=False)
    crops_affected = Column(Text, nullable=False)
    weather_context = Column(Text, nullable=False)
    actionable_guidance = Column(Text, nullable=False)
    source_agency = Column(String(200), nullable=False) # 'IMD Agromet', 'ICAR-CRIDA', 'PJTSAU'
    issued_date = Column(String(40), nullable=False)
    valid_until = Column(String(40))
    created_at = Column(DateTime(timezone=True), default=utc_now, nullable=False)


class MandiPriceRecord(Base):
    __tablename__ = "mandi_price_records"

    id = Column(String(36), primary_key=True, default=generate_uuid)
    mandi_id = Column(String(60), index=True, nullable=False)
    mandi_name = Column(String(150), index=True, nullable=False)
    district = Column(String(60), index=True, nullable=False)
    state = Column(String(60), index=True, nullable=False)
    commodity_name = Column(String(80), index=True, nullable=False)
    variety = Column(String(80))
    min_price_per_q = Column(Numeric(10, 2), nullable=False)
    max_price_per_q = Column(Numeric(10, 2), nullable=False)
    modal_price_per_q = Column(Numeric(10, 2), nullable=False)
    arrival_quantity_tonnes = Column(Numeric(10, 2), default=0.0)
    price_date = Column(String(40), index=True, nullable=False)
    source_portal = Column(String(100), default="Agmarknet", nullable=False)
    created_at = Column(DateTime(timezone=True), default=utc_now, nullable=False)


class MultilingualTerm(Base):
    __tablename__ = "multilingual_terms"

    id = Column(String(36), primary_key=True, default=generate_uuid)
    category = Column(String(60), index=True, nullable=False) # 'crop', 'pest', 'disease', 'chemical', 'agronomy'
    english_term = Column(String(150), index=True, nullable=False)
    telugu_term = Column(String(150), index=True, nullable=False)
    hindi_term = Column(String(150), index=True, nullable=False)
    scientific_name = Column(String(150))
    definition_en = Column(Text)
    definition_te = Column(Text)
    definition_hi = Column(Text)
    created_at = Column(DateTime(timezone=True), default=utc_now, nullable=False)


class VerifiedFAQ(Base):
    __tablename__ = "verified_faqs"

    id = Column(String(36), primary_key=True, default=generate_uuid)
    faq_id = Column(String(60), unique=True, index=True, nullable=False)
    crop_name = Column(String(80), index=True, nullable=False)
    category = Column(String(80), index=True, nullable=False)
    question_en = Column(Text, nullable=False)
    question_te = Column(Text, nullable=False)
    question_hi = Column(Text, nullable=False)
    verified_answer_en = Column(Text, nullable=False)
    verified_answer_te = Column(Text, nullable=False)
    verified_answer_hi = Column(Text, nullable=False)
    source_citation = Column(Text, nullable=False)
    is_published = Column(Boolean, default=True, nullable=False)
    created_at = Column(DateTime(timezone=True), default=utc_now, nullable=False)


class KnowledgeEvaluationCase(Base):
    __tablename__ = "knowledge_evaluation_cases"

    id = Column(String(36), primary_key=True, default=generate_uuid)
    test_id = Column(String(60), unique=True, index=True, nullable=False)
    category = Column(String(80), index=True, nullable=False)
    crop = Column(String(80), index=True, nullable=False)
    language = Column(String(10), default="en", nullable=False)
    question = Column(Text, nullable=False)
    expected_answer = Column(Text, nullable=False)
    expected_sources = Column(JSON, default=list) # List of source document IDs
    expected_pages = Column(JSON, default=list)
    expected_behavior = Column(String(40), default="answered", nullable=False) # 'answered', 'clarification', 'escalated'
    verification_status = Column(String(40), default="human_reviewed", nullable=False)
    created_at = Column(DateTime(timezone=True), default=utc_now, nullable=False)

