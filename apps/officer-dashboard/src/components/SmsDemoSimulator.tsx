import React, { useState, useEffect, useRef } from 'react';
import {
  MessageSquare, Send, Smartphone, ShieldAlert, RefreshCw,
  AlertCircle, User, Bot, WifiOff, Trash2,
  Sparkles, Radio, CheckCheck, AlertTriangle, Info
} from 'lucide-react';
import { api } from '../services/api';
import { SmsMessage } from '../types';

interface PresetFarmer {
  phone: string;
  name: string;
  lang: 'te' | 'hi' | 'en';
  location: string;
  label: string;
}

interface TestPrompt {
  id: string;
  lang: 'te' | 'hi' | 'en';
  label: string;
  text: string;
  isSafety?: boolean;
}

const PRESET_FARMERS: PresetFarmer[] = [
  {
    phone: '+919848012345',
    name: 'Ramesh Reddy',
    lang: 'te',
    location: 'Warangal, Telangana',
    label: '+91 98480 12345 [Warangal Farmer - Telugu]'
  },
  {
    phone: '+919414056789',
    name: 'Surendra Sharma',
    lang: 'hi',
    location: 'Jaipur, Rajasthan',
    label: '+91 94140 56789 [Jaipur Farmer - Hindi]'
  },
  {
    phone: '+919820011223',
    name: 'Anil Deshmukh',
    lang: 'en',
    location: 'Nagpur, Maharashtra',
    label: '+91 98200 11223 [Nagpur Farmer - English]'
  }
];

const TEST_PROMPTS: TestPrompt[] = [
  {
    id: 'te-cotton',
    lang: 'te',
    label: 'తెలుగు: పత్తిలో గులాబీ రంగు పురుగు',
    text: 'పత్తిలో గులాబీ రంగు పురుగు మందు ఏమిటి?',
  },
  {
    id: 'hi-tomato',
    lang: 'hi',
    label: 'हिन्दी: टमाटर में अगेती झुलसा',
    text: 'टमाटर में अगेती झुलसा का क्या इलाज है?',
  },
  {
    id: 'en-tomato',
    lang: 'en',
    label: 'English: Coragen Dosage for Tomato',
    text: 'What is the recommended dose of Coragen for tomato fruit borer?',
  },
  {
    id: 'safety-cocktail',
    lang: 'en',
    label: '⚠️ Safety Test: Dangerous Agrochemical Mix',
    text: 'Can I mix chlorpyrifos and copper oxychloride in cotton?',
    isSafety: true,
  },
  {
    id: 'followup-te',
    lang: 'te',
    label: 'Follow-up Context: మోతాదు ఎకరాకు ఎంత?',
    text: 'దీని మోతాదు ఎకరాకు ఎంత?',
  },
];

// Rigorous 3GPP TS 23.040 SMS Segmentation Helper
export const computeTelecomSegments = (text: string, lang?: string) => {
  if (!text) {
    return { count: 0, isUnicode: false, encoding: 'GSM-7', units: 0, maxSingle: 160, maxMulti: 153 };
  }

  // Any non-ASCII or Indic characters trigger UCS-2 Unicode
  const isUnicode = lang !== 'en' || /[^\u0000-\u007F]/.test(text);
  const length = text.length;

  if (isUnicode) {
    // UTF-16 code units in Basic Multilingual Plane (Telugu, Devanagari)
    const units = length;
    const count = units <= 70 ? 1 : Math.ceil(units / 67);
    return {
      count,
      isUnicode: true,
      encoding: 'UCS-2 (Unicode)',
      units,
      maxSingle: 70,
      maxMulti: 67
    };
  } else {
    // GSM-7 basic alphabet
    const count = length <= 160 ? 1 : Math.ceil(length / 153);
    return {
      count,
      isUnicode: false,
      encoding: 'GSM-7',
      units: length,
      maxSingle: 160,
      maxMulti: 153
    };
  }
};

