/*
 * To change this license header, choose License Headers in Project Properties.
 *
 * and open the template in the editor.
 */
package net.vpc.app.vainruling.plugins.academic.service;

import net.vpc.app.vainruling.core.service.*;
import net.vpc.app.vainruling.core.service.cache.CacheService;
import net.vpc.app.vainruling.core.service.model.*;
import net.vpc.app.vainruling.core.service.obj.AppEntityExtendedPropertiesProvider;
import net.vpc.app.vainruling.core.service.security.UserSession;
import net.vpc.app.vainruling.core.service.util.VrHelper;
import net.vpc.app.vainruling.plugins.academic.service.helper.AcademicConversionTableHelper;
import net.vpc.app.vainruling.plugins.academic.service.helper.CopyAcademicDataHelper;
import net.vpc.app.vainruling.plugins.academic.service.helper.TeacherGenerationHelper;
import net.vpc.app.vainruling.plugins.academic.service.model.config.*;
import net.vpc.app.vainruling.plugins.academic.service.model.current.*;
import net.vpc.app.vainruling.plugins.academic.service.model.history.*;
import net.vpc.app.vainruling.plugins.academic.service.model.imp.AcademicStudentImport;
import net.vpc.app.vainruling.plugins.academic.service.model.imp.AcademicTeacherImport;
import net.vpc.app.vainruling.plugins.academic.service.model.stat.*;
import net.vpc.common.strings.MapStringConverter;
import net.vpc.common.strings.StringComparator;
import net.vpc.common.strings.StringUtils;
import net.vpc.common.util.Chronometer;
import net.vpc.common.vfs.VFile;
import net.vpc.upa.*;
import net.vpc.upa.types.DateTime;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author taha.bensalah@gmail.com
 */
@AppPlugin()
public class AcademicPlugin implements AppEntityExtendedPropertiesProvider {

    public static final int DEFAULT_SEMESTER_MAX_WEEKS = 14;
    private static final Logger log = Logger.getLogger(AcademicPlugin.class.getName());
    @Autowired
    TraceService trace;
    @Autowired
    CorePlugin core;
    @Autowired
    CacheService cacheService;
    private CopyAcademicDataHelper copyAcademicDataHelper = new CopyAcademicDataHelper();
    private TeacherGenerationHelper teacherGenerationHelper = new TeacherGenerationHelper();

    public AcademicPlugin() {
    }

    //    protected void generatePrintableTeacherListLoadFile(int yearId, Integer[] teacherIds, String semester, String template, String output) throws IOException {
//        TeacherStat[] stats = evalTeachersStat(yearId, teacherIds, semester);
//        generateTeacherListAssignmentsSummaryFile(yearId, stats, template, output);
//    }
//    private void generatePrintableTeacherLoadSheet(int yearId, int teacherId, WritableSheet sheet) throws IOException {
//        ExcelTemplate.generateExcelSheet(sheet, preparePrintableTeacherLoadProperties(yearId,teacherId));
//    }

    public int getSemesterMaxWeeks() {
        try {
            return (Integer) core.getOrCreateAppPropertyValue("AcademicPlugin.SemesterMaxWeeks", null, DEFAULT_SEMESTER_MAX_WEEKS);
        } catch (Exception e) {
            return DEFAULT_SEMESTER_MAX_WEEKS;
        }
    }

    public void generateTeacherAssignementDocumentsFolder(int periodId) {
        generateTeacherAssignementDocumentsFolder(periodId, "/Documents/Services/Supports Pedagogiques/Par Enseignant");
    }

    public void generateTeacherAssignementDocumentsFolder(int periodId, String path) {
        for (AcademicCourseAssignment a : findAcademicCourseAssignments(periodId)) {
            if (a.getTeacher() != null && a.getTeacher().getContact() != null) {
                String n = VrHelper.toValideFileName(a.getTeacher().getContact().getFullName());
                String c = VrHelper.toValideFileName(a.getFullName());
                VFile r = core.getFileSystem().get(path + "/" + n + "/" + c);
                r.mkdirs();
            }
        }
    }

    public TeacherPeriodStat evalTeacherStat(
            int periodId,
            int teacherId,
            CourseFilter filter,
            StatCache cache) {
        AcademicTeacher teacher = cache.getAcademicTeacherMap().get(teacherId);
        if (teacher == null) {
            return null;
        }
        return evalTeacherStat(periodId, teacherId, null, null, null, filter, cache);
    }

    public TeacherPeriodStat evalTeacherStat(
            int periodId,
            int teacherId,
            AcademicTeacher teacher,
            List<AcademicTeacherSemestrialLoad> findTeacherSemestrialLoads,
            List<AcademicCourseAssignment> modules,
            CourseFilter filter,
            StatCache cache
    ) {
        Chronometer ch = new Chronometer();
        if (teacher == null) {
            teacher = cache.getAcademicTeacherMap().get(teacherId);
        }
        if (teacher == null) {
            return null;
        }
//        int teacherId = tal.getTeacher().getId();
        if (modules == null) {
            modules = findCourseAssignments(periodId, teacherId, null, filter, cache);
            if (modules == null) {
                log.severe("No assignements found for teacherId=" + teacherId + " (" + teacher + ")");
            }
        }
        TeacherPeriodStat teacher_stat = new TeacherPeriodStat();
        teacher_stat.setCourseFilter(filter);
        teacher_stat.setTeacher(teacher);
        teacher_stat.setTeacherPeriod(findAcademicTeacherPeriod(periodId, teacher_stat.getTeacher()));
        AcademicTeacherDegree degree = teacher_stat.getTeacherPeriod().getDegree();
        teacher_stat.getDueWeek().setEquiv(degree == null ? 0 : degree.getValueDU());

        boolean hasDU = teacher_stat.getDueWeek().getEquiv() > 0;
        if (!hasDU) {
            teacher_stat.getDueWeek().setEquiv(0);
            teacher_stat.getDueWeek().setEquivC(0);
            teacher_stat.getDueWeek().setEquivTD(0);
            teacher_stat.getDue().setEquiv(0);
            teacher_stat.getDue().setEquivC(0);
            teacher_stat.getDue().setEquivTD(0);
        }
        StatCache.PeriodCache periodCache = cache.forPeriod(periodId);
        if (findTeacherSemestrialLoads == null) {
            findTeacherSemestrialLoads = periodCache.getAcademicTeacherSemestrialLoadByTeacherIdMap().get(teacherId);
            if (findTeacherSemestrialLoads == null) {
                log.severe("teacherSemestrialLoads not found for teacherId=" + teacherId + " (" + teacher + ")");
                findTeacherSemestrialLoads = new ArrayList<>();
            }
        }

        teacher_stat.setSemestrialLoad(findTeacherSemestrialLoads.toArray(new AcademicTeacherSemestrialLoad[findTeacherSemestrialLoads.size()]));
        List<AcademicSemester> semesters = cache.getAcademicSemesterList();
        TeacherSemesterStat[] sems = new TeacherSemesterStat[semesters.size()];
        teacher_stat.setSemesters(sems);
        double sum_semester_weeks = 0;
        double sum_max_semester_weeks = 0;
        LoadValue teacher_extraWeek = teacher_stat.getExtraWeek();
        LoadValue teacher_extra = teacher_stat.getExtra();
        int maxWeeks = cache.getSemesterMaxWeeks();
        AcademicConversionTableHelper conversionTable = periodCache.getConversionTable();
        for (int i = 0; i < sems.length; i++) {
            AcademicSemester ss = semesters.get(i);
            TeacherSemesterStat sem = new TeacherSemesterStat();
            sem.setTeacherStat(teacher_stat);
            sem.setSemester(ss);
            int semesterWeeks = findTeacherSemestrialLoads.size() > i ? (findTeacherSemestrialLoads.get(i).getWeeksLoad()) : 0;
            sem.setWeeks(semesterWeeks);
            sem.setMaxWeeks(maxWeeks);
            LoadValue sem_value = sem.getValue();
            LoadValue sem_due = sem.getDue();
            LoadValue sem_dueWeek = sem.getDueWeek();
            LoadValue sem_extra = sem.getExtra();
            LoadValue sem_valueWeek = sem.getValueWeek();
            LoadValue sem_extraWeek = sem.getExtraWeek();

            sum_semester_weeks += sem.getWeeks();
            sum_max_semester_weeks += sem.getMaxWeeks();

            for (AcademicCourseAssignment academicCourseAssignment : modules) {
                ModuleStat ms = evalModuleStat(periodId, academicCourseAssignment.getId(), teacherId, cache);
                ModuleSemesterStat mss = ms.getSemester(ss.getName());
                sem_value.add(mss.getValue());
            }
            if (semesterWeeks == 0) {
                LoadValue zeros = new LoadValue();
                sem_valueWeek.set(zeros);
                sem_extraWeek.set(zeros);
                sem_value.setTppm(0);
                sem_valueWeek.setTppm(0);
                sem_due.setEquiv(0);
            } else {
                sem_valueWeek.set(sem_value.copy().div(semesterWeeks));
                AcademicTeacherDegree td = teacher_stat.getTeacherPeriod().getDegree();
                if (td == null) {
                    td = new AcademicTeacherDegree();
                }
                sem_extraWeek.setEquiv(sem_valueWeek.getEquiv() - td.getValueDU() * (semesterWeeks / sem.getMaxWeeks()));
                sem_extra.setEquiv(sem_value.getEquiv() - td.getValueDU() * semesterWeeks);
                AcademicTeacherDegree dd = td;
                AcademicLoadConversionRow r = conversionTable.get(dd.getConversionRule().getId());
                sem_value.setTppm(sem_value.getTp() + sem_value.getPm() * (r.getValuePM() / r.getValueTP()));
                sem_valueWeek.setTppm(sem_value.getTppm() / semesterWeeks);

                teacher_stat.getValue().add(sem_value);
                sem_dueWeek.setEquiv(td.getValueDU());
                sem_due.setEquiv(td.getValueDU() * semesterWeeks);

                if (hasDU) {
                    teacher_extra.add(sem_extraWeek.copy().mul(semesterWeeks));
                }
            }
            sem_value.setEquiv(evalValueEquiv(sem_value, degree, conversionTable));
            sems[i] = sem;
        }
        if (sum_semester_weeks == 0) {
            teacher_stat.getValue().setEquiv(0);
            teacher_stat.getValueWeek().set(new LoadValue());
            teacher_stat.getDue().set(new LoadValue());
            teacher_stat.getDueWeek().set(new LoadValue());
            teacher_extraWeek.set(new LoadValue());
            teacher_extra.set(new LoadValue());
        } else {
            teacher_stat.getValue().setEquiv(evalValueEquiv(teacher_stat.getValue(), degree, conversionTable));
            teacher_stat.getValueWeek().set(teacher_stat.getValue().copy().div(sum_semester_weeks));
            if (hasDU) {
                teacher_stat.getDue().set(teacher_stat.getDueWeek().copy().mul(sum_semester_weeks));
                teacher_extraWeek.set(teacher_extra.copy().div(sum_semester_weeks));
            }
        }
        if (hasDU) {
            teacher_extraWeek.setEquiv(teacher_stat.getValueWeek().getEquiv() - teacher_stat.getDueWeek().getEquiv());
            teacher_extra.setEquiv(teacher_stat.getValue().getEquiv() - teacher_stat.getDueWeek().getEquiv() * sum_semester_weeks);
            AcademicLoadConversionRow cr = conversionTable.get(degree.getConversionRule().getId());
            if (cr.getValueC() == 1) {
                teacher_extraWeek.setC(teacher_extraWeek.getEquiv());
                teacher_extra.setC(teacher_extra.getEquiv());
                teacher_stat.getDueWeek().setC(teacher_stat.getDueWeek().getEquiv());
                teacher_stat.getDue().setC(teacher_stat.getDue().getEquiv());
                for (TeacherSemesterStat sem : sems) {
                    sem.getExtraWeek().setC(sem.getExtraWeek().getEquiv());
                }
            } else if (cr.getValueTD() == 1) {
                teacher_extraWeek.setTd(teacher_extraWeek.getEquiv());
                teacher_extra.setTd(teacher_extra.getEquiv());
                teacher_stat.getDueWeek().setTd(teacher_stat.getDueWeek().getEquiv());
                teacher_stat.getDue().setTd(teacher_stat.getDue().getEquiv());
                for (TeacherSemesterStat sem : sems) {
                    sem.getExtraWeek().setTd(sem.getExtraWeek().getEquiv());
                }
            } else if (cr.getValueTP() == 1) {
                teacher_extraWeek.setTp(teacher_extraWeek.getEquiv());
                teacher_extra.setTp(teacher_extra.getEquiv());
                teacher_stat.getDueWeek().setTp(teacher_stat.getDueWeek().getEquiv());
                teacher_stat.getDue().setTp(teacher_stat.getDue().getEquiv());
                for (TeacherSemesterStat sem : sems) {
                    sem.getExtraWeek().setTp(sem.getExtraWeek().getEquiv());
                }
            } else {
                teacher_extraWeek.setTd(teacher_extraWeek.getEquiv());
                teacher_extra.setTd(teacher_extra.getEquiv());
                teacher_stat.getDueWeek().setTd(teacher_stat.getDueWeek().getEquiv());
                teacher_stat.getDue().setTd(teacher_stat.getDue().getEquiv());
            }
        } else {
            teacher_extraWeek.set(new LoadValue());
            teacher_extra.set(new LoadValue());
        }
        teacher_stat.setWeeks(sum_semester_weeks);
        teacher_stat.setMaxWeeks(sum_max_semester_weeks);
        log.log(Level.FINE, "evalTeacherStat {0} in {1}", new Object[]{getValidName(teacher), ch.stop()});

        return teacher_stat;
    }

    public ModuleStat evalModuleStat(int periodId, int courseAssignmentId, Integer forTeacherId, StatCache cache) {
        if (cache == null) {
            cache = new StatCache();
        }
        StatCache.PeriodCache periodCache = cache.forPeriod(periodId);
        AcademicCourseAssignment module = cache.forPeriod(periodId).getAcademicCourseAssignmentMap().get(courseAssignmentId);
        LoadValue mod_val = new LoadValue(module.getValueC(), module.getValueTD(), module.getValueTP(), module.getValuePM(), 0, 0, 0, 0);
        ModuleStat ms = new ModuleStat();
        ms.setModule(module);
        List<AcademicSemester> semesters = cache.getAcademicSemesterList();
        ModuleSemesterStat[] sems = new ModuleSemesterStat[semesters.size()];
        ms.setSemesters(sems);
        AcademicTeacher teacher = null;
        if (forTeacherId != null) {
            teacher = cache.getAcademicTeacherMap().get(forTeacherId);
            if (teacher == null) {
                throw new IllegalArgumentException("Teacher " + forTeacherId + " not found");
            }
        } else {
            teacher = module.getTeacher();
        }
        if (teacher == null) {
            for (int i = 0; i < semesters.size(); i++) {
                AcademicSemester ss = semesters.get(i);
                ModuleSemesterStat s = new ModuleSemesterStat();
                s.setSemester(ss);
                sems[i] = s;
            }
        } else {
            AcademicTeacher tal = teacher;//cache.getAcademicTeacherMap().get(teacher.getId());
            for (int i = 0; i < semesters.size(); i++) {
                AcademicSemester ss = semesters.get(i);
                ModuleSemesterStat s = new ModuleSemesterStat();
                s.setSemester(ss);
                if (module.getCoursePlan().getCourseLevel().getSemester().getName().equals(ss.getName())) {
                    s.getValue().set(mod_val.copy().mul(module.getGroupCount() * module.getShareCount()));
                    s.setValueEffWeek(module.getValueEffWeek() * module.getGroupCount() * module.getShareCount());
                    AcademicTeacherPeriod trs = findAcademicTeacherPeriod(periodId, tal);
                    s.getValue().setEquiv(evalValueEquiv(s.getValue(), trs.getDegree(), periodCache.getConversionTable()));
                    ms.getValue().set(s.getValue());
                    ms.setValueEffWeek(s.getValueEffWeek());

                } else {
                    //all zeros
                }
                sems[i] = s;
            }
        }
        return ms;
    }

