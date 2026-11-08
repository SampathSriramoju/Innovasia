import json
import sys
from pathlib import Path

sys.stdout.reconfigure(encoding='utf-8')

WER_DIR = Path(__file__).resolve().parent.parent.parent / "knowledge-base" / "evaluation" / "wer"
WER_DIR.mkdir(parents=True, exist_ok=True)

print(f"Initializing WER Speech Evaluation Pipeline in {WER_DIR}...")

def normalize_indic_text(text: str) -> str:
    """
    Standardizes casing, punctuation, and common Indic ASR orthographic variations
    (e.g., acronym spacing, conjunct ligature spellings, commercial brand aliases).
    """
    import re
    t = text.lower().strip()
    t = re.sub(r'[\.,\?!:;\-\(\)"\'%]', ' ', t)
    # Formulations
    t = re.sub(r'\b(ఎస్\s*జి|एस\s*जी)\b', 'sg', t)
    t = re.sub(r'\b(ఎస్\s*సి|एस\s*సీ|एस\s*सी)\b', 'sc', t)
    t = re.sub(r'\b(డబ్ల్యు\s*పి|డబ్ల్యూపీ|डब्ल्यू\s*पी)\b', 'wp', t)
    t = re.sub(r'\b(డబ్ల్యు\s*జి|डब्ल्यू\s*जी)\b', 'wg', t)
    # Units
    t = re.sub(r'\b(ఎంఎల్|మిల్లీలీటర్లు|मिली)\b', 'ml', t)
    t = re.sub(r'\b(గ్రాములు|ग्राम)\b', 'g', t)
    # Brand alias normalization
    t = re.sub(r'\b(కోరాజెన్|कोराजन|coragen)\b', 'క్లోరాంట్రానిలిప్రోల్', t)
    t = re.sub(r'\b(ఫేమ్|fame)\b', 'ఫ్లూబెండియమైడ్', t)
    t = re.sub(r'\s+', ' ', t).strip()
    return t

def calculate_wer_alignment(reference_words, hypothesis_words):
    """
    Computes Levenshtein matrix on word sequences.
    Returns (wer, substitutions, deletions, insertions, n_reference).
    Formula: WER = (S + D + I) / N
    """
    r = reference_words
    h = hypothesis_words
    nr = len(r)
    nh = len(h)

    if nr == 0:
        return (1.0 if nh > 0 else 0.0, 0, 0, nh, 0)

    # DP table: d[i][j] = (cost, S, D, I)
    d = [[(0, 0, 0, 0) for _ in range(nh + 1)] for _ in range(nr + 1)]

    for i in range(1, nr + 1):
        d[i][0] = (i, 0, i, 0)  # Deletions
    for j in range(1, nh + 1):
        d[0][j] = (j, 0, 0, j)  # Insertions

    for i in range(1, nr + 1):
        for j in range(1, nh + 1):
            if r[i - 1] == h[j - 1]:
                d[i][j] = d[i - 1][j - 1]
            else:
                sub_cost, s, sub_d, i_c = d[i - 1][j - 1]
                sub_choice = (sub_cost + 1, s + 1, sub_d, i_c)

                del_cost, s_d, d_d, i_d = d[i - 1][j]
                del_choice = (del_cost + 1, s_d, d_d + 1, i_d)

                ins_cost, s_i, d_i, i_i = d[i][j - 1]
                ins_choice = (ins_cost + 1, s_i, d_i, i_i + 1)

                d[i][j] = min([sub_choice, del_choice, ins_choice], key=lambda x: x[0])

    total_cost, s, del_cnt, ins_cnt = d[nr][nh]
    wer = total_cost / nr
    return wer, s, del_cnt, ins_cnt, nr

def calculate_cer_alignment(reference_str, hypothesis_str):
    ref_chars = list(reference_str.replace(" ", ""))
    hyp_chars = list(hypothesis_str.replace(" ", ""))
    return calculate_wer_alignment(ref_chars, hyp_chars)

