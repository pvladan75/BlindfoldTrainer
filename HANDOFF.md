# Dokle smo stigli

Stanje na dan **16. avgust 2026.** Ovaj fajl služi da se rad nastavi u novoj
sesiji bez ponovnog objašnjavanja.

**Gde smo stali:** svih šest modula radi na uređaju; napredak, podešavanja i
glasovni unos rade. Poslednje na čemu se radilo je **režim bez ekrana**, koji je
sa Završnice proširen na **pet od šest modula**.

Prva proba na uređaju je prošla — sve radi. Odatle su stigle dve primedbe, obe
ispravljene: zone su preraspodeljene na **50 / 25 / 25** (donje dve su bile
pretanke) i **orijentacija je zaključana na portret**. To još nije viđeno.

---

## Verzionisanje

Projekat je pod gitom od **15. avgusta 2026**: grana `master`, prvi komit
`d66a9a6` — 122 fajla, oko 1 MB.

`.gitignore` drži napolju `_stockfish-odlozeno/` (76 MB), NNUE mreže, `build/`,
`.gradle/`, `local.properties` i ključeve za potpisivanje. Pre komita je
provereno da ništa od toga nije ušlo u indeks; najveći fajl u repou je
`feature/pairs/src/main/assets/puzzles.zip` sa 270 KB.

Ispravke Modula 3 u **starom** projektu (`BlindfoldChessCouch`, grana
`masterSunfish`) su komitovane — `796508f`, vraćanje modula u navigaciju,
blindfold animacija i raspakivanje ViewModel-a. Stari projekat se odatle napušta.

---

## Šta radi

Aplikacija se gradi, pokreće, i ima **svih šest** modula za trening. Poslednji
build je prošao čisto, bez upozorenja. **140 testova, nijedan ne pada.**

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

**Izbor jezika i paket su na jednom mestu, u Podešavanjima.** Ranije je meni
nudio preuzimanje a jezik se birao u Podešavanjima — korisnik je preuzimao paket
ne videvši za koji je jezik. Sada meni nosi samo obaveštenje koje vodi tamo.

Tok je: izabereš jezik iz spiska → **Instaliraj paket** → **Koristi jezik**.
Drugo dugme stoji nedostupno dok paketa nema, jer bi prelazak na jezik bez paketa
ugasio glasovni unos a korisnik ne bi znao zašto. Veličina preuzimanja piše samo
uz jezik koji **nije** instaliran; uz instalirane stoji kvačica.

Moduli koji primaju glas prijavljuju to kroz `Capability.VOICE_INPUT`, a meni na
njihovoj kartici prikaže mikrofon. To je prva stvarna upotreba `needs` polja iz
ugovora modula.

Mikrofon je u **Parovima, Završnici i Prati partiju**. Dugme je jedno —
`VoiceInputButton` u `:core:audio`, uz `VoiceState` — jer su dozvola, stanja i
ponašanje pri odbijanju svuda isti, a tri kopije bi se pre ili kasnije razišle.

**Dugme se ne skriva kad glas nije upotrebljiv.** Prvo je skrivano, pa se sa
uređaja javilo „mikrofon nisam mogao da uključim" — a nije se moglo razaznati da
li fali paket, dozvola, ili je samo pogrešan trenutak u vežbi. To je isti onaj
nemi otkaz koji je u ovom projektu već dvaput skupo koštao. Sada dodir kaže šta
nedostaje: da paket nije preuzet, da dozvola nije data, ili da se paket još
priprema.

**Mikrofon se gasi na dodir i sam od sebe.** Sa uređaja je stigla i prijava da je
ostao upaljen posle „echo eight": ako izgovoreno nije polje — a „eight" lako ode
u „ate" — Vosk je slušao **bez ograničenja**, a dodir na dugme nije radio ništa
jer `listenForSquare` izlazi odmah kad je već u slušanju.

