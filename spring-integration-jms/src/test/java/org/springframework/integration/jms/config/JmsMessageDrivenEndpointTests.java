/*
 * Copyright 2018-2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.integration.jms.config;

import static org.assertj.core.api.Assertions.assertThat;

import javax.jms.ConnectionFactory;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.channel.QueueChannel;
import org.springframework.integration.config.EnableIntegration;
import org.springframework.integration.jms.ChannelPublishingJmsMessageListener;
import org.springframework.integration.jms.JmsMessageDrivenEndpoint;
import org.springframework.integration.test.util.TestUtils;
import org.springframework.jms.connection.CachingConnectionFactory;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.jms.listener.AbstractMessageListenerContainer;
import org.springframework.jms.listener.DefaultMessageListenerContainer;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

/**
 * @author Gary Russell
 * @since 5.1
 *
 */
@SpringJUnitConfig
@DirtiesContext
public class JmsMessageDrivenEndpointTests {

	@Test
	public void testStopStart(@Autowired JmsTemplate template,
			@Autowired JmsMessageDrivenEndpoint endpoint, @Autowired QueueChannel out) {
		template.convertAndSend("stop.start", "foo");
		assertThat(out.receive(10_000).getPayload()).isEqualTo("foo");
		endpoint.stop();
		assertThat(TestUtils.getPropertyValue(endpoint, "listenerContainer.sharedConnection")).isNull();
		endpoint.start();
		template.convertAndSend("stop.start", "bar");
		assertThat(out.receive(10_000).getPayload()).isEqualTo("bar");
	}

	@Configuration
	@EnableIntegration
	public static class Config {


		@Bean
		public ConnectionFactory cf() {
			return new ActiveMQConnectionFactory("vm://localhost?broker.persistent=false");
		}

		@Bean
		public CachingConnectionFactory ccf() {
			return new CachingConnectionFactory(cf());
		}

		@Bean
		public JmsTemplate template() {
			return new JmsTemplate(ccf());
		}

		@Bean
		public JmsMessageDrivenEndpoint inbound() {
			JmsMessageDrivenEndpoint endpoint = new JmsMessageDrivenEndpoint(container(), listener());
			return endpoint;
		}

		@Bean
		public AbstractMessageListenerContainer container() {
			DefaultMessageListenerContainer container = new DefaultMessageListenerContainer();
			container.setConnectionFactory(cf());
			container.setSessionTransacted(true);
			container.setDestinationName("stop.start");
			return container;
		}

		@Bean
		public ChannelPublishingJmsMessageListener listener() {
			ChannelPublishingJmsMessageListener listener = new ChannelPublishingJmsMessageListener();
			listener.setRequestChannel(out());
			return listener;
		}

		@Bean
		public QueueChannel out() {
			return new QueueChannel();
		}

	}

}
