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
import org.mule.api.context.notification.ServerNotificationHandler;
import org.mule.api.lifecycle.Initialisable;
import org.mule.api.lifecycle.InitialisationException;
import org.mule.api.processor.InterceptingMessageProcessor;
import org.mule.api.processor.MessageProcessor;
import org.mule.api.transport.OutputHandler;
import org.mule.api.transport.PropertyScope;
import org.mule.config.i18n.MessageFactory;
import org.mule.context.notification.MessageProcessorNotification;
import org.mule.processor.AbstractMessageProcessorOwner;
import org.mule.processor.chain.DefaultMessageProcessorChainBuilder;

import java.io.InputStream;
import java.io.Serializable;
import java.util.List;

import javax.xml.transform.stream.StreamSource;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springmodules.cache.CachingModel;
import org.springmodules.cache.provider.CacheProviderFacade;

public class CachingMessageProcessor extends AbstractMessageProcessorOwner
    implements Initialisable, InterceptingMessageProcessor
{
    protected Log logger = LogFactory.getLog(getClass());

    private CacheKeyGenerator keyGenerator;
    private CacheProviderFacade cacheProvider;
    private CachingModel cacheModel;
    private String cacheableExpression;
    private String keyGeneratorExpression;
    private List<MessageProcessor> messageProcessors;

    protected MessageProcessor next;

    protected ServerNotificationHandler notificationHandler;

    protected MuleEvent processNext(MuleEvent event) throws MuleException
    {
        if (next == null)
        {
            return event;
        }
        else
        {
            if (logger.isTraceEnabled())
            {
                logger.trace("Invoking next MessageProcessor: '" + next.getClass().getName() + "' ");
            }

            // note that we're firing event for the next in chain, not this MP
            fireNotification(event, next, MessageProcessorNotification.MESSAGE_PROCESSOR_PRE_INVOKE);

            final MuleEvent result = next.process(event);

            fireNotification(event, next, MessageProcessorNotification.MESSAGE_PROCESSOR_POST_INVOKE);

            return result;
        }
    }

    protected void fireNotification(MuleEvent event, MessageProcessor processor, int action)
    {
        if (notificationHandler != null
            && notificationHandler.isNotificationEnabled(MessageProcessorNotification.class))
        {
            notificationHandler.fireNotification(new MessageProcessorNotification(event, processor, action));
        }
    }

    public void initialise() throws InitialisationException
    {
        super.initialise();
        
        if (keyGeneratorExpression != null && keyGenerator != null)
        {
            throw new InitialisationException(
                MessageFactory.createStaticMessage("Both a key generator and key generator expression cannot be specified at the same time."),
                this);
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
            return event.getMuleContext()
                .getExpressionManager()
                .evaluateBoolean(cacheableExpression, event.getMessage());
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

    public void setMessageProcessors(List<MessageProcessor> messageProcessors) throws MuleException
    {
        this.messageProcessors = messageProcessors;
        this.next = new DefaultMessageProcessorChainBuilder().chain(messageProcessors).build();
    }

    @Override
    protected List<MessageProcessor> getOwnedMessageProcessors()
    {
        return messageProcessors;
    }

    public void setListener(MessageProcessor listener)
    {
        next = listener;
    }

}
