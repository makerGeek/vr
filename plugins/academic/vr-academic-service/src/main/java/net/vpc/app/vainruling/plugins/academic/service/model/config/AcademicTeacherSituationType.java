/*
 * To change this license header, choose License Headers in Project Properties.
 *
 * and open the template in the editor.
 */
package net.vpc.app.vainruling.plugins.academic.service.model.config;

import net.vpc.common.strings.StringUtils;
import net.vpc.upa.config.*;

/**
 * @author taha.bensalah@gmail.com
 */
public enum AcademicTeacherSituationType {
    PERMANENT(true),
    LEAVE(false),
    TEMPORARY(false),
    CONTRACTUAL(true);

    private boolean withDue;

    AcademicTeacherSituationType(boolean withDue) {
        this.withDue = withDue;
    }

    public boolean isWithDue() {
        return withDue;
    }
}
