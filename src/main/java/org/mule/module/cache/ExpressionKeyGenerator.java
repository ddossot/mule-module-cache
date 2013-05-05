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

import java.io.Serializable;

import org.mule.api.DefaultMuleException;
import org.mule.api.MuleEvent;
import org.mule.api.MuleException;

/**
 * Uses the Mule expression language to extract a key.
 */
public class ExpressionKeyGenerator implements CacheKeyGenerator
{
    private String expression;

    public Serializable generateKey(final MuleEvent event) throws MuleException
    {
        final Object o = event.getMuleContext().getExpressionManager().evaluate(expression, event);

        if (o instanceof Serializable)
        {
            return (Serializable) o;
        }
        else
        {
            throw new DefaultMuleException(
                "Cache key generator expression must return a serializable object.");
        }
    }

    public String getExpression()
    {
        return expression;
    }

    public void setExpression(final String expression)
    {
        this.expression = expression;
    }

}
