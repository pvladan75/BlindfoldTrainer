# Dokle smo stigli

Stanje na kraju **17. avgusta 2026.** Ovaj fajl služi da se rad nastavi u novoj
sesiji bez ponovnog objašnjavanja.

**Gde smo stali:** aplikacija ima **sedam** modula. Napredak, podešavanja,
govor i glasovni unos rade. Ova sesija je donela tri celine:

1. **Režim bez ekrana** je sa Završnice proširen na **pet od šest** starih
   modula. Probano na uređaju; po primedbama odatle ispravljeno je da se zone
   zaista šire po visini, podela je 50 / 25 / 25, a portret je zaključan.
2. **Glasovni unos je dobio potez, ne samo polje.** Prolaze „e four e two",
   „rook e two" i cela rečenica „rook from e four to e two" — uporedo, bez
   biranja režima. Pogrešno ime figure obara potez umesto da ga odigra.
3. **Nov modul „Postavi po diktatu"** — pozicija se izgovori, ti je složiš na
   tabli. Jedini modul koji ide od zapisa ka slici u glavi.

**Sve tri celine su viđene na uređaju**, zaključno sa **17. avgustom 2026**:
diktat u konačnom obliku, izgovaranje celog poteza u jednom dahu, i izgovor
poteza sa imenom figure. Te provere su zatvorile i poslednju rupu u režimu bez
ekrana — sažetak sesije sada ima zone i izgovara šta se sad može.

**Šta nije viđeno na uređaju:** baš ta izmena sažetka, jer je napisana posle
poslednjeg probanja.

---

## Verzionisanje

Projekat je pod gitom od **15. avgusta 2026**: grana `master`, prvi komit
`d66a9a6` — 122 fajla, oko 1 MB. Objavljen je na
`github.com/pvladan75/BlindfoldTrainer`.

`.gitignore` drži napolju `_stockfish-odlozeno/` (76 MB), NNUE mreže, `build/`,
`.gradle/`, `local.properties` i ključeve za potpisivanje. Pre komita je
provereno da ništa od toga nije ušlo u indeks; najveći fajl u repou je
`feature/pairs/src/main/assets/puzzles.zip` sa 270 KB.

Ispravke Modula 3 u **starom** projektu (`BlindfoldChessCouch`, grana
`masterSunfish`) su komitovane — `796508f`, vraćanje modula u navigaciju,
blindfold animacija i raspakivanje ViewModel-a. Stari projekat se odatle napušta.

---

## Šta radi

Aplikacija se gradi, pokreće, i ima **osam** modula za trening. Poslednji build
je prošao čisto, bez upozorenja. **222 testa, nijedan ne pada.**

**Svih sedam modula je prošlo na uređaju**, zajedno sa napretkom, poenima i
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
| `:core:model` | `ModuleId`, `Difficulty`, `Capability`, `SessionResult`, `Skill`, `Support`, `TaskSpec` | **15** |
| `:core:chess` | `Board`, `Position`, `MoveGenerator`, `Attacks`, `Fen`, `Search`, `KnightPath`, `San`, `Pgn`, `Reconstruction` | **70** |
| `:core:moduleapi` | ugovor `TrainingModule` | — |
| `:core:designsystem` | tema, `ChessBoard`, `PieceVisibility`, sličice figura | — |
| `:core:audio` | `Speaker` (TTS), `SpeechPhrases`, `VoiceInput` (Vosk), zone bez ekrana | **52** |
| `:core:engine` | `ChessEngine` interfejs, `LocalEngine` | — |
| `:core:progress` | `Xp`, `Rank`, `Achievement`, `ProgressSnapshot` (uz profil po veštinama), `ProgressRepository` | **31** |
| `:core:data` | Room istorija sesija, DataStore podešavanja | — |
| `:feature:geometry` | Geometrija table | — |
| `:feature:pairs` | Interaktivni parovi | — |
| `:feature:endgame` | Dokrajči protivnika | — |
| `:feature:knightpath` | Putanja skakača | — |
| `:feature:recall` | Zapamti poziciju | — |
| `:feature:followgame` | Prati partiju | **5** |
| `:feature:dictation` | Postavi po diktatu | — |
| `:feature:minefield` | Kroz minsko polje | — |
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

## Veštine su kičma, moduli su alat

Dogovoreno **18. avgusta 2026**. Ništa od ovoga još nije napisano u kodu; ovaj
odeljak postoji da se ne izmišlja iznova pri svakom sledećem modulu.

Do sada su moduli bili polazište, a to šta se njima razvija posledica. Odluka je
da bude obrnuto: **veština je ono što se meri i prati, a modul je samo način da
se do nje dođe.**

### Osam veština

Prve dve su znanje koje mora da postane automatizam; ostale su rad u radnoj
memoriji.

| # | veština | šta znači „imam je" | gde se danas radi |
|---|---|---|---|
| 1 | koordinatna automatika | „e4" ne računaš — znaš boju, susede, dijagonale | Geometrija |
| 2 | geometrija figure | sa datog polja odmah vidiš kuda figura ide | Putanja skakača |
| 3 | držanje pozicije | držiš veze figura–polje stabilno, bez curenja | Zapamti poziciju, Diktat |
| 4 | **ažuriranje pozicije** | primeniš potez a da sliku ne pokvariš | Prati partiju, Parovi, Završnica |
| 5 | prevod zapis ↔ slika | čuješ „g1 f3" i vidiš; i obrnuto | Diktat, Prati partiju |
| 6 | oporavak slike | primetiš da se raspala i sastaviš je ponovo | **nigde** — samo se meri |
| 7 | kontrola polja | znaš šta protivnik drži, i koje je polje vruće | **nigde** |
| 8 | računanje naslepo | vodiš varijantu bez table | Završnica, uzgred |

**Četvrta je usko grlo, ne treća.** Statična pozicija se pamti relativno lako;
greška se gomila pri ažuriranju, jer svaki potez nosi priliku da nešto ispadne, a
greške se ne poništavaju nego slažu.

**Šesta i sedma su prazne**, i to je najveći nalaz ove analize. U pravoj partiji
naslepo se figure ne gube zato što se zaboravi gde stoje, nego zato što se
zaboravi **šta drže**.

### Veština pripada zadatku, ne modulu

Prvi pokušaj je veštine kačio na modul. To je pogrešan nivo, i obara ga jedno
pitanje: u „Prati partiju" se sme pitati i „gde je beli skakač" i „koje crne
figure napadaju skakača na e5". Isti modul, isti ulaz, ista podrška — a prvo je
ažuriranje, drugo je kontrola polja na leđima držanja.

Zadatak određuje **pet nezavisnih činilaca**:

| činilac | šta određuje |
|---|---|
| **ulaz** | kako pozicija ulazi u glavu — vidi se, čuje se, sklapa se iz poteza |
| **pitanje** | šta se traži — boja polja, gde je figura, ko napada, put, rekonstrukcija |
| **izlaz** | čime se odgovara — dodir, glas, paleta |
| **podrška** | koliko slike aplikacija drži umesto tebe |
| **povratna informacija** | šta sledi posle odgovora |

Modul je **svežanj** ovih pet, i zato je porodica vežbi a ne jedna vežba.

> **Pitanje kaže šta se meri. Podrška kaže šta se uz to nosi.**

Pitanje o napadu na e5 **sa vidljivom tablom** meri samo čitanje linija; **bez
table** meri čitanje linija i držanje pozicije zajedno.

### Podrška je lestvica, ne prekidač

Danas težina znači koliko zadataka i koliko vremena — to skalira napor, ne
veštinu. Prava lestvica je koliko slike aplikacija drži umesto tebe:

```
cela tabla vidljiva  →  vidi se samo figura koja se pomera  →  vide se samo polja  →  ništa
   najlakše                                                                        najteže
```

„Bez ekrana" je do sada bio **skok sa prve prečke na poslednju** — otud i osećaj
da neki modul bez ekrana ne može. Kao prečka unutar zadatka, prestaje da bude
prekidač u Podešavanjima kome neki modul „ne radi".

Sitnica sa velikom posledicom, iz istog reda: **da li osvetljeno polje ostaje ili
se ugasi.** Ako ostaje, tabla postaje trag i pola posla ažuriranja radi ona.

### Test ili vežba — razlika je u povratnoj informaciji

> **Test kaže da li si pogodio. Vežba pokaže istinu — i to u onom kanalu kojim
> veština radi.**

Geometrija je danas **test**: pokaže „e4", ti kažeš boju, i to je sve. Ako bi
posle odgovora prikazala tablu sa poljem, postala bi vežba — ali **druge**
veštine: „nađi e4 na tabli" je preslikavanje, a ne automatizam. Ako se hoće
automatizam koji preživi zatvorene oči, istina se saopštava **govorom**, ne
tablom.

### Provera i vežba traže suprotnu podršku

Ovo je razlog zbog kog povremena provera nije trivijalna:

> **Kad podrška padne, veštine prestaju da budu razdvojive.** Pogrešan odgovor
> bez table ne kaže da li je otkazala kontrola polja ili je pozicija iscurela.

Odatle:

- **provera se radi uz visoku podršku** — svaka veština odgovara za sebe, profil
  je čitljiv;
- **vežba se radi uz nisku podršku** — veština se gradi tek pod teretom.

Provera mora da bude kratka, uvek ista i **bez poena** — čim nosi poene, prestaje
da meri i počne da se juri.

### Šta ovo traži od merenja

`SessionResult` danas zna modul, težinu i skorove — to je „koliko si dobro
prošao", ne „koja ti veština klizi". Moduli usput računaju prave pokazatelje i
bacaju ih na vratima:

| pokazatelj | gde postoji | šta meri |
|---|---|---|
| broj čitanja | Diktat | stabilnost slike (6) |
| „Čitaj poziciju" | Završnica | učestalost oporavka (6) |
| poništavanje poteza | Završnica | tačnost ažuriranja (4) |
| vreme po pitanju | Geometrija | automatizam (1) |
| **dubina do prve greške** | Prati partiju — **ne računa se** | najdijagnostičniji broj u naslepo (4) |

