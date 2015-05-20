package org.jahia.modules.ios.notifications;

import com.relayrides.pushy.apns.*;
import com.relayrides.pushy.apns.util.*;
import org.apache.commons.lang.StringUtils;
import org.jahia.registries.ServicesRegistry;
import org.jahia.services.content.*;
import org.jahia.services.content.decorator.JCRSiteNode;
import org.jahia.services.usermanager.JahiaGroup;
import org.jahia.services.usermanager.JahiaGroupManagerService;
import org.jahia.services.usermanager.JahiaUser;
import org.jahia.services.usermanager.JahiaUserManagerService;
import org.jahia.utils.LanguageCodeConverters;
import org.jahia.utils.Patterns;
import org.jahia.utils.i18n.Messages;
import org.slf4j.Logger;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;
import javax.jcr.Value;
import javax.jcr.query.Query;
import javax.jcr.query.QueryResult;
import javax.net.ssl.SSLHandshakeException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.security.Principal;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by loom on 06.03.15.
 */
public class ApplePushNotificationService implements InitializingBean, DisposableBean {

    public static final String IOS_DEVICE_TOKENS_USER_NODE = "iOSDeviceTokens";
    public static final String IOS_DEVICE_TOKENS_USER_PROPERTY = "j:deviceTokens";
    private transient static Logger logger = org.slf4j.LoggerFactory.getLogger(ApplePushNotificationService.class);
    PushManager<SimpleApnsPushNotification> pushManager;

    private String sandBoxCertificatePath;
    private String sandBoxCertificatePassword;
    private String productionCertificatePath;
    private String productionCertificatePassword;
    private boolean sandboxAPNUsed = true;
    private JahiaUserManagerService jahiaUserManagerService;
    private JahiaGroupManagerService jahiaGroupManagerService;
    private String macroRegexp = "##([a-zA-Z0-9_]+)(\\(([^\"#]*)(\\s*,\\s*[^\"#]*)*\\))?##";
    private Pattern macroPattern = null;

    public void setSandBoxCertificatePath(String sandBoxCertificatePath) {
        this.sandBoxCertificatePath = sandBoxCertificatePath;
    }

    public void setSandBoxCertificatePassword(String sandBoxCertificatePassword) {
        this.sandBoxCertificatePassword = sandBoxCertificatePassword;
    }

    public void setProductionCertificatePath(String productionCertificatePath) {
        this.productionCertificatePath = productionCertificatePath;
    }

    public void setProductionCertificatePassword(String productionCertificatePassword) {
        this.productionCertificatePassword = productionCertificatePassword;
    }

    public void setSandboxAPNUsed(boolean sandboxAPNUsed) {
        this.sandboxAPNUsed = sandboxAPNUsed;
    }

    public void setJahiaUserManagerService(JahiaUserManagerService jahiaUserManagerService) {
        this.jahiaUserManagerService = jahiaUserManagerService;
    }

    public void setJahiaGroupManagerService(JahiaGroupManagerService jahiaGroupManagerService) {
        this.jahiaGroupManagerService = jahiaGroupManagerService;
    }

    public void setMacroRegexp(String macroRegexp) {
        this.macroRegexp = macroRegexp;
    }

