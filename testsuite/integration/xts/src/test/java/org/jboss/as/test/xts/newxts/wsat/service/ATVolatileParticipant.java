/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2013, Red Hat, Inc., and individual contributors
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

package org.jboss.as.test.xts.newxts.wsat.service;

import com.arjuna.wst.*;

import org.jboss.as.test.xts.newxts.util.EventLog;
import org.jboss.as.test.xts.newxts.util.EventLogEvent;
import org.jboss.as.test.xts.newxts.util.ServiceCommand;
import org.jboss.logging.Logger;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * A WS-T Atomic Transaction volatile participant.
 */
public class ATVolatileParticipant implements Volatile2PCParticipant, Serializable {
    private static final Logger log = Logger.getLogger(ATVolatileParticipant.class);
    private static final long serialVersionUID = 1L;
    
    // Is participant already enlisted to transaction?
    private static Map<String, ATVolatileParticipant> activeParticipants = new HashMap<String, ATVolatileParticipant>();
    String transactionId;

    // Service command which define behaving of the participant
    private ServiceCommand[] serviceCommands;
    // Where to log participant activity
    private EventLog eventLog;

    /**
     * Creates a new participant for this transaction. Participants and transaction instances have a one-to-one mapping.
     */
    public ATVolatileParticipant(ServiceCommand[] serviceCommands, EventLog eventLog, String transactionId) {
        this.serviceCommands = serviceCommands;
        this.eventLog = eventLog;
        
        if(ATVolatileParticipant.isEnlisted(transactionId)) {
            throw new RuntimeException(this.getClass().getName() + " can't be enlisted to transaction " + transactionId + " because it already is enlisted.");
        } else {
          this.transactionId = transactionId;
          activeParticipants.put(transactionId, this);
        }
    }

   
    /**
     * Invokes the volatile prepare step of the business logic.
     * 
     * @return in dependence of command passed to constructor @see{ServiceCommand}
     * @throws com.arjuna.wst.WrongStateException
     * @throws com.arjuna.wst.SystemException
     */
    @Override
    // TODO: added option for System Exception would be thrown?
    public Vote prepare() throws WrongStateException, SystemException {
        eventLog.addEvent(EventLogEvent.BEFORE_PREPARE);
        log.info("[AT SERVICE] Volatile participant prepare() - logged: " + EventLogEvent.BEFORE_PREPARE);

        if(ServiceCommand.isPresent(ServiceCommand.VOTE_ROLLBACK_PRE_PREPARE, serviceCommands)) {
            log.info("[AT SERVICE] Volatile participant prepare(): " + Aborted.class.getSimpleName());
            return new Aborted();
        } else if(ServiceCommand.isPresent(ServiceCommand.VOTE_READONLY_VOLATILE, serviceCommands)) {
            log.info("[AT SERVICE] Volatile participant prepare(): " + ReadOnly.class.getSimpleName());
            return new ReadOnly();            
        } else {
            log.info("[AT SERVICE] Volatile participant prepare(): "  + Prepared.class.getSimpleName());
            return new Prepared();
        }
    }

    /**
     * Invokes the volatile commit step of the business logic.
     * All participants voted 'prepared', so coordinator tells the volatile participant that commit has been done.
     * 
     * @throws com.arjuna.wst.WrongStateException
     * @throws com.arjuna.wst.SystemException
     */
    @Override
    public void commit() throws WrongStateException, SystemException {
        eventLog.addEvent(EventLogEvent.VOLATILE_COMMIT);
        log.info("[AT SERVICE] Volatile participant commit() - logged: " + EventLogEvent.VOLATILE_COMMIT);
    }

    /**
     * Invokes the volatile rollback operation on the business logic.
     * One or more participants voted 'aborted' or a failure occurred, so coordinator tells the volatile participant that rollback has been done.
     * 
     * @throws com.arjuna.wst.WrongStateException
     * @throws com.arjuna.wst.SystemException
     */
    @Override
    public void rollback() throws WrongStateException, SystemException {
        eventLog.addEvent(EventLogEvent.VOLATILE_ROLLBACK);
        log.info("[AT SERVICE] Volatile participant rollback() - logged: " + EventLogEvent.VOLATILE_ROLLBACK);
    }

    @SuppressWarnings("deprecation")
    @Override
    public void unknown() throws SystemException {
        eventLog.addEvent(EventLogEvent.UNKNOWN);
        log.info("[AT SERVICE] Volatile participant unknown() - logged: " + EventLogEvent.UNKNOWN);
    }

    /**
     * This should not happen for volatile participant.
     */
    @Override    
    public void error() throws SystemException {
        eventLog.addEvent(EventLogEvent.ERROR);
        log.info("[AT SERVICE] Volatile participant error() - logged: " + EventLogEvent.ERROR);
    }
    
    
    // --- private helper methods ---
    public static boolean isEnlisted(String transactionId) {
        return activeParticipants.containsKey(transactionId);
    }
}
