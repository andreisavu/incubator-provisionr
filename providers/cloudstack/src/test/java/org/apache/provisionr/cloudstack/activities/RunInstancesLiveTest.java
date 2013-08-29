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

import com.google.common.collect.ImmutableMap;
import java.util.Map;
import org.activiti.engine.delegate.DelegateExecution;
import org.apache.provisionr.api.access.AdminAccess;
import org.apache.provisionr.api.hardware.Hardware;
import org.apache.provisionr.api.network.Network;
import org.apache.provisionr.api.network.Rule;
import org.apache.provisionr.api.pool.PoolSpec;
import org.apache.provisionr.api.software.Software;
import org.apache.provisionr.cloudstack.ProviderOptions;
import org.apache.provisionr.cloudstack.core.VirtualMachines;
import org.apache.provisionr.core.CoreProcessVariables;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RunInstancesLiveTest extends CloudStackActivityLiveTest<RunInstances> {

    private static final Logger LOG = LoggerFactory.getLogger(RunInstancesLiveTest.class);

    private DelegateExecution execution;
    private PoolSpec poolSpec;

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
        logSecurityGroupDetails();
        logKeyPairs();
        logVirtualMachines();
        execution = mock(DelegateExecution.class);
        poolSpec = mock(PoolSpec.class);

        final AdminAccess adminAccess = AdminAccess.builder()
            .username("admin")
            .publicKey(getResourceAsString("/org/apache/provisionr/test/id_rsa_test.pub"))
            .privateKey(getResourceAsString("/org/apache/provisionr/test/id_rsa_test"))
            .createAdminAccess();

        final Network network = Network.builder().addRules(
            Rule.builder().anySource().tcp().port(22).createRule()).createNetwork();

        final Hardware hardware = Hardware.builder().type(getProviderProperty("serviceOffering")).createHardware();
        final Software software = Software.builder()
            .imageId(getProviderProperty("templateId"))
            .createSoftware();

        Map<String, String> options = ImmutableMap.of(ProviderOptions.ZONE_ID,
            getProviderProperty("zoneId"));

        when(poolSpec.getProvider()).thenReturn(provider);
        when(poolSpec.getAdminAccess()).thenReturn(adminAccess);
        when(poolSpec.getNetwork()).thenReturn(network);
        when(poolSpec.getHardware()).thenReturn(hardware);
        when(poolSpec.getSoftware()).thenReturn(software);
        when(poolSpec.getOptions()).thenReturn(options);

        when(execution.getProcessBusinessKey()).thenReturn(BUSINESS_KEY);
        when(execution.getVariable(CoreProcessVariables.POOL)).thenReturn(poolSpec);

        new EnsureSecurityGroupExists().execute(execution);
        new EnsureKeyPairExists().execute(execution);
    }

    @Override
    @After
    public void tearDown() throws Exception {
        new DeleteKeyPair().execute(execution);
        new DeleteSecurityGroup().execute(execution);

        logSecurityGroupDetails();
        logKeyPairs();
        logVirtualMachines();
        VirtualMachines.destroyAllVirtualMachineByName(context.getApi(), BUSINESS_KEY);
        logVirtualMachines();
        super.tearDown();
    }

    @Test
    @Ignore
    public void test() throws Exception {
        activity.execute(execution);
    }
}
