package org.apache.lucene.index;

/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

import org.apache.lucene.index.codecs.PostingsFormat;
import org.apache.lucene.index.codecs.CodecProvider;
import org.apache.lucene.index.codecs.memory.MemoryPostingsFormat;
import org.apache.lucene.index.codecs.preflex.PreFlexPostingsFormat;
import org.apache.lucene.index.codecs.pulsing.PulsingPostingsFormat;
import org.apache.lucene.index.codecs.simpletext.SimpleTextPostingsFormat;
import org.apache.lucene.index.codecs.standard.StandardPostingsFormat;
import org.apache.lucene.util._TestUtil;

/**
 * CodecProvider that assigns per-field random codecs.
 * <p>
 * The same field/codec assignment will happen regardless of order,
 * a hash is computed up front that determines the mapping.
 * This means fields can be put into things like HashSets and added to
 * documents in different orders and the test will still be deterministic
 * and reproducable.
 */
public class RandomCodecProvider extends CodecProvider {
  private List<PostingsFormat> knownCodecs = new ArrayList<PostingsFormat>();
  private Map<String,PostingsFormat> previousMappings = new HashMap<String,PostingsFormat>();
  private final int perFieldSeed;
  
  public RandomCodecProvider(Random random, boolean useNoMemoryExpensiveCodec) {
    this.perFieldSeed = random.nextInt();
    // TODO: make it possible to specify min/max iterms per
    // block via CL:
    int minItemsPerBlock = _TestUtil.nextInt(random, 2, 100);
    int maxItemsPerBlock = 2*(Math.max(2, minItemsPerBlock-1)) + random.nextInt(100);
    register(new StandardPostingsFormat(minItemsPerBlock, maxItemsPerBlock));
    register(new PreFlexPostingsFormat());
    // TODO: make it possible to specify min/max iterms per
    // block via CL:
    minItemsPerBlock = _TestUtil.nextInt(random, 2, 100);
    maxItemsPerBlock = 2*(Math.max(1, minItemsPerBlock-1)) + random.nextInt(100);
    register(new PulsingPostingsFormat( 1 + random.nextInt(20), minItemsPerBlock, maxItemsPerBlock));
    if (!useNoMemoryExpensiveCodec) {
      register(new SimpleTextPostingsFormat());
      register(new MemoryPostingsFormat());
    }
    Collections.shuffle(knownCodecs, random);
  }
  
  @Override
  public synchronized void register(PostingsFormat codec) {
    if (!codec.name.equals("PreFlex"))
      knownCodecs.add(codec);
    super.register(codec);
  }
  
  @Override
  public synchronized void unregister(PostingsFormat codec) {
    knownCodecs.remove(codec);
    super.unregister(codec);
  }
  
  @Override
  public synchronized String getFieldCodec(String name) {
    PostingsFormat codec = previousMappings.get(name);
    if (codec == null) {
      codec = knownCodecs.get(Math.abs(perFieldSeed ^ name.hashCode()) % knownCodecs.size());
      if (codec instanceof SimpleTextPostingsFormat && perFieldSeed % 5 != 0) {
        // make simpletext rarer, choose again
        codec = knownCodecs.get(Math.abs(perFieldSeed ^ name.toUpperCase(Locale.ENGLISH).hashCode()) % knownCodecs.size());
      }
      previousMappings.put(name, codec);
    }
    return codec.name;
  }
  
  @Override
  public synchronized boolean hasFieldCodec(String name) {
    return true; // we have a codec for every field
  }
  
  @Override
  public synchronized String toString() {
    return "RandomCodecProvider: " + previousMappings.toString();
  }
}
