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
import org.apache.kafka.common.serialization.Serializer;

/**
 * Executes ordered chain of serializers passing output of one to another.
 * <p/>
 * Example configuration:
 * <br/><blockquote><pre>
 * 0.serializer = org.apache.kafka.common.serialization.StringDeserializer
 * 1.serializer = com.company.MyCustomDeserializer
 * 1.schema.registry.url = http://localhost:8081/
 * </pre></blockquote>
 */
public class ChainedSerializer extends BaseChainedSerDe implements Serializer<Object> {
	public static final String SERIALIZER_CLASS_CONFIG = "serializer";
	private final List<Serializer<Object>> serializers = new ArrayList<>();

	@Override
	public void configure(Map<String, ?> configs, boolean isKey) {
		final Map<Integer, Map<String, Object>> configuration = parseChainConfigurations( configs );
		int position = 0;
		while ( configuration.containsKey( position ) ) {
			serializers.add( createSerializer( position, configuration.get( position ), isKey ) );
			++position;
		}
		if ( serializers.isEmpty() ) {
			throw new ConfigException(
					String.format( "No serializers configured. Please define '0.%s' property.", SERIALIZER_CLASS_CONFIG )
			);
		}
	}

	private Serializer<Object> createSerializer(int position, Map<String, Object> config, boolean isKey) {
		final String serializerClass = (String) config.get( SERIALIZER_CLASS_CONFIG );
		if ( serializerClass == null ) {
			throw new ConfigException(
					String.format(
							"Please specify property '%d.%s' which should reflect child serializer class.",
							position, SERIALIZER_CLASS_CONFIG
					)
			);
		}
		try {
			final Serializer<Object> serializer = (Serializer<Object>) Class.forName( serializerClass ).newInstance();
			serializer.configure( config, isKey );
			return serializer;
		}
		catch ( Exception e ) {
			throw new ConfigException(
					String.format( "Failed to instantiate serializer [%s]: %s.", serializerClass, e.getMessage() )
			);
		}
	}

	@Override
	public byte[] serialize(String topic, Object data) {
		Object result = data;
		for ( Serializer<Object> serializer : serializers ) {
			result = serializer.serialize( topic, result );
		}
		return (byte[]) result;
	}

	@Override
	public void close() {
		serializers.clear();
	}
}
