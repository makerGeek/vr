/*
 * To change this license header, choose License Headers in Project Properties.
 *
 * and open the template in the editor.
 */
package net.vpc.app.vainruling.core.web.admin.actions;

import net.vpc.app.vainruling.core.service.model.AppConfig;
import net.vpc.app.vainruling.core.service.obj.EntityAction;

import net.vpc.app.vainruling.core.web.obj.ActionDialog;
import net.vpc.app.vainruling.core.web.obj.ActionDialogResult;
import net.vpc.common.jsf.FacesUtils;
import net.vpc.upa.AccessMode;
import net.vpc.upa.UPA;

import java.util.List;

/**
 * @author taha.bensalah@gmail.com
 */
@EntityAction(entityType = AppConfig.class,
        actionLabel = "u.forml", actionStyle = "fa-calculator",
        dialog = false
)
public class UpdateFormulasAction implements ActionDialog {

    @Override
    public void openDialog(String actionId, List<String> itemIds) {
        throw new IllegalArgumentException("Unsupported");
    }

    @Override
    public boolean isEnabled(String actionId, Class entityType, AccessMode mode, Object value) {
        return true;//value != null;
    }

    @Override
    public ActionDialogResult invoke(String actionId, Class entityType, Object obj, List<String> selectedIdStrings, Object[] args) {
        UPA.getPersistenceUnit().updateFormulas();
        FacesUtils.addInfoMessage("Mise à jour réussie");
        return ActionDialogResult.RELOAD_ALL;
    }
}
