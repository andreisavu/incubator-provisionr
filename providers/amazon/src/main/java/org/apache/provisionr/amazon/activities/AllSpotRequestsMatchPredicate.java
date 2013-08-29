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

import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.model.DescribeSpotInstanceRequestsRequest;
import com.amazonaws.services.ec2.model.DescribeSpotInstanceRequestsResult;
import com.amazonaws.services.ec2.model.SpotInstanceRequest;
import static com.google.common.base.Preconditions.checkNotNull;
import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import java.util.List;
import org.activiti.engine.delegate.DelegateExecution;
import org.apache.provisionr.amazon.ProcessVariables;
import org.apache.provisionr.amazon.core.ProviderClientCache;
import org.apache.provisionr.api.pool.PoolSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AllSpotRequestsMatchPredicate extends AmazonActivity {

    private static final Logger LOG = LoggerFactory.getLogger(AllSpotRequestsMatchPredicate.class);

    private final String resultVariable;
    private final Predicate<SpotInstanceRequest> predicate;

    protected AllSpotRequestsMatchPredicate(ProviderClientCache cache, String resultVariable,
                                            Predicate<SpotInstanceRequest> predicate) {
        super(cache);
        this.resultVariable = checkNotNull(resultVariable, "resultVariable is null");
        this.predicate = checkNotNull(predicate, "predicate is null");
    }

    @Override
    public void execute(AmazonEC2 client, PoolSpec poolSpec, DelegateExecution execution) {

        LOG.info(">> Checking if all spot requests match predicate {}", predicate);

        @SuppressWarnings("unchecked")
        List<String> requestIds = (List<String>) execution.getVariable(ProcessVariables.SPOT_INSTANCE_REQUEST_IDS);
        checkNotNull(requestIds, "process variable '{}' not found", ProcessVariables.SPOT_INSTANCE_REQUEST_IDS);

        DescribeSpotInstanceRequestsRequest describeRequest = new DescribeSpotInstanceRequestsRequest();
        describeRequest.setSpotInstanceRequestIds(requestIds);

        // Retrieve all of the requests we want to monitor.
        DescribeSpotInstanceRequestsResult describeResult = client.describeSpotInstanceRequests(describeRequest);
        List<SpotInstanceRequest> requests = describeResult.getSpotInstanceRequests();

        if (Iterables.all(requests, predicate)) {
            LOG.info(">> All {} requests match predicate {} ", requests, predicate);
            execution.setVariable(resultVariable, true);
        } else {
            LOG.info("<< Not all requests {} match predicate {}", requests, predicate);
            execution.setVariable(resultVariable, false);
        }
    }
}
