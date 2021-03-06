/*
 * To change this license header, choose License Headers in Project Properties.
 *
 * and open the template in the editor.
 */
package net.vpc.app.vainruling.core.web.menu;

import net.vpc.app.vainruling.core.web.VRMenuProvider;
import net.vpc.app.vainruling.core.service.CorePlugin;
import net.vpc.app.vainruling.core.service.CorePluginSecurity;
import net.vpc.app.vainruling.core.service.TraceService;
import net.vpc.app.vainruling.core.service.VrApp;
import net.vpc.app.vainruling.core.service.security.UserSession;
import net.vpc.app.vainruling.core.service.util.*;
import net.vpc.app.vainruling.core.web.*;
import net.vpc.app.vainruling.core.web.fs.FSServlet;
import net.vpc.app.vainruling.core.web.obj.ActionDialogAdapter;
import net.vpc.app.vainruling.core.web.obj.ActionDialogManager;
import net.vpc.app.vainruling.core.web.obj.ObjCtrl;
import net.vpc.app.vainruling.core.web.util.VrWebHelper;
import net.vpc.common.jsf.FacesUtils;
import net.vpc.common.strings.StringUtils;
import net.vpc.upa.*;
import net.vpc.upa.Package;
import net.vpc.upa.exceptions.UPAException;
import net.vpc.upa.filters.DefaultEntityFilter;
import net.vpc.upa.filters.EntityFilter;
import net.vpc.upa.filters.ObjectFilter;
import org.springframework.beans.factory.annotation.Autowired;

import javax.annotation.PostConstruct;
import javax.faces.context.FacesContext;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

//import net.vpc.app.vainruling.tasks.TaskPlugin;
//import net.vpc.app.vainruling.tasks.model.TodoList;
/**
 * @author taha.bensalah@gmail.com
 */
@VrController
public class VrMenuManager {

    private static final Logger log = Logger.getLogger(FSServlet.class.getName());

    private Model model = new Model();
    private Object pageCtrl;
    //    @Autowired
//    private TaskPlugin todoService;
    @Autowired
    private I18n i18n;
    @Autowired
    private CorePlugin core;
    private LinkedList<PageInfo> pageHistory = new LinkedList<>();

    public Object getPageCtrl() {
        return pageCtrl;
    }

    public void setPageCtrl(Object pageCtrl) {
        this.pageCtrl = pageCtrl;
    }

    public Object getPageCtrl(String type) {
        if (!type.endsWith("Ctrl")) {
            type = type + "Ctrl";
        }
        return VrApp.getContext().getBean(type);
    }

    public boolean isCurrentPageId(String name) {
        if (StringUtils.isEmpty(name)) {
            return StringUtils.isEmpty(getModel().getCurrentPageId());
        } else {
            return name.equals(getModel().getCurrentPageId());
        }
    }

    public VRMenuInfo createMenu(String searchText) {
        String finalSearchText = (searchText == null ? "" : searchText.toLowerCase());
        ObjectFilter<String> menuFilter = StringUtils.isEmpty(searchText) ? new ObjectFilter<String>() {
            @Override
            public boolean accept(String value) {
                return true;
            }
        } : new ObjectFilter<String>() {
            @Override
            public boolean accept(String value) {
                if (value == null) {
                    value = "";
                }
                return value.toLowerCase().contains(finalSearchText);
            }
        };
        return (new MenuTree(menuFilter).root);
    }

    public String buildMenu() {
        Map<String, String> params = FacesContext.getCurrentInstance().getExternalContext().getRequestParameterMap();
        String searchText = params.get("searchTextInput");
        getModel().setSearchText("");
        getModel().setRoot(createMenu(searchText));
        return "ignore-me";
    }

