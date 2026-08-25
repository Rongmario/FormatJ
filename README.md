# FormatJ

A Java code formatter that is configurable, buildable from CI, and the same everywhere it runs.

## Origins

Java's formatters... [are a pain in the ass](https://jqno.nl/post/2024/08/24/why-are-there-no-decent-code-formatters-for-java/)
- `google-java-format` forces two-space indent and over-indents continuations
- `prettier-java` is aesthetically pleasing but unstable between versions and needs a NodeJS runtime
- IntelliJ's formatter cannot be invoked outside the IDE
- Eclipse JDT needs Eclipse itself to produce an XML file nobody wants to edit
- `palantir-java-format` and `spring-java-format` ship no usable command line.

- `FormatJ` aims to do the best of all worlds. Core engine + ways for devs to consume in different ways: 
  - Builder-style library
  - CLI
  - Gradle plugin
  - Maven plugin
  - IntelliJ plugin

## Design

- **Lossless by Construction:** The lexer emits every character exactly once, and the parser attaches
  every comment to exactly one token, so the tree always concatenates back to the original file.
  Everything above it can therefore be verified.
- **Verified Output:** Formatting must be a fixed point, and must preserve the significant token
  stream. If either check fails, the original source is returned with a diagnostic.
- **Declared Rewrites:** Rules that add or remove code runs in a separate stage that declares
  every token it changed. The output is checked against that declaration token for token, so an
  undeclared change fails loudly as a corrupted one. A rewrite that fails verification costs
  the file its rewrites and not its formatting.
- **One Rule Catalogue:** Every rule is an `Option<T>` registered once. The TOML reader, the Gradle
  DSL, the Maven parameters and `--dump-config` all read from it.
- **Author's layout matters:** The `preservation.*` rules keep blank lines, chain breaks and
  hand-arranged initializers the author chose. Refusing to do that is what makes a formatter
  correct but unpleasant.
- **Alignment is padding:** The `alignment.*` rules run over text the layout engine has
  already produced, because where a run of lines should share a column is not known until they have
  all been printed. Nothing they do can move a line break.

## Library Usage

```java
Formatter formatter = FormatJ.newFormatter()
        .style(Style.preset(Preset.FORMATJ)
                .indent(indent -> indent.size(4).useTabs(false).continuation(8))
                .wrapping(wrapping -> wrapping.maxLineLength(120)
                        .chainedCalls(ChainPolicy.BREAK_ALL_IF_MULTILINE))
                .switches(switches -> switches.arrowCaseBraces(BracePolicy.WHEN_MULTI_STATEMENT))
                .build())
        .languageLevel(LanguageLevel.LATEST)
        .build();

FormatResult result = formatter.format(FormatRequest.of(source).withName("Foo.java"));
```

A formatter is immutable and thread-safe; one instance can serve the whole project.

## Command Line Usage

```
formatj --check src/main/java          # exit 1 if anything would change
formatj --write src/main/java          # rewrite in-place
formatj --diff src/main/java           # unified diff of what would change
formatj --dump-config                  # every rule with its effective value, as TOML
cat Foo.java | formatj --stdin --stdin-name Foo.java
```

Rules resolve strongest-first: `--set key=value`, then `--preset` or `--style`, then the nearest
`formatj.toml` above the file, then the built-in defaults.

Build it with `./gradlew :app:installDist` and the launcher lands in `app/build/install/formatj/bin`.

## Gradle Plugin Usage

```kotlin
plugins {
    java
    id("zone.rong.formatj") version "0.1.0"
}

formatJ {
    preset = Preset.GOOGLE
    styleFile = file("formatj.toml")
    rule("indent.size", 4)
    sourceSets("main", "test")
}
```

- Adds `formatJavaApply` and `formatJavaCheck`; `check` depends on the latter unless `enforceOnCheck = false`.
- Both tasks are cacheable and incremental, and every rule is a task input.

## Maven Plugin Usage

```xml
<pluginRepositories>
  <pluginRepository>
    <id>cleanroom</id>
    <url>https://maven.cleanroommc.com</url>
  </pluginRepository>
</pluginRepositories>

<plugin>
  <groupId>zone.rong.formatj</groupId>
  <artifactId>formatj-maven-plugin</artifactId>
  <version>0.1.0</version>
  <configuration>
    <styleFile>${project.basedir}/formatj.toml</styleFile>
    <rules>
      <indent.size>4</indent.size>
    </rules>
  </configuration>
</plugin>
```

- `formatj:format` binds to `process-sources`, `formatj:check` to `verify`.

## IntelliJ Plugin

Build the plugin zip with `./gradlew :intellij-plugin:buildPlugin`. The artifact lands in
`intellij-plugin/build/distributions/`. Install it from disk via **Settings > Plugins > ⚙ >
Install Plugin from Disk...**.

After install, **Reformat Code** (`Ctrl+Alt+L`) and **Optimize Imports** on Java files run FormatJ
instead of the built-in Java formatter. Format-on-save uses it too, because it uses Reformat Code.
Style comes from the nearest `formatj.toml` above the file, the same walk the CLI does.

Disable it per project under **Settings > Tools > FormatJ**. The same page can pin a style file or
preset; leaving both empty keeps discovery.

