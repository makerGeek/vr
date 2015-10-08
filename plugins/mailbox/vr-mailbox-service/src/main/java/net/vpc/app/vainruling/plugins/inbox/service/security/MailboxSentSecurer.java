/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package net.vpc.app.vainruling.plugins.inbox.service.security;

import net.vpc.app.vainruling.api.CorePlugin;
import net.vpc.app.vainruling.api.VrApp;
import net.vpc.upa.DefaultEntitySecurityManager;
import net.vpc.upa.Entity;
import net.vpc.upa.config.SecurityContext;
import net.vpc.upa.exceptions.UPAException;
import net.vpc.upa.expressions.Expression;
import net.vpc.upa.expressions.UserExpression;

/**
 *
 * @author vpc
 */
@SecurityContext(entity = "MailboxSent")
public class MailboxSentSecurer extends DefaultEntitySecurityManager{

    @Override
    public Expression getEntityFilter(Entity entity) throws UPAException {
        if(VrApp.getBean(CorePlugin.class).isActualAdmin()){
            return null;
        }
        return new UserExpression("this.deleted=false and this.sender.login=currentUser()");
    }
    
}
