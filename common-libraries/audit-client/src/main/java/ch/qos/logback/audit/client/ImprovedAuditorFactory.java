package ch.qos.logback.audit.client;

import ch.qos.logback.audit.Application;
import ch.qos.logback.audit.AuditException;
import ch.qos.logback.audit.client.joran.JoranConfigurator;
import ch.qos.logback.core.joran.spi.JoranException;
import ch.qos.logback.core.status.Status;
import ch.qos.logback.core.status.StatusManager;
import ch.qos.logback.core.status.StatusUtil;
import ch.qos.logback.core.util.Loader;
import ch.qos.logback.core.util.OptionHelper;
import ch.qos.logback.core.util.StatusPrinter;
import org.springframework.util.StringUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.List;

/**
 * Logback: the reliable, generic, fast and flexible logging framework.
 * Copyright (C) 2006-2011, QOS.ch. All rights reserved.
 * <p>
 * This program and the accompanying materials are dual-licensed under
 * either the terms of the Eclipse Public License v1.0 as published by
 * the Eclipse Foundation
 * <p>
 * or (per the licensee's choosing)
 * <p>
 * under the terms of the GNU Lesser General Public License version 2.1
 * as published by the Free Software Foundation.
 *
 * NOTE:
 * This class is a modified version of {@link ch.qos.logback.audit.client.AuditorFactory}
 * to improve configuration support for host and port. The client services
 * can use {@link ImprovedAuditorFactory#setApplicationNameWithHostAndPort(String, String, int)}
 * to initialize the audit service client without providing an XML configuration file.
 */

public class ImprovedAuditorFactory {

    static final public String AUTOCONFIG_FILE_PROPERTY = "logback.audit.autoconfig.file";
    static final public String NULL_CLIENT_APPLICATON_URL = "http://audit.qos.ch/codes.html#nullClientApp";
    static final public String NULL_AUDIT_APPENDER_URL = "http://audit.qos.ch/codes.html#nullAuditAppender";
    static final String DEFAULT_AUDITOR_NAME = "default";
    static final String AUTOCONFIG_FILE = "logback-audit.xml";
    static final String TEST_AUTOCONFIG_FILE = "logback-audit-test.xml";
    static String host = null;
    static int port = 0;
    static Auditor defaultAuditor;

    static Application clientApplication;

    static void checkSanity(Auditor auditor) throws AuditException {
        StatusManager sm = auditor.getStatusManager();

        if (getHighestLevel(0, sm) > Status.INFO) {
            StatusPrinter.print(sm);
        }

        if (auditor.getClientApplication() == null) {
            throw new AuditException("Client application has not been set");
        }

        if (auditor.getAuditAppender() == null) {
            throw new AuditException("No audit appender. Please see "
                    + NULL_AUDIT_APPENDER_URL);
        }
    }

    public static int getHighestLevel(long threshold, StatusManager sm ) {
        List filteredList = StatusUtil.filterStatusListByTimeThreshold(sm.getCopyOfStatusList(), threshold);
        int maxLevel = 0;
        Iterator i$ = filteredList.iterator();

        while(i$.hasNext()) {
            Status s = (Status)i$.next();
            if(s.getLevel() > maxLevel) {
                maxLevel = s.getLevel();
            }
        }

        return maxLevel;
    }

    public static void setApplicationName(String name) throws AuditException {
        if (clientApplication != null && clientApplication.getName().equals(name)) {
            // don't configure again
            return;
        }
        if (clientApplication != null && !clientApplication.getName().equals(name)) {
            throw new IllegalStateException("Application name "
                    + clientApplication.getName() + " once set cannot be renamed.");
        }

        if (OptionHelper.isEmpty(name)) {
            throw new IllegalArgumentException(
                    "Application name cannot be null or empty");
        } else {

            // logger.info("Naming client application as [" + name + "]");
        }

        try {
            InetAddress address = InetAddress.getLocalHost();
            String fqdn = address.getCanonicalHostName();
            // logger("Client application host is ["+fqdn+"].");
            Application aplication = new Application(name, fqdn);
            // all is nice and dandy
            clientApplication = aplication;
        } catch (UnknownHostException e) {
            throw new IllegalStateException(
                    "Failed to determine the hostname for this host", e);
        }

        // defaultAuditor.close();
        defaultAuditor = new Auditor();
        defaultAuditor.setClientApplication(clientApplication);
        defaultAuditor.setName(DEFAULT_AUDITOR_NAME);
        autoConfig(defaultAuditor);
        checkSanity(defaultAuditor);
        AuditorFactory.defaultAuditor = defaultAuditor;
    }

