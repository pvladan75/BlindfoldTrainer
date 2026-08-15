package com.program.blindfoldtrainer.core.designsystem.board

import androidx.annotation.DrawableRes
import com.program.blindfoldtrainer.core.chess.Color
import com.program.blindfoldtrainer.core.chess.Piece
import com.program.blindfoldtrainer.core.chess.PieceType
import com.program.blindfoldtrainer.core.designsystem.R

@DrawableRes
fun Piece.drawableRes(): Int = when (color) {
    Color.WHITE -> when (type) {
        PieceType.PAWN -> R.drawable.white_pawn
        PieceType.KNIGHT -> R.drawable.white_knight
        PieceType.BISHOP -> R.drawable.white_bishop
        PieceType.ROOK -> R.drawable.white_rook
        PieceType.QUEEN -> R.drawable.white_queen
        PieceType.KING -> R.drawable.white_king
    }
    Color.BLACK -> when (type) {
        PieceType.PAWN -> R.drawable.black_pawn
        PieceType.KNIGHT -> R.drawable.black_knight
        PieceType.BISHOP -> R.drawable.black_bishop
        PieceType.ROOK -> R.drawable.black_rook
        PieceType.QUEEN -> R.drawable.black_queen
        PieceType.KING -> R.drawable.black_king
    }
}