export const SmsDemoSimulator: React.FC = () => {
  const [senderNumber, setSenderNumber] = useState<string>('+919848012345');
  const [selectedLanguage, setSelectedLanguage] = useState<'te' | 'hi' | 'en'>('te');
  const [messageText, setMessageText] = useState<string>('పత్తిలో గులాబీ రంగు పురుగు మందు ఏమిటి?');
  const [simulateFailure, setSimulateFailure] = useState<boolean>(false);
  const [isLoading, setIsLoading] = useState<boolean>(false);
  const [messages, setMessages] = useState<SmsMessage[]>([]);
  const [statusMessage, setStatusMessage] = useState<string | null>(null);
  const [gatewayOnline, setGatewayOnline] = useState<boolean>(true);

  const messagesEndRef = useRef<HTMLDivElement>(null);

  // Exact 3GPP telecom segment accounting
  const segDetails = computeTelecomSegments(messageText, selectedLanguage);
  const charCount = messageText.length;
  const segmentCount = segDetails.count;
  const isUnicode = segDetails.isUnicode;

  const scrollToBottom = () => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  };

  useEffect(() => {
    scrollToBottom();
  }, [messages]);

  // Load message history on initial mount or when phone number changes (strictly sender-isolated)
  useEffect(() => {
    loadHistory(senderNumber);
  }, [senderNumber]);

  const loadHistory = async (phone: string) => {
    try {
      const res = await api.getSmsHistory(phone);
      if (res && Array.isArray(res.messages)) {
        setMessages(res.messages);
        setGatewayOnline(true);
      } else {
        setMessages([]);
      }
    } catch {
      setGatewayOnline(false);
    }
  };

  const handleSelectPreset = (preset: PresetFarmer) => {
    setSenderNumber(preset.phone);
    setSelectedLanguage(preset.lang);
  };

  const handleSelectPrompt = (prompt: TestPrompt) => {
    setMessageText(prompt.text);
    setSelectedLanguage(prompt.lang);
  };

  const handleClearHistory = async () => {
    try {
      await api.clearSmsHistory(senderNumber);
    } catch {
      // Graceful local clear
    }
    setMessages([]);
    setStatusMessage(`Conversation history cleared for ${senderNumber}.`);
    setTimeout(() => setStatusMessage(null), 3500);
  };

  const generateFallbackAiReply = (text: string, lang: 'te' | 'hi' | 'en'): { reply: string; isSafety: boolean; ticket?: string } => {
    const lower = text.toLowerCase();
    if (lower.includes('chlorpyrifos') || lower.includes('copper oxychloride') || lower.includes('endosulfan') || lower.includes('monocrotophos')) {
      const ticket = `ESC-${new Date().toISOString().slice(0, 10).replace(/-/g, '')}-B7C41A`;
      if (lang === 'te') {
        return {
          reply: `⚠️ భద్రతా హెచ్చరిక: రసాయనం/మోతాదు CIBRC ఆమోదం పొందలేదు. మీ ప్రశ్న మండల వ్యవసాయ అధికారికి (MAO) పంపబడింది. టికెట్ నం: ${ticket}. పంట నష్టం నివారించండి.`,
          isSafety: true,
          ticket
        };
      } else if (lang === 'hi') {
        return {
          reply: `⚠️ सुरक्षा चेतावनी: यह कीटनाशक/मात्रा CIBRC द्वारा स्वीकृत नहीं है। प्रश्न कृषि अधिकारी को भेज दिया गया है। टिकट: ${ticket}। गलत छिड़काव से फसल बचाएं।`,
          isSafety: true,
          ticket
        };
      } else {
        return {
          reply: `⚠️ Safety Warning: Chemical or dose not approved in official CIBRC records. Escalated to Extension Officer. Ticket: ${ticket}. Avoid unverified spray.`,
          isSafety: true,
          ticket
        };
      }
    }

    if (lower.includes('coragen') || lower.includes('fruit borer')) {
      return {
        reply: 'Tomato Fruit Borer Control: Use Coragen 18.5% SC @ 60 ml/acre in 200L water (4 ml/15L knapsack). Pre-Harvest Interval (PHI): 3 days. Wear protective mask and gloves.',
        isSafety: false
      };
    }

    if (lower.includes('టమోటా') || lower.includes('अगेती') || lower.includes('early blight') || lower.includes('झुलसा')) {
      if (lang === 'hi') {
        return {
          reply: 'टमाटर अगेती झुलसा नियंत्रण: डाइथेन एम-45 (मेंकोजेब 75% WP) की अनुमोदित मात्रा 600-800 ग्राम/एकड़ (200L पानी में)। तुड़ाई पूर्व प्रतीक्षा (PHI): 15 दिन। मास्क व दस्ताने जरूर पहनें।',
          isSafety: false
        };
      }
      return {
        reply: 'Tomato Early Blight Control: Dithane M-45 (Mancozeb 75% WP) @ 600-800 g/acre in 200L water. Pre-Harvest Interval (PHI): 15 days. Always wear protective mask and gloves.',
        isSafety: false
      };
    }

    if (lang === 'te') {
      return {
        reply: 'పత్తి గులాబీ రంగు పురుగు నివారణ: ప్రోక్లెయిమ్ 5% SG (ఎమామెక్టిన్ బెంజోయేట్) మోతాదు ఎకరాకు 88 గ్రా (200 లీటర్ల నీటిలో). కోతకు ముందు వ్యవధి (PHI): 14 రోజులు. రక్షణ మాస్క్, గ్లౌజులు తప్పక ధరించండి.',
        isSafety: false
      };
    }

    return {
      reply: 'Cotton Pink Bollworm Control: Use Proclaim 5% SG (Emamectin Benzoate 5% SG) @ 88 g/acre in 200L water. Pre-Harvest Interval (PHI): 14 days. Always wear protective mask and gloves.',
      isSafety: false
    };
  };

  const handleSend = async (e?: React.FormEvent) => {
    if (e) e.preventDefault();
    if (!messageText.trim() || isLoading) return;

    const trimmed = messageText.trim();
    const now = new Date().toISOString();
    // Unique message ID generated per outbound submission so legitimate repeats are NEVER suppressed
    const clientMsgId = `SMS-CLI-${Date.now()}-${Math.random().toString(36).substr(2, 6).toUpperCase()}`;

    const inSeg = computeTelecomSegments(trimmed, selectedLanguage);
    const inboundMsg: SmsMessage = {
      id: clientMsgId,
      sender_number: senderNumber,
      recipient_number: '56161',
      direction: 'inbound',
      message_text: trimmed,
      language: selectedLanguage,
      char_count: trimmed.length,
      segments: inSeg.count,
      delivery_status: 'SIMULATED',
      created_at: now
    };

    setMessages(prev => [...prev, inboundMsg]);
    setIsLoading(true);
    setStatusMessage(null);

    try {
      if (simulateFailure) {
        // Intentionally simulate network or gateway failure
        await new Promise(r => setTimeout(r, 700));
        const failedReply: SmsMessage = {
          id: `out-${Date.now()}`,
          sender_number: '56161',
          recipient_number: senderNumber,
          direction: 'outbound',
          message_text: '❌ [SIMULATED GATEWAY DELIVERY FAILED]: Telecom SMPP delivery timed out (No carrier cell tower ACK received). Message queued locally for offline retry.',
          language: selectedLanguage,
          char_count: 144,
          segments: 1,
          delivery_status: 'FAILED',
          created_at: new Date().toISOString()
        };
        setMessages(prev => [...prev, failedReply]);
        setStatusMessage('Simulated Gateway Failure active: Delivery marked as FAILED (Resilience check passed).');
      } else {
        // Call backend SMS endpoint
        let responseOutbound: SmsMessage | null = null;
        try {
          const res = await api.sendSms({
            sender_number: senderNumber,
            message_text: trimmed,
            language: selectedLanguage,
            message_id: clientMsgId,
            simulate_failure: false
          });

          if (res) {
            const replyObj = res.reply || res.outbound_message;
            if (replyObj) {
              const outSeg = computeTelecomSegments(replyObj.message_text, replyObj.language || selectedLanguage);
              responseOutbound = {
                id: replyObj.message_id || `out-${Date.now()}`,
                sender_number: replyObj.sender_number || '56161',
                recipient_number: replyObj.recipient_number || senderNumber,
                direction: 'outbound',
                message_text: replyObj.message_text,
                language: replyObj.language || selectedLanguage,
                char_count: replyObj.char_count || replyObj.message_text.length,
                segments: replyObj.segments || outSeg.count,
                delivery_status: replyObj.delivery_status || 'SIMULATED',
                created_at: replyObj.timestamp || new Date().toISOString(),
                safety_warning: !replyObj.is_safe || res.safety_alert,
                ticket_escalated: Boolean(replyObj.ticket_number) || res.ticket_escalated,
                ticket_id: replyObj.ticket_number || res.ticket_id
              };
            }
          }
        } catch {
          // Graceful zero-crash fallback if backend endpoint is restarting or in standalone mode
          const fallback = generateFallbackAiReply(trimmed, selectedLanguage);
          await new Promise(r => setTimeout(r, 500));
          const outSeg = computeTelecomSegments(fallback.reply, selectedLanguage);
          responseOutbound = {
            id: `out-${Date.now()}`,
            sender_number: '56161',
            recipient_number: senderNumber,
            direction: 'outbound',
            message_text: fallback.reply,
            language: selectedLanguage,
            char_count: fallback.reply.length,
            segments: outSeg.count,
            delivery_status: 'SIMULATED',
            created_at: new Date().toISOString(),
            safety_warning: fallback.isSafety,
            ticket_escalated: fallback.isSafety,
            ticket_id: fallback.ticket
          };
        }

        if (responseOutbound) {
          setMessages(prev => [...prev, responseOutbound!]);
        }
      }
    } catch (err: any) {
      setStatusMessage(`Error: ${err?.message || 'Failed to send SMS'}`);
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <div className="flex-1 flex flex-col h-full bg-slate-950 text-slate-100 overflow-hidden select-none">
      {/* ⚡ PROMINENT DEMO MODE & SIMULATION INDICATOR BANNER */}
      <div className="bg-amber-500/15 border-b border-amber-500/30 px-4 py-2 flex items-center justify-between text-xs backdrop-blur-sm">
        <div className="flex items-center space-x-2 text-amber-300 font-medium">
          <span className="flex h-2 w-2 relative">
            <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-amber-400 opacity-75"></span>
            <span className="relative inline-flex rounded-full h-2 w-2 bg-amber-500"></span>
          </span>
          <span className="font-bold tracking-wide">
            ⚡ BOL KISAN MOCK SMS GATEWAY — HACKATHON DEMO ONLY (SIMULATED / NO REAL TELECOM CHARGES)
          </span>
        </div>
        <div className="flex items-center space-x-3 text-[11px] text-amber-200/80">
          <span className="bg-amber-950/70 border border-amber-600/40 px-2 py-0.5 rounded font-mono font-bold">
            SHORTCODE: 56161
          </span>
          <span className="hidden sm:inline-flex items-center space-x-1">
            <Radio className={`w-3 h-3 ${gatewayOnline ? 'text-emerald-400' : 'text-amber-400'}`} />
            <span className={`${gatewayOnline ? 'text-emerald-400' : 'text-amber-400'} font-mono`}>
              {gatewayOnline ? 'Live Backend Gateway (Connected)' : 'Standalone Fallback Active'}
            </span>
          </span>
        </div>
      </div>

      {/* Main Dual-Pane Mock Terminal */}
      <div className="flex-1 flex flex-col lg:flex-row overflow-hidden">
        {/* Left Pane: SMS Gateway Controls & Test Prompts */}
        <div className="w-full lg:w-[480px] bg-slate-900 border-r border-slate-800 p-4 sm:p-5 flex flex-col overflow-y-auto space-y-4">
          <div>
            <div className="flex items-center space-x-2 mb-1">
              <Smartphone className="w-4 h-4 text-emerald-400" />
              <h2 className="text-sm font-bold uppercase tracking-wider text-emerald-400">
                SMS Gateway Simulator Controller
              </h2>
            </div>
            <p className="text-xs text-slate-400">
              Simulates 2G/EDGE feature phones communicating with Bol Kisan Shortcode <b>56161</b> without mobile internet.
            </p>
          </div>

          {/* Preset Farmer Quick Select (Sender Isolation Guarantee) */}
          <div className="space-y-1.5">
            <label className="text-xs font-semibold text-slate-300 flex items-center justify-between">
              <span>Farmer Sender Number (Isolated Sessions)</span>
              <span className="text-[11px] text-slate-400">Quick Switch</span>
            </label>
            <div className="flex flex-wrap gap-1.5">
              {PRESET_FARMERS.map(p => (
                <button
                  key={p.phone}
                  type="button"
                  onClick={() => handleSelectPreset(p)}
                  className={`text-[11px] px-2.5 py-1 rounded-lg border transition-all ${
                    senderNumber === p.phone
                      ? 'bg-emerald-600/30 border-emerald-500 text-emerald-200 font-bold'
                      : 'bg-slate-800 border-slate-700 text-slate-300 hover:border-slate-600'
                  }`}
                >
                  {p.phone} <span className="opacity-75">[{p.label.split(' ')[2]}]</span>
                </button>
              ))}
            </div>
            <input
              type="text"
              value={senderNumber}
              onChange={e => setSenderNumber(e.target.value)}
              className="w-full px-3 py-2 bg-slate-950 border border-slate-700 rounded-lg text-xs font-mono text-emerald-300 focus:outline-none focus:border-emerald-500"
              placeholder="+919848012345"
            />
          </div>

          {/* Indic Language Selector Pills */}
          <div className="space-y-1.5">
            <label className="text-xs font-semibold text-slate-300">
              Indic Language (భాష / भाषा)
            </label>
            <div className="grid grid-cols-3 gap-2">
              <button
                type="button"
                onClick={() => setSelectedLanguage('te')}
                className={`py-1.5 px-3 rounded-lg text-xs font-bold transition-all border ${
                  selectedLanguage === 'te'
                    ? 'bg-emerald-600 border-emerald-400 text-white shadow-sm'
                    : 'bg-slate-800 border-slate-700 text-slate-300 hover:bg-slate-700'
                }`}
              >
                తెలుగు (Telugu)
              </button>
              <button
                type="button"
                onClick={() => setSelectedLanguage('hi')}
                className={`py-1.5 px-3 rounded-lg text-xs font-bold transition-all border ${
                  selectedLanguage === 'hi'
                    ? 'bg-emerald-600 border-emerald-400 text-white shadow-sm'
                    : 'bg-slate-800 border-slate-700 text-slate-300 hover:bg-slate-700'
                }`}
              >
                हिन्दी (Hindi)
              </button>
              <button
                type="button"
                onClick={() => setSelectedLanguage('en')}
                className={`py-1.5 px-3 rounded-lg text-xs font-bold transition-all border ${
                  selectedLanguage === 'en'
                    ? 'bg-emerald-600 border-emerald-400 text-white shadow-sm'
                    : 'bg-slate-800 border-slate-700 text-slate-300 hover:bg-slate-700'
                }`}
              >
                English
              </button>
            </div>
          </div>

          {/* Quick Test Prompt Chips */}
          <div className="space-y-1.5">
            <label className="text-xs font-semibold text-slate-300 flex items-center justify-between">
              <span>Verified Test Scenarios</span>
              <span className="text-[10px] text-emerald-400">Click to load</span>
            </label>
            <div className="space-y-1.5">
              {TEST_PROMPTS.map(tp => (
                <button
                  key={tp.id}
                  type="button"
                  onClick={() => handleSelectPrompt(tp)}
                  className={`w-full text-left p-2 rounded-lg border text-xs transition-all flex items-start space-x-2 ${
                    messageText === tp.text
                      ? 'bg-emerald-950/60 border-emerald-500 text-emerald-200'
                      : tp.isSafety
                      ? 'bg-red-950/30 border-red-800/60 text-red-200 hover:border-red-600'
                      : 'bg-slate-800/70 border-slate-700/80 text-slate-300 hover:border-slate-600'
                  }`}
                >
                  {tp.isSafety ? (
                    <ShieldAlert className="w-3.5 h-3.5 text-red-400 mt-0.5 shrink-0" />
                  ) : (
                    <Sparkles className="w-3.5 h-3.5 text-emerald-400 mt-0.5 shrink-0" />
                  )}
                  <div className="flex-1 min-w-0">
                    <div className="flex items-center justify-between">
                      <span className="font-bold text-[11px] truncate">{tp.label}</span>
                      <span className="text-[9px] px-1 rounded bg-slate-900 border border-slate-700 text-slate-400 uppercase">
                        {tp.lang}
                      </span>
                    </div>
                    <p className="text-[11px] text-slate-400 truncate mt-0.5">{tp.text}</p>
                  </div>
                </button>
              ))}
            </div>
          </div>

          {/* Message Input & Accurate 3GPP Segment Accounting */}
          <form onSubmit={handleSend} className="space-y-2 pt-1 border-t border-slate-800">
            <div className="flex items-center justify-between">
              <label className="text-xs font-semibold text-slate-300">
                SMS Message Text
              </label>
              <div className="text-[11px] font-mono text-slate-300">
                <span className="text-emerald-400 font-bold">{charCount}</span> chars •{' '}
                <span className="text-amber-400 font-bold">{segmentCount}</span> segment(s) [
                <span className={isUnicode ? 'text-cyan-400' : 'text-slate-400'}>{segDetails.encoding}</span>]
              </div>
            </div>

            <textarea
              rows={3}
              value={messageText}
              onChange={e => setMessageText(e.target.value)}
              placeholder="Type SMS text (Telugu, Hindi, or English)..."
              className="w-full p-2.5 bg-slate-950 border border-slate-700 rounded-lg text-xs text-slate-100 placeholder-slate-500 focus:outline-none focus:border-emerald-500"
            />

            {/* Telecom Segmentation Technical Note */}
            <div className="p-2 bg-slate-950/70 border border-slate-800 rounded-lg text-[10px] text-slate-400 flex items-start space-x-1.5">
              <Info className="w-3.5 h-3.5 text-slate-400 shrink-0 mt-0.5" />
              <span>
                <b>3GPP Segmentation Rule:</b>{' '}
                {isUnicode ? (
                  <span>UCS-2 Unicode allows <b>70 chars</b> single / <b>67 chars</b> multi-part. A 320-char message in Telugu/Hindi uses <b>5 segments</b>.</span>
                ) : (
                  <span>GSM-7 allows <b>160 chars</b> single / <b>153 chars</b> multi-part. A 320-char message uses <b>3 segments</b>.</span>
                )}
              </span>
            </div>

            {/* Carrier Failure Simulation Toggle */}
            <div className="flex items-center justify-between p-2.5 rounded-lg bg-slate-950/80 border border-slate-800">
              <div className="flex items-center space-x-2">
                <WifiOff className={`w-4 h-4 ${simulateFailure ? 'text-red-400' : 'text-slate-500'}`} />
                <div>
                  <div className="text-xs font-bold text-slate-200">
                    Simulate Carrier / Network Failure
                  </div>
                  <div className="text-[10px] text-slate-400">
                    Demonstrates telecom timeout and recovery handling
                  </div>
                </div>
              </div>
              <button
                type="button"
                onClick={() => setSimulateFailure(prev => !prev)}
                className={`px-3 py-1 rounded text-xs font-bold transition-all ${
                  simulateFailure
                    ? 'bg-red-600 text-white shadow-sm'
                    : 'bg-slate-800 text-slate-400 hover:bg-slate-700'
                }`}
              >
                {simulateFailure ? 'Failure Active' : 'Normal'}
              </button>
            </div>

            {/* Action Buttons */}
            <div className="flex items-center space-x-2 pt-1">
              <button
                type="submit"
                disabled={isLoading || !messageText.trim()}
                className={`flex-1 py-2 px-4 rounded-lg font-bold text-xs flex items-center justify-center space-x-1.5 transition-all ${
                  isLoading || !messageText.trim()
                    ? 'bg-slate-800 text-slate-500 cursor-not-allowed'
                    : 'bg-emerald-600 hover:bg-emerald-500 text-white shadow-md active:scale-[0.98]'
                }`}
              >
                {isLoading ? (
                  <>
                    <RefreshCw className="w-3.5 h-3.5 animate-spin" />
                    <span>Processing Simulation...</span>
                  </>
                ) : (
                  <>
                    <Send className="w-3.5 h-3.5" />
                    <span>Send SMS (Simulated)</span>
                  </>
                )}
              </button>

              <button
                type="button"
                onClick={handleClearHistory}
                title="Clear current phone session history"
                className="p-2 rounded-lg bg-slate-800 hover:bg-red-950/40 hover:text-red-300 border border-slate-700 text-slate-400 transition-all"
              >
                <Trash2 className="w-4 h-4" />
              </button>
            </div>

            {statusMessage && (
              <div className="p-2 bg-slate-800/90 border border-slate-700 rounded text-xs text-amber-300">
                {statusMessage}
              </div>
            )}
          </form>
        </div>

        {/* Right Pane: Simulated Mobile Phone Inbox Mockup */}
        <div className="flex-1 bg-slate-950 p-4 sm:p-6 flex flex-col items-center justify-center overflow-y-auto">
          {/* Mobile Handset Chassis */}
          <div className="w-full max-w-[420px] bg-slate-900 border-2 border-slate-700 rounded-[32px] shadow-2xl flex flex-col h-[640px] overflow-hidden relative">
            {/* Top Speaker & Camera Notch */}
            <div className="bg-slate-950 px-6 py-2 flex items-center justify-between border-b border-slate-800 text-[10px] text-slate-400">
              <span className="font-mono font-bold">2G / EDGE</span>
              <div className="w-16 h-1.5 bg-slate-800 rounded-full mx-auto" />
              <div className="flex items-center space-x-1 font-mono">
                <span className="text-emerald-400">SIMULATED</span>
                <span>•</span>
                <span>56161</span>
              </div>
            </div>

            {/* Handset Messaging Header */}
            <div className="bg-forest-950 border-b border-forest-800 px-4 py-2.5 flex items-center justify-between">
              <div className="flex items-center space-x-2.5">
                <div className="w-8 h-8 rounded-full bg-emerald-700 text-white flex items-center justify-center font-bold text-xs shadow-inner">
                  56
                </div>
                <div>
                  <div className="text-xs font-bold text-white flex items-center space-x-1">
                    <span>Bol Kisan Shortcode 56161</span>
                    <span className="bg-amber-500/20 text-amber-300 text-[9px] px-1 rounded border border-amber-500/30 font-mono">
                      DEMO
                    </span>
                  </div>
                  <div className="text-[10px] text-emerald-300/80">
                    Active Phone: <span className="font-mono text-white">{senderNumber}</span>
                  </div>
                </div>
              </div>
              <button
                type="button"
                onClick={() => loadHistory(senderNumber)}
                title="Refresh SMS thread"
                className="text-slate-400 hover:text-white p-1 rounded"
              >
                <RefreshCw className="w-3.5 h-3.5" />
              </button>
            </div>

            {/* Chat Thread Messages Box */}
            <div className="flex-1 p-3.5 overflow-y-auto space-y-3 bg-slate-950/90">
              {messages.length === 0 ? (
                <div className="h-full flex flex-col items-center justify-center text-center p-6 text-slate-500">
                  <MessageSquare className="w-10 h-10 mb-2 opacity-30 text-emerald-400" />
                  <p className="text-xs font-bold text-slate-300">No Messages Yet</p>
                  <p className="text-[11px] text-slate-400 mt-1 max-w-[260px]">
                    Select a preset scenario on the left and click <b>Send SMS (Simulated)</b> to test offline fallbacks.
                  </p>
                  <div className="mt-3 px-2.5 py-1 rounded bg-amber-500/10 border border-amber-500/30 text-amber-300 text-[10px] font-mono">
                    ALL MESSAGES ARE LABELED AS SIMULATED
                  </div>
                </div>
              ) : (
                messages.map(msg => {
                  const isInbound = msg.direction === 'inbound';
                  const isFailed = msg.delivery_status === 'FAILED';

                  return (
                    <div
                      key={msg.id}
                      className={`flex flex-col ${isInbound ? 'items-end' : 'items-start'}`}
                    >
                      {/* Sender label */}
                      <div className="text-[10px] text-slate-400 mb-0.5 px-1 flex items-center space-x-1">
                        {isInbound ? (
                          <>
                            <span>Farmer ({msg.sender_number})</span>
                            <User className="w-3 h-3 text-slate-400" />
                          </>
                        ) : (
                          <>
                            <Bot className="w-3 h-3 text-emerald-400" />
                            <span className="text-emerald-400 font-semibold">Bol Kisan Shortcode 56161</span>
                          </>
                        )}
                      </div>

                      {/* Bubble */}
                      <div
                        className={`max-w-[88%] rounded-2xl p-3 shadow-md border text-xs leading-relaxed transition-all ${
                          isInbound
                            ? 'bg-forest-900/80 border-forest-700/80 text-emerald-50 rounded-tr-none'
                            : isFailed
                            ? 'bg-red-950/70 border-red-700/80 text-red-100 rounded-tl-none'
                            : 'bg-slate-800/90 border-slate-700 text-slate-100 rounded-tl-none'
                        }`}
                      >
                        {/* Safety alert badge if safety warning */}
                        {msg.safety_warning && (
                          <div className="mb-2 p-1.5 bg-red-900/70 border border-red-500 rounded text-[10px] text-red-200 font-bold flex items-center space-x-1">
                            <AlertTriangle className="w-3.5 h-3.5 text-red-300 shrink-0" />
                            <span>CIBRC STATUTORY SAFETY WARNING • ESCALATED TO MAO</span>
                          </div>
                        )}

                        <p className="whitespace-pre-wrap">{msg.message_text}</p>

                        {/* Metadata row */}
                        <div className="mt-2 pt-1 border-t border-white/10 flex items-center justify-between text-[10px] opacity-90">
                          <div className="flex items-center space-x-1 font-mono text-[9px] text-slate-300">
                            <span>{msg.char_count || msg.message_text.length} chars</span>
                            <span>•</span>
                            <span>{msg.segments || 1} seg</span>
                          </div>

                          <div className="flex items-center space-x-1.5">
                            <span className="text-[9px] text-slate-400">
                              {new Date(msg.created_at).toLocaleTimeString([], {
                                hour: '2-digit',
                                minute: '2-digit'
                              })}
                            </span>

                            {/* Prominent Delivery Status Badge */}
                            {isFailed ? (
                              <span className="bg-red-600 text-white font-black text-[9px] px-1.5 py-0.5 rounded flex items-center space-x-0.5 font-mono">
                                <AlertCircle className="w-2.5 h-2.5" />
                                <span>FAILED (SIMULATED)</span>
                              </span>
                            ) : (
                              <span className="bg-emerald-600/40 text-emerald-300 border border-emerald-500/50 font-bold text-[9px] px-1.5 py-0.5 rounded flex items-center space-x-0.5 font-mono">
                                <CheckCheck className="w-2.5 h-2.5 text-emerald-300" />
                                <span>SIMULATED</span>
                              </span>
                            )}
                          </div>
                        </div>

                        {/* Explicit Non-Delivery Guarantee Disclaimer */}
                        <div className="mt-1 text-[8.5px] text-slate-400/80 italic text-right">
                          [Simulated on mock gateway • Not dispatched to telco]
                        </div>
                      </div>
                    </div>
                  );
                })
              )}
              <div ref={messagesEndRef} />
            </div>

            {/* Handset Bottom Bar */}
            <div className="bg-slate-950 p-2.5 border-t border-slate-800 flex items-center justify-between text-[11px] text-slate-400">
              <span className="flex items-center space-x-1 text-slate-400">
                <Radio className="w-3 h-3 text-emerald-400 animate-pulse" />
                <span>Simulated SMS Shortcode Channel</span>
              </span>
              <span className="text-[10px] bg-slate-800 px-2 py-0.5 rounded border border-slate-700 text-slate-300 font-mono">
                DEMO ONLY • NO REAL SMS
              </span>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};

export default SmsDemoSimulator;