Najjeftiniji pošten oblik: sesija ostaje jedan red u bazi, ali nosi **razlaganje
po veštinama** — pokušano i pogođeno za svaku veštinu koju je dodirnula. Tek tada
„savladanost" ima smisla, i to **po veštini, a ne po modulu**.

### Put se pravi, ne crta

Dogovoreno **18. avgusta 2026**, uz odeljak o veštinama. Korisnika **vodimo** — ne
puštamo ga da luta po modulima, ali ga i ne stavljamo na šine.

Obe krajnosti otkazuju, iz suprotnih razloga:

- **Lutanje otkazuje tiho.** Čovek bira ono što mu ide, pa profil ostaje krivudav.
  Uz to je sedam modula puta tri težine puta četiri nivoa podrške osamdesetak
  izbora — a pred tolikim izborom se radi ono što se radilo i prošli put.
- **Šina otkazuje glasno.** Trening je dobrovoljan; prvi dan kad se ne radi to što
  piše u rasporedu je i poslednji dan.

Zato: **predlog, uvek sa razlogom, a meni ostaje netaknut ispod njega.**

```
Sledeće: kontrola polja — 3 minuta
Dubina do prve greške ti je pala sa 14 na 6 poteza.
```

Presudan je **drugi red**. Preporuka bez razloga je proročanstvo, a proročanstvu
se ne veruje kad promaši. Preporuka sa brojem je argument, i sme da se odbije.

#### Cilj traje, korak se prilagođava

Put je dinamičan, ali ne prekraja se svaki dan — inače to nije put nego kolebanje,
i korisnik prestane da mu veruje.

| izvor | određuje | koliko često |
|---|---|---|
| **provera** (visoka podrška, bez poena) | **cilj** — na kojoj veštini se radi | na svakih N sesija |
| **sesija** (razlaganje po veštinama) | **korak** — koliko podrške za sledeći zadatak | posle svake |

#### Brojčanik je podrška

Adaptivni deo nije izbor modula nego **prečka na lestvici podrške**, po paru
(veština, vrsta pitanja) — modul je samo ono što taj par ume da izgeneriše:

- **dva puta uspešno na istom nivou → jedna prečka dole** (manje podrške);
- **promašaj → prečka nazad**, bez komentara i bez kazne.

#### Savladano nije završeno

**Naslepo vene brže nego što se stiče.** Posle savladanosti veština ide u
**održavanje** — povremeno jedan zadatak na najnižoj podršci, da se vidi da još
stoji; ako padne, vraća se u cilj.

Bez toga bi put uvek išao ka najslabijem i tiho puštao da najjače propada, a to
bi se otkrilo tek na pravoj partiji.

#### Šta korisnik vidi od puta

**Cilj i sledeći korak — ništa dalje.** Plan sa dvadeset koraka bio bi i laž i
teret: laž jer se dalji koraci zaista ne znaju dok se ovaj ne odradi, teret jer
izgleda kao dug.

#### Četiri pravila bez kojih preporuka postaje smetnja

- **nikad dvaput isto zaredom**, i kad je ista veština i dalje najslabija — zastoj
  se ne probija ponavljanjem, a ostale veštine u međuvremenu venu;
- **povremeno ono što ide dobro** — preporuka koja uvek šalje na najgore je
  preporuka koja se prestane otvarati;
- **prva stvar u aplikaciji je kratka provera**, ne uvodni tekst; ona daje prvi
  profil, mora da **staje rano** i da ostane na visokoj podršci, jer joj je posao
  da nađe polaznu tačku a ne da izmeri dno;
- **odbijanje bez posledice** — nema „preskočio si" ni niza koji puca.

#### Time se zatvara staro pitanje o rangu

Da li rang išta otključava — **ne, i ne treba.** Zaključavanje i preporuka rade
isti posao, ali zaključavanje ga plaća oduzimanjem. Pogrešna preporuka se
ignoriše; pogrešno zaključavanje ostavi čoveka pred vratima.

#### Dve granice, da se ne pogreši redosled

**Merenje je gornja granica svega ovoga.** Dok `SessionResult` ne nosi razlaganje
po veštinama, „najslabija veština" se ne zna i svaka dinamika je nagađanje sa
brojem u ruci. Redosled je zato: veštine u ugovoru → razlaganje u rezultatu →
provera → put.

**Dve veštine se zasad ne daju izolovati** — oporavak slike (6) i računanje
naslepo (8). Njih ne puštati u automatski put dok se ne smisli kako se mere;
bolje ih ponuditi kao svestan izbor nego ih lažno rangirati.

### Profil se pokazuje, ne samo koristi

Vežbanje po svojoj volji ostaje — meni je uvek tu i ništa se ne zaključava. Ali
korisnik mora da **zna gde stoji**: šta mu je jako, šta slabo, i da li se to
pomera. Bez toga slobodan izbor nije sloboda nego pogađanje.

Time profil prestaje da bude samo ulaz za preporuku i postaje **ono što se
isporučuje**, a to je stroži zahtev: mora da bude pošten i kad je podatak mršav.

#### Nivo je prečka, ne procenat

Veština se ne prikazuje kao „73%". Takav broj izgleda tačno a nije — sastavljen
je od nejednakih zadataka i menja se od jedne loše večeri.

Nivo je **prečka na lestvici podrške koju veština drži**, uz smer kretanja:

```
Ažuriranje pozicije     nivo 2 od 4  ▲   slika drži do 6. poteza (bilo 4)
Držanje pozicije        nivo 3 od 4  ▬   7 figura bez greške
Kontrola polja          nije mereno
```

**„Nije mereno" je pun i pošten odgovor**, i mora da se pojavi umesto nule.
Nula bi rekla „loš si u tome", a istina je „o tome još ništa ne znamo" — to je
ista ona razlika koju je ovaj projekat već triput platio kroz nemi otkaz.

Isto važi i za **ustajao podatak**: ako veština nije proveravana tri nedelje, uz
nju stoji koliko je stara, a ne vrednost kao da je jutrošnja. Naslepo vene, pa
stara mera nije mera.

#### Brojevi u jeziku same vežbe

Uz svaku veštinu ide **jedan konkretan broj iz njenog sveta**, ne apstraktna
ocena — takav broj korisnik razume bez objašnjenja i sam vidi kad se pomeri:

| veština | broj koji je opisuje |
|---|---|
| ažuriranje | u kom potezu se slika prvi put raspala |
| držanje | koliko figura drži bez greške |
| oporavak | koliko čitanja po sesiji |
| automatika | prosečno vreme do odgovora |

#### Zid crvenog je otkaz

Profil u kom šest od osam veština stoji kao slabost je tačan i **beskoristan** —
takav ekran se otvori jednom.

Zato: istaknuta je **jedna slabost — ona koja je trenutni cilj** — a ostalo stoji
mirno, bez bojenja u neuspeh. I gde god ima podataka, pokazuje se **pomeraj**, a
ne samo stanje: napredak je ono što drži čoveka, a on se vidi samo u poređenju sa
prošlim merenjem.

#### Tri mesta na kojima se profil vidi

- **Ekran napretka** — cela slika, po veštinama a ne po modulima.
- **Kartica modula** — šta ovaj modul razvija, da se vidi da nije tek tako.
- **Sažetak sesije** — jedan red: **šta je ova sesija pomerila.** To je najvažnije
  od tri, jer stiže u trenutku kad je zaslužen, i bez otvaranja ijednog ekrana.

### Prvi presek: Geometrija zna svoju veštinu

Napisano **18. avgusta 2026**. Prvi komad ovog dogovora koji zaista postoji u
kodu — jedan modul preveden do kraja, da se vidi drži li podela vodu pre nego što
se povuče kroz ostalih šest.

**Šta je uvedeno** (`:core:model`):

- `Skill` — osam veština, sa ključem koji se ne sme menjati, kao kod `ModuleId`.
- `Support` — četiri prečke (`FULL`, `PARTIAL`, `TRACE`, `NONE`) sa `harder()` i
  `easier()`; **redosled je zajednički, značenje je na zadatku.**
- `TaskSpec` — vrsta zadatka: šta pita, koje veštine razvija, koje prečke ume.
  `measures` je prva veština u spisku — ona po kojoj zadatak ide u profil.
- `SkillTally` i `SessionResult.bySkill` — razlaganje po veštinama, uz prazno
  koje **znači „nije mereno", ne nulu**.

**Ugovor modula** je dobio `tasks` i izvedeno `skills`. Prazno `tasks` znači
„modul se još nije izjasnio" — takav modul radi kao i pre, ali ne ulazi ni u
profil ni u put. Šest ih je danas takvo.

`ModuleArgs` je dobio `taskId` i `support` — **porudžbinu** puta. Kad ih nema,
modul bira sam, po težini i po podrazumevanoj prečki iz podešavanja.

#### Geometrija je iz testa postala vežba

Zadatak `square_color` meri koordinatnu automatiku i ume **dve prečke**, namerno
bez one između:

| prečka | šta se dešava posle odgovora |
|---|---|
| `FULL` | **pokaže se tabla sa poljem** — gradi vezu koordinate i mesta |
| `NONE` | table nema, istina se **izgovori** |

Ovo je razlika test/vežba u malom: test kaže da li si pogodio, vežba pokaže
istinu — i to **posle svakog odgovora, ne samo posle greške**, jer se veza gradi
i kad se pogodi.

Time je i stara nedoumica rešena bez biranja strane: „pokaži tablu" i „izgovori"
nisu suprotni predlozi nego **dve prečke iste lestvice**. Prva je ulaz za
početnika, druga je veština koja preživi zatvorene oči.

Prekidač „bez ekrana" u ovom modulu više ne bira ekran nego **polaznu prečku**:
ko vežba zatvorenih očiju kreće od najniže koju zadatak ume.

#### Istorija se čuva

Baza je otišla na verziju 2 zbog kolone sa razlaganjem. Migracija samo dodaje
kolonu — `fallbackToDestructiveMigration` se **ne** koristi, jer je napredak
jedino što korisnik u ovoj aplikaciji ima.

Razlaganje se čuva kao tekst (`coordinates:10/8;position_hold:5/4`), iz istog
razloga iz kog su ključ modula i ime težine tekst: nova veština ne sme da pomeri
značenje već upisanih redova. Nepoznata veština ili oštećen unos **otpadaju**, a
ostatak reda preživi — ista logika po kojoj nepoznat modul ne obara ceo napredak.