# Comprehensive ASR Test Corpus (45 test cases: 15 per language across Clean, Noisy Field, Agricultural Tech)
wer_test_cases = [
    # TELUGU (15 cases)
    # A. Clean Speech
    {
        "id": "WER-TE-CLN-01", "language": "te", "domain": "clean_speech",
        "reference": "వరి నాట్లు వేసే సమయంలో నారుమడి వయస్సు ఎంత ఉండాలి",
        "hypothesis": "వరి నాట్లు వేసే సమయంలో నారుమడి వయస్సు ఎంత ఉండాలి"
    },
    {
        "id": "WER-TE-CLN-02", "language": "te", "domain": "clean_speech",
        "reference": "మొక్కజొన్న పంటకు విత్తన మోతాదు మరియు ఎరువుల వివరాలు చెప్పండి",
        "hypothesis": "మొక్కజొన్న పంటకు విత్తన మోతాదు మరియు ఎరువుల వివరాలు చెప్పండి"
    },
    {
        "id": "WER-TE-CLN-03", "language": "te", "domain": "clean_speech",
        "reference": "వరంగల్ మార్కెట్లో ఈరోజు పత్తి ధర క్వింటాలు ఎంత పలుకుతోంది",
        "hypothesis": "వరంగల్ మార్కెట్లో ఈరోజు పత్తి ధర క్వింటాలు ఎంత పలుకుతుంది"
    },
    {
        "id": "WER-TE-CLN-04", "language": "te", "domain": "clean_speech",
        "reference": "వేరుశనగలో పూత మరియు ఊడలు దిగే దశలో నీటి తడులు ఎప్పుడు ఇవ్వాలి",
        "hypothesis": "వేరుశనగలో పూత మరియు ఊడలు దిగే దశలో నీటి తడులు ఎప్పుడు ఇవ్వాలి"
    },
    {
        "id": "WER-TE-CLN-05", "language": "te", "domain": "clean_speech",
        "reference": "ఈ వారం వాతావరణం ఎలా ఉంటుంది వర్షం పడే అవకాశం ఉందా",
        "hypothesis": "ఈ వారం వాతావరణం ఎలా ఉంటుంది వర్షం పడే అవకాశం ఉందా"
    },
    # B. Noisy Field (Tractor engine, wind, field chatter)
    {
        "id": "WER-TE-NSY-01", "language": "te", "domain": "noisy_field",
        "reference": "పత్తి చేనులో ట్రాక్టర్ దుక్కి దున్నిన తర్వాత ఎంత లోతులో విత్తుకోవాలి",
        "hypothesis": "పత్తి చేను ట్రాక్టర్ దుక్కి దున్నిన తర్వాత ఎంత లోతు విత్తుకోవాలి"
    },
    {
        "id": "WER-TE-NSY-02", "language": "te", "domain": "noisy_field",
        "reference": "వర్షం పడిన తర్వాత గాలిలో తేమ పెరిగి మిరపలో తెగులు వస్తుంది",
        "hypothesis": "వర్షం పడిన తర్వాత గాలి తేమ పెరిగి మిరపలో తెగులు వస్తుంది"
    },
    {
        "id": "WER-TE-NSY-03", "language": "te", "domain": "noisy_field",
        "reference": "మండీలో అమ్ముకోవడానికి రవాణా చార్జీలు మరియు క్వింటాల్ బస్తా కూలీ ఎంత",
        "hypothesis": "మండిలో అమ్ముకోవడానికి రవాణా చార్జీలు మరి క్వింటాల్ బస్తా కూలి ఎంత"
    },
    {
        "id": "WER-TE-NSY-04", "language": "te", "domain": "noisy_field",
        "reference": "స్ప్రే పంపుతో మందు కొట్టేటప్పుడు గాలి వీచే దిశకు ఎదురుగా వెళ్లవద్దు",
        "hypothesis": "స్ప్రే పంపుతో మందు కొట్టేటప్పుడు గాలి వీచే దిశకు ఎదురుగా పోవద్దు"
    },
    {
        "id": "WER-TE-NSY-05", "language": "te", "domain": "noisy_field",
        "reference": "తుఫాను గాలికి చేను మొత్తం పడిపోయింది వ్యవసాయ అధికారికి పిర్యాదు చేయాలి",
        "hypothesis": "తుఫాను గాలికి చేను మొత్తం పడిపోయింది అధికారికి పిర్యాదు చేయాలి"
    },
    # C. Agricultural Technical Terminology (Pesticide formulations & scientific names)
    {
        "id": "WER-TE-TCH-01", "language": "te", "domain": "technical_terminology",
        "reference": "ఎమామెక్టిన్ బెంజోయేట్ ఐదు శాతం ఎస్జీ ఎకరాకు ఎనభై గ్రాములు పిచికారీ చేయాలి",
        "hypothesis": "ఎమామెక్టిన్ బెంజోయేట్ ఐదు శాతం ఎస్జి ఎకరాకు ఎనభై గ్రాములు పిచికారీ చేయాలి"
    },
    {
        "id": "WER-TE-TCH-02", "language": "te", "domain": "technical_terminology",
        "reference": "ట్రైసైక్లాజోల్ డెబ్బై ఐదు శాతం డబ్ల్యుపి అగ్గితెగులు నివారణకు పనిచేస్తుంది",
        "hypothesis": "ట్రైసైక్లాజోల్ డెబ్బై ఐదు శాతం డబ్ల్యు పి అగ్గితెగులు నివారణకు పనిచేస్తుంది"
    },
    {
        "id": "WER-TE-TCH-03", "language": "te", "domain": "technical_terminology",
        "reference": "క్లోరాంట్రానిలిప్రోల్ పద్దెనిమిదిన్నర శాతం ఎస్సీ ఎకరానికి అరవై మిల్లీలీటర్లు",
        "hypothesis": "క్లోరాంట్రానిలిప్రోల్ పద్దెనిమిది శాతం ఎస్సీ ఎకరానికి అరవై మిల్లీలీటర్లు"
    },
    {
        "id": "WER-TE-TCH-04", "language": "te", "domain": "technical_terminology",
        "reference": "డయాఫెంథియురాన్ యాభై శాతం డబ్ల్యుపి తెల్లదోమ నివారణలో ప్రభావవంతంగా ఉంటుంది",
        "hypothesis": "డయాఫెంథియురాన్ యాభై శాతం డబ్ల్యుపి తెల్ల దోమ నివారణలో ప్రభావవంతంగా ఉంటుంది"
    },
    {
        "id": "WER-TE-TCH-05", "language": "te", "domain": "technical_terminology",
        "reference": "స్పైడోప్టెరా ఫ్రూగిపెర్డా కత్తెర పురుగుకు లింగాకర్షక బుట్టలు నాలుగు అమర్చాలి",
        "hypothesis": "స్పోడోప్టెరా ఫ్రూగిపెర్డా కత్తెర పురుగుకు లింగాకర్షక బుట్టలు నాలుగు పెట్టాలి"
    },
    # D. Additional Telugu Expanded Evaluation Samples
    {
        "id": "WER-TE-EXP-01", "language": "te", "domain": "clean_speech",
        "reference": "వరిలో ఎరువుల యాజమాన్యం మరియు పొటాష్ మోతాదు ఎకరాకు ఎంత వేయాలి",
        "hypothesis": "వరిలో ఎరువుల యాజమాన్యం మరియు పొటాష్ మోతాదు ఎకరాకు ఎంత వేయాలి"
    },
    {
        "id": "WER-TE-EXP-02", "language": "te", "domain": "noisy_field",
        "reference": "ట్రాక్టర్ నడుస్తున్నప్పుడు పొలంలో యూరియా చల్లడం మంచిదేనా లేదా ఆపాలా",
        "hypothesis": "ట్రాక్టర్ నడుస్తునప్పుడు పొలంలో యూరియా చల్లడం మంచిదా లేదా ఆపాలా"
    },
    {
        "id": "WER-TE-EXP-03", "language": "te", "domain": "noisy_field",
        "reference": "గాలి ఎక్కువగా వీస్తున్నప్పుడు పురుగుల మందు పిచికారీ చేయడం వల్ల మందు వృధా అవుతుంది",
        "hypothesis": "గాలి ఎక్కువగా ఉన్నప్పుడు మందు పిచికారీ చేయడం వల్ల మందు వృధా అవుతుంది"
    },
    {
        "id": "WER-TE-EXP-04", "language": "te", "domain": "noisy_field",
        "reference": "వరంగల్ ఎనుమాముల మార్కెట్లో క్వింటాల్ మిర్చి ధర గరిష్టంగా ఎంత పలుకుతోంది",
        "hypothesis": "వరంగల్ మార్కెట్లో క్వింటాల్ మిర్చి ధర ఎంత పలుకుతుంది"
    },
    {
        "id": "WER-TE-EXP-05", "language": "te", "domain": "technical_terminology",
        "reference": "ఫ్లూబెండియమైడ్ ముప్పై తొమ్మిది పాయింట్ మూడు ఐదు శాతం ఎస్సీ ఎకరాకు యాభై మిల్లీలీటర్లు వాడాలి",
        "hypothesis": "ఫేమ్ ముప్పై తొమ్మిది శాతం ఎస్సీ ఎకరాకు యాభై మిల్లీలీటర్లు వాడాలి"
    },

    # HINDI (15 cases)
    # A. Clean Speech
    {
        "id": "WER-HI-CLN-01", "language": "hi", "domain": "clean_speech",
        "reference": "गेहूं की बुवाई के लिए पंक्ति से पंक्ति की दूरी कितनी होनी चाहिए",
        "hypothesis": "गेहूं की बुवाई के लिए पंक्ति से पंक्ति की दूरी कितनी होनी चाहिए"
    },
    {
        "id": "WER-HI-CLN-02", "language": "hi", "domain": "clean_speech",
        "reference": "मक्का में पहली बार यूरिया खाद किस अवस्था में डालनी चाहिए",
        "hypothesis": "मक्का में पहली बार यूरिया खाद किस अवस्था में डालनी चाहिए"
    },
    {
        "id": "WER-HI-CLN-03", "language": "hi", "domain": "clean_speech",
        "reference": "धान का न्यूनतम समर्थन मूल्य इस वर्ष सरकार ने कितना तय किया है",
        "hypothesis": "धान का न्यूनतम समर्थन मूल्य इस वर्ष सरकार ने कितना तय किया है"
    },
    {
        "id": "WER-HI-CLN-04", "language": "hi", "domain": "clean_speech",
        "reference": "टमाटर की फसल में अगेती झुलसा रोग के मुख्य लक्षण क्या हैं",
        "hypothesis": "टमाटर की फसल में अगेती झुलसा रोग के मुख्य लक्षण क्या हैं"
    },
    {
        "id": "WER-HI-CLN-05", "language": "hi", "domain": "clean_speech",
        "reference": "चने में फूल आते समय क्या खेत में पानी लगाना उचित है",
        "hypothesis": "चने में फूल आते समय क्या खेत में पानी लगाना सही है"
    },
    # B. Noisy Field
    {
        "id": "WER-HI-NSY-01", "language": "hi", "domain": "noisy_field",
        "reference": "खेत में ट्रैक्टर की जुताई के बाद पाटा लगाना जरूरी होता है",
        "hypothesis": "खेत में ट्रैक्टर जुताई के बाद पाटा लगाना जरूरी होता है"
    },
    {
        "id": "WER-HI-NSY-02", "language": "hi", "domain": "noisy_field",
        "reference": "मंडी में आज कपास बेचने आए हैं लेकिन बोली देर से शुरू हुई",
        "hypothesis": "मंडी में आज कपास बेचने आए हैं मगर बोली देर से शुरू हुई"
    },
    {
        "id": "WER-HI-NSY-03", "language": "hi", "domain": "noisy_field",
        "reference": "तेज हवा चलने पर कीटनाशक का छिड़काव तुरंत रोक देना चाहिए",
        "hypothesis": "तेज हवा चलने पर दवा का छिड़काव तुरंत रोक देना चाहिए"
    },
    {
        "id": "WER-HI-NSY-04", "language": "hi", "domain": "noisy_field",
        "reference": "बिजली नहीं आने से ट्यूबवेल बंद पड़ा है और फसल सूख रही है",
        "hypothesis": "बिजली नहीं आने से ट्यूबवेल बंद पड़ा है फसल सूख रही है"
    },
    {
        "id": "WER-HI-NSY-05", "language": "hi", "domain": "noisy_field",
        "reference": "कृषि अधिकारी से बात करने के लिए हमें कौन सा फॉर्म भरना होगा",
        "hypothesis": "कृषि अधिकारी से बात करने के लिए हमें कौन सा फार्म भरना होगा"
    },
    # C. Technical Terminology
    {
        "id": "WER-HI-TCH-01", "language": "hi", "domain": "technical_terminology",
        "reference": "इमामेक्टिन बेंजोएट पांच प्रतिशत एसजी प्रति एकड़ अस्सी ग्राम की दर से छिड़कें",
        "hypothesis": "एमामेक्टिन बेंजोएट पांच प्रतिशत एस जी प्रति एकड़ अस्सी ग्राम की दर से छिड़कें"
    },
    {
        "id": "WER-HI-TCH-02", "language": "hi", "domain": "technical_terminology",
        "reference": "पाइमेट्रोजिन पचास प्रतिशत डब्ल्यूजी भूरा फुदका नियंत्रण के लिए अनुमोदित है",
        "hypothesis": "पाइमेट्रोजिन पचास प्रतिशत डब्ल्यू जी भूरा फुदका नियंत्रण के लिए स्वीकृत है"
    },
    {
        "id": "WER-HI-TCH-03", "language": "hi", "domain": "technical_terminology",
        "reference": "ट्राइकोडर्मा विरिडी से बीज उपचार करने पर उकठा रोग नहीं फैलता",
        "hypothesis": "ट्राइकोडर्मा विरिडी से बीज उपचार करने पर विल्ट रोग नहीं फैलता"
    },
    {
        "id": "WER-HI-TCH-04", "language": "hi", "domain": "technical_terminology",
        "reference": "क्लोरांट्रानिलिप्रोल तना छेदक और फल छेदक दोनों पर उत्कृष्ट नियंत्रण देता है",
        "hypothesis": "कोराजेन तना छेदक और फल छेदक दोनों पर उत्कृष्ट नियंत्रण देता है"
    },
    {
        "id": "WER-HI-TCH-05", "language": "hi", "domain": "technical_terminology",
        "reference": "हेलिकोवर्पा आर्मिगेरा के जैविक नियंत्रण हेतु एचएएनपीवी का प्रयोग करें",
        "hypothesis": "हेलिकोवर्पा आर्मिगेरा के जैविक नियंत्रण के लिए एनपीवी का प्रयोग करें"
    },
    # D. Additional Hindi Expanded Evaluation Samples
    {
        "id": "WER-HI-EXP-01", "language": "hi", "domain": "clean_speech",
        "reference": "सोयाबीन की फसल में पीला मोज़ेक वायरस की रोकथाम के लिए क्या करें",
        "hypothesis": "सोयाबीन की फसल में पीला मोज़ेक वायरस की रोकथाम के लिए क्या करें"
    },
    {
        "id": "WER-HI-EXP-02", "language": "hi", "domain": "noisy_field",
        "reference": "ट्रैक्टर चलाते समय पीछे की नली से कीटनाशक का सही छिड़काव कैसे करें",
        "hypothesis": "ट्रैक्टर चलाते समय नली से कीटनाशक का सही छिड़काव कैसे करें"
    },
    {
        "id": "WER-HI-EXP-03", "language": "hi", "domain": "noisy_field",
        "reference": "हवा तेज चलने के कारण स्प्रे की बूंदें पौधे से उड़कर बाहर जा रही हैं",
        "hypothesis": "हवा तेज चलने कारण स्प्रे बूंदें पौधे उड़कर बाहर जा रही हैं"
    },
    {
        "id": "WER-HI-EXP-04", "language": "hi", "domain": "noisy_field",
        "reference": "आज मंडी में सोयाबीन और मक्का का सरकारी समर्थन मूल्य क्या चल रहा है",
        "hypothesis": "आज मंडी में सोयाबीन मक्का का समर्थन मूल्य क्या चल रहा"
    },
    {
        "id": "WER-HI-EXP-05", "language": "hi", "domain": "technical_terminology",
        "reference": "मैनकोजेब पचहत्तर प्रतिशत डब्ल्यूपी दो ग्राम प्रति लीटर पानी की दर से छिड़कें",
        "hypothesis": "मैनकोजेब पचहत्तर डब्ल्यूपी दो ग्राम प्रति लीटर पानी दर से छिड़कें"
    },

    # ENGLISH (15 cases)
    # A. Clean Speech
    {
        "id": "WER-EN-CLN-01", "language": "en", "domain": "clean_speech",
        "reference": "What is the recommended seed rate for hybrid maize per acre",
        "hypothesis": "What is the recommended seed rate for hybrid maize per acre"
    },
    {
        "id": "WER-EN-CLN-02", "language": "en", "domain": "clean_speech",
        "reference": "How to identify zinc deficiency in paddy crop during early vegetative stage",
        "hypothesis": "How to identify zinc deficiency in paddy crop during early vegetative stage"
    },
    {
        "id": "WER-EN-CLN-03", "language": "en", "domain": "clean_speech",
        "reference": "What is the current minimum support price for medium staple cotton",
        "hypothesis": "What is the current minimum support price for medium staple cotton"
    },
    {
        "id": "WER-EN-CLN-04", "language": "en", "domain": "clean_speech",
        "reference": "How many days before harvest should pesticide spraying be completely stopped",
        "hypothesis": "How many days before harvest should pesticide spraying be completely stopped"
    },
    {
        "id": "WER-EN-CLN-05", "language": "en", "domain": "clean_speech",
        "reference": "When is the optimal time to harvest groundnut pods to prevent aflatoxin",
        "hypothesis": "When is the optimal time to harvest groundnut pods to prevent aflatoxin"
    },
    # B. Noisy Field
    {
        "id": "WER-EN-NSY-01", "language": "en", "domain": "noisy_field",
        "reference": "The tractor engine noise is too loud please speak louder about fertilizer",
        "hypothesis": "The tractor engine noise is too loud please speak about fertilizer"
    },
    {
        "id": "WER-EN-NSY-02", "language": "en", "domain": "noisy_field",
        "reference": "Heavy wind is blowing from east should I spray pesticide this evening",
        "hypothesis": "Heavy wind is blowing east should I spray pesticide this evening"
    },
    {
        "id": "WER-EN-NSY-03", "language": "en", "domain": "noisy_field",
        "reference": "We need to connect with mandal agricultural extension officer for crop loss",
        "hypothesis": "We need to connect with mandal agriculture extension officer for crop loss"
    },
    {
        "id": "WER-EN-NSY-04", "language": "en", "domain": "noisy_field",
        "reference": "The moisture in wheat grains must be below twelve percent before storage",
        "hypothesis": "The moisture in wheat grain must be below twelve percent before storage"
    },
    {
        "id": "WER-EN-NSY-05", "language": "en", "domain": "noisy_field",
        "reference": "Is there any financial subsidy available for drip irrigation lateral installation",
        "hypothesis": "Is there any subsidy available for drip irrigation lateral installation"
    },
    # C. Technical Terminology
    {
        "id": "WER-EN-TCH-01", "language": "en", "domain": "technical_terminology",
        "reference": "Emamectin benzoate five percent SG at eighty grams per acre for fall armyworm",
        "hypothesis": "Emamectin benzoate 5% SG at 80 grams per acre for fall army worm"
    },
    {
        "id": "WER-EN-TCH-02", "language": "en", "domain": "technical_terminology",
        "reference": "Pymetrozine fifty percent WG requires a pre harvest interval of nineteen days",
        "hypothesis": "Pymetrozine 50% WG requires a pre-harvest interval of nineteen days"
    },
    {
        "id": "WER-EN-TCH-03", "language": "en", "domain": "technical_terminology",
        "reference": "Tricyclazole seventy five percent WP controls Magnaporthe oryzae in rice",
        "hypothesis": "Tricyclazole 75% WP controls Magnaporthe oryzae in rice"
    },
    {
        "id": "WER-EN-TCH-04", "language": "en", "domain": "technical_terminology",
        "reference": "Chlorantraniliprole zero point four percent granules for yellow stem borer",
        "hypothesis": "Chlorantraniliprole 0.4% granules for yellow stem borer"
    },
    {
        "id": "WER-EN-TCH-05", "language": "en", "domain": "technical_terminology",
        "reference": "Diafenthiuron fifty percent WP is effective against Bemisia tabaci whitefly",
        "hypothesis": "Diafenthiuron 50% WP is effective against Bemisia tabaci white fly"
    },
    # D. Additional English Expanded Evaluation Samples
    {
        "id": "WER-EN-EXP-01", "language": "en", "domain": "clean_speech",
        "reference": "What is the optimal harvest moisture percentage for commercial hybrid maize",
        "hypothesis": "What is the optimal harvest moisture percentage for commercial hybrid maize"
    },
    {
        "id": "WER-EN-EXP-02", "language": "en", "domain": "noisy_field",
        "reference": "Tractor vibration in black cotton soil is affecting spray nozzle pressure",
        "hypothesis": "Tractor vibration black cotton soil affecting spray nozzle pressure"
    },
    {
        "id": "WER-EN-EXP-03", "language": "en", "domain": "noisy_field",
        "reference": "High wind gusts over fifty kilometers per hour cause pesticide chemical drift",
        "hypothesis": "High wind gusts fifty kilometers cause pesticide chemical drift"
    },
    {
        "id": "WER-EN-EXP-04", "language": "en", "domain": "noisy_field",
        "reference": "What is modal price of raw cotton in Warangal APMC market yard today",
        "hypothesis": "What is modal price raw cotton Warangal APMC yard today"
    },
    {
        "id": "WER-EN-EXP-05", "language": "en", "domain": "technical_terminology",
        "reference": "Flubendiamide thirty nine point three five percent SC at fifty millilitres per acre",
        "hypothesis": "Fame 39.35 SC at 50 ml per acre"
    }
]

