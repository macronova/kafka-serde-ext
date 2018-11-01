/*
 * Copyright 2018 Macronova.
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
package io.macronova.kafka.common.serialization;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public abstract class BaseChainedSerDe {
	private static final Pattern serDeConfigPrefix = Pattern.compile( "^\\d+\\.(.+)" );

	/**
	 * Group configuration properties by position prefix.
	 * Property name pattern: {@code x.my.property}, where X = {0, 1, 2, ...}.
	 *
	 * @param configs Flat configuration map.
	 * @return Multiple configuration maps grouped by position prefix.
	 */
	protected Map<Integer, Map<String, Object>> parseChainConfigurations(Map<String, ?> configs) {
		final Map<Integer, Map<String, Object>> configuration = new HashMap<>();
		for ( Map.Entry<String, ?> entry : configs.entrySet() ) {
			final String key = entry.getKey();
			final Matcher matcher = serDeConfigPrefix.matcher( key );
			if ( matcher.matches() ) {
				final Integer id = Integer.valueOf( key.substring( 0, key.indexOf( '.' ) ) );
				if ( ! configuration.containsKey( id ) ) {
					configuration.put( id, new HashMap<>() );
				}
				configuration.get( id ).put( matcher.group( 1 ), entry.getValue() );
			}
		}
		return configuration;
	}
}