#### Svih sedam se izjasnilo

Isti dan, posle Geometrije. Svaki modul sad prijavljuje svoj zadatak, veštine
koje razvija i prečke koje ume, a rezultat sesije nosi razlaganje po veštini
koju taj zadatak **meri**.

| modul | zadatak | meri | uz to nosi | prečke |
|---|---|---|---|---|
| Geometrija | `square_color` | koordinatna automatika | — | FULL, NONE |
| Putanja skakača | `shortest_path` | geometrija figure | računanje | FULL, NONE |
| Interaktivni parovi | `meeting_square` | ažuriranje | držanje | FULL, NONE |
| Prati partiju | `where_is_piece` | ažuriranje | prevod zapisa | FULL, NONE |
| Dokrajči protivnika | `play_out` | ažuriranje | držanje, računanje, oporavak | FULL, NONE |
| Zapamti poziciju | `reconstruct` | držanje | — | FULL |
| Postavi po diktatu | `place_position` | prevod zapisa | držanje | FULL |

Dva nalaza koja se vide tek kad se poređa:

- **Ažuriranje meri tri zadatka, a kontrolu polja i oporavak nijedan.** Prazne
  vrste iz tabele veština nisu se popunile same od sebe.
- **Nijedan zadatak ne ume srednje prečke** (`PARTIAL`, `TRACE`). Lestvica
  postoji u kodu, ali su joj zasad zauzeti samo krajevi — a to je baš razlog
  zbog kog je „bez ekrana" delovao kao skok.

`supportsEyesFree` je **prestao da bude tvrdnja i postao izvod**: modul radi bez
ekrana ako ijedan njegov zadatak ume `Support.NONE`. Dva izvora istine su time
postala jedan. Modul koji se ne izjasni daje `false` — bolje da meni kaže „ne
radi" nego da korisnik to otkrije pred tablom u koju ne gleda.

#### Profil postoji, prikaza još nema

`ProgressSnapshot` sabira `bySkill` kroz celu istoriju i ume da kaže
`weakestSkill`. Dva pravila su ugrađena i pokrivena testovima:

- **sesija bez razlaganja ne razblažuje profil** — stare sesije ga ne pomeraju ni
  na gore ni na dole;
- **nemereno nije slabost** — `weakestSkill` ne vraća veštinu o kojoj nema
  podatka, jer „ne zna se da je slaba" i „zna se da je slaba" nisu ista stvar.

#### Profil se sad i vidi

Tri mesta, istog dana:

- **Sažetak sesije** — red „Pomereno", sa učinkom po veštini koju je sesija
  dodirnula.
- **Kartica modula** — „Razvija: …", unija veština njegovih zadataka. Modul više
  ne izgleda kao vežba sama sebi svrha.
- **Ekran napretka** — otvara se dodirom na karticu ranga u meniju. Ide **po
  veštinama, ne po modulima**, i drži tri pravila iz dogovora: „nije mereno"
  stoji umesto nule, ističe se **jedna** slabost, i uz svaku veštinu stoji
  rečenica šta znači.

Traka učinka se crta samo tamo gde ima podatka — prazna traka bi izgledala kao
nula, a nula i „nije mereno" nisu ista stvar.

#### Profil se ne izgovara

Prvo je i sažetak bez ekrana čitao razlaganje, i to je bilo stavljeno **ispred**
ekrana napretka kao najvrednije mesto — sa obrazloženjem da bez ekrana drugog
načina nema.

Sa uređaja: **„ne znači mi ništa da ga čujem."**

Razlog se vidi kad se pročita naglas ono što je zaista stizalo: „ažuriranje
pozicije 6 od 8" je ime pojma i dva broja, i to usred rečenice u kojoj se sluša
ishod. Ime veštine je oznaka za čitanje — u govoru nema za šta da se zakači.

> **Govor nosi ishod, ekran nosi analizu.**

Izbačeno je i `skillName` iz `SpeechPhrases`, sa njim. Pravilo o dva izvora
istine za imena je time ostalo neprekršeno — a bilo je prekršeno baš zbog reda
koji nije radio.

#### Prečka je deo podatka, ne ukras

Prvo je profil beležio samo pokušano i rešeno. Pitanje sa strane — *da li režim
bez ekrana menja veštinu koja se razvija?* — pokazalo je rupu:

> Deset tačnih uz tablu i deset tačnih bez nje upisivali su se **istom težinom**,
> a to nisu isti dokazi.

Posledica je bila ozbiljna: profil se mogao naduvati vežbanjem na najlakšoj
prečki. Ko uvek vežba uz punu podršku dobio bi visok procenat i preporuku da ide
dalje — a veština koju taj procenat opisuje nije stečena.

Uz to, sami smo bili napisali da je **nivo prečka a ne procenat**, pa upisali
procenat.

Zato sesija sad nosi i prečku (`SessionResult.support`, kolona u bazi, verzija
3), a profil se razlaže po paru **(veština, prečka)**:

- `SkillProfile.heldRung()` — najteža prečka na kojoj ima **dovoljno pokušaja i
  dovoljno tačno**; jedan srećan pogodak bez table nije dokaz.
- `SkillProfile.standing` — jedan broj za poređenje veština u kom **prečka vredi
  više od procenta**. Bez toga bi onaj ko sve radi uz punu podršku izgledao jači
  od onoga ko se muči bez table.
- Sesija **bez upisane prečke ne ulazi u profil** — kao i sesija bez razlaganja.
  Bolje ne znati nego znati pogrešno.

Ekran napretka zato više ne pokazuje procenat nego **„drži: bez table"**, uz po
jedan red za svaku prečku na kojoj je veština probana.

Ovo je ujedno i ulaz koji petlji puta treba: „dva puta uspešno na ovoj prečki →
prečka dole" se ne može izračunati bez ovog podatka.

Ostaje ono što traži još merenja: provera, put i podsetnici.

### Besmislene pozicije se ne pamte

Nalaz sa strane, **18. avgusta 2026**, iz intervjua sa Žužom Polgar: pokazane su
joj dve pozicije, jedna iz odigrane partije i jedna sa nasumično poređanim
figurama. Prvu je rekonstruisala, drugu nije — jer druga **nema odnose među
figurama** koje bi se imale za šta zakačiti.

`randomSparsePosition` u `:core:chess` radi upravo to drugo: baca nasumične
figure na nasumična polja, uz jedino pravilo da pešak ne stane na prvi ili osmi
red. Na tome stoje **dva modula** — „Zapamti poziciju" i „Postavi po diktatu".

Rečeno kroz naš model veština: ta dva ne mere `POSITION_HOLD` u šahovskom smislu
nego **sirovo vizuelno pamćenje**. Na takvim pozicijama su velemajstor i početnik
izjednačeni, pa modul ne može da pokaže napredak u veštini zbog koje postoji.

**Očigledno rešenje je izmereno i ne radi.** Prave pozicije iz `games.pgn`:

| figura | koliko takvih pozicija |
|---|---|
| 3–8 | **nijedna** |
| 9 | 11 |
| 10–12 | 91 |

Od 4551 pozicije u korpusu nema nijedne dovoljno retke — majstori odustanu mnogo
pre golog kraja. Ovaj sadržaj ne može da nahrani ta dva modula.

**Predlog za kasnije: isečak prave pozicije, ne cela.** Iz stvarne pozicije se
zadrži **povezan skup** figura — kreće se od jedne i dodaju se najbliži susedi
dok se ne skupi koliko treba. Pešački lanac, kralj iza zaklona, top iza
slobodnjaka — ti odnosi ostaju, a nasumično bacanje ih ne može stvoriti. Sadržaj
je već tu, ništa se ne piše. Rezerva je generisanje po pravilima uverljivosti.

**Očekivana posledica:** kad pozicije postanu smislene, 3/5/7 figura postaće
prelako — to je i poenta eksperimenta. Težinu će trebati podići, ali tek posle
prve sesije na uređaju.

### Okrenuta tabla otkriva način pamćenja

Ideja: pozicija se pokaže sa **bele** strane, a rekonstruiše sa **crne**.

Ono što se pri okretanju zaista menja je manje nego što izgleda: **koordinate se
ne okreću.** e4 je e4 sa obe strane; okreće se samo slika. Odatle:

> Ko poziciju drži kao **koordinate ili odnose**, tome je zadatak trivijalan. Ko
> je zapamtio **sliku**, tome se raspala.

Zato ovo nije nova veština nego **zadatak koji otkriva kojim si načinom
zapamtio** — i usput kažnjava lošiji način. Ono što velemajstor ima, poziciju kao
skup odnosa, nije veština pored držanja pozicije nego **mehanizam kojim je
držanje jako**; da mehanizme upisujemo u spisak veština, profil bi dva puta
brojao istu stvar.

**Pravi dobitak je poređenje.** Odnos uspeha na okrenutoj i neokrenutoj tabli je
dijagnoza koju nijedan naš zadatak zasad ne ume: 9/10 obično a 3/10 okrenuto ne
znači „slab si" nego **„pamtiš sliku umesto odnosa"**, a to je savet vredniji od
procenta.

Tri praktične stvari:

- **prikaz ne košta ništa** — `ChessBoard` već prima `orientation: Color`;
- **ovo je vrsta zadatka, ne nova osa i verovatno ne nov modul** — drugi
  `TaskSpec` u „Zapamti poziciju", sa svojim uputstvom; ako vremenom zatraži
  sopstveni identitet, postaće modul;
- **ne penje se niz lestvicu podrške** — bez table nema šta da se okrene, jer je
  u govoru „e4" isto sa obe strane. Ovaj zadatak živi pri vrhu lestvice i to je
  u redu.

### Redosled veština i praćenje kroz vreme

Urađeno **18. avgusta 2026**, iz primedbe da se neke veštine moraju razviti pre
drugih i da treba videti „nekad ovako, sad ovako".

#### Preduslov nije „tačno" nego „automatski"

Razlog nije pedagoški nego mehanički: **radna memorija je jedna.** Ako traženje
polja e4 troši pažnju, nema se čime držati pozicija — pa držanje ne napreduje ma
koliko se vežbalo.

