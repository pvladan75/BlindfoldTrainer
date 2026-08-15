# Dokle smo stigli

Stanje na dan **15. avgust 2026.** Ovaj fajl služi da se rad nastavi u novoj
sesiji bez ponovnog objašnjavanja.

---

## Verzionisanje

Projekat je pod gitom od **15. avgusta 2026**: grana `master`, prvi komit
`d66a9a6` — 122 fajla, oko 1 MB.

`.gitignore` drži napolju `_stockfish-odlozeno/` (76 MB), NNUE mreže, `build/`,
`.gradle/`, `local.properties` i ključeve za potpisivanje. Pre komita je
provereno da ništa od toga nije ušlo u indeks; najveći fajl u repou je
`feature/pairs/src/main/assets/puzzles.zip` sa 270 KB.

Uz to, u **starom** projektu (`BlindfoldChessCouch`, grana `masterSunfish`) stoje
nekomitovane ispravke Modula 3 iz iste sesije: vraćanje modula u navigaciju,
blindfold animacija i raspakivanje ViewModel-a. Ako se stari projekat napušta,
te izmene svejedno vredi komitovati da se ne izgube.

---

## Šta radi

Aplikacija se gradi, pokreće, i ima tri modula za trening. Poslednji build je
prošao čisto, bez upozorenja. **60 testova, nijedan ne pada.**

**Sva tri modula su prošla na uređaju** — Geometrija table, Interaktivni parovi
i Dokrajči protivnika (poslednji tek pošto je ispravljen bag opisan niže).

```bash
cd C:\Users\Admin\AndroidStudioProjects\BlindfoldTrainer && ./gradlew :app:assembleDebug test
```

```bash
adb install -r C:\Users\Admin\AndroidStudioProjects\BlindfoldTrainer\app\build\outputs\apk\debug\app-debug.apk
```

### Gradle moduli

| Modul | Sadržaj | Testovi |
|---|---|---|
| `:core:model` | `ModuleId`, `Difficulty`, `Capability`, `SessionResult` | — |
| `:core:chess` | `Board`, `Position`, `MoveGenerator`, `Attacks`, `Fen`, `Search` | **37** |
| `:core:moduleapi` | ugovor `TrainingModule` | — |
| `:core:designsystem` | tema, `ChessBoard`, `PieceVisibility`, sličice figura | — |
| `:core:audio` | `Speaker` (TTS), `VoiceInput` (Vosk) | **5** |
| `:core:engine` | `ChessEngine` interfejs, `LocalEngine` | — |
| `:core:progress` | `Xp`, `Rank`, `ProgressSnapshot`, `ProgressRepository` | **18** |
| `:core:data` | Room istorija sesija, `RoomProgressRepository` | — |
| `:feature:geometry` | Geometrija table | — |
| `:feature:pairs` | Interaktivni parovi | — |
| `:feature:endgame` | Dokrajči protivnika | — |
| `:app` | registar, navigacija, meni, sažetak sesije | — |

`:core:model`, `:core:chess` i `:core:progress` su **čist Kotlin, bez Androida** —
zato se ti testovi vrte u sekundi, bez emulatora.

---

## Tri odluke koje drže ceo dizajn

### 1. Registar modula, ne ručna navigacija

U staroj aplikaciji je Modul 3 nestao zato što je iz `when` bloka u
`AppNavigation.kt` ispala jedna linija — modul je postojao, ViewModel je radio,
ali do njega se nije moglo doći i ništa to nije prijavilo.

Ovde se moduli prijavljuju preko Hilt `@IntoSet`, a `AppNavigation` ima **jednu**
rutu `module/{module}/{difficulty}` i ne pominje nijedan modul poimence.

**Dodavanje novog modula = tri koraka:**
1. `include(":feature:novi")` u `settings.gradle.kts`
2. `implementation(project(":feature:novi"))` u `app/build.gradle.kts`
3. klasa koja implementira `TrainingModule` + `@Binds @IntoSet` vezivanje

Meni i navigacija se dalje popune sami.

### 2. `SessionResult` kao jedini kanal za ishod

Svi moduli prijavljuju rezultat istim tipom, kroz `onFinish`. Bodovanje i
napredak se zato pišu **jednom**, u `:core:progress`.

