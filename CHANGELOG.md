### 0.2.6
- Added `targetSourcePackages` and `targetDestinationPackages` configuration options
- Renamed `ignoreSubpackages` configuration option to `filterSubpackages`

### 0.2.5

- Added `java.lang.invoke.VarHandle` to the list of classes with
  `@HotSpotIntrinsicCandidate` annotated methods which are excluded from analysis.
  Calls to `VarHandle` methods will no longer result in missinglink generating
  false warnings about methods like `getAndSet` not existing.

### 0.2.4

- Handle EA versions of Java

### 0.2.3

Upgraded a number of dependencies:

- asm-tree updated to 9.1
- auto-matter-annotation updated to 0.16.0
- Guava updated to 30.1.1-jre
- maven-plugin-api updated to 3.8.1
- maven-artifact updated to 3.8.1
- maven-compat updated to 3.8.1
- maven-core updated to 3.8.1
- maven-plugin-plugin updated to 3.6.1

Internal changes:

- moved to Github Actions for CI
- replaced Checkstyle with com.coveo:fmt-maven-plugin
- jmh.version updated to 1.32
- mockito-core updated to 3.11.0
- junit updated to 4.13.2

### 0.2.2
- Handle Multi-Release JARs
- Use thread-safe caches to enable using Missinglink on multiple projects concurrently

### 0.2.1
- Fix bug with false positives for calls to MethodHandle.invoke()

### 0.2.0
- Remove dependencies on Guava

### 0.1.3
- Plugin now checks if exceptions that would get thrown are explicitly caught to avoid false positives

### 0.1.2
- if no classes found in build directory, log a suggestion to run `mvn compile`
  first.
- internal refactoring: add integration tests to Maven build via
  maven-invoker-plugin (thanks @dflemstr)
- made plugin java9 compatible

### 0.1.1
- rename goal in maven-plugin from `check-conflicts` to `check`
- performance improvements - missinglink allocates less objects during
  analysis, therefore creating less pressure and spending less time in GC, and
  overall operating faster.

### 0.1.0 (initial release)
- core project and maven plugin
