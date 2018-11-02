# Kafka SerDe Extensions

[![Build Status](https://travis-ci.org/macronova/kafka-serde-ext.svg)](https://travis-ci.org/macronova/kafka-serde-ext) [![Code Coverage](https://codecov.io/gh/macronova/kafka-serde-ext/branch/master/graph/badge.svg)](https://codecov.io/gh/macronova/kafka-serde-ext) [![Maven Central](https://img.shields.io/maven-central/v/io.macronova.kafka/kafka-serde-ext/1.svg)](http://central.maven.org/maven2/io/macronova/kafka/kafka-serde-ext/1.0.0)

Porpose of the project is to create extensions to default set of [serializers](https://kafka.apache.org/11/javadoc/org/apache/kafka/common/serialization/Serializer.html)
and [deserializers](https://kafka.apache.org/11/javadoc/org/apache/kafka/common/serialization/Deserializer.html) provided by Apache Kafka.

## Features

- [X] Chained serializer execution.
- [X] Protect payload with symmetric or asymmetric encryption algorithm.
    - [X] Create random initialization vector when required.
    - [X] Serializer implementing _hybrid_ encryption.
- [X] Generate and verify digital signature to guarantee authentication and data integrity.

## Table of Contents

- [Installation](#installation)
- [Chained Serializer](#chained-serializer)
- [Encryption Serializer](#encryption-serializer)
- [Hybrid Encryption Serializer](#hybrid-encryption-serializer)
- [Digital Signature Serializer](#digital-signature-serializer)
- [Tutorial](#tutorial)

## Installation

1. Download latest release ZIP archive from GitHub and extract its content to temporary folder.
2. Copy _kafka-serde-ext-${version}.jar_ with all third-party dependencies to classpath of Kafka producers and consumers.
    1. Version 1.0.0 depends only on [Bouncy Castle](https://www.bouncycastle.org) security provider.
3. Configure Kafka producers and consumers according to below documentation.

## Chained Serializer

Chained serializer allows to execute ordered list of serializers passing output of one to another. Keeping in mind that Kafka `org.apache.kafka.common.serialization.Serializer`
interface transforms given Java type always to byte array, it make sense to add extra serializers performing modifications to byte array itself. Let us take an example of application
leveraging `org.apache.kafka.common.serialization.StringSerializer` to encode value of Kafka record. If we wanted to encrypt the payload, we can use chained serializer to transform
Java `String` to byte array, followed by encryption serializer. Plese note that application code does not require any further updates.

Example producer configuration:
```
value.serializer = io.macronova.kafka.common.serialization.ChainedSerializer
0.serializer = org.apache.kafka.common.serialization.StringSerializer
1.serializer = io.macronova.kafka.common.serialization.EncryptSerializer
1.transformation = AES/ECB/PKCS5Padding
1.secret = 770A8A65DA156D24EE2A093277530142
```

Serializers are ordered by position prefix (_0_ based) and allow to take extra configuration parameters. In the above example, _transformation_ and _secret_ properties
will be passed to `io.macronova.kafka.common.serialization.EncryptSerializer` during initialization.

Example consumer configuration:
```
value.deserializer = io.macronova.kafka.common.serialization.ChainedDeserializer
0.deserializer = io.macronova.kafka.common.serialization.DecryptDeserializer
0.transformation = AES/ECB/PKCS5Padding
0.secret = 770A8A65DA156D24EE2A093277530142
1.deserializer = org.apache.kafka.common.serialization.StringDeserializer
```

Sequence of serializers needs to be reversed in case of deserialization. First we decrypt payload and only once successful, construct Java object from decoded byte array.

Please review _src/examples_ folder for complete code sample.

## Encryption Serializer

Encryption serializer allows to encrypt and decrypt stream of bytes using symmetric or asymmetric cryptography. For complete list of supported algorithms,
consult Bouncy Castly [documentation](https://www.bouncycastle.org/specifications.html). In case given transformation requires initialization vector, serializer
will generate one composed from random bytes and prepend it to the output array.

### Configuration

Serializer class: `io.macronova.kafka.common.serialization.EncryptSerializer`<br/>
Deserializer class: `io.macronova.kafka.common.serialization.DecryptDeserializer`

Configuration parameters:

| Property Name            | Mandatory                          | Description                                        |
|--------------------------|------------------------------------|----------------------------------------------------|
| transformation           | Always                             | Cryptography transformation.                       |
| secret                   | shared secret encryption           | Hexadecimal representation of secret key.          |
| key.store.path           | key pair encryption                | Key store path.                                    |
| key.store.type           | No                                 | Key store type. Default: `JKS`.                    |
| key.store.password       | key pair encryption                | Key store password.                                |
| key.store.alias          | key pair encryption                | Key alias.                                         |
| key.store.alias.password | key pair encryption, deserializer  | Alias password.                                    |

Users are required to specify either `secret` (for shared passphrase encryption) or `key.store.path` property (for asymmetric cryptography algorithm).

### Data Representation

Below diagram presents output data format.
```
+------------------------------------+
| initialization vector  | encrypted |
| (optional, 8-16 bytes) |   data    |
+------------------------------------+
```

### Examples

Please review _src/examples_ directory for complete code sample.

Example configuration of symmetric encryption / decryption using shared secret key:
```
transformation = AES/ECB/PKCS5Padding
secret = 770A8A65DA156D24EE2A093277530142
```

Example of asymmetric encryption (private and public keys retrieved from key store):
```
transformation = RSA/None/PKCS1Padding
key.store.path = /tmp/keystore.jks
key.store.password = changeit
key.store.alias = key1
key.store.alias.password = changeit      # Required only by deserializer (Kafka consumer).
```

## Hybrid Encryption Serializer

Classic asymmetric encryption does not allow to encode content larger than key size. To overcome mentioned limitation, hybrid approach does the following:
1. Generate random secret key.
2. Encrypt hereby key with asymmetric algorithm (e.g. RSA).
3. Encrypt payload with symmetric algorithm (e.g. AES) using key generated in step 1.

Note that Kafka producer and consumer need only to know asymmetric key pair to successfully encrypt and decrypt data.

### Configuration

Serializer class: `io.macronova.kafka.common.serialization.HybridEncryptSerializer`<br/>
Deserializer class: `io.macronova.kafka.common.serialization.HybridDecryptDeserializer`

Configuration parameters (all required unless specified otherwise):

| Property Name                       | Description                                      |
|-------------------------------------|--------------------------------------------------|
| symmetric.transformation            | Symmetric transformation.                        |
| asymmetric.transformation           | Asymmetric transformation, requires padding.     |
| asymmetric.key.store.path           | Key store path.                                  |
| asymmetric.key.store.type           | Key store type. Not mandatory, default: `JKS`.   |
| asymmetric.key.store.password       | Key store password.                              |
| asymmetric.key.store.alias          | Key alias.                                       |
| asymmetric.key.store.alias.password | Alias password. Mandatory only for deserializer. |

### Data Representation

Below diagram presents output data format.
```
+--------------------------------------------------------------+
| RSA encoded secret | initialization vector  | AES encrypted  |
| key used for AES   | (optional, 8-16 bytes) |      data      |
+--------------------------------------------------------------+
```

> **Note**: Always use asymmetric algorithm with padding. For classic `RSA` this would mean `RSA/None/PKCS1Padding`.

### Examples

```
symmetric.transformation = AES/ECB/PKCS5Padding
asymmetric.transformation = RSA/None/PKCS1Padding
asymmetric.key.store.path = /tmp/keystore.jks
asymmetric.key.store.password = changeit
asymmetric.key.store.alias = key1
asymmetric.key.store.alias.password = changeit      # Required only by deserializer (Kafka consumer).
```

## Digital Signature Serializer

Digital signature serializer allows to generate signature and check its correctness during deserialization to guarantee authentication and integrity of data.
For complete list of supported algorithms, consult Bouncy Castly [documentation](https://www.bouncycastle.org/specifications.html).
Deserializer throws `org.apache.kafka.common.errors.SerializationException` if verification turns out invalid. Digital signature is prepended to output byte array.

### Configuration

Serializer class: `io.macronova.kafka.common.serialization.GenerateSignatureSerializer`<br/>
Deserializer class: `io.macronova.kafka.common.serialization.VerifySignatureDeserializer`

Configuration parameters (all required unless specified otherwise):

| Property Name            | Description                                      |
|--------------------------|--------------------------------------------------|
| algorithm                | Digital signature algorithm.                     |
| key.store.path           | Key store path.                                  |
| key.store.type           | Key store type. Not mandatory, default: `JKS`.   |
| key.store.password       | Key store password.                              |
| key.store.alias          | Key alias.                                       |
| key.store.alias.password | Alias password. Mandatory only for serializer.   |

### Data Representation

Below diagram presents output data format.
```
+------------------+
| signature | data |
+------------------+
```

### Examples

```
algorithm = SHA256withRSA
key.store.path = /tmp/keystore.jks
key.store.password = changeit
key.store.alias = key1
key.store.alias.password = changeit      # Required only by serializer (Kafka producer).
```

## Tutorial

Read our five minute [blog post](https://macronova.io/encrypt-kafka-records).
