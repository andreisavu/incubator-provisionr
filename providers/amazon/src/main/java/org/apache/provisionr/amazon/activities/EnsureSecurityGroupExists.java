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
import com.amazonaws.services.ec2.model.AuthorizeSecurityGroupIngressRequest;
import com.amazonaws.services.ec2.model.CreateSecurityGroupRequest;
import com.amazonaws.services.ec2.model.CreateSecurityGroupResult;
import com.amazonaws.services.ec2.model.DescribeSecurityGroupsRequest;
import com.amazonaws.services.ec2.model.DescribeSecurityGroupsResult;
import com.amazonaws.services.ec2.model.IpPermission;
import com.amazonaws.services.ec2.model.RevokeSecurityGroupIngressRequest;
import org.apache.provisionr.amazon.core.ErrorCodes;
import org.apache.provisionr.amazon.core.ProviderClientCache;
import org.apache.provisionr.amazon.core.SecurityGroups;
import org.apache.provisionr.amazon.functions.ConvertRuleToIpPermission;
import org.apache.provisionr.api.network.Network;
import org.apache.provisionr.api.pool.PoolSpec;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import static com.google.common.collect.Iterables.getOnlyElement;
import static com.google.common.collect.Sets.difference;
import java.util.Set;
import org.activiti.engine.delegate.DelegateExecution;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EnsureSecurityGroupExists extends AmazonActivity {

    public static final Logger LOG = LoggerFactory.getLogger(EnsureSecurityGroupExists.class);

    public EnsureSecurityGroupExists(ProviderClientCache cache) {
        super(cache);
    }

    @Override
    public void execute(AmazonEC2 client, PoolSpec poolSpec, DelegateExecution execution) {
        final String businessKey = execution.getProcessBusinessKey();
        final String groupName = SecurityGroups.formatNameFromBusinessKey(businessKey);

        try {
            LOG.info(">> Creating Security Group with name {}", groupName);
            CreateSecurityGroupRequest request = new CreateSecurityGroupRequest()
                .withGroupName(groupName).withDescription("Security Group for " + businessKey);

            CreateSecurityGroupResult result = client.createSecurityGroup(request);
            LOG.info("<< Created Security Group with ID {}", result.getGroupId());

        } catch (AmazonServiceException e) {
            if (e.getErrorCode().equals(ErrorCodes.DUPLICATE_SECURITY_GROUP)) {
                LOG.warn(String.format("<< Security Group %s already exists. " +
                    "Synchronizing ingress rules.", groupName));
            } else {
                throw Throwables.propagate(e);
            }
        }

        synchronizeIngressRules(client, groupName, poolSpec.getNetwork());
    }

    private void synchronizeIngressRules(AmazonEC2 client, String groupName, Network network) {
        DescribeSecurityGroupsResult result = client.describeSecurityGroups(
            new DescribeSecurityGroupsRequest().withGroupNames(groupName));

        Set<IpPermission> existingPermissions = ImmutableSet.copyOf(getOnlyElement(
            result.getSecurityGroups()).getIpPermissions());

        Set<IpPermission> expectedPermissions = ImmutableSet.copyOf(
            Iterables.transform(network.getIngress(), ConvertRuleToIpPermission.FUNCTION));

        authorizeIngressRules(client, groupName, difference(expectedPermissions, existingPermissions));
        revokeIngressRules(client, groupName, difference(existingPermissions, expectedPermissions));
    }

    private void authorizeIngressRules(AmazonEC2 client, String groupName, Set<IpPermission> ipPermissions) {
        if (!ipPermissions.isEmpty()) {
            LOG.info(">> Authorizing Security Group Ingress Rules {} for {}", ipPermissions, groupName);

            AuthorizeSecurityGroupIngressRequest request = new AuthorizeSecurityGroupIngressRequest()
                .withGroupName(groupName).withIpPermissions(ipPermissions);
            client.authorizeSecurityGroupIngress(request);
        }
    }

    private void revokeIngressRules(AmazonEC2 client, String groupName, Set<IpPermission> ipPermissions) {
        if (!ipPermissions.isEmpty()) {
            LOG.info(">> Revoking Security Group Ingress Rules: {} for {}", ipPermissions, groupName);

            RevokeSecurityGroupIngressRequest request = new RevokeSecurityGroupIngressRequest()
                .withGroupName(groupName).withIpPermissions(ipPermissions);
            client.revokeSecurityGroupIngress(request);
        }
    }
}
