package edu.umdearborn.astronomyapp.service;

import java.util.Date;
import java.util.List;
import java.util.Map;

import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;
import javax.transaction.Transactional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import edu.umdearborn.astronomyapp.controller.exception.GroupAlterationException;
import edu.umdearborn.astronomyapp.entity.Answer;
import edu.umdearborn.astronomyapp.entity.CourseUser;
import edu.umdearborn.astronomyapp.entity.GroupMember;
import edu.umdearborn.astronomyapp.entity.Module;
import edu.umdearborn.astronomyapp.entity.ModuleGroup;
import edu.umdearborn.astronomyapp.entity.Question;
import edu.umdearborn.astronomyapp.util.ResultListUtil;

@Service
@Transactional
public class GroupServiceImpl implements GroupService {

  private static final Logger logger = LoggerFactory.getLogger(GroupServiceImpl.class);

  private EntityManager   entityManager;
  private PasswordEncoder passwordEncoder;

  public GroupServiceImpl(EntityManager entityManager, PasswordEncoder passwordEncoder) {
    this.entityManager = entityManager;
    this.passwordEncoder = passwordEncoder;
  }

  @Override
  public List<CourseUser> joinGroup(String courseUserId, String moduleId, String groupId) {

    enforceNotInGroup(courseUserId, groupId);

    logger.debug("Joining group: '{}' for course user: '{}' for module: '{}'", groupId,
        courseUserId, moduleId);
    TypedQuery<ModuleGroup> query = entityManager
        .createQuery("select g from ModuleGroup g where g.id =  :groupId", ModuleGroup.class);
    query.setParameter("groupId", groupId);
    List<ModuleGroup> result = query.getResultList();

    ModuleGroup group = result.get(0);
    GroupMember member = new GroupMember();
    CourseUser user = new CourseUser();
    user.setId(courseUserId);
    member.setCourseUser(user);
    member.setModuleGroup(group);
    entityManager.persist(member);

    return getUsersInGroup(groupId);

  }

  @Override
  public ModuleGroup createGroup(String courseUserId, String moduleId) {

    enforceNotInGroup(courseUserId, moduleId);

    logger.debug("Creating group for course user: '{}' for module: '{}'", courseUserId, moduleId);
    ModuleGroup group = new ModuleGroup();
    Module module = new Module();
    module.setId(moduleId);
    group.setModule(module);
    entityManager.persist(group);

    GroupMember member = new GroupMember();
    CourseUser user = new CourseUser();
    user.setId(courseUserId);
    member.setCourseUser(user);
    member.setModuleGroup(group);
    entityManager.persist(member);

    return group;
  }

  @Override
  public boolean isInAGroup(String courseUserId, String moduleId) {

    TypedQuery<Boolean> query = entityManager.createQuery(
        "select count(gm) > 0 from GroupMember gm join gm.moduleGroup g join g.module m "
            + "join gm.courseUser cu where cu.id = :courseUserId and m.id = :moduleId",
        Boolean.class);
    query.setParameter("courseUserId", courseUserId).setParameter("moduleId", moduleId);

    return query.getSingleResult();
  }

  private void enforceNotInGroup(String courseUserId, String moduleId) {

    TypedQuery<Boolean> query = entityManager.createQuery(
        "select count(u) > 0 from CourseUser u where u.id = :courseUserId and u.role != :role",
        Boolean.class);
    query.setParameter("courseUserId", courseUserId).setParameter("role",
        CourseUser.CourseRole.STUDENT);
    boolean isNotStudentRole = query.getSingleResult();

    if (isInAGroup(courseUserId, moduleId) || isNotStudentRole) {
      logger.info("Course user: '{}' cannot join group for module: '{}'", courseUserId, moduleId);
      throw new GroupAlterationException(
          "Course user: " + courseUserId + " cannot join group for module: " + moduleId);
    }

    logger.debug("Course user: '{}' can join group for module: '{}'", courseUserId, moduleId);
  }

