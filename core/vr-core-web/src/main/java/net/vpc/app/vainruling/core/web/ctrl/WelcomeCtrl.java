/*
 * To change this license header, choose License Headers in Project Properties.
 *
 * and open the template in the editor.
 */
package net.vpc.app.vainruling.core.web.ctrl;

import net.vpc.app.vainruling.core.service.VrApp;
import net.vpc.app.vainruling.core.web.OnPageLoad;
import net.vpc.app.vainruling.core.web.UCtrl;
import net.vpc.app.vainruling.core.web.menu.VrMenuManager;
import org.springframework.context.annotation.Scope;

/**
 * @author taha.bensalah@gmail.com
 */
@UCtrl(
        title = "Accueil",
        url = "modules/welcome",
        menu = "/"
)
@Scope(value = "singleton")
public class WelcomeCtrl {

    private Model model = new Model();

    public Model getModel() {
        return model;
    }

    @OnPageLoad
    public void onLoad() {
        VrApp.getBean(VrMenuManager.class).getModel().setCurrentPageId("welcome");
        VrApp.getBean(VrMenuManager.class).setPageCtrl("welcome");
    }

    public static class Model {

    }
}
