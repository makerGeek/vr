/*
 * To change this license header, choose License Headers in Project Properties.
 *
 * and open the template in the editor.
 */
package net.vpc.app.vainruling.core.web.obj;

import net.vpc.app.vainruling.core.service.CorePlugin;
import net.vpc.app.vainruling.core.service.CorePluginSecurity;
import net.vpc.app.vainruling.core.service.VrApp;
import net.vpc.app.vainruling.core.service.model.AppDepartment;
import net.vpc.app.vainruling.core.service.model.AppPeriod;
import net.vpc.app.vainruling.core.service.model.AppUser;
import net.vpc.app.vainruling.core.service.obj.*;
import net.vpc.app.vainruling.core.service.util.*;
import net.vpc.app.vainruling.core.web.*;
import net.vpc.app.vainruling.core.web.ctrl.AbstractObjectCtrl;
import net.vpc.app.vainruling.core.web.menu.BreadcrumbItem;
import net.vpc.app.vainruling.core.web.menu.VrMenuManager;
import net.vpc.app.vainruling.core.web.obj.defaultimpl.EntityDetailPropertyView;
import net.vpc.app.vainruling.core.web.obj.defaultimpl.FieldPropertyView;
import net.vpc.common.jsf.FacesUtils;
import net.vpc.common.strings.StringUtils;
import net.vpc.common.util.Convert;
import net.vpc.common.util.PlatformTypes;
import net.vpc.upa.*;
import net.vpc.upa.Package;
import net.vpc.upa.expressions.Expression;
import net.vpc.upa.expressions.Literal;
import net.vpc.upa.filters.FieldFilters;
import net.vpc.upa.types.*;
import org.primefaces.context.RequestContext;
import org.primefaces.event.SelectEvent;
import org.primefaces.extensions.model.dynaform.DynaFormControl;
import org.primefaces.extensions.model.dynaform.DynaFormLabel;
import org.primefaces.extensions.model.dynaform.DynaFormModel;
import org.primefaces.extensions.model.dynaform.DynaFormRow;
import org.springframework.beans.factory.annotation.Autowired;

import javax.el.ELContext;
import javax.el.ValueExpression;
import javax.faces.FacesException;
import javax.faces.component.UIComponent;
import javax.faces.context.FacesContext;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author taha.bensalah@gmail.com
 */
@VrController(
        breadcrumb = {
                @UPathItem(title = "Entité", css = "fa-dashboard", ctrl = "")},
//        css = "fa-table",
//        title = "Liste Entités",
        url = "modules/obj/objects"
)
public class ObjCtrl extends AbstractObjectCtrl<ObjRow> implements VrControllerInfoResolver {

    private static final Logger log = Logger.getLogger(ObjCtrl.class.getName());
    private final List<ColumnView> columns = new ArrayList<>();
    private final List<PropertyView> properties = new ArrayList<>();
    private final List<AutoFilter> autoFilters = new ArrayList<>();
    private final Map<String, Boolean> enabledButtons = new HashMap<>();
    @Autowired
    private CorePlugin core;
    @Autowired
    private ActionDialogManager actionDialogManager;
    @Autowired
    private PropertyViewManager propertyViewManager;
    @Autowired
    private I18n i18n;
    private DynaFormModel dynModel = new DynaFormModel();
    private MainPhotoProvider mainPhotoProvider = null;

    public ObjCtrl() {
        super(null);
        this.model = new PModel();
    }

    @Override
    public VrControllerInfo resolveVrControllerInfo(String cmd) {
        try {
            Config c = VrUtils.parseJSONObject(cmd, Config.class);
            Entity entity = UPA.getPersistenceUnit().getEntity(c.entity);
            VrControllerInfo d = new VrControllerInfo();
            d.setTitle(getPageTitleString(entity, AccessMode.READ));
            d.setUrl("modules/obj/objects");
            d.setCss("fa-table");
            d.setSecurityKey(CorePluginSecurity.getEntityRightEditor(entity));
            List<BreadcrumbItem> items = new ArrayList<>();

            Package p = entity.getParent();
            int pos = items.size();
            while (p != null && !"/".equals(p.getPath())) {
                Package pp = p.getParent();
                items.add(pos, new BreadcrumbItem(p.getTitle(), pp == null ? null : i18n.get(pp), "fa-dashboard", "", ""));
                p = p.getParent();
            }
            d.setBreadcrumb(items.toArray(new BreadcrumbItem[items.size()]));
            return d;
        } catch (Exception ex) {
            log.log(Level.SEVERE, "Error", ex);
        }
        return null;
    }

    public String getPageTitleString(Entity entity, AccessMode mode) {
        if (mode == null) {
            mode = AccessMode.READ;
        }
        if (entity == null) {
            switch (mode) {
                case READ: {
                    return ("Liste Objets");
                }
                case UPDATE: {
                    return ("Mise à jour Objet");
                }
                case PERSIST: {
                    return ("Nouvel Objet");
                }
            }
            return ("???");
        }
        String newTitleString = "???";
        String entityPlainTitle = entity.getTitle();
        switch (mode) {
            case READ: {
                newTitleString = i18n.getOrNull(entity.getI18NTitle().append("ListTitle"), entityPlainTitle);
                if (newTitleString == null) {
                    newTitleString = "Liste " + entityPlainTitle;
                }
                break;
            }
            case UPDATE: {
                newTitleString = i18n.getOrNull(entity.getI18NTitle().append("UpdateTitle"), entityPlainTitle);
                if (newTitleString == null) {
                    newTitleString = "Mise à jour " + entityPlainTitle;
                }
                break;
            }
            case PERSIST: {
                newTitleString = i18n.getOrNull(getEntity().getI18NTitle().append("NewTitle"), entityPlainTitle);
                if (newTitleString == null) {
                    newTitleString = "Nouveau " + entityPlainTitle;
                }
                break;
            }
        }
        return newTitleString;
    }

    public boolean isCustomCheckboxSelection() {
        return false;
    }

    @Override
    public boolean isEnabledButton(String buttonId) {
        Boolean v = enabledButtons.computeIfAbsent(buttonId, this::isEnabledButtonImpl);
        return v.booleanValue();
    }

    public boolean isEnabledButtonImpl(String buttonId) {
        try {
            switch (getModel().getMode()) {
                case READ: {
                    if ("Select".equals(buttonId)) {
                        return isEnabledButton("Remove")
                                || isEnabledButton("Archive");
                    }
                    if ("Refresh".equals(buttonId)) {
                        return true;
                    }
                    if ("Search".equals(buttonId)) {
                        return true;
                    }
                    if ("New".equals(buttonId)) {
                        return UPA.getPersistenceUnit().getSecurityManager().isAllowedPersist(getEntity());
                    }
                    if ("List".equals(buttonId)) {
                        return false;
                    }
                    if ("Remove".equals(buttonId)) {
                        return UPA.getPersistenceUnit().getSecurityManager().isAllowedRemove(getEntity());
                    }
                    if ("Advanced".equals(buttonId)) {
                        return
                                isEnabledButton("Archive")
                                        || isEnabledButton("ReCalc");
                    }

                    if ("Archive".equals(buttonId)) {
                        return UPA.getPersistenceUnit().getSecurityManager()
                                .isAllowedUpdate(getEntity())
                                && core.isArchivable(getModel().getEntityName());
                    }

                    if ("ReCalc".equals(buttonId)) {
                        return core.isCurrentSessionAdmin();
                    }

                    ActionDialogAdapter e = actionDialogManager.findAction(buttonId);
                    if (e != null) {
                        return e.isEnabled(getEntity().getEntityType(), getModel().getMode(), null);
                    }
                    break;
                }
                case PERSIST: {
                    if ("Save".equals(buttonId)) {
                        return UPA.getPersistenceUnit().getSecurityManager().isAllowedPersist(getEntity());
                    }
                    if ("List".equals(buttonId)) {
                        return true;
                    }
                    ActionDialogAdapter e = actionDialogManager.findAction(buttonId);
                    if (e != null) {
                        return e.isEnabled(getEntity().getEntityType(), getModel().getMode(), getModel().getCurrentDocument());
                    }
                    break;
                }
                case UPDATE: {
                    if ("Save".equals(buttonId)) {
                        UPASecurityManager sm = UPA.getPersistenceUnit().getSecurityManager();
                        boolean allowedUpdate = sm.isAllowedUpdate(getEntity());
                        if(!allowedUpdate){
                            return false;
                        }

                        return sm.isAllowedUpdate(getEntity(),getCurrentId(),getCurrentEntityObject());
                    }
                    if ("New".equals(buttonId)) {
                        return UPA.getPersistenceUnit().getSecurityManager().isAllowedPersist(getEntity());
                    }
                    if ("Refresh".equals(buttonId)) {
                        return true;
                    }
                    if ("List".equals(buttonId)) {
                        return true;
                    }
                    if ("Remove".equals(buttonId)) {
                        return UPA.getPersistenceUnit().getSecurityManager().isAllowedRemove(getEntity());
                    }
                    if ("Archive".equals(buttonId)) {
                        return UPA.getPersistenceUnit().getSecurityManager()
                                .isAllowedUpdate(getEntity())
                                && core.isArchivable(getModel().getEntityName());
                    }
                    if ("Advanced".equals(buttonId)) {
                        return isEnabledButton("Archive");
                    }
                    ActionDialogAdapter e = actionDialogManager.findAction(buttonId);
                    if (e != null) {
                        return e.isEnabled(getEntity().getEntityType(), getModel().getMode(), getModel().getCurrentDocument());
                    }
                    break;
                }
            }
            return false;
        } catch (RuntimeException ex) {
            log.log(Level.SEVERE, "Error", ex);
//            throw ex;
            return false;
        }
    }

