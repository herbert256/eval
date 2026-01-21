package com.chessreplay.ui

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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun HelpScreen(
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1A1A1A))
            .padding(16.dp)
    ) {
        // Header with back button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Text("←", fontSize = 28.sp, color = Color.White)
            }
            Text(
                text = "Help",
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.width(48.dp))
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Scrollable content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Welcome section
            HelpSection(
                title = "Welcome to Chess Replay",
                content = "Analyze your Lichess games with the powerful Stockfish 17.1 engine. " +
                    "The app automatically fetches your games and provides deep analysis to help you improve."
            )

            // Getting Started
            HelpSection(
                title = "Getting Started",
                icon = "🚀",
                content = "Enter your Lichess username and tap 'Retrieve' to fetch your recent games. " +
                    "Select a game to start the analysis. The app remembers your last game for quick startup."
            )

            // Analysis Stages
            HelpSection(
                title = "Analysis Stages",
                icon = "📊",
                content = "Games go through three analysis stages:\n\n" +
                    "1. Preview Stage (orange) - Quick scan of all positions\n" +
                    "2. Analyse Stage (blue) - Deep analysis, tap to skip\n" +
                    "3. Manual Stage - Interactive exploration"
            )

            // Navigation
            HelpSection(
                title = "Board Navigation",
                icon = "♟",
                content = "Use the navigation buttons to move through the game:\n\n" +
                    "|<  Go to start\n" +
                    "<   Previous move\n" +
                    ">   Next move\n" +
                    ">|  Go to end\n\n" +
                    "You can also swipe left/right on the board or tap the evaluation graph."
            )

            // Evaluation Graph
            HelpSection(
                title = "Evaluation Graphs",
                icon = "📈",
                content = "The top graph shows position evaluation over time:\n" +
                    "• Green = good for you\n" +
                    "• Red = bad for you\n" +
                    "• Yellow line = deep analysis scores\n\n" +
                    "The bottom graph shows score changes between moves - " +
                    "tall red bars indicate blunders!"
            )

            // Arrows
            HelpSection(
                title = "Analysis Arrows",
                icon = "↗",
                content = "In Manual stage, arrows show Stockfish's recommended moves:\n\n" +
                    "• Gray arrow icon = arrows off\n" +
                    "• White arrow icon = main line arrows\n" +
                    "• Blue arrow icon = multi-line arrows with scores\n\n" +
                    "Tap the arrow icon to cycle through modes."
            )

            // Top Bar Icons
            HelpSection(
                title = "Top Bar Icons",
                icon = "🔧",
                content = "↻  Reload latest game from Lichess\n" +
                    "≡  Return to game selection\n" +
                    "↗  Toggle arrow display mode\n" +
                    "⚙  Settings (engine & board)\n" +
                    "?  This help screen"
            )

            // Exploring Lines
            HelpSection(
                title = "Exploring Lines",
                icon = "🔍",
                content = "In the analysis panel, tap any move in a Stockfish line to explore that variation. " +
                    "The board will show the position after those moves. " +
                    "Use navigation to return to the main game."
            )

            // Settings
            HelpSection(
                title = "Settings",
                icon = "⚙",
                content = "Customize the analysis in Settings:\n\n" +
                    "• Stockfish Settings - Analysis depth, time, threads\n" +
                    "• Arrow Settings - Colors and display options\n" +
                    "• Board Layout - Colors and coordinates"
            )

            // Tips
            HelpSection(
                title = "Tips",
                icon = "💡",
                content = "• The background color shows game result: green (win), red (loss), blue (draw)\n" +
                    "• Scores are always from your perspective - positive is good for you\n" +
                    "• Player bars show remaining clock time when available\n" +
                    "• Tap the Analyse stage banner to jump to the biggest mistake"
            )

            // About
            HelpSection(
                title = "About",
                icon = "ℹ",
                content = "Chess Replay uses the Stockfish 17.1 chess engine for analysis. " +
                    "Stockfish is the world's strongest open-source chess engine.\n\n" +
                    "Game data is fetched from lichess.org via their public API."
            )

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
            containerColor = Color(0xFF2A2A2A)
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
                    color = Color(0xFF6B9BFF)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = content,
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFFCCCCCC),
                lineHeight = 22.sp
            )
        }
    }
}