    public static void setApplicationNameWithHostAndPort(String name, String host, int port) throws AuditException {
        ImprovedAuditorFactory.host = host;
        ImprovedAuditorFactory.port = port;
        setApplicationName(name);
    }

    /**
     * Get the default auditor. If it has not been previously configured, this
     * method will also configure the default auditor. In case of problems, this
     * method may throw an exception.
     *
     * @return
     * @throws AuditException
     */
    public static Auditor getAuditor() {
        return defaultAuditor;
    }

    public static void autoConfig(Auditor auditor) throws AuditException {
        ClassLoader tccl = Loader.getTCL();
        autoConfig(auditor, tccl);
    }

    public static void configureByResource(Auditor auditor, URL url)
            throws AuditException {
        if (url == null) {
            throw new IllegalArgumentException("URL argument cannot be null");
        }
        JoranConfigurator configurator = new JoranConfigurator();
        configurator.setContext(auditor);

        try {
            configurator.doConfigure(url);
        } catch (JoranException e) {
            throw new AuditException("Configuration failure in " + url, e);
        }

    }

    public static void configureByInputStream(Auditor auditor, InputStream inputStream)
            throws AuditException {
        if (inputStream == null) {
            throw new IllegalArgumentException("URL argument cannot be null");
        }
        JoranConfigurator configurator = new JoranConfigurator();
        configurator.setContext(auditor);

        try {
            configurator.doConfigure(inputStream);
        } catch (JoranException e) {
            throw new AuditException("Configuration failure in " + inputStream, e);
        }

    }

    public static void autoConfig(Auditor auditor, ClassLoader classLoader)
            throws AuditException {

        String autoConfigFileByProperty = System
                .getProperty(AUTOCONFIG_FILE_PROPERTY);
        String pathPrefix = clientApplication.getName() + "/";
        URL url = null;
        InputStream inputStream = null;
        if (StringUtils.hasText(host) && port > 0) {
            final byte[] bytes = new StringBuilder()
                    .append("<auditor><appender name=\"server\" class=\"ch.qos.logback.audit.client.net.SocketAuditAppender\"><remoteHost>")
                    .append(host)
                    .append("</remoteHost><port>")
                    .append(port)
                    .append("</port></appender></auditor>")
                    .toString()
                    .getBytes(StandardCharsets.UTF_8);
            inputStream = new ByteArrayInputStream(bytes);
        } else if (autoConfigFileByProperty != null) {
            url = Loader.getResource(pathPrefix + autoConfigFileByProperty,
                    classLoader);

        } else {
            url = Loader.getResource(pathPrefix + TEST_AUTOCONFIG_FILE, classLoader);
            if (url == null) {
                url = Loader.getResource(pathPrefix + AUTOCONFIG_FILE, classLoader);
            }
        }
        if (inputStream != null) {
            configureByInputStream(auditor, inputStream);
            try {
                inputStream.close();
            } catch (IOException e) {
                throw new AuditException("Failed loading configuration file from input stream: " + e.getMessage(), e);
            }
        } else if (url != null) {
            configureByResource(auditor, url);
        } else {
            String errMsg;
            if (autoConfigFileByProperty != null) {
                errMsg = "Failed to find configuration file [" + pathPrefix
                        + autoConfigFileByProperty + "].";
            } else {
                errMsg = "Failed to find logback-audit configuration files  ["
                        + pathPrefix + TEST_AUTOCONFIG_FILE + "] or [" + pathPrefix
                        + AUTOCONFIG_FILE + "].";
            }
            throw new AuditException(errMsg);
        }
    }

    static public void reset() {
        clientApplication = null;
        if (defaultAuditor != null) {
            defaultAuditor.shutdown();
        }
        defaultAuditor = null;
    }

}
