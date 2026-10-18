package com.program.blindfoldtrainer.core.moduleapi

/**
 * Brojevi u rečenici, na engleskom.
 *
 * Stoji uz ugovor modula jer ga koriste **svi** moduli za isti posao: da kažu
 * šta njihova težina konkretno znači. Bez toga bi svaki pisao svoje „3 pieces /
 * 1 piece" i pre ili kasnije bi negde stajalo „1 pieces".
 *
 * Do 18. oktobra 2026. ovde je stajalo srpsko pravilo sa tri oblika i izuzetkom
 * za 11–14; aplikacija je od tada samo na engleskom, pa su oblika dva.
 */
fun quantity(count: Int, one: String, many: String = one + "s"): String =
    "$count ${wordFor(count, one, many)}"

/**
 * Oblik imenice uz [count]: jednina samo uz **tačno jedan**, sve ostalo —
 * uključujući nulu — ide uz množinu („0 moves", „1 move", „2 moves").
 */
fun wordFor(count: Int, one: String, many: String = one + "s"): String =
    if (count == 1) one else many

/**
 * Sekunde iz milisekundi, bez suvišne nule: `6_000` → „6", `3_500` → „3.5".
 *
 * Decimalna tačka, ne zarez — tekst se čita na engleskom.
 */
fun secondsLabel(millis: Long): String {
    val whole = millis / 1000
    val tenth = (millis % 1000) / 100
    return if (tenth == 0L) "$whole" else "$whole.$tenth"
}
