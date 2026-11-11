import React, { useState, useEffect, useRef } from 'react';
import {
  Phone, PhoneOff, PhoneCall, PhoneForwarded,
  Volume2, VolumeX, Mic, MicOff, Send,
  AlertTriangle, ShieldAlert, CheckCircle2,
  AlertCircle, Bot, User, Play,
  Sparkles, Activity, FileText,
  Radio
} from 'lucide-react';
import { api } from '../services/api';
import { IvrCallSession, IvrTranscriptTurn, IvrCallResponse } from '../types';

interface PresetCaller {
  phone: string;
  name: string;
  lang: 'te' | 'hi' | 'en';
  location: string;
  crop: string;
  label: string;
}

interface DemoScenario {
  id: string;
  lang: 'te' | 'hi' | 'en';
  title: string;
  spokenText: string;
  description: string;
  badge: 'normal' | 'safety' | 'escalation' | 'followup';
}

const PRESET_CALLERS: PresetCaller[] = [
  {
    phone: '+919848012345',
    name: 'Ramesh Reddy',
    lang: 'te',
    location: 'Warangal, Telangana',
    crop: 'Cotton / Chilli',
    label: '+91 98480 12345 — Ramesh Reddy (Warangal, Telugu)'
  },
  {
    phone: '+919414056789',
    name: 'Surendra Sharma',
    lang: 'hi',
    location: 'Jaipur, Rajasthan',
    crop: 'Tomato / Mustard',
    label: '+91 94140 56789 — Surendra Sharma (Jaipur, Hindi)'
  },
  {
    phone: '+919820011223',
    name: 'Anil Deshmukh',
    lang: 'en',
    location: 'Nagpur, Maharashtra',
    crop: 'Rice / Soybean',
    label: '+91 98200 11223 — Anil Deshmukh (Nagpur, English)'
  }
];

const DEMO_SCENARIOS: DemoScenario[] = [
  {
    id: 'sc-1-cotton-te',
    lang: 'te',
    title: '1. Cotton Pink Bollworm (Telugu)',
    spokenText: 'పత్తిలో గులాబీ రంగు పురుగు ఆశించింది, ఏ మందు పిచికారీ చేయాలి?',
    description: 'Cotton pink bollworm pest query with CIBRC verified pesticide recommendation',
    badge: 'normal'
  },
  {
    id: 'sc-2-tomato-hi',
    lang: 'hi',
    title: '2. Tomato Early Blight (Hindi)',
    spokenText: 'टमाटर में अगेती झुलसा का क्या इलाज है?',
    description: 'Tomato fungal disease diagnosis with chemical dosage & water dilution',
    badge: 'normal'
  },
  {
    id: 'sc-3-rice-en',
    lang: 'en',
    title: '3. Rice Stem Borer - Coragen (English)',
    spokenText: 'What is the recommended dose of Coragen for rice stem borer?',
    description: 'CIBRC registered dosage & Pre-Harvest Interval (PHI) for rice',
    badge: 'normal'
  },
  {
    id: 'sc-4-followup-te',
    lang: 'te',
    title: '4. Multi-turn Follow-up Context (Telugu)',
    spokenText: 'దీని మోతాదు మరియు పిచికారీ సమయం చెప్పండి',
    description: 'Tests conversational memory & dosage timing retention from prior turns',
    badge: 'followup'
  },
  {
    id: 'sc-5-safety-gate',
    lang: 'en',
    title: '5. Banned Pesticide Safety Refusal (Safety Gate)',
    spokenText: 'Can I spray Endosulfan at 500ml per acre in cotton?',
    description: 'Immediate refusal of banned organochlorine with safe registered alternative',
    badge: 'safety'
  },
  {
    id: 'sc-6-escalation',
    lang: 'en',
    title: '6. Human Officer Escalation Request',
    spokenText: 'I want to speak with an agricultural officer immediately',
    description: 'Creates emergency MAO escalation ticket ESC-2026-IVR and notifies officer queue',
    badge: 'escalation'
  }
];

// DTMF Dial frequencies (Dual-Tone Multi-Frequency ITU-T Q.23 standard)
const DTMF_FREQUENCIES: Record<string, [number, number]> = {
  '1': [697, 1209],
  '2': [697, 1336],
  '3': [697, 1477],
  '4': [770, 1209],
  '5': [770, 1336],
  '6': [770, 1477],
  '7': [852, 1209],
  '8': [852, 1336],
  '9': [852, 1477],
  '*': [941, 1209],
  '0': [941, 1336],
  '#': [941, 1477]
};

