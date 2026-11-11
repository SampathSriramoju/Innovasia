import React, { useState, useEffect, useRef } from 'react';
import { TicketDetail, TicketChatMessage } from '../types';
import { api } from '../services/api';
import { 
  X, 
  User, 
  MapPin, 
  ShieldAlert, 
  Calendar, 
  Sprout,
  Send,
  MessageSquare,
  CheckCircle2,
  RotateCcw,
  RefreshCw,
  Image as ImageIcon,
  Bot,
  Clock,
  Sparkles,
  Phone
} from 'lucide-react';
import { PhotoInspectionViewer } from './PhotoInspectionViewer';
import { AudioWaveformPlayer } from './AudioWaveformPlayer';
import { CibrcPrescriptionBuilder } from './CibrcPrescriptionBuilder';

interface TicketDetailModalProps {
  ticket: TicketDetail;
  onClose: () => void;
  onResolve: (prescription: string, metadata?: any) => Promise<void>;
  onStatusChange?: (newStatus: string) => void;
}

export const TicketDetailModal: React.FC<TicketDetailModalProps> = ({ 
  ticket, 
  onClose, 
  onResolve,
  onStatusChange
}) => {
  const [currentStatus, setCurrentStatus] = useState<string>(ticket.status || 'open');
  const [messages, setMessages] = useState<TicketChatMessage[]>([]);
  const [isLoadingMessages, setIsLoadingMessages] = useState<boolean>(true);
  const [isSending, setIsSending] = useState<boolean>(false);
  const [isUpdatingStatus, setIsUpdatingStatus] = useState<boolean>(false);
  const [replyText, setReplyText] = useState<string>('');
  const [mediaUrlInput, setMediaUrlInput] = useState<string>('');
  const [showMediaInput, setShowMediaInput] = useState<boolean>(false);
  const chatBottomRef = useRef<HTMLDivElement | null>(null);

  const isResolved = currentStatus.toLowerCase() === 'resolved';

  // Periodic polling (every 4-5s) while modal is open so farmer follow-ups appear automatically
  useEffect(() => {
    let isMounted = true;

    const fetchMessages = async () => {
      try {
        const data = await api.getTicketMessages(ticket.id);
        if (isMounted && Array.isArray(data)) {
          setMessages(data);
        }
      } catch (err) {
        console.warn('Polling ticket messages failed or endpoint not available yet:', err);
      } finally {
        if (isMounted) {
          setIsLoadingMessages(false);
        }
      }
    };

    fetchMessages();
    const intervalId = setInterval(fetchMessages, 4000);

    return () => {
      isMounted = false;
      clearInterval(intervalId);
    };
  }, [ticket.id]);

  // Auto-scroll to bottom of chat when new messages arrive
  useEffect(() => {
    chatBottomRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages.length]);

  const handleStatusUpdate = async (newStatus: string) => {
    setIsUpdatingStatus(true);
    try {
      await api.updateTicketStatus(ticket.id, newStatus);
      setCurrentStatus(newStatus);
      if (onStatusChange) {
        onStatusChange(newStatus);
      }
    } catch (err) {
      console.warn('Status update API error, falling back to local state:', err);
      setCurrentStatus(newStatus);
      if (onStatusChange) {
        onStatusChange(newStatus);
      }
    } finally {
      setIsUpdatingStatus(false);
    }
  };

  const handleSendMessage = async () => {
    if (!replyText.trim() || isSending) return;
    const textToSend = replyText.trim();
    const mediaToSend = mediaUrlInput.trim() || undefined;
    setIsSending(true);

    try {
      const created = await api.sendTicketMessage(ticket.id, textToSend, mediaToSend);
      setMessages((prev) => [...prev, created]);
      setReplyText('');
      setMediaUrlInput('');
      setShowMediaInput(false);
    } catch (err) {
      console.warn('sendTicketMessage API error, creating optimistic message for session:', err);
      const optimisticMsg: TicketChatMessage = {
        id: `local-${Date.now()}`,
        ticket_id: ticket.id,
        sender_id: 'officer-current',
        sender_type: 'OFFICER',
        sender_name: 'Mandal Agricultural Officer (MAO)',
        msg_type: mediaToSend ? 'image' : 'text',
        content: textToSend,
        media_url: mediaToSend,
        created_at: new Date().toISOString()
      };
      setMessages((prev) => [...prev, optimisticMsg]);
      setReplyText('');
      setMediaUrlInput('');
      setShowMediaInput(false);
    } finally {
      setIsSending(false);
      setTimeout(() => {
        chatBottomRef.current?.scrollIntoView({ behavior: 'smooth' });
      }, 50);
    }
  };

  // Compile full display message list, ensuring the initial query is visible
  const displayMessages: TicketChatMessage[] = messages.length > 0
    ? messages
    : [
        {
          id: `initial-farmer-${ticket.id}`,
          ticket_id: ticket.id,
          sender_id: ticket.farmer.id || 'farmer-id',
          sender_type: 'FARMER' as const,
          sender_name: ticket.farmer.name || 'Farmer',
          msg_type: ticket.attached_image_urls?.length ? ('image' as const) : ('text' as const),
          content: ticket.original_question_transcript || ticket.escalation_reason || 'Escalated agricultural issue.',
          media_url: ticket.photo_url || ticket.attached_image_urls?.[0],
          created_at: ticket.created_at
        },
        ...(ticket.resolution_prescription ? [{
          id: `initial-officer-${ticket.id}`,
          ticket_id: ticket.id,
          sender_id: 'officer-id',
          sender_type: 'OFFICER' as const,
          sender_name: 'Mandal Agricultural Officer (MAO)',
          msg_type: 'prescription' as const,
          content: ticket.resolution_prescription,
          created_at: ticket.resolved_at || ticket.created_at
        }] : [])
      ];

  const quickAdviceTemplates = [
    "Spray Azadirachtin (Neem Oil 1500 ppm) 5ml/L water immediately.",
    "Irrigate field lightly before applying recommended treatment.",
    "Avoid spraying during high midday heat (11:00 AM - 3:00 PM).",
    "Wear protective face mask and nitrile gloves during spraying.",
    "Please send an updated photograph of the leaf underside in 48 hours."
  ];

  return (
    <div className="fixed inset-0 z-50 bg-slate-950/75 backdrop-blur-sm flex items-center justify-center p-2 sm:p-4 overflow-y-auto">
      <div className="bg-white rounded-3xl max-w-5xl w-full shadow-2xl border border-slate-200 overflow-hidden my-4 sm:my-8 flex flex-col max-h-[94vh]">
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
                <span className={`text-[10px] uppercase font-extrabold px-2.5 py-0.5 rounded-full flex items-center space-x-1 ${
                  isResolved
                    ? 'bg-emerald-800 text-emerald-200 border border-emerald-600'
                    : 'bg-amber-800 text-amber-200 border border-amber-600'
                }`}>
                  <span className={`w-1.5 h-1.5 rounded-full ${isResolved ? 'bg-emerald-400' : 'bg-amber-400 animate-ping'}`} />
                  <span>Status: {currentStatus}</span>
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
                <p className="text-xs text-slate-600 font-mono flex items-center space-x-1">
                  <Phone className="w-3 h-3 text-slate-400 inline" />
                  <span>Mobile: {ticket.farmer.phone}</span>
                </p>
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

          {/* Dedicated Interactive Farmer–Officer Chat & Reply Console */}
          <div className="border border-slate-200 rounded-3xl overflow-hidden shadow-sm bg-white">
            {/* Chat Header & Status Controls */}
            <div className="bg-slate-900 text-white px-5 py-3.5 flex flex-wrap items-center justify-between gap-3 border-b border-slate-800">
              <div className="flex items-center space-x-2.5">
                <div className="p-2 bg-forest-800/80 rounded-xl text-emerald-300">
                  <MessageSquare className="w-4 h-4" />
                </div>
                <div>
                  <div className="flex items-center space-x-2">
                    <h3 className="text-sm font-bold tracking-tight">Interactive Farmer–Officer Support Inbox</h3>
                    <span className={`text-[10px] uppercase font-bold px-2 py-0.5 rounded-full flex items-center space-x-1 ${
                      isResolved
                        ? 'bg-emerald-950 text-emerald-300 border border-emerald-700'
                        : 'bg-amber-950 text-amber-300 border border-amber-700'
                    }`}>
                      <span className={`w-1.5 h-1.5 rounded-full ${isResolved ? 'bg-emerald-400' : 'bg-amber-400 animate-pulse'}`} />
                      <span>{currentStatus.toUpperCase()}</span>
                    </span>
                  </div>
                  <p className="text-[11px] text-slate-400">
                    Live bidirectional advisory thread • Synchronized with Mobile App & SMS Gateway
                  </p>
                </div>
              </div>

              {/* Status Action Controls */}
              <div className="flex items-center space-x-2">
                {isResolved ? (
                  <button
                    type="button"
                    disabled={isUpdatingStatus}
                    onClick={() => handleStatusUpdate('open')}
                    className="px-3.5 py-1.5 bg-slate-800 hover:bg-slate-700 text-amber-300 border border-slate-700 rounded-xl text-xs font-bold flex items-center space-x-1.5 shadow-sm transition-all disabled:opacity-50"
                  >
                    {isUpdatingStatus ? <RefreshCw className="w-3.5 h-3.5 animate-spin" /> : <RotateCcw className="w-3.5 h-3.5" />}
                    <span>Reopen Ticket</span>
                  </button>
                ) : (
                  <button
                    type="button"
                    disabled={isUpdatingStatus}
                    onClick={() => handleStatusUpdate('resolved')}
                    className="px-3.5 py-1.5 bg-emerald-600 hover:bg-emerald-700 text-white rounded-xl text-xs font-bold flex items-center space-x-1.5 shadow-sm transition-all disabled:opacity-50"
                  >
                    {isUpdatingStatus ? <RefreshCw className="w-3.5 h-3.5 animate-spin" /> : <CheckCircle2 className="w-3.5 h-3.5" />}
                    <span>Mark as Resolved</span>
                  </button>
                )}
              </div>
            </div>

            {/* Chat Message Stream */}
            <div className="p-4 sm:p-5 space-y-4 bg-slate-50/70 max-h-96 overflow-y-auto">
              {isLoadingMessages && messages.length === 0 ? (
                <div className="py-8 text-center text-slate-400 text-xs flex items-center justify-center space-x-2">
                  <RefreshCw className="w-4 h-4 animate-spin text-forest-600" />
                  <span>Loading advisory history...</span>
                </div>
              ) : displayMessages.length === 0 ? (
                <div className="py-8 text-center text-slate-400 text-xs">
                  No previous messages. Use the composer below to send guidance to the farmer.
                </div>
              ) : (
                displayMessages.map((msg) => {
                  const isOfficer = msg.sender_type === 'OFFICER';
                  const isAi = msg.sender_type === 'AI';

                  if (isOfficer) {
                    return (
                      <div key={msg.id} className="flex flex-col items-end space-y-1">
                        <div className="flex items-center space-x-2 text-[10px] text-slate-400 pr-1">
                          <span className="flex items-center space-x-1">
                            <Clock className="w-3 h-3 inline" />
                            <span>{new Date(msg.created_at).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}</span>
                          </span>
                          <span className="bg-forest-800 text-emerald-300 font-bold px-1.5 py-0.5 rounded text-[9px]">
                            MAO Extension
                          </span>
                          <span className="font-semibold text-slate-700">
                            {msg.sender_name || 'Mandal Agricultural Officer'}
                          </span>
                        </div>

                        <div className="bg-forest-900 border border-forest-800 text-white rounded-2xl rounded-tr-xs p-4 shadow-md max-w-xl text-left space-y-2">
                          {msg.msg_type === 'prescription' && (
                            <div className="inline-flex items-center space-x-1.5 text-[10px] bg-forest-800/90 text-emerald-300 px-2 py-0.5 rounded font-mono font-bold tracking-wide">
                              <Sparkles className="w-3 h-3" />
                              <span>OFFICIAL CIBRC PRESCRIPTION DISPATCHED</span>
                            </div>
                          )}
                          <p className="text-xs sm:text-sm leading-relaxed whitespace-pre-wrap font-medium">
                            {msg.content}
                          </p>
                          {msg.media_url && (
                            <div className="mt-2 rounded-xl overflow-hidden border border-forest-700 bg-forest-950/60 max-w-sm">
                              <img
                                src={msg.media_url}
                                alt="Prescription diagram or reference"
                                className="w-full h-40 object-cover hover:scale-105 transition-transform cursor-pointer"
                                onClick={() => window.open(msg.media_url, '_blank')}
                              />
                              <div className="px-3 py-1 text-[10px] text-emerald-200 flex justify-between">
                                <span>Attached Guidance Media</span>
                                <span className="underline cursor-pointer">Open ↗</span>
                              </div>
                            </div>
                          )}
                        </div>
                      </div>
                    );
                  }

                  if (isAi) {
                    return (
                      <div key={msg.id} className="flex flex-col items-center my-2">
                        <div className="bg-indigo-50 border border-indigo-200 rounded-xl px-4 py-2 text-indigo-900 text-xs max-w-lg shadow-2xs flex items-start space-x-2">
                          <Bot className="w-4 h-4 text-indigo-600 flex-shrink-0 mt-0.5" />
                          <div>
                            <span className="font-bold text-[10px] uppercase tracking-wider text-indigo-700 block">
                              AI Diagnostic Assistant
                            </span>
                            <p className="text-xs leading-relaxed">{msg.content}</p>
                          </div>
                        </div>
                      </div>
                    );
                  }

                  // Default Farmer Message
                  return (
                    <div key={msg.id} className="flex flex-col items-start space-y-1">
                      <div className="flex items-center space-x-2 text-[10px] text-slate-500 pl-1">
                        <span className="w-4 h-4 rounded-full bg-emerald-100 text-emerald-800 flex items-center justify-center font-bold text-[10px]">
                          🌾
                        </span>
                        <span className="font-bold text-slate-800 text-xs">
                          {msg.sender_name || ticket.farmer.name}
                        </span>
                        <span className="bg-emerald-100 text-emerald-800 px-1.5 py-0.2 rounded font-semibold text-[9px]">
                          Farmer
                        </span>
                        <span className="text-slate-400 font-mono">
                          {ticket.farmer.phone}
                        </span>
                        <span className="text-slate-400 flex items-center space-x-1">
                          <Clock className="w-3 h-3 inline" />
                          <span>{new Date(msg.created_at).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}</span>
                        </span>
                      </div>

                      <div className="bg-white border border-slate-200 rounded-2xl rounded-tl-xs p-4 shadow-xs max-w-xl text-left space-y-2">
                        <p className="text-xs sm:text-sm text-slate-800 leading-relaxed font-normal whitespace-pre-wrap">
                          {msg.content}
                        </p>

                        {/* Attached photo preview */}
                        {msg.media_url ? (
                          <div className="mt-2 rounded-xl overflow-hidden border border-slate-200 bg-slate-100 max-w-sm">
                            <img
                              src={msg.media_url}
                              alt="Farmer attached foliage"
                              className="w-full h-44 object-cover hover:scale-105 transition-transform cursor-pointer"
                              onClick={() => window.open(msg.media_url, '_blank')}
                            />
                            <div className="px-3 py-1.5 flex items-center justify-between text-[11px] text-slate-600 bg-slate-50">
                              <span>Farmer Attached Foliage</span>
                              <span 
                                className="text-forest-700 font-bold hover:underline cursor-pointer"
                                onClick={() => window.open(msg.media_url, '_blank')}
                              >
                                View Full Size ↗
                              </span>
                            </div>
                          </div>
                        ) : ticket.attached_image_urls && ticket.attached_image_urls.length > 0 && msg.id.startsWith('initial-farmer') ? (
                          <div className="mt-2 grid grid-cols-2 gap-2 max-w-sm">
                            {ticket.attached_image_urls.map((imgUrl, i) => (
                              <div key={i} className="rounded-xl overflow-hidden border border-slate-200 bg-slate-100">
                                <img
                                  src={imgUrl}
                                  alt={`Field sample ${i + 1}`}
                                  className="w-full h-24 object-cover hover:scale-105 transition-transform cursor-pointer"
                                  onClick={() => window.open(imgUrl, '_blank')}
                                />
                                <div className="px-2 py-1 text-[10px] text-slate-500 bg-slate-50 truncate">
                                  Leaf Sample #{i + 1}
                                </div>
                              </div>
                            ))}
                          </div>
                        ) : null}
                      </div>
                    </div>
                  );
                })
              )}
              <div ref={chatBottomRef} />
            </div>

            {/* Composer Bar */}
            <div className="border-t border-slate-200 p-3 sm:p-4 bg-slate-50 space-y-2.5">
              {/* Quick Advisory Presets */}
              <div className="flex items-center space-x-1.5 overflow-x-auto pb-1 text-xs no-scrollbar">
                <span className="text-[10px] font-bold text-slate-400 uppercase tracking-wider flex-shrink-0">
                  Quick Advice:
                </span>
                {quickAdviceTemplates.map((template, idx) => (
                  <button
                    key={idx}
                    type="button"
                    onClick={() => setReplyText((prev) => prev ? `${prev} ${template}` : template)}
                    className="text-[11px] bg-white hover:bg-forest-50 hover:text-forest-800 hover:border-forest-300 text-slate-600 border border-slate-200 px-2.5 py-1 rounded-full whitespace-nowrap transition-all shadow-2xs font-medium"
                  >
                    {template}
                  </button>
                ))}
              </div>

              {/* Optional Media Link Input Drawer */}
              {showMediaInput && (
                <div className="flex items-center space-x-2 bg-white p-2 rounded-xl border border-slate-300 shadow-2xs">
                  <ImageIcon className="w-4 h-4 text-forest-700 flex-shrink-0 ml-1" />
                  <input
                    type="text"
                    value={mediaUrlInput}
                    onChange={(e) => setMediaUrlInput(e.target.value)}
                    placeholder="Enter advisory diagram or photo URL (e.g. https://...)"
                    className="w-full text-xs text-slate-800 focus:outline-none"
                  />
                  {mediaUrlInput && (
                    <button
                      type="button"
                      onClick={() => setMediaUrlInput('')}
                      className="text-slate-400 hover:text-slate-600 text-xs px-2 font-bold"
                    >
                      Clear
                    </button>
                  )}
                </div>
              )}

              {/* Main Input Row */}
              <div className="flex items-end space-x-2">
                <button
                  type="button"
                  onClick={() => setShowMediaInput(!showMediaInput)}
                  title="Attach guidance media or photo URL"
                  className={`p-2.5 rounded-xl border transition-all flex-shrink-0 ${
                    showMediaInput || mediaUrlInput
                      ? 'bg-forest-100 border-forest-400 text-forest-800'
                      : 'bg-white border-slate-300 text-slate-500 hover:bg-slate-100'
                  }`}
                >
                  <ImageIcon className="w-4 h-4" />
                </button>

                <div className="flex-1">
                  <textarea
                    rows={2}
                    value={replyText}
                    onChange={(e) => setReplyText(e.target.value)}
                    onKeyDown={(e) => {
                      if (e.key === 'Enter' && (e.ctrlKey || e.metaKey)) {
                        e.preventDefault();
                        handleSendMessage();
                      }
                    }}
                    placeholder="Type official agricultural advice or response for the farmer... (Ctrl+Enter to send)"
                    className="w-full text-xs sm:text-sm p-3 border border-slate-300 rounded-xl focus:ring-2 focus:ring-forest-600 focus:border-forest-600 resize-none bg-white text-slate-800 shadow-inner"
                  />
                </div>

                <button
                  type="button"
                  disabled={isSending || !replyText.trim()}
                  onClick={handleSendMessage}
                  className={`px-4 py-3 rounded-xl font-bold text-xs sm:text-sm flex items-center space-x-2 shadow-sm transition-all flex-shrink-0 ${
                    replyText.trim() && !isSending
                      ? 'bg-forest-700 hover:bg-forest-800 text-white cursor-pointer active:scale-95'
                      : 'bg-slate-200 text-slate-400 cursor-not-allowed'
                  }`}
                >
                  {isSending ? (
                    <>
                      <RefreshCw className="w-4 h-4 animate-spin" />
                      <span className="hidden sm:inline">Sending...</span>
                    </>
                  ) : (
                    <>
                      <Send className="w-4 h-4" />
                      <span>Send Reply to Farmer</span>
                    </>
                  )}
                </button>
              </div>
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
                setCurrentStatus('resolved');
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
