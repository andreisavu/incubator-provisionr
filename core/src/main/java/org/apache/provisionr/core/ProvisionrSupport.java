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

package org.apache.provisionr.core;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import java.util.List;
import java.util.NoSuchElementException;
import org.activiti.engine.ProcessEngine;
import org.activiti.engine.RuntimeService;
import org.activiti.engine.runtime.Execution;
import org.activiti.engine.runtime.ProcessInstance;
import org.apache.provisionr.api.Provisionr;
import org.apache.provisionr.api.pool.PoolInstance;
import org.apache.provisionr.api.provider.Provider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class ProvisionrSupport implements Provisionr {

    private static final Logger LOG = LoggerFactory.getLogger(ProvisionrSupport.class);

    @Override
    public Optional<Provider> getDefaultProvider() {
        return Optional.absent();
    }

    @Override
    public PoolInstance getPoolInstance(String poolKey) {
        throw new NoSuchElementException("Method not implemented");
    }

    @Override
    public List<PoolInstance> listPoolInstances() {
        return ImmutableList.of();
    }

    /**
     * Trigger a signal event on all executions for a specific business key.
     *
     * @see <a href="http://www.activiti.org/userguide/index.html#bpmnSignalEventDefinition" />
     * @see CoreSignals
     */
    protected void triggerSignalEvent(ProcessEngine processEngine, String businessKey, String signalName) {
        RuntimeService runtimeService = processEngine.getRuntimeService();

        ProcessInstance processInstance = runtimeService.createProcessInstanceQuery()
            .processInstanceBusinessKey(businessKey).singleResult();

        List<Execution> executions = runtimeService.createExecutionQuery()
            .processInstanceId(processInstance.getProcessInstanceId())
            .signalEventSubscriptionName(signalName).list();

        if (executions.isEmpty()) {
            throw new NoSuchElementException(String.format("No executions found waiting " +
                "for signal '%s' on process %s", signalName, businessKey));
        }
        for (Execution execution : executions) {
            LOG.info("Sending '{}' signal to execution {} for process {}",
                new Object[]{signalName, execution.getId(), businessKey});
            runtimeService.signalEventReceived(signalName, execution.getId());

        }
    }

    /**
     * Convert a timeout specified in seconds to a string representation that can be used
     * inside the Activiti definitions of timeouts.
     *
     * @see <a href="http://en.wikipedia.org/wiki/ISO_8601#Durations" />
     */
    protected String convertTimeoutToISO8601TimeDuration(int bootstrapTimeoutInSeconds) {
        StringBuilder result = new StringBuilder("PT");
        if (bootstrapTimeoutInSeconds % 60 == 0) {
            result.append(bootstrapTimeoutInSeconds / 60).append("M");
        } else {
            result.append(bootstrapTimeoutInSeconds).append("S");
        }
        return result.toString();
    }
}
