package app.streamshield

import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.materialswitch.MaterialSwitch
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppWorkflowTest {
    @Test fun protectionScreenAndSavedNavigationWorkAfterStartup() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val deadline = System.currentTimeMillis() + 15_000
            var ready = false
            while (!ready && System.currentTimeMillis() < deadline) {
                scenario.onActivity { activity ->
                    ready = activity.findViewById<BottomNavigationView>(R.id.navigation).menu.findItem(R.id.action_protection).contentDescription == activity.getString(R.string.protection_on)
                }
                if (!ready) Thread.sleep(100)
            }
            assertTrue("Native engine should start", ready)
            scenario.onActivity { activity ->
                val toolbar = activity.findViewById<MaterialToolbar>(R.id.toolbar)
                val navigation = activity.findViewById<BottomNavigationView>(R.id.navigation)
                assertEquals(View.GONE, toolbar.visibility)
                navigation.menu.performIdentifierAction(R.id.action_protection, 0)
                assertEquals(View.VISIBLE, toolbar.visibility)
                val panel = activity.findViewById<ViewGroup>(R.id.native_screen)
                assertEquals(View.VISIBLE, panel.visibility)
                assertTrue(descendants(panel).filterIsInstance<TextView>().any { it.text.toString() == activity.getString(R.string.native_engine) })
                val toggle = descendants(panel).filterIsInstance<MaterialSwitch>().single()
                assertTrue(toggle.isEnabled)
                assertTrue(toggle.isChecked)
                toggle.isChecked = false
                assertFalse(descendants(panel).filterIsInstance<MaterialSwitch>().single().isChecked)
                descendants(panel).filterIsInstance<MaterialSwitch>().single().isChecked = true
                activity.onBackPressedDispatcher.onBackPressed()
                assertEquals(View.GONE, panel.visibility)
                assertEquals(View.GONE, toolbar.visibility)
                assertEquals(R.id.nav_home, navigation.selectedItemId)
                navigation.selectedItemId = R.id.nav_saved
                assertEquals(View.VISIBLE, panel.visibility)
                assertTrue(descendants(panel).filterIsInstance<TextView>().any { it.text.toString() == activity.getString(R.string.saved_title) })
                navigation.menu.performIdentifierAction(R.id.action_protection, 0)
                activity.onBackPressedDispatcher.onBackPressed()
                assertEquals(R.id.nav_saved, navigation.selectedItemId)
                assertEquals(View.VISIBLE, panel.visibility)
                activity.onBackPressedDispatcher.onBackPressed()
                assertEquals(View.GONE, panel.visibility)
                assertEquals(R.id.nav_home, navigation.selectedItemId)
                assertEquals(View.GONE, toolbar.visibility)
                navigation.menu.performIdentifierAction(R.id.action_more, 0)
                assertEquals("More keeps the active destination selected", R.id.nav_home, navigation.selectedItemId)
            }
        }
    }

    private fun descendants(view: View): Sequence<View> = sequence {
        yield(view)
        if (view is ViewGroup) for (index in 0 until view.childCount) yieldAll(descendants(view.getChildAt(index)))
    }
}