Šav je priključen: `onFinish` u `AppNavigation` upisuje rezultat i to je jedino
mesto u aplikaciji koje dodiruje napredak. Nijedan modul ne zna da bodovanje
postoji — dodavanje modula i dalje ne dira ništa oko poena.

### 3. Nepromenljiva pozicija

`Board` i `Position` su nepromenljivi; `applyMove` vraća **novu** poziciju.
Istorija je obična lista, „vrati potez" je prethodni element, a blindfold
animacija sme da drži staro stanje dok prikazuje novo.

---

## Ispravljeni bagovi iz stare aplikacije

Sva tri su pokrivena testovima u `:core:chess`:

- **Napad pešaka na prazno polje.** Napad se računao generisanjem poteza, a
  pešak dijagonalu daje kao potez samo ako tamo već stoji figura — pa je kralj
  smeo da stane na polje koje pešak brani. `Attacks.kt` sada računa napad
  direktno.
- **Rokada.** Nije se proveravalo ni da top postoji, ni da kralj nije u šahu, ni
  da ne prelazi preko napadnutog polja.
- **Brojač poluhodova.** Uzimanje se proveravalo *posle* primene poteza, pa je
  uvek izgledalo kao da se dogodilo i brojač je zauvek bio 0.

`PerftTest` prebrojava nizove legalnih poteza do zadate dubine na pet poznatih
pozicija. Bilo kakva greška u pravilima ga obori.

---

## Bag koji se video tek na uređaju

Dokrajči protivnika je javljao „Nema pozicija za ovu težinu" iako su JSON fajlovi
bili na mestu, ispravni i spakovani u APK. Uzrok: `EndgamePuzzle` je imao
`private companion object`, i to samo zbog jedne regex konstante. Plugin za
serijalizaciju svoj `serializer()` smešta baš u companion i poziv emituje kroz
IR, bez provere vidljivosti — prevođenje je zato prolazilo čisto, a ART je na
uređaju odbio pristup privatnom polju:

```
IllegalAccessError: Field '…EndgamePuzzle.Companion' is inaccessible
to class '…EndgameCatalog$puzzles$2'
```

Pouka je dvostruka:

- **`@Serializable` klasa ne sme imati privatan companion.** Konstante idu van
  klase, kao privatne vrednosti na nivou fajla.
- **Nemo gutanje greške je koštalo više od samog baga.** Oba kataloga su hvatala
  sve u `runCatching` i vraćala prazan spisak, pa je razlog ostajao samo u logu,
  a modul je izgledao kao da sadržaja naprosto nema. Sada katalozi puštaju grešku
  dalje, a ViewModel-i je loguju **i ispisuju klasu i poruku izuzetka na ekran**,
  ispod poruke da sadržaja nema. Bez toga se uzrok ne bi našao bez telefona na
  kablu.

Uz to, `PuzzleCatalog` sada obriše folder ako raspakivanje `puzzles.zip` pukne na
pola. Ranije bi poluprazan folder zauvek preskakao novi pokušaj, jer se
proveravalo samo da li je prazan.

---

## Stockfish je izbačen

Stockfish 17 ne radi bez NNUE mreže (klasična evaluacija je izbačena u verziji
16), pa je nosio 78 MB, native prevođenje i ograničenje na `arm64-v8a`. Za jedini
modul koji ga koristi — odbrana u dobijenoj završnici — to je bilo neproporcionalno.

Zamenjen je `Search.kt` u `:core:chess`: negamax sa alfa-beta, oko 150 linija.
U oceni je bitna sitnica: kad je materijalna razlika odlučujuća, sam materijal ne
razlikuje dobar potez od lošeg, pa evaluacija dodatno gura jaču stranu da
protivničkog kralja tera ka ivici i da mu prilazi kraljem.

| | pre | posle |
|---|---|---|
| APK | 132 MB | 59,5 MB |
| ABI | samo arm64-v8a | svi |
| C++ build | ~2 min | nema ga |

Izvor, JNI most i mreže stoje u `_stockfish-odlozeno/` (76 MB, gitignored).
Povratak je vraćanje foldera i izmena vezivanja u `EngineModule`. `ChessEngine`
interfejs se nije menjao — `:feature:endgame` nije ni znao za zamenu.

---

## Napredak: poeni i rangovi

`:core:progress` je čist Kotlin i drži celo pravilo:

