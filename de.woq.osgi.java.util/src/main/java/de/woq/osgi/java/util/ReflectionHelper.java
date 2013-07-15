/*
 * Copyright 2013, WoQ - Way of Quality UG(mbH)
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package de.woq.osgi.java.util;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ReflectionHelper
{
  private ReflectionHelper()
  {

  }

  public static <T> T getProperty(Object object, String... propertyNames)
  {

    if (propertyNames == null || object == null)
    {
      return null;
    }

    Object current = object;
    for (String part : propertyNames)
    {
      current = getPropertyInternal(current, part);
      if (current == null)
      {
        break;
      }
    }

    try
    {
      T result = (T) current;
      return result;
    }
    catch (ClassCastException cce)
    {
      getLogger().warn("ReflectionHelper encountered wrong property type: " + current.getClass().getName());
      return null;
    }
  }

  public static <T> T getProperty(Object object, String propertyName)
  {
    if (propertyName == null)
    {
      return null;
    }
    else
    {
      return (T) getProperty(object, propertyName.split("\\."));
    }
  }

  private static Object getPropertyInternal(Object object, String propertyName)
  {
    for (Method m : getGetterMethods(object.getClass()))
    {
      if (getPropertyName(m).equals(propertyName))
      {
        try
        {
          Object result = m.invoke(object);
          return result;
        }
        catch (Exception e)
        {
          // Ignore this
        }
      }
    }
    return null;
  }

  public static void setProperty(Object object, Object value, String... propertyNames)
  {
    if (object == null || propertyNames == null || propertyNames.length == 0)
    {
      return;
    }

    Object current = object;

    for (int i = 0; i < propertyNames.length - 1; i++)
    {
      current = getPropertyInternal(current, propertyNames[i]);
      if (current == null)
      {
        break;
      }
    }

    if (current != null)
    {
      setProperty(current, propertyNames[propertyNames.length - 1], value);
    }
  }

  public static void setProperty(Object object, String propertyName, Object value)
  {
    for (Method m : getSetterMethods(object.getClass()))
    {
      if (getPropertyName(m).equals(propertyName))
      {
        try
        {
          m.invoke(object, value);
        }
        catch (Exception e)
        {
          // Ignore this
        }
      }
    }
  }

  public static List<Method> getGetterMethods(final Class<?> clazz)
  {
    final List<Method> result = new ArrayList<Method>();

    for (Method m : clazz.getMethods())
    {
      if (m.getName().startsWith("get"))
      {
        if (m.getParameterTypes().length == 0)
        {
          result.add(m);
        }
      }
    }
    return result;
  }

  public static List<Method> getSetterMethods(final Class<?> clazz)
  {
    final List<Method> result = new ArrayList<Method>();

    for (Method m : clazz.getMethods())
    {
      if (m.getName().startsWith("set"))
      {
        if (m.getParameterTypes().length == 1)
        {
          result.add(m);
        }
      }
    }
    return result;
  }

  public static String getPropertyName(Class<?> clazz, Class<?> propertyType)
  {
    for (Method getter : getGetterMethods(clazz))
    {
      if (getter.getReturnType().equals(propertyType))
      {
        try
        {
          clazz.getMethod(getter.getName().replaceFirst("g", "s"), propertyType);
          return getPropertyName(getter);
        }
        catch (Exception e)
        {
          // We can ignore this ...
        }
      }
    }

    return null;
  }

  public static String getPropertyName(Method m)
  {
    String result = null;

    if (m.getName().startsWith("get") || m.getName().startsWith("set"))
    {
      result = m.getName().substring(3);
      if (result.length() == 1)
      {
        result = result.toLowerCase();
      }
      else
      {
        if (result.matches(".[a-z](.)*"))
        {
          result = result.substring(0, 1).toLowerCase() + result.substring(1);
        }
      }
    }
    return result;
  }

  private static Logger getLogger()
  {
    return LoggerFactory.getLogger(ReflectionHelper.class);
  }
}