Selection and "only VCS changes" format the whole file internally, then splice only the hunks that
overlap the requested ranges. Enter and paste still use IntelliJ's indent — FormatJ does not run on
every keystroke.

Smoke it locally with `./gradlew :intellij-plugin:runIde`.

## Configuration

`formatj.toml` is discovered by walking up from each file.
A `preset` key chooses the starting point and every other key overrides one rule:

```toml
preset = "google"

[indent]
size = 4

[wrapping]
max-line-length = 120
```

## Rules

The same `key` works in:
- `formatj.toml`
- CLI: `--set key=value`
- Gradle: `rule(...)` call
- Maven: `<rules>` element

1. Keys marked `†` are in the rules set and round-trip through configuration, but do not affect output yet.
2. `·` denotes a significant space.

### `file`

| Key                             | Values                             | Default    | Effect                                                      | Example                                                                                 |
|---------------------------------|------------------------------------|------------|-------------------------------------------------------------|-----------------------------------------------------------------------------------------|
| `file.line-ending`              | `preserve`, `lf`, `crlf`, `system` | `preserve` | Line terminator written to formatted output                 | `lf` writes `\n`, `crlf` writes `\r\n`, `preserve` keeps whatever the file already used |
| `file.final-newline`            | boolean                            | `true`     | End every file with a line terminator                       | `true`: last `}` is followed by a newline                                               |
| `file.trim-trailing-whitespace` | boolean                            | `true`     | Strip whitespace at the end of every line                   | `true`: `int x = 1;···` becomes `int x = 1;`                                            |
| `file.charset`                  | charset name                       | `UTF-8`    | Charset used to read and write source files                 | `ISO-8859-1` reads and writes legacy sources unchanged                                  |
| `file.tab-width`                | integer                            | `4`        | Columns a tab character occupies when measuring line length | `8`: a leading tab costs 8 of the 120 columns                                           |

### `indent`

| Key                         | Values  | Default | Effect                                               | Example                                                         |
|-----------------------------|---------|---------|------------------------------------------------------|-----------------------------------------------------------------|
| `indent.size`               | integer | `4`     | Columns of indentation per nesting level             | `2`: `class A {`<br>`··int x;`                                  |
| `indent.use-tabs`           | boolean | `false` | Indent with tab characters instead of spaces         | `true`: each level is one `\t`                                  |
| `indent.continuation`       | integer | `8`     | Columns added to a wrapped continuation line         | `int x = a`<br>`········+ b;`                                   |
| `indent.chained-call`       | integer | `8`     | Columns added to a wrapped method chain link         | `list.stream()`<br>`········.map(f)`                            |
| `indent.array-initializer`  | integer | `4`     | Columns added inside a wrapped array initializer     | `int[] a = {`<br>`····1, 2,`<br>`};`                            |
| `indent.ternary`            | integer | `8`     | Columns added to a wrapped ternary branch, when `alignment.ternary-branches` is `none` | `x = c`<br>`········? a`<br>`········: b;`                      |
| `indent.throws-clause`      | integer | `8`     | Columns added to a wrapped throws clause             | `void f()`<br>`········throws IOException {`                    |
| `indent.switch-case-labels` | boolean | `true`  | Indent case labels one level inside the switch block | `true`: `switch (x) {`<br>`····case 1:`                         |
| `indent.switch-case-body`   | boolean | `true`  | Indent a colon-label case body past its label        | `true`: `case 1:`<br>`····doThing();`                           |
| `indent.blank-lines`        | boolean | `false` | Emit indentation whitespace on otherwise blank lines | `false`: a blank line inside a method is empty, not four spaces |

### `wrapping`

`WrapPolicy` values are `preserve`, `wrap-if-long`, `chop-down-if-long`, `chop-down-always`, `never`.

- `wrap-if-long` breaks only where the line overflows.
- `chop-down-if-long` puts every element on its own line as soon as one break is needed.
- `chop-down-always` does so regardless of length.

