# Bol Kisan Demo & Sample Question Multilingual Report

**Date:** 2026-11-09  
**Version:** 1.0.0-rc3  
**Status:** COMPLETE & VERIFIED  
**Component:** `presentation/common/LocalizedContent.kt`  

---

## 1. Problem Diagnosis

Prior to this fix, the application exhibited repetitive and cross-language question display:
1. When selecting Hindi or English, demo questions frequently remained in Telugu (e.g., `"వరిలో అగ్గితెగులు నివారణ ఎలా చేయాలి?"` appeared even when English was chosen).
2. When selecting crops other than Cotton, Chilli, or Maize (such as Tomato, Wheat, Groundnut, Turmeric, or Mango), the code defaulted to Rice / Paddy questions due to an unhandled `when` condition.
3. Offline instant suggestions in the voice modal showed Telugu text regardless of selected locale.

---

## 2. Root Cause Analysis

In `presentation/common/LocalizedContent.kt`:
```kotlin
// Former buggy implementation:
fun homeSampleQuestions(crop: String, language: String): List<String> {
    return when {
        crop.contains("Cotton", true) -> ...
        crop.contains("Chilli", true) -> ...
        crop.contains("Maize", true) -> ...
        else -> listOf(
            // Hardcoded Rice questions defaulted for all other crops!
            pick(language, "వరిలో అగ్గితెగులు నివారణ ఎలా?", "వరిలో అగ్గితెగులు నివారణ ఎలా?", "How to control rice blast?") // Notice missing Hindi translation!
        )
    }
}
```
Two defects were present:
1. **Missing Crop Cases:** Only 3 crops had specialized branches; 6 crops defaulted to Rice.
2. **Missing Hindi Translations:** In several `pick()` calls, the Hindi argument was accidentally duplicated with Telugu text.

---

## 3. Implementation of Trilingual Matrix

We refactored `LocalizedContent.kt` with a canonical resolution function:
`canonicalCropName(rawCrop: String?): String`

It normalizes all multilingual and regional variations:
- `"వరి"`, `"వడ్లు"`, `"ధాన్"`, `"धान"`, `"paddy"`, `"rice"` -> `"Rice / Paddy"`
- `"పత్తి"`, `"ప్రత్తి"`, `"कपास"`, `"cotton"` -> `"Cotton"`
- `"మిరప"`, `"మిర్చి"`, `"मिर्च"`, `"chilli"`, `"chili"` -> `"Chilli"`
- `"టమోటా"`, `"టమాట"`, `"टमाटर"`, `"tomato"` -> `"Tomato"`
- `"గోధుమ"`, `"గేహూం"`, `"गेहूं"`, `"wheat"` -> `"Wheat"`
- `"మొక్కజొన్న"`, `"మక్క"`, `"मक्का"`, `"maize"`, `"corn"` -> `"Maize"`
- `"వేరుశనగ"`, `"పల్లీ"`, `"मूंगफली"`, `"groundnut"`, `"peanut"` -> `"Groundnut"`
- `"పసుపు"`, `"हल्दी"`, `"turmeric"` -> `"Turmeric"`
- `"మామిడి"`, `"आम"`, `"mango"` -> `"Mango"`

### 3.1 Trilingual Question Catalog (All 9 Crops)

#### 1. Cotton (`Cotton`)
- **Telugu (`te`):**
  1. `పత్తిలో గులాబీ రంగు పురుగు నివారణ ఎలా చేయాలి?`
  2. `పత్తి పైరుకు ఎకరాకు ఎంత మోతాదులో ఎరువులు వేయాలి?`
  3. `పత్తిలో ఆకుముడత మరియు రసం పీల్చే పురుగుల నివారణ?`
- **Hindi (`hi`):**
  1. `कपास में गुलाबी सुंडी का नियंत्रण कैसे करें?`
  2. `कपास में प्रति एकड़ कितनी खाद और उर्वरक डालना चाहिए?`
  3. `कपास में रस चूसक कीटों और थ्रिप्स की रोकथाम कैसे करें?`
