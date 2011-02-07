Mule Cache Module
-----------------

Usage
=====

This modules provides the ability to cache messages inside a Mule message flow.
Here is a simple example:

	<?xml version="1.0" encoding="UTF-8"?>
	<mule xmlns="http://www.mulesoft.org/schema/mule/core"
	       xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
	       xmlns:spring="http://www.springframework.org/schema/beans"
	       xmlns:vm="http://www.mulesoft.org/schema/mule/vm"
	       xmlns:test="http://www.mulesoft.org/schema/mule/test"
	       xmlns:cache="http://www.mulesoft.org/schema/mule/cache"
	       xmlns:ehcache="http://www.springmodules.org/schema/ehcache"
	       xsi:schemaLocation="
	       http://www.springframework.org/schema/beans http://www.springframework.org/schema/beans/spring-beans-3.0.xsd
	       http://www.mulesoft.org/schema/mule/core http://www.mulesoft.org/schema/mule/core/3.2/mule.xsd
	       http://www.mulesoft.org/schema/mule/test http://www.mulesoft.org/schema/mule/test/3.2/mule-test.xsd
	       http://www.mulesoft.org/schema/mule/cache http://www.mulesoft.org/schema/mule/cache/3.2/mule-cache.xsd
	       http://www.springmodules.org/schema/ehcache http://www.springmodules.org/schema/cache/springmodules-ehcache.xsd
	       http://www.mulesoft.org/schema/mule/vm http://www.mulesoft.org/schema/mule/vm/3.2/mule-s.xsd">
	
	    <spring:beans>
	        <ehcache:config id="ehcache" 
	                        failQuietly="true"
	                        configLocation="classpath:ehcache.xml" />
	        
	        <spring:bean id="cachingModel" class="org.springmodules.cache.provider.ehcache.EhCacheCachingModel">
	            <spring:property name="cacheName" value="messages"/>
	        </spring:bean>
	    </spring:beans>
	    
	    <flow name="CachedFlow">
	        <inbound-endpoint address="vm://test" exchange-pattern="request-response"/>
	        <cache:cache-processor cache-ref="ehcache" 
	                               cachingModel-ref="cachingModel"/>
	        ....
	    </flow>
	    
	</mule>

This flow will cache incoming messages based on an MD5 hash of their payload. If your
payload is a stream, it will be read into memory so it can be returned during future 
invocations.

You can control whether or not messages are cacheable and what key is used for the
cache based on Mule expressions:

    <flow name="CachedFlowWithExpressions">
        <inbound-endpoint address="vm://test" exchange-pattern="request-response"/>
        <cache:cache-processor cache-ref="ehcache" 
                               cachingModel-ref="cachingModel"
                               cacheableExpression="#[xpath://cacheable[text() = 'true']]"
                               keyGeneratorExpression="#[xpath://key]"/>
        ...
    </flow>

This will cache messages which match the following form and store the message in the cache
with a key of '1'.
   
    <message>
        <cacheable>true</cacheable>
        <key>1</key>
    </message>
    
Configuring your Cache
======================
Underneath Spring Modules is used to configure caches. To find out more about configuring
a particular cache facade, see: http://wiki.apache.org/jackrabbit/Clustering.

Using in Maven
==============
To use in Maven, add the following dependency:
	
	<dependency>
		<groupId>org.mule.modules</groupId>
		<artifactId>mule-module-cache</artifactId>
		<version>1.0-SNAPSHOT</version>
	</dependency>