    public double evalValueEquiv(LoadValue value, String degree, AcademicConversionTableHelper table) {
        AcademicTeacherDegree dd = findTeacherDegree(degree);
        return evalValueEquiv(value, dd, table);
    }

    public void updateEquivCandTP(int periodId, LoadValue v, StatCache cache) {
        AcademicConversionTableHelper conversionTable = cache.forPeriod(periodId).getConversionTable();
        v.setEquivC(evalValueEquiv(v, cache.getAcademicTeacherDegreesByCodeMap().get("P"), conversionTable));
        v.setEquivTD(evalValueEquiv(v, cache.getAcademicTeacherDegreesByCodeMap().get("MA"), conversionTable));
    }

    public double evalValueEquiv(LoadValue v, AcademicTeacherDegree dd, AcademicConversionTableHelper table) {
        if (dd == null) {
            return 0;
        }
        AcademicLoadConversionRow r = table.get(dd.getConversionRule().getId());
        return r.getValueC() * v.getC()
                + r.getValueTD() * v.getTd()
                + r.getValueTP() * v.getTp()
                + r.getValuePM() * v.getPm();
    }

    public AcademicTeacher findTeacherByUser(Integer userId) {
        return UPA.getPersistenceUnit().createQuery("Select u from AcademicTeacher u where u.userId=:userId")
                .setParameter("userId", userId)
                .getFirstResultOrNull();
    }

    public AcademicStudent findStudentByUser(Integer userId) {
        return UPA.getPersistenceUnit().createQuery("Select u from AcademicStudent u where u.userId=:userId")
                .setParameter("userId", userId)
                .getFirstResultOrNull();
    }

    public AcademicTeacher findTeacherByContact(Integer contacId) {
        return UPA.getPersistenceUnit().createQuery("Select u from AcademicTeacher u where u.contactId=:contacId")
                .setParameter("contacId", contacId)
                .getFirstResultOrNull();
    }

    public AcademicStudent findStudentByContact(Integer contacId) {
        return UPA.getPersistenceUnit().createQuery("Select u from AcademicStudent u where u.contactId=:contacId")
                .setParameter("contacId", contacId)
                .getFirstResultOrNull();
    }

    //    public List<AcademicCourseAssignment> findCourseAssignments(Integer teacher, String semester, StatCache cache) {
//        List<AcademicCourseAssignment> m = new ArrayList<>();
//            for (AcademicCourseAssignment value : cache.getAcademicCourseAssignmentsByTeacherAndSemester(teacher, semester)) {
//                if (teacher == null || (value.getTeacher() != null && value.getTeacher().getId() == (teacher))) {
//                    if (semester == null || (value.getCoursePlan().getSemester() != null && value.getCoursePlan().getSemester().getName().equals(semester))) {
//                        m.add(value);
//                    }
//                }
//            }
//        return m;
//    }
    public void addTeacherAcademicCourseAssignment(int teacherId, int assignementId) {
        PersistenceUnit pu = UPA.getPersistenceUnit();
        AcademicCourseAssignment a = pu.findById(AcademicCourseAssignment.class, assignementId);
        a.setTeacher(findTeacher(teacherId));
        pu.merge(a);
    }

    public void removeTeacherAcademicCourseAssignment(int assignementId) {
        PersistenceUnit pu = UPA.getPersistenceUnit();
        AcademicCourseAssignment a = pu.findById(AcademicCourseAssignment.class, assignementId);
        a.setTeacher(null);
        pu.merge(a);
    }

    public void addIntent(int teacherId, int assignementId) {
        PersistenceUnit pu = UPA.getPersistenceUnit();
        AcademicCourseIntent i = pu.createQuery("Select a from AcademicCourseIntent a where a.teacherId=:teacherId and a.assignmentId=:assignementId")
                .setParameter("teacherId", teacherId)
                .setParameter("assignementId", assignementId)
                .getFirstResultOrNull();
        if (i == null) {
            i = new AcademicCourseIntent();
            i.setTeacher(findTeacher(teacherId));
            i.setAssignment((AcademicCourseAssignment) pu.findById(AcademicCourseAssignment.class, assignementId));
            if (i.getTeacher() == null || i.getAssignment() == null) {
                throw new RuntimeException("Error");
            }
            pu.persist(i);
        }
    }

    public void removeIntent(int teacherId, int assignementId) {
        PersistenceUnit pu = UPA.getPersistenceUnit();
        AcademicCourseIntent i = pu.createQuery("Select a from AcademicCourseIntent a where a.teacherId=:teacherId and a.assignmentId=:assignementId")
                .setParameter("teacherId", teacherId)
                .setParameter("assignementId", assignementId)
                .getFirstResultOrNull();
        if (i != null) {
            pu.remove(i);
        }
    }

    public void removeAllIntents(int assignementId) {
        PersistenceUnit pu = UPA.getPersistenceUnit();
        List<AcademicCourseIntent> intentList = pu.createQuery("Select a from AcademicCourseIntent a where a.assignmentId=:assignementId")
                .setParameter("assignementId", assignementId)
                .getResultList();
        for (AcademicCourseIntent ii : intentList) {
            pu.remove(ii);
        }
    }

    public AcademicCourseAssignment findAcademicCourseAssignment(int assignmentId) {
        return UPA.getPersistenceUnit().findById(AcademicCourseAssignment.class, assignmentId);
    }

    public List<AcademicCourseAssignment> findAcademicCourseAssignments(int periodId) {
        return UPA.getPersistenceUnit().createQuery("Select a from AcademicCourseAssignment a where a.coursePlan.periodId=:periodId")
                .setParameter("periodId", periodId)
                .getResultList();
    }

    public List<AcademicCourseIntent> findCourseIntentsByAssignment(int periodId, int assignment, String semester, CourseFilter filter, StatCache cache) {
        if (cache != null) {
            return cache.forPeriod(periodId).getAcademicCourseIntentByAssignmentAndSemester(assignment, semester);
        }
        List<AcademicCourseIntent> intents = null;
        intents = UPA.getPersistenceUnit().createQuery("Select a from AcademicCourseIntent a where a.assignmentId=:assignment")
                .setHint(QueryHints.NAVIGATION_DEPTH, 5)
                .setParameter("assignment", assignment)
                .getResultList();
        List<AcademicCourseIntent> m = new ArrayList<>();
        for (AcademicCourseIntent value : intents) {
            if (semester == null || (value.getAssignment().getCoursePlan().getCourseLevel().getSemester() != null
                    && value.getAssignment().getCoursePlan().getCourseLevel().getSemester().getName().equals(semester))) {
                if (acceptAssignment(value.getAssignment(), filter)) {
                    m.add(value);
                }
            }
        }
        return m;
    }

    public List<AcademicCourseIntent> findCourseIntents(int periodId) {
        return UPA.getPersistenceUnit().createQuery("Select a from AcademicCourseIntent a where a.assignment.coursePlan.periodId=:periodId")
                .setParameter("periodId", periodId)
                .setHint(QueryHints.NAVIGATION_DEPTH, 5)
                .getResultList();
    }

    public List<AcademicCourseIntent> findCourseIntentsByTeacher(int periodId, Integer teacher, String semester, CourseFilter filter, StatCache cache) {
        List<AcademicCourseIntent> intents = null;
        if (cache != null) {
            intents = cache.forPeriod(periodId).getAcademicCourseIntentByTeacherAndSemester(teacher, semester);
        } else {
            if (teacher == null) {
                intents = UPA.getPersistenceUnit().createQuery("Select a from AcademicCourseIntent a where a.assignment.coursePlan.periodId=:periodId")
                        .setParameter("periodId", periodId)
                        .setHint(QueryHints.NAVIGATION_DEPTH, 5)
                        .getResultList();
            } else {
                intents = UPA.getPersistenceUnit().createQuery("Select a from AcademicCourseIntent a where a.assignment.coursePlan.periodId=:periodId and a.teacherId=:teacherId")
                        .setParameter("periodId", periodId)
                        .setHint(QueryHints.NAVIGATION_DEPTH, 5)
                        .setParameter("teacherId", teacher)
                        .getResultList();
            }
        }
        List<AcademicCourseIntent> m = new ArrayList<>();
        for (AcademicCourseIntent value : intents) {
            if (semester == null || (value.getAssignment().getCoursePlan().getCourseLevel().getSemester() != null
                    && value.getAssignment().getCoursePlan().getCourseLevel().getSemester().getName().equals(semester))) {
                if (acceptAssignment(value.getAssignment(), filter)) {
                    m.add(value);
                }
            }
        }
        return m;
    }

    private List<AcademicCourseAssignment> filterAssignments(List<AcademicCourseAssignment> base, CourseFilter filter) {
        if (filter == null) {
            return base;
        }
        List<AcademicCourseAssignment> ret = new ArrayList<>();
        for (AcademicCourseAssignment academicCourseAssignment : base) {
            if (acceptAssignment(academicCourseAssignment, filter)) {
                ret.add(academicCourseAssignment);
            }
        }
        return ret;
    }

    public boolean acceptAssignment(AcademicCourseAssignment academicCourseAssignment, CourseFilter filter) {
        if (filter == null) {
            return true;
        }
        List<AcademicCourseAssignment> ret = new ArrayList<>();
        boolean accept = true;
        if (filter.getLabels() == null) {
            // nothing
        } else if (filter.getLabels().size() == 0) {
//            if (buildCoursePlanLabelsFromString(academicCourseAssignment.getCoursePlan().getLabels()).size() > 0) {
//                accept = false;
//            }
        } else {
            Set<String> foundLabels = buildCoursePlanLabelsFromString(academicCourseAssignment.getCoursePlan().getLabels());
            for (String lab : filter.getLabels()) {
                if (lab.startsWith("!")) {
                    String nlab = lab.substring(1);
                    if (!filter.getLabels().contains(nlab)) {
                        if (foundLabels.contains(nlab)) {
                            accept = false;
                        }
                    }
                } else {
                    String nlab = "!" + lab;
                    if (!filter.getLabels().contains(nlab)) {
                        if (!foundLabels.contains(lab)) {
                            accept = false;
                        }
                    }
                }
            }
        }
        if (filter.getProgramTypes() != null && filter.getProgramTypes().size() > 0) {
            AcademicProgramType t = academicCourseAssignment.getCoursePlan().getCourseLevel().getAcademicClass().getProgram().getProgramType();
            if (t == null) {
                throw new RuntimeException("Null Program Type");
            }
            if (!filter.getProgramTypes().contains(t.getId())) {
                accept = false;
            }
        }
        return (accept);
    }

    public List<AcademicCourseAssignment> findCourseAssignments(int periodId, Integer teacher, String semester, CourseFilter filter, StatCache cache) {
        List<AcademicCourseAssignment> base = null;
        if (filter.isIncludeIntents()) {
            List<AcademicCourseAssignment> all = new ArrayList<>();
            for (AcademicCourseAssignmentInfo i : findCourseAssignmentsAndIntents(periodId, teacher, semester, filter, cache)) {
                all.add(i.getAssignment());
            }
            base = all;
        } else {
            base = cache.forPeriod(periodId).getAcademicCourseAssignmentsByTeacherAndSemester(teacher, semester, filter);
        }
        return base;
    }

    public List<AcademicCourseAssignmentInfo> findCourseAssignmentsAndIntents(int periodId, Integer teacher, String semester, CourseFilter filter, StatCache cache) {
        List<AcademicCourseAssignmentInfo> all = new ArrayList<>();
        HashSet<Integer> visited = new HashSet<>();
        for (AcademicCourseAssignment a : cache.forPeriod(periodId).getAcademicCourseAssignmentsByTeacherAndSemester(teacher, semester, filter)) {
            if (!visited.contains(a.getId())) {
                visited.add(a.getId());
                AcademicCourseAssignmentInfo b = new AcademicCourseAssignmentInfo();
                b.setAssigned(a.getTeacher() != null);
                b.setAssignment(a);
                all.add(b);
            }
        }
        for (AcademicCourseIntent a : findCourseIntentsByTeacher(periodId, teacher, semester, filter, cache)) {
            if (!visited.contains(a.getAssignment().getId())) {
                visited.add(a.getAssignment().getId());
                AcademicCourseAssignmentInfo b = new AcademicCourseAssignmentInfo();
                b.setAssigned(false);
                b.setAssignment(a.getAssignment());
                all.add(b);
            }
        }
        for (AcademicCourseAssignmentInfo a : all) {
            List<AcademicCourseIntent> b = findCourseIntentsByAssignment(periodId, a.getAssignment().getId(), semester, filter, cache);
            TreeSet<String> allIntents = new TreeSet<>();
            TreeSet<Integer> allIntentIds = new TreeSet<>();
            for (AcademicCourseIntent b1 : b) {
                if (teacher == null || (teacher.intValue() != b1.getTeacher().getId())) {
                    String n = getValidName(b1.getTeacher());
                    allIntents.add(n);
                }
                allIntentIds.add(b1.getTeacher().getId());
            }
            StringBuilder sb = new StringBuilder();
            if (a.getAssignment().getTeacher() != null) {
                AcademicTeacher t = a.getAssignment().getTeacher();
                String name = getValidName(t);
                sb.append(name + " (*)");
            }
            for (String i : allIntents) {
                if (a.getAssignment().getTeacher() != null
                        && a.getAssignment().getTeacher().getContact() != null
                        && i.equals(a.getAssignment().getTeacher().getContact().getFullName())) {
                    //ignore
                } else {
                    if (sb.length() > 0) {
                        sb.append(", ");
                    }
                    sb.append(i);
                }
            }
            a.setIntentsSet(allIntents);
            a.setIntents(sb.toString());
            a.setIntentsUserIdsSet(allIntentIds);
        }
        Collections.sort(all, new Comparator<AcademicCourseAssignmentInfo>() {

            @Override
            public int compare(AcademicCourseAssignmentInfo o1, AcademicCourseAssignmentInfo o2) {
                AcademicCourseAssignment a1 = o1.getAssignment();
                AcademicCourseAssignment a2 = o2.getAssignment();
//                String s1 = a1.getCoursePlan().getName();
//                String s2 = a2.getCoursePlan().getName();
                String s1 = StringUtils.nonNull(a1.getFullName());
                String s2 = StringUtils.nonNull(a2.getFullName());
                return s1.compareTo(s2);
            }
        });
        return all;
    }


    public List<AcademicProgramType> findProgramTypes() {
        return UPA.getPersistenceUnit().findAll(AcademicProgramType.class);
    }

