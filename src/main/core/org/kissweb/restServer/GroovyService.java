package org.kissweb.restServer;

import org.kissweb.StringUtils;
import org.kissweb.database.Connection;
import org.apache.log4j.Logger;
import org.json.JSONObject;

import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.lang.reflect.Method;
import java.util.*;

/**
 * Author: Blake McBride
 * Date: 5/5/18
 */
public class GroovyService {

    private static final transient Logger logger = Logger.getLogger(GroovyService.class);

    private static final HashMap<String, GroovyClassInfo> groovyClassCache = new HashMap<>();

    private static class GroovyClassInfo {
        static long cacheLastChecked = 0;   // last time cache unload checked
        GroovyClass gclass;
        long lastModified;
        long lastAccess;
        int executing;

        GroovyClassInfo(GroovyClass gc, long lm) {
            gclass = gc;
            lastModified = lm;
            lastAccess = (new Date()).getTime() / 1000L;
            executing = 0;
        }
    }

    /**
     * This method is used to obtain a method from a groovy class.  The Groovy file is treated as a microservice.
     * This means you will always get the most current definition of the method.  Once the method is obtained, it may be evoked any number of times.
     * <br><br>
     * This method is mainly used in cases where a method will be evoked multiple times.  If it is only going to be evoked once,
     * then the <code>run</code> method (the one that doesn't take the method object) should be used instead.
     *
     * @param ignoreMissing if <code>true</code> ignore missing classes or methods and return null
     * @param filePath relative to the "backend" directory unless it is an absolute path
     * @param className
     * @param methodName
     * @param args  the actual arguments or the argument types (classes)
     * @return
     * @throws Exception
     *
     * @see #run(Method, Object, Object...)
     * @see #run(String, String, String, Object, Object...)
     * @see #getMethod(String, String, String, Object...)
     */
    public static Method getMethod(boolean ignoreMissing, String filePath, String className, String methodName, Object... args) throws Exception {
        String rootPath = MainServlet.getApplicationPath();
        rootPath = StringUtils.drop(rootPath, -1);  //  drop the trailing slash
        if (filePath == null || filePath.isEmpty())
            filePath = rootPath;
        else if (!filePath.startsWith("/"))
            filePath = rootPath + "/" + filePath;
        final String fileName = filePath + "/" + className + ".groovy";
        if (ignoreMissing && !(new File(fileName)).exists())
            return null;
        final GroovyClassInfo ci = loadGroovyClass(fileName);
        Method methp;
        if (ci == null) {
            if (ignoreMissing)
                return null;
            throw new Exception("Groovy file " + new File(fileName).getAbsolutePath() + " not found.");
        }
        Class<?>[] ca = new Class<?>[args.length];
        for (int i=0 ; i < args.length ; i++) {
            if (args[i] == null)
                ca[i] = Object.class;
            else if (args[i] instanceof Class) {
                // The user is passing a class indicating the class of a null argument
                ca[i] = (Class) args[i];
                args[i] = null;
            } else
                ca[i] = args[i].getClass();
        }
        try {
            methp = ci.gclass.getMethod(methodName, ca);
            if (methp == null) {
                if (ignoreMissing)
                    return null;
                throw new Exception();
            }
        } catch (Exception e) {
            throw new Exception("Method " + methodName + " not found in Groovy file " + fileName, e);
        }
        return methp;
    }

    /**
     * This method is used to obtain a method from a groovy class.  The Groovy file is treated as a microservice.
     * This means you will always get the most current definition of the method.  Once the method is obtained, it may be evoked any number of times.
     * <br><br>
     * This method is mainly used in cases where a method will be evoked multiple times.  If it is only going to be evoked once,
     * then the <code>run</code> method (the one that doesn't take the method object) should be used instead.
     * <br><br>
     * On the Groovy side, all arguments are received in boxed form.  Groovy
     * must also return a boxed object.
     *
     * @param filePath relative to the "backend" directory unless it is an absolute path
     * @param className
     * @param methodName
     * @param args  the actual arguments or the argument types (classes)
     * @return
     * @throws Exception
     *
     * @see #run(Method, Object, Object...)
     * @see #run(String, String, String, Object, Object...)
     * @see #getMethod(boolean, String, String, String, Object...)
     */
    public static Method getMethod(String filePath, String className, String methodName, Object... args) throws Exception {
        return getMethod(false, filePath, className, methodName, args);
    }

