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

import com.amazonaws.services.ec2.model.AuthorizeSecurityGroupIngressRequest;
import com.amazonaws.services.ec2.model.DescribeSecurityGroupsRequest;
import com.amazonaws.services.ec2.model.DescribeSecurityGroupsResult;
import com.amazonaws.services.ec2.model.IpPermission;
import com.amazonaws.services.ec2.model.RevokeSecurityGroupIngressRequest;
import com.amazonaws.services.ec2.model.SecurityGroup;
import com.google.common.collect.ImmutableSet;
import static com.google.common.collect.Iterables.getOnlyElement;
import static com.google.common.collect.Iterables.transform;
import static com.google.common.collect.Lists.newArrayList;
import com.google.common.collect.Sets;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.activiti.engine.delegate.DelegateExecution;
import org.apache.provisionr.amazon.core.SecurityGroups;
import org.apache.provisionr.amazon.functions.ConvertIpPermissionToRule;
import org.apache.provisionr.amazon.functions.ConvertRuleToIpPermission;
import org.apache.provisionr.api.network.Network;
import org.apache.provisionr.api.network.Rule;
import org.apache.provisionr.api.pool.PoolSpec;
import org.apache.provisionr.core.CoreProcessVariables;
import static org.fest.assertions.api.Assertions.assertThat;
import org.junit.After;
import org.junit.Test;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EnsureSecurityGroupExistsLiveTest extends AmazonActivityLiveTest<EnsureSecurityGroupExists> {

    private static final Logger LOG = LoggerFactory.getLogger(EnsureSecurityGroupExistsLiveTest.class);

    protected final String SECURITY_GROUP_NAME = SecurityGroups.formatNameFromBusinessKey(BUSINESS_KEY);

    @After
    public void tearDown() throws Exception {
        quietlyDeleteSecurityGroupIfExists(SECURITY_GROUP_NAME);
        super.tearDown();
    }

    @Test
    public void testCreateSecurityGroup() throws Exception {
        DelegateExecution execution = mock(DelegateExecution.class);

        final ImmutableSet<Rule> ingressRules = ImmutableSet.of(
            Rule.builder().anySource().icmp().createRule(),
            Rule.builder().anySource().port(22).tcp().createRule(),
            Rule.builder().anySource().port(53).udp().createRule()
        );

        final Network network = Network.builder().ingress(ingressRules).createNetwork();

        PoolSpec poolSpec = mock(PoolSpec.class);

        when(poolSpec.getProvider()).thenReturn(provider);
        when(poolSpec.getNetwork()).thenReturn(network);

        when(execution.getVariable(CoreProcessVariables.POOL)).thenReturn(poolSpec);
        when(execution.getProcessBusinessKey()).thenReturn(BUSINESS_KEY);

        activity.execute(execution);
        assertSecurityGroupExistsWithRules(SECURITY_GROUP_NAME, ingressRules);

        /* If any permissions is removed it should converge on a second run */
        Set<Rule> expectedAfterRevoke = revokeAnyRule(ingressRules);
        assertSecurityGroupExistsWithRules(SECURITY_GROUP_NAME, expectedAfterRevoke);

        activity.execute(execution);
        assertSecurityGroupExistsWithRules(SECURITY_GROUP_NAME, ingressRules);

        /* If any permissions is added it should converge on a second run */
        final IpPermission permission = new IpPermission().withIpProtocol("tcp")
            .withIpRanges("0.0.0.0/0").withFromPort(100).withToPort(120);

        client.authorizeSecurityGroupIngress(new AuthorizeSecurityGroupIngressRequest()
            .withGroupName(SECURITY_GROUP_NAME).withIpPermissions(permission));

        assertSecurityGroupExistsWithRules(SECURITY_GROUP_NAME, Sets.<Rule>union(ingressRules,
            ImmutableSet.of(ConvertIpPermissionToRule.FUNCTION.apply(permission))));

        activity.execute(execution);
        assertSecurityGroupExistsWithRules(SECURITY_GROUP_NAME, ingressRules);
    }

    private Set<Rule> revokeAnyRule(Set<Rule> ingressRules) {
        List<IpPermission> permissions = newArrayList(transform(ingressRules, ConvertRuleToIpPermission.FUNCTION));
        Collections.shuffle(permissions);

        IpPermission permission = permissions.get(0);
        LOG.info(">> Revoking permission {} on {} for test.", permission, SECURITY_GROUP_NAME);

        client.revokeSecurityGroupIngress(new RevokeSecurityGroupIngressRequest()
            .withGroupName(SECURITY_GROUP_NAME).withIpPermissions(permission));

        return Sets.difference(ingressRules, ImmutableSet.of(ConvertIpPermissionToRule.FUNCTION.apply(permission)));
    }

    public void assertSecurityGroupExistsWithRules(String groupName, final Set<Rule> ingressRules) {
        DescribeSecurityGroupsResult result = client.describeSecurityGroups(
            new DescribeSecurityGroupsRequest().withGroupNames(groupName));

        assertThat(result.getSecurityGroups()).hasSize(1);
        SecurityGroup group = getOnlyElement(result.getSecurityGroups());

        assertThat(group.getIpPermissions()).hasSize(ingressRules.size());
        assertThat(ingressRules).containsAll(transform(group.getIpPermissions(),
            ConvertIpPermissionToRule.FUNCTION));
    }
}
