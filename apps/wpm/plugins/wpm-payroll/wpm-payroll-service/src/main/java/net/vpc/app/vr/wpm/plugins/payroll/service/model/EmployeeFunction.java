package net.vpc.app.vr.wpm.plugins.payroll.service.model;

import net.vpc.upa.config.*;

/**
 * Created by vpc on 6/13/16.
 */
@Entity
@Path("/Payroll/Config")
public class EmployeeFunction {
    @Id
    @Sequence
    private int id;
    @Main
    @Unique
    private String name;

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}