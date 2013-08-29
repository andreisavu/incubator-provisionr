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

package org.apache.provisionr.amazon;

import com.google.common.base.Stopwatch;
import java.io.IOException;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.connection.channel.direct.Session;
import org.apache.provisionr.amazon.options.ProviderOptions;
import org.apache.provisionr.api.Provisionr;
import org.apache.provisionr.api.access.AdminAccess;
import org.apache.provisionr.api.hardware.Hardware;
import org.apache.provisionr.api.network.Network;
import org.apache.provisionr.api.network.Protocol;
import org.apache.provisionr.api.network.Rule;
import org.apache.provisionr.api.pool.Machine;
import org.apache.provisionr.api.pool.PoolInstance;
import org.apache.provisionr.api.pool.PoolSpec;
import org.apache.provisionr.api.provider.Provider;
import org.apache.provisionr.api.software.Software;
import org.apache.provisionr.core.PoolStatus;
import org.apache.provisionr.core.Ssh;
import org.apache.provisionr.core.templates.PoolTemplate;
import static org.apache.provisionr.test.KarafTests.installProvisionrFeatures;
import static org.apache.provisionr.test.KarafTests.installProvisionrTestSupportBundle;
import static org.apache.provisionr.test.KarafTests.passThroughAllSystemPropertiesWithPrefix;
import static org.apache.provisionr.test.KarafTests.useDefaultKarafAsInProjectWithJunitBundles;
import org.apache.provisionr.test.ProvisionrLiveTestSupport;
import static org.junit.Assert.assertTrue;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.Configuration;
import org.ops4j.pax.exam.junit.ExamReactorStrategy;
import org.ops4j.pax.exam.junit.JUnit4TestRunner;
import org.ops4j.pax.exam.spi.reactors.AllConfinedStagedReactorFactory;
import org.osgi.util.tracker.ServiceTracker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RunWith(JUnit4TestRunner.class)
@ExamReactorStrategy(AllConfinedStagedReactorFactory.class)
public class AmazonProvisionrLiveTest extends ProvisionrLiveTestSupport {

    public static final Logger LOG = LoggerFactory.getLogger(AmazonProvisionrLiveTest.class);

    public static final int TEST_POOL_SIZE = 2;
    public static final String TEST_SPOT_BID_PRICE = "0.04";
    public static final String TEST_INSTANCE_TYPE = "t1.micro";
    public static final String TEST_POOL_TEMPLATE = "jenkins";

    public AmazonProvisionrLiveTest() {
        super(AmazonProvisionr.ID);
    }

    @Configuration
    public Option[] configuration() throws Exception {
        return new Option[]{
            useDefaultKarafAsInProjectWithJunitBundles(),
            passThroughAllSystemPropertiesWithPrefix("test.amazon."),
            installProvisionrFeatures("provisionr-amazon", "provisionr-examples"),
            installProvisionrTestSupportBundle()
        };
    }

    @Test
    public void startProvisioningProcessForOnDemandInstances() throws Exception {
        startProvisioningProcess(null);
    }

    @Test
    public void startProvisioningProcessForSpotInstances() throws Exception {
        startProvisioningProcess(TEST_SPOT_BID_PRICE);
    }