Prvi pokušaj ispravke nije bio dovoljan, i to je dobra ilustracija: dodir jeste
gasio, ali je `Surface` imao `enabled = isPlayerTurn`, pa **čim korisnik odustane
ili pređe na sledeći zadatak dugme prestane da prima dodir** — a baš tad je
najpotrebnije. Zato sada:

- **za drugo polje se mikrofon više ne gasi.** Uz „slušaj ceo potez" se ranije
  gasio pa palio posle 250 ms, i tu bi umeo tiho da ne krene: prethodni snimač se
  još zatvara kad se traži novi. Umesto pogađanja te pauze, `listenForSquares`
  sada **ostaje da sluša** dok modul traži još jedno polje;
- slušanje ima rok od 10 sekundi;
- dugme je dodirljivo **uvek dok sluša**, i onda kad vežba više ne očekuje
  odgovor (`enabled || isListening`);
- `stopService()` je pod `runCatching`, da izuzetak pri zatvaranju ne ostavi
  stanje zauvek na „slušam";
- ViewModel-i gase mikrofon i sami — pri odustajanju, pri prelasku na sledeći
  zadatak i na kraju sesije.

Naravoučenije za dalje: **kad dugme ume da radi dve stvari, `enabled` sme da
gasi samo jednu od njih.**

#### „Slušaj ceo potez" je slušao jedno po jedno polje

Podešavanje je značilo: prepoznaj prvo polje, pa **nastavi** da slušaš drugo.
Korisnik je, sasvim razumno, izgovarao **ceo potez u jednom dahu** — „b four g
four". Vosk to vrati kao jedan izgovor, `parseSpokenInput` sastavi tokene u
`b4g4`, a to nije polje — pa se ćutalo.

`SpokenInput.Move` sada čita četiri znaka kao dva polja, a `deliver` predaje oba
polja **iz istog izgovora**, jedno za drugim, kao da su dva puta dodirnuta.
Modul koji traži samo jedno polje (Parovi, Prati partiju, Skakač) uzme prvo i
prekine — njihov ugovor se nije promenio.

Nastavak slušanja i dalje postoji, za onoga ko zastane između polja.

#### Potez se sme reći i preko imena figure

„rook e two" — polazište Završnica nađe sama, iz legalnih poteza. Radi **uporedo**
sa izgovaranjem polja i **ništa se ne bira**: rečnik je jedan spisak reči, a šta
je rečeno vidi se tek pri čitanju. Svi ovi oblici prolaze kroz isti pritisak:

| rečeno | ispada |
|---|---|
| „e four" | polje — bira figuru, sledeće polje dovršava potez |
| „e four e two" | ceo potez |
| „rook e two" | figura i odredište |
| „rook e four e two" | potez, uz imenovanu figuru — koja se **proverava** |

**Kad na isto polje mogu dve iste figure, ne pogađa se.** Kaže se da su dve i
traži se polazište. To nije korisnikova greška nego nedorečenost, pa se i ne
broji kao promašaj — a usput je i korisna vest: dve figure koje gađaju isto polje
su baš ono što naslepo izmiče.

#### Pogrešno ime figure obara potez

Prva verzija je ime uz oba polja odbacivala kao suvišno — polja ionako sve kažu.
Sa uređaja je stigla primedba koja to obara: dok na c3 stoji **dama**, prošla su
sva četiri oblika, uključujući „rook c three c two". Potez se odigrao, potvrda je
bila „c tri, c dva", i korisnik je mogao da ode dalje verujući da mu je top na c2.

> Koliko je dobro oruđe, toliko je i lak prelaz u zabludu.

Ime figure zato **nije ukras nego tvrdnja** o tome šta korisnik misli da tamo
stoji. Ako se ne slaže sa tablom, tabla se ne dira i kaže se šta je zaista tu:
„Na ce tri nije top nego dama." Broji se kao promašaj, isto kao nemoguć potez —
i jedno i drugo je pogrešna predstava o poziciji, a ne omaška u izgovoru.

#### Potez se izgovara sa figurom

