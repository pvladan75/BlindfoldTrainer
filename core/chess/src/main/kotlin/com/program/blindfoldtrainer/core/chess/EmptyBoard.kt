package com.program.blindfoldtrainer.core.chess

/**
 * Domet figure po **praznoj tabli**.
 *
 * Odvojeno od `MoveGenerator`-a iz istog razloga iz kog i [KnightPath]: ovde se
 * ne igra partija. Nema drugih figura, pa nema ni uzimanja, ni zaklanjanja, ni
 * šaha — samo geometrija. Zbog toga se sme računati napamet, bez pozicije.
 *
 * Ovo je jedini deo koji „Kretanje figura" traži od šaha. Pravilo o naizmeničnoj
 * dami i zabrana ponavljanja **nisu ovde**: to nisu šahovska pravila nego pravila
 * vežbe, i stoje u modulu koji ih izmišlja.
 */
object EmptyBoard {

    /**
     * Polja do kojih [type] stiže **jednim potezom** sa [from], po praznoj tabli.
     *
     * Pešak vraća prazno: njegov potez zavisi od boje i od toga ima li šta da
     * uzme, pa na praznoj tabli nema smisla — a tiho guranje nije ono što ovaj
     * modul vežba.
     */
    fun reach(from: Square, type: PieceType): List<Square> = when (type) {
        PieceType.KNIGHT -> KnightPath.movesFrom(from)
        PieceType.BISHOP -> slide(from, BISHOP_DIRECTIONS)
        PieceType.ROOK -> slide(from, ROOK_DIRECTIONS)
        PieceType.QUEEN -> slide(from, ALL_DIRECTIONS)
        PieceType.KING -> step(from, ALL_DIRECTIONS)
        PieceType.PAWN -> emptyList()
    }

    /**
     * Polja iz [reach] koja leže na koloni [fileIndex] (0 = 'a').
     *
     * Ovo je pitanje zadatka „Domet na liniji" doslovno: presek zraka figure i
     * jedne kolone. Prazan odgovor je **valjan** — skakač sa e5 ne stiže ni na
     * jedno polje b-linije.
     */
    fun reachOnFile(from: Square, type: PieceType, fileIndex: Int): List<Square> =
        reach(from, type).filter { it.fileIndex == fileIndex }

    /** Isto, samo po redu [rankIndex] (0 = red 1). */
    fun reachOnRank(from: Square, type: PieceType, rankIndex: Int): List<Square> =
        reach(from, type).filter { it.rankIndex == rankIndex }

    /** Klizanje do ivice table po datim smerovima. */
    private fun slide(from: Square, directions: List<Pair<Int, Int>>): List<Square> =
        buildList {
            for ((fileStep, rankStep) in directions) {
                var fileIndex = from.fileIndex + fileStep
                var rankIndex = from.rankIndex + rankStep
                while (true) {
                    add(Square.of(fileIndex, rankIndex) ?: break)
                    fileIndex += fileStep
                    rankIndex += rankStep
                }
            }
        }

    /** Jedan korak u svakom smeru — kralj. */
    private fun step(from: Square, directions: List<Pair<Int, Int>>): List<Square> =
        directions.mapNotNull { (fileStep, rankStep) ->
            Square.of(from.fileIndex + fileStep, from.rankIndex + rankStep)
        }
}
