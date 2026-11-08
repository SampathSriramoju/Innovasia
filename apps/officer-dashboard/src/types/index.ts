export interface OfficerUser {
  id: string;
  officer_code: string;
  designation: string;
  assigned_district: string;
  assigned_mandals: string[];
  name?: string;
  department?: string;
}

export interface FarmerDossier {
  id: string;
  name: string;
  phone: string;
  village: string;
  mandal: string;
  district: string;
  state: string;
  land_size_acres: number;
  soil_type: string;
  primary_crop?: string;
}

export interface ExifVerification {
  latitude: number;
  longitude: number;
  location_name: string;
  captured_at: string;
  device_model: string;
  camera_make?: string;
  exposure?: string;
  focal_length?: string;
  iso?: number;
  is_timestamp_verified: boolean;
  is_gps_verified: boolean;
  is_device_sanitized: boolean;
  tamper_check_status: 'passed' | 'warning' | 'unverified';
  hash_fingerprint?: string;
}

export interface PhotoAnnotation {
  id: string;
  label: string;
  x: number; // percentage from left
  y: number; // percentage from top
  severity?: 'low' | 'moderate' | 'severe';
  description?: string;
}

export interface AttachedPhoto {
  url: string;
  thumbnail_url?: string;
  caption?: string;
  tag?: 'leaf_dorsal' | 'leaf_ventral' | 'field_overview' | 'pest_closeup';
  exif?: ExifVerification;
  annotations?: PhotoAnnotation[];
}

export interface AudioTranscriptSegment {
  start_ms: number;
  end_ms: number;
  text_te?: string;
  text_hi?: string;
  text_en: string;
}

export interface SyncedTranscript {
  original_language: 'te' | 'hi' | 'en';
  language_label: string;
  original_text: string;
  phonetic_gloss?: string;
  english_translation: string;
  segments?: AudioTranscriptSegment[];
}

export interface CibrcPesticideRecord {
  id: string;
  product_trade_name: string;
  manufacturer: string;
  active_ingredient: string;
  cibrc_registration_number: string;
  formulation_type: string;
  toxicity_band: 'green_slightly_toxic' | 'blue_moderately_toxic' | 'yellow_highly_toxic' | 'red_extremely_toxic';
  target_crop: string;
  target_pest_or_disease: string;
  dosage_per_hectare: string;
  dosage_per_acre: string;
  dosage_per_15l_pump: string;
  dilution_water_liters: string;
  pre_harvest_interval_days: number;
  max_applications_per_season: number;
  application_method: string;
  safety_precautions: string;
  protective_gear_required: string[];
  antidote_statement?: string;
}

export interface TicketSummary {
  id: string;
  ticket_number: string;
  farmer_id: string;
  farmer_name: string;
  farmer_phone: string;
  state: string;
  district: string;
  mandal: string;
  status: 'open' | 'under_review' | 'resolved' | 'closed';
  priority: 'low' | 'medium' | 'high' | 'emergency';
  escalation_reason: string;
  original_question_transcript: string;
  crop_name?: string;
  target_pest?: string;
  photo_url?: string;
  attached_image_urls?: string[];
  audio_recording_url?: string;
  has_photo?: boolean;
  has_audio?: boolean;
  resolution_prescription?: string;
  created_at: string;
  resolved_at?: string;
}

export interface TicketDetail {
  id: string;
  ticket_number: string;
  farmer: FarmerDossier;
  status: 'open' | 'under_review' | 'resolved' | 'closed' | string;
  priority: 'low' | 'medium' | 'high' | 'emergency' | string;
  crop_name?: string;
  target_pest_or_disease?: string;
  escalation_reason: string;
  original_question_transcript: string;
  audio_recording_url?: string;
  audio_duration_seconds?: number;
  audio_transcript?: SyncedTranscript;
  attached_image_urls: string[];
  photo_url?: string;
  photos?: AttachedPhoto[];
  exif_verification?: ExifVerification;
  resolution_prescription?: string;
  prescribed_chemical?: string;
  prescribed_dosage?: string;
  prescribed_phi_days?: number;
  safety_equipment?: string[];
  push_voice_to_farmer?: boolean;
  created_at: string;
  resolved_at?: string;
}
