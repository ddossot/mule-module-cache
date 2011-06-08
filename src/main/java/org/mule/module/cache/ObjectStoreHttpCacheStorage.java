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

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.Set;

import org.codehaus.httpcache4j.HTTPRequest;
import org.codehaus.httpcache4j.HTTPResponse;
import org.codehaus.httpcache4j.cache.AbstractMapBasedCacheStorage;
import org.codehaus.httpcache4j.cache.CacheItem;
import org.codehaus.httpcache4j.cache.Key;
import org.codehaus.httpcache4j.payload.ByteArrayPayload;
import org.codehaus.httpcache4j.payload.Payload;
import org.mule.api.store.ObjectStore;
import org.mule.api.store.ObjectStoreException;
import org.mule.util.store.InMemoryObjectStore;

import edu.emory.mathcs.backport.java.util.Collections;

/**
 * Allows HttpCache4J to use {@link ObjectStore} for persistence. Code has been partially lifted from
 * {@link org.codehaus.httpcache4j.cache.MemoryCacheStorage}.
 */
public class ObjectStoreHttpCacheStorage extends AbstractMapBasedCacheStorage
{
    private static final String HTTP_CACHE_OBJECT_STORE_KEY = "HttpCache";

    // FIXME (DDO) constructor injection
    private final ObjectStore<HashMap<?, ?>> objectStore = new InMemoryObjectStore<HashMap<?, ?>>();

    public CacheItem get(final HTTPRequest request)
    {
        synchronized (objectStore)
        {
            try
            {
                if (!objectStore.contains(HTTP_CACHE_OBJECT_STORE_KEY))
                {
                    return null;
                }

                @SuppressWarnings("unchecked")
                final HashMap<Key, CacheItem> cache = (HashMap<Key, CacheItem>) objectStore.retrieve(HTTP_CACHE_OBJECT_STORE_KEY);

                for (final Entry<Key, CacheItem> entry : cache.entrySet())
                {
                    final Key key = entry.getKey();
                    if (request.getRequestURI().equals(key.getURI()) && key.getVary().matches(request))
                    {
                        return entry.getValue();
                    }
                }

                return null;
            }
            catch (final ObjectStoreException ose)
            {
                throw new RuntimeException(ose);
            }
        }
    }

    @Override
    protected HTTPResponse get(final Key key)
    {
        synchronized (objectStore)
        {
            try
            {
                if (!objectStore.contains(HTTP_CACHE_OBJECT_STORE_KEY))
                {
                    return null;
                }

                @SuppressWarnings("unchecked")
                final HashMap<Key, CacheItem> cache = (HashMap<Key, CacheItem>) objectStore.retrieve(HTTP_CACHE_OBJECT_STORE_KEY);

                final CacheItem cacheItem = cache.get(key);

                if (cacheItem == null)
                {
                    return null;
                }

                return cacheItem.getResponse();
            }
            catch (final ObjectStoreException ose)
            {
                throw new RuntimeException(ose);
            }
        }
    }

    public void invalidate(final URI uri)
    {
        synchronized (objectStore)
        {
            try
            {
                if (!objectStore.contains(HTTP_CACHE_OBJECT_STORE_KEY))
                {
                    return;
                }

                @SuppressWarnings("unchecked")
                final HashMap<Key, CacheItem> cache = (HashMap<Key, CacheItem>) objectStore.retrieve(HTTP_CACHE_OBJECT_STORE_KEY);

                final Set<Key> keysToRemove = new HashSet<Key>();
                for (final Key key : cache.keySet())
                {
                    if (key.getURI().equals(uri))
                    {
                        keysToRemove.add(key);
                    }
                }
                for (final Key keyToRemove : keysToRemove)
                {
                    cache.remove(keyToRemove);
                }

                if (!keysToRemove.isEmpty())
                {
                    objectStore.remove(HTTP_CACHE_OBJECT_STORE_KEY);
                    objectStore.store(HTTP_CACHE_OBJECT_STORE_KEY, cache);
                }
            }
            catch (final ObjectStoreException ose)
            {
                throw new RuntimeException(ose);
            }
        }
    }

    @Override
    protected void invalidate(final Key key)
    {
        synchronized (objectStore)
        {
            try
            {
                if (!objectStore.contains(HTTP_CACHE_OBJECT_STORE_KEY))
                {
                    return;
                }

                @SuppressWarnings("unchecked")
                final HashMap<Key, CacheItem> cache = (HashMap<Key, CacheItem>) objectStore.retrieve(HTTP_CACHE_OBJECT_STORE_KEY);
                if (cache.remove(key) != null)
                {
                    objectStore.remove(HTTP_CACHE_OBJECT_STORE_KEY);
                    objectStore.store(HTTP_CACHE_OBJECT_STORE_KEY, cache);
                }
            }
            catch (final ObjectStoreException ose)
            {
                throw new RuntimeException(ose);
            }
        }
    }

    public void clear()
    {
        synchronized (objectStore)
        {
            try
            {
                if (objectStore.contains(HTTP_CACHE_OBJECT_STORE_KEY))
                {
                    objectStore.remove(HTTP_CACHE_OBJECT_STORE_KEY);
                }
            }
            catch (final ObjectStoreException ose)
            {
                throw new RuntimeException(ose);
            }
        }
    }

    public int size()
    {
        return getCachedKeys().size();
    }

    public Iterator<Key> iterator()
    {
        return getCachedKeys().iterator();
    }

    @Override
    protected Payload createPayload(final Key key, final Payload payload, final InputStream stream)
        throws IOException
    {
        final ByteArrayPayload p = new ByteArrayPayload(stream, payload.getMimeType());
        if (p.isAvailable())
        {
            return p;
        }
        return null;
    }

    @Override
    protected HTTPResponse putImpl(final Key key, final HTTPResponse response)
    {
        synchronized (objectStore)
        {
            try
            {
                @SuppressWarnings("unchecked")
                final HashMap<Key, CacheItem> cache = (HashMap<Key, CacheItem>) (objectStore.contains(HTTP_CACHE_OBJECT_STORE_KEY)
                                                                                                                                  ? objectStore.remove(HTTP_CACHE_OBJECT_STORE_KEY)
                                                                                                                                  : new HashMap<Key, CacheItem>());
                cache.put(key, createCacheItem(response));
                objectStore.store(HTTP_CACHE_OBJECT_STORE_KEY, cache);
            }
            catch (final ObjectStoreException ose)
            {
                throw new RuntimeException(ose);
            }
        }
        return response;
    }

    private CacheItem createCacheItem(final HTTPResponse pCacheableResponse)
    {
        return new CacheItem(pCacheableResponse);
    }

    @SuppressWarnings("unchecked")
    private Set<Key> getCachedKeys()
    {
        synchronized (objectStore)
        {
            try
            {
                if (!objectStore.contains(HTTP_CACHE_OBJECT_STORE_KEY))
                {
                    return Collections.emptySet();
                }

                final HashMap<Key, CacheItem> cache = (HashMap<Key, CacheItem>) objectStore.retrieve(HTTP_CACHE_OBJECT_STORE_KEY);
                return cache.keySet();
            }
            catch (final ObjectStoreException ose)
            {
                throw new RuntimeException(ose);
            }
        }
    }
}
