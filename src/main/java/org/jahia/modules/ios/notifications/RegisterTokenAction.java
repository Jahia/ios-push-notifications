package org.jahia.modules.ios.notifications;

import org.jahia.bin.Action;
import org.jahia.bin.ActionResult;
import org.jahia.services.content.*;
import org.jahia.services.render.RenderContext;
import org.jahia.services.render.Resource;
import org.jahia.services.render.URLResolver;
import org.jahia.services.usermanager.JahiaUser;

import javax.jcr.RepositoryException;
import javax.servlet.http.HttpServletRequest;
import java.util.*;

/**
 * Created by loom on 09.04.15.
 */
public class RegisterTokenAction extends Action {
    @Override
    public ActionResult doExecute(HttpServletRequest httpServletRequest, RenderContext renderContext, Resource resource, JCRSessionWrapper jcrSessionWrapper, Map<String, List<String>> map, URLResolver urlResolver) throws Exception {

        final String deviceToken = httpServletRequest.getParameter("deviceToken");
        if (deviceToken == null || deviceToken.length() == 0) {
            return null;
        }

        final JahiaUser jahiaUser = renderContext.getUser();
        if (jahiaUser != null) {
            JCRTemplate.getInstance().doExecuteWithSystemSession(new JCRCallback<Object>() {
                @Override
                public Object doInJCR(JCRSessionWrapper jcrSessionWrapper) throws RepositoryException {
                    JCRNodeWrapper userNode = jcrSessionWrapper.getNode(jahiaUser.getLocalPath());
                    JCRNodeWrapper deviceTokensNode = null;
                    if (userNode.hasNode(ApplePushNotificationService.IOS_DEVICE_TOKENS_USER_NODE)) {
                        deviceTokensNode = userNode.getNode(ApplePushNotificationService.IOS_DEVICE_TOKENS_USER_NODE);
                    }
                    Set<String> deviceTokens = new LinkedHashSet<String>();
                    if (deviceTokensNode == null) {
                        jcrSessionWrapper.checkout(userNode);
                        deviceTokensNode = userNode.addNode(ApplePushNotificationService.IOS_DEVICE_TOKENS_USER_NODE, "jnt:iOSDeviceTokens");
                    } else {
                        jcrSessionWrapper.checkout(deviceTokensNode);
                    }
                    if (deviceTokensNode.hasProperty(ApplePushNotificationService.IOS_DEVICE_TOKENS_USER_PROPERTY)) {
                        JCRPropertyWrapper deviceTokensProperty = deviceTokensNode.getProperty(ApplePushNotificationService.IOS_DEVICE_TOKENS_USER_PROPERTY);
                        JCRValueWrapper[] deviceTokensValues = deviceTokensProperty.getValues();
                        for (JCRValueWrapper jcrValueWrapper : deviceTokensValues) {
                            deviceTokens.add(jcrValueWrapper.getString());
                        }
                        deviceTokens.add(deviceToken);
                        deviceTokensNode.setProperty(ApplePushNotificationService.IOS_DEVICE_TOKENS_USER_PROPERTY, deviceTokens.toArray(new String[deviceTokens.size()]));
                    } else {
                        deviceTokens.add(deviceToken);
                        deviceTokensNode.setProperty(ApplePushNotificationService.IOS_DEVICE_TOKENS_USER_PROPERTY, deviceTokens.toArray(new String[deviceTokens.size()]));
                    }
                    jcrSessionWrapper.save();
                    return null;
                }
            });
        }

        return null;
    }
}
