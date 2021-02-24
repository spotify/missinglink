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
import org.objectweb.asm.Handle;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.TypePath;

import static org.objectweb.asm.Opcodes.ASM7;

class MyMethodVisitor extends MethodVisitor {
  private final ArtifactContainerBuilder artifactContainer;
  private final AnnotationVisitor annotationVisitor;

  MyMethodVisitor(
          ArtifactContainerBuilder artifactContainer,
          MyAnnotationVisitor annotationVisitor) {
    super(ASM7);
    this.artifactContainer = artifactContainer;
    this.annotationVisitor = annotationVisitor;
  }

  @Override
  public void visitParameter(String name, int access) {
    super.visitParameter(name, access);
  }

  @Override
  public AnnotationVisitor visitAnnotationDefault() {
    return annotationVisitor;
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
  public void visitAnnotableParameterCount(int parameterCount, boolean visible) {
    super.visitAnnotableParameterCount(parameterCount, visible);
  }

  @Override
  public AnnotationVisitor visitParameterAnnotation(
          int parameter, String descriptor, boolean visible) {
    artifactContainer.addDescriptor(descriptor);
    return annotationVisitor;
  }

  @Override
  public void visitAttribute(Attribute attribute) {
    super.visitAttribute(attribute);
  }

  @Override
  public void visitFrame(int type, int numLocal, Object[] local, int numStack, Object[] stack) {
    super.visitFrame(type, numLocal, local, numStack, stack);
  }

  @Override
  public void visitInsn(int opcode) {
    super.visitInsn(opcode);
  }

  @Override
  public void visitIntInsn(int opcode, int operand) {
    super.visitIntInsn(opcode, operand);
  }

  @Override
  public void visitVarInsn(int opcode, int var) {
    super.visitVarInsn(opcode, var);
  }

  @Override
  public void visitTypeInsn(int opcode, String type) {
    artifactContainer.addOwner(type);
    super.visitTypeInsn(opcode, type);
  }

  @Override
  public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
    artifactContainer.addOwner(owner);
    artifactContainer.addDescriptor(descriptor);
    super.visitFieldInsn(opcode, owner, name, descriptor);
  }

  @Override
  public void visitMethodInsn(
          int opcode, String owner, String name, String descriptor, boolean isInterface) {
    artifactContainer.addOwner(owner);
    artifactContainer.addDescriptor(descriptor);
    super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
  }

  @Override
  public void visitInvokeDynamicInsn(
          String name, String descriptor,
          Handle bootstrapMethodHandle, Object... bootstrapMethodArguments) {
    artifactContainer.addDescriptor(descriptor);
    artifactContainer.addClass(bootstrapMethodHandle.getOwner());
    artifactContainer.addDescriptor(bootstrapMethodHandle.getDesc());

    // TODO: look into bootstrapMethodArguments too
    super.visitInvokeDynamicInsn(name, descriptor, bootstrapMethodHandle, bootstrapMethodArguments);
  }

  @Override
  public void visitJumpInsn(int opcode, Label label) {
    super.visitJumpInsn(opcode, label);
  }

  @Override
  public void visitLabel(Label label) {
    super.visitLabel(label);
  }

  @Override
  public void visitLdcInsn(Object value) {
    super.visitLdcInsn(value);
  }

  @Override
  public void visitIincInsn(int var, int increment) {
    super.visitIincInsn(var, increment);
  }

  @Override
  public void visitTableSwitchInsn(int min, int max, Label dflt, Label... labels) {
    super.visitTableSwitchInsn(min, max, dflt, labels);
  }

  @Override
  public void visitLookupSwitchInsn(Label dflt, int[] keys, Label[] labels) {
    super.visitLookupSwitchInsn(dflt, keys, labels);
  }

  @Override
  public void visitMultiANewArrayInsn(String descriptor, int numDimensions) {
    artifactContainer.addDescriptor(descriptor);
    super.visitMultiANewArrayInsn(descriptor, numDimensions);
  }

  @Override
  public AnnotationVisitor visitInsnAnnotation(
          int typeRef, TypePath typePath, String descriptor, boolean visible) {
    artifactContainer.addDescriptor(descriptor);
    return annotationVisitor;
  }

  @Override
  public void visitTryCatchBlock(Label start, Label end, Label handler, String type) {
    if (type != null) {
      artifactContainer.addClass(type);
    }
    super.visitTryCatchBlock(start, end, handler, type);
  }

  @Override
  public AnnotationVisitor visitTryCatchAnnotation(
          int typeRef, TypePath typePath, String descriptor, boolean visible) {
    artifactContainer.addDescriptor(descriptor);
    return annotationVisitor;
  }

  @Override
  public void visitLocalVariable(
          String name, String descriptor, String signature,
          Label start, Label end, int index) {
    artifactContainer.addDescriptor(descriptor);
    super.visitLocalVariable(name, descriptor, signature, start, end, index);
  }

  @Override
  public AnnotationVisitor visitLocalVariableAnnotation(
          int typeRef, TypePath typePath, Label[] start, Label[] end, int[] index,
          String descriptor, boolean visible) {
    artifactContainer.addDescriptor(descriptor);
    return annotationVisitor;
  }

  @Override
  public void visitLineNumber(int line, Label start) {
    super.visitLineNumber(line, start);
  }

  @Override
  public void visitMaxs(int maxStack, int maxLocals) {
    super.visitMaxs(maxStack, maxLocals);
  }

}
