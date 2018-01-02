/*
 * Copyright 2017-2018 the original author or authors.
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

package org.springframework.integration.webflux.config;

import org.w3c.dom.Element;

import org.springframework.beans.factory.config.RuntimeBeanReference;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.xml.ParserContext;
import org.springframework.integration.config.xml.IntegrationNamespaceUtils;
import org.springframework.integration.http.config.HttpOutboundGatewayParser;
import org.springframework.integration.webflux.outbound.WebFluxRequestExecutingMessageHandler;
import org.springframework.util.StringUtils;

/**
 * Parser for the 'outbound-gateway' element of the webflux namespace.
 *
 * @author Artem Bilan
 *
 * @since 5.0
 */
public class WebFluxOutboundGatewayParser extends HttpOutboundGatewayParser {

	@Override
	protected BeanDefinitionBuilder getBuilder(Element element, ParserContext parserContext) {
		BeanDefinitionBuilder builder =
				BeanDefinitionBuilder.genericBeanDefinition(WebFluxRequestExecutingMessageHandler.class);

		String webClientRef = element.getAttribute("web-client");
		if (StringUtils.hasText(webClientRef)) {
			builder.getBeanDefinition()
					.getConstructorArgumentValues()
					.addIndexedArgumentValue(1, new RuntimeBeanReference(webClientRef));
		}

		IntegrationNamespaceUtils.setValueIfAttributeDefined(builder, element, "reply-payload-to-flux");
		IntegrationNamespaceUtils.setReferenceIfAttributeDefined(builder, element, "body-extractor");
		return builder;
	}

}
