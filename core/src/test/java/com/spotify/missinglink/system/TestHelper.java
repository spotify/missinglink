package com.spotify.missinglink.system;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.reflect.ClassPath;
import com.spotify.missinglink.ArtifactLoader;
import com.spotify.missinglink.ClassLoadingUtil;
import com.spotify.missinglink.Conflict;
import com.spotify.missinglink.ConflictChecker;
import com.spotify.missinglink.datamodel.Artifact;
import com.spotify.missinglink.datamodel.ArtifactName;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public class TestHelper {

  public static final ImmutableList<Artifact> BOOTSTRAP_ARTIFACTS = ClassLoadingUtil.bootstrapArtifacts();

  public static ImmutableList<Conflict> getConflicts(final Class<?> baseClass) throws IOException {
    final ClassPath classPath = ClassPath.from(baseClass.getClassLoader());
    final String packageBase = baseClass.getPackage().getName();

    final String depsPackage = packageBase + ".deps";
    final String linkedPackage = packageBase + ".linked";
    final String seedPackage = packageBase + ".seeds";

    final ImmutableSet<ClassPath.ClassInfo> seedClasses =
        classPath.getTopLevelClassesRecursive(seedPackage);
    final ImmutableSet<ClassPath.ClassInfo> linkedClasses =
        classPath.getTopLevelClassesRecursive(linkedPackage);

    final ArtifactLoader artifactLoader = new ArtifactLoader();
    final Artifact seeds = artifactLoader.load(new ArtifactName("seeds"), getFiles(seedClasses));
    final Artifact linked = PackageRenamer.renamePackages(
        linkedPackage, depsPackage, artifactLoader.load(new ArtifactName("linked"), getFiles(linkedClasses)));

    final ConflictChecker conflictChecker = new ConflictChecker();
    final ImmutableList<Artifact> all = ImmutableList.<Artifact>builder()
        .add(seeds)
        .add(linked)
        .addAll(BOOTSTRAP_ARTIFACTS).build();
    return conflictChecker.check(seeds, ImmutableList.of(seeds, linked), all);
  }

  private static Collection<File> getFiles(final ImmutableSet<ClassPath.ClassInfo> classes) {
    return classes.stream()
        .map(ClassPath.ResourceInfo::url)
        .map(URL::getFile)
        .map(File::new)
        .collect(ImmutableList.toImmutableList());
  }
}
