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

import com.spotify.missinglink.datamodel.AccessedField;
import com.spotify.missinglink.datamodel.AccessedFieldBuilder;
import com.spotify.missinglink.datamodel.Artifact;
import com.spotify.missinglink.datamodel.ArtifactBuilder;
import com.spotify.missinglink.datamodel.ArtifactName;
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
        .loadedClasses(ImmutableSet.of())
        .methods(ImmutableMap.of())
        .fields(ImmutableSet.<DeclaredField>of());
  }


  public static DeclaredMethodBuilder newMethod(boolean isStatic, String returnDesc, String name,
                                                String... parameterDesc) {
    List<TypeDescriptor> param = ImmutableList.copyOf(parameterDesc).stream()
        .map(TypeDescriptors::fromRaw)
        .collect(Collectors.toList());

    return new DeclaredMethodBuilder()
        .isStatic(isStatic)
        .fieldAccesses(ImmutableSet.<AccessedField>of())
        .methodCalls(ImmutableSet.<CalledMethod>of())
        .descriptor(new MethodDescriptorBuilder()
            .name(name)
        .parameterTypes(ImmutableList.copyOf(param))
        .returnType(TypeDescriptors.fromRaw(returnDesc)).build());
  }

  public static CalledMethod newCall(
          DeclaredClass owner, DeclaredMethod method,
          boolean isStatic, boolean isVirtual) {
    return new CalledMethodBuilder()
        .owner(owner.className())
        .descriptor(method.descriptor())
        .isStatic(isStatic)
        .isVirtual(isVirtual)
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

  public static DeclaredField newField(String desc, String name) {
    return new DeclaredFieldBuilder()
        .name(name)
        .descriptor(TypeDescriptors.fromRaw(desc))
        .build();
  }

  public static AccessedField newAccess(String desc, String name, String owner, int lineNumber) {
    return new AccessedFieldBuilder()
        .name(name)
        .descriptor(TypeDescriptors.fromRaw(desc))
        .owner(TypeDescriptors.fromClassName(owner))
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
