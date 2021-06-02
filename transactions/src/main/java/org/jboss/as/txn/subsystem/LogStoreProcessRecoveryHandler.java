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

package org.jboss.as.txn.subsystem;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.txn.service.ArjunaRecoveryManagerService;
import org.jboss.as.txn.service.TxnServices;
import org.jboss.dmr.ModelNode;

/**
 * Handler for explicitly running recovery scan.
 */
public class LogStoreProcessRecoveryHandler implements OperationStepHandler {
    static final LogStoreProcessRecoveryHandler INSTANCE = new LogStoreProcessRecoveryHandler();

    public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
        ArjunaRecoveryManagerService arjunaWildFlyRecoveryManagerService = (ArjunaRecoveryManagerService) context.getServiceRegistry(false)
                .getRequiredService(TxnServices.JBOSS_TXN_ARJUNA_RECOVERY_MANAGER).getValue();
        arjunaWildFlyRecoveryManagerService.scan();
    }

}
