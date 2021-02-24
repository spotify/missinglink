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
import org.objectweb.asm.Attribute;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.TypePath;

class MyFieldVisitor extends FieldVisitor {
  private final ArtifactContainerBuilder artifactContainer;
  private final MyAnnotationVisitor annotationVisitor;

  MyFieldVisitor(
          ArtifactContainerBuilder artifactContainer,
          MyAnnotationVisitor annotationVisitor) {
    super(Opcodes.ASM7);
    this.artifactContainer = artifactContainer;
    this.annotationVisitor = annotationVisitor;
  }

  @Override
  public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
    artifactContainer.addDescriptor(descriptor);
    return annotationVisitor;
  }

  @Override
  public AnnotationVisitor visitTypeAnnotation(
          int typeRef, TypePath typePath, String descriptor, boolean visible) {
    artifactContainer.addDescriptor(descriptor);
    return annotationVisitor;
  }

  @Override
  public void visitAttribute(Attribute attribute) {
    super.visitAttribute(attribute);
  }

}