    @Override
    public void afterPropertiesSet() throws Exception {

        if (macroRegexp != null) {
            macroPattern = Pattern.compile(macroRegexp);
        }

        try {
            InetAddress.getByName("www.apple.com");
        } catch (UnknownHostException uhe) {
            logger.warn("Network doesn't seem reacheable, not starting push manager");
            return;
        }

        if (sandboxAPNUsed) {
            logger.info("Using sandbox Apple Push Notification service");
            InputStream certInputStream = this.getClass().getClassLoader().getResourceAsStream(sandBoxCertificatePath);
            pushManager =
                    new PushManager<SimpleApnsPushNotification>(
                            ApnsEnvironment.getSandboxEnvironment(),
                            SSLContextUtil.createDefaultSSLContext(certInputStream, sandBoxCertificatePassword),
                            null, // Optional: custom event loop group
                            null, // Optional: custom ExecutorService for calling listeners
                            null, // Optional: custom BlockingQueue implementation
                            new PushManagerConfiguration(),
                            "JahiaSandboxPushManager");
        } else {
            logger.info("Using production Apple Push Notification service");
            InputStream certInputStream = this.getClass().getClassLoader().getResourceAsStream(productionCertificatePath);
            pushManager =
                    new PushManager<SimpleApnsPushNotification>(
                            ApnsEnvironment.getProductionEnvironment(),
                            SSLContextUtil.createDefaultSSLContext(certInputStream, productionCertificatePassword),
                            null, // Optional: custom event loop group
                            null, // Optional: custom ExecutorService for calling listeners
                            null, // Optional: custom BlockingQueue implementation
                            new PushManagerConfiguration(),
                            "JahiaProductionPushManager");

        }

        pushManager.registerRejectedNotificationListener(new MyRejectedNotificationListener());
        pushManager.registerFailedConnectionListener(new MyFailedConnectionListener());
        pushManager.registerExpiredTokenListener(new MyExpiredTokenListener());
        pushManager.start();
        pushManager.requestExpiredTokens();

    }

    public boolean isPushManagerAvailable(String message) {
        if (pushManager == null) {
            logger.warn("Push manager not available: " + message);
            return false;
        }
        return true;
    }

    public String interpolateResourceBundleMacro(JCRNodeWrapper node, String input, String languageCode) {
        String output = input;
        if (input.contains("##resourceBundle(")) {
            JCRSessionWrapper session = null;
            try {
                session = node.getSession();
            } catch (RepositoryException e) {
                logger.warn("Error retrieving session for node {}. Cause: {}", node.getPath(), e);
            }
            Matcher macroMatcher = macroPattern.matcher(input);
            while (macroMatcher.find()) {
                String macroName = macroMatcher.group(1);
                String macroRBKey = macroMatcher.group(3);
                String[] params = Patterns.COMMA.split(macroRBKey);
                Locale locale = LanguageCodeConverters.languageCodeToLocale(languageCode);
                String macroResult = null;
                try {
                    JCRSiteNode site = node.getResolveSite();

                    try {
                        macroResult = Messages.get(params.length > 1 ? params[1] : null, ServicesRegistry.getInstance().getJahiaTemplateManagerService().getTemplatePackageById(
                                site.getTemplatePackageName()), params[0], locale);
                    } catch (Exception e) {
                        // ignore
                    }
                } catch (RepositoryException e) {
                    logger.warn("Unable to resolve the site for node {}. Cause: {}", node.getPath(),
                            e.getMessage());
                }

                if (macroResult != null) {
                    output = StringUtils.replace(output, macroMatcher.group(), macroResult);
                }
            }
            return output;
        } else {
            return input;
        }
    }

    public void sendNotification(String deviceToken, JCRNodeWrapper node, String category, String alertTitle, String alertBody, Map<String, Object> customKeys) {
        if (!isPushManagerAvailable("not sending notification")) {
            return;
        }
        try {
            final byte[] token = TokenUtil.tokenStringToByteArray(deviceToken);

            logger.info("Preparing " + category + " notification for device token " + deviceToken + "...");

            final ApnsPayloadBuilder payloadBuilder = buildPayload(node, category, alertTitle, alertBody, customKeys);

            final String payload = payloadBuilder.buildWithDefaultMaximumLength();

            pushManager.getQueue().put(new SimpleApnsPushNotification(token, payload));
        } catch (InterruptedException ie) {
            ie.printStackTrace();
        } catch (MalformedTokenStringException e) {
            e.printStackTrace();
        } catch (RepositoryException e) {
            e.printStackTrace();
        }
    }