    private List<VRMenuInfo> findCustomMenus(String parent, List<VRMenuInfo> autowiredCustomMenusByCtrl) {
        List<VRMenuInfo> ok = new ArrayList<>();
        if (parent == null) {
            parent = "/";
        }
        for (VRMenuInfo m : autowiredCustomMenusByCtrl) {
            String p = m.getPath();
            if (p == null) {
                p = "";
            }
            p = p.replace('.', '/');
            if (!p.startsWith("/")) {
                p = "/" + p;
            }
            if (!p.equals("/") && p.endsWith("/")) {
                p = p.substring(0, p.length() - 1);
            }
            if (p.equalsIgnoreCase(parent)) {
                ok.add(m);
            }
        }
        return ok;
    }

    private List<String> findCustomPaths(String parent, List<VRMenuInfo> autowiredCustomMenusByCtrl) {
        LinkedHashSet<String> ok = new LinkedHashSet<>();
        if (parent == null) {
            parent = "/";
        }
        String parent2 = parent;
        if (!parent2.endsWith("/")) {
            parent2 = parent2 + "/";
        }
        for (VRMenuInfo m : autowiredCustomMenusByCtrl) {
            String p = m.getPath();
            if (p == null) {
                p = "";
            }
            p = p.replace('.', '/');
            if (!p.startsWith("/")) {
                p = "/" + p;
            }
            if (p.toLowerCase().startsWith(parent2.toLowerCase()) && !p.toLowerCase().equals(parent2.toLowerCase())) {
                String p2 = p.substring(parent2.length() - 1);
                if (p2.length() > 0) {
                    int c = p2.indexOf("/", 1);
                    String path = null;
                    if (c < 0) {
                        path = parent2 + p2.substring(1);
                    } else {
                        path = parent2 + p2.substring(1, c);
                    }
                    if (!ok.contains(path)) {
                        ok.add(path);
                    }
                }
            }
        }
        return new ArrayList<>(ok);
    }

    private List<Entity> filterEntities(List<Entity> in) {
        return filterEntities(in, new DefaultEntityFilter().setAcceptSystem(false));
    }

    private List<Entity> filterEntities(List<Entity> in, EntityFilter f) {
        ArrayList<Entity> out = new ArrayList<>();
        for (Entity i : in) {
            if (f.accept(i)) {
                out.add(i);
            }
        }
        return out;
    }

    public VrControllerInfo resolveBestVrControllerInfo(Class type, String cmd) {
        VrControllerInfo o = null;
        for (Object profileAlternative : VrApp.getBeansForType(type)) {
            VrControllerInfo b = resolveVrControllerInfoByInstance(profileAlternative, null, cmd);
            if (b != null) {
                if (b.getEnabler() == null || b.getEnabler().isEnabled(null)) {
                    if (o == null || o.getPriority() <= b.getPriority()) {
                        o = b;
                    }
                }
            }
        }
        return o;
    }

    public VrControllerInfo resolveVrControllerInfoByInstance(Object obj, String typeName, String cmd) {
        Class targetClass = PlatformReflector.getTargetClass(obj);
        if (obj instanceof VrControllerInfoResolver) {
            VrControllerInfoResolver up = (VrControllerInfoResolver) obj;
            return up.resolveVrControllerInfo(cmd);
        }
        if (StringUtils.isEmpty(typeName)) {
            char[] chars = targetClass.getSimpleName().toCharArray();
            chars[0] = Character.toLowerCase(chars[0]);
            typeName = new String(chars);

        }
        VrController c2Ann = (VrController) targetClass.getAnnotation(VrController.class);
        if (c2Ann != null) {
            return resolveVrControllerInfoByAnnotation(obj, c2Ann, typeName);
        }
        return null;
    }