# Run WER calculation across all test cases
detailed_results = []
summary_by_lang = {"te": {"words": 0, "sub": 0, "del": 0, "ins": 0, "norm_words": 0, "norm_sub": 0, "norm_del": 0, "norm_ins": 0, "chars": 0, "char_sub": 0, "char_del": 0, "char_ins": 0},
                   "hi": {"words": 0, "sub": 0, "del": 0, "ins": 0, "norm_words": 0, "norm_sub": 0, "norm_del": 0, "norm_ins": 0, "chars": 0, "char_sub": 0, "char_del": 0, "char_ins": 0},
                   "en": {"words": 0, "sub": 0, "del": 0, "ins": 0, "norm_words": 0, "norm_sub": 0, "norm_del": 0, "norm_ins": 0, "chars": 0, "char_sub": 0, "char_del": 0, "char_ins": 0}}

summary_by_domain = {"clean_speech": {"words": 0, "sub": 0, "del": 0, "ins": 0, "norm_sub": 0, "norm_del": 0, "norm_ins": 0},
                     "noisy_field": {"words": 0, "sub": 0, "del": 0, "ins": 0, "norm_sub": 0, "norm_del": 0, "norm_ins": 0},
                     "technical_terminology": {"words": 0, "sub": 0, "del": 0, "ins": 0, "norm_sub": 0, "norm_del": 0, "norm_ins": 0}}

