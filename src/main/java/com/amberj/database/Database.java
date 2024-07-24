package com.amberj.database;

import com.moandjiezana.toml.Toml;
import jakarta.persistence.Entity;
import jakarta.persistence.Query;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.cfg.Configuration;
import org.hibernate.service.ServiceRegistry;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;

import static com.amberj.database.EntityScanner.scanForEntities;

public class Database {

    private final SessionFactory sessionFactory;

    public Database() {
        this("config.toml");
    }

    public Database(String configUrl) {
        var url = getClass().getClassLoader().getResource(configUrl);

        if (url == null)
            throw new RuntimeException(configUrl + " does not exist.");

        File file = new File(url.getFile());
        Toml toml = new Toml().read(file);
        var list = toml.getTable("database");

        Configuration configuration = getConfiguration(list);

        List<Class<?>> entityClasses = scanForEntities();
        for (Class<?> entityClass : entityClasses) {
            configuration.addAnnotatedClass(entityClass);
        }

        ServiceRegistry serviceRegistry = new StandardServiceRegistryBuilder()
            .applySettings(configuration.getProperties()).build();

        sessionFactory = configuration.buildSessionFactory(serviceRegistry);
    }

    public void save(Object entity) {
        if (!entity.getClass().isAnnotationPresent(Entity.class)) {
            throw new IllegalArgumentException("Object is not annotated with @Entity");
        }

        Transaction transaction = null;
        try (Session session = sessionFactory.openSession()) {
            transaction = session.beginTransaction();
            session.persist(entity);
            transaction.commit();
        } catch (Exception e) {
            if (transaction != null) {
                transaction.rollback();
            }
            System.out.println("Error saving(" + entity.getClass() + "): " + e.getMessage());
        }
    }

    public void update(Object entity) {
        if (!entity.getClass().isAnnotationPresent(Entity.class)) {
            throw new IllegalArgumentException("Object is not annotated with @Entity");
        }

        Transaction transaction = null;
        try (Session session = sessionFactory.openSession()) {
            transaction = session.beginTransaction();
            session.merge(entity);
            transaction.commit();
        } catch (Exception e) {
            if (transaction != null) {
                transaction.rollback();
            }
            System.out.println("Error updating(" + entity.getClass() + "): " + e.getMessage());
        }
    }

    public <T> Command<T> query(Class<T> entityClass) {
        return new Command<>(this, entityClass);
    }

    public <T> List<T> getAll(Class<T> entityClass) {
        try (Session session = sessionFactory.openSession()) {
            Query query = session.createQuery("FROM " + entityClass.getSimpleName(), entityClass);
            return query.getResultList();
        } catch (Exception e) {
            System.out.println("Error retrieving info for(" + entityClass.getSimpleName() + "): " + e.getMessage());
            return null;
        }
    }

    public <T> List<T> executeSelect(Class<T> entityClass, String queryString, HashMap<String, String> fieldValueMap) {
        Session session = null;
        Transaction transaction = null;
        try {
            session = sessionFactory.openSession();
            transaction = session.beginTransaction();

            Query query = session.createQuery(queryString, entityClass);
            fieldValueMap.forEach(query::setParameter);
            List<T> resultList = query.getResultList();
            transaction.commit();
            return resultList;
        } catch (Exception e) {
            if (transaction != null) {
                transaction.rollback();
            }
            e.printStackTrace();
            return null;
        } finally {
            if (session != null) {
                session.close();
            }
        }
    }

    public int executeDelete(String queryString, HashMap<String, String> fieldValueMap) {
        Session session = null;
        Transaction transaction = null;
        try {
            session = sessionFactory.openSession();
            transaction = session.beginTransaction();

            Query query = session.createQuery(queryString);
            fieldValueMap.forEach(query::setParameter);
            int rowCount = query.executeUpdate();
            transaction.commit();
            return rowCount;
        } catch (Exception e) {
            if (transaction != null) {
                transaction.rollback();
            }
            e.printStackTrace();
            return 0;
        } finally {
            if (session != null) {
                session.close();
            }
        }
    }

    private Configuration getConfiguration(Toml toml) {

        Configuration configuration = new Configuration();

        configuration.setProperty("hibernate.dialect", getDialectFromDriver(toml.getString("driver")));
        configuration.setProperty("hibernate.connection.driver_class", getDriverClassFromDriver(toml.getString("driver")));
        configuration.setProperty("hibernate.connection.url", "jdbc:" + toml.getString("url"));

        if (!Objects.equals(toml.getString("driver"), "sqlite")) {
            configuration.setProperty("hibernate.connection.username", toml.getString("username"));
            configuration.setProperty("hibernate.connection.password", toml.getString("password"));
        }
        if (toml.getString("ddl") != null) {
            configuration.setProperty("hibernate.hbm2ddl.auto", toml.getString("ddl"));
        }

        return configuration;
    }

    private String getDriverClassFromDriver(String driver) {
        switch (driver) {
            case "sqlite":
                return "org.sqlite.JDBC";
            case "mysql":
                return "com.mysql.cj.jdbc.Driver";
            case "postgresql":
                return "org.postgresql.Driver";
            default:
                return null;
        }
    }

    private String getDialectFromDriver(String driver) {
        switch (driver) {
            case "sqlite":
                return "org.hibernate.community.dialect.SQLiteDialect";
            case "mysql":
                return "org.hibernate.dialect.MySQLDialect";
            case "postgresql":
                return "org.hibernate.dialect.PostgreSQLDialect";
            default:
                return null;
        }
    }

    public static class Command<T> {
        private final StringBuilder query;
        private final Database database;
        private final Class<T> entityClass;
        private final HashMap<String, String> fieldValueMap;
        private boolean isDeleteQuery;

        public Command(Database database, Class<T> entityClass) {
            this.query = new StringBuilder();
            this.database = database;
            this.entityClass = entityClass;
            this.fieldValueMap = new HashMap<>();
            this.isDeleteQuery = false;
        }

        public Command<T> from() {
            if (this.query.isEmpty()) {
                this.query.append("FROM ").append(entityClass.getSimpleName());
            } else {
                this.query.append(" FROM ").append(entityClass.getSimpleName());
            }
            return this;
        }

        public Command<T> delete() {
            this.query.append(" DELETE");
            this.isDeleteQuery = true;
            return this;
        }

        public Command<T> where(String condition) {
            var list = condition.split(" ");
            var field = list[0];
            var value = list[list.length - 1].substring(1);

            this.fieldValueMap.put(field, value);

            this.query.append(" WHERE ").append(condition.replace(value, field));
            return this;
        }

        public static String eq(String field, String value) {
            return field + " = :" + value;
        }

        public static String lt(String field, String value) {
            return field + " < :" + value;
        }

        public List<T> collect() {
            if (isDeleteQuery) {
                throw new IllegalStateException("Delete query cannot collect results.");
            } else {
                return database.executeSelect(entityClass, query.toString(), fieldValueMap);
            }
        }

        public int execute() {
            if (isDeleteQuery) {
                return database.executeDelete(query.toString(), fieldValueMap);
            } else {
                throw new IllegalStateException("Execute can only be called for delete queries.");
            }
        }

        public String getQuery() {
            return this.query.toString();
        }
    }
}
