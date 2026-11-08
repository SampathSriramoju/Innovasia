import React, { useState, useEffect } from 'react';
import { 
  Play, 
  Pause, 
  Volume2, 
  VolumeX, 
  Copy, 
  Check, 
  Radio, 
  Sparkles, 
  FastForward, 
  Rewind,
  Music
} from 'lucide-react';
import { SyncedTranscript } from '../types';

interface AudioWaveformPlayerProps {
  audioUrl?: string;
  durationSeconds?: number;
  transcript?: SyncedTranscript;
  farmerName?: string;
}

// Generate realistic pseudo-random frequency heights for the 48 waveform bars
const GENERATE_WAVEFORM_BARS = (count = 48): number[] => {
  const seed = [35, 60, 45, 80, 95, 70, 50, 40, 85, 100, 75, 60, 45, 90, 80, 65, 50, 40, 70, 95, 85, 60, 40, 55, 75, 90, 100, 85, 65, 45, 70, 80, 60, 40, 50, 75, 85, 65, 45, 35, 55, 70, 60, 40, 50, 65, 45, 30];
  if (count <= seed.length) return seed.slice(0, count);
  return Array.from({ length: count }, (_, i) => 25 + Math.round(Math.abs(Math.sin(i * 0.4)) * 75));
};

const WAVEFORM_BARS = GENERATE_WAVEFORM_BARS(48);