    private ApnsPayloadBuilder buildPayload(JCRNodeWrapper node, String category, String alertTitle, String alertBody, Map<String, Object> customKeys) throws RepositoryException {
        final ApnsPayloadBuilder payloadBuilder = new ApnsPayloadBuilder();

        payloadBuilder.setCategoryName(category);
        payloadBuilder.setAlertTitle(alertTitle);
        payloadBuilder.setAlertBody(alertBody);
        payloadBuilder.setSoundFileName("ring-ring.aiff");
        payloadBuilder.addCustomProperty("nodeIdentifier", node.getIdentifier());
        payloadBuilder.addCustomProperty("nodePath", node.getPath());
        if (customKeys != null) {
            for (Map.Entry<String, Object> customKeyEntry : customKeys.entrySet()) {
                payloadBuilder.addCustomProperty(customKeyEntry.getKey(), customKeyEntry.getValue());
            }
        }
        return payloadBuilder;
    }

    public void sendNotificationToTaskCandidates(JCRNodeWrapper node, String category, String alertTitle, String alertBody, Map<String, Object> customKeys) {
        try {
            if (!node.isNodeType("jnt:task")) {
                logger.error("Expected jnt:task node type but got " + node.getPrimaryNodeType() + ", will not send notification");
                return;
            }
            Set<String> targetDeviceTokens = new HashSet<String>();
            Map<String, String> deviceTokensAndLanguages = new HashMap<String, String>();
            for (JCRValueWrapper valueWrapper : node.getProperty("candidates").getValues()) {
                String candidate = valueWrapper.getString();
                if (candidate.startsWith("u:")) {
                    JahiaUser candidateUser = jahiaUserManagerService.lookupUser(candidate.substring("u:".length()));
                    if (candidateUser != null) {
                        String preferredLanguage = getUserPreferredLanguage(node, candidateUser);
                        Set<String> candidateUserDeviceTokens = getUserDeviceTokens(candidateUser);
                        if (candidateUserDeviceTokens != null && candidateUserDeviceTokens.size() > 0) {
                            targetDeviceTokens.addAll(candidateUserDeviceTokens);
                            for (String candidateUserDeviceToken : candidateUserDeviceTokens) {
                                deviceTokensAndLanguages.put(candidateUserDeviceToken, preferredLanguage);
                            }
                        }
                    }
                } else if (candidate.startsWith("g:")) {
                    JahiaGroup candidateGroup = jahiaGroupManagerService.lookupGroup(candidate.substring("g:".length()));
                    if (candidateGroup != null) {
                        Set<Principal> groupMembers = candidateGroup.getRecursiveUserMembers();
                        for (Principal groupMember : groupMembers) {
                            if (groupMember instanceof JahiaUser) {
                                String preferredLanguage = getUserPreferredLanguage(node, (JahiaUser) groupMember);
                                Set<String> candidateUserDeviceTokens = getUserDeviceTokens((JahiaUser) groupMember);
                                if (candidateUserDeviceTokens != null && candidateUserDeviceTokens.size() > 0) {
                                    targetDeviceTokens.addAll(candidateUserDeviceTokens);
                                    for (String candidateUserDeviceToken : candidateUserDeviceTokens) {
                                        deviceTokensAndLanguages.put(candidateUserDeviceToken, preferredLanguage);
                                    }
                                }
                            }
                        }
                    }
                }
            }
            // we now have all the device tokens, let's send the notifications
            for (String targetDeviceToken : targetDeviceTokens) {
                String deviceTokenLanguage = deviceTokensAndLanguages.get(targetDeviceToken);
                alertBody = interpolateResourceBundleMacro(node, alertBody, deviceTokenLanguage);
                sendNotification(targetDeviceToken, node, category, alertTitle, alertBody, customKeys);
            }

        } catch (RepositoryException e) {
            logger.error("Error sending notification to all task candidates");
        }
    }

    private String getUserPreferredLanguage(JCRNodeWrapper node, JahiaUser candidateUser) throws RepositoryException {
        String preferredLanguage = candidateUser.getProperty("preferredLanguage");
        if (preferredLanguage == null) {
            preferredLanguage = node.getResolveSite().getDefaultLanguage();
        }
        if (preferredLanguage == null) {
            preferredLanguage = "en";
        }
        return preferredLanguage;
    }