  @Override
  public List<CourseUser> getUsersInGroup(String groupId) {

    logger.debug("Getting group members in group: '{}'", groupId);
    TypedQuery<CourseUser> query = entityManager.createQuery(
        "select cu from GroupMember gm join gm.moduleGroup g join gm.courseUser cu join cu.user u"
            + " where g.id = :groupId and cu.isActive = true and u.isEnabled = true",
        CourseUser.class);
    query.setParameter("groupId", groupId);

    return query.getResultList();
  }

  @Override
  public ModuleGroup getGroup(String courseUserId, String moduleId) {

    logger.debug("Getting group for course user: '{}' for module: '{}'", courseUserId, moduleId);
    TypedQuery<ModuleGroup> query = entityManager
        .createQuery("select mg from GroupMember gm join gm.moduleGroup mg join gm.courseUser cu "
            + "join mg.module m where cu.id = :courseUserId and "
            + "cu.isActive = true and m.id = :moduleId", ModuleGroup.class);
    query.setParameter("courseUserId", courseUserId).setParameter("moduleId", moduleId);
    List<ModuleGroup> result = query.getResultList();

    if (ResultListUtil.hasResult(result)) {
      return result.get(0);
    }

    logger.info("No group for course user: '{}' for module: '{}'", courseUserId, moduleId);
    return null;
  }

  @Override
  public CourseUser checkin(String email, String password, String groupId) {

    TypedQuery<CourseUser> query = entityManager.createQuery(
        "select cu from GroupMember gm join gm.courseUser cu join fetch cu.user u join "
            + "gm.moduleGroup g where g.id = :groupId and lower(u.email) = lower(:email) and "
            + "cu.isActive = true and u.isEnabled = true",
        CourseUser.class);
    query.setParameter("email", email).setParameter("groupId", groupId);
    List<CourseUser> result = query.getResultList();

    if (ResultListUtil.hasResult(result)
        && passwordEncoder.matches(password, result.get(0).getUser().getPassword())) {

      logger.debug("Checin successful for user: '{}' in group: '{}'", email, groupId);
      return result.get(0);
    }

    logger.info("Checin not successful for user: '{}' in group: '{}'", email, groupId);
    return null;
  }

  @Override
  public boolean hasLock(String groupId, List<String> checkedIn) {

    logger.debug("Checking if group: '{}' has the lock", groupId);
    TypedQuery<Boolean> query = entityManager.createQuery(
        "select count(cu) = 0 from GroupMember gm join gm.moduleGroup g join gm.courseUser cu "
            + "join cu.user u where g.id = :groupId and cu.isActive = true and "
            + "u.isEnabled = true and cu.id not in (:checkedIn)",
        Boolean.class);
    query.setParameter("groupId", groupId).setParameter("checkedIn", checkedIn);

    return query.getSingleResult();
  }

  @Override
  public List<Answer> saveAnswers(Map<String, String> answers, String groupId) {

    logger.debug("Saving answers for group: '{}'", groupId);
    List<Answer> savedAnswers = getAnswers(groupId, true);
    if (ResultListUtil.hasResult(savedAnswers)) {

      ModuleGroup group = new ModuleGroup();
      group.setId(groupId);

      for (String key : answers.keySet()) {
        savedAnswers.parallelStream().filter(a -> a.getQuestion().getId() == key).findAny()
            .ifPresent(a -> {
              a.setValue(answers.get(key));
            });
      }

      return getAnswers(groupId, true);
    }

    return null;
  }

  @Override
  public Long submissionNumber(String groupId) {

    logger.debug("Getting submission number for groupId: '{}'", groupId);
    TypedQuery<Long> query = entityManager.createQuery(
        "select max(a.submissionNumber) from Answer a join a.group g join g.module m where "
            + "g.groupId = :groupId",
        Long.class);
    query.setParameter("groupId", groupId);
    List<Long> result = query.getResultList();

    if (ResultListUtil.hasResult(result)) {
      return result.get(0);
    }

    logger.warn("Could not retrieve submission number for groupId: '{}'", groupId);
    return null;
  }

