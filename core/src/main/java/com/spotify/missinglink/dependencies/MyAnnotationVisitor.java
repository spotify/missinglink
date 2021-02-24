/*
 * Copyright (c) 2019 Spotify AB
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
package com.spotify.missinglink.dependencies;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.Opcodes;

class MyAnnotationVisitor extends AnnotationVisitor {
  private final ArtifactContainerBuilder artifactContainer;

  MyAnnotationVisitor(ArtifactContainerBuilder artifactContainer) {
    super(Opcodes.ASM7);
    this.artifactContainer = artifactContainer;
  }

  @Override
  public void visit(String name, Object value) {
    super.visit(name, value);
  }

  @Override
  public void visitEnum(String name, String descriptor, String value) {
    artifactContainer.addDescriptor(descriptor);
    super.visitEnum(name, descriptor, value);
  }

  @Override
  public AnnotationVisitor visitAnnotation(String name, String descriptor) {
    artifactContainer.addDescriptor(descriptor);
    return this;
  }

  @Override
  public AnnotationVisitor visitArray(String name) {
    return this;
  }

}
