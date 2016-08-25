/*
 * To change this license header, choose License Headers in Project Properties.
 *
 * and open the template in the editor.
 */
package net.vpc.app.vainruling.plugins.academic.web.load;

import net.vpc.app.vainruling.core.service.CorePlugin;
import net.vpc.app.vainruling.core.service.VrApp;
import net.vpc.app.vainruling.core.service.model.AppDepartment;
import net.vpc.app.vainruling.core.service.model.AppPeriod;
import net.vpc.app.vainruling.core.service.model.AppUser;
import net.vpc.app.vainruling.core.service.security.UserSession;
import net.vpc.app.vainruling.core.service.util.VrHelper;
import net.vpc.app.vainruling.core.web.OnPageLoad;
import net.vpc.app.vainruling.core.web.menu.VrMenuManager;
import net.vpc.app.vainruling.core.web.obj.ObjCtrl;
import net.vpc.app.vainruling.plugins.academic.service.AcademicPlugin;
import net.vpc.app.vainruling.plugins.academic.service.CourseAssignmentFilter;
import net.vpc.app.vainruling.plugins.academic.service.StatCache;
import net.vpc.app.vainruling.plugins.academic.service.model.config.AcademicSemester;
import net.vpc.app.vainruling.plugins.academic.service.model.config.AcademicTeacher;
import net.vpc.app.vainruling.plugins.academic.service.model.current.*;
import net.vpc.app.vainruling.plugins.academic.service.model.stat.DeviationConfig;
import net.vpc.app.vainruling.plugins.academic.service.model.stat.TeacherPeriodStat;
import net.vpc.common.jsf.FacesUtils;
import net.vpc.common.util.Chronometer;

import javax.faces.model.SelectItem;
import java.util.*;

/**
 * @author taha.bensalah@gmail.com
 */
public abstract class AbstractCourseLoadCtrl {

    protected Model model = new Model();
    protected TeacherLoadFilterComponent teacherFilter = new TeacherLoadFilterComponent();
    protected CourseLoadFilterComponent courseFilter = new CourseLoadFilterComponent();

    public AbstractCourseLoadCtrl() {
    }

    public TeacherLoadFilterComponent getTeacherFilter() {
        return teacherFilter;
    }

    public CourseLoadFilterComponent getCourseFilter() {
        return courseFilter;
    }

    private void reset() {
        getModel().setMineS1(new ArrayList<AcademicCourseAssignmentInfo>());
        getModel().setMineS2(new ArrayList<AcademicCourseAssignmentInfo>());
        getModel().setOthers(new ArrayList<AcademicCourseAssignmentInfo>());
        getModel().setAll(new HashMap<Integer, AcademicCourseAssignmentInfo>());
        TeacherPeriodStat teacherStat = new TeacherPeriodStat();
        teacherStat.setTeacher(new AcademicTeacher());

        getModel().setStat(teacherStat);
    }

    public abstract AcademicTeacher getCurrentTeacher();

    public void onRefreshFiltersChanged() {
        onRefresh();
    }

    @OnPageLoad
    public void onRefresh(String cmd) {
        CorePlugin core = VrApp.getBean(CorePlugin.class);
        List<AppPeriod> navigatablePeriods = core.findNavigatablePeriods();
        AppPeriod mainPeriod = core.findAppConfig().getMainPeriod();
        getTeacherFilter().onInit();
        getCourseFilter().onInit();
        onChangePeriod();
    }

    public boolean isFiltered(String value) {
        return Arrays.asList(getModel().getOthersFilters()).indexOf(value) >= 0;
    }

    public Set<Integer> getFilters(String filterType) {
        HashSet<Integer> all = new HashSet<>();
        for (String f : getModel().getOthersFilters()) {
            if (f.startsWith(filterType + ":")) {
                String idString = f.substring((filterType + ":").length());
                all.add(Integer.parseInt(idString));
            }
        }
        return all;
    }

    public String gotoCourseAssignment(AcademicCourseAssignment a) {
        if (a != null) {
            ObjCtrl.Config c = new ObjCtrl.Config();
            c.entity = "AcademicCourseAssignment";
            c.id = String.valueOf(a.getId());
            return VrApp.getBean(VrMenuManager.class).gotoPage("obj", VrHelper.formatJSONObject(c));
        }
        return null;
    }


