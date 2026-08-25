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

## Design

- **Lossless by Construction.** The lexer emits every character exactly once, and the parser attaches
  every comment to exactly one token, so the tree always concatenates back to the original file.
  Everything above it can therefore be verified.
- **Verified Output.** Formatting must preserve the significant token stream and must be a fixed
  point. If either check fails, the original source is returned with a diagnostic.
- **One Rule Catalogue.** Every rule is an `Option<T>` registered once. The TOML reader, the Gradle
  DSL, the Maven parameters and `--dump-config` all read from it.
- **Author's layout matters.** The `preservation.*` rules keep blank lines, chain breaks and
  hand-arranged initializers the author chose. Refusing to do that is what makes a formatter
  correct but unpleasant.

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

## Using the Gradle plugin

```kotlin
plugins {
    java
    id("zone.rong.formatj")
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

- `formatj:format` binds to `process-sources`, `formatj:check` to `verify`. No `settings.xml` changes are needed.

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

## Building

The Maven plugin descriptor in `maven-plugin/src/main/resources/META-INF/maven/plugin.xml` is hand-written.
Generating it needs either Maven itself or a Gradle plugin that no longer runs on Gradle 9.
`MavenPluginDescriptorTest` checks it against the mojo annotations and the project version on every build.