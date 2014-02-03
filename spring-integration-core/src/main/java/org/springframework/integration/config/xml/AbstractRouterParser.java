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

package org.springframework.integration.config.xml;

import java.util.List;

import org.w3c.dom.Element;

import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.ManagedMap;
import org.springframework.beans.factory.xml.ParserContext;
import org.springframework.integration.config.ExpressionFactoryBean;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.util.xml.DomUtils;

/**
 * Base parser for routers.
 *
 * @author Mark Fisher
 */
public abstract class AbstractRouterParser extends AbstractConsumerEndpointParser {

	@Override
	protected final BeanDefinitionBuilder parseHandler(Element element, ParserContext parserContext) {
		BeanDefinitionBuilder builder = this.doParseRouter(element, parserContext);

		IntegrationNamespaceUtils.setReferenceIfAttributeDefined(builder, element, "default-output-channel");
		IntegrationNamespaceUtils.setValueIfAttributeDefined(builder, element, "timeout");
		IntegrationNamespaceUtils.setValueIfAttributeDefined(builder, element, "resolution-required");
		IntegrationNamespaceUtils.setValueIfAttributeDefined(builder, element, "apply-sequence");
		IntegrationNamespaceUtils.setValueIfAttributeDefined(builder, element, "ignore-send-failures");

		// check if mapping is provided otherwise returned values will be treated as channel names
		List<Element> mappingElements = DomUtils.getChildElementsByTagName(element, "mapping");
		if (!CollectionUtils.isEmpty(mappingElements)) {
			ManagedMap<Object, String> channelMappings = new ManagedMap<Object, String>();
			for (Element mappingElement : mappingElements) {
				String value = mappingElement.getAttribute(this.getMappingKeyAttributeName());
				String expression = mappingElement.getAttribute(IntegrationNamespaceUtils.EXPRESSION_ATTRIBUTE);
				boolean hasAttributeValue = StringUtils.hasText(value);
				boolean hasAttributeExpression = StringUtils.hasText(expression);
				if (hasAttributeValue && hasAttributeExpression){
					parserContext.getReaderContext().error("Only one of '" + this.getMappingKeyAttributeName() +
							"' or 'expression' is allowed", element);
				}

				if (!hasAttributeValue && !hasAttributeExpression){
					parserContext.getReaderContext().error("One of '" + this.getMappingKeyAttributeName()
							+ "' or 'expression' is required", element);
				}

				if (hasAttributeValue && "value".equals(this.getMappingKeyAttributeName())) {
					expression = "'" + value + "' == #root";
				}

				Object key = value;

				if (expression != null) {
					key = BeanDefinitionBuilder.genericBeanDefinition(ExpressionFactoryBean.class)
							.addConstructorArgReference(expression).getBeanDefinition();
				}

				channelMappings.put(key, mappingElement.getAttribute("channel"));
			}
			builder.addPropertyValue("channelMappings", channelMappings);
		}
		return builder;
	}

	/**
	 * Returns the name of the attribute that provides a key for the
	 * channel mappings. This can be overridden by subclasses.
	 *
	 * @return The mapping key attribute name.
	 */
	protected String getMappingKeyAttributeName() {
		return "value";
	}

	protected abstract BeanDefinitionBuilder doParseRouter(Element element, ParserContext parserContext);

}
