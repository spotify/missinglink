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
package com.spotify.missinglink.datamodel;

import java.util.HashMap;
import java.util.InputMismatchException;
import java.util.Map;

public class TypeDescriptors {

  private TypeDescriptors() {
  }

  // Keep a cache of all the created ClassTypeDescriptor instances to prevent unnecessary
  // duplication of instances. ClassTypeDescriptor is immutable once constructed.
  // Since the CTD constructor involves string replacement, and there are several places where we
  // want to turn a String into a ClassTypeDescriptor, we can optimize how many strings are
  // created/replaced with this map.
  private static Map<String, ClassTypeDescriptor> classTypeDescriptorCache = new HashMap<>();

  public static ClassTypeDescriptor fromClassName(String className) {
    return classTypeDescriptorCache.computeIfAbsent(className, ClassTypeDescriptor::new);
  }

  public static TypeDescriptor fromRaw(String raw) {
    final int length = raw.length();

    int dimensions = raw.lastIndexOf('[') + 1;

    final String subType = raw.substring(dimensions);

    final TypeDescriptor simpleType;
    if (subType.equals("V")) {
      simpleType = VoidTypeDescriptor.voidTypeDescriptor;
    } else if (subType.startsWith("L") && subType.endsWith(";")) {
      simpleType = fromClassName(subType.substring(1, length - dimensions - 1));
    } else {
      simpleType = PrimitiveTypeDescriptor.fromRaw(subType);
      if (simpleType == null) {
        throw new InputMismatchException("Invalid type descriptor: " + raw);
      }
    }

    if (dimensions > 0) {
      return new ArrayTypeDescriptor(simpleType, dimensions);
    }
    return simpleType;
  }
}
