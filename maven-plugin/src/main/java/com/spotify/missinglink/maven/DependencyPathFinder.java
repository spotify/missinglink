/*-
 * -\-\-
 * missinglink-maven-plugin
 * --
 * Copyright (C) 2016 - 2021 Spotify AB
 * --
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * -/-/-
 */

/*
 * Rapid7 addition (Tier A attribution) — not present in upstream spotify/missinglink.
 * Given a conflict's caller artifact ("usedBy"), walk the resolved Maven dependency
 * graph to name the DIRECT dependency (first hop under the project root) that drags
 * it in. This turns "razor-audit-logging-client is the caller" into "bump direct dep X".
 */
package com.spotify.missinglink.maven;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.shared.dependency.graph.DependencyNode;

/** Finds root->caller paths in a resolved dependency graph and names the direct dep(s). */
class DependencyPathFinder {

  private final DependencyNode root;

  DependencyPathFinder(DependencyNode root) {
    this.root = root;
  }

  /**
   * @param groupId caller groupId (from Conflict.usedBy)
   * @param artifactId caller artifactId
   * @return the set of DIRECT dependencies (g:a:v) whose subtree contains the caller. Empty if the
   *     caller is not found (e.g. it is itself the project) — caller should handle that.
   */
  Set<String> directDependenciesFor(String groupId, String artifactId) {
    final Set<String> directDeps = new LinkedHashSet<>();
    // BFS, tracking the direct-dep ancestor (first hop under root) for each node.
    final Deque<Frame> queue = new ArrayDeque<>();
    for (DependencyNode directChild : root.getChildren()) {
      queue.add(new Frame(directChild, directChild));
    }
    final Set<DependencyNode> seen = new LinkedHashSet<>();
    while (!queue.isEmpty()) {
      final Frame f = queue.poll();
      if (!seen.add(f.node)) {
        continue;
      }
      final Artifact a = f.node.getArtifact();
      if (a != null && groupId.equals(a.getGroupId()) && artifactId.equals(a.getArtifactId())) {
        final Artifact d = f.directAncestor.getArtifact();
        directDeps.add(d.getGroupId() + ":" + d.getArtifactId() + ":" + d.getVersion());
        // don't return early — the same caller may be reachable via multiple direct deps
      }
      for (DependencyNode child : f.node.getChildren()) {
        queue.add(new Frame(child, f.directAncestor));
      }
    }
    return directDeps;
  }

  /** Full root->caller path(s) as human-readable strings, for the "how it got here" line. */
  List<String> pathsFor(String groupId, String artifactId) {
    final List<String> paths = new ArrayList<>();
    walk(root, groupId, artifactId, new ArrayList<>(), paths);
    return paths;
  }

  private void walk(DependencyNode node, String g, String a, List<String> trail, List<String> out) {
    final Artifact art = node.getArtifact();
    final String coord =
        art == null
            ? "project"
            : art.getGroupId() + ":" + art.getArtifactId() + ":" + art.getVersion();
    trail.add(coord);
    if (art != null && g.equals(art.getGroupId()) && a.equals(art.getArtifactId())) {
      out.add(String.join(" -> ", trail));
    } else {
      for (DependencyNode child : node.getChildren()) {
        walk(child, g, a, trail, out);
      }
    }
    trail.remove(trail.size() - 1);
  }

  private static final class Frame {
    final DependencyNode node;
    final DependencyNode directAncestor;

    Frame(DependencyNode node, DependencyNode directAncestor) {
      this.node = node;
      this.directAncestor = directAncestor;
    }
  }
}
