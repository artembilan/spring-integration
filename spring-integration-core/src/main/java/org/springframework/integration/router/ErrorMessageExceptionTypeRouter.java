/*
 * Copyright 2002-2011 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.integration.router;

import java.util.Collections;
import java.util.List;

import org.springframework.beans.factory.BeanClassLoaderAware;
import org.springframework.jmx.export.annotation.ManagedOperation;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.util.ClassUtils;

/**
 * A Message Router that resolves the target {@link MessageChannel} for
 * messages whose payload is an Exception. The channel resolution is based upon
 * the most specific cause of the error for which a channel-mapping exists.
 *
 * @author Mark Fisher
 * @author Oleg Zhurakousky
 */
public class ErrorMessageExceptionTypeRouter extends AbstractMappingMessageRouter<Class<?>>
		implements BeanClassLoaderAware {

	private volatile ClassLoader classLoader;

	@Override
	public void setBeanClassLoader(ClassLoader classLoader) {
		this.classLoader = classLoader;
	}

	/**
	 * Add a channel mapping from the provided key to channel name.
	 *
	 * @param key The key.
	 * @param channelName The channel name.
	 */
	@Override
	@ManagedOperation
	public void setChannelMapping(String key, String channelName) {
		try {
			this.channelMappings.put(ClassUtils.forName(key, this.classLoader), channelName);
		}
		catch (ClassNotFoundException e) {
			throw new IllegalArgumentException(e);
		}
	}

	/**
	 * Remove a channel mapping for the given key if present.
	 *
	 * @param key The key.
	 */
	@Override
	@ManagedOperation
	public void removeChannelMapping(String key) {
		try {
			this.channelMappings.remove(ClassUtils.forName(key, this.classLoader));
		}
		catch (ClassNotFoundException e) {
			throw new IllegalArgumentException(e);
		}
	}

	@Override
	protected List<Object> getChannelKeys(Message<?> message) {
		Class<?> mostSpecificCause = null;
		Object payload = message.getPayload();
		if (payload instanceof Throwable) {
			Throwable cause = (Throwable) payload;
			while (cause != null) {
				Class<?> causeClass = cause.getClass();
				if (this.getChannelMappings().keySet().contains(causeClass)) {
					mostSpecificCause = causeClass;
				}
				cause = cause.getCause();
			}
		}
		return Collections.singletonList((Object) mostSpecificCause);
	}
}
