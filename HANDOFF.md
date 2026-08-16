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

Aplikacija se gradi, pokreće, i ima **svih šest** modula za trening. Poslednji
build je prošao čisto, bez upozorenja. **131 test, nijedan ne pada.**

**Svih šest modula je prošlo na uređaju**, zajedno sa napretkom, poenima i
rangovima. Dva su proradila tek pošto su ispravljena baga opisana niže — oba iz
iste porodice: prevede se čisto, pukne tek na telefonu.

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
| `:core:chess` | `Board`, `Position`, `MoveGenerator`, `Attacks`, `Fen`, `Search`, `KnightPath`, `San`, `Pgn` | **64** |
| `:core:moduleapi` | ugovor `TrainingModule` | — |
| `:core:designsystem` | tema, `ChessBoard`, `PieceVisibility`, sličice figura | — |
| `:core:audio` | `Speaker` (TTS), `VoiceInput` (Vosk) | **5** |
| `:core:engine` | `ChessEngine` interfejs, `LocalEngine` | — |
| `:core:progress` | `Xp`, `Rank`, `Achievement`, `ProgressSnapshot`, `ProgressRepository` | **27** |
| `:core:data` | Room istorija sesija, DataStore podešavanja | — |
| `:feature:geometry` | Geometrija table | — |
| `:feature:pairs` | Interaktivni parovi | — |
| `:feature:endgame` | Dokrajči protivnika | — |
| `:feature:knightpath` | Putanja skakača | — |
| `:feature:recall` | Zapamti poziciju | **6** |
| `:feature:followgame` | Prati partiju | **5** |
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

### Drugi takav: regularni izraz koji radi na JVM-u, a ne na Androidu

Prati partiju je javljao `NoClassDefFoundError: …core.chess.Pgn` iako je klasa
bila u APK-u. Ispod je stajalo `ExceptionInInitializerError`, a ispod toga pravi
uzrok: `PatternSyntaxException` na izrazu `\{[^}]*}`. **Android-ov regex je
stroži od JVM-ovog** — zalutalu `}` ili `]` JVM prihvata, Android odbija. Statički
inicijalizator objekta zato pukne, a svaki sledeći dodir te klase javlja
`NoClassDefFoundError`, koji izgleda kao problem pakovanja i odvodi na pogrešnu
stranu.

**Nijedan JVM test ovo ne može da uhvati** — isti izraz se kod njih prevede bez
reči. Odbrana je da se zagrade eskejpuju i tamo gde JVM to ne traži.

Ovo je našao lanac uzroka na ekranu (`Throwable.userReason()`); sa samo spoljnim
slojem poruke bi se i dalje tražilo po pakovanju.

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
- `Achievement` — deset dostignuća, od prvog treninga do pet besprekornih zaredom.
- `ProgressSnapshot` — zbir cele istorije, sa razdvojenim napretkom po modulu.

`:core:data` čuva **sirovu istoriju sesija u Room-u, bez poena**. Snimak se
računa iz nje pri svakom čitanju, pa promena pravila prepravi i dosadašnju
istoriju umesto da ostavi zamrznute poene iz starije verzije. Sabiranje je u
Kotlinu, a ne SQL-om, da bi pravilo ostalo na jednom mestu i pokriveno testovima.

**Dostignuća su takođe izvedena, ne upisana.** Nema stanja koje može da se
razmimoiđe sa stvarnošću, a novo dostignuće odmah priznaje i onome ko ga je
odavno zaslužio. U `BrainTrainer`-u su se upisivala u trenutku osvajanja, pa je
novo dostignuće važilo samo za nove igrače.

U meniju je kartica sa rangom, poenima, trakom do sledećeg ranga i brojem
osvojenih dostignuća; sažetak sesije pokazuje osvojene poene, javlja prelazak u
viši rang i nabraja dostignuća osvojena baš tom sesijom.

**Otvorene odluke:**

- Brojevi su prvi predlog, ne dogovor. Pragovi, cena promašaja i uslovi
  dostignuća se menjaju na jednom mestu i istorija se sama preračuna.
- **Rang ništa ne otključava.** U `BrainTrainer`-u je rang držao spisak dostupnih
  modula i težina; ovde je namerno izostavljeno dok se ne dogovori.
- Nema ekrana sa spiskom dostignuća — samo brojač u meniju. Ako treba ekran,
  podaci su već tu (`ProgressSnapshot.achievements`).

---

## Šta ne radi i šta nedostaje

### Glasovni unos — radi, ali nije proveren na uređaju
**Odlučeno: model se preuzima na zahtev korisnika**, ne pakuje se u APK. Razlog
je izbor — kome glas ne treba, taj ne plaća 39 MB preuzimanja ni 67 MB na disku,
a sme i da obriše model kasnije.