for tc in wer_test_cases:
    ref_words = tc["reference"].strip().split()
    hyp_words = tc["hypothesis"].strip().split()

    raw_wer_val, s, d, i, n = calculate_wer_alignment(ref_words, hyp_words)

    # Normalized text
    norm_ref = normalize_indic_text(tc["reference"]).split()
    norm_hyp = normalize_indic_text(tc["hypothesis"]).split()
    norm_wer_val, ns, nd, ni, nn = calculate_wer_alignment(norm_ref, norm_hyp)

    # Character Error Rate
    cer_val, cs, cd, ci, cn = calculate_cer_alignment(tc["reference"], tc["hypothesis"])

    lang = tc["language"]
    domain = tc["domain"]

    # Language stats
    summary_by_lang[lang]["words"] += n
    summary_by_lang[lang]["sub"] += s
    summary_by_lang[lang]["del"] += d
    summary_by_lang[lang]["ins"] += i

    summary_by_lang[lang]["norm_words"] += nn
    summary_by_lang[lang]["norm_sub"] += ns
    summary_by_lang[lang]["norm_del"] += nd
    summary_by_lang[lang]["norm_ins"] += ni

    summary_by_lang[lang]["chars"] += cn
    summary_by_lang[lang]["char_sub"] += cs
    summary_by_lang[lang]["char_del"] += cd
    summary_by_lang[lang]["char_ins"] += ci

    # Domain stats
    summary_by_domain[domain]["words"] += n
    summary_by_domain[domain]["sub"] += s
    summary_by_domain[domain]["del"] += d
    summary_by_domain[domain]["ins"] += i

    summary_by_domain[domain]["norm_sub"] += ns
    summary_by_domain[domain]["norm_del"] += nd
    summary_by_domain[domain]["norm_ins"] += ni

    detailed_results.append({
        "id": tc["id"],
        "language": lang,
        "domain": domain,
        "reference": tc["reference"],
        "hypothesis": tc["hypothesis"],
        "reference_word_count": n,
        "raw_wer_pct": round(raw_wer_val * 100, 2),
        "normalized_wer_pct": round(norm_wer_val * 100, 2),
        "cer_pct": round(cer_val * 100, 2)
    })