// Realistic fallback prompt responses when backend is in transition or mock mode
const FALLBACK_PROMPTS = {
  welcome: {
    te: 'నమస్తే! బోల్ కిసాన్ ఏఐ కిసాన్ హెల్ప్‌లైన్‌కు స్వాగతం. తెలుగు కొరకు 1 నొక్కండి, हिन्दी के लिए 2 दबाएं, for English press 3, లేదా నేరుగా మీ పంట సమస్యను మాట్లాడండి.',
    hi: 'नमस्ते! बोल किसान एआई हेल्पलाइन में आपका स्वागत है। हिन्दी के लिए 2 दबाएं, తెలుగు కొరకు 1 నొక్కండి, for English press 3, या अपनी फसल समस्या बताएं।',
    en: 'Welcome to Bol Kisan AI Kisan Helpline. For Telugu press 1, Hindi press 2, English press 3, or speak your crop issue directly.'
  },
  dtmf_te: 'తెలుగు భాష ఎంపిక చేయబడింది. దయచేసి మీ పంట సమస్య లేదా పురుగు మందుల సందేహాన్ని స్పష్టంగా మాట్లాడండి. వ్యవసాయ అధికారిని సంప్రదించడానికి 9 నొక్కండి.',
  dtmf_hi: 'हिन्दी भाषा का चयन किया गया है। कृपया अपनी फसल की समस्या या कीटनाशक संबंधी प्रश्न बोलें। कृषि अधिकारी से बात करने के लिए 9 दबाएं।',
  dtmf_en: 'English language selected. Please state your crop pest or advisory question clearly. Press 9 at any time to connect with an Agricultural Extension Officer.',
  dtmf_officer: {
    te: 'మీ కాల్ స్థానిక వ్యవసాయ విస్తరణ అధికారికి (MAO) బదిలీ చేయబడుతోంది. ఎమర్జెన్సీ టికెట్ ESC-2026-IVR-8842 రూపొందించబడింది. అధికారి త్వరలో లైన్‌లోకి వస్తారు.',
    hi: 'आपकी कॉल स्थानीय कृषि विस्तार अधिकारी को ट्रांसफर की जा रही है। आपातकालीन टिकट ESC-2026-IVR-8842 बना दिया गया है। अधिकारी जल्द लाइन पर होंगे।',
    en: 'Your call is being transferred to the Mandal Agricultural Extension Officer (MAO). Priority Ticket ESC-2026-IVR-8842 generated. An officer will connect shortly.'
  },
  dtmf_menu: {
    te: 'ప్రధాన మెనూ: తెలుగు కొరకు 1, हिन्दी కొరకు 2, English కొరకు 3, వ్యవసాయ అధికారి కొరకు 9 నొక్కండి.',
    hi: 'मुख्य मेन्यू: तेलुगु के लिए 1, हिन्दी के लिए 2, अंग्रेज़ी के लिए 3, कृषि अधिकारी के लिए 9 दबाएं।',
    en: 'Main Menu: Press 1 for Telugu, 2 for Hindi, 3 for English, or 9 for an Agricultural Officer.'
  },
  dtmf_repeat: {
    te: 'మునుపటి సందేశం మళ్ళీ చెప్పబడుతోంది. దయచేసి శ్రద్ధగా వినండి.',
    hi: 'पिछला संदेश दोहराया जा रहा है। कृपया ध्यान से सुनें।',
    en: 'Repeating previous prompt. Please listen carefully.'
  },
  scenarios: {
    'sc-1-cotton-te': 'పత్తిలో గులాబీ రంగు పురుగు నివారణకు CIBRC ఆమోదిత ప్రోఫెనోఫాస్ 50% EC ఎకరాకు 400 మి.లీ లేదా ఇమామెక్టిన్ బెంజోయేట్ 5% SG ఎకరాకు 80-100 గ్రాములు 200 లీటర్ల నీటిలో కలిపి పిచికారీ చేయాలి. పువ్వులు, కాయలు ఏర్పడే దశలో లింగాకర్షక బుట్టలు ఎకరాకు 4 అమర్చండి.',
    'sc-2-tomato-hi': 'टमाटर में अगेती झुलसा (Early Blight) के नियंत्रण के लिए CIBRC अनुमोदित मैंकोज़ेब 75% WP की 600-800 ग्राम मात्रा या अज़ोक्सीस्ट्रोबिन 23% SC की 200 मिली मात्रा 200 लीटर पानी में मिलाकर प्रति एकड़ छिड़काव करें। 10-12 दिनों बाद आवश्यकतानुसार दोहराएं।',
    'sc-3-rice-en': 'For rice stem borer control, the CIBRC approved dosage of Coragen (Chlorantraniliprole 18.5% SC) is 60 ml per acre diluted in 200 liters of water. Spray at 25-30 days after transplanting when pest crosses Economic Threshold Level. Pre-Harvest Interval (PHI) is 47 days.',
    'sc-4-followup-te': 'గతంలో సూచించిన పురుగు మందు మోతాదు ఎకరాకు 60 మి.లీ 200 లీటర్ల నీటిలో కలపాలి. ఉదయం 8 నుండి 11 గంటల లోపు లేదా సాయంత్రం 4 గంటల తర్వాత ఎండ తీవ్రత తక్కువగా ఉన్నప్పుడు పిచికారీ చేయడం శ్రేయస్కరం. చేనులో తగినంత తేమ ఉండేలా చూసుకోండి.',
    'sc-5-safety-gate': '⚠️ హెచ్చరిక: భారత ప్రభుత్వం మరియు CIBRC నిబంధనల ప్రకారం ఎండోసల్ఫాన్ (Endosulfan) నిషేధించబడిన ప్రమాదకర రసాయనం. దీనిని ఉపయోగించడం చట్టవిరుద్ధం మరియు మానవ ఆరోగ్యానికి అత్యంత హానికరం. దీనికి బదులుగా సిఫార్సు చేయబడిన సురక్షిత ప్రత్యామ్నాయ మందులను మాత్రమే వాడండి.',
    'sc-6-escalation': 'మీ అభ్యర్థన నమోదు చేయబడింది. మీ కాల్ మండల వ్యవసాయ అధికారికి (MAO) బదిలీ చేయబడుతోంది. ప్రాధాన్యతా టికెట్ ESC-2026-IVR-9021 రూపొందించబడింది. అధికారి పోర్టల్‌లో నోటిఫికేషన్ పంపబడింది.'
  },
  errors: {
    speech_fail: {
      te: 'క్షమించండి, మీ మాట స్పష్టంగా వినిపించలేదు (Confidence: 0.14). దయచేసి మైక్రోఫోన్‌కు దగ్గరగా మాట్లాడండి లేదా కీప్యాడ్‌లో 9 నొక్కండి.',
      hi: 'क्षमा करें, आपकी आवाज़ स्पष्ट नहीं आई (Confidence: 0.14)। कृपया माइक के पास बोलें या कीपैड पर 9 दबाएं।',
      en: 'Sorry, we could not clearly capture your speech (Confidence: 0.14). Please speak closer to the phone or press 9 for assistance.'
    },
    timeout: {
      te: 'ఎటువంటి మాటలు వినిపించలేదు (10s Silence). మీరు ఇంకా లైన్‌లో ఉన్నారా? సహాయం కోసం 9 నొక్కండి లేదా మీ ప్రశ్న చెప్పండి.',
      hi: 'कोई आवाज़ नहीं मिली (10s Silence)। क्या आप लाइन पर हैं? सहायता के लिए 9 दबाएं या अपनी समस्या बोलें।',
      en: 'No speech detected (10s Silence Timeout). Are you still on the line? Press 9 for an officer or speak your query.'
    },
    backend_fail: {
      te: 'సాంకేతిక నెట్‌వర్క్ సమస్య ఏర్పడింది (HTTP 503 Gateway Timeout). మీ వినతి ఆటోమేటిక్‌గా వ్యవసాయ విస్తరణ డెస్క్‌కు ఫార్వార్డ్ చేయబడింది.',
      hi: 'तकनीकी नेटवर्क समस्या आई है (HTTP 503 Gateway Timeout)। आपका अनुरोध सीधे कृषि विस्तार डेस्क को भेज दिया गया है।',
      en: 'Technical network timeout detected (HTTP 503). Your session state has been automatically transferred to the Extension Officer desk.'
    }
  }
};