`VoskModelStore` u `:core:audio` preuzima
`alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip` preko
`HttpURLConnection` (bez nove biblioteke), raspakuje ga u `filesDir/vosk-model` i
javlja stanje kroz `ModelState`. `VoskVoiceInput` prati to stanje i učitava
`Model(putanja)` čim model postane spreman — glasovni unos se zato pali i gasi u
toku rada, bez ponovnog pokretanja aplikacije.

Tri stvari koje su namerno tako:

- **Nedovršeno preuzimanje se ne pamti kao model.** Pri prekidu ili grešci folder
  se briše, a spremnost se proverava po fajlovima koje Vosk zaista traži
  (`am/final.mdl`, `graph/HCLr.fst`, …), ne po postojanju foldera.
- **Omotački folder iz arhive se skida** — Vosk arhiva ima
  `vosk-model-small-en-us-0.15/` na vrhu, a `Model()` očekuje ono što je unutra.
- **Unos koji vodi van foldera se odbija**, pa arhiva ne može da piše izvan svog
  mesta. `ModelArchiveTest` pokriva sve troje, bez mreže i bez uređaja.

Kartica **Glasovni unos** stoji na dnu menija: stanje, dugme za preuzimanje sa
trakom, prekid, i brisanje modela.

Mikrofon je u **Parovima, Završnici i Prati partiju**. Dugme je jedno —
`VoiceInputButton` u `:core:audio`, uz `VoiceState` — jer su dozvola, stanja i
ponašanje pri odbijanju svuda isti, a tri kopije bi se pre ili kasnije razišle.
Kad glas nije upotrebljiv (model nije preuzet, ili je dozvola odbijena), dugme se
**ne prikazuje** umesto da stoji i ne radi ništa.

Izgovoreno polje ide kroz **isti `onSquareClicked`** kao i dodir, pa nema drugog
puta do odgovora ni druge provere. U Završnici to znači da se potez izgovara u
dva koraka: polazno pa odredišno polje.

Model je preuzet i spreman na uređaju; **prepoznavanje polja još nije probano**.

### Podešavanja

`:core:data` uz istoriju sesija drži i podešavanja (DataStore), a tip i ugovor
(`Settings`, `SettingsRepository`) stoje u `:core:model` — čist Kotlin, pa se
moduli koji ih koriste testiraju bez DataStore-a.

Pravilo po kom je birano šta ide u Podešavanja: **samo ono što zavisi od
korisnika, a ne od toga šta je objektivno bolje.** Glasovne opcije su takve —
koja je bolja zavisi od izgovora, a to aplikacija ne može da zna.

| Podešavanje | Podrazumevano |
|---|---|
| Tema: automatski / svetla / tamna | automatski |
| Brzina izgovaranja (0,5–1,5) | 0,85 — kao pre |
| Jezik izgovora (9 jezika) | engleski |
| Slova kao reči („bravo" umesto „b") | isključeno |
| Slušaj ceo potez (Završnica, jedan pritisak) | isključeno |
| Slovo i broj odvojeno („e", pa „four") | isključeno |

Sve glasovne opcije su podrazumevano **isključene, tj. zatečeno ponašanje** — ko
ništa ne dira, ništa mu se i ne menja.

Dve stvari koje su usput ispravljene: brzinu govora su ranije zakucavala tri
ViewModel-a svaki za sebe, a sada je čita `AndroidSpeaker` iz podešavanja; i
fonetske reči ulaze u Vosk rečnik samo kad su izabrane, jer širi rečnik znači i
više prilika da se pogreši.

**Fonetska abeceda se ne zove „NATO".** Isti standard se zove i ICAO abeceda i
međunarodna radio-telefonska abeceda; skraćenica nekome smeta, a ne dodaje
značenje — pa je i u kodu (`PHONETIC_FILES`, `phoneticAlphabet`) i na ekranu
naziv opisan.

Uz taj prekidač **stoji i spisak reči** (a — alpha, b — bravo, … h — hotel).
Bez njega je podešavanje bilo neupotrebljivo: niko ne zna napamet šta zamenjuje
`f` ili `h`.

**Fonetske reči rade samo uz engleski model.** Engleske su, a Vosk prima samo
reči iz leksikona modela — ćirilični model ih sigurno nema, a za latinične se ne
zna. Zato je prekidač nedostupan uz drugi jezik, ali **ostaje vidljiv** i kaže
šta treba uraditi: pređi na engleski model pa probaj sa njim. Zamišljeni tok je
da korisnik prvo proba polja na svom jeziku, a engleskom se okrene tek ako mu to
ne prolazi.

