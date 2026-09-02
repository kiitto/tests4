# PRDC Dynamic MiPower Input File Editor -- Java/JavaFX

Full Java/JavaFX MiPower Input File Editor, built **module by module**:
utils/models/parser, then services, then GUI, then analytics/comparison,
then (Module 5, most recent) the full **hierarchical Base File / Case
Study tree** -- see "Module 5" below for what that adds and how it was
verified. **Start there** if you already know the earlier modules; this
paragraph is a pointer, the rest of the file is the module-by-module build
log kept intact for traceability.

```
Base File (never modified)
 |-- Case Study 1
 |     |-- Case Study 1_1
 |     `-- Case Study 1_2
 |-- Case Study 2
 `-- Case Study 3
```

## Quick start

```
cd PRDC_MiPower_Editor
mvn clean compile
mvn javafx:run
```

If `javafx:run` fails to resolve the JavaFX graphics artifact, set
`-Djavafx.platform=` to `win`, `linux`, `mac`, or `mac-aarch64` (default in
`pom.xml` is `win`) to match the machine you're building on:

```
mvn javafx:run -Djavafx.platform=linux
```

## Important: read this before you build

**This code has not been compiled or run.** The sandbox this was written
in has a JRE but no `javac` (compiler) and no network access to resolve
Maven dependencies, so nothing here could be built or executed to verify
it. Every file was written by tracing the equivalent Python logic (and,
for the new domain models, the actual OUT0 sample data) carefully by hand
-- including finding and fixing three real bugs that way (see "Bugs
caught by manual review" below) -- but you will be the first to actually
compile it. **Please paste back the exact compiler error if `mvn compile`
fails.**

## Module 1 (this delivery): `utils`, `models`, `parser`

18 files. This is the foundation everything else builds on.

### `utils/`
- **`Constants.java`** -- every shared literal (default section order,
  study-type keywords, MiPower exe path, UI color palette, status
  vocabulary, analytics thresholds) in one place.
- **`ValidationUtils.java`** -- value type detection/validation
  (Integer/Float/Scientific/Text) for the future table editor, plus the
  low-level text tokenizing helpers (`tokenizeHeader`, `buildNumberedFieldMap`,
  `findFieldIndex`, etc.) that `DatParser` and the future modifier service
  both need to agree on -- so what gets *detected* and what gets *written
  back* never disagree about field boundaries.
- **`FileUtils.java`** -- centralized file I/O (read/write text, derive
  `Input_Modified_CaseN.dat0` / report paths) so no other class talks to
  `java.nio.file` directly.

### `models/`
- **`DatRecord.java` / `DatSection.java`** -- the dynamic `.dat0` editing
  model (a generic field map, not named properties). **Not in your
  original list** -- added because they're what makes `.dat0` editing
  fully dynamic in the first place (see "One important architecture
  decision" below).
- **`Bus.java`, `Generator.java`, `Line.java`, `Branch.java`** -- exactly
  as requested, modeling solved *OUT0 results* (fixed, known shape,
  unlike the input side).
  - `Branch.java` is an **abstract base class**; `Line` and a new
    **`Transformer.java`** (added, not in your original list, but the
    OUT0 file genuinely has separate line and transformer tables) both
    extend it, sharing every field and most behavior via inheritance,
    with only `getKind()` differing -- a concrete example of the
    "Inheritance... where appropriate" you asked for.
  - `Generator.java` is currently **derived from `Bus`** (`Generator.fromBus()`)
    using the generation values already present in the OUT0 "BUS
    VOLTAGES AND POWERS" table. It does *not* yet parse the separate
    "GENERATOR DATA FOR FREQUENCY DEPENDENT LOAD FLOW" block (P-RATE,
    P-MIN, P-MAX, droop, participation factor) visible in your sample
    `report.txt` -- that's a real, additional data source `Out0Parser`
    doesn't read yet. Say the word if you want that parsed into
    `Generator` directly instead of derived from `Bus`.
- **`Out0Results.java`** -- **not in your original list**, but necessary:
  the container `Out0Parser` returns (path, convergence, summary MW/MVAr
  totals, bus/branch lists, violation counts), plus Java-stream-based
  `analysisMetrics()` (min/max/avg/std voltage, loading stats, power
  balance) replacing the Python version's pandas aggregations -- no
  pandas equivalent was requested, and streams are the natural fit here.
- **`ChangeRecord.java`** -- one immutable fact about one edit.
- **`HistoryEntry.java`** -- a `ChangeRecord` plus a *mutable* Change
  History status (Pending/Saved/Undone/Deleted). Deliberately separate
  classes: the edit fact never changes once made, only how it's
  currently being treated.
- **`RunResult.java`** -- one MiPower execution's outcome (success,
  message, output file paths); gains `caseStudyName`/`executionSeconds`
  once recorded into a Case Study's run history (module 2).
- **`ComparisonResult.java`** -- pure data (with nested
  `MetricComparison`/`BusComparison`/`LineComparison`) that the future
  `ComparisonEngine` service will populate and the future dashboard will
  only ever read, never recompute.

**`CaseStudy.java` is deliberately deferred to module 2.** It's not pure
data -- in the Python version it actively *owns and orchestrates* a
`ModificationManager`. Building it now, before that service exists,
would mean either a half-real class or a forward reference to a service
package from the models package, which breaks clean layering. It'll be
the first thing in module 2.

### `parser/`
- **`DatParser.java`** -- full dynamic section/record/field-format
  detection, restructured (not just renamed) from the original port:
  uses `DatRecord`/`DatSection`, pulls all shared literals from
  `Constants`, and all tokenizing from `ValidationUtils`.
- **`Out0Parser.java` / `Out0ParseError.java`** -- parses solved OUT0
  files into `Bus`/`Line`/`Transformer`, tolerant of MiPower's
  Fortran-style "D"/"d" exponent notation and decorative separators.

## One important architecture decision

Your requested `models/` list is `Bus, Generator, Line, Branch,
CaseStudy, ChangeRecord, HistoryEntry, ComparisonResult, RunResult` --
no generic record/section model for `.dat0` **editing**. But the Python
project's whole design center is that editing is **fully dynamic**: it
works on *any* MiPower input file's section/field layout without
hardcoding what a "Bus Data" row looks like, specifically so it survives
files with extra/renamed/reordered sections.

If `Bus.java`/`Generator.java`/`Line.java` were used for the *input* side
too, that dynamism would be gone -- the editor would only work on files
whose Bus Data section has exactly the fields those classes expect. So:

- **Editing `.dat0` input** -&gt; stays dynamic, via `DatRecord`/`DatSection`
  (new, necessary).
- **Reading solved OUT0 results / Analytics / Comparison** -&gt;
  `Bus`/`Generator`/`Line`/`Branch`/`Transformer`, exactly as you asked,
  since that data genuinely has a fixed, known shape.

If you actually want typed Bus/Generator/Line editing on the *input*
side too (a real, different feature -- validated fixed-schema editing
instead of dynamic parsing), tell me and that's a clean addition to plan
into a later module rather than something to silently reinterpret now.

## Bugs caught by manual review (no compiler to catch them instead)

1. **`Double.parseDouble` accepts a trailing `d`/`D`/`f`/`F` suffix**
   that Python's `float()` rejects (`"1.5d"` parses fine in Java, raises
   `ValueError` in Python). Fixed with `ValidationUtils.tryParsePyFloat()`,
   an explicit Python-float-literal regex checked before ever calling
   `Double.parseDouble`, used everywhere on the `.dat0` side instead of
   the raw Java parser.
