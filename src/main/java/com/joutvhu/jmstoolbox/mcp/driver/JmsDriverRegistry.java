package com.joutvhu.jmstoolbox.mcp.driver;

import com.joutvhu.jmstoolbox.mcp.config.model.QManagerDef;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages Dynamic ClassLoaders for different JMS Broker types / QManager definitions.
 * Automatically discovers JMSToolBox installation across Windows, macOS, and Linux.
 */
public class JmsDriverRegistry {
    private static JmsDriverRegistry instance;

    private final Map<String, URLClassLoader> qmClassLoaders = new ConcurrentHashMap<>();
    private final List<File> jtbInstallationJars = new ArrayList<>();
    private File discoveredJtbHome = null;

    public static synchronized JmsDriverRegistry getInstance() {
        if (instance == null) {
            instance = new JmsDriverRegistry();
            instance.init();
        }
        return instance;
    }

    private void init() {
        findJtbInstallation();
    }

    private void findJtbInstallation() {
        List<File> candidates = new ArrayList<>();

        // 1. Explicit environment variable
        String jtbHomeEnv = System.getenv("JTB_HOME");
        if (jtbHomeEnv != null && !jtbHomeEnv.trim().isEmpty()) {
            candidates.add(new File(jtbHomeEnv.trim()));
        }

        String os = System.getProperty("os.name", "").toLowerCase();
        String userHome = System.getProperty("user.home");

        // 2. Windows specific paths
        if (os.contains("win")) {
            // Check drive letters C: to Z:
            File[] roots = File.listRoots();
            if (roots != null) {
                for (File root : roots) {
                    candidates.add(new File(root, "Program Files\\JMSToolBox"));
                    candidates.add(new File(root, "Program Files (x86)\\JMSToolBox"));
                    candidates.add(new File(root, "JMSToolBox"));
                    candidates.add(new File(root, "Apps\\JMSToolBox"));
                    candidates.add(new File(root, "Tools\\JMSToolBox"));
                }
            }

            String localAppData = System.getenv("LOCALAPPDATA");
            if (localAppData != null) {
                candidates.add(Paths.get(localAppData, "Programs", "JMSToolBox").toFile());
                candidates.add(Paths.get(localAppData, "JMSToolBox").toFile());
            }

            candidates.add(Paths.get(userHome, "JMSToolBox").toFile());
            candidates.add(Paths.get(userHome, "AppData", "Local", "Programs", "JMSToolBox").toFile());
        } else if (os.contains("mac")) {
            // 3. macOS specific paths
            candidates.add(new File("/Applications/JMSToolBox.app/Contents/Eclipse"));
            candidates.add(new File("/Applications/JMSToolBox.app/Contents/Resources"));
            candidates.add(new File("/Applications/JMSToolBox.app/Contents"));
            candidates.add(new File("/Applications/JMSToolBox"));
            candidates.add(Paths.get(userHome, "Applications/JMSToolBox.app/Contents/Eclipse").toFile());
            candidates.add(Paths.get(userHome, "Applications/JMSToolBox").toFile());
        } else {
            // 4. Linux / Unix specific paths
            candidates.add(new File("/opt/jmstoolbox"));
            candidates.add(new File("/opt/JMSToolBox"));
            candidates.add(new File("/usr/local/jmstoolbox"));
            candidates.add(new File("/usr/local/JMSToolBox"));
            candidates.add(new File("/usr/share/jmstoolbox"));
            candidates.add(Paths.get(userHome, "jmstoolbox").toFile());
            candidates.add(Paths.get(userHome, "JMSToolBox").toFile());
            candidates.add(Paths.get(userHome, ".local", "share", "jmstoolbox").toFile());
        }

        for (File dir : candidates) {
            if (dir.exists() && dir.isDirectory()) {
                File plugins = new File(dir, "plugins");
                if (plugins.exists() && plugins.isDirectory()) {
                    discoveredJtbHome = dir;
                    scanJars(plugins, jtbInstallationJars);
                    break;
                }
            }
        }

        if (discoveredJtbHome != null) {
            System.err.println("[JmsDriverRegistry] Detected JMSToolBox installation at: " + discoveredJtbHome.getAbsolutePath());
            System.err.println("[JmsDriverRegistry] Discovered " + jtbInstallationJars.size() + " plugin JARs from JMSToolBox.");
        } else {
            System.err.println("[JmsDriverRegistry] JMSToolBox installation not auto-detected. (You can set JTB_HOME if needed)");
        }
    }

    private void scanJars(File dir, List<File> result) {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory()) {
                scanJars(f, result);
            } else if (f.isFile() && f.getName().toLowerCase().endsWith(".jar")) {
                result.add(f);
            }
        }
    }

    public File getDiscoveredJtbHome() {
        return discoveredJtbHome;
    }

    /**
     * Get or create a dedicated URLClassLoader for a specific QManagerDef.
     */
    public ClassLoader getClassLoaderForQManager(QManagerDef qManagerDef) {
        if (qManagerDef == null) {
            return JmsDriverRegistry.class.getClassLoader();
        }

        String id = qManagerDef.getId() != null ? qManagerDef.getId() : "default";
        return qmClassLoaders.computeIfAbsent(id, k -> {
            Set<URL> urls = new LinkedHashSet<>();

            // 1. Add jars defined in the QManager definition (from config.xml)
            if (qManagerDef.getJars() != null) {
                for (String jarPath : qManagerDef.getJars()) {
                    File jarFile = new File(jarPath);
                    if (!jarFile.isAbsolute() && discoveredJtbHome != null) {
                        jarFile = new File(discoveredJtbHome, jarPath);
                    }

                    if (jarFile.exists()) {
                        try {
                            urls.add(jarFile.toURI().toURL());
                        } catch (Exception e) {
                            System.err.println("[JmsDriverRegistry] Invalid jar URL: " + jarPath + ": " + e.getMessage());
                        }
                    } else {
                        System.err.println("[JmsDriverRegistry] Warning: jar not found at " + jarPath);
                    }
                }
            }

            // 2. Add matched JTB plugins (bundled Artemis, ActiveMQ, OpenMQ, Solace...)
            for (File pJar : jtbInstallationJars) {
                try {
                    urls.add(pJar.toURI().toURL());
                } catch (Exception ignored) {}
            }

            System.err.println("[JmsDriverRegistry] Created ClassLoader for " + id + " with " + urls.size() + " JARs.");
            return new URLClassLoader(urls.toArray(new URL[0]), JmsDriverRegistry.class.getClassLoader());
        });
    }
}
