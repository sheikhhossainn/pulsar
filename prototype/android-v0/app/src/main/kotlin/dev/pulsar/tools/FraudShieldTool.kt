package dev.pulsar.tools

import java.util.regex.Pattern

/**
 * Tool 3: FraudShieldTool
 * 100% Deterministic bKash / Nagad Fake Cash-In & SMS Fraud Detection
 * Checks sender headers (Alphanumeric bKash/16247 vs personal 017/018/019...)
 * and triggers instant high-priority Bengali voice alerts.
 */
class FraudShieldTool {

    companion object {
        // Legitimate Mobile Financial Service (MFS) Alphanumeric / Shortcode Headers
        val OFFICIAL_MFS_HEADERS = setOf(
            "bkash", "16247", "nagad", "16167", "upay", "rocket", "16216", "tap"
        )

        // Personal Bangladeshi Mobile Number Pattern
        private val PERSONAL_NUMBER_REGEX = Pattern.compile("^(?:\\+?88)?01[3-9]\\d{8}$")

        // Money Received / Cash-In Patterns (English & Bengali)
        private val FAKE_CASH_IN_PATTERNS = listOf(
            Pattern.compile("you have received tk[\\s.:]+([0-9,.]+)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("received tk[\\s.:]+([0-9,.]+)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("cash in tk[\\s.:]+([0-9,.]+)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("টাকা পাঠানো হয়েছে[\\s.:]+([0-9,.]+)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("টাকা পেয়েছেন[\\s.:]+([0-9,.]+)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("টাকা গ্রহণ করেছেন", Pattern.CASE_INSENSITIVE)
        )

        // TrxID Fake Marker
        private val TRX_ID_REGEX = Pattern.compile("trxid[:\\s]+([a-zA-Z0-9]+)", Pattern.CASE_INSENSITIVE)
    }

    data class FraudVerdict(
        val isFraud: Boolean,
        val riskScore: Float, // 0.0 to 1.0
        val sender: String,
        val detectedAmount: String?,
        val detectedTrxId: String?,
        val spokenAlertBengali: String,
        val details: String
    )

    fun inspectNotification(sender: String, messageBody: String): FraudVerdict {
        val cleanSender = sender.trim().lowercase()
        val isOfficialHeader = OFFICIAL_MFS_HEADERS.contains(cleanSender)
        val isPersonalNumber = PERSONAL_NUMBER_REGEX.matcher(cleanSender.replace(" ", "").replace("-", "")).matches()

        var detectedAmount: String? = null
        var isCashInMessage = false

        for (pattern in FAKE_CASH_IN_PATTERNS) {
            val matcher = pattern.matcher(messageBody)
            if (matcher.find()) {
                isCashInMessage = true
                if (matcher.groupCount() >= 1) {
                    detectedAmount = matcher.group(1)
                }
                break
            }
        }

        val trxMatcher = TRX_ID_REGEX.matcher(messageBody)
        val detectedTrxId = if (trxMatcher.find()) trxMatcher.group(1) else null

        // DETERMINISTIC RULE: Personal phone number sending an MFS cash-in notification = 100% FRAUD
        if (isPersonalNumber && (isCashInMessage || messageBody.contains("bkash", true) || messageBody.contains("nagad", true))) {
            val amountStr = if (detectedAmount != null) "$detectedAmount টাকার" else ""
            return FraudVerdict(
                isFraud = true,
                riskScore = 1.0f,
                sender = sender,
                detectedAmount = detectedAmount,
                detectedTrxId = detectedTrxId,
                spokenAlertBengali = "সাবধান! ব্যক্তিগত নাম্বার $sender থেকে $amountStr ভুয়া টাকা পাওয়ার মেসেজ এসেছে! কাউকে টাকা ফেরত পাঠাবেন না বা কোনো কোড শেয়ার করবেন না।",
                details = "Fake MFS Cash-In from Personal Number (Header Spoofing Attack)"
            )
        }

        // Legitimate transaction notification from official shortcode
        if (isOfficialHeader && isCashInMessage) {
            return FraudVerdict(
                isFraud = false,
                riskScore = 0.0f,
                sender = sender,
                detectedAmount = detectedAmount,
                detectedTrxId = detectedTrxId,
                spokenAlertBengali = "অফিসিয়াল $sender থেকে লেনদেনের নিশ্চিতকরণ এসেছে।",
                details = "Official verified MFS SMS header"
            )
        }

        return FraudVerdict(
            isFraud = false,
            riskScore = 0.0f,
            sender = sender,
            detectedAmount = detectedAmount,
            detectedTrxId = detectedTrxId,
            spokenAlertBengali = "",
            details = "Standard message"
        )
    }
}
