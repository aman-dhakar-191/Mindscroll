package io.github.amandhakar.mindscroll

import io.github.amandhakar.mindscroll.ui.Autostart
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AutostartTest {
    @Test
    fun xiaomiRedmiAndPocoAreTheSameList() {
        assertEquals("Xiaomi", Autostart.forPhone("Xiaomi")!!.name)
        assertEquals("Xiaomi", Autostart.forPhone("unknown", "Redmi")!!.name)
        assertEquals("Xiaomi", Autostart.forPhone("XIAOMI", "POCO")!!.name)
    }

    @Test
    fun otherMakersWithTheirOwnLists() {
        for (maker in listOf("OPPO", "realme", "vivo", "iQOO", "HUAWEI", "HONOR", "OnePlus", "samsung")) {
            assertNotNull(maker, Autostart.forPhone(maker))
        }
    }

    @Test
    fun phonesWithoutOneGetNoStep() {
        assertNull(Autostart.forPhone("Google", "google"))
        assertNull(Autostart.forPhone("motorola"))
        assertNull(Autostart.forPhone(null))
    }

    @Test
    fun everyMakerSaysWhatToDo() {
        for (maker in listOf("xiaomi", "oppo", "vivo", "huawei", "oneplus", "samsung")) {
            val oem = Autostart.forPhone(maker)!!
            assertTrue(oem.where.isNotBlank())
            assertTrue(oem.pages.isNotEmpty())
        }
    }
}
