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

package org.apache.provisionr.api;

import com.google.common.base.Optional;
import java.util.List;
import org.apache.provisionr.api.pool.PoolInstance;
import org.apache.provisionr.api.pool.PoolSpec;
import org.apache.provisionr.api.provider.Provider;

public interface Provisionr {

    /**
     * Get unique ID for this provisionr
     */
    public String getId();


    /**
     * Return the default provider configured for this bundle using the
     * Blueprint configuration mechanism or something else
     *
     * @see Provider
     */
    public Optional<Provider> getDefaultProvider();

    /**
     * Start a provisioning process based on the pool specification
     * <p/>
     * This process will run until the pool is destroyed
     *
     * @param poolKey  user defined unique pool instance identifier
     * @param poolSpec pool specification
     */
    void startPoolManagementProcess(String poolKey, PoolSpec poolSpec);

    /**
     * Get the pool instance object for a specific key
     *
     * @param poolKey user defined unique pool instance identifier
     * @return
     */
    PoolInstance getPoolInstance(String poolKey);

    /**
     * List all pool instances managed by this provisionr
     */
    List<PoolInstance> listPoolInstances();

    /**
     * Trigger pool termination. This will terminate all the machines
     *
     * @param poolKey external pool unique key
     */
    void triggerPoolManagementProcessTermination(String poolKey);
}
