/*
 * JBoss, Home of Professional Open Source.
 * Copyright (c) 2020, Red Hat, Inc., and individual contributors
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

import com.arjuna.ats.arjuna.recovery.RecoveryDriver;
import org.jboss.as.arquillian.container.ManagementClient;
import org.jboss.as.controller.client.helpers.ClientConstants;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.test.integration.management.ManagementOperations;
import org.jboss.as.test.integration.management.util.MgmtOperationException;
import org.jboss.as.test.shared.TimeoutUtil;
import org.jboss.dmr.ModelNode;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;

/**
 * A helper class which wraps remote execution of the transaction recovery.
 * For this call would work it's required that transaction recovery listener is enabled.<br/>
 * <code>/subsystem=transactions:write-attribute(name=recovery-listener, value=true)</code>
 */
public class RecoveryExecutor {
    private static final ModelNode ADDRESS_TRANSACTIONS
            = new ModelNode().add("subsystem", "transactions");
    private static final ModelNode ADDRESS_SOCKET_BINDING
            = new ModelNode().add(ClientConstants.SOCKET_BINDING_GROUP, "standard-sockets");
    static {
        ADDRESS_TRANSACTIONS.protect();
        ADDRESS_SOCKET_BINDING.protect();
    }

    private static final int DEFAULT_SOCKET_READ_SCAN_TIMEOUT_MS = 60 * 1000;

    private final ManagementClient managementClient;
    private final AtomicReference<RecoveryDriver> recoveryDriverReference = new AtomicReference<>();

    public RecoveryExecutor(ManagementClient managementClient) {
        this.managementClient = managementClient;
    }

    public boolean runTransactionRecovery() {
        return runTransactionRecovery(DEFAULT_SOCKET_READ_SCAN_TIMEOUT_MS);
    }

    public boolean runTransactionRecovery(int socketReadTimeout) {
        try {
            return getRecoveryDriver().synchronousVerboseScan(TimeoutUtil.adjust(socketReadTimeout), 5);
        } catch (Exception e) {
            throw new IllegalStateException("Error when triggering transaction recovery synchronous scan with RecoveryDriver "
                    + recoveryDriverReference.get() + ", based on the management client " + managementClient, e);
        }
    }

    private RecoveryDriver getRecoveryDriver() {
        if(recoveryDriverReference.get() != null) return recoveryDriverReference.get();

        try {
            String transactionSocketBinding = readAttribute(managementClient, ADDRESS_TRANSACTIONS, "socket-binding").asString();
            ModelNode addressSocketBinding = ADDRESS_SOCKET_BINDING.clone();
            addressSocketBinding.add(ClientConstants.SOCKET_BINDING, transactionSocketBinding);
            String host = readAttribute(managementClient, addressSocketBinding, "bound-address").asString();
            int port = readAttribute(managementClient, addressSocketBinding, "bound-port").asInt();
            recoveryDriverReference.compareAndSet(null, new RecoveryDriver(port, host));
            return recoveryDriverReference.get();
        } catch (MgmtOperationException | IOException e) {
            throw new IllegalStateException("Cannot obtain host:port for transaction recovery listener regarding" +
                    " the management client "  + managementClient);
        }
    }

    private ModelNode readAttribute(final ManagementClient managementClient, ModelNode address, String name) throws IOException, MgmtOperationException {
        ModelNode operation = new ModelNode();
        operation.get(OP_ADDR).set(address);
        operation.get(OP).set(ClientConstants.READ_ATTRIBUTE_OPERATION);
        operation.get(ModelDescriptionConstants.INCLUDE_DEFAULTS).set("true");
        operation.get(ModelDescriptionConstants.RESOLVE_EXPRESSIONS).set("true");
        operation.get(ClientConstants.NAME).set(name);
        return ManagementOperations.executeOperation(managementClient.getControllerClient(), operation);
    }
}
