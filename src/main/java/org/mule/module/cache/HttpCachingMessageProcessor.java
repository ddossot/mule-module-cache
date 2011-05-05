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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;

import org.codehaus.httpcache4j.HTTPMethod;
import org.codehaus.httpcache4j.HTTPRequest;
import org.codehaus.httpcache4j.HTTPResponse;
import org.codehaus.httpcache4j.Headers;
import org.codehaus.httpcache4j.MIMEType;
import org.codehaus.httpcache4j.Status;
import org.codehaus.httpcache4j.cache.HTTPCache;
import org.codehaus.httpcache4j.payload.ByteArrayPayload;
import org.codehaus.httpcache4j.payload.Payload;
import org.codehaus.httpcache4j.resolver.ResponseResolver;
import org.mule.DefaultMuleEvent;
import org.mule.DefaultMuleMessage;
import org.mule.RequestContext;
import org.mule.api.MuleEvent;
import org.mule.api.MuleException;
import org.mule.api.MuleMessage;
import org.mule.api.expression.ExpressionManager;
import org.mule.api.lifecycle.Initialisable;
import org.mule.api.lifecycle.InitialisationException;
import org.mule.processor.AbstractInterceptingMessageProcessor;

/**
 * A wrapper around <a href="http://httpcache4j.codehaus.org/">Java HTTP Cache</a>.
 */
public class HttpCachingMessageProcessor extends AbstractInterceptingMessageProcessor
    implements Initialisable

{
    private static final class MuleResponseResolver implements ResponseResolver
    {
        private final String httpResponseStatusCodeExpression;

        public MuleResponseResolver(final String httpResponseStatusCodeExpression)
        {
            this.httpResponseStatusCodeExpression = httpResponseStatusCodeExpression;
        }

        public void shutdown()
        {
            // NOOP
        }

        public HTTPResponse resolve(final HTTPRequest ignored) throws IOException
        {
            try
            {
                @SuppressWarnings("unchecked")
                final Callable<MuleEvent> muleInvoker = (Callable<MuleEvent>) RequestContext.getEvent()
                    .getMessage()
                    .getInvocationProperty(MULE_HTTPCACHE_RESOLVER_PROPERTY_KEY);

                final MuleEvent response = muleInvoker.call();
                final MuleMessage message = response.getMessage();

                final Payload payload = new ByteArrayPayload(new ByteArrayInputStream(response.getMessage()
                    .getPayloadAsBytes()),
                    MIMEType.valueOf((String) message.getInboundProperty("Content-Type")));

                final Status status = Status.valueOf(Integer.valueOf((String) response.getMuleContext()
                    .getExpressionManager()
                    .evaluate(httpResponseStatusCodeExpression, message)));

                Headers headers = new Headers();
                for (final String propertyName : message.getInboundPropertyNames())
                {
                    headers = headers.set(propertyName, message.getInboundProperty(propertyName).toString());
                }
                return new HTTPResponse(payload, status, headers);
            }
            catch (final Exception e)
            {
                throw new IOException("Can't process HTTP request with Mule", e);
            }
        }
    }

    public static final String MULE_HTTPCACHE_RESOLVER_PROPERTY_KEY = "mule.httpcache.resolver";

    public static final String DEFAULT_REQUEST_URI_EXPRESSION = "#[header:INBOUND:http.request]";
    public static final String DEFAULT_REQUEST_HTTP_METHOD_EXPRESSION = "#[header:INBOUND:http.method]";
    public static final String DEFAULT_RESPONSE_HTTP_STATUS_CODE_EXPRESSION = "#[header:INBOUND:http.status]";

    private HTTPCache httpCache;
    private String requestUriExpression = DEFAULT_REQUEST_URI_EXPRESSION;
    private String requestHttpMethodExpression = DEFAULT_REQUEST_HTTP_METHOD_EXPRESSION;
    private String responseHttpStatusCodeExpression = DEFAULT_RESPONSE_HTTP_STATUS_CODE_EXPRESSION;

    public void initialise() throws InitialisationException
    {
        httpCache.setResolver(new MuleResponseResolver(getResponseHttpStatusCodeExpression()));
    }

    public MuleEvent process(final MuleEvent event) throws MuleException
    {
        final MuleMessage message = event.getMessage();
        final ExpressionManager expressionManager = muleContext.getExpressionManager();

        final String requestUri = (String) expressionManager.evaluate(getRequestUriExpression(), message);

        final HTTPMethod httpMethod = HTTPMethod.valueOf((String) expressionManager.evaluate(
            getRequestHttpMethodExpression(), message));

        HTTPRequest httpRequest = new HTTPRequest(requestUri, httpMethod);
        for (final String propertyName : message.getInboundPropertyNames())
        {
            httpRequest = httpRequest.addHeader(propertyName, message.getInboundProperty(propertyName)
                .toString());
        }

        event.getMessage().setInvocationProperty(MULE_HTTPCACHE_RESOLVER_PROPERTY_KEY,
            new Callable<MuleEvent>()
            {
                public MuleEvent call() throws Exception
                {
                    return processNext(event);
                }
            });

        final HTTPResponse httpResponse = httpCache.doCachedRequest(httpRequest);

        final Map<String, Object> properties = new HashMap<String, Object>();
        final Headers headers = httpResponse.getHeaders();
        for (final String headerName : headers.keySet())
        {
            properties.put(headerName, headers.getFirstHeader(headerName).getValue());
        }
        final DefaultMuleMessage resultMessage = new DefaultMuleMessage(httpResponse.getPayload()
            .getInputStream(), properties, muleContext);
        return new DefaultMuleEvent(resultMessage, event);
    }

    public HTTPCache getHttpCache()
    {
        return httpCache;
    }

    public void setHttpCache(final HTTPCache httpCache)
    {
        this.httpCache = httpCache;
    }

    public String getRequestUriExpression()
    {
        return requestUriExpression;
    }

    public void setRequestUriExpression(final String requestUriExpression)
    {
        this.requestUriExpression = requestUriExpression;
    }

    public String getRequestHttpMethodExpression()
    {
        return requestHttpMethodExpression;
    }

    public void setRequestHttpMethodExpression(final String httpMethodExpression)
    {
        this.requestHttpMethodExpression = httpMethodExpression;
    }

    public String getResponseHttpStatusCodeExpression()
    {
        return responseHttpStatusCodeExpression;
    }

    public void setResponseHttpStatusCodeExpression(final String httpResponseStatusCodeExpression)
    {
        this.responseHttpStatusCodeExpression = httpResponseStatusCodeExpression;
    }
}
