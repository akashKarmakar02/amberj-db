package com.amberj.database;

import jakarta.persistence.Entity;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class EntityScanner {

    public static List<Class<?>> scanForEntities() {
        List<Class<?>> entityClasses = new ArrayList<>();
        String classPath = System.getProperty("java.class.path");
        String[] classPathEntries = classPath.split(File.pathSeparator);

        for (String classPathEntry : classPathEntries) {
            if (!classPathEntry.endsWith(".jar")) {
                scanDirectory(new File(classPathEntry), "", entityClasses);
            }
        }
        return entityClasses;
    }

    private static void scanDirectory(File directory, String packageName, List<Class<?>> entityClasses) {
        if (directory.exists()) {
            File[] files = directory.listFiles();
            assert files != null;
            for (File file : files) {
                if (file.isDirectory()) {
                    if (packageName.isEmpty()) {
                        scanDirectory(file, file.getName(), entityClasses);
                    } else {
                        scanDirectory(file, packageName + "." + file.getName(), entityClasses);
                    }
                } else if (file.getName().endsWith(".class")) {
                    String className = packageName + "." + file.getName().substring(0, file.getName().length() - 6);
                    Class<?> clazz;
                    try {
                        clazz = Class.forName(className);
                        if (clazz.isAnnotationPresent(Entity.class)) {
                            entityClasses.add(clazz);
                        }
                    } catch (ClassNotFoundException _) {
                    }
                }
            }
        }
    }
}

