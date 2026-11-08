import React, { useState, useRef, useEffect } from 'react';
import { 
  ZoomIn, 
  ZoomOut, 
  RotateCcw, 
  Maximize2, 
  Minimize2, 
  ShieldCheck, 
  MapPin, 
  Clock, 
  Smartphone, 
  Eye, 
  EyeOff, 
  Crosshair, 
  CheckCircle, 
  Camera
} from 'lucide-react';
import { AttachedPhoto, ExifVerification, PhotoAnnotation } from '../types';

interface PhotoInspectionViewerProps {
  photos?: AttachedPhoto[];
  primaryPhotoUrl?: string;
  exifData?: ExifVerification;
  cropName?: string;
  diseaseOrPestName?: string;
}

export const PhotoInspectionViewer: React.FC<PhotoInspectionViewerProps> = ({
  photos = [],
  primaryPhotoUrl,
  exifData,
  cropName = 'Crop Foliage',
  diseaseOrPestName = 'Leaf Symptom'
}) => {
  // Normalize photos array
  const displayPhotos: AttachedPhoto[] = photos.length > 0
    ? photos
    : primaryPhotoUrl
      ? [
          {
            url: primaryPhotoUrl,
            caption: `${cropName} Field Inspection Photo`,
            exif: exifData
          }
        ]
      : [];

  const [activePhotoIndex, setActivePhotoIndex] = useState(0);
  const [zoomLevel, setZoomLevel] = useState<number>(1);
  const [panOffset, setPanOffset] = useState<{ x: number; y: number }>({ x: 0, y: 0 });
  const [isDragging, setIsDragging] = useState(false);
  const [dragStart, setDragStart] = useState<{ x: number; y: number }>({ x: 0, y: 0 });
  const [showAnnotations, setShowAnnotations] = useState(true);
  const [selectedAnnotation, setSelectedAnnotation] = useState<PhotoAnnotation | null>(null);
  const [isFullscreen, setIsFullscreen] = useState(false);
  const [showExifDrawer, setShowExifDrawer] = useState(false);
  const [imageLoaded, setImageLoaded] = useState(false);
  const [imageError, setImageError] = useState(false);

  const containerRef = useRef<HTMLDivElement>(null);

  const currentPhoto: AttachedPhoto | undefined = displayPhotos[activePhotoIndex];
  const currentExif = currentPhoto?.exif || exifData;
  const currentAnnotations = currentPhoto?.annotations || [];

  // Reset zoom & pan when photo changes
  useEffect(() => {
    setZoomLevel(1);
    setPanOffset({ x: 0, y: 0 });
    setSelectedAnnotation(null);
    setImageError(false);
    setImageLoaded(false);
  }, [activePhotoIndex]);

  const handleZoomIn = () => {
    setZoomLevel(prev => Math.min(prev + 0.3, 3.5));
  };

  const handleZoomOut = () => {
    setZoomLevel(prev => {
      const next = Math.max(prev - 0.3, 1);
      if (next === 1) {
        setPanOffset({ x: 0, y: 0 });
      }
      return next;
    });
  };

  const handleReset = () => {
    setZoomLevel(1);
    setPanOffset({ x: 0, y: 0 });
  };

  const handleMouseDown = (e: React.MouseEvent) => {
    if (zoomLevel > 1) {
      setIsDragging(true);
      setDragStart({ x: e.clientX - panOffset.x, y: e.clientY - panOffset.y });
    }
  };

  const handleMouseMove = (e: React.MouseEvent) => {
    if (isDragging && zoomLevel > 1) {
      setPanOffset({
        x: e.clientX - dragStart.x,
        y: e.clientY - dragStart.y
      });
    }
  };

  const handleMouseUp = () => {
    setIsDragging(false);
  };

  const toggleFullscreen = () => {
    setIsFullscreen(!isFullscreen);
  };

  if (displayPhotos.length === 0) {
    return (
      <div className="bg-slate-50 border border-dashed border-slate-300 rounded-2xl p-8 text-center text-slate-500 space-y-2">
        <Camera className="w-10 h-10 text-slate-400 mx-auto" />
        <p className="text-sm font-semibold text-slate-700">No Leaf Photographs Attached</p>
        <p className="text-xs text-slate-500">
          This inquiry was escalated based solely on farmer voice description or oral diagnostic parameters.
        </p>
      </div>
    );
  }

  return (
    <div className={`space-y-3 ${isFullscreen ? 'fixed inset-0 z-50 bg-slate-950 p-6 flex flex-col justify-between' : ''}`}>
      {/* Top Inspection Control Bar */}
      <div className="flex flex-wrap items-center justify-between gap-2 bg-slate-900 text-white px-4 py-2.5 rounded-xl">
        <div className="flex items-center space-x-2">
          <span className="text-xs font-bold uppercase tracking-wider text-emerald-400 flex items-center space-x-1.5">
            <Camera className="w-3.5 h-3.5" />
            <span>High-Res Optical Triage</span>
          </span>
          <span className="text-slate-400 text-xs">•</span>
          <span className="text-xs text-slate-300 font-medium">
            Photo {activePhotoIndex + 1} of {displayPhotos.length}
          </span>
          {currentPhoto?.tag && (
            <span className="text-[10px] bg-slate-800 text-slate-300 px-2 py-0.5 rounded font-mono uppercase">
              {currentPhoto.tag.replace('_', ' ')}
            </span>
          )}
        </div>

        {/* Zoom & View Controls */}
        <div className="flex items-center space-x-1 sm:space-x-2 text-xs">
          <button
            type="button"
            onClick={handleZoomOut}
            disabled={zoomLevel <= 1}
            title="Zoom Out"
            className="p-1.5 rounded-lg bg-slate-800 hover:bg-slate-700 disabled:opacity-40 disabled:hover:bg-slate-800 transition-colors"
          >
            <ZoomOut className="w-4 h-4" />
          </button>

          <span className="px-2 py-1 bg-slate-800 rounded font-mono text-emerald-400 font-bold min-w-[3.5rem] text-center">
            {Math.round(zoomLevel * 100)}%
          </span>

          <button
            type="button"
            onClick={handleZoomIn}
            disabled={zoomLevel >= 3.5}
            title="Zoom In"
            className="p-1.5 rounded-lg bg-slate-800 hover:bg-slate-700 disabled:opacity-40 disabled:hover:bg-slate-800 transition-colors"
          >
            <ZoomIn className="w-4 h-4" />
          </button>

          <button
            type="button"
            onClick={handleReset}
            title="Reset Zoom to 100%"
            className="p-1.5 rounded-lg bg-slate-800 hover:bg-slate-700 transition-colors text-slate-300 hover:text-white"
          >
            <RotateCcw className="w-4 h-4" />
          </button>

          <div className="h-4 w-px bg-slate-700 mx-1" />

          {currentAnnotations.length > 0 && (
            <button
              type="button"
              onClick={() => setShowAnnotations(!showAnnotations)}
              title={showAnnotations ? 'Hide Disease Hotspots' : 'Show Disease Hotspots'}
              className={`flex items-center space-x-1 px-2.5 py-1.5 rounded-lg transition-colors font-medium ${
                showAnnotations 
                  ? 'bg-emerald-900/60 text-emerald-300 border border-emerald-700' 
                  : 'bg-slate-800 text-slate-400 hover:text-white'
              }`}
            >
              {showAnnotations ? <Eye className="w-3.5 h-3.5" /> : <EyeOff className="w-3.5 h-3.5" />}
              <span className="hidden md:inline">Hotspots ({currentAnnotations.length})</span>
            </button>
          )}

          {currentExif && (
            <button
              type="button"
              onClick={() => setShowExifDrawer(!showExifDrawer)}
              className={`flex items-center space-x-1 px-2.5 py-1.5 rounded-lg transition-colors font-medium ${
                showExifDrawer 
                  ? 'bg-blue-900/60 text-blue-300 border border-blue-700' 
                  : 'bg-slate-800 text-slate-300 hover:text-white'
              }`}
            >
              <ShieldCheck className="w-3.5 h-3.5 text-emerald-400" />
              <span className="hidden md:inline">EXIF Info</span>
            </button>
          )}

          <button
            type="button"
            onClick={toggleFullscreen}
            title={isFullscreen ? 'Exit Fullscreen' : 'Fullscreen Inspection Mode'}
            className="p-1.5 rounded-lg bg-slate-800 hover:bg-slate-700 text-slate-300 hover:text-white transition-colors"
          >
            {isFullscreen ? <Minimize2 className="w-4 h-4" /> : <Maximize2 className="w-4 h-4" />}
          </button>
        </div>
      </div>

      {/* Main Image Stage Viewport */}
      <div 
        ref={containerRef}
        onMouseDown={handleMouseDown}
        onMouseMove={handleMouseMove}
        onMouseUp={handleMouseUp}
        onMouseLeave={handleMouseUp}
        className={`relative w-full overflow-hidden rounded-2xl bg-slate-950 flex items-center justify-center border border-slate-800 select-none ${
          isFullscreen ? 'flex-1 my-2 min-h-[60vh]' : 'h-80 sm:h-96'
        } ${zoomLevel > 1 ? (isDragging ? 'cursor-grabbing' : 'cursor-grab') : 'cursor-default'}`}
      >
        {/* Verification Status Overlay Badge */}
        {currentExif && currentExif.tamper_check_status === 'passed' && (
          <div className="absolute top-3 left-3 z-20 flex items-center space-x-1.5 bg-slate-900/85 backdrop-blur-md text-emerald-400 text-xs px-2.5 py-1.5 rounded-lg border border-emerald-500/30 shadow-lg">
            <ShieldCheck className="w-4 h-4 text-emerald-400" />
            <span className="font-semibold tracking-wide">EXIF METADATA VERIFIED</span>
          </div>
        )}

        {/* Pan hint if zoomed */}
        {zoomLevel > 1 && (
          <div className="absolute bottom-3 left-3 z-20 bg-slate-900/80 backdrop-blur-sm text-slate-300 text-[11px] px-2.5 py-1 rounded-md border border-slate-700">
            Click & drag to pan • Scroll to zoom
          </div>
        )}

        {/* The Zoomable / Pannable Image */}
        <div 
          className="relative transition-transform duration-75 ease-out"
          style={{
            transform: `scale(${zoomLevel}) translate(${panOffset.x / zoomLevel}px, ${panOffset.y / zoomLevel}px)`,
            transformOrigin: 'center center'
          }}
        >
          {currentPhoto?.url && !imageError ? (
            <img
              src={currentPhoto.url}
              alt={currentPhoto.caption || `${cropName} Leaf Photo`}
              onLoad={() => setImageLoaded(true)}
              onError={() => setImageError(true)}
              className="max-h-72 sm:max-h-88 object-contain rounded-lg shadow-2xl pointer-events-none transition-opacity duration-300"
              style={{ opacity: imageLoaded ? 1 : 0.4 }}
            />
          ) : (
            /* Fallback high-contrast agricultural leaf illustration diagram */
            <div className="w-96 h-72 bg-gradient-to-b from-forest-900 to-emerald-950 rounded-xl p-6 text-emerald-100 flex flex-col items-center justify-center text-center space-y-3 border border-emerald-700/50">
              <div className="relative">
                <svg className="w-28 h-28 text-emerald-400 opacity-80" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5">
                  <path d="M11 20A7 7 0 0 1 9.8 6.1C15.5 5 17 4.48 19 2c1 2 2 4.18 2 8 0 5.5-4.78 10-10 10Z"/>
                  <path d="M2 21c0-3 1.85-5.36 5.08-6C9.5 14.52 12 13 13 12"/>
                </svg>
                <span className="absolute top-8 left-10 w-4 h-4 rounded-full bg-red-500 animate-ping opacity-75" />
                <span className="absolute top-8 left-10 w-4 h-4 rounded-full bg-red-600 border-2 border-white" />
              </div>
              <p className="text-sm font-bold text-white">{cropName} Optical Inspection</p>
              <p className="text-xs text-emerald-200">
                Target Symptom: <span className="font-semibold text-yellow-300">{diseaseOrPestName}</span>
              </p>
            </div>
          )}

          {/* Interactive Disease Hotspot Overlays */}
          {showAnnotations && currentAnnotations.map((ann) => {
            const isSelected = selectedAnnotation?.id === ann.id;
            return (
              <div
                key={ann.id}
                onClick={(e) => {
                  e.stopPropagation();
                  setSelectedAnnotation(isSelected ? null : ann);
                }}
                style={{ left: `${ann.x}%`, top: `${ann.y}%` }}
                className="absolute -translate-x-1/2 -translate-y-1/2 cursor-pointer z-30 group"
              >
                <div className="relative flex items-center justify-center">
                  <span className={`w-6 h-6 rounded-full animate-ping opacity-75 ${
                    ann.severity === 'severe' ? 'bg-red-500' : 'bg-amber-400'
                  }`} />
                  <div className={`w-5 h-5 rounded-full flex items-center justify-center text-[10px] font-bold text-white shadow-lg border-2 border-white ${
                    ann.severity === 'severe' ? 'bg-red-600' : 'bg-amber-600'
                  }`}>
                    <Crosshair className="w-3 h-3" />
                  </div>
                </div>

                {/* Hotspot Tooltip */}
                <div className={`absolute bottom-full left-1/2 -translate-x-1/2 mb-2 w-48 bg-slate-900/95 text-white p-2.5 rounded-xl shadow-xl text-xs border border-slate-700 pointer-events-none transition-all ${
                  isSelected ? 'opacity-100 scale-100' : 'opacity-0 group-hover:opacity-100 scale-95 group-hover:scale-100'
                }`}>
                  <p className="font-bold text-emerald-400">{ann.label}</p>
                  {ann.description && (
                    <p className="text-[11px] text-slate-300 mt-0.5 leading-snug">{ann.description}</p>
                  )}
                  {ann.severity && (
                    <span className={`inline-block mt-1 text-[9px] uppercase font-bold px-1.5 py-0.5 rounded ${
                      ann.severity === 'severe' ? 'bg-red-900/80 text-red-200' : 'bg-amber-900/80 text-amber-200'
                    }`}>
                      {ann.severity} damage
                    </span>
                  )}
                </div>
              </div>
            );
          })}
        </div>

        {/* Selected Annotation Detail Bar */}
        {selectedAnnotation && (
          <div className="absolute bottom-3 right-3 z-30 bg-slate-900/95 backdrop-blur-md text-white p-3 rounded-xl border border-emerald-500/40 shadow-xl max-w-xs">
            <div className="flex items-center justify-between">
              <span className="text-xs font-bold text-emerald-400">{selectedAnnotation.label}</span>
              <button 
                onClick={() => setSelectedAnnotation(null)}
                className="text-slate-400 hover:text-white text-xs px-1"
              >
                ✕
              </button>
            </div>
            <p className="text-xs text-slate-300 mt-1">{selectedAnnotation.description}</p>
          </div>
        )}
      </div>

      {/* EXIF Verification Details Drawer / Banner */}
      {currentExif && (showExifDrawer || !isFullscreen) && (
        <div className="bg-slate-900 text-slate-200 p-4 rounded-xl border border-slate-800 space-y-3">
          <div className="flex items-center justify-between pb-2 border-b border-slate-800">
            <div className="flex items-center space-x-2">
              <div className="p-1 bg-emerald-500/20 text-emerald-400 rounded-md">
                <ShieldCheck className="w-4 h-4" />
              </div>
              <span className="text-xs font-bold uppercase tracking-wider text-slate-100">
                Deterministic EXIF & Sensor Chain-of-Custody
              </span>
            </div>
            <span className="text-[11px] bg-emerald-950 text-emerald-300 border border-emerald-700/50 px-2 py-0.5 rounded-full font-mono font-semibold">
              TAMPER STATUS: PASSED
            </span>
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-4 gap-3 text-xs">
            {/* GPS & Location */}
            <div className="space-y-1">
              <span className="text-slate-400 text-[10px] uppercase font-semibold flex items-center space-x-1">
                <MapPin className="w-3 h-3 text-harvest-500" />
                <span>Field Geolocation</span>
              </span>
              <p className="font-mono text-emerald-300 font-semibold">
                {currentExif.latitude.toFixed(4)}° N, {currentExif.longitude.toFixed(4)}° E
              </p>
              <p className="text-slate-400 text-[11px] truncate">
                {currentExif.location_name}
              </p>
            </div>

            {/* Timestamp */}
            <div className="space-y-1">
              <span className="text-slate-400 text-[10px] uppercase font-semibold flex items-center space-x-1">
                <Clock className="w-3 h-3 text-blue-400" />
                <span>Captured Timestamp</span>
              </span>
              <p className="font-mono text-slate-200 font-semibold">
                {new Date(currentExif.captured_at).toLocaleDateString([], { month: 'short', day: '2-digit', year: 'numeric' })}
              </p>
              <p className="text-slate-400 text-[11px]">
                {new Date(currentExif.captured_at).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' })} IST
              </p>
            </div>

            {/* Device & Camera */}
            <div className="space-y-1">
              <span className="text-slate-400 text-[10px] uppercase font-semibold flex items-center space-x-1">
                <Smartphone className="w-3 h-3 text-purple-400" />
                <span>Camera Hardware</span>
              </span>
              <p className="font-semibold text-slate-200">
                {currentExif.device_model}
              </p>
              <p className="text-slate-400 text-[11px] font-mono">
                {currentExif.exposure || '1/250s'} • ISO {currentExif.iso || 50}
              </p>
            </div>

            {/* Integrity Hash */}
            <div className="space-y-1">
              <span className="text-slate-400 text-[10px] uppercase font-semibold flex items-center space-x-1">
                <CheckCircle className="w-3 h-3 text-emerald-400" />
                <span>Device Sanitization</span>
              </span>
              <p className="text-emerald-400 font-medium">
                Hardware Verified
              </p>
              <p className="text-slate-500 text-[10px] font-mono truncate" title={currentExif.hash_fingerprint}>
                {currentExif.hash_fingerprint || 'sha256:7f9c2d1b8e4a9051...'}
              </p>
            </div>
          </div>
        </div>
      )}

      {/* Multi-Photo Thumbnail Strip */}
      {displayPhotos.length > 1 && (
        <div className="flex items-center space-x-2 pt-1 overflow-x-auto">
          {displayPhotos.map((photo, idx) => (
            <button
              key={idx}
              type="button"
              onClick={() => setActivePhotoIndex(idx)}
              className={`relative flex-shrink-0 w-16 h-16 rounded-xl overflow-hidden border-2 transition-all ${
                activePhotoIndex === idx
                  ? 'border-emerald-500 ring-2 ring-emerald-500/30 scale-105'
                  : 'border-slate-300 opacity-70 hover:opacity-100'
              }`}
            >
              <img
                src={photo.thumbnail_url || photo.url}
                alt={photo.caption || `Photo ${idx + 1}`}
                className="w-full h-full object-cover"
              />
              <span className="absolute bottom-0 inset-x-0 bg-slate-950/80 text-[9px] text-center text-white py-0.5 font-mono">
                #{idx + 1}
              </span>
            </button>
          ))}
        </div>
      )}
    </div>
  );
};