    public Set<String> findCoursePlanLabels(int periodId) {
        HashSet<String> labels = new HashSet<>();
        for (AcademicCoursePlan plan : findCoursePlans(periodId)) {
            labels.addAll(buildCoursePlanLabelsFromString(plan.getLabels()));
        }
        return labels;
    }

    public Set<String> buildCoursePlanLabelsFromString(String string) {
        HashSet<String> labels = new HashSet<>();
        if (string != null) {
            for (String s : string.split(",|;| |:")) {
                if (s.length() > 0) {
                    labels.add(s);
                }
            }
        }
        return labels;
    }

    public List<TeacherSemesterStat> evalTeacherSemesterStatList(int periodId, String semester, Integer[] teachers, CourseFilter filter, final StatCache cache) {
        List<TeacherSemesterStat> all = new ArrayList<>();
        for (TeacherPeriodStat s : evalTeacherStatList(periodId, teachers, semester, filter, cache)) {
            if (semester == null) {
                all.addAll(Arrays.asList(s.getSemesters()));
            } else {
                for (TeacherSemesterStat ss : s.getSemesters()) {
                    if (ss.getSemester().getCode().equals(semester)) {
                        all.add(ss);
                    }
                }
            }
        }
        return all;
    }

    public List<TeacherPeriodStat> evalTeacherStatList(final int periodId, Integer[] teachers, String semester, CourseFilter filter, final StatCache cache) {
        Chronometer ch = new Chronometer();
        if (teachers == null || teachers.length == 0) {
            Set<Integer> all = cache.getAcademicTeacherMap().keySet();
            teachers = all.toArray(new Integer[all.size()]);
        } else {
            HashSet<Integer> teachersSetRequested = new HashSet<>();
            for (Integer tt : teachers) {
                if (tt != null && cache.getAcademicTeacherMap().containsKey(tt)) {
                    teachersSetRequested.add(tt);
                } else {
                    System.err.println("Teacher id ignored " + tt);
                }
            }
            teachers = teachersSetRequested.toArray(new Integer[teachersSetRequested.size()]);
        }
        TreeSet<Integer> teachersSet = new TreeSet<Integer>(new Comparator<Integer>() {

            public int compare(Integer o1, Integer o2) {
                if (o1.equals(o2)) {
                    return 0;
                }
                AcademicTeacher t1 = cache.getAcademicTeacherMap().get(o1);
                AcademicTeacher t2 = cache.getAcademicTeacherMap().get(o2);
                if (t1 == null && t2 == null) {
                    return 0;
                }
                if (t1 == null) {
                    return -1;
                }
                if (t2 == null) {
                    return 1;
                }

                AcademicTeacherDegree d1 = findAcademicTeacherPeriod(periodId, t1).getDegree();
                AcademicTeacherDegree d2 = findAcademicTeacherPeriod(periodId, t2).getDegree();
                if (d1 == null && d2 == null) {
                    return 0;
                }
                if (d1 == null) {
                    return -1;
                }
                if (d2 == null) {
                    return 1;
                }

                int r = Integer.compare(d1.getPosition(), d2.getPosition());
                if (r != 0) {
                    return r;
                }
                r = d1.getName().compareTo(d2.getName());
                if (r != 0) {
                    return r;
                }
                r = getValidName(t1).compareTo(getValidName(t2));
                if (r != 0) {
                    return r;
                }
                return r;
            }
        });
        teachersSet.addAll(Arrays.asList(teachers));
        List<TeacherPeriodStat> stats = new ArrayList<>();
        for (Integer teacherId : teachersSet) {
            TeacherPeriodStat st = evalTeacherStat(periodId, teacherId, null, null, null, filter, cache);
            if (st != null) {
                boolean ok = false;
                if (st.getValue().getEquiv() > 0) {
                    if (semester != null) {
                        for (TeacherSemesterStat ss : st.getSemesters()) {
                            if (ss.getSemester().getName().equals(semester)) {
                                if (ss.getValue().getEquiv() > 0) {
                                    ok = true;
                                }
                            }
                        }
                    } else {
                        ok = true;
                    }
                }
                if (ok) {
                    stats.add(st);
                }
            }
        }
//        Collections.sort(stats, new Comparator<TeacherStat>() {
//
//
//            public int compare(TeacherStat o1, TeacherStat o2) {
//                return Teacher.getName(o1.getTeacher()).compareTo(Teacher.getName(o2.getTeacher()));
//            }
//        });
        log.log(Level.FINE, "evalTeachersStat {0} teachers in {1}", new Object[]{teachersSet.size(), ch.stop()});
        return stats;//.toArray(new TeacherStat[stats.size()]);
    }

    public GlobalStat evalGlobalStat(int periodId, CourseFilter filter, StatCache cache) {
        GlobalStat s = new GlobalStat();
        if (cache == null) {
            cache = new StatCache();
        }
        List<AcademicTeacher> allTeachers = findTeachers();
        List<Integer> teachersIds = new ArrayList<>();
        for (AcademicTeacher t : allTeachers) {
            teachersIds.add(t.getId());
        }
        List<AcademicSemester> findSemesters = cache.getAcademicSemesterList();

        List<TeacherPeriodStat> ts = evalTeacherStatList(periodId, teachersIds.toArray(new Integer[teachersIds.size()]), null, filter, cache);
        for (TeacherPeriodStat t : ts) {
            AcademicTeacherPeriod trs = findAcademicTeacherPeriod(periodId, t.getTeacher());
            AcademicTeacherSituation situation = trs.getSituation();
            AcademicTeacherDegree degree = trs.getDegree();
            GlobalAssignmentStat[] annStatAss = new GlobalAssignmentStat[]{
                    s.getAssignment(null, situation, degree),
                    s.getAssignment(null, situation, null),
                    s.getAssignment(null, null, null)};

            for (GlobalAssignmentStat y : annStatAss) {

                y.getValue().add(t.getValue());
                y.getExtra().add(t.getExtra());
                y.getDue().add(t.getDue());

                y.getDueWeek().add(t.getDueWeek());
                y.getValueWeek().add(t.getValueWeek());
                y.getExtraWeek().add(t.getExtraWeek());

                y.setMaxWeeks(y.getMaxWeeks() + t.getMaxWeeks());
                y.setWeeks(y.getWeeks() + t.getWeeks());
                if (!y.getTeachers().containsKey(t.getTeacher().getId())) {
                    y.getTeachers().put(t.getTeacher().getId(), t.getTeacher());
                    y.setTeachersCount(y.getTeachers().size());
                }
                int teachersSize = y.getTeachers().size();
                y.getAvgValue().set(t.getValue().copy().div(teachersSize));
                y.getAvgExtra().set(t.getExtraWeek().copy().div(teachersSize));
                y.getAvgValueWeek().set(t.getValueWeek().copy().div(teachersSize));
                y.getAvgExtraWeek().set(t.getExtraWeek().copy().div(teachersSize));
            }
            for (TeacherSemesterStat semLoad : t.getSemesters()) {
                AcademicSemester semester = semLoad.getSemester();

                GlobalAssignmentStat[] semStatAss = new GlobalAssignmentStat[]{
                        s.getAssignment(semester, situation, degree),
                        s.getAssignment(semester, situation, null),
                        s.getAssignment(semester, null, degree),
                        s.getAssignment(semester, null, null),
                        s.getAssignment(semester, situation, null),};

                for (GlobalAssignmentStat y : semStatAss) {

                    y.getValue().add(semLoad.getValue());
                    y.getExtra().add(semLoad.getExtra());
                    y.getDue().add(semLoad.getDue());

                    y.getDueWeek().add(semLoad.getDueWeek());
                    y.getValueWeek().add(semLoad.getValueWeek());
                    y.getExtraWeek().add(semLoad.getExtraWeek());

                    y.setMaxWeeks(y.getMaxWeeks() + semLoad.getMaxWeeks());
                    y.setWeeks(y.getWeeks() + semLoad.getWeeks());
                    if (!y.getTeachers().containsKey(t.getTeacher().getId())) {
                        y.getTeachers().put(t.getTeacher().getId(), t.getTeacher());
                        y.setTeachersCount(y.getTeachers().size());
                    }
                    int teachersSize = y.getTeachers().size();
                    y.getAvgValue().set(semLoad.getValue().copy().div(teachersSize));
                    y.getAvgExtra().set(semLoad.getExtraWeek().copy().div(teachersSize));
                    y.getAvgValueWeek().set(semLoad.getValueWeek().copy().div(teachersSize));
                    y.getAvgExtraWeek().set(semLoad.getExtraWeek().copy().div(teachersSize));
                }
            }
        }
        AcademicTeacherSituation Contractuel = findTeacherSituation("Contractuel");
        AcademicTeacherSituation Permanent = findTeacherSituation("Permanent");
        AcademicTeacherSituation Vacataire = findTeacherSituation("Vacataire");
        AcademicTeacherDegree assistant = findTeacherDegree("A");

        List<AcademicCoursePlan> coursePlans = findCoursePlans(periodId);
        StatCache.PeriodCache periodCache = cache.forPeriod(periodId);
        List<AcademicCourseAssignment> courseAssignments = periodCache.getAcademicCourseAssignmentList();
        s.setCoursePlanCount(coursePlans.size());
        s.setCourseAssignmentCount(courseAssignments.size());
        for (AcademicCourseAssignment value : courseAssignments) {
            AcademicSemester semester = value.getCoursePlan().getCourseLevel().getSemester();
            double grp = value.getGroupCount();
            double shr = value.getShareCount();
            LoadValue loadValue = new LoadValue(
                    value.getValueC() * grp * shr,
                    value.getValueTD() * grp * shr,
                    value.getValueTP() * grp * shr,
                    value.getValuePM() * grp * shr,
                    0, 0, 0, 0
            );
            double g = evalValueEquiv(loadValue, assistant, periodCache.getConversionTable());
            loadValue.setEquiv(g);
            if (value.getTeacher() == null) {
                s.getAssignment(semester, null, null).getMissingAssignments().add(loadValue);
                s.getAssignment(null, null, null).getMissingAssignments().add(loadValue);
            }
            s.getAssignment(semester, null, null).getTargetAssignments().add(loadValue);
            s.getAssignment(null, null, null).getTargetAssignments().add(loadValue);
        }
//        GlobalAssignmentStat a = s.getAssignment(null, null, null);
//        a.getMissingAssignments().set(a.getTargetAssignments()).substruct(a.getValue());
//        for (AcademicSemester sem : findSemesters) {
//            a = s.getAssignment(sem, null, null);
////            a.getMissingAssignments().set(a.getTargetAssignments()).substruct(a.getValue());
//        }
        s.getAssignments().sort(new Comparator<GlobalAssignmentStat>() {

            @Override
            public int compare(GlobalAssignmentStat o1, GlobalAssignmentStat o2) {
                int d1 = o1.getDegree() == null ? 0 : 1;
                int s1 = o1.getSemester() == null ? 0 : 1;
                int si1 = o1.getSituation() == null ? 0 : 1;
                int d2 = o2.getDegree() == null ? 0 : 1;
                int s2 = o2.getSemester() == null ? 0 : 1;
                int si2 = o2.getSituation() == null ? 0 : 1;
                int x = (s1 - s2);
                if (x != 0) {
                    return x;
                }
                x = (o1.getSemester() == null ? "" : o1.getSemester().getName()).compareTo(o2.getSemester() == null ? "" : o2.getSemester().getName());
                if (x != 0) {
                    return x;
                }

                x = (d1 + s1 + si1) - (d2 + s2 + si2);
                if (x != 0) {
                    return x;
                }
                x = (s1 + si1 * 2 + d1 * 4) - (s2 + si2 * 2 + d2 * 4);
                if (x != 0) {
                    return x;
                }
                x = (o1.getSemester() == null ? "" : o1.getSemester().getName()).compareTo(o2.getSemester() == null ? "" : o2.getSemester().getName());
                if (x != 0) {
                    return x;
                }
                x = (o1.getSituation() == null ? "" : o1.getSituation().getName()).compareTo(o2.getSituation() == null ? "" : o2.getSituation().getName());
                if (x != 0) {
                    return x;
                }
                x = (o1.getDegree() == null ? "" : o1.getDegree().getName()).compareTo(o2.getDegree() == null ? "" : o2.getDegree().getName());
                if (x != 0) {
                    return x;
                }
                return 0;
            }

        });
        s.setTeachersCount(s.getTotalAssignment().getTeachersCount());
        GlobalAssignmentStat vacataireAssignment = s.getAssignment(null, Vacataire, null);

        s.setTeachersTemporaryCount(vacataireAssignment.getTeachersCount());
        GlobalAssignmentStat contractuelAssignment = s.getAssignment(null, Contractuel, null);
        s.setTeachersContractualCount(contractuelAssignment.getTeachersCount());
        s.setTeachersPermanentCount(s.getAssignment(null, Permanent, null).getTeachersCount());

        s.setTeachersOtherCount(s.getTeachersCount() - s.getTeachersPermanentCount() - s.getTeachersTemporaryCount() - s.getTeachersContractualCount());
        GlobalAssignmentStat ta = s.getTotalAssignment();

        GlobalAssignmentStat neededRelative = s.getNeededRelative();
//        neededRelative
//                .getExtra()
//                .add(ta.getMissingAssignments());
//        neededRelative
//                .getExtraWeek()
//                .add(ta.getMissingAssignments().copy().div(maxWeeks));
//        neededRelative.setTeachersCount((int) Math.ceil(
//                evalValueEquiv(neededRelative.getExtra(), assistant)
//                / assistant.getValueDU()
//        )
//        );

        //calcul de la charge nécessaire selon le du des enseignant permanents
        //donc en gros combien on a besoin d'assistants pour ne plus recruter des contractuels et vacataires
        GlobalAssignmentStat neededByDue = s.getNeededAbsolute();
        double permEquivDu = s.getAssignment(null, Permanent, null).getDue().getEquiv();
        double permEquivTot = s.getAssignment(null, Permanent, null).getValue().getEquiv();

//        double contrEquivDu=s.getAssignment(null, Contractuel, null).getDue().getEquiv();
        double contrEquivTot = s.getAssignment(null, Contractuel, null).getValue().getEquiv();
        double vacEquivTot = s.getAssignment(null, Vacataire, null).getValue().getEquiv();
        double missingAss = ta.getMissingAssignments().getEquiv();
        int maxWeeks = cache.getSemesterMaxWeeks();
        neededByDue.getValue().setEquiv(permEquivTot - permEquivDu + contrEquivTot + vacEquivTot + missingAss);
        List<AcademicSemester> semesters = cache.getAcademicSemesterList();
        neededByDue.getValueWeek().setEquiv(neededByDue.getValue().getEquiv() / maxWeeks / semesters.size());
        neededByDue.setTeachersCount(
                (int) Math.ceil(
                        neededByDue.getValueWeek().getEquiv() / assistant.getValueDU()
                )
        );

        neededRelative.getValue().setEquiv(contrEquivTot + vacEquivTot + missingAss);
        neededRelative.getValueWeek().setEquiv(neededRelative.getValue().getEquiv() / maxWeeks / semesters.size());
        neededRelative.setTeachersCount(
                (int) Math.ceil(
                        neededRelative.getValueWeek().getEquiv() / assistant.getValueDU()
                )
        );

        double contratMiss = 6;
        GlobalAssignmentStat missingStat = s.getMissing();
        missingStat.getValue().set(ta.getMissingAssignments());
        missingStat.getValueWeek().set(missingStat.getValue()).div(maxWeeks * semesters.size());
        missingStat.setTeachersCount(
                (int) Math.ceil(
                        missingStat.getValueWeek().getEquiv() / contratMiss
                )
        );

//        double duAssistant = evalValueEquiv(s.getAssignment(null, Permanent, null).getDue(), assistant);
//        double targetAssistant = evalValueEquiv(s.getTotalAssignment().getTargetAssignments(), assistant);
//        neededByDue.getValue().setEquiv(duAssistant);
//        neededByDue.getTargetEquiv().setEquiv(targetAssistant);
//        neededByDue.getExtra().setEquiv(targetAssistant - duAssistant);
//        neededByDue.getValueWeek().setEquiv(neededByDue.getValue().getEquiv() / maxWeeks);
//        neededByDue.getExtraWeek().setEquiv(neededByDue.getValueWeek().getEquiv() / maxWeeks);
//        neededByDue.setTeachersCount(
//                (int) Math.ceil(
//                        neededByDue.getExtra().getEquiv() / assistant.getValueDU()
//                )
//        );
        return s;
    }


