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

package org.apache.provisionr.cloudstack.activities;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.Closeables;
import com.google.inject.Module;
import java.util.Properties;
import org.activiti.engine.delegate.DelegateExecution;
import org.activiti.engine.delegate.JavaDelegate;
import org.apache.provisionr.api.pool.PoolSpec;
import org.apache.provisionr.api.provider.Provider;
import org.apache.provisionr.core.CoreProcessVariables;
import org.jclouds.Constants;
import org.jclouds.ContextBuilder;
import org.jclouds.cloudstack.CloudStackApiMetadata;
import org.jclouds.cloudstack.CloudStackAsyncClient;
import org.jclouds.cloudstack.CloudStackClient;
import org.jclouds.logging.slf4j.config.SLF4JLoggingModule;
import org.jclouds.rest.RestContext;

/**
 * Base class for all activities that require access to a CloudStack based cloud.
 */
public abstract class CloudStackActivity implements JavaDelegate {

    /**
     * Implement activity logic in this method. It will be called with a reference to the {@link CloudStackClient}
     */
    public abstract void execute(CloudStackClient cloudStackClient, PoolSpec poolSpec, DelegateExecution execution);

    @Override
    public void execute(DelegateExecution execution) throws Exception {

        RestContext<CloudStackClient, CloudStackAsyncClient> restContext = null;
        try {
            PoolSpec poolSpec = PoolSpec.class.cast(checkNotNull(execution.getVariable(CoreProcessVariables.POOL),
                "Please add 'poolSpec' variable to the process"));
            // delegate
            restContext = newCloudStackClient(poolSpec.getProvider());
            execute(restContext.getApi(), poolSpec, execution);

        } finally {
            Closeables.closeQuietly(restContext);
        }
    }

    /**
     * Creates a new {@link CloudStackClient} with {@link Provider} supplied credentials.
     */
    RestContext<CloudStackClient, CloudStackAsyncClient> newCloudStackClient(Provider provider) {
        checkArgument(provider.getEndpoint().isPresent(), "please specify an endpoint for this provider");

        Properties overrides = new Properties();
        overrides.setProperty(Constants.PROPERTY_TRUST_ALL_CERTS, "true");

        return ContextBuilder.newBuilder(new CloudStackApiMetadata())
            .endpoint(provider.getEndpoint().get())
            .modules(ImmutableSet.<Module>of(new SLF4JLoggingModule()))
            .credentials(provider.getAccessKey(), provider.getSecretKey())
            .overrides(overrides)
            .build(CloudStackApiMetadata.CONTEXT_TOKEN);
    }
}