    public void onChangePeriod() {
        AcademicPlugin ap = VrApp.getBean(AcademicPlugin.class);
        AppDepartment userDepartment = getUserDepartment();
        int periodId = getTeacherFilter().getPeriodId();

        getModel().setEnableLoadEditing(
                (userDepartment == null || periodId<0)? false :
                        ap.getAppDepartmentPeriodRecord(periodId, userDepartment.getId()).getBoolean("enableLoadEditing", false)
        );

        getTeacherFilter().onChangePeriod();
        getCourseFilter().onChangePeriod();
        onChangeOther();
    }




    public void onChangeOther() {
        onRefresh();
    }

    public void onRefresh() {
        Chronometer chronometer=new Chronometer();
        int periodId = getTeacherFilter().getPeriodId();
        AcademicPlugin a = VrApp.getBean(AcademicPlugin.class);
        List<SelectItem> allValidFilters = new ArrayList<>();
        allValidFilters.add(FacesUtils.createSelectItem("assigned", "Modules Affectés", "vr-checkbox"));
        allValidFilters.add(FacesUtils.createSelectItem("non-assigned", "Modules Non Affectés", "vr-checkbox"));
        allValidFilters.add(FacesUtils.createSelectItem("intended", "Modules Demandés", "vr-checkbox"));
        allValidFilters.add(FacesUtils.createSelectItem("non-intended", "Modules Non Demandés", "vr-checkbox"));
        allValidFilters.add(FacesUtils.createSelectItem("conflict", "Modules En Conflits", "vr-checkbox"));
        getModel().setFilterSelectItems(allValidFilters.toArray(new SelectItem[allValidFilters.size()]));

        AcademicTeacher t = getCurrentTeacher();
        getModel().setCurrentTeacher(t);
        boolean assigned = isFiltered("assigned");
        boolean nonassigned = isFiltered("non-assigned");
        boolean intended = isFiltered("intended");
        boolean nonintended = isFiltered("non-intended");
        boolean conflict = isFiltered("conflict");

        if (!assigned && !nonassigned) {
            assigned = true;
            nonassigned = true;
        }
        if (!intended && !nonintended) {
            intended = true;
            nonintended = true;
        }

        StatCache cache = new StatCache();

        reset();

        Map<Integer, AcademicCourseAssignmentInfo> all = new HashMap<>();
        CourseAssignmentFilter courseAssignmentFilter = getCourseFilter().getCourseAssignmentFilter();
        DeviationConfig deviationConfig = getCourseFilter().getDeviationConfig();
        for (AcademicCourseAssignmentInfo b : a.findCourseAssignmentsAndIntents(periodId, null, null, courseAssignmentFilter, cache)) {
            all.put(b.getAssignment().getId(), b);
        }
        getModel().setAll(all);
        HashSet<Integer> visited = new HashSet<Integer>();
        if (t != null) {
            List<AcademicSemester> semesters = a.findSemesters();
            getModel().setMineS1(a.findCourseAssignmentsAndIntents(periodId, t.getId(), semesters.get(0).getId(), courseAssignmentFilter, cache));
            getModel().setMineS2(a.findCourseAssignmentsAndIntents(periodId, t.getId(), semesters.get(1).getId(), courseAssignmentFilter, cache));
            List<AcademicCourseAssignment> mine = new ArrayList<>();
            for (AcademicCourseAssignmentInfo m : getModel().getMineS1()) {
                mine.add(m.getAssignment());
                visited.add(m.getAssignment().getId());
            }
            for (AcademicCourseAssignmentInfo m : getModel().getMineS2()) {
                mine.add(m.getAssignment());
                visited.add(m.getAssignment().getId());
            }
            getModel().setStat(a.evalTeacherStat(periodId, t.getId(), courseAssignmentFilter,true, deviationConfig,cache));
        }

        List<AcademicCourseAssignmentInfo> others = new ArrayList<>();
        for (AcademicCourseAssignmentInfo c : a.findCourseAssignmentsAndIntents(periodId, null, null, courseAssignmentFilter, cache)) {
            if (!visited.contains(c.getAssignment().getId())) {
                boolean _assigned = c.isAssigned();
                HashSet<String> s = new HashSet<>(c.getIntentsSet());
                boolean _intended = s.size() > 0;
                boolean accepted = true;
                if (((assigned && _assigned) || (nonassigned && !_assigned))
                        && ((intended && _intended) || (nonintended && !_intended))) {
                    //ok
                } else {
                    accepted = false;
                }
                if (accepted && conflict) {
                    //show only with conflicts
                    if (c.getIntentsSet().isEmpty()) {
                        accepted = false;
                    } else if (c.getAssignment().getTeacher() != null) {
                        accepted = (c.getIntentsSet().size() == 1
                                && !c.getAssignment().getTeacher().getContact().getFullName().equals(c.getIntentsSet().toArray()[0]))
                                || c.getIntentsSet().size() > 1;
                    } else {
                        accepted = c.getIntentsSet().size() > 1;
                    }
                }
                if (accepted) {
                    others.add(c);
                }
            }
        }
        getModel().setOthers(others);
        chronometer.stop();
        System.out.println(chronometer);
    }

