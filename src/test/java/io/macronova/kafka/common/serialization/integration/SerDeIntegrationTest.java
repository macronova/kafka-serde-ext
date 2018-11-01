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
package io.macronova.kafka.common.serialization.integration;

import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import io.macronova.kafka.common.serialization.BaseTestCase;
import io.macronova.kafka.common.serialization.ReverseBytesSerDe;
import io.macronova.kafka.common.serialization.utils.KafkaEmbeddedHolder;
import io.macronova.kafka.common.serialization.utils.TestCondition;
import io.macronova.kafka.common.serialization.utils.TestUtils;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.KafkaException;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.test.rule.KafkaEmbedded;
import org.springframework.kafka.test.utils.KafkaTestUtils;

public class SerDeIntegrationTest extends BaseTestCase {
	private KafkaEmbedded embeddedKafka = null;

	@Before
	public void setUp() {
		embeddedKafka = KafkaEmbeddedHolder.getKafkaEmbedded();
	}

	@After
	public void tearDown() {
		KafkaEmbeddedHolder.destroy();
		embeddedKafka = null;
	}

	@Test
	public void testChainedSerialization() throws Exception {
		final String message = "Hello, World!";

		// Send record with reversed bytes.
		final Map<String, Object> producerProps = new HashMap<String, Object>() { {
			put( ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, "io.macronova.kafka.common.serialization.ChainedSerializer" );
			put( "0.serializer", StringSerializer.class.getName() );
			put( "1.serializer", ReverseBytesSerDe.class.getName() );
		} };
		produceRecords( producerProps, message );

		// Read record as bytes and reverse them manually to assure proper serialization.
		final Map<String, Object> consumerProps = new HashMap<>();
		consumerProps.put( ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class.getName() );
		final ConsumerRecords<Integer, byte[]> records = consumeRecords( consumerProps );

		Assert.assertEquals( 1, records.count() );
		Assert.assertArrayEquals( TestUtils.reverse( message.getBytes() ), records.iterator().next().value() );
	}

	@Test
	public void testChainedDeserialization() throws Exception {
		final String message = "Hello, World!";

		// Send record with reversed bytes.
		final Map<String, Object> producerProps = new HashMap<String, Object>() { {
			put( ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, "io.macronova.kafka.common.serialization.ChainedSerializer" );
			put( "0.serializer", StringSerializer.class.getName() );
			put( "1.serializer", ReverseBytesSerDe.class.getName() );
		} };
		produceRecords( producerProps, message );

		// Use chained deserializer to read original data.
		final Map<String, Object> consumerProps = new HashMap<String, Object>() { {
			put( ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, "io.macronova.kafka.common.serialization.ChainedDeserializer" );
			put( "0.deserializer", ReverseBytesSerDe.class.getName() );
			put( "1.deserializer", StringDeserializer.class.getName() );
		} };
		final ConsumerRecords<Integer, String> records = consumeRecords( consumerProps );

		Assert.assertEquals( 1, records.count() );
		Assert.assertEquals( message, records.iterator().next().value() );
	}

	@Test
	public void testEncryptionDecryptionSecret() throws Exception {
		final String transformation = "AES/CBC/PKCS5Padding";
		final String secret = "770A8A65DA156D24EE2A093277530142";

		// Send record with encrypted content.
		final Map<String, Object> producerProps = new HashMap<String, Object>() { {
			put( ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, "io.macronova.kafka.common.serialization.ChainedSerializer" );
			put( "0.serializer", StringSerializer.class.getName() );
			put( "1.serializer", "io.macronova.kafka.common.serialization.EncryptSerializer" );
			put( "1.transformation", transformation );
			put( "1.secret", secret );
		} };

		// Use chained and decrypt deserializers to read original message.
		final Map<String, Object> consumerProps = new HashMap<String, Object>() { {
			put( ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, "io.macronova.kafka.common.serialization.ChainedDeserializer" );
			put( "0.deserializer", "io.macronova.kafka.common.serialization.DecryptDeserializer" );
			put( "0.transformation", transformation );
			put( "0.secret", secret );
			put( "1.deserializer", StringDeserializer.class.getName() );
		} };

		checkSerializerDeserializer( producerProps, consumerProps );
	}

	@Test
	public void testEncryptionDecryptionCertificate() throws Exception {
		final String transformation = "RSA";

		// Send record with encrypted content.
		final Map<String, Object> producerProps = new HashMap<String, Object>() { {
			put( ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, "io.macronova.kafka.common.serialization.ChainedSerializer" );
			put( "0.serializer", StringSerializer.class.getName() );
			put( "1.serializer", "io.macronova.kafka.common.serialization.EncryptSerializer" );
			put( "1.transformation", transformation );
			put( "1.key.store.path", new File( keyStorePath() ).getAbsolutePath() );
			put( "1.key.store.password", keyStorePassword() );
			put( "1.key.store.alias", keyAlias() );
		} };

		// Use chained and decrypt deserializers to read original message.
		final Map<String, Object> consumerProps = new HashMap<String, Object>() { {
			put( ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, "io.macronova.kafka.common.serialization.ChainedDeserializer" );
			put( "0.deserializer", "io.macronova.kafka.common.serialization.DecryptDeserializer" );
			put( "0.transformation", transformation );
			put( "0.key.store.path", new File( keyStorePath() ).getAbsolutePath() );
			put( "0.key.store.password", keyStorePassword() );
			put( "0.key.store.alias", keyAlias() );
			put( "0.key.store.alias.password", keyAliasPassword() );
			put( "1.deserializer", StringDeserializer.class.getName() );
		} };

		checkSerializerDeserializer( producerProps, consumerProps );
	}