    public VrControllerInfo resolveVrControllerInfoByAnnotation(Object obj, VrController c2Ann, String typeName) {
        if (c2Ann != null) {
            String uniformCtrlNameBase = typeName;
            String uniformCtrlName = typeName;
            if (uniformCtrlName.endsWith("Ctrl") && uniformCtrlName.length() > "Ctrl".length()) {
                uniformCtrlName = uniformCtrlName.substring(0, uniformCtrlName.length() - "Ctrl".length());
            }
            VrControllerInfo d = new VrControllerInfo();

            String title = I18n.get().getOrNull("Controller." + uniformCtrlNameBase);
            if (StringUtils.isEmpty(title)) {
                title = I18n.get().getOrNull("Controller." + uniformCtrlName);
            }
            if (StringUtils.isEmpty(title)) {
                title = c2Ann.title();
            }
            if (StringUtils.isEmpty(title)) {
                title = "Controller." + uniformCtrlName;
            }
            d.setTitle(title);

            String subTitle = I18n.get().getOrNull("Controller." + uniformCtrlNameBase + ".subTitle");
            if (StringUtils.isEmpty(subTitle)) {
                subTitle = I18n.get().getOrNull("Controller." + uniformCtrlName + ".subTitle");
            }
            if (StringUtils.isEmpty(subTitle)) {
                subTitle = c2Ann.subTitle();
            }
            d.setSubTitle(subTitle);

            String css = I18n.get().getOrNull("Controller." + uniformCtrlNameBase + ".css");
            if (StringUtils.isEmpty(css)) {
                css = I18n.get().getOrNull("Controller." + uniformCtrlName + ".css");
            }
            if (StringUtils.isEmpty(css)) {
                css = c2Ann.css();
            }
            d.setCss(css);

            String url = VrWebHelper.evalSpringExprMessage(c2Ann.url());
            d.setUrl(url);
            d.setMenuPath(c2Ann.menu());
            d.setSource(typeName);
            d.setSecurityKey(c2Ann.securityKey());
            List<BreadcrumbItem> aa = new ArrayList<>();
            for (UPathItem breadcrumb : c2Ann.breadcrumb()) {
                title = breadcrumb.title();
                subTitle = breadcrumb.subTitle();
                css = breadcrumb.css();
                String ctrl = breadcrumb.ctrl();
                String cmd2 = breadcrumb.cmd();
                if (!StringUtils.isEmpty(ctrl)) {
                    VrControllerInfo d2 = resolveVrControllerInfo(ctrl, cmd2);
                    if (d2 != null) {
                        if (StringUtils.isEmpty(css)) {
                            css = d2.getCss();
                        }
                        if (StringUtils.isEmpty(title)) {
                            title = d2.getTitle();
                        }
                        if (StringUtils.isEmpty(subTitle)) {
                            subTitle = d2.getSubTitle();
                        }
                    }
                }
                aa.add(new BreadcrumbItem(title, subTitle, css, ctrl, cmd2));
            }
            d.setBreadcrumb(aa.toArray(new BreadcrumbItem[aa.size()]));
            if (obj instanceof VrActionEnabler) {
                d.setEnabler((VrActionEnabler) obj);
            }
            return d;
        }
        return null;
    }

    public VrControllerInfo resolveVrControllerInfo(String ctrl0, String cmd) {
        return resolveVrControllerInfoByInstance(getPageCtrl(ctrl0), null, cmd);
    }

    private String resolveGoodBeanName(Object o) {
        StringBuilder ctrl0 = new StringBuilder(PlatformReflector.getTargetClass(o).getSimpleName());
        ctrl0.setCharAt(0, Character.toLowerCase(ctrl0.charAt(0)));
        return ctrl0.toString();
    }

    //    public String gotoPage(Class type, String cmd) {
//        Object o = VrApp.getBean(type);
//        return gotoPageByBean(o, cmd);
//    }
    public String goBack() {
        if (pageHistory.size() > 0) {
            pageHistory.removeLast();
            if (pageHistory.size() > 0) {
                PageInfo last = pageHistory.getLast();
                return gotoPage(last.getCommand(), last.getArguments(), false);
            }
        }
        return null;
    }

    public String gotoPage(String command, String arguments) {
        return gotoPage(command, arguments, true);
    }

