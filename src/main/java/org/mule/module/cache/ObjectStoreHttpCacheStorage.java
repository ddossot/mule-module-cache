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
import java.util.Collections;
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

/**
 * Allows HttpCache4J to use {@link ObjectStore} for persistence. Code has been partially lifted from
 * {@link org.codehaus.httpcache4j.cache.MemoryCacheStorage}.
 */
public class ObjectStoreHttpCacheStorage extends AbstractMapBasedCacheStorage
{
    private interface ObjectStoreAction<V>
    {
        V run(ObjectStore<HashMap<Key, CacheItem>> objectStore) throws ObjectStoreException;
    };

    private interface CacheAction<V>
    {
        V run(HashMap<Key, CacheItem> cache) throws ObjectStoreException;
    };

    private interface VoidCacheAction
    {
        void run(HashMap<Key, CacheItem> cache) throws ObjectStoreException;
    };

    private static class ObjectStoreCacheAction<V> implements ObjectStoreAction<V>
    {
        private final CacheAction<V> cacheAction;
        private final V defaultResult;

        private ObjectStoreCacheAction(final CacheAction<V> cacheAction, final V defaultResult)
        {
            this.cacheAction = cacheAction;
            this.defaultResult = defaultResult;
        }

        public V run(final ObjectStore<HashMap<Key, CacheItem>> objectStore) throws ObjectStoreException
        {
            if (!objectStore.contains(HTTP_CACHE_OBJECT_STORE_KEY))
            {
                return defaultResult;
            }

            final HashMap<Key, CacheItem> cache = objectStore.remove(HTTP_CACHE_OBJECT_STORE_KEY);
            final V result = cacheAction.run(cache);
            objectStore.store(HTTP_CACHE_OBJECT_STORE_KEY, cache);
            return result;
        }
    }

    private static final String HTTP_CACHE_OBJECT_STORE_KEY = ObjectStoreHttpCacheStorage.class.getName();

    private final ObjectStore<HashMap<Key, CacheItem>> objectStore;

    public ObjectStoreHttpCacheStorage(final ObjectStore<HashMap<Key, CacheItem>> objectStore)
    {
        this.objectStore = objectStore;
    }

    public int size()
    {
        return getCachedKeys().size();
    }

    public Iterator<Key> iterator()
    {
        return getCachedKeys().iterator();
    }

    public CacheItem get(final HTTPRequest request)
    {
        return execute(new CacheAction<CacheItem>()
        {
            public CacheItem run(final HashMap<Key, CacheItem> cache) throws ObjectStoreException
            {
                for (final Entry<Key, CacheItem> entry : cache.entrySet())
                {
                    final Key key = entry.getKey();
                    if ((request.getRequestURI().equals(key.getURI())) && (key.getVary().matches(request)))
                    {
                        return entry.getValue();
                    }
                }

                return null;
            }
        });
    }

    @Override
    protected HTTPResponse get(final Key key)
    {
        return execute(new CacheAction<HTTPResponse>()
        {
            public HTTPResponse run(final HashMap<Key, CacheItem> cache) throws ObjectStoreException
            {
                final CacheItem cacheItem = cache.get(key);

                if (cacheItem == null)
                {
                    return null;
                }

                return cacheItem.getResponse();

            }
        });
    }

    public void invalidate(final URI uri)
    {
        execute(new VoidCacheAction()
        {
            public void run(final HashMap<Key, CacheItem> cache) throws ObjectStoreException
            {
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
            }
        });
    }

    @Override
    protected void invalidate(final Key key)
    {
        execute(new VoidCacheAction()
        {
            public void run(final HashMap<Key, CacheItem> cache) throws ObjectStoreException
            {
                cache.remove(key);
            }
        });
    }

    public void clear()
    {
        execute(new VoidCacheAction()
        {
            public void run(final HashMap<Key, CacheItem> cache) throws ObjectStoreException
            {
                cache.clear();
            }
        });
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
        return execute(new ObjectStoreAction<HTTPResponse>()
        {
            public HTTPResponse run(final ObjectStore<HashMap<Key, CacheItem>> objectStore)
                throws ObjectStoreException
            {
                final HashMap<Key, CacheItem> cache = objectStore.contains(HTTP_CACHE_OBJECT_STORE_KEY)
                                                                                                       ? objectStore.remove(HTTP_CACHE_OBJECT_STORE_KEY)
                                                                                                       : new HashMap<Key, CacheItem>();
                cache.put(key, createCacheItem(response));
                objectStore.store(HTTP_CACHE_OBJECT_STORE_KEY, cache);
                return response;
            }
        });
    }

    @SuppressWarnings("unchecked")
    private Set<Key> getCachedKeys()
    {
        return execute(new CacheAction<Set<Key>>()
        {
            public Set<Key> run(final HashMap<Key, CacheItem> cache) throws ObjectStoreException
            {
                return cache.keySet();
            }
        }, Collections.EMPTY_SET);
    }

    private CacheItem createCacheItem(final HTTPResponse pCacheableResponse)
    {
        return new CacheItem(pCacheableResponse);
    }

    private void execute(final VoidCacheAction action)
    {
        execute(new CacheAction<Void>()
        {
            public Void run(final HashMap<Key, CacheItem> cache) throws ObjectStoreException
            {
                action.run(cache);
                return null;
            }
        });
    }

    private <T> T execute(final CacheAction<T> action)
    {
        return execute(action, null);
    }

    private <T> T execute(final CacheAction<T> action, final T defaultResult)
    {
        return execute(new ObjectStoreCacheAction<T>(action, defaultResult));
    }

    private <T> T execute(final ObjectStoreAction<T> action)
    {
        synchronized (objectStore)
        {
            try
            {
                return action.run(objectStore);
            }
            catch (final ObjectStoreException ose)
            {
                throw new RuntimeException(ose);
            }
        }

    }
}