Iz iste primedbe: potvrda „c tri, c dva" ne kaže **šta** se pomerilo, a naslepo je
to pola slike. Završnica sada izgovara i figuru — „dama", pa „c tri, c dva" — i za
tvoj potez i za odgovor motora. Tako se potez i najavljuje u pravoj partiji
naslepo.

Boja se ne izgovara: strane se smenjuju, pa bi bila samo duža rečenica.
`SpeechWords` već ima imena figura po jeziku, pa je i ovo dvojezično bez ijedne
nove tabele.

**Ostali moduli su namerno ostavljeni na poljima.** U Parovima i Prati partiju je
figura deo onoga što se pamti; izgovoriti je značilo bi rešiti pola zadatka.

**Imena figura postoje samo na engleskom.** Dodata su posle polja i proverena su
samo tamo; ostali jezici ih nemaju i rade kao i pre, poljima. Test drži da ih
jezik ili ima sva ili nema nijedno — pola spiska bi značilo da se „rook e two"
razume a „bishop e two" ne, bez ikakvog znaka zašto.

#### Čitanje ide reč po reč, a ne spajanjem

Ovo je omogućilo i jedno i drugo. `parseSpokenInput` je ranije sve tokene lepio u
jedan niz i tražio tačno jedno polje; sada prolazi **reč po reč** i sklapa kolonu
i red u polje čim se sretnu, pa se ono što se skupi tumači na kraju.

Uz to se `[unk]` — ono što Vosk vrati za izgovor van gramatike — **preskače**, dok
reč koja nije ni to ni iz rečnika i dalje obara ceo izgovor. Razlika je namerna:
`[unk]` znači „nešto je rečeno, ne znam šta", a to je bezbedno preskočiti; sve
ostalo znači da izgovor nije razumljen i tu se ništa ne pogađa.

Zato prolazi i cela rečenica: **„rook from e four to e two"**. „from" model ne zna
pa dođe kao `[unk]` i otpadne; „to" čuje kao „two" — engleski ih izgovara isto —
pa stigne kao red bez kolone ispred sebe i takav se propušta. Ostaje top, e4, e2.

Veznici se **namerno ne dodaju u rečnik**: „to" i „two" su ista reč po zvuku, pa
bi svako „e two" postalo neizvesno. Bolje je pustiti ih kroz `[unk]` nego uneti
dvosmislenost u ono što radi.

Traženje ovog baga je otišlo u dva promašena kruga i vredi zapisati zašto:
prijava je bila „ponavlja se prvo polje", a to je zvučalo kao da se isto polje
predaje dvaput. Zapravo je to bila **potvrda polazišta** koju aplikacija sama
izgovara — radila je tačno ono što treba. Pravi trag je bio u drugoj polovini
iste rečenice: „kad izgovorim oba polja, ne dešava se ništa."

**Pouka: kad prijava sadrži i ono što radi i ono što ne radi, uzrok je gotovo
uvek u drugom delu.**

Usput su zatvorena i dva stvarna ponavljanja: `onFinalResult` se **ne predaje**
(stiže pri gašenju i ponavlja ono što je već stiglo kroz `onResult`), a predaja
ima najmanji razmak od pola sekunde — granica je ljudska, dva izgovora se ne
smene tako brzo.

Mikrofon je aktivan **samo kad se očekuje odgovor** — u Parovima kad potez
stigne, u Završnici kad je korisnik na potezu, u Prati partiju kad stoji pitanje.
Van toga je izbledeo namerno.

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
| Bez ekrana (vežbanje zatvorenih očiju) | isključeno |
| Brzina izgovaranja (0,5–1,5) | 0,85 — kao pre |
| Jezik izgovora (9 jezika, koliko uređaj ima glasova) | engleski |
| Jezik prepoznavanja (9 jezika) | engleski |
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

### Dva jezika, dva smera — ne mešati

U aplikaciji postoje **dva jezika i lako se brkaju**, jer se odnose na suprotne
smerove:

