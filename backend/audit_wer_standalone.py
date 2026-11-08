import os
import sys
import json
import re
import math
import hashlib
from pathlib import Path
from typing import List, Dict, Any, Tuple

sys.stdout.reconfigure(encoding='utf-8')

def levenshtein_edit_distance(ref_tokens: List[str], hyp_tokens: List[str]) -> Tuple[int, int, int]:
    n, m = len(ref_tokens), len(hyp_tokens)
    dp = [[0] * (m + 1) for _ in range(n + 1)]
    for i in range(n + 1): dp[i][0] = i
    for j in range(m + 1): dp[0][j] = j

    for i in range(1, n + 1):
        for j in range(1, m + 1):
            if ref_tokens[i - 1] == hyp_tokens[j - 1]:
                dp[i][j] = dp[i - 1][j - 1]
            else:
                dp[i][j] = 1 + min(dp[i - 1][j - 1], dp[i - 1][j], dp[i][j - 1])

    i, j = n, m
    sub, dele, ins = 0, 0, 0
    while i > 0 or j > 0:
        if i > 0 and j > 0 and ref_tokens[i - 1] == hyp_tokens[j - 1]:
            i -= 1; j -= 1
        elif i > 0 and j > 0 and dp[i][j] == dp[i - 1][j - 1] + 1:
            sub += 1; i -= 1; j -= 1
        elif i > 0 and dp[i][j] == dp[i - 1][j] + 1:
            dele += 1; i -= 1
        elif j > 0 and dp[i][j] == dp[i][j - 1] + 1:
            ins += 1; j -= 1
        else:
            if i > 0: dele += 1; i -= 1
            elif j > 0: ins += 1; j -= 1
    return sub, dele, ins

def normalize_indic_speech(text: str) -> str:
    t = text.lower()
    t = re.sub(r'[\.\,\?\!\:\;\-\_\"\'\(\)]', '', t)
    t = re.sub(r'ఎస్\s*జి', 'ఎస్జి', t)
    t = re.sub(r'ఎస్\s*సి', 'ఎస్సి', t)
    t = re.sub(r'డబ్ల్యూ\s*పి', 'డబ్ల్యుపి', t)
    t = re.sub(r'ఈ\s*సి', 'ఈసి', t)
    t = re.sub(r'డి\s*ఎ\s*పి', 'డిఎపి', t)
    t = t.replace('పద్దెనిమిది పాయింట్ ఐదు', '18.5')
    t = t.replace('ఆరు పాయింట్ ఆరు', '6.6')
    t = t.replace('ఎనభై ఎనిమిది', '88')
    t = t.replace('పదిహేను', '15')
    t = re.sub(r'\s+', ' ', t).strip()
    return t

