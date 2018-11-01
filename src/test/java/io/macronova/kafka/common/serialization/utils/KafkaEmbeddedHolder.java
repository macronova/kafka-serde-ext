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
package io.macronova.kafka.common.serialization.utils;

import kafka.common.KafkaException;
import org.springframework.kafka.test.rule.KafkaEmbedded;

public class KafkaEmbeddedHolder {
	private static KafkaEmbedded kafkaEmbedded = null;
	private static boolean started = false;
	private static int topicId = 0;
	private static String topicPrefix = "topic";

	public static KafkaEmbedded getKafkaEmbedded() {
		if ( ! started ) {
			try {
				// Every integration test will work on separate Kafka topic.
				++topicId;
				kafkaEmbedded = new KafkaEmbedded( 1, false, topicName() );
				kafkaEmbedded.setKafkaPorts( 9092 );
				kafkaEmbedded.before();
			}
			catch ( Exception e ) {
				throw new KafkaException( e );
			}
			started = true;
		}
		return kafkaEmbedded;
	}

	public static void destroy() {
		if ( started ) {
			try {
				kafkaEmbedded.destroy();
			}
			catch ( Exception e ) {
				// Ignore.
			}
			kafkaEmbedded = null;
			started = false;
		}
	}

	public static String topicName() {
		return topicPrefix + topicId;
	}

	private KafkaEmbeddedHolder() {
		super();
	}
}
