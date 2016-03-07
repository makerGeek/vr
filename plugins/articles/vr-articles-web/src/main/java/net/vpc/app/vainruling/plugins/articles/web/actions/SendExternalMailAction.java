/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package net.vpc.app.vainruling.plugins.articles.web.actions;

import java.util.List;
import net.vpc.app.vainruling.api.EntityAction;
import net.vpc.app.vainruling.api.VrApp;
import net.vpc.app.vainruling.api.web.ctrl.EditCtrlMode;
import net.vpc.app.vainruling.api.web.obj.ActionDialog;
import net.vpc.app.vainruling.plugins.articles.service.ArticlesPlugin;
import net.vpc.app.vainruling.plugins.articles.service.model.ArticlesItem;

/**
 *
 * @author vpc
 */
@EntityAction(entityType = ArticlesItem.class,
        actionName = "sendExternalMail",
        actionLabel = "email", actionStyle = "fa-envelope-o",
        dialog = true
)
public class SendExternalMailAction implements ActionDialog {

    @Override
    public void openDialog(String actionId, List<String> itemIds) {
        SendExternalMailActionCtrl.Config c = new SendExternalMailActionCtrl.Config();
        VrApp.getBean(SendExternalMailActionCtrl.class).openDialog(c);
    }

    @Override
    public boolean isEnabled(Class entityType, EditCtrlMode mode, Object value) {
        return value != null;
    }

    @Override
    public void invoke(Class entityType, Object obj, Object[] args) {
        VrApp.getBean(ArticlesPlugin.class).sendExternalMail((ArticlesItem) obj, (String) args[0]);
    }

}