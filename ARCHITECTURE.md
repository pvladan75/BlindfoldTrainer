# Arhitektura

Aplikacija za trening igre na slepo. Nasleđuje ideje iz `BlindfoldChessCouch`
(moduli, Stockfish, Vosk, TTS) i `BrainTrainer` (nepromenljiva tabla, pravila
modula, gamifikacija) — uz ispravke problema koje su oba imala.

## Načelo

Školjka se piše jednom, moduli se na nju kače. Školjka nosi navigaciju, temu,
tablu, glas, zvuk, podešavanja i napredak; modul nosi samo svoju vežbu.

## Gradle moduli

```
:core:model         ModuleId, Difficulty, SessionResult, Capability
:core:chess         čist Kotlin — Board, Position, MoveGenerator, Attacks, Fen, Search, KnightPath, San, Pgn, Reconstruction
:core:moduleapi     ugovor TrainingModule
:core:engine        ChessEngine interfejs + LocalEngine (ugrađena pretraga)
:core:audio         Speaker (TTS), VoiceInput (Vosk) i zone za režim bez ekrana
:core:designsystem  tema i ChessBoard komponenta
:core:progress      čist Kotlin — bodovanje, rangovi, sabiranje napretka
:core:data          Room istorija sesija i DataStore podešavanja, iza interfejsa
:feature:geometry   Geometrija table
:feature:pairs      Interaktivni parovi
:feature:endgame    Dokrajči protivnika
:feature:knightpath Putanja skakača
:feature:recall     Zapamti poziciju
:feature:followgame Prati partiju
:feature:dictation  Postavi po diktatu
:app                navigacija iz registra, DI, glavni meni
```

**Jedan modul — jedno uputstvo.** „Postavi po diktatu" je zaseban modul, a ne još
jedna težina u „Zapamti poziciju", iako dele i tablu i paletu i ocenjivanje.
Razlog nije tehnički: vežbe se razlikuju po tome **šta se od korisnika traži**, a
modul koji ume dve različite stvari mora obe da objasni. Ono što im je zajedničko
stoji u `:core:chess` (`Reconstruction.kt`) i deli se odatle.

Dostignuća postoje u `:core:progress`, ali još nemaju svoj ekran.

Režim bez ekrana stoji u `:core:audio`: `EyesFreeControls` prima spisak zona
(`EyesFreeRow`, `EyesFreeZone`, `MicrophoneZone`) i ne zna ni za jedan modul.
Modul sastavlja svoj raspored, a dozvola za mikrofon, poruka zašto glas ne radi,
vibracije i potvrda u dva dodira ostaju na jednom mestu — pet kopija toga bi se
pre ili kasnije razišlo.

Podešavanja imaju pravilo: **u njih ide samo ono što zavisi od korisnika, a ne
od toga šta je objektivno bolje.** Glasovne opcije su takve — koja je bolja
zavisi od izgovora, a to aplikacija ne može da zna. Sve podrazumevano stoji na
zatečenom ponašanju.

`:core:model`, `:core:chess` i `:core:progress` su **čist Kotlin, bez Androida**. Testovi šahovske
logike se zato vrte u sekundama, bez emulatora — a upravo je tu bilo najviše
grešaka u staroj aplikaciji.

## Tri odluke koje nose ceo dizajn

### 1. Registar modula umesto ručne navigacije

U staroj aplikaciji je modul 3 nestao zato što je iz `when` bloka u
`AppNavigation.kt` ispala jedna linija. Modul je i dalje postojao, ViewModel je
radio, ali do njega se nije moglo doći — i ništa to nije prijavilo.

Ovde se navigacija i glavni meni **generišu iz registra**. Modul se prijavljuje
preko Hilt `@IntoSet`, pa modul koji postoji a nije dostupan prestaje da bude
moguć.

```kotlin
interface TrainingModule {
    val id: ModuleId
    val titleRes: Int
    val descriptionRes: Int
    val iconRes: Int
    val difficulties: List<Difficulty>
    val needs: Set<Capability>
    val supportsEyesFree: Boolean

    @Composable
    fun Screen(args: ModuleArgs, onFinish: (SessionResult) -> Unit)
}
```

`needs` postoji da bi školjka tražila dozvolu za mikrofon i podigla Stockfish
**pre** ulaska u modul, umesto da svaki modul to petlja sam.

