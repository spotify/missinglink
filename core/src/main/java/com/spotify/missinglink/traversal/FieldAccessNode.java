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
import com.spotify.missinglink.datamodel.ClassTypeDescriptor;
import com.spotify.missinglink.datamodel.DeclaredClass;

public class FieldAccessNode extends Node {

  private Traverser traverser;
  private final AccessedField accessedField;

  public FieldAccessNode(Traverser traverser, Node from, AccessedField accessedField) {
    super(from);
    this.traverser = traverser;
    this.accessedField = accessedField;
  }

  @Override
  void validate() {
    final ClassTypeDescriptor className = accessedField.owner();
    traverser.visit(new ClassNode(traverser, this, className));

    final DeclaredClass declaredClass = traverser.knownClasses.get(className);
    if (declaredClass == null) {
      return;
    }

    if (!traverser.hasFieldInAnySuperclass(className, accessedField)) {
      traverser.conflicts.add(this);
    }
  }

  @Override
  public String toString() {
    return accessedField.pretty();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    FieldAccessNode that = (FieldAccessNode) o;

    return accessedField.equals(that.accessedField);

  }

  @Override
  public int hashCode() {
    return accessedField.hashCode();
  }
}
