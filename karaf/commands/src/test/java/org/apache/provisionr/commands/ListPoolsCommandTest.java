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

import org.apache.provisionr.api.pool.PoolSpec;
import org.apache.provisionr.core.CoreProcessVariables;
import com.google.common.collect.ImmutableList;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Collections;
import java.util.List;
import org.activiti.engine.ProcessEngine;
import org.activiti.engine.RuntimeService;
import org.activiti.engine.runtime.ProcessInstance;
import org.activiti.engine.runtime.ProcessInstanceQuery;
import org.apache.felix.service.command.CommandSession;
import static org.fest.assertions.api.Assertions.assertThat;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Matchers;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ListPoolsCommandTest {

    private ByteArrayOutputStream outputStream;
    private PrintStream out;

    @Before
    public void setUp() {
        outputStream = new ByteArrayOutputStream();
        out = new PrintStream(outputStream);
    }

    @After
    public void tearDown() throws IOException {
        out.close();
        outputStream.close();
    }

    @Test
    public void testNoProcessesRunning() throws Exception {
        final ProcessEngine processEngine = newProcessEngineMock(Collections.<ProcessInstance>emptyList());

        ListPoolsCommand command = new ListPoolsCommand(processEngine);
        command.setOut(out);

        CommandSession session = mock(CommandSession.class);
        command.execute(session);
        out.flush();

        assertThat(outputStream.toString()).containsIgnoringCase("no active pools found");
        outputStream.reset();

        command.setKey("dummy");
        command.execute(session);
        out.flush();

        assertThat(outputStream.toString()).containsIgnoringCase("no active pools found");
    }

    @Test
    public void testListProcess() throws Exception {
        final List<ProcessInstance> processes = ImmutableList.of(
            newProcessInstanceMock("p1", "k1"),
            newProcessInstanceMock("p2", "k2")
        );
        final ProcessEngine processEngine = newProcessEngineMock(processes);

        PoolSpec poolSpec = mock(PoolSpec.class);
        setVariable(processEngine, "p1", CoreProcessVariables.POOL, poolSpec);
        setVariable(processEngine, "p1", CoreProcessVariables.POOL_BUSINESS_KEY, "k1");

        ListPoolsCommand command = new ListPoolsCommand(processEngine);
        command.setOut(out);

        /* list all active pools */
        CommandSession session = mock(CommandSession.class);
        command.execute(session);
        out.flush();

        assertThat(outputStream.toString())
            .contains("PoolSpec Description")
            .contains("List of Machines")
            .contains("PoolSpec Key: k1");

        /* run the same command with a filter on business key */
        outputStream.reset();
        command.setKey("k2");
        command.execute(session);
        out.flush();

        assertThat(outputStream.toString()).isEmpty();
    }

    private ProcessInstance newProcessInstanceMock(String id, String businessKey) {
        ProcessInstance instance = mock(ProcessInstance.class);

        when(instance.getId()).thenReturn(id);
        when(instance.getBusinessKey()).thenReturn(businessKey);

        return instance;
    }

    private void setVariable(ProcessEngine engine, String instanceId, String key, Object value) {
        RuntimeService runtimeService = engine.getRuntimeService();

        when(runtimeService.getVariable(instanceId, key)).thenReturn(value);
    }

    private ProcessEngine newProcessEngineMock(List<ProcessInstance> instances) {
        ProcessEngine processEngine = mock(ProcessEngine.class);

        RuntimeService runtimeService = mock(RuntimeService.class);
        when(processEngine.getRuntimeService()).thenReturn(runtimeService);

        ProcessInstanceQuery allInstancesQuery = mock(ProcessInstanceQuery.class);
        when(allInstancesQuery.list()).thenReturn(instances);
        when(runtimeService.createProcessInstanceQuery()).thenReturn(allInstancesQuery);

        for (ProcessInstance instance : instances) {
            ProcessInstanceQuery singleResultQuery = mock(ProcessInstanceQuery.class);

            when(singleResultQuery.list()).thenReturn(ImmutableList.of(instance));
            when(allInstancesQuery.processInstanceBusinessKey(instance.getBusinessKey()))
                .thenReturn(singleResultQuery);
        }

        if (instances.isEmpty()) {
            ProcessInstanceQuery emptyQuery = mock(ProcessInstanceQuery.class);

            when(emptyQuery.list()).thenReturn(Collections.<ProcessInstance>emptyList());
            when(allInstancesQuery.processInstanceBusinessKey(Matchers.<String>any()))
                .thenReturn(emptyQuery);
        }

        return processEngine;
    }

}
