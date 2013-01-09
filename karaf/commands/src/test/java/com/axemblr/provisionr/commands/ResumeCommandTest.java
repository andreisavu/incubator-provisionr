/*
 * Copyright (c) 2013 S.C. Axemblr Software Solutions S.R.L
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.axemblr.provisionr.commands;

import com.google.common.collect.ImmutableList;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ResumeCommandTest {

    private static final String BUSINES_KEY = "k1";
    private ByteArrayOutputStream outputStream;
    private PrintStream out;

    @Before
    public void setUp() throws Exception {
        outputStream = new ByteArrayOutputStream();
        out = new PrintStream(outputStream);
    }

    @After
    public void tearDown() throws Exception {
        out.close();
        outputStream.close();
    }

    @Test
    public void testResumeCommandNeedsBusinessKey() throws Exception {
        final ResumeCommand command = new ResumeCommand(newMockProcessEngine());
        command.setOut(out);

        CommandSession commandSession = mock(CommandSession.class);
        command.execute(commandSession);
        out.flush();
        assertThat(outputStream.toString()).containsIgnoringCase("please supply a business key");
    }

    @Test
    public void testResumeCommandCallsActivateMethodForSuspendedProcesses() throws Exception {
        final ResumeCommand command = new ResumeCommand(newMockProcessEngine());
        command.setOut(out);
        command.setBusinessKey(BUSINES_KEY);

        CommandSession commandSession = mock(CommandSession.class);
        command.execute(commandSession);

        out.flush();
        assertThat(outputStream.toString())
            .containsIgnoringCase("found 3")
            .containsIgnoringCase("id p3")
            .containsIgnoringCase("id p1")
            .doesNotContain("id p2");
    }

    private ProcessEngine newMockProcessEngine() {
        final ProcessEngine engine = mock(ProcessEngine.class);
        final List<ProcessInstance> processes = ImmutableList.of(
            newProcessInstanceMock("p1", BUSINES_KEY, true),
            newProcessInstanceMock("p2", BUSINES_KEY, false),
            newProcessInstanceMock("p3", BUSINES_KEY, true)
        );

        final RuntimeService runtimeService = mock(RuntimeService.class);
        final ProcessInstanceQuery processInstanceQuery = mock(ProcessInstanceQuery.class);

        when(engine.getRuntimeService()).thenReturn(runtimeService);
        when(runtimeService.createProcessInstanceQuery()).thenReturn(processInstanceQuery);
        when(processInstanceQuery.processInstanceBusinessKey(BUSINES_KEY)).thenReturn(processInstanceQuery);
        when(processInstanceQuery.orderByProcessInstanceId()).thenReturn(processInstanceQuery);
        when(processInstanceQuery.list()).thenReturn(processes);

        return engine;
    }

    private ProcessInstance newProcessInstanceMock(String id, String businessKey, boolean isSuspended) {
        ProcessInstance instance = mock(ProcessInstance.class);

        when(instance.getId()).thenReturn(id);
        when(instance.getBusinessKey()).thenReturn(businessKey);
        when(instance.isSuspended()).thenReturn(isSuspended);

        return instance;
    }
}
