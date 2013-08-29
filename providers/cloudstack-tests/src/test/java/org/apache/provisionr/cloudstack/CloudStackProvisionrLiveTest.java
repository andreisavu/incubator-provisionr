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

package org.apache.provisionr.cloudstack;

import java.security.Security;
import java.util.UUID;
import org.apache.provisionr.api.Provisionr;
import org.apache.provisionr.api.access.AdminAccess;
import org.apache.provisionr.api.hardware.Hardware;
import org.apache.provisionr.api.network.Network;
import org.apache.provisionr.api.network.Rule;
import org.apache.provisionr.api.pool.PoolSpec;
import org.apache.provisionr.api.provider.Provider;
import org.apache.provisionr.api.software.Software;
import static org.apache.provisionr.test.KarafTests.installProvisionrFeatures;
import static org.apache.provisionr.test.KarafTests.installProvisionrTestSupportBundle;
import static org.apache.provisionr.test.KarafTests.passThroughAllSystemPropertiesWithPrefix;
import static org.apache.provisionr.test.KarafTests.useDefaultKarafAsInProjectWithJunitBundles;
import org.apache.provisionr.test.ProvisionrLiveTestSupport;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.ops4j.pax.exam.CoreOptions.maven;
import static org.ops4j.pax.exam.CoreOptions.scanFeatures;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.Configuration;
import org.ops4j.pax.exam.junit.ExamReactorStrategy;
import org.ops4j.pax.exam.junit.JUnit4TestRunner;
import org.ops4j.pax.exam.spi.reactors.AllConfinedStagedReactorFactory;

@RunWith(JUnit4TestRunner.class)
@ExamReactorStrategy(AllConfinedStagedReactorFactory.class)
public class CloudStackProvisionrLiveTest extends ProvisionrLiveTestSupport {

    public CloudStackProvisionrLiveTest() {
        super(CloudStackProvisionr.ID);
    }

    @Configuration
    public Option[] configuration() throws Exception {
        return new Option[]{
            useDefaultKarafAsInProjectWithJunitBundles(),
            passThroughAllSystemPropertiesWithPrefix("test.cloudstack."),
            scanFeatures(maven().groupId("org.jclouds.karaf").artifactId("jclouds-karaf")
                .type("xml").classifier("features").versionAsInProject(), "jclouds-api-cloudstack"),
            installProvisionrFeatures("provisionr-cloudstack"),
            installProvisionrTestSupportBundle()
        };
    }

    @Test
    public void startProvisioningProcess() throws Exception {
        waitForProcessDeployment(CloudStackProvisionr.ID);
        Provisionr provisionr = getOsgiService(Provisionr.class, 5000);

//        listAvailableJceProviders();

        final Provider provider = collectProviderCredentialsFromSystemProperties()
            // TODO: get more options as needed for CloudStack
            .createProvider();

        final Network network = Network.builder()
            .addRules(Rule.builder().anySource().tcp().port(22).createRule())
            .createNetwork();

        final Software software = Software.builder().imageId("ubuntu-10.04")
            .packages("nginx").createSoftware();

        final AdminAccess adminAccess = AdminAccess.builder().asCurrentUser().createAdminAccess();

        final Hardware hardware = Hardware.builder().type("offering").createHardware();

        final PoolSpec poolSpec = PoolSpec.builder().network(network).provider(provider).adminAccess(adminAccess)
            .software(software).hardware(hardware).minSize(1).expectedSize(1).createPool();

        String poolKey = UUID.randomUUID().toString();
        provisionr.startPoolManagementProcess(poolKey, poolSpec);

        waitForProcessEndByBusinessKey(poolKey);
        // TODO: check that the environment is clean
    }

    /**
     * debug utility method
     */
    private void listAvailableJceProviders() {
        for (java.security.Provider provider : Security.getProviders()) {
            System.out.println(provider.toString());
            for (java.security.Provider.Service service : provider.getServices()) {
                System.out.println("\t" + service.toString());
            }
        }
    }
}