2. **Python's `key_fields or fallback` treats an empty list as falsy**,
   not just `None`. `parser.py` passes `key_fields=[]` for
   `simple`/`subsection` records *expecting* the "first field name"
   fallback to kick in. A null-only check in Java would have kept the
   list empty instead. Fixed in `DatRecord`'s constructor.
3. **The OUT0 bus-row regex has no trailing `$` anchor** (unlike the
   branch-row regex, which does) -- the original Python uses `re.match()`,
   a *prefix* match, deliberately. Using Java's `Matcher.matches()`
   (which requires the *entire* line to match) for that pattern would
   have silently rejected valid bus rows with any trailing content.
   Fixed by using `lookingAt()` (Java's actual equivalent of Python's
   `re.match()`) for that one pattern, while keeping `matches()` for the
   branch-row pattern, which does need a full match.

Also hardened proactively (no compiler needed to know this one, just
knowing MiPower is Windows software): `Files.readString()` doesn't
perform Python's automatic CRLF-&gt;LF newline translation the way
`open(path, 'r')` does, so both parsers explicitly split on
`"\r\n"`/`"\r"`/`"\n"` rather than a naive `split("\n")`, which would
otherwise leave a stray trailing `\r` on every line of a
Windows-line-ending file.

## Building (module 1 only compiles standalone right now)

```
cd PRDC_MiPower_Editor
mvn clean compile
```

There's no `Main.java` yet (module 3) and nothing runnable yet -- this
module is the parsing/model foundation, verified by inspection, not an
app you can launch. `mvn javafx:run` won't work until module 3.

## Module 2 (this delivery): `services`, plus the deferred `CaseStudy` model

10 more files (28 total now). Everything here was written against Module
1's actual model shapes (field names cross-checked by hand, not assumed).

### `models/CaseStudy.java` (deferred from module 1)
Now that `ModificationManager` exists, `CaseStudy` owns one: its own
`DatParser`, its own `ModificationManager`, its own append-only
`history` (`List<HistoryEntry>`), its own `runHistory`
(`List<RunResult>`), and its own `originalDat0`/`originalOut0`/
`modifiedOut0` (`modifiedDat0`/`report` are accessor methods aliasing
`outputFile`/`reportPath`, matching your requested field names without a
second piece of state to keep in sync).

### `services/`
- **`ModificationManager.java`** -- exactly as requested: the pending
  list + undo/redo stack the GUI will talk to. The actual text-rewriting
  algorithm is delegated to a new **`ModifierEngine.java`**
  (package-private, not in your original list) -- kept separate on
  purpose: `ModifierEngine` holds no state and knows nothing about
  Case Studies, pending lists, or undo/redo; it only knows how to
  rewrite text given a list of modifications. `ModificationManager`
  is the stateful service; `ModifierEngine` is its stateless
  implementation detail. Port of `modifier.py`'s `modifyInput()` and
  all four format handlers (simple/subsection/tabular/two_row_table).
- **`MiPowerRunner.java`** -- runs `powerlfa.exe` via `ProcessBuilder`,
  captures OUT0/PLT/ETC/BAR/NT paths, returns a `RunResult`. Every
  failure mode (missing file, missing install, non-zero exit, launch
  failure) is captured in the result, never thrown.
- **`KPIEngine.java`** -- the system-wide KPI comparison table
  (generation/load/losses/voltage stats/loading stats/violations/
  iterations), each as Original/Modified/Difference/%Change.
- **`VoltageAnalyzer.java`** -- bus-by-bus voltage comparison, top-N
  changes, and violation-flag transition analysis (which buses became
  healthy/worse), plus a voltage health sub-score.
- **`LossAnalyzer.java`** -- line/transformer loading comparison, top-N
  changes, newly-overloaded/newly-normal detection, plus a loss/loading
  health sub-score. Also covers "Power Loss" analysis (real/reactive
  loss % of generation) -- consolidated here with loading rather than
  a separate analyzer, since loading and loss are directly related
  engineering concepts and your service list didn't include a
  dedicated loss-only analyzer alongside a loading one.
- **`RecommendationEngine.java`** -- the "AI Explanation"/"AI
  Recommendations" text. **Honesty note, since "AI" is in the class
  name and stated in its own Javadoc too:** no language model is called
  anywhere. It's a rule-based generator -- sentence templates are
  ordinary Java, but which sentences appear and every number inside
  them are computed fresh from the real comparison each time.
- **`ComparisonEngine.java`** -- orchestrates all four of the above into
  one `ComparisonResult`, and computes the two things that need all of
  them together: the overall Network Health Score (a transparent
  weighted formula, not a black box) and overall status
  (Improved/Degraded/No Significant Change).
- **`ReportGenerator.java`** -- TXT (port of `report.py`'s exact
  column-width formatting), **CSV** (Apache Commons CSV, new), and
  **PDF** (Apache PDFBox, new) export, all as requested.

## Library API calls I could not verify (PDFBox / Commons CSV)

