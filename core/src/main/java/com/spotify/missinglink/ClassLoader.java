/*-
 * -\-\-
 * missinglink-core
 * --
 * Copyright (C) 2016 - 2021 Spotify AB
 * --
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * -/-/-
 */

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
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.LineNumberNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TryCatchBlockNode;

/** Loads a single class from an input stream. */
public final class ClassLoader {

  // This is a set of classes that is using @HotSpotIntrinsicCandidate
  // and thus define native methods that don't actually exist in the class file
  // This could be removed if we stop loading the full JDK
  private static final Set<String> BLACKLIST =
      new HashSet<>(Arrays.asList("java/lang/invoke/MethodHandle"));

  private ClassLoader() {
    // prevent instantiation
  }

  public static DeclaredClass load(InputStream in) throws IOException {
    ClassNode classNode = readClassNode(in);

    Set<ClassTypeDescriptor> parents = readParents(classNode);
    Set<DeclaredField> declaredFields = readDeclaredFields(classNode);

    Map<MethodDescriptor, DeclaredMethod> declaredMethods = new HashMap<>();
    Set<ClassTypeDescriptor> loadedClasses = new HashSet<>();

    for (MethodNode method : classNode.methods) {
      analyseMethod(classNode.name, method, declaredMethods, loadedClasses);
    }

    return new DeclaredClassBuilder()
        .className(TypeDescriptors.fromClassName(classNode.name))
        .methods(declaredMethods)
        .parents(parents)
        .loadedClasses(loadedClasses)
        .fields(declaredFields)
        .build();
  }

  private static ClassNode readClassNode(InputStream in) throws IOException {
    final ClassNode classNode = new ClassNode();
    ClassReader reader = new ClassReader(in);
    reader.accept(classNode, 0);
    return classNode;
  }

  private static Set<ClassTypeDescriptor> readParents(ClassNode classNode) {
    final Set<ClassTypeDescriptor> parents =
        classNode.interfaces.stream()
            .map(TypeDescriptors::fromClassName)
            .collect(Collectors.toSet());
    // java/lang/Object has no superclass
    if (classNode.superName != null) {
      parents.add(TypeDescriptors.fromClassName(classNode.superName));
    }
    return parents;
  }

  private static Set<DeclaredField> readDeclaredFields(ClassNode classNode) {
    Set<DeclaredField> fields = new HashSet<>();

    final Iterable<FieldNode> classFields = classNode.fields;
    for (FieldNode field : classFields) {
      fields.add(
          new DeclaredFieldBuilder()
              .name(field.name)
              .descriptor(TypeDescriptors.fromRaw(field.desc))
              .build());
    }
    return fields;
  }

  private static void analyseMethod(
      String className,
      MethodNode method,
      Map<MethodDescriptor, DeclaredMethod> declaredMethods,
      Set<ClassTypeDescriptor> loadedClasses) {
    final Set<CalledMethod> thisCalls = new HashSet<>();
    final Set<AccessedField> thisFields = new HashSet<>();

    int lineNumber = 0;
    final List<AbstractInsnNode> instructions = toList(method.instructions.iterator());

    for (final AbstractInsnNode insn : instructions) {
      try {
        if (insn instanceof LineNumberNode) {
          lineNumber = ((LineNumberNode) insn).line;
        }
        if (insn instanceof MethodInsnNode) {
          handleMethodCall(
              thisCalls,
              lineNumber,
              (MethodInsnNode) insn,
              getTryCatchBlocksProtecting(instructions, insn, method));
        }
        if (insn instanceof FieldInsnNode) {
          handleFieldAccess(
              thisFields,
              lineNumber,
              (FieldInsnNode) insn,
              getTryCatchBlocksProtecting(instructions, insn, method));
        }
        if (insn instanceof LdcInsnNode) {
          handleLdc(loadedClasses, (LdcInsnNode) insn);
        }
      } catch (Exception e) {
        throw new MissingLinkException(
            "Error analysing " + className + "." + method.name + ", line: " + lineNumber, e);
      }
    }

    final DeclaredMethod declaredMethod =
        new DeclaredMethodBuilder()
            .descriptor(MethodDescriptors.fromDesc(method.desc, method.name))
            .lineNumber(lineNumber)
            .methodCalls(thisCalls)
            .fieldAccesses(thisFields)
            .isStatic((method.access & Opcodes.ACC_STATIC) != 0)
            .build();

    if (declaredMethods.put(declaredMethod.descriptor(), declaredMethod) != null) {
      throw new RuntimeException(
          "Multiple definitions of " + declaredMethod.descriptor() + " in class " + className);
    }
  }

