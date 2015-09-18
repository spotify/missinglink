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
package com.spotify.missinglink.traversal;

import com.spotify.missinglink.datamodel.ClassTypeDescriptor;
import com.spotify.missinglink.datamodel.DeclaredClass;

public class ClassNode extends Node {

  private Traverser traverser;
  private final ClassTypeDescriptor className;

  protected ClassNode(Traverser traverser, Node from, ClassTypeDescriptor className) {
    super(from);
    this.traverser = traverser;
    this.className = className;
  }

  @Override
  void validate() {
    final DeclaredClass declaredClass = traverser.knownClasses.get(className);
    if (declaredClass == null) {
      traverser.conflicts.add(this);
      return;
    }

    // Outside of our scope - don't care about its dependencies
    if (!traverser.relevantClasses.contains(className)) {
      return;
    }

    // TODO: check that the inheritance is correct.
    // If this class is non-abstract, it should implement all abstract methods in parents

    for (ClassTypeDescriptor parentName : declaredClass.parents()) {
      traverser.visit(new ClassNode(traverser, this, parentName));
    }

    for (ClassTypeDescriptor loadedClassName : declaredClass.loadedClasses()) {
      traverser.visit(new ClassNode(traverser, this, loadedClassName));
    }
  }

  @Override
  public String toString() {
    return "Class:" + className.getClassName();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    ClassNode classNode = (ClassNode) o;

    return className.equals(classNode.className);

  }

  @Override
  public int hashCode() {
    return className.hashCode();
  }

}