export const AudioWaveformPlayer: React.FC<AudioWaveformPlayerProps> = ({
  audioUrl,
  durationSeconds = 18,
  transcript,
  farmerName = 'Farmer'
}) => {
  const [isPlaying, setIsPlaying] = useState(false);
  const [currentTimeMs, setCurrentTimeMs] = useState(0);
  const [playbackRate, setPlaybackRate] = useState<number>(1.0);
  const [isMuted, setIsMuted] = useState(false);
  const [activeTab, setActiveTab] = useState<'bilingual' | 'original' | 'english'>('bilingual');
  const [copied, setCopied] = useState(false);
  const [hoveredBarIndex, setHoveredBarIndex] = useState<number | null>(null);

  const totalDurationMs = durationSeconds * 1000;

  // Playback timer loop for smooth animation
  useEffect(() => {
    let interval: ReturnType<typeof setInterval>;

    if (isPlaying) {
      interval = setInterval(() => {
        setCurrentTimeMs(prev => {
          const next = prev + 100 * playbackRate;
          if (next >= totalDurationMs) {
            setIsPlaying(false);
            return totalDurationMs;
          }
          return next;
        });
      }, 100);
    }

    return () => {
      if (interval) clearInterval(interval);
    };
  }, [isPlaying, playbackRate, totalDurationMs]);

  const togglePlay = () => {
    if (currentTimeMs >= totalDurationMs) {
      setCurrentTimeMs(0);
    }
    setIsPlaying(!isPlaying);
  };

  const handleSeek = (percentage: number) => {
    const targetMs = Math.round(percentage * totalDurationMs);
    setCurrentTimeMs(targetMs);
  };

  const handleSkip = (seconds: number) => {
    setCurrentTimeMs(prev => {
      const next = prev + seconds * 1000;
      return Math.max(0, Math.min(next, totalDurationMs));
    });
  };

  const handleRateChange = () => {
    const rates = [1.0, 1.25, 1.5, 0.75];
    const currentIndex = rates.indexOf(playbackRate);
    const nextRate = rates[(currentIndex + 1) % rates.length];
    setPlaybackRate(nextRate);
  };

  const copyTranscript = () => {
    if (!transcript) return;
    const textToCopy = `${transcript.original_text}\n\nTranslation: ${transcript.english_translation}`;
    navigator.clipboard.writeText(textToCopy);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  const formatTime = (ms: number) => {
    const totalSeconds = Math.floor(ms / 1000);
    const mins = Math.floor(totalSeconds / 60);
    const secs = totalSeconds % 60;
    return `${mins}:${secs.toString().padStart(2, '0')}`;
  };

  const progressFraction = totalDurationMs > 0 ? currentTimeMs / totalDurationMs : 0;
  const activeBarIndex = Math.floor(progressFraction * WAVEFORM_BARS.length);

  return (
    <div className="bg-white rounded-2xl border border-slate-200 shadow-sm p-5 space-y-4">
      {/* Header with audio metadata & language pill */}
      <div className="flex flex-wrap items-center justify-between gap-2 pb-3 border-b border-slate-100">
        <div className="flex items-center space-x-2.5">
          <div className="p-2 bg-forest-100 text-forest-800 rounded-xl">
            <Radio className="w-4 h-4 animate-pulse text-forest-700" />
          </div>
          <div>
            <h4 className="text-xs font-bold uppercase tracking-wider text-slate-800">
              Voice Recording: {farmerName}
            </h4>
            <p className="text-[11px] text-slate-500">
              Dialect: <span className="font-semibold text-forest-700">{transcript?.language_label || 'Warangal Rural Telugu'}</span>
              {audioUrl && <span className="ml-2 font-mono text-[10px] text-slate-400">Opus 48kbps</span>}
            </p>
          </div>
        </div>

        <div className="flex items-center space-x-2 text-xs">
          <span className="bg-forest-50 text-forest-800 px-2.5 py-1 rounded-lg font-mono font-bold border border-forest-200">
            {formatTime(currentTimeMs)} / {formatTime(totalDurationMs)}
          </span>

          <button
            type="button"
            onClick={copyTranscript}
            title="Copy Bilingual Transcript"
            className="flex items-center space-x-1 px-2.5 py-1 rounded-lg bg-slate-100 hover:bg-slate-200 text-slate-700 transition-colors font-medium"
          >
            {copied ? <Check className="w-3.5 h-3.5 text-forest-700" /> : <Copy className="w-3.5 h-3.5" />}
            <span className="hidden sm:inline">{copied ? 'Copied' : 'Copy'}</span>
          </button>
        </div>
      </div>

      {/* Interactive Audio Waveform Visualization */}
      <div className="bg-slate-900 rounded-2xl p-4 sm:p-5 relative select-none shadow-inner overflow-hidden">
        {/* Ambient glow behind playing waveform */}
        {isPlaying && (
          <div className="absolute inset-0 bg-gradient-to-r from-emerald-600/10 via-emerald-400/20 to-teal-600/10 animate-pulse pointer-events-none" />
        )}

        <div 
          className="relative h-20 sm:h-24 flex items-end justify-between gap-1 cursor-pointer"
          onClick={(e) => {
            const rect = e.currentTarget.getBoundingClientRect();
            const clickFraction = Math.max(0, Math.min(1, (e.clientX - rect.left) / rect.width));
            handleSeek(clickFraction);
          }}
        >
          {WAVEFORM_BARS.map((heightPercent, index) => {
            const isPlayed = index <= activeBarIndex;
            const isHovered = hoveredBarIndex !== null && index <= hoveredBarIndex;
            const isCurrent = index === activeBarIndex;

            return (
              <div
                key={index}
                onMouseEnter={() => setHoveredBarIndex(index)}
                onMouseLeave={() => setHoveredBarIndex(null)}
                style={{ height: `${heightPercent}%` }}
                className={`flex-1 rounded-full transition-all duration-75 relative group ${
                  isPlayed 
                    ? 'bg-emerald-400 shadow-[0_0_8px_rgba(52,211,153,0.6)]' 
                    : isHovered
                    ? 'bg-slate-500'
                    : 'bg-slate-700'
                } ${isCurrent && isPlaying ? 'scale-y-110 bg-white' : ''}`}
              >
                {/* Micro tooltip on waveform hover */}
                <div className="absolute bottom-full left-1/2 -translate-x-1/2 mb-2 hidden group-hover:block bg-slate-950 text-white text-[10px] font-mono px-1.5 py-0.5 rounded shadow pointer-events-none z-30">
                  {formatTime((index / WAVEFORM_BARS.length) * totalDurationMs)}
                </div>
              </div>
            );
          })}
        </div>

        {/* Playhead Scrubber Track */}
        <div className="relative mt-2 h-1.5 bg-slate-800 rounded-full overflow-hidden">
          <div 
            className="h-full bg-emerald-400 rounded-full transition-all duration-75"
            style={{ width: `${progressFraction * 100}%` }}
          />
        </div>

        {/* Playback Controls Row */}
        <div className="mt-4 flex items-center justify-between text-white text-xs">
          <div className="flex items-center space-x-2">
            <button
              type="button"
              onClick={() => handleSkip(-5)}
              title="Rewind 5 seconds"
              className="p-2 rounded-xl bg-slate-800 hover:bg-slate-700 text-slate-300 hover:text-white transition-colors"
            >
              <Rewind className="w-4 h-4" />
            </button>

            <button
              type="button"
              onClick={togglePlay}
              className="flex items-center justify-center w-11 h-11 rounded-xl bg-emerald-500 hover:bg-emerald-400 text-slate-950 font-bold transition-transform active:scale-95 shadow-lg shadow-emerald-500/30"
            >
              {isPlaying ? <Pause className="w-5 h-5 fill-current" /> : <Play className="w-5 h-5 fill-current ml-0.5" />}
            </button>

            <button
              type="button"
              onClick={() => handleSkip(5)}
              title="Forward 5 seconds"
              className="p-2 rounded-xl bg-slate-800 hover:bg-slate-700 text-slate-300 hover:text-white transition-colors"
            >
              <FastForward className="w-4 h-4" />
            </button>

            {audioUrl && (
              <span className="hidden md:flex items-center space-x-1 text-[11px] text-slate-400 ml-2">
                <Music className="w-3.5 h-3.5 text-emerald-400" />
                <span>Audio Stream Synchronized</span>
              </span>
            )}
          </div>

          <div className="flex items-center space-x-2">
            <button
              type="button"
              onClick={handleRateChange}
              title="Toggle Playback Speed"
              className="px-2.5 py-1.5 rounded-xl bg-slate-800 hover:bg-slate-700 font-mono font-bold text-emerald-400 text-xs transition-colors"
            >
              {playbackRate}x
            </button>

            <button
              type="button"
              onClick={() => setIsMuted(!isMuted)}
              title={isMuted ? 'Unmute' : 'Mute'}
              className="p-2 rounded-xl bg-slate-800 hover:bg-slate-700 text-slate-300 hover:text-white transition-colors"
            >
              {isMuted ? <VolumeX className="w-4 h-4 text-red-400" /> : <Volume2 className="w-4 h-4" />}
            </button>
          </div>
        </div>
      </div>

      {/* Synced Bilingual Transcript Section */}
      {transcript && (
        <div className="space-y-3 pt-1">
          {/* View Tabs */}
          <div className="flex items-center justify-between">
            <div className="flex items-center space-x-1 bg-slate-100 p-1 rounded-xl text-xs font-semibold">
              <button
                type="button"
                onClick={() => setActiveTab('bilingual')}
                className={`px-3 py-1 rounded-lg transition-all ${
                  activeTab === 'bilingual' ? 'bg-white text-forest-900 shadow-xs' : 'text-slate-600 hover:text-slate-900'
                }`}
              >
                Bilingual View
              </button>
              <button
                type="button"
                onClick={() => setActiveTab('original')}
                className={`px-3 py-1 rounded-lg transition-all ${
                  activeTab === 'original' ? 'bg-white text-forest-900 shadow-xs' : 'text-slate-600 hover:text-slate-900'
                }`}
              >
                Telugu (తెలుగు)
              </button>
              <button
                type="button"
                onClick={() => setActiveTab('english')}
                className={`px-3 py-1 rounded-lg transition-all ${
                  activeTab === 'english' ? 'bg-white text-forest-900 shadow-xs' : 'text-slate-600 hover:text-slate-900'
                }`}
              >
                English Gloss
              </button>
            </div>

            <span className="text-[11px] text-slate-400 flex items-center space-x-1">
              <Sparkles className="w-3 h-3 text-harvest-500" />
              <span>Realtime Synced</span>
            </span>
          </div>

          {/* Transcript Content Box */}
          <div className="bg-forest-50/40 border border-forest-200/70 rounded-2xl p-4 space-y-3">
            {/* Bilingual synced segments */}
            {activeTab === 'bilingual' && (
              <div className="space-y-2">
                {transcript.segments ? (
                  transcript.segments.map((seg, idx) => {
                    const isSegActive = currentTimeMs >= seg.start_ms && currentTimeMs < seg.end_ms;
                    return (
                      <div
                        key={idx}
                        onClick={() => setCurrentTimeMs(seg.start_ms)}
                        className={`p-2.5 rounded-xl cursor-pointer transition-all ${
                          isSegActive
                            ? 'bg-forest-100/90 border border-forest-300 ring-2 ring-forest-400/20'
                            : 'hover:bg-forest-50/80 border border-transparent'
                        }`}
                      >
                        <p className={`text-sm font-medium ${isSegActive ? 'text-forest-950 font-bold' : 'text-slate-800'}`}>
                          {seg.text_te || seg.text_hi}
                        </p>
                        <p className={`text-xs mt-0.5 ${isSegActive ? 'text-forest-800 font-semibold' : 'text-slate-500'}`}>
                          ↳ {seg.text_en}
                        </p>
                      </div>
                    );
                  })
                ) : (
                  <div>
                    <p className="text-base text-slate-900 font-medium">"{transcript.original_text}"</p>
                    <p className="text-xs text-slate-600 mt-2 italic">↳ {transcript.english_translation}</p>
                  </div>
                )}
              </div>
            )}

            {/* Telugu / Original only */}
            {activeTab === 'original' && (
              <div className="space-y-2">
                <p className="text-base text-slate-900 font-medium leading-relaxed">
                  "{transcript.original_text}"
                </p>
                {transcript.phonetic_gloss && (
                  <p className="text-xs text-slate-500 font-mono italic">
                    Phonetic: {transcript.phonetic_gloss}
                  </p>
                )}
              </div>
            )}

            {/* English gloss only */}
            {activeTab === 'english' && (
              <div>
                <p className="text-sm font-semibold text-slate-900 leading-relaxed">
                  "{transcript.english_translation}"
                </p>
                <p className="text-xs text-slate-400 mt-1">
                  Translated automatically via Bol Kisan Indian Agricultural Language Engine.
                </p>
              </div>
            )}
          </div>
        </div>
      )}
    </div>
  );
};