    public AcademicTeacher findCurrentHeadOfDepartment() {
        UserSession sm = UserSession.getCurrentSession();
        AppUser user = (sm == null) ? null : sm.getUser();
        if (user == null || user.getDepartment() == null) {
            return null;
        }
        return findHeadOfDepartment(user.getDepartment().getId());
    }

    public boolean isUserSessionManager() {
        UserSession sm = UserSession.getCurrentSession();
        AppUser user = (sm == null) ? null : sm.getUser();
        if (user == null || user.getDepartment() == null) {
            return false;
        }
        for (AppProfile u : core.findProfilesByUser(user.getId())) {
            String name = u.getName();
            if ("HeadOfDepartment".equals(name)) {
                //check if same department
                return true;
            }
            if ("DirectorOfStudies".equals(name)) {
                //check if same department
                return true;
            }
            if ("Director".equals(name)) {
                //check if same department
                return true;
            }
        }
        return false;
    }

    public AcademicTeacher findHeadOfDepartment(int depId) {
        AppUser u = core.findHeadOfDepartment(depId);
        if (u != null) {
            return findTeacherByUser(u.getId());
        }
        return null;
    }


    public void importStudent(int periodId, AcademicStudentImport s) throws IOException {
        XlsxLoadImporter i = new XlsxLoadImporter();
        XlsxLoadImporter.ImportStudentContext ctx = new XlsxLoadImporter.ImportStudentContext();
        ctx.mainPeriod = core.findPeriodOrMain(periodId);
        i.importStudent(s, ctx);
    }

    public void importTeacher(int periodId, AcademicTeacherImport t) throws IOException {
        XlsxLoadImporter i = new XlsxLoadImporter();
        XlsxLoadImporter.ImportTeacherContext ctx = new XlsxLoadImporter.ImportTeacherContext();
        ctx.mainPeriod = core.findPeriodOrMain(periodId);
        i.importTeacher(t, ctx);
    }

    public int importFile(int periodId, VFile folder, ImportOptions importOptions) throws IOException {
        XlsxLoadImporter i = new XlsxLoadImporter();
        return i.importFile(periodId, folder, importOptions);
    }

    public AcademicTeacher findTeacher(StringComparator t) {
        for (AcademicTeacher teacher : findTeachers()) {
            if (t.matches(teacher.getContact() == null ? null : teacher.getContact().getFullName())) {
                return teacher;
            }
        }
        return null;
    }

    public void update(Object t) {
        PersistenceUnit pu = UPA.getPersistenceUnit();
        pu.merge(t);
    }

    public List<AcademicTeacher> findTeachers() {
        return cacheService.getList(AcademicTeacher.class);

        //return UPA.getPersistenceUnit().findAll(AcademicTeacher.class);
    }

    public List<AcademicTeacher> findEnabledTeachers() {
        return UPA.getPersistenceUnit().createQuery("Select u from AcademicTeacher u where u.deleted=false and u.enabled=true order by u.contact.fullName").getResultList();
    }

    public List<AcademicStudent> findStudents() {
        return UPA.getPersistenceUnit().createQuery("Select u from AcademicStudent u where u.deleted=false order by u.contact.fullName").getResultList();
    }

    /**
     * @param studentFilter ql expression x based. example "x.fullName like '%R%'"
     * @return
     */
    public List<AcademicStudent> findStudents(String studentFilter) {
        return UPA.getPersistenceUnit().createQuery("Select x from AcademicStudent x " +
                " where " +
                " x.deleted=false " +
                ((StringUtils.isEmpty(studentFilter)) ? "" : (" and " + studentFilter)) +
                " order by x.contact.fullName").getResultList();
    }

    public List<AcademicStudent> findStudents(String studentProfileFilter, String studentFilter) {
        List<AcademicStudent> base = findStudents(studentFilter);
        if (!StringUtils.isEmpty(studentProfileFilter)) {
            List<AcademicStudent> goodStudents = new ArrayList<>();
            HashSet<Integer> goodUsers = new HashSet<Integer>();
            AppUserType studentType = core.findUserType("Student");
            List<AppUser> users = VrApp.getBean(CorePlugin.class).findUsersByProfileFilter(studentProfileFilter, studentType.getId());
            for (AppUser user : users) {
                goodUsers.add(user.getId());
            }
            for (AcademicStudent s : base) {
                AppUser u = s.getUser();
                if (u != null && goodUsers.contains(u.getId())) {
                    goodStudents.add(s);
                }
            }
            return goodStudents;
        } else {
            return base;
        }
    }

    public List<AcademicTeacher> findTeachers(String teacherProfileFilter) {
        List<AcademicTeacher> base = findTeachers();
        if (!StringUtils.isEmpty(teacherProfileFilter)) {
            List<AcademicTeacher> goodTeachers = new ArrayList<>();
            HashSet<Integer> goodUsers = new HashSet<Integer>();
            AppUserType teacherType = core.findUserType("Teacher");
            List<AppUser> users = VrApp.getBean(CorePlugin.class).findUsersByProfileFilter(teacherProfileFilter, teacherType.getId());
            for (AppUser user : users) {
                goodUsers.add(user.getId());
            }
            for (AcademicTeacher s : base) {
                AppUser u = s.getUser();
                if (u != null && goodUsers.contains(u.getId())) {
                    goodTeachers.add(s);
                }
            }
            return goodTeachers;
        } else {
            return base;
        }
    }

    public List<AcademicFormerStudent> findGraduatedStudents() {
        return UPA.getPersistenceUnit().createQuery("Select u from AcademicFormerStudent u where u.deleted=false and u.graduated=true").getResultList();
    }

    public List<AcademicTeacher> findTeachersWithAssignementsOrIntents() {
        return UPA.getPersistenceUnit()
                .createQuery("Select u from AcademicTeacher u where u.id in ("
                        + " Select t.id from AcademicTeacher t "
                        + " left join AcademicCourseAssignment a on a.teacheId=t.id"
                        + " left join AcademicCourseIntent i on i.teacherId=t.id"
                        + " where (a is not null) or (i is not null)"
                        + ") order by u.contact.fullName")
                .getResultList();
    }

    public List<AcademicTeacher> findTeachersWithAssignements() {
        return UPA.getPersistenceUnit()
                .createQuery("Select u from AcademicTeacher u where u.id in ("
                        + " Select t.id from AcademicTeacher t "
                        + " inner join AcademicCourseAssignment a on a.teacherId=t.id"
                        + ") order by u.contact.fullName")
                .getResultList();
    }

    public List<AcademicProgram> findPrograms() {
        return UPA.getPersistenceUnit()
                .createQuery(
                        "Select a from AcademicProgram a order by a.name")
                .getResultList();
    }

    public List<AppGender> findGenders() {
        return UPA.getPersistenceUnit().findAll(AppGender.class);
    }

    public List<AcademicTeacherDegree> findTeacherDegrees() {
        return UPA.getPersistenceUnit()
                .createQuery(
                        "Select a from AcademicTeacherDegree a")
                .getResultList();
    }

    public List<AcademicTeacherSituation> findTeacherSituations() {
        return UPA.getPersistenceUnit().findAll(AcademicTeacherSituation.class);
    }

    public List<AcademicPreClass> findAcademicPreClasses() {
        return UPA.getPersistenceUnit().findAll(AcademicPreClass.class);
    }

    public List<AcademicBac> findAcademicBacs() {
        return UPA.getPersistenceUnit().findAll(AcademicBac.class);
    }

    public Map<Integer, AcademicClass> findAcademicClassesMap() {
        HashMap<Integer, AcademicClass> _allClasses = new HashMap<>();
        for (AcademicClass a : findAcademicClasses()) {
            _allClasses.put(a.getId(), a);
        }
        return _allClasses;
    }

    public Map<Integer, AcademicPreClass> findAcademicPreClassesMap() {
        HashMap<Integer, AcademicPreClass> _allClasses = new HashMap<>();
        for (AcademicPreClass a : findAcademicPreClasses()) {
            _allClasses.put(a.getId(), a);
        }
        return _allClasses;
    }

    public Map<Integer, AcademicBac> findAcademicBacsMap() {
        HashMap<Integer, AcademicBac> _allClasses = new HashMap<>();
        for (AcademicBac a : findAcademicBacs()) {
            _allClasses.put(a.getId(), a);
        }
        return _allClasses;
    }

    public List<AcademicClass> findAcademicClasses() {
        return UPA.getPersistenceUnit().findAll(AcademicClass.class);
    }

    public List<AcademicCourseLevel> findCourseLevels() {
        return UPA.getPersistenceUnit().findAll(AcademicCourseLevel.class);
    }

    public AcademicCourseLevel findCourseLevel(int academicClassId, int semesterId) {
        return UPA.getPersistenceUnit().createQuery("Select x from AcademicCourseLevel x where "
                        + " x.academicClassId=:academicClassId"
                        + " and x.semesterId=:semesterId"
        ).setParameter("academicClassId", academicClassId)
                .setParameter("semesterId", semesterId)
                .getFirstResultOrNull();
    }

    public List<AcademicSemester> findSemesters() {
        return cacheService.getList(AcademicSemester.class);
//        return UPA.getPersistenceUnit().createQueryBuilder(AcademicSemester.class).orderBy(new Order().addOrder(new Var("name"), true))
//                .getResultList();
    }

    public AcademicProgram findProgram(int departmentId, String t) {
        return UPA.getPersistenceUnit().
                createQuery("Select a from AcademicProgram a where a.name=:t and a.departmentId=:departmentId")
                .setParameter("t", t)
                .setParameter("departmentId", departmentId)
                .getFirstResultOrNull();
    }

    public AcademicSemester findSemester(String code) {
        return UPA.getPersistenceUnit().
                createQuery("Select a from AcademicSemester a where a.code=:code or a.name=:code or a.name2=:code")
                .setParameter("code", code)
                .getFirstResultOrNull();
    }

    public AppDepartment findDepartment(String code) {
        return VrApp.getBean(CorePlugin.class).findDepartment(code);
    }

    public AppCivility findCivility(String t) {
        return VrApp.getBean(CorePlugin.class).findCivility(t);
    }

    public AcademicCoursePlan findCoursePlan(int periodId, int studentClassId, int semesterId, String courseName) {
        return UPA.getPersistenceUnit().
                createQuery("Select a from AcademicCoursePlan a where " +
                        "a.name=:courseName " +
                        "and a.semesterId=:semesterId " +
                        "and a.courseLevel.academicClassId=:studentClassId " +
                        "and a.periodId=:periodId")
                .setParameter("courseName", courseName)
                .setParameter("semesterId", semesterId)
                .setParameter("studentClassId", studentClassId)
                .setParameter("periodId", periodId)
                .getFirstResultOrNull();
    }

    public AppGender findGender(String t) {
        return (AppGender) UPA.getPersistenceUnit().findByMainField(AppGender.class, t);
    }

    public AcademicTeacher findTeacher(String t) {
        return (AcademicTeacher) UPA.getPersistenceUnit().createQuery("Select u from AcademicTeacher u where u.contact.fullName=:name").setParameter("name", t).getFirstResultOrNull();
    }

    public AcademicStudent findStudent(String t) {
        return (AcademicStudent) UPA.getPersistenceUnit().createQuery("Select u from AcademicStudent u where u.contact.fullName=:name").setParameter("name", t).getFirstResultOrNull();
    }

    public AcademicTeacherDegree findTeacherDegree(String t) {
        return UPA.getPersistenceUnit().
                createQuery("Select a from AcademicTeacherDegree a where a.code=:t")
                .setParameter("t", t)
                .getFirstResultOrNull();
    }

    public AcademicLoadConversionRule findLoadConversionRule(String t) {
        return UPA.getPersistenceUnit().
                createQuery("Select a from AcademicLoadConversionRule a where a.name=:t")
                .setParameter("t", t)
                .getFirstResultOrNull();
    }

    public AcademicLoadConversionTable findLoadConversionTable(String t) {
        return UPA.getPersistenceUnit().
                createQuery("Select a from AcademicLoadConversionTable a where a.name=:t")
                .setParameter("t", t)
                .getFirstResultOrNull();
    }

    public AcademicLoadConversionRow findLoadConversionRow(int tableId, int ruleId) {
        return UPA.getPersistenceUnit().
                createQuery("Select a from AcademicLoadConversionRow a where a.conversionTableId=:tid and a.ruleId=:rid")
                .setParameter("tid", tableId)
                .setParameter("rid", ruleId)
                .getFirstResultOrNull();
    }

    public AcademicTeacherDegree findTeacherDegree(int id) {
        return UPA.getPersistenceUnit().
                createQuery("Select a from AcademicTeacherDegree a where a.id=:t")
                .setParameter("t", id)
                .getFirstResultOrNull();
    }

    public AcademicTeacherSituation findTeacherSituation(String t) {
        return (AcademicTeacherSituation) UPA.getPersistenceUnit().findByMainField(AcademicTeacherSituation.class, t);
    }

    public AcademicTeacherSituation findTeacherSituation(int id) {
        return (AcademicTeacherSituation) UPA.getPersistenceUnit().findById(AcademicTeacherSituation.class, id);
    }

    public AcademicCourseLevel findCourseLevel(int programId, String name) {
        return UPA.getPersistenceUnit().
                createQuery("Select a from AcademicCourseLevel a where a.name=:name and a.programId=:programId")
                .setParameter("name", name)
                .setParameter("programId", programId)
                .getFirstResultOrNull();
    }

    //    public AcademicCourseLevel findCourseLevel(String name) {
//        return UPA.getPersistenceUnit().
//                createQuery("Select a from AcademicCourseLevel a where a.name=:name")
//                .setParameter("name", name)
//                .getEntity();
//    }
    public AcademicCourseGroup findCourseGroup(int periodId, int classId, String name) {
        return UPA.getPersistenceUnit().
                createQuery("Select a from AcademicCourseGroup a where a.name=:name and a.peridoId=:periodId and a.academicClassId=:classId")
                .setParameter("name", name)
                .setParameter("classId", classId)
                .setParameter("periodId", periodId)
                .getFirstResultOrNull();
    }

