What is real today:

- A hand-written lossless lexer and recursive-descent parser covering modern Java: records, sealed
  types, switch expressions with patterns and guards, record deconstruction, text blocks, lambdas,
  var, enhanced instanceof, module imports, and derived record creation behind `--preview`.
- Error tolerance: a construct the parser cannot read becomes a verbatim region with a diagnostic, so
  a half-typed file formats everywhere except the broken part.
- Layout driven by the rule catalogue: indentation, line length, wrapping of arguments, parameters,
  chains, binary runs, conditions and lists, brace placement, blank lines, spacing, and the
  preservation rules that keep the author's blank lines, chain breaks and array layouts.
- `formatj:off` / `formatj:on` comment markers (`comments.honour-formatter-off`, and the marker text
  itself, are configurable). A marked region is reproduced byte for byte. The escape hatch works at
  whole members, whole statements and whole top-level declarations; a marker in the middle of an
  expression has no boundaries in the tree to latch onto and is ignored.
- A rewrite stage between parsing and emitting, for the rules that **add or remove code**. It is the
  only part of the formatter allowed to change the program, and it pays for that by declaring every
  token it added or removed. The output is then required to be the original with precisely those
  edits applied: an edit made but not declared fails as loudly as one that corrupted the file, and so
  does an edit declared but not made. Each edit is also measured against the law of the rule that
  authorised it, so a rule cannot launder a change by describing it as something else; comments must
  survive a rewrite; and rewriting must settle after one pass.
- Two safety checks on every file: the token check above, and formatting must be a fixed point. A
  rewrite that fails verification costs the file its rewrites rather than its formatting — the
  pipeline runs again with rewriting off and returns that with a warning. Any other failure returns
  the original source with a diagnostic.
- Through the rewrite stage so far:
  - `braces.if-else`, `braces.for-loop` and `braces.while-loop`, covering `if`/`else`, `for`, enhanced
    `for`, `while` and `do`. Braces are added freely and removed only where that is safe: never round
    a declaration, never where it would let an `else` reattach to an inner `if`, and never where a
    brace is carrying a comment that would have nowhere to go.
  - `imports.order`, `imports.groups`, `imports.static-placement`, `imports.module-imports-first`,
    `imports.blank-line-between-groups` and `imports.remove-unused`. Reordering is free — two
    single-type imports of one simple name will not compile in any order, and on-demand imports lose
    to single-type imports by rule rather than by position — so the whole run is rearranged as one
    declared edit. Deleting is not free: an import goes only when nothing else in the file names it,
    comments and Javadoc included, and never when it is on-demand or a module import, whose use
    cannot be seen by reading tokens. The verifier re-derives that from the output rather than taking
    the rewrite's word for it. `imports.order = preserve` switches the whole thing off, grouping
    included, which is the default.
  - Static imports are one group rather than being subdivided by the package prefixes again, unless
    `imports.static-placement = inline` puts them in the main block.
  - The blank line between groups is emitted by the layout stage, not inserted by the rewrite: blank
    lines are whitespace, and both stages read the same `ImportOrder` so they agree on where a group
    ends.

What is deliberately not applied yet: the remaining rules that add or remove code. Dropping redundant
lambda parentheses, rewriting `case` labels between arrow and colon, re-wrapping comment and Javadoc
prose. Each needs its own edit law in `RewriteVerification` beside the brace and import ones. The
options are in the catalogue and are read back by `--dump-config`; they simply have no effect on
output. The rules still waiting are `comments.reflow`, every `javadoc.*` rule,
`lambdas.parameter-style`, `lambdas.body-braces`, `sealed.permits-order`, `switch.case-style`,
`switch.arrow-case-braces` and `switch.yield-style`. Every `textblocks.*` rule belongs there too:
changing a text block's indentation or its closing delimiter changes the string the program produces,
so it is a rewrite even though it looks like layout.

Two of those are not simply more of the same:

- `switch.case-style` is the awkward one. Turning colon cases into arrow cases changes fallthrough, so
  it needs a precondition established before rewriting — every group ends in `break`, `return` or
  `throw`, and no variable declared in one group is read in another — rather than a check afterwards.
  Where the precondition does not hold the rewrite has to decline, not try and be caught.
- Comment and Javadoc re-wrapping is a different verification problem, not more of the same one.
  Comments are not significant tokens, so the token check is blind to them: reflow could silently drop
  a paragraph today and pass every check. It needs a prose-preservation check — word sequence
  unchanged, `{@code}`, `<pre>` and `@snippet` content byte for byte — extending the comment check the
  rewrite stage already runs. `javadoc.wrap` defaults to false until that exists.

Defaults: every rule that adds or removes code defaults to the value that changes nothing, so landing
the rewrite stage changed no output for anyone. Choosing a house style is a separate decision from
building the machinery to enforce one.

Layout rules still without an implementation, none of which need the rewrite stage:

- Column alignment: the whole of `alignment.*`.
- `comments.trailing-comment-column`, `comments.keep-first-column-comments`,
  `comments.indent-with-code`.
- `preservation.keep-line-break-after-open-paren`, `preservation.keep-simple-blocks-inline`,
  `preservation.never-join-lines`.
- `wrapping.keep-simple-methods-on-one-line`, `wrapping.keep-simple-lambdas-on-one-line`,
  `wrapping.keep-simple-classes-on-one-line`, which overlap with the preservation rules above and
  should be designed together with them.
- `patterns.keep-simple-pattern-inline`, `switch.null-default-on-one-line`, `records.with-style`.

Five entries have been deleted from the catalogue rather than implemented, taking it from 160 rules to
155. `spacing.after-instanceof` could never be honoured: `instanceof` is always followed by a type
name, and removing the space between them fuses two tokens into one.
`imports.class-count-to-use-wildcard`, `imports.static-count-to-use-wildcard` and
`imports.keep-existing-wildcards` asked for something a formatter cannot decide: collapsing several
single-type imports into one on-demand import is not a rearrangement of what is there, it changes
which names the file resolves and can quietly pick up a different type of the same simple name from
the wildcarded package, and settling that needs the classpath. `patterns.guard-wrapping` was a
duplicate of `switch.guard-on-same-line`, which is implemented, so the unimplemented half went.
