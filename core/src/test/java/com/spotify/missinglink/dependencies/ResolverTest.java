package com.spotify.missinglink.dependencies;

import com.spotify.missinglink.ArtifactLoader;
import com.spotify.missinglink.ClassLoader;
import com.spotify.missinglink.ClassLoadingUtil;
import com.spotify.missinglink.Conflict;
import com.spotify.missinglink.ConflictChecker;
import com.spotify.missinglink.EmptyConflictFilter;
import com.spotify.missinglink.datamodel.Artifact;
import com.spotify.missinglink.datamodel.ArtifactBuilder;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.Assert.*;

public class ResolverTest {
  public static void main(String[] args) throws IOException {
    Resolver resolver = Resolver.createFromPomfile(new File("/home/krka/repo/bom/bom/pom.xml"));
    resolver.printDependencyTree();

    Set<String> seedNames = resolver.getRoots().stream()
            .map(ArtifactContainer::getArtifactName)
            .collect(Collectors.toSet());
    seedNames.forEach(r -> System.out.println("Root: " + r));
    Set<ArtifactContainer> latestArtifacts = resolver.getLatestArtifacts();
    //System.out.println("Keep these:");
    //latestArtifacts.forEach(System.out::println);

    Collection<ArtifactContainer> allArtifacts = resolver.getArtifacts().values();
    HashSet<ArtifactContainer> removed = new HashSet<>(allArtifacts);
    removed.removeAll(latestArtifacts);

    System.out.println("Removed:");
    removed.forEach(System.out::println);
    ConflictChecker conflictChecker = new ConflictChecker();

    ArrayList<Artifact> artifacts = new ArrayList<>();
    ArrayList<Artifact> seeds = new ArrayList<>();

    ArtifactLoader artifactLoader = new ArtifactLoader();
    for (ArtifactContainer latestArtifact : latestArtifacts) {
      Artifact loaded = artifactLoader.load(latestArtifact.getFile());
      artifacts.add(loaded);
      if (seedNames.contains(latestArtifact.getArtifactName())) {
        seeds.add(loaded);
      }
    }

    ArrayList<Artifact> all = new ArrayList<>(artifacts);
    all.addAll(ClassLoadingUtil.bootstrapArtifacts());

    List<Conflict> conflicts = conflictChecker.check(new ExpectedClasses(allArtifacts), seeds, artifacts, all);
    conflicts.forEach(conflict -> System.out.println(conflict.describe()));

  }

}