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
import javax.annotation.Generated;

public class PoolInstance {

    public static PoolInstanceBuilder builder() {
        return new PoolInstanceBuilder();
    }

    private final String key;
    private final List<Machine> machines;
    private final String status;
    private final PoolSpec poolSpec;

    public PoolInstance(String key, List<Machine> machines, String status, PoolSpec poolSpec) {
        this.key = checkNotNull(key, "key is null");
        this.machines = ImmutableList.copyOf(machines);
        this.status = checkNotNull(status, "status is null");
        this.poolSpec = checkNotNull(poolSpec, "poolSpec is null");
    }

    public String getKey() {
        return key;
    }

    public List<Machine> getMachines() {
        return machines;
    }

    public String getStatus() {
        return status;
    }

    public PoolSpec getPoolSpec() {
        return poolSpec;
    }

    @Override
    @Generated("intellij")
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        PoolInstance that = (PoolInstance) o;

        if (!key.equals(that.key)) return false;
        if (!machines.equals(that.machines)) return false;
        if (!poolSpec.equals(that.poolSpec)) return false;
        if (!status.equals(that.status)) return false;

        return true;
    }

    @Override
    @Generated("intellij")
    public int hashCode() {
        int result = key.hashCode();
        result = 31 * result + machines.hashCode();
        result = 31 * result + status.hashCode();
        result = 31 * result + poolSpec.hashCode();
        return result;
    }

    @Override
    @Generated("intellij")
    public String toString() {
        return "PoolInstance{" +
            "key='" + key + '\'' +
            ", machines=" + machines +
            ", status='" + status + '\'' +
            ", poolSpec=" + poolSpec +
            '}';
    }
}
