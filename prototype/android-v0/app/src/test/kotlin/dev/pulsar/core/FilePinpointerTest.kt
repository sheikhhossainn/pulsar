package dev.pulsar.core

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class FilePinpointerTest {

    private lateinit var pinpointer: FilePinpointer

    @Before
    fun setUp() {
        // Create mock indexed files
        val mockFiles = listOf(
            FileItem(
                path = "/storage/emulated/0/WhatsApp/Media/WhatsApp Images/IMG-WA0001.jpg",
                relPath = "WhatsApp/Media/WhatsApp Images/IMG-WA0001.jpg",
                filename = "IMG-WA0001.jpg",
                ext = ".jpg",
                mtime = System.currentTimeMillis() - 5 * 60 * 1000, // 5 min ago
                ageMinutes = 5.0,
                size = 102400,
                searchBlob = "img-wa0001.jpg whatsapp/media/whatsapp images"
            ),
            FileItem(
                path = "/storage/emulated/0/DCIM/Camera/IMG_111500.jpg",
                relPath = "DCIM/Camera/IMG_111500.jpg",
                filename = "IMG_111500.jpg",
                ext = ".jpg",
                mtime = System.currentTimeMillis() - 30 * 60 * 1000, // 30 min ago
                ageMinutes = 30.0,
                size = 204800,
                searchBlob = "img_111500.jpg dcim/camera camera"
            ),
            FileItem(
                path = "/storage/emulated/0/Download/Square_Hospital_Prescription.pdf",
                relPath = "Download/Square_Hospital_Prescription.pdf",
                filename = "Square_Hospital_Prescription.pdf",
                ext = ".pdf",
                mtime = System.currentTimeMillis() - 120 * 60 * 1000,
                ageMinutes = 120.0,
                size = 512000,
                searchBlob = "square_hospital_prescription.pdf download prescription doctor rx"
            )
        )
        pinpointer = FilePinpointer(initialFiles = mockFiles)
    }

    @Test
    fun testLastPhotoPinpoint() {
        val res = pinpointer.pinpointFile("একদম শেষের ফটোটা বাইর কর")
        assertEquals("FOUND", res.status)
        assertNotNull(res.file)
        assertTrue(res.file?.ext == ".jpg")
    }

    @Test
    fun testTodayCameraPhoto() {
        val res = pinpointer.pinpointFile("আজকে ক্যামেরায় তোলা ছবিটা ওপেন কর")
        assertEquals("FOUND", res.status)
        assertNotNull(res.file)
        assertTrue(res.file?.relPath?.contains("Camera") == true)
    }

    @Test
    fun testDoctorPrescription() {
        val res = pinpointer.pinpointFile("ডাক্তারের প্রেসক্রিপশনটা দেখাও")
        assertEquals("FOUND", res.status)
        assertNotNull(res.file)
        assertTrue(res.file?.filename?.contains("Prescription") == true)
    }
}