  @Override
  public List<Answer> getAnswers(String groupId, boolean getSavedAnswers) {
    logger.debug("Getting answers for group: '{}'", groupId);
    StringBuilder jpql = new StringBuilder(
        "select Answer a join a.group g join g.module m where g.id = :groupId and "
            + "a.submissionNumber = :submissionNumber");

    if (!getSavedAnswers) {
      logger.debug("Appending saved answers querery for groupId: '{}'", groupId);
      jpql.append(" and a.submissionDate is not null");
    }

    TypedQuery<Answer> query = entityManager.createQuery(jpql.toString(), Answer.class);
    query.setParameter("groupId", groupId);

    if (getSavedAnswers) {
      query.setParameter("submissionNumber", 0);
    } else {
      query.setParameter("submissionNumber", submissionNumber(groupId));
    }

    return query.getResultList();
  }

  @Override
  public void finalizeGroup(String groupId) {
    entityManager.createQuery("update ModuleGroup g set g.isLocked = true where g.id = :groupId")
        .setParameter("groupId", groupId).executeUpdate();

    TypedQuery<Question> questionQuery = entityManager.createQuery(
        "select distinct(q) from Question q join q.page p join p.module m where m.id = "
            + "(select m.id from ModuleGroup g join g.module m where g.id = :groupId)",
        Question.class);
    questionQuery.setParameter("groupId", groupId);
    List<Question> questionResult = questionQuery.getResultList();

    ModuleGroup group = new ModuleGroup();
    group.setId(groupId);
    if (ResultListUtil.hasResult(questionResult)) {
      logger.debug("Creating answers...");

      questionResult.stream().map(q -> {
        Answer answer = new Answer();
        answer.setGroup(group);
        answer.setQuestion(q);
        return answer;
      }).forEach(entity -> {
        logger.debug("Persisting: {}", entity);
        entityManager.persist(entity);
      });
    }

  }

  @Override
  public List<Answer> submitAnswers(String groupId) {
    logger.debug("Submitting asnwers for group: '{}'", groupId);
    List<Answer> answers = getAnswers(groupId, true);

    if (ResultListUtil.hasResult(answers)) {
      Date date = new Date();
      answers.parallelStream().map(a -> {
        a.setId(null);
        a.setSubmissionNumber(submissionNumber(groupId) + 1);
        a.setSubmissionTimestamp(date);
        return a;
      }).forEach(entityManager::persist);;
    }

    return getAnswers(groupId, false);
  }

  @Override
  public List<CourseUser> removeFromGroup(String groupId, String courseUserId) {
    logger.debug("Deleting group member in group: '{}' with course user id: '{}'", groupId,
        courseUserId);
    entityManager
        .createQuery(
            "delete from GroupMember m where m.id in (select sm.id from GroupMember sm join "
                + " sm.moduleGroup g join sm.courseUser u where g.id = :groupId and "
                + "u.id = :courseUserId)")
        .setParameter("groupId", groupId).setParameter("courseUserId", courseUserId)
        .executeUpdate();

    TypedQuery<Boolean> isGroupEmptyQuery = entityManager.createQuery(
        "select count(m) = 0 from GroupMember m join m.moduleGroup g where g.id = :groupId",
        Boolean.class).setParameter("groupId", groupId);
    boolean isGroupEmpty = isGroupEmptyQuery.getSingleResult();

    if (isGroupEmpty) {
      logger.debug("Removing group instance for group: '{}'", groupId);
      entityManager.createQuery("delete from ModuleGroup g where g.id = :groupId")
          .setParameter("groupId", groupId).executeUpdate();

      return null;
    }

    return getUsersInGroup(groupId);
  }

  @Override
  public List<CourseUser> getFreeUsers(String courseId, String moduleId) {

    TypedQuery<CourseUser> query =
        entityManager
            .createQuery(
                "select u from CourseUser u join u.course c where c.id = :courseId and u.id not in "
                    + "(select cu.id from GroupMember gm join gm.moduleGroup g join g.module m join "
                    + "gm.courseUser cu where m.id = :moduleId) and u.role = 'STUDENT'",
                CourseUser.class)
            .setParameter("courseId", courseId).setParameter("moduleId", moduleId);

    return query.getResultList();
  }
}
