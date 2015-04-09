package org.jahia.modules.ios.notifications;

import org.drools.core.spi.KnowledgeHelper;
import org.jahia.services.content.*;
import org.jahia.services.content.rules.AddedNodeFact;
import org.jahia.services.usermanager.JahiaUser;

import javax.jcr.RepositoryException;
import java.util.LinkedHashSet;
import java.util.Set;

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
        final JahiaUser jahiaUser = nodeFact.getNode().getSession().getUser();
        for (String deviceToken : applePushNotificationService.getUserDeviceTokens(jahiaUser)) {
            applePushNotificationService.sendNotification(deviceToken, notificationText);
        }
    }

    public void sendNotificationToAll(AddedNodeFact nodeFact, String notificationText, KnowledgeHelper drools)
            throws RepositoryException {
        applePushNotificationService.sendNotificationToAll(notificationText);
    }

}
