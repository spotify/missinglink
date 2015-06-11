# Example projects

## has-problematic-dependency

This project has a class with a method that calls a Guava method which was
removed in version 18.

## uses-problematic-dependency

This project calls the method from `has` above, while setting the Guava
dependency in pom.xml to version 18. By overriding the Guava version that `has`
depends on, a NoSuchMethodError would be encountered at runtime.

When the `missinglink-maven-plugin` is executed against this example project (the
example adds the plugin to the build lifecycle automatically), it outputs a
warning about this conflict and fails the build since `failOnConflicts=true`:

```
$ cd examples/uses-problematic-dependency
$ mvn test
[INFO] Scanning for projects...
[INFO]
[INFO] ------------------------------------------------------------------------
[INFO] Building uses-problematic-dependency 0.1-SNAPSHOT
[INFO] ------------------------------------------------------------------------
[INFO]
...
[INFO]
[INFO] --- missinglink-maven-plugin:0.1.0:check (default) @ uses-problematic-dependency ---
[INFO] 1 conflicts found
[WARNING] - Problem in class com.spotify.missinglink.examples.ProblematicDependency
  method:  java.lang.Object reliesOnRemovedMethod()
  call to: com.google.common.base.Enums.valueOfFunction(java.lang.Class)
  reason:  Method not found: com.google.common.base.Enums.valueOfFunction(java.lang.Class)
[INFO] ------------------------------------------------------------------------
[INFO] BUILD FAILURE
[INFO] ------------------------------------------------------------------------
```
