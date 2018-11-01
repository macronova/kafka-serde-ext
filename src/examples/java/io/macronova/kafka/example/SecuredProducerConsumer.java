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
package io.macronova.kafka.example;

import java.util.Arrays;
import java.util.Properties;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.LongDeserializer;
import org.apache.kafka.common.serialization.LongSerializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import io.macronova.kafka.common.serialization.ChainedDeserializer;
import io.macronova.kafka.common.serialization.ChainedSerializer;
import io.macronova.kafka.common.serialization.DecryptDeserializer;
import io.macronova.kafka.common.serialization.EncryptSerializer;

/**
 * Example demonstrates how to introduce record-level encryption without changes
 * to application source code. Use combination of chained and encrypt serializers.
 */
public class SecuredProducerConsumer {
	public static void main(String[] args) {
		final String broker = "localhost:9092";
		final String topic = "topic1";

		final Properties producerProps = new Properties();
		producerProps.put( ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, broker );
		producerProps.put( ProducerConfig.CLIENT_ID_CONFIG, "my-app-1" );
		producerProps.put( ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, LongSerializer.class.getName() );
		producerProps.put( ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ChainedSerializer.class.getName() );
		// Configure chained serializer, so that it first transforms text to bytes and then encrypts byte array.
		producerProps.put( "0.serializer", StringSerializer.class.getName() );
		producerProps.put( "1.serializer", EncryptSerializer.class.getName() );
		producerProps.put( "1.transformation", "AES/ECB/PKCS5Padding" );
		producerProps.put( "1.secret", "770A8A65DA156D24EE2A093277530142" );
		final KafkaProducer<Long, String> producer = new KafkaProducer<>( producerProps );
		producer.send( new ProducerRecord<>( topic, "Hello, Macronova!" ) );
		producer.close();

		final Properties consumerProps = new Properties();
		consumerProps.put( ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, broker );
		consumerProps.put( ConsumerConfig.GROUP_ID_CONFIG, "my-app" );
		consumerProps.put( ConsumerConfig.CLIENT_ID_CONFIG, "my-app-2" );
		consumerProps.put( ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, LongDeserializer.class.getName() );
		consumerProps.put( ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ChainedDeserializer.class.getName() );
		// Configure chained deserializer, so that it first decodes encrypted byte array and then transforms output to text.
		consumerProps.put( "0.deserializer", DecryptDeserializer.class.getName() );
		consumerProps.put( "0.transformation", "AES/ECB/PKCS5Padding" );
		consumerProps.put( "0.secret", "770A8A65DA156D24EE2A093277530142" );
		consumerProps.put( "1.deserializer", StringDeserializer.class.getName() );
		final KafkaConsumer<Long, String> consumer = new KafkaConsumer<Long, String>( consumerProps );
		consumer.subscribe( Arrays.asList( topic ) );
		final ConsumerRecords<Long, String> records = consumer.poll( 1000 );
		for ( ConsumerRecord<Long, String> record : records ) {
			System.out.println( record.value() );
		}
		consumer.close();
	}
}
