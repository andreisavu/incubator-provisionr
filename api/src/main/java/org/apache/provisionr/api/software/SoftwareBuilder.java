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

package org.apache.provisionr.api.software;

import org.apache.provisionr.api.util.BuilderWithOptions;
import static com.google.common.base.Preconditions.checkNotNull;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import java.util.Map;

public class SoftwareBuilder extends BuilderWithOptions<SoftwareBuilder> {

    private String imageId = "default";
    private boolean cachedImage = false;

    private ImmutableMap.Builder<String, String> files = ImmutableMap.builder();
    private ImmutableList.Builder<String> packages = ImmutableList.builder();
    private ImmutableList.Builder<Repository> repositories = ImmutableList.builder();

    @Override
    protected SoftwareBuilder getThis() {
        return this;
    }

    public SoftwareBuilder imageId(String imageId) {
        this.imageId = checkNotNull(imageId, "The imageId was null");
        return this;
    }

    public SoftwareBuilder cachedImage(boolean cachedImage) {
        this.cachedImage = cachedImage;
        return this;
    }

    public SoftwareBuilder files(Map<String, String> files) {
        this.files = ImmutableMap.<String, String>builder().putAll(files);
        return this;
    }

    public SoftwareBuilder file(String sourceUrl, String destinationPath) {
        this.files.put(sourceUrl, destinationPath);
        return this;
    }

    public SoftwareBuilder packages(Iterable<String> packages) {
        this.packages = ImmutableList.<String>builder().addAll(packages);
        return this;
    }

    public SoftwareBuilder packages(String... packages) {
        this.packages = ImmutableList.<String>builder().addAll(Lists.newArrayList(packages));
        return this;
    }

    public SoftwareBuilder addPackage(String pkg) {
        this.packages.add(pkg);
        return this;
    }

    public SoftwareBuilder repositories(Iterable<Repository> repositories) {
        this.repositories = ImmutableList.<Repository>builder().addAll(repositories);
        return this;
    }

    public SoftwareBuilder repository(Repository repository) {
        this.repositories.add(repository);
        return this;
    }

    public Software createSoftware() {
        return new Software(imageId, cachedImage, files.build(), packages.build(),
            repositories.build(), buildOptions());
    }
}