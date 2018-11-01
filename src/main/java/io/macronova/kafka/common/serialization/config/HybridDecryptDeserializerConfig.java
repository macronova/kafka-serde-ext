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
package io.macronova.kafka.common.serialization.config;

import java.util.Map;

import org.apache.kafka.common.config.ConfigDef;

public class HybridDecryptDeserializerConfig extends BaseHybridEncryptConfig {
	private static ConfigDef config = baseConfigDef();

	public static final String ASYMMETRIC_KEY_ALIAS_PASSWORD_CONFIG = "asymmetric.key.store.alias.password";
	public static final String ASYMMETRIC_KEY_ALIAS_PASSWORD_DOC = "Password to access private key present in key store.";

	public HybridDecryptDeserializerConfig(Map<?, ?> originals) {
		super( config, originals );
		validate();
	}

	protected static ConfigDef baseConfigDef() {
		final ConfigDef config = BaseHybridEncryptConfig.baseConfigDef();
		config.define( ASYMMETRIC_KEY_ALIAS_PASSWORD_CONFIG, ConfigDef.Type.PASSWORD, ConfigDef.Importance.HIGH, ASYMMETRIC_KEY_ALIAS_PASSWORD_DOC );
		return config;
	}

	public String getAsymmetricKeyAliasPassword() {
		return getPassword( ASYMMETRIC_KEY_ALIAS_PASSWORD_CONFIG ).value();
	}
}