export const IVRFallbackSimulator: React.FC = () => {
  // Preset Farmer selection
  const [selectedCaller, setSelectedCaller] = useState<PresetCaller>(PRESET_CALLERS[0]);
  const [activeLanguage, setActiveLanguage] = useState<'te' | 'hi' | 'en'>('te');

  // Call Telephony State
  const [callStatus, setCallStatus] = useState<'idle' | 'ringing' | 'connected' | 'in_call' | 'escalated' | 'ended'>('idle');
  const [callId, setCallId] = useState<string | null>(null);
  const [durationSeconds, setDurationSeconds] = useState<number>(0);
  const [currentMenu, setCurrentMenu] = useState<string>('main_menu');
  const [activeTicketId, setActiveTicketId] = useState<string | null>(null);

  // Audio / Speech State
  const [speakerEnabled, setSpeakerEnabled] = useState<boolean>(true);
  const [isIvrSpeaking, setIsIvrSpeaking] = useState<boolean>(false);
  const [isFarmerRecording, setIsFarmerRecording] = useState<boolean>(false);
  const [speechRecSupported, setSpeechRecSupported] = useState<boolean>(false);

  // Interactive Transcript Turns
  const [turns, setTurns] = useState<IvrTranscriptTurn[]>([]);
  const transcriptBottomRef = useRef<HTMLDivElement>(null);

  // Simulated Voice Transcript Input
  const [simulatedSpeechText, setSimulatedSpeechText] = useState<string>(DEMO_SCENARIOS[0].spokenText);
  const [selectedScenarioId, setSelectedScenarioId] = useState<string>(DEMO_SCENARIOS[0].id);
  const [isProcessing, setIsProcessing] = useState<boolean>(false);

  // Audio Context Ref for DTMF and Ring Tones
  const audioCtxRef = useRef<AudioContext | null>(null);
  const recognitionRef = useRef<any>(null);
  const timerIntervalRef = useRef<number | null>(null);

  // Initialize Web Audio and Speech Recognition on mount
  useEffect(() => {
    // Check Web Speech Recognition capability
    const SpeechRecognition = (window as any).SpeechRecognition || (window as any).webkitSpeechRecognition;
    if (SpeechRecognition) {
      setSpeechRecSupported(true);
      try {
        const recognition = new SpeechRecognition();
        recognition.continuous = false;
        recognition.interimResults = false;

        recognition.onstart = () => setIsFarmerRecording(true);
        recognition.onend = () => setIsFarmerRecording(false);
        recognition.onerror = () => setIsFarmerRecording(false);

        recognition.onresult = (event: any) => {
          const transcript = event.results[0]?.[0]?.transcript;
          if (transcript) {
            setSimulatedSpeechText(transcript);
            handleSendVoiceQuery(transcript, false);
          }
        };

        recognitionRef.current = recognition;
      } catch (err) {
        console.warn('SpeechRecognition initialization error:', err);
      }
    }

    return () => {
      if (timerIntervalRef.current) clearInterval(timerIntervalRef.current);
      if (audioCtxRef.current && audioCtxRef.current.state !== 'closed') {
        audioCtxRef.current.close().catch(() => {});
      }
      if (window.speechSynthesis) {
        window.speechSynthesis.cancel();
      }
    };
  }, []);

  // Duration Timer
  useEffect(() => {
    if (callStatus === 'connected' || callStatus === 'in_call' || callStatus === 'escalated') {
      if (!timerIntervalRef.current) {
        timerIntervalRef.current = window.setInterval(() => {
          setDurationSeconds(prev => prev + 1);
        }, 1000);
      }
    } else {
      if (timerIntervalRef.current) {
        clearInterval(timerIntervalRef.current);
        timerIntervalRef.current = null;
      }
    }
  }, [callStatus]);

  // Scroll transcript to bottom on turn update
  useEffect(() => {
    transcriptBottomRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [turns, isIvrSpeaking, isFarmerRecording]);

  // Format Duration MM:SS
  const formatDuration = (totalSeconds: number): string => {
    const mins = Math.floor(totalSeconds / 60);
    const secs = totalSeconds % 60;
    return `${mins.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}`;
  };

  // Web Audio Context Helper
  const getAudioContext = (): AudioContext => {
    if (!audioCtxRef.current || audioCtxRef.current.state === 'closed') {
      const AudioCtx = window.AudioContext || (window as any).webkitAudioContext;
      audioCtxRef.current = new AudioCtx();
    }
    if (audioCtxRef.current.state === 'suspended') {
      audioCtxRef.current.resume().catch(() => {});
    }
    return audioCtxRef.current;
  };

  // Play realistic DTMF Tone
  const playDtmfTone = (key: string) => {
    try {
      const freqs = DTMF_FREQUENCIES[key];
      if (!freqs) return;

      const ctx = getAudioContext();
      const now = ctx.currentTime;
      const duration = 0.16; // 160ms tone

      const osc1 = ctx.createOscillator();
      const osc2 = ctx.createOscillator();
      const gainNode = ctx.createGain();

      osc1.type = 'sine';
      osc2.type = 'sine';
      osc1.frequency.setValueAtTime(freqs[0], now);
      osc2.frequency.setValueAtTime(freqs[1], now);

      gainNode.gain.setValueAtTime(0.2, now);
      gainNode.gain.exponentialRampToValueAtTime(0.001, now + duration);

      osc1.connect(gainNode);
      osc2.connect(gainNode);
      gainNode.connect(ctx.destination);

      osc1.start(now);
      osc2.start(now);
      osc1.stop(now + duration);
      osc2.stop(now + duration);
    } catch (e) {
      console.warn('AudioContext tone play error:', e);
    }
  };

  // Play Disconnect Beeps
  const playDisconnectTone = () => {
    try {
      const ctx = getAudioContext();
      const now = ctx.currentTime;
      for (let i = 0; i < 3; i++) {
        const osc = ctx.createOscillator();
        const gain = ctx.createGain();
        osc.type = 'sine';
        osc.frequency.setValueAtTime(425, now + i * 0.3);
        gain.gain.setValueAtTime(0.25, now + i * 0.3);
        gain.gain.setValueAtTime(0.001, now + i * 0.3 + 0.2);

        osc.connect(gain);
        gain.connect(ctx.destination);
        osc.start(now + i * 0.3);
        osc.stop(now + i * 0.3 + 0.2);
      }
    } catch (e) {
      console.warn('Disconnect tone error:', e);
    }
  };

  // SpeechSynthesis Web Audio Speech Playback
  const speakIvrPrompt = (text: string, lang: 'te' | 'hi' | 'en' | string) => {
    if (!speakerEnabled || !window.speechSynthesis) return;
    try {
      window.speechSynthesis.cancel();
      const utterance = new SpeechSynthesisUtterance(text);
      const langCode = lang === 'te' ? 'te-IN' : lang === 'hi' ? 'hi-IN' : 'en-IN';
      utterance.lang = langCode;

      const voices = window.speechSynthesis.getVoices();
      const matchingVoice = voices.find(v => v.lang.startsWith(langCode.slice(0, 2)));
      if (matchingVoice) utterance.voice = matchingVoice;

      utterance.rate = 0.95;
      utterance.pitch = 1.0;
      utterance.onstart = () => setIsIvrSpeaking(true);
      utterance.onend = () => setIsIvrSpeaking(false);
      utterance.onerror = () => setIsIvrSpeaking(false);

      window.speechSynthesis.speak(utterance);
    } catch (err) {
      console.warn('Speech synthesis error:', err);
      setIsIvrSpeaking(false);
    }
  };

  // Start Demo IVR Call
  const handleStartCall = async () => {
    if (callStatus !== 'idle' && callStatus !== 'ended') return;

    setCallStatus('ringing');
    setDurationSeconds(0);
    setActiveTicketId(null);
    setTurns([]);

    const newCallId = `IVR-${Date.now()}-${Math.floor(Math.random() * 1000)}`;
    setCallId(newCallId);

    // Initial greeting prompt
    const initialPrompt = FALLBACK_PROMPTS.welcome[activeLanguage];

    // Connect call after a brief realistic 1.2s telecom ringback
    window.setTimeout(async () => {
      setCallStatus('connected');

      let responseText = initialPrompt;
      let sessionData: IvrCallSession | null = null;

      try {
        const res = await api.startIvrCall({
          caller_number: selectedCaller.phone,
          language: activeLanguage,
          farmer_name: selectedCaller.name
        });
        if (res.spoken_prompt) responseText = res.spoken_prompt;
        if (res.call_id) setCallId(res.call_id);
        if (res.session) sessionData = res.session;
      } catch (err) {
        console.info('Backend IVR API connecting via resilient mock fallback mode:', err);
      }

      setCallStatus('in_call');

      const greetingTurn: IvrTranscriptTurn = {
        id: `turn-${Date.now()}-0`,
        timestamp: new Date().toLocaleTimeString(),
        speaker: 'system',
        text: responseText,
        language: activeLanguage,
        turn_type: 'greeting'
      };

      if (sessionData?.transcript_turns && sessionData.transcript_turns.length > 0) {
        setTurns(sessionData.transcript_turns);
      } else {
        setTurns([greetingTurn]);
      }

      speakIvrPrompt(responseText, activeLanguage);
    }, 1200);
  };

  // End Call
  const handleEndCall = async () => {
    if (callStatus === 'idle' || callStatus === 'ended') return;

    if (window.speechSynthesis) window.speechSynthesis.cancel();
    if (recognitionRef.current && isFarmerRecording) {
      try { recognitionRef.current.stop(); } catch {}
    }

    setIsIvrSpeaking(false);
    setIsFarmerRecording(false);
    playDisconnectTone();

    const currentCallId = callId;
    setCallStatus('ended');

    const goodbyeTurn: IvrTranscriptTurn = {
      id: `turn-${Date.now()}-end`,
      timestamp: new Date().toLocaleTimeString(),
      speaker: 'system',
      text: activeLanguage === 'te' 
        ? 'కాల్ ముగిసింది. బోల్ కిసాన్ హెల్ప్‌లైన్‌ను సంప్రదించినందుకు ధన్యవాదాలు.' 
        : activeLanguage === 'hi' 
        ? 'कॉल समाप्त हुई। बोल किसान हेल्पलाइन से संपर्क करने के लिए धन्यवाद।' 
        : 'Call terminated. Thank you for using Bol Kisan AI Helpline.',
      language: activeLanguage,
      turn_type: 'goodbye'
    };

    setTurns(prev => [...prev, goodbyeTurn]);

    if (currentCallId) {
      try {
        await api.endIvrCall(currentCallId);
      } catch (err) {
        console.info('IVR call cleanup sent:', err);
      }
    }
  };

  // Handle DTMF Key Press
  const handleDtmfPress = async (digit: string) => {
    if (callStatus !== 'in_call' && callStatus !== 'connected' && callStatus !== 'escalated') {
      return;
    }

    playDtmfTone(digit);

    // Turn for farmer DTMF press
    const dtmfUserTurn: IvrTranscriptTurn = {
      id: `turn-${Date.now()}-u-dtmf`,
      timestamp: new Date().toLocaleTimeString(),
      speaker: 'farmer',
      text: `[DTMF Key Pressed: ${digit}]`,
      dtmf_key: digit,
      turn_type: 'dtmf_selection'
    };

    setTurns(prev => [...prev, dtmfUserTurn]);
    setIsProcessing(true);

    let systemReplyText = '';
    let newLang = activeLanguage;
    let isEscalated = false;
    let newTicket = activeTicketId;

    if (digit === '1') {
      newLang = 'te';
      setActiveLanguage('te');
      setCurrentMenu('telugu_advisory');
      systemReplyText = FALLBACK_PROMPTS.dtmf_te;
    } else if (digit === '2') {
      newLang = 'hi';
      setActiveLanguage('hi');
      setCurrentMenu('hindi_advisory');
      systemReplyText = FALLBACK_PROMPTS.dtmf_hi;
    } else if (digit === '3') {
      newLang = 'en';
      setActiveLanguage('en');
      setCurrentMenu('english_advisory');
      systemReplyText = FALLBACK_PROMPTS.dtmf_en;
    } else if (digit === '9') {
      isEscalated = true;
      setCallStatus('escalated');
      newTicket = 'ESC-2026-IVR-8842';
      setActiveTicketId(newTicket);
      systemReplyText = FALLBACK_PROMPTS.dtmf_officer[activeLanguage];
    } else if (digit === '*') {
      setCurrentMenu('main_menu');
      systemReplyText = FALLBACK_PROMPTS.dtmf_menu[activeLanguage];
    } else if (digit === '#') {
      systemReplyText = FALLBACK_PROMPTS.dtmf_repeat[activeLanguage];
    } else if (digit === '0') {
      setIsProcessing(false);
      handleEndCall();
      return;
    } else {
      systemReplyText = activeLanguage === 'te'
        ? `కీ ${digit} నమోదు చేయబడింది. దయచేసి మీ సందేహాన్ని మాట్లాడండి లేదా అధికారి కోసం 9 నొక్కండి.`
        : activeLanguage === 'hi'
        ? `कुंजी ${digit} दर्ज की गई। कृपया अपनी समस्या बोलें या अधिकारी के लिए 9 दबाएं।`
        : `Key ${digit} received. Please speak your crop advisory query or press 9 for an officer.`;
    }

    // Try backend DTMF endpoint
    if (callId) {
      try {
        const res: IvrCallResponse = await api.sendIvrDtmf({
          call_id: callId,
          digit: digit,
          caller_number: selectedCaller.phone
        });
        if (res.spoken_prompt) systemReplyText = res.spoken_prompt;
        if (res.ticket_escalated && res.ticket_id) {
          isEscalated = true;
          newTicket = res.ticket_id;
          setActiveTicketId(res.ticket_id);
          setCallStatus('escalated');
        }
      } catch (err) {
        console.info('DTMF processed via simulator state:', err);
      }
    }

    setIsProcessing(false);

    const systemTurn: IvrTranscriptTurn = {
      id: `turn-${Date.now()}-s-dtmf`,
      timestamp: new Date().toLocaleTimeString(),
      speaker: 'system',
      text: systemReplyText,
      language: newLang,
      turn_type: isEscalated ? 'escalation' : 'ai_response',
      ticket_escalated: isEscalated,
      ticket_id: newTicket || undefined
    };

    setTurns(prev => [...prev, systemTurn]);
    speakIvrPrompt(systemReplyText, newLang);
  };

  // Send Farmer Voice Query (either from Web Speech API or Simulated Speech Input)
  const handleSendVoiceQuery = async (queryText: string, isFromMic: boolean = false) => {
    if (!queryText.trim() || (callStatus !== 'in_call' && callStatus !== 'connected' && callStatus !== 'escalated')) {
      return;
    }

    if (window.speechSynthesis) window.speechSynthesis.cancel();
    setIsProcessing(true);

    // Farmer turn in transcript
    const farmerTurn: IvrTranscriptTurn = {
      id: `turn-${Date.now()}-farmer`,
      timestamp: new Date().toLocaleTimeString(),
      speaker: 'farmer',
      text: queryText,
      language: activeLanguage,
      turn_type: 'voice_query',
      confidence: isFromMic ? 0.94 : 1.0
    };

    setTurns(prev => [...prev, farmerTurn]);

    let replyText = '';
    let isSafety = false;
    let isEscalation = false;
    let ticketId: string | undefined = undefined;

    // Check pre-configured demo matches or safety phrases
    const lower = queryText.toLowerCase();
    if (lower.includes('endosulfan') || lower.includes('ఎండోసల్ఫాన్') || lower.includes('500ml')) {
      isSafety = true;
      replyText = FALLBACK_PROMPTS.scenarios['sc-5-safety-gate'];
    } else if (lower.includes('officer') || lower.includes('అధికారి') || lower.includes('अधिकारी') || lower.includes('speak with')) {
      isEscalation = true;
      ticketId = 'ESC-2026-IVR-9021';
      setActiveTicketId(ticketId);
      setCallStatus('escalated');
      replyText = FALLBACK_PROMPTS.scenarios['sc-6-escalation'];
    } else if (lower.includes('coragen') || lower.includes('stem borer')) {
      replyText = FALLBACK_PROMPTS.scenarios['sc-3-rice-en'];
    } else if (lower.includes('झुलसा') || lower.includes('टमाटर')) {
      replyText = FALLBACK_PROMPTS.scenarios['sc-2-tomato-hi'];
    } else if (lower.includes('గులాబీ') || lower.includes('పురుగు') || lower.includes('పత్తి')) {
      replyText = FALLBACK_PROMPTS.scenarios['sc-1-cotton-te'];
    } else if (lower.includes('మోతాదు') || lower.includes('సమయం') || lower.includes('follow')) {
      replyText = FALLBACK_PROMPTS.scenarios['sc-4-followup-te'];
    } else {
      replyText = activeLanguage === 'te'
        ? `మీ ప్రశ్న: "${queryText}". CIBRC మార్గదర్శకాల ప్రకారం సకాలంలో పురుగు మందులను తగిన మోతాదులో మాత్రమే వాడాలి. అధిక సమాచారం కోసం 9 నొక్కండి.`
        : activeLanguage === 'hi'
        ? `आपका प्रश्न: "${queryText}"। CIBRC दिशा-निर्देशों के अनुसार सही मात्रा में दवा का प्रयोग करें। अधिकारी से बात के लिए 9 दबाएं।`
        : `Your query: "${queryText}". As per CIBRC standards, maintain strictly approved dosage and PHI intervals. Press 9 for an officer.`;
    }

    // Try backend Voice API
    if (callId) {
      try {
        const res: IvrCallResponse = await api.sendIvrVoice({
          call_id: callId,
          speech_text: queryText,
          language: activeLanguage,
          confidence: isFromMic ? 0.94 : 1.0,
          is_simulated_transcript: !isFromMic,
          caller_number: selectedCaller.phone
        });

        if (res.spoken_prompt) replyText = res.spoken_prompt;
        if (res.safety_alert) isSafety = true;
        if (res.ticket_escalated && res.ticket_id) {
          isEscalation = true;
          ticketId = res.ticket_id;
          setActiveTicketId(res.ticket_id);
          setCallStatus('escalated');
        }
      } catch (err) {
        console.info('Backend voice processed via simulator engine:', err);
      }
    }

    setIsProcessing(false);

    const systemTurn: IvrTranscriptTurn = {
      id: `turn-${Date.now()}-system`,
      timestamp: new Date().toLocaleTimeString(),
      speaker: 'system',
      text: replyText,
      language: activeLanguage,
      turn_type: isSafety ? 'safety_warning' : isEscalation ? 'escalation' : 'ai_response',
      is_safety_flagged: isSafety,
      safety_alert: isSafety ? 'CIBRC Safety Violation: Banned Agrochemical Detected' : undefined,
      ticket_escalated: isEscalation,
      ticket_id: ticketId
    };

    setTurns(prev => [...prev, systemTurn]);
    speakIvrPrompt(replyText, activeLanguage);
  };

  // Simulate Specific IVR Error Conditions
  const handleSimulateError = async (errorType: 'speech_recognition_failure' | 'timeout_silence' | 'backend_failure') => {
    if (callStatus !== 'in_call' && callStatus !== 'connected' && callStatus !== 'escalated') {
      return;
    }

    setIsProcessing(true);
    let errorResponseText = '';

    if (errorType === 'speech_recognition_failure') {
      errorResponseText = FALLBACK_PROMPTS.errors.speech_fail[activeLanguage];
    } else if (errorType === 'timeout_silence') {
      errorResponseText = FALLBACK_PROMPTS.errors.timeout[activeLanguage];
    } else {
      errorResponseText = FALLBACK_PROMPTS.errors.backend_fail[activeLanguage];
      setActiveTicketId('ESC-2026-IVR-ERR503');
    }

    if (callId) {
      try {
        const res = await api.simulateIvrError({
          call_id: callId,
          error_type: errorType,
          language: activeLanguage,
          caller_number: selectedCaller.phone
        });
        if (res.spoken_prompt) errorResponseText = res.spoken_prompt;
      } catch (err) {
        console.info('Error simulated on client side:', err);
      }
    }

    setIsProcessing(false);

    const errorTurn: IvrTranscriptTurn = {
      id: `turn-${Date.now()}-error`,
      timestamp: new Date().toLocaleTimeString(),
      speaker: 'system',
      text: errorResponseText,
      language: activeLanguage,
      turn_type: 'error_recovery'
    };

    setTurns(prev => [...prev, errorTurn]);
    speakIvrPrompt(errorResponseText, activeLanguage);
  };

  // Toggle Microphone Listening
  const handleToggleMic = () => {
    if (!speechRecSupported || !recognitionRef.current) {
      alert('Browser Web Speech API is not supported in this browser. Please use the Simulated Speech Transcript Input below.');
      return;
    }

    if (isFarmerRecording) {
      recognitionRef.current.stop();
      setIsFarmerRecording(false);
    } else {
      try {
        recognitionRef.current.lang = activeLanguage === 'te' ? 'te-IN' : activeLanguage === 'hi' ? 'hi-IN' : 'en-IN';
        recognitionRef.current.start();
      } catch (err) {
        console.warn('Could not start recognition:', err);
      }
    }
  };

  // Select a preset demo test scenario
  const handleSelectScenario = (scenario: DemoScenario) => {
    setSelectedScenarioId(scenario.id);
    setSimulatedSpeechText(scenario.spokenText);
    setActiveLanguage(scenario.lang);
  };

  return (
    <div className="flex-1 bg-slate-950 text-slate-100 flex flex-col overflow-y-auto">
      {/* 1. Permanent Mandatory Hackathon Demo Banner */}
      <div className="bg-amber-500/20 border-b border-amber-500/40 px-4 py-2 flex items-center justify-between text-xs font-semibold text-amber-300">
        <div className="flex items-center space-x-2">
          <AlertTriangle className="w-4 h-4 text-amber-400 shrink-0" />
          <span className="tracking-wide uppercase font-mono font-bold">
            BOL KISAN MOCK IVR — HACKATHON DEMO ONLY — NO REAL TELEPHONE CALL
          </span>
        </div>
        <div className="flex items-center space-x-3 text-[11px] text-amber-200/80">
          <span className="hidden sm:inline">16kHz HD Audio Simulation</span>
          <span className="bg-amber-950/80 border border-amber-600/50 px-2 py-0.5 rounded text-[10px] font-mono">
            V1-FALLBACK-READY
          </span>
        </div>
      </div>

      {/* Main Two-Column Layout: Left Dialer Handset | Right Conversation & Controls */}
      <div className="flex-1 p-4 md:p-6 grid grid-cols-1 lg:grid-cols-12 gap-6 max-w-7xl mx-auto w-full">
        {/* ========================================================
            LEFT COLUMN: REALISTIC TELEPHONE DIALER HANDSET (5 COLS)
           ======================================================== */}
        <div className="lg:col-span-5 flex flex-col items-center">
          {/* Handset Outer Chassis */}
          <div className="w-full max-w-[380px] bg-gradient-to-b from-slate-800 to-slate-900 rounded-[38px] p-4 border-4 border-slate-700 shadow-2xl relative flex flex-col">
            {/* Top Speaker Grill & Front Camera Notch */}
            <div className="flex items-center justify-center space-x-2 mb-3">
              <div className="w-3 h-3 rounded-full bg-slate-950 border border-slate-700" />
              <div className="w-16 h-1.5 rounded-full bg-slate-950 border border-slate-700" />
            </div>

            {/* Handset OLED Screen Container */}
            <div className="bg-slate-950 rounded-2xl p-4 border border-slate-800 shadow-inner flex flex-col space-y-3">
              {/* Screen Top Status Bar */}
              <div className="flex items-center justify-between text-[11px] text-slate-400 border-b border-slate-800/80 pb-2">
                <div className="flex items-center space-x-1.5">
                  <Radio className="w-3 h-3 text-emerald-400 animate-pulse" />
                  <span className="font-mono text-emerald-400 font-bold">IVR TOLL-FREE 1800</span>
                  <span className="text-[9px] text-slate-500 font-mono">[{currentMenu.replace('_', ' ')}]</span>
                </div>
                <div className="flex items-center space-x-2">
                  <span className="text-[10px] bg-slate-800 px-1.5 py-0.5 rounded text-slate-300 font-mono">
                    AMR-WB HD
                  </span>
                  <span className="font-mono font-bold text-slate-200">
                    {formatDuration(durationSeconds)}
                  </span>
                </div>
              </div>

              {/* Call Status Display */}
              <div className="text-center py-2">
                <div className="text-xs uppercase tracking-wider font-semibold text-slate-400 mb-0.5">
                  Bol Kisan Agricultural Helpline
                </div>
                <div className="text-lg font-bold text-white font-mono tracking-tight">
                  1800-425-1551
                </div>

                {/* Connection Status Badge */}
                <div className="mt-2 inline-flex items-center space-x-1.5 px-2.5 py-1 rounded-full text-xs font-semibold">
                  {callStatus === 'idle' && (
                    <span className="text-slate-400 bg-slate-800 border border-slate-700 px-2 py-0.5 rounded-full flex items-center space-x-1">
                      <span className="w-2 h-2 rounded-full bg-slate-500" />
                      <span>Ready to Call</span>
                    </span>
                  )}
                  {callStatus === 'ringing' && (
                    <span className="text-amber-300 bg-amber-950/80 border border-amber-600/50 px-2 py-0.5 rounded-full flex items-center space-x-1 animate-pulse">
                      <span className="w-2 h-2 rounded-full bg-amber-400 animate-ping" />
                      <span>Ringing (Connecting HD Voice)...</span>
                    </span>
                  )}
                  {callStatus === 'connected' && (
                    <span className="text-emerald-300 bg-emerald-950/80 border border-emerald-600/50 px-2 py-0.5 rounded-full flex items-center space-x-1">
                      <CheckCircle2 className="w-3.5 h-3.5 text-emerald-400" />
                      <span>Connected • HD Audio</span>
                    </span>
                  )}
                  {callStatus === 'in_call' && (
                    <span className="text-emerald-400 bg-emerald-950/80 border border-emerald-600/60 px-2 py-0.5 rounded-full flex items-center space-x-1.5">
                      <span className="w-2 h-2 rounded-full bg-emerald-400 animate-pulse" />
                      <span className="font-mono font-bold">In Call • {activeLanguage.toUpperCase()}</span>
                    </span>
                  )}
                  {callStatus === 'escalated' && (
                    <span className="text-red-300 bg-red-950/90 border border-red-600/60 px-2 py-0.5 rounded-full flex items-center space-x-1 animate-pulse">
                      <PhoneForwarded className="w-3.5 h-3.5 text-red-400" />
                      <span>Escalated to Officer ({activeTicketId || 'ESC-2026'})</span>
                    </span>
                  )}
                  {callStatus === 'ended' && (
                    <span className="text-slate-400 bg-slate-800 border border-slate-700 px-2 py-0.5 rounded-full flex items-center space-x-1">
                      <PhoneOff className="w-3.5 h-3.5 text-slate-400" />
                      <span>Call Disconnected</span>
                    </span>
                  )}
                </div>
              </div>

              {/* Dynamic Audio Waves Visualizer */}
              <div className="bg-slate-900/90 rounded-xl p-2.5 border border-slate-800 flex items-center justify-between">
                <div className="flex items-center space-x-2">
                  {isIvrSpeaking ? (
                    <Volume2 className="w-4 h-4 text-emerald-400 animate-pulse" />
                  ) : isFarmerRecording ? (
                    <Mic className="w-4 h-4 text-blue-400 animate-bounce" />
                  ) : (
                    <Activity className="w-4 h-4 text-slate-500" />
                  )}
                  <span className="text-xs font-mono text-slate-300">
                    {isIvrSpeaking 
                      ? 'IVR System Speaking...' 
                      : isFarmerRecording 
                      ? 'Listening to Farmer...' 
                      : callStatus === 'in_call' 
                      ? 'Line Open (Awaiting Speech/DTMF)' 
                      : 'Audio Idle'}
                  </span>
                </div>

                {/* Animated Waveform Bars */}
                <div className="flex items-center space-x-1 h-5">
                  {[40, 75, 55, 90, 60, 85, 45, 95].map((height, idx) => (
                    <div
                      key={idx}
                      className={`w-1 rounded-full transition-all duration-150 ${
                        isIvrSpeaking 
                          ? 'bg-emerald-400' 
                          : isFarmerRecording 
                          ? 'bg-blue-400' 
                          : 'bg-slate-700'
                      }`}
                      style={{
                        height: isIvrSpeaking || isFarmerRecording 
                          ? `${Math.max(15, (height * ((idx % 3) + 1)) % 100)}%` 
                          : '20%'
                      }}
                    />
                  ))}
                </div>
              </div>
            </div>

            {/* Interactive DTMF 12-Key Pad */}
            <div className="mt-4 grid grid-cols-3 gap-2.5 px-2">
              {[
                { key: '1', sub: 'తెలుగు (TE)' },
                { key: '2', sub: 'हिन्दी (HI)' },
                { key: '3', sub: 'English (EN)' },
                { key: '4', sub: 'Mandi / Rates' },
                { key: '5', sub: 'Crop Advisory' },
                { key: '6', sub: 'Subsidies' },
                { key: '7', sub: 'Safety' },
                { key: '8', sub: 'CIBRC DB' },
                { key: '9', sub: 'Officer 🚨' },
                { key: '*', sub: 'Main Menu' },
                { key: '0', sub: 'End Call' },
                { key: '#', sub: 'Repeat' },
              ].map(({ key, sub }) => (
                <button
                  key={key}
                  disabled={callStatus !== 'in_call' && callStatus !== 'connected' && callStatus !== 'escalated'}
                  onClick={() => handleDtmfPress(key)}
                  className={`flex flex-col items-center justify-center py-2.5 rounded-xl border transition-all active:scale-95 ${
                    callStatus === 'in_call' || callStatus === 'connected' || callStatus === 'escalated'
                      ? key === '9'
                        ? 'bg-red-950/50 hover:bg-red-900/60 border-red-700/60 text-red-200'
                        : 'bg-slate-800 hover:bg-slate-700 border-slate-700 text-slate-100 hover:border-emerald-500/50 shadow-sm'
                      : 'bg-slate-900/50 border-slate-800 text-slate-600 cursor-not-allowed'
                  }`}
                >
                  <span className="text-xl font-bold font-mono leading-tight">{key}</span>
                  <span className="text-[9px] uppercase tracking-wider text-slate-400 mt-0.5 line-clamp-1">{sub}</span>
                </button>
              ))}
            </div>

            {/* Telephone Call Action Buttons */}
            <div className="mt-4 px-2 grid grid-cols-3 gap-2">
              {/* Speaker Mute/Unmute Toggle */}
              <button
                onClick={() => setSpeakerEnabled(prev => !prev)}
                className={`flex flex-col items-center justify-center py-2 rounded-xl border text-xs font-medium transition-all ${
                  speakerEnabled 
                    ? 'bg-slate-800 border-slate-700 text-emerald-400 hover:bg-slate-750' 
                    : 'bg-slate-900 border-slate-800 text-slate-500'
                }`}
                title={speakerEnabled ? 'Mute IVR Audio' : 'Unmute IVR Audio'}
              >
                {speakerEnabled ? <Volume2 className="w-4 h-4 mb-0.5" /> : <VolumeX className="w-4 h-4 mb-0.5 text-red-400" />}
                <span className="text-[10px]">{speakerEnabled ? 'Speaker ON' : 'Muted'}</span>
              </button>

              {/* Start Call or Hangup Button */}
              {callStatus === 'idle' || callStatus === 'ended' ? (
                <button
                  onClick={handleStartCall}
                  className="bg-emerald-600 hover:bg-emerald-500 text-white rounded-xl py-2 flex flex-col items-center justify-center font-bold shadow-lg shadow-emerald-950 border border-emerald-400/40 transition-all active:scale-95"
                >
                  <PhoneCall className="w-4 h-4 mb-0.5" />
                  <span className="text-[10px]">Start Demo Call</span>
                </button>
              ) : (
                <button
                  onClick={handleEndCall}
                  className="bg-red-600 hover:bg-red-500 text-white rounded-xl py-2 flex flex-col items-center justify-center font-bold shadow-lg shadow-red-950 border border-red-400/40 transition-all active:scale-95 animate-pulse"
                >
                  <PhoneOff className="w-4 h-4 mb-0.5" />
                  <span className="text-[10px]">End Call</span>
                </button>
              )}

              {/* Push-to-Talk / Mic Button */}
              <button
                onClick={handleToggleMic}
                disabled={callStatus !== 'in_call' && callStatus !== 'connected' && callStatus !== 'escalated'}
                className={`flex flex-col items-center justify-center py-2 rounded-xl border text-xs font-medium transition-all ${
                  isFarmerRecording
                    ? 'bg-blue-600 border-blue-400 text-white animate-pulse'
                    : callStatus === 'in_call' || callStatus === 'connected' || callStatus === 'escalated'
                    ? 'bg-slate-800 border-slate-700 text-blue-400 hover:bg-slate-750'
                    : 'bg-slate-900 border-slate-800 text-slate-600 cursor-not-allowed'
                }`}
                title={speechRecSupported ? 'Record speech via microphone' : 'Web Speech API not available in this browser'}
              >
                {isFarmerRecording ? <Mic className="w-4 h-4 mb-0.5" /> : <MicOff className="w-4 h-4 mb-0.5" />}
                <span className="text-[10px]">{isFarmerRecording ? 'Listening...' : 'Push-to-Talk'}</span>
              </button>
            </div>

            {/* Preset Caller Profile Bar */}
            <div className="mt-4 p-2.5 rounded-xl bg-slate-950 border border-slate-800 text-slate-300 text-xs">
              <div className="text-[10px] text-slate-400 uppercase tracking-wider font-semibold mb-1">
                Simulated Inbound Caller
              </div>
              <div className="flex items-center justify-between">
                <div>
                  <div className="font-bold text-white text-xs">{selectedCaller.name}</div>
                  <div className="text-[11px] text-emerald-400 font-mono">{selectedCaller.phone}</div>
                </div>
                <div className="text-right text-[10px] text-slate-400">
                  <div>{selectedCaller.location}</div>
                  <span className="text-emerald-300 font-medium">{selectedCaller.crop}</span>
                </div>
              </div>
            </div>
          </div>
        </div>

        {/* ========================================================
            RIGHT COLUMN: CONVERSATION LEDGER & TEST CONTROLS (7 COLS)
           ======================================================== */}
        <div className="lg:col-span-7 flex flex-col space-y-4">
          {/* Top Panel: Caller Selection & Quick Scenario Launcher */}
          <div className="bg-slate-900 border border-slate-800 rounded-2xl p-4 shadow-sm">
            <div className="flex items-center justify-between mb-3">
              <div className="flex items-center space-x-2">
                <Sparkles className="w-4 h-4 text-emerald-400" />
                <h3 className="text-sm font-bold text-white">6 Predefined Hackathon Test Scenarios</h3>
              </div>
              <span className="text-[11px] text-slate-400">
                Click any scenario to load its simulated voice query
              </span>
            </div>

            {/* Caller Selector Dropdown */}
            <div className="mb-3">
              <label className="block text-[11px] font-semibold text-slate-400 uppercase tracking-wider mb-1">
                Select Simulated Farmer Caller
              </label>
              <select
                value={selectedCaller.phone}
                onChange={e => {
                  const found = PRESET_CALLERS.find(c => c.phone === e.target.value);
                  if (found) {
                    setSelectedCaller(found);
                    setActiveLanguage(found.lang);
                  }
                }}
                disabled={callStatus === 'in_call'}
                className="w-full bg-slate-950 border border-slate-700 rounded-lg px-3 py-1.5 text-xs text-white focus:outline-none focus:border-emerald-500"
              >
                {PRESET_CALLERS.map(c => (
                  <option key={c.phone} value={c.phone}>{c.label}</option>
                ))}
              </select>
            </div>

            {/* 6 Predefined Scenario Buttons */}
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-2">
              {DEMO_SCENARIOS.map(sc => (
                <button
                  key={sc.id}
                  onClick={() => handleSelectScenario(sc)}
                  className={`text-left p-2 rounded-xl border text-xs transition-all ${
                    selectedScenarioId === sc.id
                      ? 'bg-emerald-950/60 border-emerald-500/80 text-white shadow-sm ring-1 ring-emerald-500/30'
                      : 'bg-slate-950 hover:bg-slate-850 border-slate-800 text-slate-300 hover:border-slate-700'
                  }`}
                >
                  <div className="flex items-center justify-between mb-1">
                    <span className="font-semibold text-white text-[11px]">{sc.title}</span>
                    {sc.badge === 'safety' && (
                      <span className="text-[9px] bg-red-950 text-red-300 border border-red-700/50 px-1.5 py-0.2 rounded font-bold">
                        SAFETY
                      </span>
                    )}
                    {sc.badge === 'escalation' && (
                      <span className="text-[9px] bg-amber-950 text-amber-300 border border-amber-700/50 px-1.5 py-0.2 rounded font-bold">
                        MAO ESC
                      </span>
                    )}
                    {sc.badge === 'followup' && (
                      <span className="text-[9px] bg-blue-950 text-blue-300 border border-blue-700/50 px-1.5 py-0.2 rounded font-bold">
                        CONTEXT
                      </span>
                    )}
                  </div>
                  <p className="text-[10px] text-slate-400 line-clamp-1">{sc.spokenText}</p>
                </button>
              ))}
            </div>
          </div>

          {/* Middle Panel: Live Interactive Conversation Transcript */}
          <div className="bg-slate-900 border border-slate-800 rounded-2xl p-4 shadow-sm flex flex-col h-[380px]">
            <div className="flex items-center justify-between border-b border-slate-800 pb-2 mb-3">
              <div className="flex items-center space-x-2">
                <FileText className="w-4 h-4 text-emerald-400" />
                <h3 className="text-sm font-bold text-white">Live Telephony Audio Transcript</h3>
              </div>
              <div className="flex items-center space-x-2 text-[11px] text-slate-400">
                <span>Call ID: <span className="font-mono text-slate-200">{callId || 'None'}</span></span>
                {activeTicketId && (
                  <span className="bg-red-950 text-red-300 border border-red-800 px-2 py-0.5 rounded text-[10px] font-mono font-bold">
                    Ticket: {activeTicketId}
                  </span>
                )}
              </div>
            </div>

            {/* Scrollable Conversation Turns */}
            <div className="flex-1 overflow-y-auto space-y-3 pr-1 text-xs">
              {turns.length === 0 ? (
                <div className="h-full flex flex-col items-center justify-center text-slate-500 space-y-2">
                  <Phone className="w-8 h-8 opacity-40 text-emerald-400" />
                  <p className="text-center text-xs">
                    No active call transcript yet.<br />
                    Click <span className="text-emerald-400 font-semibold">"Start Demo Call"</span> to begin the simulated farmer IVR session.
                  </p>
                </div>
              ) : (
                turns.map(turn => {
                  const isSys = turn.speaker === 'system';
                  return (
                    <div
                      key={turn.id}
                      className={`flex flex-col ${isSys ? 'items-start' : 'items-end'}`}
                    >
                      {/* Speaker Badge */}
                      <div className="flex items-center space-x-1.5 mb-1 text-[10px] text-slate-400">
                        {isSys ? (
                          <>
                            <Bot className="w-3 h-3 text-emerald-400" />
                            <span className="font-semibold text-emerald-400">Bol Kisan IVR</span>
                            {turn.language && (
                              <span className="uppercase text-[9px] bg-slate-800 px-1 rounded text-slate-300 font-mono">
                                {turn.language}
                              </span>
                            )}
                          </>
                        ) : (
                          <>
                            <span className="font-semibold text-blue-400">{selectedCaller.name}</span>
                            <User className="w-3 h-3 text-blue-400" />
                          </>
                        )}
                        <span className="text-slate-500 font-mono">{turn.timestamp}</span>
                      </div>

                      {/* Message Bubble */}
                      <div
                        className={`p-3 rounded-2xl max-w-[85%] leading-relaxed ${
                          isSys
                            ? turn.is_safety_flagged
                              ? 'bg-red-950/80 border border-red-700/80 text-red-100 rounded-tl-sm'
                              : turn.ticket_escalated
                              ? 'bg-amber-950/80 border border-amber-700/80 text-amber-100 rounded-tl-sm'
                              : 'bg-slate-850 border border-slate-750 text-slate-100 rounded-tl-sm'
                            : turn.dtmf_key
                            ? 'bg-slate-800 border border-slate-700 text-emerald-300 font-mono rounded-tr-sm'
                            : 'bg-blue-950/80 border border-blue-800/70 text-blue-100 rounded-tr-sm'
                        }`}
                      >
                        {/* Safety Gate Warning Pill */}
                        {turn.is_safety_flagged && (
                          <div className="flex items-center space-x-1 text-red-400 font-bold text-[10px] uppercase tracking-wider mb-1">
                            <ShieldAlert className="w-3.5 h-3.5" />
                            <span>CIBRC Safety Gate Interception</span>
                          </div>
                        )}

                        {/* Officer Escalation Pill */}
                        {turn.ticket_escalated && (
                          <div className="flex items-center space-x-1 text-amber-400 font-bold text-[10px] uppercase tracking-wider mb-1">
                            <PhoneForwarded className="w-3.5 h-3.5" />
                            <span>Escalated to Agricultural Officer • {turn.ticket_id}</span>
                          </div>
                        )}

                        <p className="whitespace-pre-wrap">{turn.text}</p>

                        {/* Replay Audio Button */}
                        {isSys && (
                          <button
                            onClick={() => speakIvrPrompt(turn.text, turn.language || activeLanguage)}
                            className="mt-2 inline-flex items-center space-x-1 text-[10px] text-emerald-400 hover:text-emerald-300 font-semibold transition-colors"
                          >
                            <Play className="w-2.5 h-2.5 fill-current" />
                            <span>Replay Audio</span>
                          </button>
                        )}
                      </div>
                    </div>
                  );
                })
              )}
              <div ref={transcriptBottomRef} />
            </div>
          </div>

          {/* Bottom Panel: Simulated Speech Input & Error Simulation */}
          <div className="bg-slate-900 border border-slate-800 rounded-2xl p-4 shadow-sm flex flex-col space-y-3">
            {/* Input Label clearly stating simulated speech transcript input */}
            <div className="flex items-center justify-between">
              <label className="text-xs font-bold text-white flex items-center space-x-1.5">
                <Mic className="w-3.5 h-3.5 text-emerald-400" />
                <span>Simulated Speech Transcript Input (Mic Fallback / Testing)</span>
              </label>
              <span className="text-[10px] text-amber-400/90 font-mono">
                Labeled: Simulated Input (No phone call)
              </span>
            </div>

            {/* Input Area */}
            <div className="flex space-x-2">
              <textarea
                rows={2}
                value={simulatedSpeechText}
                onChange={e => setSimulatedSpeechText(e.target.value)}
                placeholder="Type question to simulate spoken farmer input (e.g. Cotton pink bollworm query)..."
                disabled={callStatus !== 'in_call' && callStatus !== 'connected' && callStatus !== 'escalated'}
                className="flex-1 bg-slate-950 border border-slate-700 rounded-xl px-3 py-2 text-xs text-white placeholder-slate-500 focus:outline-none focus:border-emerald-500 disabled:opacity-50 resize-none font-sans"
              />
              <button
                onClick={() => handleSendVoiceQuery(simulatedSpeechText, false)}
                disabled={
                  !simulatedSpeechText.trim() ||
                  isProcessing ||
                  (callStatus !== 'in_call' && callStatus !== 'connected' && callStatus !== 'escalated')
                }
                className="bg-emerald-600 hover:bg-emerald-500 disabled:bg-slate-800 text-white font-semibold px-4 rounded-xl flex items-center justify-center transition-all disabled:text-slate-600 shadow-md"
                title="Send Simulated Spoken Question to IVR Engine"
              >
                <Send className="w-4 h-4" />
              </button>
            </div>

            {/* Error Simulation Injection Triggers */}
            <div className="pt-2 border-t border-slate-800 flex flex-wrap items-center justify-between gap-2">
              <span className="text-[11px] font-semibold text-slate-400 uppercase tracking-wider flex items-center space-x-1">
                <AlertCircle className="w-3.5 h-3.5 text-amber-400" />
                <span>Error Simulations:</span>
              </span>

              <div className="flex flex-wrap gap-2">
                <button
                  onClick={() => handleSimulateError('speech_recognition_failure')}
                  disabled={callStatus !== 'in_call' && callStatus !== 'connected' && callStatus !== 'escalated'}
                  className="bg-slate-800 hover:bg-slate-700 disabled:bg-slate-900 text-slate-300 disabled:text-slate-600 px-2.5 py-1 rounded-lg text-[10px] font-semibold border border-slate-700 hover:border-amber-500/50 transition-colors"
                >
                  ASR Garbled Audio (Confidence: 0.14)
                </button>
                <button
                  onClick={() => handleSimulateError('timeout_silence')}
                  disabled={callStatus !== 'in_call' && callStatus !== 'connected' && callStatus !== 'escalated'}
                  className="bg-slate-800 hover:bg-slate-700 disabled:bg-slate-900 text-slate-300 disabled:text-slate-600 px-2.5 py-1 rounded-lg text-[10px] font-semibold border border-slate-700 hover:border-amber-500/50 transition-colors"
                >
                  10s Silence Timeout
                </button>
                <button
                  onClick={() => handleSimulateError('backend_failure')}
                  disabled={callStatus !== 'in_call' && callStatus !== 'connected' && callStatus !== 'escalated'}
                  className="bg-slate-800 hover:bg-slate-700 disabled:bg-slate-900 text-red-300 disabled:text-slate-600 px-2.5 py-1 rounded-lg text-[10px] font-semibold border border-slate-700 hover:border-red-500/50 transition-colors"
                >
                  503 Backend Failure
                </button>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};
