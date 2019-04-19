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
package com.spotify.missinglink.maven;

import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import java.io.IOException;
import java.io.Writer;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.json.JSONArray;
import org.json.JSONObject;

public class PackageNode {
  private final TreeMap<String, PackageNode> children = Maps.newTreeMap();
  private final List<JSONObject> conflicts = Lists.newArrayList();
  private final String ignoreKey;
  private final String ignoreTag;

  public PackageNode(final String ignoreKey, final String ignoreTag) {
    this.ignoreKey = ignoreKey;
    this.ignoreTag = ignoreTag;
  }

  public void add(final JSONObject conflict) {
    final String targetClass = conflict.getJSONObject("dependency").getString(ignoreKey);
    final List<String> path = Splitter.on(".").splitToList(targetClass);
    add(conflict, path, this);
  }

  private void add(final JSONObject conflict, final List<String> path, PackageNode packageNode) {
    // Ignore the class name
    final List<String> sublist = path.subList(0, path.size() - 1);
    for (String segment : sublist) {
      packageNode = packageNode.getSubPath(segment);
    }
    packageNode.conflicts.add(conflict);
  }

  private PackageNode getSubPath(final String segment) {
    return children.computeIfAbsent(segment, s -> new PackageNode(ignoreKey, ignoreTag));
  }

  public void shrinkTree() {
    final Set<Map.Entry<String, PackageNode>> entries = children.entrySet();
    for (Map.Entry<String, PackageNode> entry : entries) {
      final String segment = entry.getKey();
      final PackageNode child = entry.getValue();
      shrinkTree(this, segment, child);
    }
  }

  private static void shrinkTree(
      final PackageNode parent, final String segment, final PackageNode child) {
    for (Map.Entry<String, PackageNode> entry : parent.children.entrySet()) {
      shrinkTree(child, entry.getKey(), entry.getValue());
    }
    if (child.children.size() >= 3) {
      for (Map.Entry<String, PackageNode> entry : child.children.entrySet()) {
        parent.children.put(segment + "." + entry.getKey(), entry.getValue());
      }
      parent.conflicts.addAll(child.conflicts);
      child.children.clear();
      child.conflicts.clear();
    }
  }

  public void printIgnores(final Writer writer) throws IOException {
    writer.append("<" + ignoreTag + "s>\n");
    printIgnores(writer, "");
    writer.append("</" + ignoreTag + "s>\n");
  }

  private void printIgnores(final Writer writer, final String path) throws IOException {
    if (!conflicts.isEmpty()) {
      StringBuilder sb = new StringBuilder();
      sb.append("  <").append(ignoreTag).append(">");
      sb.append("<package>").append(prettyPath(path)).append("</package>");
      sb.append("</").append(ignoreTag).append(">");
      sb.append(" <!-- " + conflicts.size() + " conflicts -->\n");
      writer.append(sb.toString());
    }
    final Set<Map.Entry<String, PackageNode>> entries = children.entrySet();
    for (Map.Entry<String, PackageNode> entry : entries) {
      final String segment = entry.getKey();
      final PackageNode child = entry.getValue();
      child.printIgnores(writer, prettyPath(path + "." + segment));
    }
  }

  private String prettyPath(final String path) {
    if (path.startsWith(".")) {
      return path.substring(1);
    }
    return path;
  }

  public static PackageNode fromJSON(
      final JSONArray jsonArray,
      final String ignoreKey,
      final String ignoreTag) {
    final PackageNode root = new PackageNode(ignoreKey, ignoreTag);
    for (Object o : jsonArray) {
      JSONObject o2 = (JSONObject) o;
      root.add(o2);
    }
    return root;
  }

}
