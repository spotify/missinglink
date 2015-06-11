# missing-link - a maven dependency problem finder

Be warned. This project is still immature and in development.
The API may change at any time.
It may not find all problems. It may find lots of false positives.

## Quickstart - add missinglink to your Maven build

Add the following plugin to pom.xml:

```xml
<plugin>
  <groupId>com.spotify</groupId>
  <artifactId>missinglink-maven-plugin</artifactId>
  <version>0.1.0</version>
  <executions>
    <execution>
      <goals><goal>check-conflicts</goal></goals>
      <phase>process-classes</phase>
    </execution>
  </executions>
</plugin>
```

See [how to configure the plugin below](#Configuration of the plugin).

## Problem definition

When using Java and Maven, it's easy to get into a state of pulling in a lot of
dependencies. Sometimes you even get transitive dependencies (I depend on X
which in turn depends on Y). This can lead to conflicting dependencies
sometimes.

I depend on libraries X and Y. X depends on Foo v2.0.0 and Y depends on Foo
v3.0.0

Thus, I now have transitive dependencies on two different (incompatible)
versions of Foo. Which one do I pick?

If I pick v2.0.0, Y may fail in runtime due to missing classes or methods. If I
pick v3.0.0, X may fail instead.

In order to solve this, maven has an enforcer plugin which can detect these
problems. Then you have to manually choose one of the versions and hope that it
works.

You can also try to upgrade library X to use Foo v3.0.0. Sometimes this is
tricky and time-consuming, especially if X is a foreign dependency.

## A new approach at solving some of the problems

The idea is to programmatically analyze each dependency - what does the code
depend on and what does it export - on a lower level. Instead of just looking
at version numbers, we look at the actual signatures in the code.

For instance, maybe the difference between Foo v2.0.0 and Foo v3.0.0 is only
this method signature:

```java
// Foo v2.0.0
void Foo.bar(String s, int i);

// Foo v3.0.0
void Foo.bar(String s, boolean b);
```

If X or Y doesn't actually use this method, it may not matter if we're using
version 2 or 3. This is often the case of large libraries where we only use a
small subset of the methods (google guava for instance).

(Note: I am only looking at this from an API perspective - the actual code may
have different behaviour which is out of scope for this project)

# Maven plugin

This problem finder can be executed against your Maven project from the
command-line like:

```
$ mvn com.spotify:missinglink-maven-plugin:0.1.0:check-conflicts
```

The plugin will scan the source code of the current project, the runtime
dependencies (from the Maven model), and the bootstrap JDK classes (i.e.
`java.lang`, `java.util`) for conflicts. Any conflicts found will be printed
out, grouped by category of the conflict, the artifact (jar file) it was found
it, and the problematic class.

## Requirements

This plugin is using Java 8 language features. While the JVM used to execute
Maven must be at version 1.8 or greater, the Maven projects being analyzed can
be using any Java source version.

Note that when using a higher JVM version to execute Maven than what the
project is being compiled with (the `source` argument to
`maven-compiler-plugin`), some care should be taken to make sure that the
higher-versioned bootclasspath is not accidentally used with javac.

## Configuration of the plugin

Once projects get to be of a certain size, some level of conflicts - mostly
innocent - between the various dependencies and inter-dependencies of the
libraries used are inevitable. In this case, you will probably want to add the
`missinglink-maven-plugin` as a `<plugin>` to your pom.xml so you can tweak some
of its configuration options.

For example, `ch.qos.logback:logback-core` includes a bunch of optional classes
that reference `groovy.lang` classes.  Since the logback dependency specifies
its dependency on groovy as `optional=true`, the Groovy jar is not
automatically included in your project (unless you explicitly need it).

The `missinglink-maven-plugin` offers a few configuration options that can be used
to reduce the number of warnings to avoid drowning in "false" positives.

The suggested workflow for using this plugin is to execute it against your
project once with no configuration, then carefully add dependencies/packages to
the ignores list after you are sure these are not true issues.

To add the plugin to your project, add the following to the `<plugins>` section:

```xml
<plugin>
  <groupId>com.spotify</groupId>
  <artifactId>missinglink-maven-plugin</artifactId>
  <version>VERSION</version>
</plugin>
```

The plugin can be specified to fail the build if any conflicts are found. To
automatically execute the plugin on each build, add an `<execution>` section
like:

```xml
<configuration>
  <failOnConflicts>true</failOnConflicts>
</configuration>
<executions>
  <execution>
    <goals><goal>check-conflicts</goal></goals>
    <phase>process-classes</phase>
  </execution>
</executions>
```

### Exclude some dependencies from analysis

Specific dependencies can be excluded from analysis if you know that all
conflicts within that jar are "false" or irrelevant to your project.

For example, logback-core and logback-classic have many references (in optional
classes) to classes needed by the Groovy language. To exclude these jars from
being analyzed, add an `<excludeDependencies>` section to `<configuration>`
like:

```xml
<excludeDependencies>
  <excludeDependency>
    <groupId>ch.qos.logback</groupId>
    <artifactId>logback-core</artifactId>
  </excludeDependency>
  <excludeDependency>
    <groupId>ch.qos.logback</groupId>
    <artifactId>logback-classic</artifactId>
  </excludeDependency>
</excludeDependency>
```

### Ignore conflicts in certain packages

Conflicts can be ignored based on the package name of the class that has the
conflict. There are separate configuration options for ignoring conflicts on
the "source" side of the conflict and the "destination" side of the conflict.

For example, if `com.foo.Bar` calls a method `void doSomething(int)` in the
`biz.blah.Something` class, then `com.foo.Bar` is on the source/calling side
and `biz.blah.Something` is on the destination/callee side.

Packages on the source side can be ignored with `<ignoreSourcePackages>` and
packages on the destination side can be ignored with
`<ignoreDestinationPackages>`:

```xml
<configuration>
  <!-- ignore conflicts with groovy.lang on the caller side -->
  <ignoreSourcePackages>
    <ignoreSourcePackages>
      <package>groovy.lang</package>
    </ignoreSourcePackages>
  </ignoreSourcePackages>
  <!-- ignore conflicts with com.foo on the callee side -->
  <ignoreDestinationPackages>
    <ignoreDestinationPackage>
      <package>com.foo</package>
    </ignoreDestinationPackage>
  </ignoreDestinationPackages>
</configuration>

```

By default, all subpackages of the specified packages are also ignored, but
this can be disabled on an individual basis by adding
`<ignoreSubpackages>false</ignoreSubpackages>` to the `<ignoreSourcePackage>`
or `<ignoreDestinationPackage>` element.

# Caveats and Limitations

Because this plugin analyzes the bytecode of the `.class` files of your code
and all its dependencies, it has a few limitations which prevent conflicts
from being found in certain scenarios.

## Reflection

When reflection is used to load a class or invoke a method, this tool is not
able to follow the call graph past the point of reflection.

## Dependency Injection containers

Most DI containers, such as Guice, use reflection to load modules at runtime
and wire object graphs together; therefore this tool can't follow the
connection between your source code and any modules that might be loaded by
Guice or other containers from libraries on the classpath.

## Dead code

This tool parses the bytecode of each `.class` file and looks at the "method
instruction" calls to build a graph between classes and which methods are
invoking which methods.

Since the tool is scanning the bytecode but not actually *executing it*, it has
no awareness of whether or not a method instruction will actually be executed
at runtime. 

If bytecode exists for invoking a method in a class but that code path will
never actually be activated at runtime, this tool will still follow that
connection and report any conflicts it might find through that path.

## Safe instances of class not found

Some libraries enable optional features when other classes are available on the
classpath, for example Netty tries to detect if cglib is available. These code patterns look something like

```java
boolean coolFeatureEnabled = false;
try {
    Class.forName("com.sprockets.SomeOptionalFeature");
    coolFeatureEnabled = true;
} catch (Throwable t) {
    // optional sprockets library not available
}

...
if (coolFeatureEnabled) {
    // load something that calls SomeOptionalFeature class
}
```

Javadeps will detect these calls to the optional classes and flag them as
conflicts, even though not having the class available will not cause any
runtime errors. Configure the plugin to ignore these classes/dependencies.

## History

This started as a [Spotify](<https://github.com/spotify) hackweek project
in June 2015 by
[Matt Brown](https://github.com/mattnworb),
[Kristofer Karlsson](https://github.com/krka),
[Axel Liljencrantz](https://github.com/liljencrantz)
and [Petter Måhlén](https://github.com/pettermahlen).

It was inspired by some real problems that happened when there were incompatible
transitive dependencies for a rarely used code path that wasn't detected until runtime.

We thought that should be detectable in build time instead - so we built this to see if it was feasible.

