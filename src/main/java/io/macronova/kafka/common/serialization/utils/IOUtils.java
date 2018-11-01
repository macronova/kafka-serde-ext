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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public abstract class IOUtils {
	public static void copy(final InputStream input, final OutputStream output) throws IOException {
		byte[] buffer = new byte[1024];
		int length = 0;
		while ( (length = input.read( buffer )) != -1 ) {
			output.write( buffer, 0, length );
		}
	}

	public static void closeQuietly(InputStream stream) {
		if ( stream != null ) {
			try {
				stream.close();
			}
			catch (IOException e) {
				// Ignore.
			}
		}
	}

	public static void closeQuietly(OutputStream stream) {
		if ( stream != null ) {
			try {
				stream.close();
			}
			catch (IOException e) {
				// Ignore.
			}
		}
	}
}
