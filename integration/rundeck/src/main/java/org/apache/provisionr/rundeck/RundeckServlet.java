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

package org.apache.provisionr.rundeck;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Function;
import com.google.common.base.Objects;
import static com.google.common.base.Preconditions.checkNotNull;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableMap;
import static com.google.common.collect.Iterables.transform;
import com.google.common.collect.Lists;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import org.activiti.engine.ProcessEngine;
import org.activiti.engine.runtime.ProcessInstance;
import org.apache.provisionr.api.pool.Machine;
import org.apache.provisionr.api.pool.Pool;
import org.apache.provisionr.core.CoreProcessVariables;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RundeckServlet extends HttpServlet {

    private static final Logger LOG = LoggerFactory.getLogger(RundeckServlet.class);

    private final ProcessEngine processEngine;
    private final Marshaller marshaller;

    public RundeckServlet(ProcessEngine processEngine) throws JAXBException {
        this.processEngine = checkNotNull(processEngine, "processEngine is null");

        this.marshaller = JAXBContext.newInstance(Project.class).createMarshaller();
        this.marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
        throws ServletException, IOException {
        response.setContentType("application/xml;charset=UTF-8");
        writeRundeckResourceModelXmlTo(response.getWriter());
    }

    @VisibleForTesting
    void writeRundeckResourceModelXmlTo(PrintWriter writer) {
        final List<ProcessInstance> processes = processEngine.getRuntimeService()
            .createProcessInstanceQuery().list();

        Project project = new Project();
        for (ProcessInstance instance : processes) {
            final Pool pool = (Pool) processEngine.getRuntimeService()
                .getVariable(instance.getId(), CoreProcessVariables.POOL);
            if (pool == null) {
                continue; /* skip - this process is not a provisionr process */
            }

            final String businessKey = (String) processEngine.getRuntimeService()
                .getVariable(instance.getId(), CoreProcessVariables.POOL_BUSINESS_KEY);
            if (!Objects.equal(instance.getBusinessKey(), businessKey)) {
                continue; /* ignore - this is a process started by the main pool management process */
            }

            @SuppressWarnings("unchecked")
            List<Machine> machines = (List<Machine>) processEngine.getRuntimeService()
                .getVariable(instance.getId(), CoreProcessVariables.MACHINES);
            if (machines == null) {
                continue;   /* the list of machines is not yet available */
            }

            project.setNodes(transformMachinesToRundeckNodes(businessKey, pool, machines));
        }

        try {
            marshaller.marshal(project, writer);

        } catch (JAXBException e) {
            throw Throwables.propagate(e);
        }
    }

    @VisibleForTesting
    List<Node> transformMachinesToRundeckNodes(String businessKey, Pool pool, List<Machine> machines) {
        List<Node> nodes = Lists.newArrayList();

        for (Machine machine : machines) {
            Node node = new Node(machine.getExternalId(), machine.getPublicDnsName(),
                pool.getAdminAccess().getUsername());

            node.setTags(transform(pool.getSoftware().getPackages(), withPrefix("package:")));

            node.setAttributes(ImmutableMap.<String, String>builder()
                .put("provider", pool.getProvider().getId())
                .put("key", businessKey)
                .put("publicIp", machine.getPublicIp())
                .put("privateHostname", machine.getPrivateDnsName())
                .put("privateIp", machine.getPrivateIp())
                .put("hardwareType", pool.getHardware().getType())
                .build());

            if (pool.getProvider().getOptions().containsKey("region")) {
                node.addAttribute("region", pool.getProvider().getOption("region"));
            }

            nodes.add(node);
        }

        return nodes;
    }

    @VisibleForTesting
    Function<String, String> withPrefix(final String prefix) {
        return new Function<String, String>() {
            @Override
            public String apply(String value) {
                return prefix + value;
            }
        };
    }
}
