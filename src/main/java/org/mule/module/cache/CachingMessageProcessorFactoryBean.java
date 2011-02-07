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

import org.mule.api.processor.InterceptingMessageProcessor;

import org.springmodules.cache.CachingModel;
import org.springmodules.cache.provider.CacheProviderFacade;

public class CachingMessageProcessorFactoryBean extends ChainMessageProcessorsFactoryBean
{
    private CacheKeyGenerator keyGenerator;
    private CacheProviderFacade cache;
    private CachingModel cachingModel;
    private String cacheableExpression;
    private String keyGeneratorExpression;

    
    @Override
    protected InterceptingMessageProcessor createMessageProcessor()
        throws InstantiationException, IllegalAccessException
    {
        CachingMessageProcessor mp = new CachingMessageProcessor();
        mp.setCache(cache);
        mp.setCacheableExpression(cacheableExpression);
        mp.setCachingModel(cachingModel);
        mp.setKeyGenerator(keyGenerator);
        mp.setKeyGeneratorExpression(keyGeneratorExpression);
        mp.setMuleContext(muleContext);
        
        return mp;
    }

    public CacheKeyGenerator getKeyGenerator()
    {
        return keyGenerator;
    }

    public void setKeyGenerator(CacheKeyGenerator keyGenerator)
    {
        this.keyGenerator = keyGenerator;
    }

    public CacheProviderFacade getCache()
    {
        return cache;
    }

    public void setCache(CacheProviderFacade cacheProvider)
    {
        this.cache = cacheProvider;
    }

    public CachingModel getCachingModel()
    {
        return cachingModel;
    }

    public void setCachingModel(CachingModel cacheModel)
    {
        this.cachingModel = cacheModel;
    }

    public String getCacheableExpression()
    {
        return cacheableExpression;
    }

    public void setCacheableExpression(String cacheableExpression)
    {
        this.cacheableExpression = cacheableExpression;
    }

    public String getKeyGeneratorExpression()
    {
        return keyGeneratorExpression;
    }

    public void setKeyGeneratorExpression(String keyGeneratorExpression)
    {
        this.keyGeneratorExpression = keyGeneratorExpression;
    }

}