| Key                                              | Values                                                                     | Default                  | Effect                                                            | Example                                                                          |
|--------------------------------------------------|----------------------------------------------------------------------------|--------------------------|-------------------------------------------------------------------|----------------------------------------------------------------------------------|
| `wrapping.max-line-length`                       | integer                                                                    | `120`                    | Maximum columns before a line is wrapped                          | `100`: lines are broken at 100 columns                                           |
| `wrapping.method-parameters`                     | `WrapPolicy`                                                               | `chop-down-if-long`      | Wrapping of a method declaration's parameter list                 | `chop-down-if-long`: `void f(`<br>`········int a,`<br>`········int b) {`         |
| `wrapping.method-arguments`                      | `WrapPolicy`                                                               | `chop-down-if-long`      | Wrapping of an argument list at a call site                       | `chop-down-if-long`: `f(`<br>`········a,`<br>`········b);`                       |
| `wrapping.chained-calls`                         | `preserve`, `break-all-if-multiline`, `break-when-too-long`, `never-break` | `break-all-if-multiline` | Wrapping of a chain of method calls                               | `break-all-if-multiline`: one break in the chain breaks every link               |
| `wrapping.chain-threshold`                       | integer                                                                    | `3`                      | Chain links required before the chain may be broken at all        | `3`: `a.b().c()` stays on one line however long it is                            |
| `wrapping.binary-operators`                      | `WrapPolicy`                                                               | `wrap-if-long`           | Wrapping of a binary expression                                   | `wrap-if-long`: `a + b`<br>`········+ c`                                         |
| `wrapping.operator-position`                     | `before-operator`, `after-operator`                                        | `before-operator`        | Which line a binary operator lands on when wrapped                | `before-operator`: `a`<br>`········+ b` — `after-operator`: `a +`<br>`········b` |
| `wrapping.ternary`                               | `WrapPolicy`                                                               | `wrap-if-long`           | Wrapping of a conditional expression                              | `wrap-if-long`: `c`<br>`········? a`<br>`········: b`                            |
| `wrapping.assignment`                            | `WrapPolicy`                                                               | `wrap-if-long`           | Wrapping of the right hand side of an assignment                  | `wrap-if-long`: `int x =`<br>`········compute();`                                |
| `wrapping.array-initializers`                    | `WrapPolicy`                                                               | `wrap-if-long`           | Wrapping of an array initializer                                  | `wrap-if-long`: `{ 1, 2,`<br>`····3 }`                                           |
| `wrapping.extends-implements`                    | `WrapPolicy`                                                               | `wrap-if-long`           | Wrapping of extends and implements clauses                        | `class A`<br>`········implements B, C {`                                         |
| `wrapping.throws-clause`                         | `WrapPolicy`                                                               | `wrap-if-long`           | Wrapping of a throws clause                                       | `void f()`<br>`········throws A, B {`                                            |
| `wrapping.type-parameters`                       | `WrapPolicy`                                                               | `wrap-if-long`           | Wrapping of a type parameter or type argument list                | `Map<`<br>`········String, Integer> m;`                                          |
| `wrapping.annotation-arguments`                  | `WrapPolicy`                                                               | `wrap-if-long`           | Wrapping of an annotation's element list                          | `@A(`<br>`········name = "x")`                                                   |
| `wrapping.enum-constants`                        | `WrapPolicy`                                                               | `chop-down-if-long`      | Wrapping of the constant list of an enum                          | `chop-down-if-long`: `A,`<br>`B,`<br>`C;`                                        |
| `wrapping.require-enum-constant-semicolon`       | boolean                                                                    | `false`                  | Always write a semicolon after the last no-argument enum constant | `true`: `enum E { A, B; }` — `false`: `enum E { A, B }`                          |
| `wrapping.for-statement`                         | `WrapPolicy`                                                               | `wrap-if-long`           | Wrapping of the header of a basic for statement                   | `for (int i = 0;`<br>`········i < n;`<br>`········i++) {`                        |
| `wrapping.try-resources`                         | `WrapPolicy`                                                               | `chop-down-if-long`      | Wrapping of a try-with-resources resource list                    | `try (`<br>`········A a = x();`<br>`········B b = y()) {`                        |
| `wrapping.keep-simple-methods-on-one-line`       | boolean                                                                    | `false`                  | Allow a whole short method to stay on one line                    | `true`: `int x() { return x; }`                                                  |
| `wrapping.keep-simple-lambdas-on-one-line`       | boolean                                                                    | `true`                   | Allow a short lambda body to stay on one line                     | `true`: `x -> { return x + 1; }`                                                 |
| `wrapping.keep-simple-classes-on-one-line`       | boolean                                                                    | `false`                  | Allow a short class body to stay on one line                      | `true`: `class A { int x; }`                                                     |

### `braces`

- `BracePlacement` values are `end-of-line`, `next-line`, `next-line-indented`.
- `BracePolicy` values are `always`, `never`, `when-multi-statement`, `preserve`.
  - The three body policies add and remove braces, so they run in the rewrite stage and default to `preserve`.
- `EmptyBodyStyle` values are `compact`, `spaced`, `expanded`

| Key                          | Values           | Default       | Effect                                             | Example                                                                                          |
|------------------------------|------------------|---------------|----------------------------------------------------|--------------------------------------------------------------------------------------------------|
| `braces.class-placement`     | `BracePlacement` | `end-of-line` | Opening brace position for a type declaration      | `end-of-line`: `class A {` — `next-line`: `class A`<br>`{`                                       |
| `braces.method-placement`    | `BracePlacement` | `end-of-line` | Opening brace position for a method or constructor | `next-line`: `void f()`<br>`{`                                                                   |
| `braces.control-placement`   | `BracePlacement` | `end-of-line` | Opening brace position for a control statement     | `next-line`: `if (x)`<br>`{`                                                                     |
| `braces.lambda-placement`    | `BracePlacement` | `end-of-line` | Opening brace position for a lambda block body     | `end-of-line`: `x -> {`                                                                          |
| `braces.if-else`             | `BracePolicy`    | `preserve`    | Braces around if and else bodies                   | `always`: `if (x) f();` becomes `if (x) {`<br>`····f();`<br>`}`                                  |
| `braces.for-loop`            | `BracePolicy`    | `preserve`    | Braces around for and enhanced-for bodies          | `never`: `for (T t : ts) {`<br>`····f(t);`<br>`}` becomes `for (T t : ts) f(t);`                 |
| `braces.while-loop`          | `BracePolicy`    | `preserve`    | Braces around while and do-while bodies            | `when-multi-statement`: a one-statement `while` loses its braces, a two-statement one keeps them |
| `braces.else-on-new-line`    | boolean          | `false`       | Put else on the line after the closing brace       | `false`: `} else {` — `true`: `}`<br>`else {`                                                    |
| `braces.catch-on-new-line`   | boolean          | `false`       | Put catch on the line after the closing brace      | `true`: `}`<br>`catch (E e) {`                                                                   |
| `braces.finally-on-new-line` | boolean          | `false`       | Put finally on the line after the closing brace    | `true`: `}`<br>`finally {`                                                                       |
| `braces.empty-class-body`    | `EmptyBodyStyle` | `spaced`      | Rendering of an empty type body                    | `compact`: `class A {}` — `spaced`: `class A { }` — `expanded`: `class A {`<br>`}`               |
| `braces.empty-method-body`   | `EmptyBodyStyle` | `spaced`      | Rendering of an empty method body                  | `spaced`: `void f() { }`                                                                         |
| `braces.empty-control-body`  | `EmptyBodyStyle` | `spaced`      | Rendering of an empty control statement body       | `compact`: `while (f()) {}`                                                                      |

