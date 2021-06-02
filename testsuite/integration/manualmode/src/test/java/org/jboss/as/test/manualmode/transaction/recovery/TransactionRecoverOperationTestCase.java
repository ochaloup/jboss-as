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

package org.jboss.as.test.manualmode.transaction.recovery;

import org.jboss.arquillian.container.test.api.ContainerController;
import org.jboss.arquillian.container.test.api.Deployer;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.container.test.api.TargetsContainer;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.test.integration.management.base.AbstractCliTestBase;
import org.jboss.as.test.integration.management.util.CLIOpResult;
import org.jboss.as.test.integration.security.common.Utils;
import org.jboss.as.test.integration.transactions.PersistentTestXAResource;
import org.jboss.as.test.integration.transactions.TestXAResource;
import org.jboss.as.test.integration.transactions.XidsPersister;
import org.jboss.as.test.shared.TestSuiteEnvironment;
import org.jboss.as.test.shared.TimeoutUtil;
import org.jboss.dmr.ModelNode;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import javax.transaction.xa.Xid;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Paths;
import java.util.Collection;

@RunWith(Arquillian.class)
@RunAsClient
public class TransactionRecoverOperationTestCase extends AbstractCliTestBase {
    private static final int CLI_INIT_TIMEOUT = TimeoutUtil.adjust(20_000);
    private static final String CONTAINER = "default-jbossas";
    private static final String DEPLOYMENT_NAME = "transaction-recovery-operation";

    // wflyDataDir is place where container 'default-jbossas' stores the data (see arquillian.xml)
    private static final File wflyDataDir = Paths.get(System.getProperty("jbossas.ts.submodule.dir"), "target", "wildfly", "standalone", "data").toFile();
    private static final XidsPersister xidsPersister = new XidsPersister(wflyDataDir, PersistentTestXAResource.XIDS_PERSISTER_FILE_NAME);

    private static final String ORPHAN_SAFETY_INTERVAL_ATTRIBUTE_NAME = "orphan-safety-interval";
    private static final String RECOVERY_PERIOD_ATTRIBUTE_NAME = "recovery-period";
    private static final String RECOVERY_BACKOFF_PERIOD_ATTRIBUTE_NAME = "recovery-backoff-period";
    private static final String ALLOW_RECOVERY_SUSPENSION_ATTRIBUTE_NAME = "allow-recovery-suspension";
    private static final String RECOVERY_INITIALIZATION_OFFSET_SYSTEM_PROPERTY_NAME = "RecoveryEnvironmentBean.periodicRecoveryInitilizationOffset";

    private int orphanSafetyIntervalOriginal, recoveryPeriodOriginal, recoveryBackoffPeriodOriginal;
    private boolean allowRecoverySuspensionOriginal;

    @ArquillianResource
    private ContainerController container;
    @ArquillianResource
    Deployer deployer;

    @Deployment(name = DEPLOYMENT_NAME, managed = false, testable = false)
    @TargetsContainer(CONTAINER)
    public static WebArchive deployment() {
        return ShrinkWrap.create(WebArchive.class, DEPLOYMENT_NAME + ".war")
            .addClasses(TransactionRecoveryApplication.class, TransactionRecoveryEndPoint.class)
            .addPackages(true, TestXAResource.class.getPackage())
            .addAsManifestResource(new StringAsset("Dependencies: org.jboss.jboss-transaction-spi\n"), "MANIFEST.MF");
        // TODO: permissions for security manager
    }

