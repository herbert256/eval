package com.eval.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.eval.stockfish.StockfishEngine
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EngineIdentityIntegrationTest {
    @Test fun reads_the_installed_engine_identity_again_after_restart() = runBlocking {
        val engine = StockfishEngine(ApplicationProvider.getApplicationContext<Context>())
        assertNull(engine.engineName.value)
        try {
            assertTrue(engine.initialize())
            val name = engine.engineName.value
            assertNotNull(name)
            assertTrue("Reported name: $name", name!!.startsWith("Stockfish "))
            assertTrue(engine.restart())
            assertEquals(name, engine.engineName.value)
        } finally {
            engine.shutdown()
        }
        assertNull(engine.engineName.value)
    }
}
