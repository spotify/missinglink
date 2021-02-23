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


import static com.spotify.missinglink.Simple.INT;
import static com.spotify.missinglink.Simple.VOID;
import static com.spotify.missinglink.Simple.classMap;
import static com.spotify.missinglink.Simple.methodMap;
import static com.spotify.missinglink.Simple.newCall;
import static com.spotify.missinglink.Simple.newClass;
import static com.spotify.missinglink.Simple.newField;
import static com.spotify.missinglink.Simple.newMethod;
import static org.junit.Assert.assertEquals;

import com.spotify.missinglink.datamodel.ClassTypeDescriptor;
import com.spotify.missinglink.datamodel.DeclaredClass;
import com.spotify.missinglink.datamodel.DeclaredMethod;
import com.spotify.missinglink.datamodel.MethodDescriptor;
import com.spotify.missinglink.datamodel.TypeDescriptor;
import com.spotify.missinglink.datamodel.TypeDescriptors;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.Test;

public class ReachableTest {

  @Test
  public void testUnreachable() {
    DeclaredClass root = newClass("my/Root").build();
    Set<DeclaredClass> myClasses = Collections.singleton(root);
    Map<ClassTypeDescriptor, DeclaredClass> world =
        classMap(root, newClass("other/Unknown").build());
    Set<TypeDescriptor> reachable = ConflictChecker.reachableFrom(myClasses, world);
    assertEquals(Collections.singleton(root.className()), reachable);
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
                    Collections.singleton(newCall(remote, remoteMethod, false, true))).build()))
        .build();
    Set<DeclaredClass> myClasses = Collections.singleton(root);
    Map<ClassTypeDescriptor, DeclaredClass> world = classMap(root, remote);
    Set<TypeDescriptor> reachable = ConflictChecker.reachableFrom(myClasses, world);
    Set<ClassTypeDescriptor> expected = new HashSet<>(Arrays.asList(
        root.className(), remote.className()
    ));
    assertEquals(expected, reachable);
  }

  @Test
  public void testReachableViaInheritance() {
    DeclaredClass remote = newClass("other/Unknown")
        .build();
    DeclaredClass root = newClass("my/Root")
        .parents(Collections.singleton(TypeDescriptors.fromClassName("other/Unknown")))
        .build();
    Set<DeclaredClass> myClasses = Collections.singleton(root);
    Map<ClassTypeDescriptor, DeclaredClass> world = classMap(root, remote);
    Set<TypeDescriptor> reachable = ConflictChecker.reachableFrom(myClasses, world);
    Set<ClassTypeDescriptor> expected = new HashSet<>(Arrays.asList(
        root.className(), remote.className()
    ));
    assertEquals(expected, reachable);
  }

  @Test
  public void testReachableViaLdcLoad() {
    DeclaredClass remote = newClass("other/Unknown")
        .build();
    DeclaredClass root = newClass("my/Root")
        .loadedClasses(Collections.singleton(TypeDescriptors.fromClassName("other/Unknown")))
        .build();
    Set<DeclaredClass> myClasses = Collections.singleton(root);
    Map<ClassTypeDescriptor, DeclaredClass> world = classMap(root, remote);
    Set<TypeDescriptor> reachable = ConflictChecker.reachableFrom(myClasses, world);
    Set<ClassTypeDescriptor> expected = new HashSet<>(Arrays.asList(
        root.className(), remote.className()
    ));
    assertEquals(expected, reachable);
  }

  @Test
  public void testReachableViaField() {
    DeclaredClass remote = newClass("other/Unknown")
        .fields(Collections.singleton(newField(INT, "remoteField")))
        .build();
    Map<MethodDescriptor, DeclaredMethod> methods = methodMap(
        newMethod(false, VOID, "foo")
            .fieldAccesses(
                Collections.singleton(Simple.newAccess(INT, "remoteField", "other/Unknown", 12)))
            .build());
    DeclaredClass root = newClass("my/Root")
        .methods(methods)
        .build();
    Set<DeclaredClass> myClasses = Collections.singleton(root);
    Map<ClassTypeDescriptor, DeclaredClass> world = classMap(root, remote);
    Set<TypeDescriptor> reachable = ConflictChecker.reachableFrom(myClasses, world);
    Set<ClassTypeDescriptor> expected = new HashSet<>(Arrays.asList(
        root.className(), remote.className()
    ));
    assertEquals(expected, reachable);
  }

}