```
KOORDINATE ──┬──> DRŽANJE ──┬──> AŽURIRANJE ──┐
             │              │                 ├──> RAČUNANJE
             └──> ZAPIS     └──> OPORAVAK     │
GEOMETRIJA ──┬──> AŽURIRANJE                  │
             └──> KONTROLA POLJA ─────────────┘
```

Zbog toga je moralo da se meri i **vreme**: `SkillTally` sad nosi i milisekunde,
jer se bez njih ne razlikuje *znam* od *znam automatski*. Automatska je veština
koja **drži prečku i na njoj je brza**; pragovi po veštini su prvi predlog, kao i
brojevi bodovanja.

**Preduslov ništa ne zaključava.** Ulazi u preporuku i u jednu rečenicu na
kartici veštine — pogrešna procena se tako ignoriše, dok bi zaključavanje
ostavilo čoveka pred vratima. Isto pravilo po kom rang ništa ne otključava.

#### Čuva se sirovo, izvodi se sve ostalo

Snimak je do sada sabijao istoriju u jedan broj i zato nije umeo da kaže kako je
bilo ranije. Sad nosi **spisak zapisa** `(kad, veština, prečka, učinak, vreme)`,
a profil i trend se iz njega računaju — isto pravilo po kom se poeni ne pamte
nego računaju.

Sesija ulazi u profil samo ako ima **sve troje**: razlaganje, prečku i vreme
završetka. Svako od to troje je deo podatka, ne ukras.

#### Prozor je po broju pokušaja, ne po danima

„Poslednja tri dana" je prazno kod onoga ko vežba dvaput nedeljno — a baš njemu
trend najviše treba. Zato se poredi **poslednjih dvadeset pokušaja sa prethodnim
dvadeset**; datum stoji uz to kao podatak, ne kao mera.

Na kartici veštine to izgleda ovako:

```
Držanje pozicije            drži: bez table
pre:  6/10  ·  3,4 s po zadatku
sad:  9/10  ·  1,9 s po zadatku
```

**Vreme je tu važnije od procenta**: procenat ume da bude dobar odavno, a vežba i
dalje spora — i to je tačno stanje „znam, ali nije automatski".

#### Šta ovo još ne ume

Beleži se samo veština koju zadatak **meri**, ne i one koje nosi uz nju. Zato
veština koja se uvek vežba uz punu podršku — kao držanje, koje mere samo Zapamti
poziciju i Diktat — ne može da pokaže da je automatska bez pomoći. To nije greška
u meri nego posledica toga koji zadaci postoje; popraviće se kad zadaci pokriju
prazne vrste.

### Preko modula se ne sabira

Pitanje sa strane: *ako se za jednu veštinu podaci skupljaju iz raznih modula,
kako to strpati u jedan broj — i treba li uopšte?* Odgovor je **ne treba**, a
aplikacija je to dotad radila.

Tri razloga, i svaki je dovoljan:

- **„Jedan pokušaj" nije ista stvar.** Pitanje u Geometriji traje dve sekunde,
  pozicija u Završnici tri minuta; oba se broje kao jedan. Prosečno vreme po
  pokušaju — uvedeno baš da razlikuje *znam* od *znam automatski* — time postaje
  besmisleno.
- **Tačnost nije na istoj skali.** Ko pređe sa lakog na teži modul, **broj mu
  padne iako je napredovao**. To je najgora vrsta merila: kažnjava ono što treba
  da nagradi.
- **Isti naziv, različita dubina.** Ažuriranje u Parovima je nekoliko poteza, u
  Prati partiju desetine, u Završnici uz protivnika — tri stepena iste veštine, a
  zbir ih sakrije.

Zato sesija nosi i `taskId` (kolona u bazi, verzija 4), a profil se razlaže na
**`SkillProfile` → `TaskProfile` → prečka**. Zbira preko zadataka nema; jedini
broj koji se sme sabrati je **obim** — koliko je ukupno vežbano.

Trend se takođe gleda **unutar istog zadatka**, inače bi prelazak na drugi modul
izgledao kao nazadovanje.

#### Odatle sledi čemu provera zaista služi

Podela koja je dotad bila mutna:

| | odakle | čemu služi |
|---|---|---|
| **nivo** | provera — kratka, uvek ista, uz visoku podršku | poređivo, jer je svima isti zadatak |
| **napredak** | sesije, unutar istog zadatka | pokazuje kretanje, bez poređenja |

Provera nije ukras nego **jedini pošten izvor nivoa**. Dok je nema, „najslabija
veština" je **procena**, i na ekranu tako i piše.

### Orijentir i kriva kroz vreme

**Orijentir** je rezultat kom se teži — ne „maksimum": nije gornja granica skale
i sme da se pređe. Stoji na `TaskSpec`, **po prečki**, jer modul zna kako izgleda
vladanje njegovim zadatkom.

**Par, a ne broj.** Da stoji samo vreme, merilo bi pozivalo na žurbu, a žurba
obara tačnost — koja je pola veštine. Priznaje se tek kad su ispunjena oba, i uz
dovoljno pokušaja.

Jedno priznanje uz brojeve: **vreme je ceo krug zadatka**, ne čisto razmišljanje
— u njemu su i izgovor i pauza posle odgovora. Zato je orijentir izdašniji nego
što bi se očekivalo, a na težoj prečki i veći, jer se bez ekrana ista stvar mora
i izgovoriti. Brojevi su prvi predlog, kao i pragovi rangova.

#### Kriva

Jedan grafik **po zadatku i prečki** — linija koja meša prečke ponovila bi grešku
zbog koje se prečka uopšte i upisuje.

- **Vodoravno: redni broj sesije, ne datum.** Ko vežba dvaput nedeljno dobio bi
  grafik od samih praznina.
- **Uspravno: vreme, ne procenat.** Tačnost se zasiti brzo i linija umre; vreme
  pada mnogo duže i pokazuje napredak i kad procenat miruje. Tačnost nosi **sama
  tačka** — puna kad je sesija stigla do tražene, šuplja kad nije. Jedan grafik,
  oba podatka.
- **Dve vodoravne linije: tvoj najbolji i orijentir.** Orijentir sam, pet puta
  ispod početnikove krive, nije cilj nego **zid** — takav grafik se otvori
  jednom. „Tvoj najbolji" je dostižan i pomera se sa tobom, pa je rastojanje
  između njih priča o napretku umesto podsetnika koliko fali.
- **Ispod tri sesije nema grafika**, samo brojevi: kriva kroz dve tačke ume da
  slaže u oba pravca.

Crta se `Canvas`-om, bez biblioteke — pedesetak linija u aplikaciji koja pazi na
veličinu APK-a.

#### Prelazak orijentira je signal

Kad se orijentir pređe, prestaje da bude horizont i **postaje pod**: preuzima ga
orijentir sledeće prečke. To je isti signal koji petlji puta treba — „ovo je
savladano, spusti podršku" — pa linija nije ukras nego **vidljivi oblik pravila
koje već postoji**.

### Svih sedam prima porudžbinu

Do sada je samo Geometrija razumela `args.support`; ostalih šest je prijavljivalo
prečke ali ih nije primalo — čitali su prekidač „bez ekrana". Sad svi rade isto:

```
porudžbina puta  →  ako je nema, podešavanje  →  nearestSupport zadatka
```

Time **„Bez ekrana" prestaje da bude režim i postaje polazna prečka**: ko vežba
zatvorenih očiju kreće od najniže koju zadatak ume, a zadatak koji je nema ponudi
svoju najnižu umesto da se izvinjava.

Dve stvari koje su usput ispravljene:

- **Prečka se bira na početku sesije i tu ostaje.** Završnica je dotad pratila
  podešavanje uživo, pa bi promena usred vežbe prebacila režim ispod ruke.
- **Rezultat više ne izvodi prečku iz prekidača** nego prijavljuje onu na kojoj
  je sesija stvarno odrađena.

Bez ovoga provera ne bi imala čime da radi: ona mora da poruči **određen zadatak
na određenoj prečki**, a dotle je porudžbinu razumeo jedan modul od sedam.

### Provera postoji

Urađeno **18. avgusta 2026**. Merenje koje je svima jednako, pa daje **nivo** —
ono što vežbe ne mogu da daju jer se učinak iz raznih zadataka ne sme sabrati.

Četiri svojstva, sva ugrađena:

- **uvek ista** — isti zadatak, težina i prečka, pa se dva merenja porede;
- **uz punu podršku** — kad podrška padne, veštine prestaju da budu razdvojive, a
  dijagnoza traži razdvojivost; teret ide u vežbu;
- **bez poena** — `Xp.forSession` vraća nulu za proveru. Merilo koje nagrađuje
  prestaje da meri i počne da se juri;
- **kratka** — merenje koje se izbegava ne meri ništa.

**Radi se po jednoj veštini, ne za svih osam odjednom.** Osam merenja ne stane u
tri minuta, jedno stane u jedan. Profil se puni u komadima, i to je pošteno.

**Provera postoji za tri veštine** — koordinatnu automatiku, geometriju figure i
držanje pozicije. Za ažuriranje, kontrolu polja i računanje **još ne**: njihovi
zadaci traju predugo za merenje, i tu tek treba smisliti kako. Veština bez
provere stoji na „nije provereno", što je tačno stanje.

#### Modul ne zna da je bio proveravan

Da je sesija bila provera zna **školjka**, ne modul: `AppNavigation` obeleži
rezultat pri prijemu. Modul ne zna ni za poene ni za napredak, pa nema razloga da
zna ni za merenje — a i porudžbina koju dobija je ista kao svaka druga.

Ruta modula je zato dobila neobavezan rep: zadatak, prečku i oznaku provere.

#### Nivo i napredak stoje odvojeno

`bySkill`, trend i grafik računaju **samo vežbe**; provere se čuvaju posebno i iz
njih se čita nivo. Sabrati ih značilo bi razblažiti jedino merenje koje je svima
jednako.

Na kartici veštine to izgleda ovako: ko je proveren vidi **nivo**, ko je samo
vežbao vidi napredak i uz njega „nije provereno" — jer bi inače obim vežbanja
izgledao kao dokaz o nivou.

### Put postoji