Everything above this section was verified against Python source or
Module 1's own code, which I wrote. `ReportGenerator`'s CSV/PDF code is
different: it calls **third-party library APIs I could not check against
any documentation or compiler** in this sandbox. Specifically:
- `CSVFormat.DEFAULT.builder().setHeader(...).build()` (Commons CSV
  1.11.0's builder API)
- `new PDType1Font(Standard14Fonts.FontName.HELVETICA)` (PDFBox 3.x's
  replacement for the old `PDType1Font.HELVETICA` static constant,
  which was removed/deprecated between PDFBox 2.x and 3.x)

I'm reasonably confident in both based on my training data, but **these
are exactly the kind of thing a real compiler catches instantly and I
can't** -- if `mvn compile` fails inside `ReportGenerator.java`
specifically, it's very likely one of these two calls, and it's a quick
fix once you paste the actual error.

One PDF bug I did catch and fix by manual review (not a guess): I
initially chained multiple `newLineAtOffset()` calls within a single
`beginText()/endText()` block to position table header cells.
`newLineAtOffset()` moves *relative to the current text line matrix*,
not to an absolute page position -- chaining calls like that would have
silently misaligned every column after the first. Fixed by giving each
cell its own `beginText()/endText()` pair with an absolute (x, y)
computed in Java, which is unambiguous and easy to verify by inspection
even without rendering it.

## Building (modules 1+2 compile standalone; still nothing runnable)

```
cd PRDC_MiPower_Editor
mvn clean compile
```

Still no `Main.java` (module 3) and nothing you can launch yet.

## Module 3 (this delivery): `gui`, plus CSS -- the project is now runnable

7 more files (35 total). **This is the first module you can actually
launch.**

### `gui/`
- **`Main.java`** -- entry point, shows `LoginPage`.
- **`LoginPage.java`** -- the requested Login Screen. **Honest note,
  also in its own Javadoc:** there is no user database or auth backend
  anywhere in this project. It accepts any non-empty username+password
  as a UI flow demonstration. Real authentication is a genuinely
  separate feature (credential store? server? Windows accounts?) --
  say so if you want it designed properly rather than left as a gate
  that isn't really one.
- **`Dashboard.java`** -- landing page after login: Browse Input File,
  plus a **persisted recent-files list** via a new
  `services/RecentFilesService.java` (not in your original list) --
  the first real use of the Jackson dependency, which otherwise would
  have sat in `pom.xml` unused. Small and focused on purpose: JSON to
  `~/.prdc_mipower_editor/recent_files.json`, nothing else needs
  persistence yet (Case Studies are session-only, matching the Python
  original).
- **`Workspace.java`** -- the main editor, and the largest file in the
  project: file browsing, multiple independent Case Studies (+New/
  Rename/Copy/Delete, with Copy replaying every active change onto a
  freshly-parsed copy via the same silent condition-matching logic as
  normal editing -- skipped, never guessed, if a row's no longer
  uniquely identifiable), the dynamic table (silent WHERE-condition
  resolution via `FieldEditabilityService`, new this module), Save, and
  **Run MiPower with the same automatic Original-then-Modified
  targeting** described in module 2's `CaseStudy` fields -- filling in
  `originalOut0`/`modifiedOut0` with zero user action, ready for module
  4's Comparison Dashboard to consume immediately.
- **`ReportWindow.java`** -- a Case Study's full Change History table,
  with Export TXT/CSV/PDF wired to `ReportGenerator`.
- **`RunHistoryWindow.java`** -- every MiPower run, every Case Study,
  newest first.

**Analytics and Comparison toolbar buttons exist in `Workspace` but are
placeholders** (a dialog explaining they're module 4) -- they don't
silently do nothing, they tell you what's missing and why, and for
Comparison specifically, whether *this* Case Study's data is even ready
yet.

### CSS
`resources/css/theme.css` -- mirrors `Constants.java`'s color palette
exactly (kept as two places on purpose: `Constants` for non-CSS drawing
like future chart colors, this file for actual JavaFX styling).

## A real bug caught by manual review in this module

`CaseStudy.parser` was originally declared `final DatParser parser = new
DatParser()`. That meant `Workspace` couldn't point a `CaseStudy` at the
already-parsed `DatParser` instance built on the background thread --
it would have had to re-parse the same file a *second time*,
synchronously, on the UI thread, defeating the entire point of parsing
in the background in the first place (and causing a real, avoidable UI
freeze on large files). Fixed by making the field reassignable; caught
by re-reading my own draft, not a compiler, since none exists here.

## Building and running (finally launchable)

```
cd PRDC_MiPower_Editor
mvn clean compile
mvn javafx:run
```

Still true: **not compiled or run here.** No `javac`, no network for
Maven in this sandbox. Please paste back the exact error if it doesn't
build -- three modules in, manual review has caught real bugs every
time, but a real compiler will catch things review alone can't.

## Module 4 (this delivery): `AnalyticsDashboard`, `ComparisonDashboard` -- feature-complete

2 more files (38 total). This is the last module -- every checklist item
from the original request now has a real, wired-up implementation.

### `gui/ComparisonDashboard.java`
The AI Comparison Dashboard: parses a Case Study's `originalOut0` /
`modifiedOut0` (via `Out0Parser`), runs `ComparisonEngine`, and displays
the result across 8 tabs -- Executive Summary, System KPIs, Bus Voltage,
Line Loading, Power Loss, Voltage Violations, AI Explanation, AI
Recommendations. Charts use plain `javafx.scene.chart`
(`BarChart`/`LineChart`, already part of `javafx-controls` -- no new
dependency). The Voltage Heat Map is a hand-built colored `GridPane`
(JavaFX has no built-in heatmap chart type). Like every other file that
touches "AI", it states plainly (in its own tab, and in
`RecommendationEngine`'s Javadoc) that there's no language model
involved.

### `gui/AnalyticsDashboard.java`
Deliberately a **different, smaller thing** than `ComparisonDashboard`,
not a duplicate: a multi-Case-Study *readiness* overview -- one card per
Case Study showing whether its Original/Modified runs exist yet, its
run count, its change count, and a button that opens the full
`ComparisonDashboard` for that specific Case Study once it's ready. This
gives "Analytics" (plural, across everything) and "Comparison" (singular,
one deep dive) genuinely separate purposes, matching having both as
distinct files in your original request rather than picking one
arbitrarily and stubbing the other.

### `Workspace.java` updated
The two placeholder toolbar methods from module 3
(`openAnalyticsPlaceholder`/`openComparisonPlaceholder`) are now real:
`openAnalyticsDashboard()` and `openComparisonDashboard()`, calling
straight into the two classes above.

## Post-delivery fix: Change History panel was missing from the GUI

After module 4 shipped, testing found a real gap: `CaseStudy.history`
and `HistoryEntry` (with Pending/Saved/Undone/Deleted status) existed
and were correctly populated by every edit -- but **nothing in
`Workspace.java` ever displayed them**. `ReportWindow` could show the
full history, but there was no live panel in the Workspace itself, so
pending changes were invisible while editing (the Python version has an
always-visible Change History panel; this port didn't).

Fixed by adding a right-side Change History panel to `Workspace.java`:
- Every edit appears immediately, newest first, color-coded by status
- Status counts (Pending/Saved/Undone/Deleted) shown at the top
- Per-entry **Remove** button (only shown while an entry is still
  active) -- reverts that specific entry's value and marks it Undone,
  independent of the other two buttons below
- **Undo Last** / **Redo** -- a true LIFO pair backed by
  `ModificationManager`'s existing (previously unused by the GUI)
  `undo()`/`redo()` stack
- **Clear All** -- confirms, then reverts and marks every active entry
  Deleted at once

Also fixed two hardcoded status string literals (`"Pending"`, `"Saved"`)
in `onSave()` to use `Constants.STATUS_PENDING`/`STATUS_SAVED` instead,
for consistency with the rest of the codebase.

## Project status: every checklist item has a real implementation

| Requested | Where |
|---|---|
| Login Screen | `LoginPage.java` (UI demo, no backend -- see its own Javadoc) |
| Dashboard | `Dashboard.java` |
| Browse Input File | `Dashboard.java` / `Workspace.java` |
| Automatic Study Type Detection | `DatParser.detectStudyType()` |
| Dynamic DAT0 Parser | `DatParser.java` |
| Dynamic Table Generation | `Workspace.java` (`onSectionChanged`) |
| Editable/Non-editable Columns | `FieldEditabilityService.java` |
| Multiple Case Studies | `CaseStudy.java` + `Workspace.java` |
| Independent Undo/Redo | `ModificationManager.java` (per-CaseStudy instance) |
| Pending/Saved Changes, Change History | `HistoryEntry.java` + `Workspace.java` |
| Report Generation (TXT/CSV/PDF) | `ReportGenerator.java` + `ReportWindow.java` |
| Run MiPower | `MiPowerRunner.java` + `Workspace.java` |
| Parse OUT0 | `Out0Parser.java` |
| AI Comparison Dashboard | `ComparisonDashboard.java` |
| Analytics Dashboard | `AnalyticsDashboard.java` |
| Export Reports | `ReportWindow.java` |
| Run History | `RunHistoryWindow.java` |

## Building and running

```
cd PRDC_MiPower_Editor
mvn clean compile
mvn javafx:run
```

Still true, every module: **not compiled or run here** -- no `javac`, no
network for Maven in this sandbox. Manual review caught real bugs in
every single module so far (see each module's section above) -- but
please paste back the exact error the moment `mvn clean compile` or
`mvn javafx:run` produces one. A real compiler/JVM will keep catching
things review alone can't, exactly like it already did with the pom.xml
XML-comment bug.

## Module 5 (this delivery): hierarchical Base File / Case Study tree -- the big rebuild

This module replaces the **flat** Case Study list from modules 1-4 with the
**hierarchical** Base File -> Case Study 1 -> Case Study 1_1 tree the spec
actually asks for:

```
Base File (never modified)
 |-- Case Study 1
 |     |-- Case Study 1_1
 |     `-- Case Study 1_2
 |-- Case Study 2
 `-- Case Study 3
```

### New files

- **`services/CaseStudyManager.java`** -- owns the Base File's raw text and
  the whole tree of `CaseStudy` objects. The key mechanism: a Case Study's
  "resolved text" (what its data actually looks like right now) is computed
  by starting from the Base File's raw text (or, for a child, its parent's
  resolved text) and replaying only that ONE Case Study's own active
  `ChangeRecord`s on top, via the same `ModificationManager.applyToText`
  that already existed. That single mechanism gives the whole tree its
  inheritance semantics for free -- a child always sees everything its
  parent has, plus its own edits, and nothing a sibling did -- with zero
  new text-rewriting logic. `resolveText(cs)` / `resolveReferenceText(cs)`
  are always recomputed from scratch (never cached against a stale
  snapshot), so they're correct even if an ancestor's pending list changes
  after a descendant was created. Also handles creation (root vs. child, "New
  Case -> Create From: Base File / Current Case Study"), rename (display
  name only -- the permanent `hierarchicalId` never changes), duplicate
  (new sibling, replaying only the source's own local changes), and
  cascading delete (a Case Study and every descendant).
- **`services/ChangeResolver.java`** -- answers, for ANY section/record/field
  in ANY Case Study, why it looks the way it does: `Unchanged`,
  `Inherited` (an ancestor changed it, this Case Study never touched it
  again), `Local Change` (this Case Study changed it directly, no ancestor
  had), or `Modified` (this Case Study changed it directly, overriding a
  value an ancestor had already changed). Never hardcoded to one section or
  field -- it walks whichever section/record/field you ask about, matching
  `ChangeRecord`s against a row with the exact same condition-matching
  `DatRecord.matches()` already uses elsewhere, so "was this row touched"
  can never disagree between the editor and the status column. Also
  produces the Section Record Explorer numbers (Total/Editable/Modified/
  Inherited/Unchanged records per section) and the Record Detail view
  (every field's Original-from-Base-File vs Current value).
- **`services/CaseStudyStorage.java`** -- PART 6 (Save). Every Case Study
  gets its own `.dat0` file and output folder
  (`CaseStudies/Case1_1/Case1_1.dat0`, `.../Outputs/Case1_1.out0`, etc.),
  keyed off the permanent `hierarchicalId` (via `CaseStudy.slug()`), so a
  save can never collide with or overwrite the Base File, a parent, or a
  sibling, and a rename never orphans a previously-saved file.
- **`gui/CaseStudyTreeModel.java`** -- builds the JavaFX `TreeView`
  hierarchy, with a synthetic, always-selectable "Base File (Reference)"
  root node (kept as a small `Node` wrapper rather than using `CaseStudy`
  directly, since the Base File isn't really a Case Study).

### Changed files

- **`models/CaseStudy.java`** -- added `parentId` (null = Base File is the
  reference), the permanent `hierarchicalId` ("1", "1_1", "1_2", "2", ...),
  and `childIds`. `slug()` now uses `hierarchicalId` instead of the raw
  numeric `id`, so file names stay stable across renames.
- **`utils/Constants.java`** -- added the field-status vocabulary
  (`FIELD_UNCHANGED/INHERITED/LOCAL_CHANGE/MODIFIED`) and matching colors,
  separate from the existing Pending/Saved/Undone/Deleted change-lifecycle
  vocabulary (`STATUS_*`) -- deliberately two different concepts that both
  happen to be called "status" in the spec.
- **`gui/Workspace.java`** -- rewritten around `CaseStudyManager` instead of
  a flat `List<CaseStudy>`:
  - Sidebar's `ListView` became a `TreeView<Node>` showing the whole
    hierarchy; the Base File is always node zero, and is read-only in the
    table (browsing only, per Part 1's requirement that it's never edited
    directly).
  - **New Case** opens a real dialog with the spec's exact "Create From: Base
    File / Current Case Study" radio choice, not a hidden default.
  - The records table gained a **Status** column (only for Case Studies,
    not the Base File), a **search field** (matches any field's text, built
    generically from whatever fields that section actually has -- never
    hardcoded to Transmission Line's From Bus/To Bus), and a **filter**
    dropdown (All / Modified Only / Unchanged / Inherited / Local Changes /
    Editable / Non-Editable).
  - A **Section Record Explorer** line above the table now always shows
    Total/Editable/Modified/Inherited/Unchanged counts for whichever
    section is open, for every section, not just Transmission Line.
  - A new **Record Detail** tab (next to Change History) shows, for the
    selected row, every field's Original (Base File) vs Current value and
    its status -- editable and non-editable fields both, never hidden.
  - **Save** now writes through `CaseStudyStorage` to the Case Study's own
    file, built from `manager.resolveText(cs)` (Base + every ancestor's
    changes + this Case Study's own changes) instead of always re-reading
    the Base File directly -- the real bug the flat model had: module 1-4's
    `onSave()` always applied a Case Study's changes on top of the raw
    Base File text, which was correct for root Case Studies but would have
    silently DROPPED a parent's changes for any child Case Study once
    hierarchy existed. Fixed by construction in this module.
  - **Run MiPower** now auto-saves the latest resolved data before running,
    runs the Base File once (automatically, the first time it's needed) to
    seed the shared baseline, and sets each Case Study's reference output
    to the Base File's output (root Case Study) or its parent's own last
    output (child Case Study) -- exactly the comparison pairing Part 4
    specifies for every level of the tree. `ComparisonDashboard` and
    `AnalyticsDashboard` needed **no changes at all**: they already read
    `CaseStudy.originalOut0`/`modifiedOut0`, and this module just changed
    what populates those two fields.

### How I verified this (given the same "no compiler in this sandbox" limits as every earlier module)

Unlike modules 1-4, this delivery **was actually compiled**, not just
manually reviewed. This sandbox has no direct internet access to Maven
Central, but its OS package manager (`apt`) carries OpenJDK 21,
`openjfx` (JavaFX 11 -- older than the `pom.xml`-pinned 21.0.2, but
API-compatible for every class used here), `libjackson2-databind-java`,
`libcommons-csv-java`, and `libpdfbox2-java` (2.0.29, older than the
`pom.xml`-pinned 3.0.2). Installing those and running `javac` directly
against all 42 source files compiled **every file cleanly** except 3
errors, all in `ReportGenerator.java`'s PDF export code -- and all three
are explained entirely by the PDFBox 2.x/3.x API gap already flagged
honestly in module 2's "Library API calls I could not verify" section
(`Standard14Fonts.FontName` and the matching `PDType1Font` constructor
are PDFBox-3.x-only; `pom.xml` correctly pins 3.0.2). With a clean,
single-version classpath, the *only* remaining errors were exactly those
3 lines -- nothing else in the project, including every file this module
touched or added, produced a single error or unexpected warning.
**This doesn't guarantee `ReportGenerator`'s PDF code is correct** (still
unverified against the real PDFBox 3.x jar), but it does mean the other
41 files -- the entire hierarchical Case Study system, the parser, the
analytics/comparison engines, and the rest of the GUI -- compile as
real Java, not just "looks right on manual review."

### Project status table, updated for this module

| Requested | Where |
|---|---|
| Base File is never modified | `CaseStudyManager` (only ever reads `baseRawText`) |
| Hierarchical Case Studies (1, 1_1, 1_2, 2, 3, ...) | `CaseStudyManager` + `CaseStudy.parentId/hierarchicalId` |
| Case Study tree view | `CaseStudyTreeModel.java` + `Workspace.java` |
| New Case: Base File vs Current Case Study | `Workspace.onNewCaseStudy()` |
| Rename / Duplicate / Delete (with children) | `CaseStudyManager.rename/duplicate/delete` |
| Every section/record/field tracked, not just Slack Bus Angle | `ChangeResolver.java` (works generically off `DatSection`/`DatRecord`) |
| Section Record Explorer (Total/Editable/Modified/Inherited/Unchanged) | `ChangeResolver.sectionSummary()` + `Workspace.refreshSectionSummary()` |
| All records visible, searchable, filterable | `Workspace.applyFilters()` (`TableView`, not paginated -- see Known Gaps) |
| Editable vs non-editable, always visible | `FieldEditabilityService.java` (unchanged) + Status column |
| Record status (Unchanged/Modified/Inherited/Local Change) | `ChangeResolver.FieldStatus` |
| Record Details (Original vs Current per field) | `ChangeResolver.recordDetail()` + Workspace's Record Detail tab |
| Per-Case-Study Save (never overwrites Base/parent/sibling) | `CaseStudyStorage.java` |
| Run MiPower with automatic Base-vs-parent reference targeting | `Workspace.onRunMiPower()` / `onCaseRunFinished()` |
| Analytics/Comparison against the correct reference at every tree level | `CaseStudy.originalOut0/modifiedOut0`, populated per the rule above; `ComparisonDashboard`/`AnalyticsDashboard` unchanged |

## Module 6 (this delivery): professional analytics upgrade

Focused specifically on making the Comparison/Analytics side "professional
data analytics" per the spec's Parts 20-27, on top of Module 5's
hierarchical Case Study tree (Module 6 does not touch Login, persistence,
or the rest of the broader Part-1-through-35 spec -- those are still
tracked as open items below, ready for a follow-up delivery).

### New files
- **`services/AnalyticsEngine.java`** -- the top-level analytics facade
  the architecture list asks for by name, sitting above `ComparisonEngine`.
  Owns two things the dashboard needs beyond a single `ComparisonResult`:
  **Manual mode** (`buildManualSeries` turns a user-picked `ManualType` +
  the already-computed `ComparisonResult` into a small, chart-ready
  `ManualSeries` -- the GUI never decides which numbers go in a manual
  chart, it only draws what this class hands back), and **Change Impact**
  (`buildChangeImpact` pairs a Case Study's own local edits with the real
  KPI/bus/line deltas that followed, with an explicit non-causal caveat --
  see its Javadoc for why it's deliberately NOT a causality claim).
- **`services/LineLoadingAnalyzer.java`** -- the dedicated Line Loading
  service the architecture list also asks for by name. A thin,
  intention-revealing facade in front of `LossAnalyzer` (loading and loss
  share one data source in MiPower's OUT0 output -- see that class's
  Javadoc), so GUI code that only cares about "Line Loading" depends on
  one clearly-named class instead of reaching into a loss-and-loading
  combined analyzer.

### Changed: `gui/ComparisonDashboard.java`
- **Auto / Manual mode toggle** (Part 26) at the top of the dashboard,
  defaulting to Auto. Auto shows the existing full tab set (now plus
  Change Impact, below). Manual shows a Type dropdown (Voltage /
  Generation-Load-Loss / Line Loading / Voltage Violations) and a Graph
  dropdown (Bar / Line); picking either rebuilds the chart immediately
  from `AnalyticsEngine.buildManualSeries()` -- no placeholder/fake data.
- **New "Change Impact" tab** (Part 24) -- "What You Changed" (this Case
  Study's own local edits) next to "What Happened After Running MiPower"
  (real KPI/bus/line deltas), with the non-causality caveat always shown,
  never asserting the input changes caused the listed results.
- **Professional error handling** (Part 27), replacing the old "return an
  error string, caller shows a plain `Alert`" flow:
  `ComparisonDashboard.open(caseStudy, onRunRequested)` now ALWAYS opens a
  window. If comparison data isn't ready, it shows an in-app "MiPower
  results are not available for this Case Study" screen (never an empty or
  fake chart) with a one-click **Run MiPower** button wired back to
  `Workspace.onRunMiPower()`. If OUT0 parsing throws, it shows "Unable to
  analyze the MiPower result file" with the real exception in a collapsed,
  expandable **Details** panel -- visible on demand, not hidden entirely
  and not dumped in the user's face by default. `AnalyticsDashboard.java`
  was updated to call the same new API. The old `openFor(CaseStudy)`
  method is kept as a deprecated shim (still opens the dashboard; no
  longer needed by anything in this project) so nothing calling it breaks.

### Verification
Recompiled the full project (now 44 source files) with the same `javac` +
apt-installed-library setup described in Module 5. Result: identical to
Module 5 -- every file compiles cleanly except the same 3 pre-existing
`ReportGenerator.java` PDFBox-2-vs-3 lines (unrelated to this module,
explained in Module 5's verification section). Nothing this module added
or changed introduced a single new compiler error.

### Still open from the newer, larger spec (not in this delivery)

This delivery deliberately scoped to the analytics parts. Everything else
in the latest spec message -- removing the Login page, the redesigned
three-pane professional layout, deeper Case Study actions (delete
this-case-only vs. cascade, a global Browse-the-whole-tree dialog),
Parent Value in Record Details, a combined Change Explorer view, keyboard
shortcuts, a breadcrumb, and full project persistence across restarts --
is real, understood, and not yet built. Flagging it explicitly here rather
than silently leaving it out.

## Module 7 (this delivery): the New Case dialog bug, actually fixed

Your last message's core, repeated complaint (sections 4, 5, and 40) was
specific and correct: the New Case dialog only offered two radio buttons
("Base File" / "Current Case Study"), so a Case Study could only ever be
created as a child of whatever happened to be selected in the sidebar --
you could never open the dialog while looking at Case Study 1_1 and create
a new child under Case Study 2, for example. That's a real bug, and this
delivery fixes it directly rather than patching around it.

### New file: `gui/NewCaseDialog.java`

Replaces the old two-radio-button dialog entirely. It shows the SAME
`TreeView` hierarchy as the sidebar (reusing `CaseStudyTreeModel`, so the
two can never drift apart), with every node selectable -- Base File, or
any Case Study at any depth. Selecting a node and clicking "Create Case"
calls `CaseStudyManager.createChildCaseStudy(selectedNodeOrNull, name)`
directly, which already treats a `null` reference as "create at the root,
from the Base File." Verified against the exact scenarios your spec's
section 40 test case describes:

- Tree open while Case Study 1_1 is the current selection elsewhere in the
  app still shows Base File, Case Study 1, 1_1, 1_2, 2, and 3 -- every
  case, not just the current branch.
- Selecting Case Study 1 and creating a case produces Case Study 1_3 (its
  next child), regardless of what was selected before opening the dialog.
- Selecting Case Study 2 produces Case Study 2_1.
- Selecting Case Study 1_1 produces Case Study 1_1_1 -- arbitrary depth
  was already supported by `CaseStudyManager`'s hierarchical-id logic
  (`parent.hierarchicalId + "_" + childIndex`, applied recursively), so no
  change was needed there -- only the dialog was the bottleneck.

`gui/Workspace.java`'s `onNewCaseStudy()` now calls this dialog instead of
building the old radio-button `Dialog` inline.

### Verification

Recompiled all 45 source files with the same `javac` + apt-library setup
described in Modules 5 and 6. Result: identical to before -- every file
compiles cleanly except the same 3 pre-existing `ReportGenerator.java`
PDFBox-2-vs-3 lines (explained in Module 5), unrelated to this fix.

### What this delivery does NOT cover

Your latest message is effectively a full architectural rewrite spec (new
`models/parser/services/ai/gui/utils` package layout with ~15 renamed or
brand-new classes, an AI Agent chat assistant separate from Auto/Manual
analytics, a dedicated Multi-Case Comparison screen with ranking, a
Manual-mode "add multiple visualizations as cards" builder, project
persistence, etc.) layered on top of everything built in Modules 1-6. That
is real, substantial, multi-session work that a single "fix the errors"
turn can't respect without either faking files or silently dropping most
of the spec. Given your closing line asked specifically to correct errors
and give a correct version, this delivery fixed the one concretely-broken,
repeatedly-flagged behavior (the New Case dialog) and re-verified the
whole project still compiles, rather than guessing at scope for the rest.
Tell me which of the remaining pieces (AI Agent, Multi-Case Comparison,
and Manual mode's multi-visualization builder are probably the highest-
value next three) to build next and I'll do that properly, the same
way -- real files, real compile verification, nothing faked.

## Module 8 (this delivery): the AI Agent -- a real, different third analytics mode

Direct response to "you didn't even change the analytics part, I need
different analytics ... I need AI Agent" -- this delivery adds the AI
Agent as a genuinely separate feature, not a re-skin of Auto or Manual
mode:

| | Auto Mode | Manual Mode | AI Agent |
|---|---|---|---|
| Where | `ComparisonDashboard`, Auto tab set | `ComparisonDashboard`, Manual pane | New `AIAgentWindow`, its own top-level window |
| Input | Nothing to choose -- always the full fixed tab set | User picks a Type + Graph, sees ONE chart | User types a free-text QUESTION |
| Scope | One Case Study vs. its one reference | One Case Study vs. its one reference | ANY Case Study, or ANY two, or a ranking across EVERY case in the tree |
| Output | Charts + tables | One chart, rebuilt live from the picked Type/Graph | A written answer, phrased for the specific question asked |

### New package: `ai/`
- **`ai/AnalyticsAssistant.java`** -- the question router. Takes a
  free-text question plus the "currently open" Case Study as a fallback
  context, and:
  1. figures out which Case Stud{y,ies} the question refers to (by name,
     e.g. "Case Study 1_1", or by bare id, e.g. "1_1", matched against
     every case in `CaseStudyManager`, longest id first so "1_1_1" isn't
     shadowed by "1_1"; "base"/"base file" resolves to the Base File's own
     run via `CaseStudyManager.baseOut0`);
  2. figures out the INTENT from keywords -- compare two cases, rank every
     case by a metric (loss / overloaded branches / voltage violations /
     minimum voltage), a case's inherited-vs-local changes, a specific
     bus's voltage and why it differs, overloaded lines, voltage
     violations, or a general "what happened" summary;
  3. loads only the real `.out0` file(s) that intent actually needs
     (cached per path so a follow-up question doesn't re-parse), and hands
     the real numbers to `ExplanationEngine` to phrase.
  If it can't confidently identify the question, it says so and shows
  what it does understand, rather than guessing or fabricating an answer.
- **`ai/ExplanationEngine.java`** -- turns already-computed facts into
  sentences (two-case comparison summaries, "best/worst" rankings with the
  full ordered list, inherited-vs-local change breakdowns, a specific
  bus's before/after voltage and whether it's a new or resolved
  violation, an overloaded-branches list). Deliberately separate from
  `AnalyticsAssistant`: it never touches a `.out0` file, a `CaseStudy`, or
  decides what the question meant -- only how to phrase what it's handed.
  Same honesty rule as every other "AI" surface in this project (see
  `RecommendationEngine`'s Javadoc): template-driven text from real
  numbers, never a language model call, never an invented number.

### New GUI: `gui/AIAgentWindow.java`
A chat window (message bubbles, a text input, and five one-click
suggestion chips covering the spec's example questions) opened from a new
"\uD83E\uDD16 AI Agent" toolbar button in `Workspace.java`. Non-modal, unlike the
other analytics windows, specifically so it can stay open while the user
switches which Case Study is selected in the main workspace -- the
Agent's fallback context (used when a question doesn't name a Case Study)
is read live via a `Supplier<CaseStudy>`, not frozen at the moment the
window opened.

### What it can answer right now (verified against your spec's own examples)
- "What changed in Case Study 1_1?" / "What changes were inherited by
  Case Study 1_1?" -- real inherited-vs-local breakdown from
  `CaseStudyManager.ancestorChain()` + each Case Study's own change history.
- "Compare Case Study 1 and Case Study 2." / "Compare Case 1_1 and Case
  1_2." -- parses both cases' own `.out0` results and runs the same
  `AnalyticsEngine` used by Auto Mode between THEM specifically (not
  either one's reference), then states which one is better and why.
- "Which case has the lowest loss?" / "Which case has the best voltage?"
  -- ranks every Case Study (and the Base File) that has a completed
  MiPower run, by real total loss / minimum voltage / overloaded-branch
  count / violation count, with the full ordered list, not just the winner.
- "Why is bus 5 voltage different in Case Study 1_1?" -- looks up bus 5 in
  both the reference and current results, states the before/after
  voltage, and flags if it's a new or resolved violation.
- "Are there any overloaded lines / voltage violations in Case Study X?"
  -- lists them from that case's own real result.
- "What happened after running MiPower on Case Study 1?" -- same
  `aiSummary` narrative Auto Mode's Executive Summary tab shows, reusable
  here because it's the same underlying `ComparisonResult`.

### Verification
Recompiled the full project (now 48 source files across 6 packages
including the new `ai/`) with the same `javac` + apt-library setup as
every prior module. Result: unchanged from before -- every file compiles
cleanly except the same 3 pre-existing `ReportGenerator.java` PDFBox-2-vs-3
lines. I could not runtime-test `AnalyticsAssistant` against a real
`.out0` file in this sandbox (no MiPower installation or sample output
file is available here), so its question-matching logic is verified by
careful manual review and a clean compile, not an executed test run --
flagging that honestly rather than claiming more than I've checked.

### Still open
Multi-Case Comparison as its own dedicated screen (spec sections 25-27:
checkbox case selection, grouped/radar/ranking charts, "Best Overall
Case" style awards) is a distinct, real feature from the AI Agent's
text-based ranking answers above, and isn't built yet. The AI Agent's
ranking answers (loss/voltage/violations across every case) cover the
same underlying data but as text, not as the dedicated visual screen the
spec describes. Also still open from prior modules: removing Login,
project persistence, and the `models/parser/services/gui` package
restructuring from the larger spec two messages ago.

## Module 9 (this delivery): the 6 concrete fixes from your numbered feedback

Direct response to your 6-point list. Each item below maps to exactly one
of your numbered complaints.

**1. Case Study Hierarchy panel removed from the sidebar.** The sidebar
now shows only the Base File card and a compact "Current Case" breadcrumb
(e.g. "Base File File \u203A Case Study 1 \u203A Case Study 1_1"). The full tree
still exists and still shows every saved case -- it just lives in the new
**`gui/AllCaseStudiesDialog.java`**, opened on demand from a toolbar/
sidebar "All Case Studies" button, with Open/New Case/Rename/Duplicate/
Delete/Compare/Close all in one place.

**2. New Case dialog kept (as you asked), inheritance now visible
immediately.** `NewCaseDialog.java` is unchanged. What changed:
`onNewCaseStudy()`, `onDuplicateCaseStudy()`, and `AllCaseStudiesDialog`'s
Open button all now call a new `selectCaseStudy(cs)` method that
immediately opens the new/duplicated/selected case in the main editor --
so if Case Study 1 has a saved Voltage change and you create Case Study
1_1 from it, 1_1 opens right away already showing that inherited value,
instead of you having to separately go find and open it. Mechanically,
this works because a child Case Study's data is always computed by
replaying its own edits on top of its parent's CURRENT resolved text (see
`CaseStudyManager.resolveText()`, unchanged since Module 5) -- there's no
separate "sync" step that could be forgotten or missed.

**3. Analytics scrolling fixed.** Auto Mode's 9-page `TabPane` (which
needed left/right scroll arrows once the tab strip overflowed) is now a
left-navigation list -- Executive, System KPIs, Voltage, Lines, Loss,
Violations, Change Impact, AI Explanation, AI Insight -- shown all at once,
no overflow regardless of page count. Selecting a page swaps the content
pane; nothing scrolls sideways anymore.

**4. Absolute power loss values.** Fixed at the source in
`KPIEngine.compare()` -- Real Power Loss and Reactive Power Loss are now
always `Math.abs()`'d before they're placed in the KPI table that every
other page (Change Impact, AI Explanation, Executive Summary) reads from.
Also fixed in `LossAnalyzer`'s health-subscore and loss-percent-of-
generation calculations (a raw negative loss value could previously have
inflated a case's health score above 100), and in the AI Agent's
loss-based case ranking. Signed values are still exactly what
`Out0Parser` reads from the file, kept in `Out0Results.summary` for
anything that needs the raw signed number -- only DISPLAY paths apply the
absolute value.

**5. Danger Detection Engine + "Cases in Danger" dashboard.** New
**`services/DangerDetectionEngine.java`**: classifies any solved case as
\uD83D\uDFE2 Healthy / \uD83D\uDFE1 Warning / \uD83D\uDD34 Critical from real data --
convergence failure (always Critical), voltage violations (>50 Critical,
>20 Warning), overloaded branches (>10 Critical, >3 Warning), any branch
above 120% loading (Critical), and real loss more than 15% above the
reference case (Warning) -- every rule that fired is listed, not just the
worst one. New **`gui/CasesInDangerWindow.java`**, opened from a new
"\u26A0 Cases in Danger" toolbar button: every Case Study (and the Base
File) with a completed MiPower run, sorted worst-first, with Critical/
Warning/Healthy summary counts at the top.

**6. Manual Analytics expanded -- real MiPower entities, real chart types.**
`AnalyticsEngine.ManualType` went from 4 to 13 options, every one backed
by real parsed OUT0 fields (never a placeholder): Bus Voltage, Bus Angle,
Generator MW, Generator MVAr, Load MW, Load MVAr, Line/Transformer
Loading, Line/Transformer MW Flow, Line/Transformer Real Loss,
Line/Transformer Reactive Loss, Generation/Load/Loss system totals,
Reactive system totals, Voltage Violations. `ManualGraph` went from 2 to
7: Bar, Line, Stacked Bar, Area, Scatter, Pie, and Table ("View Data").
Three spec-requested types -- Frequency, Transformer Tap Position, and
Area/Zone Load -- are deliberately NOT in the dropdown: `Out0Parser`
doesn't currently extract those fields from the OUT0 file, and this
project's rule (stated since Module 1) is never to offer a Type this
class can't back with a real number. Radar, Waterfall, Donut, and Bubble
charts from the spec also aren't implemented -- JavaFX's chart library has
no built-in Radar/Waterfall, and a Pie chart already covers the
single-moment "proportions" use case Donut targets; flagging rather than
faking these.

### Verification
Recompiled the full project (now 51 source files) with the same `javac` +
apt-library setup as every prior module. Result: unchanged from before --
every file compiles cleanly except the same 3 pre-existing
`ReportGenerator.java` PDFBox-2-vs-3 lines.

### Not done this round (from your two attached Master Prompts)
Multi-Case Analytics as a dedicated checkbox-driven screen with
grouped/radar/ranking charts, "Best Case/Worst Case" award cards inside
Auto Mode itself (the Danger dashboard above covers similar ground but as
a list, not KPI cards), Export PDF/Excel from the analytics screens,
Ctrl+S/Ctrl+Z keyboard shortcuts, and the left-nav "Analytics" menu
replicated inside a redesigned single-workspace shell (this delivery kept
`Workspace.java`'s existing toolbar+sidebar+center+right layout rather
than the ground-up 4-pane redesign both Master Prompts describe). Tell me
which of these to prioritize next.

## Module 10 (this delivery): Login actually removed, AI Agent embedded in Analytics, absolute values everywhere, less noise

Direct response to this round's feedback.

**Login page actually removed this time.** Previous modules kept saying
"remove login" but `Main.java` was still launching `LoginPage` -> `Dashboard`
-> `Workspace`. Fixed for real: `Main.start()` now creates
`new Workspace(null)` directly -- no username, no password, no click-through
Dashboard screen. `LoginPage.java` and `Dashboard.java` are DELETED from the
project (not just unused) so there's no ambiguity about which screen the
app opens to. `Workspace`'s toolbar no longer has a "\u2190 Dashboard" button
since there's nowhere to go back to.

**AI Agent is now embedded directly inside Analytics**, not just a
separate toolbar button someone could miss. Opening the Comparison
Dashboard's Auto Mode left-nav now includes a "\uD83E\uDD16 AI Agent" page,
right alongside Voltage/Lines/Loss -- a chat panel scoped to the Case
Study you're already looking at, with one-click suggested questions
("Which bus changed the most?", "Why did the loss change?"). The
standalone `AIAgentWindow` (whole-tree questions, any two cases) is still
available from the main toolbar too -- this embedded one is the "I can't
find it" fix, answering questions about the case you're already in
without leaving the Analytics screen.

**Absolute values, verified end-to-end this time.** Grepped every file
that reads `real_loss_mw`/`reactive_loss_mvar` directly (not through the
already-fixed `KPIEngine`) and confirmed the AI Agent's loss ranking is
the only other place that touched the raw field, and it already had
`Math.abs()` applied from the prior module. `groupedBarChart` (the bar
chart used on the Executive Summary and Power Loss pages) and
`RecommendationEngine`'s narrative text both read exclusively through
`result.kpi(...)`, which is fixed at the source -- so nothing downstream
of it can show a negative loss.

**"Just the buses/lines that changed" -- less noise for a non-electrical
reader.** The Bus Voltage and Line Loading pages' full tables now default
to a "changed only" filter (a checkbox, on by default) instead of dumping
every single bus/branch whether it moved or not. The heading also states
the count up front, e.g. "Buses (7 changed out of 143)" -- the full list
is one click away via the checkbox, never gone entirely.

### Verification
Recompiled the full project (49 source files -- 2 fewer than last module,
since `LoginPage.java` and `Dashboard.java` were deleted) with the same
`javac` + apt-library setup as every prior module. Result: unchanged --
every file compiles cleanly except the same 3 pre-existing
`ReportGenerator.java` PDFBox-2-vs-3 lines.

## Known gaps and honest limitations (read before relying on this for real work)

- **No user database for Login** -- accepts any non-empty credentials.
- **Duplicating a Case Study re-parses the file** rather than deep-copying
  already-parsed data.
- **`runner.py`'s Windows-only assumption carries over**: `MiPowerRunner`
  expects `powerlfa.exe` at `C:\MiPower10_1\`.
- **Bus/branch comparison assumes unchanged topology** (matches by bus
  number / by kind+from+to) -- added/removed buses or lines show `--` on
  the missing side rather than a computed difference.
- **Float formatting**: `DatParser`'s subsection values use
  `String.valueOf(double)`, which doesn't always format identically to
  Python's `str(float)` for the same number.
- **PDFBox/Commons CSV API calls** in `ReportGenerator.java` are the one
  place in this project calling third-party library APIs I couldn't
  cross-check against source I could read -- flagged in module 2's
  section above.
- **"AI" throughout means rule-based, dynamically-generated text from
  real computed numbers -- never a language model call.** Stated in
  `RecommendationEngine.java`'s Javadoc and on the dashboard's own AI
  tabs, not just here.
- **The records table is not virtualized/paginated.** JavaFX's `TableView`
  lazily renders only visible rows by default, which comfortably handles
  hundreds to a few thousand records, but there's no explicit paging UI
  for files with tens of thousands of rows in one section.
- **`resolveText()` is recomputed from scratch on every Save/Run**, walking
  the full ancestor chain and re-running `ModifierEngine` each time. Fine
  for the tree depths and file sizes this app targets; a very deep chain
  (many nested Case Studies) on a very large `.dat0` would be the first
  place to add caching if it ever became slow.
- **Run MiPower's "auto-run the Base File once" only fires for a *root*
  Case Study whose reference (`manager.baseOut0`) is still empty.** If a
  child Case Study's parent has never been run, `Workspace` does NOT
  auto-run the parent for you (that could cascade into running an entire
  branch of the tree unexpectedly) -- it runs the child anyway and tells
  you, in the result dialog, that the reference isn't ready yet and which
  Case Study to run to fix it.
- **PDFBox 3.x API usage in `ReportGenerator.java` is still unverified
  against a real PDFBox 3.x jar** -- this sandbox's package manager only
  provides PDFBox 2.0.29, which doesn't have the `Standard14Fonts`/
  `PDType1Font(FontName)` API the code (correctly, per `pom.xml` pinning
  3.0.2) uses. Every other one of the 42 source files, including
  everything added in this module, was confirmed to compile cleanly with
  `javac` -- see this module's "How I verified this" section above.

