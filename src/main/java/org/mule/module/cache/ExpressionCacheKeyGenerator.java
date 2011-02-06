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

import java.io.Serializable;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Generates a cache key based on an MD5 digest of the message payload bytes.
 */
public class ExpressionCacheKeyGenerator implements CacheKeyGenerator
{
    public Serializable generateKey(MuleEvent event) throws MuleException
    {
        byte[] bytesOfMessage = event.getMessageAsBytes();

        try
        {
            MessageDigest md = MessageDigest.getInstance("MD5");
            return new String(md.digest(bytesOfMessage));
        }
        catch (NoSuchAlgorithmException e)
        {
            throw new DefaultMuleException(e);
        }
    }
}