    public String gotoPageObjItem(String entity, String id) {
        ObjCtrl.Config c = new ObjCtrl.Config();
        c.entity = entity;
        c.id = id;
        return gotoPage("obj", VrUtils.formatJSONObject(c));
    }

    private String gotoPage(String command, String arguments, boolean addToHistory) {
        Object o = getPageCtrl(command);
        VrWebHelper.prepareUserSession();
        String goodBeanName = resolveGoodBeanName(o);
        VrControllerInfo d = resolveVrControllerInfo(goodBeanName, arguments);
        setPageCtrl(o);
        List<BreadcrumbItem> bc = new ArrayList<>();
        String url = null;
        if (d != null) {
            bc.addAll(Arrays.asList(d.getBreadcrumb()));
            url = d.getUrl();
            if (!StringUtils.isEmpty(d.getSecurityKey())) {
                if (!UPA.getPersistenceUnit().getSecurityManager().isAllowedKey(d.getSecurityKey())) {
                    if ("publicIndex".equals(command)) {
                        throw new SecurityException("Page is Inaccessible");
                    } else {
                        log.warning("Illegal Access to " + command + " by " + core.getCurrentUser());
                        FacesUtils.addErrorMessage("Illegal Access to " + command + " by " + core.getCurrentUser());
                        if (core.getCurrentUser() != null) {
                            return gotoPage("welcome", "");
                        } else {
                            return gotoPage("publicIndex", "");
                        }
                    }
                }
            }
            if (d.getEnabler() != null) {
                if (!d.getEnabler().isEnabled(d)) {
                    log.warning("Illegal Access to " + command + " by " + core.getCurrentUser());
                    return gotoPage("publicIndex", "");
                }
            }
        }
        setPageCtrl(o);
        PlatformReflector.InstanceInvoker[] mm = PlatformReflector.findInstanceByAnnotation(o, OnPageLoad.class);
        for (PlatformReflector.InstanceInvoker m : mm) {
            if (m.getParameterTypes().length == 0) {
                m.invoke();
            } else if (m.getParameterTypes().length == 1) {
                m.invoke(VrUtils.parseJSONObject(arguments, m.getParameterTypes()[0]));
            }
        }
        if (d != null) {
            bc.add(new BreadcrumbItem(d.getTitle(), d.getSubTitle(), d.getCss(), "", ""));
        } else {
            bc.add(new BreadcrumbItem("", "", "", "", ""));
        }
        getModel().setBreadcrumb(bc);
        UserSession s = CorePlugin.get().getCurrentSession();
        StringBuilder lvp = new StringBuilder();
        for (BreadcrumbItem breadcrumbItem : bc) {
            if (!StringUtils.isEmpty(breadcrumbItem.getTitle())) {
                if (lvp.length() > 0) {
                    lvp.append(", ");
                }
                lvp.append(breadcrumbItem.getTitle());
            }
        }
        String data = command;
        if (!StringUtils.isEmpty(arguments)) {
            data += " ; " + arguments;
        }
        TraceService.get().trace("visit-page", "page visited " + lvp.toString(), data, "/System/Access", Level.FINE);
        if (StringUtils.isEmpty(url)) {
            return null;
        }
        if (addToHistory) {
            pushHistory(command, arguments);
        }
        return path(url);
    }

    public void pushHistory(String command, Object arguments) {
        if (arguments == null) {
            arguments = "";
        } else if (arguments instanceof String) {
            //okkay
        } else {
            arguments = VrUtils.formatJSONObject(arguments);
        }
        PageInfo pi = new PageInfo(command, String.valueOf(arguments));
        if (pageHistory.size() > 0 && pageHistory.getLast().equals(pi)) {
            return;
        }
        pageHistory.add(pi);
        if (pageHistory.size() > 20) {
            pageHistory.removeFirst();
        }
    }

