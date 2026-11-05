package dev.pulsar.tools

import android.content.ContentProviderOperation
import android.content.Context
import android.provider.ContactsContract
import java.util.regex.Pattern

/**
 * Tool 2: ContactSaverTool
 * Ingestion of contacts directly from incoming notifications (SMS, WhatsApp, imo)
 * or voice commands ("এই নাম্বারটা ইলেকট্রিশিয়ান জামাল নামে সেভ কর").
 * Uses NotificationListenerService instead of restricted READ_SMS permission.
 */
class ContactSaverTool(private val context: Context) {

    companion object {
        // Bangladeshi 11-digit mobile regex: 013, 014, 015, 016, 017, 018, 019
        private val BD_PHONE_REGEX = Pattern.compile("(?:\\+?88)?(01[3-9]\\d{8})")
    }

    data class SaveResult(
        val success: Boolean,
        val name: String,
        val phone: String,
        val message: String
    )

    fun extractBdPhone(text: String): String? {
        val matcher = BD_PHONE_REGEX.matcher(text)
        return if (matcher.find()) {
            matcher.group(1)
        } else null
    }

    fun saveContact(name: String, rawPhone: String): SaveResult {
        val phone = extractBdPhone(rawPhone) ?: rawPhone.replace("[^0-9+]".toRegex(), "")
        if (phone.length < 11) {
            return SaveResult(
                success = false,
                name = name,
                phone = phone,
                message = "সঠিক ১১-ডিজিটের মোবাইল নাম্বার পাওয়া যায়নি।"
            )
        }

        return try {
            val ops = ArrayList<ContentProviderOperation>()

            // 1. Raw Contact insertion
            ops.add(
                ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
                    .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, null)
                    .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, null)
                    .build()
            )

            // 2. Structured Name insertion
            ops.add(
                ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                    .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
                    .withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, name)
                    .build()
            )

            // 3. Phone Number insertion
            ops.add(
                ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                    .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
                    .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, phone)
                    .withValue(ContactsContract.CommonDataKinds.Phone.TYPE, ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE)
                    .build()
            )

            context.contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)

            SaveResult(
                success = true,
                name = name,
                phone = phone,
                message = "$name এর নাম্বার ($phone) সফলভাবে সেভ করা হয়েছে।"
            )
        } catch (e: Exception) {
            SaveResult(
                success = false,
                name = name,
                phone = phone,
                message = "নাম্বার সেভ করতে ব্যর্থ: ${e.localizedMessage}"
            )
        }
    }
}
