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

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;

import com.spotify.missinglink.datamodel.AccessedField;
import com.spotify.missinglink.datamodel.AccessedFieldBuilder;
import com.spotify.missinglink.datamodel.CalledMethod;
import com.spotify.missinglink.datamodel.CalledMethodBuilder;
import com.spotify.missinglink.datamodel.ClassTypeDescriptor;
import com.spotify.missinglink.datamodel.DeclaredClass;
import com.spotify.missinglink.datamodel.DeclaredClassBuilder;
import com.spotify.missinglink.datamodel.DeclaredField;
import com.spotify.missinglink.datamodel.DeclaredFieldBuilder;
import com.spotify.missinglink.datamodel.DeclaredMethod;
import com.spotify.missinglink.datamodel.DeclaredMethodBuilder;
import com.spotify.missinglink.datamodel.MethodDescriptor;
import com.spotify.missinglink.datamodel.MethodDescriptors;
import com.spotify.missinglink.datamodel.TypeDescriptors;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.LineNumberNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static java.util.stream.Collectors.toList;

/**
 * TODO: document!
 */
public class ClassLoader {

  private final InputStream in;

  public ClassLoader(InputStream in) {
    this.in = in;
  }

  public DeclaredClass load() throws IOException {
    ClassReader reader = new ClassReader(in);
    DeclaredClassBuilder builder = new DeclaredClassBuilder();

    final String className = reader.getClassName();
    builder.className(TypeDescriptors.fromClassName(className));
    final ClassNode classNode = new ClassNode();
    reader.accept(classNode, 0);

    ImmutableSet.Builder<DeclaredField> fields = new ImmutableSet.Builder<>();

    @SuppressWarnings("unchecked")
    final Iterable<FieldNode> classFields = (Iterable<FieldNode>) classNode.fields;
    for (FieldNode field : classFields) {
      fields.add(new DeclaredFieldBuilder()
          .name(field.name)
          .descriptor(TypeDescriptors.fromRaw(field.desc))
          .build());
    }

    final Map<MethodDescriptor, DeclaredMethod> declaredMethods = Maps.newHashMap();

    for (MethodNode method : ClassLoader.<MethodNode>uncheckedCast(classNode.methods)) {
      // ... and the InsnList type looks like a java.util.List but is not one because why not?
      final Set<CalledMethod> thisCalls = new HashSet<>();
      final Set<AccessedField> thisFields = new HashSet<>();

      int lineNumber = 0;
      for (Iterator<AbstractInsnNode> it2 =
           ClassLoader.<AbstractInsnNode>uncheckedCast(method.instructions.iterator());
           it2.hasNext();) {
        final AbstractInsnNode insn = it2.next();
        if (insn instanceof LineNumberNode) {
          lineNumber = ((LineNumberNode) insn).line;
        }
        if (insn instanceof MethodInsnNode) {
          final MethodInsnNode minsn = (MethodInsnNode) insn;
          boolean isStatic;
          boolean isVirtual;
          switch (minsn.getOpcode()) {
            case Opcodes.INVOKEVIRTUAL:
            case Opcodes.INVOKEINTERFACE:
              isVirtual = true;
              isStatic = false;
              break;
            case Opcodes.INVOKESPECIAL:
              isVirtual = false;
              isStatic = false;
              break;
            case Opcodes.INVOKESTATIC:
              isVirtual = false;
              isStatic = true;
              break;
            default:
              throw new RuntimeException("Unexpected method call opcode: " + minsn.getOpcode());
          }
          if (minsn.owner.charAt(0) != '[') {
            thisCalls.add(new CalledMethodBuilder()
                .owner(TypeDescriptors.fromClassName(minsn.owner))
                .descriptor(MethodDescriptors.fromDesc(minsn.desc, minsn.name))
                .isStatic(isStatic)
                .isVirtual(isVirtual)
                .lineNumber(lineNumber)
                .build());

          }
        }
        if (insn instanceof FieldInsnNode) {
          final FieldInsnNode finsn = (FieldInsnNode) insn;
          if (finsn.owner.charAt(0) != '[') {
            thisFields.add(
                new AccessedFieldBuilder()
                    .name(finsn.name)
                    .descriptor(TypeDescriptors.fromRaw(finsn.desc))
                    .owner(TypeDescriptors.fromClassName(finsn.owner))
                    .lineNumber(lineNumber)
                    .build());
          }
        }
      }

      final DeclaredMethod declaredMethod = new DeclaredMethodBuilder()
          .descriptor(MethodDescriptors.fromDesc(method.desc, method.name))
          .methodCalls(ImmutableSet.copyOf(thisCalls))
          .fieldAccesses(ImmutableSet.copyOf(thisFields))
          .isStatic((method.access & Opcodes.ACC_STATIC) != 0)
          .build();

      if (declaredMethods.put(declaredMethod.descriptor(), declaredMethod) != null) {
        throw new RuntimeException(
            "Multiple definitions of " + declaredMethod.descriptor() + " in class " + className);
      }
    }

    final Set<ClassTypeDescriptor> parents = new HashSet<>();
    parents.addAll(ClassLoader.<String>uncheckedCast(classNode.interfaces)
        .stream()
        .map(TypeDescriptors::fromClassName)
        .collect(toList()));
    // java/lang/Object has no superclass
    if (classNode.superName != null) {
      parents.add(TypeDescriptors.fromClassName(classNode.superName));
    }

    builder.methods(ImmutableMap.copyOf(declaredMethods))
        .parents(ImmutableSet.copyOf(parents))
        .fields(fields.build());

    return builder.build();
  }

  // asm seems to compile it's code with a very low source version, so all collections from it
  // are unchecked types. These helper functions at least suppress the warnings for us:
  //
  @SuppressWarnings("unchecked")
  private static <T> List<T> uncheckedCast(List list) {
    return (List<T>) list;
  }

  @SuppressWarnings("unchecked")
  private static <T> Iterator<T> uncheckedCast(Iterator iterator) {
    return (Iterator<T>) iterator;
  }

}
