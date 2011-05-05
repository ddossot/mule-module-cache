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

import org.mule.config.spring.parsers.generic.ChildDefinitionParser;
import org.springframework.beans.factory.xml.NamespaceHandlerSupport;

public class CacheNamespaceHandler extends NamespaceHandlerSupport
{
    public void init()
    {
        registerBeanDefinitionParser("cache-processor", new ChildDefinitionParser("messageProcessor",
            CachingMessageProcessor.class));
        registerBeanDefinitionParser("http-cache-processor", new ChildDefinitionParser("messageProcessor",
            HttpCachingMessageProcessor.class));
    }

}
