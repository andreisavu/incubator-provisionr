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

import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.model.DescribeInstancesRequest;
import com.amazonaws.services.ec2.model.DescribeInstancesResult;
import com.amazonaws.services.ec2.model.Instance;
import com.google.common.base.Optional;
import static com.google.common.base.Preconditions.checkNotNull;
import com.google.common.base.Predicate;
import com.google.common.base.Throwables;
import com.google.common.collect.Iterables;
import java.util.List;
import org.activiti.engine.delegate.DelegateExecution;
import org.apache.provisionr.amazon.ProcessVariables;
import org.apache.provisionr.amazon.core.ProviderClientCache;
import org.apache.provisionr.api.pool.PoolSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AllInstancesMatchPredicate extends AmazonActivity {

    private static final Logger LOG = LoggerFactory.getLogger(AllInstancesMatchPredicate.class);

    private final String resultVariable;
    private final Predicate<Instance> predicate;

    protected AllInstancesMatchPredicate(ProviderClientCache cache,
                                         String resultVariable, Predicate<Instance> predicate) {
        super(cache);
        this.resultVariable = checkNotNull(resultVariable, "resultVariable is null");
        this.predicate = checkNotNull(predicate, "predicate is null");
    }

    @Override
    public void execute(AmazonEC2 client, PoolSpec poolSpec, DelegateExecution execution) {
        @SuppressWarnings("unchecked")
        Optional<List<String>> instanceIds =
            Optional.fromNullable((List<String>) execution.getVariable(ProcessVariables.INSTANCE_IDS));

        if (!instanceIds.isPresent()) {
            LOG.warn("<< Process variable '{}' not found", ProcessVariables.INSTANCE_IDS);
            return;
        } else if (instanceIds.get().size() == 0) {
            LOG.info(">> No instances are currently registered in the process.");
            return;
        }

        try {
            DescribeInstancesResult result = client.describeInstances(new DescribeInstancesRequest()
                .withInstanceIds(instanceIds.get()));

            List<Instance> instances = collectInstancesFromReservations(result.getReservations());

            if (Iterables.all(instances, predicate)) {
                LOG.info(">> All {} instances match predicate {} ", instanceIds, predicate);
                execution.setVariable(resultVariable, true);

            } else {
                LOG.info("<< Not all instances {} match predicate {}", instanceIds, predicate);
                execution.setVariable(resultVariable, false);
            }
        } catch (AmazonServiceException exception) {
            if (exception.getErrorCode().equalsIgnoreCase("InvalidInstanceID.NotFound")) {
                LOG.warn("<< Got error InvalidInstanceID.NotFound. Assuming predicate {} is false", predicate);
                execution.setVariable(resultVariable, false);
            } else {
                throw Throwables.propagate(exception);
            }
        }
    }
}
