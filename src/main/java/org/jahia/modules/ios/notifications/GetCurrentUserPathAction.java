package org.jahia.modules.ios.notifications;

import org.jahia.bin.Action;
import org.jahia.bin.ActionResult;
import org.jahia.services.content.JCRSessionWrapper;
import org.jahia.services.render.RenderContext;
import org.jahia.services.render.Resource;
import org.jahia.services.render.URLResolver;
import org.jahia.services.usermanager.JahiaUser;

import javax.servlet.http.HttpServletRequest;
import java.io.PrintWriter;
import java.util.List;
import java.util.Map;

/**
 * Created by loom on 09.04.15.
 */
public class GetCurrentUserPathAction extends Action {
    @Override
    public ActionResult doExecute(HttpServletRequest httpServletRequest, RenderContext renderContext, Resource resource, JCRSessionWrapper jcrSessionWrapper, Map<String, List<String>> map, URLResolver urlResolver) throws Exception {

        JahiaUser jahiaUser = renderContext.getUser();

        if (jahiaUser != null) {
            String userPath = jahiaUser.getLocalPath();
            PrintWriter out = renderContext.getResponse().getWriter();
            out.println(userPath);
        }
        return null;
    }
}
