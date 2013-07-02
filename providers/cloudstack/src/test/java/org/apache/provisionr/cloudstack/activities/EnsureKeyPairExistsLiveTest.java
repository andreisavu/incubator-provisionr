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

package org.apache.provisionr.cloudstack.activities;

import java.io.IOException;
import org.activiti.engine.delegate.DelegateExecution;
import org.apache.provisionr.api.access.AdminAccess;
import org.apache.provisionr.api.pool.PoolSpec;
import org.apache.provisionr.cloudstack.core.KeyPairs;
import org.apache.provisionr.core.CoreProcessVariables;
import static org.fest.assertions.api.Assertions.assertThat;
import org.jclouds.cloudstack.domain.SshKeyPair;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class EnsureKeyPairExistsLiveTest extends CloudStackActivityLiveTest<EnsureKeyPairExists> {

    public static final String TEST_KEY_FINGERPRINT = "0e:2f:16:f4:15:a3:80:e8:c5:1a:8b:85:e4:fe:69:fa";
    private final String KEYPAIR_NAME = KeyPairs.formatNameFromBusinessKey(BUSINESS_KEY);

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
        logKeyPairs();
    }

    @Override
    @After
    public void tearDown() throws Exception {
        context.getApi().getSSHKeyPairClient().deleteSSHKeyPair(KEYPAIR_NAME);
        logKeyPairs();
        super.tearDown();
    }

    @Test
    public void testEnsureKeyPairExists() throws Exception {
        final AdminAccess adminAccess = AdminAccess.builder()
            .username("admin")
            .publicKey(getResourceAsString("/org/apache/provisionr/test/id_rsa_test.pub"))
            .privateKey(getResourceAsString("/org/apache/provisionr/test/id_rsa_test"))
            .createAdminAccess();

        DelegateExecution execution = mock(DelegateExecution.class);
        PoolSpec poolSpec = mock(PoolSpec.class);

        when(poolSpec.getProvider()).thenReturn(provider);
        when(poolSpec.getAdminAccess()).thenReturn(adminAccess);

        when(execution.getProcessBusinessKey()).thenReturn(BUSINESS_KEY);
        when(execution.getVariable(CoreProcessVariables.POOL)).thenReturn(poolSpec);

        activity.execute(execution);
        assertKeyPairWasImportedAsExpected();

        /* the second call should just re-import the key */
        activity.execute(execution);
        assertKeyPairWasImportedAsExpected();
    }

    private void assertKeyPairWasImportedAsExpected() throws IOException {
        SshKeyPair pair = context.getApi().getSSHKeyPairClient().getSSHKeyPair(KEYPAIR_NAME);
        assertThat(pair.getFingerprint()).isEqualTo(TEST_KEY_FINGERPRINT);
    }
}
