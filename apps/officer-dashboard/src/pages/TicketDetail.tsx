import React, { useState, useEffect } from 'react';
import { TicketDetail as TicketDetailType } from '../types';
import { api } from '../services/api';
import { 
  ArrowLeft, 
  RefreshCw, 
  User, 
  MapPin, 
  ShieldAlert, 
  Calendar, 
  Sprout 
} from 'lucide-react';
import { PhotoInspectionViewer } from '../components/PhotoInspectionViewer';
import { AudioWaveformPlayer } from '../components/AudioWaveformPlayer';
import { CibrcPrescriptionBuilder } from '../components/CibrcPrescriptionBuilder';

interface TicketDetailPageProps {
  ticketId?: string;
  onBack?: () => void;
  onResolved?: () => void;
}

export const TicketDetail: React.FC<TicketDetailPageProps> = ({ 
  ticketId = 'ticket-esc-001', 
  onBack,
  onResolved 
}) => {
  const [ticket, setTicket] = useState<TicketDetailType | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const loadTicket = async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await api.getTicketDetail(ticketId);
      setTicket(data);
    } catch (err) {
      setError((err as Error).message);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadTicket();
  }, [ticketId]);

  const handleResolve = async (prescription: string, metadata?: any) => {
    if (!ticket) return;
    await api.resolveTicket(ticket.id, prescription, metadata);
    await loadTicket();
    if (onResolved) onResolved();
  };

  if (loading) {
    return (
      <div className="max-w-6xl mx-auto px-4 py-16 text-center space-y-3">
        <RefreshCw className="w-8 h-8 animate-spin mx-auto text-forest-700" />
        <p className="text-sm font-semibold text-slate-700">Loading Agricultural Escalation Dossier...</p>
      </div>
    );
  }

  if (error || !ticket) {
    return (
      <div className="max-w-xl mx-auto px-4 py-16 text-center space-y-4">
        <div className="p-4 bg-red-50 text-red-800 rounded-2xl border border-red-200 text-sm">
          {error || 'Ticket not found'}
        </div>
        {onBack && (
          <button
            onClick={onBack}
            className="inline-flex items-center space-x-2 bg-slate-800 text-white px-4 py-2 rounded-xl text-xs font-bold"
          >
            <ArrowLeft className="w-4 h-4" />
            <span>Return to Triage Queue</span>
          </button>
        )}
      </div>
    );
  }

  const isResolved = ticket.status === 'resolved';

  return (
    <div className="max-w-6xl mx-auto px-4 sm:px-6 lg:px-8 py-8 space-y-6">
      {/* Navigation & Header Bar */}
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="flex items-center space-x-3">
          {onBack && (
            <button
              onClick={onBack}
              className="p-2 rounded-xl bg-white border border-slate-200 text-slate-700 hover:bg-slate-50 transition-colors shadow-xs"
              title="Return to Dashboard"
            >
              <ArrowLeft className="w-5 h-5" />
            </button>
          )}

          <div>
            <div className="flex items-center space-x-2">
              <span className="font-mono text-sm bg-forest-800 text-white px-3 py-0.5 rounded-lg font-bold">
                {ticket.ticket_number}
              </span>
              <span className={`text-xs uppercase font-bold px-2.5 py-0.5 rounded-full ${
                isResolved 
                  ? 'bg-emerald-100 text-emerald-800 border border-emerald-300' 
                  : 'bg-red-100 text-red-800 border border-red-300 animate-pulse'
              }`}>
                {ticket.priority} Priority • {ticket.status}
              </span>
            </div>
            <h1 className="text-xl font-bold font-serif text-slate-900 mt-1">
              Field Triage & Prescription Dossier
            </h1>
          </div>
        </div>

        <button
          onClick={loadTicket}
          className="flex items-center space-x-1.5 px-3 py-1.5 rounded-xl border border-slate-200 bg-white text-xs font-semibold text-slate-700 hover:bg-slate-50 transition-colors shadow-xs"
        >
          <RefreshCw className="w-3.5 h-3.5" />
          <span>Refresh Dossier</span>
        </button>
      </div>

      {/* Farmer Dossier Banner */}
      <div className="bg-white border border-slate-200 rounded-3xl p-5 sm:p-6 shadow-sm grid grid-cols-1 md:grid-cols-2 gap-4">
        <div className="flex items-start space-x-4">
          <div className="bg-forest-100 text-forest-800 p-3.5 rounded-2xl shadow-xs">
            <User className="w-6 h-6 text-forest-700" />
          </div>
          <div>
            <p className="text-[10px] text-slate-500 font-bold uppercase tracking-wider">Farmer Profile</p>
            <p className="text-lg font-bold text-slate-900">{ticket.farmer.name}</p>
            <p className="text-xs text-slate-600 font-mono">Mobile: {ticket.farmer.phone}</p>
            <div className="flex items-center space-x-2 text-xs text-forest-800 pt-1 font-medium">
              <Sprout className="w-3.5 h-3.5 text-forest-600" />
              <span>{ticket.farmer.land_size_acres} Acres • {ticket.farmer.soil_type} Soil</span>
            </div>
          </div>
        </div>

        <div className="flex items-start space-x-4">
          <div className="bg-harvest-50 text-harvest-600 p-3.5 rounded-2xl shadow-xs">
            <MapPin className="w-6 h-6 text-harvest-600" />
          </div>
          <div>
            <p className="text-[10px] text-slate-500 font-bold uppercase tracking-wider">Location & Date</p>
            <p className="text-base font-semibold text-slate-900">
              {ticket.farmer.village}, {ticket.farmer.mandal} Mandal
            </p>
            <p className="text-xs text-slate-600">District: {ticket.farmer.district}, {ticket.farmer.state}</p>
            <p className="text-xs text-slate-500 pt-1 flex items-center space-x-1">
              <Calendar className="w-3.5 h-3.5 text-slate-400" />
              <span>Escalated: {new Date(ticket.created_at).toLocaleString()}</span>
            </p>
          </div>
        </div>
      </div>

      {/* Safety Gate Alert */}
      <div className="bg-red-50 border border-red-200 rounded-2xl p-5 flex items-start space-x-3.5 shadow-xs">
        <ShieldAlert className="w-6 h-6 text-red-600 flex-shrink-0 mt-0.5" />
        <div>
          <h4 className="text-sm font-bold text-red-900">Deterministic Safety Gate Escalation Reason</h4>
          <p className="text-xs text-red-800 mt-1 leading-relaxed font-medium">
            {ticket.escalation_reason}
          </p>
        </div>
      </div>

      {/* Optical Inspection Viewer */}
      <div className="bg-white border border-slate-200 rounded-3xl p-5 sm:p-6 shadow-sm space-y-4">
        <h2 className="text-sm font-bold uppercase tracking-wider text-slate-900 flex items-center space-x-2">
          <span className="w-2.5 h-2.5 rounded-full bg-forest-600" />
          <span>Section 1: Macro Leaf Photography & EXIF Inspection</span>
        </h2>
        <PhotoInspectionViewer
          photos={ticket.photos}
          primaryPhotoUrl={ticket.photo_url || ticket.attached_image_urls?.[0]}
          exifData={ticket.exif_verification}
          cropName={ticket.crop_name || 'Crop Foliage'}
          diseaseOrPestName={ticket.target_pest_or_disease || 'Symptom Inspection'}
        />
      </div>

      {/* Audio Waveform & Synced Bilingual Transcript */}
      <div className="bg-white border border-slate-200 rounded-3xl p-5 sm:p-6 shadow-sm space-y-4">
        <h2 className="text-sm font-bold uppercase tracking-wider text-slate-900 flex items-center space-x-2">
          <span className="w-2.5 h-2.5 rounded-full bg-blue-600" />
          <span>Section 2: Farmer Voice Recording & Synchronized Bilingual Transcript</span>
        </h2>
        <AudioWaveformPlayer
          audioUrl={ticket.audio_recording_url}
          durationSeconds={ticket.audio_duration_seconds || 18}
          transcript={ticket.audio_transcript}
          farmerName={ticket.farmer.name}
        />
      </div>

      {/* Guided CIBRC Prescription Builder */}
      <div className="bg-white border border-slate-200 rounded-3xl p-5 sm:p-6 shadow-sm space-y-4">
        <h2 className="text-sm font-bold uppercase tracking-wider text-slate-900 flex items-center space-x-2">
          <span className="w-2.5 h-2.5 rounded-full bg-emerald-600" />
          <span>Section 3: Guided CIBRC Chemical & Cultural Prescription Builder</span>
        </h2>
        <CibrcPrescriptionBuilder
          farmer={ticket.farmer}
          cropName={ticket.crop_name || 'Cotton'}
          initialPrescription={ticket.resolution_prescription}
          isResolved={isResolved}
          onDispatch={handleResolve}
          onCancel={onBack}
        />
      </div>
    </div>
  );
};
