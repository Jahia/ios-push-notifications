package org.jahia.demos.apple.watch;

import org.drools.core.spi.KnowledgeHelper;
import org.jahia.services.content.rules.AddedNodeFact;

import javax.jcr.RepositoryException;

/**
 * Created by loom on 06.03.15.
 */
public class RuleService {

    ApplePushNotificationService applePushNotificationService;

    public void setApplePushNotificationService(ApplePushNotificationService applePushNotificationService) {
        this.applePushNotificationService = applePushNotificationService;
    }

    public void sendNotification(AddedNodeFact nodeFact, String notificationText, KnowledgeHelper drools)
            throws RepositoryException {
        applePushNotificationService.sendNotification(notificationText);
    }

}