    public List<AcademicCourseGroup> findCourseGroups(int periodId) {
        return UPA.getPersistenceUnit().
                createQuery("Select a from AcademicCourseGroup a where a.periodId=:periodId")
                .setParameter("periodId", periodId)
                .getResultList();
    }

    public List<AcademicDiscipline> findDisciplines() {
        return UPA.getPersistenceUnit().
                createQuery("Select a from AcademicDiscipline a")
                .getResultList();
    }

    public AcademicDiscipline findDiscipline(String nameOrCode) {
        return UPA.getPersistenceUnit().
                createQuery("Select a from AcademicDiscipline a where a.code=:code or a.name=:code or a.name2=:code")
                .setParameter("code", nameOrCode)
                .getFirstResultOrNull();
    }

    public AcademicCourseType findCourseType(String name) {
        return UPA.getPersistenceUnit().createQuery("Select a from AcademicCourseType a where "
                + "a.name=:name")
                .setParameter("name", name)
                .getFirstResultOrNull();
    }

    public List<AcademicCourseType> findCourseTypes() {
        return UPA.getPersistenceUnit().createQuery("Select a from AcademicCourseType a ")
                .getResultList();
    }

    /**
     *
     */
    public void resetModuleTeaching() {
        resetCurrentYear();
        resetHistAcademicYears();
        trace.trace("resetModuleTeaching", "reset Module Academic", null, "academicPlugin", Level.FINE);
    }

    public void resetTeachers() {
        resetAssignments();
        UPA.getPersistenceUnit().createQuery("delete from AcademicTeacherSemestrialLoad").executeNonQuery();
        UPA.getPersistenceUnit().createQuery("delete from AcademicTeacher").executeNonQuery();
    }

    public void resetAssignments() {
        UPA.getPersistenceUnit().createQuery("delete from AcademicCourseIntent").executeNonQuery();
        UPA.getPersistenceUnit().createQuery("delete from AcademicCourseAssignment").executeNonQuery();
    }

    public void resetCourses() {
        UPA.getPersistenceUnit().createQuery("delete from AcademicCoursePlan").executeNonQuery();
        UPA.getPersistenceUnit().createQuery("delete from AcademicCourseGroup").executeNonQuery();
    }

    public void resetCurrentYear() {
        resetAssignments();
        resetCourses();
//        UPA.getPersistenceUnit().createQuery("delete from AcademicCourseType").executeNonQuery();
//        UPA.getPersistenceUnit().createQuery("delete from AcademicClass").executeNonQuery();
//        UPA.getPersistenceUnit().createQuery("delete from AcademicCourseLevel").executeNonQuery();
//        UPA.getPersistenceUnit().createQuery("delete from AcademicProgram").executeNonQuery();
//        UPA.getPersistenceUnit().createQuery("delete from AcademicSemester").executeNonQuery();
//        UPA.getPersistenceUnit().createQuery("delete from AcademicTeacherSituation").executeNonQuery();
//        UPA.getPersistenceUnit().createQuery("delete from AcademicTeacherDegree").executeNonQuery();
        trace.trace("resetCurrentYear", "reset Module Academic", null, "academicPlugin", Level.FINE);
    }

    public AcademicCourseAssignment findCourseAssignment(int courseAssignmentId) {
        return (AcademicCourseAssignment) UPA.getPersistenceUnit().findById(AcademicCourseAssignment.class, courseAssignmentId);
    }

    public AcademicTeacher findTeacher(int t) {
        return cacheService.getList(AcademicTeacher.class).getByKey(t);
//        return (AcademicTeacher) UPA.getPersistenceUnit()
//                .createQuery("Select u from AcademicTeacher u where u.id=:id")
//                .setParameter("id", t)
//                .setHint(QueryHints.NAVIGATION_DEPTH, 5)
//                .getEntity();
//                .findById(AcademicTeacher.class, t);
    }

    public AcademicStudent findStudent(int t) {
        return (AcademicStudent) UPA.getPersistenceUnit().findById(AcademicStudent.class, t);
    }

    public List<AcademicCourseAssignment> findCourseAssignments(int periodId) {
        return UPA.getPersistenceUnit().createQuery("Select a from AcademicCourseAssignment a where a.coursePlan.periodId=:periodId " +
                " order by a.coursePlan.courseLevel.semester.code,a.coursePlan.courseLevel.academicClass.program.name,a.name,a.courseType.name")
                .setParameter("periodId", periodId)
                .setHint(QueryHints.NAVIGATION_DEPTH, 5)
                .getResultList();
    }

    public List<AcademicCoursePlan> findCoursePlans(int periodId) {
        return UPA.getPersistenceUnit().createQuery("Select a from AcademicCoursePlan a where a.periodId=:periodId ")
                .setHint(QueryHints.NAVIGATION_DEPTH, 5)
                .setParameter("periodId", periodId)
                .getResultList();
    }

    public List<AcademicCourseAssignment> findCourseAssignmentsByPlan(int planId) {
        return UPA.getPersistenceUnit().createQuery("Select a from AcademicCourseAssignment a where a.coursePlanId=:v")
                .setParameter("v", planId)
                .getResultList();
    }

    public List<AcademicCourseAssignment> findCourseAssignmentsByClass(int periodId, int classId) {
        return UPA.getPersistenceUnit().createQuery("Select a from AcademicCourseAssignment a where " +
                        "(a.subClassId=:v or a.coursePlan.courseLevel.academicClassId=:v)"
                        + " and a.coursePlan.periodId=:periodId"
        )
                .setParameter("periodId", periodId)
                .setParameter("v", classId).getResultList();
    }

    public List<AcademicTeacherSemestrialLoad> findTeacherSemestrialLoadsByTeacher(int periodId, int teacherId) {
        return UPA.getPersistenceUnit().createQuery("Select a from AcademicTeacherSemestrialLoad a where a.teacherId=:t and a.periodId=:periodId")
                .setParameter("t", teacherId)
                .setParameter("periodId", periodId)
                .getResultList();
    }

    public List<AcademicTeacherSemestrialLoad> findTeacherSemestrialLoadsByPeriod(int periodId) {
        return UPA.getPersistenceUnit().createQuery("Select a from AcademicTeacherSemestrialLoad a where a.periodId=:periodId")
                .setParameter("periodId", periodId)
                .getResultList();
    }

    public AcademicClass findAcademicClass(String t) {
        return UPA.getPersistenceUnit().createQuery("Select a from AcademicClass a where a.name=:t")
                .setParameter("t", t)
                .getFirstResultOrNull();
    }

    public AcademicClass findAcademicClass(int id) {
        return UPA.getPersistenceUnit().createQuery("Select a from AcademicClass a where a.id=:t")
                .setParameter("t", id)
                .getFirstResultOrNull();
    }

    public AcademicClass findAcademicClass(int programId, String t) {
        return UPA.getPersistenceUnit().createQuery("Select a from AcademicClass a where a.name=:t and a.programId=:programId")
                .setParameter("t", t)
                .setParameter("programId", programId)
                .getFirstResultOrNull();
    }

    public void updateAllCoursePlanValuesByLoadValues(int periodId) {
        Chronometer ch = new Chronometer();
        for (AcademicCoursePlan coursePlan : findCoursePlans(periodId)) {
            updateCoursePlanValuesByLoadValues(coursePlan);
        }
        log.log(Level.INFO, "updateAllCoursePlanValuesByLoadValues in {1}", new Object[]{ch.stop()});
    }

    public void updateCoursePlanValuesByLoadValues(int coursePlanId) {
        AcademicCoursePlan p = findCoursePlan(coursePlanId);
        updateCoursePlanValuesByLoadValues(p);
    }

    private void updateCoursePlanValuesByLoadValues(AcademicCoursePlan coursePlan) {
//        Chronometer ch=new Chronometer();
        List<AcademicCourseAssignment> loads = findCourseAssignmentsByPlan(coursePlan.getId());
        double c = 0;
        double td = 0;
        double tp = 0;
        double pm = 0;

        int gc = 0;
        int gtd = 0;
        int gtp = 0;
        int gpm = 0;

        int wc = 0;
        int wtd = 0;
        int wtp = 0;
        int wpm = 0;

        for (AcademicCourseAssignment load : loads) {
            double c0 = load.getValueC();
            double td0 = load.getValueTD();
            double tp0 = load.getValueTP();
            double pm0 = load.getValuePM();
            double g0 = load.getGroupCount() * load.getShareCount();
            int w0 = load.getCourseType().getWeeks();
            c += c0 * g0;
            td += td0 * g0;
            tp += tp0 * g0;
            pm += pm0 * g0;
            if (c0 > 0) {
                gc += g0;
                wc += w0 * g0;
            }
            if (td0 > 0) {
                gtd += g0;
                wtd += w0 * g0;
            }
            if (tp0 > 0) {
                gtp += g0;
                wtp += w0 * g0;
            }
            if (pm0 > 0) {
                gpm += g0;
                wpm += w0 * g0;
            }
        }
        if (gc > 0) {
            c /= gc;
            wc /= gc;
        }
        if (gtd > 0) {
            td /= gtd;
            wtd /= gc;
        }
        if (gtp > 0) {
            tp /= gtp;
            wtp /= gtp;
        }
        if (gpm > 0) {
            pm /= gpm;
            wpm /= gpm;
        }
        double coeff = 2.0 / 3;
        double tppm = tp + pm * coeff;
        int wtppm = (int) (wtp + wpm * coeff);

        coursePlan.setValueC(c);
        coursePlan.setValueTD(td);
        coursePlan.setValueTP(tp);
        coursePlan.setValuePM(pm);
        coursePlan.setValueTPPM(tppm);

        coursePlan.setGroupCountC(gc);
        coursePlan.setGroupCountTD(gtd);
        coursePlan.setGroupCountTP(gtp);
        coursePlan.setGroupCountPM(gpm);
        coursePlan.setGroupCountTPPM(gtp + gpm);

        coursePlan.setWeeksC(wc);
        coursePlan.setWeeksTD(wtd);
        coursePlan.setWeeksTP(wtp);
        coursePlan.setWeeksPM(wpm);
        coursePlan.setWeeksTPPM(wtppm);

        update(coursePlan);
//        log.log(Level.INFO,"updateCoursePlanValuesByLoadValues in {1}",new Object[]{ch.stop()});
    }

    public AcademicCoursePlan findCoursePlan(int id) {
        return (AcademicCoursePlan) UPA.getPersistenceUnit()
                .createQueryBuilder(AcademicCoursePlan.class)
                .byField("id", id)
                .setHint(QueryHints.NAVIGATION_DEPTH, 5)
                .getFirstResultOrNull();
    }

    ////////////////////////////////////////////////////////////////////////////
    public AppPeriod addAcademicYearSnapshot(String year, String snapshotName) {
        AppPeriod s = createAcademicYear(year, snapshotName);
//        AppPeriod s = new AppPeriod();
//        s.setCreationTime(new DateTime());
//        s.setName(y.getName());
//        s.setSnapshotName(snapshotName);
//        UPA.getPersistenceUnit().persist(s);
        int periodId = s.getId();
        Map<String, AcademicHistTeacherDegree> histTeacherDegreeMap = new HashMap<>();
        for (AcademicTeacherDegree m : findTeacherDegrees()) {
            AcademicHistTeacherDegree h = new AcademicHistTeacherDegree();
            h.setCode(m.getCode());
            h.setName(m.getName());
            h.setName2(m.getName2());
            h.setPosition(m.getPosition());
            h.setConversionRule(m.getConversionRule());
            h.setAcademicYear(s);
            histTeacherDegreeMap.put(h.getCode(), h);
            add(h);
        }
        Map<Integer, AcademicHistTeacherAnnualLoad> teacherToLoad = new HashMap<>();
        for (AcademicTeacher m : findTeachers()) {
            AcademicHistTeacherAnnualLoad h = new AcademicHistTeacherAnnualLoad();
            h.setAcademicYear(s);
            AcademicTeacherPeriod ts = findAcademicTeacherPeriod(periodId, m);
            h.setDegree(histTeacherDegreeMap.get(ts.getDegree() == null ? null : ts.getDegree().getName()));
            h.setSituation(ts.getSituation());
            h.setTeacher(m);
            add(h);
            teacherToLoad.put(m.getId(), h);
        }
        for (AcademicTeacherSemestrialLoad m : findTeacherSemestrialLoadsByPeriod(periodId)) {
            AcademicHistTeacherSemestrialLoad h = new AcademicHistTeacherSemestrialLoad();
            h.setAcademicYear(s);
            h.setAnnualLoad(teacherToLoad.get(m.getTeacher().getId()));
            h.setSemester(m.getSemester());
            h.setWeeksLoad(m.getWeeksLoad());
            add(h);
        }

        Map<Integer, AcademicHistProgram> academicHistCoursePrograms = new HashMap<>();
        for (AcademicProgram m : findPrograms()) {
            AcademicHistProgram h = new AcademicHistProgram();
            h.setAcademicYear(s);
            h.setName(m.getName());
            h.setDepartment(m.getDepartment());
            h.setName(m.getName());
            h.setName2(m.getName2());
            add(h);
            academicHistCoursePrograms.put(m.getId(), h);
        }

        Map<Integer, AcademicHistCourseGroup> academicHistCourseGroups = new HashMap<>();
        for (AcademicCourseGroup m : findCourseGroups(periodId)) {
            AcademicHistCourseGroup h = new AcademicHistCourseGroup();
            h.setAcademicYear(s);
            h.setAcademicClass(m.getAcademicClass());
            add(h);
            academicHistCourseGroups.put(m.getId(), h);
        }

        for (AcademicCoursePlan m : findCoursePlans(periodId)) {
            AcademicHistCoursePlan h = new AcademicHistCoursePlan();

            h.setAcademicYear(s);
            h.setProgram(m.getCourseLevel().getAcademicClass().getProgram() == null ? null : academicHistCoursePrograms.get(m.getCourseLevel().getAcademicClass().getProgram().getId()));
            h.setCourseGroup(m.getCourseGroup() == null ? null : academicHistCourseGroups.get(m.getCourseGroup().getId()));
            h.setCourseLevel(m.getCourseLevel());
            h.setDiscipline(m.getDiscipline());
            h.setName(m.getName());
            h.setName2(m.getName2());
            h.setPosition(m.getPosition());

            h.setGroupCountC(m.getGroupCountC());
            h.setGroupCountPM(m.getGroupCountPM());
            h.setGroupCountTD(m.getGroupCountTD());
            h.setGroupCountTP(m.getGroupCountTP());
            h.setGroupCountTPPM(m.getGroupCountTPPM());

            h.setWeeksC(m.getWeeksC());
            h.setWeeksPM(m.getWeeksPM());
            h.setWeeksTD(m.getWeeksTD());
            h.setWeeksTP(m.getWeeksTP());
            h.setWeeksPM(m.getWeeksPM());
            h.setWeeksTPPM(m.getWeeksTPPM());

            h.setValueC(m.getValueC());
            h.setValuePM(m.getValuePM());
            h.setValueTD(m.getValueTD());
            h.setValueTP(m.getValueTP());
            h.setValuePM(m.getValuePM());
            h.setValueTPPM(m.getValueTPPM());

            h.setStudentClass(m.getCourseLevel().getAcademicClass());
            h.setSemester(m.getCourseLevel().getSemester());
            add(h);
        }
        return s;
    }

