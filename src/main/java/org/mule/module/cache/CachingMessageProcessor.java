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

import java.io.InputStream;
import java.io.Serializable;
import java.util.List;

import javax.xml.transform.stream.StreamSource;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
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
    private boolean payloadOnly;

    protected MessageProcessor next;

    protected ServerNotificationHandler notificationHandler;

    protected MuleEvent processNext(final MuleEvent event) throws MuleException
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

    protected void fireNotification(final MuleEvent event, final MessageProcessor processor, final int action)
    {
        if (notificationHandler != null
            && notificationHandler.isNotificationEnabled(MessageProcessorNotification.class))
        {
            notificationHandler.fireNotification(new MessageProcessorNotification(getFlowConstruct(), event,
                processor, null, action));
        }
    }

    @Override
    public void initialise() throws InitialisationException
    {
        super.initialise();

        try
        {
            next = new DefaultMessageProcessorChainBuilder(getFlowConstruct()).chain(messageProcessors)
                .build();
        }
        catch (final MuleException me)
        {
            throw new InitialisationException(me, this);
        }

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

    public MuleEvent process(final MuleEvent event) throws MuleException
    {
        if (!isCacheable(event))
        {
            return processNext(event);
        }

        // Generate the key
        final Serializable key = keyGenerator.generateKey(event);

        if (logger.isDebugEnabled())
        {
            logger.debug("Got cache key " + key + " for event " + event);
        }

        // see if we have a cached response
        final Object cachedResponse = cacheProvider.getFromCache(key, cacheModel);

        // nothing in the cache, invoke the MPs
        if (cachedResponse == null)
        {
            final MuleEvent response = processNext(event);

            cacheProvider.putInCache(key, cacheModel, getCacheableContent(response));

            return response;
        }
        else
        {
            setCachedContentOnEvent(cachedResponse, event);
            return event;
        }
    }

    protected boolean isCacheable(final MuleEvent event)
    {
        if (cacheableExpression != null)
        {
            return event.getMuleContext().getExpressionManager().evaluateBoolean(cacheableExpression, event);
        }

        // if the user doesn't specify an expression, assume the message is cacheable
        return true;
    }

    protected Object getCacheableContent(final MuleEvent event) throws MuleException
    {
        ensurePayloadIsNotConsumable(event);

        return isPayloadOnly() ? event.getMessage().getPayload() : event;
    }

    private void setCachedContentOnEvent(final Object cached, final MuleEvent event)
    {
        if (isPayloadOnly())
        {
            event.getMessage().setPayload(cached);
            return;
        }

        final MuleMessage message = event.getMessage();
        final MuleMessage cachedResponse = ((MuleEvent) cached).getMessage();

        message.clearProperties(PropertyScope.INBOUND);
        message.clearProperties(PropertyScope.INVOCATION);
        message.clearProperties(PropertyScope.OUTBOUND);

        // copy properties
        for (final String s : cachedResponse.getInboundPropertyNames())
        {
            message.setProperty(s, cachedResponse.getInboundProperty(s), PropertyScope.INBOUND);
        }

        for (final String s : cachedResponse.getInvocationPropertyNames())
        {
            message.setProperty(s, cachedResponse.getInvocationProperty(s), PropertyScope.INVOCATION);
        }

        for (final String s : cachedResponse.getOutboundPropertyNames())
        {
            message.setProperty(s, cachedResponse.getOutboundProperty(s), PropertyScope.OUTBOUND);
        }

        // copy payload
        message.setPayload(cachedResponse.getPayload());
    }

    protected void ensurePayloadIsNotConsumable(final MuleEvent response) throws MuleException
    {
        final Object payload = response.getMessage().getPayload();
        final boolean isStream = isStream(payload);
        if (isStream)
        {
            try
            {
                response.getMessage().getPayloadAsBytes();
            }
            catch (final Exception e)
            {
                if (e instanceof MuleException)
                {
                    throw (MuleException) e;
                }

                throw new DefaultMuleException(e);
            }
        }
    }

    protected boolean isStream(final Object payload)
    {
        return payload instanceof OutputHandler || payload instanceof InputStream
               || payload instanceof StreamSource;
    }

    public void setCache(final CacheProviderFacade cacheProvider)
    {
        this.cacheProvider = cacheProvider;
    }

    public void setCachingModel(final CachingModel cacheModel)
    {
        this.cacheModel = cacheModel;
    }

    public String getCacheableExpression()
    {
        return cacheableExpression;
    }

    public void setCacheableExpression(final String cacheableExpression)
    {
        this.cacheableExpression = cacheableExpression;
    }

    public void setKeyGenerator(final CacheKeyGenerator keyGenerator)
    {
        this.keyGenerator = keyGenerator;
    }

    public void setKeyGeneratorExpression(final String keyGeneratorExpression)
    {
        this.keyGeneratorExpression = keyGeneratorExpression;
    }

    public void setMessageProcessors(final List<MessageProcessor> messageProcessors)
    {
        this.messageProcessors = messageProcessors;
    }

    public void setPayloadOnly(final boolean payloadOnly)
    {
        this.payloadOnly = payloadOnly;
    }

    public boolean isPayloadOnly()
    {
        return payloadOnly;
    }

    @Override
    protected List<MessageProcessor> getOwnedMessageProcessors()
    {
        return messageProcessors;
    }

    public void setListener(final MessageProcessor listener)
    {
        next = listener;
    }
}
