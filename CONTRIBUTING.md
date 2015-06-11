# Contributing to missinglink

First of all, thanks for the interest in missinglink! :clap:

As mentioned in the README, this project is very young and not mature yet.

It was built during a Hack Week at Spotify to attempt to catch a certain type
of dependency conflict with Maven projects that the authors had bad experiences
with.

## Submitting issues

We'd like for it to be able to catch lots of other types of conflicts but there
are a lot of scenarios we have not encountered or will be able to test, so any
and all feedback is appreciated.

To report any issues or share feedback you have, please create [an issue on
Github][github-issues]. We'd like to hear about false positives that this tool
reports for you, or conflicts that it seems like *should* be caught but
weren't.

[github-issues]: https://github.com/spotify/missinglink/issues

## Building the project

With Java 8+ and Maven 3+, simply run `mvn install` in the project directory.

For any patches, please make sure that they pass all tests (and that new tests
are added), and that no Checkstyle rules are violated.

New code should be accompanied by tests that increase the overall code coverage
of the project.

## Additional documentation

- [ASM][asm] is used to read the bytecode of Java classfiles
- [auto-matter][] is used to generate "value classes" for missinglink's data model
- [Reference on Maven plugin development][maven-plugins]

[asm]: http://asm.ow2.org/
[maven-plugins]: https://maven.apache.org/guides/plugin/guide-java-plugin-development.html
[auto-matter]: https://github.com/danielnorberg/auto-matter
