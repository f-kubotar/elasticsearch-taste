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

package org.codelibs.elasticsearch.taste.similarity.precompute;

import java.util.Comparator;

import com.google.common.primitives.Doubles;

/**
 * Modeling similarity towards another item
 */
public class SimilarItem {

    public static final Comparator<SimilarItem> COMPARE_BY_SIMILARITY = new Comparator<SimilarItem>() {
        @Override
        public int compare(final SimilarItem s1, final SimilarItem s2) {
            return Doubles.compare(s1.similarity, s2.similarity);
        }
    };

    private long itemID;

    private double similarity;

    public SimilarItem(final long itemID, final double similarity) {
        set(itemID, similarity);
    }

    public void set(final long itemID, final double similarity) {
        this.itemID = itemID;
        this.similarity = similarity;
    }

    public long getItemID() {
        return itemID;
    }

    public double getSimilarity() {
        return similarity;
    }

}