    //    public String invoke(String name) {
//        try {
//            return (String) getClass().findPlatformMethod(name).invoke(this);
//        } catch (Exception ex) {
//            Logger.getLogger(VrMenu.class.getName()).log(Level.SEVERE, null, ex);
//            return null;
//        }
//    }
//
//    public String gotoLaboMyLaboTodos() {
//        TodoCtrl todo = VRApp.getBean(TodoCtrl.class);
//        getModel().setBreadcrumb(
//                new BreadcrumbItem("Dashboard", "fa-dashboard", "gotoDashboardMain"), new BreadcrumbItem("Labo", "fa-dashboard", "gotoDashboardLabo"), new BreadcrumbItem("Mes Actions", "fa-table", null)
//        );
//        todo.getModel().setListName(TodoList.LABO_ACTION);
//        todo.reloadPage(null);
//        return path("modules/todo/todos");
//    }
//
//    public String gotoLaboMyLaboTickets() {
//        final TodoCtrl todo = VRApp.getBean(TodoCtrl.class);
//        getModel().setBreadcrumb(
//                new BreadcrumbItem("Dashboard", "fa-dashboard", "gotoDashboardMain"), new BreadcrumbItem("Labo", "fa-dashboard", "gotoDashboardLabo"), new BreadcrumbItem("Mes Tickets", "fa-table", null)
//        );
//        todo.getModel().setListName(TodoList.LABO_TICKET);
//        todo.reloadPage(null);
//        return "modules/todo/todo.xhtml";
//    }
    public String path(String p) {
        String r = Vr.get().getFacesContextPrefix();
        return "/" + r + "/" + p + "?faces-redirect=true";
    }

    public Model getModel() {
        return model;
    }

    @PostConstruct
    public void initCustomMenus() {
        final CorePlugin core = VrApp.getBean(CorePlugin.class);
        UPA.getPersistenceUnit().invokePrivileged(new VoidAction() {
            @Override
            public void run() {
                for (String beanName : VrApp.getContext().getBeanNamesForAnnotation(VrController.class)) {
                    if ("vrMenuManager".equals(beanName)) {
                        continue; //unless we have unresolvable circular reference
                    }
                    Object b = VrApp.getContext().getBean(beanName);
                    VrController c = (VrController) PlatformReflector.getTargetClass(b).getAnnotation(VrController.class);
                    if (c != null) {
                        final String securityKey = c.securityKey();
                        final String[] securityKeys = c.declareSecurityKeys();
                        UPA.getContext().invokePrivileged(new VoidAction() {
                            @Override
                            public void run() {
                                if (!StringUtils.isEmpty(securityKey)) {
                                    core.createRight(securityKey, securityKey);
                                }
                                for (String key : securityKeys) {
                                    if (!StringUtils.isEmpty(key)) {
                                        core.createRight(key, key);
                                    }
                                }
                            }
                        });

                    }

                    if (b instanceof VRMenuProvider) {
                        for (VRMenuInfo md : ((VRMenuProvider) b).createCustomMenus()) {

                            if (md != null) {
                                String securityKey = md.getSecurityKey();
                                if (!StringUtils.isEmpty(securityKey)) {
                                    core.createRight(securityKey, securityKey);
                                }
                                applyVRMenuInfoSource(md, beanName);
                            }
                        }
                    }
                }

                ActionDialogManager actionDialogManager = VrApp.getBean(ActionDialogManager.class);
                for (Entity entity : UPA.getPersistenceUnit().getEntities()) {
                    for (ActionDialogAdapter a : actionDialogManager.findActionsByEntity(entity.getName())) {
                        if (!StringUtils.isEmpty(a.getId())) {
                            core.createRight(a.getId(), a.getId());
                        }
                    }
                }
            }

        });

    }

    private void applyVRMenuInfoSource(VRMenuInfo m, String source) {
        if (m == null) {
            return;
        }
        if (m.getSource() == null) {
            m.setSource(source);
        }
        List<VRMenuInfo> children = m.getChildren();
        if (children != null) {
            for (VRMenuInfo c : children) {
                applyVRMenuInfoSource(c, source);
            }
        }
    }

