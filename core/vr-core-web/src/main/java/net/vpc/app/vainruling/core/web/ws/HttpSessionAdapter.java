package net.vpc.app.vainruling.core.web.ws;

import net.vpc.app.vainruling.core.service.security.SessionStore;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpSession;
import javax.servlet.http.HttpSessionContext;
import java.util.Enumeration;

class HttpSessionAdapter implements HttpSession {
    private final SessionStore store;
    private final HttpSession base;

    public HttpSessionAdapter(HttpSession base, SessionStore store) {
        this.base = base;
        this.store = store;
    }

    public HttpSession getBase() {
        return base;
    }

    public long getCreationTime() {
        try {
            return base.getCreationTime();
        }catch (IllegalStateException ex){
            silentPropagateInvalidation();
          throw ex;
        }
    }

    public String getId() {
        return base.getId();
    }

    public long getLastAccessedTime() {
        try {
            return base.getLastAccessedTime();
        }catch (IllegalStateException ex){
            silentPropagateInvalidation();
            throw ex;
        }
    }

    public ServletContext getServletContext() {
        return base.getServletContext();
    }

    public int getMaxInactiveInterval() {
        return base.getMaxInactiveInterval();
    }

    public void setMaxInactiveInterval(int interval) {
        base.setMaxInactiveInterval(interval);
    }

    public Object getAttribute(String name) {
        try {
            return base.getAttribute(name);
        }catch (IllegalStateException ex){
            silentPropagateInvalidation();
            throw ex;
        }
    }

    public Enumeration<String> getAttributeNames() {
        try {
            return base.getAttributeNames();
        }catch (IllegalStateException ex){
            silentPropagateInvalidation();
            throw ex;
        }
    }

    public void removeAttribute(String name) {
        try {
            base.removeAttribute(name);
        }catch (IllegalStateException ex){
            silentPropagateInvalidation();
            throw ex;
        }
    }

    public void invalidate() {
        IllegalStateException ex0 = null;
        Exception ex1 = null;
        String id = getId();
        try {
            base.invalidate();
        } catch (IllegalStateException ex) {
            ex0 = ex;
        }
        try {
            propagateInvalidation();
        } catch (Exception ex) {
            ex1 = ex;
        }
        if (ex0 != null) {
            throw ex0;
        }
        if (ex1 != null) {
            throw new IllegalStateException(ex1);
        }
    }

    private boolean silentPropagateInvalidation() {
        try {
            propagateInvalidation();
            return true;
        } catch (Exception ex) {
            //
        }
        return false;
    }

    private void propagateInvalidation() {
        String id = getId();
        store.remove(id);
    }

    public boolean isNew() {
        try {
            return base.isNew();
        }catch (IllegalStateException ex){
            silentPropagateInvalidation();
            throw ex;
        }
    }

    public void setAttribute(String name, Object value) {
        try {
            base.setAttribute(name, value);
        }catch (IllegalStateException ex){
            silentPropagateInvalidation();
            throw ex;
        }
    }


    //deprecated!!
    public HttpSessionContext getSessionContext() {
        return base.getSessionContext();
    }

    public Object getValue(String name) {
        return base.getValue(name);
    }

    public String[] getValueNames() {
        try {
            return base.getValueNames();
        }catch (IllegalStateException ex){
            silentPropagateInvalidation();
            throw ex;
        }
    }

    public void putValue(String name, Object value) {
        try {
            base.putValue(name, value);
        }catch (IllegalStateException ex){
            silentPropagateInvalidation();
            throw ex;
        }
    }

    public void removeValue(String name) {
        try {
            base.removeValue(name);
        }catch (IllegalStateException ex){
            silentPropagateInvalidation();
            throw ex;
        }
    }

}
