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

package org.apache.provisionr.api.pool;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import org.apache.provisionr.api.access.AdminAccess;
import org.apache.provisionr.api.hardware.Hardware;
import org.apache.provisionr.api.network.Network;
import org.apache.provisionr.api.provider.Provider;
import org.apache.provisionr.api.software.Software;
import org.apache.provisionr.api.util.BuilderWithOptions;

public class PoolSpecBuilder extends BuilderWithOptions<PoolSpecBuilder> {

    private Provider provider;
    private Network network;

    private AdminAccess adminAccess;
    private Software software;
    private Hardware hardware;

    private int minSize = -1;
    private int expectedSize = -1;

    private int bootstrapTimeInSeconds = 15 * 60;

    @Override
    protected PoolSpecBuilder getThis() {
        return this;
    }

    public PoolSpecBuilder provider(Provider provider) {
        this.provider = checkNotNull(provider, "provider is null");
        return this;
    }

    public PoolSpecBuilder network(Network network) {
        this.network = checkNotNull(network, "network is null");
        return this;
    }

    public PoolSpecBuilder adminAccess(AdminAccess adminAccess) {
        this.adminAccess = checkNotNull(adminAccess, "adminAccess is null");
        return this;
    }

    public PoolSpecBuilder software(Software software) {
        this.software = checkNotNull(software, "software is null");
        return this;
    }

    public PoolSpecBuilder hardware(Hardware hardware) {
        this.hardware = checkNotNull(hardware, "hardware is null");
        return this;
    }

    public PoolSpecBuilder minSize(int minSize) {
        checkArgument(minSize > 0, "minSize should be positive");
        this.minSize = minSize;
        return this;
    }

    public PoolSpecBuilder expectedSize(int expectedSize) {
        checkArgument(expectedSize > 0, "expectedSize should be positive");
        this.expectedSize = expectedSize;
        return this;
    }

    public PoolSpecBuilder bootstrapTimeInSeconds(int bootstrapTimeInSeconds) {
        checkArgument(bootstrapTimeInSeconds > 0, "bootstrapTimeInSeconds should be positive");
        this.bootstrapTimeInSeconds = bootstrapTimeInSeconds;
        return this;
    }

    public PoolSpec createPool() {
        return new PoolSpec(provider, network, adminAccess, software, hardware, minSize,
            expectedSize, bootstrapTimeInSeconds, buildOptions());
    }
}