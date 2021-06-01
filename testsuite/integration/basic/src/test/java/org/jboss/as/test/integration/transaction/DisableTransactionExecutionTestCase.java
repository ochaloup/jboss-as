/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2021 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jboss.as.test.integration.transaction;

import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.jboss.as.arquillian.container.ManagementClient;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.test.integration.transactions.TestXAResource;
import org.jboss.as.test.integration.transactions.TransactionCheckerSingleton;
import org.jboss.as.test.integration.transactions.TxTestUtil;
import org.jboss.dmr.ModelNode;
import org.jboss.logging.Logger;
import org.jboss.remoting3.security.RemotingPermission;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import javax.annotation.Resource;
import javax.inject.Inject;
import javax.transaction.TransactionManager;

import java.io.FilePermission;

import static org.jboss.as.test.integration.transaction.CliOperations.TXN_SUBSYSTEM_ADDRESS;
import static org.jboss.as.test.shared.PermissionUtils.createPermissionsXmlAsset;

@RunWith(Arquillian.class)
public class DisableTransactionExecutionTestCase {
    private static final Logger log = Logger.getLogger(DisableTransactionExecutionTestCase.class);

    private static final String TRANSACTION_ENABLED_ATTR_NAME = "transactions-enabled";
    private static final ModelNode areTransactionEnabledOperation =
            Util.getReadAttributeOperation(TXN_SUBSYSTEM_ADDRESS, TRANSACTION_ENABLED_ATTR_NAME);

    @Resource(lookup = "java:/TransactionManager")
    private static TransactionManager tm;

    @ArquillianResource
    private ManagementClient managementClient;

    @Inject
    private TransactionCheckerSingleton transactionChecker;

    @Deployment
    public static JavaArchive createDeployment() {
        return ShrinkWrap.create(JavaArchive.class)
                .addClasses(DisableTransactionExecutionTestCase.class, CliOperations.class)
                .addPackage(TestXAResource.class.getPackage())
                .addAsManifestResource(new StringAsset("Dependencies: org.jboss.as.controller, org.jboss.remoting\n"), "MANIFEST.MF")
                .addAsManifestResource(createPermissionsXmlAsset(
                        // ManagementClient needs the following permissions and a dependency on 'org.jboss.remoting3' module
                        new RemotingPermission("createEndpoint"),
                        new RemotingPermission("connect"),
                        new FilePermission(System.getProperty("jboss.inst") + "/standalone/tmp/auth/*", "read")
                ), "permissions.xml");
    }

    @Before
    public void setUp() {
        areTransactionEnabledOperation.protect();
        transactionChecker.resetAll();
    }

    @Test
    public void executeWithTransactionEnabled() throws Exception {
        ModelNode transactionsDisableOperation = Util.getWriteAttributeOperation(TXN_SUBSYSTEM_ADDRESS, TRANSACTION_ENABLED_ATTR_NAME, false);
        CliOperations.executeForResult(managementClient.getControllerClient(), transactionsDisableOperation);
        ModelNode transactionsEnableOperation = Util.getWriteAttributeOperation(TXN_SUBSYSTEM_ADDRESS, TRANSACTION_ENABLED_ATTR_NAME, true);
        CliOperations.executeForResult(managementClient.getControllerClient(), transactionsEnableOperation);

        ModelNode areTransactionEnabledOperation = Util.getReadAttributeOperation(TXN_SUBSYSTEM_ADDRESS, TRANSACTION_ENABLED_ATTR_NAME);
        ModelNode value = CliOperations.executeForResult(managementClient.getControllerClient(), areTransactionEnabledOperation);
        Assert.assertEquals("Expecting transactions are enabled", true, value.asBoolean());

        tm.begin();
        TxTestUtil.enlistTestXAResource(tm, transactionChecker);
        tm.commit();
        Assert.assertEquals("TestXAResource had to be committed", 1, transactionChecker.getCommitted());
    }

    @Test
    public void executeWithTransactionDisabled() {
        try {
            ModelNode transactionsDisableOperation = Util.getWriteAttributeOperation(TXN_SUBSYSTEM_ADDRESS, TRANSACTION_ENABLED_ATTR_NAME, false);
            CliOperations.executeForResult(managementClient.getControllerClient(), transactionsDisableOperation);
            ModelNode value =  CliOperations.executeForResult(managementClient.getControllerClient(), areTransactionEnabledOperation);
            Assert.assertEquals("Expecting the transactions are disabled", false, value.asBoolean());

            try {
                tm.begin();
                Assert.fail("With disabled transaction manager the begin operation is expected to fail.");
            } catch (IllegalStateException | NullPointerException expected) { // NPE: WFTC-90
                // expected that the begin() fails
                if (expected instanceof IllegalStateException) {
                    log.error("Wrong exception message of IllegalStateException", expected);
                    Assert.assertTrue("Expected the IllegalStateException was thrown from WFTC with null info",
                            expected.getMessage().contains("null transaction"));
                }
            } catch (Exception e) {
                log.error("Unexpected exception on disabled transactions", e);
                Assert.fail("The TransactionManager.begin() with disable transaction should fail but this unexpected exception: " + e);
            }
        } finally {
            ModelNode transactionsEnableOperation = Util.getWriteAttributeOperation(TXN_SUBSYSTEM_ADDRESS, TRANSACTION_ENABLED_ATTR_NAME, true);
            CliOperations.executeForResult(managementClient.getControllerClient(), transactionsEnableOperation);
        }
    }
}