    public AcademicTeacherPeriod findAcademicTeacherPeriod(final int periodId, AcademicTeacher t) {
        Map<Integer, AcademicTeacherPeriod> m = cacheService.get(AcademicTeacherPeriod.class).getProperty("findAcademicTeacherPeriodByTeacher:" + periodId, new Action<Map<Integer, AcademicTeacherPeriod>>() {
            @Override
            public Map<Integer, AcademicTeacherPeriod> run() {

                List<AcademicTeacherPeriod> ret =
                        UPA.getPersistenceUnit()
                                .createQueryBuilder(AcademicTeacherPeriod.class)
                                .setEntityAlias("o")
                                .byExpression("o.periodId=:periodId")
                                .setHint(QueryHints.NAVIGATION_DEPTH, 4)
                                .setParameter("periodId", periodId)
                                .getResultList();
                Map<Integer, AcademicTeacherPeriod> t = new HashMap<Integer, AcademicTeacherPeriod>();
                for (AcademicTeacherPeriod o : ret) {
                    t.put(o.getTeacher().getId(), o);
                }
                return t;
            }
        });
        AcademicTeacherPeriod p = m.get(t.getId());
        if (p != null) {
            return p;
        }

        AcademicTeacherPeriod a = new AcademicTeacherPeriod();
        a.setId(-1);
        a.setSituation(t.getSituation());
        a.setDegree(t.getDegree());
        a.setDepartment(t.getDepartment());
        a.setEnabled(t.isEnabled());
        a.setPeriod(VrApp.getBean(CorePlugin.class).findPeriod(periodId));
        return a;
    }

    public double evalHistValueEquiv(int yearId, LoadValue value, String degree) {
        AcademicHistTeacherDegree dd = findHistTeacherDegree(yearId, degree);
        return evalHistValueEquiv(value, dd);
    }

    public double evalHistValueEquiv(LoadValue v, AcademicHistTeacherDegree dd) {
        return dd.getValueC() * v.getC()
                + dd.getValueTD() * v.getTd()
                + dd.getValueTP() * v.getTp()
                + dd.getValuePM() * v.getPm();
    }

    public List<AcademicHistCourseAssignment> findHistCourseAssignments(int yearId, Integer teacher, String semester) {
        List<AcademicHistCourseAssignment> m = new ArrayList<>();
        for (AcademicHistCourseAssignment value : findHistCourseAssignments(yearId)) {
            if (teacher == null || (value.getTeacher() != null && value.getTeacher().getId() == (teacher))) {
                if (semester == null || (value.getCoursePlan().getCourseLevel().getSemester() != null && value.getCoursePlan().getCourseLevel().getSemester().getName().equals(semester))) {
                    m.add(value);
                }
            }
        }
        return m;
    }

    public List<AcademicHistProgram> findHistPrograms(int yearId) {
        return UPA.getPersistenceUnit()
                .createQuery(
                        "Select a from AcademicHistProgram a where a.academicYearId=:x")
                .setParameter("x", yearId)
                .getResultList();
    }

    public List<AcademicHistTeacherDegree> findHistTeacherDegrees(int yearId) {
        return UPA.getPersistenceUnit()
                .createQuery(
                        "Select a from AcademicHistTeacherDegree a where a.academicYearId=:x")
                .setParameter("x", yearId)
                .getResultList();
    }

    public AcademicHistTeacherDegree findHistTeacherDegree(int yearId, String t) {
        return UPA.getPersistenceUnit().
                createQuery("Select a from AcademicHistTeacherDegree a where a.name=:t and a.academicYearId=:y")
                .setParameter("t", t)
                .setParameter("y", yearId)
                .getFirstResultOrNull();
    }

    public void resetHistAcademicYear(int year) {
        UPA.getPersistenceUnit().createQuery("delete from AcademicHistTeacherSemestrialLoad where academiYear=:y").setParameter("y", year).executeNonQuery();
        UPA.getPersistenceUnit().createQuery("delete from AcademicHistTeacherAnnualLoad where academiYear=:y").setParameter("y", year).executeNonQuery();
        UPA.getPersistenceUnit().createQuery("delete from AcademicHistTeacherDegree where academiYear=:y").setParameter("y", year).executeNonQuery();
        UPA.getPersistenceUnit().createQuery("delete from AcademicHistCourseAssignment a where a.coursePlan.academiYear=:y").setParameter("y", year).executeNonQuery();
        UPA.getPersistenceUnit().createQuery("delete from AcademicHistCoursePlan a where a.academiYear=:y").setParameter("y", year).executeNonQuery();
        UPA.getPersistenceUnit().createQuery("delete from AcademicHistCourseGroup a where a.courseLevel.program.academiYear=:y").setParameter("y", year).executeNonQuery();
        UPA.getPersistenceUnit().createQuery("delete from AcademicHistProgram a where a.academiYear=:y").setParameter("y", year).executeNonQuery();
        trace.trace("resetAcademicYear", "reset Academic Year", String.valueOf(year), "academicPlugin", Level.FINE);
    }

    public void resetHistAcademicYears() {
        UPA.getPersistenceUnit().createQuery("delete from AcademicHistTeacherSemestrialLoad").executeNonQuery();
        UPA.getPersistenceUnit().createQuery("delete from AcademicHistTeacherAnnualLoad").executeNonQuery();
        UPA.getPersistenceUnit().createQuery("delete from AcademicHistTeacherDegree").executeNonQuery();
        UPA.getPersistenceUnit().createQuery("delete from AcademicHistCourseAssignment").executeNonQuery();
        UPA.getPersistenceUnit().createQuery("delete from AcademicHistCoursePlan").executeNonQuery();
        UPA.getPersistenceUnit().createQuery("delete from AcademicHistCourseGroup").executeNonQuery();
        UPA.getPersistenceUnit().createQuery("delete from AcademicHistProgram").executeNonQuery();
        trace.trace("resetHistAcademicYears", "reset Academic Years", "", "academicPlugin", Level.FINE);
    }

    public List<AcademicHistTeacherAnnualLoad> findHistTeacherAnnualLoads(int year) {
        return UPA.getPersistenceUnit().createQuery("Select a from AcademicHistTeacherAnnualLoad a where a.academicYearId=:v")
                .setParameter("v", year)
                .getResultList();
    }

    public AcademicHistTeacherAnnualLoad findHistTeacherAnnualLoad(int year, int teacherId) {
        return UPA.getPersistenceUnit().createQuery("Select a from AcademicHistTeacherAnnualLoad a where a.academicYearId=:v and a.teacherId=:t")
                .setParameter("v", year)
                .setParameter("t", teacherId)
                .getFirstResultOrNull();
    }

    public List<AcademicHistTeacherSemestrialLoad> findHistTeacherSemestrialLoads(int year, int teacherId) {
        return UPA.getPersistenceUnit().createQuery("Select a from AcademicTeacherSemestrialLoad a where a.annualLoad.academicYearId=:v and a.annualLoad.teacherId=:t")
                .setParameter("v", year)
                .setParameter("t", teacherId)
                .getResultList();
    }

    public List<AcademicHistTeacherSemestrialLoad> findHistTeacherSemestrialLoads(int year) {
        return UPA.getPersistenceUnit().createQuery("Select a from AcademicHistTeacherSemestrialLoad a where a.annualLoad.academicYearId=:v")
                .setParameter("v", year)
                .getResultList();
    }

    public List<AcademicHistCourseAssignment> findHistCourseAssignments(int yearId) {
        return UPA.getPersistenceUnit().createQuery("Select a from AcademicHistCourseAssignment a where a.coursePlan.academicYearId=:v")
                .setParameter("v", yearId).getResultList();
    }

    public List<AcademicHistCoursePlan> findHistCoursePlans(int year) {
        return UPA.getPersistenceUnit().createQuery("Select a from AcademicHistCoursePlan a where a.academicYearId=:v")
                .setParameter("v", year).getResultList();
    }

    public String formatDisciplinesNames(String value, boolean autoCreate) {
        StringBuilder s = new StringBuilder();
        for (String n : parseDisciplinesNames(value, autoCreate)) {
            if (s.length() > 0) {
                s.append(", ");
            }
            s.append(n);
        }
        return s.toString();
    }

    public Set<String> parseDisciplinesNames(String value, boolean autoCreate) {
        TreeSet<String> vals = new TreeSet<>();
        for (AcademicDiscipline d : parseDisciplines(value, autoCreate)) {
            vals.add(d.getName());
        }
        return vals;
    }

    public List<AcademicDiscipline> parseDisciplines(String value, boolean autoCreate) {
        PersistenceUnit pu = UPA.getPersistenceUnit();
        if (value == null) {
            value = "";
        }
        List<AcademicDiscipline> ok = new ArrayList<>();
        for (String n : value.split(",|;| ")) {
            String[] cn = codeAndName(n);
            String code = cn[0].toLowerCase();
            AcademicDiscipline t = pu.findByMainField(AcademicDiscipline.class, code);
            if (t == null && autoCreate) {
                if (!StringUtils.isEmpty(code)) {
                    t = new AcademicDiscipline();
                    t.setCode(code);
                    t.setName(cn[1]);
                    pu.persist(t);
                }
            }
            if (t != null) {
                ok.add(t);
            }
        }
        return ok;
    }