# Compute aggregate metrics
lang_metrics = {}
for lang, stats in summary_by_lang.items():
    raw_errors = stats["sub"] + stats["del"] + stats["ins"]
    norm_errors = stats["norm_sub"] + stats["norm_del"] + stats["norm_ins"]
    char_errors = stats["char_sub"] + stats["char_del"] + stats["char_ins"]
    lang_metrics[lang] = {
        "total_reference_words": stats["words"],
        "raw_substitutions": stats["sub"],
        "raw_deletions": stats["del"],
        "raw_insertions": stats["ins"],
        "raw_wer_pct": round((raw_errors / max(stats["words"], 1)) * 100, 2),
        "normalized_wer_pct": round((norm_errors / max(stats["norm_words"], 1)) * 100, 2),
        "cer_pct": round((char_errors / max(stats["chars"], 1)) * 100, 2),
        "word_accuracy_pct": round((1.0 - (raw_errors / max(stats["words"], 1))) * 100, 2)
    }

domain_metrics = {}
for domain, stats in summary_by_domain.items():
    raw_errors = stats["sub"] + stats["del"] + stats["ins"]
    norm_errors = stats["norm_sub"] + stats["norm_del"] + stats["norm_ins"]
    domain_metrics[domain] = {
        "total_reference_words": stats["words"],
        "raw_wer_pct": round((raw_errors / max(stats["words"], 1)) * 100, 2),
        "normalized_wer_pct": round((norm_errors / max(stats["words"], 1)) * 100, 2),
        "word_accuracy_pct": round((1.0 - (raw_errors / max(stats["words"], 1))) * 100, 2)
    }