| | ko govori | šta određuje |
|---|---|---|
| **Prepoznavanje** | korisnik → aplikaciji | koji se Vosk model preuzima i koje se reči slušaju |
| **Izgovor** | aplikacija → korisniku | TTS glas kojim se čitaju potezi |

**Oba se biraju odvojeno**, u dve kartice sa izričitim naslovima: „Izgovor —
aplikacija govori tebi" i „Prepoznavanje — ti govoriš aplikaciji".

Zavise od različitih stvari, i to je razlog razdvajanja:

- prepoznavanje traži **preuzet Vosk paket** (~40 MB);
- izgovor traži **TTS glas na uređaju**, koji se ne preuzima kroz aplikaciju.

`AndroidSpeaker` pri podizanju proveri `isLanguageAvailable` za svaki jezik i
objavi spisak; Podešavanja iz njega znaju šta sme da se ponudi, a jezici bez
glasa stoje zatamnjeni sa oznakom „nema glas". Ako izabrani jezik ipak ostane bez
glasa, čita se **engleski** — bolje razumljiv engleski nego ćutanje.

**Srpski postoji za izgovor, iako za prepoznavanje ne postoji.** Zato su to i
dva odvojena skupa: `SpeechLanguage` (10, sa srpskim) i `VoiceLanguage` (9, po
Vosk modelima). Izgovor traži samo glas na uređaju, a glasa za srpski ima na
mnogim telefonima.