### `spacing`

| Key                                             | Default | Effect                                                    | Example                                                                                                 |
|-------------------------------------------------|---------|-----------------------------------------------------------|---------------------------------------------------------------------------------------------------------|
| `spacing.before-method-declaration-parenthesis` | `false` | Space between a method name and its parameter list        | `void f ()` / `void f()`                                                                                |
| `spacing.before-method-call-parenthesis`        | `false` | Space between a called name and its argument list         | `f (x)` / `f(x)`                                                                                        |
| `spacing.before-if-parenthesis`                 | `true`  | Space between if and its condition                        | `if (x)` / `if(x)`                                                                                      |
| `spacing.before-for-parenthesis`                | `true`  | Space between for and its header                          | `for (;;)` / `for(;;)`                                                                                  |
| `spacing.before-while-parenthesis`              | `true`  | Space between while and its condition                     | `while (x)` / `while(x)`                                                                                |
| `spacing.before-switch-parenthesis`             | `true`  | Space between switch and its selector                     | `switch (x)` / `switch(x)`                                                                              |
| `spacing.before-catch-parenthesis`              | `true`  | Space between catch and its parameter                     | `catch (E e)` / `catch(E e)`                                                                            |
| `spacing.before-synchronized-parenthesis`       | `true`  | Space between synchronized and its monitor                | `synchronized (m)` / `synchronized(m)`                                                                  |
| `spacing.within-parentheses`                    | `false` | Spaces just inside parentheses                            | `f( x )` / `f(x)`                                                                                       |
| `spacing.within-brackets`                       | `false` | Spaces just inside array brackets                         | `a[ i ]` / `a[i]`                                                                                       |
| `spacing.within-array-initializer-braces`       | `false` | Spaces just inside array initializer braces               | `{ 1, 2 }` / `{1, 2}`                                                                                   |
| `spacing.within-angle-brackets`                 | `false` | Spaces just inside type argument angle brackets           | `List< T >` / `List<T>`                                                                                 |
| `spacing.around-assignment-operators`           | `true`  | Spaces around `=` and compound assignment operators       | `x = 1` / `x=1`                                                                                         |
| `spacing.around-binary-operators`               | `true`  | Spaces around binary operators                            | `a + b` / `a+b`                                                                                         |
| `spacing.around-unary-operators`                | `false` | Spaces between a unary operator and its operand           | `! x` / `!x`                                                                                            |
| `spacing.around-lambda-arrow`                   | `true`  | Spaces around the lambda arrow                            | `x -> x` / `x->x`                                                                                       |
| `spacing.around-ternary-operators`              | `true`  | Spaces around the `?` and `:` of a conditional expression | `c ? a : b` / `c?a:b`                                                                                   |
| `spacing.after-comma`                           | `true`  | Space after a comma                                       | `f(a, b)` / `f(a,b)`                                                                                    |
| `spacing.before-comma`                          | `false` | Space before a comma                                      | `f(a , b)` / `f(a, b)`                                                                                  |
| `spacing.after-semicolon-in-for`                | `true`  | Space after the semicolons of a for header                | `for (a; b; c)` / `for (a;b;c)`                                                                         |
| `spacing.before-semicolon`                      | `false` | Space before a statement-terminating semicolon            | `f() ;` / `f();`                                                                                        |
| `spacing.after-type-cast`                       | `true`  | Space between a cast and its operand                      | `(int) x` / `(int)x`                                                                                    |
| `spacing.before-colon-in-enhanced-for`          | `true`  | Space before the colon of an enhanced for                 | `for (T t : ts)` / `for (T t: ts)`                                                                      |
| `spacing.after-colon-in-enhanced-for`           | `true`  | Space after the colon of an enhanced for                  | `for (T t : ts)` / `for (T t :ts)`                                                                      |
| `spacing.before-colon-in-case-label`            | `false` | Space before the colon of a case label                    | `case 1 :` / `case 1:`                                                                                  |
| `spacing.around-case-arrow`                     | `true`  | Spaces around the arrow of a case label                   | `case 1 -> f();` / `case 1->f();`                                                                       |
| `spacing.before-annotation-parenthesis`         | `false` | Space between an annotation name and its elements         | `@A ("x")` / `@A("x")`                                                                                  |
| `spacing.before-array-brackets`                 | `false` | Space between a type and its array brackets               | `int [] a` / `int[] a`                                                                                  |
| `spacing.after-varargs-ellipsis`                | `true`  | Space between a varargs ellipsis and the parameter name   | `T... ts` / `T...ts`                                                                                    |