	@Test
	public void testHybridEncryption() throws Exception {
		// Send record with hybrid encrypted content.
		final Map<String, Object> producerProps = new HashMap<String, Object>() { {
			put( ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, "io.macronova.kafka.common.serialization.ChainedSerializer" );
			put( "0.serializer", StringSerializer.class.getName() );
			put( "1.serializer", "io.macronova.kafka.common.serialization.HybridEncryptSerializer" );
			put( "1.symmetric.transformation", "AES/CBC/PKCS5Padding" );
			put( "1.asymmetric.transformation", "RSA/None/PKCS1Padding" );
			put( "1.asymmetric.key.store.path", new File( keyStorePath() ).getAbsolutePath() );
			put( "1.asymmetric.key.store.password", keyStorePassword() );
			put( "1.asymmetric.key.store.alias", keyAlias() );
		} };

		// Use chained and decrypt deserializers to read original message.
		final Map<String, Object> consumerProps = new HashMap<String, Object>() { {
			put( ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, "io.macronova.kafka.common.serialization.ChainedDeserializer" );
			put( "0.deserializer", "io.macronova.kafka.common.serialization.HybridDecryptDeserializer" );
			put( "0.symmetric.transformation", "AES/CBC/PKCS5Padding" );
			put( "0.asymmetric.transformation", "RSA/None/PKCS1Padding" );
			put( "0.asymmetric.key.store.path", new File( keyStorePath() ).getAbsolutePath() );
			put( "0.asymmetric.key.store.password", keyStorePassword() );
			put( "0.asymmetric.key.store.alias", keyAlias() );
			put( "0.asymmetric.key.store.alias.password", keyAliasPassword() );
			put( "1.deserializer", StringDeserializer.class.getName() );
		} };

		checkSerializerDeserializer( producerProps, consumerProps );
	}

	@Test
	public void testSignature() throws Exception {
		// Send record with signed content.
		final Map<String, Object> producerProps = new HashMap<String, Object>() { {
			put( ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, "io.macronova.kafka.common.serialization.ChainedSerializer" );
			put( "0.serializer", StringSerializer.class.getName() );
			put( "1.serializer", "io.macronova.kafka.common.serialization.GenerateSignatureSerializer" );
			put( "1.algorithm", "SHA256withRSA" );
			put( "1.key.store.path", new File( keyStorePath() ).getAbsolutePath() );
			put( "1.key.store.password", keyStorePassword() );
			put( "1.key.store.alias", keyAlias() );
			put( "1.key.store.alias.password", keyAliasPassword() );
		} };

		// Use chained and verify signature deserializers to read original message.
		final Map<String, Object> consumerProps = new HashMap<String, Object>() { {
			put( ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, "io.macronova.kafka.common.serialization.ChainedDeserializer" );
			put( "0.deserializer", "io.macronova.kafka.common.serialization.VerifySignatureDeserializer" );
			put( "0.algorithm", "SHA256withRSA" );
			put( "0.key.store.path", new File( keyStorePath() ).getAbsolutePath() );
			put( "0.key.store.password", keyStorePassword() );
			put( "0.key.store.alias", keyAlias() );
			put( "1.deserializer", StringDeserializer.class.getName() );
		} };

		checkSerializerDeserializer( producerProps, consumerProps );
	}

	@Test
	public void testSignatureWithEncryption() throws Exception {
		final Map<String, Object> producerProps = new HashMap<String, Object>() { {
			put( ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, "io.macronova.kafka.common.serialization.ChainedSerializer" );
			put( "0.serializer", StringSerializer.class.getName() );
			put( "2.serializer", "io.macronova.kafka.common.serialization.GenerateSignatureSerializer" );
			put( "2.algorithm", "SHA256withRSA" );
			put( "2.key.store.path", new File( keyStorePath() ).getAbsolutePath() );
			put( "2.key.store.password", keyStorePassword() );
			put( "2.key.store.alias", keyAlias() );
			put( "2.key.store.alias.password", keyAliasPassword() );
			put( "1.serializer", "io.macronova.kafka.common.serialization.EncryptSerializer" );
			put( "1.transformation", "AES/CBC/PKCS5Padding" );
			put( "1.secret", "770A8A65DA156D24EE2A093277530142" );
		} };

		final Map<String, Object> consumerProps = new HashMap<String, Object>() { {
			put( ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, "io.macronova.kafka.common.serialization.ChainedDeserializer" );
			put( "1.deserializer", "io.macronova.kafka.common.serialization.DecryptDeserializer" );
			put( "1.transformation", "AES/CBC/PKCS5Padding" );
			put( "1.secret", "770A8A65DA156D24EE2A093277530142" );
			put( "0.deserializer", "io.macronova.kafka.common.serialization.VerifySignatureDeserializer" );
			put( "0.algorithm", "SHA256withRSA" );
			put( "0.key.store.path", new File( keyStorePath() ).getAbsolutePath() );
			put( "0.key.store.password", keyStorePassword() );
			put( "0.key.store.alias", keyAlias() );
			put( "2.deserializer", StringDeserializer.class.getName() );
		} };

		checkSerializerDeserializer( producerProps, consumerProps );
	}