    /**
     * This method is used to run a method on a groovy class.  The method would normally be returned from the
     * <code>getMethod</code> method.
     * <br><br>
     * On the Groovy side, all arguments are received in boxed form.  Groovy
     * must also return a boxed object.
     *
     * @param methp the method to evoke
     * @param inst instance or null if a class method
     * @param args
     * @return
     * @throws Exception
     *
     * @see #getMethod(String, String, String, Object...)
     */
    public static Object run(Method methp, Object inst, Object... args) throws Exception {
        try {
            if (args == null) {
                args = new Object[1];
                args[0] = null;
            }
            return methp.invoke(inst, args);
        } catch (Exception e) {
            throw new Exception("Error executing method " + methp.getName() + " of class " + methp.getClass().getName(), e);
        }
    }

    /**
     * This method allows calls to Groovy microservices.
     * This method can be used to execute a static or instance methods.
     * On the Groovy side, all arguments are received in boxed form.  Groovy
     * must also return a boxed object.
     * <p>
     * On the Java side, boxed or unboxed arguments may be used but a boxed type is always returned.
     * <p>
     * If <code>ignoreMissing</code> is <code>true</code> and the file, class, or method are missing a <code>NULL</code> is returned.
     * If <code>ignoreMissing</code> is <code>false</code> and the file, class, or method are missing an exception is thrown.
     * <p>
     * <code>filePath</code> is relative to the <code>backend</code> directory unless it is an absolute path.
     *
     * @param ignoreMissing
     * @param filePath relative to the "backend" directory unless it is an absolute path
     * @param className
     * @param methodName
     * @param inst          the instance the method is evoked against or null if static method
     * @param args          boxed or unboxed arguments (variable number)
     * @return The boxed value returned by the Groovy method call
     * @throws Exception
     * @see #run(String, String, String, Object, Object...)
     */
    public static Object run(boolean ignoreMissing, String filePath, String className, String methodName, Object inst, Object... args) throws Exception {
        Method meth = getMethod(ignoreMissing, filePath, className, methodName, inst, args);
        return run(meth, inst, args);
    }

    /**
     * This is the method that allows Groovy to be used as a scripting language.
     * This method can be used to execute a static or instance method.
     * On the Groovy side, all arguments are received in boxed form.  Groovy
     * must also return a boxed object.
     * <p>
     * On the Java side, boxed or unboxed arguments may be used but a boxed type is always returned.
     * <p>
     * <code>filePath</code> is relative to the <code>backend</code> directory unless it is an absolute path.
     *
     * @param filePath relative to the "backend" directory unless it is an absolute path
     * @param className
     * @param methodName
     * @param inst          the instance the method is evoked against or null if static method
     * @param args          boxed or unboxed arguments (variable number)
     * @return The boxed value returned by the Groovy method call
     * @throws Exception
     * @see #run(boolean, String, String, String, Object, Object...)
     */
    public static Object run(String filePath, String className, String methodName, Object inst, Object... args) throws Exception {
        return run(false, filePath, className, methodName, inst, args);
    }

    /**
     * Execute a Groovy constructor.
     *
     * @param relativePath
     * @param className
     * @param args
     * @return
     * @throws Exception
     */
    public static Object constructor(String relativePath, String className, Object... args) throws Exception {
        String rootPath = MainServlet.getApplicationPath();
        final String fileName = rootPath + "/" + (relativePath != null && !relativePath.isEmpty() ? relativePath + "/" : "") + className + ".groovy";
        final GroovyClassInfo ci = loadGroovyClass(fileName);
        if (ci == null)
            throw new Exception("Groovy file " + new File(fileName).getAbsolutePath() + " not found.");
        return ci.gclass.invokeConstructor(args);
    }

    ProcessServlet.ExecutionReturn internalGroovy(ProcessServlet ms, HttpServletResponse response, String _package, String _className, String _method) {
        final String _fullClassPath = _package != null ? _package + "." + _className : _className;
        final String fileName = MainServlet.getApplicationPath() + "/" + _fullClassPath.replace(".", "/") + ".groovy";
        final GroovyClassInfo ci = loadGroovyClass(fileName);
        if (ci != null) {
            Class<?>[] ca = {
            };

            try {
                Method methp = ci.gclass.getMethod(_method, ca);
                if (methp == null) {
                    if (ms != null)
                        ms.errorReturn(response, "Method " + _method + " not found in class " + this.getClass().getName(), null);
                    return ProcessServlet.ExecutionReturn.Error;
                }
                try {
                    ci.executing++;
                    methp.invoke(null);
                } finally {
                    ci.executing--;
                }
                return ProcessServlet.ExecutionReturn.Success;
            } catch (Exception e) {
                if (ms != null)
                    ms.errorReturn(response, "Error running method " + _method + " in class " + this.getClass().getName(), e);
                return ProcessServlet.ExecutionReturn.Error;
            }
        }
        return ProcessServlet.ExecutionReturn.NotFound;
    }

