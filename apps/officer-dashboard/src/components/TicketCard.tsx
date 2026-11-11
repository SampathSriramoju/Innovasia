import React from 'react';
import { TicketSummary } from '../types';
import { 
  AlertTriangle, 
  Clock, 
  ChevronRight, 
  Phone, 
  Camera, 
  Mic, 
  MapPin, 
  User 
} from 'lucide-react';

interface TicketCardProps {
  ticket: TicketSummary;
  onSelect: (ticket: TicketSummary) => void;
}

export const TicketCard: React.FC<TicketCardProps> = ({ ticket, onSelect }) => {
  const isEmergency = ticket.priority?.toLowerCase() === 'high' || ticket.priority?.toLowerCase() === 'emergency';
  const isResolved = ticket.status?.toLowerCase() === 'resolved' || ticket.status?.toLowerCase() === 'closed';

  const photoThumbnail = ticket.photo_url || (ticket.attached_image_urls && ticket.attached_image_urls[0]);

  return (
    <div className={`bg-white rounded-2xl shadow-sm border transition-all hover:shadow-md overflow-hidden ${
      isEmergency 
        ? 'border-red-200 bg-gradient-to-r from-red-50/20 via-white to-white' 
        : isResolved 
        ? 'border-green-200 bg-gradient-to-r from-green-50/10 via-white to-white'
        : 'border-slate-200'
    }`}>
      <div className="p-5 sm:p-6 space-y-4">
        {/* Top Header: ID, Priority, Badges, Time */}
        <div className="flex flex-wrap items-center justify-between gap-2">
          <div className="flex flex-wrap items-center gap-2">
            <span className="font-mono text-xs font-bold text-slate-700 bg-slate-100 px-2.5 py-1 rounded-md border border-slate-200">
              {ticket.ticket_number}
            </span>

            <span className={`text-[11px] px-2.5 py-0.5 rounded-full font-bold uppercase tracking-wider ${
              isResolved 
                ? 'bg-green-100 text-green-800 border border-green-300' 
                : isEmergency
                ? 'bg-red-100 text-red-800 border border-red-300 animate-pulse'
                : 'bg-amber-100 text-amber-800 border border-amber-300'
            }`}>
              {ticket.status}
            </span>

            {isEmergency && (
              <span className="flex items-center space-x-1 text-xs text-red-600 font-bold bg-red-50 px-2 py-0.5 rounded border border-red-200">
                <AlertTriangle className="w-3.5 h-3.5" />
                <span>Urgent Safety Alert</span>
              </span>
            )}

            {ticket.crop_name && (
              <span className="text-xs bg-forest-50 text-forest-800 px-2 py-0.5 rounded-md font-semibold border border-forest-200">
                {ticket.crop_name}
              </span>
            )}
          </div>

          <div className="flex items-center text-xs text-slate-400 space-x-1">
            <Clock className="w-3.5 h-3.5" />
            <span>{new Date(ticket.created_at).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}</span>
          </div>
        </div>

        {/* Center: Leaf Photo Preview Thumbnail + Question Transcript + Escalation */}
        <div className="flex flex-col sm:flex-row gap-4 items-start">
          {photoThumbnail ? (
            <div className="relative flex-shrink-0 w-24 h-24 sm:w-28 sm:h-28 rounded-xl overflow-hidden border border-slate-200 group bg-slate-900">
              <img
                src={photoThumbnail}
                alt="Leaf Inspection Preview"
                className="w-full h-full object-cover transition-transform duration-300 group-hover:scale-110"
              />
              <div className="absolute inset-x-0 bottom-0 bg-slate-950/85 backdrop-blur-xs text-[10px] text-white py-1 px-1.5 flex items-center justify-between">
                <span className="flex items-center space-x-1 font-mono text-emerald-400">
                  <Camera className="w-3 h-3" />
                  <span>Optical</span>
                </span>
                <span className="text-[9px] text-slate-300">EXIF ✓</span>
              </div>
            </div>
          ) : (
            <div className="flex-shrink-0 w-24 h-24 sm:w-28 sm:h-28 rounded-xl bg-forest-50 border border-forest-200 flex flex-col items-center justify-center text-forest-700 text-center p-2">
              <Mic className="w-6 h-6 mb-1 text-forest-600 animate-pulse" />
              <span className="text-[10px] font-bold">Voice Query</span>
              <span className="text-[9px] text-forest-500">No Photo</span>
            </div>
          )}

          <div className="flex-1 space-y-2">
            <p className="text-sm font-semibold text-slate-900 line-clamp-2 leading-relaxed">
              "{ticket.original_question_transcript}"
            </p>

            <div className="p-2.5 rounded-xl bg-red-50/80 border border-red-200/80 text-xs text-red-900 flex items-start space-x-2">
              <AlertTriangle className="w-4 h-4 text-red-600 flex-shrink-0 mt-0.5" />
              <div>
                <span className="font-bold">Escalation Trigger: </span>
                <span className="font-medium text-red-800">{ticket.escalation_reason}</span>
              </div>
            </div>

            {/* Media badges */}
            <div className="flex items-center space-x-3 text-xs text-slate-500 pt-1">
              {photoThumbnail && (
                <span className="flex items-center space-x-1 text-emerald-700 bg-emerald-50 px-2 py-0.5 rounded font-medium border border-emerald-200">
                  <Camera className="w-3 h-3 text-emerald-600" />
                  <span>Photo Inspected & EXIF Verified</span>
                </span>
              )}
              {ticket.audio_recording_url && (
                <span className="flex items-center space-x-1 text-blue-700 bg-blue-50 px-2 py-0.5 rounded font-medium border border-blue-200">
                  <Mic className="w-3 h-3 text-blue-600" />
                  <span>Spoken Voice Available</span>
                </span>
              )}
            </div>
          </div>
        </div>

        {/* Footer: Farmer identity + CTA button */}
        <div className="pt-3 border-t border-slate-100 flex flex-wrap items-center justify-between gap-3">
          <div className="flex flex-wrap items-center space-x-2 text-xs text-slate-600">
            <span className="flex items-center space-x-1 font-bold text-slate-900">
              <User className="w-3.5 h-3.5 text-forest-700" />
              <span>{ticket.farmer_name}</span>
            </span>
            <span>•</span>
            <span className="flex items-center space-x-1 text-slate-600">
              <MapPin className="w-3 h-3 text-harvest-500" />
              <span>{ticket.mandal}, {ticket.district}</span>
            </span>
            {ticket.farmer_phone && (
              <>
                <span>•</span>
                <span className="flex items-center space-x-1 text-slate-500 font-mono">
                  <Phone className="w-3 h-3 text-slate-400" />
                  <span>{ticket.farmer_phone}</span>
                </span>
              </>
            )}
          </div>

          <button
            type="button"
            onClick={() => onSelect(ticket)}
            className={`flex items-center space-x-1.5 text-xs font-bold px-4 py-2 rounded-xl transition-all shadow-xs ${
              isResolved
                ? 'bg-slate-100 hover:bg-slate-200 text-slate-800 border border-slate-200'
                : 'bg-forest-700 hover:bg-forest-800 text-white shadow-forest-800/20 active:scale-98'
            }`}
          >
            <span>{isResolved ? 'View Prescribed Resolution' : 'Review, Inspect & Prescribe'}</span>
            <ChevronRight className="w-4 h-4" />
          </button>
        </div>
      </div>
    </div>
  );
};
