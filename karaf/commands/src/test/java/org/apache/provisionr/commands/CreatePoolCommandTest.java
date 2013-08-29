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

package org.apache.provisionr.commands;

import com.google.common.base.Charsets;
import com.google.common.base.Optional;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.io.Resources;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import org.apache.felix.service.command.CommandSession;
import org.apache.provisionr.api.Provisionr;
import org.apache.provisionr.api.access.AdminAccess;
import org.apache.provisionr.api.pool.PoolSpec;
import org.apache.provisionr.api.provider.Provider;
import org.apache.provisionr.api.provider.ProviderBuilder;
import org.apache.provisionr.core.templates.PoolTemplate;
import org.apache.provisionr.core.templates.xml.XmlTemplate;
import static org.fest.assertions.api.Assertions.assertThat;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.ArgumentCaptor;
import static org.mockito.Matchers.anyMapOf;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class CreatePoolCommandTest {

    public static final String TEST_PROVISIONR_ID = "amazon";
    public static final String TEST_BUSINESS_KEY = "j-123";

    public static final String PATH_TO_PUBLIC_KEY = System.getProperty("user.home") + "/.ssh/id_rsa.pub";
    public static final String PATH_TO_PRIVATE_KEY = System.getProperty("user.home") + "/.ssh/id_rsa";

    @Rule
    public ExpectedException exception = ExpectedException.none();

    @Test
    public void testCreatePoolStartsTheManagementProcess() throws Exception {
        final Provisionr service = newProvisionrMockWithId(TEST_PROVISIONR_ID);
        final PoolSpec poolSpec = mock(PoolSpec.class);

        final List<Provisionr> services = ImmutableList.of(service);
        final List<PoolTemplate> templates = ImmutableList.of();
        CreatePoolCommand command = new CreatePoolCommand(services, templates,
            PATH_TO_PUBLIC_KEY, PATH_TO_PRIVATE_KEY) {
            @Override
            protected PoolSpec createPoolSpecFromArgumentsAndServiceDefaults(Provisionr service) {
                return poolSpec;
            }
        };
        command.setId(TEST_PROVISIONR_ID);
        command.setKey(TEST_BUSINESS_KEY);

        CommandSession session = mock(CommandSession.class);
        String output = (String) command.execute(session);

        verify(service).startPoolManagementProcess(TEST_BUSINESS_KEY, poolSpec);
        assertThat(output).isEqualTo("Pool management process started (key: j-123, provider: amazon)");
    }

    @Test(expected = NoSuchElementException.class)
    public void testProvisioningServiceNotFound() throws Exception {
        CreatePoolCommand command = new CreatePoolCommand(Collections.<Provisionr>emptyList(),
            Collections.<PoolTemplate>emptyList(), PATH_TO_PUBLIC_KEY, PATH_TO_PRIVATE_KEY);
        command.setId("dummy");

        CommandSession session = mock(CommandSession.class);
        command.execute(session);
    }

    @Test
    public void testCreatePoolWithTemplate() {
        final PoolTemplate template = XmlTemplate.newXmlTemplate(readDefaultTemplate("test-1"));

        CreatePoolCommand command = newPoolCommandWithMockedAdminAccess(template);

        command.setId("service");
        command.setKey("key");
        command.setTemplate(template.getId());

        Provisionr service = mock(Provisionr.class);
        Provider provider = newProviderMockWithBuilder();
        when(service.getDefaultProvider()).thenReturn(Optional.of(provider));

        PoolSpec poolSpec = command.createPoolSpecFromArgumentsAndServiceDefaults(service);

        assertThat(poolSpec.getSoftware().getRepositories()).hasSize(1);
        assertThat(poolSpec.getSoftware().getPackages()).contains("package-1a");
    }

    @Test
    public void testProviderSpecificOptions() {
        CreatePoolCommand command = newPoolCommandWithMockedAdminAccess();
        command.setId("service");
        command.setKey("key");
        command.setProviderOptions(Lists.newArrayList("spotBid=0.07"));

        Provisionr service = mock(Provisionr.class);
        Provider provider = newProviderMockWithBuilder();
        when(service.getDefaultProvider()).thenReturn(Optional.of(provider));

        command.createPoolSpecFromArgumentsAndServiceDefaults(service);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> argument = (ArgumentCaptor<Map<String, String>>) (Object)
            ArgumentCaptor.forClass(Map.class);
        verify(provider.toBuilder()).options(argument.capture());

        assertThat(argument.getValue().containsKey("spotBid")).isTrue();
        assertThat(argument.getValue().get("spotBid")).isEqualTo("0.07");
    }

    @Test
    public void testBlockDeviceOptions() {
        CreatePoolCommand command = newPoolCommandWithMockedAdminAccess();
        command.setId("service");
        command.setKey("key");

        Provisionr service = mock(Provisionr.class);
        Provider provider = newProviderMockWithBuilder();
        when(service.getDefaultProvider()).thenReturn(Optional.of(provider));

        PoolSpec poolSpec = command.createPoolSpecFromArgumentsAndServiceDefaults(service);
        assertThat(poolSpec.getHardware().getBlockDevices()).isEmpty();

        command.setBlockDeviceOptions(Lists.newArrayList("/dev/sda2:8", "/dev/sda9:2"));
        poolSpec = command.createPoolSpecFromArgumentsAndServiceDefaults(service);
        assertThat(poolSpec.getHardware().getBlockDevices()).hasSize(2);
        assertThat(poolSpec.getHardware().getBlockDevices().get(0).getSize()).isEqualTo(8);
        assertThat(poolSpec.getHardware().getBlockDevices().get(0).getName()).isEqualTo("/dev/sda2");
        assertThat(poolSpec.getHardware().getBlockDevices().get(1).getSize()).isEqualTo(2);
        assertThat(poolSpec.getHardware().getBlockDevices().get(1).getName()).isEqualTo("/dev/sda9");

        command.setBlockDeviceOptions(Lists.newArrayList("/dev/sda1:7"));
        poolSpec = command.createPoolSpecFromArgumentsAndServiceDefaults(service);
        assertThat(poolSpec.getHardware().getBlockDevices()).hasSize(1);
        assertThat(poolSpec.getHardware().getBlockDevices().get(0).getSize()).isEqualTo(7);

        command.setBlockDeviceOptions(Lists.newArrayList("this=breaks"));
        exception.expect(IllegalArgumentException.class);
        poolSpec = command.createPoolSpecFromArgumentsAndServiceDefaults(service);

        command.setBlockDeviceOptions(Lists.newArrayList("/dev/sda1"));
        exception.expect(IllegalArgumentException.class);
        poolSpec = command.createPoolSpecFromArgumentsAndServiceDefaults(service);

    }

    private Provisionr newProvisionrMockWithId(String id) {
        Provisionr service = mock(Provisionr.class);
        when(service.getId()).thenReturn(id);
        return service;
    }

    private Provider newProviderMockWithBuilder() {
        Provider provider = mock(Provider.class);
        ProviderBuilder providerBuilder = mock(ProviderBuilder.class);
        when(providerBuilder.options(anyMapOf(String.class, String.class))).thenReturn(providerBuilder);
        when(providerBuilder.createProvider()).thenReturn(provider);
        when(provider.toBuilder()).thenReturn(providerBuilder);
        return provider;
    }


    private String readDefaultTemplate(String name) {
        try {
            return Resources.toString(Resources.getResource(PoolTemplate.class,
                String.format("/org/apache/provisionr/commands/templates/%s.xml", name)), Charsets.UTF_8);

        } catch (IOException e) {
            throw Throwables.propagate(e);
        }
    }

    private CreatePoolCommand newPoolCommandWithMockedAdminAccess(PoolTemplate template) {
        List<PoolTemplate> templates = template != null ? ImmutableList.<PoolTemplate>of(template) :
            ImmutableList.<PoolTemplate>of();
        return new CreatePoolCommand(Collections.<Provisionr>emptyList(), templates,
            PATH_TO_PUBLIC_KEY, PATH_TO_PRIVATE_KEY) {
            @Override
            protected AdminAccess collectCurrentUserCredentialsForAdminAccess() {
                return mock(AdminAccess.class);
            }
        };
    }

    private CreatePoolCommand newPoolCommandWithMockedAdminAccess() {
        return this.newPoolCommandWithMockedAdminAccess(null);
    }
}
