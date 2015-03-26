package org.codelibs.elasticsearch.taste.rest.handler;

import static org.codelibs.elasticsearch.util.action.ListenerUtils.on;

import java.security.InvalidParameterException;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.codelibs.elasticsearch.taste.TasteConstants;
import org.codelibs.elasticsearch.taste.exception.OperationFailedException;
import org.codelibs.elasticsearch.util.action.ListenerUtils.OnFailureListener;
import org.codelibs.elasticsearch.util.action.ListenerUtils.OnResponseListener;
import org.elasticsearch.action.admin.cluster.health.ClusterHealthResponse;
import org.elasticsearch.action.admin.indices.create.CreateIndexResponse;
import org.elasticsearch.action.admin.indices.exists.indices.IndicesExistsResponse;
import org.elasticsearch.action.admin.indices.mapping.put.PutMappingResponse;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.common.joda.time.format.ISODateTimeFormat;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.xcontent.ToXContent.Params;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.elasticsearch.indices.IndexAlreadyExistsException;

public class PreferenceRequestHandler extends DefaultRequestHandler {
    public PreferenceRequestHandler(final Settings settings, final Client client) {
        super(settings, client);
    }

    public boolean hasPreference(final Map<String, Object> requestMap) {
        return requestMap.containsKey("value");
    }

    @Override
    public void execute(final Params params,
            final RequestHandler.OnErrorListener listener,
            final Map<String, Object> requestMap,
            final Map<String, Object> paramMap, final RequestHandlerChain chain) {
        final String index = params.param(
                TasteConstants.REQUEST_PARAM_PREFERENCE_INDEX,
                params.param("index"));
        final String type = params.param(
                TasteConstants.REQUEST_PARAM_PREFERENCE_TYPE,
                params.param("type", TasteConstants.PREFERENCE_TYPE));
        final String userIdField = params.param(
                TasteConstants.REQUEST_PARAM_USER_ID_FIELD,
                TasteConstants.USER_ID_FIELD);
        final String itemIdField = params.param(
                TasteConstants.REQUEST_PARAM_ITEM_ID_FIELD,
                TasteConstants.ITEM_ID_FIELD);
        final String valueField = params.param(
                TasteConstants.REQUEST_PARAM_VALUE_FIELD,
                TasteConstants.VALUE_FIELD);
        final String timestampField = params.param(
                TasteConstants.REQUEST_PARAM_TIMESTAMP_FIELD,
                TasteConstants.TIMESTAMP_FIELD);

        final Number value = (Number) requestMap.get("value");
        if (value == null) {
            throw new InvalidParameterException("value is null.");
        }

        Date timestamp;
        final Object timestampObj = requestMap.get("timestamp");
        if (timestampObj == null) {
            timestamp = new Date();
        } else if (timestampObj instanceof String) {
            timestamp = new Date(ISODateTimeFormat.dateTime().parseMillis(
                    timestampObj.toString()));
        } else if (timestampObj instanceof Date) {
            timestamp = (Date) timestampObj;
        } else if (timestampObj instanceof Number) {
            timestamp = new Date(((Number) timestampObj).longValue());
        } else {
            throw new InvalidParameterException("timestamp is invalid format: "
                    + timestampObj);
        }

        final Long userId = (Long) paramMap.get(userIdField);
        final Long itemId = (Long) paramMap.get(itemIdField);

        final Map<String, Object> rootObj = new HashMap<>();
        rootObj.put(userIdField, userId);
        rootObj.put(itemIdField, itemId);
        rootObj.put(valueField, value);
        rootObj.put(timestampField, timestamp);
        final OnResponseListener<SearchResponse> responseListener = new OnResponseListener<SearchResponse>() {
            @Override
            public void onResponse(SearchResponse response) {
                chain.execute(params, listener, requestMap, paramMap);
            }
        };

        final OnFailureListener failureListener = new OnFailureListener() {
            @Override
            public void onFailure(Throwable t) {
                final List<Throwable> errorList = getErrorList(paramMap);
                if (errorList.size() >= maxRetryCount) {
                    listener.onError(t);
                } else {
                    errorList.add(t);
                    doPreferenceIndexExists(params, listener, requestMap, paramMap,
                            chain);
                }
            }
        };

        client.prepareIndex(index, type).setSource(rootObj)
                .execute(on(responseListener, failureListener));
    }