    ProcessServlet.ExecutionReturn tryGroovy(ProcessServlet ms, HttpServletResponse response, String _className, String _method, JSONObject injson, JSONObject outjson) {
        GroovyClassInfo ci;
        String fileName = MainServlet.getApplicationPath() + _className.replace(".", "/") + ".groovy";
        logger.info("Attempting to load " + fileName);
        ci = loadGroovyClass(fileName);
        if (ci != null) {
            logger.info("Found and loaded");
            try {
                ci.executing++;
                Object instance;
                try {
                    instance = ci.gclass.invokeConstructor();
                } catch (Exception e) {
                    ms.errorReturn(response, "Error creating instance of " + fileName, null);
                    return ProcessServlet.ExecutionReturn.Error;
                }

                Method meth;

                try {
                    logger.info("Searching for method " + _method);
                    meth = ci.gclass.getMethod(_method, JSONObject.class, JSONObject.class, Connection.class, ProcessServlet.class);
                } catch (Exception e) {
                    ms.errorReturn(response, "Error running " + fileName + " " + _method + "()", null);
                    return ProcessServlet.ExecutionReturn.Error;
                }

                try {
                    logger.info("Evoking method " + _method);
                    meth.invoke(instance, injson, outjson, ms.DB, ms);
                } catch (Exception e) {
                    ms.errorReturn(response, fileName + " " + _method + "()", e.getCause());
                    return ProcessServlet.ExecutionReturn.Error;
                }
                logger.info("Method completed successfully");
                return ProcessServlet.ExecutionReturn.Success;
            } finally {
                ci.executing--;
            }
        }
        return ProcessServlet.ExecutionReturn.NotFound;
    }

    private synchronized static GroovyClassInfo loadGroovyClass(String fileName) {
        GroovyClass gclass;
        GroovyClassInfo ci;
        if (groovyClassCache.containsKey(fileName)) {
            ci = groovyClassCache.get(fileName);
            /* This must be done by checking the file date rather than a directory change watcher for two reasons:
                1) directory change watchers don't work on sub-directories
                2) there is no notification for file moves
             */
            long lastModified = (new File(fileName)).lastModified();
            if (lastModified == 0L) {
                groovyClassCache.remove(fileName);
                logger.error(new File(fileName).getAbsolutePath() + " not found");
                return null;
            }
            if (lastModified == ci.lastModified) {
                ci.lastAccess = (new Date()).getTime() / 1000L;
                cleanGroovyCache();
                return ci;
            }
            groovyClassCache.remove(fileName);
        }
        cleanGroovyCache();
        File fyle = new File(fileName);
        if (!fyle.exists()) {
            logger.info(new File(fileName).getAbsolutePath() + " not found");
            return null;
        }
        try {
            GroovyClass.reset();
            gclass = new GroovyClass(false, fileName);
            groovyClassCache.put(fileName, ci = new GroovyClassInfo(gclass, fyle.lastModified()));
        } catch (Exception e) {
            logger.error("Error loading " + new File(fileName).getAbsolutePath(), e);
            return null;
        }
        return ci;
    }

    private static void cleanGroovyCache() {
        long current = (new Date()).getTime() / 1000L;
        if (current - GroovyClassInfo.cacheLastChecked > ProcessServlet.CheckCacheDelay) {
            ArrayList<String> keys = new ArrayList<>();
            for (Map.Entry<String, GroovyClassInfo> itm : groovyClassCache.entrySet()) {
                GroovyClassInfo ci = itm.getValue();
                if (ci.executing > 0)
                    ci.lastAccess = current;
                else if (current - ci.lastAccess > ProcessServlet.MaxHold)
                    keys.add(itm.getKey());
            }
            for (String key : keys)
                groovyClassCache.remove(key);
            GroovyClassInfo.cacheLastChecked = current;
        }
    }

}
