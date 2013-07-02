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

import static com.google.common.base.Preconditions.checkNotNull;
import com.google.common.collect.ImmutableList;
import java.util.List;

public class PoolInstanceBuilder {

    private String key;
    private List<Machine> machines;
    private String status;
    private PoolSpec poolSpec;

    public PoolInstanceBuilder key(String key) {
        this.key = checkNotNull(key, "key is null");
        return this;
    }

    public PoolInstanceBuilder machines(List<Machine> machines) {
        this.machines = ImmutableList.copyOf(machines);
        return this;
    }

    public PoolInstanceBuilder status(String status) {
        this.status = checkNotNull(status, "status is null");
        return this;
    }

    public PoolInstanceBuilder pool(PoolSpec poolSpec) {
        this.poolSpec = checkNotNull(poolSpec, "poolSpec is null");
        return this;
    }

    public PoolInstance createPoolInstance() {
        return new PoolInstance(key, machines, status, poolSpec);
    }
}