    public Object getCurrentId() {
        Document selectedObject = getModel().getCurrentDocument();
        EntityBuilder b = getEntity().getBuilder();
        return selectedObject == null ? null : b.documentToId(selectedObject);
    }

    public Object getCurrentEntityObject() {
        Document selectedObject = getModel().getCurrentDocument();
        EntityBuilder b = getEntity().getBuilder();
        return selectedObject == null ? null : b.documentToObject(selectedObject);
    }

    public List getSelectedEntityObjects() {
        List list = new ArrayList();
        EntityBuilder b = getEntity().getBuilder();
        for (Object selectedObject : getModel().getSelectedObjects()) {
            Object o = (selectedObject instanceof Document) ? b.documentToObject((Document) selectedObject) : selectedObject;
            list.add(o);
        }
        return list;
    }

    public String getPreferredExportFileName(){
        return core.getPreferredFileName(VrUPAUtils.getEntityListLabel(getEntity()));
    }

    @Override
    public PModel getModel() {
        return (PModel) super.getModel();
    }

    public String getEntityName() {
        return getModel().getEntityName();
    }

    public void setEntityName(String entityName) {
        enabledButtons.clear();
        try {
            UPA.getPersistenceUnit().getEntity(entityName);
            getModel().setEntityName(entityName);
            getModel().setList(new ArrayList<ObjRow>());
            getModel().setCurrent(delegated_newInstance());
            mainPhotoProvider = null;
            String p = getEntity().getProperties().getString("ui.main-photo-property");
            String d = getEntity().getProperties().getString("ui.main-photo-property.default");
            if (!StringUtils.isEmpty(p)) {
                mainPhotoProvider = new PropertyMainPhotoProvider(p, d);
            } else {
                p = Vr.get().trim(getEntity().getProperties().getString("ui.main-photo-provider"));
                if(!StringUtils.isEmpty(p)) {
                    try {
                        mainPhotoProvider = (MainPhotoProvider) Class.forName(p).newInstance();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
//            updateMode(AccessMode.READ);
//            currentModelToView();
        } catch (RuntimeException ex) {
            log.log(Level.SEVERE, "Error", ex);
//            throw ex;
        }
    }

    public Entity getEntity() {
        return UPA.getPersistenceUnit().getEntity(getModel().getEntityName());
    }

    @Override
    protected void updateMode(AccessMode m) {
        enabledButtons.clear();
        super.updateMode(m);
        updateModelFromConfig();
        updateView();
    }

    public DynaFormModel getDynModel() {
        return dynModel;
    }

    public void setDynModel(DynaFormModel dynModel) {
        this.dynModel = dynModel;
    }

    public void onReCalc() {
        try {
            CorePlugin.get().updateEntityFormulas(getEntityName());
            FacesUtils.addInfoMessage("Realcul Réussi");
        } catch (RuntimeException ex) {
            log.log(Level.SEVERE, "Error", ex);
            FacesUtils.addErrorMessage(ex);
        }
    }

    public void onSaveCurrent() {
        enabledButtons.clear();
        try {
            currentViewToModel();
            switch (getModel().getMode()) {
                case PERSIST: {
                    Object c = getModel().getCurrentDocument();
                    core.save(getEntityName(), c);
                    updateMode(AccessMode.UPDATE);
//                    onCancelCurrent();
                    break;
                }
                case UPDATE: {
                    Object c = getModel().getCurrentDocument();
                    core.save(getEntityName(), c);
                    onReloadCurrent();
//                    onCancelCurrent();
                    break;
                }
            }
//            reloadPage();
            FacesUtils.addInfoMessage("Enregistrement Réussi");
        } catch (RuntimeException ex) {
            log.log(Level.SEVERE, "Error", ex);
            FacesUtils.addErrorMessage(ex);
//            throw ex;
        }
    }

    public void onCloneCurrent() {
        enabledButtons.clear();
        try {
            currentViewToModel();
            switch (getModel().getMode()) {
                case PERSIST: {
                    Object c = getModel().getCurrentDocument();
                    core.save(getEntityName(), c);
                    updateMode(AccessMode.UPDATE);
//                    onCancelCurrent();
                    break;
                }
                case UPDATE: {
                    Document c = getModel().getCurrentDocument();
                    if (getEntity().getIdFields().size() == 1 && getEntity().getIdFields().get(0).isGeneratedId()) {
                        getEntity().getBuilder().setDocumentId(c, null);
                        core.save(getEntityName(), c);
                    }
//                    onCancelCurrent();
                    break;
                }
            }
//            reloadPage();
            FacesUtils.addInfoMessage("Enregistrement Réussi");
        } catch (RuntimeException ex) {
            log.log(Level.SEVERE, "Error", ex);
            FacesUtils.addErrorMessage(ex);
//            throw ex;
        }
    }

    public void onSaveCurrentAndClose() {
        enabledButtons.clear();
        try {
            currentViewToModel();
            switch (getModel().getMode()) {
                case PERSIST: {
                    Object c = getModel().getCurrentDocument();
                    core.save(getEntityName(), c);
                    onCancelCurrent();
                    break;
                }
                case UPDATE: {
                    Object c = getModel().getCurrentDocument();
                    core.save(getEntityName(), c);
                    onCancelCurrent();
                    break;
                }
            }
            reloadPage(true);
            updateMode(AccessMode.READ);
        } catch (RuntimeException ex) {
            log.log(Level.SEVERE, "Error", ex);
            FacesUtils.addErrorMessage(ex);
//            throw ex;
        }
    }

    public void onDeleteCurrent() {
        enabledButtons.clear();
        int count = 0;
        try {
            if (getModel().getMode() == AccessMode.READ) {
                for (ObjRow row : getSelectedRows()) {
                        core.remove(getEntityName(), core.resolveId(getEntityName(), row.getDocument()));
                        count++;
                }
                reloadPage(true);
            } else {
                currentViewToModel();
                Object c = getModel().getCurrentDocument();
                core.remove(getEntityName(), core.resolveId(getEntityName(), c));
                count++;
                loadList();
                updateMode(AccessMode.READ);
                getModel().setCurrent(null);
//                reloadPage(true);
//                updateMode(AccessMode.READ);
            }
            if (count == 0) {
                FacesUtils.addInfoMessage("Aucun Enregistrement supprimé");
            } else {
                FacesUtils.addInfoMessage(count + " Enregistrement(s) supprimé(s)");
            }
        } catch (RuntimeException ex) {
            log.log(Level.SEVERE, "Error", ex);
            FacesUtils.addErrorMessage(count + " Erreur : " + ex);
        }
    }

    public void onArchiveCurrent() {
        enabledButtons.clear();
        int count = 0;
        try {
            if (getModel().getMode() == AccessMode.READ) {
                for (ObjRow row : getSelectedRows()) {
                    core.archive(getEntityName(), core.resolveId(getEntityName(), row.getDocument()));
                    count++;
                }
                reloadPage(true);
            } else {
                currentViewToModel();
                Object c = getModel().getCurrentDocument();
                core.archive(getEntityName(), core.resolveId(getEntityName(), c));
                getModel().setCurrent(delegated_newInstance());
                updateMode(AccessMode.READ);
            }
            if (count == 0) {
                FacesUtils.addInfoMessage("Aucun Enregistrement archivé");
            } else {
                FacesUtils.addInfoMessage(count + " Enregistrement(s) archivé(s)");
            }
        } catch (RuntimeException ex) {
            log.log(Level.SEVERE, "Error", ex);
            FacesUtils.addErrorMessage(count + " / Erreur : " + ex);
        }
    }

    @OnPageLoad
    public void onPageLoad(String cmd) {
        getAutoFilters().clear();
        getModel().setSearch(null);
        getModel().setSearchText(null);
        reloadPage(cmd, false);
    }

    @Override
    public void reloadPage(String cmd, boolean enableCustomization) {
        enabledButtons.clear();
        ObjSearch oldSearch = getModel().getSearch();
        ObjFieldSelection oldFieldSelection = getModel().getFieldSelection();
        Set<String> oldDisabledFields = getModel().getDisabledFields();
        String oldEntityName = getModel().getEntityName();
        getModel().setSearch(null);
        getModel().setFieldSelection(null);
        try {
            Config cfg = VrUtils.parseJSONObject(cmd, Config.class);
            getModel().setConfig(cfg);
            getModel().setCmd(cmd);
            setEntityName(cfg.entity);
            if (oldEntityName == null || !oldEntityName.equals(cfg.entity)) {
                oldSearch = null;
                oldFieldSelection = null;
                oldDisabledFields = null;
            }
            if (enableCustomization) {
                getModel().setDisabledFields(oldDisabledFields);
                getModel().setFieldSelection(oldFieldSelection);
                getModel().setSearch(oldSearch);
            } else {
                getModel().setDisabledFields(new HashSet<String>());

                if (cfg.disabledFields != null) {
                    for (String disabledField : cfg.disabledFields) {
                        if (!StringUtils.isEmpty(disabledField)) {
                            getModel().getDisabledFields().add(disabledField);
                        }
                    }
                }
                if (cfg.selectedFields != null) {
                    getModel().setFieldSelection(new ObjFieldFieldSelection(getEntity(), cfg.selectedFields,getModel().getMode()));
                }
                if (cfg.searchExpr != null) {
                    getModel().setSearch(new ObjSimpleSearch(cfg.searchExpr));
                }
            }
            if (cfg.id == null || cfg.id.trim().length() == 0) {
                updateMode(AccessMode.READ);
                loadList();
                getModel().setCurrent(null);
            } else if (enableCustomization && getModel().getMode() == AccessMode.READ) {
                //do nothing
                updateMode(AccessMode.READ);
                loadList();
                getModel().setCurrent(null);
            } else {
                Entity entity = getEntity();
                UPASecurityManager sm = entity.getPersistenceUnit().getSecurityManager();
                EntityBuilder b = entity.getBuilder();
                Object eid = resolveEntityId(cfg.id);
                Document curr = core.findDocument(getEntityName(), eid);
                if (curr == null) {
                    //should not happen though!
                    updateMode(AccessMode.READ);
                    getModel().setCurrent(null);
                    return;
                }
                ObjRow r = new ObjRow(idToString(eid), curr, b.documentToObject(curr));
                r.setRead(sm.isAllowedLoad(entity, eid, curr));
                r.setWrite(sm.isAllowedUpdate(entity, eid, curr));
                r.setRowPos(-1);
                //now should try finding row pos
                for (ObjRow filteredObject : getModel().getList()) {
                    Object id1 = getEntity().getBuilder().documentToId(filteredObject.getDocument());
                    if (id1.equals(eid)) {
                        r.setRowPos(filteredObject.getRowPos());
                        break;
                    }
                }
                getModel().setCurrent(r);
                updateMode(AccessMode.UPDATE);
                currentModelToView();
            }
        } catch (RuntimeException ex) {
            log.log(Level.SEVERE, "Error", ex);
            FacesUtils.addErrorMessage(ex);
            throw ex;
        }
    }

    public boolean isEnabledMainPhoto() {
        return mainPhotoProvider != null;
    }

    public String getMainName(ObjRow row) {
        String t = getEntity().getMainFieldValue(row.getDocument());
        if (StringUtils.isEmpty(t)) {
            t="NoName";
        }
        return t;
    }

    public String getMainPhoto(ObjRow row) {
        return mainPhotoProvider.getMainPhotoPath(getEntity().getBuilder().documentToId(row.getDocument()), row.getDocument());
    }

    public String getMainIcon(ObjRow row) {
        return mainPhotoProvider.getMainIconPath(getEntity().getBuilder().documentToId(row.getDocument()), row.getDocument());
    }

    public void loadList() {
        String _listFilter = getModel().getConfig().listFilter;
        if (!StringUtils.isEmpty(_listFilter)) {
            _listFilter = "(" + _listFilter + ")";
        } else {
            _listFilter = "";
        }
        HashMap<String, Object> parameters = new HashMap<>();
        int autoFilterIndex = 1;
        for (AutoFilter o : getAutoFilters()) {
            String filterExpression = o.createFilterExpression(parameters, "af" + autoFilterIndex);
            if (!StringUtils.isEmpty(filterExpression)) {
                if (!StringUtils.isEmpty(_listFilter)) {
                    _listFilter += " and ";
                }
                _listFilter += "(" + filterExpression + ")";
            }
            autoFilterIndex++;
        }
        List<Document> found = core.findDocumentsByFilter(getEntityName(), _listFilter, getModel().getSearch(), getModel().getSearchText(), parameters);
        List<ObjRow> filteredObjects = new ArrayList<>();
        Entity entity = getEntity();
        UPASecurityManager sm = entity.getPersistenceUnit().getSecurityManager();
        EntityBuilder b = entity.getBuilder();
        for (Document rec : found) {
            Object id = b.documentToId(rec);
            if (sm.isAllowedNavigate(entity, id, rec)) {
                ObjRow row = new ObjRow(idToString(id), rec, b.documentToObject(rec));
                row.setRead(sm.isAllowedLoad(entity, id, rec));
                row.setWrite(sm.isAllowedUpdate(entity, id, rec));
                row.setRowPos(filteredObjects.size());
                filteredObjects.add(row);
            }
        }
        getModel().setList(filteredObjects);
    }

    private Object resolveEntityId(String strId) {
        if (strId == null || strId.trim().length() == 0) {
            return null;
        }
        //dont know why!
        if (strId.equals("null")) {
            return null;
        }
        try {
            return Convert.toInt(strId);
        } catch (Exception e) {
            return null;
        }
    }

    public Object getSortLayoutPropertyValue(Object o, String property) {
        return getLayoutPropertyValue(o, property);
    }

    public String getRowStyle() {
        Entity p = getEntity();
        String v = p.getProperties().getString(UIConstants.Grid.ROW_STYLE);
        if (StringUtils.isEmpty(v)) {
            return v;
        }
        FacesContext fc = FacesContext.getCurrentInstance();
        String val = null;

        try {
            ELContext elContext = fc.getELContext();
            String expr = "${" + v + "}";
            ValueExpression targetExpression = fc.getApplication().getExpressionFactory().createValueExpression(elContext, expr, String.class);
            val = (String) targetExpression.getValue(elContext);
//            val = (String) elContext.getELResolver().getValue(elContext, null, v);
        } catch (RuntimeException e) {
            throw new FacesException(e.getMessage(), e);
        }

        return val;
    }

    public Object getPropertyColumnStyleClass(String property, ObjRow row) {
        Field p = getEntity().getField(property);
        String v = p.getProperties().getString(UIConstants.Grid.COLUMN_STYLE_CLASS);
        if (v != null) {
            v = v.trim();
            if (v.startsWith("#{") && v.endsWith("}")) {
                VrColorTable table = VrApp.getBean(VrColorTable.class);
                PersistenceUnit pu = UPA.getPersistenceUnit();
                Map<String, Object> map = new HashMap<>();
                map.put("this", row == null ? null : row.getDocument());
                Document r = pu.getFactory().createObject(Document.class);
                r.setObject("pos", row == null ? -1 : row.getRowPos());
                map.put("ui", r);
                String expr = v.substring(2, v.length() - 1);
                ExpressionManager expressionManager = pu.getExpressionManager();
                if (!expressionManager.containsFunction("inthash")) {
                    expressionManager.addFunction("inthash", TypesFactory.INT, inthash);
                }
                if (!expressionManager.containsFunction("hashToStringArr")) {
                    expressionManager.addFunction("hashToStringArr", TypesFactory.STRING, hashToStringArr);
                }
                Expression expression = expressionManager.simplifyExpression(expr, map);
                QLEvaluator evaluator = expressionManager.createEvaluator();
                evaluator.getRegistry().registerFunctionEvaluator("hashToStringArr", hashToStringArr);
                evaluator.getRegistry().registerFunctionEvaluator("inthash", inthash);
                expression = evaluator.evalObject(expression, null);
                if (expression instanceof Literal) {
                    Object t = ((Literal) expression).getValue();
                    String ret = t == null ? "" : t.toString().trim();
                    for (int i = 0; i < 20; i++) {
                        ret = ret.replace("{bgcolor" + i + "}", table.getBgColor(i));
                        ret = ret.replace("{fgcolor" + i + "}", table.getFgColor(i));
                    }
                    return ret;
                }
            }
        }
        return v;
    }

    public Object getPropertyColumnStyle(String property, ObjRow row) {
        Field p = getEntity().getField(property);
        String v = p.getProperties().getString(UIConstants.Grid.COLUMN_STYLE);
        if (v != null) {
            v = v.trim();
            if (v.startsWith("#{") && v.endsWith("}")) {
                VrColorTable table = VrApp.getBean(VrColorTable.class);
                PersistenceUnit pu = UPA.getPersistenceUnit();
                Map<String, Object> map = new HashMap<>();
                map.put("this", row == null ? null : row.getDocument());
                Document r = pu.getFactory().createObject(Document.class);
                r.setObject("pos", row == null ? -1 : row.getRowPos());
                map.put("ui", r);
                String expr = v.substring(2, v.length() - 1);
                ExpressionManager expressionManager = pu.getExpressionManager();
                if (!expressionManager.containsFunction("inthash")) {
                    expressionManager.addFunction("inthash", TypesFactory.INT, inthash);
                }
                if (!expressionManager.containsFunction("hashToStringArr")) {
                    expressionManager.addFunction("hashToStringArr", TypesFactory.STRING, hashToStringArr);
                }
                Expression expression = expressionManager.simplifyExpression(expr, map);
                QLEvaluator evaluator = expressionManager.createEvaluator();
                evaluator.getRegistry().registerFunctionEvaluator("hashToStringArr", hashToStringArr);
                evaluator.getRegistry().registerFunctionEvaluator("inthash", inthash);
                expression = evaluator.evalObject(expression, null);
                if (expression instanceof Literal) {
                    Object t = ((Literal) expression).getValue();
                    String ret = t == null ? "" : t.toString().trim();
                    for (int i = 0; i < 20; i++) {
                        ret = ret.replace("{bgcolor" + i + "}", table.getBgColor(i));
                        ret = ret.replace("{fgcolor" + i + "}", table.getFgColor(i));
                    }
                    return ret;
                }
            }
        }
        return v;
    }

    public Object getFilterLayoutPropertyValue(Object o, String property) {
        return getLayoutPropertyValue(o, property);
    }

    public Object getLayoutPropertyValue(Object o, String property) {
        try {
            if (o == null) {
                return null;
            }
            Entity e = getEntity();
            Field field = e.getField(property);
            Object v = field.getMainValue(o);

            if (v != null) {
                DataType d = field.getDataType();
                if (d instanceof EnumType) {
                    v = i18n.getEnum(v);
                }
            }
            return v;
        } catch (RuntimeException ex) {
            log.log(Level.SEVERE, "Error", ex);
            throw ex;
        }
    }

    public String getObjectName(Object obj) {
        return core.getObjectName(getEntityName(), obj);
    }

    protected Document createInitializedDocument() {
        return getEntity().getBuilder().createInitializedDocument();
    }

    @Override
    protected ObjRow delegated_newInstance() {
        enabledButtons.clear();
        try {
            if (getModel().getEntityName() != null) {
                Entity e = getEntity();
                Document o = createInitializedDocument();
                for (Field field : e.getFields()) {
                    Object v = field.getDefaultValue();
                    if (v != null) {
                        o.setObject(field.getName(), v);
//                        e.getBuilder().setProperty(o, field.getName(), v);
                    }
                }
                EntityBuilder b = e.getBuilder();
                ObjRow r = new ObjRow(null,o, b.documentToObject(o));
                r.setRead(true);
                r.setWrite(true);
                r.setSelected(false);
                r.setRowPos(-1);

                return r;
            }
            ObjRow r = new ObjRow(null,createInitializedDocument(), getEntity().getBuilder().createObject());
            r.setRowPos(-1);
            return r;
        } catch (RuntimeException ex) {
            log.log(Level.SEVERE, "Error", ex);
            throw ex;
        }
    }

    public List<ColumnView> getColumns() {
        return columns;
    }

    public void updateModelFromConfig() {
        enabledButtons.clear();
        Config cfg = VrUtils.parseJSONObject(getModel().getCmd(), Config.class);
        Object o = getModel().getCurrentDocument();
        Entity e = getEntity();
        EntityBuilder builder = e.getBuilder();
        if (cfg != null && cfg.values != null && !cfg.values.isEmpty() && o != null && e != null) {
            for (Map.Entry<String, String> entrySet : cfg.values.entrySet()) {
                String fieldName = entrySet.getKey();
                String fieldValueString = entrySet.getValue();
                Field field = e.findField(fieldName);
                if (field != null) {
                    Object value = VrUPAUtils.jsonToObj(fieldValueString, field.getDataType());
                    builder.setProperty(o, fieldName, value);
                    Relationship manyToOnePrimitiveRelationShip = VrUPAUtils.getManyToOnePrimitiveRelationShip(field);
                    if(manyToOnePrimitiveRelationShip!=null){
                        //this is an id of another field
                        Field entityField = manyToOnePrimitiveRelationShip.getSourceRole().getEntityField();
                        Entity re = manyToOnePrimitiveRelationShip.getTargetEntity();
                        Object v = re.findById(value);
//                        v=re.getBuilder().objectToDocument(v);
                        builder.setProperty(o, entityField.getName(), v);
                    }
                }
            }
        }
    }

    public void currentModelToView() {
        List<ObjFormAction> act = new ArrayList<>();
        for (ActionDialogAdapter a : actionDialogManager.findActionsByEntity(getModel().getEntityName())) {
            act.add(new ObjFormAction(a.getLabel(), a.getStyle(), a.getId()));
        }
//        for (ActionInfo a : objService.getEntityActionList(getModel().getEntityName(), getModel().getCurrentObj())) {
//            act.add(new ObjFormAction(a.getLabel(), a.getStyle(), a.getId()));
//        }
        getModel().setActions(act);
        try {
            Object currentObj = getModel().getCurrentDocument();
            for (PropertyView property : properties) {
                property.loadFrom(currentObj);
                property.refresh();
            }
        } catch (RuntimeException ex) {
            log.log(Level.SEVERE, "Error", ex);
            throw ex;
        }
    }

    public Map<String, Object> currentViewToMap() {
        Map<String, Object> map = new HashMap<>();
        try {
            for (PropertyView property : properties) {
                property.storeToMap(map);
            }
        } catch (RuntimeException ex) {
            log.log(Level.SEVERE, "Error", ex);
            throw ex;
        }
        return map;
    }

    public void currentViewToModel() {
        try {
            Document document = getModel().getCurrentDocument();
            for (PropertyView property : properties) {
                property.storeTo(document);
            }
        } catch (RuntimeException ex) {
            log.log(Level.SEVERE, "Error", ex);
            throw ex;
        }
    }

    private boolean updateViewFlag;
    public void updateView() {
        if(updateViewFlag){
            return;
        }
        updateViewFlag=true;
        try {
            enabledButtons.clear();
            try {
                //reset table state
                FacesContext currentInstance = FacesContext.getCurrentInstance();
                try {
                    if (currentInstance != null) {
                        UIComponent table = currentInstance.getViewRoot().findComponent(":listForm:listTable");
                        if (table != null) {
                            table.setValueExpression("sortBy", null);
                        }
                    }
                } catch (Exception ex) {
                    //ignore
                }
                Entity entity = getEntity();
                try {
                    VrApp.getBean(VrMenuManager.class).getModel().getTitleCrumb().setTitle(getPageTitleString(entity, getModel().getMode()));
                    if (currentInstance != null) {
                        UIComponent table = currentInstance.getViewRoot().findComponent(":listForm:listTable");
                        if (table != null) {
                            table.setValueExpression("sortBy", null);
                        }
                    }
                } catch (Exception ex) {
                    //ignore
                }
                //update columns
                Entity ot = UPA.getPersistenceUnit().getEntity(getEntityName());
                columns.clear();
                properties.clear();
                boolean adm = core.isCurrentSessionAdmin();

                List<Field> fields = new ArrayList<Field>();
                if (getModel().getFieldSelection() == null) {
                    fields = new ArrayList<>(ot.getFields(FieldFilters.byModifiersAnyOf(FieldModifier.MAIN, FieldModifier.SUMMARY)));
                } else {
                    fields = getModel().getFieldSelection().getVisibleFields();
                }
                VrUtils.sortPreserveIndex(fields, new Comparator<Field>() {
                    @Override
                    public int compare(Field o1, Field o2) {
//                    return Integer.compare(o1.getPreferredIndex(),o2.getPreferredIndex());
                        return 0;
                    }
                });

                AccessMode mode = getModel().getMode();
                for (Field field : fields) {
                    AccessLevel ral = field.getEffectiveAccessLevel(mode);
                    boolean readonly=false;
                    if (ral == AccessLevel.INACCESSIBLE) {
                        continue;
                    } else if (ral == AccessLevel.READ_ONLY) {
                        readonly=true;
                    }
                    String type = UIConstants.Control.TEXT;
                    if (PlatformTypes.isBooleanType(field.getDataType().getPlatformType())) {
                        type = UIConstants.Control.CHECKBOX;
                    }
                    String property = field.getName();
                    String propertyExpr = field.getName();
                    if (field.getDataType() instanceof ManyToOneType) {
                        ManyToOneType e = (ManyToOneType) field.getDataType();
                        propertyExpr += "." + e.getRelationship().getTargetEntity().getMainField().getName();
                    }
                    columns.add(new ColumnView(field.getTitle(), property, propertyExpr, type));
                }
                FormHelper h = new FormHelper();
                dynModel = h.dynModel;

                ViewContext viewContext = new ViewContext();
                List<OrderedPropertyView> propertyViews = new ArrayList<OrderedPropertyView>();
                List<EntityPart> entityParts = VrUtils.sortPreserveIndex(ot.getParts(), new Comparator<EntityPart>() {
                    @Override
                    public int compare(EntityPart o1, EntityPart o2) {
//                    if (o1 instanceof Section && o2 instanceof Section) {
//                        return 0;
//                    }
//                    if (o1 instanceof Section && o2 instanceof Field) {
//                        return 1;
//                    }
//                    if (o1 instanceof Field && o2 instanceof Section) {
//                        return -1;
//                    }
//                    if (o1 instanceof Field && o2 instanceof Field) {
//                        return Integer.compare(((Field) o1).getPreferredIndex(), ((Field) o2).getPreferredIndex());
//                    }
                        return 0;
                    }
                });
                for (EntityPart entityPart : entityParts) {
                    if (entityPart instanceof Section) {
                        List<Field> sectionFields = ((Section) entityPart).getFields(FieldFilters.byModifiersNoneOf(FieldModifier.SYSTEM));
                        List<Field> sectionSortedFields = VrUtils.sortPreserveIndex(sectionFields, new Comparator<Field>() {
                            @Override
                            public int compare(Field o1, Field o2) {
//                    return Integer.compare(o1.getPreferredIndex(),o2.getPreferredIndex());
                                return 0;
                            }
                        });

                        for (Field field : sectionSortedFields) {
                            updateViewForField(field, viewContext, propertyViews);
                        }

                    } else {
                        Field field = (Field) entityPart;
                        updateViewForField(field, viewContext, propertyViews);
                    }
                }
//            for (Field field : VrUtils.sortPreserveIndex(new ArrayList<>(ot.getFields(FieldFilters.byModifiersNoneOf(FieldModifier.SYSTEM))), Comparator.comparingInt(Field::getPreferredIndex))) {
//                Map<String, Object> config = new HashMap<>();
//                if (getModel().getDisabledFields().contains(field.getName())) {
//                    config.put("disabled", true);
//                }
//                PropertyView[] ctrls = propertyViewManager.createPropertyViews(field.getName(), field, config, viewContext);
//                if (ctrls != null) {
//                    for (PropertyView ctrl : ctrls) {
//                        if (ctrl != null) {
//                            propertyViews.add(new OrderedPropertyView(propertyViews.size(), ctrl));
//                        }
//                    }
//                }
//            }
                if (getModel().getMode() == AccessMode.UPDATE) {
                    boolean firstDetailRelation = true;
                    int counter = 0;
                    int maxPerLine = 2;
                    for (Relationship relation : ot.getRelationships()) {
                        if (relation.getTargetEntity().getName().equals(ot.getName())) {
                            if (relation.getRelationshipType() == RelationshipType.COMPOSITION) {
                                EntityDetailPropertyView details = new EntityDetailPropertyView(relation.getName(), relation, UIConstants.Control.ENTITY_DETAIL, propertyViewManager);
                                details.setPrependNewLine((counter % maxPerLine) == 0);
                                details.setAppendNewLine(false);
                                details.setColspan(1);
                                details.setDisabled(!UPA.getPersistenceUnit().getSecurityManager().isAllowedNavigate(relation.getSourceEntity()));
                                counter++;
                                if (firstDetailRelation) {
                                    details.setSeparatorText("Details");
                                    firstDetailRelation = false;
                                }
                                propertyViews.add(new OrderedPropertyView(propertyViews.size(), details));
                            }
                        }
                    }
                    counter = 0;
                    for (Relationship relation : ot.getRelationships()) {
                        if (relation.getTargetEntity().getName().equals(ot.getName())) {
                            if (relation.getRelationshipType() == RelationshipType.AGGREGATION) {
                                EntityDetailPropertyView details = new EntityDetailPropertyView(relation.getName(), relation, UIConstants.Control.ENTITY_DETAIL, propertyViewManager);
                                details.setPrependNewLine((counter % maxPerLine) == 0);
                                details.setAppendNewLine(false);
                                details.setColspan(1);
                                details.setDisabled(!UPA.getPersistenceUnit().getSecurityManager().isAllowedNavigate(relation.getSourceEntity()));
                                counter++;
                                if (firstDetailRelation) {
                                    details.setSeparatorText("Details");
                                    firstDetailRelation = false;
                                }
                                propertyViews.add(new OrderedPropertyView(propertyViews.size(), details));
                            }
                        }
                    }
                    counter = 0;
                    boolean firstAssoRelation = true;
                    for (Relationship relation : ot.getRelationships()) {
                        if (relation.getTargetEntity().getName().equals(ot.getName())) {
                            final RelationshipType t = relation.getRelationshipType();
                            if (t != RelationshipType.AGGREGATION && t != RelationshipType.COMPOSITION) {
                                EntityDetailPropertyView details = new EntityDetailPropertyView(relation.getName(), relation, UIConstants.Control.ENTITY_DETAIL, propertyViewManager);
                                details.setPrependNewLine((counter % maxPerLine) == 0);
                                details.setAppendNewLine(false);
                                details.setDisabled(!UPA.getPersistenceUnit().getSecurityManager().isAllowedNavigate(relation.getSourceEntity()));
                                details.setColspan(1);
                                counter++;
                                if (firstAssoRelation) {
                                    details.setSeparatorText("Références");
                                    firstAssoRelation = false;
                                }
                                propertyViews.add(new OrderedPropertyView(propertyViews.size(), details));
                            }
                        }
                    }
                }

                Collections.sort(propertyViews);
                for (OrderedPropertyView ctrlv : propertyViews) {
                    PropertyView ctrl = ctrlv.getValue();
                    properties.add(ctrl);
                    boolean hasSeparator = ctrl.getSeparatorText() != null && ctrl.getSeparatorText().length() > 0;
                    if (hasSeparator || ctrl.isPrependNewLine()) {
                        h.newLine();
                    }
                    DynaFormLabel label = null;
                    for (int i = 0; i < ctrl.getPrependEmptyCells(); i++) {
                        h.addLabel("", 1, 1);
                    }
                    if (hasSeparator) {
                        h.addControl(new SeparatorPropertyView(ctrl.getSeparatorText()), UIConstants.Control.SEPARATOR, Integer.MAX_VALUE, 1);
                    }
                    if (!ctrl.isNoLabel()) {
                        label = h.addLabel(ctrl.getName(), 1, 1);
                        if (ctrl.isLabelNewLine()) {
                            h.newLine();
                        }
                        for (int i = 0; i < ctrl.getAppendEmptyCells(); i++) {
                            h.addLabel("", 1, 1);
                        }
                    }

                    DynaFormControl control12 = h.addControl(ctrl, ctrl.getCtrlType(), ctrl.getColspan(), ctrl.getRowpan());
                    if (label != null) {
                        label.setForControl(control12);
                    }
                    if (ctrl.isAppendNewLine()) {
                        h.newLine();
                    }

                }

                List<AutoFilter> newAutoFilters = new ArrayList<>();
                for (AutoFilterData autoFilterData : core.getEntityFilters(entity.getName())) {
                    String type = autoFilterData.getFilterType();
                    if (StringUtils.isEmpty(type) || type.equals("single-selection")) {
                        String expr = autoFilterData.getExpr();
                        if (!expr.matches("[a-zA-Z0-9.]+")) {
                            throw new IllegalArgumentException("Invalid Auto Filter Expr " + expr);
                        }
                        I18n i18n = VrApp.getBean(I18n.class);
                        DataType curr = entity.getDataType();
                        String evalLabel = entity.getTitle();
                        String[] splitted = expr.split("\\.");
                        for (int i = 0; i < splitted.length; i++) {
                            String s = splitted[i];
                            if(i==0 && s.equals("this")){
                                //ignore
                            }else if (curr instanceof KeyType) {
                                Field field = ((KeyType) curr).getEntity().getField(s);
                                evalLabel = field.getTitle();
                                if (field.isManyToOne()) {
                                    Entity targetEntity = field.getManyToOneRelationship().getTargetEntity();
                                    if (targetEntity.isHierarchical()) {
                                        //TODO process hierarchical search
                                    }
                                    curr = (KeyType) targetEntity.getDataType();
                                } else if (field.getDataType() instanceof EnumType) {
                                    curr = field.getDataType();
                                } else {
                                    curr = field.getDataType();
                                }
                            } else {
                                throw new IllegalArgumentException("Unsupported Filter Type " + expr);
                            }
                        }
                        if (StringUtils.isEmpty(autoFilterData.getLabel())) {
                            String label = i18n.getOrNull("UI.Entity." + entity.getName() + ".ui.auto-filter.class");
                            autoFilterData.setLabel(evalLabel);
//                        if(StringUtils.isEmpty(label)){
//                            if(curr instanceof KeyType){
//                                autoFilterData.setLabel(i18n.get(((KeyType) curr).getEntity()));
//                            }else {
//                                autoFilterData.setLabel(autoFilterData.getName());
//                            }
//                        }
                        }
                        PropertyViewManager manager = VrApp.getBean(PropertyViewManager.class);
                        AutoFilter autoFilter0 = null;
                        for (AutoFilter autoFilter : autoFilters) {
                            if (autoFilter.getData().equals(autoFilterData)) {
                                autoFilter0 = autoFilter;
                                break;
                            }
                        }
                        if (autoFilter0 != null) {
                            newAutoFilters.add(autoFilter0);
                        } else {
                            SingleSelectionAutoFilter f = new SingleSelectionAutoFilter(curr, autoFilterData);
                            f.getValues().add(FacesUtils.createSelectItem(String.valueOf(""), "-------", ""));
                            if ((curr instanceof KeyType) || (curr instanceof EnumType)) {
                                List<NamedId> namedIds = manager.getPropertyViewValuesProvider(null, curr).resolveValues(null, null, curr, viewContext);
                                for (NamedId namedId : namedIds) {
                                    f.getValues().add(FacesUtils.createSelectItem(namedId.getStringId(), namedId.getStringName(), ""));
                                }
                                newAutoFilters.add(f);
                                if (curr instanceof KeyType) {
                                    Entity ee = ((KeyType) curr).getEntity();
                                    Object defaultSelection = getDefaultFilterSelectedObject(autoFilterData, curr);
                                    if (defaultSelection != null) {
                                        Object theId = ee.getBuilder().objectToId(defaultSelection);
                                        f.setSelectedString(String.valueOf(theId));
                                    }
                                } else if (curr instanceof EnumType) {
                                    Object defaultSelection = getDefaultFilterSelectedObject(autoFilterData, curr);
                                    if (defaultSelection != null) {
                                        f.setSelectedString(String.valueOf(defaultSelection));
                                    }
                                }
                            } else if (curr instanceof StringType) {
                                List<String> values = UPA.getPersistenceUnit().createQuery("Select distinct " + autoFilterData.getExpr() + " from " + getEntityName())
                                        .getResultList();
                                Collections.sort(values, new Comparator<String>() {
                                    @Override
                                    public int compare(String o1, String o2) {
                                        if (o1 == null) {
                                            o1 = "";
                                        }
                                        if (o2 == null) {
                                            o2 = "";
                                        }
                                        return o1.compareTo(o2);
                                    }
                                });
                                for (String value : values) {
                                    if (value != null) {
                                        f.getValues().add(FacesUtils.createSelectItem(value, value, ""));
                                    }
                                }
                                newAutoFilters.add(f);
                                Object defaultSelection = getDefaultFilterSelectedObject(autoFilterData, curr);
                                if (defaultSelection != null) {
                                    f.setSelectedString(String.valueOf(defaultSelection));
                                }
                            } else if(curr instanceof BooleanType){
                                f.getValues().add(FacesUtils.createSelectItem(String.valueOf(true), StringUtils.nonNull(true), ""));
                                f.getValues().add(FacesUtils.createSelectItem(String.valueOf(false), StringUtils.nonNull(false), ""));
                            } else {
                                throw new IllegalArgumentException("Unsupported");
                            }
                        }
                    } else {
                        throw new IllegalArgumentException("Unsupported Auto Filter Type " + type);
                    }
                }
                autoFilters.clear();
                autoFilters.addAll(newAutoFilters);

                currentModelToView();
            } catch (RuntimeException ex) {
                log.log(Level.SEVERE, "Error", ex);
                throw ex;
            }
        }finally {
            updateViewFlag=false;
        }
    }

    private void updateViewForField(Field field, ViewContext viewContext, List<OrderedPropertyView> propertyViews) {
        Document currentDocument = getModel().getCurrentDocument();
        Map<String, Object> config = new HashMap<>();
        if (getModel().getDisabledFields().contains(field.getName())) {
            config.put("enabled", false);
        }else{
            String cond = UPAObjectHelper.findStringProperty(field, UIConstants.Form.ENABLED_CONDITION, null, null);
            if(!StringUtils.isEmpty(cond)){
                JavascriptEvaluator jse=new JavascriptEvaluator(cond);
                boolean enabled=(boolean) jse.eval(currentDocument);
                config.put("enabled", enabled);
            }
        }
        {
            String cond = UPAObjectHelper.findStringProperty(field, UIConstants.Form.VISIBLE_CONDITION, null, null);
            if(!StringUtils.isEmpty(cond)){
                JavascriptEvaluator jse=new JavascriptEvaluator(cond);
                boolean visible=(boolean) jse.eval(currentDocument);
                config.put("visible", visible);
            }
        }
        {
            String cond = UPAObjectHelper.findStringProperty(field, UIConstants.Form.SUBMIT_ON_CHANGE, null, null);
            if(!StringUtils.isEmpty(cond)){
                JavascriptEvaluator jse=new JavascriptEvaluator(cond);
                boolean submitOnChange=(boolean) jse.eval(currentDocument);
                config.put("submitOnChange", submitOnChange);
            }
        }
        PropertyView[] ctrls = propertyViewManager.createPropertyViews(field.getName(), field, config, viewContext);
        if (ctrls != null) {
            for (PropertyView ctrl : ctrls) {
                if (ctrl != null) {
                    propertyViews.add(new OrderedPropertyView(propertyViews.size(), ctrl));
                }
            }
        }
    }

    public void updatePropertyViews(){
        for (PropertyView propertyView : getProperties()) {
            updatePropertyView(propertyView);
        }
    }

    private void updatePropertyView(PropertyView p){
        Document currentDocument = getModel().getCurrentDocument();
        if(p instanceof FieldPropertyView){
            Field field = ((FieldPropertyView) p).getField();
            if (getModel().getDisabledFields().contains(field.getName())) {
                p.setDisabled(true);
            }else{
                String cond = UPAObjectHelper.findStringProperty(field, UIConstants.Form.ENABLED_CONDITION, null, null);
                if(!StringUtils.isEmpty(cond)){
                    JavascriptEvaluator jse=new JavascriptEvaluator(cond);
                    boolean enabled=(boolean) jse.eval(currentDocument);
                    p.setDisabled(!enabled);
                }
            }
            {
                String cond = UPAObjectHelper.findStringProperty(field, UIConstants.Form.VISIBLE_CONDITION, null, null);
                if(!StringUtils.isEmpty(cond)){
                    JavascriptEvaluator jse=new JavascriptEvaluator(cond);
                    boolean visible=(boolean) jse.eval(currentDocument);
                    p.setVisible(visible);
                }
            }
        }

    }

    public Object getDefaultFilterSelectedObject(AutoFilterData autoFilterData, DataType filterType) {
        Config config = getModel().getConfig();
        if(config!=null && config.ignoreAutoFilter){
            return null;
        }
        if (filterType instanceof KeyType) {
            Entity ee = ((KeyType) filterType).getEntity();
            if (ee.getEntityType().equals(AppPeriod.class)) {
                return core.getCurrentPeriod();
            } else if (ee.getEntityType().equals(AppDepartment.class)) {
                AppUser u = core.getCurrentUser();
                return u == null ? null : u.getDepartment();
            } else if (ee.getEntityType().equals(AppUser.class)) {
                AppUser u = core.getCurrentUser();
                return u == null ? null : u;
            }
        }
        if (autoFilterData.getBaseEntityName().equals("AppTrace")) {
            if (autoFilterData.getName().equals("user")) {
                AppUser u = core.getCurrentUser();
                return u == null ? null : u.getLogin();
            }
        }
        return null;
    }

    public void onAutoFilterChange() {
        onRefresh();
    }

    public List<AutoFilter> getAutoFilters() {
        return autoFilters;
    }

    public boolean isEnabledSelectNext() {
        ObjRow objRow = getModel().getCurrent();
        return objRow != null && objRow.getRowPos() >= 0 && objRow.getRowPos() + 1 < getModel().getList().size();
    }

    public void onSelectNext() {
        ObjRow objRow = getModel().getCurrent();
        if (objRow != null && objRow.getRowPos() >= 0 && objRow.getRowPos() + 1 < getModel().getList().size()) {
            getModel().setCurrent(getModel().getList().get(objRow.getRowPos() + 1));
            onSelectCurrent();
        }
    }

    public boolean isEnabledSelectPrevious() {
        ObjRow objRow = getModel().getCurrent();
        return objRow != null && objRow.getRowPos() > 0 && getModel().getList().size() > 0;
    }

    public void onSelectPrevious() {
        ObjRow objRow = getModel().getCurrent();
        if (objRow != null && objRow.getRowPos() > 0 && getModel().getList().size() > 0) {
            getModel().setCurrent(getModel().getList().get(objRow.getRowPos() - 1));
            onSelectCurrent();
        }
    }

    public void onRefreshCurrent() {
        if(getModel().getMode() == AccessMode.READ) {
            reloadPage(true);
        }else{
            onReloadCurrent();
        }
    }


    public void onReloadCurrent() {
        switch (getModel().getMode()) {
            case PERSIST: {
                onNew();
                break;
            }
            case UPDATE: {
                Object id = getCurrentId();
                ObjRow c = getModel().getCurrent();
                Document curr = id == null ? null : core.findDocument(getEntityName(), id);
                ObjRow objRow = createObjRow(curr);
                if (c != null) {
                    objRow.setRowPos(c.getRowPos());
                }
                onSelect(objRow);
                break;
            }
        }
    }

    public String idToString(Object id) {
        return id==null?null:String.valueOf(id);
    }
    public ObjRow createObjRow(Document o) {
        Entity e = getEntity();
        EntityBuilder b = e.getBuilder();
        Object id = b.documentToId(o);
        ObjRow r = new ObjRow(idToString(id), o, b.documentToObject(o));
        r.setRead(true);
        r.setWrite(true);
        r.setSelected(false);
        r.setRowPos(-1);
        return r;
    }

    @Override
    public void onSelectCurrent() {
        enabledButtons.clear();
        try {
            super.onSelectCurrent();
            currentModelToView();
        } catch (RuntimeException ex) {
            log.log(Level.SEVERE, "Error", ex);
            throw ex;
        }
        VrMenuManager menu = VrApp.getBean(VrMenuManager.class);
        if (getModel().getCurrent() != null) {
            Config cfg = new Config();
            cfg.entity = getEntityName();
            Object curr = getModel().getCurrent() == null ? null : getEntity().getBuilder().objectToId(getModel().getCurrent().getDocument());
            cfg.id = curr == null ? null : String.valueOf(curr);
            if (getModel().getFieldSelection() != null) {
                ArrayList<String> all = new ArrayList<>();
                for (Field f : getModel().getFieldSelection().getVisibleFields()) {
                    all.add(f.getName());
                }
                cfg.selectedFields = all.toArray(new String[all.size()]);
            }
            if (getModel().getSearch() != null) {
                if (getModel().getSearch() instanceof ObjSimpleSearch) {
                    cfg.searchExpr = ((ObjSimpleSearch) getModel().getSearch()).getExpression();
                }
            }
            menu.pushHistory("obj", cfg);
        }
    }

    public void fireEventSearchCancelled() {
        RequestContext ctx = RequestContext.getCurrentInstance();
        ctx.closeDialog(null);
    }

    public void fireEventSearchClosed() {
        onRefresh();
        RequestContext ctx = RequestContext.getCurrentInstance();
        //Object obj
        ctx.closeDialog(new DialogResult(null, null));
//        String[] ids=new String[]{"listForm:listTable"};
//        ctx.update(Arrays.asList(ids));
//        ctx.execute("vpcdoit();");
    }

    public void onSimpleSearch() {
        ObjSearch oldSearch = getModel().getSearch();
        ObjSimpleSearch newSearch = null;
        if (oldSearch instanceof ObjSimpleSearch) {
            newSearch = (ObjSimpleSearch) oldSearch;
        } else {
            newSearch = new ObjSimpleSearch();
        }
        getModel().setSearch(newSearch);
        Map<String, Object> options = new HashMap<String, Object>();
        options.put("resizable", false);
        options.put("draggable", true);
        options.put("modal", true);
        RequestContext.getCurrentInstance().openDialog("/modules/obj/obj-simple-search-dialog", options, null);
    }

    public void onSimpleFieldSelection() {
        ObjFieldSelection oldSearch = getModel().getFieldSelection();
        if (oldSearch == null) {
            oldSearch = new ObjFieldFieldSelection();
        }
        getModel().setFieldSelection(oldSearch);
        oldSearch.prepare(getEntity(),getModel().getMode());
        Map<String, Object> options = new HashMap<String, Object>();
        options.put("resizable", false);
        options.put("draggable", true);
        options.put("modal", true);
        RequestContext.getCurrentInstance().openDialog("/modules/obj/obj-simple-field-sel-dialog", options, null);
    }

    public void onDoNothing() {
//        System.out.println("do nothing");
    }

    public void onClearSearch() {
        getModel().setSearch(null);
        onRefresh();
    }

    public void onClearFieldSelection() {
        getModel().setFieldSelection(null);
        onRefresh();
    }

    public void onList() {
        enabledButtons.clear();
        try {
            getModel().setCurrent(null);//delegated_newInstance()
            updateMode(AccessMode.READ);
        } catch (RuntimeException ex) {
            log.log(Level.SEVERE, "Error", ex);
            throw ex;
        }
        loadList();
        pushHistory();
    }

    private void pushHistory() {
        VrMenuManager menu = VrApp.getBean(VrMenuManager.class);
        ObjRow current = getModel().getCurrent();
        Config cfg = new Config();
        cfg.entity = getEntityName();
        cfg.id = current == null ? null : String.valueOf(getEntity().getBuilder().objectToId(current.getDocument()));
        cfg.values = getModel().getConfig().values;
        cfg.listFilter = getModel().getConfig().listFilter;
        cfg.disabledFields = getModel().getConfig().disabledFields;
        menu.pushHistory("obj", cfg);
    }

    @Override
    public void onCancelCurrent() {
        enabledButtons.clear();
        super.onCancelCurrent();
        pushHistory();
    }

    public List<PropertyView> getProperties() {
        return properties;
    }

    public boolean filterByProperty(Object value, Object filter, Locale locale) {
        String filterText = (filter == null) ? null : filter.toString().trim();
        if (filterText == null || filterText.equals("")) {
            return true;
        }

        if (value == null) {
            return false;
        }
        PersistenceUnit pu = UPA.getPersistenceUnit();
        Entity e = pu.findEntity(value.getClass());
        if (e != null) {
            value = e.getBuilder().getMainValue(value);
        }
        String svalue = value == null ? "" : String.valueOf(value);
        String sfilter = String.valueOf(filterText);
        return svalue.toLowerCase().contains(sfilter.toLowerCase());
    }

    public void onAction(String actionKey, Object[] args) {
        enabledButtons.clear();
        if ("Persist".equals(actionKey)) {
            onNew();
        } else if ("Save".equals(actionKey)) {
            onSaveCurrent();
        } else if ("Remove".equals(actionKey)) {
            onDeleteCurrent();
        } else {
            try {
                currentViewToModel();
                Object c = getModel().getCurrentDocument();
                ActionDialogResult r = actionDialogManager.findAction(actionKey).invoke(getEntity().getEntityType(), c, getSelectedIdStrings(), args);
                if (r != null) {
                    switch (r) {
                        case RELOAD_CURRENT: {
                            if (getModel().getMode() == AccessMode.READ) {
                                reloadPage(true);
                            } else {
                                onReloadCurrent();
                            }
                            break;
                        }
                        case RELOAD_ALL: {
                            if (getModel().getMode() == AccessMode.READ) {
                                reloadPage(true);
                            } else {
                                loadList();
                                updateMode(AccessMode.READ);
                                getModel().setCurrent(null);
                            }
                            break;
                        }
                    }
                }
            } catch (RuntimeException ex) {
                log.log(Level.SEVERE, "Error", ex);
                throw ex;
            }
        }
    }

    public void updateAllFormulas() {
        enabledButtons.clear();
        UPA.getPersistenceUnit().updateFormulas();
    }

    public PropertyView findPropertyView(String componentId) {
        for (PropertyView p : properties) {
            if (p.getComponentId().equals(componentId)) {
                return p;
            }
        }
        return null;
    }

    public List<ObjRow> getSelectedRows() {
        List<ObjRow> selected=new ArrayList<>();
        if(isCustomCheckboxSelection()) {
            for (ObjRow r : getModel().getList()) {
                if (r.isSelected()) {
                    selected.add(r);
                }
            }
        }else{
            selected.addAll(getModel().getSelectedRows());
        }
        return selected;
    }

    public List<String> getSelectedIdStrings() {
        List<String> selected = new ArrayList<>();
        if (getModel().getMode() == AccessMode.READ) {
            for (ObjRow r : getSelectedRows()) {
                String iid = StringUtils.nonEmpty(core.resolveId(getEntityName(), r.getDocument()));
                if (iid != null) {
                    selected.add(iid);
                } else {
                    System.out.println("Why?");
                }
            }
        } else if (getModel().getCurrentDocument() != null) {
            String iid = StringUtils.nonEmpty(core.resolveId(getEntityName(), getModel().getCurrentDocument()));
            if (iid != null) {
                selected.add(iid);
            }
        }
        return selected;
    }

    public void proceedOpenActionDialog() {
        String actionId = getModel().getActionId();
        if (actionId != null) {
            ActionDialogAdapter ed = VrApp.getBean(ActionDialogManager.class).findAction(actionId);
            if (ed != null) {
                if (ed.isDialog()) {

                    currentViewToModel();
                    ed.openDialog(getSelectedIdStrings());
                    return;
                } else {
                    try {
                        currentViewToModel();
                        Object c = getModel().getCurrentDocument();
                        ActionDialogResult r = ed.invoke(getEntity().getEntityType(), c, getSelectedIdStrings(), null);
                        if (r != null) {
                            switch (r) {
                                case RELOAD_CURRENT: {
                                    if (getModel().getMode() == AccessMode.READ) {
                                        reloadPage(true);
                                    } else {
                                        onReloadCurrent();
                                    }
                                    break;
                                }
                                case RELOAD_ALL: {
                                    if (getModel().getMode() == AccessMode.READ) {
                                        reloadPage(true);
                                    } else {
                                        loadList();
                                        updateMode(AccessMode.READ);
                                        getModel().setCurrent(null);
                                    }
                                    break;
                                }
                            }
                        }
                        RequestContext ctx = RequestContext.getCurrentInstance();
                        ctx.closeDialog(null);
                    } catch (RuntimeException ex) {
                        FacesUtils.addInfoMessage("Error : " + ex.getMessage());
                        log.log(Level.SEVERE, "Error", ex);
                        throw ex;
                    }
                    return;
                }
            }
        }
        onAction(actionId, null);
        fireEventSearchClosed();
    }

    public void openActionDialog(String actionId) {
        if (actionId != null) {
            ActionDialogAdapter ed = VrApp.getBean(ActionDialogManager.class).findAction(actionId);
            if (ed != null) {
                if (ed.isConfirm()) {
                    getModel().setActionId(actionId);
                    getModel().setConfirmMessage("Etes vous sur de vouloir continuer?");
                    Map<String, Object> options = new HashMap<String, Object>();
                    options.put("resizable", false);
                    options.put("draggable", true);
                    options.put("modal", true);

                    RequestContext.getCurrentInstance().openDialog("/modules/obj/profile-open-action-dialog", options, null);
                } else {
                    getModel().setActionId(actionId);
                    proceedOpenActionDialog();
                }
            }
        }
    }

    public void onActionDialogClosed(SelectEvent event) {
        DialogResult o = (DialogResult) event.getObject();
        if (o != null) {
            String value = o.getValue();
            Object[] args=null;
            if(value!=null) {
                if (value.startsWith("[")) {
                    args = VrUtils.parseJSONObject(value, String[].class);
                } else {
                    args = new String[]{value};
                }
            }
            onAction(o.getUserInfo(), args);
        }
    }

    public void openPropertyViewDialog(String property,String action) {
        PropertyView p = findPropertyView(property);
        if (p != null) {
            PropertyViewDialog ed = VrApp.getBean(PropertyViewDialogManager.class).getPropertyViewDialog(p.getCtrlType(),action);
            if (ed != null) {
                ed.openDialog(p, action, p.getComponentId());
            }
        }
    }

    public void onSearchDialogClosed(SelectEvent event) {
//        System.out.println("onSearchDialogClosed");
//        onRefresh();
    }

    public void onPropertyViewDialogClosed(SelectEvent event) {
        DialogResult o = (DialogResult) event.getObject();
        if (o != null) {
            PropertyView p = findPropertyView(o.getUserInfo());
            if (p != null) {
                p.setValue(o.getValue());
            }
        }
    }

    public void switchWikiTextArea(PropertyView p) {
        String componentState = p.getComponentState();
        if ("Default".equals(componentState)) {
            p.setComponentState("Edit");
        } else if ("Edit".equals(componentState)) {
            p.setComponentState("Default");
        } else {
            p.setComponentState("Default");
        }
    }

    private static class FormHelper {

        DynaFormModel dynModel = new DynaFormModel();
        DynaFormRow row = null;
        int cols = 0;
        int maxcols = 4;

        private void revalidateRow() {
            if (row == null) {
                row = dynModel.createRegularRow();
            }
        }

        private void newLine() {
            cols = 0;
            row = null;
        }

        public DynaFormControl addControl(Object data, String type, int colspan, int rowspan) {
            revalidateRow();
            colspan = validColspan(colspan);
            rowspan = validRowspan(rowspan);
            DynaFormControl cc = row.addControl(data, type, colspan, rowspan);
            nextPosition(colspan, rowspan);
            return cc;
        }

        public DynaFormLabel addLabel(String data, int colspan, int rowspan) {
            revalidateRow();
            colspan = validColspan(colspan);
            rowspan = validRowspan(rowspan);
            DynaFormLabel lab = row.addLabel(data, colspan, rowspan);
            nextPosition(colspan, rowspan);
            return lab;
        }

        private int validColspan(int colspan) {
            return (colspan <= 0 || colspan == Integer.MAX_VALUE) ? maxcols - cols : colspan;
        }

        private int validRowspan(int rowspan) {
            return (rowspan <= 0 || rowspan == Integer.MAX_VALUE) ? 1 : rowspan;
        }

        private void nextPosition(int colspan, int rowspan) {
            cols += colspan;
            if (cols >= maxcols) {
                newLine();
            }
        }
    }

    public static final class Config {

        public String entity;
        public String id;
        public String listFilter;
        public Map<String, String> values;
        public String[] disabledFields;
        public String[] selectedFields;
        public String searchExpr;
        public boolean ignoreAutoFilter;
    }

    public class PModel extends Model<ObjRow> {

        private String confirmMessage;
        private String actionId;
        private String entityName;
        private List<ObjFormAction> actions = new ArrayList<ObjFormAction>();
        private Config config;
        private Set<String> disabledFields = new HashSet<String>();
        private String searchText;

        public PModel() {
            setCurrent(null);
        }

        public List<ObjFormAction> getActions() {
            return actions;
        }

        public String getActionId() {
            return actionId;
        }

        public void setActionId(String actionId) {
            this.actionId = actionId;
        }

        public void setActions(List<ObjFormAction> actions) {
            this.actions = actions;
        }

        public String getEntityName() {
            return entityName;
        }

        public String getEntityTitle(){
            return UPA.getPersistenceUnit().getEntity(entityName).getTitle();
        }

        public void setEntityName(String entityName) {
            this.entityName = entityName;
        }

        public List getSelectedObjects() {
            switch (getMode()) {
                case UPDATE:
                case PERSIST: {
                    return Arrays.asList(getCurrentDocument());
                }
                case READ: {
                    List all = new ArrayList();
                    for (ObjRow objRow : getSelectedRows()) {
                        all.add(objRow.getDocument());
                    }
                    return all;
                }
            }
            return Collections.emptyList();
        }

        public Document getCurrentDocument() {
            ObjRow c = getCurrent();
            if (c == null) {
                return null;
            }
            return c.getDocument();
        }

        public ObjRow getCurrent() {
            return super.getCurrent();
        }

        @Override
        public void setCurrent(ObjRow current) {
            super.setCurrent(current); //To change body of generated methods, choose Tools | Templates.
        }

        public List<ObjRow> getList() {
            return super.getList();
        }

        public Config getConfig() {
            return config;
        }

        public void setConfig(Config config) {
            this.config = config;
        }

        public Set<String> getDisabledFields() {
            return disabledFields;
        }

        public String getConfirmMessage() {
            return confirmMessage;
        }

        public void setConfirmMessage(String confirmMessage) {
            this.confirmMessage = confirmMessage;
        }

        public void setDisabledFields(Set<String> disabledFields) {
            this.disabledFields = disabledFields;
        }

        public String getSearchText() {
            return searchText;
        }

        public void setSearchText(String searchText) {
            this.searchText = searchText;
        }

    }

    private static Function hashToStringArr = new Function() {
        @Override
        public Object eval(EvalContext evalContext) {
            Object[] a = evalContext.getArguments();
            if (a.length == 0) {
                return "";
            }
            if (a.length == 1) {
                return a[0] == null ? "" : String.valueOf(a[0]);
            }
            int x = 1+(Math.abs(a[0] == null ? 0 : a[0].hashCode()) % (a.length - 1));
            return a[x] == null ? "" : String.valueOf(a[x]);
        }
    };
    Function inthash = new Function() {
        @Override
        public Object eval(EvalContext evalContext) {
            Object[] a = evalContext.getArguments();
            if (a.length == 0) {
                return 0;
            }
            if (a.length == 1) {
                if (a[0] == null) {
                    return 0;
                }
                return a[0].hashCode();
            }
            if (a.length == 2) {
                if (a[0] == null) {
                    return 0;
                }
                int h = a[0].hashCode();
                if (a[1] != null) {
                    int g = Math.abs(a[1].hashCode());
                    return h % g;
                }
                return h;
            }
            return a;
        }
    };

}