Urađeno **18. avgusta 2026**, poslednja karika lanca: veština → zadatak → merenje
→ provera → **put**.

`ProgressSnapshot.recommend(tasks, lastTaskId)` u čistom Kotlinu, pa se cela
odluka testira bez uređaja. Vraća **šta, kojim zadatkom i na kojoj prečki**, uz
**razlog** — a razlog je obavezan deo, ne ukras.

#### Kako bira cilj

1. **Nikad dvaput isto zaredom** — osim ako je zadatak jedini. Zastoj se ne
   probija ponavljanjem, a ostale veštine u međuvremenu venu.
2. **Na svakih pet sesija ono što ide dobro.** Preporuka koja uvek šalje na
   najgore je preporuka koja se prestane otvarati; uspeh je gorivo.
3. **Temelj pre nadgradnje** — veština čiji preduslovi nisu automatski se **ne
   zabranjuje** nego pomera unazad. Ako je sve ostalo pokriveno, doći će na red.
4. **Neprobano pre slabog** — o neprobanom se ne zna ništa, a to je vrednije
   saznanje od još jedne potvrde da je nešto slabo.
5. Inače **najslabije**, po `standing` — gde prečka vredi više od procenta.

#### Kako bira korak

Prečka se pomera po onome što se poslednje dogodilo u **tom** zadatku:

- **dva puta uspešno zaredom na istoj prečki → prečka niže**;
- **promašaj → prečka nazad**, bez kazne i bez komentara;
- inače se ostaje gde se bilo.

Uspehom se smatra orijentir te prečke; gde ga nema, 80% tačnosti.

**Prečka se pomera na sledeću koju zadatak zaista ima**, ne za jedan stepen —
lestvica je zajednička, ali zadatak sme da preskoči prečke. Geometrija ima samo
krajeve, pa je „niže" iz pune podrške odmah bez table. To je uhvatio test, a ne
uređaj.

#### Šta korisnik vidi

Kartica **„Sledeće"** iznad spiska modula: zadatak, prečka i **razlog** u jednom
redu. Ispod nje spisak modula ostaje netaknut — put je predlog, odbijanje nema
posledice, i ništa se ne zaključava.

Time je zatvoreno i ono što je stajalo od prve sesije: **rang ništa ne
otključava**, jer preporuka radi isti posao bez oduzimanja.

### Dubina do prve greške

Broj koji su moduli računali i **bacali na vratima**, a nazvali smo ga
najdijagnostičnijim u naslepo. Sad se čuva (`SessionResult.heldUntil`, kolona u
bazi, verzija 7) i prikazuje uz trend.

Zašto je vredniji od tačnosti:

> Tačnost od 70% ne kaže kako izgleda partija. Neko greši **ravnomerno**, a
> nekome se slika raspadne u šestom potezu pa dalje pogađa nasumično. Prvo se
> popravlja vežbom, drugo je granica onoga što glava trenutno drži.

Pamti se **prva** greška, ne poslednja: posle nje je slika već pokvarena, pa
ostali odgovori ne mere isto. Prikazuje se poslednje i najbolje do sada.

Meri se zasad **samo u Prati partiju**, jer je to jedini zadatak u kom se greška
gomila kroz desetine poteza. Ostali vraćaju `null`, što znači „ne meri se" a ne
nulu.

### Kontrola polja ima svoj zadatak

Prva prazna vrsta u tabeli veština je popunjena: „Prati partiju“ sad ume da pita
i **„ko napada ovu figuru“**, uz postojeće „gde stoji figura“.

**Prvi put jedan modul nosi dva zadatka**, i to je bila cela poenta razdvajanja:
isti ulaz, ista podrška, a mere različite stvari — mesto figure je ažuriranje
slike, napadači su kontrola polja.

#### Odgovor su polja, ne imena figura

Razmatrano je troje: „pešak i lovac“, „pešak sa d6 i lovac sa g7“, ili samo
polja. Izabrana su **polja**:

- **jednoznačno je** — dva topa se po imenu ne razlikuju;
- **unos već postoji** — dodir po tabli i glasovno prepoznavanje polja rade od
  prvog dana, pa nema novih kontrola;
- **tako se pitanje i postavlja u partiji**: „šta gađa e5“ rešava se traženjem
  linija do tog polja, a odgovor je odakle.

#### Broj napadača se kaže unapred

U pitanju piše koliko ih se traži. Bez toga se ne zna kad je odgovor gotov, pa bi
se merilo i pogađanje trenutka umesto same veštine — a težina zadatka je u tome
da se napadači **nađu**, ne da se pogodi koliko ih ima.

Odgovori se skupljaju dok se ne nađu svi; na ekranu piše dokle se stiglo (2/3), a
bez ekrana se svako pogođeno polje izgovori.

#### Jedna sesija — jedan zadatak

Rezultat nosi **jedan** `taskId`, pa mešanje pitanja u istoj sesiji ne bi znalo
šta je mereno. Bez porudžbine modul radi zatečeni zadatak; put i provera traže
izričito — a pošto kontrola polja nikad nije merena, put je odmah i nudi.

`Board.attackersOf` je dodat u `:core:chess`, uz osam testova. Postojeći
`isAttackedBy` odgovara na drugo pitanje: za pravila je dovoljno znati **da li**
je polje napadnuto, za vežbu je potrebno **odakle**.

### Skakač kroz minsko polje

Osmi modul, `:feature:minefield`. Crne figure drže tablu, beli skakač mora do
zadatog polja — **na lakšem zadatku ne sme da uzme nijednu figuru, na težem ni
da stane na polje koje neka od njih drži.**

Prvi modul koji pita **šta protivnik kontroliše**, a ne gde su figure. Time je i
druga prazna vrsta u tabeli veština dobila pravi zadatak — u Prati partiju se
kontrola polja **prepoznaje**, ovde se po njoj **planira**.

**Zaseban modul, a ne težina u „Putanja skakača“**, jer se menja uputstvo: tamo je
„stigni u najmanje poteza“, ovde „stigni živ“. Menja se i veština.

#### Dva zadatka, jedan lakši ulaz u drugi

| zadatak | meri | uz to nosi |
|---|---|---|
| `no_capture` | geometrija figure | držanje pozicije |
| `safe_path` | **kontrola polja** | računanje, držanje |

Prvi je prirodan ulaz: tabla je prorešetana, ali se ništa ne mora znati o tome
šta protivnik drži. Drugi je ono zbog čega modul postoji.

#### Napad se računa statično

Crne figure se ne pomeraju, i **to piše na ekranu**. Drugačije se ne može — svaki
skok bi menjao poziciju i zadatak bi postao partija — ali bez te rečenice bi
izgledalo kao da protivnik spava.

#### Zadatak se proverava pre nego što se ponudi

Za razliku od prazne table, gde je svako polje dostupno iz svakog, ovde put ume i
**da ne postoji**: skakač se ume zatvoriti sopstvenim skokovima. Zato se raspored
postavlja nasumično pa **proverava**, a nerešiv se odbacuje.

Provera je jeftinija od pametnog postavljanja, a i poštenija: zadaci ostaju
raznoliki umesto da svi liče na obrazac po kom su građeni.

#### Odbijen potez kaže zašto

„Tu stoji figura“ i „to polje je napadnuto“ su **dve različite greške**, a iz
druge se uči ono zbog čega modul postoji. Razlog se izgovara i kad se tabla vidi,
jer je on ovde sama pouka a ne potvrda da je dodir primljen.

Napadnuta polja se **ne boje na tabli** — to je baš ono što treba znati napamet.

`Board.attackersOf`, `safeKnightPath` i `randomMinefield` stoje u `:core:chess`,
uz trinaest testova u čistom Kotlinu.

### Šta iz ovoga sledi, po redu

1. `Skill` (osam) i `podrška` ulaze u ugovor zadatka; modul prijavljuje **uniju**
   veština svojih zadataka, izvedeno a ne prepisano.
2. Razlaganje po veštinama u `SessionResult`.
3. Nova pitanja u „Prati partiju" — počev od „ko napada ovu figuru", koje prvo
   dodiruje veštinu 7.
4. **Smislene pozicije** za „Zapamti poziciju" i „Postavi po diktatu" — vidi
   „Besmislene pozicije se ne pamte". Dok toga nema, ta dva modula mere pamćenje
   a ne šah.
5. **Skakač kroz minsko polje**: crne figure na tabli, beli skakač treba do
   ciljnog polja bez uzimanja — ili bez stajanja na napadnuto polje. Prvi modul
   za kontrolu polja; `Attacks.kt` i `KnightPath` već postoje, sadržaj se
   generiše. Napad se računa **statično** (crne figure se ne pomeraju) i to mora
   da piše, da ne deluje kao da protivnik spava.
5. Ekran napretka i savladanost — po veštinama, uz „nije mereno“ kao punu
   vrednost (vidi „Profil se pokazuje, ne samo koristi“).
