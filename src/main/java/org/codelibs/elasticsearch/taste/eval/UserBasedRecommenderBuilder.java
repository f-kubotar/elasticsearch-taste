package org.codelibs.elasticsearch.taste.eval;

import java.util.HashMap;
import java.util.Map;

import org.apache.mahout.cf.taste.common.TasteException;
import org.apache.mahout.cf.taste.impl.recommender.GenericUserBasedRecommender;
import org.apache.mahout.cf.taste.model.DataModel;
import org.apache.mahout.cf.taste.neighborhood.UserNeighborhood;
import org.apache.mahout.cf.taste.recommender.Recommender;
import org.apache.mahout.cf.taste.similarity.UserSimilarity;
import org.codelibs.elasticsearch.taste.TasteSystemException;
import org.codelibs.elasticsearch.taste.model.IndexInfo;
import org.codelibs.elasticsearch.taste.neighborhood.UserNeighborhoodFactory;
import org.codelibs.elasticsearch.util.SettingsUtils;

public class UserBasedRecommenderBuilder extends AbstractRecommenderBuilder {

    public UserBasedRecommenderBuilder(final IndexInfo indexInfo,
            final Map<String, Object> rootSettings) {
        super(indexInfo, rootSettings);
    }

    @Override
    public Recommender buildRecommender(final DataModel dataModel)
            throws TasteException {
        final Map<String, Object> similaritySettings = SettingsUtils.get(
                rootSettings, "similarity", new HashMap<String, Object>());
        similaritySettings.put(DATA_MODEL_ATTR, dataModel);
        final UserSimilarity similarity = createSimilarity(similaritySettings);

        final Map<String, Object> neighborhoodSettings = SettingsUtils.get(
                rootSettings, "neighborhood", new HashMap<String, Object>());
        neighborhoodSettings.put(DATA_MODEL_ATTR, dataModel);
        neighborhoodSettings.put(USER_SIMILARITY_ATTR, similarity);
        final UserNeighborhood neighborhood = createUserNeighborhood(neighborhoodSettings);

        return new GenericUserBasedRecommender(dataModel, neighborhood,
                similarity);
    }

    protected UserNeighborhood createUserNeighborhood(
            final Map<String, Object> neighborhoodSettings) {
        final String factoryName = SettingsUtils
                .get(neighborhoodSettings, "factory",
                        "org.codelibs.elasticsearch.taste.neighborhood.NearestNUserNeighborhoodFactory");
        try {
            final Class<?> clazz = Class.forName(factoryName);
            final UserNeighborhoodFactory userNeighborhoodFactory = (UserNeighborhoodFactory) clazz
                    .newInstance();
            userNeighborhoodFactory.init(neighborhoodSettings);
            return userNeighborhoodFactory.create();
        } catch (ClassNotFoundException | InstantiationException
                | IllegalAccessException e) {
            throw new TasteSystemException("Could not create an instance of "
                    + factoryName, e);
        }
    }
}
