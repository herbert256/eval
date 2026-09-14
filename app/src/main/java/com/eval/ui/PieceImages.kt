package com.eval.ui

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.eval.R
import com.eval.chess.PieceColor
import com.eval.chess.PieceType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Shared by board instances so returning from overlays does not decode pieces again. */
internal object PieceImages {
    @Volatile
    var cached: Map<Pair<PieceColor, PieceType>, ImageBitmap> = emptyMap()
        private set
    private val mutex = Mutex()

    suspend fun load(context: Context): Map<Pair<PieceColor, PieceType>, ImageBitmap> =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                if (cached.isNotEmpty()) return@withLock cached
                val resources = context.applicationContext.resources
                val pieces = mapOf(
                    (PieceColor.WHITE to PieceType.KING) to R.drawable.piece_white_king,
                    (PieceColor.WHITE to PieceType.QUEEN) to R.drawable.piece_white_queen,
                    (PieceColor.WHITE to PieceType.ROOK) to R.drawable.piece_white_rook,
                    (PieceColor.WHITE to PieceType.BISHOP) to R.drawable.piece_white_bishop,
                    (PieceColor.WHITE to PieceType.KNIGHT) to R.drawable.piece_white_knight,
                    (PieceColor.WHITE to PieceType.PAWN) to R.drawable.piece_white_pawn,
                    (PieceColor.BLACK to PieceType.KING) to R.drawable.piece_black_king,
                    (PieceColor.BLACK to PieceType.QUEEN) to R.drawable.piece_black_queen,
                    (PieceColor.BLACK to PieceType.ROOK) to R.drawable.piece_black_rook,
                    (PieceColor.BLACK to PieceType.BISHOP) to R.drawable.piece_black_bishop,
                    (PieceColor.BLACK to PieceType.KNIGHT) to R.drawable.piece_black_knight,
                    (PieceColor.BLACK to PieceType.PAWN) to R.drawable.piece_black_pawn
                )
                // Canvas sizes each piece to its square. Density-upscaling the
                // 480px source first wastes memory and can stall the UI for seconds.
                val options = BitmapFactory.Options().apply { inScaled = false }
                pieces.mapValues { (_, id) ->
                    requireNotNull(BitmapFactory.decodeResource(resources, id, options)).asImageBitmap()
                }.also { cached = it }
            }
        }
}
