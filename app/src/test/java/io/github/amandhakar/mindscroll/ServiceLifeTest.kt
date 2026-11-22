package io.github.amandhakar.mindscroll

import io.github.amandhakar.mindscroll.service.ServiceLife
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ServiceLifeTest {
    @Test
    fun nothingToReportOnFirstRunOrAfterACleanStop() {
        assertNull(ServiceLife.verdict(null, 0L))
        assertNull(ServiceLife.verdict("stopped", 1_000L))
    }

    @Test
    fun aRunThatWasStillRunningMeansItWasKilledOrCrashed() {
        val note = ServiceLife.verdict("running", 1_700_000_000_000L)
        assertNotNull(note)
        assertTrue("killed or crashed" in note!!)
    }
}
