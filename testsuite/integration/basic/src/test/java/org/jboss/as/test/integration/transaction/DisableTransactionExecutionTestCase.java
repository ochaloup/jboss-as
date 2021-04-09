/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2021, Red Hat Middleware LLC, and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.jboss.as.test.integration.transaction;

import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.jboss.as.arquillian.container.ManagementClient;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.test.integration.transactions.TestXAResource;
import org.jboss.as.test.integration.transactions.TransactionCheckerSingleton;
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

        TestXAResource xaResource = new TestXAResource(transactionChecker);
        tm.begin();
        tm.getTransaction().enlistResource(xaResource);
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
