/*
 * JBoss, Home of Professional Open Source.
 * Copyright (c) 2016, Red Hat, Inc., and individual contributors
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

package org.jboss.as.test.integration.transactions;

import org.jboss.logging.Logger;
import org.jboss.tm.XAResourceWrapper;

import javax.transaction.xa.XAException;
import javax.transaction.xa.XAResource;
import javax.transaction.xa.Xid;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * <p>
 *     {@link XAResource} class that could be used in various transaction test scenarios.
 *     If you need a persistent variant of the resource consider {@link PersistentTestXAResource}.
 * </p>
 * <p>
 * <b>WARN:</b> when this resource is used in the test then it's needed to put the {@link XAResourceWrapper}
 * to classpath. It can be done to adjust {@code MANIFIEST.MF} with transaction spi like
 * {@code .addAsManifestResource(new StringAsset("Dependencies: org.jboss.jboss-transaction-spi\n"), "MANIFEST.MF")}.
 * </p>
 */
public class TestXAResource implements XAResource, XAResourceWrapper {
    private static final Logger log = Logger.getLogger(TestXAResource.class);

    public enum TestAction {
        NONE,
        PREPARE_THROW_XAER_RMERR, PREPARE_THROW_XAER_RMFAIL, PREPARE_THROW_UNKNOWN_XA_EXCEPTION, PREPARE_CRASH_VM,
        COMMIT_THROW_XAER_RMERR, COMMIT_THROW_XAER_RMFAIL, COMMIT_THROW_UNKNOWN_XA_EXCEPTION, COMMIT_CRASH_VM,

    }

    // prepared xids are shared over all the TestXAResource instances in the JVM
    // this is an global in-VM storage as the recovery processing (XAResourceRecoveryHelper) creates a brand new XAResource
    // and this recovery purpose resource needs to access the list of the prepared Xids
    private static final Map<Xid,Xid> preparedXids = new ConcurrentHashMap<>();

    private final TransactionCheckerSingleton checker;
    private int transactionTimeout;

    protected TestAction testAction;


    public TestXAResource(TransactionCheckerSingleton checker) {
        this(TestAction.NONE, checker);
    }

    public TestXAResource() {
        this(TestAction.NONE);
    }

    public TestXAResource(TestAction testAction) {
        // the checker singleton can't be used to check processing as it's not injected as a bean
        this(testAction, new TransactionCheckerSingleton());
    }

    public TestXAResource(TestAction testAction, TransactionCheckerSingleton checker) {
        log.debugf("created %s with testAction %s and checker %s", this.getClass().getName(), testAction, checker);
        this.checker = checker;
        this.testAction = testAction;
    }

    @Override
    public int prepare(Xid xid) throws XAException {
        log.debugf("prepare xid: [%s], test action: %s", xid, testAction);
        checker.addPrepare();

        switch (testAction) {
            case PREPARE_THROW_XAER_RMERR:
                throw new XAException(XAException.XAER_RMERR);
            case PREPARE_THROW_XAER_RMFAIL:
                throw new XAException(XAException.XAER_RMFAIL);
            case PREPARE_THROW_UNKNOWN_XA_EXCEPTION:
                throw new XAException(null);
            case PREPARE_CRASH_VM:
                Runtime.getRuntime().halt(0);
            case NONE:
            default:
                preparedXids.put(xid, xid);
                return XAResource.XA_OK;
        }
    }

    @Override
    public void commit(Xid xid, boolean onePhase) throws XAException {
        log.debugf("commit xid:[%s], %s one phase, test action: %s", xid, onePhase ? "with" : "without", testAction);
        checker.addCommit();

        switch (testAction) {
            case COMMIT_THROW_XAER_RMERR:
                throw new XAException(XAException.XAER_RMERR);
            case COMMIT_THROW_XAER_RMFAIL:
                throw new XAException(XAException.XAER_RMFAIL);
            case COMMIT_THROW_UNKNOWN_XA_EXCEPTION:
                throw new XAException(null);
            case COMMIT_CRASH_VM:
                Runtime.getRuntime().halt(0);
            case NONE:
            default:
                preparedXids.remove(xid);
        }
    }

    @Override
    public void rollback(Xid xid) throws XAException {
        log.debugf("rollback xid: [%s]", xid);
        checker.addRollback();
        preparedXids.remove(xid);
    }

    @Override
    public void end(Xid xid, int flags) throws XAException {
        log.debugf("end xid:[%s], flag: %s", xid, flags);
    }

    @Override
    public void forget(Xid xid) throws XAException {
        log.debugf("forget xid:[%s]", xid);
        preparedXids.remove(xid);
    }

    @Override
    public int getTransactionTimeout() throws XAException {
        log.debugf("getTransactionTimeout: returning timeout: %s", transactionTimeout);
        return transactionTimeout;
    }

    @Override
    public boolean isSameRM(XAResource xares) throws XAException {
        log.debugf("isSameRM returning false to xares: %s", xares);
        return false;
    }

    @Override
    public Xid[] recover(int flag) throws XAException {
        log.debugf("recover with flags: %s", flag);
        return preparedXids.values().toArray(new Xid[0]);
    }

    @Override
    public boolean setTransactionTimeout(int seconds) throws XAException {
        log.debugf("setTransactionTimeout: setting timeout: %s", seconds);
        this.transactionTimeout = seconds;
        return true;
    }

    @Override
    public void start(Xid xid, int flags) throws XAException {
        log.debugf("start xid: [%s], flags: %s", xid, flags);
    }

    /**
     * Returns modifiable list of the in-VM prepared Xids.
     *
     * @return list of prepared Xids
     */
    static Map<Xid,Xid> getPreparedXids() {
        return preparedXids;
    }

    // implementation of the org.jboss.tm.XAResourceWrapper
    @Override
    public XAResource getResource() {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public String getProductName() {
        return "WildFly Testsuite";
    }

    @Override
    public String getProductVersion() {
        return "WildFly";
    }

    @Override
    public String getJndiName() {
        return TestXAResource.class.getSimpleName();
    }
}
