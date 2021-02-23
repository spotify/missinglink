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

public class PrettyPrinter {
  public static String className(String className) {
    return className.replace('/', '.');
  }

  public static String typeDescriptor(String typeDescriptor) {
    int arrayLevels = 0;

    StringBuilder sb = new StringBuilder();

    int i = 0;
    for (; i < typeDescriptor.length(); i++) {
      char c = typeDescriptor.charAt(i);
      switch (c) {
        case '[':
          arrayLevels++;
          break;
        case 'L':
          while (true) {
            i++;
            c = typeDescriptor.charAt(i);
            if (c == ';') {
              break;
            }
            if (c == '/') {
              c = '.';
            }
            sb.append(c);
          }
          break;
        case 'V': sb.append("void"); break;
        case 'I': sb.append("int"); break;
        case 'S': sb.append("short"); break;
        case 'Z': sb.append("long"); break;
        case 'B': sb.append("boolean"); break;
        case 'D': sb.append("double"); break;
        case 'F': sb.append("float"); break;
        default:
          // TODO: remove this once we have fixed the semantics for classname vs type descriptor
          return className(typeDescriptor);
          //throw new RuntimeException("Unhandled type: " + typeDescriptor);
      }
    }
    for (int j = 0; j < arrayLevels; j++) {
      sb.append("[]");
    }
    return sb.toString();
  }
}
