/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package net.vpc.app.vainruling.service;

import java.util.logging.Level;
import net.vpc.app.vainruling.api.CorePlugin;
import net.vpc.app.vainruling.api.VrApp;
import net.vpc.app.vainruling.api.security.UserSession;
import net.vpc.app.vainruling.core.service.LoginService;

/**
 *
 * @author vpc
 */
public class Standalone {

    public static void main(String[] args) {

        net.vpc.common.utils.LogUtils.configure(Level.FINE, "net.vpc");
        VrApp.runStandalone(args);
        VrApp.getBean(UserSession.class).setSessionId("custom");
        VrApp.getBean(LoginService.class).login(CorePlugin.USER_ADMIN, "admin");
    }
}
