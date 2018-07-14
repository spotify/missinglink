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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.spotify.missinglink.datamodel.Artifact;
import com.spotify.missinglink.datamodel.ArtifactBuilder;
import com.spotify.missinglink.datamodel.ArtifactName;
import com.spotify.missinglink.datamodel.access.FieldAccess;
import com.spotify.missinglink.datamodel.access.FieldAccessBuilder;
import com.spotify.missinglink.datamodel.access.MethodCall;
import com.spotify.missinglink.datamodel.access.MethodCallBuilder;
import com.spotify.missinglink.datamodel.state.DeclaredClass;
import com.spotify.missinglink.datamodel.state.DeclaredClassBuilder;
import com.spotify.missinglink.datamodel.state.DeclaredField;
import com.spotify.missinglink.datamodel.state.DeclaredFieldBuilder;
import com.spotify.missinglink.datamodel.state.DeclaredMethod;
import com.spotify.missinglink.datamodel.state.DeclaredMethodBuilder;
import com.spotify.missinglink.datamodel.type.ClassTypeDescriptor;
import com.spotify.missinglink.datamodel.type.FieldDescriptorBuilder;
import com.spotify.missinglink.datamodel.type.MethodDescriptor;
import com.spotify.missinglink.datamodel.type.MethodDescriptorBuilder;
import com.spotify.missinglink.datamodel.type.TypeDescriptor;
import com.spotify.missinglink.datamodel.type.TypeDescriptors;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Created by axel on 04/06/15.
 */
public class Simple {

  public static final String INT = "I";
  public static final String VOID = "V";
  public static final String STRING = "Ljava/lang/String;";
  public static final String OBJECT = "Ljava/lang/Object;";

  public static String array(String of) {
    return "[" + of;
  }


  /**
   * Empty new DeclaredClass, all collections filled in with empty values
   * @param className
   * @return
   */
  public static DeclaredClassBuilder newClass(String className) {
    return new DeclaredClassBuilder()
        .className(TypeDescriptors.fromClassName(className))
        .parents(ImmutableSet.of())
        .methods(ImmutableMap.of())
        .fields(ImmutableSet.<DeclaredField>of());
  }


  public static DeclaredMethodBuilder newMethod(
      boolean isStatic, String returnDesc, String className, String name, String... parameterDesc) {
    List<TypeDescriptor> param = ImmutableList.copyOf(parameterDesc).stream()
        .map(TypeDescriptors::fromRaw)
        .collect(Collectors.toList());

    return new DeclaredMethodBuilder()
        .owner(TypeDescriptors.fromClassName(className))
        .fieldAccesses(ImmutableSet.<FieldAccess>of())
        .methodCalls(ImmutableSet.<MethodCall>of())
        .descriptor(new MethodDescriptorBuilder()
            .isStatic(isStatic)
            .name(name)
            .parameterTypes(ImmutableList.copyOf(param))
            .returnType(TypeDescriptors.fromRaw(returnDesc)).build());
  }

  public static MethodCall newCall(DeclaredMethod method, boolean isStatic, boolean isVirtual) {
    return newCall(method);
  }

  public static MethodCall newCall(DeclaredMethod method) {
    return new MethodCallBuilder()
        .owner(method.owner())
        .descriptor(method.descriptor())
        .build();
  }

  public static ImmutableMap<ClassTypeDescriptor, DeclaredClass> classMap(
      DeclaredClass... classes) {
    ImmutableMap.Builder<ClassTypeDescriptor, DeclaredClass> builder = ImmutableMap.builder();
    for (DeclaredClass clazz : classes) {
      builder.put(clazz.className(), clazz);
    }
    return builder.build();
  }

  public static ImmutableMap<MethodDescriptor, DeclaredMethod> methodMap(
      DeclaredMethod... methods) {
    ImmutableMap.Builder<MethodDescriptor, DeclaredMethod> builder = ImmutableMap.builder();
    for (DeclaredMethod method: methods) {
      builder.put(method.descriptor(), method);
    }
    return builder.build();
  }

  public static DeclaredField newField(String desc, String name, boolean isStatic) {
    return new DeclaredFieldBuilder()
        .descriptor(new FieldDescriptorBuilder()
            .isStatic(isStatic)
            .name(name)
            .fieldType(TypeDescriptors.fromRaw(desc))
            .build())
        .build();
  }

  public static FieldAccess newAccess(
      String desc, String name, String owner, boolean isStatic, int lineNumber) {
    return new FieldAccessBuilder()
        .owner(TypeDescriptors.fromClassName(owner))
        .descriptor(new FieldDescriptorBuilder()
            .name(name)
            .fieldType(TypeDescriptors.fromRaw(desc))
            .isStatic(isStatic)
            .build())
        .lineNumber(lineNumber)
        .build();
  }

  public static Artifact newArtifact(String name, DeclaredClass... classes) {
    final ImmutableMap.Builder<ClassTypeDescriptor, DeclaredClass> builder = ImmutableMap.builder();
    for (DeclaredClass clazz : classes) {
      builder.put(clazz.className(), clazz);
    }

    return new ArtifactBuilder()
        .name(new ArtifactName(name))
        .classes(builder.build())
        .build();
  }

}
