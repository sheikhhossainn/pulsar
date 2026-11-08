package bd.pulsar

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "bn-rBD-w393dp-h851dp-port-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HomeTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()

    @Test
    fun homeAndReminderForm() {
        ui.onNodeWithText("আজ কী করতে চান?").assertIsDisplayed()
        ui.onNodeWithText("মনে করান").assertHasClickAction()
        val out = File("build/reports/pitch/home.png")
        out.parentFile?.mkdirs()
        ui.runOnIdle {
            val view = ui.activity.window.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            out.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        ui.onNodeWithText("মনে করান").performClick()
        ui.onNodeWithText("কোন কাজটি মনে করাব?").assertIsDisplayed()
        ui.onNodeWithText("নিশ্চিত করে রাখুন").assertIsNotEnabled()
    }

    @Test
    fun exampleCommandCreatesOnlyAfterConfirmation() {
        ui.onNodeWithContentDescription("ব্যবহারের সাহায্য").performClick()
        ui.onNodeWithText("আধা ঘণ্টা পরে পানি খেতে মনে করিও").performClick()
        ui.onNodeWithText("এগিয়ে যান").performClick()
        ui.onNodeWithText("সময় ঠিক আছে?").assertIsDisplayed()
        Store(ui.activity).use { org.junit.Assert.assertTrue(it.reminders().isEmpty()) }
        ui.onNodeWithText("নিশ্চিত করে রাখুন").performClick()
        ui.waitForIdle()
        Store(ui.activity).use {
            org.junit.Assert.assertEquals("পানি খেতে", it.reminders().single().title)
        }
    }

    @Test
    fun duplicateDailyDeliveryCreatesOnlyOneNextOccurrence() {
        Store(ui.activity).use { db ->
            val due = System.currentTimeMillis()
            val id = db.save(null, "পানি", due, true)
            val r = db.reminders().single { it.id == id }
            org.junit.Assert.assertNotNull(db.delivered(r))
            org.junit.Assert.assertNull(db.delivered(r))
            org.junit.Assert.assertEquals(1, db.reminders().count { it.daily })
        }
    }
}
