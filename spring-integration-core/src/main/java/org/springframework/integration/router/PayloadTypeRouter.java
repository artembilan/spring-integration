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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.springframework.beans.factory.BeanClassLoaderAware;
import org.springframework.jmx.export.annotation.ManagedOperation;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.util.ClassUtils;
import org.springframework.util.CollectionUtils;

/**
 * A Message Router that resolves the {@link MessageChannel} based on the
 * {@link Message Message's} payload type.
 *
 * @author Mark Fisher
 * @author Oleg Zhurakousky
 */
public class PayloadTypeRouter extends AbstractMappingMessageRouter<Class<?>> implements BeanClassLoaderAware {

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

	/**
	 * Selects the most appropriate channel name matching channel identifiers which are the
	 * fully qualified class names encountered while traversing the payload type hierarchy.
	 * To resolve ties and conflicts (e.g., Serializable and String) it will match:
	 * 1. Type name to channel identifier else...
	 * 2. Name of the subclass of the type to channel identifier else...
	 * 3. Name of the Interface of the type to channel identifier while also
	 *    preferring direct interface over indirect subclass
	 */
	@Override
	protected List<Object> getChannelKeys(Message<?> message) {
		if (CollectionUtils.isEmpty(this.getChannelMappings())) {
			return null;
		}
		Class<?> type = message.getPayload().getClass();
		Class<?> closestMatch =  this.findClosestMatch(type);
		return (closestMatch != null) ? Collections.<Object>singletonList(closestMatch) : null;
	}


	private Class<?> findClosestMatch(Class<?> type) {
		List<Class<?>> matches = new ArrayList<Class<?>>();
		for (Class<?> candidate : this.getChannelMappings().keySet()) {

			if (candidate.isAssignableFrom(type)) {
				matches.add(candidate);
			}

		}
		if (matches.size() > 1) { // ambiguity
			throw new IllegalStateException(
					"Unresolvable ambiguity while attempting to find closest match for [" + type.getName() + "]. Found: " + matches);
		}
		if (CollectionUtils.isEmpty(matches)) { // no match
			return null;
		}
		// we have a winner
		return matches.get(0);
	}

}