To usput ispravlja i zatečeni nesklad: modul je oduvek izgovarao srpske rečenice
(„Mat! Čestitamo.") kroz **engleski** glas, jer drugog nije bilo.

**Dve tabele, ali usaglašene.** `SpeechLanguages` nosi reči za govor,
`VoiceLanguages` za slušanje. Test ih drži zajedno: za svaki jezik i svih 64
polja, ono što aplikacija izgovori mora da prođe kroz njeno sopstveno
prepoznavanje — inače govori polje koje sama ne bi razumela.

Formatiranje je zato prešlo iz proširenja u `Speaker` (`say(move)`, `say(square)`,
`say(board)`): zavisi od jezika, a moduli za jezik ne znaju niti treba da znaju.

### Čitanje pozicije

`Board.spoken(words)` daje „beli kralj na e dva, bela dama na e pet. crni kralj
na ha šest" — **beli pa crni, a unutar boje kralj pa dama pa ostalo**. Redosled je
uvek isti da bi se pozicija pamtila kao niz, a ne kao skup; test to i čuva.

**Čita se u delovima** — „bela dama na", pa „e pet", pa sledeća figura — jer se
naslepo pamti u dva koraka: šta stoji, pa gde stoji.

Između delova je stajala tišina, prvo 200 ms pa 50 ms. **Sada je nema.** Sa
uređaja je stiglo da ne treba: motor i sam zastane na zarezu i tački kojima
`spokenParts` razdvaja figure i strane, pa je pauza samo usporavala čitanje.
Podela na delove ostaje — po njoj se ponavlja i po njoj se prekida.

**Izgovor ume i da sačeka svoj red** (`interrupt = false`). Motor ponekad odgovori
pre nego što se dovrši izgovor tvog poteza, pa bi ga presekao na pola reči — a
bez ekrana je taj izgovor jedina potvrda šta je razumela. U Završnici zato red
ide: tvoj potez, potez motora, ishod, sledeća pozicija — nijedan ne preseca
prethodni. Preseca samo ono što ti sam zatražiš: „ponovi" i „čitaj poziciju".

`Speaker` barata **spiskom delova**, a ne jednim tekstom: po toj podeli se
ponavlja, prekida i pušta u red. Tišina koja je između njih nekad stajala
(`playSilentUtterance`) je izbačena, ali je podela ostala korisna i bez nje.

Rod se slaže uz figuru: u srpskom je dama ženskog roda, u ruskom i ladja i
peška, u francuskom dama i top. `SpeechWords.femininePieces` to nosi po jeziku.

Završnica sada pozicija **i pročita**, ne samo prikaže, i ima dva dugmeta:

- **Ponovi** — doslovno ponavlja poslednje izgovoreno. Stoji u `Speaker`-u, pa
  radi u svakom modulu bez ijedne izmene u modulu. **Ne broji se** — nisi dočuo,
  to nije slabost.
- **Čitaj poziciju** — čita trenutno stanje table. **Broji se**, jer znači da se
  slika u glavi raspala. Ne kao kazna nego kao merilo: kad taj broj vremenom
  padne sa pet na nulu, to je dokaz napretka. Namerno je **neograničen** — kome
  ide teže, taj sme da pita koliko god puta treba.

### Režim bez ekrana

Uključuje se u Podešavanjima („Bez ekrana → Vežbaj zatvorenih očiju") i radi u
**pet od šest modula**. Tabla se ne crta; ekran je samo površina za dodir:

```
┌───────────────────────────────┐
│                               │
│           MIKROFON            │   50%
│                               │
├───────────────┬───────────────┤
│    PONOVI     │   POZICIJA    │   25%
├───────────────┴───────────────┤
│      ODUSTANI (dva puta)      │   25%
└───────────────────────────────┘
```

Zone, a ne dugmad: prst se ne cilja nego spusti. Mikrofon je najveći i **gore**,
jer se najviše koristi i najteže promašuje; dodirom se i pali i gasi.

Pomoćne zone su namerno **ispod njega, a ne na samom vrhu**. Prvo su bile na vrhu
i sa uređaja je stiglo da se tamo ne pogađa bez gledanja — vrh zauzimaju sat i
otvor za kameru. Iz istog razloga se poštuju sistemske ivice
(`WindowInsets.safeDrawing`); bez toga je `enableEdgeToEdge` gurao zonu pod
statusnu traku.

**Svaka zona vibrira drugačije** — to je jedina povratna informacija koja stiže
pre govora. Uz to vibrira i **svaki prelazak u slušanje**, ne samo dodir: kad se
mikrofon upali sam, za drugi deo poteza, dodira nema pa nema ni njegove
vibracije, a bez znaka se ne zna da je živ.

Odustajanje traži dva dodira, jer je jedino nepovratno; prvi dodir kaže „Dodirni
ponovo da odustaneš". **Dug pritisak na istu zonu poništava potez** — ista meta,
drugačiji dodir, pa raspored ostaje isti.

#### Raspored je isti u svakom modulu

Tri pojasa, uvek istim redom, jer se meta pamti rukom a ne čitanjem:

| pojas | šta stoji | zašto tu |
|---|---|---|
| **gore, 50%** | ono što se traži **sad** | najveća meta za radnju koja se najviše koristi |
| **sredina, 25%** | pomoć: ponavljanje i čitanje stanja | ispod glavne zone, van sata i otvora za kameru |
| **dole, 25%** | izlaz (dva dodira) | jedino nepovratno, pa najdalje od palca u pokretu |

Odnos stoji na jednom mestu (`MAIN_ZONE_WEIGHT`, `HELPER_ZONE_WEIGHT`), pa se
menja jednom za svih pet modula.

**Zone se dugo nisu ni širile po visini.** Sa uređaja je stiglo da su donje dve
tanke trake, sa praznim ekranom ispod. Prva sumnja je bila na odnos 55/25/20 i on
je promenjen na 50/25/25 — i ništa se nije videlo, jer uzrok nije bio tu:

> `weight` unutar `Row` deli **širinu**, a unutar `Column` **visinu**.

Redovi su dobijali svoj deo visine, ali ga nisu prosleđivali zonama u sebi, pa se
svaka zona skupljala na visinu svog natpisa. Mikrofon je izgledao ispravno samo
zato što stoji **direktno u koloni**, gde `weight` i jeste visina. Zone sada
dobijaju `fillMaxHeight()`.

Pouka je opštija od ovog ekrana: **kad promena razmere ne pomeri ništa, razmera
nije ni bila u igri.** Drugo podešavanje istog broja je bilo protraćeno; slika sa
uređaja je pokazala uzrok za sekund.

**Orijentacija je zaključana na portret** dok su zone na ekranu. Zone se dele po
visini, pa bi u pejzažu postale niske trake; uz to bi okretanje telefona usred
vežbe — a on se u ruci baš tako i drži — premestilo sve mete. Zaključava se u
`EyesFreeControls`, ne u manifestu: ostatak aplikacije se gleda i sme da se
okreće, a zatečena vrednost se pamti i vraća pri izlasku.

Šta je „ono što se traži sad" zavisi od modula:

| Modul | Gornja zona | Sredina | Dole |
|---|---|---|---|
| **Dokrajči protivnika** | mikrofon | PONOVI · POZICIJA | ODUSTANI, dugo: PONIŠTI |
| **Interaktivni parovi** | mikrofon | PONOVI · POZICIJA | ODUSTANI |
| **Putanja skakača** | mikrofon | PONOVI · STANJE | ODUSTANI |
| **Prati partiju** | SLEDEĆI POTEZ **ili** mikrofon | PONOVI | PREKINI |
| **Geometrija table** | SVETLO · TAMNO | PONOVI | PREKINI |

Dve stvari koje odatle slede:

- **Geometrija nema mikrofon** i ne treba joj: odgovor je jedan od dva, pa su
  sami odgovori i glavne zone. Uvoditi glas da bi se reklo „svetlo" bilo bi
  sporije od dodira, a tražilo bi preuzet paket od 40 MB ni za šta.
- **U Prati partiju gornja zona menja značenje po fazi** — dok partija teče to je
  sledeći potez, a čim stigne pitanje postaje mikrofon. Prst se ne premešta;
  vibracija i izgovor kažu šta je pogođeno. Zone koja bi u toj fazi bila mrtva
  nema, jer bi mrtva meta na najboljem mestu bila gora od promašaja.

#### Šta je moralo da se doda modulima

Režim se nije mogao samo „upaliti" — dva modula nisu imala čime da rade:

- **Putanja skakača nije imala mikrofon.** Odgovor je niz polja, a sa četiri zone
  se polje ne unosi. Modul sada prijavljuje `VOICE_INPUT` i `SPEECH_OUTPUT`, a
  mikrofon je dobio i običan režim — isti `VoiceInputButton` kao svuda.
- **Prati partiju nije imala govor uopšte**, iako je `SPEECH_OUTPUT` odavno
  prijavljivala. Potezi su se samo ispisivali. Sada se izgovaraju **poljima, ne
  skraćenim zapisom**: „Nf3" nijedan TTS ne pročita kao potez, „g1, f3" se čita
  svuda i isto.

Uz to, u Geometriji se **sat produžava za 1,5 s kad se pitanje izgovara**. Na
teškom je rok 3,5 s, a izgovor polja pojede skoro sekundu — bez dodatka bi se
merilo slušanje umesto računanja.

#### Zone su postale podaci

`EyesFreeControls` više ne zna ni za jedan modul. Prima spisak redova
(`EyesFreeRow` / `EyesFreeZone`) i, po želji, `MicrophoneZone`; modul sastavlja
svoj raspored. Dozvola za mikrofon, poruka zašto glas ne radi, vibracije i
potvrda u dva dodira ostaju **na jednom mestu** — da se pet kopija ne bi razišlo.

Mikrofon je posebna vrsta zone, a ne obična, baš zbog toga: uz njega ide dozvola
i objašnjenje otkaza, i to je isto u svakom modulu.

#### Dve rupe koje su se videle tek pri širenju

- **Odustajanje bez ekrana je ostavljalo vežbu da stoji.** U Završnici je
  odustajanje otkrivalo figure i čekalo dugme „sledeća pozicija" — kog u zonama
  nema, a otkrivene figure se ionako ne vide. Sada odustajanje bez ekrana samo
  najavi i pređe dalje. Isto važi i u Parovima.
- **Podešavanja su se čitala prekasno.** Svi moduli su `eyesFree` čitali iz
  kolektora koji tek treba da emituje, a prvi zadatak je kretao odmah — pa je
  prva pozicija umela da uđe u fazu pamćenja koju bez ekrana **nije čime
  završiti**. Sada se prvo podešavanje sačeka (`settings.first()`) pre nego što
  sesija krene. Isto je ispravljeno i u Završnici, gde je greška i nastala.

#### Zapamti poziciju ostaje na ekranu — zasad

Jedini modul bez ovog režima. Vežba se rešava **vraćanjem figura iz palete na
tablu**, a to je i meta koja se dodiruje i izbor koji se gleda — zone tu ne
pomažu, jer „beli top" nije meta koja se spusti prstom.

Da modul režim nema piše **u meniju, na njegovoj kartici**, i to samo kad je
režim uključen. Ugovor modula je dobio `supportsEyesFree`, po istoj logici po
kojoj postoji i `needs`: modul sam prijavljuje šta ume, a školjka to prikaže. Bez
toga bi se saznalo tek unutra, pred tablom u koju se ne gleda — a nemi otkaz je
u ovom projektu već dvaput skupo koštao.

**Odluka je ponovo otvorena** — vidi dve ideje niže.

### Zapamti poziciju: dva čista oblika umesto jednog hibrida

Primedba koja modul postavlja na svoje mesto: vežba ima **ulaz** i **izlaz**, i
oni ne moraju biti istog roda. Trenutni modul ih meša — pozicija se **vidi**, a
odgovara se **dodirom**:

| ulaz \ izlaz | dodir po tabli | izgovor |
|---|---|---|
| **vidi se** | ovo je današnji modul | — |
| **čuje se** | **ideja 2** | **ideja 1** |

**Ideja 1 — čuje se, izgovara se.** To je režim bez ekrana: aplikacija pročita
poziciju, a ti je izdiktiraš nazad, figuru po figuru.

Ovo je izvodljivije nego kad je prvi put odbijeno, iz dva razloga:

- **Imena figura sada postoje u rečniku** (zasad engleski), pa „white rook e two"
  ima čime da se prepozna. Fale još samo dve reči po jeziku — boje.
- **Redosled ne mora da se pamti.** Prvi predlog je tražio da se pozicija vrati
  redom kojim je pročitana, pa bi jedna promašena reč pomerila ceo niz. Ako se
  govori figura pa polje, odgovor je **skup**, a `gradeRecall` već poredi skupove
  — pogođena, pogrešna i propuštena polja. Ocena je time gotova bez ijedne izmene.

Ostaje da se reši samo kraj: kad se zna da je korisnik završio. Najčistije je po
broju figura — zna ga i aplikacija i korisnik, jer je pozicija upravo pročitana.

**Ideja 2 — čuje se, namešta se na tabli.** Aplikacija izdiktira poziciju, a ti
je složiš od figura iz palete. **Nije režim bez ekrana** — tabla je ovde nužna,
pa ide kao zaseban modul (ili kao vrsta zadatka u ovom).

Ovo je verovatno **najjeftinije od svega što je ostalo**: faza slaganja već
postoji i ne dira se, paleta i ocenjivanje takođe, a menja se samo faza pamćenja
— umesto da se pozicija prikaže, ona se pročita. Glasovni unos nije potreban
uopšte, pa ne traži ni Vosk paket.

Vredi i sama po sebi: gradnja slike u glavi **iz zapisa** je baš ono što
blindfold traži, a nijedan modul to zasad ne uvežbava u tom smeru.

Obe su **zabeležene, nisu dogovorene** — ni koja ide prva, ni da li je druga nov
modul ili nova težina postojećeg.

### Poništavanje poteza

Postoji zbog glasovnog unosa: ako te pogrešno razume, odigra se potez koji nisi
rekao — a bez ekrana se to ni ne vidi. Do sada se moglo samo odustati od cele
pozicije.

Tri odluke, dogovorene:

- **Vraća oba poteza**, tvoj i odgovor motora. Tvoj potez je taj odgovor i
  izazvao, pa bi vraćanje samo jednog ostavilo poziciju koja u partiji nije ni
  postojala.
- **Ne broji se kao greška** — nije tvoja greška nego njena.
- **Dug pritisak**, ne peta zona: raspored zona je već podešen prema palcu i ne
  vredi ga kvariti radi radnje koja se retko koristi.

Poništavanje prekida i motor koji misli i zakazano učitavanje sledeće pozicije,
pa radi i kad je ishod već objavljen. Posle njega se pozicija ponovo pročita —
to je ispravka, a ne pomoć, pa se **ne broji** kao čitanje pozicije.

U običnom režimu isto stoji kao dugme **PONIŠTI**, uz PONOVI i POZICIJU.

**Sve što se ranije samo videlo sada se i čuje** — to je pravilo bez kog režim ne
postoji:

- odigran potez se izgovara („e dva, e četiri"), jer je bez ekrana to jedina
  potvrda da je prepoznato ono što je rečeno;
- ishod se izgovara, ne samo ispisuje;
- kraj sesije se izgovara sa rezultatom;
- sledeća pozicija se sama učita i pročita.

Jedna posledica koju build ne hvata: **faze pamćenja u ovom režimu nema.** Ona se
inače završava dugmetom „zapamtio sam", kog ovde nema — pa bi se u njoj zaglavilo.
Čitanje pozicije *jeste* pamćenje, pa se kreće odmah na potez. Isto važi i u
Parovima, gde se pozicija sad pročita pa se odmah pušta prvi potez.

Ostaje za dalje:

- **Sažetak sesije je i dalje samo vizuelni dijalog.** Kraj se izgovara sa
  rezultatom, ali se iz dijaloga izlazi dugmetom koje se bez ekrana ne vidi. To
  je jedino mesto gde režim još „propada" nazad na gledanje.
- Odluka oko potvrde prepoznatog poteza (sada se potez odigra pa objavi;
  alternativa je pitati pre poteza).

### Jezici prepoznavanja

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
osam za redove 1–8. Uz njih smeju i šest imena figura, ali su neobavezna: jezik
bez njih radi poljima. Ništa drugo u aplikaciji ne zna za jezike.

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

1. **Probati zone koje se sad zaista šire po visini**, zaključan portret i
   „slušaj ceo potez" bez gašenja mikrofona. Sve troje su ispravke po primedbama
   sa uređaja i nisu još viđene.
2. **Izlaz iz sažetka sesije bez ekrana** — vidi gore; kraj se čuje, ali se
   dijalog zatvara dugmetom koje se ne vidi.
3. Dogovoriti brojeve bodovanja i da li rang išta otključava
4. Ekran sa spiskom dostignuća
5. Više vrsta pitanja u Prati partiju — zasad postoji samo „gde stoji figura"
6. Težine u Geometriji (vidi gore) — odloženo dogovorom
7. Ako se proba neki jezik osim engleskog, upisati `isVerified` u
   `VoiceLanguages` odnosno `SpeechLanguages`; imena figura postoje samo na
   engleskom i dopunjuju se istim putem
8. **Zapamti poziciju — dva čista oblika** (vidi gore). Ideja 2 („čuje se,
   namešta se na tabli") je najjeftinija: faza slaganja i ocenjivanje već
   postoje, menja se samo faza pamćenja i ne traži glasovni unos. Ideja 1
   („čuje se, izgovara se") je režim bez ekrana i traži još samo dve reči po
   jeziku — imena boja

**Otvoreno pitanje koje se nije zatvorilo:** da li potvrđivati prepoznat potez
pre nego što se odigra. Sada se odigra pa objavi, uz poništavanje — zaključeno
da je to brže od pitanja pred svaki potez, ali odluka nije proverena kroz duže
korišćenje.
