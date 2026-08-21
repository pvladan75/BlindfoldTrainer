package com.program.blindfoldtrainer.core.moduleapi

/**
 * Brojevi u rečenici, na srpskom.
 *
 * Stoji uz ugovor modula jer ga koriste **svi** moduli za isti posao: da kažu
 * šta njihova težina konkretno znači. Bez toga bi svaki pisao svoje „3 figure /
 * 5 figura" i pre ili kasnije bi negde stajalo „5 figure".
 */
fun quantity(count: Int, one: String, few: String, many: String = few): String =
    "$count ${wordFor(count, one, few, many)}"

/**
 * Oblik imenice uz [count].
 *
 * Pravilo je srpsko i ima izuzetak koji se lako previdi: **11 do 14 idu uz
 * množinu**, iako se završavaju na 1 do 4. „11 figura", ne „11 figura" po
 * pravilu za jedinicu.
 */
fun wordFor(count: Int, one: String, few: String, many: String = few): String {
    val lastTwo = count % 100
    if (lastTwo in 11..14) return many

    return when (count % 10) {
        1 -> one
        2, 3, 4 -> few
        else -> many
    }
}

/**
 * Sekunde iz milisekundi, bez suvišne nule: `6_000` → „6", `3_500` → „3,5".
 *
 * Decimalni zarez je namerno zarez, ne tačka — tekst se čita na srpskom.
 */
fun secondsLabel(millis: Long): String {
    val whole = millis / 1000
    val tenth = (millis % 1000) / 100
    return if (tenth == 0L) "$whole" else "$whole,$tenth"
}