### `blank-lines`

| Key                                             | Default | Effect                                                     | Example                                                 |
|-------------------------------------------------|---------|------------------------------------------------------------|---------------------------------------------------------|
| `blank-lines.max-consecutive`                   | `1`     | Most consecutive blank lines kept anywhere in a body       | `1`: three blank lines collapse to one                  |
| `blank-lines.after-package`                     | `1`     | Blank lines after the package declaration                  | `1`: `package p;`<br>``<br>`import a.B;`                |
| `blank-lines.after-imports`                     | `1`     | Blank lines after the last import                          | `2`: two blank lines before the first type              |
| `blank-lines.before-class`                      | `1`     | Blank lines before a nested type declaration               | `1`: one blank line before `static class Inner {`       |
| `blank-lines.before-method`                     | `1`     | Blank lines before a method or constructor                 | `1`: one blank line between two methods                 |
| `blank-lines.before-field`                      | `0`     | Blank lines before a field declaration                     | `0`: consecutive fields stay packed                     |
| `blank-lines.after-class-opening-brace`         | `1`     | Blank lines just inside a type body                        | `1`: `class A {`<br>``<br>`····int x;`                  |
| `blank-lines.before-class-closing-brace`        | `1`     | Blank lines just before a type body closes                 | `1`: `····}`<br>``<br>`}`                               |
| `blank-lines.around-initializer-block`          | `1`     | Blank lines around an instance or static initializer       | `1`: `static { }` is separated from its neighbours      |
| `blank-lines.before-record-compact-constructor` | `1`     | Blank lines before a compact canonical constructor         | `1`: one blank line before `R {` inside `record R(...)` |
| `blank-lines.after-enum-constants`              | `0`     | Blank lines between the constants and the body of an enum  | `1`: blank line after `A, B;`                           |
| `blank-lines.before-first-enum-constant`        | `1`     | Blank lines between an enum's brace and its first constant | `1`: `enum E {`<br>``<br>`····A,`                       |
| `blank-lines.between-switch-cases`              | `0`     | Blank lines between the cases of a switch                  | `1`: a blank line separates each `case`                 |

### `alignment`

Alignment is applied to text that has already been laid out, turning a rule on never moves a line break.
Which means, a file wraps exactly where it would have wrapped with every alignment rule off

But an aligned line can end past `wrapping.max-line-length` as the column it is padded to is not known when honouring the margin.

A run is a set of lines that are consecutive, at the same indentation, and each carrying one of the
rule's constructs. A blank line, a comment line, a line that wrapped, or a change of nesting depth
ends a run and starts another.

- `AlignmentPolicy` values are `none`, `align-on-column`, `align-when-multiline`.
- The two aligning values mean the same thing: padding only shows on a line that follows a break, so
  there is no construct one of them reaches and the other does not.

| Key                                 | Default                | Effect                                            | Example                                            |
|-------------------------------------|------------------------|---------------------------------------------------|----------------------------------------------------|
| `alignment.consecutive-fields`      | `none`                 | Align the names of consecutive field declarations | `align-on-column`: `int····x;`<br>`String·name;`   |
| `alignment.consecutive-variables`   | `none`                 | Align the names of consecutive local declarations | as above, inside a method body                     |
| `alignment.consecutive-assignments` | `none`                 | Align the `=` of consecutive assignments          | `x···= 1;`<br>`name = "a";`                        |
| `alignment.method-chains`           | `none`                 | Align the dots of a wrapped method chain          | `people.stream()`<br>`······.filter(f)`            |
| `alignment.annotation-values`       | `none`                 | Align the values of an annotation's elements      | `@A(name···= "x",`<br>`···timeout = 1)`            |
| `alignment.switch-arrows`           | `none`                 | Align the arrows of a switch's case labels        | `case A··-> 1;`<br>`case BB -> 2;`                 |
| `alignment.ternary-branches`        | `align-when-multiline` | Align the branches of a wrapped conditional       | `x = cond`<br>`····?·a`<br>`····:·b;` under `cond` |
| `alignment.trailing-comments`       | `none`                 | Align comments trailing consecutive lines         | trailing `//` comments share a start column        |

An initializer is an assignment for the purposes of `alignment.consecutive-assignments`, so a run of
declarations lines up its `=` as well as, with `alignment.consecutive-fields`, its names. Only the
first declarator of a declaration is aligned: a second name on the same line has no column of its own.

### `annotations`

- `AnnotationPlacement` values are `preserve`, `new-line`, `same-line`, `same-line-when-short`.

| Key                                 | Values                | Default     | Effect                                                            | Example                                 |
|-------------------------------------|-----------------------|-------------|-------------------------------------------------------------------|-----------------------------------------|
| `annotations.declaration-placement` | `AnnotationPlacement` | `preserve`  | Placement of an annotation on a type, method or field declaration | `new-line`: `@Override`<br>`void f() {` |
| `annotations.parameter-placement`   | `AnnotationPlacement` | `same-line` | Placement of an annotation on a parameter or local variable       | `same-line`: `void f(@Nullable T t)`    |
| `annotations.single-marker-inline`  | boolean               | `false`     | Keep a lone marker annotation on the line of its declaration      | `true`: `@Override void f() {`          |

