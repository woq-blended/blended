package blended.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public final class ReflectionHelper {

  private final static Logger LOGGER = LoggerFactory.getLogger(ReflectionHelper.class);

  private ReflectionHelper() {}

  public static List<String> getPropertyNames(final Object obj) {

    final List<Method> getter = getGetterMethods(obj.getClass());
    final List<String> result = new ArrayList<String>();

    for(Method m : getter) {
      LOGGER.info(m.getName());
      result.add(getPropertyName(m));
    }

    return result;
  }

  public static <T> T getProperty(Object object, String... propertyNames) {

    if (propertyNames == null || object == null) {
      return null;
    }

    Object current = object;
    for (String part : propertyNames) {
      current = getPropertyInternal(current, part);
      if (current == null) {
        break;
      }
    }

    try {
      T result = (T) current;
      return result;
    } catch (ClassCastException cce) {
      getLogger().warn("ReflectionHelper encountered wrong property type: " + current.getClass().getName());
      return null;
    }
  }

  public static <T> T getProperty(Object object, String propertyName) {
    if (propertyName == null) {
      return null;
    } else {
      return (T) getProperty(object, propertyName.split("\\."));
    }
  }


  public static void setProperty(Object object, Object value, String... propertyNames) {

    try {
      if (object == null || propertyNames == null || propertyNames.length == 0) {
        throw new Exception("Destination object and property name must not be empty to set the property");
      }
    } catch (Exception e) {
      LOGGER.warn("Wrong context to set property", e);
      return;
    }

    Object current = object;

    for (int i = 0; i < propertyNames.length - 1; i++) {
      current = getPropertyInternal(current, propertyNames[i]);
      if (current == null) {
        LOGGER.warn("Property [" + propertyNames[i] + "] not found for object [" + current + "]" );
        break;
      }
    }

    if (current != null) {
      setPropertyInternal(current, propertyNames[propertyNames.length - 1], value);
    }
  }

  private static void setPropertyInternal(Object object, String propertyName, Object value)
  {
    for (Method m : getSetterMethods(object.getClass())) {
      if (getPropertyName(m).equals(propertyName)) {
        try {
          LOGGER.debug("Setting property [{}] to [{}]", propertyName, value);
          m.invoke(object, value);
        } catch (Exception e) {

          Class<?> pType = m.getParameterTypes()[0];
          Class<?> tClass = Object.class;

          if (pType.isPrimitive() && pType.getName().equals("boolean")) {
            tClass = Boolean.class;
          } else if (pType.isPrimitive() && pType.getName().equals("long")) {
            tClass = Long.class;
          } else if (pType.isPrimitive() && pType.getName().equals("short")) {
            tClass = Short.class;
          } else if (pType.isPrimitive() && pType.getName().equals("int")) {
            tClass = Integer.class;
          } else if (pType.isPrimitive() && pType.getName().equals("float")) {
            tClass = Float.class;
          } else if (pType.isPrimitive() && pType.getName().equals("double")) {
            tClass = Double.class;
          } else if (pType.isPrimitive() && pType.getName().equals("byte")) {
            tClass = Byte.class;
          } else {
            tClass = pType;
          }

          try {
            Constructor<?> constructor = tClass.getConstructor(value.getClass());
            m.invoke(object, constructor.newInstance(value));
          } catch (NoSuchMethodException nsme) {
            LOGGER.warn("No constructor found for [{}] from type [{}]", tClass.getName(), value.getClass());
            return;
          } catch (Exception e1) {
            LOGGER.warn("Failed to set property ... trying to convert [{}] into [{}]", value.getClass().getName(), m.getParameterTypes()[0].getName());
          }
        }
      }
    }
  }

  public static List<Method> getGetterMethods(final Class<?> clazz) {
    final List<Method> result = new ArrayList<Method>();

    for (Method m : clazz.getMethods()) {
      if (m.getName().startsWith("get") && !(m.getName().equals("getClass")))  {
        if (m.getParameterTypes().length == 0) {
          result.add(m);
        }
      }
    }
    return result;
  }

  public static List<Method> getSetterMethods(final Class<?> clazz) {
    final List<Method> result = new ArrayList<Method>();

    for (Method m : clazz.getMethods()) {
      if (m.getName().startsWith("set")) {
        if (m.getParameterTypes().length == 1) {
          result.add(m);
        }
      }
    }
    return result;
  }

  public static String getPropertyName(Class<?> clazz, Class<?> propertyType) {
    for (Method getter : getGetterMethods(clazz)) {
      if (getter.getReturnType().equals(propertyType)) {
        try {
          clazz.getMethod(getter.getName().replaceFirst("g", "s"), propertyType);
          return getPropertyName(getter);
        } catch (Exception e) {
          // We can ignore this ...
        }
      }
    }

    return null;
  }

  public static String getPropertyName(Method m) {
    String result = null;

    if (m.getName().startsWith("get") || m.getName().startsWith("set")) {
      result = m.getName().substring(3);
      if (result.length() == 1) {
        result = result.toLowerCase();
      } else {
        if (result.matches(".[a-z](.)*")) {
          result = result.substring(0, 1).toLowerCase() + result.substring(1);
        }
      }
    }
    return result;
  }

  private static Object getPropertyInternal(Object object, String propertyName) {
    for (Method m : getGetterMethods(object.getClass())) {
      if (getPropertyName(m).equals(propertyName)) {
        try {
          Object result = m.invoke(object);
          return result;
        } catch (Exception e) {
          // Ignore this
        }
      }
    }
    return null;
  }

  private static Logger getLogger()
  {
    return LoggerFactory.getLogger(ReflectionHelper.class);
  }
}
