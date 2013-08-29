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

package org.apache.provisionr.amazon.activities;

import com.amazonaws.services.ec2.model.DeleteKeyPairRequest;
import com.amazonaws.services.ec2.model.DescribeKeyPairsRequest;
import com.amazonaws.services.ec2.model.DescribeKeyPairsResult;
import org.activiti.engine.delegate.DelegateExecution;
import org.apache.provisionr.amazon.core.KeyPairs;
import org.apache.provisionr.api.access.AdminAccess;
import org.apache.provisionr.api.pool.PoolSpec;
import org.apache.provisionr.core.CoreProcessVariables;
import static org.fest.assertions.api.Assertions.assertThat;
import org.junit.Test;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class EnsureKeyPairExistsLiveTest extends AmazonActivityLiveTest<EnsureKeyPairExists> {

    /**
     * Computed in an Amazon specific way
     */
    public static final String TEST_KEY_FINGERPRINT = "6f:05:94:d8:97:a0:ce:4a:31:d1:3c:1b:f6:ed:31:6b";

    private final String KEYPAIR_NAME = KeyPairs.formatNameFromBusinessKey(BUSINESS_KEY);

    @Override
    public void tearDown() throws Exception {
        client.deleteKeyPair(new DeleteKeyPairRequest().withKeyName(KEYPAIR_NAME));
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

    private void assertKeyPairWasImportedAsExpected() {
        final DescribeKeyPairsRequest request = new DescribeKeyPairsRequest().withKeyNames(KEYPAIR_NAME);
        DescribeKeyPairsResult result = client.describeKeyPairs(request);

        assertThat(result.getKeyPairs()).hasSize(1);
        assertThat(result.getKeyPairs().get(0).getKeyFingerprint()).isEqualTo(TEST_KEY_FINGERPRINT);
    }
}
