package net.vpc.app.vainruling.core.web.util;

import javax.faces.component.UIComponent;
import javax.faces.context.FacesContext;
import javax.faces.convert.Converter;
import javax.faces.convert.FacesConverter;
import java.sql.Timestamp;
import java.text.ParseException;
import java.text.SimpleDateFormat;

@FacesConverter("vpcDateConverter")
public class VpcDateConverter implements Converter{
    private SimpleDateFormat f = new SimpleDateFormat("dd/MM/yyyy");//02/20/2015 10:51:23

    @Override
    public Object getAsObject(FacesContext fc, UIComponent uic, String string) {
        Object a = uic.getAttributes().get("pattern");
        try {
            if (string == null || string.length() == 0) {
                return null;
            }
            return new net.vpc.upa.types.Date(f.parse(string).getTime());
        } catch (ParseException ex) {
            ex.printStackTrace();
            return null;
        }
    }

    @Override
    public String getAsString(FacesContext fc, UIComponent uic, Object o) {
        return o == null ? "" : f.format((java.util.Date) o);
    }
}
