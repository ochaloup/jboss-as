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

import org.jboss.as.test.integration.transactions.PersistentTestXAResource;
import org.jboss.as.test.integration.transactions.TestXAResource;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.transaction.TransactionManager;
import javax.transaction.Transactional;
import javax.ws.rs.GET;
import javax.ws.rs.Path;

@ApplicationScoped
@Path("/")
public class TransactionRecoveryEndPoint {
    @Inject
    private TransactionManager tm;

    @GET
    @Path("transactionCallPrepareAndCrash")
    @Transactional
    public String transactionCallPrepareAndCrash() throws Exception {
        tm.getTransaction().enlistResource( // on 2PC prepares the resource and then crash, recovery should go with rollback
                new PersistentTestXAResource(PersistentTestXAResource.PersistentTestAction.AFTER_PREPARE_CRASH_JVM));
        tm.getTransaction().enlistResource(new TestXAResource()); // need second resource to process with 2PC

        return "never returned value";
    }
}
