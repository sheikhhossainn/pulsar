package dev.pulsar.tools

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.telecom.TelecomManager
import android.telephony.SubscriptionInfo
import android.telephony.SubscriptionManager
import dev.pulsar.core.ContactResolver
import dev.pulsar.core.ResolveResult
import dev.pulsar.core.ResolveStatus

/**
 * Tool 1: KinshipDialerTool
 * Relational cellular calling via Android TelecomManager & SubscriptionManager.
 * Features:
 * - Dual-SIM carrier selection (e.g. Grameenphone vs Banglalink)
 * - Relational elder kinship resolution & 3-child ambiguity handling
 * - Direct intent dialing or confirmation trigger
 */
class KinshipDialerTool(
    private val context: Context,
    private val contactResolver: ContactResolver
) {

    data class DialExecution(
        val success: Boolean,
        val targetName: String?,
        val targetPhone: String?,
        val simSlot: Int? = null,
        val isAmbiguous: Boolean = false,
        val clarificationPrompt: String? = null,
        val message: String
    )

    fun executeDial(query: String, preferredCarrier: String? = null): DialExecution {
        val resolveResult = contactResolver.resolveContact(query)

        when (resolveResult.status) {
            ResolveStatus.AMBIGUOUS -> {
                return DialExecution(
                    success = false,
                    targetName = null,
                    targetPhone = null,
                    isAmbiguous = true,
                    clarificationPrompt = resolveResult.clarificationMessage,
                    message = resolveResult.clarificationMessage ?: "কাকে কল করতে চান?"
                )
            }
            ResolveStatus.NOT_FOUND -> {
                return DialExecution(
                    success = false,
                    targetName = null,
                    targetPhone = null,
                    message = resolveResult.clarificationMessage ?: "কন্টাক্ট খুঁজে পাওয়া যায়নি।"
                )
            }
            ResolveStatus.RESOLVED -> {
                val phone = resolveResult.phone ?: resolveResult.contact?.phone
                val name = resolveResult.contact?.name ?: "অচেনা কন্টাক্ট"

                if (phone.isNullOrBlank()) {
                    return DialExecution(
                        success = false,
                        targetName = name,
                        targetPhone = null,
                        message = "$name এর কোনো ফোন নাম্বার নেই।"
                    )
                }

                val simSlot = selectSimSlot(preferredCarrier)
                val dialSuccess = performSystemCall(phone, simSlot)

                return DialExecution(
                    success = dialSuccess,
                    targetName = name,
                    targetPhone = phone,
                    simSlot = simSlot,
                    message = if (dialSuccess) "$name-কে কল করা হচ্ছে ($phone)" else "কল শুরু করা সম্ভব হয়নি।"
                )
            }
        }
    }

    private fun selectSimSlot(preferredCarrier: String?): Int? {
        try {
            val subManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager
            val activeList: List<SubscriptionInfo>? = subManager?.activeSubscriptionInfoList
            if (activeList.isNullOrEmpty()) return null

            if (preferredCarrier != null) {
                val matched = activeList.firstOrNull {
                    it.displayName.toString().contains(preferredCarrier, ignoreCase = true) ||
                    it.carrierName.toString().contains(preferredCarrier, ignoreCase = true)
                }
                if (matched != null) return matched.simSlotIndex
            }
            return activeList.first().simSlotIndex
        } catch (_: Exception) {
            return null
        }
    }

    private fun performSystemCall(phone: String, simSlot: Int?): Boolean {
        return try {
            val cleanPhone = phone.replace(" ", "").replace("-", "")
            val callIntent = Intent(Intent.ACTION_CALL).apply {
                data = Uri.parse("tel:$cleanPhone")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                simSlot?.let {
                    putExtra("com.android.phone.extra.slot", it)
                    putExtra("simSlot", it)
                }
            }
            context.startActivity(callIntent)
            true
        } catch (_: SecurityException) {
            // Fallback to ACTION_DIAL if CALL_PHONE permission not yet granted
            val dialIntent = Intent(Intent.ACTION_DIAL).apply {
                data = Uri.parse("tel:$phone")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(dialIntent)
            true
        } catch (_: Exception) {
            false
        }
    }
}
