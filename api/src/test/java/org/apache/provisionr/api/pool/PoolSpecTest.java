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

package org.apache.provisionr.api.pool;

import static org.apache.provisionr.api.AssertSerializable.assertSerializable;
import static org.fest.assertions.api.Assertions.assertThat;

import org.apache.provisionr.api.access.AdminAccess;
import org.apache.provisionr.api.hardware.Hardware;
import org.apache.provisionr.api.network.Network;
import org.apache.provisionr.api.network.Rule;
import org.apache.provisionr.api.provider.Provider;
import org.apache.provisionr.api.software.Software;

import org.junit.Test;

public class PoolSpecTest {

    @Test
    public void testSerialization() {
        final Provider provider = Provider.builder()
            .id("amazon").accessKey("access").secretKey("secret")
            .createProvider();

        final Network network = Network.builder().addRules(
            Rule.builder().anySource().port(22).tcp().createRule(),
            Rule.builder().anySource().port(8088).tcp().createRule()
        ).createNetwork();

        final AdminAccess adminAccess = AdminAccess.builder().username("admin").publicKey("ssh-rsa AAAAB3N")
            .privateKey("-----BEGIN RSA PRIVATE KEY-----\n").createAdminAccess();

        final Software software = Software.builder()
            .packages("hadoop-0.20", "hadoop-0.20-native").createSoftware();

        PoolSpec poolSpec = PoolSpec.builder()
            .provider(provider)
            .network(network)
            .adminAccess(adminAccess)
            .software(software)
            .hardware(Hardware.builder().type("large").createHardware())
            .minSize(20)
            .expectedSize(25)
            .bootstrapTimeInSeconds(60 * 15)
            .createPool();


        assertThat(poolSpec.getSoftware().getPackages()).contains("hadoop-0.20");
        assertThat(poolSpec.toBuilder().createPool()).isEqualTo(poolSpec);

        assertSerializable(poolSpec, PoolSpec.class);
    }
}
