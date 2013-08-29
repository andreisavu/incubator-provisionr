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

package org.apache.provisionr.commands;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Objects;
import static com.google.common.base.Preconditions.checkNotNull;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import java.io.PrintStream;
import java.util.List;
import org.activiti.engine.ProcessEngine;
import org.activiti.engine.runtime.ProcessInstance;
import org.apache.felix.gogo.commands.Command;
import org.apache.felix.gogo.commands.Option;
import org.apache.karaf.shell.console.OsgiCommandSupport;
import org.apache.provisionr.api.pool.Machine;
import org.apache.provisionr.api.pool.PoolSpec;
import org.apache.provisionr.core.CoreProcessVariables;

@Command(scope = "provisionr", name = "pools", description = "List active pools")
public class ListPoolsCommand extends OsgiCommandSupport {

    private PrintStream out = System.out;

    @Option(name = "-k", aliases = "--key", description = "Key for filtering a specific pool",
        required = false)
    private String key = "";

    private final ProcessEngine processEngine;

    public ListPoolsCommand(ProcessEngine processEngine) {
        this.processEngine = checkNotNull(processEngine, "processEngine is null");
    }

    @Override
    protected Object doExecute() {
        List<ProcessInstance> processes;
        if (key.isEmpty()) {
            processes = processEngine.getRuntimeService().createProcessInstanceQuery().list();
        } else {
            processes = processEngine.getRuntimeService().createProcessInstanceQuery()
                .processInstanceBusinessKey(key).list();
        }

        if (processes.isEmpty()) {
            out.println("No active pools found. You can create one using provisionr:create");
            return null;
        }

        final Gson gson = new GsonBuilder().setPrettyPrinting().create();
        for (ProcessInstance instance : processes) {
            PoolSpec poolSpec = (PoolSpec) processEngine.getRuntimeService()
                .getVariable(instance.getId(), CoreProcessVariables.POOL);
            if (poolSpec == null) {
                continue; /* skip - this process is not a provisionr process */
            }

            String businessKey = (String) processEngine.getRuntimeService()
                .getVariable(instance.getId(), CoreProcessVariables.POOL_BUSINESS_KEY);
            if (!Objects.equal(instance.getBusinessKey(), businessKey)) {
                continue; /* ignore - this is a process started by the main poolSpec management process */
            }

            @SuppressWarnings("unchecked")
            List<Machine> machines = (List<Machine>) processEngine.getRuntimeService()
                .getVariable(instance.getId(), CoreProcessVariables.MACHINES);

            out.println("****** PoolSpec Description ******");
            out.println(gson.toJson(poolSpec));

            out.println("****** List of Machines ******");
            if (machines != null) {
                out.println(gson.toJson(machines, new TypeToken<List<Machine>>() {
                }.getType()));
            }

            out.println("PoolSpec Key: " + instance.getBusinessKey());
            out.println();
        }

        return null;
    }

    @VisibleForTesting
    void setOut(PrintStream out) {
        this.out = checkNotNull(out, "out is null");
    }

    @VisibleForTesting
    void setKey(String key) {
        this.key = checkNotNull(key, "key is null");
    }
}
