package org.jahia.modules.ios.notifications;

import com.relayrides.pushy.apns.*;
import com.relayrides.pushy.apns.util.*;
import org.jahia.services.content.*;
import org.jahia.services.usermanager.JahiaUser;
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
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Created by loom on 06.03.15.
 */
public class ApplePushNotificationService implements InitializingBean, DisposableBean {

    private transient static Logger logger = org.slf4j.LoggerFactory.getLogger(ApplePushNotificationService.class);

    public static final String IOS_DEVICE_TOKENS_USER_NODE = "iOSDeviceTokens";
    public static final String IOS_DEVICE_TOKENS_USER_PROPERTY = "j:deviceTokens";
    PushManager<SimpleApnsPushNotification> pushManager;

    private String sandBoxCertificatePath;
    private String sandBoxCertificatePassword;
    private String productionCertificatePath;
    private String productionCertificatePassword;
    private boolean sandboxAPNUsed = true;

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


    @Override
    public void afterPropertiesSet() throws Exception {

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

    public void sendNotification(String deviceToken, String alertBody) {
        try {
            final byte[] token = TokenUtil.tokenStringToByteArray(deviceToken);

            final ApnsPayloadBuilder payloadBuilder = new ApnsPayloadBuilder();

            payloadBuilder.setAlertBody(alertBody);
            payloadBuilder.setSoundFileName("ring-ring.aiff");

            final String payload = payloadBuilder.buildWithDefaultMaximumLength();

            pushManager.getQueue().put(new SimpleApnsPushNotification(token, payload));
        } catch (InterruptedException ie) {
            ie.printStackTrace();
        } catch (MalformedTokenStringException e) {
            e.printStackTrace();
        }
    }

    public void sendNotificationToAll(String alertBody) {
        try {
            final Set<String> deviceTokens = new LinkedHashSet<String>();
            JCRTemplate.getInstance().doExecuteWithSystemSession(new JCRCallback<Object>() {
                @Override
                public Object doInJCR(JCRSessionWrapper jcrSessionWrapper) throws RepositoryException {
                    Query usersWithDeviceTokensQuery = jcrSessionWrapper.getWorkspace().getQueryManager().createQuery("SELECT * FROM [jnt:iOSDeviceTokens] as deviceTokens WHERE deviceTokens.["+IOS_DEVICE_TOKENS_USER_PROPERTY+"] IS NOT NULL", Query.JCR_SQL2);
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
                final byte[] token = TokenUtil.tokenStringToByteArray(deviceToken);

                final ApnsPayloadBuilder payloadBuilder = new ApnsPayloadBuilder();

                payloadBuilder.setAlertBody(alertBody);
                payloadBuilder.setSoundFileName("ring-ring.aiff");

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
        pushManager.shutdown();
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
                                deviceTokens.add(jcrValueWrapper.toString());
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
                    Query usersWithDeviceTokensQuery = jcrSessionWrapper.getWorkspace().getQueryManager().createQuery("SELECT * FROM [jnt:iOSDeviceTokens] as deviceTokens WHERE deviceTokens.["+IOS_DEVICE_TOKENS_USER_PROPERTY+"]='" + invalidToken + "'", Query.JCR_SQL2);
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
}