6. **Provera** — kratka, uvek ista, bez poena; daje profil po veštinama.
7. **Put** — cilj iz provere, korak iz sesije (vidi „Put se pravi, ne crta").
8. Podsetnici — biraju **najslabiju veštinu**, ne najstariji modul; isti račun kao
   preporuka, samo isporučen na drugom mestu.

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

#### Podešavanje je slušalo jedno po jedno polje

Podešavanje je značilo: prepoznaj prvo polje, pa **nastavi** da slušaš drugo.
Korisnik je, sasvim razumno, izgovarao **ceo potez u jednom dahu** — „b four g
four". Vosk to vrati kao jedan izgovor, `parseSpokenInput` sastavi tokene u
`b4g4`, a to nije polje — pa se ćutalo.

`SpokenInput.Move` sada čita četiri znaka kao dva polja, a `deliver` predaje oba
polja **iz istog izgovora**, jedno za drugim, kao da su dva puta dodirnuta.
Modul koji traži samo jedno polje (Parovi, Prati partiju, Skakač) uzme prvo i
prekine — njihov ugovor se nije promenio.

Nastavak slušanja i dalje postoji, za onoga ko zastane između polja.

**Zvalo se „Slušaj ceo potez", sada se zove „Izgovori ceo potez odjednom."**
Sa uređaja: iz starog imena se nije videlo šta korisnik treba da uradi. Ime je
govorilo šta radi aplikacija, a podešavanje menja **korisnikovo** ponašanje —
ono je uputstvo, ne opis. Isto pravilo važi i za ostala glasovna podešavanja.

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

Provereno na uređaju 17. avgusta 2026 — i imenovani potez i obaranje pogrešnog
imena rade: tabla se ne dira, a izgovori se šta na tom polju zaista stoji.

**Zapažanje, nije odluka:** odbijanje i najava poteza se **slušanjem lako
pobrkaju**. „Na ce tri nije top nego dama" i „dama, ce tri, ce dva" počinju
poljem i imenom figure, a razlikuju se po jednoj reči u sredini. Pri proveri je
i onaj ko modul poznaje odbijanje opisao kao da je čuo najavu poteza. Bez ekrana
je govor jedini kanal, pa je razlika između „potez je odbijen" i „potez je
odigran" najvažnija razlika koja postoji. Ako se ovo ponovi u korišćenju, jeftina
ispravka je da odbijanje počne rečju koja se ne može zameniti ni sa čim — „Ne." pa
onda objašnjenje.

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

Model je preuzet i spreman na uređaju, a **prepoznavanje polja je probano i radi**
(17. avgusta 2026) — potvrđeno kroz ceo potez izgovoren u jednom dahu, što
podrazumeva da su oba polja prepoznata. Samo engleski.

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
| Jezik — i za govor i za slušanje | engleski (jedini prevedeni) |
| Slova kao reči („bravo" umesto „b") | isključeno |
| Izgovori ceo potez odjednom (Završnica, jedan pritisak) | isključeno |
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

### Jedan jezik, jedan spisak

Do 18. avgusta 2026 su postojala **dva odvojena izbora jezika** — izgovor
(aplikacija govori tebi) i prepoznavanje (ti govoriš aplikaciji) — i **dva
enuma**, `SpeechLanguage` sa deset jezika i `VoiceLanguage` sa devet. Razlog je
bio tačan: izgovor traži TTS glas na uređaju, prepoznavanje traži preuzet Vosk
paket, a srpski postoji samo za prvo.

Danas je to **jedan izbor i jedan `Language`**, sa devet jezika.

#### Zašto je izbor spojen

Povod je bio nalaz sa uređaja: uz nemački se čulo „pola na engleskom, pola na
nemačkom". Ta mešavina je popravljena odvojeno (vidi „Izgovorene rečenice"), ali
je otvorila veće pitanje — a odgovor na njega nije tehnički:

> Ko vežba zatvorenih očiju, leži i sklapa tablu u glavi, pritiskajući ekran koji
> ne vidi — taj ne sme uz to da pamti da **sluša jedan jezik a govori drugi**.
> Ko to ume, taj je genije, a takvih nema mnogo.

Uz to je stiglo i merilo koje vredi više od svake teorije: **sam autor je
nekoliko puta ostao u nedoumici šta je gde podesio.** Podešavanje koje ni onaj ko
ga je napravio ne drži u glavi nije podešavanje nego zamka.

#### Zašto je srpski izašao

Prvo je ostao u spisku, uz napomenu da glasovnog unosa nema. Onda je pao i taj
kompromis, i to sa punim razlogom: **srpski neće biti ni jezik aplikacije kad
dođe prevod.** Jezik koji ne može da bude izabran nema šta da radi u spisku.

Odatle je došlo i čišćenje koje se samo ponudilo: bez srpskog su dva enuma
postala **isti spisak od devet jezika**, a dva imena za istu stvar se pre ili
kasnije raziđu. `SpeechLanguage` i `VoiceLanguage` su zato jedan `Language`.

Sa njim je nestao i `voiceModel` — izvedeni model prepoznavanja, koji je postojao
samo zbog srpskog — i grana koja je javljala da prepoznavanja za izabrani jezik
nema. Nema više takvog jezika.

#### Šta je ostalo od izbora

**Engleski je jedini jezik sa rečenicama**, pa je jedini koji se nudi; ostalih
osam stoji zatamnjeno sa oznakom **„nije preveden"**, uz „nema glas" za one
kojima uređaj nema TTS. Vidljivo i sa razlogom, ne skriveno.

Prevodi se ne izmišljaju: osam prevoda koje niko od nas ne može da proveri bilo
bi osam tihih grešaka umesto jedne poznate. Isto pravilo po kom reči za polja
nose `isVerified`.

**Dodavanje jezika je time jedan potez sa četiri unosa:** `Language`,
`SpeechLanguages` (reči za govor), `VoiceLanguages` (Vosk paket) i
`SpeechPhrases` (rečenice). Dok rečenica nema, jezik se ne nudi.

Nasleđe se čita iz starog ključa za izgovor (`speech_language`); ko je imao
srpski, dobija engleski, jer srpskog više nema.

`AndroidSpeaker` i dalje proverava `isLanguageAvailable`; ako izabrani jezik
ostane bez glasa, čita se **engleski** — bolje razumljiv engleski nego ćutanje.

**Dve tabele su ostale, ali usaglašene.** `SpeechLanguages` nosi reči za govor,
`VoiceLanguages` za slušanje. Test ih drži zajedno: za svaki jezik i svih 64
polja, ono što aplikacija izgovori mora da prođe kroz njeno sopstveno
prepoznavanje — inače govori polje koje sama ne bi razumela.

Formatiranje je zato u `Speaker`-u (`say(move)`, `say(square)`, `say(board)`):
zavisi od jezika, a moduli za jezik ne znaju niti treba da znaju.

### Izgovorene rečenice prate jezik govora

Prijavljeno sa uređaja 17. avgusta 2026: kad se za izgovor izabere **engleski**,
čuo se engleski glas kako **čita srpske reči**. Polja i imena figura su jezik
odavno pratili; rečenice oko njih su stajale kao literali u modulima.

Urađeno 18. avgusta: `SpeechPhrases` u `:core:audio`. Pisane su na srpskom pa
prevedene na engleski; kad je srpski izašao iz jezika, ostao je **engleski kao
jedini prevod**. Osam jezika ga čeka — vidi „Jedan jezik, jedan spisak“.

#### Dve ose, i ne mešaju se

| šta | prati |
|---|---|
| **govor** — sve što se čuje | jezik izgovora iz Podešavanja |
| **ekran** — sve što se vidi | jezik aplikacije |

Ovo nije ista stvar i ne sme se spojiti: čovek sme da drži aplikaciju na svom
jeziku a polja da sluša na engleskom, jer engleski TTS glas ima svaki telefon a
njegov možda nema.

Gde je ista rečenica išla **i u govor i na ekran** — ishod u Završnici
(`messageFor`) i pitanje u Prati partiju (`question.prompt`) — sad su razdvojeni:
govor uzima iz `SpeechPhrases`, ekran ostaje na svome.

#### Tri odluke u samoj tabeli

- **Sučelje, ne mapa.** Nova rečenica mora da bude **greška u prevođenju** dok je
  svaki jezik ne dobije. Mapa bi je propustila i otkrila tek na uređaju, kao
  tišinu — a nemi otkaz je u ovom projektu već triput skupo koštao.
- **Funkcije, ne obrasci sa `%s`.** Jezici se ne slažu oko brojeva: „u 1 poteza"
  na engleskom mora biti „in 1 move", a u množini „in 3 moves". Funkcija to reši
  u jeziku kom pripada, umesto da svaki modul pravi izuzetak.
- **Ostali jezici dobijaju engleske rečenice**, ne prazne i ne srpske. Isto
  pravilo po kom imena polja nose `isVerified`: bolje poznata zamena nego
  izmišljen prevod koji niko od nas ne može da proveri.

#### Imena figura su usput prestala da budu srpska

Završnica i Prati partiju su imale **svoje spiskove imena figura**, oba na
srpskom, iako ih `SpeechWords` nosi po jeziku. Sad se ime traži iz `SpeechVoice`
— to je ono što modul dobije u ruke kad govori: rečenice i imena zajedno.

Zato ime figure prati **jezik**, a rečenica prati zamenu: ko govori poljski
dobija poljska imena u engleskim rečenicama. Test to i čuva, jer je namerno.

#### Zamka koju je uhvatio prevodilac

`speaker.say { correct }` u Prati partiju nije radilo: u tom bloku već postoji
lokalno `correct` tipa `Boolean`, i ono **zaklanja** rečenicu istog imena iz
prijemnika. Kotlin daje prednost lokalnom imenu, pa je ispravka `this.correct`.

Vredi zapamtiti pri dodavanju novih rečenica: **kratko ime rečenice se sudara sa
kratkim imenom promenljive**, a prevodilac to prijavi samo kad se tipovi razlikuju.

#### Šta je ostalo

**Tekst na ekranu je i dalje srpski u kodu** — `statusMessage` u ViewModel-ima,
poruke ishoda, pitanja. Dok ViewModel proizvodi tekst koji se vidi, prevod
aplikacije nije moguć: ono što treba je da ViewModel javlja **stanje** (ishod,
vrstu odgovora), a da ekran od toga pravi tekst preko resursa. To je zaseban
posao i nije počet.

Iz istog razloga rang i dostignuće se u sažetku izgovaraju **bez imena** — „Novi
rang", ne „Novi rang: Majstor". Imena su danas resursi ekrana; drugi spisak u
`SpeechPhrases` značio bi dva izvora istine za isto ime.

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

**Ideja 2 — čuje se, namešta se na tabli. Urađena je**, kao sedmi modul
„Postavi po diktatu" (`:feature:dictation`) — vidi niže. Ideja 1 je i dalje samo
zabeležena.

### Postavi po diktatu

**Provereno na uređaju 17. avgusta 2026, u konačnom obliku** — sa podelom na
slušanje i slaganje i sa čitanjem koje se posle potvrde broji kao propust. Radi.

Pozicija se **izgovori**, tabla je prazna, a ti je složiš od figura iz palete.
Jedini modul koji ide **od zapisa ka slici u glavi** — ostalih šest idu obrnuto,
od viđene pozicije ka zapisu, pa je baš ovaj smer do sada nedostajao iako je on
ono što blindfold i traži.

**Zaseban modul, a ne još jedna težina u „Zapamti poziciju"**, iako dele tablu,
paletu i ocenjivanje. Razlog nije tehnički:

