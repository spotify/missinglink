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

import static java.util.stream.Collectors.toList;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.spotify.missinglink.datamodel.access.FieldAccess;
import com.spotify.missinglink.datamodel.access.MethodCall;
import com.spotify.missinglink.datamodel.state.DeclaredClass;
import com.spotify.missinglink.datamodel.state.DeclaredClassBuilder;
import com.spotify.missinglink.datamodel.state.DeclaredField;
import com.spotify.missinglink.datamodel.state.DeclaredMethod;
import com.spotify.missinglink.datamodel.type.ClassTypeDescriptor;
import com.spotify.missinglink.datamodel.type.FieldDescriptor;
import com.spotify.missinglink.datamodel.type.MethodDescriptor;
import com.spotify.missinglink.datamodel.type.TypeDescriptors;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

/**
 * Loads a single class from an input stream.
 */
public final class ClassLoader {

  private ClassLoader() {
    // prevent instantiation
  }

  public static DeclaredClass load(InputStream in) throws IOException {
    ClassNode classNode = readClassNode(in);

    ClassTypeDescriptor descriptor = TypeDescriptors.fromClassName(classNode.name);

    Set<ClassTypeDescriptor> parents = readParents(classNode);
    ImmutableSet<DeclaredField> declaredFields = readDeclaredFields(classNode);

    Map<MethodDescriptor, DeclaredMethod> declaredMethods = Maps.newHashMap();

    for (MethodNode method : ClassLoader.<MethodNode>uncheckedCast(classNode.methods)) {
      analyseMethod(descriptor, classNode.name, method, declaredMethods);
    }

    declaredMethods.putIfAbsent(
        MethodDescriptor.staticInit(),
        DeclaredMethod.emptyStaticInit(descriptor));

    return new DeclaredClassBuilder()
        .className(descriptor)
        .methods(ImmutableMap.copyOf(declaredMethods))
        .parents(ImmutableSet.copyOf(parents))
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
    final Set<ClassTypeDescriptor> parents = new HashSet<>();
    parents.addAll(ClassLoader.<String>uncheckedCast(classNode.interfaces)
                       .stream()
                       .map(TypeDescriptors::fromClassName)
                       .collect(toList()));
    // java/lang/Object has no superclass
    if (classNode.superName != null) {
      parents.add(TypeDescriptors.fromClassName(classNode.superName));
    }
    return parents;
  }

  private static ImmutableSet<DeclaredField> readDeclaredFields(ClassNode classNode) {
    ImmutableSet.Builder<DeclaredField> fields = new ImmutableSet.Builder<>();

    for (FieldNode field : ClassLoader.<FieldNode>uncheckedCast(classNode.fields)) {
      fields.add(DeclaredField.of(FieldDescriptor.fromDesc(field.desc, field.name, field.access)));
    }
    return fields.build();
  }

  private static void analyseMethod(ClassTypeDescriptor descriptor,
                                    String className,
                                    MethodNode method,
                                    Map<MethodDescriptor, DeclaredMethod> declaredMethods) {
    final Set<MethodCall> thisCalls = new HashSet<>();
    final Set<FieldAccess> thisFields = new HashSet<>();

    int lineNumber = 0;
    for (Iterator<AbstractInsnNode> instructions =
         ClassLoader.<AbstractInsnNode>uncheckedCast(method.instructions.iterator());
         instructions.hasNext();) {
      try {
        final AbstractInsnNode insn = instructions.next();
        if (insn instanceof LineNumberNode) {
          lineNumber = ((LineNumberNode) insn).line;
        }
        if (insn instanceof MethodInsnNode) {
          handleMethodCall(thisCalls, lineNumber, (MethodInsnNode) insn);
        }
        if (insn instanceof FieldInsnNode) {
          handleFieldAccess(thisFields, lineNumber, (FieldInsnNode) insn);
        }
        if (insn instanceof LdcInsnNode) {
          handleLdc(thisCalls, lineNumber, (LdcInsnNode) insn);
        }
      } catch (Exception e) {
        throw new MissingLinkException("Error analysing " + className + "." + method.name +
                                       ", line: " + lineNumber, e);
      }
    }

    final DeclaredMethod declaredMethod = DeclaredMethod.of(
        descriptor,
        MethodDescriptor.fromDesc(method.desc, method.name, method.access),
        lineNumber,
        ImmutableSet.copyOf(thisCalls),
        ImmutableSet.copyOf(thisFields));

    if (declaredMethods.put(declaredMethod.descriptor(), declaredMethod) != null) {
      throw new RuntimeException(
          "Multiple definitions of " + declaredMethod.descriptor() + " in class " + className);
    }
  }

  private static void handleMethodCall(Set<MethodCall> thisCalls,
                                       int lineNumber,
                                       MethodInsnNode insn) {
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
    if (insn.owner.charAt(0) != '[') {
      thisCalls.add(MethodCall.of(
          TypeDescriptors.fromClassName(insn.owner),
          MethodDescriptor.fromDesc(insn.desc, insn.name, isStatic),
          lineNumber));
    }
  }

  private static void handleFieldAccess(Set<FieldAccess> thisFields, int lineNumber,
                                        FieldInsnNode insn) {
    if (insn.owner.charAt(0) != '[') {
      thisFields.add(FieldAccess.of(
          TypeDescriptors.fromClassName(insn.owner),
          FieldDescriptor.fromDesc(insn.desc, insn.name,
              insn.getOpcode() == Opcodes.GETSTATIC || insn.getOpcode() == Opcodes.PUTSTATIC),
          lineNumber));
    }
  }

  private static void handleLdc(Set<MethodCall> thisCalls,
      int lineNumber,
      LdcInsnNode insn) {
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
        thisCalls.add(MethodCall.of(
            TypeDescriptors.fromClassName(loadedType.getInternalName()),
            MethodDescriptor.staticInit(),
            lineNumber));
      }
    }
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
