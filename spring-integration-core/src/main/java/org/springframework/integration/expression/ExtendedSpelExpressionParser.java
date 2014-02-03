/*
 * Copyright 2014 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.integration.expression;

import org.springframework.expression.Expression;
import org.springframework.expression.ParseException;
import org.springframework.expression.ParserContext;
import org.springframework.expression.common.LiteralExpression;
import org.springframework.expression.spel.SpelParserConfiguration;
import org.springframework.expression.spel.standard.SpelExpressionParser;

/**
 * The {@link SpelExpressionParser} extension, which check the {@code expressionString}
 * to be single-quotes and return a {@link LiteralExpression} instance, otherwise delegate
 * to the super class.
 *
 * @author Artem Bilan
 * @since 4.0
 */
public class ExtendedSpelExpressionParser extends SpelExpressionParser {

	public ExtendedSpelExpressionParser() {
		super();
	}

	public ExtendedSpelExpressionParser(SpelParserConfiguration configuration) {
		super(configuration);
	}

	@Override
	public Expression parseExpression(String expressionString, ParserContext context) throws ParseException {
		if (expressionString.startsWith("'") && expressionString.endsWith("'")) {
			return new LiteralExpression(expressionString.replaceFirst("'", "").substring(0, expressionString.length() - 2));
		}
		else {
			return super.parseExpression(expressionString, context);
		}
	}

}
