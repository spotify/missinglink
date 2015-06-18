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

package com.spotify.missinglink.benchmarks;

import com.spotify.missinglink.datamodel.PrimitiveTypeDescriptor;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;


@State(Scope.Benchmark)
public class PrimitiveTypeDescriptorBenchmark {

  /**
   * This class holds the raw String value to use to pass to
   * {@link PrimitiveTypeDescriptor#fromRaw(String)}. The annotations used set up JMH to use the
   * one holder per test thread, and set up a new value for each iteration of the benchmark (not
   * each invocation).
   */
  @State(Scope.Thread)
  public static class RawStringHolder {

    private String value;

    @Setup(Level.Iteration)
    public void assignValue() {
      final int size = PrimitiveTypeDescriptor.values().length;
      int ix = ThreadLocalRandom.current().nextInt(size);
      this.value = PrimitiveTypeDescriptor.values()[ix].getRaw();
    }
  }

  /** Test the ImmutableMap lookup method in the current source code. */
  @Benchmark
  public PrimitiveTypeDescriptor originalMethod(RawStringHolder holder) {
    return PrimitiveTypeDescriptor.fromRaw(holder.value);
  }

  // ------------------------------------------------------------------------------------
  // test using a hashmap instead of ImmutableMap

  private Map<String, PrimitiveTypeDescriptor> hashMap;

  @Setup(Level.Trial)
  public void setup() {
    this.hashMap = new HashMap<>();
    for (PrimitiveTypeDescriptor ptd : PrimitiveTypeDescriptor.values()) {
      this.hashMap.put(ptd.getRaw(), ptd);
    }
  }

  @Benchmark
  public PrimitiveTypeDescriptor fromHashMap(RawStringHolder holder) {
    return hashMap.get(holder.value);
  }

  // ------------------------------------------------------------------------------------
  // what if we just iterated over all 8 enums each time?

  @Benchmark
  public PrimitiveTypeDescriptor dumbIteration(RawStringHolder holder) {
    for (PrimitiveTypeDescriptor ptd : PrimitiveTypeDescriptor.values()) {
      if (ptd.getRaw().equals(holder.value)) {
        return ptd;
      }
    }
    return null;
  }
}
