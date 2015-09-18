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

import com.spotify.missinglink.datamodel.AccessedField;
import com.spotify.missinglink.datamodel.CalledMethod;
import com.spotify.missinglink.datamodel.ClassTypeDescriptor;
import com.spotify.missinglink.datamodel.DeclaredMethod;

class DeclaredMethodNode extends Node {

  private Traverser traverser;
  private final ClassTypeDescriptor className;
  private final DeclaredMethod method;

  public DeclaredMethodNode(
      Traverser traverser, Node from,
      ClassTypeDescriptor className, DeclaredMethod method) {
    super(from);
    this.traverser = traverser;
    this.className = className;
    this.method = method;
  }

  @Override
  void validate() {
    for (CalledMethod calledMethod : method.methodCalls()) {
      traverser.visit(new CalledMethodNode(traverser, this, calledMethod));
    }

    for (AccessedField accessedField : method.fieldAccesses()) {
      traverser.visit(new FieldAccessNode(traverser, this, accessedField));
    }
  }

  @Override
  public String toString() {
    final String descriptor = method.descriptor().prettyWithoutReturnType();
    return className + ":" + method.lineNumber() + " " + descriptor;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    DeclaredMethodNode that = (DeclaredMethodNode) o;

    if (!className.equals(that.className)) {
      return false;
    }
    return method.equals(that.method);

  }

  @Override
  public int hashCode() {
    int result = className.hashCode();
    result = 31 * result + method.hashCode();
    return result;
  }
}
