/*
 * $Id$
 * --------------------------------------------------------------------------------------
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 *
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */

package org.mule.module.cache;

import java.util.HashMap;
import java.util.Map;

import org.mule.api.client.MuleClient;
import org.mule.client.DefaultLocalMuleClient;
import org.mule.tck.FunctionalTestCase;

public class HttpCachingMessageProcessorTest extends FunctionalTestCase
{
    @Override
    protected String getConfigResources()
    {
        return "http-cache-config.xml";
    }

    public void testDefaultExpressionsCacheableRequest() throws Exception
    {
        testHttpCachingMessageProcessorWithDefaultExpressions("cacheable-response-service", true);
    }

    public void testDefaultExpressionsNotCacheableRequest() throws Exception
    {
        testHttpCachingMessageProcessorWithDefaultExpressions("not-cacheable-response-service", false);
    }

    public void testCustomExpressionsCacheableRequest() throws Exception
    {
        testHttpCachingMessageProcessorWithCustomExpressions("cacheable-response-service", true);
    }

    public void testCustomExpressionsNotCacheableRequest() throws Exception
    {
        testHttpCachingMessageProcessorWithCustomExpressions("not-cacheable-response-service", false);
    }

    private void testHttpCachingMessageProcessorWithDefaultExpressions(final String targetService,
                                                                       final boolean expectCached)
        throws Exception
    {
        final Map<String, Object> requestProperties = new HashMap<String, Object>();
        requestProperties.put("http.method", "GET");
        requestProperties.put("http.request", "http://fake/path");
        testHttpCachingMessageProcessor("default-expressions", requestProperties, targetService, expectCached);
    }

    private void testHttpCachingMessageProcessorWithCustomExpressions(final String targetService,
                                                                      final boolean expectCached)
        throws Exception
    {
        final Map<String, Object> requestProperties = new HashMap<String, Object>();
        requestProperties.put("customHttpMethod", "GET");
        requestProperties.put("customUri", "http://fake/path");
        testHttpCachingMessageProcessor("custom-expressions", requestProperties, targetService, expectCached);
    }

    private void testHttpCachingMessageProcessor(final String testService,
                                                 final Map<String, Object> requestProperties,
                                                 final String targetService,
                                                 final boolean expectCached) throws Exception
    {
        final MuleClient client = new DefaultLocalMuleClient(muleContext);

        final Map<String, Object> properties = new HashMap<String, Object>(requestProperties);
        properties.put("target.service", targetService);
        final String result1 = client.send("vm://" + testService, null, properties).getPayloadAsString();
        final String result2 = client.send("vm://" + testService, null, properties).getPayloadAsString();
        assertEquals(expectCached, result1.equals(result2));
    }
}
