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

package org.springframework.integration.support;

import org.springframework.core.AttributeAccessor;
import org.springframework.integration.message.EnhancedErrorMessage;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.ErrorMessage;

/**
 * A simple {@link ErrorMessageStrategy} implementations which produces
 * {@link EnhancedErrorMessage} if the {@link AttributeAccessor} has
 * {@link ErrorMessageUtils#INPUT_MESSAGE_CONTEXT_KEY} attribute.
 * Otherwise plain {@link ErrorMessage} with the {@code throwable} as {@code payload}.
 *
 * @author Gary Russell
 * @author Artem Bilan
 *
 * @since 4.3.10
 *
 * @see ErrorMessageUtils
 */
public class DefaultErrorMessageStrategy implements ErrorMessageStrategy {

	@Override
	public ErrorMessage buildErrorMessage(Throwable throwable, AttributeAccessor context) {
		Object inputMessage = context.getAttribute(ErrorMessageUtils.INPUT_MESSAGE_CONTEXT_KEY);
		return inputMessage instanceof Message
				? new EnhancedErrorMessage((Message<?>) inputMessage, throwable)
				: new ErrorMessage(throwable);
	}

}