### `imports`

`imports.order = preserve` skips this ruleset entirely and preserves original authoring of imports.

| Key                                          | Values                                | Default                  | Effect                                                                                                                                           | Example                                                                                |
|----------------------------------------------|---------------------------------------|--------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------|----------------------------------------------------------------------------------------|
| `imports.groups`                             | list of prefixes                      | `["java", "javax", "*"]` | Package prefixes forming import groups, in order; `*` is the catch-all                                                                           | `["java", "*", "org"]` puts `org.*` imports last                                       |
| `imports.order`                              | `preserve`, `ascending`, `descending` | `preserve`               | Sort order applied within a group; `preserve` leaves the whole run alone, which also switches off grouping, static placement and module ordering | `ascending`: `import a.A;` before `import b.B;`                                        |
| `imports.static-placement`                   | `first`, `last`, `inline`             | `last`                   | Where static imports sit relative to ordinary ones                                                                                               | `first`: the `import static` block precedes every ordinary import                      |
| `imports.blank-line-between-groups`          | boolean                               | `true`                   | Separate import groups with a blank line                                                                                                         | `true`: `import java.util.List;`<br>``<br>`import org.x.Y;`                            |
| `imports.remove-unused`                      | boolean                               | `false`                  | Delete imports the file does not reference                                                                                                       | `true`: an import named nowhere in the file, comments and Javadoc included, is dropped |
| `imports.module-imports-first`               | boolean                               | `true`                   | Place module imports before every other import                                                                                                   | `true`: `import module java.base;` heads the block                                     |

### `comments`

| Key                                         | Values                              | Default         | Effect                                                 | Example                                                                       |
|---------------------------------------------|-------------------------------------|-----------------|--------------------------------------------------------|-------------------------------------------------------------------------------|
| `comments.reflow` **†**                     | `preserve`, `reflow-to-line-length` | `preserve`      | Whether line and block comment prose may be re-wrapped | `reflow-to-line-length` would refill paragraphs to `wrapping.max-line-length` |
| `comments.block-comment-star-alignment`     | boolean                             | `true`          | Align the leading stars of a block comment             | `true`: `/*`<br>`·* text`<br>`·*/`                                            |
| `comments.trailing-comment-min-spaces`      | integer                             | `1`             | Spaces between code and a comment trailing it          | `2`: `int x = 1;··// note`                                                    |
| `comments.trailing-comment-column` **†**    | integer                             | `0`             | Column trailing comments are padded to; `0` disables   | `40`: every trailing comment starts at column 40                              |
| `comments.keep-first-column-comments` **†** | boolean                             | `false`         | Leave a comment starting in column one where it is     | `true`: a `//` in column 1 inside a method body is not indented               |
| `comments.indent-with-code` **†**           | boolean                             | `true`          | Indent comments to match the code that follows them    | `true`: a comment above an indented statement gets that statement's indent    |
| `comments.honour-formatter-off`             | boolean                             | `true`          | Respect the off and on markers                         | `true`: everything between the markers is reproduced byte for byte            |
| `comments.off-marker`                       | string                              | `"formatj:off"` | Marker that suspends formatting until the on-marker    | `"@formatter:off"` accepts the IntelliJ and Eclipse spelling                  |
| `comments.on-marker`                        | string                              | `"formatj:on"`  | Marker that resumes formatting                         | `"@formatter:on"`                                                             |

The markers work at whole members, whole statements and whole top-level declarations. A marker in the
middle of an expression has no boundary in the tree to latch onto and is ignored.

### `javadoc` **†**

No Javadoc rule affects output yet.

Re-wrapping prose needs a preservation check that the token check cannot provide and since comments are not significant tokens, they wait on a check to be implemented.

- `JavadocTagOrder` values are `preserve` and `canonical`.

| Key                               | Values            | Default    | Effect                                                     | Example                                                       |
|-----------------------------------|-------------------|------------|------------------------------------------------------------|---------------------------------------------------------------|
| `javadoc.wrap`                    | boolean           | `false`    | Wrap Javadoc prose to the configured line length           | `true` would refill description paragraphs                    |
| `javadoc.tag-order`               | `JavadocTagOrder` | `preserve` | Ordering of Javadoc block tags                             | `canonical`: `@param`, then `@return`, then `@throws`         |
| `javadoc.blank-line-before-tags`  | boolean           | `true`     | Blank line between the description and the first block tag | `true`: `·* text`<br>`·*`<br>`·* @param a x`                  |
| `javadoc.align-tag-descriptions`  | boolean           | `false`    | Align the descriptions following block tags                | `true`: `@param a··x`<br>`@param bb y`                        |
| `javadoc.add-paragraph-tags`      | boolean           | `false`    | Insert `<p>` on blank description lines                    | `true`: a blank description line becomes `·* <p>`             |
| `javadoc.keep-single-line`        | boolean           | `true`     | Leave a one-line Javadoc comment on one line               | `true`: `/** Text. */` stays as written                       |
| `javadoc.tag-continuation-indent` | integer           | `8`        | Columns a wrapped block tag description is indented        | `8`: the second line of a long `@param` is indented 8 columns |

### `switch`

