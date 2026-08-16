package com.program.blindfoldtrainer.core.moduleapi

/**
 * Razlog neuspeha u obliku koji sme na ekran.
 *
 * Postoji zato što se greška u učitavanju sadržaja ne vidi u logu ako telefon
 * nije na kablu — a upravo su takve greške dvaput bile najskuplje u ovom
 * projektu. Uz poruku ide i uzrok: `NoClassDefFoundError` i sličan otkaz često
 * nose pravu grešku tek u `cause`.
 */
fun Throwable.userReason(): String = buildString {
    append(this@userReason::class.java.simpleName)
    message?.let { append(": ").append(it) }

    var cause = this@userReason.cause
    var depth = 0
    while (cause != null && depth < MAX_CAUSE_DEPTH) {
        append("\n← ").append(cause::class.java.simpleName)
        cause.message?.let { append(": ").append(it) }
        cause = cause.cause
        depth++
    }
}

private const val MAX_CAUSE_DEPTH = 3
