import React, { useState } from 'react';
import { 
  CheckSquare, 
  Square, 
  Volume2, 
  Send, 
  Calculator, 
  FileCheck, 
  CheckCircle2, 
  Clock,
  ShieldCheck
} from 'lucide-react';
import { CibrcPesticideRecord, FarmerDossier } from '../types';
import { CIBRC_DATABASE } from '../data/cibrcDatabase';

interface CibrcPrescriptionBuilderProps {
  farmer: FarmerDossier;
  cropName?: string;
  initialPrescription?: string;
  isResolved?: boolean;
  onDispatch: (prescriptionText: string, metadata?: any) => Promise<void>;
  onCancel?: () => void;
}

export const CibrcPrescriptionBuilder: React.FC<CibrcPrescriptionBuilderProps> = ({
  farmer,
  cropName = 'Cotton',
  initialPrescription,
  isResolved = false,
  onDispatch,
  onCancel
}) => {
  // Find default matching pesticide for current crop
  const cropLower = cropName.toLowerCase();
  const matchedPesticide = CIBRC_DATABASE.find(p => 
    p.target_crop.toLowerCase().includes(cropLower) || cropLower.includes(p.target_crop.toLowerCase())
  ) || CIBRC_DATABASE[0];

  const [selectedPesticideId, setSelectedPesticideId] = useState<string>(matchedPesticide.id);
  const [landAcres, setLandAcres] = useState<number>(farmer.land_size_acres || 3.0);
  const dilutionWater = '200 Liters / acre';
  
  // Mandatory PPE Checkboxes
  const [equipment, setEquipment] = useState({
    rubber_gloves: true,
    respirator_mask: true,
    protective_eyewear: true,
    long_clothing_boots: true,
    bee_aquatic_safety: true
  });

  // Voice synthesis push toggle & dialect
  const [pushVoice, setPushVoice] = useState(true);
  const [selectedDialect, setSelectedDialect] = useState<'te-rural' | 'hi-rural' | 'en-standard'>('te-rural');
  const [officerNotes, setOfficerNotes] = useState<string>(
    'Spray early morning before 9 AM or after 4 PM. Ensure uniform coverage on foliage.'
  );

  const [submitting, setSubmitting] = useState(false);
  const [previewTab, setPreviewTab] = useState<'prescription' | 'voice_script'>('prescription');

  const selectedRecord: CibrcPesticideRecord = CIBRC_DATABASE.find(p => p.id === selectedPesticideId) || CIBRC_DATABASE[0];

  // Knapsack Sprayer Pump Calculations
  const pumpsPerAcre = 13.3;
  const totalPumps = Math.round(landAcres * pumpsPerAcre);

  // Toxicity band styling helper
  const getToxicityBadge = (band: CibrcPesticideRecord['toxicity_band']) => {
    switch (band) {
      case 'green_slightly_toxic':
        return { label: 'Green Band (Slightly Toxic)', bg: 'bg-emerald-100 text-emerald-800 border-emerald-300' };
      case 'blue_moderately_toxic':
        return { label: 'Blue Band (Moderately Toxic)', bg: 'bg-blue-100 text-blue-800 border-blue-300' };
      case 'yellow_highly_toxic':
        return { label: 'Yellow Band (Highly Toxic)', bg: 'bg-amber-100 text-amber-900 border-amber-300' };
      case 'red_extremely_toxic':
        return { label: 'Red Band (Extremely Toxic)', bg: 'bg-red-100 text-red-900 border-red-300' };
      default:
        return { label: 'Verified Agricultural Formula', bg: 'bg-slate-100 text-slate-800 border-slate-300' };
    }
  };

  // Build the deterministic prescription text
  const generatePrescriptionText = (): string => {
    if (isResolved && initialPrescription) {
      return initialPrescription;
    }

    const ppeList: string[] = [];
    if (equipment.rubber_gloves) ppeList.push('Rubber gloves');
    if (equipment.respirator_mask) ppeList.push('N95/Respirator mask');
    if (equipment.protective_eyewear) ppeList.push('Protective eye goggles');
    if (equipment.long_clothing_boots) ppeList.push('Long-sleeved shirt & rubber boots');

    return (
      `OFFICIAL AGRICULTURAL PRESCRIPTION [CIBRC VERIFIED]\n` +
      `Farmer: ${farmer.name} (${farmer.village}, ${farmer.mandal}) | Plot Size: ${landAcres} Acres\n` +
      `Target: ${cropName} • ${selectedRecord.target_pest_or_disease}\n` +
      `Active Formulation: ${selectedRecord.active_ingredient} (${selectedRecord.product_trade_name})\n` +
      `CIBRC Reg No: ${selectedRecord.cibrc_registration_number}\n` +
      `Approved Dosage: ${selectedRecord.dosage_per_acre} (${selectedRecord.dosage_per_15l_pump})\n` +
      `Knapsack Sprayer: Mix approx ${selectedRecord.dosage_per_15l_pump} in each 15-Liter knapsack pump (Approx ${totalPumps} pumps total for ${landAcres} acres).\n` +
      `Water Dilution: ${dilutionWater}\n` +
      `Pre-Harvest Interval (PHI): ${selectedRecord.pre_harvest_interval_days} Days (Strictly observe waiting period before harvest).\n` +
      `Mandatory Safety Gear: ${ppeList.join(', ')}.\n` +
      `Field Instructions: ${officerNotes}\n` +
      `Dispatched by Mandal Agricultural Extension Officer.`
    );
  };

  // Generate localized voice synthesis script preview
  const generateVoiceScript = (): string => {
    if (selectedDialect === 'te-rural') {
      return (
        `రైతు ${farmer.name} గారికి వ్యవసాయ అధికారి సూచన: మీ ${cropName} చేనులో ${selectedRecord.target_pest_or_disease} నివారణకు ${selectedRecord.product_trade_name} (${selectedRecord.active_ingredient}) మందును ఎకరాకు ${selectedRecord.dosage_per_acre} చొప్పున 200 లీటర్ల నీటిలో కలిపి పిచికారీ చేయండి. 15 లీటర్ల చేతి పంపునకు ${selectedRecord.dosage_per_15l_pump} మాత్రమే కలపండి. పురుగుమందు పిచికారీ చేసేటప్పుడు తప్పనిసరిగా ముఖానికి మాస్క్ మరియు చేతులకు రబ్బరు గ్లౌజులు ధరించండి. పంట కోతకు ${selectedRecord.pre_harvest_interval_days} రోజుల ముందు వరకు ఎలాంటి పిచికారీ చేయకూడదు.`
      );
    } else if (selectedDialect === 'hi-rural') {
      return (
        `किसान भाई ${farmer.name} जी, कृषि अधिकारी की सलाह: आपकी ${cropName} की फसल में ${selectedRecord.target_pest_or_disease} की रोकथाम के लिए CIBRC अनुमोदित ${selectedRecord.product_trade_name} (${selectedRecord.active_ingredient}) का छिड़काव प्रति एकड़ ${selectedRecord.dosage_per_acre} की दर से 200 लीटर पानी में मिलाकर करें। 15 लीटर नैपसैक पंप में ${selectedRecord.dosage_per_15l_pump} ही मिलाएं। छिड़काव के दौरान मास्क और रबर के दस्ताने अवश्य पहनें। फसल कटाई से ${selectedRecord.pre_harvest_interval_days} दिन पहले दवा का छिड़काव न करें।`
      );
    } else {
      return (
        `Farmer advisory for ${farmer.name}: Apply CIBRC-approved ${selectedRecord.product_trade_name} (${selectedRecord.active_ingredient}) at ${selectedRecord.dosage_per_acre} diluted in 200 liters of water per acre. For a 15-liter knapsack sprayer, use ${selectedRecord.dosage_per_15l_pump}. Wear protective face mask and rubber gloves. Observe ${selectedRecord.pre_harvest_interval_days} days pre-harvest waiting interval.`
      );
    }
  };

  const handleEquipmentToggle = (key: keyof typeof equipment) => {
    setEquipment(prev => ({ ...prev, [key]: !prev[key] }));
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setSubmitting(true);
    try {
      const finalPrescription = generatePrescriptionText();
      await onDispatch(finalPrescription, {
        pesticide_id: selectedPesticideId,
        trade_name: selectedRecord.product_trade_name,
        active_ingredient: selectedRecord.active_ingredient,
        dosage_per_acre: selectedRecord.dosage_per_acre,
        dosage_per_15l_pump: selectedRecord.dosage_per_15l_pump,
        phi_days: selectedRecord.pre_harvest_interval_days,
        push_voice: pushVoice,
        voice_script: pushVoice ? generateVoiceScript() : null
      });
    } catch (err) {
      alert('Error dispatching prescription: ' + (err as Error).message);
    } finally {
      setSubmitting(false);
    }
  };

  const toxicityBadge = getToxicityBadge(selectedRecord.toxicity_band);

  return (
    <div className="bg-white rounded-2xl border border-slate-200 shadow-sm overflow-hidden">
      {/* Builder Header */}
      <div className="bg-forest-900 text-white p-5 flex items-center justify-between">
        <div className="flex items-center space-x-3">
          <div className="p-2.5 bg-forest-800 text-forest-200 rounded-xl">
            <FileCheck className="w-5 h-5 text-emerald-400" />
          </div>
          <div>
            <div className="flex items-center space-x-2">
              <h3 className="text-base font-bold font-serif">Guided CIBRC Prescription Builder</h3>
              <span className="bg-emerald-500/20 text-emerald-300 text-[10px] uppercase font-mono px-2 py-0.5 rounded border border-emerald-500/30">
                8-Param Compliant
              </span>
            </div>
            <p className="text-xs text-forest-200 mt-0.5">
              Deterministic dosage assignment with zero LLM hallucination risk
            </p>
          </div>
        </div>

        {isResolved && (
          <span className="flex items-center space-x-1.5 text-xs bg-emerald-500 text-slate-950 font-bold px-3 py-1 rounded-full shadow">
            <CheckCircle2 className="w-4 h-4" />
            <span>RESOLVED & DISPATCHED</span>
          </span>
        )}
      </div>

      <form onSubmit={handleSubmit} className="p-5 sm:p-6 space-y-6">
        {/* Step 1: Active Ingredient & Trade Name Picker */}
        <div className="space-y-3">
          <label className="block text-xs font-bold uppercase tracking-wider text-slate-700">
            1. Select CIBRC-Approved Active Ingredient & Trade Name
          </label>
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
            {CIBRC_DATABASE.map((record) => {
              const isSelected = selectedPesticideId === record.id;
              const isCropMatch = record.target_crop.toLowerCase().includes(cropLower);

              return (
                <div
                  key={record.id}
                  onClick={() => !isResolved && setSelectedPesticideId(record.id)}
                  className={`p-3.5 rounded-xl border-2 transition-all cursor-pointer relative ${
                    isSelected
                      ? 'border-forest-600 bg-forest-50/60 shadow-sm ring-2 ring-forest-500/20'
                      : 'border-slate-200 hover:border-slate-300 bg-white'
                  } ${isResolved ? 'pointer-events-none opacity-80' : ''}`}
                >
                  {isCropMatch && (
                    <span className="absolute top-2.5 right-2.5 text-[9px] bg-emerald-100 text-emerald-800 font-bold uppercase px-1.5 py-0.5 rounded">
                      Matched: {record.target_crop}
                    </span>
                  )}

                  <div className="pr-16">
                    <p className="text-sm font-bold text-slate-900">{record.product_trade_name}</p>
                    <p className="text-xs text-forest-800 font-semibold">{record.active_ingredient}</p>
                    <p className="text-[11px] text-slate-500 mt-1">Target: {record.target_pest_or_disease}</p>
                  </div>

                  <div className="mt-2.5 pt-2 border-t border-slate-100 flex items-center justify-between text-[11px]">
                    <span className="font-mono text-slate-600 font-medium">
                      Dosage: {record.dosage_per_acre}
                    </span>
                    <span className="text-slate-500 font-medium">
                      PHI: {record.pre_harvest_interval_days} Days
                    </span>
                  </div>
                </div>
              );
            })}
          </div>
        </div>

        {/* Step 2: Auto-Populated Dosage & Knapsack Pump Calculation */}
        <div className="bg-slate-50 border border-slate-200 rounded-2xl p-4 sm:p-5 space-y-4">
          <div className="flex flex-wrap items-center justify-between gap-2">
            <span className="text-xs font-bold uppercase tracking-wider text-slate-700 flex items-center space-x-1.5">
              <Calculator className="w-4 h-4 text-forest-700" />
              <span>2. Auto-Populated CIBRC Dosage & Knapsack Sprayer Calibration</span>
            </span>
            <span className={`text-[10px] font-bold px-2.5 py-0.5 rounded-full border ${toxicityBadge.bg}`}>
              {toxicityBadge.label}
            </span>
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-4 gap-3">
            {/* Acre Dosage */}
            <div className="bg-white p-3 rounded-xl border border-slate-200">
              <span className="text-[10px] text-slate-500 uppercase font-bold">Approved Acre Dosage</span>
              <p className="text-sm font-black text-slate-900 mt-1 font-mono">
                {selectedRecord.dosage_per_acre}
              </p>
              <span className="text-[10px] text-slate-400">Strict CIBRC label max</span>
            </div>

            {/* 15L Knapsack Pump */}
            <div className="bg-white p-3 rounded-xl border border-emerald-300 bg-emerald-50/20">
              <span className="text-[10px] text-emerald-800 uppercase font-bold">Per 15L Pump (Smallholder)</span>
              <p className="text-sm font-black text-emerald-700 mt-1 font-mono">
                {selectedRecord.dosage_per_15l_pump}
              </p>
              <span className="text-[10px] text-emerald-600">No guesswork for farmer</span>
            </div>

            {/* Dilution Water */}
            <div className="bg-white p-3 rounded-xl border border-slate-200">
              <span className="text-[10px] text-slate-500 uppercase font-bold">Water Volume</span>
              <p className="text-sm font-black text-slate-900 mt-1 font-mono">
                {dilutionWater}
              </p>
              <span className="text-[10px] text-slate-400">Ensures uniform coverage</span>
            </div>

            {/* Pre-Harvest Interval */}
            <div className="bg-white p-3 rounded-xl border border-amber-300 bg-amber-50/20">
              <span className="text-[10px] text-amber-800 uppercase font-bold flex items-center space-x-1">
                <Clock className="w-3 h-3 text-amber-600" />
                <span>Pre-Harvest (PHI)</span>
              </span>
              <p className="text-sm font-black text-amber-700 mt-1 font-mono">
                {selectedRecord.pre_harvest_interval_days} Days
              </p>
              <span className="text-[10px] text-amber-700">Waiting period before harvest</span>
            </div>
          </div>

          {/* Knapsack Sprayer Acre Calculator */}
          <div className="bg-white p-3.5 rounded-xl border border-slate-200 flex flex-wrap items-center justify-between gap-3 text-xs">
            <div className="flex items-center space-x-2">
              <span className="text-slate-600 font-semibold">Farmer Field Size:</span>
              <input
                type="number"
                step="0.1"
                min="0.5"
                max="50"
                value={landAcres}
                onChange={(e) => setLandAcres(parseFloat(e.target.value) || 1)}
                disabled={isResolved}
                className="w-16 px-2 py-1 border border-slate-300 rounded font-mono font-bold text-center"
              />
              <span className="text-slate-600">Acres</span>
            </div>

            <div className="flex items-center space-x-3 text-slate-700">
              <span className="bg-forest-50 text-forest-800 px-2.5 py-1 rounded font-semibold border border-forest-200">
                Total Tanks: ~{totalPumps} Knapsack Pumps (15L each)
              </span>
            </div>
          </div>
        </div>

        {/* Step 3: Mandatory Safety Equipment Checkboxes */}
        <div className="space-y-3">
          <label className="block text-xs font-bold uppercase tracking-wider text-slate-700">
            3. Standard Protective Safety Equipment & Environmental Precautions
          </label>
          <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 gap-2.5 text-xs">
            {/* Rubber Gloves */}
            <div 
              onClick={() => !isResolved && handleEquipmentToggle('rubber_gloves')}
              className={`p-3 rounded-xl border flex items-center space-x-2.5 cursor-pointer transition-colors ${
                equipment.rubber_gloves ? 'bg-forest-50 border-forest-300 text-forest-900' : 'bg-slate-50 border-slate-200 text-slate-600'
              }`}
            >
              {equipment.rubber_gloves ? (
                <CheckSquare className="w-4 h-4 text-forest-700 flex-shrink-0" />
              ) : (
                <Square className="w-4 h-4 text-slate-400 flex-shrink-0" />
              )}
              <span className="font-semibold">Chemical Rubber Gloves</span>
            </div>

            {/* Respirator Mask */}
            <div 
              onClick={() => !isResolved && handleEquipmentToggle('respirator_mask')}
              className={`p-3 rounded-xl border flex items-center space-x-2.5 cursor-pointer transition-colors ${
                equipment.respirator_mask ? 'bg-forest-50 border-forest-300 text-forest-900' : 'bg-slate-50 border-slate-200 text-slate-600'
              }`}
            >
              {equipment.respirator_mask ? (
                <CheckSquare className="w-4 h-4 text-forest-700 flex-shrink-0" />
              ) : (
                <Square className="w-4 h-4 text-slate-400 flex-shrink-0" />
              )}
              <span className="font-semibold">N95 / Particle Respirator Mask</span>
            </div>

            {/* Eyewear / Goggles */}
            <div 
              onClick={() => !isResolved && handleEquipmentToggle('protective_eyewear')}
              className={`p-3 rounded-xl border flex items-center space-x-2.5 cursor-pointer transition-colors ${
                equipment.protective_eyewear ? 'bg-forest-50 border-forest-300 text-forest-900' : 'bg-slate-50 border-slate-200 text-slate-600'
              }`}
            >
              {equipment.protective_eyewear ? (
                <CheckSquare className="w-4 h-4 text-forest-700 flex-shrink-0" />
              ) : (
                <Square className="w-4 h-4 text-slate-400 flex-shrink-0" />
              )}
              <span className="font-semibold">Protective Eyewear / Goggles</span>
            </div>

            {/* Long Clothing & Boots */}
            <div 
              onClick={() => !isResolved && handleEquipmentToggle('long_clothing_boots')}
              className={`p-3 rounded-xl border flex items-center space-x-2.5 cursor-pointer transition-colors ${
                equipment.long_clothing_boots ? 'bg-forest-50 border-forest-300 text-forest-900' : 'bg-slate-50 border-slate-200 text-slate-600'
              }`}
            >
              {equipment.long_clothing_boots ? (
                <CheckSquare className="w-4 h-4 text-forest-700 flex-shrink-0" />
              ) : (
                <Square className="w-4 h-4 text-slate-400 flex-shrink-0" />
              )}
              <span className="font-semibold">Long Sleeves & Rubber Boots</span>
            </div>

            {/* Bee & Water Body Protection */}
            <div 
              onClick={() => !isResolved && handleEquipmentToggle('bee_aquatic_safety')}
              className={`p-3 rounded-xl border flex items-center space-x-2.5 cursor-pointer transition-colors sm:col-span-2 ${
                equipment.bee_aquatic_safety ? 'bg-forest-50 border-forest-300 text-forest-900' : 'bg-slate-50 border-slate-200 text-slate-600'
              }`}
            >
              {equipment.bee_aquatic_safety ? (
                <CheckSquare className="w-4 h-4 text-forest-700 flex-shrink-0" />
              ) : (
                <Square className="w-4 h-4 text-slate-400 flex-shrink-0" />
              )}
              <span className="font-semibold">Do not spray near water bodies or during bee foraging hours</span>
            </div>
          </div>
        </div>

        {/* Step 4: Spoken Voice Prescription Push Toggle */}
        <div className="bg-gradient-to-r from-forest-50 via-emerald-50 to-teal-50 border border-forest-200 rounded-2xl p-4 sm:p-5 space-y-3">
          <div className="flex flex-wrap items-center justify-between gap-3">
            <div className="flex items-center space-x-3">
              <div className="p-2 bg-forest-700 text-white rounded-xl shadow-xs">
                <Volume2 className="w-5 h-5" />
              </div>
              <div>
                <p className="text-sm font-bold text-slate-900">
                  Push Spoken Voice Prescription to Farmer
                </p>
                <p className="text-xs text-slate-600">
                  Automatically synthesizes this exact verified prescription in the farmer's native dialect
                </p>
              </div>
            </div>

            <label className="relative inline-flex items-center cursor-pointer">
              <input 
                type="checkbox" 
                checked={pushVoice} 
                onChange={(e) => setPushVoice(e.target.checked)}
                disabled={isResolved}
                className="sr-only peer"
              />
              <div className="w-11 h-6 bg-slate-300 peer-focus:outline-none rounded-full peer peer-checked:after:translate-x-full peer-checked:after:border-white after:content-[''] after:absolute after:top-[2px] after:left-[2px] after:bg-white after:border-slate-300 after:border after:rounded-full after:h-5 after:w-5 after:transition-all peer-checked:bg-forest-700"></div>
            </label>
          </div>

          {pushVoice && (
            <div className="pt-2 flex items-center space-x-2 text-xs">
              <span className="text-slate-600 font-semibold">Target Dialect:</span>
              <select
                value={selectedDialect}
                onChange={(e) => setSelectedDialect(e.target.value as any)}
                disabled={isResolved}
                className="border border-forest-300 rounded-lg px-2.5 py-1 text-xs bg-white text-forest-900 font-medium focus:ring-1 focus:ring-forest-600"
              >
                <option value="te-rural">Warangal Rural Telugu (తెలుగు)</option>
                <option value="hi-rural">Rural Hindi (ग्रामीण हिंदी)</option>
                <option value="en-standard">Standard English</option>
              </select>
            </div>
          )}
        </div>

        {/* Step 5: Additional Officer Field Notes */}
        <div className="space-y-1.5">
          <label className="block text-xs font-bold uppercase tracking-wider text-slate-700">
            5. Additional Officer Agronomic Directives
          </label>
          <input
            type="text"
            value={officerNotes}
            onChange={(e) => setOfficerNotes(e.target.value)}
            disabled={isResolved}
            placeholder="e.g. Apply early morning; avoid midday heat."
            className="w-full border border-slate-300 rounded-xl px-3.5 py-2 text-xs sm:text-sm focus:ring-2 focus:ring-forest-500 focus:border-forest-500 font-sans disabled:bg-slate-100"
          />
        </div>

        {/* Step 6: Live Prescription & Voice Script Preview */}
        <div className="space-y-2 pt-2 border-t border-slate-200">
          <div className="flex items-center justify-between">
            <div className="flex items-center space-x-1 bg-slate-100 p-1 rounded-xl text-xs font-semibold">
              <button
                type="button"
                onClick={() => setPreviewTab('prescription')}
                className={`px-3 py-1 rounded-lg transition-all ${
                  previewTab === 'prescription' ? 'bg-white text-forest-900 shadow-xs' : 'text-slate-600 hover:text-slate-900'
                }`}
              >
                Official Prescription Statement
              </button>
              {pushVoice && (
                <button
                  type="button"
                  onClick={() => setPreviewTab('voice_script')}
                  className={`px-3 py-1 rounded-lg transition-all ${
                    previewTab === 'voice_script' ? 'bg-white text-forest-900 shadow-xs' : 'text-slate-600 hover:text-slate-900'
                  }`}
                >
                  Synthesized Audio Script Preview
                </button>
              )}
            </div>

            <span className="text-[11px] text-emerald-700 font-mono font-bold flex items-center space-x-1">
              <ShieldCheck className="w-3.5 h-3.5" />
              <span>CIBRC LAWFUL</span>
            </span>
          </div>

          {previewTab === 'prescription' ? (
            <pre className="bg-slate-900 text-slate-200 p-4 rounded-xl text-xs font-mono leading-relaxed whitespace-pre-wrap border border-slate-800 shadow-inner">
              {generatePrescriptionText()}
            </pre>
          ) : (
            <div className="bg-forest-900 text-emerald-100 p-4 rounded-xl text-xs leading-relaxed border border-forest-800 font-sans shadow-inner">
              <p className="font-bold text-emerald-300 mb-1 flex items-center space-x-1.5">
                <Volume2 className="w-4 h-4 text-emerald-400" />
                <span>Text-to-Speech Output ({selectedDialect}):</span>
              </p>
              <p className="italic">"{generateVoiceScript()}"</p>
            </div>
          )}
        </div>

        {/* Submission Actions */}
        {!isResolved && (
          <div className="pt-3 border-t border-slate-200 flex flex-wrap items-center justify-end gap-3">
            {onCancel && (
              <button
                type="button"
                onClick={onCancel}
                className="px-4 py-2.5 border border-slate-300 rounded-xl text-xs sm:text-sm font-semibold text-slate-700 hover:bg-slate-100 transition-colors"
              >
                Cancel
              </button>
            )}

            <button
              type="submit"
              disabled={submitting}
              className="flex items-center space-x-2 bg-forest-700 hover:bg-forest-800 disabled:opacity-50 text-white px-6 py-2.5 rounded-xl text-xs sm:text-sm font-bold shadow-md shadow-forest-800/20 transition-all active:scale-98"
            >
              <Send className="w-4 h-4" />
              <span>
                {submitting ? 'Dispatching Official Resolution...' : 'Dispatch Verified Prescription & Audio'}
              </span>
            </button>
          </div>
        )}
      </form>
    </div>
  );
};
