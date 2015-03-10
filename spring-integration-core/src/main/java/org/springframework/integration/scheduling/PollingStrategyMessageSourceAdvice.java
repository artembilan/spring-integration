/*
 * Copyright 2015 the original author or authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.integration.scheduling;

import java.util.Date;

import org.springframework.integration.aop.AbstractMessageSourceAdvice;
import org.springframework.integration.core.MessageSource;
import org.springframework.messaging.Message;
import org.springframework.scheduling.Trigger;
import org.springframework.scheduling.TriggerContext;

/**
 * @author Artem Bilan
 * @since 4.2
 */
public class PollingStrategyMessageSourceAdvice extends AbstractMessageSourceAdvice implements Trigger {

	private final PollingStrategy pollingStrategy;

	private volatile TriggerContext triggerContext;

	private volatile Message<?> lastMessage;

	public PollingStrategyMessageSourceAdvice(PollingStrategy pollingStrategy) {
		this.pollingStrategy = pollingStrategy;
	}

	@Override
	public boolean beforeReceive(MessageSource<?> source) {
		return this.pollingStrategy.beforeReceive(this.triggerContext, source);
	}

	@Override
	public Message<?> afterReceive(Message<?> result, MessageSource<?> source) {
		Message<?> message = this.pollingStrategy.afterReceive(this.triggerContext, result, source);
		this.lastMessage = message;
		return message;
	}

	@Override
	public Date nextExecutionTime(TriggerContext triggerContext) {
		if (triggerContext.lastScheduledExecutionTime() == null) {
			this.triggerContext = triggerContext;
			return new Date(System.currentTimeMillis() + this.pollingStrategy.getInitialDelay());
		}
		return this.pollingStrategy.nextExecutionTime(triggerContext, this.lastMessage);
	}

}
