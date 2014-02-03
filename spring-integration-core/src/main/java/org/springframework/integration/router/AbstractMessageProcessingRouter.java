/*
 * Copyright 2002-2013 the original author or authors.
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

import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.integration.expression.ExtendedSpelExpressionParser;
import org.springframework.jmx.export.annotation.ManagedOperation;
import org.springframework.messaging.Message;
import org.springframework.integration.handler.AbstractMessageProcessor;
import org.springframework.integration.handler.MessageProcessor;
import org.springframework.util.Assert;

/**
 * A base class for Router implementations that delegate to a
 * {@link MessageProcessor} instance.
 *
 * @author Mark Fisher
 * @since 2.0
 */
class AbstractMessageProcessingRouter extends AbstractMappingMessageRouter<Expression> {

	private final MessageProcessor<?> messageProcessor;


	AbstractMessageProcessingRouter(MessageProcessor<?> messageProcessor) {
		Assert.notNull(messageProcessor, "messageProcessor must not be null");
		this.messageProcessor = messageProcessor;
	}

	@Override
	public final void onInit() {
		super.onInit();
		if (this.messageProcessor instanceof AbstractMessageProcessor) {
			((AbstractMessageProcessor<?>) this.messageProcessor).setConversionService(this.getConversionService());
		}
		if (this.messageProcessor instanceof BeanFactoryAware) {
			((BeanFactoryAware) this.messageProcessor).setBeanFactory(this.getBeanFactory());
		}
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
		this.channelMappings.put(PARSER.parseExpression(key), channelName);
	}

	/**
	 * Remove a channel mapping for the given key if present.
	 *
	 * @param key The key.
	 */
	@Override
	@ManagedOperation
	public void removeChannelMapping(String key) {
		this.channelMappings.remove(PARSER.parseExpression(key));
	}

	@Override
	protected List<Object> getChannelKeys(Message<?> message) {
		Object result = this.messageProcessor.processMessage(message);
		return Collections.singletonList(result);
	}

}