    public void sendNotificationToAll(JCRNodeWrapper node, String category, String alertTitle, String alertBody, Map<String, Object> customKeys) {
        if (!isPushManagerAvailable("not sending notifications to all devices")) {
            return;
        }
        try {
            final Set<String> deviceTokens = new LinkedHashSet<String>();
            JCRTemplate.getInstance().doExecuteWithSystemSession(new JCRCallback<Object>() {
                @Override
                public Object doInJCR(JCRSessionWrapper jcrSessionWrapper) throws RepositoryException {
                    Query usersWithDeviceTokensQuery = jcrSessionWrapper.getWorkspace().getQueryManager().createQuery("SELECT * FROM [jnt:iOSDeviceTokens] as deviceTokens WHERE deviceTokens.[" + IOS_DEVICE_TOKENS_USER_PROPERTY + "] IS NOT NULL", Query.JCR_SQL2);
                    QueryResult queryResult = usersWithDeviceTokensQuery.execute();

                    NodeIterator usersWithDeviceTokensIterator = queryResult.getNodes();
                    while (usersWithDeviceTokensIterator.hasNext()) {
                        Node userWithDeviceTokens = usersWithDeviceTokensIterator.nextNode();
                        if (userWithDeviceTokens.hasProperty(IOS_DEVICE_TOKENS_USER_PROPERTY)) {
                            Value[] iosDeviceTokensValues = userWithDeviceTokens.getProperty(IOS_DEVICE_TOKENS_USER_PROPERTY).getValues();
                            for (Value iosDeviceTokensValue : iosDeviceTokensValues) {
                                deviceTokens.add(iosDeviceTokensValue.getString());
                            }
                        }
                    }
                    return null;
                }
            });
            for (String deviceToken : deviceTokens) {

                logger.info("Preparing " + category + " notification for device token " + deviceToken + "...");
                final byte[] token = TokenUtil.tokenStringToByteArray(deviceToken);

                final ApnsPayloadBuilder payloadBuilder = buildPayload(node, category, alertTitle, alertBody, customKeys);

                final String payload = payloadBuilder.buildWithDefaultMaximumLength();

                pushManager.getQueue().put(new SimpleApnsPushNotification(token, payload));
            }
        } catch (InterruptedException ie) {
            ie.printStackTrace();
        } catch (MalformedTokenStringException e) {
            e.printStackTrace();
        } catch (RepositoryException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void destroy() throws Exception {
        if (pushManager != null) {
            pushManager.shutdown();
        } else {
            logger.warn("Push manager wasn't started, nothing to shutdown.");
        }
    }

    public Set<String> getUserDeviceTokens(final JahiaUser jahiaUser) {
        final Set<String> deviceTokens = new LinkedHashSet<String>();
        try {
            JCRTemplate.getInstance().doExecuteWithSystemSession(new JCRCallback<Object>() {
                @Override
                public Object doInJCR(JCRSessionWrapper jcrSessionWrapper) throws RepositoryException {
                    JCRNodeWrapper userNode = jcrSessionWrapper.getNode(jahiaUser.getLocalPath());
                    JCRNodeWrapper deviceTokensNode = null;
                    if (userNode.hasNode(ApplePushNotificationService.IOS_DEVICE_TOKENS_USER_NODE)) {
                        deviceTokensNode = userNode.getNode(ApplePushNotificationService.IOS_DEVICE_TOKENS_USER_NODE);
                    }
                    if (deviceTokensNode != null) {
                        if (deviceTokensNode.hasProperty(ApplePushNotificationService.IOS_DEVICE_TOKENS_USER_PROPERTY)) {
                            JCRPropertyWrapper deviceTokensProperty = deviceTokensNode.getProperty(ApplePushNotificationService.IOS_DEVICE_TOKENS_USER_PROPERTY);
                            JCRValueWrapper[] deviceTokensValues = deviceTokensProperty.getValues();
                            for (JCRValueWrapper jcrValueWrapper : deviceTokensValues) {
                                deviceTokens.add(jcrValueWrapper.getString());
                            }
                        }
                    }
                    return null;
                }
            });
        } catch (RepositoryException e) {
            e.printStackTrace();
        }
        return deviceTokens;
    }

    public void deleteInvalidToken(final String invalidToken) {

        try {
            JCRTemplate.getInstance().doExecuteWithSystemSession(new JCRCallback<Object>() {
                @Override
                public Object doInJCR(JCRSessionWrapper jcrSessionWrapper) throws RepositoryException {
                    Query usersWithDeviceTokensQuery = jcrSessionWrapper.getWorkspace().getQueryManager().createQuery("SELECT * FROM [jnt:iOSDeviceTokens] as deviceTokens WHERE deviceTokens.[" + IOS_DEVICE_TOKENS_USER_PROPERTY + "]='" + invalidToken + "'", Query.JCR_SQL2);
                    QueryResult queryResult = usersWithDeviceTokensQuery.execute();

                    Set<String> deviceTokens = new LinkedHashSet<String>();
                    NodeIterator deviceTokenNodeseIterator = queryResult.getNodes();
                    while (deviceTokenNodeseIterator.hasNext()) {
                        Node deviceTokenNode = deviceTokenNodeseIterator.nextNode();
                        if (deviceTokenNode.hasProperty(IOS_DEVICE_TOKENS_USER_PROPERTY)) {
                            Value[] iosDeviceTokensValues = deviceTokenNode.getProperty(IOS_DEVICE_TOKENS_USER_PROPERTY).getValues();
                            for (Value iosDeviceTokensValue : iosDeviceTokensValues) {
                                if (!invalidToken.equals(iosDeviceTokensValue.getString())) {
                                    deviceTokens.add(iosDeviceTokensValue.getString());
                                }
                            }
                        }
                        jcrSessionWrapper.checkout(deviceTokenNode);
                        if (deviceTokens.size() > 0) {
                            deviceTokenNode.setProperty(IOS_DEVICE_TOKENS_USER_PROPERTY, deviceTokens.toArray(new String[deviceTokens.size()]));
                        } else {
                            if (deviceTokenNode.hasProperty(IOS_DEVICE_TOKENS_USER_PROPERTY)) {
                                deviceTokenNode.setProperty(IOS_DEVICE_TOKENS_USER_PROPERTY, (Value[]) null);
                            }
                        }
                        jcrSessionWrapper.save();
                    }
                    return null;
                }
            });
        } catch (RepositoryException e) {
            e.printStackTrace();
        }

    }

    private class MyRejectedNotificationListener implements RejectedNotificationListener<SimpleApnsPushNotification> {

        @Override
        public void handleRejectedNotification(
                final PushManager<? extends SimpleApnsPushNotification> pushManager,
                final SimpleApnsPushNotification notification,
                final RejectedNotificationReason reason) {

            logger.error(notification + " was rejected with rejection reason " + reason);
            if (reason.getErrorCode() == RejectedNotificationReason.INVALID_TOKEN.getErrorCode()) {
                deleteInvalidToken(TokenUtil.tokenBytesToString(notification.getToken()));
            }
        }
    }

    private class MyFailedConnectionListener implements FailedConnectionListener<SimpleApnsPushNotification> {

        @Override
        public void handleFailedConnection(
                final PushManager<? extends SimpleApnsPushNotification> pushManager,
                final Throwable cause) {

            logger.error("Failed connection:", cause);
            if (cause instanceof SSLHandshakeException) {
                // This is probably a permanent failure, and we should shut down
                // the PushManager.
            }
        }
    }

    private class MyExpiredTokenListener implements ExpiredTokenListener<SimpleApnsPushNotification> {

        @Override
        public void handleExpiredTokens(
                final PushManager<? extends SimpleApnsPushNotification> pushManager,
                final Collection<ExpiredToken> expiredTokens) {

            for (final ExpiredToken expiredToken : expiredTokens) {
                // Stop sending push notifications to each expired token if the expiration
                // time is after the last time the app registered that token.
                logger.warn("Expired token:", expiredToken.toString());
                deleteInvalidToken(TokenUtil.tokenBytesToString(expiredToken.getToken()));
            }
        }
    }
}
