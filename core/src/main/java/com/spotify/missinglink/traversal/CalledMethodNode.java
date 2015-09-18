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

import com.spotify.missinglink.datamodel.CalledMethod;
import com.spotify.missinglink.datamodel.ClassTypeDescriptor;
import com.spotify.missinglink.datamodel.DeclaredClass;
import com.spotify.missinglink.datamodel.DeclaredMethod;

public class CalledMethodNode extends Node {

  private Traverser traverser;
  private final CalledMethod calledMethod;

  public CalledMethodNode(Traverser traverser, Node from, CalledMethod calledMethod) {
    super(from);
    this.traverser = traverser;
    this.calledMethod = calledMethod;
  }

  @Override
  void validate() {
    final ClassTypeDescriptor superclassWithMethod =
        traverser.findSuperclassWithMethod(calledMethod.owner(), calledMethod);

    if (superclassWithMethod == null) {
      traverser.conflicts.add(this);
      return;
    }

    if (!traverser.relevantClasses.contains(superclassWithMethod)) {
      return;
    }

    traverser.visit(new ClassNode(traverser, this, superclassWithMethod));

    final DeclaredClass declaredClass = traverser.knownClasses.get(superclassWithMethod);
    if (declaredClass == null) {
      return;
    }

    final DeclaredMethod declaredMethod = declaredClass.methods().get(calledMethod.descriptor());
    if (declaredMethod == null) {
      // should actually be impossible
      return;
    }

    traverser.visit(new DeclaredMethodNode(traverser, this, superclassWithMethod, declaredMethod));
  }

  @Override
  public String toString() {
    return "Called method: " + calledMethod.pretty();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    CalledMethodNode that = (CalledMethodNode) o;

    return calledMethod.equals(that.calledMethod);

  }

  @Override
  public int hashCode() {
    return calledMethod.hashCode();
  }
}
