package com.program.blindfoldtrainer.core.designsystem.board

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.program.blindfoldtrainer.core.chess.Board
import com.program.blindfoldtrainer.core.chess.Color
import com.program.blindfoldtrainer.core.chess.Square
import com.program.blindfoldtrainer.core.designsystem.theme.BoardBorder
import com.program.blindfoldtrainer.core.designsystem.theme.BoardDark
import com.program.blindfoldtrainer.core.designsystem.theme.BoardLight
import com.program.blindfoldtrainer.core.designsystem.theme.SquareError
import com.program.blindfoldtrainer.core.designsystem.theme.SquareHighlight
import com.program.blindfoldtrainer.core.designsystem.theme.SquareHint
import com.program.blindfoldtrainer.core.designsystem.theme.SquareSuccess

/** Zašto je polje obojeno. Boju bira tema, ne pozivalac. */
enum class SquareTint { HIGHLIGHT, SUCCESS, ERROR, HINT }

/**
 * Koje se figure vide. Ovo je srce aplikacije za igru na slepo, pa ima svoj tip
 * umesto niza bool-ova — stara aplikacija je imala
 * `piecesVisible || square in visiblePieceSquares` na pozivnom mestu, što je
 * lako pogrešiti i teško pročitati.
 */
sealed interface PieceVisibility {
    /** Normalna tabla. */
    data object All : PieceVisibility

    /** Potpuno naslepo — polja se vide, figure ne. */
    data object None : PieceVisibility

    /** Samo navedena polja, ostalo je nevidljivo. Za animaciju poteza naslepo. */
    data class Only(val squares: Set<Square>) : PieceVisibility

    fun shows(square: Square): Boolean = when (this) {
        All -> true
        None -> false
        is Only -> square in squares
    }

    companion object {
        // Square je value class, pa vararg nad njim nije dozvoljen —
        // otud imenovane pomoćne funkcije umesto jednog varijadičnog poziva.
        fun only(square: Square): Only = Only(setOf(square))
        fun only(first: Square, second: Square): Only = Only(setOf(first, second))
    }
}

@Composable
fun ChessBoard(
    board: Board,
    modifier: Modifier = Modifier,
    orientation: Color = Color.WHITE,
    tints: Map<Square, SquareTint> = emptyMap(),
    visibility: PieceVisibility = PieceVisibility.All,
    showCoordinates: Boolean = true,
    onSquareClick: ((Square) -> Unit)? = null
) {
    val ranks = if (orientation == Color.WHITE) (7 downTo 0) else (0..7)
    val files = if (orientation == Color.WHITE) (0..7) else (7 downTo 0)
    val bottomRank = ranks.last()
    val leftFile = files.first()

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(6.dp))
            .background(BoardBorder)
            .padding(3.dp)
    ) {
        Column {
            for (rankIndex in ranks) {
                Row(modifier = Modifier.weight(1f)) {
                    for (fileIndex in files) {
                        val square = Square(rankIndex * 8 + fileIndex)
                        ChessSquare(
                            square = square,
                            board = board,
                            tint = tints[square],
                            visibility = visibility,
                            fileLabel = if (showCoordinates && rankIndex == bottomRank) square.file.toString() else null,
                            rankLabel = if (showCoordinates && fileIndex == leftFile) square.rank.toString() else null,
                            modifier = Modifier
                                .weight(1f)
                                .then(
                                    if (onSquareClick != null) {
                                        Modifier.clickable { onSquareClick(square) }
                                    } else {
                                        Modifier
                                    }
                                )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChessSquare(
    square: Square,
    board: Board,
    tint: SquareTint?,
    visibility: PieceVisibility,
    fileLabel: String?,
    rankLabel: String?,
    modifier: Modifier = Modifier
) {
    val baseColor = if (square.isLight) BoardLight else BoardDark
    val targetColor = when (tint) {
        SquareTint.HIGHLIGHT -> SquareHighlight
        SquareTint.SUCCESS -> SquareSuccess
        SquareTint.ERROR -> SquareError
        SquareTint.HINT -> SquareHint
        null -> baseColor
    }
    // Isticanja se pale i gase stalno; nagli skok boje na tabli je nemiran.
    val color by animateColorAsState(targetValue = targetColor, label = "tint-$square")
    val labelColor = if (square.isLight) BoardDark else BoardLight

    // Sakrivena figura se za čitač ekrana ponaša isto kao prazno polje —
    // inače bi TalkBack izdao ono što oko ne sme da vidi.
    val visiblePiece = board[square]?.takeIf { visibility.shows(square) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(baseColor)
            .background(color)
            .semantics {
                contentDescription = visiblePiece?.let { "$square, $it" } ?: square.toString()
            },
        contentAlignment = Alignment.Center
    ) {
        if (visiblePiece != null) {
            Image(
                painter = painterResource(id = visiblePiece.drawableRes()),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(3.dp)
            )
        }

        rankLabel?.let {
            CoordinateLabel(it, labelColor, Modifier.align(Alignment.TopStart).padding(start = 2.dp, top = 1.dp))
        }
        fileLabel?.let {
            CoordinateLabel(it, labelColor, Modifier.align(Alignment.BottomEnd).padding(end = 2.dp, bottom = 1.dp))
        }
    }
}

@Composable
private fun CoordinateLabel(text: String, color: ComposeColor, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = color,
        fontSize = 9.sp,
        fontWeight = FontWeight.Bold,
        modifier = modifier
    )
}