> Jedan modul — jedno uputstvo. Modul koji ume dve različite stvari mora obe da
> objasni, a korisnik pri ulasku mora da se seti u kojoj je varijanti.

„Zapamti poziciju" je bio hibrid: ulaz se **vidi**, izlaz je **dodir**. Sa dva
čista oblika razdvojena po modulima, svaki ima jednu rečenicu uputstva.

Zadatak ima **dve faze, i tabla postoji samo u drugoj**:

1. **Slušanje** — table nema na ekranu. Dugmad su „ČITAJ PONOVO" i
   „ZNAM GDE JE ŠTA".
2. **Slaganje** — tabla i paleta se pojave, čitanje se prekida.

Prva verzija je tablu prikazivala odmah, i sa uređaja je stigla primedba: ne
treba dozvoliti postavljanje dok čitanje traje. Ispod toga stoji nešto važnije od
zabrane — **sa praznom tablom pred očima vežba se svede na prepisivanje.** Čuješ
figuru, spustiš je, čuješ sledeću, spustiš je, i sliku u glavi nikad ne sastaviš.
A upravo je ona ceo smisao modula.

Zato je granica **potvrda korisnika**, a ne kraj čitanja: „znam gde je šta" je
odluka koja deli vežbu na pola. Pritisak na to dugme prekida čitanje, pa se
čitanje i slaganje nikad ne preklapaju sama od sebe — jedino ako se čitanje
izričito zatraži tokom slaganja, što je onda svesna provera.

