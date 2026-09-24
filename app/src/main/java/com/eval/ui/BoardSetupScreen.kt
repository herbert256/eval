package com.eval.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import androidx.compose.ui.unit.sp
import com.eval.chess.BoardSetupPosition
import com.eval.chess.Piece
import com.eval.chess.PieceColor
import com.eval.chess.PieceType
import com.eval.chess.Square

private val SetupSaver = listSaver<BoardSetupPosition, String>(
    save = { listOf(it.squares, it.whiteToMove.toString(), it.castling, it.enPassant, it.halfMoves, it.fullMove) },
    restore = { BoardSetupPosition(it[0], it[1].toBoolean(), it[2], it[3], it[4], it[5]) }
)

@Composable
internal fun BoardSetupScreen(
    currentFen: String?,
    initiallyFlipped: Boolean,
    layout: BoardLayoutSettings,
    onStart: (String) -> Boolean,
    onBack: () -> Unit,
    initialFen: String? = null,
    importWarning: String? = null,
    imagePosition: Boolean = false
) {
    val imported = remember(initialFen) { initialFen?.let(BoardSetupPosition::fromDraftFen) }
    var draft by rememberSaveable(stateSaver = SetupSaver) {
        mutableStateOf(if (initialFen == null) BoardSetupPosition.initial() else imported ?: BoardSetupPosition())
    }
    var selectedPiece by rememberSaveable { mutableStateOf("P") }
    var flipped by rememberSaveable { mutableStateOf(initiallyFlipped) }
    var details by rememberSaveable { mutableStateOf(false) }
    var startError by rememberSaveable { mutableStateOf<String?>(null) }
    val context = LocalContext.current.applicationContext
    val images by produceState(PieceImages.cached, context) { value = PieceImages.load(context) }
    val validation = remember(draft) { draft.validationError() }
    fun update(position: BoardSetupPosition) { draft = position; startError = null }
    fun replace(position: BoardSetupPosition) { update(position) }

    EvalScreen(
        backgroundColor = AppColors.DarkBlueBackground,
        topBar = { EvalTitleBar(title = "Board setup", onBackClick = onBack) }
    ) {
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).testTag("setup_scroll"),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            importWarning?.let { Text(it, color = AppColors.SubtleText, style = MaterialTheme.typography.bodySmall) }
            if (initialFen != null && imported == null) {
                Text("The found position could not be read. Set up the pieces below before starting.",
                    color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                TextButton(onClick = { replace(BoardSetupPosition()) }) { Text("Clear") }
                TextButton(onClick = { replace(BoardSetupPosition.initial()) }) { Text("Initial position") }
                if (imported != null) {
                    TextButton(onClick = { replace(imported) }) { Text("Found position") }
                } else if (currentFen != null) {
                    TextButton(onClick = { replace(BoardSetupPosition.fromFen(currentFen)) }) { Text("Current position") }
                }
            }
            SetupBoard(draft, flipped, layout, images,
                onSquare = { update(draft.place(it, selectedPiece.single())) },
                onDrop = { from, to -> update(if (to == null) draft.place(from, '.') else draft.move(from, to)) })
            Column(Modifier.widthIn(max = 440.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                for (symbols in listOf("KQRBNP", "kqrbnp")) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        symbols.forEach { symbol ->
                            val piece = requireNotNull(BoardSetupPosition.piece(symbol))
                            Box(
                                Modifier.weight(1f).height(48.dp)
                                    .background(AppColors.BlueGrayAccent, RoundedCornerShape(6.dp))
                                    .border(if (selectedPiece == symbol.toString()) 2.dp else 0.dp,
                                        if (selectedPiece == symbol.toString()) AppColors.AccentBlue else Color.Transparent,
                                        RoundedCornerShape(6.dp))
                                    .selectable(selectedPiece == symbol.toString(), role = Role.RadioButton) {
                                        selectedPiece = symbol.toString()
                                    }
                                    .testTag("setup_piece_$symbol")
                                    .semantics { contentDescription = "Place ${pieceName(piece)}" },
                                contentAlignment = Alignment.Center
                            ) { SetupPiece(piece, images, Modifier.fillMaxSize().padding(3.dp)) }
                        }
                    }
                }
            }
            OutlinedButton(onClick = { flipped = !flipped }) { Text("Flip board") }
            Text(
                "Tap to place a ${pieceName(BoardSetupPosition.piece(selectedPiece.single())!!)}. " +
                    "Drag any piece to move it, or drag it outside the board to remove it.",
                color = AppColors.SubtleText, style = MaterialTheme.typography.bodySmall
            )
            if (imagePosition) {
                TextButton(onClick = { replace(draft.rotatePieces()) }) { Text("Rotate pieces") }
                Text("If the image was read upside down, rotate the pieces. Flip board only changes your view. " +
                    "Check whose turn it is, castling rights and the last move below.",
                    color = AppColors.SubtleText, style = MaterialTheme.typography.bodySmall)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Side to move", color = Color.White)
                FilterChip(draft.whiteToMove, onClick = { update(draft.withTurn(true)) }, label = { Text("White") })
                FilterChip(!draft.whiteToMove, onClick = { update(draft.withTurn(false)) }, label = { Text("Black") })
            }
            SetupMoveRights(draft, ::update)
            TextButton(onClick = { details = !details }, modifier = Modifier.fillMaxWidth()) {
                Text(if (details) "Hide position details" else "Position details")
            }
            if (details) SetupDetails(draft, ::update)
        }
        val error = validation ?: startError
        if (error != null) {
            Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp).testTag("setup_error"))
        }
        Button(
            onClick = { if (!onStart(draft.toFen())) startError = "Unable to open this position. Check the position details." },
            enabled = validation == null,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp).testTag("setup_start")
        ) { Text("Start from this position") }
    }
}