    public List<VRMenuInfo> resolveAutowiredCustomMenusByCtrl() {
        List<VRMenuInfo> menuCtrl = new ArrayList<>();
        for (String beanName : VrApp.getContext().getBeanNamesForAnnotation(VrController.class)) {
            Object b = VrApp.getContext().getBean(beanName);
            VrController c = (VrController) PlatformReflector.getTargetClass(b).getAnnotation(VrController.class);
            if (c != null && c.menu().length() > 0) {
                String menu = c.menu();
                if (!menu.startsWith("/")) {
                    menu = "/" + menu;
                }
                String title = I18n.get().getOrNull("Controller." + beanName);
                if (StringUtils.isEmpty(title)) {
                    title = c.title();
                }
                VrActionEnabler actionEnabler = null;
                if (b instanceof VrActionEnabler) {
                    actionEnabler = (VrActionEnabler) b;
                }
                VRMenuInfo md = new VRMenuInfo(title, menu, beanName, "", c.securityKey(), actionEnabler, "", c.order(), new VRMenuLabel[0]);
                VrControllerInfo uc = resolveVrControllerInfo(md.getType(), md.getCommand());
                if (uc != null) {
                    md.setName(uc.getTitle());
                }
                if (md.getSecurityKey() == null
                        || md.getSecurityKey().length() == 0
                        || UPA.getPersistenceGroup().getSecurityManager().isAllowedKey(md.getSecurityKey())) {
                    if (md.getEnabler() == null || md.getEnabler().isEnabled(md)) {
                        menuCtrl.add(md);
                    }
                }
            }
            if (b instanceof VRMenuProvider) {
                for (VRMenuInfo md : ((VRMenuProvider) b).createCustomMenus()) {
                    if (md != null) {
                        applyVRMenuInfoSource(md, beanName);
                        if (md.getSecurityKey() == null
                                || md.getSecurityKey().length() == 0
                                || UPA.getPersistenceGroup().getSecurityManager().isAllowedKey(md.getSecurityKey())) {
                            VrControllerInfo uc = resolveVrControllerInfo(md.getType(), md.getCommand());
                            if (uc != null) {
                                md.setName(uc.getTitle());
                                if (uc.getEnabler() != null) {
                                    md.setEnabler(uc.getEnabler());
                                }
                            }
                            menuCtrl.add(md);
                        }
                    }
                }
            }
        }
        return menuCtrl;
    }

    public List<PageInfo> getPageHistory() {
        return pageHistory;
    }

    public static class Model {

        private List<BreadcrumbItem> breadcrumb = new ArrayList<BreadcrumbItem>();
        private BreadcrumbItem titleCrumb = new BreadcrumbItem("", "", "", "", "");
        private List<VRMenuInfo> menuCtrl = null;
        private String currentPageId;
        private VRMenuInfo root;
        private String searchText;

        public String getSearchText() {
            return searchText;
        }

        public void setSearchText(String searchText) {
            this.searchText = searchText;
        }

        public VRMenuInfo getRoot() {
            return root;
        }

        public Model setRoot(VRMenuInfo root) {
            this.root = root;
            return this;
        }

        public List<BreadcrumbItem> getBreadcrumb() {
            return breadcrumb;
        }

        public void setBreadcrumb(List<BreadcrumbItem> breadcrumb) {
            this.breadcrumb = breadcrumb;
            for (int i = 0; i < breadcrumb.size(); i++) {
                breadcrumb.get(i).setActive(i == breadcrumb.size() - 1);
            }
            if (this.breadcrumb.isEmpty()) {
                titleCrumb = new BreadcrumbItem("", "", "", "", "");
                titleCrumb.setActive(false);
            } else {
                titleCrumb = breadcrumb.get(breadcrumb.size() - 1);
            }
        }

