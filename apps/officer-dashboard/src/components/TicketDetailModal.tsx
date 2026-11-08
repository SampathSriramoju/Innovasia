import React from 'react';
import { TicketDetail } from '../types';
import { 
  X, 
  User, 
  MapPin, 
  ShieldAlert, 
  Calendar, 
  Sprout 
} from 'lucide-react';
import { PhotoInspectionViewer } from './PhotoInspectionViewer';
import { AudioWaveformPlayer } from './AudioWaveformPlayer';
import { CibrcPrescriptionBuilder } from './CibrcPrescriptionBuilder';

interface TicketDetailModalProps {
  ticket: TicketDetail;
  onClose: () => void;
  onResolve: (prescription: string, metadata?: any) => Promise<void>;
}

export const TicketDetailModal: React.FC<TicketDetailModalProps> = ({ 
  ticket, 
  onClose, 
  onResolve 
}) => {
  const isResolved = ticket.status === 'resolved';

  return (
    <div className="fixed inset-0 z-50 bg-slate-950/70 backdrop-blur-sm flex items-center justify-center p-2 sm:p-4 overflow-y-auto">
      <div className="bg-white rounded-3xl max-w-5xl w-full shadow-2xl border border-slate-200 overflow-hidden my-4 sm:my-8 flex flex-col max-h-[92vh]">
        {/* Modal Header */}
        <div className="bg-slate-900 text-white px-6 py-4 flex items-center justify-between border-b border-slate-800 flex-shrink-0">
          <div className="flex items-center space-x-3">
            <span className="font-mono text-xs sm:text-sm bg-forest-700 px-3 py-1 rounded-lg font-bold shadow-sm">
              {ticket.ticket_number}
            </span>
            <div>
              <div className="flex items-center space-x-2">
                <h2 className="text-base sm:text-lg font-bold font-serif">Agricultural Escalation Dossier</h2>
                <span className={`text-[10px] uppercase font-bold px-2 py-0.5 rounded-full ${
                  isResolved 
                    ? 'bg-emerald-900 text-emerald-300 border border-emerald-700' 
                    : 'bg-red-900 text-red-300 border border-red-700'
                }`}>
                  Priority: {ticket.priority}
                </span>
              </div>
              <p className="text-xs text-slate-400">
                Mandal Agricultural Officer (MAO) Human-in-the-Loop Triage Console
              </p>
            </div>
          </div>

          <button 
            type="button"
            onClick={onClose}
            className="text-slate-400 hover:text-white p-1.5 rounded-xl hover:bg-slate-800 transition-colors"
          >
            <X className="w-6 h-6" />
          </button>
        </div>

        {/* Modal Scrollable Body */}
        <div className="p-4 sm:p-6 space-y-6 overflow-y-auto flex-1">
          {/* Farmer Dossier & Land Profile Card */}
          <div className="bg-gradient-to-r from-slate-50 via-forest-50/20 to-slate-50 border border-slate-200 rounded-2xl p-4 sm:p-5 grid grid-cols-1 md:grid-cols-2 gap-4">
            <div className="flex items-start space-x-3.5">
              <div className="bg-forest-100 text-forest-800 p-3 rounded-2xl mt-0.5 shadow-xs">
                <User className="w-5 h-5 text-forest-700" />
              </div>
              <div className="space-y-0.5">
                <p className="text-[10px] text-slate-500 font-bold uppercase tracking-wider">
                  Farmer Identity & Landholding
                </p>
                <p className="text-base font-bold text-slate-900">{ticket.farmer.name}</p>
                <p className="text-xs text-slate-600 font-mono">Mobile: {ticket.farmer.phone}</p>
                <div className="flex items-center space-x-2 text-xs text-forest-800 pt-1 font-medium">
                  <Sprout className="w-3.5 h-3.5 text-forest-600" />
                  <span>{ticket.farmer.land_size_acres} Acres • {ticket.farmer.soil_type} Soil</span>
                </div>
              </div>
            </div>

            <div className="flex items-start space-x-3.5">
              <div className="bg-harvest-50 text-harvest-600 p-3 rounded-2xl mt-0.5 shadow-xs">
                <MapPin className="w-5 h-5 text-harvest-600" />
              </div>
              <div className="space-y-0.5">
                <p className="text-[10px] text-slate-500 font-bold uppercase tracking-wider">
                  Administrative Jurisdiction
                </p>
                <p className="text-base font-semibold text-slate-900">
                  {ticket.farmer.village}, {ticket.farmer.mandal} Mandal
                </p>
                <p className="text-xs text-slate-600">District: {ticket.farmer.district}, {ticket.farmer.state}</p>
                <p className="text-xs text-slate-500 pt-1 flex items-center space-x-1">
                  <Calendar className="w-3 h-3 text-slate-400" />
                  <span>Escalated: {new Date(ticket.created_at).toLocaleString()}</span>
                </p>
              </div>
            </div>
          </div>

          {/* Safety Gate Trigger Banner */}
          <div className="bg-red-50 border border-red-200 rounded-2xl p-4 sm:p-5 flex items-start space-x-3.5">
            <ShieldAlert className="w-6 h-6 text-red-600 flex-shrink-0 mt-0.5" />
            <div className="space-y-1">
              <div className="flex items-center space-x-2">
                <h4 className="text-sm font-bold text-red-900">
                  Deterministic Safety Gate Escalation Trigger
                </h4>
                <span className="text-[10px] bg-red-200 text-red-800 font-mono font-bold px-2 py-0.5 rounded">
                  BLOCKED FROM LLM
                </span>
              </div>
              <p className="text-xs text-red-800 leading-relaxed font-medium">
                {ticket.escalation_reason}
              </p>
            </div>
          </div>

          {/* Section 1: Crop Leaf Photo Inspection Viewer */}
          <div className="space-y-2">
            <h3 className="text-sm font-bold uppercase tracking-wider text-slate-800 flex items-center space-x-2">
              <span className="w-2 h-2 rounded-full bg-forest-600" />
              <span>Section 1: Macro Leaf Photography & EXIF Inspection</span>
            </h3>
            <PhotoInspectionViewer
              photos={ticket.photos}
              primaryPhotoUrl={ticket.photo_url || ticket.attached_image_urls?.[0]}
              exifData={ticket.exif_verification}
              cropName={ticket.crop_name || 'Crop Foliage'}
              diseaseOrPestName={ticket.target_pest_or_disease || 'Suspected Infestation'}
            />
          </div>

          {/* Section 2: Farmer Voice Recording Waveform & Bilingual Transcript */}
          <div className="space-y-2">
            <h3 className="text-sm font-bold uppercase tracking-wider text-slate-800 flex items-center space-x-2">
              <span className="w-2 h-2 rounded-full bg-blue-600" />
              <span>Section 2: Farmer Voice Recording & Synchronized Transcript</span>
            </h3>
            <AudioWaveformPlayer
              audioUrl={ticket.audio_recording_url}
              durationSeconds={ticket.audio_duration_seconds || 18}
              transcript={ticket.audio_transcript}
              farmerName={ticket.farmer.name}
            />
          </div>

          {/* Section 3: Guided CIBRC Prescription Builder */}
          <div className="space-y-2 pt-2 border-t border-slate-200">
            <h3 className="text-sm font-bold uppercase tracking-wider text-slate-800 flex items-center space-x-2">
              <span className="w-2 h-2 rounded-full bg-emerald-600" />
              <span>Section 3: Guided CIBRC Chemical & Cultural Prescription Builder</span>
            </h3>
            <CibrcPrescriptionBuilder
              farmer={ticket.farmer}
              cropName={ticket.crop_name || 'Cotton'}
              initialPrescription={ticket.resolution_prescription}
              isResolved={isResolved}
              onDispatch={async (prescriptionText, metadata) => {
                await onResolve(prescriptionText, metadata);
                onClose();
              }}
              onCancel={onClose}
            />
          </div>
        </div>
      </div>
    </div>
  );
};
