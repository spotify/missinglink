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

import java.io.File;
import java.util.Arrays;

/**
 * helper method for loading a file path relative to this project in a way that works in both
 * IntelliJ and with maven from the CLI.
 */
class FilePathHelper {

  static File getPath(String pathRelativeToProject) {
    final File baseDir;
    final File currentDir = new File(".");
    if (Arrays.asList(currentDir.list()).stream().anyMatch(x -> x.equals("core"))) {
      baseDir = new File(currentDir, "core");
    } else {
      baseDir = currentDir;
    }

    return new File(baseDir, pathRelativeToProject);
  }
}
