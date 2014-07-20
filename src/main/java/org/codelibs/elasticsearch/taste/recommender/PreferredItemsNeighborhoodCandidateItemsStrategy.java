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

package org.codelibs.elasticsearch.taste.recommender;

import org.codelibs.elasticsearch.taste.common.FastIDSet;
import org.codelibs.elasticsearch.taste.model.DataModel;
import org.codelibs.elasticsearch.taste.model.PreferenceArray;

public final class PreferredItemsNeighborhoodCandidateItemsStrategy extends
        AbstractCandidateItemsStrategy {

    /**
     * returns all items that have not been rated by the user and that were preferred by another user
     * that has preferred at least one item that the current user has preferred too
     */
    @Override
    protected FastIDSet doGetCandidateItems(final long[] preferredItemIDs,
            final DataModel dataModel) {
        final FastIDSet possibleItemsIDs = new FastIDSet();
        for (final long itemID : preferredItemIDs) {
            final PreferenceArray itemPreferences = dataModel
                    .getPreferencesForItem(itemID);
            final int numUsersPreferringItem = itemPreferences.length();
            for (int index = 0; index < numUsersPreferringItem; index++) {
                possibleItemsIDs.addAll(dataModel
                        .getItemIDsFromUser(itemPreferences.getUserID(index)));
            }
        }
        possibleItemsIDs.removeAll(preferredItemIDs);
        return possibleItemsIDs;
    }

}