`switch.arrow-case-braces` and `switch.yield-style` divide the same territory between them, because
the answer depends on whether the switch produces a value. A statement switch's arrow body is a
statement, so its braces are braces and nothing else: `arrow-case-braces` governs those. An expression
switch's arrow body is a value, so braces round it bring a `yield` with them — one decision rather
than two — and `yield-style` governs those. Neither rule is consulted about the other's cases, which
is what keeps one token from having two rules with an opinion about it.

- `never` and `when-multi-statement` coincide on an arrow case. An arrow body may only be an
  expression, a `throw` or a block, so a block holding more than one statement has no unbraced form to
  go to under either value.
- `yield-style = always-block` leaves a `throw` body alone: a `throw` produces no value, so there is
  no expression for a `yield` to be written round.

| Key                                       | Values                                                 | Default                | Effect                                             | Example                                                                     |
|-------------------------------------------|--------------------------------------------------------|------------------------|----------------------------------------------------|-----------------------------------------------------------------------------|
| `switch.case-style` **†**                 | `preserve`, `arrow`, `colon`                           | `preserve`             | Arrow or colon case labels                         | `arrow` would turn `case 1: f(); break;` into `case 1 -> f();`              |
| `switch.arrow-case-braces`                | `BracePolicy`                                          | `preserve`             | Braces around the body of an arrow case            | `never`: `case 1 -> { f(); }` becomes `case 1 -> f();`; statement switches only |
| `switch.yield-style`                      | `preserve`, `expression-when-possible`, `always-block` | `preserve`             | How the value of an arrow case body is written     | `expression-when-possible`: `case 1 -> { yield x; }` becomes `case 1 -> x;` |
| `switch.multi-label-wrapping`             | `WrapPolicy`                                           | `wrap-if-long`         | Wrapping of a case label listing several constants | `case A, B,`<br>`········C -> f();`                                         |
| `switch.null-default-on-one-line`         | boolean                                                | `true`                 | Keep `case null, default` on a single line         | `true`: `case null, default -> f();`                                        |
| `switch.guard-on-same-line`               | boolean                                                | `true`                 | Keep a `when` guard on the line of its pattern     | `true`: `case T t when t.ok() -> f();`                                      |
| `switch.arrow-body-on-new-line-when-long` | boolean                                                | `true`                 | Move a long arrow case body to the next line       | `true`: `case A ->`<br>`········someVeryLongCall();`                        |

### `records`

`records.with-style` is layout rather than a rewrite: a with-block on one line and the same block
spread over several are the same tokens, so it changes no code. The one-line form is only ever
offered — a block too long for its line breaks whatever the rule asks for.

| Key                                      | Values                                          | Default             | Effect                                            | Example                                                 |
|------------------------------------------|-------------------------------------------------|---------------------|---------------------------------------------------|---------------------------------------------------------|
| `records.component-wrapping`             | `WrapPolicy`                                    | `chop-down-if-long` | Wrapping of a record header's components          | `record R(`<br>`········int a,`<br>`········int b) {`   |
| `records.single-line-empty-body`         | boolean                                         | `false`             | Render an empty record body as `{}`               | `true`: `record R(int a) {}`                            |
| `records.compact-constructor-blank-line` | boolean                                         | `false`             | Blank line inside a compact canonical constructor | `true`: a blank line opens the compact constructor body |
| `records.with-style`                     | `preserve`, `always-block`, `inline-when-short` | `inline-when-short` | Layout of a derived record creation `with` block  | `inline-when-short`: `r with { a = 1; }`                |
| `records.space-before-with-block`        | boolean                                         | `true`              | Space between the `with` keyword and its block    | `r with {` / `r with{`                                  |

### `patterns`

| Key                                         | Values       | Default        | Effect                                       | Example                                                         |
|---------------------------------------------|--------------|----------------|----------------------------------------------|-----------------------------------------------------------------|
| `patterns.deconstruction-wrapping`          | `WrapPolicy` | `wrap-if-long` | Wrapping of a record deconstruction pattern  | `case R(`<br>`········int a,`<br>`········int b) -> f();`       |
| `patterns.keep-simple-pattern-inline`       | boolean      | `true`         | Keep a short pattern on the line of its test | `true`: `if (x instanceof T t) {`                               |
| `patterns.nested-indent`                    | integer      | `8`            | Columns a wrapped nested pattern is indented | `8`: an inner deconstruction is indented 8 past its outer one   |

### `sealed`

A permits clause is a set written as a list, so `sealed.permits-order` may rearrange it freely. It may
not do anything else: a permitted subclass that went missing would stop the file compiling, and one
that appeared would permit something the author never wrote, so the whole run is replaced as a single
declared edit whose tokens are a permutation of the ones that were there.

| Key                          | Values                                | Default        | Effect                                      | Example                                               |
|------------------------------|---------------------------------------|----------------|---------------------------------------------|-------------------------------------------------------|
| `sealed.permits-wrapping`    | `WrapPolicy`                          | `wrap-if-long` | Wrapping of a permits clause                | `sealed interface I`<br>`········permits A, B {`      |
| `sealed.permits-order`       | `preserve`, `ascending`, `descending` | `preserve`     | Sort order of the types in a permits clause | `ascending`: `permits A, B, C`                        |
| `sealed.permits-on-new-line` | boolean                               | `false`        | Start the permits clause on its own line    | `true`: `sealed interface I`<br>`········permits A {` |