@Composable
internal fun SetupBoard(
    draft: BoardSetupPosition,
    flipped: Boolean,
    layout: BoardLayoutSettings,
    images: Map<Pair<PieceColor, PieceType>, ImageBitmap>,
    onSquare: (Int) -> Unit,
    onDrop: (Int, Int?) -> Unit
) {
    val currentDraft by rememberUpdatedState(draft)
    val currentTap by rememberUpdatedState(onSquare)
    val currentDrop by rememberUpdatedState(onDrop)
    var draggingFrom by remember(flipped) { mutableIntStateOf(-1) }
    var dragPosition by remember { mutableStateOf(Offset.Zero) }
    BoxWithConstraints(Modifier.widthIn(max = 440.dp).fillMaxWidth().aspectRatio(1f).testTag("setup_board")
        .pointerInput(flipped) {
            fun squareAt(point: Offset): Int? {
                if (point.x < 0 || point.y < 0 || point.x >= size.width || point.y >= size.height) return null
                val col = (point.x * 8 / size.width).toInt()
                val row = (point.y * 8 / size.height).toInt()
                return (if (flipped) row else 7 - row) * 8 + if (flipped) 7 - col else col
            }
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                val from = squareAt(down.position) ?: return@awaitEachGesture
                if (currentDraft.squares[from] == '.') {
                    // Empty-square swipes remain available to the scrolling screen.
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (change.isConsumed || event.changes.size != 1 ||
                            (change.position - down.position).getDistance() > viewConfiguration.touchSlop) break
                        if (!change.pressed) { change.consume(); currentTap(from); break }
                    }
                } else {
                    // An existing piece owns the gesture, even with a palette piece selected.
                    // Consuming in Initial prevents the parent scroll from stealing vertical drags.
                    down.consume()
                    try {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (change.isConsumed || event.changes.size != 1) break
                            if (draggingFrom == -1 && (change.position - down.position).getDistance() > viewConfiguration.touchSlop)
                                draggingFrom = from
                            dragPosition = change.position
                            change.consume()
                            if (!change.pressed) {
                                if (draggingFrom != -1) currentDrop(from, squareAt(change.position)) else currentTap(from)
                                break
                            }
                        }
                    } finally { draggingFrom = -1 }
                }
            }
        }) {
        val pieceSize = maxWidth / 8
        val piecePixels = with(LocalDensity.current) { pieceSize.toPx() }
        Column(Modifier.fillMaxSize()) {
            repeat(8) { row ->
                val rank = if (flipped) row else 7 - row
                Row(Modifier.weight(1f).fillMaxWidth()) {
                    repeat(8) { col ->
                        val file = if (flipped) 7 - col else col
                        val square = Square(file, rank)
                        val piece = draft.pieceAt(square.index)
                        val light = (rank + file) % 2 != 0
                        val lightColor = Color(layout.whiteSquareColor.toInt())
                        val darkColor = Color(layout.blackSquareColor.toInt())
                        Box(
                            Modifier.weight(1f).fillMaxHeight()
                                .background(if (light) lightColor else darkColor)
                                .testTag("setup_square_${square.toAlgebraic()}")
                                .semantics {
                                    role = Role.Button
                                    contentDescription = "${square.toAlgebraic()}, ${piece?.let(::pieceName) ?: "empty"}"
                                    onClick { onSquare(square.index); true }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (piece != null && draggingFrom != square.index)
                                SetupPiece(piece, images, Modifier.fillMaxSize().padding(2.dp))
                            if (draggingFrom == square.index) Box(Modifier.matchParentSize().border(3.dp, AppColors.AccentBlue))
                            if (col == 0) Text("${rank + 1}", color = if (light) darkColor else lightColor,
                                fontSize = 10.sp, modifier = Modifier.align(Alignment.TopStart).padding(start = 2.dp))
                            if (row == 7) Text("${'a' + file}", color = if (light) darkColor else lightColor,
                                fontSize = 10.sp, modifier = Modifier.align(Alignment.BottomEnd).padding(end = 2.dp))
                        }
                    }
                }
            }
        }
        if (draggingFrom != -1) draft.pieceAt(draggingFrom)?.let { piece ->
            SetupPiece(piece, images, Modifier.offset {
                IntOffset((dragPosition.x - piecePixels / 2).roundToInt(), (dragPosition.y - piecePixels / 2).roundToInt())
            }.size(pieceSize).padding(2.dp))
        }
    }
}

