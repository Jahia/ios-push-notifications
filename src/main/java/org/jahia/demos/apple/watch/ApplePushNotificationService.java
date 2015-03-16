package org.jahia.demos.apple.watch;

import com.relayrides.pushy.apns.*;
import com.relayrides.pushy.apns.util.*;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;

import javax.net.ssl.SSLHandshakeException;
import java.io.InputStream;
import java.util.Collection;

/**
 * Created by loom on 06.03.15.
 */
public class ApplePushNotificationService implements InitializingBean, DisposableBean {

    PushManager<SimpleApnsPushNotification> pushManager;

    private class MyRejectedNotificationListener implements RejectedNotificationListener<SimpleApnsPushNotification> {

        @Override
        public void handleRejectedNotification(
                final PushManager<? extends SimpleApnsPushNotification> pushManager,
                final SimpleApnsPushNotification notification,
                final RejectedNotificationReason reason) {

            System.out.format("%s was rejected with rejection reason %s\n", notification, reason);
        }
    }

    private class MyFailedConnectionListener implements FailedConnectionListener<SimpleApnsPushNotification> {

        @Override
        public void handleFailedConnection(
                final PushManager<? extends SimpleApnsPushNotification> pushManager,
                final Throwable cause) {

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
            }
        }
    }


    @Override
    public void afterPropertiesSet() throws Exception {

        InputStream certInputStream = this.getClass().getClassLoader().getResourceAsStream("aps-development.p12");

        pushManager =
                new PushManager<SimpleApnsPushNotification>(
                        ApnsEnvironment.getSandboxEnvironment(),
                        SSLContextUtil.createDefaultSSLContext(certInputStream, "honor522{statistically"),
                        null, // Optional: custom event loop group
                        null, // Optional: custom ExecutorService for calling listeners
                        null, // Optional: custom BlockingQueue implementation
                        new PushManagerConfiguration(),
                        "ExamplePushManager");

        pushManager.registerRejectedNotificationListener(new MyRejectedNotificationListener());
        pushManager.registerFailedConnectionListener(new MyFailedConnectionListener());
        pushManager.registerExpiredTokenListener(new MyExpiredTokenListener());
        pushManager.start();
        pushManager.requestExpiredTokens();

    }

    public void sendNotification(String alertBody) {
        try {
            final byte[] token = TokenUtil.tokenStringToByteArray(
                    "<5f6aa01d 8e335894 9b7c25d4 61bb78ad 740f4707 462c7eaf bebcf74f a5ddb387>");

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

    @Override
    public void destroy() throws Exception {
        pushManager.shutdown();
    }
}