    public String formatDisciplinesForLocale(String value, String locale) {
        StringBuilder sb = new StringBuilder();
        for (String s : parseDisciplinesForLocale(value, locale)) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(s);
        }
        return sb.toString();
    }

    public List<String> parseDisciplinesForLocale(String value, String locale) {
        PersistenceUnit pu = UPA.getPersistenceUnit();
        if (value == null) {
            value = "";
        }
        List<String> ok = new ArrayList<>();
        for (String n : value.split(",|;| ")) {
            String[] cn = codeAndName(n);
            String code = cn[0].toLowerCase();
            AcademicDiscipline t = pu.findByMainField(AcademicDiscipline.class, code);
            if (t == null) {
                if (!StringUtils.isEmpty(code)) {
                    ok.add(cn[1]);
                }
            } else {
                ok.add(VrHelper.getValidString(locale, t.getName(), t.getName2(), t.getName3()));
            }
        }
        return ok;
    }

    public List<AcademicDiscipline> parseDisciplinesZombies(String value) {
        PersistenceUnit pu = UPA.getPersistenceUnit();
        if (value == null) {
            value = "";
        }
        List<AcademicDiscipline> ok = new ArrayList<>();
        for (String n : value.split(",|;| ")) {
            String[] cn = codeAndName(n);
            String code = cn[0].toLowerCase();
            AcademicDiscipline t = pu.findByMainField(AcademicDiscipline.class, code);
            if (t == null) {
                if (!StringUtils.isEmpty(code)) {
                    t = new AcademicDiscipline();
                    t.setId(-1);
                    t.setCode(code);
                    t.setName(cn[1]);
                }
            }
            if (t != null) {
                ok.add(t);
            }
        }
        return ok;
    }

    public List<String> parseWords(String value) {
        if (value == null) {
            value = "";
        }
        List<String> ok = new ArrayList<>();
        for (String n : value.split(",|;| ")) {
            String[] cn = codeAndName(n);
            String code = cn[0].toLowerCase();
            if (!StringUtils.isEmpty(code)) {
                ok.add(code);
            }
        }
        return ok;
    }

    public AcademicTeacher getCurrentTeacher() {
        UserSession sm = UserSession.getCurrentSession();
        AppUser user = (sm == null) ? null : sm.getUser();
        if (user != null) {
            return findTeacherByUser(user.getId());
        }
        return null;
    }

    public AcademicStudent getCurrentStudent() {
        UserSession sm = UserSession.getCurrentSession();
        AppUser user = (sm == null) ? null : sm.getUser();
        if (user != null) {
            return findStudentByUser(user.getId());
        }
        return null;
    }

    @Start
    public void startService() {
        core.getManagerProfiles().add("Director");
        core.getManagerProfiles().add("DirectorOfStudies");
    }

    @Install
    public void installService() {
        PersistenceUnit pu = UPA.getPersistenceUnit();
        core.createRight("Custom.Education.CourseLoadUpdateIntents", "Mettre à jours les voeux de autres");
        core.createRight("Custom.Education.CourseLoadUpdateAssignments", "Mettre à jours les affectations");
        core.createRight("Custom.Education.AllTeachersCourseLoad", "Charge tous enseignats");
        core.createRight("Custom.Education.MyCourseLoad", "Ma charge");
        core.createRight("Custom.Education.TeacherCourseLoad", "Charge Detaillee");
        core.createRight("Custom.Education.GlobalStat", "Stat Charge");
        core.createRight("Custom.Education.CourseLoadUpdateIntents", "Mettre à jours les voeux de autres");
        core.createRight("Custom.Education.CourseLoadUpdateAssignments", "Mettre à jours les affectations");
        core.createRight("Custom.FileSystem.RootFileSystem", "Systeme de Fichier Racine");
        core.createRight("Custom.FileSystem.MyFileSystem", "Systeme de Fichier Utilisateur");
        core.createRight("Custom.Education.TeacherPlanning", "TeacherPlanning");
        core.createRight("Custom.Education.MyPlanning", "MyPlanning");
        core.createRight("Custom.Education.ClassPlanning", "ClassPlanning");

        AppUserType teacherType;
        teacherType = new AppUserType();
        teacherType.setName("Teacher");
        teacherType = core.findOrCreate(teacherType);

        AppUserType studentType;
        studentType = new AppUserType();
        studentType.setName("Student");
        studentType = core.findOrCreate(studentType);

        AppProfile teacherProfile = core.findOrCreateCustomProfile("Teacher", "UserType");

        core.addProfileRight(teacherProfile.getId(), "Custom.Education.MyCourseLoad");
        core.addProfileRight(teacherProfile.getId(), "AcademicCourseIntent.Persist");
        core.addProfileRight(teacherProfile.getId(), "AcademicCourseIntent.Remove");
//        core.addProfileRight(teacherProfile.getId(), "AppContact.DefaultEditor");
        core.addProfileRight(teacherProfile.getId(), "AppContact.Load");
        core.addProfileRight(teacherProfile.getId(), "AcademicCoursePlan.Navigate");
        core.addProfileRight(teacherProfile.getId(), "Custom.FileSystem.MyFileSystem");
        for (String navigateOnlyEntity : new String[]{"AppContact"}) {
            core.addProfileRight(teacherProfile.getId(), navigateOnlyEntity + ".Navigate");
        }
        for (String readOnlyEntity : new String[]{"AcademicTeacher", "AcademicClass", "AcademicCoursePlan", "AcademicCourseLevel", "AcademicCourseGroup", "AcademicCourseType", "AcademicProgram", "AcademicDiscipline", "AcademicStudent"
                //,"AcademicCourseAssignment"
        }) {
            core.addProfileRight(teacherProfile.getId(), readOnlyEntity + ".Navigate");
            core.addProfileRight(teacherProfile.getId(), readOnlyEntity + ".DefaultEditor");
        }
        AppProfile studentProfile = core.findOrCreateCustomProfile("Student", "UserType");

        core.addProfileRight(studentProfile.getId(), "Custom.FileSystem.MyFileSystem");

        AppProfile headOfDepartment;
        headOfDepartment = core.findOrCreateCustomProfile(CorePlugin.PROFILE_HEAD_OF_DEPARTMENT, "UserType");

        core.addProfileRight(headOfDepartment.getId(), "Custom.Education.TeacherCourseLoad");
        core.addProfileRight(headOfDepartment.getId(), "Custom.Education.GlobalStat");
        core.addProfileRight(headOfDepartment.getId(), "Custom.Education.AllTeachersCourseLoad");
        core.addProfileRight(headOfDepartment.getId(), "Custom.Education.CourseLoadUpdateIntents");
        core.addProfileRight(headOfDepartment.getId(), "Custom.Education.CourseLoadUpdateAssignments");
        core.addProfileRight(headOfDepartment.getId(), "Custom.FileSystem.MyFileSystem");

        for (net.vpc.upa.Entity ee : pu.getPackage("Education").getEntities(true)) {
            core.addProfileRight(headOfDepartment.getId(), ee.getAbsoluteName() + ".Persist");
            core.addProfileRight(headOfDepartment.getId(), ee.getAbsoluteName() + ".Remove");
            core.addProfileRight(headOfDepartment.getId(), ee.getAbsoluteName() + ".Update");
            core.addProfileRight(headOfDepartment.getId(), ee.getAbsoluteName() + ".Navigate");
            core.addProfileRight(headOfDepartment.getId(), ee.getAbsoluteName() + ".Load");
            core.addProfileRight(headOfDepartment.getId(), ee.getAbsoluteName() + ".DefaultEditor");
        }

        AppProfile directorOfStudies = core.findOrCreateProfile("DirectorOfStudies");
        directorOfStudies.setCustom(true);
        directorOfStudies.setCustomType("UserType");
        pu.merge(directorOfStudies);

        core.addProfileRight(directorOfStudies.getId(), "Custom.Education.TeacherCourseLoad");
        core.addProfileRight(directorOfStudies.getId(), "Custom.Education.GlobalStat");
        core.addProfileRight(directorOfStudies.getId(), "Custom.Education.AllTeachersCourseLoad");
        core.addProfileRight(directorOfStudies.getId(), "Custom.FileSystem.MyFileSystem");

        for (net.vpc.upa.Entity ee : pu.getPackage("Education").getEntities(true)) {
            core.addProfileRight(directorOfStudies.getId(), ee.getAbsoluteName() + ".Navigate");
            core.addProfileRight(directorOfStudies.getId(), ee.getAbsoluteName() + ".Load");
            core.addProfileRight(directorOfStudies.getId(), ee.getAbsoluteName() + ".DefaultEditor");
        }

        AppProfile director = core.findOrCreateCustomProfile("Director", "UserType");

        core.addProfileRight(director.getId(), "Custom.Education.TeacherCourseLoad");
        core.addProfileRight(director.getId(), "Custom.Education.GlobalStat");
        core.addProfileRight(director.getId(), "Custom.Education.AllTeachersCourseLoad");
        core.addProfileRight(director.getId(), "Custom.FileSystem.MyFileSystem");

        for (net.vpc.upa.Entity ee : pu.getPackage("Education").getEntities(true)) {
            core.addProfileRight(director.getId(), ee.getAbsoluteName() + ".Navigate");
            core.addProfileRight(director.getId(), ee.getAbsoluteName() + ".Load");
            core.addProfileRight(director.getId(), ee.getAbsoluteName() + ".DefaultEditor");
        }
        AppConfig appConfig = core.findAppConfig();
        if(appConfig!=null) {
            AppPeriod mainPeriod = appConfig.getMainPeriod();
            if (mainPeriod != null) {
                List<AcademicCoursePlan> academicCoursePlanList = pu.findAll(AcademicCoursePlan.class);
                for (AcademicCoursePlan p : academicCoursePlanList) {
                    if (p.getPeriod() == null) {
                        p.setPeriod(mainPeriod);
                        pu.createUpdateQuery(p).update("period").execute();
                    }
                }
                List<AcademicTeacherSemestrialLoad> academicTeacherSemestrialLoadList = pu.findAll(AcademicTeacherSemestrialLoad.class);
                for (AcademicTeacherSemestrialLoad p : academicTeacherSemestrialLoadList) {
                    if (p.getPeriod() == null) {
                        p.setPeriod(mainPeriod);
                        pu.createUpdateQuery(p).update("period").execute();
                    }
                }
                for (AcademicTeacher academicTeacher : findTeachers()) {
                    updateTeacherPeriod(mainPeriod.getId(), academicTeacher.getId(), -1);
                }
            }
        }
    }


    public void copyAcademicData(int fromPeriodId, int toPeriodId) {
        copyAcademicDataHelper.copyAcademicData(fromPeriodId, toPeriodId);
    }

    private String[] codeAndName(String s) {
        if (s == null) {
            s = "";
        }
        String code = null;
        String name = null;
        int eq = s.indexOf('=');
        if (eq >= 0) {
            code = s.substring(0, eq);
            name = s.substring(eq + 1);
        } else {
            code = s;
            name = s;
        }
        return new String[]{code, name};
    }

    public void importTeachingLoad(int periodId) {
        CorePlugin core = VrApp.getBean(CorePlugin.class);
        try {
            AppPeriod mainPeriod = core.findPeriodOrMain(periodId);
            String year = mainPeriod.getName();
            String version = (String) core.getOrCreateAppPropertyValue("AcademicPlugin.import.version", null, "v01");
            String dir = (String) core.getOrCreateAppPropertyValue("AcademicPlugin.import.configFolder", null, "/Config/Import/${year}");
            Map<String, String> vars = new HashMap<>();
            vars.put("home", System.getProperty("user.home"));
            vars.put("year", year);
            vars.put("version", version);

            dir = StringUtils.replaceDollarPlaceHolders(dir, new MapStringConverter(vars));

            String dataFolder = dir + "/data";

            AcademicPlugin s = VrApp.getBean(AcademicPlugin.class);

            net.vpc.common.vfs.VirtualFileSystem fs = core.getFileSystem();
            s.resetModuleTeaching();
            s.importFile(mainPeriod.getId(),
                    fs.get(dataFolder),
                    new ImportOptions()
            );
        } catch (Exception ex) {
            Logger.getLogger(XlsxLoadImporter.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    public void addUserForTeacher(AcademicTeacher academicTeacher) {
        AppUserType teacherType = VrApp.getBean(CorePlugin.class).findUserType("Teacher");
        AppUser u = core.createUser(academicTeacher.getContact(), teacherType.getId(), academicTeacher.getDepartment().getId(), false, new String[]{"Teacher"});
        academicTeacher.setUser(u);
        UPA.getPersistenceUnit().merge(academicTeacher);
    }

    public boolean addUserForStudent(AcademicStudent academicStudent) {
        AppUserType teacherType = VrApp.getBean(CorePlugin.class).findUserType("Student");
        AppUser u = core.createUser(academicStudent.getContact(), teacherType.getId(), academicStudent.getDepartment().getId(), false, new String[]{"Student"});
        academicStudent.setUser(u);
        UPA.getPersistenceUnit().merge(academicStudent);
        for (AcademicClass c : new AcademicClass[]{academicStudent.getLastClass1(), academicStudent.getLastClass2(), academicStudent.getLastClass3()}) {
            if (c != null) {
                String s = c.getName();
                s = core.validateProfileName(s);
                AppProfile p = core.findOrCreateCustomProfile(s, "AcademicClass");
                core.userAddProfile(u.getId(), p.getCode());
            }

            AcademicProgram pr = academicStudent.getLastClass1() == null ? null : academicStudent.getLastClass1().getProgram();
            if (pr != null) {
                String s = pr.getName();
                s = core.validateProfileName(s);
                AppProfile p = core.findOrCreateCustomProfile(s, "AcademicClass");
                core.userAddProfile(u.getId(), p.getCode());
            }
        }
        AppDepartment d = academicStudent.getDepartment();
        if (d != null) {
            String s = d.getName();
            s = core.validateProfileName(s);
            AppProfile p = core.findOrCreateCustomProfile(s, "Department");
            core.userAddProfile(u.getId(), p.getCode());
        }

        return true;
    }

    public AppPeriod findAcademicYear(String name, String snapshot) {
        return (AppPeriod) UPA.getPersistenceUnit()
                .createQuery("Select a from AppPeriod a where a.name=:t and a.snapshotName=:s")
                .setParameter("t", name)
                .setParameter("s", snapshot)
                .getFirstResultOrNull();
    }

    public AppPeriod findAcademicYear(int id) {
        return (AppPeriod) UPA.getPersistenceUnit()
                .findById(AppPeriod.class, id);
    }

    public AcademicBac findAcademicBac(int id) {
        return (AcademicBac) UPA.getPersistenceUnit()
                .findById(AcademicBac.class, id);
    }

    public AcademicBac findAcademicBac(String name) {
        return (AcademicBac) UPA.getPersistenceUnit()
                .findByField(AcademicBac.class, "name", name);
    }

    public AcademicPreClass findAcademicPreClass(int id) {
        return (AcademicPreClass) UPA.getPersistenceUnit()
                .findById(AcademicPreClass.class, id);
    }

    public AcademicPreClass findAcademicPreClass(String name) {
        return (AcademicPreClass) UPA.getPersistenceUnit()
                .findByField(AcademicPreClass.class, "name", name);
    }

    public AppPeriod findAcademicYearSnapshot(String t, String snapshotName) {
        return (AppPeriod) UPA.getPersistenceUnit()
                .createQuery("Select a from AppPeriod a where a.name=:t and a.snapshotName=:s")
                .setParameter("t", t)
                .setParameter("s", snapshotName)
                .getFirstResultOrNull();
    }

    public List<AppPeriod> findAcademicYearSnapshots(String t) {
        return UPA.getPersistenceUnit()
                .createQuery("Select a from AppPeriod a where a.name=:t and a.snapshotName!=null")
                .setParameter("t", t)
                .getResultList();
    }

    public AppPeriod findOrCreateAcademicYear(String academicYearName, String snapshot) {
        AppPeriod z = findAcademicYear(academicYearName, snapshot);
        if (z == null) {
            z = new AppPeriod();
            z.setName(academicYearName);
            z.setSnapshotName(snapshot);
            add(z);
        }
        return z;
    }

    public AppPeriod createAcademicYear(String academicYearName, String snapshot) {
        AppPeriod z = findAcademicYear(academicYearName, snapshot);
        if (z != null) {
            throw new IllegalArgumentException("Already exists");
        }
        z = new AppPeriod();
        z.setName(academicYearName);
        z.setSnapshotName(snapshot);
        add(z);

        return z;
    }

    public void add(Object t) {
        if (t instanceof AppPeriod) {
            AppPeriod a = (AppPeriod) t;
            a.setCreationTime(new DateTime());
            a.setSnapshotName(null);
        }
        UPA.getPersistenceUnit().persist(t);
    }

    @Override
    public Map<String, Object> getExtendedPropertyValues(Object o) {
        if (o instanceof AppUser) {
            AcademicTeacher t = findTeacherByUser(((AppUser) o).getId());
            if (t != null) {
                AppConfig appConfig = core.findAppConfig();
                if(appConfig!=null && appConfig.getMainPeriod()!=null) {
                    AcademicTeacherPeriod pp = findAcademicTeacherPeriod(appConfig.getMainPeriod().getId(), t);
                    HashMap<String, Object> m = new HashMap<>();
                    m.put("discipline", t.getDiscipline());
                    m.put("degree", pp.getDegree() == null ? null : pp.getDegree().getName());
                    m.put("degreeCode", pp.getDegree() == null ? null : pp.getDegree().getCode());
                    m.put("situation", pp.getSituation() == null ? null : pp.getSituation().getName());
                    m.put("enabled", pp.isEnabled());
                    return m;
                }
            }
        }
        return null;
    }

    @Override
    public Set<String> getExtendedPropertyNames(Class o) {
        if (o.equals(AppUser.class)) {
            return new HashSet<>(Arrays.asList("discipline", "degree", "degreeCode", "situation", "enabled"));
        }
        return null;
    }

    public String getValidName(AcademicTeacher t) {
        String name = null;
        if (t.getContact() != null) {
            name = t.getContact().getFullName();
        }
        if (StringUtils.isEmpty(name) && t.getUser() != null) {
            name = t.getUser().getLogin();
        }
        if (StringUtils.isEmpty(name)) {
            name = "Teacher #" + t.getId();
        }
        return (name);
    }

    public String getValidName(AcademicStudent t) {
        String name = null;
        if (t.getContact() != null) {
            name = t.getContact().getFullName();
        }
        if (StringUtils.isEmpty(name) && t.getUser() != null) {
            name = t.getUser().getLogin();
        }
        if (StringUtils.isEmpty(name)) {
            name = "Teacher #" + t.getId();
        }
        return (name);
    }

    public List<AcademicClass> findAcademicUpHierarchyList(AcademicClass[] classes, Map<Integer, AcademicClass> allClasses) {
        HashSet<Integer> visited = new HashSet<>();
        if (allClasses == null) {
            allClasses = findAcademicClassesMap();
        }
        List<AcademicClass> result = new ArrayList<>();
        Stack<Integer> stack = new Stack<>();
        for (AcademicClass c : classes) {
            if (c != null) {
                stack.push(c.getId());
            }
        }
        while (!stack.isEmpty()) {
            int i = stack.pop();
            if (!visited.contains(i)) {
                AcademicClass c = allClasses.get(i);
                if (c != null) {
                    result.add(c);
                    AcademicClass p = c.getParent();
                    if (p != null) {
                        stack.push(p.getId());
                    }
                }
            }
        }
        return result;
    }

    public List<AcademicClass> findAcademicDownHierarchyList(AcademicClass[] classes, Map<Integer, AcademicClass> allClasses) {
        HashSet<Integer> visited = new HashSet<>();
        if (allClasses == null) {
            allClasses = findAcademicClassesMap();
        }
        List<AcademicClass> result = new ArrayList<>();
        Stack<Integer> stack = new Stack<>();
        for (AcademicClass c : classes) {
            if (c != null) {
                stack.push(c.getId());
            }
        }
        while (!stack.isEmpty()) {
            int i = stack.pop();
            if (!visited.contains(i)) {
                AcademicClass c = allClasses.get(i);
                if (c != null) {
                    result.add(c);
                    for (AcademicClass p : allClasses.values()) {
                        if (p.getParent() != null && p.getParent().getId() == c.getId()) {
                            stack.push(p.getId());
                        }
                    }
                }
            }
        }
        return result;
    }

    public List<AcademicPreClass> findAcademicDownHierarchyList(AcademicPreClass[] classes, Map<Integer, AcademicPreClass> allClasses) {
        HashSet<Integer> visited = new HashSet<>();
        if (allClasses == null) {
            allClasses = findAcademicPreClassesMap();
        }
        List<AcademicPreClass> result = new ArrayList<>();
        Stack<Integer> stack = new Stack<>();
        for (AcademicPreClass c : classes) {
            if (c != null) {
                stack.push(c.getId());
            }
        }
        while (!stack.isEmpty()) {
            int i = stack.pop();
            if (!visited.contains(i)) {
                AcademicPreClass c = allClasses.get(i);
                if (c != null) {
                    result.add(c);
                    for (AcademicPreClass p : allClasses.values()) {
                        if (p.getParent() != null && p.getParent().getId() == c.getId()) {
                            stack.push(p.getId());
                        }
                    }
                }
            }
        }
        return result;
    }

    public List<AcademicBac> findAcademicDownHierarchyList(AcademicBac[] classes, Map<Integer, AcademicBac> allClasses) {
        HashSet<Integer> visited = new HashSet<>();
        if (allClasses == null) {
            allClasses = findAcademicBacsMap();
        }
        List<AcademicBac> result = new ArrayList<>();
        Stack<Integer> stack = new Stack<>();
        for (AcademicBac c : classes) {
            if (c != null) {
                stack.push(c.getId());
            }
        }
        while (!stack.isEmpty()) {
            int i = stack.pop();
            if (!visited.contains(i)) {
                AcademicBac c = allClasses.get(i);
                if (c != null) {
                    result.add(c);
                    for (AcademicBac p : allClasses.values()) {
                        if (p.getParent() != null && p.getParent().getId() == c.getId()) {
                            stack.push(p.getId());
                        }
                    }
                }
            }
        }
        return result;
    }

    public void validateAcademicData(int periodId) {
        PersistenceUnit pu = UPA.getPersistenceUnit();
        Map<Integer, AcademicClass> academicClasses = findAcademicClassesMap();

        //should remove me!
//        for (AcademicCoursePlan s : findCoursePlans()) {
//            if (s.getCourseLevel().getAcademicClass() != null && s.getCourseLevel().getSemester() != null) {
//                AcademicCourseLevel lvl = findCourseLevels(s.getStudentClass().getId(), s.getSemester().getId());
//                if (lvl != null) {
//                    s.setCourseLevel(lvl);
//                    pu.merge(s);
//                }
//            }
//        }
        for (AcademicStudent s : findStudents()) {
            AppUser u = s.getUser();
            AppContact c = s.getContact();
            AppDepartment d = s.getDepartment();

            if (c == null && u != null) {
                c = u.getContact();
            }
            if (d == null && u != null) {
                d = u.getDepartment();
            }
            if (s.getDepartment() == null && d != null) {
                s.setDepartment(d);
                UPA.getPersistenceUnit().merge(s);
            }
            if (s.getContact() == null && c != null) {
                s.setContact(c);
                UPA.getPersistenceUnit().merge(s);
            }
            if (u != null) {

                if (u.getDepartment() == null && d != null) {
                    u.setDepartment(d);
                    UPA.getPersistenceUnit().merge(u);
                }
                if (u.getContact() == null && c != null) {
                    u.setContact(c);
                    UPA.getPersistenceUnit().merge(u);
                }
            }
            if (c != null) {
                HashSet<Integer> goodProfiles = new HashSet<>();

                {
                    if (d != null) {
                        String n = core.validateProfileName(d.getCode());
                        AppProfile p = core.findOrCreateCustomProfile(n, "Department");
                        goodProfiles.add(p.getId());
                    }
                }
                {
                    AppProfile p = core.findOrCreateCustomProfile("Student", "UserType");
                    goodProfiles.add(p.getId());
                }

                TreeSet<String> classNames = new TreeSet<>();
                AcademicClass[] clsArr = new AcademicClass[]{s.getLastClass1(), s.getLastClass2(), s.getLastClass3()};
                for (AcademicClass ac : clsArr) {
                    if (ac != null) {
                        String n = core.validateProfileName(ac.getName());
                        classNames.add(n);
                    }
                }
                for (AcademicClass ac : findAcademicUpHierarchyList(clsArr, academicClasses)) {
                    if (ac != null) {
                        String n = core.validateProfileName(ac.getName());
                        //ignore inherited profiles in suffix
//                        classNames.add(n);
                        AppProfile p = core.findOrCreateCustomProfile(n, "AcademicClass");
                        goodProfiles.add(p.getId());

                        AcademicProgram pr = ac.getProgram();
                        if (pr != null) {
                            n = core.validateProfileName(ac.getName());
                            p = core.findOrCreateCustomProfile(n, "AcademicProgram");
                            goodProfiles.add(p.getId());
                        }
                    }
                }
                StringBuilder goodSuffix = new StringBuilder();
                for (String cn : classNames) {
                    if (goodSuffix.length() > 0) {
                        goodSuffix.append("/");
                    }
                    goodSuffix.append(cn);
                }
                c.setPositionSuffix(goodSuffix.toString());
                pu.merge(c);

                if (u != null) {
                    List<AppProfile> oldProfiles = core.findProfilesByUser(u.getId());
                    for (AppProfile p : oldProfiles) {
                        if (goodProfiles.contains(p.getId())) {
                            goodProfiles.remove(p.getId());
                            //ok
                        } else if (p.isCustom() && ("Department".equals(p.getName()) || "AcademicClass".equals(p.getName()) || "AcademicProgram".equals(p.getName()))) {
                            core.userRemoveProfile(u.getId(), p.getId());
                        }
                    }
                    for (Integer toAdd : goodProfiles) {
                        core.userAddProfile(u.getId(), toAdd);
                    }
                }
            }
        }
        StatCache statCache = new StatCache();
        for (AcademicTeacher s : findTeachers()) {
            AppUser u = s.getUser();
            AppContact c = s.getContact();
            AppDepartment d = s.getDepartment();

            if (c == null && u != null) {
                c = u.getContact();
            }
            if (d == null && u != null) {
                d = u.getDepartment();
            }
            if (s.getDepartment() == null && d != null) {
                s.setDepartment(d);
                UPA.getPersistenceUnit().merge(s);
            }
            if (s.getContact() == null && c != null) {
                s.setContact(c);
                UPA.getPersistenceUnit().merge(s);
            }
            if (u != null) {

                if (u.getDepartment() == null && d != null) {
                    u.setDepartment(d);
                    UPA.getPersistenceUnit().merge(u);
                }
                if (u.getContact() == null && c != null) {
                    u.setContact(c);
                    UPA.getPersistenceUnit().merge(u);
                }
            }
            if (c != null) {
                HashSet<Integer> goodProfiles = new HashSet<>();
                String depCode = null;
                {
                    if (d != null) {
                        String n = core.validateProfileName(d.getCode());
                        depCode = d.getCode();
                        AppProfile p = core.findOrCreateCustomProfile(n, "Department");
                        goodProfiles.add(p.getId());
                    }
                }
                {
                    AppProfile p = core.findOrCreateCustomProfile("Teacher", "UserType");
                    goodProfiles.add(p.getId());
                }
                //find classes teached by  teacher
                List<AcademicClass> myClasses = new ArrayList<>();
                Set<String> myPrograms = new HashSet<>();
                for (AcademicCourseAssignment a : findCourseAssignments(periodId, s.getId(), null, new CourseFilter().setIncludeIntents(false), statCache)) {
                    myPrograms.add(a.getCoursePlan().getCourseLevel().getAcademicClass().getProgram().getName());
                    myClasses.add(a.getSubClass());
                    myClasses.add(a.getCoursePlan().getCourseLevel().getAcademicClass());
                }

                for (AcademicClass ac : findAcademicUpHierarchyList(myClasses.toArray(new AcademicClass[myClasses.size()]), academicClasses)) {
                    if (ac != null) {
                        String n = core.validateProfileName(ac.getName());
                        //ignore inherited profiles in suffix
//                        classNames.add(n);
                        AppProfile p = core.findOrCreateCustomProfile(n, "AcademicClass");
                        goodProfiles.add(p.getId());

                        AcademicProgram pr = ac.getProgram();
                        if (pr != null) {
                            myPrograms.add(pr.getName());
                        }
                    }
                }
                for (String myProgram : myPrograms) {
                    String n = core.validateProfileName(myProgram);
                    AppProfile p = core.findOrCreateCustomProfile(n, "AcademicProgram");
                    goodProfiles.add(p.getId());
                }

//                                    n = a.getCoursePlan().getStudentClass().getName();
//                    p = core.findOrCreateCustomProfile(n, "AcademicClass");
//                    goodProfiles.add(p.getId());
                boolean perm = false;
                List<AppProfile> oldProfiles = u == null ? new ArrayList<AppProfile>() : core.findProfilesByUser(u.getId());
                for (AppProfile op : oldProfiles) {
                    if ("Permanent".equals(op.getName())) {
                        perm = true;
                        break;
                    }
                }

                AcademicTeacherPeriod academicTeacherPeriod = findAcademicTeacherPeriod(periodId, s);
                String degreeCode = academicTeacherPeriod.getDegree() == null ? null : academicTeacherPeriod.getDegree().getCode();
                StringBuilder goodSuffix = new StringBuilder();
                goodSuffix.append("Ens.");
                if (perm) {
                    goodSuffix.append(" ").append("Perm");
                }
                if (degreeCode != null) {
                    goodSuffix.append(" ").append(degreeCode);
                }
                if (depCode != null) {
                    goodSuffix.append(" ").append(depCode);
                }
                c.setPositionSuffix(goodSuffix.toString());
                pu.merge(c);

                if (u != null) {
                    for (AppProfile p : oldProfiles) {
                        if (goodProfiles.contains(p.getId())) {
                            goodProfiles.remove(p.getId());
                            //ok
                        } else if (p.isCustom() && ("Department".equals(p.getName()) || "AcademicClass".equals(p.getName()) || "AcademicProgram".equals(p.getName()))) {
                            core.userRemoveProfile(u.getId(), p.getId());
                        }
                    }
                    for (Integer toAdd : goodProfiles) {
                        core.userAddProfile(u.getId(), toAdd);
                    }
                }
            }
        }
        generateTeacherAssignementDocumentsFolder(periodId);
    }

    public List<AcademicClass> findStudentClasses(int studentId, boolean down, boolean up) {
        AcademicStudent student = findStudent(studentId);
        if (student == null) {
            return Collections.EMPTY_LIST;
        }
        AcademicClass[] refs = new AcademicClass[]{student.getLastClass1(), student.getLastClass2(), student.getLastClass3()};
        List<AcademicClass> upList = null;
        List<AcademicClass> downList = null;
        Map<Integer, AcademicClass> mm = findAcademicClassesMap();
        if (down) {
            upList = findAcademicDownHierarchyList(refs, mm);
        }
        if (up) {
            upList = findAcademicUpHierarchyList(refs, mm);
        }
        HashSet<Integer> visited = new HashSet<>();
        List<AcademicClass> all = new ArrayList<>();
        for (List<AcademicClass> cls : Arrays.asList(upList, Arrays.asList(refs), downList)) {
            if (cls != null) {
                for (AcademicClass a : cls) {
                    if (a != null) {
                        if (!visited.contains(a.getId())) {
                            visited.add(a.getId());
                            all.add(a);
                        }
                    }
                }
            }
        }
        return all;

    }

    public void updateViewsCounterforTeacherCV(int t) {
        AcademicTeacherCV cv = findOrCreateAcademicTeacherCV(t);
        if (cv != null) {
            cv.setViewsCounter(cv.getViewsCounter() + 1);
            UPA.getPersistenceUnit().merge(cv);
        }
    }

    public AcademicTeacherCV findOrCreateAcademicTeacherCV(final int t) {
        return UPA.getContext().invokePrivileged(new Action<AcademicTeacherCV>() {

            @Override
            public AcademicTeacherCV run() {
                PersistenceUnit pu = UPA.getPersistenceUnit();
                AcademicTeacherCV a = pu.createQuery("Select u from AcademicTeacherCV u where u.teacherId=:id")
                        .setParameter("id", t).getFirstResultOrNull();
                if (a != null) {
                    return a;
                }
                //check teacher
                AcademicTeacher teacher = VrApp.getBean(AcademicPlugin.class).findTeacher(t);
                if (teacher != null) {
                    final AcademicTeacherCV cv = new AcademicTeacherCV();
                    cv.setTeacher(teacher);
                    UPA.getPersistenceUnit().persist(cv);
                    return cv;
                }
                return null;
            }
        }, null);
    }

    public void updateTeacherPeriod(int periodId, int teacherId, int copyFromPeriod) {
//        AppPeriod p = core.findAppConfig().getMainPeriod();
        AcademicTeacher teacher = findTeacher(teacherId);
        AppPeriod period = core.findPeriod(periodId);
        PersistenceUnit pu = UPA.getPersistenceUnit();
        List<AcademicTeacherPeriod> items = pu.createQuery("Select u from AcademicTeacherPeriod u where u.teacherId=:teacherId and u.periodId=:periodId")
                .setParameter("periodId", periodId)
                .setParameter("teacherId", teacherId)
                .getResultList();
        boolean toPersist = items.size() == 0;
        while (items.size() > 1) {
            AcademicTeacherPeriod i = items.get(0);
            pu.remove(i);
            log.severe("Duplicated AcademicTeacherPeriod " + items.size());
            items.remove(0);
        }
        AcademicTeacherPeriod item;
        if (toPersist) {
            item = new AcademicTeacherPeriod();
            item.setPeriod(period);
            item.setTeacher(teacher);
        } else {
            item = items.get(0);
        }
        if (copyFromPeriod <= 0) {
            item.setDegree(teacher.getDegree());
            item.setSituation(teacher.getSituation());
            item.setEnabled(teacher.isEnabled());
            item.setDepartment(teacher.getDepartment());
        } else {
            AcademicTeacherPeriod other = findAcademicTeacherPeriod(copyFromPeriod, teacher);
            item.setDegree(other.getDegree());
            item.setSituation(other.getSituation());
            item.setEnabled(other.isEnabled());
            item.setDepartment(other.getDepartment());
        }
        if (toPersist) {
            pu.persist(item);
        } else {
            pu.merge(item);
        }
    }

    public void generateTeachingLoad(int periodId, CourseFilter courseFilter, String version0) throws IOException {
        teacherGenerationHelper.generateTeachingLoad(periodId, courseFilter, version0);
    }

    public Record getAppDepartmentPeriodRecord(int periodId, int departmentId) {
        PersistenceUnit pu = UPA.getPersistenceUnit();
        return pu.findRecordById(AppDepartmentPeriod.class, getAppDepartmentPeriod(periodId, departmentId).getId());
    }

    public AppDepartmentPeriod getAppDepartmentPeriod(int periodId, int departmentId) {
        PersistenceUnit pu = UPA.getPersistenceUnit();
        AppDepartmentPeriod d = pu.createQueryBuilder(AppDepartmentPeriod.class)
                .byField("periodId", periodId)
                .byField("departmentId", departmentId).getFirstResultOrNull();
        if (d == null) {
            d = new AppDepartmentPeriod();
            d.setDepartment(core.findDepartment(departmentId));
            d.setPeriod(core.findPeriod(periodId));
            if (d.getDepartment() == null || d.getPeriod() == null) {
                throw new RuntimeException("Invalid");
            }
            pu.persist(d);
        }
        return d;
    }

    public AcademicConversionTableHelper buildAcademicConversionTable(int id) {
        PersistenceUnit pu = UPA.getPersistenceUnit();
        AcademicLoadConversionTable t = pu.findById(AcademicLoadConversionTable.class, id);
        if (t == null) {
            return null;
        }
        List<AcademicLoadConversionRow> rows = pu
                .createQueryBuilder(AcademicLoadConversionRow.class)
                .byField("conversionTableId", t.getId())
                .getResultList();
        AcademicConversionTableHelper h = new AcademicConversionTableHelper(t);
        for (AcademicLoadConversionRow row : rows) {
            h.add(row);
        }
        return h;
    }
}
