# FormatJ

A Java code formatter that aims to be

- Super configurable
- Buildable from CI
- Same everywhere it runs

One engine and one `formatj.toml` serve every entrypoint.

## Contents

- [Usage](#usage)
  - [CLI](#cli)
  - [Gradle Plugin](#gradle-plugin)
  - [Maven Plugin](#maven-plugin)
  - [IntelliJ Plugin](#intellij-plugin-experimental)
  - [Library](#library)
- [Configuration](#configuration)
- [Rules](#rules)
- [Design](#design)
- [Output stability](#output-stability)
- [Runtime](#runtime)
- [Building](#building)
- [Origins](#origins)

## Usage

### CLI

Install it one of two ways:

- Download `formatj-<version>.zip` (or `.tar`) from [GitHub Releases](https://github.com/Rongmario/FormatJ/releases), unpack it and put `bin` on `PATH`.
- From a clone of this repo, run `./gradlew :app:installDist`. The launcher lands in `app/build/install/formatj/bin`.

```
formatj --check src/main/java          # exit 1 if anything would change (default)
formatj --write src/main/java          # rewrite in-place
formatj --diff src/main/java           # unified diff of what would change
formatj --dump-config                  # every rule with its effective value, as TOML

cat Foo.java | formatj --stdin --stdin-name Foo.java
```

| Flag                      | Meaning                                                                                       |
|---------------------------|-----------------------------------------------------------------------------------------------|
| `--style FILE`            | Use this style file instead of the discovered `formatj.toml`                                  |
| `--preset formatj\|google` | Start from a preset instead of the discovered `formatj.toml`                                  |
| `--set key=value`         | Override one rule. Repeatable                                                                 |
| `--include`, `--exclude`  | Globs matched against paths relative to the working directory                                 |
| `--lines START:END`       | Format only those lines (1-based, inclusive, repeatable). Needs `--stdin` or exactly one file |
| `--stdin`                 | Read the source from stdin and write the result to stdout, unless a mode flag is given        |
| `--stdin-name`            | Name the piped file. It also picks which `formatj.toml` applies, so give it the real path     |

- Hidden directories, `build/`, `target/` and `out/` are skipped when walking a directory. A file named explicitly is always formatted.
- Exit code `0` means success, `1` means files would change (`--check`, `--diff`) and `2` means an error. Matching no Java files is an error.

For [pre-commit](https://pre-commit.com), put `formatj` on `PATH` and reference this repository's hooks. `formatj` runs `--write` and `formatj-check` runs `--check`.

```yaml
repos:
  - repo: https://github.com/Rongmario/FormatJ
    rev: <tag>
    hooks:
      - id: formatj
```

pre-commit passes the changed files by name, so the directory skipping above does not apply to them.

### Gradle Plugin

Published to the [Gradle Plugin Portal](https://plugins.gradle.org/plugin/zone.rong.formatj).

```kotlin
import zone.rong.formatj.api.Preset

plugins {
    java
    id("zone.rong.formatj") version "1.0.1"
}

formatJ {
    preset = Preset.GOOGLE              // start from the Google preset
    styleFile = file("formatj.toml")    // or from a style file
    rule("indent.size", 4)              // override one rule
    sourceSets("main", "test")          // default: every source set
    include("com/example/**")           // relative to the source directories, default: every file
    exclude("**/generated/**")
}
```

- `./gradlew formatJavaApply` rewrites sources in place.
- `./gradlew formatJavaCheck` fails if anything would change. `check` depends on it unless `enforceOnCheck = false`.
- The check task is cacheable and incremental, and rules are task inputs. Apply always runs, because it mutates the source files.
- `languageLevel` defaults to what `compileJava` targets, which is `options.release` when set and `sourceCompatibility` otherwise. Set `languageLevel = LanguageLevel.JAVA_8` to override it.

### Maven Plugin

Published to [maven.cleanroommc.com](https://maven.cleanroommc.com).

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
  <version>1.0.1</version>
  <configuration>
    <styleFile>${project.basedir}/formatj.toml</styleFile>
    <preset>formatj</preset>
    <rules>
      <indent.size>4</indent.size>
    </rules>
  </configuration>
  <executions>
    <execution>
      <goals>
        <goal>format</goal>
        <goal>check</goal>
      </goals>
    </execution>
  </executions>
</plugin>
```

- `mvn formatj:format` rewrites in place. It is bound to `process-sources` when the execution above is present.
- `mvn formatj:check` fails if anything would change. It is bound to `verify`.
- Without `<executions>`, the goals only run when invoked by name.
- `-Dformatj.skip` skips the plugin, and `-Dformatj.styleFile=...` points at a style file.
- With no `<styleFile>` or `<preset>`, the nearest `formatj.toml` above the project directory is used.
- `<includes>` and `<excludes>` are globs relative to each source root. Source roots under `target/` are skipped.
- `<languageLevel>` defaults to the compiler plugin's `<release>`, then its `<source>`, each read from the plugin configuration or the `maven.compiler.*` property. `-Dformatj.languageLevel=8` overrides it.

### IntelliJ Plugin (Experimental)

1. Build the plugin zip with `./gradlew :intellij-plugin:buildPlugin`. It lands in `intellij-plugin/build/distributions/`.
2. Install it via **Settings > Plugins > ⚙ > Install Plugin from Disk...**.

Once installed:

- **Reformat Code** (`Ctrl+Alt+L`) and **Optimize Imports** on Java files run FormatJ instead of the built-in Java formatter.
- **Format-on-save** uses it too, because it runs **Reformat Code**.
- Style comes from the nearest `formatj.toml` above the file, the same walk the CLI does.
- **Settings > Tools > FormatJ** disables it per project.
- Enter and paste still use IntelliJ's indent. FormatJ does not run on every keystroke.

Smoke it locally with `./gradlew :intellij-plugin:runIde`.

### Library

`zone.rong.formatj:formatj:1.0.1` from [maven.cleanroommc.com](https://maven.cleanroommc.com).

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

A formatter is immutable and thread-safe, so one instance can serve the whole project.

### Language Level

The language level is the Java release the sources compile for, from 8 to 25. FormatJ never writes syntax that release cannot compile, so one style file works across projects on different Java versions. A rule that would write newer syntax leaves the code as written and reports nothing.

| Rule                                        | Writes                                          | From release           |
|---------------------------------------------|-------------------------------------------------|------------------------|
| `switch.case-style = "arrow"`               | `case 1 -> f();`                                | 14, or 12 with preview |
| `text-blocks.escape-trailing-spaces = true` | `\s`                                            | 15, or 14 with preview |
| `modifiers.remove-redundant = true`         | a `@SafeVarargs` private method without `final` | 9                      |

| Entry point | Where the level comes from                                                        |
|-------------|-----------------------------------------------------------------------------------|
| Library     | `.languageLevel(...)`, default `LATEST`                                           |
| CLI         | `--language-level N` and `--preview`, default latest                              |
| Gradle      | `languageLevel`, default the release `compileJava` targets                        |
| Maven       | `<languageLevel>`, default the compiler plugin's `release`, then its `source`     |
| IntelliJ    | the language level of the file's module                                           |

Each default falls back to the latest release when the project does not name one. A style file cannot set the level, because the level belongs to the project and a style file is shared.

## Configuration

- The CLI, Gradle, Maven and IntelliJ all find `formatj.toml` by walking up from each file.
- The nearest file wins. Parent files are not merged.
- Gradle and Maven use it only when neither a style file nor a preset is configured.
- A `preset` key chooses the starting point, and every other key overrides one rule.

```toml
preset = "google"

[indent]
size = 4

[wrapping]
max-line-length = 120

[files]
include = ["src/**"]
exclude = ["**/generated/**"]
```

`[files]` globs are relative to the directory of the `formatj.toml`. They apply in every entrypoint, on top of the CLI flags and plugin includes and excludes.

## Rules

The same `key` works in `formatj.toml`, in `--set key=value` on the CLI, in a Gradle `rule(...)` call and in the Maven `<rules>` element. `formatj --dump-config` prints every rule with its effective value.

Rule groups:
[`file`](#file),
[`indent`](#indent),
[`wrapping`](#wrapping),
[`braces`](#braces),
[`spacing`](#spacing),
[`blank-lines`](#blank-lines),
[`alignment`](#alignment),
[`annotations`](#annotations),
[`imports`](#imports),
[`comments`](#comments),
[`javadoc`](#javadoc),
[`module`](#module),
[`modifiers`](#modifiers),
[`switch`](#switch),
[`records`](#records),
[`patterns`](#patterns),
[`sealed`](#sealed),
[`lambdas`](#lambdas),
[`text-blocks`](#text-blocks),
[`literals`](#literals),
[`semicolons`](#semicolons),
[`preservation`](#preservation),
[`arrays`](#arrays),
[`members`](#members)

In the examples, `·` marks a significant space and `/` separates alternatives.

Several rules share a value type:

| Type                        | Values                                                                                                |
|-----------------------------|-------------------------------------------------------------------------------------------------------|
| `WrapPolicy`                | `preserve`, `wrap-if-long`, `chop-down-if-long`, `chop-down-always`, `never`                          |
| `ChainPolicy`               | `preserve`, `break-all-if-multiline`, `break-all-when-too-long`, `break-when-too-long`, `never-break` |
| `BracePlacement`            | `end-of-line`, `next-line`, `next-line-indented`                                                      |
| `BracePolicy`               | `always`, `never`, `when-multi-statement`, `preserve`                                                 |
| `EmptyBodyStyle`            | `compact`, `spaced`, `expanded`                                                                       |
| `AlignmentPolicy`           | `none`, `align-on-column`, `align-when-multiline`                                                     |
| `AnnotationPlacement`       | `preserve`, `new-line`, `same-line`, `same-line-when-short`                                           |
| `JavadocTagOrder`           | `preserve`, `canonical`                                                                               |
| `JavadocClosingTagForm`     | `preserve`, `slash-first`                                                                             |
| `JavadocOpeningTagPosition` | `preserve`, `new-line`, `same-line`                                                                   |

### `file`

| Key                             | Values                             | Default | Effect                                                      | Example                                                                                 |
|---------------------------------|------------------------------------|---------|-------------------------------------------------------------|-----------------------------------------------------------------------------------------|
| `file.line-ending`              | `preserve`, `lf`, `crlf`, `system` | `lf`    | Line terminator written to formatted output                 | `lf` writes `\n`, `crlf` writes `\r\n`, `preserve` keeps whatever the file already used |
| `file.final-newline`            | boolean                            | `true`  | End every file with a line terminator                       | `true`: last `}` is followed by a newline                                               |
| `file.trim-trailing-whitespace` | boolean                            | `true`  | Strip whitespace at the end of every line                   | `true`: `int x = 1;···` becomes `int x = 1;`                                            |
| `file.charset`                  | charset name                       | `UTF-8` | Charset used to read and write source files                 | `ISO-8859-1` reads and writes legacy sources unchanged                                  |
| `file.tab-width`                | integer                            | `4`     | Columns a tab character occupies when measuring line length | `8`: a leading tab costs 8 of the 120 columns                                           |

### `indent`

| Key                         | Values  | Default | Effect                                                                                 | Example                                                         |
|-----------------------------|---------|---------|----------------------------------------------------------------------------------------|-----------------------------------------------------------------|
| `indent.size`               | integer | `4`     | Columns of indentation per nesting level                                               | `2`: `class A {`<br>`··int x;`                                  |
| `indent.use-tabs`           | boolean | `false` | Indent with tab characters instead of spaces                                           | `true`: each level is one `\t`                                  |
| `indent.continuation`       | integer | `4`     | Columns added to a wrapped continuation line                                           | `int x = a`<br>`····+ b;`                                       |
| `indent.chained-call`       | integer | `4`     | Columns added to a wrapped method chain link                                           | `list.stream()`<br>`····.map(f)`                                |
| `indent.array-initializer`  | integer | `4`     | Columns added inside a wrapped array initializer                                       | `int[] a = {`<br>`····1, 2,`<br>`};`                            |
| `indent.ternary`            | integer | `4`     | Columns added to a wrapped ternary branch, when `alignment.ternary-branches` is `none` | `x = c`<br>`····? a`<br>`····: b;`                              |
| `indent.throws-clause`      | integer | `4`     | Columns added to a wrapped throws clause                                               | `void f()`<br>`····throws IOException {`                        |
| `indent.switch-case-labels` | boolean | `true`  | Indent case labels one level inside the switch block                                   | `true`: `switch (x) {`<br>`····case 1:`                         |
| `indent.switch-case-body`   | boolean | `true`  | Indent a colon-label case body past its label                                          | `true`: `case 1:`<br>`····doThing();`                           |
| `indent.blank-lines`        | boolean | `false` | Emit indentation whitespace on otherwise blank lines                                   | `false`: a blank line inside a method is empty, not four spaces |

### `wrapping`

| Key                                           | Values                              | Default                   | Effect                                                                                   | Example                                                                                                         |
|-----------------------------------------------|-------------------------------------|---------------------------|------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------|
| `wrapping.max-line-length`                    | integer                             | `120`                     | Maximum columns before a line is wrapped                                                 | `100`: lines are broken at 100 columns                                                                          |
| `wrapping.method-parameters`                  | `WrapPolicy`                        | `chop-down-if-long`       | Wrapping of a method declaration's parameter list                                        | `chop-down-if-long`: `void f(`<br>`········int a,`<br>`········int b) {`                                        |
| `wrapping.method-arguments`                   | `WrapPolicy`                        | `chop-down-if-long`       | Wrapping of an argument list at a call site                                              | `chop-down-if-long`: `f(`<br>`········a,`<br>`········b);`                                                      |
| `wrapping.closing-delimiter`                  | `own-line`, `attached`              | `own-line`                | Whether a wrapped list's closing parenthesis takes its own line                          | `own-line`: `f(`<br>`····a,`<br>`····b`<br>`);`                                                                 |
| `wrapping.chained-calls`                      | `ChainPolicy`                       | `break-all-when-too-long` | Wrapping of a chain of method calls                                                      | `break-all-if-multiline`: one break in the chain breaks every link                                              |
| `wrapping.chain-threshold`                    | integer                             | `2`                       | Chain links required before the chain may be broken at all                               | `3`: `a.b().c()` stays on one line however long it is                                                           |
| `wrapping.binary-operators`                   | `WrapPolicy`                        | `wrap-if-long`            | Wrapping of a binary expression                                                          | `wrap-if-long`: `a + b`<br>`········+ c`                                                                        |
| `wrapping.operator-position`                  | `before-operator`, `after-operator` | `after-operator`          | Which line a binary operator lands on when wrapped                                       | `before-operator`: `a`<br>`········+ b` / `after-operator`: `a +`<br>`········b`                                |
| `wrapping.method-reference`                   | `WrapPolicy`                        | `never`                   | Wrapping of a method reference at `::`                                                   | `chop-down-always`: `Type`<br>`········::method`                                                                |
| `wrapping.method-reference-operator-position` | `before-operator`, `after-operator` | `before-operator`         | Which line `::` lands on when a method reference wraps                                   | `after-operator`: `Type ::`<br>`········method`                                                                 |
| `wrapping.instanceof`                         | `WrapPolicy`                        | `preserve`                | Wrapping of an `instanceof` test                                                         | `chop-down-always`: `value instanceof`<br>`········Type pattern`                                                |
| `wrapping.instanceof-operator-position`       | `before-operator`, `after-operator` | `after-operator`          | Which line `instanceof` lands on when its test wraps                                     | `before-operator`: `value`<br>`········instanceof Type pattern`                                                 |
| `wrapping.multicatch`                         | `WrapPolicy`                        | `never`                   | Wrapping of multi-catch alternatives                                                     | `chop-down-always`: `First`<br>`········&#124; Second e`                                                        |
| `wrapping.multicatch-separator-position`      | `before-operator`, `after-operator` | `before-operator`         | Which line `&#124;` lands on when multi-catch alternatives wrap                          | `after-operator`: `First &#124;`<br>`········Second e`                                                          |
| `wrapping.intersection-types`                 | `WrapPolicy`                        | `never`                   | Wrapping of intersection type bounds and casts                                           | `chop-down-always`: `A`<br>`········& B`                                                                        |
| `wrapping.intersection-separator-position`    | `before-operator`, `after-operator` | `before-operator`         | Which line `&` lands on when intersection types wrap                                     | `after-operator`: `A &`<br>`········B`                                                                          |
| `wrapping.ternary`                            | `WrapPolicy`                        | `wrap-if-long`            | Wrapping of a conditional expression                                                     | `wrap-if-long`: `c`<br>`········? a`<br>`········: b`                                                           |
| `wrapping.assignment`                         | `WrapPolicy`                        | `never`                   | Wrapping of the right hand side of an assignment                                         | `wrap-if-long`: `int x =`<br>`········compute();`                                                               |
| `wrapping.assignment-break`                   | `after-operator`, `inside-value`    | `inside-value`            | Where a long assignment breaks first                                                     | `inside-value`: `x = call(`<br>`········a,`<br>`········b);` / `after-operator`: `x =`<br>`········call(a, b);` |
| `wrapping.hug-sole-argument`                  | boolean                             | `true`                    | Keep a lone call or creation argument on the line of the parenthesis and break inside it | `true`: `add(new Entry(`<br>`········key,`<br>`········value));`                                                |
| `wrapping.break-after-open-paren`             | boolean                             | `true`                    | Start a wrapped parenthesised list on the line after its opening parenthesis             | `false`: `call(first, second,`<br>`········third);`                                                             |
| `wrapping.array-initializers`                 | `WrapPolicy`                        | `chop-down-if-long`       | Wrapping of an array initializer                                                         | `wrap-if-long`: `{ 1, 2,`<br>`····3 }`                                                                          |
| `wrapping.extends-implements`                 | `WrapPolicy`                        | `never`                   | Wrapping of extends and implements clauses                                               | `class A`<br>`········implements B, C {`                                                                        |
| `wrapping.throws-clause`                      | `WrapPolicy`                        | `wrap-if-long`            | Wrapping of a throws clause                                                              | `void f()`<br>`········throws A, B {`                                                                           |
| `wrapping.type-parameters`                    | `WrapPolicy`                        | `never`                   | Wrapping of a type parameter or type argument list                                       | `Map<`<br>`········String, Integer> m;`                                                                         |
| `wrapping.annotation-arguments`               | `WrapPolicy`                        | `wrap-if-long`            | Wrapping of an annotation's element list                                                 | `@A(`<br>`········name = "x")`                                                                                  |
| `wrapping.enum-constants`                     | `WrapPolicy`                        | `chop-down-always`        | Wrapping of the constant list of an enum                                                 | `chop-down-if-long`: `A,`<br>`B,`<br>`C;`                                                                       |
| `wrapping.require-enum-constant-semicolon`    | boolean                             | `false`                   | Always write a semicolon after the last no-argument enum constant                        | `true`: `enum E { A, B; }` / `false`: `enum E { A, B }`                                                         |
| `wrapping.for-statement`                      | `WrapPolicy`                        | `chop-down-if-long`       | Wrapping of the header of a basic for statement                                          | `for (int i = 0;`<br>`········i < n;`<br>`········i++) {`                                                       |
| `wrapping.try-resources`                      | `WrapPolicy`                        | `never`                   | Wrapping of a try-with-resources resource list                                           | `try (`<br>`········A a = x();`<br>`········B b = y()) {`                                                       |
| `wrapping.keep-simple-methods-on-one-line`    | boolean                             | `false`                   | Allow a whole short method to stay on one line                                           | `true`: `int x() { return x; }`                                                                                 |
| `wrapping.keep-simple-lambdas-on-one-line`    | boolean                             | `false`                   | Allow a short lambda body to stay on one line                                            | `true`: `x -> { return x + 1; }`                                                                                |
| `wrapping.keep-simple-classes-on-one-line`    | boolean                             | `false`                   | Allow a short class body to stay on one line                                             | `true`: `class A { int x; }`                                                                                    |

- `wrap-if-long` breaks only where the line overflows. `chop-down-if-long` puts every element on its own line as soon as one break is needed. `chop-down-always` does so regardless of length.
- `wrapping.closing-delimiter` covers every parenthesised list, meaning arguments, parameters, record components, annotation elements, deconstruction patterns and try resources. It applies only once a list has wrapped.
  - `own-line` gives the closing parenthesis its own line, at the indentation of the line that opened the list.
  - `attached` keeps it against the last element, as below.
  - Array initializer braces keep their own layout.

  ```java
  this.callIsLong(
          arg1,
          arg2);
  ```

- A list hugs a last argument that brings its own lines, such as a block lambda, an anonymous class, an array initializer or a switch expression.
  - The list is measured by the line it prints, so `register("name", Jar.class, task -> {` keeps its arguments together and indents the body from the statement.
  - A hugging list has not wrapped, so its `});` stays as it is.
  - The list still follows its wrapping policy once that first line does not fit.
  - An argument like that in any other position does not hug, because the arguments after it would be stranded against a closing brace.
- `wrapping.chained-calls` takes a `ChainPolicy`:
  - `preserve` reproduces the author's breaks before dots exactly, even when the chain is too long.
  - `break-all-if-multiline` breaks every link as soon as the chain spans more than one line. A block lambda argument is enough.
  - `break-all-when-too-long` breaks every link once the chain's own line does not fit. Measurement stops at the first forced line break, so a lambda body in the last link does not count as overflow.
  - `break-when-too-long` breaks only as many links as it takes to fit, so two links can share a line.
  - `never-break` leaves the dots alone and lets the overflow land inside an argument list.
- `wrapping.instanceof = preserve` defers to `patterns.keep-simple-pattern-inline`. Every other value overrides that pattern rule. `wrapping.instanceof-operator-position` only chooses the side of a break and does not force one.

### `braces`

| Key                          | Values           | Default       | Effect                                             | Example                                                                                          |
|------------------------------|------------------|---------------|----------------------------------------------------|--------------------------------------------------------------------------------------------------|
| `braces.class-placement`     | `BracePlacement` | `end-of-line` | Opening brace position for a type declaration      | `end-of-line`: `class A {` / `next-line`: `class A`<br>`{`                                       |
| `braces.method-placement`    | `BracePlacement` | `end-of-line` | Opening brace position for a method or constructor | `next-line`: `void f()`<br>`{`                                                                   |
| `braces.control-placement`   | `BracePlacement` | `end-of-line` | Opening brace position for a control statement     | `next-line`: `if (x)`<br>`{`                                                                     |
| `braces.lambda-placement`    | `BracePlacement` | `end-of-line` | Opening brace position for a lambda block body     | `end-of-line`: `x -> {`                                                                          |
| `braces.if-else`             | `BracePolicy`    | `always`      | Braces around if and else bodies                   | `always`: `if (x) f();` becomes `if (x) {`<br>`····f();`<br>`}`                                  |
| `braces.for-loop`            | `BracePolicy`    | `always`      | Braces around for and enhanced-for bodies          | `never`: `for (T t : ts) {`<br>`····f(t);`<br>`}` becomes `for (T t : ts) f(t);`                 |
| `braces.while-loop`          | `BracePolicy`    | `always`      | Braces around while and do-while bodies            | `when-multi-statement`: a one-statement `while` loses its braces, a two-statement one keeps them |
| `braces.else-on-new-line`    | boolean          | `false`       | Put else on the line after the closing brace       | `false`: `} else {` / `true`: `}`<br>`else {`                                                    |
| `braces.catch-on-new-line`   | boolean          | `false`       | Put catch on the line after the closing brace      | `true`: `}`<br>`catch (E e) {`                                                                   |
| `braces.finally-on-new-line` | boolean          | `false`       | Put finally on the line after the closing brace    | `true`: `}`<br>`finally {`                                                                       |
| `braces.empty-class-body`    | `EmptyBodyStyle` | `spaced`      | Rendering of an empty type body                    | `compact`: `class A {}` / `spaced`: `class A { }` / `expanded`: `class A {`<br>`}`               |
| `braces.empty-method-body`   | `EmptyBodyStyle` | `spaced`      | Rendering of an empty method body                  | `spaced`: `void f() { }`                                                                         |
| `braces.empty-control-body`  | `EmptyBodyStyle` | `expanded`    | Rendering of an empty control statement body       | `spaced`: `while (f()) { }`                                                                      |

`braces.if-else`, `braces.for-loop` and `braces.while-loop` add and remove braces, so they run in the rewrite stage.

### `spacing`

Every rule is a boolean. Each example shows `true` / `false`.

| Key                                             | Default | Effect                                                    | Example                                       |
|-------------------------------------------------|---------|-----------------------------------------------------------|-----------------------------------------------|
| `spacing.before-method-declaration-parenthesis` | `false` | Space between a method name and its parameter list        | `void f ()` / `void f()`                      |
| `spacing.before-method-call-parenthesis`        | `false` | Space between a called name and its argument list         | `f (x)` / `f(x)`                              |
| `spacing.before-if-parenthesis`                 | `true`  | Space between if and its condition                        | `if (x)` / `if(x)`                            |
| `spacing.before-for-parenthesis`                | `true`  | Space between for and its header                          | `for (;;)` / `for(;;)`                        |
| `spacing.before-while-parenthesis`              | `true`  | Space between while and its condition                     | `while (x)` / `while(x)`                      |
| `spacing.before-switch-parenthesis`             | `true`  | Space between switch and its selector                     | `switch (x)` / `switch(x)`                    |
| `spacing.before-catch-parenthesis`              | `true`  | Space between catch and its parameter                     | `catch (E e)` / `catch(E e)`                  |
| `spacing.before-synchronized-parenthesis`       | `true`  | Space between synchronized and its monitor                | `synchronized (m)` / `synchronized(m)`        |
| `spacing.within-parentheses`                    | `false` | Spaces just inside parentheses                            | `f( x )` / `f(x)`                             |
| `spacing.within-brackets`                       | `false` | Spaces just inside array brackets                         | `a[ i ]` / `a[i]`                             |
| `spacing.within-array-initializer-braces`       | `true`  | Spaces just inside array initializer braces               | `{ 1, 2 }` / `{1, 2}`                         |
| `spacing.within-angle-brackets`                 | `false` | Spaces just inside type argument angle brackets           | `List< T >` / `List<T>`                       |
| `spacing.around-assignment-operators`           | `true`  | Spaces around `=` and compound assignment operators       | `x = 1` / `x=1`                               |
| `spacing.around-binary-operators`               | `true`  | Spaces around binary operators                            | `a + b` / `a+b`                               |
| `spacing.around-unary-operators`                | `false` | Spaces between a unary operator and its operand           | `! x` / `!x`                                  |
| `spacing.around-lambda-arrow`                   | `true`  | Spaces around the lambda arrow                            | `x -> x` / `x->x`                             |
| `spacing.around-ternary-operators`              | `true`  | Spaces around the `?` and `:` of a conditional expression | `c ? a : b` / `c?a:b`                         |
| `spacing.around-method-reference-operator`      | `false` | Spaces around the `::` of a method reference              | `Type :: method` / `Type::method`             |
| `spacing.around-multicatch-separator`           | `true`  | Spaces around `&#124;` between multi-catch alternatives   | `catch (A &#124; B e)` / `catch (A&#124;B e)` |
| `spacing.around-intersection-separator`         | `true`  | Spaces around the `&` between intersection types          | `T extends A & B` / `T extends A&B`           |
| `spacing.after-comma`                           | `true`  | Space after a comma                                       | `f(a, b)` / `f(a,b)`                          |
| `spacing.before-comma`                          | `false` | Space before a comma                                      | `f(a , b)` / `f(a, b)`                        |
| `spacing.after-semicolon-in-for`                | `true`  | Space after the semicolons of a for header                | `for (a; b; c)` / `for (a;b;c)`               |
| `spacing.before-semicolon`                      | `false` | Space before a statement-terminating semicolon            | `f() ;` / `f();`                              |
| `spacing.after-type-cast`                       | `true`  | Space between a cast and its operand                      | `(int) x` / `(int)x`                          |
| `spacing.before-colon-in-enhanced-for`          | `true`  | Space before the colon of an enhanced for                 | `for (T t : ts)` / `for (T t: ts)`            |
| `spacing.after-colon-in-enhanced-for`           | `true`  | Space after the colon of an enhanced for                  | `for (T t : ts)` / `for (T t :ts)`            |
| `spacing.before-colon-in-case-label`            | `false` | Space before the colon of a case label                    | `case 1 :` / `case 1:`                        |
| `spacing.around-case-arrow`                     | `true`  | Spaces around the arrow of a case label                   | `case 1 -> f();` / `case 1->f();`             |
| `spacing.before-annotation-parenthesis`         | `false` | Space between an annotation name and its elements         | `@A ("x")` / `@A("x")`                        |
| `spacing.before-array-brackets`                 | `false` | Space between a type and its array brackets               | `int [] a` / `int[] a`                        |
| `spacing.after-varargs-ellipsis`                | `true`  | Space between a varargs ellipsis and the parameter name   | `T... ts` / `T...ts`                          |

### `blank-lines`

Every rule is an integer, except the boolean `strip-at-brace-edges`.

| Key                                             | Default | Effect                                                                                                   | Example                                                                       |
|-------------------------------------------------|---------|----------------------------------------------------------------------------------------------------------|-------------------------------------------------------------------------------|
| `blank-lines.max-consecutive`                   | `1`     | Most consecutive blank lines kept anywhere in a body                                                     | `1`: three blank lines collapse to one                                        |
| `blank-lines.after-package`                     | `1`     | Blank lines after the package declaration                                                                | `1`: `package p;`<br>``<br>`import a.B;`                                      |
| `blank-lines.after-imports`                     | `1`     | Blank lines after the last import                                                                        | `2`: two blank lines before the first type                                    |
| `blank-lines.before-class`                      | `1`     | Blank lines before a nested type declaration                                                             | `1`: one blank line before `static class Inner {`                             |
| `blank-lines.before-method`                     | `1`     | Blank lines before a method or constructor                                                               | `1`: one blank line between two methods                                       |
| `blank-lines.before-field`                      | `0`     | Blank lines before a field declaration                                                                   | `0`: consecutive fields stay packed                                           |
| `blank-lines.after-class-opening-brace`         | `1`     | Blank lines just inside a type body                                                                      | `1`: `class A {`<br>``<br>`····int x;`                                        |
| `blank-lines.before-class-closing-brace`        | `1`     | Blank lines just before a type body closes                                                               | `1`: `····}`<br>``<br>`}`                                                     |
| `blank-lines.strip-at-brace-edges`              | `false` | Drop blank lines the author left just inside the braces of a body                                        | `true`: `class A {`<br>``<br>`····int x;` becomes `class A {`<br>`····int x;` |
| `blank-lines.around-initializer-block`          | `1`     | Blank lines around an instance or static initializer                                                     | `1`: `static { }` is separated from its neighbours                            |
| `blank-lines.before-record-compact-constructor` | `1`     | Blank lines before a compact canonical constructor                                                       | `1`: one blank line before `R {` inside `record R(...)`                       |
| `blank-lines.after-enum-constants`              | `0`     | Blank lines between the constants and the body of an enum                                                | `1`: blank line after `A, B;`                                                 |
| `blank-lines.before-first-enum-constant`        | `1`     | Blank lines between an enum's brace and its first constant                                               | `1`: `enum E {`<br>``<br>`····A,`                                             |
| `blank-lines.between-member-groups`             | `1`     | Blank lines between neighbouring members of different kinds; access level does not split a run of fields | `1`: a blank line between a `final` and a non-final field                     |
| `blank-lines.between-switch-cases`              | `0`     | Blank lines between the cases of a switch                                                                | `1`: a blank line separates each `case`                                       |

### `alignment`

Every rule takes an `AlignmentPolicy`.

| Key                                 | Default | Effect                                            | Example                                            |
|-------------------------------------|---------|---------------------------------------------------|----------------------------------------------------|
| `alignment.consecutive-fields`      | `none`  | Align the names of consecutive field declarations | `align-on-column`: `int····x;`<br>`String·name;`   |
| `alignment.consecutive-variables`   | `none`  | Align the names of consecutive local declarations | as above, inside a method body                     |
| `alignment.consecutive-assignments` | `none`  | Align the `=` of consecutive assignments          | `x···= 1;`<br>`name = "a";`                        |
| `alignment.method-chains`           | `none`  | Align the dots of a wrapped method chain          | `people.stream()`<br>`······.filter(f)`            |
| `alignment.annotation-values`       | `none`  | Align the values of an annotation's elements      | `@A(name···= "x",`<br>`···timeout = 1)`            |
| `alignment.switch-arrows`           | `none`  | Align the arrows of a switch's case labels        | `case A··-> 1;`<br>`case BB -> 2;`                 |
| `alignment.ternary-branches`        | `none`  | Align the branches of a wrapped conditional       | `x = cond`<br>`····?·a`<br>`····:·b;` under `cond` |
| `alignment.trailing-comments`       | `none`  | Align comments trailing consecutive lines         | trailing `//` comments share a start column        |

- Alignment pads text that is already laid out, so it never moves a line break. A file wraps exactly where it would with every alignment rule off.
- An aligned line can end past `wrapping.max-line-length`, because the padding column is not known when the margin is applied.
- A run is a set of consecutive lines at the same indentation that each carry the rule's construct. A blank line, a comment line, a wrapped line or a change of nesting depth ends the run.
- `align-on-column` and `align-when-multiline` behave the same.
- An initializer counts as an assignment for `alignment.consecutive-assignments`, so a run of declarations lines up its `=` too.
- Only the first declarator of a declaration is aligned.

### `annotations`

| Key                                 | Values                | Default     | Effect                                                                  | Example                                 |
|-------------------------------------|-----------------------|-------------|-------------------------------------------------------------------------|-----------------------------------------|
| `annotations.declaration-placement` | `AnnotationPlacement` | `new-line`  | Placement of an annotation on a type, method or constructor declaration | `new-line`: `@Override`<br>`void f() {` |
| `annotations.field-placement`       | `AnnotationPlacement` | `same-line` | Placement of an annotation on a field declaration                       | `same-line`: `@Nullable T t;`           |
| `annotations.parameter-placement`   | `AnnotationPlacement` | `same-line` | Placement of an annotation on a parameter or local variable             | `same-line`: `void f(@Nullable T t)`    |
| `annotations.single-marker-inline`  | boolean               | `false`     | Keep a lone marker annotation on the line of its declaration            | `true`: `@Override void f() {`          |

### `imports`

| Key                                 | Values                                | Default                  | Effect                                                                                                                                                                       | Example                                                                                                  |
|-------------------------------------|---------------------------------------|--------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------|
| `imports.groups`                    | list of prefixes or prefix lists      | `["java", "javax", "*"]` | Import groups, in order; an entry is one prefix or a list of prefixes that share a group, and `*` is the catch-all. The longest matching prefix wins wherever it is declared | `[["net.minecraft", "net.minecraftforge"], "*"]` puts the game in one group and everything else after it |
| `imports.order`                     | `preserve`, `ascending`, `descending` | `ascending`              | Sort order applied within a group; `preserve` leaves the whole run alone, which also switches off grouping, static placement and module ordering                             | `ascending`: `import a.A;` before `import b.B;`                                                          |
| `imports.static-placement`          | `first`, `last`, `inline`             | `last`                   | Where static imports sit relative to ordinary ones                                                                                                                           | `first`: the `import static` block precedes every ordinary import                                        |
| `imports.blank-line-between-groups` | boolean                               | `true`                   | Separate import groups with a blank line                                                                                                                                     | `true`: `import java.util.List;`<br>``<br>`import org.x.Y;`                                              |
| `imports.remove-unused`             | boolean                               | `false`                  | Delete imports the file does not reference                                                                                                                                   | `true`: an import named nowhere in the file, comments and Javadoc included, is dropped                   |
| `imports.module-imports-first`      | boolean                               | `true`                   | Place module imports before every other import                                                                                                                               | `true`: `import module java.base;` heads the block                                                       |

- `imports.order = preserve` leaves the imports exactly as written and switches off every other rule in this group.
- A `groups` array may run over several lines and carry comments:

```toml
[imports]
order = "ascending"
groups = [
    "com.cleanroommc",
    "*",
    ["net.minecraft", "net.minecraftforge", "com.mojang"],
    ["javax", "java"],
]
```

### `comments`

| Key                                     | Values                              | Default            | Effect                                                 | Example                                                                    |
|-----------------------------------------|-------------------------------------|--------------------|--------------------------------------------------------|----------------------------------------------------------------------------|
| `comments.reflow`                       | `preserve`, `reflow-to-line-length` | `preserve`         | Whether line and block comment prose may be re-wrapped | `reflow-to-line-length` refills paragraphs to `wrapping.max-line-length`   |
| `comments.block-comment-star-alignment` | boolean                             | `true`             | Align the leading stars of a block comment             | `true`: `/*`<br>`·* text`<br>`·*/`                                         |
| `comments.trailing-comment-min-spaces`  | integer                             | `1`                | Spaces between code and a comment trailing it          | `2`: `int x = 1;··// note`                                                 |
| `comments.trailing-comment-column`      | integer                             | `0`                | Column trailing comments are padded to; `0` disables   | `40`: every trailing comment starts at column 40                           |
| `comments.keep-first-column-comments`   | boolean                             | `false`            | Leave a comment starting in column one where it is     | `true`: a `//` in column 1 inside a method body is not indented            |
| `comments.indent-with-code`             | boolean                             | `true`             | Indent comments to match the code that follows them    | `true`: a comment above an indented statement gets that statement's indent |
| `comments.honour-formatter-off`         | boolean                             | `true`             | Respect the off and on markers                         | `true`: everything between the markers is reproduced byte for byte         |
| `comments.off-marker`                   | string                              | `"@formatter:off"` | Marker that suspends formatting until the on-marker    | `"formatj:off"`: `// formatj:off` suspends formatting                      |
| `comments.on-marker`                    | string                              | `"@formatter:on"`  | Marker that resumes formatting                         | `"formatj:on"`: `// formatj:on` resumes it                                 |

- `comments.trailing-comment-column` pads each trailing comment to that column after layout, and never moves a line break.
  - A line whose code already passes the column keeps its ordinary spacing.
  - `alignment.trailing-comments` can still line a run up past the column.
- `comments.indent-with-code = false` keeps the indent the author wrote. `comments.keep-first-column-comments` is narrower and only pins comments that already start in column one.
- The off and on markers work at whole members, whole statements and whole top-level declarations. A marker in the middle of an expression is ignored.
- `comments.reflow` refills a run of `//` lines as one paragraph. It skips:
  - a comment holding a `{@code}`, `<pre>` or `@snippet` region
  - a run of `//` lines with no space after the slashes, which is what commented-out code looks like
  - anything carrying an off or on marker
- `comments.reflow` moves a `//` comment that trails code past the margin onto its own line above the statement or member, and refills it there.
  - The comment has to trail the statement's first line or its last token. One beside an argument in the middle of a list stays.
  - It stays when another comment sits between the start of the statement and it, or when a `//` comment is already above the statement.
  - It stays when moving it would let a rewriting rule remove the token it was attached to.
  - A file holding the off marker keeps all its trailing comments in place.

### `javadoc`

| Key                               | Values                      | Default    | Effect                                                                        | Example                                                        |
|-----------------------------------|-----------------------------|------------|-------------------------------------------------------------------------------|----------------------------------------------------------------|
| `javadoc.wrap`                    | boolean                     | `false`    | Wrap ordinary documentation prose to the configured line length               | `true`: safe description paragraphs are refilled to the margin |
| `javadoc.tag-order`               | `JavadocTagOrder`           | `preserve` | Ordering of Javadoc block tags                                                | `canonical`: `@param`, then `@return`, then `@throws`          |
| `javadoc.blank-line-before-tags`  | boolean                     | `true`     | Blank line between the description and the first block tag                    | `true`: `·* text`<br>`·*`<br>`·* @param a x`                   |
| `javadoc.align-tag-descriptions`  | boolean                     | `false`    | Align the descriptions following block tags                                   | `true`: `@param a··x`<br>`@param bb y`                         |
| `javadoc.add-paragraph-tags`      | boolean                     | `false`    | Write `<p>` on blank traditional Javadoc description lines                    | `true`: a blank description line becomes `·* <p>`              |
| `javadoc.keep-single-line`        | boolean                     | `false`    | Leave a one-line traditional Javadoc comment on one line                      | `true`: `/** Text. */` stays as written                        |
| `javadoc.tag-continuation-indent` | integer                     | `0`        | Columns a wrapped block tag description is indented                           | `8`: the second line of a long `@param` is indented 8 columns  |
| `javadoc.closing-tag-form`        | `JavadocClosingTagForm`     | `preserve` | Written form of a traditional Javadoc paragraph closer                        | `slash-first`: `<p/>` becomes `</p>`; `<p>` untouched          |
| `javadoc.opening-tag-position`    | `JavadocOpeningTagPosition` | `preserve` | Placement of a traditional Javadoc paragraph marker relative to its paragraph | `new-line`: `·* <p>` own line / `same-line`: `·* <p> text`     |

- Every rule is checked afterwards against the words that went in. The words and their order must match, and every `{@code}`, `<pre>` and `@snippet` region must be untouched.
- A comment that no rule applies to is reproduced character for character.
- Traditional `/**` comments and Markdown `///` runs share `wrap`, `tag-order`, `blank-line-before-tags`, `align-tag-descriptions` and `tag-continuation-indent`. The other four rules apply to traditional comments only.
- Markdown wrapping refills plain paragraphs only.
  - Code spans, code blocks, headings, lists, tables, block quotes, links, reference definitions, HTML blocks and hard line breaks keep their source lines.
  - Unclosed delimiters and other ambiguous Markdown leave the whole run unchanged.
- `javadoc.wrap` skips a paragraph holding a code sample, block markup or a table row. A leading paragraph marker is not markup, so `·* <p> text` still refills.
- `javadoc.tag-order = canonical` is `@author`, `@version`, `@param`, `@return`, `@throws`, `@exception`, `@see`, `@since`, `@serial`, `@serialField`, `@serialData`, `@deprecated`.
  - Other tags go to the end in the order the author had them.
  - The sort is stable, so two `@param` tags never swap.
  - Whole tag blocks move, and text cannot migrate between tags or comments.
- `javadoc.align-tag-descriptions` aligns each kind of tag with its own kind. A long `@throws` does not push every `@param` description across the line.
- `javadoc.closing-tag-form = slash-first` rewrites standalone closers only. `<p/>` and `</P>` become `</p>`.
  - Openers (`<p>`) are untouched.
  - Markers inside `<pre>`, `{@code}` and `@snippet` regions are content and are never rewritten.
  - Balancing validation comes later.
- `javadoc.opening-tag-position` moves standalone `<p>`, `</p>` and `<p/>` markers in the description.
  - `new-line` puts each marker on its own `·* <p>` line.
  - `same-line` joins it with the paragraph's first words (`·* <p> text`).
  - Markers glued to other text (`foo<p>bar`) and forms with spaces inside the brackets (`<p />`) are left alone.

### `module`

| Key                                            | Values                        | Default   | Effect                                                           | Example                                                     |
|------------------------------------------------|-------------------------------|-----------|------------------------------------------------------------------|-------------------------------------------------------------|
| `module.brace-placement`                       | `inherit` or `BracePlacement` | `inherit` | Opening brace position for a module declaration                  | `next-line`: `module m`<br>`{`                              |
| `module.empty-body`                            | `inherit` or `EmptyBodyStyle` | `inherit` | Rendering of an empty module body                                | `compact`: `module m {}`                                    |
| `module.blank-lines-after-opening-brace`       | `inherit` or integer          | `inherit` | Blank lines after a module's opening brace                       | `0`: the first directive follows on the next line           |
| `module.blank-lines-before-closing-brace`      | `inherit` or integer          | `inherit` | Blank lines before a module's closing brace                      | `0`: the brace follows the final directive on the next line |
| `module.blank-lines-between-directive-groups`  | integer                       | `0`       | Blank lines between adjacent groups of different directive types | `1`: separate `requires`, `exports`, and `uses` groups      |
| `module.exports-opens-target-list-wrapping`    | `WrapPolicy`                  | `never`   | Wrapping of target module lists in `exports` and `opens`         | `chop-down-always`: one target after each comma             |
| `module.provides-implementation-list-wrapping` | `WrapPolicy`                  | `never`   | Wrapping of implementation lists in `provides`                   | `wrap-if-long`: wrap implementations at the margin          |

The four `inherit` rules follow the matching class brace, empty-body and blank-line rules until a module-specific value is set.

### `modifiers`

| Key                          | Values                  | Default     | Effect                                  | Example                                                                     |
|------------------------------|-------------------------|-------------|-----------------------------------------|-----------------------------------------------------------------------------|
| `modifiers.order`            | `preserve`, `canonical` | `canonical` | Ordering of declaration modifiers       | `canonical`: `static public` becomes `public static`                        |
| `modifiers.remove-redundant` | boolean                 | `true`      | Remove modifiers the JLS makes implicit | `true`: `public abstract void run();` in an interface becomes `void run();` |

- `canonical` uses the per-declaration orders documented in `ModifierRules`.
  - Annotations keep their source order and their positions among the modifiers.
  - Modifier lists with comments, duplicates or malformed modifiers stay unchanged.
- `modifiers.remove-redundant` removes only what the JLS implies. A modifier that carries a comment stays.
- Below Java 9, `final` stays on a private method that carries `@SafeVarargs`, because the annotation needs it there.
  - `public` and bodiless `abstract` on interface methods
  - `public static final` on interface fields
  - `public static` on interface member types
  - `static` on nested enums, records and interfaces
  - `final` on records and on private methods
  - `private` on enum constructors
  - `final` on try-with-resources variables

### `switch`

| Key                                       | Values                                                 | Default                    | Effect                                             | Example                                                                         |
|-------------------------------------------|--------------------------------------------------------|----------------------------|----------------------------------------------------|---------------------------------------------------------------------------------|
| `switch.case-style`                       | `preserve`, `arrow`, `colon`                           | `arrow`                    | Arrow or colon case labels                         | `arrow`: `case 1: f(); break;` becomes `case 1 -> f();`                         |
| `switch.arrow-case-braces`                | `BracePolicy`                                          | `when-multi-statement`     | Braces around the body of an arrow case            | `never`: `case 1 -> { f(); }` becomes `case 1 -> f();`; statement switches only |
| `switch.yield-style`                      | `preserve`, `expression-when-possible`, `always-block` | `expression-when-possible` | How the value of an arrow case body is written     | `expression-when-possible`: `case 1 -> { yield x; }` becomes `case 1 -> x;`     |
| `switch.multi-label-wrapping`             | `WrapPolicy`                                           | `wrap-if-long`             | Wrapping of a case label listing several constants | `case A, B,`<br>`········C -> f();`                                             |
| `switch.null-default-on-one-line`         | boolean                                                | `true`                     | Keep `case null, default` on a single line         | `true`: `case null, default -> f();`                                            |
| `switch.guard-on-same-line`               | boolean                                                | `true`                     | Keep a `when` guard on the line of its pattern     | `true`: `case T t when t.ok() -> f();`                                          |
| `switch.arrow-body-on-new-line-when-long` | boolean                                                | `true`                     | Move a long arrow case body to the next line       | `true`: `case A ->`<br>`········someVeryLongCall();`                            |

- `switch.arrow-case-braces` governs statement switches, where an arrow body is a statement and its braces are only braces.
- `switch.yield-style` governs expression switches, where braces round an arrow body bring a `yield` with them.
- Neither rule touches the other's cases.
- `never` and `when-multi-statement` coincide on an arrow case, because a block holding more than one statement has no unbraced form.
- `yield-style = always-block` leaves a `throw` body alone, because a `throw` produces no value.
- `switch.case-style = "arrow"` does nothing below Java 14, where arrow cases do not compile. See [Language Level](#language-level).
- `switch.case-style` reads the whole switch first and converts it only when every condition below holds. A switch is converted wholly or left alone, because mixing the two forms does not compile.
  - Every group ends where it cannot fall through. That is an unlabelled `break`, which the rule removes, or a `return`, `throw`, `yield` or `continue`, which it keeps. The last group needs no terminator.
  - No `break` belonging to the switch is buried inside a group. A `break` inside a nested loop or switch binds to that and does not count.
  - No group declares a local variable or local type at its own level, because the groups of a colon switch share one scope and arrow cases do not.
  - A `default`, and a label carrying a `when` guard, are never merged with the empty cases above them.
  - `colon` converts only expression and `throw` bodies, because a block body would need a `break` after it.

### `records`

| Key                                      | Values                                          | Default             | Effect                                            | Example                                                 |
|------------------------------------------|-------------------------------------------------|---------------------|---------------------------------------------------|---------------------------------------------------------|
| `records.component-wrapping`             | `WrapPolicy`                                    | `chop-down-if-long` | Wrapping of a record header's components          | `record R(`<br>`········int a,`<br>`········int b) {`   |
| `records.single-line-empty-body`         | boolean                                         | `false`             | Render an empty record body as `{}`               | `true`: `record R(int a) {}`                            |
| `records.compact-constructor-blank-line` | boolean                                         | `false`             | Blank line inside a compact canonical constructor | `true`: a blank line opens the compact constructor body |
| `records.with-style`                     | `preserve`, `always-block`, `inline-when-short` | `inline-when-short` | Layout of a derived record creation `with` block  | `inline-when-short`: `r with { a = 1; }`                |
| `records.space-before-with-block`        | boolean                                         | `true`              | Space between the `with` keyword and its block    | `r with {` / `r with{`                                  |

`records.with-style` is layout and changes no tokens. The one-line form is only offered, so a block too long for its line still breaks.

### `patterns`

| Key                                   | Values       | Default        | Effect                                       | Example                                                       |
|---------------------------------------|--------------|----------------|----------------------------------------------|---------------------------------------------------------------|
| `patterns.deconstruction-wrapping`    | `WrapPolicy` | `wrap-if-long` | Wrapping of a record deconstruction pattern  | `case R(`<br>`········int a,`<br>`········int b) -> f();`     |
| `patterns.keep-simple-pattern-inline` | boolean      | `true`         | Keep a short pattern on the line of its test | `true`: `if (x instanceof T t) {`                             |
| `patterns.nested-indent`              | integer      | `4`            | Columns a wrapped nested pattern is indented | `4`: an inner deconstruction is indented 4 past its outer one |

### `sealed`

| Key                          | Values                                | Default        | Effect                                      | Example                                               |
|------------------------------|---------------------------------------|----------------|---------------------------------------------|-------------------------------------------------------|
| `sealed.permits-wrapping`    | `WrapPolicy`                          | `wrap-if-long` | Wrapping of a permits clause                | `sealed interface I`<br>`········permits A, B {`      |
| `sealed.permits-order`       | `preserve`, `ascending`, `descending` | `preserve`     | Sort order of the types in a permits clause | `ascending`: `permits A, B, C`                        |
| `sealed.permits-on-new-line` | boolean                               | `false`        | Start the permits clause on its own line    | `true`: `sealed interface I`<br>`········permits A {` |

`sealed.permits-order` replaces the clause as one declared edit whose tokens are a permutation of the originals, so no permitted type can go missing or appear.

### `lambdas`

| Key                                     | Values                                                  | Default                | Effect                                            | Example                                                              |
|-----------------------------------------|---------------------------------------------------------|------------------------|---------------------------------------------------|----------------------------------------------------------------------|
| `lambdas.parameter-style`               | `preserve`, `always-parenthesise`, `omit-when-possible` | `omit-when-possible`   | Parentheses around a single untyped parameter     | `omit-when-possible`: `(x) -> x` becomes `x -> x`                    |
| `lambdas.body-braces`                   | `BracePolicy`                                           | `when-multi-statement` | Braces around a lambda body                       | `never`: `x -> { return x; }` becomes `x -> x`; `always` is declined |
| `lambdas.keep-single-expression-inline` | boolean                                                 | `true`                 | Keep a single-expression body on the arrow's line | `true`: `x -> x + 1`                                                 |

- `lambdas.parameter-style = omit-when-possible` drops the parentheses only round exactly one parameter written as a bare name, with no type, no `final` and no annotation. `()`, `(a, b)`, `(int x)` and `(var x)` keep theirs.
- `lambdas.body-braces` only takes braces off. `{ return e; }` and `{ e(); }` each collapse to the expression body.
  - Where `e` is a call, an assignment or an increment, the expression body fits both a value-returning and a `void` interface.
  - A call site overloaded on both, such as `pick(Function)` beside `pick(Consumer)`, then resolves differently or turns ambiguous. Set `preserve` for code that leans on such overloads.
  - `always` is declined for an expression body, because `x -> e` could need `{ return e; }` or `{ e; }` and the text does not say which.

### `text-blocks`

| Key                                         | Values                                     | Default    | Effect                                                 | Example                                                                   |
|---------------------------------------------|--------------------------------------------|------------|--------------------------------------------------------|---------------------------------------------------------------------------|
| `text-blocks.indent-policy`                 | `preserve`, `reindent-to-block`, `minimal` | `preserve` | How incidental indentation is handled                  | `minimal` strips incidental indentation to the opening delimiter's column |
| `text-blocks.closing-delimiter-on-own-line` | boolean                                    | `false`    | Put the closing delimiter on its own line              | `true`: the value gains the trailing newline that implies                 |
| `text-blocks.escape-trailing-spaces`        | boolean                                    | `false`    | Make trailing spaces significant by escaping with `\s` | `true`: `text··` becomes `text·\s`                                        |

- `indent-policy` is layout. The language throws away the indentation every line of a block shares, so moving all lines together does not change the string. Verification compares text blocks by the string they denote.
- `closing-delimiter-on-own-line` and `escape-trailing-spaces` are rewrites, because each changes the string.
- `escape-trailing-spaces` does nothing below Java 15, where `\s` does not compile.
  - The first adds the line terminator that a delimiter on its own line implies.
  - The second makes trailing spaces significant that the language would discard.
  - Each may change only a line's trailing white space and the final line terminator.
- Both rewrites are off by default, so a string constant never changes unasked.

### `literals`

| Key                    | Values                       | Default | Effect                                        | Example                            |
|------------------------|------------------------------|---------|-----------------------------------------------|------------------------------------|
| `literals.long-suffix` | `preserve`, `upper`          | `upper` | Case of the suffix on long literals           | `upper`: `10l` becomes `10L`       |
| `literals.hex-digits`  | `preserve`, `upper`, `lower` | `upper` | Case of the digits `a` to `f` in hex literals | `upper`: `0xcafe` becomes `0xCAFE` |

- Both rules change only the case of characters in a numeric literal, never its value.
- A literal written with a Unicode escape is left alone.
- `hex-digits` changes the digits `a` to `f` of `0x` integer and hex floating literals. It leaves the `0x` prefix, the `p` exponent and every suffix alone.

### `semicolons`

| Key                           | Values  | Default | Effect                                                   | Example                            |
|-------------------------------|---------|---------|----------------------------------------------------------|------------------------------------|
| `semicolons.remove-redundant` | boolean | `true`  | Remove stray semicolons between members and after a type | `true`: `int a;;` becomes `int a;` |

- `remove-redundant` deletes a stray `;` that is an empty declaration, either between the members of a type body or after a top-level type.
- Empty statements in a method body, the `;` that ends an enum's constants, `for (;;)` and a semicolon with a comment attached are left alone.
- An enum whose only members after the constants are stray semicolons keeps them.

### `preservation`

| Key                                             | Values  | Default | Effect                                                   | Example                                                      |
|-------------------------------------------------|---------|---------|----------------------------------------------------------|--------------------------------------------------------------|
| `preservation.keep-author-blank-lines`          | boolean | `true`  | Keep blank lines the author placed inside bodies         | `true`: a blank line splitting two statement groups survives |
| `preservation.max-preserved-blank-lines`        | integer | `1`     | Most consecutive author blank lines kept                 | `1`: two author blank lines collapse to one                  |
| `preservation.keep-line-break-after-open-paren` | boolean | `false` | Keep a break the author put after an opening parenthesis | `true`: `f(`<br>`········a, b)` stays broken                 |
| `preservation.keep-simple-blocks-inline`        | boolean | `false` | Keep a block the author wrote on one line on one line    | `true`: `if (x) { return; }` is left alone                   |
| `preservation.keep-array-initializer-layout`    | boolean | `false` | Keep the row layout of a hand-arranged array initializer | `true`: a matrix written as one row per line stays that way  |
| `preservation.respect-existing-chain-breaks`    | boolean | `false` | Keep breaks the author placed in a method chain          | `true`: a chain the author broke stays broken                |
| `preservation.never-join-lines`                 | boolean | `false` | Never merge two lines the author kept apart              | `true` would make every author line break load-bearing       |

- These rules elsewhere also keep what the author wrote:
  - `wrapping.keep-simple-{methods,lambdas,classes}-on-one-line`
  - `patterns.keep-simple-pattern-inline`
  - `switch.null-default-on-one-line`
  - `wrapping.throws-clause = preserve`
- A construct is on one line when no line terminator falls between its first token and its last character.
- A comment the author kept inline is part of that line. A `//` comment ends the line, so a body carrying one was never on one line and is laid out like any other.
- `never-join-lines` applies wherever there is a wrapping decision.

### `arrays`

| Key                       | Values             | Default | Effect                                            | Example                                     |
|---------------------------|--------------------|---------|---------------------------------------------------|---------------------------------------------|
| `arrays.c-style-brackets` | `preserve`, `java` | `java`  | Placement of array brackets on declared variables | `java`: `int a[], b[]` becomes `int[] a, b` |

- `java` moves brackets from fields, locals and parameters onto the type.
- Method return brackets, varargs, annotated brackets and brackets with comments stay.
- A declaration with several declarators changes only when every declarator has the same brackets.

### `members`

| Key             | Values                 | Default    | Effect                                 | Example                                         |
|-----------------|------------------------|------------|----------------------------------------|-------------------------------------------------|
| `members.order` | `preserve`, `intellij` | `intellij` | Ordering of the members of a type body | `intellij`: a static field moves above a method |

- `intellij` sorts each type body by IntelliJ IDEA's default arrangement. Members of one group keep the order they were written in.
- The groups, in order:
  1. static final fields
  2. static fields
  3. static initializers
  4. final fields
  5. fields
  6. instance initializers
  7. constructors
  8. static methods
  9. methods
  10. nested enums
  11. nested interfaces
  12. static nested classes
  13. inner classes
- Fields are ordered by access within their group, from `public` to `private`.
- A member moves with its comments and annotations, and enum constants stay first.
- A body is left alone when it holds a formatter-off region or a stray semicolon.
- A body is left alone, with a warning, when the sort would swap two field initializers or initializer blocks whose order matters. Two fields may swap only when their initializers are built from literals, operators, casts and names, and neither names the other.

## Design

- **Lossless by Construction:** The lexer emits every character exactly once, and the parser attaches
  every comment to exactly one token, so the tree always concatenates back to the original file.
  Everything above it can therefore be verified.
- **Verified Output:** Formatting must be a fixed point, and must preserve the significant token
  stream. If either check fails, the original source is returned with a diagnostic.
- **Partial Parser Coverage is Isolated:** A construct the parser does not yet understand is emitted
  verbatim and disables rewrites for that file. Parsed regions around it may still be laid out, with
  the same token, prose, reparse and fixed-point checks as a completely parsed file.
- **Prose is Checked Too:** Comments are not significant tokens, so the token check is blind to
  them. The `comments.reflow` and `javadoc.*` rules may move words between lines and nothing else.
  The words and their order must match, and every `{@code}`, `<pre>` and `@snippet` region must
  match character for character.
- **Declared Rewrites:** Rules that add or remove code run in a separate stage that declares
  every token it changed. The output is checked against that declaration token for token, so an
  undeclared change fails loudly as a corrupted one. A rewrite that fails verification costs
  the file its rewrites and not its formatting.
- **One Rule Catalogue:** Every rule is an `Option<T>` registered once. The TOML reader, the Gradle
  DSL, the Maven parameters and `--dump-config` all read from it.
- **Author's Layout Matters:** The `preservation.*` rules keep blank lines, chain breaks and
  hand-arranged initializers the author chose. Refusing to do that is what makes a formatter
  correct but unpleasant.
- **Alignment is Padding:** The `alignment.*` rules run over text the layout engine has
  already produced, because where a run of lines should share a column is not known until they have
  all been printed. Nothing they do can move a line break.

## Output stability

- Within 1.x, the output for a given style changes only to fix a bug, where the previous output was wrong or unstable.
- The defaults shipped in 1.0.1 are the baseline. Changing one waits for 2.0.
- Rules added after 1.0.1 default to preserve or off.
- Every release diffs the formatted output of a fixed corpus against golden files.

## Runtime

| Artifact                                       | Supported                                                                                                             |
|------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------|
| Core library, CLI, Gradle plugin, Maven plugin | Java 21 and up. Compiled with `--release 21`, tested on Java 21 and on the build JDK (25)                             |
| IntelliJ plugin                                | IntelliJ IDEA 2025.1, which hosts plugins on Java 21                                                                  |
| Gradle plugin                                  | Gradle 8.5 through 9.7.0. CI runs both endpoints on Java 21                                                           |
| Maven plugin                                   | Maven 3.9.0 through 3.9.16. CI runs the packaged fixture on both endpoints. Maven 4 prereleases are not supported yet |

## Building

- The Maven plugin descriptor in `maven-plugin/src/main/resources/META-INF/maven/plugin.xml` is hand-written. Generating it needs either Maven itself or a Gradle plugin that no longer runs on Gradle 9. `MavenPluginDescriptorTest` checks it against the mojo annotations and the project version on every build.
- Versions come from [Cleanroom Versioning](https://github.com/CleanroomMC/Versioning), which derives the version from the nearest git tag through `git describe`.
- Local builds get a `+local.<distance>` suffix. A release is the numeric version and requires a matching git tag with no `v` prefix.
- The `Publish` workflow sends the Gradle plugin to the Plugin Portal, `formatj` and `formatj-maven-plugin` to [maven.cleanroommc.com](https://maven.cleanroommc.com), and the CLI zip and tar to a GitHub Release.

## Origins

Java's formatters... [are a pain in the ass](https://jqno.nl/post/2024/08/24/why-are-there-no-decent-code-formatters-for-java/)

- `google-java-format` forces two-space indent and over-indents continuations
- `prettier-java` is aesthetically pleasing but unstable between versions and needs a NodeJS runtime
- IntelliJ's formatter cannot be invoked outside the IDE
- Eclipse JDT needs Eclipse itself to produce an XML file nobody wants to edit
- `palantir-java-format` and `spring-java-format` ship no usable command line

`FormatJ` aims to do the best of all worlds, with one core engine that devs consume as a builder-style library, a CLI, a Gradle plugin, a Maven plugin or an IntelliJ plugin.

It is also a fairly complex project for testing out frontier AI model capabilities.

- Assisted with Grok 4.6 (XH), GPT 5.6 Sol (XH) & Claude Opus 5 (M)
- Subagents/agent swarms purposefully not used here
- 30 minutes (max) window after each code generation for peer-human-review
- No prior (AGENTS.md) instructions were injected
