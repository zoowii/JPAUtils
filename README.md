JPAUtils
=====

ActiveRecord-like implementation based on JPA(eg. Hibernate) or direct hibernate session

2014七夕献礼,庆祝自己向证明注孤生迈出了更加坚实的一步

## Author

* zoowii(https://github.com/zoowii)

## Features

* 底层基于JPA或者Hibernate的Session/SessionFactory，基于HQL/SQL，比自己再轮一个类HQL稳定
* 可以自动从JPA配置创建管理session,也可以手动指定EntityManagerFactory/EntityManager/SessionFactory(hibernate)/Session(hibernate)来构造jpa-utils中的Session来使用
* 提供类似ActiveRecord的使用方便友好的API，特别是查询API
* 查询的核心Finder类可以单独使用，直接使用到现有的使用JPA或Hibernate的代码中，只需要根据现有EntityManager/Session(hibernate)构造一个jpa-utils的session，然后使用Finder类来查询就好了

## Usages

    // maven
    !!! First deploy it to you local maven nexus, then.
    <dependency>
         <groupId>com.zoowii</groupId>
         <artifactId>jpa-utils</artifactId>
         <version>x.y.z</version>
    </dependency>

    // create
    Session session = EntitySession.currentSession(); // or Session.getSession(persistentUnitName);
    session.begin();
    try {
        for (int i = 0; i < 10; ++i) {
            Employee employee = new Employee();
            employee.setName("employee_" + StringUtil.randomString(10));
            employee.setAge(new Random().nextInt(100));
            employee.save(); // or employee.save(session);
            logger.info("new employee " + employee.getId());
        }
        session.commit();
    } catch (Exception e) {
        e.printStackTrace();
        session.rollback();
    }

    // query
    Session session = Session.currentSession();
    session.begin();
    try {
        Query<Employee> query = Employee.find.where().gt("age", 50);
        query = query.limit(8);
        List<Employee> employees = query.all(); // or query.all(session);
        for (int i = 0; i < employees.size(); ++i) {
            Employee employee = employees.get(i);
            logger.info((i + 1) + ". employee " + employee.getId());
        }
        logger.info("total: " + query.count()); // or query.count(session);
        session.commit();
    } catch (Exception e) {
        e.printStackTrace();
        session.rollback();
    }
