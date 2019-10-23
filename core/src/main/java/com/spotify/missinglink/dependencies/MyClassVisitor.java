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
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.TypePath;

import static org.objectweb.asm.Opcodes.ASM7;

class MyClassVisitor extends ClassVisitor {
  private final ArtifactContainerBuilder artifactContainer;
  private final MyMethodVisitor methodVisitor;
  private final MyAnnotationVisitor annotationVisitor;
  private final MyFieldVisitor fieldVisitor;
  private String className;

  MyClassVisitor(ArtifactContainerBuilder artifactContainer) {
    super(ASM7);
    this.artifactContainer = artifactContainer;
    annotationVisitor = new MyAnnotationVisitor(artifactContainer);
    methodVisitor = new MyMethodVisitor(artifactContainer, annotationVisitor);
    fieldVisitor = new MyFieldVisitor(artifactContainer, annotationVisitor);
  }

  @Override
  public void visit(
          int version, int access, String name, String signature,
          String superName, String[] interfaces) {
    className = name;
    if (className.equals("module-info")) {
      // Not useful to keep this
      return;
    }
    if (isAccessible(access)) {
      artifactContainer.addDefinition(className);
    }

    if (superName != null) {
      artifactContainer.addClass(superName);
    }
    for (String anInterface : interfaces) {
      artifactContainer.addClass(anInterface);
    }
  }

  private boolean isAccessible(int access) {
    return (access & Opcodes.ACC_PRIVATE) == 0;
  }

  @Override
  public void visitInnerClass(String name, String outerName, String innerName, int access) {
    if (outerName == null || outerName.equals(className)) {
      if (isAccessible(access)) {
        artifactContainer.addDefinition(name);
      }
    }
  }

  @Override
  public FieldVisitor visitField(
          int access, String name, String descriptor, String signature, Object value) {
    artifactContainer.addDescriptor(descriptor);
    return fieldVisitor;
  }

  @Override
  public MethodVisitor visitMethod(
          int access, String name, String descriptor, String signature, String[] exceptions) {
    artifactContainer.addDescriptor(descriptor);
    return methodVisitor;
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

}
