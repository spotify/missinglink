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
package com.spotify.missinglink.maven.reports;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.Lists;
import com.google.common.collect.Ordering;
import com.spotify.missinglink.Conflict;

import java.util.ArrayList;
import java.util.Comparator;

public class ConflictItem {
  @JsonProperty
  private final String fromArtifact;

  @JsonProperty
  private final String fromClass;

  @JsonProperty
  private final String fromMethod;

  @JsonProperty
  @JsonInclude(JsonInclude.Include.NON_NULL)
  private final Integer fromLineNumber;

  @JsonProperty
  private final String toClass;

  @JsonProperty
  private final String reason;

  @JsonProperty
  private final String category;

  public ConflictItem(Conflict conflict) {
    fromArtifact = conflict.usedBy().name();
    fromClass = conflict.dependency().fromClass().getClassName();
    fromMethod = conflict.dependency().fromMethod().prettyWithoutReturnType();
    final int lineNumber = conflict.dependency().fromLineNumber();
    fromLineNumber = lineNumber == 0 ? null : lineNumber;
    toClass = conflict.dependency().targetClass().getClassName();
    reason = conflict.reason();
    category = conflict.category().name();
  }

  public String getFromArtifact() {
    return fromArtifact;
  }

  public String getFromClass() {
    return fromClass;
  }

  public String getFromMethod() {
    return fromMethod;
  }

  public Integer getFromLineNumber() {
    return fromLineNumber;
  }

  public String getToClass() {
    return toClass;
  }

  public String getReason() {
    return reason;
  }

  public String getCategory() {
    return category;
  }

  public static final Comparator<ConflictItem> COMPARATOR = createComparator();

  private static Comparator<ConflictItem> createComparator() {
    final ArrayList<Comparator<ConflictItem>> comparators = Lists.newArrayList();
    comparators.add(Comparator.comparing(ConflictItem::getCategory));
    comparators.add(Comparator.comparing(ConflictItem::getFromArtifact));
    comparators.add(Comparator.comparing(ConflictItem::getFromClass));
    comparators.add(Comparator.comparing(ConflictItem::getFromLineNumber));
    comparators.add(Comparator.comparing(ConflictItem::getFromMethod));
    comparators.add(Comparator.comparing(ConflictItem::getToClass));
    comparators.add(Comparator.comparing(ConflictItem::getReason));
    return Ordering.compound(comparators);
  }


}
