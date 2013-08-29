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

import com.google.common.base.Optional;
import static com.google.common.base.Preconditions.checkNotNull;
import com.google.common.collect.Maps;
import java.util.Map;
import org.activiti.engine.ProcessEngine;
import org.activiti.engine.RuntimeService;
import org.activiti.engine.runtime.ProcessInstance;
import org.apache.provisionr.api.pool.PoolSpec;
import org.apache.provisionr.api.provider.Provider;
import org.apache.provisionr.core.CoreConstants;
import org.apache.provisionr.core.CoreProcessVariables;
import org.apache.provisionr.core.ProvisionrSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CloudStackProvisionr extends ProvisionrSupport {

    private static final Logger LOG = LoggerFactory.getLogger(CloudStackProvisionr.class);
    public static final String ID = "cloudstack";
    /**
     * Process key must match the one in
     * providers/cloudstack/src/main/resources/OSGI-INF/activiti/cloudstack.bpmn20.xml
     */
    public static final String PROCESS_KEY = "cloudstack";

    private final ProcessEngine processEngine;
    private final Optional<Provider> defaultProvider;

    public CloudStackProvisionr(ProcessEngine processEngine, DefaultProviderConfig providerConfig) {
        this.processEngine = checkNotNull(processEngine, "processEngine is null");
        this.defaultProvider = providerConfig.createProvider();

        if (defaultProvider.isPresent()) {
            LOG.info("Default provider for CloudStackProvisionr is {}", defaultProvider.get());
        } else {
            LOG.info("No default provider configured for CloudStackProvisionr");
        }
    }

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public Optional<Provider> getDefaultProvider() {
        return defaultProvider;
    }

    @Override
    public void startPoolManagementProcess(String poolKey, PoolSpec poolSpec) {
        Map<String, Object> arguments = Maps.newHashMap();

        arguments.put(CoreProcessVariables.POOL, poolSpec);
        arguments.put(CoreProcessVariables.PROVIDER, getId());
        arguments.put(CoreProcessVariables.POOL_BUSINESS_KEY, poolKey);
        arguments.put(CoreProcessVariables.BOOTSTRAP_TIMEOUT,
            convertTimeoutToISO8601TimeDuration(poolSpec.getBootstrapTimeInSeconds()));

        /* Authenticate as kermit to make the process visible in the Explorer UI */
        processEngine.getIdentityService().setAuthenticatedUserId(CoreConstants.ACTIVITI_EXPLORER_DEFAULT_USER);

        RuntimeService runtimeService = processEngine.getRuntimeService();
        ProcessInstance instance = runtimeService.startProcessInstanceByKey(PROCESS_KEY, poolKey, arguments);
    }

    @Override
    public void triggerPoolManagementProcessTermination(String poolKey) {
        LOG.info("**** CloudStack (destroyPool) id: " + poolKey);
        // TODO use triggerSignalEvent as needed
    }
}