- `Xp.forSession` — 10/20/35 poena po rešenom zadatku (lako/srednje/teško),
  minus 2 po promašaju, plus 50% za sesiju bez ijedne greške. Nikad ispod nule.
- `Rank` — šest rangova na pragovima 0 / 1.000 / 3.000 / 7.000 / 15.000 / 30.000.
- `ProgressSnapshot` — zbir cele istorije, sa razdvojenim napretkom po modulu.

`:core:data` čuva **sirovu istoriju sesija u Room-u, bez poena**. Snimak se
računa iz nje pri svakom čitanju, pa promena pravila prepravi i dosadašnju
istoriju umesto da ostavi zamrznute poene iz starije verzije. Sabiranje je u
Kotlinu, a ne SQL-om, da bi pravilo ostalo na jednom mestu i pokriveno testovima.

U meniju je kartica sa rangom, poenima i trakom do sledećeg ranga; sažetak
sesije pokazuje osvojene poene i javlja prelazak u viši rang.

**Otvorene odluke:**

- Brojevi su prvi predlog, ne dogovor. Pragovi i cena promašaja se menjaju na
  jednom mestu i istorija se sama preračuna.
- **Rang ništa ne otključava.** U `BrainTrainer`-u je rang držao spisak dostupnih
  modula i težina; ovde je namerno izostavljeno dok se ne dogovori.
- Dostignuća (`AchievementManager` iz `BrainTrainer`-a) još ne postoje.

---

## Šta ne radi i šta nedostaje

### Glasovni unos ne postoji
Vosk jezički model (70 MB) nije u repou. `VoiceInput` to uredno prijavljuje kao
`VoiceState.Unavailable`, ali nijedan modul mikrofon ni ne prikazuje. Treba
rešiti preuzimanje modela pri prvom pokretanju.

### APK je 59,5 MB
Skoro sve su Vosk native biblioteke za pet ABI-ja — uključujući `mips`, koji ne
postoji od 2019. Kad se bude radio glasovni unos, tu se skida tridesetak megabajta.

### Težine u Geometriji se ne razlikuju dovoljno
Primedba sa uređaja: lako i teško deluju isto. Trenutno se razlikuju samo po
broju pitanja i satu, a **pitanje je isto** — „koje je boje polje". Predlog je da
težina menja *vrstu* zadatka: srednje = „jesu li dva polja iste boje", teško =
odnos polja (ista dijagonala, šta leži između). Odloženo dogovorom.

### Nenapisani moduli
- podešavanja (DataStore) — `:core:data` zasad drži samo istoriju sesija
- dostignuća — `:core:progress` ima poene i rangove, dostignuća ne
- `:feature:recall` — Zapamti poziciju
- `:feature:knightpath` — Putanja skakača
- `:feature:followgame` — Prati partiju

### Kvalitet odbrane u teškim pozicijama
Jedina rezerva oko zamene motora. U K+2L protiv K (mat u 19) je Stockfish bio
osetno jači. Ako odbrana deluje mlako, prvo probati veću dubinu u `setupFor`
unutar `EndgameViewModel` — sad je 10/12/14, uz vremenski rok pretrage od 1,5 s.

---

## Odakle sadržaj

Sve prenето iz stare aplikacije, format nepromenjen pa nije trebalo ponovo
generisati:

- `:feature:pairs` → `puzzles.zip`, 37 fajlova, 276 KB
- `:feature:endgame` → `{easy,medium,hard}_puzzles.json`
- `:core:designsystem` → vektorske sličice figura

Iz `BrainTrainer`-a (`C:\Users\Admin\AndroidStudioProjects\BrainTrainer`,
objavljen na Google Play) preuzete su ideje, ne kod: nepromenljiva tabla,
`PuzzleRules` apstrakcija i gamifikacija. Lestvica rangova je preneta iz
`RankManager`-a (bez zaključavanja sadržaja), bodovanje je napisano iznova jer
je `ScoreManager` čuvao izračunate poene u `SharedPreferences`. Dostignuća iz
`AchievementManager`-a tek treba preneti.

---

## Predlog redosleda za nastavak

1. Proveriti napredak na uređaju — odigrati sesiju i videti da poeni i rang rastu
2. Dogovoriti brojeve bodovanja i da li rang išta otključava
3. Preuzimanje Vosk modela, pa mikrofon u Parovima i Završnici
4. Dostignuća u `:core:progress`
5. Preostala tri modula
