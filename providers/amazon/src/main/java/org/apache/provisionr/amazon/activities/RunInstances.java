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

import com.amazonaws.AmazonWebServiceRequest;
import com.amazonaws.services.ec2.model.BlockDeviceMapping;
import com.amazonaws.services.ec2.model.EbsBlockDevice;
import com.amazonaws.services.ec2.model.LaunchSpecification;
import com.amazonaws.services.ec2.model.RequestSpotInstancesRequest;
import com.amazonaws.services.ec2.model.RunInstancesRequest;
import com.amazonaws.services.ec2.model.SpotInstanceType;
import com.google.common.base.Charsets;
import static com.google.common.base.Preconditions.checkNotNull;
import com.google.common.base.Strings;
import com.google.common.base.Throwables;
import com.google.common.collect.Lists;
import com.google.common.io.Resources;
import java.io.IOException;
import java.util.Calendar;
import java.util.List;
import net.schmizz.sshj.common.Base64;
import org.activiti.engine.delegate.DelegateExecution;
import org.apache.provisionr.amazon.core.ImageTable;
import org.apache.provisionr.amazon.core.ImageTableQuery;
import org.apache.provisionr.amazon.core.KeyPairs;
import org.apache.provisionr.amazon.core.ProviderClientCache;
import org.apache.provisionr.amazon.core.SecurityGroups;
import org.apache.provisionr.amazon.options.ProviderOptions;
import org.apache.provisionr.amazon.options.SoftwareOptions;
import org.apache.provisionr.api.hardware.BlockDevice;
import org.apache.provisionr.api.pool.PoolSpec;
import org.apache.provisionr.api.provider.Provider;

public abstract class RunInstances extends AmazonActivity {

    public static final String DEFAULT_ARCH = "amd64";
    public static final String DEFAULT_TYPE = "instance-store";
    public static final String DEFAULT_AMI_ID = "ami-0cdf4965"; // Ubuntu 12.10 x64

    protected RunInstances(ProviderClientCache providerClientCache) {
        super(providerClientCache);
    }

    protected RunInstancesRequest createOnDemandInstancesRequest(PoolSpec poolSpec, DelegateExecution execution)
        throws IOException {
        return (RunInstancesRequest) createRequest(poolSpec, execution, false);
    }

    protected RequestSpotInstancesRequest createSpotInstancesRequest(PoolSpec poolSpec, DelegateExecution execution)
        throws IOException {
        return (RequestSpotInstancesRequest) createRequest(poolSpec, execution, true);
    }

    private AmazonWebServiceRequest createRequest(PoolSpec poolSpec, DelegateExecution execution, boolean spot)
        throws IOException {
        final String businessKey = execution.getProcessBusinessKey();

        final String securityGroupName = SecurityGroups.formatNameFromBusinessKey(businessKey);
        final String keyPairName = KeyPairs.formatNameFromBusinessKey(businessKey);

        final String instanceType = poolSpec.getHardware().getType();
        final String imageId = getImageIdFromPoolConfigurationOrQueryImageTable(
            poolSpec, poolSpec.getProvider(), instanceType);

        final String userData = Resources.toString(Resources.getResource(RunInstances.class,
            "/org/apache/provisionr/amazon/userdata.sh"), Charsets.UTF_8);

        List<BlockDevice> blockDevices = poolSpec.getHardware().getBlockDevices();
        List<BlockDeviceMapping> blockDeviceMappings = Lists.newArrayList();
        if (blockDevices != null && blockDevices.size() > 0) {
            for (BlockDevice device : blockDevices) {
                blockDeviceMappings.add(new BlockDeviceMapping()
                    .withDeviceName(device.getName())
                    .withEbs(new EbsBlockDevice()
                        .withVolumeSize(device.getSize())
                        .withDeleteOnTermination(true)
                    ));
            }
        }

        if (spot) {
            Calendar validUntil = Calendar.getInstance();
            validUntil.add(Calendar.MINUTE, 10);

            final String spotPrice = checkNotNull(poolSpec.getProvider().getOption(ProviderOptions.SPOT_BID),
                "The bid for spot instances was not specified");

            LaunchSpecification ls = new LaunchSpecification()
                .withInstanceType(instanceType)
                .withKeyName(keyPairName)
                .withImageId(imageId)
                .withBlockDeviceMappings(blockDeviceMappings)
                .withSecurityGroups(Lists.newArrayList(securityGroupName))
                .withUserData(Base64.encodeBytes(userData.getBytes(Charsets.UTF_8)));

            return new RequestSpotInstancesRequest()
                .withSpotPrice(spotPrice)
                .withLaunchSpecification(ls)
                .withLaunchGroup(businessKey)
                .withInstanceCount(poolSpec.getExpectedSize())
                .withType(SpotInstanceType.OneTime)
                .withValidUntil(validUntil.getTime());

        } else {
            return new RunInstancesRequest()
                .withClientToken(businessKey)
                .withSecurityGroups(securityGroupName)
                .withKeyName(keyPairName)
                .withInstanceType(instanceType)
                .withImageId(imageId)
                .withBlockDeviceMappings(blockDeviceMappings)
                .withMinCount(poolSpec.getMinSize())
                .withMaxCount(poolSpec.getExpectedSize())
                .withUserData(Base64.encodeBytes(userData.getBytes(Charsets.UTF_8)));
        }
    }

    private String getImageIdFromPoolConfigurationOrQueryImageTable(
        PoolSpec poolSpec, Provider provider, String instanceType
    ) {
        final String imageId = poolSpec.getSoftware().getImageId();
        if (!Strings.isNullOrEmpty(imageId)) {
            return "default".equals(imageId) ? DEFAULT_AMI_ID : imageId;
        }

        ImageTable imageTable;
        try {
            imageTable = ImageTable.fromCsvResource("/org/apache/provisionr/amazon/ubuntu.csv");
        } catch (IOException e) {
            throw Throwables.propagate(e);
        }

        final String region = provider.getOptionOr(ProviderOptions.REGION, ProviderOptions.DEFAULT_REGION);
        final String version = provider.getOptionOr(SoftwareOptions.BASE_OPERATING_SYSTEM_VERSION,
            SoftwareOptions.DEFAULT_BASE_OPERATING_SYSTEM_VERSION);

        ImageTableQuery query = imageTable.query()
            .filterBy("region", region)
            .filterBy("version", version)
            .filterBy("arch", DEFAULT_ARCH);

        if (instanceType.equals("t1.micro")) {
            query.filterBy("type", "ebs");
        } else {
            query.filterBy("type", DEFAULT_TYPE);
        }

        return query.singleResult();
    }
}
