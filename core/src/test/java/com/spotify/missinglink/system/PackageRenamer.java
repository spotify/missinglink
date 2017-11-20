package com.spotify.missinglink.system;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.spotify.missinglink.datamodel.AccessedField;
import com.spotify.missinglink.datamodel.AccessedFieldBuilder;
import com.spotify.missinglink.datamodel.Artifact;
import com.spotify.missinglink.datamodel.ArtifactBuilder;
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
import com.spotify.missinglink.datamodel.MethodDescriptorBuilder;
import com.spotify.missinglink.datamodel.TypeDescriptor;
import com.spotify.missinglink.datamodel.TypeDescriptors;
import java.util.Map;

public class PackageRenamer {

  static Artifact renamePackages(
      final String from, final String to,
      final Artifact artifact) {
    return ArtifactBuilder.from(artifact)
        .classes(renameClasses(from, to, artifact.classes()))
        .build();
  }

  private static ImmutableMap<ClassTypeDescriptor, DeclaredClass> renameClasses(
      final String from, final String to,
      final ImmutableMap<ClassTypeDescriptor, DeclaredClass> classes) {
    final ImmutableMap.Builder<ClassTypeDescriptor, DeclaredClass> builder = ImmutableMap.builder();
    for (Map.Entry<ClassTypeDescriptor, DeclaredClass> entry : classes.entrySet()) {
      final ClassTypeDescriptor classTypeDescriptor = entry.getKey();
      final DeclaredClass clazz = entry.getValue();
      final ClassTypeDescriptor newClassName = renameClass(from, to, classTypeDescriptor);
      final DeclaredClass newClass = renameClass(from, to, clazz);
      builder.put(newClassName, newClass);
    }
    return builder.build();
  }

  private static TypeDescriptor renameType(
      final String from, final String to,
      final TypeDescriptor typeDescriptor) {
    if (typeDescriptor instanceof ClassTypeDescriptor) {
      return renameClass(from, to, (ClassTypeDescriptor) typeDescriptor);
    }
    return typeDescriptor;
  }

  private static ClassTypeDescriptor renameClass(
      final String from, final String to,
      final ClassTypeDescriptor typeDescriptor) {
    return TypeDescriptors.fromClassName(typeDescriptor.getClassName().replace(from, to));
  }

  private static DeclaredClass renameClass(final String from, final String to, final DeclaredClass clazz) {
    return DeclaredClassBuilder.from(clazz)
        .className(renameClass(from, to, clazz.className()))
        .fields(renameFields(from, to, clazz.fields()))
        .loadedClasses(renameClassNames(from, to, clazz.loadedClasses()))
        .parents(renameClassNames(from, to, clazz.parents()))
        .methods(renameMethods(from, to, clazz.methods()))
        .build();
  }

  private static ImmutableMap<MethodDescriptor, DeclaredMethod> renameMethods(
      final String from, final String to,
      final ImmutableMap<MethodDescriptor, DeclaredMethod> methods) {
    final ImmutableMap.Builder<MethodDescriptor, DeclaredMethod> builder = ImmutableMap.builder();
    for (Map.Entry<MethodDescriptor, DeclaredMethod> entry : methods.entrySet()) {
      final MethodDescriptor descriptor = entry.getKey();
      final DeclaredMethod method = entry.getValue();
      builder.put(renameMethodDescriptor(from, to, descriptor), renameMethod(from, to, method));
    }
    return builder.build();

  }

  private static DeclaredMethod renameMethod(
      final String from, final String to,
      final DeclaredMethod method) {
    return DeclaredMethodBuilder.from(method)
        .descriptor(renameMethodDescriptor(from, to, method.descriptor()))
        .fieldAccesses(renameFieldAccesses(from, to, method.fieldAccesses()))
        .methodCalls(renameMethodCalls(from, to, method.methodCalls()))
        .build();
  }

  private static ImmutableSet<CalledMethod> renameMethodCalls(
      final String from, final String to,
      final ImmutableSet<CalledMethod> calledMethods) {
    return calledMethods.stream()
        .map(calledMethod -> renameMethodCall(from, to, calledMethod))
        .collect(ImmutableSet.toImmutableSet());
  }

  private static CalledMethod renameMethodCall(
      final String from, final String to,
      final CalledMethod calledMethod) {
    return CalledMethodBuilder.from(calledMethod)
        .descriptor(renameMethodDescriptor(from, to, calledMethod.descriptor()))
        .owner(renameClass(from, to, calledMethod.owner()))
        .build();
  }

  private static ImmutableSet<AccessedField> renameFieldAccesses(
      final String from, final String to,
      final ImmutableSet<AccessedField> accessedFields) {
    return accessedFields.stream()
        .map(accessedField -> renameFieldAccess(from, to, accessedField))
        .collect(ImmutableSet.toImmutableSet());
  }

  private static AccessedField renameFieldAccess(
      final String from, final String to,
      final AccessedField accessedField) {
    return AccessedFieldBuilder.from(accessedField)
        .descriptor(renameType(from, to, accessedField.descriptor()))
        .owner(renameClass(from, to, accessedField.owner()))
        .build();
  }

  private static MethodDescriptor renameMethodDescriptor(
      final String from, final String to,
      final MethodDescriptor descriptor) {
    return MethodDescriptorBuilder.from(descriptor)
        .parameterTypes(renameTypes(from, to, descriptor.parameterTypes()))
        .returnType(renameType(from, to, descriptor.returnType()))
        .build();
  }

  private static ImmutableList<TypeDescriptor> renameTypes(
      final String from, final String to,
      final ImmutableList<TypeDescriptor> typeDescriptors) {
    return typeDescriptors.stream()
        .map(typeDescriptor -> renameType(from, to, typeDescriptor))
        .collect(ImmutableList.toImmutableList());
  }

  private static ImmutableSet<ClassTypeDescriptor> renameClassNames(
      final String from, final String to,
      final ImmutableSet<ClassTypeDescriptor> classTypeDescriptors) {
    return classTypeDescriptors.stream()
        .map(classTypeDescriptor -> renameClass(from, to, classTypeDescriptor))
        .collect(ImmutableSet.toImmutableSet());
  }

  private static ImmutableSet<DeclaredField> renameFields(
      final String from, final String to,
      final ImmutableSet<DeclaredField> fields) {
    return fields.stream()
        .map(declaredField -> renameField(from, to, declaredField))
        .collect(ImmutableSet.toImmutableSet());
  }

  private static DeclaredField renameField(final String from, final String to, final DeclaredField field) {
    return DeclaredFieldBuilder.from(field)
        .descriptor(renameType(from, to, field.descriptor()))
        .build();
  }
}