- **English (`en`):**
  1. `How to control pink bollworm in cotton?`
  2. `What is the fertilizer schedule per acre for cotton?`
  3. `How to manage sucking pests and leaf curl in cotton?`

#### 2. Rice / Paddy (`Rice / Paddy`)
- **Telugu (`te`):**
  1. `వరిలో అగ్గితెగులు నివారణ ఎలా చేయాలి?`
  2. `వరిలో కాండం తొలిచే పురుగు మరియు సుడిదోమ నివారణ?`
  3. `వరి పైరుకు పొటాష్ మరియు నత్రజని ఎరువుల యాజమాన్యం?`
- **Hindi (`hi`):**
  1. `धान में झुलसा रोग (ब्लास्ट) का नियंत्रण कैसे करें?`
  2. `धान में तना छेदक और भूरा फुदका कीट की रोकथाम?`
  3. `धान की फसल में यूरिया और पोटाश प्रबंधन कैसे करें?`
- **English (`en`):**
  1. `How can rice blast disease be controlled?`
  2. `How to manage stem borer and BPH in paddy?`
  3. `What is the schedule for urea and potash application in rice?`

#### 3. Chilli (`Chilli`)
- **Telugu (`te`):**
  1. `మిరపలో తామర పురుగులు మరియు నల్ల తామర నివారణ?`
  2. `మిరపలో ఆకుముడత తెగులు నివారణ మందులు ఏవి?`
  3. `మిరప కాయకుళ్లు మరియు బూడిద తెగులు యాజమాన్యం?`
- **Hindi (`hi`):**
  1. `मिर्च में थ्रिप्स और काली थ्रिप्स का नियंत्रण कैसे करें?`
  2. `मिर्च में पत्ती मरोड़ (चूर्डी-मुर्डी) रोग की दवा क्या है?`
  3. `मिर्च में फल सड़न और डाई-बैक रोग का उपचार बताएं?`
- **English (`en`):**
  1. `How to control thrips and black thrips in chilli?`
  2. `What is the treatment for leaf curl virus in chilli?`
  3. `How to prevent fruit rot and dieback in chilli?`

#### 4. Tomato (`Tomato`)
- **Telugu (`te`):**
  1. `టమోటాలో ఆకుమాడు తెగులు మరియు ఆకుమచ్చల నివారణ?`
  2. `టమోటాలో పూత రాలకుండా ఏ మందు పిచికారీ చేయాలి?`
  3. `టమోటా కాయ తొలిచే పురుగు నివారణ పద్ధతులు?`
- **Hindi (`hi`):**
  1. `टमाटर में अगेती व पछेती झुलसा रोग की रोकथाम?`
  2. `टमाटर में फूल झड़ने से रोकने के लिए क्या छिड़कें?`
  3. `टमाटर में फल छेदक कीट का नियंत्रण कैसे करें?`
- **English (`en`):**
  1. `How to manage early and late blight in tomato?`
  2. `What spray prevents flower drop in tomato?`
  3. `How to control tomato fruit borer organically and chemically?`

#### 5. Wheat (`Wheat`)
- **Telugu (`te`):**
  1. `గోధుమలో కుంకుమ తెగులు (రస్ట్) నివారణ ఎలా?`
  2. `గోధుమ పంటలో మొదటి తడి మరియు యూరియా ఎప్పుడు వేయాలి?`
  3. `గోధుమలో కలుపు మొక్కల నివారణ మందులు ఏవి?`
- **Hindi (`hi`):**
  1. `गेहूं में पीला व भूरा रतुआ (रस्ट) रोग का नियंत्रण?`
  2. `गेहूं में पहली सिंचाई (सीआरआई अवस्था) और यूरिया कब दें?`
  3. `गेहूं में संकरी व चौड़ी पत्ती वाले खरपतवार की दवा?`
- **English (`en`):**
  1. `How to manage yellow and brown rust in wheat?`
  2. `When should the first irrigation (CRI stage) be given to wheat?`
  3. `Which herbicide controls broadleaf weeds in wheat?`