    @Before
    public void before() throws Exception {
        container.start(CONTAINER);
        deployer.deploy(DEPLOYMENT_NAME);

        initCLI(CLI_INIT_TIMEOUT);

        orphanSafetyIntervalOriginal = readAttributeAsInt(ORPHAN_SAFETY_INTERVAL_ATTRIBUTE_NAME);
        recoveryPeriodOriginal = readAttributeAsInt(RECOVERY_PERIOD_ATTRIBUTE_NAME);
        recoveryBackoffPeriodOriginal = readAttributeAsInt(RECOVERY_BACKOFF_PERIOD_ATTRIBUTE_NAME);
        allowRecoverySuspensionOriginal = readAttributeAsBoolean(ALLOW_RECOVERY_SUSPENSION_ATTRIBUTE_NAME);
        // reset Xid records of the PersistentTestXAResource
        xidsPersister.writeToDisk(null);
    }

    @After
    public void after() throws Exception {
        if (!container.isStarted(CONTAINER)) {
            container.start(CONTAINER);
            assertState("running", CLI_INIT_TIMEOUT, ":read-attribute(name=server-state)");
        }

        writeAttribute(ORPHAN_SAFETY_INTERVAL_ATTRIBUTE_NAME, orphanSafetyIntervalOriginal);
        writeAttribute(RECOVERY_PERIOD_ATTRIBUTE_NAME, recoveryPeriodOriginal);
        writeAttribute(RECOVERY_BACKOFF_PERIOD_ATTRIBUTE_NAME, recoveryBackoffPeriodOriginal);
        writeAttribute(ALLOW_RECOVERY_SUSPENSION_ATTRIBUTE_NAME, allowRecoverySuspensionOriginal);
        removeSystemProperty(RECOVERY_INITIALIZATION_OFFSET_SYSTEM_PROPERTY_NAME);

        closeCLI();

        deployer.undeploy(DEPLOYMENT_NAME);
        container.stop(CONTAINER);
    }

    @Test
    public void testRecovery() throws Exception {
        // deactivate automatic recovery processing by offsetting start time of the periodic recovery
        // (the system property value is considered after JVM restart)
        writeSystemProperty(RECOVERY_INITIALIZATION_OFFSET_SYSTEM_PROPERTY_NAME, 3600);

        final URI baseUri = new URI("http://" + TestSuiteEnvironment.getHttpAddress() + ":" + TestSuiteEnvironment.getHttpPort()
                + "/" + DEPLOYMENT_NAME + "/");
        final URI txnCallUri = baseUri.resolve("transactionCallPrepareAndCrash");
        try {
            Utils.makeCall(txnCallUri, -1);
            Assert.fail("Expected the container was killed during transaction test processing");
        } catch (org.apache.http.conn.HttpHostConnectException callExpectedConnection) {
            // all good here, the container should be down now but we need to announce the state to arquillian
            container.kill(CONTAINER);
        }

        Collection<Xid> recoveredXids = xidsPersister.recoverFromDisk();
        Assert.assertEquals("XAResource should be left in prepared: " + recoveredXids, 1, recoveredXids.size());

        container.start(CONTAINER);
        assertState("running", CLI_INIT_TIMEOUT, ":read-attribute(name=server-state)");
        // at this time there should unfinished transaction in the object store
        writeAttribute(ORPHAN_SAFETY_INTERVAL_ATTRIBUTE_NAME, 1);
        writeAttribute(RECOVERY_PERIOD_ATTRIBUTE_NAME, 1);
        writeAttribute(RECOVERY_BACKOFF_PERIOD_ATTRIBUTE_NAME, 1);
        writeAttribute(ALLOW_RECOVERY_SUSPENSION_ATTRIBUTE_NAME, false);
        // reload to activate the recovery period attributes (requiring reload)
        cli.sendLine(":reload(start-mode=suspend)");
        assertState("running", CLI_INIT_TIMEOUT, ":read-attribute(name=server-state)");

        // let's recover and expecting to finish with it faster than 10 seconds which is the default backoff period and which defines default time for recovery
        long startTimeStamp = System.currentTimeMillis();
        cli.sendLine("/subsystem=transactions/log-store=log-store:process-recovery");
        int recoveryTimeProcessing = Long.valueOf(System.currentTimeMillis() - startTimeStamp).intValue();
        Assert.assertTrue("Recovery processing expected to be finished in shorter time than the default 10 seconds",
                recoveryTimeProcessing < TimeoutUtil.adjust(9_000) ); // 9 seconds
        // recovery should rolled-back the orphan prepared XAResource
        Assert.assertEquals("XA resource is expected to be recovered",0, xidsPersister.recoverFromDisk().size());
    }