Izbor se pri promeni jezika **ne briše** nego samo ne dejstvuje
(`usesPhoneticAlphabet` naspram `phoneticAlphabet`), pa se vraća sam kad se vrati
engleski.

### Jezici glasovnog unosa

**Srpskog nema i neće ga biti dok ga Vosk ne objavi.** Provereno na spisku od 76
modela: nema nijedan južnoslovenski jezik — ni srpski, ni hrvatski, bosanski,
slovenački ni makedonski. Jedini put do srpskog bi bio Android-ov
`SpeechRecognizer` iza istog `VoiceInput` interfejsa, uz internet u toku vežbe i
bez uskog rečnika. Odloženo dogovorom.

Podržano je devet jezika: engleski, nemački, ruski, francuski, španski,
italijanski, poljski, češki, turski. Svaki ima svoj folder pod
`filesDir/vosk-model/<kod>`, pa povratak na već preuzet jezik ne traži novo
preuzimanje.

**Dodavanje jezika je jedan unos u `VoiceLanguages`**: ime arhive sa
`alphacephei.com/vosk/models`, veličina, i šesnaest reči — osam za kolone a–h i
osam za redove 1–8. Ništa drugo u aplikaciji ne zna za jezike.

⚠ **Samo je engleski proveren na uređaju.** Reči za ostale jezike su upisane po
pravopisu, a ne po sluhu, i model ih možda uopšte nema u svom rečniku — Vosk
gramatika prima samo reči koje postoje u leksikonu modela. Oznaka `isVerified`
to prati, a test pada ako se lista proverenih promeni a da niko ne primeti.
Kad neko ko jezik govori potvrdi da radi, menja se jedno polje u tabeli.

Ukrajinski je namerno izostavljen: njihov „mali" model je 137 MB, što je van reda
veličine ostalih (35–51 MB).

### APK je 57,7 MB
Skoro sve su Vosk native biblioteke za pet ABI-ja — uključujući `mips`, koji ne
postoji od 2019. Tu se skida tridesetak megabajta, ili filtriranjem ABI-ja ili
podelom po arhitekturi pri objavljivanju.

### Težine u Geometriji se ne razlikuju dovoljno
Primedba sa uređaja: lako i teško deluju isto. Trenutno se razlikuju samo po
broju pitanja i satu, a **pitanje je isto** — „koje je boje polje". Predlog je da
težina menja *vrstu* zadatka: srednje = „jesu li dva polja iste boje", teško =
odnos polja (ista dijagonala, šta leži između). Odloženo dogovorom.

### Nenapisano
- ekran sa spiskom dostignuća; podaci postoje, prikaza nema osim brojača

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

`:feature:followgame` → `games.pgn`, **60 partija, 38 KB**. Izvučene su iz
`C:\Users\Admin\Documents\chessdata\bundesliga2000.pgn` (12.247 partija, 9,1 MB)
uslovima: oba igrača preko 2450, odlučen ishod, između 50 i 90 poluhodova, bez
komentara i varijanti. Uzeta je svaka k-ta koja odgovara, da izbor ne bude samo
prvo kolo. Skript stoji u istoriji ove sesije; ako zatreba drugi izbor, lakše ga
je napisati ponovo nego čuvati.

`GameContentTest` čita **isti fajl koji ide u assets** i pada ako ijedna partija
ne prođe kroz pravila ili ako ijedna pozicija ne ume da ponudi pitanje. To je ona
provera sadržaja pre pakovanja koju arhitektura traži.

`:feature:knightpath` i `:feature:recall` **nemaju sadržaj** — zadaci se
izračunavaju.

Iz `BrainTrainer`-a (`C:\Users\Admin\AndroidStudioProjects\BrainTrainer`,
objavljen na Google Play) preuzete su ideje, ne kod: nepromenljiva tabla,
`PuzzleRules` apstrakcija i gamifikacija. Lestvica rangova je preneta iz
`RankManager`-a (bez zaključavanja sadržaja), a bodovanje i dostignuća su
napisani iznova: `ScoreManager` je čuvao izračunate poene u `SharedPreferences`,
a `AchievementManager` je upisivao dostignuće u trenutku osvajanja — ovde se i
jedno i drugo računa iz istorije.

---

## Predlog redosleda za nastavak

Svih šest modula postoji i radi na uređaju. Ostalo je:

1. Probati glasovni unos na uređaju i videti koje od tri opcije zaista pomažu;
   ako se proba neki jezik osim engleskog, upisati `isVerified` u `VoiceLanguages`
2. Dogovoriti brojeve bodovanja i da li rang išta otključava
3. Ekran sa spiskom dostignuća
4. Više vrsta pitanja u Prati partiju — zasad postoji samo „gde stoji figura"
5. Težine u Geometriji (vidi gore) — odloženo dogovorom