**Čitanje je neograničeno**, po ugledu na „Čitaj poziciju" u Završnici — kome ide
teže, taj sme da pita koliko god treba. Broj čitanja **stoji na ekranu**
(„Čitanja: 3"), jer merilo koje se ne vidi ne meri ništa; kad vremenom padne sa
pet na jedno, to je i ceo dokaz da vežba radi.

Ali **ne košta isto u obe faze**:

| faza | čitanje | zašto |
|---|---|---|
| slušanje | slobodno | to je sama vežba |
| slaganje | **broji se kao propust** | rekao si „znam gde je šta", pa se ispostavilo da ne znaš |

Ova druga polovina je primedba sa uređaja, i tačna je: pošto potvrda postoji,
ona nešto i **znači**. Vraćanje na čitanje posle nje nije korišćenje alata nego
priznanje da slika u glavi nije bila gotova — a to je baš ono što modul meri.

Cena zato piše **iznad dugmeta, pre dodira** („Ponovno čitanje sada se broji kao
propust"), a ne da se vidi tek kao uvećan broj grešaka. Iz istog razloga „ČITAJ
PONOVO" u toj fazi više nije glavno dugme.

Težina je broj figura — 3 / 5 / 7. **Sata nema**: pritisak vremena bi merio brzinu
slušanja, a ne to koliko se odjednom drži u glavi.

**Paleta se meša**, kao i u „Zapamti poziciju" — inače bi redosled figura odao
redosled kojim su izgovorene, pa bi se pozicija složila bez slušanja.

Glasovni unos modulu ne treba uopšte, pa ne traži ni Vosk paket od 40 MB.

#### Šta je podeljeno umesto prepisano

Ocenjivanje i pravljenje nasumične pozicije su preseljeni iz `:feature:recall` u
`:core:chess` (`Reconstruction.kt`), pod imenima koja ne pominju nijedan modul:
`ReconstructionGrade`, `gradeReconstruction`, `randomSparsePosition`.

Vežbe se razlikuju po tome **odakle pozicija stiže** — vidi se ili čuje — ali je
posao isti: složiti je, pa uporediti sa zadatom. Dve kopije istog pravila u dva
modula bi se pre ili kasnije razišle. Testovi su otišli sa kodom, pa se sada vrte
u čistom Kotlinu.

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

- Odluka oko potvrde prepoznatog poteza (sada se potez odigra pa objavi;
  alternativa je pitati pre poteza).

### Sažetak sesije bez ekrana

Poslednja rupa u režimu, i sad je zatvorena. Do 17. avgusta 2026 je kraj sesije
otvarao **vizuelni dijalog**: rezultat se izgovarao, ali se dalje išlo dugmetom
koje se ne vidi.

Provera na uređaju je pokazala da je problem manji nego što je pisalo — i to je
promenilo ispravku. **Izlaz je postojao**: dodir van dijaloga i sistemsko dugme
nazad vraćaju u meni, a poeni se upišu **pre** nego što se dijalog pojavi, pa se
izlaskom naslepo ništa nije ni gubilo. Falilo je dvoje: da se **zna** da izlaz
postoji, i „Još jednom", koje se nije moglo dohvatiti nikako. A posle jedne
vežbe se najčešće hoće još jedna — bez toga se režim završavao na kraju prve
sesije.

Sažetak zato ima dva oblika, kao i sve ostalo: dijalog za onoga ko gleda, zone
za onoga ko ne gleda (`SessionSummaryEyesFree`).

```
┌───────────────────────────────┐
│          JOŠ JEDNOM           │   50%
├───────────────────────────────┤
│        REZULTAT  8/10         │   25%
├───────────────────────────────┤
│             MENI              │   25%
└───────────────────────────────┘
```

Isti raspored kao u vežbama, pa se meta pamti rukom: gore ono što se najčešće
hoće, u sredini pomoć, dole izlaz. Pri pojavljivanju se izgovori **šta se sad
može** — „Gore još jednom, u sredini rezultat, dole meni" — jer se za zone
drugačije ne može ni saznati. Čeka svoj red iza modulovog „Kraj sesije".

Dve stvari koje odatle slede:

- **Izlaz ovde ne traži dva dodira**, iako ga traži u vežbi. Dva dodira postoje
  zbog nepovratnog, a ovde je sesija gotova i upisana — potvrda bi bila obred
  bez razloga.
- **Poeni, rang i dostignuća se sada i čuju.** Postojali su samo u dijalogu, pa
  ko vežba zatvorenih očiju za njih nije ni znao. Srednja zona ih izgovara na
  zahtev, i preseca — jer ono što se izričito traži ne treba da čeka.

#### Broj sa tačkom je redni broj

Prva proba na uređaju je odmah donela grešku koju nijedan build ne hvata:
„Rešeno 1 od 4." je izgovoreno kao **„Rešeno jedan od četvrti"**.

U srpskom je broj sa tačkom redni broj, i TTS to pravilo poštuje doslovno. Isto
važi za nemački („4." → „vierte"); engleskom ne smeta, pa se na engleskom ovo ne
bi ni primetilo.

Nije bilo u sažetku nego **u pet modula**: svaki kraj sesije se izgovara
rečenicom koja se završava brojem. Zato je ispravka na jednom mestu, u
`AndroidSpeaker.sayParts` (`withoutOrdinalPeriod`), a ne pet puta u tekstu:
pravilo ne zna nijedan modul a važi za svaki, i sledeća takva rečenica je
pokrivena unapred.

Tačka se ne briše nego **postaje zarez** kad rečenica ide dalje — pauza je bila
i njena namena. Na kraju izgovora se briše. Decimale se ne diraju, jer tačka
između dve cifre nije kraj rečenice. Pokriveno sa pet testova
(`OrdinalPeriodTest`), u čistom Kotlinu.

> Pouka je ista kao kod regularnog izraza koji radi na JVM-u a puca na Androidu:
> **govor ima pravila koja prevodilac ne vidi.** Jedini način da se nađu je
> pustiti ih naglas.

### Jezici prepoznavanja

**Srpskog nema i to je na kraju odlučilo i sudbinu srpskog u celoj aplikaciji.**
Provereno na spisku od 76 Vosk modela: nema nijedan južnoslovenski jezik — ni
srpski, ni hrvatski, bosanski, slovenački ni makedonski. Jedini put bi bio
Android-ov `SpeechRecognizer` iza istog `VoiceInput` interfejsa, uz internet u
toku vežbe i bez uskog rečnika; odloženo dogovorom, a onda i nepotrebno, jer
srpski nije jezik aplikacije (vidi „Jedan jezik, jedan spisak“).

Tekst **na ekranu** je i dalje srpski, ali kao radni jezik razvoja, ne kao
ponuđen jezik. Menja se kad dođe prevod ekranskog teksta — prva stavka na
spisku za nastavak.

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

## Server, nalozi i naplata

Dogovoreno **18. avgusta 2026**. Ništa od ovoga nije početo; ovde stoji zato što
određuje redosled svega daljeg.

### Pravilo koje seče kroz sva tri pitanja

> **Merilo koje nagrađuje prestaje da meri i počne da se juri.**

Zbog toga provera ne nosi poene. **Lestvica je najjača moguća nagrada zakačena za
merilo:** rangiranje po poenima tera na mlaćenje lakog, a rangiranje po nivou iz
provere pretvara proveru u ispit koji se ponavlja dok ne ispadne lepo.

Takmičenje zato **ne sme da se zakači za profil veština**.

### Server — da, za tri stvari

1. **Čuvanje napretka.** Najvažnije, iako najmanje uzbudljivo: ceo profil danas
   živi u jednoj tabeli na jednom telefonu, pa je promena telefona — nula. To je
   jedina stvar u aplikaciji koja se ne može povratiti.
2. **Dnevni zadatak.** Isti zadatak svima, isti dan. Uporedivo bez ijedne zamke,
   jer meri **jedan pokušaj**, a ne veštinu.
3. **Takmičenje po istrajnosti** — nizovi dana, minuti, broj sesija. Ko to
   „lažira", samo je vežbao.

**Ne pravi se lestvica po veštinama.** To je jedini oblik koji direktno napada
instrument.

### Nalog se kači na profil, ne zamenjuje ga

Dva različita problema koja se lako pobrkaju:

| | čemu služi |
|---|---|
| **lokalni profil** | otac i sin na istom telefonu, brzo prebacivanje, bez lozinke |
| **nalog** | čuvanje napretka, dnevni zadatak, takmičenje |

Dete od deset godina najčešće nema Google nalog, a prebacivanje naloga na
telefonu je težak postupak — nikako nešto pred svaku vežbu. Nalozi **umesto**
profila razbili bi baš onaj slučaj zbog kog profili i postoje.

Zato: lokalni profili ostaju, a nalog se **po želji kači** na profil.

### Naplata

**Bez reklama.** Glavni režim je vežba zatvorenih očiju: čovek leži i drži tablu
u glavi. Reklama tu ne prekida udobnost nego **ruši samu vežbu**. Uz to je
publika uska — reklame donesu sitninu, a odnesu ono zbog čega ljudi ostaju.

**Freemium, sa podelom po trošku, ne po lepoti:**

| besplatno | plaćeno |
|---|---|
| svi moduli i vežbanje | čuvanje napretka i prelazak na drugi telefon |
| profil, provera, put | istorija i grafici unazad, dnevni zadatak i lestvica |
| režim bez ekrana | više profila na uređaju |

Iskušenje je naplatiti profil i put jer su najlepši; to je greška, jer bez njih
besplatna verzija nema šta da pokaže. **Alat je besplatan, plaća se ono što košta
nas** — server. To se i objasni u jednoj rečenici.

**Jednokratna kupovina, ne pretplata.** Publika je uska i uporna, server već
postoji zbog druge aplikacije, pa marginalni trošak po korisniku ne traži mesečnu
naplatu. Pretplata za šahovsku vežbalicu odbija više nego što donosi.

### Redosled

1. **Profili — sad.** Jedina stvar koja poskupljuje ako se odloži.
2. **Nekoliko nedelja korišćenja.** Sve od 18. avgusta ima dva dana i nije ga
   probao niko osim autora: orijentiri su prvi predlog, pragovi automatizma
   nagađanje, a put nikog nije vodio kroz mesec dana.
3. **Server** — prvo čuvanje napretka, pa dnevni zadatak, pa lestvica po
   istrajnosti.
4. **Naplata poslednja**, kad postoji nešto što se ne bi dalo ni za pare.

Razlog za ovaj redosled nije opreznost: **lestvica i cena postavljene pre nego
što se aplikacija pokaže rešavaju pogrešan problem.** Nedostatak korisnika se ne
leči takmičenjem među korisnicima kojih nema.

---

## Šta bi aplikacija još mogla da bude

Zapisano **18. avgusta 2026** kao mogućnost, ne kao plan. Ništa od ovoga nije
početo; ovde stoji da se ne bi ponovo smišljalo od nule.

### Profili postoje

Urađeno **18. avgusta 2026**. Povod je bio stvaran: otac i sin koriste istu
aplikaciju, i svako hoće da vidi svoj napredak a ne njihov zbir.

**Ispalo je jeftinije nego što je najavljeno**, i to nije slučajno. Ranije je
ovde pisalo da profili moraju pre svega što se oslanja na napredak, jer se inače
prepravlja sve iznad. Ali profil veština, prečke, orijentiri, provera i put
**ništa ne pamte** — sve se računa iz spiska sesija pri svakom čitanju. Zato je
dovoljno promeniti **koje sesije ulaze u spisak**, a svih pet slojeva iznad se
prilagodi samo.

> To je cena i dobitak pravila „čuva se sirovo, izvodi se sve ostalo". Da je
> negde bio upisan izračunat profil, prepravljao bi se na sedam mesta.

Šta je zaista urađeno:

- tabela `profiles` i kolona `profileId` u sesijama, verzija baze **6**;
- **zatečena istorija se ne briše nego pripisuje prvom profilu** — ona je jedino
  što se u ovoj aplikaciji ne može povratiti;
- upit filtrira po aktivnom profilu, a aktivan profil stoji u DataStore-u jer je
  svojstvo **uređaja**, ne profila: kaže ko sad sedi pred telefonom;
- **podešavanja su postala svojstvo profila** — ključevi nose prefiks. Otac i sin
  se razlikuju baš u jeziku, brzini govora i „bez ekrana". Ono što je upisano pre
  profila čita prvi profil, jer je bilo njegovo;
- ekran za biranje, sa preimenovanjem i brisanjem; brisanje traži dva dodira jer
  briše i celu istoriju tog profila;
- ime profila stoji **u naslovnoj traci** umesto imena aplikacije: ono što se
  menja vredi više od onoga što uvek piše isto.

**Bez lozinke**, kako je i dogovoreno. Nalog će se kačiti na profil kad dođe
server; profil i nalog rešavaju različite probleme.

### Profili — kako je bilo zamišljeno

Povod je stvaran: **otac i sin koriste istu aplikaciju**, i svako hoće da vidi
svoj napredak, a ne njihov zbir. Danas bi im se sesije mešale u jednu istoriju,
pa bi obojica gledala broj koji ne opisuje nijednog od njih.

**Bez lozinke.** Podaci su na uređaju; ko ima uređaj ima i njih, pa lozinka ne
bi štitila nego se pretvarala da štiti. Ono što ovde treba nije zaštita nego
**razdvajanje napretka** — biranje profila pri ulasku, kao na televizoru. Ako se
ikad ukaže potreba da dete ne uđe u tuđi profil, PIN od četiri cifre je iskren
obim; puna prijava nije.

**Ovo dira temelje, i zato ide pre svega što se na napredak oslanja:**

- `SessionEntity` nema nijednu kolonu o korisniku;
- podešavanja su **jedan** DataStore za ceo uređaj — a bez ekrana, brzina govora
  i jezik su baš ono što se razlikuje od oca do sina.

Ako se prvo naprave ekran napretka i podsetnici pa se profil uvede posle,
prepravlja se sve iznad: upit koji sabira istoriju, snimak, pravilo podsetnika i
ekran. Ako profil ide prvi, ostalo se piše jednom.

### Ekran napretka i dostignuća

Napredak **postoji ceo** — poeni, rangovi, deset dostignuća, napredak po modulu,
besprekorni nizovi — ali se vidi samo kao brojač u meniju. Fali prikaz, ne
pravilo.

Uz njega ide i pojam koji **još ne postoji nigde**: šta znači da je modul
**savladan** na nekoj težini. Predlog je tri uzastopne sesije preko 90%
tačnosti, ali je to prvi predlog, kao i brojevi bodovanja. Bez te definicije
nema ni stranice „šta sam savladao" ni pametnog podsetnika, jer oba pitaju isto.

### Podsetnici — mogućnost, ne obaveza

„Nova vežba je spremna" — obaveštenje koje predlaže šta da se vežba.

**Odlučeno o tome kako se traži dozvola:** ništa se ne pita unapred. Podsetnici
su **izbor korisnika**; tek kad ih u Podešavanjima uključi i kaže koliko često,
onda se traži i dozvola za obaveštenja. Pitati pre toga znači tražiti nešto što
korisnik nije ni poželeo — a odbijena dozvola se teško vraća.

Ako se pravi, najjeftinije pravilo koje stvarno ispunjava obećanje: modul koji
se **najduže nije radio**, a među takvima onaj sa **najnižom tačnošću**. Oba
podatka već postoje (`byModule`, `finishedAtMillis`).

Šta se očekuje na uređaju, a ne vidi se u emulatoru: `POST_NOTIFICATIONS` je
dozvola od Androida 13, zakazivanje ide kroz `WorkManager`, a proizvođači —
Honor među prvima — umeju da uspavaju aplikaciju koja nije izuzeta iz štednje
baterije. Isti soj problema kao i dosad: prevede se čisto, ćuti na telefonu.

## Predlog redosleda za nastavak

Svih sedam modula je provereno na uređaju. Provereno je 17. avgusta i ovo:

- diktat u konačnom obliku, sa podelom na slušanje i slaganje;
- **prepoznavanje polja uopšte** — prvi put na uređaju, radi;
- ceo potez izgovoren u jednom dahu („b four g four");
- izgovor poteza sa imenom figure („dama, c tri, c dva");
- **potez preko imena figure** — „rook e two", i pogrešno ime koje obara potez;
- **zone bez ekrana i zaključan portret** — slika sa Završnice pokazuje sva tri
  pojasa u odnosu 50 / 25 / 25, popunjena po visini, i ekran se ne okreće;
- **sažetak sesije bez ekrana** — provereno kako se stari dijalog ponaša
  naslepo, i baš je ta provera odredila kako izgleda ispravka.

**Šta čeka telefon:** dve izmene od 18. avgusta nisu viđene na uređaju —
**sažetak sesije sa zonama** i **rečenice po jeziku govora**. Ovu drugu treba
slušati na oba jezika: srpski da se ništa nije izgubilo, engleski da više ne
čita srpske reči.

Ostalo je:

1. **Prevod ekranskog teksta.** Govor je od 18. avgusta na dva jezika, ekran
   nije: ViewModel-i i dalje proizvode srpski tekst koji se vidi. Dok je tako,
   aplikacija se ne može prevesti. Posao nije prevod nego **razdvajanje**: neka
   ViewModel javlja stanje, a ekran neka od njega pravi tekst preko resursa.
2. **Profili**, ako se prihvate — pre svega što se oslanja na napredak, jer im
   je mesto u bazi a ne u prikazu (vidi „Šta bi aplikacija još mogla da bude")
3. Dogovoriti brojeve bodovanja, šta znači „savladan modul", i da li rang išta
   otključava
4. **Ekran napretka i dostignuća** — podaci postoje, prikaza nema
5. Više vrsta pitanja u Prati partiju — zasad postoji samo „gde stoji figura"
6. Težine u Geometriji (vidi gore) — odloženo dogovorom
7. Ako se proba neki jezik osim engleskog, upisati `isVerified` u
   `VoiceLanguages` odnosno `SpeechLanguages`; imena figura postoje samo na
   engleskom i dopunjuju se istim putem
8. **Zapamti poziciju bez ekrana** — ideja 1 („čuje se, izgovara se"). Traži još
   samo dve reči po jeziku, imena boja; ocenjivanje je već zajedničko i poredi
   skupove, pa redosled ne mora da se pamti
9. **Podsetnici**, ako se prihvate — poslednji, jer se oslanjaju na sve iznad

**Otvoreno pitanje koje se nije zatvorilo:** da li potvrđivati prepoznat potez
pre nego što se odigra. Sada se odigra pa objavi, uz poništavanje — zaključeno
da je to brže od pitanja pred svaki potez, ali odluka nije proverena kroz duže
korišćenje.
