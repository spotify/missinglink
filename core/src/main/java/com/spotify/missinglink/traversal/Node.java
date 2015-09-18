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
package com.spotify.missinglink.traversal;

public abstract class Node {
  protected final Node from;

  protected Node(Node from) {
    this.from = from;
  }

  abstract void validate();

  public abstract String toString();

  public final String stackTrace() {
    StringBuilder sb = new StringBuilder();
    sb.append("Not found:");
    Node node = this;
    int indent = 0;
    while (node != null) {
      for (int i = 0; i < indent; i++) {
        sb.append("  ");
      }
      sb.append(node.toString());
      sb.append("\n");
      indent++;
      node = node.from;
    }
    return sb.toString();
  }
}
