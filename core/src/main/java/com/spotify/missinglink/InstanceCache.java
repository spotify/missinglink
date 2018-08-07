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

import com.spotify.missinglink.datamodel.type.ClassTypeDescriptor;
import com.spotify.missinglink.datamodel.type.FieldDescriptor;
import com.spotify.missinglink.datamodel.type.FieldDescriptors;
import com.spotify.missinglink.datamodel.type.MethodDescriptor;
import com.spotify.missinglink.datamodel.type.MethodDescriptors;
import com.spotify.missinglink.datamodel.type.TypeDescriptor;
import com.spotify.missinglink.datamodel.type.TypeDescriptors;

/**
 * This class manages the caches for various descriptors to avoid double-storing the same
 * type/descriptor. Note that it would be perfectly valid to use different caches for the
 * same artifact; the only consequence should be higher memory usage. Don't use reference
 * equality to compare the returned types/descriptors.
 */
public class InstanceCache {
  private final FieldDescriptors fieldDescriptors;
  private final MethodDescriptors methodDescriptors;
  private final TypeDescriptors typeDescriptors;

  public InstanceCache() {
    this.fieldDescriptors = new FieldDescriptors();
    this.methodDescriptors = new MethodDescriptors();
    this.typeDescriptors = new TypeDescriptors();
  }

  public FieldDescriptor fieldFromDesc(String desc, String name, int access) {
    return fieldDescriptors.fromDesc(this, desc, name, access);
  }
  public FieldDescriptor fieldFromDesc(String desc, String name, boolean isStatic) {
    return fieldDescriptors.fromDesc(this, desc, name, isStatic);
  }
  public MethodDescriptor methodFromDesc(String desc, String name, int access) {
    return methodDescriptors.fromDesc(this, desc, name, access);
  }
  public MethodDescriptor methodFromDesc(String desc, String name, boolean isStatic) {
    return methodDescriptors.fromDesc(this, desc, name, isStatic);
  }
  public TypeDescriptor typeFromRaw(String raw) {
    return typeDescriptors.fromRaw(raw);
  }
  public ClassTypeDescriptor typeFromClassName(String className) {
    return typeDescriptors.fromClassName(className);
  }
}
