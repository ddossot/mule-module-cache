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
import org.mule.api.lifecycle.Initialisable;
import org.mule.api.lifecycle.InitialisationException;
import org.mule.processor.AbstractInterceptingMessageProcessor;

/**
 * A wrapper around Java HTTP Cache.
 */
public class HttpAwareCachingMessageProcessor extends AbstractInterceptingMessageProcessor
    implements Initialisable

{
    private static final String MULE_HTTPCACHE_RESOLVER_PROPERTY_KEY = "mule.httpcache.resolver";
    private HTTPCache httpCache;

    public void initialise() throws InitialisationException
    {
        httpCache.setResolver(new ResponseResolver()
        {
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

                    final Payload payload = new ByteArrayPayload(new ByteArrayInputStream(
                        response.getMessage().getPayloadAsBytes()),
                        MIMEType.valueOf((String) message.getInboundProperty("Content-Type")));

                    final Status status = Status.valueOf(Integer.valueOf((String) message.getInboundProperty("http.status")));
                    final Headers headers = new Headers();
                    for (final String propertyName : message.getInboundPropertyNames())
                    {
                        headers.add(propertyName, message.getInboundProperty(propertyName).toString());
                    }
                    return new HTTPResponse(payload, status, headers);
                }
                catch (final Exception e)
                {
                    throw new IOException("Can't process HTTP request with Mule", e);
                }
            }
        });
    }

    public MuleEvent process(final MuleEvent event) throws MuleException
    {
        final HTTPRequest httpRequest = new HTTPRequest((String) event.getMessage().getInboundProperty(
            "http.request"));

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
            properties.put(headerName, headers.getHeaders(headerName));
        }
        final DefaultMuleMessage message = new DefaultMuleMessage(httpResponse.getPayload().getInputStream(),
            properties, muleContext);
        return new DefaultMuleEvent(message, event);
    }

    public void setHttpCache(final HTTPCache httpCache)
    {
        this.httpCache = httpCache;
    }
}
