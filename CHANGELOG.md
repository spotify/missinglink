### 0.1.2
- if no classes found in build directory, log a suggestion to run `mvn compile`
  first.
- internal refactoring: add integration tests to Maven build via
  maven-invoker-plugin (thanks @dflemstr)

### 0.1.1
- rename goal in maven-plugin from `check-conflicts` to `check`
- performance improvements - missinglink allocates less objects during
  analysis, therefore creating less pressure and spending less time in GC, and
  overall operating faster.

### 0.1.0 (initial release)
- core project and maven plugin

