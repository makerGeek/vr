/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package net.vpc.app.vainruling.core.web.install;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.faces.context.FacesContext;
import javax.servlet.ServletContext;
import net.vpc.app.vainruling.api.model.AppProperty;
import net.vpc.common.strings.StringUtils;
import net.vpc.upa.PersistenceUnit;
import net.vpc.upa.QueryBuilder;
import net.vpc.upa.UPA;
import net.vpc.upa.VoidAction;
import org.springframework.stereotype.Controller;

/**
 *
 * @author vpc
 */
@Controller
public class VrConfigureInstall {

    private static final String LOCK_PATH = "/META-INF/private/install.lock";

    private final Model model = new Model();

    public void init() {

    }

    public boolean isMandatoryConfig(ServletContext servletContext) {
        if (servletContext.getAttribute(LOCK_PATH) != null) {
            return false;
        }
        String rp = servletContext.getRealPath("/") + LOCK_PATH;
        if (new File(rp).exists()) {
            return false;
        }
        AppProperty p = findSystemProperty("System.FileSystem");
        getModel().setRootPath(p == null ? null : p.getPropertyValue());
        String s = getModel().getRootPath();
        if (StringUtils.isEmpty(s) || new File(s).exists()) {
            return true;
        }
        return false;
    }

    public AppProperty findSystemProperty(String name) {
        PersistenceUnit pu = UPA.getPersistenceUnit();
        QueryBuilder q = pu.createQueryBuilder(AppProperty.class);
        q.addAndField("propertyName", name);
        q.addAndExpression("(userId = null)");
        List<AppProperty> props = q.getEntityList();
        if (props.isEmpty()) {
            return null;
        }
        return props.get(0);
    }

    public String configureInstall() {
        UPA.getContext().invokePrivileged(new VoidAction() {
            @Override
            public void run() {
                PersistenceUnit pu = UPA.getPersistenceUnit();
                AppProperty p = findSystemProperty("System.FileSystem");
                if (p == null) {
                    p = new AppProperty();
                    p.setEnabled(true);
                    p.setPropertyName("System.FileSystem");
                    p.setPropertyValue(getModel().getRootPath());
                    pu.persist(p);
                } else {
                    p.setPropertyValue(getModel().getRootPath());
                    pu.merge(p);
                }
            }
        });

        ServletContext servletContext = (ServletContext) FacesContext.getCurrentInstance().getExternalContext().getContext();
        String rp = servletContext.getRealPath("/") + LOCK_PATH;
        File f = new File(rp);
        try {
            f.getParentFile().mkdirs();
            f.createNewFile();
        } catch (IOException ex) {
            Logger.getLogger(VrConfigureInstall.class.getName()).log(Level.SEVERE, null, ex);
        }
        servletContext.setAttribute(LOCK_PATH, true);
        return "/login";
    }

    public Model getModel() {
        return model;
    }

    public static class Model {

        private String rootPath;

        public String getRootPath() {
            return rootPath;
        }

        public void setRootPath(String rootPath) {
            this.rootPath = rootPath;
        }

    }
}