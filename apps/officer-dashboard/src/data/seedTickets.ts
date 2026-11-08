import { TicketDetail, TicketSummary } from '../types';

export const SEED_TICKETS: TicketDetail[] = [
  {
    id: 'ticket-esc-001',
    ticket_number: 'ESC-20261108-PK92B1',
    farmer: {
      id: 'farmer-wrgl-01',
      name: 'Rameshwar Rao Thota',
      phone: '+91 98480 23411',
      village: 'Dharmaram',
      mandal: 'Geesugonda',
      district: 'Warangal',
      state: 'Telangana',
      land_size_acres: 4.2,
      soil_type: 'Deep Black Cotton',
      primary_crop: 'Bt Cotton'
    },
    status: 'open',
    priority: 'high',
    crop_name: 'Cotton',
    target_pest_or_disease: 'Pink Bollworm (Pectinophora gossypiella)',
    escalation_reason: 'Deterministic Safety Gate Trigger: Farmer proposed double chemical dosage (200g/acre) of unverified pesticide cocktail without protective equipment. Mandatory human officer verification required.',
    original_question_transcript: 'నా పత్తి చేనులో గులాబీ రంగు పురుగు విపరీతంగా పెరిగింది. పక్క రైతు ప్రోక్లైమ్ మందు డబుల్ డోస్ కొట్టమన్నాడు. 200 గ్రాములు ఎకరాకు కొట్టవచ్చా? లేదా ఎంత మోతాదు వాడాలి?',
    audio_recording_url: 'https://bolkisan-audio.telangana.gov.in/recordings/20261108_geesugonda_pbw_audio.mp3',
    audio_duration_seconds: 18,
    audio_transcript: {
      original_language: 'te',
      language_label: 'Warangal Rural Telugu',
      original_text: 'నా పత్తి చేనులో గులాబీ రంగు పురుగు విపరీతంగా పెరిగింది. పక్క రైతు ప్రోక్లైమ్ మందు డబుల్ డోస్ కొట్టమన్నాడు. 200 గ్రాములు ఎకరాకు కొట్టవచ్చా? లేదా ఎంత మోతాదు వాడాలి?',
      phonetic_gloss: 'Naa patti chenulo gulaabi rangu purugu vipareethanga perigindi. Pakka raithu Proclaim mandhu double dose kottamannadu. 200 grams ekaraaku kottavachaa? Leda entha mothaadu vaadaali?',
      english_translation: 'Pink bollworm has heavily infested my cotton field. Neighboring farmer suggested spraying double dosage of Proclaim pesticide. Can I spray 200g per acre or what is the approved dosage?',
      segments: [
        {
          start_ms: 0,
          end_ms: 4500,
          text_te: 'నా పత్తి చేనులో గులాబీ రంగు పురుగు విపరీతంగా పెరిగింది.',
          text_en: 'Pink bollworm has heavily infested my cotton field.'
        },
        {
          start_ms: 4500,
          end_ms: 9500,
          text_te: 'పక్క రైతు ప్రోక్లైమ్ మందు డబుల్ డోస్ కొట్టమన్నాడు.',
          text_en: 'Neighboring farmer advised spraying double dosage of Proclaim.'
        },
        {
          start_ms: 9500,
          end_ms: 14000,
          text_te: '200 గ్రాములు ఎకరాకు కొట్టవచ్చా?',
          text_en: 'Can I spray 200 grams per acre?'
        },
        {
          start_ms: 14000,
          end_ms: 18000,
          text_te: 'లేదా ఖచ్చితమైన మోతాదు ఎంత వాడాలి?',
          text_en: 'Or what is the exact approved dosage to be used?'
        }
      ]
    },
    attached_image_urls: [
      'https://images.unsplash.com/photo-1599818816930-678456100c5c?auto=format&fit=crop&w=1200&q=80',
      'https://images.unsplash.com/photo-1598880940371-c756e015fea1?auto=format&fit=crop&w=1200&q=80'
    ],
    photo_url: 'https://images.unsplash.com/photo-1599818816930-678456100c5c?auto=format&fit=crop&w=1200&q=80',
    exif_verification: {
      latitude: 17.9689,
      longitude: 79.5941,
      location_name: 'Dharmaram Village, Geesugonda Mandal, Warangal',
      captured_at: '2026-11-08T08:42:15+05:30',
      device_model: 'Samsung Galaxy M34 5G (SM-M346B)',
      camera_make: 'Samsung ISOCELL S5KJN1',
      exposure: '1/250s at f/1.8',
      focal_length: '26mm equivalent',
      iso: 50,
      is_timestamp_verified: true,
      is_gps_verified: true,
      is_device_sanitized: true,
      tamper_check_status: 'passed',
      hash_fingerprint: 'sha256:7f9c2d1b8e4a9051d9bc43a856f712903e1a'
    },
    photos: [
      {
        url: 'https://images.unsplash.com/photo-1599818816930-678456100c5c?auto=format&fit=crop&w=1200&q=80',
        thumbnail_url: 'https://images.unsplash.com/photo-1599818816930-678456100c5c?auto=format&fit=crop&w=300&q=80',
        caption: 'Boll Inspection: Rosetted petal with larval entry hole',
        tag: 'leaf_ventral',
        exif: {
          latitude: 17.9689,
          longitude: 79.5941,
          location_name: 'Dharmaram, Geesugonda, Warangal',
          captured_at: '2026-11-08T08:42:15+05:30',
          device_model: 'Samsung Galaxy M34 5G',
          is_timestamp_verified: true,
          is_gps_verified: true,
          is_device_sanitized: true,
          tamper_check_status: 'passed'
        },
        annotations: [
          {
            id: 'ann-01',
            label: 'Larval Entry Hole',
            x: 48,
            y: 42,
            severity: 'severe',
            description: 'Pink bollworm 2nd instar bore hole with frass extrusion'
          },
          {
            id: 'ann-02',
            label: 'Petal Rosetting',
            x: 62,
            y: 58,
            severity: 'moderate',
            description: 'Classic webbed rosette flower symptom indicating active moth infestation'
          }
        ]
      },
      {
        url: 'https://images.unsplash.com/photo-1598880940371-c756e015fea1?auto=format&fit=crop&w=1200&q=80',
        thumbnail_url: 'https://images.unsplash.com/photo-1598880940371-c756e015fea1?auto=format&fit=crop&w=300&q=80',
        caption: 'Field Overview: Plot 2 Canopy Inspection',
        tag: 'field_overview',
        exif: {
          latitude: 17.9691,
          longitude: 79.5939,
          location_name: 'Dharmaram Field Plot 2',
          captured_at: '2026-11-08T08:44:02+05:30',
          device_model: 'Samsung Galaxy M34 5G',
          is_timestamp_verified: true,
          is_gps_verified: true,
          is_device_sanitized: true,
          tamper_check_status: 'passed'
        },
        annotations: []
      }
    ],
    created_at: '2026-11-08T08:45:00+05:30'
  },
  {
    id: 'ticket-esc-002',
    ticket_number: 'ESC-20261108-BR44C8',
    farmer: {
      id: 'farmer-wrgl-02',
      name: 'Anjaiah Bandaru',
      phone: '+91 94401 56789',
      village: 'Kothapalli',
      mandal: 'Wardhannapet',
      district: 'Warangal',
      state: 'Telangana',
      land_size_acres: 3.0,
      soil_type: 'Clay Loam Wetland',
      primary_crop: 'Paddy / BPT 5204'
    },
    status: 'open',
    priority: 'emergency',
    crop_name: 'Rice / Paddy',
    target_pest_or_disease: 'Brown Plant Hopper (BPH - Nilaparvata lugens)',
    escalation_reason: 'Critical Threshold Exceeded: Hopper burn patch observed in paddy field. Severe localized lodging risk. Fast chemical knockdown needed under officer supervision.',
    original_question_transcript: 'వరి చేనులో మొద్దుల వద్ద నల్లగా మచ్చలు పడి సుడులు తిరుగుతోంది. పిట్టల ముక్కు పురుగులు గుంపులుగా ఉన్నాయి. చేను ఎండిపోతోంది, ఏం మందు పిచికారీ చేయాలి?',
    audio_recording_url: 'https://bolkisan-audio.telangana.gov.in/recordings/20261108_wardhannapet_bph_audio.mp3',
    audio_duration_seconds: 15,
    audio_transcript: {
      original_language: 'te',
      language_label: 'Wardhannapet Dialect',
      original_text: 'వరి చేనులో మొద్దుల వద్ద నల్లగా మచ్చలు పడి సుడులు తిరుగుతోంది. పిట్టల ముక్కు పురుగులు గుంపులుగా ఉన్నాయి. చేను ఎండిపోతోంది, ఏం మందు పిచికారీ చేయాలి?',
      phonetic_gloss: 'Vari chenulo moddula vadda nallaga macchalu padi sudulu thiruguthondi. Pittala mukku purugulu gumpuluga unnaayi. Chenu endipothondi, em mandhu pichikaari cheyaali?',
      english_translation: 'In my paddy field, hopper burn circles have formed at the stem bases. Plant hoppers are clustered in swarms. Crop is drying out rapidly, what medicine should be sprayed?',
      segments: [
        {
          start_ms: 0,
          end_ms: 5000,
          text_te: 'వరి చేనులో మొద్దుల వద్ద నల్లగా మచ్చలు పడి సుడులు తిరుగుతోంది.',
          text_en: 'Circular hopper burn patches formed at the stem base.'
        },
        {
          start_ms: 5000,
          end_ms: 10500,
          text_te: 'పిట్టల ముక్కు పురుగులు గుంపులుగా ఉన్నాయి, చేను ఎండిపోతోంది.',
          text_en: 'Hoppers are clustered in huge swarms, the field is drying.'
        },
        {
          start_ms: 10500,
          end_ms: 15000,
          text_te: 'ఏం మందు పిచికారీ చేయాలి?',
          text_en: 'What pesticide should I spray immediately?'
        }
      ]
    },
    attached_image_urls: [
      'https://images.unsplash.com/photo-1574943320219-553eb213f72d?auto=format&fit=crop&w=1200&q=80'
    ],
    photo_url: 'https://images.unsplash.com/photo-1574943320219-553eb213f72d?auto=format&fit=crop&w=1200&q=80',
    exif_verification: {
      latitude: 17.8421,
      longitude: 79.6214,
      location_name: 'Kothapalli, Wardhannapet Mandal, Warangal',
      captured_at: '2026-11-08T09:12:30+05:30',
      device_model: 'Redmi Note 12 5G',
      camera_make: 'OmniVision OV48B',
      exposure: '1/320s at f/1.8',
      focal_length: '24mm equivalent',
      iso: 64,
      is_timestamp_verified: true,
      is_gps_verified: true,
      is_device_sanitized: true,
      tamper_check_status: 'passed',
      hash_fingerprint: 'sha256:3a1b8c9d0e2f4a6b8c0d2e4f6a8b0c2d4e6f8a0b'
    },
    photos: [
      {
        url: 'https://images.unsplash.com/photo-1574943320219-553eb213f72d?auto=format&fit=crop&w=1200&q=80',
        thumbnail_url: 'https://images.unsplash.com/photo-1574943320219-553eb213f72d?auto=format&fit=crop&w=300&q=80',
        caption: 'Tiller Base Inspection: Dense BPH nymph colony (>25 hoppers/hill)',
        tag: 'pest_closeup',
        exif: {
          latitude: 17.8421,
          longitude: 79.6214,
          location_name: 'Kothapalli, Wardhannapet',
          captured_at: '2026-11-08T09:12:30+05:30',
          device_model: 'Redmi Note 12 5G',
          is_timestamp_verified: true,
          is_gps_verified: true,
          is_device_sanitized: true,
          tamper_check_status: 'passed'
        },
        annotations: [
          {
            id: 'ann-bph-01',
            label: 'Hopper Colony at Tillers',
            x: 52,
            y: 65,
            severity: 'severe',
            description: 'Severe aggregation of brachypterous females and 3rd-5th instar nymphs'
          },
          {
            id: 'ann-bph-02',
            label: 'Honey Dew & Sooty Mold',
            x: 38,
            y: 48,
            severity: 'moderate',
            description: 'Black sooty mold resulting from honeydew excretion'
          }
        ]
      }
    ],
    created_at: '2026-11-08T09:15:20+05:30'
  },
  {
    id: 'ticket-esc-003',
    ticket_number: 'ESC-20261108-CH71D3',
    farmer: {
      id: 'farmer-wrgl-03',
      name: 'Lakshmi Devi Chada',
      phone: '+91 97012 34567',
      village: 'Neerukulla',
      mandal: 'Atmakur',
      district: 'Warangal',
      state: 'Telangana',
      land_size_acres: 2.5,
      soil_type: 'Red Sandy Loam',
      primary_crop: 'Teja Chilli'
    },
    status: 'resolved',
    priority: 'medium',
    crop_name: 'Chilli',
    target_pest_or_disease: 'Chilli Thrips (Scirtothrips dorsalis)',
    escalation_reason: 'Leaf curl symptoms with upward curling. Farmer suspected virus, but inspection confirms thrips feeding rasping damage.',
    original_question_transcript: 'మిరప తోటలో ఆకులు పైకి ముడుచుకుపోతున్నాయి. బోద రోగం అనుకుంటున్నాము. పెగాసస్ మందు కొట్టొచ్చా? ఎన్ని గ్రాములు కలపాలి?',
    audio_recording_url: 'https://bolkisan-audio.telangana.gov.in/recordings/20261108_atmakur_thrips_audio.mp3',
    audio_duration_seconds: 14,
    audio_transcript: {
      original_language: 'te',
      language_label: 'Warangal Rural Telugu',
      original_text: 'మిరప తోటలో ఆకులు పైకి ముడుచుకుపోతున్నాయి. బోద రోగం అనుకుంటున్నాము. పెగాసస్ మందు కొట్టొచ్చా? ఎన్ని గ్రాములు కలపాలి?',
      phonetic_gloss: 'Mirapa thotalo aakulu paiki muduchukupothunnaayi. Boda rogam anukuntunnaamu. Pegasus mandhu kottocchaa? Enni graamulu kalapaali?',
      english_translation: 'In my chilli crop, leaves are curling upwards like a boat. We suspected Gemini leaf curl virus. Can we spray Pegasus? How many grams to mix?',
      segments: [
        {
          start_ms: 0,
          end_ms: 4500,
          text_te: 'మిరప తోటలో ఆకులు పైకి ముడుచుకుపోతున్నాయి.',
          text_en: 'Chilli leaves are curling upwards into a boat shape.'
        },
        {
          start_ms: 4500,
          end_ms: 9000,
          text_te: 'బోద రోగం అనుకుంటున్నాము.',
          text_en: 'We suspected viral leaf curl disease.'
        },
        {
          start_ms: 9000,
          end_ms: 14000,
          text_te: 'పెగాసస్ మందు కొట్టొచ్చా? ఎన్ని గ్రాములు కలపాలి?',
          text_en: 'Can we spray Pegasus? How many grams to mix?'
        }
      ]
    },
    attached_image_urls: [
      'https://images.unsplash.com/photo-1592417817098-8f3d6910985b?auto=format&fit=crop&w=1200&q=80'
    ],
    photo_url: 'https://images.unsplash.com/photo-1592417817098-8f3d6910985b?auto=format&fit=crop&w=1200&q=80',
    exif_verification: {
      latitude: 18.1023,
      longitude: 79.712,
      location_name: 'Neerukulla, Atmakur Mandal, Warangal',
      captured_at: '2026-11-08T07:30:10+05:30',
      device_model: 'Realme Narzo 50',
      camera_make: 'Samsung GM1',
      exposure: '1/400s at f/1.8',
      focal_length: '26mm',
      iso: 100,
      is_timestamp_verified: true,
      is_gps_verified: true,
      is_device_sanitized: true,
      tamper_check_status: 'passed'
    },
    photos: [
      {
        url: 'https://images.unsplash.com/photo-1592417817098-8f3d6910985b?auto=format&fit=crop&w=1200&q=80',
        thumbnail_url: 'https://images.unsplash.com/photo-1592417817098-8f3d6910985b?auto=format&fit=crop&w=300&q=80',
        caption: 'Chilli apical foliage: Upward curling and ventral bronzing from thrips rasping',
        tag: 'leaf_dorsal',
        exif: {
          latitude: 18.1023,
          longitude: 79.712,
          location_name: 'Neerukulla, Atmakur',
          captured_at: '2026-11-08T07:30:10+05:30',
          device_model: 'Realme Narzo 50',
          is_timestamp_verified: true,
          is_gps_verified: true,
          is_device_sanitized: true,
          tamper_check_status: 'passed'
        },
        annotations: [
          {
            id: 'ann-chilli-01',
            label: 'Boat-shaped Upward Curl',
            x: 55,
            y: 40,
            severity: 'moderate',
            description: 'Thrips feeding rasps epidermis causing uneven leaf lamina elongation'
          }
        ]
      }
    ],
    resolution_prescription: 'Official Extension Prescription: Prescribed Diafenthiuron 50% WP (Pegasus) at 240g per acre (18g per 15L knapsack pump) diluted in 200 liters of water. Spray underside of leaves thoroughly. Pre-harvest interval is 7 days. Mandatory safety: Wear rubber gloves and goggles. Do not spray near fish ponds.',
    prescribed_chemical: 'Diafenthiuron 50% WP (Pegasus 50% WP)',
    prescribed_dosage: '240g / acre (18g / 15L pump)',
    prescribed_phi_days: 7,
    safety_equipment: ['Rubber Gloves', 'N95 Respirator Mask', 'Protective Eyewear / Goggles'],
    push_voice_to_farmer: true,
    created_at: '2026-11-08T07:35:00+05:30',
    resolved_at: '2026-11-08T08:15:00+05:30'
  },
  {
    id: 'ticket-esc-004',
    ticket_number: 'ESC-20261108-RB19A4',
    farmer: {
      id: 'farmer-wrgl-04',
      name: 'Malleshwar Reddy Singireddy',
      phone: '+91 99890 87654',
      village: 'Viswanathpur',
      mandal: 'Geesugonda',
      district: 'Warangal',
      state: 'Telangana',
      land_size_acres: 5.0,
      soil_type: 'Heavy Black Clay',
      primary_crop: 'Paddy / MTU 1010'
    },
    status: 'open',
    priority: 'medium',
    crop_name: 'Rice / Paddy',
    target_pest_or_disease: 'Rice Blast (Magnaporthe oryzae)',
    escalation_reason: 'Foliar blast spindle-shaped lesions observed across 15% of flag leaves. Early curative fungicide intervention needed before neck blast onset.',
    original_question_transcript: 'వరి ఆకుల మీద కంటి ఆకారంలో బూడిద రంగు మచ్చలు వచ్చాయి. ఈ మచ్చలు కాండం వరకు వెళితే పంట దెబ్బతింటుందా? ఏ మందు కొట్టాలి?',
    audio_recording_url: 'https://bolkisan-audio.telangana.gov.in/recordings/20261108_geesugonda_blast_audio.mp3',
    audio_duration_seconds: 16,
    audio_transcript: {
      original_language: 'te',
      language_label: 'Warangal Rural Telugu',
      original_text: 'వరి ఆకుల మీద కంటి ఆకారంలో బూడిద రంగు మచ్చలు వచ్చాయి. ఈ మచ్చలు కాండం వరకు వెళితే పంట దెబ్బతింటుందా? ఏ మందు కొట్టాలి?',
      phonetic_gloss: 'Vari aakula meeda kanti aakaaramlo boodida rangu macchalu vacchaayi. Ee macchalu kaandam varaku velithe panta debbathintundaa? Ee mandhu kottaali?',
      english_translation: 'Spindle-shaped grey lesions have appeared on paddy leaves. Will it harm the crop if it spreads to the neck? What fungicide should be sprayed?',
      segments: [
        {
          start_ms: 0,
          end_ms: 5000,
          text_te: 'వరి ఆకుల మీద కంటి ఆకారంలో బూడిద రంగు మచ్చలు వచ్చాయి.',
          text_en: 'Spindle eye-shaped grey spots have appeared on rice leaves.'
        },
        {
          start_ms: 5000,
          end_ms: 11000,
          text_te: 'ఈ మచ్చలు కాండం వరకు వెళితే పంట దెబ్బతింటుందా?',
          text_en: 'Will it ruin the crop if lesions spread to the panicle neck?'
        },
        {
          start_ms: 11000,
          end_ms: 16000,
          text_te: 'ఖచ్చితమైన మందు మోతాదు సూచించండి.',
          text_en: 'Please prescribe the exact fungicide and dosage.'
        }
      ]
    },
    attached_image_urls: [
      'https://images.unsplash.com/photo-1530595467537-0b5996c41f2d?auto=format&fit=crop&w=1200&q=80'
    ],
    photo_url: 'https://images.unsplash.com/photo-1530595467537-0b5996c41f2d?auto=format&fit=crop&w=1200&q=80',
    exif_verification: {
      latitude: 17.9712,
      longitude: 79.5985,
      location_name: 'Viswanathpur, Geesugonda Mandal, Warangal',
      captured_at: '2026-11-08T10:05:40+05:30',
      device_model: 'OnePlus Nord CE 3 Lite',
      camera_make: 'Samsung HM6',
      exposure: '1/500s at f/1.7',
      focal_length: '24mm',
      iso: 80,
      is_timestamp_verified: true,
      is_gps_verified: true,
      is_device_sanitized: true,
      tamper_check_status: 'passed'
    },
    photos: [
      {
        url: 'https://images.unsplash.com/photo-1530595467537-0b5996c41f2d?auto=format&fit=crop&w=1200&q=80',
        thumbnail_url: 'https://images.unsplash.com/photo-1530595467537-0b5996c41f2d?auto=format&fit=crop&w=300&q=80',
        caption: 'Rice Flag Leaf: Spindle-shaped blast lesions with grey center and brown necrotic margin',
        tag: 'leaf_dorsal',
        exif: {
          latitude: 17.9712,
          longitude: 79.5985,
          location_name: 'Viswanathpur, Geesugonda',
          captured_at: '2026-11-08T10:05:40+05:30',
          device_model: 'OnePlus Nord CE 3 Lite',
          is_timestamp_verified: true,
          is_gps_verified: true,
          is_device_sanitized: true,
          tamper_check_status: 'passed'
        },
        annotations: [
          {
            id: 'ann-blast-01',
            label: 'Spindle-shaped Blast Lesion',
            x: 45,
            y: 50,
            severity: 'moderate',
            description: 'Active sporulating blast lesion (Magnaporthe oryzae)'
          }
        ]
      }
    ],
    created_at: '2026-11-08T10:10:00+05:30'
  }
];

export const SEED_SUMMARIES: TicketSummary[] = SEED_TICKETS.map(t => ({
  id: t.id,
  ticket_number: t.ticket_number,
  farmer_id: t.farmer.id,
  farmer_name: t.farmer.name,
  farmer_phone: t.farmer.phone,
  state: t.farmer.state,
  district: t.farmer.district,
  mandal: t.farmer.mandal,
  status: t.status as 'open' | 'under_review' | 'resolved' | 'closed',
  priority: t.priority as 'low' | 'medium' | 'high' | 'emergency',
  escalation_reason: t.escalation_reason,
  original_question_transcript: t.original_question_transcript,
  crop_name: t.crop_name,
  target_pest: t.target_pest_or_disease,
  photo_url: t.photo_url || t.attached_image_urls?.[0],
  attached_image_urls: t.attached_image_urls,
  audio_recording_url: t.audio_recording_url,
  has_photo: Boolean(t.photo_url || t.attached_image_urls?.length),
  has_audio: Boolean(t.audio_recording_url),
  resolution_prescription: t.resolution_prescription,
  created_at: t.created_at,
  resolved_at: t.resolved_at
}));
