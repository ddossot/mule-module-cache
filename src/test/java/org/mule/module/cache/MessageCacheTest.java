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

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.mule.DefaultMuleMessage;
import org.mule.api.MuleException;
import org.mule.api.MuleMessage;
import org.mule.api.client.MuleClient;
import org.mule.tck.junit4.FunctionalTestCase;

public class MessageCacheTest extends FunctionalTestCase
{
    @Test
    public void testMessages() throws Exception
    {
        testCache("vm://test");
    }

    @Test
    public void testPayloadOnly() throws Exception
    {
        testCache("vm://testPayloadOnly");
    }

    private void testCache(final String vmEndpointUri) throws MuleException
    {
        final MuleClient client = muleContext.getClient();

        MuleMessage msg = client.send(vmEndpointUri, new DefaultMuleMessage("test", muleContext));
        assertEquals(0, msg.getPayload());

        msg = client.send(vmEndpointUri, new DefaultMuleMessage("test2", muleContext));
        assertEquals(1, msg.getPayload());

        msg = client.send(vmEndpointUri, new DefaultMuleMessage("test", muleContext));
        assertEquals(0, msg.getPayload());

        msg = client.send(vmEndpointUri, new DefaultMuleMessage("test2", muleContext));
        assertEquals(1, msg.getPayload());
    }

    @Override
    protected String getConfigResources()
    {
        return "cache-config.xml";
    }
}
