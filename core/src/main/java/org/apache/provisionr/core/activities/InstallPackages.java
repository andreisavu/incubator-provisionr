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

package org.apache.provisionr.core.activities;

import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.apache.provisionr.api.pool.Machine;
import org.apache.provisionr.api.pool.PoolSpec;
import org.apache.provisionr.api.software.Software;
import org.apache.provisionr.core.Mustache;

public class InstallPackages extends PuppetActivity {

    public static final String PACKAGES_TEMPLATE = "/org/apache/provisionr/core/puppet/packages.pp.mustache";

    public InstallPackages() {
        super("packages");
    }

    @Override
    public String createPuppetScript(PoolSpec poolSpec, Machine machine) {
        try {
            return Mustache.toString(InstallPackages.class, PACKAGES_TEMPLATE,
                ImmutableMap.of("packages", packagesAsListOfMaps(poolSpec.getSoftware())));

        } catch (IOException e) {
            throw Throwables.propagate(e);
        }
    }

    private List<Map<String, String>> packagesAsListOfMaps(Software software) {
        ImmutableList.Builder<Map<String, String>> result = ImmutableList.builder();
        for (String pkg : software.getPackages()) {
            result.add(ImmutableMap.of("package", pkg));
        }
        return result.build();
    }
}
