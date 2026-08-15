# Dokle smo stigli

Stanje na dan **15. avgust 2026.** Ovaj fajl služi da se rad nastavi u novoj
sesiji bez ponovnog objašnjavanja.

---

## ⚠ Prvo i najvažnije: projekat nije pod verzionisanjem

`C:\Users\Admin\AndroidStudioProjects\BlindfoldTrainer` **nije git repo**. Sav
dosadašnji rad postoji samo kao fajlovi na disku. Pre nego što se nastavi:

```bash
cd C:\Users\Admin\AndroidStudioProjects\BlindfoldTrainer && git init && git add -A && git commit -m "Skelet: jezgro, registar modula, tri modula za trening"
```

`.gitignore` je već postavljen kako treba — drži napolju `_stockfish-odlozeno/`,
NNUE mreže, `local.properties` i ključeve za potpisivanje.

Uz to, u **starom** projektu (`BlindfoldChessCouch`, grana `masterSunfish`) stoje
nekomitovane ispravke Modula 3 iz iste sesije: vraćanje modula u navigaciju,
blindfold animacija i raspakivanje ViewModel-a. Ako se stari projekat napušta,
te izmene svejedno vredi komitovati da se ne izgube.

---

## Šta radi

Aplikacija se gradi, pokreće, i ima tri modula za trening. Poslednji build je
prošao čisto, bez upozorenja. **42 testa, nijedan ne pada.**

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
| `:feature:geometry` | Geometrija table | — |
| `:feature:pairs` | Interaktivni parovi | — |
| `:feature:endgame` | Dokrajči protivnika | — |
| `:app` | registar, navigacija, meni, sažetak sesije | — |

`:core:model` i `:core:chess` su **čist Kotlin, bez Androida** — zato se ti
testovi vrte u sekundi, bez emulatora.

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

Svi moduli prijavljuju rezultat istim tipom, kroz `onFinish`. Zahvaljujući tome
će se bodovanje i napredak pisati **jednom**, u `:core:progress`.

Šav postoji i radi — ali ga zasad **niko ne sluša**: rezultat stigne do
`SessionSummaryDialog` i tu stane. To je bilo dogovoreno.

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

## Šta ne radi i šta nedostaje

### Nije provereno na uređaju
Na telefonu je pokrenuta **samo Geometrija table**. `:feature:pairs` i
`:feature:endgame` su prošli build ali **nikad nisu izvršeni**. Prvo što treba
uraditi u novoj sesiji je instalirati i proći kroz oba.

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
- `:core:data` — DataStore podešavanja, Room napredak
- `:core:progress` — XP, rangovi, dostignuća (šav postoji, funkcija ne)
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
`PuzzleRules` apstrakcija, i gamifikacija (`RankManager`, `AchievementManager`,
`ScoreManager`) koja tek treba da se prenese u `:core:progress`.

---

## Predlog redosleda za nastavak

1. `git init` i prvi komit — pre svega ostalog
2. Instalirati i proći kroz Parove i Dokrajči protivnika; to su dva modula koja
   nikad nisu izvršena
3. `:core:data` + `:core:progress` — priključiti `SessionResult` na bodovanje
4. Preuzimanje Vosk modela, pa mikrofon u Parovima i Završnici
5. Preostala tri modula
