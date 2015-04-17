package org.jahia.modules.ios.notifications;

import org.jahia.bin.Action;
import org.jahia.bin.ActionResult;
import org.jahia.services.content.JCRSessionWrapper;
import org.jahia.services.render.RenderContext;
import org.jahia.services.render.Resource;
import org.jahia.services.render.URLResolver;
import org.slf4j.Logger;

import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;

/**
 * Created by loom on 09.04.15.
 */
public class BlockUserAction extends Action {

    private transient static Logger logger = org.slf4j.LoggerFactory.getLogger(BlockUserAction.class);

    @Override
    public ActionResult doExecute(HttpServletRequest httpServletRequest, RenderContext renderContext, Resource resource, JCRSessionWrapper jcrSessionWrapper, Map<String, List<String>> map, URLResolver urlResolver) throws Exception {

        String userName = httpServletRequest.getParameter("userName");

        logger.info("Blocking user " + userName);

        // @todo add permission check

        return null;
    }
}