  private static <T> List<T> toList(final ListIterator<T> iterator) {
    List<T> list = new ArrayList<T>();
    while (iterator.hasNext()) {
      list.add(iterator.next());
    }
    return list;
  }

  private static List<TryCatchBlockNode> getTryCatchBlocksProtecting(
      final List<AbstractInsnNode> instructions,
      final AbstractInsnNode insn,
      final MethodNode method) {

    final List<TryCatchBlockNode> protectedByTryCatches = new ArrayList<>();
    final int instructionIndex = instructions.indexOf(insn);
    for (final TryCatchBlockNode tryCatchBlockNode : method.tryCatchBlocks) {
      if (tryCatchBlockNode.type == null) {
        continue;
      }
      final int catchStartIndex = instructions.indexOf(tryCatchBlockNode.start);
      final int catchEndIndex = instructions.indexOf(tryCatchBlockNode.end);
      if (instructionIndex > catchStartIndex && instructionIndex < catchEndIndex) {
        protectedByTryCatches.add(tryCatchBlockNode);
      }
    }
    return protectedByTryCatches;
  }

  private static void handleMethodCall(
      final Set<CalledMethod> thisCalls,
      final int lineNumber,
      final MethodInsnNode insn,
      final List<TryCatchBlockNode> tryCatchBlocksProtecting) {
    boolean isStatic;
    switch (insn.getOpcode()) {
      case Opcodes.INVOKEVIRTUAL:
      case Opcodes.INVOKEINTERFACE:
        isStatic = false;
        break;
      case Opcodes.INVOKESPECIAL:
        isStatic = false;
        break;
      case Opcodes.INVOKESTATIC:
        isStatic = true;
        break;
      default:
        throw new RuntimeException("Unexpected method call opcode: " + insn.getOpcode());
    }
    if (isArray(insn.owner) && !BLACKLIST.contains(insn.owner)) {
      thisCalls.add(
          new CalledMethodBuilder()
              .owner(TypeDescriptors.fromClassName(insn.owner))
              .descriptor(MethodDescriptors.fromDesc(insn.desc, insn.name))
              .isStatic(isStatic)
              .lineNumber(lineNumber)
              .caughtExceptions(
                  tryCatchBlocksProtecting.stream()
                      .map(node -> TypeDescriptors.fromClassName(node.type))
                      .collect(Collectors.toList()))
              .build());
    }
  }

  private static void handleFieldAccess(
      Set<AccessedField> thisFields,
      int lineNumber,
      FieldInsnNode insn,
      final List<TryCatchBlockNode> tryCatchBlocksProtecting) {
    if (isArray(insn.owner)) {
      thisFields.add(
          new AccessedFieldBuilder()
              .name(insn.name)
              .descriptor(TypeDescriptors.fromRaw(insn.desc))
              .owner(TypeDescriptors.fromClassName(insn.owner))
              .lineNumber(lineNumber)
              .caughtExceptions(
                  tryCatchBlocksProtecting.stream()
                      .map(node -> TypeDescriptors.fromClassName(node.type))
                      .collect(Collectors.toList()))
              .build());
    }
  }

  private static boolean isArray(String owner) {
    return owner.charAt(0) != '[';
  }

  private static void handleLdc(Set<ClassTypeDescriptor> loadedClasses, LdcInsnNode insn) {
    // See http://docs.oracle.com/javase/specs/jvms/se8/html/jvms-6.html#jvms-6.5.ldc
    // if an LDC instruction is emitted with a symbolic reference to a class, that class is
    // loaded. This means we need to at least check for presence of that class, and also
    // validate its static initialisation code, if any. It would probably be safe for some
    // future to ignore other methods defined by the class.
    if (insn.cst instanceof Type) {
      Type type = (Type) insn.cst;

      Type loadedType = type;

      if (type.getSort() == Type.ARRAY) {
        loadedType = type.getElementType();
      }

      if (loadedType.getSort() == Type.OBJECT) {
        loadedClasses.add(TypeDescriptors.fromClassName(loadedType.getInternalName()));
      }
    }
  }
}
