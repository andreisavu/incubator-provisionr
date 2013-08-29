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

import com.google.common.base.Charsets;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.Resources;
import java.io.IOException;
import java.util.Map;
import org.apache.provisionr.api.access.AdminAccess;
import org.apache.provisionr.api.pool.Machine;
import org.apache.provisionr.api.pool.PoolSpec;
import org.apache.provisionr.core.Mustache;
import org.apache.provisionr.core.activities.PuppetActivity;

public class SetupAdminAccess extends PuppetActivity {

    public static final String DEFAULT_UBUNTU_AMI_USER = "ubuntu";

    public static final String ADMIN_ACCESS_TEMPLATE = "/org/apache/provisionr/amazon/puppet/adminaccess.pp.mustache";
    public static final String SSHD_CONFIG_TEMPLATE = "/org/apache/provisionr/amazon/puppet/sshd_config.mustache";
    public static final String SUDOERS_TEMPLATE = "/org/apache/provisionr/amazon/puppet/sudoers";

    public SetupAdminAccess() {
        super("adminaccess");
    }

    @Override
    public AdminAccess overrideAdminAccess(PoolSpec poolSpec) {
        return poolSpec.getAdminAccess().toBuilder().username(DEFAULT_UBUNTU_AMI_USER).createAdminAccess();
    }

    @Override
    public String createPuppetScript(PoolSpec poolSpec, Machine machine) {
        try {
            return Mustache.toString(getClass(), ADMIN_ACCESS_TEMPLATE,
                ImmutableMap.of(
                    "user", poolSpec.getAdminAccess().getUsername(),
                    "publicKey", getRawSshKey(poolSpec))
            );

        } catch (IOException e) {
            throw Throwables.propagate(e);
        }
    }

    private String getRawSshKey(PoolSpec poolSpec) {
        return poolSpec.getAdminAccess().getPublicKey().split(" ")[1];
    }

    @Override
    public Map<String, String> createAdditionalFiles(PoolSpec poolSpec, Machine machine) {
        try {
            return ImmutableMap.of(
                "/tmp/sshd_config",
                Mustache.toString(getClass(), SSHD_CONFIG_TEMPLATE,
                    ImmutableMap.of("user", poolSpec.getAdminAccess().getUsername())),
                "/tmp/sudoers",
                Resources.toString(Resources.getResource(getClass(), SUDOERS_TEMPLATE), Charsets.UTF_8)
            );

        } catch (IOException e) {
            throw Throwables.propagate(e);
        }

    }
}