    private void doPreferenceIndexExists(final Params params,
            final RequestHandler.OnErrorListener listener,
            final Map<String, Object> requestMap,
            final Map<String, Object> paramMap, final RequestHandlerChain chain) {
        final String index = params.param("index");

        try {
            indexCreationLock.lock();
            final IndicesExistsResponse indicesExistsResponse = client.admin()
                    .indices().prepareExists(index).execute().actionGet();
            if (indicesExistsResponse.isExists()) {
                doPreferenceMappingCreation(params, listener, requestMap,
                        paramMap, chain);
            } else {
                doPreferenceIndexCreation(params, listener, requestMap,
                        paramMap, chain, index);
            }
        } catch (final Exception e) {
            final List<Throwable> errorList = getErrorList(paramMap);
            if (errorList.size() >= maxRetryCount) {
                listener.onError(e);
            } else {
                sleep(e);
                errorList.add(e);
                fork(new Runnable() {
                    @Override
                    public void run() {
                        execute(params, listener, requestMap, paramMap, chain);
                    }
                });
            }
        } finally {
            indexCreationLock.unlock();
        }
    }

    private void doPreferenceIndexCreation(final Params params,
            final RequestHandler.OnErrorListener listener,
            final Map<String, Object> requestMap,
            final Map<String, Object> paramMap,
            final RequestHandlerChain chain, final String index) {
        try {
            final CreateIndexResponse createIndexResponse = client.admin()
                    .indices().prepareCreate(index).execute().actionGet();
            if (createIndexResponse.isAcknowledged()) {
                doPreferenceMappingCreation(params, listener, requestMap,
                        paramMap, chain);
            } else {
                listener.onError(new OperationFailedException(
                        "Failed to create " + index));
            }
        } catch (final IndexAlreadyExistsException e) {
            fork(new Runnable() {
                @Override
                public void run() {
                    doPreferenceIndexExists(params, listener, requestMap, paramMap, chain);
                }
            });
        } catch (final Exception e) {
            final List<Throwable> errorList = getErrorList(paramMap);
            if (errorList.size() >= maxRetryCount) {
                listener.onError(e);
            } else {
                sleep(e);
                errorList.add(e);
                fork(new Runnable() {
                    @Override
                    public void run() {
                        execute(params, listener, requestMap, paramMap, chain);
                    }
                });
            }
        }
    }

    private void doPreferenceMappingCreation(final Params params,
            final RequestHandler.OnErrorListener listener,
            final Map<String, Object> requestMap,
            final Map<String, Object> paramMap, final RequestHandlerChain chain) {
        final String index = params.param("index");
        final String type = params
                .param("type", TasteConstants.PREFERENCE_TYPE);
        final String userIdField = params.param(
                TasteConstants.REQUEST_PARAM_USER_ID_FIELD,
                TasteConstants.USER_ID_FIELD);
        final String itemIdField = params.param(
                TasteConstants.REQUEST_PARAM_ITEM_ID_FIELD,
                TasteConstants.ITEM_ID_FIELD);
        final String valueField = params.param(
                TasteConstants.REQUEST_PARAM_VALUE_FIELD,
                TasteConstants.VALUE_FIELD);
        final String timestampField = params.param(
                TasteConstants.REQUEST_PARAM_TIMESTAMP_FIELD,
                TasteConstants.TIMESTAMP_FIELD);

        try {
            final ClusterHealthResponse healthResponse = client
                    .admin()
                    .cluster()
                    .prepareHealth(index)
                    .setWaitForYellowStatus()
                    .setTimeout(
                            params.param("timeout",
                                    DEFAULT_HEALTH_REQUEST_TIMEOUT)).execute()
                    .actionGet();
            if (healthResponse.isTimedOut()) {
                listener.onError(new OperationFailedException(
                        "Failed to create index: " + index + "/" + type));
            }

            final XContentBuilder builder = XContentFactory.jsonBuilder()//
                    .startObject()//
                    .startObject(type)//
                    .startObject("properties")//

                    // @timestamp
                    .startObject(timestampField)//
                    .field("type", "date")//
                    .field("format", "dateOptionalTime")//
                    .endObject()//

                    // user_id
                    .startObject(userIdField)//
                    .field("type", "long")//
                    .endObject()//

                    // item_id
                    .startObject(itemIdField)//
                    .field("type", "long")//
                    .endObject()//

                    // value
                    .startObject(valueField)//
                    .field("type", "double")//
                    .endObject()//

                    .endObject()//
                    .endObject()//
                    .endObject();

            final PutMappingResponse mappingResponse = client.admin().indices()
                    .preparePutMapping(index).setType(type).setSource(builder)
                    .execute().actionGet();
            if (mappingResponse.isAcknowledged()) {
                fork(new Runnable() {
                    @Override
                    public void run() {
                        execute(params, listener, requestMap, paramMap, chain);
                    }
                });
            } else {
                listener.onError(new OperationFailedException(
                        "Failed to create mapping for " + index + "/" + type));
            }
        } catch (final Exception e) {
            listener.onError(e);
        }
    }

}