    public void assignmentsToIntentsAll() {
        if (getModel().isEnableLoadEditing()) {
            ArrayList<AcademicCourseAssignmentInfo> assignmentInfos = new ArrayList<>();
            assignmentInfos.addAll(getModel().getMineS1());
            assignmentInfos.addAll(getModel().getMineS2());
            assignmentInfos.addAll(getModel().getOthers());
            AcademicPlugin a = VrApp.getBean(AcademicPlugin.class);
            for (AcademicCourseAssignmentInfo aa : assignmentInfos) {
                AcademicTeacher t = aa.getAssignment().getTeacher();
                if (t != null) {
                    a.addIntent(t.getId(), aa.getAssignment().getId());
                    a.removeTeacherAcademicCourseAssignment(aa.getAssignment().getId());
                }
            }
        }
    }

    public void assignmentsToIntentsMine() {
        ArrayList<AcademicCourseAssignmentInfo> a = new ArrayList<>();
        a.addAll(getModel().getMineS1());
        a.addAll(getModel().getMineS2());
        for (AcademicCourseAssignmentInfo aa : a) {
            addToMine(aa.getAssignment().getId());
            doUnAssign(aa.getAssignment().getId());
        }
    }

    public void addToMine(Integer assignementId) {
        AcademicPlugin a = VrApp.getBean(AcademicPlugin.class);
        AcademicTeacher t = getModel().getCurrentTeacher();
        if (t != null && assignementId != null) {
            a.addIntent(t.getId(), assignementId);
        }
        onRefresh();
    }

    public void removeFromMine(Integer assignementId) {
        AcademicPlugin a = VrApp.getBean(AcademicPlugin.class);
        AcademicTeacher t = getModel().getCurrentTeacher();
        if (t != null && assignementId != null) {
            a.removeIntent(t.getId(), assignementId);
        }
        onRefresh();
    }

    public void removeAllIntents(Integer assignementId) {
        AcademicPlugin a = VrApp.getBean(AcademicPlugin.class);
        if (assignementId != null) {
            a.removeAllIntents(assignementId);
        }
        onRefresh();
    }

    public void doUnAssign(Integer assignementId) {
        AcademicPlugin a = VrApp.getBean(AcademicPlugin.class);
        AcademicTeacher t = getModel().getCurrentTeacher();
        if (t != null && assignementId != null) {
            a.removeTeacherAcademicCourseAssignment(assignementId);
        }
        onRefresh();
    }

    public void doAssign(Integer assignementId) {
        AcademicPlugin a = VrApp.getBean(AcademicPlugin.class);
        AcademicTeacher t = getModel().getCurrentTeacher();
        if (t != null && assignementId != null) {
            a.addTeacherAcademicCourseAssignment(t.getId(), assignementId);
        }
        onRefresh();
    }

    public Model getModel() {
        return model;
    }

    public void onOthersFiltersChanged() {
        onRefresh();
    }

    public AppDepartment getUserDepartment() {
        //enableLoadEditing
        UserSession userSession = UserSession.get();
        if (userSession == null) {
            return null;
        }
        AppUser user = userSession.getUser();
        if (user == null) {
            return null;
        }
        return user.getDepartment();
    }