    private ModelNode writeSystemProperty(String systemProperty, int value) {
        try {
            String readOperation = String.format("/system-property=%s:%s", systemProperty, ModelDescriptionConstants.READ_RESOURCE_OPERATION);
            cli.sendLine(readOperation,  true);
            CLIOpResult readResourceResult = cli.readAllAsOpResult();

            String writeOperation = String.format("/system-property=%s:add(value=%d)", systemProperty, value);
            if (ModelDescriptionConstants.SUCCESS.equals(readResourceResult.getResponseNode().get(ModelDescriptionConstants.OUTCOME).asString())) {
                // when system property exists then using 'write-attribute' instead of 'add' operation
                writeOperation = String.format("/system-property=%s:write-attribute(name=value, value=%d)", systemProperty, value);
            }
            cli.sendLine(writeOperation);

            ModelNode resultModelNode = cli.readAllAsOpResult().getResponseNode();
            assertSuccess(resultModelNode, writeOperation);
            return resultModelNode;
        } catch (IOException ioe) {
            throw new IllegalStateException("Cannot add system property " + systemProperty + " with value " + value, ioe);
        }
    }

    private ModelNode removeSystemProperty(String systemProperty) {
        try {
            String removeOperation = String.format("/system-property=%s:%s", systemProperty, ModelDescriptionConstants.REMOVE);
            cli.sendLine(removeOperation);
            ModelNode result = cli.readAllAsOpResult().getResponseNode();
            assertSuccess(result, removeOperation);
            return result;
        } catch (IOException ioe) {
            throw new IllegalStateException("Cannot remove system property " + systemProperty, ioe);
        }
    }

    private ModelNode writeAttribute(String attributeName, int value) {
        return writeAttribute(attributeName, Integer.toString(value));
    }

    private ModelNode writeAttribute(String attributeName, boolean value) {
        return writeAttribute(attributeName, Boolean.toString(value));
    }

    private ModelNode writeAttribute(String attributeName, String value) {
        try {
            String writeOperation = String.format("/subsystem=transactions:write-attribute(name=%s, value=%s)", attributeName, value);
            cli.sendLine(writeOperation);

            ModelNode resultModelNode = cli.readAllAsOpResult().getResponseNode();
            assertSuccess(resultModelNode, writeOperation);
            return resultModelNode;
        } catch (IOException ioe) {
            throw new IllegalStateException("Cannot write attribute " + attributeName + " with value " + value, ioe);
        }
    }

    private boolean readAttributeAsBoolean(String attributeName) {
        return readAttribute(attributeName).asBoolean();
    }

    private int readAttributeAsInt(String attributeName) {
        return readAttribute(attributeName).asInt();
    }

    private ModelNode readAttribute(String attributeName) {
        try {
            String readOperation = String.format("/subsystem=transactions:read-attribute(name=%s)", attributeName);
            cli.sendLine(readOperation);
            ModelNode result = cli.readAllAsOpResult().getResponseNode();
            assertSuccess(result, readOperation);
            return result.get(ModelDescriptionConstants.RESULT);
        } catch (Exception e) {
            throw new IllegalStateException("Cannot read attribute " + attributeName + " with CLI", e);
        }
    }

    private void assertSuccess(ModelNode result, String operation) {
        Assert.assertEquals("Expecting operation '" + operation + "' to succeed",
                ModelDescriptionConstants.SUCCESS, result.get(ModelDescriptionConstants.OUTCOME).asString());
    }
}
