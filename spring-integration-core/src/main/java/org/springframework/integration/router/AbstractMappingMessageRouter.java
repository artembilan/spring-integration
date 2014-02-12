/*
 * Copyright 2002-2014 the original author or authors.
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
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.integration.expression.ExtendedSpelExpressionParser;
import org.springframework.integration.support.channel.BeanFactoryChannelResolver;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.core.DestinationResolutionException;
import org.springframework.messaging.core.DestinationResolver;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * Base class for all Message Routers that support mapping from arbitrary String values
 * to Message Channel names.
 *
 * @author Mark Fisher
 * @author Oleg Zhurakousky
 * @author Gunnar Hillert
 * @author Gary Russell
 * @author Artem Bilan
 * @since 2.1
 */
public abstract class AbstractMappingMessageRouter<M> extends AbstractMessageRouter implements MappingMessageRouterManagement {

	protected static final SpelExpressionParser PARSER = new ExtendedSpelExpressionParser();

	protected volatile Map<M, String> channelMappings = new ConcurrentHashMap<M, String>();

	private volatile DestinationResolver<MessageChannel> channelResolver;

	private volatile String prefix;

	private volatile String suffix;

	private volatile boolean resolutionRequired = true;


	/**
	 * Provide mappings from channel keys to channel names.
	 * Channel names will be resolved by the {@link DestinationResolver}.
	 *
	 * @param channelMappings The channel mappings.
	 */
	public void setChannelMappings(Map<M, String> channelMappings) {
		Map<M, String> oldChannelMappings = this.channelMappings;
		Map<M, String> newChannelMappings = new ConcurrentHashMap<M, String>();
		newChannelMappings.putAll(channelMappings);
		this.channelMappings = newChannelMappings;
		if (logger.isDebugEnabled()) {
			logger.debug("Channel mappings:" + oldChannelMappings
					+ " replaced with:" + newChannelMappings);
		}
	}

	/**
	 * Specify the {@link DestinationResolver} strategy to use.
	 * The default is a BeanFactoryChannelResolver.
	 * This is considered an infrastructural configuration option and
	 * as of 2.1 has been deprecated as a configuration-driven attribute.
	 *
	 * @param channelResolver The channel resolver.
	 */
	public void setChannelResolver(DestinationResolver<MessageChannel> channelResolver) {
		Assert.notNull(channelResolver, "'channelResolver' must not be null");
		this.channelResolver = channelResolver;
	}

	/**
	 * Specify a prefix to be added to each channel name prior to resolution.
	 *
	 * @param prefix The prefix.
	 */
	public void setPrefix(String prefix) {
		this.prefix = prefix;
	}

	/**
	 * Specify a suffix to be added to each channel name prior to resolution.
	 *
	 * @param suffix The suffix.
	 */
	public void setSuffix(String suffix) {
		this.suffix = suffix;
	}

	/**
	 * Specify whether this router should ignore any failure to resolve a channel name to
	 * an actual MessageChannel instance when delegating to the ChannelResolver strategy.
	 *
	 * @param resolutionRequired true if resolution is required.
	 */
	public void setResolutionRequired(boolean resolutionRequired) {
		this.resolutionRequired = resolutionRequired;
	}

	/**
	 * Returns an unmodifiable version of the channel mappings.
	 * This is intended for use by subclasses only.
	 *
	 * @return The channel mappings.
	 */
	protected Map<M, String> getChannelMappings() {
		return Collections.unmodifiableMap(this.channelMappings);
	}

	@Override
	public void onInit() {
		BeanFactory beanFactory = this.getBeanFactory();
		if (this.channelResolver == null && beanFactory != null) {
			this.channelResolver = new BeanFactoryChannelResolver(beanFactory);
		}
	}

	/**
	 * Subclasses must implement this method to return the channel keys.
	 * A "key" might be present in this router's "channelMappings", or it
	 * could be the channel's name or even the Message Channel instance itself.
	 *
	 * @param message The message.
	 * @return The channel keys.
	 */
	protected abstract List<Object> getChannelKeys(Message<?> message);


	@Override
	protected Collection<MessageChannel> determineTargetChannels(Message<?> message) {
		Collection<MessageChannel> channels = new ArrayList<MessageChannel>();
		Collection<Object> channelKeys = this.getChannelKeys(message);
		addToCollection(channels, channelKeys, message);
		return channels;
	}

	private MessageChannel resolveChannelForName(String channelName, Message<?> message) {
		if (this.channelResolver == null) {
			this.onInit();
		}
		Assert.state(this.channelResolver != null, "unable to resolve channel names, no ChannelResolver available");
		MessageChannel channel = null;
		try {
			channel = this.channelResolver.resolveDestination(channelName);
		}
		catch (DestinationResolutionException e) {
			if (this.resolutionRequired) {
				throw new MessagingException(message, "failed to resolve channel name '" + channelName + "'", e);
			}
		}
		if (channel == null && this.resolutionRequired) {
			throw new MessagingException(message, "failed to resolve channel name '" + channelName + "'");
		}
		return channel;
	}

	private void addChannelFromString(Collection<MessageChannel> channels, String channelKey, Message<?> message) {
		if (channelKey.indexOf(',') != -1) {
			for (String name : StringUtils.tokenizeToStringArray(channelKey, ",")) {
				addChannelFromString(channels, name, message);
			}
			return;
		}

		// if the channelMappings contains a mapping, we'll use the mapped value
		// otherwise, the String-based channelKey itself will be used as the channel name
		String channelName = channelKey;
		if (this.channelMappings.containsKey(channelKey)) {
			channelName = this.channelMappings.get(channelKey);
		}
		if (this.prefix != null) {
			channelName = this.prefix + channelName;
		}
		if (this.suffix != null) {
			channelName = channelName + this.suffix;
		}
		MessageChannel channel = resolveChannelForName(channelName, message);
		if (channel != null) {
			channels.add(channel);
		}
	}

	private void addToCollection(Collection<MessageChannel> channels, Collection<?> channelKeys, Message<?> message) {
		if (channelKeys == null) {
			return;
		}
		for (Object channelKey : channelKeys) {
			if (channelKey == null) {
				continue;
			}
			else if (channelKey instanceof MessageChannel) {
				channels.add((MessageChannel) channelKey);
			}
			else if (channelKey instanceof MessageChannel[]) {
				channels.addAll(Arrays.asList((MessageChannel[]) channelKey));
			}
			else if (channelKey instanceof String) {
				addChannelFromString(channels, (String) channelKey, message);
			}
			else if (channelKey instanceof String[]) {
				for (String indicatorName : (String[]) channelKey) {
					addChannelFromString(channels, indicatorName, message);
				}
			}
			else if (channelKey instanceof Collection) {
				addToCollection(channels, (Collection<?>) channelKey, message);
			}
			else if (this.getRequiredConversionService().canConvert(channelKey.getClass(), String.class)) {
				addChannelFromString(channels, this.getConversionService().convert(channelKey, String.class), message);
			}
			else {
				throw new MessagingException("unsupported return type for router [" + channelKey.getClass() + "]");
			}
		}
	}

}