    public boolean isAllowedUpdateMineIntents(Integer assignementId) {
        UserSession userSession = UserSession.get();
        if (userSession == null) {
            return false;
        }
        AppUser user = userSession.getUser();
        if (user == null) {
            return false;
        }
        if (userSession.isSuperAdmin()) {
            return true;
        }

        AppPeriod period = VrApp.getBean(CorePlugin.class).findPeriod(getTeacherFilter().getPeriodId());
        if (period == null || period.isReadOnly()) {
            return false;
        }
        AcademicPlugin a = VrApp.getBean(AcademicPlugin.class);
        AcademicTeacher teacher = a.findTeacherByUser(user.getId());
        if (teacher == null) {
            return false;
        }
        if (assignementId != null) {
            AcademicCourseAssignmentInfo t0 = getModel().getAll().get(assignementId);
            AcademicCourseAssignment t = t0 == null ? null : t0.getAssignment();
            if (t != null) {
                AppDepartment d = t.getOwnerDepartment();
                if (d != null) {
                    if (userSession.allowed("Custom.Education.CourseLoadUpdateIntents")) {
                        AppDepartment d2 = user.getDepartment();
                        if (d2 != null && d2.getId() == d.getId()) {
                            return true;
                        }
                    }
                }
                d = (t.getCoursePlan() != null && t.getCoursePlan().getCourseLevel().getAcademicClass() != null
                        && t.getCoursePlan().getCourseLevel().getAcademicClass().getProgram() != null) ?
                        t.getCoursePlan().getCourseLevel().getAcademicClass().getProgram().getDepartment() : null;
                if (d != null) {
                    if (userSession.allowed("Custom.Education.CourseLoadUpdateIntents")) {
                        AppDepartment d2 = user.getDepartment();
                        if (d2 != null && d2.getId() == d.getId()) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    public boolean isAllowedUpdateMineAssignments(Integer assignementId) {
        UserSession userSession = UserSession.get();
        if (userSession == null) {
            return false;
        }
        AppUser user = userSession.getUser();
        if (user == null) {
            return false;
        }
        AppPeriod period = VrApp.getBean(CorePlugin.class).findPeriod(getTeacherFilter().getPeriodId());

        if (userSession.isSuperAdmin()) {
            return true;
        }

        if (period == null || period.isReadOnly()) {
            return false;
        }


        if (assignementId != null) {
            AcademicCourseAssignmentInfo t0 = getModel().getAll().get(assignementId);
            AcademicCourseAssignment t = t0 == null ? null : t0.getAssignment();
            if (t != null) {
                AppDepartment d = t.getOwnerDepartment();
                if (d != null) {
                    if (userSession.allowed("Custom.Education.CourseLoadUpdateAssignments")) {
                        AppDepartment d2 = userSession.getUser().getDepartment();
                        if (d2 != null && d2.getId() == d.getId()) {
                            return true;
                        }
                    }
                }
                d = (t.getCoursePlan() != null && t.getCoursePlan().getCourseLevel().getAcademicClass() != null && t.getCoursePlan().getCourseLevel().getAcademicClass().getProgram() != null) ?
                        t.getCoursePlan().getCourseLevel().getAcademicClass().getProgram().getDepartment() : null;
                if (d != null) {
                    if (userSession.allowed("Custom.Education.CourseLoadUpdateAssignments")) {
                        AppDepartment d2 = userSession.getUser().getDepartment();
                        if (d2 != null && d2.getId() == d.getId()) {
                            return true;
                        }
                    }

                }
            }
        }
        return false;
    }

    public void doAssignByIntent(Integer assignementId) {
        AcademicPlugin a = VrApp.getBean(AcademicPlugin.class);
        if (assignementId != null) {
            AcademicCourseAssignmentInfo rr = getModel().getAll().get(assignementId);
            if (rr != null) {
                final Set<Integer> s0 = rr.getIntentsUserIdsSet();
                if (s0 != null && s0.size() > 0) {
                    List<Integer> s = new ArrayList<>(s0);
                    AcademicTeacher oldTeacher = rr.getAssignment().getTeacher();
                    int newTeacherId = -1;
                    if (oldTeacher == null) {
                        newTeacherId = s.get(0);
                    } else {
                        int lastPos = s.indexOf(oldTeacher.getId());
                        if (lastPos < 0) {
                            lastPos = 0;
                        } else {
                            lastPos = (lastPos + 1) % s.size();
                        }
                        newTeacherId = s.get(lastPos);
                    }
                    a.addTeacherAcademicCourseAssignment(newTeacherId, assignementId);
                }
                onRefresh();
            }
        }
    }

    public static class Model {

        List<AcademicCourseAssignmentInfo> mineS1 = new ArrayList<>();
        List<AcademicCourseAssignmentInfo> mineS2 = new ArrayList<>();
        List<AcademicCourseAssignmentInfo> others = new ArrayList<>();
        Map<Integer, AcademicCourseAssignmentInfo> all = new HashMap<>();
        TeacherPeriodStat stat;
        boolean nonIntentedOnly = false;
        boolean enableLoadEditing = false;
        AcademicCourseAssignmentInfo selectedFromOthers = null;
        AcademicCourseAssignmentInfo selectedFromMine1 = null;
        AcademicCourseAssignmentInfo selectedFromMine2 = null;
        boolean myDisciplineOnly = true;
        String[] defaultFilters = {"situation", "degree", "valueWeek", "extraWeek", "c", "td", "tp", "pm"};
        String[] othersFilters = defaultFilters;
        SelectItem[] filterSelectItems = new SelectItem[0];

        AcademicTeacher currentTeacher;

        public AcademicTeacher getCurrentTeacher() {
            return currentTeacher;
        }

        public void setCurrentTeacher(AcademicTeacher currentTeacher) {
            this.currentTeacher = currentTeacher;
        }

        public List<AcademicCourseAssignmentInfo> getMineS1() {
            return mineS1;
        }

        public void setMineS1(List<AcademicCourseAssignmentInfo> mineS1) {
            this.mineS1 = mineS1;
        }

        public List<AcademicCourseAssignmentInfo> getMineS2() {
            return mineS2;
        }

        public void setMineS2(List<AcademicCourseAssignmentInfo> mineS2) {
            this.mineS2 = mineS2;
        }

        public Map<Integer, AcademicCourseAssignmentInfo> getAll() {
            return all;
        }

        public void setAll(Map<Integer, AcademicCourseAssignmentInfo> all) {
            this.all = all;
        }

        public List<AcademicCourseAssignmentInfo> getOthers() {
            return others;
        }

        public void setOthers(List<AcademicCourseAssignmentInfo> others) {
            this.others = others;
        }

        public TeacherPeriodStat getStat() {
            return stat;
        }

        public void setStat(TeacherPeriodStat stat) {
            this.stat = stat;
        }

        public boolean isNonIntentedOnly() {
            return nonIntentedOnly;
        }

        public void setNonIntentedOnly(boolean nonIntentedOnly) {
            this.nonIntentedOnly = nonIntentedOnly;
        }

        public boolean isMyDisciplineOnly() {
            return myDisciplineOnly;
        }

        public void setMyDisciplineOnly(boolean myDisciplineOnly) {
            this.myDisciplineOnly = myDisciplineOnly;
        }

        public AcademicCourseAssignmentInfo getSelectedFromOthers() {
            return selectedFromOthers;
        }

        public void setSelectedFromOthers(AcademicCourseAssignmentInfo selectedFromOthers) {
            this.selectedFromOthers = selectedFromOthers;
        }

        public AcademicCourseAssignmentInfo getSelectedFromMine1() {
            return selectedFromMine1;
        }

        public void setSelectedFromMine1(AcademicCourseAssignmentInfo selectedFromMine1) {
            this.selectedFromMine1 = selectedFromMine1;
        }

        public AcademicCourseAssignmentInfo getSelectedFromMine2() {
            return selectedFromMine2;
        }

        public void setSelectedFromMine2(AcademicCourseAssignmentInfo selectedFromMine2) {
            this.selectedFromMine2 = selectedFromMine2;
        }

        public String[] getOthersFilters() {
            return othersFilters;
        }

        public void setOthersFilters(String[] othersFilters) {
            this.othersFilters = (othersFilters == null || othersFilters.length == 0) ? defaultFilters : othersFilters;
        }

        public SelectItem[] getFilterSelectItems() {
            return filterSelectItems;
        }

        public void setFilterSelectItems(SelectItem[] filterSelectItems) {
            this.filterSelectItems = filterSelectItems;
        }

        public boolean isEnableLoadEditing() {
            return enableLoadEditing;
        }

        public void setEnableLoadEditing(boolean enableLoadEditing) {
            this.enableLoadEditing = enableLoadEditing;
        }

    }
}
