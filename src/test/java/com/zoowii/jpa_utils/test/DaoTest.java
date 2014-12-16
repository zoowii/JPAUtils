package com.zoowii.jpa_utils.test;

import com.zoowii.jpa_utils.core.Session;
import com.zoowii.jpa_utils.test.models.Employee;
import com.zoowii.jpa_utils.query.Query;
import com.zoowii.jpa_utils.util.StringUtil;
import junit.framework.TestCase;
import org.apache.log4j.Logger;

import java.util.List;
import java.util.Random;

public class DaoTest extends TestCase {
    private static final Logger logger = Logger.getLogger(DaoTest.class);

    public void testCreate() {
        Session session = Session.getSession("mysql");
        session.begin();
        try {
            for (int i = 0; i < 10; ++i) {
                Employee employee = new Employee();
                employee.setName("employee_" + StringUtil.randomString(10));
                employee.setAge(new Random().nextInt(100));
                employee.save(session);
                logger.info("new employee " + employee.getId());
            }
            session.commit();
        } catch (Exception e) {
            e.printStackTrace();
            session.rollback();
        } finally {
            session.close();
        }
    }

    public void testQuery() {
        Session session = Session.getSession("mysql");
        session.begin();
        try {
            Query<Employee> query = Employee.find.where().gt("age", 50);
            query = query.limit(8);
            List<Employee> employees = query.all();
            for (int i = 0; i < employees.size(); ++i) {
                Employee employee = employees.get(i);
                logger.info((i + 1) + ". employee " + employee.getId());
            }
            logger.info("total: " + query.count());
            session.commit();
        } catch (Exception e) {
            e.printStackTrace();
            session.rollback();
        } finally {
            session.close();
        }
    }

    public void testCreateAndQuery() {
        testCreate();
        testQuery();
    }
}