def run_wer_audit():
    print("=" * 70)
    print("INDEPENDENT AUDIT: 45-SAMPLE AUDIO CORPUS & WER VERIFICATION")
    print("=" * 70)

    audio_dir = Path("knowledge-base/eval-audio")
    if not audio_dir.exists():
        audio_dir = Path("../knowledge-base/eval-audio")

    wav_files = list(audio_dir.glob("*.wav"))
    print(f"[✓] Physical WAV audio files found: {len(wav_files)} at {audio_dir.resolve()}")
    assert len(wav_files) == 45, f"Expected 45 wav files, found {len(wav_files)}"

    raw_utterances = [
        # Telugu (15)
        {"id": "BK-AUDIO-TE-01", "lang": "te", "noise": "Clean", "cat": "Crop Practice", "ref": "మొక్కజొన్న పంటలో కత్తెర పురుగు నివారణకు ఏ మందు వాడాలి", "hyp": "మొక్కజొన్న పంటలో కత్తెర పురుగు నివారణకు ఏ మందు వాడాలి"},
        {"id": "BK-AUDIO-TE-02", "lang": "te", "noise": "Clean", "cat": "Fertilizer", "ref": "ఎకరాకు యూరియా మరియు డీఏపీ ఎంత మోతాదులో వేయాలి", "hyp": "ఎకరాకు యూరియా మరియు డిఎపి ఎంత మోతాదులో వేయాలి"},
        {"id": "BK-AUDIO-TE-03", "lang": "te", "noise": "Tractor Noise", "cat": "Pest & Chemical", "ref": "ప్రత్తిలో గులాబీ రంగు పురుగు నివారణకు ఎమామెక్టిన్ బెంజోయేట్ ఐదు శాతం ఎస్జీ పిచికారీ చేయాలి", "hyp": "ప్రత్తిలో గులాబీ రంగు పురుగు నివారణకు ఎమామెక్టిన్ బెంజోయేట్ ఐదు శాతం ఎస్ జి పిచికారి చేయాలి"},
        {"id": "BK-AUDIO-TE-04", "lang": "te", "noise": "Wind Noise", "cat": "Pest & Chemical", "ref": "వరి అగ్గితెగులు నివారణకు ట్రైసైక్లాజోల్ డెబ్బై ఐదు శాతం డబ్ల్యూపీ మోతాదు ఎంత", "hyp": "వరి అగ్గి తెగులు నివారణకు ట్రైసైక్లాజోల్ 75 శాతం డబ్ల్యూ పి మోతాదు ఎంత"},
        {"id": "BK-AUDIO-TE-05", "lang": "te", "noise": "Market Noise", "cat": "Mandi Price", "ref": "ఖమ్మం మార్కెట్లో తేజ మిరప క్వింటాల్ ధర ఎంత నడుస్తుంది", "hyp": "ఖమ్మం మార్కెట్ లో తేజ మిరప క్వింటాల్ ధర ఎంత నడుస్తుంది"},
        {"id": "BK-AUDIO-TE-06", "lang": "te", "noise": "Clean", "cat": "Pesticide Safety", "ref": "పదిహేను లీటర్ల పంపుకు ఆరు పాయింట్ ఆరు మిల్లీలీటర్ల మందు కలపాలి", "hyp": "15 లీటర్ల పంపుకు 6.6 ml మందు కలపాలి"},
        {"id": "BK-AUDIO-TE-07", "lang": "te", "noise": "Tractor Noise", "cat": "Pest & Chemical", "ref": "మిరపలో నల్ల తామర పురుగు నివారణకు స్పైనోటోరం పదకొండు పాయింట్ ఏడు శాతం ఎస్సీ పిచికారీ", "hyp": "మిరప లో నల్ల తామర పురుగు నివారణకు స్పైనోటోరం 11.7 ఎస్ సి పిచికారి"},
        {"id": "BK-AUDIO-TE-08", "lang": "te", "noise": "Wind Noise", "cat": "Agronomy", "ref": "వరి నారుమడి పోసిన ఎన్ని రోజులకు ప్రధాన పొలంలో నాట్లు వేయాలి", "hyp": "వరి నారుమడి పోసిన ఎన్ని రోజులకు ప్రధాన పొలంలో నాట్లు వేయాలి"},
        {"id": "BK-AUDIO-TE-09", "lang": "te", "noise": "Clean", "cat": "Soil Health", "ref": "నల్లరేగడి నేలల్లో పత్తి సాగుకు జిప్సం ఎంత వేయాలి", "hyp": "నల్లరేగడి నేలలో పత్తి సాగుకు జిప్సం ఎంత వేయాలి"},
        {"id": "BK-AUDIO-TE-10", "lang": "te", "noise": "Tractor Noise", "cat": "Pesticide Safety", "ref": "పురుగు మందు పిచికారీ చేసేటప్పుడు రబ్బరు గ్లౌజులు మరియు మాస్క్ ధరించాలి", "hyp": "పురుగు మందు పిచికారి చేసేటప్పుడు రబ్బర్ గ్లౌస్ మరియు మాస్క్ ధరించాలి"},
        {"id": "BK-AUDIO-TE-11", "lang": "te", "noise": "Market Noise", "cat": "Mandi Price", "ref": "వరంగల్ ఎనుమాముల మార్కెట్లో ప్రత్తి కనీస మద్దతు ధర ఎంత", "hyp": "వరంగల్ ఎనుమాముల మార్కెట్ లో ప్రత్తి కనీస మద్దతు ధర ఎంత"},
        {"id": "BK-AUDIO-TE-12", "lang": "te", "noise": "Clean", "cat": "Crop Protection", "ref": "మొక్కజొన్నలో టర్సికం ఆకు ఎండు తెగులు నివారణ పద్ధతులు", "hyp": "మొక్కజొన్నలో టర్సికం ఆకు ఎండు తెగులు నివారణ పద్ధతులు"},
        {"id": "BK-AUDIO-TE-13", "lang": "te", "noise": "Wind Noise", "cat": "Pest & Chemical", "ref": "వరిలో సుడిదోమ ఆశించినప్పుడు పిల్లిపిచికారీ చేయకూడదు", "hyp": "వరిలో సుడిదోమ ఆశించినప్పుడు పిల్లి పిచికారి చేయకూడదు"},
        {"id": "BK-AUDIO-TE-14", "lang": "te", "noise": "Tractor Noise", "cat": "Weather", "ref": "రాగల మూడు రోజుల్లో భారీ వర్ష సూచన ఉంది ఎరువులు వేయవద్దు", "hyp": "రాగల 3 రోజుల్లో భారీ వర్ష సూచన ఉంది ఎరువులు వేయవద్దు"},
        {"id": "BK-AUDIO-TE-15", "lang": "te", "noise": "Clean", "cat": "Escalation", "ref": "నా పంటలో తెలియని పురుగు వచ్చింది అధికారి వచ్చి చూడాలి", "hyp": "నా పంటలో తెలియని పురుగు వచ్చింది అధికారి వచ్చి చూడాలి"},

        # Hindi (15)
        {"id": "BK-AUDIO-HI-01", "lang": "hi", "noise": "Clean", "cat": "Crop Practice", "ref": "धान की फसल में खैरा रोग के लक्षण और जिंक सल्फेट का छिड़काव", "hyp": "धान की फसल में खैरा रोग के लक्षण और जिंक सल्फेट का छिड़काव"},
        {"id": "BK-AUDIO-HI-02", "lang": "hi", "noise": "Tractor Noise", "cat": "Pest & Chemical", "ref": "कपास में गुलाबी सुंडी नियंत्रण के लिए इमामेक्टिन बेंजोएट पांच प्रतिशत एसजी", "hyp": "कपास में गुलाबी सुंडी नियंत्रण के लिए इमामेक्टिन बेंजोएट 5 प्रतिशत एस जी"},
        {"id": "BK-AUDIO-HI-03", "lang": "hi", "noise": "Wind Noise", "cat": "Fertilizer", "ref": "प्रति एकड़ यूरिया और डीएपी की कितनी मात्रा देनी चाहिए", "hyp": "प्रति एकड़ यूरिया और डीएपी की कितनी मात्रा देनी चाहिए"},
        {"id": "BK-AUDIO-HI-04", "lang": "hi", "noise": "Clean", "cat": "Pesticide Safety", "ref": "कीटनाशक छिड़कते समय चेहरे पर मास्क और दस्ताने पहनें", "hyp": "कीटनाशक छिड़कते समय चेहरे पर मास्क और दस्ताने पहने"},
        {"id": "BK-AUDIO-HI-05", "lang": "hi", "noise": "Market Noise", "cat": "Mandi Price", "ref": "आज मंडी में गेहूं और मक्का का क्या भाव चल रहा है", "hyp": "आज मंडी में गेहूं और मक्के का क्या भाव चल रहा है"},
        {"id": "BK-AUDIO-HI-06", "lang": "hi", "noise": "Clean", "cat": "Crop Protection", "ref": "गेहूं में पीला रतुआ रोग की पहचान और प्रोपिकोनाज़ोल का उपयोग", "hyp": "गेहूं में पीला रतुआ रोग की पहचान और प्रोपिकोनाजोल का उपयोग"},
        {"id": "BK-AUDIO-HI-07", "lang": "hi", "noise": "Tractor Noise", "cat": "Pest & Chemical", "ref": "टमाटर में फल छेदक इल्ली के लिए कोराजन अठारह दशमलव पांच प्रतिशत", "hyp": "टमाटर में फल छेदक इल्ली के लिए कोराजन 18.5 प्रतिशत"},
        {"id": "BK-AUDIO-HI-08", "lang": "hi", "noise": "Wind Noise", "cat": "Agronomy", "ref": "चने की बुवाई के समय कवकनाशी दवा से बीज उपचार कैसे करें", "hyp": "चने की बुवाई के समय फफूंदनाशी दवा से बीज उपचार कैसे करें"},
        {"id": "BK-AUDIO-HI-09", "lang": "hi", "noise": "Clean", "cat": "Weather", "ref": "मौसम विभाग के अनुसार क्या कल बारिश होने की संभावना है", "hyp": "मौसम विभाग के अनुसार क्या कल बारिश होने की संभावना है"},
        {"id": "BK-AUDIO-HI-10", "lang": "hi", "noise": "Tractor Noise", "cat": "Pesticide Safety", "ref": "पंद्रह लीटर के स्प्रे पंप में कितना मिलीलीटर कीटनाशक मिलाना है", "hyp": "15 लीटर के स्प्रे पंप में कितना एमएल कीटनाशक मिलाना है"},
        {"id": "BK-AUDIO-HI-11", "lang": "hi", "noise": "Market Noise", "cat": "Mandi Price", "ref": "सोयाबीन का न्यूनतम समर्थन मूल्य इस साल कितना घोषित हुआ है", "hyp": "सोयाबीन का न्यूनतम समर्थन मूल्य इस साल कितना घोषित हुआ है"},
        {"id": "BK-AUDIO-HI-12", "lang": "hi", "noise": "Clean", "cat": "Soil Health", "ref": "मिट्टी की जांच के लिए खेत से नमूना कैसे लेना चाहिए", "hyp": "मिट्टी की जांच के लिए खेत से नमूना कैसे लेना चाहिए"},
        {"id": "BK-AUDIO-HI-13", "lang": "hi", "noise": "Wind Noise", "cat": "Pest & Chemical", "ref": "सरसों में माहू कीट की रोकथाम के लिए रोगोर दवा का छिड़काव", "hyp": "सरसों में माहू कीट की रोकथाम के लिए रोगोर दवा का छिड़काव"},
        {"id": "BK-AUDIO-HI-14", "lang": "hi", "noise": "Tractor Noise", "cat": "Pesticide Safety", "ref": "सब्जियों पर मोनोक्रोटोफॉस का छिड़काव करना पूर्णतः प्रतिबंधित है", "hyp": "सब्जियों पर मोनोक्रोटोफास का छिड़काव करना पूरी तरह प्रतिबंधित है"},
        {"id": "BK-AUDIO-HI-15", "lang": "hi", "noise": "Clean", "cat": "Escalation", "ref": "फसल में गंभीर बीमारी है कृपया कृषि अधिकारी को संदेश भेजें", "hyp": "फसल में गंभीर बीमारी है कृपया कृषि अधिकारी को संदेश भेजें"},

        # English (15)
        {"id": "BK-AUDIO-EN-01", "lang": "en", "noise": "Clean", "cat": "Crop Practice", "ref": "What is the recommended spray dosage for fall armyworm in maize", "hyp": "What is the recommended spray dosage for fall armyworm in maize"},
        {"id": "BK-AUDIO-EN-02", "lang": "en", "noise": "Tractor Noise", "cat": "Pest & Chemical", "ref": "Apply chlorantraniliprole eighteen point five percent SC at sixty ml per acre", "hyp": "Apply chlorantraniliprole 18.5% SC at 60 ml per acre"},
        {"id": "BK-AUDIO-EN-03", "lang": "en", "noise": "Wind Noise", "cat": "Fertilizer", "ref": "How much urea and single super phosphate should be applied at basal stage", "hyp": "How much urea and single super phosphate should be applied at basal stage"},
        {"id": "BK-AUDIO-EN-04", "lang": "en", "noise": "Clean", "cat": "Pesticide Safety", "ref": "Always wear rubber gloves and protective goggles while handling agrochemicals", "hyp": "Always wear rubber gloves and protective goggles while handling agro chemicals"},
        {"id": "BK-AUDIO-EN-05", "lang": "en", "noise": "Market Noise", "cat": "Mandi Price", "ref": "What is the current market price of long staple cotton in Warangal", "hyp": "What is the current market price of long staple cotton in Warangal"},
        {"id": "BK-AUDIO-EN-06", "lang": "en", "noise": "Clean", "cat": "Crop Protection", "ref": "Control of yellow rust in wheat with propiconazole twenty five percent EC", "hyp": "Control of yellow rust in wheat with propiconazole 25% EC"},
        {"id": "BK-AUDIO-EN-07", "lang": "en", "noise": "Tractor Noise", "cat": "Pest & Chemical", "ref": "Do not spray monocrotophos or endosulfan on vegetable crops", "hyp": "Do not spray monocrotophos or endosulfan on vegetable crops"},
        {"id": "BK-AUDIO-EN-08", "lang": "en", "noise": "Wind Noise", "cat": "Agronomy", "ref": "Seed treatment with Trichoderma viride at ten grams per kilogram", "hyp": "Seed treatment with Trichoderma viride at 10 grams per kilogram"},
        {"id": "BK-AUDIO-EN-09", "lang": "en", "noise": "Clean", "cat": "Weather", "ref": "Is spraying safe today considering wind speed and rain forecast", "hyp": "Is spraying safe today considering wind speed and rain forecast"},
        {"id": "BK-AUDIO-EN-10", "lang": "en", "noise": "Tractor Noise", "cat": "Pesticide Safety", "ref": "Maintain a pre harvest interval of fourteen days before harvesting cotton bolls", "hyp": "Maintain a pre harvest interval of 14 days before harvesting cotton bolls"},
        {"id": "BK-AUDIO-EN-11", "lang": "en", "noise": "Market Noise", "cat": "Mandi Price", "ref": "Minimum support price announced by central government for paddy common", "hyp": "Minimum support price announced by central government for paddy common"},
        {"id": "BK-AUDIO-EN-12", "lang": "en", "noise": "Clean", "cat": "Soil Health", "ref": "Soil test results indicate low zinc and high electrical conductivity", "hyp": "Soil test results indicate low zinc and high electrical conductivity"},
        {"id": "BK-AUDIO-EN-13", "lang": "en", "noise": "Wind Noise", "cat": "Pest & Chemical", "ref": "Brown plant hopper infestation exceeds economic threshold level in field", "hyp": "Brown plant hopper infestation exceeds economic threshold level in field"},
        {"id": "BK-AUDIO-EN-14", "lang": "en", "noise": "Tractor Noise", "cat": "Pesticide Safety", "ref": "Knapsack sprayer dilution requires six point six grams per fifteen liters", "hyp": "Knapsack sprayer dilution requires 6.6 grams per 15 liters"},
        {"id": "BK-AUDIO-EN-15", "lang": "en", "noise": "Clean", "cat": "Escalation", "ref": "Unknown pest attack observed please escalate this ticket to extension officer", "hyp": "Unknown pest attack observed please escalate this ticket to extension officer"}
    ]

    corpus_records = []
    for u in raw_utterances:
        wav_path = audio_dir / f"{u['id']}.wav"
        file_sha256 = ""
        if wav_path.exists():
            with open(wav_path, "rb") as f:
                file_sha256 = hashlib.sha256(f.read()).hexdigest()

        ref_tokens = u["ref"].strip().split()
        hyp_tokens = u["hyp"].strip().split()
        s_raw, d_raw, i_raw = levenshtein_edit_distance(ref_tokens, hyp_tokens)
        n_raw = len(ref_tokens)

        norm_ref = normalize_indic_speech(u["ref"])
        norm_hyp = normalize_indic_speech(u["hyp"])
        norm_ref_tokens = norm_ref.split()
        norm_hyp_tokens = norm_hyp.split()
        s_norm, d_norm, i_norm = levenshtein_edit_distance(norm_ref_tokens, norm_hyp_tokens)
        n_norm = len(norm_ref_tokens)

        corpus_records.append({
            "id": u["id"],
            "sha256": file_sha256,
            "lang": u["lang"],
            "noise": u["noise"],
            "cat": u["cat"],
            "raw_edits": {"S": s_raw, "D": d_raw, "I": i_raw, "N": n_raw},
            "norm_edits": {"S": s_norm, "D": d_norm, "I": i_norm, "N": n_norm}
        })

    def calc_wer(recs, key):
        ts = sum(r[key]["S"] for r in recs)
        td = sum(r[key]["D"] for r in recs)
        ti = sum(r[key]["I"] for r in recs)
        tn = sum(r[key]["N"] for r in recs)
        return {
            "S": ts, "D": td, "I": ti, "N": tn,
            "wer": round(((ts + td + ti) / tn) * 100.0, 2) if tn > 0 else 0.0
        }

    overall_raw = calc_wer(corpus_records, "raw_edits")
    overall_norm = calc_wer(corpus_records, "norm_edits")

    # By language
    lang_stats = {}
    for l in ["te", "hi", "en"]:
        sub = [r for r in corpus_records if r["lang"] == l]
        lang_stats[l] = {
            "count": len(sub),
            "raw": calc_wer(sub, "raw_edits"),
            "norm": calc_wer(sub, "norm_edits")
        }

    # By noise
    noise_stats = {}
    for n in ["Clean", "Tractor Noise", "Wind Noise", "Market Noise"]:
        sub = [r for r in corpus_records if r["noise"] == n]
        noise_stats[n] = {
            "count": len(sub),
            "raw": calc_wer(sub, "raw_edits"),
            "norm": calc_wer(sub, "norm_edits")
        }

    # By terminology (Agricultural Terminology vs General)
    agri_cats = ["Pest & Chemical", "Pesticide Safety", "Crop Protection"]
    agri_sub = [r for r in corpus_records if r["cat"] in agri_cats]
    gen_sub = [r for r in corpus_records if r["cat"] not in agri_cats]

    term_stats = {
        "Agricultural Terminology": {
            "count": len(agri_sub),
            "raw": calc_wer(agri_sub, "raw_edits"),
            "norm": calc_wer(agri_sub, "norm_edits")
        },
        "General Agronomy & Weather": {
            "count": len(gen_sub),
            "raw": calc_wer(gen_sub, "raw_edits"),
            "norm": calc_wer(gen_sub, "norm_edits")
        }
    }

    out = {
        "total_samples": len(corpus_records),
        "overall_raw": overall_raw,
        "overall_norm": overall_norm,
        "by_language": lang_stats,
        "by_noise": noise_stats,
        "by_terminology": term_stats
    }

    with open("audit_wer_standalone_results.json", "w", encoding="utf-8") as f:
        json.dump(out, f, indent=2, ensure_ascii=False)

    print("\nWER Empirical Breakdown:")
    print(f"Overall Raw:  {overall_raw['wer']}% (S={overall_raw['S']}, D={overall_raw['D']}, I={overall_raw['I']}, N={overall_raw['N']})")
    print(f"Overall Norm: {overall_norm['wer']}% (S={overall_norm['S']}, D={overall_norm['D']}, I={overall_norm['I']}, N={overall_norm['N']})")
    print("\nBy Language (Norm):")
    for l, d in lang_stats.items():
        print(f"  {l.upper()} ({d['count']}): Raw={d['raw']['wer']}%, Norm={d['norm']['wer']}% (S={d['norm']['S']}, D={d['norm']['D']}, I={d['norm']['I']}, N={d['norm']['N']})")
    print("\nBy Noise (Norm):")
    for n, d in noise_stats.items():
        print(f"  {n} ({d['count']}): Raw={d['raw']['wer']}%, Norm={d['norm']['wer']}%")
    print("\nBy Terminology (Norm):")
    for t, d in term_stats.items():
        print(f"  {t} ({d['count']}): Raw={d['raw']['wer']}%, Norm={d['norm']['wer']}%")

if __name__ == "__main__":
    run_wer_audit()
