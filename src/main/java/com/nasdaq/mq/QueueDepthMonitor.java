/*
 * blog/javaclue/ibmmq/QueueDepthMonitor.java
 * 
 * Copyright (C) 2009 JackW
 * 
 * This program is free software: you can redistribute it and/or modify it under the terms of the
 * GNU Lesser General Public License as published by the Free Software Foundation, either version 3
 * of the License, or (at your option) any later version.
 * 
 * This library is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
 * even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License along with this library.
 * If not, see <http://www.gnu.org/licenses/>.
 */
package com.nasdaq.mq;

import java.io.IOException;

import org.apache.log4j.Logger;

import com.ibm.mq.MQException;
import com.ibm.mq.pcf.CMQC;
import com.ibm.mq.pcf.CMQCFC;
import com.ibm.mq.pcf.PCFException;
import com.ibm.mq.pcf.PCFMessage;
import com.ibm.mq.pcf.PCFMessageAgent;

/**
 * Simple queue depth monitor program that uses PCFAgent to generate and parse a
 * PCF query.
 */
public class QueueDepthMonitor implements Runnable {

    protected static Logger logger = Logger.getLogger(QueueDepthMonitor.class);
    protected static boolean isDebugEnabled = logger.isDebugEnabled();

    final String qmgrName;
    final String host;
    final int port;
    final String channel;
    final String queueName;
    final int alertDepth;

    final static int Polling_Freq = 30 * 1000; // 30 seconds

    QueueDepthMonitor(String name, String host, String port, String channel, String queueName,
            int alertDepth) {
        this.qmgrName = name;
        this.host = host;
        this.channel = channel;
        this.port = Integer.parseInt(port);
        this.queueName = queueName;
        this.alertDepth = alertDepth;
    }

    public void run() {
        if (isDebugEnabled) {
            logger.debug("Starting Queue Depth monitor for " + queueName + "...");
        }
        while (true) {
            checkDepth();
            try {
                Thread.sleep(Polling_Freq); // sleep for 30 seconds
            } catch (InterruptedException e) {
                logger.info("The monitor has been interrupted, exit...");
                break;
            }
        }
    }

    private void checkDepth() {
        PCFMessageAgent agent = null;
        int[] attrs = {CMQC.MQCA_Q_NAME, CMQC.MQIA_CURRENT_Q_DEPTH};
        PCFMessage request = new PCFMessage(CMQCFC.MQCMD_INQUIRE_Q);
        request.addParameter(CMQC.MQCA_Q_NAME, queueName);
        request.addParameter(CMQC.MQIA_Q_TYPE, CMQC.MQQT_LOCAL);
        request.addParameter(CMQCFC.MQIACF_Q_ATTRS, attrs);
        PCFMessage[] responses;

        if (isDebugEnabled) {
            logger.debug("Connecting to " + qmgrName + " at " + host + ":" + port + " over " + channel);
        }
        try {
            // Connect a PCFAgent to the queue manager
            agent = new PCFMessageAgent(host, port, channel);
            // Use the agent to send the request
            responses = agent.send(request);
            // retrieving queue depth
            for (int i = 0; i < responses.length; i++) {
                String name = responses[i].getStringParameterValue(CMQC.MQCA_Q_NAME);
                int depth = responses[i].getIntParameterValue(CMQC.MQIA_CURRENT_Q_DEPTH);
                if (isDebugEnabled && name != null) {
                    logger.debug("Queue " + name + " Depth " + depth);
                }
                if (name != null && queueName.equals(name.trim())) { // just for safety
                    if (depth > alertDepth) {
                        logger.info(qmgrName + "/" + queueName + " depth = " + depth
                                + ", exceeded alert threshold: " + alertDepth);
                        // XXX: add your code here to send out alert
                    }
                }
            }
        } catch (PCFException pcfe) {
            logger.error("PCFException caught", pcfe);
            PCFMessage[] msgs = (PCFMessage[]) pcfe.exceptionSource;
            for (int i = 0; i < msgs.length; i++) {
                logger.error(msgs[i]);
            }
        } catch (MQException mqe) {
            logger.error("MQException caught", mqe);
        } catch (IOException ioe) {
            logger.error("IOException caught", ioe);
        } finally {
            // Disconnect
            if (agent != null) {
                try {
                    agent.disconnect();
                } catch (Exception e) {
                    logger.error("Exception caught during disconnect", e);
                }
            } else {
                logger.warn("unable to disconnect, agent is null.");
            }
        }
    }

    public static void main(String[] args) {
        String qmgrName = "QMGR";
        String host = "localhost";
        String port = "1450";
        String channel = "SYSTEM.DEF.SVRCONN";
        String queueName = "TEST_QUEUE";

        QueueDepthMonitor monitor = new QueueDepthMonitor(qmgrName, host, port, channel, queueName, 10);
        new Thread(monitor).start();
    }
}