private fun pieceName(piece: Piece): String =
    "${piece.color.name.lowercase()} ${piece.type.name.lowercase()}"

@Composable
private fun SetupPiece(piece: Piece, images: Map<Pair<PieceColor, PieceType>, ImageBitmap>, modifier: Modifier) {
    val bitmap = images[piece.color to piece.type]
    if (bitmap != null) Image(bitmap, contentDescription = null, modifier = modifier)
    else Box(modifier, contentAlignment = Alignment.Center) {
        Text(when (piece.type) {
            PieceType.KING -> "K"; PieceType.QUEEN -> "Q"; PieceType.ROOK -> "R"
            PieceType.BISHOP -> "B"; PieceType.KNIGHT -> "N"; PieceType.PAWN -> "P"
        }, color = if (piece.color == PieceColor.WHITE) Color.White else Color.Black)
    }
}

@Composable
private fun SetupMoveRights(draft: BoardSetupPosition, onChange: (BoardSetupPosition) -> Unit) {
    Column(Modifier.widthIn(max = 440.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        for ((right, label) in listOf('K' to "White can castle short", 'Q' to "White can castle long",
            'k' to "Black can castle short", 'q' to "Black can castle long")) {
            if (draft.canCastle(right)) {
                Row(Modifier.fillMaxWidth().toggleable(
                    value = right in draft.castling, role = Role.Switch,
                    onValueChange = { onChange(draft.withCastling(right, it)) }
                ).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(label, color = Color.White, modifier = Modifier.weight(1f))
                    Switch(checked = right in draft.castling, onCheckedChange = null)
                }
            }
        }
        if ("KQkq".any(draft::canCastle)) {
            Text("Enable castling only if that king and rook have never moved.",
                color = AppColors.SubtleText, style = MaterialTheme.typography.bodySmall)
        }
        val lastMoves = remember(draft) { draft.enPassantLastMoves() }
        var menu by remember { mutableStateOf(false) }
        if (lastMoves.isNotEmpty()) {
            Text("Last move", color = Color.White, style = MaterialTheme.typography.titleSmall)
            Box {
                OutlinedButton(onClick = { menu = true }, modifier = Modifier.testTag("setup_last_move")) {
                    Text(lastMoves.firstOrNull { it.enPassant == draft.enPassant }?.label ?: "Unknown / no en passant")
                    Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Unknown / no en passant") }, onClick = {
                        onChange(draft.copy(enPassant = "-")); menu = false
                    })
                    lastMoves.forEach { move ->
                        DropdownMenuItem(text = { Text(move.label) }, onClick = {
                            onChange(draft.copy(enPassant = move.enPassant, halfMoves = "0")); menu = false
                        })
                    }
                }
            }
            Text("Choose the last pawn move only if it is known. This enables en passant.",
                color = AppColors.SubtleText, style = MaterialTheme.typography.bodySmall)
        } else {
            LaunchedEffect(Unit) { menu = false }
        }
    }
}

@Composable
private fun SetupDetails(draft: BoardSetupPosition, onChange: (BoardSetupPosition) -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(value = draft.halfMoves, onValueChange = { onChange(draft.copy(halfMoves = it)) },
                label = { Text("Halfmove counter") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f).testTag("setup_halfmoves"))
            OutlinedTextField(value = draft.fullMove, onValueChange = { onChange(draft.copy(fullMove = it)) },
                label = { Text("Move number") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f).testTag("setup_fullmove"))
        }
        Text("Halfmove counter: moves by both sides since the last pawn move or capture.",
            color = AppColors.SubtleText, style = MaterialTheme.typography.bodySmall)
        Text("FEN", color = Color.White, style = MaterialTheme.typography.titleSmall)
        SelectionContainer { Text(draft.toFen(), color = AppColors.SubtleText, style = MaterialTheme.typography.bodySmall) }
        val clipboard = LocalClipboardManager.current
        TextButton(onClick = { clipboard.setText(AnnotatedString(draft.toFen())) }, enabled = draft.validationError() == null) { Text("Copy FEN") }
    }
}