        public void setBreadcrumb(BreadcrumbItem... breadcrumb) {
            setBreadcrumb(Arrays.asList(breadcrumb));
        }

        public String getCurrentPageId() {
            return currentPageId;
        }

        public void setCurrentPageId(String currentPageId) {
            this.currentPageId = currentPageId;
        }

        public BreadcrumbItem getTitleCrumb() {
            return titleCrumb;
        }

        public List<VRMenuInfo> getMenuCtrl() {
            return menuCtrl;
        }
    }

    //    public List<VRMenuDef> getAutowiredCustomMenusByCtrl() {
//        if (getModel().getMenuCtrl() == null) {
//            getModel().menuCtrl = resolveAutowiredCustomMenusByCtrl();
//        }
//        return getModel().getMenuCtrl();
//    }
    private static class MenuTreeVisitor implements TreeVisitor<VRMenuInfo> {

        private ObjectFilter<String> filter;

        public MenuTreeVisitor(ObjectFilter<String> filter) {
            this.filter = filter;
        }

        @Override
        public void visit(VRMenuInfo t, TreeDefinition<VRMenuInfo> tree) {
            List<VRMenuInfo> children = tree.getChildren(t);
            for (int i = children.size() - 1; i >= 0; i--) {
                VRMenuInfo el = children.get(i);
                if (el.getType().equals("package") && el.getChildren().isEmpty()) {
                    children.remove(i);
                } else {
                    if (el.getChildren().size() == 0) {
                        if (!filter.accept(el.getName())) {
                            children.remove(i);
                        }
                    }
                }
            }
        }
    }

    private class MenuTree implements TreeDefinition<VRMenuInfo> {

        final VRMenuInfo root = new VRMenuInfo("/", "/", "package", "", "", null, "", 0, new VRMenuLabel[0]);
        final List<VRMenuInfo> autowiredCustomMenusByCtrl = resolveAutowiredCustomMenusByCtrl();
        final HashSet<VRMenuInfo> nonVisitedCustomMenus = new HashSet<>(autowiredCustomMenusByCtrl);
        final HashSet<VRMenuInfo> visitedCustomMenus = new HashSet<>();

        public MenuTree(ObjectFilter<String> filter) {
            TreeTraversal.postOrderTreeTraversal(this, new MenuTreeVisitor(filter));
        }

        @Override
        public VRMenuInfo getRoot() {
            return root;
        }