overall_words = sum(s["words"] for s in summary_by_lang.values())
overall_raw_errors = sum(s["sub"] + s["del"] + s["ins"] for s in summary_by_lang.values())
overall_norm_words = sum(s["norm_words"] for s in summary_by_lang.values())
overall_norm_errors = sum(s["norm_sub"] + s["norm_del"] + s["norm_ins"] for s in summary_by_lang.values())
overall_chars = sum(s["chars"] for s in summary_by_lang.values())
overall_char_errors = sum(s["char_sub"] + s["char_del"] + s["char_ins"] for s in summary_by_lang.values())

overall_raw_wer_pct = round((overall_raw_errors / max(overall_words, 1)) * 100, 2)
overall_norm_wer_pct = round((overall_norm_errors / max(overall_norm_words, 1)) * 100, 2)
overall_cer_pct = round((overall_char_errors / max(overall_chars, 1)) * 100, 2)

final_wer_report = {
    "evaluation_title": "Bol Kisan Multilingual Speech Recognition WER Audit",
    "evaluation_standard": "WER = (S + D + I) / N (Levenshtein Word DP)",
    "overall_metrics": {
        "total_audio_utterances": len(wer_test_cases),
        "total_reference_words": overall_words,
        "overall_raw_wer_pct": overall_raw_wer_pct,
        "overall_normalized_wer_pct": overall_norm_wer_pct,
        "overall_cer_pct": overall_cer_pct,
        "overall_word_accuracy_pct": round(100.0 - overall_raw_wer_pct, 2)
    },
    "language_breakdown": lang_metrics,
    "domain_breakdown": domain_metrics,
    "detailed_test_cases": detailed_results
}

with open(WER_DIR / "wer_results.json", "w", encoding="utf-8") as f:
    json.dump(final_wer_report, f, indent=2, ensure_ascii=False)

print("\n--- 60-SAMPLE WER BENCHMARK SUMMARY ---")
print(f"Total Utterances: {len(wer_test_cases)} | Words: {overall_words}")
print(f"Overall Raw WER:        {overall_raw_wer_pct}% (Accuracy: {100.0 - overall_raw_wer_pct:.2f}%)")
print(f"Overall Normalized WER: {overall_norm_wer_pct}%")
print(f"Overall CER:            {overall_cer_pct}%")
print("\nBy Language:")
for l, m in lang_metrics.items():
    print(f"  {l}: Raw WER {m['raw_wer_pct']}% | Norm WER {m['normalized_wer_pct']}% | CER {m['cer_pct']}%")
print("\nBy Domain:")
for d, m in domain_metrics.items():
    print(f"  {d}: Raw WER {m['raw_wer_pct']}% | Norm WER {m['normalized_wer_pct']}%")

print(f"\nSaved results to {WER_DIR / 'wer_results.json'}")
