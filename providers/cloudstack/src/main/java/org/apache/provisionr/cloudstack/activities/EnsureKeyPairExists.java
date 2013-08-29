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

import org.apache.provisionr.api.pool.PoolSpec;
import org.apache.provisionr.cloudstack.core.KeyPairs;
import org.activiti.engine.delegate.DelegateExecution;
import org.jclouds.cloudstack.CloudStackClient;
import org.jclouds.cloudstack.domain.SshKeyPair;
import org.jclouds.cloudstack.features.SSHKeyPairClient;
import org.jclouds.crypto.SshKeys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EnsureKeyPairExists extends CloudStackActivity {

    private static final Logger LOG = LoggerFactory.getLogger(EnsureKeyPairExists.class);

    @Override
    public void execute(CloudStackClient cloudStackClient, PoolSpec poolSpec, DelegateExecution execution) {
        String keyName = KeyPairs.formatNameFromBusinessKey(execution.getProcessBusinessKey());
        LOG.info("Creating admin access key pair as {}", keyName);
        SSHKeyPairClient sshKeyPairClient = cloudStackClient.getSSHKeyPairClient();
        try {
            SshKeyPair sshKeyPair = sshKeyPairClient.registerSSHKeyPair(keyName, poolSpec.getAdminAccess().getPublicKey());
            LOG.info("Registered remote key with fingerprint {}", sshKeyPair.getFingerprint());
        } catch (IllegalStateException e) {
            LOG.warn("Key with name {} already exists", keyName);
            SshKeyPair key = sshKeyPairClient.getSSHKeyPair(keyName);
            if (key.getFingerprint().equals(SshKeys.fingerprintPublicKey(poolSpec.getAdminAccess().getPublicKey()))) {
                LOG.info("Fingerprints match. Not updating admin access key pair.");
            } else {
                LOG.info("Fingerprint do not match. Replacing admin access key pair.");
                sshKeyPairClient.deleteSSHKeyPair(keyName);
                sshKeyPairClient.registerSSHKeyPair(keyName, poolSpec.getAdminAccess().getPublicKey());
            }
        }
    }
}
