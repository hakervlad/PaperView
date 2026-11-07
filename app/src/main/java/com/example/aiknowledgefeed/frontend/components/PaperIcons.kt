package com.example.aiknowledgefeed.frontend.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

// Eine kleine, einheitliche Vektorfamilie ohne zusätzliche Icon-Abhängigkeit.
object PaperIcons {
    val Discover = icon("Entdecken", "M12 3 A9 9 0 1 1 11.99 3 M16 8 L14 14 L8 16 L10 10 Z")
    val Knowledge = icon("Wissen", "M12 5 C9 3 5 3 3 4 L3 19 C6 18 9 18 12 20 C15 18 18 18 21 19 L21 4 C18 3 15 3 12 5 L12 20")
    val Link = icon("Verbindung", "M10 13 L14 9 M8 15 L6 17 A3.5 3.5 0 0 1 1 12 L5 8 A3.5 3.5 0 0 1 10 8 M14 16 A3.5 3.5 0 0 0 19 16 L23 12 A3.5 3.5 0 0 0 18 7 L16 9")
    val Arrow = icon("Weiter", "M5 12 L19 12 M13 6 L19 12 L13 18")
    val Back = icon("Zurück", "M19 12 L5 12 M11 6 L5 12 L11 18")
    val Search = icon("Suche", "M10.5 3 A7.5 7.5 0 1 1 10.49 3 M16 16 L21 21")
    val Close = icon("Schließen", "M6 6 L18 18 M18 6 L6 18")
    val Check = icon("Gespeichert", "M5 12 L10 17 L19 7")
    val Plus = icon("Hinzufügen", "M12 5 L12 19 M5 12 L19 12")
    val Sun = icon("Helles Design", "M12 8 A4 4 0 1 1 11.99 8 M12 2 L12 4 M12 20 L12 22 M2 12 L4 12 M20 12 L22 12 M5 5 L6.5 6.5 M17.5 17.5 L19 19 M5 19 L6.5 17.5 M17.5 6.5 L19 5")
    val Moon = icon("Dunkles Design", "M20.5 14.5 A9 9 0 0 1 9.5 3.5 A8 8 0 1 0 20.5 14.5 Z")
    val Spark = icon("Impuls", "M12 3 L14.5 9.5 L21 12 L14.5 14.5 L12 21 L9.5 14.5 L3 12 L9.5 9.5 Z")

    private fun icon(name: String, data: String): ImageVector = ImageVector.Builder(
        name = name, defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f,
    ).addPath(
        pathData = addPathNodes(data), fill = null,
        stroke = SolidColor(Color.Black), strokeLineWidth = 1.7f,
        strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
    ).build()
}
