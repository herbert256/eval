package com.eval.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private data class HelpEntry(
    val title: String,
    val content: String,
    val icon: String? = null
)

private val helpSections = listOf(
    HelpEntry(
        title = "Welcome to Eval",
        content = "Analyze your chess games from Lichess.org with the powerful Stockfish engine and 9 AI services. " +
            "The app automatically fetches your games and provides deep analysis to help you improve."
    ),
    HelpEntry(
        title = "Getting Started",
        icon = "\uD83D\uDE80",
        content = "Enter your username in the Lichess card and tap 'Retrieve' to fetch games. " +
            "You can also browse tournaments, broadcasts, TV channels, streamers, or import PGN files. " +
            "Player game lists show the opponent's name, a white or black row for the requested player's color, " +
            "and win/lost/draw from that player's perspective (a dash if unfinished). " +
            "Select a game to start the analysis. On startup, the app loads the latest game for your saved Lichess username. " +
            "If no username is saved or retrieval fails, it restores your last analysed game when available."
    ),
    HelpEntry(
        title = "Board setup",
        content = "On Select a game, choose Board setup to build a position by hand. " +
            "Select a piece from either palette and tap a square to place it. Placing a king moves the existing king. " +
            "Drag any existing piece to move it, even with another piece selected for placing. Drop outside the board to remove it. " +
            "Clear, Initial position, Current position (when available), and Flip board help you start. " +
            "Choose White or Black to move. Below it, castling toggles appear when the king and rook are on their home squares. Last move appears when en passant is legal: select the known double pawn push or leave it unknown. Position details contains move counters and Copy FEN. " +
            "Enable castling only if that king and rook have never moved. Last move lists legal double pawn pushes that allow en passant. " +
            "Fix any validation message, then tap Start from this position to open Manual mode. " +
            "Android Back leaves your current game unchanged."
    ),
    HelpEntry(
        title = "Start from url",
        content = "On Select a game, choose Start from url and enter an HTTPS page, PGN, or image address. " +
            "The scanner finds FEN strings, embedded PGN, Lichess analysis/game/study links, PGN downloads, " +
            "and 2D chessboard images. Choose a result to open a game or review a position. " +
            "Image recognition runs on your device. Check the pieces, orientation, side to move, castling and en passant " +
            "before starting. Review position opens Board setup with the found pieces and FEN details. " +
            "Edit pieces directly, use Found position to reset, and Rotate pieces to correct upside-down image recognition. " +
            "Edits survive screen recreation; Android Back returns to results. PGN still uses Open game. " +
            "Photos of physical boards at an angle are not supported. " +
            "For a dynamic page, scroll the embedded page to the board and use Scan page again. " +
            "Large pages are limited to 20 images, 12 game links and 100 results per scan; limits and failed downloads are shown."
    ),
    HelpEntry(
        title = "Supported document files",
        content = "Local files, document URLs, Android shares and clipboard attachments use the same reader. " +
            "Supported documents: PDF, Word DOCX/DOCM, RTF, OpenDocument ODT/ODS/ODP, EPUB, Excel XLSX/XLSM, PowerPoint PPTX/PPTM, " +
            "and ZIP archives containing text/PGN/HTML or images. Plain TXT, FEN, PGN, Markdown, CSV, TSV, JSON and XML are also searched. " +
            "The reader extracts text and embedded PNG/JPEG and other supported images. PDF pages are rendered to find scanned or drawn chessboards. " +
            "Image results must be reviewed. Text in scanned page images is not OCR-transcribed; chess-font diagrams in Word should be exported to PDF. " +
            "Files are limited to 16 MB, text to 2 MB, PDF text to 100 pages and board recognition to 20 images/pages per document. " +
            "Archives are limited to 512 entries, 32 MB expanded and 8 MB per part. Nested archives and embedded Office/PDF attachments are not scanned. " +
            "Limits and unreadable parts are reported. Older binary DOC/XLS/PPT files and password-protected documents must first be saved as unlocked modern Office files or PDF."
    ),
    HelpEntry(
        title = "Start from a local file",
        content = "On Select a game, choose Start from a local file and pick a document or image. " +
            "Text, FEN, PGN, HTML, PDF, Word and other supported documents are searched for chess positions and games. Clear 2D board images use on-device recognition. " +
            "Choose Open game for PGN, or Review position to open the prefilled Board setup editor for FEN and images. " +
            "Correct pieces and position details before starting. " +
            "Choose another file replaces the scan; Scan again retries it; Stop cancels it. Files are limited to 16 MB and the first 2 MB of text are searched. " +
            "See Supported document files for the formats and limits."
    ),
    HelpEntry(
        title = "Camera",
        content = "On Select a game, choose Camera and allow camera access. Point the camera at a 2D chess diagram in a book or on a screen, " +
            "held straight so the whole board is visible. Recognition runs on your device. When the same board is read on two frames in a row, " +
            "Board setup opens with the found pieces for review. Check the pieces, orientation, side to move, castling and en passant before starting. " +
            "Android Back returns to the camera. Photos of physical boards at an angle are not supported."
    ),
    HelpEntry(
        title = "Start from clipboard history",
        content = "Choose one of the last 10 clipboard entries captured by Eval, newest first. " +
            "Android provides the current item when Eval has focus. Copy something and return to Eval to capture it; " +
            "changes while Eval is open are also saved. Earlier keyboard clipboard history and copies made while Eval is in the background cannot be read. " +
            "Select an entry to scan text, FEN, PGN, HTML, URLs, documents or image/file attachments using the same scanner as URL and local-file imports. " +
            "Review image positions before starting. History survives restarts, including readable attachment copies. " +
            "Text is limited to 2 MB and saved files to 16 MB per entry. Identical entries move to the top when copied again; items marked sensitive are not saved. " +
            "Review position opens the prefilled Board setup editor for FEN and image results. " +
            "Remove and Clear history delete Eval's saved copies without changing Android's clipboard."
    ),
    HelpEntry(
        title = "Share to Eval",
        content = "In another Android app, tap Share and choose Eval. Shared URLs open the same scanner as Start from url. " +
            "Shared text, HTML, PGN files, PDF/Office documents and one or more images are checked for games and positions. " +
            "Choose Open game for a PGN, or Review position to check and edit a FEN or recognized board before starting. " +
            "Images are recognized locally and must show a clear 2D chessboard. Files are limited to 16 MB each and 20 files per share. " +
            "Use Stop to cancel or Scan again to retry. Unreadable items are reported while other results remain available."
    ),
    HelpEntry(
        title = "Analysis Stages",
        icon = "\uD83D\uDCCA",
        content = "Games progress through three analysis stages:\n\n" +
            "1. Preview Stage (orange) - Quick scan of all positions (50ms/move)\n" +
            "2. Analyse Stage (blue) - Deep analysis backward (1s/move), tap to skip\n" +
            "3. Manual Stage - Interactive exploration with real-time analysis"
    ),
    HelpEntry(
        title = "Board Navigation",
        icon = "♟",
        content = "Use the navigation buttons:\n\n" +
            "⏮  Go to start\n" +
            "◀  Previous move\n" +
            "▶  Next move\n" +
            "⏭  Go to end\n" +
            "↻  Flip board\n\n" +
            "Tap or drag on the evaluation graph to jump to any position."
    ),
    HelpEntry(
        title = "Evaluation Graphs",
        icon = "\uD83D\uDCC8",
        content = "The line graph shows position evaluation over time:\n" +
            "• Green = good for you\n" +
            "• Red = bad for you\n" +
            "• Yellow line = deep analysis scores\n\n" +
            "The bar graph shows score changes between moves - tall red bars indicate blunders!"
    ),
    HelpEntry(
        title = "Analysis Arrows",
        icon = "↗",
        content = "In Manual stage, tap the ↗ icon to cycle through three arrow modes:\n\n" +
            "• Off - No arrows\n" +
            "• Main line - Best continuation with numbered moves\n" +
            "• Multi-line - One arrow per analysis line with scores\n\n" +
            "Arrow colors can be customized in Settings."
    ),
    HelpEntry(
        title = "AI Position Analysis",
        icon = "\uD83E\uDD16",
        content = "In Manual stage, tap AI logos next to the board to get intelligent analysis from 9 AI services:\n\n" +
            "• ChatGPT (OpenAI)\n" +
            "• Claude (Anthropic)\n" +
            "• Gemini (Google)\n" +
            "• Grok (xAI)\n" +
            "• DeepSeek\n" +
            "• Mistral\n" +
            "• Perplexity\n" +
            "• Together AI\n" +
            "• OpenRouter\n\n" +
            "Configure API keys in Settings > AI Setup."
    ),
    HelpEntry(
        title = "AI Hub",
        icon = "\uD83D\uDCDD",
        content = "Access the AI Hub from the main screen for advanced AI features:\n\n" +
            "• New AI Report - Create custom AI reports with any prompt\n" +
            "• Prompt History - Reuse previously submitted prompts\n" +
            "• AI History - View previously generated reports\n\n" +
            "Reports are saved as HTML files you can view in Chrome or share."
    ),
    HelpEntry(
        title = "AI setup",
        icon = "⚙",
        content = "Settings > AI setup has separate System prompts, Prompts and AI instructions screens. Create, edit and delete each part independently. AI instructions contain instruction text only. Prompt editors offer @ popups; the instruction editor offers both @ and < popups.\n\n" +
            "When requesting any AI report, choose System prompts, Prompts and AI instructions from three dropdowns. The app remembers your last choices, including None. Tap Next to review and edit all three text fields, then Submit to prepare the data and open AI. These edits apply only to this request. Back returns to your choices and keeps edits until you change a selection.\n\n" +
            "Eval sends a data field only when its matching @name@ placeholder is used in the final edited request. This also applies to custom data and the date. Stockfish searches run only for the requested @MOVES@ or @ENGINE@ values. <prompt> and <system> contain the literal prompt text; they never select saved definitions. <model>model@provider</model> selects a model. <default> and <edit> are no longer controls. The AI app replaces placeholders in prompts, system prompts and report presentation; Eval keeps that text unchanged. Repeated placeholders share one data field. Unavailable values are empty.\n\n" +
            "Instruction placeholders: @FEN@, @COLOR@, @SERVER@, @PLAYER@, @PGN@, @MOVES@, @ENGINE@, @BOARD@ and @DATE@. @COLOR@ is White or Black according to the side to move.\n\n" +
            "@MOVES@ contains every legal move at the current position with its Stockfish evaluation. Scores use White's perspective; +M/-M marks mate for White/Black. Settings > Stockfish > Moves list for AI controls time per move, threads, memory and NNUE. Eval shows cancellable progress before handing off the complete list.\n\n" +
            "@ENGINE@ contains Stockfish's best continuations, ranked for the side to move, with scores and search depth. Settings > Stockfish > Engine moves for AI controls the number of lines, time per position, threads, memory and NNUE. A timed progress bar and the Manual analysis card show the latest complete lines. Stop and go to AI ends the search early and sends those lines immediately; Cancel returns without sending. If stopped before a complete set is available, AI receives that explanation instead of scores. Both data sets use the same captured position."
    ),
    HelpEntry(
        title = "Game Sources",
        icon = "\uD83C\uDFAE",
        content = "Lichess.org:\n" +
            "• User games, Tournaments, Broadcasts, TV channels, Streamers, Rankings\n\n" +
            "Local sources:\n" +
            "• PGN files (with ZIP support), ECO openings, FEN positions, Game history"
    ),
    HelpEntry(
        title = "Player Information",
        icon = "\uD83D\uDC64",
        content = "View detailed player profiles with ratings across time controls, " +
            "game statistics, and recent games. " +
            "Generate AI reports about players using the AI Report feature on the player screen."
    ),
    HelpEntry(
        title = "Opening Explorer",
        icon = "\uD83D\uDCD6",
        content = "Enable Opening Explorer in Settings > Interface Elements to see position statistics:\n\n" +
            "• Popular moves played in this position\n" +
            "• Win/Draw/Loss percentages\n" +
            "• Number of games with each move\n\n" +
            "Data from Lichess opening database."
    ),
    HelpEntry(
        title = "Top Bar Icons",
        icon = "\uD83D\uDD27",
        content = "Eval logo  Return to the main Eval screen\n" +
            "📂  Select a game\n" +
            "🔄  Reload latest game\n" +
            "AI logo  In Manual mode, choose the three parts for an AI report\n" +
            "⚙️  Settings\n" +
            "❓  This help screen\n\n" +
            "The screen title is below the fixed icon row and scrolls away with the content. Scroll back to the top to see it again. Use the Android Back gesture or button to return to the previous screen."
    ),
    HelpEntry(
        title = "Exploring Lines",
        icon = "\uD83D\uDD0D",
        content = "In the analysis panel, tap any move in a Stockfish line to explore that variation. " +
            "The board will show the position after those moves. " +
            "Use Android Back to return to the main game position."
    ),
    HelpEntry(
        title = "Export Features",
        icon = "\uD83D\uDCE4",
        content = "Share your analysis:\n\n" +
            "• PGN - Full game with evaluation comments\n" +
            "• GIF - Animated replay of the game\n" +
            "• AI Reports - HTML with interactive board, graphs, and AI analysis\n\n" +
            "Use Android share sheet or email directly from the app."
    ),
    HelpEntry(
        title = "Settings Overview",
        icon = "⚙",
        content = "Customize the app:\n\n" +
            "• Board Layout - Colors, coordinates, player bars, eval bar\n" +
            "• Interface Elements - Show/hide UI per stage\n" +
            "• Graph Settings - Evaluation graph colors and ranges\n" +
            "• Arrow Settings - Arrow modes, colors, count\n" +
            "• Stockfish - Engine settings per analysis stage\n" +
            "• AI Setup - Providers, prompts, agents\n" +
            "• General - Fullscreen, sounds, pagination"
    ),
    HelpEntry(
        title = "Tips",
        icon = "\uD83D\uDCA1",
        content = "• Background color shows result: green (win), red (loss), blue (draw)\n" +
            "• Scores are always from your perspective\n" +
            "• Player bars show remaining clock time when available\n" +
            "• Tap 'Analysis running' banner to jump to biggest mistake\n" +
            "• Enable 'Red border for player to move' to see whose turn it is\n" +
            "• Move list shows colored scores - green moves are good, red are mistakes\n" +
            "• In Manual mode, swipe left for the previous move or right for the next; start on an empty square or an opponent's piece to avoid dragging your own piece\n" +
            "• Double-tap the left half of the board for the start position, or the right half for the last move\n" +
            "• The menu stays fixed while the content below it scrolls, on every screen. Enable Full screen in Settings → General settings to place it at the top edge with no gap. Swipe down from the top to reveal the status bar temporarily. Android navigation remains available."
    ),
    HelpEntry(
        title = "Live Games",
        icon = "\uD83D\uDCFA",
        content = "Follow games in real-time:\n\n" +
            "• Select a game from TV channels or streamers\n" +
            "• Enable 'Auto-follow' to update automatically\n" +
            "• Watch moves appear as they're played\n" +
            "• Analysis updates with each new move"
    ),
    HelpEntry(
        title = "Developer: API Tracing",
        icon = "\uD83D\uDC1B",
        content = "Enable 'Track API calls' in General settings to log all network requests:\n\n" +
            "• All Lichess and AI service calls are logged\n" +
            "• View requests/responses in the trace viewer\n" +
            "• Useful for debugging API issues\n" +
            "• Traces are cleared when tracking is disabled"
    ),
    HelpEntry(
        title = "About",
        icon = "ℹ",
        content = "Eval uses the installed Stockfish engine. Its reported version appears in the Stockfish card.\n\n" +
            "Game data from Lichess.org public APIs.\n\n" +
            "AI analysis from OpenAI, Anthropic, Google, xAI, DeepSeek, Mistral, Perplexity, Together AI, and OpenRouter.\n\n" +
            "All data stored locally on your device."
    )
)

@Composable
fun HelpScreen(
    onBack: () -> Unit
) {
    BackHandler { onBack() }

    EvalScreen(
        backgroundColor = AppColors.DarkBackground,
        topBar = {
            EvalTitleBar(
                title = "Help",
                onBackClick = onBack,
                onEvalClick = onBack
            )
        }
    ) {

        Spacer(modifier = Modifier.height(16.dp))

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            for (section in helpSections) {
                HelpSection(
                    title = section.title,
                    content = section.content,
                    icon = section.icon
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun HelpSection(
    title: String,
    content: String,
    icon: String? = null
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = AppColors.CardBackground
        ),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (icon != null) {
                    Text(
                        text = icon,
                        fontSize = 24.sp
                    )
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = AppColors.AccentBlue
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = content,
                style = MaterialTheme.typography.bodyMedium,
                color = AppColors.LightGray,
                lineHeight = 22.sp
            )
        }
    }
}
