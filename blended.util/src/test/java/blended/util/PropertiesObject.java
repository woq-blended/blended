package blended.util;

public class PropertiesObject {

  public static boolean DEFAULT_BOOL = true;
  public static long DEFAULT_LONG = 10;
  public static int DEFAULT_INT = 100;

  private int intProp = DEFAULT_INT;
  private long longProp = DEFAULT_LONG;
  private boolean boolProp = DEFAULT_BOOL;

  public long getLongProp() {
    return longProp;
  }

  public void setLongProp(long longProp) {
    this.longProp = longProp;
  }

  public boolean getBoolProp() {
    return boolProp;
  }

  public void setBoolProp(boolean boolProp) {
    this.boolProp = boolProp;
  }

  public int getIntProp() {
    return intProp;
  }

  public void setIntProp(int intProp) {
    this.intProp = intProp;
  }
}
