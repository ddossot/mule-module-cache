/*
 * $Id: AsyncMessageProcessorsFactoryBean.java 20523 2010-12-08 20:05:28Z aperepel $
 * --------------------------------------------------------------------------------------
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 *
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */

package org.mule.module.cache;

import org.mule.api.MuleContext;
import org.mule.api.context.MuleContextAware;
import org.mule.api.processor.InterceptingMessageProcessor;
import org.mule.api.processor.MessageProcessor;
import org.mule.api.processor.MessageProcessorBuilder;
import org.mule.processor.chain.DefaultMessageProcessorChainBuilder;

import java.util.List;

import org.springframework.beans.factory.FactoryBean;

/**
 * This will be in future versions of Mule.
 */
public class ChainMessageProcessorsFactoryBean implements FactoryBean, MuleContextAware
{

    protected MuleContext muleContext;
    protected List messageProcessors;
    protected Class<?> messageProcessorClass;
    
    public ChainMessageProcessorsFactoryBean()
    {
        super();
    }

    public void setMessageProcessorClass(Class<?> messageProcessorClass)
    {
        this.messageProcessorClass = messageProcessorClass;
    }

    public Class getObjectType()
    {
        return MessageProcessor.class;
    }
    
    public void setMessageProcessors(List messageProcessors)
    {
        this.messageProcessors = messageProcessors;
    }

    public Object getObject() throws Exception
    {
        DefaultMessageProcessorChainBuilder builder = new DefaultMessageProcessorChainBuilder();

        InterceptingMessageProcessor mp = createMessageProcessor();
        
        builder.chain(mp);
        for (Object processor : messageProcessors)
        {
            if (processor instanceof MessageProcessor)
            {
                builder.chain((MessageProcessor) processor);
            }
            else if (processor instanceof MessageProcessorBuilder)
            {
                builder.chain((MessageProcessorBuilder) processor);
            }
            else
            {
                throw new IllegalArgumentException(
                    "MessageProcessorBuilder should only have MessageProcessor's or MessageProcessorBuilder's configured");
            }
        }
        return builder.build();
    }

    protected InterceptingMessageProcessor createMessageProcessor()
        throws InstantiationException, IllegalAccessException
    {
        InterceptingMessageProcessor mp = (InterceptingMessageProcessor) messageProcessorClass.newInstance();
        return mp;
    }

    public boolean isSingleton()
    {
        return false;
    }

    public void setMuleContext(MuleContext context)
    {
        this.muleContext = context;
    }

}