    private void startProvisioningProcess(String spotBid) throws Exception {
        waitForProcessDeployment(AmazonProvisionr.MANAGEMENT_PROCESS_KEY);

        final Provisionr provisionr = getOsgiService(Provisionr.class, 5000);

        Provider provider = collectProviderCredentialsFromSystemProperties()
            .option(ProviderOptions.REGION, getProviderProperty(
                ProviderOptions.REGION, ProviderOptions.DEFAULT_REGION))
            .createProvider();

        if (spotBid != null) {
            provider = provider.toBuilder()
                .option(ProviderOptions.SPOT_BID, spotBid)
                .createProvider();
        }

        final Network network = Network.builder().addRules(
            Rule.builder().anySource().icmp().createRule(),
            Rule.builder().anySource().port(22).protocol(Protocol.TCP).createRule()
        ).createNetwork();

        final Hardware hardware = Hardware.builder().type(TEST_INSTANCE_TYPE).createHardware();

        final AdminAccess adminAccess = AdminAccess.builder().asCurrentUser().createAdminAccess();

        final String destinationPath = "/home/" + adminAccess.getUsername() + "/provisionr.html";
        final Software software = Software.builder()
            .imageId("default")
            .file("http://provisionr.incubator.apache.org", destinationPath)
            .createSoftware();

        PoolTemplate template = getPoolTemplateWithId(TEST_POOL_TEMPLATE, 5000);
        final PoolSpec poolSpec = template.apply(PoolSpec.builder()
            .provider(provider)
            .network(network)
            .adminAccess(adminAccess)
            .software(software)
            .hardware(hardware)
            .minSize(TEST_POOL_SIZE)
            .expectedSize(TEST_POOL_SIZE)
            .createPool());

        final String poolKey = "j-" + UUID.randomUUID().toString();
        provisionr.startPoolManagementProcess(poolKey, poolSpec);

        try {
            waitForPoolStatus(provisionr, poolKey, PoolStatus.READY);

            PoolInstance instance = provisionr.getPoolInstance(poolKey);
            assertTrue(instance.getMachines().size() >= TEST_POOL_SIZE &&
                instance.getMachines().size() <= TEST_POOL_SIZE);

            for (Machine machine : instance.getMachines()) {
                assertSshCommand(machine, adminAccess, "test -f " + destinationPath);

                /* These are added through the Jenkins Debian template */
                assertSshCommand(machine, adminAccess, "hash git >/dev/null 2>&1");
                assertSshCommand(machine, adminAccess, "hash java >/dev/null 2>&1");
                assertSshCommand(machine, adminAccess, "test -f /etc/apt/sources.list.d/jenkins.list");
            }
        } finally {
            provisionr.triggerPoolManagementProcessTermination(poolKey);

            waitForPoolStatus(provisionr, poolKey, PoolStatus.TERMINATED);
            waitForProcessEndByBusinessKey(poolKey);
        }
    }

    private PoolTemplate getPoolTemplateWithId(String templateId, int timeoutInMilliseconds)
        throws TimeoutException, InterruptedException {
        ServiceTracker tracker = new ServiceTracker(bundleContext,
            PoolTemplate.class.getCanonicalName(), null);
        tracker.open(true);

        try {
            Stopwatch stopwatch = new Stopwatch().start();
            while (stopwatch.elapsedMillis() < timeoutInMilliseconds) {
                for (Object candidate : tracker.getServices()) {
                    if (PoolTemplate.class.cast(candidate).getId().equals(templateId)) {
                        return PoolTemplate.class.cast(candidate);
                    }
                }
                TimeUnit.MILLISECONDS.sleep(100);
            }

            throw new TimeoutException(String.format("Status check timed out after %d milliseconds",
                stopwatch.elapsedMillis()));

        } finally {
            tracker.close();
        }
    }

    private void assertSshCommand(Machine machine, AdminAccess adminAccess, String bashCommand) throws IOException {
        LOG.info("Checking return code for command '{}' on machine {}", bashCommand, machine.getExternalId());
        SSHClient client = Ssh.newClient(machine, adminAccess);
        try {
            Session session = client.startSession();
            try {
                session.allocateDefaultPTY();
                Session.Command command = session.exec(bashCommand);

                command.join();
                assertTrue("Exit code was " + command.getExitStatus() + " for command " + bashCommand,
                    command.getExitStatus() == 0);
            } finally {
                session.close();
            }
        } finally {
            client.close();
        }
    }

    private void waitForPoolStatus(Provisionr provisionr, String poolKey,
                                   String expectedStatus) throws InterruptedException, TimeoutException {
        String status;
        for (int i = 0; i < 120; i++) {
            try {
                status = provisionr.getPoolInstance(poolKey).getStatus();

            } catch (NoSuchElementException e) {
                LOG.info(String.format("Pool management process not found with key %s. " +
                    "Assuming process terminated as expected.", poolKey));
                return;  /* The process ended as expected */
            }

            if (status.equals(expectedStatus)) {
                LOG.info("Pool status is '{}'. Advancing.", status);
                return;
            } else {
                LOG.info("Pool status is '{}'. Waiting 10s for '{}'. Try {}/120",
                    new Object[]{status, expectedStatus, i});
                TimeUnit.SECONDS.sleep(10);
            }
        }
        throw new TimeoutException("Status check timed out after 20 minutes");
    }
}
