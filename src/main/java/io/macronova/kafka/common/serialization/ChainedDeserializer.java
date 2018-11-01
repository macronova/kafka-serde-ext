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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.kafka.common.config.ConfigException;
import org.apache.kafka.common.serialization.Deserializer;

/**
 * Executes ordered chain of deserializers passing output of one to another.
 * <p/>
 * Example configuration:
 * <br/><blockquote><pre>
 * 0.deserializer = com.company.MyCustomDeserializer
 * 0.schema.registry.url = http://localhost:8081/
 * 1.deserializer = org.apache.kafka.common.serialization.StringDeserializer
 * </pre></blockquote>
 */
public class ChainedDeserializer extends BaseChainedSerDe implements Deserializer<Object> {
	public static final String DESERIALIZER_CLASS_CONFIG = "deserializer";
	private final List<Deserializer<Object>> deserializers = new ArrayList<>();

	@Override
	public void configure(Map<String, ?> configs, boolean isKey) {
		final Map<Integer, Map<String, Object>> configuration = parseChainConfigurations( configs );
		int position = 0;
		while ( configuration.containsKey( position ) ) {
			deserializers.add( createDeserializer( position, configuration.get( position ), isKey ) );
			++position;
		}
		if ( deserializers.isEmpty() ) {
			throw new ConfigException(
					String.format( "No deserializers configured. Please define '0.%s' property.", DESERIALIZER_CLASS_CONFIG )
			);
		}
	}

	private Deserializer<Object> createDeserializer(int position, Map<String, Object> config, boolean isKey) {
		final String deserializerClass = (String) config.get( DESERIALIZER_CLASS_CONFIG );
		if ( deserializerClass == null ) {
			throw new ConfigException(
					String.format(
							"Please specify property '%d.%s' which should reflect child deserializer class.",
							position, DESERIALIZER_CLASS_CONFIG
					)
			);
		}
		try {
			final Deserializer<Object> deserializer = (Deserializer<Object>) Class.forName( deserializerClass ).newInstance();
			deserializer.configure( config, isKey );
			return deserializer;
		}
		catch ( Exception e ) {
			throw new ConfigException(
					String.format( "Failed to instantiate deserializer [%s]: %s.", deserializerClass, e.getMessage() )
			);
		}
	}

	@Override
	public Object deserialize(String topic, byte[] data) {
		Object result = data;
		for ( Deserializer<Object> deserializer : deserializers ) {
			result = deserializer.deserialize( topic, (byte[]) result );
		}
		return result;
	}

	@Override
	public void close() {
		deserializers.clear();
	}
}
