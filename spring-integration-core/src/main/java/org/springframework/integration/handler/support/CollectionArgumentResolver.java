/*
 * Copyright 2017 the original author or authors.
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

package org.springframework.integration.handler.support;

import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.stream.Collectors;

import org.springframework.core.MethodParameter;
import org.springframework.core.convert.TypeDescriptor;
import org.springframework.integration.util.AbstractExpressionEvaluator;
import org.springframework.messaging.Message;
import org.springframework.messaging.handler.invocation.HandlerMethodArgumentResolver;
import org.springframework.util.Assert;

/**
 * @author Artem Bilan
 *
 * @since 5.0
 */
public class CollectionArgumentResolver extends AbstractExpressionEvaluator
		implements HandlerMethodArgumentResolver {

	private final boolean canProcessMessageList;

	public CollectionArgumentResolver(boolean canProcessMessageList) {
		this.canProcessMessageList = canProcessMessageList;
	}

	@Override
	public boolean supportsParameter(MethodParameter parameter) {
		Class<?> parameterType = parameter.getParameterType();
		return Collection.class.isAssignableFrom(parameterType)
				|| Iterator.class.isAssignableFrom(parameterType)
				|| parameterType.isArray();
	}

	@Override
	@SuppressWarnings("unchecked")
	public Object resolveArgument(MethodParameter parameter, Message<?> message) throws Exception {
		Object value = message.getPayload();

		if (this.canProcessMessageList) {
			Assert.state(value instanceof Collection,
					"This Argument Resolver support only messages with payload as Collection<Message<?>>");
			Collection<Message<?>> messages = (Collection<Message<?>>) value;

			parameter.increaseNestingLevel();
			if (Message.class.isAssignableFrom(parameter.getNestedParameterType())) {
				value = messages;
			}
			else {
				value = messages.stream()
						.map(Message::getPayload)
						.collect(Collectors.toList());
			}
			parameter.decreaseNestingLevel();
		}
		if (Iterator.class.isAssignableFrom(parameter.getParameterType())) {
			if (value instanceof Iterable) {
				return ((Iterable) value).iterator();
			}
			else {
				return Collections.singleton(value).iterator();
			}
		}
		else {
			return getEvaluationContext()
					.getTypeConverter()
					.convertValue(value,
							TypeDescriptor.forObject(value),
							TypeDescriptor.valueOf(parameter.getParameterType()));
		}
	}

}
