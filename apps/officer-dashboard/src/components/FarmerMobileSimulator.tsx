import React, { useState, useEffect } from 'react';
import { 
  Mic, MicOff, Volume2, Camera, AlertTriangle, ShieldCheck, 
  Sparkles, RefreshCw, 
  MapPin, CloudRain, TrendingUp, ChevronRight, Send, Leaf
} from 'lucide-react';

interface FarmerMobileSimulatorProps {
  onTicketEscalated?: () => void;
}

interface Message {
  id: string;
  sender: 'farmer' | 'ai';
  text: string;
  teluguText?: string;
  hindiText?: string;
  sourceCitation?: string;
  dilutionKnapsack?: string;
  phiDays?: number;
  toxicityBand?: string;
  isSafetyWarning?: boolean;
  isEscalated?: boolean;
  ticketId?: string;
}

export const FarmerMobileSimulator: React.FC<FarmerMobileSimulatorProps> = ({ onTicketEscalated }) => {
  const [language, setLanguage] = useState<'te' | 'hi' | 'en'>('te');
  const [activeTab, setActiveTab] = useState<'voice' | 'camera' | 'tickets' | 'mandi'>('voice');
  const [isRecording, setIsRecording] = useState(false);
  const [queryInput, setQueryInput] = useState('');
  const [isLoading, setIsLoading] = useState(false);
  const [messages, setMessages] = useState<Message[]>([
    {
      id: 'm-welcome',
      sender: 'ai',
      text: 'నమస్కారం రైతు సోదరా! బోల్ కిసాన్ వాయిస్ అసిస్టెంట్‌కి స్వాగతం. మీ పంట సమస్యను మాట్లాడి అడగండి.',
      teluguText: 'నమస్కారం రైతు సోదరా! బోల్ కిసాన్ వాయిస్ అసిస్టెంట్‌కి స్వాగతం. మీ పంట సమస్యను మాట్లాడి అడగండి.',
      hindiText: 'नमस्ते किसान भाई! बोल किसान वॉयस असिस्टेंट में आपका स्वागत है। अपनी फसल की समस्या बोलकर बताएं।',
      sourceCitation: 'ICAR & CIBRC Verified Portal'
    }
  ]);
  const [weatherData, setWeatherData] = useState<any>(null);
  const [mandiData, setMandiData] = useState<any[]>([]);
  const [farmerTickets, setFarmerTickets] = useState<any[]>([]);

  // Sample quick queries for fast live testing
  const sampleQueries = [
    {
      label: 'వరి అగ్గితెగులు (Rice Blast)',
      query: 'వరిలో అగ్గితెగులు వచ్చింది, ఏ మందు కొట్టాలి?',
      crop: 'Rice',
      lang: 'te'
    },
    {
      label: 'పత్తి గులాబీ పురుగు (Pink Bollworm)',
      query: 'పత్తిలో గులాబీ రంగు కాయ తొలుచు పురుగు నివారణ మందు ఏమిటి?',
      crop: 'Cotton',
      lang: 'te'
    },
    {
      label: '⚠️ సేఫ్టీ టెస్ట్: మోనోక్రోటోఫాస్ (Banned Test)',
      query: 'టమోటాలో మోనోక్రోటోఫాస్ స్ప్రే చేయవచ్చా?',
      crop: 'Tomato',
      lang: 'te'
    },
    {
      label: 'धान में भूरा फुदका (BPH)',
      query: 'धान में भूरा फुदका (BPH) लगा है, क्या उपाय है?',
      crop: 'Rice',
      lang: 'hi'
    },
    {
      label: 'Mandi Price (Cotton)',
      query: 'What is today\'s cotton price in Warangal APMC mandi?',
      crop: 'Cotton',
      lang: 'en'
    }
  ];

  // Fetch live weather & mandi data on mount
  useEffect(() => {
    fetch('http://127.0.0.1:8000/api/v1/weather/district/Warangal')
      .then(r => r.json())
      .then(d => setWeatherData(d))
      .catch(() => {});

    fetch('http://127.0.0.1:8000/api/v1/mandi/prices?commodity=Cotton')
      .then(r => r.json())
      .then(d => {
        if (d.records) setMandiData(d.records);
      })
      .catch(() => {});
  }, []);

  const speakText = (text: string) => {
    if ('speechSynthesis' in window) {
      window.speechSynthesis.cancel();
      const utterance = new SpeechSynthesisUtterance(text);
      if (language === 'hi') utterance.lang = 'hi-IN';
      else if (language === 'te') utterance.lang = 'te-IN';
      else utterance.lang = 'en-IN';
      window.speechSynthesis.speak(utterance);
    }
  };

  const handleSendQuery = async (queryText: string) => {
    if (!queryText.trim()) return;
    const userMsg: Message = {
      id: `u-${Date.now()}`,
      sender: 'farmer',
      text: queryText
    };
    setMessages(prev => [...prev, userMsg]);
    setQueryInput('');
    setIsLoading(true);

    try {
      const res = await fetch('http://127.0.0.1:8000/api/v1/voice/query', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          farmer_id: 'f1000000-0000-0000-0000-000000000001',
          language: language,
          transcript: queryText,
          crop_context: 'Cotton'
        })
      });

      if (!res.ok) throw new Error('Query failed');
      const data = await res.json();

      const aiMsg: Message = {
        id: `ai-${Date.now()}`,
        sender: 'ai',
        text: data.response_text,
        sourceCitation: data.source_citation,
        dilutionKnapsack: data.dilution_per_15l_pump,
        phiDays: data.pre_harvest_interval_days,
        toxicityBand: data.toxicity_band,
        isSafetyWarning: data.is_safety_warning,
        isEscalated: data.is_escalated,
        ticketId: data.ticket_id
      };

      setMessages(prev => [...prev, aiMsg]);
      speakText(data.response_text);

      if (data.is_escalated) {
        if (onTicketEscalated) onTicketEscalated();
        setFarmerTickets(prev => [
          {
            id: data.ticket_id || `ESC-${Date.now()}`,
            crop: 'Cotton',
            issue: queryText,
            status: 'PENDING_OFFICER_REVIEW',
            time: 'Just now'
          },
          ...prev
        ]);
      }
    } catch {
      // Fallback response for offline or simulated demo
      const isBanned = queryText.toLowerCase().includes('monocrotophos') || 
                       queryText.includes('మోనోక్రోటోఫాస్') || 
                       queryText.includes('endosulfan');

      let reply = '';
      if (isBanned) {
        reply = 'హెచ్చరిక: మోనోక్రోటోఫాస్ లేదా ఎండోసల్ఫాన్ వాడకం చట్టప్రకారం పూర్తిగా నిషేధించబడింది (CIBRC Order). ఇది కూరగాయలపై వాడితే తీవ్ర విషపూరితం అవుతుంది. ఈ సమస్యను తక్షణమే మండల వ్యవసాయ అధికారి (MAO) గారికి బదిలీ చేసాము.';
      } else {
        reply = 'పత్తిలో గులాబీ రంగు పురుగు నివారణకు: ఎమామెక్టిన్ బెంజోయేట్ 5% SG మందును 15 లీటర్ల పంపునకు 6.6 గ్రాములు కలిపి పిచికారీ చేయాలి. కోతకు 14 రోజుల ముందు పిచికారీ ఆపాలి (PHI: 14 రోజులు).';
      }

      const aiMsg: Message = {
        id: `ai-${Date.now()}`,
        sender: 'ai',
        text: reply,
        sourceCitation: isBanned ? 'CIBRC Statutory Banned Pesticide Gazette' : 'CIBRC CIR-11425/2014 & ICAR-CICR',
        dilutionKnapsack: isBanned ? undefined : '6.6 g per 15L backpack pump',
        phiDays: isBanned ? undefined : 14,
        isSafetyWarning: isBanned,
        isEscalated: isBanned
      };
      setMessages(prev => [...prev, aiMsg]);
      speakText(reply);
      if (isBanned && onTicketEscalated) onTicketEscalated();
    } finally {
      setIsLoading(false);
    }
  };

  const handleSimulatePTT = () => {
    if (isRecording) {
      setIsRecording(false);
      handleSendQuery('పత్తిలో గులాబీ రంగు కాయ తొలుచు పురుగు నివారణ మందు ఏమిటి?');
    } else {
      setIsRecording(true);
      setTimeout(() => {
        setIsRecording(false);
        handleSendQuery('పత్తిలో గులాబీ రంగు కాయ తొలుచు పురుగు నివారణ మందు ఏమిటి?');
      }, 2500);
    }
  };

  return (
    <div className="w-[390px] h-[780px] bg-slate-900 rounded-[48px] p-3 shadow-2xl border-4 border-slate-700 flex flex-col relative select-none font-sans mx-auto">
      {/* Smartphone Notch / Speaker */}
      <div className="w-full flex justify-center items-center py-1 relative z-20">
        <div className="w-24 h-4 bg-slate-800 rounded-full flex items-center justify-center space-x-2">
          <div className="w-2 h-2 rounded-full bg-slate-700" />
          <div className="w-8 h-1 rounded-full bg-slate-700" />
        </div>
      </div>

      {/* Screen Frame */}
      <div className="flex-1 bg-emerald-50/70 rounded-[38px] flex flex-col overflow-hidden relative border border-emerald-100">
        
        {/* Android Status Bar */}
        <div className="h-6 px-5 flex items-center justify-between text-[11px] font-semibold text-emerald-950 bg-emerald-100/60">
          <span>12:00</span>
          <div className="flex items-center space-x-1.5">
            <span className="text-[10px] bg-emerald-700 text-white px-1 rounded font-bold">5G</span>
            <div className="w-4 h-2 border border-emerald-900 rounded-sm p-0.5 flex items-center">
              <div className="w-full h-full bg-emerald-800 rounded-2xs" />
            </div>
          </div>
        </div>

        {/* Farmer Header & Language Selector */}
        <div className="bg-forest-800 text-white p-3.5 shadow-md">
          <div className="flex items-center justify-between">
            <div className="flex items-center space-x-2">
              <div className="w-8 h-8 rounded-xl bg-forest-700 flex items-center justify-center text-white border border-forest-600">
                <Leaf className="w-5 h-5" />
              </div>
              <div>
                <h2 className="text-base font-bold font-serif leading-none tracking-tight">బోల్ కిసాన్</h2>
                <p className="text-[10px] text-emerald-200 mt-0.5">Bol Kisan • Voice Assistant</p>
              </div>
            </div>

            {/* Language Switcher */}
            <div className="flex bg-forest-900/80 p-0.5 rounded-lg border border-forest-700 text-[11px] font-medium">
              <button 
                onClick={() => setLanguage('te')} 
                className={`px-2 py-0.5 rounded ${language === 'te' ? 'bg-emerald-500 text-white font-bold' : 'text-emerald-200'}`}
              >
                తెలుగు
              </button>
              <button 
                onClick={() => setLanguage('hi')} 
                className={`px-2 py-0.5 rounded ${language === 'hi' ? 'bg-emerald-500 text-white font-bold' : 'text-emerald-200'}`}
              >
                हिन्दी
              </button>
              <button 
                onClick={() => setLanguage('en')} 
                className={`px-2 py-0.5 rounded ${language === 'en' ? 'bg-emerald-500 text-white font-bold' : 'text-emerald-200'}`}
              >
                EN
              </button>
            </div>
          </div>

          {/* Quick Weather / Mandi Ticker */}
          <div className="mt-2.5 flex items-center justify-between bg-forest-900/60 px-2.5 py-1.5 rounded-xl border border-forest-700 text-[10px] text-emerald-100">
            <div className="flex items-center space-x-1.5">
              <MapPin className="w-3 h-3 text-harvest-400" />
              <span>వరంగల్ (Warangal)</span>
              <span className="font-bold text-white">31.5°C</span>
            </div>
            <div className="flex items-center space-x-1.5">
              <TrendingUp className="w-3 h-3 text-emerald-400" />
              <span>పత్తి ధర: <b className="text-emerald-300">₹7,420</b>/క్విం</span>
            </div>
          </div>
        </div>

        {/* Content Body Based on Tab */}
        <div className="flex-1 overflow-y-auto p-3 space-y-3">
          
          {activeTab === 'voice' && (
            <>
              {/* Messages Scroll Area */}
              <div className="space-y-2.5">
                {messages.map(m => (
                  <div 
                    key={m.id} 
                    className={`flex flex-col ${m.sender === 'farmer' ? 'items-end' : 'items-start'}`}
                  >
                    <div 
                      className={`max-w-[88%] p-3 rounded-2xl text-xs shadow-sm leading-relaxed ${
                        m.sender === 'farmer' 
                          ? 'bg-emerald-700 text-white rounded-br-none font-medium' 
                          : m.isSafetyWarning
                            ? 'bg-red-50 border-2 border-red-300 text-red-900 rounded-bl-none'
                            : 'bg-white border border-emerald-100 text-slate-800 rounded-bl-none'
                      }`}
                    >
                      {m.isSafetyWarning && (
                        <div className="flex items-center space-x-1 text-red-700 font-bold mb-1 text-[11px]">
                          <AlertTriangle className="w-3.5 h-3.5" />
                          <span>చట్టబద్ధ నిషేధం • CIBRC SAFETY GATE</span>
                        </div>
                      )}

                      <p>{m.text}</p>

                      {/* Verified CIBRC Dosage Badges */}
                      {m.dilutionKnapsack && (
                        <div className="mt-2 pt-2 border-t border-emerald-100/60 space-y-1 text-[10px]">
                          <div className="flex items-center justify-between text-emerald-900 font-semibold bg-emerald-50 px-2 py-1 rounded-md">
                            <span>15L స్ప్రే పంపునకు:</span>
                            <span className="font-bold text-emerald-700">{m.dilutionKnapsack}</span>
                          </div>
                          {m.phiDays && (
                            <div className="flex items-center justify-between text-slate-600 px-1">
                              <span>కోతకు ముందు వ్యవధి (PHI):</span>
                              <span className="font-bold text-amber-700">{m.phiDays} రోజులు</span>
                            </div>
                          )}
                        </div>
                      )}

                      {/* Source Citation */}
                      {m.sourceCitation && (
                        <div className="mt-1.5 text-[9px] text-slate-400 flex items-center space-x-1">
                          <ShieldCheck className="w-3 h-3 text-emerald-600 flex-shrink-0" />
                          <span className="truncate">{m.sourceCitation}</span>
                        </div>
                      )}

                      {/* Escalation Notice */}
                      {m.isEscalated && (
                        <div className="mt-2 bg-amber-50 border border-amber-200 text-amber-900 p-1.5 rounded-lg text-[10px] flex items-center space-x-1 font-medium">
                          <AlertTriangle className="w-3.5 h-3.5 text-amber-600 flex-shrink-0" />
                          <span>ఈ సమస్య మండల వ్యవసాయ అధికారి (MAO) గారికి బదిలీ చేయబడింది.</span>
                        </div>
                      )}
                    </div>

                    {/* Audio Playback Trigger */}
                    {m.sender === 'ai' && (
                      <button 
                        onClick={() => speakText(m.text)}
                        className="mt-1 text-[10px] text-emerald-700 flex items-center space-x-1 hover:text-emerald-900 px-1 font-semibold"
                      >
                        <Volume2 className="w-3 h-3" />
                        <span>వినండి (Listen Audio)</span>
                      </button>
                    )}
                  </div>
                ))}

                {isLoading && (
                  <div className="flex items-center space-x-2 text-xs text-emerald-800 bg-white p-2.5 rounded-2xl w-fit shadow-sm border border-emerald-100">
                    <RefreshCw className="w-3.5 h-3.5 animate-spin text-emerald-600" />
                    <span>పరిశీలిస్తున్నాము... (Verifying with ICAR/CIBRC)</span>
                  </div>
                )}
              </div>

              {/* Quick Preset Queries */}
              <div className="pt-2">
                <p className="text-[10px] font-bold uppercase text-emerald-800 mb-1.5 flex items-center space-x-1">
                  <Sparkles className="w-3 h-3 text-harvest-600" />
                  <span>రైతులు తరచుగా అడిగే ప్రశ్నలు (Quick Voice Queries):</span>
                </p>
                <div className="flex flex-col space-y-1.5">
                  {sampleQueries.map((sq, idx) => (
                    <button
                      key={idx}
                      onClick={() => handleSendQuery(sq.query)}
                      className={`text-left text-[11px] p-2 rounded-xl transition-all border flex items-center justify-between ${
                        sq.label.includes('సేఫ్టీ')
                          ? 'bg-red-50/80 hover:bg-red-100 border-red-200 text-red-900 font-semibold'
                          : 'bg-white hover:bg-emerald-100 border-emerald-200/80 text-emerald-950 font-medium'
                      }`}
                    >
                      <span className="truncate">{sq.label}</span>
                      <ChevronRight className="w-3.5 h-3.5 text-emerald-600 flex-shrink-0" />
                    </button>
                  ))}
                </div>
              </div>
            </>
          )}

          {activeTab === 'camera' && (
            <div className="space-y-3">
              <div className="bg-slate-900 text-white rounded-2xl p-4 flex flex-col items-center justify-center space-y-2 border border-slate-700 aspect-video relative overflow-hidden">
                <Camera className="w-10 h-10 text-emerald-400 animate-pulse" />
                <p className="text-xs font-semibold text-center text-slate-200">
                  ఆకు లేదా పంట ఫోటో తీయండి
                </p>
                <p className="text-[10px] text-slate-400 text-center">
                  (CameraX with Automatic EXIF Sanitization)
                </p>
                <div className="absolute top-2 right-2 bg-emerald-600 text-white text-[9px] px-1.5 py-0.5 rounded font-bold">
                  EXIF SAFE
                </div>
              </div>

              <div className="space-y-2">
                <p className="text-xs font-bold text-slate-800">నమూనా పంట వ్యాధులు (Sample Diagnosis):</p>
                <button
                  onClick={() => handleSendQuery('టమోటా ఆకులపై నల్లటి వలయాలు (Early Blight) వచ్చాయి, నివారణ ఏమిటి?')}
                  className="w-full text-left p-2.5 bg-white border border-emerald-200 rounded-xl text-xs hover:bg-emerald-50 flex items-center justify-between"
                >
                  <div>
                    <p className="font-bold text-slate-800">టమోటా ముందస్తు తెగులు (Early Blight)</p>
                    <p className="text-[10px] text-slate-500">Alternaria solani • Target board symptom</p>
                  </div>
                  <ChevronRight className="w-4 h-4 text-emerald-600" />
                </button>

                <button
                  onClick={() => handleSendQuery('మిరప ఆకులు పైకి దోనెలా ముడుచుకుపోయాయి (Chilli Thrips).')}
                  className="w-full text-left p-2.5 bg-white border border-emerald-200 rounded-xl text-xs hover:bg-emerald-50 flex items-center justify-between"
                >
                  <div>
                    <p className="font-bold text-slate-800">మిరప తామర పురుగు (Chilli Thrips)</p>
                    <p className="text-[10px] text-slate-500">Upward leaf cupping • Boat shape</p>
                  </div>
                  <ChevronRight className="w-4 h-4 text-emerald-600" />
                </button>
              </div>
            </div>
          )}

          {activeTab === 'tickets' && (
            <div className="space-y-2.5">
              <h3 className="text-xs font-bold text-slate-800">నా పరిష్కృత / విచారణ టికెట్లు</h3>
              {farmerTickets.length === 0 ? (
                <div className="bg-white p-6 rounded-2xl text-center text-xs text-slate-400 border border-slate-200">
                  ప్రస్తుతం పెండింగ్ టికెట్లు ఏవీ లేవు. మీ పంట సమస్యను అడిగితే ఇక్కడ నమోదు చేయబడుతుంది.
                </div>
              ) : (
                farmerTickets.map((t, idx) => (
                  <div key={idx} className="bg-white p-3 rounded-xl border border-amber-200 shadow-sm space-y-1.5">
                    <div className="flex items-center justify-between">
                      <span className="text-[10px] font-mono font-bold text-emerald-800">{t.id}</span>
                      <span className="text-[9px] bg-amber-100 text-amber-800 font-bold px-1.5 py-0.5 rounded">
                        అధికారి వద్ద పెండింగ్
                      </span>
                    </div>
                    <p className="text-xs font-semibold text-slate-800">{t.issue}</p>
                    <p className="text-[10px] text-slate-500">అధికారి: Dr. K. Srinivas Rao (MAO Warangal)</p>
                  </div>
                ))
              )}
            </div>
          )}

          {activeTab === 'mandi' && (
            <div className="space-y-2.5">
              <h3 className="text-xs font-bold text-slate-800">మండి ధరలు & వాతావరణ సలహా</h3>
              
              {/* Weather Card */}
              {weatherData && (
                <div className="bg-gradient-to-br from-emerald-600 to-forest-800 text-white p-3 rounded-2xl shadow-sm space-y-2">
                  <div className="flex items-center justify-between text-xs">
                    <span className="font-bold">{weatherData.district} జిల్లా వాతావరణం</span>
                    <span>{weatherData.current_conditions?.temperature_c}°C</span>
                  </div>
                  <p className="text-[11px] text-emerald-100">
                    తేమ: {weatherData.current_conditions?.humidity_pct}% • గాలి వేగం: {weatherData.current_conditions?.wind_speed_kmh} km/h
                  </p>
                  {weatherData.active_risk_alerts?.length > 0 && (
                    <div className="bg-white/10 p-2 rounded-xl text-[10px] space-y-1">
                      <p className="font-bold text-amber-300 flex items-center space-x-1">
                        <CloudRain className="w-3 h-3" />
                        <span>అగ్గితెగులు ముప్పు హెచ్చరిక (IMD):</span>
                      </p>
                      <p className="text-slate-100">{weatherData.active_risk_alerts[0].recommended_action}</p>
                    </div>
                  )}
                </div>
              )}

              {/* Mandi Prices */}
              <div className="bg-white rounded-2xl p-3 border border-emerald-100 space-y-2 shadow-sm">
                <p className="text-xs font-bold text-slate-800">వరంగల్ ఎనుమాముల మార్కెట్ (APMC):</p>
                {mandiData.map((m, idx) => (
                  <div key={idx} className="flex items-center justify-between text-xs py-1.5 border-b border-slate-100 last:border-none">
                    <div>
                      <span className="font-bold text-slate-800">{m.commodity}</span>
                      <span className="text-[10px] text-slate-500 block">MSP: ₹{m.msp_price_inr}/క్విం</span>
                    </div>
                    <div className="text-right">
                      <span className="font-extrabold text-emerald-700">₹{m.modal_price_inr}</span>
                      <span className="text-[9px] text-emerald-600 block font-semibold">మోడల్ ధర</span>
                    </div>
                  </div>
                ))}
              </div>
            </div>
          )}

        </div>

        {/* Tactile Voice PTT Button & Controls */}
        <div className="p-3 bg-white/95 border-t border-emerald-100 flex flex-col items-center space-y-2 shadow-lg">
          {/* Central 80dp Tactile PTT Button */}
          <div className="relative flex items-center justify-center">
            {isRecording && (
              <span className="absolute w-24 h-24 rounded-full bg-emerald-400/40 animate-ping" />
            )}
            <button
              onClick={handleSimulatePTT}
              className={`w-20 h-20 rounded-full flex flex-col items-center justify-center shadow-xl transition-all duration-300 border-4 ${
                isRecording 
                  ? 'bg-red-600 border-red-300 scale-105 text-white' 
                  : 'bg-emerald-600 hover:bg-emerald-700 border-emerald-200 text-white active:scale-95'
              }`}
            >
              {isRecording ? <MicOff className="w-8 h-8 animate-bounce" /> : <Mic className="w-8 h-8" />}
              <span className="text-[9px] font-black uppercase tracking-wider mt-0.5">
                {isRecording ? 'రికార్డింగ్...' : 'మాట్లాడండి'}
              </span>
            </button>
          </div>
          <p className="text-[10px] text-slate-500 font-semibold text-center">
            {isRecording ? 'వాయిస్ వింటున్నాము... (Recording Indic Voice)' : 'బటన్ నొక్కి మీ ప్రశ్న అడగండి (Push-to-Talk)'}
          </p>

          {/* Quick Text Input fallback */}
          <div className="w-full flex items-center space-x-1.5 pt-1">
            <input 
              type="text"
              value={queryInput}
              onChange={e => setQueryInput(e.target.value)}
              onKeyDown={e => e.key === 'Enter' && handleSendQuery(queryInput)}
              placeholder="లేదా ఇక్కడ టైప్ చేయండి (or type query)..."
              className="flex-1 text-xs px-3 py-1.5 rounded-xl border border-slate-300 focus:outline-none focus:ring-1 focus:ring-emerald-600 bg-slate-50"
            />
            <button 
              onClick={() => handleSendQuery(queryInput)}
              className="p-1.5 bg-emerald-700 text-white rounded-xl hover:bg-emerald-800"
            >
              <Send className="w-3.5 h-3.5" />
            </button>
          </div>
        </div>

        {/* Android Navigation Bar / Tab Bar */}
        <div className="h-12 bg-white border-t border-slate-200 px-4 flex items-center justify-around text-slate-500 text-[10px] font-semibold">
          <button 
            onClick={() => setActiveTab('voice')}
            className={`flex flex-col items-center space-y-0.5 ${activeTab === 'voice' ? 'text-emerald-700 font-bold' : ''}`}
          >
            <Mic className="w-4 h-4" />
            <span>వాయిస్</span>
          </button>
          <button 
            onClick={() => setActiveTab('camera')}
            className={`flex flex-col items-center space-y-0.5 ${activeTab === 'camera' ? 'text-emerald-700 font-bold' : ''}`}
          >
            <Camera className="w-4 h-4" />
            <span>కెమెరా</span>
          </button>
          <button 
            onClick={() => setActiveTab('mandi')}
            className={`flex flex-col items-center space-y-0.5 ${activeTab === 'mandi' ? 'text-emerald-700 font-bold' : ''}`}
          >
            <TrendingUp className="w-4 h-4" />
            <span>మండి</span>
          </button>
          <button 
            onClick={() => setActiveTab('tickets')}
            className={`flex flex-col items-center space-y-0.5 ${activeTab === 'tickets' ? 'text-emerald-700 font-bold' : ''}`}
          >
            <ShieldCheck className="w-4 h-4" />
            <span>టికెట్లు</span>
          </button>
        </div>

      </div>
    </div>
  );
};
