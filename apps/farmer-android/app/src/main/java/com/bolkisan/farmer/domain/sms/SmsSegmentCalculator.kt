package com.bolkisan.farmer.domain.sms

import kotlin.math.ceil

/**
 * 3GPP TS 23.040 / ETSI GSM 03.38 Telecom SMS Segment Calculator.
 *
 * Implements strict on-device segmentation and encoding calculation:
 * 1. GSM-7 Alphabet:
 *    - Basic characters consume 1 septet (7 bits).
 *    - Extended characters (^{}\[~]|€) require an escape code (0x1B) and consume 2 septets.
 *    - Single segment: <= 160 septets.
 *    - Concatenated (multi-part): > 160 septets, calculated as ceil(septets / 153).
 *      (160 - 7 septets reserved for the 6-byte User Data Header / UDH).
 *    - Example: 320 chars in GSM-7 = ceil(320 / 153) = 3 segments (NOT 2).
 *
 * 2. UCS-2 Unicode (Telugu, Hindi, Devanagari, Emoji, non-GSM):
 *    - Triggered whenever ANY character is outside the GSM-7 alphabet.
 *    - Single segment: <= 70 UTF-16 code units (140 bytes / 2 bytes per unit).
 *    - Concatenated (multi-part): > 70 units, calculated as ceil(units / 67).
 *      (134 bytes / 2 bytes per unit, 6-byte UDH).
 *    - Example: 320 chars in Telugu/Hindi = ceil(320 / 67) = 5 segments (NOT 2).
 */
data class SMSSegmentInfo(
    val charCount: Int,
    val encoding: String,
    val isUnicode: Boolean,
    val units: Int,
    val segments: Int,
    val maxSingle: Int,
    val maxMultipart: Int,
    val explanation: String
)

object SmsSegmentCalculator {

    const val ENCODING_GSM7 = "GSM-7"
    const val ENCODING_UCS2 = "UCS-2 (Unicode)"

    // GSM 03.38 Basic character set (1 septet each)
    private val GSM7_BASIC_CHARS: Set<Char> = setOf(
        '@', '£', '$', '¥', 'è', 'é', 'ù', 'ì', 'ò', 'Ç', '\n', 'Ø', 'ø', '\r', 'Å', 'å',
        'Δ', '_', 'Φ', 'Γ', 'Λ', 'Ω', 'Π', 'Ψ', 'Σ', 'Θ', 'Ξ', '\u001b', 'Æ', 'æ', 'ß', 'É',
        ' ', '!', '"', '#', '¤', '%', '&', '\'', '(', ')', '*', '+', ',', '-', '.', '/',
        '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', ':', ';', '<', '=', '>', '?', '¡',
        'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J', 'K', 'L', 'M', 'N', 'O', 'P',
        'Q', 'R', 'S', 'T', 'U', 'V', 'W', 'X', 'Y', 'Z', 'Ä', 'Ö', 'Ñ', 'Ü', '§', '¿',
        'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j', 'k', 'l', 'm', 'n', 'o', 'p',
        'q', 'r', 's', 't', 'u', 'v', 'w', 'x', 'y', 'z', 'ä', 'ö', 'ñ', 'ü', 'à'
    )

    // GSM 03.38 Extended character set (2 septets each: 0x1B escape + character)
    private val GSM7_EXTENDED_CHARS: Set<Char> = setOf(
        '^', '{', '}', '\\', '[', '~', ']', '|', '€'
    )

    /**
     * Computes exact telecom SMS segmentation and encoding details.
     */
    fun calculateDetails(text: String?): SMSSegmentInfo {
        if (text.isNullOrEmpty()) {
            return SMSSegmentInfo(
                charCount = 0,
                encoding = ENCODING_GSM7,
                isUnicode = false,
                units = 0,
                segments = 0,
                maxSingle = 160,
                maxMultipart = 153,
                explanation = "Empty message (0 segments)"
            )
        }

        var isGsm7 = true
        var septetCount = 0

        for (ch in text) {
            when {
                ch in GSM7_BASIC_CHARS -> septetCount += 1
                ch in GSM7_EXTENDED_CHARS -> septetCount += 2
                else -> {
                    isGsm7 = false
                    break
                }
            }
        }

        return if (isGsm7) {
            val units = septetCount
            val segments = if (units <= 160) {
                if (units > 0) 1 else 0
            } else {
                ceil(units / 153.0).toInt()
            }
            SMSSegmentInfo(
                charCount = text.length,
                encoding = ENCODING_GSM7,
                isUnicode = false,
                units = units,
                segments = segments,
                maxSingle = 160,
                maxMultipart = 153,
                explanation = "GSM-7 encoding: $units septets -> $segments segment(s) (160 single / 153 multi-part)"
            )
        } else {
            // UCS-2 Unicode: Count UTF-16 code units (2 bytes per unit)
            val ucs2Units = text.length
            val segments = if (ucs2Units <= 70) {
                if (ucs2Units > 0) 1 else 0
            } else {
                ceil(ucs2Units / 67.0).toInt()
            }
            SMSSegmentInfo(
                charCount = text.length,
                encoding = ENCODING_UCS2,
                isUnicode = true,
                units = ucs2Units,
                segments = segments,
                maxSingle = 70,
                maxMultipart = 67,
                explanation = "UCS-2 Unicode encoding: $ucs2Units code units -> $segments segment(s) (70 single / 67 multi-part)"
            )
        }
    }

    /**
     * Returns standard telecom SMS segment count for a given text.
     */
    fun calculateSegments(text: String?): Int {
        return calculateDetails(text).segments
    }

    /**
     * Checks if the given text can be encoded in 7-bit GSM-03.38 charset.
     */
    fun isGsm7Compatible(text: String?): Boolean {
        if (text.isNullOrEmpty()) return true
        return text.all { it in GSM7_BASIC_CHARS || it in GSM7_EXTENDED_CHARS }
    }
}
