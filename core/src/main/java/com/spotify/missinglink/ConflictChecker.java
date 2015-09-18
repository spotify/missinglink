/*
 * Copyright (c) 2015 Spotify AB
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.spotify.missinglink;

import com.google.common.collect.ImmutableList;

import com.spotify.missinglink.datamodel.Artifact;
import com.spotify.missinglink.datamodel.ClassTypeDescriptor;
import com.spotify.missinglink.traversal.Node;
import com.spotify.missinglink.traversal.Traverser;

import java.util.List;

/**
 * Inputs:
 * <p>
 * 1) The full set of artifact dependencies (D). This can be extracted from the dependency graph.
 * The exact structure of the graph is not interesting, only knowing which dependencies are a part
 * of it. Note that this means that the same library can appear multiple times (with different
 * versions)
 * 2) The classpath of artifacts that is actually used (C). Ordering is important here. If a class
 * appears
 * more than once, the first occurrence will be used.
 * <p>
 * Assumptions:
 * <p>
 * 1) Each artifact could be compiled successfully using their own dependencies.
 * 2) Each artifact was compiled against the same JDK, or at the very least only using parts of the
 * JDK
 * that didn't change compatibility. This is not a fully safe assumption, but to catch the
 * kind of problems that could occur due to this would need a more broad analysis.
 * <p>
 * Strategy:
 * 1) Identify which classes are a part of D but does not exist in C. This is the missing set (M)
 * 2) Identify which classes are a part of D but are replaced in D (or is not the first occurrence)
 * This is the suspicious set (S)
 * 3) Walk through the class hierarchy graph.
 * If something depends on something in M, also add that class to M.
 * If something depends on something in S, also add that class to S
 * 4) Walk through the method call graph, starting from the main entry point (the primary artifact)
 * Whenever a method call is reached, check if the class and method exists.
 * If it doesn't exist, also look in parent classes
 * (implementations could exist both in superclasses and interfaces).
 * <p>
 * Note that we only need to try to verify the method if it's made to a class that is in M or S.
 * If it is in M: fail.
 * If it is in S: check it.
 * <p>
 * If we don't have access to one of the parents, we could simply assume that the call is safe
 * This would however lead to all methods being marked as safe, since everything ultimately
 * inherits from Object (or some other jdk class).
 * <p>
 * The alternative is to mark such calls as failures, which may lead to false positives.
 * This might be ok for the MVP.
 * <p>
 * So we need to have the JDK classes (or some other provided dependencies) as input
 * in order to lookup inheritance.
 * <p>
 * <p>
 * <p>
 * For now, this is not really in place - we simply just look at all the things in the classpath
 */
public class ConflictChecker {

  /**
   * @param projectArtifact  the main artifact of the project we're verifying
   *                         (this is considered the entry point for reachability)
   * @param artifactsToCheck all artifacts that are on the runtime classpath
   * @param allArtifacts     all artifacts, including implicit artifacts (runtime provided
   *                         artifacts)
   * @return a list of conflicts
   */
  public ImmutableList<Node> check(Artifact projectArtifact,
                                       List<Artifact> artifactsToCheck,
                                       List<Artifact> allArtifacts) {

    final Traverser traverser = new Traverser(allArtifacts, artifactsToCheck);
    traverser.visit(projectArtifact);
    traverser.traverse();

    // TODO: do something with this
    final List<ClassTypeDescriptor> multipleDefinitions = traverser.getMultipleDefinitions();

    return ImmutableList.copyOf(traverser.getConflicts());
  }

}
