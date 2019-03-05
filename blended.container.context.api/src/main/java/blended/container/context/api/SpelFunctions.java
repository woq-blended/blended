package blended.container.context.api;

import java.util.Map;

public class SpelFunctions {

  static String left(String s, int n) {
    if (s.length() <= n || s == null) {
      return s;
    } else {
      return s.substring(0,n);
    }
  }

  static String right(String s, int n) {
    if (s.length() <= n || s == null) {
      return s;
    } else {
      return s.substring(s.length() - n);
    }
  }

  static String capitalize(String s) {
    if (s == null || s.length() == 0) {
      return s;
    } else {
      return s.substring(0,1).toUpperCase() + s.substring(1);
    }
  }

  static String capitalizeAll(String s) {
    if (s == null || s.length() == 0) {
      return s;
    } else {
      String[] words = s.split(" ");
      StringBuffer buf = new StringBuffer();
      boolean start = true;
      for(String w : words) {
        if (!start) {
          buf.append(" ");
        }
        buf.append(capitalize(w));
        start = false;
      }

      return buf.toString();
    }
  }

  static String replace(String s, Map<String, String> repl) {
    return s;
  }
}