#### 6. Maize (`Maize`)
- **Telugu (`te`):**
  1. `మొక్కజొన్నలో కత్తెర పురుగు (ఫాల్ ఆర్మీవార్మ్) నివారణ?`
  2. `మొక్కజొన్నలో ఎకరాకు ఎరువుల మోతాదు ఎంత?`
  3. `మొక్కజొన్నలో కలుపు నివారణకు ఏ మందు వాడాలి?`
- **Hindi (`hi`):**
  1. `मक्का में फॉल आर्मीवर्म (सैनिक कीट) का नियंत्रण कैसे करें?`
  2. `मक्का में प्रति एकड़ खाद व उर्वरक की संतुलित मात्रा?`
  3. `मक्का में खरपतवार नियंत्रण के लिए कौन सी दवा डालें?`
- **English (`en`):**
  1. `How to control Fall Armyworm (FAW) in maize?`
  2. `What is the balanced NPK fertilizer dose for maize?`
  3. `Which pre-emergence herbicide is suitable for maize?`

#### 7. Groundnut (`Groundnut`)
- **Telugu (`te`):**
  1. `వేరుశనగలో తిక్కా ఆకుమచ్చ తెగులు నివారణ ఎలా?`
  2. `వేరుశనగలో ఊడలు దిగే దశలో జిప్సం ఎందుకు వేయాలి?`
  3. `వేరుశనగలో వేరుకుళ్లు తెగులు నివారణ?`
- **Hindi (`hi`):**
  1. `मूंगफली में टिक्का रोग (पत्ती धब्बा) की रोकथाम?`
  2. `मूंगफली में पेगिंग अवस्था पर जिप्सम डालना क्यों जरूरी है?`
  3. `मूंगफली में जड़ गलन और कॉलर रॉट का उपचार?`
- **English (`en`):**
  1. `How to control Tikka leaf spot disease in groundnut?`
  2. `Why is gypsum application necessary at pegging stage?`
  3. `How to treat collar rot and root rot in groundnut?`

#### 8. Turmeric (`Turmeric`)
- **Telugu (`te`):**
  1. `పసుపులో దుంపకుళ్లు తెగులు నివారణ ఎలా చేయాలి?`
  2. `పసుపు పంటలో ఎరువుల యాజమాన్యం మరియు సూక్ష్మధాతువులు?`
  3. `పసుపులో ఆకుమచ్చ తెగులు నివారణ మందులు?`
- **Hindi (`hi`):**
  1. `हल्दी में कंद सड़न (राइजोम रॉट) रोग की रोकथाम कैसे करें?`
  2. `हल्दी की फसल में सूक्ष्म पोषक तत्व और खाद प्रबंधन?`
  3. `हल्दी में पत्ती धब्बा रोग का प्रभावी उपचार क्या है?`
- **English (`en`):**
  1. `How to prevent rhizome rot in turmeric?`
  2. `What is the micronutrient spray schedule for turmeric?`
  3. `How to control leaf spot and blotch in turmeric?`

#### 9. Mango (`Mango`)
- **Telugu (`te`):**
  1. `మామిడిలో పూత మరియు పిందె దశలో తేనెమంచు పురుగు నివారణ?`
  2. `మామిడిలో బూడిద తెగులు నివారణకు ఏ మందు పిచికారీ చేయాలి?`
  3. `మామిడి కాయ తొలుచు పురుగు మరియు పిందె రాలడం నివారణ?`
- **Hindi (`hi`):**
  1. `आम में बौर आते समय मधुआ कीट (हॉपर) का नियंत्रण?`
  2. `आम में चूर्णिल आसिता (पाउडरी मिल्ड्यू) रोग का उपचार?`
  3. `आम में फल मक्खी और फल झड़ने से रोकने के उपाय?`
- **English (`en`):**
  1. `How to manage mango hopper during flowering stage?`
  2. `What is the control for powdery mildew in mango orchards?`
  3. `How to manage fruit fly and premature fruit drop in mango?`

---

## 4. Verification Check

- Executed automated check across all 9 crops in all 3 languages (27 permutations).
- **Result:** 100% of generated demo chips strictly match the requested language.
- Zero cross-language bleed observed across `homeSampleQuestions()`, `offlineInstantQuestions()`, and `voiceQuickPrompts()`.
