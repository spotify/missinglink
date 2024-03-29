/*-
 * -\-\-
 * missinglink-maven-plugin
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
package com.spotify.missinglink.maven;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.base.Preconditions;

/**
 * Package names to be ignored when reporting conflicts. A package name is a String like
 * "javax.servlet" (not a regular expression). By default, subpackages of the specified package are
 * also ignored - to disable this behavior, set filterSubpackages to false.
 */
public class PackageFilter {

  // Plexus seems to require classes to be referenced in the Maven project model to have no-arg
  // constructors and getters/setters.

  private String name;
  private boolean filterSubpackages = true;

  public PackageFilter() {}

  public PackageFilter(String name, boolean filterSubpackages) {
    this.name = checkNotNull(name);
    Preconditions.checkArgument(!name.isEmpty(), "name cannot be empty");
    this.filterSubpackages = filterSubpackages;
  }

  public String getPackage() {
    return name;
  }

  // lets the xml field be called "<package>"
  public void setPackage(String name) {
    this.name = name;
  }

  public boolean isFilterSubpackages() {
    return filterSubpackages;
  }

  public void setFilterSubpackages(boolean filterSubpackages) {
    this.filterSubpackages = filterSubpackages;
  }

  public void setIgnoreSubpackages(boolean ignoreSubpackages) {
    this.filterSubpackages = ignoreSubpackages;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    PackageFilter that = (PackageFilter) o;

    if (filterSubpackages != that.filterSubpackages) {
      return false;
    }
    return name.equals(that.name);
  }

  @Override
  public int hashCode() {
    int result = name.hashCode();
    result = 31 * result + (filterSubpackages ? 1 : 0);
    return result;
  }

  @Override
  public String toString() {
    return "IgnoredPackage{"
        + "name='"
        + name
        + '\''
        + ", filterSubpackages="
        + filterSubpackages
        + '}';
  }
}
