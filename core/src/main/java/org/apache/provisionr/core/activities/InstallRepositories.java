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

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.apache.provisionr.api.pool.Machine;
import org.apache.provisionr.api.pool.PoolSpec;
import org.apache.provisionr.api.software.Repository;
import org.apache.provisionr.api.software.Software;
import org.apache.provisionr.core.Mustache;

public class InstallRepositories extends PuppetActivity {

    public static final String REPOSITORIES_TEMPLATE =
        "/org/apache/provisionr/core/puppet/repositories.pp.mustache";

    public InstallRepositories() {
        super("repositories");
    }

    @Override
    public String createPuppetScript(PoolSpec poolSpec, Machine machine) {
        try {
            return Mustache.toString(getClass(), REPOSITORIES_TEMPLATE,
                ImmutableMap.<String, List<Map<String, String>>>of(
                    "repositories", repositoriesAsListOfMaps(poolSpec.getSoftware())));

        } catch (IOException e) {
            throw Throwables.propagate(e);
        }
    }

    private List<Map<String, String>> repositoriesAsListOfMaps(Software software) {
        return Lists.transform(software.getRepositories(), new Function<Repository, Map<String, String>>() {
            @Override
            public Map<String, String> apply(Repository repository) {
                ImmutableMap.Builder<String, String> builder = ImmutableMap.<String, String>builder()
                    .put("name", repository.getName())
                    .put("content", Joiner.on("\\n").join(repository.getEntries()));

                if (repository.getKey().isPresent()) {
                    builder.put("key", repository.getKey().get().replace("\n", "\\n"));
                }
                return builder.build();
            }
        });
    }
}
