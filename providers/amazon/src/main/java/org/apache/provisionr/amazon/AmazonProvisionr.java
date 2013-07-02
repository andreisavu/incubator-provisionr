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

import com.google.common.base.Optional;
import static com.google.common.base.Preconditions.checkNotNull;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import org.activiti.engine.ProcessEngine;
import org.activiti.engine.runtime.ProcessInstance;
import org.apache.provisionr.amazon.config.DefaultProviderConfig;
import org.apache.provisionr.amazon.options.ProviderOptions;
import org.apache.provisionr.api.pool.Machine;
import org.apache.provisionr.api.pool.PoolInstance;
import org.apache.provisionr.api.pool.PoolSpec;
import org.apache.provisionr.api.provider.Provider;
import org.apache.provisionr.core.CoreConstants;
import org.apache.provisionr.core.CoreProcessVariables;
import org.apache.provisionr.core.CoreSignals;
import org.apache.provisionr.core.PoolStatus;
import org.apache.provisionr.core.ProvisionrSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AmazonProvisionr extends ProvisionrSupport {

    public static final Logger LOG = LoggerFactory.getLogger(AmazonProvisionr.class);

    public static final String ID = "amazon";
    public static final String MANAGEMENT_PROCESS_KEY = "amazonPoolManagement";

    private final ProcessEngine processEngine;
    private final Optional<Provider> defaultProvider;

    public AmazonProvisionr(ProcessEngine processEngine, DefaultProviderConfig defaultProviderConfig) {
        this.processEngine = checkNotNull(processEngine, "processEngine is null");
        this.defaultProvider = defaultProviderConfig.createProvider();

        if (defaultProvider.isPresent()) {
            LOG.info("Default provider for AmazonProvisionr is {}", defaultProvider.get());
        } else {
            LOG.info("No default provider configured for AmazonProvisionr");
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

        arguments.put(CoreProcessVariables.IS_CACHED_IMAGE, poolSpec.getSoftware().isCachedImage());

        /* needed because the Activiti EL doesn't work as expected and properties can't be read from the poolSpec. */
        arguments.put(ProcessVariables.SPOT_BID, poolSpec.getProvider().getOption(ProviderOptions.SPOT_BID));

        /* Authenticate as kermit to make the process visible in the Explorer UI */
        processEngine.getIdentityService().setAuthenticatedUserId(CoreConstants.ACTIVITI_EXPLORER_DEFAULT_USER);

        ProcessInstance instance = processEngine.getRuntimeService()
            .startProcessInstanceByKey(MANAGEMENT_PROCESS_KEY, poolKey, arguments);

        instance.getProcessInstanceId();
    }

    @Override
    public PoolInstance getPoolInstance(String poolKey) {
        ProcessInstance instance = processEngine.getRuntimeService().createProcessInstanceQuery()
            .processInstanceBusinessKey(poolKey).singleResult();
        if (instance == null) {
            throw new NoSuchElementException("No active pool found with key: " + poolKey);
        }

        @SuppressWarnings("unchecked") List<Machine> machines = (List<Machine>) processEngine.getRuntimeService()
            .getVariable(instance.getId(), CoreProcessVariables.MACHINES);

        String status = (String) processEngine.getRuntimeService().getVariable(instance.getId(),
            CoreProcessVariables.STATUS);

        return PoolInstance.builder()
            .key(poolKey)
            .status(Optional.fromNullable(status).or(PoolStatus.UNDEFINED))
            .machines(Optional.fromNullable(machines).or(ImmutableList.<Machine>of()))
            .createPoolInstance();
    }

    @Override
    public List<PoolInstance> listPoolInstances() {
        return ImmutableList.of();
    }

    @Override
    public void triggerPoolManagementProcessTermination(String poolKey) {
        triggerSignalEvent(processEngine, poolKey, CoreSignals.TERMINATE_POOL);
    }
}