        @Override
        public List<VRMenuInfo> getChildren(VRMenuInfo t) {
            if (t.getChildren() == null) {
                ArrayList<VRMenuInfo> children = new ArrayList<VRMenuInfo>();
                if (t.getType().equals("package")) {
                    Package pp = UPA.getPersistenceUnit().getPackage(t.getPath(), MissingStrategy.NULL);
                    List<Package> pk = null;
                    if (pp == null) {
                        pk = new ArrayList<>();
                    } else {
                        pk = pp.getPackages();
                    }
                    HashSet<String> subFolders = new HashSet<String>();
                    for (Package p : pk) {
                        subFolders.add(p.getPath());
                        String p0 = p.getPath();
                        if (!p0.startsWith("/")) {
                            p0 = "/" + p0;
                        }
                        String p1 = p0;
                        p1 = p1.replace('/', '.');
                        if (p1.startsWith(".")) {
                            p1 = p1.substring(1);
                        }
                        String orderString = i18n.getOrNull("Package." + p1 + ".order");
                        int order = 100;
                        try {
                            if (!StringUtils.isEmpty(orderString)) {
                                order = Integer.parseInt(orderString);
                            }
                        } catch (Exception e) {
                            //ignore
                        }
                        children.add(new VRMenuInfo(p.getTitle(), p0, "package", "", "", null, i18n.getOrNull("Package." + p1 + ".css-icon-class"), order, new VRMenuLabel[0]));
                    }
                    for (String p : findCustomPaths(t.getPath(), autowiredCustomMenusByCtrl)) {
                        if (!subFolders.contains(p)) {
                            subFolders.add(p);
                            String p0 = p;
                            if (!p0.startsWith("/")) {
                                p0 = "/" + p0;
                            }
                            String p1 = p0;
                            p1 = p1.replace('/', '.');
                            if (p1.startsWith(".")) {
                                p1 = p1.substring(1);
                            }
                            String orderString = i18n.getOrNull("Package." + p1 + ".order");
                            int order = 100;
                            try {
                                if (!StringUtils.isEmpty(orderString)) {
                                    order = Integer.parseInt(orderString);
                                }
                            } catch (Exception e) {
                                //ignore
                            }

                            children.add(new VRMenuInfo(i18n.get("Package." + p1), p0, "package", "", "", null, i18n.getOrNull("Package." + p1 + ".css-icon-class"), order, new VRMenuLabel[0]));
                        }
                    }
                    for (VRMenuInfo p : findCustomMenus(t.getPath(), autowiredCustomMenusByCtrl)) {
                        nonVisitedCustomMenus.remove(p);
                        if (visitedCustomMenus.contains(p)) {
                            System.err.println("Menu added twice " + p);
                        } else {
                            visitedCustomMenus.add(p);
                            nonVisitedCustomMenus.add(p);
                        }
                        children.add(p);
                    }
                    if (t.getPath().equals("/")) {
                        List<Entity> entities = filterEntities(UPA.getPersistenceUnit().getDefaultPackage().getEntities(), new DefaultEntityFilter().setAcceptSystem(false));
                        for (Entity ee : entities) {
                            if (ee.getCompositionRelation() == null) {
                                try {
                                    ee.getShield().checkNavigate();
                                    if (UPA.getPersistenceGroup().getSecurityManager().isAllowedKey(
                                            CorePluginSecurity.getEntityRightEditor(ee))) {
                                        int order = 100;
                                        //UCtrlData d = ;
                                        VRMenuInfo md = new VRMenuInfo(ee.getTitle(), t.getPath(), "obj", "{entity:'" + ee.getName() + "'}", "", null, "", 100, new VRMenuLabel[0]);
                                        VrControllerInfo uc = resolveVrControllerInfo(md.getType(), md.getCommand());
                                        if (uc != null) {
                                            md.setName(uc.getTitle());
                                        }
                                        children.add(md);
                                    }
                                } catch (UPAException e) {
                                    //ignore
                                }
                            }
                        }
                    } else {
                        Package pp2 = UPA.getPersistenceUnit().getPackage(t.getPath(), MissingStrategy.NULL);
                        //should chek for deep in level 3!
                        if (pp2 != null) {
                            List<Entity> entities = filterEntities(pp2.getEntities(), new DefaultEntityFilter().setAcceptSystem(false));
                            for (Entity ee : entities) {
                                if (ee.getCompositionRelation() == null || ee.getCompositionRelation().getHierarchyExtension() != null) {
                                    try {
                                        ee.getShield().checkNavigate();
                                        if (UPA.getPersistenceGroup().getSecurityManager().isAllowedKey(
                                                CorePluginSecurity.getEntityRightEditor(ee))) {
                                            int order = 100;
                                            VRMenuInfo md = new VRMenuInfo(ee.getTitle(), t.getPath(), "obj", "{entity:'" + ee.getName() + "'}", "", null, "", 100, new VRMenuLabel[0]);
                                            VrControllerInfo uc = resolveVrControllerInfo(md.getType(), md.getCommand());
                                            if (uc != null) {
                                                md.setName(uc.getTitle());
                                            }
                                            children.add(md);
                                        }
                                    } catch (UPAException e) {
                                        //ignore
                                    }
                                }
                            }
                        }
                    }
                }
                t.setChildren(children);
            }
            return t.getChildren();
        }

    }
}
