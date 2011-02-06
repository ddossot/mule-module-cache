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

import org.mule.api.DefaultMuleException;
import org.mule.api.MuleEvent;
import org.mule.api.MuleException;
import org.mule.api.MuleMessage;
import org.mule.api.lifecycle.Initialisable;
import org.mule.api.lifecycle.InitialisationException;
import org.mule.api.transport.OutputHandler;
import org.mule.api.transport.PropertyScope;
import org.mule.config.i18n.MessageFactory;
import org.mule.processor.AbstractInterceptingMessageProcessor;

import java.io.InputStream;
import java.io.Serializable;

import javax.xml.transform.stream.StreamSource;

import org.springmodules.cache.CachingModel;
import org.springmodules.cache.provider.CacheProviderFacade;

public class CachingMessageProcessor extends AbstractInterceptingMessageProcessor 
    implements Initialisable
{
    private CacheKeyGenerator keyGenerator;
    private CacheProviderFacade cacheProvider;
    private CachingModel cacheModel;
    private String cacheableExpression;
    private String keyGeneratorExpression;
    
    public void initialise() throws InitialisationException
    {
        if (keyGeneratorExpression != null && keyGenerator != null)
        {
            throw new InitialisationException(MessageFactory.createStaticMessage("Both a key generator and key generator expression cannot be specified at the same time."), this);
        }
        
        if (keyGeneratorExpression != null)
        {
            keyGenerator = new ExpressionKeyGenerator();
            ((ExpressionKeyGenerator) keyGenerator).setExpression(keyGeneratorExpression);
        }
        else if (keyGenerator == null)
        {
            keyGenerator = new MD5CacheKeyGenerator();
        }
    }

    public MuleEvent process(MuleEvent event) throws MuleException
    {
        if (!isCacheable(event))
        {
            return processNext(event);
        }   
            
        // Generate the key
        Serializable key = keyGenerator.generateKey(event);

        if (logger.isDebugEnabled())
        {
            logger.debug("Got cache key " + key + " for event " + event);
        }
        
        // see if we have a cached response
        MuleEvent cachedResponse = (MuleEvent) cacheProvider.getFromCache(key, cacheModel);

        MuleEvent response;
        // nothing in the cache, invoke the MPs
        if (cachedResponse == null)
        {
            response = processNext(event);

            ensurePayloadIsNotConsumable(response);

            cacheProvider.putInCache(key, cacheModel, response);
        }
        else
        {
            // return the cached event
            clone(cachedResponse, event);
            response = event;
        }
        return response;
    }

    protected boolean isCacheable(MuleEvent event)
    {
        if (cacheableExpression != null)
        {
           return event.getMuleContext().getExpressionManager().evaluateBoolean(cacheableExpression, event.getMessage());
        }
     
        // if the user doesn't specify an expression, assume the message is cacheable
        return true;
    }

    protected void ensurePayloadIsNotConsumable(MuleEvent response)
        throws MuleException, DefaultMuleException
    {
        Object payload = response.getMessage().getPayload();
        boolean isStream = isStream(payload);
        if (isStream)
        {
            try
            {
                response.getMessage().getPayloadAsBytes();
            }
            catch (Exception e)
            {
                if (e instanceof MuleException)
                {
                    throw (MuleException) e;
                }

                throw new DefaultMuleException(e);
            }
        }
    }

    protected boolean isStream(Object payload)
    {
        return payload instanceof OutputHandler || payload instanceof InputStream
               || payload instanceof StreamSource;
        // || payload.getClass().isAssignableFrom("javax.xml.stream.XMLStreamReader")
    }

    private Object clone(MuleEvent cached, MuleEvent event)
    {
        MuleMessage message = event.getMessage();
        MuleMessage cachedResponse = cached.getMessage();

        message.clearProperties(PropertyScope.INBOUND);
        message.clearProperties(PropertyScope.INVOCATION);
        message.clearProperties(PropertyScope.OUTBOUND);

        // copy properties
        for (String s : cachedResponse.getInboundPropertyNames())
        {
            message.setProperty(s, cachedResponse.getInboundProperty(s), PropertyScope.INBOUND);
        }

        for (String s : cachedResponse.getInvocationPropertyNames())
        {
            message.setProperty(s, cachedResponse.getInvocationProperty(s), PropertyScope.INVOCATION);
        }

        for (String s : cachedResponse.getOutboundPropertyNames())
        {
            message.setProperty(s, cachedResponse.getOutboundProperty(s), PropertyScope.OUTBOUND);
        }

        // copy payload
        message.setPayload(cachedResponse.getPayload());

        return message;
    }

    public void setCache(CacheProviderFacade cacheProvider)
    {
        this.cacheProvider = cacheProvider;
    }

    public void setCachingModel(CachingModel cacheModel)
    {
        this.cacheModel = cacheModel;
    }

    public String getCacheableExpression()
    {
        return cacheableExpression;
    }

    public void setCacheableExpression(String cacheableExpression)
    {
        this.cacheableExpression = cacheableExpression;
    }

    public void setKeyGenerator(CacheKeyGenerator keyGenerator)
    {
        this.keyGenerator = keyGenerator;
    }

    public void setKeyGeneratorExpression(String keyGeneratorExpression)
    {
        this.keyGeneratorExpression = keyGeneratorExpression;
    }
}
