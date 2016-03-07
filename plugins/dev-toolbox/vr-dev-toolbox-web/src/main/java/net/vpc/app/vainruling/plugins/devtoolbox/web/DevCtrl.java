/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package net.vpc.app.vainruling.plugins.devtoolbox.web;

import javax.faces.bean.ManagedBean;
import net.vpc.app.vainruling.api.VrApp;
import net.vpc.app.vainruling.api.web.UCtrl;
import net.vpc.app.vainruling.plugins.devtoolbox.service.DevSrv;
import net.vpc.common.strings.StringUtils;
import org.springframework.context.annotation.Scope;

/**
 *
 * @author vpc
 */
@UCtrl(
        title = "Developer Upgrade",
        css = "fa-dashboard", url = "modules/devtoolbox/dev-upgrade",
        menu = "/Admin", securityKey = "Custom.DevTools"
)
@ManagedBean
@Scope(value = "session")
public class DevCtrl {

    private String message;

    public void doUpgrade() {
        try {
            VrApp.getBean(DevSrv.class).doUpgrade();
            setMessage("Successful upgrade");
        } catch (Exception e) {
            setMessage(StringUtils.verboseStacktraceToString(e));
        }
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getMessage() {
        return message;
    }

}