	@Test
	public void testDecryptionFailure() throws Exception {
		final String transformation = "AES/CBC/PKCS5Padding";
		final String secret = "770A8A65DA156D24EE2A093277530142";

		final Map<String, Object> producerConfig = new HashMap<String, Object>() { {
			put( ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName() );
		} };

		final Map<String, Object> consumerConfig = new HashMap<String, Object>() { {
			put( ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, "io.macronova.kafka.common.serialization.ChainedDeserializer" );
			put( "0.deserializer", "io.macronova.kafka.common.serialization.DecryptDeserializer" );
			put( "0.transformation", transformation );
			put( "0.secret", secret );
			put( "1.deserializer", StringDeserializer.class.getName() );
		} };

		produceRecords( producerConfig, "Hello, World!" );

		final Map<String, Object> consumerProps = KafkaTestUtils.consumerProps( "test-group", "true", embeddedKafka );
		consumerProps.put( ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest" );
		consumerProps.putAll( consumerConfig );
		final ConsumerFactory<Integer, String> cf = new DefaultKafkaConsumerFactory<>( consumerProps );
		Consumer<Integer, String> consumer = cf.createConsumer();
		embeddedKafka.consumeFromAnEmbeddedTopic( consumer, KafkaEmbeddedHolder.topicName() );
		try {
			KafkaTestUtils.getRecords( consumer, 100 );
			Assert.fail( "Expected issue while decrypting data." );
		}
		catch (KafkaException e) {
			// Expected.
			Assert.assertTrue( e.getCause().getMessage().startsWith( "Failed to decrypt content: Unexpected end of encrypted content." ) );
		}
		catch (Exception e) {
			Assert.fail( "Expected issue while decrypting data." );
		}
		finally {
			consumer.close();
		}
	}

	private void checkSerializerDeserializer(Map<String, Object> producerConfig, final Map<String, Object> consumerConfig) throws Exception {
		final String message = "Hello, World!";

		produceRecords( producerConfig, message );
		final StringBuilder consumedMessage = new StringBuilder();

		TestUtils.waitForCondition(
			new TestCondition() {
				public boolean conditionMet() {
					try {
						ConsumerRecords<Integer, String> records = consumeRecords( consumerConfig );
						if ( records.count() == 1 ) {
							consumedMessage.append( records.iterator().next().value() );
							return true;
						}
						return false;
					}
					catch ( Exception e ) {
						throw new RuntimeException( e );
					}
				}
			}, 5000L, "Did not consume expected records."
		);
		Assert.assertEquals( message, consumedMessage.toString() );
	}

	@SafeVarargs
	private final <K, V> void produceRecords(Map<String, Object> properties, V... data) throws Exception {
		Producer<K, V> producer = null;
		try {
			final Map<String, Object> producerProps = KafkaTestUtils.producerProps( embeddedKafka );
			producerProps.putAll( properties );
			final ProducerFactory<K, V> pf = new DefaultKafkaProducerFactory<>( producerProps );
			producer = pf.createProducer();
			for ( V msg : data ) {
				producer.send( new ProducerRecord<K, V>( KafkaEmbeddedHolder.topicName(), msg ) ).get();
			}
		}
		finally {
			if ( producer != null ) {
				producer.close();
			}
		}
	}

	private <K, V> ConsumerRecords<K, V> consumeRecords(Map<String, Object> properties) throws Exception {
		Consumer<K, V> consumer = null;
		try {
			final Map<String, Object> consumerProps = KafkaTestUtils.consumerProps(
					"test-group", "true", embeddedKafka
			);
			consumerProps.put( ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest" );
			consumerProps.putAll( properties );
			final ConsumerFactory<K, V> cf = new DefaultKafkaConsumerFactory<>( consumerProps );
			consumer = cf.createConsumer();
			embeddedKafka.consumeFromAnEmbeddedTopic( consumer, KafkaEmbeddedHolder.topicName() );
			return KafkaTestUtils.getRecords( consumer, 500 );
		}
		finally {
			if ( consumer != null ) {
				consumer.close();
			}
		}
	}
}