### `lambdas`

`lambdas.parameter-style = omit-when-possible` drops the parentheses only round the one shape the
language lets go bare — exactly one parameter, written as a name with no type, no `final` and no
annotation — so `()`, `(a, b)`, `(int x)` and `(var x)` keep theirs.

`lambdas.body-braces` runs the opposite way round from `braces.*`: taking the braces off is the safe
direction. A block body says which lambda shape the target type wanted, so `{ return e; }` and
`{ e(); }` each collapse to the expression body that compiles. Going the other way, `x -> e` could
need either `{ return e; }` or `{ e; }`, and which one is a question about the functional interface
being implemented rather than about the text. `always` is therefore declined for an expression body
rather than guessed at.

| Key                                     | Values                                                  | Default    | Effect                                            | Example                                           |
|-----------------------------------------|---------------------------------------------------------|------------|---------------------------------------------------|---------------------------------------------------|
| `lambdas.parameter-style`               | `preserve`, `always-parenthesise`, `omit-when-possible` | `preserve` | Parentheses around a single untyped parameter     | `omit-when-possible`: `(x) -> x` becomes `x -> x` |
| `lambdas.body-braces`                   | `BracePolicy`                                           | `preserve` | Braces around a lambda body                       | `never`: `x -> { return x; }` becomes `x -> x`; `always` is declined |
| `lambdas.keep-single-expression-inline` | boolean                                                 | `true`     | Keep a single-expression body on the arrow's line | `true`: `x -> x + 1`                              |

### `text-blocks` **†**

Changing a text block's indentation or its closing delimiter changes the string the program produces.

Every rule here is a rewrite despite looking like a normal layout pass. None is applied yet.

| Key                                         | Values                                     | Default    | Effect                                       | Example                                                             |
|---------------------------------------------|--------------------------------------------|------------|----------------------------------------------|---------------------------------------------------------------------|
| `text-blocks.indent-policy`                 | `preserve`, `reindent-to-block`, `minimal` | `preserve` | How incidental indentation is handled        | `minimal` would strip incidental indentation to the shallowest line |
| `text-blocks.closing-delimiter-on-own-line` | boolean                                    | `true`     | Keep the closing delimiter on its own line   | `true`: `····text`<br>`····"""`                                     |
| `text-blocks.escape-trailing-spaces`        | boolean                                    | `true`     | Escape significant trailing spaces with `\s` | `true`: `text··` becomes `text·\s`                                  |

### `preservation`

These are the rules that keep what the author wrote.

- `keep-line-break-after-open-paren`
- `keep-simple-blocks-inline`
- `never-join-lines`
- `wrapping.keep-simple-{methods,lambdas,classes}-on-one-line`
- `patterns.keep-simple-pattern-inline`
- `switch.null-default-on-one-line`
- `wrapping.throws-clause = preserve`

- A construct is on one line when no line terminator falls between its first token and its last character.
- A comment the author kept inline is part of that line but a comment that ended one is not
  - Therefore, a body carrying a `//` comment was never on one line and is laid out as any other.
  
- `never-join-lines` applies when there is a wrapping decision

| Key                                                   | Values  | Default | Effect                                                   | Example                                                      |
|-------------------------------------------------------|---------|---------|----------------------------------------------------------|--------------------------------------------------------------|
| `preservation.keep-author-blank-lines`                | boolean | `true`  | Keep blank lines the author placed inside bodies         | `true`: a blank line splitting two statement groups survives |
| `preservation.max-preserved-blank-lines`              | integer | `1`     | Most consecutive author blank lines kept                 | `1`: two author blank lines collapse to one                  |
| `preservation.keep-line-break-after-open-paren`       | boolean | `false` | Keep a break the author put after an opening parenthesis | `true`: `f(`<br>`········a, b)` stays broken                 |
| `preservation.keep-simple-blocks-inline`              | boolean | `true`  | Keep a block the author wrote on one line on one line    | `true`: `if (x) { return; }` is left alone                   |
| `preservation.keep-array-initializer-layout`          | boolean | `true`  | Keep the row layout of a hand-arranged array initializer | `true`: a matrix written as one row per line stays that way  |
| `preservation.respect-existing-chain-breaks`          | boolean | `true`  | Keep breaks the author placed in a method chain          | `true`: a chain the author broke stays broken                |
| `preservation.never-join-lines`                       | boolean | `false` | Never merge two lines the author kept apart              | `true` would make every author line break load-bearing       |

## Building

The Maven plugin descriptor in `maven-plugin/src/main/resources/META-INF/maven/plugin.xml` is hand-written.
Generating it needs either Maven itself or a Gradle plugin that no longer runs on Gradle 9.
`MavenPluginDescriptorTest` checks it against the mojo annotations and the project version on every build.

Versions come from [Cleanroom Versioning](https://github.com/CleanroomMC/Versioning): `version` and `versioning.stage` in `gradle.properties`, plus `git describe`.

Local builds get a `+local.<distance>` suffix; a release is the numeric version and requires a matching git tag with no `v` prefix.

Publishing is the `Publish` workflow: Gradle plugin to the Plugin Portal, `formatj` and `formatj-maven-plugin` to [maven.cleanroommc.com](https://maven.cleanroommc.com), CLI zip/tar to a GitHub Release.
