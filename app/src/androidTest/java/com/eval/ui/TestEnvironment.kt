package com.eval.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.eval.stockfish.StockfishEngine
import org.junit.Assume.assumeTrue

/** Skip, rather than fail, tests whose environment is missing on the device running them. */
internal object TestEnvironment {
    fun assumeStockfishInstalled() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assumeTrue("Needs the Stockfish app (com.stockfish141)", StockfishEngine(context).isStockfishInstalled())
    }

    fun assumeNetwork() {
        assumeTrue("Needs internet access to lichess.org",
            runCatching { java.net.InetAddress.getByName("lichess.org") }.isSuccess)
    }
}
