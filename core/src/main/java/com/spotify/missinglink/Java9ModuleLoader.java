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
 * Copyright (c) 2019 Spotify AB
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

import com.spotify.missinglink.datamodel.Artifact;
import com.spotify.missinglink.datamodel.ArtifactBuilder;
import com.spotify.missinglink.datamodel.ArtifactName;
import com.spotify.missinglink.datamodel.ClassTypeDescriptor;
import com.spotify.missinglink.datamodel.DeclaredClass;
import com.spotify.missinglink.datamodel.TypeDescriptors;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Java9ModuleLoader {

  public static List<Artifact> getJava9ModuleArtifacts(BiConsumer<String, Exception> log) {
    List<Artifact> artifacts = new ArrayList<>();
    try {

      final Class moduleFinderClass = Class.forName("java.lang.module.ModuleFinder");
      final Object systemModuleFinder = moduleFinderClass.getMethod("ofSystem").invoke(null);
      final Set moduleReferences =
          (Set) moduleFinderClass.getMethod("findAll").invoke(systemModuleFinder);
      final Class moduleReferenceClass = Class.forName("java.lang.module.ModuleReference");
      final Class moduleReaderClass = Class.forName("java.lang.module.ModuleReader");
      for (final Object moduleReference : moduleReferences) {
        final Object descriptor =
            moduleReferenceClass.getMethod("descriptor").invoke(moduleReference);
        final String moduleName =
            String.valueOf(descriptor.getClass().getMethod("name").invoke(descriptor));
        Object reader = moduleReferenceClass.getMethod("open").invoke(moduleReference);
        try {
          final ArtifactName name = new ArtifactName(moduleName);
          Map<ClassTypeDescriptor, DeclaredClass> classes = new HashMap<>();
          final List<String> readerList =
              ((Stream<String>) moduleReaderClass.getMethod("list").invoke(reader))
                  .filter(className -> className.endsWith(".class"))
                  .collect(Collectors.toList());

          for (String className : readerList) {
            final Optional<InputStream> opened =
                (Optional<InputStream>)
                    moduleReaderClass.getMethod("open", String.class).invoke(reader, className);
            if (!opened.isPresent()) {
              continue;
            }
            try (InputStream inputStream = opened.get()) {
              DeclaredClass declaredClass = ClassLoader.load(inputStream);
              classes.put(TypeDescriptors.fromClassName(className), declaredClass);
            } catch (Exception e) {
              log.accept("Could not read class " + className, e);
            }
          }
          artifacts.add(new ArtifactBuilder().name(name).classes(classes).build());
        } finally {
          try {
            moduleReaderClass.getMethod("close").invoke(reader);
          } catch (InvocationTargetException
              | NoSuchMethodException
              | SecurityException
              | IllegalAccessException e) {
            log.accept("Could not close reader", e);
          }
        }
      }
    } catch (InvocationTargetException
        | NoSuchMethodException
        | IllegalAccessException
        | ClassNotFoundException e) {
      log.accept("Could not read java 9 modules", e);
    }
    return artifacts;
  }
}