`supportsEyesFree` radi isto za režim bez ekrana: modul prijavljuje da li se
njegova vežba uopšte da odraditi zonama i glasom, a meni to kaže **pre** ulaska.
Bez toga bi se saznalo tek unutra, pred tablom u koju se ne gleda. Zasad ga
nema samo „Zapamti poziciju" — rekonstrukcija ide vraćanjem figura iz palete, a
glasovni unos prepoznaje samo polja.

### 2. `SessionResult` kao jedini kanal za rezultat

Svi moduli prijavljuju ishod istim tipom. Zahvaljujući tome se bodovanje,
rangovi, dostignuća i statistika pišu jednom, u `:core:progress`.

U `BrainTrainer`-u se `ScoreManager` zvao ručno sa desetak mesta u
`ChessScreen.kt` — zato je dodavanje novog modula tamo skupo.

Šav je sada priključen: `onFinish` u `AppNavigation` upisuje rezultat i to je
**jedino** mesto u aplikaciji koje dodiruje napredak. Nijedan modul ne zna da
bodovanje postoji.

**Čuva se sirova istorija, ne poeni.** Bodovanje i rangovi se računaju iz nje
pri svakom čitanju, pa promena pravila prepravi i celu dosadašnju istoriju
umesto da ostavi zamrznute poene iz starije verzije. Pravilo je čista funkcija
`Xp.forSession` i testira se bez baze i bez Androida.

### 3. Nepromenljiva pozicija

`Board` i `Position` su nepromenljivi; `applyMove` vraća **novu** poziciju.
Istorija partije je obična lista, „vrati potez" je uzimanje prethodnog elementa,
a blindfold animacija može slobodno da drži staro stanje dok prikazuje novo.

Stara aplikacija je imala `MutableMap` koji se menjao u mestu, pa je svaki
ViewModel morao da radi `board.copy()` na pravim mestima — i ponegde nije.

## Ispravljeni bagovi iz stare aplikacije

**Napad pešaka.** Napad se računao tako što bi se generisali potezi i gledalo da
li neki vodi na polje. Pešak dijagonalu generiše kao potez samo ako tamo već
stoji protivnička figura, pa prazno polje koje pešak brani nije izgledalo
napadnuto i kralj je smeo da stane na njega. Sada je `Attacks.kt` odvojen od
generisanja poteza i računa napad direktno.

**Rokada.** Nije se proveravalo ni da top postoji, ni da kralj nije u šahu, ni
da ne prelazi preko napadnutog polja. `Board.makeMove` je čak *stvarao* novog
topa ako ga nije bilo. Sada su svi uslovi u `MoveGenerator.generateCastlingMoves`.

**Brojač poluhodova.** Uzimanje se proveravalo *posle* primene poteza, pa je
uvek izgledalo kao da se dogodilo i brojač je zauvek bio 0. Sada se računa iz
table pre poteza.

Sve troje pokriva `PerftTest` — prebrojavanje nizova legalnih poteza do zadate
dubine, upoređeno sa objavljenim vrednostima. Odstupanje bilo gde u pravilima
obara test.

## Motor

Umesto Stockfish-a stoji `Search.kt` u `:core:chess` — negamax sa alfa-beta.
Stockfish 17 ne radi bez NNUE mreže (klasična evaluacija je izbačena u verziji
16), pa bi za jedini modul koji motor koristi nosio 78 MB, native prevođenje i
ograničenje na jedan ABI.

U oceni je bitno to što se, kad je materijalna razlika odlučujuća, dodaje član
koji jaču stranu gura da protivničkog kralja tera ka ivici i da mu prilazi
kraljem — bez toga su u dobijenoj završnici svi potezi jednako ocenjeni.

`ChessEngine` interfejs je ostao, pa povratak na spoljni motor ne dira nijedan
modul. Odloženi izvor stoji u `_stockfish-odlozeno/`.

## Sadržaj i veliki fajlovi

Stari repo je narastao na 157 MB jer su NNUE mreža (75 MB) i Vosk model (70 MB)
bili u gitu. Ovde NNUE više ne postoji, a Vosk model se **preuzima na zahtev
korisnika** (`VoskModelStore`) — nije ni u repou ni u APK-u. Ko vežba dodirom, ne
plaća 39 MB preuzimanja; ko ga preuzme, sme i da ga obriše.

Zagonetke idu u `assets` kao JSON po konvenciji `{modul}_{tezina}_puzzles.json`,
kao u `BrainTrainer`-u. `UniversalPuzzleSolver` (BFS nad pravilima modula) služi
da se **pre pakovanja** proveri da je svaka zagonetka rešiva.
