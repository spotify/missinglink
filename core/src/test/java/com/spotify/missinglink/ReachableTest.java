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

import com.spotify.missinglink.datamodel.ClassTypeDescriptor;
import com.spotify.missinglink.datamodel.DeclaredClass;
import com.spotify.missinglink.datamodel.DeclaredMethod;
import com.spotify.missinglink.datamodel.MethodDescriptor;
import com.spotify.missinglink.datamodel.TypeDescriptor;
import com.spotify.missinglink.datamodel.TypeDescriptors;

import org.junit.Test;

import java.util.Map;
import java.util.Set;

import static com.spotify.missinglink.Simple.INT;
import static com.spotify.missinglink.Simple.VOID;
import static com.spotify.missinglink.Simple.classMap;
import static com.spotify.missinglink.Simple.methodMap;
import static com.spotify.missinglink.Simple.newCall;
import static com.spotify.missinglink.Simple.newClass;
import static com.spotify.missinglink.Simple.newField;
import static com.spotify.missinglink.Simple.newMethod;
import static org.junit.Assert.assertEquals;

public class ReachableTest {

  @Test
  public void testUnreachable() {
    DeclaredClass root = newClass("my/Root").build();
    ImmutableSet< DeclaredClass > myClasses = ImmutableSet.of(root);
    Map<ClassTypeDescriptor, DeclaredClass> world =
        classMap(root, newClass("other/Unknown").build());
    Set<TypeDescriptor> reachable = ConflictChecker.reachableFrom(myClasses, world);
    assertEquals(ImmutableSet.of(root.className()), reachable);
  }

  @Test
  public void testReachableViaCall() {
    DeclaredMethod remoteMethod = newMethod(false, VOID, "called").build();
    DeclaredClass remote = newClass("other/Unknown")
        .methods(methodMap(remoteMethod))
        .build();
    DeclaredClass root = newClass("my/Root")
        .methods(methodMap(
            newMethod(false, VOID, "foo")
                .methodCalls(
                    ImmutableSet.of(newCall(remote, remoteMethod, false, true))).build()))
        .build();
    ImmutableSet<DeclaredClass> myClasses = ImmutableSet.of(root);
    ImmutableMap<ClassTypeDescriptor, DeclaredClass> world = classMap(root, remote);
    ImmutableSet<TypeDescriptor> reachable = ConflictChecker.reachableFrom(myClasses, world);
    assertEquals(ImmutableSet.of(root.className(), remote.className()), reachable);
  }

  @Test
  public void testReachableViaInheritance() {
    DeclaredClass remote = newClass("other/Unknown")
        .build();
    DeclaredClass root = newClass("my/Root")
        .loadedClasses(ImmutableSet.of(TypeDescriptors.fromClassName("other/Unknown")))
        .build();
    ImmutableSet<DeclaredClass> myClasses = ImmutableSet.of(root);
    ImmutableMap<ClassTypeDescriptor, DeclaredClass> world = classMap(root, remote);
    ImmutableSet<TypeDescriptor> reachable = ConflictChecker.reachableFrom(myClasses, world);
    assertEquals(ImmutableSet.of(root.className(), remote.className()), reachable);
  }

  @Test
  public void testReachableViaField() {
    DeclaredClass remote = newClass("other/Unknown")
        .fields(ImmutableSet.of(newField(INT, "remoteField")))
        .build();
    ImmutableMap<MethodDescriptor, DeclaredMethod> methods = methodMap(
        newMethod(false, VOID, "foo")
            .fieldAccesses(
                ImmutableSet.of(Simple.newAccess(INT, "remoteField", "other/Unknown", 12)))
            .build());
    DeclaredClass root = newClass("my/Root")
        .methods(methods)
        .build();
    ImmutableSet< DeclaredClass > myClasses = ImmutableSet.of(root);
    ImmutableMap<ClassTypeDescriptor, DeclaredClass> world = classMap(root, remote);
    ImmutableSet<TypeDescriptor> reachable = ConflictChecker.reachableFrom(myClasses, world);
    assertEquals(ImmutableSet.of(root.className(), remote.className()), reachable);
  }

}
