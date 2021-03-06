/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.provisionr.test;

import com.google.common.base.Charsets;
import com.google.common.base.Throwables;
import com.google.common.io.Resources;
import java.io.IOException;

public final class TestConstants {

    private TestConstants() {
    }

    static final String PUBLIC_KEY_FILE_NAME = "id_rsa_test.pub";
    static final String PRIVATE_KEY_FILE_NAME = "id_rsa_test";

    public static final String PUBLIC_KEY = loadResource(PUBLIC_KEY_FILE_NAME);
    public static final String PRIVATE_KEY = loadResource(PRIVATE_KEY_FILE_NAME);

    private static String loadResource(String fileName) {
        try {
            return Resources.toString(Resources.getResource(TestConstants.class, fileName), Charsets.UTF_8);
        } catch (IOException e) {
            throw Throwables.propagate(e);
        }
    }
}
