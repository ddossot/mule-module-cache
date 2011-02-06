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

import org.mule.DefaultMuleMessage;
import org.mule.api.MuleMessage;
import org.mule.api.client.MuleClient;
import org.mule.client.DefaultLocalMuleClient;
import org.mule.tck.FunctionalTestCase;

public class MessageCacheTest extends FunctionalTestCase
{
    public void testMessages() throws Exception
    {
        MuleClient client = new DefaultLocalMuleClient(muleContext);
        
        MuleMessage msg = client.send("vm://test", new DefaultMuleMessage("test", muleContext));
        assertEquals(0, msg.getPayload());
        
        msg = client.send("vm://test", new DefaultMuleMessage("test2", muleContext));
        assertEquals(1, msg.getPayload());
        
        msg = client.send("vm://test", new DefaultMuleMessage("test", muleContext));
        assertEquals(0, msg.getPayload());
        
        msg = client.send("vm://test", new DefaultMuleMessage("test2", muleContext));
        assertEquals(1, msg.getPayload());
    }
    @Override
    protected String getConfigResources()
    {
        return "cache-config.xml";
    }

}
