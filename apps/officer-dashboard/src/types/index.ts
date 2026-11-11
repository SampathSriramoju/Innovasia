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

export interface TicketChatMessage {
  id: string;
  ticket_id: string;
  sender_id: string;
  sender_type: 'FARMER' | 'OFFICER' | 'AI';
  sender_name: string;
  msg_type: 'text' | 'image' | 'prescription';
  content: string;
  media_url?: string;
  created_at: string;
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

export interface SmsMessage {
  id: string;
  sender_number: string;
  recipient_number?: string;
  direction: 'inbound' | 'outbound';
  message_text: string;
  language?: string;
  char_count?: number;
  segments?: number;
  delivery_status: 'SIMULATED' | 'DELIVERED' | 'FAILED' | 'PENDING' | string;
  created_at: string;
  safety_warning?: boolean;
  ticket_escalated?: boolean;
  ticket_id?: string;
}

export interface SmsIncomingPayload {
  sender_number: string;
  message_text: string;
  language?: string;
  message_id?: string;
  idempotency_key?: string;
  simulate_failure?: boolean;
}

export interface SmsIncomingResponse {
  success?: boolean;
  status?: string;
  reply_text?: string;
  reply?: any;
  inbound?: any;
  inbound_message?: SmsMessage;
  outbound_message?: SmsMessage;
  delivery_status?: string;
  safety_alert?: boolean;
  ticket_escalated?: boolean;
  ticket_id?: string;
  is_demo?: boolean;
  is_simulated?: boolean;
  is_duplicate?: boolean;
  error?: string;
}

export interface SmsHistoryResponse {
  phone_number: string;
  total_messages: number;
  messages: SmsMessage[];
}

export interface SmsGatewayStatus {
  status: string;
  short_code: string;
  mock_mode: boolean;
  supported_languages: string[];
  total_messages_processed?: number;
}

// ==========================================
// IVR Fallback Telephony Interfaces
// ==========================================

export interface IvrTranscriptTurn {
  id: string;
  timestamp: string;
  speaker: 'system' | 'farmer';
  text: string;
  language?: 'te' | 'hi' | 'en' | string;
  dtmf_key?: string;
  turn_type?: 'greeting' | 'dtmf_selection' | 'voice_query' | 'ai_response' | 'safety_warning' | 'escalation' | 'error_recovery' | 'goodbye';
  is_safety_flagged?: boolean;
  safety_alert?: string;
  ticket_escalated?: boolean;
  ticket_id?: string;
  confidence?: number;
  audio_url?: string;
}

export interface IvrCallSession {
  call_id: string;
  caller_number: string;
  farmer_name?: string;
  farmer_location?: string;
  language: 'te' | 'hi' | 'en' | string;
  status: 'ringing' | 'connected' | 'in_call' | 'escalated' | 'ended' | string;
  started_at: string;
  duration_seconds: number;
  current_menu?: string;
  last_prompt?: string;
  last_user_speech?: string;
  dtmf_history: string[];
  transcript_turns: IvrTranscriptTurn[];
  ticket_id?: string;
  is_safety_flagged?: boolean;
  created_at?: string;
  ended_at?: string;
}

export interface IvrCallResponse {
  success: boolean;
  call_id: string;
  status: 'ringing' | 'connected' | 'in_call' | 'escalated' | 'ended' | string;
  spoken_prompt: string;
  prompt_audio_url?: string;
  language: 'te' | 'hi' | 'en' | string;
  current_menu?: string;
  ticket_escalated?: boolean;
  ticket_id?: string;
  safety_alert?: boolean;
  safety_warning?: string;
  error?: string;
  is_simulated?: boolean;
  session?: IvrCallSession;
  transcript_turn?: IvrTranscriptTurn;
}

export interface IvrDtmfPayload {
  call_id: string;
  digit: string;
  caller_number?: string;
}

export interface IvrVoicePayload {
  call_id: string;
  speech_text: string;
  language?: 'te' | 'hi' | 'en' | string;
  confidence?: number;
  is_simulated_transcript?: boolean;
  caller_number?: string;
}

export interface IvrErrorPayload {
  call_id: string;
  error_type: 'speech_recognition_failure' | 'timeout_silence' | 'backend_failure' | string;
  language?: 'te' | 'hi' | 'en' | string;
  caller_number?: string;